package dev.perms.test.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import java.util.Locale;

import dev.perms.test.memory.MemoryToolHelper;
import dev.perms.test.settings.SettingsPreferenceKeys;

/** Detects the running device class and applies default UI profile options. */
public final class DeviceDetection {
    public enum Profile {
        PHONE("Phone"),
        TABLET("Tablet"),
        TV("TV"),
        VR("VR"),
        CHROMEBOOK("ChromeOS"),
        AUTOMOTIVE("Automotive"),
        WATCH("Watch");

        private final String label;

        Profile(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final class Info {
        public final Profile profile;
        public final String manufacturer;
        public final String brand;
        public final String model;
        public final String product;
        public final String device;
        public final String hardware;
        public final String cpu;
        public final String language;
        public final String serial;
        public final String osVersion;
        public final int densityDpi;
        public final int smallestWidthDp;
        public final int screenWidthDp;
        public final int screenHeightDp;

        private Info(Profile profile,
                     String manufacturer,
                     String brand,
                     String model,
                     String product,
                     String device,
                     String hardware,
                     String cpu,
                     String language,
                     String serial,
                     String osVersion,
                     int densityDpi,
                     int smallestWidthDp,
                     int screenWidthDp,
                     int screenHeightDp) {
            this.profile = profile;
            this.manufacturer = clean(manufacturer, "Unknown");
            this.brand = clean(brand, "Unknown");
            this.model = clean(model, "Unknown model");
            this.product = clean(product, "Unknown");
            this.device = clean(device, "Unknown");
            this.hardware = clean(hardware, "Unknown");
            this.cpu = clean(cpu, "Unknown ABI");
            this.language = clean(language, "Unknown");
            this.serial = clean(serial, "Unavailable");
            this.osVersion = clean(osVersion, "Unknown");
            this.densityDpi = densityDpi;
            this.smallestWidthDp = smallestWidthDp;
            this.screenWidthDp = screenWidthDp;
            this.screenHeightDp = screenHeightDp;
        }

        public String modelLine() {
            String maker = manufacturer;
            if ("Unknown".equalsIgnoreCase(maker) || model.toLowerCase(Locale.US).contains(maker.toLowerCase(Locale.US))) {
                return model;
            }
            return maker + " " + model;
        }

        public String startupSubtitle() {
            return profile.label() + " profile · " + modelLine();
        }

        public String settingsRow() {
            StringBuilder sb = new StringBuilder();
            sb.append("Device Profile: ").append(profile.label());
            sb.append("  |  Device: ").append(modelLine());
            sb.append("  |  OS: ").append(osVersion);
            sb.append('\n');
            sb.append("CPU: ").append(cpu);
            sb.append("  |  DPI: ").append(densityDpi > 0 ? densityDpi : 0);
            sb.append("  |  Lang: ").append(language);
            sb.append("  |  Serial: ").append(serial);
            return sb.toString();
        }

        public boolean hasUsableSerial() {
            return isUsableSerial(serial);
        }

        public Info withSerial(String serialValue) {
            return new Info(profile, manufacturer, brand, model, product, device, hardware, cpu, language, serialValue, osVersion,
                    densityDpi, smallestWidthDp, screenWidthDp, screenHeightDp);
        }
    }

    private DeviceDetection() {
    }

    public static Info detect(Context context) {
        Context app = context == null ? null : context.getApplicationContext();
        Configuration cfg = app == null ? null : app.getResources().getConfiguration();
        DisplayMetrics dm = app == null ? null : app.getResources().getDisplayMetrics();
        int sw = cfg == null ? 0 : cfg.smallestScreenWidthDp;
        int width = cfg == null ? 0 : cfg.screenWidthDp;
        int height = cfg == null ? 0 : cfg.screenHeightDp;
        int dpi = dm == null ? 0 : dm.densityDpi;
        String serial = safeSerial();
        try {
            if (!isUsableSerial(serial) && app != null) {
                SharedPreferences sp = app.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE);
                String cachedSerial = sp.getString(SettingsPreferenceKeys.DEVICE_SERIAL_LAST, "");
                if (isUsableSerial(cachedSerial)) {
                    serial = cachedSerial;
                }
            }
        } catch (Throwable ignored) {
        }
        return new Info(
                detectProfile(app, cfg),
                Build.MANUFACTURER,
                Build.BRAND,
                Build.MODEL,
                Build.PRODUCT,
                Build.DEVICE,
                Build.HARDWARE,
                primaryAbi(),
                Locale.getDefault().toLanguageTag(),
                serial,
                androidOsVersion(),
                dpi,
                sw,
                width,
                height);
    }

    public static Info applyAutomaticProfile(Context context) {
        return applyAutomaticProfile(context, false);
    }

    public static Info applyAutomaticProfile(Context context, boolean forceDeviceDefaults) {
        Info info = detect(context);
        if (context == null) return info;
        SharedPreferences sp = context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(SettingsPreferenceKeys.AUTOMATIC_DEVICE_DETECTION, true)) {
            return info;
        }

        SharedPreferences.Editor editor = sp.edit();
        boolean vr = info.profile == Profile.VR;
        if (forceDeviceDefaults) {
            editor.putBoolean(SettingsPreferenceKeys.DEVICE_DEFAULT_VR_MODE_USER_SET, false);
            editor.putBoolean(SettingsPreferenceKeys.DEVICE_DEFAULT_DISABLE_OVERLAYS_USER_SET, false);
            editor.putBoolean(SettingsPreferenceKeys.DEVICE_DEFAULT_FLOATING_PANELS_USER_SET, false);
        }
        if (forceDeviceDefaults || !sp.getBoolean(SettingsPreferenceKeys.DEVICE_DEFAULT_VR_MODE_USER_SET, false)) {
            editor.putBoolean(SettingsPreferenceKeys.UI_DETECT_VR_MODE, vr);
        }
        if (forceDeviceDefaults || !sp.getBoolean(SettingsPreferenceKeys.DEVICE_DEFAULT_DISABLE_OVERLAYS_USER_SET, false)) {
            editor.putBoolean(MemoryToolHelper.KEY_DISABLE_OVERLAYS_VR_COMPATIBLE, vr);
        }
        if (forceDeviceDefaults || !sp.getBoolean(SettingsPreferenceKeys.DEVICE_DEFAULT_FLOATING_PANELS_USER_SET, false)) {
            editor.putBoolean(SettingsPreferenceKeys.ENABLE_FLOATING_PANELS, vr);
        }
        if (shouldUseWithoutPtraceForOs()) {
            editor.putBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, true);
        }
        editor.putString(SettingsPreferenceKeys.DEVICE_PROFILE_LAST, info.profile.label());
        editor.putString(SettingsPreferenceKeys.DEVICE_MODEL_LAST, info.modelLine());
        editor.apply();
        return info;
    }

    public static String settingsSummary(Context context) {
        return detect(context).settingsRow();
    }

    public static String startupSubtitle(Context context) {
        return detect(context).startupSubtitle();
    }

    public static String buildSerialProbeCommand() {
        return "for p in ro.serialno ro.boot.serialno ro.boot.hw.serialno ro.boot.hardware.serialno "
                + "ro.vendor.serialno ro.product.serial persist.sys.serialno; do "
                + "v=\"$(getprop \"$p\" 2>/dev/null)\"; "
                + "case \"$v\" in \"\"|unknown|Unknown|UNKNOWN|unavailable|Unavailable|UNAVAILABLE) ;; "
                + "*) printf '%s\\n' \"$v\"; exit 0;; esac; done; printf '\\n'";
    }

    public static String parseSerialProbeOutput(String stdout) {
        if (stdout == null) return "";
        String[] lines = stdout.split("\\r?\\n");
        for (String line : lines) {
            String value = clean(line, "");
            if (isUsableSerial(value)) return value;
        }
        return "";
    }

    public static boolean isUsableSerial(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String v = value.trim();
        if (v.length() == 0) return false;
        return !("unknown".equalsIgnoreCase(v)
                || "unavailable".equalsIgnoreCase(v)
                || "null".equalsIgnoreCase(v)
                || "none".equalsIgnoreCase(v));
    }

    private static Profile detectProfile(Context context, Configuration cfg) {
        if (context != null && isLikelyVrDevice(context)) return Profile.VR;
        if (context != null && hasFeature(context, "android.hardware.type.automotive")) return Profile.AUTOMOTIVE;
        if (cfg != null && (cfg.uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_CAR) return Profile.AUTOMOTIVE;
        if (context != null && hasFeature(context, PackageManager.FEATURE_WATCH)) return Profile.WATCH;
        if (cfg != null && (cfg.uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_WATCH) return Profile.WATCH;
        if (context != null && (hasFeature(context, PackageManager.FEATURE_LEANBACK)
                || hasFeature(context, "android.software.leanback"))) return Profile.TV;
        if (cfg != null && (cfg.uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) return Profile.TV;
        if (context != null && (hasFeature(context, "org.chromium.arc")
                || hasFeature(context, "android.hardware.type.pc"))) return Profile.CHROMEBOOK;
        int sw = cfg == null ? 0 : cfg.smallestScreenWidthDp;
        if (sw > 0 && sw < 600) return Profile.PHONE;
        return Profile.TABLET;
    }

    private static boolean isLikelyVrDevice(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm != null) {
            if (hasFeature(pm, "android.hardware.vr.high_performance")
                    || hasFeature(pm, "android.software.vr.mode")
                    || hasFeature(pm, "oculus.hardware.vr")
                    || hasFeature(pm, "com.oculus.feature.VR")) {
                return true;
            }
            if (hasPackage(pm, "com.oculus.vrshell")
                    || hasPackage(pm, "com.oculus.systemdriver")
                    || hasPackage(pm, "com.meta.horizon")) {
                return true;
            }
        }
        String build = joinLower(Build.MANUFACTURER, Build.BRAND, Build.DEVICE, Build.MODEL, Build.PRODUCT, Build.HARDWARE);
        return build.contains("oculus")
                || build.contains("quest")
                || build.contains("eureka")
                || build.contains("hollywood")
                || build.contains("meta");
    }

    private static boolean hasFeature(Context context, String name) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm != null && !TextUtils.isEmpty(name) && pm.hasSystemFeature(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasFeature(PackageManager pm, String name) {
        try {
            return pm != null && !TextUtils.isEmpty(name) && pm.hasSystemFeature(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasPackage(PackageManager pm, String name) {
        try {
            pm.getPackageInfo(name, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String primaryAbi() {
        try {
            if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
                return Build.SUPPORTED_ABIS[0];
            }
        } catch (Throwable ignored) {
        }
        return Build.CPU_ABI;
    }

    public static boolean shouldUseWithoutPtraceForOs() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P;
    }

    private static String androidOsVersion() {
        String release = clean(Build.VERSION.RELEASE, "Unknown");
        if ("Unknown".equals(release)) {
            return "Android API " + Build.VERSION.SDK_INT;
        }
        return "Android " + release + " (API " + Build.VERSION.SDK_INT + ")";
    }

    private static String safeSerial() {
        try {
            String legacy = Build.SERIAL;
            if (isUsableSerial(legacy)) {
                return legacy;
            }
        } catch (Throwable ignored) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                String serial = Build.getSerial();
                if (isUsableSerial(serial)) {
                    return serial;
                }
            } catch (Throwable ignored) {
            }
        }
        return "Unavailable";
    }

    private static String clean(String value, String fallback) {
        if (TextUtils.isEmpty(value)) return fallback;
        String trimmed = value.trim();
        return trimmed.length() == 0 ? fallback : trimmed;
    }

    private static String joinLower(String... parts) {
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (String p : parts) {
                if (p != null) sb.append(' ').append(p);
            }
        }
        return sb.toString().toLowerCase(Locale.US);
    }
}
