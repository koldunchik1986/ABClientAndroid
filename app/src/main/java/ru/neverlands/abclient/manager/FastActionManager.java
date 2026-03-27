package ru.neverlands.abclient.manager;

import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Locale;

import ru.neverlands.abclient.model.InvEntry;
import ru.neverlands.abclient.postfilter.Filter;
import ru.neverlands.abclient.postfilter.MainPhp;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HtmlUtils;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.Russian;

/**
 * Менеджер быстрых действий (портирование FormMainFast.cs + PostFilter/MainPhpFast.cs).
 *
 * Часть 1 (FormMainFast.cs): Управление — fastStart, fastCancel, fastAttack*
 * Часть 2 (MainPhpFast.cs): Парсинг HTML — processMainPhp, mainPhpFast*
 *
 * Паттерн работы:
 * 1. Пользователь нажимает кнопку в QuickActionsBottomSheet → fastAttack*(nick)
 * 2. fastStart(weapon, nick) устанавливает AppVars.FastNeed = true
 * 3. WebView перезагружает main.php
 * 4. Filter.process() → MainPhp.process() → проверяет AppVars.FastNeed → processMainPhp(html)
 * 5. processMainPhp парсит HTML, генерирует форму с авто-submit → WebView отправляет POST
 */
public class FastActionManager {
    private static final String TAG = "FastActionManager";
    private static final String FAST_ID_BLISS_ELIXIR = "Эликсир Блаженства";
    private static volatile long lastBlissUseAtMs = 0L;
    private static volatile long prevBlissUseAtMs = 0L;
    private static final int FAST_INV_TRANSITION_MAX_RETRIES = 12;
    private static final String FAST_INV_RETRY_PARAM = "ab_fast_inv_retry";
    private static final int TELEPORT_DESTINATION_MIN_ID = 1;
    private static final int TELEPORT_DESTINATION_MAX_ID = 12;
    private static final int TELEPORT_DESTINATION_DEFAULT_ID = 1;
    private static final String TELEPORT_DESTINATION_DEFAULT_NAME = "\u0413\u043E\u0440\u043E\u0434 \u0424\u043E\u0440\u043F\u043E\u0441\u0442";
    private static final int[] TELEPORT_DESTINATION_IDS = new int[] {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
    };
    private static final String[] TELEPORT_DESTINATION_NAMES = new String[] {
            "\u0413\u043E\u0440\u043E\u0434 \u0424\u043E\u0440\u043F\u043E\u0441\u0442",
            "\u0413\u043E\u0440\u043E\u0434 \u041E\u043A\u0442\u0430\u043B",
            "\u0414\u0435\u0440\u0435\u0432\u043D\u044F \u041F\u043E\u0434\u0433\u043E\u0440\u043D\u0430\u044F",
            "\u041E\u043A\u0440\u0435\u0441\u0442\u043D\u043E\u0441\u0442\u044C \u0424\u0435\u0439\u0434\u0430\u043D\u0430, \u0422\u0435\u043B\u0435\u043F\u043E\u0440\u0442",
            "\u041E\u043A\u0440\u0435\u0441\u0442\u043D\u043E\u0441\u0442\u044C \u041E\u043A\u0442\u0430\u043B\u0430, \u0422\u0435\u043B\u0435\u043F\u043E\u0440\u0442",
            "\u041E\u043A\u0440\u0435\u0441\u0442\u043D\u043E\u0441\u0442\u0438 \u042D\u0440\u0438\u043D\u0433\u0440\u0430\u0434\u0430, \u0422\u0435\u043B\u0435\u043F\u043E\u0440\u0442",
            "\u041E\u043A\u0440\u0435\u0441\u0442\u043D\u043E\u0441\u0442\u044C \u0424\u043E\u0440\u043F\u043E\u0441\u0442\u0430, \u0422\u0435\u043B\u0435\u043F\u043E\u0440\u0442",
            "\u041F\u0443\u0441\u0442\u044B\u043D\u044F \u0421\u0430\u043C\u0443\u043C-\u0411\u0435\u0439\u0442, \u0422\u0435\u043B\u0435\u043F\u043E\u0440\u0442",
            "\u0421\u0435\u0432\u0435\u0440\u0441\u043A\u0438\u0439 \u0422\u0440\u0430\u043A\u0442, \u0422\u0435\u043B\u0435\u043F\u043E\u0440\u0442",
            "\u0412\u043E\u0441\u0442\u043E\u0447\u043D\u044B\u0435 \u041B\u0435\u0441\u0430, \u0422\u0435\u043B\u0435\u043F\u043E\u0440\u0442",
            "\u041E\u043A\u0440\u0435\u0441\u0442\u043D\u043E\u0441\u0442\u0438 \u041A\u0435\u043D\u0434\u0436\u0438\u0438, \u0422\u0435\u043B\u0435\u043F\u043E\u0440\u0442",
            "\u0423\u0449\u0435\u043B\u044C\u0435 \u042D\u043B\u044C-\u0422\u044D\u0440, \u0422\u0435\u043B\u0435\u043F\u043E\u0440\u0442"
    };
    private static volatile int selectedTeleportDestinationId = TELEPORT_DESTINATION_DEFAULT_ID;
    private static volatile String selectedTeleportDestinationName = TELEPORT_DESTINATION_DEFAULT_NAME;

    public static final class TeleportDestination {
        private final int id;
        private final String name;

        public TeleportDestination(int id, String name) {
            this.id = id;
            this.name = name == null ? "" : name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Bridge для fast-ветки postfilter, выполняющей навигацию по инвентарю.
     *
     * Назначение:
     * - хранить FastAction-логику в `FastActionManager`, не раздувая `MainPhp`;
     * - использовать существующие helper-методы `MainPhp` без дублирования алгоритмов.
     *
     * Правило:
     * - бизнес-решения fast-ветки (retry/cancel/успех) принимает `FastActionManager`;
     * - инфраструктурные функции страницы/URL предоставляет host.
     */
    public interface MainPhpFastHost {
        boolean isAttackFastId(String fastId);
        String getInventoryFilter(String fastId);
        boolean isFightFrameHtml(String html);
        String mainPhpFindInvWithFallback(String html, String filter, String address);
        boolean mainPhpIsInv(String html);
        boolean isInventoryAddress(String address);
        boolean inventoryAddressMatchesFilter(String address, String filter);
        int parseUrlParamInt(String url, String paramName, int fallback);
        String appendOrReplaceUrlParam(String url, String paramName, String paramValue);
        String buildFastItemNotFoundMessage(String fastId);
        void sendInventoryChatMessage(String messageHtml);
    }

    // Стандартная HTML-шапка для генерируемых страниц (аналог HelperErrors.Head() в C#).
    // Содержит GENERATED_PAGE_MARKER чтобы injectJsFix НЕ добавлял стубы в эти страницы.
    private static final String HTML_HEAD = HtmlUtils.GENERATED_PAGE_MARKER +
            "<html><head><meta http-equiv=\"Content-Type\" " +
            "content=\"text/html; charset=windows-1251\"></head><body>";

    /**
     * Снимок runtime-состояния небоевых авто-конвейеров на момент старта FastAction.
     *
     * Зачем нужен:
     * - при выполнении FastAction часть небоевых авто-веток ставится на паузу флагом
     *   `AppVars.FastPauseNonCombatAutoFunctions`;
     * - отдельные ветки могли сбрасывать runtime-навигацию (AutoMoving*), после чего
     *   маршрут/поиск клада не продолжался автоматически;
     * - snapshot позволяет детерминированно вернуть рабочий runtime-контекст после fastCancel.
     *
     * Важно:
     * - `Авто-Бой` сюда не входит и не паузится;
     * - это именно runtime-слой (не постоянные настройки профиля).
     */
    private static final class FastAutoSyncSnapshot {
        boolean autoMoving;
        String autoMovingDestination;
        ru.neverlands.abclient.utils.MapPath autoMovingMapPath;
        String autoMovingNextJump;
        int autoMovingJumps;
        ru.neverlands.abclient.model.CityGateType autoMovingCityGate;
        boolean autoDrinkBlazPending;
        long capturedAtMs;
    }

    private static volatile FastAutoSyncSnapshot fastAutoSyncSnapshot = null;

    /**
     * Фиксирует текущий runtime-контекст небоевых авто-функций до запуска FastAction.
     *
     * Условия:
     * - снимаем snapshot только для верхнего fast-контура (`FastNeed=false` до старта),
     *   чтобы вложенные/повторные fastStart не перетирали исходное состояние.
     */
    private static void captureNonCombatAutoSnapshotBeforeFast(String reason) {
        FastAutoSyncSnapshot snapshot = new FastAutoSyncSnapshot();
        snapshot.autoMoving = AppVars.AutoMoving;
        snapshot.autoMovingDestination = AppVars.AutoMovingDestinaton;
        snapshot.autoMovingMapPath = AppVars.AutoMovingMapPath;
        snapshot.autoMovingNextJump = AppVars.AutoMovingNextJump;
        snapshot.autoMovingJumps = AppVars.AutoMovingJumps;
        snapshot.autoMovingCityGate = AppVars.AutoMovingCityGate;
        snapshot.autoDrinkBlazPending = AppVars.AutoDrinkBlazPending;
        snapshot.capturedAtMs = System.currentTimeMillis();
        fastAutoSyncSnapshot = snapshot;
        Log.d(TAG, "FAST_SYNC_TRACE capture: reason=" + reason
                + ", autoMoving=" + snapshot.autoMoving
                + ", destination=" + snapshot.autoMovingDestination
                + ", jumps=" + snapshot.autoMovingJumps
                + ", blazPending=" + snapshot.autoDrinkBlazPending);
    }

    /**
     * Восстанавливает runtime-контекст после завершения FastAction.
     *
     * Правила восстановления:
     * - маршрут AutoMoving возвращается только если был активен до fast и пользователь
     *   не выключил `DoSearchBox` в процессе;
     * - `AutoDrinkBlazPending` возвращается в исходное значение;
     * - snapshot одноразовый и очищается сразу после попытки restore.
     */
    private static void restoreNonCombatAutoSnapshotAfterFast(String reason) {
        FastAutoSyncSnapshot snapshot = fastAutoSyncSnapshot;
        fastAutoSyncSnapshot = null;
        if (snapshot == null) {
            Log.d(TAG, "FAST_SYNC_TRACE restore skipped: reason=" + reason + ", snapshot is null");
            return;
        }

        if (!snapshot.autoMoving) {
            Log.d(TAG, "FAST_SYNC_TRACE restore skipped: reason=" + reason
                    + ", snapshot.autoMoving=false");
        } else if (AppVars.AutoMoving) {
            Log.d(TAG, "FAST_SYNC_TRACE restore skipped: reason=" + reason
                    + ", autoMoving already active");
        } else if (!AppVars.DoSearchBox) {
            Log.d(TAG, "FAST_SYNC_TRACE restore skipped: reason=" + reason
                    + ", DoSearchBox disabled by user");
        } else {
            AppVars.AutoMoving = true;
            AppVars.AutoMovingDestinaton = snapshot.autoMovingDestination;
            AppVars.AutoMovingMapPath = snapshot.autoMovingMapPath;
            AppVars.AutoMovingNextJump = snapshot.autoMovingNextJump;
            AppVars.AutoMovingJumps = snapshot.autoMovingJumps;
            AppVars.AutoMovingCityGate = snapshot.autoMovingCityGate;
            Log.d(TAG, "FAST_SYNC_TRACE restore AutoMoving: reason=" + reason
                    + ", destination=" + AppVars.AutoMovingDestinaton
                    + ", jumps=" + AppVars.AutoMovingJumps);
        }

        AppVars.AutoDrinkBlazPending = snapshot.autoDrinkBlazPending;
        Log.d(TAG, "FAST_SYNC_TRACE restore done: reason=" + reason
                + ", autoMovingNow=" + AppVars.AutoMoving
                + ", doSearchBoxNow=" + AppVars.DoSearchBox
                + ", blazPendingNow=" + AppVars.AutoDrinkBlazPending
                + ", snapshotAgeMs=" + (System.currentTimeMillis() - snapshot.capturedAtMs));
    }

    // --- Часть 1: Управление (из FormMainFast.cs) ---

    /**
     * Запуск быстрого действия (аналог FastStartSafe в C#).
     * Устанавливает глобальные переменные и инициирует перезагрузку main.php.
     */
    // Упрощённый вызов: одно действие без "перенаправления" (count=1).
    public static void fastStart(String id, String nick) {
        fastStart(id, nick, 1);
    }

    public static void fastStart(String id, String nick, int count) {
        boolean prevFastNeed = AppVars.FastNeed;
        String prevFastId = AppVars.FastId;
        String prevFastNick = AppVars.FastNick;
        boolean prevPauseNonCombatAuto = AppVars.FastPauseNonCombatAutoFunctions;
        if (!prevFastNeed) {
            captureNonCombatAutoSnapshotBeforeFast("fastStart:" + id);
        }
        // Глобальные флаги, которые считывает MainPhp.process() при обработке main.php.
        AppVars.FastNeed = true;
        AppVars.FastId = id;
        AppVars.FastNick = nick;
        AppVars.FastCount = count;
        AppVars.FastPauseNonCombatAutoFunctions = true;
        AppVars.FastReturnToMapPending = true;
        Log.d(TAG, "fastStart: id=" + id + ", nick=" + nick + ", count=" + count);
        Log.d(TAG, "[AA_TRACE] fastStart state: prevFastNeed=" + prevFastNeed
                + ", prevFastId=" + prevFastId
                + ", prevFastNick=" + prevFastNick
                + ", prevPauseNonCombatAuto=" + prevPauseNonCombatAuto
                + ", newFastNeed=" + AppVars.FastNeed
                + ", newFastId=" + AppVars.FastId
                + ", newFastNick=" + AppVars.FastNick
                + ", newPauseNonCombatAuto=" + AppVars.FastPauseNonCombatAutoFunctions);
        // Запускаем цепочку через reload main.php (как в ПК версии).
        reloadMainFrame();
    }

    /**
     * Отмена быстрого действия (аналог FastCancelSafe в C#).
     */
    public static void fastCancel() {
        fastCancel("unspecified");
    }

    /**
     * Отмена быстрого действия с указанием причины.
     *
     * Зависимости:
     * - `AppVars.Fast*` (сброс глобального состояния fast-конвейера),
     * - `AppVars.FastWaitEndOfBoi*` (останавливает фон ожидания конца боя).
     * Используется для детальной трассировки цепочки авто-нападения/автобоя.
     */
    public static void fastCancel(String reason) {
        boolean oldFastNeed = AppVars.FastNeed;
        String oldFastId = AppVars.FastId;
        String oldFastNick = AppVars.FastNick;
        int oldFastCount = AppVars.FastCount;
        boolean oldPauseNonCombatAuto = AppVars.FastPauseNonCombatAutoFunctions;
        // Полный сброс параметров быстрого действия.
        AppVars.FastNeed = false;
        AppVars.FastNick = null;
        AppVars.FastId = null;
        AppVars.FastCount = 0;
        AppVars.FastPauseNonCombatAutoFunctions = false;
        AppVars.FastNeedAbilDarkTeleport = false;
        AppVars.FastNeedAbilDarkFog = false;

        if (AppVars.FastWaitEndOfBoiActive) {
            // Если активен фон ожидания конца боя — запрашиваем отмену.
            AppVars.FastWaitEndOfBoiCancel = true;
        }
        Log.d(TAG, "fastCancel");
        Log.d(TAG, "[AA_TRACE] fastCancel reason=" + reason
                + ", oldFastNeed=" + oldFastNeed
                + ", oldFastId=" + oldFastId
                + ", oldFastNick=" + oldFastNick
                + ", oldFastCount=" + oldFastCount
                + ", oldPauseNonCombatAuto=" + oldPauseNonCombatAuto
                + ", newPauseNonCombatAuto=" + AppVars.FastPauseNonCombatAutoFunctions
                + ", returnToMapPending=" + AppVars.FastReturnToMapPending);
        restoreNonCombatAutoSnapshotAfterFast("fastCancel:" + reason);
    }

    /**
     * Убирает теги <i></i> из ника (аналог StripItalic в C#).
     */
    public static String stripItalic(String nick) {
        if (nick == null) return "";
        return nick.replace("<i>", "").replace("</i>", "").trim();
    }

    /**
     * Возвращает ник текущего персонажа для self-use действий,
     * где нужно явно подставить собственного персонажа.
     */
    private static String getProfileNickOrEmpty() {
        if (AppVars.Profile == null || AppVars.Profile.UserNick == null) {
            return "";
        }
        return AppVars.Profile.UserNick.trim();
    }

    // --- Методы быстрых атак (из FormMainFast.cs) ---
    // Каждый метод устанавливает weapon (=FastId) и вызывает fastStart

    /** Обычная нападалка (аналог FormMain.FastAttack) */
    public static void fastAttack(String nick) {
        fastStart("i_svi_001.gif", stripItalic(nick));
    }

    /** Кровавая нападалка (аналог FormMain.FastAttackBlood) */
    public static void fastAttackBlood(String nick) {
        fastStart("i_svi_002.gif", stripItalic(nick));
    }

    /** Боевая нападалка (аналог FormMain.FastAttackUltimate) */
    public static void fastAttackUltimate(String nick) {
        fastStart("i_w28_26.gif", stripItalic(nick));
    }

    /** Закрытая боевая нападалка (аналог FormMain.FastAttackClosedUltimate) */
    public static void fastAttackClosedUltimate(String nick) {
        fastStart("i_w28_26X.gif", stripItalic(nick));
    }

    /** Закрытая нападалка (аналог FormMain.FastAttackClosed) */
    public static void fastAttackClosed(String nick) {
        fastStart("i_svi_205.gif", stripItalic(nick));
    }

    /** Обычная кулачка (аналог FormMain.FastAttackFist) */
    public static void fastAttackFist(String nick) {
        fastStart("i_w28_24.gif", stripItalic(nick));
    }

    /** Закрытая кулачка (аналог FormMain.FastAttackClosedFist) */
    public static void fastAttackClosedFist(String nick) {
        fastStart("i_w28_25.gif", stripItalic(nick));
    }

    /** Туман (аналог FormMain.FastAttackFog) — без ожидания боя */
    public static void fastAttackFog(String nick) {
        fastStart("i_svi_213.gif", stripItalic(nick));
    }

    /** Яд (аналог FormMain.FastAttackPoison) */
    public static void fastAttackPoison(String nick) {
        fastStart("Яд", stripItalic(nick));
    }

    /** Сильная спина (аналог FormMain.FastAttackStrong) */
    public static void fastAttackStrong(String nick) {
        fastStart("Зелье Сильной Спины", stripItalic(nick));
    }

    /** Сильная спина с приоритетом "Превосходное" (если есть в инвентаре). */
    // Auto-attack strong-back variant with priority for "Превосходное ...".
    // Dependencies:
    // - fastStart(...): launches unified fast-action pipeline.
    // - mainPhpFastPotion(...): resolves potion in HTML and sends POST.
    // Fallback to regular "Зелье Сильной Спины" is implemented in mainPhpFastPotion(...).
    public static void fastAttackStrongBest(String nick) {
        fastStart("Превосходное Зелье Сильной Спины", stripItalic(nick));
    }

    /** Невидимость (аналог FormMain.FastAttackNevidPot) */
    public static void fastAttackNevidPot(String nick) {
        fastStart("Зелье Невидимости", stripItalic(nick));
    }

    /** Портал (аналог FormMain.FastAttackPortal) */
    public static void fastAttackPortal(String nick) {
        fastStart("i_w28_86.gif", stripItalic(nick));
    }

    /**
     * Выполняет авто-нападение по выбранному toolId (аналог switch в C# RoomManager.cs).
     *
     * Значения toolId:
     * 1 - боевые, 2 - закрытые боевые, 3 - кулачки, 4 - закрытые кулачки, 5 - портал, 6 - яд, 7 - сильная спина.
     *
     * @return true, если toolId поддержан и действие запущено.
     */
    // Dispatcher for per-contact/global auto-attack tool selection.
    // Dependencies:
    // - RoomManager: resolves final toolId priority (contact > global).
    // - ContactsManager/AppVars: provide source tool settings.
    // - processMainPhp(...): executes selected tool through parsed HTML forms.
    public static boolean fastAttackAutoByToolId(String nick, int toolId) {
        Log.d(TAG, "[AA_TRACE] fastAttackAutoByToolId: nick=" + nick + ", toolId=" + toolId);
        switch (toolId) {
            case 1:
                fastAttackUltimate(nick);
                Log.d(TAG, "[AA_TRACE] fastAttackAutoByToolId: started fastAttackUltimate");
                return true;
            case 2:
                fastAttackClosedUltimate(nick);
                Log.d(TAG, "[AA_TRACE] fastAttackAutoByToolId: started fastAttackClosedUltimate");
                return true;
            case 3:
                fastAttackFist(nick);
                Log.d(TAG, "[AA_TRACE] fastAttackAutoByToolId: started fastAttackFist");
                return true;
            case 4:
                fastAttackClosedFist(nick);
                Log.d(TAG, "[AA_TRACE] fastAttackAutoByToolId: started fastAttackClosedFist");
                return true;
            case 5:
                fastAttackPortal(nick);
                Log.d(TAG, "[AA_TRACE] fastAttackAutoByToolId: started fastAttackPortal");
                return true;
            case 6:
                fastAttackPoison(nick);
                Log.d(TAG, "[AA_TRACE] fastAttackAutoByToolId: started fastAttackPoison");
                return true;
            case 7:
                fastAttackStrongBest(nick);
                Log.d(TAG, "[AA_TRACE] fastAttackAutoByToolId: started fastAttackStrongBest");
                return true;
            default:
                Log.w(TAG, "[AA_TRACE] fastAttackAutoByToolId: unsupported toolId=" + toolId);
                return false;
        }
    }

    /** Защита (аналог FormMain.FastAttackZas) */
    public static void fastAttackZas(String nick) {
        fastStart("i_w28_27.gif", stripItalic(nick));
    }

    /** Телепорт (аналог FormMain.FastAttackTeleport) — wsubid=22, post_id=25 */
    public static void fastAttackTeleport(String nick) {
        fastStart("i_w28_22.gif", stripItalic(nick));
    }

    /**
     * Start teleport using selected destination id/name from quick UI.
     */
    public static void fastAttackTeleportToDestination(int destinationId, String destinationName) {
        int safeId = sanitizeTeleportDestinationId(destinationId);
        selectedTeleportDestinationId = safeId;
        selectedTeleportDestinationName = resolveTeleportDestinationName(safeId, destinationName);
        fastStart("i_w28_22.gif", "");
    }

    public static TeleportDestination[] getTeleportDestinations() {
        TeleportDestination[] items = new TeleportDestination[TELEPORT_DESTINATION_IDS.length];
        for (int i = 0; i < TELEPORT_DESTINATION_IDS.length; i++) {
            items[i] = new TeleportDestination(TELEPORT_DESTINATION_IDS[i], TELEPORT_DESTINATION_NAMES[i]);
        }
        return items;
    }

    public static int getTeleportDestinationId() {
        return selectedTeleportDestinationId;
    }

    public static String getTeleportDestinationName() {
        return selectedTeleportDestinationName;
    }

    private static int sanitizeTeleportDestinationId(int destinationId) {
        if (destinationId < TELEPORT_DESTINATION_MIN_ID || destinationId > TELEPORT_DESTINATION_MAX_ID) {
            return TELEPORT_DESTINATION_DEFAULT_ID;
        }
        return destinationId;
    }

    private static String resolveTeleportDestinationName(int destinationId, String fallbackName) {
        int index = destinationId - 1;
        if (index >= 0 && index < TELEPORT_DESTINATION_NAMES.length) {
            return TELEPORT_DESTINATION_NAMES[index];
        }
        if (fallbackName != null && !fallbackName.trim().isEmpty()) {
            return fallbackName.trim();
        }
        return TELEPORT_DESTINATION_DEFAULT_NAME;
    }

    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Саморассеивание (аналог FormMain.FastAttackSelfRass) — wsubid=23, без pnick */
    public static void fastAttackSelfRass() {
        fastStart("i_w28_23.gif", "себя");
    }

    /** Обнаружение (аналог FormMain.FastAttackOpenNevid) — wsubid=28, без pnick */
    public static void fastAttackOpenNevid() {
        fastStart("i_w28_28.gif", "клетке");
    }

    /** Тотем (аналог FormMain.FastAttackTotem) */
    public static void fastAttackTotem(String nick) {
        fastStart("Тотем", stripItalic(nick));
    }

    /** Остров телепорт (аналог FormMain.FastAttackIslandPot) — на себя */
    public static void fastAttackIslandPot() {
        fastStart("Телепорт (Остров Туротор)", getProfileNickOrEmpty());
    }

    /** Эликсир Блаженства (аналог FormMain.FastAttackBlazElixir) — на себя */
    public static void fastAttackBlazElixir() {
        // Эликсиры с вкладки im=6 применяются GET-ссылкой без поля nickname.
        fastStart("Эликсир Блаженства", "");
    }

    /** Эликсир Мгновенного Исцеления (аналог FormMain.FastAttackMomentCureElixir) — на себя */
    public static void fastAttackMomentCureElixir() {
        // Эликсиры с вкладки im=6 применяются GET-ссылкой без поля nickname.
        fastStart("Эликсир Мгновенного Исцеления", "");
    }

    /** Эликсир Восстановления (аналог FormMain.FastAttackMomentRestoreElixir) — на себя */
    public static void fastAttackMomentRestoreElixir() {
        // Эликсиры с вкладки im=6 применяются GET-ссылкой без поля nickname.
        fastStart("Эликсир Восстановления", "");
    }

    // --- Часть 1b: FastAttackAsync — фоновый поток ожидания окончания боя ---

    /**
     * Запускает быстрое действие с ожиданием окончания боя цели (аналог FormMainFast.FastAttackAsync в C#).
     *
     * Алгоритм:
     *  1. NeverApi.getAll(nick) → получаем fightLog (ID боя цели)
     *  2. Если fightLog не пустой — опрашиваем logs.fcg?fid=X до "var off = 1;"
     *  3. После окончания боя (или если цель не в бою) → fastStart + reloadMainFrame
     *
     * @param weapon  ID предмета или название (например "i_svi_001.gif", "Тотем")
     * @param nick    ник цели (уже без итальянских тегов)
     */
    public static void fastAttackAsync(final String weapon, final String nick) {
        // Фон: не блокируем UI при ожидании конца боя цели.
        new Thread(() -> fastAttackAsyncImpl(weapon, nick), "FastAttackAsync").start();
    }

    private static void fastAttackAsyncImpl(String weapon, String nick) {
        // Основной поток логики ожидания конца боя и последующего fastStart.
        Log.d(TAG, "fastAttackAsync: weapon=" + weapon + ", nick=" + nick);

        // 1. Получаем информацию о цели
        NeverApi.UserInfo userInfo = NeverApi.getAll(nick);
        if (userInfo == null) {
            writeChatMsg("<font color=#FF0000>Ошибка анализа инфы атакуемого.</font>");
            return;
        }

        String flog = userInfo.fightLog; // "" если не в бою

        // 2. Если цель в бою — ждём окончания
        if (!flog.isEmpty()) {
            int scans = 0;
            long startMs = System.currentTimeMillis();
            AppVars.FastWaitEndOfBoiCancel = false;
            AppVars.FastWaitEndOfBoiActive = true;

            Log.d(TAG, "fastAttackAsync: цель в бою flog=" + flog + ", начинаем ожидание");

            while (!AppVars.FastWaitEndOfBoiCancel) {
                String html = NeverApi.getFlog(flog);
                if (html == null || html.isEmpty()) continue;

                scans++;

                // Условие окончания 1: "var off = 1;" в HTML лога боя
                String off = ru.neverlands.abclient.utils.HelperStrings.subString(html, "var off = ", ";");
                if (off == null) continue;

                if (off.equals("1")) {
                    Log.d(TAG, "fastAttackAsync: бой завершён (off=1), scans=" + scans);
                    break;
                }

                // Условие окончания 2: открытый бой + WaitOpen=false → не ждём
                if (!AppVars.WaitOpen) {
                    boolean closedFight = html.contains("нападение бота")
                            || html.contains("закрытый бой")
                            || html.contains("закрытое нападение")
                            || html.contains("закрытое кулачное нападение")
                            || html.contains("закрытое боевое нападение");
                    if (!closedFight) {
                        Log.d(TAG, "fastAttackAsync: открытый бой, WaitOpen=false → не ждём");
                        break;
                    }
                }

                // Сообщения о прогрессе (аналог C#)
                if (scans == 1) {
                    writeChatMsg("Ожидание окончания боя (отмена: меню → быстрые действия → отмена).");
                } else if (scans % 100 == 0) {
                    long avgMs = (System.currentTimeMillis() - startMs) / scans;
                    writeChatMsg("Ожидание окончания боя (запросов: " + scans + ", средн: " + avgMs + "мс)");
                }
            }
        }

        // Завершаем цикл ожидания окончания боя.
        AppVars.FastWaitEndOfBoiActive = false;

        if (AppVars.FastWaitEndOfBoiCancel) {
            AppVars.FastWaitEndOfBoiCancel = false;
            writeChatMsg("Ожидание окончания боя прекращено.");
            Log.d(TAG, "fastAttackAsync: отменено пользователем");
            return;
        }

        // 4. Бой закончился (или цель не была в бою) → запускаем быстрое действие
        // fastStart уже вызывает reloadMainFrame() внутри себя
        Log.d(TAG, "fastAttackAsync: армируем действие weapon=" + weapon + " nick=" + nick);
        int count = AppVars.DoPerenap ? Integer.MAX_VALUE : 1;
        fastStart(weapon, nick, count);
    }

    /**
     * Отправляет сообщение в чат через LocalBroadcast (аналог WriteChatMsgSafe в C#).
     */
    static void writeChatMsg(String message) {
        android.content.Context ctx = AppVars.getContext();
        if (ctx == null) return;
        // Отправляем системное сообщение через LocalBroadcast, слушатель — Chat/WebView.
        android.content.Intent intent = new android.content.Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", message);
        androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(ctx).sendBroadcast(intent);
    }

    /**
     * Отменяет ожидание боя (аналог FastCancelSafe в C#).
     * Вызывается из UI при нажатии кнопки отмены.
     */
    /**
     * Выполняет fast-конвейер MainPhp-уровня (переход в инвентарь → нужная вкладка → применение предмета).
     *
     * Это прямой перенос логики `MainPhp.processMainPhpFast(...)` в `FastActionManager`:
     * - без изменения алгоритма;
     * - с теми же условиями отмены/ретраев/детектов;
     * - с делегированием инфраструктурных helper'ов через `MainPhpFastHost`.
     *
     * Зависимости:
     * - `AppVars.FastNeed/FastId/FastNick/NeverTimer/FastCount`;
     * - `processMainPhp(...)` — low-level парсер конкретного fast-предмета;
     * - `Filter.buildRedirect(...)` и `Russian.getBytes(...)` — возврат HTML в WebView.
     *
     * @return `byte[]` с HTML-ответом postfilter или `null`, если fast-ветка не обработана.
     */
    public static byte[] processMainPhpFast(String address, String html, MainPhpFastHost host) {
        if (host == null || !AppVars.FastNeed || AppVars.FastId == null) return null;
        String fastId = AppVars.FastId;
        Log.d(TAG, "processMainPhpFast: FastId=" + fastId + ", address=" + address);

        // NeverTimer — cooldown (аналог DateTime.Now > AppVars.NeverTimer в C#)
        boolean requireNeverTimerForFast = host.isAttackFastId(fastId);
        if (requireNeverTimerForFast && AppVars.NeverTimer > 0 && System.currentTimeMillis() < AppVars.NeverTimer) {
            Log.d(TAG, "processMainPhpFast: NeverTimer ещё не истёк, пропускаем (attack-fast)");
            return null;
        }

        // --- Особый случай: get_id=43 — это страница применения эликсира/предмета.
        // Сервер уже применил действие (по GET-запросу), поэтому FastNeed нужно сбросить.
        // Иначе мы будем бесконечно перезапускать процесс.
        if (address != null && address.contains("get_id=43")) {
            if (FAST_ID_BLISS_ELIXIR.equals(fastId)) {
                Log.d(TAG, "processMainPhpFast: get_id=43 — подтверждён Эликсир Блаженства");
                rememberBlissUseTimestamp(fastId);
                if (shouldEmitFastResultMessage(AppVars.FastCount)) {
                    Integer elixirRemainingAfterUse = resolveElixirRemainingFromInventoryCache(fastId, html);
                    writeChatMsg(buildFastResultMessage(fastId, AppVars.FastNick, elixirRemainingAfterUse));
                    String autoTreasureEtaMessage = buildAutoTreasureBlissEtaMessage(fastId, elixirRemainingAfterUse);
                    if (autoTreasureEtaMessage != null) {
                        writeChatMsg(autoTreasureEtaMessage);
                    }
                }
                AppVars.FastCount--;
                if (AppVars.FastCount <= 0) {
                    fastCancel("fast-get_id=43-action-applied");
                }
                return null;
            }
            Log.d(TAG, "processMainPhpFast: get_id=43 — действие уже выполнено, сбрасываем FastNeed");
            fastCancel("fast-get_id=43-action-already-applied");
            return null;
        }

        // Если fast-атака уже привела нас в бой (fight frame), дальнейший поиск инвентаря
        // становится бессмысленным и только мешает автобою.
        // Сценарий:
        // 1) Авто-нападение стартует из комнаты -> FastNeed=true.
        // 2) Сервер переводит в fight.frame.
        // 3) Мы продолжаем крутить processMainPhpFast на каждом обновлении боя.
        // В C# после входа в бой fast-цикл для нападалки фактически завершён.
        if (host.isFightFrameHtml(html)
                && address != null
                && address.contains("get_id=56&act=10&go=inf")
                && host.isAttackFastId(fastId)) {
            Log.d(TAG, "processMainPhpFast: вошли в бой с FastId=" + fastId
                    + ", сбрасываем FastNeed чтобы не блокировать авто-удары");
            fastCancel("entered-fight-frame-attack-fastid");
            return null;
        }

        String filter = host.getInventoryFilter(fastId);
        if (filter == null) {
            Log.w(TAG, "processMainPhpFast: неизвестный FastId=" + fastId);
            return null;
        }
        Log.d(TAG, "processMainPhpFast: filter=" + filter
                + ", isInv=" + host.mainPhpIsInv(html)
                + ", isInvByAddress=" + host.isInventoryAddress(address)
                + ", w28_form=" + (html != null && html.contains("w28_form("))
                + ", magicreform=" + (html != null && html.contains("magicreform(")));

        // --- Особый случай: Тотем НЕ требует инвентаря ---
        // В C# тотем ищет ["fig","Напасть","vcode"] на основной странице.
        // mainPhpFindFlora делает redirect на основную страницу, если нужно.
        if ("TOTEM".equals(filter)) {
            Log.d(TAG, "processMainPhpFast: тотем — без навигации на инвентарь");
            String fastHtml = processMainPhp(html);
            if (fastHtml != null) {
                Log.d(TAG, "processMainPhpFast: УСПЕХ, тотем найден");
                return Russian.getBytes(fastHtml);
            }
            Log.w(TAG, "processMainPhpFast: тотем не найден, отмена");
            fastCancel("inventory-fast-item-not-found");
            return null;
        }

        // 1. Если мы НЕ на инвентаре — ищем ссылку на инвентарь с фильтром
        String invRedirect = host.mainPhpFindInvWithFallback(html, filter, address);
        if (invRedirect != null) {
            Log.d(TAG, "processMainPhpFast: redirect на инвентарь: " + invRedirect);
            return Russian.getBytes(invRedirect);
        }

        // 2. Если мы НА инвентаре — проверяем категорию и ищем предмет
        if (host.mainPhpIsInv(html) || host.isInventoryAddress(address)) {
            String filterClean = filter.startsWith("&") ? filter.substring(1) : filter;
            // 2a. Сначала проверяем, на правильной ли мы вкладке категории.
            // Если address не содержит нужный фильтр (wca=28/wca=27),
            // перенаправляем на нужную категорию ПЕРЕД поиском предмета.
            // Это критично при 500+ предметах в инвентаре — поиск по всему
            // HTML (695KB) вместо отфильтрованной страницы (28KB) слишком медленный.
            if (!host.inventoryAddressMatchesFilter(address, filter)) {
                Log.d(TAG, "processMainPhpFast: на инвентаре, но не на нужной категории ("
                        + filterClean + "), переключаем");
                return Filter.buildRedirect("Переключение на нужную категорию", "main.php?" + filterClean);
            }

            // 2b. Мы на правильной вкладке — ищем предмет
            String fastHtml = processMainPhp(html);
            if (fastHtml != null) {
                Log.d(TAG, "processMainPhpFast: УСПЕХ, предмет найден");
                return Russian.getBytes(fastHtml);
            }

            // На части ответов go=inv сервер возвращает переходный HTML без формы предмета
            // (mainPhpIsInv=false), хотя адрес уже указывает на нужную вкладку.
            // В этом состоянии не отменяем FastAction — ждём следующий полноценный кадр.
            if (!host.mainPhpIsInv(html)) {
                String retryLink = address;
                if (retryLink == null || retryLink.isEmpty()) {
                    retryLink = "main.php?" + filterClean;
                }
                int currentRetry = host.parseUrlParamInt(retryLink, FAST_INV_RETRY_PARAM, 0);
                if (currentRetry >= FAST_INV_TRANSITION_MAX_RETRIES) {
                    String fallbackInvUrl = "main.php?" + filterClean;
                    Log.w(TAG, "processMainPhpFast: inventory transitional HTML retry limit reached ("
                            + currentRetry + "/" + FAST_INV_TRANSITION_MAX_RETRIES
                            + "), cancel fast action and force inventory reload: " + fallbackInvUrl);
                    fastCancel("inventory-fast-transition-timeout");
                    return Filter.buildRedirect("Инвентарь загружается слишком долго, сбрасываем fast-действие", fallbackInvUrl);
                }
                int nextRetry = currentRetry + 1;
                retryLink = host.appendOrReplaceUrlParam(retryLink, FAST_INV_RETRY_PARAM, String.valueOf(nextRetry));
                Log.d(TAG, "processMainPhpFast: inventory transitional HTML, retry="
                        + nextRetry + "/" + FAST_INV_TRANSITION_MAX_RETRIES + ", url=" + retryLink);
                return Filter.buildRedirect("Ожидание загрузки инвентаря (" + nextRetry
                        + "/" + FAST_INV_TRANSITION_MAX_RETRIES + ")", retryLink);
            }

            Log.w(TAG, "processMainPhpFast: предмет не найден на правильной вкладке (" + filterClean + "), отмена");
            // 3. Мы на правильной вкладке, предмет не найден — отмена
            host.sendInventoryChatMessage(host.buildFastItemNotFoundMessage(fastId));
            fastCancel("inventory-fast-unsupported-context");
            return null;
        }

        // Мы не на инвентаре и MainPhpFindInv не нашла ссылку — вероятно, нужен обычный reload
        Log.d(TAG, "processMainPhpFast: не на инвентаре, MainPhpFindInv не нашла ссылку");
        return null;
    }

    public static void cancelWaitFight() {
        if (AppVars.FastWaitEndOfBoiActive) {
            AppVars.FastWaitEndOfBoiCancel = true;
            Log.d(TAG, "cancelWaitFight: запрос отмены ожидания");
        }
    }

    // --- Часть 2: Парсинг HTML (из PostFilter/MainPhpFast.cs) ---

    /**
     * Основной диспетчер (аналог MainPhpFast в C#).
     * Вызывается из MainPhp.process() когда AppVars.FastNeed == true.
     *
     * @param html HTML-содержимое страницы main.php
     * @return Сгенерированный HTML с авто-submit формой, или null если действие не найдено
     */
    // Парсит HTML main.php и формирует авто‑submit/redirect для быстрого действия.
    public static String processMainPhp(String html) {
        Log.d(TAG, "processMainPhp: FastNeed=" + AppVars.FastNeed + ", FastId=" + AppVars.FastId
                + ", FastNick=" + AppVars.FastNick + ", htmlLen=" + (html != null ? html.length() : 0));
        if (!AppVars.FastNeed || AppVars.FastId == null || html == null) return null;

        // Логируем наличие ключевых паттернов в HTML
        Log.d(TAG, "processMainPhp: contains w28_form=" + html.contains("w28_form(")
                + ", magicreform=" + html.contains("magicreform(")
                + ", abil_svitok=" + html.contains("abil_svitok("));

        String result = null;
        String fastId = AppVars.FastId;

        switch (fastId) {
            // Нападалки (w28_form парсинг)
            case "i_svi_001.gif":
                result = mainPhpFastHit(html, new String[]{"1", "2", "3", "4"}, "обычную нападалку");
                break;
            case "i_svi_002.gif":
                result = mainPhpFastHit(html, new String[]{"5", "6", "7", "8"}, "кровавую нападалку");
                break;
            case "i_w28_26.gif":
                result = mainPhpFastHit(html, new String[]{"26"}, "боевую нападалку");
                break;
            case "i_w28_26X.gif":
                result = mainPhpFastHit(html, new String[]{"29"}, "закрытую боевую нападалку");
                break;
            case "i_svi_205.gif":
                result = mainPhpFastHit(html, new String[]{"14"}, "закрытую нападалку");
                break;
            case "i_w28_24.gif":
                result = mainPhpFastHit(html, new String[]{"24"}, "кулачку");
                break;
            case "i_w28_25.gif":
                result = mainPhpFastHit(html, new String[]{"25"}, "закрытую кулачку");
                break;

            // Абилки
            case "i_svi_213.gif":
                result = mainPhpFastFog(html);
                break;
            case "i_w28_27.gif":
                result = mainPhpFastW28(html, "27", "свиток защиты к");
                break;
            case "i_w28_86.gif":
                result = mainPhpFastW28(html, "86", "портал на");
                break;
            case "i_w28_22.gif":
                result = mainPhpFastTeleport(html);
                break;

            // Самонацеленные свитки (без pnick)
            case "i_w28_23.gif": // Саморассеивание
                result = mainPhpFastW28Self(html, "23", "Применяем свиток рассеивания невидимости на себя");
                break;
            case "i_w28_28.gif": // Обнаружение
                result = mainPhpFastW28Self(html, "28", "Применяем свиток обнаружения");
                break;

            // Островной телепорт
            case "Телепорт (Остров Туротор)":
                result = mainPhpFastIsland(html);
                break;

            // Тотем (не требует инвентаря)
            case "Тотем":
                result = mainPhpFastTotem(html);
                break;

            // Эликсиры (GET redirect)
            case "Эликсир Блаженства":
            case "Эликсир Мгновенного Исцеления":
            case "Эликсир Восстановления":
                result = mainPhpFastElixir(html);
                break;

            // Зелья (magicreform парсинг)
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
                result = mainPhpFastPotion(html);
                break;

            default:
                Log.w(TAG, "processMainPhp: неизвестный FastId = " + fastId);
                break;
        }

        // Тотем/островной телепорт/эликсиры не используют fallback-навигацию по findTargetLink:
        // - для эликсиров навигацию по вкладкам уже ведет MainPhp.processMainPhpFast (im=6),
        // - дополнительный fallback здесь может создавать цикл переходов при отсутствии предмета.
        boolean noInventoryFallback = "Тотем".equals(fastId)
                || "Телепорт (Остров Туротор)".equals(fastId)
                || isElixirFastId(fastId);

        if (result == null && !noInventoryFallback && html.contains("get_id=56")) {
            Log.d(TAG, "processMainPhp: Предмет не найден, но мы в get_id=56. Ищем ссылку на нужный раздел.");
            String targetLink = findTargetLink(html, fastId);
            if (targetLink != null) {
                Log.d(TAG, "processMainPhp: Выполняем переход на: " + targetLink);
                return HTML_HEAD + "<script language=\"JavaScript\">location='" + targetLink + "';</script></body></html>";
            }
        }

        if (result != null) {
            boolean deferBlissChatUntilGetId43 = FAST_ID_BLISS_ELIXIR.equals(fastId);
            if (!deferBlissChatUntilGetId43) {
                rememberBlissUseTimestamp(fastId);
                if (shouldEmitFastResultMessage(AppVars.FastCount)) {
                    Integer elixirRemainingAfterUse = null;
                    if (isElixirFastId(fastId)) {
                        elixirRemainingAfterUse = resolveElixirRemainingFromInventoryCache(fastId, html);
                    }
                    writeChatMsg(buildFastResultMessage(fastId, AppVars.FastNick, elixirRemainingAfterUse));
                    String autoTreasureEtaMessage = buildAutoTreasureBlissEtaMessage(fastId, elixirRemainingAfterUse);
                    if (autoTreasureEtaMessage != null) {
                        writeChatMsg(autoTreasureEtaMessage);
                    }
                }

                // Действие выполнено, уменьшаем счётчик
                AppVars.FastCount--;
                if (AppVars.FastCount <= 0) {
                    fastCancel("fast-action-finished");
                }
            } else {
                Log.d(TAG, "processMainPhp: Эликсир Блаженства отправлен, ждём get_id=43 для подтверждения/сообщения");
            }
            Log.d(TAG, "processMainPhp: УСПЕХ для FastId=" + fastId + ", resultLen=" + result.length());
            Log.d(TAG, "processMainPhp: generated HTML: " + (result.length() > 300 ? result.substring(0, 300) : result));
        } else {
            Log.w(TAG, "processMainPhp: НЕУДАЧА, result=null для FastId=" + fastId);
        }

        return result;
    }

    /**
     * Решает, нужно ли писать итог fast-действия в чат.
     *
     * Зачем:
     * - при обычном count=1 пишем сообщение один раз;
     * - при finite count>1 пишем только на последнем шаге (когда remaining==1),
     *   чтобы не спамить чат;
     * - при DoPerenap (count=Integer.MAX_VALUE) пишем только первый раз.
     */
    private static boolean shouldEmitFastResultMessage(int remainingCountBeforeDecrement) {
        return remainingCountBeforeDecrement == Integer.MAX_VALUE || remainingCountBeforeDecrement <= 1;
    }

    /**
     * Формирует системное сообщение в чат о факте отправки fast-запроса.
     *
     * Важно:
     * - на этом этапе у нас есть только локальный факт "форма собрана/отправляется";
     * - серверный итог (успех/ошибка) приходит отдельным POST-ответом `main.php`.
     * Поэтому не используем формулировку "Выполнено", чтобы не вводить в заблуждение.
     *
     * Зависимости:
     * - `writeChatMsg(...)` — отправка через LocalBroadcast в чат,
     * - `resolveFastDisplayName(...)` — преобразование внутренних FastId в человекочитаемый текст.
     */
    private static String buildFastResultMessage(String fastId, String fastNick, Integer elixirRemainingAfterUse) {
        if ("i_w28_22.gif".equals(fastId)) {
            return buildServerChatTimeHtml()
                    + "<font color=#336699>\u0422\u0435\u043B\u043F\u043E\u0440\u0442: \u0412\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E \u0442\u0435\u043B\u0435\u043F\u043E\u0440\u0442\u0438\u0440\u043E\u0432\u0430\u043D\u0438\u0435 \u0432 <b>"
                    + escapeHtml(resolveTeleportDestinationName(selectedTeleportDestinationId, selectedTeleportDestinationName))
                    + "</b>.</font>";
        }
        String displayName = resolveFastDisplayName(fastId);
        String target = (fastNick == null || fastNick.trim().isEmpty()) ? "" : " на <b>" + fastNick.trim() + "</b>";
        String elixirRemainSuffix = "";
        if (isElixirFastId(fastId) && elixirRemainingAfterUse != null) {
            elixirRemainSuffix = " Остаток: <b><font color=#01A9DB>" + elixirRemainingAfterUse + "</font></b>";
        }
        return buildServerChatTimeHtml()
                + "<font color=#336699>Запрос отправлен: <b>" + displayName + "</b>" + target + "." + elixirRemainSuffix + "</font>";
    }

    /**
     * Возвращает HTML-суффикс вида ` Остаток: N` для сообщений об использовании эликсиров.
     *
     * Назначение:
     * - единый формат для разных контуров (`FastActionManager`, `MainPhp`), чтобы не дублировать
     *   парсинг остатка и верстку цветного суффикса в нескольких местах.
     *
     * Правила:
     * - при `adjustAfterUse = -1` можно показать прогноз "после текущего использования", если сообщение
     *   формируется до фактического server-submit;
     * - при `adjustAfterUse = 0` используется текущий снимок остатка из кеша инвентаря;
     * - если остаток определить нельзя, возвращается пустая строка.
     *
     * Зависимости:
     * - {@link #resolveElixirRemainingFromInventoryCache(String, String)} — источник остатка;
     * - `AppVars.InvList` / `MainPhp.syncInventoryCacheFromHtml(...)` — фактический источник данных инвентаря.
     */
    public static String buildElixirRemainingSuffixForMessage(String elixirName,
                                                              String inventoryHtml,
                                                              int adjustAfterUse) {
        Integer remaining = resolveElixirRemainingFromInventoryCache(elixirName, inventoryHtml);
        if (remaining == null) {
            return "";
        }
        int adjusted = remaining + adjustAfterUse;
        if (adjusted < 0) {
            adjusted = 0;
        }
        return " Остаток: <b><font color=#01A9DB>" + adjusted + "</font></b>";
    }

    /**
     * Проверяет, что FastId относится к эликсирам вкладки `im=6`.
     */
    private static boolean isElixirFastId(String fastId) {
        if (fastId == null) return false;
        switch (fastId) {
            case FAST_ID_BLISS_ELIXIR:
            case "Эликсир Мгновенного Исцеления":
            case "Эликсир Восстановления":
                return true;
            default:
                return false;
        }
    }

    private static boolean isBlissElixirFastId(String fastId) {
        return FAST_ID_BLISS_ELIXIR.equals(fastId);
    }

    private static void rememberBlissUseTimestamp(String fastId) {
        if (!isBlissElixirFastId(fastId)) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (lastBlissUseAtMs > 0L) {
            prevBlissUseAtMs = lastBlissUseAtMs;
        }
        lastBlissUseAtMs = nowMs;
        Log.d(TAG, "AUTO_BLAZ_ETA_TRACE remember use: prev=" + prevBlissUseAtMs + ", last=" + lastBlissUseAtMs);
    }

    private static String buildAutoTreasureBlissEtaMessage(String fastId, Integer elixirRemainingAfterUse) {
        if (!isBlissElixirFastId(fastId) || !isAutoTreasureActiveNow()) {
            return null;
        }
        if (elixirRemainingAfterUse == null || elixirRemainingAfterUse <= 0) {
            return null;
        }
        if (prevBlissUseAtMs <= 0L || lastBlissUseAtMs <= prevBlissUseAtMs) {
            return null;
        }

        long intervalMs = lastBlissUseAtMs - prevBlissUseAtMs;
        if (intervalMs < 1_000L) {
            return null;
        }
        long estimateMs = intervalMs * (long) elixirRemainingAfterUse;
        String hhmm = formatDurationHhMm(estimateMs);
        Log.d(TAG, "AUTO_BLAZ_ETA_TRACE estimate: intervalMs=" + intervalMs
                + ", remaining=" + elixirRemainingAfterUse
                + ", estimateMs=" + estimateMs
                + ", hhmm=" + hhmm);
        return buildServerChatTimeHtml()
                + "<font color=#336699>Авто-Клад: Блаженства примерно хватит на <b><font color=#01A9DB>"
                + hhmm + "</font></b> времени.</font>";
    }

    private static String buildServerChatTimeHtml() {
        return MainPhp.buildServerChatTimeHtmlExternal();
    }

    private static boolean isAutoTreasureActiveNow() {
        return AppVars.DoSearchBox
                || AppVars.AutoMoving
                || (AppVars.Profile != null && AppVars.Profile.AutoDig);
    }

    private static String formatDurationHhMm(long durationMs) {
        long totalMinutes = Math.max(1L, Math.round(durationMs / 60000.0d));
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return String.format(Locale.US, "%02d:%02d", hours, minutes);
    }

    /**
     * Возвращает текущий суммарный остаток долговечности по эликсиру из готового кеша инвентаря.
     *
     * Используем существующий модуль инвентаря (`AppVars.InvList`), чтобы не дублировать
     * разбор HTML в FastActionManager и учитывать текущие настройки группировки.
     */
    private static Integer resolveElixirRemainingFromInventoryCache(String elixirName, String inventoryHtml) {
        if (elixirName == null || elixirName.isEmpty()) {
            return null;
        }

        // При fast-использовании эликсиров InvList может быть уже заполнен, но устаревшим снимком.
        // Поэтому сначала всегда пробуем обновить кэш из текущего HTML страницы im=6.
        trySyncInventoryCacheFromHtml(inventoryHtml, "pre-read");

        Integer fromCache = resolveElixirRemainingFromCacheEntries(elixirName);
        if (fromCache != null) {
            return fromCache;
        }

        // Если текущий снимок не дал результата, пробуем повторно (fallback) и читаем кэш ещё раз.
        trySyncInventoryCacheFromHtml(inventoryHtml, "cache-miss");
        return resolveElixirRemainingFromCacheEntries(elixirName);
    }

    private static Integer resolveElixirRemainingFromCacheEntries(String elixirName) {
        if (AppVars.InvList == null || AppVars.InvList.isEmpty()) {
            return null;
        }

        int totalCurrent = 0;
        int matchedEntries = 0;
        String normalizedName = elixirName.trim();

        for (InvEntry entry : AppVars.InvList) {
            if (entry == null || entry.Name == null) {
                continue;
            }
            if (!entry.Name.trim().equalsIgnoreCase(normalizedName)) {
                continue;
            }

            // build() уже учитывает группировку и итоговую долговечность (например, 195/200).
            String builtEntryHtml = entry.build();
            String builtDolg = HelperStrings.subString(builtEntryHtml, "Долговечность: <b>", "</b>");
            int currentDolg = parseCurrentDurability(builtDolg);
            if (currentDolg >= 0) {
                totalCurrent += currentDolg;
                matchedEntries++;
            }
        }

        if (matchedEntries <= 0) {
            return null;
        }

        Log.d(TAG, "ELIXIR_REMAIN_TRACE: name=" + elixirName
                + ", matchedEntries=" + matchedEntries
                + ", totalCurrent=" + totalCurrent
                + ", source=AppVars.InvList");
        return totalCurrent;
    }

    private static void trySyncInventoryCacheFromHtml(String inventoryHtml, String reason) {
        if (inventoryHtml == null || inventoryHtml.isEmpty()) {
            return;
        }
        if (!inventoryHtml.contains("<tr><td bgcolor=#F5F5F5>")
                && !inventoryHtml.contains("Долговечность: <b>")) {
            return;
        }
        MainPhp.syncInventoryCacheFromHtml(inventoryHtml);
        int syncedSize = AppVars.InvList == null ? 0 : AppVars.InvList.size();
        Log.d(TAG, "ELIXIR_REMAIN_TRACE sync-cache: reason=" + reason + ", syncedSize=" + syncedSize);
    }

    /**
     * Парсит текущую долговечность из строки вида `x/y`.
     */
    private static int parseCurrentDurability(String durability) {
        if (durability == null || durability.isEmpty()) {
            return -1;
        }
        int slashPos = durability.indexOf('/');
        if (slashPos <= 0) {
            return -1;
        }
        String currentPart = durability.substring(0, slashPos).trim();
        if (currentPart.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(currentPart);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * Нормализует внутренний идентификатор fast-действия для отображения в чате.
     * Если специальный alias не задан — возвращает исходный fastId.
     */
    private static String resolveFastDisplayName(String fastId) {
        if (fastId == null || fastId.trim().isEmpty()) return "быстрое действие";
        switch (fastId) {
            case "i_w28_24.gif":
                return "Кулачки";
            case "i_w28_25.gif":
                return "Закрытые кулачки";
            case "i_w28_26.gif":
                return "Боевые";
            case "i_w28_26X.gif":
                return "Закрытые боевые";
            case "i_w28_86.gif":
                return "Портал";
            case "i_svi_001.gif":
                return "Нападение";
            case "i_svi_002.gif":
                return "Кровавое нападение";
            default:
                return fastId;
        }
    }

    /**
     * Ищет ссылку на нужный раздел инвентаря в текущем HTML.
     */
    private static String findTargetLink(String html, String fastId) {
        if (fastId == null) return null;

        boolean isPotion = !fastId.endsWith(".gif");
        String wca = isPotion ? "wca=27" : "wca=28";

        Log.d(TAG, "findTargetLink: ищем категорию " + wca + " для FastId=" + fastId);

        // 1. Ищем прямую ссылку на нужную категорию (Свитки или Зелья)
        String link = findLinkWithPattern(html, wca);
        if (link != null) {
            Log.d(TAG, "findTargetLink: найдена прямая ссылка на категорию: " + link);
            return link;
        }

        // 2. Если не нашли категорию, ищем общую ссылку на инвентарь (go=inv)
        link = findLinkWithPattern(html, "go=inv");
        if (link != null) {
            Log.d(TAG, "findTargetLink: найдена ссылка на общий инвентарь: " + link);
            return link;
        }

        Log.w(TAG, "findTargetLink: ссылки на инвентарь не найдены в HTML");
        return null;
    }

    /**
     * Вспомогательный метод для поиска ссылки по паттерну внутри location='...'
     * Перебирает все вхождения location='...' и проверяет, содержит ли URL нужный паттерн.
     */
    private static String findLinkWithPattern(String html, String pattern) {
        String marker = "location='";
        int pos = 0;
        while (pos < html.length()) {
            int start = html.indexOf(marker, pos);
            if (start == -1) break;
            start += marker.length();

            int end = html.indexOf("'", start);
            if (end == -1) break;

            String link = html.substring(start, end);
            if (link.contains(pattern) && link.startsWith("main.php?")) {
                return link;
            }

            pos = end + 1;
        }
        return null;
    }

    // --- Парсеры ---

    /**
     * Универсальный парсер w28_form для нападалок (аналог mainPhpFastHit/BloodHit/Ultimate/etc в C#).
     * Все нападалки используют одинаковый паттерн, отличаясь только wsubid и post_id=8.
     *
     * @param html          HTML страницы main.php
     * @param validSubIds   допустимые значения wsubid (например {"1","2","3","4"} для обычной)
     * @param description   описание для лога ("обычную нападалку")
     * @return сгенерированный HTML с формой или null
     */
    private static String mainPhpFastHit(String html, String[] validSubIds, String description) {
        Log.d(TAG, "mainPhpFastHit: ищем " + description + " с wsubid=" + java.util.Arrays.toString(validSubIds));

        // Диагностика: показать все w28_form вызовы с их wsubid
        {
            int diagPos = 0;
            int w28Count = 0;
            StringBuilder wsubIds = new StringBuilder();
            while (diagPos < html.length()) {
                int wIdx = html.indexOf("w28_form(", diagPos);
                if (wIdx == -1) break;
                int wEnd = html.indexOf(")", wIdx);
                if (wEnd == -1) break;
                String wArgs = html.substring(wIdx + "w28_form(".length(), wEnd);
                String[] wParts = wArgs.split(",");
                if (wParts.length >= 3) {
                    String wsub = wParts[2].replace("'", "").trim();
                    if (wsubIds.length() > 0) wsubIds.append(",");
                    wsubIds.append(wsub);
                }
                w28Count++;
                diagPos = wEnd + 1;
            }
            Log.d(TAG, "mainPhpFastHit: всего w28_form=" + w28Count + ", wsubid=[" + wsubIds + "]");
        }

        String patternW28Form = "w28_form(";
        int p1 = 0;
        while (p1 != -1) {
            p1 = html.indexOf(patternW28Form, p1);
            if (p1 == -1) break;

            p1 += patternW28Form.length();
            int p2 = html.indexOf(")", p1);
            if (p2 == -1) continue;

            String args = html.substring(p1, p2);
            if (args.isEmpty()) continue;

            String[] arg = args.split(",");
            if (arg.length < 4) continue;

            String vcode = arg[0].replace("'", "").trim();
            String wuid = arg[1].replace("'", "").trim();
            String wsubid = arg[2].replace("'", "").trim();
            String wsolid = arg[3].replace("'", "").trim();

            boolean validSub = false;
            for (String id : validSubIds) {
                if (wsubid.equals(id)) { validSub = true; break; }
            }
            if (!validSub) continue;

            // Генерируем HTML с формой + fetch/redirect (аналог C# StringBuilder)
            return HTML_HEAD +
                    "Используем " + description + " на " + AppVars.FastNick + "..." +
                    "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>" +
                    "<input name=post_id type=hidden value=\"8\">" +
                    "<input name=vcode type=hidden value=\"" + vcode + "\">" +
                    "<input name=wuid type=hidden value=\"" + wuid + "\">" +
                    "<input name=wsubid type=hidden value=\"" + wsubid + "\">" +
                    "<input name=wsolid type=hidden value=\"" + wsolid + "\">" +
                    "<input name=pnick type=hidden value=\"" + AppVars.FastNick + "\">" +
                    "<input name=agree type=hidden value=\"Выполнить\">" +
                    "</form>" +
                    buildSubmitScript();
        }

        Log.w(TAG, description + " не найдена в HTML");
        return null;
    }

    /**
     * Универсальный парсер w28_form для свитков/порталов (аналог mainPhpFastZas/Portal/Teleport в C#).
     * Используют post_id=25 и pnick (кроме телепорта).
     */
    private static String mainPhpFastW28(String html, String targetSubId, String description) {
        String patternW28Form = "w28_form(";
        int p1 = 0;
        while (p1 != -1) {
            p1 = html.indexOf(patternW28Form, p1);
            if (p1 == -1) break;

            p1 += patternW28Form.length();
            int p2 = html.indexOf(")", p1);
            if (p2 == -1) continue;

            String args = html.substring(p1, p2);
            if (args.isEmpty()) continue;

            String[] arg = args.split(",");
            if (arg.length < 4) continue;

            String vcode = arg[0].replace("'", "").trim();
            String wuid = arg[1].replace("'", "").trim();
            String wsubid = arg[2].replace("'", "").trim();
            String wsolid = arg[3].replace("'", "").trim();

            if (!wsubid.equals(targetSubId)) continue;

            return HTML_HEAD +
                    "Применяем " + description + " " + AppVars.FastNick + "..." +
                    "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>" +
                    "<input name=post_id type=hidden value=\"25\">" +
                    "<input name=vcode type=hidden value=\"" + vcode + "\">" +
                    "<input name=wuid type=hidden value=\"" + wuid + "\">" +
                    "<input name=wsubid type=hidden value=\"" + wsubid + "\">" +
                    "<input name=wsolid type=hidden value=\"" + wsolid + "\">" +
                    "<input name=pnick type=hidden value=\"" + AppVars.FastNick + "\">" +
                    "<input name=agree type=hidden value=\"" + ("27".equals(wsubid) ? "Помочь" : "Выполнить") + "\">" +
                    "</form>" +
                    buildSubmitScript();
        }

        Log.w(TAG, description + " не найден в HTML");
        return null;
    }

    /**
     * Парсер для тумана (abil_svitok) — аналог mainPhpFastFog в C#.
     * Ищет abil_svitok('wuid','wmid','wmsolid','name','wmcode')
     */
    private static String mainPhpFastFog(String html) {
        String namesvitok = "'Свиток Искажающего Тумана'";
        int p0 = html.indexOf(namesvitok);
        if (p0 == -1) { Log.w(TAG, "Туман не найден"); return null; }

        int ps = html.lastIndexOf('<', p0);
        if (ps == -1) return null;
        ps++;
        int pe = html.indexOf('>', p0);
        if (pe == -1) return null;

        String chunk = html.substring(ps, pe);
        if (!chunk.contains("abil_svitok(")) return null;

        String args = HelperStrings.subString(chunk, "abil_svitok('", "')");
        if (args == null || args.isEmpty()) return null;

        String[] arg = args.split("'");
        if (arg.length < 9) return null;

        String wuid = arg[0];
        String wmid = arg[2];
        String wmsolid = arg[4];
        String wmcode = arg[8];

        return HTML_HEAD +
                "Используем Свиток Искажающего Тумана..." +
                "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>" +
                "<input name=post_id type=hidden value=\"44\">" +
                "<input name=uid type=hidden value=\"" + wuid + "\">" +
                "<input name=mid type=hidden value=\"" + wmid + "\">" +
                "<input name=curs type=hidden value=\"" + wmsolid + "\">" +
                "<input name=vcode type=hidden value=\"" + wmcode + "\">" +
                "<input name=fnick type=hidden value=\"" + AppVars.FastNick + "\">" +
                "<input name=agree type=hidden value=\"Выполнить\">" +
                "</form>" +
                buildSubmitScript();
    }

    /**
     * Парсер для зелий (magicreform) — аналог mainPhpFastPotion в C#.
     * Ищет magicreform('wuid','target','potionName','wmcode')
     */
    private static String mainPhpFastPotion(String html) {
        String fastId = AppVars.FastId;
        Log.d(TAG, "mainPhpFastPotion: ищем '" + fastId + "' в HTML (" + html.length() + " chars)");

        // Диагностика: показать все magicreform вызовы
        int diagPos = 0;
        int magicCount = 0;
        while (diagPos < html.length()) {
            int mIdx = html.indexOf("magicreform(", diagPos);
            if (mIdx == -1) break;
            int mEnd = html.indexOf(")", mIdx);
            if (mEnd == -1) break;
            String mCall = html.substring(mIdx, Math.min(mEnd + 1, mIdx + 120));
            Log.d(TAG, "  magicreform[" + magicCount + "]: " + mCall);
            magicCount++;
            diagPos = mEnd + 1;
            if (magicCount > 15) { Log.d(TAG, "  ... ещё записи опущены"); break; }
        }
        Log.d(TAG, "mainPhpFastPotion: всего magicreform = " + magicCount);

        // Ищем зелье среди magicreform вызовов.
        // В C# ищется "'Зелье Сильной Спины'" (с кавычками), но на сервере зелья могут
        // иметь префиксы (например "Превосходное Зелье Сильной Спины").
        // Поэтому ищем FastId БЕЗ кавычек внутри контекста magicreform вызовов.
        String wuid = null;
        String wmcode = null;

        // Стратегия 1: точное совпадение с кавычками (как в C#)
        String namepotion = "'" + fastId + "'";
        int p0 = indexOfIgnoreCase(html, namepotion, 0);

        // Стратегия 2: поиск без кавычек (для "Превосходное Зелье ..." и подобных вариантов)
        if (p0 == -1) {
            Log.d(TAG, "mainPhpFastPotion: точное совпадение не найдено, ищем без кавычек");
            p0 = indexOfIgnoreCase(html, fastId, 0);
        }

        // Для "Превосходного Зелья Сильной Спины" делаем fallback на обычное.
        if (p0 == -1 && "Превосходное Зелье Сильной Спины".equalsIgnoreCase(fastId)) {
            p0 = indexOfIgnoreCase(html, "'Зелье Сильной Спины'", 0);
            if (p0 == -1) {
                p0 = indexOfIgnoreCase(html, "Зелье Сильной Спины", 0);
            }
            if (p0 != -1) {
                Log.d(TAG, "mainPhpFastPotion: fallback на обычное Зелье Сильной Спины");
            }
        }

        if (p0 == -1) {
            Log.w(TAG, "Зелье не найдено: " + fastId);
            return null;
        }

        int ps = html.lastIndexOf('<', p0);
        if (ps == -1) return null;
        ps++;
        int pe = html.indexOf('>', p0);
        if (pe == -1) return null;

        String chunk = html.substring(ps, pe);
        if (indexOfIgnoreCase(chunk, "magicreform(", 0) == -1) {
            Log.d(TAG, "mainPhpFastPotion: найдено имя зелья, но нет magicreform в контексте");
            return null;
        }

        String args = HelperStrings.subString(chunk, "magicreform('", "')");
        if (args == null || args.isEmpty()) return null;

        String[] arg = args.split("'");
        if (arg.length < 7) return null;

        wuid = arg[0];
        wmcode = arg[6];

        Log.d(TAG, "mainPhpFastPotion: НАЙДЕНО wuid=" + wuid + ", wmcode=" + wmcode);

        return HTML_HEAD +
                "Используем " + AppVars.FastId + "..." +
                "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>" +
                "<input name=magicrestart type=hidden value=\"1\">" +
                "<input name=magicreuid type=hidden value=\"" + wuid + "\">" +
                "<input name=vcode type=hidden value=\"" + wmcode + "\">" +
                "<input name=post_id type=hidden value=\"46\">" +
                "<input name=fornickname type=hidden value=\"" + AppVars.FastNick + "\">" +
                "<input name=agree type=hidden value=\"Применить\">" +
                "</form>" +
                buildSubmitScript();
    }

    /**
     * Парсер для свитков без pnick (саморассеивание, обнаружение).
     * Аналог MainPhpFastSelfRass / MainPhpFastOpenNevid в C#.
     * Используют w28_form, post_id=25, БЕЗ поля pnick.
     */
    private static String mainPhpFastW28Self(String html, String targetSubId, String description) {
        String patternW28Form = "w28_form(";
        int p1 = 0;
        while (p1 != -1) {
            p1 = html.indexOf(patternW28Form, p1);
            if (p1 == -1) break;

            p1 += patternW28Form.length();
            int p2 = html.indexOf(")", p1);
            if (p2 == -1) continue;

            String args = html.substring(p1, p2);
            if (args.isEmpty()) continue;

            String[] arg = args.split(",");
            if (arg.length < 4) continue;

            String vcode = arg[0].replace("'", "").trim();
            String wuid = arg[1].replace("'", "").trim();
            String wsubid = arg[2].replace("'", "").trim();
            String wsolid = arg[3].replace("'", "").trim();

            if (!wsubid.equals(targetSubId)) continue;

            return HTML_HEAD +
                    description + "..." +
                    "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>" +
                    "<input name=post_id type=hidden value=\"25\">" +
                    "<input name=vcode type=hidden value=\"" + vcode + "\">" +
                    "<input name=wuid type=hidden value=\"" + wuid + "\">" +
                    "<input name=wsubid type=hidden value=\"" + wsubid + "\">" +
                    "<input name=wsolid type=hidden value=\"" + wsolid + "\">" +
                    "<input name=agree type=hidden value=\"Выполнить\">" +
                    "</form>" +
                    buildSubmitScript();
        }

        Log.w(TAG, description + " не найден в HTML");
        return null;
    }

    /**
     * Парсер для телепорта (wsubid=22) с wtelid — случайный пункт назначения.
     * Аналог MainPhpFastTeleport в C#.
     * post_id=25, дополнительное поле wtelid (1-12).
     */
    private static String mainPhpFastTeleport(String html) {
        String patternW28Form = "w28_form(";
        int p1 = 0;
        while (p1 != -1) {
            p1 = html.indexOf(patternW28Form, p1);
            if (p1 == -1) break;

            p1 += patternW28Form.length();
            int p2 = html.indexOf(")", p1);
            if (p2 == -1) continue;

            String args = html.substring(p1, p2);
            if (args.isEmpty()) continue;

            String[] arg = args.split(",");
            if (arg.length < 4) continue;

            String vcode = arg[0].replace("'", "").trim();
            String wuid = arg[1].replace("'", "").trim();
            String wsubid = arg[2].replace("'", "").trim();
            String wsolid = arg[3].replace("'", "").trim();

            if (!wsubid.equals("22")) continue;

            // Случайный пункт назначения (1-12), аналог Dice.Make(12) + 1 в C#
            int wtelid = sanitizeTeleportDestinationId(selectedTeleportDestinationId);
            selectedTeleportDestinationId = wtelid;
            selectedTeleportDestinationName = resolveTeleportDestinationName(wtelid, selectedTeleportDestinationName);

            return HTML_HEAD +
                    "Используем телепорт..." +
                    "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>" +
                    "<input name=post_id type=hidden value=\"25\">" +
                    "<input name=vcode type=hidden value=\"" + vcode + "\">" +
                    "<input name=wuid type=hidden value=\"" + wuid + "\">" +
                    "<input name=wsubid type=hidden value=\"" + wsubid + "\">" +
                    "<input name=wsolid type=hidden value=\"" + wsolid + "\">" +
                    "<input name=wtelid type=hidden value=\"" + wtelid + "\">" +
                    "<input name=agree type=hidden value=\"\u041F\u0440\u0438\u043C\u0435\u043D\u0438\u0442\u044C\">" +
                    "</form>" +
                    buildSubmitScript();
        }

        Log.w(TAG, "Свиток телепорта не найден в HTML");
        return null;
    }

    /**
     * Парсер для эликсиров (аналог MainPhpFastElixir в C#).
     * Ищет "Использовать <ElixirName> сейчас?" → извлекает ссылку → GET redirect.
     * confirm('Использовать Эликсир Блаженства сейчас?')) { location='main.php?get_id=43&act=107&...'
     */
    private static String mainPhpFastElixir(String html) {
        String fastId = AppVars.FastId;
        String namepotion = "Использовать " + fastId + " сейчас?";
        Log.d(TAG, "mainPhpFastElixir: ищем '" + namepotion + "'");

        int p0 = indexOfIgnoreCase(html, namepotion, 0);
        if (p0 == -1) {
            Log.w(TAG, "mainPhpFastElixir: не найдено '" + namepotion + "'");
            return null;
        }

        // Ищем ='...' после найденной строки
        int ps = html.indexOf("='", p0);
        if (ps == -1) { Log.w(TAG, "mainPhpFastElixir: =' не найден"); return null; }
        ps += 2;
        int pe = html.indexOf("'", ps);
        if (pe == -1) { Log.w(TAG, "mainPhpFastElixir: закрывающая ' не найдена"); return null; }

        String link = html.substring(ps, pe);
        Log.d(TAG, "mainPhpFastElixir: redirect на " + link);

        // Эликсиры используют GET redirect (не POST форму)
        return HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head><body>" +
                "Используем " + fastId + "..." +
                "<script language=\"JavaScript\">window.location = \"" + link + "\";</script></body></html>";
    }

    /**
     * Парсер для островного телепорта (аналог MainPhpFastIsland в C#).
     * Вариант 1: Ищет "Использовать Свиток Телепорта сейчас?" → GET redirect.
     * Вариант 2 (fallback): Ищет w28_form с wsubid=22 → POST форма с wtelid=13 (Остров Туротор).
     */
    private static String mainPhpFastIsland(String html) {
        // Вариант 1: страница с подтверждением (как в PC-версии)
        String str = "Использовать Свиток Телепорта сейчас?";
        Log.d(TAG, "mainPhpFastIsland: ищем '" + str + "'");

        int p0 = indexOfIgnoreCase(html, str, 0);
        if (p0 != -1) {
            int ps = html.indexOf("='", p0);
            if (ps != -1) {
                ps += 2;
                int pe = html.indexOf("'", ps);
                if (pe != -1) {
                    String link = html.substring(ps, pe);
                    Log.d(TAG, "mainPhpFastIsland: redirect на " + link);
                    return HtmlUtils.GENERATED_PAGE_MARKER +
                            "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                            "<title>ABClient</title></head><body>" +
                            "Используем Телепорт (Остров Туротор)..." +
                            "<script language=\"JavaScript\">window.location = \"" + link + "\";</script></body></html>";
                }
            }
        }

        // Вариант 2 (fallback): страница инвентаря со свитками (w28_form)
        // Остров Туротор = wtelid=13, wsubid=22 (свиток телепорта)
        Log.d(TAG, "mainPhpFastIsland: подтверждение не найдено, ищем w28_form с wsubid=22");
        String patternW28Form = "w28_form(";
        int p1 = 0;
        while (p1 != -1) {
            p1 = html.indexOf(patternW28Form, p1);
            if (p1 == -1) break;

            p1 += patternW28Form.length();
            int p2 = html.indexOf(")", p1);
            if (p2 == -1) continue;

            String args = html.substring(p1, p2);
            if (args.isEmpty()) continue;

            String[] arg = args.split(",");
            if (arg.length < 4) continue;

            String vcode = arg[0].replace("'", "").trim();
            String wuid = arg[1].replace("'", "").trim();
            String wsubid = arg[2].replace("'", "").trim();
            String wsolid = arg[3].replace("'", "").trim();

            if (!wsubid.equals("22")) continue;

            // Остров Туротор = wtelid=13
            int wtelid = 13;
            Log.d(TAG, "mainPhpFastIsland: найден w28_form wsubid=22, используем wtelid=" + wtelid);

            return HTML_HEAD +
                    "Используем Телепорт (Остров Туротор)..." +
                    "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>" +
                    "<input name=post_id type=hidden value=\"25\">" +
                    "<input name=vcode type=hidden value=\"" + vcode + "\">" +
                    "<input name=wuid type=hidden value=\"" + wuid + "\">" +
                    "<input name=wsubid type=hidden value=\"" + wsubid + "\">" +
                    "<input name=wsolid type=hidden value=\"" + wsolid + "\">" +
                    "<input name=wtelid type=hidden value=\"" + wtelid + "\">" +
                    "<input name=agree type=hidden value=\"Выполнить\">" +
                    "</form>" +
                    buildSubmitScript();
        }

        Log.w(TAG, "mainPhpFastIsland: не найдено");
        return null;
    }

    /**
     * Парсер для тотемного нападения (аналог MainPhpFastTotem в C#).
     * Ищет ["fig","Напасть","<vcode>"] → POST с post_id=8.
     * Тотем НЕ требует инвентаря — он доступен на основной странице.
     */
    private static String mainPhpFastTotem(String html) {
        String patternEnter = "[\"fig\",\"Напасть\",\"";
        Log.d(TAG, "mainPhpFastTotem: ищем паттерн Напасть");

        int pos = html.indexOf(patternEnter);
        if (pos == -1) {
            // Пробуем с unicode
            patternEnter = "[\"fig\",\"\u041D\u0430\u043F\u0430\u0441\u0442\u044C\",\"";
            pos = html.indexOf(patternEnter);
        }
        if (pos == -1) {
            Log.w(TAG, "mainPhpFastTotem: паттерн не найден");
            return null;
        }

        pos += patternEnter.length();
        int posEnd = html.indexOf('"', pos);
        if (posEnd == -1) {
            Log.w(TAG, "mainPhpFastTotem: закрывающая кавычка не найдена");
            return null;
        }

        String vcode = html.substring(pos, posEnd);
        Log.d(TAG, "mainPhpFastTotem: vcode=" + vcode);

        return HTML_HEAD +
                "Используем тотемное нападение на " + AppVars.FastNick + "..." +
                "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>" +
                "<input name=post_id type=hidden value=\"8\">" +
                "<input name=vcode type=hidden value=\"" + vcode + "\">" +
                "<input name=pnick type=hidden value=\"" + AppVars.FastNick + "\">" +
                "<input name=agree type=hidden value=\"Выполнить\">" +
                "</form>" +
                buildSubmitScript();
    }

    /**
     * Генерирует JavaScript для отправки формы через document.ff.submit().
     *
     * POST идёт напрямую на сервер, ответ отображается в WebView.
     * Ответ НЕ проходит через наш Filter (shouldInterceptRequest не перехватывает POST),
     * но содержит системные сообщения о результате действия
     * (например "нельзя нападать на себя", "нельзя чаще раз в 5 секунд" и т.д.).
     */
    private static String buildSubmitScript() {
        return "<script language=\"JavaScript\">" +
                "console.log('ABClient: submitting form ff, action=' + document.ff.action);" +
                "document.ff.submit();" +
                "</script></body></html>";
    }

    // --- Утилиты ---

    /**
     * Перезагружает main.php в WebView через loadUrl.
     * Аналог ReloadMainPhpInvoke → NavigateFrame("main_top", "main.php") в C#.
     *
     * В C# клиент загружает plain "main.php" в фрейм main_top.
     * Сервер возвращает go=inf страницу со свежим vcode.
     * Затем processMainPhpFast в MainPhp.process() находит vcode и делает BuildRedirect
     * на нужную вкладку инвентаря (go=inv&vcode=...&wca=28 или wca=27).
     *
     * На Android loadUrl заменяет весь frameset, но shouldInterceptRequest перехватит запрос,
     * Filter обработает, processMainPhpFast сделает redirect, WebView выполнит redirect,
     * и цепочка продолжится до тех пор пока предмет не будет найден и использован.
     */
    // Перезагрузка main.php для запуска цепочки FastAction в MainPhp.process().
    private static void reloadMainFrame() {
        if (AppVars.getContext() == null) return;

        // Загружаем main.php?get_id=56&act=10&go=inf — страница персонажа со свежим vcode.
        // В C# загружается plain "main.php" в sub-frame, сервер возвращает go=inf.
        // На Android мы не можем навигировать sub-frame, поэтому загружаем go=inf напрямую.
        // processMainPhpFast в MainPhp.process() найдёт vcode и сделает BuildRedirect на инвентарь.
        // ВАЖНО: main.php без параметров = frameset, его нельзя использовать!
        String url = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf";
        if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
            url += "&vcode=" + AppVars.VCode;
        }
        Log.d(TAG, "reloadMainFrame: loading " + url);

        // Broadcast в MainActivity: попросить WebView загрузить URL.
        Intent intent = new Intent(AppVars.ACTION_WEBVIEW_LOAD_URL);
        intent.putExtra("url", url);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }

    /**
     * Case-insensitive indexOf (аналог string.IndexOf с StringComparison.CurrentCultureIgnoreCase).
     */
    private static int indexOfIgnoreCase(String source, String target, int fromIndex) {
        if (source == null || target == null) return -1;
        String lowerSource = source.toLowerCase();
        String lowerTarget = target.toLowerCase();
        return lowerSource.indexOf(lowerTarget, fromIndex);
    }
}
