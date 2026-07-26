package ru.neverlands.anclient.ui;

import ru.neverlands.anclient.license.LicenseRuntime;

/**
 * Каталог авто-функций, доступных пользователю (D6).
 *
 * Зачем выделено: список и его лицензионная фильтрация нужны и панели быстрых кнопок,
 * и диалогу таймеров (таймер умеет включать/выключать авто-функции). После выделения
 * {@link TimerDialogs} держать константу приватной в `QuickButtonsPanel` стало нельзя,
 * а дублировать список — значит гарантированно получить расхождение при добавлении
 * новой авто-функции.
 */
public final class AutoFunctionCatalog {

    private AutoFunctionCatalog() {
    }

    /**
     * Полный список авто-функций.
     *
     * Перед показом обязательно проходит {@link #getAllowed()}: `Авто-Травник`,
     * `Авто-Лесоруб` и `Анти-Captcha` появляются только при individual full/custom grant
     * (`auto_cut`/`auto_lumberjack`/`anti_captcha`) и исчезают после истечения expiresAt.
     */
    private static final String[] AUTO_FUNCTIONS = new String[]{
            "Авто-Бой",
            "Авто-Рыбалка",
            "Авто-Охота",
            "Авто-Питьё",
            "Авто-Клад",
            "Авто-Травник",
            "Авто-Лесоруб",
            "Авто-Шахтёр",
            "Авто-Босс",
            "Анти-Captcha"
    };

    /**
     * Список авто-функций, разрешённых текущей лицензией.
     *
     * @return отфильтрованные подписи; массив создаётся заново на каждый вызов,
     *         поэтому внутренняя константа не может быть изменена вызывающим кодом.
     */
    public static String[] getAllowed() {
        return LicenseRuntime.getInstance().filterAutoFunctionLabels(AUTO_FUNCTIONS);
    }
}
