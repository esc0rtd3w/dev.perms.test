package dev.perms.test.settings;

import android.content.Context;
import android.content.SharedPreferences;

import dev.perms.test.ExecMode;
import dev.perms.test.kiosk.KioskPrefs;
import dev.perms.test.device.DeviceDetection;
import dev.perms.test.ui.ThemeColorController;
import dev.perms.test.ui.PermsTestUiCompat;

/** Initializes Settings preferences without overwriting existing user choices. */
public final class SettingsPreferenceDefaults {
    private SettingsPreferenceDefaults() {
    }

    public static void ensure(Context context, String prefsName) {
        if (context == null) return;

        try {
            SharedPreferences sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();

            if (!sp.contains(SettingsPreferenceKeys.ENABLE_FILE_OPEN_HANDLER)) {
                ed.putBoolean(SettingsPreferenceKeys.ENABLE_FILE_OPEN_HANDLER, true);
            }
            if (!sp.contains(KioskPrefs.KIOSK_MODE)) {
                ed.putBoolean(KioskPrefs.KIOSK_MODE, false);
            }
            if (!sp.contains(KioskPrefs.ICON_SIZE_DP)) {
                ed.putInt(KioskPrefs.ICON_SIZE_DP, KioskPrefs.DEFAULT_ICON_SIZE_DP);
            }
            if (!sp.contains(KioskPrefs.EXIT_PATTERN)) {
                ed.putString(KioskPrefs.EXIT_PATTERN, KioskPrefs.DEFAULT_EXIT_PATTERN);
            }
            if (!sp.contains(KioskPrefs.LOCK_TASK_MODE)) {
                ed.putBoolean(KioskPrefs.LOCK_TASK_MODE, true);
            }
            if (!sp.contains(KioskPrefs.HARDWARE_BUTTON_BYPASS)) {
                ed.putBoolean(KioskPrefs.HARDWARE_BUTTON_BYPASS, false);
            }
            if (!sp.contains(KioskPrefs.AUTO_SIZE_ICONS)) {
                ed.putBoolean(KioskPrefs.AUTO_SIZE_ICONS, false);
            }
            if (!sp.contains(KioskPrefs.SHOW_LABELS)) {
                ed.putBoolean(KioskPrefs.SHOW_LABELS, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.INSTALL_ALLOW_DOWNGRADE)) {
                ed.putBoolean(SettingsPreferenceKeys.INSTALL_ALLOW_DOWNGRADE, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.REMEMBER_OUTPUT_HEIGHT)) {
                ed.putBoolean(SettingsPreferenceKeys.REMEMBER_OUTPUT_HEIGHT, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.KEEP_BOTTOM_LOG_ABOVE_NAV_BAR)) {
                ed.putBoolean(SettingsPreferenceKeys.KEEP_BOTTOM_LOG_ABOVE_NAV_BAR, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.FAT_DROPDOWN_SCROLLBAR)) {
                ed.putBoolean(SettingsPreferenceKeys.FAT_DROPDOWN_SCROLLBAR, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.SAMSUNG_DROPDOWN_FIX)) {
                ed.putBoolean(SettingsPreferenceKeys.SAMSUNG_DROPDOWN_FIX, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.ENABLE_COLLAPSIBLE_GROUPBOXES)) {
                ed.putBoolean(SettingsPreferenceKeys.ENABLE_COLLAPSIBLE_GROUPBOXES, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.DISABLE_TUTORIAL)) {
                ed.putBoolean(SettingsPreferenceKeys.DISABLE_TUTORIAL, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.UI_VR_DEFAULT_RESET_APPLIED)) {
                ed.putBoolean(SettingsPreferenceKeys.UI_DETECT_VR_MODE, false);
                ed.putBoolean(SettingsPreferenceKeys.UI_VR_DEFAULT_RESET_APPLIED, true);
            } else if (!sp.contains(SettingsPreferenceKeys.UI_DETECT_VR_MODE)) {
                ed.putBoolean(SettingsPreferenceKeys.UI_DETECT_VR_MODE, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.ADJUST_UI_BASED_ON_DEVICE)) {
                ed.putBoolean(SettingsPreferenceKeys.ADJUST_UI_BASED_ON_DEVICE,
                        PermsTestUiCompat.isAdjustUiBasedOnDeviceEnabled(sp));
            }
            if (!sp.contains(SettingsPreferenceKeys.CLEAR_CACHE_ON_STARTUP)) {
                ed.putBoolean(SettingsPreferenceKeys.CLEAR_CACHE_ON_STARTUP, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.TRUNCATE_SHELL_OUTPUT)) {
                ed.putBoolean(SettingsPreferenceKeys.TRUNCATE_SHELL_OUTPUT, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.ENABLE_MULTIPLAYER_LINK)) {
                ed.putBoolean(SettingsPreferenceKeys.ENABLE_MULTIPLAYER_LINK, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.ENABLE_FLOATING_PANELS)) {
                ed.putBoolean(SettingsPreferenceKeys.ENABLE_FLOATING_PANELS, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.AUTOMATIC_DEVICE_DETECTION)) {
                ed.putBoolean(SettingsPreferenceKeys.AUTOMATIC_DEVICE_DETECTION, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.FUNNY_ANIMATION_TOOLTIPS)) {
                ed.putBoolean(SettingsPreferenceKeys.FUNNY_ANIMATION_TOOLTIPS, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.RANDOMIZE_ANIMATION_CATEGORY)) {
                ed.putBoolean(SettingsPreferenceKeys.RANDOMIZE_ANIMATION_CATEGORY, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.RANDOMIZE_CURRENT_TOOLTIPS)) {
                ed.putBoolean(SettingsPreferenceKeys.RANDOMIZE_CURRENT_TOOLTIPS, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.RANDOMIZE_ALL_TOOLTIPS)) {
                ed.putBoolean(SettingsPreferenceKeys.RANDOMIZE_ALL_TOOLTIPS, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.ANIMATION_CATEGORY)) {
                ed.putString(SettingsPreferenceKeys.ANIMATION_CATEGORY, "default");
            }
            if (!sp.contains(SettingsPreferenceKeys.HIDE_MAIN_PAGE_BANNER)) {
                ed.putBoolean(SettingsPreferenceKeys.HIDE_MAIN_PAGE_BANNER, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.THEME_COLORS)) {
                ed.putString(SettingsPreferenceKeys.THEME_COLORS, ThemeColorController.THEME_BLACK);
            }
            if (!sp.contains(SettingsPreferenceKeys.THEME_CUSTOM_COLOR)) {
                ed.putInt(SettingsPreferenceKeys.THEME_CUSTOM_COLOR, ThemeColorController.DEFAULT_CUSTOM_COLOR);
            }
            if (!sp.contains(SettingsPreferenceKeys.THEME_GRADIENT_DEFAULT_RESET_APPLIED)) {
                ed.putBoolean(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT, false);
                ed.putBoolean(SettingsPreferenceKeys.THEME_GRADIENT_DEFAULT_RESET_APPLIED, true);
            } else if (!sp.contains(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT)) {
                ed.putBoolean(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.THEME_COLOR_NAVIGATION_TABS)) {
                ed.putBoolean(SettingsPreferenceKeys.THEME_COLOR_NAVIGATION_TABS, true);
            }
            if (!sp.contains(SettingsPreferenceKeys.UPDATE_CUSTOM_SERVER_ENABLED)) {
                ed.putBoolean(SettingsPreferenceKeys.UPDATE_CUSTOM_SERVER_ENABLED, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.UPDATE_CUSTOM_SERVER_URL)) {
                ed.putString(SettingsPreferenceKeys.UPDATE_CUSTOM_SERVER_URL, "https://github.com/esc0rtd3w/dev.perms.test/releases");
            }
            if (!sp.contains(SettingsPreferenceKeys.UPDATE_INCLUDE_PRERELEASES)) {
                ed.putBoolean(SettingsPreferenceKeys.UPDATE_INCLUDE_PRERELEASES, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.UPDATE_ALLOW_DOWNGRADE)) {
                ed.putBoolean(SettingsPreferenceKeys.UPDATE_ALLOW_DOWNGRADE, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.UPDATE_AUTO_ENABLED)) {
                ed.putBoolean(SettingsPreferenceKeys.UPDATE_AUTO_ENABLED, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.UPDATE_AUTO_CHANNEL_PRERELEASE)) {
                ed.putBoolean(SettingsPreferenceKeys.UPDATE_AUTO_CHANNEL_PRERELEASE, false);
            }
            if (!sp.contains(SettingsPreferenceKeys.UPDATE_SILENT)) {
                ed.putBoolean(SettingsPreferenceKeys.UPDATE_SILENT, false);
            }

            if (!sp.contains(ExecMode.PREF_KEY_MODE)) {
                ed.putString(ExecMode.PREF_KEY_MODE, ExecMode.SHIZUKU.prefValue());
            }
            if (!sp.contains(ExecMode.PREF_KEY_LADB_AUTOCONNECT)) {
                ed.putBoolean(ExecMode.PREF_KEY_LADB_AUTOCONNECT, true);
            }
            if (!sp.contains(ExecMode.PREF_KEY_LADB_CONNECT_PORT)) {
                ed.putInt(ExecMode.PREF_KEY_LADB_CONNECT_PORT, ExecMode.LADB_DEFAULT_CONNECT_PORT);
            }
            if (!sp.contains(ExecMode.PREF_KEY_LADB_PAIR_PORT)) {
                ed.putInt(ExecMode.PREF_KEY_LADB_PAIR_PORT, 0);
            }

            ed.apply();
            DeviceDetection.applyAutomaticProfile(context);
        } catch (Throwable ignored) {
        }
    }
}
