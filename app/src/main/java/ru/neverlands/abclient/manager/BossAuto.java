package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Handler;
import android.os.Looper;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.postfilter.MainPhp;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.FileLogger;

/**
 * Модуль авто-функции «Авто-Боссы».
 *
 * Назначение:
 * - ловит системный анонс чата о нападении босса на игрока;
 * - запускает поиск цели через уже существующий контур Auto-Компас;
 * - после нахождения цели применяет FastAction «Свиток Защиты»;
 * - после завершения боя возвращает персонажа на исходную клетку и восстанавливает паузенные авто-функции.
 *
 * Ключевое правило реализации:
 * - не дублировать существующие конвейеры (Auto-Компас, FastAction, Navigator),
 *   а только оркестрировать их через AutoFunctionsManager.
 */
/**
 * Оркестратор сценария «Авто-Боссы».
 *
 * Задача модуля:
 * - отследить событие нападения босса из системного чата;
 * - передать поиск цели в существующий контур Auto-Компас;
 * - после нахождения цели применить «Свиток Защиты» через FastAction;
 * - после завершения сценария вернуть исходные настройки авто-функций.
 *
 * Важный инвариант:
 * - здесь нет дублирования логики навигатора/компаса/fast-action;
 *   используются только вызовы уже существующих менеджеров.
 */
/**
 * Оркестратор сценария «Авто-Боссы».
 *
 * Что делает модуль:
 * - получает системные события о боссах из чата;
 * - запускает поиск цели через уже существующий Auto-Компас;
 * - после нахождения цели выполняет FastAction «Свиток Защиты»;
 * - по завершению возвращает исходные состояния авто-функций.
 *
 * Важный инвариант реализации:
 * - здесь нет дублирования логики навигатора/fast-action;
 * - модуль только координирует вызовы через AutoFunctionsManager.
 */
final class BossAuto {
    private static final String TAG = "AutoFunctionsManager";
    private static final String TRACE_PREFIX = "AUTO_BOSS_TRACE";
    private static final String LOG_CHAIN = "boss_auto";
    private static final String KEY_AUTO_BOSS = "auto_function_auto_boss";
    private static final String PREF_AUTO_BOSS_ASK_TARGET = "auto_boss_ask_target";
    private static final String PREF_AUTO_BOSS_TRACK_CURRENT_WARS = "auto_boss_track_current_wars";
    private static final String PREF_AUTO_BOSS_CLAN_NOTIFY = "auto_boss_clan_notify";
    private static final String PREF_AUTO_BOSS_SELF_CLAN_TOKEN_CACHE = "auto_boss_self_clan_token_cache";
    private static final String PREF_AUTO_BOSS_WAIT_SCROLL_SEC = "auto_boss_wait_scroll_sec";
    private static final String PREF_AUTO_BOSS_SEARCH_TIMEOUT_SEC = "auto_boss_search_timeout_sec";
    private static final String PREF_AUTO_BOSS_WAIT_FIGHT_TIMEOUT_SEC = "auto_boss_wait_fight_timeout_sec";
    private static final String LOCAL_CHAT_MARKER = "<!--AB_LOCAL_CHAT-->";

    private static final Pattern BOSS_EVENT_PATTERN = Pattern.compile(
            "внимание!\\s*случайное событие!\\s*монстр\\s*[\"«]?([^\"»]+)[\"»]?\\s*напал на игрока\\s+([a-zа-я0-9_\\-]+)\\.",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // ОСНОВНОЙ парсер события Босса: максимально гибкий, с поддержкой:
    // - опциональной точки в конце
    // - любых ников с буквами, цифрами, спецсимволами
    // - формата со временем и без
    // - вариантов: "напал", "напала", "напали"
    // Зависимость: используется в parseBossEvent() для детекта события
    private static final Pattern BOSS_EVENT_PATTERN_FLEX = Pattern.compile(
            "(?iu)(?:\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s+)?(?:внимание!\\s*случайное\\s+событие!\\s*)?монстр\\s*[\"«]?([^\"»]+)[\"»]?\\s*(?:напал|напала|напали)\\s+на\\s+(?:игрока|игроков)?\\s*(.+?)\\s*(?:[.,:;]|$)");
    private static final Pattern CELL_PATTERN = Pattern.compile("\\b\\d{1,4}-\\d{1,5}\\b");
    private static final Pattern FIGHT_FID_IN_LINK_PATTERN = Pattern.compile("fid=([0-9]{1,16})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPAN_NICK_PATTERN = Pattern.compile(
            "<SPAN[^>]+(?:title|alt)=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FIGHT_ALLY_PATTERN = Pattern.compile(
            "([A-Za-zА-Яа-яЁё0-9_\\-]+)\\s*\\[(\\d{1,9})\\s*/\\s*(\\d{1,9})\\]");

    /**
     * Единый адаптер логирования BossAuto:
     * - пишет в logcat (для LogcatFileRecorder),
     * - дублирует в FileLogger (цепочка `boss_auto`).
     */
    private static final class Log {
        private Log() {
        }

        static int d(String tag, String message) {
            FileLogger.trace(LOG_CHAIN, message);
            return android.util.Log.d(tag, message);
        }

        static int w(String tag, String message) {
            FileLogger.warn(LOG_CHAIN, message);
            return android.util.Log.w(tag, message);
        }

        static int w(String tag, String message, Throwable error) {
            FileLogger.error(LOG_CHAIN, "WARN: " + message, error);
            return android.util.Log.w(tag, message, error);
        }
    }

    private static final int DEFAULT_SEARCH_TIMEOUT_SEC = 6 * 60;
    private static final int DEFAULT_WAIT_BEFORE_SCROLL_SEC = 2;
    private static final int DEFAULT_WAIT_FIGHT_TIMEOUT_SEC = 25;
    private static final long RETURN_TIMEOUT_MS = 2 * 60 * 1000L;
    private static final long EVENT_DEDUP_WINDOW_MS = 20_000L;
    private static final int TARGET_CHAT_ASK_MAX_ATTEMPTS = 5;
    private static final long TARGET_CHAT_ASK_RETRY_MS = 1_500L;
    private static final long TARGET_FIGHT_POLL_INTERVAL_MS = 1_000L;
    /**
     * Задержка перед отправкой клан-сообщения о событии босса.
     * Нужна для:
     * 1. Предотвращения DDoS-блокировки сервером (много параллельных запросов)
     * 2. Буферизации потока быстрых pinfo/compass запросов
     */
    private static final long CLAN_NOTIFY_DELAY_MS = 1000L;
    /**
     * Консервативный лимит длины `%clan%` сообщения.
     *
     * По логам длинные клан-пейлоады (особенно с большим списком клеток) могли
     * отправляться клиентом, но не отображаться у сокланов. Поэтому режем раньше.
     */
    private static final int CLAN_EVENT_CHAT_MAX_LEN = 160;
    /**
     * Задержка между clan message и private message для ask target.
     * Предотвращает отклонение обоих сообщений как DDoS.
     */
    private static final long CLAN_PRIVATE_MESSAGE_DELAY_MS = 500L;
    /**
     * Максимальное количество тиков отсутствия fight FID перед признанием боя потерянным.
     * Защита от бесконечного incrementing в случае потери соединения или ошибки сервера.
     */
    private static final int BOSS_FIGHT_FID_MISSING_MAX_TICKS = 10;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final AutoFunctionsManager owner;

    private final Object lock = new Object();
    private BossStage stage = BossStage.IDLE;
    private BossScenarioSnapshot snapshot;
    private String targetNick = "";
    private String bossName = "";
    private String originRegNum = "";
    private long stageStartedAtMs = 0L;
    private long actionDueAtMs = 0L;
    private long protectionSentAtMs = 0L;
    private int protectionAttempts = 0;
    private int targetAskAttempts = 0;
    private long targetAskNextAttemptAtMs = 0L;
    private String lastEventKey = "";
    private long lastEventAtMs = 0L;
    private String bossFightFid = "";
    private String bossFightLink = "";
    private int bossFightFidMissingTicks = 0;
    private long targetFightPollNextAtMs = 0L;
    private boolean targetFightPollInFlight = false;
    private String targetFightPollNick = "";
    private boolean bossFightLostPending = false;
    private String bossTargetClanToken = "";
    private String bossSelfClanToken = "";
    private String cachedSelfClanToken = "";

    private enum BossStage {
        IDLE,
        SEARCHING_TARGET,
        TARGET_FOUND_WAIT_SCROLL,
        WAIT_FIGHT_START,
        FIGHT_IN_PROGRESS,
        RETURNING_TO_ORIGIN
    }

    /**
     * Снимок авто-функций, которые ставятся на паузу во время сценария Авто-Боссов.
     * Авто-Бой/Авто-Лечение сюда не входят: они не паузятся.
     */
    /**
     * Снимок исходного состояния авто-функций на момент старта сценария.
     * Нужен для корректного восстановления после завершения поиска/боя.
     */
    /**
     * Снимок пользовательских авто-настроек на момент старта сценария.
     *
     * Нужен для корректного restore после завершения «Авто-Босса».
     * В снимок не включаются Auto-Бой и Auto-Лечение — эти режимы
     * по проектным правилам не ставятся на паузу.
     */
    private static final class BossScenarioSnapshot {
        boolean autoFishEnabled;
        boolean autoBaitEnabled;
        boolean autoSkinEnabled;
        boolean autoAttackEnabled;
        boolean autoCompassEnabled;
        boolean autoInvisibleEnabled;
        boolean locationTrackingEnabled;
        boolean autoDetectEnabled;
        boolean autoSummonEnabled;
        boolean autoDrinkEnabled;
        boolean autoMovingEnabled;
        String autoMovingDestination;
        boolean autoTreasureEnabled;
        boolean autoCutEnabled;
        boolean autoRefreshEnabled;

        String autoCompassTargetNick;
        boolean autoCompassHuntMode;
        int autoCompassPollIntervalSec;
        String autoCompassManualCellsCsv;
    }

    private static final class BossEvent {
        final String bossName;
        final String targetNick;

        BossEvent(String bossName, String targetNick) {
            this.bossName = bossName;
            this.targetNick = targetNick;
        }
    }

    private static final class FightAllyState {
        final String nick;
        final int curHp;
        final int maxHp;

        FightAllyState(String nick, int curHp, int maxHp) {
            this.nick = nick;
            this.curHp = curHp;
            this.maxHp = maxHp;
        }
    }

    BossAuto(Context context, SharedPreferences prefs, AutoFunctionsManager owner) {
        this.appContext = context.getApplicationContext();
        this.prefs = prefs;
        this.owner = owner;
        this.cachedSelfClanToken = normalizeClanToken(
                prefs.getString(PREF_AUTO_BOSS_SELF_CLAN_TOKEN_CACHE, "")
        );
    }

    boolean isAutoBossEnabled() {
        return prefs.getBoolean(KEY_AUTO_BOSS, false);
    }

    void toggleAutoBoss() {
        setAutoBossEnabled(!isAutoBossEnabled());
    }

    void setAutoBossEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_BOSS, enabled).apply();
        if (!enabled) {
            stopAndRestore("manual_disable", true);
        } else {
            owner.requestCharacterSyncForAutoFunctionEnableInternal("auto_boss");
            writeBossChat("Режим включен. Ожидаем системные сообщения о Боссах.");
        }
        Log.d(TAG, TRACE_PREFIX + " setAutoBossEnabled=" + enabled);
        owner.syncBackgroundServiceInternal("setAutoBossEnabled(" + enabled + ")");
        owner.requestQuickButtonsRefreshInternal("setAutoBossEnabled(" + enabled + ")");
    }

    void onIncomingChatMessage(String messageHtml) {
        if (isEmpty(messageHtml)) {
            return;
        }
        boolean autoBossEnabled = isAutoBossEnabled();
        if (autoBossEnabled) {
            if (isServerBossEventMessage(messageHtml)) {
                BossEvent event = parseBossEvent(messageHtml);
                if (event != null) {
                    FileLogger.log("[BossAuto.onIncomingChatMessage] Event detected: boss=" + event.bossName + ", target=" + event.targetNick);
                    handleBossEvent(event);
                }
            } else if (looksLikeBossEventText(messageHtml)) {
                String preview = toPlainText(messageHtml);
                if (preview.length() > 220) {
                    preview = preview.substring(0, 220) + "...";
                }
                FileLogger.trace(LOG_CHAIN, "[BOSS_EVENT_SKIPPED_NON_SERVER] " + preview);
                Log.d(TAG, TRACE_PREFIX + " skip boss-event parse: non-server chat source");
            }
        }
        if (!autoBossEnabled) {
            return;
        }

        String plain = toPlainText(messageHtml);
        if (isEmpty(plain)) {
            return;
        }

        if (containsFightCompleted(plain)) {
            BossStage currentStage;
            synchronized (lock) {
                currentStage = stage;
            }
            if (currentStage == BossStage.WAIT_FIGHT_START || currentStage == BossStage.FIGHT_IN_PROGRESS) {
                Log.d(TAG, TRACE_PREFIX + " fight completed message detected");
                startReturnOrRestore("fight_completed_message");
                return;
            }
        }

        String senderNick = extractSenderNick(messageHtml);
        if (isEmpty(senderNick)) {
            return;
        }
        String cellRegNum = extractCellRegNum(plain);
        if (isEmpty(cellRegNum)) {
            return;
        }
        handleCellHintIfMatchesTarget(senderNick, cellRegNum);
    }

    /**
     * Определяет, что сообщение относится к системному каналу сервера, а не к локальному/клановому чату.
     */
    private boolean isServerBossEventMessage(String messageHtml) {
        if (isEmpty(messageHtml)) {
            return false;
        }
        String lowerHtml = messageHtml.toLowerCase(Locale.ROOT);
        if (messageHtml.contains(LOCAL_CHAT_MARKER)) {
            return false;
        }
        if (lowerHtml.contains("авто-боссы") || lowerHtml.contains("[авто-боссы]")) {
            return false;
        }
        boolean hasSystemClass = lowerHtml.contains("class=massm") || lowerHtml.contains("class=\"massm\"");
        boolean hasServerSender = lowerHtml.contains("neverlands.ru");
        if (!hasSystemClass || !hasServerSender) {
            return false;
        }
        return looksLikeBossEventText(messageHtml);
    }

    private boolean looksLikeBossEventText(String messageHtml) {
        String plain = toPlainText(messageHtml).toLowerCase(Locale.ROOT);
        return plain.contains("монстр")
                && (plain.contains("напал на игрока")
                || plain.contains("напала на игрока")
                || plain.contains("напали на игрока"));
    }

    void onRoomUsersUpdated(List<String> roomNicks, String roomLocationName) {
        if (!isAutoBossEnabled() || roomNicks == null || roomNicks.isEmpty()) {
            return;
        }
        String target;
        BossStage currentStage;
        synchronized (lock) {
            currentStage = stage;
            target = targetNick;
        }
        if (isEmpty(target) || currentStage != BossStage.SEARCHING_TARGET) {
            return;
        }
        String targetNorm = normalizeNick(target);
        for (String roomNick : roomNicks) {
            if (normalizeNick(roomNick).equalsIgnoreCase(targetNorm)) {
                onTargetFoundInRoom("room_list");
                return;
            }
        }
    }

    void tickAutoBoss() {
        if (!isAutoBossEnabled()) {
            return;
        }
        BossStage currentStage;
        long stageStart;
        long now = System.currentTimeMillis();
        synchronized (lock) {
            currentStage = stage;
            stageStart = stageStartedAtMs;
        }

        switch (currentStage) {
            case SEARCHING_TARGET:
                if (!monitorTargetFightStateDuringSearch()) {
                    return;
                }
                maybeRetryAskTarget(now);
                if (now - stageStart >= getSearchTimeoutMs()) {
                    stopAndRestore("search_timeout", true);
                }
                break;
            case TARGET_FOUND_WAIT_SCROLL:
                if (now >= actionDueAtMs) {
                    sendProtectionScroll();
                }
                break;
            case WAIT_FIGHT_START:
                if (isFightLikelyActive()) {
                    synchronized (lock) {
                        stage = BossStage.FIGHT_IN_PROGRESS;
                        stageStartedAtMs = now;
                    }
                    writeBossChat("Бой начался. Передаем управление Авто-Бою.");
                    return;
                }
                if (now - protectionSentAtMs >= getWaitFightTimeoutMs()) {
                    if (protectionAttempts < 2) {
                        writeBossChat("Бой не стартовал, повторяем «Свиток Защиты».");
                        sendProtectionScroll();
                    } else {
                        startReturnOrRestore("fight_not_started");
                    }
                }
                break;
            case FIGHT_IN_PROGRESS:
                if (!isFightLikelyActive() && (now - stageStart) > 8_000L) {
                    startReturnOrRestore("fight_pulse_idle");
                }
                break;
            case RETURNING_TO_ORIGIN:
                processReturnStage(now, stageStart);
                break;
            case IDLE:
            default:
                break;
        }
    }

    /**
     * Обрабатывает триггер события «Босс напал на игрока».
     *
     * Основные шаги:
     * - выполняет anti-dup по событию;
     * - применяет фильтры участия в текущих клановых войнах;
     * - фиксирует ссылку на бой цели ({@code fid});
     * - ставит несовместимые авто-функции на паузу;
     * - запускает поиск цели через Auto-Компас.
     */
    private void handleBossEvent(BossEvent event) {
        String normalizedTarget = normalizeNick(event.targetNick);
        if (isEmpty(normalizedTarget)) {
            return;
        }
        long now = System.currentTimeMillis();
        String dedupKey = normalizeBossKey(event.bossName, normalizedTarget);

        synchronized (lock) {
            if (dedupKey.equals(lastEventKey) && (now - lastEventAtMs) < EVENT_DEDUP_WINDOW_MS) {
                Log.d(TAG, TRACE_PREFIX + " skip duplicate event: key=" + dedupKey);
                return;
            }
            lastEventKey = dedupKey;
            lastEventAtMs = now;
        }

        BossStage currentStage;
        String currentTarget;
        synchronized (lock) {
            currentStage = stage;
            currentTarget = targetNick;
        }
        if (currentStage != BossStage.IDLE) {
            if (normalizeNick(currentTarget).equalsIgnoreCase(normalizedTarget)) {
                Log.d(TAG, TRACE_PREFIX + " scenario already active for target=" + normalizedTarget);
                return;
            }
            Log.d(TAG, TRACE_PREFIX + " new event while active, replace scenario. oldTarget=" + currentTarget
                    + ", newTarget=" + normalizedTarget);
            stopAndRestore("replace_by_new_event", true);
        }

        NeverApi.PinfoCompassSnapshot targetSnapshot = safeGetPinfoSnapshot(normalizedTarget);
        NeverApi.PinfoCompassSnapshot selfSnapshot = safeGetPinfoSnapshot(resolveSelfNick());
        String targetClanToken = normalizeClanToken(targetSnapshot == null ? "" : targetSnapshot.clanToken);
        String selfClanToken = resolveSelfClanTokenWithFallback(selfSnapshot);
        boolean trackCurrentWarsEnabled = isAutoBossTrackCurrentWarsEnabled();

        Log.d(TAG, TRACE_PREFIX + " clan filters: target=" + normalizedTarget
                + ", trackCurrentWars=" + trackCurrentWarsEnabled
                + ", targetClan=" + targetClanToken
                + ", selfClan=" + selfClanToken);
        // Клан-оповещение о самом событии отправляем сразу после распознавания события.
        // Это не зависит от последующих фильтров BD/wars.
        String initialFightFid = resolveFightFidReliable(normalizedTarget, targetSnapshot);
        String initialFightLink = buildFightLogLink(initialFightFid);
        sendClanBossEventMessageIfNeeded(event.bossName, normalizedTarget, selfClanToken, initialFightLink);
        String locationLabel = resolveTargetLocationLabel(normalizedTarget, targetSnapshot);
        String targetHtml = buildTargetNickHtml(normalizedTarget, targetSnapshot);
        String locationPrefix = isEmpty(locationLabel) ? "" : " [" + escapeHtml(locationLabel) + "]";
        writeBossChat("Событие. Монстр \"" + escapeHtml(event.bossName) + "\" напал на игрока "
                + targetHtml + ". Цель " + targetHtml + " в " + buildFightWordHtml(initialFightLink) + "."
                + locationPrefix);

        if (trackCurrentWarsEnabled
                && !isEmpty(selfClanToken)
                && ClanWarsManager.getInstance(appContext).isClanTokenInCurrentWars(selfClanToken)) {
            writeBossChat("Движение к цели остановлено — наш персонаж участвует в текущей клановой войне.");
            Log.d(TAG, TRACE_PREFIX + " wars filter denied by self clan: selfClan=" + selfClanToken
                    + ", target=" + normalizedTarget);
            return;
        }

        if (trackCurrentWarsEnabled
                && !isEmpty(targetClanToken)
                && ClanWarsManager.getInstance(appContext).isClanTokenInCurrentWars(targetClanToken)) {
            String deniedTargetHtml = buildTargetNickHtml(normalizedTarget, targetSnapshot);
            askTargetOnceIfEnabled(normalizedTarget);
            writeBossChat("Движение к цели остановлено — цель " + deniedTargetHtml
                    + " состоит в клане, участвующем в текущей клановой войне.");
            sendClanBossWarDeniedMessageIfNeeded(selfClanToken);
            Log.d(TAG, TRACE_PREFIX + " wars filter denied by wars list: target=" + normalizedTarget
                    + ", targetClan=" + targetClanToken);
            return;
        }
        if (trackCurrentWarsEnabled) {
            Log.d(TAG, TRACE_PREFIX + " wars filter passed: target=" + normalizedTarget
                    + ", targetClan=" + targetClanToken);
        } else {
            Log.d(TAG, TRACE_PREFIX + " wars filter disabled by settings: target=" + normalizedTarget);
        }

        BossScenarioSnapshot newSnapshot = captureSnapshot();
        pauseNonCombatFunctions();

        synchronized (lock) {
            snapshot = newSnapshot;
            targetNick = normalizedTarget;
            bossName = safeTrim(event.bossName);
            originRegNum = currentMapRegNum();
            bossFightFid = initialFightFid;
            bossFightLink = initialFightLink;
            bossFightFidMissingTicks = 0;
            targetFightPollNextAtMs = 0L;
            targetFightPollInFlight = false;
            targetFightPollNick = "";
            bossFightLostPending = false;
            bossTargetClanToken = targetClanToken;
            bossSelfClanToken = selfClanToken;
            stage = BossStage.SEARCHING_TARGET;
            stageStartedAtMs = now;
            actionDueAtMs = 0L;
            protectionSentAtMs = 0L;
            protectionAttempts = 0;
            targetAskAttempts = 0;
            targetAskNextAttemptAtMs = 0L;
        }

        writeBossChat("Запускаем поиск цели.");
        if (isAutoBossAskTargetEnabled()) {
            synchronized (lock) {
                targetAskAttempts = 0;
                targetAskNextAttemptAtMs = now;
            }
            maybeRetryAskTarget(now);
        }
        owner.startSettingsCompassTargetSearch(normalizedTarget, "auto_boss_event");
    }

    private void handleCellHintIfMatchesTarget(String senderNick, String cellRegNum) {
        String normalizedSender = normalizeNick(senderNick);
        if (isEmpty(normalizedSender)) {
            return;
        }
        String target;
        BossStage currentStage;
        synchronized (lock) {
            target = targetNick;
            currentStage = stage;
        }
        if (currentStage != BossStage.SEARCHING_TARGET || isEmpty(target)) {
            return;
        }
        if (!normalizeNick(target).equalsIgnoreCase(normalizedSender)) {
            return;
        }

        Log.d(TAG, TRACE_PREFIX + " target cell hint from chat: sender=" + normalizedSender + ", cell=" + cellRegNum);
        writeBossChat("Получен ответ цели: клетка " + cellRegNum + ". Перестраиваем поиск.");
        owner.setAutoCompassManualCellsCsv(cellRegNum);
        owner.startSettingsCompassTargetSearch(target, "auto_boss_reply_cell_hint");
    }

    private void onTargetFoundInRoom(String source) {
        synchronized (lock) {
            if (stage != BossStage.SEARCHING_TARGET) {
                return;
            }
            stage = BossStage.TARGET_FOUND_WAIT_SCROLL;
            stageStartedAtMs = System.currentTimeMillis();
            actionDueAtMs = stageStartedAtMs + getWaitBeforeScrollMs();
        }
        if (owner.isAutoCompassEnabled()) {
            owner.setAutoCompassEnabled(false);
        }
        if (AppVars.AutoMoving) {
            owner.stopAutoMoving();
        }
        sendClanBossFoundMessageIfNeeded();
        String targetHtml = buildTargetNickHtml(targetNick, null);
        writeBossChat("Цель найдена (" + source + "): "
                + targetHtml + ". Готовим «Свиток Защиты».");
    }

    private void sendProtectionScroll() {
        String target;
        synchronized (lock) {
            if (stage != BossStage.TARGET_FOUND_WAIT_SCROLL && stage != BossStage.WAIT_FIGHT_START) {
                return;
            }
            target = targetNick;
        }
        if (isEmpty(target)) {
            stopAndRestore("empty_target_before_scroll", true);
            return;
        }

        String castTarget = resolveProtectionTargetNick(target);
        if (isEmpty(castTarget)) {
            stopAndRestore("no_alive_protection_target", true);
            return;
        }

        long sentAt = System.currentTimeMillis();
        synchronized (lock) {
            if (stage != BossStage.TARGET_FOUND_WAIT_SCROLL && stage != BossStage.WAIT_FIGHT_START) {
                return;
            }
            protectionAttempts++;
            protectionSentAtMs = sentAt;
            stage = BossStage.WAIT_FIGHT_START;
            stageStartedAtMs = sentAt;
        }

        String targetHtml = buildTargetNickHtml(castTarget, null);
        StringBuilder builder = new StringBuilder();
        builder.append(MainPhp.buildServerChatTimeHtmlExternal());
        builder.append("<font color=#7E57C2><b>[Авто-Боссы]</b></font> ");
        builder.append("Используем «Свиток Защиты» на ");
        builder.append(targetHtml);
        builder.append(".");
        FastActionManager.writeChatMsg(builder.toString());
        FastActionManager.fastAttackZas(castTarget);
        Log.d(TAG, TRACE_PREFIX + " protection scroll sent: target=" + castTarget
                + ", attempts=" + protectionAttempts);
    }

    /**
     * Запускает возврат на исходную клетку либо сразу завершает сценарий,
     * если персонаж уже на исходной клетке.
     */
    private void startReturnOrRestore(String reason) {
        String origin;
        synchronized (lock) {
            origin = originRegNum;
        }
        String current = currentMapRegNum();
        if (!isEmpty(origin) && !origin.equals(current)) {
            owner.setAutoCompassEnabled(false);
            owner.startAutoMoving(origin);
            synchronized (lock) {
                stage = BossStage.RETURNING_TO_ORIGIN;
                stageStartedAtMs = System.currentTimeMillis();
            }
            writeBossChat("Бой завершен, возвращаемся на исходную клетку " + origin + ".");
            Log.d(TAG, TRACE_PREFIX + " return to origin started: reason=" + reason + ", origin=" + origin);
            return;
        }
        stopAndRestore(reason, true);
    }

    private void processReturnStage(long now, long stageStartMs) {
        String origin;
        synchronized (lock) {
            origin = originRegNum;
        }
        if (isEmpty(origin)) {
            stopAndRestore("return_stage_no_origin", true);
            return;
        }
        String current = currentMapRegNum();
        if (origin.equals(current) && !AppVars.AutoMoving) {
            stopAndRestore("return_completed", true);
            return;
        }
        if (!AppVars.AutoMoving && !origin.equals(current)) {
            owner.startAutoMoving(origin);
        }
        if (now - stageStartMs >= RETURN_TIMEOUT_MS) {
            stopAndRestore("return_timeout", true);
        }
    }

    /**
     * Единая точка завершения сценария Авто-Босса.
     *
     * Здесь очищается внутреннее состояние и, при необходимости,
     * восстанавливается снимок ранее активных авто-функций.
     */
    private void stopAndRestore(String reason, boolean restoreSnapshot) {
        BossScenarioSnapshot snapshotToRestore = null;
        String oldTarget;
        synchronized (lock) {
            if (restoreSnapshot) {
                snapshotToRestore = snapshot;
            }
            oldTarget = targetNick;
            snapshot = null;
            stage = BossStage.IDLE;
            targetNick = "";
            bossName = "";
            originRegNum = "";
            stageStartedAtMs = 0L;
            actionDueAtMs = 0L;
            protectionSentAtMs = 0L;
            protectionAttempts = 0;
            targetAskAttempts = 0;
            targetAskNextAttemptAtMs = 0L;
            bossFightFid = "";
            bossFightLink = "";
            bossFightFidMissingTicks = 0;
            targetFightPollNextAtMs = 0L;
            targetFightPollInFlight = false;
            targetFightPollNick = "";
            bossFightLostPending = false;
            bossTargetClanToken = "";
            bossSelfClanToken = "";
        }
        if (owner.isAutoCompassEnabled()) {
            owner.setAutoCompassEnabled(false);
        }
        if (snapshotToRestore != null) {
            restoreSnapshot(snapshotToRestore);
        }
        if (!isEmpty(oldTarget)) {
            String targetHtml = buildTargetNickHtml(oldTarget, null);
            writeBossChat("Сценарий завершен (" + reason + ") для цели "
                    + targetHtml + ".");
        } else {
            writeBossChat("Сценарий завершен (" + reason + ").");
        }
        Log.d(TAG, TRACE_PREFIX + " scenario stopped: reason=" + reason);
    }

    /**
     * Сохраняет текущие флаги и параметры авто-функций перед стартом сценария.
     */
    private BossScenarioSnapshot captureSnapshot() {
        BossScenarioSnapshot snapshot = new BossScenarioSnapshot();
        snapshot.autoFishEnabled = owner.isAutoFishEnabled();
        snapshot.autoBaitEnabled = owner.isAutoBaitEnabled();
        snapshot.autoSkinEnabled = owner.isAutoSkinEnabled();
        snapshot.autoAttackEnabled = owner.isAutoAttackEnabled();
        snapshot.autoCompassEnabled = owner.isAutoCompassEnabled();
        snapshot.autoInvisibleEnabled = owner.isAutoInvisibleEnabled();
        snapshot.locationTrackingEnabled = owner.isLocationTrackingEnabled();
        snapshot.autoDetectEnabled = owner.isAutoDetectEnabled();
        snapshot.autoSummonEnabled = owner.isAutoSummonEnabled();
        snapshot.autoDrinkEnabled = owner.isAutoDrinkEnabled();
        snapshot.autoMovingEnabled = owner.isAutoMovingEnabled();
        snapshot.autoMovingDestination = AppVars.AutoMovingDestinaton == null ? "" : AppVars.AutoMovingDestinaton.trim();
        snapshot.autoTreasureEnabled = owner.isAutoTreasureEnabled();
        snapshot.autoCutEnabled = owner.isAutoCutEnabled();
        snapshot.autoRefreshEnabled = owner.isAutoRefreshEnabled();

        snapshot.autoCompassTargetNick = owner.getAutoCompassTargetNick();
        snapshot.autoCompassHuntMode = owner.isAutoCompassHuntMode();
        snapshot.autoCompassPollIntervalSec = owner.getAutoCompassPollIntervalSec();
        snapshot.autoCompassManualCellsCsv = owner.getAutoCompassManualCellsCsv();
        return snapshot;
    }

    /**
     * Ставит на паузу авто-функции, которые могут конфликтовать с поиском/маршрутом Авто-Босса.
     *
     * Важно:
     * - Авто-Бой и Авто-Лечение здесь не выключаются специально.
     */
    private void pauseNonCombatFunctions() {
        owner.setAutoFishEnabled(false);
        owner.setAutoBaitEnabled(false);
        owner.setAutoSkinEnabled(false);
        owner.setAutoAttackEnabled(false);
        owner.setAutoCompassEnabled(false);
        owner.setAutoInvisibleEnabled(false);
        owner.setLocationTrackingEnabled(false);
        owner.setAutoDetectEnabled(false);
        owner.setAutoSummonEnabled(false);
        owner.setAutoDrinkEnabled(false);
        owner.stopAutoMoving();
        owner.setAutoTreasureEnabled(false);
        owner.setAutoCutEnabled(false);
        owner.setAutoRefreshEnabled(false);
    }

    /**
     * Восстанавливает исходные состояния авто-функций после сценария.
     * Все переключения делаются только через AutoFunctionsManager, чтобы
     * не дублировать side-effects и существующие guard-ветки.
     */
    private void restoreSnapshot(BossScenarioSnapshot snapshot) {
        // Восстанавливаем в том же модуле и через те же setter-ы, чтобы не плодить дубль-логики.
        owner.setAutoFishEnabled(snapshot.autoFishEnabled);
        owner.setAutoBaitEnabled(snapshot.autoBaitEnabled);
        owner.setAutoSkinEnabled(snapshot.autoSkinEnabled);
        owner.setAutoAttackEnabled(snapshot.autoAttackEnabled);
        owner.setAutoInvisibleEnabled(snapshot.autoInvisibleEnabled);
        owner.setLocationTrackingEnabled(snapshot.locationTrackingEnabled);
        owner.setAutoDetectEnabled(snapshot.autoDetectEnabled);
        owner.setAutoSummonEnabled(snapshot.autoSummonEnabled);
        owner.setAutoDrinkEnabled(snapshot.autoDrinkEnabled);
        owner.setAutoTreasureEnabled(snapshot.autoTreasureEnabled);
        owner.setAutoCutEnabled(snapshot.autoCutEnabled);
        owner.setAutoRefreshEnabled(snapshot.autoRefreshEnabled);

        owner.setAutoCompassTargetNick(snapshot.autoCompassTargetNick);
        owner.setAutoCompassHuntMode(snapshot.autoCompassHuntMode);
        owner.setAutoCompassPollIntervalSec(snapshot.autoCompassPollIntervalSec);
        owner.setAutoCompassManualCellsCsv(snapshot.autoCompassManualCellsCsv);
        owner.setAutoCompassEnabled(snapshot.autoCompassEnabled);

        if (snapshot.autoMovingEnabled && !isEmpty(snapshot.autoMovingDestination)) {
            owner.startAutoMoving(snapshot.autoMovingDestination);
        } else {
            owner.stopAutoMoving();
        }
    }

    private BossEvent parseBossEvent(String messageHtml) {
        String plain = toPlainText(messageHtml);
        if (isEmpty(plain)) {
            return null;
        }
        Matcher matcher = BOSS_EVENT_PATTERN_FLEX.matcher(plain);
        if (!matcher.find()) {
            String lower = plain.toLowerCase(Locale.ROOT);
            boolean bossLikeMessage = lower.contains("случайное событие")
                    || (lower.contains("монстр") && lower.contains("напал на игрока"));
            if (bossLikeMessage) {
                String snippet = plain.length() > 280 ? plain.substring(0, 280) + "..." : plain;
                Log.d(TAG, TRACE_PREFIX + " parse miss boss-event: " + snippet);
            }
            return null;
        }
        String boss = safeTrim(matcher.group(1));
        String rawTarget = safeTrim(matcher.group(2));
        String target = normalizeBossTargetNick(rawTarget);
        if (isEmpty(target)) {
            return null;
        }
        Log.d(TAG, TRACE_PREFIX + " parse boss-event: rawTarget=" + rawTarget
                + ", normalizedTarget=" + target + ", boss=" + boss);
        return new BossEvent(boss, target);
    }

    /**
     * Нормализация ника цели из системного сообщения о Боссе.
     * Удаляет служебную пунктуацию в хвосте и оставляет исходные спецсимволы ника.
     */
    private String normalizeBossTargetNick(String rawNick) {
        String value = normalizeNick(rawNick);
        value = value.replaceAll("\\s*\\[\\s*\\d{1,3}\\s*]$", "").trim();
        while (!value.isEmpty()) {
            char tail = value.charAt(value.length() - 1);
            if (tail == '.' || tail == ',' || tail == ':' || tail == ';'
                    || tail == ')' || tail == ']' || tail == '}'
                    || tail == '!' || tail == '?'
                    || tail == '"' || tail == '\'' || tail == '»') {
                value = value.substring(0, value.length() - 1).trim();
                continue;
            }
            break;
        }
        while (!value.isEmpty()) {
            char head = value.charAt(0);
            if (head == '(' || head == '[' || head == '{'
                    || head == '"' || head == '\'' || head == '«') {
                value = value.substring(1).trim();
                continue;
            }
            break;
        }
        return normalizeNick(value);
    }

    private String extractSenderNick(String messageHtml) {
        if (isEmpty(messageHtml)) {
            return "";
        }
        Matcher matcher = SPAN_NICK_PATTERN.matcher(messageHtml);
        if (!matcher.find()) {
            return "";
        }
        String raw = safeTrim(matcher.group(1));
        while (raw.startsWith("%")) {
            raw = raw.substring(1);
        }
        return normalizeNick(raw);
    }

    private String resolveTargetLocationLabel(String targetNick, NeverApi.PinfoCompassSnapshot snapshot) {
        if (snapshot != null && !isEmpty(snapshot.locationName)) {
            if (!isEmpty(snapshot.locationRegion)) {
                return snapshot.locationRegion + " [" + snapshot.locationName + "]";
            }
            return snapshot.locationName;
        }
        if (isEmpty(targetNick)) {
            return "";
        }
        try {
            AutoFunctionsManager.CompassLocationResolveResult resolved = owner.resolveAutoCompassLocation(targetNick);
            if (resolved != null && resolved.success && !isEmpty(resolved.locationLabel)) {
                return resolved.locationLabel;
            }
        } catch (Exception e) {
            Log.w(TAG, TRACE_PREFIX + " resolveTargetLocationLabel failed: target=" + targetNick, e);
        }
        return owner.getAutoCompassLastLocationLabel();
    }

    /**
     * Безопасное чтение pinfo-снимка.
     * Ошибки сети логируются, но не прерывают state-machine сценария.
     */
    private NeverApi.PinfoCompassSnapshot safeGetPinfoSnapshot(String nick) {
        if (isEmpty(nick)) {
            return null;
        }
        try {
            return NeverApi.getPinfoCompassSnapshot(nick);
        } catch (Exception e) {
            Log.w(TAG, TRACE_PREFIX + " snapshot request failed: nick=" + nick, e);
            return null;
        }
    }

    /**
     * Возвращает максимально надёжный {@code fid} боя цели.
     *
     * Источники по приоритету:
     * 1) уже полученный pinfo snapshot;
     * 2) fallback через NeverApi.getAll(targetNick).
     */
    private String resolveFightFidReliable(String targetNick, NeverApi.PinfoCompassSnapshot snapshot) {
        String fromSnapshot = normalizeFightFid(snapshot == null ? "" : snapshot.fightFid);
        if (!isEmpty(fromSnapshot)) {
            return fromSnapshot;
        }
        if (isEmpty(targetNick)) {
            return "";
        }
        try {
            NeverApi.UserInfo info = NeverApi.getAll(targetNick);
            return normalizeFightFid(info == null ? "" : info.fightLog);
        } catch (Exception e) {
            Log.w(TAG, TRACE_PREFIX + " failed to resolve fight fid via getAll: target=" + targetNick, e);
            return "";
        }
    }

    private String resolveSelfNick() {
        if (AppVars.Profile == null || AppVars.Profile.UserNick == null) {
            return "";
        }
        return normalizeNick(AppVars.Profile.UserNick);
    }

    private String normalizeClanToken(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').trim();
    }

    /**
     * Возвращает clanToken нашего профиля с fallback на кэш.
     *
     * Зачем нужен fallback:
     * - `pinfo` иногда кратковременно недоступен/непарсится в момент события Босса;
     * - без fallback клан-уведомления `%clan%` ошибочно пропускаются как "вне клана".
     *
     * Правило:
     * - если live-token получен, обновляем runtime+prefs кэш;
     * - если live-token пустой, используем последний валидный кэш.
     */
    private String resolveSelfClanTokenWithFallback(NeverApi.PinfoCompassSnapshot selfSnapshot) {
        String liveToken = normalizeClanToken(selfSnapshot == null ? "" : selfSnapshot.clanToken);
        if (!isEmpty(liveToken)) {
            if (!liveToken.equals(cachedSelfClanToken)) {
                cachedSelfClanToken = liveToken;
                prefs.edit().putString(PREF_AUTO_BOSS_SELF_CLAN_TOKEN_CACHE, liveToken).apply();
                Log.d(TAG, TRACE_PREFIX + " self clan token cache update: " + liveToken);
            }
            return liveToken;
        }

        String cached = normalizeClanToken(cachedSelfClanToken);
        if (!isEmpty(cached)) {
            Log.d(TAG, TRACE_PREFIX + " self clan token fallback from runtime cache: " + cached);
            return cached;
        }

        String persistentCache = normalizeClanToken(
                prefs.getString(PREF_AUTO_BOSS_SELF_CLAN_TOKEN_CACHE, "")
        );
        if (!isEmpty(persistentCache)) {
            cachedSelfClanToken = persistentCache;
            Log.d(TAG, TRACE_PREFIX + " self clan token fallback from prefs cache: " + persistentCache);
            return persistentCache;
        }
        return "";
    }

    private String normalizeFightFid(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.isEmpty() || "0".equals(text) || "null".equalsIgnoreCase(text)) {
            return "";
        }
        Matcher digits = Pattern.compile("(\\d{1,16})").matcher(text);
        return digits.find() ? digits.group(1) : text;
    }

    private String buildFightLogLink(String fid) {
        String safeFid = normalizeFightFid(fid);
        if (isEmpty(safeFid)) {
            return "";
        }
        return "http://neverlands.ru/logs.fcg?fid=" + safeFid;
    }

    private String buildFightWordHtml(String fightLink) {
        if (isEmpty(fightLink)) {
            return "бою";
        }
        return "<a href=" + escapeHtml(fightLink) + ">бою</a>";
    }
    /**
     * Нормализует часть "в бою" для server-payload (`%clan%`) без HTML.
     *
     * Причина:
     * - `<a href=...>` внутри payload клан-чата сервер может отфильтровать/исказить;
     * - формат `[[[fid]]]` уже используется в проекте и безопасно превращается в ссылку
     *   при отображении входящего сообщения.
     */
    private String normalizeClanFightPartForSend(String fightPart) {
        String safe = safeTrim(fightPart);
        if (isEmpty(safe)) {
            return "\u0432 \u0431\u043e\u044e";
        }
        String lower = safe.toLowerCase(Locale.ROOT);
        if (!lower.contains("<a")) {
            return safe;
        }
        Matcher matcher = FIGHT_FID_IN_LINK_PATTERN.matcher(safe);
        if (matcher.find()) {
            String fid = normalizeFightFid(matcher.group(1));
            if (!isEmpty(fid)) {
                return "\u0432 \u0431\u043e\u044e [[[" + fid + "]]]";
            }
        }
        return "\u0432 \u0431\u043e\u044e";
    }

    private String getCurrentFightWordHtml() {
        String link;
        synchronized (lock) {
            link = bossFightLink;
        }
        return buildFightWordHtml(link);
    }

    /**
     * Единый рендер ника для сообщений Авто-Босса.
     *
     * Приоритет:
     * 1) готовый рендер из {@link RoomManager#buildUnifiedChatNickHtml(String)};
     * 2) fallback-сборка по данным pinfo/контактов (если room-рендер недоступен).
     */
    private String buildTargetNickHtml(String nick, NeverApi.PinfoCompassSnapshot snapshot) {
        String cleanNick = normalizeNick(nick);
        if (isEmpty(cleanNick)) {
            return "";
        }
        String rendered = RoomManager.buildUnifiedChatNickHtml(cleanNick);
        if (!isEmpty(rendered)) {
            return rendered;
        }
        NeverApi.PinfoCompassSnapshot resolvedSnapshot = snapshot != null ? snapshot : safeGetPinfoSnapshot(cleanNick);
        int level = 0;
        if (resolvedSnapshot != null && resolvedSnapshot.level != null && resolvedSnapshot.level > 0) {
            level = resolvedSnapshot.level;
        } else {
            level = ContactsManager.getLevelOfContact(cleanNick);
        }
        int classId = parseClassIdSafe(ContactsManager.getClassIdOfContact(cleanNick));
        String color = "#000000";
        if (classId == 1) {
            color = "#FF0000";
        } else if (classId == 2) {
            color = "#008000";
        }
        String escapedNick = escapeHtml(cleanNick);
        String nickForJs = escapeJsSingleQuoted(cleanNick);
        String levelHtml = level > 0
                ? " [<font class=nickname color=\"" + color + "\">" + level + "</font>]"
                : "";
        return "<a href=\"#\" onclick=\"top.say_private('" + nickForJs
                + "');\"><img src=http://image.neverlands.ru/chat/private.gif width=11 height=12 border=0 align=absmiddle></a>&nbsp;"
                + "<a class=\"activenick\" href=\"#\" onclick=\"top.say_to('" + nickForJs
                + "');\"><font class=nickname color=\"" + color + "\"><b>"
                + escapedNick + "</b></font></a>"
                + levelHtml
                + "<a href=\"http://neverlands.ru/pinfo.cgi?" + escapedNick
                + "\" onclick=\"window.open(this.href);\"><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>";
    }

    /**
     * Повторяет вопрос цели в чат о текущей клетке.
     *
     * Ограничения:
     * - не более {@code TARGET_CHAT_ASK_MAX_ATTEMPTS};
     * - соблюдается пауза {@code TARGET_CHAT_ASK_RETRY_MS};
     * - отправка выполняется только когда chat-frame готов.
     */
    private void maybeRetryAskTarget(long now) {
        String target;
        int attempts;
        long nextAttemptAt;
        BossStage currentStage;
        synchronized (lock) {
            currentStage = stage;
            target = targetNick;
            attempts = targetAskAttempts;
            nextAttemptAt = targetAskNextAttemptAtMs;
        }
        if (currentStage != BossStage.SEARCHING_TARGET || isEmpty(target)) {
            return;
        }
        if (attempts >= TARGET_CHAT_ASK_MAX_ATTEMPTS || nextAttemptAt <= 0L || now < nextAttemptAt) {
            return;
        }

        if (!isChatSendReady()) {
            synchronized (lock) {
                targetAskAttempts = attempts + 1;
                if (targetAskAttempts >= TARGET_CHAT_ASK_MAX_ATTEMPTS) {
                    targetAskNextAttemptAtMs = 0L;
                } else {
                    targetAskNextAttemptAtMs = now + TARGET_CHAT_ASK_RETRY_MS;
                }
            }
            Log.d(TAG, TRACE_PREFIX + " ask target delayed: chat frame not ready, target=" + target
                    + ", attempt=" + (attempts + 1));
            return;
        }

        String message = "%<" + target + "> Подскажи на какой клетке Босс?";
        // Отправляем private message с задержкой 500ms после clan message
        final String privateMsg = message;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Chat.sendMessageToServer(privateMsg);
            Log.d(TAG, TRACE_PREFIX + " ask target sent (delayed): target=" + target + ", message=" + privateMsg);
        }, CLAN_PRIVATE_MESSAGE_DELAY_MS);
        synchronized (lock) {
            targetAskAttempts = TARGET_CHAT_ASK_MAX_ATTEMPTS;
            targetAskNextAttemptAtMs = 0L;
        }
        Log.d(TAG, TRACE_PREFIX + " ask target scheduled (500ms delay): target=" + target);
    }

    /**
     * Однократно задаёт цели вопрос о клетке без запуска SEARCHING-цикла.
     *
     * Используется в ветках, где движение к цели заблокировано фильтрами (БД/войны),
     * но уведомить цель в приват всё равно нужно.
     */
    private void askTargetOnceIfEnabled(String target) {
        String normalizedTarget = normalizeNick(target);
        if (!isAutoBossAskTargetEnabled() || isEmpty(normalizedTarget)) {
            return;
        }
        if (!isChatSendReady()) {
            Log.d(TAG, TRACE_PREFIX + " ask target skipped (single): chat frame not ready, target=" + normalizedTarget);
            return;
        }
        String message = "%<" + normalizedTarget + "> Подскажи на какой клетке Босс?";
        // Отправляем private message с задержкой 500ms для DDoS protection
        final String singleMsg = message;
        final String finalNorm = normalizedTarget;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Chat.sendMessageToServer(singleMsg);
            String traceMsg = "[BOSS_PRIVATE_MSG_SENT] Single ask, target=" + finalNorm;
            FileLogger.trace("boss_auto", traceMsg);
            Log.d(TAG, TRACE_PREFIX + " ask target sent (single, delayed): target=" + finalNorm + ", message=" + singleMsg);
        }, CLAN_PRIVATE_MESSAGE_DELAY_MS);
        String queueMsg = "[BOSS_PRIVATE_MSG_QUEUED] Single ask, target=" + normalizedTarget + ", delay=500ms";
        FileLogger.trace("boss_auto", queueMsg);
        Log.d(TAG, TRACE_PREFIX + " ask target queued (single, 500ms delay): target=" + normalizedTarget);
    }

    /**
     * Во время поиска контролирует, что цель всё ещё находится в бою.
     *
     * Логика:
     * - если {@code fightFid} присутствует — обновляем кэш ссылки на бой;
     * - если {@code fightFid} временно пропал — даём 1 grace-тик;
     * - если отсутствует второй тик подряд — отменяем сценарий.
     */
    private boolean monitorTargetFightStateDuringSearch() {
        String target;
        synchronized (lock) {
            if (stage != BossStage.SEARCHING_TARGET) {
                return false;
            }
            target = targetNick;
        }
        if (isEmpty(target)) {
            return true;
        }

        requestTargetFightPollIfNeeded(target);

        boolean fightLostPending;
        synchronized (lock) {
            fightLostPending = bossFightLostPending;
        }
        if (!fightLostPending) {
            return true;
        }

        writeBossChat("Действие отменено, цель уже не в " + getCurrentFightWordHtml() + ".");
        // Даже если цель ушла из боя во время SEARCHING_TARGET, нужно вернуть персонажа
        // в исходную клетку (если мы уже сдвинулись), а затем восстановить snapshot.
        startReturnOrRestore("target_left_fight");
        return false;
    }

    /**
     * Определяет, на кого применять «Свиток Защиты».
     *
     * Приоритет:
     * 1) исходная цель, если она жива;
     * 2) первый живой союзник из того же боя (fallback).
     *
     * Если живых союзников не осталось, возвращается пустая строка
     * и сценарий должен быть завершён вызывающей стороной.
     */
    /**
     * Асинхронный poll pinfo цели во время SEARCHING_TARGET.
     *
     * Важно: tick Auto-Boss запускается с UI-потока (через foreground-service -> runOnUiThread),
     * поэтому сетевой вызов NeverApi.getPinfoCompassSnapshot(...) нельзя выполнять синхронно
     * внутри tick. Иначе возникает NetworkOnMainThreadException и ломается цикл поиска.
     */
    private void requestTargetFightPollIfNeeded(String target) {
        if (isEmpty(target)) {
            return;
        }
        long now = System.currentTimeMillis();
        final String targetSnapshot;
        synchronized (lock) {
            if (stage != BossStage.SEARCHING_TARGET) {
                return;
            }
            if (targetFightPollInFlight || now < targetFightPollNextAtMs) {
                return;
            }
            targetFightPollInFlight = true;
            targetFightPollNextAtMs = now + TARGET_FIGHT_POLL_INTERVAL_MS;
            targetFightPollNick = target;
            targetSnapshot = target;
        }

        Thread worker = new Thread(() -> {
            try {
                NeverApi.PinfoCompassSnapshot snapshot = safeGetPinfoSnapshot(targetSnapshot);
                applyTargetFightPollResult(targetSnapshot, snapshot);
            } finally {
                synchronized (lock) {
                    targetFightPollInFlight = false;
                }
            }
        }, "boss_target_fight_poll");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Применение результата асинхронного poll-а состояния боя цели.
     *
     * Логика соответствует прежнему синхронному варианту:
     * - если fightFid есть -> обновляем кеш и сбрасываем grace;
     * - если fightFid пропал -> первый тик grace, второй тик помечает отмену сценария.
     *
     * Сам stopAndRestore выполняется в monitorTargetFightStateDuringSearch (UI-tick),
     * чтобы не разрывать существующий контур жизненного цикла.
     */
    private void applyTargetFightPollResult(String target, NeverApi.PinfoCompassSnapshot snapshot) {
        if (isEmpty(target) || snapshot == null) {
            return;
        }
        String latestFid = normalizeFightFid(snapshot.fightFid);
        int missingTicks = 0;
        synchronized (lock) {
            if (stage != BossStage.SEARCHING_TARGET) {
                return;
            }
            if (!normalizeNick(targetNick).equalsIgnoreCase(normalizeNick(target))) {
                return;
            }
            if (!isEmpty(latestFid)) {
                bossFightFid = latestFid;
                bossFightLink = buildFightLogLink(latestFid);
                bossFightFidMissingTicks = 0;
                bossFightLostPending = false;
            } else {
                bossFightFidMissingTicks++;
                // Ограничиваем счетчик максимальным значением для защиты от переполнения
                if (bossFightFidMissingTicks > BOSS_FIGHT_FID_MISSING_MAX_TICKS) {
                    bossFightFidMissingTicks = BOSS_FIGHT_FID_MISSING_MAX_TICKS;
                }
                missingTicks = bossFightFidMissingTicks;
                if (missingTicks > 1) {
                    bossFightLostPending = true;
                }
            }
        }
        if (isEmpty(latestFid) && missingTicks == 1) {
            Log.d(TAG, TRACE_PREFIX + " target fight fid missing, wait grace tick: target=" + target);
        }
    }

    private String resolveProtectionTargetNick(String initialTarget) {
        String target = normalizeNick(initialTarget);
        if (isEmpty(target)) {
            return "";
        }

        String fid;
        synchronized (lock) {
            fid = bossFightFid;
        }
        if (isEmpty(fid)) {
            NeverApi.PinfoCompassSnapshot snapshot = safeGetPinfoSnapshot(target);
            if (snapshot != null) {
                fid = normalizeFightFid(snapshot.fightFid);
                if (!isEmpty(fid)) {
                    synchronized (lock) {
                        bossFightFid = fid;
                        bossFightLink = buildFightLogLink(fid);
                        bossFightFidMissingTicks = 0;
                    }
                }
            }
        }

        if (isEmpty(fid)) {
            return target;
        }

        String flogHtml;
        try {
            flogHtml = NeverApi.getFlog(fid);
        } catch (Exception e) {
            Log.w(TAG, TRACE_PREFIX + " failed to load flog for fid=" + fid, e);
            return target;
        }
        if (isEmpty(flogHtml)) {
            return target;
        }
        if (flogHtml.contains("var off = 1;")) {
            writeBossChat("Действие отменено, цель уже не в " + buildFightWordHtml(buildFightLogLink(fid)) + ".");
            return "";
        }

        List<FightAllyState> allies = parseFightAlliesFromLog(flogHtml);
        if (allies.isEmpty()) {
            return target;
        }

        FightAllyState initialState = null;
        FightAllyState firstAlive = null;
        String normalizedTarget = normalizeNick(target);
        for (FightAllyState ally : allies) {
            if (normalizeNick(ally.nick).equalsIgnoreCase(normalizedTarget)) {
                initialState = ally;
            }
            if (firstAlive == null && ally.curHp > 0) {
                firstAlive = ally;
            }
        }

        if (initialState != null && initialState.curHp > 0) {
            return initialState.nick;
        }
        if (firstAlive == null) {
            writeBossChat("Действие отменено — в бою не найдено живых союзников для защиты.");
            return "";
        }
        if (initialState != null && initialState.curHp <= 0) {
            String aliveHtml = buildTargetNickHtml(firstAlive.nick, null);
            writeBossChat("Исходная цель мертва, применяем Свиток Защиты на " + aliveHtml + ".");
        }
        return firstAlive.nick;
    }

    /**
     * Парсит союзную часть лога боя и извлекает состояния участников.
     * Используется fallback-логикой выбора живой цели для защиты.
     */
    private List<FightAllyState> parseFightAlliesFromLog(String flogHtml) {
        ArrayList<FightAllyState> result = new ArrayList<>();
        if (isEmpty(flogHtml)) {
            return result;
        }
        String plain = toPlainText(flogHtml);
        if (isEmpty(plain)) {
            return result;
        }
        String lower = plain.toLowerCase(Locale.ROOT);
        int startIndex = lower.indexOf("участники боя");
        if (startIndex < 0) {
            startIndex = 0;
        }
        int againstIndex = lower.indexOf(" против ", startIndex);
        String alliesPart = againstIndex > startIndex
                ? plain.substring(startIndex, againstIndex)
                : plain;

        Matcher matcher = FIGHT_ALLY_PATTERN.matcher(alliesPart);
        while (matcher.find()) {
            String nick = normalizeNick(matcher.group(1));
            int curHp = parseIntSafe(matcher.group(2));
            int maxHp = parseIntSafe(matcher.group(3));
            if (isEmpty(nick) || maxHp <= 0) {
                continue;
            }
            result.add(new FightAllyState(nick, curHp, maxHp));
        }
        return result;
    }

    private int parseIntSafe(String value) {
        if (isEmpty(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int parseClassIdSafe(String value) {
        if (isEmpty(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean isChatSendReady() {
        MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
        return activity != null
                && activity.binding != null
                && activity.binding.appBarMain != null
                && activity.binding.appBarMain.contentMain != null
                && activity.binding.appBarMain.contentMain.chatButtonsWebview != null;
    }

    private String extractCellRegNum(String plainText) {
        if (isEmpty(plainText)) {
            return "";
        }
        Matcher matcher = CELL_PATTERN.matcher(plainText);
        if (!matcher.find()) {
            return "";
        }
        return safeTrim(matcher.group());
    }

    private boolean containsFightCompleted(String plainText) {
        String normalized = plainText.toLowerCase(Locale.ROOT);
        return normalized.contains("поединок заверш");
    }

    private boolean isFightLikelyActive() {
        long now = System.currentTimeMillis();
        if (now - AppVars.LastFightPulseAtMs <= 12_000L) {
            return true;
        }
        String fightLink = AppVars.FightLink == null ? "" : AppVars.FightLink.toLowerCase(Locale.ROOT);
        if (fightLink.contains("fight") || fightLink.contains("act=7")) {
            return true;
        }
        String topUrl = AppVars.url_main_top == null ? "" : AppVars.url_main_top.toLowerCase(Locale.ROOT);
        return topUrl.contains("go=inf") && topUrl.contains("main.php");
    }

    private String currentMapRegNum() {
        if (AppVars.Profile == null || AppVars.Profile.MapLocation == null) {
            return "";
        }
        return safeTrim(AppVars.Profile.MapLocation);
    }

    private String toPlainText(String html) {
        if (isEmpty(html)) {
            return "";
        }
        return html
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeNick(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String normalizeBossKey(String boss, String target) {
        return (safeTrim(boss).toLowerCase(Locale.ROOT) + "|" + normalizeNick(target).toLowerCase(Locale.ROOT)).trim();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeJsSingleQuoted(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "");
    }

    private void writeBossChat(String message) {
        if (isEmpty(message)) {
            return;
        }
        FileLogger.trace("auto_boss", message);
        FastActionManager.writeChatMsg(
                LOCAL_CHAT_MARKER
                        + MainPhp.buildServerChatTimeHtmlExternal()
                        + "<font color=#7E57C2><b>[Авто-Боссы]</b></font> "
                        + message
        );
    }

    /**
     * Отправляет клановое уведомление о старте события Босса, если включена соответствующая опция.
     * Если наш профиль вне клана, вместо отправки в клан-чат пишет локальное уведомление.
     */
    /**
     * Отправка клан-уведомления о старте события босса.
     *
     * Зависимости:
     * - `resolveAutoCompassLocation(...)` для списка возможных клеток;
     * - `Chat.sendMessageToServer(...)` для отправки в `%clan%`;
     * - проверка наличия клана выполняется по `selfClanToken` из pinfo.
     *
     * Если персонаж вне клана, клан-сообщение не отправляется:
     * в локальный чат пишется причина отмены.
     */
    private void sendClanBossEventMessageIfNeeded(String bossName, String targetNick, String selfClanToken, String fightLink) {
        if (!isAutoBossClanNotifyEnabled()) {
            Log.d(TAG, TRACE_PREFIX + " clan notify event skipped: disabled by settings");
            FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_MSG_SKIPPED] reason=disabled_by_settings");
            return;
        }
        if (isEmpty(normalizeClanToken(selfClanToken))) {
            Log.d(TAG, TRACE_PREFIX + " clan notify event skipped: self clan token is empty");
            FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_MSG_SKIPPED] reason=empty_self_clan_token");
            writeBossChat("Отправка в клан-чат невозможна: отсутствует значок клана (selfClanToken пустой).");
            return;
        }

        String normalizedTarget = normalizeNick(targetNick);
        String cellsCsv = "";
        try {
            AutoFunctionsManager.CompassLocationResolveResult resolved = owner.resolveAutoCompassLocation(normalizedTarget);
            if (resolved != null && resolved.success) {
                cellsCsv = safeTrim(resolved.cellsCsv);
            }
        } catch (Exception e) {
            Log.w(TAG, TRACE_PREFIX + " clan notify resolve failed: target=" + normalizedTarget, e);
        }
        if (isEmpty(cellsCsv)) {
            cellsCsv = safeTrim(owner.getAutoCompassCellsCsv());
        }
        if (isEmpty(cellsCsv)) {
            cellsCsv = "не определены";
        }

        String safeBossName = safeTrim(bossName);
        if (isEmpty(safeBossName)) {
            safeBossName = "Босс";
        }
        String safeFightLink = safeTrim(fightLink);
        String fightPart = isEmpty(safeFightLink) ? "в бою" : "в " + buildFightWordHtml(safeFightLink);
        String message = buildClanBossEventMessage(safeBossName, cellsCsv, fightPart, normalizedTarget);
        boolean chatReady = isChatSendReady();
        if (!chatReady) {
            Log.w(TAG, TRACE_PREFIX + " clan notify event CANCELED: chatButtonsWebview not ready, target="
                    + normalizedTarget + ", cells=" + cellsCsv + ". Message will be retried by Chat.sendMessageToServer");
            FileLogger.log("[BossAuto.sendClanBossEventMessageIfNeeded] WebView not ready, message queued for retry: " + message.substring(0, Math.min(100, message.length())));
            writeBossChat("Клан-сообщение добавлено в очередь. Будет отправлено при подготовке чата.");
        }
        Log.d(TAG, TRACE_PREFIX + " clan notify event payload: len=" + message.length() + ", maxLen=" + CLAN_EVENT_CHAT_MAX_LEN);
        FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_MSG_PAYLOAD] len=" + message.length() + ", maxLen=" + CLAN_EVENT_CHAT_MAX_LEN);
        // Отправляем clan message с задержкой 1 сек для DDoS protection
        // (буферизация потока pinfo + compass + других запросов)
        final String clanMsg = message;
        final String finalNorm = normalizedTarget;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Chat.sendMessageToServer(clanMsg);
            String traceMsg = "[BOSS_CLAN_MSG_SENT] Delayed 1s, target=" + finalNorm + ", msg=" + clanMsg.substring(0, Math.min(80, clanMsg.length()));
            FileLogger.trace("boss_auto", traceMsg);
            FileLogger.log("[BossAuto.sendClanBossEventMessageIfNeeded] Sent to Chat.sendMessageToServer (after 1s delay): " + clanMsg.substring(0, Math.min(100, clanMsg.length())));
            Log.d(TAG, TRACE_PREFIX + " clan notify event sent (delayed): target=" + finalNorm);
        }, CLAN_NOTIFY_DELAY_MS);
        String schedMsg = "[BOSS_CLAN_MSG_SCHEDULED] 1s delay, target=" + normalizedTarget + ", cells=" + cellsCsv + ", chatReady=" + chatReady;
        FileLogger.trace("boss_auto", schedMsg);
        Log.d(TAG, TRACE_PREFIX + " clan notify event scheduled (1s delay): target=" + normalizedTarget + ", cells=" + cellsCsv
                + ", chatReady=" + chatReady);
    }

    private String buildClanBossEventMessage(String bossName, String cellsCsv, String fightPart, String normalizedTarget) {
        fightPart = normalizeClanFightPartForSend(fightPart);
        String prefix = "%clan% \"" + bossName + "\" возможно на клетках: ";
        String suffix = " " + fightPart + " с персонажем '" + normalizedTarget + "'.";
        String normalizedCells = safeTrim(cellsCsv).replaceAll("\\s+", " ");
        if (isEmpty(normalizedCells)) {
            normalizedCells = "не определены";
        }

        String full = prefix + normalizedCells + suffix;
        if (full.length() <= CLAN_EVENT_CHAT_MAX_LEN) {
            return full;
        }

        String[] cells = normalizedCells.split("\\s*,\\s*");
        StringBuilder compact = new StringBuilder();
        boolean truncated = false;
        for (String cell : cells) {
            String safeCell = safeTrim(cell);
            if (safeCell.isEmpty()) {
                continue;
            }
            String candidateCells = compact.length() == 0 ? safeCell : compact + ", " + safeCell;
            String candidateMessage = prefix + candidateCells + "..." + suffix;
            if (candidateMessage.length() > CLAN_EVENT_CHAT_MAX_LEN) {
                truncated = true;
                break;
            }
            if (compact.length() == 0) {
                compact.append(safeCell);
            } else {
                compact.append(", ").append(safeCell);
            }
        }

        if (compact.length() == 0) {
            int allowedCellsLen = CLAN_EVENT_CHAT_MAX_LEN - prefix.length() - suffix.length() - 3;
            if (allowedCellsLen <= 0) {
                return (prefix + suffix).substring(0, Math.min(CLAN_EVENT_CHAT_MAX_LEN, (prefix + suffix).length()));
            }
            String shortCells = normalizedCells.substring(0, Math.min(allowedCellsLen, normalizedCells.length())).trim();
            if (shortCells.isEmpty()) {
                shortCells = "не определены";
            }
            truncated = shortCells.length() < normalizedCells.length();
            String result = prefix + shortCells + (truncated ? "..." : "") + suffix;
            FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_MSG_TRIM] fallback=true, truncated=" + truncated + ", len=" + result.length());
            return result;
        }

        String result = prefix + compact + (truncated ? "..." : "") + suffix;
        FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_MSG_TRIM] fallback=false, truncated=" + truncated + ", len=" + result.length());
        return result;
    }

    /**
     * Отправляет клановое уведомление о точной клетке Босса после обнаружения цели в комнате.
     * Если наш профиль вне клана, вместо отправки в клан-чат пишет локальное уведомление.
     */
    /**
     * Отправка клан-уведомления с точной клеткой после обнаружения цели.
     *
     * Источник клетки:
     * - основной: `currentMapRegNum()`;
     * - fallback: `AppVars.AutoMovingDestinaton`, если карта ещё не синхронизирована.
     */
    private void sendClanBossFoundMessageIfNeeded() {
        if (!isAutoBossClanNotifyEnabled()) {
            Log.d(TAG, TRACE_PREFIX + " clan notify found skipped: disabled by settings");
            return;
        }
        String selfClanToken;
        synchronized (lock) {
            selfClanToken = bossSelfClanToken;
        }
        if (isEmpty(normalizeClanToken(selfClanToken))) {
            Log.d(TAG, TRACE_PREFIX + " clan notify found skipped: self clan token is empty");
            writeBossChat("Отправка в клан-чат невозможна: отсутствует значок клана (selfClanToken пустой).");
            return;
        }

        String exactRegNum = currentMapRegNum();
        if (isEmpty(exactRegNum) && !isEmpty(AppVars.AutoMovingDestinaton)) {
            exactRegNum = safeTrim(AppVars.AutoMovingDestinaton);
        }
        if (isEmpty(exactRegNum)) {
            exactRegNum = "?";
        }

        String message = "%clan% Босс на клетке '" + exactRegNum + "'";
        boolean chatReady = isChatSendReady();
        if (!chatReady) {
            Log.w(TAG, TRACE_PREFIX + " clan notify found send requested while chat is not ready: cell=" + exactRegNum);
        }
        FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_FOUND_PAYLOAD] cell=" + exactRegNum
                + ", chatReady=" + chatReady
                + ", msgLen=" + message.length()
                + ", msg=" + message);
        Chat.sendMessageToServer(message);
        Log.d(TAG, TRACE_PREFIX + " clan notify found sent: cell=" + exactRegNum + ", chatReady=" + chatReady);
    }

    /**
     * Отправляет в клан-чат служебное уведомление, когда сценарий остановлен фильтром текущих войн.
     *
     * Сообщение (по требованию): "%clan% Поединок с Боссом невозможен, игрок в Клановой войне."
     */
    private void sendClanBossWarDeniedMessageIfNeeded(String selfClanToken) {
        if (!isAutoBossClanNotifyEnabled()) {
            Log.d(TAG, TRACE_PREFIX + " clan notify wars-denied skipped: disabled by settings");
            FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_WARS_DENY_SKIPPED] reason=disabled_by_settings");
            return;
        }
        if (isEmpty(normalizeClanToken(selfClanToken))) {
            Log.d(TAG, TRACE_PREFIX + " clan notify wars-denied skipped: self clan token is empty");
            FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_WARS_DENY_SKIPPED] reason=empty_self_clan_token");
            writeBossChat("\u041e\u0442\u043f\u0440\u0430\u0432\u043a\u0430 \u0432 \u043a\u043b\u0430\u043d-\u0447\u0430\u0442 \u043d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u043d\u0430: \u043e\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442 \u0437\u043d\u0430\u0447\u043e\u043a \u043a\u043b\u0430\u043d\u0430 (selfClanToken \u043f\u0443\u0441\u0442\u043e\u0439).");
            return;
        }
        String message = "%clan% \u041f\u043e\u0435\u0434\u0438\u043d\u043e\u043a \u0441 \u0411\u043e\u0441\u0441\u043e\u043c \u043d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u0435\u043d, \u0438\u0433\u0440\u043e\u043a \u0432 \u041a\u043b\u0430\u043d\u043e\u0432\u043e\u0439 \u0432\u043e\u0439\u043d\u0435.";
        boolean chatReady = isChatSendReady();
        if (!chatReady) {
            Log.w(TAG, TRACE_PREFIX + " clan notify wars-denied send requested while chat is not ready");
        }
        Chat.sendMessageToServer(message);
        FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_WARS_DENY_SENT] chatReady=" + chatReady + ", msgLen=" + message.length());
        Log.d(TAG, TRACE_PREFIX + " clan notify wars-denied sent: chatReady=" + chatReady);
    }

    boolean isAutoBossAskTargetEnabled() {
        return prefs.getBoolean(PREF_AUTO_BOSS_ASK_TARGET, true);
    }

    void setAutoBossAskTargetEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_BOSS_ASK_TARGET, enabled).apply();
    }

    /**
     * Проверка по текущим клановым войнам (`wars.cgi`).
     * Если включено, Авто-Босс не пытается защищать цель,
     * чьё `targetClanToken` присутствует в списке текущих войн.
     * Настройка применяется независимо от других фильтров сценария.
     */
    /**
     * Флаг «Следить за текущими войнами».
     *
     * При включении цель отклоняется, если её clanToken присутствует
     * в кэше текущих войн, полученном из {@code wars.cgi}.
     */
    /**
     * Флаг «Следить за текущими войнами».
     *
     * Когда включён, `BossAuto` отфильтровывает цель, если её `targetClanToken`
     * присутствует в кэше `ClanWarsManager` (данные `wars.cgi`).
     */
    boolean isAutoBossTrackCurrentWarsEnabled() {
        return prefs.getBoolean(PREF_AUTO_BOSS_TRACK_CURRENT_WARS, true);
    }

    void setAutoBossTrackCurrentWarsEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_BOSS_TRACK_CURRENT_WARS, enabled).apply();
    }

    /**
     * Флаг отправки служебных сообщений в клан-чат для сценария «Авто-Босс».
     */
    boolean isAutoBossClanNotifyEnabled() {
        return prefs.getBoolean(PREF_AUTO_BOSS_CLAN_NOTIFY, false);
    }

    /**
     * Сохранение флага клан-уведомлений.
     * Используется UI-настройками, а фактическая отправка выполняется в
     * {@link #sendClanBossEventMessageIfNeeded(String, String, String, String)} Рё
     * {@link #sendClanBossFoundMessageIfNeeded()}.
     */
    void setAutoBossClanNotifyEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_BOSS_CLAN_NOTIFY, enabled).apply();
        Log.d(TAG, TRACE_PREFIX + " setAutoBossClanNotifyEnabled=" + enabled);
    }

    /**
     * Признак активного сценария поиска/входа в бой, в котором нежелательны
     * фоновые переименования карты по pinfo и искусственные задержки шага карты.
     *
     * Используется как runtime-guard для:
     * - `RoomManager` (пауза `MapRebuildFromPinfo`);
     * - `MapAjax` (пауза `MapCellCheckTimeout` при активном Авто-Боссе).
     */
    boolean shouldPauseMapRebuildFromPinfo() {
        if (!isAutoBossEnabled()) {
            return false;
        }
        BossStage currentStage;
        synchronized (lock) {
            currentStage = stage;
        }
        return currentStage == BossStage.SEARCHING_TARGET
                || currentStage == BossStage.TARGET_FOUND_WAIT_SCROLL
                || currentStage == BossStage.WAIT_FIGHT_START;
    }

    int getAutoBossWaitBeforeScrollSec() {
        int value = prefs.getInt(PREF_AUTO_BOSS_WAIT_SCROLL_SEC, DEFAULT_WAIT_BEFORE_SCROLL_SEC);
        if (value < 1) return 1;
        if (value > 10) return 10;
        return value;
    }

    void setAutoBossWaitBeforeScrollSec(int sec) {
        int safe = sec;
        if (safe < 1) safe = 1;
        if (safe > 10) safe = 10;
        prefs.edit().putInt(PREF_AUTO_BOSS_WAIT_SCROLL_SEC, safe).apply();
    }

    int getAutoBossSearchTimeoutSec() {
        int value = prefs.getInt(PREF_AUTO_BOSS_SEARCH_TIMEOUT_SEC, DEFAULT_SEARCH_TIMEOUT_SEC);
        if (value < 60) return 60;
        if (value > 20 * 60) return 20 * 60;
        return value;
    }

    void setAutoBossSearchTimeoutSec(int sec) {
        int safe = sec;
        if (safe < 60) safe = 60;
        if (safe > 20 * 60) safe = 20 * 60;
        prefs.edit().putInt(PREF_AUTO_BOSS_SEARCH_TIMEOUT_SEC, safe).apply();
    }

    int getAutoBossWaitFightTimeoutSec() {
        int value = prefs.getInt(PREF_AUTO_BOSS_WAIT_FIGHT_TIMEOUT_SEC, DEFAULT_WAIT_FIGHT_TIMEOUT_SEC);
        if (value < 10) return 10;
        if (value > 120) return 120;
        return value;
    }

    void setAutoBossWaitFightTimeoutSec(int sec) {
        int safe = sec;
        if (safe < 10) safe = 10;
        if (safe > 120) safe = 120;
        prefs.edit().putInt(PREF_AUTO_BOSS_WAIT_FIGHT_TIMEOUT_SEC, safe).apply();
    }

    private long getWaitBeforeScrollMs() {
        return getAutoBossWaitBeforeScrollSec() * 1000L;
    }

    private long getSearchTimeoutMs() {
        return getAutoBossSearchTimeoutSec() * 1000L;
    }

    private long getWaitFightTimeoutMs() {
        return getAutoBossWaitFightTimeoutSec() * 1000L;
    }
}
