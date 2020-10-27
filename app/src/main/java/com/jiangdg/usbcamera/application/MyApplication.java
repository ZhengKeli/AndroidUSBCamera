package com.jiangdg.usbcamera.application;

import android.app.Application;

public class MyApplication extends Application {
    // File Directory in sd card
    public static final String DIRECTORY_NAME = "USBCamera";

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler mCrashHandler = CrashHandler.getInstance();
        mCrashHandler.init(getApplicationContext(), getClass());
    }
}
