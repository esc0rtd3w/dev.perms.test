package dev.perms.test.vr;

import dev.perms.test.ui.PermsTestUiCompat;
import android.app.Activity;
import android.content.Context;
import android.view.WindowManager;

/**
 * Small compatibility gate for Quest/Horizon overlay behavior.
 *
 * The standard phone/tablet overlay path must remain unchanged. These helpers only
 * become active when the Settings UI has Enable VR Mode enabled and the device is
 * positively identified by PermsTestUiCompat as a VR/Quest-style device.
 */
public final class PermsTestVrOverlayCompat {
    private static final String PREFS = "perms_test";
    private static final String PREF_VR_OVERLAY_HIDDEN = "memory_overlay_vr_hidden";
    private static final String PREF_VR_OVERLAY_TARGET_PACKAGE = "memory_overlay_vr_target_package";
    private static final String PREF_VR_OVERLAY_TARGET_PID = "memory_overlay_vr_target_pid";
    private static final String PREF_VR_OVERLAY_RESTORE_AT = "memory_overlay_vr_restore_at";
    private static final String PREF_VR_STOPPED_TARGET_PACKAGE = "memory_overlay_vr_stopped_target_package";
    private static final long MAIN_LAUNCH_RESTORE_COOLDOWN_MS = 2500L;
    public static final String ACTION_TEXT_INPUT_RESULT = "dev.perms.test.action.VR_TEXT_INPUT_RESULT";
    public static final String EXTRA_FIELD_KEY = "fieldKey";
    public static final String EXTRA_FIELD_LABEL = "fieldLabel";
    public static final String EXTRA_FIELD_VALUE = "fieldValue";
    public static final String EXTRA_FIELD_HINT = "fieldHint";
    public static final String EXTRA_CANCELLED = "cancelled";

    public static final String FIELD_SEARCH_VALUE = "search_value";
    public static final String FIELD_PATCH_NAME = "patch_name";
    public static final String FIELD_PATCH_ADDRESS = "patch_address";
    public static final String FIELD_PATCH_VALUE = "patch_value";
    public static final String FIELD_RESULT_LIMIT = "result_limit";
    public static final String FIELD_MAX_RESULTS = "max_results";
    public static final String FIELD_DUMP_BEGIN = "dump_begin";
    public static final String FIELD_DUMP_END = "dump_end";
    public static final String FIELD_AUTO_RANGE_LIMIT = "auto_range_limit";

    private PermsTestVrOverlayCompat() {
    }

    public static boolean isEnabled(Context context) {
        return PermsTestUiCompat.shouldUseVrProfile(context);
    }

    public static boolean shouldKeepOverlayNonFocusable(Context context) {
        return isEnabled(context);
    }

    public static boolean shouldRemoveMainOverlayOnMinimize(Context context) {
        return isEnabled(context);
    }

    public static boolean shouldRemoveMainOverlayBeforeLaunch(Context context) {
        return isEnabled(context);
    }

    public static boolean shouldUseDetachedTextInput(Context context) {
        return isEnabled(context);
    }

    public static void moveHostTaskBehindMemoryToolIfNeeded(Activity activity) {
        if (activity == null || !isEnabled(activity)) return;
        try {
            activity.moveTaskToBack(true);
        } catch (Throwable ignored) {
        }
    }

    public static boolean shouldSwitchMainOverlayOutForTool(Context context) {
        return false;
    }

    public static boolean shouldDestroyToolOverlayOnClose(Context context) {
        return isEnabled(context);
    }

    public static boolean shouldRestoreMainOverlayAfterToolClose(Context context) {
        return false;
    }

    public static boolean shouldRestoreMainOverlayAfterLaunch(Context context) {
        return isEnabled(context);
    }

    public static boolean shouldReturnTargetOnMainOverlayDismiss(Context context) {
        return isEnabled(context);
    }

    public static long targetReturnDelayMs() {
        return 180L;
    }

    public static long mainOverlayRestoreAfterLaunchDelayMs() {
        return 4200L;
    }

    public static void markHiddenOverlayForVr(Context context, String packageName, String pid) {
        if (!isEnabled(context)) return;
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(PREF_VR_OVERLAY_HIDDEN, true)
                    .putString(PREF_VR_OVERLAY_TARGET_PACKAGE, clean(packageName))
                    .putString(PREF_VR_OVERLAY_TARGET_PID, clean(pid))
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static void markStoppedTargetForVr(Context context, String packageName) {
        if (!isEnabled(context)) return;
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PREF_VR_STOPPED_TARGET_PACKAGE, clean(packageName))
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static void clearStoppedTargetForVr(Context context) {
        if (context == null) return;
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(PREF_VR_STOPPED_TARGET_PACKAGE)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isStoppedTargetForVr(Context context, String packageName) {
        if (!isEnabled(context)) return false;
        try {
            String stopped = clean(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PREF_VR_STOPPED_TARGET_PACKAGE, ""));
            String pkg = clean(packageName);
            return !android.text.TextUtils.isEmpty(stopped) && android.text.TextUtils.equals(stopped, pkg);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void clearHiddenOverlayForVr(Context context) {
        if (context == null) return;
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(PREF_VR_OVERLAY_HIDDEN, false)
                    .remove(PREF_VR_OVERLAY_TARGET_PACKAGE)
                    .remove(PREF_VR_OVERLAY_TARGET_PID)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean consumeMainLaunchOverlayRestore(Context context) {
        if (!isEnabled(context)) return false;
        try {
            android.content.SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!sp.getBoolean(PREF_VR_OVERLAY_HIDDEN, false)) return false;
            long now = android.os.SystemClock.elapsedRealtime();
            long last = sp.getLong(PREF_VR_OVERLAY_RESTORE_AT, 0L);
            if (last > 0L && now - last < MAIN_LAUNCH_RESTORE_COOLDOWN_MS) return false;
            sp.edit().putLong(PREF_VR_OVERLAY_RESTORE_AT, now).apply();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String hiddenVrTargetPackage(Context context) {
        if (context == null) return "";
        try {
            return clean(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PREF_VR_OVERLAY_TARGET_PACKAGE, ""));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String hiddenVrTargetPid(Context context) {
        if (context == null) return "";
        try {
            return clean(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PREF_VR_OVERLAY_TARGET_PID, ""));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static int buildOverlayFlags(Context context, int baseFlags, boolean interactive) {
        int flags = baseFlags;
        if (!interactive || shouldKeepOverlayNonFocusable(context)) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        return flags;
    }
}
