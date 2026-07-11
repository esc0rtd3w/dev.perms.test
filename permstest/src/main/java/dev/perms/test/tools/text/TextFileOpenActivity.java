package dev.perms.test.tools.text;

import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import dev.perms.test.MainActivity;
import dev.perms.test.tools.intentreceiver.IntentCaptureActivity;
import dev.perms.test.tools.intentreceiver.IntentReceiverRuntime;

/** Lightweight file-open bridge for selecting PermsTest as an external text editor. */
public final class TextFileOpenActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent source = getIntent();
            Uri uri = source == null ? null : source.getData();
            if (uri == null && source != null) {
                try { uri = source.getParcelableExtra(Intent.EXTRA_STREAM); } catch (Throwable ignored) {}
            }
            if (uri == null) {
                finish();
                return;
            }

            String label = queryDisplayName(uri);
            if (TextUtils.isEmpty(label)) label = uri.toString();

            if (IntentReceiverRuntime.shouldCaptureTextOpen(this)) {
                openIntentCapture(source, uri, label);
                return;
            }

            Intent target = new Intent(this, MainActivity.class);
            target.setAction(Intent.ACTION_EDIT);
            target.setData(uri);
            target.putExtra(ToolsTextEditorController.EXTRA_OPEN_TEXT_EDITOR_URI, uri);
            target.putExtra(ToolsTextEditorController.EXTRA_OPEN_TEXT_EDITOR_LABEL, label);
            target.putExtra(ToolsTextEditorController.EXTRA_SCROLL_TO_TEXT_EDITOR, true);
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { target.setClipData(ClipData.newUri(getContentResolver(), label, uri)); } catch (Throwable ignored) {}
            startActivity(target);
        } catch (Throwable t) {
            Toast.makeText(this, "Text editor open failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            finish();
        }
    }

    private void openIntentCapture(Intent source, Uri uri, String label) {
        Intent capture = source == null ? new Intent(Intent.ACTION_VIEW) : new Intent(source);
        capture.setClass(this, IntentCaptureActivity.class);
        capture.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            String type = source == null ? null : source.getType();
            if (!TextUtils.isEmpty(type)) capture.setDataAndType(uri, type);
            else capture.setData(uri);
        } catch (Throwable ignored) {
            try { capture.setData(uri); } catch (Throwable ignored2) {}
        }
        try { capture.setClipData(ClipData.newUri(getContentResolver(), label, uri)); } catch (Throwable ignored) {}
        startActivity(capture);
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
        }
        return null;
    }
}
