package com.qrscanf.app.ui;

import android.app.Dialog;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.qrscanf.app.R;
import com.qrscanf.app.model.BarcodeItem;
import com.qrscanf.app.util.BarcodeUtils;

import java.util.ArrayList;

public class ResultSheet extends BottomSheetDialogFragment {

    private static final String ARG_ITEMS = "items";
    private static final String ARG_TITLE = "title";
    private static final String ARG_SUBTITLE = "subtitle";
    private static final String ARG_ACTION = "action";
    private static final String ARG_SHOW_COPY_ALL = "copy_all";

    private Runnable dismissAction;
    private Runnable actionAction;

    public static ResultSheet newInstance(ArrayList<BarcodeItem> items, String title, String subtitle,
                                          String actionText, boolean showCopyAll) {
        ResultSheet sheet = new ResultSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ITEMS, items);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        args.putString(ARG_ACTION, actionText);
        args.putBoolean(ARG_SHOW_COPY_ALL, showCopyAll);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnDismissAction(Runnable action) {
        this.dismissAction = action;
    }

    /**
     * Runs only when the primary action button is pressed (not when the sheet is swiped / closed).
     */
    public void setOnAction(Runnable action) {
        this.actionAction = action;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_results, container, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        ArrayList<BarcodeItem> items = new ArrayList<>();
        if (args != null) {
            Object value = args.getSerializable(ARG_ITEMS);
            if (value instanceof ArrayList) {
                items = (ArrayList<BarcodeItem>) value;
            }
        }
        String title = args != null ? args.getString(ARG_TITLE, "识别结果") : "识别结果";
        String subtitle = args != null ? args.getString(ARG_SUBTITLE, "共 " + items.size() + " 条内容") : "";
        String action = args != null ? args.getString(ARG_ACTION, "继续扫描") : "继续扫描";
        boolean showCopyAll = args == null || args.getBoolean(ARG_SHOW_COPY_ALL, items.size() > 1);
        final ArrayList<BarcodeItem> itemList = items;

        ((android.widget.TextView) view.findViewById(R.id.tv_sheet_title)).setText(title);
        ((android.widget.TextView) view.findViewById(R.id.tv_sheet_subtitle)).setText(subtitle);

        RecyclerView recyclerView = view.findViewById(R.id.rv_results);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(new ResultAdapter(itemList, itemList.size() > 1));

        MaterialButton actionButton = view.findViewById(R.id.btn_sheet_action);
        actionButton.setText(action);
        actionButton.setOnClickListener(v -> {
            if (actionAction != null) {
                Runnable actionRunnable = actionAction;
                actionAction = null;
                actionRunnable.run();
            }
            dismiss();
        });

        MaterialButton copyAll = view.findViewById(R.id.btn_sheet_copy_all);
        copyAll.setVisibility(View.VISIBLE);
        copyAll.setText(itemList.size() > 1 ? "复制全部" : "复制内容");
        copyAll.setOnClickListener(v -> {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < itemList.size(); i++) {
                if (i > 0) {
                    builder.append('\n');
                }
                builder.append(itemList.get(i).value);
            }
            BarcodeUtils.copy(requireContext(), builder.toString());
        });

        view.findViewById(R.id.btn_sheet_close).setOnClickListener(v -> dismiss());
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog)) {
            return;
        }
        BottomSheetDialog sheetDialog = (BottomSheetDialog) dialog;
        Window window = sheetDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int count = 1;
            Bundle args = getArguments();
            if (args != null && args.getSerializable(ARG_ITEMS) instanceof ArrayList) {
                count = ((ArrayList<?>) args.getSerializable(ARG_ITEMS)).size();
            }
            int height = dp(220 + Math.min(count, 5) * 118);
            height = Math.min(height, (int) (metrics.heightPixels * 0.84f));
            height = Math.max(height, dp(320));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, height);
        }
        View bottomSheet = sheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (dismissAction != null) {
            Runnable action = dismissAction;
            dismissAction = null;
            action.run();
        }
    }

    private int dp(int value) {
        return Math.round(value * Resources.getSystem().getDisplayMetrics().density);
    }
}
