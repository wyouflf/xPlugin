package org.xplugin.core.app;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.text.TextUtils;

import org.xplugin.core.ctx.Module;
import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.install.Installer;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

public class IntentHelper {

    // Manifest注册的Activity模板名称约定
    public final static String ACTIVITY_TPL_PREFIX = "org.xplugin.tpl.";

    // 记录目标Activity
    private final static String TARGET_ACTIVITY_PREF = "TARGET_ACTIVITY_PREF";
    private final static String TARGET_ACTIVITY_PKG_KEY = "TARGET_ACTIVITY_PKG_KEY";
    private final static String TARGET_ACTIVITY_CLASS_KEY = "TARGET_ACTIVITY_CLASS_KEY";

    private static final int ACTIVITY_TPL_MAX_SIZE = 5;
    private static int usedSingleTopStubActivity = 0;
    private static int usedSingleTaskStubActivity = 0;
    private static int usedSingleInstanceStubActivity = 0;

    ///////////////////////////////////////// targetActivityClass
    private static Class<?> targetActivityClass;

    public static Class<?> getTargetActivityClass() throws ClassNotFoundException {
        if (targetActivityClass == null) {
            SharedPreferences pref = x.app().getSharedPreferences(TARGET_ACTIVITY_PREF, 0);
            String targetActivityClassName = pref.getString(TARGET_ACTIVITY_CLASS_KEY, null);
            if (targetActivityClassName != null) {
                String targetActivityPkg = pref.getString(TARGET_ACTIVITY_PKG_KEY, null);
                try {
                    Plugin plugin = Installer.containsModuleActivity(targetActivityPkg, targetActivityClassName);
                    targetActivityClass = plugin.loadClass(targetActivityClassName);
                } catch (Throwable ex) {
                    LogUtil.e(ex.getMessage(), ex);
                    targetActivityClass = Installer.loadClass(targetActivityClassName);
                }
            }
        }
        return targetActivityClass;
    }

    /*packaged*/
    static void setTargetActivityClass(Class<?> targetActivityClass) {
        if (IntentHelper.targetActivityClass != targetActivityClass) {
            IntentHelper.targetActivityClass = targetActivityClass;
            SharedPreferences pref = x.app().getSharedPreferences(TARGET_ACTIVITY_PREF, 0);
            SharedPreferences.Editor editor = pref.edit()
                    .putString(TARGET_ACTIVITY_PKG_KEY, Plugin.getPlugin(targetActivityClass).getConfig().getPackageName())
                    .putString(TARGET_ACTIVITY_CLASS_KEY, targetActivityClass.getName());
            editor.apply();
        }
    }

    //////////////////////////////////// redirect2FakeActivity
    /*packaged*/
    static Class<?> redirect2FakeActivity(Intent origIntent) throws Throwable {
        replaceContentProviderUriIfNeeded(origIntent);

        final String targetAction = origIntent.getAction();
        final String intentPkg = origIntent.getPackage();

        // 替换 intent, 指向 manifest 中注册的模板
        boolean isPluginPage = false;
        String targetPackage = null;
        if (!TextUtils.isEmpty(targetAction)
                && Installer.getInstalledModules().contains(intentPkg)) {
            isPluginPage = true;
            targetPackage = intentPkg;
        }
        Class<?> targetClass = null;
        ComponentName component = origIntent.getComponent();
        if (component != null) {
            String targetClassName = component.getClassName();
            // try load the target class
            if (!Installer.getHost().isActivityRegistered(targetClassName)) {
                try {
                    Plugin plugin = Installer.containsModuleActivity(intentPkg, targetClassName);
                    if (plugin instanceof Module) {
                        isPluginPage = true;
                        targetClass = plugin.loadClass(targetClassName);
                        targetPackage = plugin.getConfig().getPackageName();
                    }
                } catch (Throwable ex) {
                    LogUtil.e(ex.getMessage(), ex);
                }
            }
        }

        if (isPluginPage) {
            // 获取目标类型信息
            if (targetClass != null) {
                setTargetActivityClass(targetClass);
            } else if (!TextUtils.isEmpty(targetAction) && !TextUtils.isEmpty(targetPackage)) {
                try {
                    targetClass = ActivityInfoLoader.getTargetClassSync(targetAction, targetPackage);
                    setTargetActivityClass(targetClass);
                } catch (Throwable ex) {
                    LogUtil.d("not found targetAction in Plugins", ex);
                    isPluginPage = false;
                }
            } else {
                LogUtil.w("missing targetAction or targetPackage");
                isPluginPage = false;
            }

            if (isPluginPage) {
                // redirect intent
                String hostPkg = x.app().getPackageName();
                String targetClassName = targetClass.getName();
                if (Installer.getHost().isActionRegistered(targetAction)
                        || Installer.getHost().isActivityRegistered(targetClassName)) {
                    origIntent.setAction(null);
                    origIntent.setPackage(hostPkg);
                    origIntent.setClassName(hostPkg, targetClassName);
                } else {
                    String activityTpl = ACTIVITY_TPL_PREFIX + "DefaultActivity";
                    Plugin plugin = Plugin.getPlugin(targetClass);
                    ActivityInfo info = plugin.getConfig().findActivityInfoByClassName(targetClassName);
                    if (info != null) {
                        switch (info.launchMode) {
                            case ActivityInfo.LAUNCH_SINGLE_TOP: {
                                usedSingleTopStubActivity = usedSingleTopStubActivity % ACTIVITY_TPL_MAX_SIZE + 1;
                                activityTpl = ACTIVITY_TPL_PREFIX + "SingleTopActivity_" + usedSingleTopStubActivity;
                                break;
                            }
                            case ActivityInfo.LAUNCH_SINGLE_TASK: {
                                usedSingleTaskStubActivity = usedSingleTaskStubActivity % ACTIVITY_TPL_MAX_SIZE + 1;
                                activityTpl = ACTIVITY_TPL_PREFIX + "SingleTaskActivity_" + usedSingleTaskStubActivity;
                                break;
                            }
                            case ActivityInfo.LAUNCH_SINGLE_INSTANCE: {
                                usedSingleInstanceStubActivity = usedSingleInstanceStubActivity % ACTIVITY_TPL_MAX_SIZE + 1;
                                activityTpl = ACTIVITY_TPL_PREFIX + "SingleInstanceActivity_" + usedSingleInstanceStubActivity;
                                break;
                            }
                            default: {
                                break;
                            }
                        }

                        Resources.Theme theme = plugin.getContext().getResources().newTheme();
                        theme.applyStyle(info.theme, true);
                        TypedArray array = theme.obtainStyledAttributes(new int[]{
                                android.R.attr.windowIsTranslucent
                        });
                        boolean windowIsTranslucent = array.getBoolean(0, false);
                        if (windowIsTranslucent) {
                            activityTpl += "_t";
                        }
                    }

                    origIntent.setAction(null);
                    origIntent.setPackage(hostPkg);
                    origIntent.setClassName(hostPkg, activityTpl);
                }
            }

        } else {
            // do nothing, don't change the origIntent.
        }

        return targetClass;
    }

    private static void replaceContentProviderUriIfNeeded(Intent intent) {
        Uri data = intent.getData();
        String type = intent.getType();
        if (data != null && data.getAuthority() != null) {
            String authority = data.getAuthority();
            if (!Installer.getHost().isProviderRegistered(authority)) {
                try {
                    ContentProvider provider = ContentProviderProxy.getRealContentProvider(authority);
                    if (provider != null) {
                        data = new Uri.Builder()
                                .scheme(data.getScheme())
                                .authority(x.app().getPackageName() + ContentProviderProxy.AUTHORITY_SUFFIX)
                                .path(data.getAuthority() + data.getPath())
                                .query(data.getQuery())
                                .fragment(data.getFragment()).build();
                        intent.setDataAndType(data, type);
                    }
                } catch (Throwable ex) {
                    LogUtil.e(ex.getMessage(), ex);
                }
            }
        }
    }
}
