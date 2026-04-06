package com.jhkj.videoplayer.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.jhkj.videoplayer.databinding.GridFileItemLayoutBinding

data class ThirdServiceDto(val icon:Int,val title:String)

class ThirdServiceListAdapter(private val fileSelect:(ThirdServiceDto, Int)->Unit):
    RecyclerView.Adapter<ThirdServiceListAdapter.FileItemHolder>(){
    private val fileList = mutableListOf<ThirdServiceDto>()
    private lateinit var context: Context

    private fun cleanFiles(){
        val oldLen = fileList.size
        fileList.clear()
        if(oldLen > 0){
            notifyItemRangeRemoved(0,oldLen)
        }
    }
    /**
     * 添加记录
     */
    fun addFiles(files:List<ThirdServiceDto>){
        cleanFiles()
        if(files.isNotEmpty()){
            fileList.addAll(files)
        }
        notifyItemRangeInserted(0,fileList.size)
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileItemHolder {
        context = parent.context
        val itemBinding = GridFileItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),parent,false)
        return FileItemHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: FileItemHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos < fileList.size && pos >= 0) {
                val item = fileList[pos]
                fileSelect.invoke(item,pos)
            }
        }
        onBindViewHolder(holder, position)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onBindViewHolder(holder: FileItemHolder, position: Int) {
        val pos =  holder.bindingAdapterPosition
        if(pos == -1 || pos >= itemCount)return
        val plan = fileList.getOrNull(pos)
        plan?.let {
            setFileInfo(holder, it)
        }
    }

    private fun setFileInfo(holder: FileItemHolder, conn: ThirdServiceDto){
        holder.icon.setImageResource(conn.icon)
        holder.name.text = conn.title
    }

    override fun getItemCount(): Int {
        return fileList.size
    }

    class FileItemHolder(itemHolder: GridFileItemLayoutBinding): ViewHolder(itemHolder.root){
        val icon = itemHolder.iconIv
        val name = itemHolder.title
    }
}