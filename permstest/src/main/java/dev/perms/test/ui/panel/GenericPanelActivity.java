package dev.perms.test.ui.panel;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import dev.perms.test.R;
import dev.perms.test.editor.SourceSyntaxHighlighter;
import dev.perms.test.ui.PermsTestUiCompat;

/** Generic Activity-hosted panel for app/plugin/output views. */
public class GenericPanelActivity extends Activity {
    private String panelKey;
    private TextView title;
    private TextView subtitle;
    private EditText content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generic_panel);
        title = findViewById(R.id.txtGenericPanelTitle);
        subtitle = findViewById(R.id.txtGenericPanelSubtitle);
        content = findViewById(R.id.edtGenericPanelContent);
        findViewById(R.id.btnGenericPanelClose).setOnClickListener(v -> finish());
        PermsTestUiCompat.applyActivityUiProfile(this, getWindow().getDecorView());
        configureContent();
        loadRequest();
    }

    @Override
    protected void onDestroy() {
        try { GenericPanelLauncher.releasePanelKey(panelKey); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    private void configureContent() {
        if (content == null) return;
        content.setInputType(InputType.TYPE_NULL);
        content.setKeyListener(null);
        content.setFocusable(false);
        content.setTextIsSelectable(true);
        content.setTypeface(android.graphics.Typeface.MONOSPACE);
        content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        content.setGravity(Gravity.START | Gravity.TOP);
        content.setSingleLine(false);
        content.setHorizontallyScrolling(true);
        content.setMinLines(20);
    }

    private void loadRequest() {
        String panelTitle = stringExtra(GenericPanelLauncher.EXTRA_TITLE, "PermsTest Panel");
        String panelSubtitle = stringExtra(GenericPanelLauncher.EXTRA_SUBTITLE, "");
        String syntax = stringExtra(GenericPanelLauncher.EXTRA_SYNTAX, "");
        panelKey = stringExtra(GenericPanelLauncher.EXTRA_PANEL_KEY, "generic");
        String path = stringExtra(GenericPanelLauncher.EXTRA_CONTENT_FILE, "");
        String text = readText(path);

        if (title != null) title.setText(panelTitle);
        if (subtitle != null) subtitle.setText(panelSubtitle);
        if (content != null) {
            content.setText(TextUtils.isEmpty(text) ? "No panel content was provided." : text);
            content.setSelection(0);
            applySyntax(content, syntax);
        }
        try { setTitle(panelTitle); } catch (Throwable ignored) {}
        try { setTaskDescription(new ActivityManager.TaskDescription(panelTitle)); } catch (Throwable ignored) {}
    }

    private String stringExtra(String key, String fallback) {
        try {
            String value = getIntent() == null ? null : getIntent().getStringExtra(key);
            return TextUtils.isEmpty(value) ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String readText(String path) {
        if (TextUtils.isEmpty(path)) return "";
        try {
            File file = new File(path);
            if (!file.isFile()) return "";
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
                 ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), 2L * 1024L * 1024L))) {
                byte[] buffer = new byte[32768];
                int r;
                while ((r = in.read(buffer)) > 0) out.write(buffer, 0, r);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            return "Unable to load panel content:\n" + t;
        }
    }

    private static void applySyntax(EditText view, String syntax) {
        if (view == null || TextUtils.isEmpty(syntax)) return;
        try {
            if (!SourceSyntaxHighlighter.canHighlightLength(view.length())) return;
            String s = syntax.trim().toLowerCase(Locale.US);
            if ("smali".equals(s)) SourceSyntaxHighlighter.applySmali(view);
            else if ("shell".equals(s) || "bash".equals(s) || "sh".equals(s)) SourceSyntaxHighlighter.applyShell(view);
            else if ("json".equals(s)) SourceSyntaxHighlighter.applyJson(view);
            else if ("properties".equals(s) || "prop".equals(s) || "ini".equals(s)) SourceSyntaxHighlighter.applyProperties(view);
            else if ("web".equals(s) || "html".equals(s) || "css".equals(s) || "js".equals(s)) SourceSyntaxHighlighter.applyWeb(view);
        } catch (Throwable ignored) {
        }
    }
}
