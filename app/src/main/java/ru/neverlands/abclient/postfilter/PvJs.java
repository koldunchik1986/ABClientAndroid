package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class PvJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        html = html.replace("'%clan% '", "'%clan%'");
        return Russian.getBytes(html);
    }
}