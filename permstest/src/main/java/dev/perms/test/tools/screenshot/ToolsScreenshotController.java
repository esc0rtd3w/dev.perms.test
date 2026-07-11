package dev.perms.test.tools.screenshot;

import android.app.Activity;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.databinding.TabToolsBinding;

/** Tools-tab controller for shell-backed screenshots and screenshot area measurement. */
public final class ToolsScreenshotController {
    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        void appendOutput(String message);
        boolean isDebugOutputEnabled();
        void debugOutput(String area, String message);
        void runShellCommandCapture(String command, ShellCallback callback);
    }

    private static final String PUBLIC_SCREENSHOT_DIR = "dev.perms.test/screenshots";
    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingTimer;
    private File lastScreenshot;

    public ToolsScreenshotController(Host host) {
        this.host = host;
    }

    public void bind() {
        TabToolsBinding b = toolsBinding();
        if (b == null) return;
        MaterialButton snapNow = b.btnToolsScreenshotNow;
        MaterialButton snapTimer = b.btnToolsScreenshotTimer;
        MaterialButton measure = b.btnToolsScreenshotMeasure;
        if (snapNow != null) snapNow.setOnClickListener(v -> captureNow());
        if (snapTimer != null) snapTimer.setOnClickListener(v -> captureAfterDelay());
        if (measure != null) measure.setOnClickListener(v -> openMeasureTool());
        if (b.edtToolsScreenshotDelaySeconds != null && TextUtils.isEmpty(b.edtToolsScreenshotDelaySeconds.getText())) {
            b.edtToolsScreenshotDelaySeconds.setText("5");
        }
        if (b.txtToolsScreenshotStatus != null && TextUtils.isEmpty(b.txtToolsScreenshotStatus.getText())) {
            b.txtToolsScreenshotStatus.setText("Screenshots are saved under /sdcard/" + PUBLIC_SCREENSHOT_DIR + ". Requires a shell-capable backend for screencap.");
        }
    }

    public void stop() {
        if (pendingTimer != null) {
            mainHandler.removeCallbacks(pendingTimer);
            pendingTimer = null;
        }
    }

    private void captureNow() {
        captureToNewFile(0);
    }

    private void captureAfterDelay() {
        int seconds = parseDelaySeconds();
        if (seconds <= 0) {
            captureNow();
            return;
        }
        if (pendingTimer != null) {
            mainHandler.removeCallbacks(pendingTimer);
            pendingTimer = null;
        }
        status("Screenshot scheduled in " + seconds + " second(s).");
        pendingTimer = () -> {
            pendingTimer = null;
            captureToNewFile(seconds);
        };
        mainHandler.postDelayed(pendingTimer, seconds * 1000L);
    }

    private int parseDelaySeconds() {
        TabToolsBinding b = toolsBinding();
        TextInputEditText edt = b == null ? null : b.edtToolsScreenshotDelaySeconds;
        String raw = edt == null || edt.getText() == null ? "" : edt.getText().toString().trim();
        int value = 5;
        try {
            if (!TextUtils.isEmpty(raw)) value = Integer.parseInt(raw);
        } catch (Throwable ignored) {
            value = 5;
        }
        if (value < 0) value = 0;
        if (value > 120) value = 120;
        if (edt != null) edt.setText(String.valueOf(value));
        return value;
    }

    private void captureToNewFile(int requestedDelaySeconds) {
        Activity activity = activity();
        if (activity == null) return;
        File dir = screenshotDir();
        if (dir != null && !dir.exists()) {
            try { dir.mkdirs(); } catch (Throwable ignored) {}
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File out = new File(dir, "perms_test_screenshot_" + stamp + ".png");
        lastScreenshot = out;
        String command = "mkdir -p " + shQuote(dir.getAbsolutePath())
                + " && screencap -p " + shQuote(out.getAbsolutePath())
                + " && ls -l " + shQuote(out.getAbsolutePath());
        debug("capture", "running screencap to " + out.getAbsolutePath());
        status("Capturing screenshot" + (requestedDelaySeconds > 0 ? " after " + requestedDelaySeconds + " second timer" : "") + "...");
        if (host == null) return;
        host.runShellCommandCapture(command, (exitCode, stdout, stderr) -> mainHandler.post(() -> {
            boolean exists = out.exists() && out.length() > 0;
            StringBuilder msg = new StringBuilder();
            if (exitCode == 0 && exists) {
                msg.append("Screenshot saved: ").append(out.getAbsolutePath());
                toast("Screenshot saved");
            } else {
                msg.append("Screenshot failed. Selected backend must be able to run screencap.");
                if (!TextUtils.isEmpty(stderr)) msg.append("\n").append(stderr.trim());
                if (!TextUtils.isEmpty(stdout)) msg.append("\n").append(stdout.trim());
                toast("Screenshot failed");
            }
            status(msg.toString());
            append("[screenshot] exit=" + exitCode + " path=" + out.getAbsolutePath() + "\n");
        }));
    }

    private void openMeasureTool() {
        Activity activity = activity();
        if (activity == null) return;
        File target = usableScreenshot(lastScreenshot);
        if (target == null) target = newestScreenshot();
        if (target == null) {
            status("No screenshot available yet. Take a screenshot first, then open the Measure Tool.");
            toast("Take a screenshot first");
            return;
        }
        try {
            Intent intent = new Intent(activity, ScreenshotMeasureActivity.class);
            intent.putExtra(ScreenshotMeasureActivity.EXTRA_PATH, target.getAbsolutePath());
            activity.startActivity(intent);
        } catch (Throwable t) {
            status("Measure Tool failed: " + safeMessage(t));
            toast("Measure Tool failed");
        }
    }

    private File newestScreenshot() {
        File dir = screenshotDir();
        File[] files = dir == null ? null : dir.listFiles((d, name) -> name != null && name.toLowerCase(Locale.US).endsWith(".png"));
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File f : files) {
            File usable = usableScreenshot(f);
            if (usable != null) return usable;
        }
        return null;
    }

    private File usableScreenshot(File file) {
        return file != null && file.exists() && file.length() > 0 ? file : null;
    }

    private File screenshotDir() {
        File root = Environment.getExternalStorageDirectory();
        return new File(root, PUBLIC_SCREENSHOT_DIR);
    }

    private String shQuote(String value) {
        if (value == null) value = "";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void status(String text) {
        TabToolsBinding b = toolsBinding();
        if (b != null && b.txtToolsScreenshotStatus != null) {
            b.txtToolsScreenshotStatus.setText(text == null ? "" : text);
        }
    }

    private void append(String text) {
        if (host != null) host.appendOutput(text);
    }

    private void debug(String area, String text) {
        if (host != null && host.isDebugOutputEnabled()) host.debugOutput(area, text);
    }

    private void toast(String text) {
        Activity activity = activity();
        if (activity != null) Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private TabToolsBinding toolsBinding() {
        ActivityMainBinding binding = host == null ? null : host.getBinding();
        return binding == null ? null : binding.tabTools;
    }

    private static String safeMessage(Throwable t) {
        String msg = t == null ? "" : t.getMessage();
        return TextUtils.isEmpty(msg) && t != null ? t.getClass().getSimpleName() : msg;
    }
}
