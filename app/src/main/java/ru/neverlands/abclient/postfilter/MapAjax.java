package ru.neverlands.abclient.postfilter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.manager.CharacterVitalsManager;
import ru.neverlands.abclient.manager.FastActionManager;
import ru.neverlands.abclient.manager.NeverApi;
import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.model.Position;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.utils.MapPath;

public class MapAjax {
    private static final String TAG = "MapAjax";
    private static final long SEARCH_BOX_VISITED_TTL_MS = 24L * 60L * 60L * 1000L;
    // Параметры режима "Тщательная проверка соседних клеток" в Auto-Кладе.
    // Используются при detour-поиске рядом с текущей позицией.
    private static final int SEARCH_BOX_THOROUGH_MANHATTAN_RADIUS_MIN = 1;
    private static final int SEARCH_BOX_THOROUGH_MANHATTAN_RADIUS_MAX = 3;
    private static final int SEARCH_BOX_THOROUGH_MANHATTAN_RADIUS_DEFAULT = 3;
    private static final int SEARCH_BOX_THOROUGH_MAX_STEPS = 5;
    private static final long SEARCH_BOX_THOROUGH_MAX_NEWER_DELTA_MS = 12L * 60L * 60L * 1000L;
    private static final int SEARCH_BOX_THOROUGH_MIN_AGE_MINUTES_MIN = 1;
    private static final int SEARCH_BOX_THOROUGH_MIN_AGE_MINUTES_MAX = 24 * 60;
    private static final int SEARCH_BOX_THOROUGH_MIN_AGE_MINUTES_DEFAULT = 30;
    private static final long SEARCH_BOX_SMART_RECHECK_MIN_AGE_MS = 170L * 60L * 1000L; // 2ч + 50м
    private static final int AUTO_MOVING_TIED_STEP_COST = 2;
    private static final int AUTO_DRINK_BLAZ_NEAR_THRESHOLD_DELTA = 6;
    private static final int MAP_CELL_CHECK_TIMEOUT_MIN_MS = 0;
    private static final int MAP_CELL_CHECK_TIMEOUT_MAX_MS = 5000;
    private static final long AUTO_DRINK_BLAZ_PINFO_SYNC_COOLDOWN_MS = 20_000L;
    private static final long AUTO_DRINK_BLAZ_STARTUP_SYNC_RETRY_COOLDOWN_MS = 10_000L;
    private static final long AUTO_DRINK_BLAZ_TRIGGER_COOLDOWN_MS = 6_000L;
    private static final long AUTO_DRINK_BLAZ_POST_TRIGGER_STICKY_MS = 8_000L;
    private static final long MAP_AJAX_ERR_RESET_WINDOW_MS = 3_000L;
    private static final int MAP_AJAX_SOFT_RETRY_LIMIT = 1;
    private static final long MAP_AJAX_SOFT_RETRY_DELAY_MS = 350L;
    // Ограничители сообщений "перестраиваю обход" в чат, чтобы не заспамить пользователя
    // при быстрых перепланировках маршрута.
    private static final long AUTO_TREASURE_ROUTE_CHAT_COOLDOWN_MS = 2_000L;
    private static final long AUTO_TREASURE_ROUTE_CHAT_DUPLICATE_SUPPRESS_MS = 15_000L;
    /**
     * Маркер локального сообщения Auto-Клада.
     *
     * Зависимости:
     * - добавляется в уведомления `postAutoTreasureReasonToChat(...)` и
     *   `postAutoTreasureRouteRebuildToChat(...)`;
     * - удаляется в `ChatFilter.filter(...)` до вывода в чат;
     * - используется как guard, чтобы локальные строки не попадали в серверные chat-hooks
     *   (например, в сценарий Авто-Босса).
     */
    private static final String LOCAL_CHAT_MARKER = "<!--AB_LOCAL_CHAT-->";
    private static volatile long lastAutoDrinkBlazPinfoSyncAtMs = 0L;
    private static volatile long lastAutoDrinkBlazStartupSyncAttemptAtMs = 0L;
    private static volatile long lastAutoDrinkBlazTriggerAtMs = 0L;
    private static volatile long lastAutoMovingCellObservedAtMs = 0L;
    private static volatile long lastMapCellCheckDelayLogAtMs = 0L;
    private static volatile long lastMapAjaxErrAtMs = 0L;
    private static volatile int consecutiveMapAjaxErrCount = 0;
    private static volatile boolean autoDrinkBlazStartupSyncDone = false;
    private static volatile long lastAutoTreasureReasonChatAtMs = 0L;
    private static volatile long lastAutoTreasureRouteChatAtMs = 0L;
    private static volatile long lastAutoTreasureRouteChatKeyAtMs = 0L;
    private static volatile String lastAutoTreasureRouteChatKey = "";

    public static String process(String html) {
        if (AppVars.FastNeed && AppVars.FastPauseNonCombatAutoFunctions) {
            if (AppVars.AutoMoving || AppVars.DoSearchBox) {
                String msg = "[MAPAJAX_TRACE_ENTRY] SKIP: fast action active, fastId='" + AppVars.FastId + "'"
                        + ", FastNeed=" + AppVars.FastNeed
                        + ", AutoMoving=" + AppVars.AutoMoving
                        + ", DoSearchBox=" + AppVars.DoSearchBox;
                AppLog.d(TAG, TAG, msg);
            }
            return html;
        }

        if (AppVars.TreasureDigPauseNonCombatAutoFunctions) {
            if (AppVars.AutoMoving || AppVars.DoSearchBox) {
                String msg = "[MAPAJAX_TRACE_ENTRY] SKIP: treasure dig preparation active";
                AppLog.d(TAG, TAG, msg);
            }
            return html;
        }

        if (!AppVars.AutoMoving || !AppVars.DoSearchBox) {
            autoDrinkBlazStartupSyncDone = false;
            lastAutoDrinkBlazStartupSyncAttemptAtMs = 0L;
        }
        if (!AppVars.AutoMoving) {
            lastAutoMovingCellObservedAtMs = 0L;
            resetMapAjaxErrCounter("automove_disabled");
        } else if (isMapAjaxGoResponse(html)) {
            resetMapAjaxErrCounter("map_ajax_go");
        }

        if (isMapAjaxErrResponse(html) && AppVars.AutoMoving) {
            long now = System.currentTimeMillis();
            int errCount = registerMapAjaxErr(now);
            if (errCount <= MAP_AJAX_SOFT_RETRY_LIMIT) {
                if (AppVars.NeverTimer < now + MAP_AJAX_SOFT_RETRY_DELAY_MS) {
                    AppVars.NeverTimer = now + MAP_AJAX_SOFT_RETRY_DELAY_MS;
                }
                AppLog.w(TAG, "AUTO_SEARCH_BOX_TRACE: map_ajax returned ERR during automove, soft retry"
                        + ", errCount=" + errCount
                        + ", delayMs=" + MAP_AJAX_SOFT_RETRY_DELAY_MS);
                return Filter.buildRedirectString(
                        "",
                        "main.php?get_id=56&act=10&go=ret&ab_nav_err_retry=" + errCount);
            }

            AppVars.AutoMovingMapPath = null;
            AppVars.AutoMovingNextJump = null;
            AppVars.AutoMovingJumps = 0;
            if (AppVars.NeverTimer < now + 900L) {
                AppVars.NeverTimer = now + 900L;
            }
            AppLog.w(TAG, "AUTO_SEARCH_BOX_TRACE: map_ajax returned ERR during automove, redirect to main.php");
            resetMapAjaxErrCounter("hard_recover");
            return Filter.buildRedirectString("\u041D\u0430\u0432\u0438\u0433\u0430\u0442\u043E\u0440: \u0432\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0435", "main.php?ab_nav_recover=1");
        }

        if (containsTooTiredMessage(html) && AppVars.AutoMoving && !AppVars.CurePauseNonCombatAutoFunctions) {
            int tiedThreshold = (AppVars.Profile == null)
                    ? 100
                    : Math.max(0, Math.min(100, AppVars.Profile.AutoDrinkBlazTied));
            CharacterVitalsManager.Snapshot currentVitals = CharacterVitalsManager.snapshot();
            long now = System.currentTimeMillis();
            long sinceTrigger = now - lastAutoDrinkBlazTriggerAtMs;
            boolean keepNearThresholdValue = currentVitals.tied >= tiedThreshold
                    && currentVitals.tied < 100
                    && sinceTrigger >= 0L
                    && sinceTrigger < AUTO_DRINK_BLAZ_POST_TRIGGER_STICKY_MS;
            CharacterVitalsManager.Snapshot tooTiredVitals = keepNearThresholdValue
                    ? currentVitals
                    : CharacterVitalsManager.updateTied(100, "MapAjax.process.tooTired");
            if (keepNearThresholdValue) {
                AppLog.d(TAG, "AUTO_BLAZ_TRACE keep tied from pinfo after trigger: tied="
                        + currentVitals.tied + ", threshold=" + tiedThreshold
                        + ", sinceTriggerMs=" + sinceTrigger);
            }
            int tiedNow = tooTiredVitals.tied;
            if (AppVars.Profile == null || !AppVars.Profile.DoAutoDrinkBlaz) {
                // Без автопитья блажа маршрут останавливаем сразу, чтобы не зациклиться на "усталости".
                AppVars.AutoMoving = false;
                AppVars.AutoMovingMapPath = null;
                AppVars.AutoMovingNextJump = null;
                AppVars.AutoMovingJumps = 0;
                postAutoTreasureReasonToChat("Авто-Клад остановлен: усталость " + tiedNow
                        + "%, порог " + tiedThreshold
                        + "%. Включите «Пить блаж, если усталость» или снизьте усталость вручную.");
                AppLog.w(TAG, "AUTO_SEARCH_BOX_TRACE: too tired, auto moving stopped (DoAutoDrinkBlaz=false)");
            } else {
                String currentRegNum = (AppVars.Profile != null) ? AppVars.Profile.MapLocation : null;
                String autoDrinkRedirect = maybeTriggerAutoDrinkBlazOnThreshold(currentRegNum);
                if (autoDrinkRedirect != null) {
                    AppLog.i(TAG, "AUTO_SEARCH_BOX_TRACE: too tired, auto moving paused, redirect to main.php for auto bliss");
                    return autoDrinkRedirect;
                }
                AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE: too tired, auto bliss gate skipped"
                        + ", tied=" + tiedNow + ", threshold=" + tiedThreshold
                        + ", fastNeed=" + AppVars.FastNeed);
            }
            return Filter.buildRedirectString(
                    "",
                    "main.php?ab_nav_tired=1");
        }

        final String patternVarMap = "var map = [[";
        int posVarMap = html.indexOf(patternVarMap);
        if (posVarMap == -1) return html;

        String previousMapLocation = AppVars.Profile != null ? AppVars.Profile.MapLocation : null;

        posVarMap += patternVarMap.length();
        int posComma = html.indexOf(',', posVarMap);
        if (posComma == -1) return html;

        String stringX = html.substring(posVarMap, posComma).trim();
        posComma++;
        int posNextComma = html.indexOf(',', posComma);
        if (posNextComma == -1) return html;
        String stringY = html.substring(posComma, posNextComma).trim();

        String positionOurLocation = stringY + "/" + stringX + "_" + stringY;
        Position ourPos = ExtMap.Location.get(positionOurLocation);
        if (ourPos != null) {
            String regNum = ourPos.RegNum;
            if (AppVars.Profile != null) {
                AppVars.Profile.MapLocation = regNum;
            }
            RoomManager.onMapLocationConfirmed(AppVars.getContext(), regNum);
            markSearchBoxVisited(regNum);
            if (regNum != null
                    && !regNum.isEmpty()
                    && (AppVars.AutoMoving || AppVars.AutoDrinkBlazPending)
                    && !AppVars.CurePauseNonCombatAutoFunctions) {
                if (AppVars.AutoMoving) {
                    maybeSyncVitalsFromPinfoAtSearchBoxStartup(regNum);
                    onAutoMovingCellObserved(previousMapLocation, regNum);
                }
                String autoDrinkRedirect = maybeTriggerAutoDrinkBlazOnThreshold(regNum);
                if (autoDrinkRedirect != null && !autoDrinkRedirect.isEmpty()) {
                    return autoDrinkRedirect;
                }
            }
        }

        posComma = posNextComma + 1;
        posNextComma = html.indexOf(',', posComma);
        if (posNextComma != -1) {
            String movingTime = html.substring(posComma, posNextComma).trim();
            if (!movingTime.isEmpty()) {
                AppVars.MovingTime = movingTime;
            }
        }

        ExtMap.MovableCells.clear();
        final String patternDoubleBrackets = ",[[";
        int posOpenBrackets = html.indexOf(patternDoubleBrackets, posVarMap);
        if (posOpenBrackets != -1) {
            int posOpenBracket = posOpenBrackets + patternDoubleBrackets.length();
            while (posOpenBracket != -1) {
                int posCloseBracket = html.indexOf(']', posOpenBracket);
                if (posCloseBracket == -1) break;
                String insideBrackets = html.substring(posOpenBracket, posCloseBracket);
                if (insideBrackets.indexOf(';') != -1) return html;
                String[] parsInsideBrackets = insideBrackets.split(",");
                if (parsInsideBrackets.length == 3) {
                    String sCoordX = parsInsideBrackets[0].trim();
                    String sCoordY = parsInsideBrackets[1].trim();
                    String position = sCoordY + "/" + sCoordX + "_" + sCoordY;
                    String magicCode = parsInsideBrackets[2].trim().replace("\"", "");
                    ExtMap.MovableCells.put(position, magicCode);
                }
                posOpenBracket = html.indexOf('[', posCloseBracket);
                if (posOpenBracket == -1) break;
                posOpenBracket++;
            }
        }

        // Пауза небоевых авто-функций на время внешнего авто-лечения.
        // Во время doctorform-конвейера не выполняем шаги автоклада и не запускаем
        // сопутствующие авто-ветки по карте (усталость/автопитье блажа).
        if (AppVars.CurePauseNonCombatAutoFunctions) {
            if (AppVars.AutoMoving || AppVars.DoSearchBox) {
                AppLog.d(TAG, "AUTO_CURE_TRACE: skip map auto processing while cure pipeline is active"
                        + ", cureNeed=" + AppVars.CureNeed
                        + ", cureNick=" + AppVars.CureNick
                        + ", cureTravm=" + AppVars.CureTravm);
            }
            return html;
        }

        // При отложенном автопитье блажа временно не делаем шаги маршрута,
        // но сохраняем AutoMoving-контекст для последующего продолжения.
        if (AppVars.AutoDrinkBlazPending) {
            AppLog.d(TAG, "AUTO_BLAZ_DECISION: stage=pause, action=skip_move_while_pending, reg="
                    + (AppVars.Profile != null ? AppVars.Profile.MapLocation : "?"));
            return html;
        }

        if (!AppVars.AutoMoving) return html;

        String mapLocation = AppVars.Profile != null ? AppVars.Profile.MapLocation : null;
        if (mapLocation == null) return html;

        if (AppVars.AutoMovingDestinaton == null || AppVars.AutoMovingDestinaton.isEmpty()) {
            AppLog.w(TAG, "AUTO_MOVING_TRACE: destination is empty while AutoMoving=true");
            return html;
        }

        if (mapLocation.equals(AppVars.AutoMovingDestinaton)) {
            if (!AppVars.DoSearchBox) {
                AppVars.AutoMoving = false;
                AppVars.AutoMovingMapPath = null;
                AppVars.AutoMovingNextJump = null;
                AppVars.AutoMovingJumps = 0;
                AppLog.i(TAG, "AutoMoving: destination reached at " + mapLocation);
                return html;
            }

            String nextSearchDestination = findNextDestForBox(mapLocation);
            if (nextSearchDestination == null || nextSearchDestination.isEmpty()) {
                AppLog.i(TAG, "AUTO_SEARCH_BOX_TRACE: no next destination from " + mapLocation);
                return html;
            }

            AppVars.AutoMovingDestinaton = nextSearchDestination;
            AppVars.AutoMovingMapPath = null;
            AppVars.AutoMovingNextJump = null;
            AppVars.AutoMovingJumps = 0;
            AppLog.i(TAG, "AUTO_SEARCH_BOX_TRACE: rotate destination to " + nextSearchDestination);
        }
        else if (AppVars.DoSearchBox && isAutoTreasureThoroughNeighborCheckEnabled()) {
            // Для режима "Тщательный обход" переоцениваем цель на каждом шаге.
            // Это устраняет сценарий, когда маршрут уже проложен далеко вперёд,
            // и detour-логика включается только после достижения старой цели.
            String stepSearchDestination = findNextDestForBox(mapLocation);
            if (stepSearchDestination != null
                    && !stepSearchDestination.isEmpty()
                    && !stepSearchDestination.equals(AppVars.AutoMovingDestinaton)) {
                AppLog.i(TAG, "AUTO_SEARCH_BOX_TRACE: step re-evaluate destination "
                        + AppVars.AutoMovingDestinaton + " -> " + stepSearchDestination
                        + " (source=" + mapLocation + ")");
                AppVars.AutoMovingDestinaton = stepSearchDestination;
                AppVars.AutoMovingMapPath = null;
                AppVars.AutoMovingNextJump = null;
                AppVars.AutoMovingJumps = 0;
            }
        }

        if (shouldDelayAutoMovingStep(mapLocation)) {
            return html;
        }

        if (AppVars.AutoMovingMapPath == null || !AppVars.AutoMovingMapPath.canUseExistingPath(mapLocation, AppVars.AutoMovingDestinaton)) {
            List<String> dest = Collections.singletonList(AppVars.AutoMovingDestinaton);
            AppVars.AutoMovingMapPath = new MapPath(mapLocation, dest);
        }

        if (!AppVars.AutoMovingMapPath.pathExists) return html;

        AppVars.AutoMovingNextJump = AppVars.AutoMovingMapPath.nextJump;
        AppVars.AutoMovingJumps = AppVars.AutoMovingMapPath.jumps;
        AppVars.AutoMovingCityGate = AppVars.AutoMovingMapPath.cityGate;

        if (AppVars.AutoMovingMapPath.isNextTeleport) {
            String newhtml = mainPhpFindEnter(html);
            if (newhtml != null && !newhtml.isEmpty()) return newhtml;
        } else if (AppVars.AutoMovingMapPath.isNextCity) {
            String newhtml = mainPhpFindEnter(html);
            if (newhtml != null && !newhtml.isEmpty()) return newhtml;
        } else {
            String nextJump = AppVars.AutoMovingMapPath.nextJump;
            if (nextJump != null) {
                String coorn = ExtMap.InvLocation.get(nextJump);
                if (coorn != null) {
                    Position position = ExtMap.Location.get(coorn);
                    if (position != null) {
                        String callMove = "moveMapTo(" + position.X + ", " + position.Y + ", map[0][2]);";
                        final String patternViewMap = "view_map();";
                        int poscript = html.toLowerCase().indexOf(patternViewMap.toLowerCase());
                        if (poscript != -1) {
                            poscript += patternViewMap.length();
                            html = html.substring(0, poscript) + callMove + html.substring(poscript);
                        }
                    }
                }
            }
        }

        return html;
    }

    /**
     * C# parity (`FormMainNavigator.FindNextDestForBox`):
     * находит ближайшую клетку, которую не посещали >= 1 суток.
     * Если таких клеток нет, выбирает клетку с самым старым маркером посещения
     * (fallback-циклирование по уже пройденным клеткам).
     */
    /**
     * Главная точка выбора следующей клетки для Auto-Клада.
     *
     * Контур выбора:
     * 1) fixed-cell режим (если включён) имеет приоритет;
     * 2) базовая цель через `findBaseSearchBoxDestination(...)`;
     * 3) при включённом `Тщательном обходе` — попытка detour рядом с текущей клеткой;
     * 4) если detour нет — используем базовую цель.
     *
     * Зависимости:
     * - `AutoFunctionsManager` (флаги smart/thorough),
     * - `AppVars.SearchBoxVisited` (visited-маркеры),
     * - `MainPhp.buildServerChatTimeHtmlExternal()` + broadcast в чат (уведомления о перестроении).
     */
    public static String findNextDestForBox(String sourceLocation) {
        String source = sourceLocation;
        if ((source == null || source.isEmpty()) && AppVars.Profile != null) {
            source = AppVars.Profile.MapLocation;
        }
        if (source == null || source.isEmpty() || !ExtMap.Cells.containsKey(source)) {
            return null;
        }

        String fixedModeDestination = findFixedTreasureCellDestination(source);
        if (fixedModeDestination != null && !fixedModeDestination.isEmpty()) {
            return fixedModeDestination;
        }

        long nowMs = System.currentTimeMillis();
        SearchBoxDestination baseDestination = findBaseSearchBoxDestination(source, nowMs);
        if (baseDestination == null || baseDestination.regNum == null || baseDestination.regNum.isEmpty()) {
            return null;
        }

        if (!isAutoTreasureThoroughNeighborCheckEnabled()) {
            return baseDestination.regNum;
        }

        if (baseDestination.visitedAtMs <= 0L) {
            AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE thorough-neighbor: skip (base has no visited), base="
                    + baseDestination.regNum);
            return baseDestination.regNum;
        }

        SearchBoxNeighborScanResult scanResult = findThoroughNeighborCandidates(source, baseDestination, nowMs);
        List<SearchBoxNeighborCandidate> candidates = scanResult.candidates;
        AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE thorough-neighbor: source=" + source
                + ", base=" + baseDestination.regNum
                + ", baseVisitedAt=" + baseDestination.visitedAtMs
                + ", baseDistance=" + baseDestination.distanceSteps
                + ", minAgeMs=" + scanResult.stats.configuredMinAgeMs
                + ", candidates=" + formatThoroughNeighborCandidates(candidates, baseDestination.visitedAtMs)
                + ", stats=" + formatThoroughNeighborStats(scanResult.stats));
        if (!candidates.isEmpty()) {
            SearchBoxNeighborCandidate selected = candidates.get(0);
            AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE thorough-neighbor: select detour="
                    + selected.regNum + ", pathLen=" + selected.distanceSteps
                    + ", visitedDeltaMs=" + (selected.visitedAtMs - baseDestination.visitedAtMs)
                    + ", visitedAgeMs=" + selected.visitedAgeMs
                    + ", then return to base=" + baseDestination.regNum);
            if (shouldPostThoroughDetourChat(source, baseDestination.regNum, selected.regNum)) {
                postAutoTreasureRouteRebuildToChat("Тщательный обход", selected.regNum);
            } else {
                AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE thorough-neighbor: route-chat skipped (detour is on normal path)"
                        + ", source=" + source
                        + ", base=" + baseDestination.regNum
                        + ", detour=" + selected.regNum);
            }
            return selected.regNum;
        }

        AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE thorough-neighbor: no detour candidates, use base="
                + baseDestination.regNum);
        return baseDestination.regNum;
    }

    /**
     * Пишем уведомление о "Дообходе" только если detour-клетка не была бы следующим
     * шагом обычного маршрута к базовой цели без режима "Тщательный обход".
     */
    private static boolean shouldPostThoroughDetourChat(String sourceRegNum, String baseRegNum, String detourRegNum) {
        if (sourceRegNum == null || sourceRegNum.isEmpty()
                || baseRegNum == null || baseRegNum.isEmpty()
                || detourRegNum == null || detourRegNum.isEmpty()) {
            return false;
        }
        if (detourRegNum.equals(baseRegNum)) {
            return false;
        }
        try {
            MapPath normalPath = new MapPath(sourceRegNum, Collections.singletonList(baseRegNum));
            if (!normalPath.pathExists) {
                return true;
            }
            String normalNext = normalPath.nextJump;
            return normalNext == null || normalNext.isEmpty() || !detourRegNum.equals(normalNext);
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_SEARCH_BOX_TRACE thorough-neighbor: compare with normal path failed", e);
            return true;
        }
    }

    /**
     * Возвращает базовую цель Auto-Клада без detour.
     *
     * Приоритет:
     * - сначала непосещённые/просроченные клетки (TTL-модель),
     * - затем fallback: самая "старая" visited-клетка.
     *
     * Для `Умной системы генерации`:
     * - fallback-клетка дополнительно проверяется по минимальному возрасту visited;
     * - если клетка слишком "свежая", метод возвращает `null` и маршрут не перестраивается.
     */
    private static SearchBoxDestination findBaseSearchBoxDestination(String source, long nowMs) {
        boolean smartGenerationEnabled = isAutoTreasureSmartGenerationEnabled();
        int[] idx = new int[] {0, 0, -1, 1, -1, 1, -1, 1};
        int[] idy = new int[] {-1, 1, 0, 0, -1, -1, 1, 1};

        Set<String> visited = new HashSet<>();
        ArrayDeque<SearchNode> frontier = new ArrayDeque<>();
        visited.add(source);
        frontier.add(new SearchNode(source, 0));

        String oldestVisitedFallback = null;
        long oldestVisitedAtMs = Long.MAX_VALUE;
        int oldestVisitedDistance = Integer.MAX_VALUE;

        while (!frontier.isEmpty()) {
            SearchNode current = frontier.poll();
            if (current == null || current.regNum == null || current.regNum.isEmpty()) {
                continue;
            }
            for (int i = 0; i < idx.length; i++) {
                String next = moveMapCell(current.regNum, idx[i], idy[i]);
                if (next == null || next.isEmpty() || visited.contains(next)) {
                    continue;
                }
                int nextDistance = current.distanceSteps + 1;
                visited.add(next);
                frontier.add(new SearchNode(next, nextDistance));

                if (isSearchBoxCandidate(next, nowMs)) {
                    Long visitedAt = AppVars.SearchBoxVisited.get(next);
                    long visitedAtMs = visitedAt == null ? -1L : visitedAt;
                    return new SearchBoxDestination(next, visitedAtMs, nextDistance);
                }

                Long visitedAt = AppVars.SearchBoxVisited.get(next);
                if (visitedAt != null
                        && visitedAt > 0L
                        && !next.equals(source)
                        && (visitedAt < oldestVisitedAtMs
                        || (visitedAt.equals(oldestVisitedAtMs) && nextDistance < oldestVisitedDistance))) {
                    oldestVisitedAtMs = visitedAt;
                    oldestVisitedFallback = next;
                    oldestVisitedDistance = nextDistance;
                }
            }
        }

        if (oldestVisitedFallback != null && !oldestVisitedFallback.isEmpty()) {
            long ageMs = Math.max(0L, nowMs - oldestVisitedAtMs);
            if (smartGenerationEnabled && ageMs < SEARCH_BOX_SMART_RECHECK_MIN_AGE_MS) {
                long waitMs = SEARCH_BOX_SMART_RECHECK_MIN_AGE_MS - ageMs;
                AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE smart-generation: skip recent fallback=" + oldestVisitedFallback
                        + ", ageMs=" + ageMs
                        + ", minAgeMs=" + SEARCH_BOX_SMART_RECHECK_MIN_AGE_MS
                        + ", waitMs=" + waitMs);
                postAutoTreasureRouteRebuildToChat("Умная генерация", oldestVisitedFallback);
                return null;
            }
            AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE: fallback oldest-visited destination="
                    + oldestVisitedFallback + ", ageMs=" + ageMs
                    + (smartGenerationEnabled ? ", smart=true" : ", smart=false"));
            return new SearchBoxDestination(oldestVisitedFallback, oldestVisitedAtMs, oldestVisitedDistance);
        }
        return null;
    }

    /**
     * Сканирует соседние клетки для режима "Тщательный обход".
     *
     * Кандидат принимается только если одновременно:
     * - путь до него <= `SEARCH_BOX_THOROUGH_MAX_STEPS`,
     * - он в манхэттен-радиусе <= текущего значения настройки "Радиус соседних клеток (Манхэттен)",
     * - у него есть visited-маркер,
     * - он новее базовой цели, но не старше лимита `SEARCH_BOX_THOROUGH_MAX_NEWER_DELTA_MS`.
     *
     * Метод также возвращает статистику отбраковки для debug-аналитики в logcat.
     */
    private static SearchBoxNeighborScanResult findThoroughNeighborCandidates(
            String source,
            SearchBoxDestination baseDestination,
            long nowMs) {
        SearchBoxNeighborDebugStats stats = new SearchBoxNeighborDebugStats();
        int thoroughRadius = resolveAutoTreasureThoroughNeighborRadius();
        long minAgeMs = resolveAutoTreasureThoroughNeighborMinAgeMs();
        stats.configuredManhattanRadius = thoroughRadius;
        stats.configuredMinAgeMs = minAgeMs;
        int[] sourceCoords = getCellCoordinates(source);
        if (sourceCoords == null || baseDestination == null || baseDestination.visitedAtMs <= 0L) {
            stats.skippedByNoSourceCoordsOrBase++;
            return new SearchBoxNeighborScanResult(Collections.emptyList(), stats);
        }
        int[] baseCoords = getCellCoordinates(baseDestination.regNum);
        int sourceToBaseManhattan = -1;
        if (baseCoords != null) {
            sourceToBaseManhattan = Math.abs(sourceCoords[0] - baseCoords[0])
                    + Math.abs(sourceCoords[1] - baseCoords[1]);
        }

        int[] idx = new int[] {0, 0, -1, 1, -1, 1, -1, 1};
        int[] idy = new int[] {-1, 1, 0, 0, -1, -1, 1, 1};

        Set<String> visited = new HashSet<>();
        ArrayDeque<SearchNode> frontier = new ArrayDeque<>();
        List<SearchBoxNeighborCandidate> candidates = new ArrayList<>();
        visited.add(source);
        frontier.add(new SearchNode(source, 0));

        while (!frontier.isEmpty()) {
            SearchNode current = frontier.poll();
            if (current == null || current.regNum == null || current.regNum.isEmpty()) {
                continue;
            }
            if (current.distanceSteps >= SEARCH_BOX_THOROUGH_MAX_STEPS) {
                continue;
            }

            for (int i = 0; i < idx.length; i++) {
                String next = moveMapCell(current.regNum, idx[i], idy[i]);
                if (next == null || next.isEmpty()) {
                    stats.skippedByInvalidNeighbor++;
                    continue;
                }
                if (visited.contains(next)) {
                    stats.skippedByAlreadySeen++;
                    continue;
                }

                int nextDistance = current.distanceSteps + 1;
                visited.add(next);
                frontier.add(new SearchNode(next, nextDistance));
                stats.discoveredNeighbors++;

                if (nextDistance > SEARCH_BOX_THOROUGH_MAX_STEPS
                        || next.equals(source)) {
                    stats.skippedByPathLimitOrSource++;
                    continue;
                }
                if (next.equals(baseDestination.regNum)) {
                    stats.skippedByBaseCell++;
                    continue;
                }

                int[] nextCoords = getCellCoordinates(next);
                if (nextCoords == null) {
                    stats.skippedByMissingCoords++;
                    continue;
                }
                int manhattanDistance = Math.abs(nextCoords[0] - sourceCoords[0]) + Math.abs(nextCoords[1] - sourceCoords[1]);
                if (manhattanDistance > thoroughRadius) {
                    stats.skippedByManhattanRadius++;
                    continue;
                }
                if (sourceToBaseManhattan >= 0) {
                    int nextToBaseManhattan = Math.abs(nextCoords[0] - baseCoords[0])
                            + Math.abs(nextCoords[1] - baseCoords[1]);
                    if (nextToBaseManhattan > sourceToBaseManhattan) {
                        stats.skippedByAwayFromBase++;
                        continue;
                    }
                }

                Long visitedAt = AppVars.SearchBoxVisited.get(next);
                if (visitedAt == null || visitedAt <= 0L) {
                    stats.skippedByNoVisitedMarker++;
                    continue;
                }

                long visitedAgeMs = Math.max(0L, nowMs - visitedAt);
                if (visitedAgeMs < minAgeMs) {
                    stats.skippedByVisitedAgeTooFresh++;
                    continue;
                }

                long visitedDelta = visitedAt - baseDestination.visitedAtMs;
                if (visitedDelta <= 0L) {
                    stats.skippedByVisitedDeltaNotNewer++;
                    continue;
                }
                if (visitedDelta > SEARCH_BOX_THOROUGH_MAX_NEWER_DELTA_MS) {
                    stats.skippedByVisitedDeltaTooLarge++;
                    continue;
                }

                candidates.add(new SearchBoxNeighborCandidate(next, visitedAt, visitedAgeMs, nextDistance, manhattanDistance));
                stats.acceptedCandidates++;
            }
        }

        // ✅ API 21 compatible sort (List#sort requires API 24)
        Collections.sort(candidates, (left, right) -> {
            if (left.distanceSteps != right.distanceSteps) {
                return Integer.compare(left.distanceSteps, right.distanceSteps);
            }
            if (left.visitedAtMs != right.visitedAtMs) {
                return Long.compare(left.visitedAtMs, right.visitedAtMs);
            }
            if (left.manhattanDistance != right.manhattanDistance) {
                return Integer.compare(left.manhattanDistance, right.manhattanDistance);
            }
            return left.regNum.compareToIgnoreCase(right.regNum);
        });
        return new SearchBoxNeighborScanResult(candidates, stats);
    }

    private static String formatThoroughNeighborCandidates(List<SearchBoxNeighborCandidate> candidates, long baseVisitedAtMs) {
        if (candidates == null || candidates.isEmpty()) {
            return "[]";
        }
        int maxItems = Math.min(candidates.size(), 8);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < maxItems; i++) {
            SearchBoxNeighborCandidate candidate = candidates.get(i);
            if (candidate == null) {
                continue;
            }
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(candidate.regNum)
                    .append("(path=").append(candidate.distanceSteps)
                    .append(",manh=").append(candidate.manhattanDistance)
                    .append(",deltaMs=").append(Math.max(0L, candidate.visitedAtMs - baseVisitedAtMs))
                    .append(",ageMs=").append(Math.max(0L, candidate.visitedAgeMs))
                    .append(")");
        }
        if (candidates.size() > maxItems) {
            sb.append(", ... +").append(candidates.size() - maxItems);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Формирует плоскую строку `stats={...}` для логов `AUTO_SEARCH_BOX_TRACE`.
     *
     * Задача формата — дать быстрый ответ "почему клетка не попала в detour":
     * по ключам `skip*` видно причину отбраковки без дополнительного дебага.
     */
    private static String formatThoroughNeighborStats(SearchBoxNeighborDebugStats stats) {
        if (stats == null) {
            return "{}";
        }
        return "{discovered=" + stats.discoveredNeighbors
                + ",radius=" + stats.configuredManhattanRadius
                + ",accepted=" + stats.acceptedCandidates
                + ",skipInvalidNeighbor=" + stats.skippedByInvalidNeighbor
                + ",skipAlreadySeen=" + stats.skippedByAlreadySeen
                + ",skipPathOrSource=" + stats.skippedByPathLimitOrSource
                + ",skipBase=" + stats.skippedByBaseCell
                + ",skipNoCoords=" + stats.skippedByMissingCoords
                + ",skipManhattan=" + stats.skippedByManhattanRadius
                + ",skipAwayFromBase=" + stats.skippedByAwayFromBase
                + ",skipNoVisited=" + stats.skippedByNoVisitedMarker
                + ",skipTooFresh=" + stats.skippedByVisitedAgeTooFresh
                + ",skipDeltaNotNewer=" + stats.skippedByVisitedDeltaNotNewer
                + ",skipDeltaTooLarge=" + stats.skippedByVisitedDeltaTooLarge
                + ",skipNoSourceOrBase=" + stats.skippedByNoSourceCoordsOrBase
                + ",minAgeMs=" + stats.configuredMinAgeMs
                + "}";
    }

    private static int[] getCellCoordinates(String regNum) {
        String location = ExtMap.InvLocation.get(regNum);
        if (location == null || location.isEmpty()) {
            return null;
        }
        String[] parts = location.split("[/_]");
        if (parts.length < 2) {
            return null;
        }
        try {
            int y = Integer.parseInt(parts[0]);
            int x = Integer.parseInt(parts[1]);
            return new int[] {x, y};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isAutoTreasureThoroughNeighborCheckEnabled() {
        try {
            if (AppVars.getContext() == null) {
                return false;
            }
            return AutoFunctionsManager.getInstance(AppVars.getContext())
                    .isAutoTreasureThoroughNeighborCheckEnabled();
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_SEARCH_BOX_TRACE thorough-neighbor: read setting failed", e);
            return false;
        }
    }

    private static boolean isAutoTreasureSmartGenerationEnabled() {
        try {
            if (AppVars.getContext() == null) {
                return false;
            }
            return AutoFunctionsManager.getInstance(AppVars.getContext())
                    .isAutoTreasureSmartGenerationEnabled();
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_SEARCH_BOX_TRACE smart-generation: read setting failed", e);
            return false;
        }
    }

    private static int resolveAutoTreasureThoroughNeighborRadius() {
        try {
            if (AppVars.getContext() == null) {
                return SEARCH_BOX_THOROUGH_MANHATTAN_RADIUS_DEFAULT;
            }
            int configuredRadius = AutoFunctionsManager.getInstance(AppVars.getContext())
                    .getAutoTreasureThoroughNeighborRadius();
            return Math.max(
                    SEARCH_BOX_THOROUGH_MANHATTAN_RADIUS_MIN,
                    Math.min(SEARCH_BOX_THOROUGH_MANHATTAN_RADIUS_MAX, configuredRadius)
            );
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_SEARCH_BOX_TRACE thorough-neighbor: read radius failed", e);
            return SEARCH_BOX_THOROUGH_MANHATTAN_RADIUS_DEFAULT;
        }
    }

    private static long resolveAutoTreasureThoroughNeighborMinAgeMs() {
        try {
            if (AppVars.getContext() == null) {
                return SEARCH_BOX_THOROUGH_MIN_AGE_MINUTES_DEFAULT * 60_000L;
            }
            int configuredMinutes = AutoFunctionsManager.getInstance(AppVars.getContext())
                    .getAutoTreasureThoroughNeighborMinAgeMinutes();
            int normalizedMinutes = Math.max(
                    SEARCH_BOX_THOROUGH_MIN_AGE_MINUTES_MIN,
                    Math.min(SEARCH_BOX_THOROUGH_MIN_AGE_MINUTES_MAX, configuredMinutes)
            );
            return normalizedMinutes * 60_000L;
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_SEARCH_BOX_TRACE thorough-neighbor: read min-age failed", e);
            return SEARCH_BOX_THOROUGH_MIN_AGE_MINUTES_DEFAULT * 60_000L;
        }
    }

    private static boolean isAutoTreasureDetourChatEnabled() {
        try {
            if (AppVars.getContext() == null) {
                return false;
            }
            return AutoFunctionsManager.getInstance(AppVars.getContext())
                    .isAutoTreasureDetourChatEnabled();
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_SEARCH_BOX_TRACE detour-chat: read setting failed", e);
            return false;
        }
    }

    private static final class SearchNode {
        final String regNum;
        final int distanceSteps;

        SearchNode(String regNum, int distanceSteps) {
            this.regNum = regNum;
            this.distanceSteps = distanceSteps;
        }
    }

    private static final class SearchBoxDestination {
        final String regNum;
        final long visitedAtMs;
        final int distanceSteps;

        SearchBoxDestination(String regNum, long visitedAtMs, int distanceSteps) {
            this.regNum = regNum;
            this.visitedAtMs = visitedAtMs;
            this.distanceSteps = distanceSteps;
        }
    }

    private static final class SearchBoxNeighborCandidate {
        final String regNum;
        final long visitedAtMs;
        final long visitedAgeMs;
        final int distanceSteps;
        final int manhattanDistance;

        SearchBoxNeighborCandidate(String regNum, long visitedAtMs, long visitedAgeMs, int distanceSteps, int manhattanDistance) {
            this.regNum = regNum;
            this.visitedAtMs = visitedAtMs;
            this.visitedAgeMs = visitedAgeMs;
            this.distanceSteps = distanceSteps;
            this.manhattanDistance = manhattanDistance;
        }
    }

    /**
     * Диагностические счётчики причин отбраковки detour-кандидатов.
     * Выводятся в `AUTO_SEARCH_BOX_TRACE ... stats={...}`.
     */
    private static final class SearchBoxNeighborDebugStats {
        int configuredManhattanRadius;
        long configuredMinAgeMs;
        int discoveredNeighbors;
        int acceptedCandidates;
        int skippedByInvalidNeighbor;
        int skippedByAlreadySeen;
        int skippedByPathLimitOrSource;
        int skippedByBaseCell;
        int skippedByMissingCoords;
        int skippedByManhattanRadius;
        int skippedByAwayFromBase;
        int skippedByNoVisitedMarker;
        int skippedByVisitedAgeTooFresh;
        int skippedByVisitedDeltaNotNewer;
        int skippedByVisitedDeltaTooLarge;
        int skippedByNoSourceCoordsOrBase;
    }

    /**
     * Результат сканирования соседей:
     * - `candidates` — отфильтрованные detour-клетки (после сортировки),
     * - `stats` — сводка причин отбраковки.
     */
    private static final class SearchBoxNeighborScanResult {
        final List<SearchBoxNeighborCandidate> candidates;
        final SearchBoxNeighborDebugStats stats;

        SearchBoxNeighborScanResult(List<SearchBoxNeighborCandidate> candidates, SearchBoxNeighborDebugStats stats) {
            this.candidates = candidates;
            this.stats = stats;
        }
    }

    private static String findFixedTreasureCellDestination(String sourceRegNum) {
        try {
            if (AppVars.getContext() == null) {
                return null;
            }
            AutoFunctionsManager manager = AutoFunctionsManager.getInstance(AppVars.getContext());
            if (!manager.isAutoTreasureFixedCellConfigured()) {
                return null;
            }
            String fixedRegNum = manager.getAutoTreasureFixedCellRegNum();
            if (fixedRegNum == null || fixedRegNum.isEmpty() || !ExtMap.Cells.containsKey(fixedRegNum)) {
                return null;
            }
            if (!fixedRegNum.equals(sourceRegNum)) {
                AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE fixed-cell: move to target " + fixedRegNum + " from " + sourceRegNum);
                return fixedRegNum;
            }

            int[] idx = new int[] {0, 0, -1, 1, -1, 1, -1, 1};
            int[] idy = new int[] {-1, 1, 0, 0, -1, -1, 1, 1};
            for (int i = 0; i < idx.length; i++) {
                String neighbor = moveMapCell(fixedRegNum, idx[i], idy[i]);
                if (neighbor == null || neighbor.isEmpty() || neighbor.equals(fixedRegNum)) {
                    continue;
                }
                AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE fixed-cell: no dig marker yet, hop to neighbor "
                        + neighbor + " and return to " + fixedRegNum);
                return neighbor;
            }
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_SEARCH_BOX_TRACE fixed-cell destination failed", e);
        }
        return null;
    }

    private static boolean isSearchBoxCandidate(String regNum, long nowMs) {
        Long visitedAt = AppVars.SearchBoxVisited.get(regNum);
        if (visitedAt == null || visitedAt <= 0L) {
            return true;
        }
        return (nowMs - visitedAt) >= SEARCH_BOX_VISITED_TTL_MS;
    }

    private static String moveMapCell(String regNum, int dx, int dy) {
        String location = ExtMap.InvLocation.get(regNum);
        if (location == null || location.isEmpty()) {
            return null;
        }
        String[] parts = location.split("[/_]");
        if (parts.length < 2) {
            return null;
        }
        int y;
        int x;
        try {
            y = Integer.parseInt(parts[0]);
            x = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        String position = ExtMap.makePosition(x + dx, y + dy);
        Position pos = ExtMap.Location.get(position);
        if (pos == null || pos.RegNum == null || pos.RegNum.isEmpty()) {
            return null;
        }
        return ExtMap.Cells.containsKey(pos.RegNum) ? pos.RegNum : null;
    }

    private static void markSearchBoxVisited(String regNum) {
        if (regNum == null || regNum.isEmpty()) {
            return;
        }
        ExtMap.markCellVisited(regNum);
    }

    private static String mainPhpFindEnter(String html) {
        final String patternEnter = "[\"dep\",\"\u0412\u043e\u0439\u0442\u0438\",\"";
        int pos = html.indexOf(patternEnter);
        if (pos == -1) return null;
        pos += patternEnter.length();
        int posEnd = html.indexOf('"', pos);
        if (posEnd == -1) return null;
        String vcode = html.substring(pos, posEnd);
        String link = "main.php?get_id=56&act=10&go=dep&vcode=" + vcode;
        return Filter.buildRedirectString("\u0412\u0445\u043e\u0434", link);
    }

    private static boolean isMapAjaxErrResponse(String html) {
        if (html == null) {
            return false;
        }
        String trimmed = html.trim();
        return "ERR".equalsIgnoreCase(trimmed);
    }

    private static boolean isMapAjaxGoResponse(String html) {
        if (html == null) {
            return false;
        }
        String trimmed = html.trim();
        return trimmed.startsWith("GO@");
    }

    private static int registerMapAjaxErr(long nowMs) {
        long delta = nowMs - lastMapAjaxErrAtMs;
        if (delta < 0L || delta > MAP_AJAX_ERR_RESET_WINDOW_MS) {
            consecutiveMapAjaxErrCount = 0;
        }
        consecutiveMapAjaxErrCount++;
        lastMapAjaxErrAtMs = nowMs;
        return consecutiveMapAjaxErrCount;
    }

    private static void resetMapAjaxErrCounter(String reason) {
        if (consecutiveMapAjaxErrCount == 0 && lastMapAjaxErrAtMs == 0L) {
            return;
        }
        String msg = "[MAPAJAX_ERR_RESET] reason='" + reason + "', prevCount=" + consecutiveMapAjaxErrCount;
        AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE: reset map_ajax ERR counter, reason=" + reason
                + ", prevCount=" + consecutiveMapAjaxErrCount);
        FileLogger.trace(TAG, msg);
        consecutiveMapAjaxErrCount = 0;
        lastMapAjaxErrAtMs = 0L;
    }

    private static boolean containsTooTiredMessage(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("\u0441\u043b\u0438\u0448\u043a\u043e\u043c \u0443\u0441\u0442\u0430\u043b")
                || lower.contains("\u043e\u0442\u0434\u043e\u0445\u043d\u0438\u0442\u0435")
                || lower.contains("\u0432\u044b \u0443\u0441\u0442\u0430\u043b\u0438");
    }

    /**
     * Стартовая синхронизация параметров персонажа для режима "Авто-Клад/Навигатор".
     *
     * Когда вызывается:
     * - только в активном `AutoMoving + DoSearchBox`;
     * - один раз на старте цикла (с retry-cooldown при неудаче).
     *
     * Что делает:
     * - запрашивает `pinfo.cgi` по нику профиля;
     * - обновляет единый витал-снимок через {@link #applyPinfoVitals(NeverApi.PinfoVitals, String, int, boolean)};
     * - фиксирует факт стартовой синхронизации для текущего цикла.
     *
     * Зависимости:
     * - `AppVars.Profile.UserNick`;
     * - `NeverApi.getPinfoVitalsFromPinfo(...)`;
     * - `CharacterVitalsManager` (единая запись vitals).
     */
    private static void maybeSyncVitalsFromPinfoAtSearchBoxStartup(String currentRegNum) {
        if (!AppVars.AutoMoving || !AppVars.DoSearchBox || autoDrinkBlazStartupSyncDone) {
            FileLogger.trace(TAG, "[MAPAJAX_STARTUP_SYNC_SKIP] AutoMoving=" + AppVars.AutoMoving
                    + ", DoSearchBox=" + AppVars.DoSearchBox
                    + ", syncDone=" + autoDrinkBlazStartupSyncDone);
            return;
        }
        if (AppVars.Profile == null) {
            FileLogger.trace(TAG, "[MAPAJAX_STARTUP_SYNC_PROFILE_NULL]");
            return;
        }
        String nick = AppVars.Profile.UserNick;
        if (nick == null || nick.trim().isEmpty()) {
            FileLogger.trace(TAG, "[MAPAJAX_STARTUP_SYNC_NICK_EMPTY]");
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastAutoDrinkBlazStartupSyncAttemptAtMs;
        if (elapsed < AUTO_DRINK_BLAZ_STARTUP_SYNC_RETRY_COOLDOWN_MS) {
            FileLogger.trace(TAG, "[MAPAJAX_STARTUP_SYNC_COOLDOWN] elapsed=" + elapsed + "ms < " + AUTO_DRINK_BLAZ_STARTUP_SYNC_RETRY_COOLDOWN_MS + "ms");
            return;
        }
        lastAutoDrinkBlazStartupSyncAttemptAtMs = now;
        FileLogger.trace(TAG, "[MAPAJAX_STARTUP_SYNC_REQUEST] nick='" + nick + "', reg=" + currentRegNum);

        int threshold = clampPercent(AppVars.Profile.AutoDrinkBlazTied);
        NeverApi.PinfoVitals vitals = NeverApi.getPinfoVitalsFromInfoApi(nick, "auto_blaz_startup");
        if (vitals == null) {
            logAutoBlazDecision(
                    "startup",
                    "skip_sync_failed",
                    CharacterVitalsManager.snapshot().tied,
                    threshold,
                    "reg=" + currentRegNum
            );
            return;
        }

        applyPinfoVitals(vitals, currentRegNum, threshold, true);
        autoDrinkBlazStartupSyncDone = true;
        logAutoBlazDecision("startup", "synced", CharacterVitalsManager.snapshot().tied, threshold, "reg=" + currentRegNum);
    }

    /**
     * Обрабатывает факт перехода на новую клетку в авто-навигации.
     *
     * Что делает:
     * - увеличивает усталость на шаг (`AUTO_MOVING_TIED_STEP_COST`) через единый менеджер;
     * - пишет trace-лог изменения усталости;
     * - при подходе к порогу запускает синхронизацию усталости из pinfo.
     *
     * Зависимости:
     * - `CharacterVitalsManager.increaseTied(...)`;
     * - `maybeSyncTiedFromPinfoIfNearThreshold(...)`.
     */
    private static void onAutoMovingCellObserved(String previousRegNum, String currentRegNum) {
        if (previousRegNum == null || previousRegNum.isEmpty()) {
            String msg = "[MAPAJAX_AUTOMOVE_SKIP] previousRegNum is null or empty";
            FileLogger.trace(TAG, msg);
            return;
        }
        if (previousRegNum.equals(currentRegNum)) {
            String msg = "[MAPAJAX_AUTOMOVE_SAME] Same cell, no movement: " + currentRegNum;
            FileLogger.trace(TAG, msg);
            return;
        }
        lastAutoMovingCellObservedAtMs = System.currentTimeMillis();
        int oldTied = CharacterVitalsManager.snapshot().tied;
        CharacterVitalsManager.Snapshot stepped = CharacterVitalsManager.increaseTied(
                AUTO_MOVING_TIED_STEP_COST,
                "MapAjax.onAutoMovingCellObserved.step"
        );
        int newTied = stepped.tied;
        String moveMsg = "[MAPAJAX_AUTOMOVE_CELL] Cell observed: from='" + previousRegNum
                + "', to='" + currentRegNum
                + "', tied: " + oldTied + "->" + newTied
                + ", stepCost=" + AUTO_MOVING_TIED_STEP_COST;
        if (newTied != oldTied) {
            AppLog.d(TAG, "AUTO_BLAZ_TRACE tied +step: old=" + oldTied
                    + ", new=" + newTied
                    + ", stepCost=" + AUTO_MOVING_TIED_STEP_COST
                    + ", from=" + previousRegNum
                    + ", to=" + currentRegNum);
            FileLogger.trace(TAG, moveMsg);
        } else {
            FileLogger.trace(TAG, moveMsg + " (tied unchanged)");
        }
        maybeSyncTiedFromPinfoIfNearThreshold(currentRegNum);
    }

    /**
     * Near-threshold синхронизация усталости из pinfo перед автопитьем блажа.
     *
     * Что делает:
     * - проверяет, что усталость близко к порогу (`threshold - delta`);
     * - по cooldown запрашивает pinfo;
     * - обновляет vitals через {@link #applyPinfoVitals(NeverApi.PinfoVitals, String, int, boolean)};
     * - логирует, изменилось ли значение усталости после синхронизации.
     *
     * Зависимости:
     * - профильные настройки `DoAutoDrinkBlaz` / `AutoDrinkBlazTied`;
     * - `CharacterVitalsManager.snapshot()`;
     * - `NeverApi.getPinfoVitalsFromPinfo(...)`.
     */
    private static void maybeSyncTiedFromPinfoIfNearThreshold(String currentRegNum) {
        if (AppVars.Profile == null || !AppVars.Profile.DoAutoDrinkBlaz) {
            FileLogger.trace(TAG, "[MAPAJAX_SYNC_SKIP] Profile disabled for auto-drink blaz");
            return;
        }
        int threshold = clampPercent(AppVars.Profile.AutoDrinkBlazTied);
        int tied = CharacterVitalsManager.snapshot().tied;
        int syncBorder = Math.max(0, threshold - AUTO_DRINK_BLAZ_NEAR_THRESHOLD_DELTA);
        String checkMsg = "[MAPAJAX_SYNC_CHECK] tied=" + tied + ", threshold=" + threshold
                + ", syncBorder=" + syncBorder + ", reg=" + currentRegNum;
        if (tied < syncBorder) {
            FileLogger.trace(TAG, checkMsg + " -> SKIP: tied < syncBorder");
            return;
        }
        FileLogger.trace(TAG, checkMsg + " -> CHECK: tied >= syncBorder");
        long now = System.currentTimeMillis();
        long lastSync = now - lastAutoDrinkBlazPinfoSyncAtMs;
        if (lastSync < AUTO_DRINK_BLAZ_PINFO_SYNC_COOLDOWN_MS) {
            FileLogger.trace(TAG, "[MAPAJAX_SYNC_COOLDOWN] lastSync=" + lastSync + "ms < " + AUTO_DRINK_BLAZ_PINFO_SYNC_COOLDOWN_MS + "ms");
            return;
        }
        String nick = AppVars.Profile.UserNick;
        if (nick == null || nick.trim().isEmpty()) {
            FileLogger.trace(TAG, "[MAPAJAX_SYNC_NICK_EMPTY] nick is null or empty");
            return;
        }
        FileLogger.trace(TAG, "[MAPAJAX_SYNC_REQUEST] Requesting pinfo from nick='" + nick + "'");
        NeverApi.PinfoVitals synced = NeverApi.getPinfoVitalsFromInfoApi(nick, "auto_blaz_near_threshold");
        if (synced == null || synced.curTire == null) {
            String failMsg = "[MAPAJAX_SYNC_FAILED] synced=" + (synced == null ? "null" : "notNull");
            FileLogger.trace(TAG, failMsg);
            logAutoBlazDecision("sync", "skip_sync_failed", tied, threshold, "reg=" + currentRegNum);
            return;
        }
        lastAutoDrinkBlazPinfoSyncAtMs = now;
        int oldTied = CharacterVitalsManager.snapshot().tied;
        FileLogger.trace(TAG, "[MAPAJAX_SYNC_APPLY_START] curTire=" + synced.curTire + ", nick=" + nick);
        applyPinfoVitals(synced, currentRegNum, threshold, false);
        int normalized = CharacterVitalsManager.snapshot().tied;
        if (oldTied != normalized) {
            String changedMsg = "[MAPAJAX_SYNC_APPLY_CHANGED] tied: " + oldTied + " -> " + normalized;
            FileLogger.trace(TAG, changedMsg);
            logAutoBlazDecision("sync", "synced_changed", normalized, threshold, "reg=" + currentRegNum);
        } else {
            FileLogger.trace(TAG, "[MAPAJAX_SYNC_APPLY_UNCHANGED] tied remained " + oldTied);
            logAutoBlazDecision("sync", "synced_unchanged", normalized, threshold, "reg=" + currentRegNum);
        }
    }

    /**
     * Применяет значения pinfo к единой витал-системе и (опционально) показывает toast синхронизации.
     *
     * Что делает:
     * - читает snapshot "до";
     * - обновляет HP/MA/усталость через `CharacterVitalsManager.updateFromPinfo(...)`;
     * - при наличии `curTire` показывает сообщение "Синхронизация Персонажа";
     * - пишет подробный trace с изменениями.
     *
     * Зависимости:
     * - `CharacterVitalsManager` (update + snapshot + buildSyncMessage);
     * - `showFatigueSyncToast(...)` для UI-уведомления;
     * - вызывается из startup-sync и near-threshold-sync.
     */
    private static void applyPinfoVitals(NeverApi.PinfoVitals vitals, String currentRegNum, int threshold, boolean startupSync) {
        if (vitals == null) {
            FileLogger.trace(TAG, "[MAPAJAX_APPLY_VITALS] vitals is null, skipping");
            return;
        }

        CharacterVitalsManager.Snapshot before = CharacterVitalsManager.snapshot();
        String syncType = startupSync ? "startup" : "near_threshold";
        String updateMsg = "[MAPAJAX_APPLY_VITALS_START] Type=" + syncType + ", reg=" + currentRegNum
                + ", tied_before=" + before.tied + ", hp_before=" + before.curHp + "/" + before.maxHp;
        FileLogger.trace(TAG, updateMsg);
        
        CharacterVitalsManager.Snapshot after = CharacterVitalsManager.updateFromPinfo(
                vitals,
                startupSync ? "MapAjax.applyPinfoVitals.startup" : "MapAjax.applyPinfoVitals.nearThreshold"
        );
        if (vitals.curTire != null) {
            showFatigueSyncToast(
                    CharacterVitalsManager.buildSyncMessage("\u0421\u0438\u043D\u0445\u0440\u0430\u043D\u0438\u0437\u0430\u0446\u0438\u044F \u041F\u0435\u0440\u0441\u043E\u043D\u0430\u0436\u0430", after)
            );
        }

        String resultMsg = "[MAPAJAX_APPLY_VITALS_END] tied: " + before.tied + " -> " + after.tied
                + ", hp: " + before.curHp + "/" + before.maxHp + " -> " + after.curHp + "/" + after.maxHp
                + ", ma: " + before.curMa + "/" + before.maxMa + " -> " + after.curMa + "/" + after.maxMa
                + ", reg=" + currentRegNum + ", threshold=" + threshold;
        AppLog.d(TAG, "AUTO_BLAZ_TRACE pinfo sync (" + syncType + "): tied=" + before.tied + "->" + after.tied
                + ", hp=" + after.curHp + "/" + after.maxHp
                + ", ma=" + after.curMa + "/" + after.maxMa
                + ", reg=" + currentRegNum
                + ", threshold=" + threshold);
        FileLogger.trace(TAG, resultMsg);
    }

    /**
     * Принимает решение о запуске автопитья блажа по текущей усталости.
     *
     * Алгоритм:
     * - проверяет профильные флаги и порог;
     * - учитывает блокировки (`FastNeed`, server never-timer, trigger cooldown);
     * - при необходимости ставит pending и откладывает действие;
     * - при достижении условий запускает `FastActionManager.fastAttackBlazElixir()`.
     *
     * Возвращает:
     * - HTML-редирект в `main.php` с диагностическим маркером, если нужен переход;
     * - `null`, если действий не требуется.
     *
     * Зависимости:
     * - `CharacterVitalsManager.snapshot().tied`;
     * - `maybeSyncTiedFromPinfoIfNearThreshold(...)`;
     * - `FastActionManager.fastAttackBlazElixir()`;
     * - `Filter.buildRedirectString(...)`.
     */
    private static String maybeTriggerAutoDrinkBlazOnThreshold(String currentRegNum) {
        if (AppVars.Profile == null || !AppVars.Profile.DoAutoDrinkBlaz) {
            AppVars.AutoDrinkBlazPending = false;
            logAutoBlazDecision("decision", "skip_profile_disabled", CharacterVitalsManager.snapshot().tied, 0, "reg=" + currentRegNum);
            String disabledMsg = "[MAPAJAX_AUTO_BLAZ] Profile disabled for auto-drink blaz";
            FileLogger.trace(TAG, disabledMsg);
            return null;
        }
        int threshold = clampPercent(AppVars.Profile.AutoDrinkBlazTied);
        int tiedBeforeSync = CharacterVitalsManager.snapshot().tied;
        if (AppVars.FastNeed) {
            // === УСИЛЕННОЕ ЛОГИРОВАНИЕ ПРИ ПРОПУСКЕ ===
            String skipMsg = "[MAPAJAX_SKIP_FASTNEED_TODAY] "
                    + "tied=" + tiedBeforeSync
                    + ", threshold=" + threshold
                    + " | FastNeed=" + AppVars.FastNeed
                    + ", FastId='" + AppVars.FastId + "'"
                    + ", FastNick='" + AppVars.FastNick + "'"
                    + ", FastCount=" + AppVars.FastCount
                    + ", reg=" + currentRegNum
                    + ", thread=" + Thread.currentThread().getId()
                    + ", timestamp=" + System.currentTimeMillis();
            AppLog.d(TAG, TAG, skipMsg);
            logAutoBlazDecision("decision", "skip_fast_need", tiedBeforeSync, threshold, "reg=" + currentRegNum + ", fastId=" + AppVars.FastId);
            return null;
        }
        String entryMsg = "[MAPAJAX_AUTO_BLAZ_CHECK] tied=" + tiedBeforeSync + ", threshold=" + threshold + ", reg=" + currentRegNum;
        FileLogger.trace(TAG, entryMsg);
        maybeSyncTiedFromPinfoIfNearThreshold(currentRegNum);
        int tied = CharacterVitalsManager.snapshot().tied;
        if (!AppVars.AutoDrinkBlazPending && tied < threshold) {
            logAutoBlazDecision("decision", "skip_below_threshold", tied, threshold, "reg=" + currentRegNum);
            return null;
        }

        long now = System.currentTimeMillis();
        long neverTimer = AppVars.NeverTimer;
        // Только откладываем через NeverTimer если tied ЕЩЁ НЕ достиг порога.
        // При tied >= threshold пить нужно сейчас; NeverTimer от навигации perpetually
        // сбрасывается каждым шагом, и если откладывать — получается бесконечный цикл:
        // MapAjax defer → resolver resolve → MapAjax defer → ...
        if (neverTimer > 0L && now < neverTimer && tied < threshold) {
            AppVars.AutoDrinkBlazPending = true;
            logAutoBlazDecision("decision", "defer_wait_never_timer", tied, threshold,
                    "reg=" + currentRegNum + ", waitMs=" + (neverTimer - now));
            return Filter.buildRedirectString(
                    "",
                    "main.php?ab_nav_blaz_wait=1");
        }

        if (tied < threshold) {
            AppVars.AutoDrinkBlazPending = false;
            logAutoBlazDecision("decision", "skip_below_threshold_after_wait", tied, threshold, "reg=" + currentRegNum);
            return null;
        }
        if ((now - lastAutoDrinkBlazTriggerAtMs) < AUTO_DRINK_BLAZ_TRIGGER_COOLDOWN_MS) {
            logAutoBlazDecision("decision", "skip_trigger_cooldown", tied, threshold,
                    "reg=" + currentRegNum + ", cooldownMs=" + (now - lastAutoDrinkBlazTriggerAtMs));
            return null;
        }
        lastAutoDrinkBlazTriggerAtMs = now;
        AppVars.AutoDrinkBlazPending = false;

        String triggerMsg = "[MAPAJAX_BLAZ_TRIGGER] EXECUTE: tied=" + tied
                + ", threshold=" + threshold
                + ", reg=" + currentRegNum
                + ", calling FastActionManager.fastAttackBlazElixir()";
        AppLog.i(TAG, TAG, triggerMsg);
        logAutoBlazDecision("decision", "trigger_fast_bliss", tied, threshold, "reg=" + currentRegNum);
        FastActionManager.fastAttackBlazElixir("Авто-Клад");
        String redirectMsg = "[MAPAJAX_BLAZ_TRIGGER] REDIRECT to main.php?ab_nav_tired=1 after fast bliss call";
        FileLogger.trace(TAG, redirectMsg);
        return Filter.buildRedirectString(
                "\u041D\u0430\u0432\u0438\u0433\u0430\u0442\u043E\u0440: \u0430\u0432\u0442\u043E\u043F\u0438\u0442\u044C\u0435 \u0431\u043B\u0430\u0436\u0430",
                "main.php?ab_nav_tired=1");
    }

    private static void logAutoBlazDecision(String stage, String action, int tied, int threshold, String details) {
        String suffix = (details == null || details.isEmpty()) ? "" : (", " + details);
        AppLog.d(TAG, "AUTO_BLAZ_DECISION: stage=" + stage
                + ", action=" + action
                + ", tied=" + tied
                + ", threshold=" + threshold
                + suffix);
    }

    private static void showFatigueSyncToast(String text) {
        final android.content.Context context = AppVars.getContext();
        if (context == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show());
    }

    private static void postAutoTreasureReasonToChat(String reason) {
        if (reason == null || reason.isEmpty()) {
            return;
        }
        android.content.Context context = AppVars.getContext();
        if (context == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastAutoTreasureReasonChatAtMs) < 2500L) {
            return;
        }
        lastAutoTreasureReasonChatAtMs = now;

        String messageHtml = LOCAL_CHAT_MARKER
                + MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#cc0000><b>" + reason + "</b></font>";
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        FileLogger.warn("auto_treasure", "reason=" + reason);
    }

    /**
     * Пишет системное уведомление в чат о перестроении обхода Auto-Клада.
     *
     * Использование:
     * - "Тщательный обход": выбран detour-кандидат;
     * - "Умная генерация": fallback-клетка отложена до достижения минимального возраста.
     *
     * Защита от дублей:
     * - общий cooldown между сообщениями;
     * - suppress одинаковой пары `настройка|клетка` на коротком окне.
     */
    private static void postAutoTreasureRouteRebuildToChat(String settingName, String detourRegNum) {
        if (settingName == null || settingName.isEmpty() || detourRegNum == null || detourRegNum.isEmpty()) {
            return;
        }
        if (!AppVars.DoSearchBox) {
            AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE route-chat skipped: DoSearchBox=false"
                    + ", setting=" + settingName + ", cell=" + detourRegNum);
            return;
        }
        if (!isAutoTreasureDetourChatEnabled()) {
            AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE route-chat skipped by setting"
                    + ", setting=" + settingName + ", cell=" + detourRegNum);
            return;
        }
        android.content.Context context = AppVars.getContext();
        if (context == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastAutoTreasureRouteChatAtMs) < AUTO_TREASURE_ROUTE_CHAT_COOLDOWN_MS) {
            return;
        }
        String key = settingName + "|" + detourRegNum;
        if (key.equals(lastAutoTreasureRouteChatKey)
                && (now - lastAutoTreasureRouteChatKeyAtMs) < AUTO_TREASURE_ROUTE_CHAT_DUPLICATE_SUPPRESS_MS) {
            return;
        }
        lastAutoTreasureRouteChatAtMs = now;
        lastAutoTreasureRouteChatKeyAtMs = now;
        lastAutoTreasureRouteChatKey = key;

        String messageHtml = LOCAL_CHAT_MARKER
                + MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#6f42c1><b>Авто-Клад:\""
                + escapeHtml(settingName)
                + "\": Дообход клетки № "
                + escapeHtml(detourRegNum)
                + "</b></font>";
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        FileLogger.trace("auto_treasure", "route_rebuild setting=" + settingName + ", detour=" + detourRegNum);
    }

    // Экранирует текст для безопасной вставки в HTML-сообщение чата.
    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static boolean shouldDelayAutoMovingStep(String currentRegNum) {
        int timeoutMs = resolveMapCellCheckTimeoutMs();
        if (timeoutMs <= 0) {
            return false;
        }
        long observedAt = lastAutoMovingCellObservedAtMs;
        if (observedAt <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        long elapsedMs = now - observedAt;
        if (elapsedMs >= timeoutMs) {
            return false;
        }
        long remainingMs = timeoutMs - elapsedMs;
        if ((now - lastMapCellCheckDelayLogAtMs) >= 350L) {
            lastMapCellCheckDelayLogAtMs = now;
            AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: hold next auto-step (configured step delay)"
                    + ", reg=" + currentRegNum
                    + ", remainingMs=" + remainingMs
                    + ", timeoutMs=" + timeoutMs);
        }
        return true;
    }

    private static int resolveMapCellCheckTimeoutMs() {
        if (AppVars.Profile == null || !AppVars.Profile.MapRebuildFromPinfo) {
            return 0;
        }
        try {
            if (AppVars.getContext() != null
                    && AutoFunctionsManager.getInstance(AppVars.getContext())
                    .shouldPauseMapRebuildFromPinfoByAutoBoss()) {
                AppLog.d(TAG, "MAP_NAME_SYNC_TRACE: skip map step delay (Auto-Boss active search stage)");
                return 0;
            }
        } catch (Exception e) {
            AppLog.w(TAG, "MAP_NAME_SYNC_TRACE: map step delay pause check failed", e);
        }
        int raw = AppVars.Profile.MapCellCheckTimeoutMs;
        if (raw < MAP_CELL_CHECK_TIMEOUT_MIN_MS) {
            return MAP_CELL_CHECK_TIMEOUT_MIN_MS;
        }
        if (raw > MAP_CELL_CHECK_TIMEOUT_MAX_MS) {
            return MAP_CELL_CHECK_TIMEOUT_MAX_MS;
        }
        return raw;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
