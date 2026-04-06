package com.jhkj.videoplayer.third_file_framework.ftp_server.impl;

import android.os.Environment;

import com.jhkj.videoplayer.app.MainApplication;
import com.jhkj.videoplayer.third_file_framework.ftp_server.FTPConnection;
import com.jhkj.videoplayer.third_file_framework.ftp_server.api.IFileSystem;
import com.jhkj.videoplayer.third_file_framework.ftp_server.api.IUserAuthenticator;

import java.io.File;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple user base which encodes passwords in MD5 (not really for security, it's just as an example)
 * @author Guilherme Chaguri
 */
public class UserbaseAuthenticator implements IUserAuthenticator {

    private final Map<String, byte[]> userbase = new HashMap<>();

    private byte[] toMD5(String pass) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(pass.getBytes("UTF-8"));
        } catch(Exception ex) {
            return pass.getBytes();
        }
    }

    public void registerUser(String username, String password) {
        userbase.put(username, toMD5(password));
    }

    @Override
    public boolean needsUsername(FTPConnection con) {
        return true;
    }

    @Override
    public boolean needsPassword(FTPConnection con, String username, InetAddress address) {
        return true;
    }

    @Override
    public IFileSystem authenticate(FTPConnection con, InetAddress address, String username, String password) throws AuthException {
        // Check for a user with that username in the database
        if(!userbase.containsKey(username)) {
            throw new AuthException();
        }

        // Gets the correct, original password
        byte[] originalPass = userbase.get(username);

        // Calculates the MD5 for the given password
        byte[] inputPass = toMD5(password);

        // Check for wrong password
        if(!Arrays.equals(originalPass, inputPass)) {
            throw new AuthException();
        }

        // Use the username as a directory for file storage
        File rootParent = Environment.getRootDirectory();
        File sdRoot = Environment.getExternalStorageDirectory();
//        File path = new File(System.getProperty("user.dir"));
        return new NativeFileSystem(sdRoot);
    }
}