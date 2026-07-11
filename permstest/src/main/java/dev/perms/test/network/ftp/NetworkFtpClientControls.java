package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.app.Activity;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

import dev.perms.test.R;
import dev.perms.test.databinding.TabNetworkBinding;

/**
 * Wires Network tab FTP client controls while NetworkFtpClientController owns the FTP client runtime holder.
 */
public final class NetworkFtpClientControls {
    private NetworkFtpClientControls() {}

    public static void bind(Activity activity, TabNetworkBinding network, Callbacks callbacks) {
        if (activity == null || network == null || callbacks == null) return;

        callbacks.ensureClient();
        State state = callbacks.loadClientState();
        if (state == null) state = new State("", 21, "anonymous", "/storage/emulated/0/dev.perms.test", false);

        if (!TextUtils.isEmpty(state.host)) network.edtFtpClientHost.setText(state.host);
        network.edtFtpClientPort.setText(String.valueOf(state.port));
        network.edtFtpClientUser.setText(TextUtils.isEmpty(state.user) ? "anonymous" : state.user);
        network.chkFtpClientUseShizuku.setChecked(state.useShizuku);
        callbacks.setLocalDirectory(state.localDirectory);

        ArrayAdapter<String> localAdapter = new ArrayAdapter<>(activity, R.layout.dropdown_item, new ArrayList<String>());
        ArrayAdapter<String> remoteAdapter = new ArrayAdapter<>(activity, R.layout.dropdown_item, new ArrayList<String>());
        network.listFtpClientLocal.setAdapter(localAdapter);
        network.listFtpClientRemote.setAdapter(remoteAdapter);
        network.listFtpClientLocal.setFastScrollEnabled(true);
        network.listFtpClientRemote.setFastScrollEnabled(true);
        allowNestedListScroll(network.listFtpClientLocal);
        allowNestedListScroll(network.listFtpClientRemote);
        callbacks.setAdapters(localAdapter, remoteAdapter);

        network.chkFtpClientUseShizuku.setOnCheckedChangeListener((buttonView, checked) -> callbacks.onUseShizukuChanged(checked));
        network.listFtpClientLocal.setOnItemClickListener((parent, view, position, id) -> callbacks.onLocalItemClick(position));
        network.listFtpClientLocal.setOnItemLongClickListener((parent, view, position, id) -> callbacks.onLocalItemLongClick(position));
        network.listFtpClientRemote.setOnItemClickListener((parent, view, position, id) -> callbacks.onRemoteItemClick(position));
        network.listFtpClientRemote.setOnItemLongClickListener((parent, view, position, id) -> callbacks.onRemoteItemLongClick(position));

        network.btnFtpClientConnect.setOnClickListener(v -> callbacks.connect());
        network.btnFtpClientDisconnect.setOnClickListener(v -> callbacks.disconnect());
        network.btnFtpClientRefresh.setOnClickListener(v -> callbacks.refreshViews());
        network.btnFtpClientLocalUp.setOnClickListener(v -> callbacks.openLocalParent());
        network.btnFtpClientLocalHome.setOnClickListener(v -> callbacks.openLocalHome());
        network.btnFtpClientRemoteUp.setOnClickListener(v -> callbacks.openRemoteParent());
        network.btnFtpClientRemoteHome.setOnClickListener(v -> callbacks.openRemoteHome());
        network.btnFtpClientUpload.setOnClickListener(v -> callbacks.uploadSelected());
        network.btnFtpClientDownload.setOnClickListener(v -> callbacks.downloadSelected());
        network.btnFtpClientDeleteLocal.setOnClickListener(v -> callbacks.deleteLocalSelected());
        network.btnFtpClientDeleteRemote.setOnClickListener(v -> callbacks.deleteRemoteSelected());
        network.btnFtpClientLocalNewFolder.setOnClickListener(v -> callbacks.createLocalFolder());
        network.btnFtpClientRemoteNewFolder.setOnClickListener(v -> callbacks.createRemoteFolder());

        callbacks.refreshLocalList();
        callbacks.clearRemoteList();
        callbacks.updateClientUi();
    }

    private static void allowNestedListScroll(ListView list) {
        if (list == null) return;
        list.setOnTouchListener((view, event) -> {
            try {
                boolean keepListScrolling = event != null
                        && event.getActionMasked() != MotionEvent.ACTION_UP
                        && event.getActionMasked() != MotionEvent.ACTION_CANCEL;
                ViewParent parent = view == null ? null : view.getParent();
                while (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(keepListScrolling);
                    parent = parent.getParent();
                }
            } catch (Throwable ignored) {
            }
            return false;
        });
    }

    private static String nonEmpty(String value, String fallback) {
        String s = value == null ? "" : value.trim();
        return TextUtils.isEmpty(s) ? fallback : s;
    }

    public static final class State {
        public final String host;
        public final int port;
        public final String user;
        public final String localDirectory;
        public final boolean useShizuku;

        public State(String host, int port, String user, String localDirectory, boolean useShizuku) {
            this.host = host == null ? "" : host;
            this.port = port < 1 || port > 65535 ? 21 : port;
            this.user = nonEmpty(user, "anonymous");
            this.localDirectory = FtpClientLocalEntry.normalizePath(localDirectory);
            this.useShizuku = useShizuku;
        }
    }

    public interface Callbacks {
        void ensureClient();
        State loadClientState();
        void setLocalDirectory(String localDirectory);
        void setAdapters(ArrayAdapter<String> localAdapter, ArrayAdapter<String> remoteAdapter);
        void onUseShizukuChanged(boolean checked);
        void onLocalItemClick(int position);
        boolean onLocalItemLongClick(int position);
        void onRemoteItemClick(int position);
        boolean onRemoteItemLongClick(int position);
        void connect();
        void disconnect();
        void refreshViews();
        void openLocalParent();
        void openLocalHome();
        void openRemoteParent();
        void openRemoteHome();
        void uploadSelected();
        void downloadSelected();
        void deleteLocalSelected();
        void deleteRemoteSelected();
        void createLocalFolder();
        void createRemoteFolder();
        void refreshLocalList();
        void clearRemoteList();
        void updateClientUi();
    }
}
