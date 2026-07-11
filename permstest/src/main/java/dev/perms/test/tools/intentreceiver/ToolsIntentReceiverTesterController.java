package dev.perms.test.tools.intentreceiver;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.databinding.TabToolsBinding;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.NoFilterArrayAdapter;

/** Foundation UI for live receiver activation and manifest intent-filter patch plans. */
public final class ToolsIntentReceiverTesterController {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        void appendOutput(String message);
    }

    private static final String GROUP_FILES_MEDIA = "Files and Media";
    private static final String GROUP_LINKS_URIS = "Links and URI Schemes";
    private static final String GROUP_MESSAGING_SHARING = "Messaging and Sharing";
    private static final String GROUP_OTHER_ACTIONS = "Other Actions";

    private static final String[] TEMPLATE_GROUP_ORDER = new String[] {
            GROUP_FILES_MEDIA,
            GROUP_LINKS_URIS,
            GROUP_MESSAGING_SHARING,
            GROUP_OTHER_ACTIONS
    };

    private static final ReceiverTemplate[] TEMPLATES = new ReceiverTemplate[] {
            new ReceiverTemplate(GROUP_FILES_MEDIA, "APK/split archives", true, ".tools.intentreceiver.IntentCapturePackageArchiveAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                    "        <data android:scheme=\"content\" android:mimeType=\"application/vnd.android.package-archive\" />\n" +
                    "        <data android:scheme=\"content\" android:mimeType=\"application/zip\" />\n" +
                    "        <data android:scheme=\"file\" android:mimeType=\"application/vnd.android.package-archive\" />\n"),
            new ReceiverTemplate(GROUP_FILES_MEDIA, "Audio", false, ".tools.intentreceiver.IntentCaptureAudioAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <data android:scheme=\"content\" android:mimeType=\"audio/*\" />\n"),
            new ReceiverTemplate(GROUP_FILES_MEDIA, "Images", false, ".tools.intentreceiver.IntentCaptureImagesAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <data android:scheme=\"content\" android:mimeType=\"image/*\" />\n"),
            new ReceiverTemplate(GROUP_FILES_MEDIA, "Open any file", false, ".tools.intentreceiver.IntentCaptureOpenAnyFileAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                    "        <data android:scheme=\"content\" android:mimeType=\"*/*\" />\n" +
                    "        <data android:scheme=\"file\" android:mimeType=\"*/*\" />\n"),
            new ReceiverTemplate(GROUP_FILES_MEDIA, "Open text/source", true, ".tools.intentreceiver.IntentCaptureOpenTextAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <action android:name=\"android.intent.action.EDIT\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                    "        <data android:scheme=\"content\" android:mimeType=\"text/*\" />\n" +
                    "        <data android:scheme=\"file\" android:mimeType=\"text/*\" />\n"),
            new ReceiverTemplate(GROUP_FILES_MEDIA, "PDF/docs", false, ".tools.intentreceiver.IntentCaptureDocumentsAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                    "        <data android:scheme=\"content\" android:mimeType=\"application/pdf\" />\n" +
                    "        <data android:scheme=\"content\" android:mimeType=\"application/json\" />\n" +
                    "        <data android:scheme=\"content\" android:mimeType=\"application/xml\" />\n"),
            new ReceiverTemplate(GROUP_FILES_MEDIA, "Video", false, ".tools.intentreceiver.IntentCaptureVideoAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <data android:scheme=\"content\" android:mimeType=\"video/*\" />\n"),

            new ReceiverTemplate(GROUP_LINKS_URIS, "Custom scheme", false, ".tools.intentreceiver.IntentCapturePermsTestSchemeAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                    "        <data android:scheme=\"perms-test\" />\n"),
            new ReceiverTemplate(GROUP_LINKS_URIS, "Geo/maps", false, ".tools.intentreceiver.IntentCaptureGeoMapsAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <data android:scheme=\"geo\" />\n" +
                    "        <data android:scheme=\"google.navigation\" />\n"),
            new ReceiverTemplate(GROUP_LINKS_URIS, "GitHub links", false, ".tools.intentreceiver.IntentCaptureGitHubLinksAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                    "        <data android:scheme=\"https\" android:host=\"github.com\" />\n"),
            new ReceiverTemplate(GROUP_LINKS_URIS, "Market links", false, ".tools.intentreceiver.IntentCaptureMarketLinksAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                    "        <data android:scheme=\"market\" />\n"),
            new ReceiverTemplate(GROUP_LINKS_URIS, "Web links", false, ".tools.intentreceiver.IntentCaptureWebLinksAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                    "        <data android:scheme=\"http\" />\n" +
                    "        <data android:scheme=\"https\" />\n"),
            new ReceiverTemplate(GROUP_LINKS_URIS, "YouTube links", true, ".tools.intentreceiver.IntentCaptureYouTubeLinksAlias",
                    "        <action android:name=\"android.intent.action.VIEW\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                    "        <data android:scheme=\"https\" android:host=\"www.youtube.com\" />\n" +
                    "        <data android:scheme=\"https\" android:host=\"m.youtube.com\" />\n" +
                    "        <data android:scheme=\"https\" android:host=\"music.youtube.com\" />\n" +
                    "        <data android:scheme=\"https\" android:host=\"youtu.be\" />\n"),

            new ReceiverTemplate(GROUP_MESSAGING_SHARING, "Mailto", false, ".tools.intentreceiver.IntentCaptureMailtoAlias",
                    "        <action android:name=\"android.intent.action.SENDTO\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <data android:scheme=\"mailto\" />\n"),
            new ReceiverTemplate(GROUP_MESSAGING_SHARING, "Share files", false, ".tools.intentreceiver.IntentCaptureShareFilesAlias",
                    "        <action android:name=\"android.intent.action.SEND\" />\n" +
                    "        <action android:name=\"android.intent.action.SEND_MULTIPLE\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <data android:mimeType=\"*/*\" />\n"),
            new ReceiverTemplate(GROUP_MESSAGING_SHARING, "Share text", true, ".tools.intentreceiver.IntentCaptureShareTextAlias",
                    "        <action android:name=\"android.intent.action.SEND\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <data android:mimeType=\"text/plain\" />\n"),
            new ReceiverTemplate(GROUP_MESSAGING_SHARING, "Tel/SMS", false, ".tools.intentreceiver.IntentCaptureTelSmsAlias",
                    "        <action android:name=\"android.intent.action.DIAL\" />\n" +
                    "        <action android:name=\"android.intent.action.SENDTO\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <data android:scheme=\"tel\" />\n" +
                    "        <data android:scheme=\"sms\" />\n"),

            new ReceiverTemplate(GROUP_OTHER_ACTIONS, "Calendar insert", false, ".tools.intentreceiver.IntentCaptureCalendarInsertAlias",
                    "        <action android:name=\"android.intent.action.INSERT\" />\n" +
                    "        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                    "        <data android:mimeType=\"vnd.android.cursor.item/event\" />\n")
    };

    private static final CustomPreset[] CUSTOM_PRESETS = new CustomPreset[] {
            new CustomPreset("Custom...", "", "", "", "", "", ""),
            new CustomPreset("DIAL tel:", Intent.ACTION_DIAL, "", "tel", "", "", ""),
            new CustomPreset("INSERT calendar event", Intent.ACTION_INSERT, "vnd.android.cursor.item/event", "", "", "", ""),
            new CustomPreset("SEND image/*", Intent.ACTION_SEND, "image/*", "", "", "", ""),
            new CustomPreset("SEND text/plain", Intent.ACTION_SEND, "text/plain", "", "", "", ""),
            new CustomPreset("SEND_MULTIPLE */*", Intent.ACTION_SEND_MULTIPLE, "*/*", "", "", "", ""),
            new CustomPreset("SENDTO mailto:", Intent.ACTION_SENDTO, "", "mailto", "", "", ""),
            new CustomPreset("SENDTO sms:", Intent.ACTION_SENDTO, "", "sms", "", "", ""),
            new CustomPreset("VIEW application/pdf", Intent.ACTION_VIEW, "application/pdf", "content", "", "", ""),
            new CustomPreset("VIEW content image/*", Intent.ACTION_VIEW, "image/*", "content", "", "", ""),
            new CustomPreset("VIEW content text/*", Intent.ACTION_VIEW, "text/*", "content", "", "", ""),
            new CustomPreset("VIEW geo:", Intent.ACTION_VIEW, "", "geo", "", "", ""),
            new CustomPreset("VIEW GitHub", Intent.ACTION_VIEW, "", "https", "github.com", "", ""),
            new CustomPreset("VIEW https://example.com", Intent.ACTION_VIEW, "", "https", "example.com", "", ""),
            new CustomPreset("VIEW market:", Intent.ACTION_VIEW, "", "market", "", "", ""),
            new CustomPreset("VIEW package:", Intent.ACTION_VIEW, "", "package", "", "", ""),
            new CustomPreset("VIEW PermsTest scheme", Intent.ACTION_VIEW, "", "perms-test", "", "", ""),
            new CustomPreset("VIEW YouTube watch", Intent.ACTION_VIEW, "", "https", "www.youtube.com", "/watch", ""),
            new CustomPreset("VIEW youtu.be", Intent.ACTION_VIEW, "", "https", "youtu.be", "", "")
    };

    private final Host host;
    private final ArrayList<MaterialCheckBox> templateChecks = new ArrayList<>();
    private boolean bound;

    public ToolsIntentReceiverTesterController(Host host) {
        this.host = host;
    }

    public void bind() {
        if (bound) return;
        TabToolsBinding b = toolsBinding();
        Activity activity = activity();
        if (b == null || activity == null) return;
        bound = true;

        buildTemplateRows(activity, b);
        bindCustomPresetDropdown(activity, b);
        refreshTemplateChecksFromAliases(activity);
        b.btnToolsReceiverOpenCapture.setOnClickListener(v -> applyActiveFilters());
        b.btnToolsReceiverDisableAll.setOnClickListener(v -> disableAllFilters());
        b.btnToolsReceiverSelectAll.setOnClickListener(v -> setAllTemplateChecks(true));
        b.btnToolsReceiverSelectNone.setOnClickListener(v -> setAllTemplateChecks(false));
        b.btnToolsReceiverPreview.setOnClickListener(v -> openCapturePreview());
        b.btnToolsReceiverCopy.setOnClickListener(v -> copySelectedFilters());
        b.btnToolsReceiverClear.setOnClickListener(v -> clearSelection(false));
        b.chkToolsReceiverPreferCaptureBuiltIns.setChecked(IntentReceiverRuntime.isPreferCaptureForBuiltInHandlers(activity));
        b.chkToolsReceiverPreferCaptureBuiltIns.setOnCheckedChangeListener((buttonView, checked) -> {
            IntentReceiverRuntime.setPreferCaptureForBuiltInHandlers(activity, checked);
            setStatus(checked
                    ? "Built-in PermsTest file handlers will hand off to Intent Capture while matching receiver aliases are active."
                    : "Built-in PermsTest file handlers keep their normal behavior even when receiver aliases are active.");
        });
        b.chkToolsReceiverSaveEventsToFile.setChecked(IntentReceiverEventStore.isSaveToFile(activity));
        b.chkToolsReceiverSaveEventsToFile.setOnCheckedChangeListener((buttonView, checked) -> {
            IntentReceiverEventStore.setSaveToFile(activity, checked);
            setStatus(checked
                    ? "Captured events will also be saved to " + IntentReceiverEventStore.filePath()
                    : "Captured events will stay in the in-app event log only.");
        });
        b.btnToolsReceiverClearEvents.setOnClickListener(v -> {
            IntentReceiverEventStore.clear(activity);
            updateEventLog();
            setStatus("Captured Intent Receiver event log cleared.");
        });
        updateEventLog();
        setStatus("Check receiver templates, tap Apply Active, then test from another app/browser/file manager. Captured intents show a dialog and are logged here.");
    }

    private void bindCustomPresetDropdown(Activity activity, TabToolsBinding b) {
        ArrayList<String> labels = new ArrayList<>();
        for (CustomPreset preset : CUSTOM_PRESETS) labels.add(preset.label);
        b.ddToolsReceiverCustomPreset.setAdapter(new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, labels));
        DropdownUi.bindClickOnlyExposedDropdown(activity, b.tilToolsReceiverCustomPreset, b.ddToolsReceiverCustomPreset,
                () -> {
                    try { b.ddToolsReceiverCustomPreset.setDropDownWidth(b.tilToolsReceiverCustomPreset.getWidth()); } catch (Throwable ignored) {}
                    DropdownUi.showDropdown(b.ddToolsReceiverCustomPreset,
                            list -> DropdownUi.applyListTweaks(list, false, 0, 6));
                });
        b.ddToolsReceiverCustomPreset.setText(CUSTOM_PRESETS[0].label, false);
        b.ddToolsReceiverCustomPreset.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= CUSTOM_PRESETS.length) return;
            applyCustomPreset(CUSTOM_PRESETS[position]);
        });
    }

    private void applyCustomPreset(CustomPreset preset) {
        TabToolsBinding b = toolsBinding();
        if (b == null || preset == null) return;
        if (TextUtils.isEmpty(preset.action) && TextUtils.isEmpty(preset.mime)
                && TextUtils.isEmpty(preset.scheme) && TextUtils.isEmpty(preset.host)
                && TextUtils.isEmpty(preset.pathPrefix) && TextUtils.isEmpty(preset.category)) {
            setStatus("Custom preset selected. Enter action/data/type values manually.");
            return;
        }
        b.edtToolsReceiverCustomAction.setText(preset.action);
        b.edtToolsReceiverCustomMime.setText(preset.mime);
        b.edtToolsReceiverCustomScheme.setText(preset.scheme);
        b.edtToolsReceiverCustomHost.setText(preset.host);
        b.edtToolsReceiverCustomPathPrefix.setText(preset.pathPrefix);
        b.edtToolsReceiverCustomCategory.setText(preset.category);
        b.chkToolsReceiverCustomDefault.setChecked(true);
        b.chkToolsReceiverCustomBrowsable.setChecked(!TextUtils.isEmpty(preset.scheme));
        setStatus("Loaded custom filter preset: " + preset.label + ". Copy these filters into a rebuilt APK when new manifest entries are needed.");
    }

    private void buildTemplateRows(Activity activity, TabToolsBinding b) {
        b.llToolsReceiverTemplates.removeAllViews();
        templateChecks.clear();

        Map<String, List<ReceiverTemplate>> groups = groupTemplates();
        for (String group : TEMPLATE_GROUP_ORDER) {
            List<ReceiverTemplate> items = groups.get(group);
            if (items == null || items.isEmpty()) continue;
            addSectionLabel(activity, b.llToolsReceiverTemplates, group);
            addCheckboxRows(activity, b.llToolsReceiverTemplates, items);
        }
    }

    private Map<String, List<ReceiverTemplate>> groupTemplates() {
        LinkedHashMap<String, List<ReceiverTemplate>> groups = new LinkedHashMap<>();
        for (String group : TEMPLATE_GROUP_ORDER) groups.put(group, new ArrayList<>());
        for (ReceiverTemplate template : TEMPLATES) {
            List<ReceiverTemplate> list = groups.get(template.group);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(template.group, list);
            }
            list.add(template);
        }
        for (List<ReceiverTemplate> list : groups.values()) {
            Collections.sort(list, Comparator.comparing(o -> o.label.toLowerCase(java.util.Locale.US)));
        }
        return groups;
    }

    private void addCheckboxRows(Activity activity, LinearLayout container, List<ReceiverTemplate> templates) {
        LinearLayout row = null;
        for (int i = 0; i < templates.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBaselineAligned(false);
                container.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            ReceiverTemplate template = templates.get(i);
            MaterialCheckBox cb = new MaterialCheckBox(activity);
            cb.setText(template.label);
            cb.setTag(template);
            cb.setSingleLine(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (row != null) row.addView(cb, lp);
            templateChecks.add(cb);
        }
    }

    private void addSectionLabel(Activity activity, LinearLayout container, String label) {
        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setTextSize(12f);
        tv.setAlpha(0.78f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(activity, container.getChildCount() == 0 ? 2 : 8);
        lp.bottomMargin = dp(activity, 2);
        container.addView(tv, lp);
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void refreshTemplateChecksFromAliases(Activity activity) {
        for (MaterialCheckBox cb : templateChecks) {
            Object tag = cb.getTag();
            if (tag instanceof ReceiverTemplate) {
                ReceiverTemplate template = (ReceiverTemplate) tag;
                cb.setChecked(IntentReceiverAliasRegistry.isAliasEnabled(activity, template.aliasSuffix));
            }
        }
    }

    private void setAllTemplateChecks(boolean checked) {
        for (MaterialCheckBox cb : templateChecks) cb.setChecked(checked);
        setStatus(checked ? "All Intent Receiver templates selected. Tap Apply Active to enable them." : "Intent Receiver template selection cleared. Active aliases are unchanged until Apply Active or Disable All is tapped.");
    }

    private void applyActiveFilters() {
        Activity activity = activity();
        if (activity == null) return;
        int enabled = 0;
        int disabled = 0;
        int failed = 0;
        for (MaterialCheckBox cb : templateChecks) {
            Object tag = cb.getTag();
            if (!(tag instanceof ReceiverTemplate)) continue;
            ReceiverTemplate template = (ReceiverTemplate) tag;
            boolean checked = cb.isChecked();
            if (IntentReceiverAliasRegistry.setAliasEnabled(activity, template.aliasSuffix, checked)) {
                if (checked) enabled++; else disabled++;
            } else {
                failed++;
            }
        }
        String msg = "Receiver aliases updated: " + enabled + " active, " + disabled + " inactive" + (failed > 0 ? (", " + failed + " failed") : "") + ".";
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
        setStatus(msg + " Now test from another app. Android default-app and verified-link rules still apply.");
        append("[Intent Receiver] " + msg + "\n");
    }

    private void disableAllFilters() {
        Activity activity = activity();
        if (activity == null) return;
        for (MaterialCheckBox cb : templateChecks) {
            Object tag = cb.getTag();
            if (tag instanceof ReceiverTemplate) {
                ReceiverTemplate template = (ReceiverTemplate) tag;
                IntentReceiverAliasRegistry.setAliasEnabled(activity, template.aliasSuffix, false);
            }
            cb.setChecked(false);
        }
        Toast.makeText(activity, "Intent Receiver aliases disabled", Toast.LENGTH_SHORT).show();
        setStatus("All installed Intent Receiver aliases are disabled.");
        append("[Intent Receiver] Disabled all receiver aliases.\n");
    }

    private void openCapturePreview() {
        Activity activity = activity();
        if (activity == null) return;
        Intent intent = buildPreviewIntent();
        try {
            activity.startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(activity, "Preview failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            append("[Intent Receiver] Preview failed: " + t + "\n");
        }
    }

    private Intent buildPreviewIntent() {
        Activity activity = activity();
        Intent intent = new Intent(activity, IntentCaptureActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("https://example.com/perms-test-intent-capture"), "text/plain");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(Intent.EXTRA_TEXT, "PermsTest intent receiver preview. Use Apply Active, then test from another app for real handler capture.");
        for (MaterialCheckBox cb : templateChecks) {
            if (!cb.isChecked()) continue;
            Object tag = cb.getTag();
            if (tag instanceof ReceiverTemplate) {
                ReceiverTemplate template = (ReceiverTemplate) tag;
                if ("Share text".equals(template.label)) {
                    intent.setAction(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.setData(null);
                    intent.putExtra(Intent.EXTRA_TEXT, "PermsTest share-text preview");
                    return intent;
                }
                if ("YouTube links".equals(template.label)) {
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("https://www.youtube.com/watch?v=perms-test"));
                    intent.setType(null);
                    return intent;
                }
                if ("Mailto".equals(template.label)) {
                    intent.setAction(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("mailto:test@example.com"));
                    intent.setType(null);
                    return intent;
                }
            }
        }
        return intent;
    }

    private void copySelectedFilters() {
        Activity activity = activity();
        if (activity == null) return;
        String snippet = buildSelectedFilterSnippet();
        if (TextUtils.isEmpty(snippet)) {
            Toast.makeText(activity, "No receiver filters selected", Toast.LENGTH_SHORT).show();
            setStatus("No receiver filters selected.");
            return;
        }
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PermsTest intent receiver filters", snippet));
        Toast.makeText(activity, "Intent filter snippet copied", Toast.LENGTH_SHORT).show();
        setStatus("Copied " + countSelectedFilters() + " receiver filter(s). Custom filters require APK repack before live activation.");
        append("[Intent Receiver] Copied manifest filters:\n" + snippet + "\n");
    }

    private String buildSelectedFilterSnippet() {
        StringBuilder filters = new StringBuilder();
        for (MaterialCheckBox cb : templateChecks) {
            if (!isChecked(cb)) continue;
            Object tag = cb.getTag();
            if (tag instanceof ReceiverTemplate) {
                ReceiverTemplate item = (ReceiverTemplate) tag;
                filters.append(filter(item.label, item.body));
            }
        }
        String custom = customFilter();
        if (!TextUtils.isEmpty(custom)) filters.append(custom);
        if (filters.length() == 0) return "";
        return "<activity\n" +
                "    android:name=\".tools.intentreceiver.IntentCaptureActivity\"\n" +
                "    android:exported=\"true\">\n" +
                filters +
                "</activity>";
    }

    private String customFilter() {
        TabToolsBinding b = toolsBinding();
        if (b == null) return "";
        String action = clean(b.edtToolsReceiverCustomAction.getText());
        String mime = clean(b.edtToolsReceiverCustomMime.getText());
        String scheme = clean(b.edtToolsReceiverCustomScheme.getText());
        String hostValue = clean(b.edtToolsReceiverCustomHost.getText());
        String pathPrefix = clean(b.edtToolsReceiverCustomPathPrefix.getText());
        String category = clean(b.edtToolsReceiverCustomCategory.getText());
        boolean hasCustom = !TextUtils.isEmpty(action) || !TextUtils.isEmpty(mime) || !TextUtils.isEmpty(scheme)
                || !TextUtils.isEmpty(hostValue) || !TextUtils.isEmpty(pathPrefix) || !TextUtils.isEmpty(category)
                || !isChecked(b.chkToolsReceiverCustomDefault) || !isChecked(b.chkToolsReceiverCustomBrowsable);
        if (!hasCustom) return "";
        StringBuilder body = new StringBuilder();
        body.append("        <action android:name=\"").append(xml(TextUtils.isEmpty(action) ? Intent.ACTION_VIEW : action)).append("\" />\n");
        if (isChecked(b.chkToolsReceiverCustomDefault)) {
            body.append("        <category android:name=\"android.intent.category.DEFAULT\" />\n");
        }
        if (isChecked(b.chkToolsReceiverCustomBrowsable)) {
            body.append("        <category android:name=\"android.intent.category.BROWSABLE\" />\n");
        }
        if (!TextUtils.isEmpty(category)) {
            for (String c : splitCustom(category)) {
                body.append("        <category android:name=\"").append(xml(c)).append("\" />\n");
            }
        }
        if (!TextUtils.isEmpty(mime) || !TextUtils.isEmpty(scheme) || !TextUtils.isEmpty(hostValue) || !TextUtils.isEmpty(pathPrefix)) {
            body.append("        <data");
            if (!TextUtils.isEmpty(scheme)) body.append(" android:scheme=\"").append(xml(scheme)).append("\"");
            if (!TextUtils.isEmpty(hostValue)) body.append(" android:host=\"").append(xml(hostValue)).append("\"");
            if (!TextUtils.isEmpty(pathPrefix)) body.append(" android:pathPrefix=\"").append(xml(pathPrefix)).append("\"");
            if (!TextUtils.isEmpty(mime)) body.append(" android:mimeType=\"").append(xml(mime)).append("\"");
            body.append(" />\n");
        }
        return filter("Custom filter", body.toString());
    }

    private String filter(String label, String body) {
        return "    <!-- " + xml(label) + " -->\n" +
                "    <intent-filter>\n" + body +
                "    </intent-filter>\n";
    }

    private int countSelectedFilters() {
        int count = 0;
        for (MaterialCheckBox cb : templateChecks) if (isChecked(cb)) count++;
        if (!TextUtils.isEmpty(customFilter())) count++;
        return count;
    }

    private void clearSelection(boolean keepAliases) {
        TabToolsBinding b = toolsBinding();
        for (MaterialCheckBox cb : templateChecks) cb.setChecked(false);
        if (b != null) {
            b.ddToolsReceiverCustomPreset.setText(CUSTOM_PRESETS[0].label, false);
            b.edtToolsReceiverCustomAction.setText("");
            b.edtToolsReceiverCustomMime.setText("");
            b.edtToolsReceiverCustomScheme.setText("");
            b.edtToolsReceiverCustomHost.setText("");
            b.edtToolsReceiverCustomPathPrefix.setText("");
            b.edtToolsReceiverCustomCategory.setText("");
            b.chkToolsReceiverCustomDefault.setChecked(true);
            b.chkToolsReceiverCustomBrowsable.setChecked(true);
        }
        setStatus(keepAliases ? "Receiver fields cleared." : "Receiver selections cleared. Tap Disable All to turn off active installed aliases.");
    }

    private static List<String> splitCustom(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        for (String part : text.split("[,\\s]+")) {
            String v = part == null ? "" : part.trim();
            if (!TextUtils.isEmpty(v)) out.add(v);
        }
        return out;
    }

    private static String clean(CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private boolean isChecked(MaterialCheckBox cb) {
        return cb != null && cb.isChecked();
    }

    private void updateEventLog() {
        Activity activity = activity();
        TabToolsBinding b = toolsBinding();
        if (activity == null || b == null || b.txtToolsReceiverEventLog == null) return;
        String log = IntentReceiverEventStore.read(activity);
        b.txtToolsReceiverEventLog.setText(TextUtils.isEmpty(log) ? "No captured receiver events yet." : log);
    }

    private void setStatus(String text) {
        TabToolsBinding b = toolsBinding();
        if (b != null) b.txtToolsReceiverStatus.setText(text == null ? "" : text);
    }

    private void append(String message) {
        if (host != null) host.appendOutput(message == null ? "" : message);
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private TabToolsBinding toolsBinding() {
        ActivityMainBinding binding = host == null ? null : host.getBinding();
        return binding == null ? null : binding.tabTools;
    }

    private static final class ReceiverTemplate {
        final String group;
        final String label;
        final boolean common;
        final String aliasSuffix;
        final String body;

        ReceiverTemplate(String group, String label, boolean common, String aliasSuffix, String body) {
            this.group = group;
            this.label = label;
            this.common = common;
            this.aliasSuffix = aliasSuffix;
            this.body = body;
        }
    }

    private static final class CustomPreset {
        final String label;
        final String action;
        final String mime;
        final String scheme;
        final String host;
        final String pathPrefix;
        final String category;

        CustomPreset(String label, String action, String mime, String scheme, String host, String pathPrefix, String category) {
            this.label = label;
            this.action = action;
            this.mime = mime;
            this.scheme = scheme;
            this.host = host;
            this.pathPrefix = pathPrefix;
            this.category = category;
        }
    }
}
