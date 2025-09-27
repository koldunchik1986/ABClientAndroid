package ru.neverlands.abclient.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.webkit.WebView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.neverlands.abclient.postfilter.ShopEntry;
import ru.neverlands.abclient.model.UserConfig;

public class AppVars {
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

    public static int LocalProxyPort = 8052;
    public static String LocalProxyAddress = "127.0.0.1";
    public static boolean DoPromptExit = true;
    public static String Chat = "";
    public static String MovingTime = "";
    public static boolean AutoMoving = false;

    public static void init(Context context) {
        assetManager = context.getAssets();
    }


    public static AssetManager getAssetManager() {
        return assetManager;
    }
}