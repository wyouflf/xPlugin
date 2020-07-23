package org.xplugin.core.exception;

public class PluginDisableException extends RuntimeException {

    private String packageName;

    public PluginDisableException(String packageName) {
        super("The plugin is disabled: " + packageName);
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }
}