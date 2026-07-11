package dev.perms.test.ladb;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal wrapper around the embedded LADB adb executable (libadb.so).
 *
 * This executes the native binary similarly to the upstream LADB app:
 * - pair / connect / disconnect
 * - adb shell sh -c "..."
 * - adb install / install-multiple
 */
public final class LadbClient {

    public static final String DEFAULT_HOST = "127.0.0.1";

    public static final class CmdResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public CmdResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }

        @NonNull @Override public String toString() {
            return "exit=" + exitCode + "\n" + stdout + (TextUtils.isEmpty(stderr) ? "" : ("\n--- stderr ---\n" + stderr));
        }
    }

    private final Context app;
    private File adbExe;

    public LadbClient(Context ctx) {
        this.app = ctx.getApplicationContext();
    }

    /** Ensure we have an executable adb binary available for ProcessBuilder. */
    public synchronized File ensureAdbExecutable() throws Exception {
        if (adbExe != null && adbExe.exists()) return adbExe;

        // Prefer executing directly from nativeLibraryDir. Many devices mount /data/user/0/.../files
        // with "noexec" which will cause EACCES when trying to run a copied binary from there.
        File src = new File(app.getApplicationInfo().nativeLibraryDir, "libadb.so");
        if (!src.exists()) {
            throw new IllegalStateException("libadb.so not found in nativeLibraryDir");
        }

        // Best-effort: mark executable (some ROMs strip exec bit when extracting).
        try { //noinspection ResultOfMethodCallIgnored
            src.setExecutable(true, true);
        } catch (Throwable ignored) {}

        adbExe = src;
        return src;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) {
            bos.write(buf, 0, r);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }

    public CmdResult rawAdb(@NonNull List<String> args) {
        try {
            ensureAdbExecutable();
            List<String> cmd = new ArrayList<>();
            cmd.add(adbExe.getAbsolutePath());
            cmd.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            // Provide sane HOME/TMPDIR similar to LADB.
            pb.environment().put("HOME", app.getFilesDir().getAbsolutePath());
            pb.environment().put("TMPDIR", app.getCacheDir().getAbsolutePath());
            Process p = pb.start();
            String out = readAll(p.getInputStream());
            String err = readAll(p.getErrorStream());
            int code = p.waitFor();
            return new CmdResult(code, out, err);
        } catch (Throwable t) {
            return new CmdResult(-1, "", t.toString());
        }
    }

    public CmdResult rawAdb(@NonNull String... args) {
        return rawAdb(Arrays.asList(args));
    }

    public CmdResult startServer() {
        return rawAdb("start-server");
    }

    public CmdResult pair(String host, int port, @NonNull String code) {
        return rawAdb("pair", host + ":" + port, code);
    }

    public CmdResult connect(String host, int port) {
        startServer();
        return rawAdb("connect", host + ":" + port);
    }

    public CmdResult disconnect(String host, int port) {
        return rawAdb("disconnect", host + ":" + port);
    }

    public CmdResult shellShC(@NonNull String shCmd) {
        // adb shell sh -c '<cmd>'
        return rawAdb("shell", "sh", "-c", shCmd);
    }

    public CmdResult install(@NonNull String apkPath) {
        return rawAdb("install", "-r", apkPath);
    }

    public CmdResult installMultiple(@NonNull List<String> paths) {
        List<String> args = new ArrayList<>();
        args.add("install-multiple");
        args.add("-r");
        args.addAll(paths);
        return rawAdb(args);
    }

    /**
     * Tokenize a user-entered "adb ..." command line into args.
     * Supports simple quotes (single/double).
     */
    public static List<String> tokenizeAdbArgs(@NonNull String full) {
        String s = full.trim();
        // Remove leading "adb" if present.
        if (s.startsWith("adb ")) s = s.substring(4).trim();
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    cur.append(c);
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
