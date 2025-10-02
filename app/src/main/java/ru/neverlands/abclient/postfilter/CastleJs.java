package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class CastleJs {
    public static byte[] process(byte[] array) {
        String js = Russian.getString(array);
        js = js.replace("document.location = goloc;", "window.open(goloc, 'main');");
        return Russian.getBytes(js);
    }
}