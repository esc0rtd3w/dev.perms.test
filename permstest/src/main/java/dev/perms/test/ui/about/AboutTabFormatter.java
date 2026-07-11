package dev.perms.test.ui.about;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

/** Applies rich About-tab formatting without changing the underlying About text. */
public final class AboutTabFormatter {
    private static final int PRIMARY = Color.rgb(38, 94, 166);
    private static final int SECONDARY = Color.rgb(88, 96, 110);
    private static final int CARD_FILL = Color.argb(16, 70, 110, 180);
    private static final int CARD_STROKE = Color.argb(55, 70, 110, 180);
    private static final int LINK_FILL = Color.argb(12, 60, 120, 190);

    private AboutTabFormatter() {
    }

    public static void apply(TextView summary, TextView info, TextView links) {
        if (summary != null) {
            summary.setTextColor(PRIMARY);
            summary.setPadding(0, 0, 0, dp(summary, 4));
        }
        if (info != null) {
            CharSequence raw = info.getText();
            info.setText(formatInfo(raw));
            info.setPadding(dp(info, 12), dp(info, 10), dp(info, 12), dp(info, 10));
            info.setBackground(makeRoundRect(info, CARD_FILL, CARD_STROKE, 14f));
        }
        if (links != null) {
            CharSequence raw = links.getText();
            links.setText(formatLinks(raw));
            links.setMovementMethod(LinkMovementMethod.getInstance());
            links.setLinksClickable(true);
            links.setPadding(dp(links, 12), dp(links, 10), dp(links, 12), dp(links, 10));
            links.setBackground(makeRoundRect(links, LINK_FILL, CARD_STROKE, 14f));
        }
    }

    private static SpannableStringBuilder formatInfo(CharSequence rawText) {
        String text = rawText == null ? "" : rawText.toString();
        SpannableStringBuilder out = new SpannableStringBuilder(text);
        int pos = 0;
        String[] lines = text.split("\n", -1);
        boolean firstContentLine = true;
        for (String line : lines) {
            int start = pos;
            int end = pos + line.length();
            String trimmed = line.trim();
            if (!TextUtils.isEmpty(trimmed)) {
                if (firstContentLine) {
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new RelativeSizeSpan(1.08f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(PRIMARY), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    firstContentLine = false;
                } else if (isHeading(trimmed)) {
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new RelativeSizeSpan(1.08f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(PRIMARY), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (trimmed.startsWith("•")) {
                    out.setSpan(new LeadingMarginSpan.Standard(0, 18), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            pos = end + 1;
        }
        return out;
    }

    private static SpannableStringBuilder formatLinks(CharSequence rawText) {
        String text = rawText == null ? "" : rawText.toString();
        SpannableStringBuilder out = new SpannableStringBuilder(text);
        int pos = 0;
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            int start = pos;
            int end = pos + line.length();
            String trimmed = line.trim();
            if (!TextUtils.isEmpty(trimmed)) {
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    out.setSpan(new URLSpan(trimmed), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(PRIMARY), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(SECONDARY), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            pos = end + 1;
        }
        return out;
    }

    private static boolean isHeading(String line) {
        if (TextUtils.isEmpty(line)) return false;
        String trimmed = line.trim();
        if (trimmed.startsWith("•")) return false;
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return false;
        if (trimmed.indexOf('—') >= 0) return false;
        if (trimmed.endsWith(".") || trimmed.endsWith(":")) return false;
        if (trimmed.length() > 80) return false;

        // About help sections are stored as plain heading lines followed by bullet rows.
        // Keep this detection structural so future wording changes do not leave only some
        // section titles styled blue/underlined.
        return true;
    }

    private static GradientDrawable makeRoundRect(View view, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(view, radiusDp));
        d.setStroke(dp(view, 1), stroke);
        return d;
    }

    private static int dp(View view, float value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }
}
