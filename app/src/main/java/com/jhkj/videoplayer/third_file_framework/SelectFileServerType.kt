package com.jhkj.videoplayer.third_file_framework

import com.sin_tech.ble_manager.ble_tradition.activity.BlueClientActivity
import com.sin_tech.ble_manager.ble_tradition.activity.BlueServerActivity


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.jhkj.videoplayer.databinding.SelectServerTypeLayoutBinding
import com.jhkj.videoplayer.third_file_framework.ftp_server.FtpServerActivity
import com.jhkj.videoplayer.third_file_framework.smb_server.SmbServerActivity
import com.sin_tech.ble_manager.utils.ImmersiveStatusBarUtils

class SelectFileServerType : AppCompatActivity() {
    private var binding: SelectServerTypeLayoutBinding? = null
    private val PERMISSION_REQUEST_CODE: Int = 100
    private val BATTERY_OPTIMIZATION_REQUEST: Int = 101
    private val STORAGE_PERMISSION_REQUEST_CODE = 102


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SelectServerTypeLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this, true)

        setSupportActionBar(binding!!.toolbar)
        binding?.toolbar?.setNavigationOnClickListener {
            finish()
        }

        binding?.SMBBox?.setOnClickListener {
            if(checkPermissions()){
                startActivity(Intent(this, SmbServerActivity::class.java))
            }else{
                checkAndRequestPermissions()
            }
        }

        binding?.FTPBox?.setOnClickListener {
            if(checkPermissions()){
                startActivity(Intent(this, FtpServerActivity::class.java))
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

        val isStoragePermit = checkStoragePermission()
        if(!isStoragePermit)return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

    fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 检测 MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager()
        } else {
            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }


    private fun checkAndRequestPermissions() {
        val permissionsNeeded: MutableList<String> = ArrayList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        }else{
            requestStoragePermission()
        }
    }

    private fun requestStoragePermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 打开所有文件访问权限页
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = ("package:$packageName").toUri()
                startActivity(intent)
            } catch (e: Exception) {
                // 回退到应用详情页
                openAppDetailsSettings()
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
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
                // 检查电池优化
                requestStoragePermission()
            } else {
                Toast.makeText(this, "需要通頟权限才能运行服务", Toast.LENGTH_SHORT).show()
            }
        }else if(requestCode == STORAGE_PERMISSION_REQUEST_CODE){
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            if (allGranted) {
                checkBatteryOptimization()
            } else {
                Toast.makeText(this, "需要存储权限才能运行服务", Toast.LENGTH_SHORT).show()
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