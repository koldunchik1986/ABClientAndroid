package ru.neverlands.abclient.postfilter;

/**
 * Заглушка пост‑фильтра для ch/msg.php (окно сообщений чата).
 * В ПК версии здесь может быть обработка HTML; на Android пока не требуется.
 */
public class MsgPhp {
    /**
     * Возвращает исходный ответ без изменений.
     */
    public static byte[] process(byte[] array) {
        return array;
    }
}
