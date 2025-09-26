package ru.neverlands.abclient.postfilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import ru.neverlands.abclient.ABClientApplication;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

/**
 * Класс для обработки содержимого файла /arena (arena.js).
 * Аналог ArenaJs.cs в оригинальном приложении.
 */
public class ArenaJs {

    /**
     * Обрабатывает JavaScript-содержимое arena.js.
     * В оригинальной C# версии этот метод полностью игнорирует входной массив байт
     * и вместо этого возвращает содержимое статического ресурса arena_v04.js,
     * возможно, с модификациями в зависимости от настроек пользователя.
     * @return Содержимое файла arena_v04.js из assets в виде массива байт, возможно, модифицированное.
     */
    public static byte[] process() { // Метод не принимает array, как в C# версии
        String html = "";
        try {
            InputStream is = ABClientApplication.getAppContext().getAssets().open("arena_v04.js");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            html = Russian.getString(buffer.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            // В случае ошибки чтения из assets, возвращаем пустую строку.
            html = "";
        }

        // Применяем модификации, если AppVars.Profile.ChatKeepMoving активно
        if (AppVars.Profile != null && AppVars.Profile.ChatKeepMoving) {
            html = html.replace("top.clr_chat();", "");
        }

        return Russian.getBytes(html);
    }
}
