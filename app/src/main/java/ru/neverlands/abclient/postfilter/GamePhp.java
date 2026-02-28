package ru.neverlands.abclient.postfilter;

/**
 * Заглушка пост‑фильтра для game.php (старый вход/фрейм игры).
 * В ПК версии есть обработка; на Android пока не требуется.
 */
public class GamePhp {
    /**
     * Возвращает исходный ответ без изменений.
     */
    public static byte[] process(byte[] array) {
        return array;
    }
}
