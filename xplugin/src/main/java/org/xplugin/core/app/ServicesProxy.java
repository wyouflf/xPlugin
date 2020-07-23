package org.xplugin.core.app;

import android.app.Application;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import org.xplugin.core.ctx.Module;
import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.install.Installer;
import org.xplugin.core.util.PluginReflectUtil;
import org.xplugin.core.util.ReflectField;
import org.xplugin.core.util.ReflectMethod;
import org.xplugin.core.util.Reflector;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件代理
 * <p>
 * 限制:
 * 1. 插件只能打开自身包含的service.
 * 2. 插件中的service和插件运行在同一进程.
 * 3. 插件中的service不能被外部调用.
 */
public class ServicesProxy extends Service {

    private static volatile boolean isRunning = false;
    private static final Object SVC_LOCK = new Object();

    private static final ConcurrentHashMap<String, Service> SVC_MAP = new ConcurrentHashMap<String, Service>(3);
    private static final ConcurrentHashMap<String, Intent> BIND_INTENT_MAP = new ConcurrentHashMap<String, Intent>(3);
    private static final WeakHashMap<ServiceConnection, String> CONN_MAP = new WeakHashMap<ServiceConnection, String>(3);

    public static Service findModuleService(Intent service) {
        if (service == null) return null;

        Service result = null;
        ComponentName componentName = service.getComponent();
        if (componentName != null) {
            try {
                String intentPkg = service.getPackage();
                String className = componentName.getClassName();
                if (!Installer.getHost().isServiceRegistered(className)) {
                    Plugin plugin = Installer.containsModuleService(intentPkg, className);
                    if (plugin instanceof Module) {
                        synchronized (SVC_LOCK) {
                            result = SVC_MAP.get(className);
                            if (result == null) {
                                Class<?> svcCls = null;
                                try {
                                    svcCls = plugin.loadClass(className);
                                } catch (Throwable ignored) {
                                    svcCls = Installer.loadClass(className);
                                }
                                result = (Service) svcCls.newInstance();
                            }
                        }
                    }
                }
            } catch (Throwable ex) {
                LogUtil.e(ex.getMessage(), ex);
            }
        }
        return result;
    }

    public static ComponentName startServiceWithProxy(Context context, Intent service, Service svcInstance) {
        startServiceProxyIfNeeded(context);
        return createNewService(context, service, svcInstance);
    }

    public static boolean bindServiceWithProxy(Context context, Intent service, Service svcInstance, ServiceConnection conn, int flags) {
        if (context == null || service == null || svcInstance == null || conn == null) return false;

        startServiceProxyIfNeeded(context);
        createNewService(context, service, svcInstance);

        synchronized (SVC_LOCK) {
            ComponentName componentName = service.getComponent();
            String svcCls = Objects.requireNonNull(componentName).getClassName();
            Service svc = SVC_MAP.get(svcCls);
            if (svc != null) {
                BIND_INTENT_MAP.put(svcCls, service);
                IBinder binder = svc.onBind(service);
                CONN_MAP.put(conn, svcCls);
                if (binder != null) {
                    conn.onServiceConnected(componentName, binder);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    conn.onNullBinding(componentName);
                }
                return true;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                conn.onBindingDied(componentName);
            }
        }

        return false;
    }

    public static boolean unbindServiceWithProxy(ServiceConnection conn) {
        if (conn == null) return false;

        synchronized (SVC_LOCK) {
            String svcCls = CONN_MAP.get(conn);
            if (svcCls != null) {
                Service svc = SVC_MAP.get(svcCls);
                if (svc != null) {
                    try {
                        CONN_MAP.remove(conn);
                        Intent bindIntent = BIND_INTENT_MAP.remove(svcCls);
                        svc.onUnbind(bindIntent);
                    } finally {
                        Plugin plugin = Plugin.getPlugin(svcCls);
                        conn.onServiceDisconnected(new ComponentName(plugin.getContext(), svcCls));
                    }
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean stopServiceWithProxy(Intent service) {
        if (service == null) return false;

        ComponentName componentName = service.getComponent();
        if (componentName != null) {
            try {
                String intentPkg = service.getPackage();
                String className = componentName.getClassName();
                if (!Installer.getHost().isServiceRegistered(className)) {
                    Plugin plugin = Installer.containsModuleService(intentPkg, className);
                    if (plugin instanceof Module) {
                        synchronized (SVC_LOCK) {
                            Service svc = SVC_MAP.get(className);
                            if (svc != null) {

                                try { // remove binding conn
                                    ServiceConnection remove = null;
                                    for (Map.Entry<ServiceConnection, String> entry : CONN_MAP.entrySet()) {
                                        ServiceConnection conn = entry.getKey();
                                        String svcCls = entry.getValue();
                                        if (className.equals(svcCls) && conn != null) {
                                            remove = conn;
                                            break;
                                        }
                                    }
                                    if (remove != null) {
                                        CONN_MAP.remove(remove);
                                        remove.onServiceDisconnected(componentName);
                                    }
                                } catch (Throwable ex) {
                                    LogUtil.e(ex.getMessage(), ex);
                                }

                                // destroy svc instance
                                SVC_MAP.remove(className);
                                svc.onDestroy();
                                return true;
                            }
                        }
                    }
                }
            } catch (Throwable ex) {
                LogUtil.e(ex.getMessage(), ex);
            }
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
        synchronized (SVC_LOCK) {
            for (Service service : SVC_MAP.values()) {
                service.onDestroy();
            }
        }
    }

    private static void startServiceProxyIfNeeded(Context context) {
        if (!isRunning) {
            synchronized (SVC_LOCK) {
                if (isRunning) {
                    Intent intent = new Intent(x.app(), ServicesProxy.class);
                    context.startService(intent);
                    isRunning = true;
                }
            }
        }
    }

    private static ComponentName createNewService(Context context, Intent service, Service svcInstance) {
        synchronized (SVC_LOCK) {
            if (SVC_MAP.containsValue(svcInstance)) {
                return service.getComponent();
            }
            try {
                { // attach
                    Reflector activityThreadReflector = Reflector.on("android.app.ActivityThread");
                    Object activityThreadObj = activityThreadReflector.method("currentActivityThread").call();
                    activityThreadReflector.bind(activityThreadObj);

                    Object atmSingletonObj = PluginReflectUtil.getAmsSingletonObject(false);
                    Reflector singletonReflector = Reflector.on("android.util.Singleton").bind(atmSingletonObj);
                    ReflectField mInstanceField = singletonReflector.field("mInstance");
                    Object amsObj = mInstanceField.get();
                    if (amsObj == null) {
                        amsObj = singletonReflector.method("get").call();
                    }

                    ReflectMethod attachMethod = Reflector.with(svcInstance).method("attach",
                            Context.class, activityThreadReflector.getType(), String.class, IBinder.class, Application.class, Object.class);
                    attachMethod.call(context, activityThreadObj, ServicesProxy.class.getName(), new Binder(), x.app(), amsObj);
                }

                svcInstance.onCreate();
                svcInstance.onStartCommand(service, Service.START_FLAG_REDELIVERY, 0);
                SVC_MAP.put(svcInstance.getClass().getName(), svcInstance);
                return service.getComponent();
            } catch (Throwable ex) {
                LogUtil.e(ex.getMessage(), ex);
                return null;
            }
        }
    }
}
