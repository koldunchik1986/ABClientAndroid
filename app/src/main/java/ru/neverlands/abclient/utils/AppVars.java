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
    public static boolean WaitFlash = false;
    public static String ContentMainPhp = "";
    public static long IdleTimer = 0;
    public static long LastMainPhp = 0;
    public static Date NextCheckNoConnection;
    public static final String ACTION_STOP_AUTOFISH = "ru.neverlands.abclient.ACTION_STOP_AUTOFISH";
    public static final String ACTION_ADD_CHAT_MESSAGE = "ru.neverlands.abclient.ACTION_ADD_CHAT_MESSAGE";
    public static final String ACTION_WEBVIEW_LOAD_URL = "ru.neverlands.abclient.ACTION_WEBVIEW_LOAD_URL";
    public static final String ACTION_PROXY_READY = "ru.neverlands.abclient.ACTION_PROXY_READY";

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

    public static List<String> myCharsOld = new ArrayList<>();
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

    private static Context context;

    public static void init(Context context) {
        AppVars.context = context;
        assetManager = context.getAssets();
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
