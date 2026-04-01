package com.sin_tech.ble_manager.ble_tradition.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.sin_tech.ble_manager.databinding.SelectBleHostOrGuestLayoutBinding
import com.sin_tech.ble_manager.utils.BluetoothStatusManager
import com.sin_tech.ble_manager.utils.ImmersiveStatusBarUtils

class SelectBlueHostOrGuest : AppCompatActivity() {
    private var binding: SelectBleHostOrGuestLayoutBinding? = null
    private val PERMISSION_REQUEST_CODE: Int = 100
    private val BATTERY_OPTIMIZATION_REQUEST: Int = 101
    private val REQUEST_ENABLE_BT = 102
    private var bluetoothStatusManager:BluetoothStatusManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SelectBleHostOrGuestLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this, true)

        setSupportActionBar(binding!!.toolbar)
        binding?.toolbar?.setNavigationOnClickListener {
            finish()
        }

        bluetoothStatusManager = BluetoothStatusManager(this)

        binding?.clientBox?.setOnClickListener {
            if(checkPermissions()){
                if(!checkBlueEnabled()){
                    return@setOnClickListener
                }
                startActivity(Intent(this@SelectBlueHostOrGuest, BlueClientActivity::class.java))
            }else{
                checkAndRequestPermissions()
            }
        }

        binding?.serverBox?.setOnClickListener {
            if(checkPermissions()){
                if(!checkBlueEnabled()){
                    return@setOnClickListener
                }
                startActivity(Intent(this@SelectBlueHostOrGuest, BlueServerActivity::class.java))
            }else{
                checkAndRequestPermissions()
            }
        }
    }

    private fun checkBlueEnabled(): Boolean{
        // 检查蓝牙是否开启
        if (bluetoothStatusManager?.isBluetoothEnabled() ?: false) {
            return true
        } else {
            // 请求开启蓝牙
            bluetoothStatusManager?.requestEnableBluetooth(this,
                REQUEST_ENABLE_BT)
        }
        return false
    }



    /**
     * 检查所有必要权限
     */
    private fun checkPermissions(): Boolean {
        var isPermissionGranted = true

        val locationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if(!locationPermission){
            isPermissionGranted = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blueScanPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            if(!blueScanPermission){
                isPermissionGranted = false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val blueConnectPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if(!blueConnectPermission){
                isPermissionGranted = false
            }
            val notificationPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if(!notificationPerm){
                isPermissionGranted = false
            }
        }

        return isPermissionGranted
    }



    private fun checkAndRequestPermissions() {
        val permissionsNeeded: MutableList<String> = ArrayList()

        // Android 12+ 需要精确位置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }else{
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
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
     * 检查电池优化
     */
    private fun checkBatteryOptimization() {
        val powerManager =
            getSystemService(POWER_SERVICE) as PowerManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(packageName)) {

            } else {
                requestBatteryOptimizationExclusion()
            }
        }
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

                // 检查电池优化
                checkBatteryOptimization();
            } else {
                Toast.makeText(this, "需要权限才能运行蓝牙服务", Toast.LENGTH_SHORT).show()
            }
        }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == BATTERY_OPTIMIZATION_REQUEST) {
            checkBatteryOptimization()
        }else if(requestCode == REQUEST_ENABLE_BT){
            if (resultCode == RESULT_OK) {
                checkPermissions()
            } else {
                Toast.makeText(this, "蓝牙未打开", Toast.LENGTH_SHORT).show()
            }
        }
    }

}