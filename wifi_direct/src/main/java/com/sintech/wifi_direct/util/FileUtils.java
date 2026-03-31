package com.sintech.wifi_direct.util;

import android.content.Context;
import android.os.Environment;

import java.io.File;

public class FileUtils {

    public static void moveFile(String originPath,File destFile){
        File oriFile = new File(originPath);
        if(oriFile.exists()) {
            oriFile.renameTo(destFile);
        }
    }

    public static File getDownloadDir(){
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
//        return Environment.getDownloadCacheDirectory();
    }
}
