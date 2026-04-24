package ru.neverlands.anclient.postfilter;

/**
 * Справочник fast-action фильтров и сообщений для MainPhp/FastActionManager.
 *
 * Источник выноса: helpers `isAttackFastId`, `getInventoryFilter`, `buildFastItemNotFoundMessage`
 * из MainPhp.java. Класс не выполняет action сам, а только классифицирует FastId и строит текст.
 */
final class MainPhpFastActionSupport {

    private MainPhpFastActionSupport() {
    }

    /**
     * Проверяет, относится ли fastId к атакующим свиткам/иконкам.
     * Вход: AppVars.FastId или нормализованный id предмета из UI/профиля.
     */
    static boolean isAttackFastId(String fastId) {
        if (fastId == null) return false;
        switch (fastId) {
            case "i_svi_001.gif":
            case "i_svi_002.gif":
            case "i_w28_26.gif":
            case "i_w28_26X.gif":
            case "i_svi_205.gif":
            case "i_w28_24.gif":
            case "i_w28_25.gif":
                return true;
            default:
                return false;
        }
    }

    /**
     * Возвращает фильтр инвентаря для fastId.
     *
     * Возвращаемые значения:
     * - `&im=0&wca=28`: свитки/телепорт.
     * - `&im=0&wca=27`: зелья.
     * - `&im=6`: эликсиры.
     * - `TOTEM`: отдельная ветка тотемов.
     * - null: фильтр неизвестен.
     */
    static String getInventoryFilter(String fastId) {
        if (fastId == null) return null;
        String normalizedFastId = normalizeFastId(fastId);
        switch (normalizedFastId) {
            case "i_svi_001.gif":
            case "i_svi_002.gif":
            case "i_w28_26.gif":
            case "i_w28_26X.gif":
            case "i_svi_205.gif":
            case "i_w28_24.gif":
            case "i_w28_25.gif":
            case "i_w28_22.gif":
            case "i_w28_23.gif":
            case "i_w28_28.gif":
            case "i_svi_213.gif":
            case "i_w28_27.gif":
            case "i_w28_86.gif":
                return "&im=0&wca=28";
            case "Яд":
            case "Зелье Сильной Спины":
            case "Превосходное Зелье Сильной Спины":
            case "Зелье Невидимости":
            case "Зелье Блаженства":
            case "Зелье Метаболизма":
            case "Зелье Просветления":
            case "Зелье Сокрушительных Ударов":
            case "Зелье Стойкости":
            case "Зелье Недосягаемости":
            case "Зелье Точного Попадания":
            case "Зелье Ловких Ударов":
            case "Зелье Мужества":
            case "Зелье Жизни":
            case "Зелье Лечения":
            case "Зелье Восстановления Маны":
            case "Зелье Энергии":
            case "Зелье Удачи":
            case "Зелье Силы":
            case "Зелье Ловкости":
            case "Зелье Гения":
            case "Зелье Боевой Славы":
            case "Зелье Секрет Волшебника":
            case "Зелье Медитации":
            case "Зелье Иммунитета":
            case "Зелье Лечения Отравлений":
            case "Зелье Огненного Ореола":
            case "Зелье Колкости":
            case "Зелье Загрубелой Кожи":
            case "Зелье Панциря":
            case "Зелье Человек-гора":
            case "Зелье Скорости":
            case "Жажда Жизни":
            case "Ментальная Жажда":
            case "Зелье подвижности":
            case "Ярость Берсерка":
            case "Зелье Хрупкости":
            case "Зелье Мифриловый Стержень":
            case "Зелье Соколиный взор":
            case "Секретное Зелье":
                return "&im=0&wca=27";
            case "Эликсир Блаженства":
            case "Эликсир Мгновенного Исцеления":
            case "Эликсир Восстановления":
                return "&im=6";
            case "Телепорт (Остров Туротор)":
                return "&im=0&wca=28";
            case "Тотем":
                return "TOTEM";
            default:
                if (InventoryParser.containsIgnoreCase(normalizedFastId, "сильной спины")) {
                    return "&im=0&wca=27";
                }
                if (InventoryParser.containsIgnoreCase(normalizedFastId, "яд")) {
                    return "&im=0&wca=27";
                }
                return null;
        }
    }

    /**
     * Строит локальное chat-сообщение об отсутствии предмета.
     * Важные переменные: safeFastId, timestamp, handler="FastActionManager".
     * Формат сохраняет требование проекта: `'timestamp' [Источник]: текст`.
     */
    static String buildFastItemNotFoundMessage(String fastId) {
        String safeFastId = fastId == null ? "" : fastId.trim();

        long now = System.currentTimeMillis();
        String timestamp = String.format("%02d:%02d:%02d",
                (now / 3600000) % 24,
                (now / 60000) % 60,
                (now / 1000) % 60);
        String handler = "FastActionManager";

        String message;
        if (safeFastId.startsWith("Эликсир ")) {
            message = "<font color=#FF0000>'" + timestamp + "' [" + handler + "]: " + safeFastId + " не найден, действие отменено.</font>";
        } else if (safeFastId.isEmpty()) {
            message = "<font color=#FF0000>'" + timestamp + "' [" + handler + "]: Предмет не найден, действие отменено.</font>";
        } else {
            message = "<font color=#FF0000>'" + timestamp + "' [" + handler + "]: " + safeFastId + " в инвентаре не найден, действие отменено.</font>";
        }

        return message;
    }

    /**
     * Нормализация FastId перед switch: убирает NBSP/BOM/zero-width и сжимает пробелы.
     */
    private static String normalizeFastId(String fastId) {
        if (fastId == null) return "";
        String normalized = fastId
                .replace('\u00A0', ' ')
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .trim();
        return normalized.replaceAll("\\s{2,}", " ");
    }
}
