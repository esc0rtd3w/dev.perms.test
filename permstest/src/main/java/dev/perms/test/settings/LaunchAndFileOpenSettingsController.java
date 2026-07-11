package dev.perms.test.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicInteger;

import androidx.appcompat.app.AppCompatActivity;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.kiosk.KioskLauncherActivity;
import dev.perms.test.kiosk.KioskPrefs;
import dev.perms.test.kiosk.KioskSafety;
import dev.perms.test.kiosk.KioskSettingsStore;

/**
 * Owns the Main/Settings controls that change launcher aliases and file-open routing.
 */
public final class LaunchAndFileOpenSettingsController {
    public interface Host {
        void appendOutput(String text);
        boolean isDebugOutputEnabled();
        void debugOutput(String area, String message);
    }

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final Handler mainHandler;
    private final Class<?> relaunchActivityClass;
    private final Host host;
    private final SharedPreferences prefs;
    private final KioskSettingsStore kioskStore;
    private final AtomicInteger launcherApplyGeneration = new AtomicInteger();
    private boolean applyingUiState;

    public LaunchAndFileOpenSettingsController(AppCompatActivity activity,
                                               ActivityMainBinding binding,
                                               Handler mainHandler,
                                               Class<?> relaunchActivityClass,
                                               Host host) {
        this.activity = activity;
        this.binding = binding;
        this.mainHandler = mainHandler;
        this.relaunchActivityClass = relaunchActivityClass;
        this.host = host;
        this.prefs = activity.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE);
        this.kioskStore = new KioskSettingsStore(activity);
    }

    public void setup() {
        KioskSafety.clearLauncherPreferenceAndAliasIfForced(activity);
        KioskSafety.clearKioskPreferenceIfForced(activity);
        boolean mode = prefs.getBoolean(SettingsPreferenceKeys.RUN_AS_LAUNCHER, false) && !kioskStore.isLauncherForceDisabled();
        boolean kioskMode = kioskStore.isKioskEnabled();
        boolean autoRestart = prefs.getBoolean(SettingsPreferenceKeys.AUTO_RESTART_LAUNCHER, true);
        boolean fileOpen = prefs.getBoolean(SettingsPreferenceKeys.ENABLE_FILE_OPEN_HANDLER, true);
        boolean hideFileOpenUi = prefs.getBoolean(SettingsPreferenceKeys.HIDE_FILE_OPEN_UI, true);
        boolean showFileOpenDoneOpen = prefs.getBoolean(SettingsPreferenceKeys.SHOW_FILE_OPEN_DONE_OPEN, true);
        boolean confirmFileOpenInstall = prefs.getBoolean(SettingsPreferenceKeys.CONFIRM_FILE_OPEN_INSTALL, true);
        boolean useAndroidDataInstallPath = prefs.getBoolean(SettingsPreferenceKeys.INSTALL_USE_ANDROID_DATA_PATH, false);
        boolean useInstallStagingFolder = prefs.getBoolean(SettingsPreferenceKeys.INSTALL_USE_STAGING_FOLDER, false);
        boolean skipInstallStagingLargeFiles = prefs.getBoolean(SettingsPreferenceKeys.INSTALL_SKIP_STAGING_LARGE_FILES, false);
        boolean lowTargetSdkBypassSupported = isLowTargetSdkBypassSupported();
        boolean bypassLowTargetSdkBlock = lowTargetSdkBypassSupported
                && prefs.getBoolean(SettingsPreferenceKeys.INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK, true);
        boolean ignoreDexoptProfile = prefs.getBoolean(SettingsPreferenceKeys.INSTALL_IGNORE_DEXOPT_PROFILE, true);
        boolean allowDowngrade = prefs.getBoolean(SettingsPreferenceKeys.INSTALL_ALLOW_DOWNGRADE, false);
        boolean splitApkShowWarningDialog = prefs.getBoolean(SettingsPreferenceKeys.SPLIT_APK_SHOW_WARNING_DIALOG, false);
        boolean customSplitOptions = prefs.getBoolean(SettingsPreferenceKeys.CUSTOM_SPLIT_OPTIONS, true);

        applyingUiState = true;
        binding.tabMain.chkRunAsLauncher.setChecked(mode);
        binding.tabMain.chkKioskMode.setChecked(kioskMode);
        binding.tabMain.chkAutoRestartLauncher.setChecked(autoRestart);
        applyingUiState = false;
        if (binding.tabSettings.chkEnableFileOpenHandler != null) binding.tabSettings.chkEnableFileOpenHandler.setChecked(fileOpen);
        if (binding.tabSettings.chkHideFileOpenUi != null) binding.tabSettings.chkHideFileOpenUi.setChecked(hideFileOpenUi);
        if (binding.tabSettings.chkShowFileOpenDoneOpen != null) binding.tabSettings.chkShowFileOpenDoneOpen.setChecked(showFileOpenDoneOpen);
        if (binding.tabSettings.chkConfirmFileOpenInstall != null) binding.tabSettings.chkConfirmFileOpenInstall.setChecked(confirmFileOpenInstall);
        if (binding.tabSettings.chkInstallUseAndroidDataPath != null) binding.tabSettings.chkInstallUseAndroidDataPath.setChecked(useAndroidDataInstallPath);
        if (binding.tabSettings.chkInstallUseStagingFolder != null) binding.tabSettings.chkInstallUseStagingFolder.setChecked(useInstallStagingFolder);
        if (binding.tabSettings.chkInstallSkipStagingLargeFiles != null) binding.tabSettings.chkInstallSkipStagingLargeFiles.setChecked(skipInstallStagingLargeFiles);
        configureLowTargetSdkBypassCheckbox(lowTargetSdkBypassSupported, bypassLowTargetSdkBlock);
        if (binding.tabSettings.chkInstallIgnoreDexoptProfile != null) binding.tabSettings.chkInstallIgnoreDexoptProfile.setChecked(ignoreDexoptProfile);
        if (binding.tabSettings.chkInstallAllowDowngrade != null) binding.tabSettings.chkInstallAllowDowngrade.setChecked(allowDowngrade);
        if (binding.tabSettings.chkSplitApkShowWarningDialog != null) binding.tabSettings.chkSplitApkShowWarningDialog.setChecked(splitApkShowWarningDialog);
        if (binding.tabSettings.chkCustomSplitOptions != null) binding.tabSettings.chkCustomSplitOptions.setChecked(customSplitOptions);

        applyLauncherMode(mode);
        applyFileOpenHandlerEnabled(fileOpen);

        binding.tabMain.chkAutoRestartLauncher.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applyingUiState) return;
            prefs.edit().putBoolean(SettingsPreferenceKeys.AUTO_RESTART_LAUNCHER, isChecked).apply();
            debugOutput("home", "auto-restart launcher setting=" + isChecked);
        });

        binding.tabMain.chkKioskMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applyingUiState) return;
            if (isChecked && kioskStore.isKioskForceDisabled()) {
                kioskStore.setKioskEnabled(false);
                applyingUiState = true;
                buttonView.setChecked(false);
                applyingUiState = false;
                String msg = "Kiosk Mode is force-disabled because " + KioskSafety.kioskOffPath() + " exists.";
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                debugOutput("home", msg);
                appendOutput("[Kiosk] " + msg + "\n");
                return;
            }
            if (isChecked && !kioskStore.hasEnabledAllowedItems()) {
                kioskStore.setKioskEnabled(false);
                applyingUiState = true;
                buttonView.setChecked(false);
                applyingUiState = false;
                String msg = "Add and enable at least one kiosk app or shortcut before enabling Kiosk Mode.";
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                debugOutput("home", msg);
                appendOutput("[Kiosk] " + msg + "\n");
                try { binding.tabMain.txtStatus.setText(binding.tabMain.txtStatus.getText() + "\n" + msg); } catch (Throwable ignored) {}
                return;
            }
            kioskStore.setKioskEnabled(isChecked);
            debugOutput("home", "Kiosk Mode checkbox=" + isChecked + ", enabledItems=" + kioskStore.enabledAllowedItemCount());
            String msg;
            if (isChecked) {
                msg = binding.tabMain.chkRunAsLauncher.isChecked()
                        ? "Kiosk Mode enabled. Opening kiosk view now; HOME will also use kiosk mode."
                        : "Kiosk Mode enabled. Opening kiosk view now. Run as Home Launcher only controls the Android HOME button.";
            } else {
                msg = "Kiosk Mode disabled. HOME returns to the normal PermsTest launcher behavior.";
            }
            try { binding.tabMain.txtStatus.setText(binding.tabMain.txtStatus.getText() + "\n" + msg); } catch (Throwable ignored) {}
            if (isChecked) openKioskSoon();
        });

        if (binding.tabSettings.chkEnableFileOpenHandler != null) {
            binding.tabSettings.chkEnableFileOpenHandler.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(SettingsPreferenceKeys.ENABLE_FILE_OPEN_HANDLER, isChecked).apply();
                applyFileOpenHandlerEnabled(isChecked);
            });
        }

        bindBooleanSetting(binding.tabSettings.chkHideFileOpenUi, SettingsPreferenceKeys.HIDE_FILE_OPEN_UI);
        bindBooleanSetting(binding.tabSettings.chkShowFileOpenDoneOpen, SettingsPreferenceKeys.SHOW_FILE_OPEN_DONE_OPEN);
        bindBooleanSetting(binding.tabSettings.chkConfirmFileOpenInstall, SettingsPreferenceKeys.CONFIRM_FILE_OPEN_INSTALL);
        bindBooleanSetting(binding.tabSettings.chkInstallUseAndroidDataPath, SettingsPreferenceKeys.INSTALL_USE_ANDROID_DATA_PATH);
        bindBooleanSetting(binding.tabSettings.chkInstallUseStagingFolder, SettingsPreferenceKeys.INSTALL_USE_STAGING_FOLDER);
        bindBooleanSetting(binding.tabSettings.chkInstallSkipStagingLargeFiles, SettingsPreferenceKeys.INSTALL_SKIP_STAGING_LARGE_FILES);
        if (lowTargetSdkBypassSupported) {
            bindBooleanSetting(binding.tabSettings.chkInstallBypassLowTargetSdkBlock, SettingsPreferenceKeys.INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK);
        }
        bindBooleanSetting(binding.tabSettings.chkInstallIgnoreDexoptProfile, SettingsPreferenceKeys.INSTALL_IGNORE_DEXOPT_PROFILE);
        bindBooleanSetting(binding.tabSettings.chkInstallAllowDowngrade, SettingsPreferenceKeys.INSTALL_ALLOW_DOWNGRADE);
        bindBooleanSetting(binding.tabSettings.chkSplitApkShowWarningDialog, SettingsPreferenceKeys.SPLIT_APK_SHOW_WARNING_DIALOG);
        bindBooleanSetting(binding.tabSettings.chkCustomSplitOptions, SettingsPreferenceKeys.CUSTOM_SPLIT_OPTIONS);

        binding.tabMain.chkRunAsLauncher.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applyingUiState) return;
            if (isChecked && kioskStore.isLauncherForceDisabled()) {
                prefs.edit()
                        .putBoolean(SettingsPreferenceKeys.RUN_AS_LAUNCHER, false)
                        .putBoolean(SettingsPreferenceKeys.PENDING_OPEN_HOME_SETTINGS, false)
                        .apply();
                applyLauncherMode(false);
                applyingUiState = true;
                buttonView.setChecked(false);
                applyingUiState = false;
                String msg = "Run as Home Launcher is force-disabled because " + KioskSafety.launcherOffPath() + " exists.";
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                debugOutput("home", msg);
                appendOutput("[Kiosk] " + msg + "\n");
                return;
            }
            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(SettingsPreferenceKeys.RUN_AS_LAUNCHER, isChecked);
            ed.putBoolean(SettingsPreferenceKeys.PENDING_OPEN_HOME_SETTINGS, isChecked);
            ed.apply();

            debugOutput("home", "Run as Home Launcher checkbox=" + isChecked);
            applyLauncherMode(isChecked);
            if (isChecked) {
                openHomeSettingsSoon(450);
            } else {
                prefs.edit().putBoolean(SettingsPreferenceKeys.PENDING_OPEN_HOME_SETTINGS, false).apply();
                appendOutput("[i] Run as Home Launcher disabled. Normal app launcher remains available; no restart needed.\n");
            }
        });
    }

    public void syncLauncherAndKioskCheckboxesFromPrefs() {
        try {
            if (binding == null || binding.tabMain == null) return;
            boolean launcher = prefs.getBoolean(SettingsPreferenceKeys.RUN_AS_LAUNCHER, false)
                    && !kioskStore.isLauncherForceDisabled();
            boolean kiosk = kioskStore.isKioskEnabled();
            applyingUiState = true;
            if (binding.tabMain.chkRunAsLauncher != null) binding.tabMain.chkRunAsLauncher.setChecked(launcher);
            if (binding.tabMain.chkKioskMode != null) binding.tabMain.chkKioskMode.setChecked(kiosk);
            applyingUiState = false;
            debugOutput("home", "synced launcher checkbox=" + launcher + ", kiosk checkbox=" + kiosk);
        } catch (Throwable t) {
            applyingUiState = false;
            debugOutput("home", "checkbox sync failed: " + t);
        }
    }

    public void maybeOpenHomeSettingsAfterModeSwitch() {
        try {
            if (!prefs.getBoolean(SettingsPreferenceKeys.PENDING_OPEN_HOME_SETTINGS, false)) return;
            if (kioskStore.isLauncherForceDisabled() || !prefs.getBoolean(SettingsPreferenceKeys.RUN_AS_LAUNCHER, false)) {
                prefs.edit().putBoolean(SettingsPreferenceKeys.PENDING_OPEN_HOME_SETTINGS, false).apply();
                return;
            }

            openHomeSettingsSoon(700);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isLowTargetSdkBypassSupported() {
        return Build.VERSION.SDK_INT >= 35;
    }

    private void configureLowTargetSdkBypassCheckbox(boolean supported, boolean checked) {
        if (binding.tabSettings.chkInstallBypassLowTargetSdkBlock == null) return;
        binding.tabSettings.chkInstallBypassLowTargetSdkBlock.setOnCheckedChangeListener(null);
        binding.tabSettings.chkInstallBypassLowTargetSdkBlock.setChecked(supported && checked);
        binding.tabSettings.chkInstallBypassLowTargetSdkBlock.setEnabled(supported);
        binding.tabSettings.chkInstallBypassLowTargetSdkBlock.setAlpha(supported ? 1f : 0.55f);
        binding.tabSettings.chkInstallBypassLowTargetSdkBlock.setText(supported
                ? "Bypass low target SDK block (Android 15+)"
                : "Bypass low target SDK block (Android 15+; not needed here)");
    }

    private void openHomeSettingsSoon(long delayMs) {
        mainHandler.postDelayed(() -> {
            Throwable firstFailure = null;
            try {
                appendOutput("[i] Opening Home app settings...\n");
                Toast.makeText(activity, "Opening Home app settings...", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(Settings.ACTION_HOME_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(i);
                prefs.edit().putBoolean(SettingsPreferenceKeys.PENDING_OPEN_HOME_SETTINGS, false).apply();
                return;
            } catch (Throwable t) {
                firstFailure = t;
            }

            try {
                Intent i2 = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(i2);
                prefs.edit().putBoolean(SettingsPreferenceKeys.PENDING_OPEN_HOME_SETTINGS, false).apply();
            } catch (Throwable t) {
                appendOutput("[!] Could not open HOME settings: " + firstFailure + " / " + t + "\n");
            }
        }, delayMs);
    }

    private void openKioskSoon() {
        mainHandler.postDelayed(() -> {
            try {
                Intent i = new Intent(activity, KioskLauncherActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(i);
            } catch (Throwable t) {
                appendOutput("[!] Failed to open Kiosk Mode: " + t + "\n");
                Toast.makeText(activity, "Failed to open Kiosk Mode: " + t, Toast.LENGTH_LONG).show();
            }
        }, 250);
    }

    private void bindBooleanSetting(com.google.android.material.checkbox.MaterialCheckBox checkBox, String key) {
        if (checkBox == null) return;
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(key, isChecked).apply());
    }

    private void restartSelfSoon() {
        try {
            appendOutput("[i] Restarting to apply mode change...\n");
            Toast.makeText(activity, "Restarting...", Toast.LENGTH_SHORT).show();

            mainHandler.postDelayed(() -> {
                try {
                    Class<?> targetClass = (relaunchActivityClass != null) ? relaunchActivityClass : activity.getClass();
                    Intent i = new Intent(activity, targetClass);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(i);
                    activity.finish();
                } catch (Throwable t) {
                    appendOutput("[!] Restart launch failed: " + t + "\n");
                }
            }, 350);
        } catch (Throwable t) {
            appendOutput("[!] Restart failed: " + t + "\n");
        }
    }

    private void applyLauncherMode(boolean runAsLauncher) {
        boolean effectiveRunAsLauncher = runAsLauncher && !kioskStore.isLauncherForceDisabled();
        if (runAsLauncher && !effectiveRunAsLauncher) {
            prefs.edit()
                    .putBoolean(SettingsPreferenceKeys.RUN_AS_LAUNCHER, false)
                    .putBoolean(SettingsPreferenceKeys.PENDING_OPEN_HOME_SETTINGS, false)
                    .apply();
        }

        final int generation = launcherApplyGeneration.incrementAndGet();
        final boolean requestedRunAsLauncher = runAsLauncher;
        final boolean effective = effectiveRunAsLauncher;
        Thread worker = new Thread(() -> {
            Throwable error = null;
            try {
                PackageManager pm = activity.getApplicationContext().getPackageManager();
                String packageName = activity.getPackageName();
                ComponentName launcherAlias = new ComponentName(packageName, packageName + ".LauncherAlias");
                ComponentName homeAlias = new ComponentName(packageName, packageName + ".HomeAlias");

                int enable = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                int disable = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

                // Keep the normal launcher icon enabled so PermsTest can always be opened
                // directly, even when the optional HOME alias is enabled and another app
                // is currently selected as Android's default Home app.
                pm.setComponentEnabledSetting(launcherAlias, enable, PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(homeAlias, effective ? enable : disable, PackageManager.DONT_KILL_APP);
            } catch (Throwable t) {
                error = t;
            }

            final Throwable finalError = error;
            mainHandler.post(() -> {
                if (generation != launcherApplyGeneration.get()) return;
                if (finalError != null) {
                    appendOutput("[!] Failed to toggle launcher mode: " + finalError + "\n");
                    return;
                }
                String msg;
                if (requestedRunAsLauncher && !effective) {
                    msg = "Home launcher mode force-disabled by " + KioskSafety.launcherOffPath() + ".";
                } else {
                    msg = effective
                            ? "Home launcher mode enabled. Go to Settings → Apps → Default apps → Home app to select it."
                            : "Normal app mode enabled.";
                }
                try {
                    applyingUiState = true;
                    if (binding != null && binding.tabMain != null && binding.tabMain.chkRunAsLauncher != null) {
                        binding.tabMain.chkRunAsLauncher.setChecked(effective);
                    }
                    applyingUiState = false;
                } catch (Throwable ignored) {
                    applyingUiState = false;
                }
                debugOutput("home", "launcher alias apply complete: requested=" + requestedRunAsLauncher + ", effective=" + effective);
                try { binding.tabMain.txtStatus.setText(binding.tabMain.txtStatus.getText() + "\n" + msg); } catch (Throwable ignored) {}
            });
        }, "PermsTestLauncherModeApply");
        worker.setDaemon(true);
        worker.start();
    }

    private void applyFileOpenHandlerEnabled(boolean enabled) {
        try {
            String packageName = activity.getPackageName();
            ComponentName cn = new ComponentName(packageName, packageName + ".FileOpenAlias");
            activity.getPackageManager().setComponentEnabledSetting(
                    cn,
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );
        } catch (Throwable ignored) {
        }
    }

    private void appendOutput(String text) {
        if (host != null) host.appendOutput(text);
    }

    private boolean isDebugOutputEnabled() {
        try {
            return host != null && host.isDebugOutputEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void debugOutput(String area, String message) {
        if (!isDebugOutputEnabled()) return;
        try {
            if (host != null) host.debugOutput(area, message);
        } catch (Throwable ignored) {
        }
    }
}
