package dev.perms.test.packages;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import dev.perms.test.databinding.ActivityMainBinding;
import java.io.File;

/**
 * Owns Packages-tab install runtime option and source-preparation wiring.
 *
 * <p>The controller keeps install path/staging/script flag decisions in the
 * package feature package while the activity only supplies shared app services
 * such as preferences, shell execution, and external-file copying.</p>
 */
public final class PackageInstallRuntimeActivityController {
    public interface Host {
        Context getContext();
        ActivityMainBinding getBinding();
        SharedPreferences getPreferences();

        File copyUriToExternalDir(Uri uri, String subdir, String filename);
        void runShellCommandCaptureAndAppend(String command, ShellCallback callback);
        ShellResult runShellCommandCaptureBlocking(String command);
        void appendOutput(String text);
        void debug(String area, String message);
    }

    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public static final class ShellResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ShellResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    public interface PathCallback {
        void onReady(String path);
    }

    private final Host host;
    private final String keyInstallUseAndroidDataPath;
    private final String keyInstallUseStagingFolder;
    private final String keyInstallSkipStagingLargeFiles;
    private final String keyInstallBypassLowTargetSdkBlock;
    private final String keyInstallIgnoreDexoptProfile;
    private final String keyInstallAllowDowngrade;
    private final String keyInstallUseInstallerScript;
    private final String externalImportsDir;
    private final String publicFilesDir;
    private final long installSkipStagingLargeBytes;

    private PackageInstallRuntimeController runtimeController;

    public PackageInstallRuntimeActivityController(Host host,
                                                   String keyInstallUseAndroidDataPath,
                                                   String keyInstallUseStagingFolder,
                                                   String keyInstallSkipStagingLargeFiles,
                                                   String keyInstallBypassLowTargetSdkBlock,
                                                   String keyInstallIgnoreDexoptProfile,
                                                   String keyInstallAllowDowngrade,
                                                   String keyInstallUseInstallerScript,
                                                   String externalImportsDir,
                                                   String publicFilesDir,
                                                   long installSkipStagingLargeBytes) {
        this.host = host;
        this.keyInstallUseAndroidDataPath = keyInstallUseAndroidDataPath;
        this.keyInstallUseStagingFolder = keyInstallUseStagingFolder;
        this.keyInstallSkipStagingLargeFiles = keyInstallSkipStagingLargeFiles;
        this.keyInstallBypassLowTargetSdkBlock = keyInstallBypassLowTargetSdkBlock;
        this.keyInstallIgnoreDexoptProfile = keyInstallIgnoreDexoptProfile;
        this.keyInstallAllowDowngrade = keyInstallAllowDowngrade;
        this.keyInstallUseInstallerScript = keyInstallUseInstallerScript;
        this.externalImportsDir = externalImportsDir;
        this.publicFilesDir = publicFilesDir;
        this.installSkipStagingLargeBytes = installSkipStagingLargeBytes;
    }

    private PackageInstallRuntimeController getRuntimeController() {
        if (runtimeController == null) {
            runtimeController = new PackageInstallRuntimeController(
                    new PackageInstallRuntimeController.Host() {
                        @Override
                        public Context getContext() {
                            return host == null ? null : host.getContext();
                        }

                        @Override
                        public SharedPreferences getPrefs() {
                            return host == null ? null : host.getPreferences();
                        }

                        @Override
                        public boolean isInstallerScriptUiChecked() {
                            ActivityMainBinding binding = host == null ? null : host.getBinding();
                            return binding != null
                                    && binding.tabPackages != null
                                    && binding.tabPackages.chkUseInstallerScript != null
                                    && binding.tabPackages.chkUseInstallerScript.isChecked();
                        }

                        @Override
                        public File copyUriToExternalDir(Uri uri, String subdir, String filename) {
                            return host == null ? null : host.copyUriToExternalDir(uri, subdir, filename);
                        }

                        @Override
                        public void runShellCommandCaptureAndAppend(String command, PackageInstallRuntimeController.ShellCallback callback) {
                            if (host == null) {
                                if (callback != null) callback.onComplete(1, "", "Shell runner is unavailable.");
                                return;
                            }
                            host.runShellCommandCaptureAndAppend(command, (exitCode, stdout, stderr) -> {
                                if (callback != null) callback.onComplete(exitCode, stdout, stderr);
                            });
                        }

                        @Override
                        public PackageInstallRuntimeController.ShellResult runShellCommandCaptureBlocking(String command) {
                            ShellResult result = host == null ? null : host.runShellCommandCaptureBlocking(command);
                            return new PackageInstallRuntimeController.ShellResult(
                                    result == null ? -1 : result.exitCode,
                                    result == null ? "" : result.stdout,
                                    result == null ? "" : result.stderr);
                        }

                        @Override
                        public void appendOutput(String text) {
                            if (host != null) host.appendOutput(text);
                        }

                        @Override
                        public void debug(String area, String message) {
                            if (host != null) host.debug(area, message);
                        }
                    },
                    keyInstallUseAndroidDataPath,
                    keyInstallUseStagingFolder,
                    keyInstallSkipStagingLargeFiles,
                    keyInstallBypassLowTargetSdkBlock,
                    keyInstallIgnoreDexoptProfile,
                    keyInstallAllowDowngrade,
                    keyInstallUseInstallerScript,
                    externalImportsDir,
                    publicFilesDir,
                    installSkipStagingLargeBytes);
        }
        return runtimeController;
    }

    public boolean shouldBypassLowTargetSdkBlock() {
        return getRuntimeController().shouldBypassLowTargetSdkBlock();
    }

    public boolean shouldIgnoreDexoptProfile() {
        return getRuntimeController().shouldIgnoreDexoptProfile();
    }

    public String buildPmInstallCreateCommand(String sizeBytes) {
        return getRuntimeController().buildPmInstallCreateCommand(sizeBytes);
    }

    public String buildPmInstallCreateCommand(String sizeBytes, boolean includeOptionalFlags) {
        return getRuntimeController().buildPmInstallCreateCommand(sizeBytes, includeOptionalFlags);
    }

    public boolean shouldUseAndroidDataInstallPath() {
        return getRuntimeController().shouldUseAndroidDataInstallPath();
    }

    public boolean shouldRestageInstallInputPath(String path) {
        return getRuntimeController().shouldRestageInstallInputPath(path);
    }

    public boolean shouldUseInstallStagingFolder() {
        return getRuntimeController().shouldUseInstallStagingFolder();
    }

    public boolean shouldSkipInstallStagingForLargeFiles() {
        return getRuntimeController().shouldSkipInstallStagingForLargeFiles();
    }

    public PackageInstallSourcePreparer.PreparedSource prepareInstallSourceFile(Uri uri, String displayName) {
        return getRuntimeController().prepareInstallSourceFile(uri, displayName);
    }

    public boolean isScriptRequested() {
        return getRuntimeController().isScriptRequested();
    }

    public String prepareInputPathForScript(String sourcePath) {
        return getRuntimeController().prepareInputPathForScript(sourcePath);
    }

    public String buildScriptCommand(File scriptFile, String inputPath) {
        return getRuntimeController().buildScriptCommand(scriptFile, inputPath);
    }

    public void prepareInputPathForPmAsync(String sourcePath, PathCallback callback) {
        getRuntimeController().prepareInputPathForPmAsync(sourcePath, path -> {
            if (callback != null) callback.onReady(path);
        });
    }

    public String prepareInputPathForPmSync(String sourcePath) {
        return getRuntimeController().prepareInputPathForPmSync(sourcePath);
    }
}
