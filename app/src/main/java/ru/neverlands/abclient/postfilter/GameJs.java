package ru.neverlands.abclient.postfilter;

import android.util.Log;
import ru.neverlands.abclient.utils.Russian;

/**
 * Пост-фильтр для скрипта game.js.
 * Модифицирует параметры чата и добавляет функции для авто-арены.
 * Портировано из GameJs.cs.
 */
public class GameJs {
    /**
     * Обрабатывает скрипт game.js.
     * @param array Массив байт с исходным JS-кодом.
     * @return Модифицированный массив байт.
     */
    public static byte[] process(byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        try {
            String html = Russian.getString(array);

            // Увеличиваем высоту фреймов (в C# дважды, мы тоже сделаем дважды для верности, если в коде два вхождения)
            html = html.replace("*,300", "*,400");
            
            // Внедряем логику авто-арены
            String oldClearSize = "var ChatClearSize = 12228;";
            String newLogic = "var ChatClearSize=12228;\n" +
                              "var AutoArena = 1;\n" +
                              "var AutoArenaTimer = -1;\n" +
                              "function arenareload(now) {\n" +
                              "  if(!AutoArena && (AutoArenaTimer < 0 || now)) {\n" +
                              "    var tm = now ? 1000 : 500;\n" +
                              "    AutoArenaTimer = setTimeout('toprefresh('+now+')', tm);\n" +
                              "  }\n" +
                              "}\n" +
                              "function toprefresh(now){\n" +
                              "  if(AutoArenaTimer >= 0) {\n" +
                              "    clearTimeout(AutoArenaTimer);\n" +
                              "    if(!AutoArena) AutoArenaTimer = setTimeout ('toprefresh(0)', 500);\n" +
                              "    else AutoArenaTimer = -1;\n" +
                              "  }\n" +
                              "  if(!AutoArena || now) top.frames['main_top'].location = './main.php';\n" +
                              "}\n";
            
            html = html.replace(oldClearSize, newLogic);

            return Russian.getBytes(html);
        } catch (Exception e) {
            Log.e("GameJs", "Error processing game script", e);
            return array;
        }
    }
}
