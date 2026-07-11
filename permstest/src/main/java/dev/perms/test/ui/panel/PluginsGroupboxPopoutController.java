package dev.perms.test.ui.panel;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.editor.SourceSyntaxHighlighter;
import dev.perms.test.ui.CollapsibleGroupboxController;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.dialog.MovableDialogChrome;

/** Adds controlled cloned popout windows for the three top-level Plugins tab groupboxes. */
public final class PluginsGroupboxPopoutController {
    public interface Host {
        void debugOutput(String area, String message);
    }

    private static final Set<String> ACTIVE_KEYS = Collections.synchronizedSet(new HashSet<>());
    private static final String TAG_MANAGER = "plugins_popout_manager";
    private static final String TAG_INSTALLED = "plugins_popout_installed";
    private static final String TAG_EDITOR = "plugins_popout_editor";
    private static final int[] DYNAMIC_CONTAINER_IDS = new int[]{
            R.id.llPluginsBundledList,
            R.id.llPluginsList,
            R.id.llPluginEditorActionsList,
            R.id.llPluginEditorAssetsList,
            R.id.llPluginEditorUiControlsList,
            R.id.llPluginEditorUiNestedItemsList
    };
    private static final int[] NESTED_SCROLL_IDS = new int[]{
            R.id.scrollPluginsBundledList,
            R.id.scrollPluginsList,
            R.id.scrollPluginEditorActions,
            R.id.scrollPluginEditorAssets,
            R.id.scrollPluginEditorUiControls,
            R.id.scrollPluginEditorUiNestedItems,
            R.id.edtPluginEditorJson,
            R.id.edtPluginEditorUiJson,
            R.id.edtPluginEditorUiActionOptionsJson,
            R.id.edtPluginEditorUiNestedActionOptionsJson
    };

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final Host host;

    public PluginsGroupboxPopoutController(AppCompatActivity activity,
                                            ActivityMainBinding binding,
                                            Host host) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
    }

    public void bind() {
        try {
            if (binding == null || binding.tabPlugins == null) return;
            View pluginsRoot = binding.tabPlugins.getRoot();
            bindButton(findTaggedButton(pluginsRoot, TAG_MANAGER),
                    v -> openPluginsCardPopout("plugins:manager", "Plugin Manager", TAG_MANAGER));
            bindButton(findTaggedButton(pluginsRoot, TAG_INSTALLED),
                    v -> openPluginsCardPopout("plugins:installed", "Installed Plugins", TAG_INSTALLED));
            bindButton(findTaggedButton(pluginsRoot, TAG_EDITOR),
                    v -> openPluginsCardPopout("plugins:editor", "Plugin Editor", TAG_EDITOR));
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

    private void openPluginsCardPopout(String key, String title, String tag) {
        if (!ensurePanelsEnabled()) return;
        if (!reserve(key)) return;
        try {
            View card = inflateDetachedPluginsCard(tag);
            if (card == null) throw new IllegalStateException("card clone unavailable");
            hideNestedPopoutButtons(card);

            final boolean[] syncing = new boolean[]{false};
            final PanelDynamicContentMirror dynamicMirror = new PanelDynamicContentMirror(activity);
            bindCloneInputs(card, syncing);
            bindCloneButtons(card, syncing, dynamicMirror);
            bindCloneDropdowns(card, syncing);
            bindNestedScrolls(card);
            installJsonHighlighting(card.findViewById(R.id.edtPluginEditorJson));
            installJsonHighlighting(card.findViewById(R.id.edtPluginEditorUiJson));
            installJsonHighlighting(card.findViewById(R.id.edtPluginEditorUiActionOptionsJson));
            installJsonHighlighting(card.findViewById(R.id.edtPluginEditorUiNestedActionOptionsJson));
            syncCloneStates(card, syncing);
            syncCloneDropdownAdapters(card);
            syncDynamicContent(card, dynamicMirror);

            final Runnable syncRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        syncCloneStates(card, syncing);
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

    private View inflateDetachedPluginsCard(String tag) {
        try {
            View root = LayoutInflater.from(activity).inflate(R.layout.tab_plugins, null, false);
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

    private TextInputLayout findAncestorTextInputLayout(View view) {
        View current = view;
        while (current != null) {
            if (current instanceof TextInputLayout) return (TextInputLayout) current;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private void hideNestedPopoutButtons(View root) {
        hideTaggedButton(root, TAG_MANAGER);
        hideTaggedButton(root, TAG_INSTALLED);
        hideTaggedButton(root, TAG_EDITOR);
    }

    private void bindCloneButtons(View root, final boolean[] syncing, PanelDynamicContentMirror dynamicMirror) {
        List<MaterialButton> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (MaterialButton cloneButton : buttons) {
            if (cloneButton == null) continue;
            int id = cloneButton.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(cloneButton)) continue;
            final View original = findOriginalPluginsView(id);
            if (original == null) continue;
            cloneButton.setOnClickListener(v -> {
                try {
                    copyCloneInputsToOriginal(root);
                    original.performClick();
                    v.postDelayed(() -> {
                        syncCloneStates(root, syncing);
                        syncCloneDropdownAdapters(root);
                        syncDynamicContent(root, dynamicMirror);
                    }, 300L);
                } catch (Throwable ignored) {
                }
            });
            cloneButton.setOnLongClickListener(v -> {
                try {
                    copyCloneInputsToOriginal(root);
                    return original.performLongClick();
                } catch (Throwable ignored) {
                    return false;
                }
            });
        }
    }

    private void bindCloneInputs(View root, final boolean[] syncing) {
        List<View> inputs = new ArrayList<>();
        collectInputViews(root, inputs);
        for (View input : inputs) {
            if (input == null) continue;
            int id = input.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(input)) continue;
            final View original = findOriginalPluginsView(id);
            if (original == null) continue;
            if (input instanceof CompoundButton && original instanceof CompoundButton) {
                ((CompoundButton) input).setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (syncing[0]) return;
                    try {
                        ((CompoundButton) original).setChecked(isChecked);
                    } catch (Throwable ignored) {
                    }
                });
            } else if (input instanceof EditText && !(input instanceof AutoCompleteTextView)
                    && original instanceof TextView) {
                EditText cloneText = (EditText) input;
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
            if (clone == null || clone.getId() == View.NO_ID) continue;
            View originalView = findOriginalPluginsView(clone.getId());
            if (!(originalView instanceof AutoCompleteTextView)) continue;
            AutoCompleteTextView original = (AutoCompleteTextView) originalView;
            updateDropdownCloneAdapter(clone, original, true);
            DropdownUi.bindExposedDropdown(activity, findAncestorTextInputLayout(clone), clone, () -> {
                try {
                    updateDropdownCloneAdapter(clone, original, false);
                    DropdownUi.showDropdown(clone);
                } catch (Throwable ignored) {
                }
            });
            clone.setOnLongClickListener(v -> {
                try {
                    copyCloneInputsToOriginal(root);
                    return original.performLongClick();
                } catch (Throwable ignored) {
                    return false;
                }
            });
            clone.setOnItemClickListener((parent, view, position, itemId) -> {
                if (syncing[0]) return;
                try {
                    Object item = parent == null ? null : parent.getItemAtPosition(position);
                    String text = item == null ? "" : String.valueOf(item);
                    if (text.startsWith("Custom ") && text.endsWith("...")) {
                        clone.setText(original.getText(), false);
                        original.performLongClick();
                        return;
                    }
                    clone.setText(text, false);
                    original.setText(text, false);
                    copyCloneInputsToOriginal(root);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    private void syncCloneDropdownAdapters(View root) {
        List<AutoCompleteTextView> dropdowns = new ArrayList<>();
        collectAutoCompleteTextViews(root, dropdowns);
        for (AutoCompleteTextView clone : dropdowns) {
            if (clone == null || clone.getId() == View.NO_ID) continue;
            View originalView = findOriginalPluginsView(clone.getId());
            if (originalView instanceof AutoCompleteTextView) {
                updateDropdownCloneAdapter(clone, (AutoCompleteTextView) originalView, false);
            }
        }
    }

    private void updateDropdownCloneAdapter(AutoCompleteTextView clone,
                                            AutoCompleteTextView original,
                                            boolean force) {
        if (clone == null || original == null) return;
        try {
            ListAdapter adapter = original.getAdapter();
            if (adapter == null) return;
            int count = adapter.getCount();
            List<String> values = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Object item = adapter.getItem(i);
                values.add(item == null ? "" : String.valueOf(item));
            }
            if (!force && dropdownAdapterMatches(clone.getAdapter(), values)) return;
            clone.setAdapter(new ArrayAdapter<>(activity,
                    android.R.layout.simple_dropdown_item_1line,
                    values));
        } catch (Throwable ignored) {
        }
    }

    private boolean dropdownAdapterMatches(ListAdapter adapter, List<String> values) {
        if (adapter == null || values == null || adapter.getCount() != values.size()) return false;
        try {
            for (int i = 0; i < values.size(); i++) {
                Object item = adapter.getItem(i);
                if (!TextUtils.equals(item == null ? "" : String.valueOf(item), values.get(i))) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void installJsonHighlighting(View view) {
        if (!(view instanceof EditText)) return;
        EditText editor = (EditText) view;
        try {
            editor.setVerticalScrollBarEnabled(true);
            editor.setHorizontalScrollBarEnabled(true);
            editor.setHorizontallyScrolling(true);
            ViewCompat.setNestedScrollingEnabled(editor, true);
        } catch (Throwable ignored) {}
        final float[] lastTouchY = new float[1];
        editor.setOnTouchListener((v, event) -> {
            try {
                if (event == null) return false;
                ViewParent parent = v.getParent();
                int action = event.getActionMasked();
                if (parent != null) {
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        parent.requestDisallowInterceptTouchEvent(false);
                    } else if (action == MotionEvent.ACTION_DOWN) {
                        lastTouchY[0] = event.getY();
                        parent.requestDisallowInterceptTouchEvent(true);
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        float y = event.getY();
                        float dy = y - lastTouchY[0];
                        lastTouchY[0] = y;
                        int direction = dy > 0f ? -1 : 1;
                        parent.requestDisallowInterceptTouchEvent(v.canScrollVertically(direction));
                    }
                }
            } catch (Throwable ignored) {}
            return false;
        });
        final boolean[] applying = new boolean[1];
        final Runnable highlight = () -> {
            if (applying[0]) return;
            try {
                applying[0] = true;
                SourceSyntaxHighlighter.applyJson(editor);
            } catch (Throwable ignored) {
            } finally {
                applying[0] = false;
            }
        };
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (applying[0]) return;
                editor.removeCallbacks(highlight);
                editor.postDelayed(highlight, 180L);
            }
        });
        editor.post(highlight);
    }

    private void syncDynamicContent(View root, PanelDynamicContentMirror mirror) {
        if (root == null || mirror == null) return;
        for (int id : DYNAMIC_CONTAINER_IDS) {
            View cloneView = root.findViewById(id);
            View originalView = findOriginalPluginsView(id);
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
        View bundledClone = root.findViewById(R.id.llPluginsBundledList);
        View installedClone = root.findViewById(R.id.llPluginsList);
        View actionsClone = root.findViewById(R.id.llPluginEditorActionsList);
        View uiControlsClone = root.findViewById(R.id.llPluginEditorUiControlsList);
        View uiNestedClone = root.findViewById(R.id.llPluginEditorUiNestedItemsList);
        if (bundledClone == null && installedClone == null && actionsClone == null && uiControlsClone == null) return;
        debug(area, "dynamic rows source/clone: bundled="
                + childCount(findOriginalPluginsView(R.id.llPluginsBundledList)) + "/" + childCount(bundledClone)
                + " installed=" + childCount(findOriginalPluginsView(R.id.llPluginsList)) + "/" + childCount(installedClone)
                + " editorActions=" + childCount(findOriginalPluginsView(R.id.llPluginEditorActionsList)) + "/" + childCount(actionsClone)
                + " editorUiControls=" + childCount(findOriginalPluginsView(R.id.llPluginEditorUiControlsList)) + "/" + childCount(uiControlsClone)
                + " editorUiNested=" + childCount(findOriginalPluginsView(R.id.llPluginEditorUiNestedItemsList)) + "/" + childCount(uiNestedClone));
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

    private void bindNestedScrolls(View root) {
        if (root == null) return;
        for (int id : NESTED_SCROLL_IDS) {
            allowNestedScrollable(root.findViewById(id));
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
        if (!(view instanceof ViewGroup) || isDynamicMirrorContainer(view)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collectButtons(group.getChildAt(i), out);
    }

    private void collectInputViews(View view, List<View> out) {
        if (view == null || out == null) return;
        if (view instanceof CompoundButton || view instanceof EditText) out.add(view);
        if (!(view instanceof ViewGroup) || isDynamicMirrorContainer(view)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collectInputViews(group.getChildAt(i), out);
    }

    private void collectAutoCompleteTextViews(View view, List<AutoCompleteTextView> out) {
        if (view == null || out == null) return;
        if (view instanceof AutoCompleteTextView) out.add((AutoCompleteTextView) view);
        if (!(view instanceof ViewGroup) || isDynamicMirrorContainer(view)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collectAutoCompleteTextViews(group.getChildAt(i), out);
    }

    private void copyCloneInputsToOriginal(View root) {
        if (root == null) return;
        List<View> inputs = new ArrayList<>();
        collectInputViews(root, inputs);
        for (View input : inputs) {
            int id = input == null ? View.NO_ID : input.getId();
            if (id == View.NO_ID || isTaggedPopoutButton(input)) continue;
            View original = findOriginalPluginsView(id);
            if (original == null || original == input) continue;
            try {
                if (input instanceof CompoundButton && original instanceof CompoundButton) {
                    ((CompoundButton) original).setChecked(((CompoundButton) input).isChecked());
                } else if (input instanceof TextView && original instanceof TextView) {
                    copyText((TextView) input, (TextView) original);
                }
            } catch (Throwable ignored) {
            }
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
            View original = findOriginalPluginsView(id);
            if (original != null && original != root) copyStateIgnoringGroupboxCollapse(original, root);
        }
        if (!(root instanceof ViewGroup) || isDynamicMirrorContainer(root)) return;
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

    private View findOriginalPluginsView(int id) {
        try {
            if (binding == null || binding.tabPlugins == null || binding.tabPlugins.getRoot() == null) return null;
            return binding.tabPlugins.getRoot().findViewById(id);
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
        return TAG_MANAGER.equals(tag) || TAG_INSTALLED.equals(tag) || TAG_EDITOR.equals(tag);
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
