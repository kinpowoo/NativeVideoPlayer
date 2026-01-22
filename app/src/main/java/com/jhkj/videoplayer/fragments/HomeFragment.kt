package com.jhkj.videoplayer.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.adapter.FileInfoListAdapter
import com.jhkj.videoplayer.compose_pages.ComDialog
import com.jhkj.videoplayer.compose_pages.models.FileType
import com.jhkj.videoplayer.databinding.HomeFragmentLayoutBinding
import com.jhkj.videoplayer.utils.PermissionTool
import com.jhkj.videoplayer.utils.file_recursive.FileItem
import com.jhkj.videoplayer.utils.file_recursive.FileTreeFactory
import com.jhkj.videoplayer.viewmodels.HomeFragmentVm
import java.util.Stack

class HomeFragment : VisibilityFragment() {
    private var binding: HomeFragmentLayoutBinding? = null
    private lateinit var vm: HomeFragmentVm
    private var permissionStr = ""
    private var isPermissionGranted = false

    private var factory: FileTreeFactory? = null
    private var fileAdapter: FileInfoListAdapter? = null

    private var filePathStack = Stack<FileItem>()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = HomeFragmentLayoutBinding.inflate(inflater, container, false)
        vm = ViewModelProvider(requireActivity())[this.javaClass.name, HomeFragmentVm::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissionStr = Manifest.permission.MANAGE_EXTERNAL_STORAGE
        } else {
            permissionStr = Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

        isPermissionGranted = checkStoragePermission()
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileAdapter = FileInfoListAdapter{ item,idx ->
            openFile(item)
            checkBackIcon()
        }
        binding?.fileList?.layoutManager = GridLayoutManager(requireContext(),3)
        binding?.fileList?.adapter = fileAdapter
        binding?.appIcon?.setOnClickListener {
            popupDir()
            checkBackIcon()
        }

        binding?.goToSettings?.setOnClickListener {
            if (isDoubleClick(it)) return@setOnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                goToSettingsAlert()
            }else{
                val isDenied = PermissionTool.checkPermissionNeverAsk(
                    requireActivity(),
                    permissionStr
                )
                if (isDenied) {
                    goToSettingsAlert()
                } else {
                    showReqPermissionDialog()
                }
            }
        }

        if(isPermissionGranted){
            createFactoryAndList()
        }
    }


    override fun onResume() {
        super.onResume()
        isPermissionGranted = checkStoragePermission()
        if(isPermissionGranted){
            binding?.storageAlertBox?.visibility = View.GONE
            if(factory == null){
                createFactoryAndList()
            }
        }else {
            val isDenied = PermissionTool.checkPermissionNeverAsk(
                requireActivity(),
                permissionStr
            )
            if (isDenied) {
                binding?.goToSettings?.setText(R.string.go_to_settings)
            } else {
                binding?.goToSettings?.setText(R.string.grant)
            }
            binding?.storageAlertBox?.visibility = View.VISIBLE
        }
    }

    //创建目录结构
    fun createFactoryAndList(){
        val externFile = Environment.getExternalStorageDirectory()
        factory = FileTreeFactory(externFile.absolutePath)
        val childFiles = factory?.listRoot()
        childFiles?.let {
            fileAdapter?.addFiles(it)
        }
    }

    fun isRoot(): Boolean{
        return filePathStack.isEmpty()
    }

    fun checkBackIcon(){
        if(isRoot()){
            binding?.appIcon?.setImageResource(R.drawable.native_play_logo)
            binding?.title?.setText(R.string.my_files)
        }else{
            binding?.appIcon?.setImageResource(R.drawable.ic_back_btn)
            val peek = filePathStack.peek()
            binding?.title?.text = peek.fileName
        }
    }

    fun openFile(fileInfo: FileItem){
        if(fileInfo.isDirectory){
            filePathStack.push(fileInfo)
            val childFiles = factory?.listFiles(fileInfo)
            childFiles?.let {
                fileAdapter?.addFiles(it)
            }
        }else{
            //打开文件
            FileType.doLocalFileOpenAction(requireContext(), fileInfo)
        }
    }

    fun popupDir(){
        if(filePathStack.isNotEmpty()) {
            filePathStack.pop()
        }
        if(!isRoot()) {
            val peekFile = filePathStack.peek()
            val childFiles = factory?.listFiles(peekFile)
            childFiles?.let {
                fileAdapter?.addFiles(it)
            }
        }else{
            val childFiles = factory?.listRoot()
            childFiles?.let {
                fileAdapter?.addFiles(it)
            }
        }
    }


    override fun onBackPressClick() {
        popupDir()
        checkBackIcon()
    }

    fun goToSettingsAlert() {
        ComDialog.Builder(requireContext())
            .setMessage(R.string.enable_storage_alert)
            .setPositiveButton(R.string.go_to_settings) {
                openStoragePermissionSettings()
            }
            .setNegativeButton(R.string.cancel) {
            }
            .create().show()
    }

    // 检查存储权限
    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 检测 MANAGE_EXTERNAL_STORAGE
            Environment.isExternalStorageManager()
        } else {
            // Android 6.0-10 检测读写权限
            val writeGranted = ContextCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            val readGranted = ContextCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            writeGranted && readGranted
        }
    }

    private fun openStoragePermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 打开所有文件访问权限页
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.setData(("package:" + requireContext().packageName).toUri())
                startActivity(intent)
            } catch (e:Exception) {
                // 回退到应用详情页
                openAppDetailsSettings()
            }
        } else
            // Android 7.0+ 尝试打开存储权限页
            try {
                val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                intent.setData(("package:" + requireContext().packageName).toUri())
                startActivity(intent)
            } catch (e:Exception) {
                openAppDetailsSettings()
            }
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", requireContext().packageName, null)
        intent.setData(uri)
        startActivity(intent)
    }

    //展示申请通知权限弹窗
    private fun showReqPermissionDialog() {
        ComDialog.Builder(requireActivity())
            .setMessage(R.string.please_give_storage_permission)
            .setPositiveButton(R.string.agree) {
                //点击了去开启按钮
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(permissionStr), 1
                )
            }.setNegativeButton(R.string.refuse) {
            }
            .create().show()
    }

    // 权限请求回调
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用户授权了权限，使用权限
                binding?.storageAlertBox?.visibility = View.GONE
                createFactoryAndList()
            } else {
                // 用户拒绝了权限，提示用户
                if (PermissionTool.checkPermissionForbid(requireActivity(), permissionStr)) {
                    binding?.goToSettings?.setText(R.string.go_to_settings)
                }
            }
        }
    }

}