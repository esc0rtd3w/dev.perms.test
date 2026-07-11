package dev.perms.test.update;

import java.io.File;

/**
 * Runs PermsTest self-update APK installs through the currently selected shell backend.
 */
public final class PermsTestSilentUpdateInstaller implements PermsTestUpdateController.SilentInstaller {
    public interface Host {
        boolean isReadyAndGranted();
        void refreshStatus();
        void appendOutput(String text);
        void runInstallCommand(String command, ResultCallback callback);
    }

    public interface ResultCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    private final Host host;

    public PermsTestSilentUpdateInstaller(Host host) {
        this.host = host;
    }

    @Override
    public void install(File apk,
                        String filename,
                        boolean allowDowngrade,
                        PermsTestUpdateController.SilentInstallCallback callback) {
        try {
            if (apk == null || !apk.isFile() || apk.length() <= 0L) {
                append("[update] Silent install failed: downloaded APK is missing.\n");
                complete(callback, false);
                return;
            }
            if (host == null || !host.isReadyAndGranted()) {
                if (host != null) host.refreshStatus();
                append("[update] Silent install requires a ready Shizuku or LADB backend.\n");
                complete(callback, false);
                return;
            }

            String flags = "-r" + (allowDowngrade ? " -d" : "");
            String command = "pm install " + flags + " " + shellQuote(apk.getAbsolutePath());
            append("[update] Silent install started: " + displayName(apk, filename) + "\n");
            host.runInstallCommand(command, (exitCode, stdout, stderr) -> {
                boolean ok = exitCode == 0;
                append(ok
                        ? "[update] Silent install complete.\n"
                        : "[update] Silent install failed with exit " + exitCode + ".\n");
                complete(callback, ok);
            });
        } catch (Throwable t) {
            append("[update] Silent install failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            complete(callback, false);
        }
    }

    private void append(String text) {
        try {
            if (host != null) host.appendOutput(text);
        } catch (Throwable ignored) {
        }
    }

    private static void complete(PermsTestUpdateController.SilentInstallCallback callback, boolean success) {
        try {
            if (callback != null) callback.onComplete(success);
        } catch (Throwable ignored) {
        }
    }

    private static String displayName(File apk, String filename) {
        if (filename != null && !filename.trim().isEmpty()) return filename.trim();
        return apk == null ? "update.apk" : apk.getName();
    }

    private static String shellQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
