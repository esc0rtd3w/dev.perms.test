package dev.perms.test.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.settings.SettingsPreferenceKeys;
import dev.perms.test.ui.DownloadManagerStatusWatcher;

/** Handles PermsTest self-update checks and installer launch routing. */
public final class PermsTestUpdateController {
    public interface Output {
        void append(String text);
    }

    public interface SilentInstaller {
        void install(File apk, String filename, boolean allowDowngrade, SilentInstallCallback callback);
    }

    public interface SilentInstallCallback {
        void onComplete(boolean success);
    }

    private static final String DEFAULT_RELEASES_URL = "https://github.com/esc0rtd3w/dev.perms.test/releases";
    private static final Pattern GITHUB_RELEASES = Pattern.compile("https?://github\\.com/([^/]+)/([^/]+)(?:/releases.*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern APK_LINK = Pattern.compile("href=\\\"([^\\\"]+?\\.apk)\\\"", Pattern.CASE_INSENSITIVE);
    private static final int GITHUB_RELEASES_PER_PAGE = 100;
    private static final int GITHUB_RELEASES_MAX_PAGES = 5;
    private static final int UPDATE_DEBUG_RELEASE_PREVIEW_LIMIT = 10;
    private static final int UPDATE_DEBUG_ASSET_PREVIEW_LIMIT = 80;
    private static final long AUTO_UPDATE_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final long AUTO_UPDATE_START_DELAY_MS = 30L * 1000L;

    private final Activity activity;
    private final ActivityMainBinding binding;
    private final SharedPreferences prefs;
    private final ExecutorService executor;
    private final Output output;
    private final SilentInstaller silentInstaller;
    private final String currentVersion;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable autoUpdateRunnable;
    private volatile boolean checkRunning;

    public PermsTestUpdateController(Activity activity,
                                     ActivityMainBinding binding,
                                     SharedPreferences prefs,
                                     ExecutorService executor,
                                     Output output,
                                     SilentInstaller silentInstaller,
                                     String currentVersion) {
        this.activity = activity;
        this.binding = binding;
        this.prefs = prefs;
        this.executor = executor;
        this.output = output;
        this.silentInstaller = silentInstaller;
        this.currentVersion = currentVersion == null ? "" : currentVersion.trim();
    }

    public void bindAbout() {
        try {
            if (binding == null || binding.tabAbout == null || binding.tabAbout.btnCheckForUpdates == null) return;
            binding.tabAbout.btnCheckForUpdates.setOnClickListener(v -> checkForUpdates());
        } catch (Throwable ignored) {
        }
    }

    public void bindSettings() {
        try {
            if (binding == null || binding.tabSettings == null || prefs == null) return;
            boolean custom = prefs.getBoolean(SettingsPreferenceKeys.UPDATE_CUSTOM_SERVER_ENABLED, false);
            boolean includePrereleases = prefs.getBoolean(SettingsPreferenceKeys.UPDATE_INCLUDE_PRERELEASES, false);
            boolean allowDowngrade = prefs.getBoolean(SettingsPreferenceKeys.UPDATE_ALLOW_DOWNGRADE, false);
            boolean autoUpdate = prefs.getBoolean(SettingsPreferenceKeys.UPDATE_AUTO_ENABLED, false);
            boolean autoPrerelease = prefs.getBoolean(SettingsPreferenceKeys.UPDATE_AUTO_CHANNEL_PRERELEASE, false);
            boolean silent = prefs.getBoolean(SettingsPreferenceKeys.UPDATE_SILENT, false);
            String url = prefs.getString(SettingsPreferenceKeys.UPDATE_CUSTOM_SERVER_URL, DEFAULT_RELEASES_URL);
            if (TextUtils.isEmpty(url)) url = DEFAULT_RELEASES_URL;

            if (binding.tabSettings.chkIncludePrereleases != null) {
                binding.tabSettings.chkIncludePrereleases.setOnCheckedChangeListener(null);
                binding.tabSettings.chkIncludePrereleases.setChecked(includePrereleases);
                binding.tabSettings.chkIncludePrereleases.setOnCheckedChangeListener((btn, checked) ->
                        prefs.edit().putBoolean(SettingsPreferenceKeys.UPDATE_INCLUDE_PRERELEASES, checked).apply());
            }
            if (binding.tabSettings.chkAllowDowngrade != null) {
                binding.tabSettings.chkAllowDowngrade.setOnCheckedChangeListener(null);
                binding.tabSettings.chkAllowDowngrade.setChecked(allowDowngrade);
                binding.tabSettings.chkAllowDowngrade.setOnCheckedChangeListener((btn, checked) ->
                        prefs.edit().putBoolean(SettingsPreferenceKeys.UPDATE_ALLOW_DOWNGRADE, checked).apply());
            }
            if (binding.tabSettings.chkAutoUpdate != null) {
                binding.tabSettings.chkAutoUpdate.setOnCheckedChangeListener(null);
                binding.tabSettings.chkAutoUpdate.setChecked(autoUpdate);
                binding.tabSettings.chkAutoUpdate.setOnCheckedChangeListener((btn, checked) -> {
                    prefs.edit().putBoolean(SettingsPreferenceKeys.UPDATE_AUTO_ENABLED, checked).apply();
                    applyAutoUpdateChannelState(checked);
                    scheduleAutoUpdateIfNeeded();
                });
            }
            if (binding.tabSettings.radioUpdateAutoRelease != null) {
                binding.tabSettings.radioUpdateAutoRelease.setOnCheckedChangeListener(null);
                binding.tabSettings.radioUpdateAutoRelease.setChecked(!autoPrerelease);
                binding.tabSettings.radioUpdateAutoRelease.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) {
                        prefs.edit().putBoolean(SettingsPreferenceKeys.UPDATE_AUTO_CHANNEL_PRERELEASE, false).apply();
                    }
                });
            }
            if (binding.tabSettings.radioUpdateAutoPrerelease != null) {
                binding.tabSettings.radioUpdateAutoPrerelease.setOnCheckedChangeListener(null);
                binding.tabSettings.radioUpdateAutoPrerelease.setChecked(autoPrerelease);
                binding.tabSettings.radioUpdateAutoPrerelease.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) {
                        prefs.edit().putBoolean(SettingsPreferenceKeys.UPDATE_AUTO_CHANNEL_PRERELEASE, true).apply();
                    }
                });
            }
            applyAutoUpdateChannelState(autoUpdate);
            if (binding.tabSettings.chkUpdateSilently != null) {
                binding.tabSettings.chkUpdateSilently.setOnCheckedChangeListener(null);
                binding.tabSettings.chkUpdateSilently.setChecked(silent);
                binding.tabSettings.chkUpdateSilently.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) showSilentUpdateWarning();
                    prefs.edit().putBoolean(SettingsPreferenceKeys.UPDATE_SILENT, checked).apply();
                });
            }
            scheduleAutoUpdateIfNeeded();
            if (binding.tabSettings.chkCustomUpdateServer != null) {
                binding.tabSettings.chkCustomUpdateServer.setOnCheckedChangeListener(null);
                binding.tabSettings.chkCustomUpdateServer.setChecked(custom);
                binding.tabSettings.chkCustomUpdateServer.setOnCheckedChangeListener((btn, checked) -> {
                    prefs.edit().putBoolean(SettingsPreferenceKeys.UPDATE_CUSTOM_SERVER_ENABLED, checked).apply();
                    applyCustomServerTextState(checked);
                });
            }
            if (binding.tabSettings.edtCustomUpdateServerUrl != null) {
                binding.tabSettings.edtCustomUpdateServerUrl.setText(url);
                binding.tabSettings.edtCustomUpdateServerUrl.setSingleLine(true);
                binding.tabSettings.edtCustomUpdateServerUrl.setEnabled(custom);
                binding.tabSettings.edtCustomUpdateServerUrl.setAlpha(custom ? 1.0f : 0.55f);
                binding.tabSettings.edtCustomUpdateServerUrl.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) saveCustomUrlFromUi();
                });
            }
        } catch (Throwable ignored) {
        }
    }

    public void checkForUpdates() {
        checkForUpdatesInternal(false);
    }

    private void checkForUpdatesInternal(boolean automatic) {
        if (activity == null) return;
        if (executor == null) {
            append("[update] Background executor is not available.\n");
            showManualUpdateToast(automatic, false, "Update check failed: background executor is not available.");
            return;
        }
        if (checkRunning) {
            if (!automatic) {
                append("[update] Update check is already running.\n");
                showManualUpdateToast(false, false, "Update check is already running.");
            }
            return;
        }
        checkRunning = true;
        saveCustomUrlFromUi();
        final String source = getSelectedUpdateSource();
        final boolean includePrereleases = automatic ? isAutoPrereleaseChannelEnabled() : isIncludePrereleasesEnabled();
        final boolean allowDowngrade = !automatic && isAllowDowngradeEnabled();
        final boolean silent = automatic && isAutoUpdateEnabled() && isSilentUpdateEnabled();
        append(automatic ? "[update] Auto update check started.\n" : "[update] Checking for PermsTest updates...\n");
        debug("check start automatic=" + automatic
                + ", includePrereleases=" + includePrereleases
                + ", allowDowngrade=" + allowDowngrade
                + ", silent=" + silent
                + ", current=" + currentVersion
                + ", source=" + source);
        executor.execute(() -> {
            try {
                if (!automatic && allowDowngrade) {
                    List<UpdateInfo> options = findSelectableUpdates(source, includePrereleases);
                    runOnUi(() -> {
                        checkRunning = false;
                        if (options == null || options.isEmpty()) {
                            append("[update] No PermsTest APK was found at the update source.\n");
                        } else {
                            showUpdateSelectionDialog(options, allowDowngrade);
                        }
                    });
                    return;
                }

                UpdateInfo update = findLatestUpdate(source, includePrereleases);
                if (update == null || TextUtils.isEmpty(update.apkUrl)) {
                    runOnUi(() -> {
                        checkRunning = false;
                        if (!automatic) {
                            append("[update] No PermsTest APK was found at the update source.\n");
                            showManualUpdateToast(false, allowDowngrade,
                                    "No PermsTest APK was found at the " + updateChannelLabel(includePrereleases) + " update source.");
                        }
                    });
                    return;
                }
                if (!TextUtils.isEmpty(update.version) && !TextUtils.isEmpty(currentVersion)) {
                    int versionCompare = compareVersions(update.version, currentVersion);
                    if (versionCompare <= 0) {
                        runOnUi(() -> {
                            checkRunning = false;
                            if (!automatic) {
                                String logMessage;
                                String toastMessage;
                                if (versionCompare == 0) {
                                    logMessage = "[update] Already current: " + currentVersion + " (latest " + update.version + ").\n";
                                    toastMessage = "Already current: " + currentVersion + " is the latest "
                                            + updateChannelLabel(includePrereleases) + " version.";
                                } else {
                                    logMessage = "[update] No newer PermsTest " + updateChannelLabel(includePrereleases)
                                            + " version is available: current " + currentVersion
                                            + " (latest " + update.version + ").\n";
                                    toastMessage = "No newer " + updateChannelLabel(includePrereleases)
                                            + " version is available. Current: " + currentVersion
                                            + ", latest: " + update.version + ".";
                                }
                                append(logMessage);
                                showManualUpdateToast(false, allowDowngrade, toastMessage);
                            }
                        });
                        return;
                    }
                }
                runOnUi(() -> {
                    checkRunning = false;
                    enqueueUpdateDownload(update, automatic, silent, allowDowngrade);
                });
            } catch (NoStableReleaseException e) {
                runOnUi(() -> {
                    checkRunning = false;
                    String message = "No release build is available yet. Enable Include Pre-Releases to check pre-release builds.";
                    append("[update] " + message + "\n");
                    showManualUpdateToast(automatic, allowDowngrade, message);
                });
            } catch (Throwable t) {
                runOnUi(() -> {
                    checkRunning = false;
                    String detail = t.getClass().getSimpleName() + ": " + t.getMessage();
                    append("[update] Update check failed: " + detail + "\n");
                    showManualUpdateToast(automatic, allowDowngrade, "Update check failed: " + detail);
                });
            }
        });
    }

    /**
     * Manual update checks can finish while the bottom log is hidden, so terminal
     * results also get a compact Toast. Downgrade mode uses the version picker
     * dialog as its visible result surface and intentionally skips these toasts.
     */
    private void showManualUpdateToast(boolean automatic, boolean allowDowngrade, String message) {
        if (automatic || allowDowngrade || TextUtils.isEmpty(message) || activity == null) return;
        try {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }

    private static String updateChannelLabel(boolean includePrereleases) {
        return includePrereleases ? "pre-release" : "release";
    }

    private void enqueueUpdateDownload(UpdateInfo update, boolean automatic, boolean silent, boolean allowDowngrade) {
        try {
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                append("[update] DownloadManager is not available on this device.\n");
                return;
            }
            File dir = new File(activity.getExternalFilesDir(null), "update_downloads");
            if (!dir.exists() && !dir.mkdirs()) {
                append("[update] Could not create update download directory.\n");
                return;
            }
            String filename = sanitizeApkFilename(update.filename);
            File apk = new File(dir, filename);
            try { if (apk.exists()) apk.delete(); } catch (Throwable ignored) {}

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(update.apkUrl));
            request.setTitle("PermsTest Update");
            request.setDescription(filename);
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationUri(Uri.fromFile(apk));

            long id = dm.enqueue(request);
            append("[update] Download started: " + filename + "\n");
            DownloadManagerStatusWatcher.watch(activity, dm, id, new DownloadManagerStatusWatcher.Listener() {
                @Override
                public void onFinalStatus(long downloadId, int status, int reason) {
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        append("[update] Download complete: " + filename + "\n");
                        if (silent && silentInstaller != null) {
                            silentInstaller.install(apk, filename, allowDowngrade, success -> {
                                if (!success && automatic) {
                                    append("[update] Silent auto update did not complete. Manual install was not opened.\n");
                                }
                            });
                        } else {
                            installDownloadedApk(apk);
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        append("[update] Download failed: " + filename + " (reason " + reason + ")\n");
                    } else {
                        append("[update] Download finished with status " + status + ": " + filename + "\n");
                    }
                }

                @Override
                public void onStatusUnavailable(long downloadId) {
                    append("[update] Download finished but status was unavailable: " + filename + "\n");
                }
            });
        } catch (Throwable t) {
            append("[update] Download failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
        }
    }

    private void installDownloadedApk(File apk) {
        try {
            if (apk == null || !apk.isFile() || apk.length() <= 0L) {
                append("[update] Downloaded APK is missing or empty.\n");
                return;
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            activity.startActivity(intent);
            append("[update] Opening Android package installer.\n");
        } catch (Throwable t) {
            append("[update] Could not open installer: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            try { Toast.makeText(activity, "Update install failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
        }
    }

    private void showUpdateSelectionDialog(List<UpdateInfo> updates, boolean allowDowngrade) {
        try {
            if (updates == null || updates.isEmpty()) {
                append("[update] No update choices are available.\n");
                return;
            }
            final UpdateInfo[] choices = updates.toArray(new UpdateInfo[0]);
            String[] labels = new String[choices.length];
            for (int i = 0; i < choices.length; i++) {
                labels[i] = choices[i].displayLabel();
            }
            append("[update] Select a release to download (" + choices.length + " found).\n");
            debugUpdateChoices("selector", updates, UPDATE_DEBUG_RELEASE_PREVIEW_LIMIT);
            new AlertDialog.Builder(activity)
                    .setTitle("Select Update Version")
                    .setItems(labels, (dialog, which) -> {
                        if (which >= 0 && which < choices.length) {
                            enqueueUpdateDownload(choices[which], false, false, allowDowngrade);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Throwable t) {
            append("[update] Could not show update selector: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
        }
    }

    private List<UpdateInfo> findSelectableUpdates(String source, boolean includePrereleases) throws Exception {
        ArrayList<UpdateInfo> out = new ArrayList<>();
        if (TextUtils.isEmpty(source)) source = DEFAULT_RELEASES_URL;
        String trimmed = source.trim();
        if (trimmed.toLowerCase(Locale.US).endsWith(".apk")) {
            debug("selectable source is a direct APK URL: " + trimmed);
            out.add(new UpdateInfo(trimmed, filenameFromUrl(trimmed), versionFromUrl(trimmed)));
            return out;
        }

        Matcher gh = GITHUB_RELEASES.matcher(trimmed);
        if (gh.matches()) {
            debug("selectable source resolved to GitHub repo " + gh.group(1) + "/" + stripDotGit(gh.group(2))
                    + ", prereleaseChannel=" + includePrereleases);
            return findGitHubReleaseApksForSelection(gh.group(1), stripDotGit(gh.group(2)), includePrereleases);
        }

        debug("selectable source resolved to HTML scan: " + trimmed);
        UpdateInfo html = findLatestHtmlApk(trimmed);
        if (html != null) out.add(html);
        return out;
    }

    private UpdateInfo findLatestUpdate(String source, boolean includePrereleases) throws Exception {
        if (TextUtils.isEmpty(source)) source = DEFAULT_RELEASES_URL;
        String trimmed = source.trim();
        if (trimmed.toLowerCase(Locale.US).endsWith(".apk")) {
            debug("latest source is a direct APK URL: " + trimmed);
            return new UpdateInfo(trimmed, filenameFromUrl(trimmed), versionFromUrl(trimmed));
        }

        Matcher gh = GITHUB_RELEASES.matcher(trimmed);
        if (gh.matches()) {
            debug("latest source resolved to GitHub repo " + gh.group(1) + "/" + stripDotGit(gh.group(2))
                    + ", includePrereleases=" + includePrereleases);
            return findLatestGitHubReleaseApk(gh.group(1), stripDotGit(gh.group(2)), includePrereleases);
        }
        debug("latest source resolved to HTML scan: " + trimmed);
        return findLatestHtmlApk(trimmed);
    }

    private UpdateInfo findLatestGitHubReleaseApk(String owner, String repo, boolean includePrereleases) throws Exception {
        if (TextUtils.isEmpty(owner) || TextUtils.isEmpty(repo)) return null;
        if (!includePrereleases) {
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
            HttpURLConnection c = open(apiUrl, "application/vnd.github+json");
            int http = c.getResponseCode();
            debug("GitHub latest release HTTP " + http + " for " + owner + "/" + repo);
            // GitHub returns 404 for /releases/latest until the project has a stable
            // release. Treat that as an empty release channel, not a broken source.
            if (http == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new NoStableReleaseException();
            }
            String json;
            try (InputStream in = c.getInputStream()) {
                json = readAll(in);
            }
            debug("GitHub latest release response bytes=" + (json == null ? 0 : json.length()));
            if (TextUtils.isEmpty(json)) return null;
            UpdateInfo latest = findApkInReleaseObject(new JSONObject(json));
            debugUpdateInfo("latest-stable", latest);
            return latest;
        }

        List<UpdateInfo> choices = findGitHubReleaseApksForSelection(owner, repo, true);
        debugUpdateChoices("latest-prerelease", choices, UPDATE_DEBUG_RELEASE_PREVIEW_LIMIT);
        return choices.isEmpty() ? null : choices.get(0);
    }

    private List<UpdateInfo> findGitHubReleaseApksForSelection(String owner, String repo, boolean prereleaseChannel) throws Exception {
        ArrayList<UpdateInfo> out = new ArrayList<>();
        if (TextUtils.isEmpty(owner) || TextUtils.isEmpty(repo)) return out;
        List<JSONObject> releases = fetchGitHubReleaseObjects(owner, repo);
        debug("GitHub releases fetched for " + owner + "/" + repo + ": " + releases.size()
                + ", requestedChannel=" + (prereleaseChannel ? "pre-release" : "release"));
        debugGitHubReleasePreview(releases);
        for (int i = 0; i < releases.size(); i++) {
            JSONObject release = releases.get(i);
            if (release == null) continue;
            boolean prerelease = release.optBoolean("prerelease", false);
            String tag = release.optString("tag_name", "");
            if (prerelease != prereleaseChannel) {
                debug("skip release tag=" + tag + ", prerelease=" + prerelease
                        + ", requestedChannel=" + prereleaseChannel);
                continue;
            }
            debug("scan release tag=" + tag + ", prerelease=" + prerelease
                    + ", assets=" + assetCount(release));
            out.addAll(findAllApksInReleaseObject(release));
        }
        sortUpdateInfos(out);
        debugUpdateChoices("github-selection-sorted", out, UPDATE_DEBUG_RELEASE_PREVIEW_LIMIT);
        return out;
    }

    private List<JSONObject> fetchGitHubReleaseObjects(String owner, String repo) throws Exception {
        ArrayList<JSONObject> out = new ArrayList<>();
        for (int page = 1; page <= GITHUB_RELEASES_MAX_PAGES; page++) {
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo
                    + "/releases?per_page=" + GITHUB_RELEASES_PER_PAGE + "&page=" + page;
            HttpURLConnection c = open(apiUrl, "application/vnd.github+json");
            int http = c.getResponseCode();
            debug("GitHub releases page " + page + " HTTP " + http + " url=" + apiUrl);
            String json;
            try (InputStream in = c.getInputStream()) {
                json = readAll(in);
            }
            debug("GitHub releases page " + page + " response bytes=" + (json == null ? 0 : json.length()));
            if (TextUtils.isEmpty(json)) break;
            JSONArray releases = new JSONArray(json);
            debug("GitHub releases page " + page + " release_count=" + releases.length());
            if (releases.length() == 0) break;
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release != null) out.add(release);
            }
            if (releases.length() < GITHUB_RELEASES_PER_PAGE) break;
        }
        return out;
    }

    private List<UpdateInfo> findAllApksInReleaseObject(JSONObject root) {
        ArrayList<UpdateInfo> preferred = new ArrayList<>();
        ArrayList<UpdateInfo> fallback = new ArrayList<>();
        if (root == null) return preferred;
        String tag = root.optString("tag_name", "");
        String releaseName = root.optString("name", "");
        String publishedAt = root.optString("published_at", root.optString("created_at", ""));
        boolean prerelease = root.optBoolean("prerelease", false);
        JSONArray assets = root.optJSONArray("assets");
        if (assets == null) return preferred;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "");
            String dl = asset.optString("browser_download_url", "");
            boolean apk = !TextUtils.isEmpty(dl) && dl.toLowerCase(Locale.US).endsWith(".apk");
            boolean preferredName = name.toLowerCase(Locale.US).contains("permstest");
            debugAssetDecision(tag, i, name, dl, apk, preferredName);
            if (!apk) continue;
            UpdateInfo info = new UpdateInfo(dl, TextUtils.isEmpty(name) ? filenameFromUrl(dl) : name,
                    TextUtils.isEmpty(tag) ? versionFromUrl(dl) : tag, prerelease, releaseName, publishedAt);
            if (preferredName) {
                preferred.add(info);
            } else {
                fallback.add(info);
            }
        }
        preferred.addAll(fallback);
        return preferred;
    }

    private UpdateInfo findApkInReleaseObject(JSONObject root) {
        if (root == null) return null;
        String tag = root.optString("tag_name", "");
        String releaseName = root.optString("name", "");
        String publishedAt = root.optString("published_at", root.optString("created_at", ""));
        boolean prerelease = root.optBoolean("prerelease", false);
        JSONArray assets = root.optJSONArray("assets");
        if (assets == null) return null;
        UpdateInfo fallback = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "");
            String dl = asset.optString("browser_download_url", "");
            boolean apk = !TextUtils.isEmpty(dl) && dl.toLowerCase(Locale.US).endsWith(".apk");
            boolean preferredName = name.toLowerCase(Locale.US).contains("permstest");
            debugAssetDecision(tag, i, name, dl, apk, preferredName);
            if (!apk) continue;
            UpdateInfo info = new UpdateInfo(dl, TextUtils.isEmpty(name) ? filenameFromUrl(dl) : name,
                    TextUtils.isEmpty(tag) ? versionFromUrl(dl) : tag, prerelease, releaseName, publishedAt);
            if (preferredName) return info;
            if (fallback == null) fallback = info;
        }
        return fallback;
    }

    private static void sortUpdateInfos(List<UpdateInfo> updates) {
        if (updates == null || updates.size() < 2) return;
        Collections.sort(updates, (left, right) -> {
            int version = compareVersions(right == null ? "" : right.version, left == null ? "" : left.version);
            if (version != 0) return version;
            String rightPublished = right == null || right.publishedAt == null ? "" : right.publishedAt;
            String leftPublished = left == null || left.publishedAt == null ? "" : left.publishedAt;
            int published = rightPublished.compareTo(leftPublished);
            if (published != 0) return published;
            String rightName = right == null || right.filename == null ? "" : right.filename;
            String leftName = left == null || left.filename == null ? "" : left.filename;
            return rightName.compareToIgnoreCase(leftName);
        });
    }

    private UpdateInfo findLatestHtmlApk(String pageUrl) throws Exception {
        HttpURLConnection c = open(pageUrl, "text/html,*/*");
        String html;
        try (InputStream in = c.getInputStream()) {
            html = readAll(in);
        }
        Matcher m = APK_LINK.matcher(html == null ? "" : html);
        while (m.find()) {
            String href = m.group(1);
            if (TextUtils.isEmpty(href)) continue;
            String absolute = absolutize(pageUrl, href);
            if (!absolute.toLowerCase(Locale.US).endsWith(".apk")) {
                debug("HTML update asset skipped (not APK): " + absolute);
                continue;
            }
            UpdateInfo info = new UpdateInfo(absolute, filenameFromUrl(absolute), versionFromUrl(absolute));
            debugUpdateInfo("html-first-apk", info);
            return info;
        }
        return null;
    }

    private HttpURLConnection open(String url, String accept) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "PermsTest");
        if (!TextUtils.isEmpty(accept)) c.setRequestProperty("Accept", accept);
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        return c;
    }

    private boolean isDebugOutputEnabled() {
        try {
            return prefs != null && prefs.getBoolean(SettingsPreferenceKeys.DEBUG_OUTPUT, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void debug(String message) {
        if (!isDebugOutputEnabled() || TextUtils.isEmpty(message)) return;
        append("[update][debug] " + message + "\n");
    }

    private void debugGitHubReleasePreview(List<JSONObject> releases) {
        if (!isDebugOutputEnabled() || releases == null || releases.isEmpty()) return;
        int limit = Math.min(UPDATE_DEBUG_RELEASE_PREVIEW_LIMIT, releases.size());
        for (int i = 0; i < limit; i++) {
            JSONObject release = releases.get(i);
            if (release == null) continue;
            debug("release[" + i + "] tag=" + release.optString("tag_name", "")
                    + ", name=" + release.optString("name", "")
                    + ", prerelease=" + release.optBoolean("prerelease", false)
                    + ", draft=" + release.optBoolean("draft", false)
                    + ", published=" + release.optString("published_at", release.optString("created_at", ""))
                    + ", assets=" + assetCount(release));
        }
        if (releases.size() > limit) {
            debug("release preview truncated: " + limit + " of " + releases.size() + " shown");
        }
    }

    private void debugAssetDecision(String tag, int index, String name, String downloadUrl, boolean acceptedApk, boolean preferredName) {
        if (!isDebugOutputEnabled()) return;
        if (index >= UPDATE_DEBUG_ASSET_PREVIEW_LIMIT) {
            if (index == UPDATE_DEBUG_ASSET_PREVIEW_LIMIT) {
                debug("asset decision preview truncated at " + UPDATE_DEBUG_ASSET_PREVIEW_LIMIT + " assets for release " + tag);
            }
            return;
        }
        debug("asset release=" + tag
                + " index=" + index
                + " name=" + (TextUtils.isEmpty(name) ? "<empty>" : name)
                + " accepted=" + acceptedApk
                + " preferred=" + preferredName
                + " url=" + abbreviate(downloadUrl, 160));
    }

    private void debugUpdateChoices(String label, List<UpdateInfo> choices, int limit) {
        if (!isDebugOutputEnabled()) return;
        int count = choices == null ? 0 : choices.size();
        debug(label + " choices=" + count);
        if (choices == null || choices.isEmpty()) return;
        int max = Math.min(Math.max(0, limit), choices.size());
        for (int i = 0; i < max; i++) {
            debugUpdateInfo(label + "[" + i + "]", choices.get(i));
        }
        if (choices.size() > max) {
            debug(label + " preview truncated: " + max + " of " + choices.size() + " shown");
        }
    }

    private void debugUpdateInfo(String label, UpdateInfo info) {
        if (!isDebugOutputEnabled()) return;
        if (info == null) {
            debug(label + "=<null>");
            return;
        }
        debug(label + " version=" + info.version
                + ", filename=" + info.filename
                + ", prerelease=" + info.prerelease
                + ", published=" + info.publishedAt
                + ", url=" + abbreviate(info.apkUrl, 180));
    }

    private static int assetCount(JSONObject release) {
        try {
            JSONArray assets = release == null ? null : release.optJSONArray("assets");
            return assets == null ? 0 : assets.length();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String abbreviate(String value, int max) {
        if (value == null) return "";
        if (max <= 3 || value.length() <= max) return value;
        return value.substring(0, max - 3) + "...";
    }

    private String getSelectedUpdateSource() {
        try {
            if (prefs == null) return DEFAULT_RELEASES_URL;
            boolean custom = prefs.getBoolean(SettingsPreferenceKeys.UPDATE_CUSTOM_SERVER_ENABLED, false);
            String url = prefs.getString(SettingsPreferenceKeys.UPDATE_CUSTOM_SERVER_URL, DEFAULT_RELEASES_URL);
            if (TextUtils.isEmpty(url)) url = DEFAULT_RELEASES_URL;
            return custom ? url.trim() : DEFAULT_RELEASES_URL;
        } catch (Throwable ignored) {
            return DEFAULT_RELEASES_URL;
        }
    }

    private void saveCustomUrlFromUi() {
        try {
            if (prefs == null || binding == null || binding.tabSettings == null || binding.tabSettings.edtCustomUpdateServerUrl == null) return;
            String url = binding.tabSettings.edtCustomUpdateServerUrl.getText() == null
                    ? "" : binding.tabSettings.edtCustomUpdateServerUrl.getText().toString().trim();
            if (TextUtils.isEmpty(url)) url = DEFAULT_RELEASES_URL;
            prefs.edit().putString(SettingsPreferenceKeys.UPDATE_CUSTOM_SERVER_URL, url).apply();
        } catch (Throwable ignored) {
        }
    }

    private void applyCustomServerTextState(boolean enabled) {
        try {
            if (binding == null || binding.tabSettings == null || binding.tabSettings.edtCustomUpdateServerUrl == null) return;
            binding.tabSettings.edtCustomUpdateServerUrl.setEnabled(enabled);
            binding.tabSettings.edtCustomUpdateServerUrl.setAlpha(enabled ? 1.0f : 0.55f);
        } catch (Throwable ignored) {
        }
    }

    private void applyAutoUpdateChannelState(boolean enabled) {
        try {
            if (binding == null || binding.tabSettings == null) return;
            if (binding.tabSettings.radioUpdateAutoChannel != null) {
                binding.tabSettings.radioUpdateAutoChannel.setEnabled(enabled);
                binding.tabSettings.radioUpdateAutoChannel.setAlpha(enabled ? 1.0f : 0.55f);
            }
            if (binding.tabSettings.radioUpdateAutoRelease != null) binding.tabSettings.radioUpdateAutoRelease.setEnabled(enabled);
            if (binding.tabSettings.radioUpdateAutoPrerelease != null) binding.tabSettings.radioUpdateAutoPrerelease.setEnabled(enabled);
        } catch (Throwable ignored) {
        }
    }

    private void showSilentUpdateWarning() {
        try {
            new android.app.AlertDialog.Builder(activity)
                    .setTitle("Update Silently")
                    .setMessage("Auto updates can check the selected release channel periodically and install a downloaded APK through the selected backend without opening the Android package installer. This can use a small amount of network, data, and battery when checks run.")
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void scheduleAutoUpdateIfNeeded() {
        try {
            if (autoUpdateRunnable != null) mainHandler.removeCallbacks(autoUpdateRunnable);
            if (!isAutoUpdateEnabled()) return;
            autoUpdateRunnable = () -> {
                try {
                    if (isAutoUpdateEnabled()) {
                        recordAutoUpdateCheckTime();
                        checkForUpdatesInternal(true);
                        scheduleAutoUpdateIfNeeded();
                    }
                } catch (Throwable ignored) {
                }
            };
            mainHandler.postDelayed(autoUpdateRunnable, getAutoUpdateDelayMs());
        } catch (Throwable ignored) {
        }
    }

    public void shutdown() {
        try {
            if (autoUpdateRunnable != null) mainHandler.removeCallbacks(autoUpdateRunnable);
            autoUpdateRunnable = null;
        } catch (Throwable ignored) {
        }
    }

    private long getAutoUpdateDelayMs() {
        try {
            if (prefs == null) return AUTO_UPDATE_INTERVAL_MS;
            long last = prefs.getLong(SettingsPreferenceKeys.UPDATE_LAST_AUTO_CHECK_MS, 0L);
            long elapsed = System.currentTimeMillis() - last;
            if (elapsed >= AUTO_UPDATE_INTERVAL_MS || elapsed < 0L) return AUTO_UPDATE_START_DELAY_MS;
            return Math.max(AUTO_UPDATE_START_DELAY_MS, AUTO_UPDATE_INTERVAL_MS - elapsed);
        } catch (Throwable ignored) {
            return AUTO_UPDATE_INTERVAL_MS;
        }
    }

    private void recordAutoUpdateCheckTime() {
        try {
            if (prefs != null) prefs.edit().putLong(SettingsPreferenceKeys.UPDATE_LAST_AUTO_CHECK_MS, System.currentTimeMillis()).apply();
        } catch (Throwable ignored) {
        }
    }

    private boolean isIncludePrereleasesEnabled() {
        try {
            return prefs != null && prefs.getBoolean(SettingsPreferenceKeys.UPDATE_INCLUDE_PRERELEASES, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isAllowDowngradeEnabled() {
        try {
            return prefs != null && prefs.getBoolean(SettingsPreferenceKeys.UPDATE_ALLOW_DOWNGRADE, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isAutoUpdateEnabled() {
        try {
            return prefs != null && prefs.getBoolean(SettingsPreferenceKeys.UPDATE_AUTO_ENABLED, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isAutoPrereleaseChannelEnabled() {
        try {
            return prefs != null && prefs.getBoolean(SettingsPreferenceKeys.UPDATE_AUTO_CHANNEL_PRERELEASE, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isSilentUpdateEnabled() {
        try {
            return prefs != null && prefs.getBoolean(SettingsPreferenceKeys.UPDATE_SILENT, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void append(String text) {
        if (output != null) output.append(text);
    }

    private void runOnUi(Runnable r) {
        if (activity != null && r != null) activity.runOnUiThread(r);
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

    private static String absolutize(String base, String href) throws Exception {
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        return new URL(new URL(base), href).toString();
    }

    private static String stripDotGit(String repo) {
        if (repo == null) return "";
        return repo.endsWith(".git") ? repo.substring(0, repo.length() - 4) : repo;
    }

    private static String filenameFromUrl(String url) {
        try {
            String path = Uri.parse(url).getLastPathSegment();
            if (!TextUtils.isEmpty(path)) return path;
        } catch (Throwable ignored) {
        }
        return "permstest-update.apk";
    }

    private static String sanitizeApkFilename(String name) {
        String n = TextUtils.isEmpty(name) ? "permstest-update.apk" : name.trim();
        n = n.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!n.toLowerCase(Locale.US).endsWith(".apk")) n += ".apk";
        return n;
    }

    private static String versionFromUrl(String url) {
        if (url == null) return "";
        Matcher m = Pattern.compile("/(v?\\d+(?:\\.\\d+)*-\\d{8}\\.\\d+)(?:/|$|[^0-9])", Pattern.CASE_INSENSITIVE).matcher(url);
        if (m.find()) return normalizeVersionPrefix(m.group(1));
        Matcher m2 = Pattern.compile("permstest-(v?\\d+(?:\\.\\d+)*-\\d{8}\\.\\d+)\\.apk", Pattern.CASE_INSENSITIVE).matcher(url);
        if (m2.find()) return normalizeVersionPrefix(m2.group(1));
        return "";
    }

    private static String normalizeVersionPrefix(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.trim();
        return v.startsWith("v") || v.startsWith("V") ? ("v" + v.substring(1)) : ("v" + v);
    }

    private static int compareVersions(String left, String right) {
        long[] a = versionNumbers(left);
        long[] b = versionNumbers(right);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            long av = i < a.length ? a[i] : 0L;
            long bv = i < b.length ? b[i] : 0L;
            if (av < bv) return -1;
            if (av > bv) return 1;
        }
        return 0;
    }

    private static long[] versionNumbers(String version) {
        if (version == null) return new long[0];
        Matcher m = Pattern.compile("\\d+").matcher(version);
        long[] tmp = new long[8];
        int count = 0;
        while (m.find() && count < tmp.length) {
            try { tmp[count++] = Long.parseLong(m.group()); } catch (Throwable ignored) { tmp[count++] = 0L; }
        }
        long[] out = new long[count];
        System.arraycopy(tmp, 0, out, 0, count);
        return out;
    }

    private static final class NoStableReleaseException extends Exception {
        NoStableReleaseException() {
            super("No stable release is available yet");
        }
    }

    private static final class UpdateInfo {
        final String apkUrl;
        final String filename;
        final String version;
        final boolean prerelease;
        final String releaseName;
        final String publishedAt;

        UpdateInfo(String apkUrl, String filename, String version) {
            this(apkUrl, filename, version, false, "", "");
        }

        UpdateInfo(String apkUrl, String filename, String version, boolean prerelease, String releaseName, String publishedAt) {
            this.apkUrl = apkUrl;
            this.filename = filename;
            this.version = version;
            this.prerelease = prerelease;
            this.releaseName = releaseName == null ? "" : releaseName.trim();
            this.publishedAt = publishedAt == null ? "" : publishedAt.trim();
        }

        String displayLabel() {
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(version)) {
                sb.append(version);
            } else if (!TextUtils.isEmpty(releaseName)) {
                sb.append(releaseName);
            } else {
                sb.append(filename);
            }
            if (prerelease) sb.append("  [pre-release]");
            if (!TextUtils.isEmpty(filename) && sb.indexOf(filename) < 0) {
                sb.append("\n").append(filename);
            }
            return sb.toString();
        }
    }
}
