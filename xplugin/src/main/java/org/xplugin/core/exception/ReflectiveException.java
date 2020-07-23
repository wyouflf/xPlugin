package org.xplugin.core.exception;

public class ReflectiveException extends Exception {

    public ReflectiveException(String message) {
        super(message);
    }

    public ReflectiveException(Throwable cause) {
        super(cause.getMessage(), cause);
    }

}