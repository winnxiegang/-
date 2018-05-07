package com.tencent.liteav.demo;

import android.app.Application;
import android.support.multidex.MultiDexApplication;

import com.tencent.bugly.crashreport.CrashReport;
import com.tencent.rtmp.TXLiveBase;


public class DemoApplication extends MultiDexApplication {

//    private RefWatcher mRefWatcher;
    private static DemoApplication instance;

    @Override
    public void onCreate() {

        super.onCreate();

        instance = this;

        TXLiveBase.setConsoleEnabled(true);
        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(getApplicationContext());
        strategy.setAppVersion(TXLiveBase.getSDKVersionStr());
        CrashReport.initCrashReport(getApplicationContext(),strategy);

//        File file = getFilesDir();
//        Log.w("DemoApplication", "load:" + file.getAbsolutePath());
//        TXLiveBase.setLibraryPath(file.getAbsolutePath());
        //测试代码
//        TCHttpEngine.getInstance().initContext(getApplicationContext());
//        mRefWatcher = LeakCanary.install(this);
    }

//    public static RefWatcher getRefWatcher(Context context) {
//        DemoApplication application = (DemoApplication) context.getApplicationContext();
//        return application.mRefWatcher;
//    }

    public static DemoApplication getApplication() {
        return instance;
    }

}
