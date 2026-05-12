package ru.neverlands.anclient.model;

/**
 * Одна серверная категория клановой казны.
 *
 * Назначение:
 * - хранит связку `wca` -> иконка -> человекочитаемое название;
 * - сохраняет исходный `href`, потому что neverlands использует не только
 *   `gameplay/invent/cat/*.gif`, но и специальные иконки для отдельных групп;
 * - используется `KaznaActivity` как серверный фильтр, а не как локальная
 *   эвристика по названию предмета.
 */
public final class KaznaCategory {
    public final String wca;
    public final String title;
    public final String iconUrl;
    public final String href;

    public KaznaCategory(String wca, String title, String iconUrl, String href) {
        this.wca = safe(wca);
        this.title = safe(title);
        this.iconUrl = safe(iconUrl);
        this.href = safe(href);
    }

    public boolean isSame(KaznaCategory other) {
        return other != null && wca.equals(other.wca);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
