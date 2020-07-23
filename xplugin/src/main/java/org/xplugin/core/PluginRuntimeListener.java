package org.xplugin.core;

import org.xplugin.core.ctx.Plugin;

import java.io.File;
import java.util.Map;

public interface PluginRuntimeListener {
    /**
     * 是否使用调试模式
     */
    boolean isDebug();

    /**
     * 获取宿主的 PluginEntry 的实现类型
     *
     * @return nullable
     */
    Class<?> getHostPluginEntry();

    /**
     * 获取公共资源模块的包名
     */
    String getRuntimePkg();

    /**
     * 验证插件文件
     */
    boolean verifyPluginFile(File pluginFile);


    /**
     * 宿主初始化完成回调
     * <p>
     * 如果宿主初始化报错, 则在onPluginsLoadError方法之后回调.
     *
     * @param hasError 初始化过程是否出错
     */
    void onHostInitialised(boolean hasError);

    /**
     * 插件加载回调
     *
     * @param plugins 新加载的插件
     */
    void onPluginsLoaded(Map<String, Plugin> plugins);

    /**
     * 模块加载错误回调
     */
    void onPluginsLoadError(Throwable ex, boolean isCallbackError);
}
