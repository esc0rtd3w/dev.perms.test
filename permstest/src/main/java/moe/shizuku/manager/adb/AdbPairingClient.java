package moe.shizuku.manager.adb;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLSocket;
import dev.perms.test.shizuku.adb.ConscryptCompat;

/**
 * ADB pairing client (ported from Shizuku Manager).
 *
 * Uses libadb's pairing crypto (SPAKE2) via JNI.
 */
public final class AdbPairingClient implements Closeable {

    private static final int kCurrentKeyHeaderVersion = 1;
    private static final int kMaxPeerInfoSize = 8192;
    private static final String kExportedKeyLabel = "adb-label\u0000";
    private static final int kExportedKeySize = 64;

    private static final int kPairingPacketHeaderSize = 6;

    private final String host;
    private final int port;
    private final String pairCode;
    private final AdbKey key;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private SSLSocket tlsSocket;
    private DataInputStream tlsIn;
    private DataOutputStream tlsOut;
    private boolean useTls;

    private PairingContext pairingContext;


    public AdbPairingClient(String host, int port, String pairCode, AdbKey key) {
        ShizukuAdbNative.ensureLoaded();
        this.host = host;
        this.port = port;
        this.pairCode = pairCode;
        this.key = key;
    }

    
    private static Socket connectSocketWithRetry(String host, int port) throws Exception {
        final long deadline = System.currentTimeMillis() + 8000L;
        ConnectException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                return new Socket(host, port);
            } catch (ConnectException e) {
                last = e;
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        if (last != null) throw last;
        return new Socket(host, port);
    }

    public void pair() throws Exception {

        socket = connectSocketWithRetry(host, port);
        socket.setTcpNoDelay(true);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        // Pairing uses TLS.
        tlsSocket = (SSLSocket) key.getSslContext().getSocketFactory().createSocket(socket, host, port, true);
        tlsSocket.startHandshake();
        tlsIn = new DataInputStream(tlsSocket.getInputStream());
        tlsOut = new DataOutputStream(tlsSocket.getOutputStream());
        useTls = true;

        // Match Shizuku Manager: password = pairCode + exported_key_material
        byte[] pairCodeBytes = pairCode.getBytes(StandardCharsets.UTF_8);
        byte[] keyMaterial = ConscryptCompat.exportKeyingMaterial(tlsSocket, kExportedKeyLabel, null, kExportedKeySize);
        byte[] passwordBytes = new byte[pairCodeBytes.length + keyMaterial.length];
        System.arraycopy(pairCodeBytes, 0, passwordBytes, 0, pairCodeBytes.length);
        System.arraycopy(keyMaterial, 0, passwordBytes, pairCodeBytes.length, keyMaterial.length);

        pairingContext = PairingContext.create(passwordBytes);
        if (pairingContext == null) throw new IllegalStateException("Unable to create PairingContext.");

        // SPAKE2 message exchange.
        byte[] ourMsg = pairingContext.msg();
        writePacket(PairingPacketHeader.Type.SPAKE2_MSG, ourMsg);
        PairingPacket theirPacket = readPacket();
        if (theirPacket == null || theirPacket.type != PairingPacketHeader.Type.SPAKE2_MSG) throw new AdbInvalidPairingCodeException();
        boolean ok = pairingContext.initCipher(theirPacket.payload);
        if (!ok) throw new AdbInvalidPairingCodeException();

        // Peer info exchange.
        byte[] peerInfo = new PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY.value, key.getAdbPublicKey()).toBytes();
        byte[] enc = pairingContext.encrypt(peerInfo);
        if (enc == null) throw new IllegalStateException("encrypt failed");
        writePacket(PairingPacketHeader.Type.PEER_INFO, enc);
        PairingPacket theirInfo = readPacket();
        if (theirInfo == null || theirInfo.type != PairingPacketHeader.Type.PEER_INFO) throw new AdbInvalidPairingCodeException();
        byte[] dec = pairingContext.decrypt(theirInfo.payload);
        if (dec == null) throw new AdbInvalidPairingCodeException();

        // Success.
    }

    private static final class PeerInfo {
        final byte type;
        final byte[] data = new byte[kMaxPeerInfoSize - 1];

        PeerInfo(byte type, byte[] input) {
            this.type = type;
            if (input != null) {
                int n = Math.min(input.length, data.length);
                System.arraycopy(input, 0, data, 0, n);
            }
        }

        byte[] toBytes() {
            ByteBuffer buf = ByteBuffer.allocate(kMaxPeerInfoSize).order(ByteOrder.BIG_ENDIAN);
            buf.put(type);
            buf.put(data);
            return buf.array();
        }

        enum Type {
            ADB_RSA_PUB_KEY((byte) 0),
            ADB_DEVICE_GUID((byte) 1);

            final byte value;

            Type(byte v) { value = v; }
        }
    }

    private void writePacket(PairingPacketHeader.Type type, byte[] payload) throws Exception {
        PairingPacketHeader header = new PairingPacketHeader(kCurrentKeyHeaderVersion, type.value, payload.length);
        ByteBuffer buf = ByteBuffer.allocate(kPairingPacketHeaderSize).order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) header.version);
        buf.put((byte) header.type);
        buf.putInt(header.payloadSize);
        tlsOut.write(buf.array());
        tlsOut.write(payload);
        tlsOut.flush();
    }

    private PairingPacket readPacket() throws Exception {
        byte[] headerBytes = new byte[kPairingPacketHeaderSize];
        tlsIn.readFully(headerBytes, 0, kPairingPacketHeaderSize);
        ByteBuffer buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN);
        int version = buffer.get() & 0xFF;
        int type = buffer.get() & 0xFF;
        int payloadSize = buffer.getInt();
        if (version != kCurrentKeyHeaderVersion) return null;
        PairingPacketHeader.Type t = PairingPacketHeader.Type.from(type);
        if (t == null) return null;
        byte[] payload = new byte[payloadSize];
        tlsIn.readFully(payload, 0, payloadSize);
        return new PairingPacket(t, payload);
    }

    @Override
    public void close() {
        try { if (in != null) in.close(); } catch (Throwable ignored) {}
        try { if (out != null) out.close(); } catch (Throwable ignored) {}
        try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
        try { if (tlsIn != null) tlsIn.close(); } catch (Throwable ignored) {}
        try { if (tlsOut != null) tlsOut.close(); } catch (Throwable ignored) {}
        try { if (tlsSocket != null) tlsSocket.close(); } catch (Throwable ignored) {}
        try { if (pairingContext != null) pairingContext.destroy(); } catch (Throwable ignored) {}
    }

    private static final class PairingPacket {
        final PairingPacketHeader.Type type;
        final byte[] payload;
        PairingPacket(PairingPacketHeader.Type type, byte[] payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    private static final class PairingPacketHeader {
        final int version;
        final int type;
        final int payloadSize;
        PairingPacketHeader(int version, int type, int payloadSize) {
            this.version = version;
            this.type = type;
            this.payloadSize = payloadSize;
        }

        enum Type {
            SPAKE2_MSG(0),
            PEER_INFO(1);

            final int value;
            Type(int v) { value = v; }

            static Type from(int v) {
                for (Type t : values()) if (t.value == v) return t;
                return null;
            }
        }
    }

    
}