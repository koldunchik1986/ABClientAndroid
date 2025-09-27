package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class CounterJs {
    public static byte[] process() {
        return Russian.getBytes("function counterview(referr){}");
    }
}