package dev.perms.test.packages;

import android.app.Activity;
import android.net.Uri;
import android.widget.Toast;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Owns the currently picked package file and the pending file-open auto-install state.
 */
public final class PackageInstallSelectionController {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        boolean isBackendReadyAndGranted();
        void installPickedPackageFile(Uri uri, String label, boolean fromFileOpen);
    }

    private final Host host;
    private Uri pickedApkUri;
    private String pickedApkLabel;
    private Uri pendingAutoInstallUri;
    private String pendingAutoInstallLabel;
    private boolean openedFromFileHandler;

    public PackageInstallSelectionController(Host host) {
        this.host = host;
    }

    public void setOpenedFromFileHandler(boolean opened) {
        openedFromFileHandler = opened;
    }

    public boolean wasOpenedFromFileHandler() {
        return openedFromFileHandler;
    }

    public void setPickedApk(Uri uri, String label) {
        pickedApkUri = uri;
        pickedApkLabel = label;
    }

    public Uri getPickedApkUri() {
        return pickedApkUri;
    }

    public String getPickedApkLabel() {
        return pickedApkLabel;
    }

    public void setPendingAutoInstall(Uri uri, String label) {
        pendingAutoInstallUri = uri;
        pendingAutoInstallLabel = label;
    }

    public void clearSelectionUi() {
        try {
            pickedApkUri = null;
            pickedApkLabel = null;
            pendingAutoInstallUri = null;
            pendingAutoInstallLabel = null;
            openedFromFileHandler = false;
            ActivityMainBinding binding = host == null ? null : host.getBinding();
            if (binding != null && binding.tabPackages != null && binding.tabPackages.edtApkToInstall != null) {
                binding.tabPackages.edtApkToInstall.setText("");
            }
        } catch (Throwable ignored) {
        }
    }

    public void tryAutoInstallPendingFile() {
        try {
            if (pendingAutoInstallUri == null) return;
            boolean ready = host != null && host.isBackendReadyAndGranted();
            if (ready) {
                maybeAutoInstallPendingFile(true, true);
            } else {
                toast("File loaded. Connect Shizuku to auto-install.");
            }
        } catch (Throwable ignored) {
        }
    }

    public void maybeAutoInstallPendingFile(boolean binderAlive, boolean granted) {
        try {
            if (pendingAutoInstallUri == null) return;
            if (!binderAlive || !granted) return;

            final Uri uri = pendingAutoInstallUri;
            final String label = pendingAutoInstallLabel;

            pendingAutoInstallUri = null;
            pendingAutoInstallLabel = null;

            Activity activity = host == null ? null : host.getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                try { Toast.makeText(activity, "Auto installing…", Toast.LENGTH_SHORT).show(); } catch (Throwable ignored2) {}
                try {
                    if (host != null) host.installPickedPackageFile(uri, label, true);
                } catch (Throwable ignored3) {}
            });
        } catch (Throwable ignored) {
        }
    }

    private void toast(String message) {
        try {
            Activity activity = host == null ? null : host.getActivity();
            if (activity != null) Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
