package dev.perms.test.packages.editor;

import android.text.TextUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ApkEditorSplitArchive {
    public static final class Info {
        public final ArrayList<String> apkEntries = new ArrayList<>();
        public final ArrayList<String> metadataEntries = new ArrayList<>();
        public String baseEntryName = "";
        public long sizeBytes;

        public boolean isSplitContainer() {
            return apkEntries.size() > 1 && !TextUtils.isEmpty(baseEntryName);
        }

        public String toReport() {
            StringBuilder out = new StringBuilder();
            out.append("Split archive\n");
            out.append("Base APK: ").append(TextUtils.isEmpty(baseEntryName) ? "not found" : baseEntryName).append('\n');
            out.append("APK entries: ").append(apkEntries.size()).append('\n');
            int limit = Math.min(apkEntries.size(), 40);
            for (int i = 0; i < limit; i++) {
                String entry = apkEntries.get(i);
                out.append(entry.equals(baseEntryName) ? "  * " : "  - ").append(entry).append('\n');
            }
            if (apkEntries.size() > limit) out.append("  ... ").append(apkEntries.size() - limit).append(" more APK entries\n");
            if (!metadataEntries.isEmpty()) {
                out.append("Metadata entries: ").append(metadataEntries.size()).append('\n');
                int metaLimit = Math.min(metadataEntries.size(), 20);
                for (int i = 0; i < metaLimit; i++) out.append("  - ").append(metadataEntries.get(i)).append('\n');
                if (metadataEntries.size() > metaLimit) out.append("  ... ").append(metadataEntries.size() - metaLimit).append(" more metadata entries\n");
            }
            return out.toString();
        }
    }

    public static final class Workspace {
        public final Info info;
        public final File splitDir;
        public final File apkDir;
        public final File metadataDir;
        public final File baseApk;

        Workspace(Info info, File splitDir, File apkDir, File metadataDir, File baseApk) {
            this.info = info;
            this.splitDir = splitDir;
            this.apkDir = apkDir;
            this.metadataDir = metadataDir;
            this.baseApk = baseApk;
        }

        public String toReport() {
            StringBuilder out = new StringBuilder();
            out.append("[APK Editor] Split workspace: ").append(splitDir.getAbsolutePath()).append('\n');
            out.append("[APK Editor] Base APK for editing: ").append(baseApk == null ? "missing" : baseApk.getAbsolutePath()).append('\n');
            out.append("[APK Editor] Companion splits: ").append(Math.max(0, info.apkEntries.size() - 1)).append('\n');
            out.append("[APK Editor] Metadata folder: ").append(metadataDir.getAbsolutePath()).append('\n');
            return out.toString();
        }
    }

    private ApkEditorSplitArchive() {
    }

    public static boolean isSplitContainer(File source) {
        if (source == null || !source.isFile()) return false;
        String name = source.getName().toLowerCase(Locale.US);
        if (name.endsWith(".apk")) return false;
        try {
            Info info = inspect(source);
            return info.isSplitContainer();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Info inspect(File source) throws IOException {
        Info info = new Info();
        if (source == null || !source.isFile()) throw new IOException("Split archive is missing.");
        info.sizeBytes = source.length();
        try (ZipFile zip = new ZipFile(source)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null || entry.isDirectory()) continue;
                String name = entry.getName();
                if (TextUtils.isEmpty(name)) continue;
                String lower = name.toLowerCase(Locale.US);
                if (lower.endsWith(".apk")) {
                    info.apkEntries.add(name);
                } else if (isMetadataEntry(lower)) {
                    info.metadataEntries.add(name);
                }
            }
        }
        Collections.sort(info.apkEntries, (a, b) -> {
            int sa = scoreApkEntryName(a);
            int sb = scoreApkEntryName(b);
            if (sa != sb) return sb - sa;
            return a.compareToIgnoreCase(b);
        });
        Collections.sort(info.metadataEntries, String::compareToIgnoreCase);
        info.baseEntryName = info.apkEntries.isEmpty() ? "" : info.apkEntries.get(0);
        return info;
    }

    public static Workspace extractSplitWorkspace(File source, File workspace) throws IOException {
        Info info = inspect(source);
        if (!info.isSplitContainer()) throw new IOException("No split APK set was found in this archive.");
        File splitDir = new File(workspace, "splits");
        File apkDir = new File(splitDir, "apks");
        File metadataDir = new File(splitDir, "metadata");
        ApkEditorFileUtils.deleteTree(splitDir);
        if (!apkDir.exists() && !apkDir.mkdirs()) throw new IOException("Unable to create " + apkDir.getAbsolutePath());
        if (!metadataDir.exists() && !metadataDir.mkdirs()) throw new IOException("Unable to create " + metadataDir.getAbsolutePath());
        File baseApk = null;
        String root = splitDir.getCanonicalPath() + File.separator;
        try (ZipFile zip = new ZipFile(source)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null || entry.isDirectory()) continue;
                String name = entry.getName();
                if (TextUtils.isEmpty(name)) continue;
                String lower = name.toLowerCase(Locale.US);
                File out;
                if (lower.endsWith(".apk")) {
                    out = new File(apkDir, safeEntryFileName(name));
                } else if (isMetadataEntry(lower)) {
                    out = new File(metadataDir, name);
                    String outPath = out.getCanonicalPath();
                    if (!outPath.startsWith(root)) throw new IOException("Blocked unsafe split metadata entry: " + name);
                } else {
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create " + parent.getAbsolutePath());
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream dst = new BufferedOutputStream(new FileOutputStream(out, false))) {
                    ApkEditorFileUtils.copy(in, dst);
                }
                if (name.equals(info.baseEntryName)) baseApk = out;
            }
        }
        writeSplitMap(info, new File(splitDir, "split-map.txt"));
        if (baseApk == null || !baseApk.isFile()) throw new IOException("Base APK was not extracted from split archive.");
        return new Workspace(info, splitDir, apkDir, metadataDir, baseApk);
    }

    public static void createArchiveReplacingBase(File sourceArchive, String baseEntryName, File replacementBaseApk, File outputArchive) throws IOException {
        if (sourceArchive == null || !sourceArchive.isFile()) throw new IOException("Original split archive is missing.");
        if (TextUtils.isEmpty(baseEntryName)) throw new IOException("Base split entry is missing.");
        if (replacementBaseApk == null || !replacementBaseApk.isFile()) throw new IOException("Edited base APK is missing.");
        File parent = outputArchive == null ? null : outputArchive.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create " + parent.getAbsolutePath());
        boolean replaced = false;
        try (ZipFile zip = new ZipFile(sourceArchive);
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputArchive, false)))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null) continue;
                String name = entry.getName();
                if (TextUtils.isEmpty(name)) continue;
                if (entry.isDirectory()) {
                    writeDirectory(out, entry);
                    continue;
                }
                if (name.equals(baseEntryName)) {
                    try (InputStream in = new FileInputStream(replacementBaseApk)) {
                        writeFileEntry(out, name, entry, in);
                    }
                    replaced = true;
                } else {
                    try (InputStream in = zip.getInputStream(entry)) {
                        writeFileEntry(out, name, entry, in);
                    }
                }
            }
        }
        if (!replaced) throw new IOException("Base split entry was not found while rebuilding archive: " + baseEntryName);
    }

    private static void writeSplitMap(Info info, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create " + parent.getAbsolutePath());
        StringBuilder text = new StringBuilder();
        text.append("# PermsTest APK Editor split workspace\n");
        text.append("base=").append(info.baseEntryName).append('\n');
        text.append("apk_count=").append(info.apkEntries.size()).append('\n');
        for (String entry : info.apkEntries) text.append(entry.equals(info.baseEntryName) ? "base_apk=" : "split_apk=").append(entry).append('\n');
        for (String entry : info.metadataEntries) text.append("metadata=").append(entry).append('\n');
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out, false))) {
            os.write(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static void writeDirectory(ZipOutputStream out, ZipEntry original) throws IOException {
        ZipEntry copy = new ZipEntry(original.getName());
        copy.setTime(original.getTime());
        out.putNextEntry(copy);
        out.closeEntry();
    }

    private static void writeFileEntry(ZipOutputStream out, String name, ZipEntry original, InputStream in) throws IOException {
        ZipEntry copy = new ZipEntry(name);
        copy.setTime(original.getTime());
        if (original.getComment() != null) copy.setComment(original.getComment());
        out.putNextEntry(copy);
        ApkEditorFileUtils.copy(in, out);
        out.closeEntry();
    }

    private static String safeEntryFileName(String entryName) {
        if (TextUtils.isEmpty(entryName)) return "split.apk";
        String base = entryName.replace('/', '_').replace('\\', '_');
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (TextUtils.isEmpty(base) || !base.toLowerCase(Locale.US).endsWith(".apk")) base = "split.apk";
        return base;
    }

    private static boolean isMetadataEntry(String lowerName) {
        if (TextUtils.isEmpty(lowerName)) return false;
        return lowerName.endsWith(".json") || lowerName.endsWith(".pb") || lowerName.endsWith(".xml")
                || lowerName.endsWith(".txt") || lowerName.endsWith(".properties") || lowerName.endsWith(".idsig")
                || lowerName.contains("manifest") || lowerName.contains("toc") || lowerName.contains("stamp")
                || lowerName.startsWith("meta-inf/");
    }

    private static int scoreApkEntryName(String name) {
        if (name == null) return 0;
        String n = name.toLowerCase(Locale.US);
        String base = n;
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        int score = 0;
        boolean configSplit = base.startsWith("config.") || base.startsWith("split_config.")
                || base.contains("dpi") || base.contains("density") || base.contains("lang") || base.contains("locale");
        if ("base.apk".equals(base)) score += 10000;
        if (base.startsWith("base-") && base.endsWith(".apk")) score += 9000;
        if (base.contains("master") && base.endsWith(".apk")) score += 8000;
        if (configSplit) score -= 5000;
        else score += 1000;
        score -= Math.min(500, base.length());
        return score;
    }
}
