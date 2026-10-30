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
import com.qrscanf.app.util.BarcodeUtils;

import java.util.List;

public class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder> {

    private final List<BarcodeItem> items;
    private final boolean showIndex;

    public ResultAdapter(List<BarcodeItem> items, boolean showIndex) {
        this.items = items;
        this.showIndex = showIndex;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_result, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        BarcodeItem item = items.get(position);
        Context context = holder.itemView.getContext();
        int color = BarcodeUtils.formatColor(item.mlkitFormat);

        holder.tvFormat.setText(item.formatLabel);
        holder.tvFormat.setTextColor(color);
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(dp(context, 6));
        chip.setColor((0x1A << 24) | (color & 0x00FFFFFF));
        holder.tvFormat.setBackground(chip);

        holder.tvIndex.setText("#" + (position + 1));
        holder.tvIndex.setVisibility(showIndex ? View.VISIBLE : View.GONE);
        holder.tvValue.setText(item.value);
        holder.tvMeta.setText(item.source + " · " + item.contentType + " · " + BarcodeUtils.time(item.timestamp));
        holder.btnCopy.setOnClickListener(v -> BarcodeUtils.copy(context, item.value));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvFormat;
        final TextView tvIndex;
        final TextView tvValue;
        final TextView tvMeta;
        final ImageButton btnCopy;

        Holder(@NonNull View itemView) {
            super(itemView);
            tvFormat = itemView.findViewById(R.id.tv_format);
            tvIndex = itemView.findViewById(R.id.tv_index);
            tvValue = itemView.findViewById(R.id.tv_value);
            tvMeta = itemView.findViewById(R.id.tv_meta);
            btnCopy = itemView.findViewById(R.id.btn_copy);
        }
    }
}
