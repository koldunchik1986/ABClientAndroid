// ChRoomPhp.java
package ru.neverlands.abclient.postfilter;

import android.util.Log;

import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.utils.Russian;

public class ChRoomPhp {
    public static byte[] process(byte[] array) {
        Log.d("ChRoomPhp", "Input length: " + array.length);
        String originalHtml = Russian.getString(array);

        // 1. Генерируем наш список игроков
        String generatedPlayerList = RoomManager.process(null, originalHtml);

        // 2. Находим контейнер в оригинальном HTML и заменяем его
        String startTag = "<font class=\"placename\">";
        String endTag = "</font>";

        int startIndex = originalHtml.indexOf(startTag);
        int endIndex = originalHtml.indexOf(endTag, startIndex);

        if (startIndex != -1 && endIndex != -1) {
            String before = originalHtml.substring(0, startIndex + startTag.length());
            String after = originalHtml.substring(endIndex);
            originalHtml = before + generatedPlayerList + after;
        } else {
            // Fallback: добавляем в конец body
            originalHtml = originalHtml.replace("</body>", generatedPlayerList + "</body>");
        }

        byte[] result = Russian.getBytes(originalHtml);
        Log.d("ChRoomPhp", "Output length: " + result.length);
        return result;
    }
}