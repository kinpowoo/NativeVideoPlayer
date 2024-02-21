package com.jhkj.gl_player.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.WindowManager;

import java.lang.reflect.Method;

public final class DensityUtil {

    private static float density = -1F;

    private DensityUtil() {
    }

    public static float getDensity(Context c) {
        if (density <= 0F) {
            density = c.getResources().getDisplayMetrics().density;
        }
        return density;
    }

    public static float dip2px(Context c,float dpValue) {
        return (dpValue * getDensity(c) + 0.5F);
    }

    public static float px2dip(Context c,float pxValue) {
        return (pxValue / getDensity(c) + 0.5F);
    }

    /**
     * 将sp值转换为px值，保证尺寸大小不变
     *
     * @return
     */
    public static float sp2px(Context c,float spVal) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                spVal,c.getResources().getDisplayMetrics());
    }


    public static int getScreenWidth(Context c) {
        WindowManager wm = (WindowManager) c
                .getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return wm.getCurrentWindowMetrics().getBounds().width();
        }else {
            DisplayMetrics outMetrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(outMetrics);
            return outMetrics.widthPixels;
        }
    }

    public static int getScreenHeight(Context c) {
        WindowManager wm = (WindowManager) c
                .getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return wm.getCurrentWindowMetrics().getBounds().height();
        }else {
            DisplayMetrics outMetrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(outMetrics);
            return outMetrics.heightPixels;
        }
    }



    public static void backgroundAlpha(Context c,float bgAlpha)
    {
        WindowManager.LayoutParams lp =((Activity)c).getWindow().getAttributes();
        lp.alpha = bgAlpha; //0.0-1.0
        ((Activity)c).getWindow().setAttributes(lp);
        ((Activity)c).getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }


    //获得状态栏高度
    public static int getStatusBarHeight(Activity mActivity) {
        Resources resources = mActivity.getResources();
        int resourceId = resources.getIdentifier("status_bar_height", "dimen","android");
        return resources.getDimensionPixelSize(resourceId);
    }


    //获得标题栏高度
    public static int getToolbarHeight(Activity mActivity) {
        TypedValue tv = new TypedValue();
        if (mActivity.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            return TypedValue.complexToDimensionPixelSize(tv.data,
                    mActivity.getResources().getDisplayMetrics());
        }
        return 0;
    }

    public static int getNavigationBarHeight(Activity mActivity) {
        Resources resources = mActivity.getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height","dimen", "android");
        return resources.getDimensionPixelSize(resourceId);
    }

    //获取是否存在NavigationBar
    public static boolean checkDeviceHasNavigationBar(Context context) {
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

        }
        return hasNavigationBar;
    }
}
