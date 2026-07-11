package dev.perms.test.memory.panel;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import dev.perms.test.memory.overlay.MemoryOverlayService;
import dev.perms.test.vr.PermsTestVrOverlayCompat;

/**
 * Activity-side opener for Memory tools hosted in normal app panels.
 *
 * The panel path is intentionally separate from raw overlay permission handling so VR users can
 * pin the PermsTest activity window and use memory tools without returning to notifications.
 */
public final class MemoryPanelActions {
    public interface TargetPackageProvider {
        String getTargetPackage();
    }

    public interface SelectedPidProvider {
        String getSelectedPid();
    }

    public interface OutputAppender {
        void append(String text);
    }

    private final Activity activity;
    private final TargetPackageProvider targetPackageProvider;
    private final SelectedPidProvider selectedPidProvider;
    private final OutputAppender outputAppender;

    public MemoryPanelActions(Activity activity,
                              TargetPackageProvider targetPackageProvider,
                              SelectedPidProvider selectedPidProvider,
                              OutputAppender outputAppender) {
        this.activity = activity;
        this.targetPackageProvider = targetPackageProvider;
        this.selectedPidProvider = selectedPidProvider;
        this.outputAppender = outputAppender;
    }

    public void openMainPanel() {
        open(MemoryOverlayService.ACTION_SHOW_OVERLAY, "Memory panel opened", "[Memory] Failed to open panel: ");
    }

    public void openPanelAction(String action, String toastText) {
        open(action,
                TextUtils.isEmpty(toastText) ? "Memory panel opened" : toastText,
                "[Memory] Failed to open panel window: ");
    }

    private void open(String action, String toastText, String failurePrefix) {
        try {
            if (activity == null) return;
            String safeAction = TextUtils.isEmpty(action) ? MemoryOverlayService.ACTION_SHOW_OVERLAY : action;
            Intent i = new Intent(activity, panelActivityForAction(safeAction));
            i.setAction(safeAction);
            String pkg = getTargetPackage();
            String pid = getSelectedPid();
            if (!TextUtils.isEmpty(pkg)) i.putExtra(MemoryOverlayService.EXTRA_TARGET_PACKAGE, pkg);
            if (!TextUtils.isEmpty(pid)) i.putExtra(MemoryOverlayService.EXTRA_TARGET_PID, pid);
            int flags = Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP;
            if (PermsTestVrOverlayCompat.isEnabled(activity)) {
                flags |= Intent.FLAG_ACTIVITY_NEW_TASK;
            }
            i.addFlags(flags);
            activity.startActivity(i);
            if (PermsTestVrOverlayCompat.isEnabled(activity)) {
                appendOutput("[Memory] " + toastText + "\n");
            } else {
                Toast.makeText(activity, toastText, Toast.LENGTH_SHORT).show();
            }
            PermsTestVrOverlayCompat.moveHostTaskBehindMemoryToolIfNeeded(activity);
        } catch (Throwable t) {
            appendOutput((TextUtils.isEmpty(failurePrefix) ? "[Memory] Failed to open panel window: " : failurePrefix) + t + "\n");
        }
    }

    private Class<?> panelActivityForAction(String action) {
        if (MemoryOverlayService.ACTION_SHOW_HEX_OVERLAY.equals(action)) {
            return MemoryHexPanelActivity.class;
        }
        if (MemoryOverlayService.ACTION_SHOW_DISASSEMBLY_OVERLAY.equals(action)) {
            return MemoryDisassemblyPanelActivity.class;
        }
        if (MemoryOverlayService.ACTION_SHOW_SPECIAL_TOOLS_OVERLAY.equals(action)) {
            return MemorySpecialToolsPanelActivity.class;
        }
        return MemoryOverlayPanelActivity.class;
    }

    private String getTargetPackage() {
        try {
            return targetPackageProvider == null ? "" : targetPackageProvider.getTargetPackage();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getSelectedPid() {
        try {
            return selectedPidProvider == null ? null : selectedPidProvider.getSelectedPid();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void appendOutput(String text) {
        if (TextUtils.isEmpty(text)) return;
        try {
            if (outputAppender != null) outputAppender.append(text);
        } catch (Throwable ignored) {
        }
    }
}
