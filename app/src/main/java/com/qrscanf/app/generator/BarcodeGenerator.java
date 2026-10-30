package com.qrscanf.app.generator;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.encoder.SymbolShapeHint;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

public final class BarcodeGenerator {

    private BarcodeGenerator() {
    }

    public static Bitmap generate(String content, GeneratorFormat format, int size, String ecKey)
            throws Exception {
        return generate(content, format, size, ecKey, "UTF-8", 0, -1, 2,
                0xFF000000, 0xFFFFFFFF);
    }

    /**
     * @param qrVersion QR version 1..40, or 0 for automatic
     * @param qrMask    QR mask 0..7, or -1 for automatic
     * @param margin    quiet zone in modules
     */
    public static Bitmap generate(String content, GeneratorFormat format, int size, String ecKey,
                                  String charset, int qrVersion, int qrMask, int margin,
                                  int foregroundColor, int backgroundColor) throws Exception {
        String normalized = format.normalize(content);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("生成内容不能为空");
        }
        if (foregroundColor == backgroundColor) {
            throw new IllegalArgumentException("前景色和背景色不能相同");
        }

        int width = size;
        int height = size;
        if (format.oneDimensional) {
            height = Math.max(220, size / 2);
        }

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET,
                charset == null || charset.isEmpty() ? "UTF-8" : charset);
        hints.put(EncodeHintType.MARGIN, Math.max(0, margin));

        if (format == GeneratorFormat.QR_CODE) {
            hints.put(EncodeHintType.ERROR_CORRECTION, errorCorrection(ecKey));
            if (qrVersion >= 1 && qrVersion <= 40) {
                hints.put(EncodeHintType.QR_VERSION, qrVersion);
            }
            if (qrMask >= 0 && qrMask <= 7) {
                hints.put(EncodeHintType.QR_MASK_PATTERN, qrMask);
            }
        }
        if (format == GeneratorFormat.DATA_MATRIX) {
            hints.put(EncodeHintType.DATA_MATRIX_SHAPE, SymbolShapeHint.FORCE_SQUARE);
            // Compact encoding enables ECI / UTF-8 when non-Latin-1 text is used.
            if (!isLatin1(normalized)) {
                hints.put(EncodeHintType.DATA_MATRIX_COMPACT, true);
            }
        }

        BarcodeFormat barcodeFormat = format.zxingFormat;
        BitMatrix matrix = new MultiFormatWriter().encode(normalized, barcodeFormat, width, height, hints);
        int matrixWidth = matrix.getWidth();
        int matrixHeight = matrix.getHeight();
        int[] pixels = new int[matrixWidth * matrixHeight];
        for (int y = 0; y < matrixHeight; y++) {
            int offset = y * matrixWidth;
            for (int x = 0; x < matrixWidth; x++) {
                pixels[offset + x] = matrix.get(x, y) ? foregroundColor : backgroundColor;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight);
        return bitmap;
    }

    private static boolean isLatin1(String content) {
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) > 0xFF) {
                return false;
            }
        }
        return true;
    }

    private static ErrorCorrectionLevel errorCorrection(String key) {
        if (key == null) {
            return ErrorCorrectionLevel.Q;
        }
        switch (key) {
            case "L":
                return ErrorCorrectionLevel.L;
            case "M":
                return ErrorCorrectionLevel.M;
            case "H":
                return ErrorCorrectionLevel.H;
            case "Q":
            default:
                return ErrorCorrectionLevel.Q;
        }
    }
}
