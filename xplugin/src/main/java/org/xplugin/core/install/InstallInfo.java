package org.xplugin.core.install;

import org.xutils.common.util.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jiaolei on 15/6/15.
 * 插件安装信息
 */
/*package*/ final class InstallInfo {
    public final State state;
    public final String pluginDirName;

    public InstallInfo(State state, String pluginDirName) {
        this.state = state;
        this.pluginDirName = pluginDirName;
    }

    public InstallInfo(String convertStr) {
        String[] lines = convertStr.split("\n");
        if (lines.length < 2) {
            throw new IllegalArgumentException("convertStr format error");
        }
        Map<String, String> infoMap = new HashMap<String, String>(2);
        for (String line : lines) {
            String[] kv = line.split("=");
            if (kv.length == 2) {
                infoMap.put(kv[0].trim(), kv[1].trim());
            }
        }
        state = State.customValueOf(infoMap.get("state"));
        pluginDirName = infoMap.get("pluginDirName");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("state=").append(String.valueOf(state.value)).append("\n");
        sb.append("pluginDirName=").append(pluginDirName);
        return sb.toString();
    }

    /*package*/ enum State {
        ENABLE(0), DISABLE(1);
        private int value;

        private State(int value) {
            this.value = value;
        }

        static State customValueOf(String value) {
            if (value == null || value.length() != 1) {
                return DISABLE;
            }

            if (value.charAt(0) == '0') {
                return ENABLE;
            }
            return DISABLE;
        }

        public int getValue() {
            return value;
        }
    }

    public static InstallInfo readInstallInfo(File infoFile) throws IOException {
        InstallInfo result = null;
        InputStream in = null;
        try {
            in = new FileInputStream(infoFile);
            String convertStr = IOUtil.readStr(in).trim();
            result = new InstallInfo(convertStr);
        } finally {
            IOUtil.closeQuietly(in);
        }

        return result;
    }

    public static void writeInstallInfo(InstallInfo installInfo, File infoFile) throws IOException {
        OutputStream out = null;
        try {
            out = new FileOutputStream(infoFile);
            IOUtil.writeStr(out, installInfo.toString());
        } finally {
            IOUtil.closeQuietly(out);
        }
    }
}
