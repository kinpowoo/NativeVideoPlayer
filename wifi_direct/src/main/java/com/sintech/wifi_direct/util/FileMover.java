package com.sintech.wifi_direct.util;

import java.io.File;

public class FileMover {
    public static boolean moveFile(String src, String dst) {
        try {
            // 检查源文件是否存在
            File source = new File(src);
            if (!source.exists()) return false;
            
            // 执行移动命令
            String command = String.format("mv \"%s\" \"%s\"", src, dst);
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            
            // 等待完成
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // 使用示例
    public void example() {
        String source = "/sdcard/Download/test.txt";
        String dest = "/sdcard/Documents/test.txt";
    }
}