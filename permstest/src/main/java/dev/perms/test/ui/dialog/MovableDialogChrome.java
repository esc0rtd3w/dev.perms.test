package dev.perms.test.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small shared helper for movable in-app dialog/plugin surfaces. */
public final class MovableDialogChrome {
    public static final String STYLE_COMPACT = "compact";
    public static final String STYLE_FULL = "full";
    public static final String FIT_CURRENT = "current";
    public static final String FIT_CONTENT = "fit";

    public static final class Chrome {
        public final View root;
        public final View dragHandle;
        public final View closeButton;
        public final String style;

        private Chrome(View root, View dragHandle, View closeButton, String style) {
            this.root = root;
            this.dragHandle = dragHandle;
            this.closeButton = closeButton;
            this.style = normalizeStyle(style);
        }
    }

    private MovableDialogChrome() {
    }

    public static Chrome create(Context context, View content, String style) {
        String normalized = normalizeStyle(style);
        return STYLE_FULL.equals(normalized)
                ? createFull(context, content)
                : createCompact(context, content);
    }

    public static LinearLayout wrap(Context context, View content, String ignoredDragLabel) {
        Chrome chrome = createCompact(context, content);
        return chrome.root instanceof LinearLayout ? (LinearLayout) chrome.root : null;
    }

    public static View getHandle(View wrapped) {
        Object tag = wrapped == null ? null : wrapped.getTag();
        return tag instanceof View ? (View) tag : null;
    }

    public static String normalizeStyle(String style) {
        if (TextUtils.isEmpty(style)) return STYLE_COMPACT;
        String s = style.trim().toLowerCase(java.util.Locale.US);
        if ("fullscreen".equals(s) || "large".equals(s) || "expanded".equals(s)) return STYLE_FULL;
        if (STYLE_FULL.equals(s)) return STYLE_FULL;
        return STYLE_COMPACT;
    }

    public static String normalizeFit(String fit) {
        if (TextUtils.isEmpty(fit)) return FIT_CURRENT;
        String s = fit.trim().toLowerCase(java.util.Locale.US);
        if ("content".equals(s) || "fit_content".equals(s) || "fit".equals(s)) return FIT_CONTENT;
        return FIT_CURRENT;
    }

    public static boolean shouldFitContent(String fit) {
        return FIT_CONTENT.equals(normalizeFit(fit));
    }

    public static boolean isFullStyle(String style) {
        return STYLE_FULL.equals(normalizeStyle(style));
    }

    public static void applyWindowStyle(Dialog dialog, String style, String fit) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        try {
            DisplayMetrics dm = dialog.getContext().getResources().getDisplayMetrics();
            if (isFullStyle(style)) {
                int width = Math.max(dp(dialog.getContext(), 360), Math.round(dm.widthPixels * 0.92f));
                int height = shouldFitContent(fit)
                        ? WindowManager.LayoutParams.WRAP_CONTENT
                        : Math.max(dp(dialog.getContext(), 420), Math.round(dm.heightPixels * 0.86f));
                window.setLayout(width, height);
            } else if (shouldFitContent(fit)) {
                window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void enable(Dialog dialog, View dragHandle) {
        if (dialog == null || dragHandle == null) return;
        try {
            Window window = dialog.getWindow();
            if (window == null) return;
            dragHandle.setOnTouchListener(new View.OnTouchListener() {
                private boolean positioned;
                private float downRawX;
                private float downRawY;
                private int startX;
                private int startY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    try {
                        Window w = dialog.getWindow();
                        if (w == null || event == null) return false;
                        WindowManager.LayoutParams lp = w.getAttributes();
                        if (!positioned) {
                            int[] loc = new int[2];
                            try {
                                View decor = w.getDecorView();
                                if (decor != null) decor.getLocationOnScreen(loc);
                            } catch (Throwable ignored) {
                            }
                            lp.gravity = Gravity.TOP | Gravity.START;
                            lp.x = Math.max(0, loc[0]);
                            lp.y = Math.max(0, loc[1]);
                            w.setAttributes(lp);
                            positioned = true;
                        }

                        int action = event.getActionMasked();
                        if (action == MotionEvent.ACTION_DOWN) {
                            downRawX = event.getRawX();
                            downRawY = event.getRawY();
                            startX = lp.x;
                            startY = lp.y;
                            return true;
                        }
                        if (action == MotionEvent.ACTION_MOVE) {
                            lp.x = Math.max(0, startX + Math.round(event.getRawX() - downRawX));
                            lp.y = Math.max(0, startY + Math.round(event.getRawY() - downRawY));
                            w.setAttributes(lp);
                            return true;
                        }
                        return action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
                    } catch (Throwable ignored) {
                        return false;
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static Chrome createCompact(Context context, View content) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        View handle = new View(context);
        handle.setContentDescription("Drag to move window");
        root.addView(handle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 18)));

        if (content != null) {
            root.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        root.setTag(handle);
        return new Chrome(root, handle, null, STYLE_COMPACT);
    }

    private static Chrome createFull(Context context, View content) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 8);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View handle = new View(context);
        handle.setContentDescription("Drag to move window");
        header.addView(handle, new LinearLayout.LayoutParams(0, dp(context, 42), 1f));

        TextView close = new TextView(context);
        close.setText("✕");
        close.setTextSize(22f);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close plugin window");
        header.addView(close, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 42)));

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (content != null) {
            root.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f));
        }
        root.setTag(handle);
        return new Chrome(root, handle, close, STYLE_FULL);
    }

    private static int dp(Context context, int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
