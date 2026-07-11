package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

/**
 * Runtime holder for the Network tab FTP client.
 *
 * Keeps the live FTP client instance and task runner together so controller
 * code can focus on routing UI actions without owning runtime lifetime details.
 */
public final class NetworkFtpClientRuntime {
    private final NetworkFtpClientTaskRunner taskRunner;
    private PermsTestFtpClient client;

    public NetworkFtpClientRuntime(NetworkFtpClientTaskRunner taskRunner) {
        this.taskRunner = taskRunner;
    }

    public NetworkFtpClientTaskRunner taskRunner() {
        return taskRunner;
    }

    public PermsTestFtpClient client() {
        return client;
    }

    public PermsTestFtpClient getOrCreateClient() {
        if (client == null) client = new PermsTestFtpClient();
        return client;
    }

    public void ensureClient() {
        getOrCreateClient();
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public void shutdown() {
        try { if (client != null) client.disconnectQuietly(); } catch (Throwable ignored) {}
    }
}
