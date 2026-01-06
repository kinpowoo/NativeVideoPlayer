package com.jhkj.videoplayer.utils;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * 软键盘工具类
 */
public class KeyboardUtils {

    /**
     * 显示软键盘
     *
     * @param editText
     */
    public static void showSoftInput(Context context,EditText editText) {
        if (editText == null)
            return;
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null)
            return;
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.requestFocus();
        imm.showSoftInput(editText, 0);
    }

    /**
     * 隐藏软键盘
     *
     * @param view
     */
    public static void hideSoftInput(Context context,View view) {
        if (view == null)
            return;
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager == null)
            return;
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * 隐藏软键盘
     *
     * @param editText
     */
    public static void hideSoftInput(Context context,EditText editText) {
        if (editText == null)
            return;
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager == null)
            return;
        editText.clearFocus();
        inputMethodManager.hideSoftInputFromWindow(editText.getWindowToken(), 0);
    }

    /**
     * 隐藏软键盘
     *
     * @param activity
     */
    public static void hideSoftInput(Activity activity) {
        Window window = activity.getWindow();
        View view = window.getCurrentFocus();
        if (view == null) {
            view = window.getDecorView();
        }
        hideSoftInput(activity.getBaseContext(),view);
    }

    /**
     * 软键盘切换
     */
    public static void toggleSoftInput(Context context) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null)
            return;
        imm.toggleSoftInput(0, 0);
    }

}
