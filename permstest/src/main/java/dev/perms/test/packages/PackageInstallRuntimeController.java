package dev.perms.test.packages;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;

/**
 * Owns the package-install source, script, and pm-input preparation helpers.
 *
 * MainActivity supplies app-specific adapters only; install policy and restaging
 * controller wiring stay with the package feature package.
 */
public final class PackageInstallRuntimeController {
    public interface Host {
        Context getContext();
        SharedPreferences getPrefs();
        boolean isInstallerScriptUiChecked();
        File copyUriToExternalDir(Uri uri, String subdir, String filename);
        void runShellCommandCaptureAndAppend(String command, ShellCallback callback);
        ShellResult runShellCommandCaptureBlocking(String command);
        void appendOutput(String text);
        void debug(String area, String message);
    }

    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface InstallPathCallback {
        void onReady(String path);
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

    private final Host host;
    private final String keyInstallUseAndroidDataPath;
    private final String keyInstallUseStagingFolder;
    private final String keyInstallSkipStagingLargeFiles;
    private final String keyInstallBypassLowTargetSdkBlock;
    private final String keyInstallIgnoreDexoptProfile;
    private final String keyInstallAllowDowngrade;
    private final String keyInstallUseInstallerScript;
    private final String importsDirName;
    private final String publicFilesDir;
    private final long skipStagingLargeBytes;

    private PackageInstallSourcePreparer sourcePreparer;
    private PackageInstallScriptController scriptController;
    private PackageInstallInputRestager inputRestager;

    public PackageInstallRuntimeController(
            Host host,
            String keyInstallUseAndroidDataPath,
            String keyInstallUseStagingFolder,
            String keyInstallSkipStagingLargeFiles,
            String keyInstallBypassLowTargetSdkBlock,
            String keyInstallIgnoreDexoptProfile,
            String keyInstallAllowDowngrade,
            String keyInstallUseInstallerScript,
            String importsDirName,
            String publicFilesDir,
            long skipStagingLargeBytes) {
        this.host = host;
        this.keyInstallUseAndroidDataPath = keyInstallUseAndroidDataPath;
        this.keyInstallUseStagingFolder = keyInstallUseStagingFolder;
        this.keyInstallSkipStagingLargeFiles = keyInstallSkipStagingLargeFiles;
        this.keyInstallBypassLowTargetSdkBlock = keyInstallBypassLowTargetSdkBlock;
        this.keyInstallIgnoreDexoptProfile = keyInstallIgnoreDexoptProfile;
        this.keyInstallAllowDowngrade = keyInstallAllowDowngrade;
        this.keyInstallUseInstallerScript = keyInstallUseInstallerScript;
        this.importsDirName = importsDirName;
        this.publicFilesDir = publicFilesDir;
        this.skipStagingLargeBytes = skipStagingLargeBytes;
    }

    public boolean shouldBypassLowTargetSdkBlock() {
        return getSourcePreparer().shouldBypassLowTargetSdkBlock();
    }

    public boolean shouldIgnoreDexoptProfile() {
        return getSourcePreparer().shouldIgnoreDexoptProfile();
    }

    public String buildPmInstallCreateCommand(String sizeBytes) {
        return getSourcePreparer().buildPmInstallCreateCommand(sizeBytes);
    }

    public String buildPmInstallCreateCommand(String sizeBytes, boolean includeOptionalFlags) {
        return getSourcePreparer().buildPmInstallCreateCommand(sizeBytes, includeOptionalFlags);
    }

    public boolean shouldUseAndroidDataInstallPath() {
        return getSourcePreparer().shouldUseAndroidDataInstallPath();
    }

    public boolean shouldRestageInstallInputPath(String path) {
        return getSourcePreparer().shouldRestageInstallInputPath(path);
    }

    public boolean shouldUseInstallStagingFolder() {
        return getSourcePreparer().shouldUseInstallStagingFolder();
    }

    public boolean shouldSkipInstallStagingForLargeFiles() {
        return getSourcePreparer().shouldSkipInstallStagingForLargeFiles();
    }

    public PackageInstallSourcePreparer.PreparedSource prepareInstallSourceFile(Uri uri, String displayName) {
        return getSourcePreparer().prepareInstallSourceFile(uri, displayName);
    }

    public boolean isScriptRequested() {
        return getScriptController().isScriptRequested();
    }

    public String prepareInputPathForScript(String sourcePath) {
        return getScriptController().prepareInputPathForScript(sourcePath);
    }

    public String buildScriptCommand(File scriptFile, String inputPath) {
        return getScriptController().buildScriptCommand(scriptFile, inputPath);
    }

    public void prepareInputPathForPmAsync(String sourcePath, final InstallPathCallback callback) {
        getInputRestager().prepareAsync(sourcePath, path -> {
            if (callback != null) callback.onReady(path);
        });
    }

    public String prepareInputPathForPmSync(String sourcePath) {
        return getInputRestager().prepareSync(sourcePath);
    }

    private PackageInstallSourcePreparer getSourcePreparer() {
        if (sourcePreparer == null) {
            sourcePreparer = new PackageInstallSourcePreparer(
                    host.getContext(),
                    host.getPrefs(),
                    keyInstallUseAndroidDataPath,
                    keyInstallUseStagingFolder,
                    keyInstallSkipStagingLargeFiles,
                    keyInstallBypassLowTargetSdkBlock,
                    keyInstallIgnoreDexoptProfile,
                    keyInstallAllowDowngrade,
                    importsDirName,
                    skipStagingLargeBytes,
                    host::copyUriToExternalDir
            );
        }
        return sourcePreparer;
    }

    private PackageInstallScriptController getScriptController() {
        if (scriptController == null) {
            scriptController = new PackageInstallScriptController(
                    new PackageInstallScriptController.Host() {
                        @Override
                        public SharedPreferences getPrefs() {
                            return host.getPrefs();
                        }

                        @Override
                        public boolean isInstallerScriptUiChecked() {
                            return host.isInstallerScriptUiChecked();
                        }

                        @Override
                        public boolean shouldUseAndroidDataInstallPath() {
                            return PackageInstallRuntimeController.this.shouldUseAndroidDataInstallPath();
                        }

                        @Override
                        public boolean shouldUseInstallStagingFolder() {
                            return PackageInstallRuntimeController.this.shouldUseInstallStagingFolder();
                        }

                        @Override
                        public boolean shouldSkipInstallStagingForLargeFiles() {
                            return PackageInstallRuntimeController.this.shouldSkipInstallStagingForLargeFiles();
                        }

                        @Override
                        public boolean shouldBypassLowTargetSdkBlock() {
                            return PackageInstallRuntimeController.this.shouldBypassLowTargetSdkBlock();
                        }

                        @Override
                        public boolean shouldIgnoreDexoptProfile() {
                            return PackageInstallRuntimeController.this.shouldIgnoreDexoptProfile();
                        }

                        @Override
                        public String prepareInstallInputPathForPmSync(String sourcePath) {
                            return PackageInstallRuntimeController.this.prepareInputPathForPmSync(sourcePath);
                        }

                        @Override
                        public void appendOutput(String text) {
                            host.appendOutput(text);
                        }
                    },
                    keyInstallUseInstallerScript);
        }
        return scriptController;
    }

    private PackageInstallInputRestager getInputRestager() {
        if (inputRestager == null) {
            inputRestager = new PackageInstallInputRestager(
                    publicFilesDir,
                    this::shouldRestageInstallInputPath,
                    (command, callback) -> host.runShellCommandCaptureAndAppend(command, (exitCode, stdout, stderr) -> {
                        if (callback != null) callback.onComplete(exitCode, stdout, stderr);
                    }),
                    command -> {
                        ShellResult result = host.runShellCommandCaptureBlocking(command);
                        return new PackageInstallInputRestager.ShellResult(
                                result == null ? -1 : result.exitCode,
                                result == null ? "" : result.stdout,
                                result == null ? "" : result.stderr);
                    },
                    host::appendOutput,
                    message -> host.debug(PackageInstallDebug.Area.INSTALL_INPUT, message)
            );
        }
        return inputRestager;
    }
}
