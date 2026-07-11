package dev.perms.test.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import dev.perms.test.R;

/** Lightweight startup overlay with visible animated status tips. */
public final class StartupLoadingOverlay {
    private static final long MIN_VISIBLE_MS = 2800L;
    private static final long TIP_INTERVAL_MS = 1250L;

    private static final String[] TIPS = new String[] {
            "Shizuku mode keeps privileged shell work clean and reversible.",
            "Debug Output adds targeted traces without noisy normal logs.",
            "VR-compatible Memory panels stay gated by Enable VR Mode.",
            "Popout panels and plugin windows stay behind the Settings gate.",
            "Bundled plugin choices start unchecked; restore only the selected .ptp assets.",
            "HTTP/Web and FTP background services are controlled separately.",
            "Memory payloads stay package-scoped under /sdcard/dev.perms.test/.",
            "Web Interface sections stay locked until their tab gate is enabled."
    };

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout overlay;
    private ImageView logo;
    private TextView tipText;
    private AnimatorSet logoAnimators;
    private int tipIndex;
    private long shownAt;
    private boolean finishing;

    private final Runnable tipTicker = new Runnable() {
        @Override
        public void run() {
            if (finishing || tipText == null) return;
            tipIndex = (tipIndex + 1) % TIPS.length;
            final String next = TIPS[tipIndex];
            tipText.animate()
                    .alpha(0f)
                    .translationY(dp(6))
                    .setDuration(160L)
                    .withEndAction(() -> {
                        if (finishing || tipText == null) return;
                        tipText.setText(next);
                        tipText.setTranslationY(dp(-6));
                        tipText.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(260L)
                                .start();
                    })
                    .start();
            handler.postDelayed(this, TIP_INTERVAL_MS);
        }
    };

    public StartupLoadingOverlay(Activity activity) {
        this.activity = activity;
    }

    public void show() {
        if (activity == null || activity.isFinishing() || overlay != null) return;
        finishing = false;
        shownAt = SystemClock.uptimeMillis();

        overlay = new FrameLayout(activity);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setAlpha(0f);
        overlay.setBackgroundColor(Color.rgb(8, 11, 17));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(18));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.rgb(19, 24, 34));
        cardBg.setCornerRadius(dp(22));
        cardBg.setStroke(dp(1), Color.argb(115, 120, 170, 255));
        card.setBackground(cardBg);
        card.setElevation(dp(10));

        logo = new ImageView(activity);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setAdjustViewBounds(true);
        logo.setContentDescription("PermsTest loading");
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(106), dp(106));
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(logo, logoLp);

        TextView title = new TextView(activity);
        title.setText("PermsTest");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(10);
        card.addView(title, titleLp);

        TextView subtitle = new TextView(activity);
        subtitle.setText("initializing controllers, services, and saved state");
        subtitle.setTextColor(Color.argb(220, 225, 235, 255));
        subtitle.setTextSize(12f);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = dp(3);
        card.addView(subtitle, subtitleLp);

        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        progressLp.topMargin = dp(18);
        card.addView(progress, progressLp);

        tipText = new TextView(activity);
        tipText.setText(TIPS[0]);
        tipText.setTextColor(Color.argb(238, 238, 244, 255));
        tipText.setTextSize(12.5f);
        tipText.setGravity(Gravity.CENTER);
        tipText.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.topMargin = dp(14);
        card.addView(tipText, tipLp);

        TextView footer = new TextView(activity);
        footer.setText("startup diagnostics stay behind Debug Output");
        footer.setTextColor(Color.argb(160, 220, 230, 245));
        footer.setTextSize(11f);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = dp(8);
        card.addView(footer, footerLp);

        int cardWidth = Math.min(dp(370), activity.getResources().getDisplayMetrics().widthPixels - dp(36));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        overlay.addView(card, cardLp);
        activity.addContentView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay.post(() -> {
            if (finishing || overlay == null) return;
            overlay.animate().alpha(1f).setDuration(220L).start();
            startLogoAnimation();
            handler.postDelayed(tipTicker, TIP_INTERVAL_MS);
        });
    }

    public void finish() {
        if (finishing) return;
        long elapsed = Math.max(0L, SystemClock.uptimeMillis() - shownAt);
        long delay = Math.max(0L, MIN_VISIBLE_MS - elapsed);
        handler.postDelayed(this::dismissNow, delay);
    }

    public void dismissNow() {
        finishing = true;
        handler.removeCallbacks(tipTicker);
        if (logoAnimators != null) {
            try { logoAnimators.cancel(); } catch (Throwable ignored) {}
            logoAnimators = null;
        }
        if (logo != null) logo.animate().cancel();
        if (tipText != null) tipText.animate().cancel();
        final FrameLayout target = overlay;
        overlay = null;
        if (target == null) return;
        target.animate()
                .alpha(0f)
                .setDuration(220L)
                .withEndAction(() -> {
                    try {
                        ViewGroup parent = (ViewGroup) target.getParent();
                        if (parent != null) parent.removeView(target);
                    } catch (Throwable ignored) {
                    }
                })
                .start();
    }

    private void startLogoAnimation() {
        if (logo == null || logoAnimators != null) return;
        ObjectAnimator rotate = ObjectAnimator.ofFloat(logo, View.ROTATION, 0f, 360f);
        rotate.setDuration(1800L);
        rotate.setRepeatCount(ValueAnimator.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 1f, 1.08f, 1f);
        scaleX.setDuration(1000L);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 1f, 1.08f, 1f);
        scaleY.setDuration(1000L);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        logoAnimators = new AnimatorSet();
        logoAnimators.playTogether(rotate, scaleX, scaleY);
        logoAnimators.start();
    }

    private int dp(float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
