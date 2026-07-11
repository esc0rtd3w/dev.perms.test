package dev.perms.test.ui.panel;

import android.content.Context;

import dev.perms.test.settings.SettingsPreferenceKeys;
import dev.perms.test.ui.PermsTestUiCompat;

/** Shared app-wide gate for optional PermsTest popout panels. */
public final class PermsTestPanelSettings {
    private PermsTestPanelSettings() {
    }

    public static boolean isPanelHostEnabled(Context context) {
        return isPanelHostSavedEnabled(context);
    }

    public static boolean isPanelHostSavedEnabled(Context context) {
        if (context == null) return false;
        try {
            return context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(SettingsPreferenceKeys.ENABLE_FLOATING_PANELS, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPanelHostAutoEnabledByVr(Context context) {
        try {
            return PermsTestUiCompat.shouldUseVrProfile(context)
                    && !context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(SettingsPreferenceKeys.DEVICE_DEFAULT_FLOATING_PANELS_USER_SET, false);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
