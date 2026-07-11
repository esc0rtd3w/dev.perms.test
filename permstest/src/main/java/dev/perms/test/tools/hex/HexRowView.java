package dev.perms.test.tools.hex;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Lightweight row view for the Tools-tab Hex Editor.
 *
 * A hex row is drawn as one custom view instead of thirty-plus TextViews. That
 * keeps scrolling smooth on larger windows and avoids per-bind listener churn
 * while preserving precise byte hit testing for hex and ASCII cells.
 */
final class HexRowView extends View {
    interface Listener {
        void onByteSelected(long absoluteOffset, boolean openEditor);
        void onByteRangeSelected(long startOffset, long endOffsetInclusive);
    }

    private static final int UNSELECTED_TEXT = 0xffeeeeee;
    private static final int SELECTED_TEXT = 0xff1b1038;
    private static final int SELECTED_BG = 0xffd8c3ff;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final int rowHeight;
    private final int leftPadding;
    private final int offsetWidth;
    private final int hexWidth;
    private final int gapWidth;
    private final int asciiWidth;
    private final int hexCellWidth;
    private final int asciiCellWidth;
    private final int touchSlop;
    private final int longPressTimeout;

    private Listener listener;
    private long baseOffset;
    private byte[] data = new byte[0];
    private int rowStart;
    private long selectedOffset = -1L;
    private int selectedLength;
    private boolean dragSelectEnabled;
    private long downOffset = -1L;
    private long dragAnchorOffset = -1L;
    private float downX;
    private float downY;
    private boolean longPressFired;
    private Runnable longPressRunnable;

    HexRowView(Context context) {
        super(context);
        rowHeight = dp(32);
        leftPadding = dp(4);
        offsetWidth = dp(106);
        hexWidth = dp(448);
        gapWidth = dp(48);
        asciiWidth = dp(320);
        hexCellWidth = dp(28);
        asciiCellWidth = dp(20);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        longPressTimeout = ViewConfiguration.getLongPressTimeout();
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextSize(sp(12f));
        selectedPaint.setColor(SELECTED_BG);
        setFocusable(true);
        setClickable(true);
    }

    void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    void bind(long baseOffset, byte[] data, int rowStart,
              long selectedOffset, int selectedLength, boolean dragSelectEnabled) {
        this.baseOffset = Math.max(0L, baseOffset);
        this.data = data == null ? new byte[0] : data;
        this.rowStart = Math.max(0, rowStart);
        this.selectedOffset = selectedOffset;
        this.selectedLength = selectedLength;
        this.dragSelectEnabled = dragSelectEnabled;
        invalidate();
    }

    void setSelection(long selectedOffset, int selectedLength) {
        this.selectedOffset = selectedOffset;
        this.selectedLength = selectedLength;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = leftPadding + offsetWidth + hexWidth + gapWidth + asciiWidth + leftPadding;
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), rowHeight + dp(4));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float baseline = (getHeight() - textPaint.descent() - textPaint.ascent()) * 0.5f;
        drawText(canvas, HexPaneRenderer.formatOffset(baseOffset + rowStart), leftPadding, baseline, UNSELECTED_TEXT);

        float hexStart = leftPadding + offsetWidth;
        float asciiStart = hexStart + hexWidth + gapWidth;
        for (int i = 0; i < HexPaneRenderer.BYTES_PER_ROW; i++) {
            int index = rowStart + i;
            if (index >= data.length) break;
            int value = data[index] & 0xff;
            long absolute = baseOffset + index;
            boolean selected = isSelected(absolute);

            float hx = hexStart + (i * hexCellWidth);
            float ax = asciiStart + (i * asciiCellWidth);
            drawCell(canvas, String.format(Locale.US, "%02X", value), hx, hexCellWidth, baseline, selected);
            drawCell(canvas, String.valueOf(printable(value)), ax, asciiCellWidth, baseline, selected);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null || data.length == 0) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            longPressFired = false;
            downOffset = absoluteOffsetForX(downX);
            if (dragSelectEnabled) {
                dragAnchorOffset = downOffset;
                if (downOffset >= 0L && listener != null) listener.onByteRangeSelected(downOffset, downOffset);
                return true;
            }
            scheduleLongPress();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (dragSelectEnabled) {
                long current = absoluteOffsetForX(event.getX());
                if (dragAnchorOffset >= 0L && current >= 0L && listener != null) {
                    listener.onByteRangeSelected(dragAnchorOffset, current);
                }
                return true;
            }
            if (movedPastSlop(event)) cancelLongPressTimer();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            cancelLongPressTimer();
            if (dragSelectEnabled) {
                long current = absoluteOffsetForX(event.getX());
                if (dragAnchorOffset >= 0L && current >= 0L && listener != null) {
                    listener.onByteRangeSelected(dragAnchorOffset, current);
                }
                dragAnchorOffset = -1L;
                downOffset = -1L;
                return true;
            }
            long current = absoluteOffsetForX(event.getX());
            if (!longPressFired && current >= 0L && listener != null) {
                listener.onByteSelected(current, false);
                performClick();
            }
            downOffset = -1L;
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            cancelLongPressTimer();
            dragAnchorOffset = -1L;
            downOffset = -1L;
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void drawCell(Canvas canvas, String text, float left, int width, float baseline, boolean selected) {
        if (selected) {
            rect.set(left + dp(1), dp(3), left + width - dp(1), getHeight() - dp(3));
            canvas.drawRoundRect(rect, dp(3), dp(3), selectedPaint);
        }
        textPaint.setColor(selected ? SELECTED_TEXT : UNSELECTED_TEXT);
        textPaint.setAlpha(selected ? 255 : 235);
        float x = left + ((width - textPaint.measureText(text)) * 0.5f);
        canvas.drawText(text, x, baseline, textPaint);
        textPaint.setAlpha(255);
    }

    private void drawText(Canvas canvas, String text, float x, float baseline, int color) {
        textPaint.setColor(color);
        textPaint.setAlpha(235);
        canvas.drawText(text, x, baseline, textPaint);
        textPaint.setAlpha(255);
    }

    private long absoluteOffsetForX(float x) {
        int cell = cellIndexForX(x);
        if (cell < 0) return -1L;
        int index = rowStart + cell;
        if (index < 0 || index >= data.length) return -1L;
        return baseOffset + index;
    }

    private int cellIndexForX(float x) {
        float hexStart = leftPadding + offsetWidth;
        float hexEnd = hexStart + hexWidth;
        if (x >= hexStart && x < hexEnd) {
            return Math.max(0, Math.min(15, (int) ((x - hexStart) / Math.max(1, hexCellWidth))));
        }
        float asciiStart = hexEnd + gapWidth;
        float asciiEnd = asciiStart + asciiWidth;
        if (x >= asciiStart && x < asciiEnd) {
            return Math.max(0, Math.min(15, (int) ((x - asciiStart) / Math.max(1, asciiCellWidth))));
        }
        return -1;
    }

    private boolean movedPastSlop(MotionEvent event) {
        return Math.abs(event.getX() - downX) > touchSlop || Math.abs(event.getY() - downY) > touchSlop;
    }

    private void scheduleLongPress() {
        cancelLongPressTimer();
        if (downOffset < 0L) return;
        longPressRunnable = () -> {
            if (downOffset < 0L || listener == null) return;
            longPressFired = true;
            listener.onByteSelected(downOffset, true);
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        };
        postDelayed(longPressRunnable, Math.max(250, longPressTimeout));
    }

    private void cancelLongPressTimer() {
        if (longPressRunnable != null) removeCallbacks(longPressRunnable);
        longPressRunnable = null;
    }

    private boolean isSelected(long absolute) {
        if (selectedOffset < 0L || selectedLength <= 0) return false;
        return absolute >= selectedOffset && absolute < selectedOffset + selectedLength;
    }

    private static char printable(int b) {
        return b >= 32 && b <= 126 ? (char) b : '.';
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
