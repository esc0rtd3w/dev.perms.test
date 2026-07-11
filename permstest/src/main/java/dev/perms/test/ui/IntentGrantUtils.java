package dev.perms.test.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;

import java.util.List;

public final class IntentGrantUtils {
    private IntentGrantUtils() {}

    public static void grantReadForIntent(Activity activity, Intent intent, Uri uri) {
        if (activity == null || intent == null || uri == null) return;
        try {
            activity.grantUriPermission(activity.getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {}
        try {
            List<ResolveInfo> handlers = activity.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (handlers == null) return;
            for (ResolveInfo info : handlers) {
                if (info == null || info.activityInfo == null || TextUtils.isEmpty(info.activityInfo.packageName)) continue;
                try {
                    activity.grantUriPermission(info.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
