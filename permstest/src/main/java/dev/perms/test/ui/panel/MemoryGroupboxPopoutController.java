package dev.perms.test.ui.panel;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
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
import dev.perms.test.memory.MemoryPackageEntry;
import dev.perms.test.memory.MemoryTargets;
import dev.perms.test.memory.payload.MemoryPayloadEditorController;
import dev.perms.test.memory.payload.MemoryPayloadQueueController;
import dev.perms.test.ui.CollapsibleGroupboxController;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.dialog.MovableDialogChrome;

/** Adds controlled cloned popout windows for Memory tab groupboxes. */
public final class MemoryGroupboxPopoutController {
    public interface Host {
        void debugOutput(String area, String message);
    }

    private static final Set<String> ACTIVE_KEYS = Collections.synchronizedSet(new HashSet<>());
    private static final String TAG_OVERLAY_OPTIONS = "memory_popout_overlay_options";
    private static final String TAG_MEMORY = "memory_popout_memory";
    private static final String TAG_PROCESSES = "memory_popout_processes";
    private static final String TAG_PAYLOAD_EDITOR = "memory_popout_payload_editor";
    private static final String TAG_RUN_PAYLOADS = "memory_popout_run_payloads";

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final Host host;

    public MemoryGroupboxPopoutController(AppCompatActivity activity,
                                          ActivityMainBinding binding,
                                          Host host) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
    }

    public void bind() {
        try {
            if (binding == null || binding.tabMemory == null) return;
            View memoryRoot = binding.tabMemory.getRoot();
            bindButton(findTaggedButton(memoryRoot, TAG_OVERLAY_OPTIONS),
                    v -> openMemoryCardPopout("memory:overlay_options", "Overlay Options", R.id.cardMemoryOverlayOptions, CardMode.PROXY));
            bindButton(findTaggedButton(memoryRoot, TAG_MEMORY),
                    v -> openMemoryCardPopout("memory:main", "Memory", R.id.cardMemoryMain, CardMode.PROXY));
            bindButton(findTaggedButton(memoryRoot, TAG_PROCESSES),
                    v -> openMemoryCardPopout("memory:processes", "Processes", R.id.cardMemoryProcesses, CardMode.PROXY));
            bindButton(findTaggedButton(memoryRoot, TAG_PAYLOAD_EDITOR),
                    v -> openMemoryCardPopout("memory:payload_editor", "Payload Editor", R.id.cardMemoryPayloadEditor, CardMode.PAYLOAD_EDITOR));
            bindButton(findTaggedButton(memoryRoot, TAG_RUN_PAYLOADS),
                    v -> openMemoryCardPopout("memory:run_payloads", "Run Payloads", R.id.cardMemoryRunPayloads, CardMode.RUN_PAYLOADS));
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

    private void openMemoryCardPopout(String key, String title, int cardId, CardMode mode) {
        if (!ensurePanelsEnabled()) return;
        if (!reserve(key)) return;
        try {
            View card = inflateDetachedMemoryCard(cardId);
            if (card == null) throw new IllegalStateException("card clone unavailable");
            hideNestedPopoutButtons(card);

            final boolean[] syncing = new boolean[]{false};
            Runnable onDismiss = null;
            if (mode == CardMode.PAYLOAD_EDITOR) {
                new MemoryPayloadEditorController(activity, card, this::getOriginalTargetPackage).bind();
            } else if (mode == CardMode.RUN_PAYLOADS) {
                new MemoryPayloadQueueController(activity, card, this::getOriginalTargetPackage, this::getOriginalTargetPid).bind();
            } else {
                bindCloneInputs(card, syncing);
                bindCloneButtons(card, syncing);
                bindCloneDropdowns(card, syncing);
                syncCloneStates(card, syncing);

                final Runnable syncRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            syncCloneStates(card, syncing);
                            card.postDelayed(this, 1000L);
                        } catch (Throwable ignored) {
                        }
                    }
                };
                card.postDelayed(syncRunnable, 1000L);
                onDismiss = () -> {
                    try { card.removeCallbacks(syncRunnable); } catch (Throwable ignored) {}
                };
            }

            showMovableClone(key, title, card, onDismiss);
            debug(key, "opened cloned groupbox: " + title);
        } catch (Throwable t) {
            release(key);
            debug(key, "open failed: " + safeMessage(t));
            Toast.makeText(activity, title + " popout failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
        }
    }

    private View inflateDetachedMemoryCard(int cardId) {
        try {
            View root = LayoutInflater.from(activity).inflate(R.layout.tab_memory, null, false);
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
        hideTaggedButton(root, TAG_OVERLAY_OPTIONS);
        hideTaggedButton(root, TAG_MEMORY);
        hideTaggedButton(root, TAG_PROCESSES);
        hideTaggedButton(root, TAG_PAYLOAD_EDITOR);
        hideTaggedButton(root, TAG_RUN_PAYLOADS);
    }

    private void bindCloneButtons(View root, final boolean[] syncing) {
        List<MaterialButton> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (MaterialButton cloneButton : buttons) {
            if (cloneButton == null) continue;
            int id = cloneButton.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(cloneButton)) continue;
            final View original = findOriginalMemoryView(id);
            if (original == null) continue;
            cloneButton.setOnClickListener(v -> {
                try {
                    copyCloneInputsToOriginal(root, syncing);
                    original.performClick();
                    v.postDelayed(() -> syncCloneStates(root, syncing), 300L);
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
            final View original = findOriginalMemoryView(id);
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
            bindTargetPackageDropdownClone(root, syncing);
        } catch (Throwable ignored) {
        }
        try {
            bindProcessDropdownClone(root, syncing);
        } catch (Throwable ignored) {
        }
    }

    private void bindTargetPackageDropdownClone(View root, final boolean[] syncing) {
        AutoCompleteTextView clone = root.findViewById(R.id.edtMemoryTargetPkg);
        TextInputLayout cloneLayout = root.findViewById(R.id.tilMemoryTargetPkg);
        AutoCompleteTextView original = findOriginalMemoryView(R.id.edtMemoryTargetPkg);
        if (clone == null || original == null) return;
        if (original.getAdapter() instanceof ArrayAdapter<?>) {
            clone.setAdapter((ArrayAdapter<?>) original.getAdapter());
        }
        DropdownUi.bindTapOnlyExposedDropdown(
                activity,
                cloneLayout,
                clone,
                ViewConfiguration.get(activity).getScaledTouchSlop(),
                300,
                () -> {
                    try {
                        if (clone.getAdapter() == null || clone.getAdapter().getCount() == 0) {
                            View refresh = findOriginalMemoryView(R.id.btnMemoryRefreshTargetPackages);
                            if (refresh != null) refresh.performClick();
                        }
                        DropdownUi.showDropdownAtLastSelection(clone,
                                clone.getText() == null ? "" : clone.getText().toString(),
                                null);
                    } catch (Throwable ignored) {
                    }
                });
        clone.setOnItemClickListener((parent, view, position, id) -> {
            if (syncing[0]) return;
            try {
                Object obj = parent == null ? null : parent.getItemAtPosition(position);
                String pkg = "";
                if (obj instanceof MemoryPackageEntry) {
                    pkg = ((MemoryPackageEntry) obj).pkg;
                } else if (obj != null) {
                    pkg = String.valueOf(obj).trim();
                }
                clone.setText(pkg, false);
                original.setText(pkg, false);
                View refreshProcesses = findOriginalMemoryView(R.id.btnMemoryRefreshProcesses);
                if (refreshProcesses != null) refreshProcesses.performClick();
            } catch (Throwable ignored) {
            }
        });
    }

    private void bindProcessDropdownClone(View root, final boolean[] syncing) {
        AutoCompleteTextView clone = root.findViewById(R.id.ddMemoryProcess);
        TextInputLayout cloneLayout = root.findViewById(R.id.tilMemoryProcess);
        AutoCompleteTextView original = findOriginalMemoryView(R.id.ddMemoryProcess);
        if (clone == null || original == null) return;
        if (original.getAdapter() instanceof ArrayAdapter<?>) {
            clone.setAdapter((ArrayAdapter<?>) original.getAdapter());
        }
        DropdownUi.bindTapOnlyExposedDropdown(
                activity,
                cloneLayout,
                clone,
                ViewConfiguration.get(activity).getScaledTouchSlop(),
                300,
                () -> {
                    try {
                        if (clone.getAdapter() == null || clone.getAdapter().getCount() <= 1) {
                            View refresh = findOriginalMemoryView(R.id.btnMemoryRefreshProcesses);
                            if (refresh != null) refresh.performClick();
                        }
                        DropdownUi.showDropdownAtLastSelection(clone,
                                clone.getText() == null ? "" : clone.getText().toString(),
                                null);
                    } catch (Throwable ignored) {
                    }
                });
        clone.setOnItemClickListener((parent, view, position, id) -> {
            if (syncing[0]) return;
            try {
                Object obj = parent == null ? null : parent.getItemAtPosition(position);
                String text = obj == null ? "" : String.valueOf(obj);
                clone.setText(text, false);
                original.setText(text, false);
            } catch (Throwable ignored) {
            }
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

    private void copyCloneInputsToOriginal(View root, final boolean[] syncing) {
        if (root == null) return;
        List<View> inputs = new ArrayList<>();
        collectInputViews(root, inputs);
        for (View input : inputs) {
            int id = input == null ? View.NO_ID : input.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(input)) continue;
            View original = findOriginalMemoryView(id);
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
            View original = findOriginalMemoryView(id);
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
                    ((TextView) dst).setText(text);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findOriginalMemoryView(int id) {
        try {
            if (binding == null || binding.tabMemory == null || binding.tabMemory.getRoot() == null) return null;
            return (T) binding.tabMemory.getRoot().findViewById(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String getOriginalTargetPackage() {
        try {
            TextView target = findOriginalMemoryView(R.id.edtMemoryTargetPkg);
            return target == null || target.getText() == null ? "" : target.getText().toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getOriginalTargetPid() {
        try {
            TextView process = findOriginalMemoryView(R.id.ddMemoryProcess);
            return process == null ? "" : MemoryTargets.parseSelectedProcessPid(process.getText());
        } catch (Throwable ignored) {
            return "";
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
        return TAG_OVERLAY_OPTIONS.equals(tag)
                || TAG_MEMORY.equals(tag)
                || TAG_PROCESSES.equals(tag)
                || TAG_PAYLOAD_EDITOR.equals(tag)
                || TAG_RUN_PAYLOADS.equals(tag);
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

    private enum CardMode {
        PROXY,
        PAYLOAD_EDITOR,
        RUN_PAYLOADS
    }
}
