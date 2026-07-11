package dev.perms.test.network.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.perms.test.network.web.PermsTestWebInterface;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * Small HTTP/HTTPS runtime for the Network tab.
 *
 * This intentionally keeps the server self-contained and dependency-free while
 * following the same raw-socket model used by the android-http-server reference.
 * Higher-level PermsTest actions are exposed only through the narrow
 * Web Interface runtime supplied by the Network controller.
 */
public final class PermsTestHttpServer {
    private static final int SOCKET_TIMEOUT_MS = 15000;
    private static final int MAX_BODY_BYTES = 256 * 1024;

    private final Object lock = new Object();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService clientExecutor;
    private volatile boolean running;
    private volatile int port;
    private volatile boolean tls;
    private volatile File rootDirectory;
    private volatile boolean directoryListingEnabled;
    private volatile boolean webInterfaceEnabled;
    private volatile String webInterfaceToken;
    private volatile Listener listener;
    private volatile PermsTestWebInterface webInterface;

    public void start(Config config, Listener listener, PermsTestWebInterface webInterface) throws IOException {
        if (config == null) throw new IOException("Missing HTTP server config.");
        synchronized (lock) {
            stopLocked();
            this.port = config.port;
            this.tls = config.tls;
            this.rootDirectory = config.rootDirectory;
            this.directoryListingEnabled = config.directoryListingEnabled;
            this.webInterfaceEnabled = config.webInterfaceEnabled;
            this.webInterfaceToken = config.webInterfaceToken == null ? "" : config.webInterfaceToken.trim();
            this.listener = listener;
            this.webInterface = webInterface;
            if (rootDirectory == null) throw new IOException("Missing HTTP root directory.");
            if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
                throw new IOException("Unable to create HTTP root: " + rootDirectory.getAbsolutePath());
            }
            if (!rootDirectory.isDirectory()) throw new IOException("HTTP root is not a directory: " + rootDirectory.getAbsolutePath());
            this.serverSocket = createServerSocket(config);
            this.clientExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "PermsTestHttpClient");
                t.setDaemon(true);
                return t;
            });
            this.running = true;
            this.acceptThread = new Thread(this::acceptLoop, "PermsTestHttpServer");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
            log((tls ? "HTTPS" : "HTTP") + " server started on port " + port + "; root=" + rootDirectory.getAbsolutePath()
                    + (webInterfaceEnabled ? "; web-interface=on" : ""));
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

    public boolean isTls() {
        return tls;
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public boolean isWebInterfaceEnabled() {
        return webInterfaceEnabled;
    }

    private ServerSocket createServerSocket(Config config) throws IOException {
        if (!config.tls) return new ServerSocket(config.port);
        File keyStoreFile = config.tlsKeyStoreFile;
        if (keyStoreFile == null || !keyStoreFile.isFile()) {
            throw new IOException("HTTPS requires a readable PKCS12 keystore file.");
        }
        char[] password = config.tlsKeyStorePassword == null ? new char[0] : config.tlsKeyStorePassword.toCharArray();
        try (FileInputStream in = new FileInputStream(keyStoreFile)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(in, password);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, password);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            return factory.createServerSocket(config.port);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            IOException io = new IOException("Unable to initialize HTTPS: " + e.getMessage());
            io.initCause(e);
            throw io;
        }
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
                ServerSocket currentSocket = serverSocket;
                if (currentSocket == null) break;
                Socket socket = currentSocket.accept();
                ExecutorService executor = clientExecutor;
                if (executor != null) {
                    executor.execute(() -> handleSocket(socket));
                } else {
                    closeQuietly(socket);
                }
            } catch (IOException e) {
                if (running) log("Accept failed: " + e.getMessage());
            } catch (Throwable e) {
                if (running) log("Accept crashed: " + e.getMessage());
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
            Response response = dispatch(request);
            writeResponse(out, request.method, response);
        } catch (Throwable e) {
            log("Client failed: " + e.getMessage());
            closeQuietly(socket);
        }
    }

    private Request readRequest(BufferedInputStream in) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.length() == 0) return null;
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return null;
        Request request = new Request();
        request.method = parts[0].trim().toUpperCase(Locale.US);
        String rawTarget = parts[1].trim();
        int queryIndex = rawTarget.indexOf('?');
        request.path = decodePath(queryIndex >= 0 ? rawTarget.substring(0, queryIndex) : rawTarget);
        request.query = queryIndex >= 0 ? rawTarget.substring(queryIndex + 1) : "";
        request.headers = new HashMap<>();
        String line;
        while ((line = readLine(in)) != null) {
            if (line.length() == 0) break;
            int colon = line.indexOf(':');
            if (colon > 0) {
                request.headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US), line.substring(colon + 1).trim());
            }
        }
        int contentLength = parseInt(request.headers.get("content-length"), 0);
        if (contentLength > 0) {
            if (contentLength > MAX_BODY_BYTES) throw new IOException("Request body too large.");
            byte[] body = new byte[contentLength];
            int offset = 0;
            while (offset < contentLength) {
                int read = in.read(body, offset, contentLength - offset);
                if (read < 0) break;
                offset += read;
            }
            request.body = new String(body, 0, offset, StandardCharsets.UTF_8);
        } else {
            request.body = "";
        }
        return request;
    }

    private Response dispatch(Request request) {
        try {
            if (!"GET".equals(request.method) && !"HEAD".equals(request.method) && !"POST".equals(request.method)) {
                return text(405, "Method Not Allowed", "Method not allowed\n");
            }
            if (webInterfaceEnabled && (request.path.equals("/permstest") || request.path.equals("/permstest/") || request.path.startsWith("/permstest/") || request.path.startsWith("/api/"))) {
                return dispatchWebInterface(request);
            }
            return dispatchStatic(request);
        } catch (Throwable e) {
            return text(500, "Internal Server Error", "Internal server error: " + safe(e.getMessage()) + "\n");
        }
    }

    private Response dispatchWebInterface(Request request) {
        PermsTestWebInterface web = webInterface;
        if (web == null) return json(503, "Service Unavailable", "{\"ok\":false,\"error\":\"web interface unavailable\"}");
        if (request.path.equals("/permstest") || request.path.equals("/permstest/")) {
            return html(200, "OK", web.buildPageHtml());
        }
        PermsTestWebInterface.ApiResult result = web.handleApi(request.method, request.path, request.query, request.headers);
        return json(result.statusCode, result.statusText, result.bodyJson);
    }

    private Response dispatchStatic(Request request) throws IOException {
        File root = rootDirectory;
        if (root == null) return text(500, "Internal Server Error", "No root configured\n");
        String relativePath = request.path == null ? "/" : request.path;
        if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
        File target = relativePath.length() == 0 ? root : new File(root, relativePath);
        File canonicalRoot = root.getCanonicalFile();
        File canonicalTarget = target.getCanonicalFile();
        if (!isInside(canonicalRoot, canonicalTarget)) {
            return text(403, "Forbidden", "Forbidden\n");
        }
        if (canonicalTarget.isDirectory()) {
            if (directoryListingEnabled) return html(200, "OK", buildDirectoryListing(canonicalRoot, canonicalTarget, request.path));
            File index = new File(canonicalTarget, "index.html").getCanonicalFile();
            if (index.isFile() && isInside(canonicalRoot, index)) return file(index);
            return text(403, "Forbidden", "Directory listing disabled\n");
        }
        if (!canonicalTarget.isFile()) return text(404, "Not Found", "Not found\n");
        return file(canonicalTarget);
    }

    private static boolean isInside(File root, File target) throws IOException {
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        return targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator);
    }

    private static String readLine(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(128);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') out.write(b);
            if (out.size() > 8192) throw new IOException("HTTP header line too long.");
        }
        if (b == -1 && out.size() == 0) return null;
        return out.toString("ISO-8859-1");
    }

    private static String decodePath(String value) {
        String decoded = urlDecode(value == null ? "/" : value);
        if (decoded.length() == 0 || decoded.charAt(0) != '/') decoded = "/" + decoded;
        return decoded;
    }

    private static String urlDecode(String value) {
        try { return URLDecoder.decode(value == null ? "" : value, "UTF-8"); } catch (Throwable ignored) { return value == null ? "" : value; }
    }

    private static Response file(File file) throws IOException {
        Response response = new Response();
        response.statusCode = 200;
        response.statusText = "OK";
        response.contentType = mimeType(file.getName());
        response.file = file;
        response.contentLength = file.length();
        return response;
    }

    private static Response text(int status, String statusText, String text) {
        Response response = new Response();
        response.statusCode = status;
        response.statusText = statusText;
        response.contentType = "text/plain; charset=utf-8";
        response.body = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        response.contentLength = response.body.length;
        return response;
    }

    private static Response html(int status, String statusText, String html) {
        Response response = text(status, statusText, html);
        response.contentType = "text/html; charset=utf-8";
        return response;
    }

    private static Response json(int status, String statusText, String json) {
        Response response = text(status, statusText, json == null ? "{}" : json);
        response.contentType = "application/json; charset=utf-8";
        return response;
    }

    private void writeResponse(OutputStream out, String method, Response response) throws IOException {
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 ").append(response.statusCode).append(' ').append(response.statusText).append("\r\n");
        header.append("Date: ").append(httpDate()).append("\r\n");
        header.append("Server: PermsTestHttpServer\r\n");
        header.append("Connection: close\r\n");
        header.append("Content-Type: ").append(response.contentType == null ? "application/octet-stream" : response.contentType).append("\r\n");
        header.append("Content-Length: ").append(Math.max(0L, response.contentLength)).append("\r\n");
        header.append("X-Content-Type-Options: nosniff\r\n");
        header.append("\r\n");
        out.write(header.toString().getBytes(StandardCharsets.ISO_8859_1));
        if ("HEAD".equals(method)) {
            out.flush();
            return;
        }
        if (response.file != null) {
            try (FileInputStream fileIn = new FileInputStream(response.file)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = fileIn.read(buffer)) != -1) out.write(buffer, 0, read);
            }
        } else if (response.body != null) {
            out.write(response.body);
        }
        out.flush();
    }

    private static String buildDirectoryListing(File root, File directory, String requestPath) {
        StringBuilder out = new StringBuilder();
        out.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Index of ")
                .append(htmlEscape(requestPath)).append("</title></head><body><h3>Index of ")
                .append(htmlEscape(requestPath)).append("</h3><ul>");
        try {
            if (!directory.equals(root)) out.append("<li><a href=\"../\">../</a></li>");
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName() + (file.isDirectory() ? "/" : "");
                    out.append("<li><a href=\"").append(htmlEscape(name)).append("\">")
                            .append(htmlEscape(name)).append("</a></li>");
                }
            }
        } catch (Throwable ignored) {
        }
        out.append("</ul></body></html>");
        return out.toString();
    }

    private static String mimeType(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".txt") || lower.endsWith(".log")) return "text/plain; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    private static String httpDate() {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date());
    }

    private static int parseInt(String text, int fallback) {
        try { return Integer.parseInt(text == null ? "" : text.trim()); } catch (Throwable ignored) { return fallback; }
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String htmlEscape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void log(String message) {
        Listener l = listener;
        if (l != null) l.onHttpLog(message == null ? "" : message);
    }

    private static void closeQuietly(Socket socket) {
        try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
    }

    private static final class Request {
        String method;
        String path;
        String query;
        Map<String, String> headers;
        String body;
    }

    private static final class Response {
        int statusCode;
        String statusText;
        String contentType;
        byte[] body;
        File file;
        long contentLength;
    }

    public static final class Config {
        public int port;
        public File rootDirectory;
        public boolean tls;
        public File tlsKeyStoreFile;
        public String tlsKeyStorePassword;
        public boolean directoryListingEnabled;
        public boolean webInterfaceEnabled;
        public String webInterfaceToken;
    }

    public interface Listener {
        void onHttpLog(String message);
    }

}
