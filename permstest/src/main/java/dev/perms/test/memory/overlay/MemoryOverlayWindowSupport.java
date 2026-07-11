package dev.perms.test.memory.overlay;

import dev.perms.test.ui.PermsTestUiCompat;
import dev.perms.test.memory.MemoryToolHelper;
import dev.perms.test.vr.PermsTestVrOverlayCompat;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

/**
 * Shared helpers for floating memory tool windows.
 *
 * Controllers use this class for overlay sizing, drag/resize persistence, input
 * focus toggling, transparency, and phone/tablet DPI scaling.  Memory operations
 * still stay in MemoryOverlayService.
 */
public final class MemoryOverlayWindowSupport {
    public interface AddressProvider {
        String getDefaultAddress();
    }

    public interface StatusReporter {
        void reportStatus(String message);
    }

    public interface DumpCallback {
        void onDumpResult(boolean success, String text);
    }

    public interface DumpRequester {
        void requestDump(String begin, String end, DumpCallback callback);
    }

    public interface ByteWriteCallback {
        void onWriteResult(boolean success, String text);
    }

    public interface ByteWriteRequester {
        void requestWriteBytes(String address, String hexBytes, ByteWriteCallback callback);
    }

    static final int BASE_OVERLAY_FLAGS =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    private static final long INPUT_FOCUS_WINDOW_MS = 1800L;

    private MemoryOverlayWindowSupport() {
    }

    public static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Applies the same phone/tablet overlay scale to default dimensions. */
    public static int scaleOverlayPx(Context context, int valuePx) {
        return PermsTestUiCompat.scaleMemoryOverlayPx(context, valuePx);
    }

    /** Keeps restored/default overlay width inside the current display. */
    public static int fitOverlayWidth(Context context, int valuePx) {
        if (context == null || valuePx <= 0 || !PermsTestUiCompat.shouldScaleMemoryOverlayForDevice(context)) return valuePx;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int max = Math.max(dp(context, 300), dm.widthPixels - dp(context, 12));
        return Math.min(valuePx, max);
    }

    /** Keeps restored/default overlay height inside the current display. */
    public static int fitOverlayHeight(Context context, int valuePx) {
        if (context == null || valuePx <= 0 || !PermsTestUiCompat.shouldScaleMemoryOverlayForDevice(context)) return valuePx;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int max = Math.max(dp(context, 260), dm.heightPixels - dp(context, 24));
        return Math.min(valuePx, max);
    }

    public static int fitOverlayX(Context context, int x, int widthPx) {
        if (context == null || !PermsTestUiCompat.shouldScaleMemoryOverlayForDevice(context)) return x;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int max = Math.max(0, dm.widthPixels - Math.max(1, widthPx) - dp(context, 4));
        return Math.max(0, Math.min(x, max));
    }

    public static int fitOverlayY(Context context, int y, int heightPx) {
        if (context == null || !PermsTestUiCompat.shouldScaleMemoryOverlayForDevice(context)) return y;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int h = heightPx > 0 ? heightPx : dp(context, 260);
        int max = Math.max(0, dm.heightPixels - h - dp(context, 4));
        return Math.max(0, Math.min(y, max));
    }

    public static void applyTransparency(Context context, View panel) {
        if (context == null || panel == null) return;
        boolean transparent = context.getSharedPreferences("perms_test", Context.MODE_PRIVATE)
                .getBoolean(MemoryToolHelper.KEY_OVERLAY_TRANSPARENT, true);
        panel.setAlpha(transparent ? 0.88f : 1.0f);
    }

    public static boolean hasFocusedInput(View root) {
        if (root == null) return false;
        View focused = root.findFocus();
        return focused instanceof android.widget.EditText;
    }

    /**
     * Adds a floating overlay with persisted geometry.  Defaults are scaled before
     * saved sizes are clamped so old tablet-sized windows do not open off-screen
     * on smaller phones.
     */
    public static WindowManager.LayoutParams addView(Context context,
                                              WindowManager wm,
                                              View root,
                                              String prefsName,
                                              String prefX,
                                              String prefY,
                                              String prefW,
                                              String prefH,
                                              int defaultW,
                                              int defaultH,
                                              int defaultX,
                                              int defaultY) {
        final int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        PermsTestUiCompat.applyMemoryOverlayUiProfile(context, root);
        SharedPreferences sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        int scaledDefaultW = fitOverlayWidth(context, scaleOverlayPx(context, defaultW));
        int scaledDefaultH = fitOverlayHeight(context, scaleOverlayPx(context, defaultH));
        int savedW = fitOverlayWidth(context, sp.getInt(prefW, scaledDefaultW));
        int savedH = fitOverlayHeight(context, sp.getInt(prefH, scaledDefaultH));
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                savedW,
                savedH,
                type,
                BASE_OVERLAY_FLAGS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = fitOverlayX(context, sp.getInt(prefX, scaleOverlayPx(context, defaultX)), savedW);
        p.y = fitOverlayY(context, sp.getInt(prefY, scaleOverlayPx(context, defaultY)), savedH);
        wm.addView(root, p);
        return p;
    }

    public static void hide(WindowManager wm, View root, WindowManager.LayoutParams params) {
        if (root == null) return;
        try {
            root.setVisibility(View.GONE);
            setInteractive(wm, root, params, false);
        } catch (Throwable ignored) {
        }
    }

    public static void destroy(WindowManager wm, View root) {
        if (wm == null || root == null) return;
        try {
            wm.removeView(root);
        } catch (Throwable ignored) {
        }
    }

    /** Clears saved geometry and immediately returns a visible overlay window to its defaults. */
    public static void resetBounds(Context context,
                                   WindowManager wm,
                                   View root,
                                   WindowManager.LayoutParams params,
                                   String prefsName,
                                   String prefX,
                                   String prefY,
                                   String prefW,
                                   String prefH,
                                   int defaultW,
                                   int defaultH,
                                   int defaultX,
                                   int defaultY) {
        if (context == null) return;
        try {
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit()
                    .remove(prefX)
                    .remove(prefY)
                    .remove(prefW)
                    .remove(prefH)
                    .apply();
        } catch (Throwable ignored) {
        }
        if (wm == null || root == null || params == null) return;
        try {
            int width = fitOverlayWidth(context, scaleOverlayPx(context, defaultW));
            int height = fitOverlayHeight(context, scaleOverlayPx(context, defaultH));
            params.width = width;
            params.height = height;
            params.x = fitOverlayX(context, scaleOverlayPx(context, defaultX), width);
            params.y = fitOverlayY(context, scaleOverlayPx(context, defaultY), height);
            wm.updateViewLayout(root, params);
        } catch (Throwable ignored) {
        }
    }

    /** Switches between passive overlay mode and input-focused edit/dropdown mode. */
    public static void setInteractive(WindowManager wm, View root, WindowManager.LayoutParams params, boolean interactive) {
        if (wm == null || root == null || params == null) return;
        int flags = PermsTestVrOverlayCompat.buildOverlayFlags(root.getContext(), BASE_OVERLAY_FLAGS, interactive);
        if (params.flags == flags) return;
        params.flags = flags;
        try {
            wm.updateViewLayout(root, params);
        } catch (Throwable ignored) {
        }
    }

    /** Persists window position after header drag while respecting scaled bounds. */
    public static void attachDragHandler(Context context,
                                  WindowManager wm,
                                  View header,
                                  View root,
                                  WindowManager.LayoutParams params,
                                  String prefsName,
                                  String prefX,
                                  String prefY,
                                  String prefW,
                                  String prefH) {
        if (header == null || root == null || params == null) return;
        header.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float touchX;
            private float touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event == null) return false;
                try {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            setInteractive(wm, root, params, true);
                            startX = params.x;
                            startY = params.y;
                            touchX = event.getRawX();
                            touchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = startX + (int) (event.getRawX() - touchX);
                            params.y = startY + (int) (event.getRawY() - touchY);
                            wm.updateViewLayout(root, params);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            saveBounds(context, prefsName, params, prefX, prefY, prefW, prefH);
                            root.postDelayed(() -> setInteractive(wm, root, params, false), 250L);
                            return true;
                        default:
                            return false;
                    }
                } catch (Throwable ignored) {
                    return false;
                }
            }
        });
    }

    public static void attachResizeHandler(Context context,
                                    WindowManager wm,
                                    View handle,
                                    View root,
                                    WindowManager.LayoutParams params,
                                    String prefsName,
                                    String prefX,
                                    String prefY,
                                    String prefW,
                                    String prefH) {
        if (handle == null || root == null || params == null) return;
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int startW;
            private int startH;
            private float touchX;
            private float touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event == null) return false;
                try {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            setInteractive(wm, root, params, true);
                            startW = params.width;
                            startH = params.height <= 0 ? root.getHeight() : params.height;
                            touchX = event.getRawX();
                            touchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            int minW = Math.min(fitOverlayWidth(context, scaleOverlayPx(context, dp(context, 280))), dp(context, 280));
                            int minH = Math.min(fitOverlayHeight(context, scaleOverlayPx(context, dp(context, 220))), dp(context, 220));
                            params.width = Math.max(minW, startW + (int) (event.getRawX() - touchX));
                            params.height = Math.max(minH, startH + (int) (event.getRawY() - touchY));
                            wm.updateViewLayout(root, params);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            saveBounds(context, prefsName, params, prefX, prefY, prefW, prefH);
                            root.postDelayed(() -> setInteractive(wm, root, params, false), 250L);
                            return true;
                        default:
                            return false;
                    }
                } catch (Throwable ignored) {
                    return false;
                }
            }
        });
    }

    public static void installInputHook(WindowManager wm, View input, View root, WindowManager.LayoutParams params) {
        if (input == null || root == null || params == null) return;
        try {
            input.setFocusableInTouchMode(true);
        } catch (Throwable ignored) {
        }
        final long[] focusAllowedUntil = {0L};
        input.setOnTouchListener((v, event) -> {
            if (event == null) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                focusAllowedUntil[0] = SystemClock.uptimeMillis() + INPUT_FOCUS_WINDOW_MS;
                setTextInputInteractive(wm, root, params, true);
            } else if (action == MotionEvent.ACTION_UP) {
                focusAllowedUntil[0] = SystemClock.uptimeMillis() + INPUT_FOCUS_WINDOW_MS;
                setTextInputInteractive(wm, root, params, true);
                v.requestFocus();
                v.postDelayed(() -> showKeyboard(v), 80L);
            }
            return false;
        });
        input.setOnLongClickListener(v -> {
            focusAllowedUntil[0] = SystemClock.uptimeMillis() + INPUT_FOCUS_WINDOW_MS;
            setTextInputInteractive(wm, root, params, true);
            v.requestFocus();
            showKeyboard(v);
            return false;
        });
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (SystemClock.uptimeMillis() <= focusAllowedUntil[0]) {
                    setTextInputInteractive(wm, root, params, true);
                } else {
                    root.post(() -> {
                        try { v.clearFocus(); } catch (Throwable ignored) {}
                        setTextInputInteractive(wm, root, params, false);
                    });
                }
            } else {
                root.postDelayed(() -> {
                    if (!hasFocusedInput(root)) {
                        setTextInputInteractive(wm, root, params, false);
                    }
                }, 180L);
            }
        });
    }

    private static void setTextInputInteractive(WindowManager wm, View root, WindowManager.LayoutParams params, boolean interactive) {
        if (wm == null || root == null || params == null) return;
        if (!interactive || !PermsTestVrOverlayCompat.isEnabled(root.getContext())) {
            setInteractive(wm, root, params, interactive);
            return;
        }
        // Quest/Horizon routing can keep a focused overlay in front of the target app
        // after several panel/tool interactions. Keep VR tool windows non-focusable so
        // touches outside the tool continue to pass back to the game. Dedicated VR text
        // input can be added per tool field later without changing normal Android paths.
        if (PermsTestVrOverlayCompat.shouldKeepOverlayNonFocusable(root.getContext())) {
            setInteractive(wm, root, params, interactive);
            return;
        }
        if (params.flags == BASE_OVERLAY_FLAGS) return;
        params.flags = BASE_OVERLAY_FLAGS;
        try {
            wm.updateViewLayout(root, params);
        } catch (Throwable ignored) {
        }
    }

    private static void showKeyboard(View input) {
        showKeyboardOnce(input);
        try {
            input.postDelayed(() -> showKeyboardOnce(input), 160L);
        } catch (Throwable ignored) {
        }
    }

    private static void showKeyboardOnce(View input) {
        try {
            InputMethodManager imm = (InputMethodManager) input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        } catch (Throwable ignored) {
        }
    }


    public static void updateAddressDefaults(View root,
                                      int addressId,
                                      int lengthId,
                                      String defaultLength,
                                      AddressProvider addressProvider) {
        updateAddressDefaults(root, addressId, lengthId, defaultLength, addressProvider, false);
    }

    public static void updateAddressDefaults(View root,
                                      int addressId,
                                      int lengthId,
                                      String defaultLength,
                                      AddressProvider addressProvider,
                                      boolean forceAddress) {
        if (root == null) return;
        try {
            TextView address = root.findViewById(addressId);
            if (address != null) {
                String value = addressProvider == null ? "" : addressProvider.getDefaultAddress();
                if (!TextUtils.isEmpty(value) && (forceAddress || TextUtils.isEmpty(address.getText()))) {
                    address.setText(value);
                }
            }
            TextView length = root.findViewById(lengthId);
            if (length != null && TextUtils.isEmpty(length.getText())) {
                length.setText(defaultLength);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void saveBounds(Context context,
                                   String prefsName,
                                   WindowManager.LayoutParams p,
                                   String prefX,
                                   String prefY,
                                   String prefW,
                                   String prefH) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit()
                .putInt(prefX, p.x)
                .putInt(prefY, p.y)
                .putInt(prefW, p.width)
                .putInt(prefH, p.height)
                .apply();
    }
}
