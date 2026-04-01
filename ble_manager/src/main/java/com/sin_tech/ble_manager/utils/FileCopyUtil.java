package com.sin_tech.ble_manager.utils;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FileCopyUtil {
    
    /**
     * 使用流拷贝文件（通用方法，兼容所有 Android 版本）
     * 
     * @param sourceFile 源文件
     * @param destFile   目标文件
     * @return 是否拷贝成功
     */
    public static boolean copyFileByStream(File sourceFile, File destFile) {
        // 1. 参数检查
        if (sourceFile == null || destFile == null) {
            return false;
        }
        
        if (!sourceFile.exists() || sourceFile.isDirectory()) {
            return false;
        }
        
        // 2. 确保目标目录存在
        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                return false;
            }
        }
        
        // 3. 如果目标文件已存在，先删除
        if (destFile.exists()) {
            if (!destFile.delete()) {
                return false;
            }
        }
        
        // 4. 执行文件拷贝
        FileInputStream fis = null;
        FileOutputStream fos = null;
        
        try {
            fis = new FileInputStream(sourceFile);
            fos = new FileOutputStream(destFile);
            
            byte[] buffer = new byte[8192]; // 8KB 缓冲区
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            
            fos.flush();
            return true;
            
        } catch (IOException e) {
            // 拷贝失败，删除可能已创建的目标文件
            if (destFile.exists()) {
                destFile.delete();
            }
            return false;
        } finally {
            // 5. 确保流被关闭
            closeQuietly(fis);
            closeQuietly(fos);
        }
    }
    
    /**
     * 使用 NIO 的 FileChannel 拷贝（性能更好，Android API 1+ 支持）
     * 
     * @param sourceFile 源文件
     * @param destFile   目标文件
     * @return 是否拷贝成功
     */
    public static boolean copyFileByChannel(File sourceFile, File destFile) {
        if (sourceFile == null || destFile == null || !sourceFile.exists()) {
            return false;
        }
        
        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                return false;
            }
        }
        
        if (destFile.exists() && !destFile.delete()) {
            return false;
        }
        
        FileChannel sourceChannel = null;
        FileChannel destChannel = null;
        
        try {
            sourceChannel = new FileInputStream(sourceFile).getChannel();
            destChannel = new FileOutputStream(destFile).getChannel();
            
            long transferred = 0;
            long size = sourceChannel.size();
            
            // 分块传输，避免大文件一次性传输
            while (transferred < size) {
                transferred += destChannel.transferFrom(sourceChannel, transferred, size - transferred);
            }
            
            return true;
            
        } catch (IOException e) {
            e.printStackTrace();
            if (destFile.exists()) {
                destFile.delete();
            }
            return false;
        } finally {
            closeQuietly(sourceChannel);
            closeQuietly(destChannel);
        }
    }
    
    /**
     * 使用 Java 7+ 的 Files.copy（需要 API 26+/Android 8.0+）
     * 
     * @param sourceFile 源文件
     * @param destFile   目标文件
     * @return 是否拷贝成功
     */
    public static boolean copyFileByFilesAPI(File sourceFile, File destFile) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return copyFileByStream(sourceFile, destFile);
        }
        
        if (sourceFile == null || destFile == null || !sourceFile.exists()) {
            return false;
        }
        
        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                return false;
            }
        }
        
        try {
            Files.copy(sourceFile.toPath(), destFile.toPath(), 
                      StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 带进度回调的文件拷贝
     * 
     * @param sourceFile 源文件
     * @param destFile   目标文件
     * @param listener   进度监听器
     * @return 是否拷贝成功
     */
    public static boolean copyFileWithProgress(File sourceFile, File destFile, 
                                              CopyProgressListener listener) {
        if (sourceFile == null || destFile == null || !sourceFile.exists()) {
            if (listener != null) listener.onError(new IOException("源文件不存在"));
            return false;
        }
        
        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                if (listener != null) listener.onError(new IOException("创建目录失败"));
                return false;
            }
        }
        
        if (destFile.exists() && !destFile.delete()) {
            if (listener != null) listener.onError(new IOException("删除目标文件失败"));
            return false;
        }
        
        FileInputStream fis = null;
        FileOutputStream fos = null;
        
        try {
            long totalSize = sourceFile.length();
            long copiedSize = 0;
            
            fis = new FileInputStream(sourceFile);
            fos = new FileOutputStream(destFile);
            
            if (listener != null) listener.onStart(totalSize);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                copiedSize += bytesRead;
                
                if (listener != null) {
                    int progress = (int) ((copiedSize * 100) / totalSize);
                    listener.onProgress(progress, copiedSize, totalSize);
                }
            }
            
            fos.flush();
            
            if (listener != null) listener.onComplete(destFile);
            return true;
            
        } catch (IOException e) {
            e.printStackTrace();
            if (destFile.exists()) {
                destFile.delete();
            }
            if (listener != null) listener.onError(e);
            return false;
        } finally {
            closeQuietly(fis);
            closeQuietly(fos);
        }
    }
    
    /**
     * 关闭流（静默关闭，不抛出异常）
     */
    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // 静默关闭
            }
        }
    }
    
    /**
     * 关闭 Channel（静默关闭）
     */
    private static void closeQuietly(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                // 静默关闭
            }
        }
    }
    
    /**
     * 拷贝进度监听接口
     */
    public interface CopyProgressListener {
        /**
         * 开始拷贝
         * @param totalSize 总大小（字节）
         */
        default void onStart(long totalSize) {}
        
        /**
         * 拷贝进度
         * @param progress 进度百分比 0-100
         * @param copiedSize 已拷贝字节数
         * @param totalSize 总字节数
         */
        void onProgress(int progress, long copiedSize, long totalSize);
        
        /**
         * 拷贝完成
         * @param destFile 目标文件
         */
        default void onComplete(File destFile) {}
        
        /**
         * 拷贝出错
         * @param e 异常
         */
        default void onError(IOException e) {}
    }
}