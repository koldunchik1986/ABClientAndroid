package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.preference.PreferenceManager;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.model.Cell;
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.postfilter.MainPhp;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.utils.Russian;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.EventSounds;
import ru.neverlands.abclient.utils.ConverterUtils;
import ru.neverlands.abclient.utils.ContactRenderHelper;
import ru.neverlands.abclient.utils.AppLog;

public class RoomManager {
    private static final String TAG = "RoomManager";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
    private static final long AUTO_ATTACK_BLACKLIST_MS = 10_000L;
    private static final String AA_TRACE_PREFIX = "[AA_TRACE]";
    private static final String AUTO_CURE_TRACE_PREFIX = "[AUTO_CURE_TRACE]";
    private static final int CONTACT_CLASS_NEUTRAL = 0;
    private static final int CONTACT_CLASS_ENEMY = 1;
    private static final int CONTACT_CLASS_FRIEND = 2;
    private static final long AUTO_CURE_ROOM_SCAN_INTERVAL_MS = 12_000L;
    private static final long AUTO_CURE_ROOM_SCAN_PRIORITY_INTERVAL_MS = 1_000L;
    private static final long AUTO_CURE_ROOM_PINFO_CACHE_TTL_MS = 30_000L;
    private static final long AUTO_CURE_ROOM_GUARD_CACHE_TTL_MS = 10_000L;
    private static final long AUTO_CURE_SELF_CLAN_TOKEN_CACHE_TTL_MS = 300_000L;
    private static final long AUTO_CURE_SKIP_NOTICE_DEDUP_MS = 20_000L;
    private static final long AUTO_CURE_POST_SUBMIT_VERIFY_DELAY_MS = 1_500L;
    private static final long MAP_PINFO_SYNC_COOLDOWN_MS = 3_000L;
    private static final long ROOM_RENDER_CACHE_TTL_MS = 20_000L;
    private static final int MAX_ROOM_RUNTIME_CACHE_ENTRIES = 512;
    private static final String PREF_SHOW_ALL_ROOM_EFFECTS = "show_all_room_effects";
    private static final Pattern FIGHT_FID_PATTERN = Pattern.compile("(\\d{1,16})");
    private static volatile Boolean showAllRoomEffectsEnabled;
    // Последние raw-элементы ChatListU по нику (lowercase), чтобы другие модули
    // (например, Auto-Компас) могли рендерить ник/иконки/уровень/травмы 1:1 как room-list.
    private static final Map<String, String> lastRoomChatEntryByNick = new ConcurrentHashMap<>();
    // Временный чёрный список целей авто-нападения (аналог C# `RoomManager.BlackList`).
    // Ключ: ник в нижнем регистре, значение: время добавления в список (мс).
    private static final Map<String, Long> autoAttackBlackList = new ConcurrentHashMap<>();
    // Кэш последнего результата pinfo-проверки травм для ников комнаты.
    private static final Map<String, CachedRoomPinfoState> autoCureRoomPinfoCache = new ConcurrentHashMap<>();
    // Кэш guard-состояния (клан/бой) для ограничений авто-лечения friend/neutral.
    private static final Map<String, AutoCureGuardState> autoCureRoomGuardCache = new ConcurrentHashMap<>();
    // Анти-спам локальных чат-уведомлений по причинам пропуска авто-лечения.
    // Ключ = "scope|reason|nickOrToken", значение = timestamp последней отправки.
    private static final Map<String, Long> autoCureSkipNoticeAtMs = new ConcurrentHashMap<>();
    private static volatile boolean autoCureRoomScanInProgress = false;
    private static volatile long lastAutoCureRoomScanAtMs = 0L;
    private static volatile String cachedAutoCureSelfClanToken = "";
    private static volatile long cachedAutoCureSelfClanTokenAtMs = 0L;
    private static final long PENDING_ROOM_LABEL_TTL_MS = 20_000L;
    private static volatile String pendingRoomLocationName;
    private static volatile String pendingRoomLocationTargetRegNum;
    private static volatile long pendingRoomLocationNameAtMs;
    private static volatile long lastMapPinfoSyncAtMs = 0L;
    private static volatile boolean mapPinfoSyncInFlight = false;
    private static volatile long lastMapLocationConfirmedSeq = 0L;
    private static volatile String lastMapLocationConfirmedRegNum = "";
    private static volatile String lastOwnPinfoRegion = "";
    private static volatile String lastOwnPinfoCellName = "";
    private static volatile String lastStableRoomRenderHtml = "";
    private static volatile long lastStableRoomRenderAtMs = 0L;

    private static final class CachedRoomPinfoState {
        final int woundType;
        final List<Integer> effectIds;
        final List<ContactRenderHelper.EffectState> effectStates;
        final long capturedAtMs;

        CachedRoomPinfoState(int woundType,
                             List<Integer> effectIds,
                             List<ContactRenderHelper.EffectState> effectStates,
                             long capturedAtMs) {
            this.woundType = woundType;
            this.effectIds = effectIds == null ? Collections.emptyList() : effectIds;
            this.effectStates = effectStates == null ? Collections.emptyList() : effectStates;
            this.capturedAtMs = capturedAtMs;
        }
    }

    private static final class AutoCureGuardState {
        final String clanToken;
        final String fightFid;
        final long capturedAtMs;

        AutoCureGuardState(String clanToken, String fightFid, long capturedAtMs) {
            this.clanToken = clanToken == null ? "" : clanToken;
            this.fightFid = fightFid == null ? "" : fightFid;
            this.capturedAtMs = capturedAtMs;
        }
    }

    private static final class AutoCureExternalGuardContext {
        final String selfClanToken;
        final boolean selfClanInCurrentWars;

        AutoCureExternalGuardContext(String selfClanToken, boolean selfClanInCurrentWars) {
            this.selfClanToken = selfClanToken == null ? "" : selfClanToken;
            this.selfClanInCurrentWars = selfClanInCurrentWars;
        }
    }

    private static final class AutoCureTarget {
        final String nick;
        final int woundType;
        final int classId;
        final boolean self;

        AutoCureTarget(String nick, int woundType, int classId, boolean self) {
            this.nick = nick;
            this.woundType = woundType;
            this.classId = classId;
            this.self = self;
        }
    }

    // Обработчик списка игроков комнаты (`ch.php?lo=1`).
    // Метод `process(...)` содержит портированную логику разбора списка комнаты.
    // РЕШЕНИЕ для автоматического отображения списка игроков:
    // 1) парсит ChatListU из HTML
    // 2) генерирует на Java стороне HTML для каждого игрока (HtmlChar)
    // 3) инжектирует сгенерированный HTML обратно в ответ сервера
    // Это обход проблемы с ch_list.js, которая не может отобразить список в изолированном WebView.
    public static String process(Context context, String html) {
        pruneRoomRuntimeCaches(System.currentTimeMillis());
        syncCellNameFromRoomHtml(context, html);
        maybeSyncCellMetaFromOwnPinfo(context);
        AppLog.d(TAG, BG_TRACE_PREFIX + " process: htmlLen=" + (html == null ? 0 : html.length())
                + ", contextNull=" + (context == null)
                + ", doShowWalkers=" + AppVars.DoShowWalkers);
        if (isEmpty(html)) {
            String cachedRoomHtml = getCachedRoomRenderHtml();
            if (!isEmpty(cachedRoomHtml)) {
                AppLog.d(TAG, BG_TRACE_PREFIX + " process: htmlLen=0 -> reuse cached room render, cacheAgeMs="
                        + (System.currentTimeMillis() - lastStableRoomRenderAtMs));
                FileLogger.trace("roommanager", "[ROOM_RENDER_CACHE] reuse cached render for empty html");
                return cachedRoomHtml;
            }
        }
        FilterProcRoomResult filterResult = FilterProcRoom(html);
        // STEP 1: Инжектируем сгенерированный HTML список игроков в ответ
        html = injectPlayerListHtmlIntoChatPhp(html, filterResult);
        AppLog.d(TAG, BG_TRACE_PREFIX + " process: HTML injection complete, htmlLen=" + html.length());
        rememberStableRoomRenderHtml(html, filterResult.numCharsInRoom);
        if (context != null) {
            try {
                AutoFunctionsManager.getInstance(context).onRoomUsersUpdated(
                        filterResult.roomNicks,
                        extractLocationName(html)
                );
            } catch (Exception e) {
                AppLog.w(TAG, "AUTO_COMPASS_TRACE room update hook failed", e);
            }
        }
        FilterGetWalkers(html, filterResult);
        boolean fightActive = isFightSessionActive();
        AppLog.d(TAG, AA_TRACE_PREFIX + " room tick: chars=" + filterResult.numCharsInRoom
                + ", enemies=" + buildEnemyCandidatesTrace(filterResult.enemyCandidates)
                + ", selectedEnemy=" + filterResult.enemyAttack
                + ", fastNeed=" + AppVars.FastNeed
                + ", fastId=" + AppVars.FastId
                + ", fastNick=" + AppVars.FastNick
                + ", fightActive=" + fightActive
                + ", fightLink=" + AppVars.FightLink);

        // C# parity extension:
        // отдельный контур авто-лечения по персонажам в текущей клетке
        // с приоритетом self -> friends -> neutrals (enemies skipped).
        maybeScheduleRoomAutoCure(context, filterResult, fightActive);

        // Авто-нападение по списку комнаты (аналог ветки `RoomManager.Process -> EnemyAttack` в C#).
        // Зависимости:
        // - `AutoFunctionsManager` (флаг AUTO_ATTACK),
        // - `ContactsManager` (`classId`/`toolId` контакта),
        // - `FastActionManager.fastAttackAutoByToolId(...)` (запуск быстрой атаки),
        // - `AppVars.FastNeed` (защита от параллельного цикла быстрой атаки).
        // Конвейер авто-нападения:
        // 1) берём выбранного противника из списка комнаты (`filterResult.enemyAttack`);
        // 2) определяем инструмент атаки с приоритетом `contact.toolId -> AppVars.AutoAttackToolId`;
        // 3) запускаем `FastActionManager.fastAttackAutoByToolId(...)` только если
        //    `AppVars.FastNeed == false`, чтобы не пересекаться с уже активным циклом быстрой атаки.
        if (context == null) {
            AppLog.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: context=null");
            return html;
        }

        if (AppVars.FastNeed) {
            AppLog.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: fast pipeline active"
                    + ", fastId=" + AppVars.FastId + ", fastNick=" + AppVars.FastNick);
            return html;
        }

        if (isEmpty(filterResult.enemyAttack)) {
            if (filterResult.enemyCandidates.isEmpty()) {
                AppLog.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: no hostile contacts in room");
            } else {
                AppLog.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: no selected enemy"
                        + ", enemies=" + buildEnemyCandidatesTrace(filterResult.enemyCandidates));
            }
            return html;
        }

        // Критическая защита:
        // Во время активного боя чат продолжает приходить тиками (`ch.php`), и без этой проверки
        // `RoomManager` может повторно запускать авто-нападение по устаревшему списку врагов.
        // Это перезапускает `FastNeed/FastId` в середине боя и конфликтует с циклом ударов автобоя.
        // Итог: конфликт между циклами "авто-нападение" и "цикл ударов автобоя".
        if (fightActive) {
            AppLog.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: active fight session"
                    + ", fastNeed=" + AppVars.FastNeed
                    + ", fightLink=" + AppVars.FightLink
                    + ", topUrl=" + AppVars.url_main_top);
            return html;
        }

        boolean autoAttackEnabled = isAutoAttackEnabled(context);
        if (!autoAttackEnabled) {
            AppLog.d(TAG, AA_TRACE_PREFIX + " auto-attack disabled: enabled=false"
                    + ", globalTool=" + AppVars.AutoAttackToolId);
            return html;
        }

        String enemyNick = stripItalic(filterResult.enemyAttack);
        // Локальная настройка инструмента для конкретного контакта из `contacts.xml`.
        // Значение `0` трактуется как "использовать глобальный инструмент".
        int contactToolId = ContactsManager.getToolIdOfContact(enemyNick);
        // Глобальный инструмент авто-нападения из быстрых настроек (`AppVars.AutoAttackToolId`).
        int globalToolId = AppVars.AutoAttackToolId;
        // Финальный выбор инструмента:
        // - приоритет у настройки контакта (`contactToolId > 0`);
        // - иначе используем глобальное значение.
        // Зависимости: `ContactsManager`, `AppVars`.
        int toolId = (contactToolId > 0) ? contactToolId : globalToolId;
        if (toolId == 0) {
            AppLog.d(TAG, AA_TRACE_PREFIX + " auto-attack skipped: no tool selected, nick=" + enemyNick
                    + ", contactTool=" + contactToolId + ", globalTool=" + globalToolId);
            return html;
        }

        long blackListRemainingMs = getBlackListRemainingMs(enemyNick);
        AppLog.d(TAG, AA_TRACE_PREFIX + " auto-attack candidate: nick=" + enemyNick + ", toolId=" + toolId
                + ", contactTool=" + contactToolId
                + ", fastNeedBefore=" + AppVars.FastNeed
                + ", globalTool=" + globalToolId
                + ", blacklistRemainingMs=" + blackListRemainingMs);
        FastActionManager.writeChatMsg("Пытаемся напасть на <b>" + enemyNick + "</b>!");
        boolean started = FastActionManager.fastAttackAutoByToolId(enemyNick, toolId);
        if (!started) {
            AppLog.w(TAG, AA_TRACE_PREFIX + " auto-attack skipped: unsupported toolId=" + toolId + ", nick=" + enemyNick);
        } else {
            AppLog.d(TAG, AA_TRACE_PREFIX + " auto-attack started: nick=" + enemyNick + ", toolId=" + toolId
                    + ", fastNeedAfter=" + AppVars.FastNeed + ", fastId=" + AppVars.FastId
                    + ", fastNick=" + AppVars.FastNick);
        }
        return html;
    }

    private static void pruneRoomRuntimeCaches(long nowMs) {
        for (Map.Entry<String, Long> entry : autoAttackBlackList.entrySet()) {
            Long createdAtMs = entry.getValue();
            if (createdAtMs == null || nowMs - createdAtMs >= AUTO_ATTACK_BLACKLIST_MS) {
                autoAttackBlackList.remove(entry.getKey());
            }
        }
        for (Map.Entry<String, CachedRoomPinfoState> entry : autoCureRoomPinfoCache.entrySet()) {
            CachedRoomPinfoState state = entry.getValue();
            if (state == null || nowMs - state.capturedAtMs >= AUTO_CURE_ROOM_PINFO_CACHE_TTL_MS) {
                autoCureRoomPinfoCache.remove(entry.getKey());
            }
        }
        for (Map.Entry<String, AutoCureGuardState> entry : autoCureRoomGuardCache.entrySet()) {
            AutoCureGuardState state = entry.getValue();
            if (state == null || nowMs - state.capturedAtMs >= AUTO_CURE_ROOM_GUARD_CACHE_TTL_MS) {
                autoCureRoomGuardCache.remove(entry.getKey());
            }
        }
        for (Map.Entry<String, Long> entry : autoCureSkipNoticeAtMs.entrySet()) {
            Long sentAtMs = entry.getValue();
            if (sentAtMs == null || nowMs - sentAtMs >= AUTO_CURE_SKIP_NOTICE_DEDUP_MS) {
                autoCureSkipNoticeAtMs.remove(entry.getKey());
            }
        }
        trimRoomRuntimeCache(lastRoomChatEntryByNick);
        trimRoomRuntimeCache(autoAttackBlackList);
        trimRoomRuntimeCache(autoCureRoomPinfoCache);
        trimRoomRuntimeCache(autoCureRoomGuardCache);
        trimRoomRuntimeCache(autoCureSkipNoticeAtMs);
    }

    private static void trimRoomRuntimeCache(Map<String, ?> cache) {
        for (Map.Entry<String, ?> entry : cache.entrySet()) {
            if (cache.size() <= MAX_ROOM_RUNTIME_CACHE_ENTRIES) {
                return;
            }
            cache.remove(entry.getKey());
        }
    }

    /**
     * Синхронизация имени клетки (`CellDivText`) по фактическому названию из `/ch.php?lo=1`.
     *
     * Зависимости:
     * - `extractLocationName(...)` — извлечение серверного названия локации;
     * - `AppVars.Profile.MapLocation` — текущий `regnum` клетки;
     * - `ExtMap.syncCellLabelFromServer(...)` — обновление runtime + `abcells.xml`;
     * - `FastActionManager.writeChatMsg(...)` — уведомление в чат о замене названия.
     *
     * Поведение:
     * - если названия отличаются, заменяет label в рабочем `abcells.xml` и пишет сообщение:
     *   `Клетка №X - "Старое" заменено на Клетка №X - "Новое"`.
     */
    private static void syncCellNameFromRoomHtml(Context context, String html) {
        if (context == null || isEmpty(html) || AppVars.Profile == null) {
            return;
        }
        if (!isMapRebuildFromPinfoEnabled()) {
            clearPendingRoomLocationName();
            return;
        }
        String serverLocationName = extractLocationName(html);
        if (isEmpty(serverLocationName)) {
            return;
        }
        if (!isNatureMapContextForCellRename()) {
            clearPendingRoomLocationName();
            AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: skip room label sync outside nature map, topUrl="
                    + AppVars.url_main_top + ", serverName=" + serverLocationName);
            return;
        }

        String currentReg = normalizeRegNum(AppVars.Profile.MapLocation);
        String regNum = normalizeRegNum(resolveCellRegNumForRoomName(serverLocationName));
        if (isEmpty(regNum)) {
            // Вне движения не применяем "неуверенное" имя из room, чтобы не переименовать
            // текущую клетку именем соседней при рассинхроне ответов.
            if (!AppVars.AutoMoving) {
                clearPendingRoomLocationName();
                AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: skip unresolved room label sync in idle mode, currentReg="
                        + currentReg + ", serverName=" + serverLocationName);
                return;
            }
            String pendingTargetReg = normalizeRegNum(AppVars.AutoMovingNextJump);
            cachePendingRoomLocationName(serverLocationName, pendingTargetReg);
            AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: defer room label sync (moving), currentReg="
                    + currentReg
                    + ", nextJump=" + AppVars.AutoMovingNextJump
                    + ", pendingReg=" + pendingTargetReg
                    + ", serverName=" + serverLocationName);
            return;
        }

        // Прямое применение допускаем только когда room-имя однозначно связано с текущей клеткой
        // и в этот момент нет активного движения по карте.
        boolean canApplyImmediately = !AppVars.AutoMoving
                && !isEmpty(currentReg)
                && currentReg.equals(regNum);
        if (!canApplyImmediately) {
            cachePendingRoomLocationName(serverLocationName, regNum);
            AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: defer room label sync, currentReg=" + currentReg
                    + ", resolvedReg=" + regNum
                    + ", autoMoving=" + AppVars.AutoMoving
                    + ", serverName=" + serverLocationName);
            return;
        }

        applyCellNameSyncAndNotify(regNum, serverLocationName);
        clearPendingRoomLocationName();
    }

    private static boolean isMapRebuildFromPinfoEnabled() {
        if (AppVars.Profile == null || !AppVars.Profile.MapRebuildFromPinfo) {
            return false;
        }
        try {
            Context context = AppVars.getContext();
            if (context == null) {
                return true;
            }
            boolean pausedByAutoBoss = AutoFunctionsManager
                    .getInstance(context)
                    .shouldPauseMapRebuildFromPinfoByAutoBoss();
            if (pausedByAutoBoss) {
                AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: map-rebuild paused by Auto-Boss active search stage");
                return false;
            }
        } catch (Exception e) {
            AppLog.w(TAG, "MAP_NAME_SYNC_TRACE: map-rebuild pause check failed", e);
        }
        return true;
    }

    private static void maybeSyncCellMetaFromOwnPinfo(Context context) {
        if (context == null || AppVars.Profile == null || !isMapRebuildFromPinfoEnabled()) {
            return;
        }
        if (!isNatureMapContextForCellRename()) {
            return;
        }
        String mapRegNum = normalizeRegNum(AppVars.Profile.MapLocation);
        if (isEmpty(mapRegNum) || !ExtMap.Cells.containsKey(mapRegNum)) {
            return;
        }
        long mapConfirmSeq = lastMapLocationConfirmedSeq;
        if (!isMapLocationConfirmedForPinfoSync(mapRegNum, mapConfirmSeq)) {
            AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: skip pinfo sync, map location not confirmed, reg=" + mapRegNum
                    + ", confirmedReg=" + normalizeRegNum(lastMapLocationConfirmedRegNum)
                    + ", confirmedSeq=" + lastMapLocationConfirmedSeq
                    + ", expectedSeq=" + mapConfirmSeq
                    + ", autoMoving=" + AppVars.AutoMoving);
            return;
        }
        String ownNick = AppVars.Profile.UserNick;
        if (isEmpty(ownNick)) {
            return;
        }

        // Быстрый проход: если регион уже известен из последнего pinfo,
        // применяем его к текущей клетке без нового сетевого запроса.
        String cachedRegion = lastOwnPinfoRegion == null ? "" : lastOwnPinfoRegion.trim();
        if (!isEmpty(cachedRegion) && isCachedPinfoAlignedWithCurrentCell(mapRegNum, cachedRegion)) {
            String oldRegion = normalizeCellLabel(getCellRegionDirect(mapRegNum));
            String oldName = normalizeCellLabel(getCellName(mapRegNum));
            boolean changedFromCache = ExtMap.syncCellMetaFromPinfo(mapRegNum, cachedRegion, "");
            if (changedFromCache) {
                boolean notified = notifyMapMetaSyncIfChanged(mapRegNum, oldRegion, oldName);
                AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: pinfo region cached sync applied, reg=" + mapRegNum
                        + ", region=" + cachedRegion
                        + ", notified=" + notified);
            }
        } else if (!isEmpty(cachedRegion)) {
            AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: skip cached pinfo region sync (not aligned), reg=" + mapRegNum
                    + ", cachedRegion=" + cachedRegion
                    + ", cachedCell=" + normalizeCellLabel(lastOwnPinfoCellName)
                    + ", currentCell=" + normalizeCellLabel(getCellName(mapRegNum)));
        }

        long now = System.currentTimeMillis();
        if (mapPinfoSyncInFlight || (now - lastMapPinfoSyncAtMs) < MAP_PINFO_SYNC_COOLDOWN_MS) {
            return;
        }
        mapPinfoSyncInFlight = true;
        final String requestRegNum = mapRegNum;
        final String requestNick = ownNick.trim();
        final long requestConfirmSeq = mapConfirmSeq;

        new Thread(() -> {
            try {
                NeverApi.PinfoCompassSnapshot snapshot = NeverApi.getPinfoCompassSnapshotFromInfoApi(requestNick, "map_region_sync");
                if (snapshot == null || snapshot.offlineOrInvisible) {
                    return;
                }
                String locationName = snapshot.locationName == null ? "" : snapshot.locationName.trim();
                String locationRegion = snapshot.locationRegion == null ? "" : snapshot.locationRegion.trim();
                if (isEmpty(locationName) && isEmpty(locationRegion)) {
                    return;
                }

                String liveRegNum = null;
                if (AppVars.Profile != null) {
                    liveRegNum = normalizeRegNum(AppVars.Profile.MapLocation);
                }
                if (isEmpty(liveRegNum) || !requestRegNum.equals(liveRegNum)) {
                    return;
                }
                if (!isMapLocationConfirmedForPinfoSync(requestRegNum, requestConfirmSeq)) {
                    AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: skip pinfo sync in worker, reg is no longer confirmed, reg="
                            + requestRegNum + ", liveReg=" + liveRegNum
                            + ", confirmedReg=" + normalizeRegNum(lastMapLocationConfirmedRegNum)
                            + ", confirmedSeq=" + lastMapLocationConfirmedSeq
                            + ", expectedSeq=" + requestConfirmSeq
                            + ", autoMoving=" + AppVars.AutoMoving);
                    return;
                }

                boolean canApplyName = canApplyPinfoNameToCurrentCell(requestRegNum, locationName, locationRegion);
                boolean canApplyRegion = canApplyPinfoRegionToCurrentCell(requestRegNum, locationRegion)
                        && (canApplyName || !AppVars.AutoMoving);
                if (!isEmpty(locationRegion) && canApplyRegion) {
                    lastOwnPinfoRegion = locationRegion;
                }
                if (!isEmpty(locationName) && canApplyName) {
                    lastOwnPinfoCellName = locationName;
                }

                String oldRegion = normalizeCellLabel(getCellRegionDirect(requestRegNum));
                String oldName = normalizeCellLabel(getCellName(requestRegNum));
                String effectiveRegion = canApplyRegion ? locationRegion : "";
                String effectiveCellName = canApplyName ? locationName : "";
                boolean changed = ExtMap.syncCellMetaFromPinfo(requestRegNum, effectiveRegion, effectiveCellName);
                if (changed) {
                    boolean notified = notifyMapMetaSyncIfChanged(requestRegNum, oldRegion, oldName);
                    AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: pinfo meta sync applied, reg=" + requestRegNum
                            + ", region=" + effectiveRegion + ", cell=" + effectiveCellName
                            + ", sourceRegion=" + locationRegion
                            + ", sourceCell=" + locationName
                            + ", canApplyName=" + canApplyName
                            + ", canApplyRegion=" + canApplyRegion
                            + ", notified=" + notified);
                } else if ((!canApplyName && !isEmpty(locationName))
                        || (!canApplyRegion && !isEmpty(locationRegion))) {
                    AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: skip pinfo meta sync for current reg, reg=" + requestRegNum
                            + ", pinfoCell=" + locationName
                            + ", pinfoRegion=" + locationRegion
                            + ", currentCell=" + normalizeCellLabel(getCellName(requestRegNum))
                            + ", currentRegion=" + normalizeCellLabel(getCellRegionDirect(requestRegNum))
                            + ", nextReg=" + normalizeRegNum(AppVars.AutoMovingNextJump)
                            + ", canApplyName=" + canApplyName
                            + ", canApplyRegion=" + canApplyRegion);
                }
            } catch (Exception e) {
                AppLog.w(TAG, "MAP_NAME_SYNC_TRACE: pinfo meta sync failed", e);
            } finally {
                lastMapPinfoSyncAtMs = System.currentTimeMillis();
                mapPinfoSyncInFlight = false;
            }
        }, "map-pinfo-sync").start();
    }

    /**
     * Защита от ложной синхронизации названий карты по `ch.php?lo=1` в городских комнатах.
     *
     * Условие "мы на природе":
     * - верхний фрейм сейчас в карте (`get_id=56&act=10&go=ret/inf`) ИЛИ в HTML есть map-пейлоад `var map = [[`
     *   при условии, что URL верхнего фрейма не указывает на городской экран (`go=build`);
     * - текущий `MapLocation` указывает на известную клетку `ExtMap.Cells`.
     *
     * Почему так:
     * - после телепорта в город `AppVars.ContentMainPhp` может кратковременно хранить старый map-пейлоад
     *   от "природы", и один только `var map = [[` даёт ложноположительный контекст;
     * - если в этот момент применить pinfo (`Октал [Рынок]`) к полевой клетке, получаем неверное
     *   переименование карты (как в логах с 4-507).
     */
    private static boolean isNatureMapContextForCellRename() {
        if (AppVars.Profile == null) {
            return false;
        }
        String normalizedReg = normalizeRegNum(AppVars.Profile.MapLocation);
        if (isEmpty(normalizedReg) || !ExtMap.Cells.containsKey(normalizedReg)) {
            return false;
        }

        String topUrlLower = AppVars.url_main_top == null ? "" : AppVars.url_main_top.toLowerCase(Locale.ROOT);
        boolean mapByTopUrl = topUrlLower.contains("get_id=56")
                && topUrlLower.contains("act=10")
                && (topUrlLower.contains("go=ret") || topUrlLower.contains("go=inf"));

        // Городские экраны (`go=build`) должны жёстко блокировать map-rename.
        if (isCityMainFrameByUrl(topUrlLower)) {
            return false;
        }

        // Fallback по HTML разрешаем только когда URL верхнего фрейма пустой/неизвестный
        // либо уже явно map-контекст (`go=ret/inf`).
        boolean allowMapHtmlFallback = isEmpty(topUrlLower) || mapByTopUrl;
        boolean mapByMainHtml = allowMapHtmlFallback
                && hasNatureMapPayload(AppVars.ContentMainPhp)
                && !isCityMainFrameByHtml(AppVars.ContentMainPhp);
        return mapByTopUrl || mapByMainHtml;
    }

    /**
     * Признак городского top-frame по URL.
     *
     * Зависимости:
     * - `MainPhpCityNavigation`: маршрутизация в городе использует `go=build` и city-screen HTML.
     * - `RoomManager.isNatureMapContextForCellRename()`: этот хелпер — фильтр ложного "природного"
     *   контекста при телепортах/переходах по городу.
     */
    private static boolean isCityMainFrameByUrl(String topUrlLower) {
        if (isEmpty(topUrlLower)) {
            return false;
        }
        return topUrlLower.contains("go=build");
    }

    /**
     * Признак "природной" карты по HTML main.php.
     */
    private static boolean hasNatureMapPayload(String html) {
        return !isEmpty(html) && html.contains("var map = [[");
    }

    /**
     * Признак городского main.php по HTML (fallback, когда URL ещё не обновился).
     *
     * Берём маркеры из `MainPhpCityNavigation`:
     * - `USEMAP="#links"` + набор `<area shape=...>`;
     * - фон/ресурсы городских экранов (`/cities/`).
     */
    private static boolean isCityMainFrameByHtml(String html) {
        if (isEmpty(html)) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        boolean hasCityUseMap = lower.contains("usemap=\"#links\"") && lower.contains("<area shape=");
        boolean hasCityAssets = lower.contains("/cities/");
        return hasCityUseMap || hasCityAssets;
    }

    /**
     * Применяет отложенную синхронизацию имени клетки после подтверждения реальной позиции на карте.
     *
     * Зачем нужен метод:
     * - `ch.php` (где берётся `placename`) и `map_ajax` (где подтверждается `regNum`) приходят асинхронно;
     * - когда `syncCellNameFromRoomHtml(...)` не смог безопасно выбрать клетку, имя сохраняется как pending;
     * - этот метод завершает синхронизацию только после фактического подтверждения клетки.
     *
     * Правила применения pending:
     * - если pending привязан к конкретному `targetRegNum`, обновление разрешено только при полном совпадении;
     * - если `targetRegNum` не задан, используется текущее подтверждение `regNum`;
     * - после успешного применения или подтверждения "без изменений" pending очищается.
     *
     * Зависимости:
     * - `MapAjax.process(...)` — источник подтверждённого `regNum` (вызов этого метода);
     * - `ExtMap.Cells` — проверка существования клетки;
     * - `applyCellNameSyncAndNotify(...)` — запись имени и уведомление в чат;
     * - `getPendingRoomLocationIfFresh()` — защита от устаревшего pending по TTL.
     */
    public static void onMapLocationConfirmed(Context context, String regNum) {
        if (context == null || isEmpty(regNum)) {
            return;
        }
        String normalizedReg = regNum.trim();
        if (!ExtMap.Cells.containsKey(normalizedReg)) {
            return;
        }
        if (!normalizedReg.equals(lastMapLocationConfirmedRegNum)) {
            lastMapLocationConfirmedSeq++;
        }
        lastMapLocationConfirmedRegNum = normalizedReg;
        PendingRoomLabel pendingLabel = getPendingRoomLocationIfFresh();
        if (pendingLabel == null || isEmpty(pendingLabel.locationName)) {
            return;
        }
        if (!isEmpty(pendingLabel.targetRegNum) && !pendingLabel.targetRegNum.equals(normalizedReg)) {
            AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: keep deferred room label sync, pendingReg="
                    + pendingLabel.targetRegNum
                    + ", confirmedReg=" + normalizedReg
                    + ", serverName=" + pendingLabel.locationName);
            return;
        }
        boolean changed = applyCellNameSyncAndNotify(normalizedReg, pendingLabel.locationName);
        if (changed || isSameAsPendingRoomLocationName(pendingLabel.locationName)) {
            clearPendingRoomLocationName();
        }
    }

    /**
     * Атомарно применяет обновление названия клетки и формирует уведомление для пользователя.
     *
     * Что делает:
     * - вызывает `ExtMap.syncCellLabelFromServer(...)` для обновления runtime + `abcells.xml`;
     * - при реальном изменении отправляет в чат сообщение о замене старого названия на новое;
     * - пишет технический trace в logcat (`MAP_NAME_SYNC_TRACE`).
     *
     * Зависимости:
     * - `ExtMap.syncCellLabelFromServer(...)` — источник истины по факту изменения;
     * - `FastActionManager.writeChatMsg(...)` — UI-уведомление;
     * - `escapeHtml(...)` — безопасная вставка текста в HTML сообщения.
     *
     * @return `true`, если произошло реальное изменение; иначе `false`.
     */
    private static boolean applyCellNameSyncAndNotify(String regNum, String serverLocationName) {
        String normalizedReg = normalizeRegNum(regNum);
        String normalizedServer = normalizeCellLabel(serverLocationName);
        if (isEmpty(normalizedReg) || isEmpty(normalizedServer)) {
            return false;
        }
        String oldRegion = normalizeCellLabel(getCellRegionDirect(normalizedReg));
        String oldLabel = normalizeCellLabel(getCellName(normalizedReg));
        String ownPinfoRegionHint = resolveOwnPinfoRegionForCell(normalizedReg, normalizedServer);
        boolean metaChanged;

        // Region-aware синхронизация карты:
        // - если есть валидная подсказка региона из pinfo (`parameters[0][5]`),
        //   обновляем и `Cell.Name`, и `Cell.Region` одним вызовом;
        // - иначе оставляем существующий путь (синхронизация только названия).
        if (!isEmpty(ownPinfoRegionHint)) {
            metaChanged = ExtMap.syncCellMetaFromPinfo(normalizedReg, ownPinfoRegionHint, normalizedServer);
            AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: apply with pinfo region, reg=" + normalizedReg
                    + ", region=" + ownPinfoRegionHint + ", name=" + normalizedServer);
        } else {
            String oldLabelFromSync = ExtMap.syncCellLabelFromServer(normalizedReg, normalizedServer);
            metaChanged = oldLabelFromSync != null;
        }

        if (!metaChanged) {
            return false;
        }
        return notifyMapMetaSyncIfChanged(normalizedReg, oldRegion, oldLabel);
    }

    private static boolean notifyMapMetaSyncIfChanged(String regNum, String oldRegion, String oldLabel) {
        String normalizedReg = normalizeRegNum(regNum);
        if (isEmpty(normalizedReg)) {
            return false;
        }
        String newLabel = normalizeCellLabel(getCellName(normalizedReg));
        String newRegion = normalizeCellLabel(getCellRegionDirect(normalizedReg));
        boolean nameChanged = !normalizeCellLabel(oldLabel).equals(normalizeCellLabel(newLabel));
        boolean regionChanged = !normalizeCellLabel(oldRegion).equals(normalizeCellLabel(newRegion));
        if (!nameChanged && !regionChanged) {
            return false;
        }

        String oldMeta = formatMapCellMetaForChat(oldRegion, oldLabel);
        String newMeta = formatMapCellMetaForChat(newRegion, newLabel);
        String safeOld = escapeHtml(oldMeta);
        String safeNew = escapeHtml(newMeta);
        FastActionManager.writeChatMsg(
                "<font color=#5D7C91><b>[Карта]</b></font> "
                        + "Клетка №" + normalizedReg + " - \"" + safeOld + "\" заменено на "
                        + "Клетка №" + normalizedReg + " - \"" + safeNew + "\""
        );
        AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: reg=" + normalizedReg
                + ", oldMeta=" + oldMeta
                + ", newMeta=" + newMeta
                + ", nameChanged=" + nameChanged
                + ", regionChanged=" + regionChanged);
        return true;
    }

    private static String formatMapCellMetaForChat(String region, String cellName) {
        String normalizedRegion = normalizeCellLabel(region);
        String normalizedCell = normalizeCellLabel(cellName);
        if (!isEmpty(normalizedRegion) && !isEmpty(normalizedCell)) {
            return normalizedRegion + " [" + normalizedCell + "]";
        }
        if (!isEmpty(normalizedCell)) {
            return normalizedCell;
        }
        return normalizedRegion;
    }

    /**
     * Возвращает region-подсказку из собственного pinfo только когда она согласована с клеткой:
     * - `regNum` должен совпадать с текущей клеткой профиля;
     * - `CellName` из pinfo должен совпадать с серверным названием из room (`placename`).
     *
     * Такой guard не даёт применять устаревший region после перехода между клетками.
     */
    private static String resolveOwnPinfoRegionForCell(String regNum, String serverCellName) {
        if (isEmpty(regNum) || isEmpty(serverCellName) || AppVars.Profile == null) {
            return "";
        }
        String currentReg = normalizeRegNum(AppVars.Profile.MapLocation);
        if (isEmpty(currentReg) || !currentReg.equals(regNum)) {
            return "";
        }
        String cachedCell = normalizeCellLabel(lastOwnPinfoCellName);
        if (isEmpty(cachedCell) || !cachedCell.equals(serverCellName)) {
            return "";
        }
        return lastOwnPinfoRegion == null ? "" : lastOwnPinfoRegion.trim();
    }

    /**
     * Проверяет, что последний pinfo-snapshot относится к текущей клетке, а не к "следующему шагу".
     *
     * Используется для защиты от гонки при навигации:
     * сервер/`pinfo` и `map_ajax` могут приходить с небольшим сдвигом, и без этой проверки
     * region/name могут применяться к предыдущей клетке.
     */
    private static boolean isCachedPinfoAlignedWithCurrentCell(String regNum, String cachedRegion) {
        if (isEmpty(regNum)) {
            return false;
        }
        if (!canApplyPinfoRegionToCurrentCell(regNum, cachedRegion)) {
            return false;
        }
        String cachedCell = normalizeCellLabel(lastOwnPinfoCellName);
        if (isEmpty(cachedCell)) {
            return false;
        }
        String currentCell = normalizeCellLabel(getCellName(regNum));
        return !isEmpty(currentCell) && cachedCell.equals(currentCell);
    }

    /**
     * Можно ли применять `locationName` из pinfo к текущему `regNum`.
     *
     * Правило:
     * - если имя pinfo совпадает с текущей клеткой — можно;
     * - если совпадает со следующей клеткой маршрута (`AutoMovingNextJump`) — нельзя (это опережающий кадр);
     * - в остальных неочевидных случаях в движении не применяем имя (только region),
     *   чтобы не переименовать текущую клетку именем соседней.
     */
    private static boolean canApplyPinfoNameToCurrentCell(String regNum, String pinfoCellName, String pinfoRegion) {
        if (isEmpty(regNum) || isEmpty(pinfoCellName)) {
            return false;
        }
        if (!isEmpty(pinfoRegion) && !canApplyPinfoRegionToCurrentCell(regNum, pinfoRegion)) {
            return false;
        }
        String normalizedPinfoCell = normalizeCellLabel(pinfoCellName);
        if (isEmpty(normalizedPinfoCell)) {
            return false;
        }
        String currentCell = normalizeCellLabel(getCellName(regNum));
        if (!isEmpty(currentCell) && normalizedPinfoCell.equals(currentCell)) {
            return true;
        }

        String nextReg = normalizeRegNum(AppVars.AutoMovingNextJump);
        if (!isEmpty(nextReg) && !nextReg.equals(regNum)) {
            String nextCell = normalizeCellLabel(getCellName(nextReg));
            if (!isEmpty(nextCell) && normalizedPinfoCell.equals(nextCell)) {
                return false;
            }
        }
        return !AppVars.AutoMoving;
    }

    /**
     * Проверяет совместимость региона из pinfo с текущей клеткой.
     *
     * Правило:
     * - если у клетки region ещё не известен — разрешаем применение;
     * - если region совпадает — разрешаем;
     * - если region отличается и идёт движение — блокируем (защита от "дёргания карты");
     * - если region отличается в idle — разрешаем как корректирующее обновление.
     */
    private static boolean canApplyPinfoRegionToCurrentCell(String regNum, String pinfoRegion) {
        if (isEmpty(regNum) || isEmpty(pinfoRegion)) {
            return false;
        }
        String normalizedPinfoRegion = normalizeCellLabel(pinfoRegion);
        if (isEmpty(normalizedPinfoRegion)) {
            return false;
        }
        String currentRegion = normalizeCellLabel(getCellRegionDirect(regNum));
        if (isEmpty(currentRegion)) {
            return true;
        }
        if (normalizedPinfoRegion.equals(currentRegion)) {
            return true;
        }
        return !AppVars.AutoMoving;
    }

    /**
     * Пытается однозначно определить `regNum`, для которого нужно применить имя из room HTML.
     *
     * Правило выбора:
     * 1) Если серверное имя совпадает с текущей клеткой (`Profile.MapLocation`) — берём текущую клетку.
     * 2) Иначе, если совпадает с `AutoMovingNextJump` — берём следующую клетку маршрута.
     * 3) Если совпадений нет — возвращаем `null` (только deferred, без рискованного fallback).
     *
     * Такой порядок предотвращает запись имени в "предыдущую" клетку во время перехода.
     */
    private static String resolveCellRegNumForRoomName(String serverLocationName) {
        if (AppVars.Profile == null) {
            return null;
        }

        String currentReg = AppVars.Profile.MapLocation;
        if (isEmpty(currentReg)) {
            return null;
        }
        currentReg = currentReg.trim();
        if (!ExtMap.Cells.containsKey(currentReg)) {
            return null;
        }

        String normalizedServer = normalizeCellLabel(serverLocationName);
        String currentLabel = normalizeCellLabel(getCellName(currentReg));
        if (!normalizedServer.isEmpty() && normalizedServer.equals(currentLabel)) {
            return currentReg;
        }

        String nextReg = AppVars.AutoMovingNextJump;
        if (!isEmpty(nextReg)) {
            nextReg = nextReg.trim();
            if (ExtMap.Cells.containsKey(nextReg)) {
                String nextLabel = normalizeCellLabel(getCellName(nextReg));
                if (!normalizedServer.isEmpty() && normalizedServer.equals(nextLabel)) {
                    return nextReg;
                }
            }
        }

        return null;
    }

    private static String getCellName(String regNum) {
        Cell cell = ExtMap.Cells.get(regNum);
        return cell != null ? cell.Name : null;
    }

    private static String getCellRegionDirect(String regNum) {
        Cell cell = ExtMap.Cells.get(regNum);
        return cell != null ? cell.Region : null;
    }

    /**
     * Нормализует отображаемое название клетки для корректных сравнений:
     * - заменяет NBSP на обычный пробел;
     * - схлопывает повторные пробелы;
     * - убирает пробелы по краям.
     *
     * Важно:
     * - логика должна быть эквивалентна нормализации в `ExtMap.normalizeCellLabel(...)`,
     *   чтобы одно и то же имя одинаково сравнивалось в `RoomManager` и `ExtMap`.
     */
    private static String normalizeCellLabel(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * Проверяет, что текущая клетка подтверждена `map_ajax` и не устарела по sequence.
     *
     * Логика без таймаутов:
     * - `lastMapLocationConfirmedRegNum` хранит последнюю подтверждённую клетку;
     * - `lastMapLocationConfirmedSeq` увеличивается на переходах между клетками;
     * - pinfo-sync разрешается только если `regNum` совпадает с confirmed-reg
     *   и sequence не изменился с момента постановки задачи.
     *
     * Это убирает риск записи данных соседней клетки при быстрых переходах
     * (1 сек/клетка) и не добавляет дополнительных запросов к серверу.
     */
    private static boolean isMapLocationConfirmedForPinfoSync(String regNum, long expectedSeq) {
        String normalizedReg = normalizeRegNum(regNum);
        if (isEmpty(normalizedReg)) {
            return false;
        }
        String confirmedReg = normalizeRegNum(lastMapLocationConfirmedRegNum);
        if (isEmpty(confirmedReg)) {
            return !AppVars.AutoMoving;
        }
        if (!normalizedReg.equals(confirmedReg)) {
            return false;
        }
        if (expectedSeq > 0L && lastMapLocationConfirmedSeq != expectedSeq) {
            return false;
        }
        return true;
    }

    /**
     * Сохраняет отложенное имя локации из room HTML до подтверждения фактической клетки на карте.
     *
     * @param locationName имя из `<font class=placename>`
     * @param targetRegNum ожидаемый `regNum` (обычно `AutoMovingNextJump`), может быть `null`
     */
    private static synchronized void cachePendingRoomLocationName(String locationName, String targetRegNum) {
        pendingRoomLocationName = locationName;
        pendingRoomLocationTargetRegNum = normalizeRegNum(targetRegNum);
        pendingRoomLocationNameAtMs = System.currentTimeMillis();
    }

    /**
     * Возвращает pending-имя, если оно ещё актуально по TTL.
     * При протухании автоматически очищает pending-состояние.
     */
    private static synchronized PendingRoomLabel getPendingRoomLocationIfFresh() {
        if (isEmpty(pendingRoomLocationName)) {
            return null;
        }
        long age = System.currentTimeMillis() - pendingRoomLocationNameAtMs;
        if (age > PENDING_ROOM_LABEL_TTL_MS) {
            clearPendingRoomLocationName();
            return null;
        }
        return new PendingRoomLabel(pendingRoomLocationName, pendingRoomLocationTargetRegNum);
    }

    /**
     * Проверяет, что переданное имя совпадает с текущим pending-именем по нормализованной форме.
     */
    private static synchronized boolean isSameAsPendingRoomLocationName(String locationName) {
        if (isEmpty(locationName) || isEmpty(pendingRoomLocationName)) {
            return false;
        }
        return normalizeCellLabel(locationName).equals(normalizeCellLabel(pendingRoomLocationName));
    }

    /**
     * Полностью очищает deferred-состояние синхронизации имени клетки.
     */
    private static synchronized void clearPendingRoomLocationName() {
        pendingRoomLocationName = null;
        pendingRoomLocationTargetRegNum = null;
        pendingRoomLocationNameAtMs = 0L;
    }

    /**
     * Нормализует `regNum` для безопасного хранения в pending-состоянии.
     * Пустая строка приводится к `null`.
     */
    private static String normalizeRegNum(String regNum) {
        if (regNum == null) {
            return null;
        }
        String normalized = regNum.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * DTO для хранения pending-данных синхронизации:
     * - `locationName` — имя локации из room HTML;
     * - `targetRegNum` — ожидаемый `regNum`, к которому можно применить это имя.
     */
    private static final class PendingRoomLabel {
        final String locationName;
        final String targetRegNum;

        PendingRoomLabel(String locationName, String targetRegNum) {
            this.locationName = locationName;
            this.targetRegNum = targetRegNum;
        }
    }

    /**
     * Определяет, что сейчас активна боевая сессия в основном фрейме.
     *
     * Зависимости:
     * - `AppVars.ContentMainPhp`: последний HTML боя, который сохраняет MainPhp;
     * - `AppVars.url_main_top`: текущий URL верхнего фрейма;
     * - `AppVars.FightLink`: ссылка цикла боя/завершения.
     *
     * Почему отдельный метод:
     * - `RoomManager` вызывается из чата (нижний фрейм), где нет прямого доступа к парсеру боя;
     * - поэтому используем агрегированное состояние из `AppVars` как защитную проверку перед авто-нападением.
     */
    private static boolean isFightSessionActive() {
        String mainHtml = AppVars.ContentMainPhp;
        if (mainHtml != null && (mainHtml.contains("var fight_ty") || mainHtml.contains("magic_slots();"))) {
            return true;
        }

        String topUrl = AppVars.url_main_top;
        if (topUrl != null && topUrl.contains("get_id=56&act=10&go=inf")) {
            return true;
        }

        String fightLink = AppVars.FightLink;
        return fightLink != null && fightLink.contains("get_id=61&act=");
    }

    public static void startTracing(MainActivity mainActivity) {
        autoAttackBlackList.clear();
    }

    public static void stopTracing() {
        autoAttackBlackList.clear();
    }

    // Формирует сообщения о входе/выходе игроков в локации (порт C# логики).
    /**
     * Формирует и публикует события перемещения по локации (вход/выход видимых персонажей и невидимок).
     *
     * Зависимости:
     * - `AppVars.DoShowWalkers`: глобальный флаг включения трекинга передвижений;
     * - `AppVars.url_ch_list`: URL текущей комнаты, используется для вычисления ключа координат `r=...`;
     * - `AppVars.myCharsOld`, `AppVars.myCoordOld`, `AppVars.myLocOld`, `AppVars.myNevidsOld`: предыдущее состояние;
     * - `parseVisibleCharsMap(...)`: извлечение видимых никнеймов из `ChatListU`;
     * - `resolveNevidsCount(...)`: расчет количества невидимок как разницы "серверный общий счётчик на клетке - видимые";
     * - `buildWalkersMessage(...)`: сборка человекочитаемого текста для чата;
     * - `FastActionManager.writeChatMsg(...)`: публикация уведомлений в игровой чат;
     * - `EventSounds.playSndMsg()`: звуковой сигнал при входящих событиях.
     *
     * Алгоритм:
     * 1) Проверяет, что трекинг передвижений включен и HTML не пустой.
     * 2) Определяет текущую локацию и набор видимых персонажей.
     * 3) На той же клетке/локации сравнивает прошлый и текущий наборы:
     *    - кто исчез из видимых;
     *    - кто появился в видимых;
     *    - как изменилось число невидимок.
     * 4) Формирует сообщения и отправляет их в чат.
     * 5) Обновляет снимок состояния для следующего тика.
     */
    private static void FilterGetWalkers(String html, FilterProcRoomResult filterResult) {
        if (!AppVars.DoShowWalkers || isEmpty(html)) {
            return;
        }

        String locationNow = extractLocationName(html);
        if (isEmpty(locationNow)) {
            return;
        }
        String currentCellRegNum = resolveCurrentCellRegNumForWalkers(locationNow);

        Map<String, String> charsNow = parseVisibleCharsMap(html);
        int visibleChars = charsNow.size();
        int locationCharsFromServer = parseLocationCharsCount(html);
        AppVars.myNevids = resolveNevidsCount(html, visibleChars);
        AppLog.d(TAG, AA_TRACE_PREFIX + " FilterGetWalkers: loc=" + locationNow
                + ", coord=" + extractRoomCoordKey(AppVars.url_ch_list)
                + ", regNum=" + currentCellRegNum
                + ", visibleChars=" + visibleChars
                + ", locationCharsFromServer=" + locationCharsFromServer
                + ", nevids=" + AppVars.myNevids);

        String roomCoordNow = extractRoomCoordKey(AppVars.url_ch_list);
        boolean sameCoord = roomCoordNow.equals(AppVars.myCoordOld);
        boolean sameLocation = locationNow.equals(AppVars.myLocOld);

        if (sameCoord && sameLocation) {
            Map<String, String> leftChars = new LinkedHashMap<>();
            for (Map.Entry<String, String> oldEntry : AppVars.myCharsOld.entrySet()) {
                String nick = oldEntry.getKey();
                if (!charsNow.containsKey(nick)) {
                    if (isSelfNick(nick)) {
                        FastActionManager.writeChatMsg("<b><font color=#01A9DB>Мы ушли в невид</font></b>");
                    } else {
                        leftChars.put(nick, oldEntry.getValue());
                    }
                }
            }

            Map<String, String> comeChars = new LinkedHashMap<>();
            for (Map.Entry<String, String> nowEntry : charsNow.entrySet()) {
                String nick = nowEntry.getKey();
                if (!AppVars.myCharsOld.containsKey(nick)) {
                    if (isSelfNick(nick)) {
                        FastActionManager.writeChatMsg("<b><font color=#DF0101>Мы вышли из невида!</font></b>");
                    } else {
                        comeChars.put(nick, nowEntry.getValue());
                    }
                }
            }

            int diffNevids = AppVars.myNevids - AppVars.myNevidsOld;
            if (!leftChars.isEmpty() || !comeChars.isEmpty() || diffNevids != 0) {
                int prevTotalChars = AppVars.myCharsOld.size() + Math.max(0, AppVars.myNevidsOld);
                int currTotalChars = locationCharsFromServer >= 0
                        ? locationCharsFromServer
                        : (visibleChars + Math.max(0, AppVars.myNevids));

                String revealFromNevidMsg = buildNevidStateChangeMessage(
                        comeChars,
                        leftChars,
                        diffNevids,
                        prevTotalChars,
                        currTotalChars,
                        false
                );
                String hideToNevidMsg = buildNevidStateChangeMessage(
                        leftChars,
                        comeChars,
                        diffNevids,
                        prevTotalChars,
                        currTotalChars,
                        true
                );

                AppVars.myWalkers1 = !isEmpty(revealFromNevidMsg)
                        ? revealFromNevidMsg
                        : buildWalkersMessage(comeChars, diffNevids, true, currentCellRegNum);
                AppVars.myWalkers2 = !isEmpty(hideToNevidMsg)
                        ? hideToNevidMsg
                        : buildWalkersMessage(leftChars, diffNevids, false, currentCellRegNum);
            }
        }

        AppVars.myCoordOld = roomCoordNow;
        AppVars.myLocOld = locationNow;
        AppVars.myCharsOld.clear();
        AppVars.myCharsOld.putAll(charsNow);
        AppVars.myNevidsOld = AppVars.myNevids;

        if (!isEmpty(AppVars.myWalkers1)) {
            EventSounds.playSndMsg();
            FastActionManager.writeChatMsg(MainPhp.buildServerChatTimeHtmlExternal() + AppVars.myWalkers1);
            AppVars.myWalkers1 = "";
        }

        if (!isEmpty(AppVars.myWalkers2)) {
            FastActionManager.writeChatMsg(MainPhp.buildServerChatTimeHtmlExternal() + AppVars.myWalkers2);
            AppVars.myWalkers2 = "";
        }
    }

    /**
     * Извлекает карту видимых персонажей из JS-массива `ChatListU`.
     *
     * Зависимости:
     * - формат серверного блока `var ChatListU = new Array(...);`;
     * - `parseChatListEntries(...)`: безопасное разбиение массива на записи;
     * - `normalizeChatUserEntry(...)`: нормализация записи к единому `:`-формату.
     *
     * Правила фильтрации:
     * - пропускаются пустые/битые записи;
     * - пропускаются записи с `<i>` (невидимые в списке видимых ников не учитываются);
     * - дубликаты ников отбрасываются, сохраняется первое вхождение.
     *
     * @param html HTML комнаты/чата
     * @return карта `nick -> rawEntry` для всех видимых персонажей на текущей клетке
     */
    private static Map<String, String> parseVisibleCharsMap(String html) {
        Map<String, String> visibleChars = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("var\\s+ChatListU\\s*=\\s*new Array\\((.*?)\\);", Pattern.DOTALL).matcher(html);
        if (!matcher.find()) {
            return visibleChars;
        }

        String[] parsedEntries = parseChatListEntries(matcher.group(1));
        for (String parsedEntry : parsedEntries) {
            String normalized = normalizeChatUserEntry(parsedEntry);
            if (isEmpty(normalized)) {
                continue;
            }
            String[] parts = normalized.split(":");
            if (parts.length < 3) {
                continue;
            }
            String nick = parts[1];
            if (nick.contains("<i>")) {
                continue;
            }
            if (!visibleChars.containsKey(nick)) {
                visibleChars.put(nick, normalized);
            }
        }
        return visibleChars;
    }

    /**
     * Собирает текст уведомления о входе/выходе персонажей и изменении количества невидимок.
     *
     * Зависимости:
     * - `HtmlChar(...)`: рендер никнейма/иконок в HTML-представление;
     * - правила склонения и построения фраз для русского текста.
     *
     * Поведение:
     * - для `incoming=true` и `diffNevids>0` добавляет блок "невидимка/невидимок";
     * - для `incoming=false` и `diffNevids<0` добавляет блок об ушедших невидимках;
     * - добавляет список видимых персонажей из `chars`;
     * - в конце добавляет хвост действия ("приходит/приходят", "покидает/покидают").
     *
     * @param chars карта персонажей, участвующих в конкретном событии
     * @param diffNevids разница `currentNevids - previousNevids`
     * @param incoming true для входа, false для выхода
     * @param currentCellRegNum номер текущей клетки (`regNum`) для дописки в конце сообщения
     * @return готовая строка для чата; пустая строка, если событие нечего публиковать
     */
    private static String buildWalkersMessage(Map<String, String> chars,
                                              int diffNevids,
                                              boolean incoming,
                                              String currentCellRegNum) {
        StringBuilder sb = new StringBuilder();
        int count = 0;

        if (incoming && diffNevids > 0) {
            count = 1;
            sb.append("<font color=#5D7C91><b>");
            if (diffNevids == 1) {
                sb.append("Невидимка");
            } else {
                sb.append(diffNevids).append(" невидимок");
            }
            sb.append("</b></font>");
        } else if (!incoming && diffNevids < 0) {
            count = 1;
            int hiddenCount = -diffNevids;
            sb.append("<font color=#5D7C91><b>");
            if (hiddenCount == 1) {
                sb.append("Невидимка");
            } else {
                sb.append(hiddenCount).append(" невидимок");
            }
            sb.append("</b></font>");
        }

        for (String rawChar : chars.values()) {
            primeRoomPinfoCacheForRawEntry(rawChar);
            if (count > 0) {
                sb.append(", ");
            }
            count++;
            try {
                sb.append(HtmlChar(rawChar));
            } catch (Exception e) {
                AppLog.w(TAG, "buildWalkersMessage: skip malformed char entry: " + rawChar, e);
            }
        }

        if (count > 0) {
            if (incoming) {
                sb.append(count > 1 ? " приходят в локацию" : " приходит в локацию");
            } else {
                sb.append(count > 1 ? " покидают локацию" : " покидает локацию");
            }
            if (!isEmpty(currentCellRegNum)) {
                sb.append(" (клетка ").append(escapeHtml(currentCellRegNum)).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * Определяет актуальный `regNum` для room-сообщений трекинга локации.
     *
     * Приоритет:
     * 1) однозначное сопоставление `placename` -> `regNum` через `resolveCellRegNumForRoomName(...)`;
     * 2) fallback на `Profile.MapLocation`.
     */
    private static String resolveCurrentCellRegNumForWalkers(String locationNow) {
        String resolvedByRoomName = resolveCellRegNumForRoomName(locationNow);
        if (!isEmpty(resolvedByRoomName)) {
            return resolvedByRoomName.trim();
        }
        if (AppVars.Profile == null || isEmpty(AppVars.Profile.MapLocation)) {
            return "";
        }
        return AppVars.Profile.MapLocation.trim();
    }

    /**
     * Формирует отдельное сообщение для перехода видимых персонажей в невидимость и обратно,
     * если общее количество на клетке не изменилось.
     *
     * Пример:
     * - было 2/2 (видимых/всего), стало 1/2, пропал ник "N" -> "N перешёл в невидимку".
     */
    private static String buildNevidStateChangeMessage(
            Map<String, String> changedChars,
            Map<String, String> oppositeChangedChars,
            int diffNevids,
            int prevTotalChars,
            int currTotalChars,
            boolean toNevid) {
        if (changedChars == null || changedChars.isEmpty()) {
            return "";
        }
        if (oppositeChangedChars != null && !oppositeChangedChars.isEmpty()) {
            return "";
        }
        if (prevTotalChars < 0 || currTotalChars < 0 || prevTotalChars != currTotalChars) {
            return "";
        }

        int expectedDiff = toNevid ? changedChars.size() : -changedChars.size();
        if (diffNevids != expectedDiff) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String rawChar : changedChars.values()) {
            primeRoomPinfoCacheForRawEntry(rawChar);
            if (count > 0) {
                sb.append(", ");
            }
            count++;
            try {
                sb.append(HtmlChar(rawChar));
            } catch (Exception e) {
                AppLog.w(TAG, "buildNevidStateChangeMessage: skip malformed char entry: " + rawChar, e);
            }
        }
        if (count == 0) {
            return "";
        }

        sb.insert(0, "<font color=#5D7C91><b>[Невид]</b></font> ");

        if (toNevid) {
            sb.append(count > 1 ? " перешли в невидимку" : " перешёл в невидимку");
        } else {
            sb.append(count > 1 ? " вышли из невидимки" : " вышел из невидимки");
        }
        return sb.toString();
    }

    private static boolean isSelfNick(String nick) {
        if (AppVars.Profile == null || isEmpty(AppVars.Profile.UserNick)) {
            return false;
        }
        return AppVars.Profile.UserNick.equalsIgnoreCase(stripItalic(nick));
    }

    /**
     * Подогревает cache эффектов для рендера сообщений слежения за локацией.
     *
     * Использует существующий контур `resolveWoundTypeFromPinfoCached(...)`:
     * - если cache свежий, сетевой запрос не выполняется;
     * - если cache устарел/пустой, данные обновляются и становятся доступны в `HtmlChar(...)`.
     */
    private static void primeRoomPinfoCacheForRawEntry(String rawChar) {
        if (!isShowAllRoomEffectsEnabled() || isEmpty(rawChar)) {
            return;
        }
        try {
            String nick = extractNick(rawChar);
            if (isEmpty(nick)) {
                return;
            }
            resolveWoundTypeFromPinfoCached(nick);
        } catch (Exception e) {
            AppLog.w(TAG, "primeRoomPinfoCacheForRawEntry: failed, rawChar=" + rawChar, e);
        }
    }

    private static String extractLocationName(String html) {
        String location = extractBetween(html, "<font class=placename><b>", "</b>");
        if (isEmpty(location)) {
            location = extractBetween(html, "<font class=placename><b>", "</b></font>");
        }
        if (isEmpty(location)) {
            return "";
        }
        return location.replace("<br>", " ").trim();
    }

    private static String extractRoomCoordKey(String roomUrl) {
        if (isEmpty(roomUrl)) {
            return "";
        }
        Matcher matcher = Pattern.compile("[?&]r=([^&]+)").matcher(roomUrl);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeJsSingleQuoted(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }

    /**
     * Вычисляет текущее число невидимок на клетке.
     *
     * Зависимости:
     * - `parseLocationCharsCount(...)`: парсинг серверного общего количества персонажей именно на локации;
     * - `AppVars.myNevids`: резервное значение, если из HTML не удалось извлечь общий счётчик.
     *
     * Формула:
     * - `nevids = max(0, totalCharsOnLocation - visibleChars)`.
     *
     * @param html HTML текущей страницы комнаты
     * @param visibleChars количество видимых ников из `ChatListU`
     * @return количество невидимок на текущей клетке
     */
    private static int resolveNevidsCount(String html, int visibleChars) {
        int totalChars = parseLocationCharsCount(html);
        if (totalChars < 0) {
            return AppVars.myNevids;
        }
        int nevids = totalChars - Math.max(0, visibleChars);
        return Math.max(0, nevids);
    }

    /**
     * Извлекает серверное количество персонажей на текущей локации.
     *
     * Зависимости:
     * - `parseFirstInt(...)`: первичный парсинг по приоритетному шаблону рядом с названием локации;
     * - `parseLastBracketedIntBeforeChatList(...)`: резервный парсинг последнего `[N]` до блока `ChatListU`.
     *
     * Почему два шага:
     * - в разных серверных шаблонах счетчик может находиться в разных местах HTML;
     * - резервный шаг нужен для устойчивости к вариациям верстки.
     *
     * @param html HTML страницы комнаты
     * @return количество персонажей на локации или `-1`, если счетчик не найден
     */
    private static int parseLocationCharsCount(String html) {
        int totalChars = parseFirstInt(html, "(?is)</b>\\s*</font>\\s*</a>\\s*\\[\\s*(\\d+)\\s*\\]");
        if (totalChars >= 0) {
            return totalChars;
        }
        return parseLastBracketedIntBeforeChatList(html);
    }

    /**
     * Резервно ищет последний числовой маркер `[N]` перед определением `var ChatListU`.
     *
     * Зависимости:
     * - серверный инвариант: счетчик "персонажей на клетке" располагается в HTML до чата;
     * - граница поиска по `var ChatListU`, чтобы не зацепить нецелевые счетчики из других блоков.
     *
     * @param html HTML страницы комнаты
     * @return найденное значение `N` или `-1`, если подходящий маркер отсутствует
     */
    private static int parseLastBracketedIntBeforeChatList(String html) {
        if (isEmpty(html)) {
            return -1;
        }
        int chatListPos = html.indexOf("var ChatListU");
        int searchLimit = chatListPos >= 0 ? chatListPos : html.length();
        String prefix = html.substring(0, searchLimit);
        Matcher matcher = Pattern.compile("\\[\\s*(\\d+)\\s*\\]").matcher(prefix);
        int result = -1;
        while (matcher.find()) {
            try {
                result = Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {
                // Игнорируем повреждённый числовой фрагмент и продолжаем поиск,
                // чтобы не терять корректный `[N]`, который может встретиться позже.
            }
        }
        return result;
    }

    private static int parseFirstInt(String source, String regex) {
        if (isEmpty(source) || isEmpty(regex)) {
            return -1;
        }
        Matcher matcher = Pattern.compile(regex).matcher(source);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String extractBetween(String text, String start, String end) {
        if (text == null) {
            return "";
        }
        int startIndex = text.indexOf(start);
        if (startIndex < 0) {
            return "";
        }
        startIndex += start.length();
        int endIndex = text.indexOf(end, startIndex);
        if (endIndex < 0) {
            return "";
        }
        return text.substring(startIndex, endIndex);
    }

    private static String HtmlChar(String schar) {
        String[] strArray = schar.split(":");
        String nnSec = strArray[1];
        String login = strArray[1];
        String pinfoNickEncoded = encodeNickForPinfoUrl(nnSec);
        int classId = parseClassIdSafe(ContactsManager.getClassIdOfContact(login));

        String color = "#000000";
        if (classId == CONTACT_CLASS_FRIEND) {
            color = "#0B610B"; // friend (desktop parity)
        } else if (classId == CONTACT_CLASS_ENEMY) {
            color = "#8A0808"; // enemy (desktop parity)
        }
        if (classId != CONTACT_CLASS_NEUTRAL) {
            FileLogger.trace("roommanager", "[ROOM_COLOR] nick=" + login + ", classId=" + classId + ", color=" + color);
        }

        while (nnSec.contains("+")) {
            nnSec = nnSec.replace("+", "%2B");
        }

        if (login.contains("<i>")) {
            login = login.replace("<i>", "");
            login = login.replace("</i>", "");
            nnSec = nnSec.replace("<i>", "");
            nnSec = nnSec.replace("</i>", "");
        }

        String ss = "";
        String altadd = "";
        if (strArray[3].length() > 1) {
            String[] signArray = strArray[3].split(";");
            if (signArray.length > 2 && signArray[2].length() > 1) {
                altadd = " (" + signArray[2] + ")";
            }

            ss =
                "<img src=http://image.neverlands.ru/signs/" +
                signArray[0] +
                " width=15 height=12 align=absmiddle alt=\"" +
                signArray[1] +
                altadd +
                "\">&nbsp;";
        }

        String sleeps = "";
        if (strArray.length > 4 && strArray[4].length() > 1) {
            sleeps =
                "<img src=http://image.neverlands.ru/signs/molch.gif width=15 height=12 border=0 alt=\"" +
                strArray[4] +
                "\" align=absmiddle>";
        }

        String ign = "";
        if (strArray.length > 5 && strArray[5].equals("1")) {
            ign =
                "<a href=\"javascript:ch_clear_ignor('" +
                login +
                "');\"><img src=http://image.neverlands.ru/signs/ignor/3.gif width=15 height=12 border=0 alt=\"Снять игнорирование\"></a>";
        }

        String inj = "";
        Integer injuryEffectId = null;
        if (strArray.length > 6 && !strArray[6].equals("0")) {
            injuryEffectId = 1;
            if (strArray[6].contains("боевая")) {
                injuryEffectId = 1;
                strArray[1] = "<font color=\"#666600\">" + strArray[1] + "</font>";
            } else if (strArray[6].contains("тяжелая")) {
                injuryEffectId = 2;
                strArray[1] = "<font color=\"#c10000\">" + strArray[1] + "</font>";
            } else if (strArray[6].contains("средняя")) {
                injuryEffectId = 3;
                strArray[1] = "<font color=\"#e94c69\">" + strArray[1] + "</font>";
            } else if (strArray[6].contains("легкая")) {
                injuryEffectId = 4;
                strArray[1] = "<font color=\"#ef7f94\">" + strArray[1] + "</font>";
            }
        }
        List<ContactRenderHelper.EffectState> cachedEffectStates = getCachedEffectStatesForNick(login);
        List<Integer> cachedEffectIds = getCachedEffectIdsForNick(login);
        if (cachedEffectStates.isEmpty()) {
            Contact contact = findContactByNickIgnoreCase(login);
            if (contact != null) {
                cachedEffectStates = normalizeEffectStates(
                        ContactRenderHelper.parseEffectStatesCsv(contact.effectStates, contact.effectIds)
                );
            }
        }
        if (cachedEffectIds.isEmpty() && !cachedEffectStates.isEmpty()) {
            cachedEffectIds = ContactRenderHelper.extractEffectIds(cachedEffectStates);
        }
        if (isShowAllRoomEffectsEnabled() && (!cachedEffectStates.isEmpty() || !cachedEffectIds.isEmpty())) {
            if (cachedEffectStates.isEmpty()) {
                cachedEffectStates = normalizeEffectStates(
                        ContactRenderHelper.parseEffectStatesCsv("", ContactRenderHelper.toEffectIdsCsv(cachedEffectIds))
                );
            }
            inj = buildEffectIconsHtml(cachedEffectStates, strArray.length > 6 ? strArray[6] : "");
        } else {
            Integer fallbackInjuryEffectId = injuryEffectId != null
                    ? injuryEffectId
                    : resolveInjuryEffectIdFromEffects(cachedEffectIds);
            if (fallbackInjuryEffectId != null) {
                ContactRenderHelper.EffectState singleState = findEffectStateById(cachedEffectStates, fallbackInjuryEffectId);
                if (singleState == null) {
                    singleState = new ContactRenderHelper.EffectState(fallbackInjuryEffectId, 1, "");
                }
                inj = buildSingleEffectIconHtml(singleState, strArray.length > 6 ? strArray[6] : "");
            }
        }

        String psg = "";
        if (strArray.length > 7 && !strArray[7].equals("0")) {
            String[] dilers = {"", "Дилер", "", "", "", "", "", "", "", "", "", "Помощник дилера"};
            psg =
                "<img src=http://image.neverlands.ru/signs/d_sm_" +
                strArray[7] +
                ".gif width=15 height=12 align=absmiddle border=0 alt=\"" +
                dilers[Integer.parseInt(strArray[7])] +
                "\">&nbsp;";
        }

        String align = "";
        if (strArray.length > 8 && !strArray[8].equals("0")) {
            String[] signArray = strArray[8].split(";");
            if (signArray.length >= 2) {
                align =
                    "<img src=http://image.neverlands.ru/signs/" +
                    signArray[0] +
                    " width=15 height=12 align=absmiddle border=0 alt=\"" +
                    signArray[1] +
                    "\">&nbsp";
            }
        }

        return
            "<a href=\"#\" onclick=\"top.say_private('" +
            login +
            "');\"><img src=http://image.neverlands.ru/chat/private.gif width=11 height=12 border=0 align=absmiddle></a>&nbsp;" +
            psg +
            align +
            ss +
            "<a class=\"activenick\" style=\"color:" + color + " !important;\" href=\"#\" onclick=\"top.say_to('" +
            login +
            "');\"><font class=nickname color=\"" + color + "\" style=\"color:" + color + " !important;\"><b style=\"color:" + color + " !important;\">" +
            strArray[1] +
            "</b></font></a><span class=\"ab-room-level\" style=\"color:" + color + " !important;\">[" +
            strArray[2] +
            "]</span><a href=\"http://neverlands.ru/pinfo.cgi?" +
            pinfoNickEncoded +
            "\" onclick=\"window.open(this.href);\"><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>" +
            inj +
            sleeps +
            ign;
    }

    /**
     * Возвращает HTML-представление игрока (private/info/ник/уровень/травма) 1:1 как в room-list.
     * Источник: последний успешно распарсенный `ChatListU` из `ch.php?lo=1`.
     */
    public static String buildRoomUserHtmlByNick(String nick) {
        if (isEmpty(nick)) {
            return "";
        }
        String key = normalizeNickKey(stripItalic(nick));
        if (isEmpty(key)) {
            return "";
        }
        String rawEntry = lastRoomChatEntryByNick.get(key);
        if (isEmpty(rawEntry)) {
            return "";
        }
        try {
            return HtmlChar(rawEntry);
        } catch (Exception e) {
            AppLog.w(TAG, "buildRoomUserHtmlByNick: failed, nick=" + nick + ", rawEntry=" + rawEntry, e);
            return "";
        }
    }

    /**
     * Единый формат ника для сообщений в чате.
     *
     * Приоритет:
     * 1) room-list рендер 1:1 (`HtmlChar`) по последнему `ChatListU`;
     * 2) fallback рендер (private + кликабельный ник) с цветом по classId:
     *    враг=красный, друг=зелёный, нейтрал=обычный.
     */
    public static String buildUnifiedChatNickHtml(String nick) {
        return buildUnifiedChatNickHtml(nick, null, null, null);
    }

    /**
     * Единый рендер ника для чата с optional override-метаданными из pinfo.
     *
     * Использование:
     * - базовый путь: только `nick` (данные берём из room/contacts);
     * - расширенный путь: можно передать `level/inclination/clanToken`, если
     *   в room-list цели ещё нет, но pinfo уже получен (например, в Компасе).
     */
    public static String buildUnifiedChatNickHtml(
            String nick,
            Integer levelOverride,
            Integer inclinationOverride,
            String clanTokenOverride) {
        if (isEmpty(nick)) {
            return "";
        }
        String cleanNick = stripItalic(nick).trim();
        if (isEmpty(cleanNick)) {
            return "";
        }

        String roomHtml = buildRoomUserHtmlByNick(cleanNick);
        if (!isEmpty(roomHtml)) {
            return roomHtml;
        }

        int classId = parseClassIdSafe(ContactsManager.getClassIdOfContact(cleanNick));
        Contact cachedContact = findContactByNickIgnoreCase(cleanNick);
        String color = "#000000";
        if (classId == CONTACT_CLASS_ENEMY) {
            color = "#8A0808"; // enemy (desktop parity)
        } else if (classId == CONTACT_CLASS_FRIEND) {
            color = "#0B610B"; // friend (desktop parity)
        }

        String escapedNick = escapeHtml(cleanNick);
        String nickForJs = escapeJsSingleQuoted(cleanNick);
        String pinfoNickEncoded = encodeNickForPinfoUrl(cleanNick);
        String inclinationHtml = buildUnifiedInclinationHtml(cachedContact, inclinationOverride);
        String clanHtml = buildUnifiedClanIconHtml(cachedContact, clanTokenOverride);
        String effectsHtml = buildUnifiedEffectsHtml(cleanNick, cachedContact);
        int resolvedLevel = resolveUnifiedLevel(cleanNick, levelOverride);
        String levelHtml = resolvedLevel > 0
                ? " [<font class=nickname color=\"" + color + "\">" + resolvedLevel + "</font>]"
                : "";
        return "<a href=\"#\" onclick=\"top.say_private('" + nickForJs
                + "');\"><img src=http://image.neverlands.ru/chat/private.gif width=11 height=12 border=0 align=absmiddle></a>&nbsp;"
                + inclinationHtml
                + clanHtml
                + "<a class=\"activenick\" href=\"#\" onclick=\"top.say_to('" + nickForJs
                + "');\"><font class=nickname color=\"" + color + "\"><b>"
                + escapedNick + "</b></font></a>"
                + levelHtml
                + "<a href=\"http://neverlands.ru/pinfo.cgi?" + pinfoNickEncoded
                + "\" onclick=\"window.open(this.href);\"><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>"
                + effectsHtml;
    }

    private static int resolveUnifiedLevel(String cleanNick, Integer levelOverride) {
        if (levelOverride != null && levelOverride > 0) {
            return levelOverride;
        }
        return ContactsManager.getLevelOfContact(cleanNick);
    }

    private static String buildUnifiedInclinationHtml(Contact contact, Integer inclinationOverride) {
        if (inclinationOverride != null && inclinationOverride > 0) {
            return buildInclinationIconHtmlByValue(inclinationOverride, "");
        }
        return buildContactInclinationHtml(contact);
    }

    private static String buildUnifiedClanIconHtml(Contact contact, String clanTokenOverride) {
        if (!isEmpty(clanTokenOverride)) {
            return buildClanIconHtmlByToken(clanTokenOverride, contact != null ? contact.clanName : "");
        }
        return buildContactClanIconHtml(contact);
    }

    private static String buildUnifiedEffectsHtml(String nick, Contact contact) {
        List<ContactRenderHelper.EffectState> effectStates = Collections.emptyList();
        if (contact != null) {
            effectStates = normalizeEffectStates(
                    ContactRenderHelper.parseEffectStatesCsv(contact.effectStates, contact.effectIds)
            );
        }
        if (effectStates.isEmpty()) {
            effectStates = getCachedEffectStatesForNick(nick);
        }
        if (effectStates.isEmpty()) {
            List<Integer> effectIds = getCachedEffectIdsForNick(nick);
            if (!effectIds.isEmpty()) {
                effectStates = normalizeEffectStates(
                        ContactRenderHelper.parseEffectStatesCsv("", ContactRenderHelper.toEffectIdsCsv(effectIds))
                );
            }
        }
        return buildEffectIconsHtml(effectStates, "");
    }

    /**
     * Инжектирует сгенерированный Java-стороной HTML список игроков в ответ ch.php?lo=1.
     * 
     * Решение для Android WebView, где ch_list.js не может корректно работать 
     * с объектом top.frames[], так как WebView это изолированная среда.
     * 
     * Алгоритм:
     * 1) Если filterResult.html пусто (нет игроков) - возвращаем исходный HTML
     * 2) Ищет в оригинальном HTML место для инжекции:
     *    - ищет скрипт с ChatListU и место после него
     *    - или ищет стандартное размещение списка (между маркерами if/for)
     * 3) В это место вставляет контейнер DIV с ID для легкого нахождения в JS
     * 4) Окружает сгенерированный HTML в правильные теги форматирования
     * 5) Заменяет оригинальное содержимое на наше
     */
    private static String injectPlayerListHtmlIntoChatPhp(String html, FilterProcRoomResult filterResult) {
        if (html == null || filterResult.html == null || filterResult.html.isEmpty()) {
            return html;
        }
        
        try {
            // Обычно сервер возвращает HTML с JavaScript инициализацией ChatListU
            // Наша задача - найти место, где функция chatlist_build была бы вызвана,
            // и подставить туда готовый HTML
            
            // Ищем скрипт с ChatListU для определения позиции
            Pattern chatListPattern = Pattern.compile(
                    "var\\s+ChatListU\\s*=\\s*new\\s+Array\\((.*?)\\);",
                    Pattern.DOTALL);
            Matcher chatListMatcher = chatListPattern.matcher(html);
            
            if (chatListMatcher.find()) {
                // Нашли скрипт с ChatListU
                int chatListEndPos = chatListMatcher.end();
                String roomListContainer = "<style id=\"_room_list_style\">"
                        + "#_room_list_container{display:block;line-height:1.4;}"
                        + "#_room_list_container .ab-room-row{display:block;margin:4px 0;}"
                        + "#_room_list_container .ab-room-row img{height:26px !important;width:auto !important;vertical-align:middle;}"
                        + "#_room_list_container .ab-room-row img.ab-room-injury-icon{height:26px !important;width:auto !important;vertical-align:middle;}"
                        + "#_room_list_container .ab-room-row .nickname{font-size:140% !important;line-height:1.1 !important;}"
                        + "#_room_list_container .ab-room-row a.activenick{font-size:140% !important;line-height:1.1 !important;}"
                        + "#_room_list_container .ab-room-row .activenick b{font-size:140% !important;line-height:1.1 !important;}"
                        + "#_room_list_container .ab-room-row .ab-room-level{font-size:200% !important;line-height:1.1 !important;}"
                        + "#_room_list_container .ab-room-effect-wrap{display:inline-flex;align-items:center;gap:2px;margin-left:2px;}"
                        + "#_room_list_container .ab-room-effect-meta{display:inline-block;line-height:1.05 !important;font-size:85% !important;vertical-align:middle;}"
                        + "</style>"
                        + "<div id=\"_room_list_container\">"
                        + filterResult.html
                        + "</div>";
                // ВАЖНО: ch.php?lo=1 содержит этот участок внутри <script>.
                // Если вставить сюда сырой HTML (<div ...>), JS ломается на "Unexpected token '<'".
                // Поэтому в script-контексте всегда вставляем через document.write('...').
                String scriptSafeRoomListWrite = "document.write('"
                        + escapeJsSingleQuoted(roomListContainer).replace("\r", "").replace("\n", "")
                        + "');";
                
                //找ищем вызов chatlist_build() после ChatListU
                Pattern buildPattern = Pattern.compile("chatlist_build\\s*\\([^)]*\\);");
                Matcher buildMatcher = buildPattern.matcher(html);
                buildMatcher.region(chatListEndPos, html.length());
                
                if (buildMatcher.find()) {
                    // Нашли вызов chatlist_build() в script-блоке.
                    // Заменяем вызов на script-safe вставку HTML через document.write.
                    String replacement = scriptSafeRoomListWrite;
                    html = html.substring(0, buildMatcher.start()) 
                        + replacement 
                        + html.substring(buildMatcher.end());
                    
                    AppLog.d(TAG, BG_TRACE_PREFIX + " injectPlayerListHtmlIntoChatPhp: injection success"
                            + ", chars=" + filterResult.numCharsInRoom
                            + ", htmlLen=" + html.length());
                    FileLogger.trace("roommanager", "[ROOM_INJECT] mode=replace_chatlist_build"
                            + ", chars=" + filterResult.numCharsInRoom
                            + ", htmlLen=" + html.length());
                } else {
                    // Вызов chatlist_build не найден.
                    // Предпочтительно вставлять уже ПОСЛЕ закрытия </script>, чтобы не ломать JS.
                    int scriptClosePos = html.indexOf("</script>", chatListEndPos);
                    if (scriptClosePos >= 0) {
                        int insertPos = scriptClosePos + "</script>".length();
                        html = html.substring(0, insertPos)
                                + roomListContainer
                                + html.substring(insertPos);
                        FileLogger.trace("roommanager", "[ROOM_INJECT] mode=insert_after_script_close"
                                + ", chars=" + filterResult.numCharsInRoom
                                + ", htmlLen=" + html.length());
                    } else {
                        // Резерв: закрывающий script не найден, значит остаёмся в script-контексте.
                        // Здесь допустима только JS-строка, а не сырой HTML.
                        html = html.substring(0, chatListEndPos)
                                + scriptSafeRoomListWrite
                                + html.substring(chatListEndPos);
                        FileLogger.trace("roommanager", "[ROOM_INJECT] mode=fallback_script_safe_write"
                                + ", chars=" + filterResult.numCharsInRoom
                                + ", htmlLen=" + html.length());
                    }
                    
                    AppLog.d(TAG, BG_TRACE_PREFIX + " injectPlayerListHtmlIntoChatPhp: no chatlist_build found, applied safe fallback injection");
                }
            } else {
                // ChatListU не найден - это необычно, логируем
                AppLog.w(TAG, BG_TRACE_PREFIX + " injectPlayerListHtmlIntoChatPhp: ChatListU pattern not found in html");
            }
        } catch (Exception e) {
            AppLog.e(TAG, BG_TRACE_PREFIX + " injectPlayerListHtmlIntoChatPhp: error during injection", e);
        }
        
        return html;
    }

    // Парсит JS-массив ChatListU и формирует HTML списка игроков.
    private static FilterProcRoomResult FilterProcRoom(String html) {
        FilterProcRoomResult result = new FilterProcRoomResult();

        Pattern pattern = Pattern.compile("var\\s+ChatListU\\s*=\\s*new Array\\((.*?)\\);", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String chatListU = matcher.group(1);
            String[] par = parseChatListEntries(chatListU);
            result.numCharsInRoom = par.length;

            StringBuilder sb = new StringBuilder();
            StringBuilder chatListUBuilder = new StringBuilder();
            List<String> enemyAttack = new ArrayList<>();
            Map<String, String> latestRoomEntries = new LinkedHashMap<>();
            for (int i = 0; i < par.length; i++) {
                String rawEntry = normalizeChatUserEntry(par[i]);
                if (rawEntry.isEmpty()) {
                    continue;
                }

                String nick = extractNick(rawEntry);
                if (!nick.isEmpty()) {
                    result.roomNicks.add(stripItalic(nick));
                    String nickKey = normalizeNickKey(nick);
                    if (!isEmpty(nickKey)) {
                        latestRoomEntries.put(nickKey, rawEntry);
                    }
                    int injuryTypeHint = parseRoomInjuryTypeHint(rawEntry);
                    if (injuryTypeHint > 0) {
                        Integer previousHint = result.injuryTypeHints.get(nickKey);
                        if (previousHint == null || injuryTypeHint > previousHint) {
                            result.injuryTypeHints.put(nickKey, injuryTypeHint);
                        }
                    }
                }
                if (!nick.isEmpty() && isEnemyContact(nick)) {
                    enemyAttack.add(nick);
                    AppLog.d(TAG, AA_TRACE_PREFIX + " enemy detected in room: " + buildEnemyTrace(nick));
                }

                try {
                    sb.append("<div class=\"ab-room-row\">");
                    sb.append(HtmlChar(rawEntry));
                    sb.append("</div>");
                } catch (Exception htmlCharError) {
                    AppLog.w(TAG, "FilterProcRoom: skip malformed ChatListU entry: " + rawEntry, htmlCharError);
                    continue;
                }
                chatListUBuilder.append("\"" + rawEntry + "\"");
                if (i < par.length - 1) {
                    chatListUBuilder.append(",");
                }
            }
            result.html = sb.toString();
            result.chatListU = chatListUBuilder.toString();
            result.enemyCandidates = enemyAttack;
            result.enemyAttack = pickEnemyForAutoAttack(enemyAttack);
            lastRoomChatEntryByNick.clear();
            lastRoomChatEntryByNick.putAll(latestRoomEntries);
            AppLog.d(TAG, "FilterProcRoom: chars=" + result.numCharsInRoom
                    + ", enemies=" + enemyAttack.size()
                    + ", enemyAttack=" + result.enemyAttack);
        }

        return result;
    }

    /**
     * Разбирает содержимое `new Array(...)` для `ChatListU`.
     *
     * Почему отдельный парсер:
     * - сервер может присылать переносы/пробелы между элементами (`",\r\n"`),
     * - простой split по `","` в таком случае может вернуть один элемент.
     *
     * Возвращает массив строк формата `nickLow:nick:level:...`.
     */
    private static String[] parseChatListEntries(String chatListU) {
        if (chatListU == null || chatListU.trim().isEmpty()) {
            return new String[0];
        }

        // Основной путь: split по разделителю элементов массива.
        String[] splitByComma = chatListU.split("\"\\s*,\\s*\"");
        if (splitByComma.length > 1) {
            return splitByComma;
        }
        AppLog.d(TAG, "[AA_TRACE] parseChatListEntries: splitByComma failed, fallback regex. rawLen=" + chatListU.length());

        // Резервный путь: извлекаем все элементы в двойных кавычках,
        // если сервер прислал нестандартные разделители/переносы.
        List<String> quoted = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(chatListU);
        while (matcher.find()) {
            quoted.add(matcher.group(1));
        }
        if (!quoted.isEmpty()) {
            AppLog.d(TAG, "[AA_TRACE] parseChatListEntries: fallback extracted=" + quoted.size());
            return quoted.toArray(new String[0]);
        }

        // Последний резерв: возвращаем исходную строку как один элемент,
        // чтобы внешняя логика могла безопасно обработать деградированный формат.
        AppLog.w(TAG, "[AA_TRACE] parseChatListEntries: no quoted entries, using raw source");
        return new String[]{chatListU};
    }

    /**
     * Добавляет ник в blacklist авто-нападения на короткое время.
     *
     * Аналог C# `RoomManager.CharAddToBlackList`.
     * Используется при ошибке "Нельзя вмешаться в закрытый бой", чтобы не спамить повторами.
     */
    public static void charAddToBlackList(String nick) {
        if (isEmpty(nick)) {
            return;
        }
        String key = normalizeNickKey(nick);
        autoAttackBlackList.put(key, System.currentTimeMillis());
        AppLog.d(TAG, AA_TRACE_PREFIX + " blacklist add: " + key + ", ttlMs=" + AUTO_ATTACK_BLACKLIST_MS
                + ", size=" + autoAttackBlackList.size());
    }

    private static boolean isCharInBlackList(String nick) {
        if (isEmpty(nick)) {
            return false;
        }
        long remainingMs = getBlackListRemainingMs(nick);
        if (remainingMs <= 0L) {
            return false;
        }
        String key = normalizeNickKey(nick);
        AppLog.d(TAG, AA_TRACE_PREFIX + " blacklist hit: " + key + ", remainingMs=" + remainingMs);
        return true;
    }

    private static boolean isAutoAttackEnabled(Context context) {
        try {
            boolean enabled = AutoFunctionsManager.getInstance(context).isAutoAttackEnabled();
            AppLog.d(TAG, BG_TRACE_PREFIX + " isAutoAttackEnabled: " + enabled);
            return enabled;
        } catch (Exception e) {
            AppLog.w(TAG, "isAutoAttackEnabled failed", e);
            return false;
        }
    }

    private static boolean isAutoCureEnabled(Context context) {
        try {
            return AutoFunctionsManager.getInstance(context).isAutoCureEnabled();
        } catch (Exception e) {
            AppLog.w(TAG, "isAutoCureEnabled failed", e);
            return false;
        }
    }

    private static AutoFunctionsManager getAutoFunctionsManagerSafe() {
        try {
            Context context = AppVars.getContext();
            if (context == null) {
                return null;
            }
            return AutoFunctionsManager.getInstance(context);
        } catch (Exception e) {
            AppLog.w(TAG, "getAutoFunctionsManagerSafe failed", e);
            return null;
        }
    }

    private static boolean isAutoCureTargetFriendsEnabled() {
        AutoFunctionsManager manager = getAutoFunctionsManagerSafe();
        return manager == null || manager.isAutoCureTargetFriendsEnabled();
    }

    private static boolean isAutoCureTargetNeutralsEnabled() {
        AutoFunctionsManager manager = getAutoFunctionsManagerSafe();
        return manager == null || manager.isAutoCureTargetNeutralsEnabled();
    }

    private static boolean isAutoCureWoundTypeEnabled(int woundType) {
        AutoFunctionsManager manager = getAutoFunctionsManagerSafe();
        return manager == null || manager.isAutoCureWoundTypeEnabled(woundType);
    }

    private static boolean isAutoCureSelfElixirEnabledForWound(int woundType) {
        AutoFunctionsManager manager = getAutoFunctionsManagerSafe();
        return manager != null && manager.isAutoCureSelfElixirEnabledForWound(woundType);
    }

    private static void maybeScheduleRoomAutoCure(Context context,
                                                  FilterProcRoomResult filterResult,
                                                  boolean fightActive) {
        if (context == null || filterResult == null || AppVars.Profile == null) {
            return;
        }
        if (!isAutoCureEnabled(context)) {
            return;
        }
        if (isEmpty(AppVars.Profile.UserNick) || AppVars.FastNeed || AppVars.CureNeed || fightActive) {
            return;
        }
        final List<String> roomNicksSnapshot = new ArrayList<>(filterResult.roomNicks);
        final Map<String, Integer> injuryHintsSnapshot = new LinkedHashMap<>(filterResult.injuryTypeHints);
        if (roomNicksSnapshot.isEmpty() && injuryHintsSnapshot.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean urgentRoomScan = hasUrgentRoomAutoCureCandidate(roomNicksSnapshot, injuryHintsSnapshot, now);
        long minScanIntervalMs = urgentRoomScan
                ? AUTO_CURE_ROOM_SCAN_PRIORITY_INTERVAL_MS
                : AUTO_CURE_ROOM_SCAN_INTERVAL_MS;
        if (autoCureRoomScanInProgress || (now - lastAutoCureRoomScanAtMs) < minScanIntervalMs) {
            return;
        }

        synchronized (RoomManager.class) {
            long nowSync = System.currentTimeMillis();
            if (autoCureRoomScanInProgress || (nowSync - lastAutoCureRoomScanAtMs) < minScanIntervalMs) {
                return;
            }
            autoCureRoomScanInProgress = true;
            lastAutoCureRoomScanAtMs = nowSync;
        }

        if (urgentRoomScan) {
            AppLog.d(TAG, AUTO_CURE_TRACE_PREFIX + " urgent room scan: hints=" + injuryHintsSnapshot.size()
                    + ", roomNicks=" + roomNicksSnapshot.size()
                    + ", minIntervalMs=" + minScanIntervalMs);
        }

        Thread worker = new Thread(() -> {
            try {
                AutoCureTarget target = selectRoomAutoCureTarget(roomNicksSnapshot, injuryHintsSnapshot);
                if (target != null) {
                    enqueueRoomAutoCureTarget(target);
                }
            } catch (Exception e) {
                AppLog.w(TAG, AUTO_CURE_TRACE_PREFIX + " room scan failed", e);
            } finally {
                autoCureRoomScanInProgress = false;
            }
        }, "RoomAutoCureScan");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean hasUrgentRoomAutoCureCandidate(List<String> roomNicks,
                                                           Map<String, Integer> injuryHints,
                                                           long nowMs) {
        String selfKey = normalizeNickKey(AppVars.Profile == null ? "" : AppVars.Profile.UserNick);
        if (injuryHints != null && !injuryHints.isEmpty()) {
            for (Map.Entry<String, Integer> entry : injuryHints.entrySet()) {
                if (entry == null) {
                    continue;
                }
                Integer hintWoundType = entry.getValue();
                if (hintWoundType == null || hintWoundType <= 0) {
                    continue;
                }
                String nickKey = normalizeNickKey(entry.getKey());
                if (isEmpty(nickKey)) {
                    continue;
                }
                if (!isEmpty(selfKey) && selfKey.equalsIgnoreCase(nickKey)) {
                    continue;
                }
                return true;
            }
        }

        if (roomNicks == null || roomNicks.isEmpty()) {
            return false;
        }
        for (String rawNick : roomNicks) {
            String cleanNick = stripItalic(rawNick);
            String nickKey = normalizeNickKey(cleanNick);
            if (isEmpty(nickKey)) {
                continue;
            }
            if (!isEmpty(selfKey) && selfKey.equalsIgnoreCase(nickKey)) {
                continue;
            }
            CachedRoomPinfoState cachedState = autoCureRoomPinfoCache.get(nickKey);
            if (cachedState == null) {
                continue;
            }
            if ((nowMs - cachedState.capturedAtMs) >= AUTO_CURE_ROOM_PINFO_CACHE_TTL_MS) {
                continue;
            }
            if (cachedState.woundType > 0) {
                return true;
            }
        }
        return false;
    }

    private static AutoCureTarget selectRoomAutoCureTarget(List<String> roomNicks,
                                                            Map<String, Integer> injuryHints) {
        boolean allowFriends = isAutoCureTargetFriendsEnabled();
        boolean allowNeutrals = isAutoCureTargetNeutralsEnabled();

        String selfNick = stripItalic(AppVars.Profile.UserNick);
        if (!isEmpty(selfNick)) {
            AutoCureTarget selfTarget = buildAutoCureTarget(
                    selfNick,
                    CONTACT_CLASS_NEUTRAL,
                    true,
                    injuryHints,
                    null
            );
            if (selfTarget != null) {
                return selfTarget;
            }
        }

        // Guard-контекст для friend/neutral ветки.
        // Зависимости:
        // - `buildAutoCureExternalGuardContext(selfNick)` -> читает clanToken себя через pinfo/cache;
        // - `guardContext.selfClanInCurrentWars` -> итог флага wars-check через `ClanWarsManager`;
        // - `guardContext.selfClanToken` -> диагностическое значение для логов/уведомлений.
        AutoCureExternalGuardContext guardContext = buildAutoCureExternalGuardContext(selfNick);
        if (guardContext.selfClanInCurrentWars) {
            AppLog.d(TAG, AUTO_CURE_TRACE_PREFIX + " skip external cures: self clan in current wars, token="
                    + guardContext.selfClanToken);
            maybeNotifyAutoCureSkipReason(
                    "external|self_clan_wars|" + guardContext.selfClanToken,
                    "Внешнее лечение временно приостановлено: наш клан участвует в текущей клановой войне."
            );
            return null;
        }

        Set<String> seen = new HashSet<>();
        if (!isEmpty(selfNick)) {
            seen.add(normalizeNickKey(selfNick));
        }
        List<String> friendNicks = new ArrayList<>();
        List<String> neutralNicks = new ArrayList<>();
        for (String nickRaw : roomNicks) {
            String nick = stripItalic(nickRaw);
            if (isEmpty(nick)) {
                continue;
            }
            String key = normalizeNickKey(nick);
            if (isEmpty(key) || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            int classId = resolveClassIdForNick(nick);
            if (classId == CONTACT_CLASS_ENEMY) {
                continue;
            }
            if (classId == CONTACT_CLASS_FRIEND) {
                friendNicks.add(nick);
            } else {
                neutralNicks.add(nick);
            }
        }

        if (allowFriends) {
            for (String friendNick : friendNicks) {
                AutoCureTarget target = buildAutoCureTarget(
                        friendNick,
                        CONTACT_CLASS_FRIEND,
                        false,
                        injuryHints,
                        guardContext
                );
                if (target != null) {
                    return target;
                }
            }
        }
        if (allowNeutrals) {
            for (String neutralNick : neutralNicks) {
                AutoCureTarget target = buildAutoCureTarget(
                        neutralNick,
                        CONTACT_CLASS_NEUTRAL,
                        false,
                        injuryHints,
                        guardContext
                );
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    private static AutoCureTarget buildAutoCureTarget(String nick,
                                                       int classId,
                                                       boolean self,
                                                       Map<String, Integer> injuryHints,
                                                       AutoCureExternalGuardContext guardContext) {
        String cleanNick = stripItalic(nick);
        if (isEmpty(cleanNick)) {
            return null;
        }
        int hintedType = 0;
        if (injuryHints != null) {
            Integer value = injuryHints.get(normalizeNickKey(cleanNick));
            hintedType = value == null ? 0 : value;
        }
        int woundType = resolveRoomWoundType(cleanNick, hintedType, self);
        if (woundType <= 0) {
            return null;
        }
        if (!isAutoCureWoundTypeEnabled(woundType)) {
            // Для self допускаем цель, если тип травмы включен именно в ветке self-эликсира.
            // Так self-лечение не зависит от чекбоксов аптечек по типам травм.
            if (!(self
                    && woundType >= 1
                    && woundType <= 3
                    && isAutoCureSelfElixirEnabledForWound(woundType))) {
                return null;
            }
        }

        if (!self) {
            // Для friend/neutral цели выполняем доп. ограничения (паритет с Auto-Boss guard):
            // 1) цель в бою (`guardState.fightFid`) -> лечение откладываем;
            // 2) клан цели в текущих войнах (`guardState.clanToken`) -> лечение пропускаем.
            // Зависимости:
            // - `resolveAutoCureGuardState(cleanNick)` -> pinfo snapshot + короткий cache;
            // - `isClanTokenInCurrentWarsSafe(...)` -> проверка по `ClanWarsManager`.
            AutoCureGuardState guardState = resolveAutoCureGuardState(cleanNick);
            if (guardState == null) {
                AppLog.d(TAG, AUTO_CURE_TRACE_PREFIX + " skip external target: guard snapshot unavailable, nick="
                        + cleanNick + ", woundType=" + woundType);
                return null;
            }

            if (!isEmpty(guardState.fightFid)) {
                AppLog.d(TAG, AUTO_CURE_TRACE_PREFIX + " postpone external target: nick=" + cleanNick
                        + ", reason=target_in_fight"
                        + ", fightFid=" + guardState.fightFid
                        + ", woundType=" + woundType
                        + ", recheck=next_room_update");
                maybeNotifyAutoCureSkipReason(
                        "target|fight|" + normalizeNickKey(cleanNick),
                        "Цель <b>" + escapeHtml(cleanNick) + "</b> сейчас в бою (fid="
                                + escapeHtml(guardState.fightFid)
                                + "), лечение отложено до следующего обновления комнаты."
                );
                return null;
            }

            if (!isEmpty(guardState.clanToken) && isClanTokenInCurrentWarsSafe(guardState.clanToken)) {
                AppLog.d(TAG, AUTO_CURE_TRACE_PREFIX + " skip external target by current wars: nick=" + cleanNick
                        + ", targetClan=" + guardState.clanToken
                        + ", selfClan=" + (guardContext == null ? "" : guardContext.selfClanToken)
                        + ", woundType=" + woundType);
                maybeNotifyAutoCureSkipReason(
                        "target|wars|" + normalizeNickKey(cleanNick) + "|" + guardState.clanToken,
                        "Цель <b>" + escapeHtml(cleanNick) + "</b> состоит в клане, участвующем в текущей клановой войне. Лечение пропущено."
                );
                return null;
            }
        }

        return new AutoCureTarget(cleanNick, woundType, classId, self);
    }

    private static AutoCureExternalGuardContext buildAutoCureExternalGuardContext(String selfNick) {
        // selfNick -> guardState(clanToken/fightFid) -> selfClanToken -> selfClanInCurrentWars
        // Важно: для self используем только clanToken (fightFid себя не блокирует self-лечение,
        // но блокирует внешнее лечение friend/neutral выше по вызову).
        String selfClanToken = "";
        if (!isEmpty(selfNick)) {
            AutoCureGuardState selfGuardState = resolveAutoCureGuardState(selfNick);
            selfClanToken = normalizeClanToken(selfGuardState == null ? "" : selfGuardState.clanToken);
        }

        long now = System.currentTimeMillis();
        if (!isEmpty(selfClanToken)) {
            cachedAutoCureSelfClanToken = selfClanToken;
            cachedAutoCureSelfClanTokenAtMs = now;
        } else {
            String cachedToken = normalizeClanToken(cachedAutoCureSelfClanToken);
            if (!isEmpty(cachedToken)
                    && (now - cachedAutoCureSelfClanTokenAtMs) < AUTO_CURE_SELF_CLAN_TOKEN_CACHE_TTL_MS) {
                selfClanToken = cachedToken;
            }
        }

        boolean selfClanInCurrentWars = !isEmpty(selfClanToken)
                && isClanTokenInCurrentWarsSafe(selfClanToken);
        return new AutoCureExternalGuardContext(selfClanToken, selfClanInCurrentWars);
    }

    private static AutoCureGuardState resolveAutoCureGuardState(String nick) {
        // Короткий кэш guard-снимка нужен для двух зависимых проверок:
        // - `target_in_fight`: поле `AutoCureGuardState.fightFid`;
        // - `current_wars`: поле `AutoCureGuardState.clanToken`.
        // Переменные: `autoCureRoomGuardCache`, `AUTO_CURE_ROOM_GUARD_CACHE_TTL_MS`, `capturedAtMs`.
        String key = normalizeNickKey(nick);
        if (isEmpty(key)) {
            return null;
        }

        long now = System.currentTimeMillis();
        AutoCureGuardState cached = autoCureRoomGuardCache.get(key);
        if (cached != null && (now - cached.capturedAtMs) < AUTO_CURE_ROOM_GUARD_CACHE_TTL_MS) {
            return cached;
        }

        AutoCureGuardState fresh = fetchAutoCureGuardStateFromPinfo(nick);
        if (fresh != null) {
            autoCureRoomGuardCache.put(key, fresh);
            return fresh;
        }

        return cached;
    }

    private static AutoCureGuardState fetchAutoCureGuardStateFromPinfo(String nick) {
        // Источник истины для guard-полей цели: `NeverApi.PinfoCompassSnapshot`.
        // Зависимости:
        // - `snapshot.fightFid` -> `normalizeFightFid(...)`;
        // - `snapshot.clanToken` -> `normalizeClanToken(...)`;
        // - fallback к `Contact.clanIco` (через `resolveContactClanTokenFallback`) при пустом pinfo clanToken.
        String cleanNick = stripItalic(nick);
        if (isEmpty(cleanNick)) {
            return null;
        }
        try {
            AppLog.d(TAG, "INFO_API_TRACE stage=info_api_runtime_call, source_module=auto_cure_room_guard, nick="
                    + cleanNick);
            NeverApi.PinfoCompassSnapshot snapshot = NeverApi.getPinfoCompassSnapshotFromInfoApi(
                    cleanNick,
                    "auto_cure_room_guard"
            );
            if (snapshot == null) {
                return null;
            }
            String clanToken = normalizeClanToken(snapshot.clanToken);
            if (isEmpty(clanToken)) {
                clanToken = resolveContactClanTokenFallback(cleanNick);
            }
            String fightFid = normalizeFightFid(snapshot.fightFid);
            return new AutoCureGuardState(clanToken, fightFid, System.currentTimeMillis());
        } catch (Exception e) {
            AppLog.w(TAG, AUTO_CURE_TRACE_PREFIX + " guard snapshot read failed for " + cleanNick, e);
            return null;
        }
    }

    private static boolean isClanTokenInCurrentWarsSafe(String clanToken) {
        String token = normalizeClanToken(clanToken);
        if (isEmpty(token)) {
            return false;
        }
        try {
            Context context = AppVars.getContext();
            if (context == null) {
                return false;
            }
            return ClanWarsManager.getInstance(context).isClanTokenInCurrentWars(token);
        } catch (Exception e) {
            AppLog.w(TAG, AUTO_CURE_TRACE_PREFIX + " wars-check failed: token=" + token, e);
            return false;
        }
    }

    private static String resolveContactClanTokenFallback(String nick) {
        Contact contact = findContactByNickIgnoreCase(nick);
        if (contact == null) {
            return "";
        }
        return normalizeClanToken(contact.clanIco);
    }

    /**
     * Публикует локальное уведомление авто-лечения с дедупликацией по ключу.
     *
     * Зависимости:
     * - `MainPhp.buildServerChatTimeHtmlExternal()` — серверный timestamp в сообщении;
     * - `FastActionManager.writeChatMsg(...)` — единый канал локального чата;
     * - `autoCureSkipNoticeAtMs` + `AUTO_CURE_SKIP_NOTICE_DEDUP_MS` — анти-спам окно.
     *
     * Переменные:
     * - `dedupKey` = scope/reason/nick-or-token;
     * - `lastAt` / `now` — контроль минимального интервала повторной отправки.
     */
    private static void maybeNotifyAutoCureSkipReason(String dedupKey, String reasonHtml) {
        String key = safeTrim(dedupKey);
        String reason = safeTrim(reasonHtml);
        if (isEmpty(key) || isEmpty(reason)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastAt = autoCureSkipNoticeAtMs.get(key);
        if (lastAt != null && (now - lastAt) < AUTO_CURE_SKIP_NOTICE_DEDUP_MS) {
            AppLog.d(TAG, AUTO_CURE_TRACE_PREFIX + " skip-notice dedup: key=" + key
                    + ", remainingMs=" + (AUTO_CURE_SKIP_NOTICE_DEDUP_MS - (now - lastAt)));
            return;
        }
        autoCureSkipNoticeAtMs.put(key, now);

        String notice = MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#5D7C91><b>[Автолечение][RoomManager]</b></font> "
                + reason;
        FastActionManager.writeChatMsg(notice);
        AppLog.d(TAG, AUTO_CURE_TRACE_PREFIX + " skip-notice posted: key=" + key);
    }

    private static int resolveRoomWoundType(String nick, int hintedType, boolean self) {
        if (hintedType == 4) {
            return 4;
        }
        int woundType = resolveWoundTypeFromPinfoCached(nick);
        if (woundType <= 0 && self) {
            woundType = resolveWoundTypeFromSelfSnapshot();
        }
        if (woundType <= 0 && hintedType >= 1 && hintedType <= 3) {
            woundType = hintedType;
        }
        return woundType;
    }

    private static int resolveWoundTypeFromPinfoCached(String nick) {
        String key = normalizeNickKey(nick);
        if (isEmpty(key)) {
            return 0;
        }
        long now = System.currentTimeMillis();
        CachedRoomPinfoState cached = autoCureRoomPinfoCache.get(key);
        if (cached != null && (now - cached.capturedAtMs) < AUTO_CURE_ROOM_PINFO_CACHE_TTL_MS) {
            return cached.woundType;
        }

        return fetchWoundTypeFromPinfo(nick);
    }

    private static int fetchWoundTypeFromPinfo(String nick) {
        int woundType = 0;
        List<Integer> effectIds = Collections.emptyList();
        List<ContactRenderHelper.EffectState> effectStates = Collections.emptyList();
        try {
            AppLog.d(TAG, "INFO_API_TRACE stage=info_api_runtime_call, source_module=auto_cure_room, nick=" + nick);
            NeverApi.PinfoVitals vitals = NeverApi.getPinfoVitalsFromInfoApi(nick, "auto_cure_room");
            if (vitals != null) {
                effectIds = normalizeEffectIds(vitals.effectIds);
                effectStates = normalizeEffectStates(
                        ContactRenderHelper.parseEffectStatesCsv(
                                vitals.effectStatesCsv,
                                ContactRenderHelper.toEffectIdsCsv(effectIds))
                );
                // Важно для авто-лечения себя:
                // RoomManager регулярно читает pinfo в фоне, но до этого не синхронизировал
                // общий runtime-снимок vitals. Из-за этого AutoCure мог не видеть новые травмы
                // до ручного toggle "Автолечение" (который делал отдельный sync).
                if (isSelfNick(nick)) {
                    CharacterVitalsManager.updateFromPinfo(
                            vitals,
                            "RoomManager.fetchWoundTypeFromPinfo(self)");
                }
                if (vitals.topWoundType != null && vitals.topWoundType > 0) {
                    // `var eff` contains full wound type, including battle wound.
                    woundType = vitals.topWoundType;
                } else {
                    // Fallback for backward compatibility with poison+non-battle counters.
                    woundType = resolveWoundTypeFromPoisonAndWounds(vitals.poisonAndWounds);
                }
            }
        } catch (Exception e) {
            AppLog.w(TAG, AUTO_CURE_TRACE_PREFIX + " pinfo read failed for " + nick, e);
        }
        cacheRoomPinfoState(nick, woundType, effectIds, effectStates, System.currentTimeMillis());
        return woundType;
    }

    /**
     * Вызывается после submit лечения (`doctorform`) для целевого персонажа.
     *
     * Делает две вещи:
     * 1) сразу сбрасывает pinfo-cache цели в "без травмы", чтобы не было мгновенного ре-queue;
     * 2) через короткую задержку перечитывает `pinfo` и обновляет cache фактическим состоянием.
     */
    public static void onAutoCureSubmitted(String nick, String cureTravm) {
        String cleanNick = stripItalic(nick);
        String key = normalizeNickKey(cleanNick);
        if (isEmpty(key)) {
            return;
        }

        long now = System.currentTimeMillis();
        autoCureRoomPinfoCache.put(key, new CachedRoomPinfoState(0, Collections.emptyList(), Collections.emptyList(), now));
        AppLog.d(TAG, AUTO_CURE_TRACE_PREFIX + " post-submit verify scheduled: nick=" + cleanNick
                + ", travm=" + cureTravm);

        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(AUTO_CURE_POST_SUBMIT_VERIFY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            int actualWoundType = fetchWoundTypeFromPinfo(cleanNick);
            AppLog.d(TAG, AUTO_CURE_TRACE_PREFIX + " post-submit verify result: nick=" + cleanNick
                    + ", woundType=" + actualWoundType + ", travm=" + cureTravm);
        }, "RoomAutoCureVerify");
        worker.setDaemon(true);
        worker.start();
    }

    public static void setShowAllRoomEffectsEnabled(boolean enabled) {
        showAllRoomEffectsEnabled = enabled;
        AppLog.d("roommanager", TAG, "[ROOM_EFFECTS] setShowAllRoomEffectsEnabled=" + enabled);
    }

    private static boolean isShowAllRoomEffectsEnabled() {
        if (showAllRoomEffectsEnabled != null) {
            return showAllRoomEffectsEnabled;
        }
        try {
            Context context = AppVars.getContext();
            if (context == null) {
                showAllRoomEffectsEnabled = true;
                return true;
            }
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            boolean enabled = preferences.getBoolean(PREF_SHOW_ALL_ROOM_EFFECTS, true);
            showAllRoomEffectsEnabled = enabled;
            return enabled;
        } catch (Exception e) {
            AppLog.w(TAG, "[ROOM_EFFECTS] read preference failed, fallback=true", e);
            showAllRoomEffectsEnabled = true;
            return true;
        }
    }

    private static void cacheRoomPinfoState(String nick,
                                            int woundType,
                                            List<Integer> effectIds,
                                            List<ContactRenderHelper.EffectState> effectStates,
                                            long capturedAtMs) {
        String key = normalizeNickKey(nick);
        if (isEmpty(key)) {
            return;
        }
        autoCureRoomPinfoCache.put(
                key,
                new CachedRoomPinfoState(
                        woundType,
                        normalizeEffectIds(effectIds),
                        normalizeEffectStates(effectStates),
                        capturedAtMs));
    }

    private static List<Integer> getCachedEffectIdsForNick(String nick) {
        String key = normalizeNickKey(nick);
        if (isEmpty(key)) {
            return Collections.emptyList();
        }
        CachedRoomPinfoState cached = autoCureRoomPinfoCache.get(key);
        if (cached == null) {
            return Collections.emptyList();
        }
        long ageMs = System.currentTimeMillis() - cached.capturedAtMs;
        if (ageMs >= AUTO_CURE_ROOM_PINFO_CACHE_TTL_MS) {
            return Collections.emptyList();
        }
        return cached.effectIds == null ? Collections.emptyList() : cached.effectIds;
    }

    private static List<ContactRenderHelper.EffectState> getCachedEffectStatesForNick(String nick) {
        String key = normalizeNickKey(nick);
        if (isEmpty(key)) {
            return Collections.emptyList();
        }
        CachedRoomPinfoState cached = autoCureRoomPinfoCache.get(key);
        if (cached == null) {
            return Collections.emptyList();
        }
        long ageMs = System.currentTimeMillis() - cached.capturedAtMs;
        if (ageMs >= AUTO_CURE_ROOM_PINFO_CACHE_TTL_MS) {
            return Collections.emptyList();
        }
        return cached.effectStates == null ? Collections.emptyList() : cached.effectStates;
    }

    private static List<Integer> normalizeEffectIds(List<Integer> effectIds) {
        if (effectIds == null || effectIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (Integer effectId : effectIds) {
            if (effectId == null || effectId <= 0) {
                continue;
            }
            result.add(effectId);
        }
        if (result.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    private static List<ContactRenderHelper.EffectState> normalizeEffectStates(List<ContactRenderHelper.EffectState> effectStates) {
        if (effectStates == null || effectStates.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<Integer, ContactRenderHelper.EffectState> byId = new LinkedHashMap<>();
        for (ContactRenderHelper.EffectState state : effectStates) {
            if (state == null || state.id <= 0) {
                continue;
            }
            ContactRenderHelper.EffectState existing = byId.get(state.id);
            if (existing == null) {
                byId.put(state.id, new ContactRenderHelper.EffectState(state.id, state.count, state.timeout));
            } else {
                int mergedCount = Math.max(1, existing.count) + Math.max(1, state.count);
                String mergedTimeout = isEmpty(existing.timeout) ? state.timeout : existing.timeout;
                byId.put(state.id, new ContactRenderHelper.EffectState(state.id, mergedCount, mergedTimeout));
            }
        }
        if (byId.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    private static Integer resolveInjuryEffectIdFromEffects(List<Integer> effectIds) {
        if (effectIds == null || effectIds.isEmpty()) {
            return null;
        }
        if (effectIds.contains(1)) return 1; // боевая травма
        if (effectIds.contains(2)) return 2; // тяжелая травма
        if (effectIds.contains(3)) return 3; // средняя травма
        if (effectIds.contains(4)) return 4; // легкая травма
        return null;
    }

    private static String buildSingleEffectIconHtml(ContactRenderHelper.EffectState state, String altText) {
        if (state == null || state.id <= 0) {
            return "";
        }
        String safeAlt = escapeHtml(altText == null ? "" : altText);
        return "<span class=\"ab-room-effect-wrap\">"
                + "<img class=\"ab-room-injury-icon\" src=http://image.neverlands.ru/pinfo/eff_" + state.id
                + ".gif border=0 width=15 height=12 alt=\""
                + safeAlt
                + "\" align=absmiddle>"
                + "<span class=\"ab-room-effect-meta\" style=\"display:inline-block;line-height:1.05;vertical-align:middle;\">"
                + ContactRenderHelper.formatEffectCounterHtml(state)
                + "</span></span>";
    }

    private static String buildEffectIconsHtml(List<ContactRenderHelper.EffectState> effectStates, String injuryText) {
        if (effectStates == null || effectStates.isEmpty()) {
            return "";
        }
        String safeInjuryText = escapeHtml(injuryText == null ? "" : injuryText);
        StringBuilder sb = new StringBuilder();
        for (ContactRenderHelper.EffectState state : effectStates) {
            if (state == null || state.id <= 0) {
                continue;
            }
            int effectId = state.id;
            String alt = safeInjuryText.isEmpty()
                    ? ("эффект #" + effectId)
                    : safeInjuryText;
            sb.append("<span class=\"ab-room-effect-wrap\">")
                    .append("<img class=\"ab-room-injury-icon\" src=http://image.neverlands.ru/pinfo/eff_")
                    .append(state.id)
                    .append(".gif border=0 width=15 height=12 alt=\"")
                    .append(alt)
                    .append("\" align=absmiddle>")
                    .append("<span class=\"ab-room-effect-meta\" style=\"display:inline-block;line-height:1.05;vertical-align:middle;\">")
                    .append(ContactRenderHelper.formatEffectCounterHtml(state))
                    .append("</span></span>");
        }
        return sb.toString();
    }

    private static ContactRenderHelper.EffectState findEffectStateById(List<ContactRenderHelper.EffectState> effectStates, int effectId) {
        if (effectStates == null || effectStates.isEmpty() || effectId <= 0) {
            return null;
        }
        for (ContactRenderHelper.EffectState state : effectStates) {
            if (state != null && state.id == effectId) {
                return state;
            }
        }
        return null;
    }

    private static int resolveWoundTypeFromSelfSnapshot() {
        CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.snapshot();
        if (snapshot.lightWoundCount > 0) {
            return 1;
        }
        if (snapshot.mediumWoundCount > 0) {
            return 2;
        }
        if (snapshot.heavyWoundCount > 0) {
            return 3;
        }
        return 0;
    }

    private static int resolveWoundTypeFromPoisonAndWounds(int[] poisonAndWounds) {
        if (poisonAndWounds == null || poisonAndWounds.length < 4) {
            return 0;
        }
        if (poisonAndWounds[1] > 0) {
            return 1;
        }
        if (poisonAndWounds[2] > 0) {
            return 2;
        }
        if (poisonAndWounds[3] > 0) {
            return 3;
        }
        return 0;
    }

    private static int resolveClassIdForNick(String nick) {
        int classId = parseClassIdSafe(ContactsManager.getClassIdOfContact(nick));
        if (classId != CONTACT_CLASS_NEUTRAL) {
            return classId;
        }
        String cleanNick = stripItalic(nick);
        List<Contact> contacts = ContactsManager.getContactsFromCache();
        for (Contact contact : contacts) {
            if (contact == null || isEmpty(contact.nick)) {
                continue;
            }
            if (contact.nick.equalsIgnoreCase(cleanNick)) {
                return contact.classId;
            }
        }
        return CONTACT_CLASS_NEUTRAL;
    }

    private static int parseClassIdSafe(String value) {
        if (isEmpty(value)) {
            return CONTACT_CLASS_NEUTRAL;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return CONTACT_CLASS_NEUTRAL;
        }
    }

    private static void enqueueRoomAutoCureTarget(AutoCureTarget target) {
        if (target == null || isEmpty(target.nick)) {
            return;
        }
        if (AppVars.CureNeed || AppVars.FastNeed) {
            return;
        }
        AppVars.CureNeed = true;
        AppVars.CureNick = target.nick;
        AppVars.CureTravm = String.valueOf(target.woundType);
        AppVars.CureNickDone = "";
        AppVars.CureNickBoi = "";
        if (AppVars.DoSearchBox || AppVars.AutoMoving) {
            AppVars.CurePauseNonCombatAutoFunctions = true;
        }

        String safeNick = target.nick.replace("<", "&lt;").replace(">", "&gt;");
        String safeTarget = target.self ? "себя" : ("<b>" + safeNick + "</b>");
        String scope = target.self ? "self" : (target.classId == CONTACT_CLASS_FRIEND ? "friend" : "neutral");
        String autoCureQueueMessage = MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#5D7C91><b>[\u0410\u0432\u0442\u043e\u043b\u0435\u0447\u0435\u043d\u0438\u0435]</b></font> "
                + "\u041d\u0430\u0439\u0434\u0435\u043d\u0430 " + getWoundLabelByType(target.woundType) + " \u0442\u0440\u0430\u0432\u043c\u0430 \u0443 " + safeTarget
                + ", \u0437\u0430\u043f\u0443\u0441\u043a\u0430\u0435\u043c \u043b\u0435\u0447\u0435\u043d\u0438\u0435...";
        FastActionManager.writeChatMsg(autoCureQueueMessage);
        AppLog.d(TAG, AUTO_CURE_TRACE_PREFIX + " queued target: nick=" + target.nick
                + ", woundType=" + target.woundType
                + ", class=" + target.classId
                + ", scope=" + scope
                + ", pauseNonCombat=" + AppVars.CurePauseNonCombatAutoFunctions);

        MainActivity activity = AppVars.mainActivity == null ? null : AppVars.mainActivity.get();
        if (activity != null) {
            activity.requestMainFrameReloadFromAutomation("room-auto-cure:" + scope + ":" + target.nick);
        }
    }

    private static String getWoundLabelByType(int woundType) {
        switch (woundType) {
            case 1:
                return "легкая";
            case 2:
                return "средняя";
            case 3:
                return "тяжелая";
            case 4:
                return "боевая";
            default:
                return "неизвестная";
        }
    }

    private static String pickEnemyForAutoAttack(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        List<String> filtered = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        for (String nick : candidates) {
            long remainingMs = getBlackListRemainingMs(nick);
            if (remainingMs <= 0L) {
                filtered.add(nick);
            } else {
                blocked.add(stripItalic(nick) + "(" + remainingMs + "ms)");
            }
        }
        AppLog.d(TAG, AA_TRACE_PREFIX + " pickEnemyForAutoAttack: total=" + candidates.size()
                + ", available=" + filtered.size()
                + ", blocked=" + blocked.size()
                + ", blockedList=" + blocked);

        if (filtered.isEmpty() && !blocked.isEmpty()) {
            AppLog.d(TAG, AA_TRACE_PREFIX + " pickEnemyForAutoAttack: all candidates are blacklisted,"
                    + " fallback to full list for compatibility");
        }

        List<String> source = filtered.isEmpty() ? candidates : filtered;
        String selected = source.get(ThreadLocalRandom.current().nextInt(source.size()));
        AppLog.d(TAG, AA_TRACE_PREFIX + " pickEnemyForAutoAttack: total=" + candidates.size()
                + ", filtered=" + filtered.size() + ", selected=" + selected);
        return selected;
    }

    private static boolean isEnemyContact(String nick) {
        try {
            return Integer.parseInt(ContactsManager.getClassIdOfContact(nick)) == CONTACT_CLASS_ENEMY;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String extractNick(String chatListEntry) {
        if (isEmpty(chatListEntry)) {
            return "";
        }
        String[] parts = chatListEntry.split(":");
        if (parts.length < 2) {
            return "";
        }
        return stripItalic(parts[1]);
    }

    private static int parseRoomInjuryTypeHint(String chatListEntry) {
        if (isEmpty(chatListEntry)) {
            return 0;
        }
        String[] parts = chatListEntry.split(":");
        if (parts.length <= 6) {
            return 0;
        }
        String injuryText = parts[6];
        if (isEmpty(injuryText) || "0".equals(injuryText)) {
            return 0;
        }
        String lower = injuryText.toLowerCase(Locale.ROOT);
        if (lower.contains("боевая")) {
            return 4;
        }
        if (lower.contains("тяж")) {
            return 3;
        }
        if (lower.contains("сред")) {
            return 2;
        }
        if (lower.contains("лег")) {
            return 1;
        }
        return 0;
    }

    private static String normalizeChatUserEntry(String rawEntry) {
        if (rawEntry == null) {
            return "";
        }
        String normalized = rawEntry.trim();
        if (normalized.startsWith("\"")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("\"")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        // Разэкраниваем JS-последовательности, если запись получена из fallback-парсера.
        normalized = normalized.replace("\\\"", "\"").replace("\\\\", "\\");
        return normalized.trim();
    }

    private static String encodeNickForPinfoUrl(String nick) {
        String cleanNick = stripItalic(nick);
        if (isEmpty(cleanNick)) {
            return "";
        }
        String encoded = ConverterUtils.nickEncode(cleanNick);
        if (isEmpty(encoded)) {
            return cleanNick.replace("+", "%2B");
        }
        return encoded.replace("+", "%20");
    }

    private static Contact findContactByNickIgnoreCase(String nick) {
        if (isEmpty(nick)) {
            return null;
        }
        List<Contact> contacts = ContactsManager.getContactsFromCache();
        for (Contact contact : contacts) {
            if (contact == null || isEmpty(contact.nick)) {
                continue;
            }
            if (contact.nick.equalsIgnoreCase(nick)) {
                return contact;
            }
        }
        return null;
    }

    private static String buildContactInclinationHtml(Contact contact) {
        if (contact == null || contact.inclination <= 0) {
            return "";
        }
        String inclinationName = isEmpty(contact.inclinationName) ? "" : contact.inclinationName;
        return buildInclinationIconHtmlByValue(contact.inclination, inclinationName);
    }

    private static String buildInclinationIconHtmlByValue(int inclination, String inclinationName) {
        String iconName;
        switch (inclination) {
            case 4:
                iconName = "chaoss.gif";
                break;
            case 3:
                iconName = "sumers.gif";
                break;
            case 2:
                iconName = "lights.gif";
                break;
            case 1:
                iconName = "darks.gif";
                break;
            default:
                return "";
        }
        String alt = isEmpty(inclinationName) ? "" : escapeHtmlAttribute(inclinationName);
        return "<img src=http://image.neverlands.ru/signs/" + iconName
                + " width=15 height=12 align=absmiddle border=0 alt=\"" + alt + "\">&nbsp;";
    }

    private static String buildContactClanIconHtml(Contact contact) {
        if (contact == null) {
            return "";
        }
        String token = contact.clanIco;
        String clanName = contact.clanName;
        return buildClanIconHtmlByToken(token, clanName);
    }

    private static String buildClanIconHtmlByToken(String clanToken, String clanName) {
        if (clanToken == null) {
            return "";
        }
        String token = clanToken.trim();
        if (isEmpty(token)) {
            return "";
        }
        String alt = isEmpty(clanName) ? "" : escapeHtmlAttribute(clanName);
        return "<img src=http://image.neverlands.ru/signs/" + token
                + " width=15 height=12 align=absmiddle alt=\"" + alt + "\">&nbsp;";
    }

    private static String escapeHtmlAttribute(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String stripItalic(String nick) {
        if (nick == null) {
            return "";
        }
        return nick.replace("<i>", "").replace("</i>", "").trim();
    }

    private static String normalizeClanToken(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeFightFid(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.isEmpty() || "0".equals(text) || "null".equalsIgnoreCase(text)) {
            return "";
        }
        Matcher digits = FIGHT_FID_PATTERN.matcher(text);
        return digits.find() ? digits.group(1) : text;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeNickKey(String nick) {
        return stripItalic(nick).toLowerCase(Locale.ROOT);
    }

    private static long getBlackListRemainingMs(String nick) {
        if (isEmpty(nick)) {
            return 0L;
        }
        String key = normalizeNickKey(nick);
        Long insertedAt = autoAttackBlackList.get(key);
        if (insertedAt == null) {
            return 0L;
        }
        long ageMs = System.currentTimeMillis() - insertedAt;
        long remainingMs = AUTO_ATTACK_BLACKLIST_MS - ageMs;
        if (remainingMs <= 0L) {
            autoAttackBlackList.remove(key);
            AppLog.d(TAG, AA_TRACE_PREFIX + " blacklist expire: " + key + ", ageMs=" + ageMs);
            return 0L;
        }
        return remainingMs;
    }

    private static String buildEnemyTrace(String nick) {
        String cleanNick = stripItalic(nick);
        String classId = ContactsManager.getClassIdOfContact(cleanNick);
        int contactTool = ContactsManager.getToolIdOfContact(cleanNick);
        long remainingMs = getBlackListRemainingMs(cleanNick);
        return cleanNick + "{classId=" + classId
                + ", contactTool=" + contactTool
                + ", blacklisted=" + (remainingMs > 0L)
                + ", blacklistRemainingMs=" + remainingMs + "}";
    }

    private static String buildEnemyCandidatesTrace(List<String> enemies) {
        if (enemies == null || enemies.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < enemies.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(buildEnemyTrace(enemies.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static void rememberStableRoomRenderHtml(String html, int numCharsInRoom) {
        if (isEmpty(html) || numCharsInRoom <= 0 || !html.contains("_room_list_container")) {
            return;
        }
        lastStableRoomRenderHtml = html;
        lastStableRoomRenderAtMs = System.currentTimeMillis();
    }

    private static String getCachedRoomRenderHtml() {
        if (isEmpty(lastStableRoomRenderHtml) || lastStableRoomRenderAtMs <= 0L) {
            return "";
        }
        long ageMs = System.currentTimeMillis() - lastStableRoomRenderAtMs;
        if (ageMs > ROOM_RENDER_CACHE_TTL_MS) {
            return "";
        }
        return lastStableRoomRenderHtml;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class MenuItem {
        public String title;
    }

    // Вспомогательный контейнер результата парсинга списка комнаты.
    private static class FilterProcRoomResult {
        int numCharsInRoom;
        String enemyAttack;
        String html;
        String chatListU;
        List<String> enemyCandidates = new ArrayList<>();
        List<String> roomNicks = new ArrayList<>();
        Map<String, Integer> injuryTypeHints = new LinkedHashMap<>();
    }
}
