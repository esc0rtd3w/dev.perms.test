package dev.perms.test.home;

import android.graphics.drawable.Drawable;

final class AppEntry {
    final String packageName;
    final CharSequence label;
    final Drawable icon;
    final boolean isSystemApp;

    AppEntry(String packageName, CharSequence label, Drawable icon, boolean isSystemApp) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
        this.isSystemApp = isSystemApp;
    }
}
