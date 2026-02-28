package ru.neverlands.abclient.postfilter;

/**
 * Заглушка пост‑фильтра для ch/room.php.
 * В ПК версии здесь есть логика обработки комнаты; на Android пока не требуется.
 * Оставляем как точку расширения для будущего портирования.
 */
public class ChRoomPhp {
    /**
     * Возвращает исходный ответ без изменений.
     */
    public static byte[] process(byte[] array) {
        return array;
    }
}
