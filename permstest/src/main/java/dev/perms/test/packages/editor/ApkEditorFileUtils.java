package dev.perms.test.packages.editor;

import android.content.ContentResolver;
import android.net.Uri;
import android.text.TextUtils;

import dev.perms.test.apk.ApkDebugToolHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ApkEditorFileUtils {
    private ApkEditorFileUtils() {
    }

    public static File stageUri(ContentResolver resolver, Uri uri, File dir, String label) throws IOException {
        if (resolver == null || uri == null) throw new IOException("Package file is not selected.");
        if (dir == null) throw new IOException("Stage directory is missing.");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Unable to create " + dir.getAbsolutePath());
        String name = ApkDebugToolHelper.defaultStageFilename(uri, label);
        File out = new File(dir, name);
        InputStream raw = resolver.openInputStream(uri);
        if (raw == null) throw new IOException("Unable to open selected package.");
        try (InputStream in = new BufferedInputStream(raw);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(out, false))) {
            copy(in, os);
        }
        if (!out.isFile() || out.length() <= 0) throw new IOException("Staged package is empty.");
        return out;
    }

    public static File publicWorkspace(String text, String label) {
        String requested = text == null ? "" : text.trim();
        if (!TextUtils.isEmpty(requested)) return new File(requested);
        String stem = stem(label);
        return new File("/storage/emulated/0/dev.perms.test/apk_editor/" + stem);
    }

    public static String stem(String label) {
        String name = ApkDebugToolHelper.sanitizeSourceName(label);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (TextUtils.isEmpty(name)) name = "package";
        return name;
    }

    public static void unzip(File zipFile, File outDir) throws IOException {
        if (zipFile == null || !zipFile.isFile()) throw new IOException("Source package is missing.");
        if (outDir == null) throw new IOException("Output directory is missing.");
        if (!outDir.exists() && !outDir.mkdirs()) throw new IOException("Unable to create " + outDir.getAbsolutePath());
        String root = outDir.getCanonicalPath() + File.separator;
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buf = new byte[128 * 1024];
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e == null) continue;
                File dst = new File(outDir, e.getName());
                String dstPath = dst.getCanonicalPath();
                if (!dstPath.startsWith(root)) throw new IOException("Blocked unsafe zip entry: " + e.getName());
                if (e.isDirectory()) {
                    if (!dst.exists() && !dst.mkdirs()) throw new IOException("Unable to create " + dstPath);
                    continue;
                }
                File parent = dst.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Unable to create " + parent.getAbsolutePath());
                }
                try (InputStream in = zip.getInputStream(e);
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(dst, false))) {
                    int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                }
            }
        }
    }

    public static File exportFile(File source, File dest) throws IOException {
        if (source == null || !source.isFile()) throw new IOException("Source file is missing.");
        if (dest == null) throw new IOException("Export path is missing.");
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create " + parent.getAbsolutePath());
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dest, false))) {
            copy(in, out);
        }
        return dest;
    }

    public static File extractZipEntryToTemp(File zipFile, String entryName, String prefix, String suffix) throws IOException {
        if (zipFile == null || !zipFile.isFile()) throw new IOException("Archive is missing.");
        if (TextUtils.isEmpty(entryName)) throw new IOException("Archive entry is empty.");
        File tmp = File.createTempFile(prefix, suffix);
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry e = zip.getEntry(entryName);
            if (e == null || e.isDirectory()) throw new IOException("Entry not found: " + entryName);
            try (InputStream in = zip.getInputStream(e);
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp, false))) {
                copy(in, out);
            }
        }
        return tmp;
    }

    public static File findToolInputApk(File source, File stageDir) throws IOException {
        if (source == null || !source.isFile()) throw new IOException("Source package is missing.");
        if (source.getName().toLowerCase(Locale.US).endsWith(".apk")) return source;
        String base = findBaseApkEntry(source);
        if (TextUtils.isEmpty(base)) throw new IOException("No base APK found inside archive. Simple Unpack can still inspect it.");
        if (stageDir != null && !stageDir.exists()) stageDir.mkdirs();
        File out = new File(stageDir == null ? source.getParentFile() : stageDir, "base-from-archive.apk");
        try (ZipFile zip = new ZipFile(source)) {
            ZipEntry e = zip.getEntry(base);
            if (e == null) throw new IOException("Base APK entry disappeared: " + base);
            try (InputStream in = zip.getInputStream(e);
                 OutputStream dst = new BufferedOutputStream(new FileOutputStream(out, false))) {
                copy(in, dst);
            }
        }
        return out;
    }

    public static String findBaseApkEntry(File archive) throws IOException {
        String fallback = "";
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e == null || e.isDirectory()) continue;
                String name = e.getName();
                String lower = name.toLowerCase(Locale.US);
                if (!lower.endsWith(".apk")) continue;
                if (TextUtils.isEmpty(fallback)) fallback = name;
                if (lower.endsWith("base.apk") || lower.equals("base-master.apk") || lower.contains("/base")) return name;
            }
        }
        return fallback;
    }

    public static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
        }
        try { file.delete(); } catch (Throwable ignored) {}
    }

    public static String readText(File file, int maxBytes) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Text file is missing.");
        if (maxBytes <= 0) maxBytes = 2 * 1024 * 1024;
        if (file.length() > maxBytes) throw new IOException("Text file is too large to preview: " + file.length() + " bytes");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file));
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int) Math.min(file.length(), 64 * 1024))) {
            copy(in, out);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        if (in == null || out == null) throw new IOException("Stream is missing.");
        byte[] buf = new byte[128 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        out.flush();
    }
}
