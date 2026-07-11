package dev.perms.test.startup;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import dev.perms.test.R;
import dev.perms.test.device.DeviceDetection;
import dev.perms.test.ui.ThemeColorController;

/** Keeps the startup screen visible until the first main UI frame is ready. */
public final class StartupLoadingHandoffOverlay {
    public static final String EXTRA_SHOW_HANDOFF = "dev.perms.test.extra.SHOW_STARTUP_HANDOFF";

    private static final long MIN_VISIBLE_MS = 450L;
    private static final long DISMISS_DELAY_MS = 160L;
    private static final long FADE_OUT_MS = 220L;
    private static final long DOT_INTERVAL_MS = 300L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View overlay;
    private final TextView dotsText;
    private final long attachedAt;
    private AnimatorSet logoAnimator;
    private int dotIndex;
    private boolean dismissed;

    private final Runnable dotTicker = new Runnable() {
        @Override
        public void run() {
            if (dismissed || dotsText == null) return;
            dotIndex = (dotIndex + 1) % 4;
            switch (dotIndex) {
                case 0:
                    dotsText.setText("•  •  •");
                    break;
                case 1:
                    dotsText.setText("●  •  •");
                    break;
                case 2:
                    dotsText.setText("•  ●  •");
                    break;
                default:
                    dotsText.setText("•  •  ●");
                    break;
            }
            handler.postDelayed(this, DOT_INTERVAL_MS);
        }
    };

    private StartupLoadingHandoffOverlay(Activity activity, View overlay) {
        this.overlay = overlay;
        ThemeColorController.applyToStartup(activity, overlay);
        fitCardWidth(activity, overlay);
        this.dotsText = overlay.findViewById(R.id.txtStartupDots);
        this.attachedAt = System.currentTimeMillis();
        TextView subtitleText = overlay.findViewById(R.id.txtStartupSubtitle);
        if (subtitleText != null) {
            subtitleText.setText(DeviceDetection.startupSubtitle(activity));
        }
        StartupLoadingTextPool.TextSelection textSelection = StartupLoadingTextPool.randomSelection(activity);
        TextView stageText = overlay.findViewById(R.id.txtStartupStage);
        if (stageText != null && textSelection.stageText.length() > 0) {
            stageText.setText(textSelection.stageText);
        }
        TextView tipText = overlay.findViewById(R.id.txtStartupTip);
        if (tipText != null && textSelection.tipText.length() > 0) {
            tipText.setText(textSelection.tipText);
        }
        startLogoAnimation(overlay);
        handler.post(dotTicker);
    }


    public static StartupLoadingHandoffOverlay showAsContent(Activity activity) {
        if (activity == null) return null;
        try {
            View overlay = LayoutInflater.from(activity).inflate(R.layout.activity_startup_loading, null, false);
            activity.setContentView(overlay);
            return new StartupLoadingHandoffOverlay(activity, overlay);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static StartupLoadingHandoffOverlay attachIfRequested(Activity activity, Intent intent) {
        if (activity == null || intent == null || !intent.getBooleanExtra(EXTRA_SHOW_HANDOFF, false)) {
            return null;
        }
        try {
            ViewGroup decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView().findViewById(android.R.id.content);
            if (decor == null) return null;
            View overlay = LayoutInflater.from(activity).inflate(R.layout.activity_startup_loading, decor, false);
            decor.addView(overlay, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return new StartupLoadingHandoffOverlay(activity, overlay);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void dismissWhenContentReady(View contentRoot) {
        if (dismissed) return;
        Runnable dismiss = () -> {
            long elapsed = System.currentTimeMillis() - attachedAt;
            long delay = Math.max(DISMISS_DELAY_MS, MIN_VISIBLE_MS - elapsed);
            handler.postDelayed(this::fadeOutAndRemove, Math.max(0L, delay));
        };
        if (contentRoot != null) {
            contentRoot.post(dismiss);
        } else {
            handler.post(dismiss);
        }
    }

    public void dismissNow() {
        if (dismissed) return;
        dismissed = true;
        handler.removeCallbacksAndMessages(null);
        stopLogoAnimation();
        try {
            ViewGroup parent = overlay.getParent() instanceof ViewGroup ? (ViewGroup) overlay.getParent() : null;
            if (parent != null) parent.removeView(overlay);
        } catch (Throwable ignored) {
        }
    }

    private void fadeOutAndRemove() {
        if (dismissed) return;
        dismissed = true;
        handler.removeCallbacksAndMessages(null);
        try {
            overlay.animate()
                    .alpha(0f)
                    .setDuration(FADE_OUT_MS)
                    .withEndAction(this::removeOverlay)
                    .start();
        } catch (Throwable ignored) {
            removeOverlay();
        }
    }

    private void removeOverlay() {
        stopLogoAnimation();
        try {
            ViewGroup parent = overlay.getParent() instanceof ViewGroup ? (ViewGroup) overlay.getParent() : null;
            if (parent != null) parent.removeView(overlay);
        } catch (Throwable ignored) {
        }
    }


    private static void fitCardWidth(Activity activity, View root) {
        try {
            View card = root == null ? null : root.findViewById(R.id.startupCard);
            if (activity == null || card == null) return;
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            if (lp == null) return;
            float density = activity.getResources().getDisplayMetrics().density;
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int maxWidth = Math.round(420f * density);
            int minWidth = Math.round(280f * density);
            int minMargin = Math.round(48f * density);
            lp.width = Math.min(maxWidth, Math.max(minWidth, screenWidth - minMargin));
            card.setLayoutParams(lp);
        } catch (Throwable ignored) {
        }
    }

    private void startLogoAnimation(View root) {
        ImageView logo = root.findViewById(R.id.imgStartupLogo);
        View logoFrame = root.findViewById(R.id.startupLogoFrame);
        if (logo == null) return;
        ObjectAnimator rotate = ObjectAnimator.ofFloat(logo, View.ROTATION, -8f, 8f, -8f);
        rotate.setDuration(1050L);
        rotate.setRepeatCount(ValueAnimator.INFINITE);
        rotate.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.94f, 1.08f, 0.94f);
        scaleX.setDuration(900L);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.94f, 1.08f, 0.94f);
        scaleY.setDuration(900L);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator alpha = ObjectAnimator.ofFloat(logo, View.ALPHA, 0.78f, 1f, 0.78f);
        alpha.setDuration(900L);
        alpha.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setInterpolator(new LinearInterpolator());

        logoAnimator = new AnimatorSet();
        if (logoFrame != null) {
            ObjectAnimator frameScaleX = ObjectAnimator.ofFloat(logoFrame, View.SCALE_X, 1f, 1.045f, 1f);
            frameScaleX.setDuration(1150L);
            frameScaleX.setRepeatCount(ValueAnimator.INFINITE);
            frameScaleX.setInterpolator(new AccelerateDecelerateInterpolator());
            ObjectAnimator frameScaleY = ObjectAnimator.ofFloat(logoFrame, View.SCALE_Y, 1f, 1.045f, 1f);
            frameScaleY.setDuration(1150L);
            frameScaleY.setRepeatCount(ValueAnimator.INFINITE);
            frameScaleY.setInterpolator(new AccelerateDecelerateInterpolator());
            logoAnimator.playTogether(rotate, scaleX, scaleY, alpha, frameScaleX, frameScaleY);
        } else {
            logoAnimator.playTogether(rotate, scaleX, scaleY, alpha);
        }
        logoAnimator.start();
    }

    private void stopLogoAnimation() {
        if (logoAnimator != null) {
            try { logoAnimator.cancel(); } catch (Throwable ignored) {}
            logoAnimator = null;
        }
    }
}
