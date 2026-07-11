package dev.perms.test.debug;

/**
 * Optional bridge for debug output that should also be mirrored into a UI pane.
 */
public interface DebugOutput {
    boolean isEnabled();
    void appendLine(String line);
}
