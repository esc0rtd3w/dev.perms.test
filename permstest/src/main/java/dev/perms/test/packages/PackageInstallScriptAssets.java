package dev.perms.test.packages;

import android.content.Context;

import dev.perms.test.scripts.ScriptsCatalog;
import dev.perms.test.shell.ShellBinaryAssets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Stages package-install helper scripts and the optional bundled unzip binary.
 */
public final class PackageInstallScriptAssets {
    public interface OutputSink {
        void append(String text);
    }

    public interface ShellCommandRunner {
        void run(String command);
    }

    public interface BundledAssetDirsProvider {
        String[] getBundledAssetDirs();
    }

    private static final String EXT_SCRIPTS_DIR = "scripts";
    private final Context context;
    private final OutputSink outputSink;
    private final ShellCommandRunner shellCommandRunner;
    private final BundledAssetDirsProvider bundledAssetDirsProvider;

    public PackageInstallScriptAssets(Context context,
                                      OutputSink outputSink,
                                      ShellCommandRunner shellCommandRunner,
                                      BundledAssetDirsProvider bundledAssetDirsProvider) {
        this.context = context;
        this.outputSink = outputSink;
        this.shellCommandRunner = shellCommandRunner;
        this.bundledAssetDirsProvider = bundledAssetDirsProvider;
    }

    public File ensureAssetScriptOnExternal(String name) {
        try {
            if (name == null || context == null) return null;

            // Stage the bundled helper script into the app scripts directory.
            File dir = context.getExternalFilesDir(EXT_SCRIPTS_DIR);
            if (dir == null) return null;
            if (!dir.exists()) dir.mkdirs();

            File out = new File(dir, name);

            // The bundled helper script source of truth is assets/scripts.
            // Copy it fresh whenever it is requested; custom script support should use
            // a separate explicit custom-script path instead of guessing from file contents.
            String assetPath = ScriptsCatalog.ASSET_SCRIPTS_DIR + "/" + name;
            try (InputStream in = context.getAssets().open(assetPath);
                 FileOutputStream fos = new FileOutputStream(out, false)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                fos.flush();
            }
            try { ensureBundledUnzip(dir); } catch (Throwable ignored) {}
            return out;
        } catch (Throwable t) {
            appendOutput("[!] Ensure asset script failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            return null;
        }
    }


    private void ensureBundledUnzip(File scriptsDir) {
        try {
            if (scriptsDir == null || context == null) return;
            File out = new File(scriptsDir, "unzip");
            if (out.exists() && out.length() > 0) {
                try { out.setExecutable(true, false); } catch (Throwable ignored) {}
                // Ensure a copy exists in /data/local/tmp as well (exec-safe), even if the script dir already has it.
                stageUnzipPublic(out);
                return;
            }

            // Optional: user-supplied unzip binary. Prefer ABI-specific assets/bin/<abi>/unzip when present,
            // otherwise fall back to assets/bin/unzip.
            InputStream in = null;
            try {
                String[] dirs = bundledAssetDirsProvider == null ? null : bundledAssetDirsProvider.getBundledAssetDirs();
                if (dirs != null) {
                    for (String d : dirs) {
                        try {
                            in = context.getAssets().open(d + "/unzip");
                            break;
                        } catch (Throwable ignored) {
                            in = null;
                        }
                    }
                }
                if (in == null) return; // no bundled unzip provided
                try (FileOutputStream fos = new FileOutputStream(out, false)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                    fos.flush();
                }
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) {}
            }
            try { out.setReadable(true, false); } catch (Throwable ignored) {}
            try { out.setWritable(true, false); } catch (Throwable ignored) {}
            try { out.setExecutable(true, false); } catch (Throwable ignored) {}
            // Best-effort chmod (requires Shizuku); ignore failures.
            stageUnzipPublic(out);
        } catch (Throwable ignored) {
            // Asset may not exist; that's fine.
        }
    }

    private void stageUnzipPublic(File out) {
        try {
            if (out == null || shellCommandRunner == null) return;
            String chmod = "chmod 755 " + shQuote(out.getAbsolutePath()) + " 2>/dev/null || true";
            shellCommandRunner.run(chmod);

            // Best-effort: stage to /data/local/tmp so scripts can execute even if external storage is mounted noexec.
            String dst = ShellBinaryAssets.PUBLIC_BIN_DIR + "/unzip";
            String stage = "mkdir -p " + shQuote(ShellBinaryAssets.PUBLIC_BIN_DIR)
                    + " && cp " + shQuote(out.getAbsolutePath()) + " " + shQuote(dst)
                    + " && chmod 755 " + shQuote(dst) + " 2>/dev/null || true";
            shellCommandRunner.run(stage);
        } catch (Throwable ignored) {}
    }

    private void appendOutput(String text) {
        try {
            if (outputSink != null) outputSink.append(text);
        } catch (Throwable ignored) {}
    }

    private static String shQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
