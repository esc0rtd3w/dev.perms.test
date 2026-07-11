package dev.perms.test.network;

import android.text.TextUtils;

/**
 * Activity-side diagnostics controller for the Network tab.
 *
 * Keeps diagnostic button binding and shell-command routing separate from FTP
 * server/client runtime paths while MainActivity keeps shell execution ownership.
 */
public final class NetworkDiagnosticsController extends NetworkControllerBase implements NetworkDiagnosticsActions.ShellCommandRunner {

    public NetworkDiagnosticsController(NetworkActivityDependencies dependencies) {
        super(dependencies);
    }

    public void bind() {
        NetworkDiagnosticsActions.bind(
                getActivity(),
                getNetworkBinding(),
                this);
    }

    @Override
    public void run(String tag, String command) {
        if (dependencies == null) return;
        dependencies.setOutputTag(TextUtils.isEmpty(tag) ? "network" : tag);
        dependencies.runShellCommand(command);
    }

}
