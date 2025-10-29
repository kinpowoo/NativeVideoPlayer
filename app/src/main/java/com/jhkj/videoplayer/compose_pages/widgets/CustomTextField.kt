package com.jhkj.videoplayer.compose_pages.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun CustomTextField(modifier: Modifier = Modifier,
                    text:String = "",
                    placeholder:String,
                    singleLine:Boolean = true,
                    maxLength:Int = 50,
                    isEditable:Boolean = true,
                    onValueChange:((String)->Unit)? = null,
                    keyboardType: KeyboardType = KeyboardType.Unspecified){
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        if (isFocused) Color.Blue else Color.Gray,
        animationSpec = tween(durationMillis = 150)
    )

    BasicTextField(
        value = text,
        onValueChange = {
            if(isEditable && it.length <= maxLength) {
                onValueChange?.invoke(it)
            }},
        singleLine = singleLine,
        enabled = isEditable,
        cursorBrush = SolidColor(borderColor),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min=20.dp,max=22.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .drawWithContent {
                drawContent()
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = if (isFocused) 2.dp.toPx() else 1.dp.toPx()
                )
            },
        decorationBox = { innerTextField ->
            Column(Modifier.padding(bottom = 2.dp, start = 2.dp)){
                Box{
                    if (text.isEmpty()) {
                        Text(placeholder, color = Color.Gray.copy(alpha = 0.7f), fontSize = 16.sp)
                    }
                    innerTextField()
                }
            }
        }
    )
}