package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.File;

import dev.perms.test.databinding.TabNetworkBinding;

/**
 * UI/status helpers for the Network tab FTP server controls.
 */
public final class NetworkFtpServerUi {
    private NetworkFtpServerUi() {}

    public static void updateNetworkAddressStatus(TabNetworkBinding network) {
        if (network == null || network.txtNetworkAddressStatus == null) return;
        NetworkAddressFormatter.Status status = NetworkAddressFormatter.currentStatus();
        network.txtNetworkAddressStatus.setText(status.text);
        network.txtNetworkAddressStatus.setTextColor(status.connected
                ? Color.rgb(76, 175, 80)
                : Color.rgb(229, 57, 53));
    }

    public static void updateServerUi(TabNetworkBinding network,
                                      PermsTestFtpServer ftpServer,
                                      String defaultRootPath) {
        if (network == null) return;
        PermsTestFtpService.Status svc = PermsTestFtpService.snapshot();
        boolean activityRunning = ftpServer != null && ftpServer.isRunning();
        boolean running = activityRunning || svc.running || svc.starting;
        network.btnFtpStart.setEnabled(!running);
        network.btnFtpStop.setEnabled(running);
        network.btnFtpCopyUrl.setEnabled(true);
        network.edtFtpPort.setEnabled(!running);
        network.edtFtpRoot.setEnabled(!running);
        network.chkFtpUseShizuku.setEnabled(!running);
        network.chkFtpKeepAliveSleep.setEnabled(!running);
        network.chkFtpBackgroundUse.setEnabled(!running);

        if (svc.running || svc.starting) {
            NetworkAddressFormatter.Status status = NetworkAddressFormatter.currentStatus();
            String host = TextUtils.isEmpty(status.firstIpv4) ? "127.0.0.1" : status.firstIpv4;
            if (svc.running) {
                network.txtFtpServerStatus.setText("Running in background: ftp://anonymous@" + host + ":" + svc.port
                        + "/  •  Root: " + svc.root
                        + (svc.usingShell ? "  •  Shizuku files" : "")
                        + (svc.keepAliveSleep ? "  •  sleep keep-alive" : ""));
                network.txtFtpServerStatus.setTextColor(Color.rgb(76, 175, 80));
            } else {
                network.txtFtpServerStatus.setText("Starting background FTP service...");
                network.txtFtpServerStatus.setTextColor(Color.rgb(255, 193, 7));
            }
        } else if (activityRunning) {
            NetworkAddressFormatter.Status status = NetworkAddressFormatter.currentStatus();
            String host = TextUtils.isEmpty(status.firstIpv4) ? "127.0.0.1" : status.firstIpv4;
            File root = ftpServer.getRootDir();
            network.txtFtpServerStatus.setText("Running: ftp://anonymous@" + host + ":" + ftpServer.getPort()
                    + "/  •  Root: " + (root == null ? "" : root.getAbsolutePath())
                    + (ftpServer.isUsingShellAccess() ? "  •  Shizuku files" : ""));
            network.txtFtpServerStatus.setTextColor(Color.rgb(76, 175, 80));
        } else {
            String err = !TextUtils.isEmpty(svc.lastError) ? svc.lastError : (ftpServer == null ? "" : ftpServer.getLastError());
            String fallbackRoot = TextUtils.isEmpty(defaultRootPath) ? "/storage/emulated/0" : defaultRootPath;
            String status = TextUtils.isEmpty(err)
                    ? "Stopped. Anonymous access only. Default root is " + fallbackRoot + "."
                    : "Stopped. Last error: " + err;
            network.txtFtpServerStatus.setText(status);
            network.txtFtpServerStatus.setTextColor(TextUtils.isEmpty(err)
                    ? Color.rgb(189, 189, 189)
                    : Color.rgb(229, 57, 53));
        }
    }

    public static void copyUrl(Context context,
                               TabNetworkBinding network,
                               PermsTestFtpServer ftpServer) {
        try {
            if (context == null || network == null) throw new IllegalStateException("Network tab is not ready.");
            PermsTestFtpService.Status svc = PermsTestFtpService.snapshot();
            int port = svc.running || svc.starting
                    ? svc.port
                    : (ftpServer != null && ftpServer.isRunning()
                    ? ftpServer.getPort()
                    : parsePort(text(network.edtFtpPort, "2221")));
            NetworkAddressFormatter.Status status = NetworkAddressFormatter.currentStatus();
            String host = TextUtils.isEmpty(status.firstIpv4) ? "127.0.0.1" : status.firstIpv4;
            String url = "ftp://anonymous@" + host + ":" + port + "/";
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PermsTest FTP URL", url));
            Toast.makeText(context, "FTP URL copied", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
            if (context != null) Toast.makeText(context, "Could not copy FTP URL", Toast.LENGTH_SHORT).show();
        }
    }

    private static int parsePort(String value) {
        int port = Integer.parseInt(value == null ? "" : value.trim());
        if (port < 1 || port > 65535) throw new NumberFormatException("range");
        return port;
    }

    private static String text(android.widget.TextView view, String fallback) {
        if (view == null || view.getText() == null) return fallback;
        String s = view.getText().toString();
        return TextUtils.isEmpty(s) ? fallback : s;
    }
}
