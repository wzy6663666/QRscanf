package com.qrscanf.app.ui;

import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qrscanf.app.MainActivity;
import com.qrscanf.app.R;
import com.qrscanf.app.data.AppPrefs;

public class SettingsFragment extends Fragment {

    private MaterialSwitch autoCopySwitch;
    private MaterialSwitch beepSwitch;
    private MaterialSwitch dedupSwitch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        activity.applyDefaultStatusBar();

        TextView title = view.findViewById(R.id.tv_header_title);
        TextView subtitle = view.findViewById(R.id.tv_header_subtitle);
        title.setText("设置");
        subtitle.setVisibility(View.VISIBLE);
        subtitle.setText("识别偏好与关于");

        autoCopySwitch = view.findViewById(R.id.sw_auto_copy);
        beepSwitch = view.findViewById(R.id.sw_beep);
        dedupSwitch = view.findViewById(R.id.sw_dedup);

        autoCopySwitch.setChecked(AppPrefs.isAutoCopy(requireContext()));
        beepSwitch.setChecked(AppPrefs.isBeep(requireContext()));
        dedupSwitch.setChecked(AppPrefs.isDedup(requireContext()));

        autoCopySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppPrefs.setAutoCopy(requireContext(), isChecked));
        beepSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppPrefs.setBeep(requireContext(), isChecked));
        dedupSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppPrefs.setDedup(requireContext(), isChecked));

        TextView version = view.findViewById(R.id.tv_version);
        try {
            PackageInfo info = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            version.setText("版本 " + info.versionName);
        } catch (Exception e) {
            version.setText("版本 1.0.0");
        }

        view.findViewById(R.id.btn_reset).setOnClickListener(v -> confirmReset());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).applyDefaultStatusBar();
        }
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("恢复默认设置？")
                .setMessage("仅重置识别偏好，不会删除扫码或生成记录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (dialog, which) -> {
                    AppPrefs.reset(requireContext());
                    autoCopySwitch.setChecked(AppPrefs.isAutoCopy(requireContext()));
                    beepSwitch.setChecked(AppPrefs.isBeep(requireContext()));
                    dedupSwitch.setChecked(AppPrefs.isDedup(requireContext()));
                    Toast.makeText(requireContext(), "已恢复默认设置", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
