package dev.perms.test.plugins;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import dev.perms.test.R;
import dev.perms.test.ui.PermsTestUiCompat;
import dev.perms.test.ui.panel.PermsTestPanelSettings;
import dev.perms.test.vr.PermsTestVrOverlayCompat;

/** Activity-hosted plugin tool panel for VR/popout use. */
public class PluginToolPanelActivity extends Activity implements PluginActionRegistry.Output {
    public static final String EXTRA_TOOL_ID = "toolId";
    public static final String EXTRA_PANEL_KEY = "panelKey";
    private static final Set<String> ACTIVE_KEYS = Collections.synchronizedSet(new HashSet<>());

    private String panelKey;
    private Object controller;

    public static boolean start(Activity activity, String pluginId, String toolId) {
        if (activity == null || TextUtils.isEmpty(toolId)) return false;
        if (!PermsTestPanelSettings.isPanelHostEnabled(activity)) {
            Toast.makeText(activity, "Enable Popout Panels in Settings first", Toast.LENGTH_SHORT).show();
            return false;
        }
        String key = clean(pluginId, "plugin") + ":" + clean(toolId, "tool");
        synchronized (ACTIVE_KEYS) {
            if (ACTIVE_KEYS.contains(key)) {
                Toast.makeText(activity, "That plugin panel is already open", Toast.LENGTH_SHORT).show();
                return false;
            }
            ACTIVE_KEYS.add(key);
        }
        try {
            Intent intent = new Intent(activity, PluginToolPanelActivity.class)
                    .putExtra(EXTRA_TOOL_ID, toolId)
                    .putExtra(EXTRA_PANEL_KEY, key)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            activity.startActivity(intent);
            PermsTestVrOverlayCompat.moveHostTaskBehindMemoryToolIfNeeded(activity);
            return true;
        } catch (Throwable t) {
            release(key);
            Toast.makeText(activity, "Plugin panel failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    static void release(String key) {
        if (!TextUtils.isEmpty(key)) ACTIVE_KEYS.remove(key);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plugin_tool_panel);
        PermsTestUiCompat.applyActivityUiProfile(this, getWindow().getDecorView());

        String toolId = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_TOOL_ID);
        panelKey = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_PANEL_KEY);
        String title = PluginToolSurfaceDialog.titleFor(toolId);

        TextView titleView = findViewById(R.id.txtPluginToolPanelTitle);
        if (titleView != null) titleView.setText("");
        View close = findViewById(R.id.btnPluginToolPanelClose);
        if (close != null) close.setOnClickListener(v -> finish());

        FrameLayout host = findViewById(R.id.framePluginToolPanelContent);
        int layout = PluginToolSurfaceDialog.layoutFor(toolId);
        if (host == null || layout == 0) {
            Toast.makeText(this, "Unsupported plugin tool panel", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        View surface = LayoutInflater.from(this).inflate(layout, host, false);
        host.addView(surface);
        controller = PluginToolSurfaceDialog.bindController(this, surface, toolId, this);
        try { setTitle(title); } catch (Throwable ignored) {}
        try { setTaskDescription(new ActivityManager.TaskDescription(title)); } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        try { PluginToolSurfaceDialog.stopController(controller); } catch (Throwable ignored) {}
        try { release(panelKey); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    public void appendOutput(String message) {
        // Plugin tool panels are intentionally decoupled from MainActivity output.
    }

    private static String clean(String value, String fallback) {
        if (TextUtils.isEmpty(value)) return fallback;
        return value.trim();
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.toString() : msg;
    }
}
