package dev.perms.test.packages;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.debugging.DebuggingWorkPaths;

/** Extracts installed APK paths into local package/debugging work folders. */
public final class InstalledPackageExtractor {
    public interface ShellRunner {
        CommandResult run(String command);
    }

    public static final class CommandResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    public static final class ExtractedInstalledPackage {
        public final File workDir;
        public final File packageFile;
        public final String sourceLabel;
        public final int apkCount;

        ExtractedInstalledPackage(File workDir, File packageFile, String sourceLabel, int apkCount) {
            this.workDir = workDir;
            this.packageFile = packageFile;
            this.sourceLabel = sourceLabel;
            this.apkCount = apkCount;
        }
    }

    private final ShellRunner shellRunner;

    public InstalledPackageExtractor(ShellRunner shellRunner) {
        this.shellRunner = shellRunner;
    }

    public ExtractedInstalledPackage extractForPublicExport(String packageName, String displayName, File exportRoot) throws IOException {
        final String pkg = packageName == null ? "" : packageName.trim();
        if (isEmpty(pkg)) throw new IOException("Missing package name.");
        if (exportRoot == null) throw new IOException("Missing output root.");

        ArrayList<String> remotePaths = resolveInstalledApkPaths(pkg);
        String cleanLabel = cleanDisplayLabel(displayName, pkg);
        File outDir = new File(exportRoot, safeWorkName(cleanLabel + "-" + pkg) + "_" + System.currentTimeMillis());
        ensurePublicDirectory(outDir);

        ArrayList<File> localApks = copyInstalledApks(remotePaths, outDir);
        File packageFile = localApks.size() == 1 ? localApks.get(0) : new File(outDir, safeWorkName(cleanLabel + "-" + pkg) + ".apks");
        if (localApks.size() > 1) writeApksArchive(localApks, packageFile);
        return new ExtractedInstalledPackage(outDir, packageFile, packageFile.getName(), localApks.size());
    }

    public ExtractedInstalledPackage extractForDebug(String packageName, String displayName, File debugRoot) throws IOException {
        final String pkg = packageName == null ? "" : packageName.trim();
        if (isEmpty(pkg)) throw new IOException("Missing package name.");
        if (debugRoot == null) throw new IOException("External files directory is unavailable.");
        if (!debugRoot.exists() && !debugRoot.mkdirs()) throw new IOException("Unable to create extraction root.");

        ArrayList<String> remotePaths = resolveInstalledApkPaths(pkg);
        File workDir = new File(debugRoot, safeWorkName(pkg) + "_" + System.currentTimeMillis());
        if (!workDir.exists() && !workDir.mkdirs()) throw new IOException("Unable to create extraction work directory.");

        ArrayList<File> localApks = copyInstalledApks(remotePaths, workDir);
        String cleanLabel = cleanDisplayLabel(displayName, pkg);
        String sourceLabel;
        File packageFile;
        if (localApks.size() == 1) {
            packageFile = localApks.get(0);
            sourceLabel = cleanLabel + "-" + pkg + ".apk";
        } else {
            sourceLabel = cleanLabel + "-" + pkg + ".apks";
            packageFile = new File(workDir, sourceLabel);
            writeApksArchive(localApks, packageFile);
        }
        return new ExtractedInstalledPackage(workDir, packageFile, sourceLabel, localApks.size());
    }

    public static File choosePrimaryApk(ExtractedInstalledPackage extracted) {
        if (extracted == null) return null;
        if (extracted.packageFile != null && extracted.packageFile.isFile()
                && extracted.packageFile.getName().toLowerCase(Locale.US).endsWith(".apk")) {
            return extracted.packageFile;
        }
        File base = extracted.workDir == null ? null : new File(extracted.workDir, "base.apk");
        if (base != null && base.isFile()) return base;
        File[] files = extracted.workDir == null ? null : extracted.workDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file != null && file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".apk")) return file;
            }
        }
        return extracted.packageFile;
    }

    private ArrayList<String> resolveInstalledApkPaths(String pkg) throws IOException {
        CommandResult pathResult = runShell("pm path " + shQuote(pkg));
        if (pathResult == null || pathResult.exitCode != 0) {
            String err = pathResult == null ? "pm path failed." : pathResult.stderr;
            throw new IOException(isEmpty(err) ? "pm path failed." : err.trim());
        }
        ArrayList<String> remotePaths = parsePmPathOutput(pathResult.stdout);
        if (remotePaths.isEmpty()) {
            throw new IOException("No installed APK paths were returned for " + pkg + ".");
        }
        return remotePaths;
    }

    private void ensurePublicDirectory(File outDir) throws IOException {
        File parent = outDir.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            CommandResult mkdirRoot = runShell("mkdir -p " + shQuote(parent.getAbsolutePath())
                    + " && chmod 775 " + shQuote(parent.getAbsolutePath()));
            if (mkdirRoot == null || mkdirRoot.exitCode != 0) {
                throw new IOException("Unable to create output root: " + parent.getAbsolutePath());
            }
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            CommandResult mkdirOut = runShell("mkdir -p " + shQuote(outDir.getAbsolutePath())
                    + " && chmod 775 " + shQuote(outDir.getAbsolutePath()));
            if (mkdirOut == null || mkdirOut.exitCode != 0) {
                throw new IOException("Unable to create output directory: " + outDir.getAbsolutePath());
            }
        }
    }

    private ArrayList<File> copyInstalledApks(ArrayList<String> remotePaths, File outDir) throws IOException {
        ArrayList<File> localApks = new ArrayList<>();
        LinkedHashSet<String> usedNames = new LinkedHashSet<>();
        for (int i = 0; i < remotePaths.size(); i++) {
            String src = remotePaths.get(i);
            File dst = new File(outDir, installedApkLocalName(src, i, usedNames));
            copyInstalledApkWithShell(src, dst);
            if (!dst.isFile() || dst.length() <= 0) {
                throw new IOException("Extracted APK is empty: " + dst.getName());
            }
            localApks.add(dst);
        }
        return localApks;
    }

    private CommandResult runShell(String command) {
        if (shellRunner == null) return new CommandResult(1, "", "Shell runner is unavailable.");
        return shellRunner.run(command);
    }

    private static ArrayList<String> parsePmPathOutput(String stdout) {
        ArrayList<String> out = new ArrayList<>();
        if (isEmpty(stdout)) return out;
        String[] lines = stdout.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) continue;
            String v = line.trim();
            if (v.startsWith("package:")) v = v.substring("package:".length());
            if (v.endsWith(".apk") && !out.contains(v)) out.add(v);
        }
        return out;
    }

    private void copyInstalledApkWithShell(String sourcePath, File destFile) throws IOException {
        if (isEmpty(sourcePath) || destFile == null) throw new IOException("Missing APK path.");
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create output directory.");
        String dst = destFile.getAbsolutePath();
        String cmd = "rm -f " + shQuote(dst)
                + " && (cat " + shQuote(sourcePath) + " > " + shQuote(dst)
                + " || cp " + shQuote(sourcePath) + " " + shQuote(dst) + ")"
                + " && chmod 644 " + shQuote(dst)
                + " && test -s " + shQuote(dst);
        CommandResult res = runShell(cmd);
        if (res == null || res.exitCode != 0) {
            String err = res == null ? "copy failed" : (isEmpty(res.stderr) ? res.stdout : res.stderr);
            throw new IOException("Failed to extract installed APK: " + (isEmpty(err) ? sourcePath : err.trim()));
        }
    }

    private static String installedApkLocalName(String path, int index, LinkedHashSet<String> usedNames) {
        String name = null;
        try { name = new File(path == null ? "" : path).getName(); } catch (Throwable ignored) {}
        if (isEmpty(name) || !name.toLowerCase(Locale.US).endsWith(".apk")) {
            name = index == 0 ? "base.apk" : ("split_" + index + ".apk");
        }
        if ("base.apk".equalsIgnoreCase(name) || (path != null && path.toLowerCase(Locale.US).endsWith("/base.apk"))) {
            name = "base.apk";
        }
        String clean = ApkDebugToolHelper.sanitizeSourceName(name);
        if (!clean.toLowerCase(Locale.US).endsWith(".apk")) clean += ".apk";
        String unique = clean;
        int n = 1;
        while (usedNames.contains(unique)) {
            int dot = clean.toLowerCase(Locale.US).lastIndexOf(".apk");
            String stem = dot > 0 ? clean.substring(0, dot) : clean;
            unique = stem + "_" + (++n) + ".apk";
        }
        usedNames.add(unique);
        return unique;
    }

    private static void writeApksArchive(List<File> apks, File outFile) throws IOException {
        if (apks == null || apks.isEmpty()) throw new IOException("No APKs to archive.");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile, false)))) {
            byte[] buf = new byte[64 * 1024];
            for (File apk : apks) {
                if (apk == null || !apk.isFile()) continue;
                ZipEntry ze = new ZipEntry(apk.getName());
                ze.setTime(apk.lastModified());
                zos.putNextEntry(ze);
                try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(apk))) {
                    int r;
                    while ((r = in.read(buf)) > 0) zos.write(buf, 0, r);
                }
                zos.closeEntry();
            }
        }
        if (!outFile.isFile() || outFile.length() <= 0) throw new IOException("Archive was not created.");
    }

    private static String cleanDisplayLabel(String displayName, String pkg) {
        String cleanLabel = ApkDebugToolHelper.sanitizeSourceName(isEmpty(displayName) ? pkg : displayName);
        return cleanLabel.replaceAll("\\.(apk|apks|apkm|xapk|zip)$", "");
    }

    private static String safeWorkName(String value) {
        return DebuggingWorkPaths.safeWorkName(value);
    }

    private static boolean isEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }

    private static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
