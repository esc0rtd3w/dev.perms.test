package dev.perms.test.packages.editor;

import android.app.Activity;
import android.content.ContentResolver;
import android.net.Uri;

import java.io.File;
import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Activity-side owner for Packages > APK Editor wiring.
 *
 * MainActivity supplies shared app services through Host while this package-owned
 * bridge keeps APK Editor controller construction and smali-workspace handoff
 * wiring out of the activity.
 */
public final class ApkEditorActivityController {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        ExecutorService getExecutor();
        Uri getPickedPackageUri();
        String getPickedPackageLabel();
        String queryDisplayName(Uri uri);
        ContentResolver contentResolver();
        File getExternalFilesDir(String type);
        ToolResult ensureBundledTool(String toolName);
        ToolResult runShell(String command);
        void appendOutput(String text);
        void runOnUiThread(Runnable runnable);
        void openSmaliWorkspace(String apkInputPath,
                                String smaliInputDirPath,
                                String selectedSmaliFilePath,
                                String dexOutputPath,
                                String apkOutputPath,
                                String dexEntry);
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
    private ApkEditorController controller;

    public ApkEditorActivityController(Host host) {
        this.host = host;
    }

    public void bind() {
        try {
            Activity activity = host == null ? null : host.getActivity();
            ActivityMainBinding binding = host == null ? null : host.getBinding();
            ExecutorService executor = host == null ? null : host.getExecutor();
            if (activity == null || binding == null || binding.tabPackages == null || executor == null) return;
            controller = new ApkEditorController(activity, binding.tabPackages, executor, new ApkEditorController.Host() {
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
                public ContentResolver contentResolver() {
                    return host == null ? null : host.contentResolver();
                }

                @Override
                public File getExternalFilesDir(String type) {
                    return host == null ? null : host.getExternalFilesDir(type);
                }

                @Override
                public ApkEditorController.ToolResult ensureBundledTool(String toolName) {
                    ToolResult result = host == null ? null : host.ensureBundledTool(toolName);
                    if (result == null) return new ApkEditorController.ToolResult(1, "", "No tool result.");
                    return new ApkEditorController.ToolResult(result.exitCode, result.stdout, result.stderr);
                }

                @Override
                public ApkEditorController.ToolResult runShell(String command) {
                    ToolResult result = host == null ? null : host.runShell(command);
                    if (result == null) return new ApkEditorController.ToolResult(1, "", "No shell result.");
                    return new ApkEditorController.ToolResult(result.exitCode, result.stdout, result.stderr);
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void runOnUiThread(Runnable runnable) {
                    if (host != null) host.runOnUiThread(runnable);
                }

                @Override
                public void openSmaliWorkspace(String apkInputPath,
                                               String smaliInputDirPath,
                                               String selectedSmaliFilePath,
                                               String dexOutputPath,
                                               String apkOutputPath,
                                               String dexEntry) {
                    if (host != null) {
                        host.openSmaliWorkspace(apkInputPath,
                                smaliInputDirPath,
                                selectedSmaliFilePath,
                                dexOutputPath,
                                apkOutputPath,
                                dexEntry);
                    }
                }
            });
            controller.bind();
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[APK Editor] Failed to bind controls: " + t + "\n");
        }
    }
}
