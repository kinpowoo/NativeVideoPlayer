package com.jhkj.videoplayer.pages

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cody.bus.ElegantBus
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.adapter.LocalDeviceListAdapter
import com.jhkj.videoplayer.app.BaseActivity
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.databinding.EditConnLayoutBinding
import com.jhkj.videoplayer.third_file_framework.smb_client.MDNSHostnameResolver
import com.jhkj.videoplayer.third_file_framework.smb_client.SMBDevice
import com.jhkj.videoplayer.third_file_framework.smb_client.SMBDeviceScanListener
import com.jhkj.videoplayer.utils.ImmersiveStatusBarUtils
import com.jhkj.videoplayer.utils.LottieDialog
import com.jhkj.videoplayer.utils.Res
import com.jhkj.videoplayer.viewmodels.ConnInfoVm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class ConnectionEditActivity : BaseActivity() {
    private var binding: EditConnLayoutBinding? = null
    private var connDto: ConnInfo? = null
    private var vm: ConnInfoVm? = null  // Activity 作用域
    private var dialog: LottieDialog? = null
    var connType:Int = ConnType.WEBDAV.ordinal
    private var adapter: LocalDeviceListAdapter? = null

    private var resolveHostname: MDNSHostnameResolver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EditConnLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        vm = ViewModelProvider(this)["ConnectionEditActivity", ConnInfoVm::class.java]

        dialog = LottieDialog(this)

        ImmersiveStatusBarUtils.setFullScreen(this,true)
        supportActionBar?.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            connDto = intent.getSerializableExtra("connInfo", ConnInfo::class.java)
        }else{
            connDto = intent.getSerializableExtra("connInfo") as? ConnInfo
        }
        connType = intent.getIntExtra("connType",ConnType.WEBDAV.ordinal)
        resolveHostname = MDNSHostnameResolver(this)
        if(connDto == null && connType != ConnType.WEBDAV.ordinal){
            resolveHostname?.startDiscovery(10000)
            resolveHostname?.setScanListener(smbScanListener)

            adapter = LocalDeviceListAdapter{ dev,idx ->
                connDto?.connType = ConnType.NAS.ordinal
                connDto?.port = dev.port
                connDto?.domain = dev.ip
                connDto?.displayName = dev.serverName
                connDto?.path = ""
                connDto?.protocol = "smb"
                connDto?.username = ""
                connDto?.pass = ""
                loadConnInfo()
            }
            binding?.localDevices?.layoutManager = LinearLayoutManager(this)
            binding?.localDevices?.adapter = adapter
        }

        if(connDto == null){
            binding?.title?.text = Res.string(R.string.add_connection)
            if(connType == ConnType.WEBDAV.ordinal) {
                connDto = ConnInfo(
                    0, "",
                    "", "", "HTTP", 80, "", "",
                    connType
                )
            }else{
                connDto = ConnInfo(
                    0, "",
                    "", "", "SMB", 445, "", "",
                    connType
                )
            }
        }else{
            binding?.title?.text = Res.string(R.string.edit_connection)
            connType = connDto?.connType ?: ConnType.WEBDAV.ordinal
        }
        if(connType == ConnType.WEBDAV.ordinal) {
            binding?.boxProtocol?.visibility = View.VISIBLE
            binding?.tips?.visibility = View.GONE
            binding?.foundTitle?.visibility = View.GONE
        }else{
            binding?.boxProtocol?.visibility = View.GONE
            binding?.tips?.visibility = View.VISIBLE
            binding?.foundTitle?.visibility = View.VISIBLE
        }

        loadConnInfo()
        setListener()
    }

    private fun loadConnInfo(){
        val displayName = connDto?.displayName ?: ""
        binding?.displayInput?.setText(displayName)

        val httpStr = connDto?.protocol ?: "HTTP"
        binding?.protocolHttp?.isChecked = (httpStr == "HTTP")
        binding?.protocolHttps?.isChecked = (httpStr == "HTTPS")

        val domain = connDto?.domain ?: ""
        binding?.hostInput?.setText(domain)

        val path = connDto?.path ?: ""
        binding?.pathInput?.setText(path)

        val port = connDto?.port ?: 80
        binding?.portInput?.setText(port.toString())

        val username = connDto?.username ?: ""
        binding?.usernameInput?.setText(username)
        val pass = connDto?.pass ?: ""
        binding?.passInput?.setText(pass)

        if(TextUtils.isEmpty(path)){
            binding?.fullNameTv?.text = String.format(Locale.US,"%s://%s:%d",
                httpStr.lowercase(),domain,port)
        }else{
            binding?.fullNameTv?.text = String.format(Locale.US,"%s://%s:%d/%s",
                httpStr.lowercase(),domain,port,path)
        }
    }

    private fun updateFullURL(){
        val httpStr = connDto?.protocol ?: "HTTP"
        val domain = connDto?.domain ?: ""
        val path = connDto?.path ?: ""
        val port = connDto?.port ?: 80
        if(TextUtils.isEmpty(path)){
            binding?.fullNameTv?.text = String.format(Locale.US,"%s://%s:%d",
                httpStr.lowercase(),domain,port)
        }else{
            binding?.fullNameTv?.text = String.format(Locale.US,"%s://%s:%d%s",
                httpStr.lowercase(),domain,port,path)
        }
    }


    private fun saveValidate(): Boolean{
        val domain = connDto?.domain ?: ""
//        val path = connDto?.path ?: ""
        if(TextUtils.isEmpty(domain)){
            Toast.makeText(this,R.string.connection_info_not_valid,
                Toast.LENGTH_SHORT).show()
            return false
        }
//        if(TextUtils.isEmpty(path)){
//            Toast.makeText(this,R.string.connection_info_not_valid,
//                Toast.LENGTH_SHORT).show()
//            return false
//        }
        return true
    }

    fun setListener(){
        binding?.displayInput?.addTextChangedListener {
            connDto?.displayName = it?.toString() ?: ""
        }

        binding?.protocolHttp?.setOnClickListener {
            binding?.protocolHttp?.isChecked = true
            binding?.protocolHttps?.isChecked = false
            connDto?.protocol = "HTTP"
            updateFullURL()
        }
        binding?.protocolHttps?.setOnClickListener {
            binding?.protocolHttp?.isChecked = false
            binding?.protocolHttps?.isChecked = true
            connDto?.protocol = "HTTPS"
            updateFullURL()
        }
        binding?.hostInput?.addTextChangedListener {
            connDto?.domain = it?.toString() ?: ""
            updateFullURL()
        }
        binding?.pathInput?.addTextChangedListener {
            connDto?.path = it?.toString() ?: ""
            updateFullURL()
        }
        binding?.portInput?.addTextChangedListener {
            if(connType == ConnType.WEBDAV.ordinal) {
                connDto?.port = (it?.toString() ?: "80").toIntOrNull() ?: 80
            }else{
                connDto?.port = (it?.toString() ?: "445").toIntOrNull() ?: 445
            }
            updateFullURL()
        }
        binding?.usernameInput?.addTextChangedListener {
            connDto?.username = it?.toString() ?: ""
        }
        binding?.passInput?.addTextChangedListener {
            connDto?.pass = it?.toString() ?: ""
        }

        binding?.saveBtn?.setOnClickListener {
            if(isDoubleClick(it))return@setOnClickListener
            if(!saveValidate())return@setOnClickListener
            lifecycleScope.launch(Dispatchers.IO){
                connDto?.let {
                    vm?.insertOrUpdateConn(it){ isSuc ->
                        handler?.runOnUi {
                            hideKeyboard()
//                        dialog?.showSucRes(isSuc)
//                        dialog?.show()
                            if(isSuc){
                                ElegantBus.getDefault("ConnUpdate").post("")
                                showToast(Res.string(R.string.save_success))
                                finishAfterTransition()
                            }else{
                                showToast(Res.string(R.string.save_failed))
                            }
                        }
                    }
                }
            }
        }
    }


    private fun getPort():Int{
        if(connType == ConnType.WEBDAV.ordinal){
            return connDto?.port ?: 80
        }else{
            return connDto?.port ?: 445
        }
    }

    private val smbScanListener = object: SMBDeviceScanListener {
        override fun onDeviceScanned(dev: SMBDevice) {
            runOnUiThread {
                adapter?.addDev(dev)
            }
        }

        override fun onScanFailed(reason: String) {

        }
    }

    override fun onDestroy() {
        resolveHostname?.stopDiscovery()
        vm?.disconnect()
        super.onDestroy()
    }
}