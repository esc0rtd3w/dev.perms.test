package dev.perms.test.home;

import dev.perms.test.R;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

final class AppTrayAdapter extends RecyclerView.Adapter<AppTrayAdapter.VH> {

    interface Host {
        void launchApp(String packageName);
        void runShell(String cmd, String labelForLog);
        void makeDebugPackage(String packageName, String label);
        void extractPackage(String packageName, String label);
        Context getContext();
    }

    private final Host host;
    private final LayoutInflater inflater;
    private final List<AppEntry> items = new ArrayList<>();

    AppTrayAdapter(@NonNull Host host) {
        this.host = host;
        this.inflater = LayoutInflater.from(host.getContext());
        setHasStableIds(true);
    }

    void setItems(@NonNull List<AppEntry> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).packageName.hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.item_app_tray, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final AppEntry e = items.get(position);
        h.icon.setImageDrawable(e.icon);
        h.label.setText(e.label);

        h.itemView.setOnClickListener(v -> host.launchApp(e.packageName));

        h.itemView.setOnLongClickListener(v -> {
            showLongPressMenu(v.getContext(), e);
            return true;
        });
    }

    private void showLongPressMenu(Context ctx, AppEntry e) {
        final ArrayList<String> opts = new ArrayList<>();
        opts.add("Open");
        opts.add("App info");
        opts.add("Make Debug Package");
        opts.add("Extract Package");
        opts.add("Backup");
        opts.add("Restore");
        opts.add("Force stop");
        if (!e.isSystemApp) opts.add("Uninstall");

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(String.valueOf(e.label))
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    String choice = opts.get(which);
                    switch (choice) {
                        case "Open":
                            host.launchApp(e.packageName);
                            return;
                        case "App info":
                            try {
                                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                i.setData(Uri.parse("package:" + e.packageName));
                                ctx.startActivity(i);
                            } catch (Throwable t) {
                                Toast.makeText(ctx, "Unable to open app info", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        case "Make Debug Package":
                            host.makeDebugPackage(e.packageName, String.valueOf(e.label));
                            return;
                        case "Extract Package":
                            host.extractPackage(e.packageName, String.valueOf(e.label));
                            return;
                        case "Backup":
                            confirmBackup(ctx, e);
                            return;
                        case "Restore":
                            confirmRestore(ctx, e);
                            return;
                        case "Force stop":
                            host.runShell("am force-stop " + shellQuote(e.packageName), "force-stop");
                            return;
                        case "Uninstall":
                            try {
                                Intent i = new Intent(Intent.ACTION_DELETE);
                                i.setData(Uri.parse("package:" + e.packageName));
                                ctx.startActivity(i);
                            } catch (Throwable t) {
                                Toast.makeText(ctx, "Unable to start uninstall", Toast.LENGTH_SHORT).show();
                            }
                            return;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void confirmBackup(Context ctx, AppEntry e) {
        final String path = AppTrayBackupCommands.backupPath(e.packageName);
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Backup")
                .setMessage("Back up " + e.label + " to:\n" + path)
                .setPositiveButton("Backup", (d, which) -> {
                    host.runShell(AppTrayBackupCommands.backupCommand(e.packageName), "backup");
                    Toast.makeText(ctx, "Backup started", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRestore(Context ctx, AppEntry e) {
        final String path = AppTrayBackupCommands.backupPath(e.packageName);
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Restore")
                .setMessage("Restore " + e.label + " from:\n" + path)
                .setPositiveButton("Restore", (d, which) -> {
                    host.runShell(AppTrayBackupCommands.restoreCommand(e.packageName), "restore");
                    Toast.makeText(ctx, "Restore started", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String shellQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imgIcon);
            label = itemView.findViewById(R.id.txtLabel);
        }
    }
}
