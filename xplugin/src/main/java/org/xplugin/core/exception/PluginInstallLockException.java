package org.xplugin.core.exception;

/**
 * Created by jiaolei on 15/6/27.
 * 其他进程正在进行安装
 */
public class PluginInstallLockException extends RuntimeException {
    public PluginInstallLockException() {
        super();
    }
}
