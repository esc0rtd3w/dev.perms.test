package dev.perms.test.logging;

import android.content.SharedPreferences;
import android.view.View;

import dev.perms.test.settings.SettingsPreferenceKeys;

import dev.perms.test.databinding.TabLoggingBinding;
import dev.perms.test.databinding.TabSettingsBinding;

/**
 * Wires Logging tab controls to Activity-owned actions.
 *
 * The Activity still owns command execution, output state, and lifecycle.  This helper keeps
 * Logging UI binding in the logging package so MainActivity does not collect more tab-specific
 * button wiring.
 */
public final class LoggingTabBinder {
    private LoggingTabBinder() {}

    public static boolean bind(TabLoggingBinding logging,
                               TabSettingsBinding settings,
                               SharedPreferences prefs,
                               String lifetimeLogKey,
                               boolean lifetimeLogEnabled,
                               LifetimeLogEnabledCallback lifetimeCallback,
                               Runnable showLifetimeLog,
                               Runnable exportLifetimeLog,
                               Runnable clearLifetimeLog,
                               Runnable markLifetimeSession,
                               Runnable archiveLogs,
                               Runnable clearLogs,
                               Runnable runLogcat,
                               Runnable runErrorLogcat,
                               Runnable clearLogcat,
                               Runnable runAllLogcat,
                               Runnable runRadioLogcat,
                               Runnable runEventsLogcat,
                               Runnable runCrashLogcat,
                               Runnable runThreadtimeLogcat,
                               Runnable runBufferInfo,
                               Runnable runAppFocusLog,
                               Runnable runAppFullLog,
                               Runnable runStartupNetworkLog,
                               Runnable runAnrDeepLog,
                               Runnable runAnrLog,
                               Runnable runDropboxLog,
                               Runnable runActivityLog,
                               Runnable runSystemStateLog,
                               Runnable runMemCpuLog,
                               Runnable runServicesLog,
                               Runnable runNotificationsLog,
                               Runnable runMemoryToolsLog,
                               Runnable saveOutput,
                               Runnable fullDiagnostic,
                               Runnable shareLastSavedFile,
                               Runnable checkRootDiagnostics,
                               Runnable backupRootDiagnostics,
                               Runnable clearRootDiagnostics) {
        boolean rootFeaturesEnabled = prefs != null
                && prefs.getBoolean(SettingsPreferenceKeys.ENABLE_ROOT_FEATURES, false);
        applyRootDiagnosticsGate(logging, rootFeaturesEnabled);
        setLifetimeControlsEnabled(logging, lifetimeLogEnabled);
        if (settings != null && settings.chkLifetimeLog != null) {
            settings.chkLifetimeLog.setChecked(lifetimeLogEnabled);
            settings.chkLifetimeLog.setOnCheckedChangeListener((buttonView, isChecked) -> {
                setLifetimeControlsEnabled(logging, isChecked);
                if (lifetimeCallback != null) lifetimeCallback.setEnabled(isChecked);
                if (prefs != null && lifetimeLogKey != null) {
                    prefs.edit().putBoolean(lifetimeLogKey, isChecked).apply();
                }
            });
        }

        if (logging != null) {
            if (logging.btnLifetimeView != null) logging.btnLifetimeView.setOnClickListener(v -> run(showLifetimeLog));
            if (logging.btnLifetimeExport != null) logging.btnLifetimeExport.setOnClickListener(v -> run(exportLifetimeLog));
            if (logging.btnLifetimeClear != null) logging.btnLifetimeClear.setOnClickListener(v -> run(clearLifetimeLog));
            if (logging.btnLifetimeMark != null) logging.btnLifetimeMark.setOnClickListener(v -> run(markLifetimeSession));
            if (logging.btnArchiveLogs != null) logging.btnArchiveLogs.setOnClickListener(v -> run(archiveLogs));
            if (logging.btnClearLogs != null) logging.btnClearLogs.setOnClickListener(v -> run(clearLogs));

            if (logging.btnLogcat != null) logging.btnLogcat.setOnClickListener(v -> run(runLogcat));
            if (logging.btnLogcatErr != null) logging.btnLogcatErr.setOnClickListener(v -> run(runErrorLogcat));
            if (logging.btnLogcatClear != null) logging.btnLogcatClear.setOnClickListener(v -> run(clearLogcat));
            if (logging.btnLogcatAll != null) logging.btnLogcatAll.setOnClickListener(v -> run(runAllLogcat));
            if (logging.btnLogcatRadio != null) logging.btnLogcatRadio.setOnClickListener(v -> run(runRadioLogcat));
            if (logging.btnLogcatEvents != null) logging.btnLogcatEvents.setOnClickListener(v -> run(runEventsLogcat));
            if (logging.btnLogcatCrash != null) logging.btnLogcatCrash.setOnClickListener(v -> run(runCrashLogcat));
            if (logging.btnLogcatThreadtime != null) logging.btnLogcatThreadtime.setOnClickListener(v -> run(runThreadtimeLogcat));
            if (logging.btnLogcatBuffers != null) logging.btnLogcatBuffers.setOnClickListener(v -> run(runBufferInfo));
            if (logging.btnLogcatApp != null) logging.btnLogcatApp.setOnClickListener(v -> run(runAppFocusLog));
            if (logging.btnLogcatAppFull != null) logging.btnLogcatAppFull.setOnClickListener(v -> run(runAppFullLog));
            if (logging.btnLogcatStartupNetwork != null) logging.btnLogcatStartupNetwork.setOnClickListener(v -> run(runStartupNetworkLog));
            if (logging.btnLogcatAnrDeep != null) logging.btnLogcatAnrDeep.setOnClickListener(v -> run(runAnrDeepLog));
            if (logging.btnLogcatAnr != null) logging.btnLogcatAnr.setOnClickListener(v -> run(runAnrLog));
            if (logging.btnLogcatDropbox != null) logging.btnLogcatDropbox.setOnClickListener(v -> run(runDropboxLog));
            if (logging.btnLogcatActivity != null) logging.btnLogcatActivity.setOnClickListener(v -> run(runActivityLog));
            if (logging.btnLogcatSystemState != null) logging.btnLogcatSystemState.setOnClickListener(v -> run(runSystemStateLog));
            if (logging.btnLogcatMemCpu != null) logging.btnLogcatMemCpu.setOnClickListener(v -> run(runMemCpuLog));
            if (logging.btnLogcatServices != null) logging.btnLogcatServices.setOnClickListener(v -> run(runServicesLog));
            if (logging.btnLogcatNotifications != null) logging.btnLogcatNotifications.setOnClickListener(v -> run(runNotificationsLog));
            if (logging.btnLogcatMemoryTools != null) logging.btnLogcatMemoryTools.setOnClickListener(v -> run(runMemoryToolsLog));
            if (logging.btnSaveOutput != null) logging.btnSaveOutput.setOnClickListener(v -> run(saveOutput));
            if (logging.btnFullDiagnostic != null) logging.btnFullDiagnostic.setOnClickListener(v -> run(fullDiagnostic));
            if (logging.btnShareLast != null) logging.btnShareLast.setOnClickListener(v -> run(shareLastSavedFile));
            if (logging.btnRootDiagnosticsCheck != null) logging.btnRootDiagnosticsCheck.setOnClickListener(v -> run(checkRootDiagnostics));
            if (logging.btnRootDiagnosticsBackup != null) logging.btnRootDiagnosticsBackup.setOnClickListener(v -> run(backupRootDiagnostics));
            if (logging.btnRootDiagnosticsClear != null) logging.btnRootDiagnosticsClear.setOnClickListener(v -> run(clearRootDiagnostics));
        }
        return lifetimeLogEnabled;
    }


    public static void applyRootDiagnosticsGate(TabLoggingBinding logging, boolean enabled) {
        if (logging == null) return;
        if (logging.cardLoggingRootDiagnostics != null) {
            logging.cardLoggingRootDiagnostics.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (logging.txtRootDiagnosticsStatus != null) {
            logging.txtRootDiagnosticsStatus.setText(enabled
                    ? "Root diagnostics: root not checked; actions request root when tapped."
                    : "Root diagnostics: disabled in Settings.");
        }
        if (logging.btnRootDiagnosticsCheck != null) logging.btnRootDiagnosticsCheck.setEnabled(enabled);
        if (logging.btnRootDiagnosticsBackup != null) logging.btnRootDiagnosticsBackup.setEnabled(enabled);
        if (logging.btnRootDiagnosticsClear != null) logging.btnRootDiagnosticsClear.setEnabled(enabled);
    }

    private static void setLifetimeControlsEnabled(TabLoggingBinding logging, boolean enabled) {
        if (logging == null) return;
        if (logging.btnLifetimeView != null) logging.btnLifetimeView.setEnabled(enabled);
        if (logging.btnLifetimeExport != null) logging.btnLifetimeExport.setEnabled(enabled);
        if (logging.btnLifetimeClear != null) logging.btnLifetimeClear.setEnabled(enabled);
        if (logging.btnLifetimeMark != null) logging.btnLifetimeMark.setEnabled(enabled);
    }

    private static void run(Runnable action) {
        if (action != null) action.run();
    }

    public interface LifetimeLogEnabledCallback {
        void setEnabled(boolean enabled);
    }
}
