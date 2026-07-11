package dev.perms.test.debugging.editor;

import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight syntax coloring shared by text editors that live inside PermsTest.
 *
 * This class intentionally only applies foreground spans. It does not own editor sizing,
 * scrolling, editability, persistence, or file I/O; callers keep those UI concerns local.
 */
public final class CodeSyntaxHighlighter {
    public static final int MAX_CHARS = 250_000;

    private static final int COLOR_COMMENT = 0xFF8F949E;
    private static final int COLOR_DIRECTIVE = 0xFFD7BAFF;
    private static final int COLOR_OPCODE = 0xFF82AAFF;
    private static final int COLOR_REGISTER = 0xFFFFCB6B;
    private static final int COLOR_STRING = 0xFFC3E88D;
    private static final int COLOR_TYPE = 0xFF89DDFF;
    private static final int COLOR_LABEL = 0xFFFF80AB;
    private static final int COLOR_NUMBER = 0xFFF78C6C;

    private static final Pattern SMALI_REGISTER_PATTERN = Pattern.compile("\\b[vp][0-9]+\\b");
    private static final Pattern SMALI_LABEL_PATTERN = Pattern.compile(":" + "[A-Za-z0-9_$\\-]+");
    private static final Pattern SMALI_TYPE_PATTERN = Pattern.compile("(?:\\[+)?L[^\\s;]+;");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])(?:0x[0-9A-Fa-f]+|-?[0-9]+(?:\\.[0-9]+)?)(?![A-Za-z0-9_])");

    private static final Pattern SHELL_KEYWORD_PATTERN = Pattern.compile("\\b(?:if|then|else|elif|fi|for|while|until|do|done|case|esac|function|in|select|time|return|exit|export|local|readonly|unset|shift|break|continue)\\b");
    private static final Pattern SHELL_VARIABLE_PATTERN = Pattern.compile("\\$\\{?[A-Za-z_][A-Za-z0-9_]*\\}?|\\$[0-9#?@*!-]");
    private static final Pattern SHELL_OPTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])--?[A-Za-z0-9][A-Za-z0-9_-]*");

    private static final Pattern JSON_KEY_PATTERN = Pattern.compile("\\\"(?:\\\\.|[^\\\\\\\"])*\\\"\\s*:");
    private static final Pattern JSON_LITERAL_PATTERN = Pattern.compile("\\b(?:true|false|null)\\b");
    private static final Pattern PROPERTIES_LITERAL_PATTERN = Pattern.compile("\\b(?:true|false|yes|no|on|off|null)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("</?[A-Za-z][A-Za-z0-9:_-]*(?=\\s|>|/)");
    private static final Pattern HTML_ATTR_PATTERN = Pattern.compile("\\b[A-Za-z_:][A-Za-z0-9_:.\\-]*(?=\\s*=)");
    private static final Pattern CSS_PROPERTY_PATTERN = Pattern.compile("(?<![-A-Za-z0-9_])[-A-Za-z][A-Za-z0-9_-]*(?=\\s*:)");
    private static final Pattern JS_KEYWORD_PATTERN = Pattern.compile("\\b(?:const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|new|class|extends|import|export|from|async|await|true|false|null|undefined)\\b");
    private static final Pattern JAVA_KEYWORD_PATTERN = Pattern.compile("\\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null)\\b");
    private static final Pattern JAVA_ANNOTATION_PATTERN = Pattern.compile("@[A-Za-z_][A-Za-z0-9_.$]*");
    private static final Pattern JAVA_TYPE_PATTERN = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*(?:\\.[A-Z][A-Za-z0-9_]*)*\\b");

    private enum Syntax { SMALI, SHELL, JSON, WEB, PROPERTIES, JAVA }

    private CodeSyntaxHighlighter() {}

    public static void applySmali(EditText editor) {
        apply(editor, Syntax.SMALI);
    }

    public static void applyShell(EditText editor) {
        apply(editor, Syntax.SHELL);
    }

    public static void applyJson(EditText editor) {
        apply(editor, Syntax.JSON);
    }

    public static void applyWeb(EditText editor) {
        apply(editor, Syntax.WEB);
    }

    public static void applyProperties(EditText editor) {
        apply(editor, Syntax.PROPERTIES);
    }

    public static void applyJava(EditText editor) {
        apply(editor, Syntax.JAVA);
    }

    public static boolean canHighlightLength(int charCount) {
        return charCount > 0 && charCount <= MAX_CHARS;
    }

    public static CharSequence formatSmaliLine(String line) {
        SpannableStringBuilder editable = new SpannableStringBuilder(line == null ? "" : line);
        if (editable.length() > 0) highlightSmaliLine(editable, editable.toString(), 0, editable.length());
        return editable;
    }

    public static CharSequence formatShellLine(String line) {
        SpannableStringBuilder editable = new SpannableStringBuilder(line == null ? "" : line);
        if (editable.length() > 0) highlightShellLine(editable, editable.toString(), 0, editable.length());
        return editable;
    }

    public static CharSequence formatJsonLine(String line) {
        SpannableStringBuilder editable = new SpannableStringBuilder(line == null ? "" : line);
        if (editable.length() > 0) highlightJsonLine(editable, editable.toString(), 0, editable.length());
        return editable;
    }

    public static CharSequence formatWebLine(String line) {
        SpannableStringBuilder editable = new SpannableStringBuilder(line == null ? "" : line);
        if (editable.length() > 0) highlightWebLine(editable, editable.toString(), 0, editable.length());
        return editable;
    }

    public static CharSequence formatPropertiesLine(String line) {
        SpannableStringBuilder editable = new SpannableStringBuilder(line == null ? "" : line);
        if (editable.length() > 0) highlightPropertiesLine(editable, editable.toString(), 0, editable.length());
        return editable;
    }

    public static CharSequence formatJavaLine(String line) {
        SpannableStringBuilder editable = new SpannableStringBuilder(line == null ? "" : line);
        if (editable.length() > 0) highlightJavaLine(editable, editable.toString(), 0, editable.length());
        return editable;
    }

    private static void apply(EditText editor, Syntax syntax) {
        if (editor == null) return;
        Editable editable = editor.getText();
        if (editable == null) return;

        int len = editable.length();
        clearColorSpans(editable);
        if (len <= 0 || len > MAX_CHARS) return;

        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        String text = editable.toString();
        int lineStart = 0;
        while (lineStart < len) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = len;
            if (syntax == Syntax.SMALI) highlightSmaliLine(editable, text, lineStart, lineEnd);
            else if (syntax == Syntax.SHELL) highlightShellLine(editable, text, lineStart, lineEnd);
            else if (syntax == Syntax.JSON) highlightJsonLine(editable, text, lineStart, lineEnd);
            else if (syntax == Syntax.PROPERTIES) highlightPropertiesLine(editable, text, lineStart, lineEnd);
            else if (syntax == Syntax.JAVA) highlightJavaLine(editable, text, lineStart, lineEnd);
            else highlightWebLine(editable, text, lineStart, lineEnd);
            lineStart = lineEnd + 1;
        }

        try {
            int safeStart = Math.max(0, Math.min(selStart, editable.length()));
            int safeEnd = Math.max(0, Math.min(selEnd, editable.length()));
            editor.setSelection(safeStart, safeEnd);
        } catch (Throwable ignored) {}
    }

    private static void clearColorSpans(Editable editable) {
        try {
            ForegroundColorSpan[] spans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
            if (spans == null) return;
            for (ForegroundColorSpan span : spans) {
                try { editable.removeSpan(span); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static void highlightSmaliLine(Editable editable, String text, int lineStart, int lineEnd) {
        if (editable == null || text == null || lineStart < 0 || lineEnd <= lineStart) return;
        int codeEnd = findSmaliCommentStart(text, lineStart, lineEnd);
        if (codeEnd < lineEnd) setColor(editable, codeEnd, lineEnd, COLOR_COMMENT);
        if (codeEnd <= lineStart) return;

        int first = firstNonWhitespace(text, lineStart, codeEnd);
        if (first >= codeEnd) return;

        int tokenEnd = nextWhitespace(text, first, codeEnd);
        char firstChar = text.charAt(first);
        if (firstChar == '.') setColor(editable, first, tokenEnd, COLOR_DIRECTIVE);
        else if (firstChar == ':') setColor(editable, first, tokenEnd, COLOR_LABEL);
        else if (firstChar != '#') setColor(editable, first, tokenEnd, COLOR_OPCODE);

        highlightQuotedStrings(editable, text, lineStart, codeEnd, true);
        highlightPattern(editable, text, SMALI_REGISTER_PATTERN, lineStart, codeEnd, COLOR_REGISTER);
        highlightPattern(editable, text, SMALI_TYPE_PATTERN, lineStart, codeEnd, COLOR_TYPE);
        highlightPattern(editable, text, SMALI_LABEL_PATTERN, lineStart, codeEnd, COLOR_LABEL);
        highlightPattern(editable, text, NUMBER_PATTERN, lineStart, codeEnd, COLOR_NUMBER);
    }

    private static void highlightShellLine(Editable editable, String text, int lineStart, int lineEnd) {
        if (editable == null || text == null || lineStart < 0 || lineEnd <= lineStart) return;
        int codeEnd = findShellCommentStart(text, lineStart, lineEnd);
        if (codeEnd < lineEnd) setColor(editable, codeEnd, lineEnd, COLOR_COMMENT);
        if (codeEnd <= lineStart) return;

        int first = firstNonWhitespace(text, lineStart, codeEnd);
        if (first >= codeEnd) return;

        int tokenEnd = nextWhitespace(text, first, codeEnd);
        String firstToken = text.substring(first, tokenEnd);
        if (firstToken.startsWith("#!")) setColor(editable, first, tokenEnd, COLOR_COMMENT);
        else if (isShellControlToken(firstToken)) setColor(editable, first, tokenEnd, COLOR_DIRECTIVE);
        else setColor(editable, first, tokenEnd, COLOR_OPCODE);

        highlightQuotedStrings(editable, text, lineStart, codeEnd, false);
        highlightPattern(editable, text, SHELL_KEYWORD_PATTERN, lineStart, codeEnd, COLOR_DIRECTIVE);
        highlightPattern(editable, text, SHELL_VARIABLE_PATTERN, lineStart, codeEnd, COLOR_REGISTER);
        highlightPattern(editable, text, SHELL_OPTION_PATTERN, lineStart, codeEnd, COLOR_TYPE);
        highlightPattern(editable, text, NUMBER_PATTERN, lineStart, codeEnd, COLOR_NUMBER);
    }

    private static void highlightJsonLine(Editable editable, String text, int lineStart, int lineEnd) {
        if (editable == null || text == null || lineStart < 0 || lineEnd <= lineStart) return;
        highlightQuotedStrings(editable, text, lineStart, lineEnd, true);
        highlightPattern(editable, text, JSON_KEY_PATTERN, lineStart, lineEnd, COLOR_DIRECTIVE);
        highlightPattern(editable, text, JSON_LITERAL_PATTERN, lineStart, lineEnd, COLOR_TYPE);
        highlightPattern(editable, text, NUMBER_PATTERN, lineStart, lineEnd, COLOR_NUMBER);
    }
    private static void highlightPropertiesLine(Editable editable, String text, int lineStart, int lineEnd) {
        if (editable == null || text == null || lineStart < 0 || lineEnd <= lineStart) return;
        int first = firstNonWhitespace(text, lineStart, lineEnd);
        if (first >= lineEnd) return;

        char firstChar = text.charAt(first);
        if (firstChar == '#' || firstChar == ';') {
            setColor(editable, first, lineEnd, COLOR_COMMENT);
            return;
        }
        if (firstChar == '[') {
            int close = text.indexOf(']', first + 1);
            if (close >= first && close < lineEnd) setColor(editable, first, close + 1, COLOR_DIRECTIVE);
            if (close + 1 < lineEnd) {
                int commentStart = findPropertiesCommentStart(text, close + 1, lineEnd);
                if (commentStart < lineEnd) setColor(editable, commentStart, lineEnd, COLOR_COMMENT);
            }
            return;
        }

        int commentStart = findPropertiesCommentStart(text, first, lineEnd);
        if (commentStart < lineEnd) setColor(editable, commentStart, lineEnd, COLOR_COMMENT);
        int codeEnd = Math.min(commentStart, lineEnd);
        int separator = findPropertiesSeparator(text, first, codeEnd);
        if (separator >= first) {
            int keyEnd = separator;
            while (keyEnd > first && Character.isWhitespace(text.charAt(keyEnd - 1))) keyEnd--;
            if (keyEnd > first) setColor(editable, first, keyEnd, COLOR_DIRECTIVE);
            setColor(editable, separator, separator + 1, COLOR_TYPE);
            highlightQuotedStrings(editable, text, separator + 1, codeEnd, false);
            highlightPattern(editable, text, PROPERTIES_LITERAL_PATTERN, separator + 1, codeEnd, COLOR_TYPE);
            highlightPattern(editable, text, NUMBER_PATTERN, separator + 1, codeEnd, COLOR_NUMBER);
        } else {
            highlightPattern(editable, text, NUMBER_PATTERN, first, codeEnd, COLOR_NUMBER);
        }
    }


    private static void highlightWebLine(Editable editable, String text, int lineStart, int lineEnd) {
        if (editable == null || text == null || lineStart < 0 || lineEnd <= lineStart) return;
        int codeEnd = findWebCommentStart(text, lineStart, lineEnd);
        if (codeEnd < lineEnd) setColor(editable, codeEnd, lineEnd, COLOR_COMMENT);
        if (codeEnd <= lineStart) return;

        highlightQuotedStrings(editable, text, lineStart, codeEnd, false);
        highlightPattern(editable, text, HTML_TAG_PATTERN, lineStart, codeEnd, COLOR_DIRECTIVE);
        highlightPattern(editable, text, HTML_ATTR_PATTERN, lineStart, codeEnd, COLOR_TYPE);
        highlightPattern(editable, text, CSS_PROPERTY_PATTERN, lineStart, codeEnd, COLOR_OPCODE);
        highlightPattern(editable, text, JS_KEYWORD_PATTERN, lineStart, codeEnd, COLOR_DIRECTIVE);
        highlightPattern(editable, text, NUMBER_PATTERN, lineStart, codeEnd, COLOR_NUMBER);
    }

    private static void highlightJavaLine(Editable editable, String text, int lineStart, int lineEnd) {
        if (editable == null || text == null || lineStart < 0 || lineEnd <= lineStart) return;
        int codeEnd = findJavaCommentStart(text, lineStart, lineEnd);
        if (codeEnd < lineEnd) setColor(editable, codeEnd, lineEnd, COLOR_COMMENT);
        if (codeEnd <= lineStart) return;

        highlightQuotedStrings(editable, text, lineStart, codeEnd, false);
        highlightPattern(editable, text, JAVA_KEYWORD_PATTERN, lineStart, codeEnd, COLOR_DIRECTIVE);
        highlightPattern(editable, text, JAVA_ANNOTATION_PATTERN, lineStart, codeEnd, COLOR_LABEL);
        highlightPattern(editable, text, JAVA_TYPE_PATTERN, lineStart, codeEnd, COLOR_TYPE);
        highlightPattern(editable, text, NUMBER_PATTERN, lineStart, codeEnd, COLOR_NUMBER);
    }

    private static int firstNonWhitespace(String text, int start, int end) {
        int first = start;
        while (first < end && Character.isWhitespace(text.charAt(first))) first++;
        return first;
    }

    private static int nextWhitespace(String text, int start, int end) {
        int tokenEnd = start;
        while (tokenEnd < end && !Character.isWhitespace(text.charAt(tokenEnd))) tokenEnd++;
        return tokenEnd;
    }

    private static int findSmaliCommentStart(String text, int start, int end) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (c == '#' && !inString) return i;
        }
        return end;
    }

    private static int findShellCommentStart(String text, int start, int end) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && !inSingle) { escaped = true; continue; }
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }
            if (c == '#' && !inSingle && !inDouble) return i;
        }
        return end;
    }

    private static int findWebCommentStart(String text, int start, int end) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }
            if (!inSingle && !inDouble) {
                if (c == '<' && i + 3 < end && text.startsWith("<!--", i)) return i;
                if (c == '/' && i + 1 < end && text.charAt(i + 1) == '/') return i;
            }
        }
        return end;
    }

    private static int findJavaCommentStart(String text, int start, int end) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }
            if (!inSingle && !inDouble && c == '/' && i + 1 < end) {
                char next = text.charAt(i + 1);
                if (next == '/' || next == '*') return i;
            }
        }
        return end;
    }

    private static int findPropertiesSeparator(String text, int start, int end) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }
            if (!inSingle && !inDouble && (c == '=' || c == ':')) return i;
        }
        return -1;
    }

    private static int findPropertiesCommentStart(String text, int start, int end) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }
            if (!inSingle && !inDouble && (c == '#' || c == ';')) return i;
        }
        return end;
    }

    private static void highlightQuotedStrings(Editable editable, String text, int start, int end, boolean doubleOnly) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;
        int stringStart = -1;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && (inDouble || !doubleOnly)) { escaped = true; continue; }
            if (!doubleOnly && c == '\'' && !inDouble) {
                if (!inSingle) { inSingle = true; stringStart = i; }
                else { setColor(editable, stringStart, i + 1, COLOR_STRING); inSingle = false; stringStart = -1; }
                continue;
            }
            if (c == '"' && !inSingle) {
                if (!inDouble) { inDouble = true; stringStart = i; }
                else { setColor(editable, stringStart, i + 1, COLOR_STRING); inDouble = false; stringStart = -1; }
            }
        }
        if ((inSingle || inDouble) && stringStart >= start) setColor(editable, stringStart, end, COLOR_STRING);
    }

    private static boolean isShellControlToken(String token) {
        if (TextUtils.isEmpty(token)) return false;
        return "if".equals(token) || "then".equals(token) || "else".equals(token)
                || "elif".equals(token) || "fi".equals(token) || "for".equals(token)
                || "while".equals(token) || "until".equals(token) || "do".equals(token)
                || "done".equals(token) || "case".equals(token) || "esac".equals(token)
                || "function".equals(token);
    }

    private static void highlightPattern(Editable editable, String text, Pattern pattern, int start, int end, int color) {
        try {
            if (pattern == null || start >= end) return;
            Matcher matcher = pattern.matcher(text);
            matcher.region(start, end);
            while (matcher.find()) setColor(editable, matcher.start(), matcher.end(), color);
        } catch (Throwable ignored) {}
    }

    private static void setColor(Editable editable, int start, int end, int color) {
        try {
            if (editable == null || start < 0 || end <= start || end > editable.length()) return;
            editable.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable ignored) {}
    }
}
