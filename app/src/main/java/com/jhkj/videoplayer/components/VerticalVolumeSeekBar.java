package com.jhkj.videoplayer.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class VerticalVolumeSeekBar extends View {

    // 画笔
    private Paint backgroundPaint;
    private Paint progressPaint;
    private Paint thumbPaint;
    private Paint borderPaint;
    
    // 绘制区域
    private final RectF backgroundRect = new RectF();
    private final RectF progressRect = new RectF();
    private final RectF thumbRect = new RectF();
    
    // 属性
    private int max = 100;            // 最大值
    private int progress = 0;         // 当前进度
    private float cornerRadius = dp2px(8f);  // 圆角半径
    private float strokeWidth = dp2px(16f);   // 进度条宽度
    private final float outsideBounds = dp2px(20f);   //
    // 滑块属性
    private boolean showThumb = true;         // 是否显示滑块
    private float thumbSize = dp2px(16f);     // 滑块大小
    private int thumbColor = Color.WHITE;     // 滑块颜色
    
    // 边框属性
    private boolean showBorder = false;        // 是否显示边框
    private float borderWidth = dp2px(1f);     // 边框宽度
    private int borderColor = Color.GRAY;      // 边框颜色
    
    // 颜色
    private int backgroundColor = 0x80000000;  // 50%透明度的灰色
    private int progressColor = Color.WHITE;   // 进度颜色
    
    // 触摸相关
    private boolean isDragging = false;
    
    // 监听器
    private OnProgressChangeListener listener;
    
    public VerticalVolumeSeekBar(Context context) {
        this(context, null);
    }
    
    public VerticalVolumeSeekBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public VerticalVolumeSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // 启用点击
        setClickable(true);
        
        // 初始化背景画笔
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.FILL);
        
        // 初始化进度画笔
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.FILL);
        
        // 初始化滑块画笔
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(thumbColor);
        thumbPaint.setStyle(Paint.Style.FILL);
        
        // 初始化边框画笔
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(borderColor);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // 计算背景矩形区域，考虑滑块和边距
        float padding = Math.max(strokeWidth, thumbSize / 2);
        float left = padding;
        float top = padding;
        float right = w - padding;
        float bottom = h - padding;
        
        backgroundRect.set(left, top, right, bottom);
        
        // 更新进度和滑块位置
        updateProgressAndThumb();
    }
    
    /**
     * 更新进度和滑块位置
     */
    private void updateProgressAndThumb() {
        // 计算进度矩形区域
        float left = backgroundRect.left;
        float right = backgroundRect.right;
        float bottom = backgroundRect.bottom;
        
        // 计算进度高度
        float progressHeight = 0;
        if (max > 0) {
            progressHeight = backgroundRect.height() * ((float) progress / max);
        }
        
        float top = bottom - progressHeight;
        progressRect.set(left, top, right, bottom);
        
        // 计算滑块位置
        if (showThumb) {
            float thumbCenterY = top;  // 滑块中心在进度顶部
            float thumbLeft = left - thumbSize / 2;
            float thumbRight = right + thumbSize / 2;
            float thumbTop = thumbCenterY - thumbSize / 2;
            float thumbBottom = thumbCenterY + thumbSize / 2;
            
            thumbRect.set(thumbLeft, thumbTop, thumbRight, thumbBottom);
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 绘制背景圆角矩形
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint);
        
        // 绘制边框
        if (showBorder) {
            canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, borderPaint);
        }
        
        // 绘制进度圆角矩形
        if (progress > 0) {
            // 计算进度区域的圆角
            float progressRadius = Math.min(cornerRadius, progressRect.height() / 2);
            canvas.drawRoundRect(progressRect, progressRadius, progressRadius, progressPaint);
            
            // 如果进度区域高度小于圆角直径，需要绘制一个矩形
//            if (progressRect.height() < cornerRadius * 2) {
//                canvas.drawRect(progressRect, progressPaint);
//            }
        }
        
        // 绘制滑块
        if (showThumb) {
            // 绘制圆形滑块
            float thumbCenterX = thumbRect.centerX();
            float thumbCenterY = thumbRect.centerY();
            float thumbRadius = thumbSize / 2;
            
            // 添加阴影效果
            thumbPaint.setShadowLayer(dp2px(2), 0, 0, 0x66000000);
            canvas.drawCircle(thumbCenterX, thumbCenterY, thumbRadius, thumbPaint);
            thumbPaint.clearShadowLayer();
            
            // 添加内圆
            thumbPaint.setColor(0xFFE0E0E0);
            canvas.drawCircle(thumbCenterX, thumbCenterY, thumbRadius * 0.7f, thumbPaint);
            thumbPaint.setColor(thumbColor);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isPointInTouchArea(x, y)) {
                    isDragging = true;
                    updateProgressFromTouch(y);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    updateProgressFromTouch(y);
                    return true;
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                break;
        }
        
        return super.onTouchEvent(event);
    }
    
    /**
     * 判断触摸点是否在触摸区域内
     */
    private boolean isPointInTouchArea(float x, float y) {
        // 检查是否在滑块上
        if (backgroundRect.contains(x, y)) {
            return true;
        }
        if( x > (backgroundRect.left-outsideBounds) && x <(backgroundRect.right+outsideBounds) &&
              y > (backgroundRect.top-outsideBounds) && x <(backgroundRect.bottom+outsideBounds)){
            return true;
        }
        // 检查是否在进度条附近
        return false;
    }
    
    /**
     * 根据触摸位置更新进度
     */
    private void updateProgressFromTouch(float y) {
        // 限制y坐标在有效范围内
        float validY = Math.max(backgroundRect.top, Math.min(y, backgroundRect.bottom));
        
        // 计算进度百分比（从底部到顶部，0-100%）
        float height = backgroundRect.height();
        float offsetFromBottom = backgroundRect.bottom - validY;
        float percent = offsetFromBottom / height;
        
        // 计算进度值
        int newProgress = Math.round(percent * max);
        newProgress = Math.max(0, Math.min(newProgress, max));
        
        setProgress(newProgress, true);
    }
    
    /**
     * 设置最大值
     */
    public void setMax(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be > 0");
        }
        this.max = max;
        if (progress > max) {
            progress = max;
        }
        updateProgressAndThumb();
        invalidate();
    }
    
    /**
     * 获取最大值
     */
    public int getMax() {
        return max;
    }
    
    /**
     * 设置当前进度
     */
    public void setProgress(int progress) {
        setProgress(progress, false);
    }
    
    private void setProgress(int progress, boolean fromUser) {
        int newProgress = Math.max(0, Math.min(progress, max));
        if (this.progress != newProgress) {
            this.progress = newProgress;
            
            updateProgressAndThumb();
            invalidate();
            
            if (listener != null) {
                float percent = max > 0 ? (float) this.progress / max : 0;
                listener.onProgressChanged(this, this.progress, percent, fromUser);
            }
        } else if (fromUser) {
            if (listener != null) {
                float percent = max > 0 ? (float) this.progress / max : 0;
                listener.onProgressChanged(this, this.progress, percent, fromUser);
            }
        }
    }
    
    /**
     * 获取当前进度
     */
    public int getProgress() {
        return progress;
    }
    
    /**
     * 设置圆角半径（dp）
     */
    public void setCornerRadiusDp(float dp) {
        this.cornerRadius = dp2px(dp);
        invalidate();
    }
    
    /**
     * 设置进度条宽度（dp）
     */
    public void setStrokeWidthDp(float dp) {
        this.strokeWidth = dp2px(dp);
        requestLayout();
    }
    
    /**
     * 设置滑块大小（dp）
     */
    public void setThumbSizeDp(float dp) {
        this.thumbSize = dp2px(dp);
        requestLayout();
    }
    
    /**
     * 设置是否显示滑块
     */
    public void setShowThumb(boolean show) {
        this.showThumb = show;
        invalidate();
    }
    
    /**
     * 设置是否显示边框
     */
    public void setShowBorder(boolean show) {
        this.showBorder = show;
        invalidate();
    }
    
    /**
     * 设置边框宽度（dp）
     */
    public void setBorderWidthDp(float dp) {
        this.borderWidth = dp2px(dp);
        borderPaint.setStrokeWidth(borderWidth);
        invalidate();
    }
    
    /**
     * 设置边框颜色
     */
    public void setBorderColor(int color) {
        this.borderColor = color;
        borderPaint.setColor(color);
        invalidate();
    }
    
    /**
     * 设置背景颜色
     */
    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
        backgroundPaint.setColor(color);
        invalidate();
    }
    
    /**
     * 设置进度条颜色
     */
    public void setProgressColor(int color) {
        this.progressColor = color;
        progressPaint.setColor(color);
        invalidate();
    }
    
    /**
     * 设置滑块颜色
     */
    public void setThumbColor(int color) {
        this.thumbColor = color;
        thumbPaint.setColor(color);
        invalidate();
    }
    
    /**
     * 设置进度变化监听器
     */
    public void setOnProgressChangeListener(OnProgressChangeListener listener) {
        this.listener = listener;
    }
    
    /**
     * 进度变化监听接口
     */
    public interface OnProgressChangeListener {
        void onProgressChanged(VerticalVolumeSeekBar seekBar, int progress, float percent, boolean fromUser);
    }
    
    /**
     * dp转px
     */
    private float dp2px(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}