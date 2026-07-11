package dev.perms.test.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import dev.perms.test.R;

/**
 * Shared foreground-service helpers for long operations that must survive tab/activity navigation.
 *
 * Dedicated feature services still own their actual work and arguments; this helper keeps the
 * Android notification/channel/startForeground plumbing consistent without forcing FTP, HTTP,
 * overlays, or other established services through a new runtime path.
 */
public final class LongOperationForegroundHelper {
    private LongOperationForegroundHelper() {
    }

    public static void ensureChannel(Context context, String channelId, String channelName, String description) {
        if (context == null || Build.VERSION.SDK_INT < 26 || TextUtils.isEmpty(channelId)) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(channelId,
                TextUtils.isEmpty(channelName) ? "PermsTest jobs" : channelName,
                NotificationManager.IMPORTANCE_LOW);
        if (!TextUtils.isEmpty(description)) channel.setDescription(description);
        nm.createNotificationChannel(channel);
    }

    public static Notification buildNotification(Context context,
                                                 String channelId,
                                                 String title,
                                                 String status,
                                                 Intent openIntent,
                                                 Intent stopIntent,
                                                 int stopRequestCode) {
        PendingIntent openPending = openIntent == null ? null : PendingIntent.getActivity(context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag());
        PendingIntent stopPending = stopIntent == null ? null : PendingIntent.getService(context, stopRequestCode, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag());
        String safeTitle = TextUtils.isEmpty(title) ? "PermsTest" : title;
        String safeStatus = TextUtils.isEmpty(status) ? "Long operation running" : status;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(safeTitle)
                .setContentText(safeStatus)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeStatus))
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (openPending != null) builder.setContentIntent(openPending);
        if (stopPending != null) builder.addAction(R.mipmap.ic_launcher, "Stop", stopPending);
        return builder.build();
    }

    public static void startDataSyncForeground(Service service, int notificationId, Notification notification) {
        if (service == null || notification == null) return;
        if (Build.VERSION.SDK_INT >= 29) {
            service.startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            service.startForeground(notificationId, notification);
        }
    }

    public static int pendingIntentImmutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }
}
