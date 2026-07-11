package dev.perms.test.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * Small SharedPreferences-backed status bridge for foreground jobs.
 *
 * The status belongs to the feature using it; this class only provides a consistent, bounded
 * status/log/result shape so Activity UI code can poll without holding a Service reference.
 */
public final class LongOperationStatusStore {
    private static final int DEFAULT_MAX_LOG_CHARS = 32000;

    public static final class Snapshot {
        public final boolean running;
        public final boolean success;
        public final String status;
        public final String log;
        public final String resultPath;
        public final String error;
        public final long updatedAt;

        Snapshot(boolean running, boolean success, String status, String log, String resultPath, String error, long updatedAt) {
            this.running = running;
            this.success = success;
            this.status = status == null ? "" : status;
            this.log = log == null ? "" : log;
            this.resultPath = resultPath == null ? "" : resultPath;
            this.error = error == null ? "" : error;
            this.updatedAt = updatedAt;
        }
    }

    private final SharedPreferences prefs;
    private final String prefix;
    private final int maxLogChars;

    public LongOperationStatusStore(Context context, String prefsName, String prefix) {
        this(context, prefsName, prefix, DEFAULT_MAX_LOG_CHARS);
    }

    public LongOperationStatusStore(Context context, String prefsName, String prefix, int maxLogChars) {
        this.prefs = context == null ? null : context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        this.prefix = prefix == null ? "job" : prefix;
        this.maxLogChars = Math.max(1024, maxLogChars);
    }

    public void start(String status, String initialLog) {
        if (prefs == null) return;
        prefs.edit()
                .putBoolean(key("running"), true)
                .putBoolean(key("success"), false)
                .putString(key("status"), status == null ? "" : status)
                .putString(key("log"), initialLog == null ? "" : initialLog)
                .putString(key("result"), "")
                .putString(key("error"), "")
                .putLong(key("updated_at"), System.currentTimeMillis())
                .apply();
    }

    public void update(String status, String appendLog) {
        if (prefs == null) return;
        String log = prefs.getString(key("log"), "");
        if (!TextUtils.isEmpty(appendLog)) log += appendLog;
        if (log.length() > maxLogChars) log = log.substring(log.length() - maxLogChars);
        prefs.edit()
                .putBoolean(key("running"), true)
                .putString(key("status"), status == null ? "" : status)
                .putString(key("log"), log)
                .putLong(key("updated_at"), System.currentTimeMillis())
                .apply();
    }

    public void finish(boolean success, String status, String appendLog, String resultPath, String error) {
        if (prefs == null) return;
        String log = prefs.getString(key("log"), "");
        if (!TextUtils.isEmpty(appendLog)) log += appendLog;
        if (log.length() > maxLogChars) log = log.substring(log.length() - maxLogChars);
        prefs.edit()
                .putBoolean(key("running"), false)
                .putBoolean(key("success"), success)
                .putString(key("status"), status == null ? "" : status)
                .putString(key("log"), log)
                .putString(key("result"), resultPath == null ? "" : resultPath)
                .putString(key("error"), error == null ? "" : error)
                .putLong(key("updated_at"), System.currentTimeMillis())
                .apply();
    }

    public void markStopped(String status) {
        finish(false, status, "", "", "stopped");
    }

    public Snapshot snapshot(boolean consumeLog) {
        if (prefs == null) return new Snapshot(false, false, "", "", "", "", 0L);
        String log = prefs.getString(key("log"), "");
        Snapshot snapshot = new Snapshot(
                prefs.getBoolean(key("running"), false),
                prefs.getBoolean(key("success"), false),
                prefs.getString(key("status"), ""),
                log,
                prefs.getString(key("result"), ""),
                prefs.getString(key("error"), ""),
                prefs.getLong(key("updated_at"), 0L));
        if (consumeLog && !TextUtils.isEmpty(log)) {
            prefs.edit().putString(key("log"), "").apply();
        }
        return snapshot;
    }

    public String status() {
        return prefs == null ? "" : prefs.getString(key("status"), "");
    }

    private String key(String name) {
        return prefix + "_" + name;
    }
}
