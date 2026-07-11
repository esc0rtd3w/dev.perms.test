package dev.perms.test.logging;

import android.content.Context;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Handles Logging tab lifetime-log actions while MainActivity keeps shared app state ownership.
 */
public final class LoggingLifetimeActions {
    private final Context context;
    private final ActivityMainBinding binding;
    private final OutputAppender outputAppender;
    private final OutputTagSetter outputTagSetter;
    private final LastSavedFileSetter lastSavedFileSetter;
    private final ShellSuccessRunner shellSuccessRunner;

    public LoggingLifetimeActions(Context context,
                                  ActivityMainBinding binding,
                                  OutputAppender outputAppender,
                                  OutputTagSetter outputTagSetter,
                                  LastSavedFileSetter lastSavedFileSetter,
                                  ShellSuccessRunner shellSuccessRunner) {
        this.context = context;
        this.binding = binding;
        this.outputAppender = outputAppender;
        this.outputTagSetter = outputTagSetter;
        this.lastSavedFileSetter = lastSavedFileSetter;
        this.shellSuccessRunner = shellSuccessRunner;
    }

    public void markSession() {
        LifetimeLogStore.addSessionMarkerAsync(context,
                "Session " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        append("[i] Added session marker to lifetime log.\n");
    }

    public void show() {
        setOutputTag("lifetime_log");
        String text = LifetimeLogStore.readForDisplay(context, 256 * 1024);
        if (binding == null || binding.txtOutput == null) return;
        if (TextUtils.isEmpty(text)) {
            binding.txtOutput.setText("[i] Lifetime log is empty.\n");
        } else {
            binding.txtOutput.setText(text);
        }
        if (binding.scrollOutput != null) {
            binding.scrollOutput.post(() -> binding.scrollOutput.fullScroll(android.view.View.FOCUS_DOWN));
        }
    }

    public void export() {
        File f = LifetimeLogStore.exportCopy(context);
        if (f == null || !f.exists()) {
            append("[!] Export failed.\n");
            return;
        }
        String publicPath = copyToPublicRoot(f);
        if (lastSavedFileSetter != null) lastSavedFileSetter.setFile(f);
        if (binding != null && binding.tabLogging != null && binding.tabLogging.btnShareLast != null) {
            binding.tabLogging.btnShareLast.setEnabled(true);
        }
        append("[+] Exported lifetime log to: " + publicPath + "\n");
    }

    public void confirmClear() {
        new AlertDialog.Builder(context)
                .setTitle("Clear lifetime log?")
                .setMessage("This will delete perms_test_actions.txt (your persistent action log).")
                .setPositiveButton("Clear", (d, w) -> {
                    LifetimeLogStore.clearAsync(context);
                    append("[i] Lifetime log cleared.\n");
                })
                .setNegativeButton(R.string.shell_action_cancel, null)
                .show();
    }

    private String copyToPublicRoot(File source) {
        if (source == null) return "";
        String root = "/sdcard/dev.perms.test/logs/lifetime";
        String sourcePath = source.getAbsolutePath();
        if (sourcePath != null && sourcePath.startsWith(root + "/")) {
            return sourcePath;
        }
        String publicPath = root + "/" + source.getName();
        boolean copied = shellSuccessRunner != null && shellSuccessRunner.run("mkdir -p " + shQuote(root)
                + " && cp -f " + shQuote(source.getAbsolutePath()) + " " + shQuote(publicPath)
                + " && chmod 0666 " + shQuote(publicPath));
        return copied ? publicPath : source.getAbsolutePath();
    }

    private void append(String text) {
        if (outputAppender != null) outputAppender.append(text);
    }

    private void setOutputTag(String tag) {
        if (outputTagSetter != null) outputTagSetter.setTag(tag);
    }

    private static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    public interface OutputAppender {
        void append(String text);
    }

    public interface OutputTagSetter {
        void setTag(String tag);
    }

    public interface LastSavedFileSetter {
        void setFile(File file);
    }

    public interface ShellSuccessRunner {
        boolean run(String command);
    }
}
