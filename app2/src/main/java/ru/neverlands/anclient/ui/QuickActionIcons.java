package ru.neverlands.anclient.ui;

import ru.neverlands.anclient.R;
import ru.neverlands.anclient.model.QuickActionType;

/**
 * Единый источник иконок для быстрых действий (D6).
 *
 * Зачем выделено:
 * - до рефакторинга карта иконок существовала в ТРЁХ экземплярах:
 *   `QuickButtonsPanel.getIconUrlForAction(...)` (полная),
 *   `FunctionListAdapter.getIconUrlForAction(...)` (только авто-функции),
 *   `FunctionListAdapter.SelfActionAdapter.getIconUrlForAction(...)` (только quick-действия);
 * - расхождение любой из копий давало разные иконки в панели и в списке выбора,
 *   а добавление нового действия требовало правки в трёх местах.
 *
 * Эквивалентность подтверждена перед слиянием:
 * - копия quick-действий совпадала с полной картой полностью;
 * - копия авто-функций отличалась только явным `AUTO_CAPTCHA -> null`,
 *   что в полной карте даёт тот же `null` через ветку `default`.
 *
 * Класс содержит только чистые функции без состояния, поэтому безопасен
 * для вызова из любого потока и из любого адаптера/представления.
 */
public final class QuickActionIcons {

    private QuickActionIcons() {
    }

    /**
     * Удалённая иконка действия с игрового сервера.
     *
     * Важно: `null` — это штатное значение, а не ошибка. Оно означает
     * «удалённой иконки нет, использовать локальный drawable из {@link #getIconRes(QuickActionType)}».
     * Все типы, не перечисленные явно (служебные пункты меню, авто-функции без ачивки),
     * попадают в `default` и тоже возвращают `null`.
     *
     * @param type тип быстрого действия.
     * @return абсолютный URL картинки или `null`, если удалённой иконки нет.
     */
    public static String getIconUrl(QuickActionType type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case AUTO_FIGHT:
                return "http://image.neverlands.ru/achievement/2/a_2_10.gif";
            case AUTO_FISH:
                return "http://image.neverlands.ru/achievement/40/a_40_10.gif";
            case AUTO_SKIN:
                return "http://image.neverlands.ru/achievement/70/a_70_10.gif";
            case AUTO_ATTACK:
                return "http://image.neverlands.ru/achievement/13/a_13_10.gif";
            case AUTO_BOSS:
                return "http://image.neverlands.ru/achievement/23/a_23_10.gif";
            case AUTO_INVISIBLE:
                return "http://image.neverlands.ru/weapon/i_w27_53.gif";
            case LOCATION_TRACKING:
                return "http://image.neverlands.ru/signs/compass.gif";
            case AUTO_DETECT:
                return "http://image.neverlands.ru/achievement/26/a_26_10.gif";
            case AUTO_SUMMON:
                return "http://image.neverlands.ru/achievement/11/a_11_10.gif";
            case AUTO_CURE:
                return "http://image.neverlands.ru/achievement/150/a_150_10.gif";
            case AUTO_TREASURE:
                return "http://image.neverlands.ru/achievement/9/a_9_10.gif";
            case AUTO_CUT:
                return "http://image.neverlands.ru/achievement/20/a_20_10.gif";
            case AUTO_LUMBERJACK:
                return "http://image.neverlands.ru/achievement/30/a_30_10.gif";
            case AUTO_MINE:
                return "http://image.neverlands.ru/achievement/60/a_60_10.gif";
            case QUICK_SELF_RASS:
                return "http://image.neverlands.ru/weapon/i_w28_23.gif";
            case QUICK_OPEN_NEVID:
                return "http://image.neverlands.ru/weapon/i_w28_28.gif";
            case QUICK_TELEPORT:
                return "http://image.neverlands.ru/weapon/i_w28_22.gif";
            case QUICK_ISLAND:
                return "http://image.neverlands.ru/weapon/i_w28_22.gif";
            case QUICK_TOTEM:
                return "http://image.neverlands.ru/signs/totems/9.gif";
            case QUICK_ELIXIR_BLAZ:
                return "http://image.neverlands.ru/weapon/i_w61_107.gif";
            case QUICK_ELIXIR_CURE:
                return "http://image.neverlands.ru/weapon/i_w61_104.gif";
            case QUICK_ELIXIR_RESTORE:
                return "http://image.neverlands.ru/weapon/i_w61_101.gif";
            default:
                // QUICK_ACTIONS, AUTO_BAIT, AUTO_COMPASS, AUTO_DRINK, AUTO_MOVING,
                // AUTO_REFRESH, AUTO_CAPTCHA, OPEN_*, TIMERS, REFRESH_CONTACTS:
                // удалённой иконки нет, используется локальный drawable.
                return null;
        }
    }

    /**
     * Локальный drawable-ресурс действия.
     *
     * Используется и как самостоятельная иконка, и как fallback,
     * пока подгружается удалённая картинка из {@link #getIconUrl(QuickActionType)}.
     *
     * @param type тип быстрого действия.
     * @return id ресурса; для неизвестных типов — нейтральный `ic_add`.
     */
    public static int getIconRes(QuickActionType type) {
        if (type == null) {
            return R.drawable.ic_add;
        }
        switch (type) {
            case AUTO_FIGHT:
                return R.drawable.ic_auto_fight;
            case QUICK_ACTIONS:
                return R.drawable.ic_sort;
            case AUTO_FISH:
                return R.drawable.ic_auto_fish;
            case AUTO_SKIN:
                return R.drawable.ic_lez_fight;
            case AUTO_ATTACK:
                return R.drawable.ic_auto_attack;
            case AUTO_COMPASS:
                return R.drawable.ic_compas;
            case AUTO_BOSS:
                return R.drawable.ic_compas;
            case AUTO_INVISIBLE:
                return R.drawable.ic_auto_invisible;
            case LOCATION_TRACKING:
                return R.drawable.ic_location;
            case AUTO_DETECT:
                return R.drawable.ic_auto_detect;
            case AUTO_SUMMON:
                return R.drawable.ic_auto_summon;
            case AUTO_CURE:
                return R.drawable.ic_red_cross;
            case AUTO_MOVING:
                return R.drawable.ic_globe;
            case AUTO_TREASURE:
                return R.drawable.ic_auto_detect;
            case AUTO_REFRESH:
                return R.drawable.ic_refresh;
            case AUTO_CAPTCHA:
                return R.drawable.ic_auto_detect;
            case OPEN_CONTACTS:
                return R.drawable.ic_add_contact;
            case OPEN_PINFO:
                return R.drawable.ic_info;
            case OPEN_STATS:
                return R.drawable.ic_info;
            case TIMERS:
                return R.drawable.ic_timer;
            case REFRESH_CONTACTS:
                return R.drawable.ic_refresh;
            case QUICK_SELF_RASS:
                return R.drawable.ic_back;
            case QUICK_OPEN_NEVID:
                return R.drawable.ic_expand_more;
            case QUICK_TELEPORT:
                return R.drawable.ic_sort;
            default:
                // AUTO_BAIT, AUTO_DRINK, AUTO_CUT, AUTO_LUMBERJACK, AUTO_MINE,
                // OPEN_LOGS, QUICK_ISLAND, QUICK_TOTEM, QUICK_ELIXIR_*:
                // отдельной локальной иконки нет, используется нейтральная.
                return R.drawable.ic_add;
        }
    }

    /**
     * Признак авто-функции: такие действия имеют состояние ВКЛ/ВЫКЛ
     * и отображаются с визуальной индикацией активности.
     *
     * @param type тип быстрого действия.
     * @return true, если действие является переключаемой авто-функцией.
     */
    public static boolean isAutoFunction(QuickActionType type) {
        if (type == null) {
            return false;
        }
        switch (type) {
            case AUTO_FIGHT:
            case AUTO_FISH:
            case AUTO_BAIT:
            case AUTO_SKIN:
            case AUTO_ATTACK:
            case AUTO_COMPASS:
            case AUTO_BOSS:
            case AUTO_INVISIBLE:
            case LOCATION_TRACKING:
            case AUTO_DETECT:
            case AUTO_SUMMON:
            case AUTO_CURE:
            case AUTO_DRINK:
            case AUTO_MOVING:
            case AUTO_TREASURE:
            case AUTO_CUT:
            case AUTO_LUMBERJACK:
            case AUTO_MINE:
            case AUTO_REFRESH:
            case AUTO_CAPTCHA:
                return true;
            default:
                return false;
        }
    }
}
