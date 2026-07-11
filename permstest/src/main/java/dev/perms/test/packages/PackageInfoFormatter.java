package dev.perms.test.packages;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.Collections;

import dev.perms.test.permissions.PermissionDropdowns;

public final class PackageInfoFormatter {
    private PackageInfoFormatter() {
    }

    public static CharSequence build(
            Context context,
            String packageName,
            int colorDangerous,
            int colorSignature,
            int colorGranted,
            int colorRevoked,
            int colorMuted) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (context == null || TextUtils.isEmpty(packageName)) return sb;

        PackageManager pm = context.getPackageManager();

        try {
            ApplicationInfo appInfo = getApplicationInfo(pm, packageName);
            String label = loadLabel(pm, appInfo, packageName);

            sb.append(label).append("\n");
            sb.append("Package: ").append(packageName).append("\n");
            sb.append("Enabled: ").append(enabledStateText(pm, appInfo, packageName)).append("\n");
            sb.append("UID: ").append(String.valueOf(appInfo.uid)).append("\n");

            ArrayList<String> permissions = requestedPermissions(pm, packageName);
            if (permissions.isEmpty()) {
                sb.append("Permissions: (none requested)\n");
                return sb;
            }

            int grantedCount = countGranted(pm, packageName, permissions);
            int deniedCount = Math.max(0, permissions.size() - grantedCount);

            sb.append("Permissions: ").append(String.valueOf(permissions.size())).append(" requested\n");
            sb.append("Granted: ").append(String.valueOf(grantedCount))
                    .append("  |  Revoked/Denied: ").append(String.valueOf(deniedCount)).append("\n\n");

            for (String permission : permissions) {
                appendPermissionRow(
                        sb,
                        pm,
                        packageName,
                        permission,
                        colorForProtection(pm, permission, colorDangerous, colorSignature),
                        colorGranted,
                        colorRevoked,
                        colorMuted);
            }

            return sb;
        } catch (NameNotFoundException e) {
            return "Package not found: " + packageName;
        } catch (Throwable t) {
            return "Failed to read package info: " + t;
        }
    }

    private static ApplicationInfo getApplicationInfo(PackageManager pm, String packageName) throws NameNotFoundException {
        if (Build.VERSION.SDK_INT >= 33) {
            return pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0));
        }
        //noinspection deprecation
        return pm.getApplicationInfo(packageName, 0);
    }

    private static String loadLabel(PackageManager pm, ApplicationInfo appInfo, String fallback) {
        try {
            CharSequence label = pm.getApplicationLabel(appInfo);
            return label == null ? fallback : label.toString();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String enabledStateText(PackageManager pm, ApplicationInfo appInfo, String packageName) {
        int enabledSetting;
        try {
            enabledSetting = pm.getApplicationEnabledSetting(packageName);
        } catch (Throwable ignored) {
            enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        }

        switch (enabledSetting) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return "ENABLED";
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                return "DISABLED";
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return "DISABLED_USER";
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                return "DISABLED_UNTIL_USED";
            default:
                return appInfo.enabled
                        ? "DEFAULT / manifest enabled"
                        : "DEFAULT / manifest disabled (not pm disable-user)";
        }
    }

    private static ArrayList<String> requestedPermissions(PackageManager pm, String packageName) {
        ArrayList<String> permissions = new ArrayList<>();
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 33) {
                info = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
            } else {
                //noinspection deprecation
                info = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            }

            if (info.requestedPermissions != null) {
                for (String permission : info.requestedPermissions) {
                    if (!TextUtils.isEmpty(permission)) permissions.add(permission);
                }
            }
        } catch (Throwable ignored) {
        }
        Collections.sort(permissions);
        return permissions;
    }

    private static int countGranted(PackageManager pm, String packageName, ArrayList<String> permissions) {
        int grantedCount = 0;
        for (String permission : permissions) {
            try {
                if (pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED) {
                    grantedCount++;
                }
            } catch (Throwable ignored) {
            }
        }
        return grantedCount;
    }

    private static void appendPermissionRow(
            SpannableStringBuilder sb,
            PackageManager pm,
            String packageName,
            String permission,
            int permissionColor,
            int colorGranted,
            int colorRevoked,
            int colorMuted) {
        boolean granted = false;
        try {
            granted = pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
        }

        appendColored(sb, PermissionDropdowns.shortName(permission), permissionColor);
        sb.append("  ");
        appendColored(sb, granted ? "granted" : "revoked", granted ? colorGranted : colorRevoked);
        sb.append("  ");
        appendColored(sb, "(" + permission + ")", colorMuted);
        sb.append("\n");
    }

    private static void appendColored(SpannableStringBuilder sb, String text, int color) {
        int start = sb.length();
        sb.append(text == null ? "" : text);
        int end = sb.length();
        if (color != -1 && end > start) {
            sb.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static int colorForProtection(PackageManager pm, String permission, int colorDangerous, int colorSignature) {
        int base = PermissionDropdowns.protectionBase(pm, permission);
        if (base == PermissionInfo.PROTECTION_DANGEROUS) return colorDangerous;
        if (base == PermissionInfo.PROTECTION_SIGNATURE) return colorSignature;
        return -1;
    }
}
