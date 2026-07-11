package dev.perms.test.vr;

import dev.perms.test.memory.overlay.MemoryOverlayService;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Quest/Horizon-safe text entry bridge for the memory overlay.
 *
 * Raw service overlays on Horizon OS are kept non-focusable so they do not become
 * long-lived focus barriers. Text entry is moved into a short-lived activity panel
 * and the result is sent back to the running overlay service.
 */
public final class MemoryOverlayVrTextInputActivity extends Activity {
    private String fieldKey;
    private EditText editText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        fieldKey = intent == null ? "" : intent.getStringExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_KEY);
        String label = intent == null ? "Input" : intent.getStringExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_LABEL);
        String value = intent == null ? "" : intent.getStringExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_VALUE);
        String hint = intent == null ? "" : intent.getStringExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_HINT);
        if (TextUtils.isEmpty(label)) label = "Input";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(18f);
        title.setGravity(Gravity.START);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        editText.setText(value == null ? "" : value);
        editText.setHint(TextUtils.isEmpty(hint) ? label : hint);
        editText.setSelectAllOnFocus(false);
        editText.setSelection(editText.getText() == null ? 0 : editText.getText().length());
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(12);
        root.addView(editText, inputParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(16);

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> finishWithResult(true));
        buttons.addView(cancel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button ok = new Button(this);
        ok.setText("OK");
        ok.setOnClickListener(v -> finishWithResult(false));
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        okParams.leftMargin = dp(10);
        buttons.addView(ok, okParams);

        root.addView(buttons, buttonsParams);
        setContentView(root);

        editText.requestFocus();
        editText.postDelayed(() -> {
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            } catch (Throwable ignored) {
            }
        }, 150L);
    }

    private void finishWithResult(boolean cancelled) {
        Intent result = new Intent(this, MemoryOverlayService.class);
        result.setAction(PermsTestVrOverlayCompat.ACTION_TEXT_INPUT_RESULT);
        result.putExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_KEY, fieldKey);
        result.putExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_VALUE,
                editText == null || editText.getText() == null ? "" : editText.getText().toString());
        result.putExtra(PermsTestVrOverlayCompat.EXTRA_CANCELLED, cancelled);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(result);
            } else {
                startService(result);
            }
        } catch (Throwable ignored) {
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        finishWithResult(true);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
