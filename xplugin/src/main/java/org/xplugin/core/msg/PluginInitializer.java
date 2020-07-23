package org.xplugin.core.msg;

import org.xplugin.core.install.Installer;

public final class PluginInitializer {
    private static PluginInitializer instance;

    private PluginInitializer() {
    }

    public static void init() {
        if (instance == null) {
            synchronized (PluginInitializer.class) {
                if (instance == null) {
                    instance = new PluginInitializer();
                    PluginMsgLooper looper = new PluginMsgLooper().start();
                    Installer.initHost(looper);
                }
            }
        }
    }
}
