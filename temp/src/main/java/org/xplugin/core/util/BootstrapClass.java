package org.xplugin.core.util;

import android.os.Build;

import java.lang.reflect.Method;

public final class BootstrapClass {

    public static boolean exemptHideApi() {
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