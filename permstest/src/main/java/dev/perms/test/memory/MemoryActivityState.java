package dev.perms.test.memory;

import android.os.Bundle;

import dev.perms.test.databinding.TabMemoryBinding;

/**
 * Small Activity-instance state holder for Memory tab UI restore values.
 *
 * This keeps the saved-instance keys and extraction logic with the Memory tab while the
 * Activity still owns the Android lifecycle callbacks that provide the Bundle.
 */
public final class MemoryActivityState {
    private static final String STATE_TARGET_PACKAGE = "state_memory_target_pkg";
    private static final String STATE_PROCESS_TEXT = "state_memory_process_text";

    private String restoredTargetPackage;
    private String restoredProcessText;

    public void restore(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        try {
            restoredTargetPackage = savedInstanceState.getString(STATE_TARGET_PACKAGE, null);
        } catch (Throwable ignored) {
            restoredTargetPackage = null;
        }
        try {
            restoredProcessText = savedInstanceState.getString(STATE_PROCESS_TEXT, null);
        } catch (Throwable ignored) {
            restoredProcessText = null;
        }
    }

    public void save(Bundle outState, TabMemoryBinding tab) {
        if (outState == null || tab == null) return;
        try {
            if (tab.edtMemoryTargetPkg != null && tab.edtMemoryTargetPkg.getText() != null) {
                outState.putString(STATE_TARGET_PACKAGE, tab.edtMemoryTargetPkg.getText().toString());
            }
        } catch (Throwable ignored) {
        }
        try {
            if (tab.ddMemoryProcess != null && tab.ddMemoryProcess.getText() != null) {
                outState.putString(STATE_PROCESS_TEXT, tab.ddMemoryProcess.getText().toString());
            }
        } catch (Throwable ignored) {
        }
    }

    public String consumeTargetPackage() {
        String value = restoredTargetPackage;
        restoredTargetPackage = null;
        return value;
    }

    public String consumeProcessText() {
        String value = restoredProcessText;
        restoredProcessText = null;
        return value;
    }
}
