package dev.perms.test.shell;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import dev.perms.test.R;

/**
 * Owns Shell custom-command import/export UI and storage dispatch.
 */
public final class CustomCommandImportExport {
    public interface Host {
        void persistCommands();
        void renderCommands();
        void appendOutput(String text);
    }

    public static final String EXPORT_FILENAME = "perms_test_custom_commands.json";
    private static final String MIME_JSON = "application/json";
    private static final int MAX_COMMANDS = 50;

    private final AppCompatActivity activity;
    private final ArrayList<CustomCommand> commands;
    private final Host host;

    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;
    private String pendingExportJson;

    public CustomCommandImportExport(AppCompatActivity activity, ArrayList<CustomCommand> commands, Host host) {
        if (activity == null) throw new IllegalArgumentException("activity == null");
        if (commands == null) throw new IllegalArgumentException("commands == null");
        if (host == null) throw new IllegalArgumentException("host == null");
        this.activity = activity;
        this.commands = commands;
        this.host = host;
    }

    public void registerActivityResults() {
        ensureLaunchers();
    }

    public void bind(View exportButton, View importButton) {
        try {
            ensureLaunchers();
            if (exportButton != null) exportButton.setOnClickListener(v -> startExport());
            if (importButton != null) importButton.setOnClickListener(v -> startImport());
        } catch (Throwable ignored) {
        }
    }

    public void startExport() {
        try {
            CustomCommandList.normalizeOrders(commands);
            host.persistCommands();
            pendingExportJson = CustomCommandJson.toExportJson(commands);

            Uri saved = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saved = CustomCommandStorage.writeTextToDownloads(activity, EXPORT_FILENAME, MIME_JSON, pendingExportJson);
            }

            if (saved != null) {
                Toast.makeText(activity, "Exported to Downloads/" + EXPORT_FILENAME, Toast.LENGTH_SHORT).show();
                pendingExportJson = null;
                return;
            }

            ensureLaunchers();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(MIME_JSON);
            intent.putExtra(Intent.EXTRA_TITLE, EXPORT_FILENAME);
            exportLauncher.launch(intent);
        } catch (Throwable t) {
            host.appendOutput("[!] Export failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public void startImport() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                String text = CustomCommandStorage.readTextFromDownloads(activity, EXPORT_FILENAME);
                if (text != null && !text.trim().isEmpty()) {
                    ArrayList<CustomCommand> imported = CustomCommandJson.parse(text);
                    if (imported != null && !imported.isEmpty()) {
                        promptApplyImported(imported);
                        return;
                    }
                }
            }

            ensureLaunchers();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(MIME_JSON);
            importLauncher.launch(intent);
        } catch (Throwable t) {
            host.appendOutput("[!] Import failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void ensureLaunchers() {
        if (exportLauncher == null) {
            exportLauncher = activity.registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::handleExportResult
            );
        }
        if (importLauncher == null) {
            importLauncher = activity.registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::handleImportResult
            );
        }
    }

    private void handleExportResult(ActivityResult result) {
        try {
            if (result == null) return;
            if (result.getResultCode() != AppCompatActivity.RESULT_OK) return;
            Intent data = result.getData();
            if (data == null) return;
            Uri uri = data.getData();
            if (uri == null) return;

            if (pendingExportJson == null) {
                pendingExportJson = CustomCommandJson.toExportJson(commands);
            }

            try (OutputStream out = activity.getContentResolver().openOutputStream(uri, "w")) {
                if (out != null) {
                    out.write(pendingExportJson.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Toast.makeText(activity, "Exported", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "Export failed (no stream)", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Throwable t) {
            host.appendOutput("[!] Export failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            pendingExportJson = null;
        }
    }

    private void handleImportResult(ActivityResult result) {
        try {
            if (result == null) return;
            if (result.getResultCode() != AppCompatActivity.RESULT_OK) return;
            Intent data = result.getData();
            if (data == null) return;
            Uri uri = data.getData();
            if (uri == null) return;

            String text;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    Toast.makeText(activity, "Import failed (no stream)", Toast.LENGTH_SHORT).show();
                    return;
                }
                text = CustomCommandStorage.readAll(in);
            }

            ArrayList<CustomCommand> imported = CustomCommandJson.parse(text);
            if (imported == null || imported.isEmpty()) {
                Toast.makeText(activity, "No commands found in file", Toast.LENGTH_SHORT).show();
                return;
            }

            promptApplyImported(imported);
        } catch (Throwable t) {
            host.appendOutput("[!] Import failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void promptApplyImported(final ArrayList<CustomCommand> imported) {
        try {
            if (imported == null || imported.isEmpty()) return;

            if (!commands.isEmpty()) {
                final int existingCount = commands.size();
                final int importCount = imported.size();
                final CharSequence[] options = new CharSequence[] {
                        "Merge (" + existingCount + " existing + " + importCount + " imported)",
                        "Replace (" + importCount + " imported)"
                };
                new AlertDialog.Builder(activity)
                        .setTitle("Import custom commands")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) applyImported(imported, false);
                            else if (which == 1) applyImported(imported, true);
                        })
                        .setNegativeButton(R.string.shell_action_cancel, null)
                        .show();
            } else {
                applyImported(imported, true);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyImported(ArrayList<CustomCommand> imported, boolean replace) {
        try {
            if (imported == null) return;

            CustomCommandList.applyImported(commands, imported, replace, MAX_COMMANDS);
            host.persistCommands();
            host.renderCommands();
            Toast.makeText(activity, replace ? "Imported" : "Merged", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            host.appendOutput("[!] Import apply failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
