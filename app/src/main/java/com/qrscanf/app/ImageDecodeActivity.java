package com.qrscanf.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.qrscanf.app.data.HistoryStore;
import com.qrscanf.app.model.BarcodeItem;
import com.qrscanf.app.ui.ResultSheet;
import com.qrscanf.app.util.BarcodeImageDecoder;
import com.qrscanf.app.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageDecodeActivity extends AppCompatActivity {

    private ImageView imageView;
    private ImageView placeholder;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvDetail;
    private MaterialButton btnPick;
    private ExecutorService executor;
    private ActivityResultLauncher<String> picker;
    private boolean decoding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_decode);
        setStatusBar();
        executor = Executors.newSingleThreadExecutor();

        imageView = findViewById(R.id.iv_image);
        placeholder = findViewById(R.id.iv_placeholder);
        progressBar = findViewById(R.id.progress);
        tvStatus = findViewById(R.id.tv_status);
        tvDetail = findViewById(R.id.tv_detail);
        btnPick = findViewById(R.id.btn_pick_another);

        TextView title = findViewById(R.id.tv_header_title);
        TextView subtitle = findViewById(R.id.tv_header_subtitle);
        title.setText("图片识别");
        subtitle.setVisibility(View.VISIBLE);
        subtitle.setText("从图片中识别二维码 / 条形码");
        findViewById(R.id.btn_header_back).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_header_back).setOnClickListener(v -> finish());

        picker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                startDecode(uri);
            }
        });
        btnPick.setOnClickListener(v -> {
            if (!decoding) {
                picker.launch("image/*");
            }
        });

        Uri uri = extractUri(getIntent());
        if (uri != null) {
            startDecode(uri);
        } else {
            progressBar.setVisibility(View.GONE);
            tvStatus.setText("选择一张图片开始识别");
            tvDetail.setText("支持 QR Code、Data Matrix 及常用条形码");
        }
    }

    private void setStatusBar() {
        getWindow().setStatusBarColor(Color.parseColor("#5B6CF9"));
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
    }

    @Nullable
    private Uri extractUri(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            }
            //noinspection deprecation
            return intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        return intent.getData();
    }

    private void startDecode(Uri uri) {
        if (decoding) {
            return;
        }
        decoding = true;
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("正在识别图片中的二维码…");
        tvDetail.setText("本地离线识别，不会上传图片");
        imageView.setVisibility(View.GONE);
        placeholder.setVisibility(View.VISIBLE);
        btnPick.setEnabled(false);

        executor.execute(() -> {
            android.graphics.Bitmap preview = ImageLoader.decodePreview(this, uri, 1200);
            List<BarcodeItem> items = null;
            String error = null;
            try {
                items = BarcodeImageDecoder.decode(this, uri, "图片识别");
            } catch (Exception e) {
                error = e.getMessage();
            }
            final List<BarcodeItem> finalItems = items;
            final String finalError = error;
            runOnUiThread(() -> {
                decoding = false;
                btnPick.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                if (preview != null) {
                    imageView.setImageBitmap(preview);
                    imageView.setVisibility(View.VISIBLE);
                    placeholder.setVisibility(View.GONE);
                }
                if (finalError != null) {
                    tvStatus.setText("图片读取失败");
                    tvDetail.setText("请重新选择一张图片（" + finalError + "）");
                    return;
                }
                if (finalItems == null || finalItems.isEmpty()) {
                    tvStatus.setText("未识别到二维码或条形码");
                    tvDetail.setText("请尝试更清晰、更完整、对比度更高的图片");
                    return;
                }
                HistoryStore.addScans(this, finalItems);
                tvStatus.setText("识别完成");
                tvDetail.setText("共找到 " + finalItems.size() + " 个码 · 点击「完成」返回");
                if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) {
                    return;
                }
                ArrayList<BarcodeItem> list = new ArrayList<>(finalItems);
                StringBuilder formats = new StringBuilder();
                java.util.Set<String> unique = new java.util.HashSet<>();
                for (BarcodeItem item : list) {
                    if (unique.add(item.formatLabel)) {
                        if (formats.length() > 0) {
                            formats.append(" / ");
                        }
                        formats.append(item.formatLabel);
                    }
                }
                ResultSheet sheet = ResultSheet.newInstance(list, "图片识别结果",
                        "共 " + list.size() + " 个 · " + formats, "完成", list.size() > 1);
                sheet.setOnAction(this::finish);
                sheet.show(getSupportFragmentManager(), "image_result");
            });
        });
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }
}
