package org.xplugin.core.msg;

import org.xplugin.core.ctx.Plugin;
import org.xutils.common.Callback;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by jiaolei on 15/6/11.
 * 通用消息
 */
public class PluginMsg implements Callback.Cancelable, Cloneable {

    private static final long DEFAULT_TIME_OUT = 15 * 1000;

    private String targetPackage;
    private final String cmd;
    private long expiry;

    private ConcurrentHashMap<String, Object> inParams = new ConcurrentHashMap<String, Object>();
    private ConcurrentHashMap<String, Object> outParams = new ConcurrentHashMap<String, Object>();

    private volatile boolean isCancelled = false;
    private volatile boolean noTargetMatched = false;
    private MsgCallback callback;
    private MsgMatcher msgMatcher;

    private Object receiver;

    private final Object SEND_SYNC_LOCK = new Object();
    private volatile boolean isSendSyncFinished = false;

    public PluginMsg(String cmd) {
        this.cmd = cmd;
        this.setTimeOut(DEFAULT_TIME_OUT);
    }

    public final void send(MsgCallback callback) {
        this.callback = callback;
        PluginMsgLooper.sendMsg(this);
    }

    public final synchronized PluginMsg sendSync() throws Throwable {
        final PluginMsg[] resultFinal = {null};
        final Throwable[] exFinal = {null};
        isSendSyncFinished = false;

        this.callback = new MsgCallback() {
            @Override
            public void onSuccess(PluginMsg result) {
                resultFinal[0] = result;
                synchronized (SEND_SYNC_LOCK) {
                    isSendSyncFinished = true;
                    SEND_SYNC_LOCK.notify();
                }
            }

            @Override
            public void onError(Throwable ex, boolean isOnCallback) {
                exFinal[0] = ex;
                synchronized (SEND_SYNC_LOCK) {
                    isSendSyncFinished = true;
                    SEND_SYNC_LOCK.notify();
                }
            }

            @Override
            public void onFinished() {
                synchronized (SEND_SYNC_LOCK) {
                    isSendSyncFinished = true;
                    SEND_SYNC_LOCK.notify();
                }
            }
        };

        PluginMsgLooper.sendMsgSync(this);

        synchronized (SEND_SYNC_LOCK) {
            while (!isSendSyncFinished) {
                try {
                    SEND_SYNC_LOCK.wait();
                } catch (Throwable ignored) {
                    break;
                }
            }
        }

        if (exFinal[0] != null) {
            throw exFinal[0];
        }

        return resultFinal[0];
    }

    @Override
    public final void cancel() {
        isCancelled = true;
    }

    @Override
    public final boolean isCancelled() {
        return isCancelled;
    }

    public boolean isNoTargetMatched() {
        return noTargetMatched;
    }

    public void setNoTargetMatched(boolean noTargetMatched) {
        this.noTargetMatched = noTargetMatched;
    }

    public final PluginMsg setTargetPackage(String targetPackage) {
        this.targetPackage = targetPackage;
        return this;
    }

    public final PluginMsg setTimeOut(long timeOut) {
        this.expiry = System.currentTimeMillis() + timeOut;
        return this;
    }

    public final PluginMsg putInParam(String key, Object value) {
        if (key == null || value == null) return this;
        inParams.put(key, value);
        return this;
    }

    public final PluginMsg putOutParam(String key, Object value) {
        if (key == null || value == null) return this;
        outParams.put(key, value);
        return this;
    }

    public final PluginMsg setMsgMatcher(MsgMatcher msgMatcher) {
        this.msgMatcher = msgMatcher;
        return this;
    }

    public final Object getInParam(String key) {
        return inParams.get(key);
    }

    public final Map<String, Object> getInParams() {
        return new HashMap<String, Object>(inParams);
    }

    public final Object getOutParam(String key) {
        return outParams.get(key);
    }

    public final Map<String, Object> getOutParams() {
        return new HashMap<String, Object>(outParams);
    }

    public final String getTargetPackage() {
        return targetPackage;
    }

    public final boolean isTimeOut() {
        return expiry > 0 && System.currentTimeMillis() > expiry;
    }

    public final String getCmd() {
        return cmd;
    }

    public final MsgMatcher getMsgMatcher() {
        return msgMatcher;
    }

    public MsgCallback getCallback() {
        return callback;
    }

    public Object getReceiver() {
        return receiver;
    }

    public void setReceiver(Object receiver) {
        this.receiver = receiver;
    }

    /**
     * 只有未指定targetPackage时, 才调用这个方法.
     *
     * @param plugin
     * @return 是否要将消息发送给这个插件
     */
    public final boolean match(Plugin plugin) {
        return msgMatcher == null || msgMatcher.match(plugin);
    }

    @Override
    public PluginMsg clone() {
        PluginMsg msg = null;
        try {
            msg = (PluginMsg) super.clone();
            msg.receiver = null;
        } catch (CloneNotSupportedException neverHappened) {
            // this instanceof Cloneable == true
        }
        return msg;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PluginMsg{");
        sb.append("cmd='").append(cmd).append('\'');
        sb.append(", targetPackage='").append(targetPackage).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
