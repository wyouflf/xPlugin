package org.xplugin.core.ctx;

import android.app.Application;

import org.xplugin.core.install.Config;

/**
 * Created by jiaolei on 15/6/10.
 * 宿主
 */
public final class Host extends Plugin {

    public Host(Application app, Config config) {
        super(app, config);
        HostParentClassLoader.init(this);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        Class<?> result = null;
        HostParentClassLoader hostParentClassLoader = HostParentClassLoader.getInstance();
        if (hostParentClassLoader == null) {
            result = this.getClassLoader().loadClass(name);
        } else {
            result = hostParentClassLoader.loadClass(name);
        }
        return result;
    }
}
