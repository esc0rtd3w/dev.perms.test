package dev.perms.test.packages;

import java.util.Locale;

/**
 * Shared helpers for interpreting package-manager install results.
 */
public final class PackageInstallResults {
    private PackageInstallResults() {}

    public static boolean isExistingPackageInstallConflict(String out, String err) {
        return isPackageUpdateIncompatible(out, err) || isPackageVersionDowngrade(out, err);
    }

    public static boolean isPackageUpdateIncompatible(String out, String err) {
        String text = combine(out, err).toLowerCase(Locale.US);
        return text.contains("install_failed_update_incompatible")
                || text.contains("signatures do not match")
                || text.contains("different certificates")
                || text.contains("incompatible signatures");
    }

    public static boolean isPackageVersionDowngrade(String out, String err) {
        String text = combine(out, err).toLowerCase(Locale.US);
        return text.contains("install_failed_version_downgrade")
                || text.contains("downgrade detected")
                || text.contains("older than current");
    }

    public static boolean isNoMatchingAbiFailure(String out, String err) {
        return PackageAbiInspector.isNoMatchingAbiInstallFailure(out, err);
    }

    public static String buildFailureMessage(String installedFromPath,
                                             String originalLabel,
                                             int exit,
                                             String out,
                                             String err,
                                             boolean includeUpdateIncompatibleHint) {
        try {
            String name = originalLabel;
            if (name == null || name.trim().isEmpty()) name = installedFromPath;
            if (name == null || name.trim().isEmpty()) name = "Package";

            StringBuilder sb = new StringBuilder();
            sb.append(name);
            sb.append("\n\nExit code: ").append(exit);

            String details = "";
            if (err != null && !err.trim().isEmpty()) details = err.trim();
            else if (out != null && !out.trim().isEmpty()) details = out.trim();

            if (isPackageVersionDowngrade(out, err)) {
                sb.append("\n\nA newer version of this package is already installed.");
            } else if (includeUpdateIncompatibleHint && isPackageUpdateIncompatible(out, err)) {
                sb.append("\n\nExisting installed package has a different signing certificate.");
            }

            if (!details.isEmpty()) {
                if (details.length() > 1500) details = details.substring(0, 1500) + "…";
                sb.append("\n\n").append(details);
            } else {
                sb.append("\n\nNo additional output.");
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "Install failed.";
        }
    }

    public static String buildDebugLog(String installedFromPath, String originalLabel, int exit, String out, String err) {
        try {
            String name = originalLabel;
            if (name == null || name.trim().isEmpty()) name = installedFromPath;
            if (name == null || name.trim().isEmpty()) name = "Package";

            StringBuilder sb = new StringBuilder();
            sb.append("== PermsTest Install Debug ==\n");
            sb.append("Name: ").append(name).append("\n");
            sb.append("Path: ").append(installedFromPath == null ? "" : installedFromPath).append("\n");
            sb.append("Exit: ").append(exit).append("\n\n");

            sb.append("---- STDOUT ----\n");
            sb.append(out == null ? "" : out);
            if (out != null && !out.endsWith("\n")) sb.append("\n");

            sb.append("\n---- STDERR ----\n");
            sb.append(err == null ? "" : err);
            if (err != null && !err.endsWith("\n")) sb.append("\n");

            final int cap = 80_000;
            if (sb.length() > cap) {
                return sb.substring(0, cap) + "\n... (truncated)\n";
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String combine(String out, String err) {
        return (out == null ? "" : out) + "\n" + (err == null ? "" : err);
    }
}
