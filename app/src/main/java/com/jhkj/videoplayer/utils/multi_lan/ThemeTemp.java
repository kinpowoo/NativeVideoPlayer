package com.jhkj.videoplayer.utils.multi_lan;

public enum ThemeTemp{

    DEFAULT(""),BLUE_THEME("_blue");

    private final String suffix;//自定义属性

    /**构造函数，枚举类型只能为私有*/
    ThemeTemp(String suffix) {
        this.suffix = suffix;
    }

    //自定义方法
    public String suffix(){
        return suffix;
    }
}