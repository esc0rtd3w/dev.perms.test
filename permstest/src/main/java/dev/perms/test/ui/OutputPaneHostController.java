package dev.perms.test.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.settings.SettingsPreferenceKeys;

/**
 * Owns MainActivity's shared bottom output pane text and resize state.
 *
 * This keeps the activity focused on orchestration while preserving the same
 * preference keys and runtime behavior used by older builds.
 */
public final class OutputPaneHostController {
    public interface State {
        boolean isOutputDisabled();
        boolean shouldTruncateOutput();
    }

    private final Context context;
    private final Handler mainHandler;
    private final int maxChars;
    private final State state;

    private ActivityMainBinding binding;
    private OutputPaneController outputPaneController;
    private OutputPaneResizer outputPaneResizer;
    private int currentOutputHeightPx = -1;
    private int outputRestoreHeightPx = -1;
    private boolean outputMinimized;

    public OutputPaneHostController(Context context, Handler mainHandler, int maxChars, State state) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.maxChars = maxChars;
        this.state = state;
    }

    public void bind(ActivityMainBinding binding) {
        this.binding = binding;
        try {
            if (binding != null) {
                getOutputPaneController().bind(binding.txtOutput, binding.scrollOutput);
            }
        } catch (Throwable ignored) {
        }
    }

    public int getCurrentOutputHeightPx() {
        return currentOutputHeightPx;
    }

    public void restoreInstanceOutputHeight(int heightPx) {
        if (heightPx > 0) currentOutputHeightPx = heightPx;
    }

    public void restorePanelStateFromPrefs(SharedPreferences sp) {
        try {
            if (sp == null || !sp.getBoolean(SettingsPreferenceKeys.REMEMBER_OUTPUT_HEIGHT, true)) return;
            int savedHeightPx = sp.getInt(SettingsPreferenceKeys.OUTPUT_HEIGHT_PX, -1);
            int savedRestoreHeightPx = sp.getInt(SettingsPreferenceKeys.OUTPUT_RESTORE_HEIGHT_PX, -1);
            if (savedHeightPx > 0) currentOutputHeightPx = savedHeightPx;
            if (savedRestoreHeightPx > 0) outputRestoreHeightPx = savedRestoreHeightPx;
            outputMinimized = sp.getBoolean(SettingsPreferenceKeys.OUTPUT_MINIMIZED, false);
        } catch (Throwable ignored) {
        }
    }

    public void setupResizer() {
        try {
            if (binding == null || binding.outputPanel == null || binding.logResizeBar == null) return;
            getOutputPaneResizer().bind(binding.outputPanel, binding.logResizeBar,
                    currentOutputHeightPx, outputMinimized, outputRestoreHeightPx);
            applyResizeHandleTheme();
        } catch (Throwable ignored) {
        }
    }

    public void applyResizeHandleTheme() {
        try {
            if (binding == null || binding.logResizeHandle == null) return;
            getOutputPaneResizer().applyHandleTheme(binding.logResizeHandle);
        } catch (Throwable ignored) {
        }
    }

    public void resetOutputPanelHeight() {
        try {
            getOutputPaneResizer().resetToDefaultHeight();
        } catch (Throwable ignored) {
        }
    }

    public void appendOutput(String msg) {
        getOutputPaneController().append(msg);
    }

    public void clearOutput() {
        getOutputPaneController().clear();
    }

    public void copyOutputToClipboard() {
        getOutputPaneController().copyToClipboard();
    }

    private OutputPaneController getOutputPaneController() {
        if (outputPaneController == null) {
            outputPaneController = new OutputPaneController(context, mainHandler, maxChars, new OutputPaneController.State() {
                @Override
                public boolean isOutputDisabled() {
                    return state != null && state.isOutputDisabled();
                }

                @Override
                public boolean shouldTruncateOutput() {
                    return state == null || state.shouldTruncateOutput();
                }
            });
        }
        return outputPaneController;
    }

    private OutputPaneResizer getOutputPaneResizer() {
        if (outputPaneResizer == null) {
            outputPaneResizer = new OutputPaneResizer(context, new OutputPaneResizer.State() {
                @Override
                public boolean shouldRememberHeight() {
                    try {
                        SharedPreferences sp = context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE);
                        return sp.getBoolean(SettingsPreferenceKeys.REMEMBER_OUTPUT_HEIGHT, true);
                    } catch (Throwable ignored) {
                        return true;
                    }
                }

                @Override
                public void persistStatePx(int currentHeightPx, int restoreHeightPx, boolean minimized) {
                    try {
                        if (currentHeightPx <= 0) return;
                        SharedPreferences.Editor ed = context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE).edit()
                                .putInt(SettingsPreferenceKeys.OUTPUT_HEIGHT_PX, currentHeightPx)
                                .putBoolean(SettingsPreferenceKeys.OUTPUT_MINIMIZED, minimized);
                        if (restoreHeightPx > 0) {
                            ed.putInt(SettingsPreferenceKeys.OUTPUT_RESTORE_HEIGHT_PX, restoreHeightPx);
                        }
                        ed.apply();
                        outputRestoreHeightPx = restoreHeightPx;
                        outputMinimized = minimized;
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void onHeightChanged(int px) {
                    currentOutputHeightPx = px;
                }
            });
        }
        return outputPaneResizer;
    }
}
