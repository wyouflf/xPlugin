package org.xplugin.core.msg.invoker;


import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.msg.PluginMsg;
import org.xutils.common.Callback;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Created by jiaolei on 15/6/10.
 * 消息执行者的基类
 */
public abstract class AbsMsgInvoker {

    protected final Plugin plugin;
    protected final WeakReference<Object> receiverRef;

    protected Method method;
    private Class<?> staticReceiverClass = null;

    public AbsMsgInvoker(Plugin plugin, Object receiver, Method method) {
        this.plugin = plugin;
        this.receiverRef = receiver == null ? null : new WeakReference<Object>(receiver);
        this.method = method;
    }

    public void setStaticReceiverClass(Class<?> staticReceiverClass) {
        this.staticReceiverClass = staticReceiverClass;
    }

    protected Object getReceiver() throws Throwable {
        Object result = null;
        if (receiverRef != null) {
            result = receiverRef.get();
        }

        boolean isStatic = method != null && Modifier.isStatic(method.getModifiers());

        if (result == null &&
                staticReceiverClass != null && !isStatic) {
            result = staticReceiverClass.newInstance();
        }

        if (result == null && !isStatic) {
            throw new Callback.CancelledException("receiver is null");
        }

        return result;
    }

    /**
     * 处理消息
     */
    public abstract void invoke(final PluginMsg msg) throws Throwable;
}
