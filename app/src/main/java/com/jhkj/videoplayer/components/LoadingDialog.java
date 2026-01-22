package com.jhkj.videoplayer.components;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.jhkj.videoplayer.R;
public class LoadingDialog extends Dialog {
    private boolean isAttached = false;

    @SuppressLint("UseCompatLoadingForDrawables")
    public LoadingDialog(final Context context){
        super(context,com.jhkj.videoplayer.R.style.AlertDialogStyle);
        setContentView(R.layout.common_loading_layout);
        setCanceledOnTouchOutside(false);
        setCancelable(false);

        ConstraintLayout rootView = findViewById(R.id.root_view);
        //设置宽高
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        // 设置居中
        params.gravity = Gravity.CENTER;
        getWindow().setAttributes(params);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.isAttached = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.isAttached = false;
    }

    @Override
    public void dismiss() {
        if(isAttached && isShowing()) {
            super.dismiss();
        }
    }

    @Override
    public void show() {
        if(!isShowing()) {
            super.show();
        }
    }

    private void backgroundAlpha(Context c, float bgAlpha)
    {
        WindowManager.LayoutParams lp =((Activity)c).getWindow().getAttributes();
        lp.alpha = bgAlpha; //0.0-1.0
        ((Activity)c).getWindow().setAttributes(lp);
        ((Activity)c).getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }
}
