package dev.perms.test.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import java.util.List;

/** Common two-line adapter for package dropdowns. */
public final class PackageDropdownAdapter extends ArrayAdapter<PackageDropdownEntry> {
    private final LayoutInflater inflater;
    private final PackageDropdownUi.ColorMode colorMode;
    private final boolean colorizeEnabledState;
    private final int enabledColor;
    private final int disabledColor;

    public PackageDropdownAdapter(
            @NonNull Context context,
            @NonNull List<PackageDropdownEntry> items,
            @NonNull PackageDropdownUi.ColorMode colorMode,
            boolean colorizeEnabledState,
            int enabledColor,
            int disabledColor) {
        super(context, 0, items);
        this.inflater = LayoutInflater.from(context);
        this.colorMode = colorMode;
        this.colorizeEnabledState = colorizeEnabledState;
        this.enabledColor = enabledColor;
        this.disabledColor = disabledColor;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        return getDropDownView(position, convertView, parent);
    }

    @NonNull
    @Override
    public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
        PackageDropdownEntry entry = getItem(position);
        String title = "";
        String subtitle = "";
        boolean enabled = true;
        boolean debuggable = false;
        if (entry != null) {
            title = PackageDropdownUi.packageTitle(entry.label, entry.pkg);
            subtitle = TextUtils.isEmpty(entry.pkg) ? "" : entry.pkg;
            if (colorMode == PackageDropdownUi.ColorMode.DEBUGGABLE_HIGHLIGHT
                    && entry.debuggable
                    && !TextUtils.isEmpty(subtitle)) {
                subtitle += "  •  debuggable";
            }
            enabled = entry.enabled;
            debuggable = entry.debuggable;
        }
        return PackageDropdownUi.bindTwoLine(
                getContext(),
                inflater,
                convertView,
                parent,
                title,
                subtitle,
                colorMode,
                enabled,
                debuggable,
                colorizeEnabledState,
                enabledColor,
                disabledColor);
    }
}
