package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class PvJs {
    public static byte[] process(byte[] array) {
        String js = Russian.getString(array);
        js = js.replace("'%clan% '", "'%clan%'");
        return Russian.getBytes(js);
    }
}
