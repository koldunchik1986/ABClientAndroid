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
    // Флаг восстановления авто-боя после ручного ввода капчи завершения боя.
    // Цепочка зависимостей:
    // 1) MainPhp.mainPhpFight(...) выставляет true, когда капча пришла при AutoboiOn.
    // 2) MainActivity.showCaptchaDialog(...) после успешного submit возвращает AutoboiOn.
    // 3) При отмене/закрытии диалога флаг сбрасывается в false (без авто-восстановления).
    public static volatile boolean ResumeAutoboiAfterCaptcha = false;
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
    public static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";

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
    public static String NamePri = "";
    public static int ValPri = 0;
    public static double AutoFishNV = 0;
    public static String AutoFishHand1 = "";
    public static String AutoFishHand1D = "";
    public static String AutoFishHand2 = "";
    public static String AutoFishHand2D = "";
    public static String AutoFishMassa = "";
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
    public static WeakReference<MainActivity> mainActivity;

    public static java.util.Map<String, String> myCharsOld = new java.util.LinkedHashMap<>();
    public static int myNevids = 0;
    public static int myNevidsOld = 0;
    public static String myLocOld = "";
    public static String myCoordOld = "";
    public static String myWalkers1 = "";
    public static String myWalkers2 = "";
    public static boolean DoShowWalkers = true;

    // Fast Attack variables (портировано из AppVars.cs — FormMainFast.cs)
    public static volatile boolean FastNeed = false;
    public static volatile String FastId = null;
    public static volatile String FastNick = null;
    public static volatile int FastCount = 0;
    public static volatile boolean FastWaitEndOfBoiActive = false;
    public static volatile boolean FastWaitEndOfBoiCancel = false;
    public static volatile boolean FastNeedAbilDarkTeleport = false;
    public static volatile boolean FastNeedAbilDarkFog = false;
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
