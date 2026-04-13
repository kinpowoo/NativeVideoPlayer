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

public class VerticalSeekBar extends View {

    // 画笔
    private Paint backgroundPaint;
    private Paint progressPaint;
    
    // 绘制区域
    private final RectF backgroundRect = new RectF();
    private final RectF progressRect = new RectF();
    
    // 属性
    private int max = 100;            // 最大值
    private int progress = 0;         // 当前进度
    private float cornerRadius = dp2px(4f);  // 圆角半径
    private float strokeWidth = dp2px(2f);   // 进度条宽度
    
    // 颜色
    private int backgroundColor = 0x80000000;  // 50%透明度的灰色
    private int progressColor = Color.WHITE;   // 进度颜色
    
    // 触摸相关
    private boolean isDragging = false;
    
    // 监听器
    private OnProgressChangeListener listener;
    
    public VerticalSeekBar(Context context) {
        this(context, null);
    }
    
    public VerticalSeekBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public VerticalSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // 计算背景矩形区域，留出边距
        float left = strokeWidth;
        float top = strokeWidth;
        float right = w - strokeWidth;
        float bottom = h - strokeWidth;
        
        backgroundRect.set(left, top, right, bottom);
        
        // 计算进度矩形区域
        updateProgressRect();
    }
    
    /**
     * 更新进度矩形区域
     */
    private void updateProgressRect() {
        float left = strokeWidth;
        float right = backgroundRect.width() + strokeWidth;
        float bottom = backgroundRect.bottom;
        
        // 计算进度高度
        float progressHeight = 0;
        if (max > 0) {
            progressHeight = backgroundRect.height() * ((float) progress / max);
        }
        
        float top = bottom - progressHeight;
        
        progressRect.set(left, top, right, bottom);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 绘制背景圆角矩形
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint);
        
        // 绘制进度圆角矩形
        if (progress > 0) {
            // 创建进度区域的圆角
            float progressRadius = Math.min(cornerRadius, progressRect.height() / 2);
            canvas.drawRoundRect(progressRect, progressRadius, progressRadius, progressPaint);
            
            // 如果进度区域高度小于圆角直径，需要绘制一个矩形
            if (progressRect.height() < cornerRadius * 2) {
                canvas.drawRect(progressRect, progressPaint);
            }
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                updateProgressFromTouch(y);
                return true;
                
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
        updateProgressRect();
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
    
    /**
     * 设置当前进度
     */
    private void setProgress(int progress, boolean fromUser) {
        int newProgress = Math.max(0, Math.min(progress, max));
        if (this.progress != newProgress) {
            this.progress = newProgress;
            
            updateProgressRect();
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
     * 获取圆角半径（px）
     */
    public float getCornerRadius() {
        return cornerRadius;
    }
    
    /**
     * 设置进度条宽度（dp）
     */
    public void setStrokeWidthDp(float dp) {
        this.strokeWidth = dp2px(dp);
        requestLayout();
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
     * 设置背景透明度
     * @param alpha 0-255
     */
    public void setBackgroundAlpha(int alpha) {
        int color = backgroundColor;
        backgroundColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        backgroundPaint.setColor(backgroundColor);
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
     * 设置进度变化监听器
     */
    public void setOnProgressChangeListener(OnProgressChangeListener listener) {
        this.listener = listener;
    }
    
    /**
     * 进度变化监听接口
     */
    public interface OnProgressChangeListener {
        void onProgressChanged(VerticalSeekBar seekBar, int progress, float percent, boolean fromUser);
    }
    
    /**
     * dp转px
     */
    private float dp2px(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}