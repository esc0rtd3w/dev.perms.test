package dev.perms.test.home;

import android.graphics.drawable.Drawable;

/** Entry for the Home tab App Tray. */
public final class HomeAppTrayEntry {
    public final String packageName;
    public final CharSequence label;
    public final Drawable icon;
    public final boolean isSystemApp;

    public HomeAppTrayEntry(String packageName, CharSequence label, Drawable icon, boolean isSystemApp) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
        this.isSystemApp = isSystemApp;
    }
}
