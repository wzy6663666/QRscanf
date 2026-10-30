package com.qrscanf.app.util;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.MultipleBarcodeReader;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.qrscanf.app.model.BarcodeItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ZXing fallback decoder. ML Kit is the primary engine, but ZXing is considerably more
 * forgiving for Data Matrix images at certain scales and serves as a reliable safety net.
 */
public final class ZxingDecoder {

    private static final List<BarcodeFormat> TWO_D_FORMATS = Arrays.asList(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417);

    private static final List<BarcodeFormat> ALL_FORMATS = Arrays.asList(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODABAR,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.ITF,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E);

    private ZxingDecoder() {
    }

    public static BarcodeItem decodeBitmapSingle(Bitmap bitmap, String source) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        try {
            RGBLuminanceSource luminanceSource = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(luminanceSource));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, ALL_FORMATS);
            Result result = new MultiFormatReader().decode(binaryBitmap, hints);
            if (result == null || result.getText() == null || result.getText().isEmpty()) {
                return null;
            }
            int format = toMlKitFormat(result.getBarcodeFormat());
            long now = System.currentTimeMillis();
            return new BarcodeItem(result.getText(), format, BarcodeUtils.formatLabel(format),
                    source, now, BarcodeUtils.contentType(result.getText()));
        } catch (Exception e) {
            return null;
        }
    }

    public static List<BarcodeItem> decodeBitmap(Bitmap bitmap, String source) {
        List<BarcodeItem> result = new ArrayList<>();
        if (bitmap == null) {
            return result;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        try {
            RGBLuminanceSource luminanceSource = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(luminanceSource));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, ALL_FORMATS);
            MultipleBarcodeReader reader = new GenericMultipleBarcodeReader(new MultiFormatReader());
            Result[] results = reader.decodeMultiple(binaryBitmap, hints);
            long now = System.currentTimeMillis();
            Set<String> seen = new HashSet<>();
            for (Result r : results) {
                if (r.getText() == null || r.getText().isEmpty()) {
                    continue;
                }
                int format = toMlKitFormat(r.getBarcodeFormat());
                String key = format + "|" + r.getText();
                if (!seen.add(key)) {
                    continue;
                }
                result.add(new BarcodeItem(r.getText(), format, BarcodeUtils.formatLabel(format),
                        source, now, BarcodeUtils.contentType(r.getText())));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /**
     * Fast fallback used by the camera analyzer; YUV luminance plane only, 2D formats only.
     */
    public static BarcodeItem decodeCameraFrame(byte[] yPlane, int width, int height, String source) {
        if (yPlane == null || width <= 0 || height <= 0) {
            return null;
        }
        BarcodeItem item = decodeCameraPlane(yPlane, width, height, source);
        if (item != null) {
            return item;
        }
        byte[] enhanced = enhanceContrast(yPlane);
        if (enhanced != null) {
            item = decodeCameraPlane(enhanced, width, height, source);
            if (item != null) {
                return item;
            }
        }
        // Small symbols in the middle of the frame benefit from a cropped, upscaled retry.
        int cropSize = Math.round(Math.min(width, height) * 0.62f);
        if (cropSize > 0) {
            CropFrame crop = cropAndUpscale(yPlane, width, height, cropSize, 2);
            if (crop != null) {
                item = decodeCameraPlane(crop.data, crop.width, crop.height, source);
                if (item != null) {
                    return item;
                }
                byte[] cropEnhanced = enhanceContrast(crop.data);
                if (cropEnhanced != null) {
                    item = decodeCameraPlane(cropEnhanced, crop.width, crop.height, source);
                }
            }
        }
        return item;
    }

    private static final class CropFrame {
        final byte[] data;
        final int width;
        final int height;

        CropFrame(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }

    private static CropFrame cropAndUpscale(byte[] yPlane, int width, int height,
                                            int cropSize, int scale) {
        int size = Math.min(cropSize, Math.min(width, height));
        int left = (width - size) / 2;
        int top = (height - size) / 2;
        int outSize = size * scale;
        byte[] out = new byte[outSize * outSize];
        for (int y = 0; y < outSize; y++) {
            int sourceY = top + y / scale;
            int sourceRow = sourceY * width;
            int outRow = y * outSize;
            for (int x = 0; x < outSize; x++) {
                out[outRow + x] = yPlane[sourceRow + left + x / scale];
            }
        }
        return new CropFrame(out, outSize, outSize);
    }

    private static BarcodeItem decodeCameraPlane(byte[] yPlane, int width, int height, String source) {
        try {
            PlanarYUVLuminanceSource luminanceSource = new PlanarYUVLuminanceSource(
                    yPlane, width, height, 0, 0, width, height, false);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(luminanceSource));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, TWO_D_FORMATS);
            Result result = new MultiFormatReader().decode(binaryBitmap, hints);
            if (result == null || result.getText() == null || result.getText().isEmpty()) {
                return null;
            }
            int format = toMlKitFormat(result.getBarcodeFormat());
            long now = System.currentTimeMillis();
            return new BarcodeItem(result.getText(), format, BarcodeUtils.formatLabel(format),
                    source, now, BarcodeUtils.contentType(result.getText()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Stretches the luminance histogram, which helps with low-contrast labels such as
     * grey modules on a blue background.
     */
    private static byte[] enhanceContrast(byte[] yPlane) {
        if (yPlane.length == 0) {
            return null;
        }
        int min = 255;
        int max = 0;
        for (int i = 0; i < yPlane.length; i += 4) {
            int value = yPlane[i] & 0xFF;
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }
        if (max - min < 18) {
            return null;
        }
        float scale = 255f / (max - min);
        byte[] out = new byte[yPlane.length];
        for (int i = 0; i < yPlane.length; i++) {
            int value = (int) (((yPlane[i] & 0xFF) - min) * scale);
            out[i] = (byte) Math.max(0, Math.min(255, value));
        }
        return out;
    }

    public static int toMlKitFormat(BarcodeFormat format) {
        if (format == null) {
            return Barcode.FORMAT_UNKNOWN;
        }
        switch (format) {
            case QR_CODE:
                return Barcode.FORMAT_QR_CODE;
            case DATA_MATRIX:
                return Barcode.FORMAT_DATA_MATRIX;
            case AZTEC:
                return Barcode.FORMAT_AZTEC;
            case PDF_417:
                return Barcode.FORMAT_PDF417;
            case CODE_128:
                return Barcode.FORMAT_CODE_128;
            case CODE_39:
                return Barcode.FORMAT_CODE_39;
            case CODE_93:
                return Barcode.FORMAT_CODE_93;
            case CODABAR:
                return Barcode.FORMAT_CODABAR;
            case EAN_13:
                return Barcode.FORMAT_EAN_13;
            case EAN_8:
                return Barcode.FORMAT_EAN_8;
            case ITF:
                return Barcode.FORMAT_ITF;
            case UPC_A:
                return Barcode.FORMAT_UPC_A;
            case UPC_E:
                return Barcode.FORMAT_UPC_E;
            default:
                return Barcode.FORMAT_UNKNOWN;
        }
    }
}
