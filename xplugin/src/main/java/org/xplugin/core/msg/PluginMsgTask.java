package org.xplugin.core.msg;


import org.xutils.common.Callback;
import org.xutils.common.task.AbsTask;

/**
 * Created by jiaolei on 15/7/16.
 * 消息任务的默认抽象类
 */
public abstract class PluginMsgTask extends AbsTask<PluginMsg> {

    private final PluginMsg msg;

    public PluginMsgTask(PluginMsg msg) {
        super(msg);
        this.msg = msg;
    }

    public PluginMsg getMsg() {
        return msg;
    }

    @Override
    protected void onWaiting() {
        // onWaiting be invoked via PluginManager#sendMsg(...)
    }

    @Override
    protected void onStarted() {
        MsgCallback msgCallback = msg.getCallback();
        if (msgCallback != null) {
            msgCallback.onStarted();
        }
    }

    @Override
    protected void onSuccess(PluginMsg result) {
        MsgCallback msgCallback = msg.getCallback();
        if (msgCallback != null) {
            msgCallback.onSuccess(result);
        }
    }

    @Override
    protected void onCancelled(Callback.CancelledException cex) {
        MsgCallback msgCallback = msg.getCallback();
        if (msgCallback != null) {
            msgCallback.onCancelled(cex);
        }
    }

    @Override
    protected void onError(Throwable ex, boolean isCallbackError) {
        MsgCallback msgCallback = msg.getCallback();
        if (msgCallback != null) {
            msgCallback.onError(ex, isCallbackError);
        }
    }

    @Override
    protected void onFinished() {
        MsgCallback msgCallback = msg.getCallback();
        if (msgCallback != null) {
            msgCallback.onFinished();
        }
    }
}
