package dev.perms.test.network.http;

import dev.perms.test.network.NetworkActivityDependencies;
import dev.perms.test.network.NetworkAddressFormatter;
import dev.perms.test.network.NetworkControllerBase;
import dev.perms.test.network.NetworkPreferenceKeys;
import dev.perms.test.network.ftp.*;

import android.app.Activity;
import android.database.Cursor;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.databinding.TabNetworkBinding;
import dev.perms.test.editor.SourceSyntaxHighlighter;
import dev.perms.test.network.web.PermsTestWebInterface;
import dev.perms.test.network.web.PermsTestWebMemoryApi;

/**
 * Activity-side HTTP server and Web Interface controller for the Network tab.
 *
 * The raw socket HTTP implementation lives in network.http. This controller owns
 * UI binding, preferences, default index.html editing, HTTP root import actions,
 * and the narrow bridge used by the Web Interface package.
 */
public final class NetworkHttpServerController extends NetworkControllerBase implements PermsTestHttpServer.Listener {
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_ROOT = "/storage/emulated/0/dev.perms.test/http";
    private static final String DEFAULT_INDEX = "<!doctype html>\n"
            + "<html>\n"
            + "<head>\n"
            + "  <meta charset=\"utf-8\">\n"
            + "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
            + "  <title>PermsTest HTTP Server</title>\n"
            + "  <style>\n"
            + "    body { font-family: system-ui, Arial, sans-serif; margin: 2rem; background: #111; color: #eee; }\n"
            + "    code { background: #222; padding: .15rem .35rem; border-radius: .25rem; }\n"
            + "    .card { border: 1px solid #444; border-radius: .75rem; padding: 1rem; max-width: 760px; }\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <div class=\"card\">\n"
            + "    <h1>PermsTest HTTP Server</h1>\n"
            + "    <p>This default page is stored as <code>index.html</code> in the configured HTTP root.</p>\n"
            + "    <p>Edit this HTML/CSS/JS directly from the Network tab.</p>\n"
            + "    <script>console.log('PermsTest HTTP server ready');</script>\n"
            + "  </div>\n"
            + "</body>\n"
            + "</html>\n";

    private final PermsTestHttpServer httpServer = new PermsTestHttpServer();
    private final NetworkFtpServerController ftpServerController;
    private ActivityResultLauncher<Intent> pickHttpFileLauncher;
    private ActivityResultLauncher<Intent> pickHttpFolderLauncher;
    private boolean httpIndexSyntaxWatcherAttached;
    private boolean httpIndexSyntaxApplying;
    private Runnable httpIndexSyntaxRunnable;

    public NetworkHttpServerController(NetworkActivityDependencies dependencies,
                                       NetworkFtpServerController ftpServerController) {
        super(dependencies);
        this.ftpServerController = ftpServerController;
    }

    public void bind() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        debug("http-bind", "binding HTTP/Web controls; running=" + httpServer.isRunning());
        ensureHttpImportPickers();
        loadState(network);
        bindHttpControls(network);
        bindWebInterfaceControls(network);
        setupHttpIndexEditorScrolling(network);
        setupHttpIndexSyntaxHighlighting(network);
        updateHttpUi();
    }

    public void refreshVisibleState() {
        updateHttpUi();
    }

    public void shutdown() {
        debug("http-lifecycle", "shutdown requested; activityRunning=" + httpServer.isRunning()
                + ", backgroundRunning=" + PermsTestHttpService.snapshot().running);
        httpServer.stop();
    }

    @Override
    public void onHttpLog(String message) {
        Handler handler = getMainHandler();
        Runnable action = () -> {
            if (!TextUtils.isEmpty(message)) appendOutput("[HTTP] " + message + "\n");
            updateHttpUi();
        };
        if (handler != null) handler.post(action); else action.run();
    }

    private void bindHttpControls(TabNetworkBinding network) {
        network.btnHttpStart.setOnClickListener(v -> startHttpServer());
        network.btnHttpStop.setOnClickListener(v -> stopHttpServer());
        network.btnHttpCopyUrl.setOnClickListener(v -> copyHttpUrl(false));
        network.btnHttpCopyFileToRoot.setOnClickListener(v -> launchCopyHttpFile());
        network.btnHttpCopyFolderToRoot.setOnClickListener(v -> launchCopyHttpFolder());
        network.btnHttpLoadIndex.setOnClickListener(v -> loadIndexIntoEditor(true));
        network.btnHttpSaveIndex.setOnClickListener(v -> saveIndexFromEditor());
        network.btnHttpResetIndex.setOnClickListener(v -> resetIndexEditor());
        network.chkHttpUseHttps.setOnCheckedChangeListener((button, checked) -> {
            saveBoolean(NetworkPreferenceKeys.HTTP_USE_HTTPS, checked);
            updateTlsFieldState();
        });
        network.chkHttpDirectoryListing.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.HTTP_DIRECTORY_LISTING, checked));
        network.chkHttpKeepAliveSleep.setOnCheckedChangeListener((button, checked) -> {
            saveBoolean(NetworkPreferenceKeys.HTTP_KEEP_ALIVE_SLEEP, checked);
            updateHttpUi();
        });
        network.chkHttpBackgroundUse.setOnCheckedChangeListener((button, checked) -> {
            saveBoolean(NetworkPreferenceKeys.HTTP_BACKGROUND_USE, checked);
            updateHttpUi();
        });
    }

    private void setupHttpIndexEditorScrolling(TabNetworkBinding network) {
        try {
            if (network == null || network.edtHttpIndexEditor == null) return;
            network.edtHttpIndexEditor.setVerticalScrollBarEnabled(false);
            try { ViewCompat.setNestedScrollingEnabled(network.edtHttpIndexEditor, true); } catch (Throwable ignored) {}

            final float[] lastTouchY = new float[1];
            network.edtHttpIndexEditor.setOnTouchListener((v, ev) -> {
                try {
                    if (ev == null) return false;
                    final ViewParent parent = network.getRoot();
                    final int action = ev.getActionMasked();
                    if (parent != null) {
                        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            parent.requestDisallowInterceptTouchEvent(false);
                        } else if (action == MotionEvent.ACTION_DOWN) {
                            lastTouchY[0] = ev.getY();
                            parent.requestDisallowInterceptTouchEvent(true);
                        } else if (action == MotionEvent.ACTION_MOVE) {
                            float y = ev.getY();
                            float dy = y - lastTouchY[0];
                            lastTouchY[0] = y;
                            int dir = (dy > 0f) ? -1 : 1;
                            parent.requestDisallowInterceptTouchEvent(v.canScrollVertically(dir));
                        }
                    }
                } catch (Throwable ignored) {
                }
                return false;
            });
        } catch (Throwable ignored) {
        }
    }

    private void setupHttpIndexSyntaxHighlighting(TabNetworkBinding network) {
        try {
            if (httpIndexSyntaxWatcherAttached) return;
            if (network == null || network.edtHttpIndexEditor == null) return;
            httpIndexSyntaxWatcherAttached = true;
            network.edtHttpIndexEditor.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (httpIndexSyntaxApplying) return;
                    scheduleHttpIndexSyntaxHighlight(false);
                }
            });
            scheduleHttpIndexSyntaxHighlight(true);
        } catch (Throwable ignored) {}
    }

    private void scheduleHttpIndexSyntaxHighlight(boolean immediate) {
        try {
            Handler handler = getMainHandler();
            if (handler == null) return;
            if (httpIndexSyntaxRunnable != null) handler.removeCallbacks(httpIndexSyntaxRunnable);
            httpIndexSyntaxRunnable = this::applyHttpIndexSyntaxHighlight;
            if (immediate) handler.post(httpIndexSyntaxRunnable);
            else handler.postDelayed(httpIndexSyntaxRunnable, 350L);
        } catch (Throwable ignored) {}
    }

    private void applyHttpIndexSyntaxHighlight() {
        if (httpIndexSyntaxApplying) return;
        try {
            TabNetworkBinding network = getNetworkBinding();
            if (network == null || network.edtHttpIndexEditor == null) return;
            httpIndexSyntaxApplying = true;
            SourceSyntaxHighlighter.applyWeb(network.edtHttpIndexEditor);
        } catch (Throwable ignored) {
        } finally {
            httpIndexSyntaxApplying = false;
        }
    }

    private void bindWebInterfaceControls(TabNetworkBinding network) {
        network.chkWebInterfaceEnabled.setOnCheckedChangeListener((button, checked) -> {
            saveBoolean(NetworkPreferenceKeys.WEB_INTERFACE_ENABLED, checked);
            updateHttpUi();
        });
        network.chkWebInterfaceRequireToken.setOnCheckedChangeListener((button, checked) -> {
            saveBoolean(NetworkPreferenceKeys.WEB_INTERFACE_REQUIRE_TOKEN, checked);
            updateHttpUi();
        });
        bindWebAccessCheckboxes(network);
        network.btnWebInterfaceCopyUrl.setOnClickListener(v -> copyHttpUrl(true));
        network.btnWebInterfaceOpen.setOnClickListener(v -> openWebInterface());
    }

    private void bindWebAccessCheckboxes(TabNetworkBinding network) {
        network.chkWebAccessGlobal.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_GLOBAL, checked));
        network.chkWebAccessMain.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_MAIN, checked));
        network.chkWebAccessShell.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_SHELL, checked));
        network.chkWebAccessPackages.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_PACKAGES, checked));
        network.chkWebAccessMemory.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_MEMORY, checked));
        network.chkWebAccessFiles.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_FILES, checked));
        network.chkWebAccessNetwork.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_NETWORK, checked));
        network.chkWebAccessScripts.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_SCRIPTS, checked));
        network.chkWebAccessDebugging.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_DEBUGGING, checked));
        network.chkWebAccessTools.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_TOOLS, checked));
        network.chkWebAccessLogging.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_LOGGING, checked));
        network.chkWebAccessSettings.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_SETTINGS, checked));
        network.chkWebAccessAbout.setOnCheckedChangeListener((button, checked) -> saveBoolean(NetworkPreferenceKeys.WEB_ACCESS_ABOUT, checked));
    }

    private void startHttpServer() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        try {
            int port = parsePort(text(network.edtHttpPort, String.valueOf(DEFAULT_PORT)), DEFAULT_PORT);
            String root = normalizeRoot(text(network.edtHttpRoot, DEFAULT_ROOT));
            boolean tls = network.chkHttpUseHttps.isChecked();
            boolean directoryListing = network.chkHttpDirectoryListing.isChecked();
            boolean webInterfaceEnabled = network.chkWebInterfaceEnabled.isChecked();
            boolean requireToken = network.chkWebInterfaceRequireToken.isChecked();
            boolean keepAliveSleep = network.chkHttpKeepAliveSleep.isChecked();
            boolean backgroundUse = network.chkHttpBackgroundUse.isChecked();
            String token = requireToken ? text(network.edtWebInterfaceToken, "") : "";
            String keyStorePath = text(network.edtHttpTlsKeystore, "");
            String keyStorePassword = text(network.edtHttpTlsPassword, "");

            File rootDir = new File(root);
            debug("http-start", "requested port=" + port + ", tls=" + tls + ", directoryListing=" + directoryListing
                    + ", webInterface=" + webInterfaceEnabled + ", requireToken=" + requireToken
                    + ", background=" + backgroundUse + ", sleepKeepAlive=" + keepAliveSleep
                    + ", root=" + rootDir.getAbsolutePath());
            if (!ensureHttpStorageAccess(rootDir, "start")) {
                updateHttpUi();
                return;
            }
            ensureRootAndIndex(rootDir);
            network.edtHttpRoot.setText(rootDir.getAbsolutePath());
            saveState(network, port, rootDir.getAbsolutePath(), tls, directoryListing, webInterfaceEnabled, requireToken,
                    keepAliveSleep, backgroundUse, token, keyStorePath, keyStorePassword);

            if (backgroundUse || keepAliveSleep) {
                startBackgroundService(port, rootDir.getAbsolutePath(), tls, directoryListing, webInterfaceEnabled, token,
                        keyStorePath, keyStorePassword, keepAliveSleep);
                updateHttpUi();
                return;
            }

            PermsTestHttpServer.Config config = new PermsTestHttpServer.Config();
            config.port = port;
            config.rootDirectory = rootDir;
            config.tls = tls;
            config.tlsKeyStoreFile = TextUtils.isEmpty(keyStorePath) ? null : new File(keyStorePath);
            config.tlsKeyStorePassword = keyStorePassword;
            config.directoryListingEnabled = directoryListing;
            config.webInterfaceEnabled = webInterfaceEnabled;
            config.webInterfaceToken = token;

            httpServer.start(config, this, createWebInterface(token));
            debug("http-start", "runtime start dispatched; activePort=" + httpServer.getPort()
                    + ", activeTls=" + httpServer.isTls() + ", activeRoot="
                    + (httpServer.getRootDirectory() == null ? "" : httpServer.getRootDirectory().getAbsolutePath()));
            appendOutput("[HTTP] " + (tls ? "HTTPS" : "HTTP") + " server starting on port " + port + "; root=" + rootDir.getAbsolutePath()
                    + (webInterfaceEnabled ? "; web-interface=/permstest" : "") + "\n");
            updateHttpUi();
        } catch (Throwable e) {
            debugWarn("http-start", "failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Toast.makeText(getActivity(), "HTTP start failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            appendOutput("[HTTP] Start failed: " + e.getMessage() + "\n");
            updateHttpUi();
        }
    }

    private void startBackgroundService(int port,
                                        String root,
                                        boolean tls,
                                        boolean directoryListing,
                                        boolean webInterfaceEnabled,
                                        String token,
                                        String keyStorePath,
                                        String keyStorePassword,
                                        boolean keepAliveSleep) {
        try { httpServer.stop(); } catch (Throwable ignored) {}
        PermsTestHttpService.markStartRequested(port, root, tls, directoryListing, webInterfaceEnabled, keepAliveSleep);
        Activity activity = getActivity();
        if (activity != null) {
            Intent intent = new Intent(activity, PermsTestHttpService.class)
                    .setAction(PermsTestHttpService.ACTION_START)
                    .putExtra(PermsTestHttpService.EXTRA_PORT, port)
                    .putExtra(PermsTestHttpService.EXTRA_ROOT, root)
                    .putExtra(PermsTestHttpService.EXTRA_TLS, tls)
                    .putExtra(PermsTestHttpService.EXTRA_DIRECTORY_LISTING, directoryListing)
                    .putExtra(PermsTestHttpService.EXTRA_WEB_INTERFACE, webInterfaceEnabled)
                    .putExtra(PermsTestHttpService.EXTRA_WEB_TOKEN, token == null ? "" : token)
                    .putExtra(PermsTestHttpService.EXTRA_TLS_KEYSTORE, keyStorePath == null ? "" : keyStorePath)
                    .putExtra(PermsTestHttpService.EXTRA_TLS_PASSWORD, keyStorePassword == null ? "" : keyStorePassword)
                    .putExtra(PermsTestHttpService.EXTRA_KEEP_ALIVE_SLEEP, keepAliveSleep)
                    .putExtra(PermsTestHttpService.EXTRA_DEBUG, dependencies != null && dependencies.isDebugOutputEnabled());
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(intent); else activity.startService(intent);
        }
        appendOutput("[HTTP] Background " + (tls ? "HTTPS" : "HTTP") + " service starting on port " + port + "; root=" + root
                + (webInterfaceEnabled ? "; web-interface=/permstest" : "")
                + (keepAliveSleep ? "; sleep-keepalive=on" : "") + "\n");
        debug("http-start", "background service dispatched port=" + port + ", tls=" + tls
                + ", directoryListing=" + directoryListing + ", webInterface=" + webInterfaceEnabled
                + ", keepAliveSleep=" + keepAliveSleep + ", root=" + root);
        Handler handler = getMainHandler();
        if (handler != null) {
            handler.postDelayed(this::updateHttpUi, 600L);
            handler.postDelayed(this::updateHttpUi, 1800L);
        }
    }

    private void stopHttpServer() {
        try {
            debug("http-stop", "stop requested; activityRunning=" + httpServer.isRunning()
                    + ", serviceRunning=" + PermsTestHttpService.snapshot().running);
            httpServer.stop();
            PermsTestHttpService.markStopRequested();
            Activity activity = getActivity();
            if (activity != null) {
                try { activity.startService(new Intent(activity, PermsTestHttpService.class).setAction(PermsTestHttpService.ACTION_STOP)); } catch (Throwable ignored) {}
                try { activity.stopService(new Intent(activity, PermsTestHttpService.class)); } catch (Throwable ignored) {}
            }
            appendOutput("[HTTP] Server stopped\n");
        } catch (Throwable ignored) {
        }
        updateHttpUi();
    }

    private PermsTestWebInterface createWebInterface(String token) {
        return new PermsTestWebInterface(new PermsTestWebInterface.Bridge() {
            @Override
            public String statusJson() {
                return buildStatusJson();
            }

            @Override
            public String outputText() {
                return currentOutputText();
            }

            @Override
            public String accessJson() {
                return webAccessJson();
            }

            @Override
            public boolean isWebSectionEnabled(String section) {
                return NetworkHttpServerController.this.isWebSectionEnabled(section);
            }

            @Override
            public String memoryApiJson(String path, String query) {
                debug("web-memory", "memory api path=" + path + ", query=" + query);
                return PermsTestWebMemoryApi.actionJson(getActivity(), getPreferences(), path, query);
            }

            @Override
            public boolean isFtpRunning() {
                return ftpServerController != null && ftpServerController.isServerRunning();
            }

            @Override
            public void startFtp() {
                debug("web-action", "startFtp requested from web interface");
                postToMain(() -> {
                    if (ftpServerController != null && ftpServerController.isServerRunning()) {
                        appendOutput("[Web] FTP is already running; start ignored.\n");
                        refreshServerUiSoon();
                        return;
                    }
                    appendOutput("[Web] Start FTP requested\n");
                    if (ftpServerController != null) ftpServerController.startServer();
                    refreshServerUiSoon();
                });
            }

            @Override
            public void stopFtp() {
                debug("web-action", "stopFtp requested from web interface");
                postToMain(() -> {
                    appendOutput("[Web] Stop FTP requested\n");
                    if (ftpServerController != null) ftpServerController.stopServer();
                    refreshServerUiSoon();
                });
            }
        }, token);
    }

    private void refreshServerUiSoon() {
        try {
            if (ftpServerController != null) ftpServerController.refreshVisibleState();
            updateHttpUi();
        } catch (Throwable ignored) {
        }
        Handler handler = getMainHandler();
        if (handler != null) {
            handler.postDelayed(() -> {
                try {
                    if (ftpServerController != null) ftpServerController.refreshVisibleState();
                    updateHttpUi();
                } catch (Throwable ignored) {
                }
            }, 500L);
            handler.postDelayed(() -> {
                try {
                    if (ftpServerController != null) ftpServerController.refreshVisibleState();
                    updateHttpUi();
                } catch (Throwable ignored) {
                }
            }, 1500L);
        }
    }

    private String webAccessJson() {
        return PermsTestWebMemoryApi.accessJson(webAccessMap());
    }

    private Map<String, Boolean> webAccessMap() {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        map.put("global", isWebSectionEnabled("global"));
        map.put("main", isWebSectionEnabled("main"));
        map.put("shell", isWebSectionEnabled("shell"));
        map.put("packages", isWebSectionEnabled("packages"));
        map.put("memory", isWebSectionEnabled("memory"));
        map.put("files", isWebSectionEnabled("files"));
        map.put("network", isWebSectionEnabled("network"));
        map.put("scripts", isWebSectionEnabled("scripts"));
        map.put("debugging", isWebSectionEnabled("debugging"));
        map.put("tools", isWebSectionEnabled("tools"));
        map.put("logging", isWebSectionEnabled("logging"));
        map.put("settings", isWebSectionEnabled("settings"));
        map.put("about", isWebSectionEnabled("about"));
        return map;
    }

    private boolean isWebSectionEnabled(String section) {
        SharedPreferences prefs = getPreferences();
        if (TextUtils.isEmpty(section)) return false;
        switch (section.trim().toLowerCase(Locale.US)) {
            case "global": return prefs == null || prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_GLOBAL, true);
            case "main": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_MAIN, false);
            case "shell": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_SHELL, false);
            case "packages": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_PACKAGES, false);
            case "memory": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_MEMORY, false);
            case "files": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_FILES, false);
            case "network": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_NETWORK, false);
            case "scripts": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_SCRIPTS, false);
            case "debugging": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_DEBUGGING, false);
            case "tools": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_TOOLS, false);
            case "logging": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_LOGGING, false);
            case "settings": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_SETTINGS, false);
            case "about": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_ABOUT, false);
            default: return false;
        }
    }

    private String buildStatusJson() {
        NetworkAddressFormatter.Status address = NetworkAddressFormatter.currentStatus();
        boolean ftpRunning = ftpServerController != null && ftpServerController.isServerRunning();
        PermsTestHttpService.Status svc = PermsTestHttpService.snapshot();
        boolean serviceActive = svc.running || svc.starting;
        String root = serviceActive ? svc.root : (httpServer.getRootDirectory() == null ? "" : httpServer.getRootDirectory().getAbsolutePath());
        boolean running = serviceActive || httpServer.isRunning();
        boolean tls = serviceActive ? svc.tls : httpServer.isTls();
        int port = serviceActive ? svc.port : httpServer.getPort();
        boolean webInterface = serviceActive ? svc.webInterface : httpServer.isWebInterfaceEnabled();
        return "{"
                + "\"ok\":true,"
                + "\"network\":{\"connected\":" + address.connected + ",\"ipv4\":\"" + jsonEscape(address.firstIpv4) + "\",\"text\":\"" + jsonEscape(address.text) + "\"},"
                + "\"ftp\":{\"running\":" + ftpRunning + "},"
                + "\"http\":{\"running\":" + running + ",\"tls\":" + tls + ",\"port\":" + port + ",\"root\":\"" + jsonEscape(root) + "\",\"webInterface\":" + webInterface + ",\"background\":" + serviceActive + ",\"sleepKeepAlive\":" + svc.keepAliveSleep + "}"
                + "}";
    }

    private String currentOutputText() {
        try {
            ActivityMainBinding binding = getBinding();
            if (binding != null && binding.txtOutput != null && binding.txtOutput.getText() != null) {
                String text = binding.txtOutput.getText().toString();
                int max = 12000;
                return text.length() > max ? text.substring(text.length() - max) : text;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }


    public void registerActivityResults() {
        ensureHttpImportPickers();
    }

    private void ensureHttpImportPickers() {
        if (pickHttpFileLauncher != null && pickHttpFolderLauncher != null) return;
        Activity activity = getActivity();
        if (!(activity instanceof ComponentActivity)) return;
        ComponentActivity componentActivity = (ComponentActivity) activity;
        if (pickHttpFileLauncher == null) {
            pickHttpFileLauncher = componentActivity.registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::handlePickedHttpFile);
        }
        if (pickHttpFolderLauncher == null) {
            pickHttpFolderLauncher = componentActivity.registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::handlePickedHttpFolder);
        }
    }

    private void launchCopyHttpFile() {
        try {
            ensureHttpImportPickers();
            if (pickHttpFileLauncher == null) {
                Toast.makeText(getActivity(), "File picker unavailable", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            debug("http-import", "launching file picker for HTTP root import");
            pickHttpFileLauncher.launch(intent);
        } catch (Throwable e) {
            debugWarn("http-import", "file picker failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Toast.makeText(getActivity(), "File picker failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void launchCopyHttpFolder() {
        try {
            ensureHttpImportPickers();
            if (pickHttpFolderLauncher == null) {
                Toast.makeText(getActivity(), "Folder picker unavailable", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            debug("http-import", "launching folder picker for HTTP root import");
            pickHttpFolderLauncher.launch(intent);
        } catch (Throwable e) {
            debugWarn("http-import", "folder picker failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Toast.makeText(getActivity(), "Folder picker failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handlePickedHttpFile(ActivityResult result) {
        try {
            if (result == null || result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
            Intent data = result.getData();
            Uri uri = data.getData();
            if (uri == null) return;
            takeReadPermission(data, uri);
            File root = resolveHttpRoot(true);
            String displayName = safeFileName(queryDisplayName(uri));
            if (TextUtils.isEmpty(displayName)) displayName = "http_file";
            File destination = new File(root, displayName);
            debug("http-import", "picked file uri=" + uri + ", displayName=" + displayName + ", destination=" + destination.getAbsolutePath());
            appendOutput("[HTTP] Copying file into web root: " + displayName + "\n");
            runHttpImportTask(() -> {
                debug("http-import", "copy file begin destination=" + destination.getAbsolutePath());
                copyUriToFile(uri, destination);
                postToMain(() -> {
                    debug("http-import", "copy file complete destination=" + destination.getAbsolutePath()
                            + ", bytes=" + destination.length());
                    appendOutput("[HTTP] Copied file to " + destination.getAbsolutePath() + "\n");
                    Toast.makeText(getActivity(), "Copied to HTTP root", Toast.LENGTH_SHORT).show();
                });
            });
        } catch (Throwable e) {
            debugWarn("http-import", "copy file failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            appendOutput("[HTTP] Copy file failed: " + e.getMessage() + "\n");
            Toast.makeText(getActivity(), "Copy file failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handlePickedHttpFolder(ActivityResult result) {
        try {
            if (result == null || result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
            Intent data = result.getData();
            Uri treeUri = data.getData();
            if (treeUri == null) return;
            takeReadPermission(data, treeUri);
            String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId);
            String folderName = safeFileName(queryDocumentName(documentUri));
            if (TextUtils.isEmpty(folderName)) folderName = "http_folder";
            File destination = new File(resolveHttpRoot(true), folderName);
            debug("http-import", "picked folder treeUri=" + treeUri + ", folderName=" + folderName + ", destination=" + destination.getAbsolutePath());
            appendOutput("[HTTP] Copying folder into web root: " + folderName + "\n");
            runHttpImportTask(() -> {
                debug("http-import", "copy folder begin destination=" + destination.getAbsolutePath());
                copyDocumentTree(treeUri, documentUri, destination);
                postToMain(() -> {
                    debug("http-import", "copy folder complete destination=" + destination.getAbsolutePath());
                    appendOutput("[HTTP] Copied folder to " + destination.getAbsolutePath() + "\n");
                    Toast.makeText(getActivity(), "Copied folder to HTTP root", Toast.LENGTH_SHORT).show();
                });
            });
        } catch (Throwable e) {
            debugWarn("http-import", "copy folder failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            appendOutput("[HTTP] Copy folder failed: " + e.getMessage() + "\n");
            Toast.makeText(getActivity(), "Copy folder failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void runHttpImportTask(ThrowingRunnable runnable) {
        java.util.concurrent.ExecutorService executor = getIoExecutor();
        Runnable wrapped = () -> {
            try {
                if (runnable != null) runnable.run();
            } catch (Throwable e) {
                debugWarn("http-import", "import worker failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                postToMain(() -> {
                    appendOutput("[HTTP] Import failed: " + e.getMessage() + "\n");
                    Toast.makeText(getActivity(), "HTTP import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        };
        if (executor != null) executor.execute(wrapped); else wrapped.run();
    }

    private File resolveHttpRoot(boolean createDefaultIndex) throws IOException {
        File root = httpServer.isRunning() && httpServer.getRootDirectory() != null
                ? httpServer.getRootDirectory()
                : new File(normalizeRoot(text(getNetworkBinding() == null ? null : getNetworkBinding().edtHttpRoot, DEFAULT_ROOT)));
        if (!ensureHttpStorageAccess(root, "import")) {
            throw new IOException("HTTP root needs All files access: " + root.getAbsolutePath());
        }
        if (!root.exists() && !root.mkdirs()) throw new IOException("Unable to create " + root.getAbsolutePath());
        if (!root.isDirectory()) throw new IOException("HTTP root is not a directory: " + root.getAbsolutePath());
        if (createDefaultIndex) ensureRootAndIndex(root);
        return root;
    }

    private void takeReadPermission(Intent data, Uri uri) {
        try {
            int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (takeFlags != 0) getActivity().getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Throwable ignored) {
        }
    }

    private void copyUriToFile(Uri uri, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create " + parent.getAbsolutePath());
        try (InputStream in = getActivity().getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(destination)) {
            if (in == null) throw new IOException("Unable to open source file");
            copyStream(in, out);
        }
    }

    private void copyDocumentTree(Uri treeUri, Uri documentUri, File destinationDirectory) throws IOException {
        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
            throw new IOException("Unable to create " + destinationDirectory.getAbsolutePath());
        }
        String documentId = DocumentsContract.getDocumentId(documentUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = getActivity().getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String childId = idColumn >= 0 ? cursor.getString(idColumn) : null;
                if (TextUtils.isEmpty(childId)) continue;
                String name = safeFileName(nameColumn >= 0 ? cursor.getString(nameColumn) : "");
                if (TextUtils.isEmpty(name)) name = "item";
                String mime = mimeColumn >= 0 ? cursor.getString(mimeColumn) : "";
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                File target = new File(destinationDirectory, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    copyDocumentTree(treeUri, childUri, target);
                } else {
                    copyUriToFile(childUri, target);
                }
            }
        }
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getActivity().getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Throwable ignored) {
        }
        String last = uri == null ? "" : uri.getLastPathSegment();
        return last == null ? "" : last;
    }

    private String queryDocumentName(Uri documentUri) {
        try (Cursor cursor = getActivity().getContentResolver().query(documentUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String safeFileName(String name) {
        if (name == null) return "";
        String cleaned = name.replace('\\', '_').replace('/', '_').replace(':', '_').trim();
        while (cleaned.startsWith(".")) cleaned = cleaned.substring(1);
        if (cleaned.length() > 120) cleaned = cleaned.substring(cleaned.length() - 120);
        return cleaned;
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
    }


    private boolean ensureHttpStorageAccess(File rootDir, String action) {
        if (!requiresAllFilesAccess(rootDir)) return true;
        if (NetworkFtpServerStorageAccess.hasAllFilesAccess(getActivity())) return true;
        String rootPath = rootDir == null ? DEFAULT_ROOT : rootDir.getAbsolutePath();
        debugWarn("http-storage", "blocked " + action + "; missing All files access for root=" + rootPath);
        NetworkFtpServerStorageAccess.showHttpStorageAccessDialog(getActivity(), rootPath, this::appendOutput);
        return false;
    }

    private boolean requiresAllFilesAccess(File rootDir) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        if (rootDir == null) return false;
        try {
            String root = canonicalPath(rootDir);
            if (TextUtils.isEmpty(root)) return false;
            if (isAppSpecificExternalPath(root)) return false;
            String external = canonicalPath(Environment.getExternalStorageDirectory());
            return !TextUtils.isEmpty(external) && (root.equals(external) || root.startsWith(external + File.separator));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isAppSpecificExternalPath(String path) {
        Activity activity = getActivity();
        if (activity == null || TextUtils.isEmpty(path)) return false;
        try {
            File files = activity.getExternalFilesDir(null);
            if (isSameOrChild(path, canonicalPath(files))) return true;
        } catch (Throwable ignored) {
        }
        try {
            File cache = activity.getExternalCacheDir();
            if (isSameOrChild(path, canonicalPath(cache))) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isSameOrChild(String path, String parent) {
        return !TextUtils.isEmpty(path) && !TextUtils.isEmpty(parent)
                && (path.equals(parent) || path.startsWith(parent + File.separator));
    }

    private static String canonicalPath(File file) {
        if (file == null) return "";
        try {
            return file.getCanonicalPath();
        } catch (Throwable ignored) {
            return file.getAbsolutePath();
        }
    }
    private void updateHttpUi() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        PermsTestHttpService.Status serviceStatus = PermsTestHttpService.snapshot();
        boolean serviceActive = serviceStatus.running || serviceStatus.starting;
        boolean running = httpServer.isRunning() || serviceActive;
        network.btnHttpStart.setEnabled(!running);
        network.btnHttpStop.setEnabled(running);
        network.btnHttpCopyUrl.setEnabled(true);
        network.btnHttpCopyFileToRoot.setEnabled(true);
        network.btnHttpCopyFolderToRoot.setEnabled(true);
        network.btnHttpLoadIndex.setEnabled(!running);
        network.btnHttpSaveIndex.setEnabled(!running);
        network.btnHttpResetIndex.setEnabled(!running);
        network.edtHttpPort.setEnabled(!running);
        network.edtHttpRoot.setEnabled(!running);
        network.chkHttpUseHttps.setEnabled(!running);
        network.chkHttpDirectoryListing.setEnabled(!running);
        network.chkHttpKeepAliveSleep.setEnabled(!running);
        network.chkHttpBackgroundUse.setEnabled(!running);
        network.chkWebInterfaceEnabled.setEnabled(!running);
        network.chkWebInterfaceRequireToken.setEnabled(!running);
        network.edtWebInterfaceToken.setEnabled(!running && network.chkWebInterfaceRequireToken.isChecked());
        updateTlsFieldState();

        String url = buildHttpUrl(false);
        if (serviceStatus.running) {
            network.txtHttpServerStatus.setText("Running in background: " + url
                    + "  •  Root: " + serviceStatus.root
                    + (serviceStatus.webInterface ? "  •  Web Interface" : "")
                    + (serviceStatus.keepAliveSleep ? "  •  sleep keep-alive" : ""));
            network.txtHttpServerStatus.setTextColor(Color.rgb(76, 175, 80));
        } else if (serviceStatus.starting) {
            network.txtHttpServerStatus.setText("Starting background HTTP service...");
            network.txtHttpServerStatus.setTextColor(Color.rgb(255, 193, 7));
        } else if (httpServer.isRunning()) {
            network.txtHttpServerStatus.setText((httpServer.isTls() ? "HTTPS" : "HTTP") + " server running: " + url
                    + "  •  Root: " + (httpServer.getRootDirectory() == null ? "" : httpServer.getRootDirectory().getAbsolutePath()));
            network.txtHttpServerStatus.setTextColor(Color.rgb(76, 175, 80));
        } else {
            String err = serviceStatus.lastError;
            network.txtHttpServerStatus.setText(TextUtils.isEmpty(err)
                    ? "HTTP server stopped. Default root is " + DEFAULT_ROOT + "."
                    : "HTTP server stopped. Last error: " + err);
            network.txtHttpServerStatus.setTextColor(TextUtils.isEmpty(err)
                    ? Color.rgb(189, 189, 189)
                    : Color.rgb(229, 57, 53));
        }

        String webUrl = buildHttpUrl(true);
        network.txtWebInterfaceStatus.setText(network.chkWebInterfaceEnabled.isChecked()
                ? (running ? "Web Interface: " + webUrl : "Web Interface will be served at /permstest when HTTP is running.")
                : "Web Interface disabled.");
        network.txtWebInterfaceStatus.setTextColor(network.chkWebInterfaceEnabled.isChecked()
                ? (running ? Color.rgb(76, 175, 80) : Color.rgb(255, 193, 7))
                : Color.rgb(189, 189, 189));
    }

    private void updateTlsFieldState() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        PermsTestHttpService.Status serviceStatus = PermsTestHttpService.snapshot();
        boolean enabled = !httpServer.isRunning() && !serviceStatus.running && !serviceStatus.starting && network.chkHttpUseHttps.isChecked();
        network.edtHttpTlsKeystore.setEnabled(enabled);
        network.edtHttpTlsPassword.setEnabled(enabled);
    }

    private void loadState(TabNetworkBinding network) {
        SharedPreferences prefs = getPreferences();
        int port = prefs == null ? DEFAULT_PORT : prefs.getInt(NetworkPreferenceKeys.HTTP_PORT, DEFAULT_PORT);
        String root = prefs == null ? DEFAULT_ROOT : prefs.getString(NetworkPreferenceKeys.HTTP_ROOT, DEFAULT_ROOT);
        boolean tls = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.HTTP_USE_HTTPS, false);
        boolean directoryListing = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.HTTP_DIRECTORY_LISTING, false);
        boolean keepAliveSleep = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.HTTP_KEEP_ALIVE_SLEEP, false);
        boolean backgroundUse = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.HTTP_BACKGROUND_USE, false);
        boolean webInterface = prefs == null || prefs.getBoolean(NetworkPreferenceKeys.WEB_INTERFACE_ENABLED, true);
        boolean requireToken = prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_INTERFACE_REQUIRE_TOKEN, false);
        String token = prefs == null ? "" : prefs.getString(NetworkPreferenceKeys.WEB_INTERFACE_TOKEN, "");
        String keyStore = prefs == null ? "" : prefs.getString(NetworkPreferenceKeys.HTTP_TLS_KEYSTORE, "");
        String keyStorePassword = prefs == null ? "" : prefs.getString(NetworkPreferenceKeys.HTTP_TLS_PASSWORD, "");

        network.edtHttpPort.setText(String.valueOf(port));
        network.edtHttpRoot.setText(TextUtils.isEmpty(root) ? DEFAULT_ROOT : root);
        network.chkHttpUseHttps.setChecked(tls);
        network.chkHttpDirectoryListing.setChecked(directoryListing);
        network.chkHttpKeepAliveSleep.setChecked(keepAliveSleep);
        network.chkHttpBackgroundUse.setChecked(backgroundUse);
        network.chkWebInterfaceEnabled.setChecked(webInterface);
        network.chkWebInterfaceRequireToken.setChecked(requireToken);
        network.chkWebAccessGlobal.setChecked(prefs == null || prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_GLOBAL, true));
        network.chkWebAccessMain.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_MAIN, false));
        network.chkWebAccessShell.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_SHELL, false));
        network.chkWebAccessPackages.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_PACKAGES, false));
        network.chkWebAccessMemory.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_MEMORY, false));
        network.chkWebAccessFiles.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_FILES, false));
        network.chkWebAccessNetwork.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_NETWORK, false));
        network.chkWebAccessScripts.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_SCRIPTS, false));
        network.chkWebAccessDebugging.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_DEBUGGING, false));
        network.chkWebAccessTools.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_TOOLS, false));
        network.chkWebAccessLogging.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_LOGGING, false));
        network.chkWebAccessSettings.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_SETTINGS, false));
        network.chkWebAccessAbout.setChecked(prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_ABOUT, false));
        network.edtWebInterfaceToken.setText(token == null ? "" : token);
        network.edtHttpTlsKeystore.setText(keyStore == null ? "" : keyStore);
        network.edtHttpTlsPassword.setText(keyStorePassword == null ? "" : keyStorePassword);
        loadIndexIntoEditor(false);
    }

    private void saveState(TabNetworkBinding network,
                           int port,
                           String root,
                           boolean tls,
                           boolean directoryListing,
                           boolean webInterface,
                           boolean requireToken,
                           boolean keepAliveSleep,
                           boolean backgroundUse,
                           String token,
                           String keyStorePath,
                           String keyStorePassword) {
        SharedPreferences prefs = getPreferences();
        if (prefs == null) return;
        prefs.edit()
                .putInt(NetworkPreferenceKeys.HTTP_PORT, port)
                .putString(NetworkPreferenceKeys.HTTP_ROOT, root)
                .putBoolean(NetworkPreferenceKeys.HTTP_USE_HTTPS, tls)
                .putBoolean(NetworkPreferenceKeys.HTTP_DIRECTORY_LISTING, directoryListing)
                .putBoolean(NetworkPreferenceKeys.HTTP_KEEP_ALIVE_SLEEP, keepAliveSleep)
                .putBoolean(NetworkPreferenceKeys.HTTP_BACKGROUND_USE, backgroundUse)
                .putBoolean(NetworkPreferenceKeys.WEB_INTERFACE_ENABLED, webInterface)
                .putBoolean(NetworkPreferenceKeys.WEB_INTERFACE_REQUIRE_TOKEN, requireToken)
                .putString(NetworkPreferenceKeys.WEB_INTERFACE_TOKEN, token == null ? "" : token)
                .putString(NetworkPreferenceKeys.HTTP_TLS_KEYSTORE, keyStorePath == null ? "" : keyStorePath)
                .putString(NetworkPreferenceKeys.HTTP_TLS_PASSWORD, keyStorePassword == null ? "" : keyStorePassword)
                .apply();
    }

    private void saveBoolean(String key, boolean value) {
        SharedPreferences prefs = getPreferences();
        if (prefs != null) prefs.edit().putBoolean(key, value).apply();
    }

    private void loadIndexIntoEditor(boolean showToast) {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        try {
            File root = new File(normalizeRoot(text(network.edtHttpRoot, DEFAULT_ROOT)));
            if (requiresAllFilesAccess(root) && !NetworkFtpServerStorageAccess.hasAllFilesAccess(getActivity())) {
                debugWarn("http-storage", "skip index load; missing All files access for root=" + root.getAbsolutePath());
                network.edtHttpIndexEditor.setText(DEFAULT_INDEX);
                scheduleHttpIndexSyntaxHighlight(true);
                if (showToast) {
                    NetworkFtpServerStorageAccess.showHttpStorageAccessDialog(getActivity(), root.getAbsolutePath(), this::appendOutput);
                }
                return;
            }
            ensureRootAndIndex(root);
            File index = new File(root, "index.html");
            network.edtHttpIndexEditor.setText(readText(index));
            scheduleHttpIndexSyntaxHighlight(true);
            debug("http-index", "loaded index=" + index.getAbsolutePath() + ", chars=" + network.edtHttpIndexEditor.length());
            if (showToast) Toast.makeText(getActivity(), "Loaded index.html", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            debugWarn("http-index", "load failed, using default index: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            network.edtHttpIndexEditor.setText(DEFAULT_INDEX);
            scheduleHttpIndexSyntaxHighlight(true);
            debug("http-index", "reset editor to bundled default index; chars=" + DEFAULT_INDEX.length());
            if (showToast) Toast.makeText(getActivity(), "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveIndexFromEditor() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        try {
            File root = new File(normalizeRoot(text(network.edtHttpRoot, DEFAULT_ROOT)));
            if (!ensureHttpStorageAccess(root, "save-index")) return;
            if (!root.exists() && !root.mkdirs()) throw new java.io.IOException("Unable to create " + root.getAbsolutePath());
            File index = new File(root, "index.html");
            writeText(index, text(network.edtHttpIndexEditor, ""));
            Toast.makeText(getActivity(), "Saved index.html", Toast.LENGTH_SHORT).show();
            debug("http-index", "saved index=" + index.getAbsolutePath() + ", chars=" + text(network.edtHttpIndexEditor, "").length());
            appendOutput("[HTTP] Saved " + index.getAbsolutePath() + "\n");
        } catch (Throwable e) {
            debugWarn("http-index", "save failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Toast.makeText(getActivity(), "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            appendOutput("[HTTP] Save index failed: " + e.getMessage() + "\n");
        }
    }

    private void resetIndexEditor() {
        TabNetworkBinding network = getNetworkBinding();
        if (network == null) return;
        network.edtHttpIndexEditor.setText(DEFAULT_INDEX);
        scheduleHttpIndexSyntaxHighlight(true);
        debug("http-index", "reset editor to bundled default index; chars=" + DEFAULT_INDEX.length());
    }

    private void ensureRootAndIndex(File root) throws java.io.IOException {
        if (root == null) throw new java.io.IOException("Missing root");
        if (!root.exists() && !root.mkdirs()) throw new java.io.IOException("Unable to create " + root.getAbsolutePath());
        File index = new File(root, "index.html");
        if (!index.exists()) writeText(index, DEFAULT_INDEX);
    }

    private void copyHttpUrl(boolean webInterface) {
        try {
            String url = buildHttpUrl(webInterface);
            Activity activity = getActivity();
            ClipboardManager cm = activity == null ? null : (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            debug("http-url", "copy " + (webInterface ? "web" : "http") + " url=" + url);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(webInterface ? "PermsTest Web Interface URL" : "PermsTest HTTP URL", url));
            Toast.makeText(activity, webInterface ? "Web Interface URL copied" : "HTTP URL copied", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private void openWebInterface() {
        try {
            Activity activity = getActivity();
            if (activity == null) return;
            String url = buildHttpUrl(true);
            debug("web-open", "open web interface url=" + url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(intent);
        } catch (Throwable e) {
            debugWarn("web-open", "open failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Toast.makeText(getActivity(), "Open failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String buildHttpUrl(boolean webInterface) {
        TabNetworkBinding network = getNetworkBinding();
        NetworkAddressFormatter.Status status = NetworkAddressFormatter.currentStatus();
        String host = TextUtils.isEmpty(status.firstIpv4) ? "127.0.0.1" : status.firstIpv4;
        PermsTestHttpService.Status serviceStatus = PermsTestHttpService.snapshot();
        boolean serviceActive = serviceStatus.running || serviceStatus.starting;
        int port = serviceActive ? serviceStatus.port : (httpServer.isRunning() ? httpServer.getPort() : parsePort(text(network == null ? null : network.edtHttpPort, String.valueOf(DEFAULT_PORT)), DEFAULT_PORT));
        boolean tls = serviceActive ? serviceStatus.tls : (httpServer.isRunning() ? httpServer.isTls() : (network != null && network.chkHttpUseHttps.isChecked()));
        String url = (tls ? "https" : "http") + "://" + host + ":" + port + "/";
        if (webInterface) url += "permstest";
        return url;
    }

    private void postToMain(Runnable runnable) {
        Handler handler = getMainHandler();
        if (handler != null) handler.post(runnable); else if (runnable != null) runnable.run();
    }

    private static int parsePort(String text, int fallback) {
        try {
            int port = Integer.parseInt(text == null ? "" : text.trim());
            if (port < 1 || port > 65535) throw new NumberFormatException("range");
            return port;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String normalizeRoot(String root) {
        String value = root == null ? "" : root.trim();
        return TextUtils.isEmpty(value) ? DEFAULT_ROOT : value;
    }

    private static String text(EditText editText, String fallback) {
        try {
            String value = editText == null || editText.getText() == null ? "" : editText.getText().toString();
            return TextUtils.isEmpty(value) ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String readText(File file) throws java.io.IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 1024 * 1024)];
            int read = in.read(data);
            return read <= 0 ? "" : new String(data, 0, read, StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String text) throws java.io.IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String jsonEscape(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

}
