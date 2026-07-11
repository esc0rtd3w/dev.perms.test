package dev.perms.test.tutorial;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Draws the smoky tutorial overlay, glowing target border, and current tip card. */
public final class TutorialOverlayView extends FrameLayout {
    public interface Listener {
        void onHighlightedTargetTapped();
        void onNextRequested();
        void onSkipRequested();
        void onDisableRequested();
    }

    private final HighlightLayer highlightLayer;
    private final LinearLayout tipCard;
    private final TextView titleView;
    private final TextView messageView;
    private final TextView progressView;
    private final CheckBox disableCheckBox;
    private final Button nextButton;
    private final Button skipButton;
    private final RectF highlightRect = new RectF();
    private Listener listener;

    public TutorialOverlayView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(false);
        setWillNotDraw(false);

        highlightLayer = new HighlightLayer(context);
        addView(highlightLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        tipCard = new LinearLayout(context);
        tipCard.setOrientation(LinearLayout.VERTICAL);
        tipCard.setPadding(dp(16), dp(14), dp(16), dp(12));
        tipCard.setClickable(true);
        tipCard.setBackground(makeCardBackground());
        if (Build.VERSION.SDK_INT >= 21) tipCard.setElevation(dp(10));

        titleView = new TextView(context);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(17);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        tipCard.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        messageView = new TextView(context);
        messageView.setTextColor(0xFFE8E8E8);
        messageView.setTextSize(14);
        messageView.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(6);
        tipCard.addView(messageView, msgLp);

        progressView = new TextView(context);
        progressView.setTextColor(0xFFBFD7FF);
        progressView.setTextSize(12);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressLp.topMargin = dp(8);
        tipCard.addView(progressView, progressLp);

        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomLp.topMargin = dp(10);

        disableCheckBox = new CheckBox(context);
        disableCheckBox.setText("Disable Tutorial");
        disableCheckBox.setTextColor(0xFFE8E8E8);
        disableCheckBox.setTextSize(13);
        disableCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && listener != null) listener.onDisableRequested();
        });
        bottomRow.addView(disableCheckBox, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        skipButton = new Button(context);
        skipButton.setAllCaps(false);
        skipButton.setText("Skip");
        skipButton.setOnClickListener(v -> {
            if (listener != null) listener.onSkipRequested();
        });
        bottomRow.addView(skipButton, compactButtonLp());

        nextButton = new Button(context);
        nextButton.setAllCaps(false);
        nextButton.setText("Next");
        nextButton.setOnClickListener(v -> {
            if (listener != null) listener.onNextRequested();
        });
        LinearLayout.LayoutParams nextLp = compactButtonLp();
        nextLp.leftMargin = dp(8);
        bottomRow.addView(nextButton, nextLp);

        tipCard.addView(bottomRow, bottomLp);
        addView(tipCard);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean showStep(View target, TutorialTabSpec spec, TutorialStep step, int stepIndex, int stepCount) {
        if (target == null || step == null) return false;
        if (!computeHighlightRect(target)) return false;
        highlightLayer.setHighlightRect(highlightRect);
        titleView.setText(step.title);
        messageView.setText(step.message + "\n\nTap the highlighted area to continue.");
        String tabName = spec == null ? "Tutorial" : spec.title;
        progressView.setText(tabName + " tutorial  •  " + (stepIndex + 1) + " of " + Math.max(1, stepCount));
        disableCheckBox.setOnCheckedChangeListener(null);
        disableCheckBox.setChecked(false);
        disableCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && listener != null) listener.onDisableRequested();
        });
        nextButton.setText(stepIndex >= stepCount - 1 ? "Done" : "Next");
        positionTipCard();
        invalidate();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (highlightRect.contains(event.getX(), event.getY())) {
                if (listener != null) listener.onHighlightedTargetTapped();
            }
        }
        return true;
    }

    private boolean computeHighlightRect(View target) {
        Rect visibleRect = new Rect();
        if (!target.getGlobalVisibleRect(visibleRect)) return false;

        int[] rootLoc = new int[2];
        getLocationOnScreen(rootLoc);
        float left = visibleRect.left - rootLoc[0];
        float top = visibleRect.top - rootLoc[1];
        float right = visibleRect.right - rootLoc[0];
        float bottom = visibleRect.bottom - rootLoc[1];

        int safeTop = systemInsetTop();
        int safeBottom = Math.max(safeTop + dp(48), getHeight() - systemInsetBottom());
        RectF rect = new RectF(left, top, right, bottom);
        if (!rect.intersect(0, safeTop, Math.max(1, getWidth()), Math.max(safeTop + 1, safeBottom))) {
            return false;
        }

        float minSize = dp(48);
        if (rect.width() < minSize) {
            float cx = rect.centerX();
            rect.left = cx - minSize / 2f;
            rect.right = cx + minSize / 2f;
        }
        if (rect.height() < minSize) {
            float cy = rect.centerY();
            rect.top = cy - minSize / 2f;
            rect.bottom = cy + minSize / 2f;
        }

        highlightRect.set(rect.left - dp(8), rect.top - dp(8), rect.right + dp(8), rect.bottom + dp(8));
        boolean valid = highlightRect.intersect(0, safeTop, Math.max(1, getWidth()), Math.max(safeTop + 1, safeBottom));
        return valid && highlightRect.width() >= dp(24) && highlightRect.height() >= dp(24);
    }

    private void positionTipCard() {
        int sideMargin = dp(16);
        int verticalGap = dp(14);
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        int maxWidth = Math.max(1, width - sideMargin * 2);
        int safeTop = sideMargin + systemInsetTop();
        int safeBottom = Math.max(safeTop + dp(120), height - systemInsetBottom() - sideMargin);

        int widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(Math.max(1, safeBottom - safeTop), MeasureSpec.AT_MOST);
        tipCard.measure(widthSpec, heightSpec);
        int cardHeight = Math.max(dp(160), tipCard.getMeasuredHeight());
        if (cardHeight > safeBottom - safeTop) cardHeight = Math.max(dp(120), safeBottom - safeTop);

        int belowTop = (int) (highlightRect.bottom + verticalGap);
        int aboveTop = (int) (highlightRect.top - cardHeight - verticalGap);
        int belowSpace = safeBottom - belowTop;
        int aboveSpace = aboveTop + cardHeight - safeTop;
        boolean placeBelow = belowSpace >= cardHeight || belowSpace >= aboveSpace;
        int top = placeBelow ? belowTop : aboveTop;
        int maxTop = Math.max(safeTop, safeBottom - cardHeight);
        if (top < safeTop) top = safeTop;
        if (top > maxTop) top = maxTop;

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = sideMargin;
        lp.rightMargin = sideMargin;
        lp.topMargin = top;
        tipCard.setLayoutParams(lp);
    }

    private int systemInsetTop() {
        if (Build.VERSION.SDK_INT < 20) return 0;
        try {
            WindowInsets insets = getRootWindowInsets();
            return insets == null ? 0 : Math.max(0, insets.getSystemWindowInsetTop());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private int systemInsetBottom() {
        if (Build.VERSION.SDK_INT < 20) return 0;
        try {
            WindowInsets insets = getRootWindowInsets();
            return insets == null ? 0 : Math.max(0, insets.getSystemWindowInsetBottom());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private LinearLayout.LayoutParams compactButtonLp() {
        return new LinearLayout.LayoutParams(dp(82), dp(42));
    }

    private GradientDrawable makeCardBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xEE151A22);
        gd.setCornerRadius(dp(18));
        gd.setStroke(dp(1), 0x99BFD7FF);
        return gd;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class HighlightLayer extends View {
        private final RectF rect = new RectF();
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HighlightLayer(Context context) {
            super(context);
            dimPaint.setColor(0xA6000000);
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(dp(8));
            glowPaint.setColor(0x6688D8FF);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(2));
            strokePaint.setColor(0xFFE1F6FF);
            setWillNotDraw(false);
        }

        void setHighlightRect(RectF source) {
            rect.set(source);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0 || rect.isEmpty()) {
                canvas.drawRect(0, 0, width, height, dimPaint);
                return;
            }

            canvas.drawRect(0, 0, width, rect.top, dimPaint);
            canvas.drawRect(0, rect.bottom, width, height, dimPaint);
            canvas.drawRect(0, rect.top, rect.left, rect.bottom, dimPaint);
            canvas.drawRect(rect.right, rect.top, width, rect.bottom, dimPaint);

            float radius = dp(16);
            canvas.drawRoundRect(rect, radius, radius, glowPaint);
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
        }
    }
}
