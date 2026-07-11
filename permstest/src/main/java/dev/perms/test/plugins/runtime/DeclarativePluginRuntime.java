package dev.perms.test.plugins.runtime;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dev.perms.test.plugins.PluginAction;
import dev.perms.test.plugins.PluginActionRegistry;
import dev.perms.test.plugins.PluginManifest;
import dev.perms.test.plugins.PluginRuntimePolicy;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.NoFilterArrayAdapter;
import dev.perms.test.ui.dialog.MovableDialogChrome;

/** Generic JSON-driven plugin UI/action runtime for self-contained .ptp plugins. */
public final class DeclarativePluginRuntime {
    private DeclarativePluginRuntime() {
    }

    public static boolean run(PluginActionRegistry.Host host, PluginManifest plugin, PluginAction action) {
        Activity activity = host == null ? null : host.getActivity();
        if (activity == null || plugin == null || action == null) return false;
        try {
            File uiFile = resolvePluginFile(plugin, firstNonEmpty(action.target, action.handler, plugin.entry, "ui.json"));
            JSONObject ui = new JSONObject(readUtf8(uiFile));
            boolean largeOverride = host != null && host.shouldRunPluginInPanel(plugin.id);
            String windowStyle = largeOverride ? "full" : firstNonEmpty(action.windowStyle, ui.optString("windowStyle", ""), plugin.windowStyle);
            String windowFit = largeOverride ? "current" : firstNonEmpty(action.windowFit, ui.optString("windowFit", ""), plugin.windowFit);
            return showUi(host, plugin, action, activity, ui, plugin.id, plugin.name, plugin.description,
                    windowStyle, windowFit, false);
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[plugins] Declarative plugin failed: " + safeMessage(t) + "\n");
            Toast.makeText(activity, "Plugin UI failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public static boolean preview(PluginActionRegistry.Host host,
                                  String pluginId,
                                  String pluginName,
                                  String pluginDescription,
                                  String windowStyle,
                                  String windowFit,
                                  String uiJson) {
        Activity activity = host == null ? null : host.getActivity();
        if (activity == null || TextUtils.isEmpty(uiJson)) return false;
        try {
            JSONObject ui = new JSONObject(uiJson);
            return showUi(host, null, null, activity, ui,
                    TextUtils.isEmpty(pluginId) ? "preview" : pluginId,
                    TextUtils.isEmpty(pluginName) ? "Plugin UI Preview" : pluginName,
                    pluginDescription,
                    firstNonEmpty(ui.optString("windowStyle", ""), windowStyle, "compact"),
                    firstNonEmpty(ui.optString("windowFit", ""), windowFit, "current"),
                    true);
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[plugins] Declarative UI preview failed: " + safeMessage(t) + "\n");
            Toast.makeText(activity, "Plugin UI preview failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static boolean showUi(PluginActionRegistry.Host host,
                                  PluginManifest plugin,
                                  PluginAction launchAction,
                                  Activity activity,
                                  JSONObject ui,
                                  String pluginId,
                                  String pluginName,
                                  String pluginDescription,
                                  String windowStyle,
                                  String windowFit,
                                  boolean preview) {
        RuntimeState state = new RuntimeState(host, plugin, launchAction, activity, preview);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 12);
        content.setPadding(pad, pad, pad, pad);

        String title = ui.optString("title", pluginName);
        if (preview && !title.toLowerCase(Locale.US).contains("preview")) title = title + " Preview";
        String description = ui.optString("description", pluginDescription);
        if (!TextUtils.isEmpty(description)) {
            TextView desc = label(activity, description, 12, false);
            desc.setAlpha(0.78f);
            desc.setPadding(0, 0, 0, dp(activity, 8));
            content.addView(desc);
        }

        JSONArray controls = ui.optJSONArray("controls");
        if (controls != null) {
            for (int i = 0; i < controls.length(); i++) {
                addControl(activity, content, state, controls.optJSONObject(i));
            }
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        MovableDialogChrome.Chrome chrome = MovableDialogChrome.create(activity, scroll, windowStyle);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setView(chrome.root);
        if (!MovableDialogChrome.isFullStyle(windowStyle)) {
            builder.setTitle(title);
            builder.setPositiveButton("Close", null);
        }
        AlertDialog dialog = builder.create();
        if (chrome.closeButton != null) chrome.closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        MovableDialogChrome.applyWindowStyle(dialog, windowStyle, windowFit);
        MovableDialogChrome.enable(dialog, chrome.dragHandle);
        if (host != null) {
            host.appendOutput(preview
                    ? "[plugins] Previewed declarative plugin UI: " + pluginId + "\n"
                    : "[plugins] Opened declarative plugin UI: " + pluginId + "\n");
        }
        return true;
    }

    private static void addControl(Activity activity, LinearLayout content, RuntimeState state, JSONObject control) {
        if (control == null) return;
        String type = control.optString("type", "label").toLowerCase(Locale.US);
        String id = control.optString("id", "");
        if ("label".equals(type) || "text".equals(type)) {
            TextView view = label(activity, control.optString("text", control.optString("label", "")),
                    control.optInt("textSize", 13), control.optBoolean("bold", false));
            view.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
            content.addView(view, matchWrap());
            register(state, id, view);
            return;
        }
        if ("output".equals(type)) {
            TextView view = label(activity, control.optString("text", ""), 12, false);
            view.setTypeface(Typeface.MONOSPACE);
            view.setTextIsSelectable(true);
            view.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
            content.addView(wrapWithCaption(activity, control.optString("label", id), view), matchWrap());
            register(state, id, view);
            return;
        }
        if ("input".equals(type) || "multiline".equals(type)) {
            TextInputLayout til = new TextInputLayout(activity);
            til.setHint(control.optString("label", id));
            TextInputEditText edit = new TextInputEditText(activity);
            edit.setSingleLine(!"multiline".equals(type) && control.optBoolean("singleLine", true));
            edit.setMinLines(control.optInt("minLines", "multiline".equals(type) ? 4 : 1));
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    | ("multiline".equals(type) ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
            edit.setText(control.optString("default", ""));
            til.addView(edit, new TextInputLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(activity, 6);
            content.addView(til, lp);
            register(state, id, edit);
            JSONObject onChange = control.optJSONObject("onChange");
            if (onChange != null) edit.addTextChangedListener(new SimpleWatcher() {
                @Override public void afterTextChanged(Editable s) {
                    if (!state.updating) runAction(state, onChange);
                }
            });
            return;
        }
        if ("dropdown".equals(type)) {
            TextInputLayout til = new TextInputLayout(activity);
            til.setHint(control.optString("label", id));
            til.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
            MaterialAutoCompleteTextView dd = new MaterialAutoCompleteTextView(activity);
            JSONArray values = control.optJSONArray("values");
            String[] items = jsonStringArray(values);
            dd.setAdapter(new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, items));
            dd.setText(control.optString("default", items.length == 0 ? "" : items[0]), false);
            DropdownUi.bindExposedDropdown(activity, til, dd, () -> DropdownUi.showDropdown(dd));
            til.addView(dd, new TextInputLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(activity, 6);
            content.addView(til, lp);
            register(state, id, dd);
            JSONObject onChange = control.optJSONObject("onChange");
            if (onChange != null) dd.setOnItemClickListener((parent, view, position, rowId) -> runAction(state, onChange));
            return;
        }
        if ("checkbox".equals(type)) {
            CheckBox check = new CheckBox(activity);
            check.setText(control.optString("label", id));
            check.setChecked(control.optBoolean("default", false));
            content.addView(check, matchWrap());
            register(state, id, check);
            return;
        }
        if ("divider".equals(type)) {
            View divider = new View(activity);
            try {
                divider.setBackgroundColor(MaterialColors.getColor(divider,
                        com.google.android.material.R.attr.colorOutline));
                divider.setAlpha(0.55f);
            } catch (Throwable ignored) {
            }
            LinearLayout.LayoutParams lp = matchWrap();
            lp.height = Math.max(1, dp(activity, 1));
            lp.topMargin = dp(activity, 8);
            lp.bottomMargin = dp(activity, 8);
            content.addView(divider, lp);
            register(state, id, divider);
            return;
        }
        if ("group".equals(type) || "section".equals(type)) {
            MaterialCardView card = new MaterialCardView(activity);
            card.setCardElevation(0f);
            try {
                card.setStrokeWidth(dp(activity, 1));
                card.setStrokeColor(MaterialColors.getColor(card,
                        com.google.android.material.R.attr.colorOutline));
            } catch (Throwable ignored) {
            }
            LinearLayout inner = new LinearLayout(activity);
            inner.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(activity, 10);
            inner.setPadding(pad, pad, pad, pad);
            String label = control.optString("label", control.optString("title", ""));
            if (!TextUtils.isEmpty(label)) {
                TextView header = label(activity, label, 13, true);
                header.setPadding(0, 0, 0, dp(activity, 6));
                inner.addView(header, matchWrap());
            }
            JSONArray children = control.optJSONArray("controls");
            if (children != null) {
                for (int i = 0; i < children.length(); i++) {
                    addControl(activity, inner, state, children.optJSONObject(i));
                }
            }
            card.addView(inner, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(activity, 8);
            content.addView(card, lp);
            register(state, id, card);
            return;
        }
        if ("button".equals(type)) {
            MaterialButton button = button(activity, control.optString("text", control.optString("label", id)));
            JSONObject action = control.optJSONObject("action");
            button.setOnClickListener(v -> runAction(state, action));
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(activity, 6);
            content.addView(button, lp);
            register(state, id, button);
            return;
        }
        if ("buttons".equals(type)) {
            JSONArray buttons = control.optJSONArray("buttons");
            if (buttons == null) return;
            LinearLayout row = null;
            for (int i = 0; i < buttons.length(); i++) {
                if ((i % 3) == 0) {
                    row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rowLp = matchWrap();
                    rowLp.topMargin = dp(activity, 6);
                    content.addView(row, rowLp);
                }
                JSONObject item = buttons.optJSONObject(i);
                MaterialButton button = button(activity, item == null ? "" : item.optString("text", ""));
                JSONObject action = item == null ? null : item.optJSONObject("action");
                button.setOnClickListener(v -> runAction(state, action));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.leftMargin = (i % 3) == 0 ? 0 : dp(activity, 6);
                if (row != null) row.addView(button, lp);
            }
        }
    }

    private static void runAction(RuntimeState state, JSONObject action) {
        if (state == null || action == null) return;
        String type = action.optString("type", "toast").toLowerCase(Locale.US);
        try {
            state.updating = true;
            if ("toast".equals(type)) {
                Toast.makeText(state.activity, template(state, action.optString("message", "")), Toast.LENGTH_SHORT).show();
            } else if ("settext".equals(type)) {
                setText(state, action.optString("target", ""), template(state, action.optString("value", "")));
            } else if ("appendtext".equals(type)) {
                setText(state, action.optString("target", ""), textOf(state, action.optString("target", "")) + action.optString("value", ""));
                runNested(state, action.optJSONObject("then"));
            } else if ("clear".equals(type)) {
                setText(state, action.optString("target", ""), "");
                runNested(state, action.optJSONObject("then"));
            } else if ("backspace".equals(type)) {
                String target = action.optString("target", "");
                String s = textOf(state, target);
                setText(state, target, s.length() == 0 ? "" : s.substring(0, s.length() - 1));
                runNested(state, action.optJSONObject("then"));
            } else if ("shell".equals(type)) {
                runShell(state, template(state, action.optString("command", "")), action.optString("output", ""));
            } else if ("api".equals(type)) {
                runApi(state, action);
                runNested(state, action.optJSONObject("then"));
            } else if ("sequence".equals(type)) {
                JSONArray steps = action.optJSONArray("steps");
                if (steps != null) for (int i = 0; i < steps.length(); i++) runAction(state, steps.optJSONObject(i));
            }
        } catch (Throwable t) {
            Toast.makeText(state.activity, "Plugin action failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
            if (state.host != null) state.host.appendOutput("[plugins] Declarative action failed: " + safeMessage(t) + "\n");
        } finally {
            state.updating = false;
        }
    }

    private static void runNested(RuntimeState state, JSONObject action) {
        if (action != null) {
            boolean wasUpdating = state.updating;
            state.updating = false;
            runAction(state, action);
            state.updating = wasUpdating;
        }
    }

    private static void runShell(RuntimeState state, String command, String outputId) {
        if (state == null || state.host == null || TextUtils.isEmpty(command)) return;
        if (state.preview) {
            blockShell(state, outputId, "Shell actions are blocked in Plugin Editor Preview UI. Save/stage the plugin and run it explicitly from the Plugins tab to test shell commands.");
            return;
        }
        if (!PluginRuntimePolicy.isScriptRuntimeEnabled(state.host.getSharedPreferences())) {
            blockShell(state, outputId, "Controlled shell/script plugin actions are disabled by Plugin Runtime Policy.");
            return;
        }
        String approvalProblem = PluginRuntimePolicy.scriptApprovalProblem(state.host.getSharedPreferences(), state.plugin, state.launchAction);
        if (!TextUtils.isEmpty(approvalProblem)) {
            blockShell(state, outputId, approvalProblem);
            return;
        }
        if (PluginRuntimePolicy.isScriptRunConfirmationRequired(state.host.getSharedPreferences())) {
            String message = "This declarative UI shell action will run through the selected PermsTest shell backend.\n\n"
                    + "$ " + command + "\n\n"
                    + "Output target: " + (TextUtils.isEmpty(outputId) ? "none" : outputId) + "\n\n"
                    + "This confirmation applies only to the current explicit tap.";
            new MaterialAlertDialogBuilder(state.activity)
                    .setTitle("Run Declarative Shell Action")
                    .setMessage(message)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Run", (dialog, which) -> runShellDispatch(state, command, outputId))
                    .show();
            return;
        }
        runShellDispatch(state, command, outputId);
    }

    private static void runShellDispatch(RuntimeState state, String command, String outputId) {
        if (state == null || state.host == null || TextUtils.isEmpty(command)) return;
        state.host.runShellCommandCapture(command, (exitCode, stdout, stderr) -> {
            StringBuilder out = new StringBuilder();
            out.append("$ ").append(command).append("\nexit=").append(exitCode).append("\n");
            if (!TextUtils.isEmpty(stdout)) out.append("\n").append(stdout);
            if (!TextUtils.isEmpty(stderr)) out.append("\n[stderr]\n").append(stderr);
            state.activity.runOnUiThread(() -> setText(state, outputId, out.toString()));
        });
    }

    private static void blockShell(RuntimeState state, String outputId, String message) {
        String safe = TextUtils.isEmpty(message) ? "Shell action blocked by Plugin Runtime Policy." : message;
        Toast.makeText(state.activity, safe, Toast.LENGTH_LONG).show();
        if (state.host != null) state.host.appendOutput("[plugins] Declarative shell action blocked: " + safe + "\n");
        if (!TextUtils.isEmpty(outputId)) setText(state, outputId, safe);
    }

    private static void runApi(RuntimeState state, JSONObject action) {
        String name = action.optString("name", "").toLowerCase(Locale.US);
        if ("calculator.evaluateinteger".equals(name)) {
            String input = textOf(state, action.optString("input", "input"));
            int base = baseOf(textOf(state, action.optString("base", "base")));
            JSONObject outputs = action.optJSONObject("outputs");
            try {
                BigInteger value = new IntegerExpressionParser(input, base).parse();
                setText(state, outputs == null ? "hex" : outputs.optString("hex", "hex"), "Hex: " + value.toString(16).toUpperCase(Locale.US));
                setText(state, outputs == null ? "dec" : outputs.optString("dec", "dec"), "Dec: " + value.toString(10));
                setText(state, outputs == null ? "oct" : outputs.optString("oct", "oct"), "Oct: " + value.toString(8));
                setText(state, outputs == null ? "bin" : outputs.optString("bin", "bin"), "Bin: " + value.toString(2));
            } catch (Throwable t) {
                setText(state, action.optString("status", "status"), "Waiting for valid expression: " + safeMessage(t));
            }
            return;
        }
        if ("converter.texttobytes".equals(name)) {
            byte[] bytes = textOf(state, action.optString("input", "text")).getBytes(charset(textOf(state, action.optString("encoding", "encoding"))));
            setText(state, action.optString("output", "values"), formatBytes(bytes, textOf(state, action.optString("valueType", "type")), delimiter(textOf(state, action.optString("delimiter", "delimiter")))));
            return;
        }
        if ("converter.bytestotext".equals(name)) {
            byte[] bytes = parseBytes(textOf(state, action.optString("input", "values")), textOf(state, action.optString("valueType", "type")));
            setText(state, action.optString("output", "text"), new String(bytes, charset(textOf(state, action.optString("encoding", "encoding")))));
            return;
        }
        if ("text.uppercase".equals(name)) {
            setText(state, action.optString("output", "output"), textOf(state, action.optString("input", "input")).toUpperCase(Locale.US));
            return;
        }
        if ("text.lowercase".equals(name)) {
            setText(state, action.optString("output", "output"), textOf(state, action.optString("input", "input")).toLowerCase(Locale.US));
            return;
        }
        if ("text.trim".equals(name)) {
            setText(state, action.optString("output", "output"), textOf(state, action.optString("input", "input")).trim());
            return;
        }
        if ("text.reverse".equals(name)) {
            setText(state, action.optString("output", "output"), new StringBuilder(textOf(state, action.optString("input", "input"))).reverse().toString());
            return;
        }
        if ("text.length".equals(name)) {
            setText(state, action.optString("output", "output"), String.valueOf(textOf(state, action.optString("input", "input")).length()));
            return;
        }
        if ("text.wordcount".equals(name)) {
            setText(state, action.optString("output", "output"), String.valueOf(wordCount(textOf(state, action.optString("input", "input")))));
            return;
        }
        if ("text.isblank".equals(name)) {
            setText(state, action.optString("output", "output"), String.valueOf(TextUtils.isEmpty(textOf(state, action.optString("input", "input")).trim())));
            return;
        }
        if ("text.linecount".equals(name)) {
            setText(state, action.optString("output", "output"), String.valueOf(lineCount(textOf(state, action.optString("input", "input")))));
            return;
        }
        if ("text.contains".equals(name)) {
            String input = textOf(state, action.optString("input", "input"));
            String query = textOf(state, action.optString("query", "query"));
            setText(state, action.optString("output", "output"), String.valueOf(!TextUtils.isEmpty(query) && input.contains(query)));
            return;
        }
        if ("text.replace".equals(name)) {
            String input = textOf(state, action.optString("input", "input"));
            String search = textOf(state, action.optString("search", "search"));
            String replacement = textOf(state, action.optString("replacement", "replacement"));
            setText(state, action.optString("output", "output"), TextUtils.isEmpty(search) ? input : input.replace(search, replacement));
            return;
        }
        if ("json.pretty".equals(name)) {
            setText(state, action.optString("output", "output"), formatJson(textOf(state, action.optString("input", "input")), true));
            return;
        }
        if ("json.minify".equals(name)) {
            setText(state, action.optString("output", "output"), formatJson(textOf(state, action.optString("input", "input")), false));
            return;
        }
        if ("url.encode".equals(name)) {
            setText(state, action.optString("output", "output"), urlEncode(textOf(state, action.optString("input", "input")), textOf(state, action.optString("encoding", "encoding"))));
            return;
        }
        if ("url.decode".equals(name)) {
            setText(state, action.optString("output", "output"), urlDecode(textOf(state, action.optString("input", "input")), textOf(state, action.optString("encoding", "encoding"))));
            return;
        }
        if ("hash.sha256".equals(name)) {
            String input = textOf(state, action.optString("input", "input"));
            setText(state, action.optString("output", "output"), digestHex("SHA-256", input.getBytes(charset(textOf(state, action.optString("encoding", "encoding"))))));
            return;
        }
        if ("encoding.base64encode".equals(name)) {
            String input = textOf(state, action.optString("input", "input"));
            byte[] bytes = input.getBytes(charset(textOf(state, action.optString("encoding", "encoding"))));
            setText(state, action.optString("output", "output"), Base64.encodeToString(bytes, Base64.NO_WRAP));
            return;
        }
        if ("encoding.base64decode".equals(name)) {
            setText(state, action.optString("output", "output"), base64Decode(textOf(state, action.optString("input", "input")), textOf(state, action.optString("encoding", "encoding"))));
            return;
        }
        if ("encoding.hexencode".equals(name)) {
            String input = textOf(state, action.optString("input", "input"));
            byte[] bytes = input.getBytes(charset(textOf(state, action.optString("encoding", "encoding"))));
            setText(state, action.optString("output", "output"), formatBytes(bytes, "Hexadecimal", delimiter(textOf(state, action.optString("delimiter", "delimiter")))));
            return;
        }
        if ("encoding.hexdecode".equals(name)) {
            setText(state, action.optString("output", "output"), hexDecode(textOf(state, action.optString("input", "input")), textOf(state, action.optString("encoding", "encoding"))));
            return;
        }
        setText(state, action.optString("output", "output"), "Unsupported API: " + name);
    }

    private static String digestHex(String algorithm, byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] out = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder hex = new StringBuilder(out.length * 2);
            for (byte b : out) hex.append(pad(Integer.toHexString(b & 0xFF).toUpperCase(Locale.US), 2));
            return hex.toString();
        } catch (Throwable t) {
            return "Hash failed: " + safeMessage(t);
        }
    }

    private static int lineCount(String text) {
        if (TextUtils.isEmpty(text)) return 0;
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    private static int wordCount(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (TextUtils.isEmpty(trimmed)) return 0;
        return trimmed.split("\\s+").length;
    }

    private static String base64Decode(String input, String encoding) {
        try {
            byte[] bytes = Base64.decode(input == null ? "" : input, Base64.DEFAULT);
            return new String(bytes, charset(encoding));
        } catch (Throwable t) {
            return "Base64 decode failed: " + safeMessage(t);
        }
    }

    private static String hexDecode(String input, String encoding) {
        try {
            byte[] bytes = parseBytes(input == null ? "" : input, "Hexadecimal");
            return new String(bytes, charset(encoding));
        } catch (Throwable t) {
            return "Hex decode failed: " + safeMessage(t);
        }
    }

    private static String formatJson(String input, boolean pretty) {
        try {
            String trimmed = input == null ? "" : input.trim();
            if (trimmed.startsWith("[")) {
                JSONArray array = new JSONArray(trimmed);
                return pretty ? array.toString(2) : array.toString();
            }
            JSONObject object = new JSONObject(trimmed);
            return pretty ? object.toString(2) : object.toString();
        } catch (Throwable t) {
            return "JSON failed: " + safeMessage(t);
        }
    }

    private static String urlEncode(String input, String encoding) {
        try {
            return URLEncoder.encode(input == null ? "" : input, charset(encoding).name());
        } catch (Throwable t) {
            return "URL encode failed: " + safeMessage(t);
        }
    }

    private static String urlDecode(String input, String encoding) {
        try {
            return URLDecoder.decode(input == null ? "" : input, charset(encoding).name());
        } catch (Throwable t) {
            return "URL decode failed: " + safeMessage(t);
        }
    }

    private static void setText(RuntimeState state, String id, String text) {
        View view = state.views.get(id);
        if (view instanceof TextView) ((TextView) view).setText(text == null ? "" : text);
    }

    private static String textOf(RuntimeState state, String id) {
        View view = state.views.get(id);
        if (view instanceof TextView) {
            CharSequence cs = ((TextView) view).getText();
            return cs == null ? "" : cs.toString();
        }
        if (view instanceof CheckBox) return ((CheckBox) view).isChecked() ? "true" : "false";
        return "";
    }

    private static String template(RuntimeState state, String value) {
        String out = value == null ? "" : value;
        for (Map.Entry<String, View> e : state.views.entrySet()) {
            out = out.replace("${" + e.getKey() + "}", textOf(state, e.getKey()));
        }
        return out;
    }

    private static void register(RuntimeState state, String id, View view) {
        if (!TextUtils.isEmpty(id) && view != null) state.views.put(id, view);
    }

    private static LinearLayout wrapWithCaption(Context context, String caption, View child) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        if (!TextUtils.isEmpty(caption)) {
            TextView label = label(context, caption, 12, true);
            label.setPadding(0, dp(context, 6), 0, dp(context, 3));
            box.addView(label);
        }
        box.addView(child, matchWrap());
        return box;
    }

    private static TextView label(Context context, String text, int sp, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text == null ? "" : text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static MaterialButton button(Context context, String text) {
        MaterialButton b = new MaterialButton(context);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinHeight(dp(context, 40));
        return b;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static String[] jsonStringArray(JSONArray array) {
        if (array == null || array.length() == 0) return new String[0];
        String[] out = new String[array.length()];
        for (int i = 0; i < array.length(); i++) out[i] = array.optString(i, "");
        return out;
    }

    private static File resolvePluginFile(PluginManifest plugin, String relative) throws Exception {
        if (plugin == null || plugin.homeDir == null) throw new IllegalArgumentException("Plugin home is unavailable");
        File file = new File(plugin.homeDir, TextUtils.isEmpty(relative) ? "ui.json" : relative);
        String root = plugin.homeDir.getCanonicalPath() + File.separator;
        String path = file.getCanonicalPath();
        if (!path.startsWith(root) || !file.isFile()) throw new IllegalArgumentException("Plugin file not found: " + relative);
        return file;
    }

    private static String readUtf8(File file) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), 1024 * 1024))) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) for (String s : values) if (!TextUtils.isEmpty(s)) return s;
        return "";
    }

    private static int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private static int baseOf(String value) {
        if ("Dec".equalsIgnoreCase(value)) return 10;
        if ("Oct".equalsIgnoreCase(value)) return 8;
        if ("Bin".equalsIgnoreCase(value)) return 2;
        return 16;
    }

    private static Charset charset(String label) {
        try {
            if ("ASCII".equals(label)) return StandardCharsets.US_ASCII;
            if ("UTF-16".equals(label)) return StandardCharsets.UTF_16;
            if (label != null && label.toLowerCase(Locale.US).contains("little")) return StandardCharsets.UTF_16LE;
            if (label != null && label.toLowerCase(Locale.US).contains("big")) return StandardCharsets.UTF_16BE;
            return StandardCharsets.UTF_8;
        } catch (Throwable ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String delimiter(String label) {
        if ("Comma".equals(label)) return ", ";
        if ("None".equals(label)) return "";
        return " ";
    }

    private static String formatBytes(byte[] bytes, String type, String delimiter) {
        StringBuilder out = new StringBuilder();
        String t = TextUtils.isEmpty(type) ? "Hexadecimal" : type;
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            if (i > 0) out.append(delimiter);
            if ("Binary".equals(t)) out.append(pad(Integer.toBinaryString(v), 8));
            else if ("Decimal".equals(t)) out.append(v);
            else if ("Octal".equals(t)) out.append(pad(Integer.toOctalString(v), 3));
            else out.append(pad(Integer.toHexString(v).toUpperCase(Locale.US), 2));
        }
        return out.toString();
    }

    private static byte[] parseBytes(String values, String type) {
        String raw = values == null ? "" : values.trim();
        if (TextUtils.isEmpty(raw)) return new byte[0];
        String t = TextUtils.isEmpty(type) ? "Hexadecimal" : type;
        String[] tokens = raw.split("[\\s,;:]+");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String token : tokens) {
            if (TextUtils.isEmpty(token)) continue;
            int v;
            if ("Binary".equals(t)) v = Integer.parseInt(cleanPrefix(token, "0b"), 2);
            else if ("Decimal".equals(t)) v = Integer.parseInt(token.trim(), 10);
            else if ("Octal".equals(t)) v = Integer.parseInt(cleanPrefix(token, "0o"), 8);
            else v = Integer.parseInt(cleanPrefix(token, "0x"), 16);
            if (v < 0 || v > 255) throw new IllegalArgumentException("Byte out of range: " + token);
            out.write(v);
        }
        return out.toByteArray();
    }

    private static String cleanPrefix(String value, String prefix) {
        String token = value == null ? "" : value.trim();
        return token.regionMatches(true, 0, prefix, 0, prefix.length()) ? token.substring(prefix.length()) : token;
    }

    private static String pad(String value, int width) {
        String s = value == null ? "" : value;
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < width; i++) b.append('0');
        b.append(s);
        return b.toString();
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.toString() : msg;
    }

    private static final class RuntimeState {
        final PluginActionRegistry.Host host;
        final PluginManifest plugin;
        final PluginAction launchAction;
        final Activity activity;
        final boolean preview;
        final Map<String, View> views = new HashMap<>();
        boolean updating;

        RuntimeState(PluginActionRegistry.Host host, PluginManifest plugin, PluginAction launchAction, Activity activity, boolean preview) {
            this.host = host;
            this.plugin = plugin;
            this.launchAction = launchAction;
            this.activity = activity;
            this.preview = preview;
        }
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private static final class IntegerExpressionParser {
        private final String s;
        private final int base;
        private int pos;

        IntegerExpressionParser(String s, int base) {
            this.s = s == null ? "" : s;
            this.base = base;
        }

        BigInteger parse() {
            BigInteger value = parseOr();
            skipWs();
            if (pos < s.length()) throw new IllegalArgumentException("Unexpected: " + s.charAt(pos));
            return value;
        }

        private BigInteger parseOr() {
            BigInteger value = parseXor();
            while (true) { skipWs(); if (match('|')) value = value.or(parseXor()); else return value; }
        }
        private BigInteger parseXor() {
            BigInteger value = parseAnd();
            while (true) { skipWs(); if (match('^')) value = value.xor(parseAnd()); else return value; }
        }
        private BigInteger parseAnd() {
            BigInteger value = parseShift();
            while (true) { skipWs(); if (match('&')) value = value.and(parseShift()); else return value; }
        }
        private BigInteger parseShift() {
            BigInteger value = parseAdd();
            while (true) {
                skipWs();
                if (match("<<")) value = value.shiftLeft(parseAdd().intValue());
                else if (match(">>")) value = value.shiftRight(parseAdd().intValue());
                else return value;
            }
        }
        private BigInteger parseAdd() {
            BigInteger value = parseMul();
            while (true) {
                skipWs();
                if (match('+')) value = value.add(parseMul());
                else if (match('-')) value = value.subtract(parseMul());
                else return value;
            }
        }
        private BigInteger parseMul() {
            BigInteger value = parseUnary();
            while (true) {
                skipWs();
                if (match('*')) value = value.multiply(parseUnary());
                else if (match('/')) value = value.divide(parseUnary());
                else return value;
            }
        }
        private BigInteger parseUnary() {
            skipWs();
            if (match('-')) return parseUnary().negate();
            if (match('~')) return parseUnary().not();
            if (match('(')) {
                BigInteger v = parseOr();
                if (!match(')')) throw new IllegalArgumentException("Missing )");
                return v;
            }
            return parseNumber();
        }
        private BigInteger parseNumber() {
            skipWs();
            int start = pos;
            while (pos < s.length() && Character.toString(s.charAt(pos)).matches("[0-9a-fA-FxXbBoO]")) pos++;
            if (start == pos) throw new IllegalArgumentException("Number expected");
            String token = s.substring(start, pos);
            int radix = base;
            if (token.startsWith("0x") || token.startsWith("0X")) { radix = 16; token = token.substring(2); }
            else if (token.startsWith("0b") || token.startsWith("0B")) { radix = 2; token = token.substring(2); }
            else if (token.startsWith("0o") || token.startsWith("0O")) { radix = 8; token = token.substring(2); }
            return new BigInteger(token, radix);
        }
        private void skipWs() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
        private boolean match(char c) { if (pos < s.length() && s.charAt(pos) == c) { pos++; return true; } return false; }
        private boolean match(String token) { if (s.startsWith(token, pos)) { pos += token.length(); return true; } return false; }
    }
}
