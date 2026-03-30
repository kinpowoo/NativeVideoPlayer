package com.sintech.wifi_direct.activity

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
import com.sintech.wifi_direct.ImmersiveStatusBarUtils
import com.sintech.wifi_direct.databinding.SelectHostOrGuestLayoutBinding

class SelectHostOrGuest : AppCompatActivity() {
    private var binding: SelectHostOrGuestLayoutBinding? = null
    private val PERMISSION_REQUEST_CODE: Int = 100
    private val BATTERY_OPTIMIZATION_REQUEST: Int = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SelectHostOrGuestLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this, true)

        setSupportActionBar(binding!!.toolbar)
        binding?.toolbar?.setNavigationOnClickListener {
            finish()
        }

        binding?.clientBox?.setOnClickListener {
            if(checkPermissions()){
                startActivity(Intent(this@SelectHostOrGuest, WifiClientActivity::class.java))
            }else{
                checkAndRequestPermissions()
            }
        }

        binding?.serverBox?.setOnClickListener {
            if(checkPermissions()){
                startActivity(Intent(this@SelectHostOrGuest, WifiServerActivity::class.java))
            }else{
                checkAndRequestPermissions()
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearbyPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            if(!nearbyPerm){
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES)
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
        if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(packageName)) {

        }else{
            requestBatteryOptimizationExclusion()
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


}