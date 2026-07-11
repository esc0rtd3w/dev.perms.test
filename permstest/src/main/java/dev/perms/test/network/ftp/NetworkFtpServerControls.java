package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.text.TextUtils;
import android.widget.EditText;

import dev.perms.test.databinding.TabNetworkBinding;

/**
 * Wires Network tab FTP server controls while the Activity keeps server/runtime ownership.
 */
public final class NetworkFtpServerControls {
    private NetworkFtpServerControls() {}

    public static void bind(TabNetworkBinding network, Callbacks callbacks) {
        if (network == null || callbacks == null) return;

        State state = callbacks.loadState();
        if (state == null) state = new State(2221, false, false, false, callbacks.defaultRootForMode(false));

        network.chkFtpUseShizuku.setChecked(state.useShizuku);
        network.chkFtpKeepAliveSleep.setChecked(state.keepAliveSleep);
        network.chkFtpBackgroundUse.setChecked(state.backgroundUse);
        network.edtFtpPort.setText(String.valueOf(state.port == 21 ? 2221 : state.port));
        network.edtFtpRoot.setText(NetworkFtpServerPaths.rootForModeSwitch(state.useShizuku, state.root));

        network.chkFtpUseShizuku.setOnCheckedChangeListener((buttonView, checked) -> {
            boolean previousMode = !checked;
            callbacks.saveRootForMode(previousMode,
                    networkText(network.edtFtpRoot, callbacks.defaultRootForMode(previousMode)));
            callbacks.saveUseShizuku(checked);
            String nextRoot = NetworkFtpServerPaths.rootForModeSwitch(checked, callbacks.loadRootForMode(checked));
            callbacks.saveRootForMode(checked, nextRoot);
            network.edtFtpRoot.setText(nextRoot);
            callbacks.updateServerUi();
        });

        network.chkFtpKeepAliveSleep.setOnCheckedChangeListener((buttonView, checked) -> {
            callbacks.saveKeepAliveSleep(checked);
            callbacks.updateServerUi();
        });

        network.chkFtpBackgroundUse.setOnCheckedChangeListener((buttonView, checked) -> {
            callbacks.saveBackgroundUse(checked);
            callbacks.updateServerUi();
        });

        network.btnFtpStart.setOnClickListener(v -> callbacks.startServer());
        network.btnFtpStop.setOnClickListener(v -> callbacks.stopServer());
        network.btnFtpCopyUrl.setOnClickListener(v -> callbacks.copyUrl());
    }

    private static String networkText(EditText editText, String fallback) {
        try {
            String s = editText == null || editText.getText() == null ? "" : editText.getText().toString().trim();
            return TextUtils.isEmpty(s) ? fallback : s;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static final class State {
        public final int port;
        public final boolean useShizuku;
        public final boolean backgroundUse;
        public final boolean keepAliveSleep;
        public final String root;

        public State(int port, boolean useShizuku, boolean backgroundUse, boolean keepAliveSleep, String root) {
            this.port = port;
            this.useShizuku = useShizuku;
            this.backgroundUse = backgroundUse;
            this.keepAliveSleep = keepAliveSleep;
            this.root = TextUtils.isEmpty(root) ? "/" : root;
        }
    }

    public interface Callbacks {
        State loadState();
        String loadRootForMode(boolean useShizuku);
        String defaultRootForMode(boolean useShizuku);
        void saveRootForMode(boolean useShizuku, String root);
        void saveUseShizuku(boolean useShizuku);
        void saveKeepAliveSleep(boolean keepAliveSleep);
        void saveBackgroundUse(boolean backgroundUse);
        void updateServerUi();
        void startServer();
        void stopServer();
        void copyUrl();
    }
}
