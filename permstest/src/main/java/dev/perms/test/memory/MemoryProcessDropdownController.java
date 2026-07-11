package dev.perms.test.memory;

import android.content.Context;
import android.text.TextUtils;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.perms.test.ui.DropdownUi;

/**
 * Activity-side controller for the Memory running-process dropdown.
 *
 * MainActivity supplies the selected target package, threading, and output plumbing. This
 * controller owns only the process dropdown adapter, process refresh, restored selection, and
 * stable Memory status text for the Activity-side Memory tab UI.
 */
public final class MemoryProcessDropdownController {
    public interface TargetPackageProvider {
        String getTargetPackage();
    }

    public interface BackgroundExecutor {
        void execute(Runnable task);
    }

    public interface UiExecutor {
        void execute(Runnable task);
    }

    public interface OutputAppender {
        void append(String text);
    }

    private final Context context;
    private final AutoCompleteTextView processDropdown;
    private final TargetPackageProvider targetPackageProvider;
    private final BackgroundExecutor backgroundExecutor;
    private final UiExecutor uiExecutor;
    private final OutputAppender outputAppender;

    private String restoredProcessText;

    public MemoryProcessDropdownController(Context context,
                                           AutoCompleteTextView processDropdown,
                                           String restoredProcessText,
                                           TargetPackageProvider targetPackageProvider,
                                           BackgroundExecutor backgroundExecutor,
                                           UiExecutor uiExecutor,
                                           OutputAppender outputAppender) {
        this.context = context;
        this.processDropdown = processDropdown;
        this.restoredProcessText = restoredProcessText;
        this.targetPackageProvider = targetPackageProvider;
        this.backgroundExecutor = backgroundExecutor;
        this.uiExecutor = uiExecutor;
        this.outputAppender = outputAppender;
    }

    public void bindInitial() {
        ArrayList<MemoryProcessEntry> initialItems = MemoryProcesses.buildInitialProcessDropdownItems();
        MemoryProcessAdapter processAdapter = new MemoryProcessAdapter(
                context,
                android.R.layout.simple_list_item_1,
                initialItems
        );
        processDropdown.setAdapter(processAdapter);
        DropdownUi.prepareExposedDropdown(null, processDropdown);
        processDropdown.setText(MemoryProcesses.resolveSelectedProcessText(null, restoredProcessText), false);
    }

    public String getSelectedPid() {
        try {
            return MemoryTargets.parseSelectedProcessPid(processDropdown.getText());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void refresh() {
        refresh(true);
    }

    public void refresh(boolean userInitiated) {
        try {
            final String pkg = getTargetPackage();
            if (TextUtils.isEmpty(pkg)) {
                if (userInitiated) {
                    Toast.makeText(context, "Enter a target package first.", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            appendOutput("[Memory] Refreshing running processes for " + pkg + "...\n");
            executeInBackground(() -> {
                final List<MemoryProcessEntry> processes = MemoryToolRuntime.listTargetProcesses(context.getApplicationContext(), pkg);
                executeOnUi(() -> updateDropdown(processes));
            });
        } catch (Throwable t) {
            appendOutput("[Memory] Failed to refresh processes: " + t + "\n");
        }
    }

    private void updateDropdown(List<MemoryProcessEntry> processes) {
        try {
            String previous = null;
            try {
                CharSequence current = processDropdown.getText();
                previous = current == null ? null : current.toString().trim();
            } catch (Throwable ignored) {
            }

            ArrayList<MemoryProcessEntry> items = MemoryProcesses.buildProcessDropdownItems(processes);

            MemoryProcessAdapter processAdapter;
            if (processDropdown.getAdapter() instanceof MemoryProcessAdapter) {
                processAdapter = (MemoryProcessAdapter) processDropdown.getAdapter();
                processAdapter.setItems(items);
            } else {
                processAdapter = new MemoryProcessAdapter(context, android.R.layout.simple_list_item_1, items);
                processDropdown.setAdapter(processAdapter);
            }

            String selected = MemoryProcesses.resolveSelectedProcessText(previous, restoredProcessText);
            int pos = findAdapterIndexByText(processAdapter, selected);
            if (pos >= 0) {
                Object item = processAdapter.getItem(pos);
                processDropdown.setText(item == null ? selected : item.toString(), false);
            } else {
                processDropdown.setText(items.get(0).toString(), false);
            }
            restoredProcessText = null;

            appendOutput(MemoryProcesses.formatProcessRefreshStatus(processes));
        } catch (Throwable t) {
            appendOutput("[Memory] Failed to update process dropdown: " + t + "\n");
        }
    }

    private String getTargetPackage() {
        try {
            return targetPackageProvider == null ? "" : targetPackageProvider.getTargetPackage();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void executeInBackground(Runnable task) {
        if (backgroundExecutor != null) backgroundExecutor.execute(task);
    }

    private void executeOnUi(Runnable task) {
        if (uiExecutor != null) uiExecutor.execute(task);
    }

    private void appendOutput(String text) {
        if (TextUtils.isEmpty(text)) return;
        try {
            if (outputAppender != null) outputAppender.append(text);
        } catch (Throwable ignored) {
        }
    }

    private static int findAdapterIndexByText(MemoryProcessAdapter adapter, String text) {
        if (adapter == null || TextUtils.isEmpty(text)) return -1;
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (item != null && text.equals(item.toString())) return i;
        }
        return -1;
    }
}
