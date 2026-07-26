package ru.neverlands.anclient.utils;

import android.content.Context;

/**
 * Мелкие UI-преобразования, общие для нескольких экранов (D6).
 *
 * Зачем выделено: `dpToPx(...)` существовал двумя побайтово идентичными приватными
 * копиями — в `Navigator` и `QuickButtonsPanel`, — а после выделения диалогов таймеров
 * потребовался и третьему классу. Вместо третьей копии реализация сведена сюда.
 */
public final class UiUtils {

    private UiUtils() {
    }

    /**
     * Переводит независимые от плотности пиксели (dp) в физические пиксели экрана.
     *
     * @param context контекст для доступа к метрикам дисплея.
     * @param dp      значение в dp.
     * @return значение в px; при `null`-контексте возвращает исходное число
     *         (раньше такой вызов приводил к NPE, поэтому поведение рабочего кода не меняется).
     */
    public static int dpToPx(Context context, int dp) {
        if (context == null) {
            return dp;
        }
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
