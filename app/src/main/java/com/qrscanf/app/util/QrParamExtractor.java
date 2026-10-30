package com.qrscanf.app.util;

import android.graphics.Bitmap;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.detector.Detector;

public final class QrParamExtractor {

    public static final class QrInfo {
        public final int version;
        public final int mask;
        public final String ecLevel;

        QrInfo(int version, int mask, String ecLevel) {
            this.version = version;
            this.mask = mask;
            this.ecLevel = ecLevel;
        }
    }

    private QrParamExtractor() {
    }

    /**
     * Reads QR version, mask pattern and error-correction level directly from the
     * module matrix of a captured QR image.
     */
    public static QrInfo extract(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            BinaryBitmap binaryBitmap = new BinaryBitmap(
                    new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
            DetectorResult detectorResult = new Detector(binaryBitmap.getBlackMatrix()).detect();
            com.google.zxing.common.BitMatrix bits = detectorResult.getBits();
            int dimension = bits.getHeight();
            if (dimension < 21 || (dimension - 17) % 4 != 0) {
                return null;
            }
            int version = (dimension - 17) / 4;
            if (version < 1 || version > 40) {
                return null;
            }
            int format1 = 0;
            for (int i = 0; i < 6; i++) {
                format1 = copy(bits, i, 8, format1);
            }
            format1 = copy(bits, 7, 8, format1);
            format1 = copy(bits, 8, 8, format1);
            format1 = copy(bits, 8, 7, format1);
            for (int j = 5; j >= 0; j--) {
                format1 = copy(bits, 8, j, format1);
            }
            int format2 = 0;
            for (int j = dimension - 1; j >= dimension - 7; j--) {
                format2 = copy(bits, 8, j, format2);
            }
            for (int i = dimension - 8; i < dimension; i++) {
                format2 = copy(bits, i, 8, format2);
            }
            int data = decodeFormatData(format1, format2);
            if (data < 0) {
                return null;
            }
            String ec = ecLevel((data >> 3) & 0x03);
            int mask = data & 0x07;
            return new QrInfo(version, mask, ec);
        } catch (Exception e) {
            return null;
        }
    }

    private static int copy(com.google.zxing.common.BitMatrix bits, int x, int y, int value) {
        return (value << 1) | (bits.get(x, y) ? 1 : 0);
    }

    private static int decodeFormatData(int format1, int format2) {
        int bestData = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int data = 0; data < 32; data++) {
            int codeword = bchFormatCodeword(data);
            int distance1 = Integer.bitCount(codeword ^ format1);
            int distance2 = Integer.bitCount(codeword ^ format2);
            int distance = Math.min(distance1, distance2);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestData = data;
            }
        }
        return bestDistance <= 3 ? bestData : -1;
    }

    /** 5 data bits + 10 BCH bits, masked with 0x5412 as required by ISO/IEC 18004. */
    private static int bchFormatCodeword(int data) {
        int value = data << 10;
        int poly = 0x537;
        while (highestBit(value) >= highestBit(poly)) {
            value ^= poly << (highestBit(value) - highestBit(poly));
        }
        return ((data << 10) | value) ^ 0x5412;
    }

    private static int highestBit(int value) {
        int bit = 0;
        while (value > 1) {
            value >>= 1;
            bit++;
        }
        return bit;
    }

    private static String ecLevel(int bits) {
        switch (bits) {
            case 0:
                return "M";
            case 1:
                return "L";
            case 2:
                return "H";
            case 3:
                return "Q";
            default:
                return "Q";
        }
    }
}
