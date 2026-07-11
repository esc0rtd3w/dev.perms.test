package dev.perms.test.debugging.mitm;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small decoded-project patch helper for Debugging tab APK patch templates.
 *
 * This class intentionally works on apktool-decoded project files only. APK
 * decode/build/sign/export stays in MainActivity so privileged shell execution
 * and existing signing/export helpers remain centralized.
 */
public final class PermsTestMitmPatchTool {
    static final String NETWORK_CONFIG_NAME = "perms_mitm_network_security_config";
    static final String NETWORK_CONFIG_REF = "@xml/" + NETWORK_CONFIG_NAME;

    public static final class Options {
        public boolean trustUserCerts;
        public boolean trustSystemCerts;
        public boolean allowCleartext;
        public boolean makeDebuggable;
        public boolean allowBackup;
        public boolean applyNetworkSecurityConfig;
        public boolean patchCertificatePinning;
    }

    public static final class Result {
        public final boolean success;
        public final String message;
        public final String report;

        Result(boolean success, String message, String report) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.report = report == null ? "" : report;
        }
    }

    private PermsTestMitmPatchTool() {
    }

    public static Result patchDecodedProject(File decodedDir, Options options) {
        StringBuilder report = new StringBuilder();
        try {
            if (decodedDir == null || !decodedDir.isDirectory()) {
                return new Result(false, "Decoded APK project folder is missing.", "");
            }
            if (options == null) options = new Options();

            File manifest = new File(decodedDir, "AndroidManifest.xml");
            if (!manifest.isFile()) {
                return new Result(false, "Decoded AndroidManifest.xml was not found.", "");
            }

            String manifestText = readUtf8(manifest);
            String updated = manifestText;
            if (options.applyNetworkSecurityConfig) {
                updated = setApplicationAttribute(updated, "android:networkSecurityConfig", NETWORK_CONFIG_REF);
                report.append("Manifest: set android:networkSecurityConfig=\"").append(NETWORK_CONFIG_REF).append("\".\n");
                File xmlDir = new File(decodedDir, "res/xml");
                if (!xmlDir.exists() && !xmlDir.mkdirs()) {
                    throw new IOException("Unable to create " + xmlDir.getAbsolutePath());
                }
                File nsc = new File(xmlDir, NETWORK_CONFIG_NAME + ".xml");
                writeUtf8(nsc, buildNetworkSecurityConfig(options));
                report.append("Resource: wrote res/xml/").append(nsc.getName()).append(".\n");
            }
            if (options.allowCleartext) {
                updated = setApplicationAttribute(updated, "android:usesCleartextTraffic", "true");
                report.append("Manifest: set android:usesCleartextTraffic=\"true\".\n");
            }
            if (options.makeDebuggable) {
                updated = setApplicationAttribute(updated, "android:debuggable", "true");
                report.append("Manifest: set android:debuggable=\"true\".\n");
            }
            if (options.allowBackup) {
                updated = setApplicationAttribute(updated, "android:allowBackup", "true");
                report.append("Manifest: set android:allowBackup=\"true\".\n");
            }

            if (options.patchCertificatePinning) {
                PinningPatchResult pinning = patchCertificatePinning(decodedDir);
                report.append(pinning.report);
            }

            if (!updated.equals(manifestText)) writeUtf8(manifest, updated);
            if (report.length() == 0) report.append("No project patch options were selected.\n");
            return new Result(true, "Decoded project patched.", report.toString());
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (TextUtils.isEmpty(msg)) msg = t.getClass().getSimpleName();
            report.append("Error: ").append(t.getClass().getSimpleName()).append(": ").append(msg).append('\n');
            return new Result(false, msg, report.toString());
        }
    }

    static final class PinningPatchResult {
        final int scannedFiles;
        final int candidateFiles;
        final int patchedMethods;
        final String report;

        PinningPatchResult(int scannedFiles, int candidateFiles, int patchedMethods, String report) {
            this.scannedFiles = scannedFiles;
            this.candidateFiles = candidateFiles;
            this.patchedMethods = patchedMethods;
            this.report = report == null ? "" : report;
        }
    }

    private static final class SmaliPinningPatch {
        final String selectorType;
        final String selectorName;
        final String methodName;
        final String signature;
        final String[] replacementLines;

        SmaliPinningPatch(String selectorType, String selectorName, String methodName, String signature, String[] replacementLines) {
            this.selectorType = selectorType;
            this.selectorName = selectorName;
            this.methodName = methodName;
            this.signature = signature;
            this.replacementLines = replacementLines;
        }
    }

    private static final class SmaliHead {
        String name = "";
        boolean isInterface;
        final ArrayList<String> implementsList = new ArrayList<>();
    }

    private static final String[] RETURN_VOID_SMALI = new String[] {
            ".locals 0",
            "return-void"
    };
    private static final String[] RETURN_TRUE_SMALI = new String[] {
            ".locals 1",
            "const/4 v0, 0x1",
            "return v0"
    };
    private static final String[] RETURN_EMPTY_CERT_ARRAY_SMALI = new String[] {
            ".locals 1",
            "const/4 v0, 0x0",
            "new-array v0, v0, [Ljava/security/cert/X509Certificate;",
            "return-object v0"
    };
    private static final SmaliPinningPatch[] PINNING_PATCHES = new SmaliPinningPatch[] {
            new SmaliPinningPatch("interface", "javax/net/ssl/X509TrustManager",
                    "X509TrustManager#checkClientTrusted", "checkClientTrusted([Ljava/security/cert/X509Certificate;Ljava/lang/String;)V", RETURN_VOID_SMALI),
            new SmaliPinningPatch("interface", "javax/net/ssl/X509TrustManager",
                    "X509TrustManager#checkServerTrusted", "checkServerTrusted([Ljava/security/cert/X509Certificate;Ljava/lang/String;)V", RETURN_VOID_SMALI),
            new SmaliPinningPatch("interface", "javax/net/ssl/X509TrustManager",
                    "X509TrustManager#getAcceptedIssuers", "getAcceptedIssuers()[Ljava/security/cert/X509Certificate;", RETURN_EMPTY_CERT_ARRAY_SMALI),
            new SmaliPinningPatch("interface", "javax/net/ssl/HostnameVerifier",
                    "HostnameVerifier#verify", "verify(Ljava/lang/String;Ljavax/net/ssl/SSLSession;)Z", RETURN_TRUE_SMALI),
            new SmaliPinningPatch("class", "com/squareup/okhttp/CertificatePinner",
                    "OkHttp 2 CertificatePinner#check", "check(Ljava/lang/String;Ljava/util/List;)V", RETURN_VOID_SMALI),
            new SmaliPinningPatch("class", "okhttp3/CertificatePinner",
                    "OkHttp 3 CertificatePinner#check", "check(Ljava/lang/String;Ljava/util/List;)V", RETURN_VOID_SMALI),
            new SmaliPinningPatch("class", "okhttp3/CertificatePinner",
                    "OkHttp 4 CertificatePinner#check$okhttp", "check$okhttp(Ljava/lang/String;Lkotlin/jvm/functions/Function0;)V", RETURN_VOID_SMALI)
    };

    public static String buildCertificatePinningReport(File decodedDir) {
        try {
            PinningPatchResult result = scanOrPatchCertificatePinning(decodedDir, false);
            return result.report;
        } catch (Throwable t) {
            return "Certificate pinning report failed: " + t.getClass().getSimpleName() + ": "
                    + (t.getMessage() == null ? "" : t.getMessage()) + "\n";
        }
    }

    static PinningPatchResult patchCertificatePinning(File decodedDir) {
        try {
            return scanOrPatchCertificatePinning(decodedDir, true);
        } catch (Throwable t) {
            String msg = "Certificate pinning patch failed: " + t.getClass().getSimpleName() + ": "
                    + (t.getMessage() == null ? "" : t.getMessage()) + "\n";
            return new PinningPatchResult(0, 0, 0, msg);
        }
    }

    private static PinningPatchResult scanOrPatchCertificatePinning(File decodedDir, boolean applyPatch) throws IOException {
        ArrayList<File> smaliFiles = new ArrayList<>();
        collectSmaliFiles(new File(decodedDir, "smali"), smaliFiles, 20000);
        collectSiblingSmaliRoots(decodedDir, smaliFiles, 20000);
        StringBuilder report = new StringBuilder();
        Set<String> seenCandidates = new LinkedHashSet<>();
        int patchedMethods = 0;
        int scanned = 0;
        for (File smali : smaliFiles) {
            if (smali == null || !smali.isFile()) continue;
            scanned++;
            PinningFileResult fileResult = processCertificatePinningFile(decodedDir, smali, applyPatch);
            if (fileResult.candidate) {
                String rel = relativePath(decodedDir, smali);
                seenCandidates.add(rel);
                if (!TextUtils.isEmpty(fileResult.reportLine)) {
                    report.append(fileResult.reportLine);
                }
            }
            patchedMethods += fileResult.patchedMethods;
        }
        if (seenCandidates.isEmpty()) {
            report.append(applyPatch ? "Certificate pinning patch: no supported smali targets found.\n"
                    : "Certificate pinning report: no supported smali targets found.\n");
        } else {
            report.insert(0, (applyPatch ? "Certificate pinning patch" : "Certificate pinning report")
                    + ": candidates=" + seenCandidates.size()
                    + " patchedMethods=" + patchedMethods
                    + " scannedSmali=" + scanned + "\n");
        }
        return new PinningPatchResult(scanned, seenCandidates.size(), patchedMethods, report.toString());
    }

    private static final class PinningFileResult {
        boolean candidate;
        int patchedMethods;
        String reportLine = "";
    }

    private static PinningFileResult processCertificatePinningFile(File decodedDir, File smali, boolean applyPatch) throws IOException {
        PinningFileResult result = new PinningFileResult();
        String original = readUtf8(smali);
        SmaliHead head = parseSmaliHead(original);
        if (head.isInterface) return result;
        ArrayList<SmaliPinningPatch> applicable = new ArrayList<>();
        for (SmaliPinningPatch patch : PINNING_PATCHES) {
            if (selectorMatches(patch, head) && original.contains(patch.signature)) applicable.add(patch);
        }
        if (applicable.isEmpty()) {
            if (containsPinningKeyword(original)) {
                result.candidate = true;
                result.reportLine = "candidate-only: " + relativePath(decodedDir, smali) + "\n";
            }
            return result;
        }
        result.candidate = true;
        String eol = original.contains("\r\n") ? "\r\n" : "\n";
        String normalized = original.replace("\r\n", "\n");
        String patched = normalized;
        StringBuilder line = new StringBuilder();
        line.append(applyPatch ? "patched: " : "patchable: ").append(relativePath(decodedDir, smali));
        for (SmaliPinningPatch patch : applicable) {
            MethodPatchResult method = patchSmaliMethod(patched, patch);
            if (method.matched) {
                line.append("  ").append(patch.methodName);
                if (applyPatch && method.patched) {
                    patched = method.text;
                    result.patchedMethods++;
                    line.append("[patched]");
                } else if (method.alreadyPatched) {
                    line.append("[already]");
                }
            }
        }
        line.append('\n');
        result.reportLine = line.toString();
        if (applyPatch && !normalized.equals(patched)) {
            if ("\r\n".equals(eol)) patched = patched.replace("\n", "\r\n");
            writeUtf8(smali, patched);
        }
        return result;
    }

    private static final class MethodPatchResult {
        boolean matched;
        boolean patched;
        boolean alreadyPatched;
        String text;
    }

    private static MethodPatchResult patchSmaliMethod(String content, SmaliPinningPatch patch) {
        MethodPatchResult out = new MethodPatchResult();
        out.text = content;
        int search = 0;
        while (search < content.length()) {
            int methodStart = content.indexOf(".method", search);
            if (methodStart < 0) break;
            int lineEnd = content.indexOf('\n', methodStart);
            if (lineEnd < 0) lineEnd = content.length();
            String methodLine = content.substring(methodStart, lineEnd).trim();
            int methodEnd = content.indexOf(".end method", lineEnd);
            if (methodEnd < 0) break;
            int blockEnd = content.indexOf('\n', methodEnd);
            if (blockEnd < 0) blockEnd = content.length();
            else blockEnd += 1;
            if (methodLine.contains(patch.signature)) {
                out.matched = true;
                String block = content.substring(methodStart, blockEnd);
                if (block.contains("PermsTest MITM")) {
                    out.alreadyPatched = true;
                    return out;
                }
                String body = content.substring(lineEnd + 1, methodEnd);
                ArrayList<String> replacement = new ArrayList<>();
                replacement.add(content.substring(methodStart, lineEnd));
                replacement.add("    # inserted by PermsTest MITM patch to disable certificate pinning");
                for (String repl : patch.replacementLines) replacement.add("    " + repl);
                replacement.add("");
                replacement.add("    # commented out by PermsTest MITM patch to preserve old method body");
                String[] oldLines = body.split("\n", -1);
                for (String oldLine : oldLines) {
                    if (oldLine.length() == 0) continue;
                    replacement.add("    # " + stripOneIndent(oldLine));
                }
                replacement.add(".end method");
                String newBlock = joinLines(replacement) + "\n";
                out.text = content.substring(0, methodStart) + newBlock + content.substring(blockEnd);
                out.patched = true;
                return out;
            }
            search = blockEnd;
        }
        return out;
    }

    private static String stripOneIndent(String line) {
        if (line == null) return "";
        if (line.startsWith("    ")) return line.substring(4);
        if (line.startsWith("\t")) return line.substring(1);
        return line;
    }

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private static boolean selectorMatches(SmaliPinningPatch patch, SmaliHead head) {
        if (patch == null || head == null) return false;
        if ("class".equals(patch.selectorType)) return patch.selectorName.equals(head.name);
        return head.implementsList.contains(patch.selectorName);
    }

    private static SmaliHead parseSmaliHead(String smali) {
        SmaliHead head = new SmaliHead();
        if (smali == null) return head;
        Matcher cls = Pattern.compile("(?m)^\\.class\\s+([^\\n]*?)L([^;]+);").matcher(smali);
        if (cls.find()) {
            head.name = cls.group(2);
            head.isInterface = cls.group(1) != null && cls.group(1).contains(" interface ");
        }
        Matcher impl = Pattern.compile("(?m)^\\.implements\\s+L([^;]+);").matcher(smali);
        while (impl.find()) head.implementsList.add(impl.group(1));
        return head;
    }

    private static boolean containsPinningKeyword(String text) {
        if (TextUtils.isEmpty(text)) return false;
        return text.contains("CertificatePinner")
                || text.contains("X509TrustManager")
                || text.contains("HostnameVerifier")
                || text.contains("checkServerTrusted")
                || text.contains("checkClientTrusted")
                || text.contains("CertificateException")
                || text.toLowerCase(Locale.US).contains("pinning");
    }

    private static void collectSiblingSmaliRoots(File decodedDir, ArrayList<File> out, int max) {
        if (decodedDir == null || !decodedDir.isDirectory()) return;
        File[] kids = decodedDir.listFiles();
        if (kids == null) return;
        for (File kid : kids) {
            if (kid != null && kid.isDirectory() && kid.getName().startsWith("smali") && !"smali".equals(kid.getName())) {
                collectSmaliFiles(kid, out, max);
                if (out.size() >= max) return;
            }
        }
    }

    private static void collectSmaliFiles(File file, ArrayList<File> out, int max) {
        if (file == null || out == null || out.size() >= max || !file.exists()) return;
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids == null) return;
            for (File kid : kids) {
                collectSmaliFiles(kid, out, max);
                if (out.size() >= max) return;
            }
            return;
        }
        if (file.getName().toLowerCase(Locale.US).endsWith(".smali")) out.add(file);
    }

    private static String relativePath(File root, File file) {
        try {
            String rp = root.getCanonicalPath();
            String fp = file.getCanonicalPath();
            if (fp.startsWith(rp)) {
                String rel = fp.substring(rp.length());
                while (rel.startsWith(File.separator)) rel = rel.substring(1);
                return rel;
            }
        } catch (Throwable ignored) {
        }
        return file == null ? "" : file.getAbsolutePath();
    }

    public static String buildExportedActivityReport(File decodedDir) {
        StringBuilder report = new StringBuilder();
        try {
            if (decodedDir == null || !decodedDir.isDirectory()) return "Decoded APK project folder is missing.\n";
            File manifest = new File(decodedDir, "AndroidManifest.xml");
            if (!manifest.isFile()) return "Decoded AndroidManifest.xml was not found.\n";
            String text = readUtf8(manifest);
            Matcher matcher = Pattern.compile("<activity(?:\\s|>)(.*?)(?:</activity>|/>)", Pattern.DOTALL).matcher(text);
            int total = 0;
            int exported = 0;
            while (matcher.find()) {
                total++;
                String block = matcher.group(0);
                String name = attr(block, "android:name");
                String exp = attr(block, "android:exported");
                boolean hasIntentFilter = block.contains("<intent-filter");
                boolean isExported = "true".equalsIgnoreCase(exp) || (TextUtils.isEmpty(exp) && hasIntentFilter);
                if (isExported) {
                    exported++;
                    report.append("exported activity: ")
                            .append(TextUtils.isEmpty(name) ? "(unnamed)" : name)
                            .append("  explicit=").append(TextUtils.isEmpty(exp) ? "(missing)" : exp)
                            .append("  intentFilter=").append(hasIntentFilter)
                            .append('\n');
                }
            }
            if (total == 0) report.append("No <activity> entries found in decoded manifest.\n");
            else if (exported == 0) report.append("No exported activities detected. totalActivities=").append(total).append('\n');
            else report.insert(0, "Exported activity report: " + exported + " / " + total + " activities exported.\n");
        } catch (Throwable t) {
            report.append("Exported activity report failed: ").append(t.getClass().getSimpleName()).append(": ")
                    .append(t.getMessage() == null ? "" : t.getMessage()).append('\n');
        }
        return report.toString();
    }

    public static List<File> findSecureFlagSmaliCandidates(File decodedDir) {
        ArrayList<File> out = new ArrayList<>();
        try {
            collectSecureFlagSmaliCandidates(decodedDir, out, 200);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void collectSecureFlagSmaliCandidates(File file, ArrayList<File> out, int max) {
        if (file == null || out.size() >= max || !file.exists()) return;
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids == null) return;
            for (File kid : kids) {
                collectSecureFlagSmaliCandidates(kid, out, max);
                if (out.size() >= max) return;
            }
            return;
        }
        String name = file.getName().toLowerCase(Locale.US);
        if (!name.endsWith(".smali")) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("FLAG_SECURE") || line.contains("0x2000") || line.contains("8192")) {
                    out.add(file);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String buildNetworkSecurityConfig(Options options) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<network-security-config>\n");
        sb.append("    <base-config cleartextTrafficPermitted=\"").append(options != null && options.allowCleartext ? "true" : "false").append("\">\n");
        sb.append("        <trust-anchors>\n");
        if (options == null || options.trustSystemCerts) sb.append("            <certificates src=\"system\" />\n");
        if (options == null || options.trustUserCerts) sb.append("            <certificates src=\"user\" />\n");
        sb.append("        </trust-anchors>\n");
        sb.append("    </base-config>\n");
        sb.append("    <debug-overrides>\n");
        sb.append("        <trust-anchors>\n");
        sb.append("            <certificates src=\"user\" />\n");
        sb.append("        </trust-anchors>\n");
        sb.append("    </debug-overrides>\n");
        sb.append("</network-security-config>\n");
        return sb.toString();
    }

    private static String setApplicationAttribute(String manifestText, String attrName, String value) throws IOException {
        if (TextUtils.isEmpty(manifestText)) throw new IOException("Manifest is empty.");
        Pattern appPattern = Pattern.compile("<application\\b([^>]*?)(/?)>", Pattern.DOTALL);
        Matcher app = appPattern.matcher(manifestText);
        if (!app.find()) throw new IOException("Manifest has no <application> tag.");
        String tag = app.group(0);
        String replacement;
        Pattern attrPattern = Pattern.compile(Pattern.quote(attrName) + "\\s*=\\s*([\"'])[^\"']*\\1");
        Matcher attr = attrPattern.matcher(tag);
        if (attr.find()) {
            replacement = attr.replaceFirst(attrName + "=\"" + value + "\"");
        } else {
            int insert = tag.endsWith("/>") ? tag.length() - 2 : tag.length() - 1;
            replacement = tag.substring(0, insert) + " " + attrName + "=\"" + value + "\"" + tag.substring(insert);
        }
        return manifestText.substring(0, app.start()) + replacement + manifestText.substring(app.end());
    }

    private static String attr(String text, String name) {
        if (text == null) return "";
        Matcher m = Pattern.compile(Pattern.quote(name) + "\\s*=\\s*([\"'])(.*?)\\1", Pattern.DOTALL).matcher(text);
        return m.find() ? m.group(2) : "";
    }

    private static String readUtf8(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(Math.max(file.length(), 1024L), 1024L * 1024L))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeUtf8(File file, String text) throws IOException {
        File parent = file == null ? null : file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create " + parent.getAbsolutePath());
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }
}
