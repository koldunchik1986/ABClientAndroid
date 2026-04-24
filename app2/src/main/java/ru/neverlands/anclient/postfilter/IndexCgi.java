package ru.neverlands.anclient.postfilter;

/**
 * Заглушка пост‑фильтра для index.cgi (вход/главная).
 * В ПК версии может быть логика редиректов/авторизации; на Android пока не требуется.
 */
public class IndexCgi {
    /**
     * Возвращает исходный ответ без изменений.
     */
    public static byte[] process(byte[] array) {
        return array;
    }
}
