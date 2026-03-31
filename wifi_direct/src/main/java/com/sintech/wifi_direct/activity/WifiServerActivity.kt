package com.sintech.wifi_direct.activity

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.sintech.wifi_direct.ImmersiveStatusBarUtils
import com.sintech.wifi_direct.databinding.WifiServerLayoutBinding
import com.sintech.wifi_direct.protocol.FileTransferCallback
import com.sintech.wifi_direct.protocol.ServerCallback
import com.sintech.wifi_direct.service.WiFiDirectForegroundService
import com.sintech.wifi_direct.util.FileCopyUtil
import com.sintech.wifi_direct.util.FileMover
import com.sintech.wifi_direct.util.FileUtils
import com.sintech.wifi_direct.util.P2pServiceManager
import com.sintech.wifi_direct.util.UriToPathUtil
import java.io.File
import java.lang.ref.WeakReference


class WifiServerActivity : AppCompatActivity(), ServiceConnection,ServerCallback,FileTransferCallback{

    private val STORAGE_PERMISSION_REQUEST_CODE: Int = 102
    private var binding: WifiServerLayoutBinding? = null
    private var clientList = mutableListOf<String>()
    private val sb = StringBuilder()
    private var isServiceRunning = false
    private var service: WiFiDirectForegroundService? = null
    private var p2pManger: P2pServiceManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WifiServerLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this, true)

        setSupportActionBar(binding!!.toolbar)
        binding?.toolbar?.setNavigationOnClickListener {
            finish()
        }

        binding?.msgArea?.movementMethod = ScrollingMovementMethod.getInstance()
        binding?.msgArea?.setOnClickListener {
            binding?.fileType?.visibility = View.GONE
        }
        binding?.sendBtn?.setOnClickListener {
            val msg = binding?.inputEt?.text?.toString() ?: ""
            clientList.firstOrNull()?.let {
                service?.server?.sendString(it,msg)
            }
            binding?.inputEt?.setText("")
        }

        p2pManger = P2pServiceManager(this, p2pListener)
        //绑定服务
        bindService(
            Intent(this, WiFiDirectForegroundService::class.java),
            this,
            BIND_AUTO_CREATE
        )
        startWiFiDirectService()

        binding?.pickFileBtn?.setOnClickListener {
            if(checkStoragePermission()) {
                binding?.fileType?.visibility = View.VISIBLE
            }else{
                AlertDialog.Builder(this)
                    .setMessage("需要授予存储访问权限")
                    .setPositiveButton("授予") { which, dialog ->
                        requestStoragePermission()
                    }
                    .setNegativeButton("拒绝") { which, dialog ->
                    }
                    .create().show()
            }
        }
        binding?.imageType?.setOnClickListener {
            binding?.fileType?.visibility = View.GONE
            initPickImg("image/*")
        }
        binding?.videoType?.setOnClickListener {
            binding?.fileType?.visibility = View.GONE
            initPickImg("video/*")
        }
        binding?.fileType?.setOnClickListener {
            openDocument()
            binding?.fileType?.visibility = View.GONE
        }
    }


    private val p2pListener = object: P2pServiceManager.ServiceStatusListener {
        override fun onError(error: String?) {
        }

        override fun onServiceStarted(port: Int) {
        }

        override fun onServiceStopped() {
        }
    }

    private fun initPickImg(mimeType:String){
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        photoOrVideoSelectIntent.launch(intent)
//        if(checkStoragePermission()) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                openAlbum13()
//            } else {
//                openAlbum()
//            }
//        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun openAlbum13(){
        val intent = Intent(MediaStore.ACTION_PICK_IMAGES)
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,1)
        photoOrVideoSelectIntent.launch(intent)
    }

    private fun openDocument(){
        //大于9.0系统，采用Documents方式获取uri
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            photoOrVideoSelectIntent.launch(intent)
        }else{
            val intent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            photoOrVideoSelectIntent.launch(intent)
        }
    }

    private fun openAlbum(){
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        // 允许多选（可选）
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        photoOrVideoSelectIntent.launch(intent)
//        startActivityForResult(Intent.createChooser(intent, "选择媒体"), 1001)
    }

    private val photoOrVideoSelectIntent: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { it2 ->
            if (it2.resultCode == Activity.RESULT_OK) {
                val photoUri = it2.data?.data
                photoUri?.let { uri ->
                    // 获取持久化权限
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }catch (e:SecurityException){
                            e.printStackTrace()
                        }
                    }
                    val filePath = UriToPathUtil.getPathFromUri(this@WifiServerActivity,uri)
                    filePath?.let { path ->
                        clientList.firstOrNull()?.let { clientId ->
                            val newFile = File(path)
                            if(newFile.exists()) {
                                service?.server?.sendFile(clientId, newFile)
                            }
                        }
                    }
                }
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
        appendStrAndShow("Client connected")
    }

    override fun onClientDisconnected(clientId: String, reason: String?) {
        println("Client disconnected: $reason")
        clientList.remove(clientId)
        appendStrAndShow("Client disconnected: $reason")
    }

    override fun onMessageReceived(clientId: String, message: String?) {
        println("receive: $message")
        appendStrAndShow("Receive Message: $message")
    }

    override fun onHeartbeatReceived(clientId: String) {
        // 心跳接收处理
    }

    override fun onHeartbeatAckReceived(clientId: String) {
        // 心跳确认处理
    }

    override fun onFileAckReceived(clientId: String, ack: String?) {
        println("File ack from $clientId: $ack")
//        appendStrAndShow("File ack from $clientId: $ack")
    }

    override fun onFileTransferStarted(
        clientId: String?, fileId: String?,
        fileName: String?, fileSize: Long
    ) {
        appendStrAndShow("Receive file: $fileName")
        if(!checkStoragePermission()) {
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("新文件接收提醒")
                    .setMessage("接收文件需授予存储访问权限")
                    .setPositiveButton("授予") { which, dialog ->
                        requestStoragePermission()
                    }
                    .setNegativeButton("拒绝") { which, dialog ->
                        Toast.makeText(this, "需要存储权限才能传输文件", Toast.LENGTH_SHORT).show()
                    }
                    .create().show()
            }
        }
    }

    override fun onFileChunkReceived(
        clientId: String?, fileId: String?,
        chunkIndex: Int, chunkSize: Int
    ) {
        // 文件分片接收处理
//        appendStrAndShow("接收文件下标: $chunkIndex , size:$chunkSize")
    }

    override fun onFileTransferCompleted(
        clientId: String?, fileId: String?,
        fileName: String, filePath: String
    ) {
        // 保存文件
        appendStrAndShow("receive file complete: $fileName")
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
           val srcFile = File(filePath)
           if(srcFile.exists()) {
               val mimeType = UriToPathUtil.getFileType(srcFile)
               val uri = UriToPathUtil.genValues(this, fileName, mimeType,
                   "WiFiDirect")
               val isSuc = UriToPathUtil.saveFileToUri(this,srcFile,uri)
               if(isSuc){
                   srcFile.delete();
               }
           }
        } else {
           if(checkStoragePermission()) {
               val downloadDir = FileUtils.getDownloadDir()
               if (downloadDir != null) {
                   val destFile = File(downloadDir, fileName)
                   FileCopyUtil.copyFileByStream(File(filePath),destFile)
//                FileUtils.moveFile(filePath, destFile)
               }
           }
        }
    }

    override fun onFileTransferError(clientId: String?, fileId: String?, error: String?) {
        System.err.println("File transfer error: $error")
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
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                  ),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }
    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package",packageName, null)
        intent.setData(uri)
        startActivity(intent)
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
        service = (binder as WiFiDirectForegroundService.SerialBinder).service
        service?.setWeakRef(WeakReference(this@WifiServerActivity))
        p2pManger?.startService()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
        p2pManger?.stopService()
    }


    override fun onDestroy() {
        unbindService(this)
        stopWiFiDirectService()
        p2pManger?.cleanup()
        super.onDestroy()
    }
}