package dev.perms.test.debugging.editor;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SmaliEditorSearch {
    public static final int DEFAULT_MAX_FILES = 0;
    public static final int DEFAULT_MAX_RESULTS = 2000;

    public static final int FILTER_CONTAINS_IGNORE_CASE = 0;
    public static final int FILTER_CONTAINS_MATCH_CASE = 1;
    public static final int FILTER_WHOLE_PHRASE_IGNORE_CASE = 2;
    public static final int FILTER_WHOLE_PHRASE_MATCH_CASE = 3;
    public static final int FILTER_ANY_WORD_IGNORE_CASE = 4;
    public static final int FILTER_ANY_WORD_MATCH_CASE = 5;
    public static final int FILTER_ALL_WORDS_IGNORE_CASE = 6;
    public static final int FILTER_ALL_WORDS_MATCH_CASE = 7;
    public static final int FILTER_WHOLE_WORD_IGNORE_CASE = 8;
    public static final int FILTER_WHOLE_WORD_MATCH_CASE = 9;

    public static final String[] FILTER_LABELS = new String[]{
            "Contains text (ignore case)",
            "Contains text (match case)",
            "Whole phrase (ignore case)",
            "Whole phrase (match case)",
            "Any word (ignore case)",
            "Any word (match case)",
            "All words (ignore case)",
            "All words (match case)",
            "Whole word (ignore case)",
            "Whole word (match case)"
    };

    public interface LabelFormatter {
        String format(File file, int line, String preview);
    }

    public static final class Result {
        public final File file;
        public final int line;
        public final String preview;
        public final String label;

        public Result(File file, int line, String preview, String label) {
            this.file = file;
            this.line = line;
            this.preview = preview == null ? "" : preview;
            this.label = label == null ? "" : label;
        }
    }

    public static final class Page {
        public final ArrayList<Result> results;
        public final String query;
        public final int scannedFiles;
        public final int totalResults;
        public final int pageIndex;
        public final int pageSize;

        public Page(ArrayList<Result> results, String query, int scannedFiles,
             int totalResults, int pageIndex, int pageSize) {
            this.results = results == null ? new ArrayList<>() : results;
            this.query = query == null ? "" : query;
            this.scannedFiles = Math.max(0, scannedFiles);
            this.totalResults = Math.max(0, totalResults);
            this.pageIndex = Math.max(0, pageIndex);
            this.pageSize = Math.max(1, pageSize);
        }
    }

    public static final class Criteria {
        public final String query;
        public final int filterMode;
        public final boolean matchCase;
        public final String compareQuery;
        public final ArrayList<String> queryTerms;
        public final boolean excludeEnabled;
        public final ArrayList<String> excludeTerms;

        public Criteria(String query, int filterMode, boolean excludeEnabled, ArrayList<String> excludeTerms) {
            this.query = query == null ? "" : query.trim();
            this.filterMode = normalizeFilterMode(filterMode);
            this.matchCase = isFilterCaseSensitive(this.filterMode);
            this.compareQuery = normalizeText(this.query, this.matchCase);
            this.queryTerms = splitSearchTerms(this.query, this.matchCase);
            this.excludeEnabled = excludeEnabled;
            this.excludeTerms = excludeTerms == null ? new ArrayList<>() : excludeTerms;
        }

        boolean isListOnly() {
            return TextUtils.isEmpty(query);
        }
    }

    private static final class SearchCount {
        int count;
    }

    private SmaliEditorSearch() {
    }

    public static Page buildPage(List<File> roots, Criteria criteria, int maxFiles, int pageSize,
                          int requestedPage, LabelFormatter labelFormatter) {
        ArrayList<File> files = new ArrayList<>();
        if (roots != null) {
            for (File root : roots) {
                collectSmaliFiles(root, files, maxFiles);
                if (maxFiles > 0 && files.size() >= maxFiles) break;
            }
        }

        int safePageSize = Math.max(1, pageSize);
        int pageIndex = Math.max(0, requestedPage);
        Criteria safeCriteria = criteria == null
                ? new Criteria("", FILTER_CONTAINS_IGNORE_CASE, false, null)
                : criteria;
        int totalResults;

        if (safeCriteria.isListOnly()) {
            totalResults = files.size();
            if (totalResults > 0) pageIndex = Math.min(pageIndex, (totalResults - 1) / safePageSize);
            ArrayList<Result> pageResults = new ArrayList<>();
            int start = pageIndex * safePageSize;
            int end = Math.min(totalResults, start + safePageSize);
            for (int i = start; i < end; i++) {
                pageResults.add(makeResult(files.get(i), 1, "", labelFormatter));
            }
            return new Page(pageResults, safeCriteria.query, files.size(), totalResults, pageIndex, safePageSize);
        }

        SearchCount count = countMatches(files, safeCriteria);
        totalResults = count.count;
        if (totalResults > 0) pageIndex = Math.min(pageIndex, (totalResults - 1) / safePageSize);
        ArrayList<Result> pageResults = collectResultPage(files, safeCriteria,
                pageIndex * safePageSize, safePageSize, labelFormatter);
        return new Page(pageResults, safeCriteria.query, files.size(), totalResults, pageIndex, safePageSize);
    }

    public static int normalizeFilterMode(int mode) {
        if (mode < 0 || mode >= FILTER_LABELS.length) return FILTER_CONTAINS_IGNORE_CASE;
        return mode;
    }

    public static boolean isFilterCaseSensitive(int mode) {
        mode = normalizeFilterMode(mode);
        return mode == FILTER_CONTAINS_MATCH_CASE
                || mode == FILTER_WHOLE_PHRASE_MATCH_CASE
                || mode == FILTER_ANY_WORD_MATCH_CASE
                || mode == FILTER_ALL_WORDS_MATCH_CASE
                || mode == FILTER_WHOLE_WORD_MATCH_CASE;
    }

    public static ArrayList<String> splitExcludeTerms(String text, boolean matchCase) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        String[] parts = text.split(",");
        for (String part : parts) {
            String term = part == null ? "" : part.trim();
            if (TextUtils.isEmpty(term)) continue;
            out.add(normalizeText(term, matchCase));
        }
        return out;
    }

    private static void collectSmaliFiles(File root, ArrayList<File> out, int max) {
        if (root == null || out == null) return;
        if (max > 0 && out.size() >= max) return;
        if (!root.exists()) return;
        if (root.isFile()) {
            if (isSmaliOrJavaFile(root)) out.add(root);
            return;
        }
        File[] children = root.listFiles();
        if (children == null) return;
        try { Arrays.sort(children, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName())); } catch (Throwable ignored) {}
        for (File child : children) {
            if (max > 0 && out.size() >= max) return;
            if (child == null) continue;
            if (child.isDirectory()) collectSmaliFiles(child, out, max);
            else if (isSmaliOrJavaFile(child)) out.add(child);
        }
    }

    private static boolean isSmaliOrJavaFile(File file) {
        if (file == null || file.getName() == null) return false;
        String lower = file.getName().toLowerCase(Locale.US);
        return lower.endsWith(".smali") || lower.endsWith(".java");
    }

    private static SearchCount countMatches(ArrayList<File> files, Criteria criteria) {
        SearchCount out = new SearchCount();
        if (files == null || criteria == null || criteria.isListOnly()) return out;
        for (File file : files) {
            if (file == null) continue;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (matchesCriteria(line, criteria)) out.count++;
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static ArrayList<Result> collectResultPage(ArrayList<File> files, Criteria criteria, int offset,
                                                       int pageSize, LabelFormatter labelFormatter) {
        ArrayList<Result> results = new ArrayList<>();
        if (files == null || criteria == null || criteria.isListOnly() || pageSize <= 0) return results;
        int seen = 0;
        for (File file : files) {
            if (file == null || results.size() >= pageSize) break;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                int lineNo = 0;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    if (!matchesCriteria(line, criteria)) continue;
                    if (seen >= offset && results.size() < pageSize) {
                        results.add(makeResult(file, lineNo, line.trim(), labelFormatter));
                    }
                    seen++;
                    if (results.size() >= pageSize) break;
                }
            } catch (Throwable ignored) {}
        }
        return results;
    }

    private static Result makeResult(File file, int line, String preview, LabelFormatter labelFormatter) {
        String label = labelFormatter == null ? "" : labelFormatter.format(file, line, preview);
        return new Result(file, line, preview, label);
    }

    private static ArrayList<String> splitSearchTerms(String text, boolean matchCase) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        String[] parts = text.trim().split("\\s+");
        for (String part : parts) {
            String term = part == null ? "" : part.trim();
            if (TextUtils.isEmpty(term)) continue;
            out.add(normalizeText(term, matchCase));
        }
        return out;
    }

    private static String normalizeText(String text, boolean matchCase) {
        String value = text == null ? "" : text;
        return matchCase ? value : value.toLowerCase(Locale.US);
    }

    private static boolean matchesCriteria(String line, Criteria criteria) {
        if (line == null || criteria == null || criteria.isListOnly()) return false;
        String haystack = normalizeText(line, criteria.matchCase);
        if (criteria.excludeEnabled && !criteria.excludeTerms.isEmpty()) {
            for (String term : criteria.excludeTerms) {
                if (!TextUtils.isEmpty(term) && haystack.contains(term)) return false;
            }
        }

        switch (normalizeFilterMode(criteria.filterMode)) {
            case FILTER_WHOLE_PHRASE_IGNORE_CASE:
            case FILTER_WHOLE_PHRASE_MATCH_CASE:
                return containsNormalizedPhrase(haystack, criteria.compareQuery);
            case FILTER_ANY_WORD_IGNORE_CASE:
            case FILTER_ANY_WORD_MATCH_CASE:
                return containsAnyTerm(haystack, criteria.queryTerms);
            case FILTER_ALL_WORDS_IGNORE_CASE:
            case FILTER_ALL_WORDS_MATCH_CASE:
                return containsAllTerms(haystack, criteria.queryTerms);
            case FILTER_WHOLE_WORD_IGNORE_CASE:
            case FILTER_WHOLE_WORD_MATCH_CASE:
                return containsAllWholeWords(haystack, criteria.queryTerms);
            case FILTER_CONTAINS_MATCH_CASE:
            case FILTER_CONTAINS_IGNORE_CASE:
            default:
                return haystack.contains(criteria.compareQuery);
        }
    }

    private static boolean containsNormalizedPhrase(String haystack, String phrase) {
        if (TextUtils.isEmpty(haystack) || TextUtils.isEmpty(phrase)) return false;
        String normalizedHaystack = haystack.replaceAll("\\s+", " ");
        String normalizedPhrase = phrase.trim().replaceAll("\\s+", " ");
        return normalizedHaystack.contains(normalizedPhrase);
    }

    private static boolean containsAnyTerm(String haystack, ArrayList<String> terms) {
        if (TextUtils.isEmpty(haystack) || terms == null || terms.isEmpty()) return false;
        for (String term : terms) {
            if (!TextUtils.isEmpty(term) && haystack.contains(term)) return true;
        }
        return false;
    }

    private static boolean containsAllTerms(String haystack, ArrayList<String> terms) {
        if (TextUtils.isEmpty(haystack) || terms == null || terms.isEmpty()) return false;
        for (String term : terms) {
            if (TextUtils.isEmpty(term) || !haystack.contains(term)) return false;
        }
        return true;
    }

    private static boolean containsAllWholeWords(String haystack, ArrayList<String> terms) {
        if (TextUtils.isEmpty(haystack) || terms == null || terms.isEmpty()) return false;
        for (String term : terms) {
            if (TextUtils.isEmpty(term) || !containsWholeWord(haystack, term)) return false;
        }
        return true;
    }

    private static boolean containsWholeWord(String haystack, String word) {
        if (TextUtils.isEmpty(haystack) || TextUtils.isEmpty(word)) return false;
        int from = 0;
        while (from <= haystack.length() - word.length()) {
            int index = haystack.indexOf(word, from);
            if (index < 0) return false;
            int before = index - 1;
            int after = index + word.length();
            if ((before < 0 || !isWordChar(haystack.charAt(before)))
                    && (after >= haystack.length() || !isWordChar(haystack.charAt(after)))) {
                return true;
            }
            from = index + Math.max(1, word.length());
        }
        return false;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
