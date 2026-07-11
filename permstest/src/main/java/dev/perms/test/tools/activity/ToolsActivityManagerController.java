package dev.perms.test.tools.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.databinding.TabToolsBinding;
import dev.perms.test.ui.ThemeColorController;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Basic Tools-tab activity browser/launcher for installed app diagnostics. */
public final class ToolsActivityManagerController {
    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        void appendOutput(String message);
        void runShellCommandCapture(String command, ShellCallback callback);
    }

    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean stopped;
    private int loadGeneration;
    private List<AppEntry> loadedApps = Collections.emptyList();

    public ToolsActivityManagerController(Host host) {
        this.host = host;
    }

    public void bind() {
        TabToolsBinding b = toolsBinding();
        if (b == null) return;
        b.btnToolsActivityRefresh.setOnClickListener(v -> loadApps());
        b.chkToolsActivityShowSystemApps.setOnCheckedChangeListener((button, checked) -> loadApps());
        b.chkToolsActivityShowIcons.setOnCheckedChangeListener((button, checked) -> renderCurrentOrReload());
        b.chkToolsActivityHideRootActivities.setOnCheckedChangeListener((button, checked) -> loadApps());
        b.chkToolsActivityHideNonExported.setOnCheckedChangeListener((button, checked) -> loadApps());
        b.edtToolsActivityFilter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderFilteredApps(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        setStatus("Tap Refresh to load app activities.");
    }

    public void stop() {
        stopped = true;
        loadGeneration++;
    }

    private void renderCurrentOrReload() {
        TabToolsBinding b = toolsBinding();
        if (b == null || loadedApps.isEmpty()) return;
        renderFilteredApps();
    }

    private void loadApps() {
        Activity activity = activity();
        TabToolsBinding b = toolsBinding();
        if (activity == null || b == null) return;
        final int generation = ++loadGeneration;
        final boolean showSystem = b.chkToolsActivityShowSystemApps.isChecked();
        final boolean hideRootActivities = b.chkToolsActivityHideRootActivities.isChecked();
        final boolean hideNonExported = b.chkToolsActivityHideNonExported.isChecked();
        b.btnToolsActivityRefresh.setEnabled(false);
        b.llToolsActivityResults.removeAllViews();
        setStatus("Loading app activities...");
        new Thread(() -> {
            List<AppEntry> apps;
            String error = null;
            try {
                apps = queryApps(activity.getPackageManager(), showSystem, hideRootActivities, hideNonExported);
            } catch (Throwable t) {
                apps = Collections.emptyList();
                error = t.getMessage() == null ? t.toString() : t.getMessage();
            }
            final List<AppEntry> result = apps;
            final String finalError = error;
            mainHandler.post(() -> {
                if (stopped || generation != loadGeneration) return;
                TabToolsBinding current = toolsBinding();
                if (current == null) return;
                current.btnToolsActivityRefresh.setEnabled(true);
                if (!TextUtils.isEmpty(finalError)) {
                    setStatus("Activity load failed: " + finalError);
                    appendOutput("Activity Manager load failed: " + finalError);
                    return;
                }
                renderApps(result);
            });
        }, "pt-tools-activity-scan").start();
    }

    private List<AppEntry> queryApps(PackageManager pm, boolean showSystem, boolean hideRootActivities, boolean hideNonExported) {
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);
        List<AppEntry> result = new ArrayList<>();
        for (PackageInfo info : packages) {
            if (info == null || info.applicationInfo == null) continue;
            ActivityInfo[] activities = info.activities;
            if (activities == null || activities.length == 0) continue;
            boolean system = (info.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (system && !showSystem) continue;
            List<ActivityEntry> activityEntries = new ArrayList<>();
            int exported = 0;
            for (ActivityInfo activityInfo : activities) {
                if (activityInfo == null || TextUtils.isEmpty(activityInfo.name)) continue;
                boolean rootLike = looksLikeRootActivity(info.packageName, activityInfo.name);
                if (hideRootActivities && rootLike) continue;
                CharSequence label = activityInfo.loadLabel(pm);
                boolean isExported = activityInfo.exported;
                if (hideNonExported && !isExported) continue;
                if (isExported) exported++;
                activityEntries.add(new ActivityEntry(
                        activityInfo.name,
                        label == null ? "" : label.toString(),
                        info.packageName,
                        isExported,
                        rootLike || !isExported));
            }
            if (activityEntries.isEmpty()) continue;
            Collections.sort(activityEntries, Comparator.comparing(a -> a.activityName.toLowerCase(Locale.US)));
            CharSequence label = info.applicationInfo.loadLabel(pm);
            Drawable icon = null;
            try {
                icon = info.applicationInfo.loadIcon(pm);
            } catch (Throwable ignored) {
                // Icon loading is optional and should never block listing results.
            }
            result.add(new AppEntry(
                    label == null ? info.packageName : label.toString(),
                    info.packageName,
                    versionText(info),
                    activityEntries.size(),
                    exported,
                    icon,
                    activityEntries));
        }
        Collections.sort(result, Comparator.comparing(a -> a.label.toLowerCase(Locale.US)));
        return result;
    }

    private void renderApps(List<AppEntry> apps) {
        loadedApps = apps == null ? Collections.emptyList() : apps;
        renderFilteredApps();
    }

    private void renderFilteredApps() {
        TabToolsBinding b = toolsBinding();
        Activity activity = activity();
        if (b == null || activity == null) return;
        List<AppEntry> apps = filterApps(loadedApps, filterText(b));
        b.llToolsActivityResults.removeAllViews();
        int activityCount = countActivities(apps);
        String filter = filterText(b);
        if (TextUtils.isEmpty(filter)) {
            setStatus(apps.size() + " apps / " + activityCount + " activities loaded. Tap an app row to expand.");
        } else {
            setStatus(apps.size() + " apps / " + activityCount + " matching activities. Filter: " + filter);
        }
        boolean showIcons = b.chkToolsActivityShowIcons.isChecked();
        for (AppEntry app : apps) {
            b.llToolsActivityResults.addView(createAppCard(activity, app, showIcons));
        }
    }

    private List<AppEntry> filterApps(List<AppEntry> apps, String rawFilter) {
        if (apps == null || apps.isEmpty()) return Collections.emptyList();
        String filter = normalizeFilter(rawFilter);
        if (TextUtils.isEmpty(filter)) return apps;
        List<AppEntry> result = new ArrayList<>();
        for (AppEntry app : apps) {
            boolean appMatches = containsFilter(app.label, filter)
                    || containsFilter(app.packageName, filter)
                    || containsFilter(app.version, filter);
            List<ActivityEntry> activityMatches = new ArrayList<>();
            for (ActivityEntry entry : app.activities) {
                if (appMatches
                        || containsFilter(entry.activityName, filter)
                        || containsFilter(entry.label, filter)
                        || containsFilter(friendlyName(entry), filter)
                        || containsFilter(entry.packageName + "/" + entry.activityName, filter)
                        || (entry.exported && containsFilter("exported", filter))
                        || (!entry.exported && containsFilter("not exported non exported", filter))
                        || (entry.needsPrivilegedLaunch && containsFilter("root privileged", filter))) {
                    activityMatches.add(entry);
                }
            }
            if (!activityMatches.isEmpty()) {
                result.add(new AppEntry(
                        app.label,
                        app.packageName,
                        app.version,
                        activityMatches.size(),
                        countExported(activityMatches),
                        app.icon,
                        activityMatches));
            }
        }
        return result;
    }

    private static int countActivities(List<AppEntry> apps) {
        int count = 0;
        if (apps != null) {
            for (AppEntry app : apps) count += app.activityCount;
        }
        return count;
    }

    private static int countExported(List<ActivityEntry> activities) {
        int count = 0;
        if (activities != null) {
            for (ActivityEntry entry : activities) if (entry.exported) count++;
        }
        return count;
    }

    private static boolean containsFilter(String value, String filter) {
        return !TextUtils.isEmpty(value) && value.toLowerCase(Locale.US).contains(filter);
    }

    private static String normalizeFilter(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static String filterText(TabToolsBinding b) {
        if (b == null || b.edtToolsActivityFilter.getText() == null) return "";
        return b.edtToolsActivityFilter.getText().toString().trim();
    }

    private View createAppCard(Activity activity, AppEntry app, boolean showIcons) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setUseCompatPadding(false);
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(8));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardLp);

        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.addView(outer);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        outer.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (showIcons && app.icon != null) {
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(app.icon);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            iconLp.setMargins(0, 0, dp(10), 0);
            row.addView(icon, iconLp);
        }

        TextView summary = new TextView(activity);
        summary.setText(app.label + "\n" + app.packageName + "\n" + app.version + "\n" + app.activityCount + " activities / " + app.exportedCount + " exported");
        summary.setTextSize(13f);
        summary.setSingleLine(false);
        row.addView(summary, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(activity);
        arrow.setText("▾");
        arrow.setTextSize(18f);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout activities = new LinearLayout(activity);
        activities.setOrientation(LinearLayout.VERTICAL);
        activities.setVisibility(View.GONE);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        listLp.setMargins(0, dp(8), 0, 0);
        outer.addView(activities, listLp);

        View.OnClickListener toggle = v -> {
            boolean show = activities.getVisibility() != View.VISIBLE;
            if (show && activities.getChildCount() == 0) renderActivities(activities, app.activities);
            activities.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.setText(show ? "▴" : "▾");
        };
        View.OnLongClickListener appMenu = v -> {
            showAppContextMenu(v, app);
            return true;
        };
        card.setLongClickable(true);
        row.setLongClickable(true);
        summary.setLongClickable(true);
        arrow.setLongClickable(true);
        card.setOnLongClickListener(appMenu);
        row.setOnLongClickListener(appMenu);
        summary.setOnLongClickListener(appMenu);
        arrow.setOnLongClickListener(appMenu);
        row.setOnClickListener(toggle);
        summary.setOnClickListener(toggle);
        card.setOnClickListener(toggle);
        return card;
    }

    private void renderActivities(LinearLayout container, List<ActivityEntry> activities) {
        Activity activity = activity();
        if (activity == null) return;
        for (ActivityEntry entry : activities) {
            MaterialCardView card = new MaterialCardView(activity);
            card.setUseCompatPadding(false);
            card.setCardElevation(0f);
            card.setRadius(dp(10));
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(activityRowStroke(activity, entry.exported, entry.needsPrivilegedLaunch));
            card.setCardBackgroundColor(activityRowFill(activity, entry.exported, entry.needsPrivilegedLaunch));
            card.setClickable(true);
            card.setLongClickable(true);
            card.setOnClickListener(v -> launchActivity(entry));
            card.setOnLongClickListener(v -> {
                showActivityContextMenu(v, entry);
                return true;
            });

            TextView row = new TextView(activity);
            row.setText(entry.activityName + "\n"
                    + friendlyName(entry) + "\n"
                    + entry.packageName + "/" + entry.activityName
                    + (entry.exported ? "" : "\nNot exported")
                    + (entry.needsPrivilegedLaunch ? "\nPrivileged/root launch may be needed" : ""));
            row.setTextSize(12f);
            row.setTypeface(Typeface.MONOSPACE);
            row.setSingleLine(false);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setOnClickListener(v -> launchActivity(entry));
            row.setOnLongClickListener(v -> {
                showActivityContextMenu(v, entry);
                return true;
            });
            card.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(12), 0, 0, dp(8));
            container.addView(card, lp);
        }
    }

    private static int activityRowFill(Activity activity, boolean exported, boolean needsPrivilegedLaunch) {
        int base = ThemeColorController.isCustom(activity)
                ? ThemeColorController.getCustomColor(activity)
                : 0xFF88D8FF;
        if (needsPrivilegedLaunch) {
            int redTint = blend(base, 0xFFFF4F5F, 0.62f);
            return withAlpha(darken(redTint, 0.32f), 0x4E);
        }
        if (exported) {
            return withAlpha(darken(lighten(base, 0.10f), 0.34f), 0x4A);
        }
        return withAlpha(darken(base, 0.20f), 0x38);
    }

    private static int activityRowStroke(Activity activity, boolean exported, boolean needsPrivilegedLaunch) {
        int base = ThemeColorController.isCustom(activity)
                ? ThemeColorController.getCustomColor(activity)
                : 0xFFBFD7FF;
        if (needsPrivilegedLaunch) {
            return withAlpha(lighten(blend(base, 0xFFFF4F5F, 0.68f), 0.22f), 0xB8);
        }
        if (exported) {
            return withAlpha(lighten(base, 0.42f), 0xAA);
        }
        return withAlpha(darken(lighten(base, 0.18f), 0.48f), 0x92);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private static int darken(int color, float keep) {
        keep = Math.max(0f, Math.min(1f, keep));
        return Color.rgb(
                Math.max(0, Math.round(Color.red(color) * keep)),
                Math.max(0, Math.round(Color.green(color) * keep)),
                Math.max(0, Math.round(Color.blue(color) * keep)));
    }

    private static int lighten(int color, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.min(255, Math.round(Color.red(color) + (255 - Color.red(color)) * amount)),
                Math.min(255, Math.round(Color.green(color) + (255 - Color.green(color)) * amount)),
                Math.min(255, Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount)));
    }

    private static int blend(int from, int to, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }

    private void showAppContextMenu(View anchor, AppEntry app) {
        if (anchor == null || app == null) return;
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add("Launch the application");
        menu.getMenu().add("Open manifest viewer");
        menu.getMenu().add("Open application info");
        menu.getMenu().add("Open in app market");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("Launch the application".equals(title)) {
                launchApplication(app);
                return true;
            }
            if ("Open manifest viewer".equals(title)) {
                showAppManifestViewer(app);
                return true;
            }
            if ("Open application info".equals(title)) {
                openApplicationInfo(app.packageName);
                return true;
            }
            if ("Open in app market".equals(title)) {
                openAppMarket(app.packageName);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void launchApplication(AppEntry app) {
        Activity activity = activity();
        if (activity == null || app == null) return;
        try {
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (launch == null) {
                Toast.makeText(activity, "No launcher activity found for this app.", Toast.LENGTH_SHORT).show();
                appendOutput("Activity Manager app launch skipped, no launcher activity: " + app.packageName);
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            activity.startActivity(launch);
            appendOutput("Activity Manager launched application: " + app.packageName);
        } catch (Throwable t) {
            String message = safeMessage(t);
            Toast.makeText(activity, "Launch failed: " + message, Toast.LENGTH_LONG).show();
            appendOutput("Activity Manager app launch failed: " + app.packageName + "\n" + message);
        }
    }

    private void showAppManifestViewer(AppEntry app) {
        Activity activity = activity();
        if (activity == null || app == null) return;
        try {
            PackageManager pm = activity.getPackageManager();
            PackageInfo info = pm.getPackageInfo(app.packageName,
                    PackageManager.GET_ACTIVITIES
                            | PackageManager.GET_SERVICES
                            | PackageManager.GET_RECEIVERS
                            | PackageManager.GET_PROVIDERS
                            | PackageManager.GET_PERMISSIONS);
            StringBuilder out = new StringBuilder();
            out.append(app.label).append('\n')
                    .append(app.packageName).append('\n')
                    .append(versionText(info)).append("\n\n");
            appendComponentSummary(out, "Activities", info.activities);
            appendComponentSummary(out, "Receivers", info.receivers);
            appendComponentSummary(out, "Services", info.services);
            appendProviderSummary(out, info.providers);
            appendPermissionsSummary(out, info.requestedPermissions);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Manifest viewer")
                    .setMessage(out.toString())
                    .setPositiveButton("OK", null)
                    .show();
            appendOutput("Activity Manager manifest summary opened: " + app.packageName);
        } catch (Throwable t) {
            String message = safeMessage(t);
            Toast.makeText(activity, "Manifest summary failed: " + message, Toast.LENGTH_LONG).show();
            appendOutput("Activity Manager manifest summary failed: " + app.packageName + "\n" + message);
        }
    }

    private static void appendComponentSummary(StringBuilder out, String title, ActivityInfo[] components) {
        out.append(title).append(": ").append(components == null ? 0 : components.length).append('\n');
        if (components != null) {
            int max = Math.min(components.length, 40);
            for (int i = 0; i < max; i++) {
                ActivityInfo info = components[i];
                if (info == null) continue;
                out.append("  ").append(info.exported ? "[exported] " : "[internal] ").append(info.name).append('\n');
            }
            if (components.length > max) out.append("  ... ").append(components.length - max).append(" more\n");
        }
        out.append('\n');
    }

    private static void appendComponentSummary(StringBuilder out, String title, android.content.pm.ServiceInfo[] components) {
        out.append(title).append(": ").append(components == null ? 0 : components.length).append('\n');
        if (components != null) {
            int max = Math.min(components.length, 40);
            for (int i = 0; i < max; i++) {
                android.content.pm.ServiceInfo info = components[i];
                if (info == null) continue;
                out.append("  ").append(info.exported ? "[exported] " : "[internal] ").append(info.name).append('\n');
            }
            if (components.length > max) out.append("  ... ").append(components.length - max).append(" more\n");
        }
        out.append('\n');
    }

    private static void appendProviderSummary(StringBuilder out, android.content.pm.ProviderInfo[] providers) {
        out.append("Providers: ").append(providers == null ? 0 : providers.length).append('\n');
        if (providers != null) {
            int max = Math.min(providers.length, 40);
            for (int i = 0; i < max; i++) {
                android.content.pm.ProviderInfo info = providers[i];
                if (info == null) continue;
                out.append("  ").append(info.exported ? "[exported] " : "[internal] ")
                        .append(info.name);
                if (!TextUtils.isEmpty(info.authority)) {
                    out.append("\n    authority: ").append(info.authority);
                }
                out.append('\n');
            }
            if (providers.length > max) out.append("  ... ").append(providers.length - max).append(" more\n");
        }
        out.append('\n');
    }

    private static void appendPermissionsSummary(StringBuilder out, String[] permissions) {
        out.append("Requested permissions: ").append(permissions == null ? 0 : permissions.length).append('\n');
        if (permissions != null) {
            int max = Math.min(permissions.length, 50);
            for (int i = 0; i < max; i++) out.append("  ").append(permissions[i]).append('\n');
            if (permissions.length > max) out.append("  ... ").append(permissions.length - max).append(" more\n");
        }
    }

    private void openApplicationInfo(String packageName) {
        Activity activity = activity();
        if (activity == null || TextUtils.isEmpty(packageName)) return;
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            activity.startActivity(intent);
            appendOutput("Activity Manager opened app info: " + packageName);
        } catch (Throwable t) {
            String message = safeMessage(t);
            Toast.makeText(activity, "App info failed: " + message, Toast.LENGTH_LONG).show();
            appendOutput("Activity Manager app info failed: " + packageName + "\n" + message);
        }
    }

    private void openAppMarket(String packageName) {
        Activity activity = activity();
        if (activity == null || TextUtils.isEmpty(packageName)) return;
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
        try {
            activity.startActivity(market);
            appendOutput("Activity Manager opened market: " + packageName);
        } catch (Throwable marketError) {
            try {
                activity.startActivity(web);
                appendOutput("Activity Manager opened web market: " + packageName);
            } catch (Throwable t) {
                String message = safeMessage(t);
                Toast.makeText(activity, "Market open failed: " + message, Toast.LENGTH_LONG).show();
                appendOutput("Activity Manager market open failed: " + packageName + "\n" + message);
            }
        }
    }

    private void showActivityContextMenu(View anchor, ActivityEntry entry) {
        if (anchor == null || entry == null) return;
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add("Create Shortcut");
        menu.getMenu().add("Launch With Parameters");
        menu.getMenu().add("Launch With Root");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("Create Shortcut".equals(title)) {
                createActivityShortcut(entry);
                return true;
            }
            if ("Launch With Parameters".equals(title)) {
                showLaunchParametersDialog(entry);
                return true;
            }
            if ("Launch With Root".equals(title)) {
                launchActivityWithRoot(entry);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void createActivityShortcut(ActivityEntry entry) {
        Activity activity = activity();
        if (activity == null || entry == null) return;
        final String component = entry.packageName + "/" + entry.activityName;
        final String label = friendlyName(entry);
        final Intent launch = buildActivityIntent(entry);
        final Bitmap iconBitmap = buildActivityShortcutBitmap(entry.packageName);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ShortcutManager sm = activity.getSystemService(ShortcutManager.class);
                if (sm == null || !sm.isRequestPinShortcutSupported()) {
                    Toast.makeText(activity, "Pinned shortcuts are not supported by this launcher.", Toast.LENGTH_SHORT).show();
                    return;
                }
                ShortcutInfo.Builder builder = new ShortcutInfo.Builder(activity, buildActivityShortcutId(entry))
                        .setShortLabel(TextUtils.isEmpty(label) ? entry.activityName : label)
                        .setLongLabel(component)
                        .setIntent(launch);
                Icon icon = buildActivityShortcutIcon(iconBitmap);
                if (icon != null) builder.setIcon(icon);
                sm.requestPinShortcut(builder.build(), null);
                Toast.makeText(activity, "Shortcut requested", Toast.LENGTH_SHORT).show();
            } else {
                Intent install = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
                install.putExtra(Intent.EXTRA_SHORTCUT_NAME, TextUtils.isEmpty(label) ? entry.activityName : label);
                install.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch);
                if (iconBitmap != null) install.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap);
                activity.sendBroadcast(install);
                Toast.makeText(activity, "Shortcut requested", Toast.LENGTH_SHORT).show();
            }
            appendOutput("Activity Manager shortcut requested: " + component
                    + (entry.exported ? "" : "\nNote: non-exported activities may not launch from a normal launcher shortcut."));
        } catch (Throwable t) {
            String message = safeMessage(t);
            Toast.makeText(activity, "Shortcut failed: " + message, Toast.LENGTH_LONG).show();
            appendOutput("Activity Manager shortcut failed: " + component + "\n" + message);
        }
    }

    private void showLaunchParametersDialog(ActivityEntry entry) {
        Activity activity = activity();
        if (activity == null || entry == null) return;
        final String component = entry.packageName + "/" + entry.activityName;
        final EditText commandField = new EditText(activity);
        commandField.setSingleLine(false);
        commandField.setMinLines(3);
        commandField.setText("am start -n " + shellQuote(component));
        commandField.setSelectAllOnFocus(false);
        commandField.setHint("am start arguments");
        int pad = dp(16);
        commandField.setPadding(pad, dp(8), pad, dp(8));

        final TextView note = new TextView(activity);
        note.setText("Edit the shell command before launch. Examples: add --es key value, --ei key 1, -a ACTION, -d URI, or flags supported by am start.");
        note.setTextSize(12f);
        note.setAlpha(0.78f);
        note.setPadding(pad, dp(4), pad, 0);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(commandField, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(note, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Launch With Parameters")
                .setView(box)
                .setPositiveButton("Launch", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String command = commandField.getText() == null ? "" : commandField.getText().toString().trim();
            if (TextUtils.isEmpty(command)) {
                Toast.makeText(activity, "Command is empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            appendOutput("Activity Manager launch with parameters: " + command);
            runActivityShellCommand(command, "Activity Manager parameter launch");
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void launchActivityWithRoot(ActivityEntry entry) {
        if (entry == null) return;
        String component = entry.packageName + "/" + entry.activityName;
        String inner = "am start -n " + shellQuote(component);
        String command = "su -c " + shellQuote(inner);
        appendOutput("Activity Manager launching with root: " + component);
        runActivityShellCommand(command, "Activity Manager root launch");
    }

    private void runActivityShellCommand(String command, String label) {
        Host h = host;
        if (h == null) return;
        h.runShellCommandCapture(command, (code, stdout, stderr) -> {
            StringBuilder out = new StringBuilder();
            out.append(label == null ? "Activity Manager launch" : label).append(" exit=").append(code);
            if (!TextUtils.isEmpty(stdout)) out.append("\nstdout: ").append(stdout.trim());
            if (!TextUtils.isEmpty(stderr)) out.append("\nstderr: ").append(stderr.trim());
            appendOutput(out.toString());
        });
    }

    private Intent buildActivityIntent(ActivityEntry entry) {
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.setComponent(new ComponentName(entry.packageName, entry.activityName));
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return launch;
    }

    private String buildActivityShortcutId(ActivityEntry entry) {
        String seed = (entry == null ? "" : entry.packageName + "/" + entry.activityName);
        return "activity-" + Integer.toHexString(seed.hashCode());
    }

    private Icon buildActivityShortcutIcon(Bitmap bitmap) {
        if (Build.VERSION.SDK_INT < 26) return null;
        return bitmap == null ? Icon.createWithResource(activity(), R.mipmap.ic_launcher) : Icon.createWithBitmap(bitmap);
    }

    private Bitmap buildActivityShortcutBitmap(String packageName) {
        Activity activity = activity();
        if (activity == null) return null;
        try {
            Drawable d = null;
            if (!TextUtils.isEmpty(packageName)) {
                try { d = activity.getPackageManager().getApplicationIcon(packageName); } catch (Throwable ignored) {}
            }
            if (d == null) d = activity.getApplicationInfo().loadIcon(activity.getPackageManager());
            int size = Math.max(dp(48), dp(72));
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            d.setBounds(0, 0, size, size);
            d.draw(canvas);
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void launchActivity(ActivityEntry entry) {
        TabToolsBinding b = toolsBinding();
        if (b != null && b.chkToolsActivityUseShizuku.isChecked()) {
            String component = entry.packageName + "/" + entry.activityName;
            String command = "am start -n " + shellQuote(component);
            appendOutput("Activity Manager launching with Shizuku/shell: " + component);
            Host h = host;
            if (h != null) {
                h.runShellCommandCapture(command, (code, stdout, stderr) -> {
                    String summary = "Activity Manager launch exit=" + code + " " + component;
                    appendOutput(summary + (TextUtils.isEmpty(stderr) ? "" : "\nstderr: " + stderr.trim()));
                });
            }
            return;
        }
        Activity activity = activity();
        if (activity == null) return;
        try {
            Intent intent = buildActivityIntent(entry);
            activity.startActivity(intent);
            appendOutput("Activity Manager launched: " + entry.packageName + "/" + entry.activityName);
        } catch (Throwable t) {
            String message = t.getMessage() == null ? t.toString() : t.getMessage();
            Toast.makeText(activity, "Launch failed: " + message, Toast.LENGTH_LONG).show();
            appendOutput("Activity Manager launch failed: " + entry.packageName + "/" + entry.activityName + "\n" + message);
        }
    }

    private static String versionText(PackageInfo info) {
        String name = info.versionName == null ? "" : info.versionName;
        long code;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            code = info.getLongVersionCode();
        } else {
            code = info.versionCode;
        }
        if (TextUtils.isEmpty(name)) return "Version " + code;
        return "Version " + name + " (" + code + ")";
    }

    private static String friendlyName(ActivityEntry entry) {
        if (!TextUtils.isEmpty(entry.label) && !entry.label.equals(entry.activityName)) {
            return entry.label;
        }
        int dot = entry.activityName.lastIndexOf('.');
        return dot >= 0 && dot + 1 < entry.activityName.length()
                ? entry.activityName.substring(dot + 1)
                : entry.activityName;
    }

    private static boolean looksLikeRootActivity(String packageName, String activityName) {
        String value = (String.valueOf(packageName) + " " + String.valueOf(activityName)).toLowerCase(Locale.US);
        return value.contains("root")
                || value.contains("supersu")
                || value.contains("superuser")
                || value.contains("magisk")
                || value.contains("/su")
                || value.contains(".su.");
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown error";
        String message = t.getMessage();
        return TextUtils.isEmpty(message) ? t.getClass().getSimpleName() : message;
    }

    private static String shellQuote(String text) {
        if (text == null) return "''";
        return "'" + text.replace("'", "'\\''") + "'";
    }

    private void setStatus(String message) {
        TabToolsBinding b = toolsBinding();
        if (b != null) b.txtToolsActivityStatus.setText(message == null ? "" : message);
    }

    private void appendOutput(String message) {
        if (host != null && !TextUtils.isEmpty(message)) host.appendOutput(message);
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private ActivityMainBinding binding() {
        return host == null ? null : host.getBinding();
    }

    private TabToolsBinding toolsBinding() {
        ActivityMainBinding binding = binding();
        return binding == null ? null : binding.tabTools;
    }

    private int dp(int value) {
        Activity activity = activity();
        float density = activity == null ? 1f : activity.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static final class AppEntry {
        final String label;
        final String packageName;
        final String version;
        final int activityCount;
        final int exportedCount;
        final Drawable icon;
        final List<ActivityEntry> activities;

        AppEntry(String label, String packageName, String version, int activityCount, int exportedCount, Drawable icon, List<ActivityEntry> activities) {
            this.label = label;
            this.packageName = packageName;
            this.version = version;
            this.activityCount = activityCount;
            this.exportedCount = exportedCount;
            this.icon = icon;
            this.activities = activities;
        }
    }

    private static final class ActivityEntry {
        final String activityName;
        final String label;
        final String packageName;
        final boolean exported;
        final boolean needsPrivilegedLaunch;

        ActivityEntry(String activityName, String label, String packageName, boolean exported, boolean needsPrivilegedLaunch) {
            this.activityName = activityName;
            this.label = label;
            this.packageName = packageName;
            this.exported = exported;
            this.needsPrivilegedLaunch = needsPrivilegedLaunch;
        }
    }
}
