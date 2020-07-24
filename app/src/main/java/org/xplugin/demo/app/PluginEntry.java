package org.xplugin.demo.app;

import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.msg.AbsPluginEntry;
import org.xplugin.core.msg.PluginMsg;
import org.xplugin.core.msg.PluginMsgEvent;

/**
 * 消息通信的入口
 * 类名约定: $packageName.PluginEntry 的形式; 为方便集成到对外sdk, 宿主的PluginEntry可通过初始化接口指定.
 */
public class PluginEntry extends AbsPluginEntry {

    public PluginEntry(Plugin plugin) {
        super(plugin);
    }

    @Override
    public void onLoaded() {

    }

    @Override
    public void onDestroy() {

    }

    @PluginMsgEvent(cmd = "demo", background = true)
    private PluginMsg demo(PluginMsg msg) {
        // do something
        return msg;
    }
}
