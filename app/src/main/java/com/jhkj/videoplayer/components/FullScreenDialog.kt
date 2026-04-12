package com.jhkj.videoplayer.components

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.*
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import com.jhkj.videoplayer.R
import java.lang.ref.WeakReference

/**
 * 全屏无动画弹窗
 * @param context 必须是Activity的上下文
 */
class FullScreenDialog(
    context: Context
) : Dialog(context) {
    private var isCancelable = true
    private var photoHolder : PhotoView? = null
    private var ref: WeakReference<PhotoView>? = null

    init {
        // 检查context是否为Activity
        if (context !is Activity) {
            throw IllegalArgumentException("Context must be an Activity")
        }
        
        // 设置无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        setContentView(R.layout.photo_display_layout)
        
        // 配置窗口属性
        window?.let { window ->
            // 清除所有动画
            window.setWindowAnimations(0)
            
            // 设置全屏
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            
            // 设置背景透明
            window.setBackgroundDrawableResource(android.R.color.transparent)
            
            // 清除对话框的装饰视图
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT

            // 设置沉浸式
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            
            // 确保弹窗显示在键盘之上
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            
            // 设置窗口类型，确保显示在所有内容之上
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL)
            
            // 设置窗口属性
            val params = window.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
//            params.gravity = Gravity.CENTER
            params.flags = params.flags or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            window.attributes = params
        }
    }
    
    override fun setContentView(layoutResID: Int) {
        val view = LayoutInflater.from(context).inflate(layoutResID, null)
        setContentView(view)
    }
    
    @SuppressLint("ClickableViewAccessibility")
    override fun setContentView(view: View) {
        super.setContentView(wrapContentView(view))
        photoHolder = view.findViewById(R.id.photo_holder)
        ref = WeakReference(photoHolder)
        photoHolder?.setOnClickListener {
            dismiss()
        }
    }
    
    override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
        super.setContentView(wrapContentView(view), params)
    }

    fun loadFilePath(filePath: String){
        photoHolder?.let {
            Glide.with(context).load(filePath).listener(object: RequestListener<Drawable>{
                override fun onLoadFailed(
                    p0: GlideException?,
                    p1: Any?,
                    p2: Target<Drawable>?,
                    p3: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    p0: Drawable,
                    p1: Any?,
                    p2: Target<Drawable>?,
                    p3: DataSource?,
                    p4: Boolean
                ): Boolean {
                    ref?.get()?.setImageDrawable(p0)
                    return true
                }
            }).preload()
        }
    }
    fun loadUri(fileUri: Uri){
        photoHolder?.setImageURI(fileUri)
    }
    fun loadUrl(url:String){
        photoHolder?.let {
            Glide.with(context).load(url).listener(object: RequestListener<Drawable>{
                override fun onLoadFailed(
                    p0: GlideException?,
                    p1: Any?,
                    p2: Target<Drawable>?,
                    p3: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    p0: Drawable,
                    p1: Any?,
                    p2: Target<Drawable>?,
                    p3: DataSource?,
                    p4: Boolean
                ): Boolean {
                    ref?.get()?.setImageDrawable(p0)
                    return true
                }
            }).preload()
        }
    }
    
    /**
     * 包装内容视图，添加点击外部不消失的处理
     */
    private fun wrapContentView(view: View): View {
        val container = FrameLayout(context)
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        // 添加遮罩层
        val maskView = View(context)
        maskView.setBackgroundColor("#80000000".toColorInt())
        maskView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(maskView)
        
        // 添加内容视图
        val contentParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
//        contentParams.gravity = Gravity.CENTER
        view.layoutParams = contentParams
        container.addView(view)
        
        // 设置点击遮罩层关闭弹窗
        maskView.setOnClickListener {
            if (isCancelable) {
                dismiss()
            }
        }
        
        return container
    }

    override fun setCancelable(flag: Boolean) {
        super.setCancelable(flag)
        isCancelable = flag
    }

    /**
     * 显示弹窗
     */
    override fun show() {
        try {
            // 禁止外部点击关闭
            setCanceledOnTouchOutside(false)
            super.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}