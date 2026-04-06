package com.jhkj.videoplayer.third_file_framework.ftp_server;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdManager.RegistrationListener;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NsdService extends Service {
    private static final String TAG = NsdService.class.getSimpleName();

    private static final String FTP_SERVICE_TYPE= "_ftp._tcp.";

    private NsdManager mNsdManager = null;

    // keep runaway threads in check
    private volatile boolean running = false;

    public static class ServerActionsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive broadcast: " + intent.getAction());

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                Log.w(TAG, "Pre-JB to old for NSD functionality");
                return;
            }
            if (intent.getAction() == null) {
                return;
            }
            if (intent.getAction().equals(FsService.ACTION_STARTED)) {
                Intent service = new Intent(context, NsdService.class);
                context.startService(service);
            } else if (intent.getAction().equals(FsService.ACTION_STOPPED)) {
                Intent service = new Intent(context, NsdService.class);
                context.stopService(service);
            }
        }

    }

    private final RegistrationListener mRegistrationListener = new RegistrationListener() {

        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceRegistered: " + serviceInfo.getServiceName());
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceUnregistered: " + serviceInfo.getServiceName());
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.e(TAG, "onRegistrationFailed: errorCode=" + errorCode);
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.e(TAG, "onUnregistrationFailed: errorCode=" + errorCode);
        }
    };

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate: Entered");

        running = true;
        String serviceNamePostfix = "FTP_Service";
        String serviceName = Build.MODEL + " " + serviceNamePostfix;

        final NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(serviceName);
        serviceInfo.setServiceType(FTP_SERVICE_TYPE);
        serviceInfo.setPort(21);

        new Thread(() -> {
            // this call sometimes hangs, this is why I get it in a separate thread
            Log.d(TAG, "onCreate: Trying to get the NsdManager");
            mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
            if (mNsdManager != null) {
                Log.d(TAG, "onCreate: Got the NsdManager");
                try {
                    // all kinds of problems with the NsdManager, give it
                    // some extra time before I make next call
                    Thread.sleep(500);
                    if (!running) {
                        Log.e(TAG, "NsdManager is no longer needed, bailing out");
                        mNsdManager = null;
                        return;
                    }
                    mNsdManager.registerService(serviceInfo,
                            NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
                } catch (Exception e) {
                    Log.e(TAG, "onCreate: Failed to register NsdManager");
                    mNsdManager = null;
                }
            } else {
                Log.d(TAG, "onCreate: Failed to get the NsdManager");
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Entered");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Entered");

        running = false;

        if (mNsdManager == null) {
            Log.e(TAG, "unregisterService: Unexpected mNsdManger to be null, bailing out");
            return;
        }
        try {
            mNsdManager.unregisterService(mRegistrationListener);
        } catch (Exception e) {
            Log.e(TAG, "Unable to unregister NSD service, error: " + e.getMessage());
        }
        mNsdManager = null;
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}