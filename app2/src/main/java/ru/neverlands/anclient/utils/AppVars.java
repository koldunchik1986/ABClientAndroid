package ru.neverlands.anclient.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.webkit.WebView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.neverlands.anclient.MainActivity;
import ru.neverlands.anclient.postfilter.ShopEntry;
import ru.neverlands.anclient.model.UserConfig;

import java.net.HttpCookie;

public class AppVars {
    private static final String RUNTIME_TIMERS_PREFS = "runtime_timers_prefs";
    private static final String KEY_NEVER_TIMER_DUE_AT_MS = "never_timer_due_at_ms";
    public static List<HttpCookie> lastCookies;
    public static UserConfig Profile;
    /**
     * Runtime-only учетные данные последнего успешного входа.
     * Не сохраняются на диск и нужны только для auto-relogin текущей сессии после `css/error.css`.
     */
    public static volatile String RuntimeAuthProfileId = "";
    public static volatile String RuntimeAuthUserNick = "";
    public static volatile String RuntimeAuthGamePassword = "";
    public static volatile String RuntimeAuthFlashPassword = "";
    public static boolean CacheRefresh = false;
    public static boolean AutoRefresh = false;
    public static boolean WaitFlash = false;
    public static String ContentMainPhp = "";
    public static ru.neverlands.anclient.model.AutoboiState Autoboi = ru.neverlands.anclient.model.AutoboiState.AutoboiOff;
    /**
     * Режим "свитка осады" (аналог C# `AppVars.DoFury` / `buttonFury`).
     *
     * Назначение:
     * - разрешает первый scroll-hit (`Снежок` / `Свиток Удар Ярости`) в `LezFight`,
     * - включает оркестрацию авто-проверки/авто-надевания свитка в `MainPhp`,
     * - после первого успешного удара свитком сбрасывается в false.
     *
     * Зависимости:
     * - `UserConfig.LezDoFury` (профильный флаг в настройках AutoBoi),
     * - `LezFight.IsMagicAllowed(...)` и post-hit auto-off логика,
     * - `MainPhp` (ветка авто-проверки экипировки свитка).
     */
    public static boolean DoFury = false;
    /**
     * Текущая задержка автоудара (сек) для активной группы противника в бою.
     *
     * Зависимости:
     * - выставляется в `LezFight.SelectFoeGroup()` из `FoeGroup.HitDelaySec`;
     * - используется в `MainActivity.getAutoBattleSubmitWaitMs()` для отложенной отправки хода;
     * - синхронизирована с delay в `LezFight.BuildFrame()`.
     */
    public static volatile int CurrentAutoBattleHitDelaySec = 0;
    /**
     * FuryAutoWear: требуется проверка, надет ли целевой свиток перед боем.
     * Аналогично `AutoSkinCheckKnife`, но для режима `DoFury`.
     */
    public static boolean AutoFuryCheckScroll = false;
    /**
     * FuryAutoWear: признак, что целевой свиток уже надет.
     * Аналогично `AutoSkinArmedKnife`, но для свитков `Снежок`/`Свиток Удар Ярости`.
     */
    public static boolean AutoFuryArmedScroll = false;
    /**
     * Название найденного/надетого свитка (для диагностики и чата).
     */
    public static String AutoFuryHand = "";
    /**
     * Долговечность надетого свитка в формате `cur/max` (если присутствует в slots-данных).
     */
    public static String AutoFuryHandD = "";
    public static String GuamodCode = "";
    public static String FightLink = "";
    // URL боевой капчи (аналог AppVars.CodeAddress в C#).
    public static String CodeAddress = "";
    // Последняя замеченная URL-картинка капчи завершения боя (/modules/code/code.php?...).
    public static volatile String LastFightCaptchaImageUrl = "";
    // Время (ms) когда была замечена LastFightCaptchaImageUrl.
    public static volatile long LastFightCaptchaImageAtMs = 0L;
    // Последние сырые байты PNG-капчи, сохранённые в WebViewRequestInterceptor.
    public static volatile byte[] LastFightCaptchaImageBytes = null;
    // Флаг "диалог капчи завершения боя сейчас открыт/показывается".
    public static volatile boolean IsFightCaptchaDialogVisible = false;
    /**
     * Ключ последней отправки боевой капчи (finish-link с нормализованным `code=????`).
     *
     * Назначение:
     * - защитить от повторного popup того же challenge, когда после submit
     *   в рантайме остается stale `FightLink` из старого fight-frame.
     *
     * Зависимости:
     * - выставляется в `MainActivity.showCaptchaDialog(...)` при нажатии "ОК";
     * - читается в `AutoModeForegroundService.maybeShowFightCaptchaDialog(...)`;
     * - сбрасывается в `MainPhp.mainPhpFight(...)`, если сервер снова требует капчу
     *   после уже отправленного `code=...` (ошибка/новый challenge).
     */
    public static volatile String LastSubmittedFightCaptchaFinishKey = "";
    /**
     * Время последней отправки боевой капчи (ms).
     * Используется вместе с `LastSubmittedFightCaptchaFinishKey` для TTL анти-дубля.
     */
    public static volatile long LastSubmittedFightCaptchaAtMs = 0L;
    /**
     * Минимальное время, раньше которого нельзя отправлять решение боевой captcha.
     *
     * Сервер может вернуть новый `fexp[4]` challenge сразу после submit, но `fexp[6]`
     * ещё содержит countdown. Диалог надо показать сразу, а auto-submit удержать до 0.
     */
    public static volatile long FightCaptchaSubmitNotBeforeMs = 0L;
    // Флаг восстановления авто-боя после ручного ввода капчи завершения боя.
    // Цепочка зависимостей:
    // 1) MainPhp.mainPhpFight(...) выставляет true, когда капча пришла при AutoboiOn.
    // 2) MainActivity.showCaptchaDialog(...) после успешного submit возвращает AutoboiOn.
    // 3) При отмене/закрытии диалога флаг сбрасывается в false (без авто-восстановления).
    public static volatile boolean ResumeAutoboiAfterCaptcha = false;
    // Флаг восстановления Auto Search Box (Авто-Клад) после ручного ввода боевой капчи.
    //
    // Зависимости:
    // 1) MainPhp.mainPhpFight(...) фиксирует состояние DoSearchBox/AutoMoving на момент
    //    появления challenge и выставляет true, если Auto-Клад должен продолжиться.
    // 2) MainActivity.showCaptchaDialog(...) после успешного submit делает bootstrap main.php
    //    (ab_search_box_bootstrap=1), чтобы DoSearchBox продолжил маршрут без ручного действия.
    // 3) При отмене/закрытии диалога флаг сбрасывается в false.
    public static volatile boolean ResumeSearchBoxAfterCaptcha = false;
    // Аналог Pers.IntHP / Pers.IntMA из C# (секунды полного восстановления HP/MA).
    // По умолчанию совпадает с C#: IntHP=2000, IntMA=9000.
    public static double PersIntHP = 2000.0;
    public static double PersIntMA = 9000.0;
    // Аналог Pers.Ready / Pers.LogReady из C# (для Timeout/Restoring после боя).
    public static long AutoboiReadyAtMs = 0L;
    public static String AutoboiReadyLog = "";
    public static String AutoboiReadyCompletedLog = "";
    // Управляемый флаг детального дампа HTML боя (вместо принудительного if(true)).
    public static boolean DebugDumpFightHtml = false;
    public static String LastBoiLog = "";
    public static String LastBoiEndLog = "";
    public static String LastBoiSostav = "";
    public static String LastBoiTravm = "";
    public static String LastBoiUron = "";
    public static Date LastBoiTimer = new Date();
    /**
     * Временная метка последнего подтверждённого "пульса боя" (ms).
     *
     * Назначение:
     * - не зависит от UI-таймера кнопки хода (`LastBoiTimer`) и используется только для фоновой автоматики;
     * - позволяет foreground-service понимать, что бой был активен совсем недавно даже при кратком переходе
     *   на промежуточный `main.php` без `fight_ty`.
     *
     * Зависимости:
     * - обновляется в `FightViewModel.processFightHtml(...)`/`autoTurnOnce(...)` при наличии маркеров боя;
     * - может обновляться в `MainPhp.mainPhpFight(...)` при активной боевой фазе (`fight.IsBoi`);
     * - читается в `AutoModeForegroundService.isFightSessionLikelyActive(...)`.
     */
    public static volatile long LastFightPulseAtMs = 0L;
    /**
     * Временная метка последнего чатово/локального анонса "Нападение" (ms).
     *
     * Нужна foreground-service как ранний сигнал:
     * - бой может начаться между кадрами и до появления `fight_ty` в верхнем фрейме;
     * - по этому сигналу сервис может форсировать синхронизацию `main.php?r=...` в фоне.
     *
     * Зависимости:
     * - обновляется в MainPhp.notifyNewFight(...);
     * - обновляется в MainActivity.broadcastReceiver при ACTION_ADD_CHAT_MESSAGE с текстом "Нападение";
     * - читается в AutoModeForegroundService.maybeForceFightFrameSync(...).
     */
    public static volatile long LastFightAnnounceAtMs = 0L;
    public static long IdleTimer = 0;
    public static long LastMainPhp = 0;
    public static Date NextCheckNoConnection;
    public static final String ACTION_STOP_AUTOFISH = "ru.neverlands.anclient.ACTION_STOP_AUTOFISH";
    public static final String ACTION_ADD_CHAT_MESSAGE = "ru.neverlands.anclient.ACTION_ADD_CHAT_MESSAGE";
    public static final String ACTION_WEBVIEW_LOAD_URL = "ru.neverlands.anclient.ACTION_WEBVIEW_LOAD_URL";
    public static final String ACTION_WEBVIEW_EVAL_JS = "ru.neverlands.anclient.ACTION_WEBVIEW_EVAL_JS";
    public static final String ACTION_PROXY_READY = "ru.neverlands.anclient.ACTION_PROXY_READY";
    public static final String ACTION_SHOW_CAPTCHA = "ru.neverlands.anclient.ACTION_SHOW_CAPTCHA";
    /**
     * Единый User-Agent для всех прямых HTTP-запросов приложения.
     *
     * Важно:
     * - строка должна оставаться браузерной (Chrome/Windows),
     * - запрещено добавлять идентификаторы клиента/приложения (имя приложения или Android-маркер),
     *   чтобы не оставлять серверу сигнатуру неофициального ПО.
     *
     * Использование:
     * - AuthManager (авторизация),
     * - ApiRepository (внешние API-запросы),
     * - NeverApi/getInfo (опрос API и логов),
     * - MainActivity/downloadCaptchaImageBytes (загрузка изображения капчи).
     */
    public static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    public static byte[] lastMainPhpResponse;
    public static byte[] lastChatMsgResponse;

    // Inventory variables
    public static java.util.List<ru.neverlands.anclient.model.InvEntry> InvList = new java.util.ArrayList<>();
    public static String BulkDropThing = "";
    public static String BulkDropPrice = "";
    public static String BulkSellThing = "";
    public static int BulkSellPrice = 0;
    public static int BulkSellSum = 0;
    public static String VCode = "";
    public static Date ServerDateTime;
    public static String url_main_top = "";
    public static String url_chmain = "";
    public static String url_ch_list = "";
    public static String url_ch_buttons = "";
    public static String url_ch_refr = "";
    public static boolean PriSelected = false;
    public static boolean AutoFishCheckUm = false;
    public static boolean AutoFishCheckUd = false;
    public static boolean AutoFishWearUd = false;
    /**
     * Флаг блокировки фоновых probe'ов (main.php?go=inf&af_tick=1) при активной рыбалке.
     * Устанавливается в FishAjaxPhp.processFishAct1() и очищается после act=2.
     * Предотвращает перезагрузку PHPSESSID между act=1 и act=2 запросами.
     */
    public static volatile boolean suppressBackgroundProbesDuringFishing = false;
    public static volatile long fishingSequenceStartAtMs = 0L;
    /**
     * Safety-net таймаут подавления фоновых probe'ов во время рыбалки (мс).
     * Начальное значение — безопасный дефолт для первого цикла.
     * После первого act=2 (Ловить) обновляется динамически:
     * JS TimerStart СКЛАДЫВАЕТ act=1(section[4]) + act=2(section[4]),
     * реальный таймер = act1_timer + act2_timer + 15s запас.
     * (напр. навык 951: 30+30+15 = 75с; навык 0: 30+291+15 = 336с).
     * В нормальном режиме act=2 response очищает флаг раньше таймаута.
     */
    public static volatile long fishingExpectedDurationMs = 360_000L;
    /**
     * AutoSkin: флаг "нужно перечитать умение охоты" (аналог C# `AutoSkinCheckUm`).
     * Используется оркестратором `MainPhp` для перехода на вкладку умений.
     */
    public static boolean AutoSkinCheckUm = false;
    /**
     * Текущее значение умения охоты (аналог C# `SkinUm`).
     */
    public static int SkinUm = 0;
    /**
     * AutoSkin: требуется проверка, надет ли разделочный нож (аналог C# `AutoSkinCheckKnife`).
     */
    public static boolean AutoSkinCheckKnife = false;
    /**
     * AutoSkin: признак, что нож надет (аналог C# `AutoSkinArmedKnife`).
     */
    public static boolean AutoSkinArmedKnife = false;
    /**
     * Название надетого ножа (аналог C# `AutoSkinHand`).
     */
    public static String AutoSkinHand = "";
    /**
     * Долговечность надетого ножа в формате `cur/max` (аналог C# `AutoSkinHandD`).
     */
    public static String AutoSkinHandD = "";
    /**
     * Время последней периодической проверки ножа в ms (аналог C# `AutoSkinLastChecked`).
     */
    public static long AutoSkinLastChecked = 0L;
    /**
     * AutoCut: требуется проверить/надеть серп перед срезом травы.
     *
     * Зависимости:
     * - выставляется `AutoCutManager.onAutoCutEnabled(...)` и `requestSickleCheckBeforeCut(...)`;
     * - читается `WebAppInterface.DoHerbAutoCut()` и `AutoCutHandler.processMainPhpAutoCutStep(...)`;
     * - пока true, map.js не запускает автоматический `Оглядеться`, чтобы сначала пройти
     *   main.php -> персонаж/инвентарь -> надевание серпа.
     */
    public static boolean AutoCutCheckSickle = false;
    /**
     * AutoCut: серп найден в руках или успешно надет.
     *
     * Зависимости:
     * - заполняется `ParsedDressed.IsWearSickle()` после разбора `slots_inv(...)`/`slots_pla(...)`;
     * - проверяется `AutoCutManager.isSickleReadyForCut()` перед отправкой `alchemy_ajax.php?act=3`;
     * - сбрасывается при выключении AutoCut и при новом запросе проверки серпа.
     */
    public static boolean AutoCutArmedSickle = false;
    /**
     * AutoCut: название найденного серпа для диагностики и файлового лога.
     *
     * Зависимости:
     * - значение берётся из parsed hand slot в `ParsedDressed.IsWearSickle()`;
     * - используется только как runtime-снимок, не как persisted-настройка.
     */
    public static String AutoCutSickleHand = "";
    /**
     * AutoCut: долговечность найденного серпа в формате `current/max`.
     *
     * Зависимости:
     * - синхронизируется с `AutoCutSickleHand` по индексу slot-списка `ParsedDressed`;
     * - нужна для диагностики износа без повторного парсинга страницы персонажа.
     */
    public static String AutoCutSickleHandD = "";
    /**
     * AutoCut: запрошен проход инвентаря для штатного cleanup/выброса просрочки.
     *
     * Зависимости:
     * - выставляется `AutoCutManager.maybeRequestCleanupAfterCut(...)`, когда прирост массы
     *   после срезов превысил настроенный порог;
     * - обслуживается `AutoCutHandler.processCleanupOpenInventory(...)` и завершается
     *   `AutoCutHandler.afterMainPhpInventoryStep(...)` после стандартного `mainPhpInv(...)`;
     * - пока true, `DoHerbAutoCut()` не запускает новый `Оглядеться`, чтобы не смешивать
     *   map ajax и inventory cleanup.
     */
    public static boolean AutoCutCleanupPending = false;
    /** Причина последнего AutoCut cleanup-запроса для `AUTO_CUT_TRACE` логов. */
    public static String AutoCutCleanupReason = "";
    /** Накопленный вес срезанной травы с момента последнего cleanup-прохода. */
    public static double AutoCutHarvestedMassSinceCleanup = 0d;
    /** Максимальная масса инвентаря из `SetAutoFishMassa(...)`; fallback-порог применяется, если 0. */
    public static double AutoCutKnownMassMax = 0d;
    /**
     * AutoSkin: флаг "нужно перечитать результаты охоты" (аналог C# `AutoSkinCheckRes`).
     */
    public static boolean AutoSkinCheckRes = false;
    /**
     * Последние значения охотничьих ресурсов (аналог C# `SkinRes`).
     * Ключ: название ресурса, значение: текущий вес/объем на странице ресурсов.
     */
    public static final java.util.Map<String, Double> SkinRes = new java.util.LinkedHashMap<>();
    public static String NamePri = "";
    public static int ValPri = 0;
    public static double AutoFishNV = 0;
    public static String AutoFishHand1 = "";
    public static String AutoFishHand1D = "";
    public static String AutoFishHand2 = "";
    public static String AutoFishHand2D = "";
    public static String AutoFishLikeId = "";
    public static String AutoFishLikeVal = "";
    public static String AutoFishMassa = "";
    /**
     * Текущий процент усталости персонажа из верхнего фрейма (`Усталость: ...`).
     * Используется в авто-рыбалке для решения: делать заброс или сначала выполнить шаг "Пить".
     */
    public static int Tied = 0;
    /**
     * Runtime-снимок отравления и небоевых травм персонажа.
     *
     * Формат (C# parity `AppVars.PoisonAndWounds`):
     * - `[0]` — количество эффектов "Яд" (code `24`)
     * - `[1]` — количество легких травм (code `4`)
     * - `[2]` — количество средних травм (code `3`)
     * - `[3]` — количество тяжелых травм (code `2`)
     *
     * Обновляется централизованно через `CharacterVitalsManager` из:
     * - pinfo-синхронизации (`var eff = [...]`);
     * - fallback-детекта server popup на карте ("У Вас тяжёлая травма").
     */
    public static int[] PoisonAndWounds = new int[] {0, 0, 0, 0};
    // Runtime-снимок текущих HP/MA из pinfo/hpmp.
    // Используется для стартовой синхронизации в авто-режимах.
    public static int CurHP = 0;
    public static int MaxHP = 0;
    public static int CurMA = 0;
    public static int MaxMA = 0;
    /**
     * Анти-зацикливание авто-рыбалки: ключ последней попытки проверки/переодевания снастей.
     * Используется в MainPhp, чтобы остановить бесконечный цикл `inf -> inv -> wear -> inf`.
     */
    public static String AutoFishWearLoopKey = "";
    /**
     * Анти-зацикливание авто-рыбалки: количество повторов одного и того же `AutoFishWearLoopKey`.
     */
    public static int AutoFishWearLoopCount = 0;
    /**
     * Анти-зацикливание авто-рыбалки: время (ms) последнего инкремента `AutoFishWearLoopCount`.
     */
    public static long AutoFishWearLoopStamp = 0L;
    /**
     * C# parity: признак, что нужно выполнить "Пить" в рамках авто-рыбалки (`AutoFishDrink`).
     */
    public static boolean AutoFishDrink = false;
    /**
     * C# parity: одноразовый флаг "Пить" после рыбацкого шага (`AutoFishDrinkOnce`).
     */
    public static boolean AutoFishDrinkOnce = false;
    /**
     * Текущий актуальный vcode для авто-рыбалки с капчей.
     *
     * Назначение:
     * - синхронизирует vcode между `fish_ajax.php?act=1` и `fish_ajax.php?act=2`;
     * - при покупке капчи сервер отправляет новый vcode в ответе модуля `modules/code/code.php`;
     * - этот новый vcode должен быть парсирован и сохранён здесь перед отправкой `act=2`;
     * - это предотвращает ошибку "неверный код защиты" при истечении TTL старого vcode.
     *
     * Зависимости:
     * - обновляется в `FishAjaxPhp.processFishAct1()` из response payload;
     * - обновляется в `WebViewRequestInterceptor` при перехвате ответа `modules/code/code.php`;
     * - используется в `FishAjaxPhp` при построении finishUrl для капчи.
     *
     * Жизненный цикл:
     * - инициализируется в `processFishAct1()` из `state.vcode`;
     * - обновляется при платеже капчи (если сервер отправляет новый vcode);
     * - очищается при остановке авто-рыбалки или при запуске нового цикла `act=1`.
     */
    public static volatile String FishCurrentVcode = "";

    /**
     * Кэш озера HTML для авто-рыбалки.
     *
     * Назначение:
     * - хранит актуальный HTML озера (main.php?get_id=55) для парсинга vcode, lakeid, act, primid;
     * - парсирование vcode ПРЯМО ИЗ озера, а НЕ из fish_ajax.php?act=1 ответа;
     * - это предотвращает проблему истечения vcode через 5 минут (vcode свеженький каждый цикл);
     * - на каждый цикл рыбалки озеро перезагружается и кэш обновляется.
     *
     * Жизненный цикл:
     * - заполняется в MainPhp.filter() когда приходит main.php?get_id=55 БЕЗ параметра &act=;
     * - используется в FishAjaxPhp.mainPhpAutoFishPrepareFromLakeAndroid() для парсинга vcode;
     * - очищается при остановке авто-рыбалки или при переходе на другую страницу.
     *
     * ПК-архитектура: этот HTML занимает место Delphi-переменной озера, которая парсилась один раз
     * при загрузке страницы и переиспользовалась для каждого цикла рыбалки.
     */
    public static String ContentLakeHtml = "";

    /**
     * Время последнего обновления ContentLakeHtml (timestamp в ms).
     * Используется для проверки что озеро "свежее" перед отправкой act=1.
     * Если (now - lastUpdate) > 120_000 ms, озеро перезагружается чтобы получить свежий vcode.
     * Фиксирует проблему: после 5+ минут в фоне vcode истекает (ошибка "Неверный код защиты").
     */
    public static volatile long ContentLakeHtmlLastUpdateAtMs = 0;

    /**
     * ID текущего озера для авто-рыбалки (ЭТАП 2).
     * Парсится из формы озера (lakeid), используется при построении act=1/act=2 запросов.
     */
    public static volatile int FishCurrentLakeid = -1;

    /**
     * Время (timestamp в ms) когда должна быть запущена следующая попытка авто-рыбалки (ЭТАП 2).
     * Используется для планирования повторных попыток с exponential backoff.
     */
    public static volatile long NextFishingAttemptDueAtMs = 0L;

    /**
     * Флаг - озеро считается "испорченным" и нужна переперезагрузка.
     * Устанавливается в true когда:
     * - Получена ошибка "Неверный код защиты" (act=1 вернул ошибку vcode)
     * - Произошел переход между контекстами (бой, pinfo, питьё, открытие других диалогов)
     * Обрабатывается в executeFishingCycleCore() - озеро очищается и перезагружается.
     */
    public static volatile boolean FishLakeShouldBeRefreshed = false;

    /**
     * Флаг - принудительно запустить probe для рыбалки, несмотря на UI флаги.
     * Устанавливается в true когда:
     * - Закончился cooldown после питья эликсира
     * - Нужно убедиться что рыбалка начнется, несмотря на то что пользователь смотрит на экран
     * Проверяется в AutoModeFgService - если true, запуск probe игнорирует uiForegroundLikely
     */
    public static volatile boolean ProbeForceNeedAutofish = false;

    /**
     * Флаг для инициализации авто-боя при холодном старте приложения.
     * Устанавливается в restorePersistentAutoModesAfterLogin когда autoFight был включен ранее.
     * ForcedActionGuard использует этот флаг чтобы запустить первый probe несмотря на uiForegroundLikely=true.
     * Очищается после первого вызова probe.
     */
    public static volatile boolean ProbeForceNeedAutoboi = false;

    public static String BulkSellOldScript = "";
    public static String BulkSellOldName = "";
    public static String BulkSellOldPrice = "";
    public static List<ShopEntry> ShopList = new ArrayList<>();
    private static AssetManager assetManager;
    private static java.io.File logsDir;
    private static java.io.File infoDir;

    public static int LocalProxyPort = 8052;
    public static String LocalProxyAddress = "127.0.0.1";
    public static boolean DoPromptExit = true;
    public static String Chat = "";
    public static String MovingTime = "";
    public static boolean AutoMoving = false;
    // C# parity: флаг menuitemDoSearchBox ("Ходим, ищем клад").
    public static boolean DoSearchBox = false;
    // C# parity (Map.AbcCells[reg].Visited): runtime-маркер времени последнего посещения клетки.
    public static final java.util.Map<String, Long> SearchBoxVisited = new java.util.concurrent.ConcurrentHashMap<>();
    public static String AutoMovingDestinaton = null;
    public static ru.neverlands.anclient.utils.MapPath AutoMovingMapPath = null;
    public static String AutoMovingNextJump = null;
    public static int AutoMovingJumps = 0;
    public static ru.neverlands.anclient.model.CityGateType AutoMovingCityGate = ru.neverlands.anclient.model.CityGateType.None;
    // Runtime-state автокопки клада: этап экипировки лопаты в инвентаре.
    public static volatile boolean AutoTreasureDigPendingInventory = false;
    // Runtime-метка, что выбранная в настройках лопата уже подготовлена в руке для копки.
    public static volatile boolean AutoTreasureShovelReady = false;
    // Последний тип лопаты, для которого выставлен AutoTreasureShovelReady.
    public static volatile String AutoTreasureShovelReadyOption = "";
    // Runtime-пауза небоевых авто-функций на этапе подготовки автокопки (экипировка лопаты).
    // Авто-бой не должен зависеть от этого флага.
    public static volatile boolean TreasureDigPauseNonCombatAutoFunctions = false;
    public static WeakReference<MainActivity> mainActivity;

    public static java.util.Map<String, String> myCharsOld = new java.util.LinkedHashMap<>();
    public static int myNevids = 0;
    public static int myNevidsOld = 0;
    public static String myLocOld = "";
    public static String myCoordOld = "";
    public static String myWalkers1 = "";
    public static String myWalkers2 = "";
    public static boolean DoShowWalkers = true;

    // C# parity (`AppVars.Cure*`):
    // Request pipeline for targeted wound cure (self/friend/neutral) through doctorform.
    // Flow:
    // 1) request is scheduled by RoomManager (or manual UI analog),
    // 2) MainPhp consumes it in non-combat flow and submits аптечку form,
    // 3) flags are reset after submit/failure.
    public static volatile boolean CureNeed = false;
    public static volatile String CureNick = "";
    // "1"=легкая, "2"=средняя, "3"=тяжелая, "4"=боевая.
    public static volatile String CureTravm = "";
    public static volatile String CureNickDone = "";
    public static volatile String CureNickBoi = "";
    // Runtime pause for non-combat auto pipelines while external auto-cure request is being processed.
    // Needed to avoid AutoMoving/AutoSearch overlap with doctorform flow.
    public static volatile boolean CurePauseNonCombatAutoFunctions = false;

    // Fast Attack variables (портировано из AppVars.cs — FormMainFast.cs)
    public static volatile boolean FastNeed = false;
    public static volatile String FastId = null;
    public static volatile String FastNick = null;
    public static volatile int FastCount = 0;
    // Runtime pause for non-combat auto pipelines while any FastAction is active.
    // Important: Autoboi/fight logic must continue working and is not paused by this flag.
    public static volatile boolean FastPauseNonCombatAutoFunctions = false;
    // One-shot marker: after FastAction completion we should return to map ("go=ret").
    public static volatile boolean FastReturnToMapPending = false;
    
    // === Система паузы авто-функций перед срабатыванием таймера зелья ===
    // Runtime pause for non-combat auto pipelines while AppTimer is about to fire (within 5 sec buffer).
    // Allows user to open inventory and drink potion without AutoFish/AutoSkin interference.
    // Important: Autoboi/fight logic must continue working and is not paused by this flag.
    public static volatile boolean TimerPauseNonCombatAutoFunctions = false;
    
    // Сохраненное состояние авто-функций перед паузой таймера.
    // Используется для восстановления после исполнения таймера.
    public static volatile boolean TimerPauseAutoFishState = false;
    public static volatile boolean TimerPauseAutoSkinState = false;
    public static volatile boolean TimerPauseAutoCutState = false;
    public static volatile boolean TimerPauseAutoLumberjackState = false;
    public static volatile boolean TimerPauseAutoBaitState = false;
    public static volatile boolean TimerPauseAutoCompassState = false;
    public static volatile boolean TimerPauseAutoAttackState = false;
    public static volatile boolean TimerPauseAutoInvisibleState = false;
    public static volatile boolean FastWaitEndOfBoiActive = false;
    public static volatile boolean FastWaitEndOfBoiCancel = false;
    public static volatile boolean FastNeedAbilDarkTeleport = false;
    public static volatile boolean FastNeedAbilDarkFog = false;
    // Имя комплекта для отложенного надевания через main.php (паритет с AppVars.WearComplect в C#).
    // Устанавливается таймерами и другими авто-сценариями, где переодевание выполняется вне UI-контекста.
    public static volatile String WearComplect = "";
    // Deferred auto-bliss trigger from MapAjax:
    // threshold reached while step cooldown is active (NeverTimer > now).
    public static volatile boolean AutoDrinkBlazPending = false;
    /**
     * Инструмент авто-нападения (аналог `AppVars.AutoAttackToolId` из C#).
     *
     * Значения:
     * 0 - авто-нападение отключено/инструмент не выбран,
     * 1 - боевые,
     * 2 - закрытые боевые,
     * 3 - кулачки,
     * 4 - закрытые кулачки,
     * 5 - портал.
     *
     * Зависимости:
     * - `AutoFunctionsManager` хранит значение в SharedPreferences и синхронизирует его сюда.
     * - `QuickButtonsPanel` даёт UI выбора инструмента.
     * - `RoomManager`/авто-нападение используют это значение как fallback, если у контакта нет своего toolId.
     */
    public static volatile int AutoAttackToolId = 0;
    // NeverTimer — cooldown перед выполнением быстрого действия (аналог DateTime.Now > AppVars.NeverTimer в C#)
    public static volatile long NeverTimer = 0;
    // WaitOpen — ждать окончания боя даже для открытых боёв (аналог AppVars.WaitOpen в C#)
    public static volatile boolean WaitOpen = false;
    // DoPerenap — атаковать повторно (аналог AppVars.DoPerenap в C#)
    public static volatile boolean DoPerenap = false;
    // Тротлинг сообщений тотема (аналог FastTotemMessageTime + FastTotemMessageTimeBlockSeconds в C#)
    public static volatile long FastTotemMessageTime = 0;
    public static final int FAST_TOTEM_MESSAGE_BLOCK_SECONDS = 10;

    private static Context context;

    public static void init(Context context) {
        AppVars.context = context;
        assetManager = context.getAssets();
        ru.neverlands.anclient.model.LezSpellCollection.init(context);
        ExtMap.init(context);
        logsDir = context.getExternalFilesDir("Logs");
        if (logsDir != null && !logsDir.exists()) {
            logsDir.mkdirs();
        }
        // files/info — постоянные пользовательские данные ANClient (license, chat, stats).
        // В отличие от files/Logs, эта директория не очищается кнопкой "Очистить логи".
        infoDir = resolveInfoDir(context);
        restorePersistentNeverTimer(context, "AppVars.init");
    }

    public static synchronized void setNeverTimerDueAt(long dueAtMs, String source) {
        NeverTimer = Math.max(0L, dueAtMs);
        persistNeverTimerDueAt(NeverTimer, source);
    }

    public static synchronized void setNeverTimerRemaining(long msLeft, String source) {
        long safeMsLeft = Math.max(0L, msLeft);
        setNeverTimerDueAt(System.currentTimeMillis() + safeMsLeft, source);
    }

    public static synchronized void clearNeverTimer(String source) {
        NeverTimer = 0L;
        persistNeverTimerDueAt(0L, source);
    }

    public static long getNeverTimerRemainingMs() {
        return Math.max(0L, NeverTimer - System.currentTimeMillis());
    }

    public static boolean isNeverTimerActive() {
        return getNeverTimerRemainingMs() > 0L;
    }

    public static synchronized long restorePersistentNeverTimer(Context context, String source) {
        if (context == null) {
            return NeverTimer;
        }
        long now = System.currentTimeMillis();
        long persistedDueAt = getRuntimeTimersPrefs(context).getLong(KEY_NEVER_TIMER_DUE_AT_MS, 0L);
        if (persistedDueAt > now) {
            NeverTimer = Math.max(NeverTimer, persistedDueAt);
            AppLog.d("NEVER_TIMER", "restorePersistentNeverTimer: source=" + source
                    + ", dueInMs=" + Math.max(0L, NeverTimer - now));
        } else if (persistedDueAt > 0L) {
            getRuntimeTimersPrefs(context).edit().remove(KEY_NEVER_TIMER_DUE_AT_MS).apply();
            if (NeverTimer <= now) {
                NeverTimer = 0L;
            }
            AppLog.d("NEVER_TIMER", "restorePersistentNeverTimer: expired, source=" + source);
        }
        return NeverTimer;
    }

    private static void persistNeverTimerDueAt(long dueAtMs, String source) {
        Context appContext = context;
        if (appContext == null) {
            return;
        }
        SharedPreferences prefs = getRuntimeTimersPrefs(appContext);
        if (dueAtMs > System.currentTimeMillis()) {
            prefs.edit().putLong(KEY_NEVER_TIMER_DUE_AT_MS, dueAtMs).apply();
            AppLog.d("NEVER_TIMER", "persistNeverTimerDueAt: source=" + source
                    + ", dueInMs=" + Math.max(0L, dueAtMs - System.currentTimeMillis()));
        } else {
            prefs.edit().remove(KEY_NEVER_TIMER_DUE_AT_MS).apply();
            AppLog.d("NEVER_TIMER", "clearPersistentNeverTimer: source=" + source);
        }
    }

    private static SharedPreferences getRuntimeTimersPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(RUNTIME_TIMERS_PREFS, Context.MODE_PRIVATE);
    }

    public static void setRuntimeAuthCredentials(UserConfig profile, String gamePassword, String flashPassword) {
        if (profile == null) {
            clearRuntimeAuthCredentials();
            return;
        }
        RuntimeAuthProfileId = profile.id == null ? "" : profile.id;
        RuntimeAuthUserNick = profile.UserNick == null ? "" : profile.UserNick;
        RuntimeAuthGamePassword = gamePassword == null ? "" : gamePassword;
        RuntimeAuthFlashPassword = flashPassword == null ? "" : flashPassword;
    }

    public static void clearRuntimeAuthCredentials() {
        RuntimeAuthProfileId = "";
        RuntimeAuthUserNick = "";
        RuntimeAuthGamePassword = "";
        RuntimeAuthFlashPassword = "";
    }

    public static Context getContext() {
        return context;
    }


    public static AssetManager getAssetManager() {
        return assetManager;
    }

    public static java.io.File getLogsDir() {
        return logsDir;
    }

    public static java.io.File getInfoDir() {
        if (infoDir == null && context != null) {
            // Lazy fallback нужен для статических вызовов после восстановления процесса Android.
            infoDir = resolveInfoDir(context);
        }
        return infoDir;
    }

    private static java.io.File resolveInfoDir(Context context) {
        if (context == null) {
            return null;
        }
        java.io.File root = context.getExternalFilesDir(null);
        if (root == null) {
            root = context.getFilesDir();
        }
        java.io.File dir = new java.io.File(root, "info");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
