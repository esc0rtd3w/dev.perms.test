package dev.perms.test.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import dev.perms.test.R;

public final class PackageDropdownUi {
    public enum ColorMode {
        DEFAULT,
        ENABLED_STATE,
        DEBUGGABLE_HIGHLIGHT
    }

    private PackageDropdownUi() {
    }

    public static View bindTwoLine(
            @NonNull Context context,
            @NonNull LayoutInflater inflater,
            View convertView,
            @NonNull ViewGroup parent,
            String title,
            String subtitle,
            ColorMode colorMode,
            boolean enabled,
            boolean debuggable,
            boolean colorizeEnabledState,
            int enabledColor,
            int disabledColor) {
        View view = convertView;
        if (view == null || view.findViewById(R.id.text1) == null) {
            view = inflater.inflate(R.layout.dropdown_item_two_line, parent, false);
        }

        TextView text1 = view.findViewById(R.id.text1);
        TextView text2 = view.findViewById(R.id.text2);
        text1.setText(title == null ? "" : title);
        text2.setText(subtitle == null ? "" : subtitle);
        text1.setSelected(true);
        text2.setSelected(true);

        applyColors(context, text1, text2, colorMode, enabled, debuggable,
                colorizeEnabledState, enabledColor, disabledColor);
        return view;
    }

    private static void applyColors(
            @NonNull Context context,
            @NonNull TextView text1,
            @NonNull TextView text2,
            ColorMode colorMode,
            boolean enabled,
            boolean debuggable,
            boolean colorizeEnabledState,
            int enabledColor,
            int disabledColor) {
        try {
            if (colorMode == ColorMode.DEBUGGABLE_HIGHLIGHT && debuggable) {
                int color = ContextCompat.getColor(context, android.R.color.holo_blue_light);
                text1.setTextColor(color);
                text2.setTextColor(color);
                return;
            }

            if (colorMode == ColorMode.ENABLED_STATE && colorizeEnabledState) {
                text1.setTextColor(enabled ? enabledColor : disabledColor);
                text2.setTextColor(android.graphics.Color.WHITE);
                return;
            }

            text1.setTextColor(MaterialColors.getColor(text1,
                    com.google.android.material.R.attr.colorOnSurface));
            text2.setTextColor(MaterialColors.getColor(text2,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
        } catch (Throwable ignored) {
        }
    }

    public static String packageTitle(String label, String packageName) {
        return TextUtils.isEmpty(label) ? (packageName == null ? "" : packageName) : label;
    }
}
