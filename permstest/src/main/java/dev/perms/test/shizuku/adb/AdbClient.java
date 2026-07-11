package dev.perms.test.shizuku.adb;
import android.os.Build;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.net.ssl.SSLSocket;

/**
 * ADB protocol client (ported from Shizuku Manager).
 *
 * Connects directly to the Wireless Debugging endpoint (no adb-server state).
 */
public final class AdbClient implements Closeable {

    private final String host;
    private final int port;
    private final AdbKey key;

    private int nextLocalId = 1;

    private synchronized int allocLocalId() {
        return nextLocalId++;
    }

    private Socket socket;
    private DataInputStream plainIn;
    private DataOutputStream plainOut;

    private boolean useTls;
    private SSLSocket tlsSocket;
    private DataInputStream tlsIn;
    private DataOutputStream tlsOut;

    private DataInputStream in() { return useTls ? tlsIn : plainIn; }
    private DataOutputStream out() { return useTls ? tlsOut : plainOut; }

    public AdbClient(String host, int port, AdbKey key) {
        this.host = host;
        this.port = port;
        this.key = key;
    }

    public void connect() throws Exception {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        plainIn = new DataInputStream(socket.getInputStream());
        plainOut = new DataOutputStream(socket.getOutputStream());

        write(new AdbMessage(AdbProtocol.A_CNXN, AdbProtocol.A_VERSION, AdbProtocol.A_MAXDATA, "host::"));

        AdbMessage message = read();
        if (message.command == AdbProtocol.A_STLS) {
            if (Build.VERSION.SDK_INT < 29) {
                throw new IllegalStateException("Connect to adb with TLS is not supported before Android 10");
            }
            write(new AdbMessage(AdbProtocol.A_STLS, AdbProtocol.A_STLS_VERSION, 0, (byte[]) null));

            tlsSocket = (SSLSocket) key.getSslContext().getSocketFactory().createSocket(socket, host, port, true);
            tlsSocket.startHandshake();

            tlsIn = new DataInputStream(tlsSocket.getInputStream());
            tlsOut = new DataOutputStream(tlsSocket.getOutputStream());
            useTls = true;

            message = read();
        } else if (message.command == AdbProtocol.A_AUTH) {
            if (!(message.command == AdbProtocol.A_AUTH && message.arg0 == AdbProtocol.ADB_AUTH_TOKEN)) {
                throw new IllegalStateException("Expected AUTH TOKEN");
            }
            write(new AdbMessage(AdbProtocol.A_AUTH, AdbProtocol.ADB_AUTH_SIGNATURE, 0, key.sign(message.data)));

            message = read();
            if (message.command != AdbProtocol.A_CNXN) {
                write(new AdbMessage(AdbProtocol.A_AUTH, AdbProtocol.ADB_AUTH_RSAPUBLICKEY, 0, key.getAdbPublicKey()));
                message = read();
            }
        }

        if (message.command != AdbProtocol.A_CNXN) {
            throw new IllegalStateException("Not CNXN");
        }
    }

    public void shellCommand(String command, DataListener listener) throws Exception {
        final int localId = allocLocalId();
        write(new AdbMessage(AdbProtocol.A_OPEN, localId, 0, "shell:" + command));

        int remoteId = 0;
        StringBuilder skipped = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            AdbMessage message = read();
            if (message.command == AdbProtocol.A_OKAY && message.arg1 == localId) {
                remoteId = message.arg0;
                break;
            }
            if (message.command == AdbProtocol.A_CLSE && message.arg1 == localId) {
                write(new AdbMessage(AdbProtocol.A_CLSE, localId, message.arg0, (byte[]) null));
                return;
            }
            rememberSkippedFrame(skipped, message);
            drainUnexpectedFrame(message);
        }
        if (remoteId <= 0) {
            throw new IllegalStateException("Expected OKAY/CLSE for shell after skipping " + skipped);
        }

        while (true) {
            AdbMessage message = read();
            if (message.command == AdbProtocol.A_WRTE && message.arg1 == localId) {
                if (message.dataLength > 0 && message.data != null) {
                    if (listener != null) listener.onData(message.data);
                }
                write(new AdbMessage(AdbProtocol.A_OKAY, localId, message.arg0, (byte[]) null));
            } else if (message.command == AdbProtocol.A_CLSE && message.arg1 == localId) {
                write(new AdbMessage(AdbProtocol.A_CLSE, localId, message.arg0, (byte[]) null));
                break;
            } else if (message.command == AdbProtocol.A_OKAY && message.arg1 == localId) {
                // Some adbd builds can send an extra stream OKAY while the shell stream is active.
                // It is only flow-control state, not shell output, so keep waiting for WRTE/CLSE.
            } else {
                rememberSkippedFrame(skipped, message);
                drainUnexpectedFrame(message);
            }
        }
    }

    private static String commandToString(int command) {
        byte[] b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array();
        String s = new String(b, java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c > 126) return "0x" + Integer.toHexString(command);
        }
        return s;
    }

    private void write(AdbMessage message) throws Exception {
        out().write(message.toByteArray());
        out().flush();
    }

    private AdbMessage read() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(AdbMessage.HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        in().readFully(buffer.array(), 0, AdbMessage.HEADER_LENGTH);
        int command = buffer.getInt();
        int arg0 = buffer.getInt();
        int arg1 = buffer.getInt();
        int dataLength = buffer.getInt();
        int checksum = buffer.getInt();
        int magic = buffer.getInt();
        byte[] data = null;
        if (dataLength > 0) {
            data = new byte[dataLength];
            in().readFully(data, 0, dataLength);
        }
        AdbMessage msg = new AdbMessage(command, arg0, arg1, dataLength, checksum, magic, data);
        msg.validateOrThrow();
        return msg;
    }

    
    /**
     * Push a file to the device using the ADB "sync:" service.
     *
     * This avoids relying on on-device unzip/toybox and works on OneUI where external storage paths
     * may be unreadable to the shell user.
     */
    public void syncPush(byte[] data, String remotePath, int mode, int mtimeSeconds) throws Exception {
        if (data == null) throw new IllegalArgumentException("data == null");
        if (remotePath == null) throw new IllegalArgumentException("remotePath == null");

        StreamIds s = openStream("sync:");
        try {
            String sendSpec = remotePath + "," + mode;
            writeSyncPacket(s, "SEND", sendSpec.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            int off = 0;
            final int max = Math.max(1024, AdbProtocol.A_MAXDATA - 8);
            while (off < data.length) {
                int n = Math.min(max, data.length - off);
                byte[] chunk = java.util.Arrays.copyOfRange(data, off, off + n);
                writeSyncPacket(s, "DATA", chunk);
                off += n;
            }
            java.io.ByteArrayOutputStream pendingSyncReply = new java.io.ByteArrayOutputStream(32);
            writeSyncDone(s, mtimeSeconds, pendingSyncReply);

            SyncReply reply = readSyncReply(s, pendingSyncReply);
            if (!"OKAY".equals(reply.id)) {
                String msg = reply.payload == null ? "" : new String(reply.payload, java.nio.charset.StandardCharsets.UTF_8);
                throw new IllegalStateException("syncPush failed: " + reply.id + (msg.isEmpty() ? "" : (": " + msg)));
            }

            // Tell remote we're done.
            writeSyncPacket(s, "QUIT", new byte[0]);
        } finally {
            closeStreamQuietly(s);
        }
    }

    private static final class StreamIds {
        final int localId;
        final int remoteId;
        StreamIds(int localId, int remoteId) {
            this.localId = localId;
            this.remoteId = remoteId;
        }
    }

    private StreamIds openStream(String service) throws Exception {
        final int localId = allocLocalId();
        write(new AdbMessage(AdbProtocol.A_OPEN, localId, 0, service));

        StringBuilder skipped = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            AdbMessage message = read();
            if (message.command == AdbProtocol.A_OKAY && message.arg1 == localId) {
                return new StreamIds(localId, message.arg0);
            }
            if (message.command == AdbProtocol.A_CLSE && message.arg1 == localId) {
                write(new AdbMessage(AdbProtocol.A_CLSE, localId, message.arg0, (byte[]) null));
                throw new IllegalStateException("Stream closed: " + service);
            }

            rememberSkippedFrame(skipped, message);
            drainUnexpectedFrame(message);
        }

        throw new IllegalStateException("Expected OKAY/CLSE for " + service + " after skipping " + skipped);
    }

    private void rememberSkippedFrame(StringBuilder out, AdbMessage message) {
        if (out == null || message == null) return;
        if (out.length() > 0) out.append("; ");
        out.append(commandToString(message.command))
                .append(" arg0=").append(message.arg0)
                .append(" arg1=").append(message.arg1)
                .append(" len=").append(message.dataLength);
    }

    private void drainUnexpectedFrame(AdbMessage message) throws Exception {
        if (message == null) return;
        if (message.command == AdbProtocol.A_WRTE) {
            write(new AdbMessage(AdbProtocol.A_OKAY, message.arg1, message.arg0, (byte[]) null));
        } else if (message.command == AdbProtocol.A_CLSE) {
            write(new AdbMessage(AdbProtocol.A_CLSE, message.arg1, message.arg0, (byte[]) null));
        }
    }

    private void closeStreamQuietly(StreamIds s) {
        if (s == null) return;
        try {
            write(new AdbMessage(AdbProtocol.A_CLSE, s.localId, s.remoteId, (byte[]) null));
        } catch (Throwable ignored) {}
    }

    private void writeSyncPacket(StreamIds s, String id4, byte[] payload) throws Exception {
        if (id4 == null || id4.length() != 4) throw new IllegalArgumentException("id4");
        if (payload == null) payload = new byte[0];

        ByteBuffer pkt = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        pkt.put(id4.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        pkt.putInt(payload.length);
        pkt.put(payload);

        writeAndAwaitOkay(s, pkt.array(), null);
    }

    private void writeSyncDone(StreamIds s, int mtimeSeconds, java.io.ByteArrayOutputStream incoming) throws Exception {
        ByteBuffer pkt = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        pkt.put("DONE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        pkt.putInt(mtimeSeconds);

        writeAndAwaitOkay(s, pkt.array(), incoming);
    }

    private void writeAndAwaitOkay(StreamIds s, byte[] payload, java.io.ByteArrayOutputStream incoming) throws Exception {
        write(new AdbMessage(AdbProtocol.A_WRTE, s.localId, s.remoteId, payload));
        while (true) {
            AdbMessage message = read();
            if (message.command == AdbProtocol.A_OKAY && message.arg1 == s.localId) {
                break;
            }
            if (message.command == AdbProtocol.A_WRTE && message.arg1 == s.localId) {
                if (incoming != null && message.dataLength > 0 && message.data != null) {
                    incoming.write(message.data);
                }
                write(new AdbMessage(AdbProtocol.A_OKAY, s.localId, message.arg0, (byte[]) null));
                continue;
            }
            if (message.command == AdbProtocol.A_CLSE && message.arg1 == s.localId) {
                write(new AdbMessage(AdbProtocol.A_CLSE, s.localId, message.arg0, (byte[]) null));
                throw new IllegalStateException("Stream closed while writing");
            }
            drainUnexpectedFrame(message);
        }
    }

    private static final class SyncReply {
        final String id;
        final byte[] payload;
        SyncReply(String id, byte[] payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    private SyncReply readSyncReply(StreamIds s, java.io.ByteArrayOutputStream incoming) throws Exception {
        if (incoming == null) incoming = new java.io.ByteArrayOutputStream(1024);
        // Need at least 8 bytes header.
        while (incoming.size() < 8) {
            AdbMessage m = read();
            if (m.command == AdbProtocol.A_WRTE && m.arg1 == s.localId) {
                if (m.dataLength > 0 && m.data != null) incoming.write(m.data);
                write(new AdbMessage(AdbProtocol.A_OKAY, s.localId, m.arg0, (byte[]) null));
            } else if (m.command == AdbProtocol.A_OKAY && m.arg1 == s.localId) {
                // ignore
            } else if (m.command == AdbProtocol.A_CLSE && m.arg1 == s.localId) {
                write(new AdbMessage(AdbProtocol.A_CLSE, s.localId, m.arg0, (byte[]) null));
                throw new IllegalStateException("Stream closed while reading reply");
            } else {
                drainUnexpectedFrame(m);
            }
        }

        byte[] buf = incoming.toByteArray();
        String id = new String(buf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        int len = ByteBuffer.wrap(buf, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // Ensure full payload present.
        while (incoming.size() < 8 + len) {
            AdbMessage m = read();
            if (m.command == AdbProtocol.A_WRTE && m.arg1 == s.localId) {
                if (m.dataLength > 0 && m.data != null) incoming.write(m.data);
                write(new AdbMessage(AdbProtocol.A_OKAY, s.localId, m.arg0, (byte[]) null));
            } else if (m.command == AdbProtocol.A_OKAY && m.arg1 == s.localId) {
                // ignore
            } else if (m.command == AdbProtocol.A_CLSE && m.arg1 == s.localId) {
                write(new AdbMessage(AdbProtocol.A_CLSE, s.localId, m.arg0, (byte[]) null));
                throw new IllegalStateException("Stream closed while reading reply payload");
            } else {
                drainUnexpectedFrame(m);
            }
        }

        byte[] all = incoming.toByteArray();
        byte[] payload = len <= 0 ? new byte[0] : java.util.Arrays.copyOfRange(all, 8, 8 + len);
        return new SyncReply(id, payload);
    }

@Override
    public void close() {
        try { if (plainIn != null) plainIn.close(); } catch (Throwable ignored) {}
        try { if (plainOut != null) plainOut.close(); } catch (Throwable ignored) {}
        try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
        if (useTls) {
            try { if (tlsIn != null) tlsIn.close(); } catch (Throwable ignored) {}
            try { if (tlsOut != null) tlsOut.close(); } catch (Throwable ignored) {}
            try { if (tlsSocket != null) tlsSocket.close(); } catch (Throwable ignored) {}
        }
    }

    public interface DataListener {
        void onData(byte[] data);
    }
}
