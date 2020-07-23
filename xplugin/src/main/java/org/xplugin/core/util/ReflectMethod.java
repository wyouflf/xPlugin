package org.xplugin.core.util;

import org.xplugin.core.exception.ReflectiveException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class ReflectMethod {

    private final Reflector mReflector;
    private final Method mMethod;
    private final boolean isStatic;

    /*packaged*/ ReflectMethod(Reflector reflector, Method method) {
        this.mReflector = reflector;
        this.mMethod = method;
        this.mMethod.setAccessible(true);
        this.isStatic = Modifier.isStatic(mMethod.getModifiers());
    }

    public Reflector getReflector() {
        return mReflector;
    }

    public ReflectMethod bind(Object caller) {
        mReflector.bind(caller);
        return this;
    }

    public <R> R call(Object... args) throws ReflectiveException {
        return callByCaller(mReflector.getCaller(), args);
    }

    @SuppressWarnings("unchecked")
    public <R> R callByCaller(Object caller, Object... args) throws ReflectiveException {
        try {
            return (R) mMethod.invoke(isStatic ? null : caller, args);
        } catch (InvocationTargetException ex) {
            throw new ReflectiveException(ex.getTargetException());
        } catch (Throwable ex) {
            throw new ReflectiveException(ex);
        }
    }
}
