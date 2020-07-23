package org.xplugin.core.ctx;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;

import org.xplugin.core.install.Installer;

/**
 * ContextWrapper for Activity or AlertDialog
 */
public class ContextProxy extends ContextThemeWrapper {

    private Plugin plugin;
    private Resources hostResources;

    /**
     * Context 代理
     *
     * @param baseContext Activity或Fragment的context
     * @param plugin      插件实例
     */
    public ContextProxy(Context baseContext, Plugin plugin) {
        super(baseContext, 0);
        this.plugin = plugin;
        //修复在ZTE G717C上发现的调用ContextProxy#getResources方法陷入死循环的问题
        if (plugin instanceof Host) {
            hostResources = plugin.getContext().getResources();
        }
    }

    public Plugin getPlugin() {
        return getPluginInternal(true);
    }

    private Plugin getPluginInternal(boolean fromGetPluginMethod) {
        Plugin result = plugin;
        if (result == null) {
            result = fromGetPluginMethod ? Installer.getHost() : getPlugin();
        }
        return result;
    }

    @Override
    public Object getSystemService(String name) {
        return getPluginInternal(false).getContext().getSystemService(name);
    }

    @Override
    public Resources.Theme getTheme() {
        return getPluginInternal(false).getContext().getTheme();
    }

    @Override
    public AssetManager getAssets() {
        if (hostResources != null) {
            return hostResources.getAssets();
        }
        return getPluginInternal(false).getContext().getAssets();
    }

    @Override
    public Resources getResources() {
        if (hostResources != null) {
            return hostResources;
        }
        return getPluginInternal(false).getContext().getResources();
    }

    @Override
    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        Context ctx = getPluginInternal(false).getContext();
        if (ctx instanceof ContextThemeWrapper) {
            ((ContextThemeWrapper) ctx).applyOverrideConfiguration(overrideConfiguration);
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        return getPluginInternal(false).getContext().getClassLoader();
    }
}
