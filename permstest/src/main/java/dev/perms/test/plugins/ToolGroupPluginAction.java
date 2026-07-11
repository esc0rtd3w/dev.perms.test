package dev.perms.test.plugins;

/** Native plugin action that opens a plugin-owned PermsTest tool surface through the host API. */
final class ToolGroupPluginAction {
    static final String HANDLER_CALCULATOR = "open_calculator_tool";
    static final String HANDLER_ASCII_HEX = "open_ascii_hex_tool";
    static final String HANDLER_ALARMS_TIMERS = "open_alarms_timers_tool";

    private ToolGroupPluginAction() {
    }

    static boolean handles(String handler) {
        return HANDLER_CALCULATOR.equals(handler)
                || HANDLER_ASCII_HEX.equals(handler)
                || HANDLER_ALARMS_TIMERS.equals(handler);
    }

    static void run(PluginActionRegistry.Host host, PluginManifest plugin, PluginAction action) {
        if (host == null || plugin == null || action == null) return;
        String toolId = toolIdFor(action.handler);
        boolean largeOverride = host.shouldRunPluginInPanel(plugin.id);
        boolean requestedPanel = action.isWindowPresentation() || largeOverride;
        String windowStyle = largeOverride ? "full" : firstNonEmpty(action.windowStyle, plugin.windowStyle);
        String windowFit = largeOverride ? "current" : firstNonEmpty(action.windowFit, plugin.windowFit);
        boolean opened = host.openPermsTestTool(plugin.id, toolId, requestedPanel, windowStyle, windowFit);
        host.appendOutput("[plugins] " + plugin.name + " requested native plugin tool surface: " + toolId
                + (requestedPanel ? " (large/window requested)" : "")
                + (opened ? "\n" : " failed\n"));
    }

    private static String firstNonEmpty(String a, String b) {
        return a == null || a.trim().isEmpty() ? (b == null ? "" : b.trim()) : a.trim();
    }

    private static String toolIdFor(String handler) {
        if (HANDLER_CALCULATOR.equals(handler)) return "calculator";
        if (HANDLER_ASCII_HEX.equals(handler)) return "ascii_hex";
        if (HANDLER_ALARMS_TIMERS.equals(handler)) return "alarms_timers";
        return handler == null ? "" : handler;
    }
}
