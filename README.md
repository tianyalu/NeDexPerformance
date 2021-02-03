# Android 加固代码实现方案

[TOC]

## 一、理论

[`MultiDex`官方文档](https://developer.android.google.cn/studio/build/multidex.html)

[Android源码在线查看](http://androidos.net.cn/sourcecode)

`Dex`加固原理如下图所示：

![image](https://github.com/tianyalu/NeDexPerformance/raw/master/show/dex_encrypt_theory.png)

## 二、实践

`Dex`加壳去壳步骤如下图所示：

![image](https://github.com/tianyalu/NeDexPerformance/raw/master/show/dex_shell_unshell_process.png)

### 2.1 加壳

#### 2.1.1 `proxy_core`

`proxy_core`是个`Android Library`，需先构建生成`build/outputs/aar/proxy_core-debug.aar`文件。

`proxy_core`中包含`ProxyApplication`，需要`app module`使用该`application`。

#### 2.1.1 `app`

`app`是主应用模块，`application`需使用`proxy_core`中的`ProxyApplication`，需先构建生成`build/outputs/apk/debug/app-debug.apk`文件。

#### 2.1.2 `proxy_tool`

`proxy_tool`是个纯`Java Module`，主要用于加壳实现，其核心代码如下：

```java
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
```

### 2.2 去壳

去壳是在`ProxyApplication`中操作的，核心代码如下（本文仅测试了`Android5.1.1`的系统）：

```java
//1. ActivityThread创建Application调用的第一个方法
@Override
protected void attachBaseContext(Context base) {
  super.attachBaseContext(base);
  //获取用户的meta-data
  getMetaData();
  //得到当前的apk文件
  // /data/app/com.sty.ne.dexperformance-2/base.apk
  File apkFile = new File(getApplicationInfo().sourceDir);

  File versionDir = getDir(app_name + "_" + app_version, MODE_PRIVATE);
  // /data/data/com.sty.ne.dexperformance/app_com.sty.ne.dexperformance.app.MyApplication_dexDir_1.0
  // /data/data/com.sty.ne.dexperformance/app_com.sty.ne.dexperformance.app.MyApplication_dexDir_1.0/app
  File appDir = new File(versionDir, "app"); //文件解压之后放置的目录
  // /data/data/com.sty.ne.dexperformance/app_com.sty.ne.dexperformance.app.MyApplication_dexDir_1.0/dexDir
  File dexDir = new File(versionDir, "dexDir"); //放置dex文件

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

/**
  * 参考：https://blog.csdn.net/qq_23992393/article/details/101441435
  * @param dexFiles
  * @param versionDir
  */
private void loadDex(List<File> dexFiles, File versionDir) {
  //通过反射方式获取pathList
  try {
    Field pathListField = Utils.findField(getClassLoader(), "pathList");
    Object pathList = pathListField.get(getClassLoader());
    //获取dexElements
    Field dexElementsField = Utils.findField(pathList, "dexElements");
    Object[] dexElements = (Object[])dexElementsField.get(pathList);

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

    }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {  //5.1.1上测试通过
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
```

