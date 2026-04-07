package com.jhkj.gl_player.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class MD5 {

    private MD5() {
    }

    private static final char[] hexDigits =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String toHexString(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(hexDigits[(b >> 4) & 0x0F]);
            hex.append(hexDigits[b & 0x0F]);
        }
        return hex.toString();
    }

    public static String md5(File file) throws IOException {
        MessageDigest messagedigest = null;
        FileInputStream in = null;
        FileChannel ch = null;
        byte[] encodeBytes = null;
        try {
            messagedigest = MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            ch = in.getChannel();
            MappedByteBuffer byteBuffer = ch.map(FileChannel.MapMode.READ_ONLY, 0, file.length());
            messagedigest.update(byteBuffer);
            encodeBytes = messagedigest.digest();
        } catch (NoSuchAlgorithmException neverHappened) {
            throw new RuntimeException(neverHappened);
        } finally {
            closeQuietly(in);
            closeQuietly(ch);
        }

        return toHexString(encodeBytes);
    }

    public static String md5(byte[] strBytes) {
        byte[] encodeBytes;
        try {
            encodeBytes = MessageDigest.getInstance("MD5").digest(strBytes);
        } catch (NoSuchAlgorithmException neverHappened) {
            throw new RuntimeException(neverHappened);
        }
        return toHexString(encodeBytes);
    }

    public static byte[] hash(byte[] strBytes) {
        byte[] encodeBytes;
        try {
            encodeBytes = MessageDigest.getInstance("MD5").digest(strBytes);
        } catch (NoSuchAlgorithmException neverHappened) {
            throw new RuntimeException(neverHappened);
        }
        return encodeBytes;
    }


    public static String md5(String string) {
        byte[] encodeBytes = null;
        try {
            encodeBytes = MessageDigest.getInstance("MD5").digest(string.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException neverHappened) {
            throw new RuntimeException(neverHappened);
        }
        return toHexString(encodeBytes);
    }

    public static byte[] md5Byte(String string) {
        byte[] encodeBytes;
        try {
            encodeBytes = MessageDigest.getInstance("MD5").digest(string.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException neverHappened) {
            throw new RuntimeException(neverHappened);
        }
        return encodeBytes;
    }



    private static void closeQuietly(Closeable closeable){
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
