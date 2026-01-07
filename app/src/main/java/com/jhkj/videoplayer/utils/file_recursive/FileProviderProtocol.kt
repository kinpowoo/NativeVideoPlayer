package com.jhkj.videoplayer.utils.file_recursive


interface FileProviderProtocol {

    fun listRoot():List<FileItem>

    fun listFiles(parent:FileItem):List<FileItem>

    fun moveFile(source:FileItem,target:FileItem)

    fun deleteFile(source: FileItem)

    fun renameFile(source: FileItem)
}