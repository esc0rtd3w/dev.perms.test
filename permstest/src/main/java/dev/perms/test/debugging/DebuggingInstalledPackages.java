package dev.perms.test.debugging;

import dev.perms.test.memory.MemoryToolRuntime;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import dev.perms.test.ui.PackageDropdownEntry;

/** Utility methods for the Debugging tab installed-package source picker. */
public final class DebuggingInstalledPackages {
    private DebuggingInstalledPackages() {
    }

    public static ArrayList<PackageDropdownEntry> load(PackageManager pm, List<PackageDropdownEntry> cachedApps) {
        ArrayList<PackageDropdownEntry> apps = copyCachedApps(pm, cachedApps);
        if (apps.isEmpty()) {
            apps.addAll(loadInstalledApps(pm));
        }
        sortByLabel(apps);
        return apps;
    }

    public static PackageDropdownEntry findSelected(List<PackageDropdownEntry> apps, String selectedPackage, String typedText) {
        String pkg = selectedPackage == null ? "" : selectedPackage.trim();
        String typed = typedText == null ? "" : typedText.trim();
        if (apps == null) return null;
        for (PackageDropdownEntry entry : apps) {
            if (entry == null) continue;
            if (!TextUtils.isEmpty(pkg) && pkg.equals(entry.pkg)) return entry;
            if (!TextUtils.isEmpty(typed)
                    && (typed.equals(entry.pkg) || typed.equals(entry.label) || typed.equals(entry.toString()))) {
                return entry;
            }
        }
        return null;
    }

    private static ArrayList<PackageDropdownEntry> copyCachedApps(PackageManager pm, List<PackageDropdownEntry> cachedApps) {
        ArrayList<PackageDropdownEntry> apps = new ArrayList<>();
        if (cachedApps == null || cachedApps.isEmpty()) return apps;
        for (PackageDropdownEntry entry : cachedApps) {
            if (entry == null || TextUtils.isEmpty(entry.pkg)) continue;
            boolean debuggable = entry.debuggable;
            try {
                if (pm != null) debuggable = MemoryToolRuntime.isPackageDebuggable(pm, entry.pkg);
            } catch (Throwable ignored) {
            }
            apps.add(new PackageDropdownEntry(entry.label, entry.pkg, entry.enabled, debuggable));
        }
        return apps;
    }

    private static ArrayList<PackageDropdownEntry> loadInstalledApps(PackageManager pm) {
        ArrayList<PackageDropdownEntry> apps = new ArrayList<>();
        if (pm == null) return apps;
        try {
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo ai : installed) {
                if (ai == null || TextUtils.isEmpty(ai.packageName)) continue;
                CharSequence labelCs;
                try {
                    labelCs = pm.getApplicationLabel(ai);
                } catch (Throwable ignored) {
                    labelCs = ai.packageName;
                }
                String label = labelCs == null ? ai.packageName : labelCs.toString();
                apps.add(new PackageDropdownEntry(label, ai.packageName, isEnabled(pm, ai), MemoryToolRuntime.isApplicationDebuggable(ai)));
            }
        } catch (Throwable ignored) {
        }
        return apps;
    }

    private static boolean isEnabled(PackageManager pm, ApplicationInfo ai) {
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

    private static void sortByLabel(ArrayList<PackageDropdownEntry> apps) {
        Collections.sort(apps, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a == null || a.label == null ? "" : a.label,
                b == null || b.label == null ? "" : b.label));
    }
}
