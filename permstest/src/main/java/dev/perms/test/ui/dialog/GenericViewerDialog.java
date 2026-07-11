package dev.perms.test.ui.dialog;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import dev.perms.test.editor.SourceSyntaxHighlighter;

/**
 * Reusable Material-style text viewer/editor dialog.
 *
 * The dialog uses an explicit title-row close affordance so file/viewer popups do
 * not rely on tapping outside the dialog. Feature controllers should call this
 * helper instead of each building slightly different quick viewers.
 */
public final class GenericViewerDialog {
    public interface SaveHost {
        void onSaved(File file);
        void onError(String label, Throwable error);
    }

    private static final long MAX_EDIT_BYTES = 2L * 1024L * 1024L;

    private GenericViewerDialog() {
    }

    public static void showText(Context context, String title, String subtitle, String text) {
        if (context == null) return;
        try {
            TextView view = new TextView(context);
            int pad = dp(context, 14);
            view.setPadding(pad, pad, pad, pad);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            view.setTypeface(Typeface.MONOSPACE);
            view.setText(text == null ? "" : text);
            view.setTextIsSelectable(true);
            view.setSingleLine(false);

            ScrollView scroll = new ScrollView(context);
            scroll.addView(view, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            AlertDialog[] holder = new AlertDialog[1];
            AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                    .setCustomTitle(buildTitleView(context, title, subtitle, holder))
                    .setView(scroll)
                    .setPositiveButton("Close", null)
                    .create();
            holder[0] = dialog;
            dialog.show();
        } catch (Throwable ignored) {
        }
    }


    public static void showHighlightedText(Context context, String title, String subtitle, String text, String syntax) {
        if (context == null) return;
        try {
            EditText view = new EditText(context);
            int pad = dp(context, 14);
            view.setPadding(pad, pad, pad, pad);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            view.setTypeface(Typeface.MONOSPACE);
            view.setGravity(Gravity.START | Gravity.TOP);
            view.setInputType(InputType.TYPE_NULL);
            view.setKeyListener(null);
            view.setFocusable(false);
            view.setTextIsSelectable(true);
            view.setSingleLine(false);
            view.setHorizontallyScrolling(true);
            view.setMinLines(14);
            view.setMaxLines(28);
            view.setText(text == null ? "" : text);
            view.setSingleLine(false);
            view.setHorizontallyScrolling(true);
            applySyntaxIfRequested(view, syntax);

            ScrollView scroll = new ScrollView(context);
            scroll.addView(view, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            AlertDialog[] holder = new AlertDialog[1];
            AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                    .setCustomTitle(buildTitleView(context, title, subtitle, holder))
                    .setView(scroll)
                    .setPositiveButton("Close", null)
                    .create();
            holder[0] = dialog;
            dialog.show();
        } catch (Throwable ignored) {
            showText(context, title, subtitle, text);
        }
    }

    public static void showHighlightedTextWindow(Context context,
                                                 String title,
                                                 String subtitle,
                                                 String text,
                                                 String syntax,
                                                 String windowStyle,
                                                 String windowFit) {
        if (context == null) return;
        try {
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            int outerPad = dp(context, 10);
            content.setPadding(outerPad, 0, outerPad, outerPad);

            TextView titleView = new TextView(context);
            titleView.setText(TextUtils.isEmpty(title) ? "Viewer" : title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            content.addView(titleView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            if (!TextUtils.isEmpty(subtitle)) {
                TextView subtitleView = new TextView(context);
                subtitleView.setText(subtitle);
                subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                subtitleView.setAlpha(0.78f);
                subtitleView.setSingleLine(false);
                subtitleView.setPadding(0, dp(context, 2), 0, dp(context, 8));
                content.addView(subtitleView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            EditText view = new EditText(context);
            int pad = dp(context, 12);
            view.setPadding(pad, pad, pad, pad);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            view.setTypeface(Typeface.MONOSPACE);
            view.setGravity(Gravity.START | Gravity.TOP);
            view.setInputType(InputType.TYPE_NULL);
            view.setKeyListener(null);
            view.setFocusable(false);
            view.setTextIsSelectable(true);
            view.setSingleLine(false);
            view.setHorizontallyScrolling(true);
            view.setMinLines(18);
            view.setText(text == null ? "" : text);
            applySyntaxIfRequested(view, syntax);

            ScrollView scroll = new ScrollView(context);
            scroll.addView(view, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams scrollLp = MovableDialogChrome.isFullStyle(windowStyle)
                    ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            content.addView(scroll, scrollLp);

            MovableDialogChrome.Chrome chrome = MovableDialogChrome.create(context, content, windowStyle);
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                    .setView(chrome.root);
            if (!MovableDialogChrome.isFullStyle(windowStyle)) {
                builder.setPositiveButton("Close", null);
            }
            AlertDialog dialog = builder.create();
            if (chrome.closeButton != null) chrome.closeButton.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
            MovableDialogChrome.applyWindowStyle(dialog, windowStyle, windowFit);
            MovableDialogChrome.enable(dialog, chrome.dragHandle);
        } catch (Throwable ignored) {
            showHighlightedText(context, title, subtitle, text, syntax);
        }
    }

    public static void showEditableFile(Context context, File file, String title, boolean syntaxXml, SaveHost host) {
        if (context == null || file == null) return;
        try {
            if (!file.isFile()) throw new java.io.IOException("File not found: " + file.getAbsolutePath());
            long size = file.length();
            if (size > MAX_EDIT_BYTES) {
                showText(context,
                        TextUtils.isEmpty(title) ? file.getName() : title,
                        file.getAbsolutePath(),
                        "This file is too large for the quick dialog editor.\n\n"
                                + "Size: " + size + " bytes\n"
                                + "Use the full editor tools for large files.");
                return;
            }

            EditText editor = new EditText(context);
            int pad = dp(context, 10);
            editor.setPadding(pad, pad, pad, pad);
            editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            editor.setTypeface(Typeface.MONOSPACE);
            editor.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setSingleLine(false);
            editor.setHorizontallyScrolling(true);
            editor.setMinLines(14);
            editor.setText(readText(file));
            editor.setSelection(0);
            if (syntaxXml && SourceSyntaxHighlighter.canHighlightLength(editor.length())) {
                SourceSyntaxHighlighter.applyWeb(editor);
            }

            ScrollView scroll = new ScrollView(context);
            scroll.addView(editor, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            AlertDialog[] holder = new AlertDialog[1];
            AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                    .setCustomTitle(buildTitleView(context,
                            TextUtils.isEmpty(title) ? file.getName() : title,
                            file.getAbsolutePath(),
                            holder))
                    .setView(scroll)
                    .setPositiveButton("Save", null)
                    .setNegativeButton("Cancel", null)
                    .create();
            holder[0] = dialog;
            dialog.setOnShowListener(d -> {
                try {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                        try {
                            Editable editable = editor.getText();
                            writeText(file, editable == null ? "" : editable.toString());
                            Toast.makeText(context, "Saved " + file.getName(), Toast.LENGTH_SHORT).show();
                            if (host != null) host.onSaved(file);
                            dialog.dismiss();
                        } catch (Throwable t) {
                            Toast.makeText(context, "Save failed: " + shortError(t), Toast.LENGTH_LONG).show();
                            if (host != null) host.onError("save", t);
                        }
                    });
                } catch (Throwable ignored) {
                }
            });
            dialog.show();
        } catch (Throwable t) {
            if (host != null) host.onError("open", t);
            showText(context, "Viewer", null, "Unable to open file:\n" + shortError(t));
        }
    }

    private static void applySyntaxIfRequested(EditText view, String syntax) {
        if (view == null || TextUtils.isEmpty(syntax)) return;
        try {
            if (!SourceSyntaxHighlighter.canHighlightLength(view.length())) return;
            String normalized = syntax.trim().toLowerCase(java.util.Locale.US);
            if ("smali".equals(normalized)) SourceSyntaxHighlighter.applySmali(view);
            else if ("shell".equals(normalized) || "bash".equals(normalized) || "sh".equals(normalized)) SourceSyntaxHighlighter.applyShell(view);
            else if ("json".equals(normalized)) SourceSyntaxHighlighter.applyJson(view);
            else if ("properties".equals(normalized) || "prop".equals(normalized) || "ini".equals(normalized)) SourceSyntaxHighlighter.applyProperties(view);
            else if ("web".equals(normalized) || "html".equals(normalized) || "css".equals(normalized) || "js".equals(normalized)) SourceSyntaxHighlighter.applyWeb(view);
        } catch (Throwable ignored) {
        }
    }

    private static View buildTitleView(Context context, String title, String subtitle, AlertDialog[] holder) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(context, 22);
        int padV = dp(context, 14);
        row.setPadding(padH, padV, dp(context, 10), dp(context, 8));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(context);
        titleView.setText(TextUtils.isEmpty(title) ? "Viewer" : title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subView = new TextView(context);
            subView.setText(subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            subView.setSingleLine(false);
            subView.setMaxLines(2);
            subView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            textCol.addView(subView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView close = new TextView(context);
        close.setText("✕");
        close.setGravity(Gravity.CENTER);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setContentDescription("Close");
        close.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        close.setOnClickListener(v -> {
            try {
                if (holder != null && holder[0] != null) holder[0].dismiss();
            } catch (Throwable ignored) {
            }
        });
        row.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static String readText(File file) throws java.io.IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), MAX_EDIT_BYTES))) {
            byte[] buf = new byte[32 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String text) throws java.io.IOException {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file, false))) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static String shortError(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.getClass().getSimpleName() : msg;
    }

    private static int dp(Context context, int value) {
        try {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value,
                    context.getResources().getDisplayMetrics());
        } catch (Throwable ignored) {
            return value;
        }
    }
}
