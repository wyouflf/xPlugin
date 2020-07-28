package org.xplugin.core.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.TestLooperManager;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.Toast;

import org.xplugin.core.ctx.HostContextProxy;
import org.xplugin.core.ctx.Module;
import org.xplugin.core.ctx.Plugin;
import org.xplugin.core.util.ReflectMethod;
import org.xplugin.core.util.Reflector;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;

/**
 * Created by jiaolei on 20/6/8.
 * 可在插件中打开的Dialog
 */
/*packaged*/ class PluginInstrumentation extends Instrumentation {

    private final Instrumentation mBase;

    public PluginInstrumentation(Instrumentation base) {
        super();
        try { // init fields
            Field[] fields = base.getClass().getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(this);
                    if (value == null || !Modifier.isFinal(field.getModifiers())) {
                        field.set(this, field.get(base));
                    }
                } catch (Throwable ex) {
                    LogUtil.e(ex.getMessage(), ex);
                }
            }
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }

        mBase = base;

        try { // Android api 26 开始支持此方式.
            Constructor<?> constructor = ActivityMonitor.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ActivityMonitor activityMonitor = new ActivityMonitor() {
                @Override
                public ActivityResult onStartActivity(Intent intent) {
                    try {
                        IntentHelper.redirect2FakeActivity(intent);
                    } catch (Throwable ex) {
                        LogUtil.e(ex.getMessage(), ex);
                    }
                    return null;
                }
            };
            this.addMonitor(activityMonitor);
            mBase.addMonitor(activityMonitor);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unused")
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        try {
            IntentHelper.redirect2FakeActivity(intent);
        } catch (Throwable ex) {
            Log.e("Instrumentation", ex.getMessage(), ex);
            Throwable cause = ex;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            Toast.makeText(target, cause.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
        try {
            ReflectMethod execStartActivityMethod = Reflector.on(Instrumentation.class).method("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class);
            return execStartActivityMethod.callByCaller(mBase, who, contextThread, token, target, intent, requestCode, options);
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }
        return null;
    }

    @SuppressWarnings("unused")
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, String target,
            Intent intent, int requestCode, Bundle options) {
        try {
            IntentHelper.redirect2FakeActivity(intent);
        } catch (Throwable ex) {
            Log.e("Instrumentation", ex.getMessage(), ex);
            Throwable cause = ex;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            Toast.makeText(x.app(), cause.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
        try {
            ReflectMethod execStartActivityMethod =
                    Reflector.on(Instrumentation.class).method("execStartActivity",
                            Context.class, IBinder.class, IBinder.class, String.class,
                            Intent.class, int.class, Bundle.class);
            return execStartActivityMethod.callByCaller(mBase, who, contextThread, token, target, intent, requestCode, options);
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }
        return null;
    }

    @SuppressWarnings("unused")
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, String resultWho,
            Intent intent, int requestCode, Bundle options, UserHandle user) {
        try {
            IntentHelper.redirect2FakeActivity(intent);
        } catch (Throwable ex) {
            Log.e("Instrumentation", ex.getMessage(), ex);
            Throwable cause = ex;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            Toast.makeText(x.app(), cause.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
        try {
            ReflectMethod execStartActivityMethod =
                    Reflector.on(Instrumentation.class).method("execStartActivity",
                            Context.class, IBinder.class, IBinder.class, String.class,
                            Intent.class, int.class, Bundle.class, UserHandle.class);
            return execStartActivityMethod.callByCaller(mBase, who, contextThread, token, resultWho, intent, requestCode, options, user);
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        try {
            beforeCallActivityOnCreate(activity);
            mBase.callActivityOnCreate(activity, icicle);
        } finally {
            afterCallActivityOnCreate(activity);
        }
    }

    @Override
    @SuppressLint("NewApi")
    public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        try {
            beforeCallActivityOnCreate(activity);
            mBase.callActivityOnCreate(activity, icicle, persistentState);
        } finally {
            afterCallActivityOnCreate(activity);
        }
    }

    private void beforeCallActivityOnCreate(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            try {
                // android api 24 以下版本未做verifyClassLoader处理, 需要清理可能来自不同ClassLoader的view构造器.
                Map<?, ?> map = Reflector.on(LayoutInflater.class).field("sConstructorMap").get();
                if (map != null) {
                    map.clear();
                }
            } catch (Throwable ex) {
                LogUtil.e(ex.getMessage(), ex);
            }
        }

        Plugin plugin = Plugin.getPlugin(activity);
        if (plugin instanceof Module) { // module activity
            ActivityHelper.initModuleActivity(activity, plugin);
        } else { // host activity
            ActivityHelper.initHostActivity(activity);
        }

        HostContextProxy.setCallingActivityOnCreate(true);
    }

    private void afterCallActivityOnCreate(Activity activity) {
        HostContextProxy.setCallingActivityOnCreate(false);
    }

    @Override
    public void onCreate(Bundle arguments) {
        mBase.onCreate(arguments);
    }

    @Override
    public void start() {
        mBase.start();
    }

    @Override
    public void onStart() {
        mBase.onStart();
    }

    @Override
    public boolean onException(Object obj, Throwable e) {
        return mBase.onException(obj, e);
    }

    @Override
    public void sendStatus(int resultCode, Bundle results) {
        mBase.sendStatus(resultCode, results);
    }

    @Override
    @SuppressLint("NewApi")
    public void addResults(Bundle results) {
        mBase.addResults(results);
    }

    @Override
    public void finish(int resultCode, Bundle results) {
        mBase.finish(resultCode, results);
    }

    @Override
    public void setAutomaticPerformanceSnapshots() {
        mBase.setAutomaticPerformanceSnapshots();
    }

    @Override
    public void startPerformanceSnapshot() {
        mBase.startPerformanceSnapshot();
    }

    @Override
    public void endPerformanceSnapshot() {
        mBase.endPerformanceSnapshot();
    }

    @Override
    public void onDestroy() {
        mBase.onDestroy();
    }

    @Override
    public Context getContext() {
        return mBase.getContext();
    }

    @Override
    public ComponentName getComponentName() {
        return mBase.getComponentName();
    }

    @Override
    public Context getTargetContext() {
        return mBase.getTargetContext();
    }

    @Override
    @SuppressLint("NewApi")
    public String getProcessName() {
        return mBase.getProcessName();
    }

    @Override
    public boolean isProfiling() {
        return mBase.isProfiling();
    }

    @Override
    public void startProfiling() {
        mBase.startProfiling();
    }

    @Override
    public void stopProfiling() {
        mBase.stopProfiling();
    }

    @Override
    public void setInTouchMode(boolean inTouch) {
        mBase.setInTouchMode(inTouch);
    }

    @Override
    public void waitForIdle(Runnable recipient) {
        mBase.waitForIdle(recipient);
    }

    @Override
    public void waitForIdleSync() {
        mBase.waitForIdleSync();
    }

    @Override
    public void runOnMainSync(Runnable runner) {
        mBase.runOnMainSync(runner);
    }

    @Override
    public Activity startActivitySync(Intent intent) {
        return mBase.startActivitySync(intent);
    }

    @Override
    @SuppressLint("NewApi")
    public Activity startActivitySync(Intent intent, Bundle options) {
        return mBase.startActivitySync(intent, options);
    }

    @Override
    public void addMonitor(ActivityMonitor monitor) {
        mBase.addMonitor(monitor);
    }

    @Override
    public ActivityMonitor addMonitor(IntentFilter filter, ActivityResult result, boolean block) {
        return mBase.addMonitor(filter, result, block);
    }

    @Override
    public ActivityMonitor addMonitor(String cls, ActivityResult result, boolean block) {
        return mBase.addMonitor(cls, result, block);
    }

    @Override
    public boolean checkMonitorHit(ActivityMonitor monitor, int minHits) {
        return mBase.checkMonitorHit(monitor, minHits);
    }

    @Override
    public Activity waitForMonitor(ActivityMonitor monitor) {
        return mBase.waitForMonitor(monitor);
    }

    @Override
    public Activity waitForMonitorWithTimeout(ActivityMonitor monitor, long timeOut) {
        return mBase.waitForMonitorWithTimeout(monitor, timeOut);
    }

    @Override
    public void removeMonitor(ActivityMonitor monitor) {
        mBase.removeMonitor(monitor);
    }

    @Override
    public boolean invokeMenuActionSync(Activity targetActivity, int id, int flag) {
        return mBase.invokeMenuActionSync(targetActivity, id, flag);
    }

    @Override
    public boolean invokeContextMenuAction(Activity targetActivity, int id, int flag) {
        return mBase.invokeContextMenuAction(targetActivity, id, flag);
    }

    @Override
    public void sendStringSync(String text) {
        mBase.sendStringSync(text);
    }

    @Override
    public void sendKeySync(KeyEvent event) {
        mBase.sendKeySync(event);
    }

    @Override
    public void sendKeyDownUpSync(int key) {
        mBase.sendKeyDownUpSync(key);
    }

    @Override
    public void sendCharacterSync(int keyCode) {
        mBase.sendCharacterSync(keyCode);
    }

    @Override
    public void sendPointerSync(MotionEvent event) {
        mBase.sendPointerSync(event);
    }

    @Override
    public void sendTrackballEventSync(MotionEvent event) {
        mBase.sendTrackballEventSync(event);
    }

    @Override
    public Application newApplication(ClassLoader cl, String className, Context context) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return mBase.newApplication(cl, className, context);
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        mBase.callApplicationOnCreate(app);
    }

    @Override
    public Activity newActivity(Class<?> clazz, Context context, IBinder token, Application application, Intent intent, ActivityInfo info, CharSequence title, Activity parent, String id, Object lastNonConfigurationInstance) throws IllegalAccessException, InstantiationException {
        return mBase.newActivity(clazz, context, token, application, intent, info, title, parent, id, lastNonConfigurationInstance);
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (className.startsWith(IntentHelper.ACTIVITY_TPL_PREFIX)) {
            ArrayList<String> targetInfo = intent.getStringArrayListExtra(IntentHelper.INTENT_TARGET_INFO_KEY);
            if (targetInfo != null && targetInfo.size() >= 2) {
                try {
                    intent.setClassName(targetInfo.get(0), targetInfo.get(1));
                    IntentHelper.redirect2FakeActivity(intent);
                } catch (Throwable ex) {
                    LogUtil.e(ex.getMessage(), ex);
                }
            }
        }
        return mBase.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnDestroy(Activity activity) {
        mBase.callActivityOnDestroy(activity);
    }

    @Override
    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState) {
        mBase.callActivityOnRestoreInstanceState(activity, savedInstanceState);
    }

    @Override
    @SuppressLint("NewApi")
    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState, PersistableBundle persistentState) {
        mBase.callActivityOnRestoreInstanceState(activity, savedInstanceState, persistentState);
    }

    @Override
    public void callActivityOnPostCreate(Activity activity, Bundle savedInstanceState) {
        mBase.callActivityOnPostCreate(activity, savedInstanceState);
    }

    @Override
    @SuppressLint("NewApi")
    public void callActivityOnPostCreate(Activity activity, Bundle savedInstanceState, PersistableBundle persistentState) {
        mBase.callActivityOnPostCreate(activity, savedInstanceState, persistentState);
    }

    @Override
    public void callActivityOnNewIntent(Activity activity, Intent intent) {
        mBase.callActivityOnNewIntent(activity, intent);
    }

    @Override
    public void callActivityOnStart(Activity activity) {
        mBase.callActivityOnStart(activity);
    }

    @Override
    public void callActivityOnRestart(Activity activity) {
        mBase.callActivityOnRestart(activity);
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        mBase.callActivityOnResume(activity);
    }

    @Override
    public void callActivityOnStop(Activity activity) {
        mBase.callActivityOnStop(activity);
    }

    @Override
    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState) {
        mBase.callActivityOnSaveInstanceState(activity, outState);
    }

    @Override
    @SuppressLint("NewApi")
    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState, PersistableBundle outPersistentState) {
        mBase.callActivityOnSaveInstanceState(activity, outState, outPersistentState);
    }

    @Override
    public void callActivityOnPause(Activity activity) {
        mBase.callActivityOnPause(activity);
    }

    @Override
    public void callActivityOnUserLeaving(Activity activity) {
        mBase.callActivityOnUserLeaving(activity);
    }

    @Override
    public void startAllocCounting() {
        mBase.startAllocCounting();
    }

    @Override
    public void stopAllocCounting() {
        mBase.stopAllocCounting();
    }

    @Override
    public Bundle getAllocCounts() {
        return mBase.getAllocCounts();
    }

    @Override
    public Bundle getBinderCounts() {
        return mBase.getBinderCounts();
    }

    @Override
    public UiAutomation getUiAutomation() {
        return mBase.getUiAutomation();
    }

    @Override
    @SuppressLint("NewApi")
    public UiAutomation getUiAutomation(int flags) {
        return mBase.getUiAutomation(flags);
    }

    @Override
    @SuppressLint("NewApi")
    public TestLooperManager acquireLooperManager(Looper looper) {
        return mBase.acquireLooperManager(looper);
    }
}