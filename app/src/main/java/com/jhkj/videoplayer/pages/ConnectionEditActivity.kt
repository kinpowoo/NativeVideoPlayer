package com.jhkj.videoplayer.pages

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.app.BaseActivity
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.databinding.EditConnLayoutBinding
import com.jhkj.videoplayer.utils.ImmersiveStatusBarUtils
import com.jhkj.videoplayer.utils.LottieDialog
import com.jhkj.videoplayer.utils.Res
import com.jhkj.videoplayer.viewmodels.ConnInfoVm
import java.util.Locale

class ConnectionEditActivity : BaseActivity() {
    private var binding: EditConnLayoutBinding? = null
    private var connDto: ConnInfo? = null
    private var vm: ConnInfoVm? = null  // Activity 作用域
    private var dialog: LottieDialog? = null

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
        if(connDto == null){
            binding?.title?.text = Res.string(R.string.add_connection)
        }else{
            binding?.title?.text = Res.string(R.string.edit_connection)
        }
        if(connDto == null) {
            connDto = ConnInfo(0,"",
                "","","http",80,"","",
                ConnType.WEBDAV.ordinal)
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

        if(!TextUtils.isEmpty(path)){
            binding?.fullNameTv?.text = String.format(Locale.US,"%s://%s:%d",
                httpStr.lowercase(),domain,port)
        }else{
            binding?.fullNameTv?.text = String.format(Locale.US,"%s://%s:%d/%s",
                httpStr.lowercase(),domain,port,path)
        }
    }

    fun setListener(){
        binding?.displayInput?.addTextChangedListener {
            connDto?.displayName = it?.toString() ?: ""
        }

        binding?.protocolHttp?.setOnClickListener {
            binding?.protocolHttp?.isChecked = true
            binding?.protocolHttps?.isChecked = false
            connDto?.protocol = "HTTP"
        }
        binding?.protocolHttps?.setOnClickListener {
            binding?.protocolHttp?.isChecked = false
            binding?.protocolHttps?.isChecked = true
            connDto?.protocol = "HTTPS"
        }
        binding?.hostInput?.addTextChangedListener {
            connDto?.domain = it?.toString() ?: ""
        }
        binding?.pathInput?.addTextChangedListener {
            connDto?.path = it?.toString() ?: ""
        }
        binding?.portInput?.addTextChangedListener {
            connDto?.port = (it?.toString() ?: "80").toIntOrNull() ?: 80
        }
        binding?.usernameInput?.addTextChangedListener {
            connDto?.username = it?.toString() ?: ""
        }
        binding?.passInput?.addTextChangedListener {
            connDto?.pass = it?.toString() ?: ""
        }

        binding?.saveBtn?.setOnClickListener {
            if(isDoubleClick(it))return@setOnClickListener
            connDto?.let {
                vm?.insertOrUpdateConn(it){ isSuc ->
                    handler?.runOnUi {
                        hideKeyboard()
//                        dialog?.showSucRes(isSuc)
//                        dialog?.show()
                        if(isSuc){
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

    override fun onDestroy() {
        vm?.disconnect()
        super.onDestroy()
    }
}