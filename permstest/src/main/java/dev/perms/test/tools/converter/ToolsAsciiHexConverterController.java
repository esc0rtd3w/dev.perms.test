package dev.perms.test.tools.converter;

import android.app.Activity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import dev.perms.test.R;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.NoFilterArrayAdapter;

/** Reusable live text/value converter for ASCII, hex, decimal, octal, and binary byte forms. */
public final class ToolsAsciiHexConverterController {
    public interface Host {
        Activity getActivity();
        View getRootView();
        void appendOutput(String message);
    }

    private static final String[] ENCODINGS = new String[] {
            "ASCII",
            "ASCII/UTF-8",
            "UTF-16",
            "UTF-16 little endian",
            "UTF-16 big endian",
            "Windows-1252",
            "Big5 (Chinese)",
            "CP866 (Russian)",
            "EUC-JP (Japanese)",
            "EUC-KR (Korean)",
            "GB 18030 (Chinese)",
            "GB 2312 (Chinese)",
            "ISO-2022-CN (Chinese)",
            "ISO-2022-JP (Japanese)",
            "ISO-8859-1 (Latin1/Western European)",
            "ISO-8859-2 (Latin2/Eastern European)",
            "ISO-8859-3 (Latin3/South European)",
            "ISO-8859-4 (Latin4/North European)",
            "ISO-8859-5 (Latin/Cyrillic)"
    };

    private static final String[] VALUE_TYPES = new String[] {
            "Hexadecimal",
            "Decimal",
            "Octal",
            "Binary",
            "Text"
    };

    private static final String[] DELIMITERS = new String[] {
            "Space",
            "Comma",
            "None",
            "User defined"
    };

    private final Host host;
    private boolean updating;

    public ToolsAsciiHexConverterController(Host host) {
        this.host = host;
    }

    public void bind() {
        Activity activity = activity();
        Views b = views();
        if (activity == null || b == null) return;

        bindDropdown(b.ddToolsAsciiEncoding, ENCODINGS, "ASCII/UTF-8", () -> updateValuesFromText());
        bindDropdown(b.ddToolsAsciiValueType, VALUE_TYPES, "Hexadecimal", () -> updateValuesFromText());
        bindDropdown(b.ddToolsAsciiDelimiter, DELIMITERS, "Space", () -> {
            if (b.tilToolsAsciiCustomDelimiter != null) {
                b.tilToolsAsciiCustomDelimiter.setVisibility("User defined".contentEquals(b.ddToolsAsciiDelimiter.getText()) ? View.VISIBLE : View.GONE);
            }
            updateValuesFromText();
        });

        b.edtToolsAsciiText.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (!updating) updateValuesFromText();
            }
        });
        b.edtToolsAsciiValues.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (!updating) updateTextFromValues();
            }
        });
        b.edtToolsAsciiCustomDelimiter.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (!updating) updateValuesFromText();
            }
        });
        updateValuesFromText();
    }

    private void bindDropdown(MaterialAutoCompleteTextView view, String[] values, String initial, Runnable afterChange) {
        Activity activity = activity();
        if (activity == null || view == null) return;
        view.setAdapter(new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, values));
        view.setText(initial, false);
        DropdownUi.bindExposedDropdown(activity, textInputLayoutFor(view), view,
                () -> DropdownUi.showDropdown(view));
        view.setOnItemClickListener((parent, itemView, position, id) -> {
            if (afterChange != null) afterChange.run();
        });
    }

    private void updateValuesFromText() {
        Views b = views();
        if (b == null) return;
        updating = true;
        try {
            String text = b.edtToolsAsciiText.getText() == null ? "" : b.edtToolsAsciiText.getText().toString();
            byte[] bytes = text.getBytes(selectedCharset());
            b.edtToolsAsciiValues.setText(formatBytes(bytes));
            setStatus("Text converted to " + selectedValueType().toLowerCase(Locale.US) + ".");
        } catch (Throwable t) {
            setStatus("Text conversion failed: " + safeMessage(t));
        } finally {
            updating = false;
        }
    }

    private void updateTextFromValues() {
        Views b = views();
        if (b == null) return;
        updating = true;
        try {
            String values = b.edtToolsAsciiValues.getText() == null ? "" : b.edtToolsAsciiValues.getText().toString();
            byte[] bytes = parseValues(values);
            b.edtToolsAsciiText.setText(new String(bytes, selectedCharset()));
            setStatus(selectedValueType() + " values converted to text.");
        } catch (Throwable t) {
            setStatus("Waiting for valid values: " + safeMessage(t));
        } finally {
            updating = false;
        }
    }

    private String formatBytes(byte[] bytes) {
        String type = selectedValueType();
        if ("Text".equals(type)) return new String(bytes, selectedCharset());
        String delimiter = delimiter();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            if (i > 0) out.append(delimiter);
            if ("Binary".equals(type)) out.append(pad(Integer.toBinaryString(v), 8));
            else if ("Decimal".equals(type)) out.append(v);
            else if ("Octal".equals(type)) out.append(pad(Integer.toOctalString(v), 3));
            else out.append(pad(Integer.toHexString(v).toUpperCase(Locale.US), 2));
        }
        return out.toString();
    }

    private byte[] parseValues(String values) {
        String type = selectedValueType();
        if ("Text".equals(type)) return values == null ? new byte[0] : values.getBytes(selectedCharset());
        String raw = values == null ? "" : values.trim();
        if (TextUtils.isEmpty(raw)) return new byte[0];
        String[] tokens;
        if ("None".equals(selectedDelimiter()) && "Hexadecimal".equals(type)) {
            String compact = raw.replaceAll("(?i)0x", "").replaceAll("[^0-9a-fA-F]", "");
            if ((compact.length() & 1) != 0) throw new IllegalArgumentException("Hex length must be even");
            tokens = new String[compact.length() / 2];
            for (int i = 0; i < tokens.length; i++) tokens[i] = compact.substring(i * 2, i * 2 + 2);
        } else {
            tokens = raw.split("[\\s,;:]+|" + quotedDelimiterRegex());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String token : tokens) {
            if (TextUtils.isEmpty(token)) continue;
            int value;
            if ("Binary".equals(type)) value = Integer.parseInt(cleanPrefix(token, "0b"), 2);
            else if ("Decimal".equals(type)) value = Integer.parseInt(token.trim(), 10);
            else if ("Octal".equals(type)) value = Integer.parseInt(cleanPrefix(token, "0o"), 8);
            else value = Integer.parseInt(cleanPrefix(token, "0x"), 16);
            if (value < 0 || value > 255) throw new IllegalArgumentException("Byte out of range: " + token);
            out.write(value);
        }
        return out.toByteArray();
    }

    private String cleanPrefix(String value, String prefix) {
        String token = value == null ? "" : value.trim();
        if (token.regionMatches(true, 0, prefix, 0, prefix.length())) return token.substring(prefix.length());
        return token;
    }

    private String quotedDelimiterRegex() {
        String delimiter = delimiter();
        if (TextUtils.isEmpty(delimiter)) return "\\b\\B";
        return java.util.regex.Pattern.quote(delimiter);
    }

    private String delimiter() {
        String selected = selectedDelimiter();
        if ("Comma".equals(selected)) return ", ";
        if ("None".equals(selected)) return "";
        if ("User defined".equals(selected)) {
            Views b = views();
            String custom = b == null || b.edtToolsAsciiCustomDelimiter.getText() == null
                    ? ""
                    : b.edtToolsAsciiCustomDelimiter.getText().toString();
            return custom;
        }
        return " ";
    }

    private String selectedDelimiter() {
        Views b = views();
        return b == null || b.ddToolsAsciiDelimiter.getText() == null ? "Space" : b.ddToolsAsciiDelimiter.getText().toString();
    }

    private String selectedValueType() {
        Views b = views();
        return b == null || b.ddToolsAsciiValueType.getText() == null ? "Hexadecimal" : b.ddToolsAsciiValueType.getText().toString();
    }

    private Charset selectedCharset() {
        Views b = views();
        String label = b == null || b.ddToolsAsciiEncoding.getText() == null ? "ASCII/UTF-8" : b.ddToolsAsciiEncoding.getText().toString();
        try {
            if ("ASCII".equals(label)) return StandardCharsets.US_ASCII;
            if ("ASCII/UTF-8".equals(label)) return StandardCharsets.UTF_8;
            if ("UTF-16".equals(label)) return StandardCharsets.UTF_16;
            if ("UTF-16 little endian".equals(label)) return StandardCharsets.UTF_16LE;
            if ("UTF-16 big endian".equals(label)) return StandardCharsets.UTF_16BE;
            if ("Windows-1252".equals(label)) return Charset.forName("windows-1252");
            if (label.startsWith("Big5")) return Charset.forName("Big5");
            if (label.startsWith("CP866")) return Charset.forName("CP866");
            if (label.startsWith("EUC-JP")) return Charset.forName("EUC-JP");
            if (label.startsWith("EUC-KR")) return Charset.forName("EUC-KR");
            if (label.startsWith("GB 18030")) return Charset.forName("GB18030");
            if (label.startsWith("GB 2312")) return Charset.forName("GB2312");
            if (label.startsWith("ISO-2022-CN")) return Charset.forName("ISO-2022-CN");
            if (label.startsWith("ISO-2022-JP")) return Charset.forName("ISO-2022-JP");
            if (label.startsWith("ISO-8859-1")) return StandardCharsets.ISO_8859_1;
            if (label.startsWith("ISO-8859-2")) return Charset.forName("ISO-8859-2");
            if (label.startsWith("ISO-8859-3")) return Charset.forName("ISO-8859-3");
            if (label.startsWith("ISO-8859-4")) return Charset.forName("ISO-8859-4");
            if (label.startsWith("ISO-8859-5")) return Charset.forName("ISO-8859-5");
        } catch (Throwable ignored) {
        }
        return StandardCharsets.UTF_8;
    }

    private static String pad(String value, int width) {
        String v = value == null ? "" : value;
        while (v.length() < width) v = "0" + v;
        return v;
    }

    private void setStatus(String message) {
        Views b = views();
        if (b != null && b.txtToolsAsciiStatus != null) b.txtToolsAsciiStatus.setText(message == null ? "" : message);
    }

    private com.google.android.material.textfield.TextInputLayout textInputLayoutFor(MaterialAutoCompleteTextView view) {
        Views b = views();
        if (b == null || view == null) return null;
        if (view == b.ddToolsAsciiEncoding) return b.tilToolsAsciiEncoding;
        if (view == b.ddToolsAsciiValueType) return b.tilToolsAsciiValueType;
        if (view == b.ddToolsAsciiDelimiter) return b.tilToolsAsciiDelimiter;
        return null;
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private Views views() {
        View root = host == null ? null : host.getRootView();
        return root == null ? null : new Views(root);
    }

    private static final class Views {
        final com.google.android.material.textfield.TextInputLayout tilToolsAsciiEncoding;
        final MaterialAutoCompleteTextView ddToolsAsciiEncoding;
        final com.google.android.material.textfield.TextInputLayout tilToolsAsciiValueType;
        final MaterialAutoCompleteTextView ddToolsAsciiValueType;
        final com.google.android.material.textfield.TextInputLayout tilToolsAsciiDelimiter;
        final MaterialAutoCompleteTextView ddToolsAsciiDelimiter;
        final com.google.android.material.textfield.TextInputLayout tilToolsAsciiCustomDelimiter;
        final com.google.android.material.textfield.TextInputEditText edtToolsAsciiCustomDelimiter;
        final com.google.android.material.textfield.TextInputEditText edtToolsAsciiText;
        final com.google.android.material.textfield.TextInputEditText edtToolsAsciiValues;
        final android.widget.TextView txtToolsAsciiStatus;

        Views(View root) {
            tilToolsAsciiEncoding = root.findViewById(R.id.tilToolsAsciiEncoding);
            ddToolsAsciiEncoding = root.findViewById(R.id.ddToolsAsciiEncoding);
            tilToolsAsciiValueType = root.findViewById(R.id.tilToolsAsciiValueType);
            ddToolsAsciiValueType = root.findViewById(R.id.ddToolsAsciiValueType);
            tilToolsAsciiDelimiter = root.findViewById(R.id.tilToolsAsciiDelimiter);
            ddToolsAsciiDelimiter = root.findViewById(R.id.ddToolsAsciiDelimiter);
            tilToolsAsciiCustomDelimiter = root.findViewById(R.id.tilToolsAsciiCustomDelimiter);
            edtToolsAsciiCustomDelimiter = root.findViewById(R.id.edtToolsAsciiCustomDelimiter);
            edtToolsAsciiText = root.findViewById(R.id.edtToolsAsciiText);
            edtToolsAsciiValues = root.findViewById(R.id.edtToolsAsciiValues);
            txtToolsAsciiStatus = root.findViewById(R.id.txtToolsAsciiStatus);
        }
    }

    private static String safeMessage(Throwable t) {
        String msg = t == null ? null : t.getMessage();
        return TextUtils.isEmpty(msg) ? String.valueOf(t) : msg;
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
