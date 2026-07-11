package dev.perms.test.packages;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Owns install-complete/install-failure dialogs and launch fallback handling.
 */
public final class PackageInstallDialogController {
    public interface DebugLogProvider {
        String getInstallDebugLog();
    }

    public interface CloseCallback {
        void closeAfterFileOpen();
    }

    public interface ShellFallback {
        void run(String command);
    }

    private final Activity activity;
    private final DebugLogProvider debugLogProvider;
    private final CloseCallback closeCallback;
    private final ShellFallback shellFallback;

    public PackageInstallDialogController(Activity activity,
                                          DebugLogProvider debugLogProvider,
                                          CloseCallback closeCallback,
                                          ShellFallback shellFallback) {
        this.activity = activity;
        this.debugLogProvider = debugLogProvider;
        this.closeCallback = closeCallback;
        this.shellFallback = shellFallback;
    }

    public void showInstallFailedDialog(String message) {
        try {
            final String debugLog = debugLogProvider == null ? "" : debugLogProvider.getInstallDebugLog();
            AlertDialog.Builder bld = new AlertDialog.Builder(activity)
                    .setTitle("Install failed")
                    .setMessage(message == null ? "Install failed" : message)
                    .setPositiveButton("OK", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                    });

            if (!TextUtils.isEmpty(debugLog)) {
                bld.setNeutralButton("Save log", (d, w) -> {
                    try { saveInstallDebugLog(debugLog); } catch (Throwable ignored) {}
                    try { d.dismiss(); } catch (Throwable ignored) {}
                });
            }

            bld.show();
        } catch (Throwable ignored) {
            try { Toast.makeText(activity, "Install failed", Toast.LENGTH_SHORT).show(); } catch (Throwable ignored2) {}
        }
    }

    public void showInstallDoneDialog(String pkg, String label, boolean finishOnDone) {
        try {
            String display = (label != null && !label.trim().isEmpty()) ? label : pkg;
            if (display == null || display.trim().isEmpty()) display = "Package";

            String msg = display;
            if (pkg != null && !pkg.trim().isEmpty() && (label == null || !label.contains(pkg))) {
                msg = display + "\n" + pkg;
            }

            final String pkgFinal = (pkg == null) ? "" : pkg.trim();
            final String msgFinal = msg;

            try {
                AlertDialog.Builder bld = new AlertDialog.Builder(activity)
                        .setTitle("Install complete")
                        .setMessage(msgFinal);

                if (!TextUtils.isEmpty(pkgFinal)) {
                    bld.setPositiveButton("Open", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        startInstalledPackage(pkgFinal);
                        if (finishOnDone) closeAfterFileOpen();
                    });
                    bld.setNegativeButton("Done", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        if (finishOnDone) closeAfterFileOpen();
                    });
                } else {
                    bld.setPositiveButton("Done", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        if (finishOnDone) closeAfterFileOpen();
                    });
                }

                bld.setOnCancelListener(d -> {
                    if (finishOnDone) closeAfterFileOpen();
                });
                bld.show();
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private void saveInstallDebugLog(String text) {
        try {
            File base = activity.getExternalFilesDir(null);
            if (base == null) {
                Toast.makeText(activity, "External files dir unavailable", Toast.LENGTH_SHORT).show();
                return;
            }
            File dir = new File(base, "debug");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File f = new File(dir, "install_debug_" + ts + ".txt");
            try (FileOutputStream fos = new FileOutputStream(f, false)) {
                fos.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            Toast.makeText(activity, "Saved: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            try { Toast.makeText(activity, "Save failed: " + t.getMessage(), Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
        }
    }

    private void startInstalledPackage(String packageName) {
        final String pkg = packageName == null ? "" : packageName.trim();
        if (TextUtils.isEmpty(pkg)) return;

        try {
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(launch);
                return;
            }
        } catch (Throwable ignored) {}

        new Thread(() -> {
            try {
                if (shellFallback != null) {
                    shellFallback.run("monkey -p " + shQuote(pkg) + " -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1");
                }
            } catch (Throwable ignored) {}
        }, "package-launch-fallback").start();
    }

    private void closeAfterFileOpen() {
        try {
            if (closeCallback != null) closeCallback.closeAfterFileOpen();
        } catch (Throwable ignored) {}
    }

    private static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
