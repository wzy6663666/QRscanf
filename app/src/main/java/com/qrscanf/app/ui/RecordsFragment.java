package com.qrscanf.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qrscanf.app.MainActivity;
import com.qrscanf.app.R;
import com.qrscanf.app.data.HistoryStore;
import com.qrscanf.app.model.BarcodeItem;
import com.qrscanf.app.model.GenRecord;
import com.qrscanf.app.util.BarcodeUtils;

import java.util.ArrayList;
import java.util.List;

public class RecordsFragment extends Fragment implements HistoryAdapter.Listener {

    private static final String ARG_TAB = "tab";

    private int tab;
    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private final List<Object> entries = new ArrayList<>();
    private TextView summary;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyDesc;
    private MaterialButtonToggleGroup toggleGroup;

    public static RecordsFragment newInstance(int tab) {
        RecordsFragment fragment = new RecordsFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TAB, tab);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_records, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tab = getArguments() != null ? getArguments().getInt(ARG_TAB, 0) : 0;
        MainActivity activity = (MainActivity) requireActivity();
        activity.applyDefaultStatusBar();

        TextView title = view.findViewById(R.id.tv_header_title);
        TextView subtitle = view.findViewById(R.id.tv_header_subtitle);
        title.setText("记录");
        subtitle.setVisibility(View.VISIBLE);
        subtitle.setText("扫码记录与生成记录");
        View action = view.findViewById(R.id.btn_header_action);
        action.setVisibility(View.VISIBLE);
        ((android.widget.ImageButton) action).setImageResource(R.drawable.ic_trash);
        action.setOnClickListener(v -> confirmClear());

        summary = view.findViewById(R.id.tv_records_summary);
        emptyState = view.findViewById(R.id.empty_state);
        emptyTitle = view.findViewById(R.id.tv_empty_title);
        emptyDesc = view.findViewById(R.id.tv_empty_desc);
        recyclerView = view.findViewById(R.id.rv_records);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter(entries, this);
        recyclerView.setAdapter(adapter);

        toggleGroup = view.findViewById(R.id.toggle_type);
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int newTab = checkedId == R.id.btn_tab_gen ? 1 : 0;
            if (newTab != tab) {
                tab = newTab;
                load();
            }
        });
        toggleGroup.check(tab == 1 ? R.id.btn_tab_gen : R.id.btn_tab_scans);
        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).applyDefaultStatusBar();
        }
        load();
    }

    private void load() {
        if (recyclerView == null) {
            return;
        }
        entries.clear();
        if (tab == 0) {
            entries.addAll(HistoryStore.getScans(requireContext()));
            emptyTitle.setText("暂无扫码记录");
            emptyDesc.setText("去扫一个二维码试试吧");
        } else {
            entries.addAll(HistoryStore.getGens(requireContext()));
            emptyTitle.setText("暂无生成记录");
            emptyDesc.setText("去生成一张二维码或条形码吧");
        }
        adapter.notifyDataSetChanged();
        summary.setText("共 " + entries.size() + " 条记录");
        boolean empty = entries.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void confirmClear() {
        String label = tab == 0 ? "扫码记录" : "生成记录";
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("清空" + label + "？")
                .setMessage("清空后将无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    if (tab == 0) {
                        HistoryStore.clearScans(requireContext());
                    } else {
                        HistoryStore.clearGens(requireContext());
                    }
                    load();
                })
                .show();
    }

    @Override
    public void onScanClick(BarcodeItem item) {
        ArrayList<BarcodeItem> list = new ArrayList<>();
        list.add(item);
        ResultSheet sheet = ResultSheet.newInstance(list, "扫码记录",
                item.source + " · " + BarcodeUtils.time(item.timestamp), "关闭", false);
        sheet.show(getParentFragmentManager(), "history_scan");
    }

    @Override
    public void onGenClick(GenRecord record) {
        String message = "规格：" + record.formatLabel
                + (record.errorCorrection.isEmpty() ? "" : " · 容错 " + record.errorCorrection)
                + "\n尺寸：" + record.size + " px"
                + "\n时间：" + BarcodeUtils.time(record.timestamp)
                + "\n\n内容：\n" + record.content;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("生成记录")
                .setMessage(message)
                .setNegativeButton("关闭", null)
                .setPositiveButton("复制内容", (dialog, which) ->
                        BarcodeUtils.copy(requireContext(), record.content))
                .show();
    }

    @Override
    public void onCopy(String text) {
        BarcodeUtils.copy(requireContext(), text);
    }
}
