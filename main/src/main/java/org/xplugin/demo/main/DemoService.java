package org.xplugin.demo.main;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import org.xutils.common.util.LogUtil;

public class DemoService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.d("###################");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.d("###################");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        LogUtil.d("###################");
        return new Binder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        LogUtil.d("###################");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.d("###################");
    }
}
