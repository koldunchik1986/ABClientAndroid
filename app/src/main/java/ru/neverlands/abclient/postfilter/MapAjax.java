package ru.neverlands.abclient.postfilter;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.manager.CharacterVitalsManager;
import ru.neverlands.abclient.manager.FastActionManager;
import ru.neverlands.abclient.manager.NeverApi;
import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.model.Position;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.MapPath;
import android.util.Log;

public class MapAjax {
    private static final String TAG = "MapAjax";
    private static final long SEARCH_BOX_VISITED_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final int AUTO_MOVING_TIED_STEP_COST = 2;
    private static final int AUTO_DRINK_BLAZ_NEAR_THRESHOLD_DELTA = 6;
    private static final long AUTO_DRINK_BLAZ_PINFO_SYNC_COOLDOWN_MS = 20_000L;
    private static final long AUTO_DRINK_BLAZ_STARTUP_SYNC_RETRY_COOLDOWN_MS = 10_000L;
    private static final long AUTO_DRINK_BLAZ_TRIGGER_COOLDOWN_MS = 6_000L;
    private static final long AUTO_DRINK_BLAZ_POST_TRIGGER_STICKY_MS = 8_000L;
    private static volatile long lastAutoDrinkBlazPinfoSyncAtMs = 0L;
    private static volatile long lastAutoDrinkBlazStartupSyncAttemptAtMs = 0L;
    private static volatile long lastAutoDrinkBlazTriggerAtMs = 0L;
    private static volatile boolean autoDrinkBlazStartupSyncDone = false;

    public static String process(String html) {
        if (AppVars.FastNeed && AppVars.FastPauseNonCombatAutoFunctions) {
            if (AppVars.AutoMoving || AppVars.DoSearchBox) {
                Log.d(TAG, "AUTO_FAST_PAUSE_TRACE: skip map auto processing while fast action is active"
                        + ", fastId=" + AppVars.FastId);
            }
            return html;
        }

        if (AppVars.TreasureDigPauseNonCombatAutoFunctions) {
            if (AppVars.AutoMoving || AppVars.DoSearchBox) {
                Log.d(TAG, "AUTO_SEARCH_BOX_TRACE: skip map auto processing while treasure dig preparation is active");
            }
            return html;
        }

        if (!AppVars.AutoMoving || !AppVars.DoSearchBox) {
            autoDrinkBlazStartupSyncDone = false;
            lastAutoDrinkBlazStartupSyncAttemptAtMs = 0L;
        }

        if (isMapAjaxErrResponse(html) && AppVars.AutoMoving) {
            AppVars.AutoMovingMapPath = null;
            AppVars.AutoMovingNextJump = null;
            AppVars.AutoMovingJumps = 0;
            Log.w(TAG, "AUTO_SEARCH_BOX_TRACE: map_ajax returned ERR during automove, redirect to main.php");
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
                Log.d(TAG, "AUTO_BLAZ_TRACE keep tied from pinfo after trigger: tied="
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
                Log.w(TAG, "AUTO_SEARCH_BOX_TRACE: too tired, auto moving stopped (DoAutoDrinkBlaz=false)");
            } else {
                String currentRegNum = (AppVars.Profile != null) ? AppVars.Profile.MapLocation : null;
                String autoDrinkRedirect = maybeTriggerAutoDrinkBlazOnThreshold(currentRegNum);
                if (autoDrinkRedirect != null) {
                    Log.i(TAG, "AUTO_SEARCH_BOX_TRACE: too tired, auto moving paused, redirect to main.php for auto bliss");
                    return autoDrinkRedirect;
                }
                Log.d(TAG, "AUTO_SEARCH_BOX_TRACE: too tired, auto bliss gate skipped"
                        + ", tied=" + tiedNow + ", threshold=" + tiedThreshold
                        + ", fastNeed=" + AppVars.FastNeed);
            }
            return Filter.buildRedirectString(
                    "\u041D\u0430\u0432\u0438\u0433\u0430\u0442\u043E\u0440: \u0443\u0441\u0442\u0430\u043B\u043E\u0441\u0442\u044C",
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
                Log.d(TAG, "AUTO_CURE_TRACE: skip map auto processing while cure pipeline is active"
                        + ", cureNeed=" + AppVars.CureNeed
                        + ", cureNick=" + AppVars.CureNick
                        + ", cureTravm=" + AppVars.CureTravm);
            }
            return html;
        }

        // При отложенном автопитье блажа временно не делаем шаги маршрута,
        // но сохраняем AutoMoving-контекст для последующего продолжения.
        if (AppVars.AutoDrinkBlazPending) {
            Log.d(TAG, "AUTO_BLAZ_DECISION: stage=pause, action=skip_move_while_pending, reg="
                    + (AppVars.Profile != null ? AppVars.Profile.MapLocation : "?"));
            return html;
        }

        if (!AppVars.AutoMoving) return html;

        String mapLocation = AppVars.Profile != null ? AppVars.Profile.MapLocation : null;
        if (mapLocation == null) return html;

        if (AppVars.AutoMovingDestinaton == null || AppVars.AutoMovingDestinaton.isEmpty()) {
            Log.w(TAG, "AUTO_MOVING_TRACE: destination is empty while AutoMoving=true");
            return html;
        }

        if (mapLocation.equals(AppVars.AutoMovingDestinaton)) {
            if (!AppVars.DoSearchBox) {
                AppVars.AutoMoving = false;
                AppVars.AutoMovingMapPath = null;
                AppVars.AutoMovingNextJump = null;
                AppVars.AutoMovingJumps = 0;
                android.util.Log.i(TAG, "AutoMoving: destination reached at " + mapLocation);
                return html;
            }

            String nextSearchDestination = findNextDestForBox(mapLocation);
            if (nextSearchDestination == null || nextSearchDestination.isEmpty()) {
                Log.i(TAG, "AUTO_SEARCH_BOX_TRACE: no next destination from " + mapLocation);
                return html;
            }

            AppVars.AutoMovingDestinaton = nextSearchDestination;
            AppVars.AutoMovingMapPath = null;
            AppVars.AutoMovingNextJump = null;
            AppVars.AutoMovingJumps = 0;
            Log.i(TAG, "AUTO_SEARCH_BOX_TRACE: rotate destination to " + nextSearchDestination);
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

        int[] idx = new int[] {0, 0, -1, 1, -1, 1, -1, 1};
        int[] idy = new int[] {-1, 1, 0, 0, -1, -1, 1, 1};

        Set<String> visited = new HashSet<>();
        ArrayDeque<String> frontier = new ArrayDeque<>();
        visited.add(source);
        frontier.add(source);

        long nowMs = System.currentTimeMillis();
        String oldestVisitedFallback = null;
        long oldestVisitedAtMs = Long.MAX_VALUE;
        while (!frontier.isEmpty()) {
            int batch = frontier.size();
            for (int k = 0; k < batch; k++) {
                String current = frontier.poll();
                if (current == null || current.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < idx.length; i++) {
                    String next = moveMapCell(current, idx[i], idy[i]);
                    if (next == null || next.isEmpty() || visited.contains(next)) {
                        continue;
                    }
                    visited.add(next);
                    frontier.add(next);

                    if (isSearchBoxCandidate(next, nowMs)) {
                        return next;
                    }
                    Long visitedAt = AppVars.SearchBoxVisited.get(next);
                    if (visitedAt != null
                            && visitedAt > 0L
                            && !next.equals(source)
                            && visitedAt < oldestVisitedAtMs) {
                        oldestVisitedAtMs = visitedAt;
                        oldestVisitedFallback = next;
                    }
                }
            }
        }
        if (oldestVisitedFallback != null && !oldestVisitedFallback.isEmpty()) {
            long ageMs = Math.max(0L, nowMs - oldestVisitedAtMs);
            Log.d(TAG, "AUTO_SEARCH_BOX_TRACE: fallback oldest-visited destination="
                    + oldestVisitedFallback + ", ageMs=" + ageMs);
            return oldestVisitedFallback;
        }
        return null;
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
                Log.d(TAG, "AUTO_SEARCH_BOX_TRACE fixed-cell: move to target " + fixedRegNum + " from " + sourceRegNum);
                return fixedRegNum;
            }

            int[] idx = new int[] {0, 0, -1, 1, -1, 1, -1, 1};
            int[] idy = new int[] {-1, 1, 0, 0, -1, -1, 1, 1};
            for (int i = 0; i < idx.length; i++) {
                String neighbor = moveMapCell(fixedRegNum, idx[i], idy[i]);
                if (neighbor == null || neighbor.isEmpty() || neighbor.equals(fixedRegNum)) {
                    continue;
                }
                Log.d(TAG, "AUTO_SEARCH_BOX_TRACE fixed-cell: no dig marker yet, hop to neighbor "
                        + neighbor + " and return to " + fixedRegNum);
                return neighbor;
            }
        } catch (Exception e) {
            Log.w(TAG, "AUTO_SEARCH_BOX_TRACE fixed-cell destination failed", e);
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
            return;
        }
        if (AppVars.Profile == null) {
            return;
        }
        String nick = AppVars.Profile.UserNick;
        if (nick == null || nick.trim().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastAutoDrinkBlazStartupSyncAttemptAtMs) < AUTO_DRINK_BLAZ_STARTUP_SYNC_RETRY_COOLDOWN_MS) {
            return;
        }
        lastAutoDrinkBlazStartupSyncAttemptAtMs = now;

        int threshold = clampPercent(AppVars.Profile.AutoDrinkBlazTied);
        NeverApi.PinfoVitals vitals = NeverApi.getPinfoVitalsFromPinfo(nick);
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
            return;
        }
        if (previousRegNum.equals(currentRegNum)) {
            return;
        }
        int oldTied = CharacterVitalsManager.snapshot().tied;
        CharacterVitalsManager.Snapshot stepped = CharacterVitalsManager.increaseTied(
                AUTO_MOVING_TIED_STEP_COST,
                "MapAjax.onAutoMovingCellObserved.step"
        );
        int newTied = stepped.tied;
        if (newTied != oldTied) {
            Log.d(TAG, "AUTO_BLAZ_TRACE tied +step: old=" + oldTied
                    + ", new=" + newTied
                    + ", stepCost=" + AUTO_MOVING_TIED_STEP_COST
                    + ", from=" + previousRegNum
                    + ", to=" + currentRegNum);
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
            return;
        }
        int threshold = clampPercent(AppVars.Profile.AutoDrinkBlazTied);
        int tied = CharacterVitalsManager.snapshot().tied;
        int syncBorder = Math.max(0, threshold - AUTO_DRINK_BLAZ_NEAR_THRESHOLD_DELTA);
        if (tied < syncBorder) {
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastAutoDrinkBlazPinfoSyncAtMs) < AUTO_DRINK_BLAZ_PINFO_SYNC_COOLDOWN_MS) {
            return;
        }
        String nick = AppVars.Profile.UserNick;
        if (nick == null || nick.trim().isEmpty()) {
            return;
        }
        NeverApi.PinfoVitals synced = NeverApi.getPinfoVitalsFromPinfo(nick);
        if (synced == null || synced.curTire == null) {
            logAutoBlazDecision("sync", "skip_sync_failed", tied, threshold, "reg=" + currentRegNum);
            return;
        }
        lastAutoDrinkBlazPinfoSyncAtMs = now;
        int oldTied = CharacterVitalsManager.snapshot().tied;
        applyPinfoVitals(synced, currentRegNum, threshold, false);
        int normalized = CharacterVitalsManager.snapshot().tied;
        if (oldTied != normalized) {
            logAutoBlazDecision("sync", "synced_changed", normalized, threshold, "reg=" + currentRegNum);
        } else {
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
            return;
        }

        CharacterVitalsManager.Snapshot before = CharacterVitalsManager.snapshot();
        CharacterVitalsManager.Snapshot after = CharacterVitalsManager.updateFromPinfo(
                vitals,
                startupSync ? "MapAjax.applyPinfoVitals.startup" : "MapAjax.applyPinfoVitals.nearThreshold"
        );
        if (vitals.curTire != null) {
            showFatigueSyncToast(
                    CharacterVitalsManager.buildSyncMessage("\u0421\u0438\u043D\u0445\u0440\u0430\u043D\u0438\u0437\u0430\u0446\u0438\u044F \u041F\u0435\u0440\u0441\u043E\u043D\u0430\u0436\u0430", after)
            );
        }

        String syncType = startupSync ? "startup" : "near_threshold";
        Log.d(TAG, "AUTO_BLAZ_TRACE pinfo sync (" + syncType + "): tied=" + before.tied + "->" + after.tied
                + ", hp=" + after.curHp + "/" + after.maxHp
                + ", ma=" + after.curMa + "/" + after.maxMa
                + ", reg=" + currentRegNum
                + ", threshold=" + threshold);
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
            return null;
        }
        int threshold = clampPercent(AppVars.Profile.AutoDrinkBlazTied);
        int tiedBeforeSync = CharacterVitalsManager.snapshot().tied;
        if (AppVars.FastNeed) {
            logAutoBlazDecision("decision", "skip_fast_need", tiedBeforeSync, threshold, "reg=" + currentRegNum + ", fastId=" + AppVars.FastId);
            return null;
        }
        maybeSyncTiedFromPinfoIfNearThreshold(currentRegNum);
        int tied = CharacterVitalsManager.snapshot().tied;
        if (!AppVars.AutoDrinkBlazPending && tied < threshold) {
            logAutoBlazDecision("decision", "skip_below_threshold", tied, threshold, "reg=" + currentRegNum);
            return null;
        }

        long now = System.currentTimeMillis();
        long neverTimer = AppVars.NeverTimer;
        if (neverTimer > 0L && now < neverTimer) {
            AppVars.AutoDrinkBlazPending = true;
            logAutoBlazDecision("decision", "defer_wait_never_timer", tied, threshold,
                    "reg=" + currentRegNum + ", waitMs=" + (neverTimer - now));
            return Filter.buildRedirectString(
                    "Навигатор: ожидание шага перед автопитьем блажа",
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

        Log.i(TAG, "AUTO_BLAZ_TRACE threshold reached in map_ajax: tied=" + tied
                + ", threshold=" + threshold
                + ", reg=" + currentRegNum
                + ", trigger fast bliss");
        logAutoBlazDecision("decision", "trigger_fast_bliss", tied, threshold, "reg=" + currentRegNum);
        FastActionManager.fastAttackBlazElixir();
        return Filter.buildRedirectString(
                "\u041D\u0430\u0432\u0438\u0433\u0430\u0442\u043E\u0440: \u0430\u0432\u0442\u043E\u043F\u0438\u0442\u044C\u0435 \u0431\u043B\u0430\u0436\u0430",
                "main.php?ab_nav_tired=1");
    }

    private static void logAutoBlazDecision(String stage, String action, int tied, int threshold, String details) {
        String suffix = (details == null || details.isEmpty()) ? "" : (", " + details);
        Log.d(TAG, "AUTO_BLAZ_DECISION: stage=" + stage
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

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
