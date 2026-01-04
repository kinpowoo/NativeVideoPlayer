package com.jhkj.videoplayer.pages

import android.os.Bundle
import android.transition.Slide
import android.view.Gravity
import androidx.recyclerview.widget.GridLayoutManager
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.adapter.ConnTypeAdapter
import com.jhkj.videoplayer.app.BaseActivity
import com.jhkj.videoplayer.databinding.SelectConnTypeLayoutBinding
import com.jhkj.videoplayer.utils.ImmersiveStatusBarUtils
import com.jhkj.videoplayer.utils.Res


enum class ConnType{
    WINDOWS,MACOS,LINUX,NAS,FTP,SFTP,WEBDAV,OWNCLOUD,NFS,GOOGLE_DRIVE,DROPBOX,ONEDRIVE,BOX,BAIDU_NETDISK,AWS,ALI_CLOUD,MEGA,JELLYFIN,EMBY
}

data class ConnItem(val icon: Int, val title:String,val identity:ConnType)


class SelectConnTypeActivity : BaseActivity() {
    private var binding: SelectConnTypeLayoutBinding? = null
    private var adapter: ConnTypeAdapter? = null

    val connTypeList = listOf(
        ConnItem(R.drawable.windows,"Windows",ConnType.WINDOWS),
        ConnItem(R.drawable.macos,"macOS",ConnType.MACOS),
        ConnItem(R.drawable.linux,"Linux",ConnType.LINUX),
        ConnItem(R.drawable.nas,"NAS",ConnType.NAS),
        ConnItem(R.drawable.ftp,"FTP",ConnType.FTP),
        ConnItem(R.drawable.sftp,"SFTP",ConnType.SFTP),
        ConnItem(R.drawable.webdav,"WebDAV",ConnType.WEBDAV),
        ConnItem(R.drawable.owncloud,"ownCloud",ConnType.OWNCLOUD),
        ConnItem(R.drawable.nfs2,"NFS",ConnType.NFS),
        ConnItem(R.drawable.google_drive,"Drive",ConnType.GOOGLE_DRIVE),
        ConnItem(R.drawable.dropbox,"Dropbox",ConnType.DROPBOX),
        ConnItem(R.drawable.onedrive,"OneDrive",ConnType.ONEDRIVE),
        ConnItem(R.drawable.box,"Box",ConnType.BOX),
        ConnItem(R.drawable.baidu_netdisk, Res.string(R.string.baidu_connection),ConnType.BAIDU_NETDISK),
        ConnItem(R.drawable.aws,"S3",ConnType.AWS),
        ConnItem(R.drawable.ali_cloud,Res.string(R.string.ali_connection),ConnType.ALI_CLOUD),
        ConnItem(R.drawable.mega,"Mega",ConnType.MEGA),
        ConnItem(R.drawable.jellyfin,"Jellyfin",ConnType.JELLYFIN),
        ConnItem(R.drawable.emby,"Emby",ConnType.EMBY),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置进入和退出过渡
//        val slide = Slide(Gravity.BOTTOM)
//        slide.setDuration(300)
//        window.setEnterTransition(slide)
//        window.setExitTransition(slide)

        binding = SelectConnTypeLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this,true)

        supportActionBar?.hide()
        binding?.closeBtn?.setOnClickListener{
            if(isDoubleClick(it))return@setOnClickListener
            finishAfterTransition()
        }

        adapter = ConnTypeAdapter{ item,idx ->

        }
        binding?.connTypeList?.layoutManager = GridLayoutManager(this,3)
        binding?.connTypeList?.adapter = adapter

        adapter?.addConn(connTypeList)
    }
}