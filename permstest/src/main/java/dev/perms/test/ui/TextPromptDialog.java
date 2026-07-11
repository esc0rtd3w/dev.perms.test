package dev.perms.test.ui;

import android.content.Context;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

/** Shared small text-input dialog helper used by feature controllers. */
public final class TextPromptDialog {
    private TextPromptDialog() {
    }

    public interface Callback {
        void onValue(String value);
    }

    public interface ErrorSink {
        void onError(Throwable t);
    }

    public static void show(
            Context context,
            String title,
            String hint,
            String preset,
            Callback callback,
            ErrorSink errorSink) {
        try {
            final EditText input = new EditText(context);
            input.setHint(hint);
            if (preset != null) {
                input.setText(preset);
            }

            new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setView(input)
                    .setPositiveButton("OK", (dialog, which) -> {
                        if (callback != null) {
                            callback.onValue(input.getText() == null ? null : input.getText().toString());
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        if (callback != null) {
                            callback.onValue(null);
                        }
                    })
                    .show();
        } catch (Throwable t) {
            if (errorSink != null) {
                errorSink.onError(t);
            }
            if (callback != null) {
                callback.onValue(null);
            }
        }
    }
}
