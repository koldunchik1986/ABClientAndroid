package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

/**
 * Класс для обработки содержимого файла /js/hp.js.
 * Аналог HpJs.cs в оригинальном приложении.
 */
public class HpJs {

    /**
     * Обрабатывает JavaScript-содержимое hp.js.
     * Заменяет стандартную логику отображения HP/MA на вызов метода в window.external,
     * что позволяет нативному Android-коду перехватывать и обрабатывать эти данные.
     * @param array Массив байт, содержащий JavaScript-содержимое hp.js.
     * @return Обработанный массив байт.
     */
    public static byte[] process(byte[] array) {
        // Преобразуем массив байт в строку, используя кодировку Windows-1251
        String html = Russian.getString(array);

        // Заменяем стандартную строку форматирования HP/MA на вызов window.external
        // Это позволяет Android-приложению получать данные о HP/MA и отображать их по-своему.
        html = html.replace(
                "s.substring(0, s.lastIndexOf(':')+1) + \"[<font color=#bb0000><b>\" + Math.round(curHP)+\"</b>/<b>\"+maxHP+\"</b></font> | <font color=#336699><b>\"+Math.round(curMA)+\"</b>/<b>\"+maxMA+\"</b></font>]\"",
                "window.external.ShowHpMaTimers(s,curHP,maxHP,intHP,curMA,maxMA,intMA)");

        // Преобразуем измененную строку обратно в массив байт в кодировке Windows-1251
        return Russian.getBytes(html);
    }
}
