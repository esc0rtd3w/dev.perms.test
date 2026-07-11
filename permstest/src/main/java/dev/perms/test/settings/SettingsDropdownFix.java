package dev.perms.test.settings;

import dev.perms.test.databinding.ActivityMainBinding;

/** Applies the Samsung/Material exposed-dropdown hint workaround used by app dropdowns. */
public final class SettingsDropdownFix {
    private SettingsDropdownFix() {
    }

    public interface PermissionHintUpdater {
        void updatePermissionHint();
    }

    public static void apply(ActivityMainBinding binding, boolean enabled, PermissionHintUpdater permissionHintUpdater) {
        if (binding == null) return;

        try {
            binding.tabPackages.tilAppDropdown.setExpandedHintEnabled(!enabled);
            binding.tabPackages.ddApp.setHint(enabled ? "Select App..." : "");

            binding.tabPackages.tilPermDropdown.setExpandedHintEnabled(!enabled);
            if (permissionHintUpdater != null) {
                permissionHintUpdater.updatePermissionHint();
            }
        } catch (Throwable ignored) {
        }
    }
}
