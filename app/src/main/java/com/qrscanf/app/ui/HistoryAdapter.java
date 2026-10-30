package com.qrscanf.app.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qrscanf.app.R;
import com.qrscanf.app.model.BarcodeItem;
import com.qrscanf.app.model.GenRecord;
import com.qrscanf.app.util.BarcodeUtils;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

    public interface Listener {
        void onScanClick(BarcodeItem item);

        void onGenClick(GenRecord record);

        void onCopy(String text);
    }

    private final List<Object> entries;
    private final Listener listener;

    public HistoryAdapter(List<Object> entries, Listener listener) {
        this.entries = entries;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Object entry = entries.get(position);
        Context context = holder.itemView.getContext();
        String formatLabel;
        String source;
        String value;
        String time;
        String copyText;
        int color;
        if (entry instanceof BarcodeItem) {
            BarcodeItem item = (BarcodeItem) entry;
            formatLabel = item.formatLabel;
            source = item.source;
            value = item.value;
            time = BarcodeUtils.time(item.timestamp);
            copyText = item.value;
            color = BarcodeUtils.formatColor(item.mlkitFormat);
            holder.itemView.setOnClickListener(v -> listener.onScanClick(item));
        } else {
            GenRecord record = (GenRecord) entry;
            formatLabel = record.formatLabel.split("（")[0];
            source = "生成 · " + record.size + " px";
            value = record.content;
            time = BarcodeUtils.time(record.timestamp);
            copyText = record.content;
            color = 0xFF5B6CF9;
            holder.itemView.setOnClickListener(v -> listener.onGenClick(record));
        }

        holder.tvFormat.setText(formatLabel);
        holder.tvFormat.setTextColor(color);
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(dp(context, 6));
        chip.setColor((0x1A << 24) | (color & 0x00FFFFFF));
        holder.tvFormat.setBackground(chip);
        holder.tvSource.setText(source);
        holder.tvValue.setText(value);
        holder.tvTime.setText(time);
        holder.btnCopy.setOnClickListener(v -> listener.onCopy(copyText));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvFormat;
        final TextView tvSource;
        final TextView tvValue;
        final TextView tvTime;
        final ImageButton btnCopy;

        Holder(@NonNull View itemView) {
            super(itemView);
            tvFormat = itemView.findViewById(R.id.tv_format);
            tvSource = itemView.findViewById(R.id.tv_source);
            tvValue = itemView.findViewById(R.id.tv_value);
            tvTime = itemView.findViewById(R.id.tv_time);
            btnCopy = itemView.findViewById(R.id.btn_copy);
        }
    }
}
