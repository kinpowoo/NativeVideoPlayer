package com.jhkj.gl_player.data_source_imp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;

import jcifs.CIFSContext;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

// 1. 创建一个简单的ContentProvider
public class SMBContentProvider extends ContentProvider {
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        try {
            // 从Uri中获取SMB路径
            String smbUrl = uri.getQueryParameter("url");
            if(smbUrl == null) {
                throw new FileNotFoundException("Failed to open empty Path SMB file: ");
            }
            String username = uri.getQueryParameter("username");
            String password = uri.getQueryParameter("password");
            CIFSContext context;
            if(!TextUtils.isEmpty(username)){
                // 如果域为空，可以传入空字符串
                NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(null,
                        username,password,
                        NtlmPasswordAuthenticator.AuthenticationType.USER);
                context = SingletonContext.getInstance().withCredentials(auth);
            }else{
                context = SingletonContext.getInstance().withGuestCrendentials();
            }
            SmbFile smbFile = new SmbFile(smbUrl, context);
            SmbFileInputStream inputStream = new SmbFileInputStream(smbFile);

            // 创建ParcelFileDescriptor
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try {
                    // 将SMB流数据写入管道
                    ParcelFileDescriptor.AutoCloseOutputStream outputStream =
                            new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    inputStream.close();
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            return pipe[0];
        } catch (Exception e) {
            throw new FileNotFoundException("Failed to open SMB file: " + e.getMessage());
        }
    }

    // 其他必要方法（简化实现）
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}
