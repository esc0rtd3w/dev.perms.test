package dev.perms.test.files;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

import dev.perms.test.ShizukuCompat;
import rikka.shizuku.Shizuku;

/** Runs Files-tab Shizuku shell captures outside MainActivity. */
public final class FilesShizukuCommandRunner {
    public interface Callback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    private FilesShizukuCommandRunner() {
    }

    public static boolean canUseShizuku() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void run(Activity activity, ExecutorService executor, String command, Callback callback) {
        if (TextUtils.isEmpty(command)) {
            finish(activity, callback, -1, "", "Command is empty");
            return;
        }
        if (executor == null) {
            finish(activity, callback, -1, "", "Executor is not available");
            return;
        }

        final String trimmed = command.trim();
        executor.execute(() -> {
            try {
                Process p = ShizukuCompat.newProcess(new String[]{"sh", "-c", trimmed}, null, null);
                String out = readAll(p.getInputStream());
                String err = readAll(p.getErrorStream());
                int code = p.waitFor();
                finish(activity, callback, code, out == null ? "" : out, err == null ? "" : err);
            } catch (Throwable t) {
                finish(activity, callback, -1, "", t.toString());
            }
        });
    }

    private static void finish(Activity activity, Callback callback, int exitCode, String stdout, String stderr) {
        if (callback == null) return;
        Runnable r = () -> callback.onComplete(exitCode, stdout == null ? "" : stdout, stderr == null ? "" : stderr);
        if (activity != null) {
            activity.runOnUiThread(r);
        } else {
            r.run();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        byte[] data = new byte[8192];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n = in.read(data)) != -1) {
            sb.append(new String(data, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
