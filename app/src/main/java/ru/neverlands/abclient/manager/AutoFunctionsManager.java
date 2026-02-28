package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.model.QuickActionType;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Менеджер автоматических функций (авто-бой, авто-рыбалка и т.д.).
 * Управляет включением/выключением авто-функций и их состоянием.
 */
public class AutoFunctionsManager {
    private static final String TAG = "AutoFunctionsManager";
    private static final String PREFS_NAME = "auto_functions_prefs";
    private static final String KEY_PREFIX = "auto_function_";
    
    private static AutoFunctionsManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    
    // SharedPreferences фиксируют состояние автозадач между перезапусками.
    private AutoFunctionsManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized AutoFunctionsManager getInstance(Context context) {
        if (instance == null) {
            instance = new AutoFunctionsManager(context);
        }
        return instance;
    }
    
    // === AUTO_FIGHT (Авто-Бой) ===
    
    // Текущее состояние авто-боя.
    public boolean isAutoFightEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_fight", false);
    }
    
    // Переключение авто-боя без знания текущего состояния снаружи.
    public void toggleAutoFight() {
        boolean newState = !isAutoFightEnabled();
        setAutoFightEnabled(newState);
    }
    
    /**
     * Включение/выключение авто-боя.
     * - Синхронизирует AppVars.Autoboi и Profile.LezDoAutoboi.
     * - При включении форсирует переход в fight.frame, как в ПК-версии.
     */
    public void setAutoFightEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_fight", enabled).apply();
        // Глобальный флаг боевого режима для ядра клиента.
        AppVars.Autoboi = enabled ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff;
        if (AppVars.Profile != null) {
            // Храним настройку в профиле, чтобы не терять состояние между входами.
            AppVars.Profile.LezDoAutoboi = enabled;
            AppVars.Profile.save(context);
        }
        Log.d(TAG, "setAutoFightEnabled: " + enabled);

        // При включении делаем форсированную загрузку боевого кадра (fight.frame).
        if (enabled) {
            // При включении автобоя дергаем авто-ход и форсируем загрузку боевого кадра, как в ПК версии.
            if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
                AppVars.mainActivity.get().runOnUiThread(() -> {
                    try {
                        // сброс кеша и таймера, чтобы не зависать на старом кадре ручного боя
                        // Сбрасываем кэш и таймеры, чтобы не "зависнуть" на старом кадре.
                        AppVars.ContentMainPhp = null;
                        AppVars.LastBoiTimer = new java.util.Date();
                        // Запрашиваем авто-удар (логика автохода в MainActivity).
                        AppVars.mainActivity.get().requestAutoTurn();
                        // Прямая перезагрузка боевого фрейма, с vcode если он есть.
                        String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf";
                        if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
                            reloadUrl += "&vcode=" + AppVars.VCode;
                        }
                        reloadUrl += "&ts=" + System.currentTimeMillis();
                        Log.d(TAG, "setAutoFightEnabled: reload fight frame " + reloadUrl);
                        AppVars.mainActivity.get().getMainWebView().loadUrl(reloadUrl);
                        // страховочный повтор через ~1.2с, если первый кадр ещё был ручным
                        // Страховочный повтор через ~1.2с: нужен, если первый кадр был "ручным".
                        final String secondReload = reloadUrl;
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> {
                                    Log.d(TAG, "setAutoFightEnabled: second reload fight frame " + secondReload);
                                    AppVars.mainActivity.get().getMainWebView().loadUrl(secondReload);
                                }, 1200);
                    } catch (Exception e) {
                        Log.e(TAG, "setAutoFightEnabled: failed to trigger auto turn", e);
                    }
                });
            }
        }
    }
    
    // === AUTO_FISH (Авто-Рыбалка) ===
    
    // Авто-рыбалка: состояние.
    public boolean isAutoFishEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_fish", false);
    }
    
    // Переключение авто-рыбалки.
    public void toggleAutoFish() {
        boolean newState = !isAutoFishEnabled();
        setAutoFishEnabled(newState);
    }
    
    // Включение авто-рыбалки включает авто-бой и отключает несовместимые режимы.
    public void setAutoFishEnabled(boolean enabled) {
        if (enabled) {
            // При включении: если Авто-Бой выключен - включаем его
            if (!isAutoFightEnabled()) {
                setAutoFightEnabled(true);
                Log.d(TAG, "setAutoFishEnabled: Авто-Бой также включен");
            }
            // Эксклюзивные функции: выключаем Авто-Охоту, Авто-Травник, Авто-Приманку
            if (isAutoSkinEnabled()) {
                setAutoSkinEnabled(false);
                Log.d(TAG, "setAutoFishEnabled: Авто-Охота выключена");
            }
            if (isAutoCutEnabled()) {
                setAutoCutEnabled(false);
                Log.d(TAG, "setAutoFishEnabled: Авто-Травник выключен");
            }
            if (isAutoBaitEnabled()) {
                setAutoBaitEnabled(false);
                Log.d(TAG, "setAutoFishEnabled: Авто-Приманка выключена");
            }
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_fish", enabled).apply();
        Log.d(TAG, "setAutoFishEnabled: " + enabled);
    }
    
    // === AUTO_BAIT (Авто-Приманка) ===
    
    // Авто-приманка: состояние.
    public boolean isAutoBaitEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_bait", false);
    }
    
    // Переключение авто-приманки.
    public void toggleAutoBait() {
        boolean newState = !isAutoBaitEnabled();
        setAutoBaitEnabled(newState);
    }
    
    // Включение авто-приманки включает авто-бой и отключает несовместимые режимы.
    public void setAutoBaitEnabled(boolean enabled) {
        if (enabled) {
            // При включении: если Авто-Бой выключен - включаем его
            if (!isAutoFightEnabled()) {
                setAutoFightEnabled(true);
                Log.d(TAG, "setAutoBaitEnabled: Авто-Бой также включен");
            }
            // Эксклюзивные функции: выключаем Авто-Рыбалку, Авто-Охоту, Авто-Травник
            if (isAutoFishEnabled()) {
                setAutoFishEnabled(false);
                Log.d(TAG, "setAutoBaitEnabled: Авто-Рыбалка выключена");
            }
            if (isAutoSkinEnabled()) {
                setAutoSkinEnabled(false);
                Log.d(TAG, "setAutoBaitEnabled: Авто-Охота выключена");
            }
            if (isAutoCutEnabled()) {
                setAutoCutEnabled(false);
                Log.d(TAG, "setAutoBaitEnabled: Авто-Травник выключен");
            }
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_bait", enabled).apply();
        Log.d(TAG, "setAutoBaitEnabled: " + enabled);
    }
    
    // === AUTO_SKIN (Авто-Охота) ===
    
    // Авто-охота: состояние.
    public boolean isAutoSkinEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_skin", false);
    }
    
    // Переключение авто-охоты.
    public void toggleAutoSkin() {
        boolean newState = !isAutoSkinEnabled();
        setAutoSkinEnabled(newState);
    }
    
    // Включение авто-охоты включает авто-бой и отключает несовместимые режимы.
    public void setAutoSkinEnabled(boolean enabled) {
        if (enabled) {
            // При включении Авто-Охоты: если Авто-Бой выключен - включаем оба
            if (!isAutoFightEnabled()) {
                setAutoFightEnabled(true);
                Log.d(TAG, "setAutoSkinEnabled: Авто-Бой также включен");
            }
            
            // Эксклюзивные функции: выключаем Авто-Рыбалку, Авто-Травник, Авто-Приманку
            if (isAutoFishEnabled()) {
                setAutoFishEnabled(false);
                Log.d(TAG, "setAutoSkinEnabled: Авто-Рыбалка выключена");
            }
            if (isAutoCutEnabled()) {
                setAutoCutEnabled(false);
                Log.d(TAG, "setAutoSkinEnabled: Авто-Травник выключен");
            }
            if (isAutoBaitEnabled()) {
                setAutoBaitEnabled(false);
                Log.d(TAG, "setAutoSkinEnabled: Авто-Приманка выключена");
            }
        }
        
        prefs.edit().putBoolean(KEY_PREFIX + "auto_skin", enabled).apply();
        Log.d(TAG, "setAutoSkinEnabled: " + enabled);
    }
    
    // === AUTO_ATTACK (Авто-Нападение) ===
    
    // Авто-нападение: состояние.
    public boolean isAutoAttackEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_attack", false);
    }
    
    // Переключение авто-нападения.
    public void toggleAutoAttack() {
        boolean newState = !isAutoAttackEnabled();
        setAutoAttackEnabled(newState);
    }
    
    // Включение/выключение авто-нападения.
    public void setAutoAttackEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_attack", enabled).apply();
        Log.d(TAG, "setAutoAttackEnabled: " + enabled);
    }
    
    // === AUTO_INVISIBLE (Авто-Невид) ===
    
    // Авто-невид: состояние.
    public boolean isAutoInvisibleEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_invisible", false);
    }
    
    // Переключение авто-невида.
    public void toggleAutoInvisible() {
        boolean newState = !isAutoInvisibleEnabled();
        setAutoInvisibleEnabled(newState);
    }
    
    // Включение/выключение авто-невида.
    public void setAutoInvisibleEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_invisible", enabled).apply();
        Log.d(TAG, "setAutoInvisibleEnabled: " + enabled);
    }
    
    // === LOCATION_TRACKING (Слежение за локацией) ===
    
    // Слежение за локацией: состояние.
    public boolean isLocationTrackingEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "location_tracking", false);
    }
    
    // Переключение слежения за локацией.
    public void toggleLocationTracking() {
        boolean newState = !isLocationTrackingEnabled();
        setLocationTrackingEnabled(newState);
    }
    
    // Включение/выключение слежения за локацией.
    public void setLocationTrackingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "location_tracking", enabled).apply();
        Log.d(TAG, "setLocationTrackingEnabled: " + enabled);
    }
    
    // === AUTO_DETECT (Авто-Обнаружение) ===
    
    // Авто-обнаружение: состояние.
    public boolean isAutoDetectEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_detect", false);
    }
    
    // Переключение авто-обнаружения.
    public void toggleAutoDetect() {
        boolean newState = !isAutoDetectEnabled();
        setAutoDetectEnabled(newState);
    }
    
    // Включение/выключение авто-обнаружения.
    public void setAutoDetectEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_detect", enabled).apply();
        Log.d(TAG, "setAutoDetectEnabled: " + enabled);
    }
    
    // === AUTO_SUMMON (Авто-Тотем) ===
    
    // Авто-тотем: состояние.
    public boolean isAutoSummonEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_summon", false);
    }
    
    // Переключение авто-тотема.
    public void toggleAutoSummon() {
        boolean newState = !isAutoSummonEnabled();
        setAutoSummonEnabled(newState);
    }
    
    // Включение/выключение авто-тотема.
    public void setAutoSummonEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_summon", enabled).apply();
        Log.d(TAG, "setAutoSummonEnabled: " + enabled);
    }
    
    // === AUTO_CURE (Авто-Лечение - DoAutoCure) ===
    
    // Авто-лечение: состояние.
    public boolean isAutoCureEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_cure", false);
    }
    
    // Переключение авто-лечения.
    public void toggleAutoCure() {
        boolean newState = !isAutoCureEnabled();
        setAutoCureEnabled(newState);
    }
    
    // Включение/выключение авто-лечения.
    public void setAutoCureEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_cure", enabled).apply();
        Log.d(TAG, "setAutoCureEnabled: " + enabled);
    }
    
    // === AUTO_DRINK (Авто-Питье) ===
    
    // Авто-питье: состояние.
    public boolean isAutoDrinkEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_drink", false);
    }
    
    // Переключение авто-питья.
    public void toggleAutoDrink() {
        boolean newState = !isAutoDrinkEnabled();
        setAutoDrinkEnabled(newState);
    }
    
    // Включение/выключение авто-питья.
    public void setAutoDrinkEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_drink", enabled).apply();
        Log.d(TAG, "setAutoDrinkEnabled: " + enabled);
    }
    
    // === AUTO_MOVING (Авто-Движение) ===
    
    // Авто-движение: состояние.
    public boolean isAutoMovingEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_moving", false);
    }
    
    // Переключение авто-движения.
    public void toggleAutoMoving() {
        boolean newState = !isAutoMovingEnabled();
        setAutoMovingEnabled(newState);
    }
    
    // Включение/выключение авто-движения.
    public void setAutoMovingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_moving", enabled).apply();
        Log.d(TAG, "setAutoMovingEnabled: " + enabled);
    }
    
    // === AUTO_CUT (Авто-Травник) ===
    
    // Авто-травник: состояние.
    public boolean isAutoCutEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_cut", false);
    }
    
    // Переключение авто-травника.
    public void toggleAutoCut() {
        boolean newState = !isAutoCutEnabled();
        setAutoCutEnabled(newState);
    }
    
    // Включение авто-травника включает авто-бой и отключает несовместимые режимы.
    public void setAutoCutEnabled(boolean enabled) {
        if (enabled) {
            // При включении: если Авто-Бой выключен - включаем его
            if (!isAutoFightEnabled()) {
                setAutoFightEnabled(true);
                Log.d(TAG, "setAutoCutEnabled: Авто-Бой также включен");
            }
            // Эксклюзивные функции: выключаем Авто-Рыбалку, Авто-Охоту, Авто-Приманку
            if (isAutoFishEnabled()) {
                setAutoFishEnabled(false);
                Log.d(TAG, "setAutoCutEnabled: Авто-Рыбалка выключена");
            }
            if (isAutoSkinEnabled()) {
                setAutoSkinEnabled(false);
                Log.d(TAG, "setAutoCutEnabled: Авто-Охота выключена");
            }
            if (isAutoBaitEnabled()) {
                setAutoBaitEnabled(false);
                Log.d(TAG, "setAutoCutEnabled: Авто-Приманка выключена");
            }
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_cut", enabled).apply();
        Log.d(TAG, "setAutoCutEnabled: " + enabled);
    }
    
    // === AUTO_REFRESH (Авто-Обновление) ===
    
    // Авто-обновление: состояние.
    public boolean isAutoRefreshEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_refresh", false);
    }
    
    // Переключение авто-обновления.
    public void toggleAutoRefresh() {
        boolean newState = !isAutoRefreshEnabled();
        setAutoRefreshEnabled(newState);
    }
    
    // Включение/выключение авто-обновления.
    public void setAutoRefreshEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_refresh", enabled).apply();
        Log.d(TAG, "setAutoRefreshEnabled: " + enabled);
    }
    
    // === Универсальные методы ===
    
    /**
     * Получить состояние функции по типу.
     */
    // Универсальный опрос состояния по типу кнопки (используется панелью быстрых кнопок).
    public boolean isFunctionEnabled(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT: return isAutoFightEnabled();
            case AUTO_FISH: return isAutoFishEnabled();
            case AUTO_BAIT: return isAutoBaitEnabled();
            case AUTO_SKIN: return isAutoSkinEnabled();
            case AUTO_ATTACK: return isAutoAttackEnabled();
            case AUTO_INVISIBLE: return isAutoInvisibleEnabled();
            case LOCATION_TRACKING: return isLocationTrackingEnabled();
            case AUTO_DETECT: return isAutoDetectEnabled();
            case AUTO_SUMMON: return isAutoSummonEnabled();
            case AUTO_CURE: return isAutoCureEnabled();
            case AUTO_DRINK: return isAutoDrinkEnabled();
            case AUTO_MOVING: return isAutoMovingEnabled();
            case AUTO_CUT: return isAutoCutEnabled();
            case AUTO_REFRESH: return isAutoRefreshEnabled();
            default: return false;
        }
    }
    
    /**
     * Переключить состояние функции по типу.
     */
    // Универсальное переключение состояния по типу кнопки.
    public void toggleFunction(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT: toggleAutoFight(); break;
            case AUTO_FISH: toggleAutoFish(); break;
            case AUTO_BAIT: toggleAutoBait(); break;
            case AUTO_SKIN: toggleAutoSkin(); break;
            case AUTO_ATTACK: toggleAutoAttack(); break;
            case AUTO_INVISIBLE: toggleAutoInvisible(); break;
            case LOCATION_TRACKING: toggleLocationTracking(); break;
            case AUTO_DETECT: toggleAutoDetect(); break;
            case AUTO_SUMMON: toggleAutoSummon(); break;
            case AUTO_CURE: toggleAutoCure(); break;
            case AUTO_DRINK: toggleAutoDrink(); break;
            case AUTO_MOVING: toggleAutoMoving(); break;
            case AUTO_CUT: toggleAutoCut(); break;
            case AUTO_REFRESH: toggleAutoRefresh(); break;
            default: break;
        }
    }
    
    /**
     * Отключить все авто-функции.
     */
    // Полный сброс всех авто-функций (например, при логауте/критической ошибке).
    public void disableAll() {
        setAutoFightEnabled(false);
        setAutoFishEnabled(false);
        setAutoBaitEnabled(false);
        setAutoSkinEnabled(false);
        setAutoAttackEnabled(false);
        setAutoInvisibleEnabled(false);
        setLocationTrackingEnabled(false);
        setAutoDetectEnabled(false);
        setAutoSummonEnabled(false);
        setAutoCureEnabled(false);
        setAutoDrinkEnabled(false);
        setAutoMovingEnabled(false);
        setAutoCutEnabled(false);
        setAutoRefreshEnabled(false);
    }
}
