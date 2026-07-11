package dev.perms.test.debugging.editor;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.debugging.jobs.SmaliEditorSearchJobClient;
import dev.perms.test.debugging.smali.PermsTestSmaliTools;
import dev.perms.test.ui.DropdownUi;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Owns the Smali/Java search state and UI wiring for the Debugging tab editor.
 * MainActivity supplies only the small pieces of app state needed to resolve roots
 * and open a selected result.
 */
public final class SmaliEditorSearchController {
    private static final String KEY_SMALI_SEARCH_MAX_FILES = "smali_search_max_files";
    private static final String KEY_SMALI_SEARCH_MAX_RESULTS = "smali_search_max_results";
    private static final String KEY_SMALI_SEARCH_LIMITS_VERSION = "smali_search_limits_version";
    private static final String KEY_SMALI_SEARCH_FILTER = "smali_search_filter";
    private static final String KEY_SMALI_SEARCH_EXCLUDE_ENABLED = "smali_search_exclude_enabled";
    private static final String KEY_SMALI_SEARCH_EXCLUDE_TERMS = "smali_search_exclude_terms";
    private static final int SMALI_SEARCH_LIMITS_VERSION = 2;

    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        SharedPreferences getPreferences();
        Handler getMainHandler();
        String currentDebuggingWorkRoot();
        ArrayList<String> currentDebuggingDexEntries();
        String debuggingSmaliDirForEntry(String entry, boolean preferCurrentField);
        void openSearchResult(SmaliEditorSearch.Result result);
        void setSearchStatus(String status);
        void finishSearchError(String label, Throwable error);
    }

    private final Host host;
    private ArrayAdapter<String> adapter;
    private final ArrayList<SmaliEditorSearch.Result> results = new ArrayList<>();
    private final ArrayList<String> labels = new ArrayList<>();
    private int pageIndex = 0;
    private int lastTotal = 0;
    private int lastPageSize = SmaliEditorSearch.DEFAULT_MAX_RESULTS;
    private boolean inFlight = false;
    private SmaliEditorSearchJobClient jobClient;

    public SmaliEditorSearchController(Host host) {
        this.host = host;
    }

    public void setup() {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null) return;
            if (b.tabDebugging.lstSmaliEditorResults != null) {
                adapter = new ArrayAdapter<>(host.getActivity(), android.R.layout.simple_list_item_1, labels);
                b.tabDebugging.lstSmaliEditorResults.setAdapter(adapter);
                b.tabDebugging.lstSmaliEditorResults.setOnItemClickListener((parent, view, position, id) -> {
                    try {
                        if (position < 0 || position >= results.size()) return;
                        host.openSearchResult(results.get(position));
                    } catch (Throwable t) {
                        host.finishSearchError("open result", t);
                    }
                });
            }
            if (b.tabDebugging.btnSmaliEditorSearch != null) {
                b.tabDebugging.btnSmaliEditorSearch.setOnClickListener(v -> runSearch());
            }
            if (b.tabDebugging.btnSmaliEditorPrevPage != null) {
                b.tabDebugging.btnSmaliEditorPrevPage.setOnClickListener(v -> showPage(pageIndex - 1));
            }
            if (b.tabDebugging.btnSmaliEditorNextPage != null) {
                b.tabDebugging.btnSmaliEditorNextPage.setOnClickListener(v -> showPage(pageIndex + 1));
            }
            updatePageButtons();
            if (b.tabDebugging.btnSmaliEditorClear != null) {
                b.tabDebugging.btnSmaliEditorClear.setOnClickListener(v -> clear());
            }
            restoreOptions();
        } catch (Throwable t) {
            host.finishSearchError("search setup", t);
        }
    }

    public void restoreJobStatus() {
        jobClient().restore();
    }

    public ArrayList<File> searchRoots() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayList<File> roots = new ArrayList<>();
        try {
            ActivityMainBinding b = binding();
            addSearchRoot(roots, seen, safeText(b == null || b.tabDebugging == null ? null : b.tabDebugging.edtSmaliOutDir));
            addSearchRoot(roots, seen, safeText(b == null || b.tabDebugging == null ? null : b.tabDebugging.edtJadxJavaOutDir));
            addSearchRoot(roots, seen, PermsTestSmaliTools.DEFAULT_SMALI_ROOT);
            addSearchRoot(roots, seen, PermsTestSmaliTools.DEFAULT_JAVA_ROOT);
            addSearchRoot(roots, seen, safeText(b == null || b.tabDebugging == null ? null : b.tabDebugging.edtSmaliInputDir));
            for (String entry : host.currentDebuggingDexEntries()) {
                addSearchRoot(roots, seen, host.debuggingSmaliDirForEntry(entry, true));
            }
        } catch (Throwable ignored) {
        }
        return roots;
    }

    public ArrayList<String> searchRootPaths() {
        ArrayList<String> out = new ArrayList<>();
        try {
            for (File root : searchRoots()) {
                if (root != null) out.add(root.getAbsolutePath());
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public void setSearchRunning(boolean running) {
        inFlight = running;
        updatePageButtons();
    }

    private void restoreOptions() {
        try {
            SharedPreferences sp = host.getPreferences();
            int storedVersion = sp.getInt(KEY_SMALI_SEARCH_LIMITS_VERSION, 1);
            int maxFiles = sp.getInt(KEY_SMALI_SEARCH_MAX_FILES, SmaliEditorSearch.DEFAULT_MAX_FILES);
            int maxResults = sp.getInt(KEY_SMALI_SEARCH_MAX_RESULTS, SmaliEditorSearch.DEFAULT_MAX_RESULTS);
            int filterMode = SmaliEditorSearch.normalizeFilterMode(sp.getInt(KEY_SMALI_SEARCH_FILTER, SmaliEditorSearch.FILTER_CONTAINS_IGNORE_CASE));
            boolean excludeEnabled = sp.getBoolean(KEY_SMALI_SEARCH_EXCLUDE_ENABLED, false);
            String excludeTerms = sp.getString(KEY_SMALI_SEARCH_EXCLUDE_TERMS, "");
            if (storedVersion < SMALI_SEARCH_LIMITS_VERSION && maxFiles == 30000) {
                maxFiles = SmaliEditorSearch.DEFAULT_MAX_FILES;
                sp.edit()
                        .putInt(KEY_SMALI_SEARCH_MAX_FILES, maxFiles)
                        .putInt(KEY_SMALI_SEARCH_LIMITS_VERSION, SMALI_SEARCH_LIMITS_VERSION)
                        .apply();
            }
            ActivityMainBinding b = binding();
            if (b != null && b.tabDebugging != null) {
                if (b.tabDebugging.ddSmaliSearchFilter != null) {
                    ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(host.getActivity(),
                            android.R.layout.simple_dropdown_item_1line, SmaliEditorSearch.FILTER_LABELS);
                    b.tabDebugging.ddSmaliSearchFilter.setAdapter(filterAdapter);
                    DropdownUi.bindExposedDropdown(host.getActivity(), b.tabDebugging.tilSmaliSearchFilter,
                            b.tabDebugging.ddSmaliSearchFilter,
                            () -> DropdownUi.showDropdown(b.tabDebugging.ddSmaliSearchFilter));
                    b.tabDebugging.ddSmaliSearchFilter.setText(SmaliEditorSearch.FILTER_LABELS[filterMode], false);
                }
                if (b.tabDebugging.chkSmaliSearchExclude != null) {
                    b.tabDebugging.chkSmaliSearchExclude.setChecked(excludeEnabled);
                    b.tabDebugging.chkSmaliSearchExclude.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        updateExcludeInputState(isChecked);
                        try {
                            host.getPreferences().edit()
                                    .putBoolean(KEY_SMALI_SEARCH_EXCLUDE_ENABLED, isChecked)
                                    .apply();
                        } catch (Throwable ignored) {
                        }
                    });
                }
                if (b.tabDebugging.edtSmaliSearchExcludeTerms != null) {
                    b.tabDebugging.edtSmaliSearchExcludeTerms.setText(excludeTerms);
                }
                updateExcludeInputState(excludeEnabled);
                if (b.tabDebugging.edtSmaliSearchMaxFiles != null) {
                    b.tabDebugging.edtSmaliSearchMaxFiles.setText(String.valueOf(maxFiles));
                }
                if (b.tabDebugging.edtSmaliSearchMaxResults != null) {
                    b.tabDebugging.edtSmaliSearchMaxResults.setText(String.valueOf(maxResults));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void updateExcludeInputState(boolean enabled) {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null || b.tabDebugging.edtSmaliSearchExcludeTerms == null) return;
            b.tabDebugging.edtSmaliSearchExcludeTerms.setEnabled(enabled);
            b.tabDebugging.edtSmaliSearchExcludeTerms.setAlpha(enabled ? 1.0f : 0.55f);
        } catch (Throwable ignored) {
        }
    }

    private int readLimit(TextView view, int fallback, int min, int max) {
        try {
            String text = safeText(view);
            int value = Integer.parseInt(text);
            return Math.max(min, Math.min(max, value));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void persistOptions(int maxFiles, int maxResults, SmaliEditorSearch.Criteria criteria, String excludeText) {
        try {
            SharedPreferences.Editor editor = host.getPreferences().edit()
                    .putInt(KEY_SMALI_SEARCH_MAX_FILES, maxFiles)
                    .putInt(KEY_SMALI_SEARCH_MAX_RESULTS, maxResults)
                    .putInt(KEY_SMALI_SEARCH_LIMITS_VERSION, SMALI_SEARCH_LIMITS_VERSION);
            if (criteria != null) {
                editor.putInt(KEY_SMALI_SEARCH_FILTER, criteria.filterMode)
                        .putBoolean(KEY_SMALI_SEARCH_EXCLUDE_ENABLED, criteria.excludeEnabled);
            }
            editor.putString(KEY_SMALI_SEARCH_EXCLUDE_TERMS, excludeText == null ? "" : excludeText.trim())
                    .apply();
            ActivityMainBinding b = binding();
            if (b != null && b.tabDebugging != null) {
                if (b.tabDebugging.edtSmaliSearchMaxFiles != null) {
                    b.tabDebugging.edtSmaliSearchMaxFiles.setText(String.valueOf(maxFiles));
                }
                if (b.tabDebugging.edtSmaliSearchMaxResults != null) {
                    b.tabDebugging.edtSmaliSearchMaxResults.setText(String.valueOf(maxResults));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void runSearch() {
        pageIndex = 0;
        runPage(0);
    }

    private void showPage(int requestedPage) {
        if (inFlight) return;
        int pageSize = Math.max(1, lastPageSize);
        int lastPage = lastTotal <= 0 ? 0 : ((lastTotal - 1) / pageSize);
        int nextPage = Math.max(0, Math.min(requestedPage, lastPage));
        if (nextPage == pageIndex && !results.isEmpty()) return;
        runPage(nextPage);
    }

    private void runPage(int requestedPage) {
        ActivityMainBinding b = binding();
        if (b == null || b.tabDebugging == null) return;
        if (inFlight) return;
        final String query = safeText(b.tabDebugging.edtSmaliEditorSearch);
        final int maxFiles = readLimit(b.tabDebugging.edtSmaliSearchMaxFiles,
                SmaliEditorSearch.DEFAULT_MAX_FILES, 0, 200000);
        final int maxResults = readLimit(b.tabDebugging.edtSmaliSearchMaxResults,
                SmaliEditorSearch.DEFAULT_MAX_RESULTS, 1, 50000);
        final int filterMode = readFilterMode();
        final boolean excludeEnabled = b.tabDebugging.chkSmaliSearchExclude != null
                && b.tabDebugging.chkSmaliSearchExclude.isChecked();
        final String excludeText = safeText(b.tabDebugging.edtSmaliSearchExcludeTerms);
        final SmaliEditorSearch.Criteria criteria = new SmaliEditorSearch.Criteria(query, filterMode,
                excludeEnabled, SmaliEditorSearch.splitExcludeTerms(excludeText, SmaliEditorSearch.isFilterCaseSensitive(filterMode)));
        final int safePage = Math.max(0, requestedPage);
        persistOptions(maxFiles, maxResults, criteria, excludeText);
        inFlight = true;
        pageIndex = safePage;
        lastPageSize = maxResults;
        updatePageButtons();
        host.setSearchStatus(safePage <= 0 ? "Searching smali/java folders..." : "Searching smali/java page " + (safePage + 1) + "...");
        jobClient().start(searchRootPaths(), host.currentDebuggingWorkRoot(), criteria, maxFiles, maxResults, safePage);
    }

    private int readFilterMode() {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null || b.tabDebugging.ddSmaliSearchFilter == null) {
                return SmaliEditorSearch.FILTER_CONTAINS_IGNORE_CASE;
            }
            String label = safeText(b.tabDebugging.ddSmaliSearchFilter);
            for (int i = 0; i < SmaliEditorSearch.FILTER_LABELS.length; i++) {
                if (SmaliEditorSearch.FILTER_LABELS[i].equals(label)) return i;
            }
        } catch (Throwable ignored) {
        }
        return SmaliEditorSearch.FILTER_CONTAINS_IGNORE_CASE;
    }

    private void addSearchRoot(ArrayList<File> roots, LinkedHashSet<String> seen, String path) {
        try {
            if (TextUtils.isEmpty(path)) return;
            File f = new File(path.trim());
            if (f.isFile()) f = f.getParentFile();
            if (f == null) return;
            String key;
            try {
                key = f.getCanonicalPath();
            } catch (Throwable ignored) {
                key = f.getAbsolutePath();
            }
            if (TextUtils.isEmpty(key) || seen.contains(key)) return;
            seen.add(key);
            roots.add(f);
        } catch (Throwable ignored) {
        }
    }

    private String makeLabel(File file, int line, String preview) {
        String rel = displayPath(file);
        if (line > 0 && !TextUtils.isEmpty(preview)) {
            return rel + ":" + line + "  " + preview;
        }
        if (line > 0) {
            return rel + ":" + line;
        }
        return rel;
    }

    private String displayPath(File file) {
        if (file == null) return "";
        String path = file.getAbsolutePath();
        try {
            String root = host.currentDebuggingWorkRoot();
            if (!TextUtils.isEmpty(root) && path.startsWith(root + File.separator)) {
                return path.substring(root.length() + 1);
            }
            String sharedRoot = PermsTestSmaliTools.DEFAULT_ROOT;
            if (!TextUtils.isEmpty(sharedRoot) && path.startsWith(sharedRoot + File.separator)) {
                return path.substring(sharedRoot.length() + 1);
            }
        } catch (Throwable ignored) {
        }
        return path;
    }

    private void applyResults(SmaliEditorSearch.Page page) {
        try {
            inFlight = false;
            results.clear();
            labels.clear();
            if (page != null) {
                pageIndex = page.pageIndex;
                lastTotal = page.totalResults;
                lastPageSize = page.pageSize;
                results.addAll(page.results);
                for (SmaliEditorSearch.Result result : page.results) labels.add(result.label);
            } else {
                pageIndex = 0;
                lastTotal = 0;
                lastPageSize = SmaliEditorSearch.DEFAULT_MAX_RESULTS;
            }
            ActivityMainBinding b = binding();
            if (adapter == null && b != null && b.tabDebugging != null && b.tabDebugging.lstSmaliEditorResults != null) {
                adapter = new ArrayAdapter<>(host.getActivity(), android.R.layout.simple_list_item_1, labels);
                b.tabDebugging.lstSmaliEditorResults.setAdapter(adapter);
            } else if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            try {
                if (b != null && b.tabDebugging != null && b.tabDebugging.lstSmaliEditorResults != null) {
                    b.tabDebugging.lstSmaliEditorResults.setSelection(0);
                }
            } catch (Throwable ignored) {
            }
            updatePageButtons();

            String mode = page == null || TextUtils.isEmpty(page.query) ? "files" : "matches";
            int total = page == null ? 0 : page.totalResults;
            int scannedFiles = page == null ? 0 : page.scannedFiles;
            if (total <= 0) {
                host.setSearchStatus("Found 0 " + mode + " in " + scannedFiles + " smali/java files.");
                return;
            }
            int first = pageIndex * lastPageSize + 1;
            int last = Math.min(total, first + results.size() - 1);
            int pages = Math.max(1, (total + lastPageSize - 1) / lastPageSize);
            host.setSearchStatus("Showing " + first + "-" + last + " of " + total + " " + mode
                    + " (page " + (pageIndex + 1) + "/" + pages + ") in "
                    + scannedFiles + " smali/java files.");
        } catch (Throwable t) {
            inFlight = false;
            updatePageButtons();
            host.finishSearchError("search results", t);
        }
    }

    private void updatePageButtons() {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null) return;
            boolean hasPrevious = !inFlight && pageIndex > 0;
            boolean hasNext = !inFlight
                    && lastTotal > 0
                    && ((pageIndex + 1) * Math.max(1, lastPageSize)) < lastTotal;
            if (b.tabDebugging.btnSmaliEditorPrevPage != null) b.tabDebugging.btnSmaliEditorPrevPage.setEnabled(hasPrevious);
            if (b.tabDebugging.btnSmaliEditorNextPage != null) b.tabDebugging.btnSmaliEditorNextPage.setEnabled(hasNext);
        } catch (Throwable ignored) {
        }
    }

    private void clear() {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null) return;
            if (b.tabDebugging.edtSmaliEditorSearch != null) b.tabDebugging.edtSmaliEditorSearch.setText("");
            results.clear();
            labels.clear();
            pageIndex = 0;
            lastTotal = 0;
            lastPageSize = SmaliEditorSearch.DEFAULT_MAX_RESULTS;
            inFlight = false;
            if (adapter != null) adapter.notifyDataSetChanged();
            updatePageButtons();
            host.setSearchStatus("Search cleared.");
        } catch (Throwable ignored) {
        }
    }

    private SmaliEditorSearchJobClient jobClient() {
        if (jobClient == null) {
            jobClient = new SmaliEditorSearchJobClient(host.getActivity(), host.getMainHandler(), new SmaliEditorSearchJobClient.Callbacks() {
                @Override
                public void setSearchRunning(boolean running, String status) {
                    inFlight = running;
                    if (!TextUtils.isEmpty(status)) host.setSearchStatus(status);
                    updatePageButtons();
                }

                @Override
                public void applyResults(SmaliEditorSearch.Page page) {
                    SmaliEditorSearchController.this.applyResults(page);
                }

                @Override
                public void finishError(String label, Throwable error) {
                    host.finishSearchError(label, error);
                }
            });
        }
        return jobClient;
    }

    private ActivityMainBinding binding() {
        return host.getBinding();
    }

    private static String safeText(TextView tv) {
        try {
            CharSequence cs = tv == null ? null : tv.getText();
            return cs == null ? "" : cs.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
