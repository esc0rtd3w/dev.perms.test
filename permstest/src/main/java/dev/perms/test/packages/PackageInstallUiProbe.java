package dev.perms.test.packages;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

import dev.perms.test.apk.BinaryXmlDebuggablePatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Probes install results and package archives for package names and app labels.
 */
public final class PackageInstallUiProbe {
    public static final class InstallUiInfo {
        public final String packageName;
        public final String appLabel;

        public InstallUiInfo(String packageName, String appLabel) {
            this.packageName = packageName;
            this.appLabel = appLabel;
        }
    }

    private final Context context;

    public PackageInstallUiProbe(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    public InstallUiInfo refreshInstalledUiInfo(InstallUiInfo info) {
        try {
            if (info == null || TextUtils.isEmpty(info.packageName) || context == null) return info;
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(info.packageName, 0);
            String label = info.appLabel;
            try {
                CharSequence cs = pm.getApplicationLabel(pi.applicationInfo);
                if (cs != null && !TextUtils.isEmpty(cs.toString())) label = cs.toString();
            } catch (Throwable ignored) {}
            return new InstallUiInfo(info.packageName, TextUtils.isEmpty(label) ? info.appLabel : label);
        } catch (Throwable ignored) {
            return info;
        }
    }

    public InstallUiInfo probeInstalledPackageFromNameHints(String... hints) {
        try {
            if (context == null) return null;
            PackageManager pm = context.getPackageManager();
            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            if (hints != null) {
                for (String hint : hints) {
                    addPackageNameHintCandidates(candidates, hint);
                }
            }

            for (String pkg : candidates) {
                if (TextUtils.equals(pkg, context.getPackageName())) continue;
                try {
                    PackageInfo pi = pm.getPackageInfo(pkg, 0);
                    String label = pkg;
                    try {
                        CharSequence cs = pm.getApplicationLabel(pi.applicationInfo);
                        if (cs != null) label = cs.toString();
                    } catch (Throwable ignored) {}
                    return new InstallUiInfo(pkg, label);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public InstallUiInfo probeInstallUiInfo(String path, String originalLabel) {
        try {
            if (path == null) return null;
            String lower = path.toLowerCase(Locale.US);

            if (lower.endsWith(".apk")) {
                return probeApkUiInfo(path, originalLabel);
            }

            if (lower.endsWith(".xapk") || lower.endsWith(".apkm") || lower.endsWith(".apks") || lower.endsWith(".zip")) {
                // Try multiple APK candidates from the archive until we can read package info.
                InstallUiInfo info = probeArchiveUiInfo(path, originalLabel);
                if (info != null) return info;

                // Legacy fallback: single "best" apk heuristic.
                File extracted = null;
                try {
                    extracted = extractBestApkFromZip(path);
                    if (extracted != null && extracted.exists() && extracted.length() > 0) {
                        return probeApkUiInfo(extracted.getAbsolutePath(), originalLabel);
                    }
                } finally {
                    if (extracted != null) {
                        try { extracted.delete(); } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private InstallUiInfo probeArchiveUiInfo(String zipPath, String originalLabel) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipPath);

            ArrayList<ZipEntry> candidates = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze == null || ze.isDirectory()) continue;
                String n = ze.getName();
                if (n == null) continue;
                if (!n.toLowerCase(Locale.US).endsWith(".apk")) continue;
                candidates.add(ze);
            }
            if (candidates.isEmpty()) return null;
            java.util.Collections.sort(candidates, (a, b) -> {
                String an = a.getName() == null ? "" : a.getName();
                String bn = b.getName() == null ? "" : b.getName();
                int sa = scoreApkEntryName(an);
                int sb = scoreApkEntryName(bn);
                if (sa != sb) return (sb - sa);
                long asz = a.getSize();
                long bsz = b.getSize();
                if (asz != bsz) return (bsz > asz) ? 1 : -1;
                return Integer.compare(an.length(), bn.length());
            });

            for (ZipEntry ze : candidates) {
                File out = null;
                try {
                    out = new File(getCacheDir(), "probe_" + System.nanoTime() + ".apk");
                    try (InputStream in = zf.getInputStream(ze);
                         FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[64 * 1024];
                        int r;
                        while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                    }
                    InstallUiInfo info = probeApkUiInfo(out.getAbsolutePath(), originalLabel);
                    if (info != null && !TextUtils.isEmpty(info.packageName)) return info;
                } catch (Throwable ignored) {
                } finally {
                    try { if (out != null) out.delete(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (zf != null) zf.close(); } catch (Throwable ignored) {}
        }
        return null;
    }

    private int scoreApkEntryName(String name) {
        if (name == null) return 0;
        String n = name.toLowerCase(Locale.US);
        int s = 0;

        if (n.equals("base.apk") || n.endsWith("/base.apk")) s += 1000;
        if (n.contains("base")) s += 200;
        if (n.contains("master") || n.contains("main")) s += 120;

        boolean configish = (n.contains("config.") || n.contains("split_config") || n.contains("dpi") || n.contains("density") || n.contains("lang") || n.contains("locale"));
        if (!configish) s += 160;

        s += Math.max(0, 30 - Math.min(30, n.length() / 8));
        return s;
    }

    private InstallUiInfo probeApkUiInfo(String apkPath, String originalLabel) {
        try {
            if (context == null) return null;
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);
            if (pi != null && pi.packageName != null) {
                String pkg = pi.packageName;
                String label = originalLabel;

                try {
                    ApplicationInfo ai = pi.applicationInfo;
                    if (ai != null) {
                        ai.sourceDir = apkPath;
                        ai.publicSourceDir = apkPath;
                        CharSequence cs = pm.getApplicationLabel(ai);
                        if (cs != null) label = cs.toString();
                    }
                } catch (Throwable ignored) {}

                if (label == null || label.trim().isEmpty()) label = pkg;
                return new InstallUiInfo(pkg, label);
            }
        } catch (Throwable ignored) {}

        try {
            String parsedPkg = readPackageNameFromApkManifest(apkPath);
            if (!TextUtils.isEmpty(parsedPkg)) return new InstallUiInfo(parsedPkg, originalLabel);
        } catch (Throwable ignored) {}
        return null;
    }

    private String readPackageNameFromApkManifest(String apkPath) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(apkPath);
            ZipEntry manifest = zf.getEntry("AndroidManifest.xml");
            if (manifest == null || manifest.getSize() > 1024L * 1024L) return null;
            try (InputStream in = zf.getInputStream(manifest)) {
                byte[] data = readSmallStream(in, 1024 * 1024);
                return BinaryXmlDebuggablePatcher.getManifestPackageName(data);
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (zf != null) zf.close(); } catch (Throwable ignored) {}
        }
    }

    private static byte[] readSmallStream(InputStream in, int maxBytes) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[32 * 1024];
        int total = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            total += r;
            if (total > maxBytes) throw new java.io.IOException("stream is too large");
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private File extractBestApkFromZip(String zipPath) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipPath);
            ZipEntry best = null;

            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze == null || ze.isDirectory()) continue;
                String n = ze.getName();
                if (n == null) continue;
                String nl = n.toLowerCase(Locale.US);
                if (!nl.endsWith(".apk")) continue;

                if (best == null) {
                    best = ze;
                } else {
                    String bl = best.getName().toLowerCase(Locale.US);
                    boolean candBase = nl.endsWith("base.apk") || nl.contains("/base.apk") || nl.contains("base-") || nl.contains("base_");
                    boolean bestBase = bl.endsWith("base.apk") || bl.contains("/base.apk") || bl.contains("base-") || bl.contains("base_");
                    if (candBase && !bestBase) best = ze;
                    else if (candBase == bestBase && n.length() < best.getName().length()) best = ze;
                }
            }

            if (best == null) return null;

            File out = new File(getCacheDir(), "probe_" + System.currentTimeMillis() + ".apk");
            try (InputStream in = zf.getInputStream(best);
                 FileOutputStream fos = new FileOutputStream(out, false)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                fos.flush();
                return out;
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (zf != null) zf.close(); } catch (Throwable ignored) {}
        }
    }

    private File getCacheDir() {
        File dir = context == null ? null : context.getCacheDir();
        if (dir != null) return dir;
        return new File(System.getProperty("java.io.tmpdir", "."));
    }

    private static void addPackageNameHintCandidates(LinkedHashSet<String> candidates, String hint) {
        try {
            if (candidates == null || TextUtils.isEmpty(hint)) return;

            String decoded = Uri.decode(hint);
            String text = TextUtils.isEmpty(decoded) ? hint : decoded;
            String slashText = text.replace('\\', '/');
            String name = slashText;
            int slash = slashText.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < slashText.length()) name = slashText.substring(slash + 1);
            name = name.replaceAll("(?i)\\.(apk|apkm|apks|xapk|zip)$", "");

            ArrayList<String> seeds = new ArrayList<>();
            seeds.add(text);
            if (!TextUtils.equals(text, name)) seeds.add(name);

            Pattern pkgPattern = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*){1,}");
            for (String seed : seeds) {
                if (TextUtils.isEmpty(seed)) continue;

                Matcher m = pkgPattern.matcher(seed);
                while (m.find()) {
                    addNormalizedPackageCandidate(candidates, m.group());
                }

                int cut = firstPackageHintMetadataDelimiter(seed);
                if (cut > 0) {
                    Matcher head = pkgPattern.matcher(seed.substring(0, cut));
                    while (head.find()) {
                        addNormalizedPackageCandidate(candidates, head.group());
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int firstPackageHintMetadataDelimiter(String value) {
        if (TextUtils.isEmpty(value)) return -1;
        int best = -1;
        char[] delimiters = new char[]{'_', '-', ' ', '('};
        for (char delimiter : delimiters) {
            int idx = value.indexOf(delimiter);
            if (idx > 0 && (best < 0 || idx < best)) best = idx;
        }
        return best;
    }

    private static void addNormalizedPackageCandidate(LinkedHashSet<String> candidates, String value) {
        if (candidates == null || TextUtils.isEmpty(value)) return;
        String candidate = value.trim();
        if (candidate.endsWith(".apk") || candidate.endsWith(".apkm") || candidate.endsWith(".apks") || candidate.endsWith(".xapk")) {
            int dot = candidate.lastIndexOf('.');
            if (dot > 0) candidate = candidate.substring(0, dot);
        }
        if (isReasonablePackageName(candidate)) candidates.add(candidate);

        String trimmed = stripLikelyPackageFilenameSuffix(candidate);
        if (!TextUtils.equals(candidate, trimmed) && isReasonablePackageName(trimmed)) {
            candidates.add(trimmed);
        }
    }

    private static String stripLikelyPackageFilenameSuffix(String candidate) {
        if (TextUtils.isEmpty(candidate)) return candidate;
        int lastDot = candidate.lastIndexOf('.');
        int underscore = candidate.indexOf('_', Math.max(0, lastDot + 1));
        if (underscore <= lastDot || underscore + 1 >= candidate.length()) return candidate;

        String suffix = candidate.substring(underscore + 1).toLowerCase(Locale.US);
        if (suffix.matches("(v?\\d.*|minapi.*|maxapi.*|arm.*|x86.*|nodpi.*|hdpi.*|xhdpi.*|xxhdpi.*|xxxhdpi.*)")) {
            return candidate.substring(0, underscore);
        }
        return candidate;
    }

    private static boolean isReasonablePackageName(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String v = value.trim();
        if (v.endsWith(".apk") || v.endsWith(".apkm") || v.endsWith(".apks") || v.endsWith(".xapk")) return false;
        if (v.startsWith("http.") || v.startsWith("https.")) return false;
        int dots = 0;
        for (int i = 0; i < v.length(); i++) if (v.charAt(i) == '.') dots++;
        return dots >= 1 && v.length() >= 5 && v.length() <= 160;
    }
}
