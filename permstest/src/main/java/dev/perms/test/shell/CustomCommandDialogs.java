package dev.perms.test.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.perms.test.R;

/**
 * Owns custom Shell command menu/dialog behavior.
 */
public final class CustomCommandDialogs {
    public interface Host {
        void runAndFillCommand(String command);
        void normalizeCommands();
        void persistCommands();
        void renderCommands();
        CustomCommand findByCommand(String command);
        void appendOutput(String text);
        void syncEditedCommandText(String oldCommand, String newCommand);
        int dp(int value);
    }

    private final Activity activity;
    private final ArrayList<CustomCommand> commands;
    private final Host host;

    public CustomCommandDialogs(Activity activity, ArrayList<CustomCommand> commands, Host host) {
        if (activity == null) throw new IllegalArgumentException("activity == null");
        if (commands == null) throw new IllegalArgumentException("commands == null");
        if (host == null) throw new IllegalArgumentException("host == null");
        this.activity = activity;
        this.commands = commands;
        this.host = host;
    }

    public void showSubCommandMenu(final CustomCommand command) {
        try {
            if (command == null) return;

            // For custom commands, only show the 3-dot submenu if the user actually defined sub-commands.
            // Internal app buttons use the built-in preset grouping. Custom commands should remain user-owned.
            LinkedHashMap<String, String> variants = command.variants == null ? null : new LinkedHashMap<>(command.variants);
            if (variants == null || variants.isEmpty()) return;

            final ArrayList<String> labels = new ArrayList<>();
            final ArrayList<String> subCommands = new ArrayList<>();
            for (Map.Entry<String, String> entry : variants.entrySet()) {
                if (entry == null) continue;
                String label = entry.getKey();
                String value = entry.getValue();
                if (label == null) label = "";
                if (value == null) value = "";
                labels.add(label);
                subCommands.add(value);
            }

            new AlertDialog.Builder(activity)
                    .setTitle(command.displayName())
                    .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                        try {
                            if (which >= 0 && which < subCommands.size()) {
                                host.runAndFillCommand(subCommands.get(which));
                            }
                        } catch (Throwable ignored) {
                        }
                    })
                    .show();
        } catch (Throwable ignored) {
        }
    }

    public void showManageMenu(final CustomCommand command) {
        try {
            if (command == null) return;

            final boolean hasVariants = command.variants != null && !command.variants.isEmpty();

            final ArrayList<CharSequence> options = new ArrayList<>();
            options.add("Rename");
            options.add("Edit command");
            options.add("Add sub-command");
            if (hasVariants) options.add("Manage sub-commands");
            options.add(command.pinned ? "Unpin favorite" : "Pin favorite");
            options.add("Remove");

            final CharSequence[] items = options.toArray(new CharSequence[0]);
            final int pinIndex = items.length - 2;
            final int removeIndex = items.length - 1;

            new AlertDialog.Builder(activity)
                    .setTitle("Custom command")
                    .setItems(items, (dialog, which) -> {
                        try {
                            if (which == 0) {
                                promptRename(command);
                            } else if (which == 1) {
                                promptEditCommand(command);
                            } else if (which == 2) {
                                promptAddSubCommand(command);
                            } else if (hasVariants && which == 3) {
                                showManageSubCommands(command);
                            } else if (which == pinIndex) {
                                togglePinned(command);
                            } else if (which == removeIndex) {
                                confirmRemove(command);
                            }
                        } catch (Throwable ignored) {
                        }
                    })
                    .setNegativeButton(R.string.shell_action_cancel, null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void promptRename(final CustomCommand command) {
        final TextInputEditText input = new TextInputEditText(activity);
        input.setSingleLine(true);
        input.setText(command.name == null ? "" : command.name);
        if (input.getText() != null) input.setSelection(input.getText().length());

        FrameLayout wrap = paddedFrame();
        wrap.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(activity)
                .setTitle("Rename")
                .setMessage("Set a label (command stays unchanged)")
                .setView(wrap)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        String name = input.getText() == null ? "" : input.getText().toString();
                        command.name = name == null ? "" : name.trim();
                        host.normalizeCommands();
                        host.persistCommands();
                        host.renderCommands();
                        toast("Renamed");
                    } catch (Throwable t) {
                        host.appendOutput("[!] Rename failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
                    }
                })
                .setNeutralButton("Details", (dialog, which) -> {
                    try {
                        String msg = "Name: " + (command.name == null ? "" : command.name)
                                + "\n\nCommand:\n" + (command.cmd == null ? "" : command.cmd);
                        new AlertDialog.Builder(activity)
                                .setTitle("Custom command")
                                .setMessage(msg)
                                .setPositiveButton("OK", null)
                                .show();
                    } catch (Throwable ignored) {
                    }
                })
                .setNegativeButton(R.string.shell_action_cancel, null)
                .show();
    }

    private void promptEditCommand(final CustomCommand command) {
        final TextInputEditText input = new TextInputEditText(activity);
        input.setSingleLine(false);
        input.setText(command.cmd == null ? "" : command.cmd);
        if (input.getText() != null) input.setSelection(input.getText().length());

        FrameLayout wrap = paddedFrame();
        wrap.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final String oldCommand = command.cmd == null ? "" : command.cmd;

        new AlertDialog.Builder(activity)
                .setTitle("Edit command")
                .setView(wrap)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        String newCommand = input.getText() == null ? "" : input.getText().toString();
                        newCommand = newCommand == null ? "" : newCommand.trim();
                        if (newCommand.isEmpty()) {
                            toast("Command cannot be empty");
                            return;
                        }
                        CustomCommand duplicate = host.findByCommand(newCommand);
                        if (duplicate != null && duplicate != command) {
                            toast("That command already exists");
                            return;
                        }

                        command.cmd = newCommand;
                        host.persistCommands();
                        host.renderCommands();
                        host.syncEditedCommandText(oldCommand, newCommand);
                        toast("Updated");
                    } catch (Throwable t) {
                        host.appendOutput("[!] Edit failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
                    }
                })
                .setNegativeButton(R.string.shell_action_cancel, null)
                .show();
    }

    private void promptAddSubCommand(final CustomCommand command) {
        try {
            if (command == null) return;

            final String base = command.cmd == null ? "" : command.cmd.trim();
            final TextInputEditText nameInput = new TextInputEditText(activity);
            nameInput.setSingleLine(true);
            nameInput.setHint("Label (e.g. audio)");

            final TextInputEditText commandInput = new TextInputEditText(activity);
            commandInput.setSingleLine(true);
            commandInput.setHint("Command (e.g. dumpsys audio)");

            if (!base.isEmpty() && base.matches("^[a-zA-Z0-9_\\.:-]+$") ) {
                commandInput.setText(base + " ");
                if (commandInput.getText() != null) commandInput.setSelection(commandInput.getText().length());
            }

            LinearLayout wrap = paddedLinearLayout();
            wrap.addView(nameInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            commandParams.topMargin = host.dp(10);
            wrap.addView(commandInput, commandParams);

            new AlertDialog.Builder(activity)
                    .setTitle("Add sub-command")
                    .setMessage("Adds a submenu item for this custom command. The 3-dot button will appear once at least one sub-command exists.")
                    .setView(wrap)
                    .setPositiveButton("Add", (dialog, which) -> {
                        try {
                            String label = nameInput.getText() == null ? "" : nameInput.getText().toString();
                            String subCommand = commandInput.getText() == null ? "" : commandInput.getText().toString();
                            label = label == null ? "" : label.trim();
                            subCommand = subCommand == null ? "" : subCommand.trim();
                            if (label.isEmpty()) {
                                toast("Label is required");
                                return;
                            }
                            if (subCommand.isEmpty()) {
                                toast("Command is required");
                                return;
                            }

                            if (command.variants == null) command.variants = new LinkedHashMap<>();
                            for (String key : command.variants.keySet()) {
                                if (key == null) continue;
                                if (key.trim().equalsIgnoreCase(label)) {
                                    toast("That label already exists");
                                    return;
                                }
                            }

                            command.variants.put(label, subCommand);
                            host.persistCommands();
                            host.renderCommands();
                            toast("Added");
                        } catch (Throwable t) {
                            host.appendOutput("[!] Add sub-command failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
                        }
                    })
                    .setNegativeButton(R.string.shell_action_cancel, null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void showManageSubCommands(final CustomCommand command) {
        try {
            if (command == null) return;
            if (command.variants == null || command.variants.isEmpty()) {
                toast("No sub-commands");
                return;
            }

            final ArrayList<String> labels = new ArrayList<>();
            final ArrayList<String> subCommands = new ArrayList<>();
            for (Map.Entry<String, String> entry : command.variants.entrySet()) {
                if (entry == null) continue;
                String label = entry.getKey() == null ? "" : entry.getKey().trim();
                String value = entry.getValue() == null ? "" : entry.getValue().trim();
                if (label.isEmpty() || value.isEmpty()) continue;
                labels.add(label);
                subCommands.add(value);
            }
            if (labels.isEmpty()) {
                toast("No sub-commands");
                return;
            }

            new AlertDialog.Builder(activity)
                    .setTitle("Manage sub-commands")
                    .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                        try {
                            if (which < 0 || which >= labels.size()) return;
                            showSubCommandActionMenu(command, labels.get(which), subCommands.get(which));
                        } catch (Throwable ignored) {
                        }
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void showSubCommandActionMenu(final CustomCommand command, final String oldName, final String oldCommand) {
        final CharSequence[] actions = new CharSequence[]{"Run", "Edit", "Remove"};
        new AlertDialog.Builder(activity)
                .setTitle(oldName)
                .setItems(actions, (dialog, which) -> {
                    try {
                        if (which == 0) {
                            host.runAndFillCommand(oldCommand);
                        } else if (which == 1) {
                            promptEditSubCommand(command, oldName, oldCommand);
                        } else if (which == 2) {
                            confirmRemoveSubCommand(command, oldName);
                        }
                    } catch (Throwable ignored) {
                    }
                })
                .setNegativeButton(R.string.shell_action_cancel, null)
                .show();
    }

    private void promptEditSubCommand(final CustomCommand command, final String oldName, final String oldCommand) {
        try {
            if (command == null) return;

            final TextInputEditText nameInput = new TextInputEditText(activity);
            nameInput.setSingleLine(true);
            nameInput.setText(oldName == null ? "" : oldName);

            final TextInputEditText commandInput = new TextInputEditText(activity);
            commandInput.setSingleLine(true);
            commandInput.setText(oldCommand == null ? "" : oldCommand);

            LinearLayout wrap = paddedLinearLayout();
            wrap.addView(nameInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            commandParams.topMargin = host.dp(10);
            wrap.addView(commandInput, commandParams);

            new AlertDialog.Builder(activity)
                    .setTitle("Edit sub-command")
                    .setView(wrap)
                    .setPositiveButton("Save", (dialog, which) -> {
                        try {
                            String newName = nameInput.getText() == null ? "" : nameInput.getText().toString();
                            String newCommand = commandInput.getText() == null ? "" : commandInput.getText().toString();
                            newName = newName == null ? "" : newName.trim();
                            newCommand = newCommand == null ? "" : newCommand.trim();
                            if (newName.isEmpty()) {
                                toast("Label is required");
                                return;
                            }
                            if (newCommand.isEmpty()) {
                                toast("Command is required");
                                return;
                            }
                            if (command.variants == null) command.variants = new LinkedHashMap<>();

                            for (String key : command.variants.keySet()) {
                                if (key == null) continue;
                                String trimmed = key.trim();
                                if (oldName != null && trimmed.equalsIgnoreCase(oldName.trim())) continue;
                                if (trimmed.equalsIgnoreCase(newName)) {
                                    toast("That label already exists");
                                    return;
                                }
                            }

                            LinkedHashMap<String, String> rebuilt = new LinkedHashMap<>();
                            for (Map.Entry<String, String> entry : command.variants.entrySet()) {
                                if (entry == null) continue;
                                String key = entry.getKey() == null ? "" : entry.getKey();
                                String value = entry.getValue() == null ? "" : entry.getValue();
                                if (oldName != null && key.trim().equalsIgnoreCase(oldName.trim())) {
                                    rebuilt.put(newName, newCommand);
                                } else if (!key.trim().isEmpty() && !value.trim().isEmpty()) {
                                    rebuilt.put(key, value);
                                }
                            }
                            command.variants.clear();
                            command.variants.putAll(rebuilt);

                            host.persistCommands();
                            host.renderCommands();
                            toast("Updated");
                        } catch (Throwable t) {
                            host.appendOutput("[!] Edit sub-command failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
                        }
                    })
                    .setNegativeButton(R.string.shell_action_cancel, null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void confirmRemoveSubCommand(final CustomCommand command, final String name) {
        new AlertDialog.Builder(activity)
                .setTitle("Remove")
                .setMessage("Remove sub-command '" + name + "'?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    try {
                        if (command.variants != null) {
                            removeVariantByName(command, name);
                            host.persistCommands();
                            host.renderCommands();
                            toast("Removed");
                        }
                    } catch (Throwable t) {
                        host.appendOutput("[!] Remove sub-command failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
                    }
                })
                .setNegativeButton(R.string.shell_action_cancel, null)
                .show();
    }

    private void togglePinned(final CustomCommand command) {
        command.pinned = !command.pinned;

        commands.remove(command);
        if (command.pinned) {
            commands.add(0, command);
        } else {
            int insertAt = 0;
            for (CustomCommand existing : commands) {
                if (existing != null && existing.pinned) insertAt++;
            }
            commands.add(insertAt, command);
        }

        host.normalizeCommands();
        host.persistCommands();
        host.renderCommands();
        toast(command.pinned ? "Pinned" : "Unpinned");
    }

    private void confirmRemove(final CustomCommand command) {
        new AlertDialog.Builder(activity)
                .setTitle("Remove")
                .setMessage("Remove this saved command?\n\n" + (command.cmd == null ? "" : command.cmd))
                .setPositiveButton("Remove", (dialog, which) -> {
                    try {
                        commands.remove(command);
                        host.normalizeCommands();
                        host.persistCommands();
                        host.renderCommands();
                        toast("Removed");
                    } catch (Throwable t) {
                        host.appendOutput("[!] Remove failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
                    }
                })
                .setNegativeButton(R.string.shell_action_cancel, null)
                .show();
    }

    private void removeVariantByName(final CustomCommand command, final String name) {
        try {
            if (command == null || command.variants == null || name == null) return;
            String needle = name.trim();
            if (needle.isEmpty()) return;

            LinkedHashMap<String, String> rebuilt = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : command.variants.entrySet()) {
                if (entry == null) continue;
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null) key = "";
                if (value == null) value = "";
                if (key.trim().equalsIgnoreCase(needle)) continue;
                if (!key.trim().isEmpty() && !value.trim().isEmpty()) rebuilt.put(key, value);
            }
            command.variants.clear();
            command.variants.putAll(rebuilt);
        } catch (Throwable ignored) {
        }
    }

    private FrameLayout paddedFrame() {
        int pad = host.dp(8);
        FrameLayout wrap = new FrameLayout(activity);
        wrap.setPadding(pad, pad, pad, pad);
        return wrap;
    }

    private LinearLayout paddedLinearLayout() {
        int pad = host.dp(8);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(pad, pad, pad, pad);
        return wrap;
    }

    private void toast(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }
}
