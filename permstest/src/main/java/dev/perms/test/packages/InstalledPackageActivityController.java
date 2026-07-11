package dev.perms.test.packages;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.shell.ShellBinaryAssets;

/**
 * Activity-side owner for installed-package extraction and installed-app debug rebuild wiring.
 *
 * <p>MainActivity supplies only shared app services through Host. This package-owned bridge keeps
 * installed-package action/extractor lifecycle beside the package feature code.</p>
 */
public final class InstalledPackageActivityController {
    public interface Host {
        Context getContext();
        ActivityMainBinding getBinding();
        ExecutorService getExecutor();
        boolean isSafePackageName(String packageName);
        ToolResult runShellCommandCaptureSync(String command);
        File getExternalFilesDir(String type);
        void clearDebuggablePackageLog();
        void appendDebuggablePackageLog(String text);
        void setDebuggablePackageJobStatus(boolean running, String status);
        void runCreateDebuggablePackage(Uri sourceUri, String sourceLabel, String outputPath, boolean useApktool);
        void deleteTreeQuietly(File file);
        void appendOutput(String text);
        void runOnUiThread(Runnable action);
    }

    public static final class ToolResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ToolResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private final Host host;
    private InstalledPackageActionController actionController;
    private InstalledPackageExtractor extractor;

    public InstalledPackageActivityController(Host host) {
        this.host = host;
    }

    public void showMakeDebugPackageDialog(String packageName, String label) {
        getActionController().showMakeDebugPackageDialog(packageName, label);
    }

    public void createDebuggablePackageFromInstalledApp(String packageName, String label) {
        getActionController().createDebuggablePackageFromInstalledApp(packageName, label);
    }

    public void extractInstalledPackageToPublicFolder(String packageName, String displayName) {
        getActionController().extractInstalledPackageToPublicFolder(packageName, displayName);
    }

    public InstalledPackageExtractor.ExtractedInstalledPackage extractForPublicExport(String packageName, String displayName) throws IOException {
        return getExtractor().extractForPublicExport(packageName, displayName, new File(ShellBinaryAssets.PUBLIC_EXTRACTED_APKS_DIR));
    }

    public InstalledPackageExtractor.ExtractedInstalledPackage extractForDebug(String packageName, String displayName) throws IOException {
        File root = host == null ? null : host.getExternalFilesDir("apk_debug_installed");
        return getExtractor().extractForDebug(packageName, displayName, root);
    }

    private InstalledPackageActionController getActionController() {
        if (actionController == null) {
            Context context = host == null ? null : host.getContext();
            actionController = new InstalledPackageActionController(context, new InstalledPackageActionController.Host() {
                @Override
                public boolean isSafePackageName(String packageName) {
                    return host != null && host.isSafePackageName(packageName);
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void executePackageWork(Runnable action) {
                    ExecutorService executor = host == null ? null : host.getExecutor();
                    if (executor != null && action != null) executor.execute(action);
                }

                @Override
                public void runOnUiThread(Runnable action) {
                    if (host != null) host.runOnUiThread(action);
                }

                @Override
                public void clearDebuggablePackageLog() {
                    if (host != null) host.clearDebuggablePackageLog();
                }

                @Override
                public void appendDebuggablePackageLog(String text) {
                    if (host != null) host.appendDebuggablePackageLog(text);
                }

                @Override
                public void setDebuggablePackageJobStatus(boolean running, String status) {
                    if (host != null) host.setDebuggablePackageJobStatus(running, status);
                }

                @Override
                public boolean isDebuggableUseApktoolSelected() {
                    ActivityMainBinding binding = host == null ? null : host.getBinding();
                    try {
                        return binding != null
                                && binding.tabPackages != null
                                && binding.tabPackages.chkDebuggableUseApktool != null
                                && binding.tabPackages.chkDebuggableUseApktool.isChecked();
                    } catch (Throwable ignored) {
                        return false;
                    }
                }

                @Override
                public void setDebuggablePackageInputs(String sourceLabel, String outputPath, String status) {
                    InstalledPackageActivityController.this.setDebuggablePackageInputs(sourceLabel, outputPath, status);
                }

                @Override
                public void runCreateDebuggablePackage(Uri sourceUri, String sourceLabel, String outputPath, boolean useApktool) {
                    if (host != null) host.runCreateDebuggablePackage(sourceUri, sourceLabel, outputPath, useApktool);
                }

                @Override
                public InstalledPackageExtractor.ExtractedInstalledPackage extractForPublicExport(String packageName, String displayName) throws IOException {
                    return InstalledPackageActivityController.this.extractForPublicExport(packageName, displayName);
                }

                @Override
                public InstalledPackageExtractor.ExtractedInstalledPackage extractForDebug(String packageName, String displayName) throws IOException {
                    return InstalledPackageActivityController.this.extractForDebug(packageName, displayName);
                }

                @Override
                public void deleteTreeQuietly(File file) {
                    if (host != null) host.deleteTreeQuietly(file);
                }
            });
        }
        return actionController;
    }

    private InstalledPackageExtractor getExtractor() {
        if (extractor == null) {
            extractor = new InstalledPackageExtractor(command -> {
                ToolResult result = host == null ? null : host.runShellCommandCaptureSync(command);
                if (result == null) return new InstalledPackageExtractor.CommandResult(1, "", "Command failed.");
                return new InstalledPackageExtractor.CommandResult(result.exitCode, result.stdout, result.stderr);
            });
        }
        return extractor;
    }

    private void setDebuggablePackageInputs(String sourceLabel, String outputPath, String status) {
        try {
            ActivityMainBinding binding = host == null ? null : host.getBinding();
            if (binding == null || binding.tabPackages == null) return;
            if (binding.tabPackages.edtDebuggableSourceApk != null) {
                binding.tabPackages.edtDebuggableSourceApk.setText(sourceLabel);
            }
            if (binding.tabPackages.edtDebuggableOutputApk != null) {
                binding.tabPackages.edtDebuggableOutputApk.setText(outputPath);
            }
            if (binding.tabPackages.txtDebuggableApkStatus != null) {
                binding.tabPackages.txtDebuggableApkStatus.setText(status);
            }
        } catch (Throwable ignored) {
        }
    }
}
