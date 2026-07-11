package dev.perms.test.logging;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Lazy Activity-side controller holder for the Logging tab.
 *
 * MainActivity keeps Android lifecycle, process-wide state, and backend execution. This
 * class owns Logging controller construction and tab binding so the Activity only exposes
 * stable shared services to the logging package.
 */
public final class LoggingActivityControllers {
    private final LoggingActivityDependencies dependencies;

    private LoggingLifetimeRecorder lifetimeRecorder;
    private LoggingLifetimeActions lifetimeActions;
    private LoggingLogcatActions logcatActions;
    private LoggingOutputActions outputActions;
    private LoggingArchiveActions archiveActions;
    private RootDiagnosticsActions rootDiagnosticsActions;

    public LoggingActivityControllers(LoggingActivityDependencies dependencies) {
        this.dependencies = dependencies;
    }

    public boolean bind() {
        ActivityMainBinding binding = getBinding();
        boolean lifetimeLogEnabled = LoggingTabBinder.bind(
                binding == null ? null : binding.tabLogging,
                binding == null ? null : binding.tabSettings,
                dependencies == null ? null : dependencies.getPreferences(),
                dependencies == null ? null : dependencies.getLifetimeLogKey(),
                dependencies != null && dependencies.getInitialLifetimeLogEnabled(),
                enabled -> getLifetimeRecorder().setEnabled(enabled),
                () -> getLifetimeActions().show(),
                () -> getLifetimeActions().export(),
                () -> getLifetimeActions().confirmClear(),
                () -> getLifetimeActions().markSession(),
                () -> getArchiveActions().archive(),
                () -> getArchiveActions().confirmClear(),
                () -> getLogcatActions().run(false),
                () -> getLogcatActions().run(true),
                () -> getLogcatActions().clear(),
                () -> getLogcatActions().runVariant("all", false),
                () -> getLogcatActions().runVariant("radio", false),
                () -> getLogcatActions().runVariant("events", false),
                () -> getLogcatActions().runVariant("crash", false),
                () -> getLogcatActions().runVariant("threadtime", false),
                () -> getLogcatActions().runVariant("buffers", false),
                () -> getLogcatActions().runVariant("app", false),
                () -> getLogcatActions().runVariant("app_full", false),
                () -> getLogcatActions().runVariant("startup_network", false),
                () -> getLogcatActions().runVariant("anr_deep", false),
                () -> getLogcatActions().runVariant("anr", false),
                () -> getLogcatActions().runVariant("dropbox", false),
                () -> getLogcatActions().runVariant("activity", false),
                () -> getLogcatActions().runVariant("system_state", false),
                () -> getLogcatActions().runVariant("mem_cpu", false),
                () -> getLogcatActions().runVariant("services", false),
                () -> getLogcatActions().runVariant("notifications", false),
                () -> getLogcatActions().runVariant("memory_tools", false),
                () -> getOutputActions().save(),
                () -> runFullDiagnostic(),
                () -> getOutputActions().share(),
                () -> getRootDiagnosticsActions().checkRoot(),
                () -> getRootDiagnosticsActions().backup(),
                () -> getRootDiagnosticsActions().confirmClear());
        getLifetimeRecorder().setEnabled(lifetimeLogEnabled);
        return lifetimeLogEnabled;
    }


    public void applyRootFeaturesGate(boolean enabled) {
        ActivityMainBinding binding = getBinding();
        LoggingTabBinder.applyRootDiagnosticsGate(binding == null ? null : binding.tabLogging, enabled);
    }

    private void runFullDiagnostic() {
        if (dependencies != null) {
            dependencies.appendOutput("[i] Full Diagnostic requested. Saving output, exporting lifetime log, and dumping logcat/diagnostic captures.\n");
        }
        getOutputActions().save();
        getLifetimeActions().export();
        getLogcatActions().runFullDiagnostic();
    }

    public void logLifetime(String tag, String message) {
        getLifetimeRecorder().log(tag, message);
    }

    public void logLifetimeActionForCommand(String command) {
        getLifetimeRecorder().logActionForCommand(command);
    }

    private LoggingLifetimeRecorder getLifetimeRecorder() {
        if (lifetimeRecorder == null) {
            lifetimeRecorder = new LoggingLifetimeRecorder(dependencies == null ? null : dependencies.getActivity());
        }
        return lifetimeRecorder;
    }

    private LoggingLifetimeActions getLifetimeActions() {
        if (lifetimeActions == null) {
            lifetimeActions = new LoggingLifetimeActions(
                    dependencies == null ? null : dependencies.getActivity(),
                    getBinding(),
                    text -> {
                        if (dependencies != null) dependencies.appendOutput(text);
                    },
                    tag -> {
                        if (dependencies != null) dependencies.setOutputTag(tag);
                    },
                    file -> {
                        if (dependencies != null) dependencies.setLastSavedFile(file);
                    },
                    command -> dependencies != null && dependencies.runShellSuccess(command));
        }
        return lifetimeActions;
    }

    private LoggingLogcatActions getLogcatActions() {
        if (logcatActions == null) {
            ActivityMainBinding binding = getBinding();
            logcatActions = new LoggingLogcatActions(
                    binding == null ? null : binding.tabLogging,
                    () -> dependencies != null && dependencies.isBackendReady(),
                    () -> {
                        if (dependencies != null) dependencies.refreshStatus();
                    },
                    text -> {
                        if (dependencies != null) dependencies.appendOutput(text);
                    },
                    tag -> {
                        if (dependencies != null) dependencies.setOutputTag(tag);
                    },
                    file -> {
                        if (dependencies != null) dependencies.setLastSavedFile(file);
                    },
                    command -> {
                        if (dependencies != null) dependencies.runShellCommand(command);
                    },
                    (command, callback) -> {
                        if (dependencies != null) dependencies.runCapture(command, callback);
                    });
        }
        return logcatActions;
    }


    private LoggingArchiveActions getArchiveActions() {
        if (archiveActions == null) {
            archiveActions = new LoggingArchiveActions(
                    dependencies == null ? null : dependencies.getActivity(),
                    getBinding(),
                    text -> {
                        if (dependencies != null) dependencies.appendOutput(text);
                    },
                    () -> {
                        if (dependencies != null) dependencies.refreshStatus();
                    },
                    file -> {
                        if (dependencies != null) dependencies.setLastSavedFile(file);
                    });
        }
        return archiveActions;
    }

    private RootDiagnosticsActions getRootDiagnosticsActions() {
        if (rootDiagnosticsActions == null) {
            ActivityMainBinding binding = getBinding();
            rootDiagnosticsActions = new RootDiagnosticsActions(
                    dependencies == null ? null : dependencies.getActivity(),
                    binding == null ? null : binding.tabLogging,
                    text -> {
                        if (dependencies != null) dependencies.appendOutput(text);
                    },
                    file -> {
                        if (dependencies != null) dependencies.setLastSavedFile(file);
                    });
        }
        return rootDiagnosticsActions;
    }

    private LoggingOutputActions getOutputActions() {
        if (outputActions == null) {
            outputActions = new LoggingOutputActions(
                    dependencies == null ? null : dependencies.getActivity(),
                    getBinding(),
                    text -> {
                        if (dependencies != null) dependencies.appendOutput(text);
                    },
                    () -> {
                        if (dependencies != null) dependencies.refreshStatus();
                    },
                    () -> dependencies == null ? null : dependencies.getOutputTag(),
                    () -> dependencies == null ? null : dependencies.getLastSavedFile(),
                    file -> {
                        if (dependencies != null) dependencies.setLastSavedFile(file);
                    });
        }
        return outputActions;
    }

    private ActivityMainBinding getBinding() {
        return dependencies == null ? null : dependencies.getBinding();
    }
}
