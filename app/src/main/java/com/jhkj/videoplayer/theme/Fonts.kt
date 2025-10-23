package com.jhkj.videoplayer.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.jhkj.videoplayer.R

val CustomFontFamily = FontFamily(
    // 加载 res/font/your_font.ttf
    Font(R.font.marckscript_regular, FontWeight.Normal),

    // 加载 res/font/your_font_bold.ttf（如果有加粗版本）
//    Font(R.font.your_font_bold, FontWeight.Bold),

    // 可以继续添加其他变体（Italic、Medium 等）
    // Font(R.font.your_font_italic, FontWeight.Normal, FontStyle.Italic),
)
val CustomTypography = Typography(
    // 覆盖所有默认的 TextStyle，确保字体生效
    displayLarge = TextStyle(fontFamily = CustomFontFamily),
    displayMedium = TextStyle(fontFamily = CustomFontFamily),
    displaySmall = TextStyle(fontFamily = CustomFontFamily),
    headlineLarge = TextStyle(fontFamily = CustomFontFamily),
    headlineMedium = TextStyle(fontFamily = CustomFontFamily),
    headlineSmall = TextStyle(fontFamily = CustomFontFamily),
    titleLarge = TextStyle(fontFamily = CustomFontFamily),
    titleMedium = TextStyle(fontFamily = CustomFontFamily),
    titleSmall = TextStyle(fontFamily = CustomFontFamily),
    bodyLarge = TextStyle(fontFamily = CustomFontFamily),
    bodyMedium = TextStyle(fontFamily = CustomFontFamily),
    bodySmall = TextStyle(fontFamily = CustomFontFamily),
    labelLarge = TextStyle(fontFamily = CustomFontFamily),
    labelMedium = TextStyle(fontFamily = CustomFontFamily),
    labelSmall = TextStyle(fontFamily = CustomFontFamily),
)
