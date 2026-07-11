package dev.perms.test.files;

import android.text.TextUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;

/** Shared path, listing, and row-metadata helpers for the Files tab browser. */
public final class FilesBrowserUtils {
    private FilesBrowserUtils() {
    }

    public static String normalizePath(String path) {
        if (TextUtils.isEmpty(path)) return "/";
        String p = path.trim();
        if (p.isEmpty()) return "/";
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    public static String childPath(String cwd, String name) {
        String base = TextUtils.isEmpty(cwd) ? "/" : cwd;
        if ("/".equals(base)) return "/" + name;
        return base.endsWith("/") ? (base + name) : (base + "/" + name);
    }

    public static boolean isPackageArchive(String name) {
        if (TextUtils.isEmpty(name)) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".apkm") || n.endsWith(".xapk");
    }

    public static boolean isKnownOpenable(String name) {
        if (TextUtils.isEmpty(name)) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return isPackageArchive(n)
                || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".gif")
                || n.endsWith(".txt") || n.endsWith(".log") || n.endsWith(".md")
                || n.endsWith(".json") || n.endsWith(".xml") || n.endsWith(".js") || n.endsWith(".java") || n.endsWith(".go") || n.endsWith(".sh")
                || n.endsWith(".zip") || n.endsWith(".7z") || n.endsWith(".rar") || n.endsWith(".tar") || n.endsWith(".gz")
                || n.endsWith(".pdf") || n.endsWith(".html") || n.endsWith(".htm")
                || n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".wav") || n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm");
    }

    public static boolean isSafeChildName(String name) {
        if (TextUtils.isEmpty(name)) return false;
        String n = name.trim();
        return !n.isEmpty() && !".".equals(n) && !"..".equals(n) && !n.contains("/") && n.indexOf('\0') < 0;
    }

    public static ArrayList<FileEntry> parseStatListing(String cwd, String out) {
        ArrayList<FileEntry> entries = new ArrayList<>();
        boolean listedDirectory = true;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String[] lines = (out == null ? "" : out).split("\n");
        for (String line : lines) {
            if (TextUtils.isEmpty(line)) continue;
            if (line.startsWith("__PT_FILES_TARGET__|")) {
                listedDirectory = line.endsWith("|d");
                continue;
            }
            String[] parts = line.split("\\|", 4);
            if (parts.length < 4) continue;
            String name = parts[3];
            if (listedDirectory && name.startsWith("./")) name = name.substring(2);
            if (!listedDirectory) name = new File(name).getName();
            if (TextUtils.isEmpty(name) || ".".equals(name) || "..".equals(name)) continue;
            String key = name.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;

            String type = parts[0] == null ? "" : parts[0].toLowerCase(Locale.ROOT);
            boolean isDir = type.contains("directory");
            boolean isLink = type.contains("symbolic link");
            long size = isDir ? 0L : parseLongQuiet(parts[1], 0L);
            long modified = parseLongQuiet(parts[2], 0L);
            String full = listedDirectory ? childPath(cwd, name) : normalizePath(cwd);
            entries.add(new FileEntry(name, isDir, isLink, full, size, modified, meta(name, isDir, isLink, size, modified)));
        }
        sortEntries(entries);
        return entries;
    }

    public static ArrayList<FileEntry> listWithJavaIfAvailable(String cwd) {
        try {
            File dir = new File(cwd);
            File[] kids = dir.listFiles();
            if (kids == null) return null;
            return buildJavaEntries(kids);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static ArrayList<FileEntry> listWithJavaFallback(String cwd) {
        try {
            File dir = new File(cwd);
            File[] kids = dir.listFiles();
            if (kids == null) return new ArrayList<>();
            return buildJavaEntries(kids);
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private static ArrayList<FileEntry> buildJavaEntries(File[] kids) {
        ArrayList<FileEntry> entries = new ArrayList<>();
        if (kids == null) return entries;
        for (File kid : kids) {
            if (kid == null) continue;
            String name = kid.getName();
            if (TextUtils.isEmpty(name)) continue;
            boolean isDir = kid.isDirectory();
            boolean isLink = false;
            try { isLink = !kid.getCanonicalPath().equals(kid.getAbsolutePath()); } catch (Throwable ignored) {}
            long size = isDir ? 0L : kid.length();
            long modified = kid.lastModified() > 0 ? (kid.lastModified() / 1000L) : 0L;
            entries.add(new FileEntry(name, isDir, isLink, kid.getAbsolutePath(), size, modified, meta(name, isDir, isLink, size, modified)));
        }
        sortEntries(entries);
        return entries;
    }

    public static void sortEntries(ArrayList<FileEntry> entries) {
        if (entries == null) return;
        Collections.sort(entries, (a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });
    }

    public static String iconFor(FileEntry entry) {
        if (entry == null) return "•";
        if (entry.isDir) return "📁";
        if (entry.isLink) return "🔗";
        String name = entry.name == null ? "" : entry.name.toLowerCase(Locale.ROOT);
        if (name.endsWith(".apk") || name.endsWith(".apks") || name.endsWith(".apkm") || name.endsWith(".xapk")) return "🤖";
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif")) return "🖼️";
        if (name.endsWith(".zip") || name.endsWith(".7z") || name.endsWith(".rar") || name.endsWith(".tar") || name.endsWith(".gz")) return "📦";
        if (name.endsWith(".pdf")) return "📕";
        if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav")) return "🎵";
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm")) return "🎬";
        if (name.endsWith(".html") || name.endsWith(".htm")) return "🌐";
        if (name.endsWith(".sh") || name.endsWith(".bat") || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".js") || name.endsWith(".go") || name.endsWith(".java")) return "</>";
        if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".md")) return "📄";
        return "📄";
    }

    public static String meta(String name, boolean isDir, boolean isLink, long size, long modifiedEpoch) {
        StringBuilder sb = new StringBuilder();
        if (isDir) sb.append(isLink ? "Linked folder" : "Folder");
        else if (isLink) sb.append("Link");
        else sb.append(formatFileSize(size)).append(" · ").append(kindForName(name));
        if (modifiedEpoch > 0) sb.append(" · ").append(formatFileTime(modifiedEpoch));
        return sb.toString();
    }

    public static String kindForName(String name) {
        if (TextUtils.isEmpty(name)) return "File";
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".apk")) return "APK";
        if (n.endsWith(".apks") || n.endsWith(".apkm") || n.endsWith(".xapk")) return "Split APK bundle";
        if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".gif")) return "Image";
        if (n.endsWith(".zip") || n.endsWith(".7z") || n.endsWith(".rar") || n.endsWith(".tar") || n.endsWith(".gz")) return "Archive";
        if (n.endsWith(".txt") || n.endsWith(".log") || n.endsWith(".md")) return "Text";
        if (n.endsWith(".json") || n.endsWith(".xml") || n.endsWith(".js") || n.endsWith(".java") || n.endsWith(".go") || n.endsWith(".sh")) return "Code/Text";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < name.length() && name.length() - dot <= 8) {
            return name.substring(dot + 1).toUpperCase(Locale.ROOT) + " file";
        }
        return "File";
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 0) bytes = 0;
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        if (unit == 0) return bytes + " B";
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    public static String formatFileTime(long epochSeconds) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(epochSeconds * 1000L));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String stripPackageArchiveExtension(String name) {
        if (TextUtils.isEmpty(name)) return "";
        String lower = name.toLowerCase(Locale.ROOT);
        String[] extensions = {".apks", ".apkm", ".xapk", ".apk"};
        for (String ext : extensions) {
            if (lower.endsWith(ext)) return lower.substring(0, lower.length() - ext.length());
        }
        return lower;
    }

    public static boolean archivePathMatchesInstalledPackage(String lowerPath, String baseName, String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        if (packageName.equals(baseName)) return true;
        if (!TextUtils.isEmpty(baseName) && baseName.startsWith(packageName)) {
            if (baseName.length() == packageName.length()) return true;
            char next = baseName.charAt(packageName.length());
            if (next == '_' || next == '-' || next == ' ' || next == '(' || next == '[' || next == '.') {
                return true;
            }
        }
        return lowerPath.contains("/" + packageName + "/")
                || lowerPath.contains("/" + packageName + "_")
                || lowerPath.contains("/" + packageName + "-")
                || lowerPath.contains("/" + packageName + ".");
    }

    private static long parseLongQuiet(String value, long fallback) {
        try { return Long.parseLong(value == null ? "" : value.trim()); } catch (Throwable ignored) { return fallback; }
    }
}
