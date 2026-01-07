package com.jhkj.videoplayer.utils.multi_lan;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import androidx.annotation.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author KINPOWOO
 * @Date 22:10
 */
public class ResourceWrapper {
    private ResourceWrapper(){}
    private static class ResourceWrapperHolder{
        private static final ResourceWrapper instance = new ResourceWrapper();
    }
    public static ResourceWrapper get(){return ResourceWrapperHolder.instance;}

    //对应的资源名称
    private final Map<Integer,String> resNameMapper = new HashMap<>();
    private final Map<String,Integer> darkResIdMapper = new HashMap<>();  //暗夜模式资源对应的id
    //对应的资源类型
    private final Map<Integer, TypedValue> resTypeValueMapper = new HashMap<>();

    @NonNull
    public TypedValue getTypedValue(Resources r,int resId) {
        TypedValue tv = resTypeValueMapper.get(resId);
        if (tv == null) {
            tv = new TypedValue();
            try {
                r.getValue(resId, tv, true);
                resTypeValueMapper.put(resId,tv);
            }catch (Resources.NotFoundException e){
                //
            }
        }
        return tv;
    }

    public boolean isColorType(TypedValue value) {
        return value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT;
    }

    //白天模式和暗夜模式如果资源名称一致，在切换暗夜模式后，可能通过相同的ID获取到资源
    //所以这里可以作为多主题切换
    public int getColor(int resId){
        Resources res = ImageResManager.get().getRes();
        getTypedValue(res,resId);

        String resName = resNameMapper.get(resId);
        if(resName == null){
            resName = getEntryName(resId);
            resNameMapper.put(resId,resName);
        }
        boolean isNightMode = ImageResManager.get().isNightMode();
        ThemeTemp curTheme = ColorTheme.get().getCurTheme();
        int finalColor;
        if(isNightMode){
            finalColor = DarkThemeColor.getColor(resName,curTheme);
        }else{
            finalColor = LightThemeColor.getColor(resName,curTheme);
        }
        if(finalColor == Integer.MAX_VALUE){
            return res.getColor(resId);
        }
        return finalColor;
    }

    public int getColor(int resId,int defaultColor){
        if(resId == 0)return defaultColor;
        Resources res = ImageResManager.get().getRes();
        getTypedValue(res,resId);

        String resName = resNameMapper.get(resId);
        if(resName == null){
            resName = getEntryName(resId);
            resNameMapper.put(resId,resName);
        }

        boolean isNightMode = ImageResManager.get().isNightMode();
        ThemeTemp curTheme = ColorTheme.get().getCurTheme();
        int finalColor;
        if(isNightMode){
            finalColor = DarkThemeColor.getColor(resName,curTheme);
        }else{
            finalColor = LightThemeColor.getColor(resName,curTheme);
        }
        if(finalColor == Integer.MAX_VALUE){
            return defaultColor;
        }
        return finalColor;
    }

    public String getEntryName(int resInt) {
        try {
            Resources res = ImageResManager.get().getRes();
            return res.getResourceEntryName(resInt);
        } catch (Resources.NotFoundException e) {
            //没有找到该资源，是硬编码
            return null;
        }
    }

    @SuppressLint("DiscouragedApi")
    public int getIdentityId(Resources res,String entryName, String type){
        String pkgName = ImageResManager.get().getPkgName();
        return res.getIdentifier(entryName,type,"com.jhkj.neakasa");
    }
}
