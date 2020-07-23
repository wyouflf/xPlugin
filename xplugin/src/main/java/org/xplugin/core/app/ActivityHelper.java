package org.xplugin.core.app;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import org.xplugin.core.ctx.ContextProxy;
import org.xplugin.core.ctx.HostContextProxy;
import org.xplugin.core.ctx.Module;
import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.install.Config;
import org.xplugin.core.install.Installer;
import org.xplugin.core.util.ReflectField;
import org.xplugin.core.util.Reflector;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.util.HashMap;

/**
 * Created by jiaolei on 15/6/10.
 * 页面打开的辅助工具类
 */
public final class ActivityHelper {

    /**
     * key: module anim id
     * value: host anim id
     */
    private final static HashMap<Integer, Integer> overridePendingTransition_AnimId = new HashMap<Integer, Integer>();

    public static void registerOverridePendingTransitionAnimId(int moduleAimId, String hostAnimResName) {
        if (moduleAimId == 0 || TextUtils.isEmpty(hostAnimResName)) return;

        int hostAnimId = x.app().getResources().getIdentifier(hostAnimResName, "anim", x.app().getPackageName());
        overridePendingTransition_AnimId.put(moduleAimId, hostAnimId);
    }

    public static int replaceOverridePendingTransitionAnimId(int moduleAimId) {
        if (moduleAimId == 0) return 0;

        try {
            Integer hostAnimIdObj = overridePendingTransition_AnimId.get(moduleAimId);
            int hostAnimId = hostAnimIdObj == null ? 0 : hostAnimIdObj;
            if (hostAnimId != 0) {
                return hostAnimId;
            } else if ((moduleAimId & 0x80000000) == 0x80000000) {
                Module runtimeModule = Installer.getRuntimeModule();
                if (runtimeModule != null) {
                    String name = runtimeModule.getContext().getResources().getResourceEntryName(moduleAimId);
                    hostAnimId = x.app().getResources().getIdentifier(name, "anim", x.app().getPackageName());
                    if (hostAnimId != 0 && (hostAnimId & 0x80000000) != 0x80000000) {
                        overridePendingTransition_AnimId.put(moduleAimId, hostAnimId);
                        return hostAnimId;
                    }
                }
            } else {
                return moduleAimId;
            }
        } catch (Throwable ignored) {
        }

        return 0;
    }

    /*packaged*/
    static void initHostActivity(Activity activity) {
        Context context = activity.getBaseContext();
        if (!(context instanceof ContextProxy)) {
            try {
                // change base context
                Reflector.on(ContextWrapper.class)
                        .bind(activity)
                        .field("mBase")
                        .set(new HostContextProxy(activity));
            } catch (Throwable ex) {
                LogUtil.e(ex.getMessage(), ex);
            }
        }
    }

    /*packaged*/
    static void initModuleActivity(Activity activity, Plugin plugin) {
        try {
            // change base context
            Reflector.on(ContextWrapper.class)
                    .bind(activity)
                    .field("mBase")
                    .set(new ContextProxy(activity.getBaseContext(), plugin));

            // change mResources
            Reflector.on(ContextThemeWrapper.class)
                    .bind(activity)
                    .field("mResources")
                    .set(plugin.getContext().getResources());

            // change mTheme
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.setTheme(plugin.getContext().getTheme());
            } else {
                Reflector.on(ContextThemeWrapper.class)
                        .bind(activity)
                        .field("mTheme")
                        .set(plugin.getContext().getTheme());
            }

            // change mTitle
            Config config = plugin.getConfig();
            Reflector activityReflector = Reflector.on(Activity.class).bind(activity);
            activityReflector.field("mTitle").set(config.getLabel());

            // change mActivityInfo
            ActivityInfo info = config.findActivityInfoByClassName(activity.getClass().getName());
            if (info != null) {
                if (info.theme != 0 && activity.getParent() == null) {
                    activity.setTheme(info.theme);
                }

                ReflectField mActivityInfoField = activityReflector.field("mActivityInfo");
                ActivityInfo tplInfo = mActivityInfoField.get();
                mActivityInfoField.set(info);

                if (tplInfo == null || tplInfo.screenOrientation != info.screenOrientation) {
                    activity.setRequestedOrientation(info.screenOrientation);
                }

                if (tplInfo == null || tplInfo.softInputMode != info.softInputMode) {
                    activity.getWindow().setSoftInputMode(info.softInputMode);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (tplInfo == null || tplInfo.colorMode != info.colorMode) {
                        activity.getWindow().setColorMode(info.colorMode);
                    }
                }

                if (tplInfo == null || tplInfo.uiOptions != info.uiOptions) {
                    if (info.uiOptions != 0) {
                        activity.getWindow().setUiOptions(info.uiOptions);
                    }
                }
            }
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }
    }
}
