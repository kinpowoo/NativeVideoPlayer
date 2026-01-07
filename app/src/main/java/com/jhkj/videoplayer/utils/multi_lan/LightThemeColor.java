package com.jhkj.videoplayer.utils.multi_lan;

import java.util.HashMap;
import java.util.Map;

public class LightThemeColor {
    private final static Map<String,Integer> colorMap = new HashMap<>();

    public static int getColor(String resName){
        return getColor(resName,ThemeTemp.DEFAULT);
    }

    public static int getColor(String resName,ThemeTemp theme){
        Integer color = colorMap.get(resName+theme.suffix());
        if(color == null)return Integer.MAX_VALUE;
        return color;
    }

}
