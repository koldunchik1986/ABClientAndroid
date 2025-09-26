package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class CounterJs {
    /**
     * Заменяет сторонние скрипты-счетчики на пустую функцию.
     * @return Массив байт с кодом-заглушкой.
     */
    public static byte[] process() {
        return Russian.getBytes("function counterview(referr){}");
    }
}
