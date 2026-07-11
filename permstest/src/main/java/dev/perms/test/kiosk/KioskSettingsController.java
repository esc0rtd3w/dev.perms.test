package dev.perms.test.kiosk;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.DropdownUi;

/** Settings-tab controller for disabled-by-default Kiosk Mode options and whitelist editing. */
public final class KioskSettingsController {
    public interface Host {
        void appendOutput(String text);
        boolean isDebugOutputEnabled();
        void debugOutput(String area, String message);
    }

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final KioskSettingsStore store;
    private final Host host;
    private final ArrayList<KioskLaunchableApp> launchableApps = new ArrayList<>();

    public KioskSettingsController(AppCompatActivity activity, ActivityMainBinding binding, Host host) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
        this.store = new KioskSettingsStore(activity);
    }

    public void setup() {
        if (binding == null || binding.tabSettings == null || binding.tabSettings.cardKioskSettings == null) return;
        bindStoredSettings();
        bindButtons();
        refreshLaunchableApps();
        renderAllowedList();
        updatePatternSummary();
    }

    public void syncMainKioskCheckbox(boolean enabled) {
        try {
            if (binding != null && binding.tabMain != null && binding.tabMain.chkKioskMode != null) {
                binding.tabMain.chkKioskMode.setChecked(enabled);
            }
        } catch (Throwable ignored) {
        }
    }

    private void bindStoredSettings() {
        setText(binding.tabSettings.edtKioskPassword, store.password());
        setText(binding.tabSettings.edtKioskIconSize, String.valueOf(store.iconSizeDp()));
        setText(binding.tabSettings.edtKioskTimerMinutes, String.valueOf(store.timerRefreshMinutes()));
        setChecked(binding.tabSettings.chkKioskTimerRefresh, store.timerRefreshEnabled());
        setChecked(binding.tabSettings.chkKioskHideStatusBar, store.hideStatusBar());
        setChecked(binding.tabSettings.chkKioskLockTaskMode, store.lockTaskEnabled());
        setChecked(binding.tabSettings.chkKioskHardwareButtonBypass, store.hardwareButtonBypassEnabled());
        setChecked(binding.tabSettings.chkKioskAutoSizeIcons, store.autoSizeIcons());
        setChecked(binding.tabSettings.chkKioskShowLabels, store.showLabels());
        updateIconSizeFieldState();
    }

    private void bindButtons() {
        if (binding.tabSettings.btnKioskSaveSettings != null) {
            binding.tabSettings.btnKioskSaveSettings.setOnClickListener(v -> saveSimpleSettings(true));
        }
        if (binding.tabSettings.btnKioskRefreshApps != null) {
            binding.tabSettings.btnKioskRefreshApps.setOnClickListener(v -> refreshLaunchableApps());
        }
        if (binding.tabSettings.btnKioskAddAllowedApp != null) {
            binding.tabSettings.btnKioskAddAllowedApp.setOnClickListener(v -> addSelectedApp());
        }
        if (binding.tabSettings.btnKioskAddShortcut != null) {
            binding.tabSettings.btnKioskAddShortcut.setOnClickListener(v -> showAddShortcutDialog());
        }
        if (binding.tabSettings.btnKioskEditPattern != null) {
            binding.tabSettings.btnKioskEditPattern.setOnClickListener(v -> showPatternDialog());
        }
        if (binding.tabSettings.chkKioskAutoSizeIcons != null) {
            binding.tabSettings.chkKioskAutoSizeIcons.setOnCheckedChangeListener((buttonView, isChecked) -> updateIconSizeFieldState());
        }
    }

    private void updateIconSizeFieldState() {
        try {
            if (binding.tabSettings.edtKioskIconSize != null) {
                binding.tabSettings.edtKioskIconSize.setEnabled(!isChecked(binding.tabSettings.chkKioskAutoSizeIcons));
            }
        } catch (Throwable ignored) {
        }
    }

    private void saveSimpleSettings(boolean toast) {
        store.setPassword(text(binding.tabSettings.edtKioskPassword));
        store.setIconSizeDp(parseInt(text(binding.tabSettings.edtKioskIconSize), KioskPrefs.DEFAULT_ICON_SIZE_DP));
        store.setTimerRefreshMinutes(parseInt(text(binding.tabSettings.edtKioskTimerMinutes), KioskPrefs.DEFAULT_TIMER_REFRESH_MINUTES));
        store.setTimerRefreshEnabled(isChecked(binding.tabSettings.chkKioskTimerRefresh));
        store.setHideStatusBar(isChecked(binding.tabSettings.chkKioskHideStatusBar));
        store.setLockTaskEnabled(isChecked(binding.tabSettings.chkKioskLockTaskMode));
        store.setHardwareButtonBypassEnabled(isChecked(binding.tabSettings.chkKioskHardwareButtonBypass));
        store.setAutoSizeIcons(isChecked(binding.tabSettings.chkKioskAutoSizeIcons));
        store.setShowLabels(isChecked(binding.tabSettings.chkKioskShowLabels));
        setText(binding.tabSettings.edtKioskIconSize, String.valueOf(store.iconSizeDp()));
        setText(binding.tabSettings.edtKioskTimerMinutes, String.valueOf(store.timerRefreshMinutes()));
        updateIconSizeFieldState();
        debugOutput("settings", "saved; iconDp=" + store.iconSizeDp()
                + ", timerMinutes=" + store.timerRefreshMinutes()
                + ", timerRefresh=" + store.timerRefreshEnabled()
                + ", immersive=" + store.hideStatusBar()
                + ", lockTask=" + store.lockTaskEnabled()
                + ", hardwareBypass=" + store.hardwareButtonBypassEnabled()
                + ", autoSizeIcons=" + store.autoSizeIcons()
                + ", showLabels=" + store.showLabels());
        if (toast) Toast.makeText(activity, "Kiosk settings saved", Toast.LENGTH_SHORT).show();
    }

    private void refreshLaunchableApps() {
        debugOutput("settings", "refreshing launchable app choices");
        new Thread(() -> {
            final ArrayList<KioskLaunchableApp> loaded = new ArrayList<>(KioskAppRepository.loadLaunchableApps(activity));
            activity.runOnUiThread(() -> applyLaunchableApps(loaded));
        }, "PermsTestKioskApps").start();
    }

    private void applyLaunchableApps(List<KioskLaunchableApp> loaded) {
        launchableApps.clear();
        if (loaded != null) launchableApps.addAll(loaded);
        ArrayList<String> labels = new ArrayList<>();
        for (KioskLaunchableApp app : launchableApps) labels.add(app.toString());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, labels);
        binding.tabSettings.ddKioskAllowedApp.setAdapter(adapter);
        DropdownUi.bindTapOnlyExposedDropdown(activity,
                binding.tabSettings.tilKioskAllowedApp,
                binding.tabSettings.ddKioskAllowedApp,
                android.view.ViewConfiguration.get(activity).getScaledTouchSlop(),
                300,
                null);
        if (!labels.isEmpty() && TextUtils.isEmpty(text(binding.tabSettings.ddKioskAllowedApp))) {
            binding.tabSettings.ddKioskAllowedApp.setText(labels.get(0), false);
        }
        debugOutput("settings", "launchable app choices refreshed: " + labels.size());
    }

    private void addSelectedApp() {
        String selected = text(binding.tabSettings.ddKioskAllowedApp);
        KioskLaunchableApp app = null;
        for (KioskLaunchableApp candidate : launchableApps) {
            if (candidate == null) continue;
            if (candidate.toString().equals(selected) || candidate.packageName.equals(selected)) {
                app = candidate;
                break;
            }
        }
        if (app == null) {
            Toast.makeText(activity, "Select an app first", Toast.LENGTH_SHORT).show();
            return;
        }
        store.addOrUpdate(new KioskAllowedItem(KioskAllowedItem.TYPE_APP, app.packageName, app.label, true));
        debugOutput("settings", "allowed app added: " + app.label + " / " + app.packageName);
        renderAllowedList();
        Toast.makeText(activity, "Allowed: " + app.label, Toast.LENGTH_SHORT).show();
    }

    private void showAddShortcutDialog() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        box.setPadding(pad, pad, pad, 0);
        EditText label = new EditText(activity);
        label.setSingleLine(true);
        label.setHint("Shortcut label");
        EditText uri = new EditText(activity);
        uri.setSingleLine(false);
        uri.setMinLines(2);
        uri.setHint("Intent URI, URL, or package: URI");
        uri.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(label);
        box.addView(uri);
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Add kiosk shortcut")
                .setMessage("Use an intent URI for advanced shortcuts, or paste https:// / package: style links for simple launcher shortcuts.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, which) -> {
                    String name = text(label);
                    String raw = text(uri);
                    String intentUri = normalizeShortcutUri(raw);
                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(intentUri)) {
                        Toast.makeText(activity, "Shortcut label and URI are required", Toast.LENGTH_LONG).show();
                        return;
                    }
                    store.addOrUpdate(new KioskAllowedItem(KioskAllowedItem.TYPE_SHORTCUT, intentUri, name, true));
                    debugOutput("settings", "shortcut added: " + name);
                    renderAllowedList();
                })
                .show();
    }

    private String normalizeShortcutUri(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String s = raw.trim();
        try {
            if (s.startsWith("intent:") || s.startsWith("android-app:")) return s;
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(s));
            return i.toUri(Intent.URI_INTENT_SCHEME);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void showPatternDialog() {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(KioskPrefs.DEFAULT_EXIT_PATTERN);
        input.setText(store.exitPattern());
        input.setSelectAllOnFocus(true);
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Kiosk exit pattern")
                .setMessage("Format: TL:3,TR:1,BL:4,BR:2. Corners are TL/TR/BL/BR. Recovery is fixed at 21 blank-space taps, then hold the 22nd press for 7-13 seconds and release.")
                .setView(input)
                .setNeutralButton("Default", (d, which) -> {
                    store.setExitPattern(KioskPrefs.DEFAULT_EXIT_PATTERN);
                    updatePatternSummary();
                })
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    store.setExitPattern(KioskExitPattern.normalizePattern(text(input)));
                    debugOutput("settings", "exit pattern saved: " + store.exitPattern());
                    updatePatternSummary();
                    Toast.makeText(activity, "Kiosk pattern saved", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void updatePatternSummary() {
        if (binding.tabSettings.txtKioskPatternSummary != null) {
            binding.tabSettings.txtKioskPatternSummary.setText("Exit pattern: " + KioskExitPattern.describe(store.exitPattern())
                    + "\n" + KioskExitPattern.recoveryDescription());
        }
    }

    private void renderAllowedList() {
        LinearLayout list = binding.tabSettings.listKioskAllowedItems;
        if (list == null) return;
        list.removeAllViews();
        List<KioskAllowedItem> items = store.loadAllowedItems();
        if (items.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No allowed apps or shortcuts yet. Add and enable at least one item before turning on Kiosk Mode.");
            empty.setTextSize(12f);
            empty.setAlpha(0.78f);
            list.addView(empty);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            KioskAllowedItem item = items.get(i);
            list.addView(rowForItem(items, item, i, items.size()));
        }
    }

    private View rowForItem(List<KioskAllowedItem> allItems, KioskAllowedItem item, int position, int count) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);

        MaterialButton move = new MaterialButton(activity);
        move.setText("☰");
        move.setContentDescription("Drag to reorder kiosk item");
        move.setMinWidth(0);
        move.setMinimumWidth(0);
        move.setPadding(dp(8), 0, dp(8), 0);
        bindDragHandle(move, row, allItems, item, count);
        top.addView(move, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView text = new TextView(activity);
        text.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        text.setPadding(dp(8), 0, dp(8), 0);
        text.setText((position + 1) + ". " + (item.enabled ? "✓ " : "○ ") + item.label + "\n" + (item.isApp() ? item.id : "shortcut"));
        text.setTextSize(13f);
        text.setAlpha(item.enabled ? 1f : 0.55f);
        top.addView(text);

        MaterialButton remove = compactActionButton("Remove");
        remove.setOnClickListener(v -> confirmRemove(allItems, item));
        top.addView(remove, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row.addView(top);

        row.setOnClickListener(v -> {
            item.enabled = !item.enabled;
            store.saveAllowedItems(allItems);
            debugOutput("settings", "allowed item " + (item.enabled ? "enabled: " : "disabled: ") + item.label);
            maybeDisableKioskWithoutItems();
            renderAllowedList();
        });
        row.setOnDragListener((target, event) -> handleAllowedItemDrag(event, allItems, item));
        return row;
    }



    private void bindDragHandle(MaterialButton handle, View dragShadowSource, List<KioskAllowedItem> allItems,
                                KioskAllowedItem item, int count) {
        if (handle == null || item == null) return;
        handle.setOnTouchListener((v, event) -> {
            if (event == null || event.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
            if (count < 2) {
                Toast.makeText(activity, "Only one kiosk item is listed", Toast.LENGTH_SHORT).show();
                return true;
            }
            try {
                ViewParent parent = v.getParent();
                while (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                    if (parent instanceof View) {
                        parent = ((View) parent).getParent();
                    } else {
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
            beginAllowedItemDrag(v, dragShadowSource == null ? v : dragShadowSource, item);
            return true;
        });
        handle.setOnLongClickListener(v -> {
            if (count < 2) return true;
            beginAllowedItemDrag(v, dragShadowSource == null ? v : dragShadowSource, item);
            return true;
        });
    }

    private void beginAllowedItemDrag(View source, View shadowSource, KioskAllowedItem item) {
        if (source == null || item == null) return;
        DragState state = new DragState(item.stableKey());
        ClipData clip = ClipData.newPlainText("kiosk-allowed-item", state.stableKey);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(shadowSource == null ? source : shadowSource);
        boolean started;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            started = source.startDragAndDrop(clip, shadow, state, 0);
        } else {
            started = source.startDrag(clip, shadow, state, 0);
        }
        if (started) {
            debugOutput("settings", "allowed item drag started: " + item.label);
        }
    }

    private boolean handleAllowedItemDrag(DragEvent event, List<KioskAllowedItem> allItems, KioskAllowedItem targetItem) {
        if (event == null || allItems == null || targetItem == null) return false;
        Object local = event.getLocalState();
        if (!(local instanceof DragState)) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return allItems.size() > 1;
            case DragEvent.ACTION_DROP:
                moveAllowedItemTo(allItems, ((DragState) local).stableKey, indexOfAllowedItem(allItems, targetItem));
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
            case DragEvent.ACTION_DRAG_LOCATION:
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                return true;
            default:
                return true;
        }
    }

    private MaterialButton compactActionButton(String label) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(label);
        int minWidth = TextUtils.equals(label, "Remove") ? dp(84) : 0;
        button.setMinWidth(minWidth);
        button.setMinimumWidth(minWidth);
        int horizontalPadding = TextUtils.equals(label, "Remove") ? dp(12) : dp(4);
        button.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        return button;
    }

    private void moveAllowedItemTo(List<KioskAllowedItem> allItems, String stableKey, int targetIndex) {
        if (TextUtils.isEmpty(stableKey) || allItems == null) return;
        for (KioskAllowedItem item : allItems) {
            if (item != null && TextUtils.equals(item.stableKey(), stableKey)) {
                moveAllowedItemTo(allItems, item, targetIndex);
                return;
            }
        }
    }

    private void moveAllowedItemTo(List<KioskAllowedItem> allItems, KioskAllowedItem item, int targetIndex) {
        if (allItems == null || item == null || allItems.size() < 2) return;
        int index = indexOfAllowedItem(allItems, item);
        if (index < 0) return;
        int clampedTarget = Math.max(0, Math.min(allItems.size() - 1, targetIndex));
        if (index == clampedTarget) return;
        KioskAllowedItem moved = allItems.remove(index);
        allItems.add(clampedTarget, moved);
        store.saveAllowedItems(allItems);
        debugOutput("settings", "allowed item moved: " + moved.label + " from " + (index + 1) + " to " + (clampedTarget + 1));
        renderAllowedList();
    }

    private int indexOfAllowedItem(List<KioskAllowedItem> allItems, KioskAllowedItem item) {
        if (allItems == null || item == null) return -1;
        String key = item.stableKey();
        for (int i = 0; i < allItems.size(); i++) {
            KioskAllowedItem candidate = allItems.get(i);
            if (candidate != null && TextUtils.equals(candidate.stableKey(), key)) return i;
        }
        return -1;
    }

    private void confirmRemove(List<KioskAllowedItem> allItems, KioskAllowedItem item) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Remove kiosk item")
                .setMessage("Remove " + item.label + " from the allowed kiosk list?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    allItems.remove(item);
                    store.saveAllowedItems(allItems);
                    debugOutput("settings", "allowed item removed: " + item.label);
                    maybeDisableKioskWithoutItems();
                    renderAllowedList();
                })
                .show();
    }

    private void maybeDisableKioskWithoutItems() {
        if (!store.isKioskModeRequested() || store.hasEnabledAllowedItems()) return;
        store.setKioskEnabled(false);
        syncMainKioskCheckbox(false);
        Toast.makeText(activity, "Kiosk Mode disabled because no kiosk apps or shortcuts are enabled.", Toast.LENGTH_LONG).show();
    }

    private void setText(TextView view, String text) {
        if (view != null) view.setText(text == null ? "" : text);
    }

    private String text(TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
    }

    private void setChecked(MaterialCheckBox box, boolean checked) {
        if (box != null) box.setChecked(checked);
    }

    private boolean isChecked(MaterialCheckBox box) {
        return box != null && box.isChecked();
    }

    private int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private int dp(int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }

    private void appendOutput(String text) {
        if (host != null) host.appendOutput(text);
    }

    private boolean isDebugOutputEnabled() {
        try {
            return host != null && host.isDebugOutputEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void debugOutput(String area, String message) {
        if (!isDebugOutputEnabled()) return;
        try {
            if (host != null) host.debugOutput(area, message);
        } catch (Throwable ignored) {
        }
    }
    private static final class DragState {
        final String stableKey;

        DragState(String stableKey) {
            this.stableKey = stableKey;
        }
    }

}
