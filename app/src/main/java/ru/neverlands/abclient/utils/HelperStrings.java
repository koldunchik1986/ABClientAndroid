package ru.neverlands.abclient.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HelperStrings {
    private HelperStrings() {}

    /**
     * Находит подстроку, заключенную между строками s1 и s2.
     * Регистронезависимый поиск.
     * @param html Исходная строка для поиска.
     * @param s1 Начальная строка-маркер.
     * @param s2 Конечная строка-маркер.
     * @return Найденная подстрока или null, если что-то не найдено.
     */
    public static String subString(String html, String s1, String s2) {
        if (html == null || s1 == null || s2 == null) return null;

        String lowerHtml = html.toLowerCase();
        String lowerS1 = s1.toLowerCase();
        String lowerS2 = s2.toLowerCase();

        int p1 = lowerHtml.indexOf(lowerS1);
        if (p1 == -1) return null;

        p1 += s1.length();

        int p2 = lowerHtml.indexOf(lowerS2, p1);
        if (p2 == -1) return null;

        return html.substring(p1, p2);
    }

    /**
     * Находит подстроку между s1 и s2 и заменяет ее на newStr.
     */
    public static String replace(String html, String s1, String s2, String newStr) {
        if (html == null || s1 == null || s2 == null || newStr == null) return html;

        String lowerHtml = html.toLowerCase();
        String lowerS1 = s1.toLowerCase();
        String lowerS2 = s2.toLowerCase();

        int p1 = lowerHtml.indexOf(lowerS1);
        if (p1 == -1) return html;

        int p2 = lowerHtml.indexOf(lowerS2, p1 + s1.length());
        if (p2 == -1) return html;

        return html.substring(0, p1 + s1.length()) +
               newStr +
               html.substring(p2);
    }

    /**
     * Парсит строку с аргументами, разделенными запятыми, с учетом кавычек.
     * Пример: 'arg1','arg2',arg3
     */
    public static String[] parseArguments(String str) {
        if (str == null) return new String[0];
        List<String> list = new ArrayList<>();
        int pos = 0;
        while (pos < str.length()) {
            int pa = pos;
            if (str.charAt(pa) == '\'') {
                int pb = str.indexOf('"', pa + 1);
                if (pb == -1) break;
                list.add(str.substring(pa + 1, pb));
                pos = pb + 1;
                if (pos < str.length() && str.charAt(pos) == ',') {
                    pos++;
                }
            } else {
                int pb = str.indexOf(',', pa + 1);
                if (pb == -1) {
                    pb = str.length();
                }
                list.add(str.substring(pa, pb));
                pos = pb + 1;
            }
        }
        return list.toArray(new String[0]);
    }
}
