package com.sty.ne.proxy_core;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: tian
 * @UpdateDate: 2021/1/29 10:11 PM
 */
public class ProxyApplication extends Application {
    private String app_name;
    private String app_version;

    //1. ActivityThread创建Application调用的第一个方法
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        //获取用户的meta-data
        getMetaData();
        //得到当前的apk文件
        File apkFile = new File(getApplicationInfo().sourceDir);

        File versionDir = getDir(app_name + "_" + app_version, MODE_PRIVATE);
        File appDir = new File(versionDir, "app"); //文件解压之后放置的目录
        File dexDir = new File(versionDir, "dexDir"); //放置dex文件

        List<File> dexFiles = new ArrayList<>();
        if(dexDir.exists() || dexDir.list().length == 0) {
            //把apk解压到appDir
            Zip.unZip(apkFile, appDir);
            //获取这个目录下所有的文件，把dex文件过滤出来
            File[] files = appDir.listFiles();
            for (File file : files) {
                String name = file.getName();
                if(name.endsWith(".dex") && !TextUtils.equals(name, "classes.dex")) {
                    AES.init(AES.DEFAULT_PWD);
                    //读取文件内容
                    try {
                        byte[] bytes = Utils.getBytes(file);
                        //解密
                        byte[] decrypt = AES.decrypt(bytes);
                        FileOutputStream fos = new FileOutputStream(file);
                        fos.write(decrypt);
                        fos.flush();
                        fos.close();
                        dexFiles.add(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }else {
            for (File file : dexDir.listFiles()) {
                dexFiles.add(file);
            }
        }
    }

    private void getMetaData() {
        try {
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
            Bundle bundle = applicationInfo.metaData;
            if(bundle != null) {
                if(bundle.containsKey("app_name")) {
                    app_name = bundle.getString("app_name");
                }
                if(bundle.containsKey("app_version")) {
                    app_version = bundle.getString("app_version");
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
