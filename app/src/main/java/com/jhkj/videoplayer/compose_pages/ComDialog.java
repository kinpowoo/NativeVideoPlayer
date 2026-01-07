package com.jhkj.videoplayer.compose_pages;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.jhkj.gl_player.util.DensityUtil;
import com.jhkj.videoplayer.R;
import com.jhkj.videoplayer.utils.TextViewLinesUtil;


/**
 * Created by anzhuo on 2016/11/4.
 */

public class ComDialog extends Dialog {

    private DialogInterface onConfirmListener;
    private DialogInterface onCancelListener;
    private TextView tv_message;
    private TextView tv_title;
    private TextView positiveBtn;
    private TextView negativeBtn;
    private boolean isAutoDismiss;
    private boolean isCancelByBtn = false;
    private boolean isTouchCancelByOutside = false;

    private ComDialog setOnConfirmListener(DialogInterface onConfirmListener) {
        this.onConfirmListener = onConfirmListener;
        return this;
    }

    private ComDialog setOnCancelListener(DialogInterface onCancelListener) {
        this.onCancelListener = onCancelListener;
        return this;
    }


    public ComDialog(Context context) {
        super(context,R.style.ScaleInAnimDialog);
        setContentView(R.layout.common_alert_dialog_layout);
        setCanceledOnTouchOutside(false);
        initViews();
    }

    public ComDialog(Context context, int theme) {
        super(context, R.style.ScaleInAnimDialog);
        setContentView(R.layout.common_alert_dialog_layout);
        setCanceledOnTouchOutside(false);
        initViews();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if(getWindow() != null) {
            IBinder token = getWindow().getDecorView().getWindowToken();
        }
    }

    private void initViews() {
        Dialog dialog = this;
        positiveBtn = findViewById(R.id.positive_btn);
        positiveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onConfirmListener != null) {
                    onConfirmListener.onClick(dialog);
                }
                isCancelByBtn = true;
                if(isAutoDismiss) {
                    dismiss();
                }
            }
        });
        negativeBtn = findViewById(R.id.close_btn);
        negativeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onCancelListener != null) {
                    onCancelListener.onClick(dialog);
                }
                isCancelByBtn = true;
                if(isAutoDismiss) {
                    dismiss();
                }
            }
        });

        tv_message = findViewById(R.id.message_tv);
        tv_title = findViewById(R.id.title);

        setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                if(isTouchCancelByOutside && !isCancelByBtn){
                    if (onCancelListener != null) {
                        onCancelListener.onClick(dialog);
                    }
                }
            }
        });

        if(getWindow() != null) {
            WindowManager.LayoutParams p = getWindow().getAttributes();
            p.width = DensityUtil.getScreenWidth(getContext());
            p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            getWindow().setAttributes(p);
        }
    }


    /**
     * 给Dialog设置提示信息
     */
    private void setMessage(@StringRes int message) {
        if (message != -1) {
            tv_message.setText(message);
            int width = DensityUtil.getScreenWidth(getContext());
            float paddingWidth = DensityUtil.dip2px(getContext(),110f);
            int tvMessageWidth = (int) (width - paddingWidth);
            tv_message.setMovementMethod(new ScrollingMovementMethod());
            int lines = TextViewLinesUtil.getTextViewLines(tv_message, tvMessageWidth);
            if(lines <= 1){
                // 1行居中
                tv_message.setGravity(Gravity.CENTER);
            }else{
                tv_message.setGravity(View.TEXT_ALIGNMENT_TEXT_START);
            }
        } else {
            tv_message.setText("");
        }
    }

    private void setMessageStr(String message) {
        if (!TextUtils.isEmpty(message)) {
            tv_message.setText(message);
            int width = DensityUtil.getScreenWidth(getContext());
            float paddingWidth = DensityUtil.dip2px(getContext(), 110f);
            int tvMessageWidth = (int) (width - paddingWidth);
            tv_message.setMovementMethod(new ScrollingMovementMethod());
            int lines = TextViewLinesUtil.getTextViewLines(tv_message, tvMessageWidth);
            if (lines <= 1) {
                // 1行居中
                tv_message.setGravity(Gravity.CENTER);
            } else {
                tv_message.setGravity(View.TEXT_ALIGNMENT_TEXT_START);
            }
        } else {
            tv_message.setText("");
        }
    }

    /**
     * 给Dialog设置提示信息
     */
    private void setSpannableMessage(SpannableString message) {
        if (message != null && message.length() > 0) {
            tv_message.setText(message);
            int width = DensityUtil.getScreenWidth(getContext());
            float paddingWidth = DensityUtil.dip2px(getContext(),110f);
            int tvMessageWidth = (int) (width - paddingWidth);
            tv_message.setMaxHeight(width);
            //linkMovementMethod继承自 ScrollingMovementMethod，可以滑动
            tv_message.setMovementMethod(new LinkMovementMethod());
            tv_message.setHighlightColor(Color.TRANSPARENT);
            int lines = TextViewLinesUtil.getTextViewLines(tv_message, tvMessageWidth);
            if(lines <= 1){
                // 1行居中
                tv_message.setGravity(Gravity.CENTER);
            }else{
                tv_message.setGravity(View.TEXT_ALIGNMENT_TEXT_START);
            }
        } else {
            tv_message.setText("");
        }
    }

    public void setAutoDismiss(boolean autoDismiss){
        isAutoDismiss = autoDismiss;
    }

    public void setCancelOnTouchOutside(){
        isTouchCancelByOutside = true;
        setCanceledOnTouchOutside(true);
    }


    public void setNoButtons(boolean noButtons){
        if(noButtons) {
            positiveBtn.setVisibility(View.GONE);
            negativeBtn.setVisibility(View.GONE);
        }
    }

    /**
     * 给Dialog设置提示信息
     */
    private void setTitleRes(@StringRes int message) {
        if (message != -1) {
            tv_title.setVisibility(View.VISIBLE);
            tv_title.setText(message);
        } else {
            tv_title.setVisibility(View.GONE);
            ConstraintLayout.LayoutParams layoutParams =
                    (ConstraintLayout.LayoutParams) tv_message.getLayoutParams();
            if(layoutParams != null){
                int dp30 = (int)DensityUtil.dip2px(getContext(),30f);
                layoutParams.topMargin = dp30;
                layoutParams.bottomMargin = dp30;
                tv_message.setLayoutParams(layoutParams);
            }
        }
    }

    private void setTitleStr(String titleStr) {
        if (!TextUtils.isEmpty(titleStr)) {
            tv_title.setVisibility(View.VISIBLE);
            tv_title.setText(titleStr);
        } else {
            tv_title.setVisibility(View.GONE);
            ConstraintLayout.LayoutParams layoutParams =
                    (ConstraintLayout.LayoutParams) tv_message.getLayoutParams();
            if(layoutParams != null){
                int dp30 = (int)DensityUtil.dip2px(getContext(),30f);
                layoutParams.topMargin = dp30;
                layoutParams.bottomMargin = dp30;
                tv_message.setLayoutParams(layoutParams);
            }
        }
    }

    /**
     * 给Dialog设置两个按钮的文字
     */
    private void setButtonText(@StringRes int negativeStr,int positiveStr) {
        if (positiveStr != -1) {
            positiveBtn.setText(positiveStr);
        }
        if (negativeStr != -1) {
            negativeBtn.setText(negativeStr);
        }
    }

    public static class Builder {
        private @StringRes int messageStrRes = -1;
        private String messageStr = "";
        private @StringRes int titleRes = -1;
        private String titleStr = "";
        private @StringRes int negativeStr = -1;
        private @StringRes int positiveStr = -1;
        private final Context context;
        private DialogInterface onConfirmListener;
        private DialogInterface onCancelListener;
        private boolean isAutoDismiss = true;
        private boolean touchOutsideDismiss = false;
        //正文
        private SpannableString spannableString;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(@StringRes int str) {
            titleRes = str;
            return this;
        }

        public Builder setTitleStr(String str) {
            titleStr = str;
            return this;
        }

        public Builder setMessage(@StringRes int message) {
            messageStrRes = message;
            return this;
        }

        public Builder setTouchOutsideDismiss(){
            this.touchOutsideDismiss = true;
            return this;
        }

        public Builder setMessageStr(String message) {
            messageStr = message;
            return this;
        }

        public Builder setMessage(SpannableString message) {
            spannableString = message;
            return this;
        }

        public Builder setNegativeButton(@StringRes int negStr, DialogInterface listener) {
            negativeStr = negStr;
            this.onCancelListener = listener;
            return this;
        }

        public Builder setPositiveButton(@StringRes int posStr, DialogInterface listener) {
            this.onConfirmListener = listener;
            positiveStr = posStr;
            return this;
        }

        public Builder setAutoDismiss(boolean autoDismiss){
            isAutoDismiss = autoDismiss;
            return this;
        }

        public ComDialog create() {
            ComDialog dialog = new ComDialog(context);
            if(messageStrRes != -1) {
                dialog.setMessage(messageStrRes);
            }
            if(!TextUtils.isEmpty(messageStr)) {
                dialog.setMessageStr(messageStr);
            }
            if(!TextUtils.isEmpty(spannableString)) {
                dialog.setSpannableMessage(spannableString);
            }
            if(titleRes != -1){
                dialog.setTitleRes(titleRes);
            }else{
                dialog.setTitleStr(titleStr);
            }

            dialog.setCancelable(false);  //按返回键不消失
            dialog.setAutoDismiss(isAutoDismiss);
            if(touchOutsideDismiss){
                dialog.setCancelOnTouchOutside();
            }
            dialog.setButtonText(negativeStr, positiveStr);
            dialog.setOnCancelListener(onCancelListener);
            dialog.setOnConfirmListener(onConfirmListener);
            return dialog;
        }
    }

    public interface DialogInterface{
        void onClick(Dialog dialog);
    }
}
