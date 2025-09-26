package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class TopJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        int pos = html.indexOf("()");
        if (pos != -1) {
            pos += "()".length();
            html = html.substring(0, pos) + "{ return ''; }";
        }
        return Russian.getBytes(html);
    }
}
