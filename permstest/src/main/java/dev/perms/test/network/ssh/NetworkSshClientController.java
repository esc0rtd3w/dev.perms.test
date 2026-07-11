package dev.perms.test.network.ssh;

import dev.perms.test.network.NetworkActivityDependencies;
import dev.perms.test.network.NetworkControllerBase;
import dev.perms.test.network.NetworkPreferenceKeys;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.TextUtils;
import android.widget.Toast;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.TabNetworkBinding;

/** Activity-side controller for the Network tab SSH Client groupbox. */
public final class NetworkSshClientController extends NetworkControllerBase {
    private static final int DEFAULT_PORT = 22;
    private static final int DEFAULT_SERVER_TEST_PORT = 2222;
    private static final String DEFAULT_SERVER_TEST_USERNAME = "perms";
    private static final int DEFAULT_TIMEOUT_SECONDS = 20;
    private static final String DEFAULT_COMMAND = "id; uname -a";

    private final Object sessionLock = new Object();
    private ClientSession currentSession;
    private SshClient currentClient;
    private volatile boolean busy;
    private String connectedLabel = "";
    private String lastOutput = "Output will appear here.";

    public NetworkSshClientController(NetworkActivityDependencies dependencies) {
        super(dependencies);
    }

    public void bind() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        loadState(network);
        bindControls(network);
        updateUi();
    }

    public void refreshVisibleState() {
        updateUi();
    }

    public void shutdown() {
        disconnectQuietly();
    }

    private void bindControls(TabNetworkBinding network) {
        network.btnSshClientUseServerSettings.setOnClickListener(v -> useServerSettings());
        network.btnSshClientConnect.setOnClickListener(v -> connect());
        network.btnSshClientDisconnect.setOnClickListener(v -> disconnect());
        network.btnSshClientRunCommand.setOnClickListener(v -> runCommand());
        network.btnSshClientCopyOutput.setOnClickListener(v -> copyOutput());
        network.btnSshClientClearOutput.setOnClickListener(v -> clearOutput());
        network.chkSshClientAcceptUnknownHostKey.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.SSH_CLIENT_ACCEPT_UNKNOWN_HOST_KEY, checked));
        network.chkSshClientRememberPassword.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.SSH_CLIENT_REMEMBER_PASSWORD, checked));
        network.chkSshClientVerbose.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.SSH_CLIENT_VERBOSE, checked));
    }

    private void loadState(TabNetworkBinding network) {
        SharedPreferences prefs = getPreferences();
        String host = prefs == null ? "" : prefs.getString(NetworkPreferenceKeys.SSH_CLIENT_HOST, "");
        int port = prefs == null ? DEFAULT_PORT : prefs.getInt(NetworkPreferenceKeys.SSH_CLIENT_PORT, DEFAULT_PORT);
        String username = prefs == null ? "" : prefs.getString(NetworkPreferenceKeys.SSH_CLIENT_USERNAME, "");
        boolean rememberPassword = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.SSH_CLIENT_REMEMBER_PASSWORD, false);
        String password = rememberPassword && prefs != null ? prefs.getString(NetworkPreferenceKeys.SSH_CLIENT_PASSWORD, "") : "";
        String command = prefs == null ? DEFAULT_COMMAND : prefs.getString(NetworkPreferenceKeys.SSH_CLIENT_COMMAND, DEFAULT_COMMAND);
        int timeout = prefs == null ? DEFAULT_TIMEOUT_SECONDS : prefs.getInt(NetworkPreferenceKeys.SSH_CLIENT_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
        boolean acceptUnknown = prefs == null || prefs.getBoolean(NetworkPreferenceKeys.SSH_CLIENT_ACCEPT_UNKNOWN_HOST_KEY, true);
        boolean verbose = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.SSH_CLIENT_VERBOSE, false);

        network.edtSshClientHost.setText(host == null ? "" : host);
        network.edtSshClientPort.setText(String.valueOf(port));
        network.edtSshClientUsername.setText(username == null ? "" : username);
        network.edtSshClientPassword.setText(password == null ? "" : password);
        network.edtSshClientCommand.setText(TextUtils.isEmpty(command) ? DEFAULT_COMMAND : command);
        network.edtSshClientTimeoutSeconds.setText(String.valueOf(timeout));
        network.chkSshClientAcceptUnknownHostKey.setChecked(acceptUnknown);
        network.chkSshClientRememberPassword.setChecked(rememberPassword);
        network.chkSshClientVerbose.setChecked(verbose);
    }

    private void useServerSettings() {
        TabNetworkBinding network = getNetworkBinding();
        Activity activity = getActivity();
        if (network == null || activity == null) return;
        SharedPreferences prefs = getPreferences();
        PermsTestSshService.Status status = PermsTestSshService.snapshot();
        int port = status.port > 0 ? status.port : (prefs == null ? DEFAULT_SERVER_TEST_PORT : prefs.getInt(NetworkPreferenceKeys.SSH_PORT, DEFAULT_SERVER_TEST_PORT));
        String username = !TextUtils.isEmpty(status.username) ? status.username : (prefs == null ? DEFAULT_SERVER_TEST_USERNAME : prefs.getString(NetworkPreferenceKeys.SSH_USERNAME, DEFAULT_SERVER_TEST_USERNAME));
        String password = prefs == null ? "" : prefs.getString(NetworkPreferenceKeys.SSH_PASSWORD, "");
        String host = SshConnectionText.currentLanHost();

        network.edtSshClientHost.setText(host);
        network.edtSshClientPort.setText(String.valueOf(port));
        network.edtSshClientUsername.setText(TextUtils.isEmpty(username) ? DEFAULT_SERVER_TEST_USERNAME : username);
        network.edtSshClientPassword.setText(password == null ? "" : password);
        network.chkSshClientAcceptUnknownHostKey.setChecked(true);
        network.chkSshClientRememberPassword.setChecked(false);
        network.edtSshClientCommand.setText(DEFAULT_COMMAND);
        network.txtSshClientStatus.setText("Filled from local SSH Server settings. Use port " + port + ".");
        network.txtSshClientStatus.setTextColor(Color.rgb(76, 175, 80));
        Toast.makeText(activity, "SSH Client filled from SSH Server settings", Toast.LENGTH_SHORT).show();
    }

    private void connect() {
        TabNetworkBinding network = getNetworkBinding();
        Activity activity = getActivity();
        ExecutorService executor = getIoExecutor();
        if (network == null || activity == null || executor == null) return;
        SshClientOptions options = readOptions(network);
        if (!validateOptions(options, true)) return;
        saveState(options);
        setBusy(true, "Connecting to " + options.host + ":" + options.port + "...");
        executor.execute(() -> {
            try {
                disconnectQuietly();
                ClientSession session = openSession(activity, options);
                synchronized (sessionLock) {
                    currentSession = session;
                    connectedLabel = options.username + "@" + options.host + ":" + options.port;
                }
                postResult("Connected to " + connectedLabel, null, true, options.verbose);
            } catch (Throwable t) {
                disconnectQuietly();
                postResult("SSH connect failed", errorMessage(t), false, options.verbose);
            } finally {
                setBusy(false, null);
            }
        });
    }

    private void disconnect() {
        disconnectQuietly();
        connectedLabel = "";
        appendClientOutput("Disconnected.");
        Toast.makeText(getActivity(), "SSH client disconnected", Toast.LENGTH_SHORT).show();
        updateUi();
    }

    private void runCommand() {
        TabNetworkBinding network = getNetworkBinding();
        Activity activity = getActivity();
        ExecutorService executor = getIoExecutor();
        if (network == null || activity == null || executor == null) return;
        SshClientOptions options = readOptions(network);
        if (!validateOptions(options, true)) return;
        if (TextUtils.isEmpty(options.command)) {
            Toast.makeText(activity, "Command is required.", Toast.LENGTH_LONG).show();
            return;
        }
        saveState(options);
        setBusy(true, "Running SSH command...");
        executor.execute(() -> {
            try {
                ClientSession session;
                synchronized (sessionLock) {
                    session = currentSession;
                }
                if (!isSessionConnected(session)) {
                    session = openSession(activity, options);
                    synchronized (sessionLock) {
                        currentSession = session;
                        connectedLabel = options.username + "@" + options.host + ":" + options.port;
                    }
                }
                CommandResult result = executeCommand(session, options.command, options.timeoutMillis());
                StringBuilder sb = new StringBuilder();
                sb.append("$ ").append(options.command).append('\n');
                if (!TextUtils.isEmpty(result.stdout)) sb.append(result.stdout.trim()).append('\n');
                if (!TextUtils.isEmpty(result.stderr)) sb.append("[stderr]\n").append(result.stderr.trim()).append('\n');
                sb.append("[exit=").append(result.exitStatus == Integer.MIN_VALUE ? "unknown" : result.exitStatus).append("]");
                postResult("SSH command completed", sb.toString(), true, options.verbose);
            } catch (Throwable t) {
                postResult("SSH command failed", errorMessage(t), false, options.verbose);
            } finally {
                setBusy(false, null);
            }
        });
    }

    private ClientSession openSession(Context context, SshClientOptions options) throws Exception {
        SshAndroidRuntimeCompat.prepareClient(context);
        SshClient client = SshClient.setUpDefaultClient();
        if (options.acceptUnknownHostKey) {
            client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        }
        client.start();
        boolean keepClient = false;
        try {
            ConnectFuture connect = client.connect(options.username, options.host, options.port);
            connect.verify(options.timeoutMillis());
            ClientSession session = connect.getSession();
            session.addPasswordIdentity(options.password);
            session.auth().verify(options.timeoutMillis());
            synchronized (sessionLock) {
                currentClient = client;
            }
            keepClient = true;
            return session;
        } finally {
            if (!keepClient) {
                try { client.stop(); } catch (Throwable ignored) {}
                try { client.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private CommandResult executeCommand(ClientSession session, String command, int timeoutMillis) throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ClientChannel channel = null;
        try {
            channel = session.createExecChannel(command);
            channel.setOut(stdout);
            channel.setErr(stderr);
            channel.open().verify(timeoutMillis);
            Set<ClientChannelEvent> events = channel.waitFor(
                    EnumSet.of(ClientChannelEvent.CLOSED, ClientChannelEvent.EOF),
                    timeoutMillis);
            if (!events.contains(ClientChannelEvent.CLOSED)) {
                throw new java.net.SocketTimeoutException("SSH command timed out after " + (timeoutMillis / 1000) + " seconds.");
            }
            Integer status = channel.getExitStatus();
            return new CommandResult(
                    new String(stdout.toByteArray(), StandardCharsets.UTF_8),
                    new String(stderr.toByteArray(), StandardCharsets.UTF_8),
                    status == null ? Integer.MIN_VALUE : status);
        } finally {
            if (channel != null) {
                try { channel.close(false); } catch (Throwable ignored) {}
            }
        }
    }

    private void copyOutput() {
        Activity activity = getActivity();
        if (activity == null) return;
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("SSH client output", lastOutput == null ? "" : lastOutput));
        Toast.makeText(activity, "SSH output copied", Toast.LENGTH_SHORT).show();
    }

    private void clearOutput() {
        lastOutput = "Output will appear here.";
        updateUi();
    }

    private void disconnectQuietly() {
        synchronized (sessionLock) {
            try { if (currentSession != null) currentSession.close(false); } catch (Throwable ignored) {}
            currentSession = null;
            try { if (currentClient != null) currentClient.stop(); } catch (Throwable ignored) {}
            try { if (currentClient != null) currentClient.close(); } catch (Throwable ignored) {}
            currentClient = null;
        }
    }

    private SshClientOptions readOptions(TabNetworkBinding network) {
        int port = parsePort(textOf(network.edtSshClientPort), DEFAULT_PORT);
        int timeout = parsePort(textOf(network.edtSshClientTimeoutSeconds), DEFAULT_TIMEOUT_SECONDS);
        return new SshClientOptions(
                textOf(network.edtSshClientHost).trim(),
                port,
                textOf(network.edtSshClientUsername).trim(),
                textOf(network.edtSshClientPassword),
                textOf(network.edtSshClientCommand),
                timeout,
                network.chkSshClientAcceptUnknownHostKey.isChecked(),
                network.chkSshClientRememberPassword.isChecked(),
                network.chkSshClientVerbose.isChecked());
    }

    private boolean validateOptions(SshClientOptions options, boolean requirePassword) {
        Activity activity = getActivity();
        if (activity == null) return false;
        if (TextUtils.isEmpty(options.host)) {
            Toast.makeText(activity, "SSH host is required.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (options.port < 1 || options.port > 65535) {
            Toast.makeText(activity, "Choose a valid SSH port.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (TextUtils.isEmpty(options.username)) {
            Toast.makeText(activity, "SSH username is required.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (requirePassword && TextUtils.isEmpty(options.password)) {
            Toast.makeText(activity, "SSH password is required.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (options.timeoutSeconds < 3 || options.timeoutSeconds > 300) {
            Toast.makeText(activity, "Timeout must be 3 to 300 seconds.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!options.acceptUnknownHostKey) {
            Toast.makeText(activity, "Enable Accept unknown host key for first-pass SSH client connections.", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void saveState(SshClientOptions options) {
        SharedPreferences prefs = getPreferences();
        if (prefs == null || options == null) return;
        SharedPreferences.Editor editor = prefs.edit()
                .putString(NetworkPreferenceKeys.SSH_CLIENT_HOST, options.host)
                .putInt(NetworkPreferenceKeys.SSH_CLIENT_PORT, options.port)
                .putString(NetworkPreferenceKeys.SSH_CLIENT_USERNAME, options.username)
                .putString(NetworkPreferenceKeys.SSH_CLIENT_COMMAND, options.command)
                .putInt(NetworkPreferenceKeys.SSH_CLIENT_TIMEOUT_SECONDS, options.timeoutSeconds)
                .putBoolean(NetworkPreferenceKeys.SSH_CLIENT_ACCEPT_UNKNOWN_HOST_KEY, options.acceptUnknownHostKey)
                .putBoolean(NetworkPreferenceKeys.SSH_CLIENT_REMEMBER_PASSWORD, options.rememberPassword)
                .putBoolean(NetworkPreferenceKeys.SSH_CLIENT_VERBOSE, options.verbose);
        editor.putString(NetworkPreferenceKeys.SSH_CLIENT_PASSWORD, options.rememberPassword ? options.password : "");
        editor.apply();
    }

    private void saveBoolean(String key, boolean value) {
        SharedPreferences prefs = getPreferences();
        if (prefs != null) prefs.edit().putBoolean(key, value).apply();
    }

    private void setBusy(boolean value, String statusText) {
        busy = value;
        if (!TextUtils.isEmpty(statusText)) postStatus(statusText, Color.rgb(255, 193, 7));
        postUi(this::updateUi);
    }

    private void postResult(String status, String output, boolean success, boolean verbose) {
        postUi(() -> {
            if (!TextUtils.isEmpty(output)) appendClientOutput(output);
            TabNetworkBinding network = getNetworkBinding();
            if (network != null) {
                network.txtSshClientStatus.setText(status);
                network.txtSshClientStatus.setTextColor(success ? Color.rgb(76, 175, 80) : Color.rgb(229, 57, 53));
            }
            if (verbose || !success) appendOutput("[SSH Client] " + status + (TextUtils.isEmpty(output) ? "" : "\n" + output) + "\n");
            Activity activity = getActivity();
            if (activity != null) Toast.makeText(activity, status, Toast.LENGTH_SHORT).show();
            updateUi();
        });
    }

    private void postStatus(String status, int color) {
        postUi(() -> {
            TabNetworkBinding network = getNetworkBinding();
            if (network == null) return;
            network.txtSshClientStatus.setText(status);
            network.txtSshClientStatus.setTextColor(color);
        });
    }

    private void appendClientOutput(String text) {
        if (TextUtils.isEmpty(text)) return;
        if (TextUtils.isEmpty(lastOutput) || "Output will appear here.".equals(lastOutput)) lastOutput = text;
        else lastOutput = lastOutput + "\n\n" + text;
        TabNetworkBinding network = getNetworkBinding();
        if (network != null) network.txtSshClientOutput.setText(lastOutput);
    }

    private void updateUi() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        boolean connected;
        synchronized (sessionLock) {
            connected = isSessionConnected(currentSession);
        }
        network.btnSshClientConnect.setEnabled(!busy && !connected);
        network.btnSshClientDisconnect.setEnabled(!busy && connected);
        network.btnSshClientUseServerSettings.setEnabled(!busy && !connected);
        network.btnSshClientRunCommand.setEnabled(!busy);
        network.btnSshClientCopyOutput.setEnabled(!TextUtils.isEmpty(lastOutput));
        network.btnSshClientClearOutput.setEnabled(!TextUtils.isEmpty(lastOutput));
        network.edtSshClientHost.setEnabled(!busy && !connected);
        network.edtSshClientPort.setEnabled(!busy && !connected);
        network.edtSshClientUsername.setEnabled(!busy && !connected);
        network.edtSshClientPassword.setEnabled(!busy && !connected);
        network.edtSshClientTimeoutSeconds.setEnabled(!busy);
        network.chkSshClientAcceptUnknownHostKey.setEnabled(!busy && !connected);
        network.chkSshClientRememberPassword.setEnabled(!busy);
        network.chkSshClientVerbose.setEnabled(!busy);
        network.edtSshClientCommand.setEnabled(!busy);
        network.txtSshClientOutput.setText(TextUtils.isEmpty(lastOutput) ? "Output will appear here." : lastOutput);
        if (busy) return;
        if (connected) {
            network.txtSshClientStatus.setText("Connected: " + connectedLabel);
            network.txtSshClientStatus.setTextColor(Color.rgb(76, 175, 80));
        } else {
            network.txtSshClientStatus.setText("SSH client disconnected");
            network.txtSshClientStatus.setTextColor(Color.rgb(189, 189, 189));
        }
    }

    private void postUi(Runnable runnable) {
        if (runnable == null) return;
        if (getMainHandler() != null) getMainHandler().post(runnable);
        else runnable.run();
    }

    private static boolean isSessionConnected(ClientSession session) {
        if (session == null) return false;
        try { return session.isOpen() && !session.isClosing() && !session.isClosed(); } catch (Throwable ignored) { return false; }
    }

    private static String errorMessage(Throwable t) {
        if (t == null) return "Unknown error";
        return SshAndroidRuntimeCompat.describeThrowable(t);
    }

    private static int parsePort(String value, int fallback) {
        try { return Integer.parseInt(value == null ? "" : value.trim()); } catch (Throwable ignored) { return fallback; }
    }

    private static String textOf(android.widget.TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString();
    }

    private static final class SshClientOptions {
        final String host;
        final int port;
        final String username;
        final String password;
        final String command;
        final int timeoutSeconds;
        final boolean acceptUnknownHostKey;
        final boolean rememberPassword;
        final boolean verbose;

        SshClientOptions(String host,
                         int port,
                         String username,
                         String password,
                         String command,
                         int timeoutSeconds,
                         boolean acceptUnknownHostKey,
                         boolean rememberPassword,
                         boolean verbose) {
            this.host = host == null ? "" : host;
            this.port = port;
            this.username = username == null ? "" : username;
            this.password = password == null ? "" : password;
            this.command = command == null ? "" : command;
            this.timeoutSeconds = timeoutSeconds;
            this.acceptUnknownHostKey = acceptUnknownHostKey;
            this.rememberPassword = rememberPassword;
            this.verbose = verbose;
        }

        int timeoutMillis() {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(3, timeoutSeconds) * 1000L);
        }
    }

    private static final class CommandResult {
        final String stdout;
        final String stderr;
        final int exitStatus;

        CommandResult(String stdout, String stderr, int exitStatus) {
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.exitStatus = exitStatus;
        }
    }
}
