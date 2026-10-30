package com.qrscanf.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.qrscanf.app.model.BarcodeItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class BarcodeImageDecoder {

    private static final int[] NORMAL_TARGETS = {1280, 1024, 1600, 800};
    private static final int[] FALLBACK_TARGETS = {1280, 1024};

    private BarcodeImageDecoder() {
    }

    /**
     * Decodes every barcode readable in the given image. Must be called off the main thread.
     * <p>
     * ML Kit is tried at several image scales (its Data Matrix detector is scale-sensitive),
     * then on inverted and contrast-enhanced versions, then ZXing is used as a fallback.
     * This covers dark-background / light-module and low-contrast labels.
     */
    public static List<BarcodeItem> decode(Context context, Uri uri, String source) throws Exception {
        Bitmap original = ImageLoader.decodePreview(context, uri, 2000);
        if (original == null) {
            throw new IllegalArgumentException("无法读取图片");
        }
        original = ImageLoader.flattenOnWhite(original);

        List<BarcodeItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        BarcodeScanner scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().build());
        try {
            runMlKitPasses(scanner, original, source, NORMAL_TARGETS, result, seen);

            if (result.isEmpty()) {
                Bitmap inverted = ImageLoader.invert(original);
                if (inverted != null) {
                    runMlKitPasses(scanner, inverted, source, FALLBACK_TARGETS, result, seen);
                    inverted.recycle();
                }
            }
            if (result.isEmpty()) {
                Bitmap enhanced = ImageLoader.enhanceContrast(original);
                if (enhanced != null) {
                    runMlKitPasses(scanner, enhanced, source, FALLBACK_TARGETS, result, seen);
                    enhanced.recycle();
                }
            }
        } finally {
            scanner.close();
        }

        // ZXing fallback (also tries inverted symbols internally).
        if (result.isEmpty()) {
            BarcodeItem fallback = ZxingDecoder.decodeBitmapSingle(original, source);
            if (fallback != null) {
                result.add(fallback);
            }
        }
        if (result.isEmpty()) {
            Bitmap enhanced = ImageLoader.enhanceContrast(original);
            if (enhanced != null) {
                BarcodeItem fallback = ZxingDecoder.decodeBitmapSingle(enhanced, source);
                if (fallback != null) {
                    result.add(fallback);
                }
                enhanced.recycle();
            }
        }
        original.recycle();
        return result;
    }

    private static void runMlKitPasses(BarcodeScanner scanner, Bitmap bitmap, String source,
                                       int[] targets, List<BarcodeItem> result, Set<String> seen) {
        for (int target : targets) {
            Bitmap scaled = ImageLoader.scaleToMaxWithMargin(bitmap, target);
            if (scaled == null) {
                continue;
            }
            try {
                List<Barcode> barcodes = Tasks.await(
                        scanner.process(InputImage.fromBitmap(scaled, 0)), 20, TimeUnit.SECONDS);
                addBarcodes(barcodes, result, seen, source);
            } catch (Exception ignored) {
            } finally {
                scaled.recycle();
            }
            if (!result.isEmpty()) {
                return;
            }
        }
    }

    private static void addBarcodes(List<Barcode> barcodes, List<BarcodeItem> result,
                                    Set<String> seen, String source) {
        if (barcodes == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Barcode barcode : barcodes) {
            String value = barcode.getRawValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            int format = barcode.getFormat();
            String key = format + "|" + value;
            if (!seen.add(key)) {
                continue;
            }
            result.add(new BarcodeItem(value, format, BarcodeUtils.formatLabel(format),
                    source, now, BarcodeUtils.contentType(value)));
        }
    }
}
