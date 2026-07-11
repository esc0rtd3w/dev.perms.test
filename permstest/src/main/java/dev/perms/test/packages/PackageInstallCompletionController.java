package dev.perms.test.packages;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Toast;

/**
 * Owns the file-open package install completion UX.
 */
public final class PackageInstallCompletionController {
    public interface Host {
        SharedPreferences getPreferences();
        int getLastFileOpenInstallExit();
        String getLastFileOpenInstallOut();
        String getLastFileOpenInstallErr();
        void setLastInstallDebugLog(String log);
        PackageInstallUiProbe.InstallUiInfo probeInstallUiInfo(String path, String originalLabel);
        PackageInstallUiProbe.InstallUiInfo probeInstalledPackageFromNameHints(String... hints);
        PackageInstallUiProbe.InstallUiInfo refreshInstalledUiInfo(PackageInstallUiProbe.InstallUiInfo info);
        void showInstallFailedDialog(String message);
        void showInstallDoneDialog(String packageName, String label, boolean finishOnDone);
        void closeTaskAfterFileOpen();
        void cleanupManagedImportFile(String path);
    }

    private final Activity activity;
    private final String hideFileOpenUiKey;
    private final Host host;

    public PackageInstallCompletionController(Activity activity, String hideFileOpenUiKey, Host host) {
        this.activity = activity;
        this.hideFileOpenUiKey = hideFileOpenUiKey;
        this.host = host;
    }

    public void onInstallFinishedUi(int exit, String installedFromPath, String originalLabel, boolean fromFileOpen) {
        try {
            // Only show the file-open UX for installs launched via file-open handler.
            if (!fromFileOpen) return;

            SharedPreferences prefs = host == null ? null : host.getPreferences();
            boolean seamless = prefs == null || prefs.getBoolean(hideFileOpenUiKey, true);

            if (exit != 0) {
                String out = host == null ? "" : host.getLastFileOpenInstallOut();
                String err = host == null ? "" : host.getLastFileOpenInstallErr();
                if (host != null) {
                    host.setLastInstallDebugLog(PackageInstallResults.buildDebugLog(
                            installedFromPath,
                            originalLabel,
                            exit,
                            out,
                            err));
                    final String msg = buildInstallFailureMessage(
                            installedFromPath,
                            originalLabel,
                            host.getLastFileOpenInstallExit(),
                            out,
                            err);
                    postToUi(() -> host.showInstallFailedDialog(msg));
                }
                return;
            }

            if (host != null) host.setLastInstallDebugLog("");

            PackageInstallUiProbe.InstallUiInfo info = null;
            try { info = host == null ? null : host.probeInstallUiInfo(installedFromPath, originalLabel); } catch (Throwable ignored) {}
            if (info == null || TextUtils.isEmpty(info.packageName)) {
                try { info = host == null ? null : host.probeInstalledPackageFromNameHints(originalLabel, installedFromPath); } catch (Throwable ignored) {}
            }
            try { info = host == null ? info : host.refreshInstalledUiInfo(info); } catch (Throwable ignored) {}

            if (seamless) {
                postToUi(() -> {
                    try { Toast.makeText(activity, "Installed", Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
                    if (host != null) host.closeTaskAfterFileOpen();
                });
                return;
            }

            final String pkg = (info == null ? null : info.packageName);
            final String label = (info == null ? null : info.appLabel);
            if (host != null) postToUi(() -> host.showInstallDoneDialog(pkg, label, true));
        } catch (Throwable ignored) {
        } finally {
            try { if (host != null) host.cleanupManagedImportFile(installedFromPath); } catch (Throwable ignored) {}
        }
    }

    private void postToUi(Runnable action) {
        if (action == null) return;
        try {
            if (activity != null) {
                activity.runOnUiThread(action);
            } else {
                action.run();
            }
        } catch (Throwable ignored) {
        }
    }

    private static String buildInstallFailureMessage(String installedFromPath, String originalLabel, int exit, String out, String err) {
        try {
            String name = originalLabel;
            if (name == null || name.trim().isEmpty()) name = installedFromPath;
            if (name == null || name.trim().isEmpty()) name = "Package";

            StringBuilder sb = new StringBuilder();
            sb.append(name);
            sb.append("\n\nExit code: ").append(exit);

            String details = "";
            if (err != null && !err.trim().isEmpty()) details = err.trim();
            else if (out != null && !out.trim().isEmpty()) details = out.trim();

            if (!details.isEmpty()) {
                // Keep the dialog readable.
                if (details.length() > 1500) details = details.substring(0, 1500) + "…";
                sb.append("\n\n").append(details);
            } else {
                sb.append("\n\nSee output log for details.");
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "Install failed. See output log for details.";
        }
    }
}
