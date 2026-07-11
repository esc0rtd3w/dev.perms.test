package dev.perms.test.debugging.mitm;

import android.text.TextUtils;
import android.widget.TextView;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.debug.DebugLog;
import dev.perms.test.debugging.DebuggingUi;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class DebuggingMitmController {
    public interface Host {
        void setDebuggingBusy(boolean busy, String status);
        void appendOutput(String text);
        void runOnUiThread(Runnable action);
        String resolveApktoolCommand() throws IOException;
        ShellResult runShellCommandCaptureSync(String command);
        String exportRebuiltApk(File rebuiltUnsigned, String outputPath, boolean makeDebuggable, File workDir) throws Exception;
        File getWorkRoot(String type);
        String getCurrentDebuggingWorkRoot();
        String getSelectedDebuggingPackage();
        String shQuote(String value);
        void deleteTreeQuietly(File file);
        boolean isDebugOutputEnabled();
    }

    public static final class ShellResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ShellResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private final ActivityMainBinding binding;
    private final ExecutorService executor;
    private final Host host;

    public DebuggingMitmController(ActivityMainBinding binding, ExecutorService executor, Host host) {
        this.binding = binding;
        this.executor = executor;
        this.host = host;
    }

    public void setup() {
        try {
            if (binding == null || binding.tabDebugging == null) return;
            if (binding.tabDebugging.btnMitmApplyPatch != null) {
                binding.tabDebugging.btnMitmApplyPatch.setOnClickListener(v -> runPatchFromUi());
            }
            if (binding.tabDebugging.btnMitmTrustUserCas != null) {
                binding.tabDebugging.btnMitmTrustUserCas.setOnClickListener(v -> runTemplatePatch(
                        "Trust User CAs", true, true, false, false, true, true, false));
            }
            if (binding.tabDebugging.btnMitmAllowCleartext != null) {
                binding.tabDebugging.btnMitmAllowCleartext.setOnClickListener(v -> runTemplatePatch(
                        "Allow Cleartext", false, true, false, false, false, false, false));
            }
            if (binding.tabDebugging.btnTemplateMakeDebuggable != null) {
                binding.tabDebugging.btnTemplateMakeDebuggable.setOnClickListener(v -> runTemplatePatch(
                        "Make Debuggable", false, false, true, false, false, false, false));
            }
            if (binding.tabDebugging.btnTemplateAllowBackup != null) {
                binding.tabDebugging.btnTemplateAllowBackup.setOnClickListener(v -> runTemplatePatch(
                        "Allow Backup", false, false, false, true, false, false, false));
            }
            if (binding.tabDebugging.btnTemplateActivityReport != null) {
                binding.tabDebugging.btnTemplateActivityReport.setOnClickListener(v -> runDecodedApkDebuggingReport(false));
            }
            if (binding.tabDebugging.btnMitmPinningReport != null) {
                binding.tabDebugging.btnMitmPinningReport.setOnClickListener(v -> runDecodedApkPinningReport());
            }
            if (binding.tabDebugging.btnMitmPatchPinning != null) {
                binding.tabDebugging.btnMitmPatchPinning.setOnClickListener(v -> runTemplatePatch(
                        "Patch Cert Pinning", false, false, false, false, false, false, true));
            }
            if (binding.tabDebugging.btnTemplateSecureFlagReport != null) {
                binding.tabDebugging.btnTemplateSecureFlagReport.setOnClickListener(v -> runDecodedApkDebuggingReport(true));
            }
            debug("setup", "MITM/template buttons bound");
        } catch (Throwable t) {
            debugWarn("setup", "failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            host.appendOutput("[Debugging] MITM/patch-template setup failed: " + t.getMessage() + "\n");
        }
    }

    private void runPatchFromUi() {
        DebuggingMitmPatch.FullPatchOptions options = DebuggingMitmPatch.readFullPatchOptions(binding);
        runTemplatePatch("MITM Patch", true, options.allowCleartext, options.makeDebuggable, false,
                options.trustUserCerts, options.trustSystemCerts, options.patchCertificatePinning);
    }

    private void runTemplatePatch(String label, boolean networkConfig, boolean cleartext,
                                  boolean makeDebuggable, boolean allowBackup,
                                  boolean trustUserCerts, boolean trustSystemCerts,
                                  boolean patchCertificatePinning) {
        if (binding == null || binding.tabDebugging == null) return;
        final String sourceApkPath = safeText(binding.tabDebugging.edtSmaliDexInput);
        final boolean debugOutput = makeDebuggable || (binding.tabDebugging.chkSmaliMakeDebugApk != null
                && binding.tabDebugging.chkSmaliMakeDebugApk.isChecked());
        final String outputPath = patchOutputPath(label, debugOutput);
        final String cleanLabel = DebuggingMitmPatch.cleanLabel(label);
        debug("patch", "request label=" + cleanLabel + ", source=" + sourceApkPath + ", output=" + outputPath
                + ", networkConfig=" + networkConfig + ", cleartext=" + cleartext + ", makeDebuggable=" + makeDebuggable
                + ", allowBackup=" + allowBackup + ", trustUser=" + trustUserCerts + ", trustSystem=" + trustSystemCerts
                + ", patchPinning=" + patchCertificatePinning);
        setBusyAndStatus(true, cleanLabel + " running...");
        setStatus(cleanLabel + " running...");
        executor.execute(() -> {
            File workDir = null;
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
            try {
                File sourceApk = DebuggingMitmPatch.requireSingleApkSource(sourceApkPath);
                String apktoolCommand = host.resolveApktoolCommand();
                debug("patch", "source ok path=" + sourceApk.getAbsolutePath() + ", bytes=" + sourceApk.length()
                        + ", apktool=" + apktoolCommand);
                File workRoot = host.getWorkRoot("mitm_patch_work");
                if (workRoot == null) throw new IOException("APK patch work directory is unavailable.");
                workDir = new File(workRoot, "patch_" + System.currentTimeMillis());
                File decodedDir = new File(workDir, "decoded");
                File unsignedApk = new File(workDir, "patched-unsigned.apk");
                if (!workDir.exists() && !workDir.mkdirs()) throw new IOException("Unable to create APK patch work directory.");
                debug("patch", "workDir=" + workDir.getAbsolutePath() + ", decodedDir=" + decodedDir.getAbsolutePath());

                ShellResult decode = host.runShellCommandCaptureSync(apktoolCommand + " d -f -o "
                        + host.shQuote(decodedDir.getAbsolutePath()) + " " + host.shQuote(sourceApk.getAbsolutePath()));
                debug("patch", "decode exit=" + (decode == null ? -1 : decode.exitCode) + ", "
                        + (decode == null ? "no result" : DebugLog.describeLengths(decode.stdout, decode.stderr)));
                if (decode == null || decode.exitCode != 0) {
                    throw new IOException("apktool decode failed: " + (decode == null ? "no result" : trimShellError(decode)));
                }

                PermsTestMitmPatchTool.Options options = DebuggingMitmPatch.buildPatchOptions(
                        networkConfig, cleartext, makeDebuggable, allowBackup, trustUserCerts, trustSystemCerts, patchCertificatePinning);
                PermsTestMitmPatchTool.Result patch = PermsTestMitmPatchTool.patchDecodedProject(decodedDir, options);
                debug("patch", "patch result=" + (patch == null ? "null" : ("success=" + patch.success + ", message=" + patch.message)));
                if (patch == null || !patch.success) {
                    throw new IOException(patch == null ? "Decoded project patch failed." : patch.message);
                }

                ShellResult build = host.runShellCommandCaptureSync(apktoolCommand + " b "
                        + host.shQuote(decodedDir.getAbsolutePath()) + " -o " + host.shQuote(unsignedApk.getAbsolutePath()));
                debug("patch", "build exit=" + (build == null ? -1 : build.exitCode) + ", unsignedExists=" + unsignedApk.isFile()
                        + ", unsignedBytes=" + (unsignedApk.isFile() ? unsignedApk.length() : -1L) + ", "
                        + (build == null ? "no result" : DebugLog.describeLengths(build.stdout, build.stderr)));
                if (build == null || build.exitCode != 0 || !unsignedApk.isFile()) {
                    throw new IOException("apktool build failed: " + (build == null ? "no result" : trimShellError(build)));
                }

                String exportOut = host.exportRebuiltApk(unsignedApk, outputPath, makeDebuggable, workDir);
                debug("patch", "export complete output=" + outputPath + ", exportReportLen=" + (exportOut == null ? 0 : exportOut.length()));
                final String report = DebuggingMitmPatch.patchCompletionReport(cleanLabel, patch, outputPath, exportOut);
                host.runOnUiThread(() -> {
                    setBusyAndStatus(false, cleanLabel + " complete: " + outputPath);
                    setStatus(cleanLabel + " complete: " + outputPath);
                    host.appendOutput(report);
                });
            } catch (Throwable t) {
                final String msg = DebuggingMitmPatch.failureMessage(t);
                debugWarn("patch", cleanLabel + " failed: " + msg);
                host.runOnUiThread(() -> {
                    setBusyAndStatus(false, cleanLabel + " failed.");
                    setStatus(cleanLabel + " failed. Check output.");
                    host.appendOutput("[Debugging] " + cleanLabel + " failed: " + msg + "\n");
                });
            } finally {
                if (workDir != null) {
                    debug("patch", "cleanup workDir=" + workDir.getAbsolutePath());
                    host.deleteTreeQuietly(workDir);
                }
            }
        });
    }

    private void runDecodedApkPinningReport() {
        if (binding == null || binding.tabDebugging == null) return;
        final String sourceApkPath = safeText(binding.tabDebugging.edtSmaliDexInput);
        final String label = "Certificate pinning candidate report";
        debug("report", "request label=" + label + ", source=" + sourceApkPath);
        setBusyAndStatus(true, label + " running...");
        setStatus(label + " running...");
        executor.execute(() -> {
            File workDir = null;
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
            try {
                workDir = createWorkDir("mitm_patch_work", "pinning_report_");
                debug("report", "pinning workDir=" + workDir.getAbsolutePath());
                File decodedDir = decodeSingleApkForReport(sourceApkPath, workDir);
                final String out = "[Debugging] " + label + "\n" + PermsTestMitmPatchTool.buildCertificatePinningReport(decodedDir);
                host.runOnUiThread(() -> {
                    setBusyAndStatus(false, label + " complete.");
                    setStatus(label + " complete. See output.");
                    host.appendOutput(out.endsWith("\n") ? out : out + "\n");
                });
            } catch (Throwable t) {
                final String msg = t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage());
                debugWarn("report", label + " failed: " + msg);
                host.runOnUiThread(() -> {
                    setBusyAndStatus(false, label + " failed.");
                    setStatus(label + " failed. Check output.");
                    host.appendOutput("[Debugging] " + label + " failed: " + msg + "\n");
                });
            } finally {
                if (workDir != null) {
                    debug("report", "cleanup workDir=" + workDir.getAbsolutePath());
                    host.deleteTreeQuietly(workDir);
                }
            }
        });
    }

    private void runDecodedApkDebuggingReport(boolean secureFlagReport) {
        if (binding == null || binding.tabDebugging == null) return;
        final String sourceApkPath = safeText(binding.tabDebugging.edtSmaliDexInput);
        final String label = secureFlagReport ? "Screenshot Secure Flag candidate report" : "Exported Activity Report";
        debug("report", "request label=" + label + ", source=" + sourceApkPath);
        setBusyAndStatus(true, label + " running...");
        setStatus(label + " running...");
        executor.execute(() -> {
            File workDir = null;
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
            try {
                workDir = createWorkDir("mitm_patch_work", "report_");
                debug("report", "debugging report workDir=" + workDir.getAbsolutePath());
                File decodedDir = decodeSingleApkForReport(sourceApkPath, workDir);
                StringBuilder report = new StringBuilder();
                report.append("[Debugging] ").append(label).append('\n');
                if (secureFlagReport) {
                    List<File> candidates = PermsTestMitmPatchTool.findSecureFlagSmaliCandidates(decodedDir);
                    report.append("Secure-flag candidate smali files: ").append(candidates.size()).append('\n');
                    for (File f : candidates) {
                        report.append("candidate: ").append(f.getAbsolutePath().replace(decodedDir.getAbsolutePath() + File.separator, "")).append('\n');
                    }
                    if (candidates.isEmpty()) report.append("No obvious FLAG_SECURE / 0x2000 candidates found.\n");
                } else {
                    report.append(PermsTestMitmPatchTool.buildExportedActivityReport(decodedDir));
                }
                final String out = report.toString();
                host.runOnUiThread(() -> {
                    setBusyAndStatus(false, label + " complete.");
                    setStatus(label + " complete. See output.");
                    host.appendOutput(out.endsWith("\n") ? out : out + "\n");
                });
            } catch (Throwable t) {
                final String msg = t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage());
                debugWarn("report", label + " failed: " + msg);
                host.runOnUiThread(() -> {
                    setBusyAndStatus(false, label + " failed.");
                    setStatus(label + " failed. Check output.");
                    host.appendOutput("[Debugging] " + label + " failed: " + msg + "\n");
                });
            } finally {
                if (workDir != null) {
                    debug("report", "cleanup workDir=" + workDir.getAbsolutePath());
                    host.deleteTreeQuietly(workDir);
                }
            }
        });
    }

    private File createWorkDir(String type, String prefix) throws IOException {
        File workRoot = host.getWorkRoot(type);
        if (workRoot == null) throw new IOException("APK patch work directory is unavailable.");
        File workDir = new File(workRoot, prefix + System.currentTimeMillis());
        if (!workDir.exists() && !workDir.mkdirs()) throw new IOException("Unable to create APK patch work directory.");
        debug("workdir", "created type=" + type + ", path=" + workDir.getAbsolutePath());
        return workDir;
    }

    private File decodeSingleApkForReport(String sourceApkPath, File workDir) throws IOException {
        if (TextUtils.isEmpty(sourceApkPath)) throw new IOException("Choose an APK source first.");
        File sourceApk = new File(sourceApkPath);
        if (!sourceApk.isFile()) throw new IOException("APK source does not exist: " + sourceApkPath);
        String apktoolCommand = host.resolveApktoolCommand();
        File decodedDir = new File(workDir, "decoded");
        ShellResult decode = host.runShellCommandCaptureSync(apktoolCommand + " d -f -o "
                + host.shQuote(decodedDir.getAbsolutePath()) + " " + host.shQuote(sourceApk.getAbsolutePath()));
        debug("report", "decode exit=" + (decode == null ? -1 : decode.exitCode) + ", source=" + sourceApk.getAbsolutePath()
                + ", " + (decode == null ? "no result" : DebugLog.describeLengths(decode.stdout, decode.stderr)));
        if (decode == null || decode.exitCode != 0) {
            throw new IOException("apktool decode failed: " + (decode == null ? "no result" : trimShellError(decode)));
        }
        return decodedDir;
    }

    private String patchOutputPath(String label, boolean debug) {
        String source = safeText(binding == null || binding.tabDebugging == null ? null : binding.tabDebugging.edtSmaliDexInput);
        return DebuggingMitmPatch.outputPath(label, debug, source, host.getCurrentDebuggingWorkRoot(), host.getSelectedDebuggingPackage());
    }

    private void setBusyAndStatus(boolean busy, String status) {
        host.setDebuggingBusy(busy, status);
    }

    private void setStatus(String status) {
        DebuggingUi.setMitmPatchStatus(binding, status);
    }

    private String trimShellError(ShellResult result) {
        return result == null ? "no result" : DebuggingUi.trimShellError(result.exitCode, result.stdout, result.stderr);
    }

    private void debug(String area, String message) {
        if (host == null || !host.isDebugOutputEnabled()) return;
        DebugLog.log(DebugLog.DEFAULT_TAG, "debugging", area, message);
        host.appendOutput(DebugLog.line("debugging", area, message) + "\n");
    }

    private void debugWarn(String area, String message) {
        if (host == null || !host.isDebugOutputEnabled()) return;
        DebugLog.warn(DebugLog.DEFAULT_TAG, "debugging", area, message);
        host.appendOutput(DebugLog.line("debugging", area, message) + "\n");
    }

    private static String safeText(TextView view) {
        CharSequence text = view == null ? null : view.getText();
        return text == null ? "" : text.toString().trim();
    }
}
