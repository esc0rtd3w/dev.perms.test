package dev.perms.test.tools.intentreceiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small shared store for live Intent Receiver capture events. */
public final class IntentReceiverEventStore {
    private static final String PREFS = "perms_test";
    private static final String KEY_EVENT_LOG = "intent_receiver_event_log";
    private static final String KEY_SAVE_EVENTS_TO_FILE = "intent_receiver_save_events_to_file";
    private static final int MAX_LOG_CHARS = 24000;
    private static final String EVENT_DIR = "/storage/emulated/0/dev.perms.test/intent_receiver";
    private static final String EVENT_DIR_FALLBACK = "/sdcard/dev.perms.test/intent_receiver";
    private static final String EVENT_FILE = "intent_receiver_events.txt";

    private IntentReceiverEventStore() {
    }

    public static boolean isSaveToFile(Context context) {
        return prefs(context).getBoolean(KEY_SAVE_EVENTS_TO_FILE, false);
    }

    public static void setSaveToFile(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SAVE_EVENTS_TO_FILE, enabled).apply();
    }

    public static String read(Context context) {
        return prefs(context).getString(KEY_EVENT_LOG, "");
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_EVENT_LOG).apply();
    }

    public static void record(Context context, String formattedIntent) {
        if (context == null) return;
        String body = formattedIntent == null ? "" : formattedIntent.trim();
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String entry = "[" + time + "] Intent captured\n" + body + "\n\n";

        SharedPreferences sp = prefs(context);
        String oldLog = sp.getString(KEY_EVENT_LOG, "");
        String next = entry + (oldLog == null ? "" : oldLog);
        if (next.length() > MAX_LOG_CHARS) next = next.substring(0, MAX_LOG_CHARS);
        sp.edit().putString(KEY_EVENT_LOG, next).apply();

        if (sp.getBoolean(KEY_SAVE_EVENTS_TO_FILE, false)) {
            appendToFile(entry);
        }
    }

    public static String filePath() {
        return EVENT_DIR + "/" + EVENT_FILE;
    }

    private static void appendToFile(String entry) {
        if (TextUtils.isEmpty(entry)) return;
        if (!tryAppend(new File(EVENT_DIR, EVENT_FILE), entry)) {
            tryAppend(new File(EVENT_DIR_FALLBACK, EVENT_FILE), entry);
        }
    }

    private static boolean tryAppend(File file, String entry) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(entry.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
