package ru.neverlands.abclient.model;

/**
 * Класс-модель, представляющий один контакт.
 * Хранит всю информацию о персонаже, полученную через API (info.cgi) и сохраненную в contacts.xml.
 */
public class Contact {

    /**
     * Уникальный идентификатор игрока.
     * Пример: "12345"
     */
    public String playerID;

    /**
     * Ник персонажа.
     * Пример: "Блудя"
     */
    public String nick;

    /**
     * Уровень персонажа.
     */
    public int playerLevel;

    /**
     * Цифровой идентификатор склонности.
     * 1 - Тьма, 2 - Свет, 3 - Сумрак, 4 - Хаос.
     */
    public int inclination;

    /**
     * Текстовое название склонности.
     * Пример: "Darks"
     */
    public String inclinationName;

    /**
     * Устаревший числовой ID клана.
     */
    public String clanNumber;

    /**
     * Имя файла иконки клана.
     * Используется для получения ID клана (удалением .gif) и для отображения иконки.
     * Зависимость: `http://image.neverlands.ru/signs/` + clanIco
     * Пример: "dshi.gif"
     */
    public String clanIco;

    /**
     * Название клана.
     * Пример: "DarkStone"
     */
    public String clanName;

    /**
     * Статус игрока в клане.
     * Пример: "Глава клана"
     */
    public String clanStatus;

    /**
     * Пол персонажа (0 или 1).
     */
    public int gender;

    /**
     * Статус блокировки (0 - не заблокирован).
     */
    public int blockStatus;

    /**
     * Статус в тюрьме (0 - не в тюрьме).
     */
    public int jailStatus;

    /**
     * Оставшееся время молчанки в чате (в секундах).
     */
    public int muteSeconds;

    /**
     * Оставшееся время молчанки на форуме (в секундах).
     */
    public int muteForumSeconds;

    /**
     * Статус онлайн.
     * 1 - онлайн, 0 - оффлайн.
     */
    public int onlineStatus;

    /**
     * Местоположение персонажа.
     * Пример: "Neverlands city"
     */
    public String geoLocation;

    /**
     * Номер лога боя (если персонаж в бою).
     */
    public String warLogNumber;

    /**
     * Тип отношения к контакту.
     * 0 - Нейтрал, 1 - Враг, 2 - Друг.
     * Зависимость: используется для окрашивания ника в `ContactsAdapter` и `ch_list.js`.
     */
    public int classId;

    /**
     * Пользовательский комментарий к контакту (пока не используется).
     */
    public String comment;

    /**
     * Comma-separated effect ids from info.cgi line "2|...".
     * Example: "3,31,46"
     */
    public String effectIds;

    /**
     * Comma-separated effect states from info.cgi line "2|...".
     * Format: "id:count:timeout,id:count:timeout"
     * Example: "3:1:01:54:59,31:5:00:41:25"
     */
    public String effectStates;

    /**
     * Инструмент авто-нападения для конкретного контакта (аналог C# `Contact.ToolId`).
     *
     * Значения:
     * 0 - использовать глобальный `AppVars.AutoAttackToolId`,
     * 1 - боевые,
     * 2 - закрытые боевые,
     * 3 - кулачки,
     * 4 - закрытые кулачки,
     * 5 - портал,
     * 6 - яд,
     * 7 - сильная спина (приоритет: превосходное зелье).
     *
     * Зависимости:
     * - `ContactsManager.getToolIdOfContact(...)` читает это поле как override,
     * - `RoomManager` использует это значение при авто-нападении.
     */
    public int toolId;

    public Contact() {}

}
