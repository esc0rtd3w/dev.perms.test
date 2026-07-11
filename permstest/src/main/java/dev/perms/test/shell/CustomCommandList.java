package dev.perms.test.shell;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * List-ordering and de-duplication helpers for Shell tab custom commands.
 */
public final class CustomCommandList {
    private CustomCommandList() {
    }

    public static void normalizeOrders(List<CustomCommand> commands) {
        try {
            if (commands == null) return;
            int pinnedOrder = 0;
            int normalOrder = 0;
            for (CustomCommand command : commands) {
                if (command == null) continue;
                if (command.pinned) command.order = pinnedOrder++;
            }
            for (CustomCommand command : commands) {
                if (command == null) continue;
                if (!command.pinned) command.order = normalOrder++;
            }
        } catch (Throwable ignored) {
        }
    }

    public static void sortForDisplay(List<CustomCommand> commands) {
        try {
            if (commands == null) return;
            Collections.sort(commands, new Comparator<CustomCommand>() {
                @Override
                public int compare(CustomCommand a, CustomCommand b) {
                    if (a == null && b == null) return 0;
                    if (a == null) return 1;
                    if (b == null) return -1;
                    if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                    if (a.order != b.order) return Integer.compare(a.order, b.order);
                    String sa = a.displayName();
                    String sb = b.displayName();
                    return sa.compareToIgnoreCase(sb);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static CustomCommand findByCommand(List<CustomCommand> commands, String commandText) {
        try {
            if (commands == null || commandText == null) return null;
            String target = commandText.trim();
            for (CustomCommand command : commands) {
                if (command == null) continue;
                if (command.cmd != null && command.cmd.trim().equals(target)) return command;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void saveOrPromote(List<CustomCommand> commands, String commandText, int maxCommands) {
        try {
            if (commands == null || commandText == null) return;
            String command = commandText.trim();
            if (command.isEmpty()) return;

            CustomCommand existing = findByCommand(commands, command);
            if (existing != null) {
                commands.remove(existing);
                if (existing.pinned) {
                    commands.add(0, existing);
                } else {
                    commands.add(firstUnpinnedIndex(commands), existing);
                }
            } else {
                commands.add(firstUnpinnedIndex(commands), new CustomCommand("", command, false, 0));
            }

            cap(commands, maxCommands);
            normalizeOrders(commands);
        } catch (Throwable ignored) {
        }
    }

    public static void applyImported(List<CustomCommand> target, List<CustomCommand> imported, boolean replace, int maxCommands) {
        try {
            if (target == null || imported == null) return;

            if (replace) {
                target.clear();
                for (CustomCommand command : imported) {
                    if (command == null) continue;
                    String cmd = command.cmd == null ? "" : command.cmd.trim();
                    if (!cmd.isEmpty() && findByCommand(target, cmd) == null) target.add(command);
                }
            } else {
                // Merge: keep existing order, append any new items.
                for (CustomCommand command : imported) {
                    if (command == null) continue;
                    String cmd = command.cmd == null ? "" : command.cmd.trim();
                    if (cmd.isEmpty()) continue;
                    if (findByCommand(target, cmd) == null) {
                        target.add(new CustomCommand(command.name, cmd, command.pinned, command.order));
                    }
                }
            }

            cap(target, maxCommands);
            sortForDisplay(target);
            normalizeOrders(target);
        } catch (Throwable ignored) {
        }
    }

    public static void cap(List<CustomCommand> commands, int maxCommands) {
        try {
            if (commands == null || maxCommands <= 0) return;
            while (commands.size() > maxCommands) {
                commands.remove(commands.size() - 1);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int firstUnpinnedIndex(List<CustomCommand> commands) {
        int index = 0;
        if (commands == null) return index;
        for (CustomCommand command : commands) {
            if (command != null && command.pinned) index++;
        }
        return index;
    }
}
