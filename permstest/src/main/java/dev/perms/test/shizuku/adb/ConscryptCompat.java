package dev.perms.test.shizuku.adb;

import org.conscrypt.Conscrypt;

import java.lang.reflect.Method;

import javax.net.ssl.SSLSocket;

/**
 * Conscrypt compatibility shim.
 *
 * Shizuku's pairing flow needs exportKeyingMaterial(). On some devices the active TLS provider is
 * platform Conscrypt (com.android.org.conscrypt.*). Calling org.conscrypt.Conscrypt directly on a
 * platform Conscrypt socket will throw "Not a conscrypt socket".
 *
 * This shim prefers platform Conscrypt via reflection when present, otherwise falls back to the
 * bundled org.conscrypt implementation.
 */
public final class ConscryptCompat {

    private ConscryptCompat() {}

    private static volatile Method sExportKeyingMaterialPlatform;
    private static volatile Method sExportKeyingMaterialBundled;

    private static Method findPlatformExport() {
        try {
            Class<?> c = Class.forName("com.android.org.conscrypt.Conscrypt");
            return c.getMethod("exportKeyingMaterial", SSLSocket.class, String.class, byte[].class, int.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findBundledExport() {
        try {
            Class<?> c = Class.forName("org.conscrypt.Conscrypt");
            return c.getMethod("exportKeyingMaterial", SSLSocket.class, String.class, byte[].class, int.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Export keying material from the provided SSL socket.
     *
     * @throws IllegalStateException if no compatible Conscrypt API is available.
     */
    public static byte[] exportKeyingMaterial(SSLSocket socket, String label, byte[] context, int length) {
        // Android blocks reflective access to platform Conscrypt exportKeyingMaterial().
        // Use bundled org.conscrypt provider instead.
        try {
            return Conscrypt.exportKeyingMaterial(socket, label, context, length);
        } catch (Throwable t) {
            final String cn = socket != null ? socket.getClass().getName() : "null";
            throw new IllegalStateException("Conscrypt exportKeyingMaterial() failed for socket=" + cn, t);
        }
    }
}
