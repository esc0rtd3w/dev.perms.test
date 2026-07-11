package dev.perms.test.memory;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import androidx.annotation.NonNull;

import dev.perms.test.ui.PackageDropdownUi;

import java.util.ArrayList;
import java.util.List;

public final class MemoryPackageAdapter extends ArrayAdapter<MemoryPackageEntry> {
    private final LayoutInflater inflater;
    private final List<MemoryPackageEntry> original = new ArrayList<>();

    public MemoryPackageAdapter(@NonNull Context context, @NonNull List<MemoryPackageEntry> items) {
        super(context, 0, new ArrayList<>(items));
        inflater = LayoutInflater.from(context);
        original.addAll(items);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        return getDropDownView(position, convertView, parent);
    }

    @NonNull
    @Override
    public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
        MemoryPackageEntry entry = getItem(position);
        String title = "";
        String subtitle = "";
        boolean running = false;
        boolean debuggable = false;
        if (entry != null) {
            title = PackageDropdownUi.packageTitle(entry.label, entry.pkg);
            subtitle = entry.pkg;
            running = entry.running;
            debuggable = entry.debuggable;
            if (debuggable && !TextUtils.isEmpty(subtitle)) {
                subtitle += "  •  debuggable";
            }
            if (running && !TextUtils.isEmpty(subtitle)) {
                subtitle += "  •  running";
            }
        }
        return PackageDropdownUi.bindTwoLine(
                getContext(),
                inflater,
                convertView,
                parent,
                title,
                subtitle,
                PackageDropdownUi.ColorMode.DEBUGGABLE_HIGHLIGHT,
                true,
                debuggable,
                false,
                0,
                0);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                ArrayList<MemoryPackageEntry> out = new ArrayList<>(original);
                results.values = out;
                results.count = out.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                try {
                    clear();
                    if (results != null && results.values != null) {
                        addAll((List<MemoryPackageEntry>) results.values);
                    }
                    notifyDataSetChanged();
                } catch (Throwable ignored) {
                }
            }
        };
    }

    public void setItems(List<MemoryPackageEntry> items) {
        original.clear();
        if (items != null) original.addAll(items);
        clear();
        if (items != null) addAll(items);
        notifyDataSetChanged();
    }
}
