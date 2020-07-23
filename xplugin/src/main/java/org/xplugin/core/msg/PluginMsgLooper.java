package org.xplugin.core.msg;

import android.os.Looper;
import android.text.TextUtils;

import org.xplugin.core.PluginRuntime;
import org.xplugin.core.ctx.Host;
import org.xplugin.core.ctx.Module;
import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.exception.PluginMsgRejectException;
import org.xplugin.core.install.Installer;
import org.xplugin.core.install.PluginInstallListener;
import org.xutils.common.Callback;
import org.xutils.common.task.PriorityExecutor;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by jiaolei on 15/6/10.
 * 插件消息管理
 */
/*package*/ final class PluginMsgLooper implements PluginInstallListener {


    private static final int MSG_QUEUE_MAX_COUNT = 100;
    private final Timer msgMonitorTimer; // 定时巡视等待执行的消息
    private static final ConcurrentLinkedQueue<PluginMsg> MSG_QUEUE = new ConcurrentLinkedQueue<PluginMsg>();
    private static final PriorityExecutor MSG_EXECUTOR = new PriorityExecutor(1, true);

    /*package*/ PluginMsgLooper() {
        msgMonitorTimer = new Timer(false); // not a daemon, stop then app exit.
    }

    /**
     * 只能从HostApplication初始化
     */
    /*package*/ PluginMsgLooper start() {
        msgMonitorTimer.schedule(new TimerTask() {
            @Override
            public void run() { // 定时巡视是否有消息等待执行情况
                try {
                    while (MSG_QUEUE.isEmpty()) {
                        synchronized (MSG_QUEUE) {
                            try {
                                MSG_QUEUE.wait();
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                    // 处理未匹配和超时的消息
                    synchronized (MSG_QUEUE) {
                        Iterator<PluginMsg> iterator = MSG_QUEUE.iterator();
                        while (iterator.hasNext()) {
                            PluginMsg item = iterator.next();
                            if (item.isNoTargetMatched()) {
                                iterator.remove();
                                rejectMsg(item, PluginMsgRejectException.Reason.NO_MATCH);
                            } else if (item.isTimeOut()) {
                                iterator.remove();
                                rejectMsg(item, PluginMsgRejectException.Reason.TIMEOUT);
                            } else if (item.isCancelled()) {
                                iterator.remove();
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }, /*delay = */500, /*period = */50);

        return this;
    }

    /**
     * 发送消息, 消息由controller去处理.
     */
    public static void sendMsg(final PluginMsg msg) {
        if (msg == null || TextUtils.isEmpty(msg.getCmd())) {
            throw new IllegalArgumentException("msg or msg#getCmd() must not be null");
        }

        final MsgCallback callback = msg.getCallback();
        if (callback != null) {
            x.task().autoPost(new Runnable() {
                @Override
                public void run() {
                    try {
                        callback.onWaiting();
                        MSG_EXECUTOR.execute(new Runnable() {
                            @Override
                            public void run() {
                                sendMsgInternalSync(msg, null, false);
                            }
                        });
                    } catch (Throwable ex) {
                        LogUtil.e(ex.getMessage(), ex);
                        try {
                            callback.onError(ex, true);
                        } catch (Throwable ex2) {
                            LogUtil.e(ex2.getMessage(), ex2);
                        } finally {
                            try {
                                callback.onFinished();
                            } catch (Throwable ex3) {
                                LogUtil.e(ex3.getMessage(), ex3);
                            }
                        }
                    }
                }
            });
        } else {
            MSG_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    sendMsgInternalSync(msg, null, false);
                }
            });
        }
    }

    public static void sendMsgSync(final PluginMsg msg) {
        sendMsgInternalSync(msg, null, false);
    }

    /**
     * 发送消息, 消息由controller去处理.
     *
     * @param msg       消息
     * @param plugins   如果没有targetPackage, 发给指定的plugins集合, plugins为null时发给已加载的所有插件.
     * @param fromQueue 是否是队列中未处理的消息.
     */
    private static synchronized void sendMsgInternalSync(
            final PluginMsg msg, Map<String, Plugin> plugins, boolean fromQueue) {

        boolean sendSuccess = false;
        String pkg = msg.getTargetPackage();

        // start dispatch msg
        synchronized (fromQueue ? MSG_QUEUE : MSG_EXECUTOR) {
            if (fromQueue != MSG_QUEUE.contains(msg)) { // 超时原因被移除.
                return;
            }

            if (pkg != null && pkg.length() > 0) { // 发送给指定插件
                Plugin toPlugin = Installer.getLoadedPlugin(pkg);
                if (toPlugin != null) {
                    PluginMsgDispatcher dispatcher = toPlugin.getPluginMsgDispatcher();
                    if (dispatcher != null && dispatcher.dispatchMsg(msg)) {
                        sendSuccess = true;
                    } else {
                        rejectMsg(msg, PluginMsgRejectException.Reason.NO_MATCH);
                    }
                }
            } else { // 发送给自定义匹配插件
                if (plugins != null && !plugins.isEmpty()) {
                    for (Plugin plugin : plugins.values()) {
                        if (msg.match(plugin)) {
                            PluginMsgDispatcher dispatcher = plugin.getPluginMsgDispatcher();
                            if (dispatcher != null && dispatcher.dispatchMsg(msg)) {
                                sendSuccess = true;
                            }
                        }
                    }
                } else { // 发送给所有插件
                    Host host = Installer.getHost();
                    if (msg.match(host)) {
                        PluginMsgDispatcher dispatcher = host.getPluginMsgDispatcher();
                        if (dispatcher != null && dispatcher.dispatchMsg(msg)) {
                            sendSuccess = true;
                        }
                    }

                    Map<String, Module> moduleMap = Installer.getLoadedModules();
                    if (moduleMap != null) {
                        for (Plugin module : moduleMap.values()) {
                            if (msg.match(module)) {
                                PluginMsgDispatcher dispatcher = module.getPluginMsgDispatcher();
                                if (dispatcher != null && dispatcher.dispatchMsg(msg)) {
                                    sendSuccess = true;
                                }
                            }
                        }
                    }
                }
            } // else try match all loaded plugins


            if (sendSuccess) {
                if (fromQueue) {
                    // 队列中的消息分发完成后移除
                    synchronized (MSG_QUEUE) {
                        MSG_QUEUE.remove(msg);
                    }
                }
                return;
            }
        } // synchronized (MSG_QUEUE), dispatch msg finished.

        if (!fromQueue /*&& !sendSuccess*/) { // 未分发成功的等待再次处理
            // 加入消息队列等待加载事件回调再执行
            PluginMsg overmuchItem = null;
            synchronized (MSG_QUEUE) {
                MSG_QUEUE.offer(msg);
                if (MSG_QUEUE.size() > MSG_QUEUE_MAX_COUNT) {
                    overmuchItem = MSG_QUEUE.poll();
                }
                MSG_QUEUE.notifyAll();
            }
            rejectMsg(overmuchItem, PluginMsgRejectException.Reason.OVERFLOW);

            // 尝试加载未自动加载的插件包
            if (!TextUtils.isEmpty(pkg)) {
                //Installer.loadModule(pkg, null);
                try {
                    final AtomicBoolean loadError = new AtomicBoolean(false);
                    x.task().startSync(new Installer.LoadTask(pkg, new Callback.CommonCallback<Module>() {
                        @Override
                        public void onSuccess(Module result) {
                            sendMsgInternalSync(msg, null, true);
                        }

                        @Override
                        public void onError(Throwable ex, boolean isOnCallback) {
                            loadError.set(true);
                            synchronized (MSG_QUEUE) {
                                MSG_QUEUE.remove(msg);
                            }
                            MsgCallback msgCallback = msg.getCallback();
                            if (msgCallback != null) { // 及时反馈加载阶段的错误信息
                                msgCallback.onError(ex, isOnCallback);
                            }
                        }

                        @Override
                        public void onCancelled(CancelledException cex) {
                        }

                        @Override
                        public void onFinished() {
                            if (loadError.get()) {
                                MsgCallback msgCallback = msg.getCallback();
                                if (msgCallback != null) {
                                    msgCallback.onFinished();
                                }
                            }
                        }
                    }) {
                        @Override
                        public Looper customLooper() {
                            // 使用子线程Looper, 为防止在MainLooper中wait操作锁死的情况.
                            Looper looper = Looper.myLooper();
                            if (looper == null) {
                                Looper.prepare();
                                looper = Looper.myLooper();
                            }
                            return looper;
                        }
                    });
                } catch (Throwable ex) {
                    LogUtil.e(ex.getMessage(), ex);
                }
            } else { // TextUtils.isEmpty(pkg)
                msg.setNoTargetMatched(true); // 未匹配
            }
        }

    }

    /**
     * 宿主初始化完成回调
     * <p>
     * 如果宿主初始化报错, 则在onPluginsLoadError方法之后回调.
     *
     * @param hasError 初始化过程是否出错
     */
    @Override
    public void onHostInitialised(boolean hasError) {
        PluginRuntime.getRuntimeListener().onHostInitialised(hasError);
    }

    /**
     * 仅被异步安装或加载过程回调
     */
    @Override
    public void onPluginsLoaded(Plugin target, final Map<String, Plugin> newLoadedPlugins) {
        if (newLoadedPlugins != null && !newLoadedPlugins.isEmpty()) {
            PluginRuntime.getRuntimeListener().onPluginsLoaded(newLoadedPlugins);
        }
        synchronized (MSG_QUEUE) {
            for (final PluginMsg item : MSG_QUEUE) {
                MSG_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        sendMsgInternalSync(item, newLoadedPlugins, true);
                    }
                });
            }
        }
    }

    /**
     * 仅被异步安装或加载过程回调
     */
    @Override
    public void onPluginsLoadError(Throwable ex, boolean isCallbackError) {
        PluginRuntime.getRuntimeListener().onPluginsLoadError(ex, isCallbackError);
    }

    /**
     * 拒绝消息:
     * 1. 超时
     * 2. 队列溢出
     * 3. 目标插件不能分发该消息
     *
     * @param msg    消息
     * @param reason 拒绝原因
     */
    private static void rejectMsg(final PluginMsg msg, final PluginMsgRejectException.Reason reason) {
        if (msg != null) { // 未处理的消息移除后通知发送者
            final MsgCallback msgCallback = msg.getCallback();
            if (msgCallback != null) {
                x.task().autoPost(new Runnable() {
                    @Override
                    public void run() {
                        msgCallback.onError(new PluginMsgRejectException(msg, reason), false);
                    }
                });
            }
        }
    }
}
