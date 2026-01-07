package com.jhkj.videoplayer.utils.multi_lan;

import android.annotation.SuppressLint;
import android.content.res.Resources;

import java.util.HashMap;
import java.util.Map;

/**
*/
public class DarkThemeColor {
    private final static Map<String,Integer> colorMap = new HashMap<>();

    public static int getColor(String resName){
        return getColor(resName,ThemeTemp.DEFAULT);
    }

    public static int getColor(String resName,ThemeTemp theme){
//        Integer color = colorMap.get(resName+theme.suffix());
//        if(color == null) return Integer.MAX_VALUE;
//        return color;
        String darkResourceName = resName + "_night";
        Integer cacheColor = colorMap.get(darkResourceName);
        if(cacheColor == null) {
            Resources res = ImageResManager.get().getRes();
            int darkColorId = getIdentityId(res, darkResourceName, "color");
            if (darkColorId > 0) {
                int color = res.getColor(darkColorId);
                colorMap.put(darkResourceName, color);
                return color;
            } else {
                return Integer.MAX_VALUE;
            }
        }else{
            return cacheColor;
        }
    }


    @SuppressLint("DiscouragedApi")
    public static int getIdentityId(Resources res,String entryName, String type){
        String pkgName = ImageResManager.get().getPkgName();
        return res.getIdentifier(entryName,type,"com.jhkj.neakasa");
    }
}
