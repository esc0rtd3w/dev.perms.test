package dev.perms.test.files;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** Renders one Files tab pane without keeping row-construction code in MainActivity. */
public final class FilesAdapter extends ArrayAdapter<FileEntry> {
    public interface Callbacks {
        int dp(int value);
        Drawable cachedPackageIcon(FileEntry entry);
        String fallbackIconFor(FileEntry entry);
        boolean isPackageArchive(String name);
        void schedulePackageIconLoad(FileEntry entry);
    }

    private final Callbacks callbacks;

    public FilesAdapter(Context context, ArrayList<FileEntry> entries, Callbacks callbacks) {
        super(context, 0, entries);
        this.callbacks = callbacks;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout row;
        ImageView iconImage;
        TextView iconText;
        TextView name;
        TextView meta;

        if (convertView instanceof LinearLayout) {
            row = (LinearLayout) convertView;
            iconImage = (ImageView) row.findViewWithTag("files_row_icon_image");
            iconText = (TextView) row.findViewWithTag("files_row_icon_text");
            name = (TextView) row.findViewWithTag("files_row_name");
            meta = (TextView) row.findViewWithTag("files_row_meta");
        } else {
            row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int padH = dp(10);
            int padV = dp(6);
            row.setPadding(padH, padV, padH, padV);
            row.setMinimumHeight(dp(54));

            FrameLayout iconBox = new FrameLayout(getContext());
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(34), dp(34));
            row.addView(iconBox, iconLp);

            iconImage = new ImageView(getContext());
            iconImage.setTag("files_row_icon_image");
            iconImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconBox.addView(iconImage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            iconText = new TextView(getContext());
            iconText.setTag("files_row_icon_text");
            iconText.setGravity(android.view.Gravity.CENTER);
            iconText.setTextSize(22);
            iconBox.addView(iconText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout textWrap = new LinearLayout(getContext());
            textWrap.setOrientation(LinearLayout.VERTICAL);
            textWrap.setPadding(dp(8), 0, 0, 0);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(textWrap, textLp);

            name = new TextView(getContext());
            name.setTag("files_row_name");
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            textWrap.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            meta = new TextView(getContext());
            meta.setTag("files_row_meta");
            meta.setTextSize(11);
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            meta.setAlpha(0.74f);
            textWrap.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        FileEntry entry = getItem(position);
        if (entry == null) {
            iconImage.setVisibility(View.GONE);
            iconImage.setImageDrawable(null);
            iconText.setVisibility(View.VISIBLE);
            iconText.setText("•");
            name.setText("");
            meta.setText("");
            return row;
        }

        Drawable packageIcon = callbacks.cachedPackageIcon(entry);
        if (packageIcon != null) {
            iconImage.setImageDrawable(packageIcon);
            iconImage.setVisibility(View.VISIBLE);
            iconText.setVisibility(View.GONE);
        } else {
            iconImage.setVisibility(View.GONE);
            iconImage.setImageDrawable(null);
            iconText.setVisibility(View.VISIBLE);
            iconText.setText(callbacks.fallbackIconFor(entry));
            if (!entry.isDir && callbacks.isPackageArchive(entry.name)) {
                callbacks.schedulePackageIconLoad(entry);
            }
        }
        name.setText(entry.name + (entry.isDir ? "/" : ""));
        meta.setText(entry.meta == null ? "" : entry.meta);
        return row;
    }

    private int dp(int value) {
        return callbacks.dp(value);
    }
}
