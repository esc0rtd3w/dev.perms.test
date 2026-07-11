package dev.perms.test.apk;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class DebuggableApkCreator {
    public static final long STREAM_PACKAGE_THRESHOLD_BYTES = 50L * 1024L * 1024L;
    private static final int MAX_BINARY_MANIFEST_BYTES = 8 * 1024 * 1024;

    public static final class Result {
        public final boolean success;
        public final String message;
        public final File signedApk;
        public final boolean splitArchive;
        public final String detailLog;

        Result(boolean success, String message, File signedApk) {
            this(success, message, signedApk, false, null);
        }

        Result(boolean success, String message, File signedApk, boolean splitArchive) {
            this(success, message, signedApk, splitArchive, null);
        }

        Result(boolean success, String message, File signedApk, boolean splitArchive, String detailLog) {
            this.success = success;
            this.message = message;
            this.signedApk = signedApk;
            this.splitArchive = splitArchive;
            this.detailLog = detailLog == null ? "" : detailLog;
        }
    }

    public interface ToolRunner {
        ToolResult run(String command);
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

    private static final class ToolLogSet {
        private final ArrayList<File> files = new ArrayList<>();

        void add(File file) {
            if (file != null) files.add(file);
        }

        boolean hasLogs() {
            return !files.isEmpty();
        }

        String summary() {
            if (files.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("APK tool logs saved:\n");
            for (File file : files) {
                if (file == null) continue;
                sb.append("- ").append(file.getAbsolutePath()).append('\n');
            }
            return sb.toString();
        }
    }

    private DebuggableApkCreator() {
    }

    public static boolean shouldUseStreaming(File sourcePackage) {
        return sourcePackage != null && sourcePackage.length() >= STREAM_PACKAGE_THRESHOLD_BYTES;
    }

    public static Result create(Context context, File sourceApk, File workDir) {
        return create(context, sourceApk, workDir, false, null, null, null);
    }

    public static Result create(Context context, File sourceApk, File workDir, boolean useApktool, String apktoolPath) {
        return create(context, sourceApk, workDir, useApktool, apktoolPath, null, null);
    }

    public static Result create(Context context, File sourceApk, File workDir, boolean useApktool, String apktoolPath, String zipalignPath) {
        return create(context, sourceApk, workDir, useApktool, apktoolPath, zipalignPath, null);
    }

    public static Result create(Context context, File sourceApk, File workDir, boolean useApktool, String apktoolPath, String zipalignPath, ToolRunner toolRunner) {
        if (context == null) return new Result(false, "Context is missing.", null);
        if (sourceApk == null || !sourceApk.isFile()) return new Result(false, "Source package is missing.", null);
        if (workDir == null) return new Result(false, "Work directory is missing.", null);
        if (!workDir.exists() && !workDir.mkdirs()) return new Result(false, "Unable to create APK work directory.", null);
        if (useApktool && isEmpty(apktoolPath)) return new Result(false, "apktool binary is missing.", null);
        if (isEmpty(zipalignPath)) return new Result(false, "zipalign binary is missing.", null);

        boolean streamPackage = shouldUseStreaming(sourceApk);
        ToolLogSet toolLogs = new ToolLogSet();

        try (InputStream ks = context.getAssets().open(ApkDebugToolHelper.DEBUG_KEYSTORE_ASSET)) {
            byte[] keyStoreBytes = readAll(ks);
            if (isSingleApk(sourceApk)) {
                File unsignedApk = new File(workDir, "debuggable-unsigned.apk");
                File alignedApk = new File(workDir, "debuggable-aligned.apk");
                File signedApk = new File(workDir, "debuggable-signed.apk");
                rebuildApk(sourceApk, unsignedApk, true, useApktool, apktoolPath, workDir, "single", toolRunner, toolLogs);
                alignApk(unsignedApk, alignedApk, zipalignPath, workDir, toolRunner);
                signApk(alignedApk, signedApk, keyStoreBytes);
                // Verify the patched binary manifest first. PackageManager archive parsing can emit
                // noisy framework warnings for some valid APKs that contain vendor/custom tags.
                if (!archiveBaseManifestDebuggable(signedApk, null, workDir) && !isPackageArchiveDebuggable(context, signedApk)) {
                    return new Result(false, "Rebuilt APK did not report android:debuggable=true after signing.", null);
                }
                try { unsignedApk.delete(); } catch (Throwable ignored) {}
                try { alignedApk.delete(); } catch (Throwable ignored) {}
                String msg = streamPackage
                        ? "Manifest patched, zipaligned, and APK signed with official v1/v2/v3 debug signatures using streaming mode."
                        : "Manifest patched, zipaligned, and APK signed with official v1/v2/v3 debug signatures.";
                return new Result(true, msg, signedApk, false, toolLogs.summary());
            }

            ArchiveInfo archiveInfo = readArchiveInfo(sourceApk);
            if (archiveInfo.apkEntries.isEmpty()) {
                return new Result(false, "No APK entries were found in the selected package.", null);
            }
            File archiveOut = new File(workDir, "debuggable-splits.zip");
            rebuildSplitArchive(sourceApk, archiveInfo, archiveOut, keyStoreBytes, workDir, useApktool, apktoolPath, zipalignPath, toolRunner, toolLogs);
            // Verify the base split manifest directly first. This avoids PackageManager parser
            // warnings for valid bundles that include custom application metadata.
            if (!archiveBaseManifestDebuggable(archiveOut, archiveInfo.baseEntryName, workDir)
                    && !isSplitArchiveBaseDebuggable(context, archiveOut, archiveInfo.baseEntryName, workDir)) {
                return new Result(false, "Rebuilt split base did not report android:debuggable=true after signing.", null);
            }
            return new Result(true,
                    streamPackage
                            ? "Base manifest patched; " + archiveInfo.apkEntries.size() + " split APK(s) zipaligned and signed with official v1/v2/v3 debug signatures using streaming mode."
                            : "Base manifest patched; " + archiveInfo.apkEntries.size() + " split APK(s) zipaligned and signed with official v1/v2/v3 debug signatures.",
                    archiveOut,
                    true,
                    toolLogs.summary());
        } catch (Throwable t) {
            String message = t.getMessage();
            if (isEmpty(message)) message = t.getClass().getSimpleName();
            String details = toolLogs == null ? "" : toolLogs.summary();
            return new Result(false, t.getClass().getSimpleName() + ": " + message, null, false, details);
        }
    }

    private static boolean isPackageArchiveDebuggable(Context context, File apkFile) {
        try {
            if (context == null || apkFile == null || !apkFile.isFile()) return false;
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info == null || info.applicationInfo == null) return false;
            info.applicationInfo.sourceDir = apkFile.getAbsolutePath();
            info.applicationInfo.publicSourceDir = apkFile.getAbsolutePath();
            return (info.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSplitArchiveBaseDebuggable(Context context, File archiveFile, String baseEntryName, File workDir) {
        File extracted = null;
        try (ZipFile zip = new ZipFile(archiveFile)) {
            ZipEntry base = isEmpty(baseEntryName) ? null : zip.getEntry(baseEntryName);
            if (base == null || base.isDirectory()) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry == null || entry.isDirectory()) continue;
                    String name = entry.getName();
                    if (name == null || !name.toLowerCase(Locale.US).endsWith(".apk")) continue;
                    if (scoreApkEntryName(name) >= 1000) {
                        base = entry;
                        break;
                    }
                }
            }
            if (base == null || base.isDirectory()) return false;
            File parent = workDir == null ? archiveFile.getParentFile() : workDir;
            extracted = new File(parent, "debuggable-base-verify.apk");
            try (InputStream in = zip.getInputStream(base);
                 FileOutputStream out = new FileOutputStream(extracted, false)) {
                copyStream(in, out);
            }
            return isPackageArchiveDebuggable(context, extracted);
        } catch (Throwable ignored) {
            return false;
        } finally {
            try { if (extracted != null) extracted.delete(); } catch (Throwable ignored) {}
        }
    }

    private static boolean archiveBaseManifestDebuggable(File archiveFile, String baseEntryName, File workDir) {
        File extracted = null;
        try {
            if (archiveFile == null || !archiveFile.isFile()) return false;
            if (isSingleApk(archiveFile)) {
                try (ZipFile zip = new ZipFile(archiveFile)) {
                    ZipEntry manifest = zip.getEntry("AndroidManifest.xml");
                    if (manifest == null || manifest.isDirectory()) return false;
                    try (InputStream in = zip.getInputStream(manifest)) {
                        return BinaryXmlDebuggablePatcher.isDebuggableEnabled(readSmallEntry(in, MAX_BINARY_MANIFEST_BYTES, "AndroidManifest.xml"));
                    }
                }
            }
            try (ZipFile zip = new ZipFile(archiveFile)) {
                ZipEntry base = isEmpty(baseEntryName) ? null : zip.getEntry(baseEntryName);
                if (base == null || base.isDirectory()) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry == null || entry.isDirectory()) continue;
                        String name = entry.getName();
                        if (name == null || !name.toLowerCase(Locale.US).endsWith(".apk")) continue;
                        if (scoreApkEntryName(name) >= 1000) {
                            base = entry;
                            break;
                        }
                    }
                }
                if (base == null || base.isDirectory()) return false;
                File parent = workDir == null ? archiveFile.getParentFile() : workDir;
                extracted = new File(parent, "debuggable-base-manifest-verify.apk");
                try (InputStream in = zip.getInputStream(base);
                     FileOutputStream out = new FileOutputStream(extracted, false)) {
                    copyStream(in, out);
                }
                return archiveBaseManifestDebuggable(extracted, null, workDir);
            }
        } catch (Throwable ignored) {
            return false;
        } finally {
            try { if (extracted != null) extracted.delete(); } catch (Throwable ignored) {}
        }
    }

    private static boolean isSingleApk(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry manifest = zip.getEntry("AndroidManifest.xml");
            return manifest != null && !manifest.isDirectory();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ArchiveInfo readArchiveInfo(File source) throws IOException {
        ArchiveInfo info = new ArchiveInfo();
        try (ZipFile zip = new ZipFile(source)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze == null || ze.isDirectory()) continue;
                String name = ze.getName();
                if (name == null) continue;
                if (name.toLowerCase(Locale.US).endsWith(".apk")) {
                    info.apkEntries.add(name);
                }
            }
        }
        Collections.sort(info.apkEntries, (a, b) -> {
            int sa = scoreApkEntryName(a);
            int sb = scoreApkEntryName(b);
            if (sa != sb) return sb - sa;
            return a.compareToIgnoreCase(b);
        });
        info.baseEntryName = info.apkEntries.isEmpty() ? null : info.apkEntries.get(0);
        return info;
    }

    private static void rebuildSplitArchive(File sourceArchive, ArchiveInfo info, File outputArchive,
                                            byte[] keyStoreBytes, File workDir,
                                            boolean useApktool, String apktoolPath, String zipalignPath,
                                            ToolRunner toolRunner, ToolLogSet toolLogs) throws IOException {
        File parent = outputArchive.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        List<String> signedEntryNames = new ArrayList<>();
        try (ZipFile inZip = new ZipFile(sourceArchive);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outputArchive, false))) {
            Enumeration<? extends ZipEntry> entries = inZip.entries();
            int splitIndex = 0;
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze == null) continue;
                String name = ze.getName();
                if (name == null) continue;
                if (ze.isDirectory()) {
                    writeDirectoryEntry(out, ze);
                    continue;
                }

                boolean isApk = name.toLowerCase(Locale.US).endsWith(".apk");
                if (!isApk) {
                    try (InputStream in = inZip.getInputStream(ze)) {
                        writeEntryStream(out, ze, in);
                    }
                    continue;
                }

                int thisSplit = splitIndex++;
                File nestedIn = new File(workDir, "split-in-" + thisSplit + ".apk");
                File unsigned = new File(workDir, "split-unsigned-" + thisSplit + ".apk");
                File aligned = new File(workDir, "split-aligned-" + thisSplit + ".apk");
                File signed = new File(workDir, "split-signed-" + thisSplit + ".apk");
                try {
                    try (InputStream in = inZip.getInputStream(ze);
                         FileOutputStream fos = new FileOutputStream(nestedIn, false)) {
                        copyStream(in, fos);
                    }
                    boolean patchManifest = name.equals(info.baseEntryName);
                    rebuildApk(nestedIn, unsigned, patchManifest, useApktool && patchManifest,
                            apktoolPath, workDir, "split-" + thisSplit, toolRunner, toolLogs);
                    alignApk(unsigned, aligned, zipalignPath, workDir, toolRunner);
                    signApk(aligned, signed, keyStoreBytes);
                    try (InputStream in = new FileInputStream(signed)) {
                        writeFileEntry(out, name, ze, in);
                    }
                    signedEntryNames.add(name);
                } finally {
                    try { nestedIn.delete(); } catch (Throwable ignored) {}
                    try { unsigned.delete(); } catch (Throwable ignored) {}
                    try { aligned.delete(); } catch (Throwable ignored) {}
                    try { signed.delete(); } catch (Throwable ignored) {}
                }
            }
        }
        if (signedEntryNames.isEmpty()) {
            throw new IOException("No split APKs were signed.");
        }
        if (info.baseEntryName == null || !signedEntryNames.contains(info.baseEntryName)) {
            throw new IOException("Base split was not rebuilt.");
        }
    }

    private static void rebuildApk(File sourceApk, File unsignedApk, boolean patchManifest,
                                   boolean useApktool, String apktoolPath, File workDir, String workName,
                                   ToolRunner toolRunner, ToolLogSet toolLogs) throws IOException {
        if (useApktool) {
            rebuildApkWithApktool(sourceApk, unsignedApk, apktoolPath, workDir, workName, toolRunner, toolLogs);
            return;
        }

        File parent = unsignedApk.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (ZipFile inZip = new ZipFile(sourceApk);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(unsignedApk, false))) {
            boolean patchedManifest = false;
            Enumeration<? extends ZipEntry> entries = inZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze == null || ze.isDirectory()) continue;
                String name = ze.getName();
                if (isSignatureEntry(name)) continue;

                if (patchManifest && "AndroidManifest.xml".equals(name)) {
                    byte[] data;
                    try (InputStream in = inZip.getInputStream(ze)) {
                        data = readSmallEntry(in, MAX_BINARY_MANIFEST_BYTES, "AndroidManifest.xml");
                    }
                    data = BinaryXmlDebuggablePatcher.patchDebuggable(data);
                    writeEntry(out, ze, data);
                    patchedManifest = true;
                } else {
                    try (InputStream in = inZip.getInputStream(ze)) {
                        writeEntryStream(out, ze, in);
                    }
                }
            }
            if (patchManifest && !patchedManifest) throw new IOException("AndroidManifest.xml was not found in the base APK.");
        }
    }

    private static void rebuildApkWithApktool(File sourceApk, File unsignedApk, String apktoolPath,
                                              File workDir, String workName, ToolRunner toolRunner, ToolLogSet toolLogs) throws IOException {
        if (isEmpty(apktoolPath)) throw new IOException("apktool binary is missing.");
        File decodedDir = new File(workDir, "apktool-" + sanitizeWorkName(workName));
        deleteTree(decodedDir);
        File parent = unsignedApk.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        File logDir = prepareApktoolLogDir(workDir);
        String safeName = sanitizeWorkName(workName);
        String logStamp = String.valueOf(System.currentTimeMillis());
        File decodeLog = new File(logDir, "apktool-" + logStamp + "-" + safeName + "-decode.log");
        File buildLog = new File(logDir, "apktool-" + logStamp + "-" + safeName + "-build.log");
        if (toolLogs != null) {
            toolLogs.add(decodeLog);
            toolLogs.add(buildLog);
        }
        runTool(new String[]{apktoolPath, "d", "-v", "--log", decodeLog.getAbsolutePath(), "-f", "-o", decodedDir.getAbsolutePath(), sourceApk.getAbsolutePath()}, workDir, "apktool decode failed", toolRunner);
        File manifest = new File(decodedDir, "AndroidManifest.xml");
        if (!manifest.isFile()) throw new IOException("apktool did not decode AndroidManifest.xml.");
        runTool(new String[]{apktoolPath, "b", "-v", "--debuggable", "--log", buildLog.getAbsolutePath(), decodedDir.getAbsolutePath(), "-o", unsignedApk.getAbsolutePath()}, workDir, "apktool build failed", toolRunner);
        if (!unsignedApk.isFile()) throw new IOException("apktool did not create a rebuilt APK.");
    }


    private static File prepareApktoolLogDir(File fallbackDir) {
        File publicDir = new File(ApkDebugToolHelper.APKTOOL_LOG_DIR);
        try {
            if ((publicDir.exists() || publicDir.mkdirs()) && publicDir.isDirectory()) {
                return publicDir;
            }
        } catch (Throwable ignored) {
        }
        File fallback = fallbackDir == null ? new File(".") : fallbackDir;
        File logs = new File(fallback, "apktool-logs");
        try {
            if (!logs.exists()) logs.mkdirs();
        } catch (Throwable ignored) {
        }
        return logs;
    }

    private static void runTool(String[] argv, File dir, String failurePrefix, ToolRunner toolRunner) throws IOException {
        if (argv == null || argv.length == 0 || isEmpty(argv[0])) {
            throw new IOException(failurePrefix + ": empty command");
        }

        if (toolRunner != null) {
            String command = buildShellCommand(argv, dir);
            ToolResult result = toolRunner.run(command);
            if (result == null) {
                throw new IOException(failurePrefix + ": command runner returned no result");
            }
            if (result.exitCode != 0) {
                throw new IOException(failurePrefix + ": " + buildToolFailureMessage(argv, result.exitCode, result));
            }
            return;
        }

        Process p;
        try {
            ProcessBuilder pb = new ProcessBuilder(argv);
            if (dir != null) pb.directory(dir);
            pb.redirectErrorStream(false);
            p = pb.start();
        } catch (Throwable t) {
            throw new IOException(failurePrefix + ": " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }

        StreamCapture stdout = new StreamCapture(p.getInputStream());
        StreamCapture stderr = new StreamCapture(p.getErrorStream());
        stdout.start();
        stderr.start();
        int code;
        try {
            code = p.waitFor();
            stdout.join(1000L);
            stderr.join(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(failurePrefix + ": interrupted", e);
        }
        if (code != 0) {
            throw new IOException(failurePrefix + ": " + buildToolFailureMessage(argv, code, new ToolResult(code, stdout.text(), stderr.text())));
        }
    }


    private static String buildToolFailureMessage(String[] argv, int exitCode, ToolResult result) {
        String msg = toolFailureText(result);
        if (isEmpty(msg)) msg = "exit " + exitCode;
        String logPath = logPathFromArgs(argv);
        if (!isEmpty(logPath)) {
            String tail = readLogTail(new File(logPath));
            msg = msg + "\nAPK tool log: " + logPath;
            if (!isEmpty(tail)) {
                msg = msg + "\n--- apktool log tail ---\n" + tail;
            }
        }
        return trimLong(msg);
    }

    private static String logPathFromArgs(String[] argv) {
        if (argv == null) return "";
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if ("--log".equals(arg) && i + 1 < argv.length) return argv[i + 1];
            if (arg != null && arg.startsWith("--log=")) return arg.substring("--log=".length());
        }
        return "";
    }

    private static String readLogTail(File file) {
        if (file == null || !file.isFile() || file.length() <= 0L) return "";
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = readAll(in);
            String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            int max = 3000;
            if (text.length() > max) {
                return text.substring(text.length() - max);
            }
            return text;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String toolFailureText(ToolResult result) {
        if (result == null) return "";
        StringBuilder sb = new StringBuilder();
        if (!isEmpty(result.stderr)) {
            sb.append(result.stderr.trim());
        }
        if (!isEmpty(result.stdout)) {
            if (sb.length() > 0) sb.append("\n--- stdout ---\n");
            sb.append(result.stdout.trim());
        }
        return sb.toString();
    }

    private static String buildShellCommand(String[] argv, File dir) {
        StringBuilder sb = new StringBuilder();
        if (dir != null) {
            sb.append("cd ").append(ApkDebugToolHelper.shQuote(dir.getAbsolutePath())).append(" && ");
        }
        for (int i = 0; i < argv.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(ApkDebugToolHelper.shQuote(argv[i]));
        }
        return sb.toString();
    }

    private static void alignApk(File unsignedApk, File alignedApk, String zipalignPath, File workDir, ToolRunner toolRunner) throws IOException {
        if (unsignedApk == null || !unsignedApk.isFile()) throw new IOException("Unsigned APK not found for zipalign.");
        if (alignedApk == null) throw new IOException("Aligned APK output path is empty.");
        if (isEmpty(zipalignPath)) throw new IOException("zipalign binary is missing.");
        File parent = alignedApk.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create zipalign output directory.");
        }
        runTool(new String[]{zipalignPath, "-f", "-p", "4", unsignedApk.getAbsolutePath(), alignedApk.getAbsolutePath()},
                workDir, "zipalign failed", toolRunner);
        makeToolOutputReadable(alignedApk, toolRunner);
        if (!alignedApk.isFile() || alignedApk.length() <= 0L) {
            throw new IOException("zipalign did not create a valid APK.");
        }
    }

    private static void makeToolOutputReadable(File file, ToolRunner toolRunner) throws IOException {
        if (file == null) return;
        try { file.setReadable(true, false); } catch (Throwable ignored) {}
        if (toolRunner == null) return;
        ToolResult result = toolRunner.run("chmod 644 " + ApkDebugToolHelper.shQuote(file.getAbsolutePath()));
        if (result != null && result.exitCode != 0) {
            String msg = result.stderr;
            if (isEmpty(msg)) msg = result.stdout;
            if (isEmpty(msg)) msg = "exit " + result.exitCode;
            throw new IOException("Unable to make tool output readable: " + trimLong(msg));
        }
    }

    private static void signApk(File unsignedApk, File signedApk, byte[] keyStoreBytes) throws IOException {
        try {
            OfficialApkSigner.sign(unsignedApk, signedApk, keyStoreBytes,
                    ApkDebugToolHelper.DEBUG_KEY_ALIAS,
                    ApkDebugToolHelper.DEBUG_KEY_PASSWORD);
            try { signedApk.setReadable(true, false); } catch (Throwable ignored) {}
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage();
            if (isEmpty(message)) message = e.getClass().getSimpleName();
            throw new IOException("Official APK signing failed: " + message, e);
        }
    }

    private static void writeDirectoryEntry(ZipOutputStream out, ZipEntry source) throws IOException {
        ZipEntry dst = new ZipEntry(source.getName());
        dst.setTime(source.getTime());
        dst.setComment(source.getComment());
        out.putNextEntry(dst);
        out.closeEntry();
    }

    private static void writeEntry(ZipOutputStream out, ZipEntry source, byte[] data) throws IOException {
        ZipEntry dst = new ZipEntry(source.getName());
        dst.setTime(source.getTime());
        dst.setComment(source.getComment());
        byte[] extra = source.getExtra();
        if (extra != null) dst.setExtra(extra);
        if (source.getMethod() == ZipEntry.STORED && data != null) {
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(data);
            dst.setMethod(ZipEntry.STORED);
            dst.setSize(data.length);
            dst.setCompressedSize(data.length);
            dst.setCrc(crc.getValue());
        }
        out.putNextEntry(dst);
        if (data != null && data.length > 0) out.write(data);
        out.closeEntry();
    }

    private static void writeEntryStream(ZipOutputStream out, ZipEntry source, InputStream in) throws IOException {
        ZipEntry dst = new ZipEntry(source.getName());
        dst.setTime(source.getTime());
        dst.setComment(source.getComment());
        byte[] extra = source.getExtra();
        if (extra != null) dst.setExtra(extra);
        if (source.getMethod() == ZipEntry.STORED) {
            long size = source.getSize();
            long compressedSize = source.getCompressedSize();
            long crc = source.getCrc();
            if (size >= 0L && compressedSize >= 0L && crc >= 0L) {
                dst.setMethod(ZipEntry.STORED);
                dst.setSize(size);
                dst.setCompressedSize(compressedSize);
                dst.setCrc(crc);
            }
        }
        out.putNextEntry(dst);
        copyStream(in, out);
        out.closeEntry();
    }

    private static void writeFileEntry(ZipOutputStream out, String name, ZipEntry source, InputStream in) throws IOException {
        ZipEntry dst = new ZipEntry(name);
        dst.setTime(source.getTime());
        dst.setComment(source.getComment());
        out.putNextEntry(dst);
        copyStream(in, out);
        out.closeEntry();
    }

    private static byte[] readSmallEntry(InputStream in, int maxBytes, String label) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int r;
        int total = 0;
        while ((r = in.read(buf)) > 0) {
            total += r;
            if (total > maxBytes) {
                throw new IOException(label + " is unexpectedly large; refusing to load it into memory.");
            }
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copyStream(in, out);
        return out.toByteArray();
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
        }
    }

    private static boolean isSignatureEntry(String name) {
        if (name == null) return false;
        String upper = name.toUpperCase(Locale.US);
        if (!upper.startsWith("META-INF/")) return false;
        String base = upper.substring("META-INF/".length());
        return "MANIFEST.MF".equals(base)
                || base.endsWith(".SF")
                || base.endsWith(".RSA")
                || base.endsWith(".DSA")
                || base.endsWith(".EC");
    }

    private static int scoreApkEntryName(String name) {
        if (name == null) return 0;
        String n = name.toLowerCase(Locale.US);
        String base = n;
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        int score = 0;
        boolean configSplit = base.startsWith("config.")
                || base.startsWith("split_config.")
                || base.contains("dpi")
                || base.contains("density")
                || base.contains("lang")
                || base.contains("locale");
        if ("base.apk".equals(base)) score += 10000;
        if (base.startsWith("base-") && base.endsWith(".apk")) score += 9000;
        if (base.contains("master") && base.endsWith(".apk")) score += 8000;
        if (configSplit) score -= 5000;
        else score += 1000;
        score -= Math.min(500, base.length());
        return score;
    }

    private static String sanitizeWorkName(String value) {
        if (isEmpty(value)) return "apk";
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String trimLong(String s) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= 1200 ? s : s.substring(0, 1200) + "...";
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteTree(k);
            }
        }
        try { f.delete(); } catch (Throwable ignored) {}
    }

    private static final class ArchiveInfo {
        final List<String> apkEntries = new ArrayList<>();
        String baseEntryName;
    }

    private static final class StreamCapture extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        StreamCapture(InputStream in) {
            this.in = in;
        }

        @Override public void run() {
            if (in == null) return;
            try {
                byte[] buf = new byte[4096];
                int r;
                while ((r = in.read(buf)) > 0) {
                    if (out.size() < 8192) {
                        out.write(buf, 0, Math.min(r, 8192 - out.size()));
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                try { in.close(); } catch (Throwable ignored) {}
            }
        }

        String text() {
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
