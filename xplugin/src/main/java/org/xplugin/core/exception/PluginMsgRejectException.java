package org.xplugin.core.exception;


import org.xplugin.core.msg.PluginMsg;

public class PluginMsgRejectException extends Exception {

    private PluginMsg msg;
    private Reason reason;

    public PluginMsgRejectException(PluginMsg msg, Reason reason) {
        super("The msg has been rejected: " + String.valueOf(reason));
        this.msg = msg;
        this.reason = reason;
    }

    public PluginMsg getMsg() {
        return msg;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        TIMEOUT, OVERFLOW, NO_MATCH;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PluginMsgRejectException{");
        sb.append("msg=").append(msg);
        sb.append(", reason=").append(reason);
        sb.append('}');
        return sb.toString();
    }
}
