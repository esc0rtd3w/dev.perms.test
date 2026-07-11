package dev.perms.test.files;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public final class FilesPackageIconLoader {
    public interface FileReadableChecker {
        boolean canRead(File file);
    }

    private static final int PACKAGE_ICON_CACHE_LIMIT = 96;

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final FileReadableChecker readableChecker;
    private final Runnable adaptersInvalidator;

    private final Map<String, Drawable> packageIconCache = new LinkedHashMap<>();
    private final ArrayList<String> packageIconLoading = new ArrayList<>();
    private final Map<String, Drawable> installedPackageIconCache = new LinkedHashMap<>();
    private final ArrayList<String> installedPackageNamesByLength = new ArrayList<>();
    private volatile boolean installedPackageIconCacheReady = false;

    public FilesPackageIconLoader(Context context,
                                  ExecutorService executor,
                                  Handler mainHandler,
                                  FileReadableChecker readableChecker,
                                  Runnable adaptersInvalidator) {
        this.context = context;
        this.executor = executor;
        this.mainHandler = mainHandler;
        this.readableChecker = readableChecker;
        this.adaptersInvalidator = adaptersInvalidator;
    }

    public Drawable cachedIcon(FileEntry entry) {
        if (entry == null || entry.isDir || !FilesBrowserUtils.isPackageArchive(entry.name)) return null;
        synchronized (packageIconCache) {
            return packageIconCache.get(iconKey(entry));
        }
    }

    public void scheduleLoad(FileEntry entry) {
        if (entry == null || entry.isDir || !FilesBrowserUtils.isPackageArchive(entry.name) || TextUtils.isEmpty(entry.fullPath)) return;
        final String key = iconKey(entry);
        synchronized (packageIconLoading) {
            synchronized (packageIconCache) {
                if (packageIconCache.containsKey(key)) return;
            }
            if (packageIconLoading.contains(key)) return;
            packageIconLoading.add(key);
        }
        executor.execute(() -> {
            Drawable icon = loadPackageArchiveIcon(entry.fullPath);
            mainHandler.post(() -> finishPackageIconLoad(key, icon));
        });
    }

    public void invalidate() {
        synchronized (packageIconCache) {
            packageIconCache.clear();
        }
        synchronized (packageIconLoading) {
            packageIconLoading.clear();
        }
        synchronized (installedPackageIconCache) {
            installedPackageIconCache.clear();
            installedPackageNamesByLength.clear();
            installedPackageIconCacheReady = false;
        }
        mainHandler.post(this::invalidateAdapters);
    }

    private void finishPackageIconLoad(String key, Drawable icon) {
        synchronized (packageIconLoading) {
            packageIconLoading.remove(key);
        }
        if (icon == null) return;
        synchronized (packageIconCache) {
            packageIconCache.put(key, icon);
            while (packageIconCache.size() > PACKAGE_ICON_CACHE_LIMIT) {
                Iterator<String> it = packageIconCache.keySet().iterator();
                if (it.hasNext()) it.next();
                try {
                    it.remove();
                } catch (Throwable ignored) {
                    break;
                }
            }
        }
        invalidateAdapters();
    }

    private void invalidateAdapters() {
        if (adaptersInvalidator == null) return;
        try {
            adaptersInvalidator.run();
        } catch (Throwable ignored) {
        }
    }

    private String iconKey(FileEntry entry) {
        if (entry == null || TextUtils.isEmpty(entry.fullPath)) return "";
        return entry.fullPath + "|" + entry.size + "|" + entry.modifiedEpoch;
    }

    private Drawable loadPackageArchiveIcon(String path) {
        if (TextUtils.isEmpty(path)) return null;

        // Prefer Android's installed-app icon cache when the archive path/name clearly
        // identifies an installed package. This avoids copying archives into app cache.
        Drawable installedIcon = loadInstalledPackageIconForArchivePath(path);
        if (installedIcon != null) return installedIcon;

        // Direct-readable single APKs can still be parsed in place. Split bundles and
        // unreadable paths intentionally fall back to the generic package icon.
        return loadPackageArchiveIconFromReadablePath(path);
    }

    private Drawable loadInstalledPackageIconForArchivePath(String path) {
        if (TextUtils.isEmpty(path)) return null;
        try {
            ensureInstalledPackageIconCache();
            String packageName = resolveInstalledPackageNameForArchivePath(path);
            if (TextUtils.isEmpty(packageName)) return null;
            synchronized (installedPackageIconCache) {
                return installedPackageIconCache.get(packageName);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void ensureInstalledPackageIconCache() {
        if (installedPackageIconCacheReady) return;
        synchronized (installedPackageIconCache) {
            if (installedPackageIconCacheReady) return;
        }

        LinkedHashMap<String, Drawable> icons = new LinkedHashMap<>();
        ArrayList<String> names = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            if (apps != null) {
                for (ApplicationInfo appInfo : apps) {
                    if (appInfo == null || TextUtils.isEmpty(appInfo.packageName)) continue;
                    String packageName = appInfo.packageName.toLowerCase(Locale.ROOT);
                    Drawable icon = null;
                    try {
                        icon = pm.getApplicationIcon(appInfo);
                    } catch (Throwable ignored) {
                    }
                    if (icon == null) continue;
                    icons.put(packageName, icon);
                    names.add(packageName);
                }
            }
        } catch (Throwable ignored) {
        }
        Collections.sort(names, (a, b) -> Integer.compare(b.length(), a.length()));

        synchronized (installedPackageIconCache) {
            installedPackageIconCache.clear();
            installedPackageIconCache.putAll(icons);
            installedPackageNamesByLength.clear();
            installedPackageNamesByLength.addAll(names);
            installedPackageIconCacheReady = true;
        }
    }

    private String resolveInstalledPackageNameForArchivePath(String path) {
        if (TextUtils.isEmpty(path)) return null;
        String lowerPath = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String baseName = lowerPath;
        int slash = baseName.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < baseName.length()) baseName = baseName.substring(slash + 1);
        baseName = FilesBrowserUtils.stripPackageArchiveExtension(baseName);

        synchronized (installedPackageIconCache) {
            for (String packageName : installedPackageNamesByLength) {
                if (FilesBrowserUtils.archivePathMatchesInstalledPackage(lowerPath, baseName, packageName)) {
                    return packageName;
                }
            }
        }
        return null;
    }

    private Drawable loadPackageArchiveIconFromReadablePath(String path) {
        if (TextUtils.isEmpty(path)) return null;
        try {
            String lower = path.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".apk")) return null;
            File apkForIcon = new File(path);
            if (readableChecker == null || !readableChecker.canRead(apkForIcon)) return null;
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkForIcon.getAbsolutePath(), PackageManager.GET_META_DATA);
            if (packageInfo == null || packageInfo.applicationInfo == null) return null;
            ApplicationInfo appInfo = packageInfo.applicationInfo;
            appInfo.sourceDir = apkForIcon.getAbsolutePath();
            appInfo.publicSourceDir = apkForIcon.getAbsolutePath();
            return appInfo.loadIcon(pm);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
