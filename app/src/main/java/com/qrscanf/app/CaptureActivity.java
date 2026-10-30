package com.qrscanf.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.qrscanf.app.model.CloneResult;
import com.qrscanf.app.util.CodeCloneHelper;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Still-photo capture used by the generator's "scan to fill" action.
 * Taking a full-resolution photo gives much more detail than a live preview frame,
 * which is important for small / low-contrast Data Matrix labels.
 */
public class CaptureActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT = "clone_result";

    private PreviewView previewView;
    private ImageButton torchButton;
    private MaterialButton captureButton;
    private TextView statusText;
    private TextView hintText;
    private ProgressBar progressBar;
    private View permissionPanel;
    private TextView permissionTitle;
    private TextView permissionDesc;
    private MaterialButton permissionButton;

    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ExecutorService ioExecutor;
    private ActivityResultLauncher<String> permissionLauncher;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector tapDetector;
    private float currentZoom = 1f;
    private float minZoom = 1f;
    private float maxZoom = 1f;
    private float zoomGestureBase = 1f;
    private boolean torchOn;
    private boolean capturing;
    private boolean cameraBound;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);
        setStatusBar();
        cameraExecutor = Executors.newSingleThreadExecutor();
        ioExecutor = Executors.newSingleThreadExecutor();

        previewView = findViewById(R.id.preview_view);
        torchButton = findViewById(R.id.btn_torch);
        captureButton = findViewById(R.id.btn_capture);
        statusText = findViewById(R.id.tv_capture_status);
        hintText = findViewById(R.id.tv_capture_hint);
        progressBar = findViewById(R.id.progress);
        permissionPanel = findViewById(R.id.permission_panel);
        permissionTitle = findViewById(R.id.tv_permission_title);
        permissionDesc = findViewById(R.id.tv_permission_desc);
        permissionButton = findViewById(R.id.btn_permission);
        TextView titleView = findViewById(R.id.tv_capture_title);
        TextView subtitleView = findViewById(R.id.tv_capture_subtitle);
        if (getIntent().getBooleanExtra("scan_mode", false)) {
            titleView.setText("拍照识别");
            subtitleView.setText("拍下二维码 / 条码后立即识别");
            captureButton.setText("拍照识别");
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        torchButton.setOnClickListener(v -> toggleTorch());
        captureButton.setOnClickListener(v -> takePhoto());
        permissionButton.setOnClickListener(v -> requestPermissionOrOpenSettings());

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        permissionPanel.setVisibility(View.GONE);
                        startCamera();
                    } else {
                        showPermissionPanel(true);
                    }
                });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        setupGestures();
        if (hasCameraPermission()) {
            startCamera();
        } else {
            showPermissionPanel(false);
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void setStatusBar() {
        getWindow().setStatusBarColor(Color.BLACK);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionOrOpenSettings() {
        if (hasCameraPermission()) {
            permissionPanel.setVisibility(View.GONE);
            startCamera();
            return;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            } catch (Exception e) {
                permissionLauncher.launch(Manifest.permission.CAMERA);
            }
        }
    }

    private void showPermissionPanel(boolean denied) {
        permissionPanel.setVisibility(View.VISIBLE);
        permissionTitle.setText(denied ? "相机权限未开启" : getString(R.string.permission_title));
        permissionDesc.setText(denied
                ? "拍摄识别需要使用相机权限，请授权或前往系统设置开启。"
                : getString(R.string.permission_desc));
        permissionButton.setText(getString(R.string.grant_permission));
    }

    private void startCamera() {
        if (cameraBound || previewView == null) {
            return;
        }
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(getApplicationContext());
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetRotation(previewView.getDisplay().getRotation())
                        .build();
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                cameraBound = true;
                permissionPanel.setVisibility(View.GONE);
                torchButton.setVisibility(
                        camera.getCameraInfo().hasFlashUnit() ? View.VISIBLE : View.GONE);
                observeZoom();
            } catch (Exception e) {
                permissionPanel.setVisibility(View.VISIBLE);
                permissionTitle.setText("相机启动失败");
                permissionDesc.setText("无法启动相机：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
                permissionButton.setText("重试");
                permissionButton.setOnClickListener(v -> {
                    cameraBound = false;
                    startCamera();
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void observeZoom() {
        if (camera == null) {
            return;
        }
        camera.getCameraInfo().getZoomState().observe(this, state -> {
            if (state == null) {
                return;
            }
            minZoom = state.getMinZoomRatio();
            maxZoom = state.getMaxZoomRatio();
            currentZoom = state.getZoomRatio();
            updateHint();
        });
    }

    private void updateHint() {
        hintText.setText(String.format(Locale.CHINA,
                "双指缩放 · 点击对焦 · 当前 %.1f×", currentZoom));
    }

    private void setZoom(float ratio) {
        if (camera == null) {
            return;
        }
        float target = Math.max(minZoom, Math.min(maxZoom, ratio));
        camera.getCameraControl().setZoomRatio(target);
        currentZoom = target;
        updateHint();
    }

    private void setupGestures() {
        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                        zoomGestureBase = currentZoom;
                        return true;
                    }

                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        setZoom(zoomGestureBase * detector.getScaleFactor());
                        return true;
                    }
                });
        tapDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(@NonNull MotionEvent e) {
                        focusAt(e.getX(), e.getY());
                        return true;
                    }
                });
        previewView.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            tapDetector.onTouchEvent(event);
            return true;
        });
    }

    private void focusAt(float x, float y) {
        if (camera == null) {
            return;
        }
        MeteringPointFactory factory = previewView.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(
                point, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build();
        camera.getCameraControl().startFocusAndMetering(action);
    }

    private void toggleTorch() {
        if (camera == null) {
            return;
        }
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
        torchButton.setBackgroundResource(torchOn ? R.drawable.bg_torch_on : R.drawable.bg_torch_off);
        torchButton.setColorFilter(torchOn
                ? Color.parseColor("#3A2A00") : Color.WHITE);
    }

    private void takePhoto() {
        if (capturing || imageCapture == null) {
            return;
        }
        capturing = true;
        captureButton.setEnabled(false);
        captureButton.setText("识别中…");
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("正在拍摄并识别…");

        File file = new File(getCacheDir(), "scan_capture_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(options, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        ioExecutor.execute(() -> {
                            Bitmap bitmap = loadBitmap(file);
                            CloneResult result = CodeCloneHelper.extract(CaptureActivity.this, bitmap);
                            if (bitmap != null) {
                                // CodeCloneHelper may have recycled the bitmap it received.
                            }
                            runOnUiThread(() -> onCaptureFinished(file, result));
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() -> {
                            resetCaptureUi();
                            Toast.makeText(CaptureActivity.this,
                                    "拍照失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void onCaptureFinished(File file, CloneResult result) {
        file.delete();
        resetCaptureUi();
        if (result == null) {
            statusText.setText("未识别到二维码 / 条形码");
            Toast.makeText(this, "未识别到码，请靠近或点击画面对焦后重试", Toast.LENGTH_LONG).show();
            return;
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT, result);
        setResult(RESULT_OK, data);
        finish();
    }

    private void resetCaptureUi() {
        capturing = false;
        progressBar.setVisibility(View.GONE);
        captureButton.setEnabled(true);
        captureButton.setText("拍照识别");
        statusText.setText("将二维码 / 条形码完整放入画面");
    }

    @Nullable
    private Bitmap loadBitmap(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 3000) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) {
                return null;
            }
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap;
            }
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
        super.onDestroy();
    }
}
