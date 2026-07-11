package dev.perms.test.memory.panel;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.TextView;

import dev.perms.test.R;
import dev.perms.test.memory.overlay.MemoryOverlayService;
import dev.perms.test.ui.PermsTestUiCompat;

/**
 * Normal Activity-hosted panel for Memory overlay UIs.
 *
 * This keeps the VR-compatible path out of raw WindowManager overlays while reusing the same
 * service-side memory backend and tool controllers.
 */
public class MemoryOverlayPanelActivity extends Activity {
    private FrameLayout content;
    private TextView title;
    private MemoryOverlayService.LocalBinder binder;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof MemoryOverlayService.LocalBinder) {
                binder = (MemoryOverlayService.LocalBinder) service;
                bound = true;
                showRequestedPanel();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            binder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory_overlay_panel);
        content = findViewById(R.id.frameMemoryOverlayPanelContent);
        title = findViewById(R.id.txtMemoryOverlayPanelTitle);
        findViewById(R.id.btnMemoryOverlayPanelClose).setOnClickListener(v -> finish());
        PermsTestUiCompat.applyActivityUiProfile(this, getWindow().getDecorView());
        applyWindowLabelForAction(resolveRequestedAction());
        bindMemoryService();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try { setIntent(intent); } catch (Throwable ignored) {}
        applyWindowLabelForAction(resolveRequestedAction());
        showRequestedPanel();
    }

    @Override
    protected void onDestroy() {
        try {
            if (bound && binder != null && content != null) {
                binder.detachPanel(content);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (bound) unbindService(connection);
        } catch (Throwable ignored) {
        }
        bound = false;
        binder = null;
        super.onDestroy();
    }

    private void bindMemoryService() {
        try {
            Intent service = new Intent(this, MemoryOverlayService.class);
            bindService(service, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable ignored) {
        }
    }

    private void showRequestedPanel() {
        if (binder == null || content == null) return;
        Intent request = buildServiceRequestIntent();
        applyWindowLabelForAction(request.getAction());
        if (title != null) title.setText(titleForAction(request.getAction()));
        binder.showPanel(content, request, this::finish);
    }

    private Intent buildServiceRequestIntent() {
        Intent src = getIntent();
        String action = src == null ? null : src.getAction();
        Intent request = new Intent(this, MemoryOverlayService.class);
        request.setAction(TextUtils.isEmpty(action) ? defaultAction() : action);
        if (src != null) {
            String pkg = src.getStringExtra(MemoryOverlayService.EXTRA_TARGET_PACKAGE);
            String pid = src.getStringExtra(MemoryOverlayService.EXTRA_TARGET_PID);
            if (!TextUtils.isEmpty(pkg)) request.putExtra(MemoryOverlayService.EXTRA_TARGET_PACKAGE, pkg);
            if (!TextUtils.isEmpty(pid)) request.putExtra(MemoryOverlayService.EXTRA_TARGET_PID, pid);
        }
        return request;
    }

    protected String defaultAction() {
        return MemoryOverlayService.ACTION_SHOW_OVERLAY;
    }

    private String resolveRequestedAction() {
        Intent src = getIntent();
        String action = src == null ? null : src.getAction();
        return TextUtils.isEmpty(action) ? defaultAction() : action;
    }

    private void applyWindowLabelForAction(String action) {
        String label = windowLabelForAction(action);
        try {
            setTitle(label);
        } catch (Throwable ignored) {
        }
        try {
            setTaskDescription(new ActivityManager.TaskDescription(label));
        } catch (Throwable ignored) {
        }
    }

    private static String titleForAction(String action) {
        if (MemoryOverlayService.ACTION_SHOW_HEX_OVERLAY.equals(action)) return "Hex Editor Panel";
        if (MemoryOverlayService.ACTION_SHOW_DISASSEMBLY_OVERLAY.equals(action)) return "Disassembly Panel";
        if (MemoryOverlayService.ACTION_SHOW_SPECIAL_TOOLS_OVERLAY.equals(action)) return "Special Tools Panel";
        return "Memory Editor Panel";
    }

    private static String windowLabelForAction(String action) {
        if (MemoryOverlayService.ACTION_SHOW_HEX_OVERLAY.equals(action)) return "Hex Editor";
        if (MemoryOverlayService.ACTION_SHOW_DISASSEMBLY_OVERLAY.equals(action)) return "Disassembly";
        if (MemoryOverlayService.ACTION_SHOW_SPECIAL_TOOLS_OVERLAY.equals(action)) return "Special Tools";
        return "Memory Editor";
    }
}
