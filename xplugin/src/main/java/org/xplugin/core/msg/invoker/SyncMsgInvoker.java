package org.xplugin.core.msg.invoker;


import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.msg.PluginMsg;
import org.xplugin.core.msg.PluginMsgTask;
import org.xutils.x;

import java.lang.reflect.Method;


/**
 * Created by jiaolei on 15/6/10.
 * 同步任务的执行者
 */
public class SyncMsgInvoker extends AbsMsgInvoker {

    public SyncMsgInvoker(Plugin plugin, Object receiver, Method method) {
        super(plugin, receiver, method);
    }

    /**
     * 处理消息
     */
    public void invoke(final PluginMsg msg) throws Throwable {
        x.task().startSync(new PluginMsgTask(msg) {
            @Override
            protected PluginMsg doBackground() throws Throwable {
                Object receiver = getReceiver();
                this.getMsg().setReceiver(receiver);
                return (PluginMsg) method.invoke(receiver, this.getMsg());
            }
        });
    }
}
