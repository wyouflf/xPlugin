package org.xplugin.core.exception;

public class PluginNeedRestartException extends Exception {

    private String packageName;

    public PluginNeedRestartException(String packageName) {
        super("You need to restart the app before reinstall/reload the plugin: " + packageName);
        this.packageName = packageName;
    }

    public PluginNeedRestartException(String packageName, Throwable cause) {
        super("You need to restart the app before reinstall/reload the plugin: " + packageName, cause);
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }
}
