package dev.perms.test.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;

import dev.perms.test.R;
import dev.perms.test.settings.SettingsPreferenceKeys;

/** Applies lightweight app color themes without changing feature behavior. */
public final class ThemeColorController {
    public static final String THEME_BLACK = "black";
    public static final String THEME_CUSTOM = "custom";

    // Kept so older saved preferences still resolve cleanly.
    public static final String THEME_CURRENT = "current_dark";
    public static final String THEME_SOLID_BLACK = "solid_black";

    private static final int DEFAULT_DARK_BACKGROUND = 0xFF0D0B14;
    private static final int DEFAULT_DARK_SURFACE = 0xFF111018;
    private static final int DEFAULT_DARK_ACCENT = 0xFFD6BCFF;

    public static final int DEFAULT_CUSTOM_COLOR = 0xFF2F7EC8;
    public static final int COLOR_SOLID_BLACK = 0xFF000000;
    public static final int COLOR_BLUE = 0xFF2F7EC8;
    public static final int COLOR_CYAN = 0xFF00ACC1;
    public static final int COLOR_TEAL = 0xFF009688;
    public static final int COLOR_PURPLE = 0xFF7E57C2;
    public static final int COLOR_PINK = 0xFFD81B60;
    public static final int COLOR_GREEN = 0xFF43A047;
    public static final int COLOR_AMBER = 0xFFFFB300;
    public static final int COLOR_ORANGE = 0xFFF57C00;
    public static final int COLOR_RED = 0xFFE53935;
    public static final int COLOR_SLATE = 0xFF546E7A;

    private ThemeColorController() {
    }

    public static String getTheme(Context context) {
        if (context == null) return THEME_BLACK;
        try {
            SharedPreferences sp = context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE);
            String value = sp.getString(SettingsPreferenceKeys.THEME_COLORS, THEME_BLACK);
            if (THEME_CUSTOM.equals(value)) return THEME_CUSTOM;
            return THEME_BLACK;
        } catch (Throwable ignored) {
            return THEME_BLACK;
        }
    }

    public static void saveTheme(Context context, String value) {
        if (context == null) return;
        try {
            context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(SettingsPreferenceKeys.THEME_COLORS,
                            THEME_CUSTOM.equals(value) ? THEME_CUSTOM : THEME_BLACK)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isCustom(Context context) {
        return THEME_CUSTOM.equals(getTheme(context));
    }

    public static int getCustomColor(Context context) {
        if (context == null) return DEFAULT_CUSTOM_COLOR;
        try {
            return context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .getInt(SettingsPreferenceKeys.THEME_CUSTOM_COLOR, DEFAULT_CUSTOM_COLOR);
        } catch (Throwable ignored) {
            return DEFAULT_CUSTOM_COLOR;
        }
    }

    public static void saveCustomColor(Context context, int color) {
        if (context == null) return;
        try {
            context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(SettingsPreferenceKeys.THEME_CUSTOM_COLOR, color)
                    .putString(SettingsPreferenceKeys.THEME_COLORS, THEME_CUSTOM)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isGradientEnabled(Context context) {
        if (context == null) return false;
        try {
            return context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean shouldColorNavigationTabs(Context context) {
        if (context == null) return true;
        try {
            return context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(SettingsPreferenceKeys.THEME_COLOR_NAVIGATION_TABS, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void saveGradientEnabled(Context context, boolean enabled) {
        if (context == null) return;
        try {
            context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT, enabled)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static void applyToActivity(Activity activity, View root) {
        if (activity == null) return;
        boolean custom = isCustom(activity);
        int color = custom ? getCustomColor(activity) : DEFAULT_DARK_ACCENT;
        int status = custom ? darken(color, 0.20f) : DEFAULT_DARK_SURFACE;
        int nav = custom ? darken(color, 0.12f) : DEFAULT_DARK_BACKGROUND;
        try {
            Window window = activity.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(background(activity));
                window.setStatusBarColor(status);
                window.setNavigationBarColor(nav);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (root != null) root.setBackground(background(activity));
        } catch (Throwable ignored) {
        }
        applyToNavigationTabs(activity);
    }

    public static void applyToStartup(Activity activity, View root) {
        if (activity == null || root == null) return;
        boolean custom = isCustom(activity);
        int color = custom ? getCustomColor(activity) : DEFAULT_DARK_ACCENT;
        try {
            root.setBackground(background(activity));
        } catch (Throwable ignored) {
        }
        try {
            View card = root.findViewById(R.id.startupCard);
            if (card != null) card.setBackground(startupCard(activity, custom, color));
        } catch (Throwable ignored) {
        }
        try {
            View halo = root.findViewById(R.id.startupLogoFrame);
            if (halo != null) halo.setBackground(startupHalo(activity, custom, color));
        } catch (Throwable ignored) {
        }
        try {
            TextView title = root.findViewById(R.id.txtStartupTitle);
            if (title != null) {
                title.setTextColor(Color.WHITE);
                title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
            TextView subtitle = root.findViewById(R.id.txtStartupSubtitle);
            if (subtitle != null) subtitle.setTextColor(custom ? lighten(color, 0.72f) : Color.rgb(220, 220, 220));
            TextView stage = root.findViewById(R.id.txtStartupStage);
            if (stage != null) stage.setTextColor(custom ? lighten(color, 0.64f) : Color.rgb(220, 220, 220));
            TextView tip = root.findViewById(R.id.txtStartupTip);
            if (tip != null) tip.setTextColor(Color.rgb(242, 242, 242));
            TextView dots = root.findViewById(R.id.txtStartupDots);
            if (dots != null) dots.setTextColor(custom ? lighten(color, 0.45f) : Color.rgb(170, 170, 170));
        } catch (Throwable ignored) {
        }
        applyToActivity(activity, root);
    }

    public static GradientDrawable swatchDrawable(Context context, int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 6));
        drawable.setColor(color);
        drawable.setStroke(dp(context, selected ? 3 : 1), selected ? Color.WHITE : Color.argb(0x88, 0xBB, 0xBB, 0xBB));
        return drawable;
    }

    private static GradientDrawable background(Context context) {
        if (!isCustom(context)) return solid(DEFAULT_DARK_BACKGROUND);
        int color = getCustomColor(context);
        if (!isGradientEnabled(context)) return solid(darken(color, 0.12f));
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { darken(color, 0.10f), darken(color, 0.16f), darken(color, 0.24f) });
    }

    private static void applyToNavigationTabs(Activity activity) {
        if (activity == null) return;
        try {
            TabLayout tabs = activity.findViewById(R.id.tabLayout);
            if (tabs == null) return;

            boolean custom = isCustom(activity);
            boolean colorTabs = shouldColorNavigationTabs(activity);
            int color = custom ? getCustomColor(activity) : DEFAULT_DARK_ACCENT;
            int background = custom && colorTabs ? darken(color, 0.16f) : DEFAULT_DARK_BACKGROUND;
            int selected = custom && colorTabs ? lighten(color, 0.78f) : DEFAULT_DARK_ACCENT;
            int normal = custom && colorTabs ? lighten(color, 0.58f) : Color.rgb(210, 207, 218);

            tabs.setBackgroundColor(background);
            tabs.setSelectedTabIndicatorColor(selected);
            tabs.setTabTextColors(new ColorStateList(
                    new int[][] { new int[] { android.R.attr.state_selected }, new int[] {} },
                    new int[] { selected, normal }));
        } catch (Throwable ignored) {
        }
    }

    private static GradientDrawable startupCard(Context context, boolean custom, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(custom ? darken(color, 0.18f) : DEFAULT_DARK_SURFACE);
        drawable.setCornerRadius(dp(context, 24));
        drawable.setStroke(dp(context, 1), custom ? lighten(color, 0.34f) : Color.rgb(90, 90, 90));
        drawable.setDither(true);
        return drawable;
    }

    private static GradientDrawable startupHalo(Context context, boolean custom, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(custom ? darken(color, 0.30f) : Color.rgb(24, 22, 32));
        drawable.setStroke(dp(context, 1), custom ? lighten(color, 0.38f) : Color.rgb(120, 120, 120));
        drawable.setDither(true);
        return drawable;
    }

    private static GradientDrawable solid(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static int darken(int color, float keep) {
        keep = Math.max(0f, Math.min(1f, keep));
        return Color.rgb(
                Math.max(0, Math.round(Color.red(color) * keep)),
                Math.max(0, Math.round(Color.green(color) * keep)),
                Math.max(0, Math.round(Color.blue(color) * keep)));
    }

    private static int lighten(int color, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.min(255, Math.round(Color.red(color) + (255 - Color.red(color)) * amount)),
                Math.min(255, Math.round(Color.green(color) + (255 - Color.green(color)) * amount)),
                Math.min(255, Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount)));
    }

    private static int dp(Context context, int dp) {
        float density = 1f;
        try { density = context.getResources().getDisplayMetrics().density; } catch (Throwable ignored) {}
        return Math.max(1, Math.round(dp * density));
    }
}
