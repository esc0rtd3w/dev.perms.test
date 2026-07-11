package dev.perms.test.plugins.runtime;

import android.text.TextUtils;

/** Optional return object for trusted-code plugin methods that want controlled viewer metadata. */
public final class TrustedPluginResult {
    public final String title;
    public final String subtitle;
    public final String text;
    public final String syntax;
    public final String presentation;
    public final String windowStyle;
    public final String windowFit;

    private TrustedPluginResult(String title,
                                String subtitle,
                                String text,
                                String syntax,
                                String presentation,
                                String windowStyle,
                                String windowFit) {
        this.title = clean(title);
        this.subtitle = clean(subtitle);
        this.text = text == null ? "" : text;
        this.syntax = clean(syntax);
        this.presentation = clean(presentation);
        this.windowStyle = clean(windowStyle);
        this.windowFit = clean(windowFit);
    }

    public static TrustedPluginResult text(String text) {
        return new TrustedPluginResult("", "", text, "text", "", "", "");
    }

    public static TrustedPluginResult json(String jsonText) {
        return new TrustedPluginResult("", "", jsonText, "json", "", "", "");
    }

    public static TrustedPluginResult of(String title,
                                         String subtitle,
                                         String text,
                                         String syntax,
                                         String presentation,
                                         String windowStyle,
                                         String windowFit) {
        return new TrustedPluginResult(title, subtitle, text, syntax, presentation, windowStyle, windowFit);
    }

    public TrustedPluginResult withTitle(String value) {
        return new TrustedPluginResult(value, subtitle, text, syntax, presentation, windowStyle, windowFit);
    }

    public TrustedPluginResult withSubtitle(String value) {
        return new TrustedPluginResult(title, value, text, syntax, presentation, windowStyle, windowFit);
    }

    public TrustedPluginResult withSyntax(String value) {
        return new TrustedPluginResult(title, subtitle, text, value, presentation, windowStyle, windowFit);
    }

    public TrustedPluginResult withPresentation(String value) {
        return new TrustedPluginResult(title, subtitle, text, syntax, value, windowStyle, windowFit);
    }

    public TrustedPluginResult withWindow(String style, String fit) {
        return new TrustedPluginResult(title, subtitle, text, syntax, presentation, style, fit);
    }

    private static String clean(String value) {
        return TextUtils.isEmpty(value) ? "" : value.trim();
    }
}
