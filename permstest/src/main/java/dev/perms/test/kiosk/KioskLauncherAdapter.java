package dev.perms.test.kiosk;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import dev.perms.test.R;

/** Minimal large-icon adapter for the Kiosk Mode launcher. */
public final class KioskLauncherAdapter extends RecyclerView.Adapter<KioskLauncherAdapter.VH> {
    public interface Host {
        void launch(KioskAllowedItem item);
        Drawable iconFor(KioskAllowedItem item);
        Context context();
    }

    private final Host host;
    private final LayoutInflater inflater;
    private final ArrayList<KioskAllowedItem> items = new ArrayList<>();
    private int iconSizePx;
    private int itemMinHeightPx;
    private boolean showLabels = true;

    public KioskLauncherAdapter(@NonNull Host host) {
        this.host = host;
        this.inflater = LayoutInflater.from(host.context());
        setHasStableIds(true);
    }

    public void setIconOptions(int iconSizePx, boolean showLabels, int itemMinHeightPx) {
        this.iconSizePx = Math.max(1, iconSizePx);
        this.showLabels = showLabels;
        this.itemMinHeightPx = Math.max(0, itemMinHeightPx);
        notifyDataSetChanged();
    }

    public void setItems(List<KioskAllowedItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        KioskAllowedItem item = items.get(position);
        return item.stableKey().hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(inflater.inflate(R.layout.item_kiosk_app, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        KioskAllowedItem item = items.get(position);
        holder.label.setText(item.label);
        holder.label.setVisibility(showLabels ? View.VISIBLE : View.GONE);
        Drawable icon = host.iconFor(item);
        if (icon != null) holder.icon.setImageDrawable(icon);
        else holder.icon.setImageResource(R.mipmap.ic_launcher);
        ViewGroup.LayoutParams lp = holder.icon.getLayoutParams();
        if (lp != null && iconSizePx > 0) {
            lp.width = iconSizePx;
            lp.height = iconSizePx;
            holder.icon.setLayoutParams(lp);
        }
        holder.itemView.setMinimumHeight(itemMinHeightPx);
        ViewGroup.LayoutParams itemLp = holder.itemView.getLayoutParams();
        if (itemLp != null) {
            itemLp.height = itemMinHeightPx > 0 ? itemMinHeightPx : ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.itemView.setLayoutParams(itemLp);
        }
        holder.itemView.setOnClickListener(null);
        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        ViewGroup.LayoutParams targetLp = holder.launchTarget.getLayoutParams();
        if (targetLp != null && iconSizePx > 0) {
            targetLp.width = Math.max(dp(112), iconSizePx + dp(32));
            targetLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.launchTarget.setLayoutParams(targetLp);
        }
        holder.launchTarget.setOnClickListener(v -> host.launch(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int dp(int value) {
        return Math.round(value * host.context().getResources().getDisplayMetrics().density);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final View launchTarget;
        final ImageView icon;
        final TextView label;

        VH(@NonNull View itemView) {
            super(itemView);
            launchTarget = itemView.findViewById(R.id.layoutKioskLaunchTarget);
            icon = itemView.findViewById(R.id.imgKioskIcon);
            label = itemView.findViewById(R.id.txtKioskLabel);
        }
    }
}
