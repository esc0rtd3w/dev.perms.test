package dev.perms.test.packages;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;

import dev.perms.test.apk.ApkDebugToolHelper;

/**
 * Owns package actions that start from an installed package instead of a picked
 * APK file: exporting installed APKs and rebuilding an installed app as a
 * debuggable package.
 */
public final class InstalledPackageActionController {
    public interface Host {
        boolean isSafePackageName(String packageName);
        void appendOutput(String text);
        void executePackageWork(Runnable action);
        void runOnUiThread(Runnable action);
        void clearDebuggablePackageLog();
        void appendDebuggablePackageLog(String text);
        void setDebuggablePackageJobStatus(boolean running, String status);
        boolean isDebuggableUseApktoolSelected();
        void setDebuggablePackageInputs(String sourceLabel, String outputPath, String status);
        void runCreateDebuggablePackage(Uri sourceUri, String sourceLabel, String outputPath, boolean useApktool);
        InstalledPackageExtractor.ExtractedInstalledPackage extractForPublicExport(String packageName, String displayName) throws IOException;
        InstalledPackageExtractor.ExtractedInstalledPackage extractForDebug(String packageName, String displayName) throws IOException;
        void deleteTreeQuietly(File file);
    }

    private final Context context;
    private final Host host;

    public InstalledPackageActionController(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    public void showMakeDebugPackageDialog(String packageName, String label) {
        try {
            final String pkg = packageName == null ? "" : packageName.trim();
            if (TextUtils.isEmpty(pkg)) return;
            final String display = TextUtils.isEmpty(label) ? pkg : label.trim();
            new MaterialAlertDialogBuilder(context)
                    .setTitle("Make Debug Package")
                    .setMessage("Extract installed APKs for " + display + " and rebuild them with android:debuggable=\"true\" using the current Create Debuggable Package logic?")
                    .setPositiveButton("Create", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        createDebuggablePackageFromInstalledApp(pkg, display);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Throwable t) {
            appendOutput("[APK Debug] Failed to show app tray debug dialog: " + t + "\n");
        }
    }

    public void createDebuggablePackageFromInstalledApp(String packageName, String label) {
        try {
            final String pkg = packageName == null ? "" : packageName.trim();
            if (TextUtils.isEmpty(pkg) || host == null) return;
            final String display = TextUtils.isEmpty(label) ? pkg : label.trim();
            final boolean useApktool = host.isDebuggableUseApktoolSelected();

            host.clearDebuggablePackageLog();
            appendOutput("[APK Debug] Extracting installed package: " + pkg + "\n");
            host.setDebuggablePackageJobStatus(true, "Extracting installed package " + display + "...");

            host.executePackageWork(() -> {
                try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
                InstalledPackageExtractor.ExtractedInstalledPackage extracted = null;
                try {
                    extracted = host.extractForDebug(pkg, display);
                    if (extracted == null || extracted.packageFile == null || !extracted.packageFile.isFile()) {
                        final String status = "Failed to extract installed package.";
                        host.setDebuggablePackageJobStatus(false, status);
                        host.appendDebuggablePackageLog("[APK Debug] Failed to extract installed package files for " + pkg + ".\n");
                        return;
                    }

                    final String sourceLabel = extracted.sourceLabel;
                    final String outputPath = ApkDebugToolHelper.defaultOutputPath(sourceLabel);
                    final Uri sourceUri = Uri.fromFile(extracted.packageFile);

                    host.runOnUiThread(() -> host.setDebuggablePackageInputs(
                            sourceLabel,
                            outputPath,
                            "Rebuilding extracted installed package..."));

                    host.appendDebuggablePackageLog("[APK Debug] Extracted " + extracted.apkCount
                            + (extracted.apkCount == 1 ? " APK" : " APKs")
                            + " from " + pkg + ".\n");
                    host.runCreateDebuggablePackage(sourceUri, sourceLabel, outputPath, useApktool);
                } catch (Throwable t) {
                    final String status = "Create Debuggable Package failed. Check the log.";
                    host.setDebuggablePackageJobStatus(false, status);
                    host.appendDebuggablePackageLog("[APK Debug] Installed-package rebuild failed: "
                            + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
                } finally {
                    if (extracted != null && extracted.workDir != null) {
                        try { host.deleteTreeQuietly(extracted.workDir); } catch (Throwable ignored) {}
                    }
                }
            });
        } catch (Throwable t) {
            appendOutput("[APK Debug] Failed to start installed-package rebuild: " + t + "\n");
        }
    }

    public void extractInstalledPackageToPublicFolder(String packageName, String displayName) {
        final String pkg = packageName == null ? "" : packageName.trim();
        if (host == null || !host.isSafePackageName(pkg)) {
            appendOutput("[Extract] Invalid package name.\n");
            return;
        }
        final String display = TextUtils.isEmpty(displayName) ? pkg : displayName.trim();
        appendOutput("[Extract] Extracting installed package: " + pkg + "\n");
        host.executePackageWork(() -> {
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
            try {
                InstalledPackageExtractor.ExtractedInstalledPackage extracted = host.extractForPublicExport(pkg, display);
                if (extracted == null || extracted.workDir == null || !extracted.workDir.isDirectory()) {
                    throw new IOException("Extraction did not create an output directory.");
                }
                final String msg = "[Extract] Extracted " + extracted.apkCount
                        + (extracted.apkCount == 1 ? " APK" : " APKs")
                        + " to " + extracted.workDir.getAbsolutePath() + "\n";
                appendOutput(msg);
                toastOnUi("Extracted package APKs");
            } catch (Throwable t) {
                appendOutput("[Extract] Failed: " + t.getClass().getSimpleName()
                        + (TextUtils.isEmpty(t.getMessage()) ? "" : (": " + t.getMessage())) + "\n");
                toastOnUi("Package extraction failed");
            }
        });
    }

    private void appendOutput(String text) {
        try {
            if (host != null) host.appendOutput(text);
        } catch (Throwable ignored) {
        }
    }

    private void toastOnUi(String message) {
        try {
            if (host == null) return;
            host.runOnUiThread(() -> {
                try { Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {
        }
    }
}
