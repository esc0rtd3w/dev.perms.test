package dev.perms.test.packages;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;

import java.io.File;
import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Activity-side owner for Packages > Create Debuggable Package wiring.
 *
 * MainActivity supplies shared app services through Host while this package-owned
 * bridge keeps job status, UI dispatch, creation, and export adapters together
 * with the rest of the package feature code.
 */
public final class DebuggablePackageActivityController {
    public interface Host {
        Context getContext();
        ActivityMainBinding getBinding();
        SharedPreferences getPreferences();
        Handler getMainHandler();
        ExecutorService getExecutor();
        Uri getPickedPackageUri();
        String getPickedPackageLabel();
        String queryDisplayName(Uri uri);
        File copyUriToExternalDir(Uri uri, String subdir, String filename);
        ToolResult ensureBundledBinaryPublicForCurrentMode(String toolName);
        ToolResult runShellCommandCaptureSync(String command);
        File getExternalFilesDir(String type);
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
    private DebuggablePackageJobStatusController jobStatusController;
    private DebuggablePackageUiController uiController;
    private DebuggablePackageCreationController creationController;

    public DebuggablePackageActivityController(Host host) {
        this.host = host;
    }

    public void setup() {
        getUiController().setup();
    }

    public void clearLog() {
        getJobStatusController().clearLog();
    }

    public void appendLog(String msg) {
        getJobStatusController().appendLog(msg);
    }

    public void restoreLogIfNeeded() {
        getJobStatusController().restoreLogIfNeeded();
    }

    public void setStatus(boolean running, String status) {
        getJobStatusController().setStatus(running, status);
    }

    public void restoreStatus() {
        getJobStatusController().restoreStatus();
    }

    public void schedulePoll() {
        getJobStatusController().schedulePoll();
    }

    public void runCreateDebuggablePackage(Uri sourceUri, String sourceLabel, String outputPath, boolean useApktool) {
        getCreationController().runCreateDebuggablePackage(sourceUri, sourceLabel, outputPath, useApktool);
    }

    private DebuggablePackageJobStatusController getJobStatusController() {
        if (jobStatusController == null) {
            Context context = host == null ? null : host.getContext();
            SharedPreferences prefs = host == null ? null : host.getPreferences();
            Handler handler = host == null ? null : host.getMainHandler();
            jobStatusController = new DebuggablePackageJobStatusController(
                    context,
                    prefs,
                    handler,
                    () -> {
                        try {
                            ActivityMainBinding binding = host == null ? null : host.getBinding();
                            return binding != null && binding.tabPackages != null
                                    ? binding.tabPackages.txtDebuggableApkStatus
                                    : null;
                        } catch (Throwable ignored) {
                            return null;
                        }
                    },
                    text -> {
                        if (host != null) host.appendOutput(text);
                    });
        }
        return jobStatusController;
    }

    private DebuggablePackageUiController getUiController() {
        if (uiController == null) {
            uiController = new DebuggablePackageUiController(host == null ? null : host.getContext(), new DebuggablePackageUiController.Host() {
                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public Uri getPickedPackageUri() {
                    return host == null ? null : host.getPickedPackageUri();
                }

                @Override
                public String getPickedPackageLabel() {
                    return host == null ? null : host.getPickedPackageLabel();
                }

                @Override
                public String queryDisplayName(Uri uri) {
                    return host == null ? null : host.queryDisplayName(uri);
                }

                @Override
                public void setJobStatus(boolean running, String status) {
                    DebuggablePackageActivityController.this.setStatus(running, status);
                }

                @Override
                public void restoreJobStatus() {
                    DebuggablePackageActivityController.this.restoreStatus();
                }

                @Override
                public void executeDebugPackageWork(Runnable action) {
                    ExecutorService executor = host == null ? null : host.getExecutor();
                    if (executor != null && action != null) executor.execute(action);
                }

                @Override
                public void runCreateDebuggablePackage(Uri sourceUri, String sourceLabel, String outputPath, boolean useApktool) {
                    getCreationController().runCreateDebuggablePackage(sourceUri, sourceLabel, outputPath, useApktool);
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }
            });
        }
        return uiController;
    }

    private DebuggablePackageCreationController getCreationController() {
        if (creationController == null) {
            creationController = new DebuggablePackageCreationController(host == null ? null : host.getContext(), new DebuggablePackageCreationController.Host() {
                @Override
                public File copyUriToExternalDir(Uri uri, String subdir, String filename) {
                    return host == null ? null : host.copyUriToExternalDir(uri, subdir, filename);
                }

                @Override
                public DebuggablePackageCreationController.ToolResult ensureBundledBinaryPublicForCurrentMode(String toolName) {
                    ToolResult result = host == null ? null : host.ensureBundledBinaryPublicForCurrentMode(toolName);
                    if (result == null) return null;
                    return new DebuggablePackageCreationController.ToolResult(result.exitCode, result.stdout, result.stderr);
                }

                @Override
                public DebuggablePackageCreationController.ToolResult runShellCommandCaptureSync(String command) {
                    ToolResult result = host == null ? null : host.runShellCommandCaptureSync(command);
                    if (result == null) return null;
                    return new DebuggablePackageCreationController.ToolResult(result.exitCode, result.stdout, result.stderr);
                }

                @Override
                public DebuggablePackageCreationController.ExportResult exportSignedPackage(File signedPackage, String outputPath) {
                    DebuggablePackageExporter.Result result = DebuggablePackageExporter.export(signedPackage, outputPath, command -> {
                        ToolResult shell = host == null ? null : host.runShellCommandCaptureSync(command);
                        if (shell == null) return null;
                        return new DebuggablePackageExporter.Result(shell.exitCode, shell.stdout, shell.stderr);
                    });
                    if (result == null) return null;
                    return new DebuggablePackageCreationController.ExportResult(result.exitCode, result.stdout, result.stderr);
                }

                @Override
                public File getExternalFilesDir(String type) {
                    return host == null ? null : host.getExternalFilesDir(type);
                }

                @Override
                public void deleteTreeQuietly(File file) {
                    if (host != null) host.deleteTreeQuietly(file);
                }

                @Override
                public void setJobStatus(boolean running, String status) {
                    DebuggablePackageActivityController.this.setStatus(running, status);
                }

                @Override
                public void setVisibleStatus(String status) {
                    if (host != null) {
                        host.runOnUiThread(() -> {
                            try {
                                ActivityMainBinding binding = host.getBinding();
                                if (binding != null && binding.tabPackages != null && binding.tabPackages.txtDebuggableApkStatus != null) {
                                    binding.tabPackages.txtDebuggableApkStatus.setText(status);
                                }
                            } catch (Throwable ignored) {
                            }
                        });
                    }
                }

                @Override
                public void restoreLogIfNeeded() {
                    if (host != null) host.runOnUiThread(DebuggablePackageActivityController.this::restoreLogIfNeeded);
                }

                @Override
                public void appendLog(String msg) {
                    DebuggablePackageActivityController.this.appendLog(msg);
                }
            });
        }
        return creationController;
    }
}
