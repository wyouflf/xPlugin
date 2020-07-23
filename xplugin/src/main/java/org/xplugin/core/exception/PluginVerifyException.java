package org.xplugin.core.exception;

import java.io.File;

public class PluginVerifyException extends RuntimeException {

    private File file;

    public PluginVerifyException(File file) {
        super("verify error: " + file);
        this.file = file;
    }

    public File getFile() {
        return file;
    }
}
