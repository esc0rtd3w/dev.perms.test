package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

/**
 * Bridges Activity-provided Shizuku file shell services into FTP client local refreshes.
 *
 * Keeps the FTP client controller focused on action routing while the shell
 * readiness and command-runner adapter details stay in one FTP-client-specific
 * helper.
 */
final class NetworkFtpClientShellBridge {
    private NetworkFtpClientShellBridge() {
    }

    static NetworkFtpClientLocalRefreshActions.ShizukuReadyChecker readyChecker(NetworkActivityDependencies dependencies) {
        return dependencies == null ? null : dependencies::filesCanUseShizuku;
    }

    static NetworkFtpClientLocalRefreshActions.ShizukuCommandRunner localRefreshRunner(NetworkActivityDependencies dependencies) {
        return dependencies == null
                ? null
                : (command, callback) -> dependencies.runFtpClientLocalRefreshCommand(command,
                        callback == null
                                ? null
                                : (exit, out, err) -> callback.onComplete(exit, out, err));
    }
}
