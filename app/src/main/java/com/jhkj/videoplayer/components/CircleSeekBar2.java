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

public class CircleSeekBar2 extends View {

    // 画笔
    private Paint backgroundPaint;
    private Paint progressPaint;
    private Paint thumbPaint;

    // 绘制区域
    private final RectF arcRect = new RectF();

    // 属性
    private int startAngle = 280;  // 起始角度
    private int endAngle = 260;    // 结束角度
    private float strokeWidth = dp2px(0.5f);    // 背景线宽
    private float progressWidth = dp2px(0.8f);  // 进度线宽
    private float thumbSize = dp2px(2f);        // 滑块半径

    // 触摸相关
    private float touchExtension = dp2px(20f);  // 触摸扩展区域，增加20dp
    private boolean isDragging = false;

    // 颜色
    private int backgroundColor = 0xFFCCCCCC;   // 背景色
    private int progressColor = Color.parseColor("#FFC300");   // 进度色
    private int thumbColor = Color.WHITE;       // 滑块颜色

    // 进度相关
    private int max = 100;       // 最大值
    private int progress = 0;    // 当前进度

    // 监听器
    private OnProgressChangeListener listener;

    public CircleSeekBar2(Context context) {
        this(context, null);
    }

    public CircleSeekBar2(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleSeekBar2(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 启用点击
        setClickable(true);

        // 初始化背景画笔
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(strokeWidth);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);

        // 初始化进度画笔
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(progressWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        // 初始化滑块画笔
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(thumbColor);
        thumbPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 计算绘制区域，留出边距
        int min = Math.min(w, h);
        float padding = Math.max(strokeWidth, progressWidth) + thumbSize + touchExtension;

        float left = (w - min) / 2f + padding;
        float top = (h - min) / 2f + padding;
        float right = left + min - 2 * padding;
        float bottom = top + min - 2 * padding;

        arcRect.set(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 计算总角度
        float sweepAngle = calculateSweepAngle();

        // 绘制背景圆弧
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, backgroundPaint);

        // 绘制进度圆弧
        if (max > 0) {
            float progressSweep = sweepAngle * ((float) progress / max);
            canvas.drawArc(arcRect, startAngle, progressSweep, false, progressPaint);

            // 绘制滑块
            drawThumb(canvas, progressSweep);
        }
    }

    /**
     * 计算总扫过的角度
     */
    private float calculateSweepAngle() {
        if (endAngle > startAngle) {
            return endAngle - startAngle;
        } else {
            return 360 - startAngle + endAngle;
        }
    }

    /**
     * 绘制滑块
     */
    private void drawThumb(Canvas canvas, float progressSweep) {
        if (max == 0) return;

        float radius = arcRect.width() / 2f;
        float centerX = arcRect.centerX();
        float centerY = arcRect.centerY();

        // 计算滑块位置的角度
        float thumbAngle = startAngle + progressSweep;
        if (thumbAngle >= 360) {
            thumbAngle -= 360;
        }

        // 将角度转换为弧度
        float radian = (float) Math.toRadians(thumbAngle);

        // 计算滑块位置
        float thumbX = centerX + radius * (float) Math.cos(radian);
        float thumbY = centerY + radius * (float) Math.sin(radian);

        // 绘制滑块
        canvas.drawCircle(thumbX, thumbY, thumbSize, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 在触摸扩展区域内就响应
                if (isPointInTouchArea(x, y)) {
                    isDragging = true;
                    updateProgressFromTouch2(x, y);
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    updateProgressFromTouch2(x, y);
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                // 即使抬起也要返回true，表示消费了事件
                if (isPointInTouchArea(x, y) || isDragging) {
                    return true;
                }
                break;
        }

        return super.onTouchEvent(event);
    }

    /**
     * 判断触摸点是否在触摸区域内
     * 增加了外部margin，更容易触发
     */
    private boolean isPointInTouchArea(float x, float y) {
        float centerX = arcRect.centerX();
        float centerY = arcRect.centerY();
        float radius = arcRect.width() / 2f;

        // 计算触摸点到圆心的距离
        float distance = (float) Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));

        // 触摸区域：圆环半径 ± 扩展距离
        float minDistance = Math.max(0, radius - touchExtension);
        float maxDistance = radius + touchExtension;

        return distance >= minDistance && distance <= maxDistance;
    }

    /**
     * 根据触摸点更新进度
     */
    private void updateProgressFromTouch2(float x, float y) {
        float centerX = arcRect.centerX();
        float centerY = arcRect.centerY();

        // 计算触摸点相对于圆心的角度
        double dx = x - centerX;
        double dy = y - centerY;

        // 计算角度（弧度），0度在3点钟方向
        double angleRad = Math.atan2(dy, dx);

        // 转换为角度，0-360度
        double angleDeg = Math.toDegrees(angleRad);
        if (angleDeg < 0) {
            angleDeg += 360;
        }

        // 计算相对于起始角度的偏移角度
        float offsetAngle = (float) ((angleDeg - startAngle + 360) % 360);

        // 计算总角度
        float sweepAngle = calculateSweepAngle();

        // 判断触摸点是否在有效范围内
        // 规则：如果触摸点在圆弧范围内，或者比当前角度更接近圆弧边界，则允许移动
        // 如果角度在空白区域（从endAngle到startAngle的20度空间），禁止移动
        if (offsetAngle > sweepAngle && offsetAngle < 360) {
            // 触摸点在空白区域，不允许拖动穿过
            return;
        }

        int newProgress;

        // 计算进度
        if (offsetAngle <= sweepAngle) {
            // 在有效角度范围内
            float percent = offsetAngle / sweepAngle;
            newProgress = (int) (percent * max);
        } else {
            // 这里理论上不会执行，因为上面已经排除了空白区域
            return;
        }

        setProgress(newProgress, true);
    }


    /**
     * 根据触摸点更新进度
     */
    private void updateProgressFromTouch(float x, float y) {
        float centerX = arcRect.centerX();
        float centerY = arcRect.centerY();

        // 计算触摸点相对于圆心的角度
        double dx = x - centerX;
        double dy = y - centerY;

        // 计算角度（弧度），0度在3点钟方向
        double angleRad = Math.atan2(dy, dx);

        // 转换为角度，0-360度
        double angleDeg = Math.toDegrees(angleRad);
        if (angleDeg < 0) {
            angleDeg += 360;
        }

        // 计算相对于起始角度的偏移角度
        float offsetAngle = (float) ((angleDeg - startAngle + 360) % 360);

        // 计算总角度
        float sweepAngle = calculateSweepAngle();

        int newProgress;

        // 判断角度是否在有效范围内
        if (offsetAngle <= sweepAngle) {
            // 在有效角度范围内
            float percent = offsetAngle / sweepAngle;
            newProgress = (int) (percent * max);
        } else {
            // 判断靠近起始点还是结束点
            if (offsetAngle - sweepAngle < 180) {
                // 靠近结束点
                newProgress = max;
            } else {
                // 靠近起始点
                newProgress = 0;
            }
        }

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
     * @param progress 进度值
     * @param fromUser 是否来自用户操作
     */
    private void setProgress(int progress, boolean fromUser) {
        int newProgress = Math.max(0, Math.min(progress, max));
        if (this.progress != newProgress) {
            this.progress = newProgress;

            // 立即重绘
            invalidate();

            if (listener != null) {
                float percent = max > 0 ? (float) this.progress / max : 0;
                listener.onProgressChanged(this, this.progress, percent, fromUser);
            }
        } else if (fromUser) {
            // 即使进度没变，如果是用户操作也需要回调
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
     * 设置背景线宽（dp）
     */
    public void setStrokeWidthDp(float dp) {
        this.strokeWidth = dp2px(dp);
        backgroundPaint.setStrokeWidth(strokeWidth);
        invalidate();
    }

    /**
     * 设置进度线宽（dp）
     */
    public void setProgressWidthDp(float dp) {
        this.progressWidth = dp2px(dp);
        progressPaint.setStrokeWidth(progressWidth);
        invalidate();
    }

    /**
     * 设置滑块大小（dp）
     */
    public void setThumbSizeDp(float dp) {
        this.thumbSize = dp2px(dp);
        invalidate();
    }

    /**
     * 设置触摸扩展区域大小（dp）
     * 这个值越大，越容易触发拖拽
     */
    public void setTouchExtensionDp(float dp) {
        this.touchExtension = dp2px(dp);
        // 需要重新计算绘制区域
        requestLayout();
    }

    /**
     * 设置背景色
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
     * 设置起始角度
     */
    public void setStartAngle(int angle) {
        this.startAngle = angle;
        invalidate();
    }

    /**
     * 设置结束角度
     */
    public void setEndAngle(int angle) {
        this.endAngle = angle;
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
        void onProgressChanged(CircleSeekBar2 seekBar, int progress, float percent, boolean fromUser);
    }

    /**
     * dp转px
     */
    private float dp2px(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}