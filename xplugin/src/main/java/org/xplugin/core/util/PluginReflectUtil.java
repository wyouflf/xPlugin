package org.xplugin.core.util;

import android.content.res.AssetManager;
import android.os.Build;

import org.xutils.common.util.LogUtil;

import java.lang.reflect.Method;

public final class PluginReflectUtil {

    private static ReflectMethod addAssetPathMethod;
    private static ReflectMethod findClassMethod;

    private PluginReflectUtil() {
    }

    /**
     * 在App的classLoader被修改之前调用,
     * 防止ReflectUtil在load时陷入findClass的死循环.
     */
    public static void init() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                Reflector clsReflector = Reflector.on(Class.class);
                ReflectMethod forName = clsReflector.method("forName", String.class);
                ReflectMethod getDeclaredMethod = clsReflector.method("getDeclaredMethod", String.class, Class[].class);

                Class<?> vmRuntimeClass = forName.call("dalvik.system.VMRuntime");

                Method getRuntime = getDeclaredMethod.callByCaller(vmRuntimeClass, "getRuntime", null);
                Object sVmRuntime = getRuntime.invoke(null);

                Method setHiddenApiExemptions = getDeclaredMethod.callByCaller(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
                Object ignoredHiddenMethodPrefix = new String[]{"L"}; // the method signature prefix, such as "Ldalvik/system", "Landroid" or even "L"
                setHiddenApiExemptions.invoke(sVmRuntime, ignoredHiddenMethodPrefix);
            } catch (Throwable ex) {
                LogUtil.e("reflect VMRuntime failed:", ex);
            }
        }

        try {
            addAssetPathMethod = Reflector.on(AssetManager.class).method("addAssetPath", String.class);
        } catch (Throwable ex) {
            throw new RuntimeException("Plugin init failed", ex);
        }

        try {
            findClassMethod = Reflector.on(ClassLoader.class).method("findClass", String.class);
        } catch (Throwable ex) {
            throw new RuntimeException("Plugin init failed", ex);
        }
    }

    public static int addAssetPath(AssetManager assetManager, String path) {
        try {
            return addAssetPathMethod.callByCaller(assetManager, path);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static Class<?> findClass(ClassLoader classLoader, String className) {
        try {
            return findClassMethod.callByCaller(classLoader, className);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Object getAmsSingletonObject(boolean useActivityTaskManager) throws Throwable {
        Object amsSingletonObj = null;

        if (useActivityTaskManager) {
            try { // api [29, ~]
                amsSingletonObj = Reflector.on("android.app.ActivityTaskManager").field("IActivityTaskManagerSingleton").get();
            } catch (Throwable ignored) {
            }
        }

        if (amsSingletonObj == null) {
            try { // api [26, 28]
                amsSingletonObj = Reflector.on("android.app.ActivityManager").field("IActivityManagerSingleton").get();
            } catch (Throwable ignored) {
            }
        }

        if (amsSingletonObj == null) {
            try { // api [19, 25]
                amsSingletonObj = Reflector.on("android.app.ActivityManagerNative").field("gDefault").get();
            } catch (Throwable ignored) {
            }
        }

        return amsSingletonObj;
    }
}