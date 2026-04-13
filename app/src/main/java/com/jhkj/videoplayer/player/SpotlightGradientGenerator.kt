package com.jhkj.videoplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.palette.graphics.Palette
import kotlin.math.max

object SpotlightGradientGenerator {
    
    /**
     * 创建聚焦式径向渐变背景
     * 中心为高亮色，四周为暗色
     */
    fun createSpotlightGradient(
        bitmap: Bitmap,
        context: Context,
        centerType: CenterType = CenterType.DOMINANT,
        style: SpotlightStyle = SpotlightStyle.SOFT
    ): Drawable {
        
        // 1. 从专辑封面提取颜色
        val palette = Palette.from(bitmap).generate()
        
        // 2. 获取中心高亮色
        val centerColor = when (centerType) {
            CenterType.VIBRANT -> palette.vibrantSwatch?.rgb ?: getFallbackColor(2)
            CenterType.LIGHT_VIBRANT -> palette.lightVibrantSwatch?.rgb ?: palette.vibrantSwatch?.rgb ?: getFallbackColor(2)
            CenterType.DOMINANT -> palette.dominantSwatch?.rgb ?: getFallbackColor(2)
            CenterType.AVERAGE -> calculateAverageColor(bitmap)
        }
        
        // 3. 生成暗色边缘
        val edgeColor = when (style) {
            SpotlightStyle.SOFT -> palette.mutedSwatch?.rgb ?: generateSoftEdgeColor(centerColor)
            SpotlightStyle.DEEP -> palette.mutedSwatch?.rgb ?: generateDeepEdgeColor(centerColor)
            SpotlightStyle.CONTRAST -> generateContrastEdgeColor(centerColor)
            SpotlightStyle.BLACK -> Color.BLACK
        }
        
        // 4. 创建径向渐变
        return createRadialGradientDrawable(centerColor, edgeColor, context, style)
    }
    
    /**
     * 生成柔和的边缘颜色
     */
    private fun generateSoftEdgeColor(centerColor: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(centerColor, hsv)
        
        // 降低亮度和饱和度
        hsv[1] = (hsv[1] * 0.5f).coerceAtLeast(0.1f)  // 降低饱和度
        hsv[2] = (hsv[2] * 0.3f).coerceAtLeast(0.1f)  // 降低亮度
        
        return Color.HSVToColor(hsv)
    }
    
    /**
     * 生成深色边缘
     */
    private fun generateDeepEdgeColor(centerColor: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(centerColor, hsv)
        
        // 保持色相，大幅降低亮度和饱和度
        hsv[1] = (hsv[1] * 0.7f).coerceAtLeast(0.2f)
        hsv[2] = 0.15f  // 非常暗
        
        return Color.HSVToColor(hsv)
    }
    
    /**
     * 生成对比强烈的边缘
     */
    private fun generateContrastEdgeColor(centerColor: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(centerColor, hsv)
        
        // 使用互补色作为边缘
        val complementaryHue = (hsv[0] + 180) % 360
        return Color.HSVToColor(floatArrayOf(complementaryHue, 0.8f, 0.2f))
    }
    
    /**
     * 创建径向渐变Drawable
     */
    private fun createRadialGradientDrawable(
        centerColor: Int,
        edgeColor: Int,
        context: Context,
        style: SpotlightStyle
    ): Drawable {
        return when (style) {
            SpotlightStyle.SOFT -> createSoftRadialGradient(centerColor, edgeColor, context)
            SpotlightStyle.DEEP -> createDeepRadialGradient(centerColor, edgeColor, context)
            SpotlightStyle.CONTRAST -> createContrastRadialGradient(centerColor, edgeColor, context)
            SpotlightStyle.BLACK -> createBlackEdgeRadialGradient(centerColor, context)
        }
    }

    /**
     * 柔和的径向渐变
     */
    fun createDefaultRadialGradient(context: Context): GradientDrawable {
        val centerColor = getFallbackColor(2)
        val edgeColor = getFallbackColor(3)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = (context.screenWidth * 0.6f)  // 较大的半径
            colors = intArrayOf(centerColor, edgeColor)
            setGradientCenter(0.5f, 0.5f)  // 中心位置
        }
    }

    /**
     * 柔和的径向渐变
     */
    fun createSoftRadialGradient(centerColor: Int, edgeColor: Int, context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = (context.screenWidth * 0.6f)  // 较大的半径
            colors = intArrayOf(centerColor, edgeColor)
            setGradientCenter(0.5f, 0.5f)  // 中心位置
        }
    }
    
    /**
     * 深色径向渐变
     */
    private fun createDeepRadialGradient(centerColor: Int, edgeColor: Int, context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = (context.screenWidth * 0.6f)  // 中等半径
            setColors(intArrayOf(centerColor, edgeColor))
            setGradientCenter(0.5f, 0.5f)
        }
    }
    
    /**
     * 对比强烈的径向渐变
     */
    private fun createContrastRadialGradient(centerColor: Int, edgeColor: Int, context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = (context.screenWidth * 0.4f)  // 较小半径，更集中
            colors = intArrayOf(centerColor, edgeColor)
            setGradientCenter(0.5f, 0.5f)
        }
    }
    
    /**
     * 黑色边缘的径向渐变
     */
    private fun createBlackEdgeRadialGradient(centerColor: Int, context: Context): GradientDrawable {
        // 创建中间过渡色
        val transitionColor = adjustColorBrightness(centerColor, -0.3f)
        
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = (context.screenWidth * 0.7f)
            setColors(intArrayOf(centerColor, transitionColor, Color.BLACK))
            setGradientCenter(0.5f, 0.5f)
        }
    }
    
    /**
     * 计算图片平均色
     */
    private fun calculateAverageColor(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        
        var red = 0L
        var green = 0L
        var blue = 0L
        
        val sampleStep = max(1, width * height / 10000)  // 采样计算
        
        for (x in 0 until width step sampleStep) {
            for (y in 0 until height step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)
            }
        }
        
        val sampleCount = (width / sampleStep) * (height / sampleStep)
        val avgRed = (red / sampleCount).toInt()
        val avgGreen = (green / sampleCount).toInt()
        val avgBlue = (blue / sampleCount).toInt()
        
        return Color.rgb(avgRed, avgGreen, avgBlue)
    }
    
    /**
     * 调整颜色亮度
     */
    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + factor).coerceIn(0.1f, 1f)
        return Color.HSVToColor(hsv)
    }
    
    /**
     * 回退颜色
     */
    private fun getFallbackColor(index: Int): Int {
        val fallbackColors = arrayOf(
            Color.parseColor("#FF6B6B"), // 珊瑚红
            Color.parseColor("#4ECDC4"), // 松石绿
            Color.parseColor("#45B7D1"), // 蓝色
            Color.parseColor("#96CEB4"), // 薄荷绿
            Color.parseColor("#FFEAA7")  // 淡黄色
        )
        return fallbackColors[index % fallbackColors.size]
    }
    
    enum class CenterType {
        VIBRANT,       // 活力色
        LIGHT_VIBRANT, // 亮活力色
        DOMINANT,      // 主导色
        AVERAGE        // 平均色
    }
    
    enum class SpotlightStyle {
        SOFT,      // 柔和过渡
        DEEP,      // 深色过渡
        CONTRAST,  // 高对比
        BLACK      // 黑色边缘
    }
}

// 屏幕宽度扩展属性
val Context.screenWidth: Int
    get() = resources.displayMetrics.widthPixels