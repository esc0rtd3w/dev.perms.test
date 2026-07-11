package dev.perms.test.debugging.smali;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.debugging.DebuggingUi;
import dev.perms.test.debugging.jobs.SmaliDisassembleAllJobClient;

/**
 * Owns Debugging-tab smali/baksmali actions that used to live directly in MainActivity.
 *
 * The controller keeps UI behavior the same while MainActivity only supplies the small
 * set of shared services that these actions need.
 */
public final class DebuggingSmaliActionController {
    public interface Host {
        Activity activity();
        ActivityMainBinding binding();
        ExecutorService ioExecutor();
        ExecutorService debugApkExecutor();
        boolean isDexEntrySelectionInvalid();
        ArrayList<String> currentDexEntries();
        String currentDexEntry();
        String smaliDirForEntry(String dexEntry, boolean preferCurrentField);
        String dexOutForEntry(String dexEntry, boolean preferCurrentField);
        boolean shouldRefreshDexEntriesIfInputPresent();
        void refreshDebuggingDexEntriesFromCurrentInput(boolean forcePathRefresh);
        void refreshDebuggingApkOutputPath(boolean forcePathRefresh);
        String exportRebuiltDebuggingApk(File rebuiltUnsigned, String apkOutput, boolean makeDebuggable, File workDir) throws Exception;
        void deleteTreeQuietly(File file);
        void setBusy(boolean busy, String status);
        void appendOutput(String text);
        void runOnUiThread(Runnable action);
        void finishError(String label, Throwable error);
    }

    private final Host host;
    private final Handler mainHandler;
    private SmaliDisassembleAllJobClient disassembleAllJobClient;

    public DebuggingSmaliActionController(Host host) {
        this.host = host;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void runDisassemble() {
        ActivityMainBinding binding = binding();
        if (binding == null || binding.tabDebugging == null) return;
        if (host.isDexEntrySelectionInvalid()) {
            host.finishError("baksmali", new IOException("No valid DEX entry is selected."));
            return;
        }
        final String input = safeText(binding.tabDebugging.edtSmaliDexInput);
        final String entry = host.currentDexEntry();
        final String output = safeText(binding.tabDebugging.edtSmaliOutDir);
        final int apiLevel = parseApiLevel();
        final boolean cleanOutput = binding.tabDebugging.chkSmaliCleanOutput != null && binding.tabDebugging.chkSmaliCleanOutput.isChecked();
        if (outputFolderHasFiles(output)) {
            confirmOutputOverwrite(
                    "Smali output already exists",
                    "The selected smali output folder already contains files.\n\n"
                            + output + "\n\n"
                            + (cleanOutput
                            ? "Clean Output is checked, so starting will clear and recreate this output folder."
                            : "Clean Output is unchecked, so starting can overwrite matching files and leave stale files from an older run."),
                    () -> startDisassemble(input, entry, output, apiLevel, cleanOutput));
            return;
        }
        startDisassemble(input, entry, output, apiLevel, cleanOutput);
    }

    private void startDisassemble(String input, String entry, String output, int apiLevel, boolean cleanOutput) {
        host.setBusy(true, "Disassembling...");
        executeIo(() -> {
            try {
                PermsTestSmaliTools.ToolResult result = PermsTestSmaliTools.disassemble(input, entry, output, apiLevel, cleanOutput);
                host.runOnUiThread(() -> finishToolRun("baksmali", result));
            } catch (Throwable t) {
                host.runOnUiThread(() -> host.finishError("baksmali", t));
            }
        });
    }

    public void runDisassembleAll() {
        ActivityMainBinding binding = binding();
        Activity activity = activity();
        if (binding == null || binding.tabDebugging == null || activity == null) return;
        if (isDisassembleAllServiceRunning()) {
            restoreDisassembleAllJobStatus();
            Toast.makeText(activity, "Disassemble All DEX is already running", Toast.LENGTH_SHORT).show();
            return;
        }

        final String input = safeText(binding.tabDebugging.edtSmaliDexInput);
        final int apiLevel = parseApiLevel();
        final boolean cleanOutput = binding.tabDebugging.chkSmaliCleanOutput != null && binding.tabDebugging.chkSmaliCleanOutput.isChecked();
        final ArrayList<String> dexEntries = host.currentDexEntries();
        final ArrayList<String> outDirs = new ArrayList<>();
        for (String entry : dexEntries) {
            outDirs.add(host.smaliDirForEntry(entry, true));
        }

        try {
            if (TextUtils.isEmpty(input)) throw new IOException("Choose an APK or DEX source first.");
            if (dexEntries.isEmpty()) throw new IOException("No DEX entries are available for this source.");
        } catch (Throwable t) {
            host.finishError("disassemble all DEX", t);
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("Disassemble ").append(dexEntries.size()).append(" DEX entr");
        msg.append(dexEntries.size() == 1 ? "y" : "ies");
        msg.append(" into the smali output tree.\n\n");
        ArrayList<String> existing = existingOutputFolders(outDirs);
        if (!existing.isEmpty()) {
            msg.append("Some smali output folders already contain files.\n");
            msg.append(formatFolderList(existing, 5)).append("\n");
            msg.append(cleanOutput
                    ? "Clean Output is checked, so starting will clear and recreate those output folders.\n\n"
                    : "Clean Output is unchecked, so starting can overwrite matching files and leave stale files from an older run.\n\n");
        }
        msg.append("This can take a while for large apps. It will run as a foreground Debugging job so it can continue if PermsTest is not visible.");
        new MaterialAlertDialogBuilder(activity)
                .setTitle(existing.isEmpty() ? "Disassemble All DEX" : "Smali output already exists")
                .setMessage(msg.toString())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(existing.isEmpty() ? "Start" : "Overwrite", (dialog, which) -> startDisassembleAllService(input, dexEntries, outDirs, apiLevel, cleanOutput))
                .show();
    }

    public void restoreDisassembleAllJobStatus() {
        disassembleAllJobClient().restore();
    }

    public void refreshDexEntriesIfInputPresent(boolean forcePathRefresh) {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabDebugging == null) return;
            if (isDisassembleAllServiceRunning()) return;
            String input = safeText(binding.tabDebugging.edtSmaliDexInput);
            if (TextUtils.isEmpty(input)) return;
            if (!host.shouldRefreshDexEntriesIfInputPresent()) return;
            host.setBusy(true, "Refreshing DEX entries...");
            host.refreshDebuggingDexEntriesFromCurrentInput(forcePathRefresh);
        } catch (Throwable ignored) {
        }
    }

    public void runAssemble() {
        ActivityMainBinding binding = binding();
        if (binding == null || binding.tabDebugging == null) return;
        final String input = safeText(binding.tabDebugging.edtSmaliInputDir);
        final String output = safeText(binding.tabDebugging.edtSmaliDexOutput);
        final int apiLevel = parseApiLevel();
        final boolean verbose = binding.tabDebugging.chkSmaliVerboseErrors == null || binding.tabDebugging.chkSmaliVerboseErrors.isChecked();
        host.setBusy(true, "Assembling...");
        executeIo(() -> {
            try {
                PermsTestSmaliTools.ToolResult result = PermsTestSmaliTools.assemble(input, output, apiLevel, verbose);
                host.runOnUiThread(() -> finishToolRun("smali", result));
            } catch (Throwable t) {
                host.runOnUiThread(() -> host.finishError("smali", t));
            }
        });
    }

    public void runReassembleApk() {
        ActivityMainBinding binding = binding();
        if (binding == null || binding.tabDebugging == null) return;
        final String sourceApkPath = safeText(binding.tabDebugging.edtSmaliDexInput);
        final String smaliInput = safeText(binding.tabDebugging.edtSmaliInputDir);
        final String dexOutput = safeText(binding.tabDebugging.edtSmaliDexOutput);
        final String dexEntry = host.currentDexEntry();
        final String apkOutput = safeText(binding.tabDebugging.edtSmaliApkOutput);
        final int apiLevel = parseApiLevel();
        final boolean verbose = binding.tabDebugging.chkSmaliVerboseErrors == null || binding.tabDebugging.chkSmaliVerboseErrors.isChecked();
        final boolean makeDebug = binding.tabDebugging.chkSmaliMakeDebugApk == null || binding.tabDebugging.chkSmaliMakeDebugApk.isChecked();
        host.setBusy(true, makeDebug ? "Reassembling debug APK..." : "Reassembling signed APK...");
        executeDebugApk(() -> {
            File workDir = null;
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
            try {
                if (TextUtils.isEmpty(sourceApkPath)) throw new IOException("Choose an APK source first.");
                if (!sourceApkPath.toLowerCase(Locale.US).endsWith(".apk")) {
                    throw new IOException("Reassemble APK currently needs a single APK source. Choose an installed app base APK or browse to an APK.");
                }
                if (TextUtils.isEmpty(apkOutput)) throw new IOException("Rebuilt APK output path is empty.");
                PermsTestSmaliTools.ToolResult dexResult = PermsTestSmaliTools.assemble(smaliInput, dexOutput, apiLevel, verbose);
                if (dexResult == null || !dexResult.success) {
                    throw new IOException(dexResult == null ? "smali assemble failed." : dexResult.summary);
                }

                File workRoot = activity().getExternalFilesDir("smali_rebuild_work");
                if (workRoot == null) throw new IOException("APK work directory is unavailable.");
                workDir = new File(workRoot, "rebuild_" + System.currentTimeMillis());
                if (!workDir.exists() && !workDir.mkdirs()) throw new IOException("Unable to create APK work directory.");
                File rebuiltUnsigned = new File(workDir, "rebuilt-unsigned.apk");
                PermsTestSmaliTools.ToolResult rebuildResult = PermsTestSmaliTools.rebuildApkWithDex(sourceApkPath, dexEntry, dexOutput, rebuiltUnsigned.getAbsolutePath());
                if (rebuildResult == null || !rebuildResult.success || !rebuiltUnsigned.isFile()) {
                    throw new IOException(rebuildResult == null ? "APK rebuild failed." : rebuildResult.summary);
                }

                final String exportOut = host.exportRebuiltDebuggingApk(rebuiltUnsigned, apkOutput, makeDebug, workDir);
                final String status = "Created " + (makeDebug ? "debug APK" : "signed APK") + ": " + apkOutput;
                final String rebuildDetails = rebuildResult.details == null ? "" : rebuildResult.details;
                host.runOnUiThread(() -> {
                    host.setBusy(false, status);
                    host.appendOutput("[Debugging] " + status + "\n");
                    if (!TextUtils.isEmpty(dexResult.summary)) host.appendOutput("[Debugging] " + dexResult.summary + "\n");
                    if (!TextUtils.isEmpty(rebuildDetails)) host.appendOutput(rebuildDetails.endsWith("\n") ? rebuildDetails : (rebuildDetails + "\n"));
                    if (!TextUtils.isEmpty(exportOut)) host.appendOutput(exportOut.endsWith("\n") ? exportOut : (exportOut + "\n"));
                });
            } catch (Throwable t) {
                host.runOnUiThread(() -> host.finishError("reassemble APK", t));
            } finally {
                if (workDir != null) host.deleteTreeQuietly(workDir);
            }
        });
    }

    public void runReassembleAllDex() {
        ActivityMainBinding binding = binding();
        if (binding == null || binding.tabDebugging == null) return;
        final String sourceApkPath = safeText(binding.tabDebugging.edtSmaliDexInput);
        final String apkOutput = safeText(binding.tabDebugging.edtSmaliApkOutput);
        final int apiLevel = parseApiLevel();
        final boolean verbose = binding.tabDebugging.chkSmaliVerboseErrors == null || binding.tabDebugging.chkSmaliVerboseErrors.isChecked();
        final boolean makeDebug = binding.tabDebugging.chkSmaliMakeDebugApk == null || binding.tabDebugging.chkSmaliMakeDebugApk.isChecked();
        final ArrayList<String> dexEntries = host.currentDexEntries();
        host.setBusy(true, makeDebug ? "Reassembling all debug APK DEX..." : "Reassembling all signed APK DEX...");
        executeDebugApk(() -> {
            File workDir = null;
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
            try {
                if (TextUtils.isEmpty(sourceApkPath)) throw new IOException("Choose an APK source first.");
                if (!sourceApkPath.toLowerCase(Locale.US).endsWith(".apk")) {
                    throw new IOException("Reassemble all DEX currently needs a single APK source.");
                }
                if (TextUtils.isEmpty(apkOutput)) throw new IOException("Rebuilt APK output path is empty.");
                LinkedHashMap<String, String> replacements = new LinkedHashMap<>();
                StringBuilder assembleDetails = new StringBuilder();
                for (String entry : dexEntries) {
                    String smaliInput = host.smaliDirForEntry(entry, true);
                    if (PermsTestSmaliTools.countSmaliSources(smaliInput) <= 0) continue;
                    String dexOutput = host.dexOutForEntry(entry, true);
                    PermsTestSmaliTools.ToolResult dexResult = PermsTestSmaliTools.assemble(smaliInput, dexOutput, apiLevel, verbose);
                    if (dexResult == null || !dexResult.success) {
                        throw new IOException(dexResult == null ? ("smali assemble failed for " + entry) : dexResult.summary);
                    }
                    replacements.put(PermsTestSmaliTools.normalizeDexEntryName(entry), dexOutput);
                    assembleDetails.append('[').append(entry).append("] ").append(dexResult.summary).append('\n');
                }
                if (replacements.isEmpty()) {
                    throw new IOException("No smali folders with content were found for the current DEX entries.");
                }

                File workRoot = activity().getExternalFilesDir("smali_rebuild_work");
                if (workRoot == null) throw new IOException("APK work directory is unavailable.");
                workDir = new File(workRoot, "rebuild_all_" + System.currentTimeMillis());
                if (!workDir.exists() && !workDir.mkdirs()) throw new IOException("Unable to create APK work directory.");
                File rebuiltUnsigned = new File(workDir, "rebuilt-unsigned.apk");
                PermsTestSmaliTools.ToolResult rebuildResult = PermsTestSmaliTools.rebuildApkWithDexReplacements(sourceApkPath, replacements, rebuiltUnsigned.getAbsolutePath());
                if (rebuildResult == null || !rebuildResult.success || !rebuiltUnsigned.isFile()) {
                    throw new IOException(rebuildResult == null ? "APK rebuild failed." : rebuildResult.summary);
                }

                String exportOut = host.exportRebuiltDebuggingApk(rebuiltUnsigned, apkOutput, makeDebug, workDir);
                final String status = "Created " + (makeDebug ? "debug APK" : "signed APK") + ": " + apkOutput;
                final String assembleOut = assembleDetails.toString();
                final String rebuildDetails = rebuildResult.details == null ? "" : rebuildResult.details;
                host.runOnUiThread(() -> {
                    host.setBusy(false, status);
                    host.appendOutput("[Debugging] " + status + "\n");
                    if (!TextUtils.isEmpty(assembleOut)) host.appendOutput(assembleOut);
                    if (!TextUtils.isEmpty(rebuildDetails)) host.appendOutput(rebuildDetails.endsWith("\n") ? rebuildDetails : (rebuildDetails + "\n"));
                    if (!TextUtils.isEmpty(exportOut)) host.appendOutput(exportOut.endsWith("\n") ? exportOut : (exportOut + "\n"));
                });
            } catch (Throwable t) {
                host.runOnUiThread(() -> host.finishError("reassemble all DEX", t));
            } finally {
                if (workDir != null) host.deleteTreeQuietly(workDir);
            }
        });
    }

    public void runListClasses() {
        ActivityMainBinding binding = binding();
        if (binding == null || binding.tabDebugging == null) return;
        final String input = safeText(binding.tabDebugging.edtSmaliDexInput);
        final String entry = host.currentDexEntry();
        final int apiLevel = parseApiLevel();
        host.setBusy(true, "Reading classes...");
        executeIo(() -> {
            try {
                PermsTestSmaliTools.ToolResult result = PermsTestSmaliTools.listClasses(input, entry, apiLevel);
                host.runOnUiThread(() -> finishToolRun("dex classes", result));
            } catch (Throwable t) {
                host.runOnUiThread(() -> host.finishError("dex classes", t));
            }
        });
    }

    private void finishToolRun(String label, PermsTestSmaliTools.ToolResult result) {
        host.setBusy(false, result == null ? "Done." : result.summary);
        if (result == null) return;
        host.appendOutput("[Debugging] " + label + ": " + result.summary + "\n");
        if (!TextUtils.isEmpty(result.details)) {
            host.appendOutput(result.details.endsWith("\n") ? result.details : result.details + "\n");
        }
        ActivityMainBinding binding = binding();
        if ("baksmali".equals(label) && result.success && !TextUtils.isEmpty(result.outputPath)
                && binding != null && binding.tabDebugging != null) {
            try { binding.tabDebugging.edtSmaliInputDir.setText(result.outputPath); } catch (Throwable ignored) {}
            host.refreshDebuggingApkOutputPath(false);
        }
    }

    private SmaliDisassembleAllJobClient disassembleAllJobClient() {
        if (disassembleAllJobClient == null) {
            Activity activity = activity();
            disassembleAllJobClient = new SmaliDisassembleAllJobClient(activity, mainHandler, new SmaliDisassembleAllJobClient.Callbacks() {
                @Override
                public void setBusy(boolean busy, String status) {
                    host.setBusy(busy, status);
                }

                @Override
                public void appendOutput(String text) {
                    host.appendOutput(text);
                }

                @Override
                public void refreshDexEntries() {
                    refreshDexEntriesIfInputPresent(false);
                }

                @Override
                public void finishError(String label, Throwable error) {
                    host.finishError(label, error);
                }

                @Override
                public void setStatusText(String status) {
                    ActivityMainBinding binding = binding();
                    if (binding != null && binding.tabDebugging != null && binding.tabDebugging.txtSmaliStatus != null) {
                        binding.tabDebugging.txtSmaliStatus.setText(status == null ? "" : status);
                    }
                }
            });
        }
        return disassembleAllJobClient;
    }

    private boolean isDisassembleAllServiceRunning() {
        return disassembleAllJobClient().isRunning();
    }

    private void startDisassembleAllService(String input, ArrayList<String> dexEntries, ArrayList<String> outDirs, int apiLevel, boolean cleanOutput) {
        disassembleAllJobClient().start(input, dexEntries, outDirs, apiLevel, cleanOutput);
    }

    private boolean outputFolderHasFiles(String path) {
        if (TextUtils.isEmpty(path)) return false;
        try {
            File file = new File(path);
            if (!file.exists()) return false;
            if (file.isFile()) return true;
            File[] children = file.listFiles();
            return children != null && children.length > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private ArrayList<String> existingOutputFolders(java.util.List<String> paths) {
        ArrayList<String> existing = new ArrayList<>();
        if (paths == null) return existing;
        for (String path : paths) {
            if (!TextUtils.isEmpty(path) && outputFolderHasFiles(path)) existing.add(path);
        }
        return existing;
    }

    private String formatFolderList(java.util.List<String> paths, int max) {
        if (paths == null || paths.isEmpty()) return "";
        int limit = Math.max(1, max);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.size() && i < limit; i++) {
            sb.append("• ").append(paths.get(i)).append('\n');
        }
        if (paths.size() > limit) sb.append("• ... ").append(paths.size() - limit).append(" more\n");
        return sb.toString();
    }

    private void confirmOutputOverwrite(String title, String message, Runnable onConfirm) {
        Activity activity = activity();
        if (activity == null || onConfirm == null) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Overwrite", (dialog, which) -> onConfirm.run())
                .show();
    }

    private int parseApiLevel() {
        return DebuggingUi.parseApiLevel(binding());
    }

    private void executeIo(Runnable action) {
        ExecutorService executor = host.ioExecutor();
        if (executor == null) {
            host.finishError("debugging", new IOException("Debugging IO executor is unavailable."));
            return;
        }
        executor.execute(action);
    }

    private void executeDebugApk(Runnable action) {
        ExecutorService executor = host.debugApkExecutor();
        if (executor == null) {
            host.finishError("debugging", new IOException("Debugging APK executor is unavailable."));
            return;
        }
        executor.execute(action);
    }

    private Activity activity() {
        return host == null ? null : host.activity();
    }

    private ActivityMainBinding binding() {
        return host == null ? null : host.binding();
    }

    private static String safeText(TextView tv) {
        return tv == null || tv.getText() == null ? "" : tv.getText().toString().trim();
    }
}
