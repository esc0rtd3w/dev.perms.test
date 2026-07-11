package dev.perms.test.files;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Small external-files helper for URI staging and app-private text output. */
public final class AppExternalFileStore {
    public interface Output {
        void append(String text);
    }

    private final Context context;
    private final Output output;

    public AppExternalFileStore(Context context, Output output) {
        this.context = context;
        this.output = output;
    }

    public File copyUriToExternalDir(Uri uri, String subdir, String filename) {
        try {
            if (context == null || uri == null) return null;
            File dir = context.getExternalFilesDir(subdir);
            if (dir == null) return null;
            if (!dir.exists()) dir.mkdirs();

            File out = new File(dir, filename == null ? "file.bin" : filename);
            InputStream source;
            try {
                if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                    source = new java.io.FileInputStream(new File(uri.getPath()));
                } else {
                    source = context.getContentResolver().openInputStream(uri);
                }
            } catch (Throwable t) {
                source = null;
            }
            try (InputStream in = source;
                 FileOutputStream fos = new FileOutputStream(out, false)) {
                if (in == null) return null;
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                fos.flush();
            }
            return out;
        } catch (Throwable t) {
            append("[!] Copy failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            return null;
        }
    }

    public File writeTextToExternalDir(String subdir, String filename, String text) {
        try {
            if (context == null) return null;
            File dir = context.getExternalFilesDir(subdir);
            if (dir == null) return null;
            if (!dir.exists()) dir.mkdirs();

            File out = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(out, false)) {
                if (text == null) text = "";
                fos.write(text.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            return out;
        } catch (Throwable t) {
            append("[!] Write failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            return null;
        }
    }

    public String queryDisplayName(Uri uri) {
        if (context == null || uri == null) return null;
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static String sanitizeFilename(String s) {
        if (s == null) return "file.bin";
        String out = s.trim();
        if (out.isEmpty()) out = "file.bin";
        out = out.replaceAll("[\\/\r\n\t\0]", "_");
        out = out.replaceAll("[^a-zA-Z0-9._ -]", "_");
        if (out.length() > 128) out = out.substring(out.length() - 128);
        return out;
    }

    private void append(String text) {
        if (output != null) output.append(text);
    }
}
