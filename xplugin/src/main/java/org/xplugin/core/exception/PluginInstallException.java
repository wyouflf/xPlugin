package org.xplugin.core.exception;

import java.util.ArrayList;
import java.util.List;

public class PluginInstallException extends Exception {

    private List<String> packageNameList;
    private final List<Throwable> exList = new ArrayList<Throwable>();

    public PluginInstallException(String message, Throwable cause) {
        super(message);
        this.addEx(cause);
    }

    public PluginInstallException(String message, Throwable cause, String packageName) {
        super(message + ": " + packageName);
        this.addEx(cause);
        if (packageName != null) {
            packageNameList = new ArrayList<String>(1);
            packageNameList.add(packageName);
        }
    }

    @Override
    public Throwable getCause() {
        return exList.size() > 0 ? exList.get(0) : null;
    }

    public void addPackageName(String packageName) {
        if (packageName != null) {
            if (packageNameList == null) {
                packageNameList = new ArrayList<String>(1);
            }
            packageNameList.add(packageName);
        }
    }

    public void addEx(Throwable ex) {
        if (ex != null) {
            exList.add(ex);
        }
    }

    public List<String> getPackageNameList() {
        return packageNameList;
    }

    public int packageNameListCount() {
        return packageNameList == null ? 0 : packageNameList.size();
    }

    public List<Throwable> getExList() {
        return exList;
    }

    public int exListCount() {
        return exList.size();
    }
}
