package dev.perms.test.packages.editor;

import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApkEditorWorkspacePatcher {
    public static final class RenameResult {
        public boolean success;
        public String oldPackage = "";
        public String newPackage = "";
        public int filesChanged;
        public int manifestChanges;
        public int labelFilesChanged;
        public int deepFilesChanged;
        public String message = "";
    }

    private static final Pattern MANIFEST_PACKAGE = Pattern.compile("(<manifest\\b[^>]*?\\bpackage\\s*=\\s*[\"'])([^\"']*)([\"'])", Pattern.DOTALL);
    private static final Pattern APPLICATION_TAG = Pattern.compile("<application\\b[^>]*>", Pattern.DOTALL);
    private static final Pattern APPLICATION_LABEL = Pattern.compile("(android:label\\s*=\\s*[\"'])([^\"']*)([\"'])");
    private static final Pattern RELATIVE_COMPONENT_NAME = Pattern.compile("(<(?:application|activity|activity-alias|service|receiver|provider|instrumentation)\\b[^>]*?\\bandroid:name\\s*=\\s*[\"'])\\.([^\"']*)([\"'])", Pattern.DOTALL);
    private static final Pattern AUTHORITIES_ATTRIBUTE = Pattern.compile("(android:authorities\\s*=\\s*[\"'])([^\"']*)([\"'])");

    private ApkEditorWorkspacePatcher() {
    }

    public static RenameResult renamePackageAndLabel(File decompiledDir,
                                                     String requestedOldPackage,
                                                     String requestedNewPackage,
                                                     String requestedAppName,
                                                     boolean quickPatchOnly,
                                                     boolean renameAuthorities) throws IOException {
        RenameResult result = new RenameResult();
        if (decompiledDir == null || !decompiledDir.isDirectory()) throw new IOException("Decompiled workspace is missing.");
        File manifest = new File(decompiledDir, "AndroidManifest.xml");
        if (!manifest.isFile()) throw new IOException("AndroidManifest.xml was not found in decompiled workspace.");
        String xml = readText(manifest);
        String oldPackage = cleanPackageName(requestedOldPackage);
        if (TextUtils.isEmpty(oldPackage)) oldPackage = readPackageName(xml);
        String newPackage = cleanPackageName(requestedNewPackage);
        String appName = requestedAppName == null ? "" : requestedAppName.trim();
        if (TextUtils.isEmpty(newPackage) && TextUtils.isEmpty(appName)) {
            result.message = "Enter a new package name and/or app name first.";
            return result;
        }
        if (!TextUtils.isEmpty(newPackage) && !isValidPackageName(newPackage)) {
            result.message = "New package name is not valid: " + newPackage;
            return result;
        }
        if (!TextUtils.isEmpty(newPackage) && TextUtils.isEmpty(oldPackage)) {
            result.message = "Could not detect the current package name. Run Inspect or enter a package that apktool can decode.";
            return result;
        }

        int manifestChanges = 0;
        String patched = xml;
        if (!TextUtils.isEmpty(newPackage) && !newPackage.equals(oldPackage)) {
            ReplaceResult packageReplace = replaceManifestPackage(patched, newPackage);
            patched = packageReplace.text;
            manifestChanges += packageReplace.count;
            ReplaceResult componentReplace = replaceRelativeComponentNames(patched, oldPackage);
            patched = componentReplace.text;
            manifestChanges += componentReplace.count;
            if (renameAuthorities) {
                ReplaceResult authReplace = replaceAuthorities(patched, oldPackage, newPackage);
                patched = authReplace.text;
                manifestChanges += authReplace.count;
            }
        }
        int labelFilesChanged = 0;
        if (!TextUtils.isEmpty(appName)) {
            LabelPatch labelPatch = patchAppLabel(decompiledDir, patched, appName);
            patched = labelPatch.manifestXml;
            manifestChanges += labelPatch.manifestChanges;
            labelFilesChanged = labelPatch.resourceFilesChanged;
        }
        if (!patched.equals(xml)) {
            writeText(manifest, patched);
            result.filesChanged++;
        }
        int deepFilesChanged = 0;
        if (!quickPatchOnly && !TextUtils.isEmpty(newPackage) && !newPackage.equals(oldPackage)) {
            deepFilesChanged = replaceDeepTextReferences(decompiledDir, oldPackage, newPackage);
        }
        result.success = true;
        result.oldPackage = oldPackage;
        result.newPackage = newPackage;
        result.manifestChanges = manifestChanges;
        result.labelFilesChanged = labelFilesChanged;
        result.deepFilesChanged = deepFilesChanged;
        result.filesChanged += labelFilesChanged + deepFilesChanged;
        StringBuilder msg = new StringBuilder();
        if (!TextUtils.isEmpty(newPackage) && !newPackage.equals(oldPackage)) {
            msg.append("Package ").append(oldPackage).append(" -> ").append(newPackage).append('.');
        }
        if (!TextUtils.isEmpty(appName)) {
            if (msg.length() > 0) msg.append(' ');
            msg.append("App name set to ").append(appName).append('.');
        }
        msg.append(" Changed files: ").append(result.filesChanged).append('.');
        result.message = msg.toString();
        return result;
    }

    public static String readPackageName(File decompiledDir) {
        if (decompiledDir == null) return "";
        File manifest = new File(decompiledDir, "AndroidManifest.xml");
        if (!manifest.isFile()) return "";
        try {
            return readPackageName(readText(manifest));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String readPackageName(String xml) {
        if (xml == null) return "";
        Matcher m = MANIFEST_PACKAGE.matcher(xml);
        return m.find() ? m.group(2) : "";
    }

    private static ReplaceResult replaceManifestPackage(String xml, String newPackage) {
        Matcher m = MANIFEST_PACKAGE.matcher(xml);
        StringBuffer sb = new StringBuffer(xml.length());
        int count = 0;
        while (m.find()) {
            count++;
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + newPackage + m.group(3)));
        }
        m.appendTail(sb);
        return new ReplaceResult(sb.toString(), count);
    }

    private static ReplaceResult replaceRelativeComponentNames(String xml, String oldPackage) {
        if (TextUtils.isEmpty(oldPackage)) return new ReplaceResult(xml, 0);
        Matcher m = RELATIVE_COMPONENT_NAME.matcher(xml);
        StringBuffer sb = new StringBuffer(xml.length());
        int count = 0;
        while (m.find()) {
            count++;
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + oldPackage + "." + m.group(2) + m.group(3)));
        }
        m.appendTail(sb);
        return new ReplaceResult(sb.toString(), count);
    }

    private static ReplaceResult replaceAuthorities(String xml, String oldPackage, String newPackage) {
        Matcher m = AUTHORITIES_ATTRIBUTE.matcher(xml);
        StringBuffer sb = new StringBuffer(xml.length());
        int count = 0;
        while (m.find()) {
            String value = m.group(2);
            String replacementValue = value.replace(oldPackage, newPackage);
            if (!replacementValue.equals(value)) count++;
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + replacementValue + m.group(3)));
        }
        m.appendTail(sb);
        return new ReplaceResult(sb.toString(), count);
    }

    private static LabelPatch patchAppLabel(File decompiledDir, String manifestXml, String appName) throws IOException {
        LabelPatch patch = new LabelPatch();
        patch.manifestXml = manifestXml;
        Matcher appMatcher = APPLICATION_TAG.matcher(manifestXml);
        if (!appMatcher.find()) return patch;
        String appTag = appMatcher.group();
        Matcher labelMatcher = APPLICATION_LABEL.matcher(appTag);
        if (labelMatcher.find()) {
            // Keep rename output installable with the native apktool-go path: the
            // current builder applies focused decoded-manifest edits back to the
            // preserved binary AndroidManifest.xml, but it does not compile
            // resources.arsc string-table edits yet. A literal manifest label is
            // therefore the safest targeted rename representation for now.
            String newTag = labelMatcher.replaceFirst(Matcher.quoteReplacement(labelMatcher.group(1) + escapeXml(appName) + labelMatcher.group(3)));
            patch.manifestXml = manifestXml.substring(0, appMatcher.start()) + newTag + manifestXml.substring(appMatcher.end());
            patch.manifestChanges = 1;
            return patch;
        }
        String newTag = appTag.substring(0, appTag.length() - 1) + " android:label=\"" + escapeXml(appName) + "\">";
        patch.manifestXml = manifestXml.substring(0, appMatcher.start()) + newTag + manifestXml.substring(appMatcher.end());
        patch.manifestChanges = 1;
        return patch;
    }

    private static int replaceStringResourceValues(File resDir, String stringName, String appName) throws IOException {
        if (resDir == null || !resDir.isDirectory() || TextUtils.isEmpty(stringName)) return 0;
        File[] dirs = resDir.listFiles();
        if (dirs == null) return 0;
        int changed = 0;
        Pattern p = Pattern.compile("(<string\\b(?=[^>]*\\bname\\s*=\\s*[\"']" + Pattern.quote(stringName) + "[\"'])[^>]*>)(.*?)(</string>)", Pattern.DOTALL);
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory() || !dir.getName().startsWith("values")) continue;
            File strings = new File(dir, "strings.xml");
            if (!strings.isFile()) continue;
            String text = readText(strings);
            Matcher m = p.matcher(text);
            StringBuffer sb = new StringBuffer(text.length());
            boolean fileChanged = false;
            while (m.find()) {
                fileChanged = true;
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + escapeXml(appName) + m.group(3)));
            }
            m.appendTail(sb);
            if (fileChanged) {
                writeText(strings, sb.toString());
                changed++;
            }
        }
        return changed;
    }

    private static int replaceDeepTextReferences(File root, String oldPackage, String newPackage) throws IOException {
        if (root == null || !root.isDirectory() || TextUtils.isEmpty(oldPackage) || TextUtils.isEmpty(newPackage)) return 0;
        String oldSlash = oldPackage.replace('.', '/');
        String newSlash = newPackage.replace('.', '/');
        int[] changed = new int[1];
        walk(root, file -> {
            String name = file.getName().toLowerCase(java.util.Locale.US);
            if (!(name.endsWith(".xml") || name.endsWith(".smali") || name.endsWith(".yml") || name.endsWith(".properties"))) return;
            String text = readText(file);
            String updated = text.replace(oldPackage, newPackage).replace(oldSlash, newSlash);
            if (!updated.equals(text)) {
                writeText(file, updated);
                changed[0]++;
            }
        });
        return changed[0];
    }

    private static void walk(File file, FileVisitor visitor) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isFile()) {
            visitor.visit(file);
            return;
        }
        File[] children = file.listFiles();
        if (children == null) return;
        for (File child : children) walk(child, visitor);
    }

    private static boolean isValidPackageName(String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        String[] parts = packageName.split("\\.");
        if (parts.length < 2) return false;
        for (String part : parts) {
            if (TextUtils.isEmpty(part)) return false;
            if (!Character.isLetter(part.charAt(0)) && part.charAt(0) != '_') return false;
            for (int i = 1; i < part.length(); i++) {
                char c = part.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') return false;
            }
        }
        return true;
    }

    private static String cleanPackageName(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String readText(File file) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String text) throws IOException {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file, false))) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class ReplaceResult {
        final String text;
        final int count;
        ReplaceResult(String text, int count) {
            this.text = text;
            this.count = count;
        }
    }

    private static final class LabelPatch {
        String manifestXml;
        int manifestChanges;
        int resourceFilesChanged;
    }

    private interface FileVisitor {
        void visit(File file) throws IOException;
    }
}
