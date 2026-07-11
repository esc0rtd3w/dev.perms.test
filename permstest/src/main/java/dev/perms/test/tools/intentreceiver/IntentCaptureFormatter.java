package dev.perms.test.tools.intentreceiver;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.Set;

/** Formats captured implicit/direct intents for debugging and copy/share workflows. */
public final class IntentCaptureFormatter {
    private IntentCaptureFormatter() {}

    public static String format(Intent intent) {
        if (intent == null) return "No intent.";
        StringBuilder sb = new StringBuilder();
        sb.append("Action: ").append(safe(intent.getAction())).append('\n');
        Uri data = intent.getData();
        sb.append("Data: ").append(data == null ? "" : data.toString()).append('\n');
        sb.append("Type: ").append(safe(intent.getType())).append('\n');
        sb.append("Package: ").append(safe(intent.getPackage())).append('\n');
        if (intent.getComponent() != null) sb.append("Component: ").append(intent.getComponent().flattenToShortString()).append('\n');
        sb.append("Flags: 0x").append(Integer.toHexString(intent.getFlags())).append('\n');
        Set<String> categories = intent.getCategories();
        sb.append("Categories: ").append(categories == null || categories.isEmpty() ? "" : TextUtils.join(", ", categories)).append('\n');
        appendExtras(sb, intent.getExtras());
        appendClipData(sb, intent.getClipData());
        return sb.toString().trim();
    }

    private static void appendExtras(StringBuilder sb, Bundle extras) {
        sb.append("Extras:\n");
        if (extras == null || extras.isEmpty()) {
            sb.append("  (none)\n");
            return;
        }
        for (String key : extras.keySet()) {
            Object value;
            try {
                value = extras.get(key);
            } catch (Throwable t) {
                value = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            sb.append("  ").append(key).append(" = ").append(value).append('\n');
        }
    }

    private static void appendClipData(StringBuilder sb, ClipData clip) {
        sb.append("ClipData:\n");
        if (clip == null || clip.getItemCount() <= 0) {
            sb.append("  (none)\n");
            return;
        }
        for (int i = 0; i < clip.getItemCount(); i++) {
            ClipData.Item item = clip.getItemAt(i);
            if (item == null) continue;
            sb.append("  [").append(i).append("] ");
            if (item.getUri() != null) sb.append("uri=").append(item.getUri());
            else if (item.getIntent() != null) sb.append("intent=").append(item.getIntent());
            else sb.append("text=").append(item.getText());
            sb.append('\n');
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
