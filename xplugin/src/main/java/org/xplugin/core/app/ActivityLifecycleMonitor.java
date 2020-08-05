package org.xplugin.core.app;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.TextUtils;

import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.util.ReflectMethod;
import org.xplugin.core.util.Reflector;
import org.xutils.common.util.LogUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;

public class ActivityLifecycleMonitor implements Application.ActivityLifecycleCallbacks {

    private static ReflectMethod getActivityToken = null;
    private final static LinkedHashMap<Object, WeakReference<Activity>> ACTIVITIES;

    static {
        ACTIVITIES = new LinkedHashMap<Object, WeakReference<Activity>>();
        try {
            getActivityToken = Reflector.on(Activity.class).method("getActivityToken");
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }
    }

    public ActivityLifecycleMonitor() {
    }

    public static boolean finishActivityAffinity(String affinity) {
        if (getActivityToken != null) {
            synchronized (ACTIVITIES) {
                try {
                    ListIterator<Map.Entry<Object, WeakReference<Activity>>> iterator
                            = new ArrayList<Map.Entry<Object, WeakReference<Activity>>>(ACTIVITIES.entrySet()).listIterator(ACTIVITIES.size());
                    while (iterator.hasPrevious()) {
                        Map.Entry<Object, WeakReference<Activity>> entry = iterator.previous();
                        WeakReference<Activity> value = entry.getValue();
                        if (value != null) {
                            Activity activity = value.get();
                            if (activity != null) {
                                while (activity.getParent() != null) {
                                    Activity parent = activity.getParent();
                                    if (parent != null) {
                                        activity = parent;
                                    }
                                }

                                ActivityInfo info = Plugin.getPlugin(activity).getConfig().findActivityInfoByClassName(activity.getClass().getName());
                                if (info != null && TextUtils.equals(info.taskAffinity, affinity)) {
                                    activity.finish();
                                } else {
                                    break;
                                }
                            } else {
                                iterator.remove();
                            }
                        } else {
                            iterator.remove();
                        }
                    }
                } catch (Throwable ex) {
                    LogUtil.e(ex.getMessage(), ex);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        if (getActivityToken != null) {
            synchronized (ACTIVITIES) {
                try {
                    ACTIVITIES.put(getActivityToken.callByCaller(activity), new WeakReference<Activity>(activity));
                } catch (Throwable ex) {
                    LogUtil.e(ex.getMessage(), ex);
                }
            }
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (getActivityToken != null) {
            synchronized (ACTIVITIES) {
                try {
                    ACTIVITIES.remove(getActivityToken.callByCaller(activity));
                } catch (Throwable ex) {
                    LogUtil.e(ex.getMessage(), ex);
                }
            }
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }
}
