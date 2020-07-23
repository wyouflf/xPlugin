package org.xplugin.core.util;

import org.xplugin.core.exception.ReflectiveException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class Reflector {

    private Object mCaller;
    private Class<?> mType;
    private Constructor<?> mConstructor;

    private Reflector() {
    }

    public static Reflector on(String name) throws ClassNotFoundException {
        return on(name, Reflector.class.getClassLoader());
    }

    public static Reflector on(String name, ClassLoader loader) throws ClassNotFoundException {
        return on(loader != null ? loader.loadClass(name) : Class.forName(name));
    }

    public static Reflector on(Class<?> type) {
        Reflector reflector = new Reflector();
        reflector.mType = type;
        return reflector;
    }

    public static Reflector with(Object caller) {
        return on(caller.getClass()).bind(caller);
    }

    public Reflector constructor(Class<?>... parameterTypes) throws NoSuchMethodException {
        mConstructor = mType.getDeclaredConstructor(parameterTypes);
        mConstructor.setAccessible(true);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <R> R newInstance(Object... initArgs) throws ReflectiveException {
        if (mConstructor == null) {
            throw new ReflectiveException("Constructor was null!");
        }
        try {
            mCaller = mConstructor.newInstance(initArgs);
            return (R) mCaller;
        } catch (InvocationTargetException ex) {
            throw new ReflectiveException(ex.getTargetException());
        } catch (Throwable ex) {
            throw new ReflectiveException(ex);
        }
    }

    public Reflector bind(Object caller) {
        mCaller = caller;
        return this;
    }

    public Class<?> getType() {
        return mType;
    }

    public Object getCaller() {
        return mCaller;
    }

    public ReflectField field(String name) throws ReflectiveException {
        Field field = null;
        NoSuchFieldException cause = null;
        try {
            field = mType.getField(name);
        } catch (NoSuchFieldException ex) {
            cause = ex;
            for (Class<?> cls = mType; cls != null; cls = cls.getSuperclass()) {
                try {
                    field = cls.getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        if (field != null) {
            return new ReflectField(this, field);
        } else {
            throw new ReflectiveException(cause);
        }
    }

    public ReflectMethod method(String name, Class<?>... parameterTypes) throws ReflectiveException {
        Method method = null;
        NoSuchMethodException cause = null;
        try {
            method = mType.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ex) {
            cause = ex;
            for (Class<?> cls = mType; cls != null; cls = cls.getSuperclass()) {
                try {
                    method = cls.getDeclaredMethod(name, parameterTypes);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        if (method != null) {
            return new ReflectMethod(this, method);
        } else {
            throw new ReflectiveException(cause);
        }
    }
}
