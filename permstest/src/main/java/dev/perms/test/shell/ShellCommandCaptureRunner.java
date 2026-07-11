package dev.perms.test.shell;

import android.text.TextUtils;

import dev.perms.test.ExecMode;
import dev.perms.test.ShizukuCompat;
import dev.perms.test.ladb.LadbClient;
import dev.perms.test.ladb.LadbController;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class ShellCommandCaptureRunner {
    public interface Callback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface Host {
        boolean isBackendReadyAndGranted();
        void refreshStatus();
        void appendOutput(String text);
        void executeIo(Runnable task);
        void runOnUiThread(Runnable task);
        ExecMode getExecMode();
        LadbController getLadbController();
        void lifetimeLogActionForCommand(String command);
        void lifetimeLog(String tag, String message);
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

    private final Host host;

    public ShellCommandCaptureRunner(Host host) {
        this.host = host;
    }

    public Result runBlocking(String command) {
        if (!host.isBackendReadyAndGranted()) {
            return new Result(-1, "", "Backend not ready");
        }
        if (TextUtils.isEmpty(command)) {
            return new Result(-1, "", "Command is empty");
        }

        String trimmed = command.trim();
        host.lifetimeLogActionForCommand(trimmed);
        host.lifetimeLog("shell", "$ " + trimmed);
        return execute(trimmed);
    }

    public void runAsync(String command, Callback callback) {
        if (!host.isBackendReadyAndGranted()) {
            host.refreshStatus();
            host.appendOutput("[!] Backend not ready.\n");
            if (callback != null) callback.onComplete(-1, "", "Backend not ready");
            return;
        }
        if (TextUtils.isEmpty(command)) {
            host.appendOutput("[!] Command is empty.\n");
            if (callback != null) callback.onComplete(-1, "", "Command is empty");
            return;
        }

        final String trimmed = command.trim();
        host.lifetimeLogActionForCommand(trimmed);
        host.lifetimeLog("shell", "$ " + trimmed);

        host.executeIo(() -> {
            Result result = execute(trimmed);
            host.runOnUiThread(() -> {
                if (callback != null) {
                    callback.onComplete(result.exitCode, result.stdout, result.stderr);
                }
            });
        });
    }

    public void runAndAppend(String command, Callback callback) {
        if (TextUtils.isEmpty(command)) {
            host.appendOutput("[!] Command is empty.\n");
            if (callback != null) callback.onComplete(-1, "", "Command is empty");
            return;
        }

        final String trimmed = command.trim();
        host.appendOutput("$ " + trimmed + "\n");
        runAsync(trimmed, (exit, out, err) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("exit=").append(exit).append("\n");
            if (!TextUtils.isEmpty(out)) {
                sb.append(out);
                if (!out.endsWith("\n")) sb.append("\n");
            }
            if (!TextUtils.isEmpty(err)) {
                sb.append("--- stderr ---\n");
                sb.append(err);
                if (!err.endsWith("\n")) sb.append("\n");
            }
            host.appendOutput(sb.toString());
            if (callback != null) callback.onComplete(exit, out, err);
        });
    }

    private Result execute(String trimmed) {
        try {
            ExecMode modeNow = host.getExecMode();

            if (modeNow == ExecMode.LADB) {
                LadbClient.CmdResult r;
                LadbController ladb = host.getLadbController();
                if (trimmed.startsWith("adb ")) {
                    r = ladb.rawAdb(LadbClient.tokenizeAdbArgs(trimmed));
                } else {
                    r = ladb.shellShC(trimmed);
                }
                host.lifetimeLog("shell", "exit=" + r.exitCode + " $ " + trimmed);
                return new Result(r.exitCode, r.stdout, r.stderr);
            }

            Process process;
            if (modeNow == ExecMode.SYSTEM) {
                process = new ProcessBuilder("sh", "-c", trimmed).redirectErrorStream(false).start();
            } else {
                process = ShizukuCompat.newProcess(new String[]{"sh", "-c", trimmed}, null, null);
            }

            String out = readAll(process.getInputStream());
            String err = readAll(process.getErrorStream());
            int code = process.waitFor();
            host.lifetimeLog("shell", "exit=" + code + " $ " + trimmed);
            return new Result(code, out, err);
        } catch (Throwable t) {
            return new Result(-1, "", t.toString());
        }
    }

    private static String readAll(InputStream input) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            out.append(buffer, 0, read);
        }
        return out.toString();
    }
}
