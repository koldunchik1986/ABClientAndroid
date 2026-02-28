package ru.neverlands.abclient.postfilter;

/**
 * Заглушка пост‑фильтра для js/map.js.
 * В ПК версии содержит логику карты; на Android пока не реализовано
 */
public class MapJs {
    /**
     * Возвращает исходный ответ без изменений.
     */
    public static byte[] process(byte[] array) {
        return array;
    }
}
