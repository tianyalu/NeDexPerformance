package com.sty.ne.proxy_core;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
        Log.e("sty", "apkFile: " + getApplicationInfo().sourceDir);

        File versionDir = getDir(app_name + "_" + app_version, MODE_PRIVATE);
        Log.e("sty", "versionDir: " + versionDir.getAbsolutePath());
        File appDir = new File(versionDir, "app"); //文件解压之后放置的目录
        File dexDir = new File(versionDir, "dexDir"); //放置dex文件
        Log.e("sty", "appDir: " + appDir.getAbsolutePath());
        Log.e("sty", "dexDir: " + dexDir.getAbsolutePath());

        List<File> dexFiles = new ArrayList<>();
        if(!dexDir.exists() || dexDir.list().length == 0) {
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

        loadDex(dexFiles, versionDir);
    }

    private void loadDex(List<File> dexFiles, File versionDir) {
        //通过反射方式获取pathList
        try {
            Field pathListField = Utils.findField(getClassLoader(), "pathList");
            Object pathList = pathListField.get(getClassLoader());
            //获取dexElements
            Field dexElementsField = Utils.findField(pathList, "dexElements");
            Object[] dexElements = (Object[])dexElementsField.get(pathList);

            //http://androidos.net.cn/android/7.1.1_r28/xref/libcore/dalvik/src/main/java/dalvik/system/DexPathList.java
            //this.nativeLibraryPathElements = makePathElements(allNativeLibraryDirectories,
            //                                                          suppressedExceptions,
            //
            //                                                          definingContext);

            Method makeDexElementsMethod;
            Object[] addElements;
            ArrayList<IOException> suppressedExceptions = new ArrayList<>();
            //版本适配
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用makeDexElements(List, File, List, ClassLoader)
                makeDexElementsMethod = Utils.findMethod(pathList, "makeDexElements", List.class, File.class, List.class, ClassLoader.class);
                addElements = (Object[]) makeDexElementsMethod.invoke(null, dexFiles, versionDir, suppressedExceptions, getClassLoader());
            }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0-7.0 使用makePathElements(List, File, List)
                makeDexElementsMethod = Utils.findMethod(pathList, "makePathElements", List.class, File.class, List.class);
                addElements = (Object[]) makeDexElementsMethod.invoke(null, dexFiles, versionDir, suppressedExceptions);

            }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Android 5.0-6.0 使用makeDexElements(ArrayList, File, ArrayList) 注意这里参数使用ArrayList类型
                makeDexElementsMethod = Utils.findMethod(pathList, "makeDexElements", ArrayList.class, File.class, ArrayList.class);
                addElements = (Object[])makeDexElementsMethod.invoke(null, dexFiles, versionDir, suppressedExceptions);
            }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Android 4.4-5.0 使用makeDexElements(ArrayList,File,ArrayList)  注意这里参数编程ArrayList类型
                // 和5.0一样的方法，注意4.4 会报错 Class ref in pre-verified class resolved to unexpected implementation
                Log.e("sty", " 当前版本：" + Build.VERSION.SDK_INT + "此版本暂未解决 Class ref in pre-verified class resolved to unexpected implementation 的问题，敬请期待！");
                return;
            }else {
                Log.e("sty", "installPatch: 当前版本：" + Build.VERSION.SDK_INT + "不支持加固");
                return;
            }

            //合并数组
            Object[] newElements = (Object[])Array.newInstance(dexElements.getClass().getComponentType(), dexElements.length + addElements.length);
            //1.原数组 2.从原数组的起始位置开始 3.目标数组 4.目标数组的起始位置 5.要拷贝的长度
            System.arraycopy(dexElements, 0, newElements, 0, dexElements.length);
            System.arraycopy(addElements, 0, newElements, dexElements.length, addElements.length);

            dexElementsField.set(pathList, newElements);

        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
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
                Log.e("sty", "app_name: " + app_name + ", app_version: " + app_version);
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
