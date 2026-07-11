package dev.perms.test.shell;

import android.text.Editable;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Coordinates the Shell tab custom-command UI, persistence, dialogs, and dispatch.
 */
public final class CustomCommandController {
    public interface Host {
        String getTargetPackage();
        boolean isSafeToken(String token);
        void runShellCommand(String command);
        void toast(String message);
        void appendOutput(String text);
        int dp(int value);
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final String prefsName;
    private final String prefsKey;
    private final ArrayList<CustomCommand> commands = new ArrayList<>();

    private ActivityMainBinding binding;
    private CustomCommandUi ui;
    private CustomCommandDialogs dialogs;
    private CustomCommandImportExport importExport;
    private CustomCommandRunner runner;
    private CustomCommandInputActions inputActions;

    public CustomCommandController(AppCompatActivity activity, String prefsName, String prefsKey, Host host) {
        if (activity == null) throw new IllegalArgumentException("activity == null");
        if (host == null) throw new IllegalArgumentException("host == null");
        this.activity = activity;
        this.host = host;
        this.prefsName = prefsName;
        this.prefsKey = prefsKey;
    }

    public void registerActivityResults() {
        try {
            getImportExport().registerActivityResults();
        } catch (Throwable ignored) {
        }
    }

    public void bind(ActivityMainBinding binding) {
        this.binding = binding;
        try {
            loadFromPrefs();
            bindSaveButton();
            bindRecycler();
            bindImportExport();
            render();
        } catch (Throwable ignored) {
        }
    }

    public void runAndFill(String command) {
        getRunner().runAndFill(command);
    }

    private void bindSaveButton() {
        try {
            if (binding == null || binding.tabShell == null || binding.tabShell.btnSaveCmd == null) return;
            binding.tabShell.btnSaveCmd.setOnClickListener(v -> getInputActions().saveCurrentCommand());
        } catch (Throwable ignored) {
        }
    }

    private void bindRecycler() {
        try {
            if (binding == null || binding.tabShell == null || binding.tabShell.customCmdRecycler == null) return;
            getUi().bind(binding.tabShell.customCmdRecycler, binding.tabShell.txtCustomCmdEmpty);
        } catch (Throwable ignored) {
        }
    }

    private void bindImportExport() {
        try {
            if (binding == null || binding.tabShell == null) return;
            getImportExport().bind(binding.tabShell.btnExportCustomCmds, binding.tabShell.btnImportCustomCmds);
        } catch (Throwable ignored) {
        }
    }

    private void loadFromPrefs() {
        CustomCommandPersistence.load(activity, prefsName, prefsKey, commands);
    }

    private void persist() {
        CustomCommandPersistence.persist(activity, prefsName, prefsKey, commands);
    }

    private void normalize() {
        CustomCommandList.normalizeOrders(commands);
    }

    private CustomCommand findByCommand(String command) {
        return CustomCommandList.findByCommand(commands, command);
    }

    private void render() {
        try {
            if (binding == null || binding.tabShell == null || binding.tabShell.customCmdRecycler == null) return;
            getUi().render(binding.tabShell.txtCustomCmdEmpty);
        } catch (Throwable ignored) {
        }
    }

    private CustomCommandRunner getRunner() {
        if (runner == null) {
            runner = new CustomCommandRunner(new CustomCommandRunner.Host() {
                @Override
                public String getTargetPackage() {
                    return host.getTargetPackage();
                }

                @Override
                public boolean isSafeToken(String token) {
                    return host.isSafeToken(token);
                }

                @Override
                public void setCommandText(String command) {
                    setEditorCommandText(command);
                }

                @Override
                public void runShellCommand(String command) {
                    host.runShellCommand(command);
                }

                @Override
                public void toast(String message) {
                    host.toast(message);
                }
            });
        }
        return runner;
    }

    private CustomCommandUi getUi() {
        if (ui == null) {
            ui = new CustomCommandUi(activity, activity.getLayoutInflater(), commands, new CustomCommandUi.Host() {
                @Override
                public void run(CustomCommand command) {
                    getRunner().run(command);
                }

                @Override
                public void showMenu(CustomCommand command) {
                    getDialogs().showSubCommandMenu(command);
                }

                @Override
                public void showManageMenu(CustomCommand command) {
                    getDialogs().showManageMenu(command);
                }

                @Override
                public void onCommandsReordered() {
                    normalize();
                    persist();
                    render();
                }
            });
        }
        return ui;
    }

    private CustomCommandDialogs getDialogs() {
        if (dialogs == null) {
            dialogs = new CustomCommandDialogs(activity, commands, new CustomCommandDialogs.Host() {
                @Override
                public void runAndFillCommand(String command) {
                    getRunner().runAndFill(command);
                }

                @Override
                public void normalizeCommands() {
                    normalize();
                }

                @Override
                public void persistCommands() {
                    persist();
                }

                @Override
                public void renderCommands() {
                    render();
                }

                @Override
                public CustomCommand findByCommand(String command) {
                    return CustomCommandController.this.findByCommand(command);
                }

                @Override
                public void appendOutput(String text) {
                    host.appendOutput(text);
                }

                @Override
                public void syncEditedCommandText(String oldCommand, String newCommand) {
                    syncEditedCommandTextIfCurrent(oldCommand, newCommand);
                }

                @Override
                public int dp(int value) {
                    return host.dp(value);
                }
            });
        }
        return dialogs;
    }

    private CustomCommandInputActions getInputActions() {
        if (inputActions == null) {
            inputActions = new CustomCommandInputActions(new CustomCommandInputActions.Host() {
                @Override
                public String getCommandText() {
                    return getEditorCommandText();
                }

                @Override
                public void persistCommands() {
                    persist();
                }

                @Override
                public void renderCommands() {
                    render();
                }

                @Override
                public void toast(String message) {
                    host.toast(message);
                }

                @Override
                public void appendOutput(String text) {
                    host.appendOutput(text);
                }
            }, commands);
        }
        return inputActions;
    }

    private CustomCommandImportExport getImportExport() {
        if (importExport == null) {
            importExport = new CustomCommandImportExport(activity, commands, new CustomCommandImportExport.Host() {
                @Override
                public void persistCommands() {
                    persist();
                }

                @Override
                public void renderCommands() {
                    render();
                }

                @Override
                public void appendOutput(String text) {
                    host.appendOutput(text);
                }
            });
        }
        return importExport;
    }

    private String getEditorCommandText() {
        try {
            if (binding == null || binding.tabShell == null || binding.tabShell.edtCmd == null) return "";
            Editable editable = binding.tabShell.edtCmd.getText();
            return editable == null ? "" : editable.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void setEditorCommandText(String command) {
        try {
            if (binding == null || binding.tabShell == null || binding.tabShell.edtCmd == null) return;
            String text = command == null ? "" : command;
            binding.tabShell.edtCmd.setText(text);
            if (binding.tabShell.edtCmd.getText() != null) {
                binding.tabShell.edtCmd.setSelection(text.length());
            }
        } catch (Throwable ignored) {
        }
    }

    private void syncEditedCommandTextIfCurrent(String oldCommand, String newCommand) {
        try {
            String current = getEditorCommandText();
            String oldText = oldCommand == null ? "" : oldCommand;
            if (current != null && current.trim().equals(oldText.trim())) {
                setEditorCommandText(newCommand);
            }
        } catch (Throwable ignored) {
        }
    }
}
