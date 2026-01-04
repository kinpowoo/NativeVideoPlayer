package com.jhkj.videoplayer.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.jhkj.videoplayer.databinding.ConnTypeListItemLayoutBinding
import com.jhkj.videoplayer.pages.ConnItem

class ConnTypeAdapter(private val connSelect:(ConnItem, Int)->Unit): RecyclerView.Adapter<ConnTypeAdapter.ConnTypeHolder>(){
    private val connList = mutableListOf<ConnItem>()
    private lateinit var context: Context
    private var selectIndex = -1

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
    fun addConn(audios:List<ConnItem>?){
        cleanConn()
        if(!audios.isNullOrEmpty()){
            connList.addAll(audios)
        }
        notifyItemRangeInserted(0,connList.size)
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnTypeHolder {
        context = parent.context
        val itemBinding = ConnTypeListItemLayoutBinding.inflate(
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

    private fun setConnInfo(holder: ConnTypeHolder, conn: ConnItem){
        val name = conn.title
        holder.name.text = name
        holder.icon.setImageResource(conn.icon)
    }

    override fun getItemCount(): Int {
        return connList.size
    }

    class ConnTypeHolder(itemHolder: ConnTypeListItemLayoutBinding): ViewHolder(itemHolder.root){
        val icon = itemHolder.iconIv
        val name = itemHolder.title
    }
}