package dev.perms.test.network;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;

import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.databinding.TabNetworkBinding;
import dev.perms.test.debug.DebugLog;

/**
 * Shared Activity-side accessors for Network controllers.
 *
 * Keeps binding, preferences, and shared executor/handler lookup in one small
 * base so FTP server, FTP client, and diagnostics controllers do not duplicate
 * the same null-safe Activity dependency plumbing.
 */
public abstract class NetworkControllerBase {
    protected final NetworkActivityDependencies dependencies;

    protected NetworkControllerBase(NetworkActivityDependencies dependencies) {
        this.dependencies = dependencies;
    }

    protected Activity getActivity() {
        return dependencies == null ? null : dependencies.getActivity();
    }

    protected ActivityMainBinding getBinding() {
        return dependencies == null ? null : dependencies.getBinding();
    }

    protected TabNetworkBinding getNetworkBinding() {
        ActivityMainBinding binding = getBinding();
        return binding == null ? null : binding.tabNetwork;
    }

    protected SharedPreferences getPreferences() {
        return dependencies == null ? null : dependencies.getPreferences();
    }

    protected Handler getMainHandler() {
        return dependencies == null ? null : dependencies.getMainHandler();
    }

    protected ExecutorService getIoExecutor() {
        return dependencies == null ? null : dependencies.getIoExecutor();
    }

    protected void appendOutput(String text) {
        if (dependencies != null) dependencies.appendOutput(text);
    }

    protected void debug(String area, String message) {
        if (dependencies == null || !dependencies.isDebugOutputEnabled()) return;
        DebugLog.log(DebugLog.DEFAULT_TAG, "network", area, message);
        dependencies.appendOutput(DebugLog.line("network", area, message) + "\n");
    }

    protected void debugWarn(String area, String message) {
        if (dependencies == null || !dependencies.isDebugOutputEnabled()) return;
        DebugLog.warn(DebugLog.DEFAULT_TAG, "network", area, message);
        dependencies.appendOutput(DebugLog.line("network", area, message) + "\n");
    }
}
