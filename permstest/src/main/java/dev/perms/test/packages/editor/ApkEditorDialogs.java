package dev.perms.test.packages.editor;

import android.content.Context;

import java.io.File;

import dev.perms.test.ui.dialog.GenericViewerDialog;

/** APK Editor dialog adapter kept for package-editor callers. */
public final class ApkEditorDialogs {
    public interface SaveHost {
        void onSaved(File file);
        void onError(String label, Throwable error);
    }

    private ApkEditorDialogs() {
    }

    public static void showText(Context context, String title, String text) {
        GenericViewerDialog.showText(context, title, null, text);
    }

    public static void showEditableFile(Context context, File file, String title, SaveHost host) {
        showEditableFile(context, file, title, false, host);
    }

    public static void showEditableFile(Context context, File file, String title, boolean syntaxXml, SaveHost host) {
        GenericViewerDialog.showEditableFile(context, file, title, syntaxXml, host == null ? null : new GenericViewerDialog.SaveHost() {
            @Override
            public void onSaved(File savedFile) {
                host.onSaved(savedFile);
            }

            @Override
            public void onError(String label, Throwable error) {
                host.onError(label, error);
            }
        });
    }
}
