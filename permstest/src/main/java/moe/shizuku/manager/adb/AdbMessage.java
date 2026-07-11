package moe.shizuku.manager.adb;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * ADB message frame (ported from Shizuku Manager).
 */
public final class AdbMessage {

    public static final int HEADER_LENGTH = 24;

    public final int command;
    public final int arg0;
    public final int arg1;
    public final int dataLength;
    public final int checksum;
    public final int magic;
    public final byte[] data;

    public AdbMessage(int command, int arg0, int arg1, byte[] data) {
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.data = data;
        this.dataLength = data == null ? 0 : data.length;
        this.checksum = checksumOf(data);
        this.magic = command ^ 0xFFFFFFFF;
    }

    public AdbMessage(int command, int arg0, int arg1, String data) {
        // Match upstream Shizuku: string payloads are NUL-terminated.
        this(command, arg0, arg1, data == null ? null : (data + "\u0000").getBytes(StandardCharsets.UTF_8));
    }

    public AdbMessage(int command, int arg0, int arg1, int dataLength, int checksum, int magic, byte[] data) {
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.dataLength = dataLength;
        this.checksum = checksum;
        this.magic = magic;
        this.data = data;
    }

    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(command);
        buffer.putInt(arg0);
        buffer.putInt(arg1);
        buffer.putInt(dataLength);
        buffer.putInt(checksum);
        buffer.putInt(magic);
        if (dataLength > 0 && data != null) buffer.put(data);
        return buffer.array();
    }

    public void validateOrThrow() {
        if ((command ^ 0xFFFFFFFF) != magic) {
            throw new IllegalStateException("Bad magic");
        }
        // Match upstream Shizuku: only validate checksum when there is payload.
        if (dataLength != 0 && checksumOf(data) != checksum) {
            throw new IllegalStateException("Bad checksum");
        }
    }

    private static int checksumOf(byte[] data) {
        if (data == null) return 0;
        int sum = 0;
        for (byte b : data) sum += (b & 0xFF);
        return sum;
    }

    @Override
    public String toString() {
        return "AdbMessage{" +
                "cmd=" + command +
                ", arg0=" + arg0 +
                ", arg1=" + arg1 +
                ", len=" + dataLength +
                ", data=" + (data == null ? "null" : Arrays.toString(Arrays.copyOf(data, Math.min(data.length, 64)))) +
                "}";
    }
}
