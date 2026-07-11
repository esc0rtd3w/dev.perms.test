package dev.perms.test.ui;

import dev.perms.test.R;
import dev.perms.test.vr.PermsTestVrOverlayCompat;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

/**
 * Central UI scaling/profile helper for activity pages and memory overlays.
 *
 * Phone/tablet scaling is gated by the Adjust UI Based On Device setting.
 * VR detection is kept separate because Quest-style overlay behavior needs its
 * own compatibility path instead of shrinking the normal Android overlay UI.
 */
public final class PermsTestUiCompat {
    public static final String PREF_UI_DETECT_VR_MODE = "ui_detect_vr_mode";
    public static final String PREF_ADJUST_UI_BASED_ON_DEVICE = "adjust_ui_based_on_device";
    private static final String LEGACY_PREF_UI_AUTO_DEVICE_LAYOUT = "ui_auto_device_layout";

    private static final String PREFS = "perms_test";

    private PermsTestUiCompat() {
    }

    /** True only when the user enabled VR detection and the device looks VR-like. */
    public static boolean shouldUseVrProfile(Context context) {
        if (context == null) return false;
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getBoolean(PREF_UI_DETECT_VR_MODE, false) && isLikelyVrDevice(context);
    }

    public static boolean shouldKeepMemoryOverlaysPassive(Context context) {
        return PermsTestVrOverlayCompat.shouldKeepOverlayNonFocusable(context);
    }

    /** Applies global activity-page text/touch scaling. */
    public static void applyActivityUiProfile(Context context, View root) {
        if (context == null || root == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean auto = isAdjustUiBasedOnDeviceEnabled(sp);
        float scale = auto ? chooseTextScale(context) : 1.0f;
        float touchScale = auto ? chooseTouchScale(context) : 1.0f;
        applyProfileRecursive(root, scale, touchScale);
    }

    /** Applies phone/tablet overlay scaling; VR overlays are deliberately excluded. */
    public static void applyMemoryOverlayUiProfile(Context context, View root) {
        if (context == null || root == null || !shouldScaleMemoryOverlayForDevice(context)) return;
        float textScale = chooseTextScale(context);
        float touchScale = Math.min(1.0f, chooseTouchScale(context));
        applyProfileRecursive(root, textScale, touchScale);
    }

    /** Scales overlay dimensions from their tablet-friendly defaults on smaller devices. */
    public static int scaleMemoryOverlayPx(Context context, int px) {
        if (context == null || px <= 0 || !shouldScaleMemoryOverlayForDevice(context)) return px;
        return Math.max(1, Math.round(px * chooseMemoryOverlayWindowScale(context)));
    }

    /** Gate used by overlay windows so VR-specific code does not inherit phone scaling. */
    public static boolean shouldScaleMemoryOverlayForDevice(Context context) {
        if (context == null || shouldUseVrProfile(context)) return false;
        return isAdjustUiBasedOnDeviceEnabled(context);
    }


    public static boolean isAdjustUiBasedOnDeviceEnabled(Context context) {
        if (context == null) return false;
        return isAdjustUiBasedOnDeviceEnabled(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    public static boolean isAdjustUiBasedOnDeviceEnabled(SharedPreferences sp) {
        if (sp == null) return true;
        if (sp.contains(PREF_ADJUST_UI_BASED_ON_DEVICE)) {
            return sp.getBoolean(PREF_ADJUST_UI_BASED_ON_DEVICE, true);
        }
        return sp.getBoolean(LEGACY_PREF_UI_AUTO_DEVICE_LAYOUT, true);
    }

    /** Chooses conservative window scaling based on smallest-width buckets. */
    private static float chooseMemoryOverlayWindowScale(Context context) {
        Configuration config = context.getResources().getConfiguration();
        int sw = config.smallestScreenWidthDp;
        int w = config.screenWidthDp;
        int h = config.screenHeightDp;
        int shortSide = Math.min(w <= 0 ? sw : w, h <= 0 ? sw : h);
        int basis = sw > 0 ? sw : shortSide;

        if (basis > 0 && basis < 360) return 0.70f;
        if ((basis > 0 && basis < 480) || (shortSide > 0 && shortSide < 420)) return 0.78f;
        if (basis > 0 && basis < 600) return 0.88f;
        return 1.0f;
    }

    private static float chooseTextScale(Context context) {
        Configuration config = context.getResources().getConfiguration();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int sw = config.smallestScreenWidthDp;
        int w = config.screenWidthDp;
        int h = config.screenHeightDp;
        int shortSide = Math.min(w <= 0 ? sw : w, h <= 0 ? sw : h);
        boolean vr = shouldUseVrProfile(context);

        if (vr) {
            return 1.16f;
        }
        if (sw > 0 && sw < 360) {
            return 0.84f;
        }
        if ((sw > 0 && sw < 480) || (shortSide > 0 && shortSide < 420)) {
            return 0.88f;
        }
        if (sw > 0 && sw < 600) {
            return 0.93f;
        }
        if (sw >= 900 && dm.densityDpi >= 420) {
            return 1.05f;
        }
        return 1.0f;
    }

    private static float chooseTouchScale(Context context) {
        Configuration config = context.getResources().getConfiguration();
        int sw = config.smallestScreenWidthDp;
        if (shouldUseVrProfile(context)) return 1.08f;
        if (sw > 0 && sw < 480) return 0.90f;
        if (sw > 0 && sw < 600) return 0.95f;
        return 1.0f;
    }

    /** Walks the view tree and preserves each widget's original size in view tags. */
    private static void applyProfileRecursive(View view, float textScale, float touchScale) {
        if (view instanceof TextView) {
            applyTextScale((TextView) view, textScale);
        }
        if (view instanceof CompoundButton || view instanceof android.widget.Button) {
            applyTouchScale(view, touchScale);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyProfileRecursive(group.getChildAt(i), textScale, touchScale);
            }
        }
    }

    private static void applyTextScale(TextView view, float scale) {
        Object tag = view.getTag(R.id.tag_ui_compat_base_text_px);
        float basePx;
        if (tag instanceof Float) {
            basePx = (Float) tag;
        } else {
            basePx = view.getTextSize();
            view.setTag(R.id.tag_ui_compat_base_text_px, Float.valueOf(basePx));
        }
        if (basePx > 0f) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, basePx * scale);
        }
    }

    private static void applyTouchScale(View view, float scale) {
        int baseMinHeight = getIntTag(view, R.id.tag_ui_compat_base_min_height, view.getMinimumHeight());
        int left = getIntTag(view, R.id.tag_ui_compat_base_padding_left, view.getPaddingLeft());
        int top = getIntTag(view, R.id.tag_ui_compat_base_padding_top, view.getPaddingTop());
        int right = getIntTag(view, R.id.tag_ui_compat_base_padding_right, view.getPaddingRight());
        int bottom = getIntTag(view, R.id.tag_ui_compat_base_padding_bottom, view.getPaddingBottom());

        if (baseMinHeight > 0) {
            view.setMinimumHeight(Math.max(1, Math.round(baseMinHeight * scale)));
        }
        view.setPadding(
                Math.max(0, Math.round(left * scale)),
                Math.max(0, Math.round(top * scale)),
                Math.max(0, Math.round(right * scale)),
                Math.max(0, Math.round(bottom * scale)));
    }

    private static int getIntTag(View view, int id, int defaultValue) {
        Object tag = view.getTag(id);
        if (tag instanceof Integer) return (Integer) tag;
        view.setTag(id, Integer.valueOf(defaultValue));
        return defaultValue;
    }

    private static boolean isLikelyVrDevice(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm != null) {
            if (hasFeature(pm, "android.hardware.vr.high_performance")
                    || hasFeature(pm, "android.software.vr.mode")
                    || hasFeature(pm, "oculus.hardware.vr")
                    || hasFeature(pm, "com.oculus.feature.VR")) {
                return true;
            }
            if (hasPackage(pm, "com.oculus.vrshell")
                    || hasPackage(pm, "com.oculus.systemdriver")
                    || hasPackage(pm, "com.meta.horizon")) {
                return true;
            }
        }
        String build = joinLower(Build.MANUFACTURER, Build.BRAND, Build.DEVICE, Build.MODEL, Build.PRODUCT, Build.HARDWARE);
        return build.contains("oculus")
                || build.contains("quest")
                || build.contains("vr")
                || build.contains("eureka")
                || build.contains("hollywood")
                || build.contains("meta");
    }

    private static boolean hasFeature(PackageManager pm, String name) {
        try {
            return name != null && pm.hasSystemFeature(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasPackage(PackageManager pm, String name) {
        try {
            pm.getPackageInfo(name, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String joinLower(String... parts) {
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (String p : parts) {
                if (p != null) sb.append(' ').append(p);
            }
        }
        return sb.toString().toLowerCase(java.util.Locale.US);
    }
}
