package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Builds local-file listings for the Network tab FTP client local pane.
 */
public final class NetworkFtpClientLocalListLoader {
    private NetworkFtpClientLocalListLoader() {}

    public static AndroidResult listWithAndroid(String requestedPath, String fallbackPath) {
        File dir = new File(FtpClientLocalEntry.normalizePath(requestedPath));
        if (!dir.isDirectory()) dir = new File(FtpClientLocalEntry.normalizePath(fallbackPath));
        File[] files = dir.listFiles();
        ArrayList<FtpClientLocalEntry> entries = new ArrayList<>();
        String warning = null;
        if (files != null) {
            ArrayList<File> sorted = new ArrayList<>();
            Collections.addAll(sorted, files);
            Collections.sort(sorted, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().toLowerCase(Locale.US).compareTo(b.getName().toLowerCase(Locale.US));
            });
            for (File f : sorted) {
                if (f == null) continue;
                boolean isDir = f.isDirectory();
                boolean isLink = false;
                try { isLink = !f.getCanonicalPath().equals(f.getAbsolutePath()); } catch (Throwable ignored) {}
                entries.add(new FtpClientLocalEntry(f.getName(), f.getAbsolutePath(), isDir ? 0L : f.length(), isDir, !isDir, isLink));
            }
        } else {
            warning = "Local list is empty or unavailable: " + dir.getAbsolutePath();
        }
        return new AndroidResult(FtpClientLocalEntry.normalizePath(dir.getAbsolutePath()), entries, warning);
    }

    public static String buildShizukuListCommand(String requestedPath) {
        String normalized = FtpClientLocalEntry.normalizePath(requestedPath);
        return "P=" + shellQuote(normalized) + "; "
                + "if [ -d \"$P\" ]; then printf '__PT_FILES_TARGET__|d\\n'; cd \"$P\" || exit 2; "
                + "stat -c '%F|%s|%Y|%n' ./* ./.[!.]* ./..?* 2>/dev/null || true; "
                + "elif [ -e \"$P\" ]; then printf '__PT_FILES_TARGET__|f\\n'; "
                + "stat -c '%F|%s|%Y|%n' \"$P\" 2>/dev/null; else exit 2; fi";
    }

    public static ArrayList<FtpClientLocalEntry> parseShizukuStatListing(String cwd, String out) {
        String normalizedCwd = FtpClientLocalEntry.normalizePath(cwd);
        ArrayList<FtpClientLocalEntry> entries = new ArrayList<>();
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
            String full = listedDirectory ? childPath(normalizedCwd, name) : normalizedCwd;
            entries.add(new FtpClientLocalEntry(name, full, size, isDir, !isDir, isLink));
        }
        Collections.sort(entries, (a, b) -> {
            if (a.directory != b.directory) return a.directory ? -1 : 1;
            return a.name.toLowerCase(Locale.US).compareTo(b.name.toLowerCase(Locale.US));
        });
        return entries;
    }

    private static String childPath(String parent, String name) {
        String p = FtpClientLocalEntry.normalizePath(parent);
        String child = name == null ? "" : name.trim();
        if (TextUtils.isEmpty(child)) return p;
        return "/".equals(p) ? "/" + child : p + "/" + child;
    }

    private static long parseLongQuiet(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String shellQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static final class AndroidResult {
        public final String directory;
        public final ArrayList<FtpClientLocalEntry> entries;
        public final String warning;

        AndroidResult(String directory, ArrayList<FtpClientLocalEntry> entries, String warning) {
            this.directory = FtpClientLocalEntry.normalizePath(directory);
            this.entries = entries == null ? new ArrayList<>() : entries;
            this.warning = warning;
        }
    }
}
