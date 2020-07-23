package org.xplugin.core.msg;

import org.xplugin.core.ctx.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by jiaolei on 15/6/10.
 * 模块中的插件消息入口类
 */
public abstract class AbsPluginEntry {

    private Plugin plugin;
    private Map<String, Class<? extends PluginMsgTask>> registeredTaskMap;

    public AbsPluginEntry(Plugin plugin) {
        this.plugin = plugin;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public final synchronized void register(String cmd, Class<? extends PluginMsgTask> taskCls) {
        if (registeredTaskMap == null) {
            registeredTaskMap = new ConcurrentHashMap<String, Class<? extends PluginMsgTask>>();
        }
        registeredTaskMap.put(cmd, taskCls);
    }

    public final Map<String, Class<? extends PluginMsgTask>> getRegisteredTaskMap() {
        return registeredTaskMap;
    }

    /**
     * 插件被加载完成时调用, 调用线程不在UI线程.
     */
    public abstract void onLoaded();

    /**
     * 卸载或停用插件时调用, 调用线程不在UI线程.
     */
    public abstract void onDestroy();
}
