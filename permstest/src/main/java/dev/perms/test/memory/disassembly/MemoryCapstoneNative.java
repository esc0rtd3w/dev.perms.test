package dev.perms.test.memory.disassembly;

/**
 * Minimal JNI surface for Capstone.
 *
 * Native code returns compact tab-delimited records so Java can keep instruction/reference models
 * project-local and reusable.  This avoids importing Android-Disassembler's Kotlin/JNA binding layer.
 */
final class MemoryCapstoneNative {
    private static final boolean LOADED;
    private static final String LOAD_ERROR;

    static {
        boolean loaded = false;
        String error = "";
        try {
            System.loadLibrary("perms_test_capstone");
            loaded = nativeIsSupported();
        } catch (Throwable t) {
            error = t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage());
        }
        LOADED = loaded;
        LOAD_ERROR = error;
    }

    private MemoryCapstoneNative() {
    }

    static boolean isLoaded() {
        return LOADED;
    }

    static String loadError() {
        return LOAD_ERROR;
    }

    static String[] disassemble(byte[] bytes, long baseAddress, int maxInstructions) {
        if (!LOADED || bytes == null || bytes.length == 0) return new String[0];
        try {
            String[] rows = nativeDisassemble(bytes, baseAddress, maxInstructions);
            return rows == null ? new String[0] : rows;
        } catch (Throwable ignored) {
            return new String[0];
        }
    }

    private static native boolean nativeIsSupported();

    private static native String[] nativeDisassemble(byte[] bytes, long baseAddress, int maxInstructions);
}
