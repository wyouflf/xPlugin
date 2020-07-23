package org.xplugin.core.msg.invoker;


import org.xplugin.core.msg.MsgCallback;
import org.xplugin.core.msg.PluginMsg;
import org.xplugin.core.msg.PluginMsgTask;
import org.xutils.common.task.AbsTask;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.lang.reflect.Constructor;


/**
 * Created by jiaolei on 15/6/10.
 * 由AbsPluginEntry注册的任务的执行者
 */
public class TaskMsgInvoker extends BackgroundMsgInvoker {

    private final Class<? extends PluginMsgTask> taskCls;

    public TaskMsgInvoker(Class<? extends PluginMsgTask> taskCls) {
        super(null, null, null);
        this.taskCls = taskCls;
    }

    /**
     * 处理消息
     */
    public void invoke(final PluginMsg msg) {
        AbsTask<PluginMsg> task = null;
        MsgCallback msgCallback = msg.getCallback();
        try {
            // create new PluginMsgTask
            Constructor<? extends PluginMsgTask> constructor = taskCls.getConstructor(PluginMsg.class);
            task = constructor.newInstance(msg);
            msg.setReceiver(task);
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
            if (msgCallback != null) {
                try {
                    msgCallback.onError(ex, false);
                } catch (Throwable ex2) {
                    LogUtil.e(ex2.getMessage(), ex2);
                } finally {
                    try {
                        msgCallback.onFinished();
                    } catch (Throwable ex3) {
                        LogUtil.e(ex3.getMessage(), ex3);
                    }
                }
            }
            return;
        }

        x.task().start(task);
    }
}
