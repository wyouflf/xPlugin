package org.xplugin.core.msg.invoker;

import android.content.pm.ActivityInfo;

import org.xplugin.core.app.ActivityInfoLoader;
import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.exception.PageNotFoundException;
import org.xplugin.core.msg.MsgCallback;
import org.xplugin.core.msg.PluginMsg;
import org.xutils.common.Callback;

import java.lang.reflect.Method;


/**
 * Created by jiaolei on 15/6/10.
 * 获取注册页面的执行者, 打开Activity或Fragment时由此执行者获取信息.
 */
public class GetPageInfoMsgInvoker extends BackgroundMsgInvoker {

    public GetPageInfoMsgInvoker(Plugin plugin, Object receiver, Method method) {
        super(plugin, receiver, method);
    }

    /**
     * 处理消息
     */
    @Override
    public void invoke(final PluginMsg msg) throws Throwable {
        MsgCallback msgCallback = msg.getCallback();
        try {
            if (msgCallback != null) {
                msgCallback.onStarted();
            }

            Class<?> targetClass = (Class<?>) msg.getInParam(ActivityInfoLoader.PAGE_CLASS_KEY);

            if (targetClass == null) {
                String className = null;
                String action = (String) msg.getInParam(ActivityInfoLoader.PAGE_ACTION_KEY);
                if (action != null) {
                    className = plugin.getConfig().findClassNameByAction(action);
                }

                if (className == null) {
                    throw new PageNotFoundException(action);
                } else if (className.charAt(0) == '.') {
                    className = plugin.getConfig().getPackageName() + className;
                }

                targetClass = plugin.loadClass(className);
            }

            ActivityInfo info = plugin.getConfig().findActivityInfoByClassName(targetClass.getName());
            msg.putOutParam(ActivityInfoLoader.PAGE_INFO_KEY, info);
            msg.putOutParam(ActivityInfoLoader.PAGE_CLASS_KEY, targetClass);
            msg.setReceiver(this.getReceiver());

            if (msgCallback != null) {
                msgCallback.onSuccess(msg);
            }
        } catch (Throwable ex) {
            if (msgCallback != null) {
                if (ex instanceof Callback.CancelledException) {
                    msgCallback.onCancelled((Callback.CancelledException) ex);
                } else {
                    msgCallback.onError(ex, false);
                }
            }
        } finally {
            if (msgCallback != null) {
                msgCallback.onFinished();
            }
        }
    }
}
