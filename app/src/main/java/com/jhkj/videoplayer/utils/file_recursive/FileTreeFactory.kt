package com.jhkj.videoplayer.utils.file_recursive

import android.text.TextUtils
import java.io.File

class FileTreeFactory(rootPath:String): FileProviderProtocol{
    private var rootFile: FileItem
    init {
        val file = File(rootPath)
        rootFile = convertFile(file)
    }

    private fun convertFile(file:File): FileItem{
        val isDir = file.isDirectory
        val name = file.name
        var subname = file.nameWithoutExtension
        val isHidden =(TextUtils.isEmpty(subname) && name.startsWith("."))
        if(TextUtils.isEmpty(subname)){
            subname = name
        }
        val suffix = name.split(subname).lastOrNull() ?: ""
        val mimeType = suffix.removePrefix(".")
        val fileItem = FileItem(isDir,file.name,
            subname,mimeType,
            file.lastModified(),file.lastModified(),
            file.length(),0,"",file.absolutePath,
            file.parent ?: "",isHidden,
            file.canWrite(), file.canRead(),"","")
        return fileItem
    }

    override fun listRoot(): List<FileItem> {
        return listFiles(rootFile)
    }

    override fun listFiles(parent: FileItem): List<FileItem> {
        if(parent.isDirectory){
            val file = File(parent.path)
            val childFiles = file.listFiles()
            if(childFiles != null && !childFiles.isEmpty()){
                val fileItems = mutableListOf<FileItem>()
                childFiles.forEach { item ->
                    val fileItem = convertFile(item)
                    if(!fileItem.isHide) {
                        fileItems.add(fileItem)
                    }
                }
                return fileItems
            }
            return listOf()
        }else{
            return listOf()
        }
    }

    override fun moveFile(
        source: FileItem,
        target: FileItem
    ) {

    }

    override fun deleteFile(source: FileItem) {

    }

    override fun renameFile(source: FileItem) {

    }

}