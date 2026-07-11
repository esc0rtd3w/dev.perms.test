package dev.perms.test.tools;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.savedata.SaveDataEditorController;
import dev.perms.test.tools.activity.ToolsActivityManagerController;
import dev.perms.test.tools.hex.FileHexEditorController;
import dev.perms.test.tools.intent.ToolsIntentLauncherController;
import dev.perms.test.tools.intentreceiver.ToolsIntentReceiverTesterController;
import dev.perms.test.tools.permissions.ToolsPermissionsTesterController;
import dev.perms.test.tools.root.ToolsRootCheckerController;
import dev.perms.test.tools.screenshot.ToolsScreenshotController;
import dev.perms.test.tools.system.ToolsSystemAnalyzerController;
import dev.perms.test.tools.text.ToolsTextEditorController;

/** Activity-side holder for the Tools tab controllers. */
public final class ToolsActivityControllers {
    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        void appendOutput(String message);
        boolean isDebugOutputEnabled();
        void debugOutput(String area, String message);
        SharedPreferences getSharedPreferences();
        void runShellCommandCapture(String command, ShellCallback callback);
        void showTab(int index);
    }

    private final Host host;
    private ToolsRootCheckerController toolsRootCheckerController;
    private ToolsSystemAnalyzerController toolsSystemAnalyzerController;
    private ToolsActivityManagerController toolsActivityManagerController;
    private ToolsIntentLauncherController toolsIntentLauncherController;
    private ToolsPermissionsTesterController toolsPermissionsTesterController;
    private ToolsIntentReceiverTesterController toolsIntentReceiverTesterController;
    private SaveDataEditorController saveDataEditorController;
    private FileHexEditorController fileHexEditorController;
    private ToolsTextEditorController toolsTextEditorController;
    private ToolsScreenshotController toolsScreenshotController;

    public ToolsActivityControllers(Host host) {
        this.host = host;
    }

    public void registerActivityResults() {
        getFileHexEditorController().registerActivityResults();
        getToolsTextEditorController().registerActivityResults();
        getToolsPermissionsTesterController().registerActivityResults();
    }

    public void bind() {
        getToolsRootCheckerController().bind();
        getToolsSystemAnalyzerController().bind();
        getToolsActivityManagerController().bind();
        getToolsIntentLauncherController().bind();
        getToolsPermissionsTesterController().bind();
        getToolsIntentReceiverTesterController().bind();
        getFileHexEditorController().bind();
        getToolsTextEditorController().bind();
        getSaveDataEditorController().bind();
        getToolsScreenshotController().bind();
    }

    public boolean handleIncomingTextEditorIntent(Intent intent) {
        return getToolsTextEditorController().handleIncomingIntent(intent);
    }

    public void stop() {
        if (toolsRootCheckerController != null) toolsRootCheckerController.stop();
        if (toolsActivityManagerController != null) toolsActivityManagerController.stop();
        if (saveDataEditorController != null) saveDataEditorController.stop();
        if (toolsScreenshotController != null) toolsScreenshotController.stop();
    }


    private ToolsRootCheckerController getToolsRootCheckerController() {
        if (toolsRootCheckerController == null) {
            toolsRootCheckerController = new ToolsRootCheckerController(new ToolsRootCheckerController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public void appendOutput(String message) {
                    if (host != null) host.appendOutput(message);
                }
            });
        }
        return toolsRootCheckerController;
    }


    private ToolsSystemAnalyzerController getToolsSystemAnalyzerController() {
        if (toolsSystemAnalyzerController == null) {
            toolsSystemAnalyzerController = new ToolsSystemAnalyzerController(new ToolsSystemAnalyzerController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public void appendOutput(String message) {
                    if (host != null) host.appendOutput(message);
                }

                @Override
                public boolean isDebugOutputEnabled() {
                    return host != null && host.isDebugOutputEnabled();
                }

                @Override
                public void debugOutput(String area, String message) {
                    if (host != null) host.debugOutput(area, message);
                }
            });
        }
        return toolsSystemAnalyzerController;
    }


    private ToolsActivityManagerController getToolsActivityManagerController() {
        if (toolsActivityManagerController == null) {
            toolsActivityManagerController = new ToolsActivityManagerController(new ToolsActivityManagerController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public void appendOutput(String message) {
                    if (host != null) host.appendOutput(message);
                }

                @Override
                public void runShellCommandCapture(String command, ToolsActivityManagerController.ShellCallback callback) {
                    if (host == null) return;
                    host.runShellCommandCapture(command, callback == null
                            ? null
                            : (code, out, err) -> callback.onComplete(code, out, err));
                }
            });
        }
        return toolsActivityManagerController;
    }


    private ToolsIntentLauncherController getToolsIntentLauncherController() {
        if (toolsIntentLauncherController == null) {
            toolsIntentLauncherController = new ToolsIntentLauncherController(new ToolsIntentLauncherController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public SharedPreferences getSharedPreferences() {
                    return host == null ? null : host.getSharedPreferences();
                }

                @Override
                public void appendOutput(String message) {
                    if (host != null) host.appendOutput(message);
                }

                @Override
                public void runShellCommandCapture(String command, ToolsIntentLauncherController.ShellCallback callback) {
                    if (host == null) return;
                    host.runShellCommandCapture(command, callback == null
                            ? null
                            : (code, out, err) -> callback.onComplete(code, out, err));
                }
            });
        }
        return toolsIntentLauncherController;
    }


    private ToolsPermissionsTesterController getToolsPermissionsTesterController() {
        if (toolsPermissionsTesterController == null) {
            toolsPermissionsTesterController = new ToolsPermissionsTesterController(new ToolsPermissionsTesterController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public void appendOutput(String message) {
                    if (host != null) host.appendOutput(message);
                }
            });
        }
        return toolsPermissionsTesterController;
    }

    private ToolsIntentReceiverTesterController getToolsIntentReceiverTesterController() {
        if (toolsIntentReceiverTesterController == null) {
            toolsIntentReceiverTesterController = new ToolsIntentReceiverTesterController(new ToolsIntentReceiverTesterController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public void appendOutput(String message) {
                    if (host != null) host.appendOutput(message);
                }
            });
        }
        return toolsIntentReceiverTesterController;
    }


    private FileHexEditorController getFileHexEditorController() {
        if (fileHexEditorController == null) {
            fileHexEditorController = new FileHexEditorController(new FileHexEditorController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public void appendOutput(String message) {
                    if (host != null) host.appendOutput(message);
                }

                @Override
                public void showTab(int index) {
                    if (host != null) host.showTab(index);
                }
            });
        }
        return fileHexEditorController;
    }

    private ToolsTextEditorController getToolsTextEditorController() {
        if (toolsTextEditorController == null) {
            toolsTextEditorController = new ToolsTextEditorController(new ToolsTextEditorController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public void appendOutput(String message) {
                    if (host != null) host.appendOutput(message);
                }

                @Override
                public void showTab(int index) {
                    if (host != null) host.showTab(index);
                }
            });
        }
        return toolsTextEditorController;
    }

    private SaveDataEditorController getSaveDataEditorController() {
        if (saveDataEditorController == null) {
            saveDataEditorController = new SaveDataEditorController(new SaveDataEditorController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public void appendOutput(String message) {
                    if (host != null) host.appendOutput(message);
                }

                @Override
                public void runShellCommandCapture(String command, SaveDataEditorController.ShellCallback callback) {
                    if (host == null) return;
                    host.runShellCommandCapture(command, callback == null
                            ? null
                            : (code, out, err) -> callback.onComplete(code, out, err));
                }
            });
        }
        return saveDataEditorController;
    }

    private ToolsScreenshotController getToolsScreenshotController() {
        if (toolsScreenshotController == null) {
            toolsScreenshotController = new ToolsScreenshotController(new ToolsScreenshotController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public void appendOutput(String message) {
                    if (host != null) host.appendOutput(message);
                }

                @Override
                public boolean isDebugOutputEnabled() {
                    return host != null && host.isDebugOutputEnabled();
                }

                @Override
                public void debugOutput(String area, String message) {
                    if (host != null) host.debugOutput(area, message);
                }

                @Override
                public void runShellCommandCapture(String command, ToolsScreenshotController.ShellCallback callback) {
                    if (host == null) return;
                    host.runShellCommandCapture(command, callback == null
                            ? null
                            : (code, out, err) -> callback.onComplete(code, out, err));
                }
            });
        }
        return toolsScreenshotController;
    }


}
