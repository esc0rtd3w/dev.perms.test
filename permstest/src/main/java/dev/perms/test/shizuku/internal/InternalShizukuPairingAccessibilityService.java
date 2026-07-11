package dev.perms.test.shizuku.internal;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight helper that watches the Wireless debugging screens and extracts the displayed port.
 * This lets the UI ask the user only for the 6-digit pairing code (like Shizuku), while we
 * auto-fill the pairing/connect ports.
 */
public final class InternalShizukuPairingAccessibilityService extends AccessibilityService {

    public static final String PREF_KEY_INTERNAL_PAIR_PORT = "internal_shizuku_pair_port";
    public static final String PREF_KEY_INTERNAL_CONNECT_PORT = "internal_shizuku_connect_port";

    // Must match MainActivity's prefs file name and keys.
    private static final String PREFS = "perms_test";
    private static final String PREF_KEY_PAIR_ARMED = "internal_shizuku_pair_armed";

    // Samsung/Pixel/etc. may render "IP:PORT" with whitespace around ':'; be flexible.
    private static final Pattern IPV4_PORT = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){3})\\s*[:：]\\s*(\\d{2,5})");
    private static final Pattern ANY_PORT = Pattern.compile("\\b(\\d{2,5})\\b");

    private static boolean isSettingsEvent(AccessibilityEvent event) {
        try {
            CharSequence pn = event == null ? null : event.getPackageName();
            if (pn == null) return false;
            String p = String.valueOf(pn);
            return p.contains("settings");
        } catch (Throwable t) {
            return false;
        }
    }

    // Small mutable holder to keep scanning logic tidy.
    private static final class PortHolder {
        int detectedPairPort;
        int detectedConnectPort;
        boolean nextIsConnect;
        boolean nextIsPair;
    }

    private final PortHolder holder = new PortHolder();

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            // Only care about Settings UI (Wireless debugging screen).
            if (!isSettingsEvent(event)) return;

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            // Collect a small set of visible text strings.
            ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
            q.add(root);

            holder.detectedPairPort = 0;
            holder.detectedConnectPort = 0;

            // Also consider the event text list (some OEMs omit node.getText but include event text).
            try {
                if (event != null && event.getText() != null) {
                    for (CharSequence ecs : event.getText()) {
                        scanText(String.valueOf(ecs), holder);
                    }
                }
            } catch (Throwable ignored) {}

            while (!q.isEmpty()) {
                AccessibilityNodeInfo n = q.removeFirst();
                if (n == null) continue;

                // Read both text and contentDescription. Some OEM builds populate only one.
                try {
                    CharSequence tcs = n.getText();
                    if (!TextUtils.isEmpty(tcs)) scanText(String.valueOf(tcs), holder);
                } catch (Throwable ignored) {}
                try {
                    CharSequence dcs = n.getContentDescription();
                    if (!TextUtils.isEmpty(dcs)) scanText(String.valueOf(dcs), holder);
                } catch (Throwable ignored) {}

                for (int i = 0; i < n.getChildCount(); i++) {
                    try {
                        AccessibilityNodeInfo c = n.getChild(i);
                        if (c != null) q.add(c);
                    } catch (Throwable ignored) {}
                }
            }

            int detectedPairPort = holder.detectedPairPort;
            int detectedConnectPort = holder.detectedConnectPort;

            if (detectedPairPort > 0 || detectedConnectPort > 0) {
                // If internal mode is enabled, keep a notification-based helper available.
                SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor ed = sp.edit();
                if (detectedPairPort > 0) ed.putInt(PREF_KEY_INTERNAL_PAIR_PORT, detectedPairPort);
                if (detectedConnectPort > 0) ed.putInt(PREF_KEY_INTERNAL_CONNECT_PORT, detectedConnectPort);
                ed.apply();

                // If user armed pairing flow, keep notification helper updated (no activity switch).
                // Do not show a pairing notification when we only saw the normal connect port;
                // Internal Shizuku can use that for Start Server without asking for a new code.
                try {
                    boolean armed = sp.getBoolean(PREF_KEY_PAIR_ARMED, false);
                    if (detectedPairPort > 0 && (InternalShizukuController.isSelected(sp) || armed)) {
                        InternalShizukuPairInputReceiver.postHelper(this, detectedPairPort);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onInterrupt() {
        // no-op
    }

    private static int safePort(String s) {
        try {
            int p = Integer.parseInt(String.valueOf(s).trim());
            if (p < 1 || p > 65535) return 0;
            return p;
        } catch (Throwable t) {
            return 0;
        }
    }

    private void scanText(String t, PortHolder h) {
        if (TextUtils.isEmpty(t) || h == null) return;

        final String tl = t.toLowerCase();

        // Keyword hints so we can classify the next port we see.
        if (tl.contains("ip address") || tl.contains("ip address & port") || tl.contains("ip address and port")) {
            h.nextIsConnect = true;
            h.nextIsPair = false;
        } else if (tl.contains("pair device") || tl.contains("pairing code") || tl.contains("pairing")) {
            h.nextIsPair = true;
        }

        // Prefer IP:PORT patterns.
        try {
            Matcher m = IPV4_PORT.matcher(t);
            while (m.find()) {
                int port = safePort(m.group(2));
                if (port <= 0) continue;

                if (h.nextIsConnect) {
                    h.detectedConnectPort = port;
                    h.nextIsConnect = false;
                    continue;
                }
                if (h.nextIsPair) {
                    h.detectedPairPort = port;
                    // keep nextIsPair true; some OEMs repeat the pairing block
                    continue;
                }

                // Heuristic fallback:
                // - First seen IP:PORT is usually the "IP address & Port" (connect) on the main Wireless debugging screen.
                // - If we already have a connect port, treat a different high port as pairing.
                if (h.detectedConnectPort <= 0) {
                    h.detectedConnectPort = port;
                } else if (h.detectedPairPort <= 0 && port != h.detectedConnectPort) {
                    h.detectedPairPort = port;
                }
            }
        } catch (Throwable ignored) {}

        // Fallback: if the screen shows just numbers, pick the most plausible using the same hints.
        try {
            Matcher m2 = ANY_PORT.matcher(t);
            while (m2.find()) {
                int port = safePort(m2.group(1));
                if (port <= 0) continue;

                if (h.nextIsConnect && h.detectedConnectPort <= 0) {
                    h.detectedConnectPort = port;
                    h.nextIsConnect = false;
                    continue;
                }
                if (h.nextIsPair && h.detectedPairPort <= 0) {
                    h.detectedPairPort = port;
                    continue;
                }
                if (h.detectedConnectPort <= 0) {
                    h.detectedConnectPort = port;
                } else if (h.detectedPairPort <= 0 && port != h.detectedConnectPort) {
                    h.detectedPairPort = port;
                }
            }
        } catch (Throwable ignored) {}
    }
}
