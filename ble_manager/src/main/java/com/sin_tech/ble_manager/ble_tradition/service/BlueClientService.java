package com.sin_tech.ble_manager.ble_tradition.service;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.sin_tech.ble_manager.R;
import com.sin_tech.ble_manager.ble_tradition.activity.BlueClientActivity;
import com.sin_tech.ble_manager.ble_tradition.client.BlueDirectClient;
import com.sin_tech.ble_manager.ble_tradition.protocol.ClientCallback;
import com.sin_tech.ble_manager.ble_tradition.protocol.FileReceiveCallback;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;


/**
 * WiFi Direct 前台服务
 * 保证服务端在后台持续运行
 */
public class BlueClientService extends Service implements ClientCallback, FileReceiveCallback {

    private static final String TAG = "WiFiDirectClient";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "WiFiDirectChannel";
    private static final String CHANNEL_NAME = "WiFi Direct Service";

    // 服务器实例
    private BlueDirectClient wiFiDirectClient;
    private boolean isClientRunning = false;

    private final IBinder binder;

    private WeakReference<BlueClientActivity> ref;

    // 唤醒锁
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    public BlueClientService() {
        binder = new SerialBinder();
    }
    public final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setWeakRef(WeakReference<BlueClientActivity> actRef){
        this.ref = actRef;
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
        Log.d(TAG, "WiFiDirectForegroundService started");
        
        // 返回START_STICKY，系统会自动重启服务
        return START_STICKY;
    }

    public @Nullable BlueDirectClient getClient(){
        return wiFiDirectClient;
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
        Intent notificationIntent = new Intent(this, BlueClientActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Blue 服务运行中")
            .setContentText("正在保持连接，点击返回应用")
            .setSmallIcon(R.drawable.ic_blue_notify)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setSound(null)
            .setVibrate(null)
            .build();
    }


    // ======== Client Callback Start ===================================================
    @Override
    public void onConnected(String clientId){
        updateNotification("已连接到服务端");
        if(ref.get() != null){
            ref.get().onConnected(clientId);
        }
    }
    @Override
    public void onDisconnected(String clientId,String reason){
        updateNotification("等待连接到服务端...");
        if(ref.get() != null){
            ref.get().onDisconnected(clientId,reason);
        }
    }
    @Override
    public void onMessageReceived(String clientId,String message){
        if(ref.get() != null){
            ref.get().onMessageReceived(clientId,message);
        }
    }
    @Override
    public void onHeartbeatReceived(String clientId){
        // 心跳处理
        if(ref.get() != null){
            ref.get().onHeartbeatReceived(clientId);
        }
    }
    @Override
    public void onHeartbeatAckReceived(String clientId){
        // 心跳确认处理
        if(ref.get() != null){
            ref.get().onHeartbeatAckReceived(clientId);
        }
    }
    @Override
    public void onFileAckReceived(String clientId,String ack){
        if(ref.get() != null){
            ref.get().onFileAckReceived(clientId,ack);
        }
    }
    // ======== Client Callback End ===================================================


    // ======== File Transfer Callback Start ===================================================
    @Override
    public void onFileReceiveStarted(String clientId,String fileId, String fileName, long fileSize){
        updateNotification("正在接收文件: " + fileName);
        if(ref.get() != null){
            ref.get().onFileReceiveStarted(clientId,fileId,fileName,fileSize);
        }
    }
    @Override
    public void onFileChunkReceived(String clientId,String fileId, int chunkIndex, int chunkSize){
        // 分片接收处理
        if(ref.get() != null){
            ref.get().onFileChunkReceived(clientId,fileId,chunkIndex,chunkSize);
        }
    }
    @Override
    public void onFileReceived(String clientId,String fileId, String fileName, String filePath){
        updateNotification("文件接收完成: " + fileName);
        if(ref.get() != null){
            ref.get().onFileReceived(clientId,fileId,fileName,filePath);
        }
    }
    @Override
    public void onFileTransferError(String clientId,String error){
        if(ref.get() != null){
            ref.get().onFileTransferError(clientId,error);
        }
    }
    // ======== File Transfer Callback End ===================================================
    
    /**
     * 启动WiFi Direct服务器
     */
    public void connectToServer(Context context, BluetoothDevice device,
                                 WeakReference<ClientCallback> callback, WeakReference<FileReceiveCallback> fileCallback) {
        if (isClientRunning) {
            stopWiFiDirectClient();
        }
        File parent = context.getExternalFilesDir("temp");
        if(parent == null || !parent.exists()){
            parent.mkdirs();
        }
        String cacheDir = parent.getAbsolutePath();
        new Thread(){
            @Override
            public void run() {
                super.run();
                if(wiFiDirectClient != null) {
                    wiFiDirectClient.disconnect();
                }
                wiFiDirectClient = null;
                wiFiDirectClient = new BlueDirectClient(cacheDir, callback, fileCallback);
                String clientId = wiFiDirectClient.getClientId();
                try {
                    wiFiDirectClient.connect(device);
                } catch (IOException e) {
//                    throw new RuntimeException(e);
                    final String error = e.getLocalizedMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onDisconnected(clientId,error);
                            updateNotification("客户端启动失败");
                        }
                    });

                }
            }
        }.start();
    }
    
    /**
     * 更新通知
     */
    private void updateNotification(String contentText) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi Direct 服务运行中")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.wifi_notify_small)
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
     * 请求电池优化白名单
     */
    @SuppressLint("NewApi")
    public void requestBatteryOptimizationExclusion(Activity activity) {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));

            try {
                activity.startActivityForResult(intent, 100);
            } catch (Exception e) {
                Log.e(TAG, "Failed to request battery optimization exclusion", e);
            }
        }
    }
    
    /**
     * 停止WiFi Direct服务器
     */
    private void stopWiFiDirectClient() {
        if (wiFiDirectClient != null) {
            try {
                wiFiDirectClient.disconnect();
                wiFiDirectClient = null;
                isClientRunning = false;
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
        stopWiFiDirectClient();
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
        public BlueClientService getService() { return BlueClientService.this; }
    }

    /**
     * 启动前台服务
     */
    public static void startService(Context context) {
        Intent serviceIntent = new Intent(context, BlueClientService.class);
        
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
        Intent serviceIntent = new Intent(context, BlueClientService.class);
        context.stopService(serviceIntent);
    }
}