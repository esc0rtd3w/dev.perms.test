package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import dev.perms.test.ShizukuCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class NetworkFtpClientLocalFileOps {
    public interface ShizukuReadyProvider {
        boolean isReady();
    }

    private NetworkFtpClientLocalFileOps() {
    }

    public static boolean uploadViaShizuku(PermsTestFtpClient client,
                                           FtpClientLocalEntry local,
                                           ShizukuReadyProvider shizukuReady) throws Exception {
        if (client == null) throw new IOException("Connect FTP client first.");
        if (local == null || !local.file) throw new IOException("Select a local file first.");
        requireShizukuReady(shizukuReady);
        Process p = ShizukuCompat.newProcess(new String[]{"sh", "-c",
                "P=" + shQuote(local.path) + "; if [ -f \"$P\" ]; then cat \"$P\"; else exit 2; fi"}, null, null);
        boolean ok;
        try (InputStream in = p.getInputStream()) {
            ok = client.upload(local.name, in);
        }
        String err = readAll(p.getErrorStream());
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("Local Shizuku read failed (" + exit + "): " + NetworkFtpClientPaths.emptyToMessage(err));
        return ok;
    }

    public static boolean downloadViaShizuku(PermsTestFtpClient client,
                                             PermsTestFtpClient.RemoteEntry remote,
                                             String localDir,
                                             ShizukuReadyProvider shizukuReady) throws Exception {
        if (client == null) throw new IOException("Connect FTP client first.");
        if (remote == null || !remote.file) throw new IOException("Select a remote file first.");
        requireShizukuReady(shizukuReady);
        String target = NetworkFtpClientPaths.childOf(localDir, remote.name);
        String cmd = "P=" + shQuote(target) + "; D=${P%/*}; [ \"$D\" = \"$P\" ] && D=/; mkdir -p \"$D\" || exit 2; cat > \"$P\"";
        Process p = ShizukuCompat.newProcess(new String[]{"sh", "-c", cmd}, null, null);
        boolean ok;
        try (OutputStream out = p.getOutputStream()) {
            ok = client.download(remote, out);
        }
        String err = readAll(p.getErrorStream());
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("Local Shizuku write failed (" + exit + "): " + NetworkFtpClientPaths.emptyToMessage(err));
        return ok;
    }

    public static boolean deleteEntry(FtpClientLocalEntry entry,
                                      boolean useShizuku,
                                      ShizukuReadyProvider shizukuReady) throws Exception {
        if (entry == null || isEmpty(entry.path)) return false;
        if (useShizuku) {
            requireShizukuReady(shizukuReady);
            String cmd = "P=" + shQuote(entry.path) + "; if [ -d \"$P\" ]; then rmdir \"$P\"; elif [ -e \"$P\" ]; then rm -f \"$P\"; else exit 2; fi";
            ShellResult result = runShizukuCommand(cmd);
            return result.exitCode == 0;
        }
        File f = new File(entry.path);
        if (!f.exists()) return false;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null && children.length > 0) return false;
        }
        return f.delete();
    }

    public static boolean createFolderViaShizuku(String localDir,
                                                 String name,
                                                 ShizukuReadyProvider shizukuReady) throws Exception {
        requireShizukuReady(shizukuReady);
        String target = NetworkFtpClientPaths.childOf(localDir, name);
        ShellResult result = runShizukuCommand("mkdir -p " + shQuote(target));
        return result.exitCode == 0;
    }

    private static void requireShizukuReady(ShizukuReadyProvider shizukuReady) throws IOException {
        if (shizukuReady == null || !shizukuReady.isReady()) {
            throw new IOException("Shizuku file access is not ready.");
        }
    }

    private static ShellResult runShizukuCommand(String cmd) throws Exception {
        if (isEmpty(cmd)) return new ShellResult(-1, "", "Command is empty");
        Process p = ShizukuCompat.newProcess(new String[]{"sh", "-c", cmd.trim()}, null, null);
        String out = readAll(p.getInputStream());
        String err = readAll(p.getErrorStream());
        int code = p.waitFor();
        return new ShellResult(code, out, err);
    }

    private static String readAll(InputStream in) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String shQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    private static final class ShellResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ShellResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }
}
