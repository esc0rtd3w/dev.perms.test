package dev.perms.test.memory.tools;

import dev.perms.test.memory.overlay.MemoryOverlayWindowSupport;
import dev.perms.test.R;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.vr.PermsTestVrOverlayCompat;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

public final class MemorySpecialToolsOverlayController {
    public interface ActionListener {
        void onSearchMemoryFileType(String extension);
        void onExportMemoryByType(String extension, String begin, String end);
        void onImportMemoryByType(String extension, String begin, String sourcePath);
        void onStartTimerBaseline();
        void onFindTimerChanges();
    }

    private static final String PREFS = "perms_test";
    private static final String PREF_X = "memory_special_overlay_x";
    private static final String PREF_Y = "memory_special_overlay_y";
    private static final String PREF_W = "memory_special_overlay_w";
    private static final String PREF_H = "memory_special_overlay_h";
    private static final int DEFAULT_WINDOW_W_DP = 620;
    private static final int DEFAULT_WINDOW_H_DP = 560;
    private static final int DEFAULT_WINDOW_X_DP = 560;
    private static final int DEFAULT_WINDOW_Y_DP = 160;

    private static final String[] COMMON_FILE_TYPES = new String[]{
            "png", "jpg", "gif", "webp", "zip", "apk", "dex", "elf", "so", "ogg", "mp3", "wav", "mp4", "xml", "json", "js"
    };

    private final Context appContext;
    private final Context overlayContext;
    private final WindowManager wm;
    private final MemoryOverlayWindowSupport.AddressProvider addressProvider;
    private final MemoryOverlayWindowSupport.StatusReporter statusReporter;
    private final ActionListener actionListener;
    private final Runnable closeCallback;

    private View root;
    private WindowManager.LayoutParams params;
    private boolean panelMode;
    private boolean externalInputActive;
    private MaterialAutoCompleteTextView ddFileType;

    public MemorySpecialToolsOverlayController(Context appContext,
                                        Context overlayContext,
                                        WindowManager wm,
                                        MemoryOverlayWindowSupport.AddressProvider addressProvider,
                                        MemoryOverlayWindowSupport.StatusReporter statusReporter,
                                        ActionListener actionListener,
                                        Runnable closeCallback) {
        this.appContext = appContext.getApplicationContext();
        this.overlayContext = overlayContext;
        this.wm = wm;
        this.addressProvider = addressProvider;
        this.statusReporter = statusReporter;
        this.actionListener = actionListener;
        this.closeCallback = closeCallback;
    }

    public void show() {
        try {
            panelMode = false;
            if (root != null && root.getParent() != null && params == null) {
                detachRootFromParent();
                root = null;
            }
            if (root == null) {
                root = LayoutInflater.from(overlayContext).inflate(R.layout.overlay_memory_special_tools, null, false);
                params = MemoryOverlayWindowSupport.addView(appContext, wm, root, PREFS, PREF_X, PREF_Y, PREF_W, PREF_H, dp(DEFAULT_WINDOW_W_DP), dp(DEFAULT_WINDOW_H_DP), dp(DEFAULT_WINDOW_X_DP), dp(DEFAULT_WINDOW_Y_DP));
                bindWindow();
            }
            applyAddressDefaults();
            root.setVisibility(View.VISIBLE);
            ensureDefaultViewSize();
            MemoryOverlayWindowSupport.applyTransparency(appContext, root.findViewById(R.id.cardMemorySpecialOverlay));
            if (!MemoryOverlayWindowSupport.hasFocusedInput(root)) {
                MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
            }
        } catch (Throwable t) {
            reportStatus("Special Tools overlay failed: " + t);
        }
    }

    public void showInPanel(ViewGroup container) {
        try {
            if (container == null) return;
            panelMode = true;
            if (root != null && params != null) {
                MemoryOverlayWindowSupport.destroy(wm, root);
                root = null;
                params = null;
            }
            if (root == null) {
                root = LayoutInflater.from(overlayContext).inflate(R.layout.overlay_memory_special_tools, null, false);
                bindWindow();
            }
            detachRootFromParent();
            container.removeAllViews();
            container.addView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            applyAddressDefaults();
            root.setVisibility(View.VISIBLE);
        } catch (Throwable t) {
            reportStatus("Special Tools panel failed: " + t);
        }
    }

    public void destroy() {
        if (params != null) {
            MemoryOverlayWindowSupport.destroy(wm, root);
        } else {
            detachRootFromParent();
        }
        root = null;
        params = null;
        panelMode = false;
        ddFileType = null;
    }

    public void setExternalInputActive(boolean active) {
        externalInputActive = active;
        if (active) {
            releaseInputFocus();
        }
    }

    public void releaseInputFocus() {
        try { if (root != null && root.findFocus() != null) root.findFocus().clearFocus(); } catch (Throwable ignored) {}
        hideKeyboard();
        MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
    }


    private void detachRootFromParent() {
        if (root == null) return;
        try {
            ViewParent parent = root.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(root);
            }
        } catch (Throwable ignored) {
        }
    }

    private void bindWindow() {
        MemoryOverlayWindowSupport.attachDragHandler(appContext, wm, root.findViewById(R.id.overlayMemorySpecialHeader), root, params, PREFS, PREF_X, PREF_Y, PREF_W, PREF_H);
        MemoryOverlayWindowSupport.attachResizeHandler(appContext, wm, root.findViewById(R.id.overlayMemorySpecialResizeHandle), root, params, PREFS, PREF_X, PREF_Y, PREF_W, PREF_H);

        ddFileType = root.findViewById(R.id.ddOverlayMemorySpecialFileType);
        if (ddFileType != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(overlayContext, android.R.layout.simple_list_item_1, COMMON_FILE_TYPES);
            ddFileType.setAdapter(adapter);
            DropdownUi.prepareExposedDropdown(root.findViewById(R.id.tilOverlayMemorySpecialFileType), ddFileType);
            ddFileType.setText(COMMON_FILE_TYPES[0], false);
            ddFileType.setKeyListener(null);
            ddFileType.setOnClickListener(v -> {
                if (externalInputActive) return;
                hideKeyboard();
                try { DropdownUi.showDropdown(ddFileType); } catch (Throwable ignored) {}
            });
            ddFileType.setOnTouchListener((v, event) -> {
                hideKeyboard();
                return externalInputActive;
            });
        }

        View resetWindow = root.findViewById(R.id.btnOverlayMemorySpecialResetWindow);
        if (resetWindow != null) resetWindow.setOnClickListener(v -> resetWindowBounds());

        View close = root.findViewById(R.id.btnOverlayMemorySpecialClose);
        if (close != null) close.setOnClickListener(v -> {
            if (panelMode || PermsTestVrOverlayCompat.shouldDestroyToolOverlayOnClose(appContext)) {
                destroy();
                if (closeCallback != null) closeCallback.run();
            } else {
                MemoryOverlayWindowSupport.hide(wm, root, params);
            }
        });
        View search = root.findViewById(R.id.btnOverlayMemorySpecialSearchType);
        if (search != null) search.setOnClickListener(v -> { releaseInputFocus(); if (actionListener != null) actionListener.onSearchMemoryFileType(currentFileType()); });
        View export = root.findViewById(R.id.btnOverlayMemorySpecialExportType);
        if (export != null) export.setOnClickListener(v -> { releaseInputFocus(); if (actionListener != null) actionListener.onExportMemoryByType(currentFileType(), textOf(R.id.edtOverlayMemorySpecialBegin), textOf(R.id.edtOverlayMemorySpecialEnd)); });
        View importReplace = root.findViewById(R.id.btnOverlayMemorySpecialImportType);
        if (importReplace != null) {
            importReplace.setOnClickListener(v -> {
                releaseInputFocus();
                if (actionListener != null) {
                    actionListener.onImportMemoryByType(
                            currentFileType(),
                            textOf(R.id.edtOverlayMemorySpecialBegin),
                            textOf(R.id.edtOverlayMemorySpecialSourcePath));
                }
            });
        }
        View timerBaseline = root.findViewById(R.id.btnOverlayMemorySpecialTimerBaseline);
        if (timerBaseline != null) timerBaseline.setOnClickListener(v -> { releaseInputFocus(); if (actionListener != null) actionListener.onStartTimerBaseline(); });
        View timerFind = root.findViewById(R.id.btnOverlayMemorySpecialTimerFind);
        if (timerFind != null) timerFind.setOnClickListener(v -> { releaseInputFocus(); if (actionListener != null) actionListener.onFindTimerChanges(); });

        MemoryOverlayWindowSupport.installInputHook(wm, root.findViewById(R.id.edtOverlayMemorySpecialCustomType), root, params);
        MemoryOverlayWindowSupport.installInputHook(wm, root.findViewById(R.id.edtOverlayMemorySpecialBegin), root, params);
        MemoryOverlayWindowSupport.installInputHook(wm, root.findViewById(R.id.edtOverlayMemorySpecialEnd), root, params);
        MemoryOverlayWindowSupport.installInputHook(wm, root.findViewById(R.id.edtOverlayMemorySpecialSourcePath), root, params);
    }


    private void resetWindowBounds() {
        MemoryOverlayWindowSupport.resetBounds(
                appContext,
                wm,
                root,
                params,
                PREFS,
                PREF_X,
                PREF_Y,
                PREF_W,
                PREF_H,
                dp(DEFAULT_WINDOW_W_DP),
                dp(DEFAULT_WINDOW_H_DP),
                dp(DEFAULT_WINDOW_X_DP),
                dp(DEFAULT_WINDOW_Y_DP));
        ensureDefaultViewSize();
        reportStatus(panelMode ? "Special Tools overlay saved window size reset." : "Special Tools overlay window reset.");
    }

    private void ensureDefaultViewSize() {
        if (params == null || wm == null || root == null) return;
        boolean changed = false;
        int minWidth = MemoryOverlayWindowSupport.fitOverlayWidth(appContext, MemoryOverlayWindowSupport.scaleOverlayPx(appContext, dp(620)));
        int minHeight = MemoryOverlayWindowSupport.fitOverlayHeight(appContext, MemoryOverlayWindowSupport.scaleOverlayPx(appContext, dp(540)));
        if (params.width > 0 && params.width < minWidth) {
            params.width = minWidth;
            changed = true;
        }
        if (params.height > 0 && params.height < minHeight) {
            params.height = minHeight;
            changed = true;
        }
        if (changed) {
            try { wm.updateViewLayout(root, params); } catch (Throwable ignored) {}
        }
    }

    private void applyAddressDefaults() {
        if (root == null) return;
        String address = addressProvider == null ? "" : addressProvider.getDefaultAddress();
        TextView begin = root.findViewById(R.id.edtOverlayMemorySpecialBegin);
        TextView end = root.findViewById(R.id.edtOverlayMemorySpecialEnd);
        if (begin != null && TextUtils.isEmpty(begin.getText()) && !TextUtils.isEmpty(address)) begin.setText(address.trim());
        if (end != null && TextUtils.isEmpty(end.getText()) && !TextUtils.isEmpty(address)) {
            try { end.setText("0x" + Long.toHexString(parseAddress(address) + (1024L * 1024L))); } catch (Throwable ignored) {}
        }
    }

    private String currentFileType() {
        String custom = textOf(R.id.edtOverlayMemorySpecialCustomType);
        if (!TextUtils.isEmpty(custom)) return custom;
        String value = "";
        try {
            value = ddFileType == null || ddFileType.getText() == null ? "" : ddFileType.getText().toString().trim();
        } catch (Throwable ignored) {
        }
        return TextUtils.isEmpty(value) ? "bin" : value;
    }

    private String textOf(int id) {
        TextView view = root == null ? null : root.findViewById(id);
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) appContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && root != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    private static long parseAddress(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("0x") || value.startsWith("0X")) return Long.parseUnsignedLong(value.substring(2), 16);
        return Long.parseUnsignedLong(value, 16);
    }

    private int dp(int value) { return MemoryOverlayWindowSupport.dp(appContext, value); }
    private void reportStatus(String message) { if (statusReporter != null) statusReporter.reportStatus(message); }
}
