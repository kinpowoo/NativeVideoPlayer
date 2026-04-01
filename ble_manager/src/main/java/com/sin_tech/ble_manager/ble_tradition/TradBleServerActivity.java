package com.sin_tech.ble_manager.ble_tradition;

import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.util.Log;

public class TradBleServerActivity extends Activity {
	private final String tag = "zhangphil";
	private final String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB";
	private BluetoothAdapter mBluetoothAdapter;

	private boolean isServerRunning = true;

	private class ServerThread extends Thread {
		private BluetoothServerSocket serverSocket;

		@SuppressLint("MissingPermission")
        @Override
		public void run() {
			try {
				serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(tag, UUID.fromString(MY_UUID));
			} catch (Exception e) {
				e.printStackTrace();
			}

			Log.d(tag, "等待客户连接...");
			while (isServerRunning) {
				try {
					BluetoothSocket socket = serverSocket.accept();
					BluetoothDevice device = socket.getRemoteDevice();
					Log.d(tag, "接受客户连接 , 远端设备名字:" + device.getName() + " , 远端设备地址:" + device.getAddress());
					if (socket.isConnected()) {
						Log.d(tag, "已建立与客户连接.");
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter != null)
			new Thread(new ServerThread()).start();
	}

	@Override
	protected void onDestroy() {
		isServerRunning = false;
		super.onDestroy();
	}
}
