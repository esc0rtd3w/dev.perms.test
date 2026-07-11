package dev.perms.test.debugging;

import android.content.Context;
import android.text.TextUtils;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import dev.perms.test.databinding.TabDebuggingBinding;
import dev.perms.test.ui.DropdownUi;

/**
 * Handles the Debugging tab DEX-entry dropdown presentation and selection wiring.
 */
public final class DebuggingDexEntryDropdownController {
    public interface Host {
        int dpToPx(int dp);
        void configureSafeDropdownEndIcon(TextInputLayout layout, Runnable onClick);
        void configureTapOnlyDropdownField(android.widget.AutoCompleteTextView view, int touchSlop, int maxTapMs, Runnable onTap);
        void showDropdownAtLastSelection(android.widget.AutoCompleteTextView view, String lastText);
        String getLastDropdownText();
        void onDexEntrySelected(String entry, boolean forcePathRefresh, boolean fromUser);
        void appendOutput(String text);
    }

    private final Context context;
    private final Host host;
    private ArrayAdapter<String> adapter;

    public DebuggingDexEntryDropdownController(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    public void setup(TabDebuggingBinding binding,
                      ArrayList<String> allItems,
                      ArrayList<String> displayItems,
                      String selectedEntry,
                      String lastDropdownText) {
        try {
            if (binding == null || binding.ddSmaliDexEntry == null) return;
            ensureDefaults(allItems, displayItems);
            ensureAdapter(binding, displayItems);
            resize(binding, displayItems);
            DropdownUi.bindTapOnlyExposedDropdown(
                    context,
                    binding.tilSmaliDexEntry,
                    binding.ddSmaliDexEntry,
                    ViewConfiguration.get(context).getScaledTouchSlop(),
                    300,
                    () -> host.showDropdownAtLastSelection(binding.ddSmaliDexEntry, host.getLastDropdownText())
            );
            binding.ddSmaliDexEntry.setOnItemClickListener((parent, view, position, id) -> {
                String entry = null;
                try { entry = adapter == null ? null : adapter.getItem(position); } catch (Throwable ignored) {}
                if (!TextUtils.isEmpty(entry)) host.onDexEntrySelected(entry, false, true);
            });
            setText(binding, selectedEntry);
            resize(binding, displayItems);
        } catch (Throwable t) {
            host.appendOutput("[Debugging] dex-entry dropdown setup failed: " + t.getMessage() + "\n");
        }
    }

    public String applyList(TabDebuggingBinding binding,
                            ArrayList<String> allItems,
                            ArrayList<String> displayItems,
                            String preferredEntry) {
        try {
            if (binding == null) return "";
            displayItems.clear();
            displayItems.addAll(DebuggingDexEntries.displayItems(allItems));
            ensureAdapter(binding, displayItems);
            if (adapter != null) adapter.notifyDataSetChanged();

            String chosen = DebuggingDexEntries.choose(displayItems, preferredEntry);
            if (TextUtils.isEmpty(chosen)) setText(binding, "");
            resize(binding, displayItems);
            return TextUtils.isEmpty(chosen) ? "" : chosen;
        } catch (Throwable t) {
            host.appendOutput("[Debugging] DEX entry list update failed: " + t.getMessage() + "\n");
            return "";
        }
    }

    public void setSelectedText(TabDebuggingBinding binding, String entry) {
        setText(binding, entry);
    }

    public void resize(TabDebuggingBinding binding, List<String> displayItems) {
        try {
            if (binding == null || binding.tilSmaliDexEntry == null || binding.ddSmaliDexEntry == null) return;
            float maxTextWidth = binding.ddSmaliDexEntry.getPaint().measureText("classes.dex");
            if (displayItems != null) {
                for (String item : displayItems) {
                    if (TextUtils.isEmpty(item)) continue;
                    maxTextWidth = Math.max(maxTextWidth, binding.ddSmaliDexEntry.getPaint().measureText(item));
                }
            }

            int minimumWidth = host.dpToPx(180);
            int maximumWidth = Math.min(host.dpToPx(360),
                    Math.max(minimumWidth, context.getResources().getDisplayMetrics().widthPixels / 3));
            int desiredWidth = (int) Math.ceil(maxTextWidth) + host.dpToPx(64);
            desiredWidth = Math.max(minimumWidth, Math.min(maximumWidth, desiredWidth));

            ViewGroup.LayoutParams params = binding.tilSmaliDexEntry.getLayoutParams();
            if (params != null && params.width != desiredWidth) {
                params.width = desiredWidth;
                binding.tilSmaliDexEntry.setLayoutParams(params);
            }
            binding.ddSmaliDexEntry.setDropDownWidth(desiredWidth);
        } catch (Throwable ignored) {
        }
    }

    private void ensureDefaults(ArrayList<String> allItems, ArrayList<String> displayItems) {
        if (allItems != null && allItems.isEmpty()) allItems.add("classes.dex");
        if (displayItems != null && displayItems.isEmpty() && allItems != null) displayItems.addAll(allItems);
    }

    private void ensureAdapter(TabDebuggingBinding binding, ArrayList<String> displayItems) {
        if (binding == null || binding.ddSmaliDexEntry == null) return;
        if (adapter == null) {
            adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, displayItems);
            binding.ddSmaliDexEntry.setAdapter(adapter);
        }
    }

    private void setText(TabDebuggingBinding binding, String entry) {
        try {
            if (binding != null && binding.ddSmaliDexEntry != null) {
                binding.ddSmaliDexEntry.setText(entry == null ? "" : entry, false);
            }
        } catch (Throwable ignored) {
        }
    }
}
