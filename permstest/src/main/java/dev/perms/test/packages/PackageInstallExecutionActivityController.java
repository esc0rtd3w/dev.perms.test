package dev.perms.test.packages;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import dev.perms.test.databinding.ActivityMainBinding;

import java.io.File;

/**
 * Owns the Packages-tab install execution wiring for MainActivity.
 *
 * <p>This keeps picked APK/archive install flow, completion dialogs, install result
 * probing, and built-in pm-session helpers in the package feature package. The
 * activity supplies only shared app services such as shell execution, source
 * preparation, public binary staging, and UI callbacks.</p>
 */
public final class PackageInstallExecutionActivityController {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        SharedPreferences getPreferences();

        void runIo(Runnable runnable);
        void runOnUi(Runnable runnable);

        String queryDisplayName(Uri uri);
        String sanitizeFilename(String name);
        PackageInstallSourcePreparer.PreparedSource prepareInstallSourceFile(Uri uri, String displayName);

        boolean isScriptRequested();
        String prepareInstallInputPathForScript(String sourcePath);
        String buildInstallScriptCommand(File scriptFile, String inputPath);

        void prepareInstallInputPathForPmAsync(String sourcePath, PathCallback callback);
        String prepareInstallInputPathForPmSync(String sourcePath);
        String buildPmInstallCreateCommand(String sizeBytes);

        void runShellCommandCaptureAndAppend(String command, ResultCallback callback);
        ShellResult runRawShizukuShellCapture(String command);
        void runShellCommand(String command);

        void ensureBundledBinaryPublic(String toolName);
        String getPublicStageDir();
        String getPublicFilesDir();
        String getPublicBinDir();
        boolean isCustomSplitOptionsEnabled();
        String[] getBundledAssetDirs();

        void clearPickedSelectionUi();
        void closeTaskAfterFileOpen();
        void cleanupManagedImportFile(String path);
        void appendOutput(String text);
        void debug(String area, String message);
    }

    public interface ResultCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface PathCallback {
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
    private final String hideFileOpenUiKey;
    private final String defaultInstallScript;

    private PackagePickedInstallCoordinator pickedInstallCoordinator;
    private PackageInstallUiProbe installUiProbe;
    private PackageInstallCompletionController completionController;
    private PackageInstallDialogController dialogController;
    private PackageArchiveExtractor archiveExtractor;
    private PackageArchiveSessionInstaller archiveSessionInstaller;
    private PackageStandaloneSessionInstaller standaloneSessionInstaller;
    private PackageInstallScriptAssets scriptAssets;

    private String lastFileOpenInstallOut = "";
    private String lastFileOpenInstallErr = "";
    private int lastFileOpenInstallExit = 0;
    private String lastInstallDebugLog = "";

    public PackageInstallExecutionActivityController(Host host,
                                                     String hideFileOpenUiKey,
                                                     String defaultInstallScript) {
        this.host = host;
        this.hideFileOpenUiKey = hideFileOpenUiKey;
        this.defaultInstallScript = defaultInstallScript;
    }

    public void install(Uri uri, String label) {
        getPickedInstallCoordinator().install(uri, label);
    }

    public void install(Uri uri, String label, boolean fromFileOpen) {
        getPickedInstallCoordinator().install(uri, label, fromFileOpen);
    }

    private PackagePickedInstallCoordinator getPickedInstallCoordinator() {
        if (pickedInstallCoordinator == null) {
            pickedInstallCoordinator = new PackagePickedInstallCoordinator(new PackagePickedInstallCoordinator.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public void runIo(Runnable runnable) {
                    if (host != null) host.runIo(runnable);
                }

                @Override
                public void runOnUi(Runnable runnable) {
                    if (host != null) host.runOnUi(runnable);
                }

                @Override
                public String queryDisplayName(Uri uri) {
                    return host == null ? null : host.queryDisplayName(uri);
                }

                @Override
                public String sanitizeFilename(String name) {
                    return host == null ? name : host.sanitizeFilename(name);
                }

                @Override
                public PackageInstallSourcePreparer.PreparedSource prepareInstallSourceFile(Uri uri, String displayName) {
                    return host == null ? null : host.prepareInstallSourceFile(uri, displayName);
                }

                @Override
                public String buildAbiInstallWarning(File apkFile) {
                    return PackageAbiInspector.buildInstallWarning(apkFile);
                }

                @Override
                public boolean isScriptRequested() {
                    return host != null && host.isScriptRequested();
                }

                @Override
                public File ensureInstallScriptOnExternal() {
                    return ensureAssetScriptOnExternal(defaultInstallScript);
                }

                @Override
                public String prepareInstallInputPathForScript(String sourcePath) {
                    return host == null ? sourcePath : host.prepareInstallInputPathForScript(sourcePath);
                }

                @Override
                public String buildInstallScriptCommand(File scriptFile, String inputPath) {
                    return host == null ? "" : host.buildInstallScriptCommand(scriptFile, inputPath);
                }

                @Override
                public void runInstallScript(String command, PackagePickedInstallCoordinator.ResultCallback callback) {
                    runShellCommandCaptureAndAppend(command, (exitCode, stdout, stderr) -> {
                        if (callback != null) callback.onComplete(exitCode, stdout, stderr);
                    });
                }

                @Override
                public void runStandaloneInstall(String apkPath, boolean useCreateSize, PackagePickedInstallCoordinator.ResultCallback callback) {
                    runBuiltInPmSessionInstall(apkPath, useCreateSize, (exitCode, stdout, stderr) -> {
                        if (callback != null) callback.onComplete(exitCode, stdout, stderr);
                    });
                }

                @Override
                public PackagePickedInstallCoordinator.InstallResult runArchiveInstallBlocking(String archivePath) {
                    ShellResult result = runBuiltInArchivePmSessionInstall(archivePath);
                    if (result == null) return new PackagePickedInstallCoordinator.InstallResult(1, "", "Archive install returned no result.");
                    return new PackagePickedInstallCoordinator.InstallResult(result.exitCode, result.stdout, result.stderr);
                }

                @Override
                public PackagePickedInstallCoordinator.InstallResult runShellCaptureBlocking(String command) {
                    ShellResult result = runRawShell(command);
                    if (result == null) return new PackagePickedInstallCoordinator.InstallResult(1, "", "Shell command returned no result.");
                    return new PackagePickedInstallCoordinator.InstallResult(result.exitCode, result.stdout, result.stderr);
                }

                @Override
                public boolean isExistingPackageInstallConflict(String stdout, String stderr) {
                    return PackageInstallResults.isExistingPackageInstallConflict(stdout, stderr);
                }

                @Override
                public String buildInstallDebugLog(String sourcePath, String label, int exitCode, String stdout, String stderr) {
                    return PackageInstallResults.buildDebugLog(sourcePath, label, exitCode, stdout, stderr);
                }

                @Override
                public String buildInstallFailureMessage(String sourcePath, String label, int exitCode, String stdout, String stderr) {
                    return PackageInstallResults.buildFailureMessage(sourcePath, label, exitCode, stdout, stderr, false);
                }

                @Override
                public void showInstallFailedDialog(String message) {
                    PackageInstallExecutionActivityController.this.showInstallFailedDialog(message);
                }

                @Override
                public void clearPickedSelectionUi() {
                    if (host != null) host.clearPickedSelectionUi();
                }

                @Override
                public void onInstallFinishedUi(int exitCode, String sourcePath, String label, boolean fromFileOpen) {
                    PackageInstallExecutionActivityController.this.onInstallFinishedUi(exitCode, sourcePath, label, fromFileOpen);
                }

                @Override
                public void setLastFileOpenInstallResult(int exitCode, String stdout, String stderr) {
                    lastFileOpenInstallExit = exitCode;
                    lastFileOpenInstallOut = stdout == null ? "" : stdout;
                    lastFileOpenInstallErr = stderr == null ? "" : stderr;
                }

                @Override
                public void resetLastFileOpenInstallResult() {
                    lastFileOpenInstallExit = 0;
                    lastFileOpenInstallOut = "";
                    lastFileOpenInstallErr = "";
                }

                @Override
                public void setLastInstallDebugLog(String log) {
                    lastInstallDebugLog = log == null ? "" : log;
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void debug(String area, String message) {
                    if (host != null) host.debug(area, message);
                }
            });
        }
        return pickedInstallCoordinator;
    }

    private PackageInstallCompletionController getCompletionController() {
        if (completionController == null) {
            completionController = new PackageInstallCompletionController(
                    host == null ? null : host.getActivity(),
                    hideFileOpenUiKey,
                    new PackageInstallCompletionController.Host() {
                        @Override
                        public SharedPreferences getPreferences() {
                            return host == null ? null : host.getPreferences();
                        }

                        @Override
                        public int getLastFileOpenInstallExit() {
                            return lastFileOpenInstallExit;
                        }

                        @Override
                        public String getLastFileOpenInstallOut() {
                            return lastFileOpenInstallOut;
                        }

                        @Override
                        public String getLastFileOpenInstallErr() {
                            return lastFileOpenInstallErr;
                        }

                        @Override
                        public void setLastInstallDebugLog(String log) {
                            lastInstallDebugLog = log == null ? "" : log;
                        }

                        @Override
                        public PackageInstallUiProbe.InstallUiInfo probeInstallUiInfo(String path, String originalLabel) {
                            return getInstallUiProbe().probeInstallUiInfo(path, originalLabel);
                        }

                        @Override
                        public PackageInstallUiProbe.InstallUiInfo probeInstalledPackageFromNameHints(String... hints) {
                            return getInstallUiProbe().probeInstalledPackageFromNameHints(hints);
                        }

                        @Override
                        public PackageInstallUiProbe.InstallUiInfo refreshInstalledUiInfo(PackageInstallUiProbe.InstallUiInfo info) {
                            return getInstallUiProbe().refreshInstalledUiInfo(info);
                        }

                        @Override
                        public void showInstallFailedDialog(String message) {
                            PackageInstallExecutionActivityController.this.showInstallFailedDialog(message);
                        }

                        @Override
                        public void showInstallDoneDialog(String packageName, String label, boolean finishOnDone) {
                            PackageInstallExecutionActivityController.this.showInstallDoneDialog(packageName, label, finishOnDone);
                        }

                        @Override
                        public void closeTaskAfterFileOpen() {
                            if (host != null) host.closeTaskAfterFileOpen();
                        }

                        @Override
                        public void cleanupManagedImportFile(String path) {
                            if (host != null) host.cleanupManagedImportFile(path);
                        }
                    });
        }
        return completionController;
    }

    private void onInstallFinishedUi(int exitCode, String sourcePath, String label, boolean fromFileOpen) {
        getCompletionController().onInstallFinishedUi(exitCode, sourcePath, label, fromFileOpen);
    }

    private PackageInstallDialogController getDialogController() {
        if (dialogController == null) {
            dialogController = new PackageInstallDialogController(
                    host == null ? null : host.getActivity(),
                    () -> lastInstallDebugLog,
                    () -> { if (host != null) host.closeTaskAfterFileOpen(); },
                    command -> { if (host != null) host.runShellCommand(command); });
        }
        return dialogController;
    }

    private void showInstallFailedDialog(String message) {
        getDialogController().showInstallFailedDialog(message);
    }

    private void showInstallDoneDialog(String packageName, String label, boolean finishOnDone) {
        getDialogController().showInstallDoneDialog(packageName, label, finishOnDone);
    }

    private PackageInstallUiProbe getInstallUiProbe() {
        if (installUiProbe == null) {
            installUiProbe = new PackageInstallUiProbe(host == null ? null : host.getActivity());
        }
        return installUiProbe;
    }

    private PackageStandaloneSessionInstaller getStandaloneSessionInstaller() {
        if (standaloneSessionInstaller == null) {
            standaloneSessionInstaller = new PackageStandaloneSessionInstaller(
                    (sourcePath, callback) -> prepareInstallInputPathForPm(sourcePath, readyPath -> {
                        if (callback != null) callback.onReady(readyPath);
                    }),
                    (command, callback) -> runShellCommandCaptureAndAppend(command, (exitCode, stdout, stderr) -> {
                        if (callback != null) callback.onComplete(exitCode, stdout, stderr);
                    }),
                    this::buildPmInstallCreateCommand,
                    this::appendOutput
            );
        }
        return standaloneSessionInstaller;
    }

    private void runBuiltInPmSessionInstall(String apkPath, boolean useCreateSize, ResultCallback callback) {
        getStandaloneSessionInstaller().install(apkPath, useCreateSize, (exitCode, stdout, stderr) -> {
            if (callback != null) callback.onComplete(exitCode, stdout, stderr);
        });
    }

    private PackageArchiveExtractor getArchiveExtractor() {
        if (archiveExtractor == null) {
            archiveExtractor = new PackageArchiveExtractor(
                    command -> {
                        ShellResult result = runRawShell(command);
                        return new PackageArchiveExtractor.Result(result.exitCode, result.stdout, result.stderr);
                    },
                    toolName -> { if (host != null) host.ensureBundledBinaryPublic(toolName); },
                    host == null ? "" : host.getPublicBinDir()
            );
        }
        return archiveExtractor;
    }

    private ShellResult extractArchiveToDir(String archivePath, String outDir) {
        PackageArchiveExtractor.Result result = getArchiveExtractor().extractArchiveToDir(archivePath, outDir);
        return new ShellResult(result.exitCode, result.stdout, result.stderr);
    }

    private PackageArchiveSessionInstaller getArchiveSessionInstaller() {
        if (archiveSessionInstaller == null) {
            archiveSessionInstaller = new PackageArchiveSessionInstaller(
                    host == null ? null : host.getActivity(),
                    host == null ? "" : host.getPublicStageDir(),
                    host == null ? "" : host.getPublicFilesDir(),
                    host == null ? "" : host.getPublicBinDir(),
                    command -> {
                        ShellResult result = runRawShell(command);
                        return new PackageArchiveSessionInstaller.Result(result.exitCode, result.stdout, result.stderr);
                    },
                    (archivePath, outputDirectory) -> {
                        ShellResult result = extractArchiveToDir(archivePath, outputDirectory);
                        return new PackageArchiveSessionInstaller.Result(result.exitCode, result.stdout, result.stderr);
                    },
                    this::prepareInstallInputPathForPmSync,
                    this::buildPmInstallCreateCommand,
                    this::getFileSizeBytes,
                    () -> host != null && host.isCustomSplitOptionsEnabled(),
                    message -> { if (host != null) host.debug(PackageInstallDebug.Area.ARCHIVE_INSTALL, message); }
            );
        }
        return archiveSessionInstaller;
    }

    private ShellResult runBuiltInArchivePmSessionInstall(String archivePath) {
        PackageArchiveSessionInstaller.Result result = getArchiveSessionInstaller().install(archivePath);
        return new ShellResult(result.exitCode, result.stdout, result.stderr);
    }

    private PackageInstallScriptAssets getScriptAssets() {
        if (scriptAssets == null) {
            scriptAssets = new PackageInstallScriptAssets(
                    host == null ? null : host.getActivity(),
                    text -> { if (host != null) host.appendOutput(text); },
                    command -> { if (host != null) host.runShellCommand(command); },
                    () -> host == null ? new String[0] : host.getBundledAssetDirs());
        }
        return scriptAssets;
    }

    private File ensureAssetScriptOnExternal(String name) {
        return getScriptAssets().ensureAssetScriptOnExternal(name);
    }

    private void runShellCommandCaptureAndAppend(String command, ResultCallback callback) {
        if (host == null) {
            if (callback != null) callback.onComplete(1, "", "Shell runner is unavailable.");
            return;
        }
        host.runShellCommandCaptureAndAppend(command, callback);
    }

    private ShellResult runRawShell(String command) {
        if (host == null) return new ShellResult(1, "", "Shell runner is unavailable.");
        ShellResult result = host.runRawShizukuShellCapture(command);
        return result == null ? new ShellResult(1, "", "Shell command returned no result.") : result;
    }

    private String buildPmInstallCreateCommand(String sizeBytes) {
        return host == null ? "pm install-create" : host.buildPmInstallCreateCommand(sizeBytes);
    }

    private void prepareInstallInputPathForPm(String sourcePath, PathCallback callback) {
        if (host == null) {
            if (callback != null) callback.onReady(sourcePath);
            return;
        }
        host.prepareInstallInputPathForPmAsync(sourcePath, callback);
    }

    private String prepareInstallInputPathForPmSync(String sourcePath) {
        return host == null ? sourcePath : host.prepareInstallInputPathForPmSync(sourcePath);
    }

    private void appendOutput(String text) {
        if (host != null) host.appendOutput(text);
    }

    private long getFileSizeBytes(String path) {
        try {
            ShellResult result = runRawShell("stat -c %s " + PackageInstallCommands.shQuote(path)
                    + " 2>/dev/null || wc -c < " + PackageInstallCommands.shQuote(path));
            String digits = extractDigits(result.stdout);
            if (TextUtils.isEmpty(digits)) return -1L;
            return Long.parseLong(digits);
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static String extractDigits(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') out.append(c);
        }
        return out.toString();
    }
}
