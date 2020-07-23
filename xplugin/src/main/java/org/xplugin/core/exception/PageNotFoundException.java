package org.xplugin.core.exception;

public class PageNotFoundException extends RuntimeException {

    public PageNotFoundException(String detailMessage) {
        super(detailMessage);
    }

    public PageNotFoundException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
