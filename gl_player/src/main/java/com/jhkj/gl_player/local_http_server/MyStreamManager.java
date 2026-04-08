package com.jhkj.gl_player.local_http_server;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.jhkj.gl_player.util.TextureUtils;
import com.yanzhenjie.andserver.AndServer;
import com.yanzhenjie.andserver.Server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class MyStreamManager {
    private static MyStreamManager instance;
    private Server mServer;
    private int mPort = 7871;

    public static MyStreamManager getInstance(Context context) {
        if (instance == null) {
            instance = new MyStreamManager(context);
        }
        return instance;
    }

    private MyStreamManager(Context context) {
        try {
            mServer = AndServer.webServer(context)
                    .inetAddress(InetAddress.getByName("0.0.0.0"))
                    .port(mPort)
                    .timeout(10, TimeUnit.SECONDS)
                    .build();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    // 在 App 启动或 Activity onCreate 中调用
    public void startServer() {
        if (mServer != null && !mServer.isRunning()) {
            mServer.startup();
        }
    }

    // 核心方法：动态转换 URL
    public String getHttpUrl(String remoteSmbUrl,String username,String password) {
        // 使用 Uri.encode 是为了防止 SMB 路径中的中文字符或空格导致 URL 非法
        if(!TextUtils.isEmpty(username)){
            return "http://127.0.0.1:" + mPort + "/play?url=" +
                    Uri.encode(remoteSmbUrl)+"&username="+Uri.encode(username)+"&password="+Uri.encode(password);
        }
        return "http://127.0.0.1:" + mPort + "/play?url=" + Uri.encode(remoteSmbUrl);
    }

    public void stopServer() {
        if (mServer != null && mServer.isRunning()) {
            mServer.shutdown();
        }
    }
}