package dev.perms.test.shell;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs an interactive shell process with bounded process-start and command runtimes.
 */
public final class ShellProcessRunner {
    public interface ProcessFactory {
        Process start() throws Exception;
    }

    public interface StopSignal {
        boolean isStopRequested();
    }

    public interface ProcessObserver {
        void onProcessReady(Process process);
        void onProcessCleared(Process process);
    }

    public static final class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;
        public final boolean stopped;
        public final boolean startTimedOut;

        public Result(int exitCode, String stdout, String stderr,
                      boolean timedOut, boolean stopped, boolean startTimedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.timedOut = timedOut;
            this.stopped = stopped;
            this.startTimedOut = startTimedOut;
        }

        public static Result error(String message) {
            return new Result(-1, "", message == null ? "" : message, false, false, false);
        }

        public static Result stoppedBeforeStart() {
            return new Result(-3, "", "", false, true, false);
        }

        public static Result startTimeout(long timeoutMs) {
            return new Result(-2, "", "", false, false, true);
        }
    }

    private static final int PROCESS_STILL_RUNNING = Integer.MIN_VALUE;
    private static final int PROCESS_WAIT_FAILED = Integer.MIN_VALUE + 1;

    private ShellProcessRunner() {
    }

    public static Result run(ProcessFactory factory,
                             long startTimeoutMs,
                             long runTimeoutMs,
                             StopSignal stopSignal,
                             ProcessObserver observer) throws InterruptedException {
        if (factory == null) {
            return Result.error("Shell process factory is missing");
        }

        final AtomicReference<Process> processRef = new AtomicReference<>();
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        final AtomicBoolean cancelStart = new AtomicBoolean(false);
        final CountDownLatch started = new CountDownLatch(1);

        Thread starter = new Thread(() -> {
            Process process = null;
            try {
                process = factory.start();
                if (cancelStart.get() || isStopRequested(stopSignal)) {
                    destroyProcess(process);
                    return;
                }
                processRef.set(process);
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                started.countDown();
            }
        }, "permstest-shell-start");
        try { starter.setDaemon(true); } catch (Throwable ignored) {}
        starter.start();

        final long deadline = System.currentTimeMillis() + Math.max(1L, startTimeoutMs);
        while (started.getCount() > 0) {
            if (isStopRequested(stopSignal)) {
                cancelStart.set(true);
                Process late = processRef.get();
                if (late != null) destroyProcess(late);
                return Result.stoppedBeforeStart();
            }
            long remain = deadline - System.currentTimeMillis();
            if (remain <= 0L) {
                cancelStart.set(true);
                Process late = processRef.get();
                if (late != null) destroyProcess(late);
                return Result.startTimeout(startTimeoutMs);
            }
            started.await(Math.min(100L, remain), TimeUnit.MILLISECONDS);
        }

        Throwable startError = errorRef.get();
        if (startError != null) {
            return Result.error(startError.toString());
        }

        Process process = processRef.get();
        if (process == null) {
            return isStopRequested(stopSignal) ? Result.stoppedBeforeStart() : Result.error("Shell process did not start");
        }

        if (observer != null) {
            observer.onProcessReady(process);
        }

        try {
            return waitForProcess(process, Math.max(1L, runTimeoutMs), stopSignal);
        } finally {
            if (observer != null) {
                observer.onProcessCleared(process);
            }
        }
    }

    public static void destroyProcess(Process process) {
        if (process == null) return;
        try { process.destroy(); } catch (Throwable ignored) {}
        try {
            if (waitForProcessExitCompat(process, 500L) == PROCESS_STILL_RUNNING) {
                try { process.destroyForcibly(); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            try { process.destroyForcibly(); } catch (Throwable ignoredToo) {}
        }
    }

    private static Result waitForProcess(Process process, long timeoutMs, StopSignal stopSignal) throws InterruptedException {
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();
        Thread outThread = startStreamCollector(process.getInputStream(), stdout, "permstest-shell-stdout");
        Thread errThread = startStreamCollector(process.getErrorStream(), stderr, "permstest-shell-stderr");

        boolean timedOut = false;
        boolean stopped = false;
        int exit = -1;
        try {
            final long deadline = System.currentTimeMillis() + Math.max(1L, timeoutMs);
            while (true) {
                if (isStopRequested(stopSignal)) {
                    stopped = true;
                    destroyProcess(process);
                    waitForProcessExitCompat(process, 1500L);
                    exit = safeProcessExitValue(process, -3);
                    break;
                }

                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0L) {
                    timedOut = true;
                    destroyProcess(process);
                    waitForProcessExitCompat(process, 1500L);
                    exit = -2;
                    break;
                }

                int waitExit = waitForProcessExitCompat(process, Math.min(100L, remain));
                if (waitExit == PROCESS_STILL_RUNNING) continue;
                exit = waitExit == PROCESS_WAIT_FAILED ? safeProcessExitValue(process, -1) : waitExit;
                break;
            }
        } finally {
            try { process.getOutputStream().close(); } catch (Throwable ignored) {}
            try { process.getInputStream().close(); } catch (Throwable ignored) {}
            try { process.getErrorStream().close(); } catch (Throwable ignored) {}
            try { outThread.join(500L); } catch (Throwable ignored) {}
            try { errThread.join(500L); } catch (Throwable ignored) {}
        }

        String outText;
        String errText;
        synchronized (stdout) { outText = stdout.toString(); }
        synchronized (stderr) { errText = stderr.toString(); }
        return new Result(exit, outText, errText, timedOut, stopped, false);
    }

    private static Thread startStreamCollector(final InputStream in, final StringBuilder target, String name) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int r;
                while ((r = br.read(buf)) != -1) {
                    synchronized (target) {
                        target.append(buf, 0, r);
                    }
                }
            } catch (Throwable ignored) {
            }
        }, name);
        try { t.setDaemon(true); } catch (Throwable ignored) {}
        t.start();
        return t;
    }

    private static int waitForProcessExitCompat(final Process process, long timeoutMs) throws InterruptedException {
        if (process == null) return PROCESS_WAIT_FAILED;
        final CountDownLatch done = new CountDownLatch(1);
        final int[] exit = new int[]{PROCESS_WAIT_FAILED};
        Thread waiter = new Thread(() -> {
            try {
                exit[0] = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignored) {
                exit[0] = safeProcessExitValue(process, PROCESS_WAIT_FAILED);
            } finally {
                done.countDown();
            }
        }, "permstest-shell-wait");
        try { waiter.setDaemon(true); } catch (Throwable ignored) {}
        waiter.start();
        if (!done.await(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS)) {
            return PROCESS_STILL_RUNNING;
        }
        return exit[0];
    }

    private static int safeProcessExitValue(Process process, int fallback) {
        try {
            return process == null ? fallback : process.exitValue();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean isStopRequested(StopSignal stopSignal) {
        try {
            return stopSignal != null && stopSignal.isStopRequested();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
