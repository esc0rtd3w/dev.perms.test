package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Keeps FTP client local/remote selection state out of the Activity.
 */
public final class NetworkFtpClientSelectionState {
    private FtpClientLocalEntry selectedLocal;
    private PermsTestFtpClient.RemoteEntry selectedRemote;
    private final LinkedHashSet<String> selectedLocalPaths = new LinkedHashSet<>();
    private final LinkedHashSet<String> selectedRemoteNames = new LinkedHashSet<>();

    public void setSelectedLocal(FtpClientLocalEntry entry) {
        selectedLocal = entry;
    }

    public void setSelectedRemote(PermsTestFtpClient.RemoteEntry entry) {
        selectedRemote = entry;
    }

    public boolean localMultiSelectActive() {
        return !selectedLocalPaths.isEmpty();
    }

    public boolean remoteMultiSelectActive() {
        return !selectedRemoteNames.isEmpty();
    }

    public void toggleLocal(FtpClientLocalEntry entry) {
        if (entry == null || TextUtils.isEmpty(entry.path)) return;
        String key = NetworkFtpClientPaths.normalize(entry.path);
        if (selectedLocalPaths.contains(key)) {
            selectedLocalPaths.remove(key);
            if (selectedLocal == entry) selectedLocal = null;
        } else {
            selectedLocalPaths.add(key);
            selectedLocal = entry;
        }
    }

    public void toggleRemote(PermsTestFtpClient.RemoteEntry entry) {
        if (entry == null || TextUtils.isEmpty(entry.name)) return;
        String key = entry.name;
        if (selectedRemoteNames.contains(key)) {
            selectedRemoteNames.remove(key);
            if (selectedRemote == entry) selectedRemote = null;
        } else {
            selectedRemoteNames.add(key);
            selectedRemote = entry;
        }
    }

    public boolean isLocalSelected(FtpClientLocalEntry entry) {
        return entry != null && !TextUtils.isEmpty(entry.path)
                && selectedLocalPaths.contains(NetworkFtpClientPaths.normalize(entry.path));
    }

    public boolean isRemoteSelected(PermsTestFtpClient.RemoteEntry entry) {
        return entry != null && !TextUtils.isEmpty(entry.name)
                && selectedRemoteNames.contains(entry.name);
    }

    public void clearLocalMultiSelection() {
        selectedLocalPaths.clear();
    }

    public void clearRemoteMultiSelection() {
        selectedRemoteNames.clear();
    }

    public ArrayList<FtpClientLocalEntry> selectedLocalEntries(List<FtpClientLocalEntry> entries) {
        ArrayList<FtpClientLocalEntry> out = new ArrayList<>();
        if (!selectedLocalPaths.isEmpty()) {
            if (entries != null) {
                for (FtpClientLocalEntry entry : entries) {
                    if (isLocalSelected(entry)) out.add(entry);
                }
            }
        } else if (selectedLocal != null) {
            out.add(selectedLocal);
        }
        return out;
    }

    public ArrayList<FtpClientLocalEntry> selectedLocalFiles(List<FtpClientLocalEntry> entries) {
        ArrayList<FtpClientLocalEntry> out = new ArrayList<>();
        for (FtpClientLocalEntry entry : selectedLocalEntries(entries)) {
            if (entry != null && entry.file) out.add(entry);
        }
        return out;
    }

    public ArrayList<PermsTestFtpClient.RemoteEntry> selectedRemoteEntries(List<PermsTestFtpClient.RemoteEntry> entries) {
        ArrayList<PermsTestFtpClient.RemoteEntry> out = new ArrayList<>();
        if (!selectedRemoteNames.isEmpty()) {
            if (entries != null) {
                for (PermsTestFtpClient.RemoteEntry entry : entries) {
                    if (isRemoteSelected(entry)) out.add(entry);
                }
            }
        } else if (selectedRemote != null) {
            out.add(selectedRemote);
        }
        return out;
    }

    public ArrayList<PermsTestFtpClient.RemoteEntry> selectedRemoteFiles(List<PermsTestFtpClient.RemoteEntry> entries) {
        ArrayList<PermsTestFtpClient.RemoteEntry> out = new ArrayList<>();
        for (PermsTestFtpClient.RemoteEntry entry : selectedRemoteEntries(entries)) {
            if (entry != null && entry.file) out.add(entry);
        }
        return out;
    }
}
