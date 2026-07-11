package dev.perms.test.shizuku.internal;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Bundle;
import android.net.Uri;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import moe.shizuku.manager.adb.AdbClient;
import moe.shizuku.manager.adb.AdbKey;
import moe.shizuku.manager.adb.AdbPairingClient;
import moe.shizuku.manager.adb.PrefAdbKeyStore;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.IOException;

import moe.shizuku.api.BinderContainer;

/**
 * Runs Internal Shizuku adb operations in a dedicated process to isolate libadb crashes
 * and serialize commands (pair/connect/shell/start/stop).
 *
 * NOTE: This does not change any existing LADB UI/logic; it only reuses the existing LadbClient
 * implementation as an internal helper.
 */
public final class InternalAdbService extends Service {

    private static final String ACTION_PAIR_CONNECT_START = "dev.perms.test.action.INTERNAL_ADB_PAIR_CONNECT_START";
    private static final String ACTION_START_SERVER = "dev.perms.test.action.INTERNAL_ADB_START_SERVER";
    private static final String ACTION_STOP_SERVER = "dev.perms.test.action.INTERNAL_ADB_STOP_SERVER";

    private static final String EXTRA_HOST = "host";
    private static final String EXTRA_PAIR_PORT = "pair_port";
    private static final String EXTRA_CONNECT_PORT = "connect_port";
    private static final String EXTRA_MDNS_CONNECT_PORT = "mdns_connect_port";
    private static final String EXTRA_CODE = "code";
    private static final String EXTRA_FORCE_RESTART = "force_restart";

    private static final String PREFS = "perms_test";
    private static final String PREF_KEY_INTERNAL_SHIZUKU_CONNECTED = "internal_shizuku_connected";
    private static final String PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING = "internal_shizuku_server_running";
    private static final String PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID = "internal_shizuku_server_pid";
    private static final String PREF_KEY_INTERNAL_SHIZUKU_HOST = "internal_shizuku_host";
    private static final String PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT_USED = "internal_shizuku_connect_port_used";
    private static final String PREF_KEY_INTERNAL_MDNS_CONNECT_PORT = "internal_shizuku_mdns_connect_port";

    private static final String INTERNAL_ADB_KEY_STORE = "internal_shizuku_adbkey_v2";
    private static final String INTERNAL_ADB_KEY_NAME = "InternalShizuku";

    private static AdbKey createAdbKey(Context ctx, SharedPreferences sp) {
        return new AdbKey(ctx, new PrefAdbKeyStore(sp, INTERNAL_ADB_KEY_STORE), INTERNAL_ADB_KEY_NAME);
    }

    private static void debug(Context ctx, String msg) {
        InternalShizukuDiagnostics.debug(ctx, msg);
    }

    private static void info(Context ctx, String msg) {
        InternalShizukuDiagnostics.info(ctx, msg);
    }

    private static void warn(Context ctx, String msg, Throwable t) {
        InternalShizukuDiagnostics.warnVerbose(ctx, msg, t);
    }

    private static void reportResult(Context ctx, String msg, boolean ok) {
        try {
            InternalShizukuAdbMdnsService.postResult(ctx, msg, ok);
        } catch (Throwable ignored) {
        }
    }

    private ExecutorService exec;

    public static void enqueuePairConnectStart(Context ctx, String host, int pairPort, int connectPort, int mdnsConnectPort, String code) {
        Intent i = new Intent(ctx, InternalAdbService.class);
        i.setAction(ACTION_PAIR_CONNECT_START);
        i.putExtra(EXTRA_HOST, host);
        i.putExtra(EXTRA_PAIR_PORT, pairPort);
        i.putExtra(EXTRA_CONNECT_PORT, connectPort);
        i.putExtra(EXTRA_MDNS_CONNECT_PORT, mdnsConnectPort);
        i.putExtra(EXTRA_CODE, code);
        try {
            ctx.startService(i);
        } catch (Throwable ignored) {}
    }

    public static void enqueueStartServer(Context ctx) {
        enqueueStartServer(ctx, false);
    }

    public static void enqueueStartServer(Context ctx, boolean forceRestart) {
        Intent i = new Intent(ctx, InternalAdbService.class);
        i.setAction(ACTION_START_SERVER);
        i.putExtra(EXTRA_FORCE_RESTART, forceRestart);
        try { ctx.startService(i); } catch (Throwable ignored) {}
    }

    public static void enqueueStopServer(Context ctx) {
        Intent i = new Intent(ctx, InternalAdbService.class);
        i.setAction(ACTION_STOP_SERVER);
        try { ctx.startService(i); } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "InternalAdbService");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || TextUtils.isEmpty(intent.getAction())) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        final String action = intent.getAction();
        final Intent copy = intent;
        debug(getApplicationContext(), "queue action=" + action + " startId=" + startId + " flags=" + flags);
        exec.execute(() -> {
            try {
                handle(action, copy);
            } catch (Throwable t) {
                // Best-effort: report, but never crash the UI process.
                warn(getApplicationContext(), "Unhandled internal adb action error", t);
                reportResult(getApplicationContext(),
                        "Internal ADB error: " + t.getClass().getSimpleName() + ": " + t.getMessage(), false);
            } finally {
                stopSelfResult(startId);
            }
        });
        return START_NOT_STICKY;
    }

    private void handle(String action, Intent intent) {
        Context ctx = getApplicationContext();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, MODE_PRIVATE | MODE_MULTI_PROCESS);
        debug(ctx, "handle action=" + action);

        if (ACTION_PAIR_CONNECT_START.equals(action)) {
            String host = intent.getStringExtra(EXTRA_HOST);
            int pairPort = intent.getIntExtra(EXTRA_PAIR_PORT, 0);
            int connectPort = intent.getIntExtra(EXTRA_CONNECT_PORT, 0);
            int mdnsConnectPort = intent.getIntExtra(EXTRA_MDNS_CONNECT_PORT, 0);
            String code = intent.getStringExtra(EXTRA_CODE);
            debug(ctx, "pair/connect request host=" + host
                    + " pairPort=" + pairPort
                    + " connectPort=" + connectPort
                    + " mdnsConnectPort=" + mdnsConnectPort
                    + " codeLength=" + (code == null ? 0 : code.length()));

            // mDNS can resolve the connect port slightly later than the notification flow;
            // read the latest value before we proceed.
            if (mdnsConnectPort <= 0) {
                try { mdnsConnectPort = sp.getInt(PREF_KEY_INTERNAL_MDNS_CONNECT_PORT, 0); } catch (Throwable ignored) {}
            }

            if (TextUtils.isEmpty(host) || pairPort <= 0 || TextUtils.isEmpty(code)) {
                InternalShizukuDiagnostics.warn("pair/connect missing required data host=" + host
                        + " pairPort=" + pairPort
                        + " connectPort=" + connectPort
                        + " mdnsConnectPort=" + mdnsConnectPort
                        + " codePresent=" + !TextUtils.isEmpty(code));
                reportResult(ctx, "Internal Shizuku: missing host/ports/code.", false);
                return;
            }

            // 1:1 with Shizuku Manager: use direct ADB protocol client (no adb-server / libadb exec state).
            AdbKey adbKey = createAdbKey(ctx, sp);

            // Pair (TLS + SPAKE2).
            try (AdbPairingClient pc = new AdbPairingClient(host, pairPort, code, adbKey)) {
                pc.pair();
                info(ctx, "pair succeeded host=" + host + " pairPort=" + pairPort);
            } catch (Throwable t) {
                warn(ctx, "pair failed host=" + host + " pairPort=" + pairPort, t);
                if (t instanceof moe.shizuku.manager.adb.AdbInvalidPairingCodeException) {
                    reportResult(ctx, "Pairing code is wrong.", false);
                } else {
                    reportResult(ctx, "Pair failed: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()), false);
                }
                return;
            }

            // Connect (TLS/auth) and start server.
            // Pairing and connect mDNS can arrive on different NSD cycles, especially on Samsung.
            // Keep connect discovery alive here so a successful pair can continue into Start Server
            // without forcing the user through the pairing screen again.
            try { InternalShizukuAdbMdnsService.startConnectDiscovery(ctx); } catch (Throwable ignored) {}

            // Match upstream behavior: wait briefly for the mDNS connect port to appear.
            if (mdnsConnectPort <= 0) {
                long deadline = System.currentTimeMillis() + 5000L;
                while (System.currentTimeMillis() < deadline) {
                    int v = 0;
                    try { v = sp.getInt(PREF_KEY_INTERNAL_MDNS_CONNECT_PORT, 0); } catch (Throwable ignored) {}
                    if (v > 0 && v != pairPort && isPortLikelyOpen(host, v)) { mdnsConnectPort = v; break; }
                    try { Thread.sleep(200L); } catch (Throwable ignored) {}
                }
            }
            debug(ctx, "connect-port selection after wait mdnsConnectPort=" + mdnsConnectPort
                    + " connectPort=" + connectPort
                    + " pairPort=" + pairPort);

            // Prefer the mDNS "connect" port if available; also never try the pairing port.
            int usedConnectPort = 0;
            AdbClient adb = null;
            Throwable err1 = null;
            Throwable err2 = null;
            StringBuilder tried = new StringBuilder();

            // Some devices do not bind the wireless debugging connect port to 127.0.0.1.
            // Try the resolved local host (mDNS) first, then fall back to localhost.
            String storedHost = getStoredHost(sp);
            String[] hostCandidates;
            if (!TextUtils.isEmpty(host) && !host.equals(storedHost) && !"127.0.0.1".equals(host) && !"127.0.0.1".equals(storedHost)) {
                hostCandidates = new String[]{host, storedHost, "127.0.0.1"};
            } else if (!TextUtils.isEmpty(host) && !"127.0.0.1".equals(host)) {
                hostCandidates = new String[]{host, "127.0.0.1"};
            } else if (!TextUtils.isEmpty(storedHost) && !"127.0.0.1".equals(storedHost)) {
                hostCandidates = new String[]{storedHost, "127.0.0.1"};
            } else {
                hostCandidates = new String[]{"127.0.0.1"};
            }

            int priorUsed = 0;
            try { priorUsed = sp.getInt(PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT_USED, 0); } catch (Throwable ignored) {}
            int[] candidates = new int[]{mdnsConnectPort, connectPort, priorUsed};
            debug(ctx, "connect hostCandidates=" + java.util.Arrays.toString(hostCandidates)
                    + " portCandidates=" + java.util.Arrays.toString(candidates));

            if (!hasReachableEndpoint(hostCandidates, candidates)) {
                sp.edit()
                        .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_CONNECTED, false)
                        .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false)
                        .putInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, 0)
                        .apply();
                reportResult(ctx,
                        "Pairing succeeded, but the Wireless debugging connect port is not accepting connections yet. Keep Wireless debugging on, stay on the Wireless debugging screen, then tap Start Server.",
                        false);
                try { InternalShizukuAdbMdnsService.startConnectDiscovery(ctx); } catch (Throwable ignored) {}
                return;
            }

            outer:
            for (String h : hostCandidates) {
                if (TextUtils.isEmpty(h)) continue;
                for (int p : candidates) {
                    if (p <= 0) continue;
                    if (p == pairPort) continue;
                    try {
                        usedConnectPort = p;
                        try {
                            sp.edit()
                                    .putString(PREF_KEY_INTERNAL_SHIZUKU_HOST, h)
                                    .putInt(PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT_USED, usedConnectPort)
                                    .apply();
                        } catch (Throwable ignored) {}
                        debug(ctx, "connect attempt " + h + ":" + p);
                        adb = new AdbClient(h, p, adbKey);
                        adb.connect();
                        info(ctx, "connect succeeded " + h + ":" + p);
                        host = h;
                        break outer;
                    } catch (Throwable t) {
                        warn(ctx, "connect failed " + h + ":" + p, t);
                        if (tried.length() > 0) tried.append("; ");
                        tried.append(h).append(":").append(p).append(": ").append(t.getClass().getSimpleName());
                        String m = String.valueOf(t.getMessage());
                        if (m != null && m.length() > 0) tried.append("(").append(m).append(")");
                        try { if (adb != null) adb.close(); } catch (Throwable ignored) {}
                        adb = null;
                        if (err1 == null) err1 = t;
                        else err2 = t;
                    }
                }
            }


            if (adb == null) {
                Throwable t = err2 != null ? err2 : err1;
                String msg = (t == null)
                        ? "Connect failed: no connect port available."
                        : ("Connect failed: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
                if (tried.length() > 0) msg = msg + " (tried: " + tried + ")";
                InternalShizukuDiagnostics.warn("connect failed all candidates: " + msg);
                clearStoredConnectPorts(ctx, sp, "pair-connect-failed");
                reportResult(ctx, msg, false);
                return;
            }

            sp.edit()
                    .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_CONNECTED, true)
                    .putString(PREF_KEY_INTERNAL_SHIZUKU_HOST, host)
                    .putInt(PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT_USED, usedConnectPort)
                    .apply();

            // Start server (background) + verify it actually stayed up. Restart an older
            // PermsTest-owned server first so BinderSender sends a fresh binder to this process.
            stopExistingInternalServerQuietly(ctx, adb, sp, "pair-connect");
            info(ctx, "starting internal server after pair/connect host=" + host + " port=" + usedConnectPort);
            ShellResult r3 = startServer(ctx, adb);
            final int pid = parsePid(r3.stdout);
            final boolean up = (pid > 0) && verifyInternalServerPid(ctx, adb, pid, ctx.getPackageName());
            debug(ctx, "start after pair result ok=" + r3.ok + " pid=" + pid + " up=" + up + " summary=" + r3.summary());
            try { adb.close(); } catch (Throwable ignored) {}
            if (!r3.ok || !up) {
                sp.edit()
                        .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false)
                        .putInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, 0)
                        .apply();
                String extra = (pid > 0 && !up) ? (" pid=" + pid + " exited") : "";
                reportResult(ctx, "Connected, but server start failed: " + r3.summary() + extra, false);
                return;
            }

            boolean binderReady = waitForEmbeddedBinder(ctx, 6500L);
            debug(ctx, "start after pair binderReady=" + binderReady + " pid=" + pid);
            sp.edit()
                    .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, true)
                    .putInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, pid)
                    .apply();
            if (binderReady) {
                reportResult(ctx, "Internal Shizuku server started and binder attached.", true);
            } else {
                reportResult(ctx, "Internal Shizuku server is running as PermsTest, but binder did not attach yet. Use Restart Server and export logs with Debug Output on.", false);
            }
            return;
        }

        if (ACTION_START_SERVER.equals(action)) {
            final boolean forceRestart = intent != null && intent.getBooleanExtra(EXTRA_FORCE_RESTART, false);
            try { InternalShizukuAdbMdnsService.startConnectDiscovery(ctx); } catch (Throwable ignored) {}
            waitForUsableConnectPort(sp, 9000L);

            String[] hosts = buildHostCandidates(sp, getStoredHost(sp));
            int[] ports = buildConnectPortCandidates(sp);
            debug(ctx, "start-server request hosts=" + java.util.Arrays.toString(hosts)
                    + " ports=" + java.util.Arrays.toString(ports));

            if (!hasReachableEndpoint(hosts, ports)) {
                clearStoredConnectPorts(ctx, sp, "start-server-unreachable");
                reportResult(ctx,
                        "Start server failed: Wireless debugging connect port is stale or unavailable. Turn Wireless debugging on, open its settings screen, then tap Pair & Start or Start Server again.",
                        false);
                try { InternalShizukuAdbMdnsService.startConnectDiscovery(ctx); } catch (Throwable ignored) {}
                return;
            }

            AdbKey adbKey = createAdbKey(ctx, sp);
            ShellResult r = null;
            int pid = 0;
            boolean up = false;
            String usedHost = null;
            int usedPort = 0;
            StringBuilder tried = new StringBuilder();

            for (String h : hosts) {
                if (TextUtils.isEmpty(h)) continue;
                for (int p : ports) {
                    if (p <= 0) continue;
                    AdbClient adb = null;
                    try {
                        debug(ctx, "start-server connecting " + h + ":" + p);
                        adb = new AdbClient(h, p, adbKey);
                        adb.connect();
                        info(ctx, "start-server connected " + h + ":" + p);
                        stopExistingInternalServerQuietly(ctx, adb, sp,
                                forceRestart ? "manual-restart" : "clean-start");
                        r = startServer(ctx, adb);
                        pid = parsePid(r.stdout);
                        up = (pid > 0) && verifyInternalServerPid(ctx, adb, pid, ctx.getPackageName());
                        usedHost = h;
                        usedPort = p;
                        break;
                    } catch (Throwable t) {
                        warn(ctx, "start-server connect/start failed " + h + ":" + p, t);
                        if (tried.length() > 0) tried.append("; ");
                        tried.append(h).append(':').append(p).append(':')
                                .append(t.getClass().getSimpleName());
                        String m = String.valueOf(t.getMessage());
                        if (!TextUtils.isEmpty(m)) tried.append('(').append(m).append(')');
                        r = new ShellResult(false, "", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
                        pid = 0;
                        up = false;
                    } finally {
                        try { if (adb != null) adb.close(); } catch (Throwable ignored) {}
                    }
                }
                if (up) break;
            }

            if (r == null) {
                r = new ShellResult(false, "", "no connect endpoint available");
            }

            debug(ctx, "start-server action result ok=" + r.ok
                    + " pid=" + pid
                    + " up=" + up
                    + " used=" + usedHost + ':' + usedPort
                    + " summary=" + r.summary()
                    + (tried.length() > 0 ? " tried=" + tried : ""));
            if (r.ok && up) {
                boolean binderReady = waitForEmbeddedBinder(ctx, 6500L);
                debug(ctx, "start-server binderReady=" + binderReady + " pid=" + pid + " used=" + usedHost + ':' + usedPort);
                sp.edit()
                        .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_CONNECTED, true)
                        .putString(PREF_KEY_INTERNAL_SHIZUKU_HOST, usedHost)
                        .putInt(PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT_USED, usedPort)
                        .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, true)
                        .putInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, pid)
                        .apply();
                if (binderReady) {
                    reportResult(ctx, "Internal Shizuku server started and binder attached.", true);
                } else {
                    String tail = "";
                    AdbClient tailAdb = null;
                    try {
                        tailAdb = new AdbClient(usedHost, usedPort, adbKey);
                        tailAdb.connect();
                        tail = readRemoteTail(tailAdb, "/data/local/tmp/" + ctx.getPackageName() + "/internal_shizuku/shizuku_server_start.log", 80);
                    } catch (Throwable ignored) {
                    } finally {
                        try { if (tailAdb != null) tailAdb.close(); } catch (Throwable ignored) {}
                    }
                    reportResult(ctx, "Internal Shizuku server is running as PermsTest, but binder did not attach yet."
                            + (TextUtils.isEmpty(tail) ? "" : (" Log: " + InternalShizukuDiagnostics.oneLine(tail, 420))), false);
                }
            } else {
                sp.edit()
                        .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false)
                        .putInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, 0)
                        .apply();
                String extra = (pid > 0 && !up) ? (" pid=" + pid + " exited") : "";
                String triedText = tried.length() > 0 ? (" Tried: " + tried) : "";
                if (tried.length() > 0) clearStoredConnectPorts(ctx, sp, "start-server-failed");
                reportResult(ctx, "Start server failed: " + r.summary() + extra + triedText, false);
            }
            return;
        }

        if (ACTION_STOP_SERVER.equals(action)) {
            String host = getStoredHost(sp);
            int port = getBestStoredConnectPort(sp);
            debug(ctx, "stop-server request host=" + host + " port=" + port);
            int pid = 0;
            try { pid = sp.getInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, 0); } catch (Throwable ignored) {}
            // Best-effort stop: kill only the PermsTest-owned internal server. Do not stop
            // an installed Shizuku server that happens to use the same nice-name.
            String sh = buildStopInternalServerCommand(ctx, pid);
            ShellResult r;
            if (TextUtils.isEmpty(host) || port <= 0) {
                r = new ShellResult(false, "", "not connected");
            } else {
                AdbKey adbKey = createAdbKey(ctx, sp);
                try (AdbClient adb = new AdbClient(host, port, adbKey)) {
                    adb.connect();
                    r = shellShC(adb, sh);
                } catch (Throwable t) {
                    r = new ShellResult(false, "", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
                }
            }
            sp.edit()
                    .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false)
                    .putInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, 0)
                    .apply();
            reportResult(ctx, "Stop server requested: " + r.summary(), r.ok);
        }
    }


    private static boolean waitForEmbeddedBinder(Context ctx, long timeoutMs) {
        if (ctx == null) return false;
        final String pkg = ctx.getPackageName();
        final long deadline = System.currentTimeMillis() + Math.max(500L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            try {
                Bundle reply = ctx.getContentResolver().call(
                        Uri.parse("content://" + pkg + ".shizuku"),
                        rikka.shizuku.ShizukuProvider.METHOD_GET_BINDER,
                        null,
                        new Bundle());
                if (reply != null) {
                    reply.setClassLoader(BinderContainer.class.getClassLoader());
                    BinderContainer embedded = reply.getParcelable(pkg + ".intent.extra.BINDER");
                    if (embedded != null && embedded.binder != null && embedded.binder.pingBinder()) {
                        debug(ctx, "embedded binder observed through provider");
                        return true;
                    }
                }
            } catch (Throwable t) {
                debug(ctx, "embedded binder wait probe failed: " + InternalShizukuDiagnostics.throwableSummary(t));
            }
            try { Thread.sleep(250L); } catch (Throwable ignored) { break; }
        }
        debug(ctx, "embedded binder wait timed out hasEmbedded=" + safeEmbeddedBinderState());
        return false;
    }

    private static String safeEmbeddedBinderState() {
        try {
            return String.valueOf(rikka.shizuku.ShizukuProvider.hasEmbeddedBinder());
        } catch (Throwable t) {
            return InternalShizukuDiagnostics.throwableSummary(t);
        }
    }


    private static void stopExistingInternalServerQuietly(Context ctx, AdbClient adb, SharedPreferences sp, String reason) {
        if (adb == null) return;
        try {
            int pid = 0;
            try { pid = sp == null ? 0 : sp.getInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, 0); } catch (Throwable ignored) {}
            ShellResult r = shellShC(adb, buildStopInternalServerCommand(ctx, pid));
            debug(ctx, "stop-existing-internal reason=" + reason + " result=" + (r == null ? "null" : r.summary()));
            try { Thread.sleep(350L); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            warn(ctx, "stop existing internal server failed reason=" + reason, t);
        }
    }

    private static String buildStopInternalServerCommand(Context ctx, int pid) {
        String pkg = ctx == null ? "dev.perms.test" : ctx.getPackageName();
        String managerPattern = "shizuku.manager.package=" + pkg;
        String argPattern = "--manager-package=" + pkg;
        StringBuilder sh = new StringBuilder();
        if (pid > 0) {
            sh.append("kill ").append(pid).append(" >/dev/null 2>&1; ");
        }
        sh.append("for p in $(pgrep -f '").append(managerPattern).append("' 2>/dev/null || true); do kill $p >/dev/null 2>&1; done; ");
        sh.append("for p in $(pgrep -f '").append(argPattern).append("' 2>/dev/null || true); do kill $p >/dev/null 2>&1; done; ");
        sh.append("true");
        return sh.toString();
    }

    private static String getStoredHost(SharedPreferences sp) {
        try {
            String host = sp.getString(PREF_KEY_INTERNAL_SHIZUKU_HOST, null);
            if (TextUtils.isEmpty(host)) host = "127.0.0.1";
            return host;
        } catch (Throwable ignored) {
            return "127.0.0.1";
        }
    }

    private static int getBestStoredConnectPort(SharedPreferences sp) {
        int[] ports = buildConnectPortCandidates(sp);
        return ports.length == 0 ? 0 : ports[0];
    }

    private static int[] buildConnectPortCandidates(SharedPreferences sp) {
        ArrayList<Integer> out = new ArrayList<>();
        addPort(out, getIntPref(sp, PREF_KEY_INTERNAL_MDNS_CONNECT_PORT));
        addPort(out, getIntPref(sp, InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_CONNECT_PORT));
        addPort(out, getIntPref(sp, PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT_USED));
        int[] result = new int[out.size()];
        for (int i = 0; i < out.size(); i++) result[i] = out.get(i);
        return result;
    }

    private static String[] buildHostCandidates(SharedPreferences sp, String preferredHost) {
        ArrayList<String> out = new ArrayList<>();
        addHost(out, preferredHost);
        addHost(out, getStringPref(sp, PREF_KEY_INTERNAL_SHIZUKU_HOST));
        addHost(out, "127.0.0.1");
        return out.toArray(new String[0]);
    }

    private static boolean hasReachableEndpoint(String[] hosts, int[] ports) {
        if (hosts == null || ports == null) return false;
        for (String host : hosts) {
            if (TextUtils.isEmpty(host)) continue;
            for (int port : ports) {
                if (port > 0 && isPortLikelyOpen(host, port)) return true;
            }
        }
        return false;
    }

    private static void clearStoredConnectPorts(Context ctx, SharedPreferences sp, String reason) {
        if (sp == null) return;
        try {
            sp.edit()
                    .remove(PREF_KEY_INTERNAL_MDNS_CONNECT_PORT)
                    .remove(InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_CONNECT_PORT)
                    .remove(PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT_USED)
                    .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_CONNECTED, false)
                    .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false)
                    .putInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, 0)
                    .apply();
            debug(ctx, "cleared stale Internal Shizuku connect ports reason=" + reason);
        } catch (Throwable ignored) {
        }
    }

    private static void addPort(ArrayList<Integer> out, int port) {
        if (out == null || port <= 0) return;
        for (Integer existing : out) {
            if (existing != null && existing.intValue() == port) return;
        }
        out.add(port);
    }

    private static void addHost(ArrayList<String> out, String host) {
        if (out == null || TextUtils.isEmpty(host)) return;
        String h = host.trim();
        if (h.isEmpty()) return;
        for (String existing : out) {
            if (h.equals(existing)) return;
        }
        out.add(h);
    }

    private static int getIntPref(SharedPreferences sp, String key) {
        try { return sp == null ? 0 : sp.getInt(key, 0); } catch (Throwable ignored) { return 0; }
    }

    private static String getStringPref(SharedPreferences sp, String key) {
        try { return sp == null ? null : sp.getString(key, null); } catch (Throwable ignored) { return null; }
    }

    private static void waitForUsableConnectPort(SharedPreferences sp, long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            int now = getIntPref(sp, PREF_KEY_INTERNAL_MDNS_CONNECT_PORT);
            if (now > 0 && isPortLikelyOpen(getStringPref(sp, PREF_KEY_INTERNAL_SHIZUKU_HOST), now)) return;
            try { Thread.sleep(200L); } catch (Throwable ignored) { return; }
        }
    }

    private static boolean isPortLikelyOpen(String host, int port) {
        if (port <= 0) return false;
        if (isPortInUse("127.0.0.1", port)) return true;
        return !TextUtils.isEmpty(host) && isPortInUse(host, port);
    }

    private static boolean isPortInUse(String host, int port) {
        if (TextUtils.isEmpty(host) || port <= 0) return false;
        java.net.Socket socket = null;
        try {
            socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 350);
            return true;
        } catch (java.io.IOException ignored) {
            return false;
        } catch (Throwable ignored) {
            return false;
        } finally {
            try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
        }
    }

    private static int parsePid(String stdout) {
        String s = stdout == null ? "" : stdout.trim();
        if (s.isEmpty()) return 0;
        String[] parts = s.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            try {
                int v = Integer.parseInt(parts[i]);
                if (v > 0) return v;
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static int parseMarkedPid(String stdout, String marker) {
        if (TextUtils.isEmpty(stdout) || TextUtils.isEmpty(marker)) return 0;
        String[] lines = stdout.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) continue;
            int idx = line.indexOf(marker);
            if (idx < 0) continue;
            String tail = line.substring(idx + marker.length()).trim();
            String[] parts = tail.split("\\s+");
            if (parts.length == 0) continue;
            try {
                int v = Integer.parseInt(parts[0]);
                if (v > 0) return v;
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static int resolveInternalServerPid(Context ctx, AdbClient adb, int fallbackPid, String pkg) {
        if (adb == null || TextUtils.isEmpty(pkg)) return 0;
        long deadline = System.currentTimeMillis() + 3500L;
        while (System.currentTimeMillis() < deadline) {
            if (fallbackPid > 0 && verifyInternalServerPid(ctx, adb, fallbackPid, pkg)) {
                return fallbackPid;
            }
            try {
                ShellResult r = shellShC(adb, buildFindInternalServerPidCommand(pkg));
                int pid = parsePid(r == null ? null : r.stdout);
                if (pid > 0 && verifyInternalServerPid(ctx, adb, pid, pkg)) {
                    return pid;
                }
                debug(ctx, "internal pid probe result=" + (r == null ? "null" : r.summary()) + " pid=" + pid);
            } catch (Throwable t) {
                debug(ctx, "internal pid probe failed: " + InternalShizukuDiagnostics.throwableSummary(t));
            }
            try { Thread.sleep(250L); } catch (Throwable ignored) { break; }
        }
        return 0;
    }

    private static String buildFindInternalServerPidCommand(String pkg) {
        String safePkg = shellSingleQuote(pkg);
        return "for p in $(pidof shizuku_server 2>/dev/null; "
                + "pgrep -f rikka.shizuku.server.ShizukuService 2>/dev/null); do "
                + "cmd=$(tr '\\0' ' ' < /proc/$p/cmdline 2>/dev/null); "
                + "case \"$cmd\" in *\"shizuku.manager.package=" + pkg + "\"*|*\"--manager-package=" + pkg + "\"*) echo $p; exit 0;; esac; "
                + "done; "
                + "echo none_for_" + safePkg;
    }

    private static boolean verifyPidUp(AdbClient adb, int pid) {
        try {
            try { Thread.sleep(500L); } catch (Throwable ignored) {}
            ShellResult r = shellShC(adb, "kill -0 " + pid + " >/dev/null 2>&1; echo $?");
            if (!r.ok) return false;
            String out = r.stdout == null ? "" : r.stdout.trim();
            return out.endsWith("0");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean verifyInternalServerPid(Context ctx, AdbClient adb, int pid, String pkg) {
        if (pid <= 0 || TextUtils.isEmpty(pkg) || !verifyPidUp(adb, pid)) return false;
        try {
            ShellResult r = shellShC(adb,
                    "cmd=$(tr '\\0' ' ' < /proc/" + pid + "/cmdline 2>/dev/null); " +
                    "case \"$cmd\" in *\"shizuku.manager.package=" + pkg + "\"*|*\"--manager-package=" + pkg + "\"*) echo internal;; *) echo external:$cmd;; esac");
            String out = r == null || r.stdout == null ? "" : r.stdout.trim();
            boolean internal = out.startsWith("internal");
            debug(ctx, "verify internal pid=" + pid + " internal=" + internal + " out=" + InternalShizukuDiagnostics.oneLine(out, 260));
            return internal;
        } catch (Throwable t) {
            debug(ctx, "verify internal pid failed pid=" + pid + ": " + InternalShizukuDiagnostics.throwableSummary(t));
            return false;
        }
    }

    private static String describeInternalServerProcesses(AdbClient adb, String pkg) {
        if (adb == null || TextUtils.isEmpty(pkg)) return "";
        try {
            ShellResult r = shellShC(adb,
                    "for p in $(pidof shizuku_server 2>/dev/null; pgrep -f rikka.shizuku.server.ShizukuService 2>/dev/null); do " +
                    "cmd=$(tr '\\0' ' ' < /proc/$p/cmdline 2>/dev/null); " +
                    "uid=$(grep '^Uid:' /proc/$p/status 2>/dev/null | tr '\\t' ' '); " +
                    "echo pid=$p $uid cmd=$cmd; done; true");
            return r == null ? "" : InternalShizukuDiagnostics.oneLine(r.stdout, 900);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String shellSingleQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    
    private static byte[] readAssetBytes(Context ctx, String assetPath) throws Exception {
        try (InputStream is = ctx.getAssets().open(assetPath);
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            return os.toByteArray();
        }
    }


    private static byte[] readFileBytes(String path) throws Exception {
        if (TextUtils.isEmpty(path)) throw new IOException("empty path");
        try (InputStream is = new FileInputStream(new File(path));
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            return os.toByteArray();
        }
    }


    private static void addClasspathEntry(List<String> out, String path) {
        if (out == null || TextUtils.isEmpty(path)) return;
        String p = path.trim();
        if (p.isEmpty()) return;
        if (!out.contains(p)) out.add(p);
    }

    private static String buildInstalledApkClasspath(Context ctx) {
        try {
            android.content.pm.ApplicationInfo ai = ctx.getApplicationInfo();
            if (ai == null) return "";
            List<String> cp = new ArrayList<>();
            addClasspathEntry(cp, ai.sourceDir);
            try {
                String[] splits = ai.splitSourceDirs;
                if (splits != null) {
                    for (String s : splits) addClasspathEntry(cp, s);
                }
            } catch (Throwable ignored) {}
            return cp.isEmpty() ? "" : TextUtils.join(":", cp);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String buildInstalledApkClasspathFromPm(AdbClient adb, String pkg) {
        if (adb == null || TextUtils.isEmpty(pkg)) return "";
        try {
            ShellResult r = shellShC(adb, "pm path " + pkg);
            if (r == null || !r.ok || TextUtils.isEmpty(r.stdout)) return "";
            String[] lines = r.stdout.split("\r?\n");
            List<String> cp = new ArrayList<>();
            for (String line : lines) {
                if (line == null) continue;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("package:")) line = line.substring("package:".length()).trim();
                addClasspathEntry(cp, line);
            }
            return cp.isEmpty() ? "" : TextUtils.join(":", cp);
        } catch (Throwable ignored) {
            return "";
        }
    }

private static ShellResult startServer(Context ctx, AdbClient adb) {
    final String pkg = ctx.getPackageName();

    String lib = "";
    String apk = "";
    try {
        android.content.pm.ApplicationInfo ai = ctx.getApplicationInfo();
        if (ai != null) {
            lib = ai.nativeLibraryDir;
            apk = ai.sourceDir;
        }
    } catch (Throwable ignored) {}
    if (TextUtils.isEmpty(lib)) lib = "";

    final String remoteDir = "/data/local/tmp/" + pkg + "/internal_shizuku";
    final String remoteLog = remoteDir + "/shizuku_server_start.log";
    final String perm = pkg + ".permission.SHIZUKU_API_V23";
    debug(ctx, "startServer begin pkg=" + pkg
            + " apk=" + InternalShizukuDiagnostics.oneLine(apk, 180)
            + " lib=" + InternalShizukuDiagnostics.oneLine(lib, 180)
            + " remoteDir=" + remoteDir);

    try {
        ShellResult mk = shellShC(adb, "mkdir -p '" + remoteDir + "'");
        if (!mk.ok) return new ShellResult(false, "", "mkdir failed: " + mk.summary());
    } catch (Exception e) {
        return new ShellResult(false, "", "mkdir failed: " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
    }

    // Pick best app_process binary (match device bitness when possible).
    String appProcess = "/system/bin/app_process";
    try {
        boolean is64 = false;
        try { is64 = android.os.Process.is64Bit(); } catch (Throwable ignored) {}
        if (is64 && new File("/system/bin/app_process64").exists()) {
            appProcess = "/system/bin/app_process64";
        } else if (!is64 && new File("/system/bin/app_process32").exists()) {
            appProcess = "/system/bin/app_process32";
        }
    } catch (Throwable ignored) {}
    debug(ctx, "startServer appProcess=" + appProcess);
    if (InternalShizukuDiagnostics.isEnabled(ctx)) {
        debug(ctx, "startServer probe: " + runStartProbe(adb, pkg, appProcess, remoteDir));
    }

    try {
        shellShC(adb, buildStopInternalServerCommand(ctx, 0));
    } catch (Throwable ignored) {}

    String firstFail = "";

    // Strategy A: dedicated tiny internal_server APK packaged as an asset.
    // Android 14+ rejects writable dynamically loaded code files for app_process/ART.
    // Push as read-only and chmod again on-device to avoid adbd/umask normalizing to 0644.
    // This keeps ShizukuService in a predictable classpath container and avoids sample APK multidex issues.
    final String remoteApk = remoteDir + "/internal_shizuku_server.apk";
    try {
        byte[] apkBytes = readAssetBytes(ctx, "internal_shizuku/internal_shizuku_server.apk");
        debug(ctx, "strategy asset-apk push bytes=" + apkBytes.length + " remote=" + remoteApk);
        adb.syncPush(apkBytes, remoteApk, 0100444, (int) (System.currentTimeMillis() / 1000L));
        try { shellShC(adb, "chmod 400 '" + remoteApk + "' >/dev/null 2>&1 || true"); } catch (Throwable ignored) {}
        if (InternalShizukuDiagnostics.isEnabled(ctx)) {
            debug(ctx, "strategy asset-apk remote stat=" + shellShC(adb, "ls -l '" + remoteApk + "' 2>&1; wc -c < '" + remoteApk + "' 2>&1").summary());
        }

        ShellResult r = startServerWithClasspath(ctx, adb, "asset-apk", appProcess, pkg, perm, lib, remoteLog, remoteApk);
        debug(ctx, "strategy asset-apk result=" + r.summary());
        if (r.ok) return r;
        firstFail = appendDiag(firstFail, "asset-apk classpath failed: " + r.summary());
        if (!TextUtils.isEmpty(r.stderr) && !r.stderr.contains("no log")) {
            return new ShellResult(false, r.stdout, r.stderr + "\n(classpath=" + remoteApk + ")");
        }
    } catch (Throwable t) {
        warn(ctx, "asset-apk strategy setup failed", t);
        String setupFail = "asset-apk setup failed: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
        firstFail = appendDiag(firstFail, setupFail);
        if (isAdbTransportError(setupFail)) {
            return new ShellResult(false, "", firstFail);
        }
    }

    // Strategy B: use the installed PermsTest APK classpath directly.
    // Keep this as a fallback because the sample APK can be multidex-heavy on app_process.
    try {
        String installedCp = buildInstalledApkClasspath(ctx);
        debug(ctx, "strategy installed-apk classpath=" + InternalShizukuDiagnostics.oneLine(installedCp, 260));
        if (TextUtils.isEmpty(installedCp)) {
            installedCp = buildInstalledApkClasspathFromPm(adb, pkg);
        }
        if (!TextUtils.isEmpty(installedCp)) {
            ShellResult r = startServerWithClasspath(ctx, adb, "installed-apk", appProcess, pkg, perm, lib, remoteLog, installedCp);
            if (r.ok) return r;
            firstFail = appendDiag(firstFail, "installed-apk classpath failed: " + r.summary());
            if (!TextUtils.isEmpty(r.stderr) && !r.stderr.contains("no log")) {
                firstFail = appendDiag(firstFail, r.stderr);
                if (isAdbTransportError(r.stderr)) {
                    // Once the ADB stream is out of sync, reusing the same connection for
                    // heavier fallback pushes usually extends the failure and can make
                    // Wireless debugging drop the connection.
                    return new ShellResult(false, r.stdout, firstFail);
                }
            }
        } else {
            firstFail = appendDiag(firstFail, "installed-apk classpath unavailable");
        }
    } catch (Throwable t) {
        warn(ctx, "installed-apk strategy setup failed", t);
        firstFail = appendDiag(firstFail, "installed-apk setup failed: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
    }

    // Strategy C: extract all classes*.dex from installed base.apk and pass them explicitly in CLASSPATH.
    // Some builds place rikka.shizuku.server.ShizukuService into classesN.dex, which app_process won't find via APK-only CLASSPATH.
    try {
        if (TextUtils.isEmpty(apk)) {
            try {
                ShellResult pathRes = shellShC(adb, "pm path " + pkg);
                if (pathRes != null && pathRes.ok && !TextUtils.isEmpty(pathRes.stdout)) {
                    String[] lines = pathRes.stdout.split("\r?\n");
                    for (String line : lines) {
                        if (line == null) continue;
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        if (line.startsWith("package:")) line = line.substring("package:".length());
                        apk = line.trim();
                        if (!TextUtils.isEmpty(apk) && apk.endsWith("/base.apk")) break;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (TextUtils.isEmpty(apk)) {
            return new ShellResult(false, "", appendDiag(firstFail, "Unable to resolve base.apk path for " + pkg));
        }

        List<DexBlob> dexBlobs = extractDexBlobs(readFileBytes(apk));
        debug(ctx, "strategy fallback-dex source=" + InternalShizukuDiagnostics.oneLine(apk, 220)
                + " dexCount=" + dexBlobs.size());
        if (dexBlobs.isEmpty()) {
            return new ShellResult(false, "", appendDiag(firstFail, "No classes*.dex found in base.apk: " + apk));
        }

        try { shellShC(adb, "rm -f '" + remoteDir + "/cp_classes" + "*.dex' >/dev/null 2>&1 || true"); } catch (Throwable ignored) {}

        List<String> cp = new ArrayList<>(dexBlobs.size());
        for (DexBlob d : dexBlobs) {
            String remoteDex = remoteDir + "/cp_" + d.name;
            adb.syncPush(d.data, remoteDex, 0100444, (int) (System.currentTimeMillis() / 1000L));
            try { shellShC(adb, "chmod 400 '" + remoteDex + "' >/dev/null 2>&1 || true"); } catch (Throwable ignored) {}
            cp.add(remoteDex);
        }

        String dexClasspath = TextUtils.join(":", cp);
        ShellResult r = startServerWithClasspath(ctx, adb, "fallback-dex", appProcess, pkg, perm, lib, remoteLog, dexClasspath);
        if (r.ok) return r;
        return new ShellResult(false, r.stdout, appendDiag(firstFail, r.stderr + "\n(fallback dex classpath entries=" + cp.size() + ")"));
    } catch (Throwable t) {
        return new ShellResult(false, "", appendDiag(firstFail, "fallback-dex setup failed: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())));
    }
}

private static ShellResult startServerWithClasspath(Context ctx, AdbClient adb, String label, String appProcess, String pkg, String perm, String lib, String remoteLog, String classpath) {
    final String sh =
            "rm -f '" + remoteLog + "'; " +
            "CP='" + classpath + "'; " +
            (TextUtils.isEmpty(lib) ? "" : ("LIB='" + lib + "'; ")) +
            // Match upstream starter behavior: export CLASSPATH on the same invocation.
            "toybox setsid env CLASSPATH=\"$CP\" " +
            appProcess +
            " -Djava.class.path=\"$CP\"" +
            " -Dshizuku.manager.package=" + pkg +
            " -Dshizuku.permission=" + perm +
            (TextUtils.isEmpty(lib) ? "" : (" -Dshizuku.library.path=\"$LIB\"")) +
            " /system/bin" +
            " --nice-name=shizuku_server" +
            " rikka.shizuku.server.ShizukuService" +
            " --manager-package=" + pkg +
            " --permission=" + perm +
            " </dev/null >'" + remoteLog + "' 2>&1 & pid=$!; echo __PTS_INTERNAL_SERVER_PID__:$pid";

    debug(ctx, "startServerWithClasspath label=" + label
            + " classpathLength=" + (classpath == null ? 0 : classpath.length())
            + " classpath=" + InternalShizukuDiagnostics.oneLine(classpath, 260));

    final ShellResult r;
    try {
        r = shellShC(adb, sh);
    } catch (Exception e) {
        warn(ctx, "startServerWithClasspath shell failed label=" + label, e);
        return new ShellResult(false, "", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
    }

    int shellPid = parseMarkedPid(r.stdout, "__PTS_INTERNAL_SERVER_PID__:");
    int serverPid = resolveInternalServerPid(ctx, adb, shellPid, pkg);
    debug(ctx, "startServerWithClasspath label=" + label
            + " shellResult=" + r.summary()
            + " shellPid=" + shellPid
            + " serverPid=" + serverPid
            + " processes=" + describeInternalServerProcesses(adb, pkg));
    if (serverPid > 0) {
        return new ShellResult(true, String.valueOf(serverPid), r.stderr);
    }

    try {
        String tail = readRemoteTail(adb, remoteLog, 120);
        if (!TextUtils.isEmpty(tail)) {
            InternalShizukuDiagnostics.warn("shizuku_server did not stay up label=" + label
                    + " shellPid=" + shellPid + " tail=" + InternalShizukuDiagnostics.oneLine(tail, 600));
            return new ShellResult(false, r.stdout, "shizuku_server did not stay up. log:\n" + tail);
        }
    } catch (Throwable ignored) {}

    InternalShizukuDiagnostics.warn("shizuku_server did not stay up and produced no log label=" + label
            + " shellPid=" + shellPid
            + " classpath=" + InternalShizukuDiagnostics.oneLine(classpath, 260));
    return new ShellResult(false, r.stdout, "internal shizuku_server did not stay up (no matching PermsTest-owned process). processes="
            + describeInternalServerProcesses(adb, pkg) + " classpath=" + classpath);
}

private static String runStartProbe(AdbClient adb, String pkg, String appProcess, String remoteDir) {
    if (adb == null) return "adb=null";
    String cmd = "echo id=$(id); "
            + "echo sdk=$(getprop ro.build.version.sdk); "
            + "echo abi=$(getprop ro.product.cpu.abi); "
            + "echo app_process='" + appProcess + "'; "
            + "ls -l '" + appProcess + "' 2>&1; "
            + "echo pm_path; pm path " + pkg + " 2>&1; "
            + "echo tmp_dir; ls -ld /data/local/tmp /data/local/tmp/" + pkg + " '" + remoteDir + "' 2>&1";
    try {
        ShellResult r = shellShC(adb, cmd);
        String out = (r.stdout == null ? "" : r.stdout.trim());
        String err = (r.stderr == null ? "" : r.stderr.trim());
        String combined = TextUtils.isEmpty(err) ? out : (out + " stderr=" + err);
        return InternalShizukuDiagnostics.oneLine(combined, 900);
    } catch (Throwable t) {
        return InternalShizukuDiagnostics.throwableSummary(t);
    }
}

private static boolean isAdbTransportError(String text) {
    if (TextUtils.isEmpty(text)) return false;
    String s = text.toLowerCase(java.util.Locale.US);
    return s.contains("expected wrte/clse")
            || s.contains("expected okay/clse")
            || s.contains("stream closed")
            || s.contains("eofexception")
            || s.contains("socketexception")
            || s.contains("ssl_read failed");
}

private static String appendDiag(String a, String b) {
    String aa = a == null ? "" : a.trim();
    String bb = b == null ? "" : b.trim();
    if (TextUtils.isEmpty(aa)) return bb;
    if (TextUtils.isEmpty(bb)) return aa;
    return aa + "\n" + bb;
}

private static String readRemoteTail(AdbClient adb, String path, int lines) {
    if (adb == null || TextUtils.isEmpty(path) || lines <= 0) return "";
    try {
        ShellResult r = shellShC(adb, "tail -n " + lines + " '" + path + "' 2>/dev/null");
        String out = r.stdout == null ? "" : r.stdout.trim();
        if (out.length() > 1200) {
            out = out.substring(out.length() - 1200);
        }
        return out;
    } catch (Throwable ignored) {
        return "";
    }
}

private static final class DexBlob {
        final String name;
        final int order;
        final byte[] data;

        DexBlob(String name, int order, byte[] data) {
            this.name = name;
            this.order = order;
            this.data = data;
        }
    }

    /**
     * Extract classes*.dex entries from an APK byte[].
     * We sort by dex order: classes.dex (1), classes2.dex (2), ...
     */
    private static List<DexBlob> extractDexBlobs(byte[] apkBytes) throws IOException {
        if (apkBytes == null) return Collections.emptyList();

        List<DexBlob> out = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(apkBytes))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name == null) continue;
                if (!name.startsWith("classes") || !name.endsWith(".dex")) continue;

                int order = 1;
                if (!"classes.dex".equals(name)) {
                    // classes2.dex -> 2, classes10.dex -> 10
                    String mid = name.substring("classes".length(), name.length() - ".dex".length());
                    try {
                        order = Integer.parseInt(mid);
                    } catch (Throwable ignored) {
                        order = 1000; // keep unknowns at the end
                    }
                }

                ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(1024, (int) Math.min(Integer.MAX_VALUE, e.getSize())));
                int n;
                while ((n = zis.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                out.add(new DexBlob(name, order, bos.toByteArray()));
            }
        }

        Collections.sort(out, new Comparator<DexBlob>() {
            @Override
            public int compare(DexBlob a, DexBlob b) {
                return Integer.compare(a.order, b.order);
            }
        });
        return out;
    }
private static ShellResult shellShC(AdbClient adb, String shCmd) throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        String safe = shCmd == null ? "" : shCmd.replace("'", "'\\''");
        adb.shellCommand("sh -c '" + safe + "'", data -> {
            try { bos.write(data); } catch (Throwable ignored) {}
        });
        String out = bos.toString(StandardCharsets.UTF_8);
        return new ShellResult(true, out, "");

    }

    private static final class ShellResult {
        final boolean ok;
        final String stdout;
        final String stderr;
        ShellResult(boolean ok, String stdout, String stderr) {
            this.ok = ok;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
        String summary() {
            if (!TextUtils.isEmpty(stderr)) return "stderr=" + oneLine(stderr);
            if (!TextUtils.isEmpty(stdout)) return "stdout=" + oneLine(stdout);
            return ok ? "ok" : "failed";
        }
        private static String oneLine(String s) {
            s = s.replace("\n", " ").replace("\r", " ").trim();
            return s.length() > 200 ? s.substring(0, 200) + "…" : s;
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
