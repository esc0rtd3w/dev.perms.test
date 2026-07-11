package dev.perms.test.logging;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.perms.test.databinding.TabLoggingBinding;
import dev.perms.test.storage.StorageAccessController;

/**
 * Runs Logging tab logcat/diagnostic actions while Activity-owned backend execution stays in MainActivity.
 */
public final class LoggingLogcatActions {
    private final TabLoggingBinding logging;
    private final BackendReadyCheck backendReadyCheck;
    private final Runnable refreshStatus;
    private final OutputAppender outputAppender;
    private final OutputTagSetter outputTagSetter;
    private final LastSavedFileSetter lastSavedFileSetter;
    private final ShellCommandRunner shellCommandRunner;
    private final CommandCaptureRunner commandRunner;
    private final ExecutorService fileIo = Executors.newSingleThreadExecutor();

    public LoggingLogcatActions(TabLoggingBinding logging,
                                BackendReadyCheck backendReadyCheck,
                                Runnable refreshStatus,
                                OutputAppender outputAppender,
                                OutputTagSetter outputTagSetter,
                                LastSavedFileSetter lastSavedFileSetter,
                                ShellCommandRunner shellCommandRunner,
                                CommandCaptureRunner commandRunner) {
        this.logging = logging;
        this.backendReadyCheck = backendReadyCheck;
        this.refreshStatus = refreshStatus;
        this.outputAppender = outputAppender;
        this.outputTagSetter = outputTagSetter;
        this.lastSavedFileSetter = lastSavedFileSetter;
        this.shellCommandRunner = shellCommandRunner;
        this.commandRunner = commandRunner;
    }

    public void run(boolean errorsOnly) {
        if (!isBackendReady()) {
            reportBackendNotReady();
            return;
        }

        int lines = getLineCount();
        String cmd = errorsOnly
                ? ("logcat -d -v time -t " + lines + " *:E")
                : ("logcat -d -v time -t " + lines);

        String tag = errorsOnly ? "logcat_errors" : "logcat";
        setOutputTag(tag);
        runCapture(tag, cmd);
    }

    public void clear() {
        if (!isBackendReady()) {
            reportBackendNotReady();
            return;
        }
        if (shellCommandRunner != null) {
            shellCommandRunner.run("logcat -c");
        }
    }

    public void runVariant(String variant, boolean errorsOnly) {
        runVariantInternal(variant, errorsOnly, false, true);
    }

    public void runFullDiagnostic() {
        if (!isBackendReady()) {
            reportBackendNotReady();
            return;
        }
        setOutputTag("full_diagnostic");
        append("[i] Full Diagnostic queued: logcat all plus every Diagnostics and Debugging capture will be saved to files.\n");
        String[] variants = {
                "all",
                "app",
                "anr",
                "dropbox",
                "app_full",
                "startup_network",
                "anr_deep",
                "activity",
                "system_state",
                "mem_cpu",
                "services",
                "notifications",
                "memory_tools"
        };
        for (String variant : variants) {
            runVariantInternal(variant, false, true, false);
        }
    }

    private void runVariantInternal(String variant, boolean errorsOnly, boolean forceSave, boolean showToast) {
        if (!isBackendReady()) {
            reportBackendNotReady();
            return;
        }

        int lines = getLineCount();
        String v = variant == null ? "" : variant.trim().toLowerCase(Locale.US);

        String cmd;
        String tag;
        if ("buffers".equals(v)) {
            cmd = "logcat -g";
            tag = "logcat_buffers";
        } else if ("threadtime".equals(v)) {
            cmd = "logcat -d -v threadtime -t " + lines;
            tag = "logcat_threadtime";
        } else if ("all".equals(v)) {
            cmd = "logcat -d -b all -v time -t " + lines;
            tag = "logcat_all";
        } else if ("radio".equals(v)) {
            cmd = "logcat -d -b radio -v time -t " + lines;
            tag = "logcat_radio";
        } else if ("events".equals(v)) {
            cmd = "logcat -d -b events -v time -t " + lines;
            tag = "logcat_events";
        } else if ("crash".equals(v)) {
            // Not all devices expose the crash buffer; fall back to main/system if unavailable.
            cmd = "logcat -d -b crash -v time -t " + lines + " 2>/dev/null || logcat -d -b main -v time -t " + lines;
            tag = "logcat_crash";
        } else if ("app".equals(v)) {
            cmd = "logcat -d -b all -v threadtime -t " + lines
                    + " | grep -iE 'dev[.]perms[.]test|MainActivity|MemoryOverlayService|AndroidRuntime|FATAL EXCEPTION|ANR|Application Not Responding|Input dispatching timed out|ActivityManager|ActivityTaskManager|WindowManager'";
            tag = "logcat_app_focus";
        } else if ("app_full".equals(v)) {
            cmd = "printf '=== PermsTest full logcat focus ===\n'; "
                    + "logcat -d -b all -v threadtime -t " + lines
                    + " | grep -iE 'dev[.]perms[.]test|AndroidRuntime|FATAL EXCEPTION|ANR|Application Not Responding|Input dispatching timed out|NetworkFtpClient|MainActivity|startup-debug|plugins-debug|network-debug'";
            tag = "diagnostic_app_full";
        } else if ("startup_network".equals(v)) {
            cmd = "printf '=== PermsTest startup/network focus ===\n'; "
                    + "logcat -d -b all -v threadtime -t " + lines
                    + " | grep -iE 'dev[.]perms[.]test|startup-debug|network-debug|NetworkFtpClient|FtpClient|NetworkActivityControllers|setupNetworkTab|continueMainCreate|Input dispatching timed out|ANR'";
            tag = "diagnostic_startup_network";
        } else if ("anr_deep".equals(v)) {
            cmd = "printf '=== PermsTest ANR deep capture ===\n'; "
                    + "printf '\n--- app focused logcat ---\n'; "
                    + "logcat -d -b all -v threadtime -t " + lines
                    + " | grep -iE 'dev[.]perms[.]test|AndroidRuntime|FATAL EXCEPTION|ANR|Application Not Responding|Input dispatching timed out|NetworkFtpClient|MainActivity|ActivityManager|ActivityTaskManager|InputDispatcher|WindowManager' || true; "
                    + "printf '\n--- dropbox ANR/crash entries ---\n'; "
                    + "dumpsys dropbox --print data_app_anr system_app_anr data_app_crash system_app_crash 2>/dev/null | head -n " + lines + " || true; "
                    + "printf '\n--- /data/anr latest ---\n'; "
                    + "latest=\"$(ls -t /data/anr/anr_* /data/anr/traces.txt 2>/dev/null | head -n 1)\"; "
                    + "if [ -n \"$latest\" ]; then printf '%s\n' \"$latest\"; head -n " + lines + " \"$latest\" 2>/dev/null || true; else echo 'No accessible ANR trace file.'; fi; "
                    + "printf '\n--- activity/process state ---\n'; "
                    + "dumpsys activity processes 2>/dev/null | grep -iE 'dev[.]perms[.]test|ANR|not responding|pid|ProcessRecord' | head -n " + lines + " || true; "
                    + "printf '\n--- current Java stack request ---\n'; "
                    + "pid=\"$(pidof dev.perms.test 2>/dev/null | awk '{print $1}')\"; "
                    + "if [ -n \"$pid\" ]; then kill -3 \"$pid\" 2>/dev/null || true; sleep 2; "
                    + "logcat -d -b main -v threadtime -t 2000 | grep -iE 'dev[.]perms[.]test|DALVIK THREADS|prio=|tid=|\\\"main\\\"|NetworkFtpClient|MainActivity|Binder|Finalizer' | head -n " + lines + " || true; "
                    + "else echo 'dev.perms.test process not running.'; fi; true";
            tag = "diagnostic_anr_deep";
        } else if ("anr".equals(v)) {
            cmd = "printf '=== /data/anr listing ===\n'; "
                    + "ls -la /data/anr 2>/dev/null || true; "
                    + "latest=\"$(ls -t /data/anr/anr_* /data/anr/traces.txt 2>/dev/null | head -n 1)\"; "
                    + "if [ -n \"$latest\" ]; then printf '\n=== %s ===\n' \"$latest\"; head -n " + lines + " \"$latest\" 2>/dev/null || true; "
                    + "else echo 'No ANR trace file found or accessible.'; fi; true";
            tag = "diagnostic_anr";
        } else if ("dropbox".equals(v)) {
            cmd = "dumpsys dropbox --print 2>/dev/null"
                    + " | grep -iE 'dev[.]perms[.]test|data_app_anr|system_app_anr|data_app_crash|system_app_crash|tombstone|watchdog|ANR|crash'"
                    + " | head -n " + lines;
            tag = "diagnostic_dropbox";
        } else if ("activity".equals(v)) {
            cmd = "logcat -d -b main,system -v threadtime -t " + lines
                    + " | grep -iE 'dev[.]perms[.]test|ActivityManager|ActivityTaskManager|InputDispatcher|WindowManager|VRI\\[MainActivity\\]|ANR|Application Not Responding'";
            tag = "diagnostic_activity";
        } else if ("system_state".equals(v)) {
            cmd = "printf '=== activity top ===\\n'; dumpsys activity top 2>/dev/null | head -n 220; "
                    + "printf '\\n=== activity processes ===\\n'; dumpsys activity processes 2>/dev/null | head -n 220; "
                    + "printf '\\n=== meminfo dev.perms.test ===\\n'; dumpsys meminfo dev.perms.test 2>/dev/null | head -n 180";
            tag = "diagnostic_system_state";
        } else if ("mem_cpu".equals(v)) {
            cmd = "printf '=== meminfo dev.perms.test ===\\n'; dumpsys meminfo dev.perms.test 2>/dev/null | head -n 240; "
                    + "printf '\\n=== cpuinfo ===\\n'; dumpsys cpuinfo 2>/dev/null | head -n 160; "
                    + "printf '\\n=== procstats dev.perms.test ===\\n'; dumpsys procstats --hours 1 dev.perms.test 2>/dev/null | head -n 180";
            tag = "diagnostic_mem_cpu";
        } else if ("services".equals(v)) {
            cmd = "printf '=== activity services: dev.perms.test ===\n'; dumpsys activity services dev.perms.test 2>/dev/null | head -n " + lines + "; "
                    + "printf '\n=== foreground service state ===\n'; dumpsys activity services 2>/dev/null"
                    + " | grep -iE 'dev[.]perms[.]test|foreground|fgs|PermsTest|ApkEditorJobService|MemoryOverlayService|Ftp|Http' | head -n " + lines;
            tag = "diagnostic_services";
        } else if ("notifications".equals(v)) {
            cmd = "dumpsys notification --noredact 2>/dev/null"
                    + " | grep -iE 'dev[.]perms[.]test|PermsTest|foreground|ongoing|ApkEditor|MemoryOverlay|FTP|HTTP|notification'"
                    + " | head -n " + lines;
            tag = "diagnostic_notifications";
        } else if ("memory_tools".equals(v)) {
            cmd = "logcat -d -b all -v threadtime -t " + lines
                    + " | grep -iE 'dev[.]perms[.]test|MemoryOverlayService|MemoryHexOverlayController|MemoryDisassemblyOverlayController|ApkEditorJobService|tool sync|hex->disasm|disasm->hex|ForegroundService|FATAL EXCEPTION|ANR'";
            tag = "diagnostic_memory_tools";
        } else {
            cmd = "logcat -d -v time -t " + lines;
            tag = "logcat";
        }

        if (!"buffers".equals(v)
                && !"anr".equals(v)
                && !"dropbox".equals(v)
                && !"system_state".equals(v)
                && !"mem_cpu".equals(v)
                && !"services".equals(v)
                && !"notifications".equals(v)
                && !"memory_tools".equals(v)
                && !"app_full".equals(v)
                && !"startup_network".equals(v)
                && !"anr_deep".equals(v)) {
            cmd = errorsOnly ? (cmd + " *:E") : cmd;
        }

        if (!forceSave) {
            setOutputTag(tag);
        }
        runCapture(tag, cmd, forceSave, showToast);
    }

    private void runCapture(String tag, String cmd) {
        runCapture(tag, cmd, false, true);
    }

    private void runCapture(String tag, String cmd, boolean forceSave, boolean showToast) {
        if (commandRunner == null) return;
        commandRunner.run(cmd, (exit, out, err) -> {
            String filter = getFilter();
            if (forceSave || shouldSaveToFile()) {
                saveCaptureToFile(tag, cmd, filter, exit, out, err, showToast);
                return;
            }

            String o = filterLines(out, filter);
            String e = filterLines(err, filter);
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(o)) sb.append(o);
            if (!TextUtils.isEmpty(e)) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
                sb.append(e);
            }
            sb.append("exit=").append(exit).append("\n");
            append(sb.toString());
        });
    }

    private int getLineCount() {
        int n = 400;
        try {
            if (logging != null && logging.edtLogLines != null && logging.edtLogLines.getText() != null) {
                n = Integer.parseInt(logging.edtLogLines.getText().toString().trim());
            }
        } catch (Throwable ignored) {}
        if (n < 50) n = 50;
        if (n > 20000) n = 20000;
        return n;
    }

    private String getFilter() {
        return logging == null || logging.edtLogFilter == null || logging.edtLogFilter.getText() == null
                ? ""
                : logging.edtLogFilter.getText().toString().trim();
    }

    private String filterLines(String text, String needle) {
        if (TextUtils.isEmpty(needle)) return text;
        String n = needle.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        String[] lines = text == null ? new String[0] : text.split("\\r?\\n");
        for (String line : lines) {
            if (line.toLowerCase(Locale.ROOT).contains(n)) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private boolean shouldSaveToFile() {
        try {
            return logging != null
                    && logging.chkLogcatSaveToFile != null
                    && logging.chkLogcatSaveToFile.isChecked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void saveCaptureToFile(String tag, String cmd, String uiFilter, int exit, String out, String err, boolean showToast) {
        Context context = null;
        try {
            context = logging == null || logging.getRoot() == null ? null : logging.getRoot().getContext();
        } catch (Throwable ignored) {}
        if (context == null) {
            append("[!] Log save skipped: context unavailable.\n");
            return;
        }

        Activity activity = context instanceof Activity ? (Activity) context : null;
        if (activity != null && !StorageAccessController.ensureSharedStorageWriteAccess(
                activity,
                "Logcat Save To File",
                "/sdcard/dev.perms.test/logs/logcat",
                outputAppender == null ? null : outputAppender::append)) {
            return;
        }

        final Context appContext = context.getApplicationContext() == null ? context : context.getApplicationContext();
        fileIo.execute(() -> {
            File saved = null;
            Exception failure = null;
            try {
                File dir = new File(Environment.getExternalStorageDirectory(), "dev.perms.test/logs/logcat");
                if (!dir.exists() && !dir.mkdirs()) {
                    File base = appContext.getExternalFilesDir(null);
                    if (base == null) throw new IllegalStateException("External files dir unavailable");
                    dir = new File(base, "logs/logcat");
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                }

                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File file = new File(dir, "PermsTest_" + sanitizeFileTag(tag) + "_" + ts + ".txt");
                StringBuilder raw = new StringBuilder();
                raw.append("PermsTest log capture\n");
                raw.append("timestamp=").append(ts).append('\n');
                raw.append("tag=").append(tag == null ? "logcat" : tag).append('\n');
                raw.append("exit=").append(exit).append('\n');
                raw.append("ui_filter=").append(TextUtils.isEmpty(uiFilter) ? "" : uiFilter).append('\n');
                raw.append("command=").append(cmd == null ? "" : cmd).append("\n\n");
                raw.append("--- stdout ---\n");
                if (!TextUtils.isEmpty(out)) raw.append(out);
                if (raw.length() > 0 && raw.charAt(raw.length() - 1) != '\n') raw.append('\n');
                raw.append("\n--- stderr ---\n");
                if (!TextUtils.isEmpty(err)) raw.append(err);
                if (raw.length() > 0 && raw.charAt(raw.length() - 1) != '\n') raw.append('\n');

                try (FileOutputStream fos = new FileOutputStream(file, false)) {
                    fos.write(raw.toString().getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                }
                try { file.setReadable(true, false); } catch (Throwable ignored) {}
                saved = file;
            } catch (Exception e) {
                failure = e;
            }

            final File finalSaved = saved;
            final Exception finalFailure = failure;
            postToUi(() -> {
                if (finalSaved != null) {
                    if (lastSavedFileSetter != null) lastSavedFileSetter.setFile(finalSaved);
                    append("[+] Saved logcat file: " + finalSaved.getAbsolutePath() + "\n");
                    if (showToast) Toast.makeText(appContext, "Saved logcat file", Toast.LENGTH_SHORT).show();
                    if (refreshStatus != null) refreshStatus.run();
                } else {
                    append("[!] Log save failed: " + finalFailure + "\n");
                }
            });
        });
    }

    private void postToUi(Runnable action) {
        if (action == null) return;
        try {
            if (logging != null && logging.getRoot() != null) {
                logging.getRoot().post(action);
                return;
            }
        } catch (Throwable ignored) {}
        action.run();
    }

    private static String sanitizeFileTag(String in) {
        if (in == null) return "logcat";
        String s = in.trim();
        if (s.isEmpty()) return "logcat";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
            if (ok) sb.append(c);
            else if (c == '.') sb.append('_');
        }
        String out = sb.toString();
        return out.isEmpty() ? "logcat" : out;
    }

    private boolean isBackendReady() {
        return backendReadyCheck != null && backendReadyCheck.isReady();
    }

    private void reportBackendNotReady() {
        if (refreshStatus != null) refreshStatus.run();
        append("[!] Shizuku not ready or permission not granted.\n");
    }

    private void setOutputTag(String tag) {
        if (outputTagSetter != null) outputTagSetter.setTag(tag);
    }

    private void append(String text) {
        if (outputAppender != null) outputAppender.append(text);
    }

    public interface BackendReadyCheck {
        boolean isReady();
    }

    public interface OutputAppender {
        void append(String text);
    }

    public interface OutputTagSetter {
        void setTag(String tag);
    }

    public interface LastSavedFileSetter {
        void setFile(File file);
    }

    public interface ShellCommandRunner {
        void run(String command);
    }

    public interface CommandCaptureRunner {
        void run(String command, CaptureResultCallback callback);
    }

    public interface CaptureResultCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }
}
