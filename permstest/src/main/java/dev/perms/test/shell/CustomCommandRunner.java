package dev.perms.test.shell;

import java.util.LinkedHashMap;

/**
 * Runs Shell custom-command selections while keeping target-package expansion
 * outside of MainActivity.
 */
public final class CustomCommandRunner {
    public interface Host {
        String getTargetPackage();
        boolean isSafeToken(String token);
        void setCommandText(String command);
        void runShellCommand(String command);
        void toast(String message);
    }

    private final Host host;

    public CustomCommandRunner(Host host) {
        this.host = host;
    }

    public void run(CustomCommand command) {
        try {
            if (command == null) return;
            String base = command.cmd == null ? "" : command.cmd;
            boolean hasVariants = command.variants != null && !command.variants.isEmpty();
            runAndFill(hasVariants ? ShellCommandVariants.defaultHelpCommand(base) : base);
        } catch (Throwable ignored) {
        }
    }

    public void runAndFill(String command) {
        try {
            String expanded = command == null ? "" : command;

            if (expanded.contains("{pkg}")) {
                String pkg = host == null ? null : host.getTargetPackage();
                if (isEmpty(pkg) || host == null || !host.isSafeToken(pkg)) {
                    expanded = expanded.replace("{pkg}", "").trim();
                    if (host != null) {
                        host.setCommandText(expanded);
                        host.toast("Enter/select a target package first.");
                    }
                    return;
                }
                expanded = expanded.replace("{pkg}", pkg);
            }

            if (host != null) {
                host.setCommandText(expanded);
                host.runShellCommand(expanded);
            }
        } catch (Throwable ignored) {
        }
    }

    public LinkedHashMap<String, String> getVariantCommands(CustomCommand command) {
        try {
            if (command == null) return null;

            if (command.variants != null && !command.variants.isEmpty()) {
                return new LinkedHashMap<>(command.variants);
            }

            String base = command.cmd == null ? "" : command.cmd.trim();
            return ShellCommandVariants.forBaseCommand(base);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
