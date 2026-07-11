package dev.perms.test.savedata;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Renders Save Data match rows with a checkbox outside the fast-scroll thumb area. */
final class SaveDataMatchAdapter extends BaseAdapter {
    private final Context context;
    private final ArrayList<SaveDataPatchEngine.Match> matches = new ArrayList<>();
    private ListView listView;

    SaveDataMatchAdapter(Context context) {
        this.context = context;
    }

    void attachListView(ListView listView) {
        this.listView = listView;
    }

    void setMatches(List<SaveDataPatchEngine.Match> newMatches) {
        matches.clear();
        if (newMatches != null) {
            for (SaveDataPatchEngine.Match match : newMatches) {
                if (match != null) matches.add(match);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return matches.size();
    }

    @Override
    public SaveDataPatchEngine.Match getItem(int position) {
        return position >= 0 && position < matches.size() ? matches.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RowHolder holder;
        if (convertView instanceof LinearLayout && convertView.getTag() instanceof RowHolder) {
            holder = (RowHolder) convertView.getTag();
        } else {
            holder = new RowHolder();
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(48));
            row.setPadding(dp(8), dp(4), dp(44), dp(4));

            CheckBox checkBox = new CheckBox(context);
            checkBox.setFocusable(false);
            checkBox.setFocusableInTouchMode(false);
            checkBox.setClickable(false);
            row.addView(checkBox, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView text = new TextView(context);
            text.setSingleLine(false);
            text.setTextSize(13f);
            text.setTypeface(Typeface.DEFAULT);
            text.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textParams.leftMargin = dp(8);
            row.addView(text, textParams);

            holder.checkBox = checkBox;
            holder.text = text;
            row.setTag(holder);
            convertView = row;
        }

        SaveDataPatchEngine.Match match = getItem(position);
        holder.text.setText(match == null ? "" : match.label());
        boolean checked = listView != null && listView.isItemChecked(position);
        holder.checkBox.setChecked(checked);
        return convertView;
    }

    private int dp(int value) {
        float density = context == null ? 1.0f : context.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * density));
    }

    private static final class RowHolder {
        CheckBox checkBox;
        TextView text;
    }
}
