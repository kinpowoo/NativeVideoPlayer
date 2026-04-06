package com.jhkj.videoplayer.third_file_framework.ftp_server;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.jhkj.videoplayer.R;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.InetAddress;

import com.jhkj.videoplayer.third_file_framework.ftp_server.impl.NativeFileSystem;
import com.jhkj.videoplayer.third_file_framework.ftp_server.impl.NoOpAuthenticator;
import com.jhkj.videoplayer.third_file_framework.ftp_server.impl.UserbaseAuthenticator;


/**
 * WiFi Direct 前台服务
 * 保证服务端在后台持续运行
 */
public class FsService extends Service{

    private static final String TAG = "FtpService";
    // Service will (global) broadcast when server start/stop
    static public final String ACTION_STARTED = "be.ppareit.swiftp.FTPSERVER_STARTED";
    static public final String ACTION_STOPPED = "be.ppareit.swiftp.FTPSERVER_STOPPED";

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "FtpServiceChannel";
    private static final String CHANNEL_NAME = "FTP Service";

    private final IBinder binder;

    // 唤醒锁
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private FTPServer ftpServer = null;
    public FsService() {
        binder = new FsService.SerialBinder();
    }

    // 广播接收器
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                Log.d(TAG, "Screen turned on");
                acquireLocks();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                Log.d(TAG, "Screen turned off");
                // 保持锁，确保后台运行
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                Log.d(TAG, "User unlocked device");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WiFiDirectForegroundService created");
        // 创建通知渠道
        createNotificationChannel();
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification());
        // 注册屏幕状态监听
        registerScreenReceiver();
        // 获取唤醒锁
        acquireLocks();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 返回START_STICKY，系统会自动重启服务
        return START_STICKY;
    }

    public @Nullable FTPServer getServer(){
        return ftpServer;
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("WiFi Direct background service");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建前台服务通知
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, FtpServerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FTP服务运行中")
                .setContentText("正在运行，点击返回应用")
                .setSmallIcon(R.drawable.ftp)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .setSound(null)
                .setVibrate(null)
                .build();
    }


    /**
     * 启动WiFi Direct服务器
     */
    public boolean startFtpServer(String host,String username,String pass,int port) {
        stopFtpServer();
        // Create the FTP server
        ftpServer = new FTPServer();
        // Create our custom authenticator
        UserbaseAuthenticator auth = new UserbaseAuthenticator();
        // Register a few users
        auth.registerUser(username,pass);
        // Set our custom authenticator
        ftpServer.setAuthenticator(auth);

        // Changes the timeout to 10 minutes
        ftpServer.setTimeout(10 * 60 * 1000); // 10 minutes

        // Changes the buffer size
        ftpServer.setBufferSize(1024 * 500); // 5 kilobytes

        try {
            new Thread(){
                @Override
                public void run() {
                    try {
                        // Start it synchronously in our localhost and in the port 21
                        ftpServer.listenSync(InetAddress.getByName(host), port);
                        updateNotification("等待设备连接...");
                    } catch (Exception e) {
                        updateNotification("FTP服务器启动失败");
                    }
                }
            }.start();
            return true;
        } catch (Exception e) {
            updateNotification("FTP服务器启动失败");
        }
        return false;
    }

    /**
     * 更新通知
     */
    private void updateNotification(String contentText) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FTP前台服务运行中")
                .setContentText(contentText)
                .setSmallIcon(com.sintech.wifi_direct.R.drawable.wifi_notify_small)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * 获取唤醒锁
     */
    private void acquireLocks() {
        // 获取唤醒锁（CPU保持运行）
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK |
                            PowerManager.ON_AFTER_RELEASE,
                    "WiFiDirect:WakeLock"
            );
            wakeLock.setReferenceCounted(false);

            if (!wakeLock.isHeld()) {
                wakeLock.acquire(10*60*1000L /*10 minutes*/);
                Log.d(TAG, "WakeLock acquired");
            }
        }

        // 获取WiFi锁（保持WiFi连接）
        WifiManager wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "WiFiDirect:WifiLock"
            );
            wifiLock.setReferenceCounted(false);

            if (!wifiLock.isHeld()) {
                wifiLock.acquire();
                Log.d(TAG, "WifiLock acquired");
            }
        }
    }

    /**
     * 释放唤醒锁
     */
    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
            Log.d(TAG, "WakeLock released");
        }

        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            wifiLock = null;
            Log.d(TAG, "WifiLock released");
        }
    }

    /**
     * 注册屏幕状态接收器
     */
    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter);
    }


    /**
     * 停止WiFi Direct服务器
     */
    public void stopFtpServer() {
        if (ftpServer != null) {
            try {
                ftpServer.close();
                ftpServer = null;
                Log.d(TAG, "WiFi Direct server stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping WiFi Direct server", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "WiFiDirectForegroundService destroying");
        // 停止服务器
        stopFtpServer();
        // 释放唤醒锁
        releaseLocks();
        // 取消广播接收器注册
        try {
            unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException e) {
            // 接收器未注册
        }
        // 停止前台服务
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class SerialBinder extends Binder {
        public FsService getService() { return FsService.this; }
    }

    /**
     * 启动前台服务
     */
    public static void startService(Context context) {
        Intent serviceIntent = new Intent(context, FsService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    /**
     * 停止前台服务
     */
    public static void stopService(Context context) {
        Intent serviceIntent = new Intent(context, FsService.class);
        context.stopService(serviceIntent);
    }
}