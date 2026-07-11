package dev.perms.test.permissions;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.perms.test.R;

public final class PermissionDropdowns {
    private PermissionDropdowns() {
    }

    public static final class Entry {
        public final String title;
        public final String subtitle;
        public final String permission;
        public final int protectionBase;

        public Entry(String title, String subtitle, String permission, int protectionBase) {
            this.title = title;
            this.subtitle = subtitle;
            this.permission = permission;
            this.protectionBase = protectionBase;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public static final class Adapter extends ArrayAdapter<Entry> {
        private final Context context;
        private final LayoutInflater inflater;
        private Integer defaultColor;
        private Integer defaultMutedColor;

        public Adapter(@NonNull Context context, @NonNull List<Entry> items) {
            super(context, 0, items);
            this.context = context;
            this.inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            // AutoCompleteTextView uses getView() for dropdown rows.
            // Use the two-line dropdown layout instead of an unpadded TextView.
            return getDropDownView(position, convertView, parent);
        }

        @NonNull
        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = convertView;
            if (view == null || view.findViewById(R.id.text1) == null) {
                view = inflater.inflate(R.layout.dropdown_item_two_line, parent, false);
            }
            TextView title = view.findViewById(R.id.text1);
            TextView subtitle = view.findViewById(R.id.text2);
            Entry entry = getItem(position);
            if (entry == null) {
                title.setText("");
                subtitle.setText("");
                return view;
            }
            title.setText(entry.title == null ? "" : entry.title);
            subtitle.setText(entry.subtitle == null ? "" : entry.subtitle);
            title.setSelected(true);
            subtitle.setSelected(true);
            bindColor(position, title, subtitle);
            return view;
        }

        private void bindColor(int position, TextView title, TextView subtitle) {
            if (title == null) return;
            if (defaultColor == null) defaultColor = title.getCurrentTextColor();
            if (subtitle != null && defaultMutedColor == null) defaultMutedColor = subtitle.getCurrentTextColor();

            Entry entry = getItem(position);
            if (entry == null) {
                title.setTextColor(defaultColor);
                if (subtitle != null) subtitle.setTextColor(defaultMutedColor == null ? defaultColor : defaultMutedColor);
                return;
            }

            int titleColor = defaultColor;
            if (entry.protectionBase == PermissionInfo.PROTECTION_DANGEROUS) {
                titleColor = ContextCompat.getColor(context, android.R.color.holo_orange_dark);
            } else if (entry.protectionBase == PermissionInfo.PROTECTION_SIGNATURE) {
                titleColor = ContextCompat.getColor(context, android.R.color.holo_blue_dark);
            }
            title.setTextColor(titleColor);
            if (subtitle != null) {
                subtitle.setTextColor(defaultMutedColor == null ? defaultColor : defaultMutedColor);
            }
        }
    }

    public static ArrayList<Entry> buildCommon(PackageManager packageManager) {
        ArrayList<Entry> out = new ArrayList<>();
        out.add(new Entry("Common Permissions", "", "", -1));
        addCommon(out, packageManager, "READ_LOGS", "android.permission.READ_LOGS");
        addCommon(out, packageManager, "WRITE_SECURE_SETTINGS", "android.permission.WRITE_SECURE_SETTINGS");
        addCommon(out, packageManager, "DUMP", "android.permission.DUMP");
        addCommon(out, packageManager, "PACKAGE_USAGE_STATS", "android.permission.PACKAGE_USAGE_STATS");
        addCommon(out, packageManager, "READ_DEVICE_CONFIG", "android.permission.READ_DEVICE_CONFIG");

        addCommon(out, packageManager, "POST_NOTIFICATIONS", "android.permission.POST_NOTIFICATIONS");
        addCommon(out, packageManager, "READ_CONTACTS", "android.permission.READ_CONTACTS");
        addCommon(out, packageManager, "ACCESS_FINE_LOCATION", "android.permission.ACCESS_FINE_LOCATION");
        addCommon(out, packageManager, "READ_PHONE_STATE", "android.permission.READ_PHONE_STATE");
        addCommon(out, packageManager, "READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_IMAGES");
        return out;
    }

    public static ArrayList<Entry> buildAll(PackageManager packageManager) {
        ArrayList<Entry> out = new ArrayList<>();
        out.add(new Entry("All Permissions", "", "", -1));
        ArrayList<String> permissions = new ArrayList<>();
        try {
            java.lang.reflect.Field[] fields = Manifest.permission.class.getFields();
            for (java.lang.reflect.Field field : fields) {
                try {
                    Object value = field.get(null);
                    if (value instanceof String) {
                        String permission = (String) value;
                        if (!TextUtils.isEmpty(permission) && !permissions.contains(permission)) {
                            permissions.add(permission);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        Collections.sort(permissions);
        for (String permission : permissions) {
            out.add(new Entry(shortName(permission), permission, permission, protectionBase(packageManager, permission)));
        }
        return out;
    }

    public static ArrayList<Entry> buildForPackage(PackageManager packageManager, String packageName) {
        ArrayList<Entry> out = emptyAppList();
        if (TextUtils.isEmpty(packageName) || packageManager == null) return out;
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= 33) {
                packageInfo = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
            } else {
                //noinspection deprecation
                packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            }
            String[] requested = packageInfo.requestedPermissions;
            if (requested == null) return out;

            ArrayList<String> permissions = new ArrayList<>();
            for (String permission : requested) {
                if (!TextUtils.isEmpty(permission)) permissions.add(permission);
            }
            Collections.sort(permissions);
            for (String permission : permissions) {
                boolean granted = false;
                try {
                    granted = packageManager.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED;
                } catch (Throwable ignored) {
                }
                String title = shortName(permission) + " (" + (granted ? "granted" : "revoked") + ")";
                out.add(new Entry(title, permission, permission, protectionBase(packageManager, permission)));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static ArrayList<Entry> emptyAppList() {
        ArrayList<Entry> out = new ArrayList<>();
        out.add(new Entry("Selected App Permissions", "", "", -1));
        return out;
    }

    public static String shortName(String permission) {
        if (TextUtils.isEmpty(permission)) return "";
        int index = permission.lastIndexOf('.');
        return index >= 0 && index + 1 < permission.length() ? permission.substring(index + 1) : permission;
    }

    public static int protectionBase(PackageManager packageManager, String permission) {
        if (TextUtils.isEmpty(permission) || packageManager == null) return -1;
        try {
            PermissionInfo info = packageManager.getPermissionInfo(permission, 0);
            return info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void addCommon(ArrayList<Entry> out, PackageManager packageManager, String name, String permission) {
        out.add(new Entry(name, permission, permission, protectionBase(packageManager, permission)));
    }
}
