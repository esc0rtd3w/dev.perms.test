package dev.perms.test.packages;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;

import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.apk.DebuggableApkCreator;
import dev.perms.test.shell.ShellBinaryAssets;

/**
 * Owns the Packages-tab debuggable package rebuild flow after the UI has
 * collected the selected package and output path.
 */
public final class DebuggablePackageCreationController {
    public interface Host {
        File copyUriToExternalDir(Uri uri, String subdir, String filename);
        ToolResult ensureBundledBinaryPublicForCurrentMode(String toolName);
        ToolResult runShellCommandCaptureSync(String command);
        ExportResult exportSignedPackage(File signedPackage, String outputPath);
        File getExternalFilesDir(String type);
        void deleteTreeQuietly(File file);
        void setJobStatus(boolean running, String status);
        void setVisibleStatus(String status);
        void restoreLogIfNeeded();
        void appendLog(String msg);
    }

    public static final class ToolResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ToolResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    public static final class ExportResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ExportResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private final Context context;
    private final Host host;

    public DebuggablePackageCreationController(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    public void runCreateDebuggablePackage(Uri sourceUri, String sourceLabel, String outputPath, boolean useApktool) {
        if (host == null) return;
        host.setJobStatus(true, "Preparing APK patch workspace...");
        cleanupScratch();
        String stagedName = ApkDebugToolHelper.defaultStageFilename(sourceUri, sourceLabel);
        File stagedInput = host.copyUriToExternalDir(sourceUri, "apk_patch_inputs", stagedName);
        if (stagedInput == null || !stagedInput.exists()) {
            failWithStatus("Failed to stage the selected APK.", "[APK Debug] Failed to stage the selected APK.\n");
            return;
        }

        final String apktoolPath;
        if (useApktool) {
            ToolResult apktool = host.ensureBundledBinaryPublicForCurrentMode(ApkDebugToolHelper.TOOL_APKTOOL);
            if (apktool == null || apktool.exitCode != 0) {
                final String msg = apktool == null ? "apktool binary is missing." : apktool.stderr;
                host.setJobStatus(false, "apktool binary was not found.");
                host.appendLog("[APK Debug] apktool binary was not found for this device ABI. Add it to assets/bin/<abi>/apktool and rebuild.\n");
                appendLineIfPresent(msg);
                host.setVisibleStatus("apktool binary was not found.");
                deleteFileQuietly(stagedInput);
                return;
            }
            apktoolPath = ApkDebugToolHelper.PUBLIC_BIN_DIR + "/" + ApkDebugToolHelper.TOOL_APKTOOL;
        } else {
            apktoolPath = null;
        }

        ToolResult zipalign = host.ensureBundledBinaryPublicForCurrentMode(ApkDebugToolHelper.TOOL_ZIPALIGN);
        if (zipalign == null || zipalign.exitCode != 0) {
            final String msg = zipalign == null ? "zipalign binary is missing." : zipalign.stderr;
            host.setJobStatus(false, "zipalign binary was not found.");
            host.appendLog("[APK Debug] zipalign binary was not found for this device ABI. Add it to assets/bin/<abi>/zipalign and rebuild.\n");
            appendLineIfPresent(msg);
            host.setVisibleStatus("zipalign binary was not found.");
            deleteFileQuietly(stagedInput);
            return;
        }
        final String zipalignPath = ApkDebugToolHelper.PUBLIC_BIN_DIR + "/" + ApkDebugToolHelper.TOOL_ZIPALIGN;

        final boolean streamPackage = DebuggableApkCreator.shouldUseStreaming(stagedInput);
        host.setJobStatus(true, "Patching manifest, zipaligning, and signing APK...");
        if (streamPackage) {
            host.appendLog("[APK Debug] Large package detected; using streaming rebuild/sign path.\n");
        }
        host.appendLog(useApktool ? "[APK Debug] Patching manifest with apktool-go.\n" : "[APK Debug] Patching binary AndroidManifest.xml.\n");
        if (useApktool) {
            host.appendLog("[APK Debug] apktool-go logs will be saved under " + ApkDebugToolHelper.APKTOOL_LOG_DIR + "\n");
        }
        host.appendLog("[APK Debug] Zipaligning before official Android APK signing.\n");
        host.setVisibleStatus("Patching manifest and signing APK...");

        File workRoot = host.getExternalFilesDir("apk_debuggable_work");
        if (workRoot == null) {
            failWithStatus("Failed to access APK work directory.", "[APK Debug] Failed to access APK work directory.\n");
            return;
        }
        File workDir = new File(workRoot, "debuggable_" + System.currentTimeMillis());
        DebuggableApkCreator.ToolRunner shellRunner = command -> {
            ToolResult result = host.runShellCommandCaptureSync(command);
            if (result == null) return new DebuggableApkCreator.ToolResult(1, "", "No shell result.");
            return new DebuggableApkCreator.ToolResult(result.exitCode, result.stdout, result.stderr);
        };
        DebuggableApkCreator.Result create = DebuggableApkCreator.create(context, stagedInput, workDir, useApktool, apktoolPath, zipalignPath, shellRunner);
        if (create == null || !create.success || create.signedApk == null || !create.signedApk.isFile()) {
            final String msg = create == null ? "Unknown APK patch failure." : create.message;
            final String failStatus = "Create Debuggable Package failed. Check the log.";
            host.setJobStatus(false, failStatus);
            host.appendLog("[APK Debug] Create Debuggable Package failed: " + msg + "\n");
            if (create != null && !TextUtils.isEmpty(create.detailLog)) {
                host.appendLog(create.detailLog.endsWith("\n") ? create.detailLog : (create.detailLog + "\n"));
            }
            host.setVisibleStatus(failStatus);
            deleteFileQuietly(stagedInput);
            host.deleteTreeQuietly(workDir);
            return;
        }

        ExportResult export = host.exportSignedPackage(create.signedApk, outputPath);
        final boolean finalSuccess = export != null && export.exitCode == 0;
        final String finalStatus = finalSuccess ? ("Created: " + outputPath) : "Create Debuggable Package failed. Check the log.";
        host.setJobStatus(false, finalStatus);
        host.appendLog(finalSuccess
                ? ("[APK Debug] Created debuggable package: " + outputPath + "\n")
                : "[APK Debug] Create Debuggable Package failed while exporting the signed package.\n");
        if (!TextUtils.isEmpty(create.detailLog)) {
            host.appendLog(create.detailLog.endsWith("\n") ? create.detailLog : (create.detailLog + "\n"));
        }
        host.appendLog(create.splitArchive
                ? "[APK Debug] Split archive rebuilt; inner APKs were zipaligned and signed with official Android APK signatures.\n"
                : "[APK Debug] APK was zipaligned and signed with official Android APK signatures.\n");
        if (export != null) {
            appendLineIfPresent(export.stdout);
            appendLineIfPresent(export.stderr);
        }
        deleteFileQuietly(stagedInput);
        host.deleteTreeQuietly(workDir);
        host.setVisibleStatus(finalStatus);
        host.restoreLogIfNeeded();
    }

    private void failWithStatus(String status, String logMessage) {
        host.setJobStatus(false, status);
        host.appendLog(logMessage);
        host.setVisibleStatus(status);
    }

    private void appendLineIfPresent(String msg) {
        if (TextUtils.isEmpty(msg)) return;
        host.appendLog(msg.endsWith("\n") ? msg : (msg + "\n"));
    }

    private void cleanupScratch() {
        try { host.deleteTreeQuietly(host.getExternalFilesDir("apk_debuggable_work")); } catch (Throwable ignored) {}
        try { host.deleteTreeQuietly(host.getExternalFilesDir("apk_patch_inputs")); } catch (Throwable ignored) {}
        try { host.deleteTreeQuietly(host.getExternalFilesDir("debugging_inputs")); } catch (Throwable ignored) {}
        try {
            String cmd = "rm -rf "
                    + shQuote(ShellBinaryAssets.PUBLIC_STAGE_DIR) + "/pkg_* "
                    + shQuote(ShellBinaryAssets.PUBLIC_FILES_DIR) + "/* "
                    + shQuote(ApkDebugToolHelper.PUBLIC_APKPATCH_DIR) + "/* "
                    + "2>/dev/null || true";
            host.runShellCommandCaptureSync(cmd);
        } catch (Throwable ignored) {}
    }

    private static void deleteFileQuietly(File file) {
        try { if (file != null) file.delete(); } catch (Throwable ignored) {}
    }

    private static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}
