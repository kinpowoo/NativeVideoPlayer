package com.sintech.wifi_direct.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class UriToPathUtil {
    private static final String TAG = "UriToPathUtil";
    
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