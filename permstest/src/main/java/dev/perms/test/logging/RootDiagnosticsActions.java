package dev.perms.test.logging;

import android.app.Activity;
import android.os.Environment;
import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.perms.test.databinding.TabLoggingBinding;
import dev.perms.test.storage.StorageAccessController;

/** Root-only Android diagnostic backup and cleanup actions for the Logging tab. */
public final class RootDiagnosticsActions {
    private static final long CHECK_TIMEOUT_MS = 20000L;
    private static final long DIAGNOSTIC_TIMEOUT_MS = 180000L;

    private final Activity activity;
    private final TabLoggingBinding binding;
    private final OutputAppender outputAppender;
    private final LastSavedFileSetter lastSavedFileSetter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RootDiagnosticsActions");
        t.setDaemon(true);
        return t;
    });

    public RootDiagnosticsActions(Activity activity,
                                  TabLoggingBinding binding,
                                  OutputAppender outputAppender,
                                  LastSavedFileSetter lastSavedFileSetter) {
        this.activity = activity;
        this.binding = binding;
        this.outputAppender = outputAppender;
        this.lastSavedFileSetter = lastSavedFileSetter;
    }

    public void checkRoot() {
        runRootTask("Checking root...", () -> {
            RootCommandResult result = runSu("id; id -u; whoami", CHECK_TIMEOUT_MS);
            boolean ok = isRootResult(result);
            StringBuilder out = new StringBuilder();
            out.append("[Root Diagnostics] Root check exit=").append(result.exitCode).append('\n');
            appendCommandText(out, result);
            out.append(ok ? "[Root Diagnostics] Root access verified.\n" : "[Root Diagnostics] Root access was not granted.\n");
            finishOnUi(ok ? "Root diagnostics: root access verified." : "Root diagnostics: root unavailable or denied.", out.toString(), null, ok ? "Root verified" : "Root unavailable");
        });
    }

    public void backup() {
        File summary = buildBackupSummaryFile();
        if (summary == null) return;
        runBackup(summary, false, null);
    }

    public void confirmClear() {
        if (activity == null) return;
        activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                .setTitle("Clear Android Diagnostics")
                .setMessage("This root-only action clears ANR traces, tombstones, and DropBox crash records where Android allows it. Backup first unless you already saved what you need.")
                .setPositiveButton("Backup Then Clear", (dialog, which) -> {
                    File summary = buildBackupSummaryFile();
                    if (summary != null) runBackup(summary, true, () -> runClear(false));
                })
                .setNegativeButton("Clear Only", (dialog, which) -> runClear(false))
                .setNeutralButton("Cancel", null)
                .show());
    }

    private void runBackup(File summaryFile, boolean chained, Runnable afterSuccess) {
        if (summaryFile == null) return;
        File dir = summaryFile.getParentFile();
        if (dir == null) return;
        final String script = buildBackupScript(dir.getAbsolutePath());
        runRootTask(chained ? "Backing up root diagnostics before clear..." : "Backing up root diagnostics...", () -> {
            RootCommandResult result = runSu(script, DIAGNOSTIC_TIMEOUT_MS);
            boolean ok = result.exitCode == 0 && summaryFile.exists();
            StringBuilder out = new StringBuilder();
            out.append("[Root Diagnostics] Backup exit=").append(result.exitCode).append('\n');
            out.append("[Root Diagnostics] Backup folder: ").append(dir.getAbsolutePath()).append('\n');
            appendCommandText(out, result);
            if (lastSavedFileSetter != null && summaryFile.exists()) lastSavedFileSetter.setFile(summaryFile);
            if (!ok) {
                out.append("[Root Diagnostics] Backup did not complete. Clear was not started.\n");
                finishOnUi("Root diagnostics: backup failed.", out.toString(), summaryFile.exists() ? summaryFile : null, "Backup failed");
                return;
            }
            if (afterSuccess != null) {
                finishOnUi("Root diagnostics: backup complete; clearing next...", out.toString(), summaryFile, "Backup complete");
                afterSuccess.run();
            } else {
                finishOnUi("Root diagnostics: backup complete.", out.toString(), summaryFile, "Root diagnostics backed up");
            }
        });
    }

    private void runClear(boolean silent) {
        runRootTask("Clearing root diagnostics...", () -> {
            RootCommandResult result = runSu(buildClearScript(), DIAGNOSTIC_TIMEOUT_MS);
            boolean ok = result.exitCode == 0;
            StringBuilder out = new StringBuilder();
            out.append("[Root Diagnostics] Clear exit=").append(result.exitCode).append('\n');
            appendCommandText(out, result);
            out.append(ok ? "[Root Diagnostics] Clear completed.\n" : "[Root Diagnostics] Clear failed or root was denied.\n");
            finishOnUi(ok ? "Root diagnostics: clear complete." : "Root diagnostics: clear failed or denied.", out.toString(), null, silent ? null : (ok ? "Diagnostics cleared" : "Clear failed"));
        });
    }

    private File buildBackupSummaryFile() {
        if (activity == null) return null;
        if (!StorageAccessController.ensureSharedStorageWriteAccess(
                activity,
                "Root Diagnostics Backup",
                "/sdcard/dev.perms.test/logs/root_diagnostics",
                outputAppender == null ? null : outputAppender::append)) {
            return null;
        }
        File base = new File(Environment.getExternalStorageDirectory(), "dev.perms.test/logs/root_diagnostics");
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = new File(base, "root_diag_" + ts);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "summary.txt");
    }

    private void runRootTask(String status, Runnable task) {
        setBusy(true, status);
        append("[Root Diagnostics] " + status + "\n");
        executor.execute(() -> {
            try {
                if (task != null) task.run();
            } catch (Throwable t) {
                finishOnUi("Root diagnostics: failed.", "[Root Diagnostics] Failed: " + t + "\n", null, "Root diagnostics failed");
            } finally {
                setBusy(false, null);
            }
        });
    }

    private void finishOnUi(String status, String output, File savedFile, String toast) {
        Activity a = activity;
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (!TextUtils.isEmpty(status) && binding != null && binding.txtRootDiagnosticsStatus != null) {
                binding.txtRootDiagnosticsStatus.setText(status);
            }
            if (!TextUtils.isEmpty(output)) append(output);
            if (savedFile != null && savedFile.exists() && lastSavedFileSetter != null) {
                lastSavedFileSetter.setFile(savedFile);
            }
            if (!TextUtils.isEmpty(toast)) Toast.makeText(a, toast, Toast.LENGTH_SHORT).show();
        });
    }

    private void setBusy(boolean busy, String status) {
        Activity a = activity;
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (binding != null) {
                if (binding.btnRootDiagnosticsCheck != null) binding.btnRootDiagnosticsCheck.setEnabled(!busy);
                if (binding.btnRootDiagnosticsBackup != null) binding.btnRootDiagnosticsBackup.setEnabled(!busy);
                if (binding.btnRootDiagnosticsClear != null) binding.btnRootDiagnosticsClear.setEnabled(!busy);
                if (!TextUtils.isEmpty(status) && binding.txtRootDiagnosticsStatus != null) {
                    binding.txtRootDiagnosticsStatus.setText("Root diagnostics: " + status);
                }
            }
        });
    }

    private RootCommandResult runSu(String script, long timeoutMs) {
        long start = SystemClock.uptimeMillis();
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", script).redirectErrorStream(false).start();
            final Process p = process;
            final StringBuilder stdout = new StringBuilder();
            final StringBuilder stderr = new StringBuilder();
            Thread outReader = new Thread(() -> readStream(p.getInputStream(), stdout), "RootDiagStdout");
            Thread errReader = new Thread(() -> readStream(p.getErrorStream(), stderr), "RootDiagStderr");
            outReader.setDaemon(true);
            errReader.setDaemon(true);
            outReader.start();
            errReader.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                try { process.destroy(); } catch (Throwable ignored) {}
                return new RootCommandResult(-998, stdout.toString(), stderr + "\ntimeout", start);
            }
            try { outReader.join(300L); } catch (Throwable ignored) {}
            try { errReader.join(300L); } catch (Throwable ignored) {}
            return new RootCommandResult(process.exitValue(), stdout.toString(), stderr.toString(), start);
        } catch (Throwable t) {
            return new RootCommandResult(-997, "", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()), start);
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean isRootResult(RootCommandResult result) {
        if (result == null) return false;
        String combined = (result.output + "\n" + result.error).toLowerCase(Locale.US);
        if (combined.contains("uid=0")) return true;
        boolean zero = false;
        boolean root = false;
        for (String line : combined.split("\\r?\\n")) {
            String clean = line == null ? "" : line.trim();
            if ("0".equals(clean)) zero = true;
            if ("root".equals(clean)) root = true;
        }
        return result.exitCode == 0 && zero && root;
    }

    private static String buildBackupScript(String outDir) {
        String out = shellSingleQuote(outDir);
        return "OUT=" + out + "; "
                + "mkdir -p \"$OUT\" \"$OUT/anr\" \"$OUT/tombstones\" \"$OUT/dropbox\"; "
                + "SUMMARY=\"$OUT/summary.txt\"; "
                + "{ echo 'Root Android diagnostics backup'; date; id; echo; "
                + "echo '[paths]'; "
                + "for d in /data/anr /data/tombstones /data/system/dropbox; do echo \"$d\"; ls -la \"$d\" 2>&1 | head -200; echo; done; } > \"$SUMMARY\" 2>&1; "
                + "cp -R -p /data/anr/. \"$OUT/anr/\" >> \"$SUMMARY\" 2>&1 || true; "
                + "cp -R -p /data/tombstones/. \"$OUT/tombstones/\" >> \"$SUMMARY\" 2>&1 || true; "
                + "cp -R -p /data/system/dropbox/. \"$OUT/dropbox/\" >> \"$SUMMARY\" 2>&1 || true; "
                + "logcat -b all -d -v threadtime > \"$OUT/logcat_all.txt\" 2>&1 || true; "
                + "dumpsys dropbox --print > \"$OUT/dropbox_dump.txt\" 2>&1 || true; "
                + "dumpsys activity anr > \"$OUT/activity_anr.txt\" 2>&1 || true; "
                + "dumpsys tombstoned > \"$OUT/tombstoned.txt\" 2>&1 || true; "
                + "chmod -R a+rX \"$OUT\" 2>/dev/null || true; "
                + "echo '[done]' >> \"$SUMMARY\"; ls -la \"$OUT\" >> \"$SUMMARY\" 2>&1; ";
    }

    private static String buildClearScript() {
        return "echo 'Root Android diagnostics clear'; date; id; "
                + "for d in /data/anr /data/tombstones /data/system/dropbox; do "
                + "if [ -d \"$d\" ]; then echo \"Clearing $d\"; find \"$d\" -mindepth 1 -maxdepth 1 -exec rm -rf {} \\; 2>&1; else echo \"Missing $d\"; fi; "
                + "done; echo '[done]'";
    }

    private static String shellSingleQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void readStream(InputStream stream, StringBuilder out) {
        if (stream == null || out == null) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line).append('\n');
        } catch (Throwable ignored) {
        }
    }

    private static void appendCommandText(StringBuilder out, RootCommandResult result) {
        if (out == null || result == null) return;
        if (!TextUtils.isEmpty(result.output)) out.append(result.output);
        if (!TextUtils.isEmpty(result.error)) out.append(result.error);
        if (!result.output.endsWith("\n") && !result.error.endsWith("\n")) out.append('\n');
    }

    private void append(String text) {
        if (outputAppender != null) outputAppender.append(text);
    }

    public interface OutputAppender {
        void append(String text);
    }

    public interface LastSavedFileSetter {
        void setFile(File file);
    }

    private static final class RootCommandResult {
        final int exitCode;
        final String output;
        final String error;
        final long startedAtMs;

        RootCommandResult(int exitCode, String output, String error, long startedAtMs) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
            this.startedAtMs = startedAtMs;
        }
    }
}
