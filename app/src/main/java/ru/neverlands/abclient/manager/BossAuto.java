package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.postfilter.MainPhp;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;

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
    private static final String KEY_AUTO_BOSS = "auto_function_auto_boss";
    private static final String PREF_AUTO_BOSS_ASK_TARGET = "auto_boss_ask_target";
    private static final String PREF_AUTO_BOSS_BD_MODE = "auto_boss_bd_mode";
    private static final String PREF_AUTO_BOSS_TRACK_CURRENT_WARS = "auto_boss_track_current_wars";
    private static final String PREF_AUTO_BOSS_CLAN_NOTIFY = "auto_boss_clan_notify";
    private static final String PREF_AUTO_BOSS_WAIT_SCROLL_SEC = "auto_boss_wait_scroll_sec";
    private static final String PREF_AUTO_BOSS_SEARCH_TIMEOUT_SEC = "auto_boss_search_timeout_sec";
    private static final String PREF_AUTO_BOSS_WAIT_FIGHT_TIMEOUT_SEC = "auto_boss_wait_fight_timeout_sec";

    private static final Pattern BOSS_EVENT_PATTERN = Pattern.compile(
            "внимание!\\s*случайное событие!\\s*монстр\\s*[\"«]?([^\"»]+)[\"»]?\\s*напал на игрока\\s+([a-zа-я0-9_\\-]+)\\.",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // Расширенный парсер события Босса: допускает ники со спецсимволами и не завязан на узкий класс символов.
    private static final Pattern BOSS_EVENT_PATTERN_FLEX = Pattern.compile(
            "(?iu)(?:\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s+)?(?:внимание!\\s*случайное\\s+событие!\\s*)?монстр\\s*[\"«]?([^\"»]+)[\"»]?\\s*напал\\s+на\\s+игрока\\s+([^\\s<>]+?)\\s*\\.?");
    private static final Pattern CELL_PATTERN = Pattern.compile("\\b\\d{1,4}-\\d{1,5}\\b");
    private static final Pattern SPAN_NICK_PATTERN = Pattern.compile(
            "<SPAN[^>]+(?:title|alt)=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FIGHT_ALLY_PATTERN = Pattern.compile(
            "([A-Za-zА-Яа-яЁё0-9_\\-]+)\\s*\\[(\\d{1,9})\\s*/\\s*(\\d{1,9})\\]");

    private static final int DEFAULT_SEARCH_TIMEOUT_SEC = 6 * 60;
    private static final int DEFAULT_WAIT_BEFORE_SCROLL_SEC = 2;
    private static final int DEFAULT_WAIT_FIGHT_TIMEOUT_SEC = 25;
    private static final long RETURN_TIMEOUT_MS = 2 * 60 * 1000L;
    private static final long EVENT_DEDUP_WINDOW_MS = 20_000L;
    private static final int TARGET_CHAT_ASK_MAX_ATTEMPTS = 5;
    private static final long TARGET_CHAT_ASK_RETRY_MS = 1_500L;

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
    private String bossTargetClanToken = "";
    private String bossSelfClanToken = "";

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
        BossEvent event = parseBossEvent(messageHtml);
        if (event != null && isAutoBossEnabled()) {
            handleBossEvent(event);
        }
        if (!isAutoBossEnabled()) {
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
                        stopAndRestore("fight_not_started", true);
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
     * - применяет фильтры БД-режима и «Следить за текущими войнами»;
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
        String selfClanToken = normalizeClanToken(selfSnapshot == null ? "" : selfSnapshot.clanToken);
        boolean bdModeEnabled = isAutoBossBdModeEnabled();
        boolean trackCurrentWarsEnabled = isAutoBossTrackCurrentWarsEnabled();

        Log.d(TAG, TRACE_PREFIX + " clan filters: target=" + normalizedTarget
                + ", bdMode=" + bdModeEnabled
                + ", trackCurrentWars=" + trackCurrentWarsEnabled
                + ", targetClan=" + targetClanToken
                + ", selfClan=" + selfClanToken);

        if (bdModeEnabled) {
            if (isEmpty(selfClanToken)) {
                writeBossChat("Продолжаем поиск цели не учитывая БД режим (наш статус позволяет вмешаться).");
            } else if (!isEmpty(targetClanToken) && !selfClanToken.equalsIgnoreCase(targetClanToken)) {
                String deniedTargetHtml = buildTargetNickHtml(normalizedTarget, targetSnapshot);
                writeBossChat("Действие отменено — БД режим не позволяет нам защитить " + deniedTargetHtml + ".");
                Log.d(TAG, TRACE_PREFIX + " bd mode denied target: target=" + normalizedTarget
                        + ", targetClan=" + targetClanToken + ", selfClan=" + selfClanToken);
                return;
            }
        }

        if (trackCurrentWarsEnabled
                && !isEmpty(targetClanToken)
                && ClanWarsManager.getInstance(appContext).isClanTokenInCurrentWars(targetClanToken)) {
            String deniedTargetHtml = buildTargetNickHtml(normalizedTarget, targetSnapshot);
            writeBossChat("Действие отменено — цель " + deniedTargetHtml
                    + " состоит в клане, участвующем в текущей клановой войне.");
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
        String initialFightFid = resolveFightFidReliable(normalizedTarget, targetSnapshot);
        String initialFightLink = buildFightLogLink(initialFightFid);

        synchronized (lock) {
            snapshot = newSnapshot;
            targetNick = normalizedTarget;
            bossName = safeTrim(event.bossName);
            originRegNum = currentMapRegNum();
            bossFightFid = initialFightFid;
            bossFightLink = initialFightLink;
            bossFightFidMissingTicks = 0;
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

        String locationLabel = resolveTargetLocationLabel(normalizedTarget, targetSnapshot);
        String targetHtml = buildTargetNickHtml(normalizedTarget, targetSnapshot);
        String locationPrefix = isEmpty(locationLabel) ? "" : " [" + escapeHtml(locationLabel) + "]";
        writeBossChat("Событие: Монстр \"" + escapeHtml(event.bossName) + "\" напал на игрока "
                + targetHtml + ". Цель " + targetHtml + " в " + buildFightWordHtml(initialFightLink)
                + ". Запускаем поиск цели." + locationPrefix);
        sendClanBossEventMessageIfNeeded(event.bossName, normalizedTarget, selfClanToken);
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
        // FIX: BOSS_EVENT_PATTERN_FLEX исторически использовал lazy-захват ника цели.
        // На коротких никах вроде "VV" это могло дать "V". Здесь аккуратно
        // расширяем захват до фактического конца токена в исходном plain-тексте,
        // не меняя остальную цепочку парсинга.
        int targetStart = matcher.start(2);
        int targetEnd = matcher.end(2);
        if (targetStart >= 0 && targetEnd > targetStart && targetEnd < plain.length()) {
            int scan = targetEnd;
            while (scan < plain.length()) {
                char ch = plain.charAt(scan);
                if (Character.isWhitespace(ch) || ch == '<' || ch == '>' || ch == '.' || ch == ',' || ch == ':' || ch == ';') {
                    break;
                }
                scan++;
            }
            if (scan > targetEnd) {
                rawTarget = plain.substring(targetStart, scan);
            }
        }
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
        while (!value.isEmpty()) {
            char tail = value.charAt(value.length() - 1);
            if (tail == '.' || tail == ',' || tail == ':' || tail == ';') {
                value = value.substring(0, value.length() - 1).trim();
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
        Chat.sendMessageToServer(message);
        synchronized (lock) {
            targetAskAttempts = TARGET_CHAT_ASK_MAX_ATTEMPTS;
            targetAskNextAttemptAtMs = 0L;
        }
        Log.d(TAG, TRACE_PREFIX + " ask target sent: target=" + target + ", message=" + message);
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

        NeverApi.PinfoCompassSnapshot snapshot = safeGetPinfoSnapshot(target);
        if (snapshot == null) {
            return true;
        }

        String latestFid = normalizeFightFid(snapshot.fightFid);
        int missingTicks = 0;
        synchronized (lock) {
            if (stage != BossStage.SEARCHING_TARGET) {
                return false;
            }
            if (!isEmpty(latestFid)) {
                bossFightFid = latestFid;
                bossFightLink = buildFightLogLink(latestFid);
                bossFightFidMissingTicks = 0;
            } else {
                bossFightFidMissingTicks++;
                missingTicks = bossFightFidMissingTicks;
            }
        }
        if (!isEmpty(latestFid)) {
            return true;
        }
        if (missingTicks <= 1) {
            Log.d(TAG, TRACE_PREFIX + " target fight fid missing, wait grace tick: target=" + target);
            return true;
        }

        writeBossChat("Действие отменено, цель уже не в " + getCurrentFightWordHtml() + ".");
        stopAndRestore("target_left_fight", true);
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
        FastActionManager.writeChatMsg(
                "<font color=#7E57C2><b>[Авто-Боссы]</b></font> " + message
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
    private void sendClanBossEventMessageIfNeeded(String bossName, String targetNick, String selfClanToken) {
        if (!isAutoBossClanNotifyEnabled()) {
            return;
        }
        if (isEmpty(normalizeClanToken(selfClanToken))) {
            writeBossChat("Мы вне клана. Сообщение отменено.");
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
        String message = "%clan% '" + safeBossName + "' напал на '" + normalizedTarget
                + "'. Возможные клетки: " + cellsCsv;
        Chat.sendMessageToServer(message);
        Log.d(TAG, TRACE_PREFIX + " clan notify event sent: target=" + normalizedTarget + ", cells=" + cellsCsv);
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
            return;
        }
        String selfClanToken;
        String localBossName;
        synchronized (lock) {
            selfClanToken = bossSelfClanToken;
            localBossName = bossName;
        }
        if (isEmpty(normalizeClanToken(selfClanToken))) {
            writeBossChat("Мы вне клана. Сообщение отменено.");
            return;
        }

        String exactRegNum = currentMapRegNum();
        if (isEmpty(exactRegNum) && !isEmpty(AppVars.AutoMovingDestinaton)) {
            exactRegNum = safeTrim(AppVars.AutoMovingDestinaton);
        }
        if (isEmpty(exactRegNum)) {
            exactRegNum = "?";
        }

        String safeBossName = safeTrim(localBossName);
        if (isEmpty(safeBossName)) {
            safeBossName = "Босс";
        }
        String message = "%clan% Босс '" + safeBossName + "' на клетке: " + exactRegNum;
        Chat.sendMessageToServer(message);
        Log.d(TAG, TRACE_PREFIX + " clan notify found sent: cell=" + exactRegNum);
    }

    boolean isAutoBossAskTargetEnabled() {
        return prefs.getBoolean(PREF_AUTO_BOSS_ASK_TARGET, true);
    }

    void setAutoBossAskTargetEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_BOSS_ASK_TARGET, enabled).apply();
    }

    boolean isAutoBossBdModeEnabled() {
        return prefs.getBoolean(PREF_AUTO_BOSS_BD_MODE, false);
    }

    void setAutoBossBdModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_BOSS_BD_MODE, enabled).apply();
    }

    /**
     * Проверка по текущим клановым войнам (`wars.cgi`).
     * Если включено, Авто-Босс не пытается защищать цель,
     * чьё `targetClanToken` присутствует в списке текущих войн.
     * Настройка независима от БД-режима.
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
     * {@link #sendClanBossEventMessageIfNeeded(String, String, String)} и
     * {@link #sendClanBossFoundMessageIfNeeded()}.
     */
    void setAutoBossClanNotifyEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_BOSS_CLAN_NOTIFY, enabled).apply();
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
