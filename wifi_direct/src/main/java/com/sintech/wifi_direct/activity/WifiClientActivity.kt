package com.sintech.wifi_direct.activity

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
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
import com.sintech.wifi_direct.ImmersiveStatusBarUtils
import com.sintech.wifi_direct.R
import com.sintech.wifi_direct.adapter.WifiDeviceListAdapter
import com.sintech.wifi_direct.databinding.WifiClientLayoutBinding
import com.sintech.wifi_direct.protocol.ClientCallback
import com.sintech.wifi_direct.protocol.FileReceiveCallback
import com.sintech.wifi_direct.service.WiFiDirectClientService
import com.sintech.wifi_direct.util.DiscoveredService
import com.sintech.wifi_direct.util.FileCopyUtil
import com.sintech.wifi_direct.util.FileUtils
import com.sintech.wifi_direct.util.UriToPathUtil
import com.sintech.wifi_direct.util.WiFiDirectDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference
import java.net.InetAddress
import java.net.InetSocketAddress


class WifiClientActivity : AppCompatActivity(), ServiceConnection, ClientCallback,
    FileReceiveCallback {
    private val STORAGE_PERMISSION_REQUEST_CODE: Int = 102

    private var binding: WifiClientLayoutBinding? = null
    private val sb = StringBuilder()
    private var service: WiFiDirectClientService? = null
    private var isClientRunning = false
    private var discover: WiFiDirectDiscovery? = null
    private var deviceAdapter: WifiDeviceListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WifiClientLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this, true)

        setSupportActionBar(binding!!.toolbar)
        binding?.toolbar?.setNavigationOnClickListener {
            finish()
        }

        //绑定服务
        bindService(
            Intent(this, WiFiDirectClientService::class.java),
            this,
            BIND_AUTO_CREATE
        )
        startWiFiDirectService()

        deviceAdapter = WifiDeviceListAdapter {
            if(!TextUtils.isEmpty(it.remoteHost)){
                connectToServer(it.remoteHost)
                runOnUiThread {
                    binding?.scanBox?.visibility = View.GONE
                    binding?.deviceList?.visibility = View.GONE
                }
            }else {
                CoroutineScope(Dispatchers.IO).launch {
                    discover?.connectToDevice(it.device)
                }
            }
            binding?.deviceList?.visibility = View.GONE
            binding?.scanBox?.visibility = View.GONE
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
            discover = WiFiDirectDiscovery(
                WeakReference(this),
                object : WiFiDirectDiscovery.DiscoveryCallback {
                    override fun onWiFiP2pStateChanged(enabled: Boolean) {
                    }

                    override fun onDiscoveryStarted() {
                    }

                    override fun onDiscoveryFailed(reason: Int) {
                    }

                    override fun onPeersDiscovered(peers: List<WifiP2pDevice>?) {

                    }

                    override fun onConnectRequested(device: WifiP2pDevice) {
                        //请求已发送成功，可以开始请求远程服务的IP地址
                        discover?.requestConnectionInfo()
                    }

                    override fun onConnectFailed(reason: String) {
                        //连接失败
                        toast("连接到远程WIFI服务失败:$reason")
                    }

                    override fun onConnectionEstablished(
                        groupOwnerAddress: InetAddress,
                        isGroupOwner: Boolean
                    ) {
                        connectToServer(groupOwnerAddress.hostAddress ?: "")
                        runOnUiThread {
                            binding?.scanBox?.visibility = View.GONE
                            binding?.deviceList?.visibility = View.GONE
                        }
                    }

                    override fun onServiceScan(service: DiscoveredService) {
                        deviceAdapter?.appendDevices(service)
                    }

                    override fun onConnectionChanged(
                        p2pInfo: WifiP2pInfo?,
                        group: WifiP2pGroup?
                    ) {

                    }

                    override fun onThisDeviceChanged(device: WifiP2pDevice?) {

                    }

                    override fun onGroupCreated() {
                    }

                    override fun onGroupCreateFailed(reason: Int) {
                    }

                    override fun onGroupRemoved() {
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
        WiFiDirectClientService.startService(this)
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
            if (it2.resultCode == Activity.RESULT_OK) {
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
                    val filePath = UriToPathUtil.getPathFromUri(this@WifiClientActivity, uri)
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
    fun connectToServer(remoteHost:String) {
        service?.connectToServer(
            this, InetSocketAddress(
                remoteHost, 8888
            ), WeakReference(this), WeakReference(this)
        )
    }

    override fun onConnected() {
        println("Connected to server")
        appendStrAndShow("Connected to server")
    }

    override fun onDisconnected(reason: String?) {
        println("Disconnected: $reason")
        appendStrAndShow("disconnected :$reason")
    }

    override fun onMessageReceived(message: String?) {
        println("Message received: $message")
        appendStrAndShow("msg receive:$message")
    }

    override fun onHeartbeatReceived() {
        // 心跳接收处理
    }

    override fun onHeartbeatAckReceived() {
        // 心跳确认处理
    }

    override fun onFileAckReceived(ack: String?) {
        println("File ack: $ack")
    }

    override fun onFileReceiveStarted(fileId: String, fileName: String?, fileSize: Long) {
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

    override fun onFileChunkReceived(fileId: String, chunkIndex: Int, chunkSize: Int) {
        // 文件分片接收处理
    }

    override fun onFileReceived(fileId: String, fileName: String?, filePath: String) {
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

    override fun onFileTransferError(error: String?) {
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
            Toast.makeText(this@WifiClientActivity, str, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isConnected(): Boolean {
//        toast("未连接到服务器")
        return service?.client?.isConnected ?: false
    }


    // 服务回调
    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        service = (binder as WiFiDirectClientService.SerialBinder).service
        service?.setWeakRef(WeakReference(this@WifiClientActivity))
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.clear()
        menu?.add(0, R.id.scan_btn, 0, "")
            ?.setIcon(R.drawable.ic_brodcast)
            ?.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.scan_btn) {
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
        WiFiDirectClientService.stopService(this)
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