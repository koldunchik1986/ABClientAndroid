package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.MapPath;

/**
 * Выделенный модуль Auto-Компас.
 *
 * Назначение:
 * - хранит и исполняет полный runtime-контур поиска цели по pinfo;
 * - управляет списком кандидатных клеток, обходом, проверкой room-list;
 * - пишет диагностические и пользовательские сообщения в чат.
 *
 * Зависимости:
 * - {@link AutoFunctionsManager} как владелец API авто-функций и навигации;
 * - {@link NeverApi} для pinfo-снимка цели;
 * - {@link ExtMap}/{@link MapPath} для поиска ближайшей клетки;
 * - {@link AppVars} для текущего состояния карты, комнаты и навигатора.
 */
final class CompasAuto {
    private static final String TAG = "AutoFunctionsManager";
    private static final String KEY_AUTO_COMPASS = "auto_function_auto_compass";

    private static final String PREF_AUTO_COMPASS_TARGET_NICK = "auto_compass_target_nick";
    private static final String PREF_AUTO_COMPASS_HUNT_MODE = "auto_compass_hunt_mode";
    private static final String PREF_AUTO_COMPASS_POLL_INTERVAL_SEC = "auto_compass_poll_interval_sec";
    private static final String PREF_AUTO_COMPASS_LAST_LOCATION = "auto_compass_last_location";
    private static final String PREF_AUTO_COMPASS_LAST_REGION = "auto_compass_last_region";
    private static final String PREF_AUTO_COMPASS_CELLS_CSV = "auto_compass_cells_csv";
    private static final String PREF_AUTO_COMPASS_MANUAL_CELLS_CSV = "auto_compass_manual_cells_csv";

    private static final int AUTO_COMPASS_POLL_DEFAULT_SEC = 2;
    private static final int AUTO_COMPASS_POLL_MIN_SEC = 1;
    private static final int AUTO_COMPASS_POLL_MAX_SEC = 5;
    private static final int AUTO_COMPASS_POLL_BACKOFF_STEP_SEC = 1;
    private static final int AUTO_COMPASS_POLL_BACKOFF_MAX_EXTRA_SEC = 5;
    private static final long AUTO_COMPASS_ROOM_REFRESH_GRACE_MS = 8_000L;

    private final Context context;
    private final SharedPreferences prefs;
    private final AutoFunctionsManager owner;

    private final Object autoCompassLock = new Object();
    private final ArrayList<String> autoCompassCandidateCells = new ArrayList<>();
    private final LinkedHashSet<String> autoCompassCheckedCells = new LinkedHashSet<>();
    private final LinkedHashSet<String> autoCompassLastRoomNicks = new LinkedHashSet<>();
    private volatile long autoCompassLastTickAtMs = 0L;
    private volatile int autoCompassAdaptivePollSec = AUTO_COMPASS_POLL_DEFAULT_SEC;
    private volatile long autoCompassLastRateLimitNoticeAtMs = 0L;
    private volatile int autoCompassLastRateLimitNoticeSec = 0;
    private volatile long autoCompassLastRoomUpdateAtMs = 0L;
    private volatile boolean autoCompassPinfoInFlight = false;
    private volatile String autoCompassCurrentDestination = "";
    private volatile long autoCompassDestinationSetAtMs = 0L;
    private volatile boolean autoCompassManualSingleRun = false;
    private volatile NeverApi.PinfoCompassSnapshot autoCompassLastSnapshot = null;

    CompasAuto(Context context, SharedPreferences prefs, AutoFunctionsManager owner) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.owner = owner;
    }

    boolean isAutoCompassEnabled() {
        return prefs.getBoolean(KEY_AUTO_COMPASS, false);
    }

    void toggleAutoCompass() {
        setAutoCompassEnabled(!isAutoCompassEnabled());
    }

    void setAutoCompassEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_COMPASS, enabled).apply();
        if (enabled) {
            autoCompassAdaptivePollSec = getAutoCompassBasePollIntervalSec();
            if (!owner.isLocationTrackingEnabled()) {
                owner.setLocationTrackingEnabled(true);
            }
            if (owner.isAutoTreasureEnabled()) {
                owner.setAutoTreasureEnabled(false);
            }
            if (AppVars.AutoMoving && !isAutoCompassMovingNow()) {
                owner.stopAutoMoving();
            }
            owner.requestCharacterSyncForAutoFunctionEnableInternal("auto_compass");
            MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    try {
                        activity.requestRoomUsersRefreshSoon();
                    } catch (Exception e) {
                        Log.w(TAG, "AUTO_COMPASS_TRACE initial room refresh failed", e);
                    }
                });
            }
            autoCompassLastTickAtMs = 0L;
            tickAutoCompass(true);
        } else {
            boolean shouldStopMoving = AppVars.AutoMoving && isAutoCompassMovingNow();
            autoCompassPinfoInFlight = false;
            autoCompassManualSingleRun = false;
            autoCompassAdaptivePollSec = getAutoCompassBasePollIntervalSec();
            synchronized (autoCompassLock) {
                autoCompassCurrentDestination = "";
                autoCompassDestinationSetAtMs = 0L;
                autoCompassCandidateCells.clear();
                autoCompassCheckedCells.clear();
            }
            if (shouldStopMoving) {
                owner.stopAutoMoving();
            }
        }
        Log.d(TAG, "setAutoCompassEnabled: " + enabled);
        owner.syncBackgroundServiceInternal("setAutoCompassEnabled(" + enabled + ")");
        owner.requestQuickButtonsRefreshInternal("setAutoCompassEnabled(" + enabled + ")");
    }

    String getAutoCompassTargetNick() {
        return normalizeCompassNick(getDefaultString(PREF_AUTO_COMPASS_TARGET_NICK, ""));
    }

    void setAutoCompassTargetNick(String nick) {
        String normalized = normalizeCompassNick(nick);
        putDefaultString(PREF_AUTO_COMPASS_TARGET_NICK, normalized);
        synchronized (autoCompassLock) {
            autoCompassCurrentDestination = "";
            autoCompassDestinationSetAtMs = 0L;
            autoCompassCandidateCells.clear();
            autoCompassCheckedCells.clear();
            autoCompassLastSnapshot = null;
        }
        if (isAutoCompassEnabled()) {
            autoCompassLastTickAtMs = 0L;
            tickAutoCompass(true);
        }
    }

    boolean isAutoCompassHuntMode() {
        return getDefaultBoolean(PREF_AUTO_COMPASS_HUNT_MODE, true);
    }

    void setAutoCompassHuntMode(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_COMPASS_HUNT_MODE, enabled);
    }

    int getAutoCompassPollIntervalSec() {
        int value = getDefaultInt(PREF_AUTO_COMPASS_POLL_INTERVAL_SEC, AUTO_COMPASS_POLL_DEFAULT_SEC);
        return normalizeAutoCompassPollIntervalSec(value);
    }

    void setAutoCompassPollIntervalSec(int sec) {
        int safe = normalizeAutoCompassPollIntervalSec(sec);
        putDefaultInt(PREF_AUTO_COMPASS_POLL_INTERVAL_SEC, safe);
        autoCompassAdaptivePollSec = Math.max(autoCompassAdaptivePollSec, getAutoCompassBasePollIntervalSec());
    }

    String getAutoCompassLastLocationLabel() {
        return getDefaultString(PREF_AUTO_COMPASS_LAST_LOCATION, "");
    }

    String getAutoCompassCellsCsv() {
        return getDefaultString(PREF_AUTO_COMPASS_CELLS_CSV, "");
    }

    String getAutoCompassManualCellsCsv() {
        return getDefaultString(PREF_AUTO_COMPASS_MANUAL_CELLS_CSV, "");
    }

    void setAutoCompassManualCellsCsv(String csv) {
        List<String> normalized = normalizeCompassRegNumList(parseCompassRegNumList(csv));
        putDefaultString(PREF_AUTO_COMPASS_MANUAL_CELLS_CSV, joinCompassRegNums(normalized));
        synchronized (autoCompassLock) {
            autoCompassCurrentDestination = "";
            autoCompassDestinationSetAtMs = 0L;
            autoCompassCandidateCells.clear();
            autoCompassCheckedCells.clear();
        }
        if (isAutoCompassEnabled()) {
            autoCompassLastTickAtMs = 0L;
            tickAutoCompass(true);
        }
    }

    AutoFunctionsManager.CompassLocationResolveResult resolveAutoCompassLocation(String nick) {
        String normalized = normalizeCompassNick(nick);
        if (normalized.isEmpty()) {
            return new AutoFunctionsManager.CompassLocationResolveResult(
                    false, "", "", "", "Компас: укажите ник цели."
            );
        }

        NeverApi.PinfoCompassSnapshot snapshot;
        try {
            snapshot = NeverApi.getPinfoCompassSnapshot(normalized);
        } catch (Exception e) {
            Log.w(TAG, "resolveAutoCompassLocation: pinfo request failed, nick=" + normalized, e);
            if (NeverApi.wasLastCompassPinfoRateLimited()) {
                onAutoCompassRateLimitError(NeverApi.getLastCompassPinfoHttpStatus());
            }
            return new AutoFunctionsManager.CompassLocationResolveResult(
                    false, normalized, "", "", "Компас: pinfo не отвечает."
            );
        }

        if (snapshot == null) {
            if (NeverApi.wasLastCompassPinfoRateLimited()) {
                onAutoCompassRateLimitError(NeverApi.getLastCompassPinfoHttpStatus());
            }
            return new AutoFunctionsManager.CompassLocationResolveResult(
                    false, normalized, "", "", "Компас: pinfo не отвечает."
            );
        }
        onAutoCompassRequestSuccess();
        if (snapshot.offlineOrInvisible) {
            return new AutoFunctionsManager.CompassLocationResolveResult(
                    false, normalized, "", "", "Компас: персонаж офлайн или в невидимости."
            );
        }

        String locationName = snapshot.locationName == null ? "" : snapshot.locationName.trim();
        String locationRegion = snapshot.locationRegion == null ? "" : snapshot.locationRegion.trim();
        if (normalizeCompassLabel(locationName).isEmpty()) {
            return new AutoFunctionsManager.CompassLocationResolveResult(
                    false, normalized, "", "", "Компас: не удалось определить локацию цели."
            );
        }

        List<String> resolvedCandidates = buildCompassCandidates(locationName);
        if (resolvedCandidates.isEmpty()) {
            String label = locationRegion.isEmpty() ? locationName : (locationRegion + " [" + locationName + "]");
            return new AutoFunctionsManager.CompassLocationResolveResult(
                    false, normalized, label, "", "Компас: не найдено клеток для локации \"" + label + "\"."
            );
        }

        String cellsCsv = joinCompassRegNums(resolvedCandidates);
        String locationLabel = locationRegion.isEmpty() ? locationName : (locationRegion + " [" + locationName + "]");
        putDefaultString(PREF_AUTO_COMPASS_TARGET_NICK, normalized);
        putDefaultString(PREF_AUTO_COMPASS_LAST_LOCATION, locationName);
        if (!locationRegion.isEmpty()) {
            putDefaultString(PREF_AUTO_COMPASS_LAST_REGION, locationRegion);
        }
        putDefaultString(PREF_AUTO_COMPASS_CELLS_CSV, cellsCsv);
        synchronized (autoCompassLock) {
            autoCompassCandidateCells.clear();
            autoCompassCandidateCells.addAll(resolvedCandidates);
            autoCompassCheckedCells.clear();
        }

        Log.d(TAG, "AUTO_COMPASS_TRACE manual resolve: target=" + normalized
                + ", location=" + locationLabel + ", candidates=" + cellsCsv);
        onAutoCompassRequestSuccess();
        return new AutoFunctionsManager.CompassLocationResolveResult(
                true, normalized, locationLabel, cellsCsv, "Компас: локация обновлена."
        );
    }

    void startManualCompassSearch(String nick) {
        startManualCompassSearch(nick, "manual");
    }

    void startManualCompassSearch(String nick, String source) {
        String normalized = normalizeCompassNick(nick);
        if (normalized.isEmpty()) {
            writeCompassChat("Компас: не удалось определить ник цели.");
            return;
        }
        String normalizedSource = normalizeStartSource(source, "manual");
        Log.d(TAG, "AUTO_COMPASS_TRACE start: mode=manual_one_shot, source="
                + normalizedSource + ", target=" + normalized);
        writeCompassStartTrace("manual_one_shot", normalizedSource, normalized);
        setAutoCompassTargetNick(normalized);
        autoCompassManualSingleRun = true;
        setAutoCompassEnabled(true);
    }

    void startSettingsCompassTargetSearch(String nick) {
        startSettingsCompassTargetSearch(nick, "settings");
    }

    void startSettingsCompassTargetSearch(String nick, String source) {
        String normalized = normalizeCompassNick(nick);
        if (normalized.isEmpty()) {
            writeCompassChat("Компас: укажите ник цели.");
            return;
        }
        String normalizedSource = normalizeStartSource(source, "settings");
        Log.d(TAG, "AUTO_COMPASS_TRACE start: mode=full_hunt, source="
                + normalizedSource + ", target=" + normalized);
        writeCompassStartTrace("full_hunt", normalizedSource, normalized);
        String currentTarget = getAutoCompassTargetNick();
        if (!normalized.equalsIgnoreCase(currentTarget)) {
            setAutoCompassTargetNick(normalized);
        } else {
            putDefaultString(PREF_AUTO_COMPASS_TARGET_NICK, normalized);
        }
        autoCompassManualSingleRun = false;
        setAutoCompassHuntMode(true);
        setAutoCompassEnabled(true);
    }

    void tickAutoCompass() {
        tickAutoCompass(false);
    }

    void onRoomUsersUpdated(List<String> roomNicks, String roomLocationName) {
        LinkedHashSet<String> normalizedRoomNicks = new LinkedHashSet<>();
        if (roomNicks != null) {
            for (String nick : roomNicks) {
                String normalized = normalizeCompassNick(nick).toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    normalizedRoomNicks.add(normalized);
                }
            }
        }
        synchronized (autoCompassLock) {
            autoCompassLastRoomNicks.clear();
            autoCompassLastRoomNicks.addAll(normalizedRoomNicks);
            autoCompassLastRoomUpdateAtMs = System.currentTimeMillis();
        }
        if (!isAutoCompassEnabled()) {
            return;
        }
        String targetNick = getAutoCompassTargetNick();
        if (targetNick.isEmpty()) {
            return;
        }
        if (normalizedRoomNicks.contains(targetNick.toLowerCase(Locale.ROOT))) {
            String foundRegNum = getCurrentMapLocationRegNum();
            if (!foundRegNum.isEmpty()) {
                finishAutoCompassFound(foundRegNum);
                return;
            }
        }
        if (roomLocationName != null && !roomLocationName.trim().isEmpty()) {
            putDefaultString(PREF_AUTO_COMPASS_LAST_LOCATION, roomLocationName.trim());
        }
    }

    private int getAutoCompassBasePollIntervalSec() {
        return Math.max(getAutoCompassPollIntervalSec(), owner.getWalkersPollIntervalSec());
    }

    private int getAutoCompassEffectivePollIntervalSec() {
        int base = getAutoCompassBasePollIntervalSec();
        int adaptive = autoCompassAdaptivePollSec;
        if (adaptive < base) {
            autoCompassAdaptivePollSec = base;
            return base;
        }
        return adaptive;
    }

    private void onAutoCompassRateLimitError(int statusCode) {
        int base = getAutoCompassBasePollIntervalSec();
        int current = Math.max(base, autoCompassAdaptivePollSec);
        int maxAllowed = base + AUTO_COMPASS_POLL_BACKOFF_MAX_EXTRA_SEC;
        int bumped = Math.min(maxAllowed, current + AUTO_COMPASS_POLL_BACKOFF_STEP_SEC);
        autoCompassAdaptivePollSec = bumped;

        long now = System.currentTimeMillis();
        boolean intervalChanged = bumped != current;
        boolean notifyByTimeout = (now - autoCompassLastRateLimitNoticeAtMs) >= 15_000L;
        boolean notifyByInterval = intervalChanged || autoCompassLastRateLimitNoticeSec != bumped;
        if (notifyByTimeout || notifyByInterval) {
            autoCompassLastRateLimitNoticeAtMs = now;
            autoCompassLastRateLimitNoticeSec = bumped;
            writeCompassChat("Компас: сервер ограничил pinfo (HTTP " + statusCode
                    + "), интервал опроса увеличен до " + bumped + "с.");
        }

        if (bumped != current) {
            Log.w(TAG, "AUTO_COMPASS_TRACE adaptive poll backoff: status=" + statusCode
                    + ", baseSec=" + base
                    + ", oldSec=" + current
                    + ", newSec=" + bumped);
        }
    }

    private void onAutoCompassRequestSuccess() {
        int base = getAutoCompassBasePollIntervalSec();
        if (autoCompassAdaptivePollSec != base) {
            int oldSec = autoCompassAdaptivePollSec;
            Log.d(TAG, "AUTO_COMPASS_TRACE adaptive poll reset: oldSec="
                    + autoCompassAdaptivePollSec + ", baseSec=" + base);
            writeCompassChat("Компас: связь восстановлена, интервал опроса возвращен к "
                    + base + "с (было " + oldSec + "с).");
        }
        autoCompassAdaptivePollSec = base;
        autoCompassLastRateLimitNoticeSec = 0;
    }

    private void tickAutoCompass(boolean forceNow) {
        if (!isAutoCompassEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long minIntervalMs = Math.max(1_000L, getAutoCompassEffectivePollIntervalSec() * 1000L);
        if (!forceNow && (now - autoCompassLastTickAtMs) < minIntervalMs) {
            return;
        }
        autoCompassLastTickAtMs = now;

        final String targetNick = getAutoCompassTargetNick();
        if (targetNick.isEmpty()) {
            stopAutoCompassWithReason("Компас: укажите цель поиска.", true);
            return;
        }
        if (autoCompassPinfoInFlight) {
            return;
        }
        autoCompassPinfoInFlight = true;
        new Thread(() -> {
            NeverApi.PinfoCompassSnapshot snapshot = null;
            try {
                snapshot = NeverApi.getPinfoCompassSnapshot(targetNick);
            } catch (Exception e) {
                Log.w(TAG, "tickAutoCompass: failed to fetch pinfo snapshot for " + targetNick, e);
            } finally {
                autoCompassPinfoInFlight = false;
            }
            applyAutoCompassSnapshot(targetNick, snapshot);
        }, "auto-compass-pinfo").start();
    }

    private void applyAutoCompassSnapshot(String targetNick, NeverApi.PinfoCompassSnapshot snapshot) {
        if (!isAutoCompassEnabled()) {
            return;
        }
        if (snapshot == null) {
            int pinfoStatus = NeverApi.getLastCompassPinfoHttpStatus();
            boolean rateLimited = (pinfoStatus == 535 || pinfoStatus == 536);
            if (rateLimited) {
                onAutoCompassRateLimitError(pinfoStatus);
            }
            NeverApi.PinfoCompassSnapshot previousSnapshot;
            synchronized (autoCompassLock) {
                previousSnapshot = autoCompassLastSnapshot;
            }
            if (previousSnapshot != null && !isEmpty(previousSnapshot.locationName)) {
                Log.d(TAG, "AUTO_COMPASS_TRACE snapshot unavailable, keep last location="
                        + previousSnapshot.locationName + ", target=" + targetNick);
                continueAutoCompassNavigation(targetNick);
                return;
            }
            if (rateLimited) {
                Log.w(TAG, "AUTO_COMPASS_TRACE snapshot unavailable because rate-limit status="
                        + pinfoStatus + ", keep waiting with adaptive interval="
                        + getAutoCompassEffectivePollIntervalSec() + "s");
                return;
            }
            stopAutoCompassWithReason("Компас: цель недоступна или pinfo не отвечает.", true);
            return;
        }

        if (snapshot.offlineOrInvisible) {
            stopAutoCompassWithReason("Компас: персонаж офлайн или в невидимости.", true);
            return;
        }

        onAutoCompassRequestSuccess();
        String locationName = snapshot.locationName.trim();
        String locationRegion = snapshot.locationRegion == null ? "" : snapshot.locationRegion.trim();
        String normalizedLocation = normalizeCompassLabel(locationName);
        String normalizedRegion = normalizeCompassLabel(locationRegion);
        if (normalizedLocation.isEmpty()) {
            stopAutoCompassWithReason("Компас: не удалось определить локацию цели.", true);
            return;
        }
        putDefaultString(PREF_AUTO_COMPASS_LAST_LOCATION, locationName);
        if (!locationRegion.isEmpty()) {
            putDefaultString(PREF_AUTO_COMPASS_LAST_REGION, locationRegion);
        }

        boolean shouldRebuildCandidates;
        synchronized (autoCompassLock) {
            NeverApi.PinfoCompassSnapshot previousSnapshot = autoCompassLastSnapshot;
            boolean locationChanged = previousSnapshot == null
                    || !normalizeCompassLabel(previousSnapshot.locationName).equals(normalizedLocation);
            boolean regionChanged = previousSnapshot == null
                    || !normalizeCompassLabel(previousSnapshot.locationRegion).equals(normalizedRegion);
            boolean tiredChanged = previousSnapshot == null
                    || (snapshot.curTire != null && !snapshot.curTire.equals(previousSnapshot.curTire));
            shouldRebuildCandidates = autoCompassCandidateCells.isEmpty()
                    || locationChanged
                    || regionChanged
                    || ((locationChanged || regionChanged) && tiredChanged);
            autoCompassLastSnapshot = snapshot;
        }

        if (shouldRebuildCandidates) {
            List<String> resolvedCandidates = buildCompassCandidates(locationName);
            if (resolvedCandidates.isEmpty()) {
                String label = locationRegion.isEmpty() ? locationName : (locationRegion + " [" + locationName + "]");
                stopAutoCompassWithReason("Компас: не найдено клеток для локации \"" + label + "\".", true);
                return;
            }
            synchronized (autoCompassLock) {
                autoCompassCandidateCells.clear();
                autoCompassCandidateCells.addAll(resolvedCandidates);
                autoCompassCheckedCells.clear();
                autoCompassCurrentDestination = "";
                autoCompassDestinationSetAtMs = 0L;
            }
            putDefaultString(PREF_AUTO_COMPASS_CELLS_CSV, joinCompassRegNums(resolvedCandidates));
            Log.d(TAG, "AUTO_COMPASS_TRACE candidates rebuilt: region=" + locationRegion
                    + ", location=" + locationName
                    + ", count=" + resolvedCandidates.size()
                    + ", cells=" + joinCompassRegNums(resolvedCandidates));
        }

        continueAutoCompassNavigation(targetNick);
    }

    private void continueAutoCompassNavigation(String targetNick) {
        if (!isAutoCompassEnabled()) {
            return;
        }
        String currentRegNum = getCurrentMapLocationRegNum();
        if (currentRegNum.isEmpty()) {
            owner.requestCharacterSyncForAutoFunctionEnableInternal("auto_compass_wait_map_location");
            MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    try {
                        activity.requestRoomUsersRefreshSoon();
                    } catch (Exception e) {
                        Log.w(TAG, "AUTO_COMPASS_TRACE room refresh failed while waiting map location", e);
                    }
                });
            }
            Log.d(TAG, "AUTO_COMPASS_TRACE waiting map location: target=" + targetNick);
            return;
        }

        if (isTargetPresentInLatestRoom(targetNick)) {
            finishAutoCompassFound(currentRegNum);
            return;
        }

        List<String> candidatesSnapshot;
        Set<String> checkedSnapshot;
        String currentDestination;
        long currentDestinationSetAtMs;
        synchronized (autoCompassLock) {
            candidatesSnapshot = new ArrayList<>(autoCompassCandidateCells);
            checkedSnapshot = new LinkedHashSet<>(autoCompassCheckedCells);
            currentDestination = autoCompassCurrentDestination;
            currentDestinationSetAtMs = autoCompassDestinationSetAtMs;
        }
        if (candidatesSnapshot.isEmpty()) {
            stopAutoCompassWithReason("Компас: список клеток пуст, поиск остановлен.", true);
            return;
        }

        boolean huntAll = shouldAutoCompassHuntAllCells();
        if (!currentDestination.isEmpty()
                && currentRegNum.equals(currentDestination)
                && !AppVars.AutoMoving) {
            boolean hasFreshRoomAfterArrival;
            synchronized (autoCompassLock) {
                hasFreshRoomAfterArrival = autoCompassLastRoomUpdateAtMs >= currentDestinationSetAtMs;
            }
            if (!hasFreshRoomAfterArrival) {
                MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        try {
                            activity.requestRoomUsersRefreshSoon();
                        } catch (Exception e) {
                            Log.w(TAG, "AUTO_COMPASS_TRACE request room refresh failed after arrival", e);
                        }
                    });
                }
                long waitMs = System.currentTimeMillis() - currentDestinationSetAtMs;
                if (waitMs < AUTO_COMPASS_ROOM_REFRESH_GRACE_MS) {
                    Log.d(TAG, "AUTO_COMPASS_TRACE waiting room refresh after arrival: destination="
                            + currentDestination + ", waitedMs=" + waitMs);
                    return;
                }
                Log.w(TAG, "AUTO_COMPASS_TRACE room refresh timeout after arrival: destination="
                        + currentDestination + ", waitedMs=" + waitMs);
            }
            synchronized (autoCompassLock) {
                autoCompassCheckedCells.add(currentDestination);
                autoCompassCurrentDestination = "";
                autoCompassDestinationSetAtMs = 0L;
            }
            if (!huntAll) {
                stopAutoCompassWithReason("Компас: цель не найдена на ближайшей клетке.", true);
                return;
            }
            if (isTargetPresentInLatestRoom(targetNick)) {
                finishAutoCompassFound(currentRegNum);
                return;
            }
        }

        if (!currentDestination.isEmpty()
                && AppVars.AutoMoving
                && currentDestination.equals(AppVars.AutoMovingDestinaton)) {
            return;
        }

        String nextDestination = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            List<String> pending = new ArrayList<>();
            for (String regNum : candidatesSnapshot) {
                if (!checkedSnapshot.contains(regNum)) {
                    pending.add(regNum);
                }
            }
            if (pending.isEmpty()) {
                stopAutoCompassWithReason("Компас: цель не найдена, все клетки проверены.", true);
                return;
            }
            nextDestination = chooseNearestCompassCell(currentRegNum, pending);
            if (nextDestination.isEmpty()) {
                stopAutoCompassWithReason("Компас: не удалось выбрать следующую клетку.", true);
                return;
            }
            if (!nextDestination.equals(currentRegNum)) {
                break;
            }
            checkedSnapshot.add(nextDestination);
            synchronized (autoCompassLock) {
                autoCompassCheckedCells.add(nextDestination);
            }
            if (!huntAll) {
                stopAutoCompassWithReason("Компас: цель не найдена на ближайшей клетке.", true);
                return;
            }
            nextDestination = "";
        }

        if (nextDestination.isEmpty()) {
            return;
        }

        if (AppVars.DoSearchBox && owner.isAutoTreasureEnabled()) {
            owner.setAutoTreasureEnabled(false);
        }
        if (AppVars.AutoMoving && !nextDestination.equals(AppVars.AutoMovingDestinaton)) {
            owner.stopAutoMoving();
        }
        synchronized (autoCompassLock) {
            autoCompassCurrentDestination = nextDestination;
            autoCompassDestinationSetAtMs = System.currentTimeMillis();
        }
        if (!nextDestination.equals(AppVars.AutoMovingDestinaton) || !AppVars.AutoMoving) {
            owner.startAutoMoving(nextDestination);
            writeCompassMoveChat(nextDestination, targetNick, candidatesSnapshot);
        }
    }

    private List<String> buildCompassCandidates(String locationName) {
        List<String> manualCells = normalizeCompassRegNumList(parseCompassRegNumList(getAutoCompassManualCellsCsv()));
        if (!manualCells.isEmpty()) {
            return manualCells;
        }

        String normalizedTarget = normalizeCompassLabel(locationName);
        if (normalizedTarget.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> exactByName = new LinkedHashSet<>();
        LinkedHashSet<String> fallbackByTooltip = new LinkedHashSet<>();
        for (CellEntry entry : collectCellEntries()) {
            String normalizedName = normalizeCompassLabel(entry.name);
            if (normalizedTarget.equals(normalizedName)) {
                exactByName.add(entry.regNum);
                continue;
            }
            String normalizedTooltip = normalizeCompassLabel(entry.tooltip);
            if (normalizedTarget.equals(normalizedTooltip)) {
                fallbackByTooltip.add(entry.regNum);
            }
        }
        ArrayList<String> result = new ArrayList<>(exactByName.size() + fallbackByTooltip.size());
        result.addAll(exactByName);
        for (String regNum : fallbackByTooltip) {
            if (!exactByName.contains(regNum)) {
                result.add(regNum);
            }
        }
        return normalizeCompassRegNumList(result);
    }

    private List<CellEntry> collectCellEntries() {
        ArrayList<CellEntry> entries = new ArrayList<>();
        for (java.util.Map.Entry<String, ru.neverlands.abclient.model.Cell> mapEntry : ExtMap.Cells.entrySet()) {
            if (mapEntry.getValue() == null) {
                continue;
            }
            entries.add(new CellEntry(mapEntry.getKey(), mapEntry.getValue().Name, mapEntry.getValue().Tooltip));
        }
        entries.sort((left, right) -> left.regNum.compareTo(right.regNum));
        return entries;
    }

    private String chooseNearestCompassCell(String currentRegNum, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        if (currentRegNum != null && candidates.contains(currentRegNum)) {
            return currentRegNum;
        }
        if (currentRegNum == null || currentRegNum.isEmpty()) {
            return candidates.get(0);
        }
        try {
            MapPath path = new MapPath(currentRegNum, candidates);
            if (path.pathExists && path.destination != null && !path.destination.trim().isEmpty()) {
                return path.destination.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "chooseNearestCompassCell: path build failed, current=" + currentRegNum, e);
        }
        return candidates.get(0);
    }

    private boolean isTargetPresentInLatestRoom(String targetNick) {
        if (targetNick == null || targetNick.trim().isEmpty()) {
            return false;
        }
        String key = targetNick.trim().toLowerCase(Locale.ROOT);
        synchronized (autoCompassLock) {
            if (System.currentTimeMillis() - autoCompassLastRoomUpdateAtMs > 20_000L) {
                return false;
            }
            return autoCompassLastRoomNicks.contains(key);
        }
    }

    private String getCurrentMapLocationRegNum() {
        if (AppVars.Profile == null || AppVars.Profile.MapLocation == null) {
            return "";
        }
        String value = AppVars.Profile.MapLocation.trim();
        return value.isEmpty() ? "" : value;
    }

    private boolean shouldAutoCompassHuntAllCells() {
        return isAutoCompassHuntMode() && !autoCompassManualSingleRun;
    }

    private boolean isAutoCompassMovingNow() {
        String destination = autoCompassCurrentDestination;
        return destination != null
                && !destination.isEmpty()
                && AppVars.AutoMoving
                && destination.equals(AppVars.AutoMovingDestinaton);
    }

    private void finishAutoCompassFound(String regNum) {
        String safeReg = regNum == null ? "" : regNum.trim();
        String message = safeReg.isEmpty()
                ? "Компас: игрок найден."
                : "Компас: Игрок найден на клетке №" + safeReg + ".";
        stopAutoCompassWithReason(message, false);
    }

    private void stopAutoCompassWithReason(String message, boolean keepEnabledWhenManualStop) {
        writeCompassChat(message);
        autoCompassManualSingleRun = false;
        if (!keepEnabledWhenManualStop) {
            setAutoCompassEnabled(false);
            return;
        }
        if (isAutoCompassEnabled()) {
            boolean shouldStopMoving = AppVars.AutoMoving && isAutoCompassMovingNow();
            prefs.edit().putBoolean(KEY_AUTO_COMPASS, false).apply();
            synchronized (autoCompassLock) {
                autoCompassCurrentDestination = "";
                autoCompassDestinationSetAtMs = 0L;
                autoCompassCandidateCells.clear();
                autoCompassCheckedCells.clear();
            }
            if (shouldStopMoving) {
                owner.stopAutoMoving();
            }
            owner.syncBackgroundServiceInternal("autoCompassStopReason");
            owner.requestQuickButtonsRefreshInternal("autoCompassStopReason");
        }
    }

    private void writeCompassChat(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        String html = "<font color=#5D7C91><b>[Компас]</b></font> " + escapeHtml(message);
        FastActionManager.writeChatMsg(html);
    }

    private void writeCompassStartTrace(String mode, String source, String targetNick) {
        String safeMode = mode == null ? "" : mode.trim();
        String safeSource = source == null ? "" : source.trim();
        String safeTarget = targetNick == null ? "" : targetNick.trim();
        writeCompassChat("Старт: режим=" + safeMode
                + ", источник=" + safeSource
                + ", цель=" + safeTarget + ".");
    }

    private void writeCompassMoveChat(String nextDestination, String targetNick, List<String> candidatesSnapshot) {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<font color=#5D7C91><b>[Компас]</b></font> ");
        htmlBuilder.append("Двигаемся к клетке №")
                .append(escapeHtml(nextDestination))
                .append(" (Цель: ")
                .append(escapeHtml(targetNick))
                .append("). ");
        htmlBuilder.append("Возможные клетки: ")
                .append(formatCompassCellsLinks(candidatesSnapshot));
        FastActionManager.writeChatMsg(htmlBuilder.toString());
    }

    private String normalizeCompassNick(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String normalizeCompassLabel(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeStartSource(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        return normalized.replace('\u00A0', ' ').replaceAll("\\s+", "_");
    }

    private int normalizeAutoCompassPollIntervalSec(int sec) {
        if (sec < AUTO_COMPASS_POLL_MIN_SEC) {
            return AUTO_COMPASS_POLL_MIN_SEC;
        }
        if (sec > AUTO_COMPASS_POLL_MAX_SEC) {
            return AUTO_COMPASS_POLL_MAX_SEC;
        }
        if (sec == 1 || sec == 2 || sec == 5) {
            return sec;
        }
        return AUTO_COMPASS_POLL_DEFAULT_SEC;
    }

    private List<String> parseCompassRegNumList(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] tokens = csv.split("[,;\\s]+");
        ArrayList<String> result = new ArrayList<>();
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String value = token.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private List<String> normalizeCompassRegNumList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (ExtMap.Cells.containsKey(normalized)) {
                unique.add(normalized);
            }
        }
        return new ArrayList<>(unique);
    }

    private String joinCompassRegNums(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private String formatCompassCellsLinks(List<String> regNums) {
        if (regNums == null || regNums.isEmpty()) {
            return "<font color=#999999>нет</font>";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String regNum : regNums) {
            if (regNum == null) {
                continue;
            }
            String value = regNum.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!first) {
                builder.append(", ");
            }
            String safeValue = escapeHtml(value);
            if (value.matches("\\d{1,4}-\\d{1,5}")) {
                builder.append("<a style='text-decoration:none' href='abmove://")
                        .append(safeValue)
                        .append("'><b>")
                        .append(safeValue)
                        .append("</b></a>");
            } else {
                builder.append(safeValue);
            }
            first = false;
        }
        return builder.length() == 0 ? "<font color=#999999>нет</font>" : builder.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private SharedPreferences defaultPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private boolean getDefaultBoolean(String key, boolean fallback) {
        try {
            return defaultPrefs().getBoolean(key, fallback);
        } catch (Exception e) {
            Log.w(TAG, "getDefaultBoolean failed: key=" + key, e);
            return fallback;
        }
    }

    private void putDefaultBoolean(String key, boolean value) {
        try {
            defaultPrefs().edit().putBoolean(key, value).apply();
        } catch (Exception e) {
            Log.w(TAG, "putDefaultBoolean failed: key=" + key + ", value=" + value, e);
        }
    }

    private String getDefaultString(String key, String fallback) {
        try {
            String value = defaultPrefs().getString(key, fallback);
            return value == null ? fallback : value;
        } catch (Exception e) {
            Log.w(TAG, "getDefaultString failed: key=" + key, e);
            return fallback;
        }
    }

    private void putDefaultString(String key, String value) {
        try {
            defaultPrefs().edit().putString(key, value).apply();
        } catch (Exception e) {
            Log.w(TAG, "putDefaultString failed: key=" + key + ", value=" + value, e);
        }
    }

    private int getDefaultInt(String key, int fallback) {
        try {
            return defaultPrefs().getInt(key, fallback);
        } catch (Exception e) {
            Log.w(TAG, "getDefaultInt failed: key=" + key, e);
            return fallback;
        }
    }

    private void putDefaultInt(String key, int value) {
        try {
            defaultPrefs().edit().putInt(key, value).apply();
        } catch (Exception e) {
            Log.w(TAG, "putDefaultInt failed: key=" + key + ", value=" + value, e);
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class CellEntry {
        final String regNum;
        final String name;
        final String tooltip;

        CellEntry(String regNum, String name, String tooltip) {
            this.regNum = regNum == null ? "" : regNum.trim();
            this.name = name == null ? "" : name;
            this.tooltip = tooltip == null ? "" : tooltip;
        }
    }
}
