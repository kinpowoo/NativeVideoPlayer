package com.jhkj.videoplayer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.jhkj.gl_player.PlayerFragment
import com.jhkj.gl_player.util.ImmersiveStatusBarUtils

class MainActivity : AppCompatActivity(){
    private var playerFragment: PlayerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)

        // 启用 Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        supportActionBar?.hide()
//        actionBar?.hide()
//        supportActionBar?.hide()

        findViewById<Button>(R.id.pick_video).setOnClickListener{
            if(!hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }else{
                openAlbum()
            }
        }

        playerFragment = PlayerFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.content_container, playerFragment!!, "PlayerFragment")
            .commit()
        supportFragmentManager.executePendingTransactions()
    }

    private fun hasPermissions(context: Context, permission: String):Boolean{
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    //申请写权限
    private val writePermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) { //如果通过了权限，打开相册
                openAlbum()
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
            openAlbumLauncher.launch(i)
        }
    }


    //选择图片或视频回调,安卓13及以上
    private val photoOrVideoSelectIntent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        //此处是跳转的result回调方法
        if (it.data != null && it.resultCode == Activity.RESULT_OK) {
            //选择多个文件的结果，会存放庆 intent 的 clipData 字段中
            val clipData = it.data?.clipData
            clipData?.let{ clip->
                for(i in 0 until clipData.itemCount){
                    val clipItem = clip.getItemAt(i)
                    val uri = clipItem.uri
                    playerFragment?.loadUri(uri)
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
}