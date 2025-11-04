package com.jhkj.gl_player.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;

import java.lang.reflect.Method;

public class NPopupWindow extends PopupWindow {

    public NPopupWindow(View contentView, int width, int height){
        super(contentView,width,height);
    }

    @Override
    public void showAsDropDown(View anchor) {
        if(getHeight() == ViewGroup.LayoutParams.MATCH_PARENT){
            resetHeight(anchor);
        }
        super.showAsDropDown(anchor);
    }

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff) {
        if(getHeight() == ViewGroup.LayoutParams.MATCH_PARENT){
            resetHeight(anchor);
        }
        super.showAsDropDown(anchor, xoff, yoff);
    }

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff, int gravity) {
        if(getHeight() == ViewGroup.LayoutParams.MATCH_PARENT){
            resetHeight(anchor);
        }
        super.showAsDropDown(anchor, xoff, yoff,gravity);
    }


    private void resetHeight(View anchor){
        int h = 0;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Rect rect = new Rect();
            anchor.getGlobalVisibleRect(rect);
            h = anchor.getResources().getDisplayMetrics().heightPixels - rect.bottom;
        }
        if(checkDeviceHasNavigationBar(anchor.getContext())){
            h = h - getVirtualBarHeight(anchor.getContext());
        }
        setHeight(h);
    }


    /**
     * 获取是否存在NavigationBar
     * @param context
     * @return
     */
    public boolean checkDeviceHasNavigationBar(Context context) {
        boolean hasNavigationBar = false;
        Resources rs = context.getResources();
        int id = rs.getIdentifier("config_showNavigationBar", "bool", "android");
        if (id > 0) {
            hasNavigationBar = rs.getBoolean(id);
        }
        try {
            Class systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method m = systemPropertiesClass.getMethod("get", String.class);
            String navBarOverride = (String) m.invoke(systemPropertiesClass, "qemu.hw.mainkeys");
            if ("1".equals(navBarOverride)) {
                hasNavigationBar = false;

            } else if ("0".equals(navBarOverride)) {
                hasNavigationBar = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return hasNavigationBar;
    }

    /**
     * 获取虚拟功能键高度
     * @param context
     * @return
     */
    public int getVirtualBarHeight(Context context) {
        int vh = 0;
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        try {
            @SuppressWarnings("rawtypes")
            Class c = Class.forName("android.view.Display");
            @SuppressWarnings("unchecked")
            Method method = c.getMethod("getRealMetrics", DisplayMetrics.class);
            method.invoke(display, dm);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                vh = dm.heightPixels - windowManager.getCurrentWindowMetrics().getBounds().height();
            }else{
                vh = dm.heightPixels - windowManager.getDefaultDisplay().getHeight();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return vh;
    }
}
