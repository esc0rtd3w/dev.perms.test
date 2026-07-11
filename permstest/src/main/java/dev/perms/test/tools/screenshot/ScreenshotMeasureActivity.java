package dev.perms.test.tools.screenshot;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-screen screenshot crop/measurement surface for Tools > Screenshot Tool. */
public final class ScreenshotMeasureActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "dev.perms.test.extra.SCREENSHOT_PATH";

    private ScreenshotMeasureView measureView;
    private TextView statusView;
    private File sourceFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_PATH);
        sourceFile = TextUtils.isEmpty(path) ? null : new File(path);
        Bitmap bitmap = sourceFile == null ? null : BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
        if (bitmap == null) {
            Toast.makeText(this, "Screenshot could not be opened", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = dp(10);
        root.setPadding(pad, pad, pad, pad);

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(13f);
        statusView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        statusView.setSingleLine(false);
        statusView.setText("Drag across the screenshot to highlight and measure an area.");
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        measureView = new ScreenshotMeasureView(this, bitmap);
        measureView.setStatusSink(text -> statusView.setText(text));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        previewLp.setMargins(0, dp(8), 0, dp(8));
        root.addView(measureView, previewLp);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton saveCrop = button("Save Crop");
        saveCrop.setOnClickListener(v -> saveCrop());
        row.addView(saveCrop, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton copyMeasure = button("Copy Measure");
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.setMargins(dp(8), 0, 0, 0);
        copyMeasure.setOnClickListener(v -> copyMeasure());
        row.addView(copyMeasure, copyLp);

        MaterialButton close = button("Close");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        closeLp.setMargins(dp(8), 0, 0, 0);
        close.setOnClickListener(v -> finish());
        row.addView(close, closeLp);

        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private MaterialButton button(String text) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setPadding(dp(8), button.getPaddingTop(), dp(8), button.getPaddingBottom());
        return button;
    }

    private void copyMeasure() {
        String text = measureView == null ? "" : measureView.selectionText();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "Select an area first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PermsTest screenshot measure", text));
            Toast.makeText(this, "Measure copied", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Copy failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
        }
    }

    private void saveCrop() {
        if (measureView == null || sourceFile == null) return;
        Rect r = measureView.selectedImageRect();
        Bitmap bitmap = measureView.bitmap();
        if (bitmap == null || r == null || r.width() <= 0 || r.height() <= 0) {
            Toast.makeText(this, "Select an area first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Bitmap crop = Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height());
            File outDir = sourceFile.getParentFile();
            if (outDir == null) outDir = getExternalFilesDir("screenshots");
            if (outDir != null && !outDir.exists()) outDir.mkdirs();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File out = new File(outDir, "perms_test_crop_" + stamp + "_"
                    + r.left + "_" + r.top + "_" + r.width() + "x" + r.height() + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                crop.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            crop.recycle();
            Toast.makeText(this, "Crop saved: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
            statusView.setText("Saved crop: " + out.getAbsolutePath() + "\n" + measureView.selectionText());
        } catch (Throwable t) {
            Toast.makeText(this, "Crop failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String safeMessage(Throwable t) {
        String msg = t == null ? "" : t.getMessage();
        return TextUtils.isEmpty(msg) && t != null ? t.getClass().getSimpleName() : msg;
    }

    private interface StatusSink {
        void onStatus(String text);
    }

    private static final class ScreenshotMeasureView extends View {
        private final Bitmap bitmap;
        private final RectF imageRect = new RectF();
        private final RectF selection = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private StatusSink statusSink;
        private float downX;
        private float downY;
        private float scale = 1f;
        private boolean hasSelection;

        ScreenshotMeasureView(Context context, Bitmap bitmap) {
            super(context);
            this.bitmap = bitmap;
            setFocusable(true);
        }

        Bitmap bitmap() {
            return bitmap;
        }

        void setStatusSink(StatusSink statusSink) {
            this.statusSink = statusSink;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null || bitmap.isRecycled()) return;
            computeImageRect();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(8, 8, 12));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(bitmap, null, imageRect, paint);
            if (hasSelection) {
                paint.setFilterBitmap(false);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x44B388FF);
                canvas.drawRect(selection, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, getResources().getDisplayMetrics().density * 2f));
                paint.setColor(0xFFE7D8FF);
                canvas.drawRect(selection, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (bitmap == null || bitmap.isRecycled()) return false;
            computeImageRect();
            float x = clamp(event.getX(), imageRect.left, imageRect.right);
            float y = clamp(event.getY(), imageRect.top, imageRect.bottom);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!imageRect.contains(event.getX(), event.getY())) return true;
                    downX = x;
                    downY = y;
                    updateSelection(downX, downY, x, y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    updateSelection(downX, downY, x, y);
                    return true;
                default:
                    return true;
            }
        }

        private void updateSelection(float x1, float y1, float x2, float y2) {
            selection.set(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
            hasSelection = selection.width() > 1f && selection.height() > 1f;
            if (statusSink != null) {
                String text = selectionText();
                statusSink.onStatus(TextUtils.isEmpty(text)
                        ? "Drag across the screenshot to highlight and measure an area."
                        : text);
            }
            invalidate();
        }

        Rect selectedImageRect() {
            if (!hasSelection || bitmap == null || scale <= 0f) return null;
            int left = clampInt(Math.round((selection.left - imageRect.left) / scale), 0, bitmap.getWidth() - 1);
            int top = clampInt(Math.round((selection.top - imageRect.top) / scale), 0, bitmap.getHeight() - 1);
            int right = clampInt(Math.round((selection.right - imageRect.left) / scale), left + 1, bitmap.getWidth());
            int bottom = clampInt(Math.round((selection.bottom - imageRect.top) / scale), top + 1, bitmap.getHeight());
            return new Rect(left, top, right, bottom);
        }

        String selectionText() {
            Rect r = selectedImageRect();
            if (r == null) return "";
            return "Selected area: x=" + r.left
                    + ", y=" + r.top
                    + ", width=" + r.width()
                    + ", height=" + r.height()
                    + " px";
        }

        private void computeImageRect() {
            if (bitmap == null || bitmap.isRecycled()) return;
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            float sx = w / (float) bitmap.getWidth();
            float sy = h / (float) bitmap.getHeight();
            scale = Math.min(sx, sy);
            float bw = bitmap.getWidth() * scale;
            float bh = bitmap.getHeight() * scale;
            float left = (w - bw) / 2f;
            float top = (h - bh) / 2f;
            imageRect.set(left, top, left + bw, top + bh);
            if (hasSelection) {
                selection.intersect(imageRect);
            }
        }

        private static float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }

        private static int clampInt(int v, int min, int max) {
            return Math.max(min, Math.min(max, v));
        }
    }
}
