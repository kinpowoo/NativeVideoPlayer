package com.jhkj.videoplayer.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.jhkj.gl_player.PlayerFragment
import com.jhkj.gl_player.model.WebResourceFile
import com.jhkj.gl_player.util.DensityUtil
import com.jhkj.gl_player.util.ImmersiveStatusBarUtils
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.utils.ContentUriUtil
import com.jhkj.videoplayer.databinding.VideoPlayerLayoutBinding
import com.jhkj.videoplayer.utils.file_recursive.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference


class VideoPlayerActivity : AppCompatActivity(){
    private var playerFragment: PlayerFragment? = null
    private lateinit var binding: VideoPlayerLayoutBinding

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val mediaPermissions = arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_IMAGES)
    @RequiresApi(Build.VERSION_CODES.R)
    private val manageStoragePermission = Manifest.permission.MANAGE_EXTERNAL_STORAGE
    private val writeStoragePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    private var requestStoragePermission = ""
    private var screenOrientation = Configuration.ORIENTATION_PORTRAIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VideoPlayerLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //获取屏幕初始方向
        screenOrientation = resources.configuration.orientation

        // 启用 Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        supportActionBar?.hide()
//        actionBar?.hide()
//        supportActionBar?.hide()

        binding.pickVideo.setOnClickListener{
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
//                if(!hasPermissions(this, mediaPermissions[0]) ||
//                    !hasPermissions(this, mediaPermissions[1])){
//                    readMediaPermissionLauncher.launch(mediaPermissions)
//                }else{
//                    openAlbum()
//                }
                openAlbum()
            } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                requestStoragePermission = manageStoragePermission
                if(!hasPermissions(this, requestStoragePermission)){
                    writePermissionLauncher.launch(requestStoragePermission)
                }else{
                    openAlbum()
                }
            } else{
                requestStoragePermission = writeStoragePermission
                if(!hasPermissions(this, requestStoragePermission)){
                    writePermissionLauncher.launch(requestStoragePermission)
                }else{
                    openAlbum()
                }
            }
        }

        playerFragment = PlayerFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.content_container, playerFragment!!, "PlayerFragment")
            .commit()
        supportFragmentManager.executePendingTransactions()

        playerFragment?.initListener = WeakReference(Runnable {
            getIntentData()
        })
    }

    private fun hasPermissions(context: Context, permission: String):Boolean{
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        getIntentData()
    }

//    在用于接收分享的Activity里面加接收代码
//    当APP进程在后台时，会调用Activity的onNewIntent方法
//    当APP进程被杀死时，会调用onCreate方法
//    所以在两个方法中都需要监听事件

    private fun getIntentData() {
        val intent = intent
        var uri = intent.data
        /*
         * API > 16 时，有些系统的文件夹的文件分享，内容会放在 ClipData 中，而不是放在 mData 中
         */
        if (uri == null && intent.clipData != null) {
            val item = intent.clipData!!.getItemAt(0)
            if (item != null) {
                uri = item.uri
            }
        }
        //从其他app跳入逻辑
        if (uri != null) {
            val path = ContentUriUtil.getPath(this, uri)
//            playerFragment?.loadUri(uri)
            playerFragment?.loadUrl(path)
            binding.pickVideo.visibility = View.GONE
//            Toast.makeText(this, "获得path:$path", Toast.LENGTH_SHORT).show()
        } else {
//            Toast.makeText(this, "外部传入的uri为null", Toast.LENGTH_SHORT).show()
            val fileInfo: FileItem? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("fileItem", FileItem::class.java)
            }else{
                intent.getSerializableExtra("fileItem") as? FileItem
            }
            if(fileInfo != null){
                val webResFile = WebResourceFile(fileInfo.path,
                    fileInfo.credentialUser ?: "",fileInfo.credentialPass ?: "")
                lifecycleScope.launch(Dispatchers.IO) {
                    playerFragment?.loadWebResource( webResFile)
                }
                binding.pickVideo.visibility = View.GONE
            }
        }
    }


    //申请写权限
    private val writePermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            lifecycleScope.launch(Dispatchers.Main) {
                if (it) { //如果通过了权限，打开相册
                    openAlbum()
                }else{
                    if (!shouldShowRequestPermissionRationale(requestStoragePermission)) {
                        Snackbar.make(binding.pickVideo, "前往设置打开存储权限", Snackbar.LENGTH_SHORT).show()
                        // 用户勾选了“不再询问”，跳转到设置页面
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    } else {
                        Snackbar.make(binding.pickVideo, "请同意访问存储权限", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

    //申请写权限
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val readMediaPermissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            lifecycleScope.launch(Dispatchers.Main) {
                if (it[mediaPermissions[0]] == true && it[mediaPermissions[1]] == true) { //如果通过了权限，打开相册
                    openAlbum()
                }else{
                    if (!shouldShowRequestPermissionRationale(requestStoragePermission)) {
                        Snackbar.make(binding.pickVideo, "前往设置打开存储权限", Snackbar.LENGTH_SHORT).show()
                        // 用户勾选了“不再询问”，跳转到设置页面
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    } else {
                        Snackbar.make(binding.pickVideo, "请同意访问存储权限", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

    //分别进入视频选择和图片选择页面
    private fun openAlbum() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            openAlbum13()
        }else{
            openAlbum12()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun openAlbum13(){
        val intent = Intent(MediaStore.ACTION_PICK_IMAGES)
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,1)
        intent.type = "video/*"
        photoOrVideoSelectIntent.launch(intent)
    }

    private fun openAlbum12(){
        //大于9.0系统，采用Documents方式获取uri
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "video/*"
            openAlbumLauncher.launch(intent)
        }else{
            val i = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "video/*"
            openAlbumLauncher.launch(i)
        }
    }


    //选择图片或视频回调,安卓13及以上
    private val photoOrVideoSelectIntent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        //此处是跳转的result回调方法
        if (it.data != null && it.resultCode == Activity.RESULT_OK) {
            //选择多个文件的结果，会存放庆 intent 的 clipData 字段中
            if(it.data?.data != null){
                val uri = it.data?.data
                if(uri != null) {
                    playerFragment?.loadUri(uri)
                }else{
                    Snackbar.make(binding.pickVideo, "没有选择任何视频文件", Snackbar.LENGTH_SHORT).show()
                }
            }else {
                val clipData = it.data?.clipData
                clipData?.let { clip ->
                    for (i in 0 until clipData.itemCount) {
                        val clipItem = clip.getItemAt(i)
                        val uri = clipItem.uri
                        playerFragment?.loadUri(uri)
                    }
                }
            }
        }
    }

    //选择图片
    private val openAlbumLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { it2 ->
            if (it2.resultCode == Activity.RESULT_OK) {
                val photoUri = it2.data?.data
                photoUri?.let {
                    playerFragment?.loadUri(it)
                }
            }
        }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus){
            ImmersiveStatusBarUtils.setFullScreen(this,true)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isDirectionChange = (newConfig.orientation != screenOrientation)
        if(isDirectionChange) {
            screenOrientation = newConfig.orientation
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) { //当屏幕方向为横屏的时候
                val params = binding.contentContainer.layoutParams
                params?.let {
                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
                    it.height = ViewGroup.LayoutParams.MATCH_PARENT
                    binding.contentContainer.layoutParams = it
                }
//                binding.pickVideo.visibility = View.GONE
            } else {
//                binding.pickVideo.visibility = View.VISIBLE
                val params = binding.contentContainer.layoutParams
                params?.let {
                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
                    val h = DensityUtil.dip2px(this@VideoPlayerActivity, 240f).toInt()
                    it.height = h
                    binding.contentContainer.layoutParams = it
                }
            }
        }
    }
}