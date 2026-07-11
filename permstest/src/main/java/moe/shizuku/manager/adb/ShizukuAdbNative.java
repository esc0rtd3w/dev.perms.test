package moe.shizuku.manager.adb;

/**
 * Loads the embedded Shizuku ADB native library used for pairing (SPAKE2) crypto.
 *
 * Important: native libraries cannot be unloaded within a process. Keep this
 * confined to the internal adb process.
 */
public final class ShizukuAdbNative {

    private static volatile boolean sLoaded;

    private ShizukuAdbNative() {}

    public static void ensureLoaded() {
        if (sLoaded) return;
        synchronized (ShizukuAdbNative.class) {
            if (sLoaded) return;
            System.loadLibrary("shizukuadb");
            sLoaded = true;
        }
    }
}
