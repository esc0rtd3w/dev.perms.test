package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

/**
 * Activity-side FTP server controller for the Network tab.
 *
 * Keeps FTP server runtime and control-state ownership out of MainActivity and
 * away from the FTP client controller path while preserving shared Activity
 * services through NetworkActivityDependencies. Runtime Shizuku shell bridging
 * is delegated to the FTP server shell bridge.
 */
public final class NetworkFtpServerController extends NetworkControllerBase implements NetworkFtpServerControls.Callbacks {
    private final NetworkFtpServerRuntime ftpServerRuntime = new NetworkFtpServerRuntime();

    public NetworkFtpServerController(NetworkActivityDependencies dependencies) {
        super(dependencies);
    }

    public void bind() {
        ftpServerRuntime.ensureServer();
        NetworkFtpServerControls.bind(getNetworkBinding(), this);
    }

    public void refreshVisibleState() {
        updateNetworkAddressStatus();
        updateFtpServerUi();
    }

    public void shutdown() {
        ftpServerRuntime.shutdown();
    }

    public boolean isServerRunning() {
        try {
            PermsTestFtpService.Status svc = PermsTestFtpService.snapshot();
            return svc.running || svc.starting || (ftpServerRuntime.server() != null && ftpServerRuntime.server().isRunning());
        } catch (Throwable ignored) {
            return ftpServerRuntime.server() != null && ftpServerRuntime.server().isRunning();
        }
    }

    private void updateNetworkAddressStatus() {
        NetworkFtpServerUi.updateNetworkAddressStatus(getNetworkBinding());
    }

    private void updateFtpServerUi() {
        NetworkFtpServerUi.updateServerUi(getNetworkBinding(), ftpServerRuntime.server(), NetworkFtpServerPaths.defaultRootPath());
    }

    @Override
    public NetworkFtpServerControls.State loadState() {
        return NetworkFtpServerControlState.load(getPreferences());
    }

    @Override
    public String loadRootForMode(boolean useShizuku) {
        return NetworkFtpServerPaths.loadRootForMode(getPreferences(), useShizuku);
    }

    @Override
    public String defaultRootForMode(boolean useShizuku) {
        return NetworkFtpServerPaths.defaultRootForMode(useShizuku);
    }

    @Override
    public void saveRootForMode(boolean useShizuku, String root) {
        NetworkFtpServerPaths.saveRootForMode(getPreferences(), useShizuku, root);
    }

    @Override
    public void saveUseShizuku(boolean useShizuku) {
        NetworkFtpServerControlState.saveUseShizuku(getPreferences(), useShizuku);
    }

    @Override
    public void saveKeepAliveSleep(boolean keepAliveSleep) {
        NetworkFtpServerControlState.saveKeepAliveSleep(getPreferences(), keepAliveSleep);
    }

    @Override
    public void saveBackgroundUse(boolean backgroundUse) {
        NetworkFtpServerControlState.saveBackgroundUse(getPreferences(), backgroundUse);
    }

    @Override
    public void updateServerUi() {
        updateFtpServerUi();
    }

    @Override
    public void startServer() {
        if (dependencies == null) return;
        ftpServerRuntime.start(
                getActivity(),
                getNetworkBinding(),
                getPreferences(),
                NetworkFtpServerShellBridge.captureRunner(dependencies),
                NetworkFtpServerShellBridge.processStarter(dependencies),
                () -> dependencies.isDebugOutputEnabled(),
                this::appendOutput,
                this::updateFtpServerUi,
                getMainHandler());
    }

    @Override
    public void stopServer() {
        if (dependencies == null) return;
        ftpServerRuntime.stop(
                getActivity(),
                this::updateFtpServerUi,
                getMainHandler());
    }

    @Override
    public void copyUrl() {
        if (dependencies == null) return;
        NetworkFtpServerUi.copyUrl(getActivity(), getNetworkBinding(), ftpServerRuntime.server());
    }

}
