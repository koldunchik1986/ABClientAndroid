package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppLog;
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
            // Серверо-ориентированный режим:
            // 1) не форсируем reload main_top из game.js;
            // 2) оставляем фактические переходы серверному потоку;
            // 3) логируем попытки client-side reload в bridge для диагностики.
            //
            // Зависимости:
            // - WebAppInterface.traceMainTopReload(...): logcat-маркер client-side reload;
            // - WebAppInterface.loadFrame(...): единая точка маршрутизации фреймов.
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
                              "  if(window.AndroidBridge && AndroidBridge.traceMainTopReload){\n" +
                              "    AndroidBridge.traceMainTopReload('game_js_toprefresh', String(now));\n" +
                              "  }\n" +
                              "}\n";
            
            boolean patchApplied = html.contains(oldClearSize);
            html = html.replace(oldClearSize, newLogic);
            AppLog.d("GameJs", "[MAIN_TOP_TRACE] game.js patch applied=" + patchApplied
                    + ", suppressClientMainTopReload=true");

            return Russian.getBytes(html);
        } catch (Exception e) {
            AppLog.e("GameJs", "Error processing game script", e);
            return array;
        }
    }
}
