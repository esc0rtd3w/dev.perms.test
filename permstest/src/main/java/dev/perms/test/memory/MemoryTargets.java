package dev.perms.test.memory;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MemoryTargets {
    private MemoryTargets() {
    }

    public static boolean isAllowedPackage(String pkg, String selfPackageName, boolean excludeSelfPackage) {
        String value = pkg == null ? "" : pkg.trim();
        if (TextUtils.isEmpty(value)) return false;
        return !excludeSelfPackage || !TextUtils.equals(selfPackageName, value);
    }

    public static ArrayList<MemoryPackageEntry> filterPackages(
            List<MemoryPackageEntry> packages,
            boolean onlyRunning,
            boolean excludeSelfPackage,
            String selfPackageName
    ) {
        ArrayList<MemoryPackageEntry> out = new ArrayList<>();
        if (packages == null) return out;
        for (MemoryPackageEntry entry : packages) {
            if (entry == null) continue;
            if (onlyRunning && !entry.running) continue;
            if (excludeSelfPackage && TextUtils.equals(selfPackageName, entry.pkg)) continue;
            out.add(entry);
        }
        return out;
    }

    public static String resolvePackageText(
            String value,
            List<MemoryPackageEntry> knownPackages,
            String selfPackageName,
            boolean excludeSelfPackage
    ) {
        String raw = value == null ? "" : value.trim();
        if (isAllowedPackage(raw, selfPackageName, excludeSelfPackage)) return raw;
        if (knownPackages == null) return "";
        for (MemoryPackageEntry entry : knownPackages) {
            if (entry == null || TextUtils.isEmpty(entry.pkg)) continue;
            if (TextUtils.equals(raw, entry.pkg)
                    || TextUtils.equals(raw, entry.label)
                    || TextUtils.equals(raw, entry.toString())) {
                return isAllowedPackage(entry.pkg, selfPackageName, excludeSelfPackage) ? entry.pkg : "";
            }
        }
        return "";
    }

    public static String parseSelectedProcessPid(CharSequence value) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (TextUtils.isEmpty(text) || text.toLowerCase(Locale.US).startsWith("auto-select")) return null;
        int sep = text.indexOf('·');
        if (sep > 0) return text.substring(0, sep).trim();
        int sp = text.indexOf(' ');
        if (sp > 0) return text.substring(0, sp).trim();
        return text;
    }
}
