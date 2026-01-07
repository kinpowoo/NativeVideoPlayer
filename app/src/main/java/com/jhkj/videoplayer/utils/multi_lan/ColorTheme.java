package com.jhkj.videoplayer.utils.multi_lan;

public class ColorTheme {
    private ColorTheme(){}
    private static class ColorThemeHolder{
        private static final ColorTheme instance = new ColorTheme();
    }
    public static ColorTheme get(){return ColorThemeHolder.instance;}

    private ThemeTemp curTheme = ThemeTemp.DEFAULT;

    public void setColorTheme(ThemeTemp colorTheme){
        this.curTheme = colorTheme;
    }

    public ThemeTemp getCurTheme(){
        return curTheme;
    }
}
