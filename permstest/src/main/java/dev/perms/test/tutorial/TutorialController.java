package dev.perms.test.tutorial;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.core.widget.NestedScrollView;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.settings.SettingsPreferenceKeys;
import dev.perms.test.ui.CollapsibleGroupboxController;

/** Coordinates first-visit tab tutorials and keeps the runtime hooks out of MainActivity. */
public final class TutorialController {
    private static final long SHOW_DELAY_MS = 280L;
    private static final int MAX_TARGET_ATTEMPTS = 4;

    private final Activity activity;
    private final ActivityMainBinding binding;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TutorialOverlayView overlay;
    private TutorialTabSpec activeSpec;
    private CollapsibleGroupboxController.ExpandSession stepExpansion;
    private int activeStepIndex;
    private int pendingTabIndex = -1;
    private boolean showing;

    public TutorialController(Activity activity, ActivityMainBinding binding, SharedPreferences prefs) {
        this.activity = activity;
        this.binding = binding;
        this.prefs = prefs;
    }

    public static void resetProgress(SharedPreferences prefs) {
        if (prefs == null) return;
        try {
            SharedPreferences.Editor ed = prefs.edit();
            for (String key : prefs.getAll().keySet()) {
                if (key != null && key.startsWith(SettingsPreferenceKeys.TUTORIAL_TAB_SEEN_PREFIX)) {
                    ed.remove(key);
                }
            }
            ed.putBoolean(SettingsPreferenceKeys.DISABLE_TUTORIAL, false);
            ed.apply();
        } catch (Throwable ignored) {
        }
    }

    public void onTabVisible(int tabIndex) {
        if (activity == null || binding == null || prefs == null) return;
        if (isTutorialDisabled()) {
            dismissOverlay();
            return;
        }
        TutorialTabSpec spec = TutorialCatalog.forTab(tabIndex);
        if (spec == null || spec.isEmpty() || isTabSeen(spec)) return;
        if (showing && activeSpec != null && activeSpec.tabIndex != tabIndex) {
            dismissOverlay();
        }

        pendingTabIndex = tabIndex;
        mainHandler.removeCallbacks(showPendingTabRunnable);
        mainHandler.postDelayed(showPendingTabRunnable, SHOW_DELAY_MS);
    }

    public void dismiss() {
        dismissOverlay();
        mainHandler.removeCallbacks(showPendingTabRunnable);
    }

    private final Runnable showPendingTabRunnable = () -> {
        if (pendingTabIndex < 0 || isTutorialDisabled()) return;
        TutorialTabSpec spec = TutorialCatalog.forTab(pendingTabIndex);
        pendingTabIndex = -1;
        if (spec == null || spec.isEmpty() || isTabSeen(spec)) return;
        activeSpec = spec;
        activeStepIndex = 0;
        showing = true;
        showCurrentStepOrAdvance();
    };

    private void showCurrentStepOrAdvance() {
        if (!showing || activeSpec == null) return;
        if (isTutorialDisabled()) {
            dismissOverlay();
            return;
        }
        if (activeStepIndex >= activeSpec.steps.size()) {
            markTabSeen(activeSpec);
            dismissOverlay();
            return;
        }

        TutorialStep step = activeSpec.steps.get(activeStepIndex);
        View anchor = findExistingView(step.targetId);
        if (anchor == null) anchor = findExistingView(step.highlightTargetId());
        if (anchor == null) {
            advancePastMissingStep();
            return;
        }

        restoreStepExpansion();
        stepExpansion = CollapsibleGroupboxController.expandGroupboxesContaining(anchor);
        anchor.requestLayout();
        mainHandler.postDelayed(() -> showPreparedStep(step, 0), 140L);
    }

    private void showPreparedStep(TutorialStep step, int attempt) {
        if (!showing || activeSpec == null || step == null) return;
        View scrollTarget = findMeasuredView(step.highlightTargetId());
        if (scrollTarget == null) scrollTarget = findMeasuredView(step.targetId);
        if (scrollTarget == null) {
            retryOrAdvance(step, attempt);
            return;
        }

        scrollTargetIntoView(scrollTarget);
        final View postedTarget = scrollTarget;
        postedTarget.postDelayed(() -> {
            View highlightTarget = findMeasuredView(step.highlightTargetId());
            if (highlightTarget == null) highlightTarget = findMeasuredView(step.targetId);
            if (highlightTarget == null) {
                retryOrAdvance(step, attempt);
                return;
            }
            ensureOverlay();
            if (overlay == null) return;
            if (overlay.getWidth() <= 0 || overlay.getHeight() <= 0) {
                View finalHighlightTarget = highlightTarget;
                overlay.post(() -> showStepOnOverlay(finalHighlightTarget, step, attempt));
            } else {
                showStepOnOverlay(highlightTarget, step, attempt);
            }
        }, 160L);
    }

    private void showStepOnOverlay(View target, TutorialStep step, int attempt) {
        if (overlay == null || !showing || activeSpec == null) return;
        if (!isMeasuredInVisibleHierarchy(target)) {
            retryOrAdvance(step, attempt);
            return;
        }
        boolean shown = overlay.showStep(target, activeSpec, step, activeStepIndex, activeSpec.steps.size());
        if (!shown) {
            retryOrAdvance(step, attempt);
        }
    }

    private void retryOrAdvance(TutorialStep step, int attempt) {
        if (!showing || activeSpec == null) return;
        if (attempt < MAX_TARGET_ATTEMPTS) {
            mainHandler.postDelayed(() -> showPreparedStep(step, attempt + 1), 140L);
        } else {
            advancePastMissingStep();
        }
    }

    private void nextStep() {
        restoreStepExpansion();
        activeStepIndex++;
        showCurrentStepOrAdvance();
    }

    private void skipTab() {
        restoreStepExpansion();
        if (activeSpec != null) markTabSeen(activeSpec);
        dismissOverlay();
    }

    private void advancePastMissingStep() {
        restoreStepExpansion();
        activeStepIndex++;
        showCurrentStepOrAdvance();
    }

    private void disableTutorial() {
        if (prefs != null) {
            prefs.edit().putBoolean(SettingsPreferenceKeys.DISABLE_TUTORIAL, true).apply();
            try {
                if (binding != null && binding.tabSettings != null && binding.tabSettings.chkDisableTutorial != null) {
                    binding.tabSettings.chkDisableTutorial.setChecked(true);
                }
            } catch (Throwable ignored) {
            }
        }
        dismissOverlay();
    }

    private boolean isTutorialDisabled() {
        return prefs != null && prefs.getBoolean(SettingsPreferenceKeys.DISABLE_TUTORIAL, false);
    }

    private boolean isTabSeen(TutorialTabSpec spec) {
        return prefs != null && spec != null
                && prefs.getBoolean(SettingsPreferenceKeys.TUTORIAL_TAB_SEEN_PREFIX + spec.key, false);
    }

    private void markTabSeen(TutorialTabSpec spec) {
        if (prefs != null && spec != null) {
            prefs.edit().putBoolean(SettingsPreferenceKeys.TUTORIAL_TAB_SEEN_PREFIX + spec.key, true).apply();
        }
    }

    private void ensureOverlay() {
        if (overlay != null && overlay.getParent() != null) return;
        if (activity == null) return;
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        overlay = new TutorialOverlayView(activity);
        overlay.setListener(new TutorialOverlayView.Listener() {
            @Override
            public void onHighlightedTargetTapped() {
                nextStep();
            }

            @Override
            public void onNextRequested() {
                nextStep();
            }

            @Override
            public void onSkipRequested() {
                skipTab();
            }

            @Override
            public void onDisableRequested() {
                disableTutorial();
            }
        });
        root.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void dismissOverlay() {
        restoreStepExpansion();
        showing = false;
        activeSpec = null;
        activeStepIndex = 0;
        try {
            if (overlay != null && overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
        } catch (Throwable ignored) {
        }
        overlay = null;
    }

    private View findExistingView(int targetId) {
        if (binding == null || binding.getRoot() == null || targetId == 0) return null;
        try {
            return binding.getRoot().findViewById(targetId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private View findMeasuredView(int targetId) {
        View target = findExistingView(targetId);
        return isMeasuredInVisibleHierarchy(target) ? target : null;
    }

    private void restoreStepExpansion() {
        try {
            if (stepExpansion != null && !stepExpansion.isEmpty()) {
                stepExpansion.restore();
            }
        } catch (Throwable ignored) {
        }
        stepExpansion = null;
    }

    private boolean isMeasuredInVisibleHierarchy(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        View parent = view;
        while (parent != null) {
            if (parent.getVisibility() != View.VISIBLE) return false;
            if (!(parent.getParent() instanceof View)) break;
            parent = (View) parent.getParent();
        }
        return true;
    }

    private void scrollTargetIntoView(View target) {
        try {
            NestedScrollView nested = findNestedScrollParent(target);
            if (nested != null) {
                Rect rect = new Rect();
                target.getDrawingRect(rect);
                nested.offsetDescendantRectToMyCoords(target, rect);
                int scrollY = computeScrollY(nested.getScrollY(), nested.getHeight(), rect);
                nested.scrollTo(0, scrollY);
                return;
            }

            ScrollView scroll = findScrollParent(target);
            if (scroll != null) {
                Rect rect = new Rect();
                target.getDrawingRect(rect);
                scroll.offsetDescendantRectToMyCoords(target, rect);
                int scrollY = computeScrollY(scroll.getScrollY(), scroll.getHeight(), rect);
                scroll.scrollTo(0, scrollY);
            }
        } catch (Throwable ignored) {
        }
    }

    private int computeScrollY(int currentY, int viewportHeight, Rect targetRect) {
        int padding = dp(40);
        int bottomPadding = dp(112);
        int desiredY = currentY;
        int visibleTop = currentY + padding;
        int visibleBottom = currentY + Math.max(dp(96), viewportHeight - bottomPadding);
        if (targetRect.top < visibleTop) {
            desiredY = Math.max(0, targetRect.top - padding);
        } else if (targetRect.bottom > visibleBottom) {
            desiredY = Math.max(0, targetRect.bottom - Math.max(dp(96), viewportHeight - bottomPadding));
        }
        return Math.max(0, desiredY);
    }

    private NestedScrollView findNestedScrollParent(View view) {
        View cur = view;
        while (cur != null) {
            if (cur instanceof NestedScrollView) return (NestedScrollView) cur;
            if (!(cur.getParent() instanceof View)) return null;
            cur = (View) cur.getParent();
        }
        return null;
    }

    private ScrollView findScrollParent(View view) {
        View cur = view;
        while (cur != null) {
            if (cur instanceof ScrollView) return (ScrollView) cur;
            if (!(cur.getParent() instanceof View)) return null;
            cur = (View) cur.getParent();
        }
        return null;
    }

    private int dp(int value) {
        if (activity == null) return value;
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
