package org.xplugin.core.msg;

import org.xplugin.core.PluginRuntime;
import org.xplugin.core.app.ActivityInfoLoader;
import org.xplugin.core.ctx.Host;
import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.exception.PluginEntryNotFoundException;
import org.xplugin.core.install.Config;
import org.xplugin.core.msg.invoker.AbsMsgInvoker;
import org.xplugin.core.msg.invoker.BackgroundMsgInvoker;
import org.xplugin.core.msg.invoker.GetPageInfoMsgInvoker;
import org.xplugin.core.msg.invoker.SyncMsgInvoker;
import org.xplugin.core.msg.invoker.TaskMsgInvoker;
import org.xutils.common.util.DoubleKeyValueMap;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

/**
 * Created by jiaolei on 15/6/10.
 * 插件消息分发器
 */
public final class PluginMsgDispatcher {

    private static final String PLUGIN_ENTRY_CLS_NAME = "PluginEntry";
    private AbsPluginEntry pluginEntry;

    /**
     * key1: cmd
     * key2: receiverCls
     * value: invoker
     */
    private final DoubleKeyValueMap<String, Class<?>, AbsMsgInvoker>
            invokerMap = new DoubleKeyValueMap<String, Class<?>, AbsMsgInvoker>();

    private final Plugin plugin;

    public PluginMsgDispatcher(final Plugin plugin) {
        this.plugin = plugin;
        Config config = plugin.getConfig();
        String pkg = config.getPackageName();
        String ctrlClsName = pkg + "." + PLUGIN_ENTRY_CLS_NAME;
        try {
            Class<?> ctrlCls = null;
            if (plugin instanceof Host) {
                ctrlCls = PluginRuntime.getRuntimeListener().getHostPluginEntry();
            }
            if (ctrlCls == null) {
                ctrlCls = plugin.loadClass(ctrlClsName);
            }
            Constructor<?> constructor = ctrlCls.getConstructor(Plugin.class);
            pluginEntry = (AbsPluginEntry) constructor.newInstance(plugin);
        } catch (ClassNotFoundException ex) {
            LogUtil.w("Not found " + PLUGIN_ENTRY_CLS_NAME + ", using default impl.");
            pluginEntry = new DefaultPluginEntry(plugin);
        } catch (Throwable ex) {
            throw new PluginEntryNotFoundException(ctrlClsName, ex);
        }
    }

    public void onLoaded() {
        pluginEntry.onLoaded();
        resolveInvoker();
    }

    public void onDestroy() {
        pluginEntry.onDestroy();
    }

    public boolean containsCmd(String cmd) {
        return invokerMap.containsKey(cmd);
    }

    /**
     * 消息分发, 所有的消息都在这里进行分发.
     *
     * @param msg
     */
    public boolean dispatchMsg(final PluginMsg msg) {
        Collection<AbsMsgInvoker> msgInvokers = invokerMap.getAllValues(msg.getCmd());
        if (msgInvokers != null && msgInvokers.size() > 0) {
            for (AbsMsgInvoker msgInvoker : msgInvokers) {
                if (msgInvoker != null) {
                    if (msgInvoker instanceof BackgroundMsgInvoker) {
                        try {
                            msgInvoker.invoke(msg.clone());
                        } catch (Throwable ex) {
                            LogUtil.e(ex.getMessage(), ex);
                        }
                    } else {
                        final AbsMsgInvoker finalMsgInvoker = msgInvoker;
                        x.task().autoPost(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    finalMsgInvoker.invoke(msg.clone());
                                } catch (Throwable ex) {
                                    LogUtil.e(ex.getMessage(), ex);
                                }
                            }
                        });
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void resolveInvoker() {
        // register GetPageInfoMsgInvoker
        invokerMap.put(ActivityInfoLoader.PAGE_INFO_CMD,
                pluginEntry.getClass(),
                new GetPageInfoMsgInvoker(plugin, pluginEntry, null));

        // register TaskMsgInvoker
        Map<String, Class<? extends PluginMsgTask>> taskMap = pluginEntry.getRegisteredTaskMap();
        if (taskMap != null) {
            for (Map.Entry<String, Class<? extends PluginMsgTask>> entry : taskMap.entrySet()) {
                AbsMsgInvoker msgInvoker = new TaskMsgInvoker(entry.getValue());
                invokerMap.put(entry.getKey(), pluginEntry.getClass(), msgInvoker);
            }
        }

        // register AsyncMsgInvoker OR SyncMsgInvoker
        registerEvents(pluginEntry);
    }

    /**
     * 注册消息事件
     *
     * @param receiver 注册接收对象的实例, 若实例对象不存在或被销毁, 则事件不会再被调用.
     */
    public void registerEvents(Object receiver) {
        if (receiver == null) {
            throw new IllegalArgumentException("receiver must not be null");
        }
        resolveEvents(receiver, null, true);
    }

    /**
     * 注销消息事件
     *
     * @param receiver
     */
    public void unregisterEvents(Object receiver) {
        if (receiver == null) {
            throw new IllegalArgumentException("receiver must not be null");
        }
        resolveEvents(receiver, null, false);
    }

    /**
     * 注册消息事件
     *
     * @param staticReceiverCls 注册接收对象的类型, 用于支持静态注册.
     */
    public void registerEvents(Class<?> staticReceiverCls) {
        if (staticReceiverCls == null) {
            throw new IllegalArgumentException("staticReceiverCls must not be null");
        }
        resolveEvents(null, staticReceiverCls, true);
    }

    /**
     * 注销消息事件
     *
     * @param staticReceiverCls
     */
    public void unregisterEvents(Class<?> staticReceiverCls) {
        if (staticReceiverCls == null) {
            throw new IllegalArgumentException("staticReceiverCls must not be null");
        }
        try {
            staticReceiverCls.newInstance();
        } catch (Throwable ex) {
            throw new IllegalArgumentException("staticReceiverCls can not create instance", ex);
        }
        resolveEvents(null, staticReceiverCls, false);
    }

    /**
     * 注册消息事件
     *
     * @param receiver          可为null, 注册接收对象的实例, 若实例对象不存在或被销毁, 将尝试使用receiverCls动态创建实例.
     * @param staticReceiverCls 可为null, 注册接收对象的类型, 用于支持静态注册, 实例对象被销毁通过该类型创建新实例.
     */
    private void resolveEvents(Object receiver, Class<?> staticReceiverCls, boolean register) {
        Class<?> receiverClass = receiver == null ? staticReceiverCls : receiver.getClass();
        Method[] declaredMethods = receiverClass.getDeclaredMethods();

        if (declaredMethods == null) return;
        for (Method method : declaredMethods) {
            if (!Modifier.isPrivate(method.getModifiers())) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            // check params
            if (paramTypes != null && paramTypes.length == 1 && PluginMsg.class.equals(paramTypes[0])) {
                // check return type
                if (PluginMsg.class.equals(method.getReturnType())) {

                    PluginMsgEvent ann = method.getAnnotation(PluginMsgEvent.class);
                    if (ann != null) {
                        String cmd = ann.cmd();
                        if (register) {
                            method.setAccessible(true);
                            AbsMsgInvoker msgInvoker = null;
                            if (ann.background()) {
                                msgInvoker = new BackgroundMsgInvoker(plugin, receiver, method);
                            } else {
                                msgInvoker = new SyncMsgInvoker(plugin, receiver, method);
                            }
                            msgInvoker.setStaticReceiverClass(staticReceiverCls);
                            invokerMap.put(cmd, receiverClass, msgInvoker);
                        } else {
                            invokerMap.remove(cmd, receiverClass);
                        }
                    } // ann != null
                }
            }
        } // for
    }

    @Override
    public String toString() {
        return plugin.toString();
    }

    private static class DefaultPluginEntry extends AbsPluginEntry {
        public DefaultPluginEntry(Plugin plugin) {
            super(plugin);
        }

        @Override
        public void onLoaded() {
            LogUtil.d(getPlugin().getConfig().getPackageName() + " onLoaded");
        }

        @Override
        public void onDestroy() {
            LogUtil.d(getPlugin().getConfig().getPackageName() + " onDestroy");
        }
    }
}
