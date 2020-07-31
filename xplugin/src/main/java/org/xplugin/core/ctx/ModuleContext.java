package org.xplugin.core.ctx;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import org.xplugin.core.install.Config;
import org.xplugin.core.install.Installer;
import org.xplugin.core.util.PluginReflectUtil;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

public final class ModuleContext extends ContextWrapper {

    private final Config config;
    private final File pluginFile;
    private final ModuleClassLoader classLoader;

    private ResourcesProxy resources;
    private AssetManager assetManager;
    private final Object resourcesLock = new Object();
    private final Object assetManagerLock = new Object();

    // for Configuration Context
    private Configuration overrideConfiguration;
    private final HashMap<Configuration, ModuleContext> mConfigurationContextMap = new HashMap<Configuration, ModuleContext>(5);

    public ModuleContext(File pluginFile, Config config) {
        super(x.app());
        this.config = config;
        this.pluginFile = pluginFile;
        this.classLoader = new ModuleClassLoader(pluginFile, Objects.requireNonNull(pluginFile.getParentFile()), config);
    }

    private ModuleContext(File pluginFile, ModuleClassLoader classLoader) {
        super(x.app());
        this.pluginFile = pluginFile;
        this.classLoader = classLoader;
        this.config = classLoader.getConfig();
    }

    /*package*/ void attachModule(Module module) {
        this.classLoader.attachModule(module);
    }

    @Override
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        if (overrideConfiguration == null) return this;
        ModuleContext result = mConfigurationContextMap.get(overrideConfiguration);
        if (result == null) {
            result = new ModuleContext(pluginFile, classLoader);
            result.assetManager = assetManager;
            result.overrideConfiguration = overrideConfiguration;
            this.mConfigurationContextMap.put(overrideConfiguration, result);
        }
        return result;
    }

    @Override
    public AssetManager getAssets() {
        if (this.assetManager == null) {
            synchronized (this.assetManagerLock) {
                if (this.assetManager == null) {
                    ArrayList<String> splitSourceDirs = new ArrayList<String>();
                    // for self & runtimeModule
                    String runtimeModulePath = null;
                    Module runtimeModule = Installer.getRuntimeModule();
                    if (runtimeModule != null && !this.pluginFile.equals(runtimeModule.getPluginFile())) {
                        runtimeModulePath = runtimeModule.getPluginFile().getPath();
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        splitSourceDirs.add(this.pluginFile.getAbsolutePath());
                        if (!TextUtils.isEmpty(runtimeModulePath)) {
                            splitSourceDirs.add(runtimeModulePath);
                        }
                    } else {
                        if (!TextUtils.isEmpty(runtimeModulePath)) {
                            splitSourceDirs.add(runtimeModulePath);
                        }
                        splitSourceDirs.add(this.pluginFile.getAbsolutePath());
                    }

                    // for WebView
                    String webViewResourcesDir = PluginReflectUtil.getWebViewResourcesDir();
                    if (!TextUtils.isEmpty(webViewResourcesDir)) {
                        splitSourceDirs.add(webViewResourcesDir);
                    }

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            ApplicationInfo applicationInfo = config.getApplicationInfo();
                            applicationInfo.sourceDir = x.app().getApplicationInfo().sourceDir;
                            applicationInfo.publicSourceDir = applicationInfo.sourceDir;
                            if (applicationInfo.splitSourceDirs != null) {
                                splitSourceDirs.addAll(Arrays.asList(applicationInfo.splitSourceDirs));
                            }
                            applicationInfo.splitSourceDirs = splitSourceDirs.toArray(new String[0]);
                            applicationInfo.splitPublicSourceDirs = applicationInfo.splitSourceDirs;
                            PackageManager pm = x.app().getPackageManager();
                            Resources appResources = pm.getResourcesForApplication(applicationInfo);
                            this.assetManager = appResources.getAssets();
                        } else {
                            this.assetManager = AssetManager.class.newInstance();
                            PluginReflectUtil.addAssetPath(this.assetManager, x.app().getApplicationInfo().sourceDir);
                            for (String path : splitSourceDirs) {
                                int cookie = PluginReflectUtil.addAssetPath(this.assetManager, path);
                                if (cookie == 0) {
                                    LogUtil.e("addAssets Failed:" + path + "#" + cookie + "#" + pluginFile.getAbsolutePath());
                                }
                            }
                        }
                    } catch (Throwable ex) {
                        LogUtil.e(ex.getMessage(), ex);
                    }
                }
            }
        }
        return this.assetManager;
    }

    @Override
    public Resources getResources() {
        if (this.resources == null) {
            synchronized (this.resourcesLock) {
                if (this.resources == null) {
                    // Resources parent = super.getResources(); 不能这样用, 中兴部分机型(P188T51)会陷入死循环.
                    Resources parent = x.app().getResources();
                    DisplayMetrics metrics = parent.getDisplayMetrics();
                    Configuration configuration =
                            overrideConfiguration == null ? parent.getConfiguration() : overrideConfiguration;
                    Resources base = new Resources(getAssets(), metrics, configuration);
                    this.resources = new ResourcesProxy(base, config.getPackageName());
                }
            }
        }
        return this.resources;
    }

    public File getPluginFile() {
        return pluginFile;
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }
}
