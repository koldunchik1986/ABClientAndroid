package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.text.TextUtils;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import ru.neverlands.abclient.model.AppTimer;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Менеджер пользовательских таймеров (порт `ABClient/AppTimerManager.cs` + часть `FormMainTimers.cs`).
 *
 * Назначение:
 * - хранить список таймеров, отсортированный по времени срабатывания;
 * - выполнять due-таймеры (зелье / перемещение / комплект / простой таймер);
 * - сохранять и восстанавливать таймеры между перезапусками по текущему профилю.
 *
 * Зависимости:
 * - `FastActionManager`: запуск fast-цепочки для таймера-зелья;
 * - `AutoFunctionsManager`: запуск навигации для таймера-перемещения;
 * - `AppVars.WearComplect`: установка имени комплекта с последующим `main.php` reload;
 * - `LocalBroadcastManager + AppVars.ACTION_WEBVIEW_LOAD_URL`: reload главного фрейма.
 */
public class AppTimerManager {
    private static final String TAG = "AppTimerManager";
    private static final String PREFS_NAME = "app_timers_prefs";
    private static final String KEY_TIMERS_JSON_PREFIX = "timers_json_";
    private static final String KEY_TIMER_SOUND_PREFIX = "timer_sound_";

    private static AppTimerManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final List<AppTimer> listAppTimers = new ArrayList<>();
    private String loadedStorageKey = "";

    private AppTimerManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AppTimerManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppTimerManager(context);
        }
        return instance;
    }

    public synchronized void setAppTimers(List<AppTimer> appTimers) {
        ensureLoadedForCurrentProfileLocked();
        listAppTimers.clear();
        if (appTimers == null) {
            persistLocked();
            return;
        }
        for (AppTimer appTimer : appTimers) {
            addAppTimerInternalLocked(appTimer);
        }
        persistLocked();
    }

    public synchronized AppTimer addAppTimer(AppTimer appTimer) {
        ensureLoadedForCurrentProfileLocked();
        AppTimer added = addAppTimerInternalLocked(appTimer);
        if (added != null) {
            persistLocked();
        }
        return added;
    }

    public synchronized boolean updateTimer(int timerId, AppTimer updatedTimer) {
        ensureLoadedForCurrentProfileLocked();
        int index = findIndexByIdLocked(timerId);
        if (index < 0 || updatedTimer == null) {
            return false;
        }

        AppTimer prepared = updatedTimer.copy();
        prepared.id = timerId;
        prepared.description = safe(prepared.description);
        prepared.potion = safe(prepared.potion);
        prepared.destination = safe(prepared.destination);
        prepared.complect = safe(prepared.complect);
        if (prepared.triggerTime <= 0L) {
            prepared.triggerTime = System.currentTimeMillis();
        }

        listAppTimers.remove(index);
        insertSortedLocked(prepared);
        persistLocked();
        return true;
    }

    public synchronized List<AppTimer> getTimers() {
        ensureLoadedForCurrentProfileLocked();
        List<AppTimer> result = new ArrayList<>(listAppTimers.size());
        for (AppTimer timer : listAppTimers) {
            result.add(timer.copy());
        }
        return result;
    }

    public synchronized void removeTimerAt(int index) {
        ensureLoadedForCurrentProfileLocked();
        if (index < 0 || index >= listAppTimers.size()) {
            return;
        }
        listAppTimers.remove(index);
        persistLocked();
    }

    public synchronized void removeTimerById(int timerId) {
        ensureLoadedForCurrentProfileLocked();
        int index = findIndexByIdLocked(timerId);
        if (index < 0) {
            return;
        }
        listAppTimers.remove(index);
        persistLocked();
    }

    public synchronized void removeTimerLastAdded() {
        ensureLoadedForCurrentProfileLocked();
        int maxId = 0;
        int maxIndex = -1;
        for (int index = 0; index < listAppTimers.size(); index++) {
            AppTimer appTimer = listAppTimers.get(index);
            if (appTimer.id <= maxId) {
                continue;
            }
            maxId = appTimer.id;
            maxIndex = index;
        }
        if (maxIndex >= 0) {
            listAppTimers.remove(maxIndex);
            persistLocked();
        }
    }

    public synchronized boolean isTimerSoundEnabled() {
        ensureLoadedForCurrentProfileLocked();
        return prefs.getBoolean(getTimerSoundKeyLocked(), true);
    }

    public synchronized void setTimerSoundEnabled(boolean enabled) {
        ensureLoadedForCurrentProfileLocked();
        prefs.edit().putBoolean(getTimerSoundKeyLocked(), enabled).apply();
    }

    /**
     * Обработка сработавших таймеров (аналог `FormMain.UpdateTimers()`).
     *
     * Примечание:
     * - вызывается из UI/foreground tick;
     * - для действия "зелье/перемещение/комплект" выполняется только один таймер за проход;
     * - простые due-таймеры удаляются пакетно в этом же проходе (как `goto again` в C#).
     */
    public synchronized void processDueTimers() {
        ensureLoadedForCurrentProfileLocked();
        if (listAppTimers.isEmpty()) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        boolean listChanged = false;

        for (int index = 0; index < listAppTimers.size(); index++) {
            AppTimer timer = listAppTimers.get(index);
            if (nowMs <= timer.triggerTime) {
                continue;
            }
            if (AppVars.FastNeed) {
                return;
            }

            if (!TextUtils.isEmpty(timer.potion)) {
                executePotionTimerLocked(index, timer);
                return;
            }

            if (!TextUtils.isEmpty(timer.destination)) {
                executeDestinationTimerLocked(index, timer);
                return;
            }

            if (!TextUtils.isEmpty(timer.complect)) {
                executeComplectTimerLocked(index, timer);
                return;
            }

            listAppTimers.remove(index);
            index--;
            listChanged = true;
            playTimerSignalIfEnabledLocked();
        }

        if (listChanged) {
            persistLocked();
        }
    }

    private void executePotionTimerLocked(int index, AppTimer timer) {
        String targetNick = resolveCurrentNick();
        int drinkCount = timer.drinkCount > 0 ? timer.drinkCount : 1;

        listAppTimers.remove(index);
        if (timer.isRecur && timer.everyMinutes > 0) {
            AppTimer nextTimer = timer.copy();
            nextTimer.id = 0;
            nextTimer.triggerTime = timer.triggerTime + (timer.everyMinutes * 60_000L);
            addAppTimerInternalLocked(nextTimer);
        }
        persistLocked();

        playTimerSignalIfEnabledLocked();
        FastActionManager.fastStart(timer.potion, targetNick, drinkCount);
        Log.d(TAG, "processDueTimers: potion timer fired, id=" + timer.id + ", potion=" + timer.potion);
    }

    private void executeDestinationTimerLocked(int index, AppTimer timer) {
        listAppTimers.remove(index);
        persistLocked();

        playTimerSignalIfEnabledLocked();
        AutoFunctionsManager.getInstance(appContext).startAutoMoving(timer.destination);
        Log.d(TAG, "processDueTimers: destination timer fired, id=" + timer.id + ", destination=" + timer.destination);
    }

    private void executeComplectTimerLocked(int index, AppTimer timer) {
        listAppTimers.remove(index);
        persistLocked();

        AppVars.WearComplect = timer.complect;
        playTimerSignalIfEnabledLocked();
        reloadMainPhpInf();
        Log.d(TAG, "processDueTimers: complect timer fired, id=" + timer.id + ", complect=" + timer.complect);
    }

    private AppTimer addAppTimerInternalLocked(AppTimer source) {
        if (source == null) {
            return null;
        }

        AppTimer appTimer = source.copy();
        appTimer.description = safe(appTimer.description);
        appTimer.potion = safe(appTimer.potion);
        appTimer.destination = safe(appTimer.destination);
        appTimer.complect = safe(appTimer.complect);
        if (appTimer.triggerTime < System.currentTimeMillis()) {
            return null;
        }

        int maxId = 0;
        for (AppTimer active : listAppTimers) {
            if (active.id > maxId) {
                maxId = active.id;
            }
        }
        appTimer.id = maxId + 1;
        insertSortedLocked(appTimer);
        return appTimer.copy();
    }

    private void insertSortedLocked(AppTimer appTimer) {
        listAppTimers.add(appTimer);
        Collections.sort(listAppTimers, Comparator.comparingLong(value -> value.triggerTime));
    }

    private int findIndexByIdLocked(int timerId) {
        for (int index = 0; index < listAppTimers.size(); index++) {
            if (listAppTimers.get(index).id == timerId) {
                return index;
            }
        }
        return -1;
    }

    private void ensureLoadedForCurrentProfileLocked() {
        String storageKey = getStorageKeyLocked();
        if (storageKey.equals(loadedStorageKey)) {
            return;
        }
        loadedStorageKey = storageKey;
        listAppTimers.clear();

        String json = prefs.getString(storageKey, "[]");
        if (TextUtils.isEmpty(json)) {
            return;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                AppTimer timer = new AppTimer();
                timer.id = item.optInt("id", 0);
                timer.triggerTime = item.optLong("triggerTime", 0L);
                timer.description = item.optString("description", "");
                timer.potion = item.optString("potion", "");
                timer.drinkCount = item.optInt("drinkCount", 0);
                timer.isRecur = item.optBoolean("isRecur", false);
                timer.everyMinutes = item.optInt("everyMinutes", 0);
                timer.destination = item.optString("destination", "");
                timer.complect = item.optString("complect", "");
                timer.isHerb = item.optBoolean("isHerb", false);
                if (timer.triggerTime <= 0L) {
                    continue;
                }
                listAppTimers.add(timer);
            }
            Collections.sort(listAppTimers, Comparator.comparingLong(value -> value.triggerTime));
        } catch (Exception error) {
            Log.w(TAG, "ensureLoadedForCurrentProfileLocked: failed to parse timers json", error);
            listAppTimers.clear();
        }
    }

    private void persistLocked() {
        JSONArray array = new JSONArray();
        for (AppTimer timer : listAppTimers) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", timer.id);
                item.put("triggerTime", timer.triggerTime);
                item.put("description", safe(timer.description));
                item.put("potion", safe(timer.potion));
                item.put("drinkCount", timer.drinkCount);
                item.put("isRecur", timer.isRecur);
                item.put("everyMinutes", timer.everyMinutes);
                item.put("destination", safe(timer.destination));
                item.put("complect", safe(timer.complect));
                item.put("isHerb", timer.isHerb);
                array.put(item);
            } catch (Exception error) {
                Log.w(TAG, "persistLocked: failed to serialize timer id=" + timer.id, error);
            }
        }
        prefs.edit().putString(getStorageKeyLocked(), array.toString()).apply();
    }

    private void playTimerSignalIfEnabledLocked() {
        if (!isTimerSoundEnabled()) {
            return;
        }
        try {
            ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 220);
            toneGenerator.release();
        } catch (Exception error) {
            Log.w(TAG, "playTimerSignalIfEnabledLocked: failed", error);
        }
    }

    private void reloadMainPhpInf() {
        StringBuilder url = new StringBuilder("http://neverlands.ru/main.php?get_id=56&act=10&go=inf");
        if (!TextUtils.isEmpty(AppVars.VCode)) {
            url.append("&vcode=").append(AppVars.VCode);
        }
        url.append("&ab_timer=1&r=").append(System.currentTimeMillis());

        Intent intent = new Intent(AppVars.ACTION_WEBVIEW_LOAD_URL);
        intent.putExtra("url", url.toString());
        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
    }

    private String getStorageKeyLocked() {
        String nick = resolveCurrentNick();
        if (TextUtils.isEmpty(nick)) {
            return KEY_TIMERS_JSON_PREFIX + "default";
        }
        return KEY_TIMERS_JSON_PREFIX + nick.toLowerCase(Locale.ROOT);
    }

    private String getTimerSoundKeyLocked() {
        String nick = resolveCurrentNick();
        if (TextUtils.isEmpty(nick)) {
            return KEY_TIMER_SOUND_PREFIX + "default";
        }
        return KEY_TIMER_SOUND_PREFIX + nick.toLowerCase(Locale.ROOT);
    }

    private static String resolveCurrentNick() {
        if (AppVars.Profile != null && !TextUtils.isEmpty(AppVars.Profile.UserNick)) {
            return AppVars.Profile.UserNick.trim();
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

