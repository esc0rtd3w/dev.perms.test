package dev.perms.test.link;

import android.text.TextUtils;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small LAN session server for Multiplayer Link status and safe guest messages. */
public final class MultiplayerLinkServer {
    private static final int SOCKET_TIMEOUT_MS = 12000;
    private static final int MAX_BODY_BYTES = 64 * 1024;

    public interface SnapshotProvider {
        MultiplayerLinkPreferences.LinkSnapshot snapshot();
    }

    public interface Listener {
        void onLog(String message);
    }

    private final Object lock = new Object();
    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;
    private Thread acceptThread;
    private volatile boolean running;
    private volatile int port;
    private volatile SnapshotProvider snapshotProvider;
    private volatile Listener listener;

    public void start(int requestedPort, SnapshotProvider provider, Listener listener) throws IOException {
        synchronized (lock) {
            stopLocked();
            this.port = MultiplayerLinkPreferences.clampPort(requestedPort);
            this.snapshotProvider = provider;
            this.listener = listener;
            this.serverSocket = new ServerSocket(this.port);
            this.clientExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "PermsTestLinkClient");
                t.setDaemon(true);
                return t;
            });
            this.running = true;
            this.acceptThread = new Thread(this::acceptLoop, "PermsTestLinkServer");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
            log("Multiplayer Link host started on port " + this.port);
        }
    }

    public void stop() {
        synchronized (lock) {
            stopLocked();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    private void stopLocked() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Throwable ignored) {}
        serverSocket = null;
        try { if (clientExecutor != null) clientExecutor.shutdownNow(); } catch (Throwable ignored) {}
        clientExecutor = null;
        acceptThread = null;
    }

    private void acceptLoop() {
        while (running) {
            try {
                ServerSocket ss = serverSocket;
                if (ss == null) break;
                Socket socket = ss.accept();
                ExecutorService executor = clientExecutor;
                if (executor != null) executor.execute(() -> handleSocket(socket));
                else closeQuietly(socket);
            } catch (IOException e) {
                if (running) log("Accept failed: " + e.getMessage());
            } catch (Throwable t) {
                if (running) log("Accept crashed: " + t.getMessage());
            }
        }
        running = false;
    }

    private void handleSocket(Socket socket) {
        try (Socket ignored = socket;
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            Request request = readRequest(in);
            if (request == null) return;
            request.remote = remoteLabel(socket);
            log("Guest connected from " + request.remote + " " + request.method + " " + request.path);
            Response response = dispatch(request);
            writeResponse(out, response);
        } catch (Throwable t) {
            log("Client failed: " + t.getMessage());
            closeQuietly(socket);
        }
    }

    private Response dispatch(Request request) {
        String path = request.path == null ? "/" : request.path;
        if ("/".equals(path) || "/link".equals(path) || "/link/status".equals(path)) {
            MultiplayerLinkPreferences.LinkSnapshot snapshot = snapshot();
            JSONObject body = snapshot == null ? new JSONObject() : snapshot.toJson();
            return Response.json(200, body.toString());
        }
        if ("/link/ping".equals(path)) {
            return Response.text(200, "PermsTest Multiplayer Link OK\n");
        }
        if (path.startsWith("/link/action")) {
            return handleGuestAction(request);
        }
        return Response.text(404, "Not found\n");
    }

    private Response handleGuestAction(Request request) {
        if (request == null || !("POST".equals(request.method) || "GET".equals(request.method))) {
            return Response.text(405, "Method not allowed\n");
        }
        JSONObject input = parseJsonObject(request.body);
        String action = safeAction(input.optString("action", "ping"));
        String message = trimForLog(input.optString("message", ""), 512);
        String client = trimForLog(input.optString("client", ""), 128);
        String remote = request.remote == null ? "unknown" : request.remote;
        MultiplayerLinkPreferences.LinkSnapshot snapshot = snapshot();

        JSONObject body = new JSONObject();
        try {
            body.put("accepted", true);
            body.put("action", action);
            body.put("remote", remote);
            body.put("client", client);
            if (!isAllowed(snapshot, action)) {
                body.put("accepted", false);
                body.put("message", "Guest action disabled by host");
                log("Rejected guest action from " + remote + optionalClient(client) + ": " + action);
                return Response.json(403, body.toString());
            }
            if ("ping".equals(action)) {
                body.put("message", "Ping received by host");
                log("Guest ping from " + remote + optionalClient(client));
            } else if ("test_message".equals(action)) {
                body.put("message", TextUtils.isEmpty(message) ? "Test message received by host" : message);
                log("Guest test message from " + remote + optionalClient(client) + ": " + (TextUtils.isEmpty(message) ? "Test message from guest" : message));
            } else if ("message".equals(action)) {
                body.put("message", message);
                log("Guest message from " + remote + optionalClient(client) + ": " + (TextUtils.isEmpty(message) ? "(empty)" : message));
            } else if ("client_info".equals(action)) {
                body.put("message", message);
                log("Guest device info from " + remote + optionalClient(client) + ": " + (TextUtils.isEmpty(message) ? "(empty)" : message));
            } else if ("shared_objects".equals(action)) {
                JSONObject shared = snapshot == null ? new JSONObject() : snapshot.toJson();
                shared.put("accepted", true);
                shared.put("action", action);
                shared.put("remote", remote);
                shared.put("client", client);
                log("Guest requested Shared Objects from " + remote + optionalClient(client));
                return Response.json(202, shared.toString());
            } else {
                body.put("accepted", false);
                body.put("message", "Unsupported guest action");
                log("Unsupported guest action from " + remote + optionalClient(client) + ": " + action);
                return Response.json(400, body.toString());
            }
        } catch (Throwable ignored) {
        }
        return Response.json(202, body.toString());
    }

    private MultiplayerLinkPreferences.LinkSnapshot snapshot() {
        return snapshotProvider == null ? null : snapshotProvider.snapshot();
    }

    private static boolean isAllowed(MultiplayerLinkPreferences.LinkSnapshot snapshot, String action) {
        if (snapshot == null) return false;
        if ("ping".equals(action)) return snapshot.allowGuestPing;
        if ("test_message".equals(action) || "message".equals(action) || "client_info".equals(action)) {
            return snapshot.allowGuestMessages;
        }
        if ("shared_objects".equals(action)) return snapshot.allowSharedObjects;
        return false;
    }

    private static JSONObject parseJsonObject(String body) {
        try {
            return TextUtils.isEmpty(body) ? new JSONObject() : new JSONObject(body);
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private static String safeAction(String value) {
        String action = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if ("ping".equals(action)
                || "test_message".equals(action)
                || "message".equals(action)
                || "client_info".equals(action)
                || "shared_objects".equals(action)) return action;
        return action.length() == 0 ? "ping" : action;
    }

    private static String trimForLog(String value, int max) {
        String s = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        if (max > 0 && s.length() > max) s = s.substring(0, max) + "...";
        return s;
    }

    private static String optionalClient(String client) {
        return TextUtils.isEmpty(client) ? "" : " (client " + client + ")";
    }

    private Request readRequest(BufferedInputStream in) throws IOException {
        String header = readHeader(in);
        if (header == null || header.length() == 0) return null;
        String[] lines = header.split("\\r?\\n");
        if (lines.length == 0) return null;
        String[] first = lines[0].trim().split("\\s+");
        if (first.length < 2) return null;
        Request r = new Request();
        r.method = first[0].toUpperCase(Locale.US);
        String rawPath = first[1];
        int q = rawPath.indexOf('?');
        r.path = decode(q >= 0 ? rawPath.substring(0, q) : rawPath);
        r.headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int c = line.indexOf(':');
            if (c > 0) r.headers.put(line.substring(0, c).trim().toLowerCase(Locale.US), line.substring(c + 1).trim());
        }
        int contentLength = parseInt(r.headers.get("content-length"), 0);
        if (contentLength > 0) {
            int safe = Math.min(contentLength, MAX_BODY_BYTES);
            byte[] body = new byte[safe];
            int off = 0;
            while (off < safe) {
                int n = in.read(body, off, safe - off);
                if (n < 0) break;
                off += n;
            }
            r.body = new String(body, 0, off, StandardCharsets.UTF_8);
        }
        return r;
    }

    private String readHeader(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        int previous3 = -1, previous2 = -1, previous1 = -1;
        int b;
        while ((b = in.read()) >= 0) {
            out.write(b);
            if (previous3 == '\r' && previous2 == '\n' && previous1 == '\r' && b == '\n') break;
            if (previous2 == '\n' && previous1 == '\n') break;
            previous3 = previous2;
            previous2 = previous1;
            previous1 = b;
            if (out.size() > 16384) throw new IOException("Header too large");
        }
        return out.toString("UTF-8");
    }

    private void writeResponse(BufferedOutputStream out, Response response) throws IOException {
        byte[] body = response.body == null ? new byte[0] : response.body.getBytes(StandardCharsets.UTF_8);
        String statusText = response.status == 200 ? "OK"
                : response.status == 202 ? "Accepted"
                : response.status == 400 ? "Bad Request"
                : response.status == 403 ? "Forbidden"
                : response.status == 404 ? "Not Found"
                : response.status == 405 ? "Method Not Allowed"
                : "OK";
        String header = "HTTP/1.1 " + response.status + " " + statusText + "\r\n"
                + "Content-Type: " + response.contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "Cache-Control: no-store\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String remoteLabel(Socket socket) {
        try {
            if (socket == null || socket.getInetAddress() == null) return "unknown";
            String host = socket.getInetAddress().getHostAddress();
            int port = socket.getPort();
            return port > 0 ? host + ":" + port : host;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value == null ? "" : value, "UTF-8"); } catch (Throwable t) { return value == null ? "" : value; }
    }

    private static int parseInt(String value, int def) {
        try { return Integer.parseInt(value == null ? "" : value.trim()); } catch (Throwable t) { return def; }
    }

    private static void closeQuietly(Socket socket) {
        try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
    }

    private void log(String message) {
        Listener l = listener;
        if (l != null) {
            try { l.onLog(message); } catch (Throwable ignored) {}
        }
    }

    private static final class Request {
        String method;
        String path;
        Map<String, String> headers;
        String body;
        String remote;
    }

    private static final class Response {
        final int status;
        final String contentType;
        final String body;

        private Response(int status, String contentType, String body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }

        static Response json(int status, String body) {
            return new Response(status, "application/json; charset=utf-8", body == null ? "{}" : body);
        }

        static Response text(int status, String body) {
            return new Response(status, "text/plain; charset=utf-8", body == null ? "" : body);
        }
    }
}
