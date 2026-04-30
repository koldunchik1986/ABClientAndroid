package ru.neverlands.anclient.manager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.MainActivity;
import ru.neverlands.anclient.model.AppTimer;
import ru.neverlands.anclient.model.ParsedDressed;
import ru.neverlands.anclient.postfilter.AlchemyAjaxPhp;
import ru.neverlands.anclient.postfilter.MainPhp;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.ChatStats;
import ru.neverlands.anclient.utils.SessionManager;

/**
 * Runtime и настройки Авто-Травника.
 *
 * Контур дополняет уже существующий флаг `AUTO_CUT` в {@link AutoFunctionsManager}:
 * этот класс не включает/выключает функцию, а хранит выбранные травы, клетки поиска
 * и ставит таймеры после подтвержденного `alchemy_ajax.php?act=3`.
 *
 * Основные зависимости:
 * - `AlchemyAjaxPhp` вызывает `registerObservedHerb(...)`, `markHerbCut(...)` и sickle guards;
 * - `WebAppInterface.DoHerbAutoCut()` читает `shouldAutoLookOnCurrentCell()` перед JS `Ogl(...)`;
 * - `AutoCutHandler` обслуживает main.php-подготовку серпа и cleanup по флагам AppVars;
 * - `AutoFunctionsManager` владеет license-gated флагом `AUTO_CUT` и вызывает
 *   `onAutoCutEnabled(...)`/`onAutoCutDisabled()`;
 * - `AppTimerManager` получает herb timers после успешного среза.
 */
public final class AutoCutManager {
    /** Logcat/FileLogger tag текущего manager-а. */
    private static final String TAG = "AutoCutManager";
    /** Chain-name для критичных файловых логов AutoCut. */
    public static final String TRACE_CHAIN = "AUTO_CUT_TRACE";
    /** Серверное название мусорного предмета, который случайно падает при срезе трав. */
    public static final String GARBAGE_ITEM_NAME = "Бесполезный хлам";

    /** SharedPreferences-файл с настройками AutoCut, scoped per nick через `scopedKey(...)`. */
    private static final String PREFS_NAME = "auto_cut_prefs";
    /** JSON-словарь трав: id/name/skill/growth/group/selected. */
    private static final String KEY_HERBS_JSON_PREFIX = "herbs_json_";
    /** CSV списка клеток обхода в формате `x-y`. */
    private static final String KEY_CELLS_CSV_PREFIX = "cells_csv_";
    /** Флаг вывода сообщения о срезе в локальный чат. */
    private static final String KEY_WRITE_CHAT_PREFIX = "write_chat_";
    /** Флаг включения cleanup-прохода после прироста массы. */
    private static final String KEY_CLEANUP_ENABLED_PREFIX = "cleanup_enabled_";
    /** Флаг маршрута по herb timer-ам: идти на клетку только после due-time. */
    private static final String KEY_CUT_BY_TIMERS_PREFIX = "cut_by_timers_";
    /** JSON выбранных серпов для авто-надевания. */
    private static final String KEY_SICKLES_JSON_PREFIX = "sickles_json_";
    /** JSON расписания смен трав. */
    private static final String KEY_SHIFTS_JSON_PREFIX = "shifts_json_";
    /** Список уже проверенных клеток для текущего server-window смены трав. */
    private static final String KEY_CHECKED_SHIFT_PREFIX = "checked_shift_";
    /** Snapshot последних `HerbsList`/`RESO@` по клеткам, нужен для skip known-empty route. */
    private static final String KEY_CELL_SNAPSHOTS_JSON_PREFIX = "cell_snapshots_json_";
    /** Группа для неизвестных трав, найденных в live `RESO@`. */
    private static final String GROUP_UNKNOWN = "Не определено";
    /** Безопасное время роста для новых/неизвестных трав. */
    private static final int DEFAULT_GROWTH_MINUTES = 60;
    /** TTL JS trace `TraceCut(...)`, если success `act=3` пришёл без pending DTO. */
    private static final long TRACE_CUT_NAME_TTL_MS = 60_000L;
    /** Небольшая задержка перед стартом route, чтобы WebView завершил текущий ajax/update кадр. */
    private static final long ROUTE_NEXT_DELAY_MS = 450L;
    /** Fallback-delay для повторного `Оглядеться`, если сервер не прислал `SetNeverTimer(...)`. */
    private static final long AUTO_CUT_RETRY_FALLBACK_DELAY_MS = 1500L;
    /** Пауза перед новым кругом, когда все выбранные травы по cache пустые в текущей смене. */
    private static final long CURRENT_CELL_NO_SELECTED_RETRY_MS = 30_000L;
    /** Минимальная пауза перед повторным маршрутом AutoCut после server `too tired`. */
    private static final long TIRED_ROUTE_RETRY_MIN_DELAY_MS = 60_000L;
    /** Дедуп запроса main.php/inventory для массы, чтобы при пустом HTML не зациклить срез. */
    private static final long MASS_SYNC_REQUEST_DEDUP_MS = 30_000L;
    /** Fallback cleanup threshold, если `SetAutoFishMassa` ещё не дал max mass. */
    private static final double CLEANUP_FALLBACK_THRESHOLD_MASS = 10d;
    /** Запас к времени роста травы: AutoCut идёт по timer-у только через growth + 5 минут. */
    private static final long HERB_TIMER_EXTRA_DELAY_MINUTES = 5L;
    /** Формат строки смены: `00:50-06:50`, одна смена на строку. */
    private static final Pattern SHIFT_LINE_PATTERN = Pattern.compile("(\\d{1,2})[:\\-](\\d{1,2})\\s*[-–—]\\s*(\\d{1,2})[:\\-](\\d{1,2})");
    /** Формат строки массы на main.php/inventory: `Масса Вашего инвентаря: current/max`. */
    private static final Pattern INVENTORY_MASS_PATTERN = Pattern.compile(
            "Масса\\s+Вашего\\s+инвентаря:\\s*([0-9]+(?:[\\.,][0-9]+)?\\s*/\\s*[0-9]+(?:[\\.,][0-9]+)?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** RegNum внутри описания herb timer-а: `Вырастет <herb> на <regNum>`. */
    private static final Pattern HERB_TIMER_CELL_PATTERN = Pattern.compile("\\b(\\d{1,4}-\\d{1,5})\\b");
    /** Singleton manager-а на application context. */
    private static AutoCutManager instance;

    /** Application context для prefs, broadcasts и AppTimerManager. */
    private final Context appContext;
    /** Scoped persisted settings хранилище AutoCut. */
    private final SharedPreferences prefs;
    /** Последняя трава из JS `TraceCut`, volatile из-за вызовов из WebView bridge. */
    private volatile String lastTraceCutName = "";
    /** Время последнего `TraceCut`, нужно для TTL stale-защиты. */
    private volatile long lastTraceCutAtMs = 0L;
    /** One-shot retry `Оглядеться` после wrong captcha или частичного среза клетки. */
    private volatile boolean lookRetryPending = false;
    /** Последний источник retry для диагностики AUTO_CUT_TRACE. */
    private volatile String lookRetrySource = "";
    /** Локальное время постановки retry, чтобы видеть stale-зависания в логах. */
    private volatile long lookRetryRequestedAtMs = 0L;
    /** One-shot запрос inventory/main.php для получения `Масса Вашего инвентаря: current/max` перед срезом. */
    private volatile boolean massSnapshotSyncPending = false;
    /** Последний запрос sync массы; защищает от бесконечного go=inf/inventory loop при пустом ответе. */
    private volatile long lastMassSnapshotSyncRequestAtMs = 0L;
    /** Snapshot функций, временно выключенных на время AutoCut cleanup. */
    private volatile CleanupPauseSnapshot cleanupPauseSnapshot = null;
    /** Клетка, с которой AutoCut ушёл на due herb timer. */
    private volatile String timerRouteReturnCell = "";
    /** Due timer-клетка, после обработки которой нужно вернуться на `timerRouteReturnCell`. */
    private volatile String timerRouteTargetCell = "";
    /** true, когда AutoCut уже возвращается после timer-route и ждёт прибытия на исходную клетку. */
    private volatile boolean timerRouteReturning = false;
    /** true, когда AutoCut ждёт повторного старта маршрута после server `too tired`. */
    private volatile boolean tiredRouteRetryPending = false;
    /** Последняя цель AutoCut route, остановленная сервером из-за усталости. */
    private volatile String tiredRouteRetryDestination = "";

    private AutoCutManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Возвращает singleton AutoCutManager для application context. */
    public static synchronized AutoCutManager getInstance(Context context) {
        if (instance == null) {
            instance = new AutoCutManager(context);
        }
        return instance;
    }

    /** Persisted CSV клеток обхода, как ввёл пользователь в настройках. */
    public synchronized String getCellsCsv() {
        return prefs.getString(scopedKey(KEY_CELLS_CSV_PREFIX), "");
    }

    /** Нормализует и сохраняет CSV клеток, удаляя дубли/мусорные токены. */
    public synchronized void setCellsCsv(String csv) {
        prefs.edit().putString(scopedKey(KEY_CELLS_CSV_PREFIX), normalizeCellsCsv(csv)).apply();
    }

    /** Возвращает нормализованный список клеток для route logic. */
    public synchronized List<String> getSearchCells() {
        return parseCellsCsv(getCellsCsv());
    }

    /** true, если после успешного среза нужно писать результат в чат. */
    public synchronized boolean isWriteChatEnabled() {
        return prefs.getBoolean(scopedKey(KEY_WRITE_CHAT_PREFIX), true);
    }

    /** Сохраняет флаг chat-report для текущего nick. */
    public synchronized void setWriteChatEnabled(boolean enabled) {
        prefs.edit().putBoolean(scopedKey(KEY_WRITE_CHAT_PREFIX), enabled).apply();
    }

    /** true, если включён inventory cleanup после прироста массы. */
    public synchronized boolean isCleanupEnabled() {
        return prefs.getBoolean(scopedKey(KEY_CLEANUP_ENABLED_PREFIX), false);
    }

    /** Сохраняет cleanup-флаг для текущего nick. */
    public synchronized void setCleanupEnabled(boolean enabled) {
        prefs.edit().putBoolean(scopedKey(KEY_CLEANUP_ENABLED_PREFIX), enabled).apply();
    }

    /** true, если AutoCut должен ходить на клетки только по сработавшим herb timer-ам. */
    public synchronized boolean isCutByTimersEnabled() {
        return prefs.getBoolean(scopedKey(KEY_CUT_BY_TIMERS_PREFIX), false);
    }

    /** Сохраняет режим `Срезать по таймерам` для текущего nick. */
    public synchronized void setCutByTimersEnabled(boolean enabled) {
        prefs.edit().putBoolean(scopedKey(KEY_CUT_BY_TIMERS_PREFIX), enabled).apply();
        if (!enabled) {
            clearTimerRouteState("cut_by_timers_disabled");
        }
        AppLog.i(TRACE_CHAIN, TAG, "cut by timers setting saved: enabled=" + enabled);
    }

    /** Доступные серпы в порядке приоритета авто-надевания. */
    public String[] getAvailableSickleNames() {
        return ParsedDressed.getAutoCutSickleNames();
    }

    /** Persisted список разрешённых серпов; пустое значение трактуется как дефолтный список. */
    public synchronized List<String> getEnabledSickleNames() {
        String raw = prefs.getString(scopedKey(KEY_SICKLES_JSON_PREFIX), "");
        ArrayList<String> result = new ArrayList<>();
        if (!TextUtils.isEmpty(raw)) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    String name = safe(array.optString(i, ""));
                    if (!name.isEmpty()) {
                        result.add(name);
                    }
                }
            } catch (Exception error) {
                AppLog.w(TRACE_CHAIN, TAG, "failed to parse sickles json, fallback to defaults", error);
                result.clear();
            }
        }
        if (result.isEmpty()) {
            for (String name : getAvailableSickleNames()) {
                result.add(name);
            }
        }
        return result;
    }

    /** Сохраняет список серпов, которые можно надевать автоматически. */
    public synchronized void setEnabledSickleNames(Set<String> selectedNames) {
        Set<String> safeSelected = selectedNames == null ? new LinkedHashSet<>() : selectedNames;
        JSONArray array = new JSONArray();
        for (String name : getAvailableSickleNames()) {
            if (safeSelected.contains(name)) {
                array.put(name);
            }
        }
        prefs.edit().putString(scopedKey(KEY_SICKLES_JSON_PREFIX), array.toString()).apply();
        AppLog.i(TRACE_CHAIN, TAG, "sickle settings saved: count=" + array.length());
    }

    /** Возвращает расписание смен трав как редактируемый многострочный текст. */
    public synchronized String getShiftScheduleText() {
        StringBuilder builder = new StringBuilder();
        for (AutoCutShift shift : loadShiftsLocked()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(shift.displayRange());
        }
        return builder.toString();
    }

    /** Сохраняет пользовательское расписание смен трав. Возвращает false, если ни одна строка не распознана. */
    public synchronized boolean setShiftScheduleText(String text) {
        List<AutoCutShift> parsed = parseShiftScheduleText(text);
        if (parsed.isEmpty()) {
            return false;
        }
        persistShiftsLocked(parsed);
        AppLog.i(TRACE_CHAIN, TAG, "shift schedule saved: count=" + parsed.size());
        return true;
    }

    /** Сбрасывает расписание смен к известному серверному дефолту. */
    public synchronized void resetShiftScheduleToDefault() {
        persistShiftsLocked(defaultShifts());
        AppLog.i(TRACE_CHAIN, TAG, "shift schedule reset to default");
    }

    /** Возвращает UI-список трав с seed+live entries. */
    public synchronized List<AutoCutHerb> getHerbs() {
        return new ArrayList<>(loadHerbsLocked().values());
    }

    /** Количество выбранных трав; guard для `DoHerbAutoCut()`. */
    public synchronized int getSelectedHerbCount() {
        int count = 0;
        for (AutoCutHerb herb : loadHerbsLocked().values()) {
            if (herb.selected) {
                count++;
            }
        }
        return count;
    }

    /** Проверяет, выбрана ли трава по id или имени из live `RESO@`. */
    public synchronized boolean isHerbSelected(String id, String name) {
        AutoCutHerb herb = findHerbLocked(loadHerbsLocked(), id, name);
        return herb != null && herb.selected;
    }

    /** Сохраняет checkbox selections из настроек AutoCut. */
    public synchronized void setHerbSelections(Set<String> selectedKeys) {
        Set<String> safeKeys = selectedKeys == null ? new LinkedHashSet<>() : selectedKeys;
        LinkedHashMap<String, AutoCutHerb> herbs = loadHerbsLocked();
        LinkedHashMap<String, AutoCutHerb> updated = new LinkedHashMap<>();
        for (Map.Entry<String, AutoCutHerb> entry : herbs.entrySet()) {
            AutoCutHerb herb = entry.getValue();
            boolean selected = safeKeys.contains(herb.key);
            updated.put(entry.getKey(), herb.withSelected(selected));
        }
        persistHerbsLocked(updated);
        AppLog.i(TRACE_CHAIN, TAG, "settings saved: selectedCount=" + safeKeys.size());
    }

    /** Обновляет metadata травы из long-press UI: skill, growth minutes, group. */
    public synchronized void updateHerbMeta(String key, int skill, int growthMinutes, String group) {
        if (TextUtils.isEmpty(key)) {
            return;
        }
        LinkedHashMap<String, AutoCutHerb> herbs = loadHerbsLocked();
        AutoCutHerb current = herbs.get(key);
        if (current == null) {
            return;
        }
        int safeGrowth = growthMinutes > 0 ? growthMinutes : current.growthMinutes;
        String safeGroup = TextUtils.isEmpty(group) ? GROUP_UNKNOWN : group.trim();
        herbs.put(key, current.withMeta(Math.max(0, skill), safeGrowth, safeGroup));
        persistHerbsLocked(herbs);
        AppLog.i(TRACE_CHAIN, TAG, "herb meta updated: key=" + key
                + ", skill=" + skill + ", growth=" + safeGrowth + ", group=" + safeGroup);
    }

    /**
     * Добавляет или обновляет траву, увиденную в JS `HerbsList(...)` или `RESO@`.
     *
     * Переменные:
     * - `id` — приоритетный ключ, если сервер прислал `res_id`;
     * - `name` — fallback-ключ и UI label;
     * - `skill/growthMinutes/group` — metadata из seed/ручной правки/live parse.
     * Существующий флаг `selected` сохраняется, чтобы live discovery не сбрасывал настройки.
     */
    public synchronized void registerObservedHerb(String id, String name, int skill, int growthMinutes, String group) {
        String safeName = safe(name);
        if (safeName.isEmpty()) {
            return;
        }
        LinkedHashMap<String, AutoCutHerb> herbs = loadHerbsLocked();
        AutoCutHerb existing = findHerbLocked(herbs, id, safeName);
        String safeId = safeNumeric(id);
        String key = buildKey(safeId, safeName);
        if (existing != null) {
            herbs.remove(existing.key);
            int mergedSkill = existing.skill > 0 ? existing.skill : Math.max(0, skill);
            int mergedGrowth = existing.growthMinutes > 0
                    ? existing.growthMinutes
                    : normalizeGrowthMinutes(growthMinutes);
            String mergedGroup = GROUP_UNKNOWN.equals(existing.group) && !TextUtils.isEmpty(group)
                    ? group.trim()
                    : existing.group;
            herbs.put(key, new AutoCutHerb(key, safeId, safeName, mergedSkill, mergedGrowth, mergedGroup, existing.selected));
        } else {
            herbs.put(key, new AutoCutHerb(
                    key,
                    safeId,
                    safeName,
                    Math.max(0, skill),
                    normalizeGrowthMinutes(growthMinutes),
                    TextUtils.isEmpty(group) ? GROUP_UNKNOWN : group.trim(),
                    false));
        }
        persistHerbsLocked(herbs);
    }

    /**
     * Bridge callback `WebAppInterface.HerbsList(...)` из map.js.
     * Формат `list`: элементы через `|`, имя может идти до `:`; метод только обновляет словарь,
     * не запускает срез и не открывает captcha.
     */
    public void onHerbsList(String list) {
        if (list == null || list.trim().isEmpty()) {
            return;
        }
        String[] entries = list.split("\\|");
        int count = 0;
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            int sep = entry.indexOf(':');
            String name = sep >= 0 ? entry.substring(0, sep) : entry;
            if (!safe(name).isEmpty()) {
                registerObservedHerb("", name, 0, DEFAULT_GROWTH_MINUTES, GROUP_UNKNOWN);
                count++;
            }
        }
        updateCurrentCellSnapshot(list, "herbs_list");
        AppLog.d(TRACE_CHAIN, TAG, "HerbsList observed count=" + count);
    }

    /**
     * Сохраняет snapshot трав текущей клетки для C#-parity `HerbCells` route skip.
     * Формат `resourcesList`: `Название:count|...`; unknown/stale snapshot или snapshot прошлой
     * server-shift window не блокирует маршрут.
     */
    public synchronized void updateCurrentCellSnapshot(String resourcesList, String source) {
        List<CellHerbEntry> entries = parseCellSnapshotEntries(resourcesList);
        if (entries.isEmpty()) {
            return;
        }
        String current = resolveCurrentRegNum();
        int shift = getShiftForServerMs(getServerNowMs(System.currentTimeMillis()));
        if (TextUtils.isEmpty(current) || shift == 0) {
            return;
        }
        try {
            JSONObject snapshots = loadCellSnapshotsLocked();
            JSONArray herbs = new JSONArray();
            for (CellHerbEntry entry : entries) {
                JSONObject item = new JSONObject();
                item.put("name", entry.name);
                item.put("count", Math.max(0, entry.count));
                herbs.put(item);
            }
            JSONObject snapshot = new JSONObject();
            snapshot.put("shift", shift);
            snapshot.put("updatedAtMs", System.currentTimeMillis());
            snapshot.put("herbs", herbs);
            snapshots.put(current, snapshot);
            prefs.edit().putString(scopedKey(KEY_CELL_SNAPSHOTS_JSON_PREFIX), snapshots.toString()).apply();
            AppLog.d(TRACE_CHAIN, TAG, "cell snapshot updated: cell=" + current
                    + ", entries=" + entries.size() + ", shift=" + shift + ", source=" + source);
        } catch (Exception error) {
            AppLog.w(TRACE_CHAIN, TAG, "failed to persist cell snapshot, source=" + source, error);
        }
    }

    /**
     * Bridge callback `TraceCut/HerbCut`: запоминает имя травы как fallback для success `act=3`.
     * TTL защищает от stale trace после перезагрузки карты или ручного действия пользователя.
     */
    public void onTraceCut(String herb) {
        String safeHerb = safe(herb);
        if (safeHerb.isEmpty()) {
            return;
        }
        lastTraceCutName = safeHerb;
        lastTraceCutAtMs = System.currentTimeMillis();
        AppLog.d(TRACE_CHAIN, TAG, "TraceCut observed: " + safeHerb);
    }

    /** Возвращает и очищает свежий JS trace, если он не старше `TRACE_CUT_NAME_TTL_MS`. */
    public String consumeRecentTraceCutName() {
        long now = System.currentTimeMillis();
        String name = lastTraceCutName;
        if (name.isEmpty() || now - lastTraceCutAtMs > TRACE_CUT_NAME_TTL_MS) {
            return "";
        }
        lastTraceCutName = "";
        lastTraceCutAtMs = 0L;
        return name;
    }

    /** Упрощённая overload без массы, используется fallback-ветками. */
    public void markHerbCut(String id, String name, int growthMinutes, String regNum, String source) {
        markHerbCut(id, name, growthMinutes, regNum, source, 0d);
    }

    /** Упрощённая overload без snapshot-а клетки, используется legacy/fallback-ветками. */
    public void markHerbCut(String id, String name, int growthMinutes, String regNum, String source, double resourceMass) {
        markHerbCut(id, name, growthMinutes, regNum, source, resourceMass, "");
    }

    /** Упрощённая overload без флага повторного `Оглядеться`, используется legacy/fallback-ветками. */
    public void markHerbCut(String id,
                            String name,
                            int growthMinutes,
                            String regNum,
                            String source,
                            double resourceMass,
                            String cellResourcesSummary) {
        markHerbCut(id, name, growthMinutes, regNum, source, resourceMass, cellResourcesSummary, false);
    }

    /**
     * Финализирует успешный срез.
     *
     * Действия:
     * - ставит herb timer через `AppTimerManager`, если смена трав не слишком близко;
     * - пишет chat-report, если включён `write_chat_*`;
     * - помечает текущую клетку checked для текущей смены;
     * - добавляет массу в cleanup accumulator и либо открывает inventory cleanup, либо запускает route.
     */
    public void markHerbCut(String id,
                            String name,
                            int growthMinutes,
                            String regNum,
                            String source,
                            double resourceMass,
                            String cellResourcesSummary,
                            boolean retrySameCellAfterCut) {
        String safeName = safe(name);
        if (safeName.isEmpty()) {
            return;
        }
        registerObservedHerb(id, safeName, 0, growthMinutes, GROUP_UNKNOWN);
        AutoCutHerb herb;
        synchronized (this) {
            herb = findHerbLocked(loadHerbsLocked(), id, safeName);
        }
        int growth = herb != null ? herb.growthMinutes : normalizeGrowthMinutes(growthMinutes);
        String safeRegNum = TextUtils.isEmpty(regNum) ? resolveCurrentRegNum() : regNum.trim();
        TimerPlan timerPlan = buildTimerPlan(safeName, growth, safeRegNum);
        String massSnapshot = updateInventoryMassAfterCut(resourceMass);
        if (timerPlan.shouldCreateTimer) {
            AppTimer timer = new AppTimer();
            timer.description = "Вырастет " + safeName + " на " + safeRegNum;
            timer.triggerTime = timerPlan.triggerAtMs;
            timer.isHerb = true;
            AppTimerManager.getInstance(appContext).addAppTimer(timer);
        }
        ChatStats.addHerbCut(safeName);
        if (isWriteChatEnabled()) {
            postCutResultToChat(safeName, timerPlan, safeRegNum, cellResourcesSummary, massSnapshot, resourceMass, source);
        }
        boolean cleanupPending = AppVars.AutoCutCleanupPending;
        if (!cleanupPending) {
            cleanupPending = maybeRequestCleanupAfterCut(resourceMass, source);
        }
        if (retrySameCellAfterCut) {
            scheduleLookRetryAfterTimer("multi_cut:" + source);
        } else {
            markCurrentCellChecked("cut_success:" + source);
        }
        AppLog.i(TRACE_CHAIN, TAG, "cut success: herb=" + safeName
                + ", regNum=" + safeRegNum
                + ", growth=" + growth
                + ", timer=" + timerPlan.shouldCreateTimer
                + ", cleanupPending=" + cleanupPending
                + ", retrySameCell=" + retrySameCellAfterCut
                + ", source=" + source);
        if (!cleanupPending) {
            if (retrySameCellAfterCut) {
                AppLog.i(TRACE_CHAIN, TAG, "stay on current cell for next selected herb, source=" + source);
            } else if (routeBackToTimerReturnIfNeeded("cut_success:" + source)) {
                AppLog.i(TRACE_CHAIN, TAG, "timer-route cut completed, returning before CSV continuation, source=" + source);
            } else {
                routeNextCell("cut_success:" + source);
            }
        }
    }

    /**
     * Guard для `WebAppInterface.DoHerbAutoCut()` перед автоматическим JS `Ogl(...)`.
     * Возвращает false, если уже идёт навигация, проверка серпа, cleanup, нет выбранных трав,
     * текущая клетка не входит в CSV или уже проверена в текущую смену без due herb timer-а.
     */
    public boolean shouldAutoLookOnCurrentCell() {
        if (AppVars.AutoMoving || AppVars.AutoCutCheckSickle || AppVars.AutoCutCleanupPending) {
            return false;
        }
        if (getSelectedHerbCount() <= 0) {
            return false;
        }
        String current = resolveCurrentRegNum();
        List<String> cells = getSearchCells();
        if (!cells.isEmpty() && (current.isEmpty() || !cells.contains(current))) {
            return false;
        }
        if (hasDueHerbTimerForCurrentShift(current)) {
            return true;
        }
        if (!shouldRouteToUncheckedCell(current, "current_cell")) {
            return false;
        }
        return !isCellCheckedForCurrentShift(current);
    }

    /**
     * Продолжает обычный CSV-обход после возврата с timer-route на исходную клетку.
     * Вызывается из bridge, когда карта уже перерисовалась на return-cell и `DoHerbAutoCut()`
     * видит, что сама return-cell не требует повторного `Оглядеться`.
     */
    public boolean routeNextAfterTimerReturnIfArrived(AutoFunctionsManager manager, String source) {
        String returnCell = safe(timerRouteReturnCell);
        if (!timerRouteReturning || TextUtils.isEmpty(returnCell)) {
            return false;
        }
        String current = resolveCurrentRegNum();
        if (!returnCell.equals(current)) {
            return false;
        }
        clearTimerRouteState("timer_return_arrived:" + source);
        AppLog.i(TRACE_CHAIN, TAG, "timer-route returned to source cell, continue CSV route: cell="
                + current + ", source=" + source);
        routeNextCellWithManager(manager, "timer_return_arrived:" + source);
        return true;
    }

    /** Запускает route, если текущая unchecked-клетка по snapshot-у не содержит выбранных трав. */
    public boolean routeNextIfCurrentCellCachedNotReady(AutoFunctionsManager manager, String source) {
        if (manager == null || shouldDelayRouteForPreparation()) {
            return false;
        }
        List<String> cells = getSearchCells();
        String current = resolveCurrentRegNum();
        if (cells.isEmpty()
                || TextUtils.isEmpty(current)
                || !cells.contains(current)
                || isCellCheckedForCurrentShift(current)
                || hasDueHerbTimerForCurrentShift(current)) {
            return false;
        }
        if (shouldRouteToUncheckedCell(current, "current_cell_route")) {
            return false;
        }
        routeNextCellWithManager(manager, "cache_skip:" + source);
        return true;
    }

    /** true, если остановленный AutoMoving destination принадлежит текущему CSV-маршруту AutoCut. */
    public boolean isAutoCutRouteDestination(String destination) {
        if (TextUtils.isEmpty(destination)) {
            return false;
        }
        return getSearchCells().contains(destination.trim());
    }

    /**
     * Сохраняет AutoCut живым, когда общий MapAjax останавливает AutoMoving из-за усталости.
     * Повторный старт идёт через существующий `startAutoMoving(...)`, без второго HTTP-контура;
     * при retry заново выбирается актуальная due/unchecked клетка.
     */
    public boolean scheduleRouteRetryAfterTiredness(AutoFunctionsManager manager,
                                                   String destination,
                                                   int tiedNow,
                                                   int tiedThreshold,
                                                   String source) {
        if (manager == null || !isAutoCutRouteDestination(destination)) {
            return false;
        }
        String safeDestination = destination.trim();
        synchronized (this) {
            if (tiredRouteRetryPending && safeDestination.equals(tiredRouteRetryDestination)) {
                AppLog.d(TRACE_CHAIN, TAG, "tired route retry already pending: destination="
                        + safeDestination + ", source=" + safe(source));
                return true;
            }
            tiredRouteRetryPending = true;
            tiredRouteRetryDestination = safeDestination;
        }
        long retryDelayMs = calculateTiredRouteRetryDelayMs(tiedNow, tiedThreshold);
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> runTiredRouteRetry(source),
                retryDelayMs);
        AppLog.w(TRACE_CHAIN, TAG, "auto-moving stopped by tiredness, route retry scheduled: destination="
                + safeDestination + ", tied=" + tiedNow + ", threshold=" + tiedThreshold
                + ", delayMs=" + retryDelayMs + ", source=" + safe(source));
        return true;
    }

    private long calculateTiredRouteRetryDelayMs(int tiedNow, int tiedThreshold) {
        int tied = Math.max(0, Math.min(100, tiedNow));
        int threshold = Math.max(0, Math.min(100, tiedThreshold));
        long minutesUntilBelowThreshold = Math.max(1L, (long) tied - threshold + 1L);
        return Math.max(TIRED_ROUTE_RETRY_MIN_DELAY_MS, minutesUntilBelowThreshold * 60_000L);
    }

    private void runTiredRouteRetry(String source) {
        String destination;
        synchronized (this) {
            if (!tiredRouteRetryPending) {
                return;
            }
            tiredRouteRetryPending = false;
            destination = tiredRouteRetryDestination;
            tiredRouteRetryDestination = "";
        }
        try {
            AutoFunctionsManager manager = AutoFunctionsManager.getInstance(appContext);
            if (!manager.isAutoCutEnabled()) {
                AppLog.i(TRACE_CHAIN, TAG, "tired route retry cancelled: AutoCut disabled, destination="
                        + safe(destination) + ", source=" + safe(source));
                return;
            }
            if (AppVars.AutoMoving) {
                AppLog.i(TRACE_CHAIN, TAG, "tired route retry skipped: navigator already active, destination="
                        + safe(destination) + ", source=" + safe(source));
                return;
            }
            AppLog.i(TRACE_CHAIN, TAG, "tired route retry fired: previousDestination="
                    + safe(destination) + ", source=" + safe(source));
            routeNextCellWithManager(manager, "tired_retry:" + source);
        } catch (Exception error) {
            AppLog.w(TRACE_CHAIN, TAG, "tired route retry failed, destination="
                    + safe(destination) + ", source=" + safe(source), error);
        }
    }

    /**
     * Runtime bootstrap после license-gated включения `AUTO_CUT`.
     * Сбрасывает старые флаги, требует проверку серпа, reload-ит main frame и при необходимости
     * стартует маршрут к первой непроверенной CSV-клетке.
     */
    public void onAutoCutEnabled(AutoFunctionsManager manager) {
        AppVars.AutoCutCheckSickle = true;
        AppVars.AutoCutArmedSickle = false;
        AppVars.AutoCutSickleHand = "";
        AppVars.AutoCutSickleHandD = "";
        AppVars.AutoCutCleanupPending = false;
        AppVars.AutoCutCleanupReason = "";
        AppVars.AutoFishMassa = "";
        AppVars.AutoCutKnownMassMax = 0d;
        massSnapshotSyncPending = false;
        lastMassSnapshotSyncRequestAtMs = 0L;
        clearTimerRouteState("auto_cut_enabled");
        requestMainFrameReload("enabled_sickle_check");
        routeNextCellIfCurrentIsNotReady(manager, "enabled");
    }

    /**
     * Продолжает cold-start маршрут после main.php-подготовки, если она не принадлежала pending cut.
     * Сохраняет единственный route-контур через `startAutoMoving(...)` и повторно применяет guard
     * текущей клетки, чтобы не увести пользователя с ready-клетки до `Оглядеться`.
     */
    public void continueRouteAfterPreparationIfIdle(String source) {
        if (AlchemyAjaxPhp.hasPendingCutForRouteGuard()) {
            AppLog.d(TRACE_CHAIN, TAG, "preparation completed: pending cut owns resume, source=" + safe(source));
            return;
        }
        if (!AppVars.AutoCutArmedSickle || AppVars.AutoCutCheckSickle) {
            AppLog.d(TRACE_CHAIN, TAG, "preparation completed: route bootstrap skipped, source="
                    + safe(source) + ", armed=" + AppVars.AutoCutArmedSickle
                    + ", checkSickle=" + AppVars.AutoCutCheckSickle);
            return;
        }
        try {
            AutoFunctionsManager manager = AutoFunctionsManager.getInstance(appContext);
            AppLog.i(TRACE_CHAIN, TAG, "preparation completed: continue route bootstrap, source="
                    + safe(source) + ", current=" + safe(resolveCurrentRegNum()));
            routeNextCellIfCurrentIsNotReady(manager, "preparation_completed:" + source);
        } catch (Exception e) {
            AppLog.w(TRACE_CHAIN, TAG, "preparation route bootstrap failed, source=" + safe(source), e);
        }
    }

    /** Сброс runtime-флагов при ручном выключении или license downgrade/expiry. */
    public void onAutoCutDisabled() {
        restoreAutosPausedForCleanup("auto_cut_disabled");
        AppVars.AutoCutCheckSickle = false;
        AppVars.AutoCutArmedSickle = false;
        AppVars.AutoCutSickleHand = "";
        AppVars.AutoCutSickleHandD = "";
        AppVars.AutoCutCleanupPending = false;
        AppVars.AutoCutCleanupReason = "";
        AppVars.AutoCutHarvestedMassSinceCleanup = 0d;
        massSnapshotSyncPending = false;
        lastMassSnapshotSyncRequestAtMs = 0L;
        clearTimerRouteState("auto_cut_disabled");
        synchronized (this) {
            tiredRouteRetryPending = false;
            tiredRouteRetryDestination = "";
        }
        AppVars.BulkDropThing = "";
        AppVars.BulkDropPrice = "";
    }

    /** true, если `act=3` можно отправлять без риска среза голыми руками/без инструмента. */
    public boolean isSickleReadyForCut() {
        return AppVars.AutoCutArmedSickle && !AppVars.AutoCutCheckSickle;
    }

    /** true, если есть пригодный snapshot `current/max` для chat-report и cleanup-порога. */
    public boolean hasUsableMassSnapshot() {
        return parseMassSnapshot(AppVars.AutoFishMassa).max > 0d;
    }

    /** true, если перед `act=3` нужно один раз сходить в main.php/inventory за `current/max`. */
    public boolean needsMassSnapshotBeforeCut() {
        if (hasUsableMassSnapshot() || massSnapshotSyncPending) {
            return false;
        }
        long now = System.currentTimeMillis();
        return now - lastMassSnapshotSyncRequestAtMs >= MASS_SYNC_REQUEST_DEDUP_MS;
    }

    /** Флаг обслуживает `AutoCutHandler` в существующем main.php/inventory pipeline. */
    public boolean isMassSnapshotSyncPending() {
        return massSnapshotSyncPending;
    }

    /** Сбрасывает one-shot mass-sync после успешного парсинга или inventory fail-safe pass. */
    public void clearMassSnapshotSyncPending(String source) {
        if (!massSnapshotSyncPending) {
            return;
        }
        massSnapshotSyncPending = false;
        AppLog.d(TRACE_CHAIN, TAG, "mass snapshot sync cleared, source=" + source
                + ", mass=" + safe(AppVars.AutoFishMassa));
    }

    /**
     * Запрашивает штатный main.php/inventory проход перед срезом, если `current/max` ещё не известен.
     * Использует существующий `AutoCutCheckSickle` guard, чтобы map.js не отправил `act=3` параллельно.
     * При этом не сбрасывает `AutoCutArmedSickle`: mass-sync вызывается только после уже пройденной
     * проверки серпа, а сброс переводит надетый серп в ошибочную ветку auto-wear inventory.
     */
    public void requestMassSnapshotBeforeCut(String source) {
        massSnapshotSyncPending = true;
        lastMassSnapshotSyncRequestAtMs = System.currentTimeMillis();
        AppVars.AutoCutCheckSickle = true;
        AppLog.i(TRACE_CHAIN, TAG, "mass snapshot requested before cut, source=" + source);
        requestMainFrameReload("mass_snapshot:" + source);
    }

    /**
     * Запрашивает main.php-проверку серпа, когда `act=1` уже нашёл выбранную доступную траву.
     *
     * Зависимости и порядок:
     * - вызывается только из `AlchemyAjaxPhp.processAlchemyAct1(...)`, когда ресурс уже выбран,
     *   но `AppVars.AutoCutArmedSickle` ещё не подтверждён текущим main.php/inventory HTML;
     * - выставляет `AutoCutCheckSickle=true`, чтобы `AutoCutHandler.processMainPhpAutoCutStep(...)`
     *   взял управление в существующем main.php postfilter-контуре и не создавал второй HTTP-поток;
     * - сбрасывает `AutoCutArmedSickle`, потому что между клетками пользователь мог снять/сломать серп;
     * - после `requestMainFrameReload(...)` возврат на карту выполняется через `AutoCutHandler`.
     */
    public void requestSickleCheckBeforeCut(String source) {
        AppVars.AutoCutCheckSickle = true;
        AppVars.AutoCutArmedSickle = false;
        AppLog.i(TRACE_CHAIN, TAG, "sickle check requested before cut, source=" + source);
        requestMainFrameReload("sickle_check:" + source);
    }

    /**
     * Помечает клетку checked и маршрутизирует дальше, если `act=1` не нашёл выбранных доступных трав.
     *
     * Зависимости:
     * - вызывается из `AlchemyAjaxPhp.processAlchemyAct1(...)` после полного разбора `RESO@`;
     * - checked-set scoped по текущей серверной смене трав через `loadCheckedCellsLocked()`;
     * - переход дальше делегируется `routeNextCell(...)`, который учитывает due herb timers и круговой обход.
     */
    public void onScanWithoutSelectedHerb(String source) {
        markCurrentCellChecked("no_selected:" + source);
        if (routeBackToTimerReturnIfNeeded("no_selected:" + source)) {
            return;
        }
        routeNextCell("no_selected:" + source);
    }

    /**
     * Планирует повторное `Оглядеться` после неверной captcha, не помечая клетку checked.
     *
     * Зависимости:
     * - вызывается только для актуального pending cut из `AlchemyAjaxPhp.processAlchemyAct3(...)`;
     * - использует общий `NeverTimer`, чтобы retry не конкурировал с серверным cooldown;
     * - фактический reload делает `MainActivity.checkServerTimerDrivenActions()` через `go=ret&an_auto_cut_tick=1`.
     */
    public void onCutCaptchaRejected(String source) {
        scheduleLookRetryAfterTimer("wrong_captcha:" + source);
    }

    /**
     * Ставит one-shot retry текущей клетки после истечения общего `NeverTimer`.
     *
     * Назначение:
     * - используется для wrong captcha и multi-cut, когда клетку нельзя помечать checked;
     * - не вызывает WebView напрямую, а только записывает state, потому что единственный владелец
     *   server-timer reload-а — `MainActivity.checkServerTimerDrivenActions()`;
     * - если сервер не прислал `NeverTimer`, ставит короткий fallback, чтобы retry не завис навсегда.
     */
    public synchronized void scheduleLookRetryAfterTimer(String source) {
        long now = System.currentTimeMillis();
        lookRetryPending = true;
        lookRetrySource = safe(source);
        lookRetryRequestedAtMs = now;
        if (AppVars.NeverTimer <= now) {
            AppVars.NeverTimer = now + AUTO_CUT_RETRY_FALLBACK_DELAY_MS;
        }
        AppLog.i(TRACE_CHAIN, TAG, "look retry scheduled: source=" + lookRetrySource
                + ", dueInMs=" + Math.max(0L, AppVars.NeverTimer - now));
    }

    private synchronized void scheduleLookRetryAfterDelay(String source, long delayMs) {
        long now = System.currentTimeMillis();
        long requestedDueAtMs = now + Math.max(AUTO_CUT_RETRY_FALLBACK_DELAY_MS, delayMs);
        lookRetryPending = true;
        lookRetrySource = safe(source);
        lookRetryRequestedAtMs = now;
        if (AppVars.NeverTimer < requestedDueAtMs) {
            AppVars.NeverTimer = requestedDueAtMs;
        }
        AppLog.i(TRACE_CHAIN, TAG, "look retry scheduled: source=" + lookRetrySource
                + ", dueInMs=" + Math.max(0L, AppVars.NeverTimer - now));
    }

    /**
     * Откладывает автоматическое `Оглядеться`, если серверный cooldown ещё не истёк.
     *
     * Зависимости:
     * - вызывается из `WebAppInterface.DoHerbAutoCut()` перед JS `Ogl(...)`;
     * - использует тот же one-shot retry, что wrong-captcha/multi-cut, чтобы `MainActivity`
     *   вернула WebView на карту по ближайшему `NeverTimer` tick без второго HTTP-контура.
     */
    public synchronized boolean deferLookUntilServerTimerIfActive(String source) {
        long now = System.currentTimeMillis();
        long dueInMs = AppVars.NeverTimer - now;
        if (dueInMs <= 100L) {
            return false;
        }
        if (!lookRetryPending) {
            scheduleLookRetryAfterTimer("server_timer:" + source);
        } else {
            AppLog.d(TRACE_CHAIN, TAG, "look retry already pending while server timer active: source="
                    + safe(source) + ", pendingSource=" + lookRetrySource + ", dueInMs=" + dueInMs);
        }
        return true;
    }

    /**
     * true, если AutoCut ждёт ближайший server-timer tick для повторного `Оглядеться`.
     *
     * Зависимость: читается `MainActivity.checkServerTimerDrivenActions()` вместе с license-gated
     * `AutoFunctionsManager.isAutoCutEnabled()`, поэтому выключенный AutoCut не продолжит retry.
     */
    public synchronized boolean hasPendingLookRetry() {
        return lookRetryPending;
    }

    /**
     * Возвращает source retry, если он уже due, и атомарно очищает one-shot state.
     *
     * Контракт:
     * - пустая строка означает "ещё не due" или "нет pending retry";
     * - непустой source можно сразу использовать в логах `SERVER_TIMER_TICK`;
     * - очистка происходит до reload-а, чтобы один tick не отправил несколько `go=ret` запросов.
     */
    public synchronized String consumePendingLookRetryIfDue(long now) {
        if (!lookRetryPending) {
            return "";
        }
        if (AppVars.NeverTimer > 0L && now < AppVars.NeverTimer) {
            return "";
        }
        String source = lookRetrySource;
        long ageMs = Math.max(0L, now - lookRetryRequestedAtMs);
        lookRetryPending = false;
        lookRetrySource = "";
        lookRetryRequestedAtMs = 0L;
        AppLog.i(TRACE_CHAIN, TAG, "look retry consumed: source=" + source + ", ageMs=" + ageMs);
        return source;
    }

    /**
     * Очищает pending retry, когда реальный `Оглядеться` уже дошёл до `act=1`.
     *
     * Зависимость: вызывается из `AlchemyAjaxPhp.processAlchemyAct1(...)`, потому именно ответ `RESO@`
     * подтверждает, что WebView вернулся на карту и retry больше не нужен.
     */
    public synchronized void clearPendingLookRetry(String source) {
        if (!lookRetryPending) {
            return;
        }
        AppLog.d(TRACE_CHAIN, TAG, "look retry cleared: source=" + source + ", pendingSource=" + lookRetrySource);
        lookRetryPending = false;
        lookRetrySource = "";
        lookRetryRequestedAtMs = 0L;
    }

    /**
     * Удаляет due herb timer-ы текущей клетки после фактического `act=1` scan-а.
     *
     * Зачем это нужно:
     * - `AppTimer` хранит напоминание "на этой клетке должна вырасти трава";
     * - после реального `Оглядеться` эта клетка уже проверена, значит timer больше не должен
     *   снова тянуть маршрут на тот же regNum в рамках текущего круга;
     * - удаляются только due timer-ы текущей серверной смены, старые timer-ы другой смены чистит
     *   `pruneStaleHerbTimersForCurrentShift(...)`.
     *
     * Зависимости:
     * - `AppTimerManager` остаётся единственным хранилищем пользовательских/herb timer-ов;
     * - `extractHerbTimerCell(...)` поддерживает и новый `destination`, и legacy description.
     */
    public void clearDueHerbTimersForCurrentCell(String source) {
        String current = resolveCurrentRegNum();
        if (current.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int currentShift = getShiftForServerMs(getServerNowMs(now));
        if (currentShift == 0) {
            return;
        }
        AppTimerManager timerManager = AppTimerManager.getInstance(appContext);
        List<AppTimer> timers = timerManager.getTimers();
        int removed = 0;
        for (AppTimer timer : timers) {
            if (isDueHerbTimerForCell(timer, current, now, currentShift)) {
                timerManager.removeTimerById(timer.id);
                removed++;
            }
        }
        if (removed > 0) {
            AppLog.i(TRACE_CHAIN, TAG, "due herb timers cleared for current cell: cell="
                    + current + ", removed=" + removed + ", source=" + source);
        }
    }

    /**
     * Завершает cleanup-проход после inventory и возвращает route к следующей клетке.
     *
     * Зависимости:
     * - вызывается из `AutoCutHandler.processCleanupOpenInventory(...)` после штатного inventory pass;
     * - сбрасывает только AutoCut cleanup-флаги и массу, не трогая настройки пользователя;
     * - если на текущей клетке стоит pending look retry, маршрут не запускается, чтобы multi-cut/wrong-captcha
     *   продолжили именно текущую клетку после общего `NeverTimer`.
     */
    public void onCleanupCompleted(String source) {
        AppVars.AutoCutCleanupPending = false;
        AppVars.AutoCutCleanupReason = "";
        AppVars.AutoCutHarvestedMassSinceCleanup = 0d;
        restoreAutosPausedForCleanup("cleanup_completed:" + source);
        AppLog.i(TRACE_CHAIN, TAG, "cleanup completed, source=" + source);
        if (hasPendingLookRetry()) {
            AppLog.i(TRACE_CHAIN, TAG, "cleanup completed: keep current cell for pending look retry, source=" + source);
            return;
        }
        if (routeBackToTimerReturnIfNeeded("cleanup_completed:" + source)) {
            return;
        }
        routeNextCell("cleanup_completed:" + source);
    }

    /**
     * Обновляет max mass из bridge `SetAutoFishMassa(current/max)`.
     * Имя bridge историческое от авто-рыбалки, но map.js вызывает его и для resource pages.
     */
    public void updateMassSnapshot(String mass) {
        MassSnapshot snapshot = parseMassSnapshot(mass);
        if (snapshot.max > 0d) {
            AppVars.AutoCutKnownMassMax = snapshot.max;
            clearMassSnapshotSyncPending("mass_updated");
            AppLog.d(TRACE_CHAIN, TAG, "mass snapshot updated: current=" + snapshot.current + ", max=" + snapshot.max);
        }
    }

    /**
     * Синхронизирует `AutoFishMassa` из main.php/inventory HTML, чтобы AutoCut chat видел current/max.
     *
     * Зависимости:
     * - вызывается из `AutoCutHandler.processMainPhpAutoCutStep(...)` для каждого подходящего main.php HTML;
     * - использует тот же глобальный буфер массы, что исторически заполнялся через JS `SetAutoFishMassa(...)`;
     * - обновляет `AutoCutKnownMassMax`, от которого зависит cleanup threshold в `maybeRequestCleanupAfterCut(...)`.
     */
    public void updateMassSnapshotFromHtml(String html) {
        if (TextUtils.isEmpty(html)) {
            return;
        }
        String plain = html.replace("&nbsp;", " ").replaceAll("<[^>]+>", " ");
        Matcher matcher = INVENTORY_MASS_PATTERN.matcher(plain);
        if (!matcher.find()) {
            return;
        }
        String mass = matcher.group(1) == null ? "" : matcher.group(1).replace(" ", "").trim();
        if (mass.isEmpty()) {
            return;
        }
        AppVars.AutoFishMassa = mass;
        updateMassSnapshot(mass);
    }

    /**
     * Стартует route только если текущая клетка не подходит для немедленного `Оглядеться`.
     *
     * Зависимости:
     * - используется при включении AutoCut, когда пользователь может уже стоять на нужной клетке;
     * - не запускает `AutoFunctionsManager.startAutoMoving(...)`, если текущий regNum входит в CSV
     *   и либо ещё не checked, либо имеет due herb timer текущей смены;
     * - все остальные случаи уходят в общий `routeNextCellWithManager(...)`.
     */
    private void routeNextCellIfCurrentIsNotReady(AutoFunctionsManager manager, String source) {
        if (manager == null) {
            return;
        }
        if (shouldDelayRouteForPreparation()) {
            AppLog.d(TRACE_CHAIN, TAG, "route skip: preparation pending, source=" + source);
            return;
        }
        List<String> cells = getSearchCells();
        if (cells.isEmpty()) {
            return;
        }
        String current = resolveCurrentRegNum();
        if (!current.isEmpty()
                && cells.contains(current)
                && (hasDueHerbTimerForCurrentShift(current)
                || (!isCellCheckedForCurrentShift(current) && shouldRouteToUncheckedCell(current, "current_cell")))) {
            AppLog.d(TRACE_CHAIN, TAG, "route skip: current cell ready for Ogl, source=" + source + ", cell=" + current);
            return;
        }
        routeNextCellWithManager(manager, source);
    }

    /**
     * Блокирует старт маршрута, пока existing main.php/alchemy preparation ещё не завершён.
     * Иначе `onAutoCutEnabled(...)` может увести WebView с текущей ready-клетки до возврата
     * `AutoCutHandler` на карту после проверки серпа или one-shot mass-sync.
     */
    private boolean shouldDelayRouteForPreparation() {
        if (AppVars.AutoCutCleanupPending || massSnapshotSyncPending || AlchemyAjaxPhp.hasPendingCutForRouteGuard()) {
            return true;
        }
        if (!AppVars.AutoCutCheckSickle) {
            return false;
        }
        List<String> cells = getSearchCells();
        String current = resolveCurrentRegNum();
        if (cells.isEmpty() || TextUtils.isEmpty(current)) {
            return true;
        }
        return cells.contains(current)
                && (hasDueHerbTimerForCurrentShift(current) || !isCellCheckedForCurrentShift(current));
    }

    /** Получает `AutoFunctionsManager` и делегирует старт маршрута с защитой от исключений. */
    private void routeNextCell(String source) {
        try {
            AutoFunctionsManager manager = AutoFunctionsManager.getInstance(appContext);
            routeNextCellWithManager(manager, source);
        } catch (Exception e) {
            AppLog.w(TRACE_CHAIN, TAG, "route next failed, source=" + source, e);
        }
    }

    /**
     * Выбирает следующую CSV-клетку и запускает существующий навигатор.
     *
     * Приоритеты:
     * - сначала due herb timer текущей смены, чтобы вернуться туда, где трава должна была вырасти;
     * - затем следующая unchecked клетка текущей смены;
     * - если checked весь CSV-список, cleared checked-set и стартуется новый круг через тот же cache-skip filter.
     *
     * Зависимости:
     * - навигацию выполняет только `AutoFunctionsManager.startAutoMoving(...)`;
     * - `AppVars.AutoMovingDestinaton` защищает от дублирующего запуска того же маршрута;
     * - при `next == current` ставится one-shot retry вместо искусственного self-route.
     */
    private void routeNextCellWithManager(AutoFunctionsManager manager, String source) {
        List<String> cells = getSearchCells();
        if (cells.isEmpty()) {
            AppLog.d(TRACE_CHAIN, TAG, "route next skipped: cells list empty, source=" + source);
            return;
        }
        String current = resolveCurrentRegNum();
        pruneStaleHerbTimersForCurrentShift("route_next:" + source);

        DueHerbTimer dueTimer = findDueHerbTimerForRoute(cells, current);
        String routeReason = dueTimer != null ? "herb_timer:" + dueTimer.timerId : "unchecked";
        String next = dueTimer != null ? dueTimer.cell : findNextUncheckedCell(cells, current);
        if (TextUtils.isEmpty(next)) {
            scheduleNextRouteRound(cells, source);
            return;
        }
        if (next.equals(current)) {
            scheduleLookRetryAfterTimer("route_current:" + source);
            AppLog.i(TRACE_CHAIN, TAG, "route next: current cell scheduled for recheck, cell="
                    + next + ", reason=" + routeReason + ", source=" + source);
            return;
        }
        if (AppVars.AutoMoving && next.equals(AppVars.AutoMovingDestinaton)) {
            AppLog.d(TRACE_CHAIN, TAG, "route next skip: navigator already goes to " + next + ", source=" + source);
            return;
        }
        rememberTimerRouteReturnIfNeeded(dueTimer, current, source);
        final String destination = next;
        new Handler(Looper.getMainLooper()).postDelayed(() -> manager.startAutoMoving(destination), ROUTE_NEXT_DELAY_MS);
        AppLog.i(TRACE_CHAIN, TAG, "route next: destination=" + destination
                + ", reason=" + routeReason + ", source=" + source);
    }

    private void scheduleNextRouteRound(List<String> cells, String source) {
        clearCheckedCellsForCurrentShift("new_circle:" + source);
        int invalidated = invalidateSelectedEmptyCellSnapshots(cells);
        ShiftCheckedCells checked;
        synchronized (this) {
            checked = loadCheckedCellsLocked();
        }
        scheduleLookRetryAfterDelay(
                "all_cells_checked_new_round:" + source,
                CURRENT_CELL_NO_SELECTED_RETRY_MS);
        AppLog.i(TRACE_CHAIN, TAG, "all cells checked for shift=" + checked.shift
                + ", shiftStartServerMs=" + checked.shiftStartServerMs
                + ", restart route round, invalidated=" + invalidated
                + ", source=" + source);
    }

    private void rememberTimerRouteReturnIfNeeded(DueHerbTimer dueTimer, String current, String source) {
        if (dueTimer == null || TextUtils.isEmpty(current) || current.equals(dueTimer.cell)) {
            return;
        }
        timerRouteReturnCell = current;
        timerRouteTargetCell = dueTimer.cell;
        timerRouteReturning = false;
        AppLog.i(TRACE_CHAIN, TAG, "timer-route detour remembered: from=" + current
                + ", target=" + dueTimer.cell
                + ", timerId=" + dueTimer.timerId
                + ", source=" + source);
    }

    private boolean routeBackToTimerReturnIfNeeded(String source) {
        String current = resolveCurrentRegNum();
        String target = safe(timerRouteTargetCell);
        String returnCell = safe(timerRouteReturnCell);
        if (TextUtils.isEmpty(current) || TextUtils.isEmpty(target) || TextUtils.isEmpty(returnCell)
                || !target.equals(current)) {
            return false;
        }
        if (AppVars.AutoMoving && returnCell.equals(AppVars.AutoMovingDestinaton)) {
            return true;
        }
        timerRouteTargetCell = "";
        timerRouteReturning = true;
        try {
            AutoFunctionsManager manager = AutoFunctionsManager.getInstance(appContext);
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> manager.startAutoMoving(returnCell), ROUTE_NEXT_DELAY_MS);
            AppLog.i(TRACE_CHAIN, TAG, "timer-route return: destination=" + returnCell
                    + ", from=" + current + ", source=" + source);
            return true;
        } catch (Exception error) {
            AppLog.w(TRACE_CHAIN, TAG, "timer-route return failed, source=" + source, error);
            clearTimerRouteState("timer_return_failed:" + source);
            return false;
        }
    }

    private void clearTimerRouteState(String source) {
        if (TextUtils.isEmpty(timerRouteReturnCell)
                && TextUtils.isEmpty(timerRouteTargetCell)
                && !timerRouteReturning) {
            return;
        }
        AppLog.d(TRACE_CHAIN, TAG, "timer-route state cleared: source=" + source
                + ", returnCell=" + timerRouteReturnCell
                + ", targetCell=" + timerRouteTargetCell
                + ", returning=" + timerRouteReturning);
        timerRouteReturnCell = "";
        timerRouteTargetCell = "";
        timerRouteReturning = false;
    }

    /**
     * Round-robin поиск следующей непроверенной клетки относительно текущей позиции.
     *
     * Зависимость: checked-set scoped по точному server-window смены, поэтому смена трав или новый
     * день не переиспользуют stale отметки старого круга.
     */
    private String findNextUncheckedCell(List<String> cells, String current) {
        if (cells == null || cells.isEmpty()) {
            return "";
        }
        ShiftCheckedCells checkedSnapshot;
        synchronized (this) {
            checkedSnapshot = loadCheckedCellsLocked();
        }
        int startIndex = cells.indexOf(current);
        int skippedChecked = 0;
        ArrayList<String> skippedSamples = new ArrayList<>();
        for (int offset = 1; offset <= cells.size(); offset++) {
            int index = startIndex >= 0 ? (startIndex + offset) % cells.size() : offset - 1;
            String cell = cells.get(index);
            if (checkedSnapshot.cells.contains(cell.trim())) {
                skippedChecked++;
                if (skippedSamples.size() < 5) {
                    skippedSamples.add(cell);
                }
                continue;
            }
            if (shouldRouteToUncheckedCell(cell, "unchecked_route")) {
                logSkippedCheckedCells(skippedChecked, skippedSamples, current, checkedSnapshot, cell);
                return cell;
            }
        }
        logSkippedCheckedCells(skippedChecked, skippedSamples, current, checkedSnapshot, "");
        return "";
    }

    private void logSkippedCheckedCells(int count,
                                        List<String> samples,
                                        String current,
                                        ShiftCheckedCells checked,
                                        String next) {
        if (count <= 0 || checked == null) {
            return;
        }
        AppLog.i(TRACE_CHAIN, TAG, "route skipped checked cells: count=" + count
                + ", sample=" + samples
                + ", current=" + safe(current)
                + ", next=" + safe(next)
                + ", shift=" + checked.shift
                + ", shiftStartServerMs=" + checked.shiftStartServerMs);
    }

    private boolean shouldRouteToUncheckedCell(String cell, String source) {
        String skipReason = getUncheckedCellSkipReason(cell);
        if (TextUtils.isEmpty(skipReason)) {
            return true;
        }
        AppLog.i(TRACE_CHAIN, TAG, "route skip cell: cell=" + safe(cell)
                + ", reason=" + skipReason + ", source=" + source);
        return false;
    }

    private String getUncheckedCellSkipReason(String cell) {
        if (TextUtils.isEmpty(cell)) {
            return "invalid_cell";
        }
        JSONObject snapshot;
        synchronized (this) {
            snapshot = loadCellSnapshotsLocked().optJSONObject(cell.trim());
        }
        if (snapshot == null) {
            return "";
        }
        return getUncheckedCellSkipReasonForSnapshot(snapshot);
    }

    private String getUncheckedCellSkipReasonForSnapshot(JSONObject snapshot) {
        if (snapshot == null) {
            return "";
        }
        long now = System.currentTimeMillis();
        int currentShift = getShiftForServerMs(getServerNowMs(now));
        if (currentShift == 0
                || snapshot.optInt("shift", -1) != currentShift
                || !isSnapshotUpdatedInCurrentShift(snapshot, currentShift, now)) {
            return "";
        }
        JSONArray herbs = snapshot.optJSONArray("herbs");
        if (herbs == null || herbs.length() == 0) {
            return "";
        }
        boolean hasSelectedHerb = false;
        for (int index = 0; index < herbs.length(); index++) {
            JSONObject item = herbs.optJSONObject(index);
            if (item == null) {
                continue;
            }
            String name = safe(item.optString("name", ""));
            if (name.isEmpty() || !isHerbSelected("", name)) {
                continue;
            }
            hasSelectedHerb = true;
            if (item.optInt("count", 0) > 0) {
                return "";
            }
        }
        return hasSelectedHerb ? "selected_herbs_empty_current_shift" : "no_selected_herbs_in_cell_cache";
    }

    private int invalidateSelectedEmptyCellSnapshots(List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            return 0;
        }
        int invalidated = 0;
        synchronized (this) {
            try {
                JSONObject snapshots = loadCellSnapshotsLocked();
                for (String rawCell : cells) {
                    String cell = safe(rawCell);
                    if (TextUtils.isEmpty(cell)) {
                        continue;
                    }
                    JSONObject snapshot = snapshots.optJSONObject(cell);
                    if (!"selected_herbs_empty_current_shift".equals(getUncheckedCellSkipReasonForSnapshot(snapshot))) {
                        continue;
                    }
                    snapshot.put("updatedAtMs", 0L);
                    invalidated++;
                }
                if (invalidated > 0) {
                    prefs.edit().putString(scopedKey(KEY_CELL_SNAPSHOTS_JSON_PREFIX), snapshots.toString()).apply();
                }
            } catch (Exception error) {
                AppLog.w(TRACE_CHAIN, TAG, "failed to invalidate selected-empty cell snapshots, source=new_round", error);
            }
        }
        return invalidated;
    }

    /**
     * Проверяет, что persisted snapshot клетки был получен именно в текущем окне server-shift.
     * Номер смены 1..4 повторяется каждый день, поэтому одного `shift` недостаточно для route skip.
     */
    private boolean isSnapshotUpdatedInCurrentShift(JSONObject snapshot, int currentShift, long localNowMs) {
        long updatedAtMs = snapshot.optLong("updatedAtMs", 0L);
        if (updatedAtMs <= 0L || currentShift <= 0) {
            return false;
        }
        List<AutoCutShift> shifts;
        synchronized (this) {
            shifts = loadShiftsLocked();
        }
        if (currentShift > shifts.size()) {
            return false;
        }
        long serverNowMs = getServerNowMs(localNowMs);
        AutoCutShift shift = shifts.get(currentShift - 1);
        long shiftStartMs = getShiftStartServerMs(serverNowMs, shift);
        long shiftEndMs = getShiftEndServerMs(shiftStartMs, shift);
        long updatedServerMs = getServerNowMs(updatedAtMs);
        return updatedServerMs >= shiftStartMs
                && updatedServerMs < shiftEndMs
                && updatedServerMs <= serverNowMs + TimeUnit.MINUTES.toMillis(1);
    }

    /**
     * Накопляет массу срезов и при превышении 10% max mass ставит cleanup-флаг.
     * Если max mass неизвестна, использует `CLEANUP_FALLBACK_THRESHOLD_MASS`.
     */
    private boolean maybeRequestCleanupAfterCut(double resourceMass, String source) {
        if (!isCleanupEnabled() || resourceMass <= 0d) {
            return false;
        }
        AppVars.AutoCutHarvestedMassSinceCleanup += resourceMass;
        double maxMass = AppVars.AutoCutKnownMassMax;
        double threshold = maxMass > 0d ? maxMass * 0.10d : CLEANUP_FALLBACK_THRESHOLD_MASS;
        if (AppVars.AutoCutHarvestedMassSinceCleanup <= threshold) {
            AppLog.d(TRACE_CHAIN, TAG, "cleanup threshold not reached: delta="
                    + AppVars.AutoCutHarvestedMassSinceCleanup + ", threshold=" + threshold);
            return false;
        }
        startCleanup("mass_delta:" + source, false);
        AppLog.i(TRACE_CHAIN, TAG, "cleanup requested: delta="
                + AppVars.AutoCutHarvestedMassSinceCleanup + ", threshold=" + threshold);
        return true;
    }

    /**
     * Форсирует cleanup `Бесполезный хлам` после server success `act=3`.
     *
     * Важно: это не зависит от пользовательской галочки cleanup по массе. Само удаление делает
     * существующий inventory bulk-drop контур через `AppVars.BulkDropThing` и `InvEntry.DropLink`.
     */
    public void requestGarbageCleanupAfterCut(String source) {
        startCleanup("garbage:" + source, true);
        AppLog.i(TRACE_CHAIN, TAG, "garbage cleanup requested: thing=" + GARBAGE_ITEM_NAME
                + ", source=" + source);
    }

    private synchronized void startCleanup(String reason, boolean dropGarbage) {
        pauseAutosForCleanup(reason);
        if (dropGarbage) {
            AppVars.BulkDropThing = GARBAGE_ITEM_NAME;
            AppVars.BulkDropPrice = "";
        }
        AppVars.AutoCutCleanupPending = true;
        AppVars.AutoCutCleanupReason = safe(reason);
        requestMainFrameReload("cleanup:" + reason);
    }

    private synchronized void pauseAutosForCleanup(String reason) {
        if (cleanupPauseSnapshot != null) {
            AppLog.d(TRACE_CHAIN, TAG, "cleanup pause already active, reason=" + reason);
            return;
        }
        CleanupPauseSnapshot snapshot = new CleanupPauseSnapshot();
        try {
            AutoFunctionsManager manager = AutoFunctionsManager.getInstance(appContext);
            snapshot.autoFish = manager.isAutoFishEnabled();
            snapshot.autoSkin = manager.isAutoSkinEnabled();
            snapshot.autoBait = manager.isAutoBaitEnabled();
            snapshot.autoCompass = manager.isAutoCompassEnabled();
            snapshot.autoAttack = manager.isAutoAttackEnabled();
            snapshot.autoInvisible = manager.isAutoInvisibleEnabled();
            snapshot.autoDetect = manager.isAutoDetectEnabled();
            snapshot.autoSummon = manager.isAutoSummonEnabled();
            snapshot.autoDrink = manager.isAutoDrinkEnabled();
            snapshot.autoTreasure = manager.isAutoTreasureEnabled();
            snapshot.autoBoss = manager.isAutoBossEnabled();
            snapshot.autoRefresh = manager.isAutoRefreshEnabled();
            snapshot.autoMoving = AppVars.AutoMoving;
            snapshot.autoMovingDestination = AppVars.AutoMovingDestinaton;
            snapshot.autoMovingMapPath = AppVars.AutoMovingMapPath;
            snapshot.autoMovingNextJump = AppVars.AutoMovingNextJump;
            snapshot.autoMovingJumps = AppVars.AutoMovingJumps;
            snapshot.autoMovingCityGate = AppVars.AutoMovingCityGate;
            snapshot.capturedAtMs = System.currentTimeMillis();

            if (snapshot.autoFish) manager.setAutoFishEnabled(false);
            if (snapshot.autoSkin) manager.setAutoSkinEnabled(false);
            if (snapshot.autoBait) manager.setAutoBaitEnabled(false);
            if (snapshot.autoCompass) manager.setAutoCompassEnabled(false);
            if (snapshot.autoAttack) manager.setAutoAttackEnabled(false);
            if (snapshot.autoInvisible) manager.setAutoInvisibleEnabled(false);
            if (snapshot.autoDetect) manager.setAutoDetectEnabled(false);
            if (snapshot.autoSummon) manager.setAutoSummonEnabled(false);
            if (snapshot.autoDrink) manager.setAutoDrinkEnabled(false);
            if (snapshot.autoTreasure) manager.setAutoTreasureEnabled(false);
            if (snapshot.autoBoss) manager.setAutoBossEnabled(false);
            if (snapshot.autoRefresh) manager.setAutoRefreshEnabled(false);
            if (snapshot.autoMoving) {
                AppVars.AutoMoving = false;
            }
            cleanupPauseSnapshot = snapshot.hasPausedAnything() ? snapshot : null;
            AppLog.i(TRACE_CHAIN, TAG, "cleanup pause captured: reason=" + reason
                    + ", paused=" + snapshot.describePaused());
        } catch (Exception error) {
            cleanupPauseSnapshot = snapshot.hasPausedAnything() ? snapshot : null;
            AppLog.w(TRACE_CHAIN, TAG, "cleanup pause failed, reason=" + reason, error);
        }
    }

    private synchronized void restoreAutosPausedForCleanup(String reason) {
        CleanupPauseSnapshot snapshot = cleanupPauseSnapshot;
        cleanupPauseSnapshot = null;
        if (snapshot == null) {
            return;
        }
        try {
            AutoFunctionsManager manager = AutoFunctionsManager.getInstance(appContext);
            if (snapshot.autoFish && !manager.isAutoFishEnabled()) manager.setAutoFishEnabled(true);
            if (snapshot.autoSkin && !manager.isAutoSkinEnabled()) manager.setAutoSkinEnabled(true);
            if (snapshot.autoBait && !manager.isAutoBaitEnabled()) manager.setAutoBaitEnabled(true);
            if (snapshot.autoCompass && !manager.isAutoCompassEnabled()) manager.setAutoCompassEnabled(true);
            if (snapshot.autoAttack && !manager.isAutoAttackEnabled()) manager.setAutoAttackEnabled(true);
            if (snapshot.autoInvisible && !manager.isAutoInvisibleEnabled()) manager.setAutoInvisibleEnabled(true);
            if (snapshot.autoDetect && !manager.isAutoDetectEnabled()) manager.setAutoDetectEnabled(true);
            if (snapshot.autoSummon && !manager.isAutoSummonEnabled()) manager.setAutoSummonEnabled(true);
            if (snapshot.autoDrink && !manager.isAutoDrinkEnabled()) manager.setAutoDrinkEnabled(true);
            if (snapshot.autoTreasure && !manager.isAutoTreasureEnabled()) manager.setAutoTreasureEnabled(true);
            if (snapshot.autoBoss && !manager.isAutoBossEnabled()) manager.setAutoBossEnabled(true);
            if (snapshot.autoRefresh && !manager.isAutoRefreshEnabled()) manager.setAutoRefreshEnabled(true);
            if (snapshot.autoMoving && !AppVars.AutoMoving) {
                AppVars.AutoMoving = true;
                AppVars.AutoMovingDestinaton = snapshot.autoMovingDestination;
                AppVars.AutoMovingMapPath = snapshot.autoMovingMapPath;
                AppVars.AutoMovingNextJump = snapshot.autoMovingNextJump;
                AppVars.AutoMovingJumps = snapshot.autoMovingJumps;
                AppVars.AutoMovingCityGate = snapshot.autoMovingCityGate;
            }
            AppLog.i(TRACE_CHAIN, TAG, "cleanup restore completed: reason=" + reason
                    + ", restored=" + snapshot.describePaused()
                    + ", ageMs=" + (System.currentTimeMillis() - snapshot.capturedAtMs));
        } catch (Exception error) {
            AppLog.w(TRACE_CHAIN, TAG, "cleanup restore failed, reason=" + reason, error);
        }
    }

    /** Добавляет текущую клетку в checked-set активной смены трав. */
    private void markCurrentCellChecked(String source) {
        String current = resolveCurrentRegNum();
        if (current.isEmpty()) {
            return;
        }
        synchronized (this) {
            ShiftCheckedCells checked = loadCheckedCellsLocked();
            if (checked.cells.add(current)) {
                persistCheckedCellsLocked(checked);
            }
        }
        AppLog.d(TRACE_CHAIN, TAG, "cell checked: " + current + ", source=" + source);
    }

    /**
     * Очищает checked-set текущей смены, чтобы AutoCut продолжал следующий круг после последней CSV-клетки.
     *
     * Зависимости:
     * - не удаляет herb timers: они остаются в `AppTimerManager` и имеют приоритет в следующем route pass;
     * - persisted value сохраняется с тем же shift id, чтобы UI/route видели начало нового круга сразу.
     */
    private void clearCheckedCellsForCurrentShift(String source) {
        int removed;
        synchronized (this) {
            ShiftCheckedCells checked = loadCheckedCellsLocked();
            removed = checked.cells.size();
            if (removed <= 0) {
                return;
            }
            checked.cells.clear();
            persistCheckedCellsLocked(checked);
        }
        AppLog.i(TRACE_CHAIN, TAG, "checked cells cleared for next circle: removed="
                + removed + ", source=" + source);
    }

    private synchronized boolean isCellCheckedForCurrentShift(String cell) {
        if (TextUtils.isEmpty(cell)) {
            return false;
        }
        return loadCheckedCellsLocked().cells.contains(cell.trim());
    }

    /**
     * Ищет ближайший due herb timer в CSV-порядке относительно текущей клетки.
     *
     * Зависимости:
     * - читает snapshot timer-ов через `AppTimerManager.getTimers()`, не меняя список;
     * - учитывает только timer-ы текущей серверной смены, чтобы прошлые смены не ломали круг;
     * - порядок поиска совпадает с основным round-robin маршрутом, поэтому timer не создаёт второй навигатор.
     */
    private DueHerbTimer findDueHerbTimerForRoute(List<String> cells, String current) {
        if (!isCutByTimersEnabled() || cells == null || cells.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        int currentShift = getShiftForServerMs(getServerNowMs(now));
        if (currentShift == 0) {
            return null;
        }
        List<AppTimer> timers = AppTimerManager.getInstance(appContext).getTimers();
        if (timers.isEmpty()) {
            return null;
        }
        int startIndex = cells.indexOf(current);
        for (int offset = 0; offset < cells.size(); offset++) {
            int index = startIndex >= 0 ? (startIndex + offset) % cells.size() : offset;
            DueHerbTimer dueTimer = findDueHerbTimerForCell(timers, cells.get(index), now, currentShift);
            if (dueTimer != null) {
                return dueTimer;
            }
        }
        return null;
    }

    /**
     * true, если конкретная клетка имеет due herb timer текущей смены.
     *
     * Используется guard-ами `shouldAutoLookOnCurrentCell()` и `routeNextCellIfCurrentIsNotReady(...)`,
     * чтобы checked-клетка могла быть проверена повторно по росту травы.
     */
    private boolean hasDueHerbTimerForCurrentShift(String cell) {
        if (!isCutByTimersEnabled() || TextUtils.isEmpty(cell)) {
            return false;
        }
        long now = System.currentTimeMillis();
        int currentShift = getShiftForServerMs(getServerNowMs(now));
        if (currentShift == 0) {
            return false;
        }
        List<AppTimer> timers = AppTimerManager.getInstance(appContext).getTimers();
        return findDueHerbTimerForCell(timers, cell.trim(), now, currentShift) != null;
    }

    /**
     * Возвращает первый due herb timer для конкретной клетки из переданного snapshot-а timer-ов.
     *
     * Зависимость: метод не удаляет timer, потому удаление должно происходить только после реального
     * `alchemy_ajax.php?act=1` через `clearDueHerbTimersForCurrentCell(...)`.
     */
    private DueHerbTimer findDueHerbTimerForCell(List<AppTimer> timers, String cell, long now, int currentShift) {
        if (timers == null || timers.isEmpty() || TextUtils.isEmpty(cell)) {
            return null;
        }
        String safeCell = cell.trim();
        for (AppTimer timer : timers) {
            if (!isDueHerbTimerForCell(timer, safeCell, now, currentShift)) {
                continue;
            }
            return new DueHerbTimer(timer.id, safeCell, timer.description, getHerbTimerReadyAtMs(timer));
        }
        return null;
    }

    /**
     * Проверяет, что timer относится к AutoCut-клетке, находится в текущей смене и уже наступил.
     *
     * Маршрут срабатывает только после фактического due-time herb timer-а.
     */
    private boolean isDueHerbTimerForCell(AppTimer timer, String cell, long now, int currentShift) {
        if (timer == null || !timer.isHerb || TextUtils.isEmpty(cell)) {
            return false;
        }
        String timerCell = extractHerbTimerCell(timer);
        if (!cell.trim().equals(timerCell)) {
            return false;
        }
        if (!isHerbTimerInCurrentShift(timer, currentShift)) {
            return false;
        }
        return now >= getHerbTimerReadyAtMs(timer);
    }

    /**
     * Удаляет herb timer-ы, чей ожидаемый рост относится не к текущей серверной смене.
     *
     * Это сохраняет правило пользователя: herb timer действует только от смены до смены.
     * Обычные timer-ы, potion/complect/destination timer-ы и timer-ы без AutoCut regNum не трогаются.
     */
    private void pruneStaleHerbTimersForCurrentShift(String source) {
        long now = System.currentTimeMillis();
        int currentShift = getShiftForServerMs(getServerNowMs(now));
        if (currentShift == 0) {
            return;
        }
        AppTimerManager timerManager = AppTimerManager.getInstance(appContext);
        List<AppTimer> timers = timerManager.getTimers();
        int removed = 0;
        for (AppTimer timer : timers) {
            if (timer == null || !timer.isHerb) {
                continue;
            }
            if (TextUtils.isEmpty(extractHerbTimerCell(timer))) {
                continue;
            }
            if (!isHerbTimerInCurrentShift(timer, currentShift)) {
                timerManager.removeTimerById(timer.id);
                removed++;
            }
        }
        if (removed > 0) {
            AppLog.i(TRACE_CHAIN, TAG, "stale herb timers removed for shift: removed="
                    + removed + ", shift=" + currentShift + ", source=" + source);
        }
    }

    /**
     * true, если готовность herb timer-а приходится на активную серверную смену трав.
     *
     * Зависимость: время переводится через `getServerNowMs(...)`, чтобы локальные часы Android
     * не ломали границы смен при известном `Profile.ServDiff`.
     */
    private boolean isHerbTimerInCurrentShift(AppTimer timer, int currentShift) {
        if (timer == null || currentShift == 0) {
            return false;
        }
        int timerShift = getShiftForServerMs(getServerNowMs(getHerbTimerReadyAtMs(timer)));
        return timerShift == currentShift;
    }

    /**
     * Возвращает локальное время, когда route уже должен считать herb timer готовым.
     *
     * `triggerTime` уже хранит фактический due-time: рост травы + 5 минут запаса.
     */
    private static long getHerbTimerReadyAtMs(AppTimer timer) {
        return timer == null ? 0L : Math.max(0L, timer.triggerTime);
    }

    /**
     * Извлекает regNum клетки из herb timer-а.
     *
     * Поддерживаются два источника:
     * - `timer.destination`, если в будущем AutoCut начнёт сохранять regNum явно;
     * - legacy/current description `Вырастет <herb> на <regNum>`, созданный `markHerbCut(...)`.
     */
    private static String extractHerbTimerCell(AppTimer timer) {
        if (timer == null) {
            return "";
        }
        String destination = safe(timer.destination);
        if (destination.matches("\\d{1,4}-\\d{1,5}")) {
            return destination;
        }
        Matcher matcher = HERB_TIMER_CELL_PATTERN.matcher(safe(timer.description));
        return matcher.find() ? matcher.group(1) : "";
    }

    private ShiftCheckedCells loadCheckedCellsLocked() {
        long localNowMs = System.currentTimeMillis();
        long serverNowMs = getServerNowMs(localNowMs);
        int shift = getShiftForServerMs(serverNowMs);
        long shiftStartServerMs = getCurrentShiftStartServerMs(serverNowMs, shift);
        String raw = prefs.getString(scopedKey(KEY_CHECKED_SHIFT_PREFIX), "");
        ShiftCheckedCells result = new ShiftCheckedCells(shift, shiftStartServerMs);
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        String[] parts = raw.split("\\|", 3);
        if (parts.length < 3) {
            // Legacy format was `shift|cells` and could leak checked cells into the same shift next day.
            return result;
        }
        long storedShiftStartServerMs = -1L;
        int storedShift = -1;
        try {
            storedShiftStartServerMs = Long.parseLong(parts[0]);
            storedShift = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
        }
        if (storedShift != shift || storedShiftStartServerMs != shiftStartServerMs) {
            return result;
        }
        result.cells.addAll(parseCellsCsv(parts[2]));
        return result;
    }

    private void persistCheckedCellsLocked(ShiftCheckedCells checked) {
        StringBuilder value = new StringBuilder();
        value.append(checked.shiftStartServerMs).append('|')
                .append(checked.shift).append('|');
        int index = 0;
        for (String cell : checked.cells) {
            if (index++ > 0) {
                value.append(',');
            }
            value.append(cell);
        }
        prefs.edit().putString(scopedKey(KEY_CHECKED_SHIFT_PREFIX), value.toString()).apply();
    }

    private void requestMainFrameReload(String source) {
        MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
        if (activity == null || activity.getMainWebView() == null) {
            AppLog.w(TRACE_CHAIN, TAG, "main frame reload skipped: activity/webview null, source=" + source);
            return;
        }
        activity.runOnUiThread(() -> {
            String link = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf";
            String vcode = SessionManager.getInstance().getValidVCodeForAction("auto_cut_" + source.replace(':', '_'));
            if (!TextUtils.isEmpty(vcode)) {
                link += "&vcode=" + vcode;
            } else {
                AppLog.w(TRACE_CHAIN, TAG, "main frame reload without vcode, source=" + source);
            }
            link += "&an_auto_cut=1&r=" + System.currentTimeMillis();
            activity.getMainWebView().loadUrl(link);
            AppLog.d(TRACE_CHAIN, TAG, "main frame reload requested: source=" + source + ", url=" + link);
        });
    }

    private List<AutoCutShift> loadShiftsLocked() {
        ArrayList<AutoCutShift> result = new ArrayList<>();
        String raw = prefs.getString(scopedKey(KEY_SHIFTS_JSON_PREFIX), "");
        if (!TextUtils.isEmpty(raw)) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    AutoCutShift shift = new AutoCutShift(
                            item.optInt("startHour", 0),
                            item.optInt("startMinute", 0),
                            item.optInt("endHour", 0),
                            item.optInt("endMinute", 0));
                    if (shift.isValid()) {
                        result.add(shift);
                    }
                }
            } catch (Exception error) {
                AppLog.w(TRACE_CHAIN, TAG, "failed to parse shift schedule, fallback to defaults", error);
                result.clear();
            }
        }
        if (result.isEmpty()) {
            result.addAll(defaultShifts());
        }
        return result;
    }

    private void persistShiftsLocked(List<AutoCutShift> shifts) {
        JSONArray array = new JSONArray();
        List<AutoCutShift> safeShifts = (shifts == null || shifts.isEmpty()) ? defaultShifts() : shifts;
        for (AutoCutShift shift : safeShifts) {
            if (shift == null || !shift.isValid()) {
                continue;
            }
            JSONObject item = new JSONObject();
            try {
                item.put("startHour", shift.startHour);
                item.put("startMinute", shift.startMinute);
                item.put("endHour", shift.endHour);
                item.put("endMinute", shift.endMinute);
                array.put(item);
            } catch (Exception error) {
                AppLog.w(TRACE_CHAIN, TAG, "failed to serialize shift: " + shift.displayRange(), error);
            }
        }
        prefs.edit().putString(scopedKey(KEY_SHIFTS_JSON_PREFIX), array.toString()).apply();
    }

    private static List<AutoCutShift> parseShiftScheduleText(String text) {
        ArrayList<AutoCutShift> result = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return result;
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            AutoCutShift shift = parseShiftLine(line);
            if (shift != null && shift.isValid()) {
                result.add(shift);
            }
        }
        return result;
    }

    private static AutoCutShift parseShiftLine(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = SHIFT_LINE_PATTERN.matcher(line.trim());
        if (!matcher.find()) {
            return null;
        }
        return new AutoCutShift(
                parseIntSafe(matcher.group(1), 0),
                parseIntSafe(matcher.group(2), 0),
                parseIntSafe(matcher.group(3), 0),
                parseIntSafe(matcher.group(4), 0));
    }

    private static List<AutoCutShift> defaultShifts() {
        ArrayList<AutoCutShift> shifts = new ArrayList<>();
        shifts.add(new AutoCutShift(0, 50, 6, 50));
        shifts.add(new AutoCutShift(6, 50, 12, 50));
        shifts.add(new AutoCutShift(12, 50, 18, 50));
        shifts.add(new AutoCutShift(18, 50, 0, 50));
        return shifts;
    }

    private int getShiftForServerMs(long serverMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(serverMs);
        int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        List<AutoCutShift> shifts;
        synchronized (this) {
            shifts = loadShiftsLocked();
        }
        for (int i = 0; i < shifts.size(); i++) {
            if (shifts.get(i).containsMinuteOfDay(minutes)) {
                return i + 1;
            }
        }
        return 0;
    }

    private long getCurrentShiftStartServerMs(long serverNowMs, int shiftIndex) {
        if (shiftIndex <= 0) {
            return 0L;
        }
        List<AutoCutShift> shifts;
        synchronized (this) {
            shifts = loadShiftsLocked();
        }
        if (shiftIndex > shifts.size()) {
            return 0L;
        }
        return getShiftStartServerMs(serverNowMs, shifts.get(shiftIndex - 1));
    }

    private static long getShiftStartServerMs(long serverNowMs, AutoCutShift shift) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(serverNowMs);
        int nowMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        setServerCalendarTime(calendar, shift.startHour, shift.startMinute);
        long startMs = calendar.getTimeInMillis();
        if (shift.startMinuteOfDay() > shift.endMinuteOfDay() && nowMinute < shift.endMinuteOfDay()) {
            startMs -= TimeUnit.DAYS.toMillis(1);
        }
        return startMs;
    }

    private static long getShiftEndServerMs(long shiftStartMs, AutoCutShift shift) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(shiftStartMs);
        setServerCalendarTime(calendar, shift.endHour, shift.endMinute);
        long endMs = calendar.getTimeInMillis();
        if (endMs <= shiftStartMs) {
            endMs += TimeUnit.DAYS.toMillis(1);
        }
        return endMs;
    }

    private static void setServerCalendarTime(Calendar calendar, int hour, int minute) {
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static MassSnapshot parseMassSnapshot(String mass) {
        MassSnapshot result = new MassSnapshot();
        if (TextUtils.isEmpty(mass) || !mass.contains("/")) {
            return result;
        }
        String[] split = mass.split("/", 2);
        result.current = parseDoubleSafe(split[0]);
        result.max = parseDoubleSafe(split[1]);
        return result;
    }

    private static String updateInventoryMassAfterCut(double resourceMass) {
        String mass = AppVars.AutoFishMassa == null ? "" : AppVars.AutoFishMassa.trim();
        if (mass.isEmpty()) {
            return "";
        }
        if (resourceMass <= 0d || !mass.contains("/")) {
            return mass;
        }
        String[] split = mass.split("/", 2);
        if (split.length < 2 || TextUtils.isEmpty(split[0]) || TextUtils.isEmpty(split[1])) {
            return mass;
        }
        double current = parseDoubleSafe(split[0]);
        double next = Math.max(0d, current + resourceMass);
        AppVars.AutoFishMassa = formatDouble(next) + "/" + split[1].trim();
        return AppVars.AutoFishMassa;
    }

    private static double parseDoubleSafe(String value) {
        if (value == null) {
            return 0d;
        }
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return 0d;
        }
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    private static int parseIntSafe(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private TimerPlan buildTimerPlan(String herb, int growthMinutes, String regNum) {
        long localNow = System.currentTimeMillis();
        long serverNow = getServerNowMs(localNow);
        int safeGrowthMinutes = Math.max(1, growthMinutes);
        long delayMinutes = safeGrowthMinutes + HERB_TIMER_EXTRA_DELAY_MINUTES;
        int currentShift = getShiftForServerMs(serverNow);
        long triggerServerMs = serverNow + TimeUnit.MINUTES.toMillis(delayMinutes);
        int nextShift = getShiftForServerMs(triggerServerMs);
        if (currentShift != nextShift) {
            return new TimerPlan(false, 0L, "Таймер не установлен, смена трав близка.");
        }
        long triggerAt = localNow + TimeUnit.MINUTES.toMillis(delayMinutes);
        String message = "Таймер установлен на " + delayMinutes + " мин. (рост +5 мин).";
        return new TimerPlan(true, triggerAt, message);
    }

    private void postCutResultToChat(String herb,
                                     TimerPlan timerPlan,
                                     String regNum,
                                     String cellResourcesSummary,
                                     String massSnapshot,
                                     double resourceMass,
                                     String source) {
        String sourceLabel = TextUtils.isEmpty(source) ? "auto_cut" : source;
        StringBuilder builder = new StringBuilder();
        builder.append(MainPhp.buildServerChatTimeHtmlExternal())
                .append("<font color=#006600><b>[")
                .append(escapeHtml(sourceLabel))
                .append("]</b> Авто-Травник: ")
                .append(escapeHtml(herb))
                .append(" срезана. ")
                .append(escapeHtml(timerPlan.message));
        String safeRegNum = safe(regNum);
        String safeSummary = safe(cellResourcesSummary);
        if (!safeRegNum.isEmpty()) {
            if (!safeSummary.isEmpty()) {
                builder.append(" Клетка '")
                        .append(escapeHtml(safeRegNum))
                        .append("' содержит: ")
                        .append(escapeHtml(safeSummary))
                        .append('.');
            } else {
                builder.append(" Клетка '")
                        .append(escapeHtml(safeRegNum))
                        .append("'.");
            }
        }
        String safeMass = safe(massSnapshot);
        if (!safeMass.isEmpty()) {
            builder.append(" Масса: <b>")
                    .append(escapeHtml(safeMass))
                    .append("</b>");
            if (resourceMass > 0d) {
                builder.append(" (<font color=#008800><b>+")
                        .append(formatDouble(resourceMass))
                        .append("</b></font>)");
            }
            builder.append('.');
        } else if (resourceMass > 0d) {
            builder.append(" Масса: <font color=#008800><b>+")
                    .append(formatDouble(resourceMass))
                    .append("</b></font>.");
            safeMass = "+" + formatDouble(resourceMass);
        }
        builder.append("</font>");
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", builder.toString());
        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
        AppLog.d(TRACE_CHAIN, TAG, "chat posted: herb=" + herb
                + ", regNum=" + safeRegNum
                + ", summary=" + safeSummary
                + ", mass=" + safeMass
                + ", source=" + source);
    }

    private LinkedHashMap<String, AutoCutHerb> loadHerbsLocked() {
        LinkedHashMap<String, AutoCutHerb> result = new LinkedHashMap<>();
        String json = prefs.getString(scopedKey(KEY_HERBS_JSON_PREFIX), "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                String id = safeNumeric(item.optString("id", ""));
                String name = safe(item.optString("name", ""));
                if (name.isEmpty()) {
                    continue;
                }
                String key = buildKey(id, name);
                result.put(key, new AutoCutHerb(
                        key,
                        id,
                        name,
                        Math.max(0, item.optInt("skill", 0)),
                        normalizeGrowthMinutes(item.optInt("growthMinutes", DEFAULT_GROWTH_MINUTES)),
                        safeGroup(item.optString("group", GROUP_UNKNOWN)),
                        item.optBoolean("selected", false)));
            }
        } catch (Exception error) {
            AppLog.w(TRACE_CHAIN, TAG, "failed to parse herbs json, reset to seeds", error);
            result.clear();
        }

        boolean changed = mergeSeedHerbs(result);
        if (changed) {
            persistHerbsLocked(result);
        }
        return result;
    }

    private JSONObject loadCellSnapshotsLocked() {
        String raw = prefs.getString(scopedKey(KEY_CELL_SNAPSHOTS_JSON_PREFIX), "{}");
        if (TextUtils.isEmpty(raw)) {
            return new JSONObject();
        }
        try {
            return new JSONObject(raw);
        } catch (Exception error) {
            AppLog.w(TRACE_CHAIN, TAG, "failed to parse cell snapshots json, reset route cache", error);
            return new JSONObject();
        }
    }

    private boolean mergeSeedHerbs(LinkedHashMap<String, AutoCutHerb> herbs) {
        boolean changed = false;
        changed |= mergeSeedHerb(herbs, "86", "Моховик", 0, 60, "5");
        changed |= mergeSeedHerb(herbs, "67", "Кассия", 0, 60, "4");
        changed |= mergeSeedHerb(herbs, "75", "Аралия", 0, 60, "6");
        changed |= mergeSeedHerb(herbs, "114", "Лимон", 5, 120, "7");
        changed |= mergeSeedHerb(herbs, "96", "Осот", 5, 60, "2");
        changed |= mergeSeedHerb(herbs, "444", "Водоросли приозёрные", 5, 60, "2");
        changed |= mergeSeedHerb(herbs, "451", "Сахарный тростник", 5, 60, "11");
        changed |= mergeSeedHerb(herbs, "102", "Пшеница", 5, 60, "11");
        changed |= mergeSeedHerb(herbs, "77", "Гравилат", 10, 120, "2");
        changed |= mergeSeedHerb(herbs, "84", "Хвоя", 10, 60, "5");
        changed |= mergeSeedHerb(herbs, "440", "Перец Форпостной", 10, 60, "11");
        changed |= mergeSeedHerb(herbs, "447", "Боровик", 10, 60, "9");
        changed |= mergeSeedHerb(herbs, "448", "Лисички", 10, 60, "9");
        changed |= mergeSeedHerb(herbs, "47", "Каланхоэ", 20, 60, "3");
        changed |= mergeSeedHerb(herbs, "83", "Сосна", 20, 60, "9");
        changed |= mergeSeedHerb(herbs, "439", "Перец Октальский", 20, 60, "11");
        changed |= mergeSeedHerb(herbs, "450", "Томат", 20, 60, "11");
        changed |= mergeSeedHerb(herbs, "443", "Картофель", 20, 60, "11");
        changed |= mergeSeedHerb(herbs, "438", "Сельдерей", 20, 60, "11");
        changed |= mergeSeedHerb(herbs, "437", "Петрушка Кровавобережная", 20, 60, "11");
        changed |= mergeSeedHerb(herbs, "87", "Бадан", 30, 60, "2");
        changed |= mergeSeedHerb(herbs, "442", "Чеснок", 30, 60, "11");
        changed |= mergeSeedHerb(herbs, "441", "Укроп болотный", 30, 60, "11");
        changed |= mergeSeedHerb(herbs, "103", "Тарвин", 40, 60, "4");
        changed |= mergeSeedHerb(herbs, "108", "Змеиный корень", 50, 60, "1");
        changed |= mergeSeedHerb(herbs, "118", "Виноград светлый", 50, 60, "8");
        changed |= mergeSeedHerb(herbs, "119", "Виноград тёмный", 50, 60, "8");
        changed |= mergeSeedHerb(herbs, "91", "Трифоль", 60, 60, "2");
        changed |= mergeSeedHerb(herbs, "115", "Бегония", 75, 60, "6");
        changed |= mergeSeedHerb(herbs, "58", "Алтей", 90, 60, "6");
        changed |= mergeSeedHerb(herbs, "76", "Бессмертник", 105, 60, "6");
        changed |= mergeSeedHerb(herbs, "98", "Катарантус", 105, 60, "1");
        changed |= mergeSeedHerb(herbs, "104", "Жизненное дерево", 110, 60, "7");
        changed |= mergeSeedHerb(herbs, "60", "Астрагал", 110, 60, "4");
        changed |= mergeSeedHerb(herbs, "80", "Ведьмино кольцо", 120, 60, "9");
        changed |= mergeSeedHerb(herbs, "88", "Болотник", 120, 60, "2");
        changed |= mergeSeedHerb(herbs, "94", "Анис", 130, 60, "7");
        changed |= mergeSeedHerb(herbs, "90", "Маклея", 130, 60, "2");
        changed |= mergeSeedHerb(herbs, "48", "Каперс", 140, 120, "7");
        changed |= mergeSeedHerb(herbs, "56", "Подберёзовик", 140, 60, "5");
        changed |= mergeSeedHerb(herbs, "112", "Кентарийская дикая роза", 150, 120, "3");
        changed |= mergeSeedHerb(herbs, "63", "Девясил", 150, 60, "4");
        changed |= mergeSeedHerb(herbs, "89", "Брусника", 160, 120, "2");
        changed |= mergeSeedHerb(herbs, "66", "Истод", 160, 60, "3");
        changed |= mergeSeedHerb(herbs, "110", "Прагениана", 170, 120, "3");
        changed |= mergeSeedHerb(herbs, "92", "Сыроежка", 170, 60, "5");
        changed |= mergeSeedHerb(herbs, "107", "Ландыш", 180, 120, "4");
        changed |= mergeSeedHerb(herbs, "49", "Кориандр", 180, 60, "7");
        changed |= mergeSeedHerb(herbs, "106", "Люминисцентная поганка", 190, 60, "9");
        changed |= mergeSeedHerb(herbs, "113", "Антуриум хрустальный", 190, 60, "2");
        changed |= mergeSeedHerb(herbs, "74", "Алоэ", 200, 120, "7");
        changed |= mergeSeedHerb(herbs, "79", "Термопсис", 20, 60, "6");
        changed |= mergeSeedHerb(herbs, "117", "Смертоцвет", 210, 120, "3");
        changed |= mergeSeedHerb(herbs, "65", "Карагана", 210, 60, "9");
        changed |= mergeSeedHerb(herbs, "52", "Берёза", 220, 120, "5");
        changed |= mergeSeedHerb(herbs, "68", "Кипрей", 220, 60, "5");
        changed |= mergeSeedHerb(herbs, "57", "Поганка", 230, 60, "5");
        changed |= mergeSeedHerb(herbs, "93", "Мухомор", 230, 60, "9");
        changed |= mergeSeedHerb(herbs, "50", "Крестовник", 240, 60, "11");
        changed |= mergeSeedHerb(herbs, "101", "Парибигус", 240, 60, "6");
        changed |= mergeSeedHerb(herbs, "46", "Айва", 250, 120, "7");
        changed |= mergeSeedHerb(herbs, "111", "Камелия", 250, 120, "3");
        changed |= mergeSeedHerb(herbs, "69", "Лён", 250, 60, "11");
        changed |= mergeSeedHerb(herbs, "82", "Дягиль", 300, 120, "9");
        changed |= mergeSeedHerb(herbs, "99", "Коризиус", 300, 60, "5");
        changed |= mergeSeedHerb(herbs, "53", "Дуб", 350, 120, "5");
        changed |= mergeSeedHerb(herbs, "61", "Вереск", 350, 60, "4");
        changed |= mergeSeedHerb(herbs, "51", "Секуринега", 400, 120, "3");
        changed |= mergeSeedHerb(herbs, "97", "Фенхель", 400, 60, "1");
        changed |= mergeSeedHerb(herbs, "81", "Амми", 400, 60, "7");
        changed |= mergeSeedHerb(herbs, "105", "Секвойя", 400, 60, "4");
        changed |= mergeSeedHerb(herbs, "73", "Эфедра", 550, 60, "4");
        changed |= mergeSeedHerb(herbs, "116", "Кипарис", 600, 120, "9");
        changed |= mergeSeedHerb(herbs, "54", "Ива", 600, 60, "5");
        changed |= mergeSeedHerb(herbs, "55", "Лиственница", 600, 60, "9");
        changed |= mergeSeedHerb(herbs, "64", "Дурман", 640, 120, "2");
        changed |= mergeSeedHerb(herbs, "109", "Пустынный агапантус", 640, 60, "1");
        changed |= mergeSeedHerb(herbs, "59", "Арника", 680, 60, "3");
        changed |= mergeSeedHerb(herbs, "72", "Чернокорень", 680, 60, "4");
        changed |= mergeSeedHerb(herbs, "71", "Рапонтикум", 700, 120, "3");
        changed |= mergeSeedHerb(herbs, "95", "Инжир", 720, 120, "1");
        changed |= mergeSeedHerb(herbs, "100", "Куфис", 720, 60, "6");
        changed |= mergeSeedHerb(herbs, "78", "Родиола", 720, 60, "6");
        changed |= mergeSeedHerb(herbs, "62", "Галега", 780, 60, "3");
        changed |= mergeSeedHerb(herbs, "85", "Подосиновик", 780, 60, "5");
        changed |= mergeSeedHerb(herbs, "70", "Плаун", 800, 60, "2");
        return changed;
    }

    private boolean mergeSeedHerb(LinkedHashMap<String, AutoCutHerb> herbs,
                                  String id,
                                  String name,
                                  int skill,
                                  int growthMinutes,
                                  String group) {
        AutoCutHerb existing = findHerbLocked(herbs, id, name);
        String key = buildKey(id, name);
        if (existing == null) {
            herbs.put(key, new AutoCutHerb(key, id, name, skill, growthMinutes, group, false));
            return true;
        }
        if (!existing.key.equals(key)) {
            herbs.remove(existing.key);
            herbs.put(key, new AutoCutHerb(key, id, name, existing.skill, existing.growthMinutes, existing.group, existing.selected));
            return true;
        }
        return false;
    }

    private void persistHerbsLocked(LinkedHashMap<String, AutoCutHerb> herbs) {
        JSONArray array = new JSONArray();
        for (AutoCutHerb herb : herbs.values()) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", herb.id);
                item.put("name", herb.name);
                item.put("skill", herb.skill);
                item.put("growthMinutes", herb.growthMinutes);
                item.put("group", herb.group);
                item.put("selected", herb.selected);
                array.put(item);
            } catch (Exception error) {
                AppLog.w(TRACE_CHAIN, TAG, "failed to serialize herb: " + herb.name, error);
            }
        }
        prefs.edit().putString(scopedKey(KEY_HERBS_JSON_PREFIX), array.toString()).apply();
    }

    private AutoCutHerb findHerbLocked(LinkedHashMap<String, AutoCutHerb> herbs, String id, String name) {
        String safeId = safeNumeric(id);
        if (!safeId.isEmpty()) {
            AutoCutHerb byId = herbs.get("id:" + safeId);
            if (byId != null) {
                return byId;
            }
        }
        String normalizedName = normalizeName(name);
        if (normalizedName.isEmpty()) {
            return null;
        }
        for (AutoCutHerb herb : herbs.values()) {
            if (normalizeName(herb.name).equals(normalizedName)) {
                return herb;
            }
        }
        return null;
    }

    private String scopedKey(String prefix) {
        String nick = "default";
        if (AppVars.Profile != null && !TextUtils.isEmpty(AppVars.Profile.UserNick)) {
            nick = AppVars.Profile.UserNick.trim().toLowerCase(Locale.ROOT);
        }
        return prefix + nick;
    }

    private static String normalizeCellsCsv(String csv) {
        List<String> cells = parseCellsCsv(csv);
        StringBuilder builder = new StringBuilder();
        for (String cell : cells) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(cell);
        }
        return builder.toString();
    }

    private static List<String> parseCellsCsv(String csv) {
        ArrayList<String> result = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) {
            return result;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        String[] parts = csv.split("[,;\\s]+");
        for (String part : parts) {
            String cell = part == null ? "" : part.trim();
            if (cell.matches("\\d{1,4}-\\d{1,5}")) {
                unique.add(cell);
            }
        }
        result.addAll(unique);
        return result;
    }

    private static List<CellHerbEntry> parseCellSnapshotEntries(String list) {
        ArrayList<CellHerbEntry> result = new ArrayList<>();
        if (TextUtils.isEmpty(list)) {
            return result;
        }
        String[] entries = list.split("\\|");
        for (String rawEntry : entries) {
            String entry = safe(rawEntry);
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.lastIndexOf(':');
            String name = separator >= 0 ? entry.substring(0, separator).trim() : entry;
            int count = separator >= 0 ? parseIntSafe(entry.substring(separator + 1), 0) : 1;
            if (!name.isEmpty()) {
                result.add(new CellHerbEntry(name, count));
            }
        }
        return result;
    }

    private static String buildKey(String id, String name) {
        String safeId = safeNumeric(id);
        if (!safeId.isEmpty()) {
            return "id:" + safeId;
        }
        return "name:" + normalizeName(name);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeNumeric(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "").trim();
    }

    private static String safeGroup(String value) {
        String group = safe(value);
        return group.isEmpty() ? GROUP_UNKNOWN : group;
    }

    private static int normalizeGrowthMinutes(int value) {
        return value > 0 ? value : DEFAULT_GROWTH_MINUTES;
    }

    private static String normalizeName(String value) {
        return safe(value).replace('ё', 'е').replace('Ё', 'Е').toLowerCase(Locale.ROOT);
    }

    private static long getServerNowMs(long localNowMs) {
        if (AppVars.Profile != null && AppVars.Profile.ServDiff != Long.MIN_VALUE) {
            return localNowMs - AppVars.Profile.ServDiff;
        }
        return localNowMs;
    }

    private static String resolveCurrentRegNum() {
        if (AppVars.Profile == null || TextUtils.isEmpty(AppVars.Profile.MapLocation)) {
            return "";
        }
        return AppVars.Profile.MapLocation.trim();
    }

    private static String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Snapshot функций, которые AutoCut временно поставил на паузу ради inventory cleanup. */
    private static final class CleanupPauseSnapshot {
        boolean autoFish;
        boolean autoSkin;
        boolean autoBait;
        boolean autoCompass;
        boolean autoAttack;
        boolean autoInvisible;
        boolean autoDetect;
        boolean autoSummon;
        boolean autoDrink;
        boolean autoTreasure;
        boolean autoBoss;
        boolean autoRefresh;
        boolean autoMoving;
        String autoMovingDestination;
        ru.neverlands.anclient.utils.MapPath autoMovingMapPath;
        String autoMovingNextJump;
        int autoMovingJumps;
        ru.neverlands.anclient.model.CityGateType autoMovingCityGate;
        long capturedAtMs;

        boolean hasPausedAnything() {
            return autoFish || autoSkin || autoBait || autoCompass || autoAttack || autoInvisible
                    || autoDetect || autoSummon || autoDrink || autoTreasure || autoBoss
                    || autoRefresh || autoMoving;
        }

        String describePaused() {
            StringBuilder builder = new StringBuilder();
            appendPaused(builder, autoFish, "auto_fish");
            appendPaused(builder, autoSkin, "auto_skin");
            appendPaused(builder, autoBait, "auto_bait");
            appendPaused(builder, autoCompass, "auto_compass");
            appendPaused(builder, autoAttack, "auto_attack");
            appendPaused(builder, autoInvisible, "auto_invisible");
            appendPaused(builder, autoDetect, "auto_detect");
            appendPaused(builder, autoSummon, "auto_summon");
            appendPaused(builder, autoDrink, "auto_drink");
            appendPaused(builder, autoTreasure, "auto_treasure");
            appendPaused(builder, autoBoss, "auto_boss");
            appendPaused(builder, autoRefresh, "auto_refresh");
            appendPaused(builder, autoMoving, "auto_moving");
            return builder.length() == 0 ? "none" : builder.toString();
        }

        private static void appendPaused(StringBuilder builder, boolean enabled, String label) {
            if (!enabled) {
                return;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(label);
        }
    }

    /** План таймера роста после среза; отделяет расчёт от создания `AppTimer`. */
    private static final class TimerPlan {
        /** true, если смена трав не близко и таймер можно создавать. */
        final boolean shouldCreateTimer;
        /** Локальное время срабатывания таймера в milliseconds. */
        final long triggerAtMs;
        /** Текст для chat-report, почему таймер создан или пропущен. */
        final String message;

        TimerPlan(boolean shouldCreateTimer, long triggerAtMs, String message) {
            this.shouldCreateTimer = shouldCreateTimer;
            this.triggerAtMs = triggerAtMs;
            this.message = message;
        }
    }

    /** Due herb timer, который должен вернуть AutoCut на клетку внутри текущей смены. */
    private static final class DueHerbTimer {
        final int timerId;
        final String cell;
        final String description;
        final long readyAtMs;

        DueHerbTimer(int timerId, String cell, String description, long readyAtMs) {
            this.timerId = timerId;
            this.cell = cell;
            this.description = description;
            this.readyAtMs = readyAtMs;
        }
    }

    /** Persisted checked-set одного server-window смены трав. */
    private static final class ShiftCheckedCells {
        /** Номер смены 1..4, вычисленный по server time + `Profile.ServDiff`. */
        final int shift;
        /** Начало конкретного server-window смены; не даёт переиспользовать checked на следующий день. */
        final long shiftStartServerMs;
        /** Нормализованные regNum клеток, уже проверенных в этой смене. */
        final LinkedHashSet<String> cells = new LinkedHashSet<>();

        ShiftCheckedCells(int shift, long shiftStartServerMs) {
            this.shift = shift;
            this.shiftStartServerMs = shiftStartServerMs;
        }
    }

    /** Parsed `current/max` из `SetAutoFishMassa(...)`. */
    private static final class MassSnapshot {
        /** Текущая масса inventory; сейчас используется только в логах. */
        double current;
        /** Максимальная масса inventory; задаёт 10% cleanup threshold. */
        double max;
    }

    /** Одна запись snapshot-а клетки: имя травы и доступное количество/флаг среза. */
    private static final class CellHerbEntry {
        final String name;
        final int count;

        CellHerbEntry(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    /** Редактируемое окно смены трав. */
    public static final class AutoCutShift {
        public final int startHour;
        public final int startMinute;
        public final int endHour;
        public final int endMinute;

        AutoCutShift(int startHour, int startMinute, int endHour, int endMinute) {
            this.startHour = startHour;
            this.startMinute = startMinute;
            this.endHour = endHour;
            this.endMinute = endMinute;
        }

        boolean isValid() {
            return isTimeValid(startHour, startMinute)
                    && isTimeValid(endHour, endMinute)
                    && startMinuteOfDay() != endMinuteOfDay();
        }

        boolean containsMinuteOfDay(int minuteOfDay) {
            int start = startMinuteOfDay();
            int end = endMinuteOfDay();
            if (start < end) {
                return minuteOfDay >= start && minuteOfDay < end;
            }
            return minuteOfDay >= start || minuteOfDay < end;
        }

        String displayRange() {
            return two(startHour) + ":" + two(startMinute) + "-" + two(endHour) + ":" + two(endMinute);
        }

        private int startMinuteOfDay() {
            return startHour * 60 + startMinute;
        }

        private int endMinuteOfDay() {
            return endHour * 60 + endMinute;
        }

        private static boolean isTimeValid(int hour, int minute) {
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
        }

        private static String two(int value) {
            return value < 10 ? "0" + value : String.valueOf(value);
        }
    }

    /**
     * UI/persisted DTO травы.
     *
     * Связь с ПК-версией:
     * - сохраняет понятные поля из `FormMainHerbs`/`FormSettingsAutoCut`;
     * - `key` стабилизирует checkbox state между discovery по имени и discovery по id;
     * - `selected` определяет, будет ли `AlchemyAjaxPhp` отправлять `act=3` для этой травы.
     */
    public static final class AutoCutHerb {
        /** Persisted key: `id:<res_id>` или `name:<normalizedName>`. */
        public final String key;
        /** Server `res_id`, если известен из `RESO@` или seed-словаря. */
        public final String id;
        /** Отображаемое имя травы. */
        public final String name;
        /** Минимальное умение травника; сейчас metadata для UI/отладки. */
        public final int skill;
        /** Время роста в минутах, используется для timer plan. */
        public final int growthMinutes;
        /** Группа трав из справочника или `Не определено`. */
        public final String group;
        /** Checkbox пользователя: разрешён ли автосрез этой травы. */
        public final boolean selected;

        AutoCutHerb(String key, String id, String name, int skill, int growthMinutes, String group, boolean selected) {
            this.key = key;
            this.id = id;
            this.name = name;
            this.skill = skill;
            this.growthMinutes = growthMinutes;
            this.group = group;
            this.selected = selected;
        }

        /** Immutable-copy helper для checkbox changes. */
        AutoCutHerb withSelected(boolean selected) {
            return new AutoCutHerb(key, id, name, skill, growthMinutes, group, selected);
        }

        /** Immutable-copy helper для long-press metadata edits. */
        AutoCutHerb withMeta(int skill, int growthMinutes, String group) {
            return new AutoCutHerb(key, id, name, skill, growthMinutes, group, selected);
        }

        /** Формирует строку checkbox-а в настройках AutoCut. */
        public String displayLabel() {
            String idPart = TextUtils.isEmpty(id) ? "" : (" #" + id);
            return name + idPart + " | умение " + skill + " | рост " + growthMinutes + " мин | группа " + group;
        }
    }
}
