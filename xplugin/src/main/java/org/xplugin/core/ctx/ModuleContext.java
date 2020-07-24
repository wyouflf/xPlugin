package org.xplugin.core.ctx;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;

import org.xplugin.core.install.Config;
import org.xplugin.core.install.Installer;
import org.xplugin.core.util.PluginReflectUtil;
import org.xutils.common.util.LogUtil;
import org.xutils.x;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ModuleContext extends ContextThemeWrapper {

    private final File pluginFile;
    private final ModuleClassLoader classLoader;

    private Resources.Theme theme;
    private Resources resources;
    private AssetManager assetManager;
    private LayoutInflater layoutInflater;
    private Configuration overrideConfiguration;

    private final Object themeLock = new Object();
    private final Object resourcesLock = new Object();
    private final Object assetManagerLock = new Object();
    private final Object layoutInflaterLock = new Object();

    public ModuleContext(File pluginFile, Config config) {
        super(x.app(), 0);
        this.pluginFile = pluginFile;
        this.classLoader = new ModuleClassLoader(pluginFile, Objects.requireNonNull(pluginFile.getParentFile()), config);
    }

    /*package*/ void attachModule(Module module) {
        this.classLoader.attachModule(module);
    }

    @Override
    public Object getSystemService(String name) {
        if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
            if (this.layoutInflater == null) {
                synchronized (this.layoutInflaterLock) {
                    if (this.layoutInflater == null) {
                        this.layoutInflater = LayoutInflater.from(getBaseContext()).cloneInContext(this);
                    }
                }
            }
            return this.layoutInflater;
        } else {
            return super.getSystemService(name);
        }
    }

    @Override
    public Resources.Theme getTheme() {
        if (this.theme == null) {
            synchronized (this.themeLock) {
                if (this.theme == null) {
                    Resources.Theme oldTheme = super.getTheme();
                    this.theme = this.getResources().newTheme();
                    this.theme.setTo(oldTheme);
                }
            }
        }
        return this.theme;
    }

    @Override
    public AssetManager getAssets() {
        if (this.assetManager == null) {
            synchronized (this.assetManagerLock) {
                if (this.assetManager == null) {
                    try {
                        this.assetManager = AssetManager.class.newInstance();
                    } catch (Throwable ex) {
                        throw new RuntimeException("Plugin init failed:", ex);
                    }
                    List<String> assetsPathList = new ArrayList<String>(3);
                    String runtimeModulePath = null;
                    Module runtimeModule = Installer.getRuntimeModule();
                    if (runtimeModule != null && !this.pluginFile.equals(runtimeModule.getPluginFile())) {
                        runtimeModulePath = runtimeModule.getPluginFile().getPath();
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        assetsPathList.add(x.app().getApplicationInfo().sourceDir);
                        assetsPathList.add(this.pluginFile.getAbsolutePath());
                        if (!TextUtils.isEmpty(runtimeModulePath)) {
                            assetsPathList.add(runtimeModulePath);
                        }
                    } else {
                        assetsPathList.add(x.app().getApplicationInfo().sourceDir);
                        if (!TextUtils.isEmpty(runtimeModulePath)) {
                            assetsPathList.add(runtimeModulePath);
                        }
                        assetsPathList.add(this.pluginFile.getAbsolutePath());
                    }

                    String webViewResourcesDir = PluginReflectUtil.getWebViewResourcesDir();
                    if (!TextUtils.isEmpty(webViewResourcesDir)) {
                        assetsPathList.add(webViewResourcesDir);
                    }

                    for (String path : assetsPathList) {
                        int cookie = PluginReflectUtil.addAssetPath(this.assetManager, path);
                        if (cookie == 0) {
                            LogUtil.e("addAssets Failed:" + path + "#" + cookie + "#" + pluginFile.getAbsolutePath());
                        }
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
                    Configuration configuration = this.overrideConfiguration == null ?
                            parent.getConfiguration() : this.overrideConfiguration;
                    Resources res = new Resources(getAssets(), parent.getDisplayMetrics(), configuration);
                    this.resources = new ResourcesProxy(res, classLoader.getModule().getConfig().getPackageName());
                }
            }
        }
        return this.resources;
    }

    @Override
    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        this.overrideConfiguration = new Configuration(overrideConfiguration);
    }

    public File getPluginFile() {
        return pluginFile;
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }
}
