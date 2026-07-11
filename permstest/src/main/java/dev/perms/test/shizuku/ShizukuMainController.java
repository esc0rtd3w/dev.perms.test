package dev.perms.test.shizuku;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;

import dev.perms.test.ExecMode;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.shizuku.internal.InternalShizukuController;
import dev.perms.test.ui.ShizukuDownloadDialog;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

/**
 * Owns the installed/internal Shizuku controls on the Main tab.
 * Runtime behavior intentionally matches the old MainActivity implementation.
 */
public final class ShizukuMainController {
    private static final String PREFS = "perms_test";
    public static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";

    public interface Host {
        ExecMode getExecMode();
        boolean isInternalShizukuEnabled();
        boolean isShizukuSoftDisconnected();
        void setShizukuSoftDisconnected(boolean disconnected);
        boolean isBackendReadyAndGranted();
        void refreshStatus();
        void applyExecModeUi();
        void appendOutput(String text);
        void resetInternalServerStateAndStartDiscovery();
        void requestInternalShizukuStartServer();
    }

    private interface IntSupplier {
        int get();
    }

    private interface StringSupplier {
        String get();
    }

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final SharedPreferences prefs;
    private final ExecutorService io;
    private final int requestCode;
    private final Host host;

    public ShizukuMainController(AppCompatActivity activity,
                                 ActivityMainBinding binding,
                                 SharedPreferences prefs,
                                 ExecutorService io,
                                 int requestCode,
                                 Host host) {
        this.activity = activity;
        this.binding = binding;
        this.prefs = prefs;
        this.io = io;
        this.requestCode = requestCode;
        this.host = host;
    }

    public void bind() {
        try { binding.tabMain.btnRequest.setOnClickListener(v -> requestPermission()); } catch (Throwable ignored) {}
        try { binding.tabMain.btnRunId.setOnClickListener(v -> showServerInfo()); } catch (Throwable ignored) {}
        try { binding.tabMain.btnOpenShizuku.setOnClickListener(v -> openManager()); } catch (Throwable ignored) {}
        try { binding.tabMain.btnStartShizuku.setOnClickListener(v -> startOrToggle()); } catch (Throwable ignored) {}
        try { binding.tabMain.btnDownloadShizuku.setOnClickListener(v -> openDownload()); } catch (Throwable ignored) {}
    }

    public void applyExecModeUi(ExecMode mode, boolean internalEnabled, boolean softDisconnected) {
        try {
            boolean shiz = ExecMode.isShizukuLike(mode);
            boolean internalMode = shiz && internalEnabled;

            int shizVis = shiz ? View.VISIBLE : View.GONE;
            try { binding.tabMain.txtShizukuOptionsHeader.setVisibility(shizVis); } catch (Throwable ignored) {}
            try { binding.tabMain.txtShizukuOptionsHeader.setText(internalMode ? "Shizuku Status" : "Shizuku Options"); } catch (Throwable ignored) {}
            try { binding.tabMain.grpShizukuRow1.setVisibility(shizVis); } catch (Throwable ignored) {}
            try { binding.tabMain.grpShizukuRow2.setVisibility((shiz && !internalMode) ? View.VISIBLE : View.GONE); } catch (Throwable ignored) {}
            applyInternalControls(shiz, internalEnabled);

            try { binding.tabMain.btnRefresh.setEnabled(true); } catch (Throwable ignored) {}
            boolean shizButtons = shiz && !softDisconnected;
            try { binding.tabMain.btnRequest.setVisibility(internalMode ? View.GONE : View.VISIBLE); } catch (Throwable ignored) {}
            try { binding.tabMain.btnRequest.setEnabled(shizButtons && !internalMode); } catch (Throwable ignored) {}
            try { binding.tabMain.btnRunId.setEnabled(shizButtons); } catch (Throwable ignored) {}
            try { binding.tabMain.btnOpenShizuku.setEnabled(shizButtons && !internalMode); } catch (Throwable ignored) {}
            try { binding.tabMain.btnDownloadShizuku.setEnabled(shizButtons && !internalMode); } catch (Throwable ignored) {}

            // Installed Shizuku keeps the app-local soft Connect/Disconnect toggle.
            // Internal Shizuku has explicit PAIR/START/STOP controls, so the installed-manager row is hidden.
            try { binding.tabMain.btnStartShizuku.setEnabled(shiz && !internalMode); } catch (Throwable ignored) {}
            try { binding.tabMain.btnStartShizuku.setText(softDisconnected ? "Connect" : "Disconnect"); } catch (Throwable ignored) {}

            // Shell run is always available; backend readiness is reflected in status/log output.
            try { binding.tabShell.btnRunCmd.setEnabled(true); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }

    public void requestPermission() {
        boolean internalMode = false;
        boolean internalServerRunning = false;
        boolean internalHasEndpoint = false;
        try {
            ExecMode modeNow = host.getExecMode();
            internalMode = modeNow != null && modeNow.isInternalShizuku();
            if (internalMode) {
                SharedPreferences fresh = getInternalStatePrefs();
                internalServerRunning = fresh.getBoolean(InternalShizukuController.PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false);
                internalHasEndpoint = InternalShizukuController.hasEndpoint(fresh);
            }
        } catch (Throwable ignored) {
        }

        boolean binderAlive = isAcceptedBinderAliveForCurrentMode();

        if (internalMode && internalServerRunning) {
            if (binderAlive) {
                host.appendOutput("[internal-shizuku] No separate permission request is needed; PermsTest is the Internal Shizuku manager.\n");
            } else {
                host.appendOutput("[internal-shizuku] Server is marked running, but the binder is not attached yet. Refresh status; if it stays dead, restart the internal server.\n");
            }
            host.refreshStatus();
            return;
        }

        if (internalMode && !internalServerRunning) {
            if (binderAlive) {
                host.appendOutput("[internal-shizuku] Installed Shizuku binder detected, but ignored because Internal Shizuku mode is selected. Start the internal server first or switch Exec Mode to Shizuku.\n");
            } else if (internalHasEndpoint) {
                host.appendOutput("[internal-shizuku] Binder dead; trying Start Server first.\n");
                host.requestInternalShizukuStartServer();
            } else {
                host.appendOutput("[internal-shizuku] No paired endpoint yet. Use PAIR & START (or Open Wireless debugging) first.\n");
            }
            host.refreshStatus();
            return;
        }

        if (!binderAlive) {
            host.refreshStatus();
            return;
        }

        int perm;
        try {
            perm = Shizuku.checkSelfPermission();
        } catch (Throwable t) {
            perm = PackageManager.PERMISSION_DENIED;
        }

        if (perm == PackageManager.PERMISSION_GRANTED) {
            host.refreshStatus();
            return;
        }

        if (Shizuku.shouldShowRequestPermissionRationale()) {
            try {
                binding.tabMain.txtStatus.setText(binding.tabMain.txtStatus.getText()
                        + "\n\nPermission denied with 'Don't ask again'. Open Shizuku → Authorized apps.");
            } catch (Throwable ignored) {
            }
            return;
        }

        Shizuku.requestPermission(requestCode);
    }

    public void showServerInfo() {
        if (!host.isBackendReadyAndGranted()) {
            host.refreshStatus();
            return;
        }

        int uid = safeCallInt(Shizuku::getUid, -1);
        int ver = safeCallInt(Shizuku::getVersion, -1);
        int patch = safeCallInt(Shizuku::getServerPatchVersion, -1);
        String ctx = safeCallString(Shizuku::getSELinuxContext, "?");

        StringBuilder sb = new StringBuilder();
        sb.append("Server info\n");
        sb.append("- UID: ").append(uid).append(" (0=root, 2000=shell)\n");
        sb.append("- Version: ").append(ver).append('\n');
        sb.append("- Patch: ").append(patch).append('\n');
        sb.append("- SELinux context: ").append(ctx).append('\n');
        sb.append("- Pre-v11 server: ").append(Shizuku.isPreV11()).append('\n');

        try { binding.tabMain.txtStatus.setText(sb.toString()); } catch (Throwable ignored) {}
    }

    public void openManager() {
        if (isInternalMode()) {
            host.resetInternalServerStateAndStartDiscovery();
            return;
        }

        try {
            Intent i = activity.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PKG);
            if (i != null) {
                activity.startActivity(i);
                return;
            }
        } catch (Throwable ignored) {
        }
        host.appendOutput("[!] Could not open Shizuku app (" + SHIZUKU_PKG + ").\n");
    }

    public void startOrToggle() {
        if (isInternalMode()) {
            try {
                SharedPreferences fresh = getInternalStatePrefs();
                boolean internalConnected = fresh.getBoolean(InternalShizukuController.PREF_KEY_INTERNAL_SHIZUKU_CONNECTED, false);
                boolean internalServerRunning = fresh.getBoolean(InternalShizukuController.PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false);
                if (!internalConnected) {
                    host.appendOutput("[internal-shizuku] Not connected yet. Use Pair & Start.\n");
                    openManager();
                } else if (internalServerRunning) {
                    host.appendOutput("[internal-shizuku] Server already marked running. Refreshing status.\n");
                    host.refreshStatus();
                } else {
                    host.requestInternalShizukuStartServer();
                }
            } catch (Throwable ignored) {
                host.requestInternalShizukuStartServer();
            }
            return;
        }

        host.setShizukuSoftDisconnected(!host.isShizukuSoftDisconnected());
        host.refreshStatus();
        host.applyExecModeUi();

        if (!host.isShizukuSoftDisconnected() && !isShizukuReady()) {
            openManager();
        }
    }

    public void openDownload() {
        ShizukuDownloadDialog.show(
                activity,
                SHIZUKU_PKG,
                io,
                host::appendOutput,
                task -> activity.runOnUiThread(task));
    }

    public boolean isShizukuReady() {
        try {
            if (!isAcceptedBinderAliveForCurrentMode()) return false;
            if (isInternalMode()) return true;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void applyInternalControls(boolean shiz, boolean internalEnabled) {
        try {
            boolean showInternal = (shiz && internalEnabled);
            boolean internalConnected = false;
            boolean internalServerRunning = false;
            boolean internalHasEndpoint = false;
            boolean binderAlive = false;
            try {
                SharedPreferences fresh = getInternalStatePrefs();
                internalConnected = fresh.getBoolean(InternalShizukuController.PREF_KEY_INTERNAL_SHIZUKU_CONNECTED, false);
                internalServerRunning = fresh.getBoolean(InternalShizukuController.PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false);
                internalHasEndpoint = InternalShizukuController.hasEndpoint(fresh);
            } catch (Throwable ignored) {}
            try { binderAlive = ShizukuProvider.hasEmbeddedBinder(); } catch (Throwable ignored) { binderAlive = false; }
            boolean internalReady = internalServerRunning && binderAlive;

            try {
                if (binding.tabMain.txtInternalShizukuOptionsHeader != null) {
                    binding.tabMain.txtInternalShizukuOptionsHeader.setVisibility(showInternal ? View.VISIBLE : View.GONE);
                }
                if (binding.tabMain.txtInternalShizukuModeHint != null) {
                    binding.tabMain.txtInternalShizukuModeHint.setVisibility(showInternal ? View.VISIBLE : View.GONE);
                }
            } catch (Throwable ignored) {}
            if (binding.tabMain.rowInternalShizukuButtons != null) {
                binding.tabMain.rowInternalShizukuButtons.setVisibility(showInternal ? View.VISIBLE : View.GONE);
            }
            if (binding.tabMain.btnInternalShizukuPairStart != null) {
                binding.tabMain.btnInternalShizukuPairStart.setVisibility(showInternal ? View.VISIBLE : View.GONE);
                binding.tabMain.btnInternalShizukuPairStart.setEnabled(showInternal);
                binding.tabMain.btnInternalShizukuPairStart.setText(internalConnected ? "Pair Again" : "Pair & Start");
            }
            if (binding.tabMain.btnInternalShizukuStartServer != null) {
                binding.tabMain.btnInternalShizukuStartServer.setVisibility(showInternal ? View.VISIBLE : View.GONE);
                binding.tabMain.btnInternalShizukuStartServer.setText((internalServerRunning && !internalReady) ? "Restart Server" : "Start Server");
                binding.tabMain.btnInternalShizukuStartServer.setEnabled(showInternal && !internalReady);
            }
            if (binding.tabMain.btnInternalShizukuStopServer != null) {
                binding.tabMain.btnInternalShizukuStopServer.setVisibility(showInternal ? View.VISIBLE : View.GONE);
                binding.tabMain.btnInternalShizukuStopServer.setEnabled(showInternal && internalServerRunning);
            }
            if (binding.tabMain.rowInternalShizukuWirelessSettings != null) {
                binding.tabMain.rowInternalShizukuWirelessSettings.setVisibility(showInternal ? View.VISIBLE : View.GONE);
            }
            if (binding.tabMain.btnInternalShizukuWirelessSettings != null) {
                binding.tabMain.btnInternalShizukuWirelessSettings.setVisibility(showInternal ? View.VISIBLE : View.GONE);
                binding.tabMain.btnInternalShizukuWirelessSettings.setEnabled(showInternal);
            }
            if (binding.tabMain.txtInternalShizukuPairHint != null) {
                binding.tabMain.txtInternalShizukuPairHint.setVisibility(showInternal ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isAcceptedBinderAliveForCurrentMode() {
        try {
            boolean internal = isInternalMode();
            if (internal) {
                if (ShizukuProvider.hasEmbeddedBinder()) return true;
                try { ShizukuProvider.restoreCachedBinder(activity, true); } catch (Throwable ignored) {}
                return ShizukuProvider.hasEmbeddedBinder();
            }
            if (ShizukuProvider.hasExternalBinder()) return true;
            try { ShizukuProvider.restoreCachedBinder(activity, false); } catch (Throwable ignored) {}
            return ShizukuProvider.hasExternalBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private SharedPreferences getInternalStatePrefs() {
        try {
            return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        } catch (Throwable ignored) {
            return prefs;
        }
    }

    private boolean isInternalMode() {
        try {
            ExecMode mode = host == null ? null : host.getExecMode();
            return mode != null && mode.isInternalShizuku();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int safeCallInt(IntSupplier s, int fallback) {
        try {
            return s.get();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String safeCallString(StringSupplier s, String fallback) {
        try {
            String v = s.get();
            return v == null ? fallback : v;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
