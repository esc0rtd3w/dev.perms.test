package dev.perms.test.plugins.runtime;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import dalvik.system.DexClassLoader;
import dev.perms.test.plugins.PluginAction;
import dev.perms.test.plugins.PluginActionRegistry;
import dev.perms.test.plugins.PluginManifest;
import dev.perms.test.plugins.PluginRuntimePolicy;
import dev.perms.test.ui.dialog.GenericViewerDialog;

/** Policy-gated trusted DEX/JAR/APK action runtime for explicit user-tapped actions only. */
public final class TrustedDexPluginRuntime {
    private TrustedDexPluginRuntime() {
    }

    public static boolean run(PluginActionRegistry.Host host, PluginManifest plugin, PluginAction action) {
        if (host == null || plugin == null || action == null) return false;
        Activity activity = host.getActivity();
        if (activity == null) {
            host.appendOutput("[plugins] Trusted-Dex action blocked: Activity context is unavailable.\n");
            return false;
        }
        try {
            TrustedEntry entry = resolveEntry(plugin, action);
            File sourcePayload = resolvePayload(plugin, entry.payloadPath);
            String actualSha256 = sha256File(sourcePayload);
            if (TextUtils.isEmpty(entry.expectedSha256)) {
                host.appendOutput("[plugins] Trusted-Dex action blocked: expected sha256 is not declared.\n");
                return false;
            }
            if (!entry.expectedSha256.equalsIgnoreCase(actualSha256)) {
                host.appendOutput("[plugins] Trusted-Dex action blocked: payload hash mismatch. expected="
                        + entry.expectedSha256 + " actual=" + actualSha256 + "\n");
                return false;
            }
            String approvalProblem = PluginRuntimePolicy.trustedDexApprovalProblem(host.getSharedPreferences(), plugin, action);
            if (!TextUtils.isEmpty(approvalProblem)) {
                host.appendOutput(PluginRuntimePolicy.trustedDexApprovalBlockedMessage(approvalProblem));
                return false;
            }

            File loadFile = preparePrivateReadOnlyPayload(activity, plugin, action, sourcePayload, actualSha256);
            File optimizedDir = new File(activity.getCodeCacheDir(), "trusted_plugin_opt/" + safeId(plugin.id) + "/" + actualSha256);
            if (!optimizedDir.isDirectory() && !optimizedDir.mkdirs()) {
                host.appendOutput("[plugins] Trusted-Dex action blocked: could not create optimized code directory.\n");
                return false;
            }

            host.appendOutput("[plugins] Loading trusted code " + plugin.id + "/" + action.id
                    + " sha256=" + actualSha256 + "\n");
            DexClassLoader loader = new DexClassLoader(
                    loadFile.getAbsolutePath(),
                    optimizedDir.getAbsolutePath(),
                    null,
                    activity.getClassLoader());
            Class<?> clazz = loader.loadClass(entry.className);
            Method method = findTrustedMethod(clazz, entry.methodName);
            Object result = invoke(method, activity, action.raw == null ? new JSONObject() : action.raw);
            showTrustedResult(host, plugin, action, formatTrustedResult(result));
            return true;
        } catch (Throwable t) {
            host.appendOutput("[plugins] Trusted-Dex action failed/blocked for " + plugin.id + "/" + action.id
                    + ": " + safeMessage(t) + "\n");
            return false;
        }
    }

    private static TrustedEntry resolveEntry(PluginManifest plugin, PluginAction action) {
        JSONObject raw = action.raw == null ? new JSONObject() : action.raw;
        String payload = firstNonEmpty(action.target, action.handler,
                raw.optString("dex", ""), raw.optString("path", ""));
        String className = firstNonEmpty(raw.optString("className", ""),
                raw.optString("entryClass", ""), raw.optString("class", ""));
        String methodName = firstNonEmpty(raw.optString("methodName", ""), raw.optString("method", ""), "run");
        String expectedSha256 = raw.optString("sha256", "").trim().toLowerCase(Locale.US);
        if (TextUtils.isEmpty(payload)) throw new IllegalArgumentException("trusted-code payload target is missing");
        if (!isSafeRelativePluginPath(payload)) throw new IllegalArgumentException("unsafe trusted-code payload path: " + payload);
        if (TextUtils.isEmpty(className)) throw new IllegalArgumentException("trusted-code className/entryClass is missing");
        if (!isSafeClassName(className)) throw new IllegalArgumentException("unsafe trusted-code class name: " + className);
        if (!isSafeMethodName(methodName)) throw new IllegalArgumentException("unsafe trusted-code method name: " + methodName);
        if (!TextUtils.isEmpty(expectedSha256) && !expectedSha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expected sha256 must be 64 hex characters");
        }
        return new TrustedEntry(payload, className, methodName, expectedSha256);
    }

    private static File resolvePayload(PluginManifest plugin, String payloadPath) throws Exception {
        File root = plugin.homeDir;
        if (root == null || !root.isDirectory()) throw new IllegalArgumentException("plugin directory is unavailable");
        File payload = new File(root, payloadPath).getCanonicalFile();
        String rootPath = root.getCanonicalPath() + File.separator;
        if (!payload.getPath().startsWith(rootPath)) throw new IllegalArgumentException("trusted-code payload escapes plugin directory");
        if (!payload.isFile()) throw new IllegalArgumentException("trusted-code payload is missing: " + payloadPath);
        String lower = payload.getName().toLowerCase(Locale.US);
        if (!(lower.endsWith(".dex") || lower.endsWith(".jar") || lower.endsWith(".apk"))) {
            throw new IllegalArgumentException("trusted-code payload must be .dex, .jar, or .apk");
        }
        return payload;
    }

    private static File preparePrivateReadOnlyPayload(Activity activity,
                                                      PluginManifest plugin,
                                                      PluginAction action,
                                                      File sourcePayload,
                                                      String actualSha256) throws Exception {
        File dir = new File(activity.getCodeCacheDir(), "trusted_plugin_code/" + safeId(plugin.id) + "/" + actualSha256);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("could not create trusted-code cache directory");
        }
        String name = safeId(action.id) + extension(sourcePayload.getName());
        File target = new File(dir, name);
        if (target.isFile()) {
            try {
                if (actualSha256.equalsIgnoreCase(sha256File(target))) {
                    target.setReadable(true, true);
                    target.setWritable(false, false);
                    target.setExecutable(false, false);
                    return target;
                }
            } catch (Throwable ignored) {
            }
            target.setWritable(true, true);
            if (!target.delete()) throw new IllegalStateException("could not replace stale trusted-code cache payload");
        }
        copyFile(sourcePayload, target);
        if (!actualSha256.equalsIgnoreCase(sha256File(target))) {
            target.delete();
            throw new IllegalStateException("trusted-code cache copy hash mismatch");
        }
        target.setReadable(true, true);
        target.setWritable(false, false);
        target.setExecutable(false, false);
        return target;
    }

    private static Method findTrustedMethod(Class<?> clazz, String methodName) throws Exception {
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (!methodName.equals(method.getName())) continue;
            int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 2 && Context.class.isAssignableFrom(params[0]) && JSONObject.class.isAssignableFrom(params[1])) return method;
            if (params.length == 1 && Context.class.isAssignableFrom(params[0])) return method;
            if (params.length == 1 && JSONObject.class.isAssignableFrom(params[0])) return method;
            if (params.length == 0) return method;
        }
        throw new NoSuchMethodException("public static " + methodName + " method not found with supported parameters");
    }

    private static Object invoke(Method method, Context context, JSONObject actionJson) throws Exception {
        Class<?>[] params = method.getParameterTypes();
        if (params.length == 2) return method.invoke(null, context, actionJson);
        if (params.length == 1 && Context.class.isAssignableFrom(params[0])) return method.invoke(null, context);
        if (params.length == 1 && JSONObject.class.isAssignableFrom(params[0])) return method.invoke(null, actionJson);
        return method.invoke(null);
    }

    private static TrustedResult formatTrustedResult(Object result) {
        if (result instanceof TrustedPluginResult) {
            TrustedPluginResult typed = (TrustedPluginResult) result;
            return new TrustedResult(typed.title, typed.subtitle, typed.text, typed.syntax,
                    typed.presentation, typed.windowStyle, typed.windowFit);
        }
        if (result == null) {
            return new TrustedResult("", "", "Trusted code method completed with no return value.",
                    "text", "", "", "");
        }
        if (result instanceof JSONObject) {
            return new TrustedResult("", "", jsonPretty((JSONObject) result), "json", "", "", "");
        }
        if (result instanceof JSONArray) {
            return new TrustedResult("", "", jsonPretty((JSONArray) result), "json", "", "", "");
        }
        if (result instanceof Map) {
            return new TrustedResult("", "", jsonPretty(new JSONObject((Map<?, ?>) result)), "json", "", "", "");
        }
        if (result instanceof Collection) {
            return new TrustedResult("", "", jsonPretty(new JSONArray((Collection<?>) result)), "json", "", "", "");
        }
        return new TrustedResult("", "", String.valueOf(result), "text", "", "", "");
    }

    private static void showTrustedResult(PluginActionRegistry.Host host, PluginManifest plugin, PluginAction action, TrustedResult result) {
        Activity activity = host.getActivity();
        String title = firstNonEmpty(result.title, plugin.name + " / " + action.title);
        String subtitle = firstNonEmpty(result.subtitle, "Trusted code result");
        String body = TextUtils.isEmpty(result.text) ? "Trusted code method returned no output." : result.text;
        String presentation = firstNonEmpty(result.presentation, action.presentation);
        host.appendOutput("[plugins] trusted_dex " + plugin.id + "/" + action.id + " completed.\n");
        if (isLogPresentation(presentation)) {
            host.appendOutput(body.endsWith("\n") ? body : body + "\n");
            return;
        }
        boolean largeOverride = host.shouldRunPluginInPanel(plugin.id);
        boolean requestWindow = isWindowPresentation(presentation) || (!isDialogPresentation(presentation) && largeOverride);
        String syntax = firstNonEmpty(action.syntax, result.syntax, "text");
        String windowStyle = largeOverride ? "full" : firstNonEmpty(action.windowStyle, result.windowStyle, plugin.windowStyle, "full");
        String windowFit = largeOverride ? "current" : firstNonEmpty(action.windowFit, result.windowFit, plugin.windowFit, "current");
        if (requestWindow && host.showPluginTextPanel(plugin.id + "." + action.id + ".trusted_dex",
                title, subtitle, body, syntax, windowStyle, windowFit)) {
            return;
        }
        if (activity != null) {
            activity.runOnUiThread(() -> GenericViewerDialog.showHighlightedText(activity,
                    title,
                    subtitle,
                    body,
                    syntax));
        }
    }

    private static String jsonPretty(JSONObject object) {
        try {
            return object.toString(2);
        } catch (Throwable ignored) {
            return String.valueOf(object);
        }
    }

    private static String jsonPretty(JSONArray array) {
        try {
            return array.toString(2);
        } catch (Throwable ignored) {
            return String.valueOf(array);
        }
    }

    private static boolean isLogPresentation(String value) {
        return "log".equalsIgnoreCase(value)
                || "output".equalsIgnoreCase(value)
                || "main_output".equalsIgnoreCase(value);
    }

    private static boolean isWindowPresentation(String value) {
        return "window".equalsIgnoreCase(value)
                || "panel".equalsIgnoreCase(value)
                || "large".equalsIgnoreCase(value);
    }

    private static boolean isDialogPresentation(String value) {
        return "dialog".equalsIgnoreCase(value)
                || "viewer".equalsIgnoreCase(value);
    }

    private static void copyFile(File source, File target) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private static String sha256File(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private static String safeId(String value) {
        String raw = TextUtils.isEmpty(value) ? "plugin" : value;
        return raw.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String extension(String name) {
        if (name == null) return ".dex";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".dex";
    }

    private static boolean isSafeRelativePluginPath(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String p = path.replace('\\', '/');
        return !p.startsWith("/")
                && !p.contains("../")
                && !p.equals("..")
                && !p.contains("/..")
                && !p.contains("//");
    }

    private static boolean isSafeClassName(String value) {
        return !TextUtils.isEmpty(value) && value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    }

    private static boolean isSafeMethodName(String value) {
        return !TextUtils.isEmpty(value) && value.matches("[A-Za-z_$][A-Za-z0-9_$]*");
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        if (!TextUtils.isEmpty(msg)) return msg;
        return t.getClass().getSimpleName();
    }


    private static final class TrustedResult {
        final String title;
        final String subtitle;
        final String text;
        final String syntax;
        final String presentation;
        final String windowStyle;
        final String windowFit;

        TrustedResult(String title,
                      String subtitle,
                      String text,
                      String syntax,
                      String presentation,
                      String windowStyle,
                      String windowFit) {
            this.title = title == null ? "" : title.trim();
            this.subtitle = subtitle == null ? "" : subtitle.trim();
            this.text = text == null ? "" : text;
            this.syntax = syntax == null ? "" : syntax.trim();
            this.presentation = presentation == null ? "" : presentation.trim();
            this.windowStyle = windowStyle == null ? "" : windowStyle.trim();
            this.windowFit = windowFit == null ? "" : windowFit.trim();
        }
    }

    private static final class TrustedEntry {
        final String payloadPath;
        final String className;
        final String methodName;
        final String expectedSha256;

        TrustedEntry(String payloadPath, String className, String methodName, String expectedSha256) {
            this.payloadPath = payloadPath;
            this.className = className;
            this.methodName = methodName;
            this.expectedSha256 = expectedSha256;
        }
    }
}
