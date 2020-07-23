package org.xplugin.core.msg.invoker;


import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.msg.PluginMsg;
import org.xplugin.core.msg.PluginMsgTask;
import org.xutils.x;

import java.lang.reflect.Method;

/**
 * Created by jiaolei on 15/6/10.
 * 异步任务的执行者
 */
public class BackgroundMsgInvoker extends AbsMsgInvoker {

    public BackgroundMsgInvoker(Plugin plugin, Object receiver, Method method) {
        super(plugin, receiver, method);
    }

    /**
     * 处理消息
     */
    @Override
    public void invoke(final PluginMsg msg) throws Throwable {
        x.task().start(new PluginMsgTask(msg) {
            @Override
            protected PluginMsg doBackground() throws Throwable {
                Object receiver = getReceiver();
                this.getMsg().setReceiver(receiver);
                return (PluginMsg) method.invoke(receiver, this.getMsg());
            }
        });
    }
}
