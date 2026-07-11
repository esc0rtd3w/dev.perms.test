package dev.perms.test.shell;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Storage helpers for Shell custom command import/export files.
 */
public final class CustomCommandStorage {
    private static final Uri DOWNLOADS_EXTERNAL_URI = Uri.parse("content://media/external/downloads");

    private CustomCommandStorage() {
    }

    public static Uri writeTextToDownloads(Context context, String displayName, String mimeType, String text) {
        try {
            if (context == null) return null;
            if (displayName == null || displayName.trim().isEmpty()) return null;
            ContentResolver cr = context.getContentResolver();
            Uri downloads = DOWNLOADS_EXTERNAL_URI;

            // Overwrite previous file with same name (best-effort).
            try (Cursor c = cr.query(
                    downloads,
                    new String[] { "_id" },
                    "display_name=?",
                    new String[] { displayName },
                    null
            )) {
                if (c != null) {
                    while (c.moveToNext()) {
                        long id = c.getLong(0);
                        Uri u = ContentUris.withAppendedId(downloads, id);
                        try { cr.delete(u, null, null); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            ContentValues v = new ContentValues();
            v.put("display_name", displayName);
            if (mimeType != null && !mimeType.trim().isEmpty()) v.put("mime_type", mimeType);
            v.put("relative_path", Environment.DIRECTORY_DOWNLOADS + "/");
            v.put("is_pending", 1);

            Uri uri = cr.insert(downloads, v);
            if (uri == null) return null;

            try (OutputStream out = cr.openOutputStream(uri, "w")) {
                if (out == null) return null;
                byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
                out.write(bytes);
                out.flush();
            }

            try {
                ContentValues done = new ContentValues();
                done.put("is_pending", 0);
                cr.update(uri, done, null, null);
            } catch (Throwable ignored) {}

            return uri;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String readTextFromDownloads(Context context, String displayName) {
        try {
            if (context == null) return null;
            if (displayName == null || displayName.trim().isEmpty()) return null;
            ContentResolver cr = context.getContentResolver();
            Uri downloads = DOWNLOADS_EXTERNAL_URI;

            try (Cursor c = cr.query(
                    downloads,
                    new String[] { "_id" },
                    "display_name=?",
                    new String[] { displayName },
                    "date_modified DESC"
            )) {
                if (c == null) return null;
                if (!c.moveToFirst()) return null;

                long id = c.getLong(0);
                Uri uri = ContentUris.withAppendedId(downloads, id);
                try (InputStream in = cr.openInputStream(uri)) {
                    if (in == null) return null;
                    return readAll(in);
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String readAll(InputStream in) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
