package dev.perms.test.tools.permissions;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.databinding.TabToolsBinding;

/** Foundation UI for building manifest permission patch plans from the Tools tab. */
public final class ToolsPermissionsTesterController {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        void appendOutput(String message);
    }

    private static final String GROUP_CAMERA_MIC = "Camera and Microphone";
    private static final String GROUP_LOCATION = "Location";
    private static final String GROUP_CONTACTS_ACCOUNTS = "Contacts and Accounts";
    private static final String GROUP_CALENDAR = "Calendar";
    private static final String GROUP_PHONE_SMS = "Phone and SMS";
    private static final String GROUP_FILES_STORAGE = "Files and Storage";
    private static final String GROUP_MEDIA_ANDROID_13 = "Android 13+ Media";
    private static final String GROUP_BLUETOOTH_NEARBY = "Bluetooth and Nearby";
    private static final String GROUP_SENSORS_ACTIVITY = "Sensors and Activity";
    private static final String GROUP_PACKAGES_APPS = "Packages and Apps";
    private static final String GROUP_NETWORK_CONNECTIVITY = "Network and Connectivity";
    private static final String GROUP_FOREGROUND_SERVICES = "Foreground Services";
    private static final String GROUP_SYSTEM_SPECIAL = "System and Special";
    private static final String GROUP_CUSTOM = "Custom";
    private static final String DEFAULT_GENERATED_PACKAGE = "dev.perms.test.generated.permissiontest";
    private static final String DEFAULT_LAUNCHABLE_LABEL = "PermsTest Permission Test";
    private static final String DEFAULT_MANIFEST_ONLY_LABEL = "PermsTest Manifest Test";
    private static final String DEFAULT_THIRD_PARTY_LABEL = "APK Permission Repack";
    private static final String MANIFEST_ONLY_SUFFIX = ".manifestonly";

    private static final String[] PERMISSION_GROUP_ORDER = new String[] {
            GROUP_CAMERA_MIC,
            GROUP_LOCATION,
            GROUP_CONTACTS_ACCOUNTS,
            GROUP_CALENDAR,
            GROUP_PHONE_SMS,
            GROUP_FILES_STORAGE,
            GROUP_MEDIA_ANDROID_13,
            GROUP_BLUETOOTH_NEARBY,
            GROUP_SENSORS_ACTIVITY,
            GROUP_PACKAGES_APPS,
            GROUP_NETWORK_CONNECTIVITY,
            GROUP_FOREGROUND_SERVICES,
            GROUP_SYSTEM_SPECIAL,
            GROUP_CUSTOM
    };

    private static final PermissionItem[] DEFAULT_PERMISSIONS = new PermissionItem[] {
            new PermissionItem(GROUP_CAMERA_MIC, "Camera", "android.permission.CAMERA", true),
            new PermissionItem(GROUP_CAMERA_MIC, "Microphone", "android.permission.RECORD_AUDIO", true),

            new PermissionItem(GROUP_LOCATION, "Background location", "android.permission.ACCESS_BACKGROUND_LOCATION", false),
            new PermissionItem(GROUP_LOCATION, "Coarse location", "android.permission.ACCESS_COARSE_LOCATION", true),
            new PermissionItem(GROUP_LOCATION, "Fine location", "android.permission.ACCESS_FINE_LOCATION", true),

            new PermissionItem(GROUP_CONTACTS_ACCOUNTS, "Accounts", "android.permission.GET_ACCOUNTS", false),
            new PermissionItem(GROUP_CONTACTS_ACCOUNTS, "Contacts read", "android.permission.READ_CONTACTS", true),
            new PermissionItem(GROUP_CONTACTS_ACCOUNTS, "Contacts write", "android.permission.WRITE_CONTACTS", false),

            new PermissionItem(GROUP_CALENDAR, "Calendar read", "android.permission.READ_CALENDAR", false),
            new PermissionItem(GROUP_CALENDAR, "Calendar write", "android.permission.WRITE_CALENDAR", false),

            new PermissionItem(GROUP_PHONE_SMS, "Call log read", "android.permission.READ_CALL_LOG", false),
            new PermissionItem(GROUP_PHONE_SMS, "Call log write", "android.permission.WRITE_CALL_LOG", false),
            new PermissionItem(GROUP_PHONE_SMS, "MMS receive", "android.permission.RECEIVE_MMS", false),
            new PermissionItem(GROUP_PHONE_SMS, "Phone call", "android.permission.CALL_PHONE", false),
            new PermissionItem(GROUP_PHONE_SMS, "Phone state", "android.permission.READ_PHONE_STATE", true),
            new PermissionItem(GROUP_PHONE_SMS, "SMS read", "android.permission.READ_SMS", false),
            new PermissionItem(GROUP_PHONE_SMS, "SMS receive", "android.permission.RECEIVE_SMS", false),
            new PermissionItem(GROUP_PHONE_SMS, "SMS send", "android.permission.SEND_SMS", false),

            new PermissionItem(GROUP_FILES_STORAGE, "Access media location", "android.permission.ACCESS_MEDIA_LOCATION", false),
            new PermissionItem(GROUP_FILES_STORAGE, "All files access", "android.permission.MANAGE_EXTERNAL_STORAGE", false),
            new PermissionItem(GROUP_FILES_STORAGE, "Read storage", "android.permission.READ_EXTERNAL_STORAGE", false),
            new PermissionItem(GROUP_FILES_STORAGE, "Write media storage", "android.permission.WRITE_MEDIA_STORAGE", false),
            new PermissionItem(GROUP_FILES_STORAGE, "Write storage", "android.permission.WRITE_EXTERNAL_STORAGE", false),

            new PermissionItem(GROUP_MEDIA_ANDROID_13, "Audio/media", "android.permission.READ_MEDIA_AUDIO", false),
            new PermissionItem(GROUP_MEDIA_ANDROID_13, "Notifications", "android.permission.POST_NOTIFICATIONS", true),
            new PermissionItem(GROUP_MEDIA_ANDROID_13, "Photos/images", "android.permission.READ_MEDIA_IMAGES", true),
            new PermissionItem(GROUP_MEDIA_ANDROID_13, "Selected media", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED", false),
            new PermissionItem(GROUP_MEDIA_ANDROID_13, "Videos", "android.permission.READ_MEDIA_VIDEO", false),

            new PermissionItem(GROUP_BLUETOOTH_NEARBY, "Bluetooth", "android.permission.BLUETOOTH", false),
            new PermissionItem(GROUP_BLUETOOTH_NEARBY, "Bluetooth admin", "android.permission.BLUETOOTH_ADMIN", false),
            new PermissionItem(GROUP_BLUETOOTH_NEARBY, "Bluetooth advertise", "android.permission.BLUETOOTH_ADVERTISE", false),
            new PermissionItem(GROUP_BLUETOOTH_NEARBY, "Bluetooth connect", "android.permission.BLUETOOTH_CONNECT", false),
            new PermissionItem(GROUP_BLUETOOTH_NEARBY, "Bluetooth scan", "android.permission.BLUETOOTH_SCAN", false),
            new PermissionItem(GROUP_BLUETOOTH_NEARBY, "Nearby Wi-Fi", "android.permission.NEARBY_WIFI_DEVICES", false),

            new PermissionItem(GROUP_SENSORS_ACTIVITY, "Activity recognition", "android.permission.ACTIVITY_RECOGNITION", false),
            new PermissionItem(GROUP_SENSORS_ACTIVITY, "Biometric", "android.permission.USE_BIOMETRIC", false),
            new PermissionItem(GROUP_SENSORS_ACTIVITY, "Body sensors", "android.permission.BODY_SENSORS", false),
            new PermissionItem(GROUP_SENSORS_ACTIVITY, "Body sensors bg", "android.permission.BODY_SENSORS_BACKGROUND", false),

            new PermissionItem(GROUP_PACKAGES_APPS, "Delete packages", "android.permission.REQUEST_DELETE_PACKAGES", false),
            new PermissionItem(GROUP_PACKAGES_APPS, "Install packages", "android.permission.REQUEST_INSTALL_PACKAGES", false),
            new PermissionItem(GROUP_PACKAGES_APPS, "Query packages", "android.permission.QUERY_ALL_PACKAGES", false),

            new PermissionItem(GROUP_NETWORK_CONNECTIVITY, "Change network", "android.permission.CHANGE_NETWORK_STATE", false),
            new PermissionItem(GROUP_NETWORK_CONNECTIVITY, "Change Wi-Fi", "android.permission.CHANGE_WIFI_STATE", false),
            new PermissionItem(GROUP_NETWORK_CONNECTIVITY, "Internet", "android.permission.INTERNET", false),
            new PermissionItem(GROUP_NETWORK_CONNECTIVITY, "Network state", "android.permission.ACCESS_NETWORK_STATE", false),
            new PermissionItem(GROUP_NETWORK_CONNECTIVITY, "Wi-Fi multicast", "android.permission.CHANGE_WIFI_MULTICAST_STATE", false),
            new PermissionItem(GROUP_NETWORK_CONNECTIVITY, "Wi-Fi state", "android.permission.ACCESS_WIFI_STATE", false),

            new PermissionItem(GROUP_FOREGROUND_SERVICES, "FGS camera", "android.permission.FOREGROUND_SERVICE_CAMERA", false),
            new PermissionItem(GROUP_FOREGROUND_SERVICES, "FGS location", "android.permission.FOREGROUND_SERVICE_LOCATION", false),
            new PermissionItem(GROUP_FOREGROUND_SERVICES, "FGS microphone", "android.permission.FOREGROUND_SERVICE_MICROPHONE", false),
            new PermissionItem(GROUP_FOREGROUND_SERVICES, "Foreground service", "android.permission.FOREGROUND_SERVICE", false),

            new PermissionItem(GROUP_SYSTEM_SPECIAL, "Boot completed", "android.permission.RECEIVE_BOOT_COMPLETED", false),
            new PermissionItem(GROUP_SYSTEM_SPECIAL, "Draw overlays", "android.permission.SYSTEM_ALERT_WINDOW", false),
            new PermissionItem(GROUP_SYSTEM_SPECIAL, "Modify audio", "android.permission.MODIFY_AUDIO_SETTINGS", false),
            new PermissionItem(GROUP_SYSTEM_SPECIAL, "Exact alarm", "android.permission.SCHEDULE_EXACT_ALARM", false),
            new PermissionItem(GROUP_SYSTEM_SPECIAL, "Ignore battery opt", "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", false),
            new PermissionItem(GROUP_SYSTEM_SPECIAL, "NFC", "android.permission.NFC", false),
            new PermissionItem(GROUP_SYSTEM_SPECIAL, "Usage stats", "android.permission.PACKAGE_USAGE_STATS", false),
            new PermissionItem(GROUP_SYSTEM_SPECIAL, "Use exact alarm", "android.permission.USE_EXACT_ALARM", false),
            new PermissionItem(GROUP_SYSTEM_SPECIAL, "Vibrate", "android.permission.VIBRATE", false),
            new PermissionItem(GROUP_SYSTEM_SPECIAL, "Wake lock", "android.permission.WAKE_LOCK", false),
            new PermissionItem(GROUP_SYSTEM_SPECIAL, "Write settings", "android.permission.WRITE_SETTINGS", false)
    };

    private final Host host;
    private final ArrayList<PermissionItem> permissions = new ArrayList<>();
    private final ArrayList<MaterialCheckBox> permissionChecks = new ArrayList<>();
    private ActivityResultLauncher<Intent> pickThirdPartyApkLauncher;
    private Uri thirdPartyApkUri;
    private String thirdPartyApkName;
    private boolean bound;

    public ToolsPermissionsTesterController(Host host) {
        this.host = host;
    }

    public void registerActivityResults() {
        try {
            if (pickThirdPartyApkLauncher != null) return;
            Activity activity = activity();
            if (!(activity instanceof ComponentActivity)) return;
            pickThirdPartyApkLauncher = ((ComponentActivity) activity).registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::handlePickedThirdPartyApk);
        } catch (Throwable ignored) {
        }
    }

    public void bind() {
        if (bound) return;
        TabToolsBinding b = toolsBinding();
        Activity activity = activity();
        if (b == null || activity == null) return;
        bound = true;

        if (permissions.isEmpty()) {
            for (PermissionItem item : DEFAULT_PERMISSIONS) permissions.add(item);
        }
        buildPermissionRows(activity, b);
        b.btnToolsPermissionsCreateApk.setOnClickListener(v -> createNewApkPlan());
        b.btnToolsPermissionsCopyManifest.setOnClickListener(v -> copyFullManifest());
        b.btnToolsPermissionsCommon.setOnClickListener(v -> selectCommon(true));
        b.btnToolsPermissionsSelectAll.setOnClickListener(v -> selectAll(true));
        b.btnToolsPermissionsSelectNone.setOnClickListener(v -> selectAll(false));
        b.btnToolsPermissionsLoadFromApp.setOnClickListener(v -> loadPermissionsFromSelectedApk());
        b.btnToolsPermissionsClear.setOnClickListener(v -> clearSelection());
        b.btnToolsPermissionsCopy.setOnClickListener(v -> copySelectedManifestSnippet());
        b.btnToolsPermissionsAddCustom.setOnClickListener(v -> addCustomPermissions());
        b.chkToolsPermissionsThirdPartyApps.setOnCheckedChangeListener((buttonView, checked) -> updateThirdPartyUi());
        b.rgToolsPermissionsGeneratedMode.setOnCheckedChangeListener((group, checkedId) -> updateThirdPartyUi());
        b.btnToolsPermissionsBrowseApk.setOnClickListener(v -> launchThirdPartyApkPicker());
        updateThirdPartyUi();
    }

    private void buildPermissionRows(Activity activity, TabToolsBinding b) {
        b.llToolsPermissionsTesterList.removeAllViews();
        permissionChecks.clear();

        Map<String, List<PermissionItem>> groups = groupPermissions();
        for (String group : PERMISSION_GROUP_ORDER) {
            List<PermissionItem> items = groups.get(group);
            if (items == null || items.isEmpty()) continue;
            addSectionLabel(activity, b.llToolsPermissionsTesterList, group);
            addCheckboxRows(activity, b.llToolsPermissionsTesterList, items);
        }
    }

    private Map<String, List<PermissionItem>> groupPermissions() {
        LinkedHashMap<String, List<PermissionItem>> groups = new LinkedHashMap<>();
        for (String group : PERMISSION_GROUP_ORDER) groups.put(group, new ArrayList<>());
        for (PermissionItem item : permissions) {
            List<PermissionItem> list = groups.get(item.group);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(item.group, list);
            }
            list.add(item);
        }
        for (List<PermissionItem> list : groups.values()) {
            Collections.sort(list, Comparator.comparing(o -> o.label.toLowerCase(java.util.Locale.US)));
        }
        return groups;
    }

    private void addCheckboxRows(Activity activity, LinearLayout container, List<PermissionItem> items) {
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if ((i & 1) == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                container.addView(row, rowLp);
            }

            PermissionItem item = items.get(i);
            MaterialCheckBox cb = new MaterialCheckBox(activity);
            cb.setText(displayLabel(item));
            cb.setTag(item);
            cb.setSingleLine(!GROUP_CUSTOM.equals(item.group));
            cb.setMaxLines(GROUP_CUSTOM.equals(item.group) ? 2 : 1);
            cb.setContentDescription(item.permission);
            cb.setTextSize(13f);
            cb.setMinHeight(dp(activity, 40));
            cb.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if ((i & 1) == 1) lp.leftMargin = dp(activity, 8);
            if (row != null) row.addView(cb, lp);
            permissionChecks.add(cb);
        }
        if ((items.size() & 1) == 1 && container.getChildCount() > 0) {
            View last = container.getChildAt(container.getChildCount() - 1);
            if (last instanceof LinearLayout) {
                SpaceFiller.add(activity, (LinearLayout) last);
            }
        }
    }

    private void addSectionLabel(Activity activity, LinearLayout container, String label) {
        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setTextSize(12f);
        tv.setAlpha(0.78f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(activity, container.getChildCount() == 0 ? 2 : 8);
        lp.bottomMargin = dp(activity, 2);
        container.addView(tv, lp);
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void updateThirdPartyUi() {
        TabToolsBinding b = toolsBinding();
        if (b == null) return;
        boolean thirdParty = b.chkToolsPermissionsThirdPartyApps.isChecked();
        boolean manifestOnly = !thirdParty && isManifestOnlyModeSelected();

        applyDefaultPackageAndLabelForMode(b, thirdParty, manifestOnly);

        try { b.btnToolsPermissionsBrowseApk.setEnabled(thirdParty); } catch (Throwable ignored) {}
        try { b.btnToolsPermissionsLoadFromApp.setEnabled(thirdParty && thirdPartyApkUri != null); } catch (Throwable ignored) {}
        try { b.txtToolsPermissionsSelectedApk.setEnabled(true); } catch (Throwable ignored) {}
        try { b.rdoToolsPermissionsLaunchableApk.setEnabled(!thirdParty); } catch (Throwable ignored) {}
        try { b.rdoToolsPermissionsManifestOnlyApk.setEnabled(!thirdParty); } catch (Throwable ignored) {}

        if (thirdParty) {
            String name = TextUtils.isEmpty(thirdPartyApkName) ? "No APK selected" : thirdPartyApkName;
            b.txtToolsPermissionsSelectedApk.setText("Selected APK: " + name);
            setStatus("3rd Party Apps mode is enabled. Browse a single APK, use Load From App to select its declared permissions, then Create New APK to make a signed permission-repacked copy. The selected APK package name is preserved.");
        } else if (manifestOnly) {
            String pkg = effectiveGeneratedPackageName(true);
            b.txtToolsPermissionsSelectedApk.setText("Generated manifest-only APK: " + pkg + " (Settings > Apps only)");
            setStatus("Manifest-only mode is enabled. Create New APK builds a no-code package that installs and requests permissions in the manifest, but it has no icon, no Open action, and no runtime UI.");
        } else {
            String pkg = effectiveGeneratedPackageName(false);
            b.txtToolsPermissionsSelectedApk.setText("Generated launchable test APK: " + pkg + " (icon/Open enabled)");
            setStatus("Launchable app mode is enabled. Create New APK builds a real generated test app with code, selected permissions, and Android's runtime permission screen.");
        }
    }

    private void applyDefaultPackageAndLabelForMode(TabToolsBinding b, boolean thirdParty, boolean manifestOnly) {
        if (b == null) return;
        String currentPackage = clean(b.edtToolsPermissionsPackage.getText());
        String currentLabel = clean(b.edtToolsPermissionsLabel.getText());
        if (isAutoPackageDefault(currentPackage)) {
            b.edtToolsPermissionsPackage.setText(manifestOnly ? DEFAULT_GENERATED_PACKAGE + MANIFEST_ONLY_SUFFIX : DEFAULT_GENERATED_PACKAGE);
        }
        if (isAutoLabelDefault(currentLabel)) {
            b.edtToolsPermissionsLabel.setText(thirdParty ? DEFAULT_THIRD_PARTY_LABEL : (manifestOnly ? DEFAULT_MANIFEST_ONLY_LABEL : DEFAULT_LAUNCHABLE_LABEL));
        }
    }

    private boolean isAutoPackageDefault(String value) {
        String v = value == null ? "" : value.trim();
        return TextUtils.isEmpty(v)
                || DEFAULT_GENERATED_PACKAGE.equals(v)
                || (DEFAULT_GENERATED_PACKAGE + MANIFEST_ONLY_SUFFIX).equals(v);
    }

    private boolean isAutoLabelDefault(String value) {
        String v = value == null ? "" : value.trim();
        return TextUtils.isEmpty(v)
                || DEFAULT_LAUNCHABLE_LABEL.equals(v)
                || DEFAULT_MANIFEST_ONLY_LABEL.equals(v)
                || DEFAULT_THIRD_PARTY_LABEL.equals(v)
                || "PermsTest Permission Manifest Test".equals(v);
    }

    private boolean thirdPartyMode() {
        TabToolsBinding b = toolsBinding();
        return b != null && b.chkToolsPermissionsThirdPartyApps != null && b.chkToolsPermissionsThirdPartyApps.isChecked();
    }

    private void launchThirdPartyApkPicker() {
        Activity activity = activity();
        if (activity == null) return;
        if (pickThirdPartyApkLauncher == null) {
            Toast.makeText(activity, "APK picker is not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] types = new String[] {
                "application/vnd.android.package-archive",
                "application/zip",
                "application/octet-stream",
                "application/x-zip-compressed",
                "application/vnd.apkm",
                "application/x-apks",
                "application/x-xapk"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
        try {
            pickThirdPartyApkLauncher.launch(intent);
        } catch (Throwable t) {
            Toast.makeText(activity, "Open APK picker failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handlePickedThirdPartyApk(ActivityResult result) {
        Activity activity = activity();
        if (activity == null || result == null || result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        Uri uri = result.getData().getData();
        if (uri == null) return;
        try {
            final int flags = result.getData().getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            activity.getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        thirdPartyApkUri = uri;
        thirdPartyApkName = displayName(activity, uri);
        if (TextUtils.isEmpty(thirdPartyApkName)) thirdPartyApkName = uri.toString();
        updateThirdPartyUi();
        setStatus("Selected 3rd party APK: " + thirdPartyApkName + ". Create New APK will create a signed permission-repacked APK for this source.");
        append("[Permissions Tester] Selected 3rd party APK: " + thirdPartyApkName + "\n" + uri + "\n");
    }

    private String displayName(Context context, Uri uri) {
        if (context == null || uri == null) return "";
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) return cursor.getString(idx);
                }
            } catch (Throwable ignored) {
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last;
    }

    private void loadPermissionsFromSelectedApk() {
        Activity activity = activity();
        if (activity == null) return;
        if (!thirdPartyMode() || thirdPartyApkUri == null) {
            Toast.makeText(activity, "Select a 3rd party APK first", Toast.LENGTH_SHORT).show();
            setStatus("Load From App needs 3rd Party Apps enabled and a selected APK.");
            return;
        }
        setBusy(true, "Reading declared permissions from selected APK...");
        append("[Permissions Tester] Reading permissions from selected APK: " + (TextUtils.isEmpty(thirdPartyApkName) ? thirdPartyApkUri : thirdPartyApkName) + "\n");
        new Thread(() -> {
            File staged = null;
            PermissionApkManifestInspector.Result inspect = null;
            List<String> declared = Collections.emptyList();
            String error = "";
            try {
                staged = stageThirdPartySource(thirdPartyApkUri, thirdPartyApkName);
                inspect = PermissionApkManifestInspector.readDeclaredPermissionsResult(staged);
                declared = inspect.permissions;
            } catch (Throwable t) {
                error = message(t);
            } finally {
                try { if (staged != null) staged.delete(); } catch (Throwable ignored) {}
            }
            final List<String> finalDeclared = declared;
            final PermissionApkManifestInspector.Result finalInspect = inspect;
            final String finalError = error;
            activity.runOnUiThread(() -> {
                setBusy(false, "");
                if (!TextUtils.isEmpty(finalError)) {
                    setStatus("Load From App failed: " + finalError);
                    append("[Permissions Tester] Load From App failed: " + finalError + "\n");
                    Toast.makeText(activity, "Load From App failed", Toast.LENGTH_SHORT).show();
                    return;
                }
                applyDeclaredPermissionsFromApp(finalDeclared, finalInspect);
            });
        }, "PermsTest-PermissionTester-LoadFromApp").start();
    }

    private void applyDeclaredPermissionsFromApp(List<String> declared, PermissionApkManifestInspector.Result inspect) {
        Activity activity = activity();
        TabToolsBinding b = toolsBinding();
        if (activity == null || b == null) return;
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (declared != null) {
            for (String raw : declared) {
                String permission = normalizePermissionName(raw);
                if (!TextUtils.isEmpty(permission)) values.add(permission);
            }
        }
        if (values.isEmpty()) {
            selectAll(false);
            setStatus("Selected APK declares no uses-permission entries.");
            append("[Permissions Tester] Selected APK declares no uses-permission entries.\n");
            return;
        }

        LinkedHashSet<String> existing = new LinkedHashSet<>();
        for (PermissionItem item : permissions) existing.add(item.permission);
        int added = 0;
        for (String permission : values) {
            if (existing.add(permission)) {
                permissions.add(new PermissionItem(GROUP_CUSTOM, customLabel(permission), permission, false));
                added++;
            }
        }
        if (added > 0) buildPermissionRows(activity, b);
        for (MaterialCheckBox cb : permissionChecks) {
            Object tag = cb.getTag();
            if (tag instanceof PermissionItem) {
                cb.setChecked(values.contains(((PermissionItem) tag).permission));
            }
        }
        String msg = "Loaded " + values.size() + " permission(s) from selected "
                + (inspect != null && inspect.splitContainer ? "split container" : "APK")
                + (added > 0 ? " and added " + added + " custom checkbox(es)." : ".");
        setStatus(msg);
        append("[Permissions Tester] " + msg + "\n");
        if (inspect != null && inspect.splitContainer) {
            append("[Permissions Tester] Split container source: " + inspect.sourceEntry
                    + " (" + inspect.apkEntryCount + " nested APK(s)); only the manifest-bearing base APK was used for permission selection.\n");
        }
        if (added > 0) appendCustomPermissionLog(values);
    }


    private void appendCustomPermissionLog(LinkedHashSet<String> values) {
        LinkedHashSet<String> known = new LinkedHashSet<>();
        for (PermissionItem item : DEFAULT_PERMISSIONS) known.add(item.permission);
        ArrayList<String> unknown = new ArrayList<>();
        for (String permission : values) {
            if (!known.contains(permission)) unknown.add(permission);
        }
        if (unknown.isEmpty()) return;
        Collections.sort(unknown);
        append("[Permissions Tester] Added custom permission checkbox(es) for manifest entries not in the built-in list:\n");
        for (String permission : unknown) append("  - " + permission + "\n");
    }

    private void addCustomPermissions() {
        TabToolsBinding b = toolsBinding();
        Activity activity = activity();
        if (b == null || activity == null) return;
        List<String> names = splitPermissionNames(b.edtToolsPermissionsCustom.getText());
        if (names.isEmpty()) {
            Toast.makeText(activity, "Enter one or more permission names", Toast.LENGTH_SHORT).show();
            return;
        }
        int added = 0;
        LinkedHashSet<String> existing = new LinkedHashSet<>();
        for (PermissionItem item : permissions) existing.add(item.permission);
        for (String name : names) {
            String normalized = normalizePermissionName(name);
            if (TextUtils.isEmpty(normalized) || existing.contains(normalized)) continue;
            permissions.add(new PermissionItem(GROUP_CUSTOM, customLabel(normalized), normalized, false));
            existing.add(normalized);
            added++;
        }
        if (added <= 0) {
            Toast.makeText(activity, "Custom permission already exists", Toast.LENGTH_SHORT).show();
            return;
        }
        buildPermissionRows(activity, b);
        for (MaterialCheckBox cb : permissionChecks) {
            Object tag = cb.getTag();
            if (tag instanceof PermissionItem) {
                PermissionItem item = (PermissionItem) tag;
                if (namesContain(names, item.permission)) cb.setChecked(true);
            }
        }
        b.edtToolsPermissionsCustom.setText("");
        setStatus("Added " + added + " custom permission(s). Copy will include selected custom entries.");
    }

    private boolean namesContain(List<String> names, String normalized) {
        for (String name : names) {
            if (normalizePermissionName(name).equals(normalized)) return true;
        }
        return false;
    }

    private void selectCommon(boolean checked) {
        for (MaterialCheckBox cb : permissionChecks) {
            Object tag = cb.getTag();
            if (tag instanceof PermissionItem) {
                PermissionItem item = (PermissionItem) tag;
                cb.setChecked(checked && item.common);
            }
        }
        setStatus(checked ? "Common dangerous/runtime permissions selected." : "Permission selection cleared.");
    }

    private void selectAll(boolean checked) {
        for (MaterialCheckBox cb : permissionChecks) cb.setChecked(checked);
        setStatus(checked ? "All permissions selected." : "Permission selection cleared.");
    }

    private void clearSelection() {
        for (MaterialCheckBox cb : permissionChecks) cb.setChecked(false);
        TabToolsBinding b = toolsBinding();
        if (b != null) b.edtToolsPermissionsCustom.setText("");
        setStatus("Permission selection cleared.");
    }

    private void createNewApkPlan() {
        Activity activity = activity();
        if (activity == null) return;
        final ArrayList<String> selectedPermissions = selectedPermissionNames();
        if (selectedPermissions.isEmpty()) {
            Toast.makeText(activity, "Select at least one permission", Toast.LENGTH_SHORT).show();
            setStatus("Select at least one permission before creating an APK.");
            return;
        }
        if (thirdPartyMode() && thirdPartyApkUri == null) {
            Toast.makeText(activity, "Browse and select an APK first", Toast.LENGTH_SHORT).show();
            setStatus("3rd Party Apps mode needs a selected source APK before creating a repack.");
            return;
        }
        final boolean thirdParty = thirdPartyMode();
        final boolean permissionOnly = !thirdParty && isManifestOnlyRequested();
        final boolean debuggable = isDebuggableRequested();
        final String label = effectiveLabel();
        setBusy(true, thirdParty ? "Creating 3rd party permission-repacked APK..." : (permissionOnly ? "Creating manifest-only APK..." : "Creating generated permission-test APK..."));
        append("[Permissions Tester] Creating " + (thirdParty ? "3rd party permission repack" : (permissionOnly ? "manifest-only APK" : "generated permission-test APK")) + " with " + selectedPermissions.size() + " permission(s).\n");
        new Thread(() -> {
            PermissionTestApkCreator.Result result;
            File output = null;
            File stagedSource = null;
            try {
                output = buildOutputApkFile(thirdParty, permissionOnly, label, thirdPartyApkName);
                if (thirdParty) {
                    stagedSource = stageThirdPartySource(thirdPartyApkUri, thirdPartyApkName);
                    if (PermissionApkManifestInspector.looksLikeSplitContainer(stagedSource)) {
                        throw new IOException("3rd Party Apps repack accepts one APK file. The selected file is a split APK container; use Load From App for permission selection, then extract or select a single base APK for repack.");
                    }
                    result = PermissionTestApkCreator.repackSingleApk(activity, stagedSource, selectedPermissions, debuggable, output);
                } else {
                    if (permissionOnly) {
                        result = PermissionTestApkCreator.createPermissionOnly(activity,
                                effectiveGeneratedPackageName(true), effectivePermissionOnlyLabel(label), selectedPermissions, debuggable, output);
                    } else {
                        result = PermissionTestApkCreator.createStandalone(activity,
                                effectiveGeneratedPackageName(false), label, selectedPermissions, debuggable,
                                isLaunchableGeneratedModeRequested(), output);
                    }
                }
            } catch (Throwable t) {
                result = new PermissionTestApkCreator.Result(false, message(t), null);
            } finally {
                try { if (stagedSource != null) stagedSource.delete(); } catch (Throwable ignored) {}
            }
            final PermissionTestApkCreator.Result finalResult = result;
            activity.runOnUiThread(() -> handleCreateApkResult(finalResult, thirdParty, permissionOnly, selectedPermissions));
        }, "PermsTest-PermissionTester-ApkCreate").start();
    }

    private ArrayList<String> selectedPermissionNames() {
        ArrayList<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (MaterialCheckBox cb : permissionChecks) {
            if (!cb.isChecked()) continue;
            Object tag = cb.getTag();
            if (!(tag instanceof PermissionItem)) continue;
            String value = ((PermissionItem) tag).permission;
            if (!TextUtils.isEmpty(value) && seen.add(value)) out.add(value);
        }
        return out;
    }

    private void handleCreateApkResult(PermissionTestApkCreator.Result result, boolean thirdParty, boolean permissionOnly, List<String> selectedPermissions) {
        setBusy(false, "");
        Activity activity = activity();
        if (activity == null) return;
        if (result == null || !result.success || result.apk == null || !result.apk.isFile()) {
            String msg = result == null ? "Unknown create failure" : result.message;
            Toast.makeText(activity, "Create APK failed", Toast.LENGTH_SHORT).show();
            setStatus("Create New APK failed: " + msg);
            append("[Permissions Tester] Create APK failed: " + msg + "\n");
            showCreateFailedDialog(msg, thirdParty, permissionOnly);
            return;
        }
        String msg = (thirdParty ? "Created permission-repacked APK: " : (permissionOnly ? "Created manifest-only permission APK: " : "Created generated permission-test APK: ")) + result.apk.getAbsolutePath();
        Toast.makeText(activity, "APK created", Toast.LENGTH_SHORT).show();
        setStatus(msg);
        append("[Permissions Tester] " + msg + "\n" + result.message + "\n" + permissionSummaryForLog(selectedPermissions) + "\n");
        showCreatedApkDialog(result.apk, thirdParty, permissionOnly, selectedPermissions);
    }

    private void showCreateFailedDialog(String message, boolean thirdParty, boolean permissionOnly) {
        Activity activity = activity();
        if (activity == null) return;
        String detail = TextUtils.isEmpty(message) ? "Unknown create failure." : message;
        String body = "Permission Tester could not create the "
                + (thirdParty ? "3rd party permission-repacked APK." : (permissionOnly ? "manifest-only APK." : "generated permission-test APK."))
                + "\n\n" + detail
                + "\n\nThe full message was also written to the Permissions Tester event log.";
        try {
            new AlertDialog.Builder(activity)
                    .setTitle("Create APK Failed")
                    .setMessage(body)
                    .setPositiveButton("Copy Error", (dialog, which) -> {
                        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PermsTest Permission Tester Error", detail));
                        Toast.makeText(activity, "Error copied", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.ok, null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void showCreatedApkDialog(File apk, boolean thirdParty, boolean permissionOnly, List<String> selectedPermissions) {
        Activity activity = activity();
        if (activity == null || apk == null) return;
        String packageDetail = thirdParty ? "Uses the original selected APK package name."
                : (permissionOnly
                ? "Package: " + effectiveGeneratedPackageName(true) + "\nThis is a no-code manifest/install test. It has no app icon or UI; after install, find it under Android Settings > Apps."
                : "Package: " + effectiveGeneratedPackageName(false) + "\n" + (isLaunchableGeneratedModeRequested() ? "Launcher icon/Open support is enabled." : "Launcher icon/Open support is disabled."));
        String message = (thirdParty ? "Permission-repacked APK saved:" : (permissionOnly ? "Manifest-only permission APK saved:" : "Generated permission-test APK saved:"))
                + "\n\n" + apk.getAbsolutePath()
                + "\n\n" + packageDetail
                + "\n\n" + permissionSummaryForDialog(selectedPermissions)
                + "\n\nInstall it now, or cancel and install it later from Files/Downloads."
                + "\n\nNote: this APK is signed with the bundled debug key. Installing over an already-installed package with a different signature may require uninstalling the original first.";
        try {
            new AlertDialog.Builder(activity)
                    .setTitle("Permission Tester Output")
                    .setMessage(message)
                    .setPositiveButton("Install", (dialog, which) -> openInstaller(apk))
                    .setNeutralButton("Copy Path", (dialog, which) -> {
                        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PermsTest Permission Tester APK", apk.getAbsolutePath()));
                        Toast.makeText(activity, "APK path copied", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Throwable ignored) {
        }
    }


    private String permissionSummaryForLog(List<String> selectedPermissions) {
        if (selectedPermissions == null || selectedPermissions.isEmpty()) return "Requested permissions: (none)";
        StringBuilder sb = new StringBuilder("Requested permissions (" + selectedPermissions.size() + "):\n");
        for (String permission : selectedPermissions) sb.append("  - ").append(permission).append('\n');
        return sb.toString().trim();
    }

    private String permissionSummaryForDialog(List<String> selectedPermissions) {
        if (selectedPermissions == null || selectedPermissions.isEmpty()) return "Requested permissions: none";
        StringBuilder sb = new StringBuilder("Requested permissions: ").append(selectedPermissions.size()).append('\n');
        int shown = 0;
        for (String permission : selectedPermissions) {
            if (shown >= 12) {
                sb.append("…and ").append(selectedPermissions.size() - shown).append(" more");
                break;
            }
            sb.append("• ").append(permission).append('\n');
            shown++;
        }
        return sb.toString().trim();
    }

    private void openInstaller(File apk) {
        Activity activity = activity();
        if (activity == null || apk == null || !apk.isFile()) return;
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(activity, "Open installer failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            append("[Permissions Tester] Open installer failed: " + t + "\n");
        }
    }

    private File buildOutputApkFile(boolean thirdParty, boolean permissionOnly, String label, String sourceName) throws IOException {
        Activity activity = activity();
        if (activity == null) throw new IOException("Activity is missing.");
        File dir = publicPermissionOutputDir();
        if (!ensureWritableDir(dir)) {
            dir = activity.getExternalFilesDir("permission_tester");
            if (!ensureWritableDir(dir)) throw new IOException("Permission tester output directory unavailable.");
        }
        String base = thirdParty ? sourceName : effectiveGeneratedPackageName(permissionOnly);
        if (TextUtils.isEmpty(base)) base = thirdParty ? "permission-repack.apk" : "permission-test.apk";
        base = sanitizeFileName(base);
        String lower = base.toLowerCase(java.util.Locale.US);
        if (!lower.endsWith(".apk")) base = base + ".apk";
        int dot = base.toLowerCase(java.util.Locale.US).lastIndexOf(".apk");
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String outName = thirdParty ? stem + "-permissions.apk" : (permissionOnly ? stem + "-manifest-only.apk" : stem + "-permissions.apk");
        return uniqueFile(dir, outName);
    }

    private File publicPermissionOutputDir() {
        File root = Environment.getExternalStorageDirectory();
        return new File(root, "dev.perms.test/permission_tester");
    }

    private boolean ensureWritableDir(File dir) {
        if (dir == null) return false;
        try {
            if (!dir.exists() && !dir.mkdirs()) return false;
            File probe = File.createTempFile(".probe", ".tmp", dir);
            try (FileOutputStream out = new FileOutputStream(probe)) { out.write(1); }
            try { probe.delete(); } catch (Throwable ignored) {}
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private File stageThirdPartySource(Uri uri, String label) throws IOException {
        Activity activity = activity();
        if (activity == null || uri == null) throw new IOException("Selected APK is missing.");
        File dir = new File(activity.getCacheDir(), "permission_tester/source");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Unable to create source staging directory.");
        String name = sanitizeFileName(label);
        if (TextUtils.isEmpty(name)) name = "selected.apk";
        String lowerName = name.toLowerCase(java.util.Locale.US);
        if (!(lowerName.endsWith(".apk") || lowerName.endsWith(".apks")
                || lowerName.endsWith(".apkm") || lowerName.endsWith(".xapk"))) {
            name += ".apk";
        }
        File outFile = new File(dir, name);
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(outFile, false)) {
            if (in == null) throw new IOException("Unable to open selected APK.");
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        if (!outFile.isFile() || outFile.length() <= 0L) throw new IOException("Selected APK copy is empty.");
        return outFile;
    }

    private File uniqueFile(File dir, String name) {
        File file = new File(dir, name);
        if (!file.exists()) return file;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            File candidate = new File(dir, stem + "-" + i + ext);
            if (!candidate.exists()) return candidate;
        }
        return new File(dir, stem + "-" + System.currentTimeMillis() + ext);
    }

    private String effectivePackageName() {
        TabToolsBinding b = toolsBinding();
        String pkg = b == null ? "" : clean(b.edtToolsPermissionsPackage.getText());
        return TextUtils.isEmpty(pkg) ? DEFAULT_GENERATED_PACKAGE : pkg;
    }

    private String effectiveGeneratedPackageName(boolean permissionOnly) {
        String pkg = effectivePackageName();
        if (permissionOnly && DEFAULT_GENERATED_PACKAGE.equals(pkg)) return pkg + MANIFEST_ONLY_SUFFIX;
        return pkg;
    }

    private String effectivePermissionOnlyLabel(String label) {
        String cleanLabel = TextUtils.isEmpty(label) ? DEFAULT_MANIFEST_ONLY_LABEL : label;
        if (DEFAULT_LAUNCHABLE_LABEL.equals(cleanLabel) || DEFAULT_THIRD_PARTY_LABEL.equals(cleanLabel)) return DEFAULT_MANIFEST_ONLY_LABEL;
        return cleanLabel;
    }

    private String effectiveLabel() {
        TabToolsBinding b = toolsBinding();
        String label = b == null ? "" : clean(b.edtToolsPermissionsLabel.getText());
        return TextUtils.isEmpty(label) ? DEFAULT_LAUNCHABLE_LABEL : label;
    }

    private boolean isDebuggableRequested() {
        TabToolsBinding b = toolsBinding();
        return b != null && b.chkToolsPermissionsDebuggable != null && b.chkToolsPermissionsDebuggable.isChecked();
    }

    private boolean isLaunchableGeneratedModeRequested() {
        TabToolsBinding b = toolsBinding();
        return b == null || b.rdoToolsPermissionsLaunchableApk == null || b.rdoToolsPermissionsLaunchableApk.isChecked();
    }

    private boolean isManifestOnlyModeSelected() {
        TabToolsBinding b = toolsBinding();
        return b != null && b.rdoToolsPermissionsManifestOnlyApk != null && b.rdoToolsPermissionsManifestOnlyApk.isChecked();
    }

    private boolean isManifestOnlyRequested() {
        return !thirdPartyMode() && isManifestOnlyModeSelected();
    }

    private void setBusy(boolean busy, String status) {
        TabToolsBinding b = toolsBinding();
        if (b != null) {
            b.btnToolsPermissionsCreateApk.setEnabled(!busy);
            b.btnToolsPermissionsBrowseApk.setEnabled(!busy && b.chkToolsPermissionsThirdPartyApps.isChecked());
            b.btnToolsPermissionsLoadFromApp.setEnabled(!busy && b.chkToolsPermissionsThirdPartyApps.isChecked() && thirdPartyApkUri != null);
        }
        if (!TextUtils.isEmpty(status)) setStatus(status);
    }

    private static String sanitizeFileName(String value) {
        String out = value == null ? "" : value.trim();
        int slash = Math.max(out.lastIndexOf('/'), out.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < out.length()) out = out.substring(slash + 1);
        out = out.replaceAll("[\\r\\n\\t\\x00]+", "_");
        out = out.replaceAll("[^a-zA-Z0-9._ -]", "_");
        return out.trim();
    }

    private static String message(Throwable t) {
        if (t == null) return "Unknown error";
        String m = t.getMessage();
        return TextUtils.isEmpty(m) ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + m;
    }

    private String buildThirdPartyPatchPlan(String manifest) {
        String snippet = buildSelectedManifestSnippet();
        TabToolsBinding b = toolsBinding();
        boolean debuggable = b != null && b.chkToolsPermissionsDebuggable != null && b.chkToolsPermissionsDebuggable.isChecked();
        return "PermsTest Permission Tester - 3rd Party APK Repack Plan\n" +
                "Source APK: " + (TextUtils.isEmpty(thirdPartyApkName) ? "(selected APK)" : thirdPartyApkName) + "\n" +
                "Source URI: " + (thirdPartyApkUri == null ? "" : thirdPartyApkUri.toString()) + "\n" +
                "Debuggable package: " + debuggable + "\n" +
                "Selected permissions: " + countSelected() + "\n\n" +
                "Manifest permission lines to merge near the top of the target manifest:\n" +
                snippet + "\n\n" +
                "Generated standalone manifest reference:\n" +
                manifest;
    }

    private void copyFullManifest() {
        Activity activity = activity();
        if (activity == null) return;
        String manifest = buildFullManifest();
        if (TextUtils.isEmpty(manifest)) {
            Toast.makeText(activity, "Select at least one permission", Toast.LENGTH_SHORT).show();
            setStatus("No permissions selected for manifest copy.");
            return;
        }
        String content = thirdPartyMode() ? buildThirdPartyPatchPlan(manifest) : manifest;
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PermsTest permission manifest", content));
        Toast.makeText(activity, thirdPartyMode() ? "Repack plan copied" : "Full manifest copied", Toast.LENGTH_SHORT).show();
        setStatus("Copied " + (thirdPartyMode() ? "3rd party repack plan" : "manifest reference") + " with " + countSelected() + " permission(s).");
        append("[Permissions Tester] Copied permission output:\n" + content + "\n");
    }

    private String buildFullManifest() {
        String permissionsXml = buildSelectedManifestSnippet();
        if (TextUtils.isEmpty(permissionsXml)) return "";
        TabToolsBinding b = toolsBinding();
        String pkg = b == null ? "" : clean(b.edtToolsPermissionsPackage.getText());
        String label = b == null ? "" : clean(b.edtToolsPermissionsLabel.getText());
        boolean debuggable = b != null && b.chkToolsPermissionsDebuggable != null && b.chkToolsPermissionsDebuggable.isChecked();
        boolean launcher = isLaunchableGeneratedModeRequested();
        if (TextUtils.isEmpty(pkg)) pkg = DEFAULT_GENERATED_PACKAGE;
        if (TextUtils.isEmpty(label)) label = DEFAULT_LAUNCHABLE_LABEL;
        StringBuilder sb = new StringBuilder();
        sb.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        sb.append("    package=\"").append(xml(pkg)).append("\">\n\n");
        sb.append(permissionsXml).append("\n\n");
        sb.append("    <application\n");
        sb.append("        android:label=\"").append(xml(label)).append("\"\n");
        sb.append("        android:theme=\"@android:style/Theme.Material.Light.NoActionBar\"");
        if (debuggable) sb.append("\n        android:debuggable=\"true\"");
        sb.append(">\n");
        if (launcher) {
            sb.append("        <activity android:name=\".MainActivity\" android:exported=\"true\">\n");
            sb.append("            <intent-filter>\n");
            sb.append("                <action android:name=\"android.intent.action.MAIN\" />\n");
            sb.append("                <category android:name=\"android.intent.category.LAUNCHER\" />\n");
            sb.append("            </intent-filter>\n");
            sb.append("        </activity>\n");
        }
        sb.append("    </application>\n");
        sb.append("</manifest>\n");
        return sb.toString();
    }

    private void copySelectedManifestSnippet() {
        Activity activity = activity();
        if (activity == null) return;
        String snippet = buildSelectedManifestSnippet();
        if (TextUtils.isEmpty(snippet)) {
            Toast.makeText(activity, "No permissions selected", Toast.LENGTH_SHORT).show();
            setStatus("No permissions selected.");
            return;
        }
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PermsTest permissions", snippet));
        Toast.makeText(activity, "Permission manifest snippet copied", Toast.LENGTH_SHORT).show();
        setStatus("Copied " + countSelected() + " uses-permission line(s).");
        append("[Permissions Tester] Copied manifest snippet:\n" + snippet + "\n");
    }

    private String buildSelectedManifestSnippet() {
        StringBuilder sb = new StringBuilder();
        for (MaterialCheckBox cb : permissionChecks) {
            if (!cb.isChecked()) continue;
            Object tag = cb.getTag();
            if (!(tag instanceof PermissionItem)) continue;
            PermissionItem item = (PermissionItem) tag;
            sb.append("<uses-permission android:name=\"").append(xml(item.permission)).append("\" />\n");
        }
        return sb.toString().trim();
    }

    private int countSelected() {
        int count = 0;
        for (MaterialCheckBox cb : permissionChecks) if (cb.isChecked()) count++;
        return count;
    }

    private static List<String> splitPermissionNames(CharSequence text) {
        ArrayList<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.toString().split("[,\\s]+")) {
            String v = part == null ? "" : part.trim();
            if (!TextUtils.isEmpty(v)) out.add(v);
        }
        return out;
    }

    private static String normalizePermissionName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (TextUtils.isEmpty(value)) return "";
        if (!value.contains(".")) value = "android.permission." + value;
        return value;
    }

    private static String shortLabel(String permission) {
        int idx = permission == null ? -1 : permission.lastIndexOf('.');
        String tail = idx >= 0 ? permission.substring(idx + 1) : permission;
        return tail == null ? "Custom" : tail.replace('_', ' ').toLowerCase(java.util.Locale.US);
    }

    private static String customLabel(String permission) {
        String shortName = shortLabel(permission);
        return shortName.toUpperCase(java.util.Locale.US) + " (" + permission + ")";
    }

    private static String displayLabel(PermissionItem item) {
        if (item == null) return "";
        if (GROUP_CUSTOM.equals(item.group)) return item.label;
        return item.label;
    }

    private static String clean(CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void setStatus(String text) {
        TabToolsBinding b = toolsBinding();
        if (b != null) b.txtToolsPermissionsStatus.setText(text == null ? "" : text);
    }

    private void append(String message) {
        if (host != null) host.appendOutput(message == null ? "" : message);
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private TabToolsBinding toolsBinding() {
        ActivityMainBinding binding = host == null ? null : host.getBinding();
        return binding == null ? null : binding.tabTools;
    }

    private static final class SpaceFiller {
        static void add(Activity activity, LinearLayout row) {
            View spacer = new View(activity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.leftMargin = Math.round(8 * activity.getResources().getDisplayMetrics().density);
            row.addView(spacer, lp);
        }
    }

    private static final class PermissionItem {
        final String group;
        final String label;
        final String permission;
        final boolean common;

        PermissionItem(String group, String label, String permission, boolean common) {
            this.group = group;
            this.label = label;
            this.permission = permission;
            this.common = common;
        }
    }
}
