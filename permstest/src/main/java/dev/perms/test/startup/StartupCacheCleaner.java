package dev.perms.test.startup;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import dev.perms.test.ShizukuCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

public final class StartupCacheCleaner {
    private static final String PUBLIC_TMP_ROOT = "/data/local/tmp/dev.perms.test";
    private static final String[] PUBLIC_TMP_TRANSIENT_DIRS = new String[]{
            PUBLIC_TMP_ROOT + "/files",
            PUBLIC_TMP_ROOT + "/stage",
            PUBLIC_TMP_ROOT + "/imports",
            PUBLIC_TMP_ROOT + "/apkpatch",
            PUBLIC_TMP_ROOT + "/bin/stage"
    };
    private static volatile boolean cleanupAttempted;

    private final Context context;
    private final SharedPreferences prefs;
    private final String clearCacheKey;
    private final String importsDirName;
    private final Executor executor;
    private final Callback callback;

    public interface Callback {
        void onCleanupSummary(String message);
    }

    public StartupCacheCleaner(Context context,
                               SharedPreferences prefs,
                               String clearCacheKey,
                               String importsDirName,
                               Executor executor,
                               Callback callback) {
        this.context = context == null ? null : context.getApplicationContext();
        this.prefs = prefs;
        this.clearCacheKey = clearCacheKey;
        this.importsDirName = importsDirName;
        this.executor = executor;
        this.callback = callback;
    }

    public void cleanupOnStartupIfEnabled() {
        try {
            if (cleanupAttempted) return;
            cleanupAttempted = true;
            if (context == null || prefs == null || executor == null) return;
            if (!prefs.getBoolean(clearCacheKey, true)) return;
            executor.execute(() -> {
                CleanupStats stats = new CleanupStats();
                try { cleanupCacheRootContents(context.getCacheDir(), stats); } catch (Throwable ignored) {}
                try { cleanupCacheRootContents(context.getExternalCacheDir(), stats); } catch (Throwable ignored) {}
                try {
                    File[] roots = context.getExternalCacheDirs();
                    if (roots != null) {
                        for (File root : roots) {
                            try { cleanupCacheRootContents(root, stats); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
                try { cleanupManagedStartupScratch(stats); } catch (Throwable ignored) {}
                try { cleanupPublicTmpScratch(stats); } catch (Throwable ignored) {}

                if (stats.deletedFiles > 0 || stats.deletedDirs > 0 || stats.publicTmpCleaned) {
                    Callback cb = callback;
                    if (cb != null) {
                        cb.onCleanupSummary("[i] Startup cache cleanup removed "
                                + formatBytesShort(stats.deletedBytes) + " from "
                                + stats.deletedFiles + " files"
                                + (stats.publicTmpCleaned ? " and public temp staging" : "")
                                + "."
                                + (stats.failedDeletes > 0 ? " Some protected/stale entries were skipped." : "")
                                + "\n");
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void cleanupCacheRootContents(File root, CleanupStats stats) {
        try {
            if (root == null || stats == null) return;
            if (!root.exists() || !root.isDirectory()) return;
            File[] children = root.listFiles();
            if (children == null) return;
            for (File child : children) {
                deleteManagedCacheTree(child, stats);
            }
        } catch (Throwable ignored) {
        }
    }

    private void cleanupManagedStartupScratch(CleanupStats stats) {
        try {
            if (context == null) return;
            File root = context.getExternalFilesDir(null);
            if (root == null || !root.isDirectory()) return;
            String[] scratchDirs = new String[]{
                    importsDirName,
                    "apk_debug_installed",
                    "apk_debuggable_work",
                    "apk_patch_inputs",
                    "debugging_inputs",
                    "update_downloads",
                    "memory_dump_stage",
                    "memory_export_stage",
                    "memory_patch_stage",
                    "memory_payload_stage",
                    "bundled_bin_stage",
                    "bin_stage"
            };
            for (String name : scratchDirs) {
                if (TextUtils.isEmpty(name)) continue;
                deleteManagedCacheTree(new File(root, name), stats);
            }
        } catch (Throwable ignored) {
        }
    }


    private void cleanupPublicTmpScratch(CleanupStats stats) {
        try {
            if (stats == null || !isShizukuReadyAndGranted()) return;
            StringBuilder cmd = new StringBuilder();
            cmd.append("for d in");
            for (String dir : PUBLIC_TMP_TRANSIENT_DIRS) {
                cmd.append(' ').append(shQuote(dir));
            }
            cmd.append("; do ")
                    .append("[ -d \"$d\" ] || continue; ")
                    .append("find \"$d\" -mindepth 1 -maxdepth 1 -exec rm -rf {} + 2>/dev/null || true; ")
                    .append("done; ")
                    .append("exit 0");
            CmdResult result = runShizukuShellCapture(cmd.toString());
            if (result != null && result.exitCode == 0) {
                stats.publicTmpCleaned = true;
            } else if (result != null) {
                stats.failedDeletes++;
            }
        } catch (Throwable ignored) {
            try { if (stats != null) stats.failedDeletes++; } catch (Throwable ignored2) {}
        }
    }

    private static boolean isShizukuReadyAndGranted() {
        boolean binderAlive = false;
        try { binderAlive = Shizuku.pingBinder(); } catch (Throwable ignored) { binderAlive = false; }
        if (!binderAlive) return false;
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static CmdResult runShizukuShellCapture(String shCmd) {
        Process process = null;
        try {
            process = ShizukuCompat.newProcess(new String[]{"sh", "-c", shCmd}, null, null);
            String out = readProcessStream(process.getInputStream());
            String err = readProcessStream(process.getErrorStream());
            boolean completed = process.waitFor(12, TimeUnit.SECONDS);
            if (!completed) {
                try { process.destroy(); } catch (Throwable ignored) {}
                return new CmdResult(124, out, err);
            }
            return new CmdResult(process.exitValue(), out, err);
        } catch (Throwable t) {
            return new CmdResult(1, "", t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage()));
        } finally {
            try { if (process != null) process.destroy(); } catch (Throwable ignored) {}
        }
    }

    private static String readProcessStream(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String shQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static final class CmdResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        CmdResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private void deleteManagedCacheTree(File file, CleanupStats stats) {
        try {
            if (file == null || stats == null || !file.exists()) return;
            boolean wasDirectory = file.isDirectory();
            if (wasDirectory) {
                File[] kids = file.listFiles();
                if (kids != null) {
                    for (File kid : kids) deleteManagedCacheTree(kid, stats);
                }
            }
            long len = file.isFile() ? Math.max(0L, file.length()) : 0L;
            if (file.delete()) {
                if (len > 0L) stats.deletedBytes += len;
                if (wasDirectory) stats.deletedDirs++;
                else stats.deletedFiles++;
            } else if (file.exists()) {
                stats.failedDeletes++;
            }
        } catch (Throwable ignored) {
            try { if (stats != null) stats.failedDeletes++; } catch (Throwable ignored2) {}
        }
    }

    private static String formatBytesShort(long bytes) {
        try {
            if (bytes < 1024L) return bytes + " B";
            double value = bytes / 1024.0;
            String[] units = new String[]{"KB", "MB", "GB", "TB"};
            int unit = 0;
            while (value >= 1024.0 && unit < units.length - 1) {
                value /= 1024.0;
                unit++;
            }
            return String.format(Locale.US, value >= 100.0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
        } catch (Throwable ignored) {
            return bytes + " B";
        }
    }

    private static final class CleanupStats {
        long deletedBytes;
        int deletedFiles;
        int deletedDirs;
        int failedDeletes;
        boolean publicTmpCleaned;
    }
}
