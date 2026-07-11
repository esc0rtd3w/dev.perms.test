package dev.perms.test.memory.payload;

import dev.perms.test.R;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.vr.PermsTestVrOverlayCompat;
import dev.perms.test.assets.AssetDefaultsInstaller;
import dev.perms.test.editor.SourceSyntaxHighlighter;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Small Memory-tab payload JSON editor.
 *
 * The Hex overlay remains the primary capture/write path.  This controller only
 * gives users a safer way to inspect and repair payload JSON, especially ASCII
 * replacements and wildcard masks that would otherwise require an external hex
 * converter.
 */
public final class MemoryPayloadEditorController {
    private static final int PAYLOAD_ENABLED_COLOR = Color.rgb(76, 175, 80);
    private static final int PAYLOAD_DISABLED_COLOR = Color.rgb(229, 57, 53);

    public interface TargetPackageProvider {
        String getTargetPackage();
    }

    private final Context context;
    private final View root;
    private final TargetPackageProvider targetPackageProvider;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<File> payloadFiles = new ArrayList<>();
    private final ArrayList<String> payloadLabels = new ArrayList<>();
    private final ArrayList<Boolean> payloadEnabledStates = new ArrayList<>();

    private MaterialAutoCompleteTextView payloadDropdown;
    private CheckBox installBundledCheckbox;
    private TextView statusView;
    private File selectedPayloadFile;
    private int payloadDropdownDefaultTextColor = Color.WHITE;

    public MemoryPayloadEditorController(Context context, View root, TargetPackageProvider targetPackageProvider) {
        this.context = context;
        this.root = root;
        this.targetPackageProvider = targetPackageProvider;
    }

    public void bind() {
        payloadDropdown = root.findViewById(R.id.ddMemoryPayloadEditorPayload);
        installBundledCheckbox = root.findViewById(R.id.chkMemoryPayloadEditorInstallBundled);
        statusView = root.findViewById(R.id.txtMemoryPayloadEditorStatus);
        View refresh = root.findViewById(R.id.btnMemoryPayloadEditorRefresh);
        View load = root.findViewById(R.id.btnMemoryPayloadEditorLoad);
        View create = root.findViewById(R.id.btnMemoryPayloadEditorNew);
        View delete = root.findViewById(R.id.btnMemoryPayloadEditorDelete);
        View enable = root.findViewById(R.id.btnMemoryPayloadEditorEnable);
        View disable = root.findViewById(R.id.btnMemoryPayloadEditorDisable);
        View clearMask = root.findViewById(R.id.btnMemoryPayloadEditorClearMask);
        if (payloadDropdown == null || refresh == null || load == null || create == null || delete == null
                || enable == null || disable == null || clearMask == null) return;

        try {
            payloadDropdownDefaultTextColor = payloadDropdown.getCurrentTextColor();
        } catch (Throwable ignored) {
            payloadDropdownDefaultTextColor = Color.WHITE;
        }
        DropdownUi.prepareExposedDropdown(root.findViewById(R.id.tilMemoryPayloadEditorPayload), payloadDropdown);
        payloadDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < payloadFiles.size()) {
                selectedPayloadFile = payloadFiles.get(position);
                updateSelectedPayloadTextColor(position);
                setStatus("Selected " + selectedPayloadFile.getName() + ".");
            }
        });
        payloadDropdown.setOnClickListener(v -> showPayloadDropdown());
        payloadDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showPayloadDropdown();
        });
        refresh.setOnClickListener(v -> refreshPayloadList(true));
        load.setOnClickListener(v -> showPayloadEditor(false));
        create.setOnClickListener(v -> showPayloadEditor(true));
        delete.setOnClickListener(v -> confirmDeleteSelectedPayload());
        enable.setOnClickListener(v -> updateSelectedPayloadEnabled(true));
        disable.setOnClickListener(v -> updateSelectedPayloadEnabled(false));
        clearMask.setOnClickListener(v -> confirmClearSelectedPayloadMask());
        setStatus("Payloads path: " + MemoryHexPayloadStore.PAYLOAD_DIR + "/<package>");
    }

    private void showPayloadDropdown() {
        try {
            if (payloadDropdown == null) return;
            if (payloadDropdown.getAdapter() == null || payloadDropdown.getAdapter().getCount() == 0) {
                refreshPayloadList(false);
                return;
            }
            if (PermsTestVrOverlayCompat.isEnabled(context)) {
                showPayloadDialog();
            } else {
                DropdownUi.showDropdown(payloadDropdown, this::styleFastList);
            }
        } catch (Throwable ignored) {
        }
    }

    private void showPayloadDialog() {
        try {
            final ArrayList<String> labels = new ArrayList<>();
            if (!payloadLabels.isEmpty()) {
                labels.addAll(payloadLabels);
            } else {
                Adapter adapter = payloadDropdown == null ? null : payloadDropdown.getAdapter();
                if (adapter != null) {
                    for (int i = 0; i < adapter.getCount(); i++) {
                        Object item = adapter.getItem(i);
                        if (item != null) labels.add(String.valueOf(item));
                    }
                }
            }
            if (labels.isEmpty()) {
                setStatus("No payload JSON files are available.");
                return;
            }
            ArrayAdapter<String> adapter = createPayloadAdapter(labels);
            AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                    .setTitle("Payload JSON")
                    .setAdapter(adapter, (d, which) -> {
                        if (which >= 0 && which < labels.size()) {
                            if (payloadDropdown != null) payloadDropdown.setText(labels.get(which), false);
                            selectedPayloadFile = which < payloadFiles.size() ? payloadFiles.get(which) : selectedPayloadFile;
                            updateSelectedPayloadTextColor(which);
                            if (selectedPayloadFile != null) setStatus("Selected " + selectedPayloadFile.getName() + ".");
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            styleDialogList(dialog);
        } catch (Throwable t) {
            setStatus("Payload picker failed: " + safeMessage(t));
        }
    }

    private void styleDialogList(AlertDialog dialog) {
        try {
            if (dialog == null) return;
            ListView list = dialog.getListView();
            if (list != null) styleFastList(list);
        } catch (Throwable ignored) {
        }
    }

    private ArrayAdapter<String> createPayloadAdapter(ArrayList<String> labels) {
        return new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return tintPayloadRow(super.getView(position, convertView, parent), position);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return tintPayloadRow(super.getDropDownView(position, convertView, parent), position);
            }
        };
    }

    private View tintPayloadRow(View view, int position) {
        try {
            TextView text = view instanceof TextView ? (TextView) view : view.findViewById(android.R.id.text1);
            if (text != null) text.setTextColor(payloadColorForPosition(position));
        } catch (Throwable ignored) {
        }
        return view;
    }

    private void updateSelectedPayloadTextColor(int position) {
        try {
            if (payloadDropdown == null) return;
            if (position >= 0 && position < payloadEnabledStates.size()) {
                payloadDropdown.setTextColor(payloadColorForPosition(position));
            } else {
                payloadDropdown.setTextColor(payloadDropdownDefaultTextColor);
            }
        } catch (Throwable ignored) {
        }
    }

    private int payloadColorForPosition(int position) {
        boolean enabled = true;
        if (position >= 0 && position < payloadEnabledStates.size()) {
            enabled = payloadEnabledStates.get(position);
        }
        return enabled ? PAYLOAD_ENABLED_COLOR : PAYLOAD_DISABLED_COLOR;
    }

    private boolean isPayloadEnabled(File file) {
        try {
            return MemoryHexPayloadStore.loadPayload(context, file).enabled;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void applyDropdownFastScroll(MaterialAutoCompleteTextView dropdown) {
        try {
            if (dropdown == null) return;
            java.lang.reflect.Field f = android.widget.AutoCompleteTextView.class.getDeclaredField("mPopup");
            f.setAccessible(true);
            Object popup = f.get(dropdown);
            if (popup == null) return;
            java.lang.reflect.Method m = popup.getClass().getMethod("getListView");
            Object lv = m.invoke(popup);
            if (lv instanceof ListView) styleFastList((ListView) lv);
        } catch (Throwable ignored) {
        }
    }

    private void styleFastList(ListView list) {
        try {
            if (list == null) return;
            list.setVerticalScrollBarEnabled(true);
            list.setFastScrollEnabled(true);
            list.setFastScrollAlwaysVisible(true);
            list.setScrollbarFadingEnabled(false);
            list.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            list.setScrollBarSize(dp(28));
        } catch (Throwable ignored) {
        }
    }

    private int dp(int value) {
        float density = context == null ? 1.0f : context.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * density));
    }

    private void refreshPayloadList(boolean showToast) {
        final String packageName = currentPackageName();
        if (TextUtils.isEmpty(packageName)) {
            setStatus("Select a target package before refreshing payloads.");
            return;
        }
        setStatus("Loading payloads from " + MemoryHexPayloadStore.packageDirectoryPath(packageName) + "...");
        new Thread(() -> {
            final boolean installBundled = installBundledCheckbox == null || installBundledCheckbox.isChecked();
            final int copiedDefaults = installBundled
                    ? AssetDefaultsInstaller.installBundledMemoryPayloads(context, packageName, false)
                    : 0;
            final ArrayList<File> files = MemoryHexPayloadStore.listPayloadFiles(context, packageName);
            final ArrayList<String> labels = new ArrayList<>();
            final ArrayList<Boolean> enabledStates = new ArrayList<>();
            for (File file : files) {
                labels.add(MemoryHexPayloadStore.payloadLabel(context, file));
                enabledStates.add(isPayloadEnabled(file));
            }
            mainHandler.post(() -> {
                payloadFiles.clear();
                payloadFiles.addAll(files);
                payloadLabels.clear();
                payloadLabels.addAll(labels);
                payloadEnabledStates.clear();
                payloadEnabledStates.addAll(enabledStates);
                ArrayAdapter<String> adapter = createPayloadAdapter(labels);
                payloadDropdown.setAdapter(adapter);
                selectedPayloadFile = files.isEmpty() ? null : files.get(0);
                payloadDropdown.setText(files.isEmpty() ? "" : labels.get(0), false);
                updateSelectedPayloadTextColor(files.isEmpty() ? -1 : 0);
                String status = files.isEmpty()
                        ? "No payload JSON files found for " + packageName + ". Use New or save one from the Hex Editor."
                        : "Found " + files.size() + " payload JSON file(s) for " + packageName + ".";
                if (copiedDefaults > 0) {
                    status = "Installed " + copiedDefaults + " bundled payload(s). " + status;
                } else if (!installBundled) {
                    status = status + " Bundled payload install is disabled.";
                }
                setStatus(status);
                if (showToast) Toast.makeText(context, files.isEmpty() ? "No payloads found" : "Payload list refreshed", Toast.LENGTH_SHORT).show();
            });
        }, "MemoryPayloadEditorList").start();
    }

    private File selectedOrFirstPayloadFile() {
        if (selectedPayloadFile != null) return selectedPayloadFile;
        return payloadFiles.isEmpty() ? null : payloadFiles.get(0);
    }

    private void updateSelectedPayloadEnabled(boolean enabled) {
        File file = selectedOrFirstPayloadFile();
        if (file == null) {
            setStatus("No payload is selected. Press Refresh or select a payload first.");
            return;
        }
        setStatus((enabled ? "Enabling " : "Disabling ") + file.getName() + "...");
        new Thread(() -> {
            try {
                String path = MemoryHexPayloadStore.setPayloadEnabled(context, file, enabled, stampNow());
                mainHandler.post(() -> {
                    setStatus((enabled ? "Enabled " : "Disabled ") + file.getName() + " at " + path);
                    Toast.makeText(context, enabled ? "Payload enabled" : "Payload disabled", Toast.LENGTH_SHORT).show();
                    refreshPayloadList(false);
                });
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    setStatus("Payload update failed: " + safeMessage(t));
                    Toast.makeText(context, safeMessage(t), Toast.LENGTH_LONG).show();
                });
            }
        }, enabled ? "MemoryPayloadEditorEnable" : "MemoryPayloadEditorDisable").start();
    }

    private void confirmClearSelectedPayloadMask() {
        File file = selectedOrFirstPayloadFile();
        if (file == null) {
            setStatus("No payload is selected. Press Refresh or select a payload first.");
            return;
        }
        final File clearFile = file;
        new MaterialAlertDialogBuilder(context)
                .setTitle("Remove Payload Mask")
                .setMessage("Remove mask_hex from " + clearFile.getName() + "?\n\nThis makes the payload use exact original_hex matching again.")
                .setNegativeButton("No", null)
                .setPositiveButton("Yes", (dialog, which) -> clearSelectedPayloadMask(clearFile))
                .show();
    }

    private void clearSelectedPayloadMask(File file) {
        if (file == null) return;
        setStatus("Removing mask from " + file.getName() + "...");
        new Thread(() -> {
            try {
                String path = MemoryHexPayloadStore.clearPayloadMask(context, file, stampNow());
                mainHandler.post(() -> {
                    setStatus("Removed mask from " + file.getName() + " at " + path);
                    Toast.makeText(context, "Payload mask removed", Toast.LENGTH_SHORT).show();
                    refreshPayloadList(false);
                });
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    setStatus("Remove mask failed: " + safeMessage(t));
                    Toast.makeText(context, safeMessage(t), Toast.LENGTH_LONG).show();
                });
            }
        }, "MemoryPayloadEditorClearMask").start();
    }

    private void confirmDeleteSelectedPayload() {
        File file = selectedOrFirstPayloadFile();
        if (file == null) {
            setStatus("No payload is selected. Press Refresh or select a payload first.");
            return;
        }
        final File deleteFile = file;
        new MaterialAlertDialogBuilder(context)
                .setTitle("Delete Payload JSON")
                .setMessage("Delete " + deleteFile.getName() + "?\n\nThis cannot be undone.")
                .setNegativeButton("No", null)
                .setPositiveButton("Yes", (dialog, which) -> deleteSelectedPayload(deleteFile))
                .show();
    }

    private void deleteSelectedPayload(File file) {
        if (file == null) return;
        final String path = file.getAbsolutePath();
        setStatus("Deleting " + file.getName() + "...");
        new Thread(() -> {
            try {
                MemoryHexPayloadStore.deletePayloadFile(context, file);
                mainHandler.post(() -> {
                    if (selectedPayloadFile != null && TextUtils.equals(selectedPayloadFile.getAbsolutePath(), path)) {
                        selectedPayloadFile = null;
                    }
                    setStatus("Deleted " + file.getName() + ".");
                    Toast.makeText(context, "Payload deleted", Toast.LENGTH_SHORT).show();
                    refreshPayloadList(false);
                });
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    setStatus("Payload delete failed: " + safeMessage(t));
                    Toast.makeText(context, safeMessage(t), Toast.LENGTH_LONG).show();
                });
            }
        }, "MemoryPayloadEditorDelete").start();
    }

    private void showPayloadEditor(boolean createNew) {
        final String packageName = currentPackageName();
        if (TextUtils.isEmpty(packageName)) {
            setStatus("Select a target package before editing payloads.");
            return;
        }
        File file = createNew ? null : selectedPayloadFile;
        if (!createNew && file == null && !payloadFiles.isEmpty()) file = payloadFiles.get(0);
        if (!createNew && file == null) {
            setStatus("No payload is selected. Press Refresh or create a new payload.");
            return;
        }
        final File selectedFile = file;
        new Thread(() -> {
            try {
                final JSONObject json = createNew ? defaultPayloadJson(packageName) : new JSONObject(MemoryHexPayloadStore.readPayloadTextFile(context, selectedFile));
                mainHandler.post(() -> showPayloadEditorDialog(selectedFile, json, createNew));
            } catch (Throwable t) {
                mainHandler.post(() -> setStatus("Payload load failed: " + safeMessage(t)));
            }
        }, "MemoryPayloadEditorLoad").start();
    }

    private void showPayloadEditorDialog(File sourceFile, JSONObject json, boolean createNew) {
        final LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        body.setPadding(pad, pad, pad, pad);

        EditText name = addField(body, "Name", json.optString("name", createNew ? "payload" : ""), false, 1);
        EditText packageName = addField(body, "Package name", json.optString("package_name", currentPackageName()), false, 1);
        EditText fileName = addField(body, "File name", json.optString("file_name", ""), false, 1);
        EditText originalAscii = addField(body, "Original ASCII (fixed length)", asciiForJson(json, "original_hex"), false, 2);
        EditText patchedAscii = addField(body, "Patched ASCII (fixed length)", asciiForJson(json, "patched_hex"), false, 2);
        EditText maskPattern = addField(body, "Mask pattern (? or X = wildcard byte)", maskPatternForJson(json), false, 2);
        EditText originalHex = addField(body, "Original Hex", json.optString("original_hex", ""), false, 3);
        EditText patchedHex = addField(body, "Patched Hex", json.optString("patched_hex", ""), false, 3);
        EditText maskHex = addField(body, "Mask Hex", json.optString("mask_hex", ""), false, 3);
        EditText sectionStartAscii = addField(body, "Section start ASCII (optional)", sectionAsciiForJson(json, "section_start_ascii", "section_start_hex"), false, 1);
        EditText sectionEndAscii = addField(body, "Section end ASCII (optional)", sectionAsciiForJson(json, "section_end_ascii", "section_end_hex"), false, 1);
        EditText sectionStartHex = addField(body, "Section Start Hex", json.optString("section_start_hex", ""), false, 2);
        EditText sectionEndHex = addField(body, "Section End Hex", json.optString("section_end_hex", ""), false, 2);
        installSectionMarkerSync(sectionStartAscii, sectionStartHex);
        installSectionMarkerSync(sectionEndAscii, sectionEndHex);
        EditText originalAddress = addField(body, "Original Address", json.optString("original_address", json.optString("address", "")), false, 1);
        EditText patchedAddress = addField(body, "Patched Address", json.optString("patched_address", json.optString("address", "")), false, 1);
        CheckBox enabled = new CheckBox(context);
        enabled.setText("Payload enabled");
        enabled.setChecked(!json.has("enabled") || json.optBoolean("enabled", true));
        body.addView(enabled);
        CheckBox preserveMaskedBytes = new CheckBox(context);
        preserveMaskedBytes.setText("Preserve masked bytes on write");
        preserveMaskedBytes.setChecked(json.optBoolean("preserve_mask_wildcards", false));
        body.addView(preserveMaskedBytes);
        CheckBox fixPackageFields = new CheckBox(context);
        fixPackageFields.setText("Fix package/schema fields to current target on save");
        fixPackageFields.setChecked(false);
        body.addView(fixPackageFields);
        EditText rawJson = addField(body, "Raw JSON / extra fields", prettyJsonForEditor(json), true, 8);
        installJsonSyntaxHighlighting(rawJson);
        CheckBox useAscii = new CheckBox(context);
        useAscii.setText("Sync hex from ASCII on save");
        useAscii.setChecked(false);
        body.addView(useAscii);
        CheckBox useMaskPattern = new CheckBox(context);
        useMaskPattern.setText("Use mask pattern to rebuild mask_hex");
        useMaskPattern.setChecked(!TextUtils.isEmpty(maskPattern.getText().toString()));
        body.addView(useMaskPattern);

        LinearLayout toolRow = new LinearLayout(context);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setPadding(0, dp(6), 0, 0);
        Button syncAscii = new Button(context);
        syncAscii.setText("Update hex from ASCII");
        Button refreshAscii = new Button(context);
        refreshAscii.setText("Refresh ASCII from hex");
        toolRow.addView(syncAscii, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        toolRow.addView(refreshAscii, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(toolRow);

        LinearLayout maskRow = new LinearLayout(context);
        maskRow.setOrientation(LinearLayout.HORIZONTAL);
        maskRow.setPadding(0, dp(4), 0, 0);
        Button maskPresets = new Button(context);
        maskPresets.setText("Mask presets");
        Button clearMaskInEditor = new Button(context);
        clearMaskInEditor.setText("Clear mask");
        maskRow.addView(maskPresets, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        maskRow.addView(clearMaskInEditor, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(maskRow);
        syncAscii.setOnClickListener(v -> {
            try {
                originalHex.setText(applyAsciiEditsToHex(originalHex.getText().toString(), originalAscii.getText().toString(), "Original ASCII"));
                patchedHex.setText(applyAsciiEditsToHex(patchedHex.getText().toString(), patchedAscii.getText().toString(), "Patched ASCII"));
                sectionStartHex.setText(sectionAsciiToHex(sectionStartAscii.getText().toString()));
                sectionEndHex.setText(sectionAsciiToHex(sectionEndAscii.getText().toString()));
                Toast.makeText(context, "Hex updated from ASCII fields", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(context, safeMessage(t), Toast.LENGTH_LONG).show();
            }
        });
        refreshAscii.setOnClickListener(v -> {
            try {
                originalAscii.setText(asciiForHexField(originalHex.getText().toString(), "original_hex"));
                patchedAscii.setText(asciiForHexField(patchedHex.getText().toString(), "patched_hex"));
                sectionStartAscii.setText(sectionAsciiPreviewForHexField(sectionStartHex.getText().toString(), "section_start_hex"));
                sectionEndAscii.setText(sectionAsciiPreviewForHexField(sectionEndHex.getText().toString(), "section_end_hex"));
                Toast.makeText(context, "ASCII refreshed from hex fields", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(context, safeMessage(t), Toast.LENGTH_LONG).show();
            }
        });
        maskPresets.setOnClickListener(v -> showMaskPresetDialog(originalAscii, originalHex, maskPattern, maskHex, useAscii, useMaskPattern, preserveMaskedBytes));
        clearMaskInEditor.setOnClickListener(v -> {
            maskPattern.setText("");
            maskHex.setText("");
            useMaskPattern.setChecked(false);
            preserveMaskedBytes.setChecked(false);
            try {
                JSONObject raw = new JSONObject(rawJson.getText().toString());
                raw.remove("mask_hex");
                raw.remove("mask_wildcard_count");
                raw.remove("mask_note");
                raw.remove("preserve_mask_wildcards");
                rawJson.setText(prettyJsonForEditor(raw));
            } catch (Throwable ignored) {
            }
            Toast.makeText(context, "Mask fields cleared", Toast.LENGTH_SHORT).show();
        });

        TextView helper = new TextView(context);
        helper.setText("ASCII edits are fixed-length: normal characters update matching bytes, while dots over non-printable bytes keep the original byte. Sync is off by default; use Update hex from ASCII for a manual one-time conversion, or check Sync hex from ASCII on save when intentionally editing bytes through ASCII. Section start/end ASCII or Hex is optional and limits Apply to matches found between those marker bytes. Payload enabled controls whether Apply All and Apply On Attach include this JSON. Preserve masked bytes on write keeps live memory bytes at 00 mask positions instead of writing stale patched_hex wildcard bytes. Fix package/schema fields is off by default; when checked it rewrites package_name/package_folder to the current target and fills required schema metadata. Mask presets show a description for each option and can ignore unstable pointer/padding bytes, NUL padding, or variable values after a prefix/key; Clear mask restores exact matching.");
        helper.setTextSize(12f);
        helper.setAlpha(0.75f);
        helper.setPadding(0, dp(8), 0, 0);
        body.addView(helper);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(createNew ? "New Payload JSON" : "Edit Payload JSON")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                JSONObject edited = new JSONObject(rawJson.getText().toString());
                applyText(edited, "name", name.getText().toString());
                String savePackageName = packageName.getText().toString();
                if (fixPackageFields.isChecked()) {
                    String currentTarget = currentPackageName();
                    if (!TextUtils.isEmpty(currentTarget)) savePackageName = currentTarget;
                    edited.put("schema", MemoryHexPayloadStore.PAYLOAD_SCHEMA);
                    edited.put("version", 1);
                }
                applyText(edited, "package_name", savePackageName);
                if (fixPackageFields.isChecked() && !TextUtils.isEmpty(savePackageName)) {
                    edited.put("package_folder", MemoryHexPayloadStore.sanitizePackageFolder(savePackageName));
                    packageName.setText(savePackageName);
                }
                applyText(edited, "file_name", fileName.getText().toString());
                applyText(edited, "original_address", originalAddress.getText().toString());
                applyText(edited, "patched_address", patchedAddress.getText().toString());
                applyText(edited, "section_start_ascii", sectionStartAscii.getText().toString());
                applyText(edited, "section_end_ascii", sectionEndAscii.getText().toString());
                applyText(edited, "section_start_hex", sectionStartHex.getText().toString());
                applyText(edited, "section_end_hex", sectionEndHex.getText().toString());
                if (TextUtils.isEmpty(sectionStartAscii.getText().toString().trim())
                        && TextUtils.isEmpty(sectionEndAscii.getText().toString().trim())
                        && TextUtils.isEmpty(sectionStartHex.getText().toString().trim())
                        && TextUtils.isEmpty(sectionEndHex.getText().toString().trim())) {
                    edited.remove("section_start_ascii");
                    edited.remove("section_end_ascii");
                    edited.remove("section_start_hex");
                    edited.remove("section_end_hex");
                    edited.remove("section_scope_note");
                }
                edited.put("enabled", enabled.isChecked());
                if (preserveMaskedBytes.isChecked()) {
                    edited.put("preserve_mask_wildcards", true);
                } else {
                    edited.remove("preserve_mask_wildcards");
                }
                if (useAscii.isChecked()) {
                    String updatedOriginalHex = applyAsciiEditsToHex(originalHex.getText().toString(), originalAscii.getText().toString(), "Original ASCII");
                    String updatedPatchedHex = applyAsciiEditsToHex(patchedHex.getText().toString(), patchedAscii.getText().toString(), "Patched ASCII");
                    String updatedSectionStartHex = sectionAsciiToHex(sectionStartAscii.getText().toString());
                    String updatedSectionEndHex = sectionAsciiToHex(sectionEndAscii.getText().toString());
                    edited.put("original_hex", updatedOriginalHex);
                    edited.put("patched_hex", updatedPatchedHex);
                    applyText(edited, "section_start_hex", updatedSectionStartHex);
                    applyText(edited, "section_end_hex", updatedSectionEndHex);
                    originalHex.setText(updatedOriginalHex);
                    patchedHex.setText(updatedPatchedHex);
                    sectionStartHex.setText(updatedSectionStartHex);
                    sectionEndHex.setText(updatedSectionEndHex);
                } else {
                    applyText(edited, "original_hex", originalHex.getText().toString());
                    applyText(edited, "patched_hex", patchedHex.getText().toString());
                }
                if (useMaskPattern.isChecked()) {
                    edited.put("mask_hex", maskPatternToHex(maskPattern.getText().toString(), MemoryHexPayloadStore.byteCountForHex(edited.optString("original_hex", ""))));
                } else {
                    applyText(edited, "mask_hex", maskHex.getText().toString());
                }
                MemoryHexPayloadStore.EditorSaveResult saved = MemoryHexPayloadStore.saveEditedPayload(context, edited, currentPackageName(), sourceFile == null ? null : sourceFile.getName(), stampNow());
                dialog.dismiss();
                setStatus("Saved " + saved.fileName + " (" + MemoryHexPayloadStore.formatPayloadLength(saved.length)
                        + ", mask " + saved.wildcardBytes + ") to " + saved.path);
                refreshPayloadList(false);
            } catch (Throwable t) {
                setStatus("Payload save failed: " + safeMessage(t));
                Toast.makeText(context, safeMessage(t), Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    /**
     * Pretty-print payload JSON for the editor without forcing callers to
     * handle JSONObject.toString(int)'s checked exception at view-build time.
     */
    private String prettyJsonForEditor(JSONObject json) {
        if (json == null) return "{}";
        try {
            return json.toString(2).replace("\\/", "/");
        } catch (Throwable ignored) {
            return json.toString().replace("\\/", "/");
        }
    }

    private EditText addField(LinearLayout body, String label, String value, boolean monospace, int minLines) {
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextSize(12f);
        title.setAlpha(0.75f);
        title.setPadding(0, dp(8), 0, 0);
        body.addView(title);
        EditText edit = new EditText(context);
        edit.setText(value == null ? "" : value);
        edit.setSingleLine(minLines <= 1);
        edit.setMinLines(Math.max(1, minLines));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | (minLines > 1 ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (monospace) edit.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return edit;
    }

    private void installJsonSyntaxHighlighting(EditText editText) {
        try {
            if (editText == null) return;
            final boolean[] applying = new boolean[1];
            editText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (applying[0]) return;
                    mainHandler.postDelayed(() -> {
                        if (applying[0]) return;
                        try {
                            applying[0] = true;
                            SourceSyntaxHighlighter.applyJson(editText);
                        } catch (Throwable ignored) {
                        } finally {
                            applying[0] = false;
                        }
                    }, 250L);
                }
            });
            mainHandler.post(() -> {
                try {
                    applying[0] = true;
                    SourceSyntaxHighlighter.applyJson(editText);
                } catch (Throwable ignored) {
                } finally {
                    applying[0] = false;
                }
            });
        } catch (Throwable ignored) {}
    }

    private JSONObject defaultPayloadJson(String packageName) throws Exception {
        JSONObject json = new JSONObject();
        json.put("schema", MemoryHexPayloadStore.PAYLOAD_SCHEMA);
        json.put("version", 1);
        json.put("package_name", packageName);
        json.put("package_folder", MemoryHexPayloadStore.sanitizePackageFolder(packageName));
        json.put("name", "payload");
        json.put("file_name", "payload.json");
        json.put("enabled", true);
        json.put("created_at", stampNow());
        json.put("original_hex", "");
        json.put("patched_hex", "");
        json.put("mask_hex", "");
        return json;
    }

    private String currentPackageName() {
        try {
            return targetPackageProvider == null ? "" : nonNullTrim(targetPackageProvider.getTargetPackage());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void setStatus(String text) {
        if (statusView != null) statusView.setText(text == null ? "" : text);
    }

    private void applyText(JSONObject json, String key, String value) throws Exception {
        String trimmed = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(trimmed)) json.remove(key); else json.put(key, trimmed);
    }

    private String asciiForJson(JSONObject json, String key) {
        try {
            String hex = json.optString(key, "");
            return TextUtils.isEmpty(hex) ? "" : MemoryHexPayloadStore.asciiPreview(hex);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String sectionAsciiForJson(JSONObject json, String asciiKey, String hexKey) {
        String ascii = json == null ? "" : json.optString(asciiKey, "");
        if (!TextUtils.isEmpty(ascii)) return ascii;
        try {
            return sectionAsciiPreview(json == null ? "" : json.optString(hexKey, ""));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String sectionAsciiToHex(String ascii) {
        String value = ascii == null ? "" : ascii.trim();
        if (TextUtils.isEmpty(value)) return "";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b & 0xff));
        return sb.toString();
    }

    private String sectionAsciiPreviewForHexField(String hex, String label) {
        try {
            return sectionAsciiPreview(hex);
        } catch (Throwable t) {
            throw new IllegalArgumentException(label + " is not valid hex: " + safeMessage(t));
        }
    }

    private String sectionAsciiPreview(String hex) {
        if (TextUtils.isEmpty(hex)) return "";
        String clean = MemoryHexPayloadStore.normalizeAnyHexBytes(hex);
        byte[] bytes = new byte[clean.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        for (int i = 0; i < decoded.length(); i++) {
            if (Character.isISOControl(decoded.charAt(i))) return "";
        }
        return decoded;
    }

    private void installSectionMarkerSync(EditText asciiInput, EditText hexInput) {
        if (asciiInput == null || hexInput == null) return;
        final boolean[] updating = { false };
        asciiInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                if (updating[0]) return;
                updating[0] = true;
                try {
                    hexInput.setText(sectionAsciiToHex(editable == null ? "" : editable.toString()));
                } catch (Throwable ignored) {
                } finally {
                    updating[0] = false;
                }
            }
        });
        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                if (updating[0]) return;
                updating[0] = true;
                try {
                    String raw = editable == null ? "" : editable.toString();
                    asciiInput.setText(TextUtils.isEmpty(raw.trim()) ? "" : sectionAsciiPreview(raw));
                } catch (Throwable ignored) {
                } finally {
                    updating[0] = false;
                }
            }
        });
    }

    private String asciiForHexField(String hex, String label) {
        try {
            String clean = MemoryHexPayloadStore.normalizeAnyHexBytes(hex);
            return TextUtils.isEmpty(clean) ? "" : MemoryHexPayloadStore.asciiPreview(clean);
        } catch (Throwable t) {
            throw new IllegalArgumentException(label + " is not valid hex: " + safeMessage(t));
        }
    }

    private String maskPatternForJson(JSONObject json) {
        try {
            String originalHex = json.optString("original_hex", "");
            String maskHex = json.optString("mask_hex", "");
            if (TextUtils.isEmpty(originalHex) || TextUtils.isEmpty(maskHex)) return "";
            int length = MemoryHexPayloadStore.byteCountForHex(originalHex);
            String normalizedMask = MemoryHexPayloadStore.normalizeMaskHex(maskHex, length);
            byte[] originalBytes = hexToBytes(originalHex);
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                String byteHex = normalizedMask.substring(i * 2, i * 2 + 2);
                if ("00".equals(byteHex)) sb.append('X');
                else sb.append(i < originalBytes.length ? patternFixedChar(originalBytes[i] & 0xff) : 'F');
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static final int MASK_COMPARE_ALL = 0;
    private static final int MASK_IGNORE_NON_PRINTABLE = 1;
    private static final int MASK_IGNORE_NUL = 2;
    private static final int MASK_IGNORE_MIDDLE_GAP = 3;
    private static final int MASK_KEEP_READABLE_EDGES = 4;
    private static final int MASK_KEEP_FIRST_READABLE = 5;
    private static final int MASK_KEEP_LAST_READABLE = 6;
    private static final int MASK_PREFIX_COLON = 7;
    private static final int MASK_PREFIX_EQUALS = 8;

    private static final class MaskPreset {
        final int id;
        final String title;
        final String description;
        final boolean preserveMaskedBytes;

        MaskPreset(int id, String title, String description) {
            this(id, title, description, false);
        }

        MaskPreset(int id, String title, String description, boolean preserveMaskedBytes) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.preserveMaskedBytes = preserveMaskedBytes;
        }
    }

    private void showMaskPresetDialog(EditText originalAscii,
                                      EditText originalHex,
                                      EditText maskPattern,
                                      EditText maskHex,
                                      CheckBox useAscii,
                                      CheckBox useMaskPattern,
                                      CheckBox preserveMaskedBytes) {
        final MemoryHexPayloadStore.MaskPreset[] presets = MemoryHexPayloadStore.maskPresets(false);

        final AlertDialog[] holder = new AlertDialog[1];
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        list.setPadding(pad, dp(8), pad, dp(8));

        TextView legend = new TextView(context);
        legend.setText("Mask output uses FF to compare a byte and 00 to wildcard/ignore a byte. Pattern uses ? or X for wildcard; all other characters mean compare that byte.");
        legend.setTextSize(12f);
        legend.setAlpha(0.75f);
        legend.setPadding(0, 0, 0, dp(8));
        list.addView(legend);

        for (MemoryHexPayloadStore.MaskPreset preset : presets) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));
            row.setClickable(true);
            row.setFocusable(true);

            TextView title = new TextView(context);
            title.setText(preset.title);
            title.setTextSize(16f);
            title.setAlpha(0.95f);
            row.addView(title);

            TextView description = new TextView(context);
            description.setText(preset.description);
            description.setTextSize(12f);
            description.setAlpha(0.70f);
            description.setPadding(0, dp(2), 0, 0);
            row.addView(description);

            row.setOnClickListener(v -> {
                try {
                    applyMaskPreset(preset.id, originalAscii, originalHex, maskPattern, maskHex, useAscii, useMaskPattern);
                    if (preserveMaskedBytes != null) preserveMaskedBytes.setChecked(preset.preserveMaskedBytes);
                    Toast.makeText(context, "Mask preset applied", Toast.LENGTH_SHORT).show();
                    if (holder[0] != null) holder[0].dismiss();
                } catch (Throwable t) {
                    Toast.makeText(context, safeMessage(t), Toast.LENGTH_LONG).show();
                }
            });
            list.addView(row);

            View divider = new View(context);
            divider.setAlpha(0.35f);
            list.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        }

        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Mask Presets")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .create();
        holder[0] = dialog;
        dialog.show();
    }

    private void applyMaskPreset(int presetId,
                                 EditText originalAscii,
                                 EditText originalHex,
                                 EditText maskPattern,
                                 EditText maskHex,
                                 CheckBox useAscii,
                                 CheckBox useMaskPattern) {
        String hex = originalHex.getText().toString();
        if (useAscii != null && useAscii.isChecked()) {
            hex = applyAsciiEditsToHex(hex, originalAscii.getText().toString(), "Original ASCII");
            originalHex.setText(hex);
        } else {
            hex = MemoryHexPayloadStore.normalizeAnyHexBytes(hex);
        }

        String pattern = MemoryHexPayloadStore.buildMaskPatternForPreset(presetId, hex);

        maskPattern.setText(pattern);
        maskHex.setText(MemoryHexPayloadStore.buildMaskHexForPreset(presetId, hex));
        if (useMaskPattern != null) useMaskPattern.setChecked(true);
    }

    private String buildAllFixedMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        StringBuilder out = new StringBuilder(bytes.length);
        for (byte b : bytes) out.append(patternFixedChar(b & 0xff));
        return out.toString();
    }

    private String buildIgnoreNonPrintableMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        StringBuilder out = new StringBuilder(bytes.length);
        int fixed = 0;
        for (byte b : bytes) {
            int v = b & 0xff;
            if (isPrintableAscii(v)) {
                out.append(patternFixedChar(v));
                fixed++;
            } else {
                out.append('?');
            }
        }
        if (fixed <= 0) throw new IllegalArgumentException("No printable bytes were found to keep fixed.");
        return out.toString();
    }

    private String buildIgnoreByteValueMaskPattern(String hex, int ignoredByte, String emptyMessage) {
        byte[] bytes = hexToBytes(hex);
        StringBuilder out = new StringBuilder(bytes.length);
        int fixed = 0;
        int wildcard = 0;
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v == ignoredByte) {
                out.append('?');
                wildcard++;
            } else {
                out.append(patternFixedChar(v));
                fixed++;
            }
        }
        if (fixed <= 0) throw new IllegalArgumentException("Mask must leave at least one fixed byte.");
        if (wildcard <= 0) throw new IllegalArgumentException(emptyMessage);
        return out.toString();
    }

    private String buildIgnoreMiddleNonPrintableGapMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        ArrayList<ReadableRun> runs = readableRuns(bytes, 3);
        if (runs.size() < 2) {
            throw new IllegalArgumentException("Need at least two readable text blocks with a middle gap.");
        }
        ReadableRun first = runs.get(0);
        ReadableRun last = runs.get(runs.size() - 1);
        if (first.end >= last.start) {
            throw new IllegalArgumentException("No middle gap was found between readable text blocks.");
        }
        StringBuilder out = new StringBuilder(bytes.length);
        int fixed = 0;
        int wildcard = 0;
        for (int p = 0; p < bytes.length; p++) {
            if (p >= first.end && p < last.start) {
                out.append('?');
                wildcard++;
            } else {
                out.append(patternFixedChar(bytes[p] & 0xff));
                fixed++;
            }
        }
        if (fixed <= 0) throw new IllegalArgumentException("Mask must leave at least one fixed byte.");
        if (wildcard <= 0) throw new IllegalArgumentException("No middle gap was found between readable text blocks.");
        return out.toString();
    }

    private String buildKeepReadableEdgesMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        ArrayList<ReadableRun> runs = readableRuns(bytes, 3);
        if (runs.size() < 2) {
            throw new IllegalArgumentException("Need at least two readable text blocks to keep readable edges.");
        }
        ReadableRun first = runs.get(0);
        ReadableRun last = runs.get(runs.size() - 1);
        StringBuilder out = wildcardPattern(bytes.length);
        writeFixedRun(out, bytes, first);
        writeFixedRun(out, bytes, last);
        return out.toString();
    }

    private String buildKeepFirstReadableRunMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        ArrayList<ReadableRun> runs = readableRuns(bytes, 3);
        if (runs.isEmpty()) throw new IllegalArgumentException("No readable text block was found.");
        StringBuilder out = wildcardPattern(bytes.length);
        writeFixedRun(out, bytes, runs.get(0));
        return out.toString();
    }

    private String buildKeepLastReadableRunMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        ArrayList<ReadableRun> runs = readableRuns(bytes, 3);
        if (runs.isEmpty()) throw new IllegalArgumentException("No readable text block was found.");
        StringBuilder out = wildcardPattern(bytes.length);
        writeFixedRun(out, bytes, runs.get(runs.size() - 1));
        return out.toString();
    }

    private static final class ReadableRun {
        final int start;
        final int end;

        ReadableRun(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private ArrayList<ReadableRun> readableRuns(byte[] bytes, int minLength) {
        ArrayList<ReadableRun> runs = new ArrayList<>();
        int i = 0;
        while (bytes != null && i < bytes.length) {
            while (i < bytes.length && !isPrintableAscii(bytes[i] & 0xff)) i++;
            int start = i;
            while (i < bytes.length && isPrintableAscii(bytes[i] & 0xff)) i++;
            if (i - start >= minLength) runs.add(new ReadableRun(start, i));
        }
        return runs;
    }

    private StringBuilder wildcardPattern(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) out.append('?');
        return out;
    }

    private void writeFixedRun(StringBuilder pattern, byte[] bytes, ReadableRun run) {
        if (pattern == null || bytes == null || run == null) return;
        for (int i = Math.max(0, run.start); i < run.end && i < bytes.length && i < pattern.length(); i++) {
            pattern.setCharAt(i, patternFixedChar(bytes[i] & 0xff));
        }
    }

    private String buildPrefixSeparatorMaskPattern(String hex, String separator) {
        byte[] bytes = hexToBytes(hex);
        String ascii = bytesToEditorAscii(bytes);
        int idx = ascii.indexOf(separator);
        if (idx < 0) throw new IllegalArgumentException("Separator not found: " + separator);
        int keepThrough = idx + separator.length();
        StringBuilder out = new StringBuilder(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            out.append(i < keepThrough ? patternFixedChar(bytes[i] & 0xff) : '?');
        }
        if (keepThrough <= 0 || keepThrough >= bytes.length) {
            throw new IllegalArgumentException("Preset would not leave both fixed and wildcard bytes.");
        }
        return out.toString();
    }

    private String maskPatternToHex(String pattern, int byteCount) {
        String value = pattern == null ? "" : pattern;
        if (value.length() != byteCount) {
            throw new IllegalArgumentException("Mask pattern is " + MemoryHexPayloadStore.formatPayloadLength(value.length())
                    + " but payload is " + MemoryHexPayloadStore.formatPayloadLength(byteCount) + ".");
        }
        StringBuilder out = new StringBuilder(byteCount * 2);
        int fixed = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c > 0x7e) throw new IllegalArgumentException("Mask pattern must use one character per byte.");
            boolean wildcard = c == 'X' || c == 'x' || c == '?';
            out.append(wildcard ? "00" : "FF");
            if (!wildcard) fixed++;
        }
        if (fixed <= 0) throw new IllegalArgumentException("Mask must leave at least one fixed byte.");
        return out.toString();
    }

    private String applyAsciiEditsToHex(String existingHex, String editedAscii, String label) {
        String text = editedAscii == null ? "" : editedAscii;
        if (TextUtils.isEmpty(existingHex)) return asciiToHex(text);
        byte[] bytes = hexToBytes(existingHex);
        if (text.length() != bytes.length) {
            throw new IllegalArgumentException(label + " is " + MemoryHexPayloadStore.formatPayloadLength(text.length())
                    + " but the hex field is " + MemoryHexPayloadStore.formatPayloadLength(bytes.length)
                    + ". Keep ASCII edits fixed-length or edit hex directly.");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                throw new IllegalArgumentException(label + " must use single-byte printable ASCII characters.");
            }
            int original = bytes[i] & 0xff;
            if (c == '.' && !isPrintableAscii(original)) {
                continue;
            }
            bytes[i] = (byte) c;
        }
        return bytesToHex(bytes);
    }

    private byte[] hexToBytes(String hex) {
        String clean = MemoryHexPayloadStore.normalizeAnyHexBytes(hex);
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v < 0x10) sb.append('0');
            sb.append(Integer.toHexString(v).toUpperCase(Locale.US));
        }
        return sb.toString();
    }

    private String bytesToEditorAscii(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes == null ? 0 : bytes.length);
        if (bytes == null) return "";
        for (byte b : bytes) {
            int v = b & 0xff;
            sb.append(isPrintableAscii(v) ? (char) v : '.');
        }
        return sb.toString();
    }

    private boolean isPrintableAscii(int value) {
        return value >= 32 && value <= 126;
    }

    private char patternFixedChar(int value) {
        if (isPrintableAscii(value)) {
            char c = (char) value;
            return (c == 'X' || c == 'x' || c == '?') ? 'F' : c;
        }
        return 'F';
    }

    private String asciiToHex(String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v < 0x10) sb.append('0');
            sb.append(Integer.toHexString(v).toUpperCase(Locale.US));
        }
        return sb.toString();
    }

    private String stampNow() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private String safeMessage(Throwable t) {
        String msg = t == null ? "" : t.getMessage();
        return TextUtils.isEmpty(msg) ? String.valueOf(t) : msg;
    }

    private String nonNullTrim(String value) {
        return value == null ? "" : value.trim();
    }

}
