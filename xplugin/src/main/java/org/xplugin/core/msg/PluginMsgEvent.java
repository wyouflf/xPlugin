package org.xplugin.core.msg;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by jiaolei on 15/6/11.
 * 消息事件, 带有此注解的方法将被注册给插件消息分发器.
 * 注意: 方法必须private修饰.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginMsgEvent {

    /**
     * 消息cmd
     */
    String cmd();


    /**
     * 是否在后台线程调用此方法
     */
    boolean background() default false;
}
