package dev.perms.test.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import dev.perms.test.ui.FastScrollOverlay;

/**
 * Shared source editor facade for small and large text files.
 *
 * Callers keep a single editor surface. Internally, normal-size files can use the existing
 * EditText path, while large files use a line-virtualized RecyclerView path to avoid UI-thread
 * stalls from giant setText/layout/span operations.
 */
public final class VirtualTextEditorController {
    public interface Host {
        void onStatus(String status);
        void onError(String label, Throwable error);
    }

    public enum Mode {
        TEXT,
        VIRTUAL
    }

    private final Context context;
    private final EditText textEditor;
    private final TextInputLayout textInputLayout;
    private final RecyclerView virtualList;
    private final View textFastScrollTouch;
    private final View textFastScrollThumb;
    private final Host host;
    private final VirtualTextLineAdapter adapter = new VirtualTextLineAdapter();

    private Mode mode = Mode.TEXT;
    private File currentFile;
    private VirtualTextDocument document;
    private View fastScrollTarget;

    public VirtualTextEditorController(Context context,
                                       EditText textEditor,
                                       TextInputLayout textInputLayout,
                                       RecyclerView virtualList,
                                       View textFastScrollTouch,
                                       View textFastScrollThumb,
                                       Host host) {
        this.context = context;
        this.textEditor = textEditor;
        this.textInputLayout = textInputLayout;
        this.virtualList = virtualList;
        this.textFastScrollTouch = textFastScrollTouch;
        this.textFastScrollThumb = textFastScrollThumb;
        this.host = host;
        setupVirtualList();
    }

    public void setLineFormatter(VirtualTextLineAdapter.LineFormatter formatter) {
        adapter.setFormatter(formatter);
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isTextMode() {
        return mode == Mode.TEXT;
    }

    public boolean isVirtualMode() {
        return mode == Mode.VIRTUAL;
    }

    public String getText() {
        if (mode == Mode.VIRTUAL) {
            return document == null ? "" : document.joinText();
        }
        return textEditor == null || textEditor.getText() == null ? "" : textEditor.getText().toString();
    }

    public void showText(File file, String text) {
        currentFile = file;
        document = null;
        mode = Mode.TEXT;
        adapter.setDocument(null);
        bindFastScroll(textEditor);
        if (textInputLayout != null) textInputLayout.setVisibility(View.VISIBLE);
        if (textEditor != null) {
            textEditor.setVisibility(View.VISIBLE);
            textEditor.setText(text == null ? "" : text);
        }
        if (virtualList != null) virtualList.setVisibility(View.GONE);
        setFastScrollVisibility(View.VISIBLE);
    }

    public void showDocument(VirtualTextDocument document, int lineHint) {
        this.document = document;
        this.currentFile = document == null ? null : document.getSourceFile();
        this.mode = Mode.VIRTUAL;
        if (textInputLayout != null) textInputLayout.setVisibility(View.GONE);
        if (textEditor != null) {
            textEditor.setText("");
            textEditor.setVisibility(View.GONE);
        }
        adapter.setDocument(document);
        if (virtualList != null) {
            virtualList.setVisibility(View.VISIBLE);
            int targetLine = Math.max(0, lineHint - 1);
            if (targetLine < adapter.getItemCount()) virtualList.scrollToPosition(targetLine);
            try { virtualList.requestFocus(); } catch (Throwable ignored) {}
        }
        bindFastScroll(virtualList);
        setFastScrollVisibility(View.VISIBLE);
    }

    public void clear() {
        currentFile = null;
        document = null;
        adapter.setDocument(null);
        mode = Mode.TEXT;
        bindFastScroll(textEditor);
        if (textEditor != null) {
            textEditor.setText("");
            textEditor.setVisibility(View.VISIBLE);
        }
        if (textInputLayout != null) textInputLayout.setVisibility(View.VISIBLE);
        if (virtualList != null) virtualList.setVisibility(View.GONE);
        setFastScrollVisibility(View.VISIBLE);
    }

    public void writeTo(File file) throws IOException {
        File target = file == null ? currentFile : file;
        if (target == null) throw new IOException("Missing output file.");
        if (mode == Mode.VIRTUAL) {
            if (document == null) throw new IOException("No document is loaded.");
            document.writeTo(target);
            return;
        }
        String text = textEditor == null || textEditor.getText() == null ? "" : textEditor.getText().toString();
        writeUtf8(target, text);
    }

    private static void writeUtf8(File file, String text) throws IOException {
        File canonical = file.getCanonicalFile();
        File parent = canonical.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create output folder: " + parent.getAbsolutePath());
        }
        File temp = File.createTempFile(canonical.getName(), ".tmp", parent);
        boolean moved = false;
        try {
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(temp, false))) {
                out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            }
            VirtualTextDocument.replaceFile(temp, canonical);
            moved = true;
        } finally {
            if (!moved) {
                try { temp.delete(); } catch (Throwable ignored) {}
            }
        }
    }

    private void setupVirtualList() {
        if (virtualList == null) return;
        virtualList.setLayoutManager(new LinearLayoutManager(context));
        virtualList.setAdapter(adapter);
        virtualList.setHasFixedSize(false);
        virtualList.setNestedScrollingEnabled(true);
        virtualList.setVerticalScrollBarEnabled(false);
        virtualList.setHorizontalScrollBarEnabled(true);
        adapter.setLineClickListener(this::editLine);
    }

    private void editLine(int lineIndex, String line) {
        if (lineIndex < 0 || document == null) return;
        try {
            EditText input = new EditText(context);
            input.setTypeface(Typeface.MONOSPACE);
            input.setText(line == null ? "" : line);
            input.setSingleLine(false);
            input.setMinLines(3);
            input.setMaxLines(10);
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            int pad = dp(16);
            input.setPadding(pad, pad, pad, pad);
            if (input.getText() != null) input.setSelection(input.getText().length());

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("Edit line " + (lineIndex + 1))
                    .setView(input)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Apply", (d, which) -> {
                        try {
                            document.setLine(lineIndex, input.getText() == null ? "" : input.getText().toString());
                            adapter.notifyLineChanged(lineIndex);
                            if (host != null) host.onStatus("Edited line " + (lineIndex + 1) + ". Press Save to write changes.");
                        } catch (Throwable t) {
                            if (host != null) host.onError("edit line", t);
                        }
                    })
                    .create();
            dialog.setOnShowListener(d -> {
                try { dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); } catch (Throwable ignored) {}
            });
            dialog.show();
        } catch (Throwable t) {
            if (host != null) host.onError("edit line", t);
        }
    }

    private void bindFastScroll(View target) {
        try {
            if (target == null || textFastScrollTouch == null || textFastScrollThumb == null) return;
            if (fastScrollTarget == target) return;
            fastScrollTarget = target;
            FastScrollOverlay.attach(target, textFastScrollTouch, textFastScrollThumb);
        } catch (Throwable ignored) {
        }
    }

    private void setFastScrollVisibility(int visibility) {
        try { if (textFastScrollTouch != null) textFastScrollTouch.setVisibility(visibility); } catch (Throwable ignored) {}
        try { if (textFastScrollThumb != null) textFastScrollThumb.setVisibility(View.GONE); } catch (Throwable ignored) {}
    }

    private int dp(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
