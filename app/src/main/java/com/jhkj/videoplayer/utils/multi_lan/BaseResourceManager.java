package com.jhkj.videoplayer.utils.multi_lan;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;
import java.lang.reflect.Method;

public class BaseResourceManager {
    Application app = null;
    protected Resources appResource;
    protected Resources otherResource;  //其它的Resource来源，可以是从apk来的，也可以是从其它module
    private String otherPkgName = "";
    private boolean isNightMode = false;

    protected void init(Application app){
        this.app = app;
        appResource = app.getResources();
    }
    //设置当前的主题模式
    public void setThemeMode(boolean isNight){
        this.isNightMode = isNight;
    }
    public boolean isNightMode(){
        return isNightMode;
    }

    public Context getApp(){
        return app;
    }

    public Resources getRes(){
        if(app == null){
            throw new RuntimeException("Resource Manager not initialized");
        }
        if(otherResource != null){
            return otherResource;
        }else{
            return appResource;
        }
    }

    public String getPkgName(){
        if(otherResource != null){
            return otherPkgName;
        }else{
            return app.getPackageName();
        }
    }

    public void setResourcePackage(String packName) {
        try {
            PackageManager pm = app.getPackageManager();
            otherResource = pm.getResourcesForApplication(packName);
            otherPkgName = packName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            otherResource = null;
            otherPkgName = "";
        }
    }
}
