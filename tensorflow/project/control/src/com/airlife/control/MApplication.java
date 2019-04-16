package com.airlife.control;

import android.app.Application;
import android.content.Context;

import com.secneo.sdk.Helper;

public class MApplication extends Application {

    private DJIBase djiBase;
    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(MApplication.this);
        if (djiBase == null) {
            djiBase = new DJIBase();
            djiBase.setContext(this);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        djiBase.onCreate();
    }

}
