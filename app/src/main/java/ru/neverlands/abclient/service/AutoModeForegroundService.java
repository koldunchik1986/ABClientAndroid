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
 * Foreground-service Р Т‘Р В»РЎРЏ Р С—Р С•Р Т‘Р Т‘Р ВµРЎР‚Р В¶Р В°Р Р…Р С‘РЎРЏ Р В°Р Р†РЎвЂљР С•-Р С”Р С•Р Р…РЎвЂљРЎС“РЎР‚Р С•Р Р† Р С—РЎР‚Р С‘ Р В·Р В°Р В±Р В»Р С•Р С”Р С‘РЎР‚Р С•Р Р†Р В°Р Р…Р Р…Р С•Р С РЎРЊР С”РЎР‚Р В°Р Р…Р Вµ.
 *
 * Р вЂ”Р В°Р Т‘Р В°РЎвЂЎР С‘:
 * - Р Т‘Р ВµРЎР‚Р В¶Р В°РЎвЂљРЎРЉ Р С—РЎР‚Р С•РЎвЂ Р ВµРЎРѓРЎРѓ Р Р† foreground РЎР‚Р ВµР В¶Р С‘Р СР Вµ Р Р†Р С• Р Р†РЎР‚Р ВµР СРЎРЏ Р В°Р С”РЎвЂљР С‘Р Р†Р Р…Р С•Р С–Р С• Р В°Р Р†РЎвЂљР С•-РЎР‚Р ВµР В¶Р С‘Р СР В°,
 * - РЎС“Р Т‘Р ВµРЎР‚Р В¶Р С‘Р Р†Р В°РЎвЂљРЎРЉ CPU/WiРІР‚вЂFi (РЎвЂЎР ВµРЎР‚Р ВµР В· WakeLock/WifiLock) РЎвЂљР С•Р В»РЎРЉР С”Р С• Р С—Р С•Р С”Р В° Р Р…РЎС“Р В¶Р ВµР Р… Р В°Р Р†РЎвЂљР С•-РЎР‚Р ВµР В¶Р С‘Р С,
 * - Р С—Р ВµРЎР‚Р С‘Р С•Р Т‘Р С‘РЎвЂЎР ВµРЎРѓР С”Р С‘ Р С—Р С‘Р Р…Р С–Р С•Р Р†Р В°РЎвЂљРЎРЉ UI-Р С”Р С•Р Р…РЎвЂљРЎС“РЎР‚ (room polling + auto-turn) РЎвЂЎР ВµРЎР‚Р ВµР В· MainActivity, Р ВµРЎРѓР В»Р С‘ Activity Р В¶Р С‘Р Р†Р В°.
 *
 * Р С›Р С–РЎР‚Р В°Р Р…Р С‘РЎвЂЎР ВµР Р…Р С‘РЎРЏ РЎвЂљР ВµР С”РЎС“РЎвЂ°Р ВµР в„– Р В°РЎР‚РЎвЂ¦Р С‘РЎвЂљР ВµР С”РЎвЂљРЎС“РЎР‚РЎвЂ№:
 * - Р В±Р С•Р ВµР Р†Р С•Р в„– pipeline Р Р†РЎРѓРЎвЂ Р ВµРЎвЂ°РЎвЂ Р С•Р С—Р С‘РЎР‚Р В°Р ВµРЎвЂљРЎРѓРЎРЏ Р Р…Р В° WebView/Activity,
 * - Р ВµРЎРѓР В»Р С‘ Activity РЎС“Р Р…Р С‘РЎвЂЎРЎвЂљР С•Р В¶Р ВµР Р…Р В° РЎРѓР С‘РЎРѓРЎвЂљР ВµР СР С•Р в„–, РЎРѓР ВµРЎР‚Р Р†Р С‘РЎРѓ Р Р…Р Вµ Р СР С•Р В¶Р ВµРЎвЂљ Р С—Р С•Р В»Р Р…Р С•РЎРѓРЎвЂљРЎРЉРЎР‹ Р В·Р В°Р СР ВµР Р…Р С‘РЎвЂљРЎРЉ WebView-Р С”Р С•Р Р…РЎвЂљРЎС“РЎР‚.
 */
public class AutoModeForegroundService extends Service {
    private static final String TAG = "AutoModeFgService";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";

    private static final String CHANNEL_ID = "auto_mode_background";
    private static final int NOTIFICATION_ID = 6201;
    private static final long TICK_INTERVAL_MS = 1000L;
    private static final long NO_ACTIVITY_STOP_TIMEOUT_MS = 60_000L;
    private static final long AUTO_TURN_MIN_INTERVAL_MS = 1000L;
    private static final long ROOM_REFRESH_MIN_INTERVAL_MS = 1000L;
    private static final long CHAT_REFRESH_STALE_GRACE_MS = 3_000L;
    private static final long FIGHT_PULSE_GRACE_MS = 12_000L;

    private static final String ACTION_SYNC = "ru.neverlands.abclient.action.AUTO_BG_SYNC";

    private final Handler handler = createMainHandler();
    private Runnable tickRunnable;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private long lastMainActivitySeenAtMs = 0L;
    private long lastRoomUsersTickAtMs = 0L;
    private long lastAutoTurnTickAtMs = 0L;
    private long lastForcedChatRefreshAtMs = 0L;

    /**
     * Р РЋР С•Р В·Р Т‘Р В°Р ВµРЎвЂљ "Р В°РЎРѓР С‘Р Р…РЎвЂ¦РЎР‚Р С•Р Р…Р Р…РЎвЂ№Р в„–" main handler (API 28+), РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ tick loop РЎРѓР ВµРЎР‚Р Р†Р С‘РЎРѓР В°
     * Р Р…Р Вµ Р В·Р В°Р Р†Р С‘РЎРѓР ВµР В» Р С•РЎвЂљ sync barrier UI-pipeline Р С—РЎР‚Р С‘ lockscreen/background.
     */
    private static Handler createMainHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Handler.createAsync(Looper.getMainLooper());
        }
        return new Handler(Looper.getMainLooper());
    }

    /**
     * Р РЋР С‘Р Р…РЎвЂ¦РЎР‚Р С•Р Р…Р С‘Р В·Р С‘РЎР‚РЎС“Р ВµРЎвЂљ РЎРѓР С•РЎРѓРЎвЂљР С•РЎРЏР Р…Р С‘Р Вµ РЎРѓР ВµРЎР‚Р Р†Р С‘РЎРѓР В° РЎРѓ РЎвЂљР ВµР С”РЎС“РЎвЂ°Р С‘Р СР С‘ Р В°Р Р†РЎвЂљР С•-РЎвЂћР В»Р В°Р С–Р В°Р СР С‘.
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
     * Р вЂўР Т‘Р С‘Р Р…Р В°РЎРЏ Р С—РЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В°: Р Р…РЎС“Р В¶Р ВµР Р… Р В»Р С‘ РЎвЂћР С•Р Р…Р С•Р Р†РЎвЂ№Р в„– РЎР‚Р ВµР В¶Р С‘Р С.
     *
     * Р вЂ™Р С”Р В»РЎР‹РЎвЂЎР В°Р ВµР С РЎРѓР ВµРЎР‚Р Р†Р С‘РЎРѓ, Р ВµРЎРѓР В»Р С‘:
     * - Р В°Р С”РЎвЂљР С‘Р Р†Р ВµР Р… Р С’Р Р†РЎвЂљР С•-Р вЂР С•Р в„–, Р С‘Р В»Р С‘
     * - Р В°Р С”РЎвЂљР С‘Р Р†Р Р…Р С• Р С’Р Р†РЎвЂљР С•-Р СњР В°Р С—Р В°Р Т‘Р ВµР Р…Р С‘Р Вµ + Р Р†Р С”Р В»РЎР‹РЎвЂЎР ВµР Р…Р С• Р РЋР В»Р ВµР В¶Р ВµР Р…Р С‘Р Вµ Р В·Р В° Р В»Р С•Р С”Р В°РЎвЂ Р С‘Р ВµР в„–.
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
        startForeground(NOTIFICATION_ID, buildNotification("Р С’Р Р†РЎвЂљР С•-РЎР‚Р ВµР В¶Р С‘Р С РЎР‚Р В°Р В±Р С•РЎвЂљР В°Р ВµРЎвЂљ Р Р† РЎвЂћР С•Р Р…Р Вµ"));
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

                if (locationTrackingEnabled) {
                    if (tickNow - lastRoomUsersTickAtMs >= roomTickIntervalMs) {
                        activity.requestRoomUsersRefreshSoon();
                        lastRoomUsersTickAtMs = tickNow;
                    } else {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: room refresh throttled, remainingMs="
                                + (roomTickIntervalMs - (tickNow - lastRoomUsersTickAtMs)));
                    }
                }

                if (autoFightEnabled && fightLikelyActive && !captchaDialogVisible) {
                    // Recovery Р С—Р С•РЎРѓР В»Р Вµ renderer restart/РЎРѓРЎР‚РЎвЂ№Р Р†Р В° Р Р…Р В°Р Р†Р С‘Р С–Р В°РЎвЂ Р С‘Р С‘:
                    // Р ВµРЎРѓР В»Р С‘ Р В±Р С•Р в„– Р В°Р С”РЎвЂљР С‘Р Р†Р ВµР Р… Р С—Р С• AppVars, Р Р…Р С• top frame РЎС“РЎв‚¬РЎвЂР В» РЎРѓ fight.frame,
                    // РЎвЂћР С•РЎР‚РЎРѓР С‘РЎР‚РЎС“Р ВµР С Р Р†Р С•Р В·Р Р†РЎР‚Р В°РЎвЂљ Р Р…Р В° Р В±Р С•Р ВµР Р†Р С•Р в„– URL c cooldown.                    if (tickNow - lastAutoTurnTickAtMs >= AUTO_TURN_MIN_INTERVAL_MS) {
                        activity.requestAutoTurn();
                        lastAutoTurnTickAtMs = tickNow;
                    } else {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: autoTurn throttled, remainingMs="
                                + (AUTO_TURN_MIN_INTERVAL_MS - (tickNow - lastAutoTurnTickAtMs)));
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
     * Р ВµРЎРѓР В»Р С‘ Р С—Р ВµРЎР‚Р С‘Р С•Р Т‘Р С‘РЎвЂЎР ВµРЎРѓР С”Р С‘Р в„– `ch.php?show=1` Р Р† Activity "Р В·Р В°Р СР С•Р В»РЎвЂЎР В°Р В»" Р Р† РЎвЂћР С•Р Р…Р Вµ,
     * РЎРѓР ВµРЎР‚Р Р†Р С‘РЎРѓ РЎвЂћР С•РЎР‚РЎРѓР С‘РЎР‚РЎС“Р ВµРЎвЂљ РЎР‚Р В°Р В·Р С•Р Р†РЎвЂ№Р в„– refresh, РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ Р Р…Р Вµ Р С—РЎР‚Р С•Р С—РЎС“РЎРѓР С”Р В°РЎвЂљРЎРЉ Р Р†РЎвЂ¦Р С•Р Т‘РЎРЏРЎвЂ°Р С‘Р Вµ РЎРѓР С•Р В±РЎвЂ№РЎвЂљР С‘РЎРЏ Р В±Р С•РЎРЏ.
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

        // Р СњР В°РЎвЂЎР В°Р В»РЎРЉР Р…РЎвЂ№Р в„– bootstrap: Р ВµРЎРѓР В»Р С‘ Activity Р ВµРЎвЂ°Р Вµ Р Р…Р Вµ Р Т‘Р ВµР В»Р В°Р В»Р В° Р Р…Р С‘ Р С•Р Т‘Р Р…Р С•Р С–Р С• poll-Р В·Р В°Р С—РЎР‚Р С•РЎРѓР В°.
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
        // 1) Р РЋР В°Р СРЎвЂ№Р в„– Р Р…Р В°Р Т‘РЎвЂР В¶Р Р…РЎвЂ№Р в„– РЎРѓР С‘Р С–Р Р…Р В°Р В»: Р Р† Р С”Р ВµРЎв‚¬Р Вµ main.php Р ВµРЎРѓРЎвЂљРЎРЉ Р В±Р С•Р ВµР Р†РЎвЂ№Р Вµ Р СР В°РЎР‚Р С”Р ВµРЎР‚РЎвЂ№.
        String mainHtml = AppVars.ContentMainPhp;
        if (mainHtml != null && (mainHtml.contains("var fight_ty") || mainHtml.contains("magic_slots();"))) {
            return true;
        }

        // 2) Р СџРЎР‚РЎРЏР СР С•Р в„– РЎРѓР С‘Р С–Р Р…Р В°Р В» Р С•Р С”Р С•Р Р…РЎвЂЎР В°Р Р…Р С‘РЎРЏ/Р В±Р С•Р ВµР Р†Р С•Р С–Р С• action-link.
        String fightLink = AppVars.FightLink;
        if (fightLink != null && fightLink.contains("get_id=61&act=")) {
            return true;
        }

        // 3) "Р вЂР С•Р ВµР Р†Р С•Р в„– Р С—РЎС“Р В»РЎРЉРЎРѓ" Р В·Р В° Р С—Р С•РЎРѓР В»Р ВµР Т‘Р Р…Р С‘Р Вµ N РЎРѓР ВµР С”РЎС“Р Р…Р Т‘.
        // Р СњРЎС“Р В¶Р ВµР Р… Р Т‘Р В»РЎРЏ Р С—Р ВµРЎР‚Р ВµРЎвЂ¦Р С•Р Т‘Р Р…РЎвЂ№РЎвЂ¦ Р С”Р В°Р Т‘РЎР‚Р С•Р Р†, Р С–Р Т‘Р Вµ URL/HTML Р С”РЎР‚Р В°РЎвЂљР С”Р С• РЎвЂљР ВµРЎР‚РЎРЏРЎР‹РЎвЂљ fight-Р СР В°РЎР‚Р С”Р ВµРЎР‚РЎвЂ№.
        long pulseAtMs = AppVars.LastFightPulseAtMs;
        boolean recentFightPulse = pulseAtMs > 0L
                && (System.currentTimeMillis() - pulseAtMs) <= FIGHT_PULSE_GRACE_MS;
        if (!recentFightPulse) {
            return false;
        }

        // 4) URL РЎРѓР В°Р СР С‘ Р С—Р С• РЎРѓР ВµР В±Р Вµ Р Р…Р ВµР Р…Р В°Р Т‘РЎвЂР В¶Р Р…РЎвЂ№ (go=inf Р СР С•Р В¶Р ВµРЎвЂљ Р Р†Р ВµРЎР‚Р Р…РЎС“РЎвЂљРЎРЉ Р Р…Р ВµР В±Р С•Р ВµР Р†Р С•Р в„– html), Р Р…Р С• Р Р† РЎРѓР С•РЎвЂЎР ВµРЎвЂљР В°Р Р…Р С‘Р С‘ РЎРѓ recent pulse
        // Р Т‘Р В°РЎР‹РЎвЂљ РЎС“РЎРѓРЎвЂљР С•Р в„–РЎвЂЎР С‘Р Р†Р С•Р Вµ Р С•Р С—РЎР‚Р ВµР Т‘Р ВµР В»Р ВµР Р…Р С‘Р Вµ Р В°Р С”РЎвЂљР С‘Р Р†Р Р…Р С•Р С–Р С• Р В±Р С•РЎРЏ.
        if (activity != null && activity.getMainWebView() != null) {
            String currentMainUrl = activity.getMainWebView().getUrl();
            if (currentMainUrl != null && currentMainUrl.contains("get_id=56&act=10&go=inf")) {
                return true;
            }
        }
        String topUrl = AppVars.url_main_top;
        if (topUrl != null && topUrl.contains("get_id=56&act=10&go=inf")) {
            return true;
        }

        return false;
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
                "Р В¤Р С•Р Р…Р С•Р Р†РЎвЂ№Р в„– Р В°Р Р†РЎвЂљР С•-РЎР‚Р ВµР В¶Р С‘Р С",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Р СџР С•Р Т‘Р Т‘Р ВµРЎР‚Р В¶Р С‘Р Р†Р В°Р ВµРЎвЂљ Р В°Р Р†РЎвЂљР С•-РЎР‚Р ВµР В¶Р С‘Р СРЎвЂ№ Р С—РЎР‚Р С‘ Р В·Р В°Р В±Р В»Р С•Р С”Р С‘РЎР‚Р С•Р Р†Р В°Р Р…Р Р…Р С•Р С РЎРЊР С”РЎР‚Р В°Р Р…Р Вµ");
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
                .setContentTitle("ABClient: РЎвЂћР С•Р Р…Р С•Р Р†РЎвЂ№Р в„– Р В°Р Р†РЎвЂљР С•-РЎР‚Р ВµР В¶Р С‘Р С")
                .setContentText(contentText)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();
    }
}

