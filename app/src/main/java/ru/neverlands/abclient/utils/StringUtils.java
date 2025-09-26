package ru.neverlands.abclient.utils;

public class StringUtils {
    public static String subString(String text, String start, String end) {
        if (text == null || start == null || end == null) {
            return null;
        }
        int p1 = text.indexOf(start);
        if (p1 == -1) {
            return null;
        }
        p1 += start.length();
        int p2 = text.indexOf(end, p1);
        if (p2 == -1) {
            return null;
        }
        return text.substring(p1, p2);
    }
}
