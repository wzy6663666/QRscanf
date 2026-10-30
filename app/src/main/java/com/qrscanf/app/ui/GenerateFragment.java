package com.qrscanf.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.qrscanf.app.CaptureActivity;
import com.qrscanf.app.MainActivity;
import com.qrscanf.app.R;
import com.qrscanf.app.data.HistoryStore;
import com.qrscanf.app.generator.BarcodeGenerator;
import com.qrscanf.app.generator.GeneratorFormat;
import com.qrscanf.app.model.CloneResult;
import com.qrscanf.app.model.GenRecord;
import com.qrscanf.app.util.CodeCloneHelper;
import com.qrscanf.app.util.ImageLoader;
import com.qrscanf.app.util.ImageSaver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GenerateFragment extends Fragment {

    private static final String[] EC_KEYS = {"L", "M", "Q", "H"};
    private static final String[] ENCODINGS = {"UTF-8", "GBK", "ISO-8859-1"};
    private static final String[] MARGIN_LABELS = {"0 模块", "1 模块", "2 模块", "4 模块", "6 模块"};
    private static final int[] MARGIN_VALUES = {0, 1, 2, 4, 6};

    private MaterialAutoCompleteTextView formatDropdown;
    private MaterialAutoCompleteTextView ecDropdown;
    private MaterialAutoCompleteTextView versionDropdown;
    private MaterialAutoCompleteTextView maskDropdown;
    private MaterialAutoCompleteTextView encodingDropdown;
    private MaterialAutoCompleteTextView marginDropdown;
    private View ecLayout;
    private View qrAdvancedLayout;
    private TextInputLayout contentLayout;
    private TextInputEditText contentInput;
    private TextInputEditText fgColorInput;
    private TextInputEditText bgColorInput;
    private SeekBar sizeSeek;
    private TextView sizeValue;
    private ImageView preview;
    private View previewPlaceholder;
    private TextView previewBadge;
    private TextView previewInfo;
    private MaterialButton generateButton;
    private MaterialButton saveButton;
    private MaterialButton scanFillButton;
    private MaterialButton imageFillButton;

    private GeneratorFormat format = GeneratorFormat.QR_CODE;
    private String ecKey = "Q";
    private String charset = "UTF-8";
    private int qrVersion = 0;
    private int qrMask = -1;
    private int margin = 2;
    private Bitmap currentBitmap;
    private ExecutorService executor;
    private ActivityResultLauncher<String> storagePermission;
    private ActivityResultLauncher<Intent> captureLauncher;
    private ActivityResultLauncher<String> imageFillLauncher;
    private boolean generating;
    private boolean saving;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        storagePermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        saveCurrentBitmap();
                    } else {
                        Toast.makeText(requireContext(),
                                "需要存储权限才能保存到相册", Toast.LENGTH_LONG).show();
                    }
                });
        captureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK
                            && result.getData() != null) {
                        CloneResult cloneResult = (CloneResult) result.getData()
                                .getSerializableExtra(CaptureActivity.EXTRA_RESULT);
                        if (cloneResult != null) {
                            applyCloneResult(cloneResult);
                        }
                    }
                });
        imageFillLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) {
                        loadCloneFromImage(uri);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_generate, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        activity.applyDefaultStatusBar();

        TextView title = view.findViewById(R.id.tv_header_title);
        TextView subtitle = view.findViewById(R.id.tv_header_subtitle);
        title.setText("生成二维码 / 条形码");
        subtitle.setVisibility(View.VISIBLE);
        subtitle.setText("指定规格与内容，本地生成并保存");
        view.findViewById(R.id.btn_header_back).setVisibility(View.VISIBLE);
        view.findViewById(R.id.btn_header_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        formatDropdown = view.findViewById(R.id.act_format);
        ecDropdown = view.findViewById(R.id.act_ec);
        versionDropdown = view.findViewById(R.id.act_qr_version);
        maskDropdown = view.findViewById(R.id.act_qr_mask);
        encodingDropdown = view.findViewById(R.id.act_encoding);
        marginDropdown = view.findViewById(R.id.act_margin);
        ecLayout = view.findViewById(R.id.layout_ec);
        qrAdvancedLayout = view.findViewById(R.id.layout_qr_advanced);
        contentLayout = view.findViewById(R.id.til_content);
        contentInput = view.findViewById(R.id.et_content);
        fgColorInput = view.findViewById(R.id.et_fg_color);
        bgColorInput = view.findViewById(R.id.et_bg_color);
        sizeSeek = view.findViewById(R.id.seek_size);
        sizeValue = view.findViewById(R.id.tv_size_value);
        preview = view.findViewById(R.id.iv_preview);
        previewPlaceholder = view.findViewById(R.id.preview_placeholder);
        previewBadge = view.findViewById(R.id.tv_preview_badge);
        previewInfo = view.findViewById(R.id.tv_preview_info);
        generateButton = view.findViewById(R.id.btn_generate);
        saveButton = view.findViewById(R.id.btn_save);
        scanFillButton = view.findViewById(R.id.btn_scan_fill);
        imageFillButton = view.findViewById(R.id.btn_image_fill);

        String[] labels = GeneratorFormat.labels();
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(
                requireContext(), R.layout.item_dropdown, labels);
        formatDropdown.setAdapter(formatAdapter);
        formatDropdown.setText(labels[0], false);
        formatDropdown.setOnItemClickListener((parent, v, position, id) -> {
            format = GeneratorFormat.fromIndex(position);
            onFormatChanged();
        });

        String[] ecLabels = getResources().getStringArray(R.array.ec_levels);
        ArrayAdapter<String> ecAdapter = new ArrayAdapter<>(
                requireContext(), R.layout.item_dropdown, ecLabels);
        ecDropdown.setAdapter(ecAdapter);
        ecDropdown.setText(ecLabels[2], false);
        ecDropdown.setOnItemClickListener((parent, v, position, id) -> {
            if (position >= 0 && position < EC_KEYS.length) {
                ecKey = EC_KEYS[position];
            }
        });

        String[] versionLabels = new String[41];
        versionLabels[0] = "自动";
        for (int i = 1; i <= 40; i++) {
            versionLabels[i] = "版本 " + i;
        }
        versionDropdown.setAdapter(new ArrayAdapter<>(requireContext(),
                R.layout.item_dropdown, versionLabels));
        versionDropdown.setText(versionLabels[0], false);
        versionDropdown.setOnItemClickListener((parent, v, position, id) -> qrVersion = position);

        String[] maskLabels = new String[9];
        maskLabels[0] = "自动";
        for (int i = 1; i <= 8; i++) {
            maskLabels[i] = "掩码 " + (i - 1);
        }
        maskDropdown.setAdapter(new ArrayAdapter<>(requireContext(),
                R.layout.item_dropdown, maskLabels));
        maskDropdown.setText(maskLabels[0], false);
        maskDropdown.setOnItemClickListener((parent, v, position, id) -> qrMask = position - 1);

        encodingDropdown.setAdapter(new ArrayAdapter<>(requireContext(),
                R.layout.item_dropdown, ENCODINGS));
        encodingDropdown.setText(ENCODINGS[0], false);
        encodingDropdown.setOnItemClickListener((parent, v, position, id) -> {
            if (position >= 0 && position < ENCODINGS.length) {
                charset = ENCODINGS[position];
            }
        });

        marginDropdown.setAdapter(new ArrayAdapter<>(requireContext(),
                R.layout.item_dropdown, MARGIN_LABELS));
        marginDropdown.setText(MARGIN_LABELS[2], false);
        marginDropdown.setOnItemClickListener((parent, v, position, id) -> {
            if (position >= 0 && position < MARGIN_VALUES.length) {
                margin = MARGIN_VALUES[position];
            }
        });

        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSizeLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        generateButton.setOnClickListener(v -> generate());
        saveButton.setOnClickListener(v -> requestSave());
        scanFillButton.setOnClickListener(v -> captureLauncher.launch(
                new Intent(requireContext(), CaptureActivity.class)));
        imageFillButton.setOnClickListener(v -> imageFillLauncher.launch("image/*"));
        updateSizeLabel();
        onFormatChanged();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).applyDefaultStatusBar();
        }
    }

    private int currentSize() {
        return 256 + sizeSeek.getProgress() * 64;
    }

    private void updateSizeLabel() {
        int size = currentSize();
        if (format.oneDimensional) {
            int height = Math.max(220, size / 2);
            sizeValue.setText(size + " × " + height + " px");
        } else {
            sizeValue.setText(size + " × " + size + " px");
        }
    }

    private void onFormatChanged() {
        ecLayout.setVisibility(format.supportsErrorCorrection ? View.VISIBLE : View.GONE);
        qrAdvancedLayout.setVisibility(format == GeneratorFormat.QR_CODE ? View.VISIBLE : View.GONE);
        contentLayout.setHint(format.hint);
        previewBadge.setText(format.label.split("（")[0]);
        previewInfo.setText("当前规格：" + format.label + "。输入内容后点击「生成预览」。");
        updateSizeLabel();
        clearPreview();
    }

    private void clearPreview() {
        currentBitmap = null;
        preview.setVisibility(View.GONE);
        preview.setImageDrawable(null);
        previewPlaceholder.setVisibility(View.VISIBLE);
        previewBadge.setVisibility(View.GONE);
        saveButton.setEnabled(false);
        saveButton.setAlpha(0.5f);
    }

    private void loadCloneFromImage(Uri uri) {
        Toast.makeText(requireContext(), "正在识别图片中的码…", Toast.LENGTH_SHORT).show();
        android.content.Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            Bitmap source = ImageLoader.decodePreview(appContext, uri, 2400);
            CloneResult result = source == null ? null : CodeCloneHelper.extract(appContext, source);
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                if (result == null) {
                    Toast.makeText(requireContext(),
                            "图片中未识别到可生成的码", Toast.LENGTH_LONG).show();
                } else {
                    applyCloneResult(result);
                }
            });
        });
    }

    private void applyCloneResult(CloneResult clone) {
        GeneratorFormat target = GeneratorFormat.fromMlKitFormat(clone.mlkitFormat);
        if (target == null) {
            contentInput.setText(clone.content);
            Toast.makeText(requireContext(),
                    "该码制暂不支持生成，已复制内容", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = GeneratorFormat.labels();
        format = target;
        formatDropdown.setText(labels[GeneratorFormat.indexOf(target)], false);
        onFormatChanged();
        contentInput.setText(clone.content);
        contentInput.setSelection(contentInput.getText() == null ? 0 : contentInput.getText().length());
        contentLayout.setError(null);

        if (format == GeneratorFormat.QR_CODE) {
            if (clone.ecLevel != null) {
                for (int i = 0; i < EC_KEYS.length; i++) {
                    if (EC_KEYS[i].equals(clone.ecLevel)) {
                        ecKey = EC_KEYS[i];
                        String[] ecLabels = getResources().getStringArray(R.array.ec_levels);
                        ecDropdown.setText(ecLabels[i], false);
                        break;
                    }
                }
            }
            if (clone.qrVersion > 0) {
                qrVersion = clone.qrVersion;
                versionDropdown.setText("版本 " + qrVersion, false);
            } else {
                qrVersion = 0;
                versionDropdown.setText("自动", false);
            }
            if (clone.qrMask >= 0) {
                qrMask = clone.qrMask;
                maskDropdown.setText("掩码 " + qrMask, false);
            } else {
                qrMask = -1;
                maskDropdown.setText("自动", false);
            }
        }

        int targetSize = Math.max(256, Math.min(1024, clone.width));
        int progress = Math.max(0, Math.min(12,
                Math.round((targetSize - 256) / 64f)));
        sizeSeek.setProgress(progress);
        updateSizeLabel();

        Bitmap crop = BitmapFactory.decodeFile(clone.cropPath);
        if (crop == null) {
            Toast.makeText(requireContext(), "扫描原图读取失败，请重新扫描", Toast.LENGTH_LONG).show();
            return;
        }
        currentBitmap = crop;
        preview.setImageBitmap(crop);
        preview.setVisibility(View.VISIBLE);
        previewPlaceholder.setVisibility(View.GONE);
        previewBadge.setVisibility(View.VISIBLE);
        previewBadge.setText(format.label.split("（")[0] + " · 扫描复刻");
        StringBuilder info = new StringBuilder("已填充扫描内容与格式");
        if (format == GeneratorFormat.QR_CODE) {
            if (clone.qrVersion > 0) {
                info.append(" · 版本 ").append(clone.qrVersion);
            }
            if (clone.qrMask >= 0) {
                info.append(" · 掩码 ").append(clone.qrMask);
            }
            if (clone.ecLevel != null) {
                info.append(" · 容错 ").append(clone.ecLevel);
            }
        }
        info.append("。当前预览为扫描原图像素级复刻，可直接保存；点击「生成预览」将按上方参数重新生成。");
        previewInfo.setText(info.toString());
        saveButton.setEnabled(true);
        saveButton.setAlpha(1f);
        Toast.makeText(requireContext(), "已填充扫描内容、码制与参数", Toast.LENGTH_SHORT).show();
    }

    private void generate() {
        if (generating) {
            return;
        }
        String content = contentInput.getText() == null ? "" : contentInput.getText().toString().trim();
        if (content.isEmpty()) {
            contentLayout.setError("请输入要生成的内容");
            contentInput.requestFocus();
            return;
        }
        contentLayout.setError(null);
        int foregroundColor;
        int backgroundColor;
        try {
            foregroundColor = parseHexColor(fgColorInput.getText() == null
                    ? "" : fgColorInput.getText().toString(), 0xFF000000);
            backgroundColor = parseHexColor(bgColorInput.getText() == null
                    ? "" : bgColorInput.getText().toString(), 0xFFFFFFFF);
        } catch (IllegalArgumentException e) {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        hideKeyboard();
        final int size = currentSize();
        final int fg = foregroundColor;
        final int bg = backgroundColor;
        final int version = qrVersion;
        final int mask = qrMask;
        final int quietZone = margin;
        final String encoding = charset;
        generating = true;
        generateButton.setEnabled(false);
        generateButton.setText("生成中…");

        executor.execute(() -> {
            Bitmap bitmap = null;
            String error = null;
            try {
                bitmap = BarcodeGenerator.generate(content, format, size, ecKey,
                        encoding, version, mask, quietZone, fg, bg);
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = "生成失败：" + (e.getMessage() == null ? "内容或规格不受支持" : e.getMessage());
            }
            final Bitmap finalBitmap = bitmap;
            final String finalError = error;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                generating = false;
                generateButton.setEnabled(true);
                generateButton.setText("生成预览");
                if (finalError != null || finalBitmap == null) {
                    if (finalError != null && (finalError.contains("需要") || finalError.contains("仅支持")
                            || finalError.contains("版本") || finalError.contains("too big")
                            || finalError.contains("Data too big"))) {
                        contentLayout.setError(finalError);
                    } else {
                        Toast.makeText(requireContext(), finalError != null ? finalError : "生成失败",
                                Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                currentBitmap = finalBitmap;
                preview.setImageBitmap(finalBitmap);
                preview.setVisibility(View.VISIBLE);
                previewPlaceholder.setVisibility(View.GONE);
                previewBadge.setVisibility(View.VISIBLE);
                String info = format.label;
                if (format.supportsErrorCorrection) {
                    info += " · 容错 " + ecKey;
                }
                if (format == GeneratorFormat.QR_CODE) {
                    if (version > 0) {
                        info += " · 版本 " + version;
                    }
                    if (mask >= 0) {
                        info += " · 掩码 " + mask;
                    }
                }
                info += " · 留白 " + quietZone;
                info += " · " + finalBitmap.getWidth() + " × " + finalBitmap.getHeight() + " px";
                previewInfo.setText(info + "。可保存到相册，生成记录已自动保存。");
                saveButton.setEnabled(true);
                saveButton.setAlpha(1f);
                HistoryStore.addGen(requireContext(), new GenRecord(
                        content, format.label, format.key, System.currentTimeMillis(),
                        currentSize(), ecKey));
            });
        });
    }

    private int parseHexColor(String value, int fallback) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return fallback;
        }
        if (!text.startsWith("#")) {
            text = "#" + text;
        }
        if (!text.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("颜色格式应为 #RRGGBB，例如 #000000");
        }
        return android.graphics.Color.parseColor(text);
    }

    private void requestSave() {
        if (currentBitmap == null || saving) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        saveCurrentBitmap();
    }

    private void saveCurrentBitmap() {
        if (currentBitmap == null || saving) {
            return;
        }
        saving = true;
        saveButton.setEnabled(false);
        saveButton.setText("保存中…");
        Bitmap bitmap = currentBitmap;
        executor.execute(() -> {
            String error = null;
            try {
                ImageSaver.savePng(requireContext(), bitmap);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String finalError = error;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                saving = false;
                saveButton.setEnabled(true);
                saveButton.setText("保存到相册");
                if (finalError == null) {
                    Toast.makeText(requireContext(),
                            "已保存到相册：Pictures/QRscanf", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(),
                            "保存失败：" + finalError, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void hideKeyboard() {
        View focus = requireActivity().getCurrentFocus();
        if (focus != null) {
            InputMethodManager manager = (InputMethodManager)
                    requireActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }
}
