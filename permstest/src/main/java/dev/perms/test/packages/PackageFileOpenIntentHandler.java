package dev.perms.test.packages;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Handles package archives opened through Android's file-open ACTION_VIEW flow.
 */
public final class PackageFileOpenIntentHandler {
    private PackageFileOpenIntentHandler() {}

    public interface Host {
        SharedPreferences getPreferences();
        String queryDisplayName(Uri uri);
        void setOpenedFromFileHandler(boolean opened);
        void setPickedApk(Uri uri, String label);
        void setPendingAutoInstall(Uri uri, String label);
        void tryAutoInstallPendingFile();
        void showPackagesTab();
        void appendOutput(String text);
    }

    public static boolean handle(Activity activity,
                                 ActivityMainBinding binding,
                                 Intent intent,
                                 String hideFileOpenUiKey,
                                 Host host) {
        try {
            if (activity == null || host == null || intent == null) return false;
            if (!Intent.ACTION_VIEW.equals(intent.getAction())) return false;

            Uri uri = intent.getData();
            if (uri == null) return false;

            host.setOpenedFromFileHandler(true);

            boolean hideUi = true;
            try {
                SharedPreferences prefs = host.getPreferences();
                if (prefs != null) hideUi = prefs.getBoolean(hideFileOpenUiKey, true);
            } catch (Throwable ignored) {
                hideUi = true;
            }

            String label = host.queryDisplayName(uri);
            if (label == null || label.trim().isEmpty()) label = uri.toString();
            host.setPickedApk(uri, label);

            try {
                final int takeFlags = intent.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (takeFlags != 0) activity.getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (Throwable ignored) {
            }

            updatePackageInstallFields(binding, label);

            if (!hideUi) {
                host.showPackagesTab();
            }

            host.setPendingAutoInstall(uri, label);
            host.tryAutoInstallPendingFile();

            try {
                Toast.makeText(activity, "Loaded: " + label, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable t) {
            host.appendOutput("[!] File open failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            return false;
        }
    }

    private static void updatePackageInstallFields(ActivityMainBinding binding, String label) {
        if (binding == null) return;
        try {
            if (binding.tabPackages.edtApkToInstall != null) {
                binding.tabPackages.edtApkToInstall.setText(label == null ? "" : label);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (binding.tabPackages.edtDebuggableSourceApk != null) {
                binding.tabPackages.edtDebuggableSourceApk.setText(label == null ? "" : label);
            }
            if (binding.tabPackages.edtDebuggableOutputApk != null) {
                binding.tabPackages.edtDebuggableOutputApk.setText(ApkDebugToolHelper.defaultOutputPath(label));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void showPackagesTab(ActivityMainBinding binding, Runnable afterShow) {
        try {
            if (binding != null && binding.tabLayout != null) {
                TabLayout.Tab tab = binding.tabLayout.getTabAt(2);
                if (tab != null) {
                    tab.select();
                    runAfterTabShow(binding, afterShow);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        if (afterShow != null) afterShow.run();
    }

    private static void runAfterTabShow(ActivityMainBinding binding, Runnable action) {
        if (action == null) return;
        try {
            if (binding != null && binding.getRoot() != null) {
                binding.getRoot().post(action);
                binding.getRoot().postDelayed(action, 250L);
                return;
            }
        } catch (Throwable ignored) {
        }
        action.run();
    }
}
