package org.xplugin.core.util;

import org.xplugin.core.exception.ReflectiveException;
import org.xutils.common.util.LogUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class ReflectField {

    private final Reflector mReflector;
    private final Field mField;
    private final boolean isStatic;

    /*packaged*/ ReflectField(Reflector reflector, Field field) {
        this.mReflector = reflector;
        this.mField = field;
        this.mField.setAccessible(true);
        this.isStatic = Modifier.isStatic(mField.getModifiers());
    }

    public Reflector getReflector() {
        return mReflector;
    }

    public ReflectField bind(Object caller) {
        mReflector.bind(caller);
        return this;
    }

    public <R> R get() throws ReflectiveException {
        return get(mReflector.getCaller());
    }

    @SuppressWarnings("unchecked")
    public <R> R get(Object caller) throws ReflectiveException {
        try {
            return (R) mField.get(isStatic ? null : caller);
        } catch (Throwable ex) {
            throw new ReflectiveException(ex);
        }
    }

    public void set(Object value) throws ReflectiveException {
        set(mReflector.getCaller(), value);
    }

    public void set(Object caller, Object value) throws ReflectiveException {
        try {
            mField.set(isStatic ? null : caller, value);
        } catch (Throwable ex) {
            throw new ReflectiveException(ex);
        }
    }

    public ReflectField field(String name) throws ReflectiveException {
        ReflectField child = Reflector.on(mField.getType()).field(name);
        try {
            child.bind(this.get());
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }
        return child;
    }

    public ReflectMethod method(String name, Class<?>... parameterTypes) throws ReflectiveException {
        ReflectMethod child = Reflector.on(mField.getType()).method(name, parameterTypes);
        try {
            child.bind(this.get());
        } catch (Throwable ex) {
            LogUtil.e(ex.getMessage(), ex);
        }
        return child;
    }
}
