package dev.perms.test.debugging.jobs;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;

/**
 * Activity-side controller for the foreground Disassemble All DEX job.
 *
 * The service owns the long-running baksmali work. This class owns the UI-facing start,
 * restore, and status-polling contract so MainActivity does not need to carry service
 * preference plumbing or foreground-service intent setup.
 */
public final class SmaliDisassembleAllJobClient {
    public interface Callbacks {
        void setBusy(boolean busy, String status);
        void appendOutput(String text);
        void refreshDexEntries();
        void finishError(String label, Throwable error);
        void setStatusText(String status);
    }

    private final Context context;
    private final Handler handler;
    private final Callbacks callbacks;
    private Runnable statusPoller;

    public SmaliDisassembleAllJobClient(Context context, Handler handler, Callbacks callbacks) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.callbacks = callbacks;
    }

    public boolean isRunning() {
        try {
            return prefs().getBoolean(PermsTestSmaliJobService.PREF_JOB_RUNNING, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void start(String input, ArrayList<String> dexEntries, ArrayList<String> outDirs, int apiLevel, boolean cleanOutput) {
        try {
            Intent svc = new Intent(context, PermsTestSmaliJobService.class);
            svc.setAction(PermsTestSmaliJobService.ACTION_DISASSEMBLE_ALL);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_INPUT, input);
            svc.putStringArrayListExtra(PermsTestSmaliJobService.EXTRA_DEX_ENTRIES, dexEntries);
            svc.putStringArrayListExtra(PermsTestSmaliJobService.EXTRA_OUT_DIRS, outDirs);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_API_LEVEL, apiLevel);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_CLEAN_OUTPUT, cleanOutput);
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, svc);
            else context.startService(svc);
            callbacks.setBusy(true, "Disassemble All DEX running in foreground...");
            callbacks.appendOutput("[Debugging] Started foreground Disassemble All DEX job.\n");
            scheduleStatusPoll();
        } catch (Throwable t) {
            callbacks.finishError("start foreground disassemble all DEX", t);
        }
    }

    public void restore() {
        try {
            SharedPreferences sp = prefs();
            boolean running = sp.getBoolean(PermsTestSmaliJobService.PREF_JOB_RUNNING, false);
            String status = sp.getString(PermsTestSmaliJobService.PREF_JOB_STATUS, "");
            long smaliUpdatedAt = sp.getLong(PermsTestSmaliJobService.PREF_JOB_UPDATED_AT, 0L);
            long jadxUpdatedAt = sp.getLong(PermsTestSmaliJobService.PREF_JADX_UPDATED_AT, 0L);
            boolean jadxRunning = sp.getBoolean(PermsTestSmaliJobService.PREF_JADX_RUNNING, false);
            boolean newerJadxState = !running && (jadxRunning || jadxUpdatedAt > smaliUpdatedAt);
            if (newerJadxState) return;
            if (!TextUtils.isEmpty(status)) callbacks.setStatusText(status);
            if (running) {
                callbacks.setBusy(true, TextUtils.isEmpty(status) ? "Disassemble All DEX running in foreground..." : status);
                scheduleStatusPoll();
            } else if (!TextUtils.isEmpty(status)) {
                callbacks.setBusy(false, status);
                callbacks.refreshDexEntries();
            }
        } catch (Throwable ignored) {
        }
    }

    public void scheduleStatusPoll() {
        try {
            if (statusPoller != null) handler.removeCallbacks(statusPoller);
            statusPoller = () -> {
                try {
                    SharedPreferences sp = prefs();
                    boolean running = sp.getBoolean(PermsTestSmaliJobService.PREF_JOB_RUNNING, false);
                    String status = sp.getString(PermsTestSmaliJobService.PREF_JOB_STATUS, "");
                    if (!TextUtils.isEmpty(status)) callbacks.setStatusText(status);
                    if (running) {
                        callbacks.setBusy(true, TextUtils.isEmpty(status) ? "Disassemble All DEX running in foreground..." : status);
                        handler.postDelayed(statusPoller, 1000L);
                    } else {
                        callbacks.setBusy(false, TextUtils.isEmpty(status) ? "Disassemble All DEX finished." : status);
                        String log = sp.getString(PermsTestSmaliJobService.PREF_JOB_LOG, "");
                        if (!TextUtils.isEmpty(log)) callbacks.appendOutput(log.endsWith("\n") ? log : (log + "\n"));
                        sp.edit().putString(PermsTestSmaliJobService.PREF_JOB_LOG, "").apply();
                        callbacks.refreshDexEntries();
                        statusPoller = null;
                    }
                } catch (Throwable ignored) {
                    statusPoller = null;
                }
            };
            handler.postDelayed(statusPoller, 1000L);
        } catch (Throwable ignored) {
        }
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PermsTestSmaliJobService.PREFS, Context.MODE_PRIVATE);
    }
}
