package com.jhkj.videoplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.get
import androidx.core.graphics.drawable.toDrawable

object DominantColorBackgroundGenerator {
    
    /**
     * 从封面图像提取主色调并生成背景
     */
    fun createBackgroundFromCover(
        bitmap: Bitmap,
        context: Context,
        style: BackgroundStyle = BackgroundStyle.GRADIENT
    ): BackgroundResult {
        
        // 1. 提取主色调
        val dominantColor = extractDominantColor(bitmap)
        
        // 2. 生成背景
        val background = when (style) {
            BackgroundStyle.SOLID -> createSolidBackground(dominantColor)
            BackgroundStyle.GRADIENT -> createGradientBackground(dominantColor, context)
            BackgroundStyle.MATERIAL -> createMaterialBackground(dominantColor, context)
            BackgroundStyle.BLUR -> createBlurBackground(bitmap, dominantColor, context)
        }
        
        // 3. 计算合适的文字颜色（确保可读性）
        val textColor = getOptimalTextColor(dominantColor)
        
        return BackgroundResult(
            dominantColor = dominantColor,
            backgroundDrawable = background,
            textColor = textColor,
            isDarkBackground = isDarkColor(dominantColor)
        )
    }
    
    /**
     * 提取主色调（模仿LeapMusic的算法）
     */
    private fun extractDominantColor(bitmap: Bitmap): Int {
        // 方法1: 使用Palette库提取
        val palette = Palette.from(bitmap)
            .maximumColorCount(12)  // 增加颜色数量提高准确性
            .generate()
        
        // 优先使用活力色，如果没有则使用主导色
        return palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: calculateAverageColor(bitmap)  // 回退到平均色
    }
    
    /**
     * 计算图片的平均色（备用方案）
     */
    private fun calculateAverageColor(bitmap: Bitmap): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height
        
        // 抽样计算，提高性能
        val sampleStep = max(1, min(width, height) / 50)
        
        for (x in 0 until width step sampleStep) {
            for (y in 0 until height step sampleStep) {
                val pixel = bitmap[x, y]
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
     * 创建纯色背景
     */
    private fun createSolidBackground(color: Int): Drawable {
        return color.toDrawable()
    }
    
    /**
     * 创建渐变色背景（主色调的明暗变化）
     */
    private fun createGradientBackground(color: Int, context: Context): Drawable {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        
        // 生成明暗变体
        val lightVariant = adjustColorBrightness(color, 0.3f)  // 调亮
        val darkVariant = adjustColorBrightness(color, -0.3f)   // 调暗
        
        val colors = intArrayOf(
            lightVariant,
            color,
            darkVariant
        )
        
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            colors
        ).apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.LINEAR_GRADIENT
            cornerRadius = 0f
        }
    }
    
    /**
     * 创建Material Design风格的背景
     */
    private fun createMaterialBackground(color: Int, context: Context): Drawable {
        val primaryColor = color
        val primaryDark = adjustColorBrightness(color, -0.15f)
        val primaryLight = adjustColorBrightness(color, 0.2f)
        
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(primaryLight, primaryColor, primaryDark)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.LINEAR_GRADIENT
            cornerRadius = context.dpToPx(12f).toFloat()
        }
        
        // 添加阴影层
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(adjustColorBrightness(color, -0.25f))
            cornerRadius = context.dpToPx(12f).toFloat()
        }
        
        return LayerDrawable(arrayOf(shadowDrawable, gradientDrawable)).apply {
            setLayerInset(1, context.dpToPx(2f), context.dpToPx(2f), 0, 0)
        }
    }
    
    /**
     * 创建毛玻璃（模糊）背景
     */
    private fun createBlurBackground(bitmap: Bitmap, dominantColor: Int, context: Context): Drawable {
        // 创建模糊背景
        val blurredBitmap = blurBitmap(bitmap, context, 25f)
        
        // 创建覆盖层（主色调半透明）
        val overlay = ColorDrawable(
            Color.argb(180, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor))
        )
        
        return LayerDrawable(arrayOf(
            BitmapDrawable(context.resources, blurredBitmap),
            overlay
        ))
    }
    
    /**
     * 模糊图片
     */
    private fun blurBitmap(bitmap: Bitmap, context: Context, radius: Float): Bitmap {
        return try {
            val rs = RenderScript.create(context)
            val input = Allocation.createFromBitmap(rs, bitmap)
            val output = Allocation.createTyped(rs, input.type)
            
            val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            script.setRadius(radius)
            script.setInput(input)
            script.forEach(output)
            
            output.copyTo(bitmap)
            rs.destroy()
            bitmap
        } catch (e: Exception) {
            bitmap
        }
    }
    
    /**
     * 调整颜色亮度
     */
    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + factor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }
    
    /**
     * 获取最佳文字颜色（确保在背景上可读）
     */
    private fun getOptimalTextColor(backgroundColor: Int): Int {
        return if (isDarkColor(backgroundColor)) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }
    
    /**
     * 判断颜色是否偏暗
     */
    private fun isDarkColor(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }
    
    /**
     * 增强颜色饱和度（使颜色更鲜艳）
     */
    fun enhanceColorSaturation(color: Int, factor: Float = 0.3f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] + factor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }
    
    /**
     * 生成调色板（基于主色调的配色方案）
     */
    fun generateColorPalette(dominantColor: Int): ColorPalette {
        val hsv = FloatArray(3)
        Color.colorToHSV(dominantColor, hsv)
        
        return ColorPalette(
            primary = dominantColor,
            primaryDark = adjustColorBrightness(dominantColor, -0.15f),
            primaryLight = adjustColorBrightness(dominantColor, 0.2f),
            accent = Color.HSVToColor(floatArrayOf((hsv[0] + 150) % 360, 0.7f, 0.9f)), // 互补色
            textPrimary = getOptimalTextColor(dominantColor),
            textSecondary = if (isDarkColor(dominantColor)) {
                Color.parseColor("#B3FFFFFF") // 白色 70%
            } else {
                Color.parseColor("#8A000000") // 黑色 54%
            }
        )
    }
    
    data class BackgroundResult(
        val dominantColor: Int,
        val backgroundDrawable: Drawable,
        val textColor: Int,
        val isDarkBackground: Boolean
    )
    
    data class ColorPalette(
        val primary: Int,
        val primaryDark: Int,
        val primaryLight: Int,
        val accent: Int,
        val textPrimary: Int,
        val textSecondary: Int
    )
    
    enum class BackgroundStyle {
        SOLID,      // 纯色
        GRADIENT,   // 渐变色
        MATERIAL,   // Material Design
        BLUR        // 毛玻璃效果
    }
}

// 扩展函数
fun Context.dpToPx(dp: Float): Int {
    return (dp * resources.displayMetrics.density).toInt()
}