package com.jhkj.videoplayer.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.jhkj.videoplayer.databinding.LocalDeviceItemLayoutBinding
import com.jhkj.videoplayer.third_file_framework.smb_client.SMBDevice
import java.util.Locale

class LocalDeviceListAdapter(private val devSelect:(SMBDevice, Int)->Unit):
    RecyclerView.Adapter<LocalDeviceListAdapter.DevHolder>(){
    private val devList = mutableListOf<SMBDevice>()
    private lateinit var context: Context

    private fun cleanConn(){
        val oldLen = devList.size
        devList.clear()
        if(oldLen > 0){
            notifyItemRangeRemoved(0,oldLen)
        }
    }
    /**
     * 添加记录
     */
    fun addDev(dev: SMBDevice){
        devList.add(dev)
        notifyItemInserted(devList.size)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevHolder {
        context = parent.context
        val itemBinding = LocalDeviceItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),parent,false)
        return DevHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: DevHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos < devList.size && pos >= 0) {
                val item = devList[pos]
                devSelect.invoke(item,pos)
            }
        }

        onBindViewHolder(holder, position)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onBindViewHolder(holder: DevHolder, position: Int) {
        val pos =  holder.bindingAdapterPosition
        if(pos == -1 || pos >= itemCount)return
        val plan = devList.getOrNull(pos)
        plan?.let {
            setConnInfo(holder, it)
        }
    }

    private fun setConnInfo(holder: DevHolder, conn: SMBDevice){
        val name = conn.serverName
        if(!TextUtils.isEmpty(name)) {
            holder.name.text = String.format(
                Locale.US,"%s(%s:%d)",
                conn.serverName,
                conn.ip,conn.port)
        }else{
            holder.name.text = String.format(
                Locale.US,"smb://%s:%d",
                conn.ip,conn.port)
        }
    }

    override fun getItemCount(): Int {
        return devList.size
    }

    class DevHolder(itemHolder: LocalDeviceItemLayoutBinding): ViewHolder(itemHolder.root){
        val name = itemHolder.title
    }
}