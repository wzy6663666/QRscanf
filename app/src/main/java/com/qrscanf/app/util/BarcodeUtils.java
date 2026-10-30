package com.qrscanf.app.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.common.Barcode;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class BarcodeUtils {

    private static final SimpleDateFormat DATE_TIME =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private static final SimpleDateFormat TIME_ONLY =
            new SimpleDateFormat("HH:mm", Locale.CHINA);

    private BarcodeUtils() {
    }

    public static String formatLabel(int format) {
        switch (format) {
            case Barcode.FORMAT_QR_CODE:
                return "QR Code";
            case Barcode.FORMAT_DATA_MATRIX:
                return "Data Matrix";
            case Barcode.FORMAT_PDF417:
                return "PDF417";
            case Barcode.FORMAT_AZTEC:
                return "Aztec";
            case Barcode.FORMAT_CODE_128:
                return "Code 128";
            case Barcode.FORMAT_CODE_39:
                return "Code 39";
            case Barcode.FORMAT_CODE_93:
                return "Code 93";
            case Barcode.FORMAT_CODABAR:
                return "Codabar";
            case Barcode.FORMAT_EAN_13:
                return "EAN-13";
            case Barcode.FORMAT_EAN_8:
                return "EAN-8";
            case Barcode.FORMAT_ITF:
                return "ITF";
            case Barcode.FORMAT_UPC_A:
                return "UPC-A";
            case Barcode.FORMAT_UPC_E:
                return "UPC-E";
            default:
                return "未知码制";
        }
    }

    public static String formatFullLabel(int format) {
        switch (format) {
            case Barcode.FORMAT_QR_CODE:
                return "QR Code（二维码）";
            case Barcode.FORMAT_DATA_MATRIX:
                return "Data Matrix（DM码）";
            case Barcode.FORMAT_PDF417:
                return "PDF417";
            case Barcode.FORMAT_AZTEC:
                return "Aztec";
            case Barcode.FORMAT_CODE_128:
                return "Code 128";
            case Barcode.FORMAT_CODE_39:
                return "Code 39";
            case Barcode.FORMAT_CODE_93:
                return "Code 93";
            case Barcode.FORMAT_CODABAR:
                return "Codabar";
            case Barcode.FORMAT_EAN_13:
                return "EAN-13";
            case Barcode.FORMAT_EAN_8:
                return "EAN-8";
            case Barcode.FORMAT_ITF:
                return "ITF";
            case Barcode.FORMAT_UPC_A:
                return "UPC-A";
            case Barcode.FORMAT_UPC_E:
                return "UPC-E";
            default:
                return "未知码制";
        }
    }

    public static boolean isTwoDimensional(int format) {
        return format == Barcode.FORMAT_QR_CODE
                || format == Barcode.FORMAT_DATA_MATRIX
                || format == Barcode.FORMAT_PDF417
                || format == Barcode.FORMAT_AZTEC;
    }

    public static int formatColor(int format) {
        switch (format) {
            case Barcode.FORMAT_QR_CODE:
                return 0xFF5B6CF9;
            case Barcode.FORMAT_DATA_MATRIX:
                return 0xFF0FB981;
            case Barcode.FORMAT_PDF417:
                return 0xFF2F80ED;
            case Barcode.FORMAT_AZTEC:
                return 0xFF8E54E9;
            case Barcode.FORMAT_UNKNOWN:
                return 0xFF64748B;
            default:
                return 0xFFF57C3D;
        }
    }

    public static String cleanValue(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        while (start < value.length()) {
            char c = value.charAt(start);
            if (c == '\t' || c == '\n' || c == '\r') {
                break;
            }
            if (c >= 0x20 && c != 0x7F) {
                break;
            }
            start++;
        }
        return start == 0 ? value : value.substring(start);
    }

    public static String contentType(String value) {        if (value == null) {
            return "内容";
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return "网址";
        }
        if (lower.startsWith("wifi:")) {
            return "WiFi";
        }
        if (lower.startsWith("mailto:")) {
            return "邮箱";
        }
        if (lower.startsWith("tel:")) {
            return "电话";
        }
        if (lower.startsWith("smsto:") || lower.startsWith("sms:")) {
            return "短信";
        }
        if (lower.startsWith("begin:vcard")) {
            return "名片";
        }
        if (lower.startsWith("geo:")) {
            return "位置";
        }
        if (lower.startsWith("otpauth:")) {
            return "动态口令";
        }
        if (lower.matches("^[0-9]+$")) {
            return "数字";
        }
        return "文本";
    }

    public static String time(long timestamp) {
        return DATE_TIME.format(new Date(timestamp));
    }

    public static String shortTime(long timestamp) {
        return TIME_ONLY.format(new Date(timestamp));
    }

    public static void copy(Context context, String text) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            return;
        }
        manager.setPrimaryClip(ClipData.newPlainText("QRscanf", text));
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }
}
