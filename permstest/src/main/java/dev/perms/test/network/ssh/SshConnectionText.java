package dev.perms.test.network.ssh;

import dev.perms.test.network.NetworkAddressFormatter;

import android.text.TextUtils;

/** Shared SSH/SFTP connection text helpers for the Network tab. */
final class SshConnectionText {
    private SshConnectionText() {
    }

    static String currentLanHost() {
        NetworkAddressFormatter.Status address = NetworkAddressFormatter.currentStatus();
        return TextUtils.isEmpty(address.firstIpv4) ? "127.0.0.1" : address.firstIpv4;
    }

    static String sshCommand(String username, int port) {
        return "ssh " + safeUser(username) + "@" + currentLanHost() + " -p " + safePort(port);
    }

    static String sftpCommand(String username, int port) {
        return "sftp -P " + safePort(port) + " " + safeUser(username) + "@" + currentLanHost();
    }

    static String sftpUri(int port) {
        return "sftp://" + currentLanHost() + ":" + safePort(port);
    }

    static String clipboardText(String username, int port) {
        String user = safeUser(username);
        int safePort = safePort(port);
        String host = currentLanHost();
        return "SSH command:\n"
                + "ssh " + user + "@" + host + " -p " + safePort + "\n\n"
                + "SFTP command:\n"
                + "sftp -P " + safePort + " " + user + "@" + host + "\n\n"
                + "FileZilla:\n"
                + "Protocol: SFTP\n"
                + "Host: sftp://" + host + "\n"
                + "Port: " + safePort + "\n"
                + "Username: " + user;
    }

    private static String safeUser(String username) {
        return TextUtils.isEmpty(username) ? "perms" : username.trim();
    }

    private static int safePort(int port) {
        return port <= 0 ? 2222 : port;
    }
}
