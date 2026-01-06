package com.jhkj.videoplayer.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import com.airbnb.lottie.LottieAnimationView
import com.jhkj.videoplayer.R

class LottieDialog(context: Context) :
    Dialog(context, R.style.AlertDialogStyle) {
    private var lottieView: LottieAnimationView? = null
    private var sucRes:String? = null
    private var failRes:String? = null
    private var showSuc = true

    init {
        setContentView(R.layout.commom_dialog)
        setCanceledOnTouchOutside(false)
        setCancelable(false)

        lottieView = findViewById(R.id.animation_view)
        lottieView?.addAnimatorListener(object: AnimatorListenerAdapter(){
            override fun onAnimationEnd(animation: Animator) {
                dismiss()
            }
        })

        //设置宽高
        if (window != null) {
            val params = window?.attributes
            params?.width = ViewGroup.LayoutParams.WRAP_CONTENT
            params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            // 设置居中
            params?.gravity = Gravity.CENTER
            window?.setAttributes(params)
            window?.setBackgroundDrawableResource(R.color.transparent)
        }
        setOnDismissListener {
            lottieView?.cancelAnimation()
        }
    }

    fun setAnimRes(suc:String,fail:String){
        sucRes = suc
        failRes = fail
    }

    fun showSucRes(x: Boolean){
        showSuc = x
        if(x){
            lottieView?.setAnimation(sucRes ?: "success.json")
        }else{
            lottieView?.setAnimation(failRes ?: "failed.json")
        }
    }

    override fun show() {
        try {
            if (window == null) return
            super.show()
            lottieView?.playAnimation()
        } catch (e: Exception) {
            Log.e("DialogError", "show in null activity or window")
        }
    }

    override fun onDetachedFromWindow() {
        lottieView?.cancelAnimation()
        super.onDetachedFromWindow()
    }

    private fun backgroundAlpha(c: Context, bgAlpha: Float) {
        val lp = (c as Activity).window.attributes
        lp.alpha = bgAlpha //0.0-1.0
        c.window.setAttributes(lp)
        c.window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
}
