package dev.perms.test.memory.payload;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import dev.perms.test.memory.overlay.MemoryOverlayService;

/**
 * Focus-safe shortcut entry point for Launch With Payloads.
 *
 * Shortcuts must start an Activity, but using a fully transparent no-window
 * transition can leave the task without a focused window while Android is also
 * launching the target app. This tiny visible bridge keeps input dispatch sane
 * and immediately delegates the real work to MemoryOverlayService.
 */
public final class MemoryPayloadLauncherActivity extends Activity {
    public static final String ACTION_LAUNCH_WITH_PAYLOADS = "dev.perms.test.action.SHORTCUT_LAUNCH_WITH_PAYLOADS";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean handled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHandoffWindow();
        mainHandler.post(() -> handleAndClose(getIntent()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handled = false;
        mainHandler.post(() -> handleAndClose(intent));
    }

    private void showHandoffWindow() {
        try {
            TextView text = new TextView(this);
            text.setText("Launching with payloads...");
            text.setTextColor(Color.WHITE);
            text.setGravity(Gravity.CENTER);
            text.setTextSize(16f);
            int pad = (int) (24f * getResources().getDisplayMetrics().density + 0.5f);
            text.setPadding(pad, pad, pad, pad);
            FrameLayout frame = new FrameLayout(this);
            frame.addView(text, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            setContentView(frame);
        } catch (Throwable ignored) {
        }
    }

    private void handleAndClose(Intent source) {
        if (handled) return;
        handled = true;
        handleIntent(source);
        mainHandler.postDelayed(() -> {
            try {
                closeShortcutTask();
                overridePendingTransition(0, 0);
            } catch (Throwable ignored) {
            }
        }, 350L);
    }

    private void closeShortcutTask() {
        try {
            if (isFinishing()) return;
            if (Build.VERSION.SDK_INT >= 21) {
                finishAndRemoveTask();
            } else {
                finish();
            }
        } catch (Throwable t) {
            try { finish(); } catch (Throwable ignored) {}
        }
    }

    private void handleIntent(Intent source) {
        try {
            String pkg = source == null ? null : source.getStringExtra(MemoryOverlayService.EXTRA_TARGET_PACKAGE);
            if (TextUtils.isEmpty(pkg)) {
                Toast.makeText(this, "Payload shortcut is missing a package.", Toast.LENGTH_SHORT).show();
                return;
            }
            String targetPkg = pkg.trim();
            Intent launch = getPackageManager().getLaunchIntentForPackage(targetPkg);
            if (launch == null) {
                Toast.makeText(this, "No launchable activity for payload shortcut.", Toast.LENGTH_SHORT).show();
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(launch);

            Intent svc = new Intent(this, MemoryOverlayService.class);
            svc.setAction(MemoryOverlayService.ACTION_APPLY_PAYLOADS_ONLY);
            svc.putExtra(MemoryOverlayService.EXTRA_TARGET_PACKAGE, targetPkg);
            String label = source.getStringExtra(MemoryOverlayService.EXTRA_TARGET_LABEL);
            if (!TextUtils.isEmpty(label)) svc.putExtra(MemoryOverlayService.EXTRA_TARGET_LABEL, label.trim());
            String[] selectedPayloadFiles = getSelectedPayloadFiles(source);
            if (selectedPayloadFiles != null && selectedPayloadFiles.length > 0) {
                svc.putExtra(MemoryOverlayService.EXTRA_PAYLOAD_FILE_NAMES, selectedPayloadFiles);
            }
            long delayMs = getPayloadDelayMs(source);
            if (delayMs > 0L) {
                svc.putExtra(MemoryOverlayService.EXTRA_PAYLOAD_DELAY_MS, delayMs);
            }
            Log.i("PermsTestPayloadShortcut", "Launching payload shortcut: pkg=" + targetPkg
                    + ", selected=" + (selectedPayloadFiles == null ? 0 : selectedPayloadFiles.length)
                    + ", delayMs=" + delayMs);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
            Toast.makeText(this, "Launching with payloads", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Launch With Payloads failed", Toast.LENGTH_SHORT).show();
        }
    }

    private String[] getSelectedPayloadFiles(Intent source) {
        if (source == null) return null;
        try {
            String[] arr = source.getStringArrayExtra(MemoryOverlayService.EXTRA_PAYLOAD_FILE_NAMES);
            if (arr != null && arr.length > 0) return arr;
        } catch (Throwable ignored) {
        }
        try {
            java.util.ArrayList<String> list = source.getStringArrayListExtra(MemoryOverlayService.EXTRA_PAYLOAD_FILE_NAMES);
            if (list != null && !list.isEmpty()) return list.toArray(new String[0]);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private long getPayloadDelayMs(Intent source) {
        if (source == null) return 0L;
        try {
            return Math.max(0L, Math.min(source.getLongExtra(MemoryOverlayService.EXTRA_PAYLOAD_DELAY_MS, 0L), 30000L));
        } catch (Throwable ignored) {
            return 0L;
        }
    }
}
