package moe.shizuku.manager.adb;
/**
 * Minimal ADB protocol constants (ported from Shizuku Manager).
 */
public final class AdbProtocol {

    private AdbProtocol() {}

    public static final int A_SYNC = 0x434e5953; // 'SYNC'
    public static final int A_CNXN = 0x4e584e43; // 'CNXN'
    public static final int A_OPEN = 0x4e45504f; // 'OPEN'
    public static final int A_OKAY = 0x59414b4f; // 'OKAY'
    public static final int A_CLSE = 0x45534c43; // 'CLSE'
    public static final int A_WRTE = 0x45545257; // 'WRTE'
    public static final int A_AUTH = 0x48545541; // 'AUTH'
    public static final int A_STLS = 0x534c5453; // 'STLS'

    // Match upstream Shizuku exactly.
    public static final int A_VERSION = 0x01000000;
    public static final int A_MAXDATA = 4096;

    public static final int A_STLS_VERSION = 0x01000000;

    public static final int ADB_AUTH_TOKEN = 1;
    public static final int ADB_AUTH_SIGNATURE = 2;
    public static final int ADB_AUTH_RSAPUBLICKEY = 3;
}
