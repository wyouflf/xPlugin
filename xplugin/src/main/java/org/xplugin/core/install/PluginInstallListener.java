package org.xplugin.core.install;

import org.xplugin.core.ctx.Plugin;

import java.util.Map;

/**
 * Created by jiaolei on 15/6/11.
 * 插件安装加载事件通知
 */
public interface PluginInstallListener {

    void onHostInitialised(boolean hasError);

    void onPluginsLoaded(Plugin target, Map<String, Plugin> newLoadedPlugins);

    void onPluginsLoadError(Throwable ex, boolean isCallbackError);
}
