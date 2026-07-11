package dev.perms.test.packages.editor.jobs;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import dev.perms.test.MainActivity;
import dev.perms.test.packages.editor.ApkEditorArchiveInspector;
import dev.perms.test.packages.editor.ApkEditorSmaliWorkspace;
import dev.perms.test.service.LongOperationForegroundHelper;
import dev.perms.test.service.LongOperationStatusStore;

import java.io.File;

/**
 * Foreground APK Editor jobs.
 *
 * This service is intentionally narrow for now: APK Editor gets a service-backed smali decompile
 * path without changing the established FTP, HTTP, Memory overlay, or Debugging job services.
 * New long APK Editor actions can be added as explicit actions once their argument/result shape is
 * proven stable.
 */
public final class ApkEditorJobService extends Service {
    public static final String ACTION_SMALI_DECOMPILE = "dev.perms.test.action.APK_EDITOR_SMALI_DECOMPILE";
    public static final String ACTION_STOP = "dev.perms.test.action.STOP_APK_EDITOR_JOB";

    public static final String EXTRA_STAGED_PACKAGE = "staged_package";
    public static final String EXTRA_WORKSPACE = "workspace";
    public static final String EXTRA_SOURCE_LABEL = "source_label";
    public static final String EXTRA_SPLIT_AWARE = "split_aware";
    public static final String EXTRA_API_LEVEL = "api_level";

    public static final String PREFS = "perms_test";
    public static final String STATUS_PREFIX_SMALI = "apk_editor_smali_decompile";

    private static final String CHANNEL_ID = "apk_editor_jobs_v1";
    private static final int NOTIFICATION_ID = 70531;

    private volatile boolean stopRequested;
    private Thread worker;
    private LongOperationStatusStore smaliStatus;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SMALI_DECOMPILE : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopRequested = true;
            status().update("Stopping APK Editor job...", "[APK Editor] Stop requested.\n");
            updateNotification("Stopping APK Editor job...");
            if (worker == null || !worker.isAlive()) {
                status().markStopped("APK Editor job stopped.");
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        if (worker != null && worker.isAlive()) {
            status().update("APK Editor job is already running...", "[APK Editor] Another APK Editor job is already running.\n");
            updateNotification("APK Editor job is already running...");
            return START_NOT_STICKY;
        }

        startForegroundCompat("Starting APK Editor smali decompile...");
        startSmaliDecompile(intent);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopRequested = true;
        super.onDestroy();
    }

    private synchronized void startSmaliDecompile(Intent intent) {
        stopRequested = false;
        final String stagedPath = intent == null ? "" : intent.getStringExtra(EXTRA_STAGED_PACKAGE);
        final String workspacePath = intent == null ? "" : intent.getStringExtra(EXTRA_WORKSPACE);
        final String sourceLabel = intent == null ? "" : intent.getStringExtra(EXTRA_SOURCE_LABEL);
        final boolean splitAware = intent == null || intent.getBooleanExtra(EXTRA_SPLIT_AWARE, true);
        final int apiLevel = intent == null ? Build.VERSION.SDK_INT : intent.getIntExtra(EXTRA_API_LEVEL, Build.VERSION.SDK_INT);

        status().start("Starting APK Editor smali decompile...", "[APK Editor] Foreground smali decompile started.\n");
        worker = new Thread(() -> runSmaliDecompile(stagedPath, workspacePath, sourceLabel, splitAware, apiLevel), "PermsTest-ApkEditorSmali");
        worker.start();
    }

    private void runSmaliDecompile(String stagedPath, String workspacePath, String sourceLabel, boolean splitAware, int apiLevel) {
        try {
            if (TextUtils.isEmpty(stagedPath)) throw new IllegalArgumentException("No staged package path was provided.");
            if (TextUtils.isEmpty(workspacePath)) throw new IllegalArgumentException("No APK Editor workspace path was provided.");
            File staged = new File(stagedPath);
            File workspace = new File(workspacePath);
            if (!staged.isFile()) throw new IllegalArgumentException("Staged package is missing: " + stagedPath);

            String label = TextUtils.isEmpty(sourceLabel) ? staged.getName() : sourceLabel;
            String start = "Decompiling smali from " + label + "...";
            status().update(start, "[APK Editor] Source: " + staged.getAbsolutePath() + "\n");
            updateNotification(start);

            ApkEditorSmaliWorkspace.Workspace result = ApkEditorSmaliWorkspace.disassemble(
                    this,
                    staged,
                    workspace,
                    splitAware,
                    apiLevel,
                    new ApkEditorSmaliWorkspace.ProgressCallback() {
                        @Override
                        public void onProgress(String statusText, String logLine) {
                            status().update(statusText, logLine);
                            updateNotification(statusText);
                        }

                        @Override
                        public boolean isStopRequested() {
                            return stopRequested;
                        }
                    });

            String done = "Smali workspace ready: " + result.dexEntries.size()
                    + (result.dexEntries.size() == 1 ? " DEX file" : " DEX files") + ".";
            status().finish(true, done, result.report, result.workspaceRoot.getAbsolutePath(), "");
            updateNotification(done);
        } catch (Throwable t) {
            String message = ApkEditorArchiveInspector.shortError(t);
            status().finish(false, "APK Editor smali decompile failed. Check log.", "[APK Editor] Failed: " + message + "\n", "", message);
            updateNotification("APK Editor smali decompile failed.");
        } finally {
            stopSelf();
        }
    }

    private LongOperationStatusStore status() {
        if (smaliStatus == null) {
            smaliStatus = new LongOperationStatusStore(this, PREFS, STATUS_PREFIX_SMALI);
        }
        return smaliStatus;
    }

    private void startForegroundCompat(String status) {
        LongOperationForegroundHelper.startDataSyncForeground(this, NOTIFICATION_ID, buildNotification(status));
    }

    private Notification buildNotification(String status) {
        LongOperationForegroundHelper.ensureChannel(this, CHANNEL_ID, "APK Editor jobs", "Long-running PermsTest APK Editor jobs");
        Intent open = new Intent(this, MainActivity.class);
        Intent stop = new Intent(this, ApkEditorJobService.class);
        stop.setAction(ACTION_STOP);
        return LongOperationForegroundHelper.buildNotification(this,
                CHANNEL_ID,
                "PermsTest APK Editor",
                status,
                open,
                stop,
                1);
    }

    private void updateNotification(String status) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(status));
        } catch (Throwable ignored) {
        }
    }
}
