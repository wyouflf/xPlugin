package org.xplugin.core.exception;

public class PluginConfigException extends RuntimeException {

    private String fileName;

    public PluginConfigException(String fileName, Throwable cause) {
        super("The config read error: " + fileName, cause);
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
