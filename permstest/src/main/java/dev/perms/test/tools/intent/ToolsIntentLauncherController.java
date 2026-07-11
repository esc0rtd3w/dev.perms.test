package dev.perms.test.tools.intent;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.databinding.TabToolsBinding;
import dev.perms.test.ui.PackageDropdownAdapter;
import dev.perms.test.ui.PackageDropdownEntry;
import dev.perms.test.ui.PackageDropdownUi;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.NoFilterArrayAdapter;

/** Tools-tab intent builder/launcher with reusable saved launch configs. */
public final class ToolsIntentLauncherController {
    private static final String PREF_HISTORY = "tools_intent_launcher_history_json";
    private static final int MAX_HISTORY = 25;
    private static final String MANUAL_PACKAGE_LABEL = "Type custom package...";
    private static final String MANUAL_CLASS_LABEL = "Type custom class...";
    private static final String CUSTOM_ACTION_LABEL = "Type custom action...";
    private static final String CUSTOM_DATA_LABEL = "Type custom data URI...";
    private static final String CUSTOM_CATEGORY_LABEL = "Type custom categories...";
    private static final String CUSTOM_MIME_LABEL = "Type custom MIME type...";

    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        SharedPreferences getSharedPreferences();
        void appendOutput(String message);
        void runShellCommandCapture(String command, ShellCallback callback);
    }

    private static final String[] ACTION_PRESETS = new String[] {
            "",
            Intent.ACTION_MAIN,
            Intent.ACTION_VIEW,
            Intent.ACTION_SEND,
            Intent.ACTION_SENDTO,
            Intent.ACTION_DIAL,
            Intent.ACTION_SEARCH,
            Intent.ACTION_WEB_SEARCH,
            android.provider.Settings.ACTION_SETTINGS,
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            CUSTOM_ACTION_LABEL
    };

    private static final String[] DATA_URI_BASE_PRESETS = new String[] {
            "",
            "https://",
            "http://",
            "market://details?id=",
            "package:",
            "content://",
            "file://",
            "tel:",
            "sms:",
            "mailto:",
            "geo:0,0?q=",
            CUSTOM_DATA_LABEL
    };

    private static final String[] CATEGORY_PRESETS = new String[] {
            "",
            Intent.CATEGORY_DEFAULT,
            Intent.CATEGORY_BROWSABLE,
            Intent.CATEGORY_LAUNCHER,
            Intent.CATEGORY_HOME,
            Intent.CATEGORY_ALTERNATIVE,
            Intent.CATEGORY_SELECTED_ALTERNATIVE,
            Intent.CATEGORY_APP_BROWSER,
            Intent.CATEGORY_APP_EMAIL,
            Intent.CATEGORY_APP_GALLERY,
            Intent.CATEGORY_APP_MAPS,
            Intent.CATEGORY_APP_MARKET,
            Intent.CATEGORY_APP_MESSAGING,
            Intent.CATEGORY_APP_MUSIC,
            Intent.CATEGORY_DEFAULT + "," + Intent.CATEGORY_BROWSABLE,
            CUSTOM_CATEGORY_LABEL
    };

    private static final String[] MIME_PRESETS = new String[] {
            "",
            "text/plain",
            "text/html",
            "application/json",
            "application/octet-stream",
            "image/*",
            "video/*",
            "audio/*",
            "*/*",
            CUSTOM_MIME_LABEL
    };

    private static final Map<String, Integer> FLAG_PRESETS = new LinkedHashMap<>();

    static {
        FLAG_PRESETS.put("NEW_TASK", Intent.FLAG_ACTIVITY_NEW_TASK);
        FLAG_PRESETS.put("CLEAR_TOP", Intent.FLAG_ACTIVITY_CLEAR_TOP);
        FLAG_PRESETS.put("SINGLE_TOP", Intent.FLAG_ACTIVITY_SINGLE_TOP);
        FLAG_PRESETS.put("NO_HISTORY", Intent.FLAG_ACTIVITY_NO_HISTORY);
        FLAG_PRESETS.put("EXCLUDE_FROM_RECENTS", Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        FLAG_PRESETS.put("RESET_TASK_IF_NEEDED", Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        FLAG_PRESETS.put("CLEAR_TASK", Intent.FLAG_ACTIVITY_CLEAR_TASK);
        FLAG_PRESETS.put("MULTIPLE_TASK", Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        FLAG_PRESETS.put("GRANT_READ_URI_PERMISSION", Intent.FLAG_GRANT_READ_URI_PERMISSION);
        FLAG_PRESETS.put("GRANT_WRITE_URI_PERMISSION", Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    private final Host host;
    private final List<SavedIntent> savedIntents = new ArrayList<>();
    private final ArrayList<SavedIntentDropdownEntry> savedIntentItems = new ArrayList<>();
    private final ArrayList<PackageDropdownEntry> packageItems = new ArrayList<>();
    private final ArrayList<String> classItems = new ArrayList<>();
    private PackageDropdownAdapter packageAdapter;
    private SavedIntentDropdownAdapter savedIntentAdapter;
    private ArrayAdapter<String> classAdapter;
    private ArrayAdapter<String> dataUriAdapter;
    private ArrayAdapter<String> categoryAdapter;
    private boolean packageLoadStarted;
    private boolean packageLoadRunning;
    private String currentClassPackage = "";

    public ToolsIntentLauncherController(Host host) {
        this.host = host;
    }

    public void bind() {
        TabToolsBinding b = toolsBinding();
        Activity activity = activity();
        if (b == null || activity == null) return;

        bindPackageDropdown(b);
        bindClassDropdown(b);
        bindDataUriDropdown(b);
        bindCategoryDropdown(b);
        bindDropdown(b.ddToolsIntentAction, ACTION_PRESETS, CUSTOM_ACTION_LABEL, "Custom action", value -> b.ddToolsIntentAction.setText(value, false));
        bindDropdown(b.ddToolsIntentMime, MIME_PRESETS, CUSTOM_MIME_LABEL, "Custom MIME type", value -> b.ddToolsIntentMime.setText(value, false));

        b.btnToolsIntentLaunch.setOnClickListener(v -> launchCurrent());
        b.btnToolsIntentClear.setOnClickListener(v -> clearFields());
        b.btnToolsIntentSave.setOnClickListener(v -> showSaveDialog());
        b.btnToolsIntentLoad.setOnClickListener(v -> loadSelectedSavedIntent());
        b.btnToolsIntentDelete.setOnClickListener(v -> confirmDeleteSelectedIntent());
        loadSavedIntents();
        refreshHistoryDropdown();
        setStatus("Pick a package/class, or use action/data fields. Long-press package, class, action, or MIME for custom text.");
    }

    private void bindPackageDropdown(TabToolsBinding b) {
        Activity activity = activity();
        if (activity == null || b == null) return;
        packageAdapter = new PackageDropdownAdapter(
                activity,
                packageItems,
                PackageDropdownUi.ColorMode.ENABLED_STATE,
                true,
                enabledPackageColor(activity),
                disabledPackageColor(activity));
        b.edtToolsIntentPackage.setAdapter(packageAdapter);
        DropdownUi.bindExposedDropdown(activity, b.tilToolsIntentPackage, b.edtToolsIntentPackage,
                () -> showPackageDropdown());
        b.edtToolsIntentPackage.setOnLongClickListener(v -> {
            showCustomValueDialog("Package name", text(b.edtToolsIntentPackage), value -> applyPackageText(value));
            return true;
        });
        b.edtToolsIntentPackage.setOnItemClickListener((parent, view, position, id) -> {
            Object obj = parent.getItemAtPosition(position);
            if (!(obj instanceof PackageDropdownEntry)) return;
            PackageDropdownEntry entry = (PackageDropdownEntry) obj;
            if (entry == null) return;
            if (TextUtils.isEmpty(entry.pkg) && MANUAL_PACKAGE_LABEL.equals(entry.label)) {
                b.edtToolsIntentPackage.setText("", false);
                showCustomValueDialog("Package name", "", value -> applyPackageText(value));
                return;
            }
            if (TextUtils.isEmpty(entry.pkg)) return;
            applyPackageText(entry.pkg);
        });
    }

    private void bindClassDropdown(TabToolsBinding b) {
        Activity activity = activity();
        if (activity == null || b == null) return;
        classAdapter = new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, classItems);
        b.edtToolsIntentClass.setAdapter(classAdapter);
        DropdownUi.bindExposedDropdown(activity, b.tilToolsIntentClass, b.edtToolsIntentClass,
                () -> showClassDropdown());
        b.edtToolsIntentClass.setOnLongClickListener(v -> {
            showCustomValueDialog("Class name", text(b.edtToolsIntentClass), value -> b.edtToolsIntentClass.setText(value, false));
            return true;
        });
        b.edtToolsIntentClass.setOnItemClickListener((parent, view, position, id) -> {
            Object obj = parent.getItemAtPosition(position);
            String value = obj == null ? "" : obj.toString();
            if (MANUAL_CLASS_LABEL.equals(value)) {
                b.edtToolsIntentClass.setText("", false);
                showCustomValueDialog("Class name", "", custom -> b.edtToolsIntentClass.setText(custom, false));
                return;
            }
            b.edtToolsIntentClass.setText(value, false);
        });
        setClassChoices(null, false);
    }

    private void bindDataUriDropdown(TabToolsBinding b) {
        Activity activity = activity();
        if (activity == null || b == null) return;
        dataUriAdapter = new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, buildDataUriChoices(text(b.edtToolsIntentPackage)));
        b.edtToolsIntentData.setAdapter(dataUriAdapter);
        DropdownUi.bindExposedDropdown(activity, b.tilToolsIntentData, b.edtToolsIntentData,
                () -> showDropdown(b.edtToolsIntentData));
        b.edtToolsIntentData.setOnLongClickListener(v -> {
            showCustomValueDialog("Data URI", text(b.edtToolsIntentData), value -> b.edtToolsIntentData.setText(value, false));
            return true;
        });
        b.edtToolsIntentData.setOnItemClickListener((parent, itemView, position, id) -> {
            Object obj = parent.getItemAtPosition(position);
            String value = obj == null ? "" : obj.toString();
            if (CUSTOM_DATA_LABEL.equals(value)) {
                b.edtToolsIntentData.setText("", false);
                showCustomValueDialog("Data URI", "", custom -> b.edtToolsIntentData.setText(custom, false));
            } else {
                b.edtToolsIntentData.setText(value, false);
            }
        });
    }

    private void bindCategoryDropdown(TabToolsBinding b) {
        Activity activity = activity();
        if (activity == null || b == null) return;
        categoryAdapter = new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, CATEGORY_PRESETS);
        b.edtToolsIntentCategories.setAdapter(categoryAdapter);
        DropdownUi.bindExposedDropdown(activity, b.tilToolsIntentCategories, b.edtToolsIntentCategories,
                () -> showDropdown(b.edtToolsIntentCategories));
        b.edtToolsIntentCategories.setOnLongClickListener(v -> {
            showCustomValueDialog("Categories", text(b.edtToolsIntentCategories), value -> b.edtToolsIntentCategories.setText(value, false));
            return true;
        });
        b.edtToolsIntentCategories.setOnItemClickListener((parent, itemView, position, id) -> {
            Object obj = parent.getItemAtPosition(position);
            String value = obj == null ? "" : obj.toString();
            if (CUSTOM_CATEGORY_LABEL.equals(value)) {
                b.edtToolsIntentCategories.setText("", false);
                showCustomValueDialog("Categories", "", custom -> b.edtToolsIntentCategories.setText(custom, false));
            } else {
                b.edtToolsIntentCategories.setText(value, false);
            }
        });
    }

    private void bindDropdown(MaterialAutoCompleteTextView view, String[] items, String customLabel, String title, ValueCallback callback) {
        Activity activity = activity();
        if (activity == null || view == null) return;
        view.setAdapter(new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, items));
        DropdownUi.bindExposedDropdown(activity, textInputLayoutFor(view), view,
                () -> showDropdown(view));
        view.setOnLongClickListener(v -> {
            showCustomValueDialog(title, text(view), callback);
            return true;
        });
        view.setOnItemClickListener((parent, itemView, position, id) -> {
            Object obj = parent.getItemAtPosition(position);
            String value = obj == null ? "" : obj.toString();
            if (customLabel.equals(value)) {
                view.setText("", false);
                showCustomValueDialog(title, "", callback);
            } else {
                view.setText(value, false);
            }
        });
    }

    private com.google.android.material.textfield.TextInputLayout textInputLayoutFor(MaterialAutoCompleteTextView view) {
        TabToolsBinding b = toolsBinding();
        if (b == null || view == null) return null;
        if (view == b.ddToolsIntentAction) return b.tilToolsIntentAction;
        if (view == b.ddToolsIntentMime) return b.tilToolsIntentMime;
        if (view == b.ddToolsIntentHistory) return b.tilToolsIntentHistory;
        if (view == b.edtToolsIntentData) return b.tilToolsIntentData;
        if (view == b.edtToolsIntentCategories) return b.tilToolsIntentCategories;
        if (view == b.edtToolsIntentPackage) return b.tilToolsIntentPackage;
        if (view == b.edtToolsIntentClass) return b.tilToolsIntentClass;
        return null;
    }

    private void showPackageDropdown() {
        TabToolsBinding b = toolsBinding();
        if (b == null) return;
        hideKeyboard(b.edtToolsIntentPackage);
        ensurePackageChoicesLoaded();
        showDropdown(b.edtToolsIntentPackage);
    }

    private void showClassDropdown() {
        TabToolsBinding b = toolsBinding();
        if (b == null) return;
        hideKeyboard(b.edtToolsIntentClass);
        String pkg = text(b.edtToolsIntentPackage);
        if (!TextUtils.isEmpty(pkg) && !pkg.equals(currentClassPackage)) {
            loadClassChoices(pkg, true);
        }
        showDropdown(b.edtToolsIntentClass);
    }

    private void showDropdown(MaterialAutoCompleteTextView view) {
        if (view == null) return;
        hideKeyboard(view);
        try { DropdownUi.showDropdown(view); } catch (Throwable ignored) {}
    }

    private void applyPackageText(String packageName) {
        TabToolsBinding b = toolsBinding();
        if (b == null) return;
        String pkg = packageName == null ? "" : packageName.trim();
        b.edtToolsIntentPackage.setText(pkg, false);
        refreshDataUriChoices(pkg);
        if (TextUtils.isEmpty(pkg)) {
            setClassChoices(null, true);
        } else {
            loadClassChoices(pkg, true);
        }
    }

    private void ensurePackageChoicesLoaded() {
        if (packageLoadStarted || packageLoadRunning) return;
        packageLoadStarted = true;
        packageLoadRunning = true;
        setStatus("Loading installed package list...");
        new Thread(() -> {
            final ArrayList<PackageDropdownEntry> loaded = loadInstalledPackages();
            Activity activity = activity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                packageLoadRunning = false;
                packageItems.clear();
                packageItems.add(new PackageDropdownEntry(MANUAL_PACKAGE_LABEL, "", true));
                packageItems.addAll(loaded);
                if (packageAdapter != null) packageAdapter.notifyDataSetChanged();
                setStatus("Loaded " + loaded.size() + " packages. Pick one or long-press Package name for custom text.");
                TabToolsBinding b = toolsBinding();
                if (b != null && b.edtToolsIntentPackage.hasFocus()) showDropdown(b.edtToolsIntentPackage);
            });
        }, "PermsTestIntentPackages").start();
    }

    private ArrayList<PackageDropdownEntry> loadInstalledPackages() {
        ArrayList<PackageDropdownEntry> entries = new ArrayList<>();
        try {
            Activity activity = activity();
            if (activity == null) return entries;
            PackageManager pm = activity.getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : installed) {
                if (ai == null || TextUtils.isEmpty(ai.packageName)) continue;
                String label;
                try {
                    CharSequence cs = pm.getApplicationLabel(ai);
                    label = cs == null ? ai.packageName : cs.toString();
                } catch (Throwable ignored) {
                    label = ai.packageName;
                }
                entries.add(new PackageDropdownEntry(label, ai.packageName, isAppEnabled(pm, ai)));
            }
        } catch (Throwable ignored) {
        }
        Collections.sort(entries, (a, b) -> safeLabel(a).compareToIgnoreCase(safeLabel(b)));
        return entries;
    }

    private void loadClassChoices(String packageName, boolean clearCurrentClass) {
        final String pkg = packageName == null ? "" : packageName.trim();
        currentClassPackage = pkg;
        if (TextUtils.isEmpty(pkg)) {
            setClassChoices(null, clearCurrentClass);
            return;
        }
        setStatus("Loading activities for " + pkg + "...");
        new Thread(() -> {
            final ArrayList<String> loaded = loadActivityClassNames(pkg);
            Activity activity = activity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!pkg.equals(currentClassPackage)) return;
                setClassChoices(loaded, clearCurrentClass);
                setStatus("Loaded " + loaded.size() + " activities for " + pkg + ".");
                TabToolsBinding b = toolsBinding();
                if (b != null && b.edtToolsIntentClass.hasFocus()) showDropdown(b.edtToolsIntentClass);
            });
        }, "PermsTestIntentClasses").start();
    }

    private ArrayList<String> loadActivityClassNames(String packageName) {
        ArrayList<String> names = new ArrayList<>();
        try {
            Activity activity = activity();
            if (activity == null) return names;
            PackageManager pm = activity.getPackageManager();
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            ActivityInfo[] activities = info == null ? null : info.activities;
            if (activities == null) return names;
            for (ActivityInfo ai : activities) {
                if (ai == null || TextUtils.isEmpty(ai.name)) continue;
                names.add(ai.name);
            }
        } catch (Throwable ignored) {
        }
        Collections.sort(names, String::compareToIgnoreCase);
        return names;
    }

    private void setClassChoices(List<String> names, boolean clearCurrentClass) {
        classItems.clear();
        classItems.add(MANUAL_CLASS_LABEL);
        if (names != null) classItems.addAll(names);
        if (classAdapter != null) classAdapter.notifyDataSetChanged();
        TabToolsBinding b = toolsBinding();
        if (clearCurrentClass && b != null) b.edtToolsIntentClass.setText("", false);
    }

    private void refreshDataUriChoices(String packageName) {
        if (dataUriAdapter == null) return;
        dataUriAdapter.clear();
        dataUriAdapter.addAll(buildDataUriChoices(packageName));
        dataUriAdapter.notifyDataSetChanged();
    }

    private List<String> buildDataUriChoices(String packageName) {
        ArrayList<String> items = new ArrayList<>();
        String pkg = packageName == null ? "" : packageName.trim();
        if (!TextUtils.isEmpty(pkg)) {
            items.add("package:" + pkg);
            items.add("market://details?id=" + pkg);
            items.add("https://play.google.com/store/apps/details?id=" + pkg);
        }
        for (String preset : DATA_URI_BASE_PRESETS) {
            if (!items.contains(preset)) items.add(preset);
        }
        return items;
    }

    private void launchCurrent() {
        Activity activity = activity();
        TabToolsBinding b = toolsBinding();
        if (activity == null || b == null) return;
        IntentSpec spec = readSpecFromUi();
        if (!spec.hasLaunchTarget()) {
            Toast.makeText(activity, "Add a component, action, or data URI first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (b.chkToolsIntentUseShizuku.isChecked()) {
            String command = buildAmStartCommand(spec);
            appendOutput("Intent Launcher Shizuku launch: " + command);
            runShell(command, "Intent Launcher Shizuku launch");
            setStatus("Running through Shizuku/shell...");
            return;
        }
        try {
            Intent intent = buildAndroidIntent(spec);
            activity.startActivity(intent);
            appendOutput("Intent Launcher launched: " + spec.summary());
            setStatus("Launch requested: " + spec.summary());
        } catch (ActivityNotFoundException e) {
            String message = safeMessage(e);
            Toast.makeText(activity, "No activity matched: " + message, Toast.LENGTH_LONG).show();
            appendOutput("Intent Launcher failed: " + spec.summary() + "\n" + message);
            setStatus("Launch failed: no matching activity.");
        } catch (Throwable t) {
            String message = safeMessage(t);
            Toast.makeText(activity, "Launch failed: " + message, Toast.LENGTH_LONG).show();
            appendOutput("Intent Launcher failed: " + spec.summary() + "\n" + message);
            setStatus("Launch failed: " + message);
        }
    }

    private Intent buildAndroidIntent(IntentSpec spec) {
        Intent intent = new Intent();
        if (!TextUtils.isEmpty(spec.action)) intent.setAction(spec.action);
        String className = normalizeClassName(spec.packageName, spec.className);
        if (!TextUtils.isEmpty(spec.packageName) && !TextUtils.isEmpty(className)) {
            intent.setComponent(new ComponentName(spec.packageName, className));
        } else if (!TextUtils.isEmpty(spec.packageName)) {
            intent.setPackage(spec.packageName);
        }
        if (!TextUtils.isEmpty(spec.data) && !TextUtils.isEmpty(spec.mimeType)) {
            intent.setDataAndType(Uri.parse(spec.data), spec.mimeType);
        } else if (!TextUtils.isEmpty(spec.data)) {
            intent.setData(Uri.parse(spec.data));
        } else if (!TextUtils.isEmpty(spec.mimeType)) {
            intent.setType(spec.mimeType);
        }
        for (String category : splitList(spec.categories)) {
            intent.addCategory(category);
        }
        int flags = parseFlags(spec.flags);
        if (flags != 0) intent.addFlags(flags);
        applyExtras(intent, spec.extras);
        return intent;
    }

    private String buildAmStartCommand(IntentSpec spec) {
        StringBuilder cmd = new StringBuilder("am start");
        String className = normalizeClassName(spec.packageName, spec.className);
        if (!TextUtils.isEmpty(spec.packageName) && !TextUtils.isEmpty(className)) {
            cmd.append(" -n ").append(shellQuote(spec.packageName + "/" + className));
        } else if (!TextUtils.isEmpty(spec.packageName)) {
            cmd.append(" -p ").append(shellQuote(spec.packageName));
        }
        if (!TextUtils.isEmpty(spec.action)) cmd.append(" -a ").append(shellQuote(spec.action));
        if (!TextUtils.isEmpty(spec.data)) cmd.append(" -d ").append(shellQuote(spec.data));
        if (!TextUtils.isEmpty(spec.mimeType)) cmd.append(" -t ").append(shellQuote(spec.mimeType));
        for (String category : splitList(spec.categories)) {
            cmd.append(" -c ").append(shellQuote(category));
        }
        int flags = parseFlags(spec.flags);
        if (flags != 0) cmd.append(" -f 0x").append(Integer.toHexString(flags));
        appendAmExtras(cmd, spec.extras);
        return cmd.toString();
    }

    private void runShell(String command, String label) {
        Host h = host;
        if (h == null) return;
        h.runShellCommandCapture(command, (exitCode, stdout, stderr) -> {
            StringBuilder out = new StringBuilder();
            out.append(label == null ? "Intent Launcher" : label).append(" exit=").append(exitCode);
            if (!TextUtils.isEmpty(stdout)) out.append("\nstdout: ").append(stdout.trim());
            if (!TextUtils.isEmpty(stderr)) out.append("\nstderr: ").append(stderr.trim());
            appendOutput(out.toString());
            setStatus("Shell launch finished with exit " + exitCode + ".");
        });
    }

    private void clearFields() {
        TabToolsBinding b = toolsBinding();
        if (b == null) return;
        b.edtToolsIntentPackage.setText("", false);
        b.edtToolsIntentClass.setText("", false);
        b.ddToolsIntentAction.setText("", false);
        setText(b.edtToolsIntentData, "");
        b.ddToolsIntentMime.setText("", false);
        setText(b.edtToolsIntentCategories, "");
        setText(b.edtToolsIntentFlags, "NEW_TASK");
        setText(b.edtToolsIntentExtras, "");
        currentClassPackage = "";
        refreshDataUriChoices("");
        setClassChoices(null, false);
        setStatus("Intent fields cleared.");
    }

    private void showSaveDialog() {
        Activity activity = activity();
        if (activity == null) return;
        IntentSpec spec = readSpecFromUi();
        if (!spec.hasLaunchTarget()) {
            Toast.makeText(activity, "Add something to save first.", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText nameField = new EditText(activity);
        nameField.setSingleLine(true);
        nameField.setText(defaultSaveName(spec));
        nameField.setSelectAllOnFocus(true);
        int pad = dp(16);
        nameField.setPadding(pad, dp(8), pad, dp(8));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Save Intent")
                .setMessage("Save this intent launcher config as a named preset.")
                .setView(nameField)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(activity, "Name is empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            saveIntent(name, spec);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void saveIntent(String name, IntentSpec spec) {
        for (int i = 0; i < savedIntents.size(); i++) {
            if (name.equals(savedIntents.get(i).name)) {
                savedIntents.remove(i);
                break;
            }
        }
        savedIntents.add(0, new SavedIntent(name, spec));
        while (savedIntents.size() > MAX_HISTORY) savedIntents.remove(savedIntents.size() - 1);
        persistSavedIntents();
        refreshHistoryDropdown();
        TabToolsBinding b = toolsBinding();
        if (b != null) b.ddToolsIntentHistory.setText(name, false);
        Toast.makeText(activity(), "Intent saved", Toast.LENGTH_SHORT).show();
        appendOutput("Intent Launcher saved preset: " + name);
        setStatus("Saved intent preset: " + name);
    }

    private void loadSelectedSavedIntent() {
        TabToolsBinding b = toolsBinding();
        if (b == null) return;
        String name = text(b.ddToolsIntentHistory);
        SavedIntent saved = findSaved(name);
        if (saved == null) {
            Toast.makeText(activity(), "Pick a saved intent first.", Toast.LENGTH_SHORT).show();
            return;
        }
        applySpecToUi(saved.spec);
        setStatus("Loaded intent preset: " + saved.name);
        appendOutput("Intent Launcher loaded preset: " + saved.name);
    }

    private void confirmDeleteSelectedIntent() {
        Activity activity = activity();
        TabToolsBinding b = toolsBinding();
        if (activity == null || b == null) return;
        String name = text(b.ddToolsIntentHistory);
        SavedIntent saved = findSaved(name);
        if (saved == null) {
            Toast.makeText(activity, "Pick a saved intent first.", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Delete Saved Intent")
                .setMessage("Delete \"" + saved.name + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    savedIntents.remove(saved);
                    persistSavedIntents();
                    refreshHistoryDropdown();
                    b.ddToolsIntentHistory.setText("", false);
                    setStatus("Deleted saved intent: " + saved.name);
                    appendOutput("Intent Launcher deleted preset: " + saved.name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applySpecToUi(IntentSpec spec) {
        TabToolsBinding b = toolsBinding();
        if (b == null || spec == null) return;
        b.edtToolsIntentPackage.setText(spec.packageName, false);
        refreshDataUriChoices(spec.packageName);
        b.edtToolsIntentClass.setText(spec.className, false);
        b.ddToolsIntentAction.setText(spec.action, false);
        setText(b.edtToolsIntentData, spec.data);
        b.ddToolsIntentMime.setText(spec.mimeType, false);
        setText(b.edtToolsIntentCategories, spec.categories);
        setText(b.edtToolsIntentFlags, spec.flags);
        setText(b.edtToolsIntentExtras, spec.extras);
        if (!TextUtils.isEmpty(spec.packageName)) loadClassChoices(spec.packageName, false);
    }

    private IntentSpec readSpecFromUi() {
        TabToolsBinding b = toolsBinding();
        IntentSpec spec = new IntentSpec();
        if (b == null) return spec;
        spec.packageName = text(b.edtToolsIntentPackage);
        spec.className = text(b.edtToolsIntentClass);
        spec.action = text(b.ddToolsIntentAction);
        spec.data = text(b.edtToolsIntentData);
        spec.mimeType = text(b.ddToolsIntentMime);
        spec.categories = text(b.edtToolsIntentCategories);
        spec.flags = text(b.edtToolsIntentFlags);
        spec.extras = text(b.edtToolsIntentExtras);
        return spec;
    }

    private void loadSavedIntents() {
        savedIntents.clear();
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        String raw = prefs.getString(PREF_HISTORY, "");
        if (TextUtils.isEmpty(raw)) return;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "").trim();
                if (TextUtils.isEmpty(name)) continue;
                IntentSpec spec = IntentSpec.fromJson(o.optJSONObject("intent"));
                savedIntents.add(new SavedIntent(name, spec));
            }
        } catch (Throwable ignored) {
            // Ignore corrupted local history; the next save will rewrite it.
        }
    }

    private void persistSavedIntents() {
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        JSONArray array = new JSONArray();
        for (SavedIntent saved : savedIntents) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", saved.name);
                o.put("intent", saved.spec.toJson());
                array.put(o);
            } catch (Throwable ignored) {}
        }
        prefs.edit().putString(PREF_HISTORY, array.toString()).apply();
    }

    private void refreshHistoryDropdown() {
        Activity activity = activity();
        TabToolsBinding b = toolsBinding();
        if (activity == null || b == null) return;
        savedIntentItems.clear();
        for (SavedIntent saved : savedIntents) {
            savedIntentItems.add(buildSavedIntentDropdownEntry(activity, saved));
        }
        if (savedIntentAdapter == null) {
            savedIntentAdapter = new SavedIntentDropdownAdapter(
                    activity,
                    savedIntentItems,
                    enabledPackageColor(activity),
                    disabledPackageColor(activity));
            b.ddToolsIntentHistory.setAdapter(savedIntentAdapter);
            DropdownUi.bindExposedDropdown(activity, b.tilToolsIntentHistory, b.ddToolsIntentHistory,
                    () -> showDropdown(b.ddToolsIntentHistory));
            b.ddToolsIntentHistory.setOnItemClickListener((parent, view, position, id) -> {
                Object obj = parent.getItemAtPosition(position);
                if (obj instanceof SavedIntentDropdownEntry) {
                    b.ddToolsIntentHistory.setText(((SavedIntentDropdownEntry) obj).name, false);
                }
                loadSelectedSavedIntent();
            });
        } else {
            savedIntentAdapter.notifyDataSetChanged();
        }
        b.btnToolsIntentLoad.setEnabled(!savedIntentItems.isEmpty());
        b.btnToolsIntentDelete.setEnabled(!savedIntentItems.isEmpty());
    }

    private SavedIntentDropdownEntry buildSavedIntentDropdownEntry(Activity activity, SavedIntent saved) {
        String name = saved == null ? "" : saved.name;
        IntentSpec spec = saved == null ? null : saved.spec;
        String summary = spec == null ? "" : spec.summary();
        String pkg = spec == null ? "" : spec.packageName;
        boolean hasPackage = !TextUtils.isEmpty(pkg);
        boolean enabled = !hasPackage || isPackageEnabled(activity, pkg);
        return new SavedIntentDropdownEntry(name, summary, hasPackage, enabled);
    }

    private static boolean isPackageEnabled(Activity activity, String packageName) {
        if (activity == null || TextUtils.isEmpty(packageName)) return true;
        try {
            PackageManager pm = activity.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return isAppEnabled(pm, ai);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int enabledPackageColor(Context context) {
        try { return ContextCompat.getColor(context, android.R.color.holo_green_dark); }
        catch (Throwable ignored) { return Color.rgb(0, 128, 0); }
    }

    private static int disabledPackageColor(Context context) {
        try { return ContextCompat.getColor(context, android.R.color.holo_red_dark); }
        catch (Throwable ignored) { return Color.rgb(192, 0, 0); }
    }

    private SavedIntent findSaved(String name) {
        if (TextUtils.isEmpty(name)) return null;
        for (SavedIntent saved : savedIntents) {
            if (name.equals(saved.name)) return saved;
        }
        return null;
    }

    private void showCustomValueDialog(String title, String initial, ValueCallback callback) {
        Activity activity = activity();
        if (activity == null || callback == null) return;
        final EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setText(initial == null ? "" : initial);
        field.setSelectAllOnFocus(true);
        int pad = dp(16);
        field.setPadding(pad, dp(8), pad, dp(8));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(field)
                .setPositiveButton("Apply", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = field.getText() == null ? "" : field.getText().toString().trim();
            callback.onValue(value);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static void applyExtras(Intent intent, String extrasText) {
        for (ExtraSpec extra : parseExtras(extrasText)) {
            String type = extra.type;
            String key = extra.key;
            String value = extra.value;
            try {
                if ("i".equals(type) || "int".equals(type)) intent.putExtra(key, Integer.parseInt(value.trim()));
                else if ("l".equals(type) || "long".equals(type)) intent.putExtra(key, Long.parseLong(value.trim()));
                else if ("f".equals(type) || "float".equals(type)) intent.putExtra(key, Float.parseFloat(value.trim()));
                else if ("b".equals(type) || "bool".equals(type) || "boolean".equals(type)) intent.putExtra(key, Boolean.parseBoolean(value.trim()));
                else intent.putExtra(key, value);
            } catch (Throwable ignored) {
                intent.putExtra(key, value);
            }
        }
    }

    private static void appendAmExtras(StringBuilder cmd, String extrasText) {
        for (ExtraSpec extra : parseExtras(extrasText)) {
            String type = extra.type;
            String flag = "--es";
            if ("i".equals(type) || "int".equals(type)) flag = "--ei";
            else if ("l".equals(type) || "long".equals(type)) flag = "--el";
            else if ("f".equals(type) || "float".equals(type)) flag = "--ef";
            else if ("b".equals(type) || "bool".equals(type) || "boolean".equals(type)) flag = "--ez";
            cmd.append(' ').append(flag).append(' ')
                    .append(shellQuote(extra.key)).append(' ')
                    .append(shellQuote(extra.value));
        }
    }

    private static List<ExtraSpec> parseExtras(String extrasText) {
        List<ExtraSpec> extras = new ArrayList<>();
        if (TextUtils.isEmpty(extrasText)) return extras;
        String[] lines = extrasText.split("\\r?\\n");
        for (String line : lines) {
            String value = line == null ? "" : line.trim();
            if (TextUtils.isEmpty(value) || value.startsWith("#")) continue;
            int eq = value.indexOf('=');
            if (eq <= 0) continue;
            String left = value.substring(0, eq).trim();
            String right = value.substring(eq + 1).trim();
            String type = "s";
            String key = left;
            int colon = left.indexOf(':');
            if (colon > 0) {
                type = left.substring(0, colon).trim().toLowerCase(Locale.US);
                key = left.substring(colon + 1).trim();
            }
            if (!TextUtils.isEmpty(key)) extras.add(new ExtraSpec(type, key, right));
        }
        return extras;
    }

    private static List<String> splitList(String text) {
        List<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return list;
        String[] parts = text.split("[\\r\\n,]+");
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (!TextUtils.isEmpty(value)) list.add(value);
        }
        return list;
    }

    private static int parseFlags(String text) {
        if (TextUtils.isEmpty(text)) return 0;
        int flags = 0;
        String[] parts = text.split("[\\s,|+]+");
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (TextUtils.isEmpty(value)) continue;
            Integer preset = FLAG_PRESETS.get(value.toUpperCase(Locale.US).replace("FLAG_ACTIVITY_", "").replace("INTENT.", ""));
            if (preset != null) {
                flags |= preset;
                continue;
            }
            try {
                if (value.startsWith("0x") || value.startsWith("0X")) flags |= (int) Long.parseLong(value.substring(2), 16);
                else flags |= Integer.parseInt(value);
            } catch (Throwable ignored) {}
        }
        return flags;
    }

    private static String normalizeClassName(String packageName, String className) {
        if (TextUtils.isEmpty(className)) return "";
        String value = className.trim();
        if (TextUtils.isEmpty(packageName)) return value;
        if (value.startsWith(".")) return packageName + value;
        if (value.indexOf('.') < 0) return packageName + "." + value;
        return value;
    }

    private static String defaultSaveName(IntentSpec spec) {
        if (!TextUtils.isEmpty(spec.packageName) && !TextUtils.isEmpty(spec.className)) {
            return spec.packageName + "/" + spec.className;
        }
        if (!TextUtils.isEmpty(spec.action)) return spec.action;
        if (!TextUtils.isEmpty(spec.data)) return spec.data;
        return "Intent";
    }

    private static boolean isAppEnabled(PackageManager pm, ApplicationInfo ai) {
        if (pm == null || ai == null) return true;
        try {
            int state = pm.getApplicationEnabledSetting(ai.packageName);
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return ai.enabled;
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } catch (Throwable ignored) {
            return ai.enabled;
        }
    }

    private static String safeLabel(PackageDropdownEntry entry) {
        if (entry == null) return "";
        return TextUtils.isEmpty(entry.label) ? (entry.pkg == null ? "" : entry.pkg) : entry.label;
    }

    private static String text(TextInputEditText view) {
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static String text(MaterialAutoCompleteTextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static void setText(TextInputEditText view, String text) {
        if (view != null) view.setText(text == null ? "" : text);
    }

    private static void setText(MaterialAutoCompleteTextView view, String text) {
        if (view != null) view.setText(text == null ? "" : text, false);
    }

    private void setStatus(String message) {
        TabToolsBinding b = toolsBinding();
        if (b != null) b.txtToolsIntentStatus.setText(message == null ? "" : message);
    }

    private void appendOutput(String message) {
        if (host != null && !TextUtils.isEmpty(message)) host.appendOutput(message);
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private SharedPreferences prefs() {
        return host == null ? null : host.getSharedPreferences();
    }

    private ActivityMainBinding binding() {
        return host == null ? null : host.getBinding();
    }

    private TabToolsBinding toolsBinding() {
        ActivityMainBinding binding = binding();
        return binding == null ? null : binding.tabTools;
    }

    private int dp(int value) {
        Activity activity = activity();
        float density = activity == null ? 1f : activity.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static void hideKeyboard(View view) {
        if (view == null) return;
        try {
            Object service = view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (service instanceof InputMethodManager) {
                ((InputMethodManager) service).hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String shellQuote(String text) {
        if (text == null) return "''";
        return "'" + text.replace("'", "'\\''") + "'";
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown error";
        String message = t.getMessage();
        return TextUtils.isEmpty(message) ? t.getClass().getSimpleName() : message;
    }

    private interface ValueCallback {
        void onValue(String value);
    }

    private static final class IntentSpec {
        String packageName = "";
        String className = "";
        String action = "";
        String data = "";
        String mimeType = "";
        String categories = "";
        String flags = "NEW_TASK";
        String extras = "";

        boolean hasLaunchTarget() {
            return !TextUtils.isEmpty(packageName)
                    || !TextUtils.isEmpty(className)
                    || !TextUtils.isEmpty(action)
                    || !TextUtils.isEmpty(data);
        }

        String summary() {
            if (!TextUtils.isEmpty(packageName) && !TextUtils.isEmpty(className)) return packageName + "/" + className;
            if (!TextUtils.isEmpty(action)) return action;
            if (!TextUtils.isEmpty(data)) return data;
            return "intent";
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("packageName", packageName);
            o.put("className", className);
            o.put("action", action);
            o.put("data", data);
            o.put("mimeType", mimeType);
            o.put("categories", categories);
            o.put("flags", flags);
            o.put("extras", extras);
            return o;
        }

        static IntentSpec fromJson(JSONObject o) {
            IntentSpec spec = new IntentSpec();
            if (o == null) return spec;
            spec.packageName = o.optString("packageName", "");
            spec.className = o.optString("className", "");
            spec.action = o.optString("action", "");
            spec.data = o.optString("data", "");
            spec.mimeType = o.optString("mimeType", "");
            spec.categories = o.optString("categories", "");
            spec.flags = o.optString("flags", "NEW_TASK");
            spec.extras = o.optString("extras", "");
            return spec;
        }
    }

    private static final class SavedIntent {
        final String name;
        final IntentSpec spec;

        SavedIntent(String name, IntentSpec spec) {
            this.name = name;
            this.spec = spec;
        }
    }

    private static final class ExtraSpec {
        final String type;
        final String key;
        final String value;

        ExtraSpec(String type, String key, String value) {
            this.type = TextUtils.isEmpty(type) ? "s" : type;
            this.key = key;
            this.value = value == null ? "" : value;
        }
    }
}
