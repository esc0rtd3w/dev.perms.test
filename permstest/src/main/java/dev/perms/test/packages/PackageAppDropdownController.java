package dev.perms.test.packages;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ViewConfiguration;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.PackageDropdownAdapter;
import dev.perms.test.ui.PackageDropdownEntry;
import dev.perms.test.ui.PackageDropdownUi;

public final class PackageAppDropdownController {
    private static final String DEFAULT_ENTRY_LABEL = "Select App...";

    public interface Host {
        void executeIo(Runnable task);
        void runOnUiThread(Runnable task);
        void preservePackagesScrollPosition(Runnable action);
        void setPackagesSpinnerVisible(boolean visible);
        void configureSafeDropdownEndIcon(TextInputLayout layout, Runnable onClick);
        void configureTapOnlyDropdownField(AutoCompleteTextView view, int touchSlop, int maxTapMs, Runnable onTap);
        void showDropdownAtLastSelection(AutoCompleteTextView view, String lastText);
        void rememberPackageToolsTargetPackage(String packageName);
        void updatePackageInfoSoon();
    }

    private final Context context;
    private final ActivityMainBinding binding;
    private final Host host;
    private final int colorGranted;
    private final int colorRevoked;

    private final ArrayList<PackageDropdownEntry> allApps = new ArrayList<>();
    private final ArrayList<PackageDropdownEntry> filteredApps = new ArrayList<>();
    private PackageDropdownAdapter appAdapter;
    private boolean colorizeAppDropdown;
    private String lastAppDropdownText;
    private String selectedPackageToolsPackage;
    private StateFilter stateFilter = StateFilter.ALL;

    public PackageAppDropdownController(
            Context context,
            ActivityMainBinding binding,
            Host host,
            boolean colorizeAppDropdown,
            int colorGranted,
            int colorRevoked) {
        this.context = context;
        this.binding = binding;
        this.host = host;
        this.colorizeAppDropdown = colorizeAppDropdown;
        this.colorGranted = colorGranted;
        this.colorRevoked = colorRevoked;
    }

    public void setup(String restoreFilterText) {
        if (host == null || binding == null || binding.tabPackages == null) return;
        host.setPackagesSpinnerVisible(true);
        host.executeIo(() -> {
            final ArrayList<PackageDropdownEntry> apps = loadInstalledApps();
            host.runOnUiThread(() -> host.preservePackagesScrollPosition(() -> bindLoadedApps(apps, restoreFilterText)));
        });
    }

    public void setColorizeAppDropdown(boolean enabled) {
        colorizeAppDropdown = enabled;
        if (appAdapter != null) {
            appAdapter.notifyDataSetChanged();
        }
    }

    public String getSelectedPackageToolsPackage() {
        return selectedPackageToolsPackage;
    }

    public ArrayAdapter<?> getCurrentAdapterForClone() {
        return new PackageDropdownAdapter(
                context,
                snapshotFilteredAppsForClone(),
                PackageDropdownUi.ColorMode.ENABLED_STATE,
                colorizeAppDropdown,
                colorGranted,
                colorRevoked);
    }

    public int getCurrentAdapterCloneCount() {
        try {
            return snapshotFilteredAppsForClone().size();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private ArrayList<PackageDropdownEntry> snapshotFilteredAppsForClone() {
        ArrayList<PackageDropdownEntry> snapshot = new ArrayList<>();
        try {
            if (!filteredApps.isEmpty()) {
                snapshot.addAll(filteredApps);
            } else if (appAdapter != null && appAdapter.getCount() > 0) {
                for (int i = 0; i < appAdapter.getCount(); i++) {
                    PackageDropdownEntry entry = appAdapter.getItem(i);
                    if (entry != null) snapshot.add(entry);
                }
            }
        } catch (Throwable ignored) {
        }
        if (snapshot.isEmpty()) {
            snapshot.add(new PackageDropdownEntry(DEFAULT_ENTRY_LABEL, "", true));
        }
        return snapshot;
    }

    public void selectEntryFromClone(PackageDropdownEntry entry) {
        if (entry == null) return;
        if (TextUtils.isEmpty(entry.pkg)) {
            clearSelectedAppToPlaceholder();
            return;
        }

        String show = TextUtils.isEmpty(entry.label) ? entry.pkg : entry.label;
        try {
            if (binding.tabPackages.ddApp != null) binding.tabPackages.ddApp.setText(show, false);
            if (binding.tabPackages.edtTargetPkg != null) binding.tabPackages.edtTargetPkg.setText(entry.pkg);
        } catch (Throwable ignored) {
        }
        lastAppDropdownText = show;
        selectedPackageToolsPackage = entry.pkg;
        try {
            host.rememberPackageToolsTargetPackage(entry.pkg);
            host.updatePackageInfoSoon();
        } catch (Throwable ignored) {
        }
    }

    public List<PackageDropdownEntry> getAllApps() {
        return allApps;
    }

    public ArrayList<PackageDropdownEntry> snapshotAllApps() {
        synchronized (allApps) {
            return new ArrayList<>(allApps);
        }
    }

    public int findIndexByPackage(String packageName) {
        try {
            if (TextUtils.isEmpty(packageName) || appAdapter == null) return -1;
            for (int i = 0; i < appAdapter.getCount(); i++) {
                PackageDropdownEntry entry = appAdapter.getItem(i);
                if (entry != null && packageName.equals(entry.pkg)) return i;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public void refreshAfterUninstall(final int removedIndex, final String filterText) {
        if (host == null) return;
        host.executeIo(() -> {
            final ArrayList<PackageDropdownEntry> apps = loadInstalledAppsWithMetaData();
            host.runOnUiThread(() -> {
                try {
                    synchronized (allApps) {
                        allApps.clear();
                        allApps.addAll(apps);
                    }

                    applyFilter(filterText, false);

                    PackageDropdownEntry next = null;
                    if (appAdapter != null && appAdapter.getCount() > 0) {
                        int index = removedIndex;
                        if (index < 0) index = 0;
                        if (index >= appAdapter.getCount()) index = appAdapter.getCount() - 1;
                        next = appAdapter.getItem(index);
                    }

                    if (next != null) {
                        lastAppDropdownText = next.label;
                        if (binding.tabPackages.ddApp != null) binding.tabPackages.ddApp.setText(next.label, false);
                        if (binding.tabPackages.edtTargetPkg != null) binding.tabPackages.edtTargetPkg.setText(next.pkg);
                        selectedPackageToolsPackage = next.pkg;
                        host.updatePackageInfoSoon();
                    } else if (binding.tabPackages.ddApp != null) {
                        binding.tabPackages.ddApp.setText("", false);
                    }
                } catch (Throwable ignored) {
                }
            });
        });
    }

    public void refreshEnabledStateForPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) return;
        try {
            PackageManager pm = context.getPackageManager();
            boolean enabled = true;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                enabled = isAppEnabled(pm, ai);
            } catch (Throwable ignored) {
                enabled = false;
            }

            synchronized (allApps) {
                for (PackageDropdownEntry entry : allApps) {
                    if (entry != null && packageName.equals(entry.pkg)) {
                        entry.enabled = enabled;
                        break;
                    }
                }
            }

            if (appAdapter != null) {
                applyFilter(currentFilterText(), false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void bindLoadedApps(ArrayList<PackageDropdownEntry> apps, String restoreFilterText) {
        try {
            synchronized (allApps) {
                allApps.clear();
                allApps.addAll(apps);
            }

            appAdapter = new PackageDropdownAdapter(
                    context,
                    new ArrayList<>(),
                    PackageDropdownUi.ColorMode.ENABLED_STATE,
                    colorizeAppDropdown,
                    colorGranted,
                    colorRevoked);
            binding.tabPackages.ddApp.setAdapter(appAdapter);
            DropdownUi.prepareExposedDropdown(binding.tabPackages.tilAppDropdown, binding.tabPackages.ddApp);

            String appFilterText = restoreFilterText;
            if (appFilterText == null && binding.tabPackages.edtAppFilter != null
                    && binding.tabPackages.edtAppFilter.getText() != null) {
                appFilterText = binding.tabPackages.edtAppFilter.getText().toString();
            }
            if (binding.tabPackages.edtAppFilter != null && appFilterText != null) {
                String currentFilterText = binding.tabPackages.edtAppFilter.getText() == null
                        ? ""
                        : binding.tabPackages.edtAppFilter.getText().toString();
                if (!TextUtils.equals(currentFilterText, appFilterText)) {
                    binding.tabPackages.edtAppFilter.setText(appFilterText);
                    binding.tabPackages.edtAppFilter.setSelection(appFilterText.length());
                }
            }
            boolean hasAppFilter = !TextUtils.isEmpty(appFilterText);
            applyFilter(appFilterText, !hasAppFilter);

            DropdownUi.bindTapOnlyExposedDropdown(
                    context,
                    binding.tabPackages.tilAppDropdown,
                    binding.tabPackages.ddApp,
                    ViewConfiguration.get(context).getScaledTouchSlop() / 2,
                    350,
                    () -> host.showDropdownAtLastSelection(binding.tabPackages.ddApp, lastAppDropdownText));

            binding.tabPackages.ddApp.setOnItemClickListener((parent, view, position, id) -> {
                Object obj = parent.getItemAtPosition(position);
                if (!(obj instanceof PackageDropdownEntry)) return;
                PackageDropdownEntry entry = (PackageDropdownEntry) obj;
                if (entry == null) return;
                if (TextUtils.isEmpty(entry.pkg)) {
                    clearSelectedAppToPlaceholder();
                    return;
                }

                String show = TextUtils.isEmpty(entry.label) ? entry.pkg : entry.label;
                binding.tabPackages.ddApp.setText(show, false);
                lastAppDropdownText = show;
                binding.tabPackages.edtTargetPkg.setText(entry.pkg);
                selectedPackageToolsPackage = entry.pkg;
                host.rememberPackageToolsTargetPackage(entry.pkg);
                host.updatePackageInfoSoon();
            });

            if (binding.tabPackages.edtAppFilter != null) {
                binding.tabPackages.edtAppFilter.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        applyFilter(s == null ? "" : s.toString(), false);
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });
            }
            bindPackageStateFilterRadios();
            host.setPackagesSpinnerVisible(false);
        } catch (Throwable ignored) {
            host.setPackagesSpinnerVisible(false);
        }
    }

    private void bindPackageStateFilterRadios() {
        try {
            RadioGroup group = binding.tabPackages.rgrpPackageStateFilter;
            if (group == null) return;
            group.setOnCheckedChangeListener((g, checkedId) -> {
                if (checkedId == binding.tabPackages.rdoPackageFilterEnabled.getId()) {
                    stateFilter = StateFilter.ENABLED;
                } else if (checkedId == binding.tabPackages.rdoPackageFilterDisabled.getId()) {
                    stateFilter = StateFilter.DISABLED;
                } else {
                    stateFilter = StateFilter.ALL;
                }
                applyFilter(currentFilterText(), false);
            });
        } catch (Throwable ignored) {
        }
    }

    private String currentFilterText() {
        try {
            return binding.tabPackages.edtAppFilter == null || binding.tabPackages.edtAppFilter.getText() == null
                    ? ""
                    : binding.tabPackages.edtAppFilter.getText().toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void applyFilter(String filterRaw, boolean initial) {
        if (appAdapter == null) return;
        String filter = filterRaw == null ? "" : filterRaw.trim().toLowerCase(Locale.ROOT);

        filteredApps.clear();
        filteredApps.add(new PackageDropdownEntry(DEFAULT_ENTRY_LABEL, "", true));

        synchronized (allApps) {
            for (PackageDropdownEntry app : allApps) {
                if (app == null) continue;
                String label = TextUtils.isEmpty(app.label) ? app.pkg : app.label;
                String haystack = (label + " " + app.pkg).toLowerCase(Locale.ROOT);
                if (!TextUtils.isEmpty(filter) && !haystack.contains(filter)) continue;
                if (stateFilter == StateFilter.ENABLED && !app.enabled) continue;
                if (stateFilter == StateFilter.DISABLED && app.enabled) continue;
                filteredApps.add(app);
            }
        }

        appAdapter.clear();
        appAdapter.addAll(filteredApps);
        appAdapter.notifyDataSetChanged();

        if (initial) {
            showDefaultAppPlaceholder();
        }
    }

    private void clearSelectedAppToPlaceholder() {
        try {
            showDefaultAppPlaceholder();
            if (binding.tabPackages.edtTargetPkg != null) binding.tabPackages.edtTargetPkg.setText("");
            selectedPackageToolsPackage = "";
            host.rememberPackageToolsTargetPackage("");
            host.updatePackageInfoSoon();
        } catch (Throwable ignored) {
        }
    }

    private void showDefaultAppPlaceholder() {
        try {
            binding.tabPackages.ddApp.setText(DEFAULT_ENTRY_LABEL, false);
            lastAppDropdownText = DEFAULT_ENTRY_LABEL;
        } catch (Throwable ignored) {
            lastAppDropdownText = DEFAULT_ENTRY_LABEL;
        }
    }

    private ArrayList<PackageDropdownEntry> loadInstalledApps() {
        ArrayList<PackageDropdownEntry> apps = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : installed) {
                String pkg = ai.packageName;
                if (TextUtils.isEmpty(pkg)) continue;
                CharSequence labelCs;
                try {
                    labelCs = pm.getApplicationLabel(ai);
                } catch (Throwable ignored) {
                    labelCs = pkg;
                }
                String label = labelCs == null ? pkg : labelCs.toString();
                apps.add(new PackageDropdownEntry(label, pkg, isAppEnabled(pm, ai)));
            }
        } catch (Throwable ignored) {
        }
        sortByLabel(apps);
        return apps;
    }

    private ArrayList<PackageDropdownEntry> loadInstalledAppsWithMetaData() {
        ArrayList<PackageDropdownEntry> apps = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo ai : installed) {
                String pkg = ai.packageName;
                String label = String.valueOf(pm.getApplicationLabel(ai));
                boolean enabled = ai.enabled;
                apps.add(new PackageDropdownEntry(label, pkg, enabled));
            }
        } catch (Throwable ignored) {
        }
        sortByLabel(apps);
        return apps;
    }

    private static void sortByLabel(ArrayList<PackageDropdownEntry> apps) {
        Collections.sort(apps, new Comparator<PackageDropdownEntry>() {
            @Override
            public int compare(PackageDropdownEntry o1, PackageDropdownEntry o2) {
                String a = (o1 == null || o1.label == null) ? "" : o1.label;
                String b = (o2 == null || o2.label == null) ? "" : o2.label;
                return a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT));
            }
        });
    }

    private static boolean isAppEnabled(PackageManager pm, ApplicationInfo ai) {
        if (pm == null || ai == null) return true;
        try {
            int state = pm.getApplicationEnabledSetting(ai.packageName);
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                return ai.enabled;
            }
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } catch (Throwable ignored) {
            return ai.enabled;
        }
    }
    private enum StateFilter {
        ALL,
        ENABLED,
        DISABLED
    }

}
