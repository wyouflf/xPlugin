package org.xplugin.core.install;

import android.content.res.XmlResourceParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*packaged*/ class ManifestReader {

    private static final String NAME_SPACE_ANDROID = "http://schemas.android.com/apk/res/android";
    private static final Set<String> ACTION_CONTAINER = new HashSet<String>(3);

    static {
        ACTION_CONTAINER.add("activity");
        ACTION_CONTAINER.add("service");
        ACTION_CONTAINER.add("receiver");
    }

    public static Map<String, String> readActionsFromManifest(String pkg, XmlResourceParser parser) throws Throwable {
        Map<String, String> result = new HashMap<String, String>();
        String className = null;
        int event = parser.getEventType();
        while (event != XmlResourceParser.END_DOCUMENT) {
            switch (event) {
                case XmlResourceParser.START_DOCUMENT:
                    break;
                case XmlResourceParser.START_TAG:
                    String startTag = parser.getName();
                    if (ACTION_CONTAINER.contains(startTag)) {
                        className = parser.getAttributeValue(NAME_SPACE_ANDROID, "name");
                        if (className.startsWith(".")) {
                            className = pkg + className;
                        }
                    }
                    if (className != null) {
                        if ("action".equals(startTag)) {
                            String action = parser.getAttributeValue(NAME_SPACE_ANDROID, "name");
                            result.put(action, className);
                        }
                    }
                    break;
                case XmlResourceParser.END_TAG:
                    String endTag = parser.getName();
                    if (ACTION_CONTAINER.contains(endTag)) {
                        className = null;
                    }
                    break;
            }
            event = parser.next();
        }

        return result;
    }


}
