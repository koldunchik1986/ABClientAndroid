package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class BuildingJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        if (AppVars.Profile != null && AppVars.Profile.ChatKeepMoving) {
            html = html.replace("parent.clr_chat();", "");
        }
        return Russian.getBytes(html);
    }
}