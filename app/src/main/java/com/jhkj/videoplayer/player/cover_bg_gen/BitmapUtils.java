package com.jhkj.videoplayer.player.cover_bg_gen;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

public class BitmapUtils {

    /**
     * 生成一张优雅的默认唱片机占位 Bitmap
     * 适合作为模糊背景的原始输入
     *
     * @param width  生成图片的宽度（建议设为 512 或 1024）
     * @param height 生成图片的高度
     * @return 优雅的渐变占位 Bitmap
     */
    public static Bitmap createDefaultCover(int width, int height) {
        // 1. 创建一个支持透明度的 ARGB_8888 Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); // 开启抗锯齿

        // ---- 设计理念：清冷温润的极简渐变 ----
        // 使用冷灰色 vec3(0.9, 0.92, 0.95) 到 暖白色 vec3(1.0, 0.98, 0.96) 的线性渐变
        // 这会让模糊后的背景产生微妙的冷暖流动感
        int colorStart = Color.parseColor("#E1E6F0"); // 清冷银灰
        int colorEnd = Color.parseColor("#FFF5EE");   // 温润雪白

        // 2. 绘制背景线性渐变（从左上到右下）
        LinearGradient backGradient = new LinearGradient(
                0, 0, width, height,
                colorStart, colorEnd,
                Shader.TileMode.CLAMP);
        paint.setShader(backGradient);
        canvas.drawRect(0, 0, width, height, paint);

        // 3. 绘制一个柔和的“内发光”唱片机轮廓
        // 使用径向渐变模拟唱片机的凹陷和光晕质感
        paint.setShader(null); // 清除之前的渐变
        
        // 定义唱片机的中心和半径
        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = Math.min(width, height) * 0.4f; // 占画面的 80%

        // 径向渐变颜色：中心透明 -> 边缘浅灰 -> 极边缘更浅
        int[] ringColors = new int[]{
                Color.TRANSPARENT,           // 中心透明
                Color.parseColor("#10000000"), // 极淡的黑色（模拟凹陷阴影）
                Color.parseColor("#05000000")  // 更淡的黑色
        };
        // 渐变位置：0% -> 70% -> 100%
        float[] ringStops = new float[]{0.0f, 0.7f, 1.0f};

        RadialGradient ringGradient = new RadialGradient(
                centerX, centerY, radius,
                ringColors, ringStops,
                Shader.TileMode.CLAMP);
        
        paint.setShader(ringGradient);
        // 使用这种方式可以让渐变看起来像一个“内阴影”圆环
        canvas.drawCircle(centerX, centerY, radius, paint);

        // 4. 绘制一个极小的中心孔（增加辨识度）
        paint.setShader(null);
        paint.setColor(Color.parseColor("#20000000")); // 淡淡的灰色
        canvas.drawCircle(centerX, centerY, radius * 0.03f, paint); // 半径的 3%

        return bitmap;
    }

    /**
     * 生成一张优雅的、暗黑色调的默认音乐占位 Bitmap
     * 适合作为模糊背景的原始输入
     *
     * @param width  生成图片的宽度（建议设为 1024 或 2048，增加波形高频细节）
     * @param height 生成图片的高度
     * @return 优雅的暗黑音乐渐变占位 Bitmap
     */
    public static Bitmap createDefaultCoverMusicDark(int width, int height) {
        // 1. 创建一个支持透明度的 ARGB_8888 Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); // 开启抗锯齿

        // ---- 设计理念：深邃清冷的暗黑流光 ----
        // 使用深炭灰 vec3(0.07, 0.07, 0.07) 到 深蓝黑 vec3(0.1, 0.1, 0.1) 的线性渐变
        int colorStart = Color.parseColor("#121212"); // 深炭灰
        int colorEnd = Color.parseColor("#181818");   // 深蓝黑

        // 2. 绘制背景线性渐变（从左上到右下）
        LinearGradient backGradient = new LinearGradient(
                0, 0, width, height,
                colorStart, colorEnd,
                Shader.TileMode.CLAMP);
        paint.setShader(backGradient);
        canvas.drawRect(0, 0, width, height, paint);

        // ---- 核心：绘制抽象高频波形纹理（音乐元素） ----
        paint.setShader(null); // 清除之前的渐变
        paint.setStyle(Paint.Style.STROKE); // 设为描边模式
        paint.setStrokeWidth(Math.max(width, height) * 0.002f); // 设定极细的描边宽度（例如2/1000）

        // 淡淡的灰色：#30FFFFFF (30%透明度的纯白)
        paint.setColor(Color.parseColor("#30FFFFFF"));

        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = Math.min(width, height) * 0.45f; // 波形占画面的 90%

        // 3. 绘制多组具有不同高频特征的矢量波形 Path
        Path musicPath = new Path();

        // --- 纹理 A: 高频锯齿波 (模拟频谱细节) ---
        float waveFrequencyA = 50.0f; // 频率 A
        float waveAmplitudeA = radius * 0.08f; // 振幅 A
        for (int i = 0; i <= width; i += 2) {
            float dx = (float) i;
            float dy = centerY + (float) Math.sin((float) i / width * Math.PI * 2 * waveFrequencyA) * waveAmplitudeA;
            if (i == 0) musicPath.moveTo(dx, dy);
            else musicPath.lineTo(dx, dy);
        }
        canvas.drawPath(musicPath, paint);

        // --- 纹理 B: 高频正弦波 (增加色块交融) ---
        musicPath.reset(); // 重置 Path
        float waveFrequencyB = 30.0f; // 频率 B
        float waveAmplitudeB = radius * 0.12f; // 振幅 B
        for (int i = 0; i <= width; i += 2) {
            float dx = (float) i;
            float dy = (centerY - radius * 0.15f) + (float) Math.sin((float) i / width * Math.PI * 2 * waveFrequencyB) * waveAmplitudeB;
            if (i == 0) musicPath.moveTo(dx, dy);
            else musicPath.lineTo(dx, dy);
        }
        canvas.drawPath(musicPath, paint);

        // --- 纹理 C: 极高频脉冲波 (抹除后的烟雾感) ---
        musicPath.reset(); // 重置 Path
        float waveFrequencyC = 70.0f; // 频率 C
        float waveAmplitudeC = radius * 0.05f; // 振幅 C
        paint.setColor(Color.parseColor("#20FFFFFF")); // 更淡的灰色
        paint.setStrokeWidth(Math.max(width, height) * 0.0015f); // 更细的描边
        for (int i = 0; i <= width; i += 2) {
            float dx = (float) i;
            float dy = (centerY + radius * 0.15f) + (float) Math.sin((float) i / width * Math.PI * 2 * waveFrequencyC) * waveAmplitudeC;
            if (i == 0) musicPath.moveTo(dx, dy);
            else musicPath.lineTo(dx, dy);
        }
        canvas.drawPath(musicPath, paint);

        return bitmap;
    }
}