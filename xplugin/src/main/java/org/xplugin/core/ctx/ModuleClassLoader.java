package org.xplugin.core.ctx;

import org.xplugin.core.install.Config;
import org.xplugin.core.install.Installer;
import org.xplugin.core.util.PluginReflectUtil;
import org.xutils.common.util.LogUtil;

import java.io.File;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;

public final class ModuleClassLoader extends DexClassLoader {

    private Module module;

    private final Config config;
    private final ClassLoader bootClassLoader;
    private final ClassLoader appClassLoader;
    private ClassLoader additionClassLoader;

    public ModuleClassLoader(File pluginFile, File pluginDir, Config config) {
        super(  // dexPath
                pluginFile.getAbsolutePath(),
                // optimizedDir
                pluginDir.getAbsolutePath(),
                // libDir
                pluginDir.getAbsolutePath(),
                // parent
                HostParentClassLoader.getBootClassLoader());
        this.config = config;
        this.bootClassLoader = HostParentClassLoader.getBootClassLoader();
        this.appClassLoader = HostParentClassLoader.getAppClassLoader();
    }

    /*package*/ void attachModule(Module module) {
        if (this.module == null) {
            this.module = module;
        }
    }

    public Config getConfig() {
        return config;
    }

    public Module getModule() {
        return module;
    }

    public void setAdditionClassLoader(ClassLoader additionClassLoader) {
        this.additionClassLoader = additionClassLoader;
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return loadClass(className, false);
    }

    @Override
    public Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
        Class<?> hotFixCls = null;
        if ((hotFixCls = module.findHotFixClass(className)) != null) {
            return hotFixCls;
        }

        Class<?> result = super.findLoadedClass(className);

        Throwable cause = null;
        if (result == null) {
            try {
                result = this.bootClassLoader.loadClass(className);
            } catch (Throwable ex) {
                cause = ex;
            }
        }

        if (result == null) { // 优先查找模块内部类型, 因为对应的资源id是模块内部编译结果.
            try {
                result = this.findClass(className);
            } catch (Throwable ex) {
                cause = ex;
            }
        }

        if (result == null && additionClassLoader != null) {
            try {
                if (additionClassLoader instanceof BaseDexClassLoader) {
                    result = PluginReflectUtil.findClass(additionClassLoader, className);
                } else {
                    result = additionClassLoader.loadClass(className);
                }
            } catch (Throwable ex) {
                cause = ex;
            }
        }

        if (result == null && !resolve) { // 从app classLoader查找
            try {
                result = PluginReflectUtil.findClass(appClassLoader, className);
            } catch (Throwable ex) {
                cause = ex;
            }
        }

        if (result == null && !resolve) { // 从其他 Module 查找
            try {
                result = HostParentClassLoader.findClassFromModules(className, this.module);
            } catch (Throwable ex) {
                cause = ex;
            }
        }

        if (result == null) {
            throw new ClassNotFoundException(className, cause);
        }

        return result;
    }

    /**
     * 修复阿里云v2.1等早期版本findLibrary
     * 以系统内置为最高优先级加载的问题
     */
    @Override
    public String findLibrary(String name) {
        File libFile = null;
        try {
            libFile = Installer.findLibrary(config.getPackageName(), name);
        } catch (Throwable ex) {
            LogUtil.d("findLibrary", ex);
        }
        if (libFile != null && libFile.exists()) {
            return libFile.getAbsolutePath();
        } else {
            return super.findLibrary(name);
        }
    }

    @Override
    public String toString() {
        return "ModuleClassLoader: " + this.config.getPackageName();
    }
}
