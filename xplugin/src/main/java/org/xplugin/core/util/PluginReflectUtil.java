package org.xplugin.core.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;

import org.xutils.common.util.IOUtil;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

import dalvik.system.DexFile;

public final class PluginReflectUtil {

    private static ReflectMethod addAssetPathMethod;
    private static ReflectMethod findClassMethod;
    private static String sWebViewResourcesDir;

    private static final String DEX = "ZGV4CjAzNQDsLlmvTNJ0Qhlx+oWyv99KN2uwf6xMxpQMBwAAcAAAAHhWNBIAAAAAAAAAAGAGAAAkAAAAcAAAAA4AAAAAAQAABAAAADgBAAAGAAAAaAEAAAcAAACYAQAAAwAAANABAADcBAAAMAIAAJgDAACdAwAApQMAALUDAADBAwAA1gMAAOgDAADvAwAA8gMAAPUDAAD6AwAAFgQAACkEAAA9BAAAUQQAAG0EAACVBAAAugQAANUEAADeBAAA4QQAAO8EAAD9BAAAAAUAABQFAAApBQAAPgUAAFcFAABmBQAAbwUAAIIFAACOBQAAlgUAAK0FAAC2BQAAzgUAAAcAAAAKAAAACwAAAAwAAAANAAAADgAAAA8AAAAQAAAAEQAAABMAAAAWAAAAFwAAABgAAAAZAAAACQAAAAMAAACIAwAACQAAAAUAAACQAwAAEwAAAAkAAAAAAAAAFgAAAAoAAAAAAAAAAQAAABIAAAAHAAQAAgAAAAcABAADAAAABwAKAAYAAAAHAAAAFAAAAAcABAAVAAAAAgABAB0AAAADAAIAAQAAAAUAAAAfAAAABgACAAEAAAAGAAMAGwAAAAcAAgABAAAACAACAAEAAAAGAAAAEQAAAAMAAAAAAAAABAAAAAAAAAAoBgAAAAAAAAcAAAARAAAAAwAAAAAAAAAFAAAAAAAAADYGAABUBgAACAAAABEAAAADAAAAAAAAAP////8AAAAASgYAAAAAAAAIAAAAAwABAHQDAABuAAAAYAAAABIREwIcADQgaAASABwCAgAaAxwAIxQLABwFBABNBQQAbjAAADIEDAIcAwIAGgQdABIlI1YLABwHBABNBwYAHAcLAE0HBgFuMAAAQwYMAyMUDAAaBhoATQYEABIGbjACAGIEDAIfAgIAI1QMABoHHgBNBwQATQYEAW4wAgAjBAwEHwQFACMHDABuMAIAZAcMBCNVDAAaBiIATQYFACMWCwAcBw0ATQcGAE0GBQFuMAIAIwUMAh8CBQAaAwgAJBANAAMADAMjFQwATQMFAG4wAgBCBQ8BDwAPAQgAAABjAAEAAQBsAAEAAQABAAAAcAMAAAQAAABwEAEAAAAOAAEAAQABAAAAggMAAAQAAABwEAEAAAAOAAEAAQABAAAAAAAAAAQAAABwEAEAAAAOAAcADgAKAA6I4QEaEOKHxOGHAAYADgAAAAIAAAADAAwAAgAAAAQACwADMS4wAAY8aW5pdD4ADkFQUExJQ0FUSU9OX0lEAApCVUlMRF9UWVBFABNCb290c3RyYXBDbGFzcy5qYXZhABBCdWlsZENvbmZpZy5qYXZhAAVERUJVRwABSQABTAADTExMABpMYW5kcm9pZC9vcy9CdWlsZCRWRVJTSU9OOwARTGphdmEvbGFuZy9DbGFzczsAEkxqYXZhL2xhbmcvT2JqZWN0OwASTGphdmEvbGFuZy9TdHJpbmc7ABpMamF2YS9sYW5nL3JlZmxlY3QvTWV0aG9kOwAmTG9yZy94cGx1Z2luL2NvcmUvdXRpbC9Cb290c3RyYXBDbGFzczsAI0xvcmcveHBsdWdpbi9jb3JlL3V0aWwvQnVpbGRDb25maWc7ABlMb3JnL3hwbHVnaW4vY29yZS91dGlsL1I7AAdTREtfSU5UAAFWAAxWRVJTSU9OX0NPREUADFZFUlNJT05fTkFNRQABWgASW0xqYXZhL2xhbmcvQ2xhc3M7ABNbTGphdmEvbGFuZy9PYmplY3Q7ABNbTGphdmEvbGFuZy9TdHJpbmc7ABdkYWx2aWsuc3lzdGVtLlZNUnVudGltZQANZXhlbXB0SGlkZUFwaQAHZm9yTmFtZQARZ2V0RGVjbGFyZWRNZXRob2QACmdldFJ1bnRpbWUABmludm9rZQAVb3JnLnhwbHVnaW4uY29yZS51dGlsAAdyZWxlYXNlABZzZXRIaWRkZW5BcGlFeGVtcHRpb25zAFh+fkQ4eyJjb21waWxhdGlvbi1tb2RlIjoicmVsZWFzZSIsImhhcy1jaGVja3N1bXMiOmZhbHNlLCJtaW4tYXBpIjoxOSwidmVyc2lvbiI6IjIuMC44OCJ9AAAAAgADgYAEqAYBCbAEBQABAAEZARkBGQEZARkFgYAEwAYAAAEABoKABNgGBRcgFyEfBAEXAAAADgAAAAAAAAABAAAAAAAAAAEAAAAkAAAAcAAAAAIAAAAOAAAAAAEAAAMAAAAEAAAAOAEAAAQAAAAGAAAAaAEAAAUAAAAHAAAAmAEAAAYAAAADAAAA0AEAAAEgAAAEAAAAMAIAAAMgAAADAAAAcAMAAAEQAAACAAAAiAMAAAIgAAAkAAAAmAMAAAAgAAADAAAAKAYAAAUgAAABAAAAVAYAAAAQAAABAAAAYAYAAA==";

    private PluginReflectUtil() {
    }

    /**
     * 在App的classLoader被修改之前调用,
     * 防止ReflectUtil在load时陷入findClass的死循环.
     */
    public static void init() {

        // 避免调用隐藏API的警告
        boolean exemptHideApi = false;
        if (!exemptHideApi()) {
            exemptHideApi = exemptHideApiByDexFile();
        } else {
            exemptHideApi = true;
        }
        LogUtil.d("exemptHideApi: " + exemptHideApi);

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

    public static String getWebViewResourcesDir() {
        if (sWebViewResourcesDir != null) {
            return sWebViewResourcesDir;
        }

        String pkg = null;
        try {
            int sdkVersion = Build.VERSION.SDK_INT;
            if (sdkVersion < Build.VERSION_CODES.LOLLIPOP) { // [ ~, 21)
                return null;
            } else if (sdkVersion < Build.VERSION_CODES.N) { // [21, 24)
                pkg = Reflector.on("android.webkit.WebViewFactory").method("getWebViewPackageName").call();
            } else { // [24, ~]
                Context context = Reflector.on("android.webkit.WebViewFactory").method("getWebViewContextAndSetProvider").call();
                if (context != null) {
                    pkg = context.getApplicationInfo().packageName;
                }
            }
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }

        if (TextUtils.isEmpty(pkg)) {
            pkg = "com.google.android.webview";
        }

        try {
            PackageInfo pi = x.app().getPackageManager().getPackageInfo(pkg, PackageManager.GET_SHARED_LIBRARY_FILES);
            sWebViewResourcesDir = pi.applicationInfo.sourceDir;
            return sWebViewResourcesDir;
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }

        return null;
    }

    private static boolean exemptHideApiByDexFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            FileOutputStream fos = null;
            File codeCacheDir = x.app().getCodeCacheDir();
            File code = new File(codeCacheDir, System.currentTimeMillis() + ".dex");
            try {
                byte[] bytes = Base64.decode(DEX, Base64.NO_WRAP);

                fos = new FileOutputStream(code);
                fos.write(bytes);
                IOUtil.closeQuietly(fos);

                DexFile dexFile = new DexFile(code);
                Class<?> reflectUtilCls = dexFile.loadClass("org.xplugin.core.util.BootstrapClass", null);
                Method exemptAll = reflectUtilCls.getDeclaredMethod("exemptHideApi");
                return (boolean) exemptAll.invoke(null);
            } catch (Throwable ex) {
                LogUtil.e(ex.getMessage(), ex);
                return false;
            } finally {
                IOUtil.closeQuietly(fos);
                IOUtil.deleteFileOrDir(code);
            }
        }
        return true;
    }

    private static boolean exemptHideApi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                Method forName = Class.class.getDeclaredMethod("forName", String.class);
                Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);

                Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, new Object[]{"dalvik.system.VMRuntime"});

                Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
                Object sVmRuntime = getRuntime.invoke(null);

                Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
                Object ignoredHiddenMethodPrefix = new String[]{"L"}; // the method signature prefix, such as "Ldalvik/system", "Landroid" or even "L"
                setHiddenApiExemptions.invoke(sVmRuntime, ignoredHiddenMethodPrefix);
                return true;
            } catch (Throwable ex) {
                return false;
            }
        }
        return true;
    }
}