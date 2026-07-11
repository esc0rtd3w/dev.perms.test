package dev.perms.test.packages.editor;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import dev.perms.test.apk.BinaryXmlDebuggablePatcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ApkEditorArchiveInspector {
    public static final class Summary {
        public boolean singleApk;
        public long sizeBytes;
        public int entryCount;
        public int apkEntryCount;
        public int dexEntryCount;
        public int nativeLibCount;
        public int resourceEntryCount;
        public String packageName = "";
        public boolean debuggable;
        public boolean manifestFound;
        public String baseApkEntryName = "";
        public final ArrayList<String> apkEntryNames = new ArrayList<>();
        public String error = "";

        public boolean isSplitContainer() {
            return !singleApk && apkEntryCount > 1 && !TextUtils.isEmpty(baseApkEntryName);
        }

        public String toReport(File source) {
            StringBuilder out = new StringBuilder();
            out.append("APK Editor inspect\n");
            if (source != null) out.append("Source: ").append(source.getAbsolutePath()).append('\n');
            out.append("Type: ").append(singleApk ? "single APK" : "package archive / split container").append('\n');
            out.append("Size: ").append(sizeBytes).append(" bytes\n");
            out.append("Zip entries: ").append(entryCount).append('\n');
            if (!singleApk) out.append("APK entries: ").append(apkEntryCount).append('\n');
            if (!TextUtils.isEmpty(baseApkEntryName)) out.append("Base APK: ").append(baseApkEntryName).append('\n');
            if (!singleApk && !apkEntryNames.isEmpty()) {
                out.append("Split entries:\n");
                int limit = Math.min(apkEntryNames.size(), 40);
                for (int i = 0; i < limit; i++) {
                    String entry = apkEntryNames.get(i);
                    out.append(entry.equals(baseApkEntryName) ? "  * " : "  - ").append(entry).append('\n');
                }
                if (apkEntryNames.size() > limit) out.append("  ... ").append(apkEntryNames.size() - limit).append(" more APK entries\n");
            }
            out.append("DEX files: ").append(dexEntryCount).append('\n');
            out.append("Native libs: ").append(nativeLibCount).append('\n');
            out.append("Resource-like entries: ").append(resourceEntryCount).append('\n');
            out.append("Manifest: ").append(manifestFound ? "found" : "not found").append('\n');
            if (!TextUtils.isEmpty(packageName)) out.append("Package: ").append(packageName).append('\n');
            out.append("Debuggable: ").append(debuggable ? "true" : "false/unknown").append('\n');
            if (!TextUtils.isEmpty(error)) out.append("Note: ").append(error).append('\n');
            return out.toString();
        }
    }

    private ApkEditorArchiveInspector() {
    }

    public static Summary inspect(Context context, File source) {
        Summary s = new Summary();
        if (source == null || !source.isFile()) {
            s.error = "Source package is missing.";
            return s;
        }
        s.sizeBytes = source.length();
        s.singleApk = isApkFile(source);
        if (s.singleApk) {
            inspectSingleApk(context, source, s);
        } else {
            inspectContainer(context, source, s);
        }
        return s;
    }

    public static boolean isApkFile(File file) {
        return file != null && file.getName().toLowerCase(Locale.US).endsWith(".apk");
    }

    private static void inspectSingleApk(Context context, File source, Summary s) {
        try (ZipFile zip = new ZipFile(source)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e == null || e.isDirectory()) continue;
                String name = e.getName();
                s.entryCount++;
                countEntry(name, s);
                if ("AndroidManifest.xml".equals(name)) {
                    s.manifestFound = true;
                    byte[] manifest = readEntry(zip, e, 3 * 1024 * 1024);
                    s.packageName = nullToEmpty(BinaryXmlDebuggablePatcher.getManifestPackageName(manifest));
                    s.debuggable = BinaryXmlDebuggablePatcher.isDebuggableEnabled(manifest);
                }
            }
        } catch (Throwable t) {
            s.error = "Zip inspect failed: " + shortError(t);
        }
        enrichFromPackageManager(context, source, s);
    }

    private static void inspectContainer(Context context, File source, Summary s) {
        try (ZipFile zip = new ZipFile(source)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e == null || e.isDirectory()) continue;
                String name = e.getName();
                String lower = name.toLowerCase(Locale.US);
                s.entryCount++;
                if (lower.endsWith(".apk")) {
                    s.apkEntryCount++;
                    s.apkEntryNames.add(name);
                    if (TextUtils.isEmpty(s.baseApkEntryName) || scoreApkEntryName(name) > scoreApkEntryName(s.baseApkEntryName)) {
                        s.baseApkEntryName = name;
                    }
                }
            }
            if (!TextUtils.isEmpty(s.baseApkEntryName)) {
                File base = ApkEditorFileUtils.extractZipEntryToTemp(source, s.baseApkEntryName, "apk-editor-base", ".apk");
                try {
                    Summary baseSummary = new Summary();
                    baseSummary.singleApk = true;
                    baseSummary.sizeBytes = base.length();
                    inspectSingleApk(context, base, baseSummary);
                    s.dexEntryCount = baseSummary.dexEntryCount;
                    s.nativeLibCount = baseSummary.nativeLibCount;
                    s.resourceEntryCount = baseSummary.resourceEntryCount;
                    s.manifestFound = baseSummary.manifestFound;
                    s.packageName = baseSummary.packageName;
                    s.debuggable = baseSummary.debuggable;
                } finally {
                    if (base != null) try { base.delete(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            s.error = "Archive inspect failed: " + shortError(t);
        }
    }

    private static void enrichFromPackageManager(Context context, File apk, Summary s) {
        if (context == null || apk == null || !apk.isFile()) return;
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi;
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.PackageInfoFlags.of(0));
            } else {
                pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            }
            if (pi == null) return;
            if (TextUtils.isEmpty(s.packageName)) s.packageName = nullToEmpty(pi.packageName);
            ApplicationInfo ai = pi.applicationInfo;
            if (ai != null) s.debuggable = s.debuggable || ((ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        } catch (Throwable ignored) {
        }
    }

    private static void countEntry(String name, Summary s) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".dex")) s.dexEntryCount++;
        if (lower.startsWith("lib/") && lower.endsWith(".so")) s.nativeLibCount++;
        if (lower.startsWith("res/") || lower.equals("resources.arsc") || lower.startsWith("assets/")) {
            s.resourceEntryCount++;
        }
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

    private static byte[] readEntry(ZipFile zip, ZipEntry entry, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(Math.max(entry.getSize(), 0), 65536));
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] buf = new byte[64 * 1024];
            int total = 0;
            int r;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > maxBytes) throw new IOException("Entry too large: " + entry.getName());
                out.write(buf, 0, r);
            }
        }
        return out.toByteArray();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String shortError(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        if (TextUtils.isEmpty(msg)) return t.getClass().getSimpleName();
        return t.getClass().getSimpleName() + ": " + msg;
    }
}
