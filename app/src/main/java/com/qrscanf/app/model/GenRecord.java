package com.qrscanf.app.model;

import java.io.Serializable;

/**
 * One generated barcode record.
 */
public class GenRecord implements Serializable {

    public final String content;
    public final String formatLabel;
    public final String formatKey;
    public final long timestamp;
    public final int size;
    public final String errorCorrection;

    public GenRecord(String content, String formatLabel, String formatKey, long timestamp,
                     int size, String errorCorrection) {
        this.content = content;
        this.formatLabel = formatLabel;
        this.formatKey = formatKey;
        this.timestamp = timestamp;
        this.size = size;
        this.errorCorrection = errorCorrection;
    }
}
