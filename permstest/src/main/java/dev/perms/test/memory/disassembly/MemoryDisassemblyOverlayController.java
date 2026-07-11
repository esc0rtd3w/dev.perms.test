package dev.perms.test.memory.disassembly;

import dev.perms.test.vr.PermsTestVrOverlayCompat;
import dev.perms.test.memory.overlay.MemoryOverlayWindowSupport;
import dev.perms.test.R;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MemoryDisassemblyOverlayController {
    public interface ActionListener {
        void onOpenHex(long address);

        void onAddressSelected(long address);
    }

    private static final String PREFS = "perms_test";
    private static final String PREF_X = "memory_disasm_overlay_x";
    private static final String PREF_Y = "memory_disasm_overlay_y";
    private static final String PREF_W = "memory_disasm_overlay_w";
    private static final String PREF_H = "memory_disasm_overlay_h";
    private static final int DEFAULT_WINDOW_W_DP = 620;
    private static final int DEFAULT_WINDOW_H_DP = 455;
    private static final int DEFAULT_WINDOW_X_DP = 520;
    private static final int DEFAULT_WINDOW_Y_DP = 160;
    private static final int DEFAULT_LENGTH = 1024;
    private static final int MAX_DISASM_READ = 4096;
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("0x[0-9a-fA-F]{6,16}");

    private final Context appContext;
    private final Context overlayContext;
    private final WindowManager wm;
    private final MemoryOverlayWindowSupport.AddressProvider addressProvider;
    private final MemoryOverlayWindowSupport.StatusReporter statusReporter;
    private final MemoryOverlayWindowSupport.DumpRequester dumpRequester;
    private final ActionListener actionListener;
    private final Runnable closeCallback;

    private View root;
    private WindowManager.LayoutParams params;
    private boolean panelMode;
    private long lastBaseAddress = -1L;
    private byte[] lastBytes = new byte[0];
    private long selectedInstructionAddress = -1L;
    private long selectedInstructionWord = -1L;
    private String selectedInstructionText = "";
    private List<MemoryDisassemblyInstruction> lastInstructions = Collections.emptyList();
    private final MemoryDisassemblyDecoder disassembler = MemoryDisassemblyDecoder.createBestAvailable();
    private final MemoryDisassemblyReferenceAnalyzer referenceAnalyzer = new MemoryDisassemblyReferenceAnalyzer();
    private String lastRenderedOutput;
    private boolean disasmScrollThumbUpdating;
    private boolean externalInputActive;
    private int dumpGeneration;
    private int pendingAutoActionGeneration;
    private boolean suppressNextHexAddressSync;

    public MemoryDisassemblyOverlayController(Context appContext,
                                       Context overlayContext,
                                       WindowManager wm,
                                       MemoryOverlayWindowSupport.AddressProvider addressProvider,
                                       MemoryOverlayWindowSupport.StatusReporter statusReporter,
                                       MemoryOverlayWindowSupport.DumpRequester dumpRequester,
                                       ActionListener actionListener,
                                       Runnable closeCallback) {
        this.appContext = appContext.getApplicationContext();
        this.overlayContext = overlayContext;
        this.wm = wm;
        this.addressProvider = addressProvider;
        this.statusReporter = statusReporter;
        this.dumpRequester = dumpRequester;
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
                root = LayoutInflater.from(overlayContext).inflate(R.layout.overlay_memory_disassembly, null, false);
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
                    R.id.edtOverlayMemoryDisasmAddress,
                    R.id.edtOverlayMemoryDisasmLength,
                    String.valueOf(DEFAULT_LENGTH),
                    addressProvider,
                    true);
            root.setVisibility(View.VISIBLE);
            ensureDefaultViewSize();
            MemoryOverlayWindowSupport.applyTransparency(appContext, root.findViewById(R.id.cardMemoryDisasmOverlay));
            if (!MemoryOverlayWindowSupport.hasFocusedInput(root)) {
                MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
            }
        } catch (Throwable t) {
            reportStatus("Disassembly overlay failed: " + t);
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
                root = LayoutInflater.from(overlayContext).inflate(R.layout.overlay_memory_disassembly, null, false);
                bindWindow();
            }
            detachRootFromParent();
            container.removeAllViews();
            container.addView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            MemoryOverlayWindowSupport.updateAddressDefaults(
                    root,
                    R.id.edtOverlayMemoryDisasmAddress,
                    R.id.edtOverlayMemoryDisasmLength,
                    String.valueOf(DEFAULT_LENGTH),
                    addressProvider,
                    true);
            root.setVisibility(View.VISIBLE);
        } catch (Throwable t) {
            reportStatus("Disassembly panel failed: " + t);
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
        reportStatus(panelMode ? "Disassembly overlay saved window size reset." : "Disassembly overlay window reset.");
    }

    private void ensureDefaultViewSize() {
        if (params == null || wm == null || root == null) return;
        boolean changed = false;
        int minWidth = MemoryOverlayWindowSupport.fitOverlayWidth(appContext, MemoryOverlayWindowSupport.scaleOverlayPx(appContext, dp(590)));
        int minHeight = MemoryOverlayWindowSupport.fitOverlayHeight(appContext, MemoryOverlayWindowSupport.scaleOverlayPx(appContext, dp(420)));
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

    public void setAddress(String address, boolean autoAction) {
        setAddressInternal(address, autoAction, false);
    }

    /**
     * Updates Disassembly from a Hex Editor cursor change without bouncing the
     * aligned instruction address back into Hex during the automatic refresh.
     */
    public void setAddressFromHexEditor(String address, boolean autoAction) {
        setAddressInternal(address, autoAction, true);
    }

    private void setAddressInternal(String address, boolean autoAction, boolean fromHexEditor) {
        if (root == null || TextUtils.isEmpty(address)) return;
        TextView view = root.findViewById(R.id.edtOverlayMemoryDisasmAddress);
        if (view != null) view.setText(address.trim());
        if (autoAction) {
            suppressNextHexAddressSync = fromHexEditor;
            scheduleSelectedAutoAddressAction();
        } else if (!fromHexEditor) {
            suppressNextHexAddressSync = false;
        }
    }

    public boolean isActiveVisible() {
        try {
            return root != null
                    && root.getVisibility() == View.VISIBLE
                    && (panelMode || root.isAttachedToWindow() || root.getWindowToken() != null);
        } catch (Throwable ignored) {
            return root != null && root.getVisibility() == View.VISIBLE;
        }
    }

    private void scheduleSelectedAutoAddressAction() {
        if (root == null) return;
        final int generation = ++pendingAutoActionGeneration;
        root.postDelayed(() -> {
            if (generation != pendingAutoActionGeneration) return;
            if (isActiveVisible()) runSelectedAutoAddressAction();
        }, 35L);
    }

    private void runSelectedAutoAddressAction() {
        if (isAutoAnalyzeSelected()) {
            refreshInstructionWords(this::showCurrentAnalysis);
        } else {
            refreshInstructionWords();
        }
    }

    private boolean isAutoAnalyzeSelected() {
        try {
            RadioButton rb = root == null ? null : root.findViewById(R.id.rbOverlayMemoryDisasmAutoAnalyze);
            return rb != null && rb.isChecked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean isSyncWithHexEditor() {
        try {
            CheckBox sync = root == null ? null : root.findViewById(R.id.chkOverlayMemoryDisasmSyncHex);
            return sync == null || sync.isChecked();
        } catch (Throwable ignored) {
            return true;
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
                root.findViewById(R.id.overlayMemoryDisasmHeader),
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
                root.findViewById(R.id.overlayMemoryDisasmResizeHandle),
                root,
                params,
                PREFS,
                PREF_X,
                PREF_Y,
                PREF_W,
                PREF_H);

        View resetWindow = root.findViewById(R.id.btnOverlayMemoryDisasmResetWindow);
        if (resetWindow != null) resetWindow.setOnClickListener(v -> resetWindowBounds());

        View close = root.findViewById(R.id.btnOverlayMemoryDisasmClose);
        if (close != null) close.setOnClickListener(v -> {
            if (panelMode || PermsTestVrOverlayCompat.shouldDestroyToolOverlayOnClose(appContext)) {
                destroy();
                if (closeCallback != null) closeCallback.run();
            } else {
                MemoryOverlayWindowSupport.hide(wm, root, params);
            }
        });

        View refresh = root.findViewById(R.id.btnOverlayMemoryDisasmRefresh);
        if (refresh != null) refresh.setOnClickListener(v -> refreshInstructionWords());

        View analyze = root.findViewById(R.id.btnOverlayMemoryDisasmAnalyze);
        if (analyze != null) analyze.setOnClickListener(v -> refreshInstructionWords(this::showCurrentAnalysis));

        View refs = root.findViewById(R.id.btnOverlayMemoryDisasmFindRefs);
        if (refs != null) refs.setOnClickListener(v -> findReferenceTargetFromField());

        CheckBox syncHex = root.findViewById(R.id.chkOverlayMemoryDisasmSyncHex);
        if (syncHex != null) syncHex.setChecked(true);
        RadioButton autoAnalyze = root.findViewById(R.id.rbOverlayMemoryDisasmAutoAnalyze);
        if (autoAnalyze != null) autoAnalyze.setChecked(true);

        TextView output = outputView();
        if (output != null) {
            output.setMovementMethod(LinkMovementMethod.getInstance());
            output.setLinksClickable(true);
            output.setHighlightColor(0x00000000);
            output.setVerticalScrollBarEnabled(true);
            output.setScrollbarFadingEnabled(false);
            installDisassemblyOutputTouchHandler(output);
            bindDisassemblyScrollThumb(output);
        }

        MemoryOverlayWindowSupport.installInputHook(wm, root.findViewById(R.id.edtOverlayMemoryDisasmAddress), root, params);
        MemoryOverlayWindowSupport.installInputHook(wm, root.findViewById(R.id.edtOverlayMemoryDisasmLength), root, params);
        MemoryOverlayWindowSupport.installInputHook(wm, root.findViewById(R.id.edtOverlayMemoryDisasmRefTarget), root, params);
    }

    /**
     * Mirrors the scrolling TextView with the same compact thumbbar used by the
     * hex overlay.  The TextView remains the scroll owner so address/span hit
     * testing stays aligned with the rendered disassembly text.
     */
    private void bindDisassemblyScrollThumb(TextView output) {
        final SeekBar thumb = root == null ? null : root.findViewById(R.id.seekOverlayMemoryDisasmScroll);
        if (output == null || thumb == null) return;
        output.setVerticalScrollBarEnabled(true);
        output.setScrollbarFadingEnabled(false);
        thumb.setMax(1000);
        thumb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || disasmScrollThumbUpdating) return;
                int maxScroll = disassemblyOutputMaxScrollY(output);
                int y = maxScroll <= 0 ? 0 : Math.round((maxScroll * progress) / 1000.0f);
                try { output.scrollTo(output.getScrollX(), y); } catch (Throwable ignored) {}
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        if (Build.VERSION.SDK_INT >= 23) {
            output.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> syncDisassemblyScrollThumbFromOutput(output));
        }
        output.post(() -> {
            fitDisassemblyScrollThumbToOutputHeight();
            syncDisassemblyScrollThumbFromOutput(output);
        });
    }

    /** Sizes the rotated disassembly thumbbar to the current output height. */
    private void fitDisassemblyScrollThumbToOutputHeight() {
        if (root == null) return;
        View frame = root.findViewById(R.id.frameOverlayMemoryDisasmScrollThumb);
        SeekBar thumb = root.findViewById(R.id.seekOverlayMemoryDisasmScroll);
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

    /** Shows the thumbbar only when the disassembly output can scroll. */
    private void syncDisassemblyScrollThumbFromOutput(TextView output) {
        if (root == null || output == null) return;
        SeekBar thumb = root.findViewById(R.id.seekOverlayMemoryDisasmScroll);
        View frame = root.findViewById(R.id.frameOverlayMemoryDisasmScrollThumb);
        if (thumb == null || frame == null) return;
        int maxScroll = disassemblyOutputMaxScrollY(output);
        int visibility = maxScroll > 0 ? View.VISIBLE : View.GONE;
        if (frame.getVisibility() != visibility) frame.setVisibility(visibility);
        if (maxScroll <= 0) {
            disasmScrollThumbUpdating = true;
            try { thumb.setProgress(0); } finally { disasmScrollThumbUpdating = false; }
            return;
        }
        int progress = Math.max(0, Math.min(1000, Math.round((output.getScrollY() * 1000.0f) / maxScroll)));
        disasmScrollThumbUpdating = true;
        try { thumb.setProgress(progress); } finally { disasmScrollThumbUpdating = false; }
    }

    private int disassemblyOutputMaxScrollY(TextView output) {
        if (output == null || output.getLayout() == null) return 0;
        int contentHeight = output.getLayout().getHeight() + output.getTotalPaddingTop() + output.getTotalPaddingBottom();
        return Math.max(0, contentHeight - output.getHeight());
    }

    private void syncHexEditorAddressIfEnabled(long address) {
        try {
            if (isSyncWithHexEditor() && actionListener != null) {
                actionListener.onAddressSelected(address);
            }
        } catch (Throwable ignored) {
        }
    }

    private void refreshInstructionWords() {
        refreshInstructionWords(null);
    }

    private void refreshInstructionWords(Runnable afterSuccess) {
        TextView output = outputView();
        if (dumpRequester == null) {
            setOutput(output, "Disassembly read is unavailable for the current memory backend/target.");
            return;
        }
        long begin;
        int length;
        try {
            begin = parseAddress(textOf(R.id.edtOverlayMemoryDisasmAddress));
            length = parseLength(textOf(R.id.edtOverlayMemoryDisasmLength), DEFAULT_LENGTH, MAX_DISASM_READ);
        } catch (IllegalArgumentException e) {
            setOutput(output, e.getMessage());
            return;
        }
        long alignedBegin = begin & ~3L;
        int alignedLength = Math.max(4, ((length + 3) / 4) * 4);
        long end = alignedBegin + alignedLength;
        boolean suppressHexSync = suppressNextHexAddressSync;
        suppressNextHexAddressSync = false;
        if (!suppressHexSync) {
            syncHexEditorAddressIfEnabled(alignedBegin);
        }
        setOutput(output, "Reading " + alignedLength + " bytes from " + formatHex(alignedBegin) + "...\n");
        if (!MemoryOverlayWindowSupport.hasFocusedInput(root)) {
            MemoryOverlayWindowSupport.setInteractive(wm, root, params, false);
        }
        long finalAlignedBegin = alignedBegin;
        final int generation = ++dumpGeneration;
        dumpRequester.requestDump(formatHex(alignedBegin), formatHex(end), (success, text) -> {
            if (generation != dumpGeneration) return;
            String body = text == null ? "" : text.trim();
            if (!success) {
                setOutput(output, body.isEmpty() ? "Disassembly read failed." : filteredBackendText(body));
                return;
            }
            byte[] bytes = parseHexDumpBytes(body);
            if (bytes.length == 0) {
                lastBaseAddress = -1L;
                lastBytes = new byte[0];
                lastInstructions = Collections.emptyList();
                setOutput(output, "No dump bytes parsed.\n\n" + filteredBackendText(body));
                return;
            }
            lastBaseAddress = finalAlignedBegin;
            lastBytes = bytes;
            lastInstructions = disassembler.disassemble(bytes, finalAlignedBegin);
            setOutput(output, buildInstructionWordView(finalAlignedBegin, bytes, lastInstructions));
            if (afterSuccess != null) afterSuccess.run();
        });
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
            if (parts.length < 2 || !isAddressToken(parts[0])) continue;
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

    private CharSequence buildInstructionWordView(long baseAddress, byte[] bytes, List<MemoryDisassemblyInstruction> instructions) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendSelectionSummary(sb);
        sb.append(disassembler.name()).append(" + local references\n");
        if (!TextUtils.isEmpty(disassembler.detail())) {
            sb.append("Decoder: ").append(disassembler.detail()).append("\n");
        }
        sb.append("Tap an instruction/address to open it in Hex. Long-press for address, refs, follow-target, and detail actions. Use Ref Target to find direct refs in the current read window.\n");
        sb.append("addr        bytes       word      decoded / refs\n");
        sb.append("----------  ----------  --------  ----------------------------------------\n");
        Map<Long, List<MemoryDisassemblyReference>> comments = referenceAnalyzer.commentsBySource(instructions);
        Map<Long, String> labels = localLabelsFor(instructions, comments);
        if (instructions != null) {
            for (MemoryDisassemblyInstruction insn : instructions) {
                if (insn == null) continue;
                String label = labels.get(insn.address);
                if (!TextUtils.isEmpty(label)) {
                    int labelStart = sb.length();
                    sb.append(label).append(":\n");
                    sb.setSpan(new DisasmAddressClickSpan(insn.address), labelStart, labelStart + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                String decoded = decodedWithLabel(insn, labels);
                String commentText = referenceComment(comments.get(insn.address), labels);
                String line = String.format(Locale.US,
                        "0x%08x  %s  %s  %s%s\n",
                        insn.address,
                        insn.bytesText(),
                        insn.wordText(),
                        decoded,
                        commentText.isEmpty() ? "" : "  ; " + commentText);
                int lineStart = sb.length();
                sb.append(line);
                int lineEnd = Math.max(lineStart, sb.length() - 1);
                sb.setSpan(new DisasmInstructionClickSpan(insn), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (selectedInstructionAddress == insn.address) {
                    sb.setSpan(new BackgroundColorSpan(0x553F8CFF), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        int count = bytes == null ? 0 : bytes.length;
        if (count % 4 != 0) {
            sb.append("\n").append(String.valueOf(count % 4)).append(" trailing byte").append(count % 4 == 1 ? "" : "s").append(" not shown as an instruction word.");
        }
        return sb;
    }

    private static String referenceComment(List<MemoryDisassemblyReference> refs, Map<Long, String> labels) {
        if (refs == null || refs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (MemoryDisassemblyReference ref : refs) {
            if (ref == null) continue;
            if (shown > 0) sb.append(" | ");
            sb.append(ref.kind).append(" -> ").append(displayTarget(ref.targetAddress, labels));
            shown++;
            if (shown >= 2) {
                if (refs.size() > shown) sb.append(" | +").append(refs.size() - shown);
                break;
            }
        }
        return sb.toString();
    }

    private Map<Long, String> localLabelsFor(List<MemoryDisassemblyInstruction> instructions,
                                             Map<Long, List<MemoryDisassemblyReference>> refsBySource) {
        if (instructions == null || instructions.isEmpty() || refsBySource == null || refsBySource.isEmpty()) {
            return Collections.emptyMap();
        }
        java.util.LinkedHashSet<Long> addresses = new java.util.LinkedHashSet<>();
        for (MemoryDisassemblyInstruction insn : instructions) {
            if (insn != null) addresses.add(insn.address);
        }
        java.util.LinkedHashMap<Long, String> labels = new java.util.LinkedHashMap<>();
        for (List<MemoryDisassemblyReference> refs : refsBySource.values()) {
            if (refs == null) continue;
            for (MemoryDisassemblyReference ref : refs) {
                if (ref == null || !addresses.contains(ref.targetAddress)) continue;
                if (!labels.containsKey(ref.targetAddress)) labels.put(ref.targetAddress, localLabel(ref.targetAddress));
            }
        }
        return labels;
    }

    private static String decodedWithLabel(MemoryDisassemblyInstruction instruction, Map<Long, String> labels) {
        if (instruction == null) return "";
        String decoded = instruction.displayText == null ? "" : instruction.displayText;
        if (!instruction.hasDirectTarget()) return decoded;
        String label = labels == null ? null : labels.get(instruction.directTargetAddress);
        return TextUtils.isEmpty(label) ? decoded : decoded + " <" + label + ">";
    }

    private static String displayTarget(long address, Map<Long, String> labels) {
        String label = labels == null ? null : labels.get(address);
        return TextUtils.isEmpty(label) ? MemoryDisassemblyReference.formatHex(address) : label + " (" + MemoryDisassemblyReference.formatHex(address) + ")";
    }

    private static String localLabel(long address) {
        if ((address & 0xffffffff00000000L) == 0L) {
            return String.format(Locale.US, "loc_%08x", address & 0xffffffffL);
        }
        return String.format(Locale.US, "loc_%016x", address);
    }

    private void appendSelectionSummary(SpannableStringBuilder sb) {
        if (selectedInstructionAddress < 0L) return;
        sb.append("Selected ")
                .append(formatHex(selectedInstructionAddress))
                .append(" · word 0x")
                .append(String.format(Locale.US, "%08x", selectedInstructionWord & 0xffffffffL))
                .append(" · ")
                .append(selectedInstructionText == null ? "" : selectedInstructionText)
                .append("\n\n");
    }

    private void selectInstruction(MemoryDisassemblyInstruction instruction) {
        if (instruction == null) return;
        selectedInstructionAddress = instruction.address;
        selectedInstructionWord = instruction.word;
        selectedInstructionText = instruction.displayText == null ? "" : instruction.displayText;
        publishCurrentAddress(instruction.address, false);
        renderCachedDisassembly();
    }

    private void showInstructionMenu(View anchor, MemoryDisassemblyInstruction instruction) {
        if (externalInputActive) return;
        if (anchor == null || instruction == null) return;
        long address = instruction.address;
        long word = instruction.word;
        String decoded = instruction.displayText;
        long targetAddress = primaryReferenceTargetForInstruction(instruction);
        MemoryOverlayWindowSupport.setInteractive(wm, root, params, true);
        PopupMenu menu = new PopupMenu(overlayContext, anchor);
        menu.getMenu().add(0, 1, 0, "Refresh disasm here");
        menu.getMenu().add(0, 2, 1, "Set current address");
        menu.getMenu().add(0, 3, 2, "Open in Hex");
        menu.getMenu().add(0, 4, 3, "Instruction details");
        menu.getMenu().add(0, 5, 4, "Set Ref Target");
        menu.getMenu().add(0, 6, 5, "Find refs to this address");
        if (targetAddress != MemoryDisassemblyInstruction.NO_ADDRESS) {
            menu.getMenu().add(0, 9, 6, "Set target current: " + formatHex(targetAddress));
            menu.getMenu().add(0, 12, 7, "Follow target in Disasm");
            menu.getMenu().add(0, 10, 8, "Open target in Hex");
            menu.getMenu().add(0, 11, 9, "Find refs to target");
        }
        menu.getMenu().add(0, 7, 20, "Copy address");
        menu.getMenu().add(0, 8, 21, "Copy word");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    setAddress(formatHex(address), true);
                    publishCurrentAddress(address, false);
                    return true;
                case 2:
                    publishCurrentAddress(address, true);
                    return true;
                case 3:
                    openHexForAddress(address);
                    return true;
                case 4:
                    showInstructionDetails(address, word, decoded);
                    return true;
                case 5:
                    publishCurrentAddress(address, false);
                    setRefTargetField(formatHex(address));
                    return true;
                case 6:
                    publishCurrentAddress(address, false);
                    setRefTargetField(formatHex(address));
                    findReferencesToAddress(address);
                    return true;
                case 7:
                    copyText("PermsTest address", formatHex(address));
                    return true;
                case 8:
                    copyText("PermsTest word", String.format(Locale.US, "%08x", word & 0xffffffffL));
                    return true;
                case 9:
                    publishCurrentAddress(targetAddress, true);
                    return true;
                case 10:
                    openHexForAddress(targetAddress);
                    return true;
                case 11:
                    publishCurrentAddress(targetAddress, false);
                    setRefTargetField(formatHex(targetAddress));
                    findReferencesToAddress(targetAddress);
                    return true;
                case 12:
                    setAddress(formatHex(targetAddress), true);
                    publishCurrentAddress(targetAddress, false);
                    return true;
                default:
                    return false;
            }
        });
        menu.setOnDismissListener(ignored -> MemoryOverlayWindowSupport.setInteractive(wm, root, params, false));
        try { menu.show(); } catch (Throwable t) { reportStatus("Disassembly menu failed: " + t.getMessage()); }
    }

    private void setRefTargetField(String address) {
        TextView target = root == null ? null : root.findViewById(R.id.edtOverlayMemoryDisasmRefTarget);
        if (target != null) target.setText(address == null ? "" : address);
    }

    /**
     * Publishes a disassembly-selected address as the shared memory-tool address.
     * The service mirrors this into the main patch address and any already-open
     * tool overlays, while this controller updates its local Address and Ref Target
     * fields so reference searches do not require manual retyping.
     */
    private void publishCurrentAddress(long address, boolean report) {
        String formatted = formatHex(address);
        TextView disasmAddress = root == null ? null : root.findViewById(R.id.edtOverlayMemoryDisasmAddress);
        if (disasmAddress != null) disasmAddress.setText(formatted);
        setRefTargetField(formatted);
        if (actionListener != null) actionListener.onAddressSelected(address);
        if (report) reportStatus("Current memory address: " + formatted);
    }

    private void openHexForAddress(long address) {
        publishCurrentAddress(address, false);
        Runnable openAction = () -> {
            if (actionListener != null) actionListener.onOpenHex(address);
        };
        View view = root;
        if (view != null) {
            view.post(openAction);
        } else {
            openAction.run();
        }
    }

    private long primaryReferenceTargetForInstruction(MemoryDisassemblyInstruction instruction) {
        if (instruction == null) return MemoryDisassemblyInstruction.NO_ADDRESS;
        List<MemoryDisassemblyReference> refs = referenceAnalyzer.commentsBySource(lastInstructions).get(instruction.address);
        if (refs != null && !refs.isEmpty() && refs.get(0) != null) return refs.get(0).targetAddress;
        return instruction.hasDirectTarget() ? instruction.directTargetAddress : MemoryDisassemblyInstruction.NO_ADDRESS;
    }

    private String localLabelForLoadedAddress(long address) {
        if (lastInstructions == null || lastInstructions.isEmpty()) return "";
        for (MemoryDisassemblyInstruction insn : lastInstructions) {
            if (insn != null && insn.address == address) return localLabel(address);
        }
        return "";
    }

    private void findReferenceTargetFromField() {
        long target;
        try {
            String raw = textOf(R.id.edtOverlayMemoryDisasmRefTarget);
            if (TextUtils.isEmpty(raw) && selectedInstructionAddress >= 0L) raw = formatHex(selectedInstructionAddress);
            target = parseAddress(raw);
        } catch (IllegalArgumentException e) {
            setOutput(outputView(), e.getMessage());
            return;
        }
        findReferencesToAddress(target);
    }

    private void findReferencesToAddress(long target) {
        if (lastInstructions == null || lastInstructions.isEmpty()) {
            setOutput(outputView(), "Refresh a code range before finding references.");
            return;
        }
        List<MemoryDisassemblyReference> refs = referenceAnalyzer.findReferences(lastInstructions, target);
        StringBuilder sb = new StringBuilder();
        String localLabel = localLabelForLoadedAddress(target);
        sb.append("References to ").append(formatHex(target));
        if (!TextUtils.isEmpty(localLabel)) sb.append(" <").append(localLabel).append(">");
        sb.append(" in current read window\n");
        sb.append("Range: ").append(formatHex(lastBaseAddress)).append(" - ").append(formatHex(lastBaseAddress + Math.max(0, lastBytes == null ? 0 : lastBytes.length))).append("\n\n");
        if (refs.isEmpty()) {
            sb.append("No direct references found. This pass checks direct branch/literal targets and conservative ADRP+ADD/LDR/STR address construction within the currently loaded bytes.");
        } else {
            for (MemoryDisassemblyReference ref : refs) {
                sb.append(ref.oneLine()).append('\n');
            }
        }
        setOutputWithAddressLinks(outputView(), sb.toString().trim());
    }

    private void showCurrentAnalysis() {
        if (lastInstructions == null || lastInstructions.isEmpty()) {
            setOutput(outputView(), "Refresh a code range before running analysis.");
            return;
        }
        setOutputWithAddressLinks(outputView(), referenceAnalyzer.buildSummary(lastInstructions, lastBaseAddress, lastBytes == null ? 0 : lastBytes.length));
    }

    private static String bytesForWord(long word) {
        return String.format(Locale.US,
                "%02x %02x %02x %02x",
                word & 0xff,
                (word >> 8) & 0xff,
                (word >> 16) & 0xff,
                (word >> 24) & 0xff);
    }

    private void showInstructionDetails(long address, long word, String decoded) {
        String message = "Address: " + formatHex(address)
                + "\nWord: 0x" + String.format(Locale.US, "%08x", word & 0xffffffffL)
                + "\nBytes: " + bytesForWord(word)
                + "\nDecoded: " + (decoded == null ? "" : decoded);
        AlertDialog dialog = new MaterialAlertDialogBuilder(overlayContext)
                .setTitle("Instruction")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .create();
        try {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setType(Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE);
            }
        } catch (Throwable ignored) {}
        try { dialog.show(); } catch (Throwable t) { reportStatus("Instruction detail failed: " + t.getMessage()); }
    }

    private void copyText(String label, String value) {
        try {
            ClipboardManager clipboard = (ClipboardManager) appContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            reportStatus("Copied: " + value);
        } catch (Throwable t) {
            reportStatus("Copy failed: " + t.getMessage());
        }
    }

    private void renderCachedDisassembly() {
        if (lastBaseAddress < 0L || lastBytes == null || lastBytes.length == 0) return;
        lastRenderedOutput = null;
        if (lastInstructions == null || lastInstructions.isEmpty()) {
            lastInstructions = disassembler.disassemble(lastBytes, lastBaseAddress);
        }
        setOutput(outputView(), buildInstructionWordView(lastBaseAddress, lastBytes, lastInstructions));
    }

    private final class DisasmInstructionClickSpan extends ClickableSpan {
        private final MemoryDisassemblyInstruction instruction;

        DisasmInstructionClickSpan(MemoryDisassemblyInstruction instruction) {
            this.instruction = instruction;
        }

        @Override
        public void onClick(View widget) {
            handleTap(widget);
        }

        void handleTap(View widget) {
            if (instruction == null) return;
            selectInstruction(instruction);
            openHexForAddress(instruction.address);
        }

        void handleLongPress(View widget) {
            if (instruction == null) return;
            selectInstruction(instruction);
            showInstructionMenu(widget, instruction);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }
    }


    private void setOutputWithAddressLinks(TextView output, String text) {
        if (output == null) return;
        String value = text == null ? "" : text;
        SpannableStringBuilder sb = new SpannableStringBuilder(value);
        Matcher matcher = ADDRESS_PATTERN.matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            try {
                long address = parseAddress(token);
                sb.setSpan(new DisasmAddressClickSpan(address), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Throwable ignored) {
            }
        }
        setOutput(output, sb);
    }

    private void showAddressMenu(View anchor, long address) {
        if (externalInputActive) return;
        if (anchor == null) return;
        publishCurrentAddress(address, false);
        MemoryOverlayWindowSupport.setInteractive(wm, root, params, true);
        PopupMenu menu = new PopupMenu(overlayContext, anchor);
        menu.getMenu().add(0, 1, 0, "Set current address");
        menu.getMenu().add(0, 2, 1, "Refresh disasm here");
        menu.getMenu().add(0, 3, 2, "Open in Hex");
        menu.getMenu().add(0, 4, 3, "Set Ref Target");
        menu.getMenu().add(0, 5, 4, "Find refs to this address");
        menu.getMenu().add(0, 6, 5, "Copy address");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    publishCurrentAddress(address, true);
                    return true;
                case 2:
                    setAddress(formatHex(address), true);
                    publishCurrentAddress(address, false);
                    return true;
                case 3:
                    openHexForAddress(address);
                    return true;
                case 4:
                    publishCurrentAddress(address, false);
                    setRefTargetField(formatHex(address));
                    return true;
                case 5:
                    publishCurrentAddress(address, false);
                    setRefTargetField(formatHex(address));
                    findReferencesToAddress(address);
                    return true;
                case 6:
                    copyText("PermsTest address", formatHex(address));
                    return true;
                default:
                    return false;
            }
        });
        menu.setOnDismissListener(ignored -> MemoryOverlayWindowSupport.setInteractive(wm, root, params, false));
        try { menu.show(); } catch (Throwable t) { reportStatus("Disassembly address menu failed: " + t.getMessage()); }
    }

    private final class DisasmAddressClickSpan extends ClickableSpan {
        private final long address;

        DisasmAddressClickSpan(long address) {
            this.address = address;
        }

        @Override
        public void onClick(View widget) {
            handleTap(widget);
        }

        void handleTap(View widget) {
            openHexForAddress(address);
        }

        void handleLongPress(View widget) {
            showAddressMenu(widget, address);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }
    }

    private void installDisassemblyOutputTouchHandler(TextView output) {
        if (output == null) return;
        final int touchSlop = ViewConfiguration.get(output.getContext()).getScaledTouchSlop();
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final boolean[] moved = new boolean[1];
        final boolean[] longPressFired = new boolean[1];
        final Object[] downSpan = new Object[1];
        final Runnable[] longPress = new Runnable[1];
        output.setOnTouchListener((v, event) -> {
            if (event == null) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downSpan[0] = findDisassemblySpanAt(output, event.getX(), event.getY());
                if (downSpan[0] == null) return false;
                downX[0] = event.getX();
                downY[0] = event.getY();
                moved[0] = false;
                longPressFired[0] = false;
                longPress[0] = () -> {
                    if (!moved[0] && downSpan[0] != null) {
                        longPressFired[0] = true;
                        try { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); } catch (Throwable ignored) {}
                        handleDisassemblySpanLongPress(v, downSpan[0]);
                    }
                };
                output.postDelayed(longPress[0], ViewConfiguration.getLongPressTimeout());
                return true;
            }
            if (downSpan[0] == null) return false;
            if (action == MotionEvent.ACTION_MOVE) {
                if (!moved[0]) {
                    float dx = Math.abs(event.getX() - downX[0]);
                    float dy = Math.abs(event.getY() - downY[0]);
                    if (dx > touchSlop || dy > touchSlop) {
                        moved[0] = true;
                        if (longPress[0] != null) output.removeCallbacks(longPress[0]);
                        longPress[0] = null;
                    }
                }
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (longPress[0] != null) output.removeCallbacks(longPress[0]);
                longPress[0] = null;
                Object span = downSpan[0];
                downSpan[0] = null;
                if (action == MotionEvent.ACTION_UP && !moved[0] && !longPressFired[0]) {
                    Object upSpan = findDisassemblySpanAt(output, event.getX(), event.getY());
                    if (span == upSpan) handleDisassemblySpanTap(v, span);
                }
                return true;
            }
            return true;
        });
    }

    private Object findDisassemblySpanAt(TextView output, float x, float y) {
        try {
            CharSequence text = output == null ? null : output.getText();
            if (!(text instanceof Spanned)) return null;
            Layout layout = output.getLayout();
            if (layout == null) return null;
            int line = layout.getLineForVertical((int) (y + output.getScrollY() - output.getTotalPaddingTop()));
            int offset = layout.getOffsetForHorizontal(line, x + output.getScrollX() - output.getTotalPaddingLeft());
            int start = Math.max(0, Math.min(offset, text.length()));
            int end = Math.max(start, Math.min(text.length(), start + 1));
            Spanned spanned = (Spanned) text;
            DisasmAddressClickSpan[] addresses = spanned.getSpans(start, end, DisasmAddressClickSpan.class);
            if (addresses != null && addresses.length > 0) return addresses[0];
            DisasmInstructionClickSpan[] instructions = spanned.getSpans(start, end, DisasmInstructionClickSpan.class);
            if (instructions != null && instructions.length > 0) return instructions[0];
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void handleDisassemblySpanTap(View widget, Object span) {
        if (span instanceof DisasmAddressClickSpan) {
            ((DisasmAddressClickSpan) span).handleTap(widget);
        } else if (span instanceof DisasmInstructionClickSpan) {
            ((DisasmInstructionClickSpan) span).handleTap(widget);
        }
    }

    private void handleDisassemblySpanLongPress(View widget, Object span) {
        if (span instanceof DisasmAddressClickSpan) {
            ((DisasmAddressClickSpan) span).handleLongPress(widget);
        } else if (span instanceof DisasmInstructionClickSpan) {
            ((DisasmInstructionClickSpan) span).handleLongPress(widget);
        }
    }

    private TextView outputView() {
        return root == null ? null : root.findViewById(R.id.txtOverlayMemoryDisasmOutput);
    }

    private String textOf(int id) {
        TextView view = root == null ? null : root.findViewById(id);
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
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

    private void setOutput(TextView output, CharSequence text) {
        if (output == null) return;
        CharSequence safeText = text == null ? "" : text;
        String value = safeText.toString();
        if (TextUtils.equals(lastRenderedOutput, value)) return;
        int scrollX = output.getScrollX();
        int scrollY = output.getScrollY();
        lastRenderedOutput = value;
        output.setText(safeText);
        output.post(() -> {
            try { output.scrollTo(scrollX, scrollY); } catch (Throwable ignored) {}
            fitDisassemblyScrollThumbToOutputHeight();
            syncDisassemblyScrollThumbFromOutput(output);
        });
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

    public void setExternalInputActive(boolean active) {
        externalInputActive = active;
        if (active) {
            releaseInputFocus();
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

    private int dp(int value) {
        return MemoryOverlayWindowSupport.dp(appContext, value);
    }

    private void reportStatus(String message) {
        if (statusReporter != null) statusReporter.reportStatus(message);
    }
}
