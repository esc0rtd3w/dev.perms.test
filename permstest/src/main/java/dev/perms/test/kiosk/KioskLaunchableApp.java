package dev.perms.test.kiosk;

import android.graphics.drawable.Drawable;

/** Launchable package entry shown in Kiosk Settings. */
public final class KioskLaunchableApp {
    public final String packageName;
    public final String label;
    public final Drawable icon;

    public KioskLaunchableApp(String packageName, String label, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
    }

    @Override
    public String toString() {
        return label + " (" + packageName + ")";
    }
}
