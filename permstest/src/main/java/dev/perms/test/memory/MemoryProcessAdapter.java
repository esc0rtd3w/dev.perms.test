package dev.perms.test.memory;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-filtering process adapter so the running-process dropdown always shows the full list
 * even after selection, rotation, or state restore.
 */
public final class MemoryProcessAdapter extends ArrayAdapter<MemoryProcessEntry> {
    private final List<MemoryProcessEntry> original = new ArrayList<>();

    public MemoryProcessAdapter(Context ctx, int resource, List<MemoryProcessEntry> items) {
        super(ctx, resource, new ArrayList<>(items));
        if (items != null) original.addAll(items);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                ArrayList<MemoryProcessEntry> out = new ArrayList<>(original);
                results.values = out;
                results.count = out.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                try {
                    clear();
                    if (results != null && results.values != null) addAll((List<MemoryProcessEntry>) results.values);
                    notifyDataSetChanged();
                } catch (Throwable ignored) {
                }
            }
        };
    }

    public void setItems(List<MemoryProcessEntry> items) {
        try {
            original.clear();
            if (items != null) original.addAll(items);
            clear();
            if (items != null) addAll(items);
            notifyDataSetChanged();
        } catch (Throwable ignored) {
        }
    }
}
