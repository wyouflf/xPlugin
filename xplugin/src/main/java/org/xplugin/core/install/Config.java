package org.xplugin.core.install;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by jiaolei on 15/6/15.
 * 注册在AndroidManifest.xml中的插件配置信息
 */
public final class Config {

    // { from AndroidManifest.xml
    /*package*/ String label;
    /*package*/ String packageName;
    /*package*/ String applicationClassName;
    /*package*/ long version;
    /*package*/ String versionName;
    /*package*/ Drawable icon;
    /*package*/ ApplicationInfo applicationInfo;
    /*package*/ final Set<String> dependence;
    /*package*/ final Map<String, String> actionMap;       // <action, className>
    /*package*/ final Map<String, ActivityInfo> activityMap;   // <className, info>
    /*package*/ final Map<String, ServiceInfo> serviceMap;   // <className, info>
    /*package*/ final Map<String, ArrayList<String>> receiverMap;   // <className, actionList>
    /*package*/ final Map<String, ProviderInfo> providerMap;   // <authority, info>
    /*package*/ final static Bundle allModulesMetaData = new Bundle();
    // } from AndroidManifest.xml

    /*package*/ Config() {
        dependence = new LinkedHashSet<String>(5);
        actionMap = new HashMap<String, String>(5);
        activityMap = new HashMap<String, ActivityInfo>(5);
        serviceMap = new HashMap<String, ServiceInfo>(5);
        receiverMap = new HashMap<String, ArrayList<String>>(5);
        providerMap = new HashMap<String, ProviderInfo>(5);
    }

    public String getLabel() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getApplicationClassName() {
        return applicationClassName;
    }

    public long getVersion() {
        return version;
    }

    public String getVersionName() {
        return versionName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public ApplicationInfo getApplicationInfo() {
        return new ApplicationInfo(applicationInfo);
    }

    public Set<String> getDependence() {
        return new LinkedHashSet<String>(dependence);
    }

    public String findClassNameByAction(String action) {
        return actionMap.get(action);
    }

    public ActivityInfo findActivityInfoByClassName(String className) {
        return activityMap.get(className);
    }

    public ServiceInfo findServiceInfoByClassName(String className) {
        return serviceMap.get(className);
    }

    public ProviderInfo findProviderInfoByAuthority(String authority) {
        return providerMap.get(authority);
    }

    public List<ProviderInfo> getProviderList() {
        if (providerMap.isEmpty()) return null;
        List<ProviderInfo> list = new ArrayList<ProviderInfo>(providerMap.size());
        list.addAll(providerMap.values());
        return list;
    }

    /**
     * key: className
     * value: actionList
     */
    public Map<String, ArrayList<String>> getReceiverMap() {
        return new HashMap<String, ArrayList<String>>(receiverMap);
    }

    public static Bundle getAllModulesMetaData() {
        return allModulesMetaData;
    }

    @Override
    public String toString() {
        return packageName;
    }
}
