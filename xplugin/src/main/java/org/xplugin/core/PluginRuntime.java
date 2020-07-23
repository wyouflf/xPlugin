package org.xplugin.core;

import android.app.Application;

import org.xplugin.core.app.AndroidApiHook;
import org.xplugin.core.msg.PluginInitializer;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

public final class PluginRuntime {

    private static PluginRuntimeListener runtimeListener;

    private PluginRuntime() {
    }

    public static void init(Application app, PluginRuntimeListener listener) {
        runtimeListener = listener;
        boolean isDebug = listener.isDebug();
        x.Ext.init(app);
        x.Ext.setDebug(isDebug);
        PluginInitializer.init();

        try {
            AndroidApiHook.hookInstrumentation();
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }

        try {
            AndroidApiHook.hookActivityManager();
        } catch (Throwable ex) {
            LogUtil.d(ex.getMessage(), ex);
        }

        try {
            AndroidApiHook.hookPackageManager();
        } catch (Throwable ex) {
            LogUtil.d(ex.getMessage(), ex);
        }
    }


    public static PluginRuntimeListener getRuntimeListener() {
        return runtimeListener;
    }
}
