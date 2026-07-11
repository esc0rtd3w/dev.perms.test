package dev.perms.test.ui;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShizukuDownloadDialog {
    public interface Output {
        void append(String text);
    }

    public interface UiRunner {
        void run(Runnable task);
    }

    private ShizukuDownloadDialog() {
    }

    public static void show(Activity activity,
                            String shizukuPackageName,
                            ExecutorService executor,
                            Output output,
                            UiRunner uiRunner) {
        if (activity == null) return;
        try {
            new AlertDialog.Builder(activity)
                    .setTitle("Get Shizuku")
                    .setMessage("Choose a source")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Download From Play Store", (d, w) -> openPlayStore(activity, shizukuPackageName, output))
                    .setNeutralButton("Download From GitHub", (d, w) -> downloadLatestFromGitHub(activity, executor, output, uiRunner))
                    .show();
        } catch (Throwable t) {
            openPlayStore(activity, shizukuPackageName, output);
        }
    }

    private static void openPlayStore(Activity activity, String shizukuPackageName, Output output) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + shizukuPackageName));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + shizukuPackageName));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (Throwable t) {
            append(output, "[!] Failed to open Play Store listing: " + t + "\n");
        }
    }

    private static void downloadLatestFromGitHub(Activity activity,
                                                 ExecutorService executor,
                                                 Output output,
                                                 UiRunner uiRunner) {
        append(output, "[i] Fetching latest Shizuku APK from GitHub...\n");
        if (executor == null) {
            append(output, "[!] Background executor is not available.\n");
            return;
        }
        executor.execute(() -> {
            try {
                String url = findLatestApkUrl();
                if (TextUtils.isEmpty(url)) {
                    runOnUi(uiRunner, () -> append(output, "[!] Could not locate the latest APK on the releases page.\n"));
                    return;
                }
                runOnUi(uiRunner, () -> enqueueApkDownload(activity, url, output));
            } catch (Throwable t) {
                runOnUi(uiRunner, () -> append(output, "[!] GitHub fetch failed: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n"));
            }
        });
    }

    private static String findLatestApkUrl() throws Exception {
        try {
            URL api = new URL("https://api.github.com/repos/RikkaApps/Shizuku/releases/latest");
            HttpURLConnection c = (HttpURLConnection) api.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "PermsTest");
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            String json;
            try (InputStream in = c.getInputStream()) {
                json = readAll(in);
            }
            if (!TextUtils.isEmpty(json)) {
                JSONObject o = new JSONObject(json);
                JSONArray assets = o.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a == null) continue;
                        String name = a.optString("name", "");
                        String dl = a.optString("browser_download_url", "");
                        if (TextUtils.isEmpty(dl)) continue;
                        if (!dl.endsWith(".apk")) continue;
                        if (!TextUtils.isEmpty(name) && name.toLowerCase().contains("release")) {
                            return dl;
                        }
                    }
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a == null) continue;
                        String dl = a.optString("browser_download_url", "");
                        if (!TextUtils.isEmpty(dl) && dl.endsWith(".apk")) return dl;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall back to HTML parsing.
        }

        URL u = new URL("https://github.com/RikkaApps/Shizuku/releases");
        HttpURLConnection c2 = (HttpURLConnection) u.openConnection();
        c2.setInstanceFollowRedirects(true);
        c2.setRequestProperty("User-Agent", "PermsTest");
        c2.setConnectTimeout(15000);
        c2.setReadTimeout(20000);
        String html;
        try (InputStream in = c2.getInputStream()) {
            html = readAll(in);
        }
        Pattern p = Pattern.compile("href=\\\"([^\\\"]+?\\.apk)\\\"");
        Matcher m = p.matcher(html == null ? "" : html);
        while (m.find()) {
            String href = m.group(1);
            if (TextUtils.isEmpty(href)) continue;
            if (!href.contains("/RikkaApps/Shizuku/releases/download/")) continue;
            if (!href.endsWith(".apk")) continue;
            return href.startsWith("http") ? href : ("https://github.com" + href);
        }
        return null;
    }

    private static void enqueueApkDownload(Activity activity, String url, Output output) {
        try {
            if (activity == null) {
                append(output, "[!] Download failed: Activity is not available.\n");
                return;
            }
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                append(output, "[!] DownloadManager is not available on this device.\n");
                return;
            }

            Uri uri = Uri.parse(url);
            String filename = url.substring(url.lastIndexOf('/') + 1);

            DownloadManager.Request r = new DownloadManager.Request(uri);
            r.setTitle("Shizuku APK");
            r.setDescription("GitHub Releases");
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

            long downloadId = dm.enqueue(r);
            append(output, "[+] Download started: " + filename + " (Downloads)\n");
            DownloadManagerStatusWatcher.watch(activity, dm, downloadId, new DownloadManagerStatusWatcher.Listener() {
                @Override
                public void onFinalStatus(long id, int status, int reason) {
                    appendDownloadResult(filename, output, status, reason);
                }

                @Override
                public void onStatusUnavailable(long id) {
                    append(output, "[!] Download finished but status was unavailable: " + filename + "\n");
                }
            });
        } catch (Throwable t) {
            append(output, "[!] Download failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
        }
    }

    private static void appendDownloadResult(String filename,
                                             Output output,
                                             int status,
                                             int reason) {
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            append(output, "[+] Download complete: " + filename + " (Downloads)\n");
        } else if (status == DownloadManager.STATUS_FAILED) {
            append(output, "[!] Download failed: " + filename + " (reason " + reason + ")\n");
        } else {
            append(output, "[i] Download finished with status " + status + ": " + filename + "\n");
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        if (in == null) return "";
        byte[] data = new byte[8192];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n = in.read(data)) != -1) {
            sb.append(new String(data, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static void append(Output output, String text) {
        if (output != null) output.append(text);
    }

    private static void runOnUi(UiRunner uiRunner, Runnable task) {
        if (uiRunner != null) {
            uiRunner.run(task);
        } else if (task != null) {
            task.run();
        }
    }
}
