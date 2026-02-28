package ru.neverlands.abclient.utils;

public class HelperStrings {

    /**
     * Извлекает подстроку между двумя маркерами.
     * Зависимости:
     * - Обычный поиск по строке (indexOf), без regex и без учета вложенности.
     * Назначение:
     * - Используется парсерами HTML/JS (например, ButPhp) для извлечения значений.
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
     * Парсит список аргументов вида: "1,'text',abc".
     * Зависимости:
     * - Примитивный разбор по запятым и одиночным кавычкам.
     * - Не поддерживает экранирование и вложенные кавычки.
     * Назначение:
     * - Вспомогательный метод для простых JS/HTML конструкций, где аргументы
     *   передаются строкой (используется в старых обработчиках).
     * @param str Строка с аргументами.
     * @return Массив аргументов, каждый без внешних кавычек.
     */
    public static String[] parseArguments(String str) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (str == null || str.isEmpty()) {
            return new String[0];
        }

        int pos = 0;
        while (pos < str.length()) {
            int pa = pos;
            if (str.charAt(pa) == '\'') {
                int pb = str.indexOf('\'', pa + 1);
                if (pb == -1) {
                    break;
                }
                String quotedArg = str.substring(pa + 1, pb);
                list.add(quotedArg);
                pos = pb + 1;
                if (pos < str.length() && str.charAt(pos) == ',') {
                    pos++;
                }
            } else {
                int pb = str.indexOf(',', pa);
                if (pb == -1) {
                    pb = str.length();
                }
                String nonQuotedArg = str.substring(pa, pb);
                list.add(nonQuotedArg);
                pos = pb + 1;
            }
        }
        return list.toArray(new String[0]);
    }
}
