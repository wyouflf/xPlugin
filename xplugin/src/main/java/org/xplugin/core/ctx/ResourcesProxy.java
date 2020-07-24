package org.xplugin.core.ctx;

import android.content.res.Resources;

public class ResourcesProxy extends Resources {

    private boolean forHost = false;
    private final Resources mResources;
    private final String pluginPackage;

    public ResourcesProxy(Resources resources, String pluginPackage) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
        this.mResources = resources;
        this.pluginPackage = pluginPackage;
    }

    public ResourcesProxy cloneForHost() {
        ResourcesProxy result = new ResourcesProxy(mResources, pluginPackage);
        result.forHost = true;
        return result;
    }

    @Override
    public int getIdentifier(String name, String defType, String defPackage) {
        int id = 0;
        if (forHost) {
            id = super.getIdentifier(name, defType, defPackage);
            if (id == 0 && !pluginPackage.equals(defPackage)) {
                id = super.getIdentifier(name, defType, pluginPackage);
            }
        } else {
            id = super.getIdentifier(name, defType, pluginPackage);
            if (id == 0 && !pluginPackage.equals(defPackage)) {
                id = super.getIdentifier(name, defType, defPackage);
            }
        }
        if (id != 0 && (id & 0x80000000) == 0x80000000 && "id".equals(defType)) {
            id = (id & 0x0000FFFF) | 0x7F800000;
        }
        return id;
    }
}
