package com.qrscanf.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.qrscanf.app.model.BarcodeItem;
import com.qrscanf.app.model.CloneResult;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Extracts a scannable code from a still image and saves a cropped, pixel-exact copy
 * for the generator's "scan to clone" preview.
 */
public final class CodeCloneHelper {

    private CodeCloneHelper() {
    }

    public static CloneResult extract(Context context, Bitmap source) {
        if (source == null) {
            return null;
        }
        Bitmap base = ImageLoader.flattenOnWhite(source);
        if (base.getWidth() > 2000 || base.getHeight() > 2000) {
            float scale = 2000f / Math.max(base.getWidth(), base.getHeight());
            Bitmap scaled = Bitmap.createScaledBitmap(base,
                    Math.max(1, Math.round(base.getWidth() * scale)),
                    Math.max(1, Math.round(base.getHeight() * scale)), true);
            base = scaled;
        }

        BarcodeScanner scanner = BarcodeScanning.getClient(
                new BarcodeScannerOptions.Builder().build());
        Barcode found = null;
        try {
            found = scan(scanner, base);
            if (found == null) {
                Bitmap inverted = ImageLoader.invert(base);
                if (inverted != null) {
                    found = scan(scanner, inverted);
                    inverted.recycle();
                }
            }
            if (found == null) {
                Bitmap enhanced = ImageLoader.enhanceContrast(base);
                if (enhanced != null) {
                    found = scan(scanner, enhanced);
                    enhanced.recycle();
                }
            }
        } finally {
            scanner.close();
        }

        if (found == null || found.getRawValue() == null) {
            // Last chance: ZXing can read some low-contrast/inverted symbols ML Kit misses.
            BarcodeItem fallback = ZxingDecoder.decodeBitmapSingle(base, "扫描填充");
            if (fallback == null) {
                base.recycle();
                return null;
            }
            Bitmap crop = centralCrop(base);
            int width = crop.getWidth();
            int height = crop.getHeight();
            String path = saveCrop(context, crop);
            if (crop != base) {
                crop.recycle();
            }
            base.recycle();
            if (path == null) {
                return null;
            }
            return new CloneResult(fallback.value, fallback.mlkitFormat, path,
                    width, height, 0, -1, null);
        }

        Bitmap crop = cropAround(base, found.getBoundingBox());
        QrParamExtractor.QrInfo qr = null;
        if (found.getFormat() == Barcode.FORMAT_QR_CODE) {
            qr = QrParamExtractor.extract(crop);
        }
        int width = crop.getWidth();
        int height = crop.getHeight();
        String path = saveCrop(context, crop);
        if (crop != base) {
            crop.recycle();
        }
        base.recycle();
        if (path == null) {
            return null;
        }
        return new CloneResult(found.getRawValue(), found.getFormat(), path, width, height,
                qr == null ? 0 : qr.version, qr == null ? -1 : qr.mask,
                qr == null ? null : qr.ecLevel);
    }

    private static Barcode scan(BarcodeScanner scanner, Bitmap bitmap) {
        try {
            List<Barcode> barcodes = Tasks.await(
                    scanner.process(InputImage.fromBitmap(bitmap, 0)), 20, TimeUnit.SECONDS);
            for (Barcode barcode : barcodes) {
                if (barcode.getRawValue() != null && !barcode.getRawValue().isEmpty()) {
                    return barcode;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Bitmap cropAround(Bitmap bitmap, Rect box) {
        if (box == null) {
            return centralCrop(bitmap);
        }
        int pad = Math.max(12, Math.round(Math.max(box.width(), box.height()) * 0.08f));
        int left = Math.max(0, box.left - pad);
        int top = Math.max(0, box.top - pad);
        int right = Math.min(bitmap.getWidth(), box.right + pad);
        int bottom = Math.min(bitmap.getHeight(), box.bottom + pad);
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return centralCrop(bitmap);
        }
        return Bitmap.createBitmap(bitmap, left, top, width, height);
    }

    private static Bitmap centralCrop(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int left = (bitmap.getWidth() - size) / 2;
        int top = (bitmap.getHeight() - size) / 2;
        return Bitmap.createBitmap(bitmap, left, top, size, size);
    }

    private static String saveCrop(Context context, Bitmap bitmap) {
        try {
            File dir = new File(context.getCacheDir(), "scan_clone");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File file = new File(dir, "clone_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    return null;
                }
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}
