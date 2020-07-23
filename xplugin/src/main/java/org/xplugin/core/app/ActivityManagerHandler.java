package org.xplugin.core.app;

import android.app.Service;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;

import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.util.Reflector;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/*packaged*/ class ActivityManagerHandler implements InvocationHandler {

    private Object mBase;

    public ActivityManagerHandler(Object base) {
        this.mBase = base;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        try {
            switch (methodName) {
                case "overridePendingTransition":
                    // overridePendingTransition 是跨进程执行的, 必须试用宿主中的资源.
                    /*void overridePendingTransition(in IBinder token, in String packageName, int enterAnim, int exitAnim);*/
                    args[2] = ActivityHelper.replaceOverridePendingTransitionAnimId((int) args[2]);
                    args[3] = ActivityHelper.replaceOverridePendingTransitionAnimId((int) args[3]);
                    return method.invoke(mBase, args);
                case "startService": {
                    /*ComponentName startService(in IApplicationThread caller, in Intent service,
                    in String resolvedType, boolean requireForeground, in String callingPackage, int userId);*/
                    Intent service = (Intent) args[1];
                    Service svc = ServicesProxy.findModuleService(service);
                    if (svc != null) {
                        Plugin plugin = Plugin.getPlugin(svc);
                        return ServicesProxy.startServiceWithProxy(plugin.getContext(), service, svc);
                    }
                    break;
                }
                case "bindService":
                case "bindIsolatedService": {
                    /*int bindService(in IApplicationThread caller, in IBinder token, in Intent service,
                    in String resolvedType, in IServiceConnection connection, int flags,
                    in String callingPackage, int userId);*/
                    /*int bindIsolatedService(in IApplicationThread caller, in IBinder token, in Intent service,
                    in String resolvedType, in IServiceConnection connection, int flags,
                    in String instanceName, in String callingPackage, int userId);*/
                    Intent service = (Intent) args[2];
                    int flags = (int) args[5];
                    Service svc = ServicesProxy.findModuleService(service);
                    if (svc != null) {
                        Plugin plugin = Plugin.getPlugin(svc);
                        //conn = args[4].mDispatcher.get().mConnection
                        Object dispatcher = Reflector.with(args[4]).field("mDispatcher").method("get").call();
                        if (dispatcher != null) {
                            ServiceConnection conn = Reflector.with(dispatcher).field("mConnection").get();
                            if (conn != null) {
                                if (ServicesProxy.bindServiceWithProxy(plugin.getContext(), service, svc, conn, flags)) {
                                    return 1;
                                }
                            }
                        }
                        return 0;
                    }
                    break;
                }
                case "unbindService":
                    /*boolean unbindService(in IServiceConnection connection);*/
                    //conn = args[0].mDispatcher.get().mConnection
                    Object dispatcher = Reflector.with(args[0]).field("mDispatcher").method("get").call();
                    if (dispatcher != null) {
                        ServiceConnection conn = Reflector.with(dispatcher).field("mConnection").get();
                        if (conn != null) {
                            return ServicesProxy.unbindServiceWithProxy(conn);
                        }
                    }
                    break;
                case "stopService": {
                    /*int stopService(in IApplicationThread caller, in Intent service,
                    in String resolvedType, int userId);*/
                    Intent service = (Intent) args[1];
                    if (ServicesProxy.stopServiceWithProxy(service)) {
                        return 1;
                    }
                    break;
                }
                default: {
                    break;
                }
            }
        } catch (Throwable ex) {
            Log.e(ActivityManagerHandler.class.getSimpleName(), methodName + ":" + ex.getMessage(), ex);
        }

        return method.invoke(mBase, args);
    }
}