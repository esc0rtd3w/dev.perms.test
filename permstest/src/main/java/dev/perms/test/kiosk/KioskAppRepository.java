package dev.perms.test.kiosk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small launchable-app query helper shared by kiosk settings and launcher surfaces. */
public final class KioskAppRepository {
    private KioskAppRepository() {
    }

    public static List<KioskLaunchableApp> loadLaunchableApps(Context context) {
        ArrayList<KioskLaunchableApp> out = new ArrayList<>();
        if (context == null) return out;
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            if (apps == null) return out;
            for (ApplicationInfo ai : apps) {
                if (ai == null || ai.packageName == null) continue;
                Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
                if (launch == null) continue;
                CharSequence labelCs;
                try {
                    labelCs = pm.getApplicationLabel(ai);
                } catch (Throwable t) {
                    labelCs = ai.packageName;
                }
                Drawable icon = null;
                try {
                    icon = pm.getApplicationIcon(ai);
                } catch (Throwable ignored) {
                }
                out.add(new KioskLaunchableApp(ai.packageName, String.valueOf(labelCs), icon));
            }
            Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static KioskLaunchableApp findByPackage(List<KioskLaunchableApp> apps, String packageName) {
        if (apps == null || packageName == null) return null;
        for (KioskLaunchableApp app : apps) {
            if (app != null && packageName.equals(app.packageName)) return app;
        }
        return null;
    }
}
