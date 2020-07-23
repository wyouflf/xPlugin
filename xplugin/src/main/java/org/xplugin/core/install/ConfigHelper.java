package org.xplugin.core.install;

import android.app.Application;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.text.TextUtils;

import org.xplugin.core.PluginRuntime;
import org.xplugin.core.PluginRuntimeListener;
import org.xplugin.core.exception.PluginConfigException;
import org.xplugin.core.exception.PluginVerifyException;
import org.xplugin.core.util.PluginReflectUtil;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/**
 * Created by jiaolei on 15/6/15.
 * 配置文件操作工具类
 */
/*package*/ final class ConfigHelper {

    private final static String META_DEPENDENCE = "dependence";

    public static void supplementHostConfig(final Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        Application app = x.app();
        try {
            PackageManager pm = app.getPackageManager();
            PackageInfo pkgInfo = pm.getPackageInfo(app.getPackageName(),
                    PackageManager.GET_META_DATA |
                            PackageManager.GET_ACTIVITIES |
                            PackageManager.GET_SERVICES |
                            PackageManager.GET_PROVIDERS);
            if (pkgInfo != null) {

                // package info
                config.packageName = pkgInfo.packageName;
                config.versionName = pkgInfo.versionName;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    config.version = pkgInfo.getLongVersionCode();
                } else {
                    config.version = pkgInfo.versionCode;
                }

                // application
                ApplicationInfo appInfo = pkgInfo.applicationInfo;
                int appTheme = 0;
                if (appInfo != null) {
                    appTheme = appInfo.theme;
                    if (appInfo.metaData != null) {
                        String depStr = appInfo.metaData.getString(META_DEPENDENCE);
                        if (!TextUtils.isEmpty(depStr)) {
                            String[] depArray = depStr.replaceAll("\\s*", "").split(",");
                            if (depArray != null && depArray.length > 0) {
                                Collections.addAll(config.dependence, depArray);
                            }
                        }
                    }
                }

                // actionMap
                try {
                    XmlResourceParser xmlResourceParser = app.getAssets().openXmlResourceParser("AndroidManifest.xml");
                    Map<String, String> actionMap = ManifestReader.readActionsFromManifest(config.packageName, xmlResourceParser);
                    config.actionMap.putAll(actionMap);
                } catch (Throwable ex) {
                    LogUtil.d("readActionsFromManifest", ex);
                }

                // activities
                if (pkgInfo.activities != null) {
                    for (ActivityInfo info : pkgInfo.activities) {
                        info.theme = info.theme != 0 ? info.theme : appTheme;

                        String className = info.name.startsWith(".") ?
                                info.packageName + info.name : info.name;
                        config.activityMap.put(className, info);
                    }
                }

                // services
                if (pkgInfo.services != null) {
                    for (ServiceInfo info : pkgInfo.services) {

                        String className = info.name.startsWith(".") ?
                                info.packageName + info.name : info.name;
                        config.serviceMap.put(className, info);
                    }
                }

                // providers
                if (pkgInfo.providers != null) {
                    for (ProviderInfo info : pkgInfo.providers) {
                        if (!TextUtils.isEmpty(info.authority)) {
                            config.providerMap.put(info.authority, info);
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            throw new PluginConfigException(app.getPackageCodePath(), ex);
        }
    }

    public static Config getModuleConfig(final File pluginFile, boolean verifyModuleFile) {

        if (verifyModuleFile) { // 验证文件
            PluginRuntimeListener lsn = PluginRuntime.getRuntimeListener();
            if (!lsn.verifyPluginFile(pluginFile)) {
                throw new PluginVerifyException(pluginFile);
            }
        }

        // 读取配置信息
        Config config = null;
        try {
            PackageManager pm = x.app().getPackageManager();
            PackageInfo pkgInfo = pm.getPackageArchiveInfo(pluginFile.getAbsolutePath(),
                    PackageManager.GET_META_DATA |
                            PackageManager.GET_ACTIVITIES |
                            PackageManager.GET_SERVICES |
                            PackageManager.GET_RECEIVERS |
                            PackageManager.GET_PROVIDERS);
            if (pkgInfo != null) {

                // package info
                config = new Config();
                config.packageName = pkgInfo.packageName;
                config.versionName = pkgInfo.versionName;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    config.version = pkgInfo.getLongVersionCode();
                } else {
                    config.version = pkgInfo.versionCode;
                }

                // application
                ApplicationInfo appInfo = pkgInfo.applicationInfo;
                int appTheme = 0;
                int appLabelRes = 0;
                int appIcon = 0;
                if (appInfo != null) {
                    appTheme = appInfo.theme;
                    appLabelRes = appInfo.labelRes;
                    appIcon = appInfo.icon;

                    config.applicationClassName = appInfo.className;

                    if (appInfo.metaData != null) {
                        Config.allModulesMetaData.putAll(appInfo.metaData);
                        String depStr = appInfo.metaData.getString(META_DEPENDENCE);
                        if (!TextUtils.isEmpty(depStr)) {
                            String[] depArray = depStr.replaceAll("\\s*", "").split(",");
                            if (depArray != null && depArray.length > 0) {
                                Collections.addAll(config.dependence, depArray);
                            }
                        }
                    }

                    try {
                        appInfo.sourceDir = pluginFile.getAbsolutePath();
                        appInfo.publicSourceDir = appInfo.sourceDir;
                        CharSequence label = appInfo.loadLabel(pm);
                        config.label = label == null ? "" : label.toString();
                        config.icon = appInfo.loadIcon(pm);
                    } catch (Throwable ex) {
                        LogUtil.d("load icon error", ex);
                    }
                }

                // actionMap
                try {
                    AssetManager assetManager = AssetManager.class.newInstance();
                    int cookie = PluginReflectUtil.addAssetPath(assetManager, pluginFile.getAbsolutePath());
                    XmlResourceParser xmlResourceParser = assetManager.openXmlResourceParser(cookie, "AndroidManifest.xml");
                    Map<String, String> actionMap = ManifestReader.readActionsFromManifest(config.packageName, xmlResourceParser);
                    config.actionMap.putAll(actionMap);
                } catch (Throwable ex) {
                    LogUtil.d("readActionsFromManifest", ex);
                }

                // activities
                if (pkgInfo.activities != null) {
                    for (ActivityInfo info : pkgInfo.activities) {
                        info.theme = info.theme != 0 ? info.theme : appTheme;
                        info.labelRes = info.labelRes != 0 ? info.labelRes : appLabelRes;
                        info.icon = info.icon != 0 ? info.icon : appIcon;

                        String className = info.name.startsWith(".") ?
                                info.packageName + info.name : info.name;
                        config.activityMap.put(className, info);
                    }
                }

                // services
                if (pkgInfo.services != null) {
                    for (ServiceInfo info : pkgInfo.services) {

                        String className = info.name.startsWith(".") ?
                                info.packageName + info.name : info.name;
                        config.serviceMap.put(className, info);
                    }
                }

                // receivers
                if (pkgInfo.receivers != null) {
                    for (ActivityInfo info : pkgInfo.receivers) {
                        String className = info.name.startsWith(".") ? info.packageName + info.name : info.name;
                        ArrayList<String> actions = new ArrayList<String>(1);
                        for (Map.Entry<String, String> entry : config.actionMap.entrySet()) {
                            String value = entry.getValue();
                            if (className.equals(value)) {
                                actions.add(entry.getKey());
                            }
                        }
                        config.receiverMap.put(className, actions);
                    }
                }

                // providers
                if (pkgInfo.providers != null) {
                    for (ProviderInfo info : pkgInfo.providers) {
                        if (!TextUtils.isEmpty(info.authority)) {
                            // fix: targetSdkVersion 较低时 sourceDir 和 publicSourceDir 可能为空
                            info.applicationInfo.sourceDir = pluginFile.getAbsolutePath();
                            info.applicationInfo.publicSourceDir = pluginFile.getAbsolutePath();

                            config.providerMap.put(info.authority, info);

                            if (info.authority.startsWith(pkgInfo.packageName)) {
                                ProviderInfo newInfo = new ProviderInfo(info);
                                newInfo.authority = info.authority.replace(pkgInfo.packageName, info.packageName);
                                config.providerMap.put(newInfo.authority, newInfo);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            throw new PluginConfigException(pluginFile.getName(), ex);
        }

        return config;
    }
}
