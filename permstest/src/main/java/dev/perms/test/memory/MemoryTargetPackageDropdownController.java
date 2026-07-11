package dev.perms.test.memory;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import dev.perms.test.databinding.TabMemoryBinding;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.vr.PermsTestVrOverlayCompat;

/**
 * Activity-side controller for the Memory target-package dropdown.
 *
 * MainActivity supplies preference filtering, threading, and shared dropdown helpers. This
 * controller owns only the target package adapter, target refresh, pending dropdown updates,
 * restored target text, and the VR-compatible target picker used by the Memory tab.
 */
public final class MemoryTargetPackageDropdownController {
    public interface PackageFilter {
        ArrayList<MemoryPackageEntry> filter(List<MemoryPackageEntry> packages);
    }

    public interface RunningStateSupplier {
        boolean includeRunningState();
    }

    public interface ProcessRefreshCallback {
        void refresh(boolean userInitiated);
    }

    public interface BackgroundExecutor {
        void execute(Runnable task);
    }

    public interface UiExecutor {
        void execute(Runnable task);
    }

    public interface OutputAppender {
        void append(String text);
    }

    public interface DropdownShower {
        void show(AutoCompleteTextView dropdown, String lastText);
    }

    public interface DropdownTweaker {
        void apply(AutoCompleteTextView dropdown);
    }

    private final Context context;
    private final TabMemoryBinding tab;
    private final PackageFilter packageFilter;
    private final RunningStateSupplier runningStateSupplier;
    private final ProcessRefreshCallback processRefreshCallback;
    private final BackgroundExecutor backgroundExecutor;
    private final UiExecutor uiExecutor;
    private final OutputAppender outputAppender;
    private final DropdownShower dropdownShower;
    private final DropdownTweaker dropdownTweaker;
    private final ArrayList<MemoryPackageEntry> packageItems = new ArrayList<>();

    private MemoryPackageAdapter packageAdapter;
    private List<MemoryPackageEntry> pendingPackageUpdate;
    private boolean dropdownReopenAfterRefresh;
    private boolean packageRefreshInFlight;
    private String restoredTargetText;

    public MemoryTargetPackageDropdownController(Context context,
                                                 TabMemoryBinding tab,
                                                 String restoredTargetText,
                                                 PackageFilter packageFilter,
                                                 RunningStateSupplier runningStateSupplier,
                                                 ProcessRefreshCallback processRefreshCallback,
                                                 BackgroundExecutor backgroundExecutor,
                                                 UiExecutor uiExecutor,
                                                 OutputAppender outputAppender,
                                                 DropdownShower dropdownShower,
                                                 DropdownTweaker dropdownTweaker) {
        this.context = context;
        this.tab = tab;
        this.restoredTargetText = restoredTargetText;
        this.packageFilter = packageFilter;
        this.runningStateSupplier = runningStateSupplier;
        this.processRefreshCallback = processRefreshCallback;
        this.backgroundExecutor = backgroundExecutor;
        this.uiExecutor = uiExecutor;
        this.outputAppender = outputAppender;
        this.dropdownShower = dropdownShower;
        this.dropdownTweaker = dropdownTweaker;
    }

    public void bindInitial() {
        if (tab == null || tab.edtMemoryTargetPkg == null) return;

        packageAdapter = new MemoryPackageAdapter(context, packageItems);
        tab.edtMemoryTargetPkg.setAdapter(packageAdapter);
        DropdownUi.prepareExposedDropdown(tab.tilMemoryTargetPkg, tab.edtMemoryTargetPkg);

        if (!TextUtils.isEmpty(restoredTargetText)) {
            tab.edtMemoryTargetPkg.setText(restoredTargetText, false);
            restoredTargetText = null;
        }

        tab.edtMemoryTargetPkg.setOnItemClickListener((parent, view, position, id) -> {
            Object obj = parent.getItemAtPosition(position);
            if (obj instanceof MemoryPackageEntry) {
                MemoryPackageEntry entry = (MemoryPackageEntry) obj;
                tab.edtMemoryTargetPkg.setText(entry.pkg, false);
                refreshProcesses(false);
            }
        });
    }

    public String getTargetPackage() {
        try {
            if (tab != null && tab.edtMemoryTargetPkg != null && tab.edtMemoryTargetPkg.getText() != null) {
                return tab.edtMemoryTargetPkg.getText().toString().trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public void setTargetPackage(String pkg) {
        try {
            if (tab != null && tab.edtMemoryTargetPkg != null) {
                tab.edtMemoryTargetPkg.setText(pkg == null ? "" : pkg, false);
            }
        } catch (Throwable ignored) {
        }
    }

    public ArrayList<MemoryPackageEntry> getPackageItemsSnapshot() {
        return new ArrayList<>(packageItems);
    }

    public void refresh() {
        refresh(false);
    }

    public void refresh(boolean userInitiated) {
        try {
            if (userInitiated) {
                appendOutput("[Memory] Refreshing target package list...\n");
                try {
                    Toast.makeText(context, "Refreshing target package list...", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
            if (packageRefreshInFlight) {
                setSpinnerVisible(true);
                if (userInitiated || dropdownReopenAfterRefresh) {
                    dropdownReopenAfterRefresh = true;
                }
                return;
            }
            packageRefreshInFlight = true;
            setSpinnerVisible(true);
            final boolean includeRunningState = shouldIncludeRunningState();
            executeInBackground(() -> {
                final List<MemoryPackageEntry> packages = MemoryToolRuntime.listTargetPackages(context.getApplicationContext(), includeRunningState);
                executeOnUi(() -> {
                    packageRefreshInFlight = false;
                    setSpinnerVisible(false);
                    updateDropdown(packages);
                    if (userInitiated) {
                        try {
                            Toast.makeText(context, "Target package list refreshed", Toast.LENGTH_SHORT).show();
                            tab.edtMemoryTargetPkg.post(() -> applyDropdownTweaks(tab.edtMemoryTargetPkg));
                        } catch (Throwable ignored) {
                        }
                    }
                });
            });
        } catch (Throwable t) {
            packageRefreshInFlight = false;
            setSpinnerVisible(false);
            appendOutput("[Memory] Failed to refresh target packages: " + t + "\n");
        }
    }

    public void openOrRefreshDropdown() {
        try {
            final boolean hasItems = packageAdapter != null && packageAdapter.getCount() > 0;
            if (hasItems) {
                if (PermsTestVrOverlayCompat.isEnabled(context)) {
                    showTargetPackageDialog();
                } else {
                    showDropdown(tab.edtMemoryTargetPkg, getTargetPackage());
                    tab.edtMemoryTargetPkg.post(() -> applyDropdownTweaks(tab.edtMemoryTargetPkg));
                }
            } else {
                dropdownReopenAfterRefresh = true;
                refresh();
            }
        } catch (Throwable ignored) {
        }
    }

    public void onDropdownDismissed() {
        applyPendingDropdownUpdate();
    }

    private void updateDropdown(List<MemoryPackageEntry> packages) {
        try {
            if (isPopupShowing(tab.edtMemoryTargetPkg)) {
                pendingPackageUpdate = packages == null ? new ArrayList<>() : new ArrayList<>(packages);
                return;
            }
            updateDropdownNow(packages);
        } catch (Throwable t) {
            dropdownReopenAfterRefresh = false;
            pendingPackageUpdate = null;
            appendOutput("[Memory] Failed to update target packages: " + t + "\n");
        }
    }

    private void updateDropdownNow(List<MemoryPackageEntry> packages) {
        try {
            pendingPackageUpdate = null;
            String currentText = null;
            try {
                CharSequence current = tab.edtMemoryTargetPkg.getText();
                currentText = current == null ? null : current.toString().trim();
            } catch (Throwable ignored) {
            }
            packageItems.clear();
            packageItems.addAll(filterPackages(packages));
            if (packageAdapter == null) {
                packageAdapter = new MemoryPackageAdapter(context, packageItems);
                tab.edtMemoryTargetPkg.setAdapter(packageAdapter);
            } else {
                packageAdapter.setItems(packageItems);
            }
            if (!TextUtils.isEmpty(currentText)) {
                tab.edtMemoryTargetPkg.setText(currentText, false);
            }
            if (dropdownReopenAfterRefresh) {
                dropdownReopenAfterRefresh = false;
                final String selected = TextUtils.isEmpty(currentText) ? getTargetPackage() : currentText;
                tab.edtMemoryTargetPkg.postDelayed(() -> {
                    try {
                        if (PermsTestVrOverlayCompat.isEnabled(context)) {
                            showTargetPackageDialog();
                        } else {
                            showDropdown(tab.edtMemoryTargetPkg, selected);
                        }
                    } catch (Throwable ignored) {}
                }, 80L);
            }
        } catch (Throwable t) {
            dropdownReopenAfterRefresh = false;
            pendingPackageUpdate = null;
            appendOutput("[Memory] Failed to update target packages: " + t + "\n");
        }
    }

    private void applyPendingDropdownUpdate() {
        if (pendingPackageUpdate == null) return;
        List<MemoryPackageEntry> pending = pendingPackageUpdate;
        pendingPackageUpdate = null;
        updateDropdownNow(pending);
    }

    private void showTargetPackageDialog() {
        try {
            if (packageAdapter == null || packageAdapter.getCount() <= 0) {
                dropdownReopenAfterRefresh = true;
                refresh(false);
                return;
            }
            ArrayList<MemoryPackageEntry> dialogItems = new ArrayList<>(packageItems);
            MemoryPackageAdapter adapter = new MemoryPackageAdapter(context, dialogItems);
            AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                    .setTitle("Target package")
                    .setAdapter(adapter, (d, which) -> {
                        try {
                            MemoryPackageEntry entry = adapter.getItem(which);
                            if (entry == null || TextUtils.isEmpty(entry.pkg)) return;
                            tab.edtMemoryTargetPkg.setText(entry.pkg, false);
                            refreshProcesses(false);
                        } catch (Throwable ignored) {
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            try {
                ListView list = dialog.getListView();
                if (list != null) {
                    list.setFastScrollEnabled(true);
                    list.setFastScrollAlwaysVisible(true);
                    list.setVerticalScrollBarEnabled(true);
                    list.setScrollbarFadingEnabled(false);
                    list.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            appendOutput("[Memory] Failed to open target package picker: " + t + "\n");
        }
    }

    private ArrayList<MemoryPackageEntry> filterPackages(List<MemoryPackageEntry> packages) {
        try {
            if (packageFilter != null) return packageFilter.filter(packages);
        } catch (Throwable ignored) {
        }
        return packages == null ? new ArrayList<>() : new ArrayList<>(packages);
    }

    private void refreshProcesses(boolean userInitiated) {
        try {
            if (processRefreshCallback != null) processRefreshCallback.refresh(userInitiated);
        } catch (Throwable ignored) {
        }
    }

    private boolean shouldIncludeRunningState() {
        try {
            return runningStateSupplier != null && runningStateSupplier.includeRunningState();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void executeInBackground(Runnable task) {
        if (backgroundExecutor != null) backgroundExecutor.execute(task);
    }

    private void executeOnUi(Runnable task) {
        if (uiExecutor != null) uiExecutor.execute(task);
    }

    private void appendOutput(String text) {
        if (TextUtils.isEmpty(text)) return;
        try {
            if (outputAppender != null) outputAppender.append(text);
        } catch (Throwable ignored) {
        }
    }

    private void showDropdown(AutoCompleteTextView dropdown, String lastText) {
        try {
            if (dropdownShower != null) dropdownShower.show(dropdown, lastText);
        } catch (Throwable ignored) {
        }
    }

    private void applyDropdownTweaks(AutoCompleteTextView dropdown) {
        try {
            if (dropdownTweaker != null) dropdownTweaker.apply(dropdown);
        } catch (Throwable ignored) {
        }
    }

    private void setSpinnerVisible(boolean visible) {
        try {
            if (tab != null && tab.pbMemoryTargetPackagesLoading != null) {
                tab.pbMemoryTargetPackagesLoading.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isPopupShowing(AutoCompleteTextView dropdown) {
        try {
            return dropdown != null && dropdown.isPopupShowing();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
