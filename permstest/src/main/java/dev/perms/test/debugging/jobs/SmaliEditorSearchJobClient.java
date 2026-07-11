package dev.perms.test.debugging.jobs;

import dev.perms.test.debugging.editor.SmaliEditorSearch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Base64;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Activity-side controller for the foreground Smali Editor search job. */
public final class SmaliEditorSearchJobClient {
    public interface Callbacks {
        void setSearchRunning(boolean running, String status);
        void applyResults(SmaliEditorSearch.Page page);
        void finishError(String label, Throwable error);
    }

    private final Context context;
    private final Handler handler;
    private final Callbacks callbacks;
    private Runnable statusPoller;

    public SmaliEditorSearchJobClient(Context context, Handler handler, Callbacks callbacks) {
        this.context = context == null ? null : context.getApplicationContext();
        this.handler = handler;
        this.callbacks = callbacks;
    }

    public boolean isRunning() {
        try {
            return prefs().getBoolean(PermsTestSmaliJobService.PREF_SEARCH_RUNNING, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void start(ArrayList<String> rootPaths,
                      String displayRoot,
                      SmaliEditorSearch.Criteria criteria,
                      int maxFiles,
                      int pageSize,
                      int pageIndex) {
        try {
            if (context == null) return;
            Intent svc = new Intent(context, PermsTestSmaliJobService.class);
            svc.setAction(PermsTestSmaliJobService.ACTION_SEARCH_EDITOR);
            svc.putStringArrayListExtra(PermsTestSmaliJobService.EXTRA_SEARCH_ROOTS, rootPaths);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_SEARCH_DISPLAY_ROOT, displayRoot == null ? "" : displayRoot);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_SEARCH_QUERY, criteria == null ? "" : criteria.query);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_SEARCH_FILTER_MODE, criteria == null ? SmaliEditorSearch.FILTER_CONTAINS_IGNORE_CASE : criteria.filterMode);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_SEARCH_EXCLUDE_ENABLED, criteria != null && criteria.excludeEnabled);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_SEARCH_EXCLUDE_TERMS, joinCriteriaTerms(criteria == null ? null : criteria.excludeTerms));
            svc.putExtra(PermsTestSmaliJobService.EXTRA_SEARCH_MAX_FILES, maxFiles);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_SEARCH_PAGE_SIZE, pageSize);
            svc.putExtra(PermsTestSmaliJobService.EXTRA_SEARCH_PAGE_INDEX, pageIndex);
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, svc);
            else context.startService(svc);
            callbacks.setSearchRunning(true, pageIndex <= 0 ? "Searching smali/java folders..." : "Searching smali/java page " + (pageIndex + 1) + "...");
            scheduleStatusPoll();
        } catch (Throwable t) {
            callbacks.finishError("start smali search service", t);
        }
    }

    public void restore() {
        try {
            SharedPreferences sp = prefs();
            boolean running = sp.getBoolean(PermsTestSmaliJobService.PREF_SEARCH_RUNNING, false);
            String status = sp.getString(PermsTestSmaliJobService.PREF_SEARCH_STATUS, "");
            if (running) {
                callbacks.setSearchRunning(true, TextUtils.isEmpty(status) ? "Smali/Java search running..." : status);
                scheduleStatusPoll();
            } else if (sp.getBoolean(PermsTestSmaliJobService.PREF_SEARCH_HAS_RESULT, false)) {
                callbacks.setSearchRunning(false, status);
                callbacks.applyResults(parsePage(sp));
                sp.edit().putBoolean(PermsTestSmaliJobService.PREF_SEARCH_HAS_RESULT, false).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    public void scheduleStatusPoll() {
        try {
            if (handler == null) return;
            if (statusPoller != null) handler.removeCallbacks(statusPoller);
            statusPoller = () -> {
                try {
                    SharedPreferences sp = prefs();
                    boolean running = sp.getBoolean(PermsTestSmaliJobService.PREF_SEARCH_RUNNING, false);
                    String status = sp.getString(PermsTestSmaliJobService.PREF_SEARCH_STATUS, "");
                    if (running) {
                        callbacks.setSearchRunning(true, TextUtils.isEmpty(status) ? "Smali/Java search running..." : status);
                        handler.postDelayed(statusPoller, 800L);
                    } else {
                        callbacks.setSearchRunning(false, status);
                        String error = sp.getString(PermsTestSmaliJobService.PREF_SEARCH_ERROR, "");
                        if (!TextUtils.isEmpty(error)) {
                            sp.edit().putString(PermsTestSmaliJobService.PREF_SEARCH_ERROR, "").apply();
                            callbacks.finishError("smali search", new RuntimeException(error));
                        } else if (sp.getBoolean(PermsTestSmaliJobService.PREF_SEARCH_HAS_RESULT, false)) {
                            SmaliEditorSearch.Page page = parsePage(sp);
                            callbacks.applyResults(page);
                            sp.edit().putBoolean(PermsTestSmaliJobService.PREF_SEARCH_HAS_RESULT, false).apply();
                        }
                        statusPoller = null;
                    }
                } catch (Throwable ignored) {
                    statusPoller = null;
                }
            };
            handler.postDelayed(statusPoller, 500L);
        } catch (Throwable ignored) {
        }
    }

    private SmaliEditorSearch.Page parsePage(SharedPreferences sp) {
        ArrayList<SmaliEditorSearch.Result> results = new ArrayList<>();
        String rows = sp.getString(PermsTestSmaliJobService.PREF_SEARCH_ROWS, "");
        if (!TextUtils.isEmpty(rows)) {
            String[] lines = rows.split("\\n");
            for (String line : lines) {
                if (TextUtils.isEmpty(line)) continue;
                String[] parts = line.split("\\t", -1);
                if (parts.length < 4) continue;
                String path = decode(parts[0]);
                int lineNo = parseInt(parts[1], 1);
                String preview = decode(parts[2]);
                String label = decode(parts[3]);
                results.add(new SmaliEditorSearch.Result(new File(path), lineNo, preview, label));
            }
        }
        String query = sp.getString(PermsTestSmaliJobService.PREF_SEARCH_QUERY_TEXT, "");
        int scanned = sp.getInt(PermsTestSmaliJobService.PREF_SEARCH_SCANNED_FILES, 0);
        int total = sp.getInt(PermsTestSmaliJobService.PREF_SEARCH_TOTAL_RESULTS, 0);
        int pageIndex = sp.getInt(PermsTestSmaliJobService.PREF_SEARCH_PAGE_INDEX_RESULT, 0);
        int pageSize = sp.getInt(PermsTestSmaliJobService.PREF_SEARCH_PAGE_SIZE_RESULT, SmaliEditorSearch.DEFAULT_MAX_RESULTS);
        return new SmaliEditorSearch.Page(results, query, scanned, total, pageIndex, pageSize);
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PermsTestSmaliJobService.PREFS, Context.MODE_PRIVATE);
    }

    private static String joinCriteriaTerms(ArrayList<String> terms) {
        if (terms == null || terms.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String term : terms) {
            if (TextUtils.isEmpty(term)) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(term);
        }
        return sb.toString();
    }

    private static int parseInt(String text, int fallback) {
        try { return Integer.parseInt(text); } catch (Throwable ignored) { return fallback; }
    }

    private static String decode(String encoded) {
        try {
            if (TextUtils.isEmpty(encoded)) return "";
            byte[] data = Base64.decode(encoded, Base64.NO_WRAP);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }
}
