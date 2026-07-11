package dev.perms.test.debug;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.List;

/**
 * Shared debug logging helpers for PermsTest subsystems.
 */
public final class DebugLog {
    public static final String DEFAULT_TAG = "PermsTestDebug";

    private DebugLog() {
    }

    public static String line(String channel, String area, String message) {
        return "[" + safe(channel) + "-debug:" + safe(area) + "] " + safe(message);
    }

    public static void log(String tag, String channel, String area, String message) {
        try {
            Log.d(safeTag(tag), line(channel, area, message));
        } catch (Throwable ignored) {
        }
    }

    public static void logToOutput(String tag, String channel, String area, String message, DebugOutput output) {
        log(tag, channel, area, message);
        appendToOutput(channel, area, message, output);
    }

    public static void warn(String tag, String channel, String area, String message) {
        try {
            Log.w(safeTag(tag), line(channel, area, message));
        } catch (Throwable ignored) {
        }
    }

    public static void warnToOutput(String tag, String channel, String area, String message, DebugOutput output) {
        warn(tag, channel, area, message);
        appendToOutput(channel, area, message, output);
    }

    public static void error(String tag, String channel, String area, String message, Throwable t) {
        try {
            Log.e(safeTag(tag), line(channel, area, message), t);
        } catch (Throwable ignored) {
        }
    }

    public static String describeLengths(String stdout, String stderr) {
        return "stdoutLen=" + length(stdout) + ", stderrLen=" + length(stderr);
    }

    public static String describePath(String path) {
        if (TextUtils.isEmpty(path)) return "<empty>";
        try {
            File f = new File(path);
            return path + " [name=" + f.getName() + ", exists=" + f.exists() + ", len=" + (f.exists() ? f.length() : -1L) + "]";
        } catch (Throwable ignored) {
            return path;
        }
    }

    public static String describePathList(List<String> paths, int maxItems) {
        if (paths == null) return "<null>";
        StringBuilder sb = new StringBuilder();
        sb.append("count=").append(paths.size());
        int count = Math.min(Math.max(0, maxItems), paths.size());
        for (int i = 0; i < count; i++) {
            sb.append("\n  ").append(i).append(": ").append(describePath(paths.get(i)));
        }
        if (paths.size() > count) {
            sb.append("\n  ... +").append(paths.size() - count).append(" more");
        }
        return sb.toString();
    }

    private static void appendToOutput(String channel, String area, String message, DebugOutput output) {
        if (output == null) return;
        try {
            if (!output.isEnabled()) return;
            output.appendLine(line(channel, area, message));
        } catch (Throwable ignored) {
        }
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }

    private static String safeTag(String tag) {
        return TextUtils.isEmpty(tag) ? DEFAULT_TAG : tag;
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
