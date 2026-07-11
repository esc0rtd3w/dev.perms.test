package dev.perms.test.plugins;

import android.text.TextUtils;

import java.io.File;

/** Read-only report for export/import round-trip validation of a staged plugin package. */
public final class PluginExportRoundTripReport {
    private PluginExportRoundTripReport() {
    }

    public static String build(PluginManifest source, PluginManifest inspected, File archive, boolean archiveKept) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("Plugin Export Round Trip").append(nl);
        if (source == null) {
            sb.append(nl).append("Result: failed").append(nl);
            sb.append("• Source plugin manifest is unavailable.").append(nl);
            return sb.toString();
        }

        appendLine(sb, "Name", source.name, nl);
        appendLine(sb, "ID", source.id, nl);
        appendLine(sb, "Version", source.version, nl);
        appendLine(sb, "Runtime", source.runtime, nl);
        if (source.homeDir != null) appendLine(sb, "Source folder", source.homeDir.getAbsolutePath(), nl);
        if (archive != null) {
            appendLine(sb, archiveKept ? "Archive" : "Temporary archive", archive.getAbsolutePath(), nl);
            if (archive.isFile()) appendLine(sb, "Archive size", archive.length() + " bytes", nl);
        }

        sb.append(nl).append("Checks:").append(nl);
        String sourceProblem = PluginPackageValidator.requiredFileProblem(source, true);
        if (TextUtils.isEmpty(sourceProblem)) {
            sb.append("• Source package files: ready").append(nl);
        } else {
            sb.append("• Source package files: failed — ").append(sourceProblem).append(nl);
        }
        sb.append("• Export archive build: ").append(archive != null && archive.isFile() ? "created" : "missing").append(nl);
        sb.append("• Import validator: ").append(inspected == null ? "not inspected" : "passed").append(nl);

        sb.append(nl).append("Manifest comparison:").append(nl);
        boolean sameId = inspected != null && equalsText(source.id, inspected.id);
        boolean sameVersion = inspected != null && equalsText(source.version, inspected.version);
        boolean sameRuntime = inspected != null && equalsText(source.runtime, inspected.runtime);
        boolean sameApi = inspected != null && source.apiVersion == inspected.apiVersion;
        boolean sameActions = inspected != null && actionCount(source) == actionCount(inspected);
        appendCompare(sb, "ID", source.id, inspected == null ? "" : inspected.id, sameId, nl);
        appendCompare(sb, "Version", source.version, inspected == null ? "" : inspected.version, sameVersion, nl);
        appendCompare(sb, "Runtime", source.runtime, inspected == null ? "" : inspected.runtime, sameRuntime, nl);
        appendCompare(sb, "API version", String.valueOf(source.apiVersion), inspected == null ? "" : String.valueOf(inspected.apiVersion), sameApi, nl);
        appendCompare(sb, "Action count", String.valueOf(actionCount(source)), inspected == null ? "" : String.valueOf(actionCount(inspected)), sameActions, nl);

        boolean passed = TextUtils.isEmpty(sourceProblem)
                && archive != null
                && archive.isFile()
                && inspected != null
                && sameId
                && sameVersion
                && sameRuntime
                && sameApi
                && sameActions;
        sb.append(nl).append("Result: ").append(passed ? "ready" : "failed").append(nl);
        if (passed) {
            sb.append("The staged plugin can be exported and re-inspected through the normal import validator without changing staged plugin state.").append(nl);
        } else {
            sb.append("Fix the failed item above before treating this plugin package as round-trip ready.").append(nl);
        }
        sb.append("This report does not install a copy, run plugin actions, execute shell commands, approve scripts, trust code payloads, load trusted-code payloads, launch plugin APK components, or enable startup/background execution.").append(nl);
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, String label, String value, String nl) {
        if (sb == null || TextUtils.isEmpty(label) || TextUtils.isEmpty(value)) return;
        sb.append(label).append(": ").append(value).append(nl);
    }

    private static void appendCompare(StringBuilder sb, String label, String source, String inspected, boolean match, String nl) {
        if (sb == null) return;
        sb.append("• ").append(label).append(": ");
        if (match) {
            sb.append("match");
            if (!TextUtils.isEmpty(source)) sb.append(" (").append(source).append(")");
        } else {
            sb.append("mismatch");
            sb.append(" source=").append(TextUtils.isEmpty(source) ? "<empty>" : source);
            sb.append(" inspected=").append(TextUtils.isEmpty(inspected) ? "<empty>" : inspected);
        }
        sb.append(nl);
    }

    private static boolean equalsText(String first, String second) {
        return (first == null ? "" : first).equals(second == null ? "" : second);
    }

    private static int actionCount(PluginManifest manifest) {
        return manifest == null || manifest.actions == null ? 0 : manifest.actions.size();
    }
}
