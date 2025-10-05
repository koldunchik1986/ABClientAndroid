package ru.neverlands.abclient.postfilter;

import android.util.Log;

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
     * Этот метод выполняет финальные строковые замены.
     * @param array Массив байт, содержащий ИЗМЕНЕННЫЙ скрипт.
     * @return Финальная версия скрипта для выполнения в WebView.
     */
    public static byte[] process(byte[] array) {
        Log.d("ChListJs", "process() called");
        if (array == null || array.length == 0) {
            return array;
        }

        try {
            String html = Russian.getString(array);

            // Prepend the bridge script
            html = "window.external = window.AndroidBridge;\n" + html;

            // This is the block of code we want to insert.
            // It modifies str_array[1] in place.
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

            // We find the line where the `login` variable is set, and insert our code right after it.
            String targetLine = "var login = str_array[1];";
            String replacement = targetLine + "\n" + insertion;
            
            html = html.replace(targetLine, replacement);

            // Other original replacements
            html = html.replace("alt=", "title=");
            html = html.replace("target=\"_blank\"", "target='_blank'");

            // Log the final script
            try {
                File logFile = new File(AppVars.getLogsDir(), "ChListJs_final.txt");
                try (OutputStream os = new FileOutputStream(logFile)) {
                    os.write(html.getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            Log.d("ChListJs", "Finished processing ch_list.js");
            return Russian.getBytes(html);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("ChListJs", "Error processing ch_list.js", e);
            return new byte[0];
        }
    }
}