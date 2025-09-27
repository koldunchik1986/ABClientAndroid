package ru.neverlands.abclient.utils;

public class HelperStrings {

    /**
     * Extracts the content between two strings.
     * @param html The source string to search in.
     * @param s1 The starting delimiter string.
     * @param s2 The ending delimiter string.
     * @return The substring between s1 and s2, or null if not found.
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