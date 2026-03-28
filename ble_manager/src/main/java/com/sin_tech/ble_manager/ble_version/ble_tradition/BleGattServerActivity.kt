package com.sin_tech.ble_manager.ble_version.ble_tradition

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID


class BleGattServerActivity : AppCompatActivity(){
    private var SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private var CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val mHandler: Handler = Handler(Looper.getMainLooper()) // 声明一个处理器对象
    private val dip_margin = 0 // 每条聊天记录的四周空白距离
    private val mMinute = "00:00"
    private var mBluetoothManager: BluetoothManager? = null // 声明一个蓝牙管理器对象
    private var mBluetoothAdapter: BluetoothAdapter? = null // 声明一个蓝牙适配器对象
    private var mRemoteDevice: BluetoothDevice? = null // 声明一个蓝牙设备对象
    private var mGattServer: BluetoothGattServer? = null // 声明一个蓝牙GATT服务器对象
    private var mReadChara: BluetoothGattCharacteristic? = null // 客户端读取数据的特征值


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        val gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)
        gattServer.addService(service)

        initBluetooth(); // 初始化蓝牙适配器
        mHandler.postDelayed(mAdvertise, 200); // 延迟200毫秒开启低功耗蓝牙广播任务
    }


    var gattServerCallback: BluetoothGattServerCallback = object : BluetoothGattServerCallback() {
        public override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // 当设备连接时的处理逻辑
            }
        }

        public override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            // 处理特征读取请求
        }
    }

    // 初始化蓝牙适配器
    private fun initBluetooth() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "当前设备不支持低功耗蓝牙", Toast.LENGTH_SHORT).show()
            finish() // 关闭当前页面
        }
        // 获取蓝牙管理器，并从中得到蓝牙适配器
        mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?
        mBluetoothAdapter = mBluetoothManager?.adapter // 获取蓝牙适配器
        if (!BluetoothUtil.getBlueToothStatus(this)) { // 还未打开蓝牙
            // 开启蓝牙功能
            bluetoothPermissionLauncher.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    //申请蓝牙权限
    private val bluetoothPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if(it.resultCode == Activity.RESULT_OK){
                //如果通过了权限，进入下一页面
            }else{

            }
        }

    // 创建一个低功耗蓝牙广播任务
    private val mAdvertise: Runnable = object : Runnable {
        override fun run() {
            if (BluetoothUtil.getBlueToothStatus(this@BleGattServerActivity)) { // 已经打开蓝牙
                stopAdvertise() // 停止低功耗蓝牙广播
                val server_name = getIntent().getStringExtra("server_name")
                startAdvertise(server_name) // 开始低功耗蓝牙广播
//                tv_hint.setText("“" + server_name + "”服务端正在广播，请等候客户端连接")
            } else {
                mHandler.postDelayed(this, 2000)
            }
        }
    }

    // 开始低功耗蓝牙广播
    @SuppressLint("MissingPermission")
    private fun startAdvertise(ble_name: String?) {
        // 设置广播参数
        val settings = AdvertiseSettings.Builder()
            .setConnectable(true) // 是否允许连接
            .setTimeout(0) // 设置超时时间
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .build()
        // 设置广播内容
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // 是否把设备名称也广播出去
            .setIncludeTxPowerLevel(true) // 是否把功率电平也广播出去
            .build()
        mBluetoothAdapter?.setName(ble_name) // 设置BLE服务端的名称
        // 获取BLE广播器
        val advertiser = mBluetoothAdapter?.bluetoothLeAdvertiser
        // BLE服务端开始广播，好让别人发现自己
        advertiser?.startAdvertising(settings, advertiseData, mAdvertiseCallback)
    }

    // 停止低功耗蓝牙广播
    @SuppressLint("MissingPermission")
    private fun stopAdvertise() {
        // 获取BLE广播器
        val advertiser = mBluetoothAdapter?.bluetoothLeAdvertiser
        advertiser?.stopAdvertising(mAdvertiseCallback) // 停止低功耗蓝牙广播
    }

    // 创建一个低功耗蓝牙广播回调对象
    private val mAdvertiseCallback: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settings: AdvertiseSettings) {
//            Log.d(TAG, "低功耗蓝牙广播成功：" + settings.toString())
            addService() // 添加读写服务UUID，特征值等
        }

        override fun onStartFailure(errorCode: Int) {
//            Log.d(TAG, "低功耗蓝牙广播失败，错误代码为" + errorCode)
        }
    }

    // 添加读写服务UUID，特征值等
    @SuppressLint("MissingPermission")
    private fun addService() {
        val gattService = BluetoothGattService(
            BleConstant.UUID_SERVER, BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        // 只读的特征值
        mReadChara = BluetoothGattCharacteristic(
            BleConstant.UUID_CHAR_READ,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        // 只写的特征值
        val charaWrite = BluetoothGattCharacteristic(
            BleConstant.UUID_CHAR_WRITE,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        gattService.addCharacteristic(mReadChara) // 将特征值添加到服务里面
        gattService.addCharacteristic(charaWrite) // 将特征值添加到服务里面
        // 开启GATT服务器等待客户端连接
        mGattServer = mBluetoothManager?.openGattServer(this, mGattCallback)
        mGattServer?.addService(gattService) // 向GATT服务器添加指定服务
    }

    private val mGattCallback: BluetoothGattServerCallback =
        object : BluetoothGattServerCallback() {
            // BLE连接的状态发生变化时回调
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int
            ) {
                super.onConnectionStateChange(device, status, newState)

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    mRemoteDevice = device
                    runOnUiThread(Runnable {
                        val desc = String.format(
                            "已连接BLE客户端，对方名称为“%s”，MAC地址为%s",
                            device.name, device.address
                        )
//                        tv_hint.setText(desc)
//                        ll_input.setVisibility(View.VISIBLE)
                    })
                }
            }

            // 收到BLE客户端写入请求时回调
            @SuppressLint("MissingPermission")
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                chara: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(
                    device,
                    requestId,
                    chara,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value
                )
                val message = String(value!!) // 把客户端发来的数据转成字符串
//                Log.d(TAG, "收到了客户端发过来的数据 " + message)
                // 向GATT客户端发送应答，告诉它成功收到了要写入的数据
                mGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    chara.getValue()
                )
                runOnUiThread(Runnable { appendChatMsg(message, false) }) // 往聊天窗口添加聊天消息
            }
        }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        stopAdvertise() // 停止低功耗蓝牙广播
        mGattServer?.close()
    }

    // 发送聊天消息
    private fun sendMessage() {
        val message: String? = ""
        if (TextUtils.isEmpty(message)) {
            Toast.makeText(this, "请先输入聊天消息", Toast.LENGTH_SHORT).show()
            return
        }

//        val msgList: MutableList<String?> = ChatUtil.splitString(message, 20) // 按照20字节切片
//        for (msg in msgList) {
//            mReadChara!!.setValue(msg) // 设置读特征值
//            // 发送本地特征值已更新的通知
//            mGattServer!!.notifyCharacteristicChanged(mRemoteDevice, mReadChara, false)
//        }
        appendChatMsg(message, true) // 往聊天窗口添加聊天消息
    }

    // 往聊天窗口添加聊天消息
    private fun appendChatMsg(content: String?, isSelf: Boolean) {
        appendNowMinute() // 往聊天窗口添加当前时间
        // 把单条消息的线性布局添加到聊天窗口上
    }

    // 往聊天窗口添加当前时间
    private fun appendNowMinute() {

    }
}