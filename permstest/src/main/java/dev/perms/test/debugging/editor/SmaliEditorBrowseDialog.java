package dev.perms.test.debugging.editor;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Small browser dialog for selecting smali/java source files for the internal editor.
 */
public final class SmaliEditorBrowseDialog {
    public interface Host {
        boolean isAllowedSmaliFile(File file);
        void onSmaliFileSelected(File file);
        void onSmaliBrowseError(String label, Throwable error);
    }

    private SmaliEditorBrowseDialog() {}

    public static void show(Context context, File startDir, File baseDir, boolean openAny, Host host) {
        if (context == null) return;
        try {
            final File base = baseDir == null ? startDir : baseDir;
            final File[] current = new File[]{startDir == null ? base : startDir};
            final ArrayList<File> entries = new ArrayList<>();
            final ArrayList<String> labels = new ArrayList<>();

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(context, 12);
            root.setPadding(pad, pad, pad, 0);

            TextView pathView = new TextView(context);
            pathView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            pathView.setSingleLine(false);
            root.addView(pathView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView help = new TextView(context);
            help.setText(openAny
                    ? "Open Any is enabled. Tap folders to browse shared storage, or tap a .smali/.java file to fill Selected source file."
                    : "Tap a folder to browse, or tap a .smali/.java file to fill Selected source file.");
            help.setAlpha(0.80f);
            help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            root.addView(help, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            ListView list = new ListView(context);
            list.setFastScrollEnabled(true);
            list.setDividerHeight(1);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels);
            list.setAdapter(adapter);
            LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(dp(context, 260), Math.min(dp(context, 460), context.getResources().getDisplayMetrics().heightPixels / 2)));
            listParams.topMargin = dp(context, 8);
            root.addView(list, listParams);

            final AlertDialog[] dialogRef = new AlertDialog[1];
            list.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    if (position < 0 || position >= entries.size()) return;
                    File picked = entries.get(position);
                    if (picked == null) return;
                    if (picked.isDirectory()) {
                        current[0] = picked;
                        SmaliEditorFiles.refreshBrowseList(current[0], base, pathView, entries, labels, adapter, openAny);
                        return;
                    }
                    String name = picked.getName();
                    if (TextUtils.isEmpty(name)) return;
                    String lower = name.toLowerCase(Locale.US);
                    if (!lower.endsWith(".smali") && !lower.endsWith(".java")) return;
                    if (!openAny && (host == null || !host.isAllowedSmaliFile(picked))) {
                        Toast.makeText(context, "Refusing to select outside the current debugging source folders", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (host != null) host.onSmaliFileSelected(picked);
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                } catch (Throwable t) {
                    report(host, "browse select", t);
                }
            });

            SmaliEditorFiles.refreshBrowseList(current[0], base, pathView, entries, labels, adapter, openAny);
            AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                    .setTitle("Browse smali/java files")
                    .setView(root)
                    .setNegativeButton("Cancel", null)
                    .create();
            dialogRef[0] = dialog;
            dialog.show();
        } catch (Throwable t) {
            report(host, "browse", t);
        }
    }

    private static void report(Host host, String label, Throwable error) {
        if (host != null) host.onSmaliBrowseError(label, error);
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
