package dev.perms.test.tools.intentreceiver;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import dev.perms.test.MainActivity;

/** Displays captured intent details without adding intent-handling logic to MainActivity. */
public final class IntentCaptureActivity extends AppCompatActivity {
    private String formattedIntent = "";
    private boolean openingPermsTest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        formattedIntent = IntentCaptureFormatter.format(getIntent());
        IntentReceiverEventStore.record(this, formattedIntent);
        showCaptureDialog();
    }

    private void showCaptureDialog() {
        TextView body = new TextView(this);
        body.setText(formattedIntent);
        body.setTextIsSelectable(true);
        body.setTextSize(13f);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = dp(12);
        body.setPadding(pad, pad, pad, pad);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("PermsTest captured intent")
                .setMessage("Review the incoming intent details. Close returns to the source app; Open PermsTest opens the full app.")
                .setView(scroll)
                .setPositiveButton("Open PermsTest", (d, which) -> openPermsTest())
                .setNeutralButton("Copy", null)
                .setNegativeButton("Close", (d, which) -> closeCapture())
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> copyCapture());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> openPermsTest());
        });
        dialog.setOnDismissListener(d -> {
            if (!openingPermsTest && !isFinishing()) closeCapture();
        });
        dialog.show();
    }

    private void copyCapture() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PermsTest captured intent", formattedIntent));
        Toast.makeText(this, "Captured intent copied", Toast.LENGTH_SHORT).show();
    }

    private void closeCapture() {
        try {
            finishAndRemoveTask();
        } catch (Throwable ignored) {
            finish();
        }
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
    }

    @Override
    public void onBackPressed() {
        closeCapture();
    }

    private void openPermsTest() {
        openingPermsTest = true;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
