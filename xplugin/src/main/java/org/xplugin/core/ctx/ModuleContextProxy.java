package org.xplugin.core.ctx;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;

/**
 * ContextWrapper for Activity or AlertDialog
 */
public class ModuleContextProxy extends ContextThemeWrapper {

    private final Module module;

    private Resources.Theme theme;
    private Configuration configuration;
    private LayoutInflater layoutInflater;
    private final Object themeLock = new Object();
    private final Object layoutInflaterLock = new Object();

    public ModuleContextProxy(Activity activity, Module module) {
        super(activity.getBaseContext(), 0);
        this.module = module;
        this.configuration = activity.getResources().getConfiguration();
    }

    @Override
    public Resources.Theme getTheme() {
        if (this.theme == null) {
            synchronized (this.themeLock) {
                if (this.theme == null) {
                    Resources.Theme oldTheme = getBaseContext().getTheme();
                    this.theme = this.getResources().newTheme();
                    this.theme.setTo(oldTheme);
                }
            }
        }
        return this.theme;
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
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        return module.getContext().createConfigurationContext(overrideConfiguration);
    }

    @Override
    public AssetManager getAssets() {
        return getResources().getAssets();
    }

    @Override
    public Resources getResources() {
        Resources result = null;
        Context context = module.getContext();
        if (configuration != null) {
            Context configurationContext = context.createConfigurationContext(configuration);
            result = configurationContext.getResources();
        } else {
            result = context.getResources();
        }

        return result;
    }

    @Override
    public ClassLoader getClassLoader() {
        return module.getContext().getClassLoader();
    }
}
