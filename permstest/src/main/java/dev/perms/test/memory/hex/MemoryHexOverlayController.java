package dev.perms.test.memory.hex;

import dev.perms.test.memory.overlay.MemoryOverlayWindowSupport;
import dev.perms.test.R;
import dev.perms.test.vr.PermsTestVrOverlayCompat;
import dev.perms.test.memory.MemoryToolRuntime;
import dev.perms.test.memory.payload.MemoryHexPayloadStore;
import dev.perms.test.ui.DropdownUi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/**
 * Floating Hex Editor overlay controller.
 *
 * This class owns only the overlay UI and user interaction state.  It does not
 * directly attach to processes or patch memory; those privileged operations are
 * delegated through callback interfaces back to MemoryOverlayService.  Keeping
 * that split makes the same controller usable on phone/tablet overlays and the
 * separate VR testing path without duplicating the memory backend.
 */
public final class MemoryHexOverlayController {
    public interface SelectionListener {
        void onHexSelection(long byteAddress, long dwordAddress, long dwordValue);
    }

    public interface AddressChangeListener {
        void onHexAddressChanged(long address);
    }

    /** Callback used by service-side payload search/apply requests. */
    public interface PayloadSearchCallback {
        void onPayloadSearchResult(boolean success, String message);
    }

    /** Requests a byte-pattern search through apk-medit search-bytes. */
    public interface PayloadSearchRequester {
        void requestSearchHexPayload(String hexBytes, String maskHex, PayloadSearchCallback callback);
    }

    /**
     * One complete apply operation: find originalHex in the target process and
     * replace it with patchedHex at the discovered address.
     */
    public static final class PayloadApplySpec {
        public final String name;
        public final String originalHex;
        public final String patchedHex;
        public final String maskHex;
        public final String sectionStartHex;
        public final String sectionEndHex;
        public final boolean enabled;
        public final boolean preserveMaskWildcards;

        public PayloadApplySpec(String name, String originalHex, String patchedHex, String maskHex) {
            this(name, originalHex, patchedHex, maskHex, null, null, true, false);
        }

        public PayloadApplySpec(String name, String originalHex, String patchedHex, String maskHex, boolean enabled) {
            this(name, originalHex, patchedHex, maskHex, null, null, enabled, false);
        }

        public PayloadApplySpec(String name, String originalHex, String patchedHex, String maskHex, boolean enabled, boolean preserveMaskWildcards) {
            this(name, originalHex, patchedHex, maskHex, null, null, enabled, preserveMaskWildcards);
        }

        public PayloadApplySpec(String name,
                         String originalHex,
                         String patchedHex,
                         String maskHex,
                         String sectionStartHex,
                         String sectionEndHex,
                         boolean enabled,
                         boolean preserveMaskWildcards) {
            this.name = name;
            this.originalHex = originalHex;
            this.patchedHex = patchedHex;
            this.maskHex = maskHex;
            this.sectionStartHex = sectionStartHex;
            this.sectionEndHex = sectionEndHex;
            this.enabled = enabled;
            this.preserveMaskWildcards = preserveMaskWildcards;
        }
    }

    /** Applies one or more complete payloads through the service backend. */
    public interface PayloadApplyRequester {
        void requestApplyHexPayloads(ArrayList<PayloadApplySpec> payloads, PayloadSearchCallback callback);
    }

    public interface PackageNameProvider {
        String currentPackageName();
    }

    public interface PayloadPatchValueListener {
        void onHexPayloadLoaded(String hexBytes);
    }

    private static final String PREFS = "perms_test";
    private static final String PREF_X = "memory_hex_overlay_x";
    private static final String PREF_Y = "memory_hex_overlay_y";
    private static final String PREF_W = "memory_hex_overlay_w";
    private static final String PREF_H = "memory_hex_overlay_h";
    private static final int DEFAULT_WINDOW_W_DP = 600;
    private static final int DEFAULT_WINDOW_H_DP = 445;
    private static final int DEFAULT_WINDOW_X_DP = 460;
    private static final int DEFAULT_WINDOW_Y_DP = 90;
    private static final String PAYLOAD_DIR = "/sdcard/dev.perms.test/memory_payloads";
    private static final String PAYLOAD_SCHEMA = "perms_test_memory_hex_payload";
    private static final int DEFAULT_LENGTH = 1024;
    private static final int MAX_HEX_READ = 4096;
    private static final long LIVE_REFRESH_MS = 1000L;
    private static final int DEFAULT_WRITE_WIDTH_CHECKBOX_ID = R.id.chkOverlayMemoryHexWriteDword;
    private static final String[] ALIGNMENT_LABELS = new String[]{"16 bytes", "Byte", "Word", "Dword", "Qword"};

    private final Context appContext;
    private final Context overlayContext;
    private final WindowManager wm;
    private final MemoryOverlayWindowSupport.AddressProvider addressProvider;
    private final MemoryOverlayWindowSupport.StatusReporter statusReporter;
    private final MemoryOverlayWindowSupport.DumpRequester dumpRequester;
    private final MemoryOverlayWindowSupport.ByteWriteRequester byteWriteRequester;
    private final PayloadSearchRequester payloadSearchRequester;
    private final PayloadApplyRequester payloadApplyRequester;
    private final PackageNameProvider packageNameProvider;
    private final AddressChangeListener addressChangeListener;
    private final SelectionListener selectionListener;
    private final PayloadPatchValueListener payloadPatchValueListener;
    private final Runnable closeCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View root;
    private WindowManager.LayoutParams params;
    private boolean panelMode;
    private boolean readInFlight;
    private boolean externalInputActive;
    private boolean backendBusy;
    private String lastRenderedOutput;
    private long lastBaseAddress = -1L;
    private byte[] lastBytes = new byte[0];
    private long readGeneration;
    private final ArrayList<HitRegion> hitRegions = new ArrayList<>();
    private long selectedByteAddress = -1L;
    private long selectedDwordAddress = -1L;
    private long selectedDwordValue = -1L;
    private long selectedHighlightAddress = -1L;
    private int selectedHighlightBytes;
    private boolean selectedHighlightFromAscii;
    private float hexTouchDownX;
    private float hexTouchDownY;
    private boolean hexTouchMoved;
    // Drag Select state.  The start address is set on ACTION_DOWN and cleared
    // when the gesture ends or when Tap mode is restored.
    private long payloadTouchStartAddress = -1L;
    // Optional anchor used for long payload selections.  Set S records the first byte,
    // then Set E can be pressed after scrolling to select through the current byte.
    private long payloadSelectionMarkedStartAddress = -1L;
    private boolean payloadTouchFromAscii;

    // Section markers staged from Drag Select. Sec S/Sec E record the current
    // byte range and keep whether the user selected it from the rendered ASCII
    // side or the hex side, so the Save dialog can fill the matching section
    // field cleanly.
    private String pendingPayloadSectionStartAscii;
    private String pendingPayloadSectionEndAscii;
    private String pendingPayloadSectionStartHex;
    private String pendingPayloadSectionEndHex;

    private boolean hexScrollThumbUpdating;
    // Loaded payload state.  original_hex is the search signature; patched_hex
    // is the replacement block. loadedPayloadHex is kept as the patch-text bridge.
    private String loadedPayloadHex;
    private String loadedPayloadOriginalHex;
    private String loadedPayloadPatchedHex;
    private String loadedPayloadMaskHex;
    private String loadedPayloadSectionStartHex;
    private String loadedPayloadSectionEndHex;
    private boolean loadedPayloadPreserveMaskWildcards;
    private String loadedPayloadName;
    private String loadedPayloadPackageName;
    private long loadedPayloadOriginalAddress = -1L;
    private long loadedPayloadPatchedAddress = -1L;
    private long pendingZeroBaseAddress = -1L;
    private int pendingZeroByteCount;
    private boolean resetOutputScrollOnNextRender;

    // Live refresh is paused whenever the user is typing, dragging, or a backend
    // command is busy so late reads do not repaint stale data over an edit.
    private final Runnable liveRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isLiveEnabled()) return;
            if (externalInputActive || backendBusy) {
                scheduleLiveRefresh();
                return;
            }
            if (MemoryOverlayWindowSupport.hasFocusedInput(root)) {
                scheduleLiveRefresh();
                return;
            }
            readHexRange(false);
        }
    };

    public MemoryHexOverlayController(Context appContext,
                               Context overlayContext,
                               WindowManager wm,
                               MemoryOverlayWindowSupport.AddressProvider addressProvider,
                               MemoryOverlayWindowSupport.StatusReporter statusReporter,
                               MemoryOverlayWindowSupport.DumpRequester dumpRequester,
                               MemoryOverlayWindowSupport.ByteWriteRequester byteWriteRequester,
                               PayloadSearchRequester payloadSearchRequester,
                               PayloadApplyRequester payloadApplyRequester,
                               PackageNameProvider packageNameProvider,
                               AddressChangeListener addressChangeListener,
                               SelectionListener selectionListener,
                               PayloadPatchValueListener payloadPatchValueListener,
                               Runnable closeCallback) {
        this.appContext = appContext.getApplicationContext();
        this.overlayContext = overlayContext;
        this.wm = wm;
        this.addressProvider = addressProvider;
        this.statusReporter = statusReporter;
        this.dumpRequester = dumpRequester;
        this.byteWriteRequester = byteWriteRequester;
        this.payloadSearchRequester = payloadSearchRequester;
        this.payloadApplyRequester = payloadApplyRequester;
        this.packageNameProvider = packageNameProvider;
        this.addressChangeListener = addressChangeListener;
        this.selectionListener = selectionListener;
        this.payloadPatchValueListener = payloadPatchValueListener;
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
                root = LayoutInflater.from(overlayContext).inflate(R.layout.overlay_memory_hex, null, false);
                params = MemoryOverlayWindowSupport.addView(
                        appContext,
                        wm,
                        root,
                        PREFS,
                        PREF_X,
                        PREF_Y,
                        PREF_W,
                        PREF_H,
                        dp(DEFAULT_WINDOW_W_DP),
                        dp(DEFAULT_WINDOW_H_DP),
                        dp(DEFAULT_WINDOW_X_DP),
                        dp(DEFAULT_WINDOW_Y_DP));
                bindWindow();
            }
            MemoryOverlayWindowSupport.updateAddressDefaults(
                    root,
                    R.id.edtOverlayMemoryHexAddress,
                    R.id.edtOverlayMemoryHexLength,
                    String.valueOf(DEFAULT_LENGTH),
                    addressProvider,
                    true);
            root.setVisibility(View.VISIBLE);
            ensureDefaultViewSize();
            MemoryOverlayWindowSupport.applyTransparency(appContext, root.findViewById(R.id.cardMemoryHexOverlay));
            if (!MemoryOverlayWindowSupport.hasFocusedInput(root)) {
                MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
            }
            if (isLiveEnabled()) scheduleLiveRefresh();
        } catch (Throwable t) {
            reportStatus("Hex overlay failed: " + t);
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
                root = LayoutInflater.from(overlayContext).inflate(R.layout.overlay_memory_hex, null, false);
                bindWindow();
            }
            detachRootFromParent();
            container.removeAllViews();
            container.addView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            MemoryOverlayWindowSupport.updateAddressDefaults(
                    root,
                    R.id.edtOverlayMemoryHexAddress,
                    R.id.edtOverlayMemoryHexLength,
                    String.valueOf(DEFAULT_LENGTH),
                    addressProvider,
                    true);
            root.setVisibility(View.VISIBLE);
            if (isLiveEnabled()) scheduleLiveRefresh();
        } catch (Throwable t) {
            reportStatus("Hex panel failed: " + t);
        }
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
        reportStatus(panelMode ? "Hex overlay saved window size reset." : "Hex overlay window reset.");
    }

    private void ensureDefaultViewSize() {
        if (params == null || wm == null || root == null) return;
        boolean changed = false;
        int minWidth = MemoryOverlayWindowSupport.fitOverlayWidth(appContext, MemoryOverlayWindowSupport.scaleOverlayPx(appContext, dp(600)));
        int minHeight = MemoryOverlayWindowSupport.fitOverlayHeight(appContext, MemoryOverlayWindowSupport.scaleOverlayPx(appContext, dp(440)));
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

    public void setExternalInputActive(boolean active) {
        externalInputActive = active;
        if (active) {
            stopLiveRefresh();
        } else if (isLiveEnabled()) {
            scheduleLiveRefresh();
        }
    }

    public void setBackendBusy(boolean busy) {
        backendBusy = busy;
        if (busy) {
            stopLiveRefresh();
        } else if (isLiveEnabled()) {
            scheduleLiveRefresh();
        }
    }

    public void setAddress(String address, boolean autoRead) {
        if (root == null || TextUtils.isEmpty(address)) return;
        String cleanAddress = address.trim();
        TextView view = root.findViewById(R.id.edtOverlayMemoryHexAddress);
        String previousAddress = view == null || view.getText() == null ? "" : view.getText().toString().trim();
        boolean addressChanged = !TextUtils.equals(previousAddress, cleanAddress);
        if (view != null) view.setText(cleanAddress);
        try {
            long parsed = parseAddress(cleanAddress);
            selectedByteAddress = parsed;
            selectedDwordAddress = parsed & ~3L;
            selectedDwordValue = readLittleEndianUnsigned(selectedDwordAddress, 4);
            int width = selectedWriteWidthBytes();
            selectedHighlightAddress = selectedWriteAddress(width);
            selectedHighlightBytes = width;
            selectedHighlightFromAscii = false;
        } catch (Throwable ignored) {
            clearSelection();
        }
        if (addressChanged) {
            readGeneration++;
            readInFlight = false;
            requestOutputScrollToTop();
        }
        if (isActiveVisible() && (autoRead || isLiveEnabled())) readHexRange(false);
    }

    public boolean isActiveVisible() {
        try {
            return root != null && root.getVisibility() == View.VISIBLE && root.isAttachedToWindow();
        } catch (Throwable ignored) {
            return root != null && root.getVisibility() == View.VISIBLE;
        }
    }

    private boolean hasAddress() {
        return !TextUtils.isEmpty(textOf(R.id.edtOverlayMemoryHexAddress));
    }

    private void notifyHexAddressChanged(long address) {
        try {
            if (addressChangeListener != null) addressChangeListener.onHexAddressChanged(address);
        } catch (Throwable ignored) {
        }
    }

    public void destroy() {
        stopLiveRefresh();
        if (params != null) {
            MemoryOverlayWindowSupport.destroy(wm, root);
        } else {
            detachRootFromParent();
        }
        root = null;
        params = null;
        panelMode = false;
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
        MemoryOverlayWindowSupport.attachDragHandler(
                appContext,
                wm,
                root.findViewById(R.id.overlayMemoryHexHeader),
                root,
                params,
                PREFS,
                PREF_X,
                PREF_Y,
                PREF_W,
                PREF_H);
        MemoryOverlayWindowSupport.attachResizeHandler(
                appContext,
                wm,
                root.findViewById(R.id.overlayMemoryHexResizeHandle),
                root,
                params,
                PREFS,
                PREF_X,
                PREF_Y,
                PREF_W,
                PREF_H);

        View resetWindow = root.findViewById(R.id.btnOverlayMemoryHexResetWindow);
        if (resetWindow != null) resetWindow.setOnClickListener(v -> resetWindowBounds());

        View close = root.findViewById(R.id.btnOverlayMemoryHexClose);
        if (close != null) close.setOnClickListener(v -> {
            stopLiveRefresh();
            if (panelMode || PermsTestVrOverlayCompat.shouldDestroyToolOverlayOnClose(appContext)) {
                destroy();
                if (closeCallback != null) closeCallback.run();
            } else {
                MemoryOverlayWindowSupport.hide(wm, root, params);
            }
        });

        View read = root.findViewById(R.id.btnOverlayMemoryHexRead);
        if (read != null) read.setOnClickListener(v -> readHexRange(true));
        View write = root.findViewById(R.id.btnOverlayMemoryHexWrite);
        if (write != null) write.setOnClickListener(v -> showWriteDialogForSelection());
        // Payload buttons are bound even though the row is hidden in Tap mode.
        // updatePayloadModeUi() controls visibility without rebuilding listeners.
        View payloadFind = root.findViewById(R.id.btnOverlayMemoryHexPayloadFind);
        if (payloadFind != null) payloadFind.setOnClickListener(v -> searchSelectedOrLoadedPayload());
        View payloadSave = root.findViewById(R.id.btnOverlayMemoryHexPayloadSave);
        if (payloadSave != null) payloadSave.setOnClickListener(v -> saveSelectedPayload());
        View payloadMask = root.findViewById(R.id.btnOverlayMemoryHexPayloadMask);
        if (payloadMask != null) payloadMask.setOnClickListener(v -> maskSelectedPayloadBytes());
        View payloadLoad = root.findViewById(R.id.btnOverlayMemoryHexPayloadLoad);
        if (payloadLoad != null) payloadLoad.setOnClickListener(v -> showLoadPayloadDialog());
        View payloadWrite = root.findViewById(R.id.btnOverlayMemoryHexPayloadWrite);
        if (payloadWrite != null) payloadWrite.setOnClickListener(v -> writeLoadedPayloadToSelection());
        View payloadMarkStart = root.findViewById(R.id.btnOverlayMemoryHexPayloadMarkStart);
        if (payloadMarkStart != null) payloadMarkStart.setOnClickListener(v -> markPayloadSelectionStart());
        View payloadMarkEnd = root.findViewById(R.id.btnOverlayMemoryHexPayloadMarkEnd);
        if (payloadMarkEnd != null) payloadMarkEnd.setOnClickListener(v -> markPayloadSelectionEnd());
        View payloadSectionStart = root.findViewById(R.id.btnOverlayMemoryHexPayloadSectionStart);
        if (payloadSectionStart != null) payloadSectionStart.setOnClickListener(v -> markPayloadSectionStart());
        View payloadSectionEnd = root.findViewById(R.id.btnOverlayMemoryHexPayloadSectionEnd);
        if (payloadSectionEnd != null) payloadSectionEnd.setOnClickListener(v -> markPayloadSectionEnd());
        View payloadApply = root.findViewById(R.id.btnOverlayMemoryHexPayloadApply);
        if (payloadApply != null) payloadApply.setOnClickListener(v -> applyLoadedPayload());
        View payloadApplyAll = root.findViewById(R.id.btnOverlayMemoryHexPayloadApplyAll);
        if (payloadApplyAll != null) payloadApplyAll.setOnClickListener(v -> applyAllPayloadsForCurrentPackage());
        View payloadStartMinus = root.findViewById(R.id.btnOverlayMemoryHexPayloadStartMinus);
        if (payloadStartMinus != null) payloadStartMinus.setOnClickListener(v -> adjustPayloadSelectionStart(-1));
        View payloadStartPlus = root.findViewById(R.id.btnOverlayMemoryHexPayloadStartPlus);
        if (payloadStartPlus != null) payloadStartPlus.setOnClickListener(v -> adjustPayloadSelectionStart(1));
        View payloadEndMinus = root.findViewById(R.id.btnOverlayMemoryHexPayloadEndMinus);
        if (payloadEndMinus != null) payloadEndMinus.setOnClickListener(v -> adjustPayloadSelectionEnd(-1));
        View payloadEndPlus = root.findViewById(R.id.btnOverlayMemoryHexPayloadEndPlus);
        if (payloadEndPlus != null) payloadEndPlus.setOnClickListener(v -> adjustPayloadSelectionEnd(1));
        View selectionEdit = root.findViewById(R.id.btnOverlayMemoryHexSelectionEdit);
        if (selectionEdit != null) selectionEdit.setOnClickListener(v -> editCurrentSelection());
        View selectionMinus = root.findViewById(R.id.btnOverlayMemoryHexSelectionMinus);
        if (selectionMinus != null) selectionMinus.setOnClickListener(v -> slideCurrentSelection(-1));
        View selectionPlus = root.findViewById(R.id.btnOverlayMemoryHexSelectionPlus);
        if (selectionPlus != null) selectionPlus.setOnClickListener(v -> slideCurrentSelection(1));

        // Drag Select changes only the hit behavior: Tap mode edits one byte or
        // ASCII run, Drag mode selects a byte range for payload save/write.
        CheckBox dragSelect = root.findViewById(R.id.chkOverlayMemoryHexPayloads);
        if (dragSelect != null) {
            dragSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    payloadTouchStartAddress = -1L;
                    if (!isChecked) payloadSelectionMarkedStartAddress = -1L;
                    updatePayloadModeUi();
                    reportStatus(isChecked
                            ? "Drag Select enabled. Drag across hex/ASCII bytes to select payload bytes."
                            : "Tap mode enabled. Tap bytes/ASCII to edit normally.");
                    renderCachedHex();
                } catch (Throwable t) {
                    payloadTouchStartAddress = -1L;
                    payloadSelectionMarkedStartAddress = -1L;
                    reportStatus("Hex selection mode switch failed safely: " + t.getMessage());
                }
            });
        }
        updatePayloadModeUi();

        CheckBox live = root.findViewById(R.id.chkOverlayMemoryHexLive);
        if (live != null) {
            live.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    readHexRange(true);
                } else {
                    stopLiveRefresh();
                }
            });
        }
        bindWriteWidthCheckboxes();
        bindAlignmentControls();

        MemoryOverlayWindowSupport.installInputHook(wm, root.findViewById(R.id.edtOverlayMemoryHexAddress), root, params);
        MemoryOverlayWindowSupport.installInputHook(wm, root.findViewById(R.id.edtOverlayMemoryHexLength), root, params);

        TextView output = outputView();
        if (output != null) {
            // Use a plain scrolling movement method and route taps through explicit hit regions.
            // LinkMovementMethod keeps span state during drags and can re-fire clicks after scroll.
            output.setMovementMethod(ScrollingMovementMethod.getInstance());
            output.setLinksClickable(false);
            output.setHighlightColor(0x00000000);
            output.setOnTouchListener(this::handleHexOutputTouch);
            bindHexScrollThumb(output);
        }
    }

    /**
     * Adds a small thumbbar beside the hex text view.  TextView keeps the real
     * scroll state so tap/drag hit testing remains tied to the rendered text;
     * the SeekBar only mirrors or changes vertical scroll position.
     */
    private void bindHexScrollThumb(TextView output) {
        final SeekBar thumb = root == null ? null : root.findViewById(R.id.seekOverlayMemoryHexScroll);
        if (output == null || thumb == null) return;
        // Keep TextView as the scroll owner for byte hit testing.  The framework
        // scrollbar remains enabled but uses transparent drawables from XML; some
        // vendor Android builds crash when a scrolling TextView draws after its
        // ScrollBarDrawable has been disabled.  The visible fast-scroll control is
        // still the dedicated thumbbar beside the hex output.
        output.setVerticalScrollBarEnabled(true);
        output.setScrollbarFadingEnabled(false);
        thumb.setMax(1000);
        thumb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || hexScrollThumbUpdating) return;
                int maxScroll = hexOutputMaxScrollY(output);
                int y = maxScroll <= 0 ? 0 : Math.round((maxScroll * progress) / 1000.0f);
                try { output.scrollTo(output.getScrollX(), y); } catch (Throwable ignored) {}
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopLiveRefresh();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isLiveEnabled()) scheduleLiveRefresh();
            }
        });
        if (Build.VERSION.SDK_INT >= 23) {
            output.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> syncHexScrollThumbFromOutput(output));
        }
        output.post(() -> {
            fitHexScrollThumbToOutputHeight();
            syncHexScrollThumbFromOutput(output);
        });
    }

    /** Sizes the rotated SeekBar to the current output height after layout/resizes. */
    private void fitHexScrollThumbToOutputHeight() {
        if (root == null) return;
        View frame = root.findViewById(R.id.frameOverlayMemoryHexScrollThumb);
        SeekBar thumb = root.findViewById(R.id.seekOverlayMemoryHexScroll);
        if (frame == null || thumb == null || frame.getHeight() <= 0) return;
        ViewGroup.LayoutParams lp = thumb.getLayoutParams();
        if (lp == null) return;
        int desired = Math.max(dp(120), frame.getHeight());
        if (lp.width != desired || lp.height != dp(28)) {
            lp.width = desired;
            lp.height = dp(28);
            thumb.setLayoutParams(lp);
        }
    }

    /** Keeps the thumbbar visible only when the rendered hex text can scroll. */
    private void syncHexScrollThumbFromOutput(TextView output) {
        if (root == null || output == null) return;
        SeekBar thumb = root.findViewById(R.id.seekOverlayMemoryHexScroll);
        View frame = root.findViewById(R.id.frameOverlayMemoryHexScrollThumb);
        if (thumb == null || frame == null) return;
        int maxScroll = hexOutputMaxScrollY(output);
        int visibility = maxScroll > 0 ? View.VISIBLE : View.GONE;
        if (frame.getVisibility() != visibility) frame.setVisibility(visibility);
        if (maxScroll <= 0) {
            hexScrollThumbUpdating = true;
            try { thumb.setProgress(0); } finally { hexScrollThumbUpdating = false; }
            return;
        }
        int progress = Math.max(0, Math.min(1000, Math.round((output.getScrollY() * 1000.0f) / maxScroll)));
        hexScrollThumbUpdating = true;
        try { thumb.setProgress(progress); } finally { hexScrollThumbUpdating = false; }
    }

    private int hexOutputMaxScrollY(TextView output) {
        if (output == null || output.getLayout() == null) return 0;
        int contentHeight = output.getLayout().getHeight() + output.getTotalPaddingTop() + output.getTotalPaddingBottom();
        return Math.max(0, contentHeight - output.getHeight());
    }

    /**
     * Reads and renders a memory window.  Alignment can move the displayed start
     * address backward so the requested address stays visible on a clean boundary.
     */
    private void readHexRange(boolean manual) {
        if (!manual && externalInputActive) {
            scheduleLiveRefresh();
            return;
        }
        if (backendBusy) {
            if (manual) setOutput(outputView(), "Hex read paused while a memory scan/patch command is running.");
            scheduleLiveRefresh();
            return;
        }
        TextView output = outputView();
        if (dumpRequester == null) {
            setOutput(output, "Hex read is unavailable for the current memory backend/target.");
            return;
        }
        if (readInFlight) {
            if (!manual) {
                return;
            }
            // A manual read supersedes an older request. The generation check below prevents
            // late callbacks from repainting stale offsets after a new range is requested.
            readGeneration++;
            readInFlight = false;
        }
        long requestedBegin;
        int requestedLength;
        try {
            requestedBegin = parseAddress(textOf(R.id.edtOverlayMemoryHexAddress));
            requestedLength = parseLength(textOf(R.id.edtOverlayMemoryHexLength), DEFAULT_LENGTH, MAX_HEX_READ);
        } catch (IllegalArgumentException e) {
            setOutput(output, e.getMessage());
            stopLiveRefresh();
            return;
        }
        // Alignment affects display range only.  The requested address remains
        // the user's logical target; extra prefix bytes are added for context.
        int alignBytes = isHexAlignmentEnabled() ? selectedAlignBytes() : 1;
        long begin = alignHexDisplayBegin(requestedBegin, alignBytes);
        long prefixBytes = Math.max(0L, requestedBegin - begin);
        int length = (int) Math.min((long) MAX_HEX_READ, Math.max(1L, prefixBytes + requestedLength));
        long end = begin + length;
        readInFlight = true;
        final long requestGeneration = ++readGeneration;
        if (manual) {
            String alignNote = begin == requestedBegin ? "" : " (aligned from " + formatHex(requestedBegin) + " to " + alignBytes + " byte" + (alignBytes == 1 ? "" : "s") + ")";
            setOutput(output, "Reading " + length + " byte" + (length == 1 ? "" : "s") + " from " + formatHex(begin) + alignNote + "...\n");
        }
        if (!MemoryOverlayWindowSupport.hasFocusedInput(root)) {
            MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
        }
        final long finalBegin = begin;
        dumpRequester.requestDump(formatHex(begin), formatHex(end), (success, text) -> {
            if (requestGeneration != readGeneration) {
                return;
            }
            readInFlight = false;
            String body = text == null ? "" : text.trim();
            if (!success) {
                if (isTransientBackendBusy(body)) {
                    if (manual) {
                        setOutput(output, "Hex read skipped while the memory backend is busy. It will retry automatically.");
                    }
                    scheduleLiveRefresh();
                    return;
                }
                setOutput(output, body.isEmpty() ? "Hex read failed." : filteredBackendText(body));
                scheduleLiveRefresh();
                return;
            }
            byte[] bytes = parseHexDumpBytes(body);
            if (!manual && shouldKeepPreviousForTransientZeroDump(finalBegin, bytes)) {
                scheduleLiveRefresh();
                return;
            }
            if (manual || !isAllZero(bytes)) {
                clearPendingZeroFrame();
            }
            if (bytes.length == 0) {
                lastBaseAddress = -1L;
                lastBytes = new byte[0];
                hitRegions.clear();
                clearSelection();
                setOutput(output, "No hex bytes parsed from dump output.\n\n" + filteredBackendText(body));
            } else {
                lastBaseAddress = finalBegin;
                lastBytes = bytes;
                updateSelectionForRange(requestedBegin, finalBegin, bytes.length);
                setOutput(output, buildHexEditorView(finalBegin, bytes));
            }
            scheduleLiveRefresh();
        });
    }

    private void showWriteDialogForSelection() {
        if (selectedByteAddress < 0L) {
            reportStatus("Tap a byte in the hex view before writing.");
            return;
        }
        int width = selectedWriteWidthBytes();
        long address = selectedHighlightAddress >= 0L && selectedHighlightBytes == width
                ? selectedHighlightAddress
                : selectedWriteAddress(width);
        String initial = readHexBytesFromCache(address, width);
        showWriteDialog(address, initial, width);
    }

    private void showWriteDialog(long address, String initialHex, int width) {
        if (byteWriteRequester == null) {
            reportStatus("Hex write is not connected to the memory backend.");
            return;
        }
        stopLiveRefresh();
        MemoryOverlayWindowSupport.setInteractive(wm, root, params, true);
        LinearLayout box = new LinearLayout(overlayContext);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);
        TextView addressView = new TextView(overlayContext);
        addressView.setText("Address: " + formatHex(address) + " · " + writeWidthLabel(width));
        box.addView(addressView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextInputLayout inputLayout = new TextInputLayout(overlayContext);
        inputLayout.setHint("Hex bytes (" + width + " byte" + (width == 1 ? "" : "s") + ")");
        TextInputEditText input = new TextInputEditText(overlayContext);
        input.setSingleLine(true);
        input.setText(initialHex == null ? "" : initialHex.trim());
        input.setSelectAllOnFocus(true);
        inputLayout.addView(input, new TextInputLayout.LayoutParams(TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        box.addView(inputLayout, lp);
        AlertDialog dialog = new MaterialAlertDialogBuilder(overlayContext)
                .setTitle("Write " + writeWidthLabel(width))
                .setView(box)
                .setPositiveButton("Write", null)
                .setNegativeButton("Cancel", (d, w) -> {
                    try { d.dismiss(); } catch (Throwable ignored) {}
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                    if (isLiveEnabled()) scheduleLiveRefresh();
                })
                .create();
        try {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setType(Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE);
            }
        } catch (Throwable ignored) {}
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String normalized;
                try {
                    normalized = normalizeHexBytes(input.getText() == null ? "" : input.getText().toString(), width);
                } catch (IllegalArgumentException e) {
                    inputLayout.setError(e.getMessage());
                    return;
                }
                inputLayout.setError(null);
                byteWriteRequester.requestWriteBytes(formatHex(address), normalized, (success, text) -> {
                    reportStatus(text == null || text.trim().isEmpty() ? (success ? "Hex write finished." : "Hex write failed.") : text.trim());
                    if (success) {
                        updateCachedBytes(address, normalized);
                        renderCachedHex();
                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                    }
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                    if (isLiveEnabled()) scheduleLiveRefresh();
                });
            });
        });
        dialog.setOnCancelListener(d -> {
            MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
            if (isLiveEnabled()) scheduleLiveRefresh();
        });
        try { dialog.show(); } catch (Throwable t) { reportStatus("Write dialog failed: " + t.getMessage()); }
    }


    private void showAsciiWriteDialog(long address, AsciiEditWindow edit) {
        if (byteWriteRequester == null) {
            reportStatus("ASCII write is not connected to the memory backend.");
            return;
        }
        final AsciiEditWindow asciiEdit = edit == null ? buildAsciiEditWindow(address) : edit;
        stopLiveRefresh();
        MemoryOverlayWindowSupport.setInteractive(wm, root, params, true);
        LinearLayout box = new LinearLayout(overlayContext);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);
        TextView addressView = new TextView(overlayContext);
        addressView.setText("Address: " + formatHex(address) + " · ASCII / UTF-8 bytes");
        box.addView(addressView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextInputLayout inputLayout = new TextInputLayout(overlayContext);
        inputLayout.setHint("Text to write");
        TextInputEditText input = new TextInputEditText(overlayContext);
        input.setSingleLine(true);
        input.setText(asciiEdit.initialText);
        input.setSelectAllOnFocus(true);
        inputLayout.addView(input, new TextInputLayout.LayoutParams(TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        box.addView(inputLayout, lp);
        CheckBox clearTrailing = new CheckBox(overlayContext);
        clearTrailing.setText("Clear leftover text bytes");
        clearTrailing.setTextSize(12f);
        // Leave destructive cleanup opt-in. Shorter ASCII writes still add a terminating
        // 00 byte inside the selected write span, but preserving the remaining bytes by
        // default avoids erasing adjacent data when the detected text boundary is wrong.
        clearTrailing.setChecked(false);
        clearTrailing.setEnabled(asciiEdit.clearBytes > asciiEdit.editBytes);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clearLp.topMargin = dp(6);
        box.addView(clearTrailing, clearLp);
        CheckBox allowPaddingOverride = new CheckBox(overlayContext);
        allowPaddingOverride.setText("Allow consuming null padding");
        allowPaddingOverride.setTextSize(12f);
        allowPaddingOverride.setChecked(false);
        allowPaddingOverride.setEnabled(asciiEdit.maxWriteBytes > asciiEdit.safeWriteBytes);
        LinearLayout.LayoutParams overrideLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        overrideLp.topMargin = dp(2);
        box.addView(allowPaddingOverride, overrideLp);
        CheckBox allowBlankZeroFill = new CheckBox(overlayContext);
        allowBlankZeroFill.setText("Allow blank text to write 00 bytes");
        allowBlankZeroFill.setTextSize(12f);
        allowBlankZeroFill.setChecked(false);
        LinearLayout.LayoutParams blankLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blankLp.topMargin = dp(2);
        box.addView(allowBlankZeroFill, blankLp);
        TextView note = new TextView(overlayContext);
        StringBuilder noteText = new StringBuilder();
        noteText.append("Writes up to ").append(asciiEdit.editBytes).append(" byte").append(asciiEdit.editBytes == 1 ? "" : "s");
        if (asciiEdit.clearBytes > asciiEdit.editBytes) {
            noteText.append(" and can clear the remaining ").append(asciiEdit.clearBytes - asciiEdit.editBytes).append(" byte").append(asciiEdit.clearBytes - asciiEdit.editBytes == 1 ? "" : "s").append(" of the detected text.");
        } else {
            noteText.append(". Blank input writes 00 byte").append(asciiEdit.editBytes == 1 ? "" : "s").append('.');
        }
        if (asciiEdit.safeWriteBytes > asciiEdit.clearBytes) {
            noteText.append(" Safe expansion can write up to ").append(asciiEdit.safeWriteBytes).append(" bytes while keeping two 00 bytes after the new text.");
        }
        if (asciiEdit.clearBytes > asciiEdit.editBytes) {
            noteText.append(" Clear leftover text bytes is off by default; enable it only when the trailing bytes belong to the same string and should be zeroed.");
        }
        if (asciiEdit.maxWriteBytes > asciiEdit.safeWriteBytes) {
            noteText.append(" Override can consume up to ").append(asciiEdit.maxWriteBytes).append(" bytes of contiguous 00 padding.");
        }
        noteText.append(" Blank input is blocked unless the 00-byte checkbox is enabled.");
        note.setText(noteText.toString());
        note.setTextSize(12f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(2);
        box.addView(note, noteLp);
        AlertDialog dialog = new MaterialAlertDialogBuilder(overlayContext)
                .setTitle("Write ASCII")
                .setView(box)
                .setPositiveButton("Write", null)
                .setNegativeButton("Cancel", (d, w) -> {
                    try { d.dismiss(); } catch (Throwable ignored) {}
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                    if (isLiveEnabled()) scheduleLiveRefresh();
                })
                .create();
        try {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setType(Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE);
            }
        } catch (Throwable ignored) {}
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String text = input.getText() == null ? "" : input.getText().toString();
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                if (bytes.length == 0 && !allowBlankZeroFill.isChecked()) {
                    inputLayout.setError("Blank ASCII text would write 00 bytes. Enable the 00-byte checkbox to clear bytes intentionally.");
                    return;
                }
                int baseBytes = clearTrailing.isChecked() ? asciiEdit.clearBytes : asciiEdit.editBytes;
                if (baseBytes <= 0) baseBytes = 1;
                int safeBytes = Math.max(Math.max(asciiEdit.editBytes, asciiEdit.clearBytes), asciiEdit.safeWriteBytes);
                int maxBytes = allowPaddingOverride.isChecked() ? Math.max(safeBytes, asciiEdit.maxWriteBytes) : safeBytes;
                int requestedBytes = Math.max(baseBytes, bytes.length);
                if (bytes.length > maxBytes || requestedBytes > maxBytes) {
                    boolean couldOverride = !allowPaddingOverride.isChecked() && bytes.length <= asciiEdit.maxWriteBytes && asciiEdit.maxWriteBytes > safeBytes;
                    String limitKind = couldOverride ? "safe ASCII edit" : (allowPaddingOverride.isChecked() ? "available padded ASCII edit" : "safe ASCII edit");
                    inputLayout.setError("Text is " + bytes.length + " byte" + (bytes.length == 1 ? "" : "s") + "; " + limitKind + " is " + maxBytes + " byte" + (maxBytes == 1 ? "" : "s") + (couldOverride ? ". Enable padding override to consume extra 00 padding." : "."));
                    return;
                }
                inputLayout.setError(null);
                final int targetBytes = requestedBytes;
                byte[] writeBytes = new byte[targetBytes];
                Arrays.fill(writeBytes, (byte) 0);
                System.arraycopy(bytes, 0, writeBytes, 0, bytes.length);
                String hex = bytesToHex(writeBytes);
                byteWriteRequester.requestWriteBytes(formatHex(address), hex, (success, resultText) -> {
                    reportStatus(resultText == null || resultText.trim().isEmpty() ? (success ? "ASCII write finished." : "ASCII write failed.") : resultText.trim());
                    if (success) {
                        updateCachedBytes(address, hex);
                        selectedHighlightAddress = address;
                        selectedHighlightBytes = targetBytes;
                        selectedHighlightFromAscii = true;
                        renderCachedHex();
                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                    }
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                    if (isLiveEnabled()) scheduleLiveRefresh();
                });
            });
        });
        dialog.setOnCancelListener(d -> {
            MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
            if (isLiveEnabled()) scheduleLiveRefresh();
        });
        try { dialog.show(); } catch (Throwable t) { reportStatus("ASCII dialog failed: " + t.getMessage()); }
    }

    private AsciiEditWindow buildAsciiEditWindow(long address) {
        int selectedBytes = Math.max(1, selectedWriteWidthBytes());
        int printableBytes = printableAsciiRunLengthFromCache(address, 64);
        int editBytes = printableBytes > 0 ? Math.min(printableBytes, selectedBytes) : selectedBytes;
        int clearBytes = Math.max(editBytes, printableBytes);
        int trailingNullBytes = nullRunLengthFromCache(address + clearBytes, 64);
        int safeWriteBytes = clearBytes + Math.max(0, trailingNullBytes - 2);
        int maxWriteBytes = clearBytes + trailingNullBytes;
        return new AsciiEditWindow(readAsciiFixedFromCache(address, editBytes), editBytes, clearBytes, safeWriteBytes, maxWriteBytes, trailingNullBytes);
    }

    private int printableAsciiRunLengthFromCache(long address, int maxBytes) {
        if (lastBaseAddress < 0L || lastBytes == null) return 0;
        long offsetLong = address - lastBaseAddress;
        if (offsetLong < 0L || offsetLong > Integer.MAX_VALUE) return 0;
        int offset = (int) offsetLong;
        if (offset < 0 || offset >= lastBytes.length) return 0;
        int max = Math.min(lastBytes.length, offset + Math.max(1, maxBytes));
        int count = 0;
        for (int i = offset; i < max; i++) {
            int b = lastBytes[i] & 0xff;
            if (b == 0 || b < 32 || b > 126) break;
            count++;
        }
        return count;
    }

    private int nullRunLengthFromCache(long address, int maxBytes) {
        if (lastBaseAddress < 0L || lastBytes == null) return 0;
        long offsetLong = address - lastBaseAddress;
        if (offsetLong < 0L || offsetLong > Integer.MAX_VALUE) return 0;
        int offset = (int) offsetLong;
        if (offset < 0 || offset >= lastBytes.length) return 0;
        int max = Math.min(lastBytes.length, offset + Math.max(1, maxBytes));
        int count = 0;
        for (int i = offset; i < max; i++) {
            if ((lastBytes[i] & 0xff) != 0) break;
            count++;
        }
        return count;
    }

    private String readAsciiFixedFromCache(long address, int byteCount) {
        if (lastBaseAddress < 0L || lastBytes == null || byteCount <= 0) return "";
        long offsetLong = address - lastBaseAddress;
        if (offsetLong < 0L || offsetLong > Integer.MAX_VALUE) return "";
        int offset = (int) offsetLong;
        if (offset < 0 || offset >= lastBytes.length) return "";
        int max = Math.min(lastBytes.length, offset + byteCount);
        StringBuilder sb = new StringBuilder(byteCount);
        for (int i = offset; i < max; i++) {
            int b = lastBytes[i] & 0xff;
            if (b == 0 || b < 32 || b > 126) break;
            sb.append((char) b);
        }
        return sb.toString();
    }

    private static final class AsciiEditWindow {
        final String initialText;
        final int editBytes;
        final int clearBytes;
        final int safeWriteBytes;
        final int maxWriteBytes;
        final int trailingNullBytes;

        AsciiEditWindow(String initialText, int editBytes, int clearBytes, int safeWriteBytes, int maxWriteBytes, int trailingNullBytes) {
            this.initialText = initialText == null ? "" : initialText;
            this.editBytes = Math.max(1, editBytes);
            this.clearBytes = Math.max(this.editBytes, clearBytes);
            this.safeWriteBytes = Math.max(this.clearBytes, safeWriteBytes);
            this.maxWriteBytes = Math.max(this.safeWriteBytes, maxWriteBytes);
            this.trailingNullBytes = Math.max(0, trailingNullBytes);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b & 0xff));
        return sb.toString();
    }

    private static String sectionMarkerAsciiPreview(String hex) {
        if (TextUtils.isEmpty(hex)) return "";
        try {
            String clean = normalizeAnyHexBytes(hex);
            byte[] bytes = new byte[clean.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            }
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            for (int i = 0; i < decoded.length(); i++) {
                if (Character.isISOControl(decoded.charAt(i))) return "";
            }
            return decoded;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String sectionMarkerHexPreview(String hex) {
        if (TextUtils.isEmpty(hex)) return "";
        try {
            return normalizeAnyHexBytes(hex);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String sectionMarkerAsciiToHexInput(String ascii) {
        String value = ascii == null ? "" : ascii.trim();
        if (TextUtils.isEmpty(value)) return "";
        return bytesToHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private void installSectionMarkerSync(TextInputEditText asciiInput, TextInputEditText hexInput) {
        if (asciiInput == null || hexInput == null) return;
        final boolean[] updating = { false };
        asciiInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                if (updating[0]) return;
                updating[0] = true;
                try {
                    hexInput.setText(sectionMarkerAsciiToHexInput(editable == null ? "" : editable.toString()));
                } catch (Throwable ignored) {
                } finally {
                    updating[0] = false;
                }
            }
        });
        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                if (updating[0]) return;
                updating[0] = true;
                try {
                    String raw = editable == null ? "" : editable.toString();
                    asciiInput.setText(TextUtils.isEmpty(raw.trim()) ? "" : sectionMarkerAsciiPreview(raw));
                } catch (Throwable ignored) {
                } finally {
                    updating[0] = false;
                }
            }
        });
    }

    private void updateCachedBytes(long address, String normalizedHex) {
        if (lastBaseAddress < 0L || lastBytes == null || normalizedHex == null) return;
        int offset = (int) (address - lastBaseAddress);
        if (offset < 0 || offset >= lastBytes.length) return;
        for (int i = 0; i + 1 < normalizedHex.length(); i += 2) {
            int target = offset + (i / 2);
            if (target >= lastBytes.length) break;
            try { lastBytes[target] = (byte) Integer.parseInt(normalizedHex.substring(i, i + 2), 16); } catch (Throwable ignored) { break; }
        }
    }

    private static String normalizeHexBytes(String raw, int width) {
        String value = raw == null ? "" : raw.trim();
        value = value.replace("0x", "").replace("0X", "").replaceAll("[^0-9a-fA-F]", "");
        if (value.isEmpty()) throw new IllegalArgumentException("Enter hex bytes.");
        if ((value.length() & 1) != 0) throw new IllegalArgumentException("Hex byte text must have an even digit count.");
        int byteCount = value.length() / 2;
        if (byteCount != width) {
            throw new IllegalArgumentException("Enter exactly " + width + " byte" + (width == 1 ? "" : "s") + ".");
        }
        return value.toUpperCase(Locale.US);
    }

    private boolean isLiveEnabled() {
        if (root == null || root.getVisibility() != View.VISIBLE) return false;
        CheckBox live = root.findViewById(R.id.chkOverlayMemoryHexLive);
        return live != null && live.isChecked();
    }

    private void scheduleLiveRefresh() {
        stopLiveRefresh();
        if (isLiveEnabled()) handler.postDelayed(liveRefreshRunnable, LIVE_REFRESH_MS);
    }

    private void stopLiveRefresh() {
        handler.removeCallbacks(liveRefreshRunnable);
    }

    /**
     * Binds the alignment dropdown as a real dropdown, not a text field.  Several
     * keyboard-suppression calls are intentionally layered because OEM overlay
     * windows do not all honor the same input flags.
     */
    private void bindAlignmentControls() {
        MaterialAutoCompleteTextView dropdown = root == null ? null : root.findViewById(R.id.ddOverlayMemoryHexAlignMode);
        if (dropdown != null) {
            dropdown.setAdapter(new ArrayAdapter<>(overlayContext, R.layout.dropdown_memory_overlay_item, ALIGNMENT_LABELS));
            dropdown.setText(ALIGNMENT_LABELS[0], false);
            DropdownUi.prepareExposedDropdown(root == null ? null : root.findViewById(R.id.tilOverlayMemoryHexAlignMode), dropdown);
            try { dropdown.setRawInputType(android.text.InputType.TYPE_NULL); } catch (Throwable ignored) {}
            try { dropdown.setKeyListener(null); } catch (Throwable ignored) {}
            try { dropdown.setFocusable(false); } catch (Throwable ignored) {}
            try { dropdown.setFocusableInTouchMode(false); } catch (Throwable ignored) {}
            dropdown.setOnTouchListener((v, event) -> {
                if (event != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, true);
                    hideSoftInput(dropdown);
                    try { DropdownUi.showDropdown(dropdown); } catch (Throwable ignored) {}
                    return true;
                }
                return false;
            });
            dropdown.setOnClickListener(v -> {
                MemoryOverlayWindowSupport.setInteractive(wm, root, params, true);
                hideSoftInput(dropdown);
                try { DropdownUi.showDropdown(dropdown); } catch (Throwable ignored) {}
            });
            dropdown.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, true);
                    hideSoftInput(dropdown);
                    try { DropdownUi.showDropdown(dropdown); } catch (Throwable ignored) {}
                }
            });
            dropdown.setOnItemClickListener((parent, view, position, id) -> {
                try { dropdown.dismissDropDown(); } catch (Throwable ignored) {}
                try { dropdown.clearFocus(); } catch (Throwable ignored) {}
                MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                readHexRange(true);
            });
        }
        CheckBox align = root == null ? null : root.findViewById(R.id.chkOverlayMemoryHexAlign);
        if (align != null) {
            align.setChecked(true);
            align.setOnCheckedChangeListener((buttonView, isChecked) -> readHexRange(true));
        }
    }

    private void hideSoftInput(View view) {
        if (view == null || overlayContext == null) return;
        try {
            InputMethodManager imm = (InputMethodManager) overlayContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Throwable ignored) {}
    }

    private void bindWriteWidthCheckboxes() {
        int[] ids = new int[]{
                R.id.chkOverlayMemoryHexWriteByte,
                R.id.chkOverlayMemoryHexWriteWord,
                R.id.chkOverlayMemoryHexWriteDword,
                R.id.chkOverlayMemoryHexWriteQword
        };
        for (int id : ids) {
            CheckBox cb = root.findViewById(id);
            if (cb == null) continue;
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) {
                    if (!isAnyWriteWidthChecked()) setWriteWidthChecked(DEFAULT_WRITE_WIDTH_CHECKBOX_ID);
                    return;
                }
                setWriteWidthChecked(buttonView.getId());
                renderCachedHex();
            });
        }
    }

    private boolean isAnyWriteWidthChecked() {
        return isChecked(R.id.chkOverlayMemoryHexWriteByte)
                || isChecked(R.id.chkOverlayMemoryHexWriteWord)
                || isChecked(R.id.chkOverlayMemoryHexWriteDword)
                || isChecked(R.id.chkOverlayMemoryHexWriteQword);
    }

    private boolean isChecked(int id) {
        CheckBox cb = root == null ? null : root.findViewById(id);
        return cb != null && cb.isChecked();
    }

    private void setWriteWidthChecked(int checkedId) {
        int[] ids = new int[]{
                R.id.chkOverlayMemoryHexWriteByte,
                R.id.chkOverlayMemoryHexWriteWord,
                R.id.chkOverlayMemoryHexWriteDword,
                R.id.chkOverlayMemoryHexWriteQword
        };
        for (int id : ids) {
            CheckBox cb = root == null ? null : root.findViewById(id);
            if (cb == null) continue;
            cb.setOnCheckedChangeListener(null);
            cb.setChecked(id == checkedId);
        }
        bindWriteWidthCheckboxes();
        if (selectedByteAddress >= 0L) {
            int width = selectedWriteWidthBytes();
            selectedHighlightAddress = selectedWriteAddress(width);
            selectedHighlightBytes = width;
        }
    }

    private int selectedWriteWidthBytes() {
        if (isChecked(R.id.chkOverlayMemoryHexWriteQword)) return 8;
        if (isChecked(R.id.chkOverlayMemoryHexWriteDword)) return 4;
        if (isChecked(R.id.chkOverlayMemoryHexWriteWord)) return 2;
        return 1;
    }

    private long selectedWriteAddress(int width) {
        if (width <= 1) return selectedByteAddress;
        return selectedByteAddress & ~((long) width - 1L);
    }

    private String readHexBytesFromCache(long address, int width) {
        if (lastBaseAddress < 0L || lastBytes == null) return "";
        long offsetLong = address - lastBaseAddress;
        if (offsetLong < 0L || offsetLong > Integer.MAX_VALUE) return "";
        int offset = (int) offsetLong;
        if (offset < 0 || offset + width > lastBytes.length) return "";
        StringBuilder sb = new StringBuilder(width * 2);
        for (int i = 0; i < width; i++) {
            sb.append(String.format(Locale.US, "%02X", lastBytes[offset + i] & 0xff));
        }
        return sb.toString();
    }

    private static String writeWidthLabel(int width) {
        switch (width) {
            case 2: return "word";
            case 4: return "dword";
            case 8: return "qword";
            default: return "byte";
        }
    }

    private static byte[] parseHexDumpBytes(String dump) {
        ArrayList<Byte> out = new ArrayList<>();
        if (dump == null) return new byte[0];
        String[] lines = dump.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) continue;
            String t = line.trim();
            if (TextUtils.isEmpty(t)) continue;
            if (t.startsWith("Attached TID") || t.startsWith("Detached TID") || t.startsWith("Target PID")) continue;
            int ascii = t.indexOf('|');
            String hexPart = ascii >= 0 ? t.substring(0, ascii) : t;
            hexPart = hexPart.replace(':', ' ');
            String[] parts = hexPart.trim().split("\\s+");
            if (parts.length < 2) continue;
            if (!isAddressToken(parts[0])) continue;
            for (int i = 1; i < parts.length; i++) {
                String p = parts[i].trim();
                if (!p.matches("[0-9a-fA-F]{2}")) continue;
                try {
                    out.add((byte) Integer.parseInt(p, 16));
                } catch (Throwable ignored) {
                }
            }
        }
        byte[] bytes = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) bytes[i] = out.get(i);
        return bytes;
    }

    private static boolean isAddressToken(String token) {
        if (token == null) return false;
        String t = token.trim();
        if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        return t.matches("[0-9a-fA-F]{6,16}");
    }

    private CharSequence buildHexEditorView(long baseAddress, byte[] bytes) {
        hitRegions.clear();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendSelectionSummary(sb);
        int count = bytes == null ? 0 : bytes.length;
        int addressWidth = Math.max("Address".length(), formatHex(baseAddress + Math.max(0, count - 1)).length());
        sb.append(padRight("Address", addressWidth))
                .append("  00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F  ASCII\n");
        sb.append(repeat('-', addressWidth))
                .append("  ------------------------------------------------  ----------------\n");
        for (int row = 0; row < count; row += 16) {
            long rowAddress = baseAddress + row;
            sb.append(padRight(formatHex(rowAddress), addressWidth)).append("  ");
            int asciiStart = row;
            for (int i = 0; i < 16; i++) {
                int index = row + i;
                if (index < count) {
                    int b = bytes[index] & 0xff;
                    long byteAddress = baseAddress + index;
                    int tokenStart = sb.length();
                    sb.append(String.format(Locale.US, "%02X", b));
                    int tokenEnd = sb.length();
                    hitRegions.add(new HitRegion(tokenStart, tokenEnd, byteAddress, false));
                    if (isInSelectedWriteRange(byteAddress)) {
                        sb.setSpan(new BackgroundColorSpan(0x553F8CFF), tokenStart, tokenEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    sb.append(' ');
                } else {
                    sb.append("   ");
                }
            }
            sb.append(' ');
            for (int i = 0; i < 16; i++) {
                int index = asciiStart + i;
                if (index < count) {
                    int b = bytes[index] & 0xff;
                    long byteAddress = baseAddress + index;
                    int tokenStart = sb.length();
                    sb.append(b >= 32 && b <= 126 ? (char) b : '.');
                    int tokenEnd = sb.length();
                    hitRegions.add(new HitRegion(tokenStart, tokenEnd, byteAddress, true));
                    if (isInSelectedWriteRange(byteAddress)) {
                        sb.setSpan(new BackgroundColorSpan(0x553F8CFF), tokenStart, tokenEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                } else {
                    sb.append(' ');
                }
            }
            sb.append('\n');
        }
        return sb;
    }

    private static String padRight(String value, int width) {
        String s = value == null ? "" : value;
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(width);
        sb.append(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(Math.max(0, count));
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }

    private void appendSelectionSummary(SpannableStringBuilder sb) {
        if (selectedByteAddress < 0L || selectedDwordAddress < 0L) return;
        sb.append("Selected byte ")
                .append(formatHex(selectedByteAddress))
                .append("; dword ")
                .append(formatHex(selectedDwordAddress));
        if (selectedDwordValue >= 0L) {
            sb.append(" = ")
                    .append(Long.toUnsignedString(selectedDwordValue))
                    .append(" (0x")
                    .append(String.format(Locale.US, "%08x", selectedDwordValue & 0xffffffffL))
                    .append(')');
        }
        sb.append(". Edit Patch value and use Patch on the main overlay.\n\n");
    }

    private boolean isInSelectedWriteRange(long address) {
        if (selectedByteAddress < 0L) return false;
        long start = selectedHighlightAddress >= 0L ? selectedHighlightAddress : selectedWriteAddress(selectedWriteWidthBytes());
        int width = selectedHighlightBytes > 0 ? selectedHighlightBytes : selectedWriteWidthBytes();
        return address >= start && address < start + width;
    }

    private void selectHexByte(long byteAddress) {
        selectedByteAddress = byteAddress;
        selectedDwordAddress = byteAddress & ~3L;
        selectedDwordValue = readLittleEndianUnsigned(selectedDwordAddress, 4);
        int width = selectedWriteWidthBytes();
        selectedHighlightAddress = selectedWriteAddress(width);
        selectedHighlightBytes = width;
        selectedHighlightFromAscii = false;
        if (selectionListener != null) {
            selectionListener.onHexSelection(byteAddress, selectedDwordAddress, selectedDwordValue);
        }
        renderCachedHex();
    }

    private void updateSelectionForRange(long requestedAddress, long baseAddress, int byteCount) {
        long endAddress = baseAddress + Math.max(0, byteCount);
        if (selectedHighlightAddress >= baseAddress
                && selectedHighlightBytes > 0
                && selectedHighlightAddress + selectedHighlightBytes <= endAddress) {
            // Preserve the exact tap/slide/drag highlight across live refreshes.  Re-aligning
            // here makes the Slide controls appear to jump or move the wrong direction.
            if (selectedByteAddress < selectedHighlightAddress
                    || selectedByteAddress >= selectedHighlightAddress + selectedHighlightBytes) {
                selectedByteAddress = selectedHighlightAddress;
            }
            selectedDwordAddress = selectedByteAddress & ~3L;
            selectedDwordValue = readLittleEndianUnsigned(selectedDwordAddress, 4);
            return;
        }
        if (selectedByteAddress < baseAddress || selectedByteAddress >= endAddress) {
            selectedByteAddress = requestedAddress >= baseAddress && requestedAddress < endAddress ? requestedAddress : -1L;
        }
        if (selectedByteAddress < 0L) {
            clearSelection();
            return;
        }
        selectedDwordAddress = selectedByteAddress & ~3L;
        selectedDwordValue = readLittleEndianUnsigned(selectedDwordAddress, 4);
        int width = selectedWriteWidthBytes();
        selectedHighlightAddress = selectedWriteAddress(width);
        selectedHighlightBytes = width;
        selectedHighlightFromAscii = false;
    }

    private void clearSelection() {
        selectedByteAddress = -1L;
        selectedDwordAddress = -1L;
        selectedDwordValue = -1L;
        selectedHighlightAddress = -1L;
        selectedHighlightBytes = 0;
        selectedHighlightFromAscii = false;
    }

    private void renderCachedHex() {
        if (lastBaseAddress < 0L || lastBytes == null || lastBytes.length == 0) return;
        lastRenderedOutput = null;
        setOutput(outputView(), buildHexEditorView(lastBaseAddress, lastBytes));
    }

    private long readLittleEndianUnsigned(long address, int width) {
        if (lastBaseAddress < 0L || lastBytes == null) return -1L;
        long offsetLong = address - lastBaseAddress;
        if (offsetLong < 0L || offsetLong > Integer.MAX_VALUE) return -1L;
        int offset = (int) offsetLong;
        if (offset + width > lastBytes.length) return -1L;
        long value = 0L;
        for (int i = 0; i < width; i++) {
            value |= (long) (lastBytes[offset + i] & 0xff) << (8 * i);
        }
        return value;
    }

    private boolean handleHexOutputTouch(View view, MotionEvent event) {
        if (!(view instanceof TextView) || event == null) return false;
        if (isPayloadModeEnabled()) {
            return handlePayloadSelectionTouch((TextView) view, event);
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            hexTouchDownX = event.getX();
            hexTouchDownY = event.getY();
            hexTouchMoved = false;
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = Math.abs(event.getX() - hexTouchDownX);
            float dy = Math.abs(event.getY() - hexTouchDownY);
            int slop = Math.max(4, dp(8));
            if (dx > slop || dy > slop) hexTouchMoved = true;
            return false;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            hexTouchMoved = false;
            return false;
        }
        if (action != MotionEvent.ACTION_UP || hexTouchMoved) {
            hexTouchMoved = false;
            return false;
        }
        TextView textView = (TextView) view;
        HitRegion hit = findHitRegionForTouch(textView, event);
        hexTouchMoved = false;
        if (hit == null) {
            return false;
        }
        if (hit.ascii) {
            handleAsciiClick(hit.address);
        } else {
            selectHexByte(hit.address);
            showWriteDialogForSelection();
        }
        return true;
    }

    private HitRegion findHitRegionForTouch(TextView textView, MotionEvent event) {
        if (hitRegions.isEmpty() || textView.getLayout() == null) return null;
        int x = Math.round(event.getX()) + textView.getScrollX() - textView.getTotalPaddingLeft();
        int y = Math.round(event.getY()) + textView.getScrollY() - textView.getTotalPaddingTop();
        if (x < 0 || y < 0) return null;
        int line = textView.getLayout().getLineForVertical(y);
        int offset = textView.getLayout().getOffsetForHorizontal(line, x);
        HitRegion exact = findHitRegionForOffset(offset);
        if (exact != null) return exact;

        // The ASCII column is narrow and controller/touch input can land on the neighboring space.
        // Search a tiny character radius on the same rendered line instead of relying only on the
        // framework's nearest-character span hit.
        int lineStart = textView.getLayout().getLineStart(line);
        int lineEnd = textView.getLayout().getLineEnd(line);
        for (int radius = 1; radius <= 2; radius++) {
            HitRegion left = offset - radius >= lineStart ? findHitRegionForOffset(offset - radius) : null;
            if (left != null) return left;
            HitRegion right = offset + radius < lineEnd ? findHitRegionForOffset(offset + radius) : null;
            if (right != null) return right;
        }
        return null;
    }

    private HitRegion findHitRegionForOffset(int offset) {
        for (HitRegion region : hitRegions) {
            if (offset >= region.start && offset < region.end) {
                return region;
            }
        }
        return null;
    }

    private void handleAsciiClick(long address) {
        AsciiEditWindow edit = buildAsciiEditWindow(address);
        selectAsciiEditRange(address, edit.editBytes);
        showAsciiWriteDialog(address, edit);
    }

    private void selectAsciiEditRange(long address, int editBytes) {
        selectedByteAddress = address;
        selectedDwordAddress = address & ~3L;
        selectedDwordValue = readLittleEndianUnsigned(selectedDwordAddress, 4);
        selectedHighlightAddress = address;
        selectedHighlightBytes = Math.max(1, editBytes);
        selectedHighlightFromAscii = true;
        if (selectionListener != null) {
            selectionListener.onHexSelection(address, selectedDwordAddress, selectedDwordValue);
        }
        renderCachedHex();
    }

    /** Shows payload controls only while Drag Select is active. */
    private void updatePayloadModeUi() {
        if (root == null) return;
        int visibility = isPayloadModeEnabled() ? View.VISIBLE : View.GONE;
        int[] payloadControls = new int[]{
                R.id.btnOverlayMemoryHexPayloadFind,
                R.id.btnOverlayMemoryHexPayloadSave,
                R.id.btnOverlayMemoryHexPayloadMask,
                R.id.btnOverlayMemoryHexPayloadLoad,
                R.id.btnOverlayMemoryHexPayloadWrite,
                R.id.btnOverlayMemoryHexPayloadMarkStart,
                R.id.btnOverlayMemoryHexPayloadMarkEnd,
                R.id.btnOverlayMemoryHexPayloadStartMinus,
                R.id.btnOverlayMemoryHexPayloadStartPlus,
                R.id.btnOverlayMemoryHexPayloadEndMinus,
                R.id.btnOverlayMemoryHexPayloadEndPlus
        };
        for (int id : payloadControls) {
            View control = root.findViewById(id);
            if (control != null) control.setVisibility(visibility);
        }
        View sectionRow = root.findViewById(R.id.rowOverlayMemoryHexPayloadSection);
        if (sectionRow != null) sectionRow.setVisibility(visibility);
        View applyRow = root.findViewById(R.id.rowOverlayMemoryHexPayloadApply);
        if (applyRow != null) applyRow.setVisibility(visibility);
    }

    private boolean isPayloadModeEnabled() {
        CheckBox cb = root == null ? null : root.findViewById(R.id.chkOverlayMemoryHexPayloads);
        return cb != null && cb.isChecked();
    }

    /**
     * Converts drag gestures over the rendered hex/ASCII text into byte-address
     * ranges.  Rendering supplies hit regions, so the gesture works for either
     * the hex byte column or the ASCII preview column.
     */
    private boolean handlePayloadSelectionTouch(TextView textView, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            HitRegion hit = findHitRegionForTouch(textView, event);
            if (hit == null) return false;
            payloadTouchStartAddress = hit.address;
            payloadTouchFromAscii = hit.ascii;
            updatePayloadSelection(hit.address, hit.address, true, payloadTouchFromAscii);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (payloadTouchStartAddress < 0L) return false;
            HitRegion hit = findHitRegionForTouch(textView, event);
            if (hit != null) updatePayloadSelection(payloadTouchStartAddress, hit.address, true, payloadTouchFromAscii);
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (payloadTouchStartAddress < 0L) return false;
            HitRegion hit = findHitRegionForTouch(textView, event);
            if (hit != null) updatePayloadSelection(payloadTouchStartAddress, hit.address, true, payloadTouchFromAscii);
            payloadTouchStartAddress = -1L;
            payloadTouchFromAscii = false;
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            payloadTouchStartAddress = -1L;
            payloadTouchFromAscii = false;
            return true;
        }
        return false;
    }

    /** Updates the current selected byte range and optionally reports it. */
    private void updatePayloadSelection(long startAddress, long endAddress, boolean notify) {
        updatePayloadSelection(startAddress, endAddress, notify, false);
    }

    private void updatePayloadSelection(long startAddress, long endAddress, boolean notify, boolean fromAscii) {
        long start = Math.min(startAddress, endAddress);
        long end = Math.max(startAddress, endAddress);
        long count = end - start + 1L;
        if (count > MAX_HEX_READ) count = MAX_HEX_READ;
        selectedByteAddress = start;
        selectedDwordAddress = start & ~3L;
        selectedDwordValue = readLittleEndianUnsigned(selectedDwordAddress, 4);
        selectedHighlightAddress = start;
        selectedHighlightBytes = (int) Math.max(1L, count);
        selectedHighlightFromAscii = fromAscii;
        if (notify && selectionListener != null) {
            selectionListener.onHexSelection(start, selectedDwordAddress, selectedDwordValue);
        }
        renderCachedHex();
    }

    /** Opens the editor for the current tap-mode highlight without requiring another precise tap. */
    private void editCurrentSelection() {
        if (lastBaseAddress < 0L || lastBytes == null || lastBytes.length == 0) {
            reportStatus("Read memory before editing the current selection.");
            return;
        }
        long address = selectedHighlightAddress >= 0L ? selectedHighlightAddress : selectedByteAddress;
        if (address < lastBaseAddress || address >= lastBaseAddress + lastBytes.length) {
            reportStatus("Tap a byte or ASCII character before using Edit.");
            return;
        }
        if (selectedHighlightFromAscii) {
            AsciiEditWindow edit = buildAsciiEditWindow(address);
            selectAsciiEditRange(address, edit.editBytes);
            showAsciiWriteDialog(address, edit);
        } else {
            if (selectedHighlightAddress < 0L || selectedHighlightBytes <= 0) {
                selectHexByte(address);
            }
            showWriteDialogForSelection();
        }
    }

    /** Moves the current tap-mode highlight left/right without changing its size. */
    private void slideCurrentSelection(int delta) {
        if (lastBaseAddress < 0L || lastBytes == null || lastBytes.length == 0) {
            reportStatus("Read memory before sliding the current selection.");
            return;
        }
        if (selectedHighlightAddress < 0L || selectedHighlightBytes <= 0) {
            reportStatus("Tap a byte or ASCII character before sliding the selection.");
            return;
        }
        long cursor = selectedByteAddress >= 0L ? selectedByteAddress : selectedHighlightAddress;
        long start = cursor + delta;
        long end = start + selectedHighlightBytes - 1L;
        long min = lastBaseAddress;
        long max = lastBaseAddress + lastBytes.length - 1L;
        if (start < min || end > max) {
            reportStatus("Selection slide is at the visible range boundary.");
            return;
        }
        selectedByteAddress = start;
        selectedDwordAddress = start & ~3L;
        selectedDwordValue = readLittleEndianUnsigned(selectedDwordAddress, 4);
        selectedHighlightAddress = start;
        if (selectionListener != null) {
            selectionListener.onHexSelection(start, selectedDwordAddress, selectedDwordValue);
        }
        reportStatus("Selection moved to " + formatHex(start) + " - " + formatHex(end)
                + " (" + selectedHighlightBytes + " byte" + (selectedHighlightBytes == 1 ? "" : "s") + ").");
        renderCachedHex();
    }

    /** Marks the current Drag Select cursor as the start of a long payload range. */
    private void markPayloadSelectionStart() {
        if (!ensurePayloadSelectionAdjustable()) return;
        payloadSelectionMarkedStartAddress = selectedByteAddress >= 0L ? selectedByteAddress : selectedHighlightAddress;
        reportStatus("Payload selection start marked at " + formatHex(payloadSelectionMarkedStartAddress)
                + ". Scroll/tap the last byte, then press Set E.");
    }

    /**
     * Completes a long payload range from the previously marked start to the
     * current Drag Select cursor.  This avoids trying to auto-scroll while a
     * drag gesture is active inside the hex text view.
     */
    private void markPayloadSelectionEnd() {
        if (!ensurePayloadSelectionAdjustable()) return;
        if (payloadSelectionMarkedStartAddress < 0L) {
            reportStatus("Tap the first byte and press Set S before setting the payload end.");
            return;
        }
        long end = selectedByteAddress >= 0L ? selectedByteAddress : selectedHighlightAddress;
        applyPayloadSelectionBounds(payloadSelectionMarkedStartAddress, end, "range");
    }

    /** Records the selected byte range as the optional section-start marker. */
    private void markPayloadSectionStart() {
        markPayloadSectionMarker(true);
    }

    /** Records the selected byte range as the optional section-end marker. */
    private void markPayloadSectionEnd() {
        markPayloadSectionMarker(false);
    }

    private void markPayloadSectionMarker(boolean startMarker) {
        if (!ensurePayloadSelectionAdjustable()) return;
        String hex;
        try {
            hex = normalizeAnyHexBytes(selectedPayloadHex());
        } catch (IllegalArgumentException e) {
            reportStatus("Select the section marker bytes with Drag Select first.");
            return;
        }
        String ascii = selectedHighlightFromAscii ? asciiTextForPayloadSelection(hex) : "";
        boolean useAscii = selectedHighlightFromAscii && !TextUtils.isEmpty(ascii);
        if (selectedHighlightFromAscii && TextUtils.isEmpty(ascii)) {
            reportStatus("Selected ASCII marker contains non-printable bytes; staged as hex instead.");
        }
        if (startMarker) {
            pendingPayloadSectionStartAscii = useAscii ? ascii : "";
            pendingPayloadSectionStartHex = useAscii ? "" : hex;
        } else {
            pendingPayloadSectionEndAscii = useAscii ? ascii : "";
            pendingPayloadSectionEndHex = useAscii ? "" : hex;
        }
        String label = startMarker ? "Section start" : "Section end";
        reportStatus(label + " marker staged from " + (useAscii ? "ASCII" : "hex")
                + " selection (" + formatPayloadLength(byteCountForHex(hex)) + "). Save payload to write it into JSON.");
    }

    private String asciiTextForPayloadSelection(String hex) {
        if (TextUtils.isEmpty(hex)) return "";
        try {
            String clean = normalizeAnyHexBytes(hex);
            byte[] bytes = new byte[clean.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                int b = Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
                if (b < 32 || b > 126) return "";
                bytes[i] = (byte) b;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Moves the selected start edge by one byte at a time for precise trimming. */
    private void adjustPayloadSelectionStart(int delta) {
        if (!ensurePayloadSelectionAdjustable()) return;
        long start = selectedHighlightAddress + delta;
        long end = selectedHighlightAddress + selectedHighlightBytes - 1L;
        applyPayloadSelectionBounds(start, end, "start");
    }

    /** Moves the selected end edge by one byte at a time for precise trimming. */
    private void adjustPayloadSelectionEnd(int delta) {
        if (!ensurePayloadSelectionAdjustable()) return;
        long start = selectedHighlightAddress;
        long end = selectedHighlightAddress + selectedHighlightBytes - 1L + delta;
        applyPayloadSelectionBounds(start, end, "end");
    }

    private boolean ensurePayloadSelectionAdjustable() {
        if (!isPayloadModeEnabled()) {
            reportStatus("Enable Drag Select before adjusting a payload selection.");
            return false;
        }
        if (!hasPayloadSelection()) {
            reportStatus("Select bytes with Drag Select before adjusting the selection.");
            return false;
        }
        if (lastBaseAddress < 0L || lastBytes == null || lastBytes.length == 0) {
            reportStatus("Read memory before adjusting a payload selection.");
            return false;
        }
        return true;
    }

    private void applyPayloadSelectionBounds(long requestedStart, long requestedEnd, String edgeLabel) {
        long min = lastBaseAddress;
        long max = lastBaseAddress + lastBytes.length - 1L;
        if (requestedStart < min || requestedEnd > max) {
            reportStatus("Payload selection " + edgeLabel + " is at the visible range boundary.");
            return;
        }
        if (requestedStart > requestedEnd) {
            reportStatus("Payload selection must keep at least one byte selected.");
            return;
        }
        long count = requestedEnd - requestedStart + 1L;
        if (count > MAX_HEX_READ) {
            reportStatus("Payload selection is limited to " + MAX_HEX_READ + " bytes.");
            return;
        }
        updatePayloadSelection(requestedStart, requestedEnd, true, selectedHighlightFromAscii);
        reportStatus("Payload selection " + edgeLabel + " adjusted: "
                + formatHex(selectedHighlightAddress)
                + " - "
                + formatHex(selectedHighlightAddress + selectedHighlightBytes - 1L)
                + " ("
                + selectedHighlightBytes
                + " bytes).");
    }

    /**
     * Finds payload bytes in process memory.  Loaded payload original_hex wins;
     * the current selection is used only when no payload file is loaded.
     */
    private void searchSelectedOrLoadedPayload() {
        String source = "selected bytes";
        // Applying by payload means search for original bytes, not patched bytes.
        String rawHex = loadedPayloadOriginalHex;
        String rawMask = null;
        if (TextUtils.isEmpty(rawHex)) {
            rawHex = selectedPayloadHex();
        } else {
            rawMask = loadedPayloadMaskHex;
            source = TextUtils.isEmpty(loadedPayloadName) ? "loaded payload original bytes" : ("original bytes from payload \"" + loadedPayloadName + "\"");
        }

        String hex;
        String mask;
        try {
            hex = normalizeAnyHexBytes(rawHex);
            mask = normalizedPayloadMaskOrNull(rawMask, byteCountForHex(hex));
        } catch (Throwable e) {
            reportStatus("Select bytes or load a payload before searching: " + e.getMessage());
            return;
        }
        if (payloadSearchRequester == null) {
            reportStatus("Hex payload search is not connected to the memory backend.");
            return;
        }
        stopLiveRefresh();
        int masked = MemoryHexPayloadStore.countWildcardBytes(mask);
        reportStatus("Searching " + source + " (" + byteCountForHex(hex) + " bytes"
                + (masked > 0 ? ", mask " + masked : "") + ")...");
        payloadSearchRequester.requestSearchHexPayload(hex, mask, (success, message) -> {
            reportStatus(TextUtils.isEmpty(message) ? (success ? "Hex payload search finished." : "Hex payload search failed.") : message);
            if (success && isLiveEnabled()) scheduleLiveRefresh();
        });
    }

    /** Starts the save flow for the currently highlighted byte range. */
    private void saveSelectedPayload() {
        String hex;
        try {
            hex = normalizeAnyHexBytes(selectedPayloadHex());
        } catch (IllegalArgumentException e) {
            reportStatus("Select one or more bytes with Drag Select before saving.");
            return;
        }
        showSavePayloadDialog(hex);
    }

    /**
     * Lets the user name the payload and choose whether the selected bytes are
     * original_hex or patched_hex for the package-scoped JSON file.
     */
    private void showSavePayloadDialog(String hex) {
        final String payloadHex;
        try {
            payloadHex = normalizeAnyHexBytes(hex);
        } catch (IllegalArgumentException e) {
            reportStatus("Select one or more bytes with Drag Select before saving.");
            return;
        }
        final String packageName = currentPayloadPackageName();
        if (TextUtils.isEmpty(packageName)) {
            reportStatus("Select a target package before saving a payload.");
            return;
        }
        stopLiveRefresh();
        MemoryOverlayWindowSupport.setInteractive(wm, root, params, true);
        LinearLayout box = new LinearLayout(overlayContext);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);
        TextView summary = new TextView(overlayContext);
        summary.setText("Package: " + packageName
                + "\nPayload size: " + byteCountForHex(payloadHex) + " byte" + (byteCountForHex(payloadHex) == 1 ? "" : "s")
                + "\nAddress: " + (selectedHighlightAddress >= 0L ? formatHex(selectedHighlightAddress) : ""));
        summary.setTextSize(12f);
        box.addView(summary, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextInputLayout inputLayout = new TextInputLayout(overlayContext);
        inputLayout.setHint("Payload name");
        TextInputEditText input = new TextInputEditText(overlayContext);
        input.setSingleLine(true);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        input.setText(defaultPayloadDisplayName(payloadHex, stamp));
        input.setSelectAllOnFocus(true);
        inputLayout.addView(input, new TextInputLayout.LayoutParams(TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        box.addView(inputLayout, lp);

        // Original bytes are the bytes to find. Patched bytes are the bytes to
        // write after the original bytes are discovered in memory.
        RadioGroup roleGroup = new RadioGroup(overlayContext);
        roleGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton original = new RadioButton(overlayContext);
        original.setId(View.generateViewId());
        original.setText("Original bytes");
        RadioButton patched = new RadioButton(overlayContext);
        patched.setId(View.generateViewId());
        patched.setText("Patched bytes");
        roleGroup.addView(original, new RadioGroup.LayoutParams(RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT));
        roleGroup.addView(patched, new RadioGroup.LayoutParams(RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT));
        roleGroup.check(original.getId());
        LinearLayout.LayoutParams roleLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        roleLp.topMargin = dp(8);
        box.addView(roleGroup, roleLp);


        final MemoryHexPayloadStore.MaskPreset[] maskPresets = MemoryHexPayloadStore.maskPresets(true);
        String[] maskPresetLabels = new String[maskPresets.length];
        for (int i = 0; i < maskPresets.length; i++) maskPresetLabels[i] = maskPresets[i].title;
        final int[] selectedMaskPresetId = { MemoryHexPayloadStore.MASK_PRESET_NO_CHANGE };
        TextInputLayout maskPresetLayout = new TextInputLayout(overlayContext);
        maskPresetLayout.setHint("Mask preset");
        MaterialAutoCompleteTextView maskPresetInput = new MaterialAutoCompleteTextView(overlayContext);
        maskPresetInput.setSingleLine(true);
        maskPresetInput.setAdapter(new ArrayAdapter<>(overlayContext, android.R.layout.simple_dropdown_item_1line, maskPresetLabels));
        DropdownUi.bindExposedDropdown(overlayContext, maskPresetLayout, maskPresetInput, () -> DropdownUi.showDropdown(maskPresetInput));
        maskPresetInput.setText(maskPresetLabels.length > 0 ? maskPresetLabels[0] : "", false);
        maskPresetLayout.addView(maskPresetInput, new TextInputLayout.LayoutParams(TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams maskPresetLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        maskPresetLp.topMargin = dp(8);
        box.addView(maskPresetLayout, maskPresetLp);

        TextView maskPresetDescription = new TextView(overlayContext);
        maskPresetDescription.setText(maskPresets.length > 0 ? maskPresets[0].description : "");
        maskPresetDescription.setTextSize(12f);
        maskPresetDescription.setAlpha(0.75f);
        LinearLayout.LayoutParams maskDescriptionLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        maskDescriptionLp.topMargin = dp(2);
        box.addView(maskPresetDescription, maskDescriptionLp);

        CheckBox preserveMaskInput = new CheckBox(overlayContext);
        preserveMaskInput.setText("Preserve masked bytes on write");
        preserveMaskInput.setChecked(false);
        preserveMaskInput.setEnabled(false);
        LinearLayout.LayoutParams preserveMaskLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        preserveMaskLp.topMargin = dp(2);
        box.addView(preserveMaskInput, preserveMaskLp);
        maskPresetInput.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= maskPresets.length) return;
            MemoryHexPayloadStore.MaskPreset preset = maskPresets[position];
            selectedMaskPresetId[0] = preset.id;
            maskPresetDescription.setText(preset.description);
            boolean changesMask = preset.id != MemoryHexPayloadStore.MASK_PRESET_NO_CHANGE;
            preserveMaskInput.setEnabled(changesMask);
            preserveMaskInput.setChecked(changesMask && preset.preserveMaskedBytes);
            maskPresetLayout.setError(null);
        });

        String loadedSectionStartHex = MemoryHexPayloadStore.packageNameMatches(loadedPayloadPackageName, packageName) ? loadedPayloadSectionStartHex : null;
        String loadedSectionEndHex = MemoryHexPayloadStore.packageNameMatches(loadedPayloadPackageName, packageName) ? loadedPayloadSectionEndHex : null;
        String initialSectionStartAscii = !TextUtils.isEmpty(pendingPayloadSectionStartAscii)
                ? pendingPayloadSectionStartAscii
                : (TextUtils.isEmpty(pendingPayloadSectionStartHex) ? sectionMarkerAsciiPreview(loadedSectionStartHex) : "");
        String initialSectionEndAscii = !TextUtils.isEmpty(pendingPayloadSectionEndAscii)
                ? pendingPayloadSectionEndAscii
                : (TextUtils.isEmpty(pendingPayloadSectionEndHex) ? sectionMarkerAsciiPreview(loadedSectionEndHex) : "");
        String initialSectionStartHex = !TextUtils.isEmpty(pendingPayloadSectionStartAscii)
                ? sectionMarkerAsciiToHexInput(pendingPayloadSectionStartAscii)
                : (!TextUtils.isEmpty(pendingPayloadSectionStartHex) ? pendingPayloadSectionStartHex : sectionMarkerHexPreview(loadedSectionStartHex));
        String initialSectionEndHex = !TextUtils.isEmpty(pendingPayloadSectionEndAscii)
                ? sectionMarkerAsciiToHexInput(pendingPayloadSectionEndAscii)
                : (!TextUtils.isEmpty(pendingPayloadSectionEndHex) ? pendingPayloadSectionEndHex : sectionMarkerHexPreview(loadedSectionEndHex));
        LinearLayout sectionAsciiRow = new LinearLayout(overlayContext);
        sectionAsciiRow.setOrientation(LinearLayout.HORIZONTAL);
        TextInputLayout sectionStartLayout = new TextInputLayout(overlayContext);
        sectionStartLayout.setHint("Section start ASCII");
        TextInputEditText sectionStartInput = new TextInputEditText(overlayContext);
        sectionStartInput.setSingleLine(true);
        sectionStartInput.setText(initialSectionStartAscii);
        sectionStartLayout.addView(sectionStartInput, new TextInputLayout.LayoutParams(TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        TextInputLayout sectionEndLayout = new TextInputLayout(overlayContext);
        sectionEndLayout.setHint("Section end ASCII");
        TextInputEditText sectionEndInput = new TextInputEditText(overlayContext);
        sectionEndInput.setSingleLine(true);
        sectionEndInput.setText(initialSectionEndAscii);
        sectionEndLayout.addView(sectionEndInput, new TextInputLayout.LayoutParams(TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sectionStartLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sectionStartLp.rightMargin = dp(6);
        sectionAsciiRow.addView(sectionStartLayout, sectionStartLp);
        LinearLayout.LayoutParams sectionEndLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sectionEndLp.leftMargin = dp(6);
        sectionAsciiRow.addView(sectionEndLayout, sectionEndLp);
        LinearLayout.LayoutParams sectionAsciiLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionAsciiLp.topMargin = dp(8);
        box.addView(sectionAsciiRow, sectionAsciiLp);

        LinearLayout sectionHexRow = new LinearLayout(overlayContext);
        sectionHexRow.setOrientation(LinearLayout.HORIZONTAL);
        TextInputLayout sectionStartHexLayout = new TextInputLayout(overlayContext);
        sectionStartHexLayout.setHint("Section start hex");
        TextInputEditText sectionStartHexInput = new TextInputEditText(overlayContext);
        sectionStartHexInput.setSingleLine(true);
        sectionStartHexInput.setText(initialSectionStartHex);
        sectionStartHexLayout.addView(sectionStartHexInput, new TextInputLayout.LayoutParams(TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        TextInputLayout sectionEndHexLayout = new TextInputLayout(overlayContext);
        sectionEndHexLayout.setHint("Section end hex");
        TextInputEditText sectionEndHexInput = new TextInputEditText(overlayContext);
        sectionEndHexInput.setSingleLine(true);
        sectionEndHexInput.setText(initialSectionEndHex);
        sectionEndHexLayout.addView(sectionEndHexInput, new TextInputLayout.LayoutParams(TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sectionStartHexLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sectionStartHexLp.rightMargin = dp(6);
        sectionHexRow.addView(sectionStartHexLayout, sectionStartHexLp);
        LinearLayout.LayoutParams sectionEndHexLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sectionEndHexLp.leftMargin = dp(6);
        sectionHexRow.addView(sectionEndHexLayout, sectionEndHexLp);
        LinearLayout.LayoutParams sectionHexLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionHexLp.topMargin = dp(4);
        box.addView(sectionHexRow, sectionHexLp);
        installSectionMarkerSync(sectionStartInput, sectionStartHexInput);
        installSectionMarkerSync(sectionEndInput, sectionEndHexInput);

        TextView note = new TextView(overlayContext);
        note.setText("Saves JSON under " + MemoryHexPayloadStore.packageDirectoryPath(packageName)
                + ". Save the same name as Original first, then edit/reselect and save the same name as Patched. Choose a mask preset here, or keep the existing mask. Use Sec S/Sec E in Drag Select to stage optional section markers.");
        note.setTextSize(12f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(4);
        box.addView(note, noteLp);
        AlertDialog dialog = new MaterialAlertDialogBuilder(overlayContext)
                .setTitle("Save Hex Payload")
                .setView(box)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, w) -> {
                    try { d.dismiss(); } catch (Throwable ignored) {}
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                    if (isLiveEnabled()) scheduleLiveRefresh();
                })
                .create();
        try {
            android.view.Window window = dialog.getWindow();
            if (window != null) window.setType(Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE);
        } catch (Throwable ignored) {}
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            if (TextUtils.isEmpty(name)) name = defaultPayloadDisplayName(payloadHex, stamp);
            String safeFilePart = sanitizePayloadFilePart(name);
            if (TextUtils.isEmpty(safeFilePart)) {
                inputLayout.setError("Enter at least one letter or number.");
                return;
            }
            inputLayout.setError(null);
            String sectionStart = sectionStartInput.getText() == null ? "" : sectionStartInput.getText().toString().trim();
            String sectionEnd = sectionEndInput.getText() == null ? "" : sectionEndInput.getText().toString().trim();
            String sectionStartHex = sectionStartHexInput.getText() == null ? "" : sectionStartHexInput.getText().toString().trim();
            String sectionEndHex = sectionEndHexInput.getText() == null ? "" : sectionEndHexInput.getText().toString().trim();
            boolean hasSectionStart = !TextUtils.isEmpty(sectionStart) || !TextUtils.isEmpty(sectionStartHex);
            boolean hasSectionEnd = !TextUtils.isEmpty(sectionEnd) || !TextUtils.isEmpty(sectionEndHex);
            if (hasSectionStart != hasSectionEnd) {
                sectionStartLayout.setError(hasSectionStart ? null : "Required with section end.");
                sectionStartHexLayout.setError(hasSectionStart ? null : "Required with section end.");
                sectionEndLayout.setError(hasSectionEnd ? null : "Required with section start.");
                sectionEndHexLayout.setError(hasSectionEnd ? null : "Required with section start.");
                return;
            }
            sectionStartLayout.setError(null);
            sectionEndLayout.setError(null);
            sectionStartHexLayout.setError(null);
            sectionEndHexLayout.setError(null);
            boolean saveAsPatched = roleGroup.getCheckedRadioButtonId() == patched.getId();
            String maskHexOverride = "";
            boolean replaceMask = selectedMaskPresetId[0] != MemoryHexPayloadStore.MASK_PRESET_NO_CHANGE;
            Boolean preserveMaskOverride = null;
            if (replaceMask) {
                try {
                    maskHexOverride = MemoryHexPayloadStore.buildMaskHexForPreset(selectedMaskPresetId[0], payloadHex);
                    preserveMaskOverride = preserveMaskInput.isChecked();
                    maskPresetLayout.setError(null);
                } catch (Throwable t) {
                    maskPresetLayout.setError(safeMessage(t));
                    return;
                }
            }
            try { dialog.dismiss(); } catch (Throwable ignored) {}
            savePayloadJsonAsync(payloadHex, packageName, name, saveAsPatched, maskHexOverride, replaceMask, preserveMaskOverride, sectionStart, sectionEnd, sectionStartHex, sectionEndHex, stamp);
        }));
        dialog.setOnCancelListener(d -> {
            MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
            if (isLiveEnabled()) scheduleLiveRefresh();
        });
        try { dialog.show(); } catch (Throwable t) { reportStatus("Save payload dialog failed: " + t.getMessage()); }
    }

    /** Performs payload JSON merging/writing off the UI thread. */
    private void savePayloadJsonAsync(String hex, String packageName, String displayName, boolean saveAsPatched, String maskHexOverride, boolean replaceMask, Boolean preserveMaskWildcardsOverride, String sectionStartAscii, String sectionEndAscii, String sectionStartHexInput, String sectionEndHexInput, String stamp) {
        final String payloadName = TextUtils.isEmpty(displayName) ? "payload" : displayName.trim();
        final String selectedMaskHex = maskHexOverride == null ? "" : maskHexOverride.trim();
        final boolean shouldReplaceMask = replaceMask;
        final Boolean preserveMaskOverride = preserveMaskWildcardsOverride;
        final String sectionStart = sectionStartAscii == null ? "" : sectionStartAscii.trim();
        final String sectionEnd = sectionEndAscii == null ? "" : sectionEndAscii.trim();
        final String sectionStartHex = sectionStartHexInput == null ? "" : sectionStartHexInput.trim();
        final String sectionEndHex = sectionEndHexInput == null ? "" : sectionEndHexInput.trim();
        reportStatus("Saving " + (saveAsPatched ? "patched" : "original") + " bytes for payload \"" + payloadName + "\"...");
        new Thread(() -> {
            String status;
            MemoryHexPayloadStore.SaveResult result = null;
            try {
                result = MemoryHexPayloadStore.saveSelectedPayload(
                        appContext,
                        packageName,
                        payloadName,
                        hex,
                        selectedHighlightAddress,
                        saveAsPatched,
                        selectedMaskHex,
                        shouldReplaceMask,
                        preserveMaskOverride,
                        sectionStart,
                        sectionEnd,
                        sectionStartHex,
                        sectionEndHex,
                        stamp);
                status = "Saved " + (result.savedAsPatched ? "patched" : "original") + " bytes for payload \""
                        + result.name + "\" (" + formatPayloadLength(result.savedBytes) + ").\nFile: " + result.path;
                if (result.complete) {
                    status += "\nPayload has original and patched bytes.";
                    if (!TextUtils.isEmpty(result.sectionStartHex)) status += "\nPayload is section-scoped.";
                    loadedPayloadOriginalHex = result.originalHex;
                    loadedPayloadPatchedHex = result.patchedHex;
                    loadedPayloadMaskHex = result.maskHex;
                    loadedPayloadSectionStartHex = result.sectionStartHex;
                    loadedPayloadSectionEndHex = result.sectionEndHex;
                    clearPendingPayloadSectionMarkers();
                    loadedPayloadPreserveMaskWildcards = result.preserveMaskWildcards;
                    loadedPayloadHex = result.patchedHex;
                    loadedPayloadName = result.name;
                    loadedPayloadPackageName = packageName;
                    if (result.savedAsPatched) {
                        loadedPayloadPatchedAddress = selectedHighlightAddress;
                    } else {
                        loadedPayloadOriginalAddress = selectedHighlightAddress;
                    }
                } else {
                    status += "\nPayload is incomplete until both original_hex and patched_hex are saved.";
                }
            } catch (Throwable t) {
                status = "Save payload failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            final String finalStatus = status;
            final MemoryHexPayloadStore.SaveResult finalResult = result;
            handler.post(() -> {
                // Once both sides exist, preload patched_hex into Patch Value so
                // manual patch/write flows match the payload that was just saved.
                if (finalResult != null && finalResult.complete && !TextUtils.isEmpty(finalResult.patchedHex) && payloadPatchValueListener != null) {
                    payloadPatchValueListener.onHexPayloadLoaded(finalResult.patchedHex);
                }
                MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                reportStatus(finalStatus);
                if (isLiveEnabled()) scheduleLiveRefresh();
            });
        }, "MemoryHexPayloadSave").start();
    }

    private String savePayloadTextToDisk(String fileName, String text) throws java.io.IOException {
        File finalFile = new File(PAYLOAD_DIR, fileName);
        String shellStatus = savePayloadTextWithShell(finalFile, text);
        if (!TextUtils.isEmpty(shellStatus)) return shellStatus;
        File fallback = payloadFallbackFile(fileName);
        if (fallback == null) throw new java.io.IOException("No writable payload directory");
        File dir = fallback.getParentFile();
        if (dir == null || ((!dir.exists() && !dir.mkdirs()) || !dir.isDirectory())) {
            throw new java.io.IOException("Cannot create fallback payload directory");
        }
        try (FileOutputStream fos = new FileOutputStream(fallback, false)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return "Saved hex payload to app fallback: " + fallback.getAbsolutePath();
    }

    private String savePayloadTextWithShell(File finalFile, String text) {
        try {
            String b64 = Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            String tmpPath = PAYLOAD_DIR + "/." + finalFile.getName() + ".tmp." + Long.toHexString(System.nanoTime());
            StringBuilder cmd = new StringBuilder();
            cmd.append("mkdir -p ").append(MemoryToolRuntime.shQuote(PAYLOAD_DIR)).append(" && ");
            cmd.append("if command -v base64 >/dev/null 2>&1; then ");
            cmd.append("printf %s ").append(MemoryToolRuntime.shQuote(b64)).append(" | base64 -d > ").append(MemoryToolRuntime.shQuote(tmpPath)).append("; ");
            cmd.append("else printf %s ").append(MemoryToolRuntime.shQuote(b64)).append(" | toybox base64 -d > ").append(MemoryToolRuntime.shQuote(tmpPath)).append("; fi");
            cmd.append(" && mv -f ").append(MemoryToolRuntime.shQuote(tmpPath)).append(' ').append(MemoryToolRuntime.shQuote(finalFile.getAbsolutePath()));
            cmd.append(" && chmod 0666 ").append(MemoryToolRuntime.shQuote(finalFile.getAbsolutePath()));
            cmd.append(" && wc -c < ").append(MemoryToolRuntime.shQuote(finalFile.getAbsolutePath()));
            MemoryToolRuntime.CmdResult r = MemoryToolRuntime.runShellCommandCaptureSync(appContext, cmd.toString());
            if (r != null && r.exitCode == 0) {
                return "Saved hex payload: " + finalFile.getAbsolutePath();
            }
            return "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Lists payloads for the active package folder and loads the selected JSON. */
    private void showLoadPayloadDialog() {
        String packageName = currentPayloadPackageName();
        if (TextUtils.isEmpty(packageName)) {
            reportStatus("Select a target package before loading payloads.");
            return;
        }
        ArrayList<File> files = MemoryHexPayloadStore.listPayloadFiles(appContext, packageName);
        if (files.isEmpty()) {
            reportStatus("No hex payload JSON files found in " + MemoryHexPayloadStore.packageDirectoryPath(packageName) + ".");
            return;
        }
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) labels[i] = MemoryHexPayloadStore.payloadLabel(appContext, files.get(i));
        AlertDialog dialog = new MaterialAlertDialogBuilder(overlayContext)
                .setTitle("Load Hex Payload")
                .setItems(labels, (d, which) -> loadPayloadFile(files.get(which), false))
                .setNegativeButton("Cancel", null)
                .create();
        try {
            android.view.Window window = dialog.getWindow();
            if (window != null) window.setType(Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE);
        } catch (Throwable ignored) {}
        try { dialog.show(); } catch (Throwable t) { reportStatus("Load payload dialog failed: " + t.getMessage()); }
    }

    /** Loads one payload, optionally allowing a user-confirmed package mismatch. */
    private void loadPayloadFile(File file, boolean allowPackageMismatch) {
        try {
            MemoryHexPayloadStore.Payload payload = MemoryHexPayloadStore.loadPayload(appContext, file);
            String currentPackage = currentPayloadPackageName();
            if (!allowPackageMismatch
                    && !TextUtils.isEmpty(currentPackage)
                    && !MemoryHexPayloadStore.packageNameMatches(payload.packageName, currentPackage)) {
                showLoadMismatchedPayloadDialog(file, payload, currentPackage);
                return;
            }
            loadedPayloadOriginalHex = payload.originalHex;
            loadedPayloadPatchedHex = payload.patchedHex;
            loadedPayloadMaskHex = payload.maskHex;
            loadedPayloadSectionStartHex = payload.sectionStartHex;
            loadedPayloadSectionEndHex = payload.sectionEndHex;
            clearPendingPayloadSectionMarkers();
            loadedPayloadPreserveMaskWildcards = payload.preserveMaskWildcards;
            loadedPayloadHex = payload.patchedHex;
            loadedPayloadName = payload.name;
            loadedPayloadPackageName = payload.packageName;
            loadedPayloadOriginalAddress = payload.originalAddress;
            loadedPayloadPatchedAddress = payload.patchedAddress;
            if (payloadPatchValueListener != null) payloadPatchValueListener.onHexPayloadLoaded(payload.patchedHex);
            int maskedBytes = MemoryHexPayloadStore.countWildcardBytes(payload.maskHex);
            String loadedStatus = "Loaded hex payload \"" + payload.name + "\" (" + formatPayloadLength(payload.length) + ")."
                    + "\nPackage: " + payload.packageName
                    + "\nOriginal bytes: " + formatPayloadLength(byteCountForHex(payload.originalHex))
                    + "\nPatched bytes: " + formatPayloadLength(byteCountForHex(payload.patchedHex))
                    + "\nMask: " + (maskedBytes > 0 ? (maskedBytes + " wildcard byte" + (maskedBytes == 1 ? "" : "s")) : "none")
                    + "\nSection scope: " + (!TextUtils.isEmpty(payload.sectionStartHex) ? "yes" : "none")
                    + "\nEnabled: " + (payload.enabled ? "yes" : "no")
                    + "\nPreserve masked bytes: " + (payload.preserveMaskWildcards ? "yes" : "no")
                    + "\nFile: " + (file == null ? payload.fileName : file.getAbsolutePath())
                    + "\nUse Find to locate original bytes, Write to write patched bytes, or Apply to find and patch automatically.";
            if (hasPayloadSelection() && selectedHighlightBytes != payload.length) {
                reportStatus(loadedStatus + "\nSelected range is " + formatPayloadLength(selectedHighlightBytes) + "; Write will ask before writing the full payload.");
            } else {
                reportStatus(loadedStatus);
            }
            renderCachedHex();
        } catch (Throwable t) {
            reportStatus("Load payload failed: " + t.getMessage());
        }
    }

    /** Confirms loading a payload from a different package folder/name. */
    private void showLoadMismatchedPayloadDialog(File file, MemoryHexPayloadStore.Payload payload, String currentPackage) {
        stopLiveRefresh();
        MemoryOverlayWindowSupport.setInteractive(wm, root, params, true);
        AlertDialog dialog = new MaterialAlertDialogBuilder(overlayContext)
                .setTitle("Payload Package Mismatch")
                .setMessage("Payload \"" + payload.name + "\" is for " + payload.packageName
                        + ", but the current target is " + currentPackage + ".\n\nLoad anyway?")
                .setPositiveButton("Load", (d, which) -> {
                    try { d.dismiss(); } catch (Throwable ignored) {}
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                    loadPayloadFile(file, true);
                })
                .setNegativeButton("Cancel", (d, which) -> {
                    try { d.dismiss(); } catch (Throwable ignored) {}
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                    if (isLiveEnabled()) scheduleLiveRefresh();
                })
                .create();
        dialog.setOnCancelListener(d -> {
            MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
            if (isLiveEnabled()) scheduleLiveRefresh();
        });
        try {
            android.view.Window window = dialog.getWindow();
            if (window != null) window.setType(Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE);
        } catch (Throwable ignored) {}
        try { dialog.show(); } catch (Throwable t) {
            MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
            reportStatus("Package mismatch dialog failed: " + t.getMessage());
            if (isLiveEnabled()) scheduleLiveRefresh();
        }
    }

    /** Finds loaded original_hex and writes loaded patched_hex for one payload. */
    private void applyLoadedPayload() {
        if (payloadApplyRequester == null) {
            reportStatus("Apply Payloads is not connected to the memory backend.");
            return;
        }
        if (TextUtils.isEmpty(loadedPayloadOriginalHex) || TextUtils.isEmpty(loadedPayloadPatchedHex)) {
            reportStatus("Load a complete payload with original_hex and patched_hex before applying.");
            return;
        }
        // Invalid or mismatched files are skipped and reported instead of
        // blocking the valid payloads in the same package folder.
        ArrayList<PayloadApplySpec> payloads = new ArrayList<>();
        payloads.add(new PayloadApplySpec(loadedPayloadName, loadedPayloadOriginalHex, loadedPayloadPatchedHex, loadedPayloadMaskHex, loadedPayloadSectionStartHex, loadedPayloadSectionEndHex, true, loadedPayloadPreserveMaskWildcards));
        stopLiveRefresh();
        reportStatus("Applying payload \"" + (TextUtils.isEmpty(loadedPayloadName) ? "loaded payload" : loadedPayloadName) + "\"...");
        payloadApplyRequester.requestApplyHexPayloads(payloads, (success, message) -> {
            reportStatus(TextUtils.isEmpty(message) ? (success ? "Apply Payloads finished." : "Apply Payloads failed.") : message);
            if (success) readHexRange(false);
            if (isLiveEnabled()) scheduleLiveRefresh();
        });
    }

    /** Applies every complete payload JSON in the current package folder. */
    private void applyAllPayloadsForCurrentPackage() {
        if (payloadApplyRequester == null) {
            reportStatus("Apply Payloads is not connected to the memory backend.");
            return;
        }
        String packageName = currentPayloadPackageName();
        if (TextUtils.isEmpty(packageName)) {
            reportStatus("Select a target package before applying payloads.");
            return;
        }
        ArrayList<File> files = MemoryHexPayloadStore.listPayloadFiles(appContext, packageName);
        if (files.isEmpty()) {
            reportStatus("No payloads found in " + MemoryHexPayloadStore.packageDirectoryPath(packageName) + ".");
            return;
        }
        ArrayList<PayloadApplySpec> payloads = new ArrayList<>();
        ArrayList<String> skipped = new ArrayList<>();
        for (File file : files) {
            try {
                MemoryHexPayloadStore.Payload payload = MemoryHexPayloadStore.loadPayload(appContext, file);
                if (!MemoryHexPayloadStore.packageNameMatches(payload.packageName, packageName)) {
                    skipped.add(payload.name + " package mismatch");
                    continue;
                }
                if (!payload.enabled) {
                    skipped.add(payload.name + " disabled");
                    continue;
                }
                payloads.add(new PayloadApplySpec(payload.name, payload.originalHex, payload.patchedHex, payload.maskHex, payload.sectionStartHex, payload.sectionEndHex, payload.enabled, payload.preserveMaskWildcards));
            } catch (Throwable t) {
                skipped.add((file == null ? "payload" : file.getName()) + " invalid: " + t.getMessage());
            }
        }
        if (payloads.isEmpty()) {
            reportStatus("No valid complete payloads to apply for " + packageName + "."
                    + (skipped.isEmpty() ? "" : "\n" + TextUtils.join("\n", skipped)));
            return;
        }
        stopLiveRefresh();
        reportStatus("Applying " + payloads.size() + " payload" + (payloads.size() == 1 ? "" : "s") + " for " + packageName + "...");
        payloadApplyRequester.requestApplyHexPayloads(payloads, (success, message) -> {
            String status = TextUtils.isEmpty(message) ? (success ? "Apply Payloads finished." : "Apply Payloads failed.") : message;
            if (!skipped.isEmpty()) status += "\nSkipped:\n" + TextUtils.join("\n", skipped);
            reportStatus(status);
            if (success) readHexRange(false);
            if (isLiveEnabled()) scheduleLiveRefresh();
        });
    }

    /**
     * Writes patched_hex at the selected start address.  If the selected visible
     * range is shorter than the payload, confirmation expands the write range to
     * the full payload length without requiring the user to drag perfectly.
     */
    private void writeLoadedPayloadToSelection() {
        String hex;
        try {
            hex = normalizeAnyHexBytes(TextUtils.isEmpty(loadedPayloadPatchedHex) ? loadedPayloadHex : loadedPayloadPatchedHex);
        } catch (IllegalArgumentException e) {
            reportStatus("Load a replacement payload before writing.");
            return;
        }
        if (!hasPayloadSelection()) {
            reportStatus("Select the target bytes with Drag Select before writing.");
            return;
        }
        int payloadBytes = byteCountForHex(hex);
        final long address = selectedHighlightAddress;
        if (payloadBytes != selectedHighlightBytes) {
            showWriteFullPayloadDialog(hex, address, selectedHighlightBytes, payloadBytes);
            return;
        }
        writeHexPayloadAtAddress(hex, address, payloadBytes, false);
    }

    /** Confirms full-payload writes when the visible selection length differs. */
    private void showWriteFullPayloadDialog(String hex, long address, int selectedBytes, int payloadBytes) {
        if (byteWriteRequester == null) {
            reportStatus("Hex payload write is not connected to the memory backend.");
            return;
        }
        stopLiveRefresh();
        MemoryOverlayWindowSupport.setInteractive(wm, root, params, true);
        String name = TextUtils.isEmpty(loadedPayloadName) ? "loaded payload" : loadedPayloadName.trim();
        String endAddress = formatHex(address + Math.max(0, payloadBytes - 1));
        String message = "Selected range is " + formatPayloadLength(selectedBytes)
                + ", but the loaded payload \"" + name + "\" is " + formatPayloadLength(payloadBytes) + ".\n\n"
                + "Write full payload at " + formatHex(address) + " - " + endAddress + "?";
        AlertDialog dialog = new MaterialAlertDialogBuilder(overlayContext)
                .setTitle("Write Full Payload")
                .setMessage(message)
                .setPositiveButton("Write", (d, which) -> {
                    try { d.dismiss(); } catch (Throwable ignored) {}
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                    selectPayloadWriteRange(address, payloadBytes);
                    writeHexPayloadAtAddress(hex, address, payloadBytes, true);
                })
                .setNegativeButton("Cancel", (d, which) -> {
                    try { d.dismiss(); } catch (Throwable ignored) {}
                    MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
                    if (isLiveEnabled()) scheduleLiveRefresh();
                })
                .create();
        dialog.setOnCancelListener(d -> {
            MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
            if (isLiveEnabled()) scheduleLiveRefresh();
        });
        try {
            android.view.Window window = dialog.getWindow();
            if (window != null) window.setType(Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE);
        } catch (Throwable ignored) {}
        try { dialog.show(); } catch (Throwable t) {
            MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
            reportStatus("Write payload dialog failed: " + t.getMessage());
            if (isLiveEnabled()) scheduleLiveRefresh();
        }
    }

    /** Dispatches the final byte write to the service memory backend. */
    private void writeHexPayloadAtAddress(String hex, long address, int payloadBytes, boolean fullPayloadConfirmed) {
        if (byteWriteRequester == null) {
            reportStatus("Hex payload write is not connected to the memory backend.");
            if (isLiveEnabled()) scheduleLiveRefresh();
            return;
        }
        stopLiveRefresh();
        String name = TextUtils.isEmpty(loadedPayloadName) ? "loaded payload" : loadedPayloadName.trim();
        if (fullPayloadConfirmed) {
            reportStatus("Writing full hex payload \"" + name + "\" (" + formatPayloadLength(payloadBytes)
                    + ") at " + formatHex(address) + "...");
        }
        byteWriteRequester.requestWriteBytes(formatHex(address), hex, (success, text) -> {
            reportStatus(text == null || text.trim().isEmpty() ? (success ? "Hex payload write finished." : "Hex payload write failed.") : text.trim());
            if (success) {
                updateCachedBytes(address, hex);
                renderCachedHex();
            }
            if (isLiveEnabled()) scheduleLiveRefresh();
        });
    }

    /**
     * Expands the visual selection to the write length after the user confirms a
     * full-payload write from a single search-result address.
     */
    private void selectPayloadWriteRange(long address, int payloadBytes) {
        selectedByteAddress = address;
        selectedDwordAddress = address & ~3L;
        selectedDwordValue = readLittleEndianUnsigned(selectedDwordAddress, 4);
        selectedHighlightAddress = address;
        selectedHighlightBytes = Math.max(1, payloadBytes);
        if (selectionListener != null) {
            selectionListener.onHexSelection(address, selectedDwordAddress, selectedDwordValue);
        }
        renderCachedHex();
    }


    /** Saves the current Drag Select range as wildcard bytes for the loaded payload. */
    private void maskSelectedPayloadBytes() {
        if (TextUtils.isEmpty(loadedPayloadName)
                || TextUtils.isEmpty(loadedPayloadOriginalHex)
                || TextUtils.isEmpty(loadedPayloadPatchedHex)) {
            reportStatus("Load or save a complete payload before marking mask bytes.");
            return;
        }
        if (!hasPayloadSelection()) {
            reportStatus("Select the payload bytes to ignore, then press Mask.");
            return;
        }
        final String packageName = currentPayloadPackageName();
        if (TextUtils.isEmpty(packageName)) {
            reportStatus("Select a target package before saving a payload mask.");
            return;
        }
        final int payloadBytes;
        try {
            payloadBytes = byteCountForHex(loadedPayloadOriginalHex);
            if (payloadBytes != byteCountForHex(loadedPayloadPatchedHex)) {
                reportStatus("Loaded payload original and patched byte counts differ.");
                return;
            }
        } catch (Throwable t) {
            reportStatus("Loaded payload bytes are invalid: " + t.getMessage());
            return;
        }
        final int maskOffset = resolvePayloadMaskOffset(payloadBytes);
        if (maskOffset < 0 || maskOffset + selectedHighlightBytes > payloadBytes) {
            reportStatus("Mask selection must be inside the loaded payload range. Load or save the payload from this visible address first.");
            return;
        }
        final int maskBytes = selectedHighlightBytes;
        final String payloadName = loadedPayloadName;
        final String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        stopLiveRefresh();
        reportStatus("Saving payload mask for \"" + payloadName + "\" at offset 0x"
                + Integer.toHexString(maskOffset).toUpperCase(Locale.US)
                + " (" + maskBytes + " byte" + (maskBytes == 1 ? "" : "s") + ")...");
        new Thread(() -> {
            String status;
            MemoryHexPayloadStore.SaveMaskResult result = null;
            try {
                result = MemoryHexPayloadStore.savePayloadMask(appContext, packageName, payloadName, maskOffset, maskBytes, stamp);
                status = "Saved payload mask for \"" + result.name + "\": "
                        + result.maskedBytes + " wildcard byte" + (result.maskedBytes == 1 ? "" : "s")
                        + " of " + formatPayloadLength(result.length) + ".\nFile: " + result.path;
            } catch (Throwable t) {
                status = "Save payload mask failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            final String finalStatus = status;
            final MemoryHexPayloadStore.SaveMaskResult finalResult = result;
            handler.post(() -> {
                if (finalResult != null) loadedPayloadMaskHex = finalResult.maskHex;
                reportStatus(finalStatus);
                if (isLiveEnabled()) scheduleLiveRefresh();
                renderCachedHex();
            });
        }, "MemoryHexPayloadMaskSave").start();
    }

    /** Resolves the selected mask range to a byte offset inside the loaded payload. */
    private int resolvePayloadMaskOffset(int payloadBytes) {
        long start = selectedHighlightAddress;
        long end = selectedHighlightAddress + selectedHighlightBytes - 1L;
        int offset = offsetIfSelectionInsidePayloadBase(start, end, loadedPayloadOriginalAddress, payloadBytes);
        if (offset >= 0) return offset;
        offset = offsetIfSelectionInsidePayloadBase(start, end, loadedPayloadPatchedAddress, payloadBytes);
        if (offset >= 0) return offset;
        return offsetFromMatchingPayloadWindow(start, end, payloadBytes);
    }

    private int offsetIfSelectionInsidePayloadBase(long start, long end, long base, int payloadBytes) {
        if (base < 0L || payloadBytes <= 0) return -1;
        long payloadEnd = base + payloadBytes - 1L;
        if (start >= base && end <= payloadEnd) return (int) (start - base);
        return -1;
    }

    /**
     * Fallback for payloads whose saved address moved: scan the visible cache for
     * a complete original/patched payload window that contains the selected bytes.
     */
    private int offsetFromMatchingPayloadWindow(long start, long end, int payloadBytes) {
        if (lastBaseAddress < 0L || lastBytes == null || payloadBytes <= 0 || payloadBytes > lastBytes.length) return -1;
        byte[] original = bytesFromHexSafe(loadedPayloadOriginalHex);
        byte[] patched = bytesFromHexSafe(loadedPayloadPatchedHex);
        int selectedStart = (int) (start - lastBaseAddress);
        int selectedEnd = (int) (end - lastBaseAddress);
        if (selectedStart < 0 || selectedEnd >= lastBytes.length || selectedStart > selectedEnd) return -1;
        for (int baseOffset = 0; baseOffset + payloadBytes <= lastBytes.length; baseOffset++) {
            if (selectedStart < baseOffset || selectedEnd >= baseOffset + payloadBytes) continue;
            if (bytesEqualAt(lastBytes, baseOffset, original) || bytesEqualAt(lastBytes, baseOffset, patched)) {
                return selectedStart - baseOffset;
            }
        }
        return -1;
    }

    private static boolean bytesEqualAt(byte[] haystack, int offset, byte[] needle) {
        if (haystack == null || needle == null || offset < 0 || offset + needle.length > haystack.length) return false;
        for (int i = 0; i < needle.length; i++) {
            if (haystack[offset + i] != needle[i]) return false;
        }
        return true;
    }

    private static byte[] bytesFromHexSafe(String hex) {
        try {
            String clean = normalizeAnyHexBytes(hex);
            byte[] out = new byte[clean.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    private static String normalizedPayloadMaskOrNull(String maskHex, int payloadBytes) {
        if (TextUtils.isEmpty(maskHex)) return null;
        String normalized = MemoryHexPayloadStore.normalizeMaskHex(maskHex, payloadBytes);
        return MemoryHexPayloadStore.hasWildcardBytes(normalized) ? normalized : null;
    }


    private String currentPayloadPackageName() {
        try {
            return packageNameProvider == null ? "" : packageNameProvider.currentPackageName();
        } catch (Throwable ignored) {
            return "";
        }
    }
    private void clearPendingPayloadSectionMarkers() {
        pendingPayloadSectionStartAscii = "";
        pendingPayloadSectionEndAscii = "";
        pendingPayloadSectionStartHex = "";
        pendingPayloadSectionEndHex = "";
    }

    private boolean hasPayloadSelection() {
        return selectedHighlightAddress >= 0L && selectedHighlightBytes > 0;
    }

    /** Extracts selected bytes from the last rendered read buffer. */
    private String selectedPayloadHex() {
        if (!hasPayloadSelection()) return "";
        return readHexBytesFromCache(selectedHighlightAddress, selectedHighlightBytes);
    }

    private File payloadDirectory() {
        File direct = new File(PAYLOAD_DIR);
        try {
            if ((direct.exists() || direct.mkdirs()) && direct.canWrite()) return direct;
        } catch (Throwable ignored) {
        }
        try {
            File base = appContext.getExternalFilesDir(null);
            if (base == null) base = appContext.getFilesDir();
            File dir = new File(base, "memory_payloads");
            if ((dir.exists() || dir.mkdirs()) && dir.canWrite()) return dir;
        } catch (Throwable ignored) {
        }
        return direct;
    }

    private File payloadFallbackFile(String fileName) {
        try {
            File base = appContext.getExternalFilesDir(null);
            if (base == null) base = appContext.getFilesDir();
            if (base == null) return null;
            return new File(new File(base, "memory_payloads"), fileName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String payloadDirectoryPathForStatus() {
        return PAYLOAD_DIR;
    }

    private ArrayList<File> listPayloadFiles() {
        ArrayList<File> out = new ArrayList<>();
        addShellPayloadFiles(out);
        File dir = payloadDirectory();
        File[] files = dir == null ? null : dir.listFiles((d, name) -> name != null && name.toLowerCase(Locale.US).endsWith(".json"));
        if (files != null) {
            for (File f : files) addPayloadFileIfMissing(out, f);
        }
        File fallbackDir = null;
        try {
            File base = appContext.getExternalFilesDir(null);
            if (base == null) base = appContext.getFilesDir();
            if (base != null) fallbackDir = new File(base, "memory_payloads");
        } catch (Throwable ignored) {}
        File[] fallbackFiles = fallbackDir == null ? null : fallbackDir.listFiles((d, name) -> name != null && name.toLowerCase(Locale.US).endsWith(".json"));
        if (fallbackFiles != null) {
            for (File f : fallbackFiles) addPayloadFileIfMissing(out, f);
        }
        Collections.sort(out, (a, b) -> Long.compare(payloadLastModified(b), payloadLastModified(a)));
        return out;
    }

    private void addShellPayloadFiles(ArrayList<File> out) {
        try {
            String script = "for f in " + MemoryToolRuntime.shQuote(PAYLOAD_DIR) + "/*.json; do [ -f \"$f\" ] && echo \"$f\"; done";
            MemoryToolRuntime.CmdResult r = MemoryToolRuntime.runShellCommandCaptureSync(appContext, script);
            if (r == null || r.exitCode != 0 || TextUtils.isEmpty(r.stdout)) return;
            String[] lines = r.stdout.split("\\n");
            for (String line : lines) {
                String path = line == null ? "" : line.trim();
                if (path.endsWith(".json")) addPayloadFileIfMissing(out, new File(path));
            }
        } catch (Throwable ignored) {
        }
    }

    private void addPayloadFileIfMissing(ArrayList<File> out, File file) {
        if (file == null || TextUtils.isEmpty(file.getAbsolutePath())) return;
        String path = file.getAbsolutePath();
        for (File existing : out) {
            if (existing != null && TextUtils.equals(existing.getAbsolutePath(), path)) return;
        }
        out.add(file);
    }

    private long payloadLastModified(File file) {
        try {
            if (file != null && file.exists()) return file.lastModified();
        } catch (Throwable ignored) {}
        try {
            MemoryToolRuntime.CmdResult r = MemoryToolRuntime.runShellCommandCaptureSync(appContext,
                    "stat -c %Y " + MemoryToolRuntime.shQuote(file.getAbsolutePath()) + " 2>/dev/null || echo 0");
            String out = r == null || r.stdout == null ? "" : r.stdout.trim();
            int nl = out.indexOf('\n');
            if (nl >= 0) out = out.substring(0, nl).trim();
            return Long.parseLong(out);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private String readPayloadTextFile(File file) throws java.io.IOException {
        try {
            return readTextFileDirect(file);
        } catch (java.io.IOException directError) {
            try {
                String cmd = "cat " + MemoryToolRuntime.shQuote(file.getAbsolutePath());
                MemoryToolRuntime.CmdResult r = MemoryToolRuntime.runShellCommandCaptureSync(appContext, cmd);
                if (r != null && r.exitCode == 0 && !TextUtils.isEmpty(r.stdout)) return r.stdout;
                String msg = r == null ? directError.getMessage() : ((r.stdout == null ? "" : r.stdout) + (r.stderr == null ? "" : r.stderr)).trim();
                java.io.IOException out = new java.io.IOException(TextUtils.isEmpty(msg) ? directError.getMessage() : msg);
                out.initCause(directError);
                throw out;
            } catch (java.io.IOException e) {
                throw e;
            } catch (Throwable t) {
                java.io.IOException out = new java.io.IOException(t.getMessage());
                out.initCause(directError);
                throw out;
            }
        }
    }

    private static String readTextFileDirect(File file) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            int n;
            while ((n = fis.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    private String payloadLabel(File file) {
        try {
            JSONObject json = new JSONObject(readPayloadTextFile(file));
            String name = json.optString("name", "").trim();
            int bytes = byteCountForHex(json.optString("hex", ""));
            Integer declaredBytes = payloadDeclaredLength(json);
            String base = TextUtils.isEmpty(name) ? (file == null ? "" : file.getName()) : name;
            if (declaredBytes != null && declaredBytes != bytes) {
                return base + " (invalid: length " + declaredBytes + ", hex " + bytes + " bytes)";
            }
            return base + " (" + bytes + " bytes)";
        } catch (Throwable ignored) {
        }
        return file == null ? "" : file.getName();
    }

    private static Integer payloadDeclaredLength(JSONObject json) {
        String[] keys = new String[]{"length", "size", "byte_count", "bytes"};
        for (String key : keys) {
            if (json == null || !json.has(key)) continue;
            Object value = json.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            if (value instanceof Number) {
                int n = ((Number) value).intValue();
                if (n < 0) throw new IllegalArgumentException("Payload " + key + " must not be negative.");
                return n;
            }
            String text = String.valueOf(value).trim();
            if (TextUtils.isEmpty(text)) continue;
            try {
                int n;
                if (text.startsWith("0x") || text.startsWith("0X")) {
                    n = Integer.parseInt(text.substring(2), 16);
                } else {
                    n = Integer.parseInt(text);
                }
                if (n < 0) throw new IllegalArgumentException("Payload " + key + " must not be negative.");
                return n;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Payload " + key + " must be a decimal or 0x-prefixed byte count.");
            }
        }
        return null;
    }

    private static String formatPayloadLength(int bytes) {
        return bytes + " bytes (0x" + Integer.toHexString(bytes).toUpperCase(Locale.US) + ")";
    }

    private static String defaultPayloadDisplayName(String hex, String stamp) {
        String preview = asciiPreview(hex).trim();
        String prefix = "Payload";
        if (!TextUtils.isEmpty(preview)) {
            String clean = sanitizePayloadFilePart(preview);
            if (!TextUtils.isEmpty(clean)) prefix = clean.replace('_', ' ');
        }
        return prefix + " " + stamp;
    }

    private static String uniquePayloadFileName(String safeFilePart, String stamp) {
        String safe = sanitizePayloadFilePart(safeFilePart);
        if (TextUtils.isEmpty(safe)) safe = "payload";
        return "memory_payload_" + safe + "_" + stamp + ".json";
    }

    private static String sanitizePayloadFilePart(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder();
        boolean lastUnderscore = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (ok) {
                out.append(c);
                lastUnderscore = false;
            } else if (!lastUnderscore && out.length() > 0) {
                out.append('_');
                lastUnderscore = true;
            }
            if (out.length() >= 48) break;
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '_') out.deleteCharAt(out.length() - 1);
        return out.toString();
    }

    private static String normalizeAnyHexBytes(String raw) {
        String value = raw == null ? "" : raw.trim();
        value = value.replace("0x", "").replace("0X", "").replaceAll("[^0-9a-fA-F]", "");
        if (value.isEmpty()) throw new IllegalArgumentException("Enter hex bytes.");
        if ((value.length() & 1) != 0) throw new IllegalArgumentException("Hex byte text must have an even digit count.");
        return value.toUpperCase(Locale.US);
    }

    private static int byteCountForHex(String hex) {
        return TextUtils.isEmpty(hex) ? 0 : normalizeAnyHexBytes(hex).length() / 2;
    }

    private static String asciiPreview(String hex) {
        try {
            byte[] bytes = new byte[byteCountForHex(hex)];
            String clean = normalizeAnyHexBytes(hex);
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            }
            StringBuilder sb = new StringBuilder(bytes.length);
            for (byte b : bytes) {
                int v = b & 0xff;
                sb.append(v >= 32 && v <= 126 ? (char) v : '.');
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static final class HitRegion {
        final int start;
        final int end;
        final long address;
        final boolean ascii;

        HitRegion(int start, int end, long address, boolean ascii) {
            this.start = start;
            this.end = end;
            this.address = address;
            this.ascii = ascii;
        }
    }


    public void releaseInputFocus() {
        try {
            if (root != null && root.findFocus() != null) {
                root.findFocus().clearFocus();
            }
        } catch (Throwable ignored) {
        }
        MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
    }

    private static String filteredBackendText(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) continue;
            String t = line.trim();
            if (t.startsWith("Attached TID") || t.startsWith("Detached TID")) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString().trim();
    }

    private boolean shouldKeepPreviousForTransientZeroDump(long baseAddress, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || !isAllZero(bytes)) return false;
        if (lastBaseAddress != baseAddress || lastBytes == null || lastBytes.length != bytes.length || isAllZero(lastBytes)) {
            clearPendingZeroFrame();
            return false;
        }
        if (pendingZeroBaseAddress == baseAddress && pendingZeroByteCount == bytes.length) {
            clearPendingZeroFrame();
            return false;
        }
        pendingZeroBaseAddress = baseAddress;
        pendingZeroByteCount = bytes.length;
        reportStatus("Hex live read returned a zero-filled frame; keeping previous bytes unless it repeats.");
        return true;
    }

    private void clearPendingZeroFrame() {
        pendingZeroBaseAddress = -1L;
        pendingZeroByteCount = 0;
    }

    private static boolean isAllZero(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return false;
        for (byte b : bytes) {
            if (b != 0) return false;
        }
        return true;
    }

    private static boolean isTransientBackendBusy(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.US);
        return lower.contains("text file busy") || lower.contains("resource busy");
    }

    private TextView outputView() {
        return root == null ? null : root.findViewById(R.id.txtOverlayMemoryHexOutput);
    }

    private String textOf(int id) {
        TextView view = root == null ? null : root.findViewById(id);
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
    }

    private boolean isHexAlignmentEnabled() {
        CheckBox align = root == null ? null : root.findViewById(R.id.chkOverlayMemoryHexAlign);
        return align == null || align.isChecked();
    }

    private int selectedAlignBytes() {
        MaterialAutoCompleteTextView dropdown = root == null ? null : root.findViewById(R.id.ddOverlayMemoryHexAlignMode);
        String label = dropdown == null || dropdown.getText() == null ? ALIGNMENT_LABELS[0] : dropdown.getText().toString().trim().toLowerCase(Locale.US);
        if (label.contains("qword")) return 8;
        if (label.contains("dword")) return 4;
        if (label.contains("word")) return 2;
        if (label.contains("byte") && !label.contains("16")) return 1;
        return 16;
    }

    /** Moves display begin down to the requested alignment boundary. */
    private static long alignHexDisplayBegin(long address, int alignBytes) {
        if (alignBytes <= 1) return address;
        long mask = (long) alignBytes - 1L;
        return address & ~mask;
    }

    private static int parseLength(String raw, int fallback, int max) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return fallback;
        int parsed;
        if (value.startsWith("0x") || value.startsWith("0X")) {
            parsed = (int) Long.parseLong(value.substring(2), 16);
        } else {
            parsed = Integer.parseInt(value);
        }
        if (parsed <= 0) throw new IllegalArgumentException("Length must be greater than zero.");
        if (parsed > max) return max;
        return parsed;
    }

    private static long parseAddress(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter an address first.");
        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return Long.parseUnsignedLong(value.substring(2), 16);
            }
            return Long.parseUnsignedLong(value, 16);
        } catch (Throwable first) {
            try {
                return Long.parseUnsignedLong(value);
            } catch (Throwable ignored) {
                throw new IllegalArgumentException("Invalid address: " + value);
            }
        }
    }

    private static String formatHex(long value) {
        return "0x" + Long.toHexString(value);
    }

    private void requestOutputScrollToTop() {
        resetOutputScrollOnNextRender = true;
        TextView output = outputView();
        if (output != null) {
            output.post(() -> {
                try { output.scrollTo(0, 0); } catch (Throwable ignored) {}
                fitHexScrollThumbToOutputHeight();
                syncHexScrollThumbFromOutput(output);
            });
        }
    }

    private void setOutput(TextView output, CharSequence text) {
        if (output == null) return;
        CharSequence safeText = text == null ? "" : text;
        String value = safeText.toString();
        boolean resetScroll = resetOutputScrollOnNextRender;
        if (TextUtils.equals(lastRenderedOutput, value)) {
            if (resetScroll) {
                resetOutputScrollOnNextRender = false;
                output.post(() -> {
                    try { output.scrollTo(0, 0); } catch (Throwable ignored) {}
                    fitHexScrollThumbToOutputHeight();
                    syncHexScrollThumbFromOutput(output);
                });
            }
            return;
        }
        int scrollX = resetScroll ? 0 : output.getScrollX();
        int scrollY = resetScroll ? 0 : output.getScrollY();
        if (resetScroll) {
            resetOutputScrollOnNextRender = false;
        }
        lastRenderedOutput = value;
        output.setText(safeText);
        output.post(() -> {
            try { output.scrollTo(scrollX, scrollY); } catch (Throwable ignored) {}
            fitHexScrollThumbToOutputHeight();
            syncHexScrollThumbFromOutput(output);
        });
    }

    private int dp(int value) {
        return MemoryOverlayWindowSupport.dp(appContext, value);
    }

    private String safeMessage(Throwable t) {
        String msg = t == null ? "" : t.getMessage();
        return TextUtils.isEmpty(msg) ? String.valueOf(t) : msg;
    }

    private void reportStatus(String message) {
        if (statusReporter != null) statusReporter.reportStatus(message);
    }
}
