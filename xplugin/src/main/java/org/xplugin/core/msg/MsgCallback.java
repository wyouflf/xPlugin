package org.xplugin.core.msg;


import org.xutils.common.Callback;

/**
 * 插件消息回调接口
 */
public abstract class MsgCallback implements Callback.ProgressCallback<PluginMsg> {

    @Override
    public void onWaiting() {

    }

    @Override
    public void onStarted() {

    }

    @Override
    public void onLoading(long total, long current, boolean isDownloading) {

    }

    @Override
    public void onCancelled(CancelledException cex) {

    }

    @Override
    public void onFinished() {

    }
}

