package org.xplugin.core.ctx;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;

import org.xplugin.core.install.Installer;
import org.xplugin.core.util.PluginReflectUtil;
import org.xplugin.core.util.ReflectField;
import org.xplugin.core.util.Reflector;
import org.xutils.common.task.AbsTask;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public final class HostContextProxy extends ContextProxy {

    private static final WeakHashMap<Activity, HostContextProxy> gHostContextProxyMap = new WeakHashMap<Activity, HostContextProxy>(5);

    private Module runtimeModule;
    private WeakReference<Activity> activityRef;

    public HostContextProxy(Activity activity) {
        super(activity.getBaseContext(), null);
        activityRef = new WeakReference<Activity>(activity);
        gHostContextProxyMap.put(activity, this);
        resolveRuntimeModule(Installer.getRuntimeModule(), true);
    }

    private static volatile boolean callingActivityOnCreate = false;
    private static final Object callingActivityOnCreateLock = new Object();

    public static void setCallingActivityOnCreate(boolean callingActivityOnCreate) {
        synchronized (callingActivityOnCreateLock) {
            HostContextProxy.callingActivityOnCreate = callingActivityOnCreate;
            if (!callingActivityOnCreate) {
                callingActivityOnCreateLock.notifyAll();
            }
        }
    }

    public static void onRuntimeModuleLoaded(final Module runtime) {
        x.task().start(new AbsTask<Void>() {
            @Override
            protected Void doBackground() throws Throwable {
                synchronized (callingActivityOnCreateLock) {
                    while (callingActivityOnCreate) {
                        callingActivityOnCreateLock.wait();
                    }
                }
                return null;
            }

            @Override
            protected void onSuccess(Void result) {
                try {
                    Application application = x.app();
                    AssetManager oldAssets = application.getAssets();
                    PluginReflectUtil.addAssetPath(oldAssets, runtime.getPluginFile().getAbsolutePath());
                    Context base = application.getBaseContext();
                    Resources runtimeResources = runtime.getContext().getResources();
                    if (runtimeResources instanceof ResourcesProxy) {
                        runtimeResources = ((ResourcesProxy) runtimeResources).cloneForHost();
                    }
                    Reflector.with(base).field("mResources").set(runtimeResources);
                } catch (Throwable ex) {
                    LogUtil.w(ex.getMessage(), ex);
                }

                for (HostContextProxy proxy : gHostContextProxyMap.values()) {
                    if (proxy != null) {
                        proxy.resolveRuntimeModule(runtime, false);
                    }
                }
            }

            @Override
            protected void onError(Throwable ex, boolean isCallbackError) {
            }
        });
    }

    @Override
    public Plugin getPlugin() {
        return runtimeModule != null ? runtimeModule : Installer.getHost();
    }

    private synchronized void resolveRuntimeModule(Module runtime, boolean fromConstructor) {
        if (runtimeModule != null) return;

        runtimeModule = runtime;
        if (runtimeModule != null && !fromConstructor) {
            try {
                Activity activity = activityRef.get();
                if (activity != null) {
                    Reflector ctxReflector = Reflector.on(ContextThemeWrapper.class).bind(activity);
                    Resources runtimeResources = runtimeModule.getContext().getResources();
                    if (runtimeResources instanceof ResourcesProxy) {
                        runtimeResources = ((ResourcesProxy) runtimeResources).cloneForHost();
                    }
                    ctxReflector.field("mResources").set(runtimeResources);

                    ReflectField mInflaterField = ctxReflector.field("mInflater");
                    LayoutInflater inflater = mInflaterField.get();
                    if (inflater != null) {
                        inflater = inflater.cloneInContext(this);
                        mInflaterField.set(inflater);
                    }
                }
            } catch (Throwable ex) {
                LogUtil.e(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        return Installer.getHost().getContext().getClassLoader();
    }
}
