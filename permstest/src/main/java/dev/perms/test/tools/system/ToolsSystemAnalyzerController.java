package dev.perms.test.tools.system;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Locale;

import dev.perms.test.databinding.ActivityMainBinding;

/** Tools-tab controller for a local hardware/dev-state snapshot. */
public final class ToolsSystemAnalyzerController {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        void appendOutput(String message);
        boolean isDebugOutputEnabled();
        void debugOutput(String area, String message);
    }

    private final Host host;
    private String lastReport = "";

    public ToolsSystemAnalyzerController(Host host) {
        this.host = host;
    }

    public void bind() {
        ActivityMainBinding binding = binding();
        if (binding == null || binding.tabTools == null) return;
        MaterialButton refresh = binding.tabTools.btnToolsSystemAnalyzerRefresh;
        MaterialButton copy = binding.tabTools.btnToolsSystemAnalyzerCopy;
        if (refresh != null) refresh.setOnClickListener(v -> refresh());
        if (copy != null) copy.setOnClickListener(v -> copyReport());
        if (TextUtils.isEmpty(lastReport)) refresh();
    }

    private void refresh() {
        debug("refresh requested");
        Activity activity = activity();
        ActivityMainBinding binding = binding();
        if (activity == null || binding == null || binding.tabTools == null) return;
        ArrayList<Section> sections = new ArrayList<>();
        sections.add(new Section("Device", deviceInfo(activity)));
        sections.add(new Section("CPU", cpuInfo()));
        sections.add(new Section("GPU / Display", gpuDisplayInfo(activity)));
        sections.add(new Section("RAM", ramInfo(activity)));
        sections.add(new Section("Storage", storageInfo(activity)));
        sections.add(new Section("Developer", developerInfo(activity)));
        lastReport = toReport(sections);
        render(sections);
        if (binding.tabTools.txtToolsSystemAnalyzerStatus != null) {
            binding.tabTools.txtToolsSystemAnalyzerStatus.setText("Updated " + timeLabel());
        }
        debug("refresh complete; chars=" + lastReport.length());
    }

    private void render(ArrayList<Section> sections) {
        Activity activity = activity();
        ActivityMainBinding binding = binding();
        if (activity == null || binding == null || binding.tabTools == null || binding.tabTools.listToolsSystemAnalyzer == null) return;
        LinearLayout list = binding.tabTools.listToolsSystemAnalyzer;
        list.removeAllViews();
        for (Section section : sections) {
            list.addView(cardFor(activity, section));
        }
    }

    private View cardFor(Activity activity, Section section) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setUseCompatPadding(false);
        card.setStrokeWidth(dp(activity, 1));
        card.setRadius(dp(activity, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(activity, 6), 0, 0);
        card.setLayoutParams(lp);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 10);
        box.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(activity);
        title.setText(section.title);
        title.setTextSize(14f);
        title.setGravity(Gravity.START);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView body = new TextView(activity);
        body.setText(section.body);
        body.setTextSize(12f);
        body.setAlpha(0.86f);
        body.setPadding(0, dp(activity, 4), 0, 0);
        box.addView(body);

        card.addView(box);
        return card;
    }

    private String deviceInfo(Activity activity) {
        StringBuilder sb = new StringBuilder();
        line(sb, "Model", Build.MANUFACTURER + " " + Build.MODEL);
        line(sb, "Brand/Product", Build.BRAND + " / " + Build.PRODUCT);
        line(sb, "Device", Build.DEVICE + " / " + Build.BOARD);
        line(sb, "Hardware", Build.HARDWARE + " / " + Build.HOST);
        line(sb, "Android", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        line(sb, "Build", Build.ID + " / " + Build.DISPLAY);
        line(sb, "Security patch", Build.VERSION.SECURITY_PATCH);
        line(sb, "ABIs", TextUtils.join(", ", Build.SUPPORTED_ABIS));
        return sb.toString();
    }

    private String cpuInfo() {
        StringBuilder sb = new StringBuilder();
        line(sb, "Java cores", String.valueOf(Runtime.getRuntime().availableProcessors()));
        line(sb, "Online CPUs", readOneLine("/sys/devices/system/cpu/online"));
        line(sb, "Possible CPUs", readOneLine("/sys/devices/system/cpu/possible"));
        line(sb, "CPU max MHz", cpuFreqSummary("cpuinfo_max_freq"));
        line(sb, "CPU min MHz", cpuFreqSummary("cpuinfo_min_freq"));
        String model = cpuInfoValue("Hardware");
        if (TextUtils.isEmpty(model)) model = cpuInfoValue("model name");
        if (TextUtils.isEmpty(model)) model = cpuInfoValue("Processor");
        line(sb, "CPU model", model);
        line(sb, "Features", ellipsize(cpuInfoValue("Features"), 180));
        return sb.toString();
    }

    private String gpuDisplayInfo(Activity activity) {
        StringBuilder sb = new StringBuilder();
        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.getDeviceConfigurationInfo() != null) {
                int req = am.getDeviceConfigurationInfo().reqGlEsVersion;
                line(sb, "OpenGL ES", ((req >> 16) & 0xffff) + "." + (req & 0xffff));
            }
        } catch (Throwable ignored) {
        }
        try {
            android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            line(sb, "Resolution px", dm.widthPixels + " x " + dm.heightPixels);
            line(sb, "Density", dm.densityDpi + " dpi / " + String.format(Locale.US, "%.2f", dm.density));
            line(sb, "Screen dp", activity.getResources().getConfiguration().screenWidthDp + " x "
                    + activity.getResources().getConfiguration().screenHeightDp
                    + " / smallest " + activity.getResources().getConfiguration().smallestScreenWidthDp);
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private String ramInfo(Activity activity) {
        StringBuilder sb = new StringBuilder();
        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                line(sb, "Total", formatBytes(mi.totalMem));
                line(sb, "Available", formatBytes(mi.availMem));
                line(sb, "Low memory", String.valueOf(mi.lowMemory));
                line(sb, "Threshold", formatBytes(mi.threshold));
            }
        } catch (Throwable ignored) {
        }
        String memTotal = memInfoValue("MemTotal");
        String swapTotal = memInfoValue("SwapTotal");
        if (!TextUtils.isEmpty(memTotal)) line(sb, "/proc MemTotal", memTotal);
        if (!TextUtils.isEmpty(swapTotal)) line(sb, "/proc SwapTotal", swapTotal);
        return sb.toString();
    }

    private String storageInfo(Activity activity) {
        StringBuilder sb = new StringBuilder();
        appendStorage(sb, "Data", Environment.getDataDirectory());
        try { appendStorage(sb, "External", activity.getExternalFilesDir(null)); } catch (Throwable ignored) {}
        try { appendStorage(sb, "Public", Environment.getExternalStorageDirectory()); } catch (Throwable ignored) {}
        return sb.toString();
    }

    private String developerInfo(Activity activity) {
        StringBuilder sb = new StringBuilder();
        line(sb, "Build tags", Build.TAGS);
        line(sb, "Debuggable build", String.valueOf((activity.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0));
        line(sb, "Developer settings", globalSetting(activity, "development_settings_enabled"));
        line(sb, "ADB enabled", globalSetting(activity, "adb_enabled"));
        line(sb, "Package", activity.getPackageName());
        line(sb, "Files dir", safePath(activity.getFilesDir()));
        line(sb, "External files", safePath(activity.getExternalFilesDir(null)));
        return sb.toString();
    }

    private void copyReport() {
        Activity activity = activity();
        if (activity == null) return;
        if (TextUtils.isEmpty(lastReport)) refresh();
        try {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PermsTest System Analyzer", lastReport));
            Toast.makeText(activity, "System Analyzer copied", Toast.LENGTH_SHORT).show();
            debug("report copied; chars=" + (lastReport == null ? 0 : lastReport.length()));
        } catch (Throwable t) {
            Toast.makeText(activity, "Copy failed: " + t, Toast.LENGTH_LONG).show();
            debug("copy failed: " + t);
        }
    }

    private String toReport(ArrayList<Section> sections) {
        StringBuilder sb = new StringBuilder("PermsTest System Analyzer\n");
        for (Section section : sections) {
            sb.append('\n').append("== ").append(section.title).append(" ==\n").append(section.body);
        }
        return sb.toString();
    }

    private void appendStorage(StringBuilder sb, String label, File file) {
        try {
            if (file == null) return;
            StatFs fs = new StatFs(file.getAbsolutePath());
            long total = fs.getTotalBytes();
            long free = fs.getAvailableBytes();
            line(sb, label, safePath(file) + " — " + formatBytes(free) + " free / " + formatBytes(total) + " total");
        } catch (Throwable ignored) {
        }
    }

    private String cpuFreqSummary(String filename) {
        ArrayList<String> vals = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            String raw = readOneLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/" + filename);
            if (TextUtils.isEmpty(raw)) continue;
            try {
                long khz = Long.parseLong(raw.trim());
                vals.add(String.format(Locale.US, "cpu%d %.0f", i, khz / 1000.0));
            } catch (Throwable ignored) {
            }
        }
        return vals.isEmpty() ? "unavailable" : TextUtils.join(" MHz, ", vals) + " MHz";
    }

    private String cpuInfoValue(String key) {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                int idx = line.indexOf(':');
                if (idx <= 0) continue;
                String k = line.substring(0, idx).trim();
                if (key.equalsIgnoreCase(k)) return line.substring(idx + 1).trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private String memInfoValue(String key) {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                int idx = line.indexOf(':');
                if (idx <= 0) continue;
                if (key.equalsIgnoreCase(line.substring(0, idx).trim())) return line.substring(idx + 1).trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private String readOneLine(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String globalSetting(Activity activity, String key) {
        try {
            return String.valueOf(Settings.Global.getInt(activity.getContentResolver(), key));
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    private void line(StringBuilder sb, String key, String value) {
        if (TextUtils.isEmpty(value)) value = "unavailable";
        sb.append(key).append(": ").append(value).append('\n');
    }

    private String ellipsize(String value, int max) {
        if (TextUtils.isEmpty(value) || value.length() <= max) return value;
        return value.substring(0, max - 1) + "…";
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "unavailable";
        double v = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (v >= 1024 && idx < units.length - 1) {
            v /= 1024.0;
            idx++;
        }
        return String.format(Locale.US, "%.1f %s", v, units[idx]);
    }

    private String safePath(File file) {
        try { return file == null ? "unavailable" : file.getAbsolutePath(); }
        catch (Throwable ignored) { return "unavailable"; }
    }

    private String timeLabel() {
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date());
    }

    private int dp(Activity activity, int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private ActivityMainBinding binding() {
        return host == null ? null : host.getBinding();
    }

    private void debug(String message) {
        try {
            if (host != null && host.isDebugOutputEnabled()) host.debugOutput("system-analyzer", message);
        } catch (Throwable ignored) {
        }
    }

    private static final class Section {
        final String title;
        final String body;

        Section(String title, String body) {
            this.title = title;
            this.body = body == null ? "" : body;
        }
    }
}
