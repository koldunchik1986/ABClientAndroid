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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.R;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.manager.AppTimerManager;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.RuntimeNetTrace;

/**
 * Foreground-service для поддержания авто-контуров при заблокированном экране.
 *
 * Задачи:
 * - держать процесс в foreground-режиме во время активного авто-режима,
 * - удерживать CPU/Wi-Fi (через WakeLock/WifiLock) только пока нужен авто-режим,
 * - периодически пинговать UI-контур (room polling + auto-turn) через MainActivity, если Activity жива.
 *
 * Ограничения текущей архитектуры:
 * - боевой pipeline все еще опирается на WebView/Activity,
 * - если Activity уничтожена системой, сервис не может полностью заменить WebView-контур.
 */
public class AutoModeForegroundService extends Service {
    private static final String TAG = "AutoModeFgService";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";

    private static final String CHANNEL_ID = "auto_mode_background";
    private static final int NOTIFICATION_ID = 6201;
    private static final long NOTIFICATION_MIN_UPDATE_MS = 800L;
    private static final long TICK_INTERVAL_MS = 1000L;
    private static final long NO_ACTIVITY_STOP_TIMEOUT_MS = 60_000L;
    private static final long AUTO_TURN_MIN_INTERVAL_MS = 1000L;
    private static final long AUTO_TURN_IDLE_PROBE_INTERVAL_MS = 2_000L;
    private static final long POST_FIGHT_PIPELINE_MIN_INTERVAL_MS = 1_500L;
    private static final long ROOM_REFRESH_MIN_INTERVAL_MS = 1000L;
    private static final long CHAT_REFRESH_STALE_GRACE_MS = 3_000L;
    private static final long FIGHT_PULSE_GRACE_MS = 12_000L;
    private static final long FIGHT_FINISH_PULSE_GRACE_MS = 3_500L;
    private static final long FIGHT_ANNOUNCE_GRACE_MS = 25_000L;
    private static final long FORCE_FIGHT_SYNC_MIN_INTERVAL_MS = 2_500L;
    private static final long AUTO_FIGHT_FINISH_MIN_INTERVAL_MS = 1_500L;
    /**
     * Минимальный интервал между одинаковыми broadcast-запросами на показ боевой капчи.
     *
     * Назначение:
     * - гасит "переоткрытие" одного и того же popup при повторных тиках сервиса;
     * - работает как первый слой антидребезга до проверки submit-key.
     */
    private static final long FIGHT_CAPTCHA_BROADCAST_DEDUP_MS = 8_000L;
    /**
     * Временное окно защиты после отправки code=... для того же finish-key.
     *
     * Назначение:
     * - не показывать повторно тот же challenge сразу после submit;
     * - дать серверу время прислать новое состояние кадра боя.
     */
    private static final long FIGHT_CAPTCHA_SUBMIT_GUARD_TTL_MS = 20_000L;

    private static final String ACTION_SYNC = "ru.neverlands.abclient.action.AUTO_BG_SYNC";

    private final Handler handler = createMainHandler();
    private Runnable tickRunnable;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private long lastMainActivitySeenAtMs = 0L;
    private long lastRoomUsersTickAtMs = 0L;
    private long lastAutoTurnTickAtMs = 0L;
    private long lastForcedChatRefreshAtMs = 0L;
    private long lastPostFightPipelineTickAtMs = 0L;
    private long lastAutoFightFinishDispatchAtMs = 0L;
    private String lastAutoFightFinishLink = "";
    private long lastForceFightSyncAtMs = 0L;
    private long lastFightCaptchaBroadcastAtMs = 0L;
    private String lastFightCaptchaBroadcastKey = "";
    private long lastNotificationUpdateAtMs = 0L;
    private String lastNotificationContentText = "";
    private long lastClientActionAtMs = 0L;
    private String lastClientAction = "ожидание";

    /**
     * Создает "асинхронный" main handler (API 28+), чтобы tick loop сервиса
     * не зависел от sync barrier UI-pipeline при lockscreen/background.
     */
    private static Handler createMainHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Handler.createAsync(Looper.getMainLooper());
        }
        return new Handler(Looper.getMainLooper());
    }

    /**
     * Синхронизирует состояние сервиса с текущими авто-флагами.
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
     * Единая проверка: нужен ли фоновый режим.
     *
     * Включаем сервис, если:
     * - активен Авто-Бой, или
     * - активно Авто-Нападение + включено Слежение за локацией.
     */
    public static boolean shouldRunInBackground(Context context) {
        try {
            AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(context);
            boolean autoFightEnabled = autoFunctionsManager.isAutoFightEnabled()
                    || AppVars.Autoboi == AutoboiState.AutoboiOn;
            boolean autoAttackEnabled = autoFunctionsManager.isAutoAttackEnabled();
            boolean locationTrackingEnabled = autoFunctionsManager.isLocationTrackingEnabled();
            boolean autoCompassEnabled = autoFunctionsManager.isAutoCompassEnabled();
            boolean autoBossEnabled = autoFunctionsManager.isAutoBossEnabled();
            return autoFightEnabled
                    || (autoAttackEnabled && locationTrackingEnabled)
                    || autoCompassEnabled
                    || autoBossEnabled;
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " shouldRunInBackground: fallback by AppVars.Autoboi", e);
            return AppVars.Autoboi == AutoboiState.AutoboiOn;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannelIfNeeded();
        markClientAction("Сервис запущен");
        startForeground(NOTIFICATION_ID, buildNotification("Авто-режим работает в фоне"));
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

    /**
     * Главный тик фонового авто-режима.
     *
     * Что делает на каждом проходе:
     * - проверяет доступность `MainActivity`; при длительном отсутствии корректно останавливает сервис;
     * - синхронизирует флаги авто-режимов и обновляет foreground-уведомление;
     * - поддерживает room/chat polling в фоне;
     * - исполняет боевой pipeline: завершение боя, капча, синхронизация fight-frame, авто-удар;
     * - применяет anti-loop/anti-spam guard по времени и ключам состояния.
     * - фиксирует последнее действие в уведомлении через `markClientAction(...)`.
     *
     * Зависимости:
     * - `AutoFunctionsManager` (флаги авто-режимов и интервалы polling);
     * - `MainActivity` (`getMainWebView()`, `requestAutoTurnBackgroundAware()`, lifecycle-флаги);
     * - `AppVars` (FightLink/CodeAddress/LastFightPulseAtMs/IsFightCaptchaDialogVisible и др.);
     * - локальные guard-таймеры сервиса (`last*AtMs`).
     */
    private void runBackgroundTick() {
        MainActivity activity = (AppVars.mainActivity != null) ? AppVars.mainActivity.get() : null;
        long now = System.currentTimeMillis();
        if (activity == null) {
            if (lastMainActivitySeenAtMs == 0L) {
                lastMainActivitySeenAtMs = now;
            }
            long noActivityForMs = now - lastMainActivitySeenAtMs;
            Log.d(TAG, BG_TRACE_PREFIX + " tick: mainActivity=null, noActivityForMs=" + noActivityForMs);
            markClientAction("UI недоступен: " + (noActivityForMs / 1000) + "с");
            refreshForegroundNotification(false, false, false, false);
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
                boolean autoCompassEnabled = autoFunctionsManager.isAutoCompassEnabled();
                boolean autoBossEnabled = autoFunctionsManager.isAutoBossEnabled();
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
                boolean uiForegroundInteractive = activity.isUiForegroundInteractive();
                boolean uiForegroundLikely = activity.isUiForegroundLikely();
                Log.d(TAG, BG_TRACE_PREFIX + " uiTick: locationTracking=" + locationTrackingEnabled
                        + ", autoCompass=" + autoCompassEnabled
                        + ", autoBoss=" + autoBossEnabled
                        + ", autoFight=" + autoFightEnabled
                        + ", captchaDialogVisible=" + captchaDialogVisible
                        + ", walkersPollIntervalSec=" + walkersPollIntervalSec
                        + ", fightLikelyActive=" + fightLikelyActive
                        + ", uiForegroundInteractive=" + uiForegroundInteractive
                        + ", uiForegroundLikely=" + uiForegroundLikely);
                refreshForegroundNotification(autoFightEnabled, locationTrackingEnabled, captchaDialogVisible, false);

                ensureChatRefreshAlive(activity, tickNow);
                if (autoCompassEnabled) {
                    autoFunctionsManager.tickAutoCompass();
                }
                if (autoBossEnabled) {
                    autoFunctionsManager.tickAutoBoss();
                }
                AppTimerManager.getInstance(activity).processDueTimers();

                // Приоритетная отправка ссылки "Завершить бой" (act=7), если она уже готова.
                // Без этого цикл может зависать: autoTurn крутится по одному и тому же fight-frame,
                // LezFight собирает AppVars.FightLink, но сам URL завершения никогда не открывается.
                //
                // Зависимости:
                // - AppVars.FightLink: формируется в LezFight.ParseNonFight()/BuildFightLink(...);
                // - MainActivity.getMainWebView().loadUrl(...): фактическая отправка act=7;
                // - anti-loop guard: lastAutoFightFinishDispatchAtMs + lastAutoFightFinishLink.
                String pendingFightFinishLink = normalizeNeverlandsUrl(AppVars.FightLink);
                boolean staleReadyFinishLink = isReadyFightFinishLink(pendingFightFinishLink)
                        && !isFightFinishDispatchContextValid(tickNow);
                if (staleReadyFinishLink) {
                    Log.d(TAG, BG_TRACE_PREFIX + " uiTick: drop stale fight finish link (no active fight context): "
                            + pendingFightFinishLink);
                    AppVars.FightLink = "";
                    pendingFightFinishLink = "";
                }
                boolean canDispatchFightFinish = autoFightEnabled
                        && !captchaDialogVisible
                        && isReadyFightFinishLink(pendingFightFinishLink)
                        && isFightFinishDispatchContextValid(tickNow);
                if (canDispatchFightFinish) {
                    long sinceLastFinishDispatch = tickNow - lastAutoFightFinishDispatchAtMs;
                    boolean sameAsLastFinish = pendingFightFinishLink.equals(lastAutoFightFinishLink);
                    if (!sameAsLastFinish || sinceLastFinishDispatch >= AUTO_FIGHT_FINISH_MIN_INTERVAL_MS) {
                        if (activity.getMainWebView() != null) {
                            Log.d(TAG, BG_TRACE_PREFIX + " uiTick: dispatch fight finish link: " + pendingFightFinishLink);
                            activity.getMainWebView().loadUrl(pendingFightFinishLink);
                            markClientAction("Завершение боя: act=7");
                            lastAutoFightFinishDispatchAtMs = tickNow;
                            lastAutoFightFinishLink = pendingFightFinishLink;
                        } else {
                            Log.w(TAG, BG_TRACE_PREFIX + " uiTick: skip fight finish dispatch, mainWebView=null");
                            markClientAction("Пропуск act=7: webView=null");
                        }
                        // Чистим ссылку после попытки отправки, чтобы не дублировать в следующем тике.
                        AppVars.FightLink = "";
                        refreshForegroundNotification(autoFightEnabled, locationTrackingEnabled, captchaDialogVisible, true);
                        return;
                    } else {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: fight finish dispatch throttled, remainingMs="
                                + (AUTO_FIGHT_FINISH_MIN_INTERVAL_MS - sinceLastFinishDispatch));
                    }
                }

                // Критичный guard для боя с капчей:
                // когда сервер вернул finish-link с `code=????`, авто-ходы нужно остановить,
                // показать popup капчи и дождаться ручного ввода.
                // Иначе каждую секунду запускается autoTurn, страница капчи перезагружается,
                // challenge меняется, и пользователь физически не успевает ввести код.
                //
                // Зависимости:
                // - AppVars.FightLink / AppVars.CodeAddress: источник URL завершения и картинки капчи;
                // - AppVars.ACTION_SHOW_CAPTCHA + LocalBroadcastManager: открытие popup в MainActivity;
                // - AppVars.IsFightCaptchaDialogVisible: единый стоп-флаг для service/FightViewModel/MainPhp.
                if (maybeShowFightCaptchaDialog(activity, autoFightEnabled, captchaDialogVisible, pendingFightFinishLink, tickNow)) {
                    refreshForegroundNotification(autoFightEnabled, locationTrackingEnabled, true, true);
                    return;
                }
                if (autoFightEnabled && !captchaDialogVisible && !uiForegroundLikely) {
                    maybeForceFightFrameSync(activity, tickNow, pendingFightFinishLink);
                }

                // Когда после боя висят отложенные post-fight задачи (разделка/проверка инвентаря/fast-flow),
                // приоритетно двигаем именно pipeline main.php вместо autoTurn idle-probe.
                // Иначе цикл server-probe "нет маркеров боя" может бесконечно оттеснять обработку ресурсов.
                if (!uiForegroundLikely && !captchaDialogVisible && hasPendingBackgroundPipelineTasks()) {
                    long sinceLastPipelineTick = tickNow - lastPostFightPipelineTickAtMs;
                    if (sinceLastPipelineTick >= POST_FIGHT_PIPELINE_MIN_INTERVAL_MS) {
                        if (activity.getMainWebView() != null) {
                            String pipelineUrl = "http://neverlands.ru/main.php?r=" + tickNow + "&ab_bg_pipeline=1";
                            activity.getMainWebView().loadUrl(pipelineUrl);
                            lastPostFightPipelineTickAtMs = tickNow;
                            String pendingReason = buildPendingBackgroundPipelineReason();
                            markClientAction("Фоновый pipeline: " + pendingReason);
                            Log.d(TAG, BG_TRACE_PREFIX + " uiTick: run pending pipeline, reason=" + pendingReason
                                    + ", url=" + pipelineUrl);
                        } else {
                            markClientAction("Пропуск pipeline: webView=null");
                            Log.w(TAG, BG_TRACE_PREFIX + " uiTick: skip pending pipeline, mainWebView=null");
                        }
                    } else {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: pending pipeline throttled, remainingMs="
                                + (POST_FIGHT_PIPELINE_MIN_INTERVAL_MS - sinceLastPipelineTick)
                                + ", reason=" + buildPendingBackgroundPipelineReason());
                    }
                    refreshForegroundNotification(autoFightEnabled, locationTrackingEnabled, captchaDialogVisible, false);
                    return;
                }

                if (locationTrackingEnabled) {
                    if (tickNow - lastRoomUsersTickAtMs >= roomTickIntervalMs) {
                        activity.requestRoomUsersRefreshSoon();
                        markClientAction("Слежение: обновление локации");
                        lastRoomUsersTickAtMs = tickNow;
                    } else {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: room refresh throttled, remainingMs="
                                + (roomTickIntervalMs - (tickNow - lastRoomUsersTickAtMs)));
                    }
                }

                // Подробное описание guard-блока авто-хода:
                // Назначение:
                // - в активном foreground UI не дергать лишние probe/autoTurn, когда действительно нет признаков боя.
                //
                // Ключевая деталь текущего фикса:
                // - условие skip применяется только при !fightLikelyActive.
                // - если бой уже вероятно активен (fightLikelyActive=true), skip не срабатывает,
                //   и сервис продолжает requestAutoTurnBackgroundAware(), чтобы не было "залипания" после логина.
                //
                // Зависимости:
                // - hasFightMarkers(AppVars.ContentMainPhp): маркеры боя в текущем html-кэше;
                // - pendingFightFinishLink: наличие этапа завершения/капчи;
                // - fightLikelyActive: агрегированная эвристика активности боя.
                if (autoFightEnabled && !captchaDialogVisible) {
                    boolean mapAutomationActive = AppVars.AutoMoving;
                    if (mapAutomationActive
                            && !fightLikelyActive
                            && !hasFightMarkers(AppVars.ContentMainPhp)
                            && pendingFightFinishLink.isEmpty()) {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: skip autoTurn/probe while map automation active");
                        markClientAction("Пауза авто-хода: активен Авто-Клад");
                        refreshForegroundNotification(autoFightEnabled, locationTrackingEnabled, captchaDialogVisible, false);
                        return;
                    }
                    long sinceLastAutoTurnMs = tickNow - lastAutoTurnTickAtMs;
                    if ((uiForegroundInteractive || uiForegroundLikely)
                            && !fightLikelyActive
                            && !hasFightMarkers(AppVars.ContentMainPhp)
                            && pendingFightFinishLink.isEmpty()
                            && sinceLastAutoTurnMs < AUTO_TURN_IDLE_PROBE_INTERVAL_MS) {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: skip autoTurn/probe in foreground-likely UI (no fight markers)");
                        markClientAction("Пауза авто-хода: активный UI");
                        refreshForegroundNotification(autoFightEnabled, locationTrackingEnabled, captchaDialogVisible, false);
                        return;
                    }
                    // Background-safe polling: do auto-turn/probe even when fightLikelyActive=false.
                    long minIntervalMs = fightLikelyActive
                            ? AUTO_TURN_MIN_INTERVAL_MS
                            : AUTO_TURN_IDLE_PROBE_INTERVAL_MS;
                    if (sinceLastAutoTurnMs >= minIntervalMs) {
                        if (!fightLikelyActive) {
                            Log.d(TAG, BG_TRACE_PREFIX + " uiTick: autoTurn idle probe");
                        }
                        activity.requestAutoTurnBackgroundAware();
                        markClientAction(fightLikelyActive ? "Авто-ход: запрос шага" : "Авто-ход: idle-probe");
                        lastAutoTurnTickAtMs = tickNow;
                    } else {
                        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: autoTurn throttled, remainingMs="
                                + (minIntervalMs - sinceLastAutoTurnMs)
                                + ", fightLikelyActive=" + fightLikelyActive);
                    }
                } else if (autoFightEnabled && captchaDialogVisible) {
                    Log.d(TAG, BG_TRACE_PREFIX + " uiTick: skip autoTurn, captcha dialog visible");
                    markClientAction("Ожидание ввода капчи");
                }
                refreshForegroundNotification(autoFightEnabled, locationTrackingEnabled, captchaDialogVisible, false);
            } catch (Exception e) {
                Log.e(TAG, BG_TRACE_PREFIX + " uiTick: failed", e);
            }
        });
    }

    /**
     * Watchdog chat polling:
     * если периодический `ch.php?show=1` в Activity "замолчал" в фоне,
     * сервис форсирует разовый refresh, чтобы не пропускать входящие события боя.
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

        // Начальный bootstrap: если Activity еще не делала ни одного poll-запроса.
        if (lastChatRefreshAtMs <= 0L) {
            if (tickNow - lastForcedChatRefreshAtMs >= forceCooldownMs) {
                Log.d(TAG, BG_TRACE_PREFIX + " uiTick: chat refresh bootstrap");
                activity.requestChatRefreshNow();
                markClientAction("Чат: bootstrap refresh");
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
        markClientAction("Чат: watchdog refresh");
        lastForcedChatRefreshAtMs = tickNow;
    }

    /**
     * Проверяет, есть ли в рантайме отложенные задачи post-fight pipeline,
     * которые требуют реальной навигации по main.php (инвентарь/ресурсы/fast-action),
     * а не только server-probe авто-боя.
     */
    private boolean hasPendingBackgroundPipelineTasks() {
        return AppVars.AutoSkinCheckRes
                || AppVars.AutoSkinCheckUm
                || AppVars.AutoSkinCheckKnife
                || AppVars.FastNeed
                || AppVars.AutoFishCheckUm
                || AppVars.AutoFishCheckUd
                || AppVars.AutoFishWearUd;
    }

    /**
     * Короткая строка причин для логов/уведомления: какие именно pending-флаги держат pipeline.
     */
    private String buildPendingBackgroundPipelineReason() {
        StringBuilder sb = new StringBuilder();
        if (AppVars.AutoSkinCheckRes) sb.append("SkinRes,");
        if (AppVars.AutoSkinCheckUm) sb.append("SkinUm,");
        if (AppVars.AutoSkinCheckKnife) sb.append("SkinKnife,");
        if (AppVars.FastNeed) sb.append("FastNeed,");
        if (AppVars.AutoFishCheckUm) sb.append("FishUm,");
        if (AppVars.AutoFishCheckUd) sb.append("FishUd,");
        if (AppVars.AutoFishWearUd) sb.append("FishWear,");
        if (sb.length() == 0) {
            return "none";
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private boolean isFightSessionLikelyActive(MainActivity activity) {
        // 1) Самый надежный сигнал: в кеше main.php есть боевые маркеры.
        String mainHtml = AppVars.ContentMainPhp;
        if (hasFightMarkers(mainHtml)) {
            return true;
        }

        // 2) Прямой сигнал окончания/боевого action-link.
        String fightLink = AppVars.FightLink;
        if (fightLink != null && fightLink.contains("get_id=61&act=")) {
            return true;
        }

        // 3) "Боевой пульс" за последние N секунд.
        // Нужен для переходных кадров, где URL/HTML кратко теряют fight-маркеры.
        long pulseAtMs = AppVars.LastFightPulseAtMs;
        boolean recentFightPulse = pulseAtMs > 0L
                && (System.currentTimeMillis() - pulseAtMs) <= FIGHT_PULSE_GRACE_MS;
        // Ранее здесь была дополнительная жесткая проверка URL (go=inf),
        // из-за которой в фоне fightLikelyActive часто оставался false даже при живом боевом pulse.
        // Для lockscreen/background достаточно самого свежего pulse - он обновляется только боевым HTML.
        if (recentFightPulse) {
            return true;
        }

        return false;
    }

    private boolean hasFightMarkers(String html) {
        return html != null && (html.contains("var fight_ty") || html.contains("magic_slots();"));
    }

    private boolean isFightFinishDispatchContextValid(long tickNow) {
        if (hasFightMarkers(AppVars.ContentMainPhp)) {
            return true;
        }
        long pulseAtMs = AppVars.LastFightPulseAtMs;
        return pulseAtMs > 0L
                && (tickNow - pulseAtMs) >= 0L
                && (tickNow - pulseAtMs) <= FIGHT_FINISH_PULSE_GRACE_MS;
    }

    /**
     * Форсирует мягкую синхронизацию верхнего фрейма в фоне после анонса "Нападение",
     * когда бой еще не отразился в текущем HTML.
     */
    /**
     * Форсирует мягкую синхронизацию верхнего фрейма после анонса нападения.
     *
     * Используется только как восстановление контекста:
     * - когда бой на сервере уже стартовал, но текущий HTML ещё не содержит fight-маркеры;
     * - когда не готов `finish-link` и не требуется ввод капчи.
     *
     * Защиты от лишних перезагрузок:
     * - временное окно по `LastFightAnnounceAtMs`;
     * - троттлинг `FORCE_FIGHT_SYNC_MIN_INTERVAL_MS`;
     * - выход без действия при наличии активного боевого HTML или готового `act=7` URL.
     *
     * Зависимости:
     * - `MainActivity.getMainWebView().loadUrl(...)`;
     * - `AppVars.LastFightAnnounceAtMs`, `AppVars.ContentMainPhp`, `AppVars.FightLink`;
     * - `isReadyFightFinishLink(...)`, `isFightCaptchaFinishLink(...)`.
     */
    private void maybeForceFightFrameSync(MainActivity activity, long tickNow, String pendingFightFinishLink) {
        if (activity == null || activity.getMainWebView() == null) {
            return;
        }
        if (isReadyFightFinishLink(pendingFightFinishLink) || isFightCaptchaFinishLink(pendingFightFinishLink)) {
            return;
        }
        if (hasFightMarkers(AppVars.ContentMainPhp)) {
            return;
        }
        long announceAtMs = AppVars.LastFightAnnounceAtMs;
        if (announceAtMs <= 0L || (tickNow - announceAtMs) > FIGHT_ANNOUNCE_GRACE_MS) {
            return;
        }
        if ((tickNow - lastForceFightSyncAtMs) < FORCE_FIGHT_SYNC_MIN_INTERVAL_MS) {
            return;
        }

        String syncUrl = "http://neverlands.ru/main.php?r=" + tickNow + "&ab_force_fight_sync=1";
        activity.getMainWebView().loadUrl(syncUrl);
        lastForceFightSyncAtMs = tickNow;
        markClientAction("Синхронизация fight-frame");
        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: force fight-frame sync after attack announce, url=" + syncUrl);
    }

    /**
     * Готовая ссылка завершения боя (act=7) без placeholder капчи.
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
     * Проверяет, что finish-link относится к завершению боя с обязательной капчей.
     *
     * Критерии:
     * - get_id=61 и act=7 (боевое завершение);
     * - placeholder code=???? (код ещё не введён вручную).
     *
     * Зависимости:
     * - AppVars.FightLink, сформированный боевым парсером;
     * - maybeShowFightCaptchaDialog(...), где это условие запускает popup-flow.
     */
    private boolean isFightCaptchaFinishLink(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.contains("get_id=61")
                && url.contains("act=7")
                && url.contains("code=????");
    }

    /**
     * Центральная оркестрация показа боевой капчи из фонового сервиса.
     *
     * Правила:
     * - не показывать popup, если уже открыт dialog;
     * - не показывать popup повторно для того же finish-key после успешного submit,
     *   пока сервер не пришлёт новый challenge (по тайм-метке перехваченной картинки);
     * - отправлять ACTION_SHOW_CAPTCHA только после дедупликации по key+time.
     *
     * Зависимости:
     * - AppVars.FightLink / CodeAddress / LastSubmittedFightCaptcha* / LastFightCaptchaImageAtMs;
     * - MainActivity.broadcastReceiver -> MainActivity.showCaptchaDialog(...);
     * - normalizeNeverlandsUrl(...) и buildFightCaptchaFinishKey(...) для унификации сравнения.
     *
     * @return true, если тик должен завершиться в режиме ожидания ручного ввода капчи.
     */
    private boolean maybeShowFightCaptchaDialog(
            MainActivity activity,
            boolean autoFightEnabled,
            boolean captchaDialogVisible,
            String pendingFightFinishLink,
            long tickNow
    ) {
        if (activity == null || !autoFightEnabled || captchaDialogVisible) {
            return false;
        }
        if (!isFightCaptchaFinishLink(pendingFightFinishLink)) {
            return false;
        }

        String pendingFightKey = buildFightCaptchaFinishKey(pendingFightFinishLink);
        if (!pendingFightKey.isEmpty()) {
            String lastSubmittedKey = AppVars.LastSubmittedFightCaptchaFinishKey == null
                    ? ""
                    : AppVars.LastSubmittedFightCaptchaFinishKey;
            long submittedAtMs = AppVars.LastSubmittedFightCaptchaAtMs;
            long submittedAgeMs = submittedAtMs > 0L ? (tickNow - submittedAtMs) : Long.MAX_VALUE;
            boolean sameAsSubmitted = pendingFightKey.equals(lastSubmittedKey);
            long lastCapturedCaptchaAtMs = AppVars.LastFightCaptchaImageAtMs;

            // После успешного submit сервер может ещё какое-то время держать stale fight-link/code=???? в рантайме.
            // Если ключ тот же, а новой капчи (по времени перехваченной картинки) после submit не было,
            // повторно popup не показываем вообще, пока сервер не пришлёт новый challenge.
            if (sameAsSubmitted && submittedAtMs > 0L
                    && lastCapturedCaptchaAtMs > 0L
                    && lastCapturedCaptchaAtMs <= submittedAtMs) {
                Log.d(TAG, BG_TRACE_PREFIX + " uiTick: skip stale fight captcha after submit,"
                        + " ageMs=" + submittedAgeMs
                        + ", lastCaptchaAtMs=" + lastCapturedCaptchaAtMs
                        + ", submittedAtMs=" + submittedAtMs);
                markClientAction("Ожидание нового challenge капчи");
                return true;
            }

            if (sameAsSubmitted && submittedAgeMs >= 0L && submittedAgeMs < FIGHT_CAPTCHA_SUBMIT_GUARD_TTL_MS) {
                Log.d(TAG, BG_TRACE_PREFIX + " uiTick: skip duplicate fight captcha after submit, ageMs=" + submittedAgeMs);
                markClientAction("Капча: anti-duplicate guard");
                return true;
            }
            if (!sameAsSubmitted && submittedAgeMs >= FIGHT_CAPTCHA_SUBMIT_GUARD_TTL_MS) {
                AppVars.LastSubmittedFightCaptchaFinishKey = "";
                AppVars.LastSubmittedFightCaptchaAtMs = 0L;
            }
        }

        String captchaUrl = normalizeNeverlandsUrl(AppVars.CodeAddress);
        if (captchaUrl.isEmpty()) {
            Log.w(TAG, BG_TRACE_PREFIX + " uiTick: fight captcha required but captcha url is empty");
            markClientAction("Капча: нет URL challenge");
            return true;
        }

        String key = captchaUrl + "|" + pendingFightFinishLink;
        if (key.equals(lastFightCaptchaBroadcastKey)
                && (tickNow - lastFightCaptchaBroadcastAtMs) < FIGHT_CAPTCHA_BROADCAST_DEDUP_MS) {
            AppVars.IsFightCaptchaDialogVisible = true;
            markClientAction("Капча: ожидание ввода");
            return true;
        }

        lastFightCaptchaBroadcastKey = key;
        lastFightCaptchaBroadcastAtMs = tickNow;
        AppVars.IsFightCaptchaDialogVisible = true;
        AppVars.ResumeAutoboiAfterCaptcha = true;

        Intent intent = new Intent(AppVars.ACTION_SHOW_CAPTCHA);
        intent.putExtra("captchaUrl", captchaUrl);
        intent.putExtra("finishUrl", pendingFightFinishLink);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        markClientAction("Капча: показ popup");
        Log.d(TAG, BG_TRACE_PREFIX + " uiTick: fight captcha popup requested, finishUrl=" + pendingFightFinishLink);
        return true;
    }

    /**
     * Нормализует игровую ссылку в абсолютный URL neverlands.
     *
     * Что делает:
     * - trim + проверка пустых значений;
     * - относительные пути переводит в http://neverlands.ru/...;
     * - абсолютные http/https оставляет без изменений.
     *
     * Зависимости:
     * - buildFightCaptchaFinishKey(...);
     * - maybeShowFightCaptchaDialog(...), где нормализуется CodeAddress.
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

    /**
     * Строит нормализованный ключ finish-link для сравнения challenge между тиками.
     *
     * Принцип:
     * - URL нормализуется до абсолютного вида;
     * - значение code=... унифицируется до code=????;
     * - одинаковый challenge всегда даёт одинаковый ключ.
     *
     * Зависимости:
     * - AppVars.LastSubmittedFightCaptchaFinishKey;
     * - maybeShowFightCaptchaDialog(...), где ключ используется в anti-loop логике.
     */
    private String buildFightCaptchaFinishKey(String finishUrl) {
        String normalized = normalizeNeverlandsUrl(finishUrl);
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.replaceFirst("([?&])code=[^&]*", "$1code=????");
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

    /**
     * Фиксирует последнее действие фонового клиента для показа в уведомлении и отладки.
     *
     * Зависимости:
     * - runBackgroundTick()/maybeShowFightCaptchaDialog()/maybeForceFightFrameSync() как источники событий;
     * - refreshForegroundNotification(...) как потребитель строки.
     */
    private void markClientAction(String action) {
        if (action == null) {
            return;
        }
        String sanitized = action.replace('\n', ' ').replace('\r', ' ').trim();
        if (sanitized.isEmpty()) {
            return;
        }
        if (sanitized.length() > 96) {
            sanitized = sanitized.substring(0, 93) + "...";
        }
        lastClientAction = sanitized;
        lastClientActionAtMs = System.currentTimeMillis();
    }

    /**
     * Обновляет foreground-уведомление состоянием авто-режима и последним действием клиента.
     *
     * Зависимости:
     * - markClientAction(...) -> lastClientAction/lastClientActionAtMs;
     * - RuntimeNetTrace.snapshotForUi() для расширенного debug-текста;
     * - NotificationManager.notify(...) для live-обновления уже поднятого foreground уведомления.
     */
    private void refreshForegroundNotification(
            boolean autoFightEnabled,
            boolean locationTrackingEnabled,
            boolean captchaDialogVisible,
            boolean force
    ) {
        long now = System.currentTimeMillis();
        String actionTs = lastClientActionAtMs > 0L
                ? new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(lastClientActionAtMs))
                : "--:--:--";
        String contentText = "Бой:" + (autoFightEnabled ? "ON" : "OFF")
                + " Локация:" + (locationTrackingEnabled ? "ON" : "OFF")
                + " Капча:" + (captchaDialogVisible ? "да" : "нет")
                + " | " + actionTs + " " + lastClientAction;

        if (!force) {
            boolean tooFrequent = (now - lastNotificationUpdateAtMs) < NOTIFICATION_MIN_UPDATE_MS;
            if (tooFrequent && contentText.equals(lastNotificationContentText)) {
                return;
            }
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText));
        lastNotificationContentText = contentText;
        lastNotificationUpdateAtMs = now;
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
                "Фоновый авто-режим",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Поддерживает авто-режимы при заблокированном экране");
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
                .setContentTitle("ABClient: фоновый авто-режим")
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        buildNotificationBigText(contentText)))
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();
    }

    /**
     * Формирует расширенный текст уведомления:
     * базовая строка + сетевой runtime + полная версия последнего системного сообщения чата.
     */
    private String buildNotificationBigText(String contentText) {
        StringBuilder sb = new StringBuilder(contentText);
        sb.append("\nNET: ").append(RuntimeNetTrace.snapshotForUi());

        String system = Chat.getLastSystemChatMessage();
        if (system != null && !system.trim().isEmpty()) {
            String full = system.trim();
            if (full.length() > 320) {
                full = full.substring(0, 317) + "...";
            }
            sb.append("\nSYS_FULL: ").append(full);
        }
        return sb.toString();
    }
}
