package dev.perms.test.shizuku.internal;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

/**
 * Lightweight diagnostics for the Internal Shizuku bootstrap path.
 * Verbose messages are gated by Settings > Debug Output so normal use stays quiet.
 */
public final class InternalShizukuDiagnostics {
    private static final String TAG = "PermsTestInternalShizuku";
    private static final String PREFS = "perms_test";
    private static final String PREF_DEBUG_OUTPUT = "debug_mode";
    private static final int MAX_LOG_CHARS = 1800;

    private InternalShizukuDiagnostics() {
    }

    static boolean isEnabled(Context context) {
        try {
            if (context == null) return false;
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
            return sp != null && sp.getBoolean(PREF_DEBUG_OUTPUT, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void debug(Context context, String message) {
        if (!isEnabled(context)) return;
        Log.d(TAG, safe(message));
    }

    static void info(Context context, String message) {
        if (!isEnabled(context)) return;
        Log.i(TAG, safe(message));
    }

    static void warn(String message) {
        Log.w(TAG, safe(message));
    }

    static void warn(String message, Throwable t) {
        if (t == null) {
            warn(message);
            return;
        }
        Log.w(TAG, safe(message) + ": " + throwableSummary(t));
    }

    static void warnVerbose(Context context, String message, Throwable t) {
        if (isEnabled(context)) {
            Log.w(TAG, safe(message), t);
        } else {
            warn(message, t);
        }
    }

    static String throwableSummary(Throwable t) {
        if (t == null) return "unknown";
        String msg = String.valueOf(t.getMessage());
        if (TextUtils.isEmpty(msg) || "null".equals(msg)) return t.getClass().getSimpleName();
        return t.getClass().getSimpleName() + ": " + oneLine(msg, 220);
    }

    static String oneLine(String value, int maxChars) {
        if (value == null) return "";
        String out = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        if (maxChars <= 0 || out.length() <= maxChars) return out;
        return out.substring(0, maxChars) + "…";
    }

    private static String safe(String message) {
        String out = message == null ? "" : message;
        if (out.length() <= MAX_LOG_CHARS) return out;
        return out.substring(0, MAX_LOG_CHARS) + "…";
    }
}
