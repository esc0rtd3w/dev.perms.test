package dev.perms.test.files;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import dev.perms.test.ui.IntentGrantUtils;

import java.io.File;
import java.util.Locale;

public final class FilesEntryActions {
    public interface CaptureCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface ShellRunner {
        void run(String command, CaptureCallback callback);
    }

    public interface Callbacks {
        void appendOutput(String text);
        void toast(String message);
        String shellQuote(String value);
        boolean useInternalApkInstall();
    }

    private final Activity activity;
    private final ShellRunner shellRunner;
    private final Callbacks callbacks;

    public FilesEntryActions(Activity activity, ShellRunner shellRunner, Callbacks callbacks) {
        this.activity = activity;
        this.shellRunner = shellRunner;
        this.callbacks = callbacks;
    }

    public void openKnownFile(FileEntry entry) {
        if (entry == null || entry.isDir) return;
        if (FilesBrowserUtils.isPackageArchive(entry.name)) {
            openKnownFileNow(entry, new File(entry.fullPath == null ? "" : entry.fullPath), true);
            return;
        }
        openAssociatedFile(entry);
    }

    public void openPackageWithExternalHandler(FileEntry entry) {
        openWithExternalHandler(entry);
    }

    public void openWithExternalHandler(FileEntry entry) {
        if (entry == null || entry.isDir) return;
        File direct = new File(entry.fullPath == null ? "" : entry.fullPath);
        if (canAppReadFile(direct)) {
            openWithExternalHandlerNow(entry, direct);
            return;
        }

        File cacheDir = new File(activity.getExternalCacheDir(), "file_open");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            appendOutput("[Files] Open With failed: cache directory is not available.\n");
            toast("Cannot prepare file for opening");
            return;
        }
        String safeName = safeCacheName(entry.name);
        File cached = new File(cacheDir, safeName);
        final String cmd = "mkdir -p " + shellQuote(cacheDir.getAbsolutePath())
                + " && cp -f " + shellQuote(entry.fullPath) + " " + shellQuote(cached.getAbsolutePath())
                + " && chmod 644 " + shellQuote(cached.getAbsolutePath());
        appendOutput("[Files] Preparing file for Open With: " + entry.fullPath + "\n");
        runShellCommandCapture(cmd, (exit, out, err) -> activity.runOnUiThread(() -> {
            if (exit == 0 && canAppReadFile(cached)) {
                openWithExternalHandlerNow(entry, cached);
            } else {
                appendOutput("[Files] Open With prepare failed (" + exit + "): " + (err == null ? "" : err.trim()) + "\n");
                toast("Cannot prepare file for opening");
            }
        }));
    }

    public void copyPath(String path) {
        try {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("file path", path));
            toast("Path copied");
        } catch (Throwable t) {
            appendOutput("[Files] Copy path failed: " + t + "\n");
        }
    }

    public void showProperties(FileEntry entry) {
        if (entry == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Path: ").append(entry.fullPath).append('\n');
        sb.append("Type: ").append(entry.isDir ? "Folder" : (entry.isLink ? "Link" : FilesBrowserUtils.kindForName(entry.name))).append('\n');
        if (!entry.isDir) sb.append("Size: ").append(FilesBrowserUtils.formatFileSize(entry.size)).append(" (").append(entry.size).append(" bytes)\n");
        if (entry.modifiedEpoch > 0) sb.append("Modified: ").append(FilesBrowserUtils.formatFileTime(entry.modifiedEpoch)).append('\n');
        new MaterialAlertDialogBuilder(activity)
                .setTitle(entry.name + (entry.isDir ? "/" : ""))
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    public static boolean canAppReadFile(File file) {
        if (file == null || !file.isFile()) return false;
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            if (file.length() == 0) return true;
            return in.read() >= 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void openAssociatedFile(FileEntry entry) {
        File direct = new File(entry.fullPath == null ? "" : entry.fullPath);
        if (canAppReadFile(direct)) {
            openKnownFileNow(entry, direct, false);
            return;
        }

        File cacheDir = new File(activity.getExternalCacheDir(), "file_open");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            appendOutput("[Files] Open failed: cache directory is not available.\n");
            toast("Cannot prepare file for opening");
            return;
        }
        String safeName = safeCacheName(entry.name);
        File cached = new File(cacheDir, safeName);
        final String cmd = "mkdir -p " + shellQuote(cacheDir.getAbsolutePath())
                + " && cp -f " + shellQuote(entry.fullPath) + " " + shellQuote(cached.getAbsolutePath())
                + " && chmod 644 " + shellQuote(cached.getAbsolutePath());
        appendOutput("[Files] Preparing file for associated app: " + entry.fullPath + "\n");
        runShellCommandCapture(cmd, (exit, out, err) -> activity.runOnUiThread(() -> {
            if (exit == 0 && canAppReadFile(cached)) {
                openKnownFileNow(entry, cached, false);
            } else {
                appendOutput("[Files] Open prepare failed (" + exit + "): " + (err == null ? "" : err.trim()) + "\n");
                toast("Cannot prepare file for opening");
            }
        }));
    }

    private void openWithExternalHandlerNow(FileEntry entry, File file) {
        try {
            if (file == null || !file.isFile()) {
                appendOutput("[Files] Open With failed: file is not readable: " + (file == null ? entry.fullPath : file.getAbsolutePath()) + "\n");
                toast("Cannot open this file path");
                return;
            }

            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setDataAndType(uri, mimeForName(entry.name));
            intent.setClipData(ClipData.newUri(activity.getContentResolver(), entry.name == null ? "file" : entry.name, uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            IntentGrantUtils.grantReadForIntent(activity, intent, uri);

            Intent chooser = Intent.createChooser(intent, "Open With...");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            appendOutput("[Files] Opening file with external handler: " + entry.fullPath + "\n");
            activity.startActivity(chooser);
        } catch (Throwable t) {
            appendOutput("[Files] Open With failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            toast("No app could open this file");
        }
    }

    private String safeCacheName(String name) {
        String n = TextUtils.isEmpty(name) ? "file" : name.trim();
        n = n.replace('\\', '_').replace('/', '_').replace('\0', '_');
        if (n.isEmpty() || ".".equals(n) || "..".equals(n)) n = "file";
        return n;
    }

    private void openKnownFileNow(FileEntry entry, File file, boolean packageArchive) {
        try {
            if (file == null || !file.isFile()) {
                appendOutput("[Files] Open failed: file is not readable: " + (file == null ? entry.fullPath : file.getAbsolutePath()) + "\n");
                toast("Cannot open this file path");
                return;
            }

            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file);
            String mime = mimeForName(entry.name);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setDataAndType(uri, mime);
            intent.setClipData(ClipData.newUri(activity.getContentResolver(), entry.name == null ? "file" : entry.name, uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (packageArchive) {
                if (useInternalApkInstall()) {
                    intent.setComponent(new ComponentName(activity.getPackageName(), activity.getPackageName() + ".packages.FileOpenInstallActivity"));
                    appendOutput("[Files] Opening package with internal installer: " + entry.fullPath + "\n");
                } else {
                    // Preserve the original package-open route unless the Files-tab internal install gate is enabled.
                    intent.setComponent(new ComponentName(activity.getPackageName(), activity.getPackageName() + ".FileOpenAlias"));
                    appendOutput("[Files] Opening package with installer alias: " + entry.fullPath + "\n");
                }
                IntentGrantUtils.grantReadForIntent(activity, intent, uri);
                activity.startActivity(intent);
                return;
            }

            IntentGrantUtils.grantReadForIntent(activity, intent, uri);
            appendOutput("[Files] Opening with associated app: " + entry.fullPath + "\n");
            activity.startActivity(intent);
        } catch (Throwable t) {
            appendOutput("[Files] Open failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            toast("No app could open this file");
        }
    }


    private String mimeForName(String name) {
        if (TextUtils.isEmpty(name)) return "application/octet-stream";
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (n.endsWith(".apks") || n.endsWith(".apkm") || n.endsWith(".xapk")) return "application/zip";

        String ext = "";
        int dot = n.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < n.length()) ext = n.substring(dot + 1);
        String mime = TextUtils.isEmpty(ext) ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (!TextUtils.isEmpty(mime)) return mime;

        if (n.endsWith(".log") || n.endsWith(".md") || n.endsWith(".json") || n.endsWith(".xml")
                || n.endsWith(".js") || n.endsWith(".java") || n.endsWith(".go") || n.endsWith(".sh")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private void runShellCommandCapture(String command, CaptureCallback callback) {
        if (shellRunner != null) {
            shellRunner.run(command, callback);
        } else if (callback != null) {
            callback.onComplete(1, "", "shell runner unavailable");
        }
    }

    private void appendOutput(String text) {
        if (callbacks != null) callbacks.appendOutput(text);
    }

    private void toast(String message) {
        if (callbacks != null) callbacks.toast(message);
    }

    private String shellQuote(String value) {
        return callbacks == null ? "''" : callbacks.shellQuote(value);
    }

    private boolean useInternalApkInstall() {
        try {
            return callbacks != null && callbacks.useInternalApkInstall();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
