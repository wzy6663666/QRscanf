package com.qrscanf.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;

import java.io.InputStream;

public final class ImageLoader {

    private ImageLoader() {
    }

    public static Bitmap decodePreview(Context context, Uri uri, int maxSize) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSize * 2) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap invert(Bitmap source) {
        if (source == null) {
            return null;
        }
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix(new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0
        });
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0, 0, paint);
        return output;
    }

    public static Bitmap enhanceContrast(Bitmap source) {
        if (source == null) {
            return null;
        }
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        android.graphics.ColorMatrix grayscale = new android.graphics.ColorMatrix();
        grayscale.setSaturation(0f);
        float scale = 2.2f;
        float offset = 128f * (1f - scale);
        android.graphics.ColorMatrix contrast = new android.graphics.ColorMatrix(new float[]{
                scale, 0, 0, 0, offset,
                0, scale, 0, 0, offset,
                0, 0, scale, 0, offset,
                0, 0, 0, 1, 0
        });
        contrast.postConcat(grayscale);
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(contrast));
        canvas.drawBitmap(source, 0, 0, paint);
        return output;
    }

    public static Bitmap flattenOnWhite(Bitmap bitmap) {        if (bitmap == null) {
            return null;
        }
        if (!bitmap.hasAlpha()) {
            return bitmap;
        }
        Bitmap flattened = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(flattened);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        return flattened;
    }

    /**
     * Scales so the longest side becomes {@code target} and adds a white quiet-zone margin.
     * ML Kit's Data Matrix detector is noticeably more reliable on this normalized input.
     */
    public static Bitmap scaleToMaxWithMargin(Bitmap source, int target) {
        if (source == null || target <= 0) {
            return null;
        }
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            int longest = Math.max(width, height);
            float scale = (float) target / longest;
            int scaledWidth = Math.max(1, Math.round(width * scale));
            int scaledHeight = Math.max(1, Math.round(height * scale));
            int margin = Math.max(8, Math.round(Math.max(scaledWidth, scaledHeight) * 0.05f));
            Bitmap output = Bitmap.createBitmap(scaledWidth + margin * 2,
                    scaledHeight + margin * 2, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            canvas.drawColor(Color.WHITE);
            Bitmap scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);
            canvas.drawBitmap(scaled, margin, margin, new Paint(Paint.FILTER_BITMAP_FLAG));
            if (scaled != source) {
                scaled.recycle();
            }
            return output;
        } catch (Exception e) {
            return null;
        }
    }
}
