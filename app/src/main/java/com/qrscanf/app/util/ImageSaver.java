package com.qrscanf.app.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ImageSaver {

    private ImageSaver() {
    }

    public static Uri savePng(Context context, Bitmap bitmap) throws Exception {
        String name = "QRscanf_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveModern(context, bitmap, name);
        }
        return saveLegacy(context, bitmap, name);
    }

    private static Uri saveModern(Context context, Bitmap bitmap, String name) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + "QRscanf");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("无法创建相册文件");
        }
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("写入图片失败");
            }
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        return uri;
    }

    private static Uri saveLegacy(Context context, Bitmap bitmap, String name) throws Exception {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "QRscanf");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建相册目录");
        }
        File file = new File(dir, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("写入图片失败");
            }
        }
        MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
        return Uri.fromFile(file);
    }
}
