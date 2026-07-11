package dev.perms.test.network.ssh;

import dev.perms.test.network.NetworkActivityDependencies;
import dev.perms.test.network.NetworkControllerBase;
import dev.perms.test.network.NetworkPreferenceKeys;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import dev.perms.test.databinding.TabNetworkBinding;

/** Activity-side controller for the Network tab SSH Server groupbox. */
public final class NetworkSshServerController extends NetworkControllerBase {
    private static final int DEFAULT_PORT = 2222;
    private static final String DEFAULT_ROOT = "/storage/emulated/0";
    private static final String DEFAULT_USERNAME = "perms";

    public NetworkSshServerController(NetworkActivityDependencies dependencies) {
        super(dependencies);
    }

    public void bind() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        loadState(network);
        bindControls(network);
        updateSshUi();
    }

    public void refreshVisibleState() {
        updateSshUi();
    }

    public void shutdown() {
        // SSH is service-owned. Do not stop it just because the Activity is recreated.
    }

    private void bindControls(TabNetworkBinding network) {
        network.btnSshStart.setOnClickListener(v -> startSshServer());
        network.btnSshStop.setOnClickListener(v -> stopSshServer());
        network.btnSshCopyCommand.setOnClickListener(v -> copySshCommand());
        network.btnSshResetHostKey.setOnClickListener(v -> confirmResetHostKey());
        network.chkSshEnableSftp.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.SSH_ENABLE_SFTP, checked));
        network.chkSshEnableShell.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.SSH_ENABLE_SHELL, checked));
        network.chkSshKeepAliveSleep.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.SSH_KEEP_ALIVE_SLEEP, checked));
        network.chkSshDebug.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.SSH_DEBUG, checked));
    }

    private void loadState(TabNetworkBinding network) {
        SharedPreferences prefs = getPreferences();
        int port = prefs == null ? DEFAULT_PORT : prefs.getInt(NetworkPreferenceKeys.SSH_PORT, DEFAULT_PORT);
        String root = prefs == null ? DEFAULT_ROOT : prefs.getString(NetworkPreferenceKeys.SSH_ROOT, DEFAULT_ROOT);
        String username = prefs == null ? DEFAULT_USERNAME : prefs.getString(NetworkPreferenceKeys.SSH_USERNAME, DEFAULT_USERNAME);
        String password = prefs == null ? "" : prefs.getString(NetworkPreferenceKeys.SSH_PASSWORD, "");
        boolean sftp = prefs == null || prefs.getBoolean(NetworkPreferenceKeys.SSH_ENABLE_SFTP, true);
        boolean shell = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.SSH_ENABLE_SHELL, false);
        boolean keepAlive = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.SSH_KEEP_ALIVE_SLEEP, false);
        boolean debug = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.SSH_DEBUG, false);
        network.edtSshPort.setText(String.valueOf(port));
        network.edtSshRoot.setText(TextUtils.isEmpty(root) ? DEFAULT_ROOT : root);
        network.edtSshUsername.setText(TextUtils.isEmpty(username) ? DEFAULT_USERNAME : username);
        network.edtSshPassword.setText(password == null ? "" : password);
        network.chkSshEnableSftp.setChecked(sftp);
        network.chkSshEnableShell.setChecked(shell);
        network.chkSshKeepAliveSleep.setChecked(keepAlive);
        network.chkSshDebug.setChecked(debug);
    }

    private void startSshServer() {
        TabNetworkBinding network = getNetworkBinding();
        Activity activity = getActivity();
        if (network == null || activity == null) return;
        int port = parsePort(textOf(network.edtSshPort), DEFAULT_PORT);
        String root = textOf(network.edtSshRoot);
        String username = textOf(network.edtSshUsername).trim();
        String password = textOf(network.edtSshPassword);
        boolean sftp = network.chkSshEnableSftp.isChecked();
        boolean shell = network.chkSshEnableShell.isChecked();
        boolean keepAlive = network.chkSshKeepAliveSleep.isChecked();
        boolean debug = network.chkSshDebug.isChecked() || (dependencies != null && dependencies.isDebugOutputEnabled());

        if (port < 1 || port > 65535) {
            Toast.makeText(activity, "Choose a valid SSH port.", Toast.LENGTH_LONG).show();
            return;
        }
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(activity, "SSH username is required.", Toast.LENGTH_LONG).show();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(activity, "SSH password is required.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!sftp && !shell) {
            Toast.makeText(activity, "Enable SFTP or shell access first.", Toast.LENGTH_LONG).show();
            return;
        }
        if (TextUtils.isEmpty(root)) root = DEFAULT_ROOT;

        saveState(port, root, username, password, sftp, shell, keepAlive, network.chkSshDebug.isChecked());
        PermsTestSshService.markStartRequested(port, root, username, sftp, shell, keepAlive);
        Intent intent = new Intent(activity, PermsTestSshService.class)
                .setAction(PermsTestSshService.ACTION_START)
                .putExtra(PermsTestSshService.EXTRA_PORT, port)
                .putExtra(PermsTestSshService.EXTRA_ROOT, root)
                .putExtra(PermsTestSshService.EXTRA_USERNAME, username)
                .putExtra(PermsTestSshService.EXTRA_PASSWORD, password)
                .putExtra(PermsTestSshService.EXTRA_ENABLE_SFTP, sftp)
                .putExtra(PermsTestSshService.EXTRA_ENABLE_SHELL, shell)
                .putExtra(PermsTestSshService.EXTRA_KEEP_ALIVE_SLEEP, keepAlive)
                .putExtra(PermsTestSshService.EXTRA_DEBUG, debug);
        if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(intent); else activity.startService(intent);
        appendOutput("[SSH] Server starting on port " + port + "; user=" + username + "; root=" + root
                + (sftp ? "; sftp=on" : "; sftp=off")
                + (shell ? "; shell=on" : "; shell=off")
                + (keepAlive ? "; sleep-keepalive=on" : "") + "\n");
        Toast.makeText(activity, "Starting SSH server", Toast.LENGTH_SHORT).show();
        updateSshUi();
        postRefreshSoon();
    }

    private void stopSshServer() {
        Activity activity = getActivity();
        if (activity != null) {
            try { activity.startService(new Intent(activity, PermsTestSshService.class).setAction(PermsTestSshService.ACTION_STOP)); } catch (Throwable ignored) {}
            try { activity.stopService(new Intent(activity, PermsTestSshService.class)); } catch (Throwable ignored) {}
        }
        PermsTestSshService.markStopRequested();
        appendOutput("[SSH] Server stopped\n");
        if (activity != null) Toast.makeText(activity, "SSH server stopped", Toast.LENGTH_SHORT).show();
        updateSshUi();
    }

    private void copySshCommand() {
        Activity activity = getActivity();
        if (activity == null) return;
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(
                "PermsTest SSH/SFTP commands",
                buildSshClipboardText()));
        Toast.makeText(activity, "SSH/SFTP commands copied", Toast.LENGTH_SHORT).show();
    }

    private void confirmResetHostKey() {
        Activity activity = getActivity();
        if (activity == null) return;
        PermsTestSshService.Status status = PermsTestSshService.snapshot();
        if (status.running || status.starting) {
            Toast.makeText(activity, "Stop SSH before resetting the host key.", Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Reset SSH Host Key")
                .setMessage("This deletes the generated SSH host key. SSH clients will warn that the host identity changed next time you connect.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> resetHostKey())
                .show();
    }

    private void resetHostKey() {
        Activity activity = getActivity();
        if (activity == null) return;
        File key = PermsTestSshService.hostKeyFile(activity);
        boolean deleted = !key.exists() || key.delete();
        appendOutput("[SSH] Host key " + (deleted ? "reset" : "reset failed") + ": " + key.getAbsolutePath() + "\n");
        Toast.makeText(activity, deleted ? "SSH host key reset" : "SSH host key reset failed", Toast.LENGTH_LONG).show();
        updateSshUi();
    }

    private void updateSshUi() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        PermsTestSshService.Status status = PermsTestSshService.snapshot();
        boolean active = status.running || status.starting;
        network.btnSshStart.setEnabled(!active);
        network.btnSshStop.setEnabled(active);
        network.btnSshCopyCommand.setEnabled(true);
        network.btnSshResetHostKey.setEnabled(!active);
        network.edtSshPort.setEnabled(!active);
        network.edtSshRoot.setEnabled(!active);
        network.edtSshUsername.setEnabled(!active);
        network.edtSshPassword.setEnabled(!active);
        network.chkSshEnableSftp.setEnabled(!active);
        network.chkSshEnableShell.setEnabled(!active);
        network.chkSshKeepAliveSleep.setEnabled(!active);
        network.chkSshDebug.setEnabled(!active);

        if (status.running) {
            network.txtSshServerStatus.setText("Running: " + SshConnectionText.sshCommand(status.username, status.port)
                    + "  •  Root: " + status.root
                    + (status.sftpEnabled ? "  •  SFTP" : "")
                    + (status.shellEnabled ? "  •  shell" : "")
                    + (status.keepAliveSleep ? "  •  sleep keep-alive" : ""));
            network.txtSshServerStatus.setTextColor(Color.rgb(76, 175, 80));
        } else if (status.starting) {
            network.txtSshServerStatus.setText("Starting foreground SSH service...");
            network.txtSshServerStatus.setTextColor(Color.rgb(255, 193, 7));
        } else {
            String keyPath = PermsTestSshService.hostKeyFile(getActivity()).getAbsolutePath();
            network.txtSshServerStatus.setText(TextUtils.isEmpty(status.lastError)
                    ? "SSH server stopped. Host key: " + keyPath
                    : "SSH server stopped. Last error: " + status.lastError);
            network.txtSshServerStatus.setTextColor(TextUtils.isEmpty(status.lastError)
                    ? Color.rgb(189, 189, 189)
                    : Color.rgb(229, 57, 53));
        }
    }

    private String buildSshClipboardText() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) {
            PermsTestSshService.Status status = PermsTestSshService.snapshot();
            return SshConnectionText.clipboardText(status.username, status.port <= 0 ? DEFAULT_PORT : status.port);
        }
        int port = parsePort(textOf(network.edtSshPort), DEFAULT_PORT);
        String username = textOf(network.edtSshUsername).trim();
        if (TextUtils.isEmpty(username)) username = DEFAULT_USERNAME;
        return SshConnectionText.clipboardText(username, port);
    }

    private void postRefreshSoon() {
        if (getMainHandler() == null) return;
        getMainHandler().postDelayed(this::updateSshUi, 600L);
        getMainHandler().postDelayed(this::updateSshUi, 1800L);
    }

    private void saveState(int port,
                           String root,
                           String username,
                           String password,
                           boolean sftp,
                           boolean shell,
                           boolean keepAlive,
                           boolean debug) {
        SharedPreferences prefs = getPreferences();
        if (prefs == null) return;
        prefs.edit()
                .putInt(NetworkPreferenceKeys.SSH_PORT, port)
                .putString(NetworkPreferenceKeys.SSH_ROOT, root == null ? DEFAULT_ROOT : root)
                .putString(NetworkPreferenceKeys.SSH_USERNAME, username == null ? DEFAULT_USERNAME : username)
                .putString(NetworkPreferenceKeys.SSH_PASSWORD, password == null ? "" : password)
                .putBoolean(NetworkPreferenceKeys.SSH_ENABLE_SFTP, sftp)
                .putBoolean(NetworkPreferenceKeys.SSH_ENABLE_SHELL, shell)
                .putBoolean(NetworkPreferenceKeys.SSH_KEEP_ALIVE_SLEEP, keepAlive)
                .putBoolean(NetworkPreferenceKeys.SSH_DEBUG, debug)
                .apply();
    }

    private void saveBoolean(String key, boolean value) {
        SharedPreferences prefs = getPreferences();
        if (prefs != null) prefs.edit().putBoolean(key, value).apply();
    }

    private static int parsePort(String value, int fallback) {
        try { return Integer.parseInt(value == null ? "" : value.trim()); } catch (Throwable ignored) { return fallback; }
    }

    private static String textOf(android.widget.TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString();
    }
}
