package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class NlPinfoJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        html = html.replace(
                "+alt+",
                "+AndroidBridge.InfoToolTip(arr[0],alt)+");
        return Russian.getBytes(html);
    }
}
