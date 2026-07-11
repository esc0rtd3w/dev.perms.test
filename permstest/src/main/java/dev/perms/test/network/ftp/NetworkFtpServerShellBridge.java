package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import java.io.IOException;

/**
 * Bridges Activity-provided Shizuku shell services into the FTP server runtime.
 *
 * Keeps NetworkActivityDependencies generic and prevents the FTP server
 * controller from owning shell-result conversion details.
 */
final class NetworkFtpServerShellBridge {
    private static final String SHIZUKU_NOT_READY = "Shizuku not ready or permission not granted.";

    private NetworkFtpServerShellBridge() {
    }

    static NetworkFtpServerRuntimeActions.ShellCaptureRunner captureRunner(NetworkActivityDependencies dependencies) {
        return command -> {
            NetworkActivityDependencies.ShellResult result = dependencies == null
                    ? null
                    : dependencies.runFtpServerShellCapture(command);
            if (result == null) {
                return new NetworkFtpServerRuntimeActions.ShellResult(1, "", SHIZUKU_NOT_READY);
            }
            return new NetworkFtpServerRuntimeActions.ShellResult(result.exitCode, result.stdout, result.stderr);
        };
    }

    static NetworkFtpServerRuntimeActions.ShizukuProcessStarter processStarter(NetworkActivityDependencies dependencies) {
        return command -> {
            if (dependencies == null) throw new IOException(SHIZUKU_NOT_READY);
            return dependencies.startFtpServerProcess(command);
        };
    }
}
