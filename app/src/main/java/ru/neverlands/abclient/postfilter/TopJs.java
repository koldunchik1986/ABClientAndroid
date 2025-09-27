package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class TopJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        int posone = html.indexOf("()");
        if (posone != -1) {
            posone += "()".length();
            html = html.substring(0, posone) + "{ return ''; }";
        }
        return Russian.getBytes(html);
    }
}