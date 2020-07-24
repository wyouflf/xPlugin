package org.xplugin.demo.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

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

    @PluginMsgEvent(cmd = "getView", background = false)
    private PluginMsg getView(PluginMsg msg) {
        Context context = this.getPlugin().getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        View testView = inflater.inflate(R.layout.activity_thirdparty_activiy, null);
        msg.putOutParam("testView", testView);
        return msg;
    }
}
