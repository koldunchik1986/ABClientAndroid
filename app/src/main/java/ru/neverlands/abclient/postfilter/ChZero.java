package ru.neverlands.abclient.postfilter;

/**
 * Заглушка пост‑фильтра для ch/zero.php.
 * В ПК версии используется для специальных обновлений чата; на Android пока не реализовано.
 */
public class ChZero {
    /**
     * Возвращает исходный ответ без изменений.
     */
    public static byte[] process(byte[] array) {
        return array;
    }
}
