package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Handler;
import android.os.Looper;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.postfilter.MainPhp;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.FileLogger;

/**
 * РњРѕРґСѓР»СЊ Р°РІС‚Рѕ-С„СѓРЅРєС†РёРё В«РђРІС‚Рѕ-Р‘РѕСЃСЃС‹В».
 *
 * РќР°Р·РЅР°С‡РµРЅРёРµ:
 * - Р»РѕРІРёС‚ СЃРёСЃС‚РµРјРЅС‹Р№ Р°РЅРѕРЅСЃ С‡Р°С‚Р° Рѕ РЅР°РїР°РґРµРЅРёРё Р±РѕСЃСЃР° РЅР° РёРіСЂРѕРєР°;
 * - Р·Р°РїСѓСЃРєР°РµС‚ РїРѕРёСЃРє С†РµР»Рё С‡РµСЂРµР· СѓР¶Рµ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёР№ РєРѕРЅС‚СѓСЂ Auto-РљРѕРјРїР°СЃ;
 * - РїРѕСЃР»Рµ РЅР°С…РѕР¶РґРµРЅРёСЏ С†РµР»Рё РїСЂРёРјРµРЅСЏРµС‚ FastAction В«РЎРІРёС‚РѕРє Р—Р°С‰РёС‚С‹В»;
 * - РїРѕСЃР»Рµ Р·Р°РІРµСЂС€РµРЅРёСЏ Р±РѕСЏ РІРѕР·РІСЂР°С‰Р°РµС‚ РїРµСЂСЃРѕРЅР°Р¶Р° РЅР° РёСЃС…РѕРґРЅСѓСЋ РєР»РµС‚РєСѓ Рё РІРѕСЃСЃС‚Р°РЅР°РІР»РёРІР°РµС‚ РїР°СѓР·РµРЅРЅС‹Рµ Р°РІС‚Рѕ-С„СѓРЅРєС†РёРё.
 *
 * РљР»СЋС‡РµРІРѕРµ РїСЂР°РІРёР»Рѕ СЂРµР°Р»РёР·Р°С†РёРё:
 * - РЅРµ РґСѓР±Р»РёСЂРѕРІР°С‚СЊ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёРµ РєРѕРЅРІРµР№РµСЂС‹ (Auto-РљРѕРјРїР°СЃ, FastAction, Navigator),
 *   Р° С‚РѕР»СЊРєРѕ РѕСЂРєРµСЃС‚СЂРёСЂРѕРІР°С‚СЊ РёС… С‡РµСЂРµР· AutoFunctionsManager.
 */
/**
 * РћСЂРєРµСЃС‚СЂР°С‚РѕСЂ СЃС†РµРЅР°СЂРёСЏ В«РђРІС‚Рѕ-Р‘РѕСЃСЃС‹В».
 *
 * Р—Р°РґР°С‡Р° РјРѕРґСѓР»СЏ:
 * - РѕС‚СЃР»РµРґРёС‚СЊ СЃРѕР±С‹С‚РёРµ РЅР°РїР°РґРµРЅРёСЏ Р±РѕСЃСЃР° РёР· СЃРёСЃС‚РµРјРЅРѕРіРѕ С‡Р°С‚Р°;
 * - РїРµСЂРµРґР°С‚СЊ РїРѕРёСЃРє С†РµР»Рё РІ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёР№ РєРѕРЅС‚СѓСЂ Auto-РљРѕРјРїР°СЃ;
 * - РїРѕСЃР»Рµ РЅР°С…РѕР¶РґРµРЅРёСЏ С†РµР»Рё РїСЂРёРјРµРЅРёС‚СЊ В«РЎРІРёС‚РѕРє Р—Р°С‰РёС‚С‹В» С‡РµСЂРµР· FastAction;
 * - РїРѕСЃР»Рµ Р·Р°РІРµСЂС€РµРЅРёСЏ СЃС†РµРЅР°СЂРёСЏ РІРµСЂРЅСѓС‚СЊ РёСЃС…РѕРґРЅС‹Рµ РЅР°СЃС‚СЂРѕР№РєРё Р°РІС‚Рѕ-С„СѓРЅРєС†РёР№.
 *
 * Р’Р°Р¶РЅС‹Р№ РёРЅРІР°СЂРёР°РЅС‚:
 * - Р·РґРµСЃСЊ РЅРµС‚ РґСѓР±Р»РёСЂРѕРІР°РЅРёСЏ Р»РѕРіРёРєРё РЅР°РІРёРіР°С‚РѕСЂР°/РєРѕРјРїР°СЃР°/fast-action;
 *   РёСЃРїРѕР»СЊР·СѓСЋС‚СЃСЏ С‚РѕР»СЊРєРѕ РІС‹Р·РѕРІС‹ СѓР¶Рµ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёС… РјРµРЅРµРґР¶РµСЂРѕРІ.
 */
/**
 * РћСЂРєРµСЃС‚СЂР°С‚РѕСЂ СЃС†РµРЅР°СЂРёСЏ В«РђРІС‚Рѕ-Р‘РѕСЃСЃС‹В».
 *
 * Р§С‚Рѕ РґРµР»Р°РµС‚ РјРѕРґСѓР»СЊ:
 * - РїРѕР»СѓС‡Р°РµС‚ СЃРёСЃС‚РµРјРЅС‹Рµ СЃРѕР±С‹С‚РёСЏ Рѕ Р±РѕСЃСЃР°С… РёР· С‡Р°С‚Р°;
 * - Р·Р°РїСѓСЃРєР°РµС‚ РїРѕРёСЃРє С†РµР»Рё С‡РµСЂРµР· СѓР¶Рµ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёР№ Auto-РљРѕРјРїР°СЃ;
 * - РїРѕСЃР»Рµ РЅР°С…РѕР¶РґРµРЅРёСЏ С†РµР»Рё РІС‹РїРѕР»РЅСЏРµС‚ FastAction В«РЎРІРёС‚РѕРє Р—Р°С‰РёС‚С‹В»;
 * - РїРѕ Р·Р°РІРµСЂС€РµРЅРёСЋ РІРѕР·РІСЂР°С‰Р°РµС‚ РёСЃС…РѕРґРЅС‹Рµ СЃРѕСЃС‚РѕСЏРЅРёСЏ Р°РІС‚Рѕ-С„СѓРЅРєС†РёР№.
 *
 * Р’Р°Р¶РЅС‹Р№ РёРЅРІР°СЂРёР°РЅС‚ СЂРµР°Р»РёР·Р°С†РёРё:
 * - Р·РґРµСЃСЊ РЅРµС‚ РґСѓР±Р»РёСЂРѕРІР°РЅРёСЏ Р»РѕРіРёРєРё РЅР°РІРёРіР°С‚РѕСЂР°/fast-action;
 * - РјРѕРґСѓР»СЊ С‚РѕР»СЊРєРѕ РєРѕРѕСЂРґРёРЅРёСЂСѓРµС‚ РІС‹Р·РѕРІС‹ С‡РµСЂРµР· AutoFunctionsManager.
 */
final class BossAuto {
    private static final String TAG = "AutoFunctionsManager";
    private static final String TRACE_PREFIX = "AUTO_BOSS_TRACE";
    private static final String LOG_CHAIN = "boss_auto";
    private static final String KEY_AUTO_BOSS = "auto_function_auto_boss";
    private static final String PREF_AUTO_BOSS_ASK_TARGET = "auto_boss_ask_target";
    private static final String PREF_AUTO_BOSS_TRACK_CURRENT_WARS = "auto_boss_track_current_wars";
    private static final String PREF_AUTO_BOSS_CLAN_NOTIFY = "auto_boss_clan_notify";
    private static final String PREF_AUTO_BOSS_SELF_CLAN_TOKEN_CACHE = "auto_boss_self_clan_token_cache";
    private static final String PREF_AUTO_BOSS_WAIT_SCROLL_SEC = "auto_boss_wait_scroll_sec";
    private static final String PREF_AUTO_BOSS_SEARCH_TIMEOUT_SEC = "auto_boss_search_timeout_sec";
    private static final String PREF_AUTO_BOSS_WAIT_FIGHT_TIMEOUT_SEC = "auto_boss_wait_fight_timeout_sec";
    private static final String LOCAL_CHAT_MARKER = "<!--AB_LOCAL_CHAT-->";

    private static final Pattern BOSS_EVENT_PATTERN = Pattern.compile(
            "РІРЅРёРјР°РЅРёРµ!\\s*СЃР»СѓС‡Р°Р№РЅРѕРµ СЃРѕР±С‹С‚РёРµ!\\s*РјРѕРЅСЃС‚СЂ\\s*[\"В«]?([^\"В»]+)[\"В»]?\\s*РЅР°РїР°Р» РЅР° РёРіСЂРѕРєР°\\s+([a-zР°-СЏ0-9_\\-]+)\\.",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // РћРЎРќРћР’РќРћР™ РїР°СЂСЃРµСЂ СЃРѕР±С‹С‚РёСЏ Р‘РѕСЃСЃР°: РјР°РєСЃРёРјР°Р»СЊРЅРѕ РіРёР±РєРёР№, СЃ РїРѕРґРґРµСЂР¶РєРѕР№:
    // - РѕРїС†РёРѕРЅР°Р»СЊРЅРѕР№ С‚РѕС‡РєРё РІ РєРѕРЅС†Рµ
    // - Р»СЋР±С‹С… РЅРёРєРѕРІ СЃ Р±СѓРєРІР°РјРё, С†РёС„СЂР°РјРё, СЃРїРµС†СЃРёРјРІРѕР»Р°РјРё
    // - С„РѕСЂРјР°С‚Р° СЃРѕ РІСЂРµРјРµРЅРµРј Рё Р±РµР·
    // - РІР°СЂРёР°РЅС‚РѕРІ: "РЅР°РїР°Р»", "РЅР°РїР°Р»Р°", "РЅР°РїР°Р»Рё"
    // Р—Р°РІРёСЃРёРјРѕСЃС‚СЊ: РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РІ parseBossEvent() РґР»СЏ РґРµС‚РµРєС‚Р° СЃРѕР±С‹С‚РёСЏ
    private static final Pattern BOSS_EVENT_PATTERN_FLEX = Pattern.compile(
            "(?iu)(?:\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s+)?(?:РІРЅРёРјР°РЅРёРµ!\\s*СЃР»СѓС‡Р°Р№РЅРѕРµ\\s+СЃРѕР±С‹С‚РёРµ!\\s*)?РјРѕРЅСЃС‚СЂ\\s*[\"В«]?([^\"В»]+)[\"В»]?\\s*(?:РЅР°РїР°Р»|РЅР°РїР°Р»Р°|РЅР°РїР°Р»Рё)\\s+РЅР°\\s+(?:РёРіСЂРѕРєР°|РёРіСЂРѕРєРѕРІ)?\\s*(.+?)\\s*(?:[.,:;]|$)");
    private static final Pattern CELL_PATTERN = Pattern.compile("\\b\\d{1,4}-\\d{1,5}\\b");
    private static final Pattern FIGHT_FID_IN_LINK_PATTERN = Pattern.compile("fid=([0-9]{1,16})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPAN_NICK_PATTERN = Pattern.compile(
            "<SPAN[^>]+(?:title|alt)=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FIGHT_ALLY_PATTERN = Pattern.compile(
            "([A-Za-zРђ-РЇР°-СЏРЃС‘0-9_\\-]+)\\s*\\[(\\d{1,9})\\s*/\\s*(\\d{1,9})\\]");

    /**
     * Р•РґРёРЅС‹Р№ Р°РґР°РїС‚РµСЂ Р»РѕРіРёСЂРѕРІР°РЅРёСЏ BossAuto:
     * - РїРёС€РµС‚ РІ logcat (РґР»СЏ LogcatFileRecorder),
     * - РґСѓР±Р»РёСЂСѓРµС‚ РІ FileLogger (С†РµРїРѕС‡РєР° `boss_auto`).
     */
    private static final class Log {
        private Log() {
        }

        static int d(String tag, String message) {
            FileLogger.trace(LOG_CHAIN, message);
            return android.util.Log.d(tag, message);
        }

        static int w(String tag, String message) {
            FileLogger.warn(LOG_CHAIN, message);
            return android.util.Log.w(tag, message);
        }

        static int w(String tag, String message, Throwable error) {
            FileLogger.error(LOG_CHAIN, "WARN: " + message, error);
            return android.util.Log.w(tag, message, error);
        }
    }

    private static final int DEFAULT_SEARCH_TIMEOUT_SEC = 6 * 60;
    private static final int DEFAULT_WAIT_BEFORE_SCROLL_SEC = 2;
    private static final int DEFAULT_WAIT_FIGHT_TIMEOUT_SEC = 25;
    private static final long RETURN_TIMEOUT_MS = 2 * 60 * 1000L;
    private static final long EVENT_DEDUP_WINDOW_MS = 20_000L;
    private static final int TARGET_CHAT_ASK_MAX_ATTEMPTS = 5;
    private static final long TARGET_CHAT_ASK_RETRY_MS = 1_500L;
    private static final long TARGET_FIGHT_POLL_INTERVAL_MS = 1_000L;
    /**
     * Р—Р°РґРµСЂР¶РєР° РїРµСЂРµРґ РѕС‚РїСЂР°РІРєРѕР№ РєР»Р°РЅ-СЃРѕРѕР±С‰РµРЅРёСЏ Рѕ СЃРѕР±С‹С‚РёРё Р±РѕСЃСЃР°.
     * РќСѓР¶РЅР° РґР»СЏ:
     * 1. РџСЂРµРґРѕС‚РІСЂР°С‰РµРЅРёСЏ DDoS-Р±Р»РѕРєРёСЂРѕРІРєРё СЃРµСЂРІРµСЂРѕРј (РјРЅРѕРіРѕ РїР°СЂР°Р»Р»РµР»СЊРЅС‹С… Р·Р°РїСЂРѕСЃРѕРІ)
     * 2. Р‘СѓС„РµСЂРёР·Р°С†РёРё РїРѕС‚РѕРєР° Р±С‹СЃС‚СЂС‹С… pinfo/compass Р·Р°РїСЂРѕСЃРѕРІ
     */
    private static final long CLAN_NOTIFY_DELAY_MS = 1000L;
    private static final int CLAN_EVENT_CHAT_MAX_LEN = 220;
    /**
     * Р—Р°РґРµСЂР¶РєР° РјРµР¶РґСѓ clan message Рё private message РґР»СЏ ask target.
     * РџСЂРµРґРѕС‚РІСЂР°С‰Р°РµС‚ РѕС‚РєР»РѕРЅРµРЅРёРµ РѕР±РѕРёС… СЃРѕРѕР±С‰РµРЅРёР№ РєР°Рє DDoS.
     */
    private static final long CLAN_PRIVATE_MESSAGE_DELAY_MS = 500L;
    /**
     * РњР°РєСЃРёРјР°Р»СЊРЅРѕРµ РєРѕР»РёС‡РµСЃС‚РІРѕ С‚РёРєРѕРІ РѕС‚СЃСѓС‚СЃС‚РІРёСЏ fight FID РїРµСЂРµРґ РїСЂРёР·РЅР°РЅРёРµРј Р±РѕСЏ РїРѕС‚РµСЂСЏРЅРЅС‹Рј.
     * Р—Р°С‰РёС‚Р° РѕС‚ Р±РµСЃРєРѕРЅРµС‡РЅРѕРіРѕ incrementing РІ СЃР»СѓС‡Р°Рµ РїРѕС‚РµСЂРё СЃРѕРµРґРёРЅРµРЅРёСЏ РёР»Рё РѕС€РёР±РєРё СЃРµСЂРІРµСЂР°.
     */
    private static final int BOSS_FIGHT_FID_MISSING_MAX_TICKS = 10;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final AutoFunctionsManager owner;

    private final Object lock = new Object();
    private BossStage stage = BossStage.IDLE;
    private BossScenarioSnapshot snapshot;
    private String targetNick = "";
    private String bossName = "";
    private String originRegNum = "";
    private long stageStartedAtMs = 0L;
    private long actionDueAtMs = 0L;
    private long protectionSentAtMs = 0L;
    private int protectionAttempts = 0;
    private int targetAskAttempts = 0;
    private long targetAskNextAttemptAtMs = 0L;
    private String lastEventKey = "";
    private long lastEventAtMs = 0L;
    private String bossFightFid = "";
    private String bossFightLink = "";
    private int bossFightFidMissingTicks = 0;
    private long targetFightPollNextAtMs = 0L;
    private boolean targetFightPollInFlight = false;
    private String targetFightPollNick = "";
    private boolean bossFightLostPending = false;
    private String bossTargetClanToken = "";
    private String bossSelfClanToken = "";
    private String cachedSelfClanToken = "";

    private enum BossStage {
        IDLE,
        SEARCHING_TARGET,
        TARGET_FOUND_WAIT_SCROLL,
        WAIT_FIGHT_START,
        FIGHT_IN_PROGRESS,
        RETURNING_TO_ORIGIN
    }

    /**
     * РЎРЅРёРјРѕРє Р°РІС‚Рѕ-С„СѓРЅРєС†РёР№, РєРѕС‚РѕСЂС‹Рµ СЃС‚Р°РІСЏС‚СЃСЏ РЅР° РїР°СѓР·Сѓ РІРѕ РІСЂРµРјСЏ СЃС†РµРЅР°СЂРёСЏ РђРІС‚Рѕ-Р‘РѕСЃСЃРѕРІ.
     * РђРІС‚Рѕ-Р‘РѕР№/РђРІС‚Рѕ-Р›РµС‡РµРЅРёРµ СЃСЋРґР° РЅРµ РІС…РѕРґСЏС‚: РѕРЅРё РЅРµ РїР°СѓР·СЏС‚СЃСЏ.
     */
    /**
     * РЎРЅРёРјРѕРє РёСЃС…РѕРґРЅРѕРіРѕ СЃРѕСЃС‚РѕСЏРЅРёСЏ Р°РІС‚Рѕ-С„СѓРЅРєС†РёР№ РЅР° РјРѕРјРµРЅС‚ СЃС‚Р°СЂС‚Р° СЃС†РµРЅР°СЂРёСЏ.
     * РќСѓР¶РµРЅ РґР»СЏ РєРѕСЂСЂРµРєС‚РЅРѕРіРѕ РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёСЏ РїРѕСЃР»Рµ Р·Р°РІРµСЂС€РµРЅРёСЏ РїРѕРёСЃРєР°/Р±РѕСЏ.
     */
    /**
     * РЎРЅРёРјРѕРє РїРѕР»СЊР·РѕРІР°С‚РµР»СЊСЃРєРёС… Р°РІС‚Рѕ-РЅР°СЃС‚СЂРѕРµРє РЅР° РјРѕРјРµРЅС‚ СЃС‚Р°СЂС‚Р° СЃС†РµРЅР°СЂРёСЏ.
     *
     * РќСѓР¶РµРЅ РґР»СЏ РєРѕСЂСЂРµРєС‚РЅРѕРіРѕ restore РїРѕСЃР»Рµ Р·Р°РІРµСЂС€РµРЅРёСЏ В«РђРІС‚Рѕ-Р‘РѕСЃСЃР°В».
     * Р’ СЃРЅРёРјРѕРє РЅРµ РІРєР»СЋС‡Р°СЋС‚СЃСЏ Auto-Р‘РѕР№ Рё Auto-Р›РµС‡РµРЅРёРµ вЂ” СЌС‚Рё СЂРµР¶РёРјС‹
     * РїРѕ РїСЂРѕРµРєС‚РЅС‹Рј РїСЂР°РІРёР»Р°Рј РЅРµ СЃС‚Р°РІСЏС‚СЃСЏ РЅР° РїР°СѓР·Сѓ.
     */
    private static final class BossScenarioSnapshot {
        boolean autoFishEnabled;
        boolean autoBaitEnabled;
        boolean autoSkinEnabled;
        boolean autoAttackEnabled;
        boolean autoCompassEnabled;
        boolean autoInvisibleEnabled;
        boolean locationTrackingEnabled;
        boolean autoDetectEnabled;
        boolean autoSummonEnabled;
        boolean autoDrinkEnabled;
        boolean autoMovingEnabled;
        String autoMovingDestination;
        boolean autoTreasureEnabled;
        boolean autoCutEnabled;
        boolean autoRefreshEnabled;

        String autoCompassTargetNick;
        boolean autoCompassHuntMode;
        int autoCompassPollIntervalSec;
        String autoCompassManualCellsCsv;
    }

    private static final class BossEvent {
        final String bossName;
        final String targetNick;

        BossEvent(String bossName, String targetNick) {
            this.bossName = bossName;
            this.targetNick = targetNick;
        }
    }

    private static final class FightAllyState {
        final String nick;
        final int curHp;
        final int maxHp;

        FightAllyState(String nick, int curHp, int maxHp) {
            this.nick = nick;
            this.curHp = curHp;
            this.maxHp = maxHp;
        }
    }

    BossAuto(Context context, SharedPreferences prefs, AutoFunctionsManager owner) {
        this.appContext = context.getApplicationContext();
        this.prefs = prefs;
        this.owner = owner;
        this.cachedSelfClanToken = normalizeClanToken(
                prefs.getString(PREF_AUTO_BOSS_SELF_CLAN_TOKEN_CACHE, "")
        );
    }

    boolean isAutoBossEnabled() {
        return prefs.getBoolean(KEY_AUTO_BOSS, false);
    }

    void toggleAutoBoss() {
        setAutoBossEnabled(!isAutoBossEnabled());
    }

    void setAutoBossEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_BOSS, enabled).apply();
        if (!enabled) {
            stopAndRestore("manual_disable", true);
        } else {
            owner.requestCharacterSyncForAutoFunctionEnableInternal("auto_boss");
            writeBossChat("Р РµР¶РёРј РІРєР»СЋС‡РµРЅ. РћР¶РёРґР°РµРј СЃРёСЃС‚РµРјРЅС‹Рµ СЃРѕРѕР±С‰РµРЅРёСЏ Рѕ Р‘РѕСЃСЃР°С….");
        }
        Log.d(TAG, TRACE_PREFIX + " setAutoBossEnabled=" + enabled);
        owner.syncBackgroundServiceInternal("setAutoBossEnabled(" + enabled + ")");
        owner.requestQuickButtonsRefreshInternal("setAutoBossEnabled(" + enabled + ")");
    }

    void onIncomingChatMessage(String messageHtml) {
        if (isEmpty(messageHtml)) {
            return;
        }
        boolean autoBossEnabled = isAutoBossEnabled();
        if (autoBossEnabled) {
            if (isServerBossEventMessage(messageHtml)) {
                BossEvent event = parseBossEvent(messageHtml);
                if (event != null) {
                    FileLogger.log("[BossAuto.onIncomingChatMessage] Event detected: boss=" + event.bossName + ", target=" + event.targetNick);
                    handleBossEvent(event);
                }
            } else if (looksLikeBossEventText(messageHtml)) {
                String preview = toPlainText(messageHtml);
                if (preview.length() > 220) {
                    preview = preview.substring(0, 220) + "...";
                }
                FileLogger.trace(LOG_CHAIN, "[BOSS_EVENT_SKIPPED_NON_SERVER] " + preview);
                Log.d(TAG, TRACE_PREFIX + " skip boss-event parse: non-server chat source");
            }
        }
        if (!autoBossEnabled) {
            return;
        }

        String plain = toPlainText(messageHtml);
        if (isEmpty(plain)) {
            return;
        }

        if (containsFightCompleted(plain)) {
            BossStage currentStage;
            synchronized (lock) {
                currentStage = stage;
            }
            if (currentStage == BossStage.WAIT_FIGHT_START || currentStage == BossStage.FIGHT_IN_PROGRESS) {
                Log.d(TAG, TRACE_PREFIX + " fight completed message detected");
                startReturnOrRestore("fight_completed_message");
                return;
            }
        }

        String senderNick = extractSenderNick(messageHtml);
        if (isEmpty(senderNick)) {
            return;
        }
        String cellRegNum = extractCellRegNum(plain);
        if (isEmpty(cellRegNum)) {
            return;
        }
        handleCellHintIfMatchesTarget(senderNick, cellRegNum);
    }

    /**
     * РћРїСЂРµРґРµР»СЏРµС‚, С‡С‚Рѕ СЃРѕРѕР±С‰РµРЅРёРµ РѕС‚РЅРѕСЃРёС‚СЃСЏ Рє СЃРёСЃС‚РµРјРЅРѕРјСѓ РєР°РЅР°Р»Сѓ СЃРµСЂРІРµСЂР°, Р° РЅРµ Рє Р»РѕРєР°Р»СЊРЅРѕРјСѓ/РєР»Р°РЅРѕРІРѕРјСѓ С‡Р°С‚Сѓ.
     */
    private boolean isServerBossEventMessage(String messageHtml) {
        if (isEmpty(messageHtml)) {
            return false;
        }
        String lowerHtml = messageHtml.toLowerCase(Locale.ROOT);
        if (messageHtml.contains(LOCAL_CHAT_MARKER)) {
            return false;
        }
        if (lowerHtml.contains("Р°РІС‚Рѕ-Р±РѕСЃСЃС‹") || lowerHtml.contains("[Р°РІС‚Рѕ-Р±РѕСЃСЃС‹]")) {
            return false;
        }
        boolean hasSystemClass = lowerHtml.contains("class=massm") || lowerHtml.contains("class=\"massm\"");
        boolean hasServerSender = lowerHtml.contains("neverlands.ru");
        if (!hasSystemClass || !hasServerSender) {
            return false;
        }
        return looksLikeBossEventText(messageHtml);
    }

    private boolean looksLikeBossEventText(String messageHtml) {
        String plain = toPlainText(messageHtml).toLowerCase(Locale.ROOT);
        return plain.contains("РјРѕРЅСЃС‚СЂ")
                && (plain.contains("РЅР°РїР°Р» РЅР° РёРіСЂРѕРєР°")
                || plain.contains("РЅР°РїР°Р»Р° РЅР° РёРіСЂРѕРєР°")
                || plain.contains("РЅР°РїР°Р»Рё РЅР° РёРіСЂРѕРєР°"));
    }

    void onRoomUsersUpdated(List<String> roomNicks, String roomLocationName) {
        if (!isAutoBossEnabled() || roomNicks == null || roomNicks.isEmpty()) {
            return;
        }
        String target;
        BossStage currentStage;
        synchronized (lock) {
            currentStage = stage;
            target = targetNick;
        }
        if (isEmpty(target) || currentStage != BossStage.SEARCHING_TARGET) {
            return;
        }
        String targetNorm = normalizeNick(target);
        for (String roomNick : roomNicks) {
            if (normalizeNick(roomNick).equalsIgnoreCase(targetNorm)) {
                onTargetFoundInRoom("room_list");
                return;
            }
        }
    }

    void tickAutoBoss() {
        if (!isAutoBossEnabled()) {
            return;
        }
        BossStage currentStage;
        long stageStart;
        long now = System.currentTimeMillis();
        synchronized (lock) {
            currentStage = stage;
            stageStart = stageStartedAtMs;
        }

        switch (currentStage) {
            case SEARCHING_TARGET:
                if (!monitorTargetFightStateDuringSearch()) {
                    return;
                }
                maybeRetryAskTarget(now);
                if (now - stageStart >= getSearchTimeoutMs()) {
                    stopAndRestore("search_timeout", true);
                }
                break;
            case TARGET_FOUND_WAIT_SCROLL:
                if (now >= actionDueAtMs) {
                    sendProtectionScroll();
                }
                break;
            case WAIT_FIGHT_START:
                if (isFightLikelyActive()) {
                    synchronized (lock) {
                        stage = BossStage.FIGHT_IN_PROGRESS;
                        stageStartedAtMs = now;
                    }
                    writeBossChat("Р‘РѕР№ РЅР°С‡Р°Р»СЃСЏ. РџРµСЂРµРґР°РµРј СѓРїСЂР°РІР»РµРЅРёРµ РђРІС‚Рѕ-Р‘РѕСЋ.");
                    return;
                }
                if (now - protectionSentAtMs >= getWaitFightTimeoutMs()) {
                    if (protectionAttempts < 2) {
                        writeBossChat("Р‘РѕР№ РЅРµ СЃС‚Р°СЂС‚РѕРІР°Р», РїРѕРІС‚РѕСЂСЏРµРј В«РЎРІРёС‚РѕРє Р—Р°С‰РёС‚С‹В».");
                        sendProtectionScroll();
                    } else {
                        startReturnOrRestore("fight_not_started");
                    }
                }
                break;
            case FIGHT_IN_PROGRESS:
                if (!isFightLikelyActive() && (now - stageStart) > 8_000L) {
                    startReturnOrRestore("fight_pulse_idle");
                }
                break;
            case RETURNING_TO_ORIGIN:
                processReturnStage(now, stageStart);
                break;
            case IDLE:
            default:
                break;
        }
    }

    /**
     * РћР±СЂР°Р±Р°С‚С‹РІР°РµС‚ С‚СЂРёРіРіРµСЂ СЃРѕР±С‹С‚РёСЏ В«Р‘РѕСЃСЃ РЅР°РїР°Р» РЅР° РёРіСЂРѕРєР°В».
     *
     * РћСЃРЅРѕРІРЅС‹Рµ С€Р°РіРё:
     * - РІС‹РїРѕР»РЅСЏРµС‚ anti-dup РїРѕ СЃРѕР±С‹С‚РёСЋ;
     * - РїСЂРёРјРµРЅСЏРµС‚ С„РёР»СЊС‚СЂС‹ СѓС‡Р°СЃС‚РёСЏ РІ С‚РµРєСѓС‰РёС… РєР»Р°РЅРѕРІС‹С… РІРѕР№РЅР°С…;
     * - С„РёРєСЃРёСЂСѓРµС‚ СЃСЃС‹Р»РєСѓ РЅР° Р±РѕР№ С†РµР»Рё ({@code fid});
     * - СЃС‚Р°РІРёС‚ РЅРµСЃРѕРІРјРµСЃС‚РёРјС‹Рµ Р°РІС‚Рѕ-С„СѓРЅРєС†РёРё РЅР° РїР°СѓР·Сѓ;
     * - Р·Р°РїСѓСЃРєР°РµС‚ РїРѕРёСЃРє С†РµР»Рё С‡РµСЂРµР· Auto-РљРѕРјРїР°СЃ.
     */
    private void handleBossEvent(BossEvent event) {
        String normalizedTarget = normalizeNick(event.targetNick);
        if (isEmpty(normalizedTarget)) {
            return;
        }
        long now = System.currentTimeMillis();
        String dedupKey = normalizeBossKey(event.bossName, normalizedTarget);

        synchronized (lock) {
            if (dedupKey.equals(lastEventKey) && (now - lastEventAtMs) < EVENT_DEDUP_WINDOW_MS) {
                Log.d(TAG, TRACE_PREFIX + " skip duplicate event: key=" + dedupKey);
                return;
            }
            lastEventKey = dedupKey;
            lastEventAtMs = now;
        }

        BossStage currentStage;
        String currentTarget;
        synchronized (lock) {
            currentStage = stage;
            currentTarget = targetNick;
        }
        if (currentStage != BossStage.IDLE) {
            if (normalizeNick(currentTarget).equalsIgnoreCase(normalizedTarget)) {
                Log.d(TAG, TRACE_PREFIX + " scenario already active for target=" + normalizedTarget);
                return;
            }
            Log.d(TAG, TRACE_PREFIX + " new event while active, replace scenario. oldTarget=" + currentTarget
                    + ", newTarget=" + normalizedTarget);
            stopAndRestore("replace_by_new_event", true);
        }

        NeverApi.PinfoCompassSnapshot targetSnapshot = safeGetPinfoSnapshot(normalizedTarget);
        NeverApi.PinfoCompassSnapshot selfSnapshot = safeGetPinfoSnapshot(resolveSelfNick());
        String targetClanToken = normalizeClanToken(targetSnapshot == null ? "" : targetSnapshot.clanToken);
        String selfClanToken = resolveSelfClanTokenWithFallback(selfSnapshot);
        boolean trackCurrentWarsEnabled = isAutoBossTrackCurrentWarsEnabled();

        Log.d(TAG, TRACE_PREFIX + " clan filters: target=" + normalizedTarget
                + ", trackCurrentWars=" + trackCurrentWarsEnabled
                + ", targetClan=" + targetClanToken
                + ", selfClan=" + selfClanToken);
        // РљР»Р°РЅ-РѕРїРѕРІРµС‰РµРЅРёРµ Рѕ СЃР°РјРѕРј СЃРѕР±С‹С‚РёРё РѕС‚РїСЂР°РІР»СЏРµРј СЃСЂР°Р·Сѓ РїРѕСЃР»Рµ СЂР°СЃРїРѕР·РЅР°РІР°РЅРёСЏ СЃРѕР±С‹С‚РёСЏ.
        // Р­С‚Рѕ РЅРµ Р·Р°РІРёСЃРёС‚ РѕС‚ РїРѕСЃР»РµРґСѓСЋС‰РёС… С„РёР»СЊС‚СЂРѕРІ BD/wars.
        String initialFightFid = resolveFightFidReliable(normalizedTarget, targetSnapshot);
        String initialFightLink = buildFightLogLink(initialFightFid);
        sendClanBossEventMessageIfNeeded(event.bossName, normalizedTarget, selfClanToken, initialFightLink);
        String locationLabel = resolveTargetLocationLabel(normalizedTarget, targetSnapshot);
        String targetHtml = buildTargetNickHtml(normalizedTarget, targetSnapshot);
        String locationPrefix = isEmpty(locationLabel) ? "" : " [" + escapeHtml(locationLabel) + "]";
        writeBossChat("РЎРѕР±С‹С‚РёРµ. РњРѕРЅСЃС‚СЂ \"" + escapeHtml(event.bossName) + "\" РЅР°РїР°Р» РЅР° РёРіСЂРѕРєР° "
                + targetHtml + ". Р¦РµР»СЊ " + targetHtml + " РІ " + buildFightWordHtml(initialFightLink) + "."
                + locationPrefix);

        if (trackCurrentWarsEnabled
                && !isEmpty(selfClanToken)
                && ClanWarsManager.getInstance(appContext).isClanTokenInCurrentWars(selfClanToken)) {
            writeBossChat("Р”РІРёР¶РµРЅРёРµ Рє С†РµР»Рё РѕСЃС‚Р°РЅРѕРІР»РµРЅРѕ вЂ” РЅР°С€ РїРµСЂСЃРѕРЅР°Р¶ СѓС‡Р°СЃС‚РІСѓРµС‚ РІ С‚РµРєСѓС‰РµР№ РєР»Р°РЅРѕРІРѕР№ РІРѕР№РЅРµ.");
            Log.d(TAG, TRACE_PREFIX + " wars filter denied by self clan: selfClan=" + selfClanToken
                    + ", target=" + normalizedTarget);
            return;
        }

        if (trackCurrentWarsEnabled
                && !isEmpty(targetClanToken)
                && ClanWarsManager.getInstance(appContext).isClanTokenInCurrentWars(targetClanToken)) {
            String deniedTargetHtml = buildTargetNickHtml(normalizedTarget, targetSnapshot);
            askTargetOnceIfEnabled(normalizedTarget);
            writeBossChat("Р”РІРёР¶РµРЅРёРµ Рє С†РµР»Рё РѕСЃС‚Р°РЅРѕРІР»РµРЅРѕ вЂ” С†РµР»СЊ " + deniedTargetHtml
                    + " СЃРѕСЃС‚РѕРёС‚ РІ РєР»Р°РЅРµ, СѓС‡Р°СЃС‚РІСѓСЋС‰РµРј РІ С‚РµРєСѓС‰РµР№ РєР»Р°РЅРѕРІРѕР№ РІРѕР№РЅРµ.");
            sendClanBossWarDeniedMessageIfNeeded(selfClanToken);
            Log.d(TAG, TRACE_PREFIX + " wars filter denied by wars list: target=" + normalizedTarget
                    + ", targetClan=" + targetClanToken);
            return;
        }
        if (trackCurrentWarsEnabled) {
            Log.d(TAG, TRACE_PREFIX + " wars filter passed: target=" + normalizedTarget
                    + ", targetClan=" + targetClanToken);
        } else {
            Log.d(TAG, TRACE_PREFIX + " wars filter disabled by settings: target=" + normalizedTarget);
        }

        BossScenarioSnapshot newSnapshot = captureSnapshot();
        pauseNonCombatFunctions();

        synchronized (lock) {
            snapshot = newSnapshot;
            targetNick = normalizedTarget;
            bossName = safeTrim(event.bossName);
            originRegNum = currentMapRegNum();
            bossFightFid = initialFightFid;
            bossFightLink = initialFightLink;
            bossFightFidMissingTicks = 0;
            targetFightPollNextAtMs = 0L;
            targetFightPollInFlight = false;
            targetFightPollNick = "";
            bossFightLostPending = false;
            bossTargetClanToken = targetClanToken;
            bossSelfClanToken = selfClanToken;
            stage = BossStage.SEARCHING_TARGET;
            stageStartedAtMs = now;
            actionDueAtMs = 0L;
            protectionSentAtMs = 0L;
            protectionAttempts = 0;
            targetAskAttempts = 0;
            targetAskNextAttemptAtMs = 0L;
        }

        writeBossChat("Р—Р°РїСѓСЃРєР°РµРј РїРѕРёСЃРє С†РµР»Рё.");
        if (isAutoBossAskTargetEnabled()) {
            synchronized (lock) {
                targetAskAttempts = 0;
                targetAskNextAttemptAtMs = now;
            }
            maybeRetryAskTarget(now);
        }
        owner.startSettingsCompassTargetSearch(normalizedTarget, "auto_boss_event");
    }

    private void handleCellHintIfMatchesTarget(String senderNick, String cellRegNum) {
        String normalizedSender = normalizeNick(senderNick);
        if (isEmpty(normalizedSender)) {
            return;
        }
        String target;
        BossStage currentStage;
        synchronized (lock) {
            target = targetNick;
            currentStage = stage;
        }
        if (currentStage != BossStage.SEARCHING_TARGET || isEmpty(target)) {
            return;
        }
        if (!normalizeNick(target).equalsIgnoreCase(normalizedSender)) {
            return;
        }

        Log.d(TAG, TRACE_PREFIX + " target cell hint from chat: sender=" + normalizedSender + ", cell=" + cellRegNum);
        writeBossChat("РџРѕР»СѓС‡РµРЅ РѕС‚РІРµС‚ С†РµР»Рё: РєР»РµС‚РєР° " + cellRegNum + ". РџРµСЂРµСЃС‚СЂР°РёРІР°РµРј РїРѕРёСЃРє.");
        owner.setAutoCompassManualCellsCsv(cellRegNum);
        owner.startSettingsCompassTargetSearch(target, "auto_boss_reply_cell_hint");
    }

    private void onTargetFoundInRoom(String source) {
        synchronized (lock) {
            if (stage != BossStage.SEARCHING_TARGET) {
                return;
            }
            stage = BossStage.TARGET_FOUND_WAIT_SCROLL;
            stageStartedAtMs = System.currentTimeMillis();
            actionDueAtMs = stageStartedAtMs + getWaitBeforeScrollMs();
        }
        if (owner.isAutoCompassEnabled()) {
            owner.setAutoCompassEnabled(false);
        }
        if (AppVars.AutoMoving) {
            owner.stopAutoMoving();
        }
        sendClanBossFoundMessageIfNeeded();
        String targetHtml = buildTargetNickHtml(targetNick, null);
        writeBossChat("Р¦РµР»СЊ РЅР°Р№РґРµРЅР° (" + source + "): "
                + targetHtml + ". Р“РѕС‚РѕРІРёРј В«РЎРІРёС‚РѕРє Р—Р°С‰РёС‚С‹В».");
    }

    private void sendProtectionScroll() {
        String target;
        synchronized (lock) {
            if (stage != BossStage.TARGET_FOUND_WAIT_SCROLL && stage != BossStage.WAIT_FIGHT_START) {
                return;
            }
            target = targetNick;
        }
        if (isEmpty(target)) {
            stopAndRestore("empty_target_before_scroll", true);
            return;
        }

        String castTarget = resolveProtectionTargetNick(target);
        if (isEmpty(castTarget)) {
            stopAndRestore("no_alive_protection_target", true);
            return;
        }

        long sentAt = System.currentTimeMillis();
        synchronized (lock) {
            if (stage != BossStage.TARGET_FOUND_WAIT_SCROLL && stage != BossStage.WAIT_FIGHT_START) {
                return;
            }
            protectionAttempts++;
            protectionSentAtMs = sentAt;
            stage = BossStage.WAIT_FIGHT_START;
            stageStartedAtMs = sentAt;
        }

        String targetHtml = buildTargetNickHtml(castTarget, null);
        StringBuilder builder = new StringBuilder();
        builder.append(MainPhp.buildServerChatTimeHtmlExternal());
        builder.append("<font color=#7E57C2><b>[РђРІС‚Рѕ-Р‘РѕСЃСЃС‹]</b></font> ");
        builder.append("РСЃРїРѕР»СЊР·СѓРµРј В«РЎРІРёС‚РѕРє Р—Р°С‰РёС‚С‹В» РЅР° ");
        builder.append(targetHtml);
        builder.append(".");
        FastActionManager.writeChatMsg(builder.toString());
        FastActionManager.fastAttackZas(castTarget);
        Log.d(TAG, TRACE_PREFIX + " protection scroll sent: target=" + castTarget
                + ", attempts=" + protectionAttempts);
    }

    /**
     * Р—Р°РїСѓСЃРєР°РµС‚ РІРѕР·РІСЂР°С‚ РЅР° РёСЃС…РѕРґРЅСѓСЋ РєР»РµС‚РєСѓ Р»РёР±Рѕ СЃСЂР°Р·Сѓ Р·Р°РІРµСЂС€Р°РµС‚ СЃС†РµРЅР°СЂРёР№,
     * РµСЃР»Рё РїРµСЂСЃРѕРЅР°Р¶ СѓР¶Рµ РЅР° РёСЃС…РѕРґРЅРѕР№ РєР»РµС‚РєРµ.
     */
    private void startReturnOrRestore(String reason) {
        String origin;
        synchronized (lock) {
            origin = originRegNum;
        }
        String current = currentMapRegNum();
        if (!isEmpty(origin) && !origin.equals(current)) {
            owner.setAutoCompassEnabled(false);
            owner.startAutoMoving(origin);
            synchronized (lock) {
                stage = BossStage.RETURNING_TO_ORIGIN;
                stageStartedAtMs = System.currentTimeMillis();
            }
            writeBossChat("Р‘РѕР№ Р·Р°РІРµСЂС€РµРЅ, РІРѕР·РІСЂР°С‰Р°РµРјСЃСЏ РЅР° РёСЃС…РѕРґРЅСѓСЋ РєР»РµС‚РєСѓ " + origin + ".");
            Log.d(TAG, TRACE_PREFIX + " return to origin started: reason=" + reason + ", origin=" + origin);
            return;
        }
        stopAndRestore(reason, true);
    }

    private void processReturnStage(long now, long stageStartMs) {
        String origin;
        synchronized (lock) {
            origin = originRegNum;
        }
        if (isEmpty(origin)) {
            stopAndRestore("return_stage_no_origin", true);
            return;
        }
        String current = currentMapRegNum();
        if (origin.equals(current) && !AppVars.AutoMoving) {
            stopAndRestore("return_completed", true);
            return;
        }
        if (!AppVars.AutoMoving && !origin.equals(current)) {
            owner.startAutoMoving(origin);
        }
        if (now - stageStartMs >= RETURN_TIMEOUT_MS) {
            stopAndRestore("return_timeout", true);
        }
    }

    /**
     * Р•РґРёРЅР°СЏ С‚РѕС‡РєР° Р·Р°РІРµСЂС€РµРЅРёСЏ СЃС†РµРЅР°СЂРёСЏ РђРІС‚Рѕ-Р‘РѕСЃСЃР°.
     *
     * Р—РґРµСЃСЊ РѕС‡РёС‰Р°РµС‚СЃСЏ РІРЅСѓС‚СЂРµРЅРЅРµРµ СЃРѕСЃС‚РѕСЏРЅРёРµ Рё, РїСЂРё РЅРµРѕР±С…РѕРґРёРјРѕСЃС‚Рё,
     * РІРѕСЃСЃС‚Р°РЅР°РІР»РёРІР°РµС‚СЃСЏ СЃРЅРёРјРѕРє СЂР°РЅРµРµ Р°РєС‚РёРІРЅС‹С… Р°РІС‚Рѕ-С„СѓРЅРєС†РёР№.
     */
    private void stopAndRestore(String reason, boolean restoreSnapshot) {
        BossScenarioSnapshot snapshotToRestore = null;
        String oldTarget;
        synchronized (lock) {
            if (restoreSnapshot) {
                snapshotToRestore = snapshot;
            }
            oldTarget = targetNick;
            snapshot = null;
            stage = BossStage.IDLE;
            targetNick = "";
            bossName = "";
            originRegNum = "";
            stageStartedAtMs = 0L;
            actionDueAtMs = 0L;
            protectionSentAtMs = 0L;
            protectionAttempts = 0;
            targetAskAttempts = 0;
            targetAskNextAttemptAtMs = 0L;
            bossFightFid = "";
            bossFightLink = "";
            bossFightFidMissingTicks = 0;
            targetFightPollNextAtMs = 0L;
            targetFightPollInFlight = false;
            targetFightPollNick = "";
            bossFightLostPending = false;
            bossTargetClanToken = "";
            bossSelfClanToken = "";
        }
        if (owner.isAutoCompassEnabled()) {
            owner.setAutoCompassEnabled(false);
        }
        if (snapshotToRestore != null) {
            restoreSnapshot(snapshotToRestore);
        }
        if (!isEmpty(oldTarget)) {
            String targetHtml = buildTargetNickHtml(oldTarget, null);
            writeBossChat("РЎС†РµРЅР°СЂРёР№ Р·Р°РІРµСЂС€РµРЅ (" + reason + ") РґР»СЏ С†РµР»Рё "
                    + targetHtml + ".");
        } else {
            writeBossChat("РЎС†РµРЅР°СЂРёР№ Р·Р°РІРµСЂС€РµРЅ (" + reason + ").");
        }
        Log.d(TAG, TRACE_PREFIX + " scenario stopped: reason=" + reason);
    }

    /**
     * РЎРѕС…СЂР°РЅСЏРµС‚ С‚РµРєСѓС‰РёРµ С„Р»Р°РіРё Рё РїР°СЂР°РјРµС‚СЂС‹ Р°РІС‚Рѕ-С„СѓРЅРєС†РёР№ РїРµСЂРµРґ СЃС‚Р°СЂС‚РѕРј СЃС†РµРЅР°СЂРёСЏ.
     */
    private BossScenarioSnapshot captureSnapshot() {
        BossScenarioSnapshot snapshot = new BossScenarioSnapshot();
        snapshot.autoFishEnabled = owner.isAutoFishEnabled();
        snapshot.autoBaitEnabled = owner.isAutoBaitEnabled();
        snapshot.autoSkinEnabled = owner.isAutoSkinEnabled();
        snapshot.autoAttackEnabled = owner.isAutoAttackEnabled();
        snapshot.autoCompassEnabled = owner.isAutoCompassEnabled();
        snapshot.autoInvisibleEnabled = owner.isAutoInvisibleEnabled();
        snapshot.locationTrackingEnabled = owner.isLocationTrackingEnabled();
        snapshot.autoDetectEnabled = owner.isAutoDetectEnabled();
        snapshot.autoSummonEnabled = owner.isAutoSummonEnabled();
        snapshot.autoDrinkEnabled = owner.isAutoDrinkEnabled();
        snapshot.autoMovingEnabled = owner.isAutoMovingEnabled();
        snapshot.autoMovingDestination = AppVars.AutoMovingDestinaton == null ? "" : AppVars.AutoMovingDestinaton.trim();
        snapshot.autoTreasureEnabled = owner.isAutoTreasureEnabled();
        snapshot.autoCutEnabled = owner.isAutoCutEnabled();
        snapshot.autoRefreshEnabled = owner.isAutoRefreshEnabled();

        snapshot.autoCompassTargetNick = owner.getAutoCompassTargetNick();
        snapshot.autoCompassHuntMode = owner.isAutoCompassHuntMode();
        snapshot.autoCompassPollIntervalSec = owner.getAutoCompassPollIntervalSec();
        snapshot.autoCompassManualCellsCsv = owner.getAutoCompassManualCellsCsv();
        return snapshot;
    }

    /**
     * РЎС‚Р°РІРёС‚ РЅР° РїР°СѓР·Сѓ Р°РІС‚Рѕ-С„СѓРЅРєС†РёРё, РєРѕС‚РѕСЂС‹Рµ РјРѕРіСѓС‚ РєРѕРЅС„Р»РёРєС‚РѕРІР°С‚СЊ СЃ РїРѕРёСЃРєРѕРј/РјР°СЂС€СЂСѓС‚РѕРј РђРІС‚Рѕ-Р‘РѕСЃСЃР°.
     *
     * Р’Р°Р¶РЅРѕ:
     * - РђРІС‚Рѕ-Р‘РѕР№ Рё РђРІС‚Рѕ-Р›РµС‡РµРЅРёРµ Р·РґРµСЃСЊ РЅРµ РІС‹РєР»СЋС‡Р°СЋС‚СЃСЏ СЃРїРµС†РёР°Р»СЊРЅРѕ.
     */
    private void pauseNonCombatFunctions() {
        owner.setAutoFishEnabled(false);
        owner.setAutoBaitEnabled(false);
        owner.setAutoSkinEnabled(false);
        owner.setAutoAttackEnabled(false);
        owner.setAutoCompassEnabled(false);
        owner.setAutoInvisibleEnabled(false);
        owner.setLocationTrackingEnabled(false);
        owner.setAutoDetectEnabled(false);
        owner.setAutoSummonEnabled(false);
        owner.setAutoDrinkEnabled(false);
        owner.stopAutoMoving();
        owner.setAutoTreasureEnabled(false);
        owner.setAutoCutEnabled(false);
        owner.setAutoRefreshEnabled(false);
    }

    /**
     * Р’РѕСЃСЃС‚Р°РЅР°РІР»РёРІР°РµС‚ РёСЃС…РѕРґРЅС‹Рµ СЃРѕСЃС‚РѕСЏРЅРёСЏ Р°РІС‚Рѕ-С„СѓРЅРєС†РёР№ РїРѕСЃР»Рµ СЃС†РµРЅР°СЂРёСЏ.
     * Р’СЃРµ РїРµСЂРµРєР»СЋС‡РµРЅРёСЏ РґРµР»Р°СЋС‚СЃСЏ С‚РѕР»СЊРєРѕ С‡РµСЂРµР· AutoFunctionsManager, С‡С‚РѕР±С‹
     * РЅРµ РґСѓР±Р»РёСЂРѕРІР°С‚СЊ side-effects Рё СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёРµ guard-РІРµС‚РєРё.
     */
    private void restoreSnapshot(BossScenarioSnapshot snapshot) {
        // Р’РѕСЃСЃС‚Р°РЅР°РІР»РёРІР°РµРј РІ С‚РѕРј Р¶Рµ РјРѕРґСѓР»Рµ Рё С‡РµСЂРµР· С‚Рµ Р¶Рµ setter-С‹, С‡С‚РѕР±С‹ РЅРµ РїР»РѕРґРёС‚СЊ РґСѓР±Р»СЊ-Р»РѕРіРёРєРё.
        owner.setAutoFishEnabled(snapshot.autoFishEnabled);
        owner.setAutoBaitEnabled(snapshot.autoBaitEnabled);
        owner.setAutoSkinEnabled(snapshot.autoSkinEnabled);
        owner.setAutoAttackEnabled(snapshot.autoAttackEnabled);
        owner.setAutoInvisibleEnabled(snapshot.autoInvisibleEnabled);
        owner.setLocationTrackingEnabled(snapshot.locationTrackingEnabled);
        owner.setAutoDetectEnabled(snapshot.autoDetectEnabled);
        owner.setAutoSummonEnabled(snapshot.autoSummonEnabled);
        owner.setAutoDrinkEnabled(snapshot.autoDrinkEnabled);
        owner.setAutoTreasureEnabled(snapshot.autoTreasureEnabled);
        owner.setAutoCutEnabled(snapshot.autoCutEnabled);
        owner.setAutoRefreshEnabled(snapshot.autoRefreshEnabled);

        owner.setAutoCompassTargetNick(snapshot.autoCompassTargetNick);
        owner.setAutoCompassHuntMode(snapshot.autoCompassHuntMode);
        owner.setAutoCompassPollIntervalSec(snapshot.autoCompassPollIntervalSec);
        owner.setAutoCompassManualCellsCsv(snapshot.autoCompassManualCellsCsv);
        owner.setAutoCompassEnabled(snapshot.autoCompassEnabled);

        if (snapshot.autoMovingEnabled && !isEmpty(snapshot.autoMovingDestination)) {
            owner.startAutoMoving(snapshot.autoMovingDestination);
        } else {
            owner.stopAutoMoving();
        }
    }

    private BossEvent parseBossEvent(String messageHtml) {
        String plain = toPlainText(messageHtml);
        if (isEmpty(plain)) {
            return null;
        }
        Matcher matcher = BOSS_EVENT_PATTERN_FLEX.matcher(plain);
        if (!matcher.find()) {
            String lower = plain.toLowerCase(Locale.ROOT);
            boolean bossLikeMessage = lower.contains("СЃР»СѓС‡Р°Р№РЅРѕРµ СЃРѕР±С‹С‚РёРµ")
                    || (lower.contains("РјРѕРЅСЃС‚СЂ") && lower.contains("РЅР°РїР°Р» РЅР° РёРіСЂРѕРєР°"));
            if (bossLikeMessage) {
                String snippet = plain.length() > 280 ? plain.substring(0, 280) + "..." : plain;
                Log.d(TAG, TRACE_PREFIX + " parse miss boss-event: " + snippet);
            }
            return null;
        }
        String boss = safeTrim(matcher.group(1));
        String rawTarget = safeTrim(matcher.group(2));
        String target = normalizeBossTargetNick(rawTarget);
        if (isEmpty(target)) {
            return null;
        }
        Log.d(TAG, TRACE_PREFIX + " parse boss-event: rawTarget=" + rawTarget
                + ", normalizedTarget=" + target + ", boss=" + boss);
        return new BossEvent(boss, target);
    }

    /**
     * РќРѕСЂРјР°Р»РёР·Р°С†РёСЏ РЅРёРєР° С†РµР»Рё РёР· СЃРёСЃС‚РµРјРЅРѕРіРѕ СЃРѕРѕР±С‰РµРЅРёСЏ Рѕ Р‘РѕСЃСЃРµ.
     * РЈРґР°Р»СЏРµС‚ СЃР»СѓР¶РµР±РЅСѓСЋ РїСѓРЅРєС‚СѓР°С†РёСЋ РІ С…РІРѕСЃС‚Рµ Рё РѕСЃС‚Р°РІР»СЏРµС‚ РёСЃС…РѕРґРЅС‹Рµ СЃРїРµС†СЃРёРјРІРѕР»С‹ РЅРёРєР°.
     */
    private String normalizeBossTargetNick(String rawNick) {
        String value = normalizeNick(rawNick);
        value = value.replaceAll("\\s*\\[\\s*\\d{1,3}\\s*]$", "").trim();
        while (!value.isEmpty()) {
            char tail = value.charAt(value.length() - 1);
            if (tail == '.' || tail == ',' || tail == ':' || tail == ';') {
                value = value.substring(0, value.length() - 1).trim();
                continue;
            }
            break;
        }
        return normalizeNick(value);
    }

    private String extractSenderNick(String messageHtml) {
        if (isEmpty(messageHtml)) {
            return "";
        }
        Matcher matcher = SPAN_NICK_PATTERN.matcher(messageHtml);
        if (!matcher.find()) {
            return "";
        }
        String raw = safeTrim(matcher.group(1));
        while (raw.startsWith("%")) {
            raw = raw.substring(1);
        }
        return normalizeNick(raw);
    }

    private String resolveTargetLocationLabel(String targetNick, NeverApi.PinfoCompassSnapshot snapshot) {
        if (snapshot != null && !isEmpty(snapshot.locationName)) {
            if (!isEmpty(snapshot.locationRegion)) {
                return snapshot.locationRegion + " [" + snapshot.locationName + "]";
            }
            return snapshot.locationName;
        }
        if (isEmpty(targetNick)) {
            return "";
        }
        try {
            AutoFunctionsManager.CompassLocationResolveResult resolved = owner.resolveAutoCompassLocation(targetNick);
            if (resolved != null && resolved.success && !isEmpty(resolved.locationLabel)) {
                return resolved.locationLabel;
            }
        } catch (Exception e) {
            Log.w(TAG, TRACE_PREFIX + " resolveTargetLocationLabel failed: target=" + targetNick, e);
        }
        return owner.getAutoCompassLastLocationLabel();
    }

    /**
     * Р‘РµР·РѕРїР°СЃРЅРѕРµ С‡С‚РµРЅРёРµ pinfo-СЃРЅРёРјРєР°.
     * РћС€РёР±РєРё СЃРµС‚Рё Р»РѕРіРёСЂСѓСЋС‚СЃСЏ, РЅРѕ РЅРµ РїСЂРµСЂС‹РІР°СЋС‚ state-machine СЃС†РµРЅР°СЂРёСЏ.
     */
    private NeverApi.PinfoCompassSnapshot safeGetPinfoSnapshot(String nick) {
        if (isEmpty(nick)) {
            return null;
        }
        try {
            return NeverApi.getPinfoCompassSnapshot(nick);
        } catch (Exception e) {
            Log.w(TAG, TRACE_PREFIX + " snapshot request failed: nick=" + nick, e);
            return null;
        }
    }

    /**
     * Р’РѕР·РІСЂР°С‰Р°РµС‚ РјР°РєСЃРёРјР°Р»СЊРЅРѕ РЅР°РґС‘Р¶РЅС‹Р№ {@code fid} Р±РѕСЏ С†РµР»Рё.
     *
     * РСЃС‚РѕС‡РЅРёРєРё РїРѕ РїСЂРёРѕСЂРёС‚РµС‚Сѓ:
     * 1) СѓР¶Рµ РїРѕР»СѓС‡РµРЅРЅС‹Р№ pinfo snapshot;
     * 2) fallback С‡РµСЂРµР· NeverApi.getAll(targetNick).
     */
    private String resolveFightFidReliable(String targetNick, NeverApi.PinfoCompassSnapshot snapshot) {
        String fromSnapshot = normalizeFightFid(snapshot == null ? "" : snapshot.fightFid);
        if (!isEmpty(fromSnapshot)) {
            return fromSnapshot;
        }
        if (isEmpty(targetNick)) {
            return "";
        }
        try {
            NeverApi.UserInfo info = NeverApi.getAll(targetNick);
            return normalizeFightFid(info == null ? "" : info.fightLog);
        } catch (Exception e) {
            Log.w(TAG, TRACE_PREFIX + " failed to resolve fight fid via getAll: target=" + targetNick, e);
            return "";
        }
    }

    private String resolveSelfNick() {
        if (AppVars.Profile == null || AppVars.Profile.UserNick == null) {
            return "";
        }
        return normalizeNick(AppVars.Profile.UserNick);
    }

    private String normalizeClanToken(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').trim();
    }

    /**
     * Р’РѕР·РІСЂР°С‰Р°РµС‚ clanToken РЅР°С€РµРіРѕ РїСЂРѕС„РёР»СЏ СЃ fallback РЅР° РєСЌС€.
     *
     * Р—Р°С‡РµРј РЅСѓР¶РµРЅ fallback:
     * - `pinfo` РёРЅРѕРіРґР° РєСЂР°С‚РєРѕРІСЂРµРјРµРЅРЅРѕ РЅРµРґРѕСЃС‚СѓРїРµРЅ/РЅРµРїР°СЂСЃРёС‚СЃСЏ РІ РјРѕРјРµРЅС‚ СЃРѕР±С‹С‚РёСЏ Р‘РѕСЃСЃР°;
     * - Р±РµР· fallback РєР»Р°РЅ-СѓРІРµРґРѕРјР»РµРЅРёСЏ `%clan%` РѕС€РёР±РѕС‡РЅРѕ РїСЂРѕРїСѓСЃРєР°СЋС‚СЃСЏ РєР°Рє "РІРЅРµ РєР»Р°РЅР°".
     *
     * РџСЂР°РІРёР»Рѕ:
     * - РµСЃР»Рё live-token РїРѕР»СѓС‡РµРЅ, РѕР±РЅРѕРІР»СЏРµРј runtime+prefs РєСЌС€;
     * - РµСЃР»Рё live-token РїСѓСЃС‚РѕР№, РёСЃРїРѕР»СЊР·СѓРµРј РїРѕСЃР»РµРґРЅРёР№ РІР°Р»РёРґРЅС‹Р№ РєСЌС€.
     */
    private String resolveSelfClanTokenWithFallback(NeverApi.PinfoCompassSnapshot selfSnapshot) {
        String liveToken = normalizeClanToken(selfSnapshot == null ? "" : selfSnapshot.clanToken);
        if (!isEmpty(liveToken)) {
            if (!liveToken.equals(cachedSelfClanToken)) {
                cachedSelfClanToken = liveToken;
                prefs.edit().putString(PREF_AUTO_BOSS_SELF_CLAN_TOKEN_CACHE, liveToken).apply();
                Log.d(TAG, TRACE_PREFIX + " self clan token cache update: " + liveToken);
            }
            return liveToken;
        }

        String cached = normalizeClanToken(cachedSelfClanToken);
        if (!isEmpty(cached)) {
            Log.d(TAG, TRACE_PREFIX + " self clan token fallback from runtime cache: " + cached);
            return cached;
        }

        String persistentCache = normalizeClanToken(
                prefs.getString(PREF_AUTO_BOSS_SELF_CLAN_TOKEN_CACHE, "")
        );
        if (!isEmpty(persistentCache)) {
            cachedSelfClanToken = persistentCache;
            Log.d(TAG, TRACE_PREFIX + " self clan token fallback from prefs cache: " + persistentCache);
            return persistentCache;
        }
        return "";
    }

    private String normalizeFightFid(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.isEmpty() || "0".equals(text) || "null".equalsIgnoreCase(text)) {
            return "";
        }
        Matcher digits = Pattern.compile("(\\d{1,16})").matcher(text);
        return digits.find() ? digits.group(1) : text;
    }

    private String buildFightLogLink(String fid) {
        String safeFid = normalizeFightFid(fid);
        if (isEmpty(safeFid)) {
            return "";
        }
        return "http://neverlands.ru/logs.fcg?fid=" + safeFid;
    }

    private String buildFightWordHtml(String fightLink) {
        if (isEmpty(fightLink)) {
            return "Р±РѕСЋ";
        }
        return "<a href=" + escapeHtml(fightLink) + ">Р±РѕСЋ</a>";
    }
    /**
     * РќРѕСЂРјР°Р»РёР·СѓРµС‚ С‡Р°СЃС‚СЊ "РІ Р±РѕСЋ" РґР»СЏ server-payload (`%clan%`) Р±РµР· HTML.
     *
     * РџСЂРёС‡РёРЅР°:
     * - `<a href=...>` РІРЅСѓС‚СЂРё payload РєР»Р°РЅ-С‡Р°С‚Р° СЃРµСЂРІРµСЂ РјРѕР¶РµС‚ РѕС‚С„РёР»СЊС‚СЂРѕРІР°С‚СЊ/РёСЃРєР°Р·РёС‚СЊ;
     * - С„РѕСЂРјР°С‚ `[[[fid]]]` СѓР¶Рµ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РІ РїСЂРѕРµРєС‚Рµ Рё Р±РµР·РѕРїР°СЃРЅРѕ РїСЂРµРІСЂР°С‰Р°РµС‚СЃСЏ РІ СЃСЃС‹Р»РєСѓ
     *   РїСЂРё РѕС‚РѕР±СЂР°Р¶РµРЅРёРё РІС…РѕРґСЏС‰РµРіРѕ СЃРѕРѕР±С‰РµРЅРёСЏ.
     */
    private String normalizeClanFightPartForSend(String fightPart) {
        String safe = safeTrim(fightPart);
        if (isEmpty(safe)) {
            return "\u0432 \u0431\u043e\u044e";
        }
        String lower = safe.toLowerCase(Locale.ROOT);
        if (!lower.contains("<a")) {
            return safe;
        }
        Matcher matcher = FIGHT_FID_IN_LINK_PATTERN.matcher(safe);
        if (matcher.find()) {
            String fid = normalizeFightFid(matcher.group(1));
            if (!isEmpty(fid)) {
                return "\u0432 \u0431\u043e\u044e [[[" + fid + "]]]";
            }
        }
        return "\u0432 \u0431\u043e\u044e";
    }

    private String getCurrentFightWordHtml() {
        String link;
        synchronized (lock) {
            link = bossFightLink;
        }
        return buildFightWordHtml(link);
    }

    /**
     * Р•РґРёРЅС‹Р№ СЂРµРЅРґРµСЂ РЅРёРєР° РґР»СЏ СЃРѕРѕР±С‰РµРЅРёР№ РђРІС‚Рѕ-Р‘РѕСЃСЃР°.
     *
     * РџСЂРёРѕСЂРёС‚РµС‚:
     * 1) РіРѕС‚РѕРІС‹Р№ СЂРµРЅРґРµСЂ РёР· {@link RoomManager#buildUnifiedChatNickHtml(String)};
     * 2) fallback-СЃР±РѕСЂРєР° РїРѕ РґР°РЅРЅС‹Рј pinfo/РєРѕРЅС‚Р°РєС‚РѕРІ (РµСЃР»Рё room-СЂРµРЅРґРµСЂ РЅРµРґРѕСЃС‚СѓРїРµРЅ).
     */
    private String buildTargetNickHtml(String nick, NeverApi.PinfoCompassSnapshot snapshot) {
        String cleanNick = normalizeNick(nick);
        if (isEmpty(cleanNick)) {
            return "";
        }
        String rendered = RoomManager.buildUnifiedChatNickHtml(cleanNick);
        if (!isEmpty(rendered)) {
            return rendered;
        }
        NeverApi.PinfoCompassSnapshot resolvedSnapshot = snapshot != null ? snapshot : safeGetPinfoSnapshot(cleanNick);
        int level = 0;
        if (resolvedSnapshot != null && resolvedSnapshot.level != null && resolvedSnapshot.level > 0) {
            level = resolvedSnapshot.level;
        } else {
            level = ContactsManager.getLevelOfContact(cleanNick);
        }
        int classId = parseClassIdSafe(ContactsManager.getClassIdOfContact(cleanNick));
        String color = "#000000";
        if (classId == 1) {
            color = "#FF0000";
        } else if (classId == 2) {
            color = "#008000";
        }
        String escapedNick = escapeHtml(cleanNick);
        String nickForJs = escapeJsSingleQuoted(cleanNick);
        String levelHtml = level > 0
                ? " [<font class=nickname color=\"" + color + "\">" + level + "</font>]"
                : "";
        return "<a href=\"#\" onclick=\"top.say_private('" + nickForJs
                + "');\"><img src=http://image.neverlands.ru/chat/private.gif width=11 height=12 border=0 align=absmiddle></a>&nbsp;"
                + "<a class=\"activenick\" href=\"#\" onclick=\"top.say_to('" + nickForJs
                + "');\"><font class=nickname color=\"" + color + "\"><b>"
                + escapedNick + "</b></font></a>"
                + levelHtml
                + "<a href=\"http://neverlands.ru/pinfo.cgi?" + escapedNick
                + "\" onclick=\"window.open(this.href);\"><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>";
    }

    /**
     * РџРѕРІС‚РѕСЂСЏРµС‚ РІРѕРїСЂРѕСЃ С†РµР»Рё РІ С‡Р°С‚ Рѕ С‚РµРєСѓС‰РµР№ РєР»РµС‚РєРµ.
     *
     * РћРіСЂР°РЅРёС‡РµРЅРёСЏ:
     * - РЅРµ Р±РѕР»РµРµ {@code TARGET_CHAT_ASK_MAX_ATTEMPTS};
     * - СЃРѕР±Р»СЋРґР°РµС‚СЃСЏ РїР°СѓР·Р° {@code TARGET_CHAT_ASK_RETRY_MS};
     * - РѕС‚РїСЂР°РІРєР° РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РєРѕРіРґР° chat-frame РіРѕС‚РѕРІ.
     */
    private void maybeRetryAskTarget(long now) {
        String target;
        int attempts;
        long nextAttemptAt;
        BossStage currentStage;
        synchronized (lock) {
            currentStage = stage;
            target = targetNick;
            attempts = targetAskAttempts;
            nextAttemptAt = targetAskNextAttemptAtMs;
        }
        if (currentStage != BossStage.SEARCHING_TARGET || isEmpty(target)) {
            return;
        }
        if (attempts >= TARGET_CHAT_ASK_MAX_ATTEMPTS || nextAttemptAt <= 0L || now < nextAttemptAt) {
            return;
        }

        if (!isChatSendReady()) {
            synchronized (lock) {
                targetAskAttempts = attempts + 1;
                if (targetAskAttempts >= TARGET_CHAT_ASK_MAX_ATTEMPTS) {
                    targetAskNextAttemptAtMs = 0L;
                } else {
                    targetAskNextAttemptAtMs = now + TARGET_CHAT_ASK_RETRY_MS;
                }
            }
            Log.d(TAG, TRACE_PREFIX + " ask target delayed: chat frame not ready, target=" + target
                    + ", attempt=" + (attempts + 1));
            return;
        }

        String message = "%<" + target + "> РџРѕРґСЃРєР°Р¶Рё РЅР° РєР°РєРѕР№ РєР»РµС‚РєРµ Р‘РѕСЃСЃ?";
        // РћС‚РїСЂР°РІР»СЏРµРј private message СЃ Р·Р°РґРµСЂР¶РєРѕР№ 500ms РїРѕСЃР»Рµ clan message
        final String privateMsg = message;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Chat.sendMessageToServer(privateMsg);
            Log.d(TAG, TRACE_PREFIX + " ask target sent (delayed): target=" + target + ", message=" + privateMsg);
        }, CLAN_PRIVATE_MESSAGE_DELAY_MS);
        synchronized (lock) {
            targetAskAttempts = TARGET_CHAT_ASK_MAX_ATTEMPTS;
            targetAskNextAttemptAtMs = 0L;
        }
        Log.d(TAG, TRACE_PREFIX + " ask target scheduled (500ms delay): target=" + target);
    }

    /**
     * РћРґРЅРѕРєСЂР°С‚РЅРѕ Р·Р°РґР°С‘С‚ С†РµР»Рё РІРѕРїСЂРѕСЃ Рѕ РєР»РµС‚РєРµ Р±РµР· Р·Р°РїСѓСЃРєР° SEARCHING-С†РёРєР»Р°.
     *
     * РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РІ РІРµС‚РєР°С…, РіРґРµ РґРІРёР¶РµРЅРёРµ Рє С†РµР»Рё Р·Р°Р±Р»РѕРєРёСЂРѕРІР°РЅРѕ С„РёР»СЊС‚СЂР°РјРё (Р‘Р”/РІРѕР№РЅС‹),
     * РЅРѕ СѓРІРµРґРѕРјРёС‚СЊ С†РµР»СЊ РІ РїСЂРёРІР°С‚ РІСЃС‘ СЂР°РІРЅРѕ РЅСѓР¶РЅРѕ.
     */
    private void askTargetOnceIfEnabled(String target) {
        String normalizedTarget = normalizeNick(target);
        if (!isAutoBossAskTargetEnabled() || isEmpty(normalizedTarget)) {
            return;
        }
        if (!isChatSendReady()) {
            Log.d(TAG, TRACE_PREFIX + " ask target skipped (single): chat frame not ready, target=" + normalizedTarget);
            return;
        }
        String message = "%<" + normalizedTarget + "> РџРѕРґСЃРєР°Р¶Рё РЅР° РєР°РєРѕР№ РєР»РµС‚РєРµ Р‘РѕСЃСЃ?";
        // РћС‚РїСЂР°РІР»СЏРµРј private message СЃ Р·Р°РґРµСЂР¶РєРѕР№ 500ms РґР»СЏ DDoS protection
        final String singleMsg = message;
        final String finalNorm = normalizedTarget;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Chat.sendMessageToServer(singleMsg);
            String traceMsg = "[BOSS_PRIVATE_MSG_SENT] Single ask, target=" + finalNorm;
            FileLogger.trace("boss_auto", traceMsg);
            Log.d(TAG, TRACE_PREFIX + " ask target sent (single, delayed): target=" + finalNorm + ", message=" + singleMsg);
        }, CLAN_PRIVATE_MESSAGE_DELAY_MS);
        String queueMsg = "[BOSS_PRIVATE_MSG_QUEUED] Single ask, target=" + normalizedTarget + ", delay=500ms";
        FileLogger.trace("boss_auto", queueMsg);
        Log.d(TAG, TRACE_PREFIX + " ask target queued (single, 500ms delay): target=" + normalizedTarget);
    }

    /**
     * Р’Рѕ РІСЂРµРјСЏ РїРѕРёСЃРєР° РєРѕРЅС‚СЂРѕР»РёСЂСѓРµС‚, С‡С‚Рѕ С†РµР»СЊ РІСЃС‘ РµС‰С‘ РЅР°С…РѕРґРёС‚СЃСЏ РІ Р±РѕСЋ.
     *
     * Р›РѕРіРёРєР°:
     * - РµСЃР»Рё {@code fightFid} РїСЂРёСЃСѓС‚СЃС‚РІСѓРµС‚ вЂ” РѕР±РЅРѕРІР»СЏРµРј РєСЌС€ СЃСЃС‹Р»РєРё РЅР° Р±РѕР№;
     * - РµСЃР»Рё {@code fightFid} РІСЂРµРјРµРЅРЅРѕ РїСЂРѕРїР°Р» вЂ” РґР°С‘Рј 1 grace-С‚РёРє;
     * - РµСЃР»Рё РѕС‚СЃСѓС‚СЃС‚РІСѓРµС‚ РІС‚РѕСЂРѕР№ С‚РёРє РїРѕРґСЂСЏРґ вЂ” РѕС‚РјРµРЅСЏРµРј СЃС†РµРЅР°СЂРёР№.
     */
    private boolean monitorTargetFightStateDuringSearch() {
        String target;
        synchronized (lock) {
            if (stage != BossStage.SEARCHING_TARGET) {
                return false;
            }
            target = targetNick;
        }
        if (isEmpty(target)) {
            return true;
        }

        requestTargetFightPollIfNeeded(target);

        boolean fightLostPending;
        synchronized (lock) {
            fightLostPending = bossFightLostPending;
        }
        if (!fightLostPending) {
            return true;
        }

        writeBossChat("Р”РµР№СЃС‚РІРёРµ РѕС‚РјРµРЅРµРЅРѕ, С†РµР»СЊ СѓР¶Рµ РЅРµ РІ " + getCurrentFightWordHtml() + ".");
        stopAndRestore("target_left_fight", true);
        return false;
    }

    /**
     * РћРїСЂРµРґРµР»СЏРµС‚, РЅР° РєРѕРіРѕ РїСЂРёРјРµРЅСЏС‚СЊ В«РЎРІРёС‚РѕРє Р—Р°С‰РёС‚С‹В».
     *
     * РџСЂРёРѕСЂРёС‚РµС‚:
     * 1) РёСЃС…РѕРґРЅР°СЏ С†РµР»СЊ, РµСЃР»Рё РѕРЅР° Р¶РёРІР°;
     * 2) РїРµСЂРІС‹Р№ Р¶РёРІРѕР№ СЃРѕСЋР·РЅРёРє РёР· С‚РѕРіРѕ Р¶Рµ Р±РѕСЏ (fallback).
     *
     * Р•СЃР»Рё Р¶РёРІС‹С… СЃРѕСЋР·РЅРёРєРѕРІ РЅРµ РѕСЃС‚Р°Р»РѕСЃСЊ, РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ РїСѓСЃС‚Р°СЏ СЃС‚СЂРѕРєР°
     * Рё СЃС†РµРЅР°СЂРёР№ РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ Р·Р°РІРµСЂС€С‘РЅ РІС‹Р·С‹РІР°СЋС‰РµР№ СЃС‚РѕСЂРѕРЅРѕР№.
     */
    /**
     * РђСЃРёРЅС…СЂРѕРЅРЅС‹Р№ poll pinfo С†РµР»Рё РІРѕ РІСЂРµРјСЏ SEARCHING_TARGET.
     *
     * Р’Р°Р¶РЅРѕ: tick Auto-Boss Р·Р°РїСѓСЃРєР°РµС‚СЃСЏ СЃ UI-РїРѕС‚РѕРєР° (С‡РµСЂРµР· foreground-service -> runOnUiThread),
     * РїРѕСЌС‚РѕРјСѓ СЃРµС‚РµРІРѕР№ РІС‹Р·РѕРІ NeverApi.getPinfoCompassSnapshot(...) РЅРµР»СЊР·СЏ РІС‹РїРѕР»РЅСЏС‚СЊ СЃРёРЅС…СЂРѕРЅРЅРѕ
     * РІРЅСѓС‚СЂРё tick. РРЅР°С‡Рµ РІРѕР·РЅРёРєР°РµС‚ NetworkOnMainThreadException Рё Р»РѕРјР°РµС‚СЃСЏ С†РёРєР» РїРѕРёСЃРєР°.
     */
    private void requestTargetFightPollIfNeeded(String target) {
        if (isEmpty(target)) {
            return;
        }
        long now = System.currentTimeMillis();
        final String targetSnapshot;
        synchronized (lock) {
            if (stage != BossStage.SEARCHING_TARGET) {
                return;
            }
            if (targetFightPollInFlight || now < targetFightPollNextAtMs) {
                return;
            }
            targetFightPollInFlight = true;
            targetFightPollNextAtMs = now + TARGET_FIGHT_POLL_INTERVAL_MS;
            targetFightPollNick = target;
            targetSnapshot = target;
        }

        Thread worker = new Thread(() -> {
            try {
                NeverApi.PinfoCompassSnapshot snapshot = safeGetPinfoSnapshot(targetSnapshot);
                applyTargetFightPollResult(targetSnapshot, snapshot);
            } finally {
                synchronized (lock) {
                    targetFightPollInFlight = false;
                }
            }
        }, "boss_target_fight_poll");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * РџСЂРёРјРµРЅРµРЅРёРµ СЂРµР·СѓР»СЊС‚Р°С‚Р° Р°СЃРёРЅС…СЂРѕРЅРЅРѕРіРѕ poll-Р° СЃРѕСЃС‚РѕСЏРЅРёСЏ Р±РѕСЏ С†РµР»Рё.
     *
     * Р›РѕРіРёРєР° СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓРµС‚ РїСЂРµР¶РЅРµРјСѓ СЃРёРЅС…СЂРѕРЅРЅРѕРјСѓ РІР°СЂРёР°РЅС‚Сѓ:
     * - РµСЃР»Рё fightFid РµСЃС‚СЊ -> РѕР±РЅРѕРІР»СЏРµРј РєРµС€ Рё СЃР±СЂР°СЃС‹РІР°РµРј grace;
     * - РµСЃР»Рё fightFid РїСЂРѕРїР°Р» -> РїРµСЂРІС‹Р№ С‚РёРє grace, РІС‚РѕСЂРѕР№ С‚РёРє РїРѕРјРµС‡Р°РµС‚ РѕС‚РјРµРЅСѓ СЃС†РµРЅР°СЂРёСЏ.
     *
     * РЎР°Рј stopAndRestore РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ РІ monitorTargetFightStateDuringSearch (UI-tick),
     * С‡С‚РѕР±С‹ РЅРµ СЂР°Р·СЂС‹РІР°С‚СЊ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёР№ РєРѕРЅС‚СѓСЂ Р¶РёР·РЅРµРЅРЅРѕРіРѕ С†РёРєР»Р°.
     */
    private void applyTargetFightPollResult(String target, NeverApi.PinfoCompassSnapshot snapshot) {
        if (isEmpty(target) || snapshot == null) {
            return;
        }
        String latestFid = normalizeFightFid(snapshot.fightFid);
        int missingTicks = 0;
        synchronized (lock) {
            if (stage != BossStage.SEARCHING_TARGET) {
                return;
            }
            if (!normalizeNick(targetNick).equalsIgnoreCase(normalizeNick(target))) {
                return;
            }
            if (!isEmpty(latestFid)) {
                bossFightFid = latestFid;
                bossFightLink = buildFightLogLink(latestFid);
                bossFightFidMissingTicks = 0;
                bossFightLostPending = false;
            } else {
                bossFightFidMissingTicks++;
                // РћРіСЂР°РЅРёС‡РёРІР°РµРј СЃС‡РµС‚С‡РёРє РјР°РєСЃРёРјР°Р»СЊРЅС‹Рј Р·РЅР°С‡РµРЅРёРµРј РґР»СЏ Р·Р°С‰РёС‚С‹ РѕС‚ РїРµСЂРµРїРѕР»РЅРµРЅРёСЏ
                if (bossFightFidMissingTicks > BOSS_FIGHT_FID_MISSING_MAX_TICKS) {
                    bossFightFidMissingTicks = BOSS_FIGHT_FID_MISSING_MAX_TICKS;
                }
                missingTicks = bossFightFidMissingTicks;
                if (missingTicks > 1) {
                    bossFightLostPending = true;
                }
            }
        }
        if (isEmpty(latestFid) && missingTicks == 1) {
            Log.d(TAG, TRACE_PREFIX + " target fight fid missing, wait grace tick: target=" + target);
        }
    }

    private String resolveProtectionTargetNick(String initialTarget) {
        String target = normalizeNick(initialTarget);
        if (isEmpty(target)) {
            return "";
        }

        String fid;
        synchronized (lock) {
            fid = bossFightFid;
        }
        if (isEmpty(fid)) {
            NeverApi.PinfoCompassSnapshot snapshot = safeGetPinfoSnapshot(target);
            if (snapshot != null) {
                fid = normalizeFightFid(snapshot.fightFid);
                if (!isEmpty(fid)) {
                    synchronized (lock) {
                        bossFightFid = fid;
                        bossFightLink = buildFightLogLink(fid);
                        bossFightFidMissingTicks = 0;
                    }
                }
            }
        }

        if (isEmpty(fid)) {
            return target;
        }

        String flogHtml;
        try {
            flogHtml = NeverApi.getFlog(fid);
        } catch (Exception e) {
            Log.w(TAG, TRACE_PREFIX + " failed to load flog for fid=" + fid, e);
            return target;
        }
        if (isEmpty(flogHtml)) {
            return target;
        }
        if (flogHtml.contains("var off = 1;")) {
            writeBossChat("Р”РµР№СЃС‚РІРёРµ РѕС‚РјРµРЅРµРЅРѕ, С†РµР»СЊ СѓР¶Рµ РЅРµ РІ " + buildFightWordHtml(buildFightLogLink(fid)) + ".");
            return "";
        }

        List<FightAllyState> allies = parseFightAlliesFromLog(flogHtml);
        if (allies.isEmpty()) {
            return target;
        }

        FightAllyState initialState = null;
        FightAllyState firstAlive = null;
        String normalizedTarget = normalizeNick(target);
        for (FightAllyState ally : allies) {
            if (normalizeNick(ally.nick).equalsIgnoreCase(normalizedTarget)) {
                initialState = ally;
            }
            if (firstAlive == null && ally.curHp > 0) {
                firstAlive = ally;
            }
        }

        if (initialState != null && initialState.curHp > 0) {
            return initialState.nick;
        }
        if (firstAlive == null) {
            writeBossChat("Р”РµР№СЃС‚РІРёРµ РѕС‚РјРµРЅРµРЅРѕ вЂ” РІ Р±РѕСЋ РЅРµ РЅР°Р№РґРµРЅРѕ Р¶РёРІС‹С… СЃРѕСЋР·РЅРёРєРѕРІ РґР»СЏ Р·Р°С‰РёС‚С‹.");
            return "";
        }
        if (initialState != null && initialState.curHp <= 0) {
            String aliveHtml = buildTargetNickHtml(firstAlive.nick, null);
            writeBossChat("РСЃС…РѕРґРЅР°СЏ С†РµР»СЊ РјРµСЂС‚РІР°, РїСЂРёРјРµРЅСЏРµРј РЎРІРёС‚РѕРє Р—Р°С‰РёС‚С‹ РЅР° " + aliveHtml + ".");
        }
        return firstAlive.nick;
    }

    /**
     * РџР°СЂСЃРёС‚ СЃРѕСЋР·РЅСѓСЋ С‡Р°СЃС‚СЊ Р»РѕРіР° Р±РѕСЏ Рё РёР·РІР»РµРєР°РµС‚ СЃРѕСЃС‚РѕСЏРЅРёСЏ СѓС‡Р°СЃС‚РЅРёРєРѕРІ.
     * РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ fallback-Р»РѕРіРёРєРѕР№ РІС‹Р±РѕСЂР° Р¶РёРІРѕР№ С†РµР»Рё РґР»СЏ Р·Р°С‰РёС‚С‹.
     */
    private List<FightAllyState> parseFightAlliesFromLog(String flogHtml) {
        ArrayList<FightAllyState> result = new ArrayList<>();
        if (isEmpty(flogHtml)) {
            return result;
        }
        String plain = toPlainText(flogHtml);
        if (isEmpty(plain)) {
            return result;
        }
        String lower = plain.toLowerCase(Locale.ROOT);
        int startIndex = lower.indexOf("СѓС‡Р°СЃС‚РЅРёРєРё Р±РѕСЏ");
        if (startIndex < 0) {
            startIndex = 0;
        }
        int againstIndex = lower.indexOf(" РїСЂРѕС‚РёРІ ", startIndex);
        String alliesPart = againstIndex > startIndex
                ? plain.substring(startIndex, againstIndex)
                : plain;

        Matcher matcher = FIGHT_ALLY_PATTERN.matcher(alliesPart);
        while (matcher.find()) {
            String nick = normalizeNick(matcher.group(1));
            int curHp = parseIntSafe(matcher.group(2));
            int maxHp = parseIntSafe(matcher.group(3));
            if (isEmpty(nick) || maxHp <= 0) {
                continue;
            }
            result.add(new FightAllyState(nick, curHp, maxHp));
        }
        return result;
    }

    private int parseIntSafe(String value) {
        if (isEmpty(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int parseClassIdSafe(String value) {
        if (isEmpty(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean isChatSendReady() {
        MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
        return activity != null
                && activity.binding != null
                && activity.binding.appBarMain != null
                && activity.binding.appBarMain.contentMain != null
                && activity.binding.appBarMain.contentMain.chatButtonsWebview != null;
    }

    private String extractCellRegNum(String plainText) {
        if (isEmpty(plainText)) {
            return "";
        }
        Matcher matcher = CELL_PATTERN.matcher(plainText);
        if (!matcher.find()) {
            return "";
        }
        return safeTrim(matcher.group());
    }

    private boolean containsFightCompleted(String plainText) {
        String normalized = plainText.toLowerCase(Locale.ROOT);
        return normalized.contains("РїРѕРµРґРёРЅРѕРє Р·Р°РІРµСЂС€");
    }

    private boolean isFightLikelyActive() {
        long now = System.currentTimeMillis();
        if (now - AppVars.LastFightPulseAtMs <= 12_000L) {
            return true;
        }
        String fightLink = AppVars.FightLink == null ? "" : AppVars.FightLink.toLowerCase(Locale.ROOT);
        if (fightLink.contains("fight") || fightLink.contains("act=7")) {
            return true;
        }
        String topUrl = AppVars.url_main_top == null ? "" : AppVars.url_main_top.toLowerCase(Locale.ROOT);
        return topUrl.contains("go=inf") && topUrl.contains("main.php");
    }

    private String currentMapRegNum() {
        if (AppVars.Profile == null || AppVars.Profile.MapLocation == null) {
            return "";
        }
        return safeTrim(AppVars.Profile.MapLocation);
    }

    private String toPlainText(String html) {
        if (isEmpty(html)) {
            return "";
        }
        return html
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeNick(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String normalizeBossKey(String boss, String target) {
        return (safeTrim(boss).toLowerCase(Locale.ROOT) + "|" + normalizeNick(target).toLowerCase(Locale.ROOT)).trim();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeJsSingleQuoted(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "");
    }

    private void writeBossChat(String message) {
        if (isEmpty(message)) {
            return;
        }
        FileLogger.trace("auto_boss", message);
        FastActionManager.writeChatMsg(
                LOCAL_CHAT_MARKER
                        + MainPhp.buildServerChatTimeHtmlExternal()
                        + "<font color=#7E57C2><b>[РђРІС‚Рѕ-Р‘РѕСЃСЃС‹]</b></font> "
                        + message
        );
    }

    /**
     * РћС‚РїСЂР°РІР»СЏРµС‚ РєР»Р°РЅРѕРІРѕРµ СѓРІРµРґРѕРјР»РµРЅРёРµ Рѕ СЃС‚Р°СЂС‚Рµ СЃРѕР±С‹С‚РёСЏ Р‘РѕСЃСЃР°, РµСЃР»Рё РІРєР»СЋС‡РµРЅР° СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓСЋС‰Р°СЏ РѕРїС†РёСЏ.
     * Р•СЃР»Рё РЅР°С€ РїСЂРѕС„РёР»СЊ РІРЅРµ РєР»Р°РЅР°, РІРјРµСЃС‚Рѕ РѕС‚РїСЂР°РІРєРё РІ РєР»Р°РЅ-С‡Р°С‚ РїРёС€РµС‚ Р»РѕРєР°Р»СЊРЅРѕРµ СѓРІРµРґРѕРјР»РµРЅРёРµ.
     */
    /**
     * РћС‚РїСЂР°РІРєР° РєР»Р°РЅ-СѓРІРµРґРѕРјР»РµРЅРёСЏ Рѕ СЃС‚Р°СЂС‚Рµ СЃРѕР±С‹С‚РёСЏ Р±РѕСЃСЃР°.
     *
     * Р—Р°РІРёСЃРёРјРѕСЃС‚Рё:
     * - `resolveAutoCompassLocation(...)` РґР»СЏ СЃРїРёСЃРєР° РІРѕР·РјРѕР¶РЅС‹С… РєР»РµС‚РѕРє;
     * - `Chat.sendMessageToServer(...)` РґР»СЏ РѕС‚РїСЂР°РІРєРё РІ `%clan%`;
     * - РїСЂРѕРІРµСЂРєР° РЅР°Р»РёС‡РёСЏ РєР»Р°РЅР° РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ РїРѕ `selfClanToken` РёР· pinfo.
     *
     * Р•СЃР»Рё РїРµСЂСЃРѕРЅР°Р¶ РІРЅРµ РєР»Р°РЅР°, РєР»Р°РЅ-СЃРѕРѕР±С‰РµРЅРёРµ РЅРµ РѕС‚РїСЂР°РІР»СЏРµС‚СЃСЏ:
     * РІ Р»РѕРєР°Р»СЊРЅС‹Р№ С‡Р°С‚ РїРёС€РµС‚СЃСЏ РїСЂРёС‡РёРЅР° РѕС‚РјРµРЅС‹.
     */
    private void sendClanBossEventMessageIfNeeded(String bossName, String targetNick, String selfClanToken, String fightLink) {
        if (!isAutoBossClanNotifyEnabled()) {
            Log.d(TAG, TRACE_PREFIX + " clan notify event skipped: disabled by settings");
            FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_MSG_SKIPPED] reason=disabled_by_settings");
            return;
        }
        if (isEmpty(normalizeClanToken(selfClanToken))) {
            Log.d(TAG, TRACE_PREFIX + " clan notify event skipped: self clan token is empty");
            FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_MSG_SKIPPED] reason=empty_self_clan_token");
            writeBossChat("РћС‚РїСЂР°РІРєР° РІ РєР»Р°РЅ-С‡Р°С‚ РЅРµРІРѕР·РјРѕР¶РЅР°: РѕС‚СЃСѓС‚СЃС‚РІСѓРµС‚ Р·РЅР°С‡РѕРє РєР»Р°РЅР° (selfClanToken РїСѓСЃС‚РѕР№).");
            return;
        }

        String normalizedTarget = normalizeNick(targetNick);
        String cellsCsv = "";
        try {
            AutoFunctionsManager.CompassLocationResolveResult resolved = owner.resolveAutoCompassLocation(normalizedTarget);
            if (resolved != null && resolved.success) {
                cellsCsv = safeTrim(resolved.cellsCsv);
            }
        } catch (Exception e) {
            Log.w(TAG, TRACE_PREFIX + " clan notify resolve failed: target=" + normalizedTarget, e);
        }
        if (isEmpty(cellsCsv)) {
            cellsCsv = safeTrim(owner.getAutoCompassCellsCsv());
        }
        if (isEmpty(cellsCsv)) {
            cellsCsv = "РЅРµ РѕРїСЂРµРґРµР»РµРЅС‹";
        }

        String safeBossName = safeTrim(bossName);
        if (isEmpty(safeBossName)) {
            safeBossName = "Р‘РѕСЃСЃ";
        }
        String safeFightLink = safeTrim(fightLink);
        String fightPart = isEmpty(safeFightLink) ? "РІ Р±РѕСЋ" : "РІ " + buildFightWordHtml(safeFightLink);
        String message = buildClanBossEventMessage(safeBossName, cellsCsv, fightPart, normalizedTarget);
        boolean chatReady = isChatSendReady();
        if (!chatReady) {
            Log.w(TAG, TRACE_PREFIX + " clan notify event CANCELED: chatButtonsWebview not ready, target="
                    + normalizedTarget + ", cells=" + cellsCsv + ". Message will be retried by Chat.sendMessageToServer");
            FileLogger.log("[BossAuto.sendClanBossEventMessageIfNeeded] WebView not ready, message queued for retry: " + message.substring(0, Math.min(100, message.length())));
            writeBossChat("РљР»Р°РЅ-СЃРѕРѕР±С‰РµРЅРёРµ РґРѕР±Р°РІР»РµРЅРѕ РІ РѕС‡РµСЂРµРґСЊ. Р‘СѓРґРµС‚ РѕС‚РїСЂР°РІР»РµРЅРѕ РїСЂРё РїРѕРґРіРѕС‚РѕРІРєРµ С‡Р°С‚Р°.");
        }
        Log.d(TAG, TRACE_PREFIX + " clan notify event payload: len=" + message.length() + ", maxLen=" + CLAN_EVENT_CHAT_MAX_LEN);
        FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_MSG_PAYLOAD] len=" + message.length() + ", maxLen=" + CLAN_EVENT_CHAT_MAX_LEN);
        // РћС‚РїСЂР°РІР»СЏРµРј clan message СЃ Р·Р°РґРµСЂР¶РєРѕР№ 1 СЃРµРє РґР»СЏ DDoS protection
        // (Р±СѓС„РµСЂРёР·Р°С†РёСЏ РїРѕС‚РѕРєР° pinfo + compass + РґСЂСѓРіРёС… Р·Р°РїСЂРѕСЃРѕРІ)
        final String clanMsg = message;
        final String finalNorm = normalizedTarget;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Chat.sendMessageToServer(clanMsg);
            String traceMsg = "[BOSS_CLAN_MSG_SENT] Delayed 1s, target=" + finalNorm + ", msg=" + clanMsg.substring(0, Math.min(80, clanMsg.length()));
            FileLogger.trace("boss_auto", traceMsg);
            FileLogger.log("[BossAuto.sendClanBossEventMessageIfNeeded] Sent to Chat.sendMessageToServer (after 1s delay): " + clanMsg.substring(0, Math.min(100, clanMsg.length())));
            Log.d(TAG, TRACE_PREFIX + " clan notify event sent (delayed): target=" + finalNorm);
        }, CLAN_NOTIFY_DELAY_MS);
        String schedMsg = "[BOSS_CLAN_MSG_SCHEDULED] 1s delay, target=" + normalizedTarget + ", cells=" + cellsCsv + ", chatReady=" + chatReady;
        FileLogger.trace("boss_auto", schedMsg);
        Log.d(TAG, TRACE_PREFIX + " clan notify event scheduled (1s delay): target=" + normalizedTarget + ", cells=" + cellsCsv
                + ", chatReady=" + chatReady);
    }

    private String buildClanBossEventMessage(String bossName, String cellsCsv, String fightPart, String normalizedTarget) {
        fightPart = normalizeClanFightPartForSend(fightPart);
        String prefix = "%clan% \"" + bossName + "\" РІРѕР·РјРѕР¶РЅРѕ РЅР° РєР»РµС‚РєР°С…: ";
        String suffix = " " + fightPart + " СЃ РїРµСЂСЃРѕРЅР°Р¶РµРј '" + normalizedTarget + "'.";
        String normalizedCells = safeTrim(cellsCsv).replaceAll("\\s+", " ");
        if (isEmpty(normalizedCells)) {
            normalizedCells = "РЅРµ РѕРїСЂРµРґРµР»РµРЅС‹";
        }

        String full = prefix + normalizedCells + suffix;
        if (full.length() <= CLAN_EVENT_CHAT_MAX_LEN) {
            return full;
        }

        String[] cells = normalizedCells.split("\\s*,\\s*");
        StringBuilder compact = new StringBuilder();
        boolean truncated = false;
        for (String cell : cells) {
            String safeCell = safeTrim(cell);
            if (safeCell.isEmpty()) {
                continue;
            }
            String candidateCells = compact.length() == 0 ? safeCell : compact + ", " + safeCell;
            String candidateMessage = prefix + candidateCells + "..." + suffix;
            if (candidateMessage.length() > CLAN_EVENT_CHAT_MAX_LEN) {
                truncated = true;
                break;
            }
            if (compact.length() == 0) {
                compact.append(safeCell);
            } else {
                compact.append(", ").append(safeCell);
            }
        }

        if (compact.length() == 0) {
            int allowedCellsLen = CLAN_EVENT_CHAT_MAX_LEN - prefix.length() - suffix.length() - 3;
            if (allowedCellsLen <= 0) {
                return (prefix + suffix).substring(0, Math.min(CLAN_EVENT_CHAT_MAX_LEN, (prefix + suffix).length()));
            }
            String shortCells = normalizedCells.substring(0, Math.min(allowedCellsLen, normalizedCells.length())).trim();
            if (shortCells.isEmpty()) {
                shortCells = "РЅРµ РѕРїСЂРµРґРµР»РµРЅС‹";
            }
            truncated = shortCells.length() < normalizedCells.length();
            String result = prefix + shortCells + (truncated ? "..." : "") + suffix;
            FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_MSG_TRIM] fallback=true, truncated=" + truncated + ", len=" + result.length());
            return result;
        }

        String result = prefix + compact + (truncated ? "..." : "") + suffix;
        FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_MSG_TRIM] fallback=false, truncated=" + truncated + ", len=" + result.length());
        return result;
    }

    /**
     * РћС‚РїСЂР°РІР»СЏРµС‚ РєР»Р°РЅРѕРІРѕРµ СѓРІРµРґРѕРјР»РµРЅРёРµ Рѕ С‚РѕС‡РЅРѕР№ РєР»РµС‚РєРµ Р‘РѕСЃСЃР° РїРѕСЃР»Рµ РѕР±РЅР°СЂСѓР¶РµРЅРёСЏ С†РµР»Рё РІ РєРѕРјРЅР°С‚Рµ.
     * Р•СЃР»Рё РЅР°С€ РїСЂРѕС„РёР»СЊ РІРЅРµ РєР»Р°РЅР°, РІРјРµСЃС‚Рѕ РѕС‚РїСЂР°РІРєРё РІ РєР»Р°РЅ-С‡Р°С‚ РїРёС€РµС‚ Р»РѕРєР°Р»СЊРЅРѕРµ СѓРІРµРґРѕРјР»РµРЅРёРµ.
     */
    /**
     * РћС‚РїСЂР°РІРєР° РєР»Р°РЅ-СѓРІРµРґРѕРјР»РµРЅРёСЏ СЃ С‚РѕС‡РЅРѕР№ РєР»РµС‚РєРѕР№ РїРѕСЃР»Рµ РѕР±РЅР°СЂСѓР¶РµРЅРёСЏ С†РµР»Рё.
     *
     * РСЃС‚РѕС‡РЅРёРє РєР»РµС‚РєРё:
     * - РѕСЃРЅРѕРІРЅРѕР№: `currentMapRegNum()`;
     * - fallback: `AppVars.AutoMovingDestinaton`, РµСЃР»Рё РєР°СЂС‚Р° РµС‰С‘ РЅРµ СЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅР°.
     */
    private void sendClanBossFoundMessageIfNeeded() {
        if (!isAutoBossClanNotifyEnabled()) {
            Log.d(TAG, TRACE_PREFIX + " clan notify found skipped: disabled by settings");
            return;
        }
        String selfClanToken;
        synchronized (lock) {
            selfClanToken = bossSelfClanToken;
        }
        if (isEmpty(normalizeClanToken(selfClanToken))) {
            Log.d(TAG, TRACE_PREFIX + " clan notify found skipped: self clan token is empty");
            writeBossChat("РћС‚РїСЂР°РІРєР° РІ РєР»Р°РЅ-С‡Р°С‚ РЅРµРІРѕР·РјРѕР¶РЅР°: РѕС‚СЃСѓС‚СЃС‚РІСѓРµС‚ Р·РЅР°С‡РѕРє РєР»Р°РЅР° (selfClanToken РїСѓСЃС‚РѕР№).");
            return;
        }

        String exactRegNum = currentMapRegNum();
        if (isEmpty(exactRegNum) && !isEmpty(AppVars.AutoMovingDestinaton)) {
            exactRegNum = safeTrim(AppVars.AutoMovingDestinaton);
        }
        if (isEmpty(exactRegNum)) {
            exactRegNum = "?";
        }
        String message = "%clan% Босс на клетке '" + exactRegNum + "'";
        boolean chatReady = isChatSendReady();
        if (!chatReady) {
            Log.w(TAG, TRACE_PREFIX + " clan notify found send requested while chat is not ready: cell=" + exactRegNum);
        }
        Chat.sendMessageToServer(message);
        Log.d(TAG, TRACE_PREFIX + " clan notify found sent: cell=" + exactRegNum + ", chatReady=" + chatReady);
    }

    /**
     * РћС‚РїСЂР°РІР»СЏРµС‚ РІ РєР»Р°РЅ-С‡Р°С‚ СЃР»СѓР¶РµР±РЅРѕРµ СѓРІРµРґРѕРјР»РµРЅРёРµ, РєРѕРіРґР° СЃС†РµРЅР°СЂРёР№ РѕСЃС‚Р°РЅРѕРІР»РµРЅ С„РёР»СЊС‚СЂРѕРј С‚РµРєСѓС‰РёС… РІРѕР№РЅ.
     *
     * РЎРѕРѕР±С‰РµРЅРёРµ (РїРѕ С‚СЂРµР±РѕРІР°РЅРёСЋ): "%clan% РџРѕРµРґРёРЅРѕРє СЃ Р‘РѕСЃСЃРѕРј РЅРµРІРѕР·РјРѕР¶РµРЅ, РёРіСЂРѕРє РІ РљР»Р°РЅРѕРІРѕР№ РІРѕР№РЅРµ."
     */
    private void sendClanBossWarDeniedMessageIfNeeded(String selfClanToken) {
        if (!isAutoBossClanNotifyEnabled()) {
            Log.d(TAG, TRACE_PREFIX + " clan notify wars-denied skipped: disabled by settings");
            FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_WARS_DENY_SKIPPED] reason=disabled_by_settings");
            return;
        }
        if (isEmpty(normalizeClanToken(selfClanToken))) {
            Log.d(TAG, TRACE_PREFIX + " clan notify wars-denied skipped: self clan token is empty");
            FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_WARS_DENY_SKIPPED] reason=empty_self_clan_token");
            writeBossChat("\u041e\u0442\u043f\u0440\u0430\u0432\u043a\u0430 \u0432 \u043a\u043b\u0430\u043d-\u0447\u0430\u0442 \u043d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u043d\u0430: \u043e\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442 \u0437\u043d\u0430\u0447\u043e\u043a \u043a\u043b\u0430\u043d\u0430 (selfClanToken \u043f\u0443\u0441\u0442\u043e\u0439).");
            return;
        }
        String message = "%clan% \u041f\u043e\u0435\u0434\u0438\u043d\u043e\u043a \u0441 \u0411\u043e\u0441\u0441\u043e\u043c \u043d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u0435\u043d, \u0438\u0433\u0440\u043e\u043a \u0432 \u041a\u043b\u0430\u043d\u043e\u0432\u043e\u0439 \u0432\u043e\u0439\u043d\u0435.";
        boolean chatReady = isChatSendReady();
        if (!chatReady) {
            Log.w(TAG, TRACE_PREFIX + " clan notify wars-denied send requested while chat is not ready");
        }
        Chat.sendMessageToServer(message);
        FileLogger.trace(LOG_CHAIN, "[BOSS_CLAN_WARS_DENY_SENT] chatReady=" + chatReady + ", msgLen=" + message.length());
        Log.d(TAG, TRACE_PREFIX + " clan notify wars-denied sent: chatReady=" + chatReady);
    }

    boolean isAutoBossAskTargetEnabled() {
        return prefs.getBoolean(PREF_AUTO_BOSS_ASK_TARGET, true);
    }

    void setAutoBossAskTargetEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_BOSS_ASK_TARGET, enabled).apply();
    }

    /**
     * РџСЂРѕРІРµСЂРєР° РїРѕ С‚РµРєСѓС‰РёРј РєР»Р°РЅРѕРІС‹Рј РІРѕР№РЅР°Рј (`wars.cgi`).
     * Р•СЃР»Рё РІРєР»СЋС‡РµРЅРѕ, РђРІС‚Рѕ-Р‘РѕСЃСЃ РЅРµ РїС‹С‚Р°РµС‚СЃСЏ Р·Р°С‰РёС‰Р°С‚СЊ С†РµР»СЊ,
     * С‡СЊС‘ `targetClanToken` РїСЂРёСЃСѓС‚СЃС‚РІСѓРµС‚ РІ СЃРїРёСЃРєРµ С‚РµРєСѓС‰РёС… РІРѕР№РЅ.
     * РќР°СЃС‚СЂРѕР№РєР° РїСЂРёРјРµРЅСЏРµС‚СЃСЏ РЅРµР·Р°РІРёСЃРёРјРѕ РѕС‚ РґСЂСѓРіРёС… С„РёР»СЊС‚СЂРѕРІ СЃС†РµРЅР°СЂРёСЏ.
     */
    /**
     * Р¤Р»Р°Рі В«РЎР»РµРґРёС‚СЊ Р·Р° С‚РµРєСѓС‰РёРјРё РІРѕР№РЅР°РјРёВ».
     *
     * РџСЂРё РІРєР»СЋС‡РµРЅРёРё С†РµР»СЊ РѕС‚РєР»РѕРЅСЏРµС‚СЃСЏ, РµСЃР»Рё РµС‘ clanToken РїСЂРёСЃСѓС‚СЃС‚РІСѓРµС‚
     * РІ РєСЌС€Рµ С‚РµРєСѓС‰РёС… РІРѕР№РЅ, РїРѕР»СѓС‡РµРЅРЅРѕРј РёР· {@code wars.cgi}.
     */
    /**
     * Р¤Р»Р°Рі В«РЎР»РµРґРёС‚СЊ Р·Р° С‚РµРєСѓС‰РёРјРё РІРѕР№РЅР°РјРёВ».
     *
     * РљРѕРіРґР° РІРєР»СЋС‡С‘РЅ, `BossAuto` РѕС‚С„РёР»СЊС‚СЂРѕРІС‹РІР°РµС‚ С†РµР»СЊ, РµСЃР»Рё РµС‘ `targetClanToken`
     * РїСЂРёСЃСѓС‚СЃС‚РІСѓРµС‚ РІ РєСЌС€Рµ `ClanWarsManager` (РґР°РЅРЅС‹Рµ `wars.cgi`).
     */
    boolean isAutoBossTrackCurrentWarsEnabled() {
        return prefs.getBoolean(PREF_AUTO_BOSS_TRACK_CURRENT_WARS, true);
    }

    void setAutoBossTrackCurrentWarsEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_BOSS_TRACK_CURRENT_WARS, enabled).apply();
    }

    /**
     * Р¤Р»Р°Рі РѕС‚РїСЂР°РІРєРё СЃР»СѓР¶РµР±РЅС‹С… СЃРѕРѕР±С‰РµРЅРёР№ РІ РєР»Р°РЅ-С‡Р°С‚ РґР»СЏ СЃС†РµРЅР°СЂРёСЏ В«РђРІС‚Рѕ-Р‘РѕСЃСЃВ».
     */
    boolean isAutoBossClanNotifyEnabled() {
        return prefs.getBoolean(PREF_AUTO_BOSS_CLAN_NOTIFY, false);
    }

    /**
     * РЎРѕС…СЂР°РЅРµРЅРёРµ С„Р»Р°РіР° РєР»Р°РЅ-СѓРІРµРґРѕРјР»РµРЅРёР№.
     * РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ UI-РЅР°СЃС‚СЂРѕР№РєР°РјРё, Р° С„Р°РєС‚РёС‡РµСЃРєР°СЏ РѕС‚РїСЂР°РІРєР° РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ РІ
     * {@link #sendClanBossEventMessageIfNeeded(String, String, String, String)} Рё
     * {@link #sendClanBossFoundMessageIfNeeded()}.
     */
    void setAutoBossClanNotifyEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_BOSS_CLAN_NOTIFY, enabled).apply();
        Log.d(TAG, TRACE_PREFIX + " setAutoBossClanNotifyEnabled=" + enabled);
    }

    /**
     * РџСЂРёР·РЅР°Рє Р°РєС‚РёРІРЅРѕРіРѕ СЃС†РµРЅР°СЂРёСЏ РїРѕРёСЃРєР°/РІС…РѕРґР° РІ Р±РѕР№, РІ РєРѕС‚РѕСЂРѕРј РЅРµР¶РµР»Р°С‚РµР»СЊРЅС‹
     * С„РѕРЅРѕРІС‹Рµ РїРµСЂРµРёРјРµРЅРѕРІР°РЅРёСЏ РєР°СЂС‚С‹ РїРѕ pinfo Рё РёСЃРєСѓСЃСЃС‚РІРµРЅРЅС‹Рµ Р·Р°РґРµСЂР¶РєРё С€Р°РіР° РєР°СЂС‚С‹.
     *
     * РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РєР°Рє runtime-guard РґР»СЏ:
     * - `RoomManager` (РїР°СѓР·Р° `MapRebuildFromPinfo`);
     * - `MapAjax` (РїР°СѓР·Р° `MapCellCheckTimeout` РїСЂРё Р°РєС‚РёРІРЅРѕРј РђРІС‚Рѕ-Р‘РѕСЃСЃРµ).
     */
    boolean shouldPauseMapRebuildFromPinfo() {
        if (!isAutoBossEnabled()) {
            return false;
        }
        BossStage currentStage;
        synchronized (lock) {
            currentStage = stage;
        }
        return currentStage == BossStage.SEARCHING_TARGET
                || currentStage == BossStage.TARGET_FOUND_WAIT_SCROLL
                || currentStage == BossStage.WAIT_FIGHT_START;
    }

    int getAutoBossWaitBeforeScrollSec() {
        int value = prefs.getInt(PREF_AUTO_BOSS_WAIT_SCROLL_SEC, DEFAULT_WAIT_BEFORE_SCROLL_SEC);
        if (value < 1) return 1;
        if (value > 10) return 10;
        return value;
    }

    void setAutoBossWaitBeforeScrollSec(int sec) {
        int safe = sec;
        if (safe < 1) safe = 1;
        if (safe > 10) safe = 10;
        prefs.edit().putInt(PREF_AUTO_BOSS_WAIT_SCROLL_SEC, safe).apply();
    }

    int getAutoBossSearchTimeoutSec() {
        int value = prefs.getInt(PREF_AUTO_BOSS_SEARCH_TIMEOUT_SEC, DEFAULT_SEARCH_TIMEOUT_SEC);
        if (value < 60) return 60;
        if (value > 20 * 60) return 20 * 60;
        return value;
    }

    void setAutoBossSearchTimeoutSec(int sec) {
        int safe = sec;
        if (safe < 60) safe = 60;
        if (safe > 20 * 60) safe = 20 * 60;
        prefs.edit().putInt(PREF_AUTO_BOSS_SEARCH_TIMEOUT_SEC, safe).apply();
    }

    int getAutoBossWaitFightTimeoutSec() {
        int value = prefs.getInt(PREF_AUTO_BOSS_WAIT_FIGHT_TIMEOUT_SEC, DEFAULT_WAIT_FIGHT_TIMEOUT_SEC);
        if (value < 10) return 10;
        if (value > 120) return 120;
        return value;
    }

    void setAutoBossWaitFightTimeoutSec(int sec) {
        int safe = sec;
        if (safe < 10) safe = 10;
        if (safe > 120) safe = 120;
        prefs.edit().putInt(PREF_AUTO_BOSS_WAIT_FIGHT_TIMEOUT_SEC, safe).apply();
    }

    private long getWaitBeforeScrollMs() {
        return getAutoBossWaitBeforeScrollSec() * 1000L;
    }

    private long getSearchTimeoutMs() {
        return getAutoBossSearchTimeoutSec() * 1000L;
    }

    private long getWaitFightTimeoutMs() {
        return getAutoBossWaitFightTimeoutSec() * 1000L;
    }
}
