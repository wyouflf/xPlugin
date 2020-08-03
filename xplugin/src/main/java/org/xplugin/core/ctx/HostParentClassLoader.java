package org.xplugin.core.ctx;


import android.os.Build;

import org.xplugin.core.app.IntentHelper;
import org.xplugin.core.install.Installer;
import org.xplugin.core.util.PluginReflectUtil;
import org.xplugin.core.util.ReflectField;
import org.xplugin.core.util.Reflector;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*package*/ final class HostParentClassLoader extends ClassLoader {

    private static HostParentClassLoader instance;
    private static ClassLoader appClassLoader;
    private static ClassLoader bootClassLoader;

    private Host host;

    private HostParentClassLoader(Host host) {
        PluginReflectUtil.init();
        this.host = host;
        // class loader结构:
        // bootClassLoader <-- HostParentClassLoader <-- appClassLoader
        appClassLoader = host.getClassLoader();
        try {
            ReflectField parentField = Reflector.on(ClassLoader.class).field("parent");
            bootClassLoader = appClassLoader.getParent();
            parentField.set(this, bootClassLoader);
            parentField.set(appClassLoader, this);
        } catch (Throwable e) {
            LogUtil.e("init app class loader", e);
        }
    }

    public synchronized static void init(Host host) {
        if (instance == null) {
            instance = new HostParentClassLoader(host);
        }
    }

    public static HostParentClassLoader getInstance() {
        return instance;
    }

    public static ClassLoader getBootClassLoader() {
        return bootClassLoader;
    }

    public static ClassLoader getAppClassLoader() {
        return appClassLoader;
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return loadClass(className, false);
    }

    @Override
    protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
        Class<?> hotFixCls = null;
        if ((hotFixCls = host.findHotFixClass(className)) != null) {
            return hotFixCls;
        }

        Class<?> result = null;
        try {
            result = super.loadClass(className, false);
        } catch (Throwable ignored) {
        }

        if (result == null) {
            result = PluginReflectUtil.findClass(appClassLoader, className);
        }

        if (result == null && className.startsWith(IntentHelper.ACTIVITY_TPL_PREFIX)) {
            Installer.waitForInit();
            result = IntentHelper.getTargetActivityClass();
        }

        if (result == null) {
            try {
                result = findClassFromModules(className, null);
            } catch (Throwable ignored) {
            }
        }

        if (result == null) {
            throw new ClassNotFoundException(className);
        }

        return result;
    }

    /*package*/
    static Class<?> findClassFromModules(final String className, Module exclude) throws Throwable {
        Class<?> result = null;

        HashSet<String> excludeSet = new HashSet<String>();
        if (exclude != null) {
            excludeSet.add(exclude.getConfig().getPackageName());
        }

        Module runtimeModule = Installer.getRuntimeModule();
        if (runtimeModule != null && runtimeModule != exclude) {
            try {
                excludeSet.add(runtimeModule.getConfig().getPackageName());
                result = runtimeModule.loadClass(className);
            } catch (Throwable ignored) {
            }
        }

        if (result == null) {
            Map<String, Module> moduleMap = Installer.getLoadedModules();
            if (!moduleMap.isEmpty()) {
                for (Module module : moduleMap.values()) {
                    if (module != runtimeModule && module != exclude) {
                        try {
                            excludeSet.add(module.getConfig().getPackageName());
                            result = module.loadClass(className);
                            if (result != null) {
                                break;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }

        if (result == null && !className.startsWith("android.")) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && className.startsWith("androidx.")) {
                return null;
            }
            Set<String> installedModules = Installer.getInstalledModules();
            if (!installedModules.isEmpty()) {
                for (String pkg : installedModules) {
                    if (!excludeSet.contains(pkg)) {
                        try {
                            Module module = x.task().startSync(new Installer.LoadTask(pkg, null));
                            result = module.loadClass(className);
                            if (result != null) {
                                break;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }

        return result;
    }
}