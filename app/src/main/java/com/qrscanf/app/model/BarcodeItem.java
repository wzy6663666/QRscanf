package com.qrscanf.app.model;

import com.qrscanf.app.util.BarcodeUtils;

import java.io.Serializable;

/**
 * One decoded barcode. Serializable so it can be passed to the result bottom sheet.
 */
public class BarcodeItem implements Serializable {

    public final String value;
    public final int mlkitFormat;
    public final String formatLabel;
    public final String source;
    public final long timestamp;
    public final String contentType;

    public BarcodeItem(String value, int mlkitFormat, String formatLabel, String source, long timestamp, String contentType) {
        this.value = BarcodeUtils.cleanValue(value);
        this.mlkitFormat = mlkitFormat;
        this.formatLabel = formatLabel;
        this.source = source;
        this.timestamp = timestamp;
        this.contentType = contentType;
    }

    public String key() {
        return mlkitFormat + "|" + value;
    }
}
