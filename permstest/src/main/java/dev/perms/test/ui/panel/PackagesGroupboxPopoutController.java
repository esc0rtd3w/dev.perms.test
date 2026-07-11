package dev.perms.test.ui.panel;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.packages.PackageAppDropdownController;
import dev.perms.test.packages.PackageDebloatPresets;
import dev.perms.test.permissions.PermissionDropdownController;
import dev.perms.test.permissions.PermissionDropdowns;
import dev.perms.test.ui.CollapsibleGroupboxController;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.PackageDropdownEntry;
import dev.perms.test.ui.dialog.MovableDialogChrome;

/** Adds controlled cloned popout windows for selected Packages tab groupboxes. */
public final class PackagesGroupboxPopoutController {
    public interface Host {
        void debugOutput(String area, String message);
    }

    private static final Set<String> ACTIVE_KEYS = Collections.synchronizedSet(new HashSet<>());
    private static final String TAG_APK_INSTALLER = "packages_popout_apk_installer";
    private static final String TAG_CREATE_DEBUGGABLE = "packages_popout_create_debuggable";
    private static final String TAG_APK_EDITOR = "packages_popout_apk_editor";
    private static final String TAG_PERMISSIONS_STATE = "packages_popout_permissions_state";

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final PackageAppDropdownController appDropdownController;
    private final PermissionDropdownController permissionDropdownController;
    private final Host host;

    public PackagesGroupboxPopoutController(AppCompatActivity activity,
                                            ActivityMainBinding binding,
                                            PackageAppDropdownController appDropdownController,
                                            PermissionDropdownController permissionDropdownController,
                                            Host host) {
        this.activity = activity;
        this.binding = binding;
        this.appDropdownController = appDropdownController;
        this.permissionDropdownController = permissionDropdownController;
        this.host = host;
    }

    public void bind() {
        try {
            if (binding == null || binding.tabPackages == null) return;
            View packagesRoot = binding.tabPackages.getRoot();
            bindButton(findTaggedButton(packagesRoot, TAG_APK_INSTALLER),
                    v -> openPackageCardPopout("packages:apk_installer", "APK Installer", R.id.cardApkInstaller));
            bindButton(findTaggedButton(packagesRoot, TAG_CREATE_DEBUGGABLE),
                    v -> openPackageCardPopout("packages:create_debuggable", "Create Debuggable Package", R.id.cardCreateDebuggablePackage));
            bindButton(findTaggedButton(packagesRoot, TAG_APK_EDITOR),
                    v -> openPackageCardPopout("packages:apk_editor", "APK Editor", R.id.cardApkEditor));
            bindButton(findTaggedButton(packagesRoot, TAG_PERMISSIONS_STATE),
                    v -> openPackageCardPopout("packages:permissions_state", "Permissions & State", R.id.cardPermissionsState));
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

    private void openPackageCardPopout(String key, String title, int cardId) {
        if (!ensurePanelsEnabled()) return;
        if (!reserve(key)) return;
        try {
            View card = inflateDetachedPackageCard(cardId);
            if (card == null) throw new IllegalStateException("card clone unavailable");
            hideNestedPopoutButtons(card);

            final boolean[] syncing = new boolean[]{false};
            bindCloneInputs(card, syncing);
            bindCloneButtons(card, syncing);
            bindCloneDropdowns(card, syncing);
            syncCloneStates(card, syncing);
            syncCloneDropdownAdapters(card);
            appendDropdownDebug(key, card, "open");

            final Runnable syncRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        syncCloneStates(card, syncing);
                        syncCloneDropdownAdapters(card);
                        card.postDelayed(this, 1000L);
                    } catch (Throwable ignored) {
                    }
                }
            };
            card.postDelayed(syncRunnable, 1000L);
            showMovableClone(key, title, card, () -> {
                try { card.removeCallbacks(syncRunnable); } catch (Throwable ignored) {}
            });
            debug(key, "opened cloned groupbox: " + title);
        } catch (Throwable t) {
            release(key);
            debug(key, "open failed: " + safeMessage(t));
            Toast.makeText(activity, title + " popout failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
        }
    }

    private View inflateDetachedPackageCard(int cardId) {
        try {
            View root = LayoutInflater.from(activity).inflate(R.layout.tab_packages, null, false);
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
        hideTaggedButton(root, TAG_APK_INSTALLER);
        hideTaggedButton(root, TAG_CREATE_DEBUGGABLE);
        hideTaggedButton(root, TAG_APK_EDITOR);
        hideTaggedButton(root, TAG_PERMISSIONS_STATE);
    }

    private void bindCloneButtons(View root, final boolean[] syncing) {
        List<MaterialButton> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (MaterialButton cloneButton : buttons) {
            if (cloneButton == null) continue;
            int id = cloneButton.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(cloneButton)) continue;
            final View original = findOriginalPackageView(id);
            if (original == null) continue;
            cloneButton.setOnClickListener(v -> {
                try {
                    copyCloneInputsToOriginal(root, syncing);
                    original.performClick();
                    v.postDelayed(() -> {
                        syncCloneStates(root, syncing);
                        syncCloneDropdownAdapters(root);
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
            final View original = findOriginalPackageView(id);
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
            } else if (input instanceof EditText && !(input instanceof AutoCompleteTextView)
                    && original instanceof TextView) {
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

    private void bindCloneDropdowns(View root, final boolean[] syncing) {
        try {
            bindAppDropdownClone(root, syncing);
        } catch (Throwable ignored) {
        }
        try {
            bindPermissionDropdownClone(root, syncing);
        } catch (Throwable ignored) {
        }
        try {
            bindDebloatPresetDropdownClone(root);
        } catch (Throwable ignored) {
        }
    }

    private void bindAppDropdownClone(View root, final boolean[] syncing) {
        AutoCompleteTextView clone = root.findViewById(R.id.ddApp);
        TextInputLayout cloneLayout = root.findViewById(R.id.tilAppDropdown);
        if (clone == null) return;
        updateAppDropdownCloneAdapter(clone, true);
        DropdownUi.bindTapOnlyExposedDropdown(
                activity,
                cloneLayout,
                clone,
                ViewConfiguration.get(activity).getScaledTouchSlop() / 2,
                350,
                () -> DropdownUi.showDropdownAtLastSelection(clone,
                        clone.getText() == null ? "" : clone.getText().toString(),
                        null));
        clone.setOnItemClickListener((parent, view, position, id) -> {
            if (syncing[0]) return;
            try {
                Object obj = parent == null ? null : parent.getItemAtPosition(position);
                if (!(obj instanceof PackageDropdownEntry)) return;
                PackageDropdownEntry entry = (PackageDropdownEntry) obj;
                if (appDropdownController != null) appDropdownController.selectEntryFromClone(entry);
                String show = TextUtils.isEmpty(entry.pkg) ? "" : (TextUtils.isEmpty(entry.label) ? entry.pkg : entry.label);
                clone.setText(show, false);
                syncCloneStates(root, syncing);
                syncCloneDropdownAdapters(root);
            } catch (Throwable ignored) {
            }
        });
    }

    private void bindPermissionDropdownClone(View root, final boolean[] syncing) {
        AutoCompleteTextView clone = root.findViewById(R.id.ddPerm);
        TextInputLayout cloneLayout = root.findViewById(R.id.tilPermDropdown);
        if (clone == null) return;
        updatePermissionDropdownCloneAdapter(clone, true);
        DropdownUi.bindTapOnlyExposedDropdown(
                activity,
                cloneLayout,
                clone,
                ViewConfiguration.get(activity).getScaledTouchSlop(),
                0,
                () -> DropdownUi.showDropdownAtLastSelection(clone,
                        clone.getText() == null ? "" : clone.getText().toString(),
                        null));
        clone.setOnItemClickListener((parent, view, position, id) -> {
            if (syncing[0]) return;
            try {
                Object obj = parent == null ? null : parent.getItemAtPosition(position);
                if (!(obj instanceof PermissionDropdowns.Entry)) return;
                PermissionDropdowns.Entry entry = (PermissionDropdowns.Entry) obj;
                if (permissionDropdownController != null) permissionDropdownController.selectEntryFromClone(entry);
                clone.setText(TextUtils.isEmpty(entry.permission) ? "" : entry.title, false);
                syncCloneStates(root, syncing);
                syncCloneDropdownAdapters(root);
            } catch (Throwable ignored) {
            }
        });
    }


    private void bindDebloatPresetDropdownClone(View root) {
        AutoCompleteTextView clone = root.findViewById(R.id.ddPackageDebloatPreset);
        TextInputLayout cloneLayout = root.findViewById(R.id.tilPackageDebloatPreset);
        if (clone == null) return;
        try {
            List<PackageDebloatPresets.Preset> presets = PackageDebloatPresets.list(activity);
            ArrayList<String> labels = new ArrayList<>();
            for (PackageDebloatPresets.Preset preset : presets) {
                if (preset != null && !TextUtils.isEmpty(preset.label)) labels.add(preset.label);
            }
            clone.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, labels));
            if (!labels.isEmpty() && TextUtils.isEmpty(clone.getText())) {
                clone.setText(labels.get(0), false);
            }
            DropdownUi.bindExposedDropdown(activity, cloneLayout, clone, () -> DropdownUi.showDropdown(clone));
        } catch (Throwable ignored) {
        }
    }

    private void syncCloneDropdownAdapters(View root) {
        if (root == null) return;
        try {
            AutoCompleteTextView appClone = root.findViewById(R.id.ddApp);
            updateAppDropdownCloneAdapter(appClone, false);
        } catch (Throwable ignored) {
        }
        try {
            AutoCompleteTextView permClone = root.findViewById(R.id.ddPerm);
            updatePermissionDropdownCloneAdapter(permClone, false);
        } catch (Throwable ignored) {
        }
    }

    private void updateAppDropdownCloneAdapter(AutoCompleteTextView clone, boolean force) {
        if (clone == null || appDropdownController == null) return;
        try {
            ArrayAdapter<?> adapter = appDropdownController.getCurrentAdapterForClone();
            if (adapter == null) return;
            if (force || shouldReplaceAdapter(clone.getAdapter(), adapter)) {
                clone.setAdapter(adapter);
            }
        } catch (Throwable ignored) {
        }
    }

    private void updatePermissionDropdownCloneAdapter(AutoCompleteTextView clone, boolean force) {
        if (clone == null || permissionDropdownController == null) return;
        try {
            ArrayAdapter<?> adapter = permissionDropdownController.getCurrentAdapterForClone();
            if (adapter == null) return;
            if (force || shouldReplaceAdapter(clone.getAdapter(), adapter)) {
                clone.setAdapter(adapter);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean shouldReplaceAdapter(android.widget.ListAdapter current, ArrayAdapter<?> candidate) {
        if (candidate == null) return false;
        if (current == null) return true;
        try {
            int currentCount = current.getCount();
            int candidateCount = candidate.getCount();
            return currentCount != candidateCount || (currentCount <= 1 && candidateCount > 1);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void appendDropdownDebug(String area, View root, String event) {
        try {
            int appCount = adapterCount(root == null ? null : ((AutoCompleteTextView) root.findViewById(R.id.ddApp)));
            int permCount = adapterCount(root == null ? null : ((AutoCompleteTextView) root.findViewById(R.id.ddPerm)));
            if (appCount < 0 && permCount < 0) return;
            debug(area, event
                    + " dropdown counts: apps=" + appCount
                    + " permissions=" + permCount);
        } catch (Throwable ignored) {
        }
    }

    private int adapterCount(AutoCompleteTextView view) {
        if (view == null || view.getAdapter() == null) return -1;
        try {
            return view.getAdapter().getCount();
        } catch (Throwable ignored) {
            return -1;
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

    private void collectInputViews(View view, List<View> out) {
        if (view == null || out == null) return;
        if (view instanceof CompoundButton || view instanceof EditText) out.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectInputViews(group.getChildAt(i), out);
        }
    }

    private void copyCloneInputsToOriginal(View root, final boolean[] syncing) {
        if (root == null) return;
        List<View> inputs = new ArrayList<>();
        collectInputViews(root, inputs);
        for (View input : inputs) {
            int id = input == null ? View.NO_ID : input.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(input)) continue;
            View original = findOriginalPackageView(id);
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
                if (dst instanceof AutoCompleteTextView) {
                    ((AutoCompleteTextView) dst).setText(text, false);
                } else {
                    dst.setText(text);
                }
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
            View original = findOriginalPackageView(id);
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
                    if (dst instanceof AutoCompleteTextView) {
                        ((AutoCompleteTextView) dst).setText(text, false);
                    } else {
                        ((TextView) dst).setText(text);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private View findOriginalPackageView(int id) {
        try {
            if (binding == null || binding.tabPackages == null || binding.tabPackages.getRoot() == null) return null;
            return binding.tabPackages.getRoot().findViewById(id);
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
        return TAG_APK_INSTALLER.equals(tag)
                || TAG_CREATE_DEBUGGABLE.equals(tag)
                || TAG_APK_EDITOR.equals(tag)
                || TAG_PERMISSIONS_STATE.equals(tag);
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
