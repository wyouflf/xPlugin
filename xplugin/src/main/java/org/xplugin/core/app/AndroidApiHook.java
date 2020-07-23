package org.xplugin.core.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.PackageManager;

import org.xplugin.core.util.PluginReflectUtil;
import org.xplugin.core.util.ReflectField;
import org.xplugin.core.util.Reflector;
import org.xutils.x;

import java.lang.reflect.Proxy;

/**
 * Created by jiaolei on 20/6/7.
 * 可在插件中打开的Dialog
 */
public final class AndroidApiHook {

    private static PluginInstrumentation pluginInstrumentation;

    public static PluginInstrumentation getPluginInstrumentation() {
        return pluginInstrumentation;
    }

    public static void hookInstrumentation() throws Throwable {
        Reflector activityThreadReflector = Reflector.on("android.app.ActivityThread");
        Object activityThreadObj = activityThreadReflector.method("currentActivityThread").call();
        activityThreadReflector.bind(activityThreadObj);
        ReflectField instrumentationField = activityThreadReflector.field("mInstrumentation");
        Instrumentation instrumentation = instrumentationField.get();
        pluginInstrumentation = new PluginInstrumentation(instrumentation);
        instrumentationField.set(pluginInstrumentation);
    }

    public static void hookActivityManager() throws Throwable {
        Object atmSingletonObj = PluginReflectUtil.getAmsSingletonObject(true);
        Reflector singletonReflector = Reflector.on("android.util.Singleton").bind(atmSingletonObj);
        ReflectField mInstanceField = singletonReflector.field("mInstance");
        Object amsObj = mInstanceField.get();
        if (amsObj == null) {
            amsObj = singletonReflector.method("get").call();
        }
        // proxy
        Object atmProxyObj = Proxy.newProxyInstance(Activity.class.getClassLoader(),
                amsObj.getClass().getInterfaces(),
                new ActivityManagerHandler(amsObj));
        mInstanceField.set(atmProxyObj);

        Object amSingletonObj = PluginReflectUtil.getAmsSingletonObject(false);
        if (amSingletonObj != null && amSingletonObj != atmSingletonObj) {
            singletonReflector.bind(amSingletonObj);
            Object amObj = mInstanceField.get();
            if (amObj == null) {
                amObj = singletonReflector.method("get").call();
            }
            // proxy
            Object amProxyObj = Proxy.newProxyInstance(Activity.class.getClassLoader(),
                    amObj.getClass().getInterfaces(),
                    new ActivityManagerHandler(amObj));
            mInstanceField.set(amProxyObj);
        }
    }

    public static void hookPackageManager() throws Throwable {
        Reflector activityThreadReflector = Reflector.on("android.app.ActivityThread");
        Object activityThreadObj = activityThreadReflector.method("currentActivityThread").call();
        activityThreadReflector.bind(activityThreadObj);

        // proxy
        Object iPackageManager = activityThreadReflector.method("getPackageManager").call();
        PackageManagerHandler handler = new PackageManagerHandler(iPackageManager);
        Object proxy = Proxy.newProxyInstance(Activity.class.getClassLoader(),
                iPackageManager.getClass().getInterfaces(), handler);

        // set ActivityThread#sPackageManager
        activityThreadReflector.field("sPackageManager").set(proxy);

        // set ApplicationPackageManager#mPM
        PackageManager packageManager = x.app().getPackageManager();
        Reflector.with(packageManager).field("mPM").set(proxy);
    }
}
