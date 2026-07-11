package dev.perms.test.settings.backup;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.settings.SettingsPreferenceDefaults;
import dev.perms.test.settings.SettingsPreferenceKeys;

/** Handles Settings-tab backup/restore without adding backup file I/O to MainActivity. */
public final class SettingsBackupController {
    private static final String BACKUP_DIR = "dev.perms.test/backups";
    private static final String ENTRY_SETTINGS = "settings.json";
    private static final String ENTRY_FILES_PREFIX = "files/";
    private static final String ENTRY_NO_BACKUP_PREFIX = "no_backup/";
    private static final String EXT = ".ptbackup.zip";
    private static final int BUFFER_SIZE = 32 * 1024;

    private SettingsBackupController() {
    }

    public interface Listener {
        void onSettingsImported();
    }

    public static void bind(Context context, ActivityMainBinding binding, SharedPreferences prefs, Listener listener) {
        if (context == null || binding == null || binding.tabSettings == null || prefs == null) return;
        Activity activity = context instanceof Activity ? (Activity) context : null;
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (binding.tabSettings.btnExportSettings != null) {
            binding.tabSettings.btnExportSettings.setOnClickListener(v -> {
                boolean includePrivate = binding.tabSettings.chkFullPrivateBackup != null
                        && binding.tabSettings.chkFullPrivateBackup.isChecked();
                if (includePrivate && activity != null) {
                    new AlertDialog.Builder(activity)
                            .setTitle("Export full/private backup?")
                            .setMessage("This includes app-private files and ADB/LADB/Internal Shizuku pairing data when available. The backup is written to shared storage, so keep it private. AndroidKeyStore-backed data may still require re-pairing after reinstall.")
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton("Export", (dialog, which) -> exportAsync(context, binding, prefs, includePrivate, mainHandler))
                            .show();
                } else {
                    exportAsync(context, binding, prefs, false, mainHandler);
                }
            });
        }

        if (binding.tabSettings.btnImportSettings != null) {
            binding.tabSettings.btnImportSettings.setOnClickListener(v -> showImportDialog(context, binding, prefs, listener, mainHandler));
        }
    }

    private static void exportAsync(Context context, ActivityMainBinding binding, SharedPreferences prefs,
                                    boolean includePrivate, Handler mainHandler) {
        setStatus(binding, "Exporting settings...");
        new Thread(() -> {
            try {
                File dir = backupDir();
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create " + dir.getAbsolutePath());
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
                File out = new File(dir, includePrivate
                        ? "permstest-full-private-" + stamp + EXT
                        : "permstest-settings-" + stamp + EXT);
                writeBackup(context, prefs, out, includePrivate);
                mainHandler.post(() -> {
                    setStatus(binding, "Exported: " + out.getAbsolutePath());
                    toast(context, "Settings exported");
                });
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    setStatus(binding, "Export failed: " + safeMessage(t));
                    toast(context, "Export failed: " + safeMessage(t));
                });
            }
        }, "PermsTestSettingsExport").start();
    }

    private static void showImportDialog(Context context, ActivityMainBinding binding, SharedPreferences prefs,
                                         Listener listener, Handler mainHandler) {
        Activity activity = context instanceof Activity ? (Activity) context : null;
        File[] files = listBackups();
        if (files == null || files.length == 0) {
            setStatus(binding, "No backups found in " + backupDir().getAbsolutePath());
            toast(context, "No settings backups found");
            return;
        }
        if (activity == null) {
            importAsync(context, binding, prefs, files[0], false, listener, mainHandler);
            return;
        }

        String[] labels = new String[files.length];
        for (int i = 0; i < files.length; i++) labels[i] = files[i].getName();
        new AlertDialog.Builder(activity)
                .setTitle("Import Settings")
                .setItems(labels, (dialog, which) -> {
                    boolean includePrivate = binding.tabSettings.chkFullPrivateBackup != null
                            && binding.tabSettings.chkFullPrivateBackup.isChecked();
                    File selected = files[Math.max(0, Math.min(which, files.length - 1))];
                    if (includePrivate) {
                        new AlertDialog.Builder(activity)
                                .setTitle("Import full/private data?")
                                .setMessage("This may restore app-private files and ADB/LADB/Internal Shizuku pairing data from the selected backup. Continue only if you trust this backup.")
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton("Import", (confirm, ignored) ->
                                        importAsync(context, binding, prefs, selected, true, listener, mainHandler))
                                .show();
                    } else {
                        importAsync(context, binding, prefs, selected, false, listener, mainHandler);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void importAsync(Context context, ActivityMainBinding binding, SharedPreferences prefs, File file,
                                    boolean includePrivate, Listener listener, Handler mainHandler) {
        setStatus(binding, "Importing settings...");
        new Thread(() -> {
            try {
                ImportResult result = readBackup(context, prefs, file, includePrivate);
                SettingsPreferenceDefaults.ensure(context, SettingsPreferenceKeys.PREFS);
                mainHandler.post(() -> {
                    if (listener != null) listener.onSettingsImported();
                    String suffix = result.privateDataSkipped ? " Private data skipped." : "";
                    setStatus(binding, "Imported: " + file.getName() + "." + suffix);
                    toast(context, "Settings imported" + (result.privateDataSkipped ? " (private data skipped)" : ""));
                });
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    setStatus(binding, "Import failed: " + safeMessage(t));
                    toast(context, "Import failed: " + safeMessage(t));
                });
            }
        }, "PermsTestSettingsImport").start();
    }

    private static void writeBackup(Context context, SharedPreferences prefs, File out, boolean includePrivate) throws Exception {
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)));
        try {
            JSONObject root = new JSONObject();
            root.put("schema", 1);
            root.put("app", "dev.perms.test");
            root.put("created_ms", System.currentTimeMillis());
            root.put("full_private", includePrivate);
            root.put("preferences", encodePreferences(prefs, includePrivate));
            writeZipText(zos, ENTRY_SETTINGS, root.toString(2));

            if (includePrivate && context != null) {
                addDirectoryToZip(zos, context.getFilesDir(), ENTRY_FILES_PREFIX);
                try { addDirectoryToZip(zos, context.getNoBackupFilesDir(), ENTRY_NO_BACKUP_PREFIX); } catch (Throwable ignored) {}
            }
        } finally {
            try { zos.close(); } catch (Throwable ignored) {}
        }
    }

    private static ImportResult readBackup(Context context, SharedPreferences prefs, File file, boolean includePrivate) throws Exception {
        JSONObject settings = null;
        boolean hasPrivateEntries = false;
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)));
        try {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (ENTRY_SETTINGS.equals(name)) {
                    settings = new JSONObject(readZipText(zis));
                } else if (name != null && (name.startsWith(ENTRY_FILES_PREFIX) || name.startsWith(ENTRY_NO_BACKUP_PREFIX))) {
                    hasPrivateEntries = true;
                    if (includePrivate && context != null) restorePrivateEntry(context, zis, name);
                }
            }
        } finally {
            try { zis.close(); } catch (Throwable ignored) {}
        }

        if (settings == null) throw new IllegalStateException("Missing settings.json");
        boolean backupPrivate = settings.optBoolean("full_private", hasPrivateEntries);
        JSONObject allPrefs = settings.optJSONObject("preferences");
        JSONObject mainPrefs = allPrefs == null ? null : allPrefs.optJSONObject(SettingsPreferenceKeys.PREFS);
        if (mainPrefs == null) throw new IllegalStateException("Missing PermsTest preferences");
        importPreferences(prefs, mainPrefs, includePrivate);
        return new ImportResult((backupPrivate || hasPrivateEntries) && !includePrivate);
    }

    private static JSONObject encodePreferences(SharedPreferences prefs, boolean includePrivate) throws Exception {
        JSONObject prefsRoot = new JSONObject();
        JSONObject values = new JSONObject();
        Map<String, ?> all = prefs.getAll();
        if (all != null) {
            TreeSet<String> keys = new TreeSet<>(all.keySet());
            for (String key : keys) {
                if (!includePrivate && isPrivatePreferenceKey(key)) continue;
                Object value = all.get(key);
                JSONObject item = encodePreferenceValue(value);
                if (item != null) values.put(key, item);
            }
        }
        prefsRoot.put(SettingsPreferenceKeys.PREFS, values);
        return prefsRoot;
    }

    private static JSONObject encodePreferenceValue(Object value) throws Exception {
        if (value == null) return null;
        JSONObject item = new JSONObject();
        if (value instanceof Boolean) {
            item.put("type", "boolean");
            item.put("value", ((Boolean) value).booleanValue());
        } else if (value instanceof Integer) {
            item.put("type", "int");
            item.put("value", ((Integer) value).intValue());
        } else if (value instanceof Long) {
            item.put("type", "long");
            item.put("value", ((Long) value).longValue());
        } else if (value instanceof Float) {
            item.put("type", "float");
            item.put("value", ((Float) value).doubleValue());
        } else if (value instanceof Set) {
            item.put("type", "string_set");
            JSONArray array = new JSONArray();
            ArrayList<String> sorted = new ArrayList<>();
            for (Object obj : (Set<?>) value) if (obj != null) sorted.add(String.valueOf(obj));
            Collections.sort(sorted);
            for (String s : sorted) array.put(s);
            item.put("value", array);
        } else {
            item.put("type", "string");
            item.put("value", String.valueOf(value));
        }
        return item;
    }

    private static void importPreferences(SharedPreferences prefs, JSONObject values, boolean includePrivate) throws Exception {
        if (prefs == null || values == null) return;
        SharedPreferences.Editor ed = prefs.edit();
        JSONArray names = values.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i, null);
                if (key == null) continue;
                if (!includePrivate && isPrivatePreferenceKey(key)) continue;
                JSONObject item = values.optJSONObject(key);
                if (item == null) continue;
                String type = item.optString("type", "string");
                if ("boolean".equals(type)) {
                    ed.putBoolean(key, item.optBoolean("value", false));
                } else if ("int".equals(type)) {
                    ed.putInt(key, item.optInt("value", 0));
                } else if ("long".equals(type)) {
                    ed.putLong(key, item.optLong("value", 0L));
                } else if ("float".equals(type)) {
                    ed.putFloat(key, (float) item.optDouble("value", 0.0d));
                } else if ("string_set".equals(type)) {
                    JSONArray array = item.optJSONArray("value");
                    TreeSet<String> set = new TreeSet<>();
                    if (array != null) {
                        for (int j = 0; j < array.length(); j++) set.add(array.optString(j, ""));
                    }
                    ed.putStringSet(key, set);
                } else {
                    ed.putString(key, item.optString("value", ""));
                }
            }
        }
        ed.apply();
    }

    private static boolean isPrivatePreferenceKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.US);
        return k.contains("adbkey")
                || k.contains("pair_code")
                || k.contains("pairing_code")
                || k.equals("ladb_pair_code")
                || k.equals("internal_shizuku_pair_code");
    }

    private static void addDirectoryToZip(ZipOutputStream zos, File root, String prefix) throws Exception {
        if (zos == null || root == null || prefix == null || !root.exists()) return;
        String base = root.getCanonicalPath();
        addDirectoryToZipRecursive(zos, root, base, prefix);
    }

    private static void addDirectoryToZipRecursive(ZipOutputStream zos, File file, String base, String prefix) throws Exception {
        if (file == null || !file.exists()) return;
        String relative = relativePath(base, file);
        if (shouldSkipRelativePath(relative)) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) addDirectoryToZipRecursive(zos, child, base, prefix);
            return;
        }
        if (!file.isFile()) return;
        String entryName = prefix + relative.replace(File.separatorChar, '/');
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(file.lastModified());
        zos.putNextEntry(entry);
        FileInputStream fis = new FileInputStream(file);
        try { copy(fis, zos); } finally { try { fis.close(); } catch (Throwable ignored) {} }
        zos.closeEntry();
    }

    private static void restorePrivateEntry(Context context, ZipInputStream zis, String name) throws Exception {
        File targetRoot;
        String rel;
        if (name.startsWith(ENTRY_FILES_PREFIX)) {
            targetRoot = context.getFilesDir();
            rel = name.substring(ENTRY_FILES_PREFIX.length());
        } else if (name.startsWith(ENTRY_NO_BACKUP_PREFIX)) {
            targetRoot = context.getNoBackupFilesDir();
            rel = name.substring(ENTRY_NO_BACKUP_PREFIX.length());
        } else {
            return;
        }
        if (shouldSkipRelativePath(rel)) return;
        File out = new File(targetRoot, rel);
        String rootPath = targetRoot.getCanonicalPath() + File.separator;
        String outPath = out.getCanonicalPath();
        if (!outPath.startsWith(rootPath)) throw new SecurityException("Unsafe backup path: " + name);
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Could not create " + parent.getAbsolutePath());
        FileOutputStream fos = new FileOutputStream(out);
        try { copy(zis, fos); } finally { try { fos.close(); } catch (Throwable ignored) {} }
    }

    private static boolean shouldSkipRelativePath(String relative) {
        if (relative == null || relative.length() == 0) return false;
        String r = relative.replace('\\', '/').toLowerCase(Locale.US);
        String[] parts = r.split("/");
        for (String part : parts) {
            if (part.length() == 0) continue;
            if (part.equals("cache") || part.equals("temp") || part.equals("tmp")
                    || part.equals("workspace") || part.equals("workspaces")
                    || part.equals("stage") || part.equals("staging")
                    || part.equals("imports") || part.equals("apkpatch")) {
                return true;
            }
        }
        return false;
    }

    private static String relativePath(String base, File file) throws Exception {
        String path = file.getCanonicalPath();
        if (path.equals(base)) return "";
        if (path.startsWith(base + File.separator)) return path.substring(base.length() + 1);
        return file.getName();
    }

    private static void writeZipText(ZipOutputStream zos, String name, String text) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        byte[] data = text == null ? new byte[0] : text.getBytes("UTF-8");
        zos.write(data);
        zos.closeEntry();
    }

    private static String readZipText(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copy(in, bos);
        return bos.toString("UTF-8");
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (n > 0) out.write(buf, 0, n);
        }
    }

    private static File backupDir() {
        return new File(Environment.getExternalStorageDirectory(), BACKUP_DIR);
    }

    private static File[] listBackups() {
        File dir = backupDir();
        File[] files = dir.listFiles((d, name) -> name != null && name.toLowerCase(Locale.US).endsWith(EXT));
        if (files == null) return null;
        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        Collections.sort(list, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                long am = a == null ? 0L : a.lastModified();
                long bm = b == null ? 0L : b.lastModified();
                return Long.compare(bm, am);
            }
        });
        return list.toArray(new File[0]);
    }

    private static void setStatus(ActivityMainBinding binding, String status) {
        try {
            if (binding != null && binding.tabSettings != null && binding.tabSettings.txtSettingsBackupStatus != null) {
                binding.tabSettings.txtSettingsBackupStatus.setText(status == null ? "" : status);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void toast(Context context, String text) {
        try { Toast.makeText(context, text, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
    }

    private static String safeMessage(Throwable t) {
        String msg = t == null ? null : t.getMessage();
        if (msg == null || msg.length() == 0) return t == null ? "unknown error" : t.getClass().getSimpleName();
        return msg;
    }

    private static final class ImportResult {
        final boolean privateDataSkipped;

        ImportResult(boolean privateDataSkipped) {
            this.privateDataSkipped = privateDataSkipped;
        }
    }
}
