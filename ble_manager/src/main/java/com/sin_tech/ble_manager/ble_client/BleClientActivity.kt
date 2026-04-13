package com.sin_tech.ble_manager.ble_client

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.sin_tech.ble_manager.R
import com.sin_tech.ble_manager.ble_tradition.activity.BlueDeviceListAdapter
import com.sin_tech.ble_manager.ble_tradition.protocol.ClientCallback
import com.sin_tech.ble_manager.ble_tradition.protocol.FileReceiveCallback
import com.sin_tech.ble_manager.ble_tradition.service.BlueDirectDiscovery
import com.sin_tech.ble_manager.databinding.BlueClientLayoutBinding
import com.sin_tech.ble_manager.models.BleDevice
import com.sin_tech.ble_manager.utils.FileCopyUtil
import com.sin_tech.ble_manager.utils.FileUtils
import com.sin_tech.ble_manager.utils.ImmersiveStatusBarUtils
import com.sin_tech.ble_manager.utils.UriToPathUtil
import java.io.File
import java.lang.ref.WeakReference

class BleClientActivity : AppCompatActivity(), ServiceConnection, ClientCallback,
    FileReceiveCallback {
    private val STORAGE_PERMISSION_REQUEST_CODE: Int = 102

    private var binding: BlueClientLayoutBinding? = null
    private val sb = StringBuilder()
    private var service: BleClientService? = null
    private var isClientRunning = false
    private var discover: BlueDirectDiscovery? = null
    private var deviceAdapter: BlueDeviceListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BlueClientLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this, true)

        setSupportActionBar(binding!!.toolbar)
        binding?.toolbar?.setNavigationOnClickListener {
            finish()
        }

        //绑定服务
        bindService(
            Intent(this, BleClientService::class.java),
            this,
            BIND_AUTO_CREATE
        )
        startWiFiDirectService()

        deviceAdapter = BlueDeviceListAdapter {
            connectToServer(it)
            runOnUiThread {
                binding?.scanBox?.visibility = View.GONE
                binding?.deviceList?.visibility = View.GONE
            }
        }
        binding?.deviceList?.layoutManager = LinearLayoutManager(this)
        binding?.deviceList?.adapter = deviceAdapter


        initWifiDiscovery()

        binding?.msgArea?.movementMethod = ScrollingMovementMethod.getInstance()
        binding?.msgArea?.setOnClickListener {
            binding?.fileType?.visibility = View.GONE
        }
        binding?.sendBtn?.setOnClickListener {
            val msg = binding?.inputEt?.text?.toString() ?: ""
            if (isConnected()) {
                service?.client?.sendString(msg)
            }
            binding?.inputEt?.setText("")
        }

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

    private fun initWifiDiscovery() {
        if (discover == null) {
            discover = BlueDirectDiscovery(
                WeakReference(this),
                object : BlueDirectDiscovery.BlueDiscoveryCallback {
                    override fun onDeviceFound(dev: BleDevice) {
                        deviceAdapter?.appendDevice(dev)
                    }

                    override fun onScanEnd() {
                    }

                    override fun onScanStart() {
                    }
                })
        }
        discover?.stopDiscovery()
        discover?.startDiscovery()
        binding?.deviceList?.visibility = View.VISIBLE
        binding?.scanBox?.visibility = View.VISIBLE
    }

    /**
     * 启动WiFi Direct服务
     */
    private fun startWiFiDirectService() {
        // 启动前台服务
        BleClientService.startService(this)
        // 调度Job（Android 5.0+）
//        WiFiDirectJobService.scheduleJob(this)
        // 更新UI
        isClientRunning = true
    }


    private fun initPickImg(mimeType: String) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
    private fun openAlbum13() {
        val intent = Intent(MediaStore.ACTION_PICK_IMAGES)
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 1)
        photoOrVideoSelectIntent.launch(intent)
    }

    private fun openDocument() {
        //大于9.0系统，采用Documents方式获取uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            photoOrVideoSelectIntent.launch(intent)
        } else {
            val intent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            photoOrVideoSelectIntent.launch(intent)
        }
    }

    private fun openAlbum() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        // 允许多选（可选）
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        photoOrVideoSelectIntent.launch(intent)
//        startActivityForResult(Intent.createChooser(intent, "选择媒体"), 1001)
    }

    private val photoOrVideoSelectIntent: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { it2 ->
            if (it2.resultCode == RESULT_OK) {
                val photoUri = it2.data?.data
                photoUri?.let { uri ->
                    // 获取持久化权限
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                        }
                    }
                    val filePath = UriToPathUtil.getPathFromUri(this@BleClientActivity, uri)
                    filePath?.let { path ->
                        val newFile = File(path)
                        if (newFile.exists()) {
                            Thread {
                                service?.client?.sendFile(newFile)
                            }.start()
                        }
                    }
                }
            }
        }


    //连接到服务器
    fun connectToServer(dev: BleDevice) {
        service?.connectToServer(
            this, dev, WeakReference(this), WeakReference(this)
        )
    }

    override fun onConnected(clientId:String) {
        println("Connected to server")
        appendStrAndShow("Connected to server")
    }

    override fun onDisconnected(clientId:String,reason: String?) {
        println("Disconnected: $reason")
        appendStrAndShow("disconnected :$reason")
    }

    override fun onMessageReceived(clientId:String,message: String?) {
        println("Message received: $message")
        appendStrAndShow("msg receive:$message")
    }

    override fun onHeartbeatReceived(clientId:String) {
        // 心跳接收处理
    }

    override fun onHeartbeatAckReceived(clientId:String) {
        // 心跳确认处理
    }

    override fun onFileAckReceived(clientId:String,ack: String?) {
        println("File ack: $ack")
    }

    override fun onFileReceiveStarted(clientId:String,fileId: String, fileName: String?, fileSize: Long) {
        appendStrAndShow("receive file : $fileName")
        if (!checkStoragePermission()) {
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

    override fun onFileChunkReceived(clientId:String,fileId: String, chunkIndex: Int, chunkSize: Int) {
        // 文件分片接收处理
    }

    override fun onFileReceived(clientId:String,fileId: String, fileName: String?, filePath: String) {
        appendStrAndShow("receive file complete: $fileName")
        // 保存文件
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

    override fun onFileTransferError(clientId:String,error: String?) {
        System.err.println("File transfer error: $error")
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

    fun requestStoragePermission() {
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
                Toast.makeText(this, "需要存储权限才能传输文件", Toast.LENGTH_SHORT).show()
            }
        }
    }


    fun appendStrAndShow(str: String) {
        sb.append(str).append("\n")
        runOnUiThread {
            binding?.msgArea?.text = sb.toString()
        }
    }

    fun toast(str: String) {
        runOnUiThread {
            Toast.makeText(this@BleClientActivity, str, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isConnected(): Boolean {
//        toast("未连接到服务器")
        return service?.client?.isConnected ?: false
    }


    // 服务回调
    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        service = (binder as BleClientService.SerialBinder).service
        service?.setWeakRef(WeakReference(this@BleClientActivity))
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.clear()
        menu?.add(0, R.id.scan_blue_btn, 0, "")
            ?.setIcon(R.drawable.blue_client)
            ?.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.scan_blue_btn) {
            deviceAdapter?.cleanDevices()

            val wiFiDirectClient = service?.client
            wiFiDirectClient?.disconnect()
            initWifiDiscovery()
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * 停止WiFi Direct服务
     */
    private fun stopWiFiDirectService() {
        // 停止前台服务
        BleClientService.stopService(this)
        // 取消Job
//        WiFiDirectJobService.cancelJob(this)
        // 更新UI
        isClientRunning = false
    }

    override fun onDestroy() {
        discover?.stopDiscovery()
        discover?.cleanup()
        discover = null
        unbindService(this)
        stopWiFiDirectService()
        super.onDestroy()
    }
}