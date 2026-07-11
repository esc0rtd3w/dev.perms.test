package dev.perms.test.ui;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * ArrayAdapter variant that never filters the visible item set.
 *
 * This keeps Material exposed-dropdown fields behaving like true dropdowns when a
 * value is already selected in the text field.
 */
public final class NoFilterArrayAdapter extends ArrayAdapter<String> {
    private final Filter noFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            results.values = null;
            results.count = getCount();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            notifyDataSetChanged();
        }
    };

    public NoFilterArrayAdapter(@NonNull Context context, int resource, @NonNull String[] objects) {
        super(context, resource, objects);
    }

    public NoFilterArrayAdapter(@NonNull Context context, int resource, @NonNull List<String> objects) {
        super(context, resource, objects);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return noFilter;
    }
}
