package dev.perms.test.shizuku;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import dev.perms.test.ExecMode;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ladb.LadbController;
import dev.perms.test.shell.ShellBinaryController;
import dev.perms.test.shizuku.internal.InternalShizukuController;
import dev.perms.test.ui.StatusChipUi;

import rikka.shizuku.Shizuku;

/**
 * Owns the visible backend status refresh path for MainActivity.
 *
 * <p>The app still keeps backend execution state in MainActivity for now because many
 * older controllers read it directly, but status text/chips and backend-gated quick
 * controls are kept here so MainActivity is not responsible for rendering every mode.</p>
 */
public final class BackendStatusController {
    public interface Host {
        SharedPreferences prefs();
        ExecMode getExecMode();
        void setExecMode(ExecMode mode);
        boolean isShizukuSoftDisconnected();
        void setInternalShizukuEnabled(boolean enabled);
        LadbController getLadbController();
        boolean hasAcceptedShizukuBinder(boolean internalMode);
        SharedPreferences getInternalShizukuPrefs();
        void maybeAutoInstallPendingFile(boolean binderAlive, boolean granted);
        void applyExecModeUi();
        void refreshBinaryAvailabilityUi();
        ShellBinaryController getShellBinaryController();
    }

    private final Activity activity;
    private final ActivityMainBinding binding;
    private final Host host;
    public BackendStatusController(Activity activity, ActivityMainBinding binding, Host host) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
    }

    public void refreshStatus() {
        host.refreshBinaryAvailabilityUi();

        SharedPreferences prefs = host.prefs();
        ExecMode mode;
        try {
            mode = prefs == null ? ExecMode.SHIZUKU : ExecMode.get(prefs);
        } catch (Throwable ignored) {
            mode = ExecMode.SHIZUKU;
        }
        host.setExecMode(mode);

        boolean internalMode = false;
        try {
            internalMode = mode.isInternalShizuku();
        } catch (Throwable ignored) {
        }
        host.setInternalShizukuEnabled(internalMode);

        if (mode == ExecMode.LADB) {
            refreshLadbStatus(prefs);
            return;
        }

        if (mode == ExecMode.SYSTEM) {
            refreshSystemStatus();
            return;
        }

        refreshShizukuStatus(internalMode);
    }

    private void refreshLadbStatus(SharedPreferences prefs) {
        boolean ladbConnected = host.getLadbController().isConnected();
        StatusChipUi.updateLadbStatusChip(activity, binding, true, ladbConnected);
        StatusChipUi.updateShizukuStatusChip(activity, binding, false, false);

        StringBuilder sb = new StringBuilder();
        sb.append("Mode: LADB\n");
        sb.append("- Host: ").append(LadbController.DEFAULT_HOST).append("\n");
        int port = prefs == null
                ? ExecMode.LADB_DEFAULT_CONNECT_PORT
                : prefs.getInt(ExecMode.PREF_KEY_LADB_CONNECT_PORT, ExecMode.LADB_DEFAULT_CONNECT_PORT);
        sb.append("- Port: ").append(port).append("\n");
        sb.append("- Connected: ").append(ladbConnected).append("\n");
        binding.tabMain.txtStatus.setText(sb.toString());
        host.applyExecModeUi();
    }

    private void refreshSystemStatus() {
        StatusChipUi.updateLadbStatusChip(activity, binding, false, false);
        StatusChipUi.updateShizukuStatusChip(activity, binding, false, false);

        StringBuilder sb = new StringBuilder();
        sb.append("Mode: System\n");
        sb.append("- Running commands as app UID (limited)\n");
        binding.tabMain.txtStatus.setText(sb.toString());
        host.applyExecModeUi();
    }

    private void refreshShizukuStatus(boolean internalMode) {
        try {
            Shizuku.pingBinder();
        } catch (Throwable ignored) {
        }

        boolean binderAlive = host.hasAcceptedShizukuBinder(internalMode);
        boolean softDisconnected = host.isShizukuSoftDisconnected();

        int perm = PackageManager.PERMISSION_DENIED;
        if (binderAlive) {
            try {
                perm = Shizuku.checkSelfPermission();
            } catch (Throwable ignored) {
                perm = PackageManager.PERMISSION_DENIED;
            }
        }

        boolean granted = perm == PackageManager.PERMISSION_GRANTED;
        if (internalMode && binderAlive) {
            // Internal Shizuku is app-owned, so no separate installed-manager permission prompt exists.
            granted = true;
        }

        StatusChipUi.updateShizukuStatusChip(activity, binding,
                !softDisconnected && binderAlive,
                !softDisconnected && granted);
        StatusChipUi.updateLadbStatusChip(activity, binding, false, false);
        host.maybeAutoInstallPendingFile(!softDisconnected && binderAlive, !softDisconnected && granted);

        StringBuilder sb = new StringBuilder();
        sb.append("Mode: Shizuku\n");
        sb.append("- Backend: ").append(internalMode ? "Internal (embedded)" : "Installed (app)").append('\n');
        sb.append("- Soft disconnect: ").append(softDisconnected).append('\n');

        if (internalMode) {
            appendInternalState(sb);
        }

        sb.append('\n');
        sb.append("Binder: ").append(binderAlive ? "alive" : "dead").append('\n');
        if (internalMode) {
            sb.append("Permission: ").append(granted ? "SELF-AUTHORIZED" : "WAITING FOR INTERNAL BINDER").append('\n');
            sb.append("Tip: Pair & Start Internal Shizuku once. No separate Request Permission prompt is needed.").append('\n');
        } else {
            sb.append("Permission: ").append(granted ? "GRANTED" : "DENIED").append('\n');
            sb.append("Tip: Start Shizuku/Sui first, then authorize this app when prompted.").append('\n');
        }

        binding.tabMain.txtStatus.setText(sb.toString());
        boolean enabled = !softDisconnected && binderAlive && granted;

        // Ensure quick-command groups follow backend readiness. Individual scanned binaries
        // are still gated separately by ShellBinaryController.
        setEnabledRecursive(binding.tabShell.shellQuickGroups, enabled);
        binding.tabMain.btnRunId.setEnabled(enabled);
        binding.tabShell.btnRunCmd.setEnabled(enabled);
        binding.tabShell.btnCopy.setEnabled(true);
        host.getShellBinaryController().applyEnabled(binding, enabled);

        StatusChipUi.equalizeStatusChips(binding);
    }

    private void appendInternalState(StringBuilder sb) {
        SharedPreferences internalPrefs = host.getInternalShizukuPrefs();
        boolean internalConnected = internalPrefs.getBoolean(InternalShizukuController.PREF_KEY_INTERNAL_SHIZUKU_CONNECTED, false);
        boolean internalRunning = internalPrefs.getBoolean(InternalShizukuController.PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false);
        int internalPid = internalPrefs.getInt("internal_shizuku_server_pid", 0);
        String hostName = internalPrefs.getString("internal_shizuku_host", "");
        int pairPort = internalPrefs.getInt("internal_shizuku_pair_port", 0);
        int connectPort = internalPrefs.getInt("internal_shizuku_connect_port", 0);
        int connectPortUsed = internalPrefs.getInt("internal_shizuku_connect_port_used", 0);

        sb.append("- Internal state: connected=").append(internalConnected)
                .append(", server_running=").append(internalRunning).append('\n');
        if (internalPid > 0) sb.append("- Internal pid: ").append(internalPid).append('\n');
        if (!TextUtils.isEmpty(hostName)) sb.append("- Internal host: ").append(hostName).append('\n');
        if (pairPort > 0) sb.append("- Pair port: ").append(pairPort).append('\n');
        if (connectPort > 0) sb.append("- Connect port: ").append(connectPort).append('\n');
        if (connectPortUsed > 0) sb.append("- Connect port (used): ").append(connectPortUsed).append('\n');

        sb.append("Config: manager=").append(activity.getPackageName()).append('\n');
        sb.append("Config: permission=").append(activity.getPackageName()).append(".permission.SHIZUKU_API_V23").append('\n');
    }

    private static void setEnabledRecursive(View view, boolean enabled) {
        if (view == null) return;
        try {
            view.setEnabled(enabled);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    setEnabledRecursive(group.getChildAt(i), enabled);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
