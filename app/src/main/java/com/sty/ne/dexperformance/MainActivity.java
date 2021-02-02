package com.sty.ne.dexperformance;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private TextView tvContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e("sty", "类加载器：" + getClassLoader().toString());
        // dalvik.system.PathClassLoader[DexPathList[[zip file "/data/app/com.sty.ne.dexperformance-1/base.apk"],
        // nativeLibraryDirectories=[/vendor/lib, /system/lib]]]

        initView();
    }

    private void initView() {
        tvContent = findViewById(R.id.tv_content);
        tvContent.setText("hello world 加固实现");
    }
}