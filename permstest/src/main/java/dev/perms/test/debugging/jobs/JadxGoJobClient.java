package dev.perms.test.debugging.jobs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import java.io.File;

/** Activity-side controller for the foreground jadx-go DEX-to-Java job. */
public final class JadxGoJobClient {
    public interface Callbacks {
        void setBusy(boolean busy, String status);
        void appendOutput(String text);
        void setStatusText(String status);
        void setProgressState(boolean running, String status, int current, int total, String phase);
    }

    private static final long RUNNING_STALE_MS = 30_000L;
    private static final long FUTURE_CLOCK_SKEW_MS = 60_000L;

    private final Context context;
    private final Handler handler;
    private final Callbacks callbacks;
    private Runnable statusPoller;

    public JadxGoJobClient(Context context, Handler handler, Callbacks callbacks) {
        this.context = context == null ? null : context.getApplicationContext();
        this.handler = handler;
        this.callbacks = callbacks;
    }

    public boolean isRunning() {
        try {
            SharedPreferences sp = prefs();
            clearStaleRunningStateIfNeeded(sp);
            return sp.getBoolean(PermsTestSmaliJobService.PREF_JADX_RUNNING, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void start(String input, String outDir, boolean zipOutput, boolean javaInnerNames, String dexEntry) {
        try {
            if (context == null) return;
            Intent svc = new Intent(context, PermsTestSmaliJobService.class);
            svc.setAction(PermsTestSmaliJobService.ACTION_JADX_DECOMPILE);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_INPUT, input == null ? "" : input);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_JADX_OUT_DIR, outDir == null ? "" : outDir);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_JADX_ZIP_OUTPUT, zipOutput);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_JADX_JAVA_INNER_NAMES, javaInnerNames);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_JADX_DEX_ENTRY, dexEntry == null ? "" : dexEntry);
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, svc);
            else context.startService(svc);
            if (callbacks != null) {
                callbacks.setBusy(true, "DEX to Java running in foreground...");
                callbacks.setProgressState(true, "DEX to Java running in foreground...", 0, 0, "start");
                callbacks.appendOutput("[Debugging] Started foreground DEX to Java job.\n");
            }
            scheduleStatusPoll();
        } catch (Throwable t) {
            if (callbacks != null) {
                callbacks.setBusy(false, "DEX to Java start failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    public void restore() {
        try {
            SharedPreferences sp = prefs();
            clearStaleRunningStateIfNeeded(sp);
            boolean running = sp.getBoolean(PermsTestSmaliJobService.PREF_JADX_RUNNING, false);
            String status = sp.getString(PermsTestSmaliJobService.PREF_JADX_STATUS, "");
            int current = sp.getInt(PermsTestSmaliJobService.PREF_JADX_PROGRESS_CURRENT, 0);
            int total = sp.getInt(PermsTestSmaliJobService.PREF_JADX_PROGRESS_TOTAL, 0);
            String phase = sp.getString(PermsTestSmaliJobService.PREF_JADX_PHASE, "");
            if (!TextUtils.isEmpty(status) && callbacks != null) {
                callbacks.setStatusText(status);
                callbacks.setProgressState(running, status, current, total, phase);
            }
            if (running) {
                if (callbacks != null) callbacks.setBusy(true, TextUtils.isEmpty(status) ? "DEX to Java running in foreground..." : status);
                scheduleStatusPoll();
            } else if (!TextUtils.isEmpty(status) && callbacks != null) {
                callbacks.setBusy(false, status);
                callbacks.setProgressState(false, status, current, total, phase);
            }
        } catch (Throwable ignored) {
        }
    }

    public void scheduleStatusPoll() {
        try {
            if (handler == null) return;
            if (statusPoller != null) handler.removeCallbacks(statusPoller);
            statusPoller = () -> {
                try {
                    SharedPreferences sp = prefs();
                    clearStaleRunningStateIfNeeded(sp);
                    boolean running = sp.getBoolean(PermsTestSmaliJobService.PREF_JADX_RUNNING, false);
                    String status = sp.getString(PermsTestSmaliJobService.PREF_JADX_STATUS, "");
                    int current = sp.getInt(PermsTestSmaliJobService.PREF_JADX_PROGRESS_CURRENT, 0);
                    int total = sp.getInt(PermsTestSmaliJobService.PREF_JADX_PROGRESS_TOTAL, 0);
                    String phase = sp.getString(PermsTestSmaliJobService.PREF_JADX_PHASE, "");
                    if (!TextUtils.isEmpty(status) && callbacks != null) {
                        callbacks.setStatusText(status);
                        callbacks.setProgressState(running, status, current, total, phase);
                    }
                    if (running) {
                        if (callbacks != null) callbacks.setBusy(true, TextUtils.isEmpty(status) ? "DEX to Java running in foreground..." : status);
                        handler.postDelayed(statusPoller, 1000L);
                    } else {
                        if (callbacks != null) {
                            callbacks.setBusy(false, TextUtils.isEmpty(status) ? "DEX to Java finished." : status);
                            callbacks.setProgressState(false, TextUtils.isEmpty(status) ? "DEX to Java finished." : status, current, total, phase);
                            String log = sp.getString(PermsTestSmaliJobService.PREF_JADX_LOG, "");
                            if (!TextUtils.isEmpty(log)) callbacks.appendOutput(log.endsWith("\n") ? log : (log + "\n"));
                        }
                        sp.edit().putString(PermsTestSmaliJobService.PREF_JADX_LOG, "").apply();
                        statusPoller = null;
                    }
                } catch (Throwable ignored) {
                    statusPoller = null;
                }
            };
            handler.postDelayed(statusPoller, 500L);
        } catch (Throwable ignored) {
        }
    }

    private void clearStaleRunningStateIfNeeded(SharedPreferences sp) {
        if (sp == null) return;
        if (!sp.getBoolean(PermsTestSmaliJobService.PREF_JADX_RUNNING, false)) return;
        long updatedAt = sp.getLong(PermsTestSmaliJobService.PREF_JADX_UPDATED_AT, 0L);
        long now = System.currentTimeMillis();
        boolean stale = updatedAt <= 0L || updatedAt > now + FUTURE_CLOCK_SKEW_MS || now - updatedAt > RUNNING_STALE_MS;
        if (!stale) return;
        String previous = sp.getString(PermsTestSmaliJobService.PREF_JADX_STATUS, "");
        String zipPath = sp.getString(PermsTestSmaliJobService.PREF_JADX_ZIP_PATH, "");
        String outDir = sp.getString(PermsTestSmaliJobService.PREF_JADX_OUT_DIR, "");
        boolean zipPresent = !TextUtils.isEmpty(zipPath) && new File(zipPath).isFile() && new File(zipPath).length() > 0L;
        boolean outputPresent = !TextUtils.isEmpty(outDir) && new File(outDir, "index.txt").isFile();
        String status;
        if (zipPresent && previous != null && previous.toLowerCase(java.util.Locale.US).contains("zipping")) {
            status = "DEX to Java zip file is present after restart: " + zipPath;
        } else if (outputPresent && previous != null && previous.toLowerCase(java.util.Locale.US).contains("zipping")) {
            status = "DEX to Java output is present after restart; zip may need to be rerun.";
        } else {
            status = "DEX to Java interrupted before completion. Tap Run to start again.";
        }
        String log = sp.getString(PermsTestSmaliJobService.PREF_JADX_LOG, "");
        StringBuilder append = new StringBuilder();
        append.append("[Debugging] Cleared stale DEX to Java running state after app/service restart.");
        if (!TextUtils.isEmpty(previous)) append.append(" Previous status: ").append(previous);
        if (zipPresent) append.append(" Recovered zip path: ").append(zipPath);
        append.append('\n');
        log += append.toString();
        sp.edit()
                .putBoolean(PermsTestSmaliJobService.PREF_JADX_RUNNING, false)
                .putString(PermsTestSmaliJobService.PREF_JADX_STATUS, status)
                .putString(PermsTestSmaliJobService.PREF_JADX_ERROR, status)
                .putString(PermsTestSmaliJobService.PREF_JADX_LOG, log)
                .putString(PermsTestSmaliJobService.PREF_JADX_PHASE, zipPresent ? "zip-present" : "stale")
                .putLong(PermsTestSmaliJobService.PREF_JADX_UPDATED_AT, now)
                .apply();
    }


    private SharedPreferences prefs() {
        return context.getSharedPreferences(PermsTestSmaliJobService.PREFS, Context.MODE_PRIVATE);
    }
}
