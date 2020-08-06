package org.xplugin.core.ctx;

import android.app.Application;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.text.TextUtils;

import org.xplugin.core.app.AndroidApiHook;
import org.xplugin.core.install.Config;
import org.xplugin.core.util.ReflectField;
import org.xplugin.core.util.Reflector;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by jiaolei on 15/6/10.
 * 模块
 */
public final class Module extends Plugin {

    private Application application;
    private List<BroadcastReceiver> registerReceiverList;

    public Module(ModuleContext context, Config config) {
        super(context, config);
        context.attachModule(this);
    }

    public final String findLibrary(String name) {
        return ((ModuleClassLoader) this.getClassLoader()).findLibrary(name);
    }

    @Override
    public void onLoaded() {
        this.registerReceivers();
        this.makeApplication();
        super.onLoaded();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.removeReceivers();
        if (application != null) {
            application.onTerminate();
        }
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return ((ModuleClassLoader) this.getClassLoader()).loadClass(name, true);
    }

    @Override
    public Context getApplicationContext() {
        return application != null ? application : x.app();
    }

    /**
     * 注册广播接收者
     */
    private void registerReceivers() {
        Map<String, ArrayList<String>> map = this.getConfig().getReceiverMap();
        if (map != null && map.size() > 0) {
            registerReceiverList = new ArrayList<BroadcastReceiver>();
            for (Map.Entry<String, ArrayList<String>> entry : map.entrySet()) {
                String clsName = entry.getKey();
                ArrayList<String> actions = entry.getValue();
                if (clsName != null) {
                    try {
                        Class<?> cls = this.loadClass(clsName);
                        BroadcastReceiver receiver = (BroadcastReceiver) cls.newInstance();
                        IntentFilter filter = new IntentFilter();
                        if (actions != null && !actions.isEmpty()) {
                            for (String action : actions) {
                                filter.addAction(action);
                            }
                        }
                        this.getContext().registerReceiver(receiver, filter);
                        registerReceiverList.add(receiver);
                    } catch (Throwable ex) {
                        throw new RuntimeException("register receiver error", ex);
                    }
                }
            }
        }
    }

    private void removeReceivers() {
        if (registerReceiverList != null && registerReceiverList.size() > 0) {
            Context context = this.getContext();
            if (context != null) {
                for (BroadcastReceiver receiver : registerReceiverList) {
                    context.unregisterReceiver(receiver);
                }
            }
        }
    }

    private void makeApplication() {
        final String appClassName = this.getConfig().getApplicationClassName();
        if (!TextUtils.isEmpty(appClassName)) {
            x.task().autoPost(new Runnable() {
                @Override
                public void run() {
                    try {
                        Instrumentation instrumentation = AndroidApiHook.getPluginInstrumentation();
                        application = instrumentation.newApplication(Module.this.getClassLoader(), appClassName, Module.this.getContext());
                        Reflector appReflector = Reflector.on(Application.class);
                        try {
                            ReflectField mComponentCallbacksField = appReflector.field("mComponentCallbacks");
                            mComponentCallbacksField.set(application, mComponentCallbacksField.get(x.app()));
                        } catch (Throwable warn) {
                            LogUtil.w(warn.getMessage(), warn);
                        }
                        try {
                            ReflectField mActivityLifecycleCallbacksField = appReflector.field("mActivityLifecycleCallbacks");
                            mActivityLifecycleCallbacksField.set(application, mActivityLifecycleCallbacksField.get(x.app()));
                        } catch (Throwable warn) {
                            LogUtil.w(warn.getMessage(), warn);
                        }
                        try {
                            ReflectField mAssistCallbacksField = appReflector.field("mAssistCallbacks");
                            mAssistCallbacksField.set(application, mAssistCallbacksField.get(x.app()));
                        } catch (Throwable warn) {
                            LogUtil.w(warn.getMessage(), warn);
                        }
                        instrumentation.callApplicationOnCreate(application);
                    } catch (Throwable ex) {
                        LogUtil.e(ex.getMessage(), ex);
                    }
                }
            });
        }
    }
}
