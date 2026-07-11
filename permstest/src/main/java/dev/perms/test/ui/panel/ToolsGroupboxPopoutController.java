package dev.perms.test.ui.panel;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
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

/** Adds controlled cloned popout windows for selected Tools tab groupboxes. */
public final class ToolsGroupboxPopoutController {
    public interface Host {
        void debugOutput(String area, String message);
    }

    private static final Set<String> ACTIVE_KEYS = Collections.synchronizedSet(new HashSet<>());
    private static final String TAG_ROOT_CHECKER = "tools_popout_root_checker";
    private static final String TAG_ACTIVITY_MANAGER = "tools_popout_activity_manager";
    private static final String TAG_INTENT_TESTER = "tools_popout_intent_tester";
    private static final String TAG_PERMISSIONS_TESTER = "tools_popout_permissions_tester";
    private static final String TAG_HEX_EDITOR = "tools_popout_hex_editor";
    private static final String TAG_TEXT_EDITOR = "tools_popout_text_editor";
    private static final String TAG_SAVE_DATA_EDITOR = "tools_popout_save_data_editor";
    private static final int[] DYNAMIC_CONTAINER_IDS = new int[]{
            R.id.llToolsActivityResults,
            R.id.llToolsReceiverTemplates,
            R.id.llToolsPermissionsTesterList
    };

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final Host host;

    public ToolsGroupboxPopoutController(AppCompatActivity activity,
                                         ActivityMainBinding binding,
                                         Host host) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
    }

    public void bind() {
        try {
            if (binding == null || binding.tabTools == null) return;
            View toolsRoot = binding.tabTools.getRoot();
            bindButton(findTaggedButton(toolsRoot, TAG_ROOT_CHECKER),
                    v -> openToolsCardPopout("tools:root_checker", "Root Checker", TAG_ROOT_CHECKER));
            bindButton(findTaggedButton(toolsRoot, TAG_ACTIVITY_MANAGER),
                    v -> openToolsCardPopout("tools:activity_manager", "Activity Manager", TAG_ACTIVITY_MANAGER));
            bindButton(findTaggedButton(toolsRoot, TAG_INTENT_TESTER),
                    v -> openToolsCardPopout("tools:intent_tester", "Intent Tester", TAG_INTENT_TESTER));
            bindButton(findTaggedButton(toolsRoot, TAG_PERMISSIONS_TESTER),
                    v -> openToolsCardPopout("tools:permissions_tester", "Permissions Tester", TAG_PERMISSIONS_TESTER));
            bindButton(findTaggedButton(toolsRoot, TAG_HEX_EDITOR),
                    v -> openToolsCardPopout("tools:hex_editor", "Hex Editor", TAG_HEX_EDITOR));
            bindButton(findTaggedButton(toolsRoot, TAG_TEXT_EDITOR),
                    v -> openToolsCardPopout("tools:text_editor", "Text Editor", TAG_TEXT_EDITOR));
            bindButton(findTaggedButton(toolsRoot, TAG_SAVE_DATA_EDITOR),
                    v -> openToolsCardPopout("tools:save_data_editor", "Save Data Editor", TAG_SAVE_DATA_EDITOR));
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

    private void openToolsCardPopout(String key, String title, String tag) {
        if (!ensurePanelsEnabled()) return;
        if (!reserve(key)) return;
        try {
            View card = inflateDetachedToolsCard(tag);
            if (card == null) throw new IllegalStateException("card clone unavailable");
            hideNestedPopoutButtons(card);

            final boolean[] syncing = new boolean[]{false};
            final PanelDynamicContentMirror dynamicMirror = new PanelDynamicContentMirror(activity);
            bindCloneInputs(card, syncing);
            bindCloneButtons(card, syncing, dynamicMirror);
            bindCloneAdapterViews(card);
            bindCloneDropdowns(card, syncing);
            syncCloneStates(card, syncing);
            syncCloneAdapterViews(card);
            syncCloneDropdownAdapters(card);
            syncDynamicContent(card, dynamicMirror);

            final Runnable syncRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        syncCloneStates(card, syncing);
                        syncCloneAdapterViews(card);
                        syncCloneDropdownAdapters(card);
                        syncDynamicContent(card, dynamicMirror);
                        card.postDelayed(this, 1000L);
                    } catch (Throwable ignored) {
                    }
                }
            };
            card.postDelayed(syncRunnable, 1000L);
            showMovableClone(key, title, card, () -> {
                try { card.removeCallbacks(syncRunnable); } catch (Throwable ignored) {}
                detachCloneAdapterViews(card);
                clearDynamicContent(card, dynamicMirror);
            });
            debug(key, "opened cloned groupbox: " + title);
            appendDynamicContentDebug(key, card);
        } catch (Throwable t) {
            release(key);
            debug(key, "open failed: " + safeMessage(t));
            Toast.makeText(activity, title + " popout failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
        }
    }

    private View inflateDetachedToolsCard(String tag) {
        try {
            View root = LayoutInflater.from(activity).inflate(R.layout.tab_tools, null, false);
            MaterialButton button = findTaggedButton(root, tag);
            if (button == null) return null;
            MaterialCardView card = findAncestorCard(button);
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

    private MaterialCardView findAncestorCard(View view) {
        View current = view;
        while (current != null) {
            if (current instanceof MaterialCardView) return (MaterialCardView) current;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private void hideNestedPopoutButtons(View root) {
        hideTaggedButton(root, TAG_ROOT_CHECKER);
        hideTaggedButton(root, TAG_ACTIVITY_MANAGER);
        hideTaggedButton(root, TAG_INTENT_TESTER);
        hideTaggedButton(root, TAG_PERMISSIONS_TESTER);
        hideTaggedButton(root, TAG_HEX_EDITOR);
        hideTaggedButton(root, TAG_TEXT_EDITOR);
        hideTaggedButton(root, TAG_SAVE_DATA_EDITOR);
    }

    private void bindCloneButtons(View root, final boolean[] syncing, PanelDynamicContentMirror dynamicMirror) {
        List<MaterialButton> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (MaterialButton cloneButton : buttons) {
            if (cloneButton == null) continue;
            int id = cloneButton.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(cloneButton)) continue;
            final View original = findOriginalToolsView(id);
            if (original == null) continue;
            cloneButton.setOnClickListener(v -> {
                try {
                    copyCloneInputsToOriginal(root, syncing);
                    original.performClick();
                    v.postDelayed(() -> {
                        syncCloneStates(root, syncing);
                        syncCloneAdapterViews(root);
                        syncCloneDropdownAdapters(root);
                        syncDynamicContent(root, dynamicMirror);
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
            final View original = findOriginalToolsView(id);
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
        List<AutoCompleteTextView> dropdowns = new ArrayList<>();
        collectAutoCompleteTextViews(root, dropdowns);
        for (AutoCompleteTextView clone : dropdowns) {
            if (clone == null) continue;
            int id = clone.getId();
            if (id == View.NO_ID) continue;
            View originalView = findOriginalToolsView(id);
            if (!(originalView instanceof AutoCompleteTextView)) continue;
            AutoCompleteTextView original = (AutoCompleteTextView) originalView;
            updateDropdownCloneAdapter(clone, original, true);
            clone.setOnClickListener(v -> {
                try {
                    updateDropdownCloneAdapter(clone, original, false);
                    clone.showDropDown();
                } catch (Throwable ignored) {
                }
            });
            clone.setOnItemClickListener((parent, view, position, itemId) -> {
                if (syncing[0]) return;
                try {
                    Object item = parent == null ? null : parent.getItemAtPosition(position);
                    String text = item == null ? "" : String.valueOf(item);
                    clone.setText(text, false);
                    original.setText(text, false);
                    copyCloneInputsToOriginal(root, syncing);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    private void syncCloneDropdownAdapters(View root) {
        List<AutoCompleteTextView> dropdowns = new ArrayList<>();
        collectAutoCompleteTextViews(root, dropdowns);
        for (AutoCompleteTextView clone : dropdowns) {
            if (clone == null) continue;
            int id = clone.getId();
            if (id == View.NO_ID) continue;
            View originalView = findOriginalToolsView(id);
            if (originalView instanceof AutoCompleteTextView) {
                updateDropdownCloneAdapter(clone, (AutoCompleteTextView) originalView, false);
            }
        }
    }

    private void updateDropdownCloneAdapter(AutoCompleteTextView clone, AutoCompleteTextView original, boolean force) {
        if (clone == null || original == null) return;
        try {
            ListAdapter adapter = original.getAdapter();
            if (adapter == null) return;
            int count = adapter.getCount();
            if (!force && clone.getAdapter() != null && clone.getAdapter().getCount() == count) return;
            List<String> values = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Object item = adapter.getItem(i);
                values.add(item == null ? "" : String.valueOf(item));
            }
            ArrayAdapter<String> snapshot = new ArrayAdapter<>(activity,
                    android.R.layout.simple_dropdown_item_1line,
                    values);
            clone.setAdapter(snapshot);
        } catch (Throwable ignored) {
        }
    }

    private void syncDynamicContent(View root, PanelDynamicContentMirror mirror) {
        if (root == null || mirror == null) return;
        for (int id : DYNAMIC_CONTAINER_IDS) {
            View cloneView = root.findViewById(id);
            View originalView = findOriginalToolsView(id);
            if (!(cloneView instanceof ViewGroup) || !(originalView instanceof ViewGroup)) continue;
            try {
                mirror.sync((ViewGroup) originalView, (ViewGroup) cloneView);
                cloneView.setVisibility(CollapsibleGroupboxController.visibilityIgnoringGroupboxCollapse(originalView));
            } catch (Throwable ignored) {
            }
        }
    }

    private void clearDynamicContent(View root, PanelDynamicContentMirror mirror) {
        if (root == null || mirror == null) return;
        for (int id : DYNAMIC_CONTAINER_IDS) {
            View cloneView = root.findViewById(id);
            if (cloneView instanceof ViewGroup) {
                try { mirror.clear((ViewGroup) cloneView); } catch (Throwable ignored) {}
            }
        }
    }

    private void appendDynamicContentDebug(String area, View root) {
        if (root == null) return;
        View activityClone = root.findViewById(R.id.llToolsActivityResults);
        View receiverClone = root.findViewById(R.id.llToolsReceiverTemplates);
        View permissionClone = root.findViewById(R.id.llToolsPermissionsTesterList);
        if (activityClone == null && receiverClone == null && permissionClone == null) return;
        debug(area, "dynamic rows source/clone: activity="
                + childCount(findOriginalToolsView(R.id.llToolsActivityResults)) + "/" + childCount(activityClone)
                + " receiver=" + childCount(findOriginalToolsView(R.id.llToolsReceiverTemplates)) + "/" + childCount(receiverClone)
                + " permissions=" + childCount(findOriginalToolsView(R.id.llToolsPermissionsTesterList)) + "/" + childCount(permissionClone));
    }

    private int childCount(View view) {
        return view instanceof ViewGroup ? ((ViewGroup) view).getChildCount() : 0;
    }

    private boolean isDynamicMirrorContainer(View view) {
        if (view == null) return false;
        int id = view.getId();
        for (int dynamicId : DYNAMIC_CONTAINER_IDS) {
            if (id == dynamicId) return true;
        }
        return false;
    }

    private void bindCloneAdapterViews(View root) {
        syncListViews(root);
        syncRecyclerViews(root);
    }

    private void syncCloneAdapterViews(View root) {
        bindCloneAdapterViews(root);
    }

    private void syncListViews(View root) {
        List<ListView> lists = new ArrayList<>();
        collectListViews(root, lists);
        for (ListView clone : lists) {
            if (clone == null) continue;
            int id = clone.getId();
            if (id == View.NO_ID) continue;
            View originalView = findOriginalToolsView(id);
            if (!(originalView instanceof ListView)) continue;
            ListView original = (ListView) originalView;
            try {
                ListAdapter adapter = original.getAdapter();
                if (clone.getAdapter() != adapter) clone.setAdapter(adapter);
            } catch (Throwable ignored) {
            }
            try { clone.setChoiceMode(original.getChoiceMode()); } catch (Throwable ignored) {}
            try { clone.setFastScrollEnabled(original.isFastScrollEnabled()); } catch (Throwable ignored) {}
            allowNestedScrollable(clone);
            clone.setOnItemClickListener((parent, view, position, itemId) -> {
                try {
                    copyCloneInputsToOriginal(root, new boolean[]{false});
                    original.performItemClick(null, position, itemId);
                    clone.postDelayed(() -> syncCloneStates(root, new boolean[]{false}), 250L);
                } catch (Throwable ignored) {
                }
            });
            clone.setOnItemLongClickListener((parent, view, position, itemId) -> {
                try {
                    return original.performLongClick();
                } catch (Throwable ignored) {
                    return false;
                }
            });
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void syncRecyclerViews(View root) {
        List<RecyclerView> views = new ArrayList<>();
        collectRecyclerViews(root, views);
        for (RecyclerView clone : views) {
            if (clone == null) continue;
            int id = clone.getId();
            if (id == View.NO_ID) continue;
            View originalView = findOriginalToolsView(id);
            if (!(originalView instanceof RecyclerView)) continue;
            RecyclerView original = (RecyclerView) originalView;
            try {
                if (clone.getLayoutManager() == null) {
                    clone.setLayoutManager(new LinearLayoutManager(activity));
                }
                RecyclerView.Adapter adapter = original.getAdapter();
                if (adapter != null && clone.getAdapter() != adapter) clone.setAdapter(adapter);
            } catch (Throwable ignored) {
            }
            allowNestedScrollable(clone);
        }
    }

    private void detachCloneAdapterViews(View root) {
        try {
            List<ListView> lists = new ArrayList<>();
            collectListViews(root, lists);
            for (ListView list : lists) {
                try { list.setAdapter((ListAdapter) null); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
        try {
            List<RecyclerView> views = new ArrayList<>();
            collectRecyclerViews(root, views);
            for (RecyclerView rv : views) {
                try { rv.setAdapter(null); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

    private void allowNestedScrollable(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            try {
                ViewParent parent = v.getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                if (event != null && (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
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
        if (isDynamicMirrorContainer(view)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectButtons(group.getChildAt(i), out);
        }
    }

    private void collectInputViews(View view, List<View> out) {
        if (view == null || out == null) return;
        if (view instanceof CompoundButton || view instanceof EditText) out.add(view);
        if (!(view instanceof ViewGroup)) return;
        if (view instanceof AdapterView || view instanceof RecyclerView || isDynamicMirrorContainer(view)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectInputViews(group.getChildAt(i), out);
        }
    }

    private void collectAutoCompleteTextViews(View view, List<AutoCompleteTextView> out) {
        if (view == null || out == null) return;
        if (view instanceof AutoCompleteTextView) out.add((AutoCompleteTextView) view);
        if (!(view instanceof ViewGroup)) return;
        if (view instanceof AdapterView || view instanceof RecyclerView || isDynamicMirrorContainer(view)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectAutoCompleteTextViews(group.getChildAt(i), out);
        }
    }

    private void collectListViews(View view, List<ListView> out) {
        if (view == null || out == null) return;
        if (view instanceof ListView) out.add((ListView) view);
        if (!(view instanceof ViewGroup)) return;
        if (view instanceof AdapterView || view instanceof RecyclerView || isDynamicMirrorContainer(view)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectListViews(group.getChildAt(i), out);
        }
    }

    private void collectRecyclerViews(View view, List<RecyclerView> out) {
        if (view == null || out == null) return;
        if (view instanceof RecyclerView) out.add((RecyclerView) view);
        if (!(view instanceof ViewGroup)) return;
        if (view instanceof AdapterView || view instanceof RecyclerView || isDynamicMirrorContainer(view)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectRecyclerViews(group.getChildAt(i), out);
        }
    }

    private void copyCloneInputsToOriginal(View root, final boolean[] syncing) {
        if (root == null) return;
        List<View> inputs = new ArrayList<>();
        collectInputViews(root, inputs);
        for (View input : inputs) {
            int id = input == null ? View.NO_ID : input.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(input)) continue;
            View original = findOriginalToolsView(id);
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
            View original = findOriginalToolsView(id);
            if (original != null && original != root) copyStateIgnoringGroupboxCollapse(original, root);
        }
        if (!(root instanceof ViewGroup)) return;
        if (root instanceof AdapterView || root instanceof RecyclerView || isDynamicMirrorContainer(root)) return;
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

    private View findOriginalToolsView(int id) {
        try {
            if (binding == null || binding.tabTools == null || binding.tabTools.getRoot() == null) return null;
            return binding.tabTools.getRoot().findViewById(id);
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
        return TAG_ROOT_CHECKER.equals(tag)
                || TAG_ACTIVITY_MANAGER.equals(tag)
                || TAG_INTENT_TESTER.equals(tag)
                || TAG_PERMISSIONS_TESTER.equals(tag)
                || TAG_HEX_EDITOR.equals(tag)
                || TAG_TEXT_EDITOR.equals(tag)
                || TAG_SAVE_DATA_EDITOR.equals(tag);
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
