package org.xplugin.core.exception;

public class PluginEntryNotFoundException extends RuntimeException {

    public PluginEntryNotFoundException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}
