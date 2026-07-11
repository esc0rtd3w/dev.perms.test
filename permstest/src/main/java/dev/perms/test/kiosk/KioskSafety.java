package dev.perms.test.kiosk;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;

import java.io.File;

import dev.perms.test.settings.SettingsPreferenceKeys;

/** File-marker failsafes for Kiosk Mode and HOME launcher mode. */
public final class KioskSafety {
    public static final String PUBLIC_ROOT = "dev.perms.test";
    public static final String KIOSK_OFF = "kiosk.off";
    public static final String LAUNCHER_OFF = "launcher.off";

    private KioskSafety() {
    }

    public static boolean isKioskForceDisabled() {
        return markerExists(KIOSK_OFF);
    }

    public static boolean isLauncherForceDisabled() {
        return markerExists(LAUNCHER_OFF);
    }

    public static String kioskOffPath() {
        return "/sdcard/" + PUBLIC_ROOT + "/" + KIOSK_OFF;
    }

    public static String launcherOffPath() {
        return "/sdcard/" + PUBLIC_ROOT + "/" + LAUNCHER_OFF;
    }

    public static void clearKioskPreferenceIfForced(Context context) {
        if (context == null || !isKioskForceDisabled()) return;
        try {
            context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KioskPrefs.KIOSK_MODE, false)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static void clearLauncherPreferenceAndAliasIfForced(Context context) {
        if (context == null || !isLauncherForceDisabled()) return;
        try {
            context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(SettingsPreferenceKeys.RUN_AS_LAUNCHER, false)
                    .putBoolean(SettingsPreferenceKeys.PENDING_OPEN_HOME_SETTINGS, false)
                    .apply();
        } catch (Throwable ignored) {
        }
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            ComponentName launcherAlias = new ComponentName(packageName, packageName + ".LauncherAlias");
            ComponentName homeAlias = new ComponentName(packageName, packageName + ".HomeAlias");
            pm.setComponentEnabledSetting(launcherAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(homeAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable ignored) {
        }
    }

    private static boolean markerExists(String name) {
        if (isPresent(new File("/sdcard/" + PUBLIC_ROOT, name))) return true;
        if (isPresent(new File("/storage/emulated/0/" + PUBLIC_ROOT, name))) return true;
        try {
            File external = Environment.getExternalStorageDirectory();
            if (external != null && isPresent(new File(new File(external, PUBLIC_ROOT), name))) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isPresent(File file) {
        try {
            return file != null && file.exists();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
