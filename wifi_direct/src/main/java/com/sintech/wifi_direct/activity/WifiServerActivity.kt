package com.sintech.wifi_direct.activity

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.sintech.wifi_direct.ImmersiveStatusBarUtils
import com.sintech.wifi_direct.databinding.WifiServerLayoutBinding
import com.sintech.wifi_direct.protocol.FileTransferCallback
import com.sintech.wifi_direct.protocol.ServerCallback
import com.sintech.wifi_direct.service.WiFiDirectJobService
import com.sintech.wifi_direct.service.WiFiDirectForegroundService
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.file.Files


class WifiServerActivity : AppCompatActivity(), ServiceConnection,ServerCallback,FileTransferCallback{
    private val PERMISSION_REQUEST_CODE: Int = 100
    private val BATTERY_OPTIMIZATION_REQUEST: Int = 101

    private var binding: WifiServerLayoutBinding? = null
    private var clientList = mutableListOf<String>()
    private val sb = StringBuilder()
    private var isServiceRunning = false
    private var service: WiFiDirectForegroundService? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WifiServerLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this, true)

        setSupportActionBar(binding!!.toolbar)
        binding?.toolbar?.setNavigationOnClickListener {
            finish()
        }

        binding?.sendBtn?.setOnClickListener {
            val msg = binding?.inputEt?.text?.toString() ?: ""
            clientList.firstOrNull()?.let {
                service?.server?.sendString(it,msg)
            }
            binding?.inputEt?.setText("")
        }

        //绑定服务
        bindService(
            Intent(this, WiFiDirectForegroundService::class.java),
            this,
            BIND_AUTO_CREATE
        )

        if (checkPermissions()) {
            startWiFiDirectService()
        } else {
            checkAndRequestPermissions()
        }
    }

    fun appendStrAndShow(str:String){
        sb.append(str).append("\n")
        runOnUiThread {
            binding?.msgArea?.text = sb.toString()
        }
    }

    fun toast(str:String){
        runOnUiThread {
            Toast.makeText(this@WifiServerActivity,str,Toast.LENGTH_SHORT).show()
        }
    }



    override fun onClientConnected(clientId: String) {
        println("Client connected: $clientId")
        clientList.add(clientId)
        appendStrAndShow("Client connected: $clientId")
    }

    override fun onClientDisconnected(clientId: String, reason: String?) {
        println("Client disconnected: $clientId - $reason")
        clientList.remove(clientId)
        appendStrAndShow("Client disconnected: $clientId - $reason")
    }

    override fun onMessageReceived(clientId: String, message: String?) {
        println("Message from $clientId: $message")
        appendStrAndShow("Message from $clientId: $message")
    }

    override fun onHeartbeatReceived(clientId: String) {
        // 心跳接收处理
    }

    override fun onHeartbeatAckReceived(clientId: String) {
        // 心跳确认处理
    }

    override fun onFileAckReceived(clientId: String, ack: String?) {
        println("File ack from $clientId: $ack")
        appendStrAndShow("File ack from $clientId: $ack")
    }

    override fun onFileTransferStarted(
        clientId: String?, fileId: String?,
        fileName: String?, fileSize: Long
    ) {

    }

    override fun onFileChunkReceived(
        clientId: String?, fileId: String?,
        chunkIndex: Int, chunkSize: Int
    ) {
        // 文件分片接收处理
    }

    override fun onFileTransferCompleted(
        clientId: String?, fileId: String?,
        fileName: String?, fileData: ByteArray
    ) {
        // 保存文件
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val file = File("received_$fileName")
                Files.write(file.toPath(), fileData)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onFileTransferError(clientId: String?, fileId: String?, error: String?) {
        System.err.println("File transfer error: $error")
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded: MutableList<String?> = ArrayList<String?>()
        // 位置权限（WiFi Direct需要）
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }


        // Android 12+ 需要精确位置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_BOOT_COMPLETED
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.RECEIVE_BOOT_COMPLETED)
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray<String?>(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * 检查所有必要权限
     */
    private fun checkPermissions(): Boolean {
        var isPermissionGranted = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val locationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            if(!locationPermission){
                isPermissionGranted = false
            }
        } else {
            val locationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if(!locationPermission){
                isPermissionGranted = false
            }
        }
        val bootPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
        ) == PackageManager.PERMISSION_GRANTED
        if(!bootPermission){
            isPermissionGranted = false
        }
        return isPermissionGranted
    }

    /**
     * 检查电池优化
     */
    private fun checkBatteryOptimization() {
        val powerManager =
            getSystemService(POWER_SERVICE) as PowerManager?
        if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(packageName)) {

        }else{
            requestBatteryOptimizationExclusion()
        }
    }


    /**
     * 启动WiFi Direct服务
     */
    private fun startWiFiDirectService() {
        // 启动前台服务
        WiFiDirectForegroundService.startService(this)
        // 调度Job（Android 5.0+）
//        WiFiDirectJobService.scheduleJob(this)
        // 更新UI
        isServiceRunning = true
        Toast.makeText(this, "WiFi Direct服务已启动", Toast.LENGTH_SHORT).show()
    }

    /**
     * 停止WiFi Direct服务
     */
    private fun stopWiFiDirectService() {
        // 停止前台服务
        WiFiDirectForegroundService.stopService(this)
        // 取消Job
//        WiFiDirectJobService.cancelJob(this)
        // 更新UI
        isServiceRunning = false
        Toast.makeText(this, "WiFi Direct服务已停止", Toast.LENGTH_SHORT).show()
    }


    /**
     * 请求电池优化白名单
     */
    private fun requestBatteryOptimizationExclusion() {
        val intent = Intent()
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = ("package:$packageName").toUri()

        try {
            startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            if (allGranted) {
                Toast.makeText(this, "权限已获取", Toast.LENGTH_SHORT).show()
                startWiFiDirectService()

                // 检查电池优化
                checkBatteryOptimization();
            } else {
                Toast.makeText(this, "需要权限才能运行WiFi Direct服务", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == BATTERY_OPTIMIZATION_REQUEST) {
            checkBatteryOptimization()
        }
    }



    // 服务回调
    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        service = (binder as WiFiDirectForegroundService.SerialBinder).service
        service?.setWeakRef(WeakReference(this@WifiServerActivity))
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }


    override fun onDestroy() {
        unbindService(this)
        stopWiFiDirectService()
        super.onDestroy()
    }
}