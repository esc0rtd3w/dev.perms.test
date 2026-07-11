package dev.perms.test.kiosk;

import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Detects corner-tap kiosk exit patterns plus the recovery tap-then-hold sequence. */
public final class KioskExitPattern {
    public interface Callback {
        void onExitPatternMatched(boolean recovery);
    }

    private static final long STEP_TIMEOUT_MS = 3500L;
    private static final long RECOVERY_INACTIVITY_RESET_MS = 2500L;
    private static final long RECOVERY_TIMEOUT_MS = 30000L;
    private static final long RECOVERY_LONG_PRESS_MIN_MS = 7000L;
    private static final long RECOVERY_LONG_PRESS_MAX_MS = 13000L;
    private static final int RECOVERY_TAPS_BEFORE_LONG_PRESS = 21;

    private final Callback callback;
    private final ArrayList<Step> steps = new ArrayList<>();
    private int stepIndex;
    private int stepTapCount;
    private long lastStepTapTime;
    private float recoveryStartX;
    private float recoveryStartY;
    private int recoveryTapCount;
    private long recoveryStartTime;
    private long recoveryLastTapTime;
    private boolean recoveryLongPressArmed;
    private long recoveryLongPressStartTime;

    public KioskExitPattern(String pattern, Callback callback) {
        this.callback = callback;
        parse(pattern);
    }

    public boolean onTouch(View view, MotionEvent ev) {
        if (view == null || ev == null) return false;
        long now = System.currentTimeMillis();
        float x = ev.getX();
        float y = ev.getY();
        handleRecovery(view, ev, x, y, now);
        if (ev.getActionMasked() != MotionEvent.ACTION_UP) return false;
        int corner = cornerFor(view, x, y);
        if (corner != Corner.NONE) {
            handlePatternCorner(corner, now);
        }
        return false;
    }

    private void handlePatternCorner(int corner, long now) {
        if (steps.isEmpty()) return;
        if (lastStepTapTime > 0 && now - lastStepTapTime > STEP_TIMEOUT_MS) resetPattern();
        Step step = steps.get(stepIndex);
        if (corner != step.corner) {
            resetPattern();
            step = steps.get(stepIndex);
            if (corner != step.corner) return;
        }
        stepTapCount++;
        lastStepTapTime = now;
        if (stepTapCount < step.count) return;
        stepIndex++;
        stepTapCount = 0;
        if (stepIndex >= steps.size()) {
            resetPattern();
            if (callback != null) callback.onExitPatternMatched(false);
        }
    }

    private void handleRecovery(View view, MotionEvent ev, float x, float y, long now) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            resetRecovery();
            return;
        }
        if (recoveryLongPressArmed) {
            if (action == MotionEvent.ACTION_MOVE && !isWithinRecoveryArea(view, x, y)) {
                resetRecovery();
                return;
            }
            if (action == MotionEvent.ACTION_UP) {
                long held = now - recoveryLongPressStartTime;
                boolean accepted = held >= RECOVERY_LONG_PRESS_MIN_MS && held <= RECOVERY_LONG_PRESS_MAX_MS;
                resetRecovery();
                if (accepted && callback != null) callback.onExitPatternMatched(true);
            }
            return;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            if (recoveryTapCount == RECOVERY_TAPS_BEFORE_LONG_PRESS && isValidRecoveryContinuation(view, x, y, now)) {
                armRecoveryLongPress(view);
            } else if (recoveryTapCount > 0 && !isValidRecoveryContinuation(view, x, y, now)) {
                resetRecovery();
            }
            return;
        }
        if (action != MotionEvent.ACTION_UP) return;
        if (recoveryTapCount <= 0 || !isValidRecoveryContinuation(view, x, y, now)) {
            recoveryStartX = x;
            recoveryStartY = y;
            recoveryStartTime = now;
            recoveryLastTapTime = now;
            recoveryTapCount = 1;
            return;
        }
        if (recoveryTapCount >= RECOVERY_TAPS_BEFORE_LONG_PRESS) {
            resetRecovery();
            return;
        }
        recoveryTapCount++;
        recoveryLastTapTime = now;
    }

    private boolean isValidRecoveryContinuation(View view, float x, float y, long now) {
        return now - recoveryLastTapTime <= RECOVERY_INACTIVITY_RESET_MS
                && now - recoveryStartTime <= RECOVERY_TIMEOUT_MS
                && isWithinRecoveryArea(view, x, y);
    }

    private boolean isWithinRecoveryArea(View view, float x, float y) {
        float density = Math.max(1f, view.getResources().getDisplayMetrics().density);
        float maxMove = Math.max(180f * density, Math.min(view.getWidth(), view.getHeight()) * 0.28f);
        return distance(recoveryStartX, recoveryStartY, x, y) <= maxMove;
    }

    private void armRecoveryLongPress(View view) {
        recoveryLongPressArmed = true;
        recoveryLastTapTime = System.currentTimeMillis();
        recoveryLongPressStartTime = System.currentTimeMillis();
    }

    private static float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private int cornerFor(View view, float x, float y) {
        int w = Math.max(1, view.getWidth());
        int h = Math.max(1, view.getHeight());
        float density = Math.max(1f, view.getResources().getDisplayMetrics().density);
        float zoneW = Math.max(156f * density, w * 0.34f);
        float zoneH = Math.max(156f * density, h * 0.30f);
        zoneW = Math.min(zoneW, w * 0.48f);
        zoneH = Math.min(zoneH, h * 0.48f);
        boolean left = x <= zoneW;
        boolean right = x >= w - zoneW;
        boolean top = y <= zoneH;
        boolean bottom = y >= h - zoneH;
        if (left && top) return Corner.TOP_LEFT;
        if (right && top) return Corner.TOP_RIGHT;
        if (left && bottom) return Corner.BOTTOM_LEFT;
        if (right && bottom) return Corner.BOTTOM_RIGHT;
        return Corner.NONE;
    }

    private void resetPattern() {
        stepIndex = 0;
        stepTapCount = 0;
        lastStepTapTime = 0L;
    }

    private void resetRecovery() {
        recoveryTapCount = 0;
        recoveryStartTime = 0L;
        recoveryLastTapTime = 0L;
        recoveryStartX = 0f;
        recoveryStartY = 0f;
        recoveryLongPressArmed = false;
        recoveryLongPressStartTime = 0L;
    }

    private void parse(String pattern) {
        steps.clear();
        String raw = TextUtils.isEmpty(pattern) ? KioskPrefs.DEFAULT_EXIT_PATTERN : pattern;
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (part == null) continue;
            String[] kv = part.trim().split(":");
            if (kv.length != 2) continue;
            int corner = Corner.fromToken(kv[0]);
            int count;
            try {
                count = Integer.parseInt(kv[1].trim());
            } catch (Throwable t) {
                count = 0;
            }
            if (corner != Corner.NONE && count > 0 && count <= 20) steps.add(new Step(corner, count));
        }
        if (steps.isEmpty()) {
            steps.add(new Step(Corner.TOP_LEFT, 3));
            steps.add(new Step(Corner.TOP_RIGHT, 1));
            steps.add(new Step(Corner.BOTTOM_LEFT, 4));
            steps.add(new Step(Corner.BOTTOM_RIGHT, 2));
        }
    }

    public static String normalizePattern(String pattern) {
        KioskExitPattern tmp = new KioskExitPattern(pattern, null);
        ArrayList<String> parts = new ArrayList<>();
        for (Step step : tmp.steps) {
            parts.add(Corner.toToken(step.corner) + ":" + step.count);
        }
        return TextUtils.join(",", parts);
    }

    public static String describe(String pattern) {
        KioskExitPattern tmp = new KioskExitPattern(pattern, null);
        List<String> out = new ArrayList<>();
        for (Step step : tmp.steps) {
            out.add(Corner.toLabel(step.corner) + " x" + step.count);
        }
        return TextUtils.join(" → ", out);
    }

    public static String recoveryDescription() {
        return "Recovery: 21 blank-space taps, then hold the 22nd press for 7-13 seconds and release.";
    }

    private static final class Step {
        final int corner;
        final int count;

        Step(int corner, int count) {
            this.corner = corner;
            this.count = count;
        }
    }

    private static final class Corner {
        static final int NONE = 0;
        static final int TOP_LEFT = 1;
        static final int TOP_RIGHT = 2;
        static final int BOTTOM_LEFT = 3;
        static final int BOTTOM_RIGHT = 4;

        static int fromToken(String token) {
            String t = token == null ? "" : token.trim().toUpperCase(Locale.US);
            if ("TL".equals(t) || "TOP_LEFT".equals(t)) return TOP_LEFT;
            if ("TR".equals(t) || "TOP_RIGHT".equals(t)) return TOP_RIGHT;
            if ("BL".equals(t) || "BOTTOM_LEFT".equals(t)) return BOTTOM_LEFT;
            if ("BR".equals(t) || "BOTTOM_RIGHT".equals(t)) return BOTTOM_RIGHT;
            return NONE;
        }

        static String toToken(int corner) {
            switch (corner) {
                case TOP_LEFT: return "TL";
                case TOP_RIGHT: return "TR";
                case BOTTOM_LEFT: return "BL";
                case BOTTOM_RIGHT: return "BR";
                default: return "";
            }
        }

        static String toLabel(int corner) {
            switch (corner) {
                case TOP_LEFT: return "top-left";
                case TOP_RIGHT: return "top-right";
                case BOTTOM_LEFT: return "bottom-left";
                case BOTTOM_RIGHT: return "bottom-right";
                default: return "unknown";
            }
        }
    }
}
