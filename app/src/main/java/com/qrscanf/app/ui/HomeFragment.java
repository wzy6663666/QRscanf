package com.qrscanf.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.qrscanf.app.ImageDecodeActivity;
import com.qrscanf.app.MainActivity;
import com.qrscanf.app.R;

public class HomeFragment extends Fragment {

    private ActivityResultLauncher<String> imagePicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && getContext() != null) {
                Intent intent = new Intent(requireContext(), ImageDecodeActivity.class);
                intent.setData(uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        activity.applyDefaultStatusBar();

        view.findViewById(R.id.card_single).setOnClickListener(v -> activity.openScan(false));
        view.findViewById(R.id.card_continuous).setOnClickListener(v -> activity.openScan(true));
        view.findViewById(R.id.card_image).setOnClickListener(v -> imagePicker.launch("image/*"));
        view.findViewById(R.id.card_generate).setOnClickListener(v -> activity.openGenerate());
        view.findViewById(R.id.card_scan_records).setOnClickListener(v -> activity.openRecords(0));
        view.findViewById(R.id.card_generate_records).setOnClickListener(v -> activity.openRecords(1));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).applyDefaultStatusBar();
        }
    }
}
