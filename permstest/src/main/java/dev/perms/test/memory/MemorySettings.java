package dev.perms.test.memory;

import android.content.Context;
import android.content.SharedPreferences;

import dev.perms.test.vr.PermsTestVrOverlayCompat;

/**
 * Centralized persisted options for the Memory tab and floating overlays.
 */
public final class MemorySettings {
    private MemorySettings() {
    }

    public static void ensureDefaults(SharedPreferences prefs) {
        if (prefs == null) return;
        try {
            SharedPreferences.Editor editor = null;
            editor = putDefault(editor, prefs, MemoryToolHelper.KEY_USE_OVERLAY, true);
            editor = putDefault(editor, prefs, MemoryToolHelper.KEY_OVERLAY_TRANSPARENT, true);
            editor = putDefault(editor, prefs, MemoryToolHelper.KEY_OVERLAY_RESIZABLE, true);
            editor = putDefault(editor, prefs, MemoryToolHelper.KEY_ONLY_RUNNING_PACKAGES, false);
            editor = putDefault(editor, prefs, MemoryToolHelper.KEY_SHOW_RUNNING_PACKAGES, false);
            editor = putDefault(editor, prefs, MemoryToolHelper.KEY_EXCLUDE_SELF_PACKAGE, true);
            editor = putDefault(editor, prefs, MemoryToolHelper.KEY_AUTO_STAGE, true);
            editor = putDefault(editor, prefs, MemoryToolHelper.KEY_OVERLAY_SHOW_SESSION_BUTTONS, true);
            editor = putDefault(editor, prefs, MemoryToolHelper.KEY_SHOW_ALL_PATCHED_PAYLOAD_ADDRESSES, true);
            editor = putDefault(editor, prefs, MemoryToolHelper.KEY_STRING_CASE_SENSITIVE, false);
            if (editor != null) editor.apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean shouldPatchWithoutPtrace(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
    }

    public static boolean shouldUseOverlay(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_USE_OVERLAY, true);
    }

    public static boolean shouldOverlayTransparent(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_OVERLAY_TRANSPARENT, true);
    }

    public static boolean shouldOverlayResizable(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_OVERLAY_RESIZABLE, true);
    }

    public static boolean shouldDisableOverlaysForVrCompatible(Context context, SharedPreferences prefs) {
        if (prefs != null) {
            try {
                if (prefs.contains(MemoryToolHelper.KEY_DISABLE_OVERLAYS_VR_COMPATIBLE)) {
                    return prefs.getBoolean(MemoryToolHelper.KEY_DISABLE_OVERLAYS_VR_COMPATIBLE, false);
                }
            } catch (Throwable ignored) {
            }
        }
        return PermsTestVrOverlayCompat.isEnabled(context);
    }

    public static boolean shouldOnlyShowRunningPackages(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_ONLY_RUNNING_PACKAGES, false);
    }

    public static boolean shouldShowRunningPackages(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_SHOW_RUNNING_PACKAGES, false);
    }

    public static boolean shouldExcludeSelfPackage(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_EXCLUDE_SELF_PACKAGE, true);
    }

    public static boolean shouldAutoStage(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_AUTO_STAGE, true);
    }

    public static boolean shouldStringCaseSensitive(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_STRING_CASE_SENSITIVE, false);
    }

    public static boolean shouldShowOverlaySessionButtons(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_OVERLAY_SHOW_SESSION_BUTTONS, true);
    }

    public static boolean shouldApplyPayloadsOnAttach(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_APPLY_PAYLOADS_ON_ATTACH, false);
    }

    public static boolean shouldApplyPayloadsToAllMatches(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_APPLY_PAYLOADS_TO_ALL_MATCHES, true);
    }

    public static boolean shouldShowAllPatchedPayloadAddresses(SharedPreferences prefs) {
        return getBoolean(prefs, MemoryToolHelper.KEY_SHOW_ALL_PATCHED_PAYLOAD_ADDRESSES, true);
    }

    public static void setLastPackageToolsTarget(SharedPreferences prefs, String pkg) {
        if (prefs == null) return;
        try {
            String value = pkg == null ? "" : pkg.trim();
            prefs.edit().putString(MemoryToolHelper.KEY_LAST_PACKAGE_TOOLS_TARGET, value).apply();
        } catch (Throwable ignored) {
        }
    }

    public static String getLastPackageToolsTarget(SharedPreferences prefs) {
        if (prefs == null) return "";
        try {
            String value = prefs.getString(MemoryToolHelper.KEY_LAST_PACKAGE_TOOLS_TARGET, "");
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static SharedPreferences.Editor putDefault(SharedPreferences.Editor editor,
                                                       SharedPreferences prefs,
                                                       String key,
                                                       boolean value) {
        try {
            if (prefs == null || prefs.contains(key)) return editor;
            if (editor == null) editor = prefs.edit();
            editor.putBoolean(key, value);
        } catch (Throwable ignored) {
        }
        return editor;
    }

    private static boolean getBoolean(SharedPreferences prefs, String key, boolean defaultValue) {
        if (prefs == null) return defaultValue;
        try {
            return prefs.getBoolean(key, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}
