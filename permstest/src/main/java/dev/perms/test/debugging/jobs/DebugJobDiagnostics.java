package dev.perms.test.debugging.jobs;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small diagnostics helpers for long-running Debugging foreground jobs. */
final class DebugJobDiagnostics {
    private DebugJobDiagnostics() {
    }

    static String timestamp() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (Throwable ignored) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    static String elapsed(long startMs) {
        long ms = Math.max(0L, System.currentTimeMillis() - startMs);
        if (ms < 1000L) return ms + " ms";
        long sec = ms / 1000L;
        long min = sec / 60L;
        sec %= 60L;
        if (min <= 0L) return sec + " s";
        return min + " min " + sec + " s";
    }

    static void append(File file, String line) {
        if (file == null || TextUtils.isEmpty(line)) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            String text = line.endsWith("\n") ? line : (line + "\n");
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((timestamp() + " " + text).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    static String memoryLine(String label) {
        try {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            StringBuilder sb = new StringBuilder();
            sb.append("[Debugging] memory ").append(TextUtils.isEmpty(label) ? "snapshot" : label);
            sb.append(": appUsed=").append(mb(used)).append("MB");
            sb.append(" appTotal=").append(mb(rt.totalMemory())).append("MB");
            sb.append(" appMax=").append(mb(rt.maxMemory())).append("MB");
            long memAvailable = procMeminfoKb("MemAvailable:");
            long swapFree = procMeminfoKb("SwapFree:");
            if (memAvailable >= 0) sb.append(" memAvailable=").append(memAvailable / 1024L).append("MB");
            if (swapFree >= 0) sb.append(" swapFree=").append(swapFree / 1024L).append("MB");
            sb.append('\n');
            return sb.toString();
        } catch (Throwable t) {
            return "[Debugging] memory " + (TextUtils.isEmpty(label) ? "snapshot" : label) + ": unavailable\n";
        }
    }

    private static long mb(long bytes) {
        return Math.max(0L, bytes / (1024L * 1024L));
    }

    private static long procMeminfoKb(String key) {
        if (TextUtils.isEmpty(key)) return -1L;
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith(key)) continue;
                String rest = line.substring(key.length()).trim();
                String[] parts = rest.split("\\s+");
                if (parts.length > 0) return Long.parseLong(parts[0]);
            }
        } catch (Throwable ignored) {
        }
        return -1L;
    }
}
