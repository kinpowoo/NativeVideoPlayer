package com.jhkj.videoplayer.utils.multi_lan;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;

import java.util.Locale;

/**
 * 用来管理文字资源id，从哪个resource来获取
 */
public class TextResManager extends BaseResourceManager{
    private TextResManager(){
    }
    private static class TextResourceHolder{
        private static final TextResManager instance = new TextResManager();
    }
    public static TextResManager get(){return TextResourceHolder.instance;}

    public void intTextResource(Application app){
        super.init(app);
    }

    /**
     *  设置resource的语言地区
     */
    public void setLocale(Locale locale){
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if(otherResource != null) {
                    Configuration configuration = otherResource.getConfiguration();
                    configuration.setLocale(locale);
                    otherResource.updateConfiguration(configuration,otherResource.getDisplayMetrics());
                }else{
                    if(appResource != null) {
                        Configuration configuration = appResource.getConfiguration();
//                        configuration.setLocale(locale);
                        // 设置新Locale
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            configuration.setLocale(locale);
                        } else {
                            configuration.locale = locale;
                        }
                        // Android 15+ 需要额外处理
                        int curSdk = Build.VERSION.SDK_INT;
                        if (curSdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            // 确保配置传播到所有资源实例
                            Context ctx = app.createConfigurationContext(configuration);
                            appResource = ctx.getResources();
                        }else{
                            if(curSdk >= Build.VERSION_CODES.N){
                                // 确保配置传播到所有资源实例
                                Context ctx = app.createConfigurationContext(configuration);
                                appResource = ctx.getResources();
                            }else{
                                appResource.updateConfiguration(configuration, appResource.getDisplayMetrics());
                            }
                        }
                    }
                }
            }
        });
    }
}
