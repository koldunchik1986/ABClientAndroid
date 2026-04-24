package ru.neverlands.anclient.utils;

/**
 * Утилита для безопасного парсинга строк в примитивные типы.
 * Консолидирует parseIntSafe, parseLongSafe, parseDoubleSafe из 8 файлов.
 * Дефолтные значения: int=0, long=0L, double=0d
 */
public class ParseUtils {

    public static int parseIntSafe(String value) {
        return parseIntSafe(value, 0);
    }

    public static int parseIntSafe(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value.trim()); } 
        catch (Exception ignored) { return defaultValue; }
    }

    public static int parseIntSafeStripped(String value) {
        if (value == null) return 0;
        try { return Integer.parseInt(value.trim().replaceAll("[^0-9\\-]", "")); } 
        catch (Exception ignored) { return 0; }
    }

    public static long parseLongSafe(String value) {
        return parseLongSafe(value, 0L);
    }

    public static long parseLongSafe(String value, long defaultValue) {
        if (value == null) return defaultValue;
        try { return Long.parseLong(value.trim()); } 
        catch (Exception ignored) { return defaultValue; }
    }

    public static double parseDoubleSafe(String value) {
        return parseDoubleSafe(value, 0d);
    }

    public static double parseDoubleSafe(String value, double defaultValue) {
        if (value == null) return defaultValue;
        try {
            String normalized = value.trim().replace(" ", "").replace(',', '.');
            if (normalized.isEmpty()) return defaultValue;
            return Double.parseDouble(normalized);
        } catch (Exception ignored) { return defaultValue; }
    }
}
