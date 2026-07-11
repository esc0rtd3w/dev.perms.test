package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

/**
 * Shared FTP server path, port, and storage-root helpers for the Network tab.
 */
public final class NetworkFtpServerPaths {
    private NetworkFtpServerPaths() {}

    public static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            if (port < 1 || port > 65535) throw new NumberFormatException("range");
            return port;
        } catch (Throwable ignored) {
            throw new IllegalArgumentException("Port must be 1-65535.");
        }
    }

    public static String defaultRootPath() {
        try {
            String path = normalizeAbsoluteRoot(Environment.getExternalStorageDirectory().getAbsolutePath(), "/storage/emulated/0");
            if ("/sdcard".equals(path) || "/storage/self/primary".equals(path)) return "/storage/emulated/0";
            return path;
        } catch (Throwable ignored) {
            return "/storage/emulated/0";
        }
    }

    public static String defaultRootForMode(boolean useShizuku) {
        return useShizuku ? "/" : defaultRootPath();
    }

    public static String loadRootForMode(SharedPreferences prefs, boolean useShizuku) {
        try {
            if (prefs == null) return defaultRootForMode(useShizuku);
            String saved = prefs.getString(useShizuku
                    ? NetworkPreferenceKeys.FTP_ROOT_SHIZUKU
                    : NetworkPreferenceKeys.FTP_ROOT_NORMAL, null);
            if (!TextUtils.isEmpty(saved)) {
                String normalized = normalizeAbsoluteRoot(saved, defaultRootForMode(useShizuku));
                return normalizeRootForMode(useShizuku, normalized);
            }

            String legacy = normalizeAbsoluteRoot(prefs.getString(NetworkPreferenceKeys.FTP_ROOT, null), "");
            if (TextUtils.isEmpty(legacy)) return defaultRootForMode(useShizuku);
            if (useShizuku && (isSharedStorageRoot(legacy) || isLegacyAppRoot(legacy))) return "/";
            return normalizeRootForMode(useShizuku, legacy);
        } catch (Throwable ignored) {
            return defaultRootForMode(useShizuku);
        }
    }

    public static void saveRootForMode(SharedPreferences prefs, boolean useShizuku, String root) {
        try {
            if (prefs == null) return;
            prefs.edit()
                    .putString(useShizuku
                                    ? NetworkPreferenceKeys.FTP_ROOT_SHIZUKU
                                    : NetworkPreferenceKeys.FTP_ROOT_NORMAL,
                            normalizeRootForMode(useShizuku,
                                    normalizeAbsoluteRoot(root, defaultRootForMode(useShizuku))))
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static String normalizeRootForMode(boolean useShizuku, String root) {
        String normalized = normalizeAbsoluteRoot(root, defaultRootForMode(useShizuku));
        if (!useShizuku && isUnsupportedNormalRoot(normalized)) return defaultRootPath();
        return normalized;
    }

    public static boolean isUnsupportedNormalRoot(String root) {
        String value = normalizeAbsoluteRoot(root, "");
        return "/".equals(value)
                || "/storage".equals(value)
                || "/storage/emulated".equals(value);
    }

    public static String rootForModeSwitch(boolean useShizuku, String loadedRoot) {
        String root = normalizeRootForMode(useShizuku, loadedRoot);
        if (!useShizuku && isUnsupportedNormalRoot(root)) return defaultRootPath();
        return root;
    }

    public static String normalizeAbsoluteRoot(String path, String fallback) {
        String value = path == null ? "" : path.trim().replace('\\', '/');
        if (TextUtils.isEmpty(value)) value = fallback == null ? "" : fallback.trim().replace('\\', '/');
        if (TextUtils.isEmpty(value)) value = "/";
        if (!value.startsWith("/")) value = "/" + value;
        while (value.contains("//")) value = value.replace("//", "/");
        while (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public static boolean needsAllFilesAccess(String root) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        String path = root == null ? "" : root.trim().replace('\\', '/');
        if (TextUtils.isEmpty(path)) return false;
        return "/sdcard".equals(path)
                || path.startsWith("/sdcard/")
                || "/storage/emulated/0".equals(path)
                || path.startsWith("/storage/emulated/0/")
                || "/storage/self/primary".equals(path)
                || path.startsWith("/storage/self/primary/");
    }

    private static boolean isSharedStorageRoot(String path) {
        String value = normalizeAbsoluteRoot(path, "");
        return normalizeAbsoluteRoot(defaultRootPath(), "/storage/emulated/0").equals(value)
                || "/sdcard".equals(value)
                || "/storage/emulated/0".equals(value);
    }

    private static boolean isLegacyAppRoot(String path) {
        String value = normalizeAbsoluteRoot(path, "");
        return "/storage/emulated/0/dev.perms.test".equals(value)
                || "/sdcard/dev.perms.test".equals(value);
    }
}
