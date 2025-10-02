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

            try {
                File logFile = new File(AppVars.getLogsDir(), "ChListJs_original.html");
                try (OutputStream os = new FileOutputStream(logFile)) {
                    os.write(html.getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            html = html.replace("alt=", "title=");
            html = html.replace("target=_blank", "target=\"_blank\"");

            try {
                File logFile = new File(AppVars.getLogsDir(), "ChListJs_modified.html");
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
            // Return an empty script to avoid breaking the page
            return new byte[0];
        }
    }
}