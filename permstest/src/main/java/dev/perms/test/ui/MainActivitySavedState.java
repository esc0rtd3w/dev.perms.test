package dev.perms.test.ui;

import android.os.Bundle;

import java.io.File;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.files.FilesBrowserController;
import dev.perms.test.memory.MemoryActivityControllers;

/** Owns small MainActivity UI state save/restore details. */
public final class MainActivitySavedState {
    private static final String STATE_TAB_INDEX = "state_tab_index";
    private static final String STATE_LAST_SAVED_PATH = "state_last_saved_path";
    private static final String STATE_OUTPUT_TEXT = "state_output_text";
    private static final String STATE_OUTPUT_HEIGHT_PX = "state_output_height_px";
    private static final String STATE_FILES_LEFT_CWD = "state_files_left_cwd";
    private static final String STATE_FILES_RIGHT_CWD = "state_files_right_cwd";
    private static final String STATE_FILES_SPLIT = "state_files_split";
    private static final String STATE_FILES_ACTIVE_RIGHT = "state_files_active_right";
    private static final String STATE_APP_FILTER_TEXT = "state_app_filter_text";

    private MainActivitySavedState() {
    }

    public static Restored restore(Bundle savedState,
                                   ActivityMainBinding binding,
                                   MemoryActivityControllers memoryControllers) {
        Restored out = new Restored();
        if (savedState == null) return out;

        out.currentTabIndex = savedState.getInt(STATE_TAB_INDEX, 0);
        String lastPath = savedState.getString(STATE_LAST_SAVED_PATH, null);
        if (lastPath != null && !lastPath.trim().isEmpty()) {
            File f = new File(lastPath);
            if (f.exists()) out.lastSavedFile = f;
        }
        try {
            int h = savedState.getInt(STATE_OUTPUT_HEIGHT_PX, -1);
            if (h > 0) out.outputHeightPx = h;
        } catch (Throwable ignored) {
        }

        // Keep the main status/log panel clean when the app is opened again.
        // Fresh actions append new output immediately, so stale saved output is not restored here.
        try {
            if (binding != null && binding.txtOutput != null) binding.txtOutput.setText("");
        } catch (Throwable ignored) {
        }

        try { out.filesLeftCwd = savedState.getString(STATE_FILES_LEFT_CWD, null); } catch (Throwable ignored) {}
        try { out.filesRightCwd = savedState.getString(STATE_FILES_RIGHT_CWD, null); } catch (Throwable ignored) {}
        try { out.filesSplit = savedState.containsKey(STATE_FILES_SPLIT) ? savedState.getBoolean(STATE_FILES_SPLIT) : null; } catch (Throwable ignored) {}
        try { out.filesActiveRight = savedState.containsKey(STATE_FILES_ACTIVE_RIGHT) ? savedState.getBoolean(STATE_FILES_ACTIVE_RIGHT) : null; } catch (Throwable ignored) {}
        try { out.appFilterText = savedState.getString(STATE_APP_FILTER_TEXT, null); } catch (Throwable ignored) {}
        try {
            if (memoryControllers != null) memoryControllers.restoreActivityState(savedState);
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void save(Bundle outState,
                            int currentTabIndex,
                            int currentOutputHeightPx,
                            File lastSavedFile,
                            FilesBrowserController filesBrowserController,
                            ActivityMainBinding binding,
                            MemoryActivityControllers memoryControllers) {
        if (outState == null) return;
        outState.putInt(STATE_TAB_INDEX, currentTabIndex);
        try {
            if (currentOutputHeightPx > 0) outState.putInt(STATE_OUTPUT_HEIGHT_PX, currentOutputHeightPx);
        } catch (Throwable ignored) {
        }
        if (lastSavedFile != null) {
            outState.putString(STATE_LAST_SAVED_PATH, lastSavedFile.getAbsolutePath());
        }
        try {
            if (binding != null && binding.txtOutput != null) {
                CharSequence t = binding.txtOutput.getText();
                outState.putString(STATE_OUTPUT_TEXT, t == null ? "" : t.toString());
            }
        } catch (Throwable ignored) {
        }

        try {
            if (filesBrowserController != null) {
                String leftCwd = filesBrowserController.getLeftCwd();
                String rightCwd = filesBrowserController.getRightCwd();
                if (leftCwd != null) outState.putString(STATE_FILES_LEFT_CWD, leftCwd);
                if (rightCwd != null) outState.putString(STATE_FILES_RIGHT_CWD, rightCwd);
                outState.putBoolean(STATE_FILES_SPLIT, filesBrowserController.isSplitChecked());
                outState.putBoolean(STATE_FILES_ACTIVE_RIGHT, filesBrowserController.isActiveRight());
            }
            if (binding != null && binding.tabPackages != null
                    && binding.tabPackages.edtAppFilter != null
                    && binding.tabPackages.edtAppFilter.getText() != null) {
                outState.putString(STATE_APP_FILTER_TEXT, binding.tabPackages.edtAppFilter.getText().toString());
            }
            if (memoryControllers != null) memoryControllers.saveActivityState(outState);
        } catch (Throwable ignored) {
        }
    }

    public static final class Restored {
        public int currentTabIndex;
        public int outputHeightPx = -1;
        public File lastSavedFile;
        public String filesLeftCwd;
        public String filesRightCwd;
        public Boolean filesSplit;
        public Boolean filesActiveRight;
        public String appFilterText;
    }
}
