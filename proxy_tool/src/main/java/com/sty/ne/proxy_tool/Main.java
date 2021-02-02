package com.sty.ne.proxy_tool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * @Author: tian
 * @UpdateDate: 2021/1/29 9:12 PM
 */
public class Main {

    //加密dex文件的过程
    public static void main(String[] args) {
        //加密dex文件的过程

        //1. 制作只包含解密代码的dex文件 <-- proxy_core模块
        File aarFile = new File("proxy_core/build/outputs/aar/proxy_core-debug.aar");
        File aarTemp = new File("proxy_core/temp");
        //解压 proxy_core-debug.aar
        Zip.unZip(aarFile, aarTemp);
        File classJar = new File(aarTemp, "classes.jar");
        File classesDex = new File(aarTemp, "classes.dex");

        Process process;

        //classes.jar --> classes.dex
        try {
            // Windows下的命令
            //"cmd /c dx --dex --output " + classesDex.getAbsolutePath() + " " + classJar.getAbsolutePath();
            process = Runtime.getRuntime().exec("dx --dex --output " +
                    classesDex.getAbsolutePath() + " " + classJar.getAbsolutePath());
            process.waitFor();
            if(process.exitValue() != 0) {
                throw new RuntimeException("dex error");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        //2. 加密apk的dex文件
        File apkFile = new File("app/build/outputs/apk/debug/app-debug.apk");
        File apkTemp = new File("app/build/outputs/apk/debug/temp");
        //解压 app-debug.apk
        Zip.unZip(apkFile, apkTemp);
        //获取解压后的所有dex文件
        File[] dexFiles = apkTemp.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.endsWith(".dex");
            }
        });
        //AES加密
        AES.init(AES.DEFAULT_PWD);
        for (File dexFile : dexFiles) {
            try {
                byte[] bytes = Utils.getBytes(dexFile);
                byte[] encrypt = AES.encrypt(bytes);
                FileOutputStream fos = new FileOutputStream(new File(apkTemp, "secret-" + dexFile.getName()));
                fos.write(encrypt);
                fos.flush();
                fos.close();
                dexFile.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //3.把加密后的dex文件放入apk解压的目录，重新压缩成新的apk文件
        classesDex.renameTo(new File(apkTemp, "classes.dex"));
        File unSignedApk = new File("app/build/outputs/apk/debug/app-unsigned.apk");
        try {
            Zip.zip(apkTemp, unSignedApk);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //4. 对齐和签名 https://developer.android.google.cn/studio/command-line/zipalign
        File alignedApk = new File("app/build/outputs/apk/debug/app-unsigned-aligned.apk");
        try {
            // "cmd /c zipalign -v 4 " + unSignedApk.getAbsolutePath() + " " + alignedApk.getAbsolutePath()
            process = Runtime.getRuntime().exec("zipalign -f -v 4 " + unSignedApk.getAbsolutePath()
                    + " " + alignedApk.getAbsolutePath());
            process.waitFor();
            if(process.exitValue() != 0) {
                throw new RuntimeException("aligned error");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        //签名
        File signedApk = new File("app/build/outputs/apk/debug/app-signed-aligned.apk");
        File jks = new File("proxy_tool/store.jks");
        try {
            process = Runtime.getRuntime().exec("apksigner sign --ks " + jks.getAbsolutePath() +
                    " --ks-key-alias key0 --ks-pass pass:123456 --key-pass pass:123456 --out " +
                    signedApk.getAbsolutePath() + " " + alignedApk.getAbsolutePath());
            process.waitFor();
            if(process.exitValue() != 0) {
                throw new RuntimeException("signed error");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }


}
