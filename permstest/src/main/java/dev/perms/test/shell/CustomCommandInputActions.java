package dev.perms.test.shell;

import java.util.List;

/**
 * Save-command input action for the Shell tab.
 */
public final class CustomCommandInputActions {
    public interface Host {
        String getCommandText();
        void persistCommands();
        void renderCommands();
        void toast(String message);
        void appendOutput(String text);
    }

    private static final int CUSTOM_COMMANDS_MAX = 50;

    private final Host host;
    private final List<CustomCommand> commands;

    public CustomCommandInputActions(Host host, List<CustomCommand> commands) {
        this.host = host;
        this.commands = commands;
    }

    public void saveCurrentCommand() {
        try {
            String cmd = host == null ? "" : host.getCommandText();
            cmd = cmd == null ? "" : cmd.trim();
            if (cmd.isEmpty()) {
                if (host != null) host.toast("Nothing to save");
                return;
            }

            CustomCommandList.saveOrPromote(commands, cmd, CUSTOM_COMMANDS_MAX);
            if (host != null) {
                host.persistCommands();
                host.renderCommands();
                host.toast("Saved");
            }
        } catch (Throwable t) {
            if (host != null) {
                host.appendOutput("[!] Save failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            }
        }
    }
}
