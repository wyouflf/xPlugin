package org.xplugin.core.ctx;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.text.TextUtils;

import org.xplugin.core.install.Config;
import org.xplugin.core.install.Installer;
import org.xplugin.core.msg.PluginMsgDispatcher;

import java.io.File;
import java.util.HashMap;

/**
 * Created by jiaolei on 15/6/10.
 * 插件
 */
public abstract class Plugin {
    private final Config config;
    private final Context context;
    private PluginMsgDispatcher pluginMsgDispatcher;
    private HashMap<String, Class<?>> hotFixClassMap;

    /*package*/ Plugin(Context context, Config config) {
        this.context = context;
        this.config = config;
    }

    public final synchronized void init() {
        if (this.pluginMsgDispatcher == null) {
            this.pluginMsgDispatcher = new PluginMsgDispatcher(this);
        }
    }

    /**
     * 插件被加载完成时调用, 调用线程不在UI线程.
     */
    public void onLoaded() {
        PluginMsgDispatcher dispatcher = getPluginMsgDispatcher();
        if (dispatcher != null) {
            dispatcher.onLoaded();
        }
    }

    /**
     * 卸载或停用插件时调用, 调用线程不在UI线程.
     */
    public void onDestroy() {
        PluginMsgDispatcher dispatcher = getPluginMsgDispatcher();
        if (dispatcher != null) {
            dispatcher.onDestroy();
        }
    }

    /**
     * 获取对象或类型所在的插件
     *
     * @param obj 对象实例或class
     * @return 产生这个实例的Plugin
     */
    public static Plugin getPlugin(Object obj) {
        ClassLoader classLoader = null;
        if (obj instanceof Class) {
            classLoader = ((Class<?>) obj).getClassLoader();
        } else if (obj instanceof ClassLoader) {
            classLoader = (ClassLoader) obj;
        } else if (obj instanceof ModuleContext) {
            classLoader = ((ModuleContext) obj).getClassLoader();
        } else if (obj instanceof ContextProxy) {
            classLoader = ((ContextProxy) obj).getClassLoader();
        } else {
            classLoader = obj.getClass().getClassLoader();
        }
        if (classLoader instanceof ModuleClassLoader) {
            return ((ModuleClassLoader) classLoader).getModule();
        } else {
            return Installer.getHost();
        }
    }

    public abstract Class<?> loadClass(String name) throws ClassNotFoundException;

    /*package*/
    final ClassLoader getClassLoader() {
        return this.context.getClassLoader();
    }

    public final Config getConfig() {
        return this.config;
    }

    public final Context getContext() {
        return this.context;
    }

    public final File getPluginFile() {
        if (context instanceof ModuleContext) {
            return ((ModuleContext) context).getPluginFile();
        } else {
            return new File(context.getApplicationInfo().sourceDir);
        }
    }

    public PluginMsgDispatcher getPluginMsgDispatcher() {
        return this.pluginMsgDispatcher;
    }

    public void registerHotFixClass(String clsName, Class<?> insteadCls) {
        if (hotFixClassMap == null) {
            hotFixClassMap = new HashMap<String, Class<?>>(1);
        }
        hotFixClassMap.put(clsName, insteadCls);
    }

    public Class<?> findHotFixClass(String clsName) {
        return hotFixClassMap == null ? null : hotFixClassMap.get(clsName);
    }

    public boolean isActivityRegistered(String className) {
        if (TextUtils.isEmpty(className)) return false;
        ActivityInfo info = this.getConfig().findActivityInfoByClassName(className);
        return info != null;
    }

    public boolean isServiceRegistered(String className) {
        if (TextUtils.isEmpty(className)) return false;
        ServiceInfo info = this.getConfig().findServiceInfoByClassName(className);
        return info != null;
    }

    public boolean isProviderRegistered(String authority) {
        if (TextUtils.isEmpty(authority)) return false;
        ProviderInfo info = this.getConfig().findProviderInfoByAuthority(authority);
        return info != null;
    }

    public boolean isActionRegistered(String action) {
        if (TextUtils.isEmpty(action)) return false;
        String className = this.getConfig().findClassNameByAction(action);
        return !TextUtils.isEmpty(className);
    }

    @Override
    public String toString() {
        return this.config.getPackageName();
    }
}
