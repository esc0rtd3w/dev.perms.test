package dev.perms.test.ui.panel;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ListAdapter;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.CollapsibleGroupboxController;
import dev.perms.test.ui.dialog.MovableDialogChrome;

/** Adds controlled cloned popout windows for selected Network tab groupboxes. */
public final class NetworkGroupboxPopoutController {
    public interface Host {
        void debugOutput(String area, String message);
    }

    private static final Set<String> ACTIVE_KEYS = Collections.synchronizedSet(new HashSet<>());
    private static final String TAG_FTP_SERVER = "network_popout_ftp_server";
    private static final String TAG_FTP_CLIENT = "network_popout_ftp_client";
    private static final String TAG_HTTP_SERVER = "network_popout_http_server";
    private static final String TAG_WEB_INTERFACE = "network_popout_web_interface";
    private static final String TAG_SSH_SERVER = "network_popout_ssh_server";
    private static final String TAG_SSH_CLIENT = "network_popout_ssh_client";
    private static final String TAG_MULTIPLAYER_LINK = "network_popout_multiplayer_link";
    private static final String TAG_NETWORK_INFO = "network_popout_network_info";
    private static final String TAG_HOST_TESTS = "network_popout_host_tests";
    private static final String TAG_HTTP_REQUEST = "network_popout_http_request";
    private static final String TAG_TCP_PORT_CHECK = "network_popout_tcp_port_check";

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final Host host;

    public NetworkGroupboxPopoutController(AppCompatActivity activity,
                                           ActivityMainBinding binding,
                                           Host host) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
    }

    public void bind() {
        try {
            if (binding == null || binding.tabNetwork == null) return;
            View networkRoot = binding.tabNetwork.getRoot();
            bindButton(findTaggedButton(networkRoot, TAG_FTP_SERVER),
                    v -> openNetworkCardPopout("network:ftp_server", "FTP Server", R.id.cardNetworkFtpServer));
            bindButton(findTaggedButton(networkRoot, TAG_FTP_CLIENT),
                    v -> openNetworkCardPopout("network:ftp_client", "FTP Client", R.id.cardNetworkFtpClient));
            bindButton(findTaggedButton(networkRoot, TAG_HTTP_SERVER),
                    v -> openNetworkCardPopout("network:http_server", "HTTP Server", R.id.cardNetworkHttpServer));
            bindButton(findTaggedButton(networkRoot, TAG_WEB_INTERFACE),
                    v -> openNetworkCardPopout("network:web_interface", "Web Interface", R.id.cardNetworkWebInterface));
            bindButton(findTaggedButton(networkRoot, TAG_SSH_SERVER),
                    v -> openNetworkCardPopout("network:ssh_server", "SSH Server", R.id.cardNetworkSshServer));
            bindButton(findTaggedButton(networkRoot, TAG_SSH_CLIENT),
                    v -> openNetworkCardPopout("network:ssh_client", "SSH Client", R.id.cardNetworkSshClient));
            bindButton(findTaggedButton(networkRoot, TAG_MULTIPLAYER_LINK),
                    v -> openNetworkCardPopout("network:multiplayer_link", "Multiplayer Link", R.id.cardMultiplayerLink));
            bindButton(findTaggedButton(networkRoot, TAG_NETWORK_INFO),
                    v -> openNetworkCardPopout("network:info", "Network Info", R.id.cardNetworkInfo));
            bindButton(findTaggedButton(networkRoot, TAG_HOST_TESTS),
                    v -> openNetworkCardPopout("network:host_tests", "Host Tests", R.id.cardNetworkHostTests));
            bindButton(findTaggedButton(networkRoot, TAG_HTTP_REQUEST),
                    v -> openNetworkCardPopout("network:http_request", "HTTP Request", R.id.cardNetworkHttpRequest));
            bindButton(findTaggedButton(networkRoot, TAG_TCP_PORT_CHECK),
                    v -> openNetworkCardPopout("network:tcp_port_check", "TCP Port Check", R.id.cardNetworkTcpPortCheck));
        } catch (Throwable ignored) {
        }
    }

    private void bindButton(MaterialButton button, View.OnClickListener listener) {
        if (button == null) return;
        try {
            button.setAllCaps(false);
            button.setOnClickListener(listener);
        } catch (Throwable ignored) {
        }
    }

    private void openNetworkCardPopout(String key, String title, int cardId) {
        if (!ensurePanelsEnabled()) return;
        if (!reserve(key)) return;
        try {
            View card = inflateDetachedNetworkCard(cardId);
            if (card == null) throw new IllegalStateException("card clone unavailable");
            hideNestedPopoutButtons(card);

            final boolean[] syncing = new boolean[]{false};
            bindCloneInputs(card, syncing);
            bindCloneButtons(card, syncing);
            bindCloneLists(card);
            syncCloneStates(card, syncing);

            final Runnable syncRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        syncCloneStates(card, syncing);
                        syncCloneLists(card);
                        card.postDelayed(this, 1000L);
                    } catch (Throwable ignored) {
                    }
                }
            };
            card.postDelayed(syncRunnable, 1000L);
            showMovableClone(key, title, card, () -> {
                try { card.removeCallbacks(syncRunnable); } catch (Throwable ignored) {}
                detachCloneLists(card);
            });
            debug(key, "opened cloned groupbox: " + title);
        } catch (Throwable t) {
            release(key);
            debug(key, "open failed: " + safeMessage(t));
            Toast.makeText(activity, title + " popout failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
        }
    }

    private View inflateDetachedNetworkCard(int cardId) {
        try {
            View root = LayoutInflater.from(activity).inflate(R.layout.tab_network, null, false);
            View card = root.findViewById(cardId);
            if (card == null) return null;
            ViewParent parent = card.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(card);
            }
            card.setVisibility(View.VISIBLE);
            return card;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void hideNestedPopoutButtons(View root) {
        hideTaggedButton(root, TAG_FTP_SERVER);
        hideTaggedButton(root, TAG_FTP_CLIENT);
        hideTaggedButton(root, TAG_HTTP_SERVER);
        hideTaggedButton(root, TAG_WEB_INTERFACE);
        hideTaggedButton(root, TAG_MULTIPLAYER_LINK);
        hideTaggedButton(root, TAG_NETWORK_INFO);
        hideTaggedButton(root, TAG_HOST_TESTS);
        hideTaggedButton(root, TAG_HTTP_REQUEST);
        hideTaggedButton(root, TAG_TCP_PORT_CHECK);
    }

    private void bindCloneButtons(View root, final boolean[] syncing) {
        List<MaterialButton> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (MaterialButton cloneButton : buttons) {
            if (cloneButton == null) continue;
            int id = cloneButton.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(cloneButton)) continue;
            final View original = findOriginalNetworkView(id);
            if (original == null) continue;
            cloneButton.setOnClickListener(v -> {
                try {
                    copyCloneInputsToOriginal(root, syncing);
                    original.performClick();
                    v.postDelayed(() -> {
                        syncCloneStates(root, syncing);
                        syncCloneLists(root);
                    }, 300L);
                } catch (Throwable ignored) {
                }
            });
            try {
                cloneButton.setOnLongClickListener(v -> {
                    try {
                        return original.performLongClick();
                    } catch (Throwable ignored) {
                        return false;
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private void bindCloneInputs(View root, final boolean[] syncing) {
        List<View> inputs = new ArrayList<>();
        collectInputViews(root, inputs);
        for (View input : inputs) {
            if (input == null) continue;
            int id = input.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(input)) continue;
            final View original = findOriginalNetworkView(id);
            if (original == null) continue;
            if (input instanceof CompoundButton && original instanceof CompoundButton) {
                final CompoundButton cloneButton = (CompoundButton) input;
                cloneButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (syncing[0]) return;
                    try {
                        ((CompoundButton) original).setChecked(isChecked);
                    } catch (Throwable ignored) {
                    }
                });
            } else if (input instanceof EditText && original instanceof TextView) {
                final EditText cloneText = (EditText) input;
                cloneText.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (syncing[0]) return;
                        copyText(cloneText, (TextView) original);
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });
            }
        }
    }

    private void syncCloneLists(View root) {
        bindCloneLists(root);
    }

    private void bindCloneLists(View root) {
        List<ListView> lists = new ArrayList<>();
        collectListViews(root, lists);
        for (ListView clone : lists) {
            if (clone == null) continue;
            int id = clone.getId();
            if (id == View.NO_ID) continue;
            View originalView = findOriginalNetworkView(id);
            if (!(originalView instanceof ListView)) continue;
            ListView original = (ListView) originalView;
            try {
                ListAdapter adapter = original.getAdapter();
                if (clone.getAdapter() != adapter) clone.setAdapter(adapter);
            } catch (Throwable ignored) {
            }
            try { clone.setChoiceMode(original.getChoiceMode()); } catch (Throwable ignored) {}
            try { clone.setFastScrollEnabled(original.isFastScrollEnabled()); } catch (Throwable ignored) {}
            allowNestedListScroll(clone);
            clone.setOnItemClickListener((parent, view, position, id1) -> {
                try {
                    original.performItemClick(null, position, id1);
                } catch (Throwable ignored) {
                }
            });
            clone.setOnItemLongClickListener((parent, view, position, id1) -> {
                try {
                    View child = original.getChildAt(position - original.getFirstVisiblePosition());
                    return original.performLongClick();
                } catch (Throwable ignored) {
                    return false;
                }
            });
        }
    }

    private void detachCloneLists(View root) {
        try {
            List<ListView> lists = new ArrayList<>();
            collectListViews(root, lists);
            for (ListView list : lists) {
                try { list.setAdapter(null); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
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

    private void collectButtons(View view, List<MaterialButton> out) {
        if (view == null || out == null) return;
        if (view instanceof MaterialButton) out.add((MaterialButton) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectButtons(group.getChildAt(i), out);
        }
    }

    private void collectInputViews(View view, List<View> out) {
        if (view == null || out == null) return;
        if (view instanceof CompoundButton || view instanceof EditText) out.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectInputViews(group.getChildAt(i), out);
        }
    }

    private void collectListViews(View view, List<ListView> out) {
        if (view == null || out == null) return;
        if (view instanceof ListView) out.add((ListView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectListViews(group.getChildAt(i), out);
        }
    }

    private void copyCloneInputsToOriginal(View root, final boolean[] syncing) {
        if (root == null) return;
        List<View> inputs = new ArrayList<>();
        collectInputViews(root, inputs);
        for (View input : inputs) {
            int id = input == null ? View.NO_ID : input.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(input)) continue;
            View original = findOriginalNetworkView(id);
            if (original == null || original == input) continue;
            copyInput(input, original, syncing);
        }
    }

    private void copyInput(View src, View dst, final boolean[] syncing) {
        if (src == null || dst == null) return;
        try {
            if (src instanceof CompoundButton && dst instanceof CompoundButton) {
                ((CompoundButton) dst).setChecked(((CompoundButton) src).isChecked());
            } else if (src instanceof TextView && dst instanceof TextView) {
                copyText((TextView) src, (TextView) dst);
            }
        } catch (Throwable ignored) {
        }
    }

    private void copyText(TextView src, TextView dst) {
        if (src == null || dst == null) return;
        try {
            CharSequence text = src.getText();
            if (!TextUtils.equals(dst.getText(), text)) {
                dst.setText(text);
            }
        } catch (Throwable ignored) {
        }
    }

    private void syncCloneStates(View root, final boolean[] syncing) {
        syncing[0] = true;
        try {
            syncViewsById(root);
        } catch (Throwable ignored) {
        } finally {
            syncing[0] = false;
        }
    }

    private void syncViewsById(View root) {
        if (root == null) return;
        int id = root.getId();
        if (id != View.NO_ID && !isTaggedPopoutButton(root)) {
            View original = findOriginalNetworkView(id);
            if (original != null && original != root) copyStateIgnoringGroupboxCollapse(original, root);
        }

        // Adapter-backed rows reuse framework child IDs such as android.R.id.text1.
        // Do not recurse into ListView row children or every cloned FTP row can be
        // overwritten with the first matching original row during periodic sync.
        if (root instanceof AdapterView) return;

        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            syncViewsById(group.getChildAt(i));
        }
    }

    private void copyStateIgnoringGroupboxCollapse(View src, View dst) {
        if (src == null || dst == null) return;
        try {
            dst.setVisibility(CollapsibleGroupboxController.visibilityIgnoringGroupboxCollapse(src));
        } catch (Throwable ignored) {
        }
        try { dst.setEnabled(src.isEnabled()); } catch (Throwable ignored) {}
        try { dst.setAlpha(src.getAlpha()); } catch (Throwable ignored) {}
        try {
            if (src instanceof CompoundButton && dst instanceof CompoundButton) {
                ((CompoundButton) dst).setChecked(((CompoundButton) src).isChecked());
            }
        } catch (Throwable ignored) {}
        try {
            if (src instanceof TextView && dst instanceof TextView && !(dst instanceof MaterialButton)) {
                if (dst instanceof EditText && dst.hasFocus()) return;
                CharSequence text = ((TextView) src).getText();
                if (!TextUtils.equals(((TextView) dst).getText(), text)) {
                    ((TextView) dst).setText(text);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private View findOriginalNetworkView(int id) {
        try {
            if (binding == null || binding.tabNetwork == null || binding.tabNetwork.getRoot() == null) return null;
            return binding.tabNetwork.getRoot().findViewById(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private MaterialButton findTaggedButton(View root, String tag) {
        if (root == null || tag == null) return null;
        if (root instanceof MaterialButton && tag.equals(String.valueOf(root.getTag()))) {
            return (MaterialButton) root;
        }
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            MaterialButton found = findTaggedButton(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private void hideTaggedButton(View root, String tag) {
        try {
            MaterialButton button = findTaggedButton(root, tag);
            if (button != null) button.setVisibility(View.GONE);
        } catch (Throwable ignored) {
        }
    }

    private boolean isTaggedPopoutButton(View view) {
        if (view == null) return false;
        String tag = String.valueOf(view.getTag());
        return TAG_FTP_SERVER.equals(tag)
                || TAG_FTP_CLIENT.equals(tag)
                || TAG_HTTP_SERVER.equals(tag)
                || TAG_WEB_INTERFACE.equals(tag)
                || TAG_MULTIPLAYER_LINK.equals(tag)
                || TAG_NETWORK_INFO.equals(tag)
                || TAG_HOST_TESTS.equals(tag)
                || TAG_HTTP_REQUEST.equals(tag)
                || TAG_TCP_PORT_CHECK.equals(tag);
    }

    private void showMovableClone(String key, String title, View content, Runnable onDismiss) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        MovableDialogChrome.Chrome chrome = MovableDialogChrome.create(activity, scroll, MovableDialogChrome.STYLE_FULL);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(chrome.root)
                .create();
        dialog.setOnDismissListener(d -> {
            try {
                if (onDismiss != null) onDismiss.run();
            } catch (Throwable ignored) {
            }
            release(key);
            debug(key, "closed cloned groupbox: " + title);
        });
        if (chrome.closeButton != null) chrome.closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        MovableDialogChrome.applyWindowStyle(dialog, MovableDialogChrome.STYLE_FULL, MovableDialogChrome.FIT_CURRENT);
        MovableDialogChrome.enable(dialog, chrome.dragHandle);
    }

    private boolean ensurePanelsEnabled() {
        if (PermsTestPanelSettings.isPanelHostEnabled(activity)) return true;
        Toast.makeText(activity, "Enable Popout Panels in Settings > UI first", Toast.LENGTH_SHORT).show();
        return false;
    }

    private boolean reserve(String key) {
        synchronized (ACTIVE_KEYS) {
            if (ACTIVE_KEYS.contains(key)) {
                debug(key, "open skipped: already open");
                Toast.makeText(activity, "That groupbox popout is already open", Toast.LENGTH_SHORT).show();
                return false;
            }
            ACTIVE_KEYS.add(key);
            return true;
        }
    }

    private void release(String key) {
        if (key == null) return;
        ACTIVE_KEYS.remove(key);
    }

    private void debug(String area, String message) {
        try {
            if (host != null) host.debugOutput(area, message);
        } catch (Throwable ignored) {
        }
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.toString() : msg;
    }
}
