package com.robining.games.frame.utils.share;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;

public class FileProviderUtil {
    public static String getAuthorities(Context context) {
        return context.getPackageName() + ".XProvider";
    }

    public static Uri getUriForFile(@NonNull Context context, @NonNull File file) {
        return FileProvider.getUriForFile(context, getAuthorities(context), file);
    }
}
