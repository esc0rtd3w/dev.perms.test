package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Activity-side FTP client controller for the Network tab.
 *
 * Keeps FTP client runtime, state, task sequencing, and action ownership out of
 * MainActivity and separated from the FTP server controller path while reusing
 * shared Activity services through NetworkActivityDependencies.
 */
public final class NetworkFtpClientController extends NetworkControllerBase implements NetworkFtpClientControls.Callbacks {
    private final NetworkFtpClientState ftpClientState = new NetworkFtpClientState();
    private final NetworkFtpClientLocalRefreshActions ftpClientLocalRefreshActions = new NetworkFtpClientLocalRefreshActions();
    private final NetworkFtpClientRuntime ftpClientRuntime;
    private volatile int localRefreshGeneration;

    public NetworkFtpClientController(NetworkActivityDependencies dependencies) {
        super(dependencies);
        this.ftpClientRuntime = new NetworkFtpClientRuntime(new NetworkFtpClientTaskRunner(
                getIoExecutor(),
                getMainHandler(),
                () -> {
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "FTP client is busy.", Toast.LENGTH_SHORT).show();
                    }
                },
                this::setClientStatus,
                this::updateClientUi,
                dependencies == null ? null : this::appendOutput));
    }

    public void bind() {
        if (dependencies == null) return;
        NetworkFtpClientControls.bind(getActivity(), getNetworkBinding(), this);
    }

    public void refreshVisibleState() {
        updateClientUi();
    }

    public void shutdown() {
        ftpClientRuntime.shutdown();
    }

    @Override
    public void ensureClient() {
        ftpClientRuntime.ensureClient();
    }

    @Override
    public NetworkFtpClientControls.State loadClientState() {
        return NetworkFtpClientControlState.load(getPreferences());
    }

    @Override
    public void setLocalDirectory(String localDirectory) {
        ftpClientState.setLocalDirectory(localDirectory);
    }

    @Override
    public void setAdapters(ArrayAdapter<String> localAdapter, ArrayAdapter<String> remoteAdapter) {
        ftpClientState.setAdapters(localAdapter, remoteAdapter);
    }

    @Override
    public void onUseShizukuChanged(boolean checked) {
        NetworkFtpClientNavigationActions.onUseShizukuChanged(
                getPreferences(),
                checked,
                ftpClientState.selection(),
                ftpClientState::clearLocalMultiSelection,
                this::refreshLocalList,
                dependencies == null ? null : this::appendOutput);
    }

    @Override
    public void onLocalItemClick(int position) {
        NetworkFtpClientNavigationActions.onLocalItemClick(
                position,
                ftpClientState.localEntries(),
                ftpClientState.selection(),
                ftpClientState::setLocalDirectory,
                ftpClientState::clearLocalMultiSelection,
                this::saveClientLocalDirectory,
                this::refreshLocalList,
                this::refreshLocalAdapterDisplay,
                this::updateClientUi);
    }

    @Override
    public boolean onLocalItemLongClick(int position) {
        return NetworkFtpClientNavigationActions.onLocalItemLongClick(
                position,
                ftpClientState.localEntries(),
                ftpClientState.selection(),
                this::refreshLocalAdapterDisplay,
                this::updateClientUi);
    }

    @Override
    public void onRemoteItemClick(int position) {
        NetworkFtpClientNavigationActions.onRemoteItemClick(
                position,
                ftpClientState.remoteEntries(),
                ftpClientRuntime.client(),
                ftpClientState.selection(),
                this::changeRemoteDirectory,
                ftpClientState::clearRemoteMultiSelection,
                this::refreshRemoteAdapterDisplay,
                this::updateClientUi);
    }

    @Override
    public boolean onRemoteItemLongClick(int position) {
        return NetworkFtpClientNavigationActions.onRemoteItemLongClick(
                position,
                ftpClientState.remoteEntries(),
                ftpClientState.selection(),
                this::refreshRemoteAdapterDisplay,
                this::updateClientUi);
    }

    @Override
    public void connect() {
        if (dependencies == null) return;
        NetworkFtpClientConnectionActions.connectFromUi(
                getActivity(),
                getNetworkBinding(),
                getPreferences(),
                ftpClientRuntime.taskRunner(),
                getMainHandler(),
                ftpClientRuntime::getOrCreateClient,
                this::setRemoteEntries,
                this::setClientStatus,
                this::appendOutput);
    }

    @Override
    public void disconnect() {
        if (dependencies == null) return;
        NetworkFtpClientConnectionActions.disconnect(
                ftpClientRuntime.client(),
                ftpClientRuntime.taskRunner(),
                getMainHandler(),
                this::clearRemoteList,
                this::setClientStatus,
                this::appendOutput);
    }

    @Override
    public void refreshViews() {
        refreshLocalList();
        refreshRemoteList();
    }

    @Override
    public void openLocalParent() {
        NetworkFtpClientNavigationActions.openLocalParent(
                ftpClientState.localDirectory(),
                ftpClientState::setLocalDirectory,
                ftpClientState.selection(),
                ftpClientState::clearLocalMultiSelection,
                this::saveClientLocalDirectory,
                this::refreshLocalList);
    }

    @Override
    public void openLocalHome() {
        NetworkFtpClientNavigationActions.openLocalHome(
                ftpClientState::setLocalDirectory,
                ftpClientState.selection(),
                ftpClientState::clearLocalMultiSelection,
                this::saveClientLocalDirectory,
                this::refreshLocalList);
    }

    @Override
    public void openRemoteParent() {
        NetworkFtpClientRemoteActions.changeToParent(
                ftpClientRuntime.client(),
                ftpClientRuntime.taskRunner(),
                getMainHandler(),
                this::setRemoteEntries);
    }

    @Override
    public void openRemoteHome() {
        changeRemoteDirectory("/");
    }

    @Override
    public void uploadSelected() {
        if (dependencies == null) return;
        List<FtpClientLocalEntry> locals = ftpClientState.selectedLocalFiles();
        if (locals.isEmpty()) {
            showToast("Select one or more local files first.");
            return;
        }
        if (!ftpClientRuntime.isConnected()) {
            showToast("Connect FTP client first.");
            return;
        }
        NetworkFtpClientTransferActions.uploadSelected(
                ftpClientRuntime.client(),
                ftpClientRuntime.taskRunner(),
                getMainHandler(),
                locals,
                NetworkFtpClientUi.useShizukuLocal(getNetworkBinding()),
                dependencies::filesCanUseShizuku,
                ftpClientState::clearLocalMultiSelection,
                () -> ftpClientState.renderLocalEntries(getNetworkBinding()),
                this::setRemoteEntries,
                this::setClientStatus,
                this::appendOutput);
    }

    @Override
    public void downloadSelected() {
        if (dependencies == null) return;
        List<PermsTestFtpClient.RemoteEntry> remotes = ftpClientState.selectedRemoteFiles();
        if (remotes.isEmpty()) {
            showToast("Select one or more remote files first.");
            return;
        }
        if (!ftpClientRuntime.isConnected()) {
            showToast("Connect FTP client first.");
            return;
        }
        NetworkFtpClientTransferActions.downloadSelected(
                ftpClientRuntime.client(),
                ftpClientRuntime.taskRunner(),
                getMainHandler(),
                remotes,
                NetworkFtpClientPaths.normalize(ftpClientState.localDirectory()),
                NetworkFtpClientUi.useShizukuLocal(getNetworkBinding()),
                dependencies::filesCanUseShizuku,
                ftpClientState::clearRemoteMultiSelection,
                () -> ftpClientState.renderRemoteEntries(getNetworkBinding(), ftpClientRuntime.client()),
                this::refreshLocalList,
                this::setClientStatus,
                this::appendOutput);
    }

    @Override
    public void deleteLocalSelected() {
        if (dependencies == null) return;
        NetworkFtpClientDeleteActions.confirmDeleteLocal(
                getActivity(),
                ftpClientState.selectedLocalEntries(),
                NetworkFtpClientUi.useShizukuLocal(getNetworkBinding()),
                ftpClientRuntime.taskRunner(),
                getMainHandler(),
                (entry, useShizuku) -> NetworkFtpClientLocalFileOps.deleteEntry(entry, useShizuku, dependencies::filesCanUseShizuku),
                () -> {
                    ftpClientState.selection().setSelectedLocal(null);
                    ftpClientState.clearLocalMultiSelection();
                },
                this::refreshLocalList,
                this::setClientStatus);
    }

    @Override
    public void deleteRemoteSelected() {
        if (dependencies == null) return;
        NetworkFtpClientDeleteActions.confirmDeleteRemote(
                getActivity(),
                ftpClientRuntime.client(),
                ftpClientState.selectedRemoteEntries(),
                ftpClientRuntime.taskRunner(),
                getMainHandler(),
                () -> {
                    ftpClientState.selection().setSelectedRemote(null);
                    ftpClientState.clearRemoteMultiSelection();
                },
                this::setRemoteEntries,
                this::setClientStatus);
    }

    @Override
    public void createLocalFolder() {
        showClientNewFolderDialog(false);
    }

    @Override
    public void createRemoteFolder() {
        showClientNewFolderDialog(true);
    }

    @Override
    public void refreshLocalList() {
        final int generation = ++localRefreshGeneration;
        final String directory = ftpClientState.localDirectory();
        final boolean useShizuku = NetworkFtpClientUi.useShizukuLocal(getNetworkBinding());
        setClientStatus("Loading local files...", false);

        Runnable work = () -> {
            try {
                String normalized = ftpClientLocalRefreshActions.refresh(
                        directory,
                        useShizuku,
                        NetworkFtpClientShellBridge.readyChecker(dependencies),
                        NetworkFtpClientShellBridge.localRefreshRunner(dependencies),
                        (resolvedDirectory, entries) -> postToMain(() -> {
                            if (generation != localRefreshGeneration) return;
                            ftpClientState.setLocalDirectory(resolvedDirectory);
                            setLocalEntries(entries);
                        }),
                        (text, error) -> postToMain(() -> {
                            if (generation != localRefreshGeneration) return;
                            setClientStatus(text, error);
                        }));
                postToMain(() -> {
                    if (generation != localRefreshGeneration) return;
                    ftpClientState.setLocalDirectory(normalized);
                    updateClientUi();
                });
            } catch (Throwable e) {
                postToMain(() -> {
                    if (generation != localRefreshGeneration) return;
                    setClientStatus("Local list failed: " + e.getMessage(), true);
                });
            }
        };

        ExecutorService executor = getIoExecutor();
        if (executor != null) {
            executor.execute(work);
        } else {
            work.run();
        }
    }

    @Override
    public void clearRemoteList() {
        ftpClientState.clearRemoteList(getNetworkBinding());
    }

    @Override
    public void updateClientUi() {
        try {
            ftpClientState.updateControls(getNetworkBinding(), ftpClientRuntime.taskRunner(), ftpClientRuntime.client());
        } catch (Throwable ignored) {
        }
    }

    private void showToast(String text) {
        if (getActivity() != null) Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();
    }

    private void changeRemoteDirectory(String path) {
        NetworkFtpClientRemoteActions.changeDirectory(
                ftpClientRuntime.client(),
                ftpClientRuntime.taskRunner(),
                getMainHandler(),
                path,
                this::setRemoteEntries);
    }

    private void refreshRemoteList() {
        NetworkFtpClientRemoteActions.refreshList(
                ftpClientRuntime.client(),
                ftpClientRuntime.taskRunner(),
                getMainHandler(),
                this::clearRemoteList,
                this::updateClientUi,
                this::setRemoteEntries);
    }

    private void showClientNewFolderDialog(boolean remote) {
        if (dependencies == null) return;
        NetworkFtpClientFolderActions.showNewFolderDialog(
                getActivity(),
                remote,
                ftpClientRuntime.client(),
                ftpClientRuntime.taskRunner(),
                getMainHandler(),
                ftpClientState::localDirectory,
                () -> NetworkFtpClientUi.useShizukuLocal(getNetworkBinding()),
                dependencies::filesCanUseShizuku,
                this::refreshLocalList,
                this::setRemoteEntries,
                this::setClientStatus);
    }


    private void postToMain(Runnable runnable) {
        if (runnable == null) return;
        android.os.Handler handler = getMainHandler();
        if (handler != null) {
            handler.post(runnable);
        } else {
            runnable.run();
        }
    }

    private void setLocalEntries(List<FtpClientLocalEntry> entries) {
        ftpClientState.setLocalEntries(getNetworkBinding(), entries);
        updateClientUi();
    }

    private void setRemoteEntries(java.util.List<PermsTestFtpClient.RemoteEntry> entries) {
        ftpClientState.setRemoteEntries(getNetworkBinding(), ftpClientRuntime.client(), entries);
        updateClientUi();
    }

    private void setClientStatus(String text, boolean error) {
        try {
            ftpClientState.setStatus(getNetworkBinding(), text, error);
        } catch (Throwable ignored) {
        }
    }

    private void refreshLocalAdapterDisplay() {
        ftpClientState.renderLocalEntries(getNetworkBinding());
    }

    private void refreshRemoteAdapterDisplay() {
        ftpClientState.renderRemoteEntries(getNetworkBinding(), ftpClientRuntime.client());
    }

    private void saveClientLocalDirectory() {
        NetworkFtpClientControlState.saveLocalDirectory(getPreferences(), ftpClientState.localDirectory());
    }

}
