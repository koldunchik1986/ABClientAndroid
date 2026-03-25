package ru.neverlands.abclient.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.webkit.WebView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.postfilter.ShopEntry;
import ru.neverlands.abclient.model.UserConfig;

import java.net.HttpCookie;

public class AppVars {
    public static List<HttpCookie> lastCookies;
    public static UserConfig Profile;
    public static boolean CacheRefresh = false;
    public static boolean AutoRefresh = false;
    public static boolean WaitFlash = false;
    public static String ContentMainPhp = "";
    public static ru.neverlands.abclient.model.AutoboiState Autoboi = ru.neverlands.abclient.model.AutoboiState.AutoboiOff;
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
    public static final String ACTION_STOP_AUTOFISH = "ru.neverlands.abclient.ACTION_STOP_AUTOFISH";
    public static final String ACTION_ADD_CHAT_MESSAGE = "ru.neverlands.abclient.ACTION_ADD_CHAT_MESSAGE";
    public static final String ACTION_WEBVIEW_LOAD_URL = "ru.neverlands.abclient.ACTION_WEBVIEW_LOAD_URL";
    public static final String ACTION_WEBVIEW_EVAL_JS = "ru.neverlands.abclient.ACTION_WEBVIEW_EVAL_JS";
    public static final String ACTION_PROXY_READY = "ru.neverlands.abclient.ACTION_PROXY_READY";
    public static final String ACTION_SHOW_CAPTCHA = "ru.neverlands.abclient.ACTION_SHOW_CAPTCHA";
    /**
     * Единый User-Agent для всех прямых HTTP-запросов приложения.
     *
     * Важно:
     * - строка должна оставаться браузерной (Chrome/Windows),
     * - запрещено добавлять идентификаторы клиента/приложения (например, ABClient/Android; ABClient),
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
    public static java.util.List<ru.neverlands.abclient.model.InvEntry> InvList = new java.util.ArrayList<>();
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
    public static String BulkSellOldScript = "";
    public static String BulkSellOldName = "";
    public static String BulkSellOldPrice = "";
    public static List<ShopEntry> ShopList = new ArrayList<>();
    private static AssetManager assetManager;
    private static java.io.File logsDir;

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
    public static ru.neverlands.abclient.utils.MapPath AutoMovingMapPath = null;
    public static String AutoMovingNextJump = null;
    public static int AutoMovingJumps = 0;
    public static ru.neverlands.abclient.model.CityGateType AutoMovingCityGate = ru.neverlands.abclient.model.CityGateType.None;
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
    public static volatile boolean FastWaitEndOfBoiActive = false;
    public static volatile boolean FastWaitEndOfBoiCancel = false;
    public static volatile boolean FastNeedAbilDarkTeleport = false;
    public static volatile boolean FastNeedAbilDarkFog = false;
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
        ru.neverlands.abclient.model.LezSpellCollection.init(context);
        ExtMap.init(context);
        logsDir = context.getExternalFilesDir("Logs");
        if (logsDir != null && !logsDir.exists()) {
            logsDir.mkdirs();
        }
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
}
