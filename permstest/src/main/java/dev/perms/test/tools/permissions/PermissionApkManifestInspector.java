package dev.perms.test.tools.permissions;

import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import dev.perms.test.apk.BinaryXmlPermissionPatcher;

/** Reads declared uses-permission entries from APKs and split-APK containers. */
final class PermissionApkManifestInspector {
    private static final Pattern TEXT_PERMISSION = Pattern.compile("<uses-permission\\b[^>]*?android:name\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int MAX_MANIFEST_BYTES = 8 * 1024 * 1024;
    private static final int MAX_NESTED_APK_SCAN_BYTES = 256 * 1024 * 1024;

    private PermissionApkManifestInspector() {
    }

    static Result readDeclaredPermissionsResult(File source) throws IOException {
        if (source == null || !source.isFile()) throw new IOException("APK file is missing.");
        try (ZipFile zip = new ZipFile(source)) {
            ZipEntry manifestEntry = zip.getEntry("AndroidManifest.xml");
            if (manifestEntry != null) {
                byte[] manifest;
                try (InputStream in = zip.getInputStream(manifestEntry)) {
                    manifest = readAllBounded(in, MAX_MANIFEST_BYTES, "AndroidManifest.xml");
                }
                return new Result(readPermissions(manifest), false, "", 0, 0);
            }
            return readSplitContainer(zip);
        }
    }

    static List<String> readDeclaredPermissions(File source) throws IOException {
        return readDeclaredPermissionsResult(source).permissions;
    }

    static boolean looksLikeSplitContainer(File source) {
        if (source == null || !source.isFile()) return false;
        try (ZipFile zip = new ZipFile(source)) {
            if (zip.getEntry("AndroidManifest.xml") != null) return false;
            int count = 0;
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e != null && !e.isDirectory() && isApkEntry(e.getName())) {
                    count++;
                    if (count > 0) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Result readSplitContainer(ZipFile zip) throws IOException {
        List<ZipEntry> apkEntries = new ArrayList<>();
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e != null && !e.isDirectory() && isApkEntry(e.getName())) apkEntries.add(e);
        }
        if (apkEntries.isEmpty()) throw new IOException("AndroidManifest.xml was not found in the APK or split container.");
        Collections.sort(apkEntries, new Comparator<ZipEntry>() {
            @Override
            public int compare(ZipEntry a, ZipEntry b) {
                int sa = splitApkScore(a == null ? null : a.getName());
                int sb = splitApkScore(b == null ? null : b.getName());
                if (sa != sb) return Integer.compare(sa, sb);
                String an = a == null ? "" : a.getName();
                String bn = b == null ? "" : b.getName();
                return an.compareToIgnoreCase(bn);
            }
        });

        IOException lastError = null;
        for (ZipEntry entry : apkEntries) {
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] manifest = readNestedApkManifest(in, entry.getName());
                if (manifest == null) continue;
                return new Result(readPermissions(manifest), true, entry.getName(), apkEntries.size(), splitApkScore(entry.getName()));
            } catch (IOException e) {
                lastError = e;
            }
        }
        if (lastError != null) throw lastError;
        throw new IOException("No nested APK manifest was found in the split container.");
    }

    private static byte[] readNestedApkManifest(InputStream in, String label) throws IOException {
        if (in == null) return null;
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry nested;
            int scanned = 0;
            byte[] buf = new byte[32768];
            while ((nested = zis.getNextEntry()) != null) {
                String name = nested.getName();
                if ("AndroidManifest.xml".equals(name)) {
                    return readAllBounded(zis, MAX_MANIFEST_BYTES, label + "!/AndroidManifest.xml");
                }
                int n;
                while ((n = zis.read(buf)) != -1) {
                    scanned += n;
                    if (scanned > MAX_NESTED_APK_SCAN_BYTES) throw new IOException(label + " is too large to inspect safely.");
                }
            }
        }
        return null;
    }

    private static boolean isApkEntry(String name) {
        return name != null && name.toLowerCase(Locale.US).endsWith(".apk");
    }

    private static int splitApkScore(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        String leaf = lower;
        int slash = leaf.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < leaf.length()) leaf = leaf.substring(slash + 1);
        if ("base.apk".equals(leaf)) return 0;
        if (leaf.startsWith("base-") || leaf.startsWith("base_")) return 1;
        if (leaf.contains("master") || leaf.contains("base")) return 2;
        if (!leaf.contains("split") && !leaf.contains("config.")) return 3;
        return 10;
    }

    private static List<String> readPermissions(byte[] manifest) throws IOException {
        if (manifest == null || manifest.length == 0) return Collections.emptyList();
        try {
            return normalize(BinaryXmlPermissionPatcher.readUsesPermissions(manifest));
        } catch (IOException binaryError) {
            String text = new String(manifest, StandardCharsets.UTF_8);
            List<String> parsed = readTextPermissions(text);
            if (!parsed.isEmpty()) return parsed;
            throw binaryError;
        }
    }

    private static List<String> readTextPermissions(String xml) {
        if (TextUtils.isEmpty(xml)) return Collections.emptyList();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher m = TEXT_PERMISSION.matcher(xml);
        while (m.find()) {
            String permission = m.group(1) == null ? "" : m.group(1).trim();
            if (!TextUtils.isEmpty(permission)) out.add(permission);
        }
        return new ArrayList<>(out);
    }

    private static List<String> normalize(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return Collections.emptyList();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String raw : permissions) {
            String permission = raw == null ? "" : raw.trim();
            if (!TextUtils.isEmpty(permission)) out.add(permission);
        }
        return new ArrayList<>(out);
    }

    private static byte[] readAllBounded(InputStream in, int maxBytes, String label) throws IOException {
        if (in == null) throw new IOException(label + " input is unavailable.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[32768];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) throw new IOException(label + " is too large to inspect safely.");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static final class Result {
        final List<String> permissions;
        final boolean splitContainer;
        final String sourceEntry;
        final int apkEntryCount;
        final int sourceEntryScore;

        Result(List<String> permissions, boolean splitContainer, String sourceEntry, int apkEntryCount, int sourceEntryScore) {
            this.permissions = permissions == null ? Collections.emptyList() : permissions;
            this.splitContainer = splitContainer;
            this.sourceEntry = sourceEntry == null ? "" : sourceEntry;
            this.apkEntryCount = apkEntryCount;
            this.sourceEntryScore = sourceEntryScore;
        }
    }
}
