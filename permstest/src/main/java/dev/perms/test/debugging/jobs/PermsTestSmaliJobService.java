package dev.perms.test.debugging.jobs;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import dev.perms.test.MainActivity;
import dev.perms.test.R;
import dev.perms.test.ShizukuCompat;
import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.debugging.editor.SmaliEditorSearch;
import dev.perms.test.debugging.smali.PermsTestSmaliTools;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import rikka.shizuku.Shizuku;

/**
 * Foreground worker for long Debugging-tab smali/baksmali jobs.
 * Short single-DEX actions remain Activity-owned; long all-DEX disassembly runs here so it is
 * less likely to be killed when PermsTest is backgrounded during a large APK pass.
 */
public final class PermsTestSmaliJobService extends Service {
    public static final String ACTION_DISASSEMBLE_ALL = "dev.perms.test.action.DISASSEMBLE_ALL_DEX";
    public static final String ACTION_SEARCH_EDITOR = "dev.perms.test.action.SEARCH_SMALI_EDITOR";
    public static final String ACTION_JADX_DECOMPILE = "dev.perms.test.action.JADX_DECOMPILE";
    public static final String ACTION_STOP = "dev.perms.test.action.STOP_SMALI_JOB";

    public static final String EXTRA_INPUT = "input";
    public static final String EXTRA_DEX_ENTRIES = "dex_entries";
    public static final String EXTRA_OUT_DIRS = "out_dirs";
    public static final String EXTRA_API_LEVEL = "api_level";
    public static final String EXTRA_CLEAN_OUTPUT = "clean_output";
    public static final String EXTRA_JADX_OUT_DIR = "jadx_out_dir";
    public static final String EXTRA_JADX_ZIP_OUTPUT = "jadx_zip_output";
    public static final String EXTRA_JADX_JAVA_INNER_NAMES = "jadx_java_inner_names";
    public static final String EXTRA_JADX_DEX_ENTRY = "jadx_dex_entry";
    public static final String EXTRA_SEARCH_ROOTS = "search_roots";
    public static final String EXTRA_SEARCH_DISPLAY_ROOT = "search_display_root";
    public static final String EXTRA_SEARCH_QUERY = "search_query";
    public static final String EXTRA_SEARCH_FILTER_MODE = "search_filter_mode";
    public static final String EXTRA_SEARCH_EXCLUDE_ENABLED = "search_exclude_enabled";
    public static final String EXTRA_SEARCH_EXCLUDE_TERMS = "search_exclude_terms";
    public static final String EXTRA_SEARCH_MAX_FILES = "search_max_files";
    public static final String EXTRA_SEARCH_PAGE_SIZE = "search_page_size";
    public static final String EXTRA_SEARCH_PAGE_INDEX = "search_page_index";

    public static final String PREF_JOB_RUNNING = "smali_disassemble_all_job_running";
    public static final String PREF_JOB_STATUS = "smali_disassemble_all_job_status";
    public static final String PREF_JOB_LOG = "smali_disassemble_all_job_log";
    public static final String PREF_JOB_UPDATED_AT = "smali_disassemble_all_job_updated_at";
    public static final String PREF_JADX_RUNNING = "jadx_go_job_running";
    public static final String PREF_JADX_STATUS = "jadx_go_job_status";
    public static final String PREF_JADX_LOG = "jadx_go_job_log";
    public static final String PREF_JADX_ERROR = "jadx_go_job_error";
    public static final String PREF_JADX_OUT_DIR = "jadx_go_job_out_dir";
    public static final String PREF_JADX_ZIP_PATH = "jadx_go_job_zip_path";
    public static final String PREF_JADX_UPDATED_AT = "jadx_go_job_updated_at";
    public static final String PREF_JADX_PROGRESS_CURRENT = "jadx_go_job_progress_current";
    public static final String PREF_JADX_PROGRESS_TOTAL = "jadx_go_job_progress_total";
    public static final String PREF_JADX_PHASE = "jadx_go_job_phase";
    public static final String PREF_SEARCH_RUNNING = "smali_editor_search_running";
    public static final String PREF_SEARCH_STATUS = "smali_editor_search_status";
    public static final String PREF_SEARCH_ERROR = "smali_editor_search_error";
    public static final String PREF_SEARCH_HAS_RESULT = "smali_editor_search_has_result";
    public static final String PREF_SEARCH_ROWS = "smali_editor_search_rows";
    public static final String PREF_SEARCH_QUERY_TEXT = "smali_editor_search_query_text";
    public static final String PREF_SEARCH_SCANNED_FILES = "smali_editor_search_scanned_files";
    public static final String PREF_SEARCH_TOTAL_RESULTS = "smali_editor_search_total_results";
    public static final String PREF_SEARCH_PAGE_INDEX_RESULT = "smali_editor_search_page_index_result";
    public static final String PREF_SEARCH_PAGE_SIZE_RESULT = "smali_editor_search_page_size_result";
    public static final String PREF_SEARCH_UPDATED_AT = "smali_editor_search_updated_at";

    public static final String PREFS = "perms_test";
    private static final String CHANNEL_ID = "smali_job_status_v1";
    private static final int NOTIFICATION_ID = 70517;
    private static final int MAX_JOB_LOG_CHARS = 32000;

    private volatile boolean stopRequested;
    private volatile boolean jobHeartbeatActive;
    private volatile boolean jadxZipToastShown;
    private volatile Process runningProcess;
    private volatile String workerAction;
    private Thread worker;
    private Thread jobHeartbeatThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_DISASSEMBLE_ALL : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopRequested = true;
            boolean smaliRunning = false;
            boolean jadxRunning = false;
            boolean searchRunning = false;
            try {
                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                smaliRunning = sp.getBoolean(PREF_JOB_RUNNING, false);
                jadxRunning = sp.getBoolean(PREF_JADX_RUNNING, false);
                searchRunning = sp.getBoolean(PREF_SEARCH_RUNNING, false);
            } catch (Throwable ignored) {
            }
            try { if (runningProcess != null) runningProcess.destroy(); } catch (Throwable ignored) {}
            if (smaliRunning) updateStatus(true, "Stopping smali job...", "[Debugging] Stop requested.\n", false);
            if (jadxRunning) updateJadxStatus(true, "Stopping DEX to Java...", "[Debugging] jadx-go stop requested.\n", null, null, false);
            if (searchRunning) updateSearchStatus(false, "Smali/Java search stopped.", null, "", true);
            if (!smaliRunning && !jadxRunning && !searchRunning) updateStatus(false, "Debugging stop requested.", "[Debugging] Stop requested, but no active job was recorded.\n", false);
            updateNotification("Stopping debugging job...");
            if (worker == null || !worker.isAlive()) {
                if (smaliRunning) updateStatus(false, "Smali job stopped.", "", false);
                if (jadxRunning) updateJadxStatus(false, "DEX to Java stopped.", "", null, null, false);
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (ACTION_SEARCH_EDITOR.equals(action)) {
            startForegroundCompat(buildNotification("Searching smali/java files..."));
            startSearchEditor(intent);
            return START_NOT_STICKY;
        }
        if (ACTION_JADX_DECOMPILE.equals(action)) {
            startForegroundCompat(buildNotification("Starting DEX to Java..."));
            startJadxDecompile(intent);
            return START_NOT_STICKY;
        }
        startForegroundCompat(buildNotification("Starting smali disassembly..."));
        startDisassembleAll(intent);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopRequested = true;
        stopJobHeartbeat();
        super.onDestroy();
    }

    private synchronized void startDisassembleAll(Intent intent) {
        if (worker != null && worker.isAlive()) {
            updateNotification("Smali disassembly is already running...");
            return;
        }
        stopRequested = false;
        final String input = intent == null ? "" : intent.getStringExtra(EXTRA_INPUT);
        final ArrayList<String> entries = intent == null ? null : intent.getStringArrayListExtra(EXTRA_DEX_ENTRIES);
        final ArrayList<String> outDirs = intent == null ? null : intent.getStringArrayListExtra(EXTRA_OUT_DIRS);
        final int apiLevel = intent == null ? Build.VERSION.SDK_INT : intent.getIntExtra(EXTRA_API_LEVEL, Build.VERSION.SDK_INT);
        final boolean cleanOutput = intent != null && intent.getBooleanExtra(EXTRA_CLEAN_OUTPUT, false);

        workerAction = ACTION_DISASSEMBLE_ALL;
        worker = new Thread(() -> runDisassembleAll(input, entries, outDirs, apiLevel, cleanOutput), "PermsTest-SmaliJob");
        worker.start();
    }

    private synchronized void startSearchEditor(Intent intent) {
        if (worker != null && worker.isAlive()) {
            updateSearchStatus(false, "Another smali job is already running...",
                    null, "Another smali job is already running.", true);
            updateNotification("Another smali job is already running...");
            return;
        }
        stopRequested = false;
        final ArrayList<String> roots = intent == null ? null : intent.getStringArrayListExtra(EXTRA_SEARCH_ROOTS);
        final String displayRoot = intent == null ? "" : intent.getStringExtra(EXTRA_SEARCH_DISPLAY_ROOT);
        final String query = intent == null ? "" : intent.getStringExtra(EXTRA_SEARCH_QUERY);
        final int filterMode = intent == null ? SmaliEditorSearch.FILTER_CONTAINS_IGNORE_CASE
                : intent.getIntExtra(EXTRA_SEARCH_FILTER_MODE, SmaliEditorSearch.FILTER_CONTAINS_IGNORE_CASE);
        final boolean excludeEnabled = intent != null && intent.getBooleanExtra(EXTRA_SEARCH_EXCLUDE_ENABLED, false);
        final String excludeTerms = intent == null ? "" : intent.getStringExtra(EXTRA_SEARCH_EXCLUDE_TERMS);
        final int maxFiles = intent == null ? SmaliEditorSearch.DEFAULT_MAX_FILES
                : intent.getIntExtra(EXTRA_SEARCH_MAX_FILES, SmaliEditorSearch.DEFAULT_MAX_FILES);
        final int pageSize = intent == null ? SmaliEditorSearch.DEFAULT_MAX_RESULTS
                : intent.getIntExtra(EXTRA_SEARCH_PAGE_SIZE, SmaliEditorSearch.DEFAULT_MAX_RESULTS);
        final int pageIndex = intent == null ? 0 : intent.getIntExtra(EXTRA_SEARCH_PAGE_INDEX, 0);

        workerAction = ACTION_SEARCH_EDITOR;
        worker = new Thread(() -> runSearchEditor(roots, displayRoot, query, filterMode,
                excludeEnabled, excludeTerms, maxFiles, pageSize, pageIndex), "PermsTest-SmaliSearch");
        worker.start();
    }



    private synchronized void startJadxDecompile(Intent intent) {
        if (worker != null && worker.isAlive()) {
            boolean sameJob = ACTION_JADX_DECOMPILE.equals(workerAction);
            updateJadxStatus(sameJob, sameJob ? "DEX to Java is already running..." : "Another debugging job is already running...",
                    "[Debugging] DEX to Java start ignored because another debugging job is running.\n", "", "", false);
            updateNotification(sameJob ? "DEX to Java is already running..." : "Another debugging job is already running...");
            return;
        }
        stopRequested = false;
        final String input = intent == null ? "" : intent.getStringExtra(EXTRA_INPUT);
        final String outDir = intent == null ? "" : intent.getStringExtra(EXTRA_JADX_OUT_DIR);
        final boolean zipOutput = intent != null && intent.getBooleanExtra(EXTRA_JADX_ZIP_OUTPUT, false);
        final boolean javaInnerNames = intent == null || intent.getBooleanExtra(EXTRA_JADX_JAVA_INNER_NAMES, true);
        final String dexEntry = intent == null ? "" : intent.getStringExtra(EXTRA_JADX_DEX_ENTRY);
        workerAction = ACTION_JADX_DECOMPILE;
        worker = new Thread(() -> runJadxDecompile(input, outDir, zipOutput, javaInnerNames, dexEntry), "PermsTest-JadxGo");
        worker.start();
    }


    private void runJadxDecompile(String input, String outDir, boolean zipOutput, boolean javaInnerNames, String dexEntry) {
        String zipPath = "";
        String logPath = "";
        File jobDebugLog = null;
        long jobStartedAt = System.currentTimeMillis();
        try {
            if (TextUtils.isEmpty(input)) throw new IllegalArgumentException("Choose an APK or DEX source first.");
            if (TextUtils.isEmpty(outDir)) throw new IllegalArgumentException("Choose a Java output folder first.");
            File logDir = new File("/sdcard/dev.perms.test/logs/jadx-go");
            if (!logDir.exists()) logDir.mkdirs();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            logPath = new File(logDir, "jadx-go_" + stamp + ".log").getAbsolutePath();
            jobDebugLog = new File(logDir, "perms-jadx-job_" + stamp + ".log");
            zipPath = zipOutput ? defaultZipPath(outDir) : "";
            ArrayList<String> dexEntries = resolveJadxDexEntries(input, dexEntry);
            String jobText = dexEntries.size() == 1
                    ? (TextUtils.isEmpty(dexEntries.get(0)) ? "raw DEX input" : dexEntries.get(0))
                    : (dexEntries.size() + " top-level DEX entries");
            jadxZipToastShown = false;
            String startLog = "[Debugging] Foreground DEX to Java started for " + jobText + ".\n"
                    + "[Debugging] Java Output options: input=" + input
                    + " out=" + outDir
                    + " zip=" + (zipOutput ? zipPath : "off")
                    + " innerNames=" + (javaInnerNames ? "java" : "binary")
                    + " selectedDex=" + (TextUtils.isEmpty(dexEntry) ? "all/top-level" : dexEntry) + "\n"
                    + "[Debugging] Java Output dex entries: " + joinDexEntries(dexEntries) + "\n"
                    + "[Debugging] Java Output job log=" + jobDebugLog.getAbsolutePath() + "\n"
                    + "[Debugging] jadx-go log=" + logPath + "\n"
                    + (TextUtils.isEmpty(dexEntry) && dexEntries.size() > 1
                    ? "[Debugging] Running jadx-go one DEX at a time to keep memory bounded on large multi-DEX APKs.\n"
                    : "")
                    + DebugJobDiagnostics.memoryLine("job-start");
            DebugJobDiagnostics.append(jobDebugLog, startLog);
            updateJadxStatus(true, "Starting DEX to Java...",
                    startLog,
                    outDir, zipPath, true, 0, dexEntries.size(), "start");
            updateNotification("Starting DEX to Java...");
            startJobHeartbeat(PREF_JADX_RUNNING, PREF_JADX_UPDATED_AT);

            ensureJadxBinaryPublic();
            for (int i = 0; i < dexEntries.size(); i++) {
                if (stopRequested) throw new InterruptedException("DEX to Java was stopped.");
                String entry = dexEntries.get(i);
                String label = TextUtils.isEmpty(entry) ? "raw DEX" : entry;
                String thisZipPath = zipOutput && i == dexEntries.size() - 1 ? zipPath : "";
                String cmd = buildJadxCommand(input, outDir, logDir.getAbsolutePath(), logPath,
                        javaInnerNames, entry, thisZipPath);
                String dexStartLog = "[Debugging] jadx-go command (" + (i + 1) + "/" + dexEntries.size() + "): " + cmd + "\n"
                        + DebugJobDiagnostics.memoryLine("before-" + label);
                DebugJobDiagnostics.append(jobDebugLog, dexStartLog);
                updateJadxStatus(true,
                        "DEX to Java " + label + " (" + (i + 1) + "/" + dexEntries.size() + ")...",
                        dexStartLog,
                        outDir, zipPath, false, i + 1, dexEntries.size(), "dex");
                runJadxCommand(cmd, outDir, zipPath, i + 1, dexEntries.size(), label, jobDebugLog);
                String dexDoneLog = "[Debugging] jadx-go complete for " + label + " (" + (i + 1) + "/" + dexEntries.size() + ").\n"
                        + DebugJobDiagnostics.memoryLine("after-" + label);
                DebugJobDiagnostics.append(jobDebugLog, dexDoneLog);
                updateJadxStatus(true,
                        "DEX to Java completed " + label + " (" + (i + 1) + "/" + dexEntries.size() + ").",
                        dexDoneLog,
                        outDir, zipPath, false, i + 1, dexEntries.size(), "dex-done");
            }
            String status = "DEX to Java complete: " + outDir + (TextUtils.isEmpty(zipPath) ? "" : ("; zip: " + zipPath));
            String log = "[Debugging] DEX to Java complete. output=" + outDir
                    + (TextUtils.isEmpty(zipPath) ? "" : (" zip=" + zipPath))
                    + " log=" + logPath
                    + " jobLog=" + (jobDebugLog == null ? "" : jobDebugLog.getAbsolutePath())
                    + " elapsed=" + DebugJobDiagnostics.elapsed(jobStartedAt) + "\n"
                    + DebugJobDiagnostics.memoryLine("job-complete");
            DebugJobDiagnostics.append(jobDebugLog, log);
            updateJadxStatus(false, status, log, outDir, zipPath, false, dexEntries.size(), dexEntries.size(), "complete");
            updateNotification(status);
        } catch (Throwable t) {
            runningProcess = null;
            boolean stopped = stopRequested || t instanceof InterruptedException;
            if (stopped) {
                DebugJobDiagnostics.append(jobDebugLog, "[Debugging] DEX to Java stopped by user after " + DebugJobDiagnostics.elapsed(jobStartedAt) + ".");
                updateJadxStatus(false, "DEX to Java stopped.",
                        "[Debugging] DEX to Java stopped by user.\n", outDir, zipPath, false, 0, 0, "stopped");
                updateNotification("DEX to Java stopped");
            } else {
                String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
                DebugJobDiagnostics.append(jobDebugLog, "[Debugging] DEX to Java failed after " + DebugJobDiagnostics.elapsed(jobStartedAt) + ": " + msg);
                updateJadxStatus(false, "DEX to Java failed: " + msg,
                        "[Debugging] DEX to Java failed: " + msg + "\n", outDir, zipPath, false, 0, 0, "failed");
                updateNotification("DEX to Java failed");
            }
        } finally {
            runningProcess = null;
            if (ACTION_JADX_DECOMPILE.equals(workerAction)) workerAction = null;
            stopJobHeartbeat();
            stopSelf();
        }
    }

    private ArrayList<String> resolveJadxDexEntries(String input, String dexEntry) throws Exception {
        ArrayList<String> out = new ArrayList<>();
        if (isRawDexInput(input)) {
            out.add("");
            return out;
        }
        if (!TextUtils.isEmpty(dexEntry)) {
            out.add(PermsTestSmaliTools.normalizeDexEntryName(dexEntry));
            return out;
        }
        PermsTestSmaliTools.DexEntryScanResult scan = PermsTestSmaliTools.listDexEntriesDetailed(input);
        if (scan != null && scan.entries != null) {
            for (String entry : scan.entries) {
                String clean = PermsTestSmaliTools.normalizeDexEntryName(entry);
                if (!TextUtils.isEmpty(clean) && !out.contains(clean)) out.add(clean);
            }
        }
        if (out.isEmpty()) throw new IllegalArgumentException("No top-level classes*.dex entries found in APK/ZIP input.");
        return out;
    }

    private boolean isRawDexInput(String input) {
        if (TextUtils.isEmpty(input)) return false;
        File file = new File(input);
        if (!file.isFile()) return false;
        byte[] magic = new byte[4];
        try (InputStream in = new FileInputStream(file)) {
            int off = 0;
            while (off < magic.length) {
                int r = in.read(magic, off, magic.length - off);
                if (r < 0) break;
                off += r;
            }
            return off == 4 && magic[0] == 'd' && magic[1] == 'e' && magic[2] == 'x' && magic[3] == '\n';
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String buildJadxCommand(String input, String outDir, String logDir, String logPath,
                                    boolean javaInnerNames, String dexEntry, String zipPath) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("mkdir -p ").append(ApkDebugToolHelper.shQuote(outDir));
        cmd.append(" ").append("&& mkdir -p ").append(ApkDebugToolHelper.shQuote(logDir));
        cmd.append(" ").append("&& ").append(ApkDebugToolHelper.shQuote(ApkDebugToolHelper.PUBLIC_BIN_DIR + "/" + ApkDebugToolHelper.TOOL_JADX));
        cmd.append(" --progress --debug-summary --append-log --log ").append(ApkDebugToolHelper.shQuote(logPath));
        cmd.append(javaInnerNames ? " --java-inner-names" : " --binary-inner-names");
        if (!TextUtils.isEmpty(zipPath)) cmd.append(" --zip-out ").append(ApkDebugToolHelper.shQuote(zipPath));
        if (!TextUtils.isEmpty(dexEntry)) cmd.append(" --dex-entry ").append(ApkDebugToolHelper.shQuote(dexEntry));
        cmd.append(" -d ").append(ApkDebugToolHelper.shQuote(outDir));
        cmd.append(" ").append(ApkDebugToolHelper.shQuote(input));
        cmd.append(" 2>&1");
        return cmd.toString();
    }

    private void runJadxCommand(String cmd, String outDir, String zipPath, int dexIndex, int dexTotal, String dexLabel, File jobDebugLog) throws Exception {
        long startedAt = System.currentTimeMillis();
        DebugJobDiagnostics.append(jobDebugLog, "[Debugging] starting jadx-go subprocess for " + safeDexLabel(dexLabel) + " (" + dexIndex + "/" + dexTotal + ").");
        Process process = ShizukuCompat.newProcess(new String[]{"sh", "-c", cmd}, null, null);
        runningProcess = process;
        StringBuilder tail = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (stopRequested) {
                    try { process.destroy(); } catch (Throwable ignored) {}
                    throw new InterruptedException("DEX to Java was stopped.");
                }
                handleJadxLine(line, outDir, zipPath, tail, dexIndex, dexTotal, dexLabel);
            }
        }
        int code = process.waitFor();
        runningProcess = null;
        String finishLine = "[Debugging] jadx-go subprocess exit=" + code + " dex=" + safeDexLabel(dexLabel) + " elapsed=" + DebugJobDiagnostics.elapsed(startedAt) + ".";
        DebugJobDiagnostics.append(jobDebugLog, finishLine);
        updateJadxStatus(true, "DEX to Java " + safeDexLabel(dexLabel) + " exited with code " + code + progressSuffix(dexIndex, dexTotal) + ".", finishLine + "\n", outDir, zipPath, false, dexIndex, dexTotal, "exit");
        if (stopRequested) throw new InterruptedException("DEX to Java was stopped.");
        if (code != 0) throw new IllegalStateException("jadx-go exited with code " + code + tailForError(tail));
    }

    private void handleJadxLine(String line, String outDir, String zipPath, StringBuilder tail, int dexIndex, int dexTotal, String dexLabel) {
        if (line == null) return;
        appendTail(tail, line);
        String status = null;
        String phase = "running";
        if (line.startsWith("PROGRESS ")) {
            if (line.contains("phase=parse")) {
                phase = "parse";
                status = "DEX to Java parsing " + valueAfter(line, "dex=") + progressSuffix(dexIndex, dexTotal) + "...";
            } else if (line.contains("phase=write")) {
                phase = "write";
                status = "DEX to Java writing " + safeDexLabel(dexLabel) + progressSuffix(dexIndex, dexTotal) + "...";
            } else if (line.contains("phase=class")) {
                phase = "class";
                String dex = valueAfter(line, "dex=");
                String cls = valueAfter(line, "class=");
                String file = valueAfter(line, "file=");
                status = "DEX to Java " + dex + progressSuffix(dexIndex, dexTotal) + " " + cls + (TextUtils.isEmpty(file) ? "" : (" -> " + file));
            } else if (line.contains("phase=zip")) {
                phase = "zip";
                String out = valueAfter(line, "out=");
                status = "Zipping Java output" + (TextUtils.isEmpty(out) ? "" : (" -> " + out)) + "...";
                showJadxToastOnce("Zipping Java output...");
            } else if (line.contains("phase=summary")) {
                phase = "summary";
                status = "DEX to Java summary " + progressSuffix(dexIndex, dexTotal) + ": " + line.substring("PROGRESS phase=summary".length()).trim();
            } else if (line.contains("phase=done")) {
                phase = "finalize";
                status = "DEX to Java finalizing...";
            } else if (line.contains("phase=load")) {
                phase = "load";
                status = "DEX to Java loading " + safeDexLabel(dexLabel) + progressSuffix(dexIndex, dexTotal) + "...";
            }
        } else if (line.startsWith("jadx-go wrote ")) {
            phase = "written";
            status = line;
        } else if (line.startsWith("jadx-go zipped output ")) {
            phase = "zip";
            status = line;
        }
        updateJadxStatus(true, TextUtils.isEmpty(status) ? "DEX to Java running..." : status, line + "\n", outDir, zipPath, false, dexIndex, dexTotal, phase);
        if (!TextUtils.isEmpty(status)) updateNotification(status);
    }


    private String joinDexEntries(ArrayList<String> entries) {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String entry : entries) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(TextUtils.isEmpty(entry) ? "raw DEX" : entry);
        }
        return sb.toString();
    }

    private String progressSuffix(int current, int total) {
        return total > 1 && current > 0 ? " (" + current + "/" + total + ")" : "";
    }

    private String safeDexLabel(String label) {
        return TextUtils.isEmpty(label) ? "raw DEX" : label;
    }

    private void showJadxToastOnce(String text) {
        if (jadxZipToastShown || TextUtils.isEmpty(text)) return;
        jadxZipToastShown = true;
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                try { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {
        }
    }

    private void ensureJadxBinaryPublic() throws Exception {
        if (!isShizukuReady()) throw new IllegalStateException("Shizuku backend is not connected/granted.");
        File stageDir = new File(getExternalFilesDir(null), "bin_stage");
        if (!stageDir.exists() && !stageDir.mkdirs()) throw new IllegalStateException("Unable to create binary stage directory.");
        File stage = new File(stageDir, ApkDebugToolHelper.TOOL_JADX);
        try (InputStream in = openBundledJadxAsset();
             OutputStream out = new java.io.FileOutputStream(stage, false)) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            out.flush();
        }
        try { stage.setReadable(true, false); } catch (Throwable ignored) {}
        try { stage.setExecutable(true, false); } catch (Throwable ignored) {}
        String dst = ApkDebugToolHelper.PUBLIC_BIN_DIR + "/" + ApkDebugToolHelper.TOOL_JADX;
        String cmd = "mkdir -p " + ApkDebugToolHelper.shQuote(ApkDebugToolHelper.PUBLIC_BIN_DIR)
                + " && rm -f " + ApkDebugToolHelper.shQuote(dst)
                + " && cp " + ApkDebugToolHelper.shQuote(stage.getAbsolutePath()) + " " + ApkDebugToolHelper.shQuote(dst)
                + " && chmod 755 " + ApkDebugToolHelper.shQuote(dst)
                + " && test -x " + ApkDebugToolHelper.shQuote(dst);
        Process p = ShizukuCompat.newProcess(new String[]{"sh", "-c", cmd}, null, null);
        drainQuietly(p.getInputStream());
        drainQuietly(p.getErrorStream());
        int code = p.waitFor();
        if (code != 0) throw new IllegalStateException("Unable to stage bundled jadx-go backend. exit=" + code);
    }

    private InputStream openBundledJadxAsset() throws java.io.IOException {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis != null) {
            for (String abi : abis) {
                if (TextUtils.isEmpty(abi)) continue;
                try {
                    return getAssets().open("bin/" + abi + "/" + ApkDebugToolHelper.TOOL_JADX);
                } catch (Throwable ignored) {
                }
            }
        }
        return getAssets().open("bin/" + ApkDebugToolHelper.TOOL_JADX);
    }

    private boolean isShizukuReady() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void drainQuietly(InputStream in) {
        if (in == null) return;
        try {
            byte[] buf = new byte[4096];
            while (in.read(buf) >= 0) {
            }
            in.close();
        } catch (Throwable ignored) {
        }
    }

    private static String defaultZipPath(String outDir) {
        if (TextUtils.isEmpty(outDir)) return "";
        String base = outDir.endsWith("/") ? outDir.substring(0, outDir.length() - 1) : outDir;
        return base + ".zip";
    }

    private static void appendTail(StringBuilder tail, String line) {
        if (tail == null || line == null) return;
        tail.append(line).append('\n');
        if (tail.length() > 2000) tail.delete(0, tail.length() - 2000);
    }

    private static String tailForError(StringBuilder tail) {
        if (tail == null || tail.length() == 0) return "";
        return ": " + tail.toString().trim();
    }

    private static String valueAfter(String line, String key) {
        if (TextUtils.isEmpty(line) || TextUtils.isEmpty(key)) return "";
        int idx = line.indexOf(key);
        if (idx < 0) return "";
        int start = idx + key.length();
        int end = line.indexOf(' ', start);
        if (end < 0) end = line.length();
        return line.substring(start, end).trim();
    }

    private void runDisassembleAll(String input, ArrayList<String> entries, ArrayList<String> outDirs, int apiLevel, boolean cleanOutput) {
        int total = entries == null ? 0 : entries.size();
        int successCount = 0;
        int failureCount = 0;
        try {
            if (TextUtils.isEmpty(input)) throw new IllegalArgumentException("Choose an APK or DEX source first.");
            if (entries == null || entries.isEmpty()) throw new IllegalArgumentException("No DEX entries were provided.");
            if (outDirs == null || outDirs.size() != entries.size()) throw new IllegalArgumentException("DEX output folders were not provided.");
            updateStatus(true, "Disassembling all DEX: 0 / " + total, "[Debugging] Foreground Disassemble All DEX started.\n", true);

            for (int i = 0; i < entries.size(); i++) {
                if (stopRequested) throw new InterruptedException("Disassemble All DEX was stopped.");
                String entry = PermsTestSmaliTools.normalizeDexEntryName(entries.get(i));
                String outDir = outDirs.get(i);
                String progress = "Disassembling " + entry + " (" + (i + 1) + " / " + total + ")...";
                updateStatus(true, progress, "[" + entry + "] start: " + outDir + "\n", false);
                updateNotification(progress);

                try {
                    PermsTestSmaliTools.ToolResult result = PermsTestSmaliTools.disassemble(input, entry, outDir, apiLevel, cleanOutput);
                    if (result != null && result.success) successCount++;
                    else failureCount++;
                    String summary = result == null ? "No result" : result.summary;
                    StringBuilder line = new StringBuilder();
                    line.append('[').append(entry).append("] ").append(summary).append('\n');
                    if (result != null && !TextUtils.isEmpty(result.details)) {
                        line.append(result.details.endsWith("\n") ? result.details : (result.details + "\n"));
                    }
                    updateStatus(true, "Disassembling all DEX: " + (i + 1) + " / " + total, line.toString(), false);
                } catch (Throwable entryError) {
                    failureCount++;
                    String msg = entryError.getClass().getSimpleName() + ": " + entryError.getMessage();
                    updateStatus(true, "Disassembling all DEX: " + (i + 1) + " / " + total,
                            "[" + entry + "] skipped: " + msg + "\n", false);
                }
            }

            String status = "Disassembled " + successCount + " / " + total + " DEX entries"
                    + (failureCount > 0 ? ("; failed/skipped " + failureCount + ".") : ".");
            updateStatus(false, status, "[Debugging] Disassemble all DEX complete. success="
                    + successCount + "/" + total + " failed=" + failureCount + "\n", false);
            updateNotification(status);
        } catch (Throwable t) {
            String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
            updateStatus(false, "Disassemble All DEX failed: " + msg, "[Debugging] Disassemble all DEX failed: " + msg + "\n", false);
            updateNotification("Disassemble All DEX failed");
        } finally {
            if (ACTION_DISASSEMBLE_ALL.equals(workerAction)) workerAction = null;
            stopSelf();
        }
    }


    private void runSearchEditor(ArrayList<String> roots,
                                 String displayRoot,
                                 String query,
                                 int filterMode,
                                 boolean excludeEnabled,
                                 String excludeTerms,
                                 int maxFiles,
                                 int pageSize,
                                 int pageIndex) {
        try {
            ArrayList<File> rootFiles = new ArrayList<>();
            if (roots != null) {
                for (String root : roots) {
                    if (!TextUtils.isEmpty(root)) rootFiles.add(new File(root));
                }
            }
            updateSearchStatus(true, pageIndex <= 0 ? "Searching smali/java folders..." : "Searching smali/java page " + (pageIndex + 1) + "...", null, "", true);
            updateNotification("Searching smali/java files...");
            ArrayList<String> excludes = splitSerializedTerms(excludeTerms);
            SmaliEditorSearch.Criteria criteria = new SmaliEditorSearch.Criteria(query, filterMode, excludeEnabled, excludes);
            SmaliEditorSearch.Page page = SmaliEditorSearch.buildPage(rootFiles, criteria,
                    maxFiles, pageSize, pageIndex, (file, line, preview) -> formatSearchLabel(file, line, preview, displayRoot));
            String status = formatSearchStatus(page);
            updateSearchStatus(false, status, page, "", true);
            updateNotification(status);
        } catch (Throwable t) {
            String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
            updateSearchStatus(false, "Smali/Java search failed: " + msg, null, msg, true);
            updateNotification("Smali/Java search failed");
        } finally {
            if (ACTION_SEARCH_EDITOR.equals(workerAction)) workerAction = null;
            stopSelf();
        }
    }

    private void updateSearchStatus(boolean running, String status, SmaliEditorSearch.Page page, String error, boolean clearRowsWhenNoPage) {
        try {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_SEARCH_RUNNING, running)
                    .putString(PREF_SEARCH_STATUS, status == null ? "" : status)
                    .putString(PREF_SEARCH_ERROR, error == null ? "" : error)
                    .putLong(PREF_SEARCH_UPDATED_AT, System.currentTimeMillis());
            if (page != null) {
                editor.putBoolean(PREF_SEARCH_HAS_RESULT, true)
                        .putString(PREF_SEARCH_ROWS, encodeRows(page))
                        .putString(PREF_SEARCH_QUERY_TEXT, page.query)
                        .putInt(PREF_SEARCH_SCANNED_FILES, page.scannedFiles)
                        .putInt(PREF_SEARCH_TOTAL_RESULTS, page.totalResults)
                        .putInt(PREF_SEARCH_PAGE_INDEX_RESULT, page.pageIndex)
                        .putInt(PREF_SEARCH_PAGE_SIZE_RESULT, page.pageSize);
            } else if (clearRowsWhenNoPage) {
                editor.putBoolean(PREF_SEARCH_HAS_RESULT, false)
                        .putString(PREF_SEARCH_ROWS, "")
                        .putString(PREF_SEARCH_QUERY_TEXT, "")
                        .putInt(PREF_SEARCH_SCANNED_FILES, 0)
                        .putInt(PREF_SEARCH_TOTAL_RESULTS, 0)
                        .putInt(PREF_SEARCH_PAGE_INDEX_RESULT, 0)
                        .putInt(PREF_SEARCH_PAGE_SIZE_RESULT, SmaliEditorSearch.DEFAULT_MAX_RESULTS);
            }
            editor.apply();
        } catch (Throwable ignored) {
        }
    }

    private String formatSearchStatus(SmaliEditorSearch.Page page) {
        try {
            String mode = page == null || TextUtils.isEmpty(page.query) ? "files" : "matches";
            int total = page == null ? 0 : page.totalResults;
            int scannedFiles = page == null ? 0 : page.scannedFiles;
            if (total <= 0) return "Found 0 " + mode + " in " + scannedFiles + " smali/java files.";
            int first = page.pageIndex * page.pageSize + 1;
            int last = Math.min(total, first + page.results.size() - 1);
            int pages = Math.max(1, (total + page.pageSize - 1) / page.pageSize);
            return "Showing " + first + "-" + last + " of " + total + " " + mode
                    + " (page " + (page.pageIndex + 1) + "/" + pages + ") in "
                    + scannedFiles + " smali/java files.";
        } catch (Throwable ignored) {
            return "Smali/Java search complete.";
        }
    }

    private String formatSearchLabel(File file, int line, String preview, String displayRoot) {
        String path = file == null ? "" : file.getAbsolutePath();
        try {
            if (!TextUtils.isEmpty(displayRoot) && path.startsWith(displayRoot + File.separator)) {
                path = path.substring(displayRoot.length() + 1);
            }
        } catch (Throwable ignored) {
        }
        if (line > 0 && !TextUtils.isEmpty(preview)) return path + ":" + line + "  " + preview;
        if (line > 0) return path + ":" + line;
        return path;
    }

    private String encodeRows(SmaliEditorSearch.Page page) {
        StringBuilder sb = new StringBuilder();
        try {
            if (page == null || page.results == null) return "";
            for (SmaliEditorSearch.Result result : page.results) {
                if (result == null || result.file == null) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(encode(result.file.getAbsolutePath())).append('\t')
                        .append(result.line).append('\t')
                        .append(encode(result.preview)).append('\t')
                        .append(encode(result.label));
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private static String encode(String value) {
        try {
            return Base64.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static ArrayList<String> splitSerializedTerms(String value) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(value)) return out;
        String[] lines = value.split("\\n");
        for (String line : lines) {
            if (!TextUtils.isEmpty(line)) out.add(line);
        }
        return out;
    }


    private void updateJadxStatus(boolean running, String status, String appendLog, String outDir, String zipPath, boolean clearLog) {
        updateJadxStatus(running, status, appendLog, outDir, zipPath, clearLog, 0, 0, "");
    }

    private void updateJadxStatus(boolean running, String status, String appendLog, String outDir, String zipPath, boolean clearLog, int current, int total, String phase) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            String log = clearLog ? "" : sp.getString(PREF_JADX_LOG, "");
            if (!TextUtils.isEmpty(appendLog)) log += appendLog;
            if (log.length() > MAX_JOB_LOG_CHARS) log = log.substring(log.length() - MAX_JOB_LOG_CHARS);
            SharedPreferences.Editor editor = sp.edit()
                    .putBoolean(PREF_JADX_RUNNING, running)
                    .putString(PREF_JADX_STATUS, status == null ? "" : status)
                    .putString(PREF_JADX_LOG, log)
                    .putLong(PREF_JADX_UPDATED_AT, System.currentTimeMillis())
                    .putInt(PREF_JADX_PROGRESS_CURRENT, Math.max(0, current))
                    .putInt(PREF_JADX_PROGRESS_TOTAL, Math.max(0, total))
                    .putString(PREF_JADX_PHASE, phase == null ? "" : phase);
            if (outDir != null) editor.putString(PREF_JADX_OUT_DIR, outDir);
            if (zipPath != null) editor.putString(PREF_JADX_ZIP_PATH, zipPath);
            if (!running) editor.putString(PREF_JADX_ERROR, status != null && status.startsWith("DEX to Java failed") ? status : "");
            editor.apply();
        } catch (Throwable ignored) {
        }
    }

    private void updateStatus(boolean running, String status, String appendLog, boolean clearLog) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            String log = clearLog ? "" : sp.getString(PREF_JOB_LOG, "");
            if (!TextUtils.isEmpty(appendLog)) log += appendLog;
            if (log.length() > MAX_JOB_LOG_CHARS) log = log.substring(log.length() - MAX_JOB_LOG_CHARS);
            sp.edit()
                    .putBoolean(PREF_JOB_RUNNING, running)
                    .putString(PREF_JOB_STATUS, status == null ? "" : status)
                    .putString(PREF_JOB_LOG, log)
                    .putLong(PREF_JOB_UPDATED_AT, System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private void startJobHeartbeat(String runningKey, String updatedAtKey) {
        stopJobHeartbeat();
        if (TextUtils.isEmpty(runningKey) || TextUtils.isEmpty(updatedAtKey)) return;
        jobHeartbeatActive = true;
        jobHeartbeatThread = new Thread(() -> {
            while (jobHeartbeatActive && !stopRequested) {
                try {
                    Thread.sleep(5000L);
                    SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                    if (!sp.getBoolean(runningKey, false)) break;
                    sp.edit().putLong(updatedAtKey, System.currentTimeMillis()).apply();
                } catch (InterruptedException ignored) {
                    break;
                } catch (Throwable ignored) {
                }
            }
        }, "PermsTest-DebugJobHeartbeat");
        try { jobHeartbeatThread.setDaemon(true); } catch (Throwable ignored) {}
        jobHeartbeatThread.start();
    }

    private void stopJobHeartbeat() {
        jobHeartbeatActive = false;
        try { if (jobHeartbeatThread != null) jobHeartbeatThread.interrupt(); } catch (Throwable ignored) {}
        jobHeartbeatThread = null;
    }


    private Notification buildNotification(String status) {
        ensureChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag());
        Intent stop = new Intent(this, PermsTestSmaliJobService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag());
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("PermsTest Debugging")
                .setContentText(status == null ? "Debugging job running" : status)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(status == null ? "Debugging job running" : status))
                .setContentIntent(openIntent)
                .addAction(R.mipmap.ic_launcher, "Stop", stopIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String status) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(status));
        } catch (Throwable ignored) {
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Debugging jobs", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Long-running PermsTest smali/baksmali and DEX-to-Java jobs");
        nm.createNotificationChannel(ch);
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private static int pendingIntentImmutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }
}
