package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.content.SharedPreferences;

/**
 * SharedPreferences-backed state helpers for Network tab FTP server controls.
 */
final class NetworkFtpServerControlState {
    private NetworkFtpServerControlState() {}

    static NetworkFtpServerControls.State load(SharedPreferences prefs) {
        int savedPort = prefs == null ? 2221 : prefs.getInt(NetworkPreferenceKeys.FTP_PORT, 2221);
        if (savedPort == 21) savedPort = 2221;
        boolean savedUseShizuku = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.FTP_USE_SHIZUKU, false);
        boolean savedBackgroundUse = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.FTP_BACKGROUND_USE, false);
        boolean savedKeepAliveSleep = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.FTP_KEEP_ALIVE_SLEEP, false);
        return new NetworkFtpServerControls.State(
                savedPort,
                savedUseShizuku,
                savedBackgroundUse,
                savedKeepAliveSleep,
                NetworkFtpServerPaths.loadRootForMode(prefs, savedUseShizuku));
    }

    static void saveUseShizuku(SharedPreferences prefs, boolean useShizuku) {
        if (prefs != null) {
            prefs.edit().putBoolean(NetworkPreferenceKeys.FTP_USE_SHIZUKU, useShizuku).apply();
        }
    }

    static void saveKeepAliveSleep(SharedPreferences prefs, boolean keepAliveSleep) {
        if (prefs != null) {
            prefs.edit().putBoolean(NetworkPreferenceKeys.FTP_KEEP_ALIVE_SLEEP, keepAliveSleep).apply();
        }
    }

    static void saveBackgroundUse(SharedPreferences prefs, boolean backgroundUse) {
        if (prefs != null) {
            prefs.edit().putBoolean(NetworkPreferenceKeys.FTP_BACKGROUND_USE, backgroundUse).apply();
        }
    }
}
