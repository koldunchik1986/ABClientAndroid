package ru.neverlands.abclient.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HelperStrings {

    /**
     * Извлекает подстроку между двумя маркерами (case-insensitive).
     * Аналог HelperStrings.SubString() в C#.
     * @param html Строка-источник.
     * @param s1 Маркер начала.
     * @param s2 Маркер окончания.
     * @return Подстрока между s1 и s2 или null, если не найдено.
     */
    public static String subString(String html, String s1, String s2) {
        if (html == null || s1 == null || s2 == null) {
            return null;
        }

        int p1 = html.indexOf(s1);
        if (p1 == -1) {
            return null;
        }

        p1 += s1.length();

        int p2 = html.indexOf(s2, p1);
        if (p2 == -1) {
            return null;
        }

        return html.substring(p1, p2);
    }

    /**
     * Заменяет содержимое между маркерами s1 и s2 на newStr (case-insensitive).
     * Аналог HelperStrings.Replace() в C#.
     * Результат: html[0..p1+s1.len] + newStr + html[p2..end]
     * (s1 остаётся на месте, s2 тоже сохраняется, заменяется только то, что между ними)
     * @param html Строка-источник.
     * @param s1 Маркер начала (сохраняется).
     * @param s2 Маркер конца (сохраняется).
     * @param newStr Строка-замена вставляется между s1 и s2.
     * @return Изменённая строка или оригинал, если маркеры не найдены.
     */
    public static String replace(String html, String s1, String s2, String newStr) {
        if (html == null || s1 == null || s2 == null) {
            return html;
        }
        int p1 = indexOfIgnoreCase(html, s1, 0);
        if (p1 == -1) {
            return html;
        }
        int p2 = indexOfIgnoreCase(html, s2, p1 + s1.length());
        if (p2 == -1) {
            return html;
        }
        return html.substring(0, p1 + s1.length()) + newStr + html.substring(p2);
    }

    /**
     * Парсит список аргументов вида: "1,'text',abc".
     * Аналог HelperStrings.ParseArguments() в C#.
     * @param str Строка с аргументами.
     * @return Массив аргументов, каждый без внешних кавычек.
     */
    public static String[] parseArguments(String str) {
        List<String> list = new ArrayList<>();
        if (str == null || str.isEmpty()) {
            return new String[0];
        }

        int pos = 0;
        do {
            int pa = pos;
            if (str.charAt(pa) == '\'') {
                int pb = str.indexOf('\'', pa + 1);
                if (pb == -1) {
                    break;
                }
                String quotedArg = str.substring(pa + 1, pb);
                list.add(quotedArg);
                pos = pb + 1;
                if (pos < str.length()) {
                    if (str.charAt(pos) != ',') {
                        break;
                    }
                    pos++;
                }
            } else {
                int pb = str.indexOf(',', pa + 1);
                if (pb == -1) {
                    pb = str.length();
                }
                String nonQuotedArg = str.substring(pa, pb);
                list.add(nonQuotedArg);
                pos = pb + 1;
            }
        } while (pos < str.length());

        return list.toArray(new String[0]);
    }

    /**
     * Парсит данные о пользователе вида: 'value','value',...
     * Ищет пары одиночных кавычек, затем запятую между ними.
     * Аналог HelperStrings.ParsingUserinfo() в C#.
     * @param posu Строка с данными пользователя.
     * @return Массив извлечённых значений.
     */
    public static String[] parsingUserinfo(String posu) {
        List<String> list = new ArrayList<>();
        if (posu == null || posu.isEmpty()) {
            return new String[0];
        }
        int pos = 0;
        while (true) {
            int p1 = posu.indexOf('\'', pos);
            if (p1 == -1 || (p1 + 1) == posu.length()) break;
            int p2 = posu.indexOf('\'', p1 + 1);
            if (p2 == -1) break;
            list.add(posu.substring(p1 + 1, p2));
            if ((p2 + 1) == posu.length()) break;
            int p3 = posu.indexOf(',', p2 + 1);
            if (p3 == -1 || (p3 + 1) == posu.length()) break;
            pos = p3 + 1;
        }
        return list.toArray(new String[0]);
    }

    /**
     * Разбивает строку по строкам (игнорируя пустые и начинающиеся с ';'),
     * затем перемешивает в случайном порядке.
     * Аналог HelperStrings.RandomArray() в C#.
     * @param source Многострочный текст. Строки, начинающиеся с ';' — комментарии.
     * @return Перемешанный массив строк или null, если нет допустимых строк.
     */
    public static String[] randomArray(String source) {
        if (source == null || source.isEmpty()) return null;
        String[] sp = source.split("\\r?\\n");
        List<String> list = new ArrayList<>();
        for (String s : sp) {
            if (s == null || s.isEmpty()) continue;
            if (s.charAt(0) == ';') continue;
            list.add(s);
        }
        if (list.isEmpty()) return null;
        Random rnd = new Random();
        List<String> rlist = new ArrayList<>();
        while (!list.isEmpty()) {
            int index = rnd.nextInt(list.size());
            rlist.add(list.get(index));
            list.remove(index);
        }
        return rlist.toArray(new String[0]);
    }

    /**
     * Парсит строку JS-массива вида: 1,300,"text",[nested,array],...
     * Каждый элемент — либо простое значение (List из 1 элемента),
     * либо вложенный массив [...] (List из нескольких элементов).
     * Аналог HelperStrings.ParseJsString() в C#.
     * Пример входа: "1,300,10,0,2,\"\",\"1\",[],[800817,\"БИЗОНИУС\",1387692418]"
     * @param str Содержимое JS-массива (без внешних скобок).
     * @return List<List<String>> или null если входная строка пустая/null.
     */
    public static List<List<String>> parseJsString(String str) {
        if (str == null || str.length() < 2) return null;

        List<List<String>> result = new ArrayList<>();
        int p1 = 0;

        while (p1 < str.length()) {
            int p2;
            if (str.charAt(p1) != '[') {
                p2 = str.indexOf(',', p1 + 1);
            } else {
                p2 = str.indexOf(']', p1 + 1);
                if (p2 != -1) p2++;
            }
            if (p2 == -1) p2 = str.length();

            String s = str.substring(p1, p2);
            List<String> arg = new ArrayList<>();
            if (!s.isEmpty()) {
                if (s.charAt(0) != '[') {
                    arg.add(trimQuoteChars(s));
                } else {
                    String inner = trimBracketChars(s);
                    String[] sarg = inner.split(",");
                    for (String ss : sarg) {
                        arg.add(trimQuoteChars(ss));
                    }
                }
            }
            result.add(arg);
            p1 = p2 + 1;
        }

        return result;
    }

    private static int indexOfIgnoreCase(String str, String searchStr, int fromIndex) {
        if (str == null || searchStr == null) return -1;
        return str.toLowerCase().indexOf(searchStr.toLowerCase(), fromIndex);
    }

    private static String trimQuoteChars(String s) {
        if (s == null) return "";
        int start = 0, end = s.length();
        while (start < end) {
            char c = s.charAt(start);
            if (c == ' ' || c == '"' || c == '\'') start++;
            else break;
        }
        while (end > start) {
            char c = s.charAt(end - 1);
            if (c == ' ' || c == '"' || c == '\'') end--;
            else break;
        }
        return s.substring(start, end);
    }

    private static String trimBracketChars(String s) {
        if (s == null) return "";
        int start = 0, end = s.length();
        while (start < end) {
            char c = s.charAt(start);
            if (c == ' ' || c == '[' || c == ']') start++;
            else break;
        }
        while (end > start) {
            char c = s.charAt(end - 1);
            if (c == ' ' || c == '[' || c == ']') end--;
            else break;
        }
        return s.substring(start, end);
    }
}
