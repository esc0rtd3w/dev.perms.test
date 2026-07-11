package dev.perms.test.startup;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import dev.perms.test.MainActivity;
import dev.perms.test.R;
import dev.perms.test.device.DeviceDetection;
import dev.perms.test.settings.SettingsPreferenceDefaults;
import dev.perms.test.settings.SettingsPreferenceKeys;
import dev.perms.test.ui.ThemeColorController;

/** Launcher-only loading screen with visible startup animation and rotating operational tips. */
public final class StartupLoadingActivity extends Activity {
    private static final long MIN_VISIBLE_MS = 3200L;
    private static final long TIP_INTERVAL_MS = 2100L;
    private static final long STAGE_INTERVAL_MS = 1350L;
    private static final long DOT_INTERVAL_MS = 300L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView tipText;
    private TextView stageText;
    private TextView dotsText;
    private ImageView logo;
    private View logoFrame;
    private AnimatorSet logoAnimator;
    private int tipIndex;
    private int stageIndex;
    private int dotIndex;
    private boolean finishing;

    private String[] tips;
    private String[] stages;

    private final Runnable tipTicker = new Runnable() {
        @Override
        public void run() {
            if (finishing || tipText == null || tips == null || tips.length == 0) return;
            String currentStage = (stages != null && stages.length > 0) ? stages[stageIndex] : "";
            tipIndex = StartupLoadingTextPool.nextTipIndexForStage(tips, tipIndex, currentStage);
            final String nextTip = tips[tipIndex];
            tipText.animate()
                    .alpha(0f)
                    .translationY(dp(4))
                    .setDuration(130L)
                    .withEndAction(() -> {
                        if (finishing || tipText == null) return;
                        tipText.setText(nextTip);
                        tipText.setTranslationY(-dp(4));
                        tipText.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(210L)
                                .start();
                    })
                    .start();
            handler.postDelayed(this, TIP_INTERVAL_MS);
        }
    };

    private final Runnable stageTicker = new Runnable() {
        @Override
        public void run() {
            if (finishing || stageText == null || stages == null || stages.length == 0) return;
            stageIndex = nextIndex(stageIndex, stages);
            final String nextStage = stages[stageIndex];
            stageText.animate()
                    .alpha(0f)
                    .translationY(dp(3))
                    .setDuration(100L)
                    .withEndAction(() -> {
                        if (finishing || stageText == null) return;
                        stageText.setText(nextStage);
                        stageText.setTranslationY(-dp(3));
                        stageText.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(170L)
                                .start();
                    })
                    .start();
            handler.postDelayed(this, STAGE_INTERVAL_MS);
        }
    };

    private final Runnable dotTicker = new Runnable() {
        @Override
        public void run() {
            if (finishing || dotsText == null) return;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup_loading);
        ThemeColorController.applyToStartup(this, findViewById(R.id.startupRoot));
        SettingsPreferenceDefaults.ensure(this, SettingsPreferenceKeys.PREFS);
        DeviceDetection.Info deviceInfo = DeviceDetection.applyAutomaticProfile(this);

        StartupLoadingTextPool.Sequence textSequence = StartupLoadingTextPool.createSequence(this);
        tips = textSequence.tips;
        stages = textSequence.stages;
        tipIndex = textSequence.tipIndex;
        stageIndex = textSequence.stageIndex;

        logo = findViewById(R.id.imgStartupLogo);
        logoFrame = findViewById(R.id.startupLogoFrame);
        tipText = findViewById(R.id.txtStartupTip);
        stageText = findViewById(R.id.txtStartupStage);
        dotsText = findViewById(R.id.txtStartupDots);
        TextView subtitleText = findViewById(R.id.txtStartupSubtitle);
        if (subtitleText != null && deviceInfo != null) {
            subtitleText.setText(deviceInfo.startupSubtitle());
        }
        if (tips.length > 0) {
            String currentStage = (stages.length > 0) ? stages[stageIndex] : "";
            tipIndex = StartupLoadingTextPool.balancedTipIndex(tips, tipIndex, currentStage);
        }
        if (tipText != null && tips.length > 0) {
            tipText.setText(tips[tipIndex]);
        }
        if (stageText != null && stages.length > 0) {
            stageText.setText(stages[stageIndex]);
        }
        fitCardWidth();
        View root = findViewById(R.id.startupRoot);
        if (root != null) {
            root.setAlpha(0f);
            root.post(() -> {
                if (finishing) return;
                root.animate().alpha(1f).setDuration(220L).start();
                startAnimations();
                handler.postDelayed(stageTicker, STAGE_INTERVAL_MS);
                handler.postDelayed(tipTicker, TIP_INTERVAL_MS);
                handler.post(dotTicker);
            });
        } else {
            startAnimations();
            handler.postDelayed(stageTicker, STAGE_INTERVAL_MS);
            handler.postDelayed(tipTicker, TIP_INTERVAL_MS);
            handler.post(dotTicker);
        }
        handler.postDelayed(this::openMainActivity, MIN_VISIBLE_MS);
    }

    @Override
    protected void onDestroy() {
        finishing = true;
        handler.removeCallbacksAndMessages(null);
        if (logoAnimator != null) {
            try { logoAnimator.cancel(); } catch (Throwable ignored) {}
            logoAnimator = null;
        }
        try { if (tipText != null) tipText.animate().cancel(); } catch (Throwable ignored) {}
        try { if (logo != null) logo.animate().cancel(); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    private void openMainActivity() {
        if (finishing || isFinishing()) return;
        finishing = true;
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra(StartupLoadingHandoffOverlay.EXTRA_SHOW_HANDOFF, true);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private void startAnimations() {
        if (logo == null || logoAnimator != null) return;
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

        if (logoFrame != null) {
            ObjectAnimator frameScaleX = ObjectAnimator.ofFloat(logoFrame, View.SCALE_X, 1f, 1.045f, 1f);
            frameScaleX.setDuration(1150L);
            frameScaleX.setRepeatCount(ValueAnimator.INFINITE);
            frameScaleX.setInterpolator(new AccelerateDecelerateInterpolator());
            ObjectAnimator frameScaleY = ObjectAnimator.ofFloat(logoFrame, View.SCALE_Y, 1f, 1.045f, 1f);
            frameScaleY.setDuration(1150L);
            frameScaleY.setRepeatCount(ValueAnimator.INFINITE);
            frameScaleY.setInterpolator(new AccelerateDecelerateInterpolator());
            logoAnimator = new AnimatorSet();
            logoAnimator.playTogether(rotate, scaleX, scaleY, alpha, frameScaleX, frameScaleY);
        } else {
            logoAnimator = new AnimatorSet();
            logoAnimator.playTogether(rotate, scaleX, scaleY, alpha);
        }
        logoAnimator.start();
    }

    private void fitCardWidth() {
        View card = findViewById(R.id.startupCard);
        if (card == null) return;
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp == null) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int maxWidth = dp(420);
        int minMargin = dp(24) * 2;
        lp.width = Math.min(maxWidth, Math.max(dp(280), screenWidth - minMargin));
        card.setLayoutParams(lp);
    }

    private int nextIndex(int current, String[] values) {
        if (values == null || values.length == 0) return 0;
        return (current + 1) % values.length;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
