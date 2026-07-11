package dev.perms.test.shizuku.internal;

import dev.perms.test.ladb.LadbClient;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;

/**
 * Receives RemoteInput from the Internal Shizuku "Pair Helper" notification.
 *
 * Goal: Let the user stay on the Wireless debugging pairing screen (no activity switch),
 * pull down notifications, enter the 6-digit code, and start internal Shizuku.
 *
 * This does NOT modify any existing LADB code; it only reuses the existing LadbClient binary
 * (libadb.so) to run adb pair/connect + adb shell (same as upstream Shizuku's adb starter path).
 */
public final class InternalShizukuPairInputReceiver extends BroadcastReceiver {

    public static final String KEY_INTERNAL_SHIZUKU_CODE = "internal_shizuku_pair_code";

    // Must match MainActivity's prefs file name to read the same toggles/ports.
    private static final String PREFS = "perms_test";
    private static final String PREF_KEY_INTERNAL_MDNS_CONNECT_PORT = "internal_shizuku_mdns_connect_port";

    private static final String NOTIF_CHANNEL = "internal_shizuku_pair";
    private static final int NOTIF_ID_HELPER = 11001;
    private static final int NOTIF_ID_RESULT = 11002;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        new Thread(new Runnable() {
            @Override public void run() {
                handle(context, intent);
            }
        }, "InternalShizukuPairInputReceiver").start();
    }

    private static void handle(Context context, Intent intent) {
        try {
            if (context == null || intent == null) return;

            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!InternalShizukuController.isSelected(sp)) {
                postResult(context, "Internal Shizuku exec mode is not selected.", false);
                return;
            }

            CharSequence input = null;
            try {
                Bundle results = RemoteInput.getResultsFromIntent(intent);
                if (results != null) input = results.getCharSequence(KEY_INTERNAL_SHIZUKU_CODE);
            } catch (Throwable ignored) {}

            final String raw = input == null ? "" : input.toString().trim();
            String code = raw.replaceAll("[^0-9]", "");
            if (code.length() == 0) {
                postResult(context, "No pairing code entered.", false);
                postHelper(context, 0);
                return;
            }
            if (code.length() != 6) {
                // Some devices use 6 digits; we still allow other lengths but warn.
                // Keep it permissive.
            }

                        // Hide the helper notification once a code has been submitted.
            dismissHelper(context);

int pairPort = 0;
            int connectPort = 0;
            try { pairPort = sp.getInt(InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_PAIR_PORT, 0); } catch (Throwable ignored) {}
            try { connectPort = sp.getInt(InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_CONNECT_PORT, 0); } catch (Throwable ignored) {}

            
            int mdnsConnectPort = 0;
            try { mdnsConnectPort = sp.getInt(PREF_KEY_INTERNAL_MDNS_CONNECT_PORT, 0); } catch (Throwable ignored) {}
            if (connectPort <= 0 && mdnsConnectPort > 0) connectPort = mdnsConnectPort;
String host = "127.0.0.1";
if (pairPort <= 0) {
                postResult(context, "Pairing port not detected yet. Open Wireless debugging → Pair device, then pull down notifications again.", false);
                postHelper(context, 0);
                return;
            }

            // Hand off to a dedicated process to run internal-adb operations.
// This isolates libadb crashes from the main UI process and serializes commands.
postResult(context, "Pairing/connecting…", true);
InternalAdbService.enqueuePairConnectStart(context.getApplicationContext(), host, pairPort, connectPort, mdnsConnectPort, code);
// Keep helper visible for additional attempts; it will be updated when the port is detected.
postHelper(context, pairPort);
return;

} catch (Throwable t) {
            postResult(context, "Error: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()), false);
        }
    }

    private static boolean looksOk(LadbClient.CmdResult r) {
        if (r == null) return false;
        if (r.exitCode != 0) return false;
        try {
            String s = (r.stdout + "\n" + r.stderr).toLowerCase();
            if (s.contains("failed") || s.contains("error") || s.contains("exception")) return false;
        } catch (Throwable ignored) {}
        return true;
    }

    private static String summarize(LadbClient.CmdResult r) {
        if (r == null) return "unknown";
        String s = (r.stderr == null || r.stderr.trim().isEmpty()) ? r.stdout : r.stderr;
        if (s == null) s = "";
        s = s.trim().replace('\n', ' ');
        if (s.length() > 120) s = s.substring(0, 120) + "...";
        return "exit=" + r.exitCode + " " + s;
    }

    public 
    static void dismissHelper(Context context) {
        try {
            if (context == null) return;
            NotificationManagerCompat.from(context).cancel(NOTIF_ID_HELPER);
        } catch (Throwable ignored) {
        }
    }

static void postHelper(Context context, int pairPortHint) {
        try {
            if (context == null) return;
            ensureChannel(context);

            Intent i = new Intent(context, InternalShizukuPairInputReceiver.class);
            i.setPackage(context.getPackageName());

            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                flags |= android.app.PendingIntent.FLAG_MUTABLE;
            } else if (android.os.Build.VERSION.SDK_INT >= 23) {
                flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
            }
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(context, 1201, i, flags);

            RemoteInput ri = new RemoteInput.Builder(KEY_INTERNAL_SHIZUKU_CODE)
                    .setLabel("Pairing code (6 digits)")
                    .build();

            NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                    android.R.drawable.ic_input_add,
                    "Enter code",
                    pi
            ).addRemoteInput(ri).build();

            String title = "Internal Shizuku pairing";
            String text = (pairPortHint > 0)
                    ? ("Pairing port detected: " + pairPortHint + ". Enter code here (notification reply).")
                    : ("Open Wireless debugging → Pair device, then pull down and enter the code here.");

            NotificationCompat.Builder b = new NotificationCompat.Builder(context, NOTIF_CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .addAction(action)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManagerCompat.from(context).notify(NOTIF_ID_HELPER, b.build());
        } catch (Throwable ignored) {}
    }

    static void postResult(Context context, String msg, boolean ok) {
        try {
            if (context == null) return;
            ensureChannel(context);

            NotificationCompat.Builder b = new NotificationCompat.Builder(context, NOTIF_CHANNEL)
                    .setSmallIcon(ok ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
                    .setContentTitle(ok ? "Internal Shizuku: Success" : "Internal Shizuku: Failed")
                    .setContentText(msg)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManagerCompat.from(context).notify(NOTIF_ID_RESULT, b.build());
        } catch (Throwable ignored) {}
    }

    private static void ensureChannel(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = nm.getNotificationChannel(NOTIF_CHANNEL);
            if (ch != null) return;
            nm.createNotificationChannel(new NotificationChannel(
                    NOTIF_CHANNEL,
                    "Internal Shizuku Pairing",
                    NotificationManager.IMPORTANCE_HIGH
            ));
        } catch (Throwable ignored) {}
    }
}
