package com.jhkj.videoplayer.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import com.jhkj.videoplayer.const_dto.IntentConst;
import com.jhkj.videoplayer.utils.multi_lan.TextResManager;
import com.tencent.mmkv.MMKV;

import java.util.Locale;

public class LangUtils {
    private static final String CHINESE = "zh";
    private static final String CHINESE_HANT = "zh_tw";
    private static final String ENGLISH = "en";
    private static final String JAPANESE = "ja";
    private static final String GERMANY = "de";
    private static final String FRANCE = "fr";
    private static final String RUSSIAN = "ru";
    private static final String TURKEY = "tr";
    private static final String KOREAN = "ko";
    private static final String ITALY = "it";
    private static final String SPANISH = "es";
    private static final String POLISH = "pl";
    public static final String FOLLOW_SYSTEM = "fs";
    private static final String[] supportLan = new String[] {
            "zh","zh_tw","en","fr","de","ja","es","ru","tr","ko","it","pl","fs"
    };
    public static final String[] supportLanFully =
            new String[]{"zh-CN","zh-TW","en-US","fr-FR","de-DE","ja-JP","es-ES","ru-RU","tr-TR","ko-KR","pl-PL","it-IT"};

    /**
     * 获取系统的地区设置
     */
    public static Locale getSystemLocale() {
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
           LocaleList systemLocales = Resources.getSystem().getConfiguration().getLocales();
           //当前系统设置的语言，如果系统不支持该语言，会将其排在第二位，
           //第一位是上一次设置的系统支持显示的语言
           Locale systemDefaultLocale = systemLocales.get(0);
           if(systemLocales.size() > 1){
               //系统语言排序列表，app代码可以对其造成影响，如果app强行设置了一种语言，
               //那么app设置的语言为第一顺位，系统设置的语言会被后挪
               Locale secondLocale = systemLocales.get(1);
               //这个是app设置语言后的排序
               LocaleList preferenceLocales = LocaleList.getDefault();
               if(!preferenceLocales.isEmpty()){
                   int systemDefaultIndex = Integer.MAX_VALUE;
                   int secondLocaleIndex = Integer.MAX_VALUE;
                   for(int i=0;i<preferenceLocales.size();i++){
                       Locale k = preferenceLocales.get(i);
                       if(k.equals(systemDefaultLocale)){
                           systemDefaultIndex = i;
                       }
                       if(k.equals(secondLocale)){
                           secondLocaleIndex = i;
                       }
                       if(systemDefaultIndex != Integer.MAX_VALUE &&
                            secondLocaleIndex != Integer.MAX_VALUE){
                           break;
                       }
                   }
                   if(systemDefaultIndex != Integer.MAX_VALUE &&
                           secondLocaleIndex != Integer.MAX_VALUE){
                       if(systemDefaultIndex < secondLocaleIndex){
                           return systemDefaultLocale;
                       }else{
                           return secondLocale;
                       }
                   }else if(systemDefaultIndex != Integer.MAX_VALUE){
                       return systemDefaultLocale;
                   }else if(secondLocaleIndex != Integer.MAX_VALUE){
                       return secondLocale;
                   }else{
                       return systemDefaultLocale;
                   }
               }else{
                   return systemDefaultLocale;
               }
           }else{
               return systemDefaultLocale;
           }
        } else {  //安卓27以下
           return Resources.getSystem().getConfiguration().locale;
        }
    }


    //获取系统语言偏好列表第一位
    public static Locale getPreferLocale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Resources.getSystem().getConfiguration().getLocales().get(0);
        } else {  //安卓27以下
            return Resources.getSystem().getConfiguration().locale;
        }
    }


    /**
     * 获取当前 APP 使用的 locale
     * @return
     */
    public static Locale getCurrentLocale() {
        String saveLan = MMKV.defaultMMKV().decodeString(IntentConst.CurrentLanguageAbbr,
                FOLLOW_SYSTEM);
        if(saveLan == null){
            saveLan = FOLLOW_SYSTEM;
        }
        switch (saveLan) {
            case CHINESE:
                return Locale.SIMPLIFIED_CHINESE;
            case CHINESE_HANT:
                return Locale.TRADITIONAL_CHINESE;
            case ENGLISH:
                return Locale.US;
            case JAPANESE:
                return Locale.JAPAN;
            case GERMANY:
                return Locale.GERMANY;
            case FRANCE:
                return Locale.FRANCE;
            case RUSSIAN:
                return new Locale("ru","RU");
            case TURKEY:
                return new Locale("tr","TR");
            case KOREAN:
                return Locale.KOREA;
            case ITALY:
                return Locale.ITALY;
            case SPANISH:
                return new Locale("es","ES");
            case POLISH:
                return new Locale("pl","PL");
            case FOLLOW_SYSTEM:
                Locale defaultLocale = getSystemLocale();
                return getAppSupportLocale(defaultLocale);
            default:
                return Locale.US;
        }
    }


    public static String getCurrentLanShort(){
        Locale locale = LangUtils.getCurrentLocale();
        String currentLan = locale.getLanguage();
        String region = locale.getCountry();
        if(currentLan.equals("zh") && region.equals("TW")) {
            currentLan = "zh_tw";
        }
        boolean isFindSupportLan = false;
        for(int i=0;i<supportLan.length;i++){
            if(currentLan.equals(supportLan[i])){
                isFindSupportLan = true;
                break;
            }
        }
        if(!isFindSupportLan){
            currentLan = "en";
        }
        return currentLan;
    }

    /**
     * 获取当前 APP 支持的 Locale,可能与系统 locale不一致
     * @return
     */
    public static Locale getAppSupportLocale(Locale locale){
        //如果跟随系统，我们要拆开
        String systemLan = locale.getLanguage();
        boolean isFindSupport = false;
        String script = locale.getScript();
        for(String sL:supportLanFully){
            String[] twoPart = sL.split("-");
            if(twoPart[0].equalsIgnoreCase(systemLan)){
                //在App支持的语言列表内
                isFindSupport = true;
                if(twoPart[0].equals("zh")){
                    if(!TextUtils.isEmpty(script) && script.equals("Hant")){
                        locale = Locale.TRADITIONAL_CHINESE;
                    }else{
                        locale = Locale.SIMPLIFIED_CHINESE;
                    }
                    break;
                }
                if(twoPart.length == 2) {
                    locale = new Locale(twoPart[0], twoPart[1]);
                }else if(twoPart.length == 3){
                    locale = new Locale(twoPart[0], twoPart[1],twoPart[2]);
                }
                break;
            }
        }
        if(!isFindSupport){
            //不支持的语言默认显示英文
            locale = Locale.US;
        }
        return locale;
    }


    //获取当前语言配置的下标
    public static int getCurrentLangConfigIndex() {
        String saveLan = MMKV.defaultMMKV().decodeString(IntentConst.CurrentLanguageAbbr,
                FOLLOW_SYSTEM);
        if(saveLan == null){
            saveLan = FOLLOW_SYSTEM;
        }
        for (int i=0;i<supportLan.length;i++){
            if(saveLan.equals(supportLan[i])){
                return i;
            }
        }
        return supportLan.length-1;  //默认是跟随系统
    }


    //设置当前语言的配置
    public static void setCurrentLangConfig(int index) {
        String abbr = "fs";
        if(index >= 0 && index < supportLan.length){
            abbr = supportLan[index];
        }
        MMKV.defaultMMKV().encode(IntentConst.CurrentLanguageAbbr,abbr);
    }

    //获取当前语言的配置
    public static String getCurrentLangConfig(){
        return MMKV.defaultMMKV().decodeString(IntentConst.CurrentLanguageAbbr,
                FOLLOW_SYSTEM);
    }


    public static Context getAttachBaseContext(Context context) {
        //Android 8.0之后需要用另一种方式更改res语言,即配置进context中
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return updateResources(context);
        } else {
            //8.0之前的更新语言资源方式
            changeResLanguage(context);
            return context;
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Context updateResources(Context context) {
        Resources resources = context.getResources();
        Locale locale = LangUtils.getCurrentLocale();
        TextResManager.get().setLocale(locale);
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }

    private static void changeResLanguage(Context context) {
        Locale locale = LangUtils.getCurrentLocale();
        TextResManager.get().setLocale(locale);
        Resources res = context.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        Configuration conf = res.getConfiguration();
        conf.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //大于安卓7.0
            conf.setLocales(new LocaleList(locale));
        }
        res.updateConfiguration(conf, dm);
    }
}