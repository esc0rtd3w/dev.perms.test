package dev.perms.test.packages;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import dev.perms.test.databinding.ActivityMainBinding;

import java.io.File;
import java.util.Locale;

/**
 * Coordinates the Package Tools "picked package" install flow.
 *
 * <p>MainActivity owns the UI lifecycle and backend wiring. This class owns the
 * branch selection and completion sequencing for picked APK/archive installs so
 * the activity does not carry the package-install workflow directly.</p>
 */
public final class PackagePickedInstallCoordinator {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();

        void runIo(Runnable runnable);
        void runOnUi(Runnable runnable);

        String queryDisplayName(Uri uri);
        String sanitizeFilename(String name);
        PackageInstallSourcePreparer.PreparedSource prepareInstallSourceFile(Uri uri, String displayName);
        String buildAbiInstallWarning(File apkFile);

        boolean isScriptRequested();
        File ensureInstallScriptOnExternal();
        String prepareInstallInputPathForScript(String sourcePath);
        String buildInstallScriptCommand(File scriptFile, String inputPath);
        void runInstallScript(String command, ResultCallback callback);

        void runStandaloneInstall(String apkPath, boolean useCreateSize, ResultCallback callback);
        InstallResult runArchiveInstallBlocking(String archivePath);
        InstallResult runShellCaptureBlocking(String command);

        boolean isExistingPackageInstallConflict(String stdout, String stderr);
        String buildInstallDebugLog(String sourcePath, String label, int exitCode, String stdout, String stderr);
        String buildInstallFailureMessage(String sourcePath, String label, int exitCode, String stdout, String stderr);
        void showInstallFailedDialog(String message);

        void clearPickedSelectionUi();
        void onInstallFinishedUi(int exitCode, String sourcePath, String label, boolean fromFileOpen);
        void setLastFileOpenInstallResult(int exitCode, String stdout, String stderr);
        void resetLastFileOpenInstallResult();
        void setLastInstallDebugLog(String log);

        void appendOutput(String text);
        void debug(String area, String message);
    }

    public interface ResultCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public static final class InstallResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public InstallResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private final Host host;

    public PackagePickedInstallCoordinator(Host host) {
        this.host = host;
    }

    public void install(final Uri uri, final String label) {
        install(uri, label, false);
    }

    public void install(final Uri uri, final String label, final boolean fromFileOpen) {
        try {
            if (uri == null || host == null) return;
            debug("installPickedPackageFile start label=" + label + ", uri=" + uri + ", fromFileOpen=" + fromFileOpen);
            if (fromFileOpen) host.resetLastFileOpenInstallResult();

            final boolean useScriptRequested = host.isScriptRequested();
            final boolean clearAfterSuccess = isClearAfterInstallChecked();
            final boolean useCreateSize = isCreateSizeChecked();
            setInstallButtonEnabled(false);
            toast("Preparing install…");

            host.runIo(() -> prepareAndDispatchInstall(uri, label, fromFileOpen, useScriptRequested, clearAfterSuccess, useCreateSize));
        } catch (Throwable ignored) {
            setInstallButtonEnabled(true);
        }
    }

    private void prepareAndDispatchInstall(Uri uri,
                                           String label,
                                           boolean fromFileOpen,
                                           boolean useScriptRequested,
                                           boolean clearAfterSuccess,
                                           boolean useCreateSize) {
        try {
            String suggested = label;
            if (TextUtils.isEmpty(trim(suggested))) suggested = host.queryDisplayName(uri);
            if (TextUtils.isEmpty(trim(suggested))) suggested = "package.bin";
            suggested = host.sanitizeFilename(suggested);

            PackageInstallSourcePreparer.PreparedSource prepared = host.prepareInstallSourceFile(uri, suggested);
            File dest = prepared == null ? null : prepared.file;
            debug("prepared source suggested=" + suggested
                    + ", usedImportsCopy=" + (prepared != null && prepared.usedImportsCopy)
                    + ", requestedDirectPath=" + (prepared != null && prepared.requestedDirectPath)
                    + ", skippedLarge=" + (prepared != null && prepared.skippedByLargeFile)
                    + ", dest=" + PackageInstallDebug.describePath(dest == null ? null : dest.getAbsolutePath()));
            if (dest == null) {
                host.runOnUi(() -> {
                    setInstallButtonEnabled(true);
                    toast("Copy failed");
                });
                return;
            }

            final String destPath = dest.getAbsolutePath();
            final boolean isStandaloneApk = destPath.toLowerCase(Locale.US).endsWith(".apk");
            if (isStandaloneApk) {
                String abiWarning = host.buildAbiInstallWarning(dest);
                if (!TextUtils.isEmpty(abiWarning)) {
                    host.appendOutput("[APK ABI] " + abiWarning + "\n");
                    debug("abi warning: " + abiWarning);
                }
            }

            debug("install branch standaloneApk=" + isStandaloneApk
                    + ", useScriptRequested=" + useScriptRequested
                    + ", clearAfterSuccess=" + clearAfterSuccess
                    + ", useCreateSize=" + useCreateSize
                    + ", dest=" + PackageInstallDebug.describePath(destPath));

            appendSourcePreparationNote(prepared);

            File script = resolveInstallScript(useScriptRequested);
            if (useScriptRequested && script == null) {
                host.runOnUi(() -> {
                    setInstallButtonEnabled(true);
                    toast("Missing install-apk.sh");
                });
                return;
            }

            String scriptInputPath = destPath;
            if (script != null) scriptInputPath = host.prepareInstallInputPathForScript(destPath);
            final File scriptFinal = script;
            final String scriptInputPathFinal = scriptInputPath;
            debug("script input=" + PackageInstallDebug.describePath(scriptInputPathFinal));

            host.runOnUi(() -> dispatchPreparedInstall(
                    destPath,
                    label,
                    fromFileOpen,
                    useScriptRequested,
                    clearAfterSuccess,
                    useCreateSize,
                    isStandaloneApk,
                    scriptFinal,
                    scriptInputPathFinal));
        } catch (Throwable t) {
            host.runOnUi(() -> {
                setInstallButtonEnabled(true);
                host.appendOutput("[!] Install prep failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            });
        }
    }

    private void dispatchPreparedInstall(String destPath,
                                         String label,
                                         boolean fromFileOpen,
                                         boolean useScriptRequested,
                                         boolean clearAfterSuccess,
                                         boolean useCreateSize,
                                         boolean isStandaloneApk,
                                         File scriptFinal,
                                         String scriptInputPathFinal) {
        try {
            setInstallButtonEnabled(true);
            if (!useScriptRequested) {
                if (isStandaloneApk) {
                    runStandaloneBuiltIn(destPath, label, fromFileOpen, clearAfterSuccess, useCreateSize, scriptFinal, scriptInputPathFinal);
                } else {
                    runArchiveBuiltIn(destPath, label, fromFileOpen, clearAfterSuccess, scriptFinal, scriptInputPathFinal);
                }
            } else {
                runRequestedScript(destPath, label, fromFileOpen, clearAfterSuccess, scriptFinal, scriptInputPathFinal);
            }
        } catch (Throwable ignored) {
        }
    }

    private void runStandaloneBuiltIn(String destPath,
                                      String label,
                                      boolean fromFileOpen,
                                      boolean clearAfterSuccess,
                                      boolean useCreateSize,
                                      File scriptFinal,
                                      String scriptInputPathFinal) {
        host.runStandaloneInstall(destPath, useCreateSize, (exit, out, err) -> {
            setFileOpenResultIfNeeded(fromFileOpen, exit, out, err);

            if (exit == 0) {
                if (clearAfterSuccess) host.runOnUi(host::clearPickedSelectionUi);
                host.onInstallFinishedUi(exit, destPath, label, fromFileOpen);
                return;
            }

            if (scriptFinal != null && !host.isExistingPackageInstallConflict(out, err)) {
                host.appendOutput("[i] Built-in install failed; falling back to install-apk.sh\n");
                String command = host.buildInstallScriptCommand(scriptFinal, scriptInputPathFinal);
                host.runInstallScript(command, (e2, o2, r2) -> {
                    setFileOpenResultIfNeeded(fromFileOpen, e2, o2, r2);
                    if (e2 == 0 && clearAfterSuccess) host.runOnUi(host::clearPickedSelectionUi);
                    host.onInstallFinishedUi(e2, destPath, label, fromFileOpen);
                });
                return;
            }

            host.onInstallFinishedUi(exit, destPath, label, fromFileOpen);
        });
    }

    private void runArchiveBuiltIn(String destPath,
                                   String label,
                                   boolean fromFileOpen,
                                   boolean clearAfterSuccess,
                                   File scriptFinal,
                                   String scriptInputPathFinal) {
        host.appendOutput("[i] Installing archive with built-in installer (unzip + pm session)...\n");
        debug("archive install dispatch dest=" + PackageInstallDebug.describePath(destPath));
        host.runIo(() -> {
            debug("archive worker begin dest=" + PackageInstallDebug.describePath(destPath));
            InstallResult finalResult = host.runArchiveInstallBlocking(destPath);
            debug("archive worker result exit=" + finalResult.exitCode
                    + ", stdoutLen=" + finalResult.stdout.length()
                    + ", stderrLen=" + finalResult.stderr.length());

            if (finalResult.exitCode != 0
                    && scriptFinal != null
                    && scriptFinal.exists()
                    && !host.isExistingPackageInstallConflict(finalResult.stdout, finalResult.stderr)) {
                debug("archive built-in failed; running script fallback exit=" + finalResult.exitCode);
                String fallbackCommand = "chmod 777 " + PackageInstallCommands.shQuote(scriptFinal.getAbsolutePath()) + " 2>/dev/null || true; "
                        + host.buildInstallScriptCommand(scriptFinal, scriptInputPathFinal);
                InstallResult fallback = host.runShellCaptureBlocking(fallbackCommand);
                if (fallback.exitCode == 0) {
                    finalResult = fallback;
                } else {
                    finalResult = new InstallResult(
                            finalResult.exitCode,
                            safeJoin(safeJoin("[i] Built-in archive install failed; falling back to install-apk.sh\n", finalResult.stdout), fallback.stdout),
                            safeJoin(finalResult.stderr, fallback.stderr));
                }
            }

            final InstallResult result = finalResult;
            host.runOnUi(() -> finishArchiveInstall(destPath, label, fromFileOpen, clearAfterSuccess, result));
        });
    }

    private void finishArchiveInstall(String destPath,
                                      String label,
                                      boolean fromFileOpen,
                                      boolean clearAfterSuccess,
                                      InstallResult result) {
        debug("archive worker posting UI result exit=" + result.exitCode + ", fromFileOpen=" + fromFileOpen);
        setInstallButtonEnabled(true);
        if (!TextUtils.isEmpty(result.stdout)) host.appendOutput(result.stdout.endsWith("\n") ? result.stdout : result.stdout + "\n");
        if (!TextUtils.isEmpty(result.stderr)) host.appendOutput(result.stderr.endsWith("\n") ? result.stderr : result.stderr + "\n");
        setFileOpenResultIfNeeded(fromFileOpen, result.exitCode, result.stdout, result.stderr);
        if (result.exitCode == 0 && clearAfterSuccess) host.clearPickedSelectionUi();
        host.onInstallFinishedUi(result.exitCode, destPath, label, fromFileOpen);

        if (!fromFileOpen) {
            if (result.exitCode != 0) {
                host.setLastInstallDebugLog(host.buildInstallDebugLog(destPath, label, result.exitCode, result.stdout, result.stderr));
                host.showInstallFailedDialog(host.buildInstallFailureMessage(destPath, label, result.exitCode, result.stdout, result.stderr));
            } else {
                host.setLastInstallDebugLog("");
            }
        }
    }

    private void runRequestedScript(String destPath,
                                    String label,
                                    boolean fromFileOpen,
                                    boolean clearAfterSuccess,
                                    File scriptFinal,
                                    String scriptInputPathFinal) {
        String command = host.buildInstallScriptCommand(scriptFinal, scriptInputPathFinal);
        host.runInstallScript(command, (exit, out, err) -> {
            setFileOpenResultIfNeeded(fromFileOpen, exit, out, err);
            if (exit == 0 && clearAfterSuccess) host.runOnUi(host::clearPickedSelectionUi);
            host.onInstallFinishedUi(exit, destPath, label, fromFileOpen);
        });
    }

    private File resolveInstallScript(boolean useScriptRequested) {
        try {
            return host.ensureInstallScriptOnExternal();
        } catch (Throwable ignored) {
            return useScriptRequested ? null : null;
        }
    }

    private void appendSourcePreparationNote(PackageInstallSourcePreparer.PreparedSource prepared) {
        if (prepared == null) return;
        if (!prepared.usedImportsCopy) {
            if (prepared.skippedByLargeFile) {
                host.appendOutput("[i] Skipping imports staging for large file (> 900 MB).\n");
            } else {
                host.appendOutput("[i] Using direct source path without imports staging.\n");
            }
        } else if (prepared.requestedDirectPath) {
            host.appendOutput("[i] Direct source path unavailable; using imports staging copy.\n");
        }
    }

    private void setFileOpenResultIfNeeded(boolean fromFileOpen, int exit, String out, String err) {
        if (fromFileOpen) host.setLastFileOpenInstallResult(exit, out, err);
    }

    private boolean isClearAfterInstallChecked() {
        try {
            ActivityMainBinding binding = host.getBinding();
            return binding == null
                    || binding.tabPackages == null
                    || binding.tabPackages.chkClearAfterInstall == null
                    || binding.tabPackages.chkClearAfterInstall.isChecked();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean isCreateSizeChecked() {
        try {
            ActivityMainBinding binding = host.getBinding();
            return binding != null
                    && binding.tabPackages != null
                    && binding.tabPackages.chkPmInstallCreateUseSize != null
                    && binding.tabPackages.chkPmInstallCreateUseSize.isChecked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void setInstallButtonEnabled(boolean enabled) {
        try {
            ActivityMainBinding binding = host.getBinding();
            if (binding != null && binding.tabPackages != null && binding.tabPackages.btnInstallPickedApk != null) {
                binding.tabPackages.btnInstallPickedApk.setEnabled(enabled);
            }
        } catch (Throwable ignored) {
        }
    }

    private void toast(String text) {
        try {
            Activity activity = host.getActivity();
            if (activity != null) Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private void debug(String message) {
        try {
            host.debug(PackageInstallDebug.Area.PACKAGE_TAB, message);
        } catch (Throwable ignored) {
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String safeJoin(String a, String b) {
        String aa = a == null ? "" : a;
        String bb = b == null ? "" : b;
        if (TextUtils.isEmpty(aa)) return bb;
        if (TextUtils.isEmpty(bb)) return aa;
        if (aa.endsWith("\n")) return aa + bb;
        return aa + "\n" + bb;
    }
}
