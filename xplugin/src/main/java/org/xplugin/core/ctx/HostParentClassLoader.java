package org.xplugin.core.ctx;


import org.xplugin.core.app.IntentHelper;
import org.xplugin.core.install.Installer;
import org.xplugin.core.util.PluginReflectUtil;
import org.xplugin.core.util.ReflectField;
import org.xplugin.core.util.Reflector;
import org.xutils.common.util.LogUtil;

import java.util.Map;

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

        if (className.startsWith(IntentHelper.ACTIVITY_TPL_PREFIX)) {
            Installer.waitForInit();
            result = IntentHelper.getTargetActivityClass();
        }

        if (result == null) {
            result = findClassFromModules(className);
        }

        if (result == null) {
            throw new ClassNotFoundException(className);
        }

        return result;
    }

    // 从已加载的模块查找类型
    private Class<?> findClassFromModules(String className) {
        if (className.endsWith("Activity")
                || className.endsWith("Fragment")
                || className.endsWith("Service")
                || className.endsWith("Receiver")
                || className.endsWith("Provider")) {
            Installer.waitForInit();
        }
        Class<?> result = null;
        Map<String, Module> moduleMap = Installer.getLoadedModules();
        if (moduleMap != null && moduleMap.size() > 0) {
            for (Module module : moduleMap.values()) {
                try {
                    // 不要让ModuleClassLoader的findClass查找其他依赖, 最好不要覆盖它.
                    result = ((ModuleClassLoader) module.getClassLoader()).loadClass(className, true);
                    if (result != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return result;
    }
}