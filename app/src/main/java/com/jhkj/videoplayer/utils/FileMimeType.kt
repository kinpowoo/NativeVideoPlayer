package com.jhkj.videoplayer.utils

import java.io.File

object FileMimeType {

    fun getFileType(fileName: String): String {
        if (fileName.endsWith(".mp4") ||
            fileName.endsWith(".mkv") ||
            fileName.endsWith(".rmvb") ||
            fileName.endsWith(".ts")
        ) {
            return "video/*"
        } else if (fileName.endsWith(".jpg") ||
            fileName.endsWith(".png") ||
            fileName.endsWith(".jpeg") ||
            fileName.endsWith(".webp") ||
            fileName.endsWith(".JPG") ||
            fileName.endsWith(".PNG") ||
            fileName.endsWith(".JPEG") ||
            fileName.endsWith(".WEBP")
        ) {
            return "image/*"
        } else if (fileName.endsWith(".mp3") ||
            fileName.endsWith(".ogg") ||
            fileName.endsWith(".flac") ||
            fileName.endsWith(".wav") ||
            fileName.endsWith(".dst") ||
            fileName.endsWith(".ape")
        ) {
            return "audio/*"
        } else {
            return "*/*"
        }
    }
}