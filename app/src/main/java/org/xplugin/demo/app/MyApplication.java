package org.xplugin.demo.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.xplugin.core.PluginRuntime;
import org.xplugin.core.PluginRuntimeListener;
import org.xplugin.core.ctx.Plugin;

import java.io.File;
import java.util.Map;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        PluginRuntime.init(this, new PluginRuntimeListener() {
            @Override
            public boolean isDebug() {
                return true;
            }

            @Override
            public Class<?> getHostPluginEntry() {
                return null;
            }

            @Override
            public String getRuntimePkg() {
                return "org.xplugin.demo.main";
            }

            @Override
            public boolean verifyPluginFile(File pluginFile) {
                // 校验文件签名
                return true;
            }

            @Override
            public void onHostInitialised(boolean hasError) {
                Log.d("PluginRuntime", "hasError: " + hasError);
            }

            @Override
            public void onPluginsLoaded(Map<String, Plugin> plugins) {
                Log.d("PluginRuntime", "plugins: " + plugins.keySet());
            }

            @Override
            public void onPluginsLoadError(Throwable ex, boolean isCallbackError) {
                Log.e("PluginRuntime", ex.getMessage(), ex);
            }
        });
    }
}
