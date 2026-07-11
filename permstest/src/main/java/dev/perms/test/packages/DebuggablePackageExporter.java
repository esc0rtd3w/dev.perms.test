package dev.perms.test.packages;

import android.os.Environment;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import dev.perms.test.apk.ApkDebugToolHelper;

/**
 * Exports rebuilt/debuggable APK artifacts to the user-requested public path.
 *
 * The app-side copy is attempted first for normal shared-storage destinations.
 * Shell copy remains as a fallback for paths that the selected privileged backend
 * can write but normal app storage APIs cannot.
 */
public final class DebuggablePackageExporter {
    public interface ShellRunner {
        Result run(String command);
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

    private DebuggablePackageExporter() {
    }

    public static Result export(File signedPackage, String outputPath, ShellRunner shellRunner) {
        if (signedPackage == null || !signedPackage.isFile() || signedPackage.length() <= 0) {
            return new Result(1, "", "Signed package was not created or is empty.");
        }
        if (TextUtils.isEmpty(outputPath)) {
            outputPath = ApkDebugToolHelper.defaultOutputPath("package.apk");
        }

        StringBuilder notes = new StringBuilder();
        File javaDest = publicExternalFileForPath(outputPath);
        if (javaDest != null) {
            try {
                copyAndVerify(signedPackage, javaDest);
                try { javaDest.setReadable(true, false); } catch (Throwable ignored) {}
                // Do not ask MediaProvider to scan APK/APKM/APKS/XAPK exports by raw path.
                // On scoped-storage devices, MediaProvider can log a false permission error
                // for these package archives even though the app copy is complete and usable.
                runQuiet(shellRunner, "chmod 644 " + shQuote(outputPath) + " 2>/dev/null || true");
                return new Result(0, "[APK Debug] Export verified by app copy; size=" + javaDest.length() + " bytes.\n", "");
            } catch (Throwable t) {
                notes.append("[APK Debug] App-side export failed: ")
                        .append(t.getClass().getSimpleName()).append(": ")
                        .append(t.getMessage() == null ? "" : t.getMessage()).append('\n');
            }
        }

        // Shell copy is retained as a fallback, but success is only reported after an explicit
        // non-empty destination check. This prevents rotation-time UI restoration from showing
        // "Created" when the final file was not actually exported.
        try { signedPackage.setReadable(true, false); } catch (Throwable ignored) {}
        String copyCmd = "mkdir -p " + shQuote(shellParentDir(outputPath))
                + " && cp -f " + shQuote(signedPackage.getAbsolutePath()) + " " + shQuote(outputPath)
                + " && chmod 644 " + shQuote(outputPath)
                + " && test -s " + shQuote(outputPath)
                + " && echo '[APK Debug] Export verified by shell copy.'";
        Result copy = shellRunner == null ? null : shellRunner.run(copyCmd);
        if (copy != null && copy.exitCode == 0) {
            return new Result(0, notes.toString() + copy.stdout, copy.stderr);
        }

        StringBuilder err = new StringBuilder(notes);
        err.append("[APK Debug] Shell export failed.");
        if (copy != null) {
            err.append(" exit=").append(copy.exitCode);
            if (!TextUtils.isEmpty(copy.stdout)) err.append("\n").append(copy.stdout.trim());
            if (!TextUtils.isEmpty(copy.stderr)) err.append("\n").append(copy.stderr.trim());
        }
        err.append('\n');
        return new Result(copy == null ? 1 : copy.exitCode, "", err.toString());
    }

    private static void copyAndVerify(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create " + parent.getAbsolutePath());
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(destination, false))) {
            byte[] buf = new byte[128 * 1024];
            int read;
            while ((read = in.read(buf)) > 0) out.write(buf, 0, read);
            out.flush();
        }
        if (!destination.isFile() || destination.length() <= 0) {
            throw new IOException("Exported file is empty after Java copy.");
        }
        if (destination.length() != source.length()) {
            throw new IOException("Exported size mismatch: " + destination.length() + " != " + source.length());
        }
    }

    private static void runQuiet(ShellRunner shellRunner, String command) {
        try { if (shellRunner != null) shellRunner.run(command); } catch (Throwable ignored) {}
    }

    private static File publicExternalFileForPath(String path) {
        if (TextUtils.isEmpty(path)) return null;
        try {
            String external = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (path.startsWith("/sdcard/")) {
                return new File(external + path.substring("/sdcard".length()));
            }
            if (path.equals("/sdcard")) {
                return new File(external);
            }
            if (path.startsWith("/storage/emulated/0/")) {
                return new File(external + path.substring("/storage/emulated/0".length()));
            }
            if (path.equals("/storage/emulated/0")) {
                return new File(external);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String shellParentDir(String path) {
        if (TextUtils.isEmpty(path)) return ApkDebugToolHelper.DEFAULT_EXPORT_DIR;
        int slash = path.lastIndexOf('/');
        if (slash <= 0) return ApkDebugToolHelper.DEFAULT_EXPORT_DIR;
        return path.substring(0, slash);
    }

    private static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}
