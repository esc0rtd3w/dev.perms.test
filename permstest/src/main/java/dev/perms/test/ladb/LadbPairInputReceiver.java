package dev.perms.test.ladb;

import dev.perms.test.ExecMode;
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
 * Receives RemoteInput from the LADB "Pair Helper" notification and runs:
 *   1) adb pair <port> <code>
 *   2) adb connect 127.0.0.1:<port>
 *
 * This keeps all work inside the receiver (no MainActivity dependency) and avoids any
 * Java language level changes (pure Java 8 syntax).
 */
public final class LadbPairInputReceiver extends BroadcastReceiver {

    public static final String KEY_LADB_PAIR_INPUT = "ladb_pair_input";

    // Notification channel + ids for result feedback
    private static final String NOTIF_CHANNEL_LADB = "ladb_pair";
    private static final int NOTIF_ID_LADB_RESULT = 10022;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // Do work off the main thread
        new Thread(new Runnable() {
            @Override public void run() {
                handle(context, intent);
            }
        }, "LadbPairInputReceiver").start();
    }

    private static void handle(Context context, Intent intent) {
        try {
            if (context == null || intent == null) return;

            CharSequence input = null;
            try {
                Bundle results = RemoteInput.getResultsFromIntent(intent);
                if (results != null) input = results.getCharSequence(KEY_LADB_PAIR_INPUT);
            } catch (Throwable ignored) {}

            final String raw = input == null ? "" : input.toString().trim();
            if (raw.length() == 0) {
                postResult(context, "No input received.", false);
                try { postHelper(context); } catch (Throwable ignored) {}
                return;
            }

            // Parse input more defensively.
// Users typically enter: "<pairPort> <pairCode>" (example: 37123 123456)
// Some OEM keyboards/notifications may include punctuation/newlines; we just extract number groups.
java.util.ArrayList<String> nums = new java.util.ArrayList<String>();
try {
    java.util.regex.Matcher mm = java.util.regex.Pattern.compile("(\\d+)").matcher(raw);
    while (mm.find()) nums.add(mm.group(1));
} catch (Throwable ignored) {}

if (nums.size() < 2) {
    postResult(context, "Format: <pairPort> <pairCode> (example: 37123 123456). Optional: add <connectPort> at the end.", false);
    // Re-show the helper so it's available for the next attempt.
    try { postHelper(context); } catch (Throwable ignored) {}
    return;
}

int pairPort;
String pairCode;

// Heuristic: if user pastes "<pairCode> <pairPort>" (some UIs show code first), swap when obvious.
long n0 = -1L, n1 = -1L;
try { n0 = Long.parseLong(nums.get(0)); } catch (Throwable ignored) {}
try { n1 = Long.parseLong(nums.get(1)); } catch (Throwable ignored) {}
boolean swap = (n0 > 65535L && n1 > 0L && n1 <= 65535L);

if (swap) {
    pairPort = (int) n1;
    pairCode = String.valueOf(n0);
} else {
    try {
        pairPort = Integer.parseInt(nums.get(0));
    } catch (Throwable t) {
        postResult(context, "Invalid port: " + nums.get(0), false);
        try { postHelper(context); } catch (Throwable ignored) {}
        return;
    }
    pairCode = nums.get(1).trim();
}
if (pairCode.length() == 0) {
    postResult(context, "Invalid code.", false);
    try { postHelper(context); } catch (Throwable ignored) {}
    return;
}

// Optional connect port (often different from pairing port)
int connectPort = 0;
if (nums.size() >= 3) {
    try { connectPort = Integer.parseInt(nums.get(2)); } catch (Throwable ignored) { connectPort = 0; }
}
            if (pairCode.length() == 0) {
                postResult(context, "Invalid code.", false);
                return;
            }

            int savedConnectPort = 0;
            try {
                SharedPreferences sp = context.getSharedPreferences("perms_test", Context.MODE_PRIVATE);
                savedConnectPort = sp.getInt(ExecMode.PREF_KEY_LADB_CONNECT_PORT, 0);
                if (connectPort <= 0) connectPort = savedConnectPort;
                SharedPreferences.Editor ed = sp.edit()
                        .putInt(ExecMode.PREF_KEY_LADB_PAIR_PORT, pairPort)
                        .putString(ExecMode.PREF_KEY_LADB_PAIR_CODE, pairCode)
                        ;
                if (connectPort > 0) {
                    ed.putInt(ExecMode.PREF_KEY_LADB_CONNECT_PORT, connectPort);
                }
                ed.apply();
            } catch (Throwable ignored) {}

            if (connectPort <= 0) {
                // If the user hasn't entered a connect port in the main UI yet, tell them what to do.
                postResult(context, "Missing connect port. Enter it on the main LADB UI, or use: <pairPort> <pairCode> <connectPort>", false);
                return;
            }


            LadbClient client = new LadbClient(context.getApplicationContext());

            // Use the same host/signature as MainActivity ("127.0.0.1", port, code)
            LadbClient.CmdResult r1 = client.pair(LadbClient.DEFAULT_HOST, pairPort, pairCode);
            if (!looksOk(r1)) {
                postResult(context, "Pair failed: " + summarize(r1), false);
                return;
            }

            LadbClient.CmdResult r2 = client.connect(LadbClient.DEFAULT_HOST, connectPort);
            if (!looksOk(r2)) {
                postResult(context, "Connect failed: " + summarize(r2), false);
                return;
            }

            postResult(context, "Paired + connected (127.0.0.1:" + connectPort + ")", true);

        } catch (Throwable t) {
            postResult(context, "Error: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()), false);
        }
    }

        private static boolean looksOk(LadbClient.CmdResult r) {
        if (r == null) return false;
        if (r.exitCode != 0) return false;
        try {
            String s = (r.stdout + "\n" + r.stderr).toLowerCase();
            // Some adb builds return exit=0 even when printing an error.
            if (s.contains("failed") || s.contains("error") || s.contains("bad port") || s.contains("invalid")) return false;
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

    
    private static void postHelper(Context context) {
        try {
            ensureChannel(context);



// RemoteInput action -> this receiver.
            Intent i = new Intent(context, LadbPairInputReceiver.class);
            i.setPackage(context.getPackageName());

            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                flags |= android.app.PendingIntent.FLAG_MUTABLE;
            } else if (android.os.Build.VERSION.SDK_INT >= 23) {
                flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
            }

            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                    context, 1001, i, flags
            );

            RemoteInput ri = new RemoteInput.Builder(KEY_LADB_PAIR_INPUT)
                    .setLabel("pairPort pairCode (optional connectPort)")
                    .build();

            NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "Submit",
                    pi
            ).addRemoteInput(ri).build();

            NotificationCompat.Builder nb = new NotificationCompat.Builder(context, NOTIF_CHANNEL_LADB)
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setContentTitle("LADB Pair Helper")
                    .setContentText("PermsTest Pairing Code")
                                        .setOngoing(true)
                    .setAutoCancel(false)
                    .addAction(action)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManagerCompat.from(context).notify(10021, nb.build());
        } catch (Throwable ignored) {}
    }

private static void postResult(Context context, String msg, boolean ok) {
        try {
            ensureChannel(context);
            NotificationCompat.Builder nb = new NotificationCompat.Builder(context, NOTIF_CHANNEL_LADB)
                    .setSmallIcon(ok ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
                    .setContentTitle("LADB Pair Helper")
                    .setContentText("PermsTest Pairing Code")
                                        .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManagerCompat.from(context).notify(NOTIF_ID_LADB_RESULT, nb.build());
        } catch (Throwable ignored) {}
    }

    private static void ensureChannel(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = nm.getNotificationChannel(NOTIF_CHANNEL_LADB);
            if (ch != null) return;
            ch = new NotificationChannel(NOTIF_CHANNEL_LADB, "LADB Pair Helper", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Pair/connect helper for wireless debugging");
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {}
    }
}
