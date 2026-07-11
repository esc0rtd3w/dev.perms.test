package dev.perms.test.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Shared status-chip presentation for the always-visible backend badges.
 */
public final class StatusChipUi {
    private StatusChipUi() {
    }

    public static void updateShizukuStatusChip(Activity activity, ActivityMainBinding b, boolean binderAlive, boolean granted) {
        try {
            if (activity == null || b == null || b.chipShizukuStatus == null) return;

            String text;
            int bg;
            int fg = ContextCompat.getColor(activity, android.R.color.white);

            if (!binderAlive) {
                text = "Shizuku: Not running";
                bg = ContextCompat.getColor(activity, android.R.color.darker_gray);
            } else if (!granted) {
                text = "Shizuku: Permission needed";
                bg = ContextCompat.getColor(activity, android.R.color.holo_orange_dark);
            } else {
                text = "Shizuku: Connected";
                bg = ContextCompat.getColor(activity, android.R.color.holo_green_dark);
            }

            b.chipShizukuStatus.setText(text);
            b.chipShizukuStatus.setTextColor(fg);
            b.chipShizukuStatus.setChipBackgroundColor(ColorStateList.valueOf(bg));
        } catch (Throwable ignored) {
        }
    }

    public static void updateLadbStatusChip(Activity activity, ActivityMainBinding b, boolean show, boolean connected) {
        try {
            if (activity == null || b == null || b.chipLadbStatus == null) return;

            // Always keep the LADB badge visible so the header layout is stable and
            // the user can immediately see whether LADB is available/connected.
            b.chipLadbStatus.setVisibility(View.VISIBLE);

            String text;
            int bg;
            int fg = ContextCompat.getColor(activity, android.R.color.white);

            if (!show) {
                text = "LADB: Disabled";
                bg = ContextCompat.getColor(activity, android.R.color.darker_gray);
            } else if (connected) {
                text = "LADB: Connected";
                bg = ContextCompat.getColor(activity, android.R.color.holo_green_dark);
            } else {
                text = "LADB: Not connected";
                bg = ContextCompat.getColor(activity, android.R.color.darker_gray);
            }

            b.chipLadbStatus.setText(text);
            b.chipLadbStatus.setTextColor(fg);
            b.chipLadbStatus.setChipBackgroundColor(ColorStateList.valueOf(bg));
        } catch (Throwable ignored) {
        }
    }

    public static void equalizeStatusChips(ActivityMainBinding b) {
        try {
            if (b == null) return;
            final View chip1 = b.chipShizukuStatus;
            final View chip2 = b.chipLadbStatus;
            if (chip1 == null || chip2 == null) return;

            // Let them measure their natural width, then force both to the same width
            // so they look uniform without stretching across the entire row.
            chip1.post(() -> {
                try {
                    View c1 = b.chipShizukuStatus;
                    View c2 = b.chipLadbStatus;
                    if (c1 == null || c2 == null) return;

                    c1.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    c2.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

                    int w = Math.max(c1.getMeasuredWidth(), c2.getMeasuredWidth());
                    if (w <= 0) return;

                    ViewGroup.LayoutParams lp1 = c1.getLayoutParams();
                    ViewGroup.LayoutParams lp2 = c2.getLayoutParams();
                    if (lp1 != null) lp1.width = w;
                    if (lp2 != null) lp2.width = w;
                    c1.setLayoutParams(lp1);
                    c2.setLayoutParams(lp2);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
