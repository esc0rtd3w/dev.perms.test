package dev.perms.test.ui.device;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputLayout;

import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.PermsTestUiCompat;

/**
 * Centralized phone-only layout polish for places where tablet-friendly rows are too wide.
 *
 * The XML layout remains the source layout for tablet and VR. On phones this class applies a
 * generic control-row pass that stacks only rows that would clip: button groups, checkbox groups,
 * and mixed input/action rows. Keep phone-width behavior here instead of adding one-off tab fixes.
 */
public final class PhoneLayoutTuner {
    private static final int PHONE_SW_DP = 600;

    private PhoneLayoutTuner() {
    }

    public static void apply(Activity activity, ActivityMainBinding binding) {
        if (activity == null || binding == null || binding.getRoot() == null) return;
        if (!shouldApply(activity)) return;
        tuneOutputHeader(activity, binding);
        tuneButtonsRecursive(binding.getRoot());
        tunePhoneControlRows(activity, binding.getRoot());
    }

    private static boolean shouldApply(Context context) {
        if (context == null) return false;
        if (!PermsTestUiCompat.isAdjustUiBasedOnDeviceEnabled(context)) return false;
        if (PermsTestUiCompat.shouldUseVrProfile(context)) return false;
        Configuration c = context.getResources().getConfiguration();
        int sw = c.smallestScreenWidthDp;
        int w = c.screenWidthDp;
        return (sw > 0 && sw < PHONE_SW_DP) || (w > 0 && w < PHONE_SW_DP);
    }

    private static void tuneOutputHeader(Activity activity, ActivityMainBinding b) {
        try {
            b.outputStatusBar.setOrientation(LinearLayout.VERTICAL);
            b.outputStatusBar.setGravity(Gravity.CENTER_VERTICAL);
            b.outputStatusSpacer.setVisibility(View.GONE);

            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            b.outputStatusChipRow.setLayoutParams(chipParams);
            b.outputStatusChipRow.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            actionParams.topMargin = dp(activity, 4);
            b.outputActionRow.setLayoutParams(actionParams);
            b.outputActionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

            tuneChip(activity, b.chipShizukuStatus);
            tuneChip(activity, b.chipLadbStatus);
            tuneSmallButton(b.btnResetOutputGlobal);
            tuneSmallButton(b.btnClearOutputGlobal);
        } catch (Throwable ignored) {
        }
    }

    private static void tuneChip(Context context, Chip chip) {
        if (chip == null) return;
        chip.setMinWidth(0);
        chip.setMinimumWidth(0);
        chip.setMaxWidth(dp(context, 166));
        chip.setSingleLine(true);
        chip.setTextSize(11f);
        chip.setChipStartPadding(dp(context, 10));
        chip.setChipEndPadding(dp(context, 10));
    }

    private static void tuneButtonsRecursive(View view) {
        if (view == null) return;
        if (view instanceof MaterialButton) {
            tuneSmallButton((MaterialButton) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tuneButtonsRecursive(group.getChildAt(i));
            }
        }
    }

    private static void tuneSmallButton(MaterialButton button) {
        if (button == null) return;
        try {
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setSingleLine(false);
            button.setMaxLines(2);
            button.setGravity(Gravity.CENTER);
            button.setIncludeFontPadding(false);
            button.setTextSize(13f);
            button.setMinHeight(Math.min(button.getMinimumHeight(), dp(button.getContext(), 42)));
            button.setPadding(dp(button.getContext(), 8), button.getPaddingTop(), dp(button.getContext(), 8), button.getPaddingBottom());
        } catch (Throwable ignored) {
        }
    }

    private static void tunePhoneControlRows(Activity activity, View root) {
        if (activity == null || root == null) return;
        try {
            stackOverflowControlRows(activity, root, false);
        } catch (Throwable ignored) {
        }
        try {
            root.post(() -> {
                try {
                    stackOverflowControlRows(activity, root, true);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void stackOverflowControlRows(Context context, View view, boolean useMeasuredWidth) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        if (group instanceof TextInputLayout) return;
        if (group instanceof LinearLayout) {
            LinearLayout row = (LinearLayout) group;
            if (row.getOrientation() == LinearLayout.HORIZONTAL && shouldStackRow(context, row, useMeasuredWidth)) {
                stackRow(context, row);
            }
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            stackOverflowControlRows(context, group.getChildAt(i), useMeasuredWidth);
        }
    }

    private static boolean shouldStackRow(Context context, LinearLayout row, boolean useMeasuredWidth) {
        if (context == null || row == null) return false;
        if (row.getTag(R.id.tag_phone_responsive_stacked) != null) return false;
        if (isCollapsibleHeader(row) || isInsideHorizontalScroller(row)) return false;

        RowStats stats = collectRowStats(row);
        if (stats.visibleChildren < 2 || stats.interactiveChildren == 0) return false;

        if (stats.compoundButtons >= 2 && stats.visibleChildren >= 3) return true;
        if (stats.materialButtons >= 3) return true;
        if (stats.interactiveChildren >= 3 && stats.weightedChildren >= 2) return true;

        int available = availableRowWidth(context, row, useMeasuredWidth);
        if (available <= 0) return false;
        int wanted = estimateRowWidth(context, row, useMeasuredWidth);
        return wanted > available;
    }

    private static boolean isCollapsibleHeader(LinearLayout row) {
        Object tag = row.getTag();
        return tag != null && "collapsible_groupbox_header".equals(String.valueOf(tag));
    }

    private static boolean isInsideHorizontalScroller(View view) {
        View parent = null;
        try {
            if (view.getParent() instanceof View) parent = (View) view.getParent();
        } catch (Throwable ignored) {
        }
        while (parent != null) {
            if (parent instanceof HorizontalScrollView) return true;
            try {
                if (parent.getParent() instanceof View) {
                    parent = (View) parent.getParent();
                } else {
                    parent = null;
                }
            } catch (Throwable ignored) {
                parent = null;
            }
        }
        return false;
    }

    private static RowStats collectRowStats(LinearLayout row) {
        RowStats stats = new RowStats();
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child == null || child.getVisibility() == View.GONE) continue;
            stats.visibleChildren++;
            if (isInteractiveControl(child)) stats.interactiveChildren++;
            if (child instanceof CompoundButton) stats.compoundButtons++;
            if (child instanceof MaterialButton || child instanceof Button) stats.materialButtons++;
            if (child instanceof TextInputLayout || child instanceof EditText || child instanceof Spinner) stats.textInputs++;
            if (hasDirectNestedInteractiveControls(child)) stats.interactiveChildren++;
            ViewGroup.LayoutParams raw = child.getLayoutParams();
            if (raw instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
                if (lp.weight > 0f) stats.weightedChildren++;
            }
        }
        return stats;
    }

    private static boolean hasDirectNestedInteractiveControls(View view) {
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child != null && child.getVisibility() != View.GONE && isInteractiveControl(child)) return true;
        }
        return false;
    }

    private static boolean isInteractiveControl(View view) {
        if (view instanceof MaterialButton || view instanceof Button) return true;
        if (view instanceof CompoundButton) return true;
        if (view instanceof TextInputLayout || view instanceof EditText || view instanceof Spinner) return true;
        String name = view.getClass().getName();
        return name.contains("TextInput") || name.contains("AutoComplete") || name.contains("Spinner");
    }

    private static int availableRowWidth(Context context, LinearLayout row, boolean useMeasuredWidth) {
        int available = useMeasuredWidth ? row.getWidth() : 0;
        if (available <= 0) {
            Configuration c = context.getResources().getConfiguration();
            int screenDp = c.screenWidthDp > 0 ? c.screenWidthDp : 360;
            available = dp(context, Math.max(240, screenDp - 32));
        }
        return Math.max(0, available - row.getPaddingLeft() - row.getPaddingRight());
    }

    private static int estimateRowWidth(Context context, LinearLayout row, boolean useMeasuredWidth) {
        int total = row.getPaddingLeft() + row.getPaddingRight();
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child == null || child.getVisibility() == View.GONE) continue;
            ViewGroup.LayoutParams raw = child.getLayoutParams();
            if (raw instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) raw;
                total += mlp.leftMargin + mlp.rightMargin;
            }
            int width = useMeasuredWidth ? child.getMeasuredWidth() : 0;
            if (width <= 0 && raw != null && raw.width > 0) width = raw.width;
            if (width <= 0) width = estimateControlWidth(context, child, raw);
            total += Math.max(0, width);
        }
        return total;
    }

    private static int estimateControlWidth(Context context, View child, ViewGroup.LayoutParams raw) {
        if (child instanceof TextInputLayout || child instanceof EditText || child instanceof Spinner) return dp(context, 160);
        if (child instanceof CompoundButton) return estimateTextWidth(context, child, 56);
        if (child instanceof MaterialButton || child instanceof Button) return estimateTextWidth(context, child, 64);
        if (hasDirectNestedInteractiveControls(child)) return dp(context, 180);
        if (raw instanceof LinearLayout.LayoutParams && ((LinearLayout.LayoutParams) raw).weight > 0f) return dp(context, 120);
        return Math.max(child.getMinimumWidth(), dp(context, 80));
    }

    private static int estimateTextWidth(Context context, View child, int baseDp) {
        int base = dp(context, baseDp);
        if (child instanceof TextView) {
            CharSequence text = ((TextView) child).getText();
            if (text != null && text.length() > 0) {
                try {
                    return Math.max(base, Math.round(((TextView) child).getPaint().measureText(text.toString()))
                            + child.getPaddingLeft() + child.getPaddingRight() + dp(context, 20));
                } catch (Throwable ignored) {
                    return Math.max(base, dp(context, baseDp + Math.min(180, text.length() * 7)));
                }
            }
        }
        return base;
    }

    private static void stackRow(Context context, LinearLayout row) {
        try {
            row.setTag(R.id.tag_phone_responsive_stacked, Boolean.TRUE);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.START);
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                if (child == null || child.getVisibility() == View.GONE) continue;
                ViewGroup.LayoutParams raw = child.getLayoutParams();
                LinearLayout.LayoutParams lp;
                if (raw instanceof LinearLayout.LayoutParams) {
                    lp = (LinearLayout.LayoutParams) raw;
                } else {
                    lp = new LinearLayout.LayoutParams(raw == null ? ViewGroup.LayoutParams.MATCH_PARENT : raw.width,
                            raw == null ? ViewGroup.LayoutParams.WRAP_CONTENT : raw.height);
                }
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.weight = 0f;
                lp.leftMargin = 0;
                lp.rightMargin = 0;
                if (i > 0 && lp.topMargin < dp(context, 6)) lp.topMargin = dp(context, 6);
                child.setLayoutParams(lp);
                if (child instanceof MaterialButton) tuneSmallButton((MaterialButton) child);
                if (child instanceof TextView && !(child instanceof EditText)) {
                    TextView tv = (TextView) child;
                    tv.setSingleLine(false);
                    tv.setMaxLines(Math.max(tv.getMaxLines(), 2));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int dp(Context context, int value) {
        try {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        } catch (Throwable ignored) {
            return value;
        }
    }

    private static final class RowStats {
        int visibleChildren;
        int interactiveChildren;
        int compoundButtons;
        int materialButtons;
        int textInputs;
        int weightedChildren;
    }
}
