package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class HpJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        // Original JS code that builds the HP/MP string
        String original = "s.substring(0, s.lastIndexOf(':')+1) + \"[<font color=#bb0000><b>\" + Math.round(curHP)+\"</b>/<b>\"+maxHP+\"</b></font> | <font color=#336699><b>\"+Math.round(curMA)+\"</b>/<b>\"+maxMA+\"</b></font>]\"";

        // Replace it with a call to our native Android interface
        String replacement = "AndroidBridge.showHpMaTimers(s, curHP, maxHP, intHP, curMA, maxMA, intMA)";

        html = html.replace(original, replacement);

        return Russian.getBytes(html);
    }
}