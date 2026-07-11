package dev.perms.test.tools.intent;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import java.util.List;

import dev.perms.test.ui.PackageDropdownUi;

/** Two-line saved-intent dropdown adapter with package-enabled tinting. */
final class SavedIntentDropdownAdapter extends ArrayAdapter<SavedIntentDropdownEntry> {
    private final LayoutInflater inflater;
    private final int enabledColor;
    private final int disabledColor;

    SavedIntentDropdownAdapter(@NonNull Context context,
                               @NonNull List<SavedIntentDropdownEntry> items,
                               int enabledColor,
                               int disabledColor) {
        super(context, 0, items);
        this.inflater = LayoutInflater.from(context);
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
        SavedIntentDropdownEntry entry = getItem(position);
        String title = entry == null ? "" : entry.name;
        String subtitle = entry == null ? "" : entry.summary;
        boolean hasPackage = entry != null && entry.hasPackage;
        boolean enabled = entry == null || entry.packageEnabled;
        return PackageDropdownUi.bindTwoLine(
                getContext(),
                inflater,
                convertView,
                parent,
                title,
                subtitle,
                PackageDropdownUi.ColorMode.ENABLED_STATE,
                enabled,
                false,
                hasPackage,
                enabledColor,
                disabledColor);
    }
}
