package org.xplugin.core.app;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;

import org.xplugin.core.ctx.Module;
import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.install.Config;
import org.xplugin.core.install.Installer;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/*packaged*/ class PackageManagerHandler implements InvocationHandler {

    private Object mBase;

    public PackageManagerHandler(Object base) {
        this.mBase = base;
    }

    @Override
    public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
        String methodName = method.getName();
        try {
            switch (methodName) {
                case "getActivityInfo": {
                    /*ActivityInfo getActivityInfo(in ComponentName className, int flags, int userId);*/
                    ComponentName component = (ComponentName) args[0];
                    Intent intent = new Intent();
                    intent.setPackage(component.getPackageName());
                    intent.setComponent(component);
                    Class<?> targetClass = IntentHelper.redirect2FakeActivity(intent);
                    if (targetClass != null) {
                        args[0] = intent.getComponent();
                        ActivityInfo info = (ActivityInfo) method.invoke(mBase, args);
                        if (info != null) {
                            resolveActivityInfo(targetClass, info);
                        }
                        return info;
                    }
                    break;
                }
                case "resolveIntent": {
                    /*ResolveInfo resolveIntent(in Intent intent, String resolvedType, int flags, int userId);*/
                    Intent intent = new Intent((Intent) args[0]);
                    Class<?> targetClass = IntentHelper.redirect2FakeActivity(intent);
                    if (targetClass != null) {
                        args[0] = intent;
                        ResolveInfo resolveInfo = (ResolveInfo) method.invoke(mBase, args);
                        if (resolveInfo != null && resolveInfo.activityInfo != null) {
                            ActivityInfo info = resolveInfo.activityInfo;
                            resolveActivityInfo(targetClass, info);
                        }
                        return resolveInfo;
                    }
                    break;
                }
                case "getApplicationInfo": {
                    /*ApplicationInfo getApplicationInfo(String packageName, int flags ,int userId);*/
                    ApplicationInfo info = (ApplicationInfo) method.invoke(mBase, args);
                    if (info != null) {
                        if (info.metaData == null) {
                            info.metaData = new Bundle();
                        }
                        info.metaData.putAll(Config.getAllModulesMetaData());
                    }
                    return info;
                }
                case "getPackageInfo":
                    /*PackageInfo getPackageInfo(String packageName, int flags, int userId);*/
                    PackageInfo packageInfo = (PackageInfo) method.invoke(mBase, args);
                    if (packageInfo != null && packageInfo.applicationInfo != null) {
                        ApplicationInfo info = packageInfo.applicationInfo;
                        if (info.metaData == null) {
                            info.metaData = new Bundle();
                        }
                        info.metaData.putAll(Config.getAllModulesMetaData());
                    }
                    return packageInfo;
                case "resolveContentProvider": {
                    /*ProviderInfo resolveContentProvider(String name, int flags, int userId);*/
                    ProviderInfo info = (ProviderInfo) method.invoke(mBase, args);
                    if (info == null) {
                        String authority = String.valueOf(args[0]);
                        Installer.waitForInit();
                        info = findProviderInfoFromLoadedModules(authority, false);
                        if (info == null) {
                            info = findProviderInfoFromLoadedModules(authority, true);
                        }
                    }
                    return info;
                }
                default: {
                    break;
                }
            }
        } catch (Throwable ex) {
            LogUtil.e(methodName + ":" + ex.getMessage(), ex);
        }

        return method.invoke(mBase, args);
    }

    private static ProviderInfo findProviderInfoFromLoadedModules(String authority, boolean replacePkg) {
        String hostPkg = x.app().getPackageName();
        replacePkg = replacePkg && authority.startsWith(hostPkg);
        Map<String, Module> modules = Installer.getLoadedModules();
        for (Module module : modules.values()) {
            String currAuthority = authority;
            if (replacePkg) {
                currAuthority = authority.replace(hostPkg, module.getConfig().getPackageName());
            }
            ProviderInfo info = module.getConfig().findProviderInfoByAuthority(currAuthority);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    // 实际作用不大, 主要考虑到可能存在的兼容问题.
    private static void resolveActivityInfo(Class<?> targetClass, ActivityInfo info) {
        info.name = targetClass.getName();
        info.targetActivity = info.name;
        Plugin plugin = Plugin.getPlugin(targetClass);
        ActivityInfo realInfo = plugin.getConfig().findActivityInfoByClassName(info.name);
        if (realInfo != null) {
            info.flags = realInfo.flags;
            info.launchMode = realInfo.launchMode;
            info.configChanges = realInfo.configChanges;
            info.taskAffinity = realInfo.taskAffinity;
            info.uiOptions = realInfo.uiOptions;
            info.softInputMode = realInfo.softInputMode;
            info.screenOrientation = realInfo.screenOrientation;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                info.colorMode = realInfo.colorMode;
            }
        }
    }
}