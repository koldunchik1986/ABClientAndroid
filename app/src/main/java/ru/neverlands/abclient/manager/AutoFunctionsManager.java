package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.model.QuickActionType;
import ru.neverlands.abclient.service.AutoModeForegroundService;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Менеджер автоматических функций (авто-бой, авто-рыбалка и т.д.).
 * Управляет включением/выключением авто-функций и их состоянием.
 */
public class AutoFunctionsManager {
    private static final String TAG = "AutoFunctionsManager";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
    private static final String PREFS_NAME = "auto_functions_prefs";
    private static final String KEY_PREFIX = "auto_function_";
    private static final String KEY_AUTO_SKIN = KEY_PREFIX + "auto_skin";
    private static final String KEY_AUTO_ATTACK_LEGACY = KEY_PREFIX + "auto_attack";
    private static final String KEY_AUTO_ATTACK_TOOL_ID = KEY_PREFIX + "auto_attack_tool_id";
    private static final String KEY_AUTO_ATTACK_LAST_NON_ZERO_TOOL_ID = KEY_PREFIX + "auto_attack_last_non_zero_tool_id";
    private static final String KEY_LOCATION_TRACKING = KEY_PREFIX + "location_tracking";
    private static final String KEY_WALKERS_POLL_INTERVAL_SEC = KEY_PREFIX + "walkers_poll_interval_sec";
    private static final int WALKERS_POLL_INTERVAL_DEFAULT_SEC = 1;
    
    private static AutoFunctionsManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    
    // SharedPreferences фиксируют состояние автозадач между перезапусками.
    private AutoFunctionsManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Поднимаем runtime-состояние выбранного инструмента авто-нападения из постоянного хранилища.
        migrateLegacyAutoAttackFlagIfNeeded();
        AppVars.AutoAttackToolId = getAutoAttackToolId();
        AppVars.DoShowWalkers = isLocationTrackingEnabled();
        syncAutoSkinWithProfileIfPresent();
        syncAutoFightWithProfileIfPresent();
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
        if (AppVars.Profile != null) {
            boolean profileValue = AppVars.Profile.LezDoAutoboi;
            boolean prefValue = prefs.getBoolean(KEY_PREFIX + "auto_fight", profileValue);
            if (prefValue != profileValue) {
                prefs.edit().putBoolean(KEY_PREFIX + "auto_fight", profileValue).apply();
                Log.d(TAG, "isAutoFightEnabled: sync pref from profile LezDoAutoboi=" + profileValue);
            }
            return profileValue;
        }
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
        boolean furyEnabledByProfile = AppVars.Profile != null && AppVars.Profile.hasAnyLezFuryGroup();
        if (AppVars.Profile != null) {
            AppVars.Profile.LezDoFury = furyEnabledByProfile;
        }
        AppVars.DoFury = furyEnabledByProfile;
        if (enabled && furyEnabledByProfile) {
            AppVars.AutoFuryCheckScroll = true;
            AppVars.AutoFuryArmedScroll = false;
            AppVars.AutoFuryHand = "";
            AppVars.AutoFuryHandD = "";
            Log.d(TAG, "setAutoFightEnabled: AutoFury primed (DoFury=true)");
        }

        Log.d(TAG, "setAutoFightEnabled: " + enabled);
        syncBackgroundService("setAutoFightEnabled(" + enabled + ")");

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
                        Log.d(TAG, "setAutoFightEnabled: immediate requestAutoTurn disabled, forcing frame reload only");
                        // Прямая перезагрузка боевого фрейма, с vcode если он есть.
                        String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_reload_probe=1";
                        if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
                            reloadUrl += "&vcode=" + AppVars.VCode;
                        }
                        reloadUrl += "&ts=" + System.currentTimeMillis();
                        Log.d(TAG, "setAutoFightEnabled: reload fight frame " + reloadUrl);
                        AppVars.mainActivity.get().getMainWebView().loadUrl(reloadUrl);
                        // страховочный повтор через ~1.2с, если первый кадр ещё был ручным
                        // Страховочный повтор через ~1.2с: нужен, если первый кадр был "ручным".
                        // Удален second reload: в некоторых сессиях он провоцировал лишние перезагрузки
                        // верхнего фрейма и мешал нормальной навигации после боя.
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

            // C# parity (`FormMain.ButtonAutoFish_Click`): инициализируем runtime-состояние авто-рыбалки.
            AppVars.AutoFishCheckUd = true;
            AppVars.AutoFishWearUd = false;
            AppVars.AutoFishCheckUm = AppVars.Profile != null && AppVars.Profile.FishUm == 0;
            AppVars.AutoFishHand1 = "";
            AppVars.AutoFishHand1D = "";
            AppVars.AutoFishHand2 = "";
            AppVars.AutoFishHand2D = "";
            AppVars.AutoFishMassa = "";
            AppVars.AutoFishNV = 0;
            AppVars.AutoFishDrink = false;
            AppVars.AutoFishWearLoopKey = "";
            AppVars.AutoFishWearLoopCount = 0;
            AppVars.AutoFishWearLoopStamp = 0L;
            // Нормализация старых профилей: пустая 2-я рука трактуется как "Нет",
            // иначе это приводит к циклическому переодеванию (пустая строка матчится везде).
            if (AppVars.Profile != null) {
                if (AppVars.Profile.FishHandOne == null || AppVars.Profile.FishHandOne.trim().isEmpty()) {
                    AppVars.Profile.FishHandOne = "Любая удочка";
                }
                if (AppVars.Profile.FishHandTwo == null || AppVars.Profile.FishHandTwo.trim().isEmpty()) {
                    AppVars.Profile.FishHandTwo = "Нет";
                }
            }
            // Снимаем возможный cooldown fast-действий, чтобы авто-рыбалка стартовала сразу после включения.
            AppVars.NeverTimer = 0L;
        } else {
            // При ручном выключении также очищаем anti-loop state, чтобы при следующем старте
            // авто-рыбалка начинала с чистого runtime-контекста.
            AppVars.AutoFishWearLoopKey = "";
            AppVars.AutoFishWearLoopCount = 0;
            AppVars.AutoFishWearLoopStamp = 0L;
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_fish", enabled).apply();
        if (AppVars.Profile != null) {
            AppVars.Profile.AutoFish = enabled;
            AppVars.Profile.save(context);
        }
        if (enabled && AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().runOnUiThread(() -> {
                try {
                    if (AppVars.mainActivity.get() == null || AppVars.mainActivity.get().getMainWebView() == null) {
                        return;
                    }
                    // Форсируем вход в поток main.php, чтобы MainPhp сразу начал C#-цепочку
                    // проверки персонажа/инвентаря без ручного клика "Ваш персонаж".
                    String url = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&af_bootstrap=1";
                    if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
                        url += "&vcode=" + AppVars.VCode;
                    }
                    url += "&ts=" + System.currentTimeMillis();
                    Log.d(TAG, "setAutoFishEnabled: bootstrap navigation to " + url);
                    AppVars.mainActivity.get().getMainWebView().loadUrl(url);
                } catch (Exception e) {
                    Log.e(TAG, "setAutoFishEnabled: bootstrap navigation failed", e);
                }
            });
        }
        Log.d(TAG, "setAutoFishEnabled: " + enabled);
    }

    /**
     * Восстанавливает runtime-состояние сохранённых авто-режимов после успешного входа в MainActivity.
     *
     * Зависимости:
     * - SharedPreferences (`isAutoFishEnabled()/isAutoFightEnabled()`) как источник сохранённых флагов;
     * - `setAutoFishEnabled(true)` для полной C#-цепочки AutoFish (инициализация runtime + bootstrap-навигация в `go=inf`);
     * - `setAutoFightEnabled(true)` как fallback, если включён только AutoFight;
     * - `AppVars.mainActivity`/`MainActivity.getMainWebView()` внутри указанных методов для фактического старта потока.
     *
     * Почему нужен метод:
     * - после повторного входа кнопки могут быть "ВКЛ" по prefs, но без повторного запуска runtime-инициализации
     *   авто-цепочка не стартует до ручного перехода в "Ваш персонаж";
     * - метод переиспользует существующие точки входа (`setAuto*Enabled`) без дублирования логики.
     */
    public void restorePersistentAutoModesAfterLogin() {
        boolean autoFish = isAutoFishEnabled();
        boolean autoFight = isAutoFightEnabled();
        Log.d(TAG, "restorePersistentAutoModesAfterLogin: autoFish=" + autoFish + ", autoFight=" + autoFight);

        if (autoFish) {
            setAutoFishEnabled(true);
            return;
        }

        restoreAutoFightRuntimeAfterLogin(autoFight);
    }

    private void restoreAutoFightRuntimeAfterLogin(boolean autoFightEnabledByProfile) {
        AppVars.Autoboi = autoFightEnabledByProfile ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff;

        boolean furyEnabledByProfile = AppVars.Profile != null && AppVars.Profile.hasAnyLezFuryGroup();
        AppVars.DoFury = furyEnabledByProfile;
        if (autoFightEnabledByProfile && furyEnabledByProfile) {
            AppVars.AutoFuryCheckScroll = true;
            AppVars.AutoFuryArmedScroll = false;
            AppVars.AutoFuryHand = "";
            AppVars.AutoFuryHandD = "";
        }

        syncBackgroundService("restoreAutoFightRuntimeAfterLogin(" + autoFightEnabledByProfile + ")");
        Log.d(TAG, "restoreAutoFightRuntimeAfterLogin: runtime autoboi=" + AppVars.Autoboi
                + ", profileAutoFight=" + autoFightEnabledByProfile);
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
        if (AppVars.Profile != null) {
            boolean profileValue = AppVars.Profile.SkinAuto;
            boolean prefValue = prefs.getBoolean(KEY_AUTO_SKIN, false);
            if (profileValue != prefValue) {
                prefs.edit().putBoolean(KEY_AUTO_SKIN, profileValue).apply();
                applyAutoSkinRuntimeFlags(profileValue, "sync_from_profile");
                Log.d(TAG, "isAutoSkinEnabled: sync pref from profile SkinAuto=" + profileValue);
                return profileValue;
            }
        }
        return prefs.getBoolean(KEY_AUTO_SKIN, false);
    }
    
    // Переключение авто-охоты.
    public void toggleAutoSkin() {
        boolean newState = !isAutoSkinEnabled();
        setAutoSkinEnabled(newState);
    }
    
    // Включение авто-охоты включает авто-бой и отключает несовместимые режимы.
    public void setAutoSkinEnabled(boolean enabled) {
        boolean autoFightWasEnabled = isAutoFightEnabled();
        if (enabled) {
            // При включении Авто-Охоты: если Авто-Бой выключен - включаем оба
            if (!autoFightWasEnabled) {
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
        
        prefs.edit().putBoolean(KEY_AUTO_SKIN, enabled).apply();
        applyAutoSkinRuntimeFlags(enabled, "setAutoSkinEnabled");
        if (enabled && autoFightWasEnabled) {
            triggerAutoSkinCharacterCheck();
        }
        if (AppVars.Profile != null && AppVars.Profile.SkinAuto != enabled) {
            AppVars.Profile.SkinAuto = enabled;
            AppVars.Profile.save(context);
        }
        Log.d(TAG, "setAutoSkinEnabled: " + enabled);
    }

    /**
     * Синхронизирует runtime-флаги AutoSkin с C#-семантикой `buttonAutoSkin`:
     * - при включении инициирует последовательность проверки умения/ножа/ресурсов;
     * - при выключении останавливает активные проверки AutoSkin.
     */
    private void applyAutoSkinRuntimeFlags(boolean enabled, String reason) {
        if (enabled) {
            AppVars.AutoSkinCheckUm = true;
            AppVars.AutoSkinCheckRes = true;
            AppVars.SkinUm = 0;
            AppVars.AutoSkinCheckKnife = true;
            AppVars.AutoSkinArmedKnife = false;
            AppVars.AutoSkinLastChecked = System.currentTimeMillis();
            Log.d(TAG, "applyAutoSkinRuntimeFlags: enabled, reason=" + reason);
        } else {
            AppVars.AutoSkinCheckUm = false;
            AppVars.AutoSkinCheckRes = false;
            AppVars.AutoSkinCheckKnife = false;
            AppVars.AutoSkinArmedKnife = false;
            Log.d(TAG, "applyAutoSkinRuntimeFlags: disabled, reason=" + reason);
        }
    }

    /**
     * При включении AutoSkin сразу запрашивает страницу персонажа (`go=inf`),
     * чтобы цепочка `AutoSkinCheckUm/Res/Knife` стартовала без ручного перехода.
     */
    private void triggerAutoSkinCharacterCheck() {
        if (AppVars.mainActivity == null || AppVars.mainActivity.get() == null) {
            Log.w(TAG, "triggerAutoSkinCharacterCheck: mainActivity is null");
            return;
        }
        AppVars.mainActivity.get().runOnUiThread(() -> {
            try {
                String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf";
                if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
                    reloadUrl += "&vcode=" + AppVars.VCode;
                }
                reloadUrl += "&ts=" + System.currentTimeMillis();
                Log.d(TAG, "triggerAutoSkinCharacterCheck: load " + reloadUrl);
                AppVars.mainActivity.get().getMainWebView().loadUrl(reloadUrl);
            } catch (Exception e) {
                Log.e(TAG, "triggerAutoSkinCharacterCheck: failed", e);
            }
        });
    }

    /**
     * Первичная синхронизация после создания менеджера:
     * если профиль уже загружен, `Profile.SkinAuto` считается источником истины.
     */
    private void syncAutoSkinWithProfileIfPresent() {
        if (AppVars.Profile == null) {
            return;
        }
        boolean profileValue = AppVars.Profile.SkinAuto;
        prefs.edit().putBoolean(KEY_AUTO_SKIN, profileValue).apply();
        applyAutoSkinRuntimeFlags(profileValue, "constructor_sync");
        Log.d(TAG, "syncAutoSkinWithProfileIfPresent: SkinAuto=" + profileValue);
    }

    private void syncAutoFightWithProfileIfPresent() {
        if (AppVars.Profile == null) {
            return;
        }
        boolean profileValue = AppVars.Profile.LezDoAutoboi;
        prefs.edit().putBoolean(KEY_PREFIX + "auto_fight", profileValue).apply();
        AppVars.Autoboi = profileValue ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff;
        Log.d(TAG, "syncAutoFightWithProfileIfPresent: LezDoAutoboi=" + profileValue);
    }
    
    // === AUTO_ATTACK (Авто-Нападение) ===
    
    // Авто-нападение: состояние.
    public boolean isAutoAttackEnabled() {
        return getAutoAttackToolId() != 0;
    }
    
    // Переключение авто-нападения.
    public void toggleAutoAttack() {
        boolean newState = !isAutoAttackEnabled();
        setAutoAttackEnabled(newState);
    }
    
    // Включение/выключение авто-нападения.
    public void setAutoAttackEnabled(boolean enabled) {
        if (enabled) {
            int toolId = getAutoAttackToolId();
            if (toolId == 0) {
                toolId = getLastNonZeroAutoAttackToolId();
            }
            setAutoAttackToolId(toolId);
        } else {
            setAutoAttackToolId(0);
        }

        // Аналог ПК-логики: авто-нападение работает вместе со "Слежением за локацией".
        // Поэтому при включении AUTO_ATTACK автоматически поднимаем LOCATION_TRACKING.
        Log.d(TAG, "setAutoAttackEnabled(wrapper): " + enabled + ", toolId=" + getAutoAttackToolId());
    }

    /**
     * Возвращает выбранный инструмент авто-нападения.
     *
     * Аналог C# `AppVars.AutoAttackToolId`.
     * Значение хранится в SharedPreferences и дублируется в `AppVars` для быстрого доступа
     * в потоке пост-фильтра/боевой логики.
     */
    public int getAutoAttackToolId() {
        int toolId = prefs.getInt(KEY_AUTO_ATTACK_TOOL_ID, AppVars.AutoAttackToolId);
        int safeToolId = normalizeAutoAttackToolId(toolId);
        if (safeToolId != toolId) {
            prefs.edit().putInt(KEY_AUTO_ATTACK_TOOL_ID, safeToolId).apply();
        }
        AppVars.AutoAttackToolId = safeToolId;
        if (safeToolId > 0) {
            rememberLastNonZeroAutoAttackToolId(safeToolId);
        }
        return safeToolId;
    }

    /**
     * Устанавливает инструмент авто-нападения (0..5).
     *
     * Значение:
     * - сохраняется в SharedPreferences для перезапуска клиента,
     * - синхронизируется в `AppVars.AutoAttackToolId` для runtime-потока.
     */
    public void setAutoAttackToolId(int toolId) {
        int safeToolId = normalizeAutoAttackToolId(toolId);
        prefs.edit().putInt(KEY_AUTO_ATTACK_TOOL_ID, safeToolId).apply();
        AppVars.AutoAttackToolId = safeToolId;
        if (safeToolId > 0) {
            rememberLastNonZeroAutoAttackToolId(safeToolId);
            if (!isLocationTrackingEnabled()) {
                setLocationTrackingEnabled(true);
            }
        }
        Log.d(TAG, "setAutoAttackToolId: " + safeToolId);
        syncBackgroundService("setAutoAttackToolId(" + safeToolId + ")");
    }

    private int normalizeAutoAttackToolId(int toolId) {
        if (toolId < 0) return 0;
        if (toolId > 5) return 5;
        return toolId;
    }

    private int getLastNonZeroAutoAttackToolId() {
        int candidate = normalizeAutoAttackToolId(
                prefs.getInt(KEY_AUTO_ATTACK_LAST_NON_ZERO_TOOL_ID, 1));
        return candidate == 0 ? 1 : candidate;
    }

    private void rememberLastNonZeroAutoAttackToolId(int toolId) {
        int safeToolId = normalizeAutoAttackToolId(toolId);
        if (safeToolId == 0) {
            return;
        }
        prefs.edit().putInt(KEY_AUTO_ATTACK_LAST_NON_ZERO_TOOL_ID, safeToolId).apply();
    }

    private void migrateLegacyAutoAttackFlagIfNeeded() {
        int toolId = normalizeAutoAttackToolId(prefs.getInt(KEY_AUTO_ATTACK_TOOL_ID, AppVars.AutoAttackToolId));
        boolean legacyEnabled = prefs.getBoolean(KEY_AUTO_ATTACK_LEGACY, false);
        if (toolId == 0 && legacyEnabled) {
            toolId = 1;
            prefs.edit()
                    .putInt(KEY_AUTO_ATTACK_TOOL_ID, toolId)
                    .putInt(KEY_AUTO_ATTACK_LAST_NON_ZERO_TOOL_ID, toolId)
                    .apply();
            Log.d(TAG, "migrateLegacyAutoAttackFlagIfNeeded: legacy auto_attack=true migrated to toolId=1");
        } else if (toolId > 0) {
            rememberLastNonZeroAutoAttackToolId(toolId);
        }
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
        return prefs.getBoolean(KEY_LOCATION_TRACKING, false);
    }
    
    // Переключение слежения за локацией.
    public void toggleLocationTracking() {
        boolean newState = !isLocationTrackingEnabled();
        setLocationTrackingEnabled(newState);
    }
    
    // Включение/выключение слежения за локацией.
    public void setLocationTrackingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCATION_TRACKING, enabled).apply();
        AppVars.DoShowWalkers = enabled;
        if (enabled) {
            AppVars.myCoordOld = "";
            AppVars.myLocOld = "";
            AppVars.myWalkers1 = "";
            AppVars.myWalkers2 = "";
        }
        Log.d(TAG, "setLocationTrackingEnabled: " + enabled);
        syncBackgroundService("setLocationTrackingEnabled(" + enabled + ")");

        // При включении сразу запрашиваем room-list, чтобы RoomManager получил тик немедленно.
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            ru.neverlands.abclient.MainActivity activity = AppVars.mainActivity.get();
            activity.runOnUiThread(() -> {
                try {
                    activity.onWalkersPollingConfigChanged();
                    if (enabled) {
                        activity.requestRoomUsersRefreshSoon();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "setLocationTrackingEnabled: room users refresh trigger failed", e);
                }
            });
        }
    }

    public int getWalkersPollIntervalSec() {
        int value = prefs.getInt(KEY_WALKERS_POLL_INTERVAL_SEC, WALKERS_POLL_INTERVAL_DEFAULT_SEC);
        return normalizeWalkersPollIntervalSec(value);
    }

    public void setWalkersPollIntervalSec(int sec) {
        int safe = normalizeWalkersPollIntervalSec(sec);
        prefs.edit().putInt(KEY_WALKERS_POLL_INTERVAL_SEC, safe).apply();
        Log.d(TAG, "setWalkersPollIntervalSec: " + safe);
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            ru.neverlands.abclient.MainActivity activity = AppVars.mainActivity.get();
            activity.runOnUiThread(() -> {
                try {
                    activity.onWalkersPollingConfigChanged();
                } catch (Exception e) {
                    Log.w(TAG, "setWalkersPollIntervalSec: polling reschedule failed", e);
                }
            });
        }
    }

    private int normalizeWalkersPollIntervalSec(int sec) {
        switch (sec) {
            case 1:
            case 2:
            case 5:
            case 10:
                return sec;
            default:
                return WALKERS_POLL_INTERVAL_DEFAULT_SEC;
        }
    }

    private void syncBackgroundService(String reason) {
        try {
            AutoModeForegroundService.syncServiceState(context, reason);
            Log.d(TAG, BG_TRACE_PREFIX + " syncBackgroundService: " + reason);
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " syncBackgroundService failed: " + reason, e);
        }
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
