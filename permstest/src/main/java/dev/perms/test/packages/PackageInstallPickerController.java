package dev.perms.test.packages;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.databinding.ActivityMainBinding;

public final class PackageInstallPickerController {
    public interface Host {
        ActivityMainBinding getBinding();
        String queryDisplayName(Uri uri);
        void setOpenedFromFileHandler(boolean opened);
        void setPickedApk(Uri uri, String label);
        Uri getPickedApkUri();
        String getPickedApkLabel();
        boolean wasOpenedFromFileHandler();
        void installPickedPackageFile(Uri uri, String label, boolean fromFileOpen);
        void appendOutput(String text);
    }

    private final AppCompatActivity activity;
    private final Host host;
    private ActivityResultLauncher<Intent> pickApkLauncher;

    public PackageInstallPickerController(AppCompatActivity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    public void registerActivityResults() {
        if (pickApkLauncher != null) return;
        pickApkLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handlePickResult
        );
    }

    public void setup() {
        try {
            registerActivityResults();
            ActivityMainBinding binding = host == null ? null : host.getBinding();
            if (pickApkLauncher == null || binding == null || binding.tabPackages == null) return;

            if (binding.tabPackages.btnPickApk != null) {
                binding.tabPackages.btnPickApk.setOnClickListener(v -> launchPicker());
            }

            if (binding.tabPackages.btnInstallPickedApk != null) {
                binding.tabPackages.btnInstallPickedApk.setOnClickListener(v -> installPickedFile());
            }
        } catch (Throwable ignored) {
        }
    }

    private void launchPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/vnd.android.package-archive",
                    "application/octet-stream",
                    "application/zip"
            });
            pickApkLauncher.launch(intent);
        } catch (Throwable t) {
            try { Toast.makeText(activity, "Picker failed: " + t.getMessage(), Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
        }
    }

    private void installPickedFile() {
        try {
            if (host == null || host.getPickedApkUri() == null) {
                Toast.makeText(activity, "Pick a file first", Toast.LENGTH_SHORT).show();
                return;
            }
            host.installPickedPackageFile(host.getPickedApkUri(), host.getPickedApkLabel(), host.wasOpenedFromFileHandler());
        } catch (Throwable t) {
            try { Toast.makeText(activity, "Install failed: " + t.getMessage(), Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
        }
    }

    private void handlePickResult(ActivityResult result) {
        try {
            if (result == null) return;
            if (result.getResultCode() != AppCompatActivity.RESULT_OK) return;
            Intent data = result.getData();
            if (data == null) return;
            Uri uri = data.getData();
            if (uri == null) return;

            host.setOpenedFromFileHandler(false);

            String label = host.queryDisplayName(uri);
            if (label == null || label.trim().isEmpty()) label = uri.toString();
            host.setPickedApk(uri, label);

            try {
                final int takeFlags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (takeFlags != 0) activity.getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (Throwable ignored) {
            }

            ActivityMainBinding binding = host.getBinding();
            if (binding != null && binding.tabPackages != null && binding.tabPackages.edtApkToInstall != null) {
                binding.tabPackages.edtApkToInstall.setText(label);
            }
            try {
                if (binding != null && binding.tabPackages != null && binding.tabPackages.edtDebuggableSourceApk != null) {
                    binding.tabPackages.edtDebuggableSourceApk.setText(label == null ? "" : label);
                }
                if (binding != null && binding.tabPackages != null && binding.tabPackages.edtDebuggableOutputApk != null) {
                    binding.tabPackages.edtDebuggableOutputApk.setText(ApkDebugToolHelper.defaultOutputPath(label));
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            if (host != null) {
                host.appendOutput("[!] APK picker failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            }
        }
    }
}
