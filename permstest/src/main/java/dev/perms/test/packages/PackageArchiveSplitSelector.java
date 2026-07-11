package dev.perms.test.packages;

import dev.perms.test.debug.PackageInstallDebug;

import android.app.Activity;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Selects the base APK and device-matched config splits from staged APK/APKS/APKM/XAPK contents.
 */
public final class PackageArchiveSplitSelector {
    public interface SizeProvider {
        long sizeOf(String path);
    }

    private PackageArchiveSplitSelector() {
    }

    public static String selectBestBaseApk(List<String> apks, SizeProvider sizeProvider) {
        if (apks == null || apks.isEmpty()) return null;
        String bestPath = null;
        int bestScore = Integer.MIN_VALUE;
        long bestSize = -1L;
        for (String p : apks) {
            if (TextUtils.isEmpty(p)) continue;
            String name = new File(p).getName();
            int score = scoreApkEntryNameForInstall(name);
            long size = -1L;
            try { size = sizeProvider == null ? -1L : sizeProvider.sizeOf(p); } catch (Throwable ignored) {}
            if (bestPath == null || score > bestScore || (score == bestScore && size > bestSize)) {
                bestPath = p;
                bestScore = score;
                bestSize = size;
            }
        }
        return bestPath == null ? apks.get(0) : bestPath;
    }

    public static List<String> selectForDevice(Activity activity, List<String> apks, String baseApk, boolean promptUser) {
        try {
            PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_SELECTOR, "selectForDevice start promptUser=" + promptUser
                    + ", baseArg=" + PackageInstallDebug.describePath(baseApk)
                    + ", apkList=" + PackageInstallDebug.describePathList(apks, 30));
            if (apks == null || apks.isEmpty()) return apks;

            String base = baseApk;
            if (TextUtils.isEmpty(base)) base = apks.get(0);

            List<String> always = new ArrayList<>();
            List<SplitCandidate> config = new ArrayList<>();

            List<String> abiPrefs = buildAbiPreferences();
            List<String> localePrefs = buildLocalePreferences(activity);
            Map<String, Integer> densities = buildDensityMap();
            int densityDpi = readDensityDpi(activity);

            for (String p0 : apks) {
                String p = p0 == null ? "" : p0.trim();
                if (TextUtils.isEmpty(p)) continue;
                if (p.equals(base)) continue;
                String fn = new File(p).getName();
                String qual = configQualifierFromApkName(fn);
                if (!TextUtils.isEmpty(qual)) {
                    int cat = classifyConfigQualifier(qual, abiPrefs, densities);
                    config.add(new SplitCandidate(p, fn, qual, cat));
                } else {
                    always.add(p);
                }
            }

            String pickAbi = pickFirstMatchingConfig(config, abiPrefs, SplitCandidate.CATEGORY_ABI);
            String pickLoc = pickFirstMatchingConfig(config, localePrefs, SplitCandidate.CATEGORY_LOCALE);
            String pickDen = pickDensityConfig(config, densities, densityDpi);

            ArrayList<SplitCandidate> uiConfig = new ArrayList<>();
            ArrayList<String> alwaysConfig = new ArrayList<>();
            for (SplitCandidate c : config) {
                if (c.category == SplitCandidate.CATEGORY_OTHER) alwaysConfig.add(c.path);
                else uiConfig.add(c);
            }

            ArrayList<String> defaultConfig = new ArrayList<>(alwaysConfig);
            if (!TextUtils.isEmpty(pickAbi)) defaultConfig.add(pickAbi);
            if (!TextUtils.isEmpty(pickLoc)) defaultConfig.add(pickLoc);
            if (!TextUtils.isEmpty(pickDen)) defaultConfig.add(pickDen);

            PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_SELECTOR, "classified config ui=" + uiConfig.size()
                    + ", always=" + always.size()
                    + ", alwaysConfigOther=" + alwaysConfig.size()
                    + ", densityDpi=" + densityDpi
                    + ", pickAbi=" + PackageInstallDebug.describePath(pickAbi)
                    + ", pickLoc=" + PackageInstallDebug.describePath(pickLoc)
                    + ", pickDen=" + PackageInstallDebug.describePath(pickDen)
                    + ", defaultConfig=" + PackageInstallDebug.describePathList(defaultConfig, 30));

            List<String> chosenConfig = defaultConfig;
            if (promptUser && !uiConfig.isEmpty()) {
                PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_SELECTOR, "show dialog requested uiConfig=" + uiConfig.size());
                chosenConfig = showSplitSelectionDialog(activity, base, always, uiConfig, alwaysConfig, defaultConfig);
                PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_SELECTOR, "dialog result=" + PackageInstallDebug.describePathList(chosenConfig, 30));
                if (chosenConfig == null) return null;
            }

            LinkedHashSet<String> out = new LinkedHashSet<>();
            out.add(base);
            out.addAll(always);
            if (chosenConfig != null) out.addAll(chosenConfig);
            ArrayList<String> result = new ArrayList<>(out);
            PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_SELECTOR, "selectForDevice result=" + PackageInstallDebug.describePathList(result, 30));
            return result;
        } catch (Throwable t) {
            PackageInstallDebug.error(PackageInstallDebug.Area.SPLIT_SELECTOR, "selectForDevice failed; returning original APK list", t);
            return apks;
        }
    }

    private static int scoreApkEntryNameForInstall(String name) {
        if (name == null) return 0;
        String n = name.toLowerCase(Locale.US);
        int score = 0;
        if (n.equals("base.apk") || n.endsWith("/base.apk")) score += 10000;
        if (n.contains("base") || n.contains("master") || n.contains("main")) score += 1000;
        boolean configish = n.startsWith("config.") || n.contains("/config.") || n.contains("split_config")
                || n.contains("dpi") || n.contains("density") || n.contains("lang") || n.contains("locale");
        if (configish) score -= 5000;
        else score += 500;
        score += Math.max(0, 30 - Math.min(30, n.length() / 8));
        return score;
    }

    private static String configQualifierFromApkName(String fileName) {
        if (TextUtils.isEmpty(fileName)) return null;
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.startsWith("split_config.") && lower.endsWith(".apk")) {
            return fileName.substring("split_config.".length(), fileName.length() - ".apk".length());
        }
        if (lower.startsWith("config.") && lower.endsWith(".apk")) {
            return fileName.substring("config.".length(), fileName.length() - ".apk".length());
        }
        return null;
    }

    private static int classifyConfigQualifier(String qualifier, List<String> abiPrefs, Map<String, Integer> densities) {
        if (TextUtils.isEmpty(qualifier)) return SplitCandidate.CATEGORY_OTHER;
        if (abiPrefs != null && abiPrefs.contains(qualifier)) return SplitCandidate.CATEGORY_ABI;
        if (densities != null && densities.containsKey(qualifier)) return SplitCandidate.CATEGORY_DENSITY;
        if (qualifier.matches("^[a-z]{2,3}(_r?[A-Z]{2})?$")) return SplitCandidate.CATEGORY_LOCALE;
        return SplitCandidate.CATEGORY_OTHER;
    }

    private static List<String> buildAbiPreferences() {
        List<String> abiPrefs = new ArrayList<>();
        try {
            for (String abi : android.os.Build.SUPPORTED_ABIS) {
                if (!TextUtils.isEmpty(abi)) abiPrefs.add(abi.replace('-', '_'));
            }
        } catch (Throwable ignored) {}
        return abiPrefs;
    }

    private static List<String> buildLocalePreferences(Activity activity) {
        List<String> localePrefs = new ArrayList<>();
        try {
            android.content.res.Configuration cfg = activity == null ? null : activity.getResources().getConfiguration();
            if (cfg == null) return localePrefs;
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                android.os.LocaleList ll = cfg.getLocales();
                for (int i = 0; i < ll.size(); i++) {
                    addLocalePreference(localePrefs, ll.get(i));
                }
            } else {
                addLocalePreference(localePrefs, cfg.locale);
            }
        } catch (Throwable ignored) {}
        return localePrefs;
    }

    private static void addLocalePreference(List<String> localePrefs, Locale loc) {
        if (localePrefs == null || loc == null) return;
        String lang = loc.getLanguage();
        String country = loc.getCountry();
        if (!TextUtils.isEmpty(lang) && !TextUtils.isEmpty(country)) {
            localePrefs.add(lang + "_r" + country);
            localePrefs.add(lang + "_" + country);
        }
        if (!TextUtils.isEmpty(lang)) localePrefs.add(lang);
    }

    private static Map<String, Integer> buildDensityMap() {
        Map<String, Integer> densities = new HashMap<>();
        densities.put("ldpi", 120);
        densities.put("mdpi", 160);
        densities.put("tvdpi", 213);
        densities.put("hdpi", 240);
        densities.put("xhdpi", 320);
        densities.put("xxhdpi", 480);
        densities.put("xxxhdpi", 640);
        return densities;
    }

    private static int readDensityDpi(Activity activity) {
        try { return activity == null ? 0 : activity.getResources().getDisplayMetrics().densityDpi; } catch (Throwable ignored) {}
        return 0;
    }

    private static String pickFirstMatchingConfig(List<SplitCandidate> config, List<String> prefs, int category) {
        if (config == null || prefs == null) return null;
        for (String pref : prefs) {
            for (SplitCandidate c : config) {
                if (c.category == category && pref.equals(c.qualifier)) return c.path;
            }
        }
        return null;
    }

    private static String pickDensityConfig(List<SplitCandidate> config, Map<String, Integer> densities, int densityDpi) {
        if (config == null || densities == null || densityDpi <= 0) return null;
        int bestDiff = Integer.MAX_VALUE;
        String picked = null;
        for (SplitCandidate c : config) {
            if (c.category != SplitCandidate.CATEGORY_DENSITY) continue;
            Integer dpi = densities.get(c.qualifier);
            if (dpi == null) continue;
            int diff = Math.abs(dpi - densityDpi);
            if (diff < bestDiff) {
                bestDiff = diff;
                picked = c.path;
            }
        }
        return picked;
    }

    @SuppressWarnings("unchecked")
    private static List<String> showSplitSelectionDialog(Activity activity,
                                                         String baseApk,
                                                         List<String> alwaysSplits,
                                                         List<SplitCandidate> uiConfig,
                                                         List<String> alwaysConfig,
                                                         List<String> defaultConfig) {
        if (activity == null) {
            PackageInstallDebug.warn(PackageInstallDebug.Area.SPLIT_DIALOG, "activity is null");
            return null;
        }

        PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "prepare labels uiConfig=" + uiConfig.size()
                + ", alwaysSplits=" + (alwaysSplits == null ? -1 : alwaysSplits.size())
                + ", alwaysConfig=" + (alwaysConfig == null ? -1 : alwaysConfig.size())
                + ", defaultConfig=" + PackageInstallDebug.describePathList(defaultConfig, 30));

        ArrayList<DialogSplitRow> rows = new ArrayList<>();
        if (!TextUtils.isEmpty(baseApk)) {
            rows.add(DialogSplitRow.required(baseApk, "Base: " + new File(baseApk).getName() + " (required)"));
        }
        if (alwaysSplits != null) {
            for (String path : alwaysSplits) {
                if (!TextUtils.isEmpty(path)) {
                    rows.add(DialogSplitRow.required(path, "Feature: " + new File(path).getName() + " (required)"));
                }
            }
        }
        if (alwaysConfig != null) {
            for (String path : alwaysConfig) {
                if (!TextUtils.isEmpty(path)) {
                    rows.add(DialogSplitRow.required(path, "Config: " + new File(path).getName() + " (required)"));
                }
            }
        }
        for (SplitCandidate c : uiConfig) {
            boolean checked = defaultConfig != null && defaultConfig.contains(c.path);
            boolean editable = c.category == SplitCandidate.CATEGORY_LOCALE;
            rows.add(new DialogSplitRow(c.path, buildDialogSplitLabel(c, checked, editable), c.category, checked, editable));
        }

        final String[] labels = new String[rows.size()];
        final boolean[] editableRows = new boolean[rows.size()];
        final boolean[] checkedRows = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            DialogSplitRow row = rows.get(i);
            labels[i] = row.label;
            editableRows[i] = row.editable;
            checkedRows[i] = row.checked;
        }
        PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "display rows=" + rows.size()
                + ", editableLanguageRows=" + countEditableRows(editableRows));

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean completed = new AtomicBoolean(false);
        final boolean[] cancelled = new boolean[]{false};
        final ArrayList<String>[] selectedOut = new ArrayList[]{null};

        PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "posting dialog to UI thread activity=" + activity.getClass().getSimpleName());
        activity.runOnUiThread(() -> {
            try {
                PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "UI runnable entered finishing=" + activity.isFinishing()
                        + ", destroyed=" + (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())
                        + ", hasWindowFocus=" + activity.hasWindowFocus());
                if (activity.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                    cancelled[0] = true;
                    if (completed.compareAndSet(false, true)) latch.countDown();
                    return;
                }

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                        activity.getResources().getDisplayMetrics());
                root.setPadding(pad, pad, pad, pad);

                TextView msg = new TextView(activity);
                msg.setText("Required splits are shown checked and disabled. Language splits can be changed before install.");
                root.addView(msg);

                ListView list = new ListView(activity);
                list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_multiple_choice, labels) {
                    @Override
                    public boolean isEnabled(int position) {
                        return position >= 0 && position < editableRows.length && editableRows[position];
                    }

                    @Override
                    public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
                        android.view.View view = super.getView(position, convertView, parent);
                        boolean editable = position >= 0 && position < editableRows.length && editableRows[position];
                        view.setEnabled(editable);
                        view.setAlpha(editable ? 1.0f : 0.55f);
                        return view;
                    }
                };
                list.setAdapter(adapter);

                for (int i = 0; i < checkedRows.length; i++) {
                    list.setItemChecked(i, checkedRows[i]);
                }

                list.setOnItemClickListener((parent, view, position, id) -> {
                    try {
                        if (position < 0 || position >= editableRows.length || editableRows[position]) return;
                        list.setItemChecked(position, checkedRows[position]);
                    } catch (Throwable ignored) {}
                });

                int maxHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320,
                        activity.getResources().getDisplayMetrics());
                root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight));

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle("Select archive splits");
                builder.setView(root);
                builder.setPositiveButton("Install", (dialog, which) -> {
                    try {
                        ArrayList<String> selected = new ArrayList<>();
                        if (alwaysConfig != null) selected.addAll(alwaysConfig);
                        SparseBooleanArray checked = list.getCheckedItemPositions();
                        for (SplitCandidate c : uiConfig) {
                            if (c.category != SplitCandidate.CATEGORY_LOCALE && defaultConfig != null && defaultConfig.contains(c.path)) {
                                selected.add(c.path);
                            }
                        }
                        for (int i = 0; i < rows.size(); i++) {
                            DialogSplitRow row = rows.get(i);
                            if (!row.editable || row.category != SplitCandidate.CATEGORY_LOCALE) continue;
                            if (checked != null && checked.get(i)) selected.add(row.path);
                        }
                        selectedOut[0] = selected;
                        PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "positive Install selected=" + PackageInstallDebug.describePathList(selected, 30));
                    } catch (Throwable t) {
                        PackageInstallDebug.error(PackageInstallDebug.Area.SPLIT_DIALOG, "positive Install failed while collecting checked splits", t);
                    }
                    if (completed.compareAndSet(false, true)) latch.countDown();
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> {
                    PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "negative Cancel clicked");
                    cancelled[0] = true;
                    if (completed.compareAndSet(false, true)) latch.countDown();
                });
                builder.setOnCancelListener(dialog -> {
                    PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "dialog cancel event");
                    cancelled[0] = true;
                    if (completed.compareAndSet(false, true)) latch.countDown();
                });

                AlertDialog dialog = builder.create();
                dialog.setOnDismissListener(d -> {
                    PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "dialog dismiss event completed=" + completed.get());
                    if (completed.compareAndSet(false, true)) {
                        cancelled[0] = true;
                        latch.countDown();
                    }
                });
                dialog.show();
                PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "dialog.show returned isShowing=" + dialog.isShowing());
            } catch (Throwable t) {
                PackageInstallDebug.error(PackageInstallDebug.Area.SPLIT_DIALOG, "UI runnable failed before/while showing dialog", t);
                cancelled[0] = true;
                if (completed.compareAndSet(false, true)) latch.countDown();
            }
        });

        try {
            PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "await begin");
            if (!latch.await(5, TimeUnit.MINUTES)) {
                PackageInstallDebug.warn(PackageInstallDebug.Area.SPLIT_DIALOG, "await timed out");
                return null;
            }
            PackageInstallDebug.log(PackageInstallDebug.Area.SPLIT_DIALOG, "await complete cancelled=" + cancelled[0]
                    + ", selected=" + PackageInstallDebug.describePathList(selectedOut[0], 30));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            PackageInstallDebug.warn(PackageInstallDebug.Area.SPLIT_DIALOG, "await interrupted");
            return null;
        }
        if (cancelled[0]) return null;
        return selectedOut[0] != null ? selectedOut[0] : defaultConfig;
    }

    private static String buildDialogSplitLabel(SplitCandidate c, boolean checked, boolean editable) {
        if (c == null) return "Config";
        String prefix = c.category == SplitCandidate.CATEGORY_ABI ? "ABI: "
                : c.category == SplitCandidate.CATEGORY_LOCALE ? "Lang: "
                : c.category == SplitCandidate.CATEGORY_DENSITY ? "DPI: "
                : "Config: ";
        String suffix;
        if (editable) suffix = checked ? " (selected)" : " (optional)";
        else suffix = checked ? " (required)" : " (not used)";
        return prefix + c.qualifier + " (" + c.fileName + ")" + suffix;
    }

    private static int countEditableRows(boolean[] editableRows) {
        if (editableRows == null) return 0;
        int count = 0;
        for (boolean editable : editableRows) {
            if (editable) count++;
        }
        return count;
    }

    private static final class DialogSplitRow {
        final String path;
        final String label;
        final int category;
        final boolean checked;
        final boolean editable;

        DialogSplitRow(String path, String label, int category, boolean checked, boolean editable) {
            this.path = path;
            this.label = label;
            this.category = category;
            this.checked = checked;
            this.editable = editable;
        }

        static DialogSplitRow required(String path, String label) {
            return new DialogSplitRow(path, label, SplitCandidate.CATEGORY_OTHER, true, false);
        }
    }

    private static final class SplitCandidate {
        static final int CATEGORY_ABI = 1;
        static final int CATEGORY_LOCALE = 2;
        static final int CATEGORY_DENSITY = 3;
        static final int CATEGORY_OTHER = 4;

        final String path;
        final String fileName;
        final String qualifier;
        final int category;

        SplitCandidate(String path, String fileName, String qualifier, int category) {
            this.path = path;
            this.fileName = fileName;
            this.qualifier = qualifier;
            this.category = category;
        }
    }
}
