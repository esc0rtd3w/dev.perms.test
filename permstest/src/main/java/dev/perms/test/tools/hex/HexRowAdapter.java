package dev.perms.test.tools.hex;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Virtualized row adapter for the Tools-tab Hex Editor.
 *
 * Rows are custom-drawn instead of being built from many TextViews. That keeps
 * file-window scrolling light, avoids unnecessary layout work, and still gives
 * precise byte hit testing for the hex and ASCII columns.
 */
public final class HexRowAdapter extends RecyclerView.Adapter<HexRowAdapter.VH> {
    public interface Listener {
        void onByteSelected(long absoluteOffset, boolean openEditor);
        void onByteRangeSelected(long startOffset, long endOffsetInclusive);
    }

    private final Listener listener;
    private long baseOffset;
    private byte[] data = new byte[0];
    private long selectedOffset = -1L;
    private int selectedLength;
    private boolean dragSelectEnabled;

    public HexRowAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(long baseOffset, byte[] bytes, long selectedOffset, int selectedLength) {
        this.baseOffset = Math.max(0L, baseOffset);
        this.data = bytes == null ? new byte[0] : bytes;
        this.selectedOffset = selectedOffset;
        this.selectedLength = selectedLength;
        notifyDataSetChanged();
    }

    public void updateSelection(long newSelectedOffset, int newSelectedLength) {
        if (selectedOffset == newSelectedOffset && selectedLength == newSelectedLength) return;
        int oldStart = rowForSelectionStart(selectedOffset, selectedLength);
        int oldEnd = rowForSelectionEnd(selectedOffset, selectedLength);
        int newStart = rowForSelectionStart(newSelectedOffset, newSelectedLength);
        int newEnd = rowForSelectionEnd(newSelectedOffset, newSelectedLength);
        selectedOffset = newSelectedOffset;
        selectedLength = newSelectedLength;
        notifyRange(oldStart, oldEnd);
        notifyRange(newStart, newEnd);
    }

    public void setDragSelectEnabled(boolean enabled) {
        dragSelectEnabled = enabled;
    }

    public int rowForAbsoluteOffset(long absoluteOffset) {
        if (absoluteOffset < baseOffset) return 0;
        long row = (absoluteOffset - baseOffset) / HexPaneRenderer.BYTES_PER_ROW;
        long max = Math.max(0L, getItemCount() - 1L);
        return (int) Math.max(0L, Math.min(row, max));
    }

    @Override
    public long getItemId(int position) {
        return baseOffset + ((long) position * HexPaneRenderer.BYTES_PER_ROW);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        HexRowView row = new HexRowView(parent.getContext());
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setListener(listener == null ? null : new HexRowView.Listener() {
            @Override
            public void onByteSelected(long absoluteOffset, boolean openEditor) {
                listener.onByteSelected(absoluteOffset, openEditor);
            }

            @Override
            public void onByteRangeSelected(long startOffset, long endOffsetInclusive) {
                listener.onByteRangeSelected(startOffset, endOffsetInclusive);
            }
        });
        return new VH(row);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        int rowStart = position * HexPaneRenderer.BYTES_PER_ROW;
        holder.row.bind(baseOffset, data, rowStart, selectedOffset, selectedLength, dragSelectEnabled);
    }

    @Override
    public int getItemCount() {
        if (data.length == 0) return 1;
        return (data.length + HexPaneRenderer.BYTES_PER_ROW - 1) / HexPaneRenderer.BYTES_PER_ROW;
    }

    private int rowForSelectionStart(long offset, int length) {
        if (offset < 0L || length <= 0 || data.length <= 0) return -1;
        long start = Math.max(baseOffset, offset);
        long end = baseOffset + data.length - 1L;
        if (start > end) return -1;
        return rowForAbsoluteOffset(start);
    }

    private int rowForSelectionEnd(long offset, int length) {
        if (offset < 0L || length <= 0 || data.length <= 0) return -1;
        long start = Math.max(baseOffset, offset);
        long end = Math.min(baseOffset + data.length - 1L, offset + length - 1L);
        if (end < start) return -1;
        return rowForAbsoluteOffset(end);
    }

    private void notifyRange(int start, int end) {
        if (start < 0 || end < start) return;
        int count = Math.min(getItemCount(), end + 1) - start;
        if (count > 0) notifyItemRangeChanged(start, count, "selection");
    }

    static final class VH extends RecyclerView.ViewHolder {
        final HexRowView row;

        VH(@NonNull HexRowView row) {
            super(row);
            this.row = row;
        }
    }
}
