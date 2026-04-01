package com.sin_tech.ble_manager.ble_tradition;

import java.io.IOException;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

public class TradBleClientActivity extends Activity {

	private BluetoothAdapter mBluetoothAdapter;

	private final String tag = "zhangphil";
	private final String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB";
	private ClientThread clientThread = null;

	// 广播接收发现蓝牙设备
	@SuppressLint("MissingPermission")
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {

				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				String name = device != null ? device.getName() : null;
				if (name != null)
					Log.d(tag, "发现设备:" + name);

				if (name != null && name.equals("phil-pad")) {
					Log.d(tag, "发现目标设备，开始线程连接!");

					// 蓝牙搜索是非常消耗系统资源开销的过程，一旦发现了目标感兴趣的设备，可以考虑关闭扫描。
					mBluetoothAdapter.cancelDiscovery();

					clientThread = new ClientThread(device);
					clientThread.start();
				}
			}
		}
	};

	private class ClientThread extends Thread {
		private final BluetoothDevice device;

		public ClientThread(BluetoothDevice device) {
			this.device = device;
		}

		@Override
		public void run() {
			BluetoothSocket socket = null;
			try {
				socket = device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));

				Log.d(tag, "连接服务端...");
				socket.connect();
				Log.d(tag, "连接建立.");
			} catch (Exception e) {
				e.printStackTrace();
			}finally {
				if(socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
						Log.d(tag, "连接断开.");
                    }
                }
			}
		}
	}

	@SuppressLint("MissingPermission")
    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// 注册广播接收器。接收蓝牙发现讯息
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mReceiver, filter);

		if (mBluetoothAdapter.startDiscovery()) {
			Log.d(tag, "启动蓝牙扫描设备...");
		}
	}

	@Override
	protected void onDestroy() {
		if(clientThread != null){
			try {
				clientThread.interrupt();
			}catch (Exception e){
				e.printStackTrace();
			}finally {
				clientThread = null;
			}
		}
		unregisterReceiver(mReceiver);
		super.onDestroy();
	}
}