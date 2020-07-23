package org.xplugin.demo.module1;

import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.msg.AbsPluginEntry;
import org.xplugin.core.msg.PluginMsg;
import org.xplugin.core.msg.PluginMsgEvent;

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

    @PluginMsgEvent(cmd = "sayHi", background = true)
    private PluginMsg sayHi(PluginMsg msg) {
        msg.putOutParam("content", "Hi, I'm module1.");
        return msg;
    }
}
