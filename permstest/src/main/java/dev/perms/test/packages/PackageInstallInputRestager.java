package dev.perms.test.packages;

import android.text.TextUtils;

import java.io.File;

/**
 * Handles install-input restaging before handing shared-storage files to pm.
 */
public final class PackageInstallInputRestager {
    public interface RestagePolicy {
        boolean shouldRestage(String path);
    }

    public interface AsyncShellRunner {
        void run(String command, Completion callback);
    }

    public interface SyncShellRunner {
        ShellResult run(String command);
    }

    public interface OutputSink {
        void append(String text);
    }

    public interface DebugSink {
        void log(String message);
    }

    public interface Completion {
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

    private final String publicFilesDir;
    private final RestagePolicy restagePolicy;
    private final AsyncShellRunner asyncShellRunner;
    private final SyncShellRunner syncShellRunner;
    private final OutputSink outputSink;
    private final DebugSink debugSink;

    public PackageInstallInputRestager(String publicFilesDir,
                                       RestagePolicy restagePolicy,
                                       AsyncShellRunner asyncShellRunner,
                                       SyncShellRunner syncShellRunner,
                                       OutputSink outputSink,
                                       DebugSink debugSink) {
        this.publicFilesDir = publicFilesDir;
        this.restagePolicy = restagePolicy;
        this.asyncShellRunner = asyncShellRunner;
        this.syncShellRunner = syncShellRunner;
        this.outputSink = outputSink;
        this.debugSink = debugSink;
    }

    public String buildRestageInstallInputCommand(String sourcePath, String stagedPath) {
        return PackageInstallCommands.buildRestageInstallInputCommand(publicFilesDir, sourcePath, stagedPath);
    }

    public void prepareAsync(String sourcePath, PathCallback callback) {
        if (!shouldRestage(sourcePath)) {
            if (callback != null) callback.onReady(sourcePath);
            return;
        }

        final String stagedPath = buildStagedPath(sourcePath);
        final String cmd = buildRestageInstallInputCommand(sourcePath, stagedPath);
        if (asyncShellRunner == null) {
            appendOutput("[i] /data/local/tmp restage failed; using original install path.\n");
            if (callback != null) callback.onReady(sourcePath);
            return;
        }

        asyncShellRunner.run(cmd, (exitCode, stdout, stderr) -> {
            if (exitCode == 0) {
                appendOutput("[i] Restaged install input to /data/local/tmp for pm access.\n");
                if (callback != null) callback.onReady(stagedPath);
                return;
            }

            appendOutput("[i] /data/local/tmp restage failed; using original install path.\n");
            if (callback != null) callback.onReady(sourcePath);
        });
    }

    public String prepareSync(String sourcePath) {
        if (!shouldRestage(sourcePath)) {
            return sourcePath;
        }

        final String stagedPath = buildStagedPath(sourcePath);
        debug("restage begin source="
                + PackageInstallDebug.describePath(sourcePath)
                + ", staged=" + stagedPath);

        ShellResult result = syncShellRunner == null
                ? new ShellResult(-1, "", "No sync shell runner")
                : syncShellRunner.run(buildRestageInstallInputCommand(sourcePath, stagedPath));

        debug("restage exit=" + (result == null ? -1 : result.exitCode)
                + ", stdoutLen=" + (result == null || result.stdout == null ? 0 : result.stdout.length())
                + ", stderrLen=" + (result == null || result.stderr == null ? 0 : result.stderr.length())
                + ", staged=" + PackageInstallDebug.describePath(stagedPath));

        if (result != null && result.exitCode == 0) {
            appendOutput("[i] Restaged install input to /data/local/tmp for pm access.\n");
            return stagedPath;
        }

        appendOutput("[i] /data/local/tmp restage failed; using original install path.\n");
        return sourcePath;
    }

    private boolean shouldRestage(String sourcePath) {
        try {
            return restagePolicy != null && restagePolicy.shouldRestage(sourcePath);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String buildStagedPath(String sourcePath) {
        String name = "package.bin";
        try {
            String raw = new File(sourcePath == null ? "" : sourcePath).getName();
            if (!TextUtils.isEmpty(raw)) name = sanitizeFilename(raw);
        } catch (Throwable ignored) {}
        return publicFilesDir + "/" + System.currentTimeMillis() + "_" + name;
    }

    private static String sanitizeFilename(String name) {
        if (name == null) return "file.bin";
        String out = name.trim();
        if (out.isEmpty()) out = "file.bin";
        out = out.replaceAll("[\\/\r\n\t\0]", "_");
        out = out.replaceAll("[^a-zA-Z0-9._ -]", "_");
        if (out.length() > 128) out = out.substring(out.length() - 128);
        return out;
    }

    private void appendOutput(String text) {
        try {
            if (outputSink != null) outputSink.append(text);
        } catch (Throwable ignored) {}
    }

    private void debug(String message) {
        try {
            if (debugSink != null) debugSink.log(message);
        } catch (Throwable ignored) {}
    }
}
