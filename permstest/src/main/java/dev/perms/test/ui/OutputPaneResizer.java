package dev.perms.test.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;

/** Owns the shared bottom output pane resize handle and height persistence callbacks. */
public final class OutputPaneResizer {
    public interface State {
        boolean shouldRememberHeight();
        void persistStatePx(int currentHeightPx, int restoreHeightPx, boolean minimized);
        void onHeightChanged(int px);
    }

    private static final int DEFAULT_HEIGHT_DP = 230;
    private static final int MIN_HEIGHT_DP = 120;
    private static final int HIDDEN_HEIGHT_DP = 1;
    private static final int BOTTOM_SNAP_DP = 8;
    private static final int HANDLE_RADIUS_DP = 8;
    private static final long DOUBLE_TAP_MS = 320L;
    private static final long DRAG_TAP_SUPPRESS_MS = 420L;
    private static final float MAX_SCREEN_FRACTION = 0.7f;

    private final Context context;
    private final State state;

    private View outputFrame;
    private View resizeBar;
    private int currentHeightPx = -1;
    private int restoreHeightPx = -1;
    private long lastTapUpMs;
    private long lastDragUpMs;
    private int touchSlopPx;

    public OutputPaneResizer(Context context, State state) {
        this.context = context;
        this.state = state;
    }

    public void bind(View outputFrame, View resizeBar, int restoredHeightPx, boolean restoredMinimized, int restoredRestoreHeightPx) {
        this.outputFrame = outputFrame;
        this.resizeBar = resizeBar;
        if (outputFrame == null || resizeBar == null) return;

        int minHeightPx = dpToPx(MIN_HEIGHT_DP);
        int hiddenPx = dpToPx(HIDDEN_HEIGHT_DP);
        touchSlopPx = getTouchSlopPx();
        restoreHeightPx = restoredRestoreHeightPx >= minHeightPx
                ? restoredRestoreHeightPx
                : (restoredHeightPx >= minHeightPx ? restoredHeightPx : dpToPx(DEFAULT_HEIGHT_DP));
        int startHeightPx = restoredMinimized ? hiddenPx : (restoredHeightPx > 0 ? restoredHeightPx : minHeightPx);
        setHeightPx(startHeightPx);

        resizeBar.setClickable(true);
        resizeBar.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            int startHeight;
            boolean moved;
            boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                try {
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downY = ev.getRawY();
                            startHeight = getCurrentLayoutHeight();
                            moved = false;
                            dragging = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float deltaY = ev.getRawY() - downY;
                            if (!dragging && Math.abs(deltaY) < touchSlopPx) {
                                return true;
                            }
                            dragging = true;
                            moved = true;
                            lastTapUpMs = 0L;
                            setHeightPx(clampHeightPx((int) (startHeight - deltaY)));
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (moved || dragging) {
                                setHeightPx(snapFinalHeightPx(getCurrentLayoutHeight()));
                                persistHeightIfEnabled();
                                lastTapUpMs = 0L;
                                lastDragUpMs = ev.getEventTime();
                                return true;
                            }
                            try {
                                v.performClick();
                            } catch (Throwable ignored) {
                            }
                            long now = ev.getEventTime();
                            if (now - lastDragUpMs > DRAG_TAP_SUPPRESS_MS
                                    && now - lastTapUpMs <= DOUBLE_TAP_MS) {
                                toggleMinimized();
                                lastTapUpMs = 0L;
                                return true;
                            }
                            lastTapUpMs = now;
                            persistHeightIfEnabled();
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            if (moved || dragging) {
                                setHeightPx(snapFinalHeightPx(getCurrentLayoutHeight()));
                                persistHeightIfEnabled();
                            }
                            lastTapUpMs = 0L;
                            return true;
                    }
                } catch (Throwable ignored) {
                }
                return false;
            }
        });
    }

    public void applyHandleTheme(View handle) {
        try {
            if (handle == null) return;
            int color = ThemeColorController.isCustom(context)
                    ? ThemeColorController.getCustomColor(context)
                    : 0xFFD6BCFF;
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(dpToPx(HANDLE_RADIUS_DP));
            drawable.setColor(color);
            drawable.setStroke(dpToPx(1), Color.argb(0xAA, 0xFF, 0xFF, 0xFF));
            handle.setBackground(drawable);
            handle.setAlpha(0.85f);
        } catch (Throwable ignored) {
        }
    }

    public void setHeightPx(int px) {
        try {
            if (px < 0 || outputFrame == null) return;
            ViewGroup.LayoutParams lp = outputFrame.getLayoutParams();
            if (lp == null) return;
            lp.height = px;
            outputFrame.setLayoutParams(lp);
            currentHeightPx = px;
            keepContentVisibleWhileSliding();
            if (px >= dpToPx(MIN_HEIGHT_DP)) restoreHeightPx = px;
            if (state != null) state.onHeightChanged(px);
        } catch (Throwable ignored) {
        }
    }

    public void persistHeightIfEnabled() {
        try {
            if (currentHeightPx < 0) return;
            if (state != null && !state.shouldRememberHeight()) return;
            int minHeightPx = dpToPx(MIN_HEIGHT_DP);
            int normalHeightPx = restoreHeightPx >= minHeightPx
                    ? restoreHeightPx
                    : (currentHeightPx >= minHeightPx ? currentHeightPx : dpToPx(DEFAULT_HEIGHT_DP));
            if (state != null) state.persistStatePx(currentHeightPx, normalHeightPx, isMinimized());
        } catch (Throwable ignored) {
        }
    }

    public void resetToDefaultHeight() {
        try {
            setHeightPx(dpToPx(DEFAULT_HEIGHT_DP));
            persistHeightIfEnabled();
        } catch (Throwable ignored) {
        }
    }

    public int getCurrentHeightPx() {
        return currentHeightPx;
    }

    public int getRestoreHeightPx() {
        int minHeightPx = dpToPx(MIN_HEIGHT_DP);
        if (restoreHeightPx >= minHeightPx) return restoreHeightPx;
        return currentHeightPx >= minHeightPx ? currentHeightPx : dpToPx(DEFAULT_HEIGHT_DP);
    }

    public boolean isMinimized() {
        return getCurrentLayoutHeight() <= dpToPx(HIDDEN_HEIGHT_DP) + 1;
    }

    private void toggleMinimized() {
        try {
            int minHeightPx = dpToPx(MIN_HEIGHT_DP);
            int hiddenPx = dpToPx(HIDDEN_HEIGHT_DP);
            if (getCurrentLayoutHeight() <= hiddenPx + 1) {
                int target = restoreHeightPx >= minHeightPx ? restoreHeightPx : dpToPx(DEFAULT_HEIGHT_DP);
                setHeightPx(target);
            } else {
                restoreHeightPx = Math.max(getCurrentLayoutHeight(), minHeightPx);
                setHeightPx(hiddenPx);
            }
            persistHeightIfEnabled();
        } catch (Throwable ignored) {
        }
    }

    private int getCurrentLayoutHeight() {
        try {
            if (outputFrame != null && outputFrame.getLayoutParams() != null) {
                int height = outputFrame.getLayoutParams().height;
                if (height >= 0) return height;
            }
        } catch (Throwable ignored) {
        }
        return currentHeightPx >= 0 ? currentHeightPx : dpToPx(MIN_HEIGHT_DP);
    }

    private int getTouchSlopPx() {
        try {
            int slop = ViewConfiguration.get(context).getScaledTouchSlop();
            return slop > 0 ? slop : dpToPx(4);
        } catch (Throwable ignored) {
            return dpToPx(4);
        }
    }

    private int clampHeightPx(int heightPx) {
        int hiddenPx = dpToPx(HIDDEN_HEIGHT_DP);
        int maxHeightPx = hiddenPx;
        try {
            maxHeightPx = (int) (context.getResources().getDisplayMetrics().heightPixels * MAX_SCREEN_FRACTION);
        } catch (Throwable ignored) {
        }
        if (maxHeightPx < hiddenPx) maxHeightPx = hiddenPx;
        if (heightPx < hiddenPx) return hiddenPx;
        if (heightPx > maxHeightPx) return maxHeightPx;
        return heightPx;
    }

    private int snapFinalHeightPx(int heightPx) {
        int hiddenPx = dpToPx(HIDDEN_HEIGHT_DP);
        int snapPx = dpToPx(BOTTOM_SNAP_DP);
        int clamped = clampHeightPx(heightPx);
        if (clamped <= snapPx) return hiddenPx;
        return clamped;
    }

    private void keepContentVisibleWhileSliding() {
        try {
            if (!(outputFrame instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) outputFrame;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child != null && child.getVisibility() == View.INVISIBLE) {
                    child.setVisibility(View.VISIBLE);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private int dpToPx(int dp) {
        try {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    dp,
                    context.getResources().getDisplayMetrics());
        } catch (Throwable ignored) {
            return dp;
        }
    }
}
