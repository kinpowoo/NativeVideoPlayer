/*
 * Copyright 2017 Guilherme Chaguri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jhkj.videoplayer.third_file_framework.ftp_server.impl;

import com.jhkj.videoplayer.third_file_framework.ftp_server.FTPConnection;
import com.jhkj.videoplayer.third_file_framework.ftp_server.api.IFileSystem;
import com.jhkj.videoplayer.third_file_framework.ftp_server.api.IUserAuthenticator;
import java.net.InetAddress;

/**
 * No Operation Authenticator
 *
 * Allows any user in with a predefined file system
 * @author Guilherme Chaguri
 */
public class NoOpAuthenticator implements IUserAuthenticator {

    private final IFileSystem fs;

    /**
     * Creates the authenticator
     * @param fs A file system
     * @see NativeFileSystem
     */
    public NoOpAuthenticator(IFileSystem fs) {
        this.fs = fs;
    }

    @Override
    public boolean needsUsername(FTPConnection con) {
        return false;
    }

    @Override
    public boolean needsPassword(FTPConnection con, String username, InetAddress address) {
        return false;
    }

    @Override
    public IFileSystem authenticate(FTPConnection con, InetAddress address, String username, String password) throws AuthException {
        return fs;
    }
}
