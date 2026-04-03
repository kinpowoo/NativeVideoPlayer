package com.jhkj.videoplayer.third_file_framework.smb

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation

interface OnOperationFileCallback {

    fun onSuccess()
    fun onFailure(message: String)
}

interface OnReadFileListNameCallback {

    fun onSuccess(fileNameList: List<String>)
    fun onFailure(message: String)
}

interface OnListFileCallback {
    fun onSuccess(fileList: List<FileIdBothDirectoryInformation>)
    fun onFailure(message: String)
}