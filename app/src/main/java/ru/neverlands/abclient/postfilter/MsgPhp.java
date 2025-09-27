package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class MsgPhp {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        if (AppVars.Profile != null && AppVars.Profile.ChatKeepGame && AppVars.Chat != null && !AppVars.Chat.isEmpty()) {
            html = html.replace(" id=msg>", " id=msg>" + AppVars.Chat);
        }
        return Russian.getBytes(html);
    }
}