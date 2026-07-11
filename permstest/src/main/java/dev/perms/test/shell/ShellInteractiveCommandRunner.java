package dev.perms.test.shell;

import android.text.TextUtils;

import java.util.concurrent.ExecutorService;

import dev.perms.test.ExecMode;
import dev.perms.test.ladb.LadbClient;
import dev.perms.test.ShizukuCompat;

/**
 * Owns Shell-tab interactive command state and bounded process execution.
 */
public final class ShellInteractiveCommandRunner {
    private static final long DEFAULT_COMMAND_TIMEOUT_MS = 15000L;
    private static final long DEFAULT_START_TIMEOUT_MS = 8000L;

    public interface Host {
        boolean isBackendReady();
        void refreshStatus();
        void appendOutput(String text);
        void runOnUiThread(Runnable action);
        void setRunningUi(boolean running);
        void lifetimeLogActionForCommand(String command);
        void lifetimeLog(String tag, String message);
        void setOutputTag(String tag);
        String rewriteCommandWithLocalBins(String command);
        ExecMode getExecMode();
        LadbClient getLadbClient();
    }

    private final ExecutorService shellIo;
    private final Host host;
    private final long startTimeoutMs;
    private final long commandTimeoutMs;
    private final Object lock = new Object();

    private volatile Process activeProcess = null;
    private volatile boolean busy = false;
    private volatile boolean stopRequested = false;

    public ShellInteractiveCommandRunner(ExecutorService shellIo, Host host) {
        this(shellIo, host, DEFAULT_START_TIMEOUT_MS, DEFAULT_COMMAND_TIMEOUT_MS);
    }

    public ShellInteractiveCommandRunner(ExecutorService shellIo, Host host,
                                         long startTimeoutMs, long commandTimeoutMs) {
        if (shellIo == null) {
            throw new IllegalArgumentException("shellIo == null");
        }
        if (host == null) {
            throw new IllegalArgumentException("host == null");
        }
        this.shellIo = shellIo;
        this.host = host;
        this.startTimeoutMs = Math.max(1L, startTimeoutMs);
        this.commandTimeoutMs = Math.max(1L, commandTimeoutMs);
    }

    public void runCommand(String cmd) {
        if (!host.isBackendReady()) {
            host.refreshStatus();
            host.appendOutput("[!] Backend not ready.\n");
            return;
        }

        if (TextUtils.isEmpty(cmd)) {
            host.appendOutput("[!] Command is empty.\n");
            return;
        }

        synchronized (lock) {
            if (busy || activeProcess != null) {
                host.appendOutput("[!] A shell command is still running. Tap Stop to reset it.\n");
                return;
            }
            busy = true;
            stopRequested = false;
        }

        final String trimmed = cmd.trim();
        host.lifetimeLogActionForCommand(trimmed);
        host.setOutputTag("shell");
        host.appendOutput("$ " + trimmed + "\n");
        host.setRunningUi(true);

        shellIo.execute(() -> runCommandOnWorker(trimmed));
    }

    public void stopCommand() {
        Process process;
        synchronized (lock) {
            stopRequested = true;
            process = activeProcess;
        }
        if (process == null) {
            host.appendOutput(busy ? "[i] Shell worker is starting; stop requested.\n" : "[i] No running shell command.\n");
            host.setRunningUi(busy);
            return;
        }
        ShellProcessRunner.destroyProcess(process);
        host.appendOutput("[i] Stopping shell command...\n");
    }

    private void runCommandOnWorker(String trimmed) {
        String effective = safeRewrite(trimmed);
        host.lifetimeLog("shell", "$ " + effective);
        try {
            ShellProcessRunner.Result result = runForCurrentBackend(effective);
            host.lifetimeLog("shell", "exit=" + result.exitCode + " $ " + effective);
            String output = formatResult(result);
            host.runOnUiThread(() -> host.appendOutput(output));
        } catch (Throwable t) {
            host.runOnUiThread(() -> host.appendOutput("[!] Failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n"));
        } finally {
            synchronized (lock) {
                busy = false;
            }
            host.runOnUiThread(() -> host.setRunningUi(false));
        }
    }

    private ShellProcessRunner.Result runForCurrentBackend(String effective) throws InterruptedException {
        ExecMode modeNow = host.getExecMode();
        if (modeNow == ExecMode.LADB) {
            return runLadbShell(effective);
        }
        if (modeNow == ExecMode.SYSTEM) {
            return ShellProcessRunner.run(
                    () -> new ProcessBuilder("sh", "-c", effective).redirectErrorStream(false).start(),
                    startTimeoutMs,
                    commandTimeoutMs,
                    this::isStopRequested,
                    processObserver());
        }
        return ShellProcessRunner.run(
                () -> ShizukuCompat.newProcess(new String[]{"sh", "-c", effective}, null, null),
                startTimeoutMs,
                commandTimeoutMs,
                this::isStopRequested,
                processObserver());
    }

    private ShellProcessRunner.Result runLadbShell(String effective) {
        try {
            LadbClient client = host.getLadbClient();
            if (client == null) {
                return ShellProcessRunner.Result.error("LADB client is not connected");
            }
            LadbClient.CmdResult r;
            if (effective.startsWith("adb ")) {
                r = client.rawAdb(LadbClient.tokenizeAdbArgs(effective));
            } else {
                r = client.shellShC(effective);
            }
            if (r == null) {
                return ShellProcessRunner.Result.error("LADB returned no result");
            }
            return new ShellProcessRunner.Result(r.exitCode, r.stdout, r.stderr, false, false, false);
        } catch (Throwable t) {
            return ShellProcessRunner.Result.error(t.toString());
        }
    }

    private ShellProcessRunner.ProcessObserver processObserver() {
        return new ShellProcessRunner.ProcessObserver() {
            @Override
            public void onProcessReady(Process process) {
                synchronized (lock) {
                    activeProcess = process;
                }
            }

            @Override
            public void onProcessCleared(Process process) {
                clearActiveProcess(process);
            }
        };
    }

    private void clearActiveProcess(Process process) {
        synchronized (lock) {
            if (activeProcess == process) {
                activeProcess = null;
                stopRequested = false;
            }
        }
    }

    private boolean isStopRequested() {
        synchronized (lock) {
            return stopRequested;
        }
    }

    private String safeRewrite(String command) {
        try {
            String rewritten = host.rewriteCommandWithLocalBins(command);
            return rewritten == null ? command : rewritten;
        } catch (Throwable ignored) {
            return command;
        }
    }

    private String formatResult(ShellProcessRunner.Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("exit=").append(result.exitCode).append("\n");
        if (result.startTimedOut) {
            sb.append("[!] Shell backend did not start within ").append(startTimeoutMs / 1000L)
                    .append("s and was reset. Try the command again.\n");
        } else if (result.timedOut) {
            sb.append("[!] Command timed out after ").append(commandTimeoutMs / 1000L)
                    .append("s and was stopped. Use bounded commands such as: ping -c 4 <host>\n");
        } else if (result.stopped) {
            sb.append("[i] Command stopped.\n");
        }
        if (!TextUtils.isEmpty(result.stdout)) {
            sb.append(result.stdout);
            if (!result.stdout.endsWith("\n")) sb.append("\n");
        }
        if (!TextUtils.isEmpty(result.stderr)) {
            sb.append("--- stderr ---\n");
            sb.append(result.stderr);
            if (!result.stderr.endsWith("\n")) sb.append("\n");
        }
        return sb.toString();
    }
}
