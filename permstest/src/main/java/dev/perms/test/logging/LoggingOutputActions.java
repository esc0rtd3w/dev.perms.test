package dev.perms.test.logging;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.storage.StorageAccessController;

/**
 * Handles Logging tab output save/share actions while MainActivity owns the shared output state.
 */
public final class LoggingOutputActions {
    private final Activity activity;
    private final ActivityMainBinding binding;
    private final OutputAppender outputAppender;
    private final Runnable refreshStatus;
    private final OutputTagProvider outputTagProvider;
    private final LastSavedFileProvider lastSavedFileProvider;
    private final LastSavedFileSetter lastSavedFileSetter;

    public LoggingOutputActions(Activity activity,
                                ActivityMainBinding binding,
                                OutputAppender outputAppender,
                                Runnable refreshStatus,
                                OutputTagProvider outputTagProvider,
                                LastSavedFileProvider lastSavedFileProvider,
                                LastSavedFileSetter lastSavedFileSetter) {
        this.activity = activity;
        this.binding = binding;
        this.outputAppender = outputAppender;
        this.refreshStatus = refreshStatus;
        this.outputTagProvider = outputTagProvider;
        this.lastSavedFileProvider = lastSavedFileProvider;
        this.lastSavedFileSetter = lastSavedFileSetter;
    }

    public void save() {
        String text = binding == null || binding.txtOutput == null || binding.txtOutput.getText() == null
                ? ""
                : binding.txtOutput.getText().toString();
        if (TextUtils.isEmpty(text.trim())) {
            append("[!] Nothing to save.\n");
            return;
        }

        if (!StorageAccessController.ensureSharedStorageWriteAccess(
                activity,
                "Logging Save",
                "/sdcard/dev.perms.test/logs",
                outputAppender == null ? null : outputAppender::append)) {
            return;
        }

        File dir = new File(Environment.getExternalStorageDirectory(), "dev.perms.test/logs");
        if (!dir.exists() && !dir.mkdirs()) {
            File base = activity == null ? null : activity.getExternalFilesDir(null);
            if (base == null) {
                append("[!] External files dir unavailable.\n");
                return;
            }
            dir = new File(base, "logs");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String tag = sanitizeFileTag(outputTagProvider == null ? null : outputTagProvider.getTag());
        File file = new File(dir, "PermsTest_" + tag + "_" + ts + ".txt");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            try { file.setReadable(true, false); } catch (Throwable ignored) {}
            if (lastSavedFileSetter != null) lastSavedFileSetter.setFile(file);
            append("[+] Saved: " + file.getAbsolutePath() + "\n");
            if (refreshStatus != null) refreshStatus.run();
        } catch (Exception e) {
            append("[!] Save failed: " + e + "\n");
        }
    }

    public void share() {
        File lastSavedFile = lastSavedFileProvider == null ? null : lastSavedFileProvider.getFile();
        if (lastSavedFile == null || !lastSavedFile.exists()) {
            append("[!] No saved file yet. Tap Save first.\n");
            if (refreshStatus != null) refreshStatus.run();
            return;
        }
        try {
            String authority = activity == null ? null : activity.getPackageName() + ".files";
            Uri uri = FileProvider.getUriForFile(activity, authority, lastSavedFile);
            append("[i] Sharing: " + lastSavedFile.getName() + "\n");

            Toast.makeText(activity, "Opening share chooser...", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            try {
                intent.setDataAndType(uri, "text/plain");
            } catch (Throwable ignored) {}
            intent.putExtra(Intent.EXTRA_SUBJECT, "PermsTest log");
            intent.putExtra(Intent.EXTRA_TEXT, lastSavedFile.getName());

            // Some targets on newer Android versions require ClipData for URI grants.
            intent.setClipData(ClipData.newUri(activity.getContentResolver(), "log", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "Share log");
            // Some ROMs return null for resolveActivity() on chooser intents; just try and catch instead.
            activity.startActivity(chooser);
        } catch (Exception e) {
            append("[!] Share failed: " + e + "\n");
            Toast.makeText(activity, "Share failed: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private static String sanitizeFileTag(String in) {
        if (in == null) return "output";
        String s = in.trim();
        if (s.isEmpty()) return "output";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
            if (ok) sb.append(c);
            else if (c == '.') sb.append('_');
        }
        String out = sb.toString();
        return out.isEmpty() ? "output" : out;
    }

    private void append(String text) {
        if (outputAppender != null) outputAppender.append(text);
    }

    public interface OutputAppender {
        void append(String text);
    }

    public interface OutputTagProvider {
        String getTag();
    }

    public interface LastSavedFileProvider {
        File getFile();
    }

    public interface LastSavedFileSetter {
        void setFile(File file);
    }
}
