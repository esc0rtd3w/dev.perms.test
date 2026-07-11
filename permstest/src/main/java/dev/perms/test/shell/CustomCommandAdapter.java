package dev.perms.test.shell;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.List;

import dev.perms.test.R;

/**
 * RecyclerView adapter for user-saved Shell tab commands.
 */
public final class CustomCommandAdapter extends RecyclerView.Adapter<CustomCommandAdapter.VH> {
    public interface Callbacks {
        void run(CustomCommand command);
        void showMenu(CustomCommand command);
        void showManageMenu(CustomCommand command);
        void startDrag(RecyclerView.ViewHolder holder);
    }

    public static final class VH extends RecyclerView.ViewHolder {
        final MaterialButton btnCmd;
        final MaterialButton btnMenu;

        VH(View itemView) {
            super(itemView);
            btnCmd = itemView.findViewById(R.id.btnCmd);
            btnMenu = itemView.findViewById(R.id.btnCmdMenu);
        }
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final List<CustomCommand> commands;
    private final Callbacks callbacks;

    public CustomCommandAdapter(Context context, LayoutInflater inflater, List<CustomCommand> commands, Callbacks callbacks) {
        this.context = context;
        this.inflater = inflater;
        this.commands = commands;
        this.callbacks = callbacks;
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.item_custom_command, parent, false);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int m = dp(6);
        lp.setMargins(0, m, 0, 0);
        v.setLayoutParams(lp);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(final VH h, int position) {
        if (commands == null || position < 0 || position >= commands.size()) return;
        final CustomCommand cc = commands.get(position);
        if (cc == null) return;

        h.btnCmd.setAllCaps(false);
        h.btnCmd.setText(truncateLabel(cc.displayName(), 40));
        styleCommandButton(h.btnCmd);

        final boolean hasVariants = cc.variants != null && !cc.variants.isEmpty();
        h.btnMenu.setVisibility(hasVariants ? View.VISIBLE : View.GONE);

        h.btnCmd.setOnClickListener(v -> {
            try {
                if (callbacks != null) callbacks.run(cc);
            } catch (Throwable ignored) {
            }
        });

        h.btnMenu.setOnClickListener(v -> {
            try {
                if (callbacks != null) callbacks.showMenu(cc);
            } catch (Throwable ignored) {
            }
        });
        h.btnMenu.setOnLongClickListener(v -> {
            try {
                if (callbacks != null) callbacks.startDrag(h);
            } catch (Throwable ignored) {
            }
            return true;
        });

        h.btnCmd.setOnLongClickListener(v -> {
            try {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (callbacks != null) callbacks.showManageMenu(cc);
            } catch (Throwable ignored) {
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return commands == null ? 0 : commands.size();
    }

    private void styleCommandButton(MaterialButton button) {
        try {
            int bg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorSecondaryContainer, 0);
            int fg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSecondaryContainer, 0);
            int outline = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOutline, 0);
            button.setBackgroundTintList(ColorStateList.valueOf(bg));
            button.setTextColor(fg);
            button.setStrokeColor(ColorStateList.valueOf(outline));
            button.setStrokeWidth(dp(1));
            button.setCornerRadius(dp(16));
        } catch (Throwable ignored) {
        }
    }

    private int dp(int value) {
        try {
            float density = context == null ? 1f : context.getResources().getDisplayMetrics().density;
            return Math.round(value * density);
        } catch (Throwable ignored) {
            return value;
        }
    }

    private static String truncateLabel(String value, int max) {
        if (value == null) return "";
        if (max <= 0 || value.length() <= max) return value;
        if (max <= 1) return value.substring(0, max);
        return value.substring(0, max - 1) + "…";
    }
}
