package dev.perms.test.ladb;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Small floating pairing helper for Wireless Debugging / LADB.
 *
 * Requires "Display over other apps" (SYSTEM_ALERT_WINDOW) permission.
 * This is intentionally tiny and self-contained to keep diffs minimal.
 */
public final class OverlayPairHelper {

    public interface PairListener {
        void onPairRequested(int pairPort, String pairCode);
        void onDismissed();
    }

    private static View sView;

    private OverlayPairHelper() {}

    public static boolean isShowing() {
        return sView != null;
    }

    public static void show(Context ctx, int initialPort, String initialCode, PairListener listener) {
        if (ctx == null) return;
        if (isShowing()) return;

        final Context app = ctx.getApplicationContext();
        final WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        LinearLayout root = new LinearLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(app, 12);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xEE111111);

        TextView title = new TextView(app);
        title.setText("LADB Pair Helper");
        title.setTextSize(16f);
        title.setTextColor(0xFFFFFFFF);
        root.addView(title);

        TextView hint = new TextView(app);
        hint.setText("Open Wireless debugging > Pair device with pairing code,\nthen enter Pair port + code here.");
        hint.setTextSize(12f);
        hint.setTextColor(0xFFCCCCCC);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        hintLp.topMargin = dp(app, 6);
        root.addView(hint, hintLp);

        EditText port = new EditText(app);
        port.setHint("Pair port");
        port.setSingleLine(true);
        port.setTextColor(0xFFFFFFFF);
        port.setHintTextColor(0xFF888888);
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (initialPort > 0) port.setText(String.valueOf(initialPort));
        LinearLayout.LayoutParams portLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        portLp.topMargin = dp(app, 10);
        root.addView(port, portLp);

        EditText code = new EditText(app);
        code.setHint("Pair code");
        code.setSingleLine(true);
        code.setTextColor(0xFFFFFFFF);
        code.setHintTextColor(0xFF888888);
        if (!TextUtils.isEmpty(initialCode)) code.setText(initialCode);
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        codeLp.topMargin = dp(app, 8);
        root.addView(code, codeLp);

        LinearLayout buttons = new LinearLayout(app);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonsLp.topMargin = dp(app, 10);
        root.addView(buttons, buttonsLp);

        Button btnPair = new Button(app);
        btnPair.setText("PAIR");
        LinearLayout.LayoutParams pairLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        buttons.addView(btnPair, pairLp);

        Button btnClose = new Button(app);
        btnClose.setText("CLOSE");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        closeLp.leftMargin = dp(app, 8);
        buttons.addView(btnClose, closeLp);

        btnPair.setOnClickListener(v -> {
            int p = safeParseInt(port.getText() == null ? "" : port.getText().toString(), 0);
            String c = code.getText() == null ? "" : code.getText().toString().trim();
            if (p <= 0 || TextUtils.isEmpty(c)) return;
            if (listener != null) listener.onPairRequested(p, c);
        });

        btnClose.setOnClickListener(v -> {
            dismiss(app);
            if (listener != null) listener.onDismissed();
        });

        // Drag-to-move (simple; long-press not required)
        title.setOnTouchListener(new DragMoveTouchListener(wm));

        int type = (Build.VERSION.SDK_INT >= 26)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(app, 320),
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(app, 10);
        lp.y = dp(app, 80);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;

        try {
            wm.addView(root, lp);
            sView = root;
        } catch (Throwable ignored) {
        }
    }

    public static void dismiss(Context ctx) {
        if (ctx == null) return;
        if (!isShowing()) return;

        final Context app = ctx.getApplicationContext();
        final WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        try {
            wm.removeView(sView);
        } catch (Throwable ignored) {
        } finally {
            sView = null;
        }
    }

    private static int dp(Context ctx, int dp) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }

    private static int safeParseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; }
    }

    private static final class DragMoveTouchListener implements View.OnTouchListener {
        private final WindowManager wm;
        private float downRawX, downRawY;
        private int startX, startY;
        private long downTime;

        DragMoveTouchListener(WindowManager wm) {
            this.wm = wm;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            try {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
                if (lp == null) return false;

                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        downTime = SystemClock.uptimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(e.getRawX() - downRawX);
                        int dy = Math.round(e.getRawY() - downRawY);
                        lp.x = startX - dx; // END gravity uses inverted X
                        lp.y = startY + dy;
                        wm.updateViewLayout(v, lp);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                    default:
                        return true;
                }
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
