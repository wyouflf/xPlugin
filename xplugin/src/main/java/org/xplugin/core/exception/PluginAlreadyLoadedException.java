package org.xplugin.core.exception;

public class PluginAlreadyLoadedException extends RuntimeException {

    private String packageName;
    private long version;

    public PluginAlreadyLoadedException(String packageName, long version) {
        super("The plugin is already loaded: " + packageName);
        this.packageName = packageName;
        this.version = version;
    }

    public String getPackageName() {
        return packageName;
    }

    public long getVersion() {
        return version;
    }
}
