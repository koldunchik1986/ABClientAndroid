package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.model.QuickActionType;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Менеджер автоматических функций (автобой, авторыбалка и т.д.).
 * Управляет включением/выключением автофункций и их состоянием.
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
    
    // === AUTO_FIGHT (Автобой) ===
    
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
    
    // === AUTO_RECALL (Авторыбалка) ===
    
    public boolean isAutoRecallEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_recall", false);
    }
    
    public void toggleAutoRecall() {
        boolean newState = !isAutoRecallEnabled();
        setAutoRecallEnabled(newState);
    }
    
    public void setAutoRecallEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_recall", enabled).apply();
        Log.d(TAG, "setAutoRecallEnabled: " + enabled);
    }
    
    // === AUTO_HUNT (Автоохота) ===
    
    public boolean isAutoHuntEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_hunt", false);
    }
    
    public void toggleAutoHunt() {
        boolean newState = !isAutoHuntEnabled();
        setAutoHuntEnabled(newState);
    }
    
    public void setAutoHuntEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_hunt", enabled).apply();
        Log.d(TAG, "setAutoHuntEnabled: " + enabled);
    }
    
    // === AUTO_ATTACK (Автонападение) ===
    
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
    
    // === AUTO_INVISIBLE (АвтоНевид) ===
    
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
    
    // === AUTO_DETECT (АвтоОбнаружение) ===
    
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
    
    // === AUTO_SUMMON (АвтоПризыв) ===
    
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
    
    // === AUTO_HEAL (АвтоЛечение) ===
    
    public boolean isAutoHealEnabled() {
        return prefs.getBoolean(KEY_PREFIX + "auto_heal", false);
    }
    
    public void toggleAutoHeal() {
        boolean newState = !isAutoHealEnabled();
        setAutoHealEnabled(newState);
    }
    
    public void setAutoHealEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREFIX + "auto_heal", enabled).apply();
        Log.d(TAG, "setAutoHealEnabled: " + enabled);
    }
    
    // === Универсальные методы ===
    
    /**
     * Получить состояние функции по типу.
     */
    public boolean isFunctionEnabled(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT: return isAutoFightEnabled();
            case AUTO_RECALL: return isAutoRecallEnabled();
            case AUTO_HUNT: return isAutoHuntEnabled();
            case AUTO_ATTACK: return isAutoAttackEnabled();
            case AUTO_INVISIBLE: return isAutoInvisibleEnabled();
            case LOCATION_TRACKING: return isLocationTrackingEnabled();
            case AUTO_DETECT: return isAutoDetectEnabled();
            case AUTO_SUMMON: return isAutoSummonEnabled();
            case AUTO_HEAL: return isAutoHealEnabled();
            default: return false;
        }
    }
    
    /**
     * Переключить состояние функции по типу.
     */
    public void toggleFunction(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT: toggleAutoFight(); break;
            case AUTO_RECALL: toggleAutoRecall(); break;
            case AUTO_HUNT: toggleAutoHunt(); break;
            case AUTO_ATTACK: toggleAutoAttack(); break;
            case AUTO_INVISIBLE: toggleAutoInvisible(); break;
            case LOCATION_TRACKING: toggleLocationTracking(); break;
            case AUTO_DETECT: toggleAutoDetect(); break;
            case AUTO_SUMMON: toggleAutoSummon(); break;
            case AUTO_HEAL: toggleAutoHeal(); break;
            default: break;
        }
    }
    
    /**
     * Отключить все автофункции.
     */
    public void disableAll() {
        setAutoFightEnabled(false);
        setAutoRecallEnabled(false);
        setAutoHuntEnabled(false);
        setAutoAttackEnabled(false);
        setAutoInvisibleEnabled(false);
        setLocationTrackingEnabled(false);
        setAutoDetectEnabled(false);
        setAutoSummonEnabled(false);
        setAutoHealEnabled(false);
    }
}
