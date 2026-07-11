package dev.perms.test.plugins;

import android.app.Activity;
import android.view.LayoutInflater;
import android.text.TextUtils;
import android.view.View;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import dev.perms.test.R;
import dev.perms.test.ui.dialog.MovableDialogChrome;
import dev.perms.test.tools.alarms.ToolsAlarmsTimersController;
import dev.perms.test.tools.calc.ToolsCalculatorController;
import dev.perms.test.tools.converter.ToolsAsciiHexConverterController;

/** Hosts native plugin tool layouts without depending on the Tools tab view binding. */
public final class PluginToolSurfaceDialog {
    private static final Set<String> ACTIVE_KEYS = Collections.synchronizedSet(new HashSet<>());

    private PluginToolSurfaceDialog() {
    }

    public static boolean show(Activity activity, String pluginId, String toolId, String windowStyle, String windowFit, PluginActionRegistry.Output output) {
        if (activity == null) return false;
        int layoutId = layoutFor(toolId);
        String title = titleFor(toolId);
        if (layoutId == 0) return false;
        String key = clean(pluginId, "plugin") + ":" + clean(toolId, "tool");
        synchronized (ACTIVE_KEYS) {
            if (ACTIVE_KEYS.contains(key)) {
                Toast.makeText(activity, "That plugin is already open", Toast.LENGTH_SHORT).show();
                return false;
            }
            ACTIVE_KEYS.add(key);
        }
        try {
            View surface = LayoutInflater.from(activity).inflate(layoutId, null, false);
            Object controller = bindController(activity, surface, toolId, output);
            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(false);
            scroll.addView(surface, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT));

            MovableDialogChrome.Chrome chrome = MovableDialogChrome.create(activity, scroll, windowStyle);
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                    .setView(chrome.root);
            if (!MovableDialogChrome.isFullStyle(windowStyle)) {
                builder.setPositiveButton("Close", null);
            }
            AlertDialog dialog = builder.create();
            dialog.setOnDismissListener(d -> {
                stopController(controller);
                ACTIVE_KEYS.remove(key);
            });
            if (chrome.closeButton != null) chrome.closeButton.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
            MovableDialogChrome.applyWindowStyle(dialog, windowStyle, windowFit);
            MovableDialogChrome.enable(dialog, chrome.dragHandle);
            if (output != null) output.appendOutput("[plugins] Opened " + title + " plugin surface.\n");
            return true;
        } catch (Throwable t) {
            ACTIVE_KEYS.remove(key);
            Toast.makeText(activity, "Plugin surface failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
            if (output != null) output.appendOutput("[plugins] Plugin surface failed: " + safeMessage(t) + "\n");
            return false;
        }
    }

    public static int layoutFor(String toolId) {
        if ("calculator".equals(toolId)) return R.layout.plugin_tool_calculator;
        if ("ascii_hex".equals(toolId)) return R.layout.plugin_tool_ascii_hex_converter;
        if ("alarms_timers".equals(toolId)) return R.layout.plugin_tool_alarms_timers;
        return 0;
    }

    public static String titleFor(String toolId) {
        if ("calculator".equals(toolId)) return "Calculator";
        if ("ascii_hex".equals(toolId)) return "ASCII / Hex Converter";
        if ("alarms_timers".equals(toolId)) return "Alarms and Timers";
        return "Plugin Tool";
    }

    public static Object bindController(Activity activity, View surface, String toolId, PluginActionRegistry.Output output) {
        if ("calculator".equals(toolId)) {
            ToolsCalculatorController controller = new ToolsCalculatorController(new ToolsCalculatorController.Host() {
                @Override public Activity getActivity() { return activity; }
                @Override public View getRootView() { return surface; }
                @Override public void appendOutput(String message) { if (output != null) output.appendOutput(message); }
            });
            controller.bind();
            return controller;
        }
        if ("ascii_hex".equals(toolId)) {
            ToolsAsciiHexConverterController controller = new ToolsAsciiHexConverterController(new ToolsAsciiHexConverterController.Host() {
                @Override public Activity getActivity() { return activity; }
                @Override public View getRootView() { return surface; }
                @Override public void appendOutput(String message) { if (output != null) output.appendOutput(message); }
            });
            controller.bind();
            return controller;
        }
        if ("alarms_timers".equals(toolId)) {
            ToolsAlarmsTimersController controller = new ToolsAlarmsTimersController(new ToolsAlarmsTimersController.Host() {
                @Override public Activity getActivity() { return activity; }
                @Override public View getRootView() { return surface; }
                @Override public void appendOutput(String message) { if (output != null) output.appendOutput(message); }
            });
            controller.bind();
            return controller;
        }
        return null;
    }

    public static void stopController(Object controller) {
        try {
            if (controller instanceof ToolsAlarmsTimersController) {
                ((ToolsAlarmsTimersController) controller).stop();
            }
        } catch (Throwable ignored) {
        }
    }

    private static String clean(String value, String fallback) {
        if (TextUtils.isEmpty(value)) return fallback;
        return value.trim();
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        return msg == null || msg.trim().isEmpty() ? t.toString() : msg;
    }
}
