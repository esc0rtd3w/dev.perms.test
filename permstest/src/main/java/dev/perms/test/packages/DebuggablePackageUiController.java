package dev.perms.test.packages;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Owns the Packages-tab UI wiring for creating a debuggable package from the
 * currently selected package file. Heavy rebuild work stays in the existing
 * package-debug pipeline; this controller only coordinates UI state and dispatch.
 */
public final class DebuggablePackageUiController {
    public interface Host {
        ActivityMainBinding getBinding();
        Uri getPickedPackageUri();
        String getPickedPackageLabel();
        String queryDisplayName(Uri uri);
        void setJobStatus(boolean running, String status);
        void restoreJobStatus();
        void executeDebugPackageWork(Runnable action);
        void runCreateDebuggablePackage(Uri sourceUri, String sourceLabel, String outputPath, boolean useApktool);
        void appendOutput(String text);
    }

    private final Context context;
    private final Host host;

    public DebuggablePackageUiController(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    public void setup() {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabPackages == null) return;
            if (binding.tabPackages.btnDebuggableUsePickedApk != null) {
                binding.tabPackages.btnDebuggableUsePickedApk.setOnClickListener(v -> usePickedPackageForDebuggablePatch());
            }
            if (binding.tabPackages.btnCreateDebuggableApk != null) {
                binding.tabPackages.btnCreateDebuggableApk.setOnClickListener(v -> createDebuggablePackageFromPickedFile());
            }
            if (host != null) host.restoreJobStatus();
        } catch (Throwable ignored) {
        }
    }

    private void usePickedPackageForDebuggablePatch() {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabPackages == null || host == null) return;
            Uri pickedUri = host.getPickedPackageUri();
            String pickedLabel = host.getPickedPackageLabel();
            if (pickedUri == null) {
                toast("Pick a package file first.");
                return;
            }
            String label = TextUtils.isEmpty(pickedLabel) ? host.queryDisplayName(pickedUri) : pickedLabel;
            if (TextUtils.isEmpty(label)) label = "package.apk";
            binding.tabPackages.edtDebuggableSourceApk.setText(label);
            if (binding.tabPackages.edtDebuggableOutputApk.getText() == null
                    || TextUtils.isEmpty(binding.tabPackages.edtDebuggableOutputApk.getText().toString().trim())) {
                binding.tabPackages.edtDebuggableOutputApk.setText(ApkDebugToolHelper.defaultOutputPath(label));
            }
            if (binding.tabPackages.txtDebuggableApkStatus != null) {
                binding.tabPackages.txtDebuggableApkStatus.setText("Ready to rebuild the selected APK as debuggable.");
            }
        } catch (Throwable t) {
            appendOutput("[APK Debug] Failed to load picked file: " + t + "\n");
        }
    }

    private void createDebuggablePackageFromPickedFile() {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabPackages == null || host == null) return;
            Uri pickedUri = host.getPickedPackageUri();
            String pickedLabel = host.getPickedPackageLabel();
            if (pickedUri == null) {
                toast("Pick a package file first.");
                return;
            }
            String outputPath = "";
            try {
                if (binding.tabPackages.edtDebuggableOutputApk.getText() != null) {
                    outputPath = binding.tabPackages.edtDebuggableOutputApk.getText().toString().trim();
                }
            } catch (Throwable ignored) {
            }
            if (TextUtils.isEmpty(outputPath)) {
                outputPath = ApkDebugToolHelper.defaultOutputPath(TextUtils.isEmpty(pickedLabel) ? "package.apk" : pickedLabel);
                binding.tabPackages.edtDebuggableOutputApk.setText(outputPath);
            }
            final String finalOutputPath = outputPath;
            final boolean useApktool = binding.tabPackages.chkDebuggableUseApktool != null
                    && binding.tabPackages.chkDebuggableUseApktool.isChecked();
            final String sourceLabel = TextUtils.isEmpty(pickedLabel) ? host.queryDisplayName(pickedUri) : pickedLabel;
            if (binding.tabPackages.txtDebuggableApkStatus != null) {
                binding.tabPackages.txtDebuggableApkStatus.setText("Preparing APK patch tools...");
            }
            appendOutput("[APK Debug] Preparing debuggable rebuild for "
                    + (TextUtils.isEmpty(sourceLabel) ? "package.apk" : sourceLabel) + "\n");
            host.setJobStatus(true, "Preparing debuggable rebuild for "
                    + (TextUtils.isEmpty(sourceLabel) ? "package.apk" : sourceLabel) + "...");
            host.executeDebugPackageWork(() -> {
                try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
                host.runCreateDebuggablePackage(pickedUri, sourceLabel, finalOutputPath, useApktool);
            });
        } catch (Throwable t) {
            appendOutput("[APK Debug] Failed to start debuggable rebuild: " + t + "\n");
        }
    }

    private ActivityMainBinding binding() {
        try {
            return host == null ? null : host.getBinding();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void appendOutput(String text) {
        try {
            if (host != null) host.appendOutput(text);
        } catch (Throwable ignored) {
        }
    }

    private void toast(String message) {
        try {
            if (context != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
