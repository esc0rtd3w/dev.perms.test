package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.content.SharedPreferences;

/**
 * SharedPreferences-backed state helpers for Network tab FTP client controls.
 */
final class NetworkFtpClientControlState {
    private NetworkFtpClientControlState() {}

    static NetworkFtpClientControls.State load(SharedPreferences prefs) {
        String defaultLocal = NetworkFtpClientPaths.defaultLocalPath();
        String savedHost = prefs == null ? "" : prefs.getString(NetworkPreferenceKeys.FTP_CLIENT_HOST, "");
        int savedPort = prefs == null ? 21 : prefs.getInt(NetworkPreferenceKeys.FTP_CLIENT_PORT, 21);
        String savedUser = prefs == null ? "anonymous" : prefs.getString(NetworkPreferenceKeys.FTP_CLIENT_USER, "anonymous");
        String savedLocal = prefs == null ? defaultLocal : prefs.getString(NetworkPreferenceKeys.FTP_CLIENT_LOCAL_DIR, defaultLocal);
        boolean savedUseShizuku = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.FTP_CLIENT_USE_SHIZUKU, false);
        String localDir = NetworkFtpClientPaths.ensureLocalDirectory(savedLocal);
        return new NetworkFtpClientControls.State(savedHost, savedPort, savedUser, localDir, savedUseShizuku);
    }

    static void saveLocalDirectory(SharedPreferences prefs, String localDirectory) {
        NetworkFtpClientPaths.saveLocalDirectory(prefs, localDirectory);
    }
}
