package com.jhkj.videoplayer.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.databinding.ConnInfoItemLayoutBinding
import com.jhkj.videoplayer.databinding.ConnTypeListItemLayoutBinding
import com.jhkj.videoplayer.pages.ConnItem
import com.jhkj.videoplayer.pages.ConnType
import com.jhkj.videoplayer.utils.Res
import java.util.Locale

class ConnListAdapter(private val connSelect:(ConnInfo, Int)->Unit,
                    private val moreClick:(ConnInfo?, Int, View)->Unit): RecyclerView.Adapter<ConnListAdapter.ConnTypeHolder>(){
    private val connList = mutableListOf<ConnInfo>()
    private lateinit var context: Context
    private var selectIndex = -1

    private val connTypeList = listOf(
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

    private fun cleanConn(){
        val oldLen = connList.size
        selectIndex = -1
        connList.clear()
        if(oldLen > 0){
            notifyItemRangeRemoved(0,oldLen)
        }
    }
    /**
     * 添加记录
     */
    fun addConn(conns:List<ConnInfo>?){
        cleanConn()
        if(!conns.isNullOrEmpty()){
            connList.addAll(conns)
        }
        notifyItemRangeInserted(0,connList.size)
    }

    fun getItem(pos:Int): ConnInfo?{
        return connList.getOrNull(pos)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnTypeHolder {
        context = parent.context
        val itemBinding = ConnInfoItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),parent,false)
        return ConnTypeHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: ConnTypeHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos < connList.size && pos >= 0) {
                val item = connList[pos]
                connSelect.invoke(item,pos)
            }
        }
        holder.moreIv.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos < connList.size && pos >= 0) {
                val item = connList[pos]
                moreClick.invoke(item,pos,holder.moreIv)
            }
        }
        onBindViewHolder(holder, position)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onBindViewHolder(holder: ConnTypeHolder, position: Int) {
        val pos =  holder.bindingAdapterPosition
        if(pos == -1 || pos >= itemCount)return
        val plan = connList.getOrNull(pos)
        plan?.let {
            setConnInfo(holder, it)
        }
    }

    private fun setConnInfo(holder: ConnTypeHolder, conn: ConnInfo){
        val name = conn.displayName
        if(TextUtils.isEmpty(name)) {
            holder.name.text = name
        }else{
            holder.name.text = String.format(
                Locale.US,"%s:%s:%d",
                conn.protocol.lowercase(),
                conn.domain,conn.port)
        }
        val img = connTypeList.find { conn.connType == it.identity.ordinal }!!
        holder.icon.setImageResource(img.icon)
    }

    override fun getItemCount(): Int {
        return connList.size
    }

    class ConnTypeHolder(itemHolder: ConnInfoItemLayoutBinding): ViewHolder(itemHolder.root){
        val icon = itemHolder.iconIv
        val name = itemHolder.title
        val moreIv = itemHolder.moreIv
    }
}