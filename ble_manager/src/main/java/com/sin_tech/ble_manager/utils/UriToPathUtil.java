package com.sin_tech.ble_manager.utils;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class UriToPathUtil {
    private static final String TAG = "UriToPathUtil";

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static Uri genValues(Context ctx, String fileName, String mimeType,String appName){
        ContentValues values = new ContentValues();
        Uri collection;
        if(mimeType.startsWith("image/")){
            //allowed directories are [DCIM, Pictures]
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            values.put(MediaStore.Images.Media.DISPLAY_NAME,fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE,mimeType);
            values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/"+appName);
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
        }else if(mimeType.startsWith("audio/")){
            //allowed directories are [Alarms, Music, Notifications, Podcasts, Ringtones]
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            values.put(MediaStore.Audio.Media.DISPLAY_NAME,fileName);
            values.put(MediaStore.Audio.Media.MIME_TYPE,mimeType);
            values.put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/"+appName);
            values.put(MediaStore.Audio.Media.IS_PENDING, 0);
        }else if(mimeType.startsWith("video/")){
            //allowed directories are [DCIM, Movies]
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            values.put(MediaStore.Video.Media.DISPLAY_NAME,fileName);
            values.put(MediaStore.Video.Media.MIME_TYPE,mimeType);
            values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/"+appName);
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
        }else{
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME,fileName);
            values.put(MediaStore.Files.FileColumns.MIME_TYPE,mimeType);
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH,"Documents/"+appName);
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 0);
        }
        if(collection!=null){
            Uri item = ctx.getContentResolver().insert(collection, values);
            if(item!=null){
                try {
                    @SuppressLint("Recycle")
                    ParcelFileDescriptor fileDescriptor =
                            ctx.getContentResolver().openFileDescriptor(item, "w", null);
                    Parcel out = Parcel.obtain();
                    if (fileDescriptor != null) {
                        fileDescriptor.writeToParcel(out, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                    }
                    return item;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }
        return null;
    }


    public static String getFileType(Context ctx,Uri uri){
        if ("file".equals(uri.getScheme())){
            String fileName = uri.getPath();
            if(fileName == null){
                fileName = "";
            }
            if(fileName.endsWith(".mp4")){
                return  "video/*";
            }else if(fileName.endsWith(".jpg") ||
                    fileName.endsWith(".png") ||
                    fileName.endsWith(".jpeg") ||
                    fileName.endsWith(".webp") ||
                    fileName.endsWith(".JPG") ||
                    fileName.endsWith(".PNG") ||
                    fileName.endsWith(".JPEG") ||
                    fileName.endsWith(".WEBP")){
                return  "image/*";
            }else{
                return  "*/*";
            }
        }else{
            return ctx.getContentResolver().getType(uri);
        }
    }

    public static String getFileType(File file){
        String fileName = file.getPath();
        if(fileName.endsWith(".mp4") ||
                fileName.endsWith(".mkv") ||
                fileName.endsWith(".rmvb") ||
                fileName.endsWith(".ts")){
            return  "video/*";
        }else if(fileName.endsWith(".jpg") ||
                fileName.endsWith(".png") ||
                fileName.endsWith(".jpeg") ||
                fileName.endsWith(".webp") ||
                fileName.endsWith(".JPG") ||
                fileName.endsWith(".PNG") ||
                fileName.endsWith(".JPEG") ||
                fileName.endsWith(".WEBP")){
            return  "image/*";
        }else if(fileName.endsWith(".mp3") ||
                fileName.endsWith(".ogg") ||
                fileName.endsWith(".flac") ||
                fileName.endsWith(".wav") ||
                fileName.endsWith(".dst") ||
                fileName.endsWith(".ape")){
            return  "audio/*";
        }else{
            return  "*/*";
        }
    }

    //提醒文件被移除
    public static void notifyFileDelete(Context context,String mimeType,String fileAbsolutePath){
        String mediaType = "";
        Uri fileUri = null;
        if(mimeType.startsWith("image/")){
            mediaType = MediaStore.Images.Media.DATA + "=?";
            fileUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }else if(mimeType.startsWith("video/")){
            mediaType = MediaStore.Video.Media.DATA + "=?";
            fileUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }else if(mimeType.startsWith("audio/")){
            mediaType = MediaStore.Audio.Media.DATA + "=?";
            fileUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }
        if(fileUri!=null) {
            context.getContentResolver().delete(fileUri, mediaType, new String[]{fileAbsolutePath});
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public static void closeContentValues(Context ctx, String mimeType, Uri con){
        ContentValues values = new ContentValues();
        if(mimeType.startsWith("image/")){
            values.put(MediaStore.Images.Media.IS_PENDING, 0); //释放，使其他应用可以访问
        }else if(mimeType.startsWith("audio/")){
            values.put(MediaStore.Audio.Media.IS_PENDING, 0); //释放，使其他应用可以访问
        }else if(mimeType.startsWith("video/")){
            values.put(MediaStore.Video.Media.IS_PENDING, 0); //释放，使其他应用可以访问
        }else{
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 0); //释放，使其他应用可以访问
        }
        if(con!=null){
            ctx.getContentResolver().update(con, values, null, null);
        }
    }


    public static boolean saveFileToUri(Context context,File file, Uri saveUri) {
        OutputStream outputStream;
        String mimeType = getFileType(file);
        try {
            outputStream = context.getContentResolver().openOutputStream(saveUri);
            InputStream inputStream = new FileInputStream(file);
            if (outputStream != null) {
                int len = 0;
                byte[] buf = new byte[4096];
                while((len = inputStream.read(buf)) !=-1){
                    outputStream.write(buf,0,len);
                }
                inputStream.close();
                outputStream.close();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                closeContentValues(context,mimeType,saveUri);
            }
        }
        return false;
    }

    /**
     * 将 Uri 转换为可能的文件路径（高版本 Android 可能返回 null 或临时文件路径）
     * 
     * @param context 上下文
     * @param uri 文件的 Uri
     * @return 可能的文件路径，无法获取时返回 null
     */
    public static String getPathFromUri(Context context, Uri uri) {
        if (uri == null) return null;
        
        // 检查 Uri 的 scheme
        String scheme = uri.getScheme();
        
        // 1. 如果是 file:// 协议，直接返回路径
        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            String path = uri.getPath();
            if (path != null && new File(path).exists()) {
                return path;
            }
        }
        
        // 2. 如果是 content:// 协议
        else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            // Android 4.4+ 的 DocumentsContract
            if (DocumentsContract.isDocumentUri(context, uri)) {
                return getPathFromDocumentUri(context, uri);
            }
            
            // 常规的 MediaStore
            return getDataColumn(context, uri, null, null);
        }
        
        return null;
    }
    
    /**
     * 处理 DocumentsContract 类型的 Uri（Android 4.4+）
     */
    private static String getPathFromDocumentUri(Context context, Uri uri) {
        if (isExternalStorageDocument(uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];
            
            if ("primary".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            }
            
            // 处理非主存储（如 SD 卡） - 需要额外权限
            // Android 4.4-5.1 可以获取，Android 6.0+ 需要 MANAGE_EXTERNAL_STORAGE
        } 
        
        else if (isDownloadsDocument(uri)) {
            final String id = DocumentsContract.getDocumentId(uri);
            if (!TextUtils.isEmpty(id) && id.matches("\\d+")) {
                // 下载管理器的文件
                final Uri contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    Long.valueOf(id)
                );
                return getDataColumn(context, contentUri, null, null);
            } else {
                // 第三方下载管理器的文件
                return getDataColumn(context, uri, null, null);
            }
        } 
        
        else if (isMediaDocument(uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];
            
            Uri contentUri = null;
            switch (type) {
                case "image":
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    break;
                case "video":
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    break;
                case "audio":
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    break;
            }
            
            final String selection = "_id=?";
            final String[] selectionArgs = new String[]{split[1]};
            
            return getDataColumn(context, contentUri, selection, selectionArgs);
        }
        
        return null;
    }
    
    /**
     * 从 ContentResolver 查询 _data 字段
     */
    private static String getDataColumn(Context context, Uri uri, 
            String selection, String[] selectionArgs) {
        final String column = MediaStore.Files.FileColumns.DATA;
        final String[] projection = {column};
        
        try (Cursor cursor = context.getContentResolver().query(
                uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                String path = cursor.getString(column_index);
                
                // 检查文件是否存在
                if (path != null && new File(path).exists()) {
                    return path;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "查询 _data 字段失败", e);
        }
        
        return null;
    }
    
    /**
     * 检查 Uri 是否是外部存储文档
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }
    
    /**
     * 检查 Uri 是否是下载文档
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }
    
    /**
     * 检查 Uri 是否是媒体文档
     */
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
    
    /**
     * 终极备选方案：复制文件到临时目录（适用于所有 Android 版本）
     * 这是最可靠的方案，特别是 Android 10+
     * 
     * @param context 上下文
     * @param uri 文件 Uri
     * @return 临时文件的路径，失败返回 null
     */
    public static String copyToTempFile(Context context, Uri uri) {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        
        try {
            // 获取文件名
            String fileName = getFileName(context, uri);
            if (fileName == null) {
                fileName = "temp_" + System.currentTimeMillis();
            }
            
            // 创建临时文件
            File tempDir = new File(context.getCacheDir(), "temp_files");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            
            File tempFile = new File(tempDir, fileName);
            
            // 复制文件
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;
            
            outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            return tempFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "复制到临时文件失败", e);
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (Exception e) {
                Log.w(TAG, "关闭流失败", e);
            }
        }
    }
    
    /**
     * 获取文件名
     */
    private static String getFileName(Context context, Uri uri) {
        String result = null;
        
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(
                    uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "获取文件名失败", e);
            }
        }
        
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        
        return result;
    }
}