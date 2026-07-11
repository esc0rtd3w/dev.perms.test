package dev.perms.test.packages;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the built-in package-session install path for a single APK.
 *
 * <p>The caller still owns UI state, shell backend selection, and install-source staging.
 * This helper owns the pm install-create/install-write/install-commit sequencing so
 * MainActivity does not carry package-manager session details directly.</p>
 */
public final class PackageStandaloneSessionInstaller {
    public interface ShellRunner {
        void run(String command, ResultCallback callback);
    }

    public interface PathPreparer {
        void prepare(String apkPath, PathCallback callback);
    }

    public interface InstallCreateCommandBuilder {
        String build(String sizeBytes);
    }

    public interface OutputSink {
        void append(String text);
    }

    public interface ResultCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface PathCallback {
        void onReady(String preparedPath);
    }

    private final PathPreparer pathPreparer;
    private final ShellRunner shellRunner;
    private final InstallCreateCommandBuilder installCreateCommandBuilder;
    private final OutputSink outputSink;

    public PackageStandaloneSessionInstaller(PathPreparer pathPreparer,
                                             ShellRunner shellRunner,
                                             InstallCreateCommandBuilder installCreateCommandBuilder,
                                             OutputSink outputSink) {
        this.pathPreparer = pathPreparer;
        this.shellRunner = shellRunner;
        this.installCreateCommandBuilder = installCreateCommandBuilder;
        this.outputSink = outputSink;
    }

    /**
     * Installs one APK through discrete pm session commands.
     *
     * <p>The discrete command sequence intentionally mirrors the previous MainActivity
     * flow and keeps OEM install-create output variations handled by session-id parsing.</p>
     */
    public void install(String apkPath, boolean useCreateSize, ResultCallback callback) {
        try {
            preparePath(apkPath, readyPath -> runPreparedInstall(readyPath, useCreateSize, callback));
        } catch (Throwable t) {
            completeWithError(callback, 1, "", t.toString());
        }
    }

    private void runPreparedInstall(String preparedPath, boolean useCreateSize, ResultCallback callback) {
        try {
            final String quotedPath = PackageInstallCommands.shQuote(preparedPath);
            if (useCreateSize) {
                runWithCreateSizeFirst(quotedPath, callback);
            } else {
                runWithCreateBeforeSize(quotedPath, callback);
            }
        } catch (Throwable t) {
            completeWithError(callback, 1, "", t.toString());
        }
    }

    private void runWithCreateSizeFirst(String quotedPath, ResultCallback callback) {
        runShell("stat -c %s " + quotedPath, (sizeExit, sizeOut, sizeErr) -> {
            final String size = safe(sizeOut).trim();
            if (sizeExit != 0 || TextUtils.isEmpty(size) || !TextUtils.isDigitsOnly(size)) {
                String msg = "Failed to get APK size\n" + safe(sizeOut) + "\n" + safe(sizeErr);
                completeWithError(callback, sizeExit != 0 ? sizeExit : 1, safe(sizeOut), msg);
                return;
            }

            runInstallCreate(size, (createExit, sid, createOut, createErr) -> {
                if (createExit != 0 || TextUtils.isEmpty(sid)) {
                    String combined = safeJoin(createOut, createErr);
                    String msg = createExit != 0
                            ? ("pm install-create failed\n" + combined)
                            : ("Failed to parse install session id\n" + combined);
                    completeWithError(callback, createExit != 0 ? createExit : 1, safe(createOut), msg);
                    return;
                }

                runInstallWriteAndCommit(quotedPath, size, sid, callback);
            });
        });
    }

    private void runWithCreateBeforeSize(String quotedPath, ResultCallback callback) {
        runInstallCreate(null, (createExit, sid, createOut, createErr) -> {
            if (createExit != 0 || TextUtils.isEmpty(sid)) {
                String combined = safeJoin(createOut, createErr);
                String msg = createExit != 0
                        ? ("pm install-create failed\n" + combined)
                        : ("Failed to parse install session id\n" + combined);
                completeWithError(callback, createExit != 0 ? createExit : 1, safe(createOut), msg);
                return;
            }

            runShell("stat -c %s " + quotedPath, (sizeExit, sizeOut, sizeErr) -> {
                final String size = safe(sizeOut).trim();
                if (sizeExit != 0 || TextUtils.isEmpty(size) || !TextUtils.isDigitsOnly(size)) {
                    String msg = "Failed to get APK size\n" + safe(sizeOut) + "\n" + safe(sizeErr);
                    completeWithError(callback, sizeExit != 0 ? sizeExit : 1, safe(sizeOut), msg);
                    return;
                }

                runInstallWriteAndCommit(quotedPath, size, sid, callback);
            });
        });
    }

    private void runInstallCreate(String size, InstallCreateCallback callback) {
        String command = buildInstallCreateCommand(size);
        runShell(command, (exit, stdout, stderr) -> {
            String combined = safe(stdout) + "\n" + safe(stderr);
            String sid = extractInstallSessionId(combined);
            if (callback != null) callback.onComplete(exit, sid, safe(stdout), safe(stderr));
        });
    }

    private void runInstallWriteAndCommit(String quotedPath, String size, String sid, ResultCallback callback) {
        final String write = "pm install-write -S " + size + " " + sid + " base.apk " + quotedPath;
        runShell(write, (writeExit, writeOut, writeErr) -> {
            if (writeExit != 0) {
                String msg = "pm install-write failed\n" + safe(writeOut) + "\n" + safe(writeErr);
                completeWithError(callback, writeExit, safe(writeOut), msg);
                return;
            }

            runShell("pm install-commit " + sid, (commitExit, commitOut, commitErr) -> {
                if (callback != null) callback.onComplete(commitExit, safe(commitOut), safe(commitErr));
            });
        });
    }

    private void preparePath(String apkPath, PathCallback callback) {
        if (pathPreparer == null) {
            if (callback != null) callback.onReady(apkPath);
            return;
        }
        pathPreparer.prepare(apkPath, preparedPath -> {
            if (callback != null) callback.onReady(preparedPath);
        });
    }

    private void runShell(String command, ResultCallback callback) {
        if (shellRunner == null) {
            if (callback != null) callback.onComplete(1, "", "Shell runner is unavailable.");
            return;
        }
        try {
            shellRunner.run(command, (exit, stdout, stderr) -> {
                if (callback != null) callback.onComplete(exit, safe(stdout), safe(stderr));
            });
        } catch (Throwable t) {
            if (callback != null) callback.onComplete(1, "", t.toString());
        }
    }

    private String buildInstallCreateCommand(String size) {
        if (installCreateCommandBuilder == null) {
            return "echo 'Package install-create command is unavailable' >&2; exit 1";
        }
        return installCreateCommandBuilder.build(size);
    }

    private void completeWithError(ResultCallback callback, int exit, String stdout, String message) {
        appendOutput("[!] " + safe(message) + "\n");
        if (callback != null) callback.onComplete(exit, safe(stdout), safe(message));
    }

    private void appendOutput(String text) {
        try {
            if (outputSink != null) outputSink.append(text);
        } catch (Throwable ignored) {}
    }

    private interface InstallCreateCallback {
        void onComplete(int exitCode, String sessionId, String stdout, String stderr);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeJoin(String a, String b) {
        String aa = safe(a);
        String bb = safe(b);
        if (TextUtils.isEmpty(aa)) return bb;
        if (TextUtils.isEmpty(bb)) return aa;
        if (aa.endsWith("\n")) return aa + bb;
        return aa + "\n" + bb;
    }

    private static String extractInstallSessionId(String text) {
        try {
            if (text == null) return "";
            Matcher matcher = Pattern.compile("\\[(\\d+)\\]").matcher(text);
            if (matcher.find()) return matcher.group(1);
            matcher = Pattern.compile("(\\d+)").matcher(text);
            String last = null;
            while (matcher.find()) last = matcher.group(1);
            return last == null ? "" : last;
        } catch (Throwable ignored) {
            return "";
        }
    }
}
