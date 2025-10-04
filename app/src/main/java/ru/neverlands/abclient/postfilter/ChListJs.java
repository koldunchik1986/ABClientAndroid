// ChListJs.java
package ru.neverlands.abclient.postfilter;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.DataManager;
import ru.neverlands.abclient.utils.Russian;

public class ChListJs {
    private static final String TAG = "ChListJs";

    public static byte[] process(byte[] array) {
        Log.d(TAG, "process() called");
        try {
            // Читаем ch_list.js из assets
            InputStream is = AppVars.getAssetManager().open("js/ch_list.js");
            byte[] fileBytes = DataManager.readAllBytes(is);
            is.close();

            // Декодируем в строку
            String js = Russian.getString(fileBytes);

            // ВАЖНО: Заменяем `alt=` на `title=` для совместимости
            js = js.replace("alt=", "title=");

            // Добавляем мост для Android
            String bridgeScript = "window.external = window.AndroidBridge;";
            js = bridgeScript + js;

            // Логируем для отладки (опционально)
            // ru.neverlands.abclient.utils.DataManager.writeStringToFile("Logs/ch_list_debug.js.txt", js);

            // Кодируем обратно и возвращаем
            return Russian.getBytes(js);

        } catch (IOException e) {
            Log.e(TAG, "Error reading ch_list.js from assets", e);
            // В случае ошибки возвращаем оригинальный массив, чтобы не сломать страницу
            return array;
        }
    }
}