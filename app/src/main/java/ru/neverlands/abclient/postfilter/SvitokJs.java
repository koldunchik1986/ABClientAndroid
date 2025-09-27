package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class SvitokJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        html = html.replace(
            "<input type=submit value=\"выполнить\" class=lbut>",
            "<input type=submit value=\"выполнить\" class=lbut onclick=\"window.AndroidBridge.TraceDrinkPotion(fornickname.value, wnametxt)\">");
        return Russian.getBytes(html);
    }
}
