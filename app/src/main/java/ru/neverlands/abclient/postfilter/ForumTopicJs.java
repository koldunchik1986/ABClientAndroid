package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class ForumTopicJs {
    public static byte[] process(byte[] array) {
        if (AppVars.Profile == null || !AppVars.Profile.isLightForum()) {
            return array;
        }

        String js = Russian.getString(array);

        js = js.replace(
            "<br><img src=\"http://image.neverlands.ru/forum/avatars/\'+fdata[10]+\".jpg\" width=\"80\" height=\"80\" border=\"0\" vspace=\"3\">",
            ""
        );

        js = js.replace(
            "<br><img src=\"http://image.neverlands.ru/forum/avatars/\'+fdata[i][6]+\".jpg\" width=\"80\" height=\"80\" border=\"0\" vspace=\"3\">",
            ""
        );

        return Russian.getBytes(js);
    }
}
