package dev.perms.test.debugging.editor;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

import dev.perms.test.debugging.DebuggingWorkPaths;
import dev.perms.test.debugging.smali.PermsTestSmaliTools;

/**
 * Maps between generated jadx-go Java source files and baksmali source files.
 *
 * The Java output is used as a readable map. The smali output remains the editable
 * source for assemble/repack/install flows.
 */
public final class SourceBridge {
    public static final class Match {
        public final File file;
        public final String status;

        public Match(File file, String status) {
            this.file = file;
            this.status = status == null ? "" : status;
        }

        public boolean found() {
            return file != null && file.isFile();
        }
    }

    private SourceBridge() {
    }

    public static boolean isJavaFile(File file) {
        return hasExtension(file, ".java");
    }

    public static boolean isSmaliFile(File file) {
        return hasExtension(file, ".smali");
    }

    public static Match findMatchingSmali(File javaFile, String currentDexEntry, String currentWorkRoot) {
        try {
            File java = canonical(javaFile);
            if (java == null || !java.isFile() || !isJavaFile(java)) {
                return new Match(null, "Open a generated Java file first.");
            }
            String rel = relativeJavaClassPath(java);
            if (TextUtils.isEmpty(rel)) {
                return new Match(null, "Java file is not under a recognized jadx-go sources folder.");
            }
            String smaliRel = rel.substring(0, rel.length() - ".java".length()) + ".smali";
            String dex = readDexNameFromJavaHeader(java);
            if (TextUtils.isEmpty(dex)) dex = currentDexEntry;
            ArrayList<File> candidates = new ArrayList<>();
            addSmaliCandidate(candidates, currentWorkRoot, dex, smaliRel);
            addSmaliCandidate(candidates, workRootFromJava(java), dex, smaliRel);
            addSmaliSearchCandidates(candidates, workNameFromJava(java), smaliRel);
            addSmaliSearchCandidates(candidates, DebuggingWorkPaths.workNameFromRootSafe(currentWorkRoot), smaliRel);
            File found = firstExisting(candidates);
            if (found != null) {
                return new Match(found, "Matched Java to smali: " + compactPath(found));
            }
            return new Match(null, "No matching smali file found. Disassemble the matching DEX first, then try again.");
        } catch (Throwable t) {
            return new Match(null, "Java to smali match failed: " + safeMessage(t));
        }
    }

    public static Match findMatchingJava(File smaliFile, String javaOutDir, String currentWorkRoot) {
        try {
            File smali = canonical(smaliFile);
            if (smali == null || !smali.isFile() || !isSmaliFile(smali)) {
                return new Match(null, "Open an editable smali file first.");
            }
            String rel = relativeSmaliClassPath(smali);
            if (TextUtils.isEmpty(rel)) {
                return new Match(null, "Smali file is not under a recognized smali output folder.");
            }
            String javaRel = rel.substring(0, rel.length() - ".smali".length()) + ".java";
            ArrayList<File> candidates = new ArrayList<>();
            addJavaCandidate(candidates, javaOutDir, javaRel);
            addJavaCandidate(candidates, javaRootForWorkName(workNameFromSmali(smali)), javaRel);
            addJavaCandidate(candidates, javaRootForWorkName(DebuggingWorkPaths.workNameFromRootSafe(currentWorkRoot)), javaRel);
            File found = firstExisting(candidates);
            if (found != null) {
                return new Match(found, "Matched smali to Java map: " + compactPath(found));
            }
            return new Match(null, "No matching Java map found. Run DEX to Java for this APK/DEX first, then try again.");
        } catch (Throwable t) {
            return new Match(null, "Smali to Java match failed: " + safeMessage(t));
        }
    }

    public static String describe(File file, String javaOutDir, String currentDexEntry, String currentWorkRoot) {
        try {
            File target = canonical(file);
            if (target == null) return "Open a Java or smali file to enable source mapping.";
            if (isJavaFile(target)) {
                Match match = findMatchingSmali(target, currentDexEntry, currentWorkRoot);
                return match.found()
                        ? "Java map only. Matching editable smali is available."
                        : match.status;
            }
            if (isSmaliFile(target)) {
                Match match = findMatchingJava(target, javaOutDir, currentWorkRoot);
                return match.found()
                        ? "Editable smali. Matching generated Java map is available."
                        : match.status;
            }
        } catch (Throwable ignored) {
        }
        return "Open a Java or smali file to enable source mapping.";
    }

    public static String readDexNameFromJavaHeader(File javaFile) {
        File file = canonical(javaFile);
        if (file == null || !file.isFile()) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count++ < 32) {
                String trimmed = line.trim();
                if (trimmed.startsWith("* DEX:")) {
                    String value = trimmed.substring("* DEX:".length()).trim();
                    return PermsTestSmaliTools.normalizeDexEntryName(value);
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static void addSmaliCandidate(ArrayList<File> out, String workRoot, String dexEntry, String smaliRel) {
        if (out == null || TextUtils.isEmpty(smaliRel)) return;
        String root = TextUtils.isEmpty(workRoot) ? DebuggingWorkPaths.rootForWorkName(DebuggingWorkPaths.DEFAULT_WORK_NAME) : workRoot;
        String smaliDir = DebuggingWorkPaths.smaliDir(root, TextUtils.isEmpty(dexEntry) ? "classes.dex" : dexEntry);
        out.add(new File(smaliDir, smaliRel));
    }

    private static void addSmaliSearchCandidates(ArrayList<File> out, String workName, String smaliRel) {
        if (out == null || TextUtils.isEmpty(workName) || TextUtils.isEmpty(smaliRel)) return;
        File workDir = new File(PermsTestSmaliTools.DEFAULT_SMALI_ROOT, DebuggingWorkPaths.safeWorkName(workName));
        File[] children = workDir.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
        for (File child : children) {
            if (child != null && child.isDirectory() && isSmaliSourceFolder(child.getName())) {
                out.add(new File(child, smaliRel));
            }
        }
    }

    private static void addJavaCandidate(ArrayList<File> out, String javaRoot, String javaRel) {
        if (out == null || TextUtils.isEmpty(javaRoot) || TextUtils.isEmpty(javaRel)) return;
        out.add(new File(new File(javaRoot, "sources"), javaRel));
        out.add(new File(javaRoot, javaRel));
    }

    private static File firstExisting(ArrayList<File> candidates) {
        if (candidates == null) return null;
        for (File candidate : candidates) {
            File file = canonical(candidate);
            if (file != null && file.isFile()) return file;
        }
        return null;
    }

    private static String relativeJavaClassPath(File javaFile) {
        File file = canonical(javaFile);
        if (file == null) return "";
        String path = normalize(file.getAbsolutePath());
        int sources = path.indexOf("/sources/");
        if (sources >= 0 && sources + 9 < path.length()) return path.substring(sources + 9);
        String javaRoot = normalize(PermsTestSmaliTools.DEFAULT_JAVA_ROOT) + "/";
        int root = path.indexOf(javaRoot);
        if (root >= 0) {
            String rel = path.substring(root + javaRoot.length());
            int slash = rel.indexOf('/');
            if (slash >= 0 && slash + 1 < rel.length()) {
                rel = rel.substring(slash + 1);
                if (rel.startsWith("sources/")) rel = rel.substring("sources/".length());
                return rel;
            }
        }
        return "";
    }

    private static String relativeSmaliClassPath(File smaliFile) {
        File file = canonical(smaliFile);
        if (file == null) return "";
        String path = normalize(file.getAbsolutePath());
        String prefix = normalize(PermsTestSmaliTools.DEFAULT_SMALI_ROOT) + "/";
        int idx = path.indexOf(prefix);
        if (idx < 0) return "";
        String rel = path.substring(idx + prefix.length());
        int workSlash = rel.indexOf('/');
        if (workSlash < 0 || workSlash + 1 >= rel.length()) return "";
        rel = rel.substring(workSlash + 1);
        int folderSlash = rel.indexOf('/');
        if (folderSlash < 0 || folderSlash + 1 >= rel.length()) return "";
        String sourceFolder = rel.substring(0, folderSlash);
        if (!isSmaliSourceFolder(sourceFolder)) return "";
        return rel.substring(folderSlash + 1);
    }

    private static String workNameFromJava(File javaFile) {
        File file = canonical(javaFile);
        if (file == null) return "";
        String path = normalize(file.getAbsolutePath());
        String prefix = normalize(PermsTestSmaliTools.DEFAULT_JAVA_ROOT) + "/";
        int idx = path.indexOf(prefix);
        if (idx < 0) return "";
        String rel = path.substring(idx + prefix.length());
        int slash = rel.indexOf('/');
        return slash <= 0 ? "" : DebuggingWorkPaths.safeWorkName(rel.substring(0, slash));
    }

    private static String workRootFromJava(File javaFile) {
        String work = workNameFromJava(javaFile);
        return TextUtils.isEmpty(work) ? "" : DebuggingWorkPaths.rootForWorkName(work);
    }

    private static String workNameFromSmali(File smaliFile) {
        File file = canonical(smaliFile);
        if (file == null) return "";
        String path = normalize(file.getAbsolutePath());
        String prefix = normalize(PermsTestSmaliTools.DEFAULT_SMALI_ROOT) + "/";
        int idx = path.indexOf(prefix);
        if (idx < 0) return "";
        String rel = path.substring(idx + prefix.length());
        int slash = rel.indexOf('/');
        return slash <= 0 ? "" : DebuggingWorkPaths.safeWorkName(rel.substring(0, slash));
    }

    private static String javaRootForWorkName(String workName) {
        if (TextUtils.isEmpty(workName)) return "";
        return PermsTestSmaliTools.DEFAULT_JAVA_ROOT + "/" + DebuggingWorkPaths.safeWorkName(workName);
    }

    private static boolean isSmaliSourceFolder(String name) {
        if (TextUtils.isEmpty(name)) return false;
        String lower = name.toLowerCase(Locale.US);
        return "smali".equals(lower) || lower.startsWith("smali_classes");
    }

    private static boolean hasExtension(File file, String extension) {
        if (file == null || TextUtils.isEmpty(extension)) return false;
        String name = file.getName();
        return name != null && name.toLowerCase(Locale.US).endsWith(extension.toLowerCase(Locale.US));
    }

    private static File canonical(File file) {
        try {
            return file == null ? null : file.getCanonicalFile();
        } catch (Throwable ignored) {
            return file;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static String compactPath(File file) {
        String path = normalize(file == null ? "" : file.getAbsolutePath());
        String root = normalize(PermsTestSmaliTools.DEFAULT_ROOT) + "/";
        if (path.startsWith(root)) return path.substring(root.length());
        return path;
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.getClass().getSimpleName() : msg;
    }
}
