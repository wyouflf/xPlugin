package org.xplugin.core.app;

import org.xplugin.core.exception.PageNotFoundException;
import org.xplugin.core.msg.PluginMsg;

import java.util.HashMap;

public class ActivityInfoLoader {

    // 获取页面信息的cmd
    public final static String PAGE_INFO_CMD = "getPageInfo";
    public final static String PAGE_INFO_KEY = "pageInfo";
    public final static String PAGE_ACTION_KEY = "pageAction";
    public final static String PAGE_CLASS_KEY = "pageClass";

    private final static HashMap<String, Class<?>> TARGET_ACTIVITY_CLASS_CACHE = new HashMap<String, Class<?>>();

    public static Class<?> getTargetClassSync(String targetAction, String targetPackage) throws Throwable {
        String key = targetAction + "#" + targetPackage;
        Class<?> targetClass = TARGET_ACTIVITY_CLASS_CACHE.get(key);
        if (targetClass != null) return targetClass;

        PluginMsg pluginMsg = new PluginMsg(PAGE_INFO_CMD);
        pluginMsg.putInParam(PAGE_ACTION_KEY, targetAction);
        pluginMsg.setTargetPackage(targetPackage);
        PluginMsg result = pluginMsg.sendSync();
        targetClass = (Class<?>) result.getOutParam(PAGE_CLASS_KEY);

        if (targetClass != null) {
            TARGET_ACTIVITY_CLASS_CACHE.put(key, targetClass);
        } else {
            throw new PageNotFoundException(targetAction);
        }

        return targetClass;
    }
}