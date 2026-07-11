package dev.perms.test.permissions;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.ViewConfiguration;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.concurrent.Executor;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.DropdownUi;

public final class PermissionDropdownController {
    public interface Host {
        void configureSafeDropdownEndIcon(TextInputLayout layout, Runnable onClick);
        void configureTapOnlyDropdownField(AutoCompleteTextView view, int touchSlop, int maxTapMs, Runnable onTap);
        void showDropdownAtLastSelection(AutoCompleteTextView view, String lastText);
        void preservePackagesScrollPosition(Runnable action);
        void setPackagesSpinnerVisible(boolean visible);
        boolean isSafeToken(String value);
    }

    private final Activity activity;
    private final ActivityMainBinding binding;
    private final SharedPreferences preferences;
    private final Executor io;
    private final Host host;
    private final String useAppPermissionsKey;

    private ArrayList<PermissionDropdowns.Entry> commonEntries = new ArrayList<>();
    private ArrayList<PermissionDropdowns.Entry> allEntries = new ArrayList<>();
    private ArrayList<PermissionDropdowns.Entry> appEntries = new ArrayList<>();
    private ArrayList<PermissionDropdowns.Entry> currentEntries = new ArrayList<>();
    private PermissionDropdowns.Adapter commonAdapter;
    private PermissionDropdowns.Adapter allAdapter;
    private PermissionDropdowns.Adapter appAdapter;
    private boolean allPermissionsLoaded;
    private boolean allPermissionsLoading;
    private int allPermissionsRequestId;
    private String lastDropdownText = "";

    public PermissionDropdownController(Activity activity,
                                        ActivityMainBinding binding,
                                        SharedPreferences preferences,
                                        Executor io,
                                        String useAppPermissionsKey,
                                        Host host) {
        this.activity = activity;
        this.binding = binding;
        this.preferences = preferences;
        this.io = io;
        this.useAppPermissionsKey = useAppPermissionsKey;
        this.host = host;
    }

    public void bind() {
        io.execute(() -> {
            ArrayList<PermissionDropdowns.Entry> common = PermissionDropdowns.buildCommon(activity.getPackageManager());
            activity.runOnUiThread(() -> {
                commonEntries = common;
                commonAdapter = new PermissionDropdowns.Adapter(activity, commonEntries);

                applyMode(false);
                updateHint();
                bindPermissionListControls();
                bindUseAppPermissionsToggle();
            });
        });
    }

    public boolean usesAppPermissions() {
        try {
            return binding.tabPackages.chkUseAppPerms != null && binding.tabPackages.chkUseAppPerms.isChecked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public ArrayAdapter<?> getCurrentAdapterForClone() {
        try {
            return new PermissionDropdowns.Adapter(activity, snapshotEntriesForClone());
        } catch (Throwable ignored) {
            return commonAdapter == null
                    ? new PermissionDropdowns.Adapter(activity, new ArrayList<>())
                    : new PermissionDropdowns.Adapter(activity, new ArrayList<>(commonEntries));
        }
    }

    public int getCurrentAdapterCloneCount() {
        try {
            return snapshotEntriesForClone().size();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private ArrayList<PermissionDropdowns.Entry> snapshotEntriesForClone() {
        ArrayList<PermissionDropdowns.Entry> snapshot = new ArrayList<>();
        try {
            if (usesAppPermissions()) {
                snapshot.addAll(appEntries == null || appEntries.isEmpty() ? commonEntries : appEntries);
            } else if (binding.tabPackages.chkAllPerms != null && binding.tabPackages.chkAllPerms.isChecked()) {
                snapshot.addAll(allEntries == null || allEntries.isEmpty() ? commonEntries : allEntries);
            } else {
                snapshot.addAll(commonEntries);
            }
        } catch (Throwable ignored) {
            snapshot.clear();
            snapshot.addAll(commonEntries);
        }
        return snapshot;
    }

    public void selectEntryFromClone(PermissionDropdowns.Entry entry) {
        if (entry == null) return;
        try {
            if (TextUtils.isEmpty(entry.permission)) {
                binding.tabPackages.ddPerm.setText("", false);
                lastDropdownText = "";
                return;
            }
            if (!TextUtils.isEmpty(entry.title)) {
                binding.tabPackages.ddPerm.setText(entry.title, false);
                lastDropdownText = entry.title;
            }
            binding.tabPackages.edtPermission.setText(entry.permission);
        } catch (Throwable ignored) {
        }
    }

    public void refreshForPackageAsync(String packageName) {
        io.execute(() -> refreshForPackage(packageName));
    }

    public void refreshForPackage(String packageName) {
        activity.runOnUiThread(() -> host.setPackagesSpinnerVisible(true));

        ArrayList<PermissionDropdowns.Entry> packageEntries;
        if (TextUtils.isEmpty(packageName) || !host.isSafeToken(packageName)) {
            packageEntries = PermissionDropdowns.emptyAppList();
        } else {
            packageEntries = PermissionDropdowns.buildForPackage(activity.getPackageManager(), packageName);
        }

        ArrayList<PermissionDropdowns.Entry> finalEntries = packageEntries;
        activity.runOnUiThread(() -> host.preservePackagesScrollPosition(() -> {
            appEntries = finalEntries;
            appAdapter = new PermissionDropdowns.Adapter(activity, appEntries);
            currentEntries = appEntries;
            binding.tabPackages.ddPerm.setAdapter(appAdapter);

            updateHint();
            restoreLastSelection();
            host.setPackagesSpinnerVisible(false);
        }));
    }

    public void updateHint() {
        try {
            boolean showAll = binding.tabPackages.chkAllPerms != null && binding.tabPackages.chkAllPerms.isChecked();
            boolean useApp = binding.tabPackages.chkUseAppPerms != null && binding.tabPackages.chkUseAppPerms.isChecked();
            String label = showAll ? "All Permissions" : (useApp ? "Selected App Permissions" : "Common Permissions");

            binding.tabPackages.tilPermDropdown.setHintEnabled(false);
            binding.tabPackages.tilPermDropdown.setHint(null);
            binding.tabPackages.ddPerm.setHint(label);
        } catch (Throwable ignored) {
        }
    }

    private void bindPermissionListControls() {
        binding.tabPackages.chkAllPerms.setOnCheckedChangeListener((buttonView, isChecked) -> {
            applyMode(isChecked);
            updateHint();
        });

        DropdownUi.bindTapOnlyExposedDropdown(
                activity,
                binding.tabPackages.tilPermDropdown,
                binding.tabPackages.ddPerm,
                ViewConfiguration.get(activity).getScaledTouchSlop(),
                0,
                () -> host.showDropdownAtLastSelection(binding.tabPackages.ddPerm, lastDropdownText));

        binding.tabPackages.ddPerm.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (!(item instanceof PermissionDropdowns.Entry)) return;
            PermissionDropdowns.Entry entry = (PermissionDropdowns.Entry) item;
            if (entry == null) return;

            if (TextUtils.isEmpty(entry.permission)) {
                binding.tabPackages.ddPerm.setText("", false);
                lastDropdownText = "";
                return;
            }

            if (!TextUtils.isEmpty(entry.title)) {
                binding.tabPackages.ddPerm.setText(entry.title, false);
                lastDropdownText = entry.title;
            }
            binding.tabPackages.edtPermission.setText(entry.permission);
        });
    }

    private void bindUseAppPermissionsToggle() {
        if (binding.tabPackages.chkUseAppPerms == null) return;

        boolean defaultUseApp = true;
        try {
            defaultUseApp = preferences.getBoolean(useAppPermissionsKey, true);
        } catch (Throwable ignored) {
        }

        binding.tabPackages.chkUseAppPerms.setChecked(defaultUseApp);
        binding.tabPackages.chkAllPerms.setEnabled(!defaultUseApp);
        if (defaultUseApp) {
            String packageName = binding.tabPackages.edtTargetPkg.getText() == null
                    ? ""
                    : binding.tabPackages.edtTargetPkg.getText().toString().trim();
            refreshForPackageAsync(packageName);
        }

        binding.tabPackages.chkUseAppPerms.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                preferences.edit().putBoolean(useAppPermissionsKey, isChecked).apply();
            } catch (Throwable ignored) {
            }
            binding.tabPackages.chkAllPerms.setEnabled(!isChecked);
            updateHint();
            if (isChecked) {
                String packageName = binding.tabPackages.edtTargetPkg.getText() == null
                        ? ""
                        : binding.tabPackages.edtTargetPkg.getText().toString().trim();
                refreshForPackageAsync(packageName);
            } else {
                applyMode(binding.tabPackages.chkAllPerms.isChecked());
            }
        });
    }

    private void applyMode(boolean all) {
        if (usesAppPermissions()) {
            return;
        }
        if (commonAdapter == null) return;
        if (all && !allPermissionsLoaded) {
            showAllPermissionsLoading();
            ensureAllPermissionsLoaded();
            return;
        }
        currentEntries = all ? allEntries : commonEntries;
        binding.tabPackages.ddPerm.setAdapter(all ? allAdapter : commonAdapter);
        updateHint();
        restoreLastSelection();
    }

    private void showAllPermissionsLoading() {
        ArrayList<PermissionDropdowns.Entry> loading = new ArrayList<>();
        loading.add(new PermissionDropdowns.Entry("Loading all permissions...", "", "", -1));
        currentEntries = loading;
        binding.tabPackages.ddPerm.setAdapter(new PermissionDropdowns.Adapter(activity, loading));
        binding.tabPackages.ddPerm.setText("", false);
        try {
            host.setPackagesSpinnerVisible(true);
        } catch (Throwable ignored) {
        }
    }

    private void ensureAllPermissionsLoaded() {
        if (allPermissionsLoaded || allPermissionsLoading) return;
        allPermissionsLoading = true;
        final int requestId = ++allPermissionsRequestId;
        io.execute(() -> {
            ArrayList<PermissionDropdowns.Entry> all = PermissionDropdowns.buildAll(activity.getPackageManager());
            activity.runOnUiThread(() -> {
                if (requestId != allPermissionsRequestId) return;
                allPermissionsLoading = false;
                allPermissionsLoaded = true;
                allEntries = all;
                allAdapter = new PermissionDropdowns.Adapter(activity, allEntries);
                try {
                    host.setPackagesSpinnerVisible(false);
                } catch (Throwable ignored) {
                }
                if (!usesAppPermissions()
                        && binding.tabPackages.chkAllPerms != null
                        && binding.tabPackages.chkAllPerms.isChecked()) {
                    applyMode(true);
                }
            });
        });
    }

    private void restoreLastSelection() {
        String last = lastDropdownText;
        if (TextUtils.isEmpty(last) || currentEntries == null) {
            binding.tabPackages.ddPerm.setText("", false);
            return;
        }

        boolean found = false;
        for (PermissionDropdowns.Entry entry : currentEntries) {
            if (entry != null && last.equals(entry.title) && !TextUtils.isEmpty(entry.permission)) {
                found = true;
                break;
            }
        }
        binding.tabPackages.ddPerm.setText(found ? last : "", false);
    }
}
