package dev.perms.test.debugging.smali;

import org.jf.baksmali.Baksmali;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Small wrapper around smali/baksmali so MainActivity only handles UI state.
 */
public final class PermsTestSmaliTools {
    public static final String DEFAULT_ROOT = "/sdcard/dev.perms.test/debugging";
    public static final String DEFAULT_SMALI_ROOT = DEFAULT_ROOT + "/smali";
    public static final String DEFAULT_JAVA_ROOT = DEFAULT_ROOT + "/java";
    public static final String DEFAULT_BUILD_ROOT = DEFAULT_ROOT + "/build";
    public static final String DEFAULT_SMALI_OUT = DEFAULT_SMALI_ROOT + "/package/smali";
    public static final String DEFAULT_DEX_OUT = DEFAULT_BUILD_ROOT + "/package/classes.dex";

    private static final Object TOOL_STDIO_LOCK = new Object();

    private PermsTestSmaliTools() {
    }

    public static ToolResult disassemble(String inputPath, String dexEntry, String outputPath,
                                  int apiLevel, boolean cleanOutput) throws Exception {
        long start = System.currentTimeMillis();
        File input = requireExistingFile(inputPath, "DEX/APK input");
        File outputDir = requireDirectoryTarget(outputPath, "Smali output folder");
        DexFile dexFile = loadDexFile(input, dexEntry, apiLevel);
        if (cleanOutput && outputDir.exists()) {
            clearSafeDebuggingOutput(outputDir);
        }
        ensureDirectory(outputDir);

        Captured<Boolean> captured = captureToolOutput(() -> {
            BaksmaliOptions options = new BaksmaliOptions();
            options.apiLevel = apiLevel;
            int jobs = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
            return Baksmali.disassembleDexFile(dexFile, outputDir, jobs, options);
        });

        int smaliCount = countSmaliFiles(outputDir);
        boolean ok = Boolean.TRUE.equals(captured.value);
        String summary = (ok ? "Disassembled" : "Disassemble failed")
                + "  classes=" + smaliCount
                + "  output=" + outputDir.getAbsolutePath()
                + durationSuffix(start);
        return new ToolResult(ok, summary, captured.output, outputDir.getAbsolutePath(), smaliCount);
    }

    public static ToolResult assemble(String smaliInputPath, String outputDexPath,
                               int apiLevel, boolean verboseErrors) throws Exception {
        long start = System.currentTimeMillis();
        File smaliInput = requireExistingPath(smaliInputPath, "Smali input");
        File outputDex = requireFileTarget(outputDexPath, "Assembled DEX output");
        ensureDirectory(outputDex.getParentFile());

        Captured<Boolean> captured = captureToolOutput(() -> {
            SmaliOptions options = new SmaliOptions();
            options.apiLevel = apiLevel;
            options.verboseErrors = verboseErrors;
            options.jobs = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
            options.outputDexFile = outputDex.getAbsolutePath();
            return Smali.assemble(options, smaliInput.getAbsolutePath());
        });

        boolean ok = Boolean.TRUE.equals(captured.value) && outputDex.exists() && outputDex.length() > 0;
        String summary = (ok ? "Assembled" : "Assemble failed")
                + "  output=" + outputDex.getAbsolutePath()
                + (outputDex.exists() ? "  size=" + outputDex.length() + " bytes" : "")
                + durationSuffix(start);
        return new ToolResult(ok, summary, captured.output, outputDex.getAbsolutePath(), countSmaliFiles(smaliInput));
    }

    public static ToolResult listClasses(String inputPath, String dexEntry, int apiLevel) throws Exception {
        long start = System.currentTimeMillis();
        File input = requireExistingFile(inputPath, "DEX/APK input");
        DexFile dexFile = loadDexFile(input, dexEntry, apiLevel);
        List<String> classes = new ArrayList<>();
        for (ClassDef classDef : dexFile.getClasses()) {
            if (classDef != null && classDef.getType() != null) classes.add(classDef.getType());
        }
        Collections.sort(classes);
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(400, classes.size());
        for (int i = 0; i < shown; i++) {
            sb.append(classes.get(i)).append('\n');
        }
        if (classes.size() > shown) {
            sb.append("... ").append(classes.size() - shown).append(" more classes not shown\n");
        }
        String summary = "Classes=" + classes.size() + durationSuffix(start);
        return new ToolResult(true, summary, sb.toString(), input.getAbsolutePath(), classes.size());
    }

    public static ToolResult rebuildApkWithDex(String sourceApkPath, String dexEntry, String replacementDexPath, String outputApkPath) throws Exception {
        LinkedHashMap<String, String> replacements = new LinkedHashMap<>();
        replacements.put(normalizeDexEntryName(dexEntry), requireExistingFile(replacementDexPath, "Replacement DEX").getAbsolutePath());
        return rebuildApkWithDexReplacements(sourceApkPath, replacements, outputApkPath);
    }

    public static ToolResult rebuildApkWithDexReplacements(String sourceApkPath, Map<String, String> replacementDexByEntry, String outputApkPath) throws Exception {
        long start = System.currentTimeMillis();
        File sourceApk = requireExistingFile(sourceApkPath, "Source APK");
        File outputApk = requireFileTarget(outputApkPath, "Rebuilt APK output");
        ensureDirectory(outputApk.getParentFile());
        if (replacementDexByEntry == null || replacementDexByEntry.isEmpty()) {
            throw new IllegalArgumentException("No replacement DEX files were provided.");
        }

        LinkedHashMap<String, File> replacements = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : replacementDexByEntry.entrySet()) {
            String entryName = normalizeDexEntryName(entry == null ? null : entry.getKey());
            File replacementDex = requireExistingFile(entry == null ? null : entry.getValue(), "Replacement DEX");
            replacements.put(entryName, replacementDex);
        }

        int copiedEntries = 0;
        LinkedHashSet<String> replacedEntries = new LinkedHashSet<>();
        byte[] buffer = new byte[128 * 1024];
        LinkedHashSet<String> writtenEntries = new LinkedHashSet<>();

        try (ZipFile zip = new ZipFile(sourceApk);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputApk, false)))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry inEntry = entries.nextElement();
                if (inEntry == null) continue;
                String name = inEntry.getName();
                if (name == null || name.isEmpty()) continue;
                if (isSignatureEntry(name)) continue;
                if (writtenEntries.contains(name)) continue;

                ZipEntry outEntry = new ZipEntry(name);
                outEntry.setTime(inEntry.getTime());
                zos.putNextEntry(outEntry);
                if (!inEntry.isDirectory()) {
                    File replacementDex = replacements.get(name);
                    if (replacementDex != null) {
                        try (InputStream in = new BufferedInputStream(new FileInputStream(replacementDex))) {
                            copyStream(in, zos, buffer);
                        }
                        replacedEntries.add(name);
                    } else {
                        try (InputStream in = new BufferedInputStream(zip.getInputStream(inEntry))) {
                            copyStream(in, zos, buffer);
                        }
                    }
                }
                zos.closeEntry();
                writtenEntries.add(name);
                copiedEntries++;
            }

            for (Map.Entry<String, File> replacement : replacements.entrySet()) {
                String entryName = replacement.getKey();
                if (replacedEntries.contains(entryName)) continue;
                ZipEntry dexOut = new ZipEntry(entryName);
                dexOut.setTime(System.currentTimeMillis());
                zos.putNextEntry(dexOut);
                try (InputStream in = new BufferedInputStream(new FileInputStream(replacement.getValue()))) {
                    copyStream(in, zos, buffer);
                }
                zos.closeEntry();
                replacedEntries.add(entryName);
                copiedEntries++;
            }
        }

        if (!outputApk.isFile() || outputApk.length() <= 0) {
            throw new IOException("Rebuilt APK was not created: " + outputApk.getAbsolutePath());
        }

        StringBuilder details = new StringBuilder();
        details.append("entries=").append(copiedEntries).append('\n');
        for (String entryName : replacedEntries) {
            details.append("dexEntry=").append(entryName).append('\n');
        }
        String summary = "Rebuilt APK  output=" + outputApk.getAbsolutePath()
                + "  size=" + outputApk.length() + " bytes"
                + "  dexReplaced=" + replacedEntries.size()
                + durationSuffix(start);
        return new ToolResult(true, summary, details.toString(), outputApk.getAbsolutePath(), replacedEntries.size());
    }

    private static void copyStream(InputStream in, OutputStream out, byte[] buffer) throws IOException {
        int r;
        while ((r = in.read(buffer)) > 0) out.write(buffer, 0, r);
    }

    private static boolean isSignatureEntry(String name) {
        if (name == null) return false;
        String upper = name.toUpperCase(Locale.US);
        if (!upper.startsWith("META-INF/")) return false;
        return upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")
                || upper.endsWith(".SF") || upper.endsWith(".MF");
    }

    public static List<String> listDexEntries(String inputPath) throws Exception {
        return listDexEntriesDetailed(inputPath).entries;
    }

    public static DexEntryScanResult listDexEntriesDetailed(String inputPath) throws Exception {
        File input = requireExistingFile(inputPath, "DEX/APK input");
        ArrayList<String> entries = new ArrayList<>();
        ArrayList<String> skipped = new ArrayList<>();
        if (isDexContainer(input)) {
            try (ZipFile zip = new ZipFile(input)) {
                Enumeration<? extends ZipEntry> zipEntries = zip.entries();
                while (zipEntries.hasMoreElements()) {
                    ZipEntry zipEntry = zipEntries.nextElement();
                    if (zipEntry == null || zipEntry.isDirectory()) continue;
                    String rawName = zipEntry.getName();
                    if (rawName == null || rawName.isEmpty()) continue;
                    String lowerName = rawName.toLowerCase(Locale.US);
                    if (!lowerName.endsWith(".dex")) continue;

                    // APK class DEX files are top-level entries. Skipping nested .dex-named assets
                    // avoids loading sanitized basenames that do not actually exist in the container.
                    if (rawName.indexOf('/') >= 0 || rawName.indexOf('\\') >= 0) {
                        skipped.add(rawName);
                        continue;
                    }
                    if (!isZipEntryDexMagic(zip, zipEntry)) {
                        skipped.add(rawName);
                        continue;
                    }

                    String name = normalizeDexEntryName(rawName);
                    if (!entries.contains(name)) entries.add(name);
                }
            }
        } else if (hasDexMagic(input)) {
            entries.add(normalizeDexEntryName(input.getName()));
        } else {
            skipped.add(input.getName());
        }
        Collections.sort(entries, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int ai = dexEntrySortIndex(a);
                int bi = dexEntrySortIndex(b);
                if (ai != bi) return Integer.compare(ai, bi);
                return String.CASE_INSENSITIVE_ORDER.compare(a == null ? "" : a, b == null ? "" : b);
            }
        });
        return new DexEntryScanResult(entries, skipped);
    }

    private static boolean isZipEntryDexMagic(ZipFile zip, ZipEntry entry) {
        if (zip == null || entry == null) return false;
        try (InputStream in = zip.getInputStream(entry)) {
            return streamHasDexMagic(in);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasDexMagic(File file) {
        if (file == null || !file.isFile()) return false;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return streamHasDexMagic(in);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean streamHasDexMagic(InputStream in) throws IOException {
        if (in == null) return false;
        byte[] magic = new byte[4];
        int off = 0;
        while (off < magic.length) {
            int r = in.read(magic, off, magic.length - off);
            if (r < 0) break;
            off += r;
        }
        return off == 4 && magic[0] == 'd' && magic[1] == 'e' && magic[2] == 'x' && magic[3] == '\n';
    }

    public static String normalizeDexEntryName(String dexEntry) {
        String clean = cleanPath(dexEntry);
        if (clean.isEmpty()) return "classes.dex";
        int slash = clean.lastIndexOf('/');
        if (slash >= 0 && slash < clean.length() - 1) clean = clean.substring(slash + 1);
        if (!clean.toLowerCase(Locale.US).endsWith(".dex")) clean = clean + ".dex";
        return clean;
    }

    public static String defaultSmaliFolderNameForDexEntry(String dexEntry) {
        String clean = normalizeDexEntryName(dexEntry);
        if ("classes.dex".equalsIgnoreCase(clean)) return "smali";
        String stem = clean.substring(0, clean.length() - 4);
        stem = stem.replaceAll("[^A-Za-z0-9_]+", "_");
        stem = stem.replaceAll("_+", "_");
        if (stem.startsWith("_")) stem = stem.substring(1);
        if (stem.endsWith("_")) stem = stem.substring(0, stem.length() - 1);
        if (stem.isEmpty()) stem = "dex";
        return "smali_" + stem;
    }

    public static String defaultDexOutputNameForDexEntry(String dexEntry) {
        return normalizeDexEntryName(dexEntry);
    }

    public static int countSmaliSources(String smaliInputPath) {
        return countSmaliFiles(new File(cleanPath(smaliInputPath)));
    }

    private static int dexEntrySortIndex(String dexEntry) {
        String clean = normalizeDexEntryName(dexEntry).toLowerCase(Locale.US);
        if ("classes.dex".equals(clean)) return 1;
        if (clean.startsWith("classes") && clean.endsWith(".dex")) {
            String middle = clean.substring("classes".length(), clean.length() - ".dex".length());
            if (middle.matches("[0-9]+")) {
                try { return Integer.parseInt(middle); } catch (Throwable ignored) { }
            }
        }
        return Integer.MAX_VALUE;
    }

    private static DexFile loadDexFile(File input, String dexEntry, int apiLevel) throws IOException {
        Opcodes opcodes = Opcodes.forApi(apiLevel);
        String entry = dexEntry == null ? "" : dexEntry.trim();
        if (isDexContainer(input) && !entry.isEmpty()) {
            MultiDexContainer.DexEntry<?> dex = DexFileFactory.loadDexEntry(input, entry, true, opcodes);
            return dex.getDexFile();
        }
        return DexFileFactory.loadDexFile(input, opcodes);
    }

    private static boolean isDexContainer(File input) {
        String name = input == null ? "" : input.getName().toLowerCase(Locale.US);
        return name.endsWith(".apk") || name.endsWith(".zip") || name.endsWith(".jar")
                || name.endsWith(".apks") || name.endsWith(".xapk") || name.endsWith(".apkm");
    }

    private static File requireExistingFile(String path, String label) {
        File file = requireExistingPath(path, label);
        if (!file.isFile()) {
            throw new IllegalArgumentException(label + " is not a file: " + file.getAbsolutePath());
        }
        return file;
    }

    private static File requireExistingPath(String path, String label) {
        String clean = cleanPath(path);
        if (clean.isEmpty()) throw new IllegalArgumentException(label + " is empty.");
        File file = new File(clean);
        if (!file.exists()) throw new IllegalArgumentException(label + " does not exist: " + clean);
        return file;
    }

    private static File requireDirectoryTarget(String path, String label) {
        String clean = cleanPath(path);
        if (clean.isEmpty()) throw new IllegalArgumentException(label + " is empty.");
        return new File(clean);
    }

    private static File requireFileTarget(String path, String label) {
        String clean = cleanPath(path);
        if (clean.isEmpty()) throw new IllegalArgumentException(label + " is empty.");
        File file = new File(clean);
        if (file.exists() && file.isDirectory()) {
            throw new IllegalArgumentException(label + " is a folder, not a file: " + clean);
        }
        return file;
    }

    private static String cleanPath(String path) {
        return path == null ? "" : path.trim();
    }

    private static void ensureDirectory(File dir) throws IOException {
        if (dir == null) return;
        if (dir.exists()) {
            if (!dir.isDirectory()) throw new IOException("Path is not a directory: " + dir.getAbsolutePath());
            return;
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Unable to create directory: " + dir.getAbsolutePath());
        }
    }

    private static void clearSafeDebuggingOutput(File dir) throws IOException {
        String canonical = dir.getCanonicalPath();
        String raw = dir.getAbsolutePath();
        if (!isUnderSafeDebuggingRoot(canonical) && !isUnderSafeDebuggingRoot(raw)) {
            throw new IOException("Clean output is only allowed under " + DEFAULT_ROOT + " for safety.");
        }
        if (canonical.equals("/") || canonical.length() < 8) {
            throw new IOException("Refusing to clean unsafe output path: " + canonical);
        }
        deleteChildren(dir);
    }

    private static boolean isUnderSafeDebuggingRoot(String path) throws IOException {
        if (path == null) return false;
        String clean = path.replace('\\', '/');
        String[] roots = new String[]{
                DEFAULT_ROOT,
                "/storage/emulated/0/dev.perms.test/debugging",
                "/storage/self/primary/dev.perms.test/debugging"
        };
        for (String root : roots) {
            String rawRoot = root.replace('\\', '/');
            String canonicalRoot = new File(root).getCanonicalPath().replace('\\', '/');
            if (clean.equals(rawRoot) || clean.startsWith(rawRoot + "/")
                    || clean.equals(canonicalRoot) || clean.startsWith(canonicalRoot + "/")) {
                return true;
            }
        }
        return false;
    }

    private static void deleteChildren(File dir) throws IOException {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            deleteRecursive(child);
        }
    }

    private static void deleteRecursive(File file) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) deleteChildren(file);
        if (!file.delete() && file.exists()) {
            throw new IOException("Unable to delete: " + file.getAbsolutePath());
        }
    }

    private static int countSmaliFiles(File root) {
        if (root == null || !root.exists()) return 0;
        if (root.isFile()) return root.getName().endsWith(".smali") ? 1 : 0;
        int count = 0;
        File[] children = root.listFiles();
        if (children == null) return 0;
        for (File child : children) {
            count += countSmaliFiles(child);
        }
        return count;
    }

    private static String durationSuffix(long startMs) {
        long ms = Math.max(0L, System.currentTimeMillis() - startMs);
        return "  time=" + ms + " ms";
    }

    private interface ThrowingTask<T> {
        T run() throws Exception;
    }

    private static <T> Captured<T> captureToolOutput(ThrowingTask<T> task) throws Exception {
        synchronized (TOOL_STDIO_LOCK) {
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PrintStream capture = new PrintStream(bytes, true, "UTF-8");
            try {
                System.setOut(capture);
                System.setErr(capture);
                T value = task.run();
                capture.flush();
                return new Captured<>(value, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            } finally {
                try { capture.flush(); } catch (Throwable ignored) {}
                System.setOut(oldOut);
                System.setErr(oldErr);
                try { capture.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static final class Captured<T> {
        final T value;
        final String output;

        Captured(T value, String output) {
            this.value = value;
            this.output = output == null ? "" : output;
        }
    }

    public static final class DexEntryScanResult {
        public final ArrayList<String> entries;
        public final ArrayList<String> skippedEntries;
        public final int skippedCount;

        DexEntryScanResult(ArrayList<String> entries, ArrayList<String> skippedEntries) {
            this.entries = entries == null ? new ArrayList<>() : entries;
            this.skippedEntries = skippedEntries == null ? new ArrayList<>() : skippedEntries;
            this.skippedCount = this.skippedEntries.size();
        }
    }

    public static final class ToolResult {
        public final boolean success;
        public final String summary;
        public final String details;
        public final String outputPath;
        public final int count;

        public ToolResult(boolean success, String summary, String details, String outputPath, int count) {
            this.success = success;
            this.summary = summary == null ? "" : summary;
            this.details = details == null ? "" : details;
            this.outputPath = outputPath == null ? "" : outputPath;
            this.count = count;
        }
    }
}
