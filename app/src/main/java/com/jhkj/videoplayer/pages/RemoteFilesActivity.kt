package com.jhkj.videoplayer.pages

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.jhkj.videoplayer.adapter.FileInfoListAdapter
import com.jhkj.videoplayer.app.BaseActivity
import com.jhkj.videoplayer.components.LoadingDialog
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.compose_pages.models.FileType
import com.jhkj.videoplayer.databinding.RemoteFilesLayoutBinding
import com.jhkj.videoplayer.utils.ImmersiveStatusBarUtils
import com.jhkj.videoplayer.utils.file_recursive.FileItem
import com.jhkj.videoplayer.viewmodels.RemoteProvider
import com.jhkj.videoplayer.viewmodels.WebdavViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack

class RemoteFilesActivity: BaseActivity() {
    private var binding: RemoteFilesLayoutBinding? = null
    private var fileAdapter: FileInfoListAdapter? = null
    private var recursivePath = Stack<String>()
    private var remoteVM: RemoteProvider? = null
    private var connDto: ConnInfo? = null
    private var loadingDialog: LoadingDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RemoteFilesLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            connDto = intent.getSerializableExtra("connInfo", ConnInfo::class.java)
        }else{
            connDto = intent.getSerializableExtra("connInfo") as? ConnInfo
        }

        connDto?.let{ conn ->
            if(conn.connType == ConnType.WEBDAV.ordinal) {
                remoteVM = ViewModelProvider(this)[this.javaClass.name, WebdavViewModel::class.java]
            }
        }
        loadingDialog = LoadingDialog(this)


//        ImmersiveStatusBarUtils.setImmersiveStatusBar(this)
        ImmersiveStatusBarUtils.setFullScreen(this, true)
        supportActionBar?.hide()


        val callback = object : OnBackPressedCallback(
                true // default to enabled
        ) {
            override fun handleOnBackPressed() {
                onBackPressClick()
            }
        }
        onBackPressedDispatcher.addCallback(
                this, // LifecycleOwner
                callback
        )

        fileAdapter = FileInfoListAdapter { item, idx ->
            openFile(item)
            checkBackIcon()
        }
        binding?.fileList?.layoutManager = GridLayoutManager(this, 3)
        binding?.fileList?.adapter = fileAdapter
        binding?.backBtn?.setOnClickListener {
            onBackPressClick()
        }

        initPath()
    }

    private fun initPath(){
        val initPath = connDto?.path ?: ""
        if(!TextUtils.isEmpty(initPath)){
            val pathsArr = initPath.split("/")
            recursivePath.push("/")  //根目录
            pathsArr.forEach {
                if(!TextUtils.isEmpty(it)) {
                    recursivePath.push(it)
                }
            }
        }else {
            //初始化块
            recursivePath.push("/")  //根目录
        }
        relistDir()
        checkBackIcon()
    }

    private fun relistDir(){
        var subPath = ""
        recursivePath.forEachIndexed {idx,item ->
            if(idx == 0) {
                subPath += item
            }else{
                subPath += "$item/"
            }
        }
        loadingDialog?.show()
        lifecycleScope.launch(Dispatchers.IO) {
            val files:List<FileItem>? = if(connDto != null){
                remoteVM?.listFiles(connDto!!,subPath)
            }else null
            withContext(Dispatchers.Main) {
                loadingDialog?.dismiss()
                fileAdapter?.addFiles(files)
            }
        }
    }

    fun checkBackIcon(){
        if(isRoot()){
            val displayName = connDto?.displayName ?: ""
            binding?.title?.text = displayName
        }else{
            val peek = recursivePath.peek()
            binding?.title?.text = peek
        }
    }


    override fun onBackPressed() {
        super.onBackPressed()
        onBackPressClick()
    }


    fun isRoot(): Boolean{
        return recursivePath.size == 1
    }


    fun openFile(fileInfo: FileItem){
        //点击跳转到下一级目录
        if (fileInfo.isDirectory) {
            recursivePath.push(fileInfo.fileName)
            relistDir()
        } else {
            FileType.doLocalFileOpenAction(this, fileInfo)
        }
    }

    fun popupDir(){
        if(recursivePath.size > 1) {
            recursivePath.pop()
            relistDir()
        }
    }

    fun onBackPressClick() {
        if(isRoot()){
            finish()
        }else {
            popupDir()
            checkBackIcon()
        }
    }
}