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

import ru.neverlands.anclient.MainActivity;
import ru.neverlands.anclient.model.AppTimer;
import ru.neverlands.anclient.postfilter.MainPhp;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.SessionManager;

/**
 * Runtime и настройки Авто-Травника.
 *
 * Контур дополняет уже существующий флаг `AUTO_CUT` в {@link AutoFunctionsManager}:
 * этот класс не включает/выключает функцию, а хранит выбранные травы, клетки поиска
 * и ставит таймеры после подтвержденного `alchemy_ajax.php?act=3`.
 */
public final class AutoCutManager {
    private static final String TAG = "AutoCutManager";
    public static final String TRACE_CHAIN = "AUTO_CUT_TRACE";

    private static final String PREFS_NAME = "auto_cut_prefs";
    private static final String KEY_HERBS_JSON_PREFIX = "herbs_json_";
    private static final String KEY_CELLS_CSV_PREFIX = "cells_csv_";
    private static final String KEY_WRITE_CHAT_PREFIX = "write_chat_";
    private static final String KEY_CLEANUP_ENABLED_PREFIX = "cleanup_enabled_";
    private static final String KEY_CHECKED_SHIFT_PREFIX = "checked_shift_";
    private static final String GROUP_UNKNOWN = "Не определено";
    private static final int DEFAULT_GROWTH_MINUTES = 60;
    private static final long TRACE_CUT_NAME_TTL_MS = 60_000L;
    private static final long ROUTE_NEXT_DELAY_MS = 450L;
    private static final double CLEANUP_FALLBACK_THRESHOLD_MASS = 10d;

    private static AutoCutManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private volatile String lastTraceCutName = "";
    private volatile long lastTraceCutAtMs = 0L;

    private AutoCutManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AutoCutManager getInstance(Context context) {
        if (instance == null) {
            instance = new AutoCutManager(context);
        }
        return instance;
    }

    public synchronized String getCellsCsv() {
        return prefs.getString(scopedKey(KEY_CELLS_CSV_PREFIX), "");
    }

    public synchronized void setCellsCsv(String csv) {
        prefs.edit().putString(scopedKey(KEY_CELLS_CSV_PREFIX), normalizeCellsCsv(csv)).apply();
    }

    public synchronized List<String> getSearchCells() {
        return parseCellsCsv(getCellsCsv());
    }

    public synchronized boolean isWriteChatEnabled() {
        return prefs.getBoolean(scopedKey(KEY_WRITE_CHAT_PREFIX), true);
    }

    public synchronized void setWriteChatEnabled(boolean enabled) {
        prefs.edit().putBoolean(scopedKey(KEY_WRITE_CHAT_PREFIX), enabled).apply();
    }

    public synchronized boolean isCleanupEnabled() {
        return prefs.getBoolean(scopedKey(KEY_CLEANUP_ENABLED_PREFIX), false);
    }

    public synchronized void setCleanupEnabled(boolean enabled) {
        prefs.edit().putBoolean(scopedKey(KEY_CLEANUP_ENABLED_PREFIX), enabled).apply();
    }

    public synchronized List<AutoCutHerb> getHerbs() {
        return new ArrayList<>(loadHerbsLocked().values());
    }

    public synchronized int getSelectedHerbCount() {
        int count = 0;
        for (AutoCutHerb herb : loadHerbsLocked().values()) {
            if (herb.selected) {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean isHerbSelected(String id, String name) {
        AutoCutHerb herb = findHerbLocked(loadHerbsLocked(), id, name);
        return herb != null && herb.selected;
    }

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
        AppLog.d(TRACE_CHAIN, TAG, "HerbsList observed count=" + count);
    }

    public void onTraceCut(String herb) {
        String safeHerb = safe(herb);
        if (safeHerb.isEmpty()) {
            return;
        }
        lastTraceCutName = safeHerb;
        lastTraceCutAtMs = System.currentTimeMillis();
        AppLog.d(TRACE_CHAIN, TAG, "TraceCut observed: " + safeHerb);
    }

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

    public void markHerbCut(String id, String name, int growthMinutes, String regNum, String source) {
        markHerbCut(id, name, growthMinutes, regNum, source, 0d);
    }

    public void markHerbCut(String id, String name, int growthMinutes, String regNum, String source, double resourceMass) {
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
        if (timerPlan.shouldCreateTimer) {
            AppTimer timer = new AppTimer();
            timer.description = "Вырастет " + safeName + " на " + safeRegNum;
            timer.triggerTime = timerPlan.triggerAtMs;
            timer.isHerb = true;
            AppTimerManager.getInstance(appContext).addAppTimer(timer);
        }
        if (isWriteChatEnabled()) {
            postCutResultToChat(safeName, timerPlan, source);
        }
        markCurrentCellChecked("cut_success:" + source);
        boolean cleanupPending = maybeRequestCleanupAfterCut(resourceMass, source);
        AppLog.i(TRACE_CHAIN, TAG, "cut success: herb=" + safeName
                + ", regNum=" + safeRegNum
                + ", growth=" + growth
                + ", timer=" + timerPlan.shouldCreateTimer
                + ", cleanupPending=" + cleanupPending
                + ", source=" + source);
        if (!cleanupPending) {
            routeNextCell("cut_success:" + source);
        }
    }

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
        return !isCellCheckedForCurrentShift(current);
    }

    public void onAutoCutEnabled(AutoFunctionsManager manager) {
        AppVars.AutoCutCheckSickle = true;
        AppVars.AutoCutArmedSickle = false;
        AppVars.AutoCutSickleHand = "";
        AppVars.AutoCutSickleHandD = "";
        AppVars.AutoCutCleanupPending = false;
        AppVars.AutoCutCleanupReason = "";
        requestMainFrameReload("enabled_sickle_check");
        routeNextCellIfCurrentIsNotReady(manager, "enabled");
    }

    public void onAutoCutDisabled() {
        AppVars.AutoCutCheckSickle = false;
        AppVars.AutoCutArmedSickle = false;
        AppVars.AutoCutSickleHand = "";
        AppVars.AutoCutSickleHandD = "";
        AppVars.AutoCutCleanupPending = false;
        AppVars.AutoCutCleanupReason = "";
        AppVars.AutoCutHarvestedMassSinceCleanup = 0d;
    }

    public boolean isSickleReadyForCut() {
        return AppVars.AutoCutArmedSickle && !AppVars.AutoCutCheckSickle;
    }

    public void requestSickleCheckBeforeCut(String source) {
        AppVars.AutoCutCheckSickle = true;
        AppVars.AutoCutArmedSickle = false;
        AppLog.i(TRACE_CHAIN, TAG, "sickle check requested before cut, source=" + source);
        requestMainFrameReload("sickle_check:" + source);
    }

    public void onScanWithoutSelectedHerb(String source) {
        markCurrentCellChecked("no_selected:" + source);
        routeNextCell("no_selected:" + source);
    }

    public void onCleanupCompleted(String source) {
        AppVars.AutoCutCleanupPending = false;
        AppVars.AutoCutCleanupReason = "";
        AppVars.AutoCutHarvestedMassSinceCleanup = 0d;
        AppLog.i(TRACE_CHAIN, TAG, "cleanup completed, source=" + source);
        routeNextCell("cleanup_completed:" + source);
    }

    public void updateMassSnapshot(String mass) {
        MassSnapshot snapshot = parseMassSnapshot(mass);
        if (snapshot.max > 0d) {
            AppVars.AutoCutKnownMassMax = snapshot.max;
            AppLog.d(TRACE_CHAIN, TAG, "mass snapshot updated: current=" + snapshot.current + ", max=" + snapshot.max);
        }
    }

    private void routeNextCellIfCurrentIsNotReady(AutoFunctionsManager manager, String source) {
        if (manager == null) {
            return;
        }
        List<String> cells = getSearchCells();
        if (cells.isEmpty()) {
            return;
        }
        String current = resolveCurrentRegNum();
        if (!current.isEmpty() && cells.contains(current) && !isCellCheckedForCurrentShift(current)) {
            AppLog.d(TRACE_CHAIN, TAG, "route skip: current cell ready for Ogl, source=" + source + ", cell=" + current);
            return;
        }
        routeNextCellWithManager(manager, source);
    }

    private void routeNextCell(String source) {
        try {
            AutoFunctionsManager manager = AutoFunctionsManager.getInstance(appContext);
            routeNextCellWithManager(manager, source);
        } catch (Exception e) {
            AppLog.w(TRACE_CHAIN, TAG, "route next failed, source=" + source, e);
        }
    }

    private void routeNextCellWithManager(AutoFunctionsManager manager, String source) {
        List<String> cells = getSearchCells();
        if (cells.isEmpty()) {
            AppLog.d(TRACE_CHAIN, TAG, "route next skipped: cells list empty, source=" + source);
            return;
        }
        String next = findNextUncheckedCell(cells, resolveCurrentRegNum());
        if (TextUtils.isEmpty(next)) {
            AppLog.i(TRACE_CHAIN, TAG, "route next skipped: all cells checked for current shift, source=" + source);
            return;
        }
        if (AppVars.AutoMoving && next.equals(AppVars.AutoMovingDestinaton)) {
            AppLog.d(TRACE_CHAIN, TAG, "route next skip: navigator already goes to " + next + ", source=" + source);
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> manager.startAutoMoving(next), ROUTE_NEXT_DELAY_MS);
        AppLog.i(TRACE_CHAIN, TAG, "route next: destination=" + next + ", source=" + source);
    }

    private String findNextUncheckedCell(List<String> cells, String current) {
        if (cells == null || cells.isEmpty()) {
            return "";
        }
        int startIndex = cells.indexOf(current);
        for (int offset = 1; offset <= cells.size(); offset++) {
            int index = startIndex >= 0 ? (startIndex + offset) % cells.size() : offset - 1;
            String cell = cells.get(index);
            if (!isCellCheckedForCurrentShift(cell)) {
                return cell;
            }
        }
        return "";
    }

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
        AppVars.AutoCutCleanupPending = true;
        AppVars.AutoCutCleanupReason = "mass_delta:" + source;
        requestMainFrameReload("cleanup:" + source);
        AppLog.i(TRACE_CHAIN, TAG, "cleanup requested: delta="
                + AppVars.AutoCutHarvestedMassSinceCleanup + ", threshold=" + threshold);
        return true;
    }

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

    private synchronized boolean isCellCheckedForCurrentShift(String cell) {
        if (TextUtils.isEmpty(cell)) {
            return false;
        }
        return loadCheckedCellsLocked().cells.contains(cell.trim());
    }

    private ShiftCheckedCells loadCheckedCellsLocked() {
        int shift = getShift(getServerNowMs(System.currentTimeMillis()));
        String raw = prefs.getString(scopedKey(KEY_CHECKED_SHIFT_PREFIX), "");
        ShiftCheckedCells result = new ShiftCheckedCells(shift);
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        String[] parts = raw.split("\\|", 2);
        int storedShift = -1;
        try {
            storedShift = Integer.parseInt(parts[0]);
        } catch (Exception ignored) {
        }
        if (storedShift != shift) {
            return result;
        }
        if (parts.length > 1) {
            result.cells.addAll(parseCellsCsv(parts[1]));
        }
        return result;
    }

    private void persistCheckedCellsLocked(ShiftCheckedCells checked) {
        StringBuilder value = new StringBuilder();
        value.append(checked.shift).append('|');
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

    private TimerPlan buildTimerPlan(String herb, int growthMinutes, String regNum) {
        long localNow = System.currentTimeMillis();
        long serverNow = getServerNowMs(localNow);
        int currentShift = getShift(serverNow);
        long firstWindowMs = serverNow + TimeUnit.MINUTES.toMillis(Math.max(1, growthMinutes) - 2L);
        int nextShift = getShift(firstWindowMs);
        if (currentShift != nextShift) {
            return new TimerPlan(false, 0L, "Таймер не установлен, смена трав близка.");
        }
        long triggerAt = localNow + TimeUnit.MINUTES.toMillis(Math.max(1, growthMinutes) - 2L + 30L);
        String message = growthMinutes >= 120
                ? "Таймер установлен на 2 часа."
                : "Таймер установлен на 1 час.";
        if (!TextUtils.isEmpty(regNum)) {
            message += " Клетка: " + regNum + ".";
        }
        return new TimerPlan(true, triggerAt, message);
    }

    private void postCutResultToChat(String herb, TimerPlan timerPlan, String source) {
        String sourceLabel = TextUtils.isEmpty(source) ? "auto_cut" : source;
        String message = MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#006600><b>[" + escapeHtml(sourceLabel) + "]</b> Авто-Травник: "
                + escapeHtml(herb)
                + " срезана. "
                + escapeHtml(timerPlan.message)
                + "</font>";
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
        AppLog.d(TRACE_CHAIN, TAG, "chat posted: herb=" + herb + ", source=" + source);
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

    private boolean mergeSeedHerbs(LinkedHashMap<String, AutoCutHerb> herbs) {
        boolean changed = false;
        changed |= mergeSeedHerb(herbs, "437", "Петрушка кровавобережная", 20, 60, "11");
        changed |= mergeSeedHerb(herbs, "442", "Чеснок", 30, 60, "11");
        changed |= mergeSeedHerb(herbs, "443", "Картофель", 20, 60, "11");
        changed |= mergeSeedHerb(herbs, "450", "Томат", 20, 60, "11");
        changed |= mergeSeedHerb(herbs, "451", "Сахарный тростник", 5, 60, "11");
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

    private static int getShift(long serverMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(serverMs);
        int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        if (minutes < 50 || minutes >= 18 * 60 + 50) {
            return 4;
        }
        if (minutes < 6 * 60 + 50) {
            return 1;
        }
        if (minutes < 12 * 60 + 50) {
            return 2;
        }
        return 3;
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

    private static final class TimerPlan {
        final boolean shouldCreateTimer;
        final long triggerAtMs;
        final String message;

        TimerPlan(boolean shouldCreateTimer, long triggerAtMs, String message) {
            this.shouldCreateTimer = shouldCreateTimer;
            this.triggerAtMs = triggerAtMs;
            this.message = message;
        }
    }

    private static final class ShiftCheckedCells {
        final int shift;
        final LinkedHashSet<String> cells = new LinkedHashSet<>();

        ShiftCheckedCells(int shift) {
            this.shift = shift;
        }
    }

    private static final class MassSnapshot {
        double current;
        double max;
    }

    public static final class AutoCutHerb {
        public final String key;
        public final String id;
        public final String name;
        public final int skill;
        public final int growthMinutes;
        public final String group;
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

        AutoCutHerb withSelected(boolean selected) {
            return new AutoCutHerb(key, id, name, skill, growthMinutes, group, selected);
        }

        AutoCutHerb withMeta(int skill, int growthMinutes, String group) {
            return new AutoCutHerb(key, id, name, skill, growthMinutes, group, selected);
        }

        public String displayLabel() {
            String idPart = TextUtils.isEmpty(id) ? "" : (" #" + id);
            return name + idPart + " | умение " + skill + " | рост " + growthMinutes + " мин | группа " + group;
        }
    }
}
