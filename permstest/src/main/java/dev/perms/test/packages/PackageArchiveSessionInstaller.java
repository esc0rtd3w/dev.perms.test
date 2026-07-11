package dev.perms.test.packages;

import dev.perms.test.debug.PackageInstallDebug;

import android.app.Activity;
import android.os.Build;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the built-in package-session install path for APKS/APKM/XAPK/ZIP archives.
 *
 * <p>The UI and shell backend stay owned by the caller. This helper owns only the
 * package-archive session flow so MainActivity does not carry the split-selection
 * and pm install-write details directly.</p>
 */
public final class PackageArchiveSessionInstaller {
    public interface ShellRunner {
        Result run(String command);
    }

    public interface ArchiveExtractor {
        Result extract(String archivePath, String outputDirectory);
    }

    public interface PathPreparer {
        String prepareForPackageManager(String path);
    }

    public interface InstallCreateCommandBuilder {
        String build(String sizeBytes);
    }

    public interface CustomSplitOptionsProvider {
        boolean isCustomSplitSelectionEnabled();
    }

    public interface DebugSink {
        void log(String message);
    }

    public static final class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private final Activity activity;
    private final String stageRoot;
    private final String publicFilesDir;
    private final String publicBinDir;
    private final ShellRunner shellRunner;
    private final ArchiveExtractor archiveExtractor;
    private final PathPreparer pathPreparer;
    private final InstallCreateCommandBuilder installCreateCommandBuilder;
    private final PackageArchiveSplitSelector.SizeProvider sizeProvider;
    private final CustomSplitOptionsProvider customSplitOptionsProvider;
    private final DebugSink debugSink;

    public PackageArchiveSessionInstaller(Activity activity,
                                          String stageRoot,
                                          String publicFilesDir,
                                          String publicBinDir,
                                          ShellRunner shellRunner,
                                          ArchiveExtractor archiveExtractor,
                                          PathPreparer pathPreparer,
                                          InstallCreateCommandBuilder installCreateCommandBuilder,
                                          PackageArchiveSplitSelector.SizeProvider sizeProvider,
                                          CustomSplitOptionsProvider customSplitOptionsProvider,
                                          DebugSink debugSink) {
        this.activity = activity;
        this.stageRoot = stageRoot;
        this.publicFilesDir = publicFilesDir;
        this.publicBinDir = publicBinDir;
        this.shellRunner = shellRunner;
        this.archiveExtractor = archiveExtractor;
        this.pathPreparer = pathPreparer;
        this.installCreateCommandBuilder = installCreateCommandBuilder;
        this.sizeProvider = sizeProvider;
        this.customSplitOptionsProvider = customSplitOptionsProvider;
        this.debugSink = debugSink;
    }

    public Result install(String archivePath) {
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();

        debug("begin originalArchive=" + PackageInstallDebug.describePath(archivePath));
        archivePath = pathPreparer == null ? archivePath : pathPreparer.prepareForPackageManager(archivePath);
        debug("preparedArchive=" + PackageInstallDebug.describePath(archivePath));

        // Keep staging outside the bin directory so cleanup never touches installed tools.
        try { runShell("rm -rf " + shQuote(stageRoot) + "/pkg_*" + " 2>/dev/null || true"); } catch (Throwable ignored) {}
        final String stageDir = stageRoot + "/pkg_" + System.currentTimeMillis();
        debug("stageDir=" + stageDir);

        try {
            Result mk = runShell("mkdir -p " + shQuote(stageDir) + " 2>/dev/null || true");
            debug("mkdir exit=" + mk.exitCode
                    + ", stdoutLen=" + (mk.stdout == null ? 0 : mk.stdout.length())
                    + ", stderrLen=" + (mk.stderr == null ? 0 : mk.stderr.length()));
            appendWithNewline(out, mk.stdout);
            appendWithNewline(err, mk.stderr);

            debug("extract begin");
            Result ex = archiveExtractor == null
                    ? new Result(1, "", "Archive extractor is unavailable.")
                    : archiveExtractor.extract(archivePath, stageDir);
            debug("extract exit=" + ex.exitCode
                    + ", stdoutLen=" + (ex.stdout == null ? 0 : ex.stdout.length())
                    + ", stderrLen=" + (ex.stderr == null ? 0 : ex.stderr.length()));
            out.append(ex.stdout);
            appendWithNewline(err, ex.stderr);
            if (ex.exitCode != 0) {
                return new Result(ex.exitCode, out.toString(), safeJoin(err.toString(), "[install] Extract failed."));
            }

            debug("apk list direct begin");
            Result ls1 = runShell("ls -1 " + shQuote(stageDir) + "/*.apk 2>/dev/null || true");
            debug("apk list direct exit=" + ls1.exitCode
                    + ", stdoutLen=" + (ls1.stdout == null ? 0 : ls1.stdout.length())
                    + ", stderrLen=" + (ls1.stderr == null ? 0 : ls1.stderr.length()));
            String apks = (ls1.stdout == null) ? "" : ls1.stdout.trim();
            if (TextUtils.isEmpty(apks)) {
                debug("apk list find begin");
                Result ls2 = runShell("find " + shQuote(stageDir) + " -maxdepth 3 -type f -name '*.apk' 2>/dev/null || true");
                debug("apk list find exit=" + ls2.exitCode
                        + ", stdoutLen=" + (ls2.stdout == null ? 0 : ls2.stdout.length())
                        + ", stderrLen=" + (ls2.stderr == null ? 0 : ls2.stderr.length()));
                apks = (ls2.stdout == null) ? "" : ls2.stdout.trim();
                appendWithNewline(out, ls2.stdout);
                appendWithNewline(err, ls2.stderr);
            } else {
                appendWithNewline(out, ls1.stdout);
                appendWithNewline(err, ls1.stderr);
            }

            if (TextUtils.isEmpty(apks)) {
                return new Result(1, out.toString(), safeJoin(err.toString(), "[install] No APKs found in archive."));
            }

            appendApkDiagnostics(out, apks);

            List<String> allApks = parseApkList(apks);
            debug("all staged APKs: " + PackageInstallDebug.describePathList(allApks, 30));
            String base = PackageArchiveSplitSelector.selectBestBaseApk(allApks, sizeProvider);
            debug("selected base=" + PackageInstallDebug.describePath(base));

            boolean customSplit = false;
            try { customSplit = customSplitOptionsProvider != null && customSplitOptionsProvider.isCustomSplitSelectionEnabled(); }
            catch (Throwable ignored) { customSplit = false; }
            debug("split selector begin customSplit=" + customSplit
                    + ", activityFinishing=" + (activity != null && activity.isFinishing())
                    + ", activityDestroyed=" + (activity != null && Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()));
            List<String> selectedApks = PackageArchiveSplitSelector.selectForDevice(activity, allApks, base, customSplit);
            debug("split selector result: " + PackageInstallDebug.describePathList(selectedApks, 30));
            if (selectedApks == null || selectedApks.isEmpty()) {
                return new Result(1, out.toString(), safeJoin(err.toString(), "[install] Cancelled or no APKs selected."));
            }
            out.append("[diag] Base APK: ").append(base).append("\n");

            long sessionSize = totalSessionSize(selectedApks);
            if (sessionSize <= 0) {
                // Fallback to a small guess; pm install-create can sometimes still work.
                sessionSize = 1024 * 1024;
            }

            debug("install-create begin sessionSize=" + sessionSize);
            Result r1 = runShell(buildInstallCreateCommand(String.valueOf(sessionSize)));
            debug("install-create exit=" + r1.exitCode
                    + ", stdout=" + (r1.stdout == null ? "" : r1.stdout.trim())
                    + ", stderr=" + (r1.stderr == null ? "" : r1.stderr.trim()));
            String sid = extractInstallSessionId(r1.stdout + "\n" + r1.stderr);
            if (TextUtils.isEmpty(sid) || r1.exitCode != 0) {
                return new Result(r1.exitCode != 0 ? r1.exitCode : 1,
                        safeJoin(out.toString(), safeJoin(r1.stdout, r1.stderr)),
                        safeJoin(err.toString(), "[install] Failed to create install session."));
            }

            out.append("[diag] Install session: ").append(sid).append("\n");

            int part = 0;
            for (String entry : selectedApks) {
                String path = entry == null ? "" : entry.trim();
                if (TextUtils.isEmpty(path)) continue;
                String name = path.equals(base) ? "base.apk" : ("split" + (part++) + ".apk");
                long size = sizeOf(path);
                if (size <= 0) size = 1024 * 1024;
                debug("install-write begin name=" + name + ", size=" + size + ", source=" + PackageInstallDebug.describePath(path));
                Result wr = runShell("pm install-write -S " + size + " " + sid + " " + name + " " + shQuote(path));
                debug("install-write exit=" + wr.exitCode
                        + ", name=" + name
                        + ", stdout=" + (wr.stdout == null ? "" : wr.stdout.trim())
                        + ", stderr=" + (wr.stderr == null ? "" : wr.stderr.trim()));
                out.append(wr.stdout);
                appendWithNewline(err, wr.stderr);
                if (wr.exitCode != 0) {
                    runShell("pm install-abandon " + sid + " 2>/dev/null || true");
                    return new Result(wr.exitCode, out.toString(), safeJoin(err.toString(), "[install] install-write failed."));
                }
            }

            debug("install-commit begin sid=" + sid);
            Result cm = runShell("pm install-commit " + sid);
            debug("install-commit exit=" + cm.exitCode
                    + ", stdout=" + (cm.stdout == null ? "" : cm.stdout.trim())
                    + ", stderr=" + (cm.stderr == null ? "" : cm.stderr.trim()));
            out.append(cm.stdout);
            appendWithNewline(err, cm.stderr);

            if (cm.exitCode == 0) {
                return new Result(0, out.toString(), err.toString());
            }
            return new Result(cm.exitCode, out.toString(), safeJoin(err.toString(), "[install] install-commit failed."));
        } finally {
            debug("cleanup begin stageDir=" + stageDir);
            try { runShell("rm -rf " + shQuote(stageDir) + " 2>/dev/null || true"); } catch (Throwable ignored) {}
            try { runShell("rm -rf " + shQuote(stageRoot) + "/pkg_*" + " 2>/dev/null || true"); } catch (Throwable ignored) {}
            try { runShell("rm -rf " + shQuote(publicFilesDir) + "/*" + " 2>/dev/null || true"); } catch (Throwable ignored) {}
            try { runShell("rm -rf " + shQuote(publicBinDir + "/stage") + " 2>/dev/null || true"); } catch (Throwable ignored) {}
        }
    }

    private Result runShell(String command) {
        if (shellRunner == null) return new Result(1, "", "Shell runner is unavailable.");
        Result result = shellRunner.run(command);
        return result == null ? new Result(1, "", "Shell runner returned no result.") : result;
    }

    private String buildInstallCreateCommand(String sizeBytes) {
        if (installCreateCommandBuilder == null) {
            return "echo 'Package install-create command is unavailable' >&2; exit 1";
        }
        return installCreateCommandBuilder.build(sizeBytes);
    }

    private long totalSessionSize(List<String> selectedApks) {
        long sessionSize = 0;
        if (selectedApks == null) return 0;
        for (String entry : selectedApks) {
            String path = entry == null ? "" : entry.trim();
            if (TextUtils.isEmpty(path)) continue;
            long size = sizeOf(path);
            if (size > 0) sessionSize += size;
        }
        return sessionSize;
    }

    private long sizeOf(String path) {
        try { return sizeProvider == null ? -1L : sizeProvider.sizeOf(path); }
        catch (Throwable ignored) { return -1L; }
    }

    private void debug(String message) {
        try {
            if (debugSink != null) debugSink.log(message);
        } catch (Throwable ignored) {}
    }

    private static List<String> parseApkList(String apks) {
        String[] lines = apks == null ? new String[0] : apks.split("\n");
        List<String> allApks = new ArrayList<>();
        for (String line : lines) {
            String path = line == null ? "" : line.trim();
            if (TextUtils.isEmpty(path)) continue;
            allApks.add(path);
        }
        return allApks;
    }

    private static void appendApkDiagnostics(StringBuilder out, String apks) {
        try {
            String[] discovered = apks.split("\n");
            int total = 0;
            for (String value : discovered) {
                if (!TextUtils.isEmpty(value != null ? value.trim() : "")) total++;
            }
            out.append("[diag] Found ").append(total).append(" APK(s) in archive:\n");
            int shown = 0;
            for (String value : discovered) {
                String path = value == null ? "" : value.trim();
                if (TextUtils.isEmpty(path)) continue;
                out.append("  ").append(path).append("\n");
                shown++;
                if (shown >= 20) break;
            }
            if (total > shown) out.append("  ... (+").append(total - shown).append(" more)\n");
        } catch (Throwable ignored) {}
    }

    private static void appendWithNewline(StringBuilder target, String value) {
        if (target == null || TextUtils.isEmpty(value)) return;
        target.append(value).append(value.endsWith("\n") ? "" : "\n");
    }

    private static String safeJoin(String a, String b) {
        String aa = a == null ? "" : a;
        String bb = b == null ? "" : b;
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

    private static String shQuote(String value) {
        return PackageInstallCommands.shQuote(value);
    }
}
