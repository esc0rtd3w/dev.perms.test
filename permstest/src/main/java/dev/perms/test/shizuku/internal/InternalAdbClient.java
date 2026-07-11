package dev.perms.test.shizuku.internal;

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
 * Internal Shizuku-only adb wrapper.
 *
 * IMPORTANT: This intentionally does NOT modify LadbClient or any LADB flows.
 * It runs the same embedded adb binary (libadb.so) but uses an isolated adb-server port
 * and HOME directory so it can run concurrently and avoid protocol/daemon conflicts.
 */
public final class InternalAdbClient {

    // Avoid the default 5037 which can conflict / be blocked on-device.
    private static final String INTERNAL_ADB_SERVER_PORT = "5039";

    public static final class CmdResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public CmdResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private final Context app;
    private File adbExe;

    public InternalAdbClient(Context ctx) {
        this.app = ctx.getApplicationContext();
    }

    private synchronized File ensureAdbExecutable() throws Exception {
        if (adbExe != null && adbExe.exists()) return adbExe;
        File src = new File(app.getApplicationInfo().nativeLibraryDir, "libadb.so");
        if (!src.exists()) throw new IllegalStateException("libadb.so not found");
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
        while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
        return bos.toString(StandardCharsets.UTF_8);
    }

    public CmdResult rawAdb(@NonNull List<String> args) {
        try {
            ensureAdbExecutable();
            List<String> cmd = new ArrayList<>();
            cmd.add(adbExe.getAbsolutePath());
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);

            // Isolated HOME so the internal adb server and keys don't clash with other flows.
            File home = app.getDir("internal_adb_home", Context.MODE_PRIVATE);
            File tmp = new File(home, "tmp");
            //noinspection ResultOfMethodCallIgnored
            tmp.mkdirs();

            pb.environment().put("HOME", home.getAbsolutePath());
            pb.environment().put("TMPDIR", tmp.getAbsolutePath());

            // Run adb server on an alternate port (avoid 5037).
            pb.environment().put("ADB_SERVER_PORT", INTERNAL_ADB_SERVER_PORT);

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

    public CmdResult killServer() {
        return rawAdb("kill-server");
    }

    public CmdResult pair(String host, int port, @NonNull String code) {
        // Ensure server is up on our isolated port.
        startServer();
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
        return rawAdb("shell", "sh", "-c", shCmd);
    }

    public static boolean looksOk(CmdResult r) {
        return r != null && r.exitCode == 0;
    }

    public static String summarize(CmdResult r) {
        if (r == null) return "null";
        String out = (r.stdout == null) ? "" : r.stdout.trim();
        String err = (r.stderr == null) ? "" : r.stderr.trim();
        String msg = "exit=" + r.exitCode;
        if (!TextUtils.isEmpty(err)) msg += " stderr=" + oneLine(err);
        else if (!TextUtils.isEmpty(out)) msg += " stdout=" + oneLine(out);
        return msg;
    }

    private static String oneLine(String s) {
        s = s.replace("\n", " ").replace("\r", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
