package dev.perms.test.memory;

import android.text.TextUtils;

import dev.perms.test.ui.PackageDropdownEntry;

import java.util.List;

public final class MemoryPackageToolTargets {
    private static final String PACKAGES_DROPDOWN_DEFAULT = "Select App...";

    private MemoryPackageToolTargets() {
    }

    public static String resolveCurrentPackageToolsTarget(
            String selectedPackageToolsPackage,
            CharSequence packagesDropdownText,
            CharSequence typedTargetPackageText,
            List<PackageDropdownEntry> installedApps,
            String selfPackageName,
            boolean excludeSelfPackage
    ) {
        if (MemoryTargets.isAllowedPackage(selectedPackageToolsPackage, selfPackageName, excludeSelfPackage)) {
            return selectedPackageToolsPackage.trim();
        }

        String selected = trim(packagesDropdownText);
        if (!TextUtils.isEmpty(selected) && !PACKAGES_DROPDOWN_DEFAULT.equalsIgnoreCase(selected)) {
            String resolved = resolveInstalledPackageDropdownText(selected, installedApps, selfPackageName, excludeSelfPackage);
            if (!TextUtils.isEmpty(resolved)) return resolved;
        }

        String typed = trim(typedTargetPackageText);
        if (MemoryTargets.isAllowedPackage(typed, selfPackageName, excludeSelfPackage)) {
            return typed;
        }
        return "";
    }

    public static String resolvePreferredPackageToolsTarget(
            String currentPackageToolsTarget,
            String rememberedPackageToolsTarget,
            String selfPackageName,
            boolean excludeSelfPackage
    ) {
        if (MemoryTargets.isAllowedPackage(currentPackageToolsTarget, selfPackageName, excludeSelfPackage)) {
            return currentPackageToolsTarget.trim();
        }
        if (MemoryTargets.isAllowedPackage(rememberedPackageToolsTarget, selfPackageName, excludeSelfPackage)) {
            return rememberedPackageToolsTarget.trim();
        }
        return "";
    }

    public static String resolveSyncedMemoryTarget(
            String currentMemoryTargetText,
            List<MemoryPackageEntry> memoryPackageItems,
            String currentPackageToolsTarget,
            String rememberedPackageToolsTarget,
            String selfPackageName,
            boolean excludeSelfPackage
    ) {
        String resolvedMemoryTarget = MemoryTargets.resolvePackageText(
                currentMemoryTargetText,
                memoryPackageItems,
                selfPackageName,
                excludeSelfPackage
        );
        if (!TextUtils.isEmpty(resolvedMemoryTarget)) return resolvedMemoryTarget;

        return resolvePreferredPackageToolsTarget(
                currentPackageToolsTarget,
                rememberedPackageToolsTarget,
                selfPackageName,
                excludeSelfPackage
        );
    }

    public static String formatMissingPackageToolsTargetMessage(boolean excludeSelfPackage) {
        return excludeSelfPackage ? "Select a non-PermsTest package first." : "Select a package first.";
    }

    private static String resolveInstalledPackageDropdownText(
            String selected,
            List<PackageDropdownEntry> installedApps,
            String selfPackageName,
            boolean excludeSelfPackage
    ) {
        if (installedApps == null) return "";
        for (PackageDropdownEntry entry : installedApps) {
            if (entry == null || TextUtils.isEmpty(entry.pkg)) continue;
            String label = TextUtils.isEmpty(entry.label) ? entry.pkg : entry.label;
            if (selected.equals(entry.pkg) || selected.equals(label)) {
                return MemoryTargets.isAllowedPackage(entry.pkg, selfPackageName, excludeSelfPackage) ? entry.pkg : "";
            }
        }
        return "";
    }

    private static String trim(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
