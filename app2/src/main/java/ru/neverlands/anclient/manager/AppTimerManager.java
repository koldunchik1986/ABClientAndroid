package ru.neverlands.anclient.manager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.text.TextUtils;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.license.LicenseRuntime;
import ru.neverlands.anclient.model.AppTimer;
import ru.neverlands.anclient.model.QuickActionType;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.SessionManager;

/**
 * Менеджер пользовательских таймеров (порт `ANClient/AppTimerManager.cs` + часть `FormMainTimers.cs`).
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
    private final AutoFunctionsManager autoFunctionsManager;
    private final List<AppTimer> listAppTimers = new ArrayList<>();
    private String loadedStorageKey = "";

    private AppTimerManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        autoFunctionsManager = AutoFunctionsManager.getInstance(appContext);
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
        if (!LicenseRuntime.getInstance().isActionAllowed(QuickActionType.TIMERS)) {
            // Выполнение таймера может косвенно запускать protected actions (drink, move,
            // переключение auto-functions). Сначала gate-им dispatcher, потом отдельные
            // labels auto-functions проверяются через `isTimerAutoFunctionAllowed(...)`.
            return;
        }
        ensureLoadedForCurrentProfileLocked();
        if (listAppTimers.isEmpty()) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        boolean listChanged = false;

        for (int index = 0; index < listAppTimers.size(); index++) {
            AppTimer timer = listAppTimers.get(index);
            
            // === 5-SECOND BUFFER BEFORE TIMER FIRES ===
            // За 5 секунд до срабатывания таймера И если NeverTimer еще активен:
            // - сохраняем состояние авто-функций
            // - приостанавливаем все авто-функции кроме авто-боя
            // Это позволяет пользователю спокойно открыть инвентарь и пить зелье.
            long timeUntilFireMs = timer.triggerTime - nowMs;
            if (timeUntilFireMs > 0 && timeUntilFireMs <= 5000 && nowMs < AppVars.NeverTimer) {
                if (!AppVars.TimerPauseNonCombatAutoFunctions) {
                    // Сохраняем текущее состояние всех авто-функций
                    AutoFunctionsManager mgr = AutoFunctionsManager.getInstance(this.appContext);
                    AppVars.TimerPauseAutoFishState = mgr.isAutoFishEnabled();
                    AppVars.TimerPauseAutoSkinState = mgr.isAutoSkinEnabled();
                    AppVars.TimerPauseAutoCutState = mgr.isAutoCutEnabled();
                    AppVars.TimerPauseAutoBaitState = mgr.isAutoBaitEnabled();
                    AppVars.TimerPauseAutoCompassState = mgr.isAutoCompassEnabled();
                    AppVars.TimerPauseAutoAttackState = mgr.isAutoAttackEnabled();
                    AppVars.TimerPauseAutoInvisibleState = mgr.isAutoInvisibleEnabled();
                    
                    // Выключаем все авто-функции кроме авто-боя
                    if (AppVars.TimerPauseAutoFishState) {
                        mgr.setAutoFishEnabled(false);
                        AppLog.d(TAG, "[TIMER_PAUSE] Auto-Fishing paused for inventory access");
                    }
                    if (AppVars.TimerPauseAutoSkinState) {
                        mgr.setAutoSkinEnabled(false);
                        AppLog.d(TAG, "[TIMER_PAUSE] Auto-Hunting paused");
                    }
                    if (AppVars.TimerPauseAutoCutState) {
                        mgr.setAutoCutEnabled(false);
                        AppLog.d(TAG, "[TIMER_PAUSE] Auto-Herb paused");
                    }
                    if (AppVars.TimerPauseAutoBaitState) {
                        mgr.setAutoBaitEnabled(false);
                        AppLog.d(TAG, "[TIMER_PAUSE] Auto-Bait paused");
                    }
                    if (AppVars.TimerPauseAutoCompassState) {
                        mgr.setAutoCompassEnabled(false);
                        AppLog.d(TAG, "[TIMER_PAUSE] Auto-Compass paused");
                    }
                    if (AppVars.TimerPauseAutoAttackState) {
                        mgr.setAutoAttackEnabled(false);
                        AppLog.d(TAG, "[TIMER_PAUSE] Auto-Attack paused");
                    }
                    if (AppVars.TimerPauseAutoInvisibleState) {
                        mgr.setAutoInvisibleEnabled(false);
                        AppLog.d(TAG, "[TIMER_PAUSE] Auto-Invisible paused");
                    }
                    
                    AppVars.TimerPauseNonCombatAutoFunctions = true;
                    String msg = "[TIMER_PAUSE] Non-combat autos paused, timeUntilFire=" + timeUntilFireMs 
                            + "ms, timerId=" + timer.id;
                    AppLog.d("app_timer", TAG, msg);
                }
                continue;  // Не срабатываем еще, даем время на инвентарь
            }
            
            if (nowMs <= timer.triggerTime) {
                continue;
            }
            
            // Проверяем NeverTimer (серверный cooldown: рыбалка, перемещение, и пр.)
            // Если NeverTimer ещё активен, пропускаем таймер на этой итерации
            if (nowMs < AppVars.NeverTimer) {
                long deltaMs = AppVars.NeverTimer - nowMs;
                String msg = "[TIMER_DEFER_NEVERTIMER] Дожидаемся NeverTimer, deltaMs=" + deltaMs 
                        + ", timerId=" + timer.id;
                AppLog.w(TAG, msg);
                ru.neverlands.anclient.utils.FileLogger.trace("app_timer", msg);
                // Не удаляем таймер, просто пропускаем это срабатывание
                // Таймер срабатит на следующей итерации processDueTimers
                continue;
            }
            
            if (AppVars.FastNeed) {
                return;
            }

            if (!TextUtils.isEmpty(timer.enableAutoFunction)) {
                executeEnableAutoFunctionTimerLocked(index, timer);
                return;
            }

            if (!TextUtils.isEmpty(timer.disableAutoFunction)) {
                executeDisableAutoFunctionTimerLocked(index, timer);
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
        String msg = "[POTION_TIMER_FIRED] id=" + timer.id + ", potion='" + timer.potion + "', target=" + targetNick 
                + ", drinkCount=" + drinkCount + ", isRecur=" + timer.isRecur;
        AppLog.d(TAG, "executePotionTimer: " + msg);
        ru.neverlands.anclient.utils.FileLogger.trace("app_timer", msg);
        FastActionManager.fastStart(timer.potion, targetNick, drinkCount);
    }

    private void executeDestinationTimerLocked(int index, AppTimer timer) {
        listAppTimers.remove(index);
        persistLocked();

        playTimerSignalIfEnabledLocked();
        AutoFunctionsManager.getInstance(appContext).startAutoMoving(timer.destination);
        AppLog.d(TAG, "processDueTimers: destination timer fired, id=" + timer.id + ", destination=" + timer.destination);
    }

    private void executeComplectTimerLocked(int index, AppTimer timer) {
        listAppTimers.remove(index);
        persistLocked();

        AppVars.WearComplect = timer.complect;
        String msg = "COMPLECT_TIMER_FIRED_TRACE: id=" + timer.id + ", complect=\"" + timer.complect + "\"";
        AppLog.d(TAG, TAG, msg);
        playTimerSignalIfEnabledLocked();
        
        // CRITICAL: Нужно перейти в ИНВЕНТАРЬ (go=inv), чтобы SessionManager получил VCode
        // и MainPhp смог распарсить параметры комплекта из compl_view()
        // Это согласно C# логике в MainPhp.cs, которая ждет MainPhpIsInv(html) перед надеванием
        navigateToInventoryForComplectWear();
    }
    
    /**
     * Навигация в инвентарь для надевания комплекта.
     * SessionManager получит свежий VCode с инвентаря, и затем MainPhp сможет парсить
     * параметры комплекта из JavaScript функции compl_view().
     */
    private void navigateToInventoryForComplectWear() {
        StringBuilder url = new StringBuilder("http://neverlands.ru/main.php?get_id=56&act=10&go=inv");
        
        // Получить свежий VCode через SessionManager согласно правилу 5
        String vcode = SessionManager.getInstance().getValidVCodeForAction("complect_timer_inventory");
        if (!TextUtils.isEmpty(vcode)) {
            url.append("&vcode=").append(vcode);
        } else {
            AppLog.w(TAG, TAG, "COMPLECT_WEAR_TRACE: vcode not available, navigate to inventory anyway");
        }
        
        url.append("&ab_timer=1&r=").append(System.currentTimeMillis());
        
        String msg = "COMPLECT_WEAR_TRACE: navigating to inventory for complect wear, url=" + url.toString();
        AppLog.d(TAG, TAG, msg);

        Intent intent = new Intent(AppVars.ACTION_WEBVIEW_LOAD_URL);
        intent.putExtra("url", url.toString());
        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
    }

    /**
     * Срабатывание таймера для включения авто-функции.
     */
    private void executeEnableAutoFunctionTimerLocked(int index, AppTimer timer) {
        listAppTimers.remove(index);
        persistLocked();

        String autoFunc = timer.enableAutoFunction;
        String msg = "AUTO_FUNCTION_TIMER_FIRED: id=" + timer.id + ", action=ENABLE, function=\"" + autoFunc + "\"";
        AppLog.d(TAG, TAG, msg);
        playTimerSignalIfEnabledLocked();

        // Включение авто-функции через AutoFunctionsManager
        if (!TextUtils.isEmpty(autoFunc) && autoFunctionsManager != null) {
            if (!LicenseRuntime.getInstance().isTimerAutoFunctionAllowed(autoFunc)) {
                AppLog.w("ANCLIENT_LICENSE", TAG, "LICENSE_FEATURE_DENIED: timer enable function=" + autoFunc);
                FileLogger.trace(TAG, "LICENSE_FEATURE_DENIED: timer enable function=" + autoFunc);
                return;
            }
            if ("Авто-Бой".equals(autoFunc)) {
                autoFunctionsManager.setAutoFightEnabled(true);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Бой ENABLED");
            } else if ("Авто-Рыбалка".equals(autoFunc)) {
                autoFunctionsManager.setAutoFishEnabled(true);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Рыбалка ENABLED");
            } else if ("Авто-Охота".equals(autoFunc)) {
                autoFunctionsManager.setAutoSkinEnabled(true);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Охота ENABLED");
            } else if ("Авто-Питьё".equals(autoFunc)) {
                autoFunctionsManager.setAutoDrinkEnabled(true);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Питьё ENABLED");
            } else if ("Авто-Клад".equals(autoFunc)) {
                autoFunctionsManager.setAutoTreasureEnabled(true);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Клад ENABLED");
            } else if ("Авто-Босс".equals(autoFunc) || "Авто-Боссы".equals(autoFunc)) {
                autoFunctionsManager.setAutoBossEnabled(true);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Босс ENABLED");
            }
            
            // Обновляем UI QuickButtons - должны изменить визуальный статус (обводка)
            if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
                AppVars.mainActivity.get().invalidateQuickButtonsUI();
            }
        }
    }

    /**
     * Срабатывание таймера для отключения авто-функции.
     */
    private void executeDisableAutoFunctionTimerLocked(int index, AppTimer timer) {
        listAppTimers.remove(index);
        persistLocked();

        String autoFunc = timer.disableAutoFunction;
        String msg = "AUTO_FUNCTION_TIMER_FIRED: id=" + timer.id + ", action=DISABLE, function=\"" + autoFunc + "\"";
        AppLog.d(TAG, TAG, msg);
        playTimerSignalIfEnabledLocked();

        // Отключение авто-функции через AutoFunctionsManager
        if (!TextUtils.isEmpty(autoFunc) && autoFunctionsManager != null) {
            if (!LicenseRuntime.getInstance().isTimerAutoFunctionAllowed(autoFunc)) {
                AppLog.w("ANCLIENT_LICENSE", TAG, "LICENSE_FEATURE_DENIED: timer disable function=" + autoFunc);
                FileLogger.trace(TAG, "LICENSE_FEATURE_DENIED: timer disable function=" + autoFunc);
                return;
            }
            if ("Авто-Бой".equals(autoFunc)) {
                autoFunctionsManager.setAutoFightEnabled(false);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Бой DISABLED");
            } else if ("Авто-Рыбалка".equals(autoFunc)) {
                autoFunctionsManager.setAutoFishEnabled(false);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Рыбалка DISABLED");
            } else if ("Авто-Охота".equals(autoFunc)) {
                autoFunctionsManager.setAutoSkinEnabled(false);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Охота DISABLED");
            } else if ("Авто-Питьё".equals(autoFunc)) {
                autoFunctionsManager.setAutoDrinkEnabled(false);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Питьё DISABLED");
            } else if ("Авто-Клад".equals(autoFunc)) {
                autoFunctionsManager.setAutoTreasureEnabled(false);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Клад DISABLED");
            } else if ("Авто-Босс".equals(autoFunc) || "Авто-Боссы".equals(autoFunc)) {
                autoFunctionsManager.setAutoBossEnabled(false);
                FileLogger.trace(TAG, "AUTO_FUNCTION_TIMER_FIRED: Авто-Босс DISABLED");
            }
            
            // Обновляем UI QuickButtons - должны изменить визуальный статус (обводка)
            if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
                AppVars.mainActivity.get().invalidateQuickButtonsUI();
            }
        }
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
        appTimer.enableAutoFunction = safe(appTimer.enableAutoFunction);
        appTimer.disableAutoFunction = safe(appTimer.disableAutoFunction);
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
        // ✅ API 21 compatible sort (comparingLong requires API 24)
        Collections.sort(listAppTimers, (o1, o2) -> Long.compare(o1.triggerTime, o2.triggerTime));
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
                timer.enableAutoFunction = item.optString("enableAutoFunction", "");
                timer.disableAutoFunction = item.optString("disableAutoFunction", "");
                if (timer.triggerTime <= 0L) {
                    continue;
                }
                listAppTimers.add(timer);
            }
            // ✅ API 21 compatible sort (comparingLong requires API 24)
            Collections.sort(listAppTimers, (o1, o2) -> Long.compare(o1.triggerTime, o2.triggerTime));
        } catch (Exception error) {
            AppLog.w(TAG, "ensureLoadedForCurrentProfileLocked: failed to parse timers json", error);
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
                item.put("enableAutoFunction", safe(timer.enableAutoFunction));
                item.put("disableAutoFunction", safe(timer.disableAutoFunction));
                array.put(item);
            } catch (Exception error) {
                AppLog.w(TAG, "persistLocked: failed to serialize timer id=" + timer.id, error);
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
            AppLog.w(TAG, "playTimerSignalIfEnabledLocked: failed", error);
        }
    }

    private void reloadMainPhpInf() {
        StringBuilder url = new StringBuilder("http://neverlands.ru/main.php?get_id=56&act=10&go=inf");
        
        // ОБЯЗАТЕЛЬНО: Получить VCode через SessionManager согласно правилу 5
        String vcode = SessionManager.getInstance().getValidVCodeForAction("app_timer_reload");
        if (!TextUtils.isEmpty(vcode)) {
            url.append("&vcode=").append(vcode);
        } else {
            AppLog.w(TAG, TAG, "APP_TIMER_TRACE reloadMainPhpInf: vcode not available, reload without vcode");
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

