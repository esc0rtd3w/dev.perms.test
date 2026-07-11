package dev.perms.test.packages.editor.jobs;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

import dev.perms.test.service.LongOperationStatusStore;

import java.io.File;

/** Activity-side launcher/poller for APK Editor foreground jobs. */
public final class ApkEditorJobClient {
    private final Context context;
    private final LongOperationStatusStore smaliStatus;

    public ApkEditorJobClient(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
        this.smaliStatus = new LongOperationStatusStore(this.context, ApkEditorJobService.PREFS, ApkEditorJobService.STATUS_PREFIX_SMALI);
    }

    public void startSmaliDecompile(File stagedPackage,
                                    File workspace,
                                    String sourceLabel,
                                    boolean splitAware,
                                    int apiLevel) {
        if (context == null) return;
        smaliStatus.start("Starting foreground smali decompile service...",
                "[APK Editor] Starting foreground smali decompile service.\n");
        Intent svc = new Intent(context, ApkEditorJobService.class);
        svc.setAction(ApkEditorJobService.ACTION_SMALI_DECOMPILE);
        svc.putExtra(ApkEditorJobService.EXTRA_STAGED_PACKAGE, stagedPackage == null ? "" : stagedPackage.getAbsolutePath());
        svc.putExtra(ApkEditorJobService.EXTRA_WORKSPACE, workspace == null ? "" : workspace.getAbsolutePath());
        svc.putExtra(ApkEditorJobService.EXTRA_SOURCE_LABEL, sourceLabel == null ? "" : sourceLabel);
        svc.putExtra(ApkEditorJobService.EXTRA_SPLIT_AWARE, splitAware);
        svc.putExtra(ApkEditorJobService.EXTRA_API_LEVEL, apiLevel);
        try {
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, svc);
            else context.startService(svc);
        } catch (RuntimeException e) {
            smaliStatus.finish(false, "Smali decompile service start failed.",
                    "[APK Editor] Service start failed: " + e.getMessage() + "\n", "", e.toString());
            throw e;
        }
    }

    public LongOperationStatusStore.Snapshot snapshot(boolean consumeLog) {
        return smaliStatus.snapshot(consumeLog);
    }
}
