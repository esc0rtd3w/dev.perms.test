package dev.perms.test.tools.root;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.perms.test.databinding.ActivityMainBinding;

/** Small Tools tab controller for local root availability checks. */
public final class ToolsRootCheckerController {
    private static final int COLOR_ROOT_OK = Color.rgb(104, 170, 0);
    private static final int COLOR_ROOT_MISSING = Color.rgb(210, 32, 48);
    private static final int COLOR_ROOT_UNKNOWN = Color.rgb(180, 180, 180);

    private static final long ROOT_COMMAND_TIMEOUT_MS = 12000L;

    private static final List<String> ROOT_MANAGER_PACKAGES = Arrays.asList(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "eu.chainfire.supersu.pro",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.zachspong.temprootremovejb",
            "com.ramdroid.appquarantine"
    );

    private static final List<String> SU_PATHS = Arrays.asList(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/adb/magisk/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/vendor/bin/su"
    );

    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        void appendOutput(String message);
    }

    private final Host host;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PermsTestRootCheck");
        t.setDaemon(true);
        return t;
    });

    private long requestSeq;

    public ToolsRootCheckerController(@NonNull Host host) {
        this.host = host;
    }

    public void bind() {
        ActivityMainBinding binding = host.getBinding();
        if (binding == null || binding.tabTools == null) return;
        if (binding.tabTools.btnToolsRootCheck == null || binding.tabTools.txtToolsRootStatus == null) return;

        setStatus("Root: not checked", COLOR_ROOT_UNKNOWN);
        binding.tabTools.btnToolsRootCheck.setOnClickListener(v -> checkRoot());
    }

    public void stop() {
        try {
            executor.shutdownNow();
        } catch (Throwable ignored) {
        }
    }

    private void checkRoot() {
        final Activity activity = host.getActivity();
        if (activity == null) return;

        final long seq = ++requestSeq;
        setStatus("Root: checking...", COLOR_ROOT_UNKNOWN);
        host.appendOutput("[Root Checker] Checking local root access...\n");

        executor.execute(() -> {
            RootCheckResult result = runChecks(activity.getApplicationContext());
            Activity a = host.getActivity();
            if (a == null || seq != requestSeq) return;
            a.runOnUiThread(() -> {
                if (seq != requestSeq) return;
                if (result.rootAccess) {
                    setStatus("Root: detected", COLOR_ROOT_OK);
                } else if (result.rootHintsFound) {
                    setStatus("Root: found, no access", COLOR_ROOT_MISSING);
                } else {
                    setStatus("Root: not detected", COLOR_ROOT_MISSING);
                }
                host.appendOutput(result.logText + "\n");
            });
        });
    }

    private void setStatus(String text, int color) {
        ActivityMainBinding binding = host.getBinding();
        TextView status = binding == null || binding.tabTools == null ? null : binding.tabTools.txtToolsRootStatus;
        if (status == null) return;
        status.setText(text);
        status.setTextColor(color);
    }

    private RootCheckResult runChecks(Context context) {
        StringBuilder log = new StringBuilder();
        log.append("[Root Checker] Results:\n");

        List<String> managers = detectRootManagers(context);
        List<String> suPaths = detectSuPaths();
        String shellSu = findShellSu();

        if (!managers.isEmpty()) {
            log.append("[Root Checker] Root manager packages: ").append(TextUtils.join(", ", managers)).append('\n');
        }
        if (!suPaths.isEmpty()) {
            log.append("[Root Checker] su paths: ").append(TextUtils.join(", ", suPaths)).append('\n');
        }
        if (!TextUtils.isEmpty(shellSu)) {
            log.append("[Root Checker] shell su: ").append(shellSu).append('\n');
        }

        List<String> candidates = new ArrayList<>();
        candidates.add("su");
        candidates.addAll(suPaths);
        if (!TextUtils.isEmpty(shellSu) && !candidates.contains(shellSu)) candidates.add(shellSu);

        RootCommandResult best = null;
        for (String su : candidates) {
            if (TextUtils.isEmpty(su)) continue;
            RootCommandResult r = runSuId(su);
            best = r;
            log.append("[Root Checker] ").append(su).append(" -> exit=").append(r.exitCode);
            if (!TextUtils.isEmpty(r.output)) log.append(" out=").append(compact(r.output));
            if (!TextUtils.isEmpty(r.error)) log.append(" err=").append(compact(r.error));
            log.append('\n');
            if (r.rootAccess) {
                log.append("[Root Checker] Root access verified through ").append(su).append(".\n");
                return new RootCheckResult(true, true, log.toString());
            }
            if (SystemClock.uptimeMillis() - r.startedAtMs > ROOT_COMMAND_TIMEOUT_MS) break;
        }

        boolean hints = !managers.isEmpty() || !suPaths.isEmpty() || !TextUtils.isEmpty(shellSu);
        if (!hints) {
            log.append("[Root Checker] No common Magisk/SuperSU/Superuser/KingRoot indicators found.\n");
        } else if (best != null) {
            log.append("[Root Checker] Root indicators exist, but uid=0 access was not granted.\n");
        }
        return new RootCheckResult(false, hints, log.toString());
    }

    private List<String> detectRootManagers(Context context) {
        List<String> out = new ArrayList<>();
        if (context == null) return out;
        PackageManager pm = context.getPackageManager();
        if (pm == null) return out;
        for (String pkg : ROOT_MANAGER_PACKAGES) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (ai != null) out.add(pkg);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private List<String> detectSuPaths() {
        List<String> out = new ArrayList<>();
        for (String path : SU_PATHS) {
            try {
                File f = new File(path);
                if (f.exists()) out.add(path);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private String findShellSu() {
        ProcessResult result = runProcess(new String[]{"sh", "-c", "command -v su 2>/dev/null || which su 2>/dev/null"}, 3000L);
        if (result == null || TextUtils.isEmpty(result.output)) return "";
        String[] lines = result.output.split("\\r?\\n");
        for (String line : lines) {
            String clean = line == null ? "" : line.trim();
            if (!TextUtils.isEmpty(clean) && clean.contains("su")) return clean;
        }
        return "";
    }

    private RootCommandResult runSuId(String suBinary) {
        long start = SystemClock.uptimeMillis();
        ProcessResult result = runProcess(new String[]{suBinary, "-c", "id; id -u; whoami"}, ROOT_COMMAND_TIMEOUT_MS);
        String out = result == null ? "" : result.output;
        String err = result == null ? "" : result.error;
        int exit = result == null ? -999 : result.exitCode;
        String combined = (out + "\n" + err).toLowerCase(Locale.US);
        boolean hasUidZero = combined.contains("uid=0");
        boolean hasLineZero = false;
        boolean hasLineRoot = false;
        for (String line : combined.split("\\r?\\n")) {
            String clean = line == null ? "" : line.trim();
            if ("0".equals(clean)) hasLineZero = true;
            if ("root".equals(clean)) hasLineRoot = true;
        }
        boolean root = hasUidZero || (exit == 0 && hasLineZero && hasLineRoot);
        return new RootCommandResult(exit, out, err, root, start);
    }

    private ProcessResult runProcess(String[] cmd, long timeoutMs) {
        Process process = null;
        try {
            process = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            final Process p = process;
            final StringBuilder stdout = new StringBuilder();
            final StringBuilder stderr = new StringBuilder();
            Thread outReader = new Thread(() -> readStream(p.getInputStream(), stdout), "PermsTestRootStdout");
            Thread errReader = new Thread(() -> readStream(p.getErrorStream(), stderr), "PermsTestRootStderr");
            outReader.setDaemon(true);
            errReader.setDaemon(true);
            outReader.start();
            errReader.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                }
                return new ProcessResult(-998, stdout.toString(), stderr.toString() + " timeout");
            }
            try { outReader.join(250L); } catch (Throwable ignored) {}
            try { errReader.join(250L); } catch (Throwable ignored) {}
            return new ProcessResult(process.exitValue(), stdout.toString(), stderr.toString());
        } catch (Throwable t) {
            return new ProcessResult(-997, "", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void readStream(InputStream stream, StringBuilder out) {
        if (stream == null || out == null) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append('\n');
            }
        } catch (Throwable ignored) {
        }
    }

    private static String compact(String text) {
        if (text == null) return "";
        String clean = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.length() > 220) return clean.substring(0, 220) + "...";
        return clean;
    }

    private static final class RootCheckResult {
        final boolean rootAccess;
        final boolean rootHintsFound;
        final String logText;

        RootCheckResult(boolean rootAccess, boolean rootHintsFound, String logText) {
            this.rootAccess = rootAccess;
            this.rootHintsFound = rootHintsFound;
            this.logText = logText;
        }
    }

    private static final class RootCommandResult {
        final int exitCode;
        final String output;
        final String error;
        final boolean rootAccess;
        final long startedAtMs;

        RootCommandResult(int exitCode, String output, String error, boolean rootAccess, long startedAtMs) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
            this.rootAccess = rootAccess;
            this.startedAtMs = startedAtMs;
        }
    }

    private static final class ProcessResult {
        final int exitCode;
        final String output;
        final String error;

        ProcessResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
        }
    }
}
