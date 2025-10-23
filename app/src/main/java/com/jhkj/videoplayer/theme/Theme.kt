package com.jhkj.videoplayer.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,

    //Other default colors to override
    background = Color(0xFF0C0B0E),
    surface = Color(0xFF0C0B0E),
    onPrimary = Color(0xBF24252B),
    onSecondary = Color(0xBF24252B),
    onTertiary = Color(0xBF24252B),
    onBackground = Color(0xFF0C0B0E),
    onSurface = Color(0xFF0C0B0E),
)

val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    //Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color(0xFFF7F8FC),
    onSecondary = Color(0xFFF7F8FC),
    onTertiary = Color(0xFFF7F8FC),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)