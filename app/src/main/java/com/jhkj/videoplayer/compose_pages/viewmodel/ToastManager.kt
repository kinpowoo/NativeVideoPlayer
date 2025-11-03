package com.jhkj.videoplayer.compose_pages.viewmodel

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// 最实用的封装
@Composable
fun rememberToast(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
