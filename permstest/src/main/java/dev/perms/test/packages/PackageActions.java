package dev.perms.test.packages;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.DropdownUi;

/** Activity-side package command actions for the Packages tab. */
public final class PackageActions {
    public interface ShellCaptureCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface Host {
        boolean isReadyAndGranted();
        boolean isSafeToken(String value);
        void refreshStatus();
        void appendOutput(String text);
        void setLastOutputTag(String tag);
        void runShellCommand(String command);
        void runShellCommandCapture(String command, ShellCaptureCallback callback);
        int findPackageDropdownIndex(String packageName);
        String getPackageFilterText();
        void refreshAfterUninstall(int removedIndex, String filterText);
        void refreshEnabledStateForPackage(String packageName);
        void refreshPackageDropdown();
        void refreshHomeAppTray();
        void extractInstalledPackage(String packageName, String displayName);
    }

    private static final String PUBLIC_EXTRACTED_APKS_DIR = "/sdcard/dev.perms.test/extracted_apks";
    private static final String DEBLOAT_PRESET_DUMMY_LABEL = "Select Preset";

    private final Context context;
    private final ActivityMainBinding binding;
    private final Host host;
    private final ArrayList<PackageStateRow> packageStateRows = new ArrayList<>();
    private final ArrayList<PackageDebloatPresets.Preset> debloatPresets = new ArrayList<>();

    public PackageActions(Context context, ActivityMainBinding binding, Host host) {
        this.context = context;
        this.binding = binding;
        this.host = host;
    }

    public void bindPackageToolButtons() {
        try {
            if (binding == null || binding.tabPackages == null) return;
            if (binding.tabPackages.btnGrantPerm != null) binding.tabPackages.btnGrantPerm.setOnClickListener(v -> runPmGrantRevoke(true));
            if (binding.tabPackages.btnRevokePerm != null) binding.tabPackages.btnRevokePerm.setOnClickListener(v -> runPmGrantRevoke(false));
            if (binding.tabPackages.btnPmEnable != null) binding.tabPackages.btnPmEnable.setOnClickListener(v -> runPmEnableDisable(true));
            if (binding.tabPackages.btnPmDisable != null) binding.tabPackages.btnPmDisable.setOnClickListener(v -> runPmEnableDisable(false));
            if (binding.tabPackages.btnSavePackageStateList != null) binding.tabPackages.btnSavePackageStateList.setOnClickListener(v -> savePackageStateList());
            if (binding.tabPackages.btnLoadPackageStateList != null) binding.tabPackages.btnLoadPackageStateList.setOnClickListener(v -> loadPackageStateList());
            if (binding.tabPackages.btnEnablePackageStateList != null) binding.tabPackages.btnEnablePackageStateList.setOnClickListener(v -> applyPackageStateList(true));
            if (binding.tabPackages.btnDisablePackageStateList != null) binding.tabPackages.btnDisablePackageStateList.setOnClickListener(v -> applyPackageStateList(false));
            if (binding.tabPackages.btnSelectAllPackageStateList != null) binding.tabPackages.btnSelectAllPackageStateList.setOnClickListener(v -> setPackageStateListSelection(true));
            if (binding.tabPackages.btnSelectNonePackageStateList != null) binding.tabPackages.btnSelectNonePackageStateList.setOnClickListener(v -> setPackageStateListSelection(false));
            if (binding.tabPackages.btnSelectEnabledPackageStateList != null) binding.tabPackages.btnSelectEnabledPackageStateList.setOnClickListener(v -> setPackageStateListSelectionBySavedState(true));
            if (binding.tabPackages.btnSelectDisabledPackageStateList != null) binding.tabPackages.btnSelectDisabledPackageStateList.setOnClickListener(v -> setPackageStateListSelectionBySavedState(false));
            bindDebloatPresetControls();
            if (binding.tabPackages.btnLaunchPkg != null) binding.tabPackages.btnLaunchPkg.setOnClickListener(v -> launchTargetPackage());
            if (binding.tabPackages.btnUninstallPkg != null) binding.tabPackages.btnUninstallPkg.setOnClickListener(v -> uninstallTargetPackage());
            if (binding.tabPackages.btnInfoPkg != null) binding.tabPackages.btnInfoPkg.setOnClickListener(v -> openTargetPackageInfo());
            if (binding.tabPackages.btnExtractPkg != null) binding.tabPackages.btnExtractPkg.setOnClickListener(v -> extractTargetPackage());
        } catch (Throwable ignored) {
        }
    }


    private void bindDebloatPresetControls() {
        try {
            if (binding == null || binding.tabPackages == null) return;
            debloatPresets.clear();
            debloatPresets.addAll(PackageDebloatPresets.list(context));
            ArrayList<String> labels = new ArrayList<>();
            labels.add(DEBLOAT_PRESET_DUMMY_LABEL);
            for (PackageDebloatPresets.Preset preset : debloatPresets) {
                if (preset != null && !TextUtils.isEmpty(preset.label)) labels.add(preset.label);
            }
            if (binding.tabPackages.ddPackageDebloatPreset != null) {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, labels);
                binding.tabPackages.ddPackageDebloatPreset.setAdapter(adapter);
                if (TextUtils.isEmpty(binding.tabPackages.ddPackageDebloatPreset.getText())) {
                    binding.tabPackages.ddPackageDebloatPreset.setText(DEBLOAT_PRESET_DUMMY_LABEL, false);
                }
                try {
                    DropdownUi.bindExposedDropdown(context,
                            binding.tabPackages.tilPackageDebloatPreset,
                            binding.tabPackages.ddPackageDebloatPreset,
                            () -> DropdownUi.showDropdown(binding.tabPackages.ddPackageDebloatPreset));
                } catch (Throwable ignored) {
                }
            }
            if (binding.tabPackages.btnLoadPackageDebloatPreset != null) {
                binding.tabPackages.btnLoadPackageDebloatPreset.setOnClickListener(v -> loadSelectedDebloatPreset());
            }
        } catch (Throwable t) {
            host.appendOutput("[Packages] Failed to bind debloat presets: " + t + "\n");
        }
    }

    private void loadSelectedDebloatPreset() {
        if (debloatPresets.isEmpty()) {
            Toast.makeText(context, "No bundled debloat presets found", Toast.LENGTH_SHORT).show();
            host.appendOutput("[Packages] No bundled debloat presets found under assets/package_states.\n");
            return;
        }
        PackageDebloatPresets.Preset preset = findSelectedDebloatPreset();
        if (preset == null) {
            Toast.makeText(context, "Select a debloat preset first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String json = PackageDebloatPresets.readJson(context, preset);
            int count = populatePackageStateList(json, PackageStateListCommands.StateFilter.ALL);
            int selected = selectLoadedPackageStateRows(true);
            host.setLastOutputTag("load_debloat_preset");
            host.appendOutput("[Packages] Loaded bundled debloat preset: " + preset.label
                    + " (" + preset.assetPath + ")\n");
            host.appendOutput("[Packages] Debloat preset entries loaded: " + count
                    + "; selected for review: " + selected + "\n");
            host.appendOutput("[Packages] Review/uncheck entries, then use Disable Packages to apply.\n");
            Toast.makeText(context, "Debloat preset loaded for review", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            host.appendOutput("[Packages] Failed to load bundled debloat preset: " + t + "\n");
            Toast.makeText(context, "Load debloat preset failed", Toast.LENGTH_SHORT).show();
        }
    }

    private PackageDebloatPresets.Preset findSelectedDebloatPreset() {
        String selected = "";
        try {
            selected = binding.tabPackages.ddPackageDebloatPreset == null
                    ? ""
                    : String.valueOf(binding.tabPackages.ddPackageDebloatPreset.getText()).trim();
        } catch (Throwable ignored) {
        }
        if (TextUtils.isEmpty(selected) || DEBLOAT_PRESET_DUMMY_LABEL.equals(selected)) return null;
        for (PackageDebloatPresets.Preset preset : debloatPresets) {
            if (preset != null && preset.label.equals(selected)) return preset;
        }
        return null;
    }

    public void launchTargetPackage() {
        String pkg = getTargetPackage();
        if (!isValidPackage(pkg)) return;

        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) {
                host.appendOutput("[!] No launchable activity for: " + pkg + "\n");
                return;
            }
            context.startActivity(intent);
            host.appendOutput("[+] Launched: " + pkg + "\n");
        } catch (Throwable t) {
            host.appendOutput("[!] Launch failed: " + t + "\n");
        }
    }

    public void uninstallTargetPackage() {
        final String pkg = getTargetPackage();
        if (!isValidPackage(pkg)) return;

        final int removedIndex = host.findPackageDropdownIndex(pkg);
        final String filterText = host.getPackageFilterText();

        new MaterialAlertDialogBuilder(context)
                .setTitle("Uninstall")
                .setMessage("Uninstall " + pkg + " ?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    String cmd = "pm uninstall " + pkg;
                    host.appendOutput("$ " + cmd + "\n");

                    host.runShellCommandCapture(cmd, (exitCode, stdout, stderr) -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append("exit=").append(exitCode).append("\n");
                        appendIfPresent(sb, stdout, null);
                        appendIfPresent(sb, stderr, null);
                        host.appendOutput(sb.toString());

                        if (exitCode == 0) {
                            host.refreshAfterUninstall(removedIndex, filterText);
                            Toast.makeText(context, "Uninstalled: " + pkg, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Uninstall failed: " + pkg, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("No", null)
                .show();
    }

    public void openTargetPackageInfo() {
        final String pkg = getTargetPackage();
        if (!isValidPackage(pkg)) return;

        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            context.startActivity(intent);
        } catch (Throwable t) {
            try {
                context.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
            } catch (Throwable ignored) {
                Toast.makeText(context, "Unable to open app info.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void extractTargetPackage() {
        try {
            final String pkg = getTargetPackage();
            if (!isValidPackage(pkg)) return;
            showExtractPackageDialog(pkg, pkg);
        } catch (Throwable t) {
            host.appendOutput("[Extract] Failed to start package extraction: " + t + "\n");
        }
    }

    public void showExtractPackageDialog(String packageName, String label) {
        try {
            final String pkg = packageName == null ? "" : packageName.trim();
            if (!host.isSafeToken(pkg)) {
                host.appendOutput("[Extract] Invalid package name.\n");
                return;
            }
            final String display = TextUtils.isEmpty(label) ? pkg : label.trim();
            new MaterialAlertDialogBuilder(context)
                    .setTitle("Extract Package")
                    .setMessage("Extract installed APK files for " + display + " to:\n" + PUBLIC_EXTRACTED_APKS_DIR)
                    .setPositiveButton("Extract", (dialog, which) -> {
                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                        host.extractInstalledPackage(pkg, display);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Throwable t) {
            host.appendOutput("[Extract] Failed to show package extraction dialog: " + t + "\n");
        }
    }

    public void runPmGrantRevoke(boolean grant) {
        if (!ensureBackendReady()) return;

        String pkg = getTargetPackage();
        String perm = getPermissionText();

        if (!isValidPackage(pkg)) return;
        if (!host.isSafeToken(perm)) {
            host.appendOutput("[!] Invalid permission name (example: android.permission.READ_LOGS).\n");
            return;
        }

        host.runShellCommand((grant ? "pm grant " : "pm revoke ") + pkg + " " + perm);
    }

    public void runPmEnableDisable(boolean enable) {
        if (!ensureBackendReady()) return;

        String pkg = getTargetPackage();
        if (!isValidPackage(pkg)) return;

        final String cmd = enable ? ("pm enable " + pkg) : ("pm disable-user --user 0 " + pkg);
        final String trimmed = cmd.trim();
        host.setLastOutputTag("shell");
        host.appendOutput("$ " + trimmed + "\n");

        final String pkgFinal = pkg;
        host.runShellCommandCapture(trimmed, (exit, out, err) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("exit=").append(exit).append("\n");
            appendIfPresent(sb, out, null);
            appendIfPresent(sb, err, "--- stderr ---\n");
            host.appendOutput(sb.toString());
            host.refreshEnabledStateForPackage(pkgFinal);
        });
    }

    public void savePackageStateList() {
        if (!ensureBackendReady()) return;

        final String path = PackageStateListCommands.statePath();
        final PackageStateListCommands.StateFilter filter = getPackageStateListFilter();
        host.setLastOutputTag("save_package_state_list");
        host.appendOutput("[Packages] Saving " + filter.jsonName() + " package state list to " + path + "\n");
        host.runShellCommandCapture(PackageStateListCommands.saveCommand(context.getPackageName(), filter), (exit, out, err) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[Packages] Save package state list exit=").append(exit).append("\n");
            appendIfPresent(sb, out, null);
            appendIfPresent(sb, err, null);
            host.appendOutput(sb.toString());
            Toast.makeText(context, exit == 0 ? "Package state list saved" : "Save package state list failed", Toast.LENGTH_SHORT).show();
        });
    }

    public void loadPackageStateList() {
        if (!ensureBackendReady()) return;

        final String path = PackageStateListCommands.statePath();
        final PackageStateListCommands.StateFilter filter = getPackageStateListFilter();
        host.setLastOutputTag("load_package_state_list");
        host.appendOutput("[Packages] Loading " + filter.jsonName() + " package state entries from " + path + "\n");
        host.runShellCommandCapture(PackageStateListCommands.readCommand(), (exit, out, err) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[Packages] Load package state list exit=").append(exit).append("\n");
            appendIfPresent(sb, err, null);
            host.appendOutput(sb.toString());
            if (exit == 0 && !TextUtils.isEmpty(out)) {
                int count = populatePackageStateList(out, filter);
                host.appendOutput("[Packages] " + filter.jsonName() + " package state entries loaded for review: " + count + "\n");
                Toast.makeText(context, "Package state list loaded", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Load package state list failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void applyPackageStateList(boolean enable) {
        if (!ensureBackendReady()) return;

        List<String> packages = collectSelectedPackageStateTargets();
        if (packages.isEmpty()) {
            Toast.makeText(context, enable ? "No selected packages to enable" : "No selected packages to disable", Toast.LENGTH_SHORT).show();
            host.appendOutput(enable
                    ? "[Packages] No selected package-state entries to enable.\n"
                    : "[Packages] No selected package-state entries to disable.\n");
            return;
        }

        final String label = enable ? "Enable" : "Disable";
        new MaterialAlertDialogBuilder(context)
                .setTitle(label + " Packages")
                .setMessage(label + " " + packages.size() + " selected packages from the loaded package state list?")
                .setPositiveButton(label, (dialog, which) -> runPackageStateApply(packages, enable))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runPackageStateApply(List<String> packages, boolean enable) {
        final String action = enable ? "enable_package_state_list" : "disable_package_state_list";
        host.setLastOutputTag(action);
        host.appendOutput("[Packages] " + (enable ? "Enabling" : "Disabling")
                + " " + packages.size() + " selected package state entries.\n");
        host.runShellCommandCapture(PackageStateListCommands.applyCommand(packages, enable, context.getPackageName()), (exit, out, err) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[Packages] Apply package state list exit=").append(exit).append("\n");
            appendIfPresent(sb, out, null);
            appendIfPresent(sb, err, null);
            host.appendOutput(sb.toString());
            Toast.makeText(context, exit == 0 ? "Package state action complete" : "Package state action finished with errors", Toast.LENGTH_SHORT).show();
            host.refreshPackageDropdown();
            host.refreshHomeAppTray();
        });
    }

    private int populatePackageStateList(String jsonText, PackageStateListCommands.StateFilter filter) {
        LinearLayout list = binding == null || binding.tabPackages == null ? null : binding.tabPackages.layoutPackageStateList;
        if (list == null) return 0;
        packageStateRows.clear();
        list.removeAllViews();
        try {
            JSONObject root = new JSONObject(jsonText);
            JSONArray packages = root.optJSONArray("packages");
            if (packages == null) return 0;
            for (int i = 0; i < packages.length(); i++) {
                Object item = packages.opt(i);
                String pkg = "";
                boolean enabled = false;
                String enabledState = "";
                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    pkg = obj.optString("package", "").trim();
                    enabled = obj.optBoolean("enabled", false);
                    enabledState = obj.optString("enabledState", "").trim();
                }
                if (!host.isSafeToken(pkg)) continue;
                if (TextUtils.isEmpty(enabledState)) {
                    enabledState = enabled ? "enabled" : "disabled";
                }
                if (filter == PackageStateListCommands.StateFilter.ENABLED && !enabled) continue;
                if (filter == PackageStateListCommands.StateFilter.DISABLED && enabled) continue;
                MaterialCheckBox checkBox = new MaterialCheckBox(context);
                checkBox.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                checkBox.setText(pkg + "  (" + packageStateLabel(enabled, enabledState) + ")");
                checkBox.setSingleLine(false);
                checkBox.setChecked(false);
                try {
                    checkBox.setTextColor(ContextCompat.getColor(context,
                            packageStateColorRes(enabled, enabledState)));
                } catch (Throwable ignored) {
                }
                checkBox.setPadding(0, 2, 0, 2);
                list.addView(checkBox);
                packageStateRows.add(new PackageStateRow(pkg, enabled, enabledState, checkBox));
            }
        } catch (Throwable t) {
            host.appendOutput("[Packages] Failed to parse package state list: " + t + "\n");
        }
        int count = packageStateRows.size();
        if (count == 0) {
            MaterialCheckBox empty = new MaterialCheckBox(context);
            empty.setText("No package entries loaded.");
            empty.setEnabled(false);
            list.addView(empty);
        }
        return count;
    }

    private PackageStateListCommands.StateFilter getPackageStateListFilter() {
        try {
            int checkedId = binding.tabPackages.rgrpPackageStateListFilter == null
                    ? 0
                    : binding.tabPackages.rgrpPackageStateListFilter.getCheckedRadioButtonId();
            if (checkedId == binding.tabPackages.rdoPackageStateListEnabled.getId()) {
                return PackageStateListCommands.StateFilter.ENABLED;
            }
            if (checkedId == binding.tabPackages.rdoPackageStateListDisabled.getId()) {
                return PackageStateListCommands.StateFilter.DISABLED;
            }
        } catch (Throwable ignored) {
        }
        return PackageStateListCommands.StateFilter.ALL;
    }

    private List<String> collectSelectedPackageStateTargets() {
        ArrayList<String> out = new ArrayList<>();
        for (PackageStateRow row : packageStateRows) {
            if (row == null || row.checkBox == null) continue;
            if (row.checkBox.isChecked() && host.isSafeToken(row.packageName)) {
                out.add(row.packageName);
            }
        }
        return out;
    }

    private void setPackageStateListSelection(boolean checked) {
        if (packageStateRows.isEmpty()) {
            Toast.makeText(context, "Load a package state list first", Toast.LENGTH_SHORT).show();
            return;
        }
        int count = selectLoadedPackageStateRows(checked);
        host.appendOutput("[Packages] Package state list selection set to "
                + (checked ? "all" : "none") + " for " + count + " entries.\n");
        Toast.makeText(context, checked ? "Selected all package rows" : "Cleared all package rows", Toast.LENGTH_SHORT).show();
    }

    private int selectLoadedPackageStateRows(boolean checked) {
        int count = 0;
        for (PackageStateRow row : packageStateRows) {
            if (row == null || row.checkBox == null) continue;
            row.checkBox.setChecked(checked);
            count++;
        }
        return count;
    }

    private void setPackageStateListSelectionBySavedState(boolean enabledState) {
        if (packageStateRows.isEmpty()) {
            Toast.makeText(context, "Load a package state list first", Toast.LENGTH_SHORT).show();
            return;
        }
        int selected = 0;
        int total = 0;
        for (PackageStateRow row : packageStateRows) {
            if (row == null || row.checkBox == null) continue;
            boolean match = row.enabled == enabledState;
            row.checkBox.setChecked(match);
            if (match) selected++;
            total++;
        }
        host.appendOutput("[Packages] Package state list selected "
                + (enabledState ? "enabled" : "disabled") + " rows: " + selected + " of " + total + ".\n");
        Toast.makeText(context,
                enabledState ? "Selected enabled package rows" : "Selected disabled package rows",
                Toast.LENGTH_SHORT).show();
    }

    private boolean ensureBackendReady() {
        if (host.isReadyAndGranted()) return true;
        host.refreshStatus();
        host.appendOutput("[!] Shizuku not ready or permission not granted.\n");
        return false;
    }

    private boolean isValidPackage(String pkg) {
        if (host.isSafeToken(pkg)) return true;
        host.appendOutput("[!] Invalid package name.\n");
        return false;
    }

    private String getTargetPackage() {
        try {
            return binding.tabPackages.edtTargetPkg.getText() == null
                    ? ""
                    : binding.tabPackages.edtTargetPkg.getText().toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getPermissionText() {
        try {
            return binding.tabPackages.edtPermission.getText() == null
                    ? ""
                    : binding.tabPackages.edtPermission.getText().toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void appendIfPresent(StringBuilder sb, String text, String header) {
        if (TextUtils.isEmpty(text)) return;
        if (!TextUtils.isEmpty(header)) sb.append(header);
        sb.append(text);
        if (!text.endsWith("\n")) sb.append("\n");
    }

    private static String packageStateLabel(boolean enabled, String enabledState) {
        String state = enabledState == null ? "" : enabledState.trim();
        if ("disabled_user".equals(state)) return "Disabled by user";
        if ("disabled".equals(state)) return "Disabled";
        if ("disabled_until_used".equals(state)) return "Disabled until used";
        if ("default_disabled".equals(state)) return "Default disabled";
        if ("pm_list_disabled".equals(state)) return "PM listed disabled";
        if ("enabled".equals(state)) return "Enabled";
        if ("default_enabled".equals(state)) return "Default enabled";
        if ("unknown_enabled".equals(state)) return "Enabled (fallback)";
        return enabled ? "Enabled" : "Disabled";
    }

    private static int packageStateColorRes(boolean enabled, String enabledState) {
        String state = enabledState == null ? "" : enabledState.trim();
        if ("disabled_until_used".equals(state) || "default_disabled".equals(state) || "pm_list_disabled".equals(state)) {
            return android.R.color.holo_orange_light;
        }
        return enabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
    }

    private static final class PackageStateRow {
        final String packageName;
        final boolean enabled;
        final String enabledState;
        final MaterialCheckBox checkBox;

        PackageStateRow(String packageName, boolean enabled, String enabledState, MaterialCheckBox checkBox) {
            this.packageName = packageName;
            this.enabled = enabled;
            this.enabledState = enabledState;
            this.checkBox = checkBox;
        }
    }

}
