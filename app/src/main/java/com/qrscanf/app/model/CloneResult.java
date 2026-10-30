package com.qrscanf.app.model;

import java.io.Serializable;

/**
 * Result of "scan to fill" in the generator: content, symbology, a cropped copy of the
 * scanned symbol (used for the pixel-exact preview), and inferred QR parameters.
 */
public class CloneResult implements Serializable {

    public final String content;
    public final int mlkitFormat;
    public final String cropPath;
    public final int width;
    public final int height;
    public final int qrVersion;
    public final int qrMask;
    public final String ecLevel;

    public CloneResult(String content, int mlkitFormat, String cropPath, int width, int height,
                       int qrVersion, int qrMask, String ecLevel) {
        this.content = content;
        this.mlkitFormat = mlkitFormat;
        this.cropPath = cropPath;
        this.width = width;
        this.height = height;
        this.qrVersion = qrVersion;
        this.qrMask = qrMask;
        this.ecLevel = ecLevel;
    }
}
