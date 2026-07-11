package dev.perms.test.editor;

import android.widget.EditText;

import dev.perms.test.debugging.editor.CodeSyntaxHighlighter;

/** Generic syntax entry points for shared source editors. */
public final class SourceSyntaxHighlighter {
    private SourceSyntaxHighlighter() {}

    public static boolean canHighlightLength(int charCount) {
        return CodeSyntaxHighlighter.canHighlightLength(charCount);
    }

    public static void applySmali(EditText editor) {
        CodeSyntaxHighlighter.applySmali(editor);
    }

    public static void applyShell(EditText editor) {
        CodeSyntaxHighlighter.applyShell(editor);
    }

    public static void applyJson(EditText editor) {
        CodeSyntaxHighlighter.applyJson(editor);
    }

    public static void applyWeb(EditText editor) {
        CodeSyntaxHighlighter.applyWeb(editor);
    }

    public static void applyProperties(EditText editor) {
        CodeSyntaxHighlighter.applyProperties(editor);
    }

    public static void applyJava(EditText editor) {
        CodeSyntaxHighlighter.applyJava(editor);
    }

    public static CharSequence formatSmaliLine(String line) {
        return CodeSyntaxHighlighter.formatSmaliLine(line);
    }

    public static CharSequence formatShellLine(String line) {
        return CodeSyntaxHighlighter.formatShellLine(line);
    }

    public static CharSequence formatJsonLine(String line) {
        return CodeSyntaxHighlighter.formatJsonLine(line);
    }

    public static CharSequence formatWebLine(String line) {
        return CodeSyntaxHighlighter.formatWebLine(line);
    }

    public static CharSequence formatPropertiesLine(String line) {
        return CodeSyntaxHighlighter.formatPropertiesLine(line);
    }

    public static CharSequence formatJavaLine(String line) {
        return CodeSyntaxHighlighter.formatJavaLine(line);
    }
}
