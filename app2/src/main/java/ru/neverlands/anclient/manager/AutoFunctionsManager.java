package ru.neverlands.anclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ru.neverlands.anclient.license.LicenseRuntime;
import ru.neverlands.anclient.MainActivity;
import ru.neverlands.anclient.model.AutoboiState;
import ru.neverlands.anclient.model.QuickActionType;
import ru.neverlands.anclient.repository.ApiRepository;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.SessionManager;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.service.AutoModeForegroundService;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.ExtMap;
import ru.neverlands.anclient.utils.MapPath;

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
    // Anti-Captcha хранится как обычная auto-function, но лицензируется строже:
    // - `LicenseFeature.expandPublicFeatureSpec(...)` никогда не выдаёт `anti_captcha` из public bundle;
    // - `full`/custom индивидуальный grant включает её через `QuickActionType.AUTO_CAPTCHA.getActionKey()`;
    // - при истечении grant `LicenseRuntime.requireSession(...)` обновляет сессию и вызывает
    //   `disableUnavailableFeatures(...)`, который сбрасывает этот persisted-флаг.
    // Такой же non-public контракт применён к `AUTO_CUT`: флаг `auto_cut` виден только
    // в individual full/custom grant и затирается тем же downgrade-проходом ниже.
    private static final String KEY_ANTI_CAPTCHA = KEY_PREFIX + "anti_captcha";
    private static final String PREF_ANTI_CAPTCHA_API_KEY = "anti_captcha_api_key";
    private static final String PREF_ANTI_CAPTCHA_PHRASE = "anti_captcha_phrase";
    private static final String PREF_ANTI_CAPTCHA_CASE = "anti_captcha_case";
    private static final String PREF_ANTI_CAPTCHA_NUMERIC = "anti_captcha_numeric";
    private static final String PREF_ANTI_CAPTCHA_MATH = "anti_captcha_math";
    private static final String PREF_ANTI_CAPTCHA_MIN_LENGTH = "anti_captcha_min_length";
    private static final String PREF_ANTI_CAPTCHA_MAX_LENGTH = "anti_captcha_max_length";
    private static final String PREF_ANTI_CAPTCHA_LANGUAGE_POOL = "anti_captcha_language_pool";
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
    // Доп. флаги поведения Auto-Клада (хранятся в default SharedPreferences, чтобы
    // postfilter-контур `MapAjax` мог читать те же значения без дублирования состояния):
    // - THOROUGH_NEIGHBOR_CHECK: дообход соседних клеток перед базовой "старой" целью.
    // - SMART_GENERATION: защита от повторных проверок слишком "свежих" клеток.
    private static final String PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_CHECK =
            "auto_treasure_thorough_neighbor_check";
    private static final String PREF_AUTO_TREASURE_SMART_GENERATION = "auto_treasure_smart_generation";
    private static final String PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_RADIUS =
            "auto_treasure_thorough_neighbor_radius";
    private static final String PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_MIN_AGE_MINUTES =
            "auto_treasure_thorough_neighbor_min_age_minutes";
    private static final String PREF_AUTO_TREASURE_DETOUR_CHAT_ENABLED =
            "auto_treasure_detour_chat_enabled";
    private static final int AUTO_TREASURE_THOROUGH_RADIUS_MIN = 1;
    private static final int AUTO_TREASURE_THOROUGH_RADIUS_MAX = 3;
    private static final int AUTO_TREASURE_THOROUGH_RADIUS_DEFAULT = 3;
    private static final int AUTO_TREASURE_THOROUGH_MIN_AGE_MINUTES_MIN = 1;
    private static final int AUTO_TREASURE_THOROUGH_MIN_AGE_MINUTES_MAX = 24 * 60;
    private static final int AUTO_TREASURE_THOROUGH_MIN_AGE_MINUTES_DEFAULT = 30;
    public static final String TREASURE_SHOVEL_NONE = "Нет";
    public static final String TREASURE_SHOVEL_ANY = "Любая лопата";
    public static final String TREASURE_SHOVEL_SEEKER = "Лопата кладоискателя";
    public static final String TREASURE_SHOVEL_TRAVEL = "Походная лопатка";
    public static final String TREASURE_SHOVEL_ARCHAEOLOGIST = "Лопата археолога";
    public static final int ANTI_CAPTCHA_NUMERIC_NONE = 0;
    public static final int ANTI_CAPTCHA_NUMERIC_NUMBERS_ONLY = 1;
    public static final int ANTI_CAPTCHA_NUMERIC_NO_NUMBERS = 2;
    public static final int ANTI_CAPTCHA_MIN_LENGTH_DEFAULT = 5;
    public static final int ANTI_CAPTCHA_MAX_LENGTH_DEFAULT = 5;
    
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

    private boolean rejectFeatureIfDenied(QuickActionType type, boolean enabled, String source) {
        // Guard на стороне setter. Если вызывающий код пытается включить feature,
        // которой нет в `LicenseSession.enabledFeatures`, отказываем до изменения AppVars/Profile/prefs.
        // `source` нужен только для логов, чтобы было видно, какая функция пыталась включиться.
        if (!enabled || type == null || type == QuickActionType.NONE) {
            return false;
        }
        if (LicenseRuntime.getInstance().isActionAllowed(type)) {
            return false;
        }
        AppLog.w("ANCLIENT_LICENSE", TAG, "LICENSE_FEATURE_DENIED: source=" + source
                + ", action=" + type.getActionKey());
        requestQuickButtonsRefreshInternal("license_denied:" + source);
        return true;
    }

    private boolean isFeatureAvailable(QuickActionType type, String source) {
        // Guard на стороне getter/tick. Persisted-флаги могут пережить смену лицензии,
        // поэтому каждый public getter/tick проверяет runtime-доступность перед выдачей ON-состояния.
        if (type == null || type == QuickActionType.NONE) {
            return true;
        }
        boolean allowed = LicenseRuntime.getInstance().isActionAllowed(type);
        if (!allowed) {
            AppLog.w("ANCLIENT_LICENSE", TAG, "LICENSE_FEATURE_HIDDEN: source=" + source
                    + ", action=" + type.getActionKey());
        }
        return allowed;
    }

    // === AUTO_FIGHT (Авто-Бой) ===

    // Текущее состояние авто-боя.
    public boolean isAutoFightEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_FIGHT, "isAutoFightEnabled")) {
            AppVars.Autoboi = AutoboiState.AutoboiOff;
            return false;
        }
        if (AppVars.Profile != null) {
            boolean profileValue = AppVars.Profile.LezDoAutoboi;
            boolean prefValue = prefs.getBoolean(KEY_PREFIX + "auto_fight", profileValue);
            if (prefValue != profileValue) {
                prefs.edit().putBoolean(KEY_PREFIX + "auto_fight", profileValue).apply();
                AppLog.d(TAG, "isAutoFightEnabled: sync pref from profile LezDoAutoboi=" + profileValue);
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
        if (rejectFeatureIfDenied(QuickActionType.AUTO_FIGHT, enabled, "setAutoFightEnabled")) {
            prefs.edit().putBoolean(KEY_PREFIX + "auto_fight", false).apply();
            AppVars.Autoboi = AutoboiState.AutoboiOff;
            if (AppVars.Profile != null) {
                AppVars.Profile.LezDoAutoboi = false;
                AppVars.Profile.save(context);
            }
            return;
        }
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
            AppLog.d(TAG, "setAutoFightEnabled: AutoFury primed (DoFury=true)");
        }

        AppLog.d(TAG, "setAutoFightEnabled: " + enabled);
        syncBackgroundService("setAutoFightEnabled(" + enabled + ")");
        boolean suppressFightBootstrapBecauseAutoFish = enabled
                && (isAutoFishEnabled() || (AppVars.Profile != null && AppVars.Profile.AutoFish));
        if (suppressFightBootstrapBecauseAutoFish) {
            AppLog.d(TAG, TAG, "AUTO_FISH_TRACE suppress auto-fight bootstrap reload because auto-fish owns cold start");
        }
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_fight");
        }

        // КРИТИЧНЫЙ ФИХ: Синхронизируем FightViewModel UI-состояние с runtime-состоянием.
        // Это необходимо для правильной работы автобоя при нежданной атаке во время навигации.
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().getFightViewModel().setAutoBattleActive(enabled);
            AppLog.d(TAG, "setAutoFightEnabled: FightViewModel UI state synced to " + enabled);
        }

        // При включении делаем форсированную загрузку боевого кадра (fight.frame).
        if (enabled && !suppressFightBootstrapBecauseAutoFish) {
            // При включении автобоя дергаем авто-ход и форсируем загрузку боевого кадра, как в ПК версии.
            if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
                AppVars.mainActivity.get().runOnUiThread(() -> {
                    try {
                        // сброс кеша и таймера, чтобы не зависать на старом кадре ручного боя
                        // Сбрасываем кэш и таймеры, чтобы не "зависнуть" на старом кадре.
                        AppVars.ContentMainPhp = null;
                        AppVars.LastBoiTimer = new java.util.Date();
                        // Запрашиваем авто-удар (логика автохода в MainActivity).
                        AppLog.d(TAG, "setAutoFightEnabled: immediate requestAutoTurn disabled, forcing frame reload only");
                        // Прямая перезагрузка боевого фрейма, с vcode если он есть.
                        String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_reload_probe=1";
                        String freshVcode = SessionManager.getInstance().getValidVCodeForAction("autofight_reload");
                        if (freshVcode != null && !freshVcode.isEmpty()) {
                            reloadUrl += "&vcode=" + freshVcode;
                        } else {
                            AppLog.w(TAG, TAG, "AUTO_FISH_TRACE auto-fight bootstrap reload has no vcode");
                        }
                        reloadUrl += "&ts=" + System.currentTimeMillis();
                        AppLog.d(TAG, "setAutoFightEnabled: reload fight frame " + reloadUrl);
                        AppVars.mainActivity.get().getMainWebView().loadUrl(reloadUrl);
                        // страховочный повтор через ~1.2с, если первый кадр ещё был ручным
                        // Страховочный повтор через ~1.2с: нужен, если первый кадр был "ручным".
                        // Удален second reload: в некоторых сессиях он провоцировал лишние перезагрузки
                        // верхнего фрейма и мешал нормальной навигации после боя.
                    } catch (Exception e) {
                        AppLog.e(TAG, "setAutoFightEnabled: failed to trigger auto turn", e);
                    }
                });
            }
        }
    }
    
    // === AUTO_FISH (Авто-Рыбалка) ===
    
    // Авто-рыбалка: состояние.
    public boolean isAutoFishEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_FISH, "isAutoFishEnabled")) {
            return false;
        }
        return prefs.getBoolean(KEY_PREFIX + "auto_fish", false);
    }
    
    // Переключение авто-рыбалки.
    public void toggleAutoFish() {
        boolean newState = !isAutoFishEnabled();
        setAutoFishEnabled(newState);
    }
    
    // Включение авто-рыбалки включает авто-бой (враги нападают в озере).
    // Выключение авто-рыбалки не отключает авто-бой.
    public void setAutoFishEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_FISH, enabled, "setAutoFishEnabled")) {
            prefs.edit().putBoolean(KEY_PREFIX + "auto_fish", false).apply();
            if (AppVars.Profile != null) {
                AppVars.Profile.AutoFish = false;
                AppVars.Profile.save(context);
            }
            return;
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_fish", enabled).apply();
        if (AppVars.Profile != null) {
            AppVars.Profile.AutoFish = enabled;
        }
        if (enabled) {
            // При включении Авто-Рыбалки: если Авто-Бой выключен - включаем оба
            if (!isAutoFightEnabled()) {
                setAutoFightEnabled(true);
                AppLog.d(TAG, "setAutoFishEnabled: Авто-Бой также включен");
            }
            
            // Эксклюзивные функции: выключаем Авто-Охоту, Авто-Травник, Авто-Приманку
            if (isAutoSkinEnabled()) {
                setAutoSkinEnabled(false);
                AppLog.d(TAG, "setAutoFishEnabled: Авто-Охота выключена");
            }
            if (isAutoCutEnabled()) {
                setAutoCutEnabled(false);
                AppLog.d(TAG, "setAutoFishEnabled: Авто-Травник выключен");
            }
            if (isAutoBaitEnabled()) {
                setAutoBaitEnabled(false);
                AppLog.d(TAG, "setAutoFishEnabled: Авто-Приманка выключена");
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
            
            // Очищаем NeverTimer, чтобы следующая попытка включить рыбалку не была заблокирована
            // старым cooldown'ом.
            AppVars.NeverTimer = 0L;
        }
        if (AppVars.Profile != null) {
            AppVars.Profile.save(context);
        }
        // Синхронизация QuickButton UI при programmatic stop/start (например, отключение из postfilter),
        // чтобы обводка/подсветка кнопки Авто-Рыбалка не зависала в старом состоянии.
        String quickUiMsg = "QUICK_UI_SYNC: request refresh from setAutoFishEnabled(" + enabled + ")";
        AppLog.d(TAG, TAG, quickUiMsg);
        requestQuickButtonsRefreshInternal("setAutoFishEnabled(" + enabled + ")");
        if (enabled && AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().runOnUiThread(() -> {
                try {
                    if (AppVars.mainActivity.get() == null || AppVars.mainActivity.get().getMainWebView() == null) {
                        return;
                    }
                    // Форсируем вход в озеро (go=10) для холодного старта рыбалки.
                    // go=10: озеро (холодный старт), не go=inf (последнее состояние, может быть бой).
                    String url = "http://neverlands.ru/main.php?get_id=56&act=10&go=10&af_bootstrap=1";
                    String fishVcode = SessionManager.getInstance().getValidVCodeForAction("autofish_bootstrap");
                    if (fishVcode != null && !fishVcode.isEmpty()) {
                        url += "&vcode=" + fishVcode;
                    } else {
                        AppLog.w(TAG, TAG, "AUTO_FISH_TRACE cold-start bootstrap has no vcode, continue with go=10");
                    }
                    url += "&ts=" + System.currentTimeMillis();
                    AppLog.d(TAG, "setAutoFishEnabled: bootstrap navigation to LAKE (go=10), url=" + url);
                    AppVars.mainActivity.get().getMainWebView().loadUrl(url);
                } catch (Exception e) {
                    AppLog.e(TAG, "setAutoFishEnabled: bootstrap navigation failed", e);
                }
            });
        }
        AppLog.d(TAG, "setAutoFishEnabled: " + enabled);
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
        AppLog.d(TAG, "restorePersistentAutoModesAfterLogin: autoFish=" + autoFish
                + ", autoFight=" + autoFight
                + ", autoTreasure=" + autoTreasure);

        requestCharacterSyncAfterLogin();
        requestClanWarsSyncAfterLogin();

        // ⚠️ FIX: Рыбалка и бой работают параллельно в озере
        // Враги нападают на рыбака - их нужно убивать чтобы продолжить рыбалку
        if (autoFish) {
            AppLog.d(TAG, TAG, "AUTO_FISH_TRACE restore after login: auto-fish owns cold start bootstrap");
            setAutoFishEnabled(true);
            if (autoFight) {
                restoreAutoFightRuntimeAfterLogin(true, false);
            }
            return;
        }

        AppVars.DoSearchBox = autoTreasure;
        if (!autoTreasure) {
            ExtMap.flushVisitedToDisk();
            AppLog.d(TAG, "restorePersistentAutoModesAfterLogin: keep visited cache, entries="
                    + AppVars.SearchBoxVisited.size());
        }

        restoreAutoFightRuntimeAfterLogin(autoFight, true);
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
     * - заранее прогреть кэш войн для `BossAuto` (проверки участия в войнах) без сетевого вызова в момент события;
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
                    AppLog.i(TAG, "AUTO_BOSS_TRACE: post-login wars sync ok, rows=" + size);
                }

                @Override
                public void onFailure(String message) {
                    AppLog.w(TAG, "AUTO_BOSS_TRACE: post-login wars sync failed: " + message);
                }
            });
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_BOSS_TRACE: post-login wars sync exception", e);
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
            AppLog.d(TAG, "AUTO_BLAZ_TRACE: skip " + CHARACTER_SYNC_LABEL
                    + " (cooldown), reason=" + reason + ", cooldownMs=" + cooldownMs);
            return;
        }
        lastCharacterSyncRequestedAtMs = now;

        Thread syncThread = new Thread(() -> {
            String sourceModule = (reason != null && reason.startsWith("after-login"))
                    ? "login_sync"
                    : "character_sync_auto_enable";
            AppLog.d(TAG, "INFO_API_TRACE stage=info_api_runtime_call, source_module="
                    + sourceModule + ", nick=" + nick + ", reason=" + reason);
            NeverApi.PinfoVitals vitals = NeverApi.getPinfoVitalsFromInfoApi(nick, sourceModule);
            if (vitals == null) {
                AppLog.w(TAG, "AUTO_BLAZ_TRACE: " + CHARACTER_SYNC_LABEL
                        + " failed (no vitals), nick=" + nick + ", reason=" + reason);
                return;
            }

            CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.updateFromPinfo(vitals, source);
            AppLog.i(TAG, "AUTO_BLAZ_TRACE: " + CHARACTER_SYNC_LABEL
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
            AppLog.w(TAG, "showCharacterSyncToast failed", e);
        }
    }

    private void restoreAutoFightRuntimeAfterLogin(boolean autoFightEnabledByProfile, boolean allowBootstrapReload) {
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
        AppLog.d(TAG, "restoreAutoFightRuntimeAfterLogin: runtime autoboi=" + AppVars.Autoboi
                + ", profileAutoFight=" + autoFightEnabledByProfile
                + ", allowBootstrapReload=" + allowBootstrapReload);

        // Установить флаг для принудительного запуска авто-боя при холодном старте.
        // Это нужно чтобы первый probe запустился несмотря на uiForegroundLikely=true.
        if (autoFightEnabledByProfile) {
            AppVars.ProbeForceNeedAutoboi = true;
            AppLog.d(TAG, "restoreAutoFightRuntimeAfterLogin: set ProbeForceNeedAutoboi flag for cold start override");
        }

        // Bootstrap после restore:
        // - запускается только при включенном авто-бое в профиле;
        // - не меняет сам флаг, а только инициирует загрузку боевого кадра для старта цикла.
        if (autoFightEnabledByProfile && !allowBootstrapReload) {
            AppLog.d(TAG, TAG, "AUTO_FISH_TRACE restore auto-fight runtime without bootstrap reload");
        }
        if (autoFightEnabledByProfile && allowBootstrapReload
                && AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().runOnUiThread(() -> {
                try {
                    if (AppVars.mainActivity.get() == null || AppVars.mainActivity.get().getMainWebView() == null) {
                        return;
                    }
                    AppVars.ContentMainPhp = null;
                    AppVars.LastBoiTimer = new java.util.Date();
                    AppLog.d(TAG, "restoreAutoFightRuntimeAfterLogin: forcing frame reload bootstrap");
                    String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_reload_probe=1";
                    String loginRestoreVcode = SessionManager.getInstance().getValidVCodeForAction("autofight_restore_login");
                    if (loginRestoreVcode != null && !loginRestoreVcode.isEmpty()) {
                        reloadUrl += "&vcode=" + loginRestoreVcode;
                    }
                    reloadUrl += "&ts=" + System.currentTimeMillis();
                    AppLog.d(TAG, "restoreAutoFightRuntimeAfterLogin: reload fight frame " + reloadUrl);
                    AppVars.mainActivity.get().getMainWebView().loadUrl(reloadUrl);
                } catch (Exception e) {
                    AppLog.e(TAG, "restoreAutoFightRuntimeAfterLogin: failed to reload fight frame", e);
                }
            });
        }
    }
    
    // === AUTO_BAIT (Авто-Приманка) ===
    
    // Авто-приманка: состояние.
    public boolean isAutoBaitEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_BAIT, "isAutoBaitEnabled")) {
            return false;
        }
        return prefs.getBoolean(KEY_PREFIX + "auto_bait", false);
    }
    
    // Переключение авто-приманки.
    public void toggleAutoBait() {
        boolean newState = !isAutoBaitEnabled();
        setAutoBaitEnabled(newState);
    }
    
    // Включение авто-приманки включает авто-бой и отключает несовместимые режимы.
    public void setAutoBaitEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_BAIT, enabled, "setAutoBaitEnabled")) {
            prefs.edit().putBoolean(KEY_PREFIX + "auto_bait", false).apply();
            return;
        }
        if (enabled) {
            // При включении: если Авто-Бой выключен - включаем его
            if (!isAutoFightEnabled()) {
                setAutoFightEnabled(true);
                AppLog.d(TAG, "setAutoBaitEnabled: Авто-Бой также включен");
            }
            // Эксклюзивные функции: выключаем Авто-Рыбалку, Авто-Охоту, Авто-Травник
            if (isAutoFishEnabled()) {
                setAutoFishEnabled(false);
                AppLog.d(TAG, "setAutoBaitEnabled: Авто-Рыбалка выключена");
            }
            if (isAutoSkinEnabled()) {
                setAutoSkinEnabled(false);
                AppLog.d(TAG, "setAutoBaitEnabled: Авто-Охота выключена");
            }
            if (isAutoCutEnabled()) {
                setAutoCutEnabled(false);
                AppLog.d(TAG, "setAutoBaitEnabled: Авто-Травник выключен");
            }
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_bait", enabled).apply();
        AppLog.d(TAG, "setAutoBaitEnabled: " + enabled);
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_bait");
        }
    }
    
    // === AUTO_SKIN (Авто-Охота) ===
    
    // Авто-охота: состояние.
    public boolean isAutoSkinEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_SKIN, "isAutoSkinEnabled")) {
            applyAutoSkinRuntimeFlags(false, "license_denied");
            return false;
        }
        if (AppVars.Profile != null) {
            boolean profileValue = AppVars.Profile.SkinAuto;
            boolean prefValue = prefs.getBoolean(KEY_AUTO_SKIN, false);
            if (profileValue != prefValue) {
                prefs.edit().putBoolean(KEY_AUTO_SKIN, profileValue).apply();
                applyAutoSkinRuntimeFlags(profileValue, "sync_from_profile");
                AppLog.d(TAG, "isAutoSkinEnabled: sync pref from profile SkinAuto=" + profileValue);
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
        if (rejectFeatureIfDenied(QuickActionType.AUTO_SKIN, enabled, "setAutoSkinEnabled")) {
            prefs.edit().putBoolean(KEY_AUTO_SKIN, false).apply();
            applyAutoSkinRuntimeFlags(false, "license_denied");
            if (AppVars.Profile != null) {
                AppVars.Profile.SkinAuto = false;
                AppVars.Profile.save(context);
            }
            return;
        }
        boolean autoFightWasEnabled = isAutoFightEnabled();
        if (enabled) {
            // При включении Авто-Охоты: если Авто-Бой выключен - включаем оба
            if (!autoFightWasEnabled) {
                setAutoFightEnabled(true);
                AppLog.d(TAG, "setAutoSkinEnabled: Авто-Бой также включен");
            }
            
            // Эксклюзивные функции: выключаем Авто-Рыбалку, Авто-Травник, Авто-Приманку
            if (isAutoFishEnabled()) {
                setAutoFishEnabled(false);
                AppLog.d(TAG, "setAutoSkinEnabled: Авто-Рыбалка выключена");
            }
            if (isAutoCutEnabled()) {
                setAutoCutEnabled(false);
                AppLog.d(TAG, "setAutoSkinEnabled: Авто-Травник выключен");
            }
            if (isAutoBaitEnabled()) {
                setAutoBaitEnabled(false);
                AppLog.d(TAG, "setAutoSkinEnabled: Авто-Приманка выключена");
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
        AppLog.d(TAG, "setAutoSkinEnabled: " + enabled);
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
            AppLog.d(TAG, "applyAutoSkinRuntimeFlags: enabled, reason=" + reason);
        } else {
            AppVars.AutoSkinCheckUm = false;
            AppVars.AutoSkinCheckRes = false;
            AppVars.AutoSkinCheckKnife = false;
            AppVars.AutoSkinArmedKnife = false;
            AppLog.d(TAG, "applyAutoSkinRuntimeFlags: disabled, reason=" + reason);
        }
    }

    /**
     * При включении AutoSkin сразу запрашивает страницу персонажа (`go=inf`),
     * чтобы цепочка `AutoSkinCheckUm/Res/Knife` стартовала без ручного перехода.
     */
    private void triggerAutoSkinCharacterCheck() {
        if (AppVars.mainActivity == null || AppVars.mainActivity.get() == null) {
            AppLog.w(TAG, "triggerAutoSkinCharacterCheck: mainActivity is null");
            return;
        }
        AppVars.mainActivity.get().runOnUiThread(() -> {
            try {
                String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf";
                String vcode = SessionManager.getInstance().getValidVCodeForAction("auto_skin_check");
                if (vcode != null) {
                    reloadUrl += "&vcode=" + vcode;
                } else {
                    AppLog.w("vcode_migration", TAG, "[VCode_MISSING] getValidVCodeForAction returned null for auto_skin_check");
                }
                reloadUrl += "&ts=" + System.currentTimeMillis();
                AppLog.d(TAG, "triggerAutoSkinCharacterCheck: load " + reloadUrl);
                AppVars.mainActivity.get().getMainWebView().loadUrl(reloadUrl);
            } catch (Exception e) {
                AppLog.e(TAG, "triggerAutoSkinCharacterCheck: failed", e);
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
        AppLog.d(TAG, "syncAutoSkinWithProfileIfPresent: SkinAuto=" + profileValue);
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
        AppLog.d(TAG, "syncAutoFightWithProfileIfPresent: LezDoAutoboi=" + profileValue);
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
        AppLog.d(TAG, "syncAutoTreasureWithProfileIfPresent: AutoDig=" + profileValue);
    }
    
    // === AUTO_ATTACK (Авто-Нападение) ===
    
    // Авто-нападение: состояние.
    public boolean isAutoAttackEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_ATTACK, "isAutoAttackEnabled")) {
            AppVars.AutoAttackToolId = 0;
            return false;
        }
        return getAutoAttackToolId() != 0;
    }
    
    // Переключение авто-нападения.
    public void toggleAutoAttack() {
        boolean newState = !isAutoAttackEnabled();
        setAutoAttackEnabled(newState);
    }
    
    // Включение/выключение авто-нападения.
    public void setAutoAttackEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_ATTACK, enabled, "setAutoAttackEnabled")) {
            setAutoAttackToolId(0);
            return;
        }
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
        AppLog.d(TAG, "setAutoAttackEnabled(wrapper): " + enabled + ", toolId=" + getAutoAttackToolId());
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
        if (!isFeatureAvailable(QuickActionType.AUTO_ATTACK, "getAutoAttackToolId")) {
            AppVars.AutoAttackToolId = 0;
            return 0;
        }
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
        if (safeToolId > 0 && rejectFeatureIfDenied(QuickActionType.AUTO_ATTACK, true, "setAutoAttackToolId")) {
            safeToolId = 0;
        }
        prefs.edit().putInt(KEY_AUTO_ATTACK_TOOL_ID, safeToolId).apply();
        AppVars.AutoAttackToolId = safeToolId;
        if (safeToolId > 0) {
            rememberLastNonZeroAutoAttackToolId(safeToolId);
            if (!isLocationTrackingEnabled()) {
                setLocationTrackingEnabled(true);
            }
            requestCharacterSyncForAutoFunctionEnable("auto_attack_tool_" + safeToolId);
        }
        AppLog.d(TAG, "setAutoAttackToolId: " + safeToolId);
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
            AppLog.d(TAG, "migrateLegacyAutoAttackFlagIfNeeded: legacy auto_attack=true migrated to toolId=1");
        } else if (toolId > 0) {
            rememberLastNonZeroAutoAttackToolId(toolId);
        }
    }
    
    // === AUTO_INVISIBLE (Авто-Невид) ===
    
    // Авто-невид: состояние.
    public boolean isAutoInvisibleEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_INVISIBLE, "isAutoInvisibleEnabled")) {
            return false;
        }
        return prefs.getBoolean(KEY_PREFIX + "auto_invisible", false);
    }
    
    // Переключение авто-невида.
    public void toggleAutoInvisible() {
        boolean newState = !isAutoInvisibleEnabled();
        setAutoInvisibleEnabled(newState);
    }
    
    // Включение/выключение авто-невида.
    public void setAutoInvisibleEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_INVISIBLE, enabled, "setAutoInvisibleEnabled")) {
            prefs.edit().putBoolean(KEY_PREFIX + "auto_invisible", false).apply();
            return;
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_invisible", enabled).apply();
        AppLog.d(TAG, "setAutoInvisibleEnabled: " + enabled);
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_invisible");
        }
    }
    
    // === LOCATION_TRACKING (Слежение за локацией) ===
    
    // Слежение за локацией: состояние.
    public boolean isLocationTrackingEnabled() {
        if (!isFeatureAvailable(QuickActionType.LOCATION_TRACKING, "isLocationTrackingEnabled")) {
            AppVars.DoShowWalkers = false;
            return false;
        }
        return prefs.getBoolean(KEY_LOCATION_TRACKING, false);
    }
    
    // Переключение слежения за локацией.
    public void toggleLocationTracking() {
        boolean newState = !isLocationTrackingEnabled();
        setLocationTrackingEnabled(newState);
    }
    
    // Включение/выключение слежения за локацией.
    public void setLocationTrackingEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.LOCATION_TRACKING, enabled, "setLocationTrackingEnabled")) {
            prefs.edit().putBoolean(KEY_LOCATION_TRACKING, false).apply();
            AppVars.DoShowWalkers = false;
            return;
        }
        prefs.edit().putBoolean(KEY_LOCATION_TRACKING, enabled).apply();
        AppVars.DoShowWalkers = enabled;
        if (enabled) {
            AppVars.myCoordOld = "";
            AppVars.myLocOld = "";
            AppVars.myWalkers1 = "";
            AppVars.myWalkers2 = "";
        }
        AppLog.d(TAG, "setLocationTrackingEnabled: " + enabled);
        syncBackgroundService("setLocationTrackingEnabled(" + enabled + ")");

        // При включении сразу запрашиваем room-list, чтобы RoomManager получил тик немедленно.
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            ru.neverlands.anclient.MainActivity activity = AppVars.mainActivity.get();
            activity.runOnUiThread(() -> {
                try {
                    activity.onWalkersPollingConfigChanged();
                    if (enabled) {
                        activity.requestRoomUsersRefreshSoon();
                    }
                } catch (Exception e) {
                    AppLog.w(TAG, "setLocationTrackingEnabled: room users refresh trigger failed", e);
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
        AppLog.d(TAG, "setWalkersPollIntervalSec: " + safe);
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            ru.neverlands.anclient.MainActivity activity = AppVars.mainActivity.get();
            activity.runOnUiThread(() -> {
                try {
                    activity.onWalkersPollingConfigChanged();
                } catch (Exception e) {
                    AppLog.w(TAG, "setWalkersPollIntervalSec: polling reschedule failed", e);
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
        if (!isFeatureAvailable(QuickActionType.AUTO_COMPASS, "isAutoCompassEnabled")) {
            return false;
        }
        return compasAuto.isAutoCompassEnabled();
    }

    // Переключение авто-компаса.
    public void toggleAutoCompass() {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_COMPASS, true, "toggleAutoCompass")) {
            compasAuto.setAutoCompassEnabled(false);
            return;
        }
        compasAuto.toggleAutoCompass();
    }

    // Включение/выключение авто-компаса.
    public void setAutoCompassEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_COMPASS, enabled, "setAutoCompassEnabled")) {
            compasAuto.setAutoCompassEnabled(false);
            return;
        }
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
        if (rejectFeatureIfDenied(QuickActionType.AUTO_COMPASS, true, "startSettingsCompassTargetSearch")) {
            compasAuto.setAutoCompassEnabled(false);
            return;
        }
        compasAuto.startSettingsCompassTargetSearch(nick, source);
    }

    /**
     * Публичный вход в цикл авто-компаса (вызывается из foreground-service/таймера).
     * Делегирует в внутренний метод с `forceNow=false`.
     */
    public void tickAutoCompass() {
        if (!isFeatureAvailable(QuickActionType.AUTO_COMPASS, "tickAutoCompass")) {
            return;
        }
        compasAuto.tickAutoCompass();
    }

    public void onRoomUsersUpdated(List<String> roomNicks, String roomLocationName) {
        if (isFeatureAvailable(QuickActionType.AUTO_COMPASS, "onRoomUsersUpdated:auto_compass")) {
            compasAuto.onRoomUsersUpdated(roomNicks, roomLocationName);
        }
        if (isFeatureAvailable(QuickActionType.AUTO_BOSS, "onRoomUsersUpdated:auto_boss")) {
            bossAuto.onRoomUsersUpdated(roomNicks, roomLocationName);
        }
    }

    // === AUTO_BOSS (Авто-Боссы) ===

    // === AUTO_BOSS (Авто-Боссы) ===
    // Публичный фасад: UI и сервисы вызывают методы этого блока, а детальная
    // state-machine логика остаётся инкапсулированной в BossAuto.
    public boolean isAutoBossEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_BOSS, "isAutoBossEnabled")) {
            return false;
        }
        return bossAuto.isAutoBossEnabled();
    }

    public void toggleAutoBoss() {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_BOSS, true, "toggleAutoBoss")) {
            bossAuto.disableForLicenseSync("license_denied:toggleAutoBoss");
            return;
        }
        bossAuto.toggleAutoBoss();
    }

    public void setAutoBossEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_BOSS, enabled, "setAutoBossEnabled")) {
            bossAuto.disableForLicenseSync("license_denied:setAutoBossEnabled");
            return;
        }
        bossAuto.setAutoBossEnabled(enabled);
    }

    public void onIncomingChatMessage(String messageHtml) {
        if (!isFeatureAvailable(QuickActionType.AUTO_BOSS, "onIncomingChatMessage:auto_boss")) {
            bossAuto.disableForLicenseSync("license_denied:onIncomingChatMessage");
            return;
        }
        bossAuto.onIncomingChatMessage(messageHtml);
    }

    public void tickAutoBoss() {
        if (!isFeatureAvailable(QuickActionType.AUTO_BOSS, "tickAutoBoss")) {
            return;
        }
        bossAuto.tickAutoBoss();
    }

    public boolean isAutoBossAskTargetEnabled() {
        return bossAuto.isAutoBossAskTargetEnabled();
    }

    public void setAutoBossAskTargetEnabled(boolean enabled) {
        bossAuto.setAutoBossAskTargetEnabled(enabled);
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

    /**
     * Сохраняет настройку клан-уведомлений для Авто-Босса.
     * Важно: метод только меняет конфиг, но сам сообщения в чат не отправляет.
     */

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

    /**
     * Runtime-флаг: нужно ли временно приостановить map-rebuild по pinfo
     * (и связанные задержки карты), пока Авто-Босс ищет цель/заходит в бой.
     */
    public boolean shouldPauseMapRebuildFromPinfoByAutoBoss() {
        return bossAuto.shouldPauseMapRebuildFromPinfo();
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
            AppLog.w(TAG, "requestQuickButtonsRefreshInternal failed, reason=" + reason, e);
        }
    }

    private void syncBackgroundService(String reason) {
        try {
            AutoModeForegroundService.syncServiceState(context, reason);
            AppLog.d(TAG, BG_TRACE_PREFIX + " syncBackgroundService: " + reason);
        } catch (Exception e) {
            AppLog.w(TAG, BG_TRACE_PREFIX + " syncBackgroundService failed: " + reason, e);
        }
    }
    
    // === AUTO_DETECT (Авто-Обнаружение) ===
    
    // Авто-обнаружение: состояние.
    public boolean isAutoDetectEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_DETECT, "isAutoDetectEnabled")) {
            return false;
        }
        return prefs.getBoolean(KEY_PREFIX + "auto_detect", false);
    }
    
    // Переключение авто-обнаружения.
    public void toggleAutoDetect() {
        boolean newState = !isAutoDetectEnabled();
        setAutoDetectEnabled(newState);
    }
    
    // Включение/выключение авто-обнаружения.
    public void setAutoDetectEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_DETECT, enabled, "setAutoDetectEnabled")) {
            prefs.edit().putBoolean(KEY_PREFIX + "auto_detect", false).apply();
            return;
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_detect", enabled).apply();
        AppLog.d(TAG, "setAutoDetectEnabled: " + enabled);
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_detect");
        }
    }
    
    // === AUTO_SUMMON (Авто-Тотем) ===
    
    // Авто-тотем: состояние.
    public boolean isAutoSummonEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_SUMMON, "isAutoSummonEnabled")) {
            return false;
        }
        return prefs.getBoolean(KEY_PREFIX + "auto_summon", false);
    }
    
    // Переключение авто-тотема.
    public void toggleAutoSummon() {
        boolean newState = !isAutoSummonEnabled();
        setAutoSummonEnabled(newState);
    }
    
    // Включение/выключение авто-тотема.
    public void setAutoSummonEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_SUMMON, enabled, "setAutoSummonEnabled")) {
            prefs.edit().putBoolean(KEY_PREFIX + "auto_summon", false).apply();
            return;
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_summon", enabled).apply();
        AppLog.d(TAG, "setAutoSummonEnabled: " + enabled);
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_summon");
        }
    }
    
    // === AUTO_CURE (Авто-Лечение - DoAutoCure) ===
    
    // Авто-лечение: состояние.
    public boolean isAutoCureEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_CURE, "isAutoCureEnabled")) {
            return false;
        }
        return prefs.getBoolean(KEY_PREFIX + "auto_cure", false);
    }
    
    // Переключение авто-лечения.
    public void toggleAutoCure() {
        boolean newState = !isAutoCureEnabled();
        setAutoCureEnabled(newState);
    }
    
    // Включение/выключение авто-лечения.
    public void setAutoCureEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_CURE, enabled, "setAutoCureEnabled")) {
            prefs.edit().putBoolean(KEY_PREFIX + "auto_cure", false).apply();
            return;
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_cure", enabled).apply();
        AppLog.d(TAG, "setAutoCureEnabled: " + enabled);
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
            AppLog.w(TAG, "getDefaultBoolean failed: key=" + key, e);
            return fallback;
        }
    }

    private void putDefaultBoolean(String key, boolean value) {
        try {
            defaultPrefs().edit().putBoolean(key, value).apply();
        } catch (Exception e) {
            AppLog.w(TAG, "putDefaultBoolean failed: key=" + key + ", value=" + value, e);
        }
    }

    private String getDefaultString(String key, String fallback) {
        try {
            String value = defaultPrefs().getString(key, fallback);
            return value == null ? fallback : value;
        } catch (Exception e) {
            AppLog.w(TAG, "getDefaultString failed: key=" + key, e);
            return fallback;
        }
    }

    private void putDefaultString(String key, String value) {
        try {
            defaultPrefs().edit().putString(key, value).apply();
        } catch (Exception e) {
            AppLog.w(TAG, "putDefaultString failed: key=" + key + ", value=" + value, e);
        }
    }

    private int getDefaultInt(String key, int fallback) {
        try {
            return defaultPrefs().getInt(key, fallback);
        } catch (Exception e) {
            AppLog.w(TAG, "getDefaultInt failed: key=" + key, e);
            return fallback;
        }
    }

    private void putDefaultInt(String key, int value) {
        try {
            defaultPrefs().edit().putInt(key, value).apply();
        } catch (Exception e) {
            AppLog.w(TAG, "putDefaultInt failed: key=" + key + ", value=" + value, e);
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
        if (!isFeatureAvailable(QuickActionType.AUTO_DRINK, "isAutoDrinkEnabled")) {
            return false;
        }
        return prefs.getBoolean(KEY_PREFIX + "auto_drink", false);
    }
    
    // Переключение авто-питья.
    public void toggleAutoDrink() {
        boolean newState = !isAutoDrinkEnabled();
        setAutoDrinkEnabled(newState);
    }
    
    // Включение/выключение авто-питья.
    public void setAutoDrinkEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_DRINK, enabled, "setAutoDrinkEnabled")) {
            prefs.edit().putBoolean(KEY_PREFIX + "auto_drink", false).apply();
            return;
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_drink", enabled).apply();
        AppLog.d(TAG, "setAutoDrinkEnabled: " + enabled);
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_drink");
        }
    }

    // === AUTO_TREASURE (Авто-Клад / DoSearchBox) ===

    // Возвращает текущее состояние "Авто-Клад".
    public boolean isAutoTreasureEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_TREASURE, "isAutoTreasureEnabled")) {
            AppVars.DoSearchBox = false;
            return false;
        }
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
        if (rejectFeatureIfDenied(QuickActionType.AUTO_TREASURE, enabled, "setAutoTreasureEnabled")) {
            prefs.edit().putBoolean(KEY_AUTO_TREASURE, false).apply();
            AppVars.DoSearchBox = false;
            if (AppVars.Profile != null) {
                AppVars.Profile.AutoDig = false;
                AppVars.Profile.save(context);
            }
            return;
        }
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
            AppLog.d(TAG, "setAutoTreasureEnabled: keep visited cache on disable, entries="
                    + AppVars.SearchBoxVisited.size());
        }

        AppLog.d(TAG, "setAutoTreasureEnabled: " + enabled);
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_treasure");
        }
        if (enabled && AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().runOnUiThread(() -> {
                try {
                    if (AppVars.mainActivity.get() == null || AppVars.mainActivity.get().getMainWebView() == null) {
                        return;
                    }
                    String reloadUrl = "http://neverlands.ru/main.php?an_search_box_bootstrap=1&r="
                            + System.currentTimeMillis();
                    AppVars.mainActivity.get().getMainWebView().loadUrl(reloadUrl);
                    AppLog.d(TAG, "setAutoTreasureEnabled: bootstrap reload " + reloadUrl);
                } catch (Exception e) {
                    AppLog.e(TAG, "setAutoTreasureEnabled: bootstrap reload failed", e);
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

    /**
     * Сохраняет флаг "Тщательная проверка соседних клеток" для Auto-Клада.
     *
     * Зависимости:
     * - `QuickButtonsPanel.showAutoTreasureSettingsDialog()` — запись значения из UI.
     * - `MapAjax.findNextDestForBox(...)` — чтение значения и выбор detour-клеток.
     * - `PreferenceManager.getDefaultSharedPreferences(...)` — единое хранилище с другими
     *   настройками карты/автофункций.
     */
    public void setAutoTreasureThoroughNeighborCheckEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_CHECK, enabled);
    }

    /**
     * "Умная система генерации" для Авто-Клада.
     *
     * Зависимости:
     * - `MapAjax.findNextDestForBox(...)` использует настройку, чтобы не делать повторные
     *   проходы по недавно проверенным клеткам (анти-спам по Блажу) и включать отложенный
     *   возврат к уже посещённым клеткам.
     * - Хранение в default SharedPreferences, чтобы настройка была доступна и из UI, и из postfilter.
     */
    public boolean isAutoTreasureSmartGenerationEnabled() {
        return getDefaultBoolean(PREF_AUTO_TREASURE_SMART_GENERATION, false);
    }

    /**
     * Сохраняет флаг "Умная система генерации" для Auto-Клада.
     *
     * Зависимости:
     * - `QuickButtonsPanel.showAutoTreasureSettingsDialog()` — запись флага из чекбокса.
     * - `MapAjax.findBaseSearchBoxDestination(...)` — фильтрация fallback-целей по возрасту
     *   метки посещения (анти-повтор свежих клеток).
     */
    public void setAutoTreasureSmartGenerationEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_TREASURE_SMART_GENERATION, enabled);
    }

    /**
     * Радиус "Дообхода" для Auto-Клада (манхэттен-радиус от текущей клетки).
     *
     * Допустимый диапазон:
     * - минимум: 1;
     * - максимум: 3.
     *
     * Зависимости:
     * - `QuickButtonsPanel.showAutoTreasureSettingsDialog()` задаёт значение из UI;
     * - `MapAjax.findThoroughNeighborCandidates(...)` читает это значение при отборе соседних клеток.
     */
    public int getAutoTreasureThoroughNeighborRadius() {
        int value = getDefaultInt(
                PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_RADIUS,
                AUTO_TREASURE_THOROUGH_RADIUS_DEFAULT
        );
        return Math.max(
                AUTO_TREASURE_THOROUGH_RADIUS_MIN,
                Math.min(AUTO_TREASURE_THOROUGH_RADIUS_MAX, value)
        );
    }

    /**
     * Сохраняет радиус "Дообхода" (манхэттен) для Auto-Клада.
     *
     * Валидация:
     * - любое входное значение принудительно ограничивается диапазоном [1..3].
     */
    public void setAutoTreasureThoroughNeighborRadius(int radius) {
        int normalized = Math.max(
                AUTO_TREASURE_THOROUGH_RADIUS_MIN,
                Math.min(AUTO_TREASURE_THOROUGH_RADIUS_MAX, radius)
        );
        putDefaultInt(PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_RADIUS, normalized);
    }

    /**
     * Минимальный возраст visited-маркера (в минутах) для detour-кандидатов "Тщательного обхода".
     * Кандидат с возрастом меньше этого порога исключается из дообхода.
     */
    public int getAutoTreasureThoroughNeighborMinAgeMinutes() {
        int value = getDefaultInt(
                PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_MIN_AGE_MINUTES,
                AUTO_TREASURE_THOROUGH_MIN_AGE_MINUTES_DEFAULT
        );
        return Math.max(
                AUTO_TREASURE_THOROUGH_MIN_AGE_MINUTES_MIN,
                Math.min(AUTO_TREASURE_THOROUGH_MIN_AGE_MINUTES_MAX, value)
        );
    }

    /**
     * Сохраняет минимальный возраст visited-маркера (в минутах) для detour-кандидатов.
     * Любое входное значение принудительно нормализуется в диапазон [1..1440].
     */
    public void setAutoTreasureThoroughNeighborMinAgeMinutes(int minutes) {
        int normalized = Math.max(
                AUTO_TREASURE_THOROUGH_MIN_AGE_MINUTES_MIN,
                Math.min(AUTO_TREASURE_THOROUGH_MIN_AGE_MINUTES_MAX, minutes)
        );
        putDefaultInt(PREF_AUTO_TREASURE_THOROUGH_NEIGHBOR_MIN_AGE_MINUTES, normalized);
    }

    /**
     * Флаг вывода в чат сообщений о перестроении маршрута дообхода.
     *
     * Зависимости:
     * - `QuickButtonsPanel.showAutoTreasureSettingsDialog()` — настройка чекбокса;
     * - `MapAjax.postAutoTreasureRouteRebuildToChat(...)` — фактический вывод системного сообщения.
     */
    public boolean isAutoTreasureDetourChatEnabled() {
        return getDefaultBoolean(PREF_AUTO_TREASURE_DETOUR_CHAT_ENABLED, false);
    }

    /**
     * Сохраняет флаг вывода в чат сообщений о перестроении маршрута дообхода.
     */
    public void setAutoTreasureDetourChatEnabled(boolean enabled) {
        putDefaultBoolean(PREF_AUTO_TREASURE_DETOUR_CHAT_ENABLED, enabled);
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
        if (!isFeatureAvailable(QuickActionType.AUTO_MOVING, "isAutoMovingEnabled")) {
            return false;
        }
        return AppVars.AutoMoving;
    }

    // Непосредственное включение/выключение флага навигатора (без выбора маршрута).
    // Для полноценного запуска используйте startAutoMoving(destination).
    public void setAutoMovingEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_MOVING, enabled, "setAutoMovingEnabled")) {
            AppVars.AutoMoving = false;
            return;
        }
        AppVars.AutoMoving = enabled;
        AppLog.d(TAG, "setAutoMovingEnabled: " + enabled);
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
        if (rejectFeatureIfDenied(QuickActionType.AUTO_MOVING, true, "startAutoMoving")) {
            AppVars.AutoMoving = false;
            return;
        }
        if (destination == null || destination.isEmpty()) {
            AppLog.w(TAG, "startAutoMoving: destination is empty");
            return;
        }
        AppVars.AutoMovingDestinaton = destination;
        AppVars.AutoMovingNextJump = null;
        AppVars.AutoMovingJumps = 0;
        AppVars.AutoMovingCityGate = ru.neverlands.anclient.model.CityGateType.None;
        String mapLocation = (AppVars.Profile != null) ? AppVars.Profile.MapLocation : null;
        if (mapLocation != null && !mapLocation.isEmpty()) {
            MapPath path = new MapPath(mapLocation, java.util.Collections.singletonList(destination));
            AppVars.AutoMovingMapPath = path;
            AppVars.AutoMovingNextJump = path.nextJump;
            AppVars.AutoMovingJumps = path.jumps;
            AppVars.AutoMovingCityGate = path.cityGate;
            AppLog.d(TAG, "startAutoMoving: destination=" + destination + " pathExists=" + path.pathExists + " jumps=" + path.jumps);
        } else {
            AppVars.AutoMovingMapPath = null;
            AppLog.d(TAG, "startAutoMoving: destination=" + destination + " (MapLocation unknown, path will be built lazily)");
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
        AppVars.AutoMovingCityGate = ru.neverlands.anclient.model.CityGateType.None;
        AppLog.d(TAG, "stopAutoMoving");
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
            AppLog.d(TAG, "startAutoMoving: bootstrap skipped (activity is null)");
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                if (activity.binding == null || activity.binding.appBarMain == null
                        || activity.binding.appBarMain.contentMain == null
                        || activity.binding.appBarMain.contentMain.webView == null) {
                    AppLog.d(TAG, "startAutoMoving: bootstrap skipped (webView is null)");
                    return;
                }
                String vcode = SessionManager.getInstance().getValidVCodeForAction("auto_nav_bootstrap");
                String url;
                if (vcode != null && !vcode.isEmpty()) {
                    url = "http://neverlands.ru/main.php?get_id=56&act=10&go=ret&vcode="
                            + vcode
                            + "&ab_nav_bootstrap=1&r="
                            + System.currentTimeMillis();
                    AppLog.i("vcode_migration", TAG, "[AUTO_NAV_BOOTSTRAP] VCode obtained from SessionManager, URL prepared");
                } else {
                    // Fallback: если vcode еще не извлечен, запускаем старый bootstrap через go=inf.
                    url = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_nav_bootstrap=1&r="
                            + System.currentTimeMillis();
                }
                activity.binding.appBarMain.contentMain.webView.loadUrl(url);
                AppLog.d(TAG, "startAutoMoving: bootstrap navigation to " + url);
            } catch (Exception e) {
                AppLog.e(TAG, "startAutoMoving: bootstrap navigation failed", e);
            }
        });
    }
    
    // === AUTO_CUT (Авто-Травник) ===
    
    // Авто-травник: состояние.
    public boolean isAutoCutEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_CUT, "isAutoCutEnabled")) {
            return false;
        }
        return prefs.getBoolean(KEY_PREFIX + "auto_cut", false);
    }
    
    // Переключение авто-травника.
    public void toggleAutoCut() {
        boolean newState = !isAutoCutEnabled();
        setAutoCutEnabled(newState);
    }
    
    /**
     * Включает/выключает premium-функцию Авто-Травник.
     *
     * Переменные и зависимости:
     * - `enabled` — желаемое persisted-состояние `KEY_PREFIX + "auto_cut"`;
     * - `rejectFeatureIfDenied(AUTO_CUT, ...)` — первый license guard, запрещает включение
     *   без individual `full` или custom grant `auto_cut`;
     * - `AutoCutManager.onAutoCutEnabled(...)` — runtime bootstrap: проверка серпа,
     *   сброс cleanup-флагов и старт маршрута по CSV-клеткам;
     * - конфликтующие навигационные владельцы (`AutoFish`, `AutoSkin`, `AutoBait`, `AutoTreasure`)
     *   выключаются через существующие setter-ы, чтобы не создавать второй маршрутный контур;
     * - `requestCharacterSyncForAutoFunctionEnable("auto_cut")` поднимает main.php-контекст,
     *   где `AutoCutHandler` сможет проверить экипировку и вернуться на карту.
     */
    public void setAutoCutEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_CUT, enabled, "setAutoCutEnabled")) {
            prefs.edit().putBoolean(KEY_PREFIX + "auto_cut", false).apply();
            return;
        }
        if (enabled) {
            // При включении: если Авто-Бой выключен - включаем его
            if (!isAutoFightEnabled()) {
                setAutoFightEnabled(true);
                AppLog.d(TAG, "setAutoCutEnabled: Авто-Бой также включен");
            }
            // Эксклюзивные функции: выключаем Авто-Рыбалку, Авто-Охоту, Авто-Приманку
            if (isAutoFishEnabled()) {
                setAutoFishEnabled(false);
                AppLog.d(TAG, "setAutoCutEnabled: Авто-Рыбалка выключена");
            }
            if (isAutoSkinEnabled()) {
                setAutoSkinEnabled(false);
                AppLog.d(TAG, "setAutoCutEnabled: Авто-Охота выключена");
            }
            if (isAutoBaitEnabled()) {
                setAutoBaitEnabled(false);
                AppLog.d(TAG, "setAutoCutEnabled: Авто-Приманка выключена");
            }
            if (isAutoTreasureEnabled()) {
                setAutoTreasureEnabled(false);
                AppLog.d(AutoCutManager.TRACE_CHAIN, TAG, "setAutoCutEnabled: Авто-Клад выключен как конфликтующая навигация");
            }
            int selectedHerbs = AutoCutManager.getInstance(context).getSelectedHerbCount();
            if (selectedHerbs == 0) {
                AppLog.w(AutoCutManager.TRACE_CHAIN, TAG,
                        "setAutoCutEnabled: enabled without selected herbs");
            }
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_cut", enabled).apply();
        if (enabled) {
            AutoCutManager.getInstance(context).onAutoCutEnabled(this);
        } else {
            AutoCutManager.getInstance(context).onAutoCutDisabled();
        }
        AppLog.d(TAG, "setAutoCutEnabled: " + enabled);
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_cut");
        }
    }
    
    // === AUTO_REFRESH (Авто-Обновление) ===
    
    // Авто-обновление: состояние.
    public boolean isAutoRefreshEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_REFRESH, "isAutoRefreshEnabled")) {
            return false;
        }
        return prefs.getBoolean(KEY_PREFIX + "auto_refresh", false);
    }
    
    // Переключение авто-обновления.
    public void toggleAutoRefresh() {
        boolean newState = !isAutoRefreshEnabled();
        setAutoRefreshEnabled(newState);
    }
    
    // Включение/выключение авто-обновления.
    public void setAutoRefreshEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_REFRESH, enabled, "setAutoRefreshEnabled")) {
            prefs.edit().putBoolean(KEY_PREFIX + "auto_refresh", false).apply();
            return;
        }
        prefs.edit().putBoolean(KEY_PREFIX + "auto_refresh", enabled).apply();
        AppLog.d(TAG, "setAutoRefreshEnabled: " + enabled);
        if (enabled) {
            requestCharacterSyncForAutoFunctionEnable("auto_refresh");
        }
    }

    public String getAutoCutCellsCsv() {
        return AutoCutManager.getInstance(context).getCellsCsv();
    }

    public void setAutoCutCellsCsv(String csv) {
        AutoCutManager.getInstance(context).setCellsCsv(csv);
    }

    public boolean isAutoCutWriteChatEnabled() {
        return AutoCutManager.getInstance(context).isWriteChatEnabled();
    }

    public void setAutoCutWriteChatEnabled(boolean enabled) {
        AutoCutManager.getInstance(context).setWriteChatEnabled(enabled);
    }

    public boolean isAutoCutCleanupEnabled() {
        return AutoCutManager.getInstance(context).isCleanupEnabled();
    }

    public void setAutoCutCleanupEnabled(boolean enabled) {
        AutoCutManager.getInstance(context).setCleanupEnabled(enabled);
    }

    public List<AutoCutManager.AutoCutHerb> getAutoCutHerbs() {
        return AutoCutManager.getInstance(context).getHerbs();
    }

    public void setAutoCutHerbSelections(Set<String> selectedKeys) {
        AutoCutManager.getInstance(context).setHerbSelections(selectedKeys);
    }

    public void updateAutoCutHerbMeta(String key, int skill, int growthMinutes, String group) {
        AutoCutManager.getInstance(context).updateHerbMeta(key, skill, growthMinutes, group);
    }

    public String[] getAutoCutAvailableSickleNames() {
        return AutoCutManager.getInstance(context).getAvailableSickleNames();
    }

    public List<String> getAutoCutEnabledSickleNames() {
        return AutoCutManager.getInstance(context).getEnabledSickleNames();
    }

    public void setAutoCutEnabledSickleNames(Set<String> selectedNames) {
        AutoCutManager.getInstance(context).setEnabledSickleNames(selectedNames);
    }

    public String getAutoCutShiftScheduleText() {
        return AutoCutManager.getInstance(context).getShiftScheduleText();
    }

    public boolean setAutoCutShiftScheduleText(String text) {
        return AutoCutManager.getInstance(context).setShiftScheduleText(text);
    }

    public void resetAutoCutShiftScheduleToDefault() {
        AutoCutManager.getInstance(context).resetShiftScheduleToDefault();
    }

    public int getAutoCutSelectedHerbCount() {
        return AutoCutManager.getInstance(context).getSelectedHerbCount();
    }

    // === ANTI_CAPTCHA ===
    // Назначение:
    // - хранить локальный ON/OFF и настройки ImageToTextTask для сервиса anti-captcha.com;
    // - отдавать MainActivity готовый immutable Config для текущего popup captcha;
    // - не отправлять запросы к внешнему сервису без активного licensed feature `anti_captcha`.
    //
    // Зависимости:
    // - QuickActionType.AUTO_CAPTCHA / LicenseFeature.FEATURE_ANTI_CAPTCHA — feature key в profile.reg;
    // - MainActivity.maybeStartAntiCaptchaForActiveChallenge(...) — единственная точка runtime запуска;
    // - AntiCaptchaManager.Config — DTO параметров createTask/getTaskResult;
    // - LicenseRuntime.disableUnavailableFeatures(...) — сброс флага при истечении временного grant.

    public boolean isAntiCaptchaEnabled() {
        if (!isFeatureAvailable(QuickActionType.AUTO_CAPTCHA, "isAntiCaptchaEnabled")) {
            disableAntiCaptchaForLicenseLoss("isAntiCaptchaEnabled");
            return false;
        }
        return prefs.getBoolean(KEY_ANTI_CAPTCHA, false);
    }

    public void toggleAntiCaptcha() {
        setAntiCaptchaEnabled(!isAntiCaptchaEnabled());
    }

    public void setAntiCaptchaEnabled(boolean enabled) {
        if (rejectFeatureIfDenied(QuickActionType.AUTO_CAPTCHA, enabled, "setAntiCaptchaEnabled")) {
            disableAntiCaptchaForLicenseLoss("setAntiCaptchaEnabled_denied");
            return;
        }
        prefs.edit().putBoolean(KEY_ANTI_CAPTCHA, enabled).apply();
        AppLog.d(TAG, "setAntiCaptchaEnabled: " + enabled);
    }

    private void disableAntiCaptchaForLicenseLoss(String source) {
        if (!prefs.getBoolean(KEY_ANTI_CAPTCHA, false)) {
            return;
        }
        // Этот сброс нужен именно для временных full/custom grants: после окончания expiresAt
        // флаг не должен оставаться "включенным" в SharedPreferences и самовосстанавливаться
        // при следующем popup captcha. Ручной API key при этом сохраняется, чтобы после
        // продления лицензии пользователь не вводил его заново.
        prefs.edit().putBoolean(KEY_ANTI_CAPTCHA, false).apply();
        AppLog.w("ANCLIENT_LICENSE", TAG, "LICENSE_FEATURE_FLAG_DISABLED: source=" + source
                + ", action=" + QuickActionType.AUTO_CAPTCHA.getActionKey());
    }

    public String getAntiCaptchaApiKey() {
        return prefs.getString(PREF_ANTI_CAPTCHA_API_KEY, "");
    }

    public void setAntiCaptchaApiKey(String value) {
        prefs.edit().putString(PREF_ANTI_CAPTCHA_API_KEY, value == null ? "" : value.trim()).apply();
    }

    public boolean isAntiCaptchaPhrase() {
        return prefs.getBoolean(PREF_ANTI_CAPTCHA_PHRASE, false);
    }

    public void setAntiCaptchaPhrase(boolean value) {
        prefs.edit().putBoolean(PREF_ANTI_CAPTCHA_PHRASE, value).apply();
    }

    public boolean isAntiCaptchaCaseSensitive() {
        return prefs.getBoolean(PREF_ANTI_CAPTCHA_CASE, false);
    }

    public void setAntiCaptchaCaseSensitive(boolean value) {
        prefs.edit().putBoolean(PREF_ANTI_CAPTCHA_CASE, value).apply();
    }

    public int getAntiCaptchaNumeric() {
        return clampInt(prefs.getInt(PREF_ANTI_CAPTCHA_NUMERIC, ANTI_CAPTCHA_NUMERIC_NUMBERS_ONLY), 0, 2);
    }

    public void setAntiCaptchaNumeric(int value) {
        prefs.edit().putInt(PREF_ANTI_CAPTCHA_NUMERIC, clampInt(value, 0, 2)).apply();
    }

    public int getAntiCaptchaMath() {
        return clampInt(prefs.getInt(PREF_ANTI_CAPTCHA_MATH, 0), 0, 1);
    }

    public void setAntiCaptchaMath(int value) {
        prefs.edit().putInt(PREF_ANTI_CAPTCHA_MATH, clampInt(value, 0, 1)).apply();
    }

    public int getAntiCaptchaMinLength() {
        return clampInt(prefs.getInt(PREF_ANTI_CAPTCHA_MIN_LENGTH, ANTI_CAPTCHA_MIN_LENGTH_DEFAULT), 0, 20);
    }

    public void setAntiCaptchaMinLength(int value) {
        prefs.edit().putInt(PREF_ANTI_CAPTCHA_MIN_LENGTH, clampInt(value, 0, 20)).apply();
    }

    public int getAntiCaptchaMaxLength() {
        return clampInt(prefs.getInt(PREF_ANTI_CAPTCHA_MAX_LENGTH, ANTI_CAPTCHA_MAX_LENGTH_DEFAULT), 0, 20);
    }

    public void setAntiCaptchaMaxLength(int value) {
        prefs.edit().putInt(PREF_ANTI_CAPTCHA_MAX_LENGTH, clampInt(value, 0, 20)).apply();
    }

    public String getAntiCaptchaLanguagePool() {
        String value = prefs.getString(PREF_ANTI_CAPTCHA_LANGUAGE_POOL, "en");
        value = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? "en" : value;
    }

    public void setAntiCaptchaLanguagePool(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!"rn".equals(normalized)) {
            normalized = "en";
        }
        prefs.edit().putString(PREF_ANTI_CAPTCHA_LANGUAGE_POOL, normalized).apply();
    }

    public AntiCaptchaManager.Config getAntiCaptchaConfig() {
        // Config собирается непосредственно перед отправкой captcha. Так MainActivity получает
        // актуальные defaults Neverlands (5 цифр, numeric=1, languagePool=en) и ручные override
        // из long-press настроек, не держа stale-копию между разными popup challenge.
        int minLength = getAntiCaptchaMinLength();
        int maxLength = getAntiCaptchaMaxLength();
        if (maxLength > 0 && minLength > maxLength) {
            minLength = maxLength;
        }
        return new AntiCaptchaManager.Config(
                getAntiCaptchaApiKey(),
                isAntiCaptchaPhrase(),
                isAntiCaptchaCaseSensitive(),
                getAntiCaptchaNumeric(),
                getAntiCaptchaMath(),
                minLength,
                maxLength,
                getAntiCaptchaLanguagePool()
        );
    }
    
    // === Универсальные методы ===
    
    /**
     * Получить состояние функции по типу.
     */
    // Универсальный опрос состояния по типу кнопки (используется панелью быстрых кнопок).
    public boolean isFunctionEnabled(QuickActionType type) {
        if (!isFeatureAvailable(type, "isFunctionEnabled")) {
            return false;
        }
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
            case AUTO_CAPTCHA: return isAntiCaptchaEnabled();
            default: return false;
        }
    }
    
    /**
     * Переключить состояние функции по типу.
     */
    // Универсальное переключение состояния по типу кнопки.
    public void toggleFunction(QuickActionType type) {
        if (rejectFeatureIfDenied(type, true, "toggleFunction")) {
            return;
        }
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
            case AUTO_CAPTCHA: toggleAntiCaptcha(); break;
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
        setAntiCaptchaEnabled(false);
    }

    /**
     * Снимает persisted/runtime-флаги только у тех авто-функций, которые исчезли из текущей
     * license-сессии. Используется при downgrade full -> public после истечения grant.
     * Для Anti-Captcha и AutoCut это обязательный путь выключения по временному лимиту:
     * full/custom grant истёк, `LicenseRuntime` пересобрал public-only session, а этот метод
     * затирает `KEY_ANTI_CAPTCHA` и `KEY_PREFIX + "auto_cut"` через соответствующие setter-ы.
     */
    public void disableUnavailableFeatures(String reason) {
        StringBuilder disabled = new StringBuilder();
        disableIfUnavailable(QuickActionType.AUTO_FIGHT, this::setAutoFightEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_FISH, this::setAutoFishEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_BAIT, this::setAutoBaitEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_SKIN, this::setAutoSkinEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_ATTACK, this::setAutoAttackEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_COMPASS, this::setAutoCompassEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_BOSS,
                enabled -> {
                    if (enabled) {
                        setAutoBossEnabled(true);
                    } else {
                        bossAuto.disableForLicenseSync("license_downgrade:" + reason);
                    }
                },
                disabled);
        disableIfUnavailable(QuickActionType.AUTO_INVISIBLE, this::setAutoInvisibleEnabled, disabled);
        disableIfUnavailable(QuickActionType.LOCATION_TRACKING, this::setLocationTrackingEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_DETECT, this::setAutoDetectEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_SUMMON, this::setAutoSummonEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_CURE, this::setAutoCureEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_DRINK, this::setAutoDrinkEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_MOVING, this::setAutoMovingEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_TREASURE, this::setAutoTreasureEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_CUT, this::setAutoCutEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_REFRESH, this::setAutoRefreshEnabled, disabled);
        disableIfUnavailable(QuickActionType.AUTO_CAPTCHA, this::setAntiCaptchaEnabled, disabled);
        requestQuickButtonsRefreshInternal("license_sync:" + reason);
        if (disabled.length() > 0) {
            AppLog.w("ANCLIENT_LICENSE", TAG, "LICENSE_FEATURE_FLAGS_DISABLED: reason=" + reason
                    + ", actions=" + disabled);
            syncBackgroundService("license_downgrade:" + reason);
        }
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private void disableIfUnavailable(QuickActionType type,
                                      BoolSetter setter,
                                      StringBuilder disabled) {
        if (type == null || LicenseRuntime.getInstance().isActionAllowed(type) || !isRawFunctionEnabled(type)) {
            return;
        }
        setter.set(false);
        if (disabled.length() > 0) {
            disabled.append(',');
        }
        disabled.append(type.getActionKey());
    }

    private boolean isRawFunctionEnabled(QuickActionType type) {
        if (type == null) {
            return false;
        }
        switch (type) {
            case AUTO_FIGHT:
                return AppVars.Profile != null
                        ? AppVars.Profile.LezDoAutoboi
                        : prefs.getBoolean(KEY_PREFIX + "auto_fight", false);
            case AUTO_FISH:
                return prefs.getBoolean(KEY_PREFIX + "auto_fish", false);
            case AUTO_BAIT:
                return prefs.getBoolean(KEY_PREFIX + "auto_bait", false);
            case AUTO_SKIN:
                return AppVars.Profile != null
                        ? AppVars.Profile.SkinAuto
                        : prefs.getBoolean(KEY_AUTO_SKIN, false);
            case AUTO_ATTACK:
                return normalizeAutoAttackToolId(prefs.getInt(KEY_AUTO_ATTACK_TOOL_ID, AppVars.AutoAttackToolId)) > 0
                        || prefs.getBoolean(KEY_AUTO_ATTACK_LEGACY, false);
            case AUTO_COMPASS:
                return compasAuto.isAutoCompassEnabled();
            case AUTO_BOSS:
                return bossAuto.isAutoBossEnabled();
            case AUTO_INVISIBLE:
                return prefs.getBoolean(KEY_PREFIX + "auto_invisible", false);
            case LOCATION_TRACKING:
                return prefs.getBoolean(KEY_LOCATION_TRACKING, false) || AppVars.DoShowWalkers;
            case AUTO_DETECT:
                return prefs.getBoolean(KEY_PREFIX + "auto_detect", false);
            case AUTO_SUMMON:
                return prefs.getBoolean(KEY_PREFIX + "auto_summon", false);
            case AUTO_CURE:
                return prefs.getBoolean(KEY_PREFIX + "auto_cure", false);
            case AUTO_DRINK:
                return prefs.getBoolean(KEY_PREFIX + "auto_drink", false);
            case AUTO_MOVING:
                return AppVars.AutoMoving;
            case AUTO_TREASURE:
                return AppVars.Profile != null
                        ? AppVars.Profile.AutoDig
                        : prefs.getBoolean(KEY_AUTO_TREASURE, false);
            case AUTO_CUT:
                return prefs.getBoolean(KEY_PREFIX + "auto_cut", false);
            case AUTO_REFRESH:
                return prefs.getBoolean(KEY_PREFIX + "auto_refresh", false);
            case AUTO_CAPTCHA:
                return prefs.getBoolean(KEY_ANTI_CAPTCHA, false);
            default:
                return false;
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
