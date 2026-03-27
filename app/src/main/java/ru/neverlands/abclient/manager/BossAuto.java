package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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
final class BossAuto {
    private static final String TAG = "AutoFunctionsManager";
    private static final String TRACE_PREFIX = "AUTO_BOSS_TRACE";
    private static final String KEY_AUTO_BOSS = "auto_function_auto_boss";
    private static final String PREF_AUTO_BOSS_ASK_TARGET = "auto_boss_ask_target";
    private static final String PREF_AUTO_BOSS_WAIT_SCROLL_SEC = "auto_boss_wait_scroll_sec";
    private static final String PREF_AUTO_BOSS_SEARCH_TIMEOUT_SEC = "auto_boss_search_timeout_sec";
    private static final String PREF_AUTO_BOSS_WAIT_FIGHT_TIMEOUT_SEC = "auto_boss_wait_fight_timeout_sec";

    private static final Pattern BOSS_EVENT_PATTERN = Pattern.compile(
            "внимание!\\s*случайное событие!\\s*монстр\\s*[\"«]?([^\"»]+)[\"»]?\\s*напал на игрока\\s+([a-zа-я0-9_\\-]+)\\.",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CELL_PATTERN = Pattern.compile("\\b\\d{1,4}-\\d{1,5}\\b");
    private static final Pattern SPAN_NICK_PATTERN = Pattern.compile(
            "<SPAN[^>]+(?:title|alt)=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    private static final int DEFAULT_SEARCH_TIMEOUT_SEC = 6 * 60;
    private static final int DEFAULT_WAIT_BEFORE_SCROLL_SEC = 2;
    private static final int DEFAULT_WAIT_FIGHT_TIMEOUT_SEC = 25;
    private static final long RETURN_TIMEOUT_MS = 2 * 60 * 1000L;
    private static final long EVENT_DEDUP_WINDOW_MS = 20_000L;
    private static final int TARGET_CHAT_ASK_MAX_ATTEMPTS = 5;
    private static final long TARGET_CHAT_ASK_RETRY_MS = 1_500L;

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

    BossAuto(Context context, SharedPreferences prefs, AutoFunctionsManager owner) {
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

        BossScenarioSnapshot newSnapshot = captureSnapshot();
        pauseNonCombatFunctions();

        synchronized (lock) {
            snapshot = newSnapshot;
            targetNick = normalizedTarget;
            bossName = safeTrim(event.bossName);
            originRegNum = currentMapRegNum();
            stage = BossStage.SEARCHING_TARGET;
            stageStartedAtMs = now;
            actionDueAtMs = 0L;
            protectionSentAtMs = 0L;
            protectionAttempts = 0;
            targetAskAttempts = 0;
            targetAskNextAttemptAtMs = 0L;
        }

        String locationLabel = resolveTargetLocationLabel(normalizedTarget);
        String targetHtml = RoomManager.buildUnifiedChatNickHtml(normalizedTarget);
        String locationPrefix = isEmpty(locationLabel) ? "" : " [" + escapeHtml(locationLabel) + "]";
        writeBossChat("Событие: Монстр \"" + event.bossName + "\" напал на игрока "
                + (isEmpty(targetHtml) ? escapeHtml(normalizedTarget) : targetHtml)
                + locationPrefix + ". Запускаем поиск цели.");
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
        String targetHtml = RoomManager.buildUnifiedChatNickHtml(targetNick);
        writeBossChat("Цель найдена (" + source + "): "
                + (isEmpty(targetHtml) ? escapeHtml(targetNick) : targetHtml)
                + ". Готовим «Свиток Защиты».");
    }

    private void sendProtectionScroll() {
        String target;
        synchronized (lock) {
            if (stage != BossStage.TARGET_FOUND_WAIT_SCROLL && stage != BossStage.WAIT_FIGHT_START) {
                return;
            }
            target = targetNick;
            protectionAttempts++;
            protectionSentAtMs = System.currentTimeMillis();
            stage = BossStage.WAIT_FIGHT_START;
            stageStartedAtMs = protectionSentAtMs;
        }
        if (isEmpty(target)) {
            stopAndRestore("empty_target_before_scroll", true);
            return;
        }
        String targetHtml = RoomManager.buildUnifiedChatNickHtml(target);
        StringBuilder builder = new StringBuilder();
        builder.append(MainPhp.buildServerChatTimeHtmlExternal());
        builder.append("<font color=#7E57C2><b>[Авто-Боссы]</b></font> ");
        builder.append("Используем «Свиток Защиты» на ");
        if (!isEmpty(targetHtml)) {
            builder.append(targetHtml);
        } else {
            builder.append(escapeHtml(target));
        }
        builder.append(".");
        FastActionManager.writeChatMsg(builder.toString());
        FastActionManager.fastAttackZas(target);
        Log.d(TAG, TRACE_PREFIX + " protection scroll sent: target=" + target
                + ", attempts=" + protectionAttempts);
    }

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
        }
        if (owner.isAutoCompassEnabled()) {
            owner.setAutoCompassEnabled(false);
        }
        if (snapshotToRestore != null) {
            restoreSnapshot(snapshotToRestore);
        }
        if (!isEmpty(oldTarget)) {
            String targetHtml = RoomManager.buildUnifiedChatNickHtml(oldTarget);
            writeBossChat("Сценарий завершен (" + reason + ") для цели "
                    + (isEmpty(targetHtml) ? escapeHtml(oldTarget) : targetHtml) + ".");
        } else {
            writeBossChat("Сценарий завершен (" + reason + ").");
        }
        Log.d(TAG, TRACE_PREFIX + " scenario stopped: reason=" + reason);
    }

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
        Matcher matcher = BOSS_EVENT_PATTERN.matcher(plain);
        if (!matcher.find()) {
            return null;
        }
        String boss = safeTrim(matcher.group(1));
        String target = normalizeNick(matcher.group(2));
        if (isEmpty(target)) {
            return null;
        }
        return new BossEvent(boss, target);
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

    private String resolveTargetLocationLabel(String targetNick) {
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

    private void writeBossChat(String message) {
        if (isEmpty(message)) {
            return;
        }
        FastActionManager.writeChatMsg(
                "<font color=#7E57C2><b>[Авто-Боссы]</b></font> " + message
        );
    }

    boolean isAutoBossAskTargetEnabled() {
        return prefs.getBoolean(PREF_AUTO_BOSS_ASK_TARGET, true);
    }

    void setAutoBossAskTargetEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_BOSS_ASK_TARGET, enabled).apply();
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
