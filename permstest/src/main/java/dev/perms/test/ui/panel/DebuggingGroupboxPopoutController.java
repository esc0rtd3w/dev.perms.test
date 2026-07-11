package dev.perms.test.ui.panel;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

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
import dev.perms.test.ui.CollapsibleGroupboxController;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.dialog.MovableDialogChrome;

/** Adds controlled cloned popout windows for selected Debugging tab groupboxes. */
public final class DebuggingGroupboxPopoutController {
    public interface Host {
        void debugOutput(String area, String message);
        void selectInstalledPackageFromClone(Object entry);
        void selectDexEntryFromClone(String entry);
    }

    private static final Set<String> ACTIVE_KEYS = Collections.synchronizedSet(new HashSet<>());
    private static final String TAG_ASSEMBLER = "debugging_popout_assembler";
    private static final String TAG_MITM_PATCH = "debugging_popout_mitm_patch";
    private static final String TAG_SMALI_EDITOR = "debugging_popout_smali_editor";

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final Host host;

    public DebuggingGroupboxPopoutController(AppCompatActivity activity,
                                             ActivityMainBinding binding,
                                             Host host) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
    }

    public void bind() {
        try {
            if (binding == null || binding.tabDebugging == null) return;
            View debuggingRoot = binding.tabDebugging.getRoot();
            bindButton(findTaggedButton(debuggingRoot, TAG_ASSEMBLER),
                    v -> openDebuggingCardPopout("debugging:assembler", "Assembler/Disassembler", R.id.cardDebuggingAssembler));
            bindButton(findTaggedButton(debuggingRoot, TAG_MITM_PATCH),
                    v -> openDebuggingCardPopout("debugging:mitm_patch", "Network Inspection / MITM Patch", R.id.cardMitmPatch));
            bindButton(findTaggedButton(debuggingRoot, TAG_SMALI_EDITOR),
                    v -> openDebuggingCardPopout("debugging:smali_editor", "Smali Editor", R.id.cardSmaliEditor));
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

    private void openDebuggingCardPopout(String key, String title, int cardId) {
        if (!ensurePanelsEnabled()) return;
        if (!reserve(key)) return;
        try {
            View card = inflateDetachedDebuggingCard(cardId);
            if (card == null) throw new IllegalStateException("card clone unavailable");
            hideNestedPopoutButtons(card);

            final boolean[] syncing = new boolean[]{false};
            bindCloneInputs(card, syncing);
            bindCloneButtons(card, syncing);
            bindCloneLists(card);
            bindCloneDropdowns(card, syncing);
            syncCloneStates(card, syncing);
            syncCloneDropdownAdapters(card);

            final Runnable syncRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        syncCloneStates(card, syncing);
                        syncCloneLists(card);
                        syncCloneDropdownAdapters(card);
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

    private View inflateDetachedDebuggingCard(int cardId) {
        try {
            View root = LayoutInflater.from(activity).inflate(R.layout.tab_debugging, null, false);
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
        hideTaggedButton(root, TAG_ASSEMBLER);
        hideTaggedButton(root, TAG_MITM_PATCH);
        hideTaggedButton(root, TAG_SMALI_EDITOR);
    }

    private void bindCloneButtons(View root, final boolean[] syncing) {
        List<MaterialButton> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (MaterialButton cloneButton : buttons) {
            if (cloneButton == null) continue;
            int id = cloneButton.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(cloneButton)) continue;
            final View original = findOriginalDebuggingView(id);
            if (original == null) continue;
            cloneButton.setOnClickListener(v -> {
                try {
                    copyCloneInputsToOriginal(root, syncing);
                    original.performClick();
                    v.postDelayed(() -> {
                        syncCloneStates(root, syncing);
                        syncCloneLists(root);
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
            final View original = findOriginalDebuggingView(id);
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

    private void bindCloneLists(View root) {
        List<ListView> lists = new ArrayList<>();
        collectListViews(root, lists);
        for (ListView clone : lists) {
            if (clone == null) continue;
            int id = clone.getId();
            if (id == View.NO_ID) continue;
            View originalView = findOriginalDebuggingView(id);
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
                    copyCloneInputsToOriginal(root, new boolean[]{false});
                    original.performItemClick(null, position, id1);
                    clone.postDelayed(() -> syncCloneStates(root, new boolean[]{false}), 250L);
                } catch (Throwable ignored) {
                }
            });
            clone.setOnItemLongClickListener((parent, view, position, id1) -> {
                try {
                    return original.performLongClick();
                } catch (Throwable ignored) {
                    return false;
                }
            });
        }
    }

    private void syncCloneLists(View root) {
        bindCloneLists(root);
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

    private void bindCloneDropdowns(View root, final boolean[] syncing) {
        try { bindInstalledPackageDropdownClone(root, syncing); } catch (Throwable ignored) {}
        try { bindDexEntryDropdownClone(root, syncing); } catch (Throwable ignored) {}
        try { bindSearchFilterDropdownClone(root, syncing); } catch (Throwable ignored) {}
    }

    private void bindInstalledPackageDropdownClone(View root, final boolean[] syncing) {
        AutoCompleteTextView clone = root.findViewById(R.id.ddDebuggingInstalledPackage);
        TextInputLayout cloneLayout = root.findViewById(R.id.tilDebuggingInstalledPackage);
        if (clone == null) return;
        updateDropdownCloneAdapter(clone, R.id.ddDebuggingInstalledPackage, true);
        DropdownUi.bindTapOnlyExposedDropdown(
                activity,
                cloneLayout,
                clone,
                ViewConfiguration.get(activity).getScaledTouchSlop(),
                300,
                () -> DropdownUi.showDropdownAtLastSelection(clone,
                        clone.getText() == null ? "" : clone.getText().toString(),
                        null));
        clone.setOnItemClickListener((parent, view, position, id) -> {
            if (syncing[0]) return;
            try {
                Object obj = parent == null ? null : parent.getItemAtPosition(position);
                if (obj == null) return;
                clone.setText(obj.toString(), false);
                if (host != null) host.selectInstalledPackageFromClone(obj);
                syncCloneStates(root, syncing);
                syncCloneDropdownAdapters(root);
            } catch (Throwable ignored) {
            }
        });
    }

    private void bindDexEntryDropdownClone(View root, final boolean[] syncing) {
        AutoCompleteTextView clone = root.findViewById(R.id.ddSmaliDexEntry);
        TextInputLayout cloneLayout = root.findViewById(R.id.tilSmaliDexEntry);
        if (clone == null) return;
        updateDropdownCloneAdapter(clone, R.id.ddSmaliDexEntry, true);
        DropdownUi.bindTapOnlyExposedDropdown(
                activity,
                cloneLayout,
                clone,
                ViewConfiguration.get(activity).getScaledTouchSlop(),
                300,
                () -> DropdownUi.showDropdownAtLastSelection(clone,
                        clone.getText() == null ? "" : clone.getText().toString(),
                        null));
        clone.setOnItemClickListener((parent, view, position, id) -> {
            if (syncing[0]) return;
            try {
                Object obj = parent == null ? null : parent.getItemAtPosition(position);
                String text = obj == null ? "" : obj.toString();
                clone.setText(text, false);
                if (host != null) host.selectDexEntryFromClone(text);
                syncCloneStates(root, syncing);
                syncCloneDropdownAdapters(root);
            } catch (Throwable ignored) {
            }
        });
    }

    private void bindSearchFilterDropdownClone(View root, final boolean[] syncing) {
        AutoCompleteTextView clone = root.findViewById(R.id.ddSmaliSearchFilter);
        TextInputLayout cloneLayout = root.findViewById(R.id.tilSmaliSearchFilter);
        if (clone == null) return;
        updateDropdownCloneAdapter(clone, R.id.ddSmaliSearchFilter, true);
        DropdownUi.bindTapOnlyExposedDropdown(
                activity,
                cloneLayout,
                clone,
                ViewConfiguration.get(activity).getScaledTouchSlop(),
                300,
                () -> DropdownUi.showDropdownAtLastSelection(clone,
                        clone.getText() == null ? "" : clone.getText().toString(),
                        null));
        clone.setOnItemClickListener((parent, view, position, id) -> {
            if (syncing[0]) return;
            try {
                Object obj = parent == null ? null : parent.getItemAtPosition(position);
                String text = obj == null ? "" : obj.toString();
                clone.setText(text, false);
                View original = findOriginalDebuggingView(R.id.ddSmaliSearchFilter);
                if (original instanceof AutoCompleteTextView) {
                    ((AutoCompleteTextView) original).setText(text, false);
                } else if (original instanceof TextView) {
                    ((TextView) original).setText(text);
                }
                syncCloneStates(root, syncing);
                syncCloneDropdownAdapters(root);
            } catch (Throwable ignored) {
            }
        });
    }

    private void syncCloneDropdownAdapters(View root) {
        if (root == null) return;
        try { updateDropdownCloneAdapter(root.findViewById(R.id.ddDebuggingInstalledPackage), R.id.ddDebuggingInstalledPackage, false); } catch (Throwable ignored) {}
        try { updateDropdownCloneAdapter(root.findViewById(R.id.ddSmaliDexEntry), R.id.ddSmaliDexEntry, false); } catch (Throwable ignored) {}
        try { updateDropdownCloneAdapter(root.findViewById(R.id.ddSmaliSearchFilter), R.id.ddSmaliSearchFilter, false); } catch (Throwable ignored) {}
    }

    private void updateDropdownCloneAdapter(AutoCompleteTextView clone, int originalId, boolean force) {
        if (clone == null) return;
        try {
            View originalView = findOriginalDebuggingView(originalId);
            if (!(originalView instanceof AutoCompleteTextView)) return;
            AutoCompleteTextView original = (AutoCompleteTextView) originalView;
            Adapter adapter = original.getAdapter();
            ArrayAdapter<Object> snapshot = snapshotAdapter(adapter);
            if (snapshot == null) return;
            if (force || shouldReplaceAdapter(clone.getAdapter(), snapshot)) {
                clone.setAdapter(snapshot);
            }
        } catch (Throwable ignored) {
        }
    }

    private ArrayAdapter<Object> snapshotAdapter(Adapter adapter) {
        if (adapter == null) return null;
        ArrayList<Object> items = new ArrayList<>();
        try {
            int count = adapter.getCount();
            for (int i = 0; i < count; i++) {
                Object item = adapter.getItem(i);
                if (item != null) items.add(item);
            }
        } catch (Throwable ignored) {
        }
        if (items.isEmpty()) return null;
        return new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, items);
    }

    private boolean shouldReplaceAdapter(ListAdapter current, ArrayAdapter<Object> candidate) {
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

    private void collectButtons(View view, List<MaterialButton> out) {
        if (view == null || out == null) return;
        if (view instanceof MaterialButton) out.add((MaterialButton) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collectButtons(group.getChildAt(i), out);
    }

    private void collectInputViews(View view, List<View> out) {
        if (view == null || out == null) return;
        if (view instanceof CompoundButton || view instanceof EditText) out.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collectInputViews(group.getChildAt(i), out);
    }

    private void collectListViews(View view, List<ListView> out) {
        if (view == null || out == null) return;
        if (view instanceof ListView) out.add((ListView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collectListViews(group.getChildAt(i), out);
    }

    private void copyCloneInputsToOriginal(View root, final boolean[] syncing) {
        if (root == null) return;
        List<View> inputs = new ArrayList<>();
        collectInputViews(root, inputs);
        for (View input : inputs) {
            int id = input == null ? View.NO_ID : input.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(input)) continue;
            View original = findOriginalDebuggingView(id);
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
            View original = findOriginalDebuggingView(id);
            if (original != null && original != root) copyStateIgnoringGroupboxCollapse(original, root);
        }

        if (root instanceof AdapterView || root instanceof RecyclerView) return;

        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) syncViewsById(group.getChildAt(i));
    }

    private void copyStateIgnoringGroupboxCollapse(View src, View dst) {
        if (src == null || dst == null) return;
        try { dst.setVisibility(CollapsibleGroupboxController.visibilityIgnoringGroupboxCollapse(src)); } catch (Throwable ignored) {}
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

    private View findOriginalDebuggingView(int id) {
        try {
            if (binding == null || binding.tabDebugging == null || binding.tabDebugging.getRoot() == null) return null;
            return binding.tabDebugging.getRoot().findViewById(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private MaterialButton findTaggedButton(View root, String tag) {
        if (root == null || tag == null) return null;
        if (root instanceof MaterialButton && tag.equals(String.valueOf(root.getTag()))) return (MaterialButton) root;
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
        return TAG_ASSEMBLER.equals(tag) || TAG_MITM_PATCH.equals(tag) || TAG_SMALI_EDITOR.equals(tag);
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
