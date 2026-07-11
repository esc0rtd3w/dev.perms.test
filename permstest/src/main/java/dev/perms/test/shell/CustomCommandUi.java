package dev.perms.test.shell;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

public final class CustomCommandUi {
    public interface Host {
        void run(CustomCommand command);
        void showMenu(CustomCommand command);
        void showManageMenu(CustomCommand command);
        void onCommandsReordered();
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final List<CustomCommand> commands;
    private final Host host;

    private CustomCommandAdapter adapter;
    private ItemTouchHelper touchHelper;

    public CustomCommandUi(Context context, LayoutInflater inflater, List<CustomCommand> commands, Host host) {
        this.context = context;
        this.inflater = inflater;
        this.commands = commands;
        this.host = host;
    }

    public void bind(RecyclerView recyclerView, TextView emptyView) {
        if (recyclerView == null) return;
        ensureAdapter();
        recyclerView.setLayoutManager(new GridLayoutManager(context, 2));
        recyclerView.setAdapter(adapter);
        ensureTouchHelper();
        touchHelper.attachToRecyclerView(recyclerView);
        render(emptyView);
    }

    public void render(TextView emptyView) {
        if (emptyView != null) {
            emptyView.setVisibility(commands == null || commands.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void ensureAdapter() {
        if (adapter != null) return;
        adapter = new CustomCommandAdapter(context, inflater, commands, new CustomCommandAdapter.Callbacks() {
            @Override
            public void run(CustomCommand command) {
                if (host != null) host.run(command);
            }

            @Override
            public void showMenu(CustomCommand command) {
                if (host != null) host.showMenu(command);
            }

            @Override
            public void showManageMenu(CustomCommand command) {
                if (host != null) host.showManageMenu(command);
            }

            @Override
            public void startDrag(RecyclerView.ViewHolder holder) {
                if (touchHelper != null && holder != null) touchHelper.startDrag(holder);
            }
        });
    }

    private void ensureTouchHelper() {
        if (touchHelper != null) return;
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                0
        ) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                // Swipe actions are intentionally disabled for custom command rows.
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                try {
                    int from = viewHolder.getAdapterPosition();
                    int to = target.getAdapterPosition();
                    if (from < 0 || to < 0) return false;
                    if (commands == null || from >= commands.size() || to >= commands.size()) return false;

                    CustomCommand current = commands.get(from);
                    CustomCommand targetCommand = commands.get(to);
                    if (current == null || targetCommand == null) return false;

                    // Keep pinned commands grouped at the top.
                    if (current.pinned != targetCommand.pinned) return false;

                    Collections.swap(commands, from, to);
                    if (adapter != null) adapter.notifyItemMoved(from, to);
                    return true;
                } catch (Throwable ignored) {
                    return false;
                }
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                try {
                    if (host != null) host.onCommandsReordered();
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
