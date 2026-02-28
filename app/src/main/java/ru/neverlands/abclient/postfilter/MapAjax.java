package ru.neverlands.abclient.postfilter;

/**
 * Заглушка пост‑фильтра для map_ajax.php (AJAX карты).
 * В ПК версии используется для обновлений карты; на Android пока не реализовано.
 */
public class MapAjax {
    /**
     * Возвращает исходный ответ без изменений.
     */
    public static String process(String html) {
        return html;
    }
}
