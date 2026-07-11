package dev.perms.test.ui.panel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import dev.perms.test.vr.PermsTestVrOverlayCompat;

/** Starts generic PermsTest panel activities through a small, controlled host API. */
public final class GenericPanelLauncher {
    private static final Set<String> ACTIVE_PANEL_KEYS = Collections.synchronizedSet(new HashSet<>());
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SUBTITLE = "subtitle";
    public static final String EXTRA_CONTENT_FILE = "contentFile";
    public static final String EXTRA_SYNTAX = "syntax";
    public static final String EXTRA_PANEL_KEY = "panelKey";

    private GenericPanelLauncher() {
    }

    public static boolean showTextPanel(Activity activity,
                                        String panelKey,
                                        String title,
                                        String subtitle,
                                        String text,
                                        String syntax) {
        if (activity == null) return false;
        if (!PermsTestPanelSettings.isPanelHostEnabled(activity)) {
            Toast.makeText(activity, "Enable Popout Panels in Settings first", Toast.LENGTH_SHORT).show();
            return false;
        }
        String activeKey = clean(panelKey, "generic");
        if (!reservePanelKey(activeKey)) {
            Toast.makeText(activity, "That panel is already open", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            File file = writePanelText(activity, activeKey, text);
            Intent intent = new Intent(activity, GenericPanelActivity.class);
            intent.putExtra(EXTRA_TITLE, clean(title, "PermsTest Panel"));
            intent.putExtra(EXTRA_SUBTITLE, clean(subtitle, ""));
            intent.putExtra(EXTRA_CONTENT_FILE, file.getAbsolutePath());
            intent.putExtra(EXTRA_SYNTAX, clean(syntax, ""));
            intent.putExtra(EXTRA_PANEL_KEY, activeKey);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            activity.startActivity(intent);
            PermsTestVrOverlayCompat.moveHostTaskBehindMemoryToolIfNeeded(activity);
            return true;
        } catch (Throwable t) {
            releasePanelKey(activeKey);
            Toast.makeText(activity, "Panel failed: " + shortMessage(t), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    static void releasePanelKey(String panelKey) {
        if (TextUtils.isEmpty(panelKey)) return;
        ACTIVE_PANEL_KEYS.remove(panelKey);
    }

    private static boolean reservePanelKey(String panelKey) {
        String key = clean(panelKey, "generic");
        synchronized (ACTIVE_PANEL_KEYS) {
            if (ACTIVE_PANEL_KEYS.contains(key)) return false;
            ACTIVE_PANEL_KEYS.add(key);
            return true;
        }
    }

    public static boolean startPanelActivity(Activity activity, Intent intent, String label) {
        if (activity == null || intent == null) return false;
        if (!PermsTestPanelSettings.isPanelHostEnabled(activity)) {
            Toast.makeText(activity, "Enable Popout Panels in Settings first", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            activity.startActivity(intent);
            PermsTestVrOverlayCompat.moveHostTaskBehindMemoryToolIfNeeded(activity);
            return true;
        } catch (Throwable t) {
            Toast.makeText(activity, clean(label, "Panel") + " failed: " + shortMessage(t), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static File writePanelText(Context context, String panelKey, String text) throws Exception {
        File dir = new File(context.getCacheDir(), "panels");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Unable to create panel cache");
        cleanupOldPanelFiles(dir);
        String safeKey = cleanFileName(panelKey);
        File file = new File(dir, safeKey + "_" + System.currentTimeMillis() + ".txt");
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            byte[] data = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            out.write(data);
            out.flush();
        }
        return file;
    }

    private static void cleanupOldPanelFiles(File dir) {
        try {
            File[] files = dir == null ? null : dir.listFiles();
            if (files == null) return;
            long cutoff = System.currentTimeMillis() - (24L * 60L * 60L * 1000L);
            for (File file : files) {
                if (file != null && file.isFile() && file.lastModified() < cutoff) {
                    try { file.delete(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String cleanFileName(String value) {
        String raw = clean(value, "generic");
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            sb.append(ok ? c : '_');
        }
        return sb.length() == 0 ? "generic" : sb.toString();
    }

    private static String clean(String value, String fallback) {
        if (TextUtils.isEmpty(value)) return fallback == null ? "" : fallback;
        return value.trim();
    }

    private static String shortMessage(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.toString() : msg;
    }
}
