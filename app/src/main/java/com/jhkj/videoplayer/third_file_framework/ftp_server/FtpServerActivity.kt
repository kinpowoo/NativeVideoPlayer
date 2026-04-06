package com.jhkj.videoplayer.third_file_framework.ftp_server

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.app.BaseActivity
import com.jhkj.videoplayer.databinding.SmbServerLayoutBinding
import com.sintech.wifi_direct.ImmersiveStatusBarUtils
import java.util.Locale

class FtpServerActivity : BaseActivity(), ServiceConnection{

    private val STORAGE_PERMISSION_REQUEST_CODE: Int = 102
    private var binding: SmbServerLayoutBinding? = null
    private var isServiceRunning = false
    private var isFtpServiceRunning = false
    private var service: FsService? = null
    private var isRandPass = false
    private var localIpStr = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SmbServerLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this, true)

        supportActionBar?.hide()
        actionBar?.hide()
        binding?.backBtn?.setOnClickListener {
            if(isDoubleClick(it))return@setOnClickListener
            finish()
        }
        //绑定服务
        bindService(
            Intent(this, FsService::class.java),
            this,
            BIND_AUTO_CREATE
        )
        startFtpService()
        localIpStr = getLocalIPAddress()

        binding?.enableRandomPassCb?.setOnCheckedChangeListener { button, isChecked ->
            isRandPass = isChecked
            if(isChecked){
                binding?.editPassBox?.visibility = View.GONE
            }else{
                binding?.editPassBox?.visibility = View.VISIBLE
            }
        }

        binding?.serverToggleBtn?.setOnClickListener{
            if(isDoubleClick(it))return@setOnClickListener
            toggleStartBtn()
        }
    }

    fun toggleStartBtn(){
        if(isFtpServiceRunning){
            service?.stopFtpServer()
            isFtpServiceRunning = false
            binding?.connInfoBox?.visibility = View.GONE
            binding?.configEditBox?.visibility = View.VISIBLE
            binding?.configEditBox?.visibility = View.VISIBLE
            binding?.serverToggleBtn?.text = getString(R.string.start_server)
            binding?.connectTips?.text = getString(R.string.start_server_tips)
        }else{
            if(service == null){
                toast("服务启动失败，稍后再试")
                return
            }
            if(!checkStoragePermission()){
                toast("请授予存储权限")
                return
            }
            val username = "pc"
            val pass = if(isRandPass){
                val numArr = (0..9).shuffled().take(6)
                numArr.joinToString("")
            }else{
                binding?.passEt?.text?.toString() ?: "000000"
            }
            val portStr = binding?.portEt?.text?.toString() ?: ""
            if(TextUtils.isEmpty(pass)){
                toast("密码不能为空")
                return
            }
            if(pass.length<6){
                toast("密码长度不足")
                return
            }

            if(TextUtils.isEmpty(pass)){
                toast("密码不能为空")
                return
            }
            var port = 21
            if(!TextUtils.isEmpty(portStr)){
                if(portStr.length < 4) {
                    toast("端口长度不足")
                    return
                }else{
                    port = portStr.toInt()
                }
            }

            val isSuc = service?.startFtpServer(localIpStr,username,pass,port) ?: false

            if(!isSuc){
                toast("服务启动失败，稍后再试")
            }else{
                isFtpServiceRunning = true
                binding?.urlTv?.text = String.format(Locale.US,
                    "ftp://%s:%d",localIpStr,port)
                binding?.passValue?.text = pass

                binding?.serverToggleBtn?.text = getString(R.string.stop_server)
                binding?.connectTips?.text = getString(R.string.access_from_ftp_client)
                binding?.connInfoBox?.visibility = View.VISIBLE
                binding?.configEditBox?.visibility = View.GONE
                binding?.configEditBox?.visibility = View.GONE
            }
        }
    }

    private fun getLocalIPAddress(): String {
        return try {
            val wifiManager = getSystemService(Context.WIFI_SERVICE) as (WifiManager)
            val wifiInfo = wifiManager.connectionInfo
            val ip = wifiInfo.ipAddress
            "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
        } catch (e: Exception) {
            "localhost"
        }
    }

    fun toast(str:String){
        runOnUiThread {
            Toast.makeText(this@FtpServerActivity,str, Toast.LENGTH_SHORT).show()
        }
    }

    fun checkStoragePermission(): Boolean{
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 检测 MANAGE_EXTERNAL_STORAGE
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        return storagePermission
    }
    fun requestStoragePermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 打开所有文件访问权限页
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = ("package:$packageName").toUri()
                startActivity(intent)
            } catch (e:Exception) {
                // 回退到应用详情页
                openAppDetailsSettings()
            }
        }else{
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                  ),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }
    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package",packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    /**
     * 启动WiFi Direct服务
     */
    private fun startFtpService() {
        // 启动前台服务
        FsService.startService(this)
        isServiceRunning = true
    }

    /**
     * 停止WiFi Direct服务
     */
    private fun stopFtpService() {
        // 停止前台服务
        FsService.stopService(this)
        // 取消Job
//        WiFiDirectJobService.cancelJob(this)
        // 更新UI
        isServiceRunning = false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "需要权限才能传输文件", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // 服务回调
    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        service = (binder as FsService.SerialBinder).service
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }


    override fun onDestroy() {
        unbindService(this)
        stopFtpService()
        super.onDestroy()
    }
}