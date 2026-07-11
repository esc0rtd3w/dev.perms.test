package dev.perms.test.plugins;

import android.app.Activity;
import android.content.SharedPreferences;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.plugins.runtime.DeclarativePluginRuntime;
import dev.perms.test.plugins.runtime.ScriptPluginRuntime;
import dev.perms.test.plugins.runtime.TrustedDexPluginRuntime;

/** Dispatches validated manifest actions to native PermsTest plugin handlers. */
public final class PluginActionRegistry {
    public interface Output {
        void appendOutput(String message);
    }

    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        SharedPreferences getSharedPreferences();
        void appendOutput(String message);
        boolean shouldRunPluginInPanel(String pluginId);
        boolean showPluginTextPanel(String panelKey, String title, String subtitle, String text, String syntax, String windowStyle, String windowFit);
        boolean openPermsTestTool(String pluginId, String toolId, boolean requestPanel, String windowStyle, String windowFit);
        void runShellCommandCapture(String command, ShellCallback callback);
    }

    private PluginActionRegistry() {
    }

    public static boolean execute(Host host, PluginManifest plugin, PluginAction action) {
        if (host == null || plugin == null || action == null) return false;
        String capabilityProblem = PluginRuntimePolicy.capabilityDispatchProblem(plugin, action);
        if (capabilityProblem != null && capabilityProblem.length() > 0) {
            host.appendOutput(PluginRuntimePolicy.capabilityBlockedMessage(capabilityProblem));
            return false;
        }
        if (action.isDeclarativeAction()) {
            return DeclarativePluginRuntime.run(host, plugin, action);
        }
        if (action.isScriptAction()) {
            if (!PluginRuntimePolicy.isScriptRuntimeEnabled(host.getSharedPreferences())) {
                host.appendOutput(PluginRuntimePolicy.scriptRuntimeBlockedMessage(plugin.id, action.id));
                return false;
            }
            String approvalProblem = PluginRuntimePolicy.scriptApprovalProblem(host.getSharedPreferences(), plugin, action);
            if (approvalProblem != null && approvalProblem.length() > 0) {
                host.appendOutput(PluginRuntimePolicy.scriptApprovalBlockedMessage(approvalProblem));
                return false;
            }
            return ScriptPluginRuntime.run(host, plugin, action);
        }
        if (action.isTrustedDexAction()) {
            if (!PluginRuntimePolicy.isTrustedDexRuntimeEnabled(host.getSharedPreferences())) {
                host.appendOutput(PluginRuntimePolicy.trustedDexBlockedMessage(plugin.id, action.id));
                return false;
            }
            String trustedProblem = PluginRuntimePolicy.trustedDexApprovalProblem(host.getSharedPreferences(), plugin, action);
            if (trustedProblem != null && trustedProblem.length() > 0) {
                host.appendOutput(PluginRuntimePolicy.trustedDexApprovalBlockedMessage(trustedProblem));
                return false;
            }
            return TrustedDexPluginRuntime.run(host, plugin, action);
        }
        if (!action.isNativeAction()) {
            host.appendOutput("[plugins] Unsupported action type for " + plugin.id + ": " + action.type + "\n");
            return false;
        }
        if (BuildPropPluginAction.HANDLER.equals(action.handler)) {
            BuildPropPluginAction.run(host, plugin, action);
            return true;
        }
        if (DeviceInfoPluginAction.HANDLER.equals(action.handler)) {
            DeviceInfoPluginAction.run(host, plugin, action);
            return true;
        }
        if (LogSnapshotPluginAction.HANDLER.equals(action.handler)) {
            LogSnapshotPluginAction.run(host, plugin, action);
            return true;
        }
        if (ToolGroupPluginAction.handles(action.handler)) {
            ToolGroupPluginAction.run(host, plugin, action);
            return true;
        }
        host.appendOutput("[plugins] No native handler registered for " + plugin.id + ": " + action.handler + "\n");
        return false;
    }
}
