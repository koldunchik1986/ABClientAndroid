package ru.neverlands.abclient.model;

/**
 * Перечисление типов быстрых действий для кнопок.
 * Каждый тип соответствует определенной функции в приложении.
 */
public enum QuickActionType {
    // Основные действия
    AUTO_FIGHT("Автобой", "auto_fight"),
    QUICK_ACTIONS("Быстрые действия ▼", "quick_actions"),
    AUTO_RECALL("Авторыбалка", "auto_recall"),
    AUTO_HUNT("Автоохота", "auto_hunt"),
    AUTO_ATTACK("Автонападение", "auto_attack"),
    AUTO_INVISIBLE("АвтоНевид", "auto_invisible"),
    LOCATION_TRACKING("Слежение за локацией", "location_tracking"),
    AUTO_DETECT("АвтоОбнаружение", "auto_detect"),
    AUTO_SUMMON("АвтоПризыв", "auto_summon"),
    AUTO_HEAL("АвтоЛечение", "auto_heal"),
    
    // Дополнительные действия
    OPEN_CONTACTS("Открыть контакты", "open_contacts"),
    OPEN_PINFO("Открыть PINFO", "open_pinfo"),
    OPEN_LOGS("Открыть Логи", "open_logs"),
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
