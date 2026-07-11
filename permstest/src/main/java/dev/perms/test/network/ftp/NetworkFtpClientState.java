package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

import dev.perms.test.databinding.TabNetworkBinding;

/**
 * Holds Activity-side FTP client list, selection, and adapter state.
 */
public final class NetworkFtpClientState {
    private String localDirectory;
    private ArrayAdapter<String> localAdapter;
    private ArrayAdapter<String> remoteAdapter;
    private final ArrayList<FtpClientLocalEntry> localEntries = new ArrayList<>();
    private final ArrayList<PermsTestFtpClient.RemoteEntry> remoteEntries = new ArrayList<>();
    private final NetworkFtpClientSelectionState selection = new NetworkFtpClientSelectionState();

    public String localDirectory() {
        return localDirectory;
    }

    public void setLocalDirectory(String localDirectory) {
        this.localDirectory = NetworkFtpClientPaths.normalize(localDirectory);
    }

    public ArrayList<FtpClientLocalEntry> localEntries() {
        return localEntries;
    }

    public ArrayList<PermsTestFtpClient.RemoteEntry> remoteEntries() {
        return remoteEntries;
    }

    public NetworkFtpClientSelectionState selection() {
        return selection;
    }

    public void setAdapters(ArrayAdapter<String> localAdapter, ArrayAdapter<String> remoteAdapter) {
        this.localAdapter = localAdapter;
        this.remoteAdapter = remoteAdapter;
    }

    public void setLocalEntries(TabNetworkBinding network, List<FtpClientLocalEntry> entries) {
        localEntries.clear();
        if (entries != null) localEntries.addAll(entries);
        selection.setSelectedLocal(null);
        clearLocalMultiSelection();
        renderLocalEntries(network);
    }

    public void setRemoteEntries(TabNetworkBinding network,
                                 PermsTestFtpClient client,
                                 List<PermsTestFtpClient.RemoteEntry> entries) {
        remoteEntries.clear();
        if (entries != null) remoteEntries.addAll(entries);
        selection.setSelectedRemote(null);
        clearRemoteMultiSelection();
        renderRemoteEntries(network, client);
    }

    public void clearRemoteList(TabNetworkBinding network) {
        remoteEntries.clear();
        selection.setSelectedRemote(null);
        clearRemoteMultiSelection();
        NetworkFtpClientUi.clearRemoteEntries(network, remoteAdapter);
    }

    public void renderLocalEntries(TabNetworkBinding network) {
        NetworkFtpClientUi.renderLocalEntries(
                network,
                localAdapter,
                localEntries,
                localDirectory,
                this::isLocalSelected);
    }

    public void renderRemoteEntries(TabNetworkBinding network, PermsTestFtpClient client) {
        String path = (client == null || !client.isConnected()) ? "/" : client.currentDirectory();
        NetworkFtpClientUi.renderRemoteEntries(
                network,
                remoteAdapter,
                remoteEntries,
                path,
                this::isRemoteSelected);
    }

    public void updateControls(TabNetworkBinding network,
                               NetworkFtpClientTaskRunner taskRunner,
                               PermsTestFtpClient client) {
        boolean connected = client != null && client.isConnected();
        NetworkFtpClientUi.updateControls(
                network,
                taskRunner != null && taskRunner.isBusy(),
                connected,
                localDirectory);
    }

    public void setStatus(TabNetworkBinding network, String text, boolean error) {
        NetworkFtpClientUi.setStatus(network, text, error);
    }

    public boolean isLocalSelected(FtpClientLocalEntry entry) {
        return selection.isLocalSelected(entry);
    }

    public boolean isRemoteSelected(PermsTestFtpClient.RemoteEntry entry) {
        return selection.isRemoteSelected(entry);
    }

    public void clearLocalMultiSelection() {
        selection.clearLocalMultiSelection();
    }

    public void clearRemoteMultiSelection() {
        selection.clearRemoteMultiSelection();
    }

    public ArrayList<FtpClientLocalEntry> selectedLocalEntries() {
        return selection.selectedLocalEntries(localEntries);
    }

    public ArrayList<FtpClientLocalEntry> selectedLocalFiles() {
        return selection.selectedLocalFiles(localEntries);
    }

    public ArrayList<PermsTestFtpClient.RemoteEntry> selectedRemoteEntries() {
        return selection.selectedRemoteEntries(remoteEntries);
    }

    public ArrayList<PermsTestFtpClient.RemoteEntry> selectedRemoteFiles() {
        return selection.selectedRemoteFiles(remoteEntries);
    }
}
