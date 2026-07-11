package dev.perms.test.packages;

import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Inspects APK native-library ABI coverage before install.
 *
 * Android rejects an APK with native libraries when none of its lib/<abi>/ entries
 * match the device ABIs. Detecting that before install makes APK Editor and file
 * installs easier to diagnose without hiding the real package-manager result.
 */
public final class PackageAbiInspector {
    private PackageAbiInspector() {}

    public static final class Result {
        public final boolean inspected;
        public final boolean hasNativeLibraries;
        public final boolean hasMatchingDeviceAbi;
        public final List<String> packageAbis;
        public final List<String> deviceAbis;
        public final String warning;

        Result(boolean inspected,
               boolean hasNativeLibraries,
               boolean hasMatchingDeviceAbi,
               List<String> packageAbis,
               List<String> deviceAbis,
               String warning) {
            this.inspected = inspected;
            this.hasNativeLibraries = hasNativeLibraries;
            this.hasMatchingDeviceAbi = hasMatchingDeviceAbi;
            this.packageAbis = packageAbis == null ? Collections.emptyList() : packageAbis;
            this.deviceAbis = deviceAbis == null ? Collections.emptyList() : deviceAbis;
            this.warning = warning == null ? "" : warning;
        }
    }

    public static Result inspectSingleApk(File apk) {
        if (apk == null || !apk.isFile()) {
            return new Result(false, false, true, Collections.emptyList(), deviceAbis(), "");
        }

        LinkedHashSet<String> packageAbis = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(apk)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null || entry.isDirectory()) continue;
                String abi = abiFromNativeLibraryEntry(entry.getName());
                if (!TextUtils.isEmpty(abi)) packageAbis.add(abi);
            }
        } catch (Throwable ignored) {
            return new Result(false, false, true, Collections.emptyList(), deviceAbis(), "");
        }

        List<String> deviceAbis = deviceAbis();
        boolean hasLibs = !packageAbis.isEmpty();
        boolean matches = !hasLibs || hasAnyMatch(packageAbis, deviceAbis);
        String warning = matches ? "" : buildNoMatchWarning(new ArrayList<>(packageAbis), deviceAbis);
        return new Result(true, hasLibs, matches, new ArrayList<>(packageAbis), deviceAbis, warning);
    }

    public static String buildInstallWarning(File apk) {
        Result r = inspectSingleApk(apk);
        return r == null ? "" : r.warning;
    }

    public static boolean isNoMatchingAbiInstallFailure(String out, String err) {
        String text = ((out == null ? "" : out) + "\n" + (err == null ? "" : err)).toLowerCase(Locale.US);
        return text.contains("install_failed_no_matching_abis")
                || text.contains("no matching abis")
                || text.contains("failed to extract native libraries");
    }

    private static String abiFromNativeLibraryEntry(String name) {
        if (TextUtils.isEmpty(name)) return "";
        String n = name.replace('\\', '/');
        if (!n.startsWith("lib/")) return "";
        int next = n.indexOf('/', 4);
        if (next <= 4 || !n.endsWith(".so")) return "";
        String abi = n.substring(4, next).trim();
        return knownAbi(abi) ? abi : "";
    }

    private static boolean knownAbi(String abi) {
        if (TextUtils.isEmpty(abi)) return false;
        return "arm64-v8a".equals(abi)
                || "armeabi-v7a".equals(abi)
                || "armeabi".equals(abi)
                || "x86".equals(abi)
                || "x86_64".equals(abi)
                || "mips".equals(abi)
                || "mips64".equals(abi);
    }

    private static List<String> deviceAbis() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            if (Build.SUPPORTED_ABIS != null) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    if (!TextUtils.isEmpty(abi)) out.add(abi);
                }
            }
        } catch (Throwable ignored) {}
        try {
            if (!TextUtils.isEmpty(Build.CPU_ABI)) out.add(Build.CPU_ABI);
        } catch (Throwable ignored) {}
        try {
            if (!TextUtils.isEmpty(Build.CPU_ABI2)) out.add(Build.CPU_ABI2);
        } catch (Throwable ignored) {}
        return new ArrayList<>(out);
    }

    private static boolean hasAnyMatch(Set<String> packageAbis, List<String> deviceAbis) {
        if (packageAbis == null || packageAbis.isEmpty()) return true;
        if (deviceAbis == null || deviceAbis.isEmpty()) return false;
        for (String abi : deviceAbis) {
            if (packageAbis.contains(abi)) return true;
        }
        return false;
    }

    private static String buildNoMatchWarning(List<String> packageAbis, List<String> deviceAbis) {
        return "This APK contains native libraries for " + join(packageAbis)
                + ", but this device supports " + join(deviceAbis) + ". "
                + "Android will reject it with INSTALL_FAILED_NO_MATCHING_ABIS unless you use an APK/split archive variant that includes a matching ABI.";
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "unknown ABI";
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(value);
        }
        return sb.length() == 0 ? "unknown ABI" : sb.toString();
    }
}
