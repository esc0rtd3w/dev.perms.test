package dev.perms.test.memory;

import android.content.Context;
import android.content.SharedPreferences;

import dev.perms.test.databinding.TabMemoryBinding;
import dev.perms.test.settings.SettingsPreferenceKeys;

/**
 * Binds persisted Memory-tab option checkboxes to shared Memory preferences.
 */
public final class MemoryOptionsBinder {
    private static boolean syncingDeviceDefaultOption;

    private MemoryOptionsBinder() {
    }

    public static void bind(Context context, TabMemoryBinding tab, SharedPreferences prefs, Runnable onTargetFilterChanged) {
        if (tab == null || prefs == null) return;

        tab.chkMemoryWithoutPtrace.setChecked(MemorySettings.shouldPatchWithoutPtrace(prefs));
        tab.chkMemoryUseOverlay.setChecked(MemorySettings.shouldUseOverlay(prefs));
        tab.chkMemoryOverlayTransparent.setChecked(MemorySettings.shouldOverlayTransparent(prefs));
        tab.chkMemoryOverlayResizable.setChecked(MemorySettings.shouldOverlayResizable(prefs));
        tab.chkMemoryDisableOverlaysVrCompatible.setChecked(MemorySettings.shouldDisableOverlaysForVrCompatible(context, prefs));
        tab.chkMemoryOnlyRunningPackages.setChecked(MemorySettings.shouldOnlyShowRunningPackages(prefs));
        tab.chkMemoryShowRunningPackages.setChecked(MemorySettings.shouldShowRunningPackages(prefs));
        tab.chkMemoryExcludeSelfPackage.setChecked(MemorySettings.shouldExcludeSelfPackage(prefs));
        tab.chkMemoryAutoStage.setChecked(MemorySettings.shouldAutoStage(prefs));
        tab.chkMemoryOverlayShowSessionButtons.setChecked(MemorySettings.shouldShowOverlaySessionButtons(prefs));
        tab.chkMemoryApplyPayloadsOnAttach.setChecked(MemorySettings.shouldApplyPayloadsOnAttach(prefs));
        tab.chkMemoryApplyPayloadsToAllMatches.setChecked(MemorySettings.shouldApplyPayloadsToAllMatches(prefs));
        tab.chkMemoryShowAllPatchedAddresses.setChecked(MemorySettings.shouldShowAllPatchedPayloadAddresses(prefs));

        tab.chkMemoryWithoutPtrace.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, isChecked).apply());
        tab.chkMemoryUseOverlay.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(MemoryToolHelper.KEY_USE_OVERLAY, isChecked).apply());
        tab.chkMemoryOverlayTransparent.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(MemoryToolHelper.KEY_OVERLAY_TRANSPARENT, isChecked).apply());
        tab.chkMemoryOverlayResizable.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(MemoryToolHelper.KEY_OVERLAY_RESIZABLE, isChecked).apply());
        tab.chkMemoryDisableOverlaysVrCompatible.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = prefs.edit()
                    .putBoolean(MemoryToolHelper.KEY_DISABLE_OVERLAYS_VR_COMPATIBLE, isChecked);
            if (!syncingDeviceDefaultOption) {
                editor.putBoolean(SettingsPreferenceKeys.DEVICE_DEFAULT_DISABLE_OVERLAYS_USER_SET, true);
            }
            editor.apply();
        });
        tab.chkMemoryOnlyRunningPackages.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(MemoryToolHelper.KEY_ONLY_RUNNING_PACKAGES, isChecked).apply();
            runTargetFilterChanged(onTargetFilterChanged);
        });
        tab.chkMemoryShowRunningPackages.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(MemoryToolHelper.KEY_SHOW_RUNNING_PACKAGES, isChecked).apply();
            runTargetFilterChanged(onTargetFilterChanged);
        });
        tab.chkMemoryExcludeSelfPackage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(MemoryToolHelper.KEY_EXCLUDE_SELF_PACKAGE, isChecked).apply();
            runTargetFilterChanged(onTargetFilterChanged);
        });
        tab.chkMemoryAutoStage.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(MemoryToolHelper.KEY_AUTO_STAGE, isChecked).apply());
        tab.chkMemoryOverlayShowSessionButtons.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(MemoryToolHelper.KEY_OVERLAY_SHOW_SESSION_BUTTONS, isChecked).apply());
        tab.chkMemoryApplyPayloadsOnAttach.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(MemoryToolHelper.KEY_APPLY_PAYLOADS_ON_ATTACH, isChecked).apply());
        tab.chkMemoryApplyPayloadsToAllMatches.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(MemoryToolHelper.KEY_APPLY_PAYLOADS_TO_ALL_MATCHES, isChecked).apply());
        tab.chkMemoryShowAllPatchedAddresses.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(MemoryToolHelper.KEY_SHOW_ALL_PATCHED_PAYLOAD_ADDRESSES, isChecked).apply());
    }

    public static void syncDisableOverlaysForDeviceDefaults(Context context, TabMemoryBinding tab, SharedPreferences prefs) {
        try {
            if (tab == null || prefs == null || tab.chkMemoryDisableOverlaysVrCompatible == null) return;
            syncingDeviceDefaultOption = true;
            try {
                tab.chkMemoryDisableOverlaysVrCompatible.setChecked(
                        MemorySettings.shouldDisableOverlaysForVrCompatible(context, prefs));
            } finally {
                syncingDeviceDefaultOption = false;
            }
        } catch (Throwable ignored) {
            syncingDeviceDefaultOption = false;
        }
    }

    private static void runTargetFilterChanged(Runnable callback) {
        try {
            if (callback != null) callback.run();
        } catch (Throwable ignored) {
        }
    }
}
