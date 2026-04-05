package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.DataManager;
import ru.neverlands.abclient.utils.Russian;

/**
 * Пост-фильтр для скрипта ch_list.js.
 */
public class ChListJs {
    /**
     * Обрабатывает скрипт ch_list.js ПОСЛЕ того, как он был изменен в MainActivity.
     * MainActivity уже добавил в начало скрипта мост `window.external` и массив `ChatListU`.
     * Этот метод выполняет финальные строковые замены (цвета ников/клик/target).
     * @param array Массив байт, содержащий ИЗМЕНЕННЫЙ скрипт.
     * @return Финальная версия скрипта для выполнения в WebView.
     */
    public static byte[] process(byte[] array) {
        AppLog.d("ChListJs", "process() called");
        if (array == null || array.length == 0) {
            return array;
        }

        try {
            String html = Russian.getString(array);

            // Препендим Android‑мост и недостающие top.* функции (frameset API).
            html = "window.external = window.AndroidBridge;\n" +
                   "if (typeof top.say_private !== 'function') { top.say_private = function(n){ if(window.external && window.external.chatSayPrivate){ window.external.chatSayPrivate(n); } }; }\n" +
                   "if (typeof top.say_to !== 'function') { top.say_to = function(n){ if(window.external && window.external.chatSayTo){ window.external.chatSayTo(n); } }; }\n" +
                   "if (typeof top.reload !== 'function') { top.reload = function(){ if(window.external && window.external.loadFrame){ window.external.loadFrame('ch_list','http://neverlands.ru/ch.php?lo=1&'+(new Date().getTime())); } }; }\n" +
                   "if (typeof top.save_scroll_p !== 'function') { top.save_scroll_p = function() {}; }\n" +
                   "if (typeof top.OnlineStop === 'undefined') { top.OnlineStop = 0; }\n" +
                   "if (typeof top.OnlineScrollPosition === 'undefined') { top.OnlineScrollPosition = 0; }\n" +
                   html;

            // Вставка для раскраски ников по classId (контакты).
            // Модифицирует str_array[1] на месте.
            String insertion = "    var classid = '0';\n" +
                               "    try {\n" +
                               "        var rawClassId = window.external.GetClassIdOfContact(login);\n" +
                               "        console.log('Raw classId for ' + login + ': ' + rawClassId);\n" +
                               "        classid = String(rawClassId || '0');\n" +
                               "    } catch (e) {\n" +
                               "        console.log('Error getting classId for ' + login + ': ' + e.message);\n" +
                               "    }\n" +
                               "    if (classid == '1') {\n" +
                               "        str_array[1] = \"<font color='#8A0808'>\" + str_array[1] + \"</font>\";\n" +
                               "    } else if (classid == '2') {\n" +
                               "        str_array[1] = \"<font color='#0B610B'>\" + str_array[1] + \"</font>\";\n" +
                               "    }\n";

            // Находим строку `var login = str_array[1];` и вставляем блок сразу после.
            String targetLine = "var login = str_array[1];";
            String replacement = targetLine + "\n" + insertion;
            
            html = html.replace(targetLine, replacement);

            // Прочие замены: alt->title, target='_blank' (для корректного клика в WebView).
            html = html.replace("alt=", "title=");
            html = html.replace("target=\"_blank\"", "target='_blank'");

            // Логируем финальный скрипт для отладки (при необходимости).
            try {
                File logFile = new File(AppVars.getLogsDir(), "ChListJs_final.txt");
                try (OutputStream os = new FileOutputStream(logFile)) {
                    os.write(html.getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            AppLog.d("ChListJs", "Finished processing ch_list.js");
            return Russian.getBytes(html);
        } catch (Exception e) {
            e.printStackTrace();
            AppLog.e("ChListJs", "Error processing ch_list.js", e);
            return new byte[0];
        }
    }
}
