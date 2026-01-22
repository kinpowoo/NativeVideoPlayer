package com.jhkj.videoplayer.compose_pages.models

import android.content.Context
import android.content.Intent
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.player.VideoPlayerActivity
import com.jhkj.videoplayer.utils.file_recursive.FileItem
import com.thegrizzlylabs.sardineandroid.DavResource


object FileType{
    const val oneKb = 1024
    const val oneM = 1024*1024
    const val oneG = 1024*1024*1024
    const val oneT = 1024*1024*1024*1024

    fun calcSize(size:Long):String{
        if(size < oneKb){
            val numB = size
            return "${numB}b"
        }else if(size < oneM){
            val numKB = size / oneKb
            return "${numKB}KB"
        } else if(size < oneG){
            val numM = size % oneM
            val numKB = size / oneKb
            return "${numM}G${numKB}M"
        }else {
            val numG = size % oneG
            val numM = size / oneM
            return "${numG}G${numM}M"
        }
//        else{
//            val numT = size % oneT
//            val numG = size / oneG
//            return "${numT}T${numG}G"
//        }
    }

    fun doWebDavFileOpenAction(context: Context,item:DavResource,connDto: ConnInfo){
        val name:String = item.name
        val resPath = item.path
        if(isMovie(name)){
//            val intent = Intent(context, VideoPlayerActivity::class.java)
//            val info = WebdavResource(connDto.displayName,
//                connDto.domain,resPath,
//                connDto.protocol,connDto.port,
//                connDto.username,connDto.pass)
//            intent.putExtra("webdav",info)
//            context.startActivity(intent)
        }else if(isMusic(name)){

        }else if(isPhoto(name)){

        }else if(isText(name)){

        }else if(isBook(name)){

        }else if(isGif(name)){

        }else if(isWord(name)){

        }else if(isExcel(name)){

        }else if(isPdf(name)){

        }else if(isZip(name)) {
        }
        else{

        }
    }

    fun doLocalFileOpenAction(context: Context,file: FileItem){
        val name:String = file.fileName
        val resPath = file.path
        if(isMovie(name)){
            val intent = Intent(context, VideoPlayerActivity::class.java)
            intent.putExtra("fileItem",file)
            context.startActivity(intent)
        }else if(isMusic(name)){

        }else if(isPhoto(name)){

        }else if(isText(name)){

        }else if(isBook(name)){

        }else if(isGif(name)){

        }else if(isWord(name)){

        }else if(isExcel(name)){

        }else if(isPdf(name)){

        }else if(isZip(name)) {
        }
        else{

        }
    }


    fun getFileIdentity(name:String):Int{
        if(isMovie(name)){
            return R.drawable.file_video
        }else if(isMusic(name)){
            return R.drawable.file_audio
        }else if(isPhoto(name)){
            return R.drawable.file_photo2
        }else if(isText(name)){
            return R.drawable.file_text
        }else if(isBook(name)){
            return R.drawable.file_epub
        }else if(isGif(name)){
            return R.drawable.file_gif
        }else if(isWord(name)){
            return R.drawable.file_word
        }else if(isExcel(name)){
            return R.drawable.file_excel
        }else if(isPdf(name)){
            return R.drawable.file_pdf
        }else if(isZip(name)){
            return R.drawable.file_zip
        }
        else{
            return R.drawable.file_unknow
        }
    }

    fun isMovie(name:String): Boolean{
        val lower = name.lowercase()
        return lower.endsWith(".mp4") ||
                lower.endsWith(".mkv") ||
                lower.endsWith(".rmvb") ||
                lower.endsWith(".mpeg4") ||
                lower.endsWith(".ts")
    }

    fun isMusic(name:String): Boolean{
        val lower = name.lowercase()
        return lower.endsWith(".mp3") ||
                lower.endsWith(".flac") ||
                lower.endsWith(".wav") ||
                lower.endsWith(".aac") ||
                lower.endsWith(".dst") ||
                lower.endsWith(".ape")
    }

    fun isText(name:String): Boolean{
        val lower = name.lowercase()
        return lower.endsWith(".txt") ||
                lower.endsWith(".ttf") ||
                lower.endsWith(".log")
    }

    fun isBook(name:String): Boolean{
        val lower = name.lowercase()
        return lower.endsWith(".epub")
    }

    fun isPhoto(name:String): Boolean{
        val lower = name.lowercase()
        return lower.endsWith(".png") ||
                lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".webp")
    }

    fun isGif(name:String): Boolean{
        val lower = name.lowercase()
        return lower.endsWith(".gif")
    }

    fun isWord(name:String): Boolean{
        val lower = name.lowercase()
        return lower.endsWith(".doc") ||
                lower.endsWith(".docx")
    }

    fun isExcel(name:String): Boolean{
        val lower = name.lowercase()
        return lower.endsWith(".xls") ||
                lower.endsWith(".xlsx")
    }

    fun isPdf(name:String): Boolean{
        val lower = name.lowercase()
        return lower.endsWith(".pdf")
    }

    fun isZip(name:String): Boolean{
        val lower = name.lowercase()
        return lower.endsWith(".zip") ||
                lower.endsWith(".7z") ||
                lower.endsWith(".rar") ||
                lower.endsWith(".gzip")
    }
}