package dev.perms.test.files;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Owns Files tab copy/move/delete/create/rename mutations. */
public final class FilesMutationActions {
    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface PromptCallback {
        void onText(String value);
    }

    public interface Callbacks {
        FilesPaneState activePane();
        FilesPaneState leftPane();
        FilesPaneState rightPane();
        FilesPaneState otherPane(FilesPaneState pane);
        boolean splitVisible();
        String paneLabel(FilesPaneState pane);
        void appendOutput(String text);
        String shellQuote(String value);
        void runShellCommand(String command, ShellCallback callback);
        void runOnUiThread(Runnable runnable);
        void refreshPane(FilesPaneState pane);
        void updateStatusLine();
        void showTextPromptDialog(String title, String hint, String preset, PromptCallback callback);
    }

    private interface PostOpCallback {
        void onSuccess();
    }

    private final Context context;
    private final FilesClipboardState clipboard;
    private final Callbacks callbacks;

    public FilesMutationActions(Context context, FilesClipboardState clipboard, Callbacks callbacks) {
        this.context = context;
        this.clipboard = clipboard;
        this.callbacks = callbacks;
    }

    public void copyCut(boolean cut) {
        FilesPaneState pane = activePane();
        if (pane == null || pane.selected == null) {
            appendOutput("[Files] Select a file/folder first.\n");
            return;
        }
        clipboard.set(pane.selected.fullPath, cut, pane == rightPane(), pane.selected.name + (pane.selected.isDir ? "/" : ""));
        appendOutput("[Files] " + (cut ? "Cut" : "Copied") + ": " + clipboard.path + "\n");
        updateStatusLine();
    }

    public void paste() {
        paste(activePane());
    }

    public void paste(FilesPaneState pane) {
        if (pane == null) return;
        if (!clipboard.hasEntry()) {
            appendOutput("[Files] Clipboard is empty.\n");
            updateStatusLine();
            return;
        }
        String baseName = clipboard.path;
        int idx = baseName.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < baseName.length()) baseName = baseName.substring(idx + 1);

        String dst = pane.cwd;
        if (!dst.endsWith("/")) dst += "/";
        dst += baseName;

        final String op = clipboard.cut ? "Move" : "Copy";
        final String dir = (clipboard.fromRight ? "Right" : "Left") + " -> " + paneLabel(pane);
        appendOutput("[Files] " + op + " (" + dir + ") into: " + pane.cwd + "\n");

        copyMovePathToPane(clipboard.path, clipboard.cut, pane, op, dst, () -> {
            if (clipboard.cut) {
                clipboard.clear();
            }
        });
    }

    public void copyMoveEntryToPane(FileEntry entry, FilesPaneState sourcePane, FilesPaneState targetPane, boolean move) {
        if (entry == null || sourcePane == null || targetPane == null) return;
        if (sourcePane == targetPane) {
            copyCut(move);
            return;
        }
        String dst = targetPane.cwd;
        if (!dst.endsWith("/")) dst += "/";
        dst += entry.name;
        final String op = move ? "Move" : "Copy";
        appendOutput("[Files] " + op + " (" + paneLabel(sourcePane) + " -> " + paneLabel(targetPane) + "): "
                + entry.fullPath + " -> " + dst + "\n");
        copyMovePathToPane(entry.fullPath, move, targetPane, op, dst, () -> {
            if (move) {
                sourcePane.selected = null;
                refreshPane(sourcePane);
            }
        });
    }

    public void confirmDelete(FilesPaneState pane, FileEntry entry) {
        if (pane == null || entry == null) {
            appendOutput("[Files] Select a file/folder first.\n");
            return;
        }
        String title = entry.isDir ? "Delete folder?" : "Delete file?";
        String name = entry.name + (entry.isDir ? "/" : "");
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage("Delete " + name + "?\n\n" + entry.fullPath)
                .setNegativeButton("No", null)
                .setPositiveButton("Yes", (dialog, which) -> delete(pane, entry))
                .show();
    }

    public void delete() {
        FilesPaneState pane = activePane();
        FileEntry entry = pane == null ? null : pane.selected;
        delete(pane, entry);
    }

    public void delete(FilesPaneState pane, FileEntry entry) {
        if (pane == null || entry == null) {
            appendOutput("[Files] Select a file/folder first.\n");
            return;
        }
        pane.selected = entry;
        final String target = entry.fullPath;
        final String side = pane == rightPane() ? "Right" : "Left";
        appendOutput("[Files] Delete (" + side + "): " + target + "\n");
        final String cmd = "rm -rf " + shellQuote(target);

        final int seq = ++pane.refreshSeq;
        final String requestedCwd = pane.cwd;

        runShellCommand(cmd, (exit, out, err) -> runOnUiThread(() -> {
            try {
                if (pane.refreshSeq != seq) return;
                if (!TextUtils.equals(requestedCwd, pane.cwd)) return;
            } catch (Throwable ignored) {}
            if (exit == 0) {
                appendOutput("[Files] Deleted: " + target + "\n");
                try {
                    if (pane.entries != null) {
                        for (int i = 0; i < pane.entries.size(); i++) {
                            FileEntry fe = pane.entries.get(i);
                            if (fe != null && target.equals(fe.fullPath)) {
                                pane.entries.remove(i);
                                pane.adapter.notifyDataSetChanged();
                                break;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
                pane.selected = null;
                refreshPane(pane);
                if (splitVisible()) {
                    FilesPaneState left = leftPane();
                    FilesPaneState right = rightPane();
                    if (left != null) refreshPane(left);
                    if (right != null) refreshPane(right);
                }
            } else {
                appendOutput("[Files] Delete failed (" + exit + "): " + (err == null ? "" : err) + "\n");
            }
            updateStatusLine();
        }));
    }

    public void mkdir() {
        FilesPaneState pane = activePane();
        if (pane == null) return;

        showTextPromptDialog("New Folder", "Folder name", "", name -> {
            if (name == null) return;
            name = name.trim();
            if (!FilesBrowserUtils.isSafeChildName(name)) {
                appendOutput("[Files] Invalid folder name.\n");
                return;
            }

            final String dst = (pane.cwd.endsWith("/") ? pane.cwd : (pane.cwd + "/")) + name;

            final String side = pane == rightPane() ? "Right" : "Left";
            appendOutput("[Files] New Folder (" + side + "): " + dst + "\n");
            final String cmd = "mkdir -p " + shellQuote(dst);

            final int seq = ++pane.refreshSeq;
            final String requestedCwd = pane.cwd;

            runShellCommand(cmd, (exit, out, err) -> runOnUiThread(() -> {
                try {
                    if (pane.refreshSeq != seq) return;
                    if (!TextUtils.equals(requestedCwd, pane.cwd)) return;
                } catch (Throwable ignored) {}
                if (exit == 0) {
                    appendOutput("[Files] Created folder: " + dst + "\n");
                    refreshPane(pane);
                } else {
                    appendOutput("[Files] mkdir failed (" + exit + "): " + (err == null ? "" : err) + "\n");
                }
                updateStatusLine();
            }));
        });
    }

    public void rename() {
        FilesPaneState pane = activePane();
        if (pane == null || pane.selected == null) {
            appendOutput("[Files] Select a file/folder first.\n");
            return;
        }
        final String src = pane.selected.fullPath;
        String baseName = pane.selected.name;

        showTextPromptDialog("Rename", "New name", baseName, name -> {
            if (name == null) return;
            name = name.trim();
            if (!FilesBrowserUtils.isSafeChildName(name)) {
                appendOutput("[Files] Invalid file name.\n");
                return;
            }

            String dstDir = pane.cwd;
            if (!dstDir.endsWith("/")) dstDir += "/";
            final String dst = dstDir + name;

            final String side = pane == rightPane() ? "Right" : "Left";
            appendOutput("[Files] Rename (" + side + "): " + src + " -> " + dst + "\n");
            final String cmd = "mv " + shellQuote(src) + " " + shellQuote(dst);

            final int seq = ++pane.refreshSeq;
            final String requestedCwd = pane.cwd;

            runShellCommand(cmd, (exit, out, err) -> runOnUiThread(() -> {
                try {
                    if (pane.refreshSeq != seq) return;
                    if (!TextUtils.equals(requestedCwd, pane.cwd)) return;
                } catch (Throwable ignored) {}
                if (exit == 0) {
                    appendOutput("[Files] Renamed: " + src + " -> " + dst + "\n");
                    pane.selected = null;
                    refreshPane(pane);
                } else {
                    appendOutput("[Files] Rename failed (" + exit + "): " + (err == null ? "" : err) + "\n");
                }
                updateStatusLine();
            }));
        });
    }

    private void copyMovePathToPane(String srcPath, boolean move, FilesPaneState targetPane, String op, String dst, PostOpCallback onSuccess) {
        if (TextUtils.isEmpty(srcPath) || targetPane == null || TextUtils.isEmpty(dst)) return;

        // Prefer shell primitives through the active backend. With Shizuku, this can operate on
        // locations that the app process itself cannot read; Java file APIs stay fallback-only.
        final String cmd = ""
                + "SRC=" + shellQuote(srcPath) + "; "
                + "DST=" + shellQuote(dst) + "; "
                + "if [ -e \"$DST\" ]; then "
                + "  i=1; while [ -e \"$DST.$i\" ]; do i=$((i+1)); done; DST=\"$DST.$i\"; "
                + "fi; "
                + (move
                ? "(mv \"$SRC\" \"$DST\" || toybox mv \"$SRC\" \"$DST\")"
                : "if [ -d \"$SRC\" ]; then "
                + "  (cp -a \"$SRC\" \"$DST\" || cp -pR \"$SRC\" \"$DST\" || toybox cp -a \"$SRC\" \"$DST\" || toybox cp -pR \"$SRC\" \"$DST\"); "
                + "else "
                + "  (cp -p \"$SRC\" \"$DST\" || cp \"$SRC\" \"$DST\" || toybox cp -p \"$SRC\" \"$DST\" || toybox cp \"$SRC\" \"$DST\"); "
                + "fi");

        final int seq = ++targetPane.refreshSeq;
        final String requestedCwd = targetPane.cwd;

        runShellCommand(cmd, (exit, out, err) -> runOnUiThread(() -> {
            try {
                if (targetPane.refreshSeq != seq) return;
                if (!TextUtils.equals(requestedCwd, targetPane.cwd)) return;
            } catch (Throwable ignored) {}
            if (exit == 0) {
                appendOutput("[Files] " + (move ? "Moved" : "Copied") + " OK.\n");
                if (onSuccess != null) onSuccess.onSuccess();
                refreshPane(targetPane);
                FilesPaneState left = leftPane();
                FilesPaneState right = rightPane();
                if (left != null) refreshPane(left);
                if (splitVisible() && right != null) refreshPane(right);
            } else {
                appendOutput("[Files] " + op + " failed (" + exit + "): " + (err == null ? "" : err) + "\n");
                if (out != null && !out.trim().isEmpty()) appendOutput("[Files] stdout: " + out.trim() + "\n");
            }
            updateStatusLine();
        }));
    }

    private FilesPaneState activePane() {
        return callbacks == null ? null : callbacks.activePane();
    }

    private FilesPaneState leftPane() {
        return callbacks == null ? null : callbacks.leftPane();
    }

    private FilesPaneState rightPane() {
        return callbacks == null ? null : callbacks.rightPane();
    }

    private boolean splitVisible() {
        return callbacks != null && callbacks.splitVisible();
    }

    private String paneLabel(FilesPaneState pane) {
        return callbacks == null ? "Left" : callbacks.paneLabel(pane);
    }

    private void appendOutput(String text) {
        if (callbacks != null) callbacks.appendOutput(text);
    }

    private String shellQuote(String value) {
        return callbacks == null ? "''" : callbacks.shellQuote(value);
    }

    private void runShellCommand(String command, ShellCallback callback) {
        if (callbacks != null) callbacks.runShellCommand(command, callback);
    }

    private void runOnUiThread(Runnable runnable) {
        if (callbacks != null && runnable != null) callbacks.runOnUiThread(runnable);
    }

    private void refreshPane(FilesPaneState pane) {
        if (callbacks != null) callbacks.refreshPane(pane);
    }

    private void updateStatusLine() {
        if (callbacks != null) callbacks.updateStatusLine();
    }

    private void showTextPromptDialog(String title, String hint, String preset, PromptCallback callback) {
        if (callbacks != null) callbacks.showTextPromptDialog(title, hint, preset, callback);
    }
}
