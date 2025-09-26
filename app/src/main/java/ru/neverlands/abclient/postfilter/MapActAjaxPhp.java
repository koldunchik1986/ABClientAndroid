package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

/**
 * Класс для обработки содержимого файла gameplay/ajax/map_act_ajax.php.
 * Аналог MapActAjaxPhp.cs в оригинальном приложении.
 */
public class MapActAjaxPhp {

    /**
     * Обрабатывает HTML-содержимое map_act_ajax.php.
     * В оригинальной C# версии этот метод преобразует байты в строку, но не выполняет никаких модификаций,
     * возвращая исходный массив байт.
     * @param array Массив байт, содержащий HTML-содержимое map_act_ajax.php.
     * @return Обработанный массив байт (в текущей версии - без изменений).
     */
    public static byte[] process(byte[] array) {
        // Преобразуем массив байт в строку, используя кодировку Windows-1251
        // В оригинальной C# версии здесь происходит преобразование, но без дальнейших модификаций.
        String html = Russian.getString(array);

        // Возвращаем исходный массив байт, так как в C# версии модификации не выполняются.
        return array;
    }
}
