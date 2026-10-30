package com.qrscanf.app.generator;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.zxing.BarcodeFormat;

import java.util.Locale;

/**
 * Formats offered by the generator, including a few content constraints and hints.
 */
public enum GeneratorFormat {

    QR_CODE("QR Code（二维码）", "QR_CODE", BarcodeFormat.QR_CODE, true, false,
            "输入文本、网址、编号等内容"),
    DATA_MATRIX("Data Matrix（DM码）", "DATA_MATRIX", BarcodeFormat.DATA_MATRIX, false, false,
            "输入文本、编号或零件序列号"),
    PDF417("PDF417", "PDF_417", BarcodeFormat.PDF_417, false, false,
            "输入文本、证件信息或较长内容"),
    AZTEC("Aztec", "AZTEC", BarcodeFormat.AZTEC, false, false,
            "输入文本、网址或票据信息"),
    CODE_128("Code 128（条形码）", "CODE_128", BarcodeFormat.CODE_128, false, true,
            "输入英文、数字或 ASCII 字符"),
    CODE_39("Code 39（条形码）", "CODE_39", BarcodeFormat.CODE_39, false, true,
            "仅支持大写字母、数字和 - . $ / + % 空格"),
    CODE_93("Code 93（条形码）", "CODE_93", BarcodeFormat.CODE_93, false, true,
            "仅支持大写字母、数字和常用符号"),
    EAN_13("EAN-13（商品码）", "EAN_13", BarcodeFormat.EAN_13, false, true,
            "输入 12 位数字（自动补校验位）或 13 位"),
    EAN_8("EAN-8（商品码）", "EAN_8", BarcodeFormat.EAN_8, false, true,
            "输入 7 位数字（自动补校验位）或 8 位"),
    CODABAR("Codabar（条形码）", "CODABAR", BarcodeFormat.CODABAR, false, true,
            "输入数字及 - $ : / . + 符号"),
    ITF("ITF（交叉二五码）", "ITF", BarcodeFormat.ITF, false, true,
            "输入偶数位数字");

    public final String label;
    public final String key;
    public final BarcodeFormat zxingFormat;
    public final boolean supportsErrorCorrection;
    public final boolean oneDimensional;
    public final String hint;

    GeneratorFormat(String label, String key, BarcodeFormat zxingFormat, boolean supportsErrorCorrection,
                    boolean oneDimensional, String hint) {
        this.label = label;
        this.key = key;
        this.zxingFormat = zxingFormat;
        this.supportsErrorCorrection = supportsErrorCorrection;
        this.oneDimensional = oneDimensional;
        this.hint = hint;
    }

    public static GeneratorFormat fromKey(String key) {
        for (GeneratorFormat format : values()) {
            if (format.key.equals(key)) {
                return format;
            }
        }
        return QR_CODE;
    }

    public static GeneratorFormat fromIndex(int index) {
        if (index < 0 || index >= values().length) {
            return QR_CODE;
        }
        return values()[index];
    }

    /**
     * Maps an ML Kit barcode format to a generator format. Returns null when the
     * detected symbology has no encoder in this app (e.g. UPC-A/E).
     */
    public static GeneratorFormat fromMlKitFormat(int mlkitFormat) {
        switch (mlkitFormat) {
            case Barcode.FORMAT_QR_CODE:
                return QR_CODE;
            case Barcode.FORMAT_DATA_MATRIX:
                return DATA_MATRIX;
            case Barcode.FORMAT_PDF417:
                return PDF417;
            case Barcode.FORMAT_AZTEC:
                return AZTEC;
            case Barcode.FORMAT_CODE_128:
                return CODE_128;
            case Barcode.FORMAT_CODE_39:
                return CODE_39;
            case Barcode.FORMAT_CODE_93:
                return CODE_93;
            case Barcode.FORMAT_EAN_13:
                return EAN_13;
            case Barcode.FORMAT_EAN_8:
                return EAN_8;
            case Barcode.FORMAT_CODABAR:
                return CODABAR;
            case Barcode.FORMAT_ITF:
                return ITF;
            default:
                return null;
        }
    }

    public static int indexOf(GeneratorFormat target) {
        GeneratorFormat[] formats = values();
        for (int i = 0; i < formats.length; i++) {
            if (formats[i] == target) {
                return i;
            }
        }
        return 0;
    }

    public static String[] labels() {
        GeneratorFormat[] formats = values();
        String[] labels = new String[formats.length];
        for (int i = 0; i < formats.length; i++) {
            labels[i] = formats[i].label;
        }
        return labels;
    }

    /**
     * Validates and normalizes content so that the resulting barcode is standards compliant.
     *
     * @throws IllegalArgumentException with a user-facing Chinese message
     */
    public String normalize(String raw) {
        String content = raw == null ? "" : raw;
        switch (this) {
            case CODE_39:
            case CODE_93:
                content = content.toUpperCase(Locale.ROOT);
                if (!content.matches("[0-9A-Z \\-.$/+%]+")) {
                    throw new IllegalArgumentException("该码制仅支持大写字母、数字和 - . $ / + % 空格");
                }
                return content;
            case CODE_128:
                for (int i = 0; i < content.length(); i++) {
                    if (content.charAt(i) > 127) {
                        throw new IllegalArgumentException("Code 128 仅支持英文、数字和 ASCII 字符，包含中文时请使用 QR Code 或 Data Matrix");
                    }
                }
                return content;
            case EAN_13:
                content = content.replace(" ", "");
                if (!content.matches("\\d{12,13}")) {
                    throw new IllegalArgumentException("EAN-13 需要 12 位或 13 位数字");
                }
                return content;
            case EAN_8:
                content = content.replace(" ", "");
                if (!content.matches("\\d{7,8}")) {
                    throw new IllegalArgumentException("EAN-8 需要 7 位或 8 位数字");
                }
                return content;
            case ITF:
                content = content.replace(" ", "");
                if (!content.matches("\\d+") || content.length() % 2 != 0) {
                    throw new IllegalArgumentException("ITF 需要偶数位数字");
                }
                return content;
            case CODABAR:
                content = content.toUpperCase(Locale.ROOT);
                if (!content.matches("[0-9A-D\\-$:/.+]+")) {
                    throw new IllegalArgumentException("Codabar 仅支持数字、A-D 及 - $ : / . + 符号");
                }
                return content;
            default:
                return content;
        }
    }
}
