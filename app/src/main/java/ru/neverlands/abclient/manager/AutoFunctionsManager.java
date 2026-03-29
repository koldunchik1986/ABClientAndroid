package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.model.QuickActionType;
import ru.neverlands.abclient.repository.ApiRepository;
import ru.neverlands.abclient.service.AutoModeForegroundService;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.MapPath;

/**
 * Менеджер автоматических функций (авто-бой, авто-рыбалка и т.д.).
 * Управляет включением/выключением авто-функций и их состоянием.
 */
public class AutoFunctionsManager {
    private static final String TAG = "AutoFunctionsManager";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
    private static final String CHARACTER_SYNC_LABEL = "\u0421\u0438\u043D\u0445\u0440\u0430\u043D\u0438\u0437\u0430\u0446\u0438\u044F \u041F\u0435\u0440\u0441\u043E\u043D\u0430\u0436\u0430";
    private static final long CHARACTER_SYNC_LOGIN_COOLDOWN_MS = 15_000L;
    private static final long CHARACTER_SYNC_AUTO_ENABLE_COOLDOWN_MS = 1_500L;
    private static final String PREFS_NAME = "auto_functions_prefs";
    private static final String KEY_PREFIX = "auto_function_";
    private static final String KEY_AUTO_SKIN = KEY_PREFIX + "auto_skin";
    private static final String KEY_AUTO_TREASURE = KEY_PREFIX + "auto_treasure";
    private static final String KEY_AUTO_ATTACK_LEGACY = KEY_PREFIX + "auto_attack";
    private static final String KEY_AUTO_ATTACK_TOOL_ID = KEY_PREFIX + "auto_attack_tool_id";
    private static final String KEY_AUTO_ATTACK_LAST_NON_ZERO_TOOL_ID = KEY_PREFIX + "auto_attack_last_non_zero_tool_id";
    private static final String KEY_LOCATION_TRACKING = KEY_PREFIX + "location_tracking";
    private static final String KEY_WALKERS_POLL_INTERVAL_SEC = KEY_PREFIX + "walkers_poll_interval_sec";
    private static final int WALKERS_POLL_INTERVAL_DEFAULT_SEC = 1;
    // Настройки Авто-Лечения (UI long-press + MainPhp/RoomManager используют общий набор ключей).
    private static final String PREF_AUTO_CURE_WOUND_LIGHT = "auto_cure_wound_light";
    private static final String PREF_AUTO_CURE_WOUND_MEDIUM = "auto_cure_wound_medium";
    private static final String PREF_AUTO_CURE_WOUND_HEAVY = "auto_cure_wound_heavy";
    private static final String PREF_AUTO_CURE_WOUND_BATTLE = "auto_cure_wound_battle";
    private static final String PREF_AUTO_CURE_TARGET_FRIENDS = "auto_cure_target_friends";
    private static final String PREF_AUTO_CURE_TARGET_NEUTRALS = "auto_cure_target_neutrals";
    private static final String PREF_AUTO_CURE_USE_SELF_ELIXIR = "auto_cure_use_self_elixir";
    private static final String PREF_AUTO_CURE_ELIXIR_LIGHT = "auto_cure_elixir_light";
    private static final String PREF_AUTO_CURE_ELIXIR_MEDIUM = "auto_cure_elixir_medium";
    private static final String PREF_AUTO_CURE_ELIXIR_HEAVY = "auto_cure_elixir_heavy";
    private static final String PREF_AUTO_TREASURE_USE_DIG = "auto_treasure_use_dig";
    private static final String PREF_AUTO_TREASURE_SHOVEL = "auto_treasure_shovel";
    private static final String PREF_AUTO_TREASURE_FIXED_CELL_ENABLED = "auto_treasure_fixed_cell_enabled";
    private static final String PREF_AUTO_TREASURE_FIXED_CELL = "auto_treasure_fixed_cell";
    private static final String PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_CHECK =
            "auto_treasure_thorough_neighbor_check";
    public static final String TREASURE_SHOVEL_NONE = "Нет";
    public static final String TREASURE_SHOVEL_ANY = "Любая лопата";
    public static final String TREASURE_SHOVEL_SEEKER = "Лопата кладоискателя";
    public static final String TREASURE_SHOVEL_TRAVEL = "Походная лопатка";
    public static final String TREASURE_SHOVEL_ARCHAEOLOGIST = "Лопата археолога";
    
    private static AutoFunctionsManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final CompasAuto compasAuto;
    private final BossAuto bossAuto;
    private volatile long lastCharacterSyncRequestedAtMs = 0L;
    
    // SharedPreferences фиксируют состояние автозадач между перезапусками.
    private AutoFunctionsManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.compasAuto = new CompasAuto(this.context, this.prefs, this);
        this.bossAuto = new BossAuto(this.context, this.prefs, this);
        // Поднимаем runtime-состояние выбранного инструмента авто-нападения из постоянного хранилища.
        migrateLegacyAutoAttackFlagIfNeeded();
        AppVars.AutoAttackToolId = getAutoAttackToolId();
        AppVars.DoShowWalkers = isLocationTrackingEnabled();
        syncAutoSkinWithProfileIfPresent();
        syncAutoFightWithProfileIfPresent();
        syncAutoTreasureWithProfileIfPresent();
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
    // Подробное описание (актуально для Android-порта 1:1):
    // Назначение:
    // - включает/выключает runtime авто-боя;
    // - синхронизирует состояние между профилем, AppVars и фоновым сервисом;
    // - при включении выполняет bootstrap боевого фрейма (ab_reload_probe), чтобы цикл удара стартовал без ручного клика.
    //
    // Зависимости:
    // - AppVars.Profile.LezDoAutoboi: источник/приемник постоянного флага;
    // - AppVars.Autoboi / AutoboiState: runtime флаг для боевого пайплайна;
    // - AppVars.DoFury и AutoFury*: подготовка опции "Ярость/Снежок";
    // - syncBackgroundService(...): запуск/остановка AutoModeForegroundService;
    // - AppVars.mainActivity.get().getMainWebView().loadUrl(...): фактический bootstrap fight.frame.
    //
    // Почему это важно:
    // - без bootstrap после включения флаг мог быть ON, но авто-ход не стартовал до ручного переключения.
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_fight");
        }

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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_fish");
        }
    }

    /**
     * Восстанавливает runtime-состояние сохранённых авто-режимов после успешного входа в MainActivity.
     *
     * Зависимости:
     * - SharedPreferences (`isAutoFishEnabled()/isAutoFightEnabled()`) как источник сохранённых флагов;
     * - `setAutoFishEnabled(true)` для полной C#-цепочки AutoFish (инициализация runtime + bootstrap-навигация в `go=inf`);
     * - `restoreAutoFightRuntimeAfterLogin(...)` для мягкого восстановления только runtime AutoFight
     *   (без лишнего reload верхнего фрейма и без принудительного запроса авто-удара);
     * - `AppVars.mainActivity`/`MainActivity.getMainWebView()` внутри указанных методов для фактического старта потока.
     *
     * Почему нужен метод:
     * - после повторного входа кнопки могут быть "ВКЛ" по prefs, но без повторного запуска runtime-инициализации
     *   авто-цепочка не стартует до ручного перехода в "Ваш персонаж";
     * - метод переиспользует существующие точки входа (`setAuto*Enabled`) без дублирования логики.
     */
    // Подробное описание:
    // Назначение:
    // - после успешного логина восстанавливает сохраненные авто-режимы в runtime.
    // Алгоритм:
    // - если включена авто-рыбалка, приоритетно запускается ее полная цепочка;
    // - иначе мягко восстанавливается runtime авто-боя через restoreAutoFightRuntimeAfterLogin(...).
    //
    // Зависимости:
    // - SharedPreferences: сохраненные флаги AutoFish/AutoFight;
    // - setAutoFishEnabled(true): полная инициализация рыбалки;
    // - restoreAutoFightRuntimeAfterLogin(...): мягкое восстановление боевого контура.
    public void restorePersistentAutoModesAfterLogin() {
        boolean autoFish = isAutoFishEnabled();
        boolean autoFight = isAutoFightEnabled();
        boolean autoTreasure = isAutoTreasureEnabled();
        Log.d(TAG, "restorePersistentAutoModesAfterLogin: autoFish=" + autoFish
                + ", autoFight=" + autoFight
                + ", autoTreasure=" + autoTreasure);

        requestCharacterSyncAfterLogin();
        requestClanWarsSyncAfterLogin();

        if (autoFish) {
            setAutoFishEnabled(true);
            return;
        }

        AppVars.DoSearchBox = autoTreasure;
        if (!autoTreasure) {
            ExtMap.flushVisitedToDisk();
            Log.d(TAG, "restorePersistentAutoModesAfterLogin: keep visited cache, entries="
                    + AppVars.SearchBoxVisited.size());
        }

        restoreAutoFightRuntimeAfterLogin(autoFight);
    }

    /**
     * Выполняет первичную "Синхронизацию Персонажа" после успешного логина.
     *
     * Что делает:
     * - берет nickname из текущего профиля;
     * - по cooldown не допускает частого повторного вызова;
     * - в отдельном daemon-потоке запрашивает `pinfo.cgi`;
     * - обновляет единый runtime-снимок через {@link CharacterVitalsManager#updateFromPinfo(NeverApi.PinfoVitals, String)};
     * - показывает toast с текущими HP/MA/усталостью.
     *
     * Зависимости:
     * - `AppVars.Profile.UserNick` — источник ника для pinfo-запроса;
     * - {@link NeverApi#getPinfoVitalsFromPinfo(String)} — источник серверных значений;
     * - {@link CharacterVitalsManager} — единая точка записи/чтения vitals;
     * - {@link #showCharacterSyncToast()} — визуальное подтверждение успешной синхронизации.
     *
     * Примечание:
     * - метод не переключает никакие авто-функции и не меняет навигацию;
     * - метод только синхронизирует параметры персонажа.
     */
    private void requestCharacterSyncAfterLogin() {
        requestCharacterSync("after-login",
                "AutoFunctionsManager.requestCharacterSyncAfterLogin",
                CHARACTER_SYNC_LOGIN_COOLDOWN_MS,
                true);
    }

    /**
     * Неблокирующий post-login sync текущих клановых войн (`wars.cgi`).
     *
     * Назначение:
     * - заранее прогреть кэш войн для `BossAuto` (БД-режим) без сетевого вызова в момент события;
     * - синхронизировать данные для экрана `Кланы -> Текущие войны`.
     *
     * Правила:
     * - метод не влияет на процесс логина и не переключает авто-функции;
     * - любые ошибки только логируются (мягкий отказ).
     *
     * Зависимости:
     * - `ClanWarsManager.syncWarsAsync(...)` — загрузка/парсинг `http://neverlands.ru/modules/api/wars.cgi`;
     * - внутренний in-memory/file cache `ClanWarsManager` для дальнейшего чтения `BossAuto` и `ClansActivity`.
     */
    private void requestClanWarsSyncAfterLogin() {
        try {
            ClanWarsManager.getInstance(context).syncWarsAsync(new ApiRepository.ApiCallback<List<ClanWarsManager.WarEntry>>() {
                @Override
                public void onSuccess(List<ClanWarsManager.WarEntry> result) {
                    int size = result == null ? 0 : result.size();
                    Log.i(TAG, "AUTO_BOSS_TRACE: post-login wars sync ok, rows=" + size);
                }

                @Override
                public void onFailure(String message) {
                    Log.w(TAG, "AUTO_BOSS_TRACE: post-login wars sync failed: " + message);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "AUTO_BOSS_TRACE: post-login wars sync exception", e);
        }
    }

    /**
     * Запускает синхронизацию параметров персонажа при включении авто-функции.
     *
     * Правила вызова:
     * - вызывать только в переходе `enabled=false -> enabled=true`;
     * - использовать короткий cooldown (`CHARACTER_SYNC_AUTO_ENABLE_COOLDOWN_MS`), чтобы не спамить `pinfo.cgi`
     *   при каскадном включении зависимых режимов;
     * - toast показывать только для `auto_cure`, чтобы пользователь явно видел, что лечение стартует
     *   по свежим данным.
     *
     * Зависимости:
     * - {@link #requestCharacterSync(String, String, long, boolean)} — общий исполнитель;
     * - `functionName` — диагностическая метка в `reason/source` для logcat-трассировки;
     * - `CharacterVitalsManager` обновляется внутри общего исполнителя.
     */
    private void requestCharacterSyncForAutoFunctionEnable(String functionName) {
        boolean showToast = "auto_cure".equals(functionName);
        requestCharacterSync("auto-enable:" + functionName,
                "AutoFunctionsManager.requestCharacterSyncForAutoFunctionEnable(" + functionName + ")",
                CHARACTER_SYNC_AUTO_ENABLE_COOLDOWN_MS,
                showToast);
    }

    /**
     * Унифицированный запуск server-sync персонажа через `pinfo.cgi`.
     *
     * Алгоритм:
     * 1) проверяет наличие активного профиля и валидного nick;
     * 2) применяет анти-спам cooldown;
     * 3) в daemon-потоке читает pinfo через {@link NeverApi#getPinfoVitalsFromPinfo(String)};
     * 4) обновляет runtime-снимок vitals через
     *    {@link CharacterVitalsManager#updateFromPinfo(NeverApi.PinfoVitals, String)};
     * 5) пишет расширенный trace в logcat; опционально показывает toast.
     *
     * Правила:
     * - метод не меняет состояние авто-функций и не запускает навигацию;
     * - метод отвечает только за актуализацию параметров персонажа;
     * - `reason/source` обязательно передавать заполненными для корректной диагностики.
     *
     * @param reason краткая причина синхронизации (для логов и имени потока)
     * @param source источник обновления, записывается в `CharacterVitalsManager.lastSource`
     * @param cooldownMs минимальный интервал между повторами sync
     * @param showToast показывать ли UI-подтверждение пользователю
     */
    private void requestCharacterSync(String reason, String source, long cooldownMs, boolean showToast) {
        if (AppVars.Profile == null) {
            return;
        }
        final String nick = AppVars.Profile.UserNick != null ? AppVars.Profile.UserNick.trim() : "";
        if (nick.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastCharacterSyncRequestedAtMs) < cooldownMs) {
            Log.d(TAG, "AUTO_BLAZ_TRACE: skip " + CHARACTER_SYNC_LABEL
                    + " (cooldown), reason=" + reason + ", cooldownMs=" + cooldownMs);
            return;
        }
        lastCharacterSyncRequestedAtMs = now;

        Thread syncThread = new Thread(() -> {
            NeverApi.PinfoVitals vitals = NeverApi.getPinfoVitalsFromPinfo(nick);
            if (vitals == null) {
                Log.w(TAG, "AUTO_BLAZ_TRACE: " + CHARACTER_SYNC_LABEL
                        + " failed (no vitals), nick=" + nick + ", reason=" + reason);
                return;
            }

            CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.updateFromPinfo(vitals, source);
            Log.i(TAG, "AUTO_BLAZ_TRACE: " + CHARACTER_SYNC_LABEL
                    + ", reason=" + reason
                    + ", tied=" + snapshot.tied
                    + ", hp=" + snapshot.curHp + "/" + snapshot.maxHp
                    + ", ma=" + snapshot.curMa + "/" + snapshot.maxMa
                    + ", nick=" + nick);
            if (showToast) {
                showCharacterSyncToast();
            }
        }, "CharacterSync_" + reason.replace(':', '_').replace('(', '_').replace(')', '_'));
        syncThread.setDaemon(true);
        syncThread.start();
    }

    /**
     * Показывает короткое UI-сообщение с текущим snapshot параметров персонажа.
     *
     * Формат:
     * - `Синхронизация Персонажа: HP cur/max; MA cur/max; Усталость: tied`.
     *
     * Зависимости:
     * - `AppVars.mainActivity` для доступа к UI-потоку;
     * - {@link CharacterVitalsManager#snapshot()} — источник актуальных значений;
     * - {@link CharacterVitalsManager#buildSyncMessage(String, CharacterVitalsManager.Snapshot)}
     *   — единый формат сообщения.
     */
    private void showCharacterSyncToast() {
        try {
            MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
            if (activity == null) {
                return;
            }
            CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.snapshot();
            final String message = CharacterVitalsManager.buildSyncMessage(CHARACTER_SYNC_LABEL, snapshot);
            activity.runOnUiThread(() -> Toast.makeText(
                    activity,
                    message,
                    Toast.LENGTH_SHORT
            ).show());
        } catch (Exception e) {
            Log.w(TAG, "showCharacterSyncToast failed", e);
        }
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

        // Bootstrap после restore:
        // - запускается только при включенном авто-бое в профиле;
        // - не меняет сам флаг, а только инициирует загрузку боевого кадра для старта цикла.
        if (autoFightEnabledByProfile && AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().runOnUiThread(() -> {
                try {
                    if (AppVars.mainActivity.get() == null || AppVars.mainActivity.get().getMainWebView() == null) {
                        return;
                    }
                    AppVars.ContentMainPhp = null;
                    AppVars.LastBoiTimer = new java.util.Date();
                    Log.d(TAG, "restoreAutoFightRuntimeAfterLogin: forcing frame reload bootstrap");
                    String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_reload_probe=1";
                    if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
                        reloadUrl += "&vcode=" + AppVars.VCode;
                    }
                    reloadUrl += "&ts=" + System.currentTimeMillis();
                    Log.d(TAG, "restoreAutoFightRuntimeAfterLogin: reload fight frame " + reloadUrl);
                    AppVars.mainActivity.get().getMainWebView().loadUrl(reloadUrl);
                } catch (Exception e) {
                    Log.e(TAG, "restoreAutoFightRuntimeAfterLogin: failed to reload fight frame", e);
                }
            });
        }
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_bait");
        }
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_skin");
        }
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

    /**
     * Первичная синхронизация авто-боя из профиля при создании менеджера.
     *
     * Назначение:
     * - сделать профиль (`Profile.LezDoAutoboi`) источником истины сразу после запуска процесса;
     * - выровнять persisted-значение в SharedPreferences с профилем;
     * - обновить runtime-флаг `AppVars.Autoboi`, чтобы боевой контур видел актуальное состояние.
     *
     * Важно:
     * - метод не запускает навигацию, не инициирует авто-удар и не форсирует fight-frame;
     * - используется только для "тихой" синхронизации состояния.
     *
     * Зависимости:
     * - `AppVars.Profile` (поле `LezDoAutoboi`);
     * - `SharedPreferences` (`KEY_PREFIX + "auto_fight"`);
     * - `AppVars.Autoboi` / `AutoboiState`.
     */
    private void syncAutoFightWithProfileIfPresent() {
        if (AppVars.Profile == null) {
            return;
        }
        boolean profileValue = AppVars.Profile.LezDoAutoboi;
        prefs.edit().putBoolean(KEY_PREFIX + "auto_fight", profileValue).apply();
        AppVars.Autoboi = profileValue ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff;
        Log.d(TAG, "syncAutoFightWithProfileIfPresent: LezDoAutoboi=" + profileValue);
    }

    /**
     * Первичная синхронизация "Авто-Клад" из профиля.
     * Источник истины: `Profile.AutoDig` (C# parity для menuitemDoSearchBox).
     */
    private void syncAutoTreasureWithProfileIfPresent() {
        if (AppVars.Profile == null) {
            return;
        }
        boolean profileValue = AppVars.Profile.AutoDig;
        prefs.edit().putBoolean(KEY_AUTO_TREASURE, profileValue).apply();
        AppVars.DoSearchBox = profileValue;
        Log.d(TAG, "syncAutoTreasureWithProfileIfPresent: AutoDig=" + profileValue);
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
        if (enabled && getAutoAttackToolId() > 0) {
            requestCharacterSyncForAutoFunctionEnable("auto_attack");
        }
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
            requestCharacterSyncForAutoFunctionEnable("auto_attack_tool_" + safeToolId);
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_invisible");
        }
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("location_tracking");
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

    // === AUTO_COMPASS (Компас/Авто-компас) ===

    // Авто-компас: текущее состояние.
    public boolean isAutoCompassEnabled() {
        return compasAuto.isAutoCompassEnabled();
    }

    // Переключение авто-компаса.
    public void toggleAutoCompass() {
        compasAuto.toggleAutoCompass();
    }

    // Включение/выключение авто-компаса.
    public void setAutoCompassEnabled(boolean enabled) {
        compasAuto.setAutoCompassEnabled(enabled);
    }

    public String getAutoCompassTargetNick() {
        return compasAuto.getAutoCompassTargetNick();
    }

    public void setAutoCompassTargetNick(String nick) {
        compasAuto.setAutoCompassTargetNick(nick);
    }

    public boolean isAutoCompassHuntMode() {
        return compasAuto.isAutoCompassHuntMode();
    }

    public void setAutoCompassHuntMode(boolean enabled) {
        compasAuto.setAutoCompassHuntMode(enabled);
    }

    public int getAutoCompassPollIntervalSec() {
        return compasAuto.getAutoCompassPollIntervalSec();
    }

    public void setAutoCompassPollIntervalSec(int sec) {
        compasAuto.setAutoCompassPollIntervalSec(sec);
    }

    public String getAutoCompassLastLocationLabel() {
        return compasAuto.getAutoCompassLastLocationLabel();
    }

    public String getAutoCompassCellsCsv() {
        return compasAuto.getAutoCompassCellsCsv();
    }

    public String getAutoCompassManualCellsCsv() {
        return compasAuto.getAutoCompassManualCellsCsv();
    }

    public void setAutoCompassManualCellsCsv(String csv) {
        compasAuto.setAutoCompassManualCellsCsv(csv);
    }

    /**
     * Результат ручного поиска локации цели из окна настроек "Авто-компас".
     * Нужен для сценария, когда пользователь вводит ник и хочет только обновить
     * "Текущую локацию цели" + список возможных клеток, без запуска движения.
     */
    public static final class CompassLocationResolveResult {
        public final boolean success;
        public final String targetNick;
        public final String locationLabel;
        public final String cellsCsv;
        public final String message;

        public CompassLocationResolveResult(
                boolean success,
                String targetNick,
                String locationLabel,
                String cellsCsv,
                String message) {
            this.success = success;
            this.targetNick = targetNick == null ? "" : targetNick.trim();
            this.locationLabel = locationLabel == null ? "" : locationLabel.trim();
            this.cellsCsv = cellsCsv == null ? "" : cellsCsv.trim();
            this.message = message == null ? "" : message.trim();
        }
    }

    /**
     * Ручной refresh цели для "Авто-компас":
     * 1) делает pinfo-запрос,
     * 2) обновляет сохраненные поля (ник/локация/клетки),
     * 3) возвращает данные для немедленного обновления UI диалога.
     */
    public CompassLocationResolveResult resolveAutoCompassLocation(String nick) {
        return compasAuto.resolveAutoCompassLocation(nick);
    }

    public void startManualCompassSearch(String nick) {
        compasAuto.startManualCompassSearch(nick, "manual");
    }

    public void startManualCompassSearch(String nick, String source) {
        compasAuto.startManualCompassSearch(nick, source);
    }

    /**
     * Запуск полного цикла "Авто-компас" из окна настроек.
     *
     * Назначение:
     * - используется кнопкой `ПОИСК ЦЕЛИ` в `QuickButtonsPanel`;
     * - переводит контур в режим полного обхода клеток (hunt-all);
     * - не делает "разовый шаг", а запускает постоянный цикл tick + навигации.
     *
     * Зависимости:
     * - `setAutoCompassTargetNick(...)` — фиксирует цель;
     * - `setAutoCompassHuntMode(true)` — разрешает проход по всем кандидатам;
     * - `setAutoCompassEnabled(true)` — стартует runtime-контур.
     *
     * Важно:
     * - если целевой ник не изменился, snapshot/кандидаты не сбрасываются.
     *   Это исключает лишний "холодный старт" между повторными нажатиями из настроек.
     */
    public void startSettingsCompassTargetSearch(String nick) {
        compasAuto.startSettingsCompassTargetSearch(nick, "settings");
    }

    public void startSettingsCompassTargetSearch(String nick, String source) {
        compasAuto.startSettingsCompassTargetSearch(nick, source);
    }

    /**
     * Публичный вход в цикл авто-компаса (вызывается из foreground-service/таймера).
     * Делегирует в внутренний метод с `forceNow=false`.
     */
    public void tickAutoCompass() {
        compasAuto.tickAutoCompass();
    }

    public void onRoomUsersUpdated(List<String> roomNicks, String roomLocationName) {
        compasAuto.onRoomUsersUpdated(roomNicks, roomLocationName);
        bossAuto.onRoomUsersUpdated(roomNicks, roomLocationName);
    }

    // === AUTO_BOSS (Авто-Боссы) ===

    // === AUTO_BOSS (Авто-Боссы) ===
    // Публичный фасад: UI и сервисы вызывают методы этого блока, а детальная
    // state-machine логика остаётся инкапсулированной в BossAuto.
    public boolean isAutoBossEnabled() {
        return bossAuto.isAutoBossEnabled();
    }

    public void toggleAutoBoss() {
        bossAuto.toggleAutoBoss();
    }

    public void setAutoBossEnabled(boolean enabled) {
        bossAuto.setAutoBossEnabled(enabled);
    }

    public void onIncomingChatMessage(String messageHtml) {
        bossAuto.onIncomingChatMessage(messageHtml);
    }

    public void tickAutoBoss() {
        bossAuto.tickAutoBoss();
    }

    public boolean isAutoBossAskTargetEnabled() {
        return bossAuto.isAutoBossAskTargetEnabled();
    }

    public void setAutoBossAskTargetEnabled(boolean enabled) {
        bossAuto.setAutoBossAskTargetEnabled(enabled);
    }

    public boolean isAutoBossBdModeEnabled() {
        return bossAuto.isAutoBossBdModeEnabled();
    }

    public void setAutoBossBdModeEnabled(boolean enabled) {
        bossAuto.setAutoBossBdModeEnabled(enabled);
    }

    public boolean isAutoBossTrackCurrentWarsEnabled() {
        return bossAuto.isAutoBossTrackCurrentWarsEnabled();
    }

    public void setAutoBossTrackCurrentWarsEnabled(boolean enabled) {
        bossAuto.setAutoBossTrackCurrentWarsEnabled(enabled);
    }

    /**
     * Флаг отправки клан-уведомлений в сценарии Авто-Босса.
     *
     * Зависимости:
     * - источник истины: BossAuto (SharedPreferences + runtime-проверки);
     * - потребители: QuickButtonsPanel и runtime-ветки BossAuto.
     */
    public boolean isAutoBossClanNotifyEnabled() {
        return bossAuto.isAutoBossClanNotifyEnabled();
    }

    /**
     * Сохраняет настройку клан-уведомлений для Авто-Босса.
     * Важно: метод только меняет конфиг, но сам сообщения в чат не отправляет.
     */
    public void setAutoBossClanNotifyEnabled(boolean enabled) {
        bossAuto.setAutoBossClanNotifyEnabled(enabled);
    }

    public int getAutoBossWaitBeforeScrollSec() {
        return bossAuto.getAutoBossWaitBeforeScrollSec();
    }

    public void setAutoBossWaitBeforeScrollSec(int sec) {
        bossAuto.setAutoBossWaitBeforeScrollSec(sec);
    }

    public int getAutoBossSearchTimeoutSec() {
        return bossAuto.getAutoBossSearchTimeoutSec();
    }

    public void setAutoBossSearchTimeoutSec(int sec) {
        bossAuto.setAutoBossSearchTimeoutSec(sec);
    }

    public int getAutoBossWaitFightTimeoutSec() {
        return bossAuto.getAutoBossWaitFightTimeoutSec();
    }

    public void setAutoBossWaitFightTimeoutSec(int sec) {
        bossAuto.setAutoBossWaitFightTimeoutSec(sec);
    }
    // Внутренние точки расширения для вынесенных модулей (например, `CompasAuto`).
    // Оставляем package-private доступ, чтобы не раскрывать их наружу API менеджера.
    void requestCharacterSyncForAutoFunctionEnableInternal(String functionName) {
        requestCharacterSyncForAutoFunctionEnable(functionName);
    }

    void syncBackgroundServiceInternal(String reason) {
        syncBackgroundService(reason);
    }

    void requestQuickButtonsRefreshInternal(String reason) {
        try {
            MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> activity.refreshQuickButtonsPanelState(reason));
        } catch (Exception e) {
            Log.w(TAG, "requestQuickButtonsRefreshInternal failed, reason=" + reason, e);
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_detect");
        }
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_summon");
        }
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_cure");
        }
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

    // Какие типы травм лечить (по умолчанию: все включены).
    public boolean isAutoCureWoundLightEnabled() {
        return getDefaultBoolean(PREF_AUTO_CURE_WOUND_LIGHT, true);
    }

    public void setAutoCureWoundLightEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_CURE_WOUND_LIGHT, enabled);
    }

    public boolean isAutoCureWoundMediumEnabled() {
        return getDefaultBoolean(PREF_AUTO_CURE_WOUND_MEDIUM, true);
    }

    public void setAutoCureWoundMediumEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_CURE_WOUND_MEDIUM, enabled);
    }

    public boolean isAutoCureWoundHeavyEnabled() {
        return getDefaultBoolean(PREF_AUTO_CURE_WOUND_HEAVY, true);
    }

    public void setAutoCureWoundHeavyEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_CURE_WOUND_HEAVY, enabled);
    }

    public boolean isAutoCureWoundBattleEnabled() {
        return getDefaultBoolean(PREF_AUTO_CURE_WOUND_BATTLE, true);
    }

    public void setAutoCureWoundBattleEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_CURE_WOUND_BATTLE, enabled);
    }

    public boolean isAutoCureWoundTypeEnabled(int woundType) {
        switch (woundType) {
            case 1:
                return isAutoCureWoundLightEnabled();
            case 2:
                return isAutoCureWoundMediumEnabled();
            case 3:
                return isAutoCureWoundHeavyEnabled();
            case 4:
                return isAutoCureWoundBattleEnabled();
            default:
                return false;
        }
    }

    // Какие цели лечить в комнате (self лечится отдельно и всегда приоритетно).
    public boolean isAutoCureTargetFriendsEnabled() {
        return getDefaultBoolean(PREF_AUTO_CURE_TARGET_FRIENDS, true);
    }

    public void setAutoCureTargetFriendsEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_CURE_TARGET_FRIENDS, enabled);
    }

    public boolean isAutoCureTargetNeutralsEnabled() {
        return getDefaultBoolean(PREF_AUTO_CURE_TARGET_NEUTRALS, true);
    }

    public void setAutoCureTargetNeutralsEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_CURE_TARGET_NEUTRALS, enabled);
    }

    // Настройки self-лечения эликсиром.
    public boolean isAutoCureUseSelfElixirEnabled() {
        return getDefaultBoolean(PREF_AUTO_CURE_USE_SELF_ELIXIR, false);
    }

    public void setAutoCureUseSelfElixirEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_CURE_USE_SELF_ELIXIR, enabled);
    }

    public boolean isAutoCureSelfElixirLightEnabled() {
        return getDefaultBoolean(PREF_AUTO_CURE_ELIXIR_LIGHT, true);
    }

    public void setAutoCureSelfElixirLightEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_CURE_ELIXIR_LIGHT, enabled);
    }

    public boolean isAutoCureSelfElixirMediumEnabled() {
        return getDefaultBoolean(PREF_AUTO_CURE_ELIXIR_MEDIUM, true);
    }

    public void setAutoCureSelfElixirMediumEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_CURE_ELIXIR_MEDIUM, enabled);
    }

    public boolean isAutoCureSelfElixirHeavyEnabled() {
        return getDefaultBoolean(PREF_AUTO_CURE_ELIXIR_HEAVY, true);
    }

    public void setAutoCureSelfElixirHeavyEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_CURE_ELIXIR_HEAVY, enabled);
    }

    public boolean isAutoCureSelfElixirEnabledForWound(int woundType) {
        if (!isAutoCureUseSelfElixirEnabled()) {
            return false;
        }
        switch (woundType) {
            case 1:
                return isAutoCureSelfElixirLightEnabled();
            case 2:
                return isAutoCureSelfElixirMediumEnabled();
            case 3:
                return isAutoCureSelfElixirHeavyEnabled();
            default:
                // Боевую травму лечим только аптечкой.
                return false;
        }
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_drink");
        }
    }

    // === AUTO_TREASURE (Авто-Клад / DoSearchBox) ===

    // Возвращает текущее состояние "Авто-Клад".
    public boolean isAutoTreasureEnabled() {
        if (AppVars.Profile != null) {
            boolean profileValue = AppVars.Profile.AutoDig;
            boolean prefValue = prefs.getBoolean(KEY_AUTO_TREASURE, profileValue);
            if (prefValue != profileValue) {
                prefs.edit().putBoolean(KEY_AUTO_TREASURE, profileValue).apply();
            }
            AppVars.DoSearchBox = profileValue;
            return profileValue;
        }
        boolean enabled = prefs.getBoolean(KEY_AUTO_TREASURE, false);
        AppVars.DoSearchBox = enabled;
        return enabled;
    }

    // Переключение "Авто-Клад".
    public void toggleAutoTreasure() {
        setAutoTreasureEnabled(!isAutoTreasureEnabled());
    }

    /**
     * Включение/выключение "Авто-Клад".
     * C# parity: menuitemDoSearchBox -> AppVars.DoSearchBox + ReloadMainFrame().
     */
    public void setAutoTreasureEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_TREASURE, enabled).apply();
        AppVars.DoSearchBox = enabled;

        if (AppVars.Profile != null && AppVars.Profile.AutoDig != enabled) {
            AppVars.Profile.AutoDig = enabled;
            AppVars.Profile.save(context);
        }

        if (!enabled) {
            ExtMap.flushVisitedToDisk();
            AppVars.AutoTreasureDigPendingInventory = false;
            AppVars.AutoTreasureShovelReady = false;
            AppVars.AutoTreasureShovelReadyOption = "";
            AppVars.TreasureDigPauseNonCombatAutoFunctions = false;
            if (AppVars.AutoMoving) {
                stopAutoMoving();
            }
            Log.d(TAG, "setAutoTreasureEnabled: keep visited cache on disable, entries="
                    + AppVars.SearchBoxVisited.size());
        }

        Log.d(TAG, "setAutoTreasureEnabled: " + enabled);
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_treasure");
        }
        if (enabled && AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().runOnUiThread(() -> {
                try {
                    if (AppVars.mainActivity.get() == null || AppVars.mainActivity.get().getMainWebView() == null) {
                        return;
                    }
                    String reloadUrl = "http://neverlands.ru/main.php?ab_search_box_bootstrap=1&r="
                            + System.currentTimeMillis();
                    AppVars.mainActivity.get().getMainWebView().loadUrl(reloadUrl);
                    Log.d(TAG, "setAutoTreasureEnabled: bootstrap reload " + reloadUrl);
                } catch (Exception e) {
                    Log.e(TAG, "setAutoTreasureEnabled: bootstrap reload failed", e);
                }
            });
        }
    }

    public boolean isAutoTreasureDigEnabled() {
        return getDefaultBoolean(PREF_AUTO_TREASURE_USE_DIG, false);
    }

    public void setAutoTreasureDigEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_TREASURE_USE_DIG, enabled);
    }

    public String getAutoTreasureShovelOption() {
        String option = getDefaultString(PREF_AUTO_TREASURE_SHOVEL, TREASURE_SHOVEL_ANY).trim();
        return option.isEmpty() ? TREASURE_SHOVEL_ANY : option;
    }

    public void setAutoTreasureShovelOption(String option) {
        String normalized = option == null ? "" : option.trim();
        if (normalized.isEmpty()) {
            normalized = TREASURE_SHOVEL_ANY;
        }
        putDefaultString(PREF_AUTO_TREASURE_SHOVEL, normalized);
    }

    public boolean isAutoTreasureFixedCellEnabled() {
        return getDefaultBoolean(PREF_AUTO_TREASURE_FIXED_CELL_ENABLED, false);
    }

    public void setAutoTreasureFixedCellEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_TREASURE_FIXED_CELL_ENABLED, enabled);
    }

    public String getAutoTreasureFixedCellRegNum() {
        return normalizeRegNum(getDefaultString(PREF_AUTO_TREASURE_FIXED_CELL, ""));
    }

    public void setAutoTreasureFixedCellRegNum(String regNum) {
        putDefaultString(PREF_AUTO_TREASURE_FIXED_CELL, normalizeRegNum(regNum));
    }

    public boolean isAutoTreasureFixedCellConfigured() {
        return isAutoTreasureFixedCellEnabled() && !getAutoTreasureFixedCellRegNum().isEmpty();
    }

    /**
     * Доп. режим "Авто-Клад": тщательная проверка соседних клеток.
     *
     * Зависимости:
     * - `MapAjax.findNextDestForBox(...)` читает это значение и встраивает detour-логику
     *   поверх базового выбора "самой старой" клетки.
     * - Настройка хранится в default SharedPreferences, как и остальные параметры Auto-Клада.
     */
    public boolean isAutoTreasureThoroughNeighborCheckEnabled() {
        return getDefaultBoolean(PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_CHECK, false);
    }

    public void setAutoTreasureThoroughNeighborCheckEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_CHECK, enabled);
    }

    private String normalizeRegNum(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", "");
    }
    
    // === AUTO_MOVING (Навигатор) ===
    //
    // Навигатор — это не авто-функция с постоянным включением, а навигационное
    // runtime-состояние (C# AppVars.AutoMoving). Хранится только в AppVars, не в SharedPreferences.
    // Запуск и остановка: startAutoMoving(destination) / stopAutoMoving().

    // Возвращает true, если навигатор активен (рейс к пункту назначения в процессе).
    public boolean isAutoMovingEnabled() {
        return AppVars.AutoMoving;
    }

    // Непосредственное включение/выключение флага навигатора (без выбора маршрута).
    // Для полноценного запуска используйте startAutoMoving(destination).
    public void setAutoMovingEnabled(boolean enabled) {
        AppVars.AutoMoving = enabled;
        Log.d(TAG, "setAutoMovingEnabled: " + enabled);
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_moving");
        }
    }

    // Переключение флага навигатора (оставлен для совместимости; лучше использовать
    // startAutoMoving / stopAutoMoving для полного управления маршрутом).
    public void toggleAutoMoving() {
        setAutoMovingEnabled(!AppVars.AutoMoving);
    }

    // Запуск навигатора к указанному пункту назначения.
    public void startAutoMoving(String destination) {
        if (destination == null || destination.isEmpty()) {
            Log.w(TAG, "startAutoMoving: destination is empty");
            return;
        }
        AppVars.AutoMovingDestinaton = destination;
        AppVars.AutoMovingNextJump = null;
        AppVars.AutoMovingJumps = 0;
        AppVars.AutoMovingCityGate = ru.neverlands.abclient.model.CityGateType.None;
        String mapLocation = (AppVars.Profile != null) ? AppVars.Profile.MapLocation : null;
        if (mapLocation != null && !mapLocation.isEmpty()) {
            MapPath path = new MapPath(mapLocation, java.util.Collections.singletonList(destination));
            AppVars.AutoMovingMapPath = path;
            AppVars.AutoMovingNextJump = path.nextJump;
            AppVars.AutoMovingJumps = path.jumps;
            AppVars.AutoMovingCityGate = path.cityGate;
            Log.d(TAG, "startAutoMoving: destination=" + destination + " pathExists=" + path.pathExists + " jumps=" + path.jumps);
        } else {
            AppVars.AutoMovingMapPath = null;
            Log.d(TAG, "startAutoMoving: destination=" + destination + " (MapLocation unknown, path will be built lazily)");
        }
        AppVars.AutoMoving = true;
        requestCharacterSyncForAutoFunctionEnable("auto_moving_start");
        triggerAutoMovingBootstrapNavigation();
    }

    // Остановка навигатора.
    public void stopAutoMoving() {
        AppVars.AutoMoving = false;
        AppVars.AutoMovingDestinaton = null;
        AppVars.AutoMovingMapPath = null;
        AppVars.AutoMovingNextJump = null;
        AppVars.AutoMovingJumps = 0;
        AppVars.AutoMovingCityGate = ru.neverlands.abclient.model.CityGateType.None;
        Log.d(TAG, "stopAutoMoving");
    }

    /**
     * Стартовый "пульс" навигатора:
     * сразу после включения принудительно грузим `main.php?go=inf`, чтобы postfilter-цепочка
     * (`MainPhp` -> `MainPhpCityNavigation`/`MapAjax`) начала первый шаг без ручного клика.
     *
     * Зависимости:
     * - `AppVars.mainActivity` + основной WebView верхнего фрейма;
     * - runtime-флаг `AppVars.AutoMoving`;
     * - postfilter-навигация в `MainPhp`/`MapAjax`/`TeleportAjax`.
     */
    private void triggerAutoMovingBootstrapNavigation() {
        MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
        if (activity == null) {
            Log.d(TAG, "startAutoMoving: bootstrap skipped (activity is null)");
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                if (activity.binding == null || activity.binding.appBarMain == null
                        || activity.binding.appBarMain.contentMain == null
                        || activity.binding.appBarMain.contentMain.webView == null) {
                    Log.d(TAG, "startAutoMoving: bootstrap skipped (webView is null)");
                    return;
                }
                String vcode = AppVars.VCode != null ? AppVars.VCode.trim() : "";
                String url;
                if (!vcode.isEmpty()) {
                    url = "http://neverlands.ru/main.php?get_id=56&act=10&go=ret&vcode="
                            + vcode
                            + "&ab_nav_bootstrap=1&r="
                            + System.currentTimeMillis();
                } else {
                    // Fallback: если vcode еще не извлечен, запускаем старый bootstrap через go=inf.
                    url = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_nav_bootstrap=1&r="
                            + System.currentTimeMillis();
                }
                activity.binding.appBarMain.contentMain.webView.loadUrl(url);
                Log.d(TAG, "startAutoMoving: bootstrap navigation to " + url);
            } catch (Exception e) {
                Log.e(TAG, "startAutoMoving: bootstrap navigation failed", e);
            }
        });
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_cut");
        }
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
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_refresh");
        }
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
            case AUTO_COMPASS: return isAutoCompassEnabled();
            case AUTO_BOSS: return isAutoBossEnabled();
            case AUTO_INVISIBLE: return isAutoInvisibleEnabled();
            case LOCATION_TRACKING: return isLocationTrackingEnabled();
            case AUTO_DETECT: return isAutoDetectEnabled();
            case AUTO_SUMMON: return isAutoSummonEnabled();
            case AUTO_CURE: return isAutoCureEnabled();
            case AUTO_DRINK: return isAutoDrinkEnabled();
            case AUTO_MOVING: return isAutoMovingEnabled();
            case AUTO_TREASURE: return isAutoTreasureEnabled();
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
            case AUTO_COMPASS: toggleAutoCompass(); break;
            case AUTO_BOSS: toggleAutoBoss(); break;
            case AUTO_INVISIBLE: toggleAutoInvisible(); break;
            case LOCATION_TRACKING: toggleLocationTracking(); break;
            case AUTO_DETECT: toggleAutoDetect(); break;
            case AUTO_SUMMON: toggleAutoSummon(); break;
            case AUTO_CURE: toggleAutoCure(); break;
            case AUTO_DRINK: toggleAutoDrink(); break;
            case AUTO_MOVING: toggleAutoMoving(); break;
            case AUTO_TREASURE: toggleAutoTreasure(); break;
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
        setAutoCompassEnabled(false);
        setAutoBossEnabled(false);
        setAutoInvisibleEnabled(false);
        setLocationTrackingEnabled(false);
        setAutoDetectEnabled(false);
        setAutoSummonEnabled(false);
        setAutoCureEnabled(false);
        setAutoDrinkEnabled(false);
        setAutoMovingEnabled(false);
        setAutoTreasureEnabled(false);
        setAutoCutEnabled(false);
        setAutoRefreshEnabled(false);
    }
}
