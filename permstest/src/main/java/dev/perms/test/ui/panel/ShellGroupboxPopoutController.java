package dev.perms.test.ui.panel;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

/** Adds controlled cloned popout windows for selected Shell tab groupboxes. */
public final class ShellGroupboxPopoutController {
    public interface Host {
        void debugOutput(String area, String message);
    }

    private static final Set<String> ACTIVE_KEYS = Collections.synchronizedSet(new HashSet<>());
    private static final String TAG_CUSTOM_COMMANDS = "shell_popout_custom_commands";
    private static final String TAG_SHELL_COMMANDS = "shell_popout_shell_commands";
    private static final String TAG_SYSTEM_BINARIES = "shell_popout_system_binaries";

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final Host host;

    public ShellGroupboxPopoutController(AppCompatActivity activity,
                                         ActivityMainBinding binding,
                                         Host host) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
    }

    public void bind() {
        try {
            if (binding == null || binding.tabShell == null) return;
            View shellRoot = binding.tabShell.getRoot();
            bindButton(findTaggedButton(shellRoot, TAG_CUSTOM_COMMANDS),
                    v -> openShellCardPopout("shell:custom_commands", "Custom Commands", R.id.cardCustomCommands));
            bindButton(findTaggedButton(shellRoot, TAG_SHELL_COMMANDS),
                    v -> openShellCardPopout("shell:shell_commands", "Shell Commands", R.id.cardShellCommands));
            bindButton(findTaggedButton(shellRoot, TAG_SYSTEM_BINARIES),
                    v -> openShellCardPopout("shell:system_binaries", "System Binaries", R.id.cardSystemBinaries));
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

    private void openShellCardPopout(String key, String title, int cardId) {
        if (!ensurePanelsEnabled()) return;
        if (!reserve(key)) return;
        try {
            View card = inflateDetachedShellCard(cardId);
            if (card == null) throw new IllegalStateException("card clone unavailable");
            hideNestedPopoutButtons(card);
            bindCloneButtons(card);
            attachCustomCommandsCloneIfPresent(card);
            syncCloneStates(card);

            final Runnable syncRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        syncCloneStates(card);
                        card.postDelayed(this, 1000L);
                    } catch (Throwable ignored) {
                    }
                }
            };
            card.postDelayed(syncRunnable, 1000L);
            showMovableClone(key, title, card, () -> {
                try { card.removeCallbacks(syncRunnable); } catch (Throwable ignored) {}
                detachCustomCommandsCloneIfPresent(card);
            });
            debug(key, "opened cloned groupbox: " + title);
        } catch (Throwable t) {
            release(key);
            debug(key, "open failed: " + safeMessage(t));
            Toast.makeText(activity, title + " popout failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
        }
    }

    private View inflateDetachedShellCard(int cardId) {
        try {
            View root = LayoutInflater.from(activity).inflate(R.layout.tab_shell, null, false);
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
        hideTaggedButton(root, TAG_CUSTOM_COMMANDS);
        hideTaggedButton(root, TAG_SHELL_COMMANDS);
        hideTaggedButton(root, TAG_SYSTEM_BINARIES);
    }

    private void bindCloneButtons(View root) {
        List<MaterialButton> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (MaterialButton cloneButton : buttons) {
            if (cloneButton == null) continue;
            int id = cloneButton.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(cloneButton)) continue;
            final View original = findOriginalShellView(id);
            if (original == null) continue;
            cloneButton.setOnClickListener(v -> {
                try {
                    original.performClick();
                    v.postDelayed(() -> syncCloneStates(root), 250L);
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

    private void collectButtons(View view, List<MaterialButton> out) {
        if (view == null || out == null) return;
        if (view instanceof MaterialButton) out.add((MaterialButton) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectButtons(group.getChildAt(i), out);
        }
    }

    private void attachCustomCommandsCloneIfPresent(View root) {
        try {
            RecyclerView clone = root.findViewById(R.id.customCmdRecycler);
            RecyclerView original = findOriginalShellView(R.id.customCmdRecycler) instanceof RecyclerView
                    ? (RecyclerView) findOriginalShellView(R.id.customCmdRecycler)
                    : null;
            if (clone == null || original == null || original.getAdapter() == null) return;
            clone.setLayoutManager(new GridLayoutManager(activity, 2));
            clone.setNestedScrollingEnabled(false);
            clone.setAdapter(original.getAdapter());
        } catch (Throwable ignored) {
        }
    }

    private void detachCustomCommandsCloneIfPresent(View root) {
        try {
            RecyclerView clone = root == null ? null : root.findViewById(R.id.customCmdRecycler);
            if (clone != null) clone.setAdapter(null);
        } catch (Throwable ignored) {
        }
    }

    private void syncCloneStates(View root) {
        try {
            syncViewsById(root);
        } catch (Throwable ignored) {
        }
    }

    private void syncViewsById(View root) {
        if (root == null) return;
        int id = root.getId();
        if (id != View.NO_ID && !isTaggedPopoutButton(root)) {
            View original = findOriginalShellView(id);
            if (original != null && original != root) copyStateIgnoringGroupboxCollapse(original, root);
        }
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
        try {
            dst.setEnabled(src.isEnabled());
        } catch (Throwable ignored) {
        }
        try {
            dst.setAlpha(src.getAlpha());
        } catch (Throwable ignored) {
        }
        try {
            if (src instanceof TextView && dst instanceof TextView && !(dst instanceof MaterialButton)) {
                CharSequence text = ((TextView) src).getText();
                if (!TextUtils.equals(((TextView) dst).getText(), text)) {
                    ((TextView) dst).setText(text);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private View findOriginalShellView(int id) {
        try {
            if (binding == null || binding.tabShell == null || binding.tabShell.getRoot() == null) return null;
            return binding.tabShell.getRoot().findViewById(id);
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
        return TAG_CUSTOM_COMMANDS.equals(tag)
                || TAG_SHELL_COMMANDS.equals(tag)
                || TAG_SYSTEM_BINARIES.equals(tag);
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
