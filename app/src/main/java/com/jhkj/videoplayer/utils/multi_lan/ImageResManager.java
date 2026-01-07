package com.jhkj.videoplayer.utils.multi_lan;

import android.app.Application;

/**
 * 用来管理文字资源id，从哪个resource来获取
 */
public class ImageResManager extends BaseResourceManager{
    private ImageResManager(){}
    private static class TextResourceHolder{
        private static final ImageResManager instance = new ImageResManager();
    }
    public static ImageResManager get(){return TextResourceHolder.instance;}

    public void intImageResource(Application app){
        super.init(app);
    }

}