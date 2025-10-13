package com.robining.games.frame.utils.share;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ShareUtil {
    public static void shareText(Context context, CharSequence content, CharSequence chooserTitle) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, content);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(intent, chooserTitle));
    }

    public static void shareBitmap(Context context, String title, Bitmap bitmap, CharSequence chooserTitle) {
        File file = createTempFile(context, title + ".png");
        if (file == null) {
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_STREAM, FileProviderUtil.getUriForFile(context, file));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(intent, chooserTitle));
    }

    private static File createTempFile(Context context, String fileName) {
        File baseDir = null;
        if (Environment.isExternalStorageEmulated()) {
            File externalCache = context.getExternalCacheDir();
            if (externalCache != null && externalCache.exists()) {
                baseDir = externalCache;
            }
        } else {
            File cache = context.getCacheDir();
            if (cache != null && cache.exists()) {
                baseDir = cache;
            }
        }

        if (baseDir == null) {
            return null;
        }

        File dir = new File(baseDir, "share_temp");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                return null;
            }
        }
        File file = new File(dir, fileName);
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    return null;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        file.deleteOnExit();

        return file;
    }
}
