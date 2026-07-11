package dev.perms.test.ui.progress;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * Small theme-aware rounded progress strip for inline tool status rows.
 *
 * The view supports a determinate fraction when the caller knows progress and a
 * lightweight indeterminate sweep when only a running phase is known. It has no
 * external lifecycle dependency; it starts/stops its own invalidation tick when
 * visible and running.
 */
public final class PermsProgressStrip extends View {
    private static final long FRAME_MS = 32L;
    private static final long CYCLE_MS = 1250L;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF segment = new RectF();
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running || !isShown()) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    private boolean running;
    private boolean indeterminate = true;
    private float progressFraction = 0f;
    private long cycleStartMs;

    public PermsProgressStrip(Context context) {
        this(context, null);
    }

    public PermsProgressStrip(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PermsProgressStrip(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints(context);
        setMinimumHeight(dp(6));
    }

    public void setRunning(boolean value) {
        if (running == value) return;
        running = value;
        if (running) {
            cycleStartMs = System.currentTimeMillis();
            removeCallbacks(ticker);
            post(ticker);
        } else {
            removeCallbacks(ticker);
        }
        invalidate();
    }

    public void setIndeterminate(boolean value) {
        indeterminate = value;
        invalidate();
    }

    public void setProgressFraction(float fraction) {
        if (Float.isNaN(fraction) || Float.isInfinite(fraction)) fraction = 0f;
        progressFraction = Math.max(0f, Math.min(1f, fraction));
        indeterminate = false;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (running) post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        removeCallbacks(ticker);
        if (running && isShown()) post(ticker);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = dp(6);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) width = dp(96);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        rect.set(0f, 0f, w, h);
        float radius = h / 2f;
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        if (indeterminate && running) {
            float width = w * 0.32f;
            float travel = w + width;
            float phase = ((System.currentTimeMillis() - cycleStartMs) % CYCLE_MS) / (float) CYCLE_MS;
            float left = phase * travel - width;
            segment.set(Math.max(0f, left), 0f, Math.min(w, left + width), h);
            if (segment.right > segment.left) canvas.drawRoundRect(segment, radius, radius, fillPaint);
        } else {
            float right = w * progressFraction;
            if (right > 0f) {
                segment.set(0f, 0f, right, h);
                canvas.drawRoundRect(segment, radius, radius, fillPaint);
            }
        }
    }

    private void initPaints(Context context) {
        int fill = resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary, 0xffbb86fc);
        int track = withAlpha(fill, 0.24f);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fill);
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(track);
    }

    private int resolveThemeColor(Context context, int attr, int fallback) {
        if (context == null) return fallback;
        TypedValue out = new TypedValue();
        if (context.getTheme() != null && context.getTheme().resolveAttribute(attr, out, true)) {
            if (out.resourceId != 0) {
                try {
                    TypedArray array = context.obtainStyledAttributes(new int[]{attr});
                    int value = array.getColor(0, fallback);
                    array.recycle();
                    return value;
                } catch (Throwable ignored) {
                }
            }
            if (out.data != 0) return out.data;
        }
        return fallback;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255f * alpha)));
        return (color & 0x00ffffff) | (a << 24);
    }

    private int dp(int value) {
        return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density));
    }
}
