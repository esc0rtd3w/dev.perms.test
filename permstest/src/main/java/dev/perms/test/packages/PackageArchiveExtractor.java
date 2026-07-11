package dev.perms.test.packages;

import android.text.TextUtils;

/** Extracts package archives with system unzip first and staged bundled unzip as fallback. */
public final class PackageArchiveExtractor {
    public interface ShellRunner {
        Result run(String command);
    }

    public interface BundledBinaryEnsurer {
        void ensure(String assetName) throws Exception;
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

    private final ShellRunner shellRunner;
    private final BundledBinaryEnsurer bundledBinaryEnsurer;
    private final String publicBinDir;

    public PackageArchiveExtractor(ShellRunner shellRunner,
                                   BundledBinaryEnsurer bundledBinaryEnsurer,
                                   String publicBinDir) {
        this.shellRunner = shellRunner;
        this.bundledBinaryEnsurer = bundledBinaryEnsurer;
        this.publicBinDir = publicBinDir == null ? "" : publicBinDir;
    }

    public Result extractArchiveToDir(String archivePath, String outDir) {
        try {
            if (bundledBinaryEnsurer != null) bundledBinaryEnsurer.ensure("unzip");
        } catch (Throwable ignored) {
        }

        String a = shellQuote(archivePath);
        String o = shellQuote(outDir);
        String staged = publicBinDir + "/unzip";

        String[] attempts = new String[]{
                "unzip -o " + a + " -d " + o,
                "/system/bin/unzip -o " + a + " -d " + o,
                "/system/xbin/unzip -o " + a + " -d " + o,
                "/vendor/bin/unzip -o " + a + " -d " + o,
                "/system_ext/bin/unzip -o " + a + " -d " + o,
                "/product/bin/unzip -o " + a + " -d " + o,
                "/odm/bin/unzip -o " + a + " -d " + o,
                "/apex/com.android.runtime/bin/unzip -o " + a + " -d " + o,
                "/apex/com.android.art/bin/unzip -o " + a + " -d " + o,
                "busybox unzip -o " + a + " -d " + o,
                "/system/bin/busybox unzip -o " + a + " -d " + o,
                "toybox unzip -d " + o + " " + a,
                "toybox unzip " + a + " -d " + o,
                "/system/bin/toybox unzip -d " + o + " " + a,
                "/system/bin/toybox unzip " + a + " -d " + o,
                staged + " -o " + a + " -d " + o,
                staged + " -d " + o + " " + a,
                staged + " " + a + " -d " + o
        };

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int last = 1;

        for (String cmd : attempts) {
            Result r = shellRunner == null ? new Result(1, "", "Shell runner is unavailable.") : shellRunner.run(cmd);
            out.append("[unzip] $ ").append(cmd).append("\n");
            if (!TextUtils.isEmpty(r.stdout)) out.append(r.stdout).append(r.stdout.endsWith("\n") ? "" : "\n");
            if (!TextUtils.isEmpty(r.stderr)) err.append(r.stderr).append(r.stderr.endsWith("\n") ? "" : "\n");
            last = r.exitCode;
            if (r.exitCode == 0) {
                out.append("[diag] unzip backend: ").append(cmd).append("\n");
                return new Result(0, out.toString(), err.toString());
            }
        }

        return new Result(last == 0 ? 1 : last,
                out.toString(),
                safeJoin(err.toString(), "unzip failed for: " + archivePath));
    }

    private static String shellQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\"'\"'") + "'";
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
