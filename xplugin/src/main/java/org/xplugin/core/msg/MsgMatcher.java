package org.xplugin.core.msg;


import org.xplugin.core.ctx.Plugin;

/**
 * 发送消息时, 只有未指定targetPackage时, 才调用这个接口.
 * <p>
 * Created by jiaolei on 15/6/10.
 */
public interface MsgMatcher {
    /**
     * 只有未指定targetPackage时, 才调用这个方法.
     *
     * @param plugin
     * @return 是否要将消息发送给这个插件
     */
    boolean match(Plugin plugin);
}
