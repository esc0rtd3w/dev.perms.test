package dev.perms.test.network.ssh;

import android.content.Context;
import android.text.TextUtils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.security.Provider;
import java.security.Security;

/** Android runtime preparation for embedded SSH client/server use. */
final class SshAndroidRuntimeCompat {
    private static final Object LOCK = new Object();
    private static boolean runtimePrepared;
    private static String lastStatus = "";

    private SshAndroidRuntimeCompat() {
    }

    static void prepare(Context context) {
        prepareServer(context);
    }

    static void prepareServer(Context context) {
        prepareRuntime(context, "SSH server");
    }

    static void prepareClient(Context context) {
        prepareRuntime(context, "SSH client");
    }

    private static void prepareRuntime(Context context, String label) {
        synchronized (LOCK) {
            if (runtimePrepared) return;
            StringBuilder status = new StringBuilder();
            try {
                prepareSystemProperties(context, status);
            } catch (Throwable t) {
                appendStatus(status, "System property setup warning: " + describeThrowable(t));
            }
            try {
                Provider existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
                if (existing == null || !existing.getClass().getName().equals(BouncyCastleProvider.class.getName())) {
                    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
                    Security.insertProviderAt(new BouncyCastleProvider(), 1);
                    appendStatus(status, "Bouncy Castle provider installed for " + label + ".");
                } else {
                    appendStatus(status, "Bouncy Castle provider already active for " + label + ".");
                }
            } catch (Throwable t) {
                appendStatus(status, "Bouncy Castle provider setup warning: " + describeThrowable(t));
            }
            lastStatus = status.toString();
            runtimePrepared = true;
        }
    }

    private static void prepareSystemProperties(Context context, StringBuilder status) {
        Context app = context == null ? null : context.getApplicationContext();
        File files = app == null ? null : app.getFilesDir();
        File cache = app == null ? null : app.getCacheDir();
        File sshBase = files == null ? new File("/data/local/tmp/permstest-ssh") : new File(files, "ssh");
        File sshHome = new File(sshBase, "home");
        File sshTmp = cache == null ? new File(sshBase, "tmp") : new File(cache, "ssh-tmp");
        ensureDir(sshBase);
        ensureDir(sshHome);
        ensureDir(sshTmp);
        setIfBlank("user.home", sshHome.getAbsolutePath(), status);
        setIfBlank("user.dir", sshBase.getAbsolutePath(), status);
        setIfBlank("java.io.tmpdir", sshTmp.getAbsolutePath(), status);
        setIfBlank("user.name", "perms", status);
    }

    private static void ensureDir(File dir) {
        if (dir != null && !dir.exists()) dir.mkdirs();
    }

    private static void setIfBlank(String key, String value, StringBuilder status) {
        String current = System.getProperty(key);
        if (!TextUtils.isEmpty(current)) return;
        System.setProperty(key, value);
        appendStatus(status, key + "=" + value);
    }

    private static void appendStatus(StringBuilder sb, String line) {
        if (sb == null || TextUtils.isEmpty(line)) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(line);
    }

    static String status() {
        synchronized (LOCK) {
            return lastStatus == null ? "" : lastStatus;
        }
    }

    static String describeThrowable(Throwable throwable) {
        if (throwable == null) return "Unknown error";
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 8) {
            if (sb.length() > 0) sb.append("; caused by ");
            String name = current.getClass().getName();
            String message = current.getMessage();
            sb.append(name == null ? current.getClass().getSimpleName() : name);
            if (!TextUtils.isEmpty(message)) sb.append(": ").append(message);
            StackTraceElement[] trace = current.getStackTrace();
            if (trace != null && trace.length > 0) {
                sb.append(" at ").append(trace[0].getClassName()).append('.').append(trace[0].getMethodName()).append(':').append(trace[0].getLineNumber());
            }
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
            depth++;
        }
        return sb.toString();
    }
}
