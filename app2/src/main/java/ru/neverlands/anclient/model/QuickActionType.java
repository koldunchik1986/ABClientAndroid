package ru.neverlands.anclient.model;

/**
 * Перечисление типов быстрых действий для кнопок.
 * Каждый тип соответствует определенной функции в приложении.
 */
public enum QuickActionType {
    // Основные действия
    AUTO_FIGHT("Авто-Бой", "auto_fight"),
    QUICK_ACTIONS("Быстрые действия ▼", "quick_actions"),
    AUTO_FISH("Авто-Рыбалка", "auto_fish"),
    AUTO_BAIT("Авто-Приманка", "auto_bait"),
    AUTO_ATTACK("Авто-Нападение", "auto_attack"),
    AUTO_COMPASS("Авто-Компас", "auto_compass"),
    AUTO_BOSS("Авто-Боссы", "auto_boss"),
    AUTO_INVISIBLE("Авто-Невид", "auto_invisible"),
    LOCATION_TRACKING("Слежение за локацией", "location_tracking"),
    AUTO_DETECT("Авто-Обнаружение", "auto_detect"),
    AUTO_SUMMON("Авто-Тотем", "auto_summon"),
    AUTO_CURE("Авто-Лечение", "auto_cure"),
    AUTO_DRINK("Авто-Питье", "auto_drink"),
    AUTO_MOVING("Навигатор", "auto_moving"),
    AUTO_TREASURE("Авто-Клад", "auto_treasure"),
    AUTO_CUT("Авто-Травник", "auto_cut"),
    AUTO_REFRESH("Авто-Обновление", "auto_refresh"),
    // Anti-Captcha: платная внешняя интеграция anti-captcha.com.
    // Лицензирование: не входит в public/limited; доступна через full grant или custom `anti_captcha`.
    AUTO_CAPTCHA("Анти-Captcha", "anti_captcha"),
    AUTO_SKIN("Авто-Охота", "auto_skin"),

    // Дополнительные действия
    OPEN_CONTACTS("Открыть контакты", "open_contacts"),
    OPEN_PINFO("Открыть PINFO", "open_pinfo"),
    OPEN_LOGS("Открыть Логи", "open_logs"),
    OPEN_STATS("Статистика", "open_stats"),
    TIMERS("Таймеры", "timers"),
    REFRESH_CONTACTS("Обновить контакты", "refresh_contacts"),

    // Быстрые действия на себя
    QUICK_SELF_RASS("Рассеять невид", "quick_self_rass", "selfRass"),
    QUICK_OPEN_NEVID("Обнаружение", "quick_open_nevid", "openNevid"),
    QUICK_TELEPORT("Телепорт", "quick_teleport", "teleport"),
    QUICK_ISLAND("Остров (Туротор)", "quick_island", "island"),
    QUICK_TOTEM("Тотем", "quick_totem", "totem"),
    QUICK_ELIXIR_BLAZ("Эликсир Блаженства", "quick_elixir_blaz", "elixirBlaz"),
    QUICK_ELIXIR_CURE("Эликсир Исцеления", "quick_elixir_cure", "elixirCure"),
    QUICK_ELIXIR_RESTORE("Эликсир Восстановления", "quick_elixir_restore", "elixirRestore"),

    // Пустая кнопка
    NONE("Пустая", "none", null);

    private final String displayName;
    private final String actionKey;
    private final String quickActionKey;

    QuickActionType(String displayName, String actionKey) {
        this(displayName, actionKey, null);
    }

    QuickActionType(String displayName, String actionKey, String quickActionKey) {
        this.displayName = displayName;
        this.actionKey = actionKey;
        this.quickActionKey = quickActionKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getActionKey() {
        return actionKey;
    }

    public String getQuickActionKey() {
        return quickActionKey;
    }

    public boolean hasQuickActionKey() {
        return quickActionKey != null;
    }

    public static QuickActionType fromActionKey(String key) {
        for (QuickActionType type : values()) {
            if (type.actionKey.equals(key)) {
                return type;
            }
        }
        return NONE;
    }
}
