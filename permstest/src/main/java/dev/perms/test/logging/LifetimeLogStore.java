package dev.perms.test.logging;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Simple, persistent "lifetime" action log.
 *
 * - Stored under /sdcard/dev.perms.test/logs/lifetime when shared storage is available
 * - Falls back to the app-private external files dir if public storage is unavailable
 * - Appends asynchronously to avoid blocking UI
 * - Rotates when it grows too large
 */
public final class LifetimeLogStore {
    private static final String LOG_DIR = "logs";
    private static final String PUBLIC_LIFETIME_ROOT = "dev.perms.test/logs/lifetime";
    private static final String LOG_FILE = "perms_test_actions.txt";
    private static final long MAX_BYTES = 2L * 1024L * 1024L; // 2MB

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LifetimeLogStore");
        t.setDaemon(true);
        return t;
    });

    private LifetimeLogStore() {}

    public static File getLogFile(Context ctx) {
        File publicFile = getPublicLogFile();
        if (ensureDirectory(publicFile.getParentFile())) {
            migrateLegacyPrivateLogIfNeeded(ctx, publicFile);
            return publicFile;
        }
        return getPrivateLogFile(ctx);
    }

    public static File getPublicLogDirectory() {
        File root = Environment.getExternalStorageDirectory();
        return new File(root, PUBLIC_LIFETIME_ROOT);
    }

    public static File getPublicLogFile() {
        return new File(getPublicLogDirectory(), LOG_FILE);
    }

    public static File getPrivateLogFile(Context ctx) {
        File base = ctx == null ? null : ctx.getExternalFilesDir(null);
        if (base == null && ctx != null) base = ctx.getFilesDir();
        File dir = base == null ? new File(LOG_DIR) : new File(base, LOG_DIR);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, LOG_FILE);
    }

    public static void appendAsync(Context ctx, String tag, String msg) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        final String safeTag = tag == null ? "" : tag.trim();
        final String safeMsg = msg == null ? "" : msg;
        if (TextUtils.isEmpty(safeMsg)) return;

        IO.execute(() -> appendInternal(app, safeTag, safeMsg));
    }

    public static void addSessionMarkerAsync(Context ctx, String title) {
        String t = TextUtils.isEmpty(title) ? "session" : title.trim();
        appendAsync(ctx, "marker", "----- " + t + " -----\n");
    }

    public static void clearAsync(Context ctx) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            try {
                deleteIfPresent(getPublicLogFile());
                deleteIfPresent(getPrivateLogFile(app));
                File f = getLogFile(app);
                // recreate empty file for consistency
                //noinspection ResultOfMethodCallIgnored
                new FileOutputStream(f, false).close();
            } catch (Throwable ignored) {
            }
        });
    }

    /** Waits briefly for previously queued lifetime-log writes to reach disk. */
    public static boolean flushPending(long timeoutMs) {
        if (Thread.currentThread().getName().startsWith("LifetimeLogStore")) return true;
        try {
            Future<?> marker = IO.submit(() -> { });
            marker.get(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Copies the lifetime log to a timestamped file and returns the destination.
     * Returns null on failure.
     */
    public static File exportCopy(Context ctx) {
        if (ctx == null) return null;
        try {
            flushPending(1000L);
            File src = getLogFile(ctx);
            if (!src.exists()) return null;

            File dir = src.getParentFile();
            if (dir == null || !dir.isDirectory()) dir = getPublicLogDirectory();
            if (!ensureDirectory(dir)) {
                File base = ctx.getExternalFilesDir(null);
                if (base == null) base = ctx.getFilesDir();
                dir = new File(base, LOG_DIR);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File dst = new File(dir, "perms_test_actions_export_" + ts + ".txt");
            copyFile(src, dst);
            return dst;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Reads up to maxBytes from the end of the log for display. If the log is larger than maxBytes,
     * only the tail is returned with a short header.
     */
    public static String readForDisplay(Context ctx, int maxBytes) {
        if (ctx == null) return "";
        try {
            File f = getLogFile(ctx);
            if (!f.exists() || f.length() == 0) return "";

            long len = f.length();
            int want = Math.max(1, maxBytes);
            if (len <= want) {
                return readAllBytes(f);
            }

            // tail read
            try (FileInputStream fis = new FileInputStream(f);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                long skip = len - want;
                while (skip > 0) {
                    long s = bis.skip(skip);
                    if (s <= 0) break;
                    skip -= s;
                }
                byte[] buf = new byte[want];
                int r = bis.read(buf);
                if (r <= 0) return "";
                String tail = new String(buf, 0, r, StandardCharsets.UTF_8);
                String header = "[i] Showing last " + want + " bytes of lifetime log (file is " + len + " bytes).\n\n";
                return header + tail;
            }
        } catch (Throwable t) {
            return "[!] Failed to read lifetime log: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n";
        }
    }

    private static void appendInternal(Context ctx, String tag, String msg) {
        File primary = null;
        try {
            primary = getLogFile(ctx);
            appendToFile(primary, tag, msg);
        } catch (Throwable firstFailure) {
            try {
                File fallback = getPrivateLogFile(ctx);
                if (primary == null || !fallback.getAbsolutePath().equals(primary.getAbsolutePath())) {
                    appendToFile(fallback, tag, msg);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void appendToFile(File f, String tag, String msg) throws Exception {
        if (f == null) return;
        File parent = f.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        rotateIfNeeded(f);

        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String prefix = "[" + ts + "]";
        if (!TextUtils.isEmpty(tag)) prefix += " [" + tag + "]";
        prefix += " ";

        // Prefix each line for readability.
        String normalized = msg.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);

        try (FileOutputStream fos = new FileOutputStream(f, true);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (i == lines.length - 1 && line.isEmpty()) continue; // ignore trailing blank
                String out = prefix + line + "\n";
                bos.write(out.getBytes(StandardCharsets.UTF_8));
            }
            bos.flush();
        }
    }

    private static boolean ensureDirectory(File dir) {
        try {
            if (dir == null) return false;
            if (!dir.exists() && !dir.mkdirs()) return false;
            return dir.isDirectory();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void migrateLegacyPrivateLogIfNeeded(Context ctx, File publicFile) {
        try {
            if (ctx == null || publicFile == null) return;
            File legacy = getPrivateLogFile(ctx);
            if (legacy == null || !legacy.isFile() || legacy.length() == 0) return;
            if (publicFile.isFile() && publicFile.length() > 0) return;
            File parent = publicFile.getParentFile();
            if (!ensureDirectory(parent)) return;
            copyFile(legacy, publicFile);
        } catch (Throwable ignored) {
        }
    }

    private static void deleteIfPresent(File f) {
        try {
            if (f != null && f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void rotateIfNeeded(File f) {
        try {
            if (!f.exists()) return;
            if (f.length() <= MAX_BYTES) return;

            File dir = f.getParentFile();
            if (dir == null) return;

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File rotated = new File(dir, "perms_test_actions_" + ts + ".txt");
            //noinspection ResultOfMethodCallIgnored
            f.renameTo(rotated);
        } catch (Throwable ignored) {
        }
    }

    private static String readAllBytes(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = bis.read(buf)) > 0) {
                baos.write(buf, 0, r);
            }
            return baos.toString("UTF-8");
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (FileInputStream in = new FileInputStream(src);
             BufferedInputStream bis = new BufferedInputStream(in);
             FileOutputStream out = new FileOutputStream(dst);
             BufferedOutputStream bos = new BufferedOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = bis.read(buf)) > 0) {
                bos.write(buf, 0, r);
            }
            bos.flush();
        }
    }
}
