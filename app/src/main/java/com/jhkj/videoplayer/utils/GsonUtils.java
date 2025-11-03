package com.jhkj.videoplayer.utils;

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.regex.PatternSyntaxException;

/**
 * Created by DeskTop29 on 2017/11/21.
 */

public class GsonUtils {
    private static Gson gson;
    private GsonUtils(){
        GsonBuilder gsonBuilder = new GsonBuilder();
        //注册自定义String的适配器
        //gsonBuilder.registerTypeAdapter(String.class, STRING);
        //gsonBuilder.setDateFormat("yyyy-MM-dd HH:mm:ss");
        //.registerTypeAdapterFactory(new NullStringToEmptyAdapterFactory<Object>())
        gson = gsonBuilder
                .serializeNulls()
                .create();
    }
    private static class GsonUtilsHolder{
        private final static GsonUtils gsonUtils = new GsonUtils();
    }
    private static GsonUtils get(){
        return GsonUtilsHolder.gsonUtils;
    }
    public Gson getGsonInner(){
        return gson;
    }
    private static Gson getGson(){
        return get().getGsonInner();
    }

    public static <T> T objFromJson(String json, Class<T> obj) {
        T object =  null;
        if(isJson(json)) {
            try {
                object = getGson().fromJson(json, obj);
            }catch (IllegalStateException | PatternSyntaxException | JsonSyntaxException e){
                //内部格式解析匹配出错
                return null;
            }
        }else {
            Log.i("json","返回的字符串不是json格式:"+json);
//            String newJsonStr = repairJsonStr(json);
//            try {
//                object = getGson().fromJson(newJsonStr, obj);
//                return object;
//            }catch (IllegalStateException | PatternSyntaxException| JsonSyntaxException e){
//                //内部格式解析匹配出错
//                return null;
//            }
            return null;
        }
        return object;
    }

    public static <T> T objFromJsonByType(String json, Type obj) {
        T object =  null;
        try {
            object = getGson().fromJson(json, obj);
        }catch (IllegalStateException | PatternSyntaxException| JsonSyntaxException e){
            //内部格式解析匹配出错
            return null;
        }
        return object;
    }


    public static <T> T objFromJson(String json, Type obj) {
        T object =  null;
        if(TextUtils.isEmpty(json))return null;
        if(isJson(json)) {
            try {
                object = getGson().fromJson(json, obj);
            }catch (IllegalStateException | PatternSyntaxException| JsonSyntaxException e){
                //内部格式解析匹配出错
                return null;
            }
        }else {
            Log.i("json","返回的字符串不是json格式:"+json);
//            String newJsonStr = repairJsonStr(json);
//            try {
//                object = getGson().fromJson(newJsonStr, obj);
//                return object;
//            }catch (IllegalStateException | PatternSyntaxException| JsonSyntaxException e){
//                //内部格式解析匹配出错
//                return null;
//            }
            return null;
        }
        return object;
    }


    public static String objToJson(Object obj){
        return getGson().toJson(obj);
    }


    private static String repairJsonStr(String value){
        String jsonStr = value.replaceAll("([\\{\\[,]\\s*)([a-zA-Z]+)(\\s*:)","$1\"$2\"$3");
        jsonStr = jsonStr.replaceAll("\"\"(.+?)\"","\",\"$1\"");  // 解决这种情况 ”“aa" -> ","aa"
        jsonStr = jsonStr.replaceAll("},\"(.+?)\"","}\",\"$1\"");
        return jsonStr;
    }
    /**
     * 判断是否是json结构
     */
    public static boolean isJson(String value) {
        try {
            new JSONObject(value);
        } catch (JSONException e) {
            try {
                new org.json.JSONArray(value);
            } catch (org.json.JSONException e2) {
                return false;
            }
        }
        return true;
    }


    private static final TypeAdapter<String> STRING = new TypeAdapter<String>()
    {
        public String read(JsonReader reader) throws IOException
        {
            if (reader.peek() == JsonToken.NULL)
            {
                reader.nextNull();
                return "";
            }
            return reader.nextString();
        }

        public void write(JsonWriter writer, String value) throws IOException
        {
            if (value == null)
            {
                // 在这里处理null改为空字符串
                //writer.nullValue();
                writer.value("");
                return;
            }
            writer.value(value);
        }
    };

}
