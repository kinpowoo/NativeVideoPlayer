package com.jhkj.videoplayer.player

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable

class PaletteGradientDrawable(private val colors: IntArray) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderMatrix = Matrix()
    
    init {
        // 创建线性渐变
//        val linearGradient = LinearGradient(
//            0f, 0f, 1f, 1f,  // 起点和终点（归一化坐标）
//            colors,
//            null,  // 位置数组，null 表示均匀分布
//            Shader.TileMode.CLAMP
//        )
        
        // 创建角度渐变
//        val sweepGradient = SweepGradient(0.5f, 0.5f, colors, null)
//
        // 或者使用 RadialGradient
        val radialGradient = RadialGradient(
            0.5f, 0.5f, 0.5f,  // 中心点, 半径
            colors,
            null,
            Shader.TileMode.CLAMP
        )
        
        // 为 shader 应用矩阵，可以旋转或缩放渐变
        shaderMatrix.setRotate(45f, 0.5f, 0.5f)
        radialGradient.setLocalMatrix(shaderMatrix)
        
        paint.shader = radialGradient
    }
    
    override fun draw(canvas: Canvas) {
        canvas.drawPaint(paint)
    }
    
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter 
    }
}