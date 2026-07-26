package ru.neverlands.anclient.utils;

/**
 * Утилита безопасного парсинга строк и нормализации числовых диапазонов.
 *
 * Консолидирует `parseIntSafe`, `parseLongSafe`, `parseDoubleSafe`, а также
 * зажим значений в границы (`clamp`, `clampPercent`, `parseIntInRange`).
 * Дефолтные значения: int=0, long=0L, double=0d.
 *
 * История (D6): ранее по проекту были разбросаны девять приватных копий `parseIntSafe`
 * и пять копий зажима (`clamp`/`clampInt`/`clampPercent`). Копии были поведенчески
 * эквивалентны (проверено перед слиянием), но требовали правки в девяти местах
 * и расходились по обработке `null`/пробелов.
 */
public class ParseUtils {

    /**
     * Зажимает значение в границы `[min, max]`.
     *
     * Заменяет прежние `AntiCaptchaManager.clamp(...)` и `AutoFunctionsManager.clampInt(...)`,
     * которые были побайтово идентичны.
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Зажимает значение в диапазон процентов `[0, 100]`.
     *
     * Заменяет три идентичные копии из `CharacterVitalsManager`, `NeverApi` и `MapAjax`.
     */
    public static int clampPercent(int value) {
        return clamp(value, 0, 100);
    }

    public static int parseIntSafe(String value) {
        return parseIntSafe(value, 0);
    }

    public static int parseIntSafe(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value.trim()); } 
        catch (Exception ignored) { return defaultValue; }
    }

    /**
     * Парсит целое и приводит результат в диапазон `[min, max]` (D6).
     *
     * Зачем здесь: раньше жила приватной копией в `QuickButtonsPanel.parseIntInRange(...)`
     * и использовалась семью диалогами (травы, деревья, антикапча, таймеры).
     * `ParseUtils` — уже существующая точка консолидации парсинга, поэтому реализация
     * переехала сюда, а не в новый вспомогательный класс.
     *
     * Поведение сохранено 1:1: пустая строка и `null` дают `defaultValue`,
     * некорректный текст — тоже, после чего значение зажимается в границы.
     *
     * @param value        исходная строка (обычно текст поля ввода).
     * @param min          нижняя граница включительно.
     * @param max          верхняя граница включительно.
     * @param defaultValue значение при пустой/некорректной строке (тоже зажимается).
     */
    public static int parseIntInRange(String value, int min, int max, int defaultValue) {
        return clamp(parseIntSafe(value, defaultValue), min, max);
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
