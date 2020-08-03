package org.xplugin.core.ctx;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Window;

import org.xplugin.core.install.Installer;
import org.xplugin.core.util.PluginReflectUtil;
import org.xplugin.core.util.ReflectField;
import org.xplugin.core.util.Reflector;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.WeakHashMap;

public final class HostContextProxy extends ContextThemeWrapper {

    private Module runtimeModule;
    private Configuration configuration;
    private WeakReference<Activity> activityRef;

    private Resources.Theme theme;
    private Resources baseResources;
    private Resources runtimeResources;
    private LayoutInflater layoutInflater;

    private volatile boolean isContentCreated = false;
    private final Object CONTENT_CREATE_LOCK = new Object();

    private static final WeakHashMap<Activity, HostContextProxy> gHostContextProxyMap = new WeakHashMap<Activity, HostContextProxy>(5);

    public HostContextProxy(Activity activity, int themeId, boolean isContentCreated) {
        super(activity.getBaseContext(), themeId);
        this.baseResources = activity.getResources();
        this.configuration = baseResources.getConfiguration();
        this.activityRef = new WeakReference<Activity>(activity);
        this.isContentCreated = isContentCreated;
        synchronized (gHostContextProxyMap) {
            Module runtime = Installer.getRuntimeModule();
            if (runtime != null) {
                onRuntimeModuleLoaded(runtime, false);
            } else {
                Window window = activity.getWindow();
                if (window != null && !isContentCreated) {
                    Window.Callback callback = window.getCallback();
                    if (callback == null) {
                        callback = activity;
                    }
                    Object callbackWrapper = Proxy.newProxyInstance(activity.getClass().getClassLoader(),
                            new Class[]{Window.Callback.class},
                            new WindowCallbackWrapper(callback));
                    window.setCallback((Window.Callback) callbackWrapper);
                }

                gHostContextProxyMap.put(activity, this);
            }
        }
    }

    public static void onRuntimeModuleLoaded(final Module runtime, boolean fromInitCallback) {
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

        synchronized (gHostContextProxyMap) {
            for (HostContextProxy proxy : gHostContextProxyMap.values()) {
                if (proxy != null) {
                    proxy.resolveRuntimeModule(runtime, fromInitCallback);
                }
            }
        }
    }

    private synchronized void resolveRuntimeModule(Module runtime, boolean fromInitCallback) {
        if (this.runtimeModule != null) return;

        if (fromInitCallback) {
            synchronized (CONTENT_CREATE_LOCK) {
                if (!isContentCreated) {
                    try {
                        CONTENT_CREATE_LOCK.wait(1000);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        try {
            runtimeModule = runtime;
            Activity activity = activityRef.get();
            if (activity != null) {
                Resources tempResources = null;
                Context runtimeContext = runtimeModule.getContext();
                if (configuration != null) {
                    Context configurationContext = runtimeContext.createConfigurationContext(configuration);
                    tempResources = configurationContext.getResources();
                } else {
                    tempResources = runtimeContext.getResources();
                }

                if (tempResources instanceof ResourcesProxy) {
                    tempResources = ((ResourcesProxy) tempResources).cloneForHost();
                }

                final Reflector ctxReflector = Reflector.on(ContextThemeWrapper.class).bind(activity);
                ctxReflector.field("mResources").set(tempResources);
                runtimeResources = tempResources;

                x.task().autoPost(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ReflectField mInflaterField = ctxReflector.field("mInflater");
                            LayoutInflater inflater = mInflaterField.get();
                            if (inflater != null) {
                                inflater = inflater.cloneInContext(HostContextProxy.this);
                                mInflaterField.set(inflater);
                            }
                        } catch (Throwable ex) {
                            LogUtil.e(ex.getMessage(), ex);
                        }
                    }
                });
            }
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }
    }

    @Override
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        if (runtimeModule != null) {
            return runtimeModule.getContext().createConfigurationContext(overrideConfiguration);
        } else {
            return super.createConfigurationContext(overrideConfiguration);
        }
    }

    @Override
    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        this.configuration = overrideConfiguration;
    }

    @Override
    public Object getSystemService(String name) {
        if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
            if (this.layoutInflater == null) {
                this.layoutInflater = LayoutInflater.from(getBaseContext()).cloneInContext(this);
            }
            return this.layoutInflater;
        } else {
            return super.getSystemService(name);
        }
    }

    @Override
    public AssetManager getAssets() {
        return this.getResources().getAssets();
    }

    @Override
    public Resources.Theme getTheme() {
        if (this.theme == null) {
            Resources.Theme oldTheme = super.getTheme();
            this.theme = this.getResources().newTheme();
            this.theme.setTo(oldTheme);
        }
        return this.theme;
    }

    @Override
    public Resources getResources() {
        if (runtimeResources != null) {
            return runtimeResources;
        } else {
            return baseResources;
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        return Installer.getHost().getContext().getClassLoader();
    }

    private class WindowCallbackWrapper implements InvocationHandler {
        final Window.Callback mWrapped;

        public WindowCallbackWrapper(Window.Callback wrapped) {
            mWrapped = wrapped;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("onContentChanged".equals(method.getName())) {
                synchronized (CONTENT_CREATE_LOCK) {
                    isContentCreated = true;
                    CONTENT_CREATE_LOCK.notify();
                }
            }
            return method.invoke(mWrapped, args);
        }
    }
}
