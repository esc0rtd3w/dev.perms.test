package dev.perms.test.packages.editor;

import android.content.Context;
import android.text.TextUtils;

import dev.perms.test.debugging.smali.PermsTestSmaliTools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in smali workspace helpers for APK Editor.
 *
 * This path intentionally does not depend on apktool. It edits DEX payloads only:
 * APK/APKS/APKM/XAPK structure is preserved, resources are not decoded, and resource
 * or manifest package rename remains the apktool-backed path.
 */
public final class ApkEditorSmaliWorkspace {
    public interface ProgressCallback {
        void onProgress(String status, String logLine);
        boolean isStopRequested();
    }

    public static final class Workspace {
        public final File workspaceRoot;
        public final File editApk;
        public final File originalPackage;
        public final File smaliRoot;
        public final File dexRoot;
        public final File outputPackage;
        public final ArrayList<String> dexEntries;
        public final boolean splitArchive;
        public final String splitBaseEntryName;
        public final String report;

        Workspace(File workspaceRoot,
                  File editApk,
                  File originalPackage,
                  File smaliRoot,
                  File dexRoot,
                  File outputPackage,
                  ArrayList<String> dexEntries,
                  boolean splitArchive,
                  String splitBaseEntryName,
                  String report) {
            this.workspaceRoot = workspaceRoot;
            this.editApk = editApk;
            this.originalPackage = originalPackage;
            this.smaliRoot = smaliRoot;
            this.dexRoot = dexRoot;
            this.outputPackage = outputPackage;
            this.dexEntries = dexEntries == null ? new ArrayList<>() : dexEntries;
            this.splitArchive = splitArchive;
            this.splitBaseEntryName = splitBaseEntryName == null ? "" : splitBaseEntryName;
            this.report = report == null ? "" : report;
        }

        public File firstSmaliDir() {
            if (dexEntries.isEmpty()) return smaliRoot;
            return smaliDirFor(dexEntries.get(0));
        }

        public File firstDexOut() {
            if (dexEntries.isEmpty()) return new File(dexRoot, "classes.dex");
            return dexOutFor(dexEntries.get(0));
        }

        public File smaliDirFor(String dexEntry) {
            return new File(smaliRoot, PermsTestSmaliTools.defaultSmaliFolderNameForDexEntry(dexEntry));
        }

        public File dexOutFor(String dexEntry) {
            return new File(dexRoot, PermsTestSmaliTools.defaultDexOutputNameForDexEntry(dexEntry));
        }

        public String dexEntryForSmaliFile(File file) {
            if (file == null) return dexEntries.isEmpty() ? "classes.dex" : dexEntries.get(0);
            try {
                String filePath = file.getCanonicalPath();
                for (String entry : dexEntries) {
                    File dir = smaliDirFor(entry);
                    String dirPath = dir.getCanonicalPath();
                    if (filePath.equals(dirPath) || filePath.startsWith(dirPath + File.separator)) {
                        return entry;
                    }
                }
            } catch (Throwable ignored) {
            }
            return dexEntries.isEmpty() ? "classes.dex" : dexEntries.get(0);
        }

        public File smaliDirForFile(File file) {
            return smaliDirFor(dexEntryForSmaliFile(file));
        }

        public File dexOutForFile(File file) {
            return dexOutFor(dexEntryForSmaliFile(file));
        }
    }

    public static final class RepackResult {
        public final File unsignedBaseApk;
        public final Map<String, String> replacements;
        public final String report;

        RepackResult(File unsignedBaseApk, Map<String, String> replacements, String report) {
            this.unsignedBaseApk = unsignedBaseApk;
            this.replacements = replacements;
            this.report = report == null ? "" : report;
        }
    }

    private ApkEditorSmaliWorkspace() {
    }

    private static void progress(ProgressCallback callback, String status, String logLine) throws InterruptedException {
        if (callback == null) return;
        if (callback.isStopRequested()) throw new InterruptedException("APK Editor smali decompile was stopped.");
        callback.onProgress(status, logLine);
        if (callback.isStopRequested()) throw new InterruptedException("APK Editor smali decompile was stopped.");
    }

    public static Workspace disassemble(Context context,
                                        File stagedPackage,
                                        File workspaceRoot,
                                        boolean splitAware,
                                        int apiLevel) throws Exception {
        return disassemble(context, stagedPackage, workspaceRoot, splitAware, apiLevel, null);
    }

    public static Workspace disassemble(Context context,
                                        File stagedPackage,
                                        File workspaceRoot,
                                        boolean splitAware,
                                        int apiLevel,
                                        ProgressCallback progress) throws Exception {
        if (stagedPackage == null || !stagedPackage.isFile()) throw new IOException("Pick a package file first.");
        if (workspaceRoot == null) throw new IOException("Workspace is unavailable.");
        if (!workspaceRoot.exists() && !workspaceRoot.mkdirs()) {
            throw new IOException("Unable to create workspace: " + workspaceRoot.getAbsolutePath());
        }

        File editApk = stagedPackage;
        boolean split = false;
        String splitBaseEntry = "";
        StringBuilder report = new StringBuilder();
        report.append("[APK Editor] Built-in smali mode does not use apktool. It edits DEX files only.\n");
        progress(progress, "Preparing built-in smali workspace...", "[APK Editor] Preparing built-in smali workspace.\n");

        if (splitAware && ApkEditorSplitArchive.isSplitContainer(stagedPackage)) {
            ApkEditorSplitArchive.Workspace splitWorkspace = ApkEditorSplitArchive.extractSplitWorkspace(stagedPackage, workspaceRoot);
            editApk = splitWorkspace.baseApk;
            split = true;
            splitBaseEntry = splitWorkspace.info.baseEntryName;
            report.append("[APK Editor] Split-aware smali mode: using base APK ")
                    .append(splitBaseEntry)
                    .append(" and preserving companion splits during repack.\n");
            progress(progress, "Split archive extracted. Scanning base APK DEX entries...",
                    "[APK Editor] Split-aware smali mode: using base APK " + splitBaseEntry + "\n");
        }

        progress(progress, "Scanning DEX entries in " + editApk.getName() + "...", "");
        PermsTestSmaliTools.DexEntryScanResult scan = PermsTestSmaliTools.listDexEntriesDetailed(editApk.getAbsolutePath());
        ArrayList<String> dexEntries = new ArrayList<>();
        if (scan != null && scan.entries != null) dexEntries.addAll(scan.entries);
        if (dexEntries.isEmpty()) throw new IOException("No top-level DEX entries were found in " + editApk.getName());
        progress(progress, "Found " + dexEntries.size() + (dexEntries.size() == 1 ? " DEX file." : " DEX files."),
                "[APK Editor] Found " + dexEntries.size() + " DEX entr" + (dexEntries.size() == 1 ? "y" : "ies") + ": " + dexEntries + "\n");

        File smaliRoot = new File(workspaceRoot, "smali");
        File dexRoot = new File(workspaceRoot, "dex");
        ApkEditorFileUtils.deleteTree(smaliRoot);
        ApkEditorFileUtils.deleteTree(dexRoot);
        if (!smaliRoot.exists() && !smaliRoot.mkdirs()) throw new IOException("Unable to create " + smaliRoot.getAbsolutePath());
        if (!dexRoot.exists() && !dexRoot.mkdirs()) throw new IOException("Unable to create " + dexRoot.getAbsolutePath());

        for (int i = 0; i < dexEntries.size(); i++) {
            String entry = dexEntries.get(i);
            File outDir = new File(smaliRoot, PermsTestSmaliTools.defaultSmaliFolderNameForDexEntry(entry));
            String current = "Decompiling " + entry + " (" + (i + 1) + " / " + dexEntries.size() + ")...";
            progress(progress, current, "[APK Editor] " + current + " -> " + outDir.getAbsolutePath() + "\n");
            PermsTestSmaliTools.ToolResult result = PermsTestSmaliTools.disassemble(
                    editApk.getAbsolutePath(), entry, outDir.getAbsolutePath(), apiLevel, true);
            if (result == null || !result.success) {
                throw new IOException(result == null ? ("baksmali failed for " + entry) : result.summary);
            }
            report.append("[APK Editor] ").append(entry).append(" -> ").append(outDir.getAbsolutePath()).append('\n');
            progress(progress, "Finished " + entry + " (" + (i + 1) + " / " + dexEntries.size() + ")", "");
        }

        File outputPackage = new File(workspaceRoot,
                ApkEditorFileUtils.stem(stagedPackage.getName()) + "-smali-debuggable" + (split ? ".apks" : ".apk"));
        report.append("[APK Editor] Smali workspace ready: ").append(smaliRoot.getAbsolutePath()).append('\n');
        progress(progress, "Smali workspace ready: " + dexEntries.size()
                + (dexEntries.size() == 1 ? " DEX file." : " DEX files."), "");
        return new Workspace(workspaceRoot, editApk, stagedPackage, smaliRoot, dexRoot, outputPackage,
                dexEntries, split, splitBaseEntry, report.toString());
    }

    public static Workspace restoreExisting(File stagedPackage, File workspaceRoot, boolean splitAware) throws Exception {
        if (stagedPackage == null || !stagedPackage.isFile()) throw new IOException("Pick a package file first.");
        if (workspaceRoot == null || !workspaceRoot.isDirectory()) throw new IOException("Run Smali Decompile first.");
        File editApk = stagedPackage;
        boolean split = false;
        String splitBaseEntry = "";
        if (splitAware && ApkEditorSplitArchive.isSplitContainer(stagedPackage)) {
            ApkEditorSplitArchive.Workspace splitWorkspace = ApkEditorSplitArchive.extractSplitWorkspace(stagedPackage, workspaceRoot);
            editApk = splitWorkspace.baseApk;
            split = true;
            splitBaseEntry = splitWorkspace.info.baseEntryName;
        }
        File smaliRoot = new File(workspaceRoot, "smali");
        File dexRoot = new File(workspaceRoot, "dex");
        if (!smaliRoot.isDirectory()) throw new IOException("Run Smali Decompile first.");
        PermsTestSmaliTools.DexEntryScanResult scan = PermsTestSmaliTools.listDexEntriesDetailed(editApk.getAbsolutePath());
        ArrayList<String> dexEntries = new ArrayList<>();
        if (scan != null && scan.entries != null) dexEntries.addAll(scan.entries);
        if (dexEntries.isEmpty()) throw new IOException("No DEX entries are available for repack.");
        File outputPackage = new File(workspaceRoot,
                ApkEditorFileUtils.stem(stagedPackage.getName()) + "-smali-debuggable" + (split ? ".apks" : ".apk"));
        return new Workspace(workspaceRoot, editApk, stagedPackage, smaliRoot, dexRoot, outputPackage,
                dexEntries, split, splitBaseEntry, "");
    }

    public static RepackResult assembleAndRebuildBase(Workspace workspace,
                                                      int apiLevel,
                                                      boolean verboseErrors) throws Exception {
        if (workspace == null) throw new IOException("Run Smali Decompile first.");
        if (workspace.editApk == null || !workspace.editApk.isFile()) throw new IOException("Edited base APK is missing.");
        if (workspace.dexEntries.isEmpty()) throw new IOException("No DEX entries are available for repack.");

        if (!workspace.dexRoot.exists() && !workspace.dexRoot.mkdirs()) {
            throw new IOException("Unable to create " + workspace.dexRoot.getAbsolutePath());
        }
        File buildDir = new File(workspace.workspaceRoot, "smali_build");
        if (!buildDir.exists() && !buildDir.mkdirs()) throw new IOException("Unable to create " + buildDir.getAbsolutePath());

        LinkedHashMap<String, String> replacements = new LinkedHashMap<>();
        StringBuilder report = new StringBuilder();
        for (String entry : workspace.dexEntries) {
            File smaliDir = workspace.smaliDirFor(entry);
            if (PermsTestSmaliTools.countSmaliSources(smaliDir.getAbsolutePath()) <= 0) continue;
            File dexOut = workspace.dexOutFor(entry);
            PermsTestSmaliTools.ToolResult assemble = PermsTestSmaliTools.assemble(
                    smaliDir.getAbsolutePath(), dexOut.getAbsolutePath(), apiLevel, verboseErrors);
            if (assemble == null || !assemble.success) {
                throw new IOException(assemble == null ? ("smali assemble failed for " + entry) : assemble.summary);
            }
            replacements.put(PermsTestSmaliTools.normalizeDexEntryName(entry), dexOut.getAbsolutePath());
            report.append("[APK Editor] ").append(entry).append(" assembled: ").append(dexOut.getAbsolutePath()).append('\n');
        }
        if (replacements.isEmpty()) throw new IOException("No smali folders with sources were found.");

        File unsignedBase = new File(buildDir, "rebuilt-base-unsigned.apk");
        PermsTestSmaliTools.ToolResult rebuild = PermsTestSmaliTools.rebuildApkWithDexReplacements(
                workspace.editApk.getAbsolutePath(), replacements, unsignedBase.getAbsolutePath());
        if (rebuild == null || !rebuild.success || !unsignedBase.isFile()) {
            throw new IOException(rebuild == null ? "APK DEX replacement failed." : rebuild.summary);
        }
        report.append("[APK Editor] Rebuilt base APK with ").append(replacements.size()).append(" DEX replacement(s).\n");
        if (!TextUtils.isEmpty(rebuild.details)) report.append(rebuild.details.endsWith("\n") ? rebuild.details : (rebuild.details + "\n"));
        return new RepackResult(unsignedBase, replacements, report.toString());
    }

    public static File findFirstSmaliFile(File root) {
        if (root == null || !root.exists()) return null;
        if (root.isFile()) {
            String name = root.getName();
            return name != null && name.toLowerCase(java.util.Locale.US).endsWith(".smali") ? root : null;
        }
        File[] children = root.listFiles();
        if (children == null) return null;
        java.util.Arrays.sort(children, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
        for (File child : children) {
            File hit = findFirstSmaliFile(child);
            if (hit != null) return hit;
        }
        return null;
    }

    public static boolean hasSmaliWorkspace(File workspaceRoot) {
        return workspaceRoot != null && new File(workspaceRoot, "smali").isDirectory();
    }
}
