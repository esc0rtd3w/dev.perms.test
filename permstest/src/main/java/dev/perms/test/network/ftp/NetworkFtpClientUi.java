package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.graphics.Color;
import android.text.TextUtils;
import android.widget.ArrayAdapter;

import java.util.List;

import dev.perms.test.databinding.TabNetworkBinding;

/**
 * Renders FTP client list/status controls for the Network tab.
 */
public final class NetworkFtpClientUi {
    private NetworkFtpClientUi() {}

    public static void renderLocalEntries(TabNetworkBinding network,
                                          ArrayAdapter<String> adapter,
                                          List<FtpClientLocalEntry> entries,
                                          String localDirectory,
                                          LocalSelectionPredicate selection) {
        if (adapter != null) {
            adapter.clear();
            if (entries != null) {
                for (FtpClientLocalEntry entry : entries) {
                    adapter.add(displayLocalName(entry, selection != null && selection.isSelected(entry)));
                }
            }
            adapter.notifyDataSetChanged();
        }
        if (network != null) {
            network.txtFtpClientLocalPath.setText("Local: " + FtpClientLocalEntry.normalizePath(localDirectory));
            network.listFtpClientLocal.clearChoices();
        }
    }

    public static void renderRemoteEntries(TabNetworkBinding network,
                                           ArrayAdapter<String> adapter,
                                           List<PermsTestFtpClient.RemoteEntry> entries,
                                           String remoteDirectory,
                                           RemoteSelectionPredicate selection) {
        if (adapter != null) {
            adapter.clear();
            if (entries != null) {
                for (PermsTestFtpClient.RemoteEntry entry : entries) {
                    adapter.add(displayRemoteName(entry, selection != null && selection.isSelected(entry)));
                }
            }
            adapter.notifyDataSetChanged();
        }
        if (network != null) {
            network.txtFtpClientRemotePath.setText("Server: " + (TextUtils.isEmpty(remoteDirectory) ? "/" : remoteDirectory));
            network.listFtpClientRemote.clearChoices();
        }
    }

    public static void clearRemoteEntries(TabNetworkBinding network, ArrayAdapter<String> adapter) {
        if (adapter != null) {
            adapter.clear();
            adapter.notifyDataSetChanged();
        }
        if (network != null) {
            network.txtFtpClientRemotePath.setText("Server: /");
            network.listFtpClientRemote.clearChoices();
        }
    }

    public static void updateControls(TabNetworkBinding network,
                                      boolean busy,
                                      boolean connected,
                                      String localDirectory) {
        if (network == null) return;
        network.btnFtpClientConnect.setEnabled(!busy && !connected);
        network.btnFtpClientDisconnect.setEnabled(!busy && connected);
        network.btnFtpClientRefresh.setEnabled(!busy);
        network.btnFtpClientUpload.setEnabled(!busy);
        network.btnFtpClientDownload.setEnabled(!busy);
        network.btnFtpClientDeleteLocal.setEnabled(!busy);
        network.btnFtpClientDeleteRemote.setEnabled(!busy);
        network.btnFtpClientRemoteUp.setEnabled(!busy && connected);
        network.btnFtpClientRemoteHome.setEnabled(!busy && connected);
        network.btnFtpClientRemoteNewFolder.setEnabled(!busy && connected);
        network.btnFtpClientLocalUp.setEnabled(!busy && !"/".equals(FtpClientLocalEntry.normalizePath(localDirectory)));
        network.btnFtpClientLocalHome.setEnabled(!busy);
        network.btnFtpClientLocalNewFolder.setEnabled(!busy);
        network.chkFtpClientUseShizuku.setEnabled(!busy);
        network.edtFtpClientHost.setEnabled(!busy && !connected);
        network.edtFtpClientPort.setEnabled(!busy && !connected);
        network.edtFtpClientUser.setEnabled(!busy && !connected);
        network.edtFtpClientPass.setEnabled(!busy && !connected);
    }

    public static boolean useShizukuLocal(TabNetworkBinding network) {
        return network != null
                && network.chkFtpClientUseShizuku != null
                && network.chkFtpClientUseShizuku.isChecked();
    }

    public static void setStatus(TabNetworkBinding network, String text, boolean error) {
        if (network == null || network.txtFtpClientStatus == null) return;
        network.txtFtpClientStatus.setText(TextUtils.isEmpty(text) ? "FTP client disconnected" : text);
        network.txtFtpClientStatus.setTextColor(error ? Color.rgb(229, 57, 53) : Color.rgb(189, 189, 189));
    }

    private static String displayLocalName(FtpClientLocalEntry entry, boolean selected) {
        if (entry == null) return "";
        String prefix = selected ? "✓ " : "";
        if (entry.directory) return prefix + "[D] " + entry.name;
        if (entry.link) return prefix + "[L] " + entry.name;
        return prefix + "[F] " + entry.name + "  " + PermsTestFtpClient.humanSize(entry.size);
    }

    private static String displayRemoteName(PermsTestFtpClient.RemoteEntry entry, boolean selected) {
        if (entry == null) return "";
        return (selected ? "✓ " : "") + entry.displayName();
    }

    public interface LocalSelectionPredicate {
        boolean isSelected(FtpClientLocalEntry entry);
    }

    public interface RemoteSelectionPredicate {
        boolean isSelected(PermsTestFtpClient.RemoteEntry entry);
    }
}
