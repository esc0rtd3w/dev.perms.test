package dev.perms.test.editor;

import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

/** RecyclerView adapter that renders only visible source-file lines. */
public final class VirtualTextLineAdapter extends RecyclerView.Adapter<VirtualTextLineAdapter.VH> {
    public interface LineFormatter {
        CharSequence formatLine(String text);
    }

    public interface LineClickListener {
        void onLineClicked(int lineIndex, String text);
    }

    private VirtualTextDocument document;
    private LineFormatter formatter;
    private LineClickListener lineClickListener;

    public void setFormatter(LineFormatter formatter) {
        this.formatter = formatter;
        notifyDataSetChanged();
    }

    public void setLineClickListener(LineClickListener listener) {
        this.lineClickListener = listener;
    }

    public void setDocument(VirtualTextDocument document) {
        this.document = document;
        notifyDataSetChanged();
    }

    public VirtualTextDocument getDocument() {
        return document;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        android.content.Context context = parent.getContext();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(context, 6), dp(context, 4), dp(context, 12), dp(context, 4));
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView lineNumber = new TextView(context);
        lineNumber.setTypeface(Typeface.MONOSPACE);
        lineNumber.setTextSize(11f);
        lineNumber.setAlpha(0.62f);
        lineNumber.setGravity(Gravity.RIGHT | Gravity.TOP);
        row.addView(lineNumber, new LinearLayout.LayoutParams(dp(context, 64), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView text = new TextView(context);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(12f);
        text.setSingleLine(true);
        text.setHorizontallyScrolling(true);
        text.setIncludeFontPadding(false);
        text.setEllipsize(null);
        text.setPadding(dp(context, 10), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return new VH(row, lineNumber, text);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        VirtualTextDocument doc = document;
        String line = doc == null ? "" : doc.getLine(position);
        holder.lineNumber.setText(String.format(Locale.US, "%6d", position + 1));
        CharSequence formatted = formatter == null ? line : formatter.formatLine(line);
        holder.text.setText(TextUtils.isEmpty(formatted) ? " " : formatted);
        holder.itemView.setOnClickListener(v -> {
            LineClickListener listener = lineClickListener;
            int adapterPosition = holder.getBindingAdapterPosition();
            if (listener != null && adapterPosition != RecyclerView.NO_POSITION) {
                listener.onLineClicked(adapterPosition, doc == null ? "" : doc.getLine(adapterPosition));
            }
        });
    }

    @Override
    public int getItemCount() {
        return document == null ? 0 : document.getLineCount();
    }

    public void notifyLineChanged(int lineIndex) {
        if (lineIndex >= 0 && lineIndex < getItemCount()) notifyItemChanged(lineIndex);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView lineNumber;
        final TextView text;

        VH(@NonNull View itemView, TextView lineNumber, TextView text) {
            super(itemView);
            this.lineNumber = lineNumber;
            this.text = text;
        }
    }

    private static int dp(android.content.Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
