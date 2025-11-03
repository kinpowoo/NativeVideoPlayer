package com.jhkj.videoplayer.compose_pages.models

import com.jhkj.videoplayer.R


object FileType{
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
                lower.endsWith(".dst")
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