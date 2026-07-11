package dev.perms.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/**
 * Shizuku API v13 makes {@code Shizuku.newProcess(...)} private (Kotlin-only).
 *
 * For a simple test-bench app, we call it via reflection.
 *
 * NOTE: This is for testing / convenience. For production apps, prefer Shizuku UserService.
 */
public final class ShizukuCompat {

    private ShizukuCompat() {
        // no instances
    }

    private static volatile Method sNewProcess;

    private static Method getNewProcess() throws NoSuchMethodException {
        Method m = sNewProcess;
        if (m != null) return m;
        m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        m.setAccessible(true);
        sNewProcess = m;
        return m;
    }

    public static Process newProcess(String[] cmd, String[] env, String dir) throws Exception {
        try {
            Object p = getNewProcess().invoke(null, cmd, env, dir);
            return (Process) p;
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        }
    }
}
