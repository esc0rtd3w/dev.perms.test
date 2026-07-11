package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.content.SharedPreferences;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;

/**
 * Shared local-path and input helpers for the Network tab FTP client.
 */
public final class NetworkFtpClientPaths {
    private static final String DEFAULT_LOCAL_PATH = "/storage/emulated/0/dev.perms.test";

    private NetworkFtpClientPaths() {}

    public static String defaultLocalPath() {
        return DEFAULT_LOCAL_PATH;
    }

    public static String ensureLocalDirectory(String path) {
        String normalized = normalize(TextUtils.isEmpty(path) ? defaultLocalPath() : path);
        File dir = new File(normalized);
        if (!dir.isDirectory()) {
            try { dir.mkdirs(); } catch (Throwable ignored) {}
        }
        if (!dir.isDirectory()) {
            File fallback = Environment.getExternalStorageDirectory();
            normalized = fallback == null ? "/sdcard" : fallback.getAbsolutePath();
        }
        return normalize(normalized);
    }

    public static String normalize(String path) {
        return FtpClientLocalEntry.normalizePath(path);
    }

    public static String parentOf(String path) {
        String p = normalize(path);
        if ("/".equals(p)) return "/";
        int idx = p.lastIndexOf('/');
        return idx <= 0 ? "/" : p.substring(0, idx);
    }

    public static String childOf(String parent, String name) {
        String p = normalize(parent);
        String safe = PermsTestFtpClient.sanitizeName(name);
        if (TextUtils.isEmpty(safe)) safe = "download.bin";
        return "/".equals(p) ? "/" + safe : p + "/" + safe;
    }

    public static void saveLocalDirectory(SharedPreferences prefs, String localDirectory) {
        if (prefs != null && !TextUtils.isEmpty(localDirectory)) {
            prefs.edit()
                    .putString(NetworkPreferenceKeys.FTP_CLIENT_LOCAL_DIR, normalize(localDirectory))
                    .apply();
        }
    }

    public static String emptyToMessage(String text) {
        String s = text == null ? "" : text.trim();
        return s.isEmpty() ? "unknown error" : s;
    }

    public static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            if (port < 1 || port > 65535) throw new NumberFormatException("range");
            return port;
        } catch (Throwable ignored) {
            throw new IllegalArgumentException("FTP client port must be 1-65535.");
        }
    }
}
