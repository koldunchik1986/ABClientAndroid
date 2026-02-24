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
    
    public boolean isAutoFightEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_fight", false);
    }
    
    public void toggleAutoFight() {
        boolean newState = !isAutoFightEnabled();
        setAutoFightEnabled(newState);
    }
    
    public void setAutoFightEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_fight", enabled).apply();
        AppVars.Autoboi = enabled ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff;
        Log.d(TAG, "setAutoFightEnabled: " + enabled);
    }
    
    // === AUTO_FISH (Авто-Рыбалка) ===
    
    public boolean isAutoFishEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_fish", false);
    }
    
    public void toggleAutoFish() {
        boolean newState = !isAutoFishEnabled();
        setAutoFishEnabled(newState);
    }
    
    public void setAutoFishEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_fish", enabled).apply();
        Log.d(TAG, "setAutoFishEnabled: " + enabled);
    }
    
    // === AUTO_BAIT (Авто-Приманка) ===
    
    public boolean isAutoBaitEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_bait", false);
    }
    
    public void toggleAutoBait() {
        boolean newState = !isAutoBaitEnabled();
        setAutoBaitEnabled(newState);
    }
    
    public void setAutoBaitEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_bait", enabled).apply();
        Log.d(TAG, "setAutoBaitEnabled: " + enabled);
    }
    
    // === LEZ_FIGHT (Авто-Охота) ===
    
    public boolean isLezFightEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "lez_fight", false);
    }
    
    public void toggleLezFight() {
        boolean newState = !isLezFightEnabled();
        setLezFightEnabled(newState);
    }
    
    public void setLezFightEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "lez_fight", enabled).apply();
        Log.d(TAG, "setLezFightEnabled: " + enabled);
    }
    
    // === AUTO_ATTACK (Авто-Нападение) ===
    
    public boolean isAutoAttackEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_attack", false);
    }
    
    public void toggleAutoAttack() {
        boolean newState = !isAutoAttackEnabled();
        setAutoAttackEnabled(newState);
    }
    
    public void setAutoAttackEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_attack", enabled).apply();
        Log.d(TAG, "setAutoAttackEnabled: " + enabled);
    }
    
    // === AUTO_INVISIBLE (Авто-Невид) ===
    
    public boolean isAutoInvisibleEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_invisible", false);
    }
    
    public void toggleAutoInvisible() {
        boolean newState = !isAutoInvisibleEnabled();
        setAutoInvisibleEnabled(newState);
    }
    
    public void setAutoInvisibleEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_invisible", enabled).apply();
        Log.d(TAG, "setAutoInvisibleEnabled: " + enabled);
    }
    
    // === LOCATION_TRACKING (Слежение за локацией) ===
    
    public boolean isLocationTrackingEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "location_tracking", false);
    }
    
    public void toggleLocationTracking() {
        boolean newState = !isLocationTrackingEnabled();
        setLocationTrackingEnabled(newState);
    }
    
    public void setLocationTrackingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "location_tracking", enabled).apply();
        Log.d(TAG, "setLocationTrackingEnabled: " + enabled);
    }
    
    // === AUTO_DETECT (Авто-Обнаружение) ===
    
    public boolean isAutoDetectEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_detect", false);
    }
    
    public void toggleAutoDetect() {
        boolean newState = !isAutoDetectEnabled();
        setAutoDetectEnabled(newState);
    }
    
    public void setAutoDetectEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_detect", enabled).apply();
        Log.d(TAG, "setAutoDetectEnabled: " + enabled);
    }
    
    // === AUTO_SUMMON (Авто-Тотем) ===
    
    public boolean isAutoSummonEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_summon", false);
    }
    
    public void toggleAutoSummon() {
        boolean newState = !isAutoSummonEnabled();
        setAutoSummonEnabled(newState);
    }
    
    public void setAutoSummonEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_summon", enabled).apply();
        Log.d(TAG, "setAutoSummonEnabled: " + enabled);
    }
    
    // === AUTO_CURE (Авто-Лечение - DoAutoCure) ===
    
    public boolean isAutoCureEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_cure", false);
    }
    
    public void toggleAutoCure() {
        boolean newState = !isAutoCureEnabled();
        setAutoCureEnabled(newState);
    }
    
    public void setAutoCureEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_cure", enabled).apply();
        Log.d(TAG, "setAutoCureEnabled: " + enabled);
    }
    
    // === AUTO_DRINK (Авто-Питье) ===
    
    public boolean isAutoDrinkEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_drink", false);
    }
    
    public void toggleAutoDrink() {
        boolean newState = !isAutoDrinkEnabled();
        setAutoDrinkEnabled(newState);
    }
    
    public void setAutoDrinkEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_drink", enabled).apply();
        Log.d(TAG, "setAutoDrinkEnabled: " + enabled);
    }
    
    // === AUTO_MOVING (Авто-Движение) ===
    
    public boolean isAutoMovingEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_moving", false);
    }
    
    public void toggleAutoMoving() {
        boolean newState = !isAutoMovingEnabled();
        setAutoMovingEnabled(newState);
    }
    
    public void setAutoMovingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_moving", enabled).apply();
        Log.d(TAG, "setAutoMovingEnabled: " + enabled);
    }
    
    // === AUTO_CUT (Авто-Травник) ===
    
    public boolean isAutoCutEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_cut", false);
    }
    
    public void toggleAutoCut() {
        boolean newState = !isAutoCutEnabled();
        setAutoCutEnabled(newState);
    }
    
    public void setAutoCutEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_cut", enabled).apply();
        Log.d(TAG, "setAutoCutEnabled: " + enabled);
    }
    
    // === AUTO_REFRESH (Авто-Обновление) ===
    
    public boolean isAutoRefreshEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_refresh", false);
    }
    
    public void toggleAutoRefresh() {
        boolean newState = !isAutoRefreshEnabled();
        setAutoRefreshEnabled(newState);
    }
    
    public void setAutoRefreshEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_refresh", enabled).apply();
        Log.d(TAG, "setAutoRefreshEnabled: " + enabled);
    }
    
    // === Универсальные методы ===
    
    /**
     * Получить состояние функции по типу.
     */
    public boolean isFunctionEnabled(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT: return isAutoFightEnabled();
            case AUTO_FISH: return isAutoFishEnabled();
            case AUTO_BAIT: return isAutoBaitEnabled();
            case LEZ_FIGHT: return isLezFightEnabled();
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
    public void toggleFunction(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT: toggleAutoFight(); break;
            case AUTO_FISH: toggleAutoFish(); break;
            case AUTO_BAIT: toggleAutoBait(); break;
            case LEZ_FIGHT: toggleLezFight(); break;
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
    public void disableAll() {
        setAutoFightEnabled(false);
        setAutoFishEnabled(false);
        setAutoBaitEnabled(false);
        setLezFightEnabled(false);
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
