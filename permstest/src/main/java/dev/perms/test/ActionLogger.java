package dev.perms.test;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight append-only action logger (opt-in).
 * Writes to the app's external files dir when available (no storage permission required).
 */
public final class ActionLogger {

    private static final long MAX_BYTES_BEFORE_ROTATE = 2L * 1024L * 1024L; // 2 MB
    private static final String FILE_NAME = "perms_test_actions.txt";
    private static final String FILE_NAME_OLD = "perms_test_actions_old.txt";

    private final Context appCtx;
    private final ExecutorService writer;
    private final SimpleDateFormat fmt;
    private volatile boolean enabled;

    public ActionLogger(Context ctx, boolean enabled) {
        this.appCtx = ctx.getApplicationContext();
        this.enabled = enabled;
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ActionLogger");
            t.setDaemon(true);
            return t;
        });
        this.fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void log(String action) {
        if (!enabled) return;
        if (action == null) return;

        final String trimmed = action.trim();
        if (trimmed.isEmpty()) return;

        final String line = fmt.format(new Date()) + "  " + trimmed;

        writer.execute(() -> {
            try {
                File f = resolveLogFile(appCtx);
                rotateIfNeeded(f);
                try (FileWriter fw = new FileWriter(f, true)) {
                    fw.append(line).append('\n');
                }
            } catch (Throwable ignored) {
                // never let logging break app flows
            }
        });
    }

    private static File resolveLogFile(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) dir = ctx.getFilesDir();
        return new File(dir, FILE_NAME);
    }

    private static void rotateIfNeeded(File f) {
        try {
            if (f.exists() && f.length() > MAX_BYTES_BEFORE_ROTATE) {
                File dir = f.getParentFile();
                if (dir == null) return;

                File old = new File(dir, FILE_NAME_OLD);
                // best-effort replace
                //noinspection ResultOfMethodCallIgnored
                if (old.exists()) old.delete();
                //noinspection ResultOfMethodCallIgnored
                f.renameTo(old);
            }
        } catch (Throwable ignored) {
        }
    }
}
