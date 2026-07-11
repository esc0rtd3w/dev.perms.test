package dev.perms.test.network;

import dev.perms.test.network.ftp.*;
import dev.perms.test.network.http.NetworkHttpServerController;
import dev.perms.test.network.ssh.NetworkSshClientController;
import dev.perms.test.network.ssh.NetworkSshServerController;
import dev.perms.test.link.MultiplayerLinkController;

/**
 * Lazy Activity-side controller holder for the Network tab.
 *
 * MainActivity keeps lifecycle and shell execution. This class owns Network
 * controller construction and tab binding while FTP server/client/diagnostics
 * controllers own their separate paths inside the Network package.
 */
public final class NetworkActivityControllers {
    private final NetworkFtpServerController ftpServerController;
    private final NetworkFtpClientController ftpClientController;
    private final NetworkHttpServerController httpServerController;
    private final NetworkSshServerController sshServerController;
    private final NetworkSshClientController sshClientController;
    private final NetworkDiagnosticsController diagnosticsController;
    private final NetworkServiceStatusRefresher serviceStatusRefresher;
    private final MultiplayerLinkController multiplayerLinkController;

    public NetworkActivityControllers(NetworkActivityDependencies dependencies) {
        this.ftpServerController = new NetworkFtpServerController(dependencies);
        this.ftpClientController = new NetworkFtpClientController(dependencies);
        this.httpServerController = new NetworkHttpServerController(dependencies, ftpServerController);
        this.sshServerController = new NetworkSshServerController(dependencies);
        this.sshClientController = new NetworkSshClientController(dependencies);
        this.diagnosticsController = new NetworkDiagnosticsController(dependencies);
        this.serviceStatusRefresher = new NetworkServiceStatusRefresher(
                dependencies == null ? null : dependencies.getMainHandler(),
                this::refreshVisibleState);
        this.multiplayerLinkController = new MultiplayerLinkController(new MultiplayerLinkController.Host() {
            @Override
            public android.app.Activity getActivity() {
                return dependencies == null ? null : dependencies.getActivity();
            }

            @Override
            public dev.perms.test.databinding.ActivityMainBinding getBinding() {
                return dependencies == null ? null : dependencies.getBinding();
            }

            @Override
            public android.content.SharedPreferences getSharedPreferences() {
                return dependencies == null ? null : dependencies.getPreferences();
            }

            @Override
            public void appendOutput(String message) {
                if (dependencies != null) dependencies.appendOutput(message);
            }
        });
    }

    public void registerActivityResults() {
        httpServerController.registerActivityResults();
    }

    public void bind() {
        registerActivityResults();
        ftpServerController.bind();
        ftpClientController.bind();
        httpServerController.bind();
        sshServerController.bind();
        sshClientController.bind();
        multiplayerLinkController.bind();
        refreshVisibleState();
        diagnosticsController.bind();
        serviceStatusRefresher.start();
    }

    public void refreshVisibleState() {
        ftpServerController.refreshVisibleState();
        ftpClientController.refreshVisibleState();
        httpServerController.refreshVisibleState();
        sshServerController.refreshVisibleState();
        sshClientController.refreshVisibleState();
        multiplayerLinkController.refreshVisibleState();
    }

    public void shutdown() {
        ftpServerController.shutdown();
        ftpClientController.shutdown();
        serviceStatusRefresher.stop();
        multiplayerLinkController.stop();
        sshClientController.shutdown();
        sshServerController.shutdown();
        httpServerController.shutdown();
    }
}
