package com.qrscanf.app.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraCharacteristics;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Size;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.qrscanf.app.ImageDecodeActivity;
import com.qrscanf.app.CaptureActivity;
import com.qrscanf.app.MainActivity;
import com.qrscanf.app.R;
import com.qrscanf.app.data.AppPrefs;
import com.qrscanf.app.data.HistoryStore;
import com.qrscanf.app.model.BarcodeItem;
import com.qrscanf.app.model.CloneResult;
import com.qrscanf.app.ui.ScannerOverlayView;
import com.qrscanf.app.util.BarcodeImageDecoder;
import com.qrscanf.app.util.BarcodeUtils;
import com.qrscanf.app.util.ZxingDecoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScanFragment extends Fragment {

    private static final String ARG_CONTINUOUS = "continuous";
    private static final long FRAME_INTERVAL_MS = 110;

    private boolean continuous;
    private PreviewView previewView;
    private ScannerOverlayView overlay;
    private ImageButton torchButton;
    private ImageButton switchCameraButton;
    private TextView zoomLabel;
    private TextView hintText;
    private TextView tvStatus;
    private TextView tvClear;
    private MaterialButton btnShowResults;
    private MaterialButton btnPickImage;
    private MaterialButton btnSnap;
    private View permissionPanel;
    private TextView permissionTitle;
    private TextView permissionDesc;
    private MaterialButton btnPermission;

    private ExecutorService cameraExecutor;
    private ExecutorService imageExecutor;
    private ExecutorService zxingExecutor;
    private ExecutorService invertedExecutor;
    private final AtomicBoolean zxingBusy = new AtomicBoolean(false);
    private final AtomicBoolean invertedBusy = new AtomicBoolean(false);
    private long lastZxingAt;
    private long lastInvertedAt;
    private BarcodeScanner scanner;
    private BarcodeScanner invertedScanner;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ImageAnalysis imageAnalysis;
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private volatile boolean paused;
    private long lastAnalyzeAt;
    private boolean cameraBound;
    private boolean bindingCamera;
    private boolean torchOn;
    private ToneGenerator toneGenerator;
    private boolean imageDecoding;

    private final List<LensOption> lensOptions = new ArrayList<>();
    private int lensIndex;
    private float currentZoom = 1f;
    private float minZoom = 1f;
    private float maxZoom = 1f;
    private float zoomGestureBase = 1f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector tapDetector;

    private final List<BarcodeItem> results = new ArrayList<>();
    private final Set<String> seenKeys = new HashSet<>();
    private String lastKey;
    private long lastKeyAt;
    private boolean resultSheetShowing;

    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<String> imagePicker;
    private ActivityResultLauncher<Intent> snapshotLauncher;

    public static ScanFragment newInstance(boolean continuous) {
        ScanFragment fragment = new ScanFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_CONTINUOUS, continuous);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        continuous = getArguments() != null && getArguments().getBoolean(ARG_CONTINUOUS, false);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        hidePermissionPanel();
                        startCamera();
                    } else {
                        showPermissionPanel(true);
                    }
                });

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) {
                        if (continuous) {
                            decodeImageBatch(uri);
                        } else {
                            openImageDecode(uri);
                        }
                    }
                });

        snapshotLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != android.app.Activity.RESULT_OK
                            || result.getData() == null) {
                        return;
                    }
                    CloneResult clone = (CloneResult) result.getData()
                            .getSerializableExtra(CaptureActivity.EXTRA_RESULT);
                    if (clone == null || clone.content.isEmpty()) {
                        return;
                    }
                    BarcodeItem item = new BarcodeItem(clone.content, clone.mlkitFormat,
                            BarcodeUtils.formatLabel(clone.mlkitFormat), "拍照识别",
                            System.currentTimeMillis(), BarcodeUtils.contentType(clone.content));
                    paused = false;
                    acceptDetection(item);
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        previewView = view.findViewById(R.id.preview_view);
        overlay = view.findViewById(R.id.scanner_overlay);
        torchButton = view.findViewById(R.id.btn_torch);
        switchCameraButton = view.findViewById(R.id.btn_switch_camera);
        zoomLabel = view.findViewById(R.id.tv_zoom);
        hintText = view.findViewById(R.id.tv_scan_hint);
        tvStatus = view.findViewById(R.id.tv_scan_status);
        tvClear = view.findViewById(R.id.tv_clear);
        btnShowResults = view.findViewById(R.id.btn_show_results);
        btnPickImage = view.findViewById(R.id.btn_pick_image);
        btnSnap = view.findViewById(R.id.btn_snap);
        permissionPanel = view.findViewById(R.id.permission_panel);
        permissionTitle = view.findViewById(R.id.tv_permission_title);
        permissionDesc = view.findViewById(R.id.tv_permission_desc);
        btnPermission = view.findViewById(R.id.btn_permission);

        TextView title = view.findViewById(R.id.tv_scan_title);
        TextView subtitle = view.findViewById(R.id.tv_scan_subtitle);
        title.setText(continuous ? R.string.continuous_scan : R.string.single_scan);
        subtitle.setText(continuous ? "连续识别多个码，完成后统一查看" : "对准二维码自动识别");
        tvStatus.setText(continuous ? "将二维码 / 条形码放入取景框，可连续识别" : "将二维码 / Data Matrix 放入取景框");

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        torchButton.setOnClickListener(v -> toggleTorch());
        switchCameraButton.setOnClickListener(v -> showLensPicker());
        zoomLabel.setOnClickListener(v -> cycleZoom());
        setupPreviewGestures();
        btnPickImage.setOnClickListener(v -> imagePicker.launch("image/*"));
        btnSnap.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CaptureActivity.class);
            intent.putExtra("scan_mode", true);
            snapshotLauncher.launch(intent);
        });
        btnShowResults.setOnClickListener(v -> showResultsSheet());
        tvClear.setOnClickListener(v -> clearResults());
        btnPermission.setOnClickListener(v -> onPermissionButtonClicked());
        btnShowResults.setVisibility(View.GONE);
        tvClear.setVisibility(View.GONE);
        if (continuous) {
            btnSnap.setVisibility(View.GONE);
            btnPickImage.setText("批量图片识别");
        }

        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().build());
        invertedScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().build());
        cameraExecutor = Executors.newSingleThreadExecutor();
        imageExecutor = Executors.newSingleThreadExecutor();
        zxingExecutor = Executors.newSingleThreadExecutor();
        invertedExecutor = Executors.newSingleThreadExecutor();

        MainActivity activity = (MainActivity) requireActivity();
        activity.setStatusBar(android.graphics.Color.BLACK, false);

        if (hasCameraPermission()) {
            startCamera();
        } else {
            showPermissionPanel(false);
            requestCameraPermission();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setStatusBar(android.graphics.Color.BLACK, false);
        }
        paused = resultSheetShowing;
        if (overlay != null) {
            overlay.setScanning(!resultSheetShowing);
        }
        if (hasCameraPermission() && !cameraBound && getView() != null) {
            startCamera();
        }
    }

    @Override
    public void onPause() {
        paused = true;
        if (overlay != null) {
            overlay.setScanning(false);
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
        if (scanner != null) {
            scanner.close();
        }
        if (invertedScanner != null) {
            invertedScanner.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (imageExecutor != null) {
            imageExecutor.shutdownNow();
        }
        if (zxingExecutor != null) {
            zxingExecutor.shutdownNow();
        }
        if (invertedExecutor != null) {
            invertedExecutor.shutdownNow();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        cameraBound = false;
        bindingCamera = false;
        cameraProvider = null;
        camera = null;
        imageAnalysis = null;
        lensOptions.clear();
        super.onDestroyView();
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void onPermissionButtonClicked() {
        if (hasCameraPermission()) {
            hidePermissionPanel();
            startCamera();
            return;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            requestCameraPermission();
        } else {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", requireContext().getPackageName(), null));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(requireContext(), "请在设置中允许相机权限", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                requestCameraPermission();
            }
        }
    }

    private void showPermissionPanel(boolean denied) {
        if (permissionPanel == null) {
            return;
        }
        permissionPanel.setVisibility(View.VISIBLE);
        if (overlay != null) {
            overlay.setScanning(false);
        }
        if (denied) {
            permissionTitle.setText("相机权限未开启");
            permissionDesc.setText("扫码需要使用相机权限。请点击下方按钮授权，或前往系统设置中开启。");
            btnPermission.setText("去授权");
        } else {
            permissionTitle.setText(R.string.permission_title);
            permissionDesc.setText(R.string.permission_desc);
            btnPermission.setText(R.string.grant_permission);
        }
    }

    private void hidePermissionPanel() {
        if (permissionPanel != null) {
            permissionPanel.setVisibility(View.GONE);
        }
    }

    private void startCamera() {
        if (bindingCamera || cameraBound || cameraExecutor == null || previewView == null
                || getContext() == null) {
            return;
        }
        bindingCamera = true;
        android.content.Context context = requireContext().getApplicationContext();
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            bindingCamera = false;
            if (!isAdded() || getView() == null) {
                return;
            }
            try {
                cameraProvider = future.get();
                enumerateLenses();
                if (lensOptions.isEmpty()) {
                    showCameraError("没有检测到可用相机");
                    return;
                }
                bindSelectedLens(false);
            } catch (Exception e) {
                showCameraError("无法启动相机：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void enumerateLenses() {
        lensOptions.clear();
        List<CameraInfo> infos = cameraProvider.getAvailableCameraInfos();
        List<LensOption> primaryBacks = new ArrayList<>();
        List<LensOption> physicalBacks = new ArrayList<>();
        List<LensOption> fronts = new ArrayList<>();
        for (CameraInfo info : infos) {
            String id = null;
            int facing = CameraSelector.LENS_FACING_BACK;
            float focal = 0f;
            try {
                Camera2CameraInfo camera2Info = Camera2CameraInfo.from(info);
                id = camera2Info.getCameraId();
                Integer facingValue = camera2Info.getCameraCharacteristic(
                        CameraCharacteristics.LENS_FACING);
                if (facingValue != null) {
                    facing = facingValue;
                }
                float[] focalLengths = camera2Info.getCameraCharacteristic(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                if (focalLengths != null && focalLengths.length > 0) {
                    focal = focalLengths[0];
                }
            } catch (Exception ignored) {
            }
            final String lensId = id;
            CameraSelector selector = selectorForLogical(lensId, facing);
            LensOption option = new LensOption(lensId, facing, focal, selector);
            if (facing == CameraSelector.LENS_FACING_FRONT) {
                option.label = "前置";
                fronts.add(option);
            } else {
                option.label = "后置主摄";
                primaryBacks.add(option);

                // Some phones (e.g. OnePlus) expose ultra-wide/telephoto as physical
                // cameras of one logical rear camera. CameraX can bind to those by
                // combining the logical camera filter with setPhysicalCameraId().
                try {
                    for (CameraInfo physical : info.getPhysicalCameraInfos()) {
                        Camera2CameraInfo physicalInfo = Camera2CameraInfo.from(physical);
                        String physicalId = physicalInfo.getCameraId();
                        if (physicalId == null || physicalId.equals(lensId)) {
                            continue;
                        }
                        float physicalFocal = 0f;
                        float[] values = physicalInfo.getCameraCharacteristic(
                                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                        if (values != null && values.length > 0) {
                            physicalFocal = values[0];
                        }
                        CameraSelector.Builder builder = new CameraSelector.Builder();
                        builder.requireLensFacing(facing);
                        if (lensId != null) {
                            builder.addCameraFilter(cameraInfos -> {
                                List<CameraInfo> filtered = new ArrayList<>();
                                for (CameraInfo candidate : cameraInfos) {
                                    try {
                                        if (lensId.equals(Camera2CameraInfo.from(candidate).getCameraId())) {
                                            filtered.add(candidate);
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                                return filtered;
                            });
                        }
                        builder.setPhysicalCameraId(physicalId);
                        LensOption physicalOption = new LensOption(physicalId, facing,
                                physicalFocal, builder.build());
                        physicalOption.physicalId = physicalId;
                        physicalOption.logicalId = lensId;
                        physicalBacks.add(physicalOption);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        Collections.sort(primaryBacks, Comparator.comparingDouble(o -> o.focalLength));
        Collections.sort(physicalBacks, Comparator.comparingDouble(o -> o.focalLength));
        Collections.sort(fronts, Comparator.comparingDouble(o -> o.focalLength));
        List<LensOption> allBacks = new ArrayList<>();
        allBacks.addAll(primaryBacks);
        allBacks.addAll(physicalBacks);
        Collections.sort(allBacks, Comparator.comparingDouble(o -> o.focalLength));

        // Different logical cameras can expose the same physical lens. Keep one option
        // per focal length. Prefer the logical camera: some devices advertise physical
        // camera IDs but still render the logical camera's preview stream.
        java.util.TreeMap<Integer, LensOption> uniqueBacks = new java.util.TreeMap<>();
        for (LensOption option : allBacks) {
            int key = Math.round(option.focalLength * 10f);
            LensOption existing = uniqueBacks.get(key);
            if (existing == null
                    || (existing.physicalId != null && option.physicalId == null)) {
                uniqueBacks.put(key, option);
            }
        }
        List<LensOption> backs = new ArrayList<>(uniqueBacks.values());
        List<LensOption> labelledBacks = new ArrayList<>();
        for (int i = 0; i < backs.size(); i++) {
            LensOption option = backs.get(i);
            option.label = backLensName(i, backs.size(), option.focalLength);
            labelledBacks.add(option);
        }
        for (int i = 0; i < fronts.size(); i++) {
            fronts.get(i).label = fronts.size() == 1 ? "前置" : "前置 " + (i + 1);
        }
        lensOptions.addAll(labelledBacks);
        lensOptions.addAll(fronts);

        int defaultIndex = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < backs.size(); i++) {
            float distance = Math.abs(backs.get(i).focalLength - 6.0f);
            if (distance < bestDistance) {
                bestDistance = distance;
                defaultIndex = i;
            }
        }
        lensIndex = defaultIndex;
        switchCameraButton.setVisibility(lensOptions.size() > 1 ? View.VISIBLE : View.GONE);
        for (LensOption option : lensOptions) {
            android.util.Log.d("QRscanfLens", "option label=" + option.label
                    + " logical=" + option.id + " physical=" + option.physicalId
                    + " focal=" + option.focalLength);
        }
    }

    private String backLensName(int index, int count, float focal) {
        String focalText = focal > 0f ? String.format(Locale.CHINA, " %.1fmm", focal) : "";
        if (count == 1) {
            return "后置主摄" + focalText;
        }
        if (index == 0) {
            return "后置超广角" + focalText;
        }
        if (index == count - 1 && count >= 3) {
            return "后置长焦" + focalText;
        }
        return "后置主摄" + focalText;
    }

    private CameraSelector selectorForLogical(String id, int facing) {
        CameraSelector.Builder builder = new CameraSelector.Builder();
        builder.requireLensFacing(facing);
        if (id != null) {
            builder.addCameraFilter(cameraInfos -> {
                List<CameraInfo> filtered = new ArrayList<>();
                for (CameraInfo candidate : cameraInfos) {
                    try {
                        if (id.equals(Camera2CameraInfo.from(candidate).getCameraId())) {
                            filtered.add(candidate);
                        }
                    } catch (Exception ignored) {
                    }
                }
                return filtered;
            });
        }
        return builder.build();
    }

    private String physicalLensLabel(int index, int count) {
        if (count == 1) {
            return "后置物理镜头";
        }
        if (index == 0) {
            return "后置超广角";
        }
        if (index == count - 1) {
            return "后置长焦";
        }
        return "后置主摄（物理）";
    }

    private boolean bindSelectedLens(boolean announce) {
        if (cameraProvider == null || lensOptions.isEmpty() || getView() == null) {
            return false;
        }
        LensOption lens = lensOptions.get(Math.max(0, Math.min(lensIndex, lensOptions.size() - 1)));
        android.util.Log.d("QRscanfLens", "bind requested label=" + lens.label
                + " logical=" + lens.id + " physical=" + lens.physicalId);
        try {
            if (imageAnalysis != null) {
                imageAnalysis.clearAnalyzer();
            }
            cameraProvider.unbindAll();
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(1920, 1080))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);
            camera = cameraProvider.bindToLifecycle(getViewLifecycleOwner(),
                    lens.selector, preview, imageAnalysis);
            cameraBound = true;
            try {
                android.util.Log.d("QRscanfLens", "bind success active="
                        + Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId());
            } catch (Exception ignored) {
            }
            torchOn = false;
            torchButton.setBackgroundResource(R.drawable.bg_torch_off);
            torchButton.setColorFilter(android.graphics.Color.WHITE);
            torchButton.setVisibility(
                    camera.getCameraInfo().hasFlashUnit() ? View.VISIBLE : View.GONE);
            hidePermissionPanel();
            updateLensHint();
            currentZoom = 1f;
            observeZoom();
            if (announce) {
                Toast.makeText(requireContext(),
                        "已切换：" + lens.label, Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (Exception e) {
            cameraBound = false;
            android.util.Log.e("QRscanfLens", "bind failed: " + e, e);
            if (lens.facing == CameraSelector.LENS_FACING_BACK) {
                // Physical cameras can be advertised but not openable for a normal
                // preview/analysis session on some devices. Fall back to the logical rear.
                try {
                    cameraProvider.unbindAll();
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());
                    imageAnalysis = new ImageAnalysis.Builder()
                            .setTargetResolution(new Size(1920, 1080))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();
                    imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);
                    camera = cameraProvider.bindToLifecycle(getViewLifecycleOwner(),
                            CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
                    cameraBound = true;
                    torchButton.setVisibility(
                            camera.getCameraInfo().hasFlashUnit() ? View.VISIBLE : View.GONE);
                    hidePermissionPanel();
                    updateLensHint();
                    observeZoom();
                    Toast.makeText(requireContext(),
                            "该镜头暂时不可用，已切回后置主摄", Toast.LENGTH_SHORT).show();
                    return true;
                } catch (Exception ignored) {
                }
            }
            showCameraError("镜头切换失败：" + (e.getMessage() == null ? "请重试" : e.getMessage()));
            return false;
        }
    }

    private void observeZoom() {
        if (camera == null) {
            return;
        }
        camera.getCameraInfo().getZoomState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            ZoomState zoomState = state;
            minZoom = zoomState.getMinZoomRatio();
            maxZoom = zoomState.getMaxZoomRatio();
            currentZoom = zoomState.getZoomRatio();
            updateZoomLabel();
        });
    }

    private void updateZoomLabel() {
        if (zoomLabel != null) {
            zoomLabel.setText(String.format(Locale.CHINA, "%.1f×", currentZoom));
        }
    }

    private void updateLensHint() {
        if (hintText != null && !lensOptions.isEmpty()) {
            String label = lensOptions.get(Math.min(lensIndex, lensOptions.size() - 1)).label;
            hintText.setText("当前：" + label + " · 双指缩放 · 点击画面对焦");
        }
    }

    private void switchToNextLens() {
        if (lensOptions.size() <= 1) {
            Toast.makeText(requireContext(), "当前设备未检测到其他可用镜头", Toast.LENGTH_SHORT).show();
            return;
        }
        lensIndex = (lensIndex + 1) % lensOptions.size();
        bindSelectedLens(true);
    }

    private void showLensPicker() {
        if (lensOptions.size() <= 1) {
            Toast.makeText(requireContext(), "当前设备仅检测到一个可用镜头", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[lensOptions.size()];
        for (int i = 0; i < lensOptions.size(); i++) {
            labels[i] = lensOptions.get(i).label
                    + (i == lensIndex ? "（当前）" : "");
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择镜头")
                .setItems(labels, (dialog, which) -> {
                    lensIndex = which;
                    bindSelectedLens(true);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void cycleZoom() {
        if (currentZoom >= maxZoom - 0.05f) {
            setZoom(1f);
            return;
        }
        float[] steps = {2f, 3f, 5f, 8f};
        float next = maxZoom;
        for (float step : steps) {
            if (step > currentZoom + 0.15f) {
                next = Math.min(step, maxZoom);
                break;
            }
        }
        if (next <= currentZoom + 0.05f) {
            next = 1f;
        }
        setZoom(next);
    }

    private void setZoom(float ratio) {
        if (camera == null) {
            return;
        }
        float target = Math.max(minZoom, Math.min(maxZoom, ratio));
        camera.getCameraControl().setZoomRatio(target);
        currentZoom = target;
        updateZoomLabel();
    }

    private void setupPreviewGestures() {
        if (previewView == null) {
            return;
        }
        scaleDetector = new ScaleGestureDetector(requireContext(),
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
        tapDetector = new GestureDetector(requireContext(),
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
            if (scaleDetector != null) {
                scaleDetector.onTouchEvent(event);
            }
            if (tapDetector != null) {
                tapDetector.onTouchEvent(event);
            }
            return true;
        });
    }

    private void focusAt(float x, float y) {
        if (camera == null || previewView == null) {
            return;
        }
        MeteringPointFactory factory = previewView.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(
                point, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build();
        camera.getCameraControl().startFocusAndMetering(action);
        if (overlay != null) {
            overlay.showFocus(x, y);
        }
    }

    private void showCameraError(String message) {
        cameraBound = false;
        if (permissionPanel == null) {
            return;
        }
        permissionPanel.setVisibility(View.VISIBLE);
        permissionTitle.setText("相机启动失败");
        permissionDesc.setText(message);
        btnPermission.setText("重试");
        btnPermission.setOnClickListener(v -> {
            cameraBound = false;
            bindingCamera = false;
            startCamera();
        });
    }


    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeFrame(ImageProxy imageProxy) {
        long now = SystemClock.elapsedRealtime();
        if (paused || scanner == null || now - lastAnalyzeAt < FRAME_INTERVAL_MS
                || !processing.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }
        lastAnalyzeAt = now;
        scheduleZxingFallback(imageProxy);
        scheduleInvertedMlKit(imageProxy);
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            processing.set(false);
            imageProxy.close();
            return;
        }
        InputImage inputImage = InputImage.fromMediaImage(mediaImage,
                imageProxy.getImageInfo().getRotationDegrees());
        scanner.process(inputImage)
                .addOnSuccessListener(ContextCompat.getMainExecutor(requireContext()),
                        barcodes -> {
                            if (!paused && isAdded()) {
                                handleBarcodes(barcodes);
                            }
                        })
                .addOnFailureListener(ignored -> {
                })
                .addOnCompleteListener(ContextCompat.getMainExecutor(requireContext()), task -> {
                    processing.set(false);
                    imageProxy.close();
                });
    }

    private void handleBarcodes(List<Barcode> barcodes) {
        if (barcodes == null || barcodes.isEmpty()) {
            return;
        }
        for (Barcode barcode : barcodes) {
            String value = barcode.getRawValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            int format = barcode.getFormat();
            BarcodeItem item = new BarcodeItem(value, format, BarcodeUtils.formatLabel(format),
                    continuous ? "连续扫码" : "单次扫码", System.currentTimeMillis(),
                    BarcodeUtils.contentType(value));
            acceptDetection(item);
            if (paused) {
                break;
            }
        }
    }

    private void acceptDetection(BarcodeItem item) {
        if (paused || !isAdded() || item == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = item.key();
        if (key.equals(lastKey) && now - lastKeyAt < 1300) {
            return;
        }
        lastKey = key;
        lastKeyAt = now;

        if (!continuous) {
            HistoryStore.addScan(requireContext(), item);
            if (AppPrefs.isAutoCopy(requireContext())) {
                BarcodeUtils.copy(requireContext(), item.value);
            }
            beep();
            showSingleResult(item);
            return;
        }

        if (AppPrefs.isDedup(requireContext()) && seenKeys.contains(key)) {
            return;
        }
        seenKeys.add(key);
        results.add(item);
        HistoryStore.addScan(requireContext(), item);
        beep();
        updateContinuousUi();
    }

    private void scheduleInvertedMlKit(ImageProxy imageProxy) {
        long now = SystemClock.elapsedRealtime();
        if (paused || invertedScanner == null || invertedExecutor == null
                || now - lastInvertedAt < 800
                || !invertedBusy.compareAndSet(false, true)) {
            return;
        }
        lastInvertedAt = now;
        try {
            int width = imageProxy.getWidth();
            int height = imageProxy.getHeight();
            ImageProxy.PlaneProxy plane = imageProxy.getPlanes()[0];
            if (plane.getPixelStride() != 1) {
                invertedBusy.set(false);
                return;
            }
            int rowStride = plane.getRowStride();
            java.nio.ByteBuffer buffer = plane.getBuffer();
            int cropSize = Math.round(Math.min(width, height) * 0.72f);
            int left = (width - cropSize) / 2;
            int top = (height - cropSize) / 2;
            int scale = 2;
            int outSize = cropSize * scale;

            int minValue = 255;
            int maxValue = 0;
            for (int y = 0; y < cropSize; y += 3) {
                int row = (top + y) * rowStride;
                for (int x = 0; x < cropSize; x += 3) {
                    int value = buffer.get(row + left + x) & 0xFF;
                    if (value < minValue) {
                        minValue = value;
                    }
                    if (value > maxValue) {
                        maxValue = value;
                    }
                }
            }
            int range = Math.max(8, maxValue - minValue);

            int[] pixels = new int[outSize * outSize];
            for (int y = 0; y < outSize; y++) {
                int sourceRow = (top + y / scale) * rowStride + left;
                int outRow = y * outSize;
                for (int x = 0; x < outSize; x++) {
                    int value = buffer.get(sourceRow + x / scale) & 0xFF;
                    int stretched = ((value - minValue) * 255) / range;
                    if (stretched < 0) {
                        stretched = 0;
                    } else if (stretched > 255) {
                        stretched = 255;
                    }
                    int inverted = 255 - stretched;
                    pixels[outRow + x] = 0xFF000000
                            | (inverted << 16) | (inverted << 8) | inverted;
                }
            }
            final Bitmap invertedBitmap = Bitmap.createBitmap(outSize, outSize,
                    Bitmap.Config.ARGB_8888);
            invertedBitmap.setPixels(pixels, 0, outSize, 0, 0, outSize, outSize);
            final int rotation = imageProxy.getImageInfo().getRotationDegrees();
            final String source = continuous ? "连续扫码" : "单次扫码";
            InputImage inputImage = InputImage.fromBitmap(invertedBitmap, rotation);
            invertedScanner.process(inputImage)
                    .addOnSuccessListener(ContextCompat.getMainExecutor(requireContext()),
                            barcodes -> {
                                if (!paused && isAdded() && barcodes != null) {
                                    for (Barcode barcode : barcodes) {
                                        String value = barcode.getRawValue();
                                        if (value == null || value.isEmpty()) {
                                            continue;
                                        }
                                        int format = barcode.getFormat();
                                        acceptDetection(new BarcodeItem(value, format,
                                                BarcodeUtils.formatLabel(format), source,
                                                System.currentTimeMillis(),
                                                BarcodeUtils.contentType(value)));
                                        if (paused) {
                                            break;
                                        }
                                    }
                                }
                            })
                    .addOnFailureListener(ignored -> {
                    })
                    .addOnCompleteListener(ContextCompat.getMainExecutor(requireContext()),
                            task -> {
                                invertedBitmap.recycle();
                                invertedBusy.set(false);
                            });
        } catch (Exception e) {
            invertedBusy.set(false);
        }
    }


    private void scheduleZxingFallback(ImageProxy imageProxy) {
        long now = SystemClock.elapsedRealtime();
        if (paused || zxingExecutor == null || now - lastZxingAt < 650
                || !zxingBusy.compareAndSet(false, true)) {
            return;
        }
        lastZxingAt = now;
        try {
            int width = imageProxy.getWidth();
            int height = imageProxy.getHeight();
            ImageProxy.PlaneProxy plane = imageProxy.getPlanes()[0];
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();
            java.nio.ByteBuffer buffer = plane.getBuffer();
            int originalPosition = buffer.position();
            byte[] yPlane = new byte[width * height];
            if (pixelStride == 1) {
                for (int row = 0; row < height; row++) {
                    buffer.position(row * rowStride);
                    buffer.get(yPlane, row * width, width);
                }
                buffer.position(originalPosition);
            } else {
                zxingBusy.set(false);
                return;
            }
            final String source = continuous ? "连续扫码" : "单次扫码";
            zxingExecutor.execute(() -> {
                BarcodeItem item = ZxingDecoder.decodeCameraFrame(yPlane, width, height, source);
                if (item == null) {
                    zxingBusy.set(false);
                    return;
                }
                if (!isAdded()) {
                    zxingBusy.set(false);
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    zxingBusy.set(false);
                    if (!paused && isAdded()) {
                        acceptDetection(item);
                    }
                });
            });
        } catch (Exception e) {
            zxingBusy.set(false);
        }
    }

    private void showSingleResult(BarcodeItem item) {
        if (!isAdded() || getParentFragmentManager().isStateSaved()) {
            return;
        }
        paused = true;
        resultSheetShowing = true;
        if (overlay != null) {
            overlay.setScanning(false);
        }
        tvStatus.setText("识别成功 · 正在显示内容");
        ArrayList<BarcodeItem> items = new ArrayList<>();
        items.add(item);
        ResultSheet sheet = ResultSheet.newInstance(items, "扫码结果",
                item.formatLabel + " · " + item.contentType, "继续扫描", false);
        sheet.setOnDismissAction(() -> {
            resultSheetShowing = false;
            if (!isAdded()) {
                return;
            }
            paused = false;
            if (overlay != null) {
                overlay.setScanning(true);
            }
            if (tvStatus != null) {
                tvStatus.setText("将二维码 / Data Matrix 放入取景框");
            }
        });
        sheet.show(getParentFragmentManager(), "single_result");
    }

    private void showResultsSheet() {
        if (results.isEmpty()) {
            Toast.makeText(requireContext(), "还没有识别到内容", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAdded() || getParentFragmentManager().isStateSaved()) {
            return;
        }
        paused = true;
        resultSheetShowing = true;
        if (overlay != null) {
            overlay.setScanning(false);
        }
        ArrayList<BarcodeItem> copy = new ArrayList<>(results);
        StringBuilder formats = new StringBuilder();
        Set<String> unique = new HashSet<>();
        for (BarcodeItem item : copy) {
            if (unique.add(item.formatLabel)) {
                if (formats.length() > 0) {
                    formats.append(" / ");
                }
                formats.append(item.formatLabel);
            }
        }
        ResultSheet sheet = ResultSheet.newInstance(copy, "连续扫码结果",
                "共 " + copy.size() + " 个 · " + formats, "继续扫描", true);
        sheet.setOnDismissAction(() -> {
            resultSheetShowing = false;
            if (!isAdded()) {
                return;
            }
            paused = false;
            if (overlay != null) {
                overlay.setScanning(true);
            }
        });
        sheet.show(getParentFragmentManager(), "batch_result");
    }

    private void updateContinuousUi() {
        int count = results.size();
        tvStatus.setText(count == 0
                ? "将二维码 / 条形码放入取景框，可连续识别"
                : "已识别 " + count + " 个，可继续扫描或查看结果");
        btnShowResults.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        tvClear.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        btnShowResults.setText("查看结果 (" + count + ")");
        if (count > 0) {
            btnPickImage.setVisibility(View.VISIBLE);
        }
    }

    private void clearResults() {
        results.clear();
        seenKeys.clear();
        lastKey = null;
        updateContinuousUi();
        Toast.makeText(requireContext(), "已清空本次扫描结果", Toast.LENGTH_SHORT).show();
    }

    private void openImageDecode(Uri uri) {
        Intent intent = new Intent(requireContext(), ImageDecodeActivity.class);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void decodeImageBatch(Uri uri) {
        if (imageDecoding || imageExecutor == null) {
            return;
        }
        imageDecoding = true;
        btnPickImage.setEnabled(false);
        tvStatus.setText("正在识别图片中的二维码 / 条形码…");
        final android.content.Context appContext = requireContext().getApplicationContext();
        imageExecutor.execute(() -> {
            List<BarcodeItem> found = null;
            String error = null;
            try {
                found = BarcodeImageDecoder.decode(appContext, uri, "连续扫码");
            } catch (Exception e) {
                error = e.getMessage();
            }
            final List<BarcodeItem> decoded = found;
            final String finalError = error;
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                imageDecoding = false;
                btnPickImage.setEnabled(true);
                if (finalError != null) {
                    tvStatus.setText("图片识别失败，请重试");
                    Toast.makeText(requireContext(), "识别失败：" + finalError,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                int added = 0;
                if (decoded != null) {
                    for (BarcodeItem item : decoded) {
                        String key = item.mlkitFormat + "|" + item.value;
                        if (AppPrefs.isDedup(requireContext()) && seenKeys.contains(key)) {
                            continue;
                        }
                        seenKeys.add(key);
                        results.add(item);
                        HistoryStore.addScan(requireContext(), item);
                        added++;
                    }
                }
                if (added > 0) {
                    beep();
                }
                updateContinuousUi();
                if (results.isEmpty()) {
                    Toast.makeText(requireContext(),
                            "图片中未识别到二维码或条形码", Toast.LENGTH_LONG).show();
                    tvStatus.setText("未识别到内容，可继续扫描或换一张图片");
                } else {
                    Toast.makeText(requireContext(),
                            "从图片中识别到 " + added + " 个新码", Toast.LENGTH_SHORT).show();
                    showResultsSheet();
                }
            });
        });
    }

    private void toggleTorch() {
        if (camera == null) {
            return;
        }
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
        torchButton.setBackgroundResource(torchOn ? R.drawable.bg_torch_on : R.drawable.bg_torch_off);
        torchButton.setColorFilter(torchOn
                ? android.graphics.Color.parseColor("#3A2A00")
                : android.graphics.Color.WHITE);
    }

    private void beep() {
        if (!AppPrefs.isBeep(requireContext())) {
            return;
        }
        try {
            if (toneGenerator == null) {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
            }
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 90);
        } catch (Exception ignored) {
        }
    }

    private static final class LensOption {
        final String id;
        final int facing;
        final float focalLength;
        final CameraSelector selector;
        String label;
        String physicalId;
        String logicalId;

        LensOption(String id, int facing, float focalLength, CameraSelector selector) {
            this.id = id;
            this.facing = facing;
            this.focalLength = focalLength;
            this.selector = selector;
        }
    }
}
