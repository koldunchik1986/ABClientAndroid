package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

/**
 * Класс для обработки содержимого файла /js/building (building.js).
 * Аналог BuildingJs.cs в оригинальном приложении.
 */
public class BuildingJs {

    /**
     * Обрабатывает JavaScript-содержимое building.js.
     * Выполняет условную модификацию JavaScript-кода в зависимости от настроек пользователя.
     * @param array Массив байт, содержащий JavaScript-содержимое building.js.
     * @return Обработанный массив байт.
     */
    public static byte[] process(byte[] array) {
        // Преобразуем массив байт в строку, используя кодировку Windows-1251
        String html = Russian.getString(array);

        // Применяем модификации, если AppVars.Profile.ChatKeepMoving активно
        // Это отключает очистку чата на страницах, связанных со зданиями.
        if (AppVars.Profile != null && AppVars.Profile.ChatKeepMoving) {
            html = html.replace("parent.clr_chat();", "");
        }

        // Преобразуем измененную строку обратно в массив байт в кодировке Windows-1251
        return Russian.getBytes(html);
    }
}
