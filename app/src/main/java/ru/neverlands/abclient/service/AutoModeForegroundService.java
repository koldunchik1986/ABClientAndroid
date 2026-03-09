package ru.neverlands.abclient.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;


import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.R;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Foreground-service РґР»СЏ РїРѕРґРґРµСЂР¶Р°РЅРёСЏ Р°РІС‚Рѕ-РєРѕРЅС‚СѓСЂРѕРІ РїСЂРё Р·Р°Р±Р»РѕРєРёСЂРѕРІР°РЅРЅРѕРј СЌРєСЂР°РЅРµ.
 *
 * Р—Р°РґР°С‡Рё:
 * - РґРµСЂР¶Р°С‚СЊ РїСЂРѕС†РµСЃСЃ РІ foreground СЂРµР¶РёРјРµ РІРѕ РІСЂРµРјСЏ Р°РєС‚РёРІРЅРѕРіРѕ Р°РІС‚Рѕ-СЂРµР¶РёРјР°,
 * - СѓРґРµСЂР¶РёРІР°С‚СЊ CPU/WiвЂ‘Fi (С‡РµСЂРµР· WakeLock/WifiLock) С‚РѕР»СЊРєРѕ РїРѕРєР° РЅСѓР¶РµРЅ Р°РІС‚Рѕ-СЂРµР¶РёРј,
 * - РїРµСЂРёРѕРґРёС‡РµСЃРєРё РїРёРЅРіРѕРІР°С‚СЊ UI-РєРѕРЅС‚СѓСЂ (room polling + auto-turn) С‡РµСЂРµР· MainActivity, РµСЃР»Рё Activity Р¶РёРІР°.
 *
 * РћРіСЂР°РЅРёС‡РµРЅРёСЏ С‚РµРєСѓС‰РµР№ Р°СЂС…РёС‚РµРєС‚СѓСЂС‹:
 * - Р±РѕРµРІРѕР№ pipeline РІСЃС‘ РµС‰С‘ РѕРїРёСЂР°РµС‚СЃСЏ РЅР° WebView/Activity,
 * - РµСЃР»Рё Activity СѓРЅРёС‡С‚РѕР¶РµРЅР° СЃРёСЃС‚РµРјРѕР№, СЃРµСЂРІРёСЃ РЅРµ РјРѕР¶РµС‚ РїРѕР»РЅРѕСЃС‚СЊСЋ Р·Р°РјРµРЅРёС‚СЊ WebView-РєРѕРЅС‚СѓСЂ.
 */
public class AutoModeForegroundService extends Service {
    private static final String TAG = "AutoModeFgService";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";

    private static final String CHANNEL_ID = "auto_mode_background";
    private static final int NOTIFICATION_ID = 6201;
    private static final long TICK_INTERVAL_MS = 1000L;
    private static final long NO_ACTIVITY_STOP_TIMEOUT_MS = 60_000L;
    private static final long AUTO_TURN_MIN_INTERVAL_MS = 1000L;
    private static final long AUTO_TURN_IDLE_PROBE_INTERVAL_MS = 2_000L;
    private static final long ROOM_REFRESH_MIN_INTERVAL_MS = 1000L;
    private static final long CHAT_REFRESH_STALE_GRACE_MS = 3_000L;
    private static final long FIGHT_PULSE_GRACE_MS = 12_000L;
    private static final long AUTO_FIGHT_FINISH_MIN_INTERVAL_MS = 1_500L;

    private static final String ACTION_SYNC = "ru.neverlands.abclient.action.AUTO_BG_SYNC";

    private final Handler handler = createMainHandler();
    private Runnable tickRunnable;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private long lastMainActivitySeenAtMs = 0L;
    private long lastRoomUsersTickAtMs = 0L;
    private long lastAutoTurnTickAtMs = 0L;
    private long lastForcedChatRefreshAtMs = 0L;
    private long lastAutoFightFinishDispatchAtMs = 0L;
    private String lastAutoFightFinishLink = "";

    /**
     * РЎРѕР·РґР°РµС‚ "Р°СЃРёРЅС…СЂРѕРЅРЅС‹Р№" main handler (API 28+), С‡С‚РѕР±С‹ tick loop СЃРµСЂРІРёСЃР°
     * РЅРµ Р·Р°РІРёСЃРµР» РѕС‚ sync barrier UI-pipeline РїСЂРё lockscreen/background.
     */
    private static Handler createMainHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Handler.createAsync(Looper.getMainLooper());
        }
        return new Handler(Looper.getMainLooper());
    }

    /**
     * РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµС‚ СЃРѕСЃС‚РѕСЏРЅРёРµ СЃРµСЂРІРёСЃР° СЃ С‚РµРєСѓС‰РёРјРё Р°РІС‚Рѕ-С„Р»Р°РіР°РјРё.
     */
    public static void syncServiceState(Context context, String reason) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        boolean shouldRun = shouldRunInBackground(appContext);
        Log.d(TAG, BG_TRACE_PREFIX + " syncServiceState: shouldRun=" + shouldRun + ", reason=" + reason);
        if (shouldRun) {
            Intent intent = new Intent(appContext, AutoModeForegroundService.class);
            intent.setAction(ACTION_SYNC);
            intent.putExtra("reason", reason);
            ContextCompat.startForegroundService(appContext, intent);
        } else {
            appContext.stopService(new Intent(appContext, AutoModeForegroundService.class));
        }
    }

    /**
     * Р•РґРёРЅР°СЏ РїСЂРѕРІРµСЂРєР°: РЅСѓР¶РµРЅ Р»Рё С„РѕРЅРѕРІС‹Р№ СЂРµР¶РёРј.
     *
     * Р’РєР»СЋС‡Р°РµРј СЃРµСЂРІРёСЃ, РµСЃР»Рё:
     * - Р°РєС‚РёРІРµРЅ РђРІС‚Рѕ-Р‘РѕР№, РёР»Рё
     * - Р°РєС‚РёРІРЅРѕ РђРІС‚Рѕ-РќР°РїР°РґРµРЅРёРµ + РІРєР»СЋС‡РµРЅРѕ РЎР»РµР¶РµРЅРёРµ Р·Р° Р»РѕРєР°С†РёРµР№.
     */
    public static boolean shouldRunInBackground(Context context) {
        try {
            AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(context);
            boolean autoFightEnabled = autoFunctionsManager.isAutoFightEnabled()
                    || AppVars.Autoboi == AutoboiState.AutoboiOn;
            boolean autoAttackEnabled = autoFunctionsManager.isAutoAttackEnabled();
            boolean locationTrackingEnabled = autoFunctionsManager.isLocationTrackingEnabled();
            return autoFightEnabled || (autoAttackEnabled && locationTrackingEnabled);
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " shouldRunInBackground: fallback by AppVars.Autoboi", e);
            return AppVars.Autoboi == AutoboiState.AutoboiOn;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannelIfNeeded();
        startForeground(NOTIFICATION_ID, buildNotification("РђРІС‚Рѕ-СЂРµР¶РёРј СЂР°Р±РѕС‚Р°РµС‚ РІ С„РѕРЅРµ"));
        ensureLocks();
        Log.d(TAG, BG_TRACE_PREFIX + " onCreate: foreground started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String reason = intent != null ? intent.getStringExtra("reason") : "unknown";
        Log.d(TAG, BG_TRACE_PREFIX + " onStartCommand: reason=" + reason + ", startId=" + startId);
        startTickLoop();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopTickLoop();
        releaseLocks();
        Log.d(TAG, BG_TRACE_PREFIX + " onDestroy: foreground stopped");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startTickLoop() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
        }
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    boolean shouldRun = shouldRunInBackground(AutoModeForegroundService.this);
                    if (!shouldRun) {
                        Log.d(TAG, BG_TRACE_PREFIX + " tick: stopSelf (flags disabled)");
                        stopSelf();
                        return;
                    }

                    ensureLocks();
                    runBackgroundTick();
                } catch (Exception e) {
                    Log.e(TAG, BG_TRACE_PREFIX + " tick: error", e);
                } finally {
                    if (tickRunnable != null) {
                        handler.postDelayed(this, TICK_INTERVAL_MS);
                    }
                }
            }
        };
        handler.post(tickRunnable);
    }

    private void stopTickLoop() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }
    }

    private void runBackgroundTick() {
        MainActivity activity = (AppVars.mainActivity != null) ? AppVars.mainActivity.get() : null;
        long now = System.currentTimeMillis();
        if (activity == null) {
            if (lastMainActivitySeenAtMs == 0L) {
                lastMainActivitySeenAtMs = now;
            }
            long noActivityForMs = now - lastMainActivitySeenAtMs;
            Log.d(TAG, BG_TRACE_PREFIX + " tick: mainActivity=null, noActivityForMs=" + noActivityForMs);
            if (noActivityForMs >= NO_ACTIVITY_STOP_TIMEOUT_MS) {
                Log.d(TAG, BG_TRACE_PREFIX + " tick: stopSelf (no activity for too long)");
                stopSelf();
            }
            return;
        }

        lastMainActivitySeenAtMs = now;
        activity.runOnUiThread(() -> {
            try {
                AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(activity);
                boolean locationTrackingEnabled = autoFunctionsManager.isLocationTrackingEnabled();
                boolean autoFightEnabled = autoFunctionsManager.isAutoFightEnabled()
                        || AppVars.Autoboi == AutoboiState.AutoboiOn;
                boolean captchaDialogVisible = AppVars.IsFightCaptchaDialogVisible;
                int walkersPollIntervalSec = autoFunctionsManager.getWalkersPollIntervalSec();
                long roomTickIntervalMs = Math.max(
                        ROOM_REFRESH_MIN_INTERVAL_MS,
                        walkersPollIntervalSec * 1000L
                );
                long tickNow = System.currentTimeMillis();

                boolean fightLikelyActive = isFightSessionLikelyActive(activity);
                Log.d(TAG, BG_TRACE_PREFIX + " uiTick: locationTracking=" + locationTrackingEnabled
                        + ", autoFight=" + autoFightEnabled
                        + ", captchaDialogVisible=" + captchaDialogVisible
                        + ", walkersPollIntervalSec=" + walkersPollIntervalSec
                        + ", fightLikelyActive=" + fightLikelyActive);

                ensureChatRefreshAlive(activity, tickNow);

                // РџСЂРёРѕСЂРёС‚РµС‚РЅР°СЏ РѕС‚РїСЂР°РІРєР° СЃСЃС‹Р»РєРё "Р—Р°РІРµСЂС€РёС‚СЊ Р±РѕР№" (act=7), РµСЃР»Рё РѕРЅР° СѓР¶Рµ РіРѕС‚РѕРІР°.
                // Р‘РµР· СЌС‚РѕРіРѕ С†РёРєР» РјРѕР¶РµС‚ Р·Р°РІРёСЃР°С‚СЊ: autoTurn РєСЂСѓС‚РёС‚СЃСЏ РїРѕ РѕРґРЅРѕРјСѓ Рё С‚РѕРјСѓ Р¶Рµ fight-frame,
                // LezFight СЃРѕР±РёСЂР°РµС‚ AppVars.FightLink, РЅРѕ СЃР°Рј URL Р·Р°РІРµСЂС€РµРЅРёСЏ РЅРёРєРѕРіРґР° РЅРµ РѕС‚РєСЂС‹РІР°РµС‚СЃСЏ.
                //
                // Р—Р°РІРёСЃРёРјРѕСЃС‚Рё:
                // - AppVars.FightLink: С„РѕСЂРјРёСЂСѓРµС‚СЃСЏ РІ LezFight.ParseNonFight()/BuildFightLink(...);
                // - MainActivity.getMainWebView().loadUrl(...): С„Р°РєС‚РёС‡РµСЃРєР°СЏ РѕС‚РїСЂР°РІРєР° act=7;
                // - anti-loop guard: lastAutoFightFinishDispatchAtMs + lastAutoFightFinishLink.
                String pendingFightFinishLink = normalizeNeverlandsUrl(AppVars.FightLink);
                boolean canDispatchFightFinish = autoFightEnabled
                        && !captchaDialogVisible
                        && isReadyFightFinishLink(pendingFightFinishLink);
                if (canDispatchFightFinish) {
                    long sinceLastFinishDispatch = tickNow - lastAutoFightFinishDispatchAtMs;
                    boolean sameAsLastFinish = pendingFightFinishLink.equals(lastAutoFightFinishLink);
                    if (!sameAsLastFinish || sinceLastFinishDispatch >= AUTO_FIGHT_FINISH_MIN_INTERVAL_MS) {
                        if (activity.getMainWebView() != null) {
                            Log.d(TAG, BG_TRACE_PREFIX + " uiTick: dispatch fight finish link: " + pendingFightFinishLink);
                            activity.getMainWebView().loadUrl(pendingFightFinishLink);
                            lastAutoFightFinishDispatchAtMs = tickNow;
                            lastAutoFightFinishLink = pendingFightFinishLink;
                        } else {
                            Log.w(TAG, BG_TRACE_PREFIX + " uiTick: skip fight finish dispatch, mainWebView=null");
                        }
                        // Р§РёСЃС‚РёРј СЃСЃС‹Р»РєСѓ РїРѕСЃР»Рµ РїРѕРїС‹С‚РєРё РѕС‚РїСЂР°РІРєРё, С‡С‚РѕР±С‹ РЅРµ РґСѓР±Р»РёСЂРѕРІР°С‚СЊ РІ СЃР»РµРґСѓСЋС‰РµРј С‚РёРєРµ.
                        AppVars.FightLink = "";
                        return;
                    } else {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: fight finish dispatch throttled, remainingMs="
                                + (AUTO_FIGHT_FINISH_MIN_INTERVAL_MS - sinceLastFinishDispatch));
                    }
                }

                if (locationTrackingEnabled) {
                    if (tickNow - lastRoomUsersTickAtMs >= roomTickIntervalMs) {
                        activity.requestRoomUsersRefreshSoon();
                        lastRoomUsersTickAtMs = tickNow;
                    } else {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: room refresh throttled, remainingMs="
                                + (roomTickIntervalMs - (tickNow - lastRoomUsersTickAtMs)));
                    }
                }

                if (autoFightEnabled && !captchaDialogVisible) {
                    // Background-safe polling: do auto-turn/probe even when fightLikelyActive=false.
                    long minIntervalMs = fightLikelyActive
                            ? AUTO_TURN_MIN_INTERVAL_MS
                            : AUTO_TURN_IDLE_PROBE_INTERVAL_MS;
                    long sinceLastAutoTurnMs = tickNow - lastAutoTurnTickAtMs;
                    if (sinceLastAutoTurnMs >= minIntervalMs) {
                        if (!fightLikelyActive) {
                            Log.d(TAG, BG_TRACE_PREFIX + " uiTick: autoTurn idle probe");
                        }
                        activity.requestAutoTurnBackgroundAware();
                        lastAutoTurnTickAtMs = tickNow;
                    } else {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: autoTurn throttled, remainingMs="
                                + (minIntervalMs - sinceLastAutoTurnMs)
                                + ", fightLikelyActive=" + fightLikelyActive);
                    }
                } else if (autoFightEnabled && captchaDialogVisible) {
                    Log.d(TAG, BG_TRACE_PREFIX + " uiTick: skip autoTurn, captcha dialog visible");
                }
            } catch (Exception e) {
                Log.e(TAG, BG_TRACE_PREFIX + " uiTick: failed", e);
            }
        });
    }

    /**
     * Watchdog chat polling:
     * РµСЃР»Рё РїРµСЂРёРѕРґРёС‡РµСЃРєРёР№ `ch.php?show=1` РІ Activity "Р·Р°РјРѕР»С‡Р°Р»" РІ С„РѕРЅРµ,
     * СЃРµСЂРІРёСЃ С„РѕСЂСЃРёСЂСѓРµС‚ СЂР°Р·РѕРІС‹Р№ refresh, С‡С‚РѕР±С‹ РЅРµ РїСЂРѕРїСѓСЃРєР°С‚СЊ РІС…РѕРґСЏС‰РёРµ СЃРѕР±С‹С‚РёСЏ Р±РѕСЏ.
     */
    private void ensureChatRefreshAlive(MainActivity activity, long tickNow) {
        if (activity == null) {
            return;
        }
        if (!activity.isChatRefreshEnabled()) {
            return;
        }
        int refreshSeconds = Math.max(1, activity.getChatRefreshSeconds());
        long refreshIntervalMs = refreshSeconds * 1000L;
        long staleThresholdMs = refreshIntervalMs + CHAT_REFRESH_STALE_GRACE_MS;
        long lastChatRefreshAtMs = activity.getLastChatRefreshAtMs();
        long forceCooldownMs = Math.max(1_000L, refreshIntervalMs);

        // РќР°С‡Р°Р»СЊРЅС‹Р№ bootstrap: РµСЃР»Рё Activity РµС‰Рµ РЅРµ РґРµР»Р°Р»Р° РЅРё РѕРґРЅРѕРіРѕ poll-Р·Р°РїСЂРѕСЃР°.
        if (lastChatRefreshAtMs <= 0L) {
            if (tickNow - lastForcedChatRefreshAtMs >= forceCooldownMs) {
                Log.d(TAG, BG_TRACE_PREFIX + " uiTick: chat refresh bootstrap");
                activity.requestChatRefreshNow();
                lastForcedChatRefreshAtMs = tickNow;
            }
            return;
        }

        long staleForMs = tickNow - lastChatRefreshAtMs;
        if (staleForMs < staleThresholdMs) {
            return;
        }
        if (tickNow - lastForcedChatRefreshAtMs < forceCooldownMs) {
            return;
        }

        Log.w(TAG, BG_TRACE_PREFIX + " uiTick: chat refresh watchdog, staleForMs=" + staleForMs
                + ", refreshIntervalMs=" + refreshIntervalMs);
        activity.requestChatRefreshNow();
        lastForcedChatRefreshAtMs = tickNow;
    }

    private boolean isFightSessionLikelyActive(MainActivity activity) {
        // 1) РЎР°РјС‹Р№ РЅР°РґС‘Р¶РЅС‹Р№ СЃРёРіРЅР°Р»: РІ РєРµС€Рµ main.php РµСЃС‚СЊ Р±РѕРµРІС‹Рµ РјР°СЂРєРµСЂС‹.
        String mainHtml = AppVars.ContentMainPhp;
        if (mainHtml != null && (mainHtml.contains("var fight_ty") || mainHtml.contains("magic_slots();"))) {
            return true;
        }

        // 2) РџСЂСЏРјРѕР№ СЃРёРіРЅР°Р» РѕРєРѕРЅС‡Р°РЅРёСЏ/Р±РѕРµРІРѕРіРѕ action-link.
        String fightLink = AppVars.FightLink;
        if (fightLink != null && fightLink.contains("get_id=61&act=")) {
            return true;
        }

        // 3) "Р‘РѕРµРІРѕР№ РїСѓР»СЊСЃ" Р·Р° РїРѕСЃР»РµРґРЅРёРµ N СЃРµРєСѓРЅРґ.
        // РќСѓР¶РµРЅ РґР»СЏ РїРµСЂРµС…РѕРґРЅС‹С… РєР°РґСЂРѕРІ, РіРґРµ URL/HTML РєСЂР°С‚РєРѕ С‚РµСЂСЏСЋС‚ fight-РјР°СЂРєРµСЂС‹.
        long pulseAtMs = AppVars.LastFightPulseAtMs;
        boolean recentFightPulse = pulseAtMs > 0L
                && (System.currentTimeMillis() - pulseAtMs) <= FIGHT_PULSE_GRACE_MS;
        // Р Р°РЅРµРµ Р·РґРµСЃСЊ Р±С‹Р»Р° РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅР°СЏ Р¶С‘СЃС‚РєР°СЏ РїСЂРѕРІРµСЂРєР° URL (go=inf),
        // РёР·-Р·Р° РєРѕС‚РѕСЂРѕР№ РІ С„РѕРЅРµ fightLikelyActive С‡Р°СЃС‚Рѕ РѕСЃС‚Р°РІР°Р»СЃСЏ false РґР°Р¶Рµ РїСЂРё Р¶РёРІРѕРј Р±РѕРµРІРѕРј pulse.
        // Р”Р»СЏ lockscreen/background РґРѕСЃС‚Р°С‚РѕС‡РЅРѕ СЃР°РјРѕРіРѕ СЃРІРµР¶РµРіРѕ pulse вЂ” РѕРЅ РѕР±РЅРѕРІР»СЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ Р±РѕРµРІС‹Рј HTML.
        if (recentFightPulse) {
            return true;
        }

        return false;
    }

    /**
     * Р“РѕС‚РѕРІР°СЏ СЃСЃС‹Р»РєР° Р·Р°РІРµСЂС€РµРЅРёСЏ Р±РѕСЏ (act=7) Р±РµР· placeholder РєР°РїС‡Рё.
     */
    private boolean isReadyFightFinishLink(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.contains("get_id=61")
                && url.contains("act=7")
                && !url.contains("code=????");
    }

    /**
     * РќРѕСЂРјР°Р»РёР·Р°С†РёСЏ РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅС‹С… neverlands-СЃСЃС‹Р»РѕРє РґРѕ Р°Р±СЃРѕР»СЋС‚РЅРѕРіРѕ URL.
     */
    private String normalizeNeverlandsUrl(String url) {
        if (url == null) {
            return "";
        }
        String normalized = url.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("/")) {
            return "http://neverlands.ru" + normalized;
        }
        return "http://neverlands.ru/" + normalized;
    }

    @SuppressWarnings("deprecation")
    private void ensureLocks() {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "ru.neverlands.abclient:AutoModeWakeLock");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
            Log.d(TAG, BG_TRACE_PREFIX + " ensureLocks: wakeLock acquired");
        }

        if (wifiLock == null) {
            try {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    int wifiLockMode = WifiManager.WIFI_MODE_FULL;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        wifiLockMode = WifiManager.WIFI_MODE_FULL_LOW_LATENCY;
                    }
                    wifiLock = wifiManager.createWifiLock(
                            wifiLockMode,
                            "ru.neverlands.abclient:AutoModeWifiLock");
                    wifiLock.setReferenceCounted(false);
                }
            } catch (Exception e) {
                Log.w(TAG, BG_TRACE_PREFIX + " ensureLocks: wifiLock unavailable", e);
            }
        }
        if (wifiLock != null && !wifiLock.isHeld()) {
            wifiLock.acquire();
            Log.d(TAG, BG_TRACE_PREFIX + " ensureLocks: wifiLock acquired");
        }
    }

    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, BG_TRACE_PREFIX + " releaseLocks: wakeLock released");
        }
        wakeLock = null;

        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            Log.d(TAG, BG_TRACE_PREFIX + " releaseLocks: wifiLock released");
        }
        wifiLock = null;
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Р¤РѕРЅРѕРІС‹Р№ Р°РІС‚Рѕ-СЂРµР¶РёРј",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("РџРѕРґРґРµСЂР¶РёРІР°РµС‚ Р°РІС‚Рѕ-СЂРµР¶РёРјС‹ РїСЂРё Р·Р°Р±Р»РѕРєРёСЂРѕРІР°РЅРЅРѕРј СЌРєСЂР°РЅРµ");
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String contentText) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("ABClient: С„РѕРЅРѕРІС‹Р№ Р°РІС‚Рѕ-СЂРµР¶РёРј")
                .setContentText(contentText)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();
    }
}

