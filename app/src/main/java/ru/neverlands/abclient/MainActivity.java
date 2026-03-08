package ru.neverlands.abclient;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.navigation.NavigationView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import ru.neverlands.abclient.bridge.WebAppInterface;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.databinding.ActivityMainBinding;
import ru.neverlands.abclient.manager.TabManager;
import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.AppLogger;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.RuntimeNetTrace;
import ru.neverlands.abclient.utils.Russian;
import ru.neverlands.abclient.webview.WebViewRequestInterceptor;
import ru.neverlands.abclient.service.AutoModeForegroundService;

import androidx.lifecycle.ViewModelProvider;
import ru.neverlands.abclient.ui.viewmodel.FightViewModel;
import ru.neverlands.abclient.ui.QuickButtonsPanel;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
    private static final String BUILD_MARKER = "2026-02-27_01-34";
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1002;
    private static final int CHAT_REFRESH_DEFAULT_SECONDS = 12;
    private static final int CHAT_REFRESH_INITIAL_DELAY_MS = 1000;
    private static final int AUTO_SUBMIT_RETRY_DELAY_MS = 180;
    private static final int AUTO_SUBMIT_MAX_RETRY_COUNT = 3;
    private static final long CAPTCHA_IMAGE_STABILIZE_DELAY_MS = 180L;
    private static final long CAPTCHA_NETWORK_FALLBACK_DELAY_MS = 900L;
    private static final int CAPTCHA_NOTIFICATION_ID = 6107;
    private static final String CAPTCHA_NOTIFICATION_CHANNEL_ID = "captcha_alerts";
    private static final long POST_RELOAD_GUARD_WINDOW_MS = 5000L;
    private static final int POST_RELOAD_GUARD_MAX_COUNT = 4;
    private static final long POST_RELOAD_GUARD_BLOCK_MS = 12000L;
    private static final long MAINFRAME_TIMEOUT_RETRY_DELAY_MS = 1500L;
    private static final long MAINFRAME_TIMEOUT_RETRY_DEDUP_MS = 12000L;
    public ActivityMainBinding binding;
    private Timer timer;
    private boolean isExiting = false;
    private boolean isRoomManagerStarted = false;
    private FightViewModel fightViewModel;
    private TabManager tabManager;
    private QuickButtonsPanel quickButtonsPanel;
    private WebView chatRefrWebView;
    private final java.util.List<WebView> chatPopupWebViews = new java.util.ArrayList<>();
    private final Handler chatRefreshHandler = createMainHandler();
    private Runnable chatRefreshRunnable;
    private final Handler roomUsersPollingHandler = createMainHandler();
    private Runnable roomUsersPollingRunnable;
    private int chatRefreshSeconds = CHAT_REFRESH_DEFAULT_SECONDS;
    private int chatFyo = 0;
    // Временная метка последнего запроса chat polling (`ch.php?show=1`).
    // Используется foreground-сервисом как watchdog, чтобы в фоне не было "тихих" пауз.
    private volatile long lastChatRefreshAtMs = 0L;
    // Временная метка последнего ручного обновления списка комнаты (`ch.php?lo=1`).
    // Используется как анти-спам guard, когда авто-нападение включено.
    private long lastRoomUsersRefreshAtMs = 0L;
    private static final long ROOM_USERS_REFRESH_MIN_INTERVAL_MS = 1000L;
    private boolean chatLatrus = false;
    private AlertDialog activeFightCaptchaDialog;
    private final Handler fightCaptchaHandler = createMainHandler();
    private Runnable fightCaptchaRefreshRunnable;
    private long activeFightCaptchaImageAtMs = 0L;
    private int activeFightCaptchaImageHash = 0;
    private boolean activeFightCaptchaImageLocked = false;
    private long activeFightCaptchaLoadSeq = 0L;
    private String activeFightCaptchaUrl = "";
    private String activeFightFinishUrl = "";
    private boolean replacingFightCaptchaDialog = false;
    private boolean appBroadcastReceiverRegistered = false;
    private boolean screenStateReceiverRegistered = false;
    private long postReloadGuardWindowStartMs = 0L;
    private int postReloadGuardCount = 0;
    private long postReloadGuardBlockUntilMs = 0L;
    private String postReloadGuardKey = "";
    private long lastMainFrameTimeoutRetryAtMs = 0L;
    private String lastMainFrameTimeoutRetryUrl = "";
    private long lastAutoBattleSubmitAtMs = 0L;
    private final Handler autoBattleDelayHandler = createMainHandler();
    private Runnable pendingAutoBattleSubmitRunnable;
    private String pendingAutoBattleSubmitPayload = "";
    private final ActivityResultLauncher<Intent> contactsActivityLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Intent data = result.getData();
                        if (result.getResultCode() == RESULT_OK && data != null) {
                            String url = data.getStringExtra("open_pinfo_url");
                            String title = data.getStringExtra("open_pinfo_title");
                            if (url != null && tabManager != null) {
                                tabManager.openTab(url, title != null ? title : "PINFO");
                            }
                        }
                    });

    /**
     * Создает "асинхронный" main handler (API 28+), чтобы periodic callback-и
     * меньше зависели от sync barrier UI-pipeline в фоне/lockscreen.
     */
    private static Handler createMainHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Handler.createAsync(Looper.getMainLooper());
        }
        return new Handler(Looper.getMainLooper());
    }

    // Доступ к ViewModel боя для других компонентов/фрагментов.
    public FightViewModel getFightViewModel() {
        return fightViewModel;
    }

    // Основной WebView (main.php + фреймы игры).
    public android.webkit.WebView getMainWebView() {
        return binding.appBarMain.contentMain.webView;
    }

    // Загрузка URL в скрытый chatRefrWebView (аналог frames['ch_refr'] в браузере).
    public void loadChatRefrUrl(String url) {
        if (chatRefrWebView == null || url == null || url.isEmpty()) {
            return;
        }
        chatRefrWebView.loadUrl(url);
    }

    // POST в chatRefrWebView — используется для отправки сообщений чата.
    public void postChatRefrUrl(String url, String data) {
        if (chatRefrWebView == null || url == null || url.isEmpty()) {
            return;
        }
        byte[] body = data == null ? new byte[0] : ru.neverlands.abclient.utils.Russian.getBytes(data);
        chatRefrWebView.postUrl(url, body);
    }

    // Быстрый повторный опрос чата (используется после активности/отправки).
    public void requestChatRefreshSoon() {
        if (chatRefreshRunnable == null) {
            startChatRefresh();
            return;
        }
        chatRefreshHandler.removeCallbacks(chatRefreshRunnable);
        chatRefreshHandler.postDelayed(chatRefreshRunnable, 200);
    }

    /**
     * Принудительное обновление room-list (`ch.php?lo=1`) для авто-нападения.
     *
     * Зависимости:
     * - `chatUsersWebview`: источник данных для `RoomManager.process(...)`,
     * - `RoomManager`: именно здесь стартует цепочка авто-нападения по hostile контактам.
     *
     * Зачем:
     * - после отдельных переходов/renderer-crash серверный JS может временно не вызывать `lo=1`,
     * - без новых room-тиков авто-нападение выглядит как "остановилось".
     */
    public void requestRoomUsersRefreshSoon() {
        if (binding == null || binding.appBarMain == null || binding.appBarMain.contentMain == null) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestRoomUsersRefreshSoon: skip binding=null");
            return;
        }
        WebView chatUsersWebView = binding.appBarMain.contentMain.chatUsersWebview;
        if (chatUsersWebView == null) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestRoomUsersRefreshSoon: skip chatUsersWebView=null");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRoomUsersRefreshAtMs < ROOM_USERS_REFRESH_MIN_INTERVAL_MS) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestRoomUsersRefreshSoon: throttled, deltaMs="
                    + (now - lastRoomUsersRefreshAtMs));
            return;
        }

        String roomUrl = "http://neverlands.ru/ch.php?lo=1&" + now;
        Log.d(TAG, "[AA_TRACE] requestRoomUsersRefreshSoon: " + roomUrl);
        AppVars.url_ch_list = roomUrl;
        chatUsersWebView.loadUrl(roomUrl);
        lastRoomUsersRefreshAtMs = now;
    }

    /**
     * Возвращает true, если авто-контуры должны продолжать работу в фоне.
     *
     * Используем общий критерий из foreground-service, чтобы логика включения/выключения
     * оставалась консистентной между Activity и сервисом.
     */
    private boolean shouldKeepBackgroundLoops() {
        return AutoModeForegroundService.shouldRunInBackground(this);
    }

    /**
     * Логирует сводное состояние фона/питания/авто-флагов для lockscreen-диагностики.
     */
    private void logBackgroundState(String stage) {
        boolean autoFightEnabled = false;
        boolean autoAttackEnabled = false;
        boolean locationTrackingEnabled = false;
        try {
            AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(this);
            autoFightEnabled = autoFunctionsManager.isAutoFightEnabled();
            autoAttackEnabled = autoFunctionsManager.isAutoAttackEnabled();
            locationTrackingEnabled = autoFunctionsManager.isLocationTrackingEnabled();
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " " + stage + ": failed to read auto flags", e);
        }

        boolean isInteractive = true;
        boolean isDeviceIdleMode = false;
        boolean batteryOptimized = false;
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
                    isInteractive = powerManager.isInteractive();
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    isDeviceIdleMode = powerManager.isDeviceIdleMode();
                    batteryOptimized = !powerManager.isIgnoringBatteryOptimizations(getPackageName());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " " + stage + ": failed to read power state", e);
        }

        Log.d(TAG, BG_TRACE_PREFIX + " " + stage
                + ": interactive=" + isInteractive
                + ", idleMode=" + isDeviceIdleMode
                + ", batteryOptimized=" + batteryOptimized
                + ", autoFight=" + autoFightEnabled
                + ", appVarsAutoboi=" + AppVars.Autoboi
                + ", autoAttack=" + autoAttackEnabled
                + ", locationTracking=" + locationTrackingEnabled
                + ", chatRefreshActive=" + (chatRefreshRunnable != null)
                + ", roomPollingActive=" + (roomUsersPollingRunnable != null));
    }

    private void registerAppBroadcastReceiverIfNeeded() {
        if (appBroadcastReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppVars.ACTION_ADD_CHAT_MESSAGE);
        filter.addAction(AppVars.ACTION_WEBVIEW_LOAD_URL);
        filter.addAction(AppVars.ACTION_WEBVIEW_EVAL_JS);
        filter.addAction(AppVars.ACTION_SHOW_CAPTCHA);
        filter.addAction(AppVars.ACTION_STOP_AUTOFISH);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);
        appBroadcastReceiverRegistered = true;
        Log.d(TAG, BG_TRACE_PREFIX + " registerAppBroadcastReceiverIfNeeded: registered");
    }

    private void unregisterAppBroadcastReceiverIfNeeded() {
        if (!appBroadcastReceiverRegistered) {
            return;
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        appBroadcastReceiverRegistered = false;
        Log.d(TAG, BG_TRACE_PREFIX + " unregisterAppBroadcastReceiverIfNeeded: unregistered");
    }

    private void registerScreenStateReceiverIfNeeded() {
        if (screenStateReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenStateReceiver, filter);
        screenStateReceiverRegistered = true;
        Log.d(TAG, BG_TRACE_PREFIX + " registerScreenStateReceiverIfNeeded: registered");
    }

    private void unregisterScreenStateReceiverIfNeeded() {
        if (!screenStateReceiverRegistered) {
            return;
        }
        unregisterReceiver(screenStateReceiver);
        screenStateReceiverRegistered = false;
        Log.d(TAG, BG_TRACE_PREFIX + " unregisterScreenStateReceiverIfNeeded: unregistered");
    }

    // Автовыбор: берем HTML текущего боя и отправляем в FightViewModel.
    public void onWalkersPollingConfigChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            restartRoomUsersPolling();
        } else {
            runOnUiThread(this::restartRoomUsersPolling);
        }
    }

    private void restartRoomUsersPolling() {
        Log.d(TAG, BG_TRACE_PREFIX + " restartRoomUsersPolling");
        stopRoomUsersPolling();
        startRoomUsersPolling();
    }

    private void startRoomUsersPolling() {
        Log.d(TAG, BG_TRACE_PREFIX + " startRoomUsersPolling: begin, hasRunnable="
                + (roomUsersPollingRunnable != null));
        if (roomUsersPollingRunnable != null) {
            roomUsersPollingHandler.removeCallbacks(roomUsersPollingRunnable);
        }
        roomUsersPollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) {
                    Log.d(TAG, BG_TRACE_PREFIX + " roomUsersPolling: stop due to finishing/destroyed");
                    return;
                }
                AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(MainActivity.this);
                if (!autoFunctionsManager.isLocationTrackingEnabled()) {
                    Log.d(TAG, BG_TRACE_PREFIX + " roomUsersPolling: skip tick, locationTracking=false");
                    return;
                }
                requestRoomUsersRefreshSoon();
                long delayMs = Math.max(
                        ROOM_USERS_REFRESH_MIN_INTERVAL_MS,
                        autoFunctionsManager.getWalkersPollIntervalSec() * 1000L
                );
                roomUsersPollingHandler.postDelayed(this, delayMs);
            }
        };

        AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(this);
        if (autoFunctionsManager.isLocationTrackingEnabled()) {
            Log.d(TAG, BG_TRACE_PREFIX + " startRoomUsersPolling: first post");
            roomUsersPollingHandler.post(roomUsersPollingRunnable);
        } else {
            Log.d(TAG, BG_TRACE_PREFIX + " startRoomUsersPolling: locationTracking disabled");
        }
    }

    private void stopRoomUsersPolling() {
        if (roomUsersPollingRunnable != null) {
            roomUsersPollingHandler.removeCallbacks(roomUsersPollingRunnable);
            roomUsersPollingRunnable = null;
            Log.d(TAG, BG_TRACE_PREFIX + " stopRoomUsersPolling: stopped");
        } else {
            Log.d(TAG, BG_TRACE_PREFIX + " stopRoomUsersPolling: already stopped");
        }
    }

    public void requestAutoSelect() {
        Log.d(TAG, BG_TRACE_PREFIX + " requestAutoSelect: start");
        binding.appBarMain.contentMain.webView.evaluateJavascript(
                "(function() { return document.documentElement.innerHTML; })();",
                html -> {
                    if (html != null && !html.equals("null")) {
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        String unquoted = gson.fromJson(html, String.class);
                        fightViewModel.autoSelect(unquoted);
                    }
                });
    }

    // Автоход: забираем HTML боя и формируем одно действие.
    public void requestAutoTurn() {
        if (AppVars.IsFightCaptchaDialogVisible) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: skip, captcha dialog visible");
            return;
        }
        Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: start");
        Log.d(TAG, "requestAutoTurn: grabbing current HTML for auto-turn");
        binding.appBarMain.contentMain.webView.evaluateJavascript(
                "(function() { return document.documentElement.innerHTML; })();",
                html -> {
                    if (html != null && !html.equals("null")) {
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        String unquoted = gson.fromJson(html, String.class);
                        Log.d(TAG, "requestAutoTurn: html length=" + (unquoted != null ? unquoted.length() : 0));
                        String autoTurnHtml = unquoted;
                        if (hasFightMarkers(unquoted)) {
                        } else {
                            String cachedFightHtml = AppVars.ContentMainPhp;
                            if (hasFightMarkers(cachedFightHtml)) {
                                autoTurnHtml = cachedFightHtml;
                                Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: fallback to cached fight html, len="
                                        + cachedFightHtml.length());
                            } else {
                                Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: no fight markers in current/cached html");
                            }
                        }
                        fightViewModel.autoTurnOnce(autoTurnHtml);
                    } else {
                        Log.d(TAG, "requestAutoTurn: html is null");
                        String cachedFightHtml = AppVars.ContentMainPhp;
                        if (hasFightMarkers(cachedFightHtml)) {
                            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: null html, fallback to cached fight html, len="
                                    + cachedFightHtml.length());
                            fightViewModel.autoTurnOnce(cachedFightHtml);
                        } else {
                            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: html is null and cached html has no fight markers");
                        }
                    }
                });
    }

    /**
     * Автовосстановление fight-frame, если авто-тик подряд получает небоевой HTML.
     *
     * Зависимости:
     * - `AppVars.Autoboi` / `Profile.LezDoAutoboi`: runtime-флаг активного авто-боя;
     * - `AppVars.VCode`: добавляется в URL восстановления, если доступен;
     * - `binding.appBarMain.contentMain.webView`: целевой WebView верхнего фрейма.
     */


    private boolean hasFightMarkers(String html) {
        return html != null && (html.contains("var fight_ty") || html.contains("magic_slots();"));
    }

    /**
     * Безопасная отправка auto-battle действия в WebView.
     *
     * Зачем:
     * - устраняет race после SCREEN_ON/RESUME, когда страница уже жива, но AutoSubmit ещё не определён;
     * - использует fallback через form submit, если JS-функция временно отсутствует.
     *
     * Зависимости:
     * - `evaluateJavascript(...)` основного `webView`;
     * - JS-контур `FightJs.AutoSubmit(...)` и резервный submit формы (`document.ff`/`document.forms[0]`).
     */
    private void submitAutoBattleActionToWebView(String result, int retriesLeft) {
        if (result == null || binding == null || binding.appBarMain == null
                || binding.appBarMain.contentMain == null
                || binding.appBarMain.contentMain.webView == null) {
            Log.w(TAG, "submitAutoBattleActionToWebView: skip, invalid state");
            return;
        }

        // Фикс "1 ход и белый фрейм": сохраняем vcode из payload авто-удара до POST-фазы,
        // чтобы последующий reload `go=inf` не уходил без vcode.
        adoptVCodeFromAutoSubmitPayload(result);

        com.google.gson.Gson gson = new com.google.gson.Gson();
        String jsonArg = gson.toJson(result);
        String script = "(function(payload){"
                + "try{"
                + "var tryPayloadSubmit=function(raw){"
                + "if(!raw){return false;}"
                + "var ss=(''+raw).split('|');"
                + "if(ss.length<9){return false;}"
                + "var mk=function(name,val){var i=document.createElement('input');i.type='hidden';i.name=name;i.value=(val==null?'':val);return i;};"
                + "var f=document.createElement('form');"
                + "f.method='POST';"
                + "f.action='main.php';"
                + "f.style.display='none';"
                + "f.appendChild(mk('post_id','7'));"
                + "f.appendChild(mk('vcode',ss[0]));"
                + "f.appendChild(mk('enemy',ss[1]));"
                + "f.appendChild(mk('group',ss[2]));"
                + "f.appendChild(mk('inf_bot',ss[3]));"
                + "f.appendChild(mk('lev_bot',ss[4]));"
                + "f.appendChild(mk('ftr',ss[5]));"
                + "f.appendChild(mk('inu',ss[6]));"
                + "f.appendChild(mk('inb',ss[7]));"
                + "f.appendChild(mk('ina',ss[8]));"
                + "(document.body||document.documentElement).appendChild(f);"
                + "f.submit();"
                + "return true;"
                + "};"
                + "if(tryPayloadSubmit(payload)){return 'ok_payload_submit';}"
                + "if(typeof window.AutoSubmit==='function'){window.AutoSubmit(payload);return 'ok_autosubmit';}"
                + "if(typeof AutoSubmit==='function'){AutoSubmit(payload);return 'ok_AutoSubmit';}"
                + "if(document&&document.ff&&typeof document.ff.submit==='function'){document.ff.submit();return 'ok_ff_submit';}"
                + "if(document&&document.forms&&document.forms.length>0&&typeof document.forms[0].submit==='function'){document.forms[0].submit();return 'ok_form_submit';}"
                + "return 'missing';"
                + "}catch(e){"
                + "console.log('ABCLIENT_AUTOBATTLE_SUBMIT_ERR:'+e);"
                + "return 'error';"
                + "}"
                + "})(" + jsonArg + ")";

        binding.appBarMain.contentMain.webView.evaluateJavascript(script, rawStatus -> {
            String status = null;
            try {
                status = gson.fromJson(rawStatus, String.class);
            } catch (Exception ignored) {
                status = rawStatus;
            }

            boolean missing = status != null && status.contains("missing");
            if (missing && retriesLeft > 0) {
                int nextRetriesLeft = retriesLeft - 1;
                Log.d(TAG, BG_TRACE_PREFIX + " submitAutoBattleAction: submit path missing, retry left=" + nextRetriesLeft);
                binding.appBarMain.contentMain.webView.postDelayed(
                        () -> submitAutoBattleActionToWebView(result, nextRetriesLeft),
                        AUTO_SUBMIT_RETRY_DELAY_MS
                );
                return;
            }

            if (missing) {
                Log.w(TAG, BG_TRACE_PREFIX + " submitAutoBattleAction: submit path still missing after retries");
            } else {
                Log.d(TAG, BG_TRACE_PREFIX + " submitAutoBattleAction: status=" + status);
            }
        });
    }

    /**
     * Извлекает vcode из payload авто-удара (`vcode|enemy|group|...`) и синхронизирует `AppVars.VCode`.
     *
     * Зависимости:
     * - `FightViewModel -> LezFight.BuildResult()` формирует payload с vcode первым токеном;
     * - `schedulePostResponseReload(...)` и recovery-ветка используют `AppVars.VCode`.
     */
    private void adoptVCodeFromAutoSubmitPayload(String payload) {
        String vcode = extractVCodeFromAutoSubmitPayload(payload);
        if (vcode.isEmpty()) {
            return;
        }
        if (!vcode.equals(AppVars.VCode)) {
            AppVars.VCode = vcode;
            Log.d(TAG, BG_TRACE_PREFIX + " adoptVCodeFromAutoSubmitPayload: vcode updated");
        }
    }

    private String extractVCodeFromAutoSubmitPayload(String payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        int delimiterPos = payload.indexOf('|');
        String token = delimiterPos >= 0 ? payload.substring(0, delimiterPos) : payload;
        token = token == null ? "" : token.trim();
        if (token.isEmpty()) {
            return "";
        }
        if (!token.matches("[0-9a-fA-F]{6,32}")) {
            return "";
        }
        return token;
    }




    /**
     * Открыть URL в новой вспомогательной вкладке.
     * Аналог CreateNewTab в C# версии (FormMainTabs.cs).
     * @param url URL для загрузки
     * @param title Заголовок вкладки (может быть "PINFO" для декодирования ника)
     */
    public void openInNewTab(String url, String title) {
        Log.d(TAG, "openInNewTab: " + title + " -> " + url);
        
        // Декодируем ник для pinfo/pname (аналог NickDecode в HelperConverters.cs)
        if ("PINFO".equals(title) && url != null) {
            try {
                String decoded = java.net.URLDecoder.decode(url, "windows-1251");
                int idx = decoded.indexOf("pinfo.cgi?");
                if (idx != -1) {
                    String nick = decoded.substring(idx + 10);
                    nick = nick.replace("|", " ").replace("%20", " ").replace("%2B", "+");
                    title = nick;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error decoding nick", e);
            }
        }
        
        if (tabManager != null) {
            tabManager.openTab(url, title);
        }
    }

    // Диалог капчи для завершения боя (когда сервер требует подтверждение).
    /**
     * Показывает диалог завершения боя с ручным вводом капчи.
     *
     * Что делает:
     * - строит UI диалога (картинка капчи + поле ввода кода),
     * - загружает изображение капчи в фоне через {@link #loadCaptchaImageAsync(String, android.widget.ImageView, android.widget.ProgressBar)},
     * - при нажатии "ОК" формирует URL завершения боя с параметром {@code code=} и открывает его в основном WebView.
     *
     * Зависимости:
     * - {@code AppVars.ACTION_SHOW_CAPTCHA} (вызов через BroadcastReceiver),
     * - {@code binding.appBarMain.contentMain.webView} (отправка URL завершения),
     * - {@link Uri#encode(String)} (безопасная передача введённого кода),
     * - методы загрузки изображения капчи (см. ниже).
     *
     * @param captchaUrl URL изображения капчи ({@code /modules/code/code.php?...}).
     * @param finishUrl URL завершения боя ({@code main.php?get_id=61&act=7...}).
     */
    private void showCaptchaDialog(String captchaUrl, String finishUrl) {
        Log.d(TAG, "showCaptchaDialog: " + captchaUrl + " -> " + finishUrl);
        if (activeFightCaptchaDialog != null && activeFightCaptchaDialog.isShowing()) {
            boolean sameCaptcha = isSameCaptchaUrl(captchaUrl, activeFightCaptchaUrl);
            boolean sameFinish = isSameFightFinishUrl(finishUrl, activeFightFinishUrl);
            if (sameCaptcha && sameFinish) {
                Log.d(TAG, "showCaptchaDialog: already visible with same challenge, skip duplicate");
            } else {
                Log.d(TAG, "showCaptchaDialog: replacing dialog with newer challenge");
                replacingFightCaptchaDialog = true;
                activeFightCaptchaLoadSeq++;
                activeFightCaptchaDialog.dismiss();
                binding.getRoot().post(() -> showCaptchaDialog(captchaUrl, finishUrl));
            }
            return;
        }
        AppVars.IsFightCaptchaDialogVisible = true;
        showCaptchaSystemNotification();

        int imageHeightPx = (int) (getResources().getDisplayMetrics().density * 120);
        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);

        android.widget.ImageView imageView = new android.widget.ImageView(this);
        imageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(true);
        imageView.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, imageHeightPx));

        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(5)
        });

        android.widget.Button refreshButton = new android.widget.Button(this);
        refreshButton.setText("↻");
        refreshButton.setAllCaps(false);
        input.setHint("Код с картинки");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 12);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(progressBar);
        layout.addView(imageView);
        android.widget.LinearLayout inputRow = new android.widget.LinearLayout(this);
        inputRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        inputRow.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        android.widget.LinearLayout.LayoutParams inputParams = new android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        inputRow.addView(input, inputParams);
        inputRow.addView(refreshButton);
        layout.addView(inputRow);

        final boolean[] captchaSubmitted = {false};
        boolean isFishCaptcha = finishUrl != null
                && ((finishUrl.contains("get_id=55") && finishUrl.contains("act=4"))
                || (finishUrl.contains("/gameplay/ajax/fish_ajax.php") && finishUrl.contains("act=2")));
        String captchaTitle = isFishCaptcha
                ? "Введите капчу для рыбалки"
                : "Введите капчу для завершения боя";

        AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(captchaTitle)
                .setView(layout)
                .setPositiveButton("ОК", (d, which) -> {
                    String code = input.getText().toString().trim();
                    if (code.isEmpty()) return;
                    captchaSubmitted[0] = true;
                    if (AppVars.ResumeAutoboiAfterCaptcha
                            && AppVars.Profile != null
                            && AppVars.Profile.LezDoAutoboi) {
                        AppVars.Autoboi = ru.neverlands.abclient.model.AutoboiState.AutoboiOn;
                        Log.d(TAG, "showCaptchaDialog: restoring autoboi after captcha submit");
                    }
                    AppVars.ResumeAutoboiAfterCaptcha = false;
                    String submitUrl = appendOrReplaceCaptchaCode(finishUrl, code);
                    Log.d(TAG, "showCaptchaDialog: submitting " + submitUrl);
                    submitCaptchaSolution(submitUrl, isFishCaptcha);
                })
                .setNegativeButton("Отмена", (d, which) -> AppVars.ResumeAutoboiAfterCaptcha = false)
                .create();

        dialog.setOnCancelListener(d -> AppVars.ResumeAutoboiAfterCaptcha = false);
        dialog.setOnDismissListener(d -> {
            stopFightCaptchaAutoRefresh();
            AppVars.IsFightCaptchaDialogVisible = false;
            cancelCaptchaSystemNotification();
            activeFightCaptchaDialog = null;
            activeFightCaptchaUrl = "";
            activeFightFinishUrl = "";
            activeFightCaptchaImageLocked = false;
            activeFightCaptchaLoadSeq++;
            if (replacingFightCaptchaDialog) {
                replacingFightCaptchaDialog = false;
            } else if (!captchaSubmitted[0]) {
                AppVars.ResumeAutoboiAfterCaptcha = false;
            }
        });
        activeFightCaptchaDialog = dialog;
        activeFightCaptchaUrl = captchaUrl == null ? "" : captchaUrl;
        activeFightFinishUrl = finishUrl == null ? "" : finishUrl;
        dialog.show();

        dialog.setOnShowListener(d -> {
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setEnabled(false);
            }
            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) { }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    String value = s == null ? "" : s.toString().trim();
                    boolean enabled = false;
                    if (!value.isEmpty() && value.length() <= 5) {
                        try {
                            int numeric = Integer.parseInt(value);
                            enabled = numeric >= 0 && numeric <= 99999;
                        } catch (NumberFormatException ignored) {
                            enabled = false;
                        }
                    }
                    android.widget.Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (btn != null) {
                        btn.setEnabled(enabled);
                    }
                }
            });
        });

        activeFightCaptchaImageAtMs = 0L;
        activeFightCaptchaImageHash = 0;
        activeFightCaptchaImageLocked = false;
        loadCaptchaImageAsync(captchaUrl, imageView, progressBar);
        refreshButton.setOnClickListener(v -> {
            activeFightCaptchaImageAtMs = 0L;
            activeFightCaptchaImageHash = 0;
            activeFightCaptchaImageLocked = false;
            loadCaptchaImageAsync(captchaUrl, imageView, progressBar);
            startFightCaptchaAutoRefresh(imageView, progressBar, captchaUrl);
        });
        startFightCaptchaAutoRefresh(imageView, progressBar, captchaUrl);
    }

    /**
     * Создаёт Android NotificationChannel для уведомлений о боевой капче.
     *
     * Зависимости:
     * - `NotificationManager`/`NotificationChannel` (API 26+),
     * - строки `captcha_notification_*` из `strings.xml`,
     * - системный звук `RingtoneManager.TYPE_NOTIFICATION`.
     *
     * Назначение:
     * - вынести капчу в отдельный канал, чтобы пользователь мог в настройках Android
     *   независимо управлять звуком, вибрацией и показом в шторке.
     */
    private void createCaptchaNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CAPTCHA_NOTIFICATION_CHANNEL_ID,
                getString(R.string.captcha_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(getString(R.string.captcha_notification_channel_description));
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 250, 150, 250});
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(sound, audioAttributes);
        notificationManager.createNotificationChannel(channel);
    }

    /**
     * Проверяет право на отправку уведомлений.
     *
     * Зависимости:
     * - `Manifest.permission.POST_NOTIFICATIONS` (Android 13+),
     * - `ContextCompat.checkSelfPermission`.
     */
    private boolean canPostNotifications() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Запрашивает runtime-разрешение на уведомления (Android 13+), если ещё не выдано.
     *
     * Зависимости:
     * - `ActivityCompat.requestPermissions`,
     * - `REQUEST_CODE_POST_NOTIFICATIONS`,
     * - обработчик `onRequestPermissionsResult`.
     */
    private void requestPostNotificationsPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (canPostNotifications()) {
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE_POST_NOTIFICATIONS
        );
    }

    /**
     * Публикует системное уведомление о появлении капчи.
     *
     * Зависимости:
     * - канал `CAPTCHA_NOTIFICATION_CHANNEL_ID`,
     * - `NotificationCompat`/`NotificationManagerCompat`,
     * - `PendingIntent` для возврата в `MainActivity`.
     *
     * Важно:
     * - не отправляет уведомление без `POST_NOTIFICATIONS` на Android 13+.
     */
    private void showCaptchaSystemNotification() {
        if (!canPostNotifications()) {
            Log.d(TAG, "showCaptchaSystemNotification: POST_NOTIFICATIONS not granted");
            return;
        }
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CAPTCHA_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.captcha_notification_title))
                .setContentText(getString(R.string.captcha_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent);
        NotificationManagerCompat.from(this).notify(CAPTCHA_NOTIFICATION_ID, builder.build());
    }

    /**
     * Снимает активное уведомление о капче при закрытии popup-диалога.
     */
    private void cancelCaptchaSystemNotification() {
        NotificationManagerCompat.from(this).cancel(CAPTCHA_NOTIFICATION_ID);
    }

    /**
     * Формирует URL завершения боя с корректным параметром {@code code=} для капчи.
     * Меняется только query-параметр {@code code}; параметр {@code vcode} не затрагивается.
     */
    private String appendOrReplaceCaptchaCode(String finishUrl, String code) {
        String submitUrl = finishUrl == null ? "" : finishUrl;
        String encodedCode = Uri.encode(code == null ? "" : code);
        if (submitUrl.isEmpty()) {
            return "code=" + encodedCode;
        }

        String fragment = "";
        int fragmentIndex = submitUrl.indexOf('#');
        if (fragmentIndex >= 0) {
            fragment = submitUrl.substring(fragmentIndex);
            submitUrl = submitUrl.substring(0, fragmentIndex);
        }

        Pattern codeParamPattern = Pattern.compile("([?&])code=[^&]*");
        Matcher codeMatcher = codeParamPattern.matcher(submitUrl);
        if (codeMatcher.find()) {
            submitUrl = codeMatcher.replaceFirst("$1code=" + encodedCode);
        } else {
            int queryIndex = submitUrl.indexOf('?');
            if (queryIndex >= 0) {
                String base = submitUrl.substring(0, queryIndex);
                String query = submitUrl.substring(queryIndex + 1);
                submitUrl = base + "?code=" + encodedCode + (query.isEmpty() ? "" : "&" + query);
            } else {
                submitUrl = submitUrl + "?code=" + encodedCode;
            }
        }
        return submitUrl + fragment;
    }

    /**
     * Отправляет решение captcha в корректный серверный контур (бой/рыбалка), не ломая frame-flow.
     *
     * Зависимости:
     * - `binding.appBarMain.contentMain.webView`: основной игровой WebView;
     * - `submitFishCaptchaViaAjaxOrFallback(...)`: fish captcha (`fish_ajax.php?act=2`) через Ajax;
     * - `WebView.loadUrl(...)`: fallback и штатный submit для боя (`main.php?get_id=61&act=7...`).
     */
    private void submitCaptchaSolution(String submitUrl, boolean isFishCaptcha) {
        if (binding == null || binding.appBarMain == null || binding.appBarMain.contentMain == null) {
            Log.w(TAG, "submitCaptchaSolution: skip, binding/content is null");
            return;
        }
        WebView mainWebView = binding.appBarMain.contentMain.webView;
        if (mainWebView == null) {
            Log.w(TAG, "submitCaptchaSolution: skip, mainWebView is null");
            return;
        }
        if (isFishCaptcha) {
            submitFishCaptchaViaAjaxOrFallback(mainWebView, submitUrl);
            return;
        }
        mainWebView.loadUrl(submitUrl);
    }

    /**
     * Отправляет fish captcha через JS `AjaxGet(...)`, чтобы `RESO@...` обрабатывался игровым JS-контуром,
     * а не открывался как отдельная страница верхнего фрейма.
     *
     * Зависимости:
     * - `WebView.evaluateJavascript(...)` для вызова JS в текущем контексте страницы;
     * - `AjaxGet`/`window.AjaxGet`/`parent.AjaxGet`/`frames.main.AjaxGet` как разные точки входа;
     * - `Gson` для чтения статуса выполнения JS и принятия решения о fallback.
     */
    private void submitFishCaptchaViaAjaxOrFallback(WebView mainWebView, String submitUrl) {
        if (submitUrl == null || submitUrl.isEmpty()) {
            Log.w(TAG, "submitFishCaptchaViaAjaxOrFallback: submitUrl is empty");
            return;
        }

        final com.google.gson.Gson gson = new com.google.gson.Gson();
        final String jsonUrl = gson.toJson(submitUrl);
        String script = "(function(rawUrl){"
                + "try{"
                + "var abs=(rawUrl||'').toString();"
                + "if(!abs){return 'empty_url';}"
                + "var rel=abs;"
                + "try{"
                + "if(/^https?:\\/\\//i.test(abs)){"
                + "var a=document.createElement('a');a.href=abs;"
                + "rel=(a.pathname||'')+(a.search||'')+(a.hash||'');"
                + "if(!rel){rel=abs;}"
                + "}"
                + "}catch(_normErr){}"
                + "try{"
                + "rel=String(rel||'').replace(/^https?:\\/\\/[^/]+/i,'');"
                + "var low=rel.toLowerCase();"
                + "var prefix='/gameplay/ajax/';"
                + "var idx=low.indexOf(prefix);"
                + "if(idx>=0){rel=rel.substring(idx+prefix.length);}"
                + "rel=rel.replace(/^\\/+/, '');"
                + "}catch(_relErr){}"
                + "if(!rel){return 'empty_rel';}"
                + "if(typeof AjaxGet==='function'){AjaxGet(rel);return 'ok_ajaxget';}"
                + "if(typeof window.AjaxGet==='function'){window.AjaxGet(rel);return 'ok_window_ajaxget';}"
                + "if(window&&window.parent&&window.parent!==window&&typeof window.parent.AjaxGet==='function'){window.parent.AjaxGet(rel);return 'ok_parent_ajaxget';}"
                + "if(window&&window.frames&&window.frames.main&&typeof window.frames.main.AjaxGet==='function'){window.frames.main.AjaxGet(rel);return 'ok_frame_main_ajaxget';}"
                + "return 'missing_ajaxget';"
                + "}catch(e){"
                + "console.log('ABCLIENT_FISH_CAPTCHA_SUBMIT_ERR:'+e);"
                + "return 'error';"
                + "}"
                + "})(" + jsonUrl + ")";

        mainWebView.evaluateJavascript(script, rawStatus -> {
            String status;
            try {
                status = gson.fromJson(rawStatus, String.class);
            } catch (Exception ignored) {
                status = rawStatus;
            }

            boolean ajaxOk = status != null && status.startsWith("ok_");
            Log.d(TAG, "submitFishCaptchaViaAjaxOrFallback: status=" + status);
            if (!ajaxOk) {
                Log.w(TAG, "submitFishCaptchaViaAjaxOrFallback: fallback to loadUrl, status=" + status);
                mainWebView.loadUrl(submitUrl);
            }
        });
    }

    // Локальные события: чат, загрузка URL, JS, капча, авто-рыбалка.
    /**
     * Асинхронно загружает PNG-капчу и отображает её в диалоге.
     *
     * Что делает:
     * - очищает текущее изображение и показывает индикатор загрузки,
     * - запускает фоновый поток для HTTP-запроса,
     * - декодирует полученные байты в Bitmap,
     * - возвращается в UI-поток и обновляет ImageView.
     *
     * Зависимости:
     * - {@link #downloadCaptchaImageBytes(String)} (сетевое получение PNG),
     * - {@link android.graphics.BitmapFactory} (декодирование),
     * - проверки {@code isFinishing/isDestroyed} для безопасного обновления UI.
     *
     * @param captchaUrl URL изображения капчи.
     * @param imageView целевой ImageView в диалоге.
     * @param progressBar индикатор загрузки.
     */
    private void loadCaptchaImageAsync(String captchaUrl, android.widget.ImageView imageView, android.widget.ProgressBar progressBar) {
        final long loadSeq = ++activeFightCaptchaLoadSeq;
        progressBar.setVisibility(View.VISIBLE);
        imageView.setImageDrawable(null);
        if (updateCaptchaImageFromCaptured(imageView, progressBar, captchaUrl, true)) {
            return;
        }
        Log.d(TAG, "loadCaptchaImageAsync: waiting captured bytes before network fallback, delayMs="
                + CAPTCHA_NETWORK_FALLBACK_DELAY_MS);

        byte[] cachedBytes = AppVars.LastFightCaptchaImageBytes;
        String cachedUrl = AppVars.LastFightCaptchaImageUrl;
        long cachedAt = AppVars.LastFightCaptchaImageAtMs;
        long cachedAge = cachedAt > 0 ? (System.currentTimeMillis() - cachedAt) : Long.MAX_VALUE;
        boolean isSameCaptchaUrl = isSameCaptchaUrl(cachedUrl, captchaUrl);
        boolean hasFreshSameUrlCaptchaBytes = isSameCaptchaUrl && cachedBytes != null && cachedBytes.length > 0
                && cachedAge >= 0 && cachedAge <= 30000L;
        if (hasFreshSameUrlCaptchaBytes) {
            android.graphics.Bitmap cachedBitmap = android.graphics.BitmapFactory.decodeByteArray(cachedBytes, 0, cachedBytes.length);
            if (cachedBitmap != null) {
                Log.d(TAG, "loadCaptchaImageAsync: showing cached captcha preview, size=" + cachedBytes.length + ", ageMs=" + cachedAge);
                imageView.setImageBitmap(cachedBitmap);
            } else {
                Log.d(TAG, "loadCaptchaImageAsync: cached captcha bytes exist but decode failed, size=" + cachedBytes.length);
            }
        }

        // Важно: повторный GET code.php может сгенерировать новый challenge.
        // Поэтому даём WebView/interceptor приоритет и только после таймаута делаем fallback-запрос.
        fightCaptchaHandler.postDelayed(() -> {
            if (loadSeq != activeFightCaptchaLoadSeq) {
                Log.d(TAG, "loadCaptchaImageAsync: skip stale delayed fallback, loadSeq=" + loadSeq
                        + ", activeSeq=" + activeFightCaptchaLoadSeq);
                return;
            }
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (activeFightCaptchaDialog == null || !activeFightCaptchaDialog.isShowing()) {
                return;
            }
            if (!isSameCaptchaUrl(captchaUrl, activeFightCaptchaUrl)) {
                return;
            }
            if (activeFightCaptchaImageLocked) {
                Log.d(TAG, "loadCaptchaImageAsync: challenge already locked from interceptor, skip network fallback");
                return;
            }
            if (updateCaptchaImageFromCaptured(imageView, progressBar, captchaUrl, true)) {
                Log.d(TAG, "loadCaptchaImageAsync: captured bytes arrived before fallback, skip network");
                return;
            }
            if (activeFightCaptchaImageLocked) {
                Log.d(TAG, "loadCaptchaImageAsync: challenge locked after captured update, skip network fallback");
                return;
            }

            final long fallbackStartedAtMs = System.currentTimeMillis();
            new Thread(() -> {
                byte[] captchaBytes = downloadCaptchaImageBytes(captchaUrl);
                final android.graphics.Bitmap bitmap = captchaBytes == null
                        ? null
                        : android.graphics.BitmapFactory.decodeByteArray(captchaBytes, 0, captchaBytes.length);

                runOnUiThread(() -> {
                    if (loadSeq != activeFightCaptchaLoadSeq) {
                        Log.d(TAG, "loadCaptchaImageAsync: skip stale network result, loadSeq=" + loadSeq
                                + ", activeSeq=" + activeFightCaptchaLoadSeq);
                        return;
                    }
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (!isSameCaptchaUrl(captchaUrl, activeFightCaptchaUrl)) {
                        Log.d(TAG, "loadCaptchaImageAsync: skip stale fallback, activeCaptchaUrl=" + activeFightCaptchaUrl);
                        return;
                    }

                    // Пока шёл fallback-запрос, bytes могли уже приехать из WebView interceptor — приоритет у них.
                    if (activeFightCaptchaImageLocked) {
                        Log.d(TAG, "loadCaptchaImageAsync: skip network apply, challenge already locked");
                        return;
                    }
                    if (updateCaptchaImageFromCaptured(imageView, progressBar, captchaUrl, true)) {
                        Log.d(TAG, "loadCaptchaImageAsync: intercepted bytes arrived during fallback, network result ignored");
                        return;
                    }
                    if (activeFightCaptchaImageLocked) {
                        Log.d(TAG, "loadCaptchaImageAsync: skip network apply after captured lock");
                        return;
                    }

                    progressBar.setVisibility(View.GONE);
                    if (bitmap != null && captchaBytes != null) {
                        if (hasFreshSameUrlCaptchaBytes && captchaBytes.length < 1024) {
                            Log.w(TAG, "loadCaptchaImageAsync: network captcha is too small, keep cached preview, bytes=" + captchaBytes.length);
                            return;
                        }
                        Log.d(TAG, "loadCaptchaImageAsync: bitmap decoded from network, url=" + captchaUrl
                                + ", bytes=" + captchaBytes.length
                                + ", elapsedMs=" + (System.currentTimeMillis() - fallbackStartedAtMs));
                        imageView.setImageBitmap(bitmap);

                        long now = System.currentTimeMillis();
                        activeFightCaptchaImageAtMs = now;
                        activeFightCaptchaImageHash = Arrays.hashCode(captchaBytes);
                        activeFightCaptchaImageLocked = true;
                        stopFightCaptchaAutoRefresh();
                        // Публикуем fallback-bytes в общий кэш капчи, чтобы последующий WebView-запрос
                        // на тот же URL мог быть обслужен из памяти без повторного code.php запроса.
                        AppVars.LastFightCaptchaImageBytes = captchaBytes;
                        AppVars.LastFightCaptchaImageUrl = captchaUrl;
                        AppVars.LastFightCaptchaImageAtMs = now;
                    } else {
                        Log.w(TAG, "loadCaptchaImageAsync: bitmap decode failed, url=" + captchaUrl
                                + ", bytes=" + (captchaBytes != null ? captchaBytes.length : 0));
                        if (!hasFreshSameUrlCaptchaBytes) {
                            Toast.makeText(this, "Failed to load captcha image", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }, "fight-captcha-loader").start();
        }, CAPTCHA_NETWORK_FALLBACK_DELAY_MS);
    }

    private void tryRefreshCaptchaImageFromLatest(android.widget.ImageView imageView, long dialogShownAtMs, String initialCaptchaUrl) {
        if (activeFightCaptchaDialog == null || !activeFightCaptchaDialog.isShowing()) {
            return;
        }

        byte[] latestBytes = AppVars.LastFightCaptchaImageBytes;
        long latestAtMs = AppVars.LastFightCaptchaImageAtMs;
        String latestUrl = AppVars.LastFightCaptchaImageUrl;
        if (latestBytes == null || latestBytes.length == 0 || latestAtMs <= dialogShownAtMs) {
            return;
        }
        if (initialCaptchaUrl == null || latestUrl == null || !latestUrl.equals(initialCaptchaUrl)) {
            return;
        }

        android.graphics.Bitmap latestBitmap = android.graphics.BitmapFactory.decodeByteArray(latestBytes, 0, latestBytes.length);
        if (latestBitmap == null) {
            return;
        }

        imageView.setImageBitmap(latestBitmap);
        Log.d(TAG, "tryRefreshCaptchaImageFromLatest: image updated from latest bytes,"
                + " initialUrl=" + initialCaptchaUrl
                + ", latestUrl=" + latestUrl
                + ", bytes=" + latestBytes.length
                + ", deltaMs=" + (latestAtMs - dialogShownAtMs));
    }

    /**
     * Периодически проверяет новые байты боевой капчи из WebViewRequestInterceptor.
     * Это прямой аналог C#-подхода (FormCode.ShowPic + AppVars.CodePng): не делаем отдельный HTTP-запрос из popup.
     */
    private void startFightCaptchaAutoRefresh(
            android.widget.ImageView imageView,
            android.widget.ProgressBar progressBar,
            String expectedCaptchaUrl
    ) {
        stopFightCaptchaAutoRefresh();
        fightCaptchaRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (activeFightCaptchaDialog == null || !activeFightCaptchaDialog.isShowing()) {
                    return;
                }
                updateCaptchaImageFromCaptured(imageView, progressBar, expectedCaptchaUrl, false);
                fightCaptchaHandler.postDelayed(this, 350);
            }
        };
        fightCaptchaHandler.postDelayed(fightCaptchaRefreshRunnable, 200);
    }

    private void stopFightCaptchaAutoRefresh() {
        if (fightCaptchaRefreshRunnable != null) {
            fightCaptchaHandler.removeCallbacks(fightCaptchaRefreshRunnable);
            fightCaptchaRefreshRunnable = null;
        }
    }

    /**
     * Обновляет изображение popup-капчи из AppVars.LastFightCaptchaImageBytes.
     */
    private boolean updateCaptchaImageFromCaptured(
            android.widget.ImageView imageView,
            android.widget.ProgressBar progressBar,
            String expectedCaptchaUrl,
            boolean forceUpdate
    ) {
        byte[] latestBytes = AppVars.LastFightCaptchaImageBytes;
        if (latestBytes == null || latestBytes.length == 0) {
            if (forceUpdate) {
                progressBar.setVisibility(View.VISIBLE);
            }
            return false;
        }

        String latestUrl = AppVars.LastFightCaptchaImageUrl;
        if (!isSameCaptchaUrl(expectedCaptchaUrl, latestUrl)) {
            if (forceUpdate) {
                progressBar.setVisibility(View.VISIBLE);
                Log.d(TAG, "updateCaptchaImageFromCaptured: skip foreign bytes, expected="
                        + expectedCaptchaUrl + ", got=" + latestUrl);
            }
            return false;
        }

        long latestAtMs = AppVars.LastFightCaptchaImageAtMs;
        int latestHash = Arrays.hashCode(latestBytes);
        boolean hasNewTime = latestAtMs > activeFightCaptchaImageAtMs;
        boolean hasNewBytes = latestHash != activeFightCaptchaImageHash;
        if (!forceUpdate && !hasNewTime && !hasNewBytes) {
            return false;
        }
        if (!forceUpdate && activeFightCaptchaImageLocked) {
            return false;
        }
        if (latestAtMs > 0L) {
            long ageMs = System.currentTimeMillis() - latestAtMs;
            if (ageMs >= 0 && ageMs < CAPTCHA_IMAGE_STABILIZE_DELAY_MS) {
                if (forceUpdate) {
                    progressBar.setVisibility(View.VISIBLE);
                }
                Log.d(TAG, "updateCaptchaImageFromCaptured: wait stabilize, ageMs=" + ageMs
                        + ", need=" + CAPTCHA_IMAGE_STABILIZE_DELAY_MS);
                return false;
            }
        }

        android.graphics.Bitmap latestBitmap = android.graphics.BitmapFactory.decodeByteArray(latestBytes, 0, latestBytes.length);
        if (latestBitmap == null) {
            if (forceUpdate) {
                progressBar.setVisibility(View.VISIBLE);
            }
            Log.w(TAG, "updateCaptchaImageFromCaptured: decode failed, bytes=" + latestBytes.length);
            return false;
        }

        imageView.setImageBitmap(latestBitmap);
        progressBar.setVisibility(View.GONE);
        int previousHash = activeFightCaptchaImageHash;
        if (latestAtMs > 0L) {
            activeFightCaptchaImageAtMs = latestAtMs;
        }
        activeFightCaptchaImageHash = latestHash;
        // Стабилизируем challenge в текущем popup: после первой отрисовки не перерисовываем
        // автоматически, чтобы пользователь вводил код именно с зафиксированной картинки.
        if (!forceUpdate && previousHash == 0) {
            activeFightCaptchaImageLocked = true;
            stopFightCaptchaAutoRefresh();
        }
        Log.d(TAG, "updateCaptchaImageFromCaptured: image updated, bytes=" + latestBytes.length
                + ", atMs=" + latestAtMs + ", url=" + latestUrl);
        return true;
    }

    private boolean isSameCaptchaUrl(String firstUrl, String secondUrl) {
        if (firstUrl == null || firstUrl.isEmpty() || secondUrl == null || secondUrl.isEmpty()) {
            return false;
        }
        String firstNormalized = normalizeCaptchaUrlForCompare(firstUrl);
        String secondNormalized = normalizeCaptchaUrlForCompare(secondUrl);
        if (firstNormalized.isEmpty() || secondNormalized.isEmpty()) {
            return false;
        }
        return firstNormalized.equals(secondNormalized);
    }

    /**
     * Сравнивает URL завершения боя (finishUrl) с нормализацией протокола/host.
     *
     * Зависимости:
     * - `normalizeFightFinishUrlForCompare`,
     * - используется в `showCaptchaDialog` для определения: тот же challenge или новый.
     */
    private boolean isSameFightFinishUrl(String firstUrl, String secondUrl) {
        if (firstUrl == null || firstUrl.isEmpty() || secondUrl == null || secondUrl.isEmpty()) {
            return false;
        }
        String firstNormalized = normalizeFightFinishUrlForCompare(firstUrl);
        String secondNormalized = normalizeFightFinishUrlForCompare(secondUrl);
        if (firstNormalized.isEmpty() || secondNormalized.isEmpty()) {
            return false;
        }
        return firstNormalized.equals(secondNormalized);
    }

    /**
     * Нормализация URL капчи для сравнения:
     * - http/https + www выравниваются,
     * - query сохраняется (token критичен, должен совпадать с текущим challenge).
     */
    private String normalizeCaptchaUrlForCompare(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return "";
        }
        try {
            String normalized = rawUrl.replaceFirst("^https://", "http://");
            normalized = normalized.replaceFirst("^http://www\\.neverlands\\.ru", "http://neverlands.ru");
            int fragmentIndex = normalized.indexOf('#');
            if (fragmentIndex >= 0) {
                normalized = normalized.substring(0, fragmentIndex);
            }
            return normalized;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeFightFinishUrlForCompare(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return "";
        }
        try {
            String normalized = rawUrl.replaceFirst("^https://", "http://");
            normalized = normalized.replaceFirst("^http://www\\.neverlands\\.ru", "http://neverlands.ru");
            int fragmentIndex = normalized.indexOf('#');
            if (fragmentIndex >= 0) {
                normalized = normalized.substring(0, fragmentIndex);
            }
            return normalized;
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Выполняет HTTP GET изображения капчи и возвращает сырые байты.
     *
     * Что делает:
     * - открывает соединение с таймаутами и отключённым кэшем,
     * - устанавливает безопасный браузерный User-Agent ({@link AppVars#BROWSER_USER_AGENT}),
     * - прокидывает cookies текущей игровой сессии из WebView CookieManager
     *   с fallback через {@link CookiesManager},
     * - читает тело ответа в {@code byte[]}.
     *
     * Зависимости:
     * - {@link HttpURLConnection},
     * - {@link CookieManager} и {@link CookiesManager},
     * - {@link ByteArrayOutputStream}/{@link InputStream}.
     *
     * @param captchaUrl URL изображения капчи.
     * @return bytes изображения или {@code null} при ошибке/неуспешном HTTP-коде.
     */
    private byte[] downloadCaptchaImageBytes(String captchaUrl) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            URL url = new URL(captchaUrl);
            java.net.Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                Log.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct captcha download: " + captchaUrl);
                return null;
            }
            connection = activeProxy != null
                    ? (HttpURLConnection) url.openConnection(activeProxy)
                    : (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Referer", "http://neverlands.ru/main.php");
            connection.setRequestProperty("User-Agent", AppVars.BROWSER_USER_AGENT);

            String cookie = CookieManager.getInstance().getCookie(captchaUrl);
            if (cookie == null || cookie.isEmpty()) {
                cookie = CookieManager.getInstance().getCookie("http://neverlands.ru/");
            }
            if ((cookie == null || cookie.isEmpty()) && url.getHost() != null) {
                cookie = CookiesManager.obtain(url.getHost());
            }
            if (cookie != null && !cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
                Log.d(TAG, "downloadCaptchaImageBytes: using cookie len=" + cookie.length());
            } else {
                Log.w(TAG, "downloadCaptchaImageBytes: cookie is empty for " + captchaUrl);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                Log.w(TAG, "downloadCaptchaImageBytes: HTTP " + responseCode + " for " + captchaUrl);
                return null;
            }

            inputStream = connection.getInputStream();
            outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            byte[] data = outputStream.toByteArray();
            Log.d(TAG, "downloadCaptchaImageBytes: loaded " + data.length + " bytes from " + captchaUrl);
            return data.length > 0 ? data : null;
        } catch (Exception e) {
            Log.e(TAG, "downloadCaptchaImageBytes: failed for " + captchaUrl, e);
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            String action = intent.getAction();
            Log.d(TAG, BG_TRACE_PREFIX + " screenStateReceiver: action=" + action);
            logBackgroundState("screen_event_" + action);
            AutoModeForegroundService.syncServiceState(context, "screen_event_" + action);

            if (Intent.ACTION_SCREEN_OFF.equals(action) && shouldKeepBackgroundLoops()) {
                startRoomUsersPolling();
                requestRoomUsersRefreshSoon();
            }
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case AppVars.ACTION_ADD_CHAT_MESSAGE:
                    String message = intent.getStringExtra("message");
                    if (message != null) {
                        Chat.addMessageToChat(message);
                    }
                    break;
                case AppVars.ACTION_WEBVIEW_LOAD_URL:
                    String url = intent.getStringExtra("url");
                    if (url != null && binding.appBarMain.contentMain.webView != null) {
                        binding.appBarMain.contentMain.webView.loadUrl(url);
                    }
                    break;
                case AppVars.ACTION_WEBVIEW_EVAL_JS:
                    String js = intent.getStringExtra("js");
                    if (js != null && binding.appBarMain.contentMain.webView != null) {
                        binding.appBarMain.contentMain.webView.evaluateJavascript(js, null);
                    }
                    break;
                case AppVars.ACTION_SHOW_CAPTCHA:
                    String captchaUrl = intent.getStringExtra("captchaUrl");
                    String finishUrl = intent.getStringExtra("finishUrl");
                    if (captchaUrl != null && finishUrl != null) {
                        showCaptchaDialog(captchaUrl, finishUrl);
                    }
                    break;
                case AppVars.ACTION_STOP_AUTOFISH:
                    Toast.makeText(context, "Авто-Рыбалка остановлена", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    // Основная инициализация UI/WebView/менеджеров.
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isRoomManagerStarted = false;
        AppVars.init(this);
        registerAppBroadcastReceiverIfNeeded();
        registerScreenStateReceiverIfNeeded();
        createCaptchaNotificationChannel();
        requestPostNotificationsPermissionIfNeeded();
        ContactsManager.initialize(this);
        AppVars.mainActivity = new WeakReference<>(this);
        Log.i(TAG, "ABCLIENT_ANDROID_BUILD=" + BUILD_MARKER);
        logBackgroundState("onCreate_afterInit");
        
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        Toolbar toolbar = binding.appBarMain.toolbar;
        setSupportActionBar(toolbar);
        if (AppVars.Profile != null && AppVars.Profile.UserNick != null) {
            getSupportActionBar().setTitle(AppVars.Profile.UserNick);
        }

        // Поднимаем proxy runtime до первой загрузки frame-URL, чтобы сетевой контур
        // Main WebView и вспомогательных WebView сразу работал через localhost proxy.
        ProxyRuntimeManager.ensureStarted(getApplicationContext(), AppVars.Profile);
        ProxyRuntimeManager.applyWebViewProxyOverride(getApplicationContext());
        Log.i(TAG, "PROXY_BOOT: MainActivity runtime state, running="
                + ProxyRuntimeManager.isRunning() + ", port=" + ProxyRuntimeManager.getActivePort());
        
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);

        View headerView = navigationView.getHeaderView(0);
        TextView navHeaderTitle = headerView.findViewById(R.id.nav_header_title);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0)).versionName;
                navHeaderTitle.setText("v" + versionName);
            } else {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                navHeaderTitle.setText("v" + versionName);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            navHeaderTitle.setText("");
        }

        // Инициализируем все WebView (main + chat + скрытый ch_refr).
        setupWebViews();
        
        // Инициализация менеджера вкладок (аналог TabControl из C# версии)
        com.google.android.material.tabs.TabLayout tabLayout = binding.appBarMain.tabLayout;
        View mainContent = binding.appBarMain.contentMain.tabMainContent;
        android.widget.FrameLayout secondaryContainer = binding.appBarMain.contentMain.tabSecondaryContainer;
        tabManager = new TabManager(this, tabLayout, mainContent, secondaryContainer);
        
        // Инициализация панели быстрых кнопок - ищем по всему корневому view
        quickButtonsPanel = new QuickButtonsPanel(this, binding.getRoot(), tabManager, actionType -> {
            if (actionType == ru.neverlands.abclient.model.QuickActionType.QUICK_ACTIONS) {
                ru.neverlands.abclient.ui.QuickActionsBottomSheet.newInstance(null)
                    .show(getSupportFragmentManager(), "QuickActions");
            }
        });
        
        // Первичная загрузка main.php + чат-фреймов.
        loadInitialUrls();
        AutoFunctionsManager.getInstance(this).restorePersistentAutoModesAfterLogin();

        // Подписка на действия автободя: результат -> AutoSubmit() в WebView.
        fightViewModel = new ViewModelProvider(this).get(FightViewModel.class);
        fightViewModel.getSubmitAction().observe(this, result -> {
            if (result != null) {
                enqueueAutoBattleSubmit(result);
                fightViewModel.onActionSubmitted();
            }
        });

        AppVars.NextCheckNoConnection = new Date(System.currentTimeMillis());
        startTimer();
        startChatRefresh();
        AutoModeForegroundService.syncServiceState(this, "onCreate");
    }

    /**
     * Планирует авто-удар с учётом пользовательской задержки между ударами.
     *
     * Поведение:
     * - если задержка не задана (0 сек), отправляем сразу;
     * - если задержка задана и ещё не прошла, ставим отложенную отправку payload (postDelayed),
     *   а не теряем ход.
     *
     * Зависимости:
     * - `AppVars.CurrentAutoBattleHitDelaySec` (задержка активной группы из вкладки "Ротация"),
     * - `submitAutoBattleActionToWebView(...)` (реальная отправка POST в бой),
     * - `lastAutoBattleSubmitAtMs` (дедуп/троттлинг по времени).
     */
    private void enqueueAutoBattleSubmit(String payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }

        long waitMs = getAutoBattleSubmitWaitMs();
        if (waitMs <= 0L) {
            clearPendingAutoBattleSubmit();
            submitAutoBattleNow(payload);
            return;
        }

        pendingAutoBattleSubmitPayload = payload;
        if (pendingAutoBattleSubmitRunnable != null) {
            autoBattleDelayHandler.removeCallbacks(pendingAutoBattleSubmitRunnable);
        }

        pendingAutoBattleSubmitRunnable = () -> {
            String delayedPayload = pendingAutoBattleSubmitPayload;
            clearPendingAutoBattleSubmit();
            if (delayedPayload == null || delayedPayload.isEmpty()) {
                return;
            }
            long secondCheckWaitMs = getAutoBattleSubmitWaitMs();
            if (secondCheckWaitMs > 0L) {
                // Редкий случай смещения таймера/часов: перепланируем корректно.
                enqueueAutoBattleSubmit(delayedPayload);
                return;
            }
            submitAutoBattleNow(delayedPayload);
        };

        autoBattleDelayHandler.postDelayed(pendingAutoBattleSubmitRunnable, waitMs);
        Log.d(TAG, BG_TRACE_PREFIX + " autoBattleDelay: queued, waitMs="
                + waitMs + ", configuredSec=" + Math.max(0, AppVars.CurrentAutoBattleHitDelaySec));
    }

    private void submitAutoBattleNow(String payload) {
        lastAutoBattleSubmitAtMs = System.currentTimeMillis();
        Log.d(TAG, BG_TRACE_PREFIX + " autoBattleDelay: submit now");
        // Подаём действие через безопасный wrapper с retry,
        // чтобы избежать race "AutoSubmit is not defined" после resume/screen on.
        submitAutoBattleActionToWebView(payload, AUTO_SUBMIT_MAX_RETRY_COUNT);
    }

    private long getAutoBattleSubmitWaitMs() {
        int delaySec = Math.max(0, AppVars.CurrentAutoBattleHitDelaySec);
        if (delaySec <= 0) {
            return 0L;
        }
        long configuredDelayMs = Math.min(300_000L, delaySec * 1000L);
        if (lastAutoBattleSubmitAtMs <= 0L) {
            return 0L;
        }
        long elapsedMs = System.currentTimeMillis() - lastAutoBattleSubmitAtMs;
        if (elapsedMs >= configuredDelayMs) {
            return 0L;
        }
        return configuredDelayMs - elapsedMs;
    }

    private void clearPendingAutoBattleSubmit() {
        if (pendingAutoBattleSubmitRunnable != null) {
            autoBattleDelayHandler.removeCallbacks(pendingAutoBattleSubmitRunnable);
            pendingAutoBattleSubmitRunnable = null;
        }
        pendingAutoBattleSubmitPayload = "";
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebViews() {
        WebView webView = binding.appBarMain.contentMain.webView;
        WebView chatMsgWebView = binding.appBarMain.contentMain.chatMsgWebview;
        WebView chatUsersWebView = binding.appBarMain.contentMain.chatUsersWebview;
        WebView chatButtonsWebView = binding.appBarMain.contentMain.chatButtonsWebview;

        CustomWebViewClient customWebViewClient = new CustomWebViewClient();

        setupWebView(webView, customWebViewClient);
        setupWebView(chatMsgWebView, customWebViewClient);
        setupWebView(chatUsersWebView, customWebViewClient);
        setupWebView(chatButtonsWebView, customWebViewClient);
        // Скрытый WebView для ch_refr (серверные ответы чата и отправка форм).
        if (chatRefrWebView == null) {
            chatRefrWebView = new WebView(this);
            setupWebView(chatRefrWebView, customWebViewClient);
            chatRefrWebView.setVisibility(View.GONE);
            if (binding != null && binding.getRoot() instanceof ViewGroup) {
                ((ViewGroup) binding.getRoot()).addView(chatRefrWebView, new ViewGroup.LayoutParams(1, 1));
            }
        }

        if (AppVars.lastCookies != null && !AppVars.lastCookies.isEmpty()) {
            java.util.List<java.net.HttpCookie> filteredCookies = new java.util.ArrayList<>();
            java.util.Set<String> names = new java.util.HashSet<>();
            for (int i = AppVars.lastCookies.size() - 1; i >= 0; i--) {
                java.net.HttpCookie cookie = AppVars.lastCookies.get(i);
                if (!names.contains(cookie.getName())) {
                    filteredCookies.add(0, cookie);
                    names.add(cookie.getName());
                }
            }

            CookieManager cookieManager = CookieManager.getInstance();
            String url = "http://neverlands.ru";
            for (java.net.HttpCookie cookie : filteredCookies) {
                String cookieString = cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain();
                cookieManager.setCookie(url, cookieString);
            }
            cookieManager.flush();
            AppVars.lastCookies = null;
        }
    }

    // Первичная загрузка основных и чат-фреймов.
    private void loadInitialUrls() {
        WebView webView = binding.appBarMain.contentMain.webView;
        WebView chatMsgWebView = binding.appBarMain.contentMain.chatMsgWebview;
        WebView chatUsersWebView = binding.appBarMain.contentMain.chatUsersWebview;
        WebView chatButtonsWebView = binding.appBarMain.contentMain.chatButtonsWebview;

        webView.loadUrl("http://neverlands.ru/main.php");
        chatMsgWebView.loadUrl("http://neverlands.ru/ch/msg.php");
        chatUsersWebView.loadUrl("http://neverlands.ru/ch.php?lo=1");
        chatButtonsWebView.loadUrl("http://neverlands.ru/ch/but.php");
    }

    // Подписка на LocalBroadcast события приложения.
    @Override
    protected void onResume() {
        super.onResume();
        registerAppBroadcastReceiverIfNeeded();
        registerScreenStateReceiverIfNeeded();
        startRoomUsersPolling();
        logBackgroundState("onResume");
        AutoModeForegroundService.syncServiceState(this, "onResume");
    }

    // Отписка от LocalBroadcast событий (во избежание утечек).
    @Override
    protected void onPause() {
        super.onPause();
        boolean keepBackgroundLoops = shouldKeepBackgroundLoops();
        logBackgroundState("onPause_keep=" + keepBackgroundLoops);
        if (keepBackgroundLoops) {
            startRoomUsersPolling();
            requestRoomUsersRefreshSoon();
        } else {
            stopRoomUsersPolling();
        }
        AutoModeForegroundService.syncServiceState(this, "onPause");
    }

    // Общая настройка WebView (JS, cookies, bridge, окна).
    @SuppressWarnings("deprecation")
    private void setupWebView(WebView webView, WebViewClient client) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setSupportMultipleWindows(true);

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(client);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.e("JS_CONSOLE", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // Для чата: target="ch_refr" создаёт новое окно — нужен новый WebView (не навигированный),
                // иначе Chromium падает с "New WebView for popup window must not have been previously navigated".
                if (binding != null && binding.appBarMain != null
                        && binding.appBarMain.contentMain != null
                        && view == binding.appBarMain.contentMain.chatButtonsWebview) {
                    WebView popup = createChatPopupWebView();
                    WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                    transport.setWebView(popup);
                    resultMsg.sendToTarget();
                    return true;
                }
                // Перехват window.open() теперь выполняется через JavaScript (см. onPageFinished)
                // Здесь ничего не делаем - просто возвращаем false
                resultMsg.sendToTarget();
                return false;
            }
        });
    }

    // Освобождаем таймеры/вебвью и менеджеры при уничтожении Activity.
    @Override
    protected void onDestroy() {
        ru.neverlands.abclient.utils.DebugLogger.log("MainActivity: onDestroy() called.");
        logBackgroundState("onDestroy_enter");
        stopTimer();
        stopChatRefresh();
        stopRoomUsersPolling();
        clearPendingAutoBattleSubmit();
        unregisterAppBroadcastReceiverIfNeeded();
        unregisterScreenStateReceiverIfNeeded();
        RoomManager.stopTracing();
        AppVars.IsFightCaptchaDialogVisible = false;
        if (activeFightCaptchaDialog != null && activeFightCaptchaDialog.isShowing()) {
            activeFightCaptchaDialog.dismiss();
        }
        activeFightCaptchaDialog = null;
        
        // Уничтожение всех вспомогательных вкладок
        if (tabManager != null) {
            tabManager.destroyAll();
        }

        if (isExiting) {
            ProxyRuntimeManager.stop(true);
        }

        destroyWebView(binding.appBarMain.contentMain.webView);
        destroyWebView(binding.appBarMain.contentMain.chatMsgWebview);
        destroyWebView(binding.appBarMain.contentMain.chatUsersWebview);
        destroyWebView(binding.appBarMain.contentMain.chatButtonsWebview);
        destroyWebView(chatRefrWebView);

        if (isExiting) {
            AutoModeForegroundService.syncServiceState(this, "onDestroy_exiting");
        } else {
            AutoModeForegroundService.syncServiceState(this, "onDestroy");
        }

        super.onDestroy();
    }

    // Безопасное уничтожение WebView, чтобы избежать утечек.
    private void destroyWebView(WebView webView) {
        if (webView != null) {
            android.view.ViewParent parent = webView.getParent();
            if (parent instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) parent).removeView(webView);
            }
            webView.stopLoading();
            webView.getSettings().setJavaScriptEnabled(false);
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        }
    }
    
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = binding.drawerLayout;
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (binding.appBarMain.contentMain.webView.canGoBack()) {
            binding.appBarMain.contentMain.webView.goBack();
        } else {
            if (AppVars.DoPromptExit) {
                showExitConfirmationDialog();
            } else {
                super.onBackPressed();
            }
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_snapshot) {
            takeSnapshot();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void takeSnapshot() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date(System.currentTimeMillis()));
        boolean mainSuccess = false;
        boolean chatSuccess = false;
        boolean chatUsersSuccess = false;

        if (AppVars.lastMainPhpResponse != null) {
            String fileName = "HtmlLog_Main_" + timeStamp + ".txt";
            String html = Russian.getString(AppVars.lastMainPhpResponse);
            mainSuccess = ru.neverlands.abclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
        }

        if (AppVars.lastChatMsgResponse != null) {
            String fileName = "HtmlLog_Chat_" + timeStamp + ".txt";
            String html = Russian.getString(AppVars.lastChatMsgResponse);
            chatSuccess = ru.neverlands.abclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
        }

        binding.appBarMain.contentMain.chatUsersWebview.evaluateJavascript(
                "(function() { return '<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'; })();",
                html -> {
                    String fileName = "HtmlLog_ChatUsers_" + timeStamp + ".txt";
                    ru.neverlands.abclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
                });

        if (mainSuccess || chatSuccess) {
            Toast.makeText(this, "Снимки кода сохранены в папке Logs", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Ошибка: нет данных для сохранения", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.nav_home) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/");
        } else if (id == R.id.nav_map) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/map.php");
        } else if (id == R.id.nav_inventory) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/main.php?get_id=33&act=10");
        } else if (id == R.id.nav_profile) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/main.php?get_id=33&act=1");
        } else if (id == R.id.nav_quick_actions) {
            ru.neverlands.abclient.ui.QuickActionsBottomSheet.newInstance(null).show(getSupportFragmentManager(), "QuickActions");
        } else if (id == R.id.nav_settings) {
        } else if (id == R.id.nav_contacts) {
            Intent intent = new Intent(this, ContactsActivity.class);
            contactsActivityLauncher.launch(intent);
        } else if (id == R.id.nav_logs) {
            Intent intent = new Intent(this, LogsActivity.class);
            startActivity(intent);
        }
        
        DrawerLayout drawer = binding.drawerLayout;
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
    
    // Таймер UI: обновление часов + проверка соединения раз в секунду.
    private void startTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    updateClock();
                    checkConnection();
                });
            }
        }, 0, 1000);
    }
    
    // Остановка таймера UI.
    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "onRequestPermissionsResult: POST_NOTIFICATIONS granted=" + granted);
        }
    }

    // Запуск периодического опроса чата (ch.php?show=1&fyo=...).
    private void startChatRefresh() {
        Log.d(TAG, BG_TRACE_PREFIX + " startChatRefresh: seconds=" + chatRefreshSeconds);
        stopChatRefresh();
        chatRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                requestChatRefresh();
                chatRefreshHandler.postDelayed(this, chatRefreshSeconds * 1000L);
            }
        };
        chatRefreshHandler.postDelayed(chatRefreshRunnable, CHAT_REFRESH_INITIAL_DELAY_MS);
    }

    // Остановка периодического опроса чата.
    private void stopChatRefresh() {
        if (chatRefreshRunnable != null) {
            chatRefreshHandler.removeCallbacks(chatRefreshRunnable);
            chatRefreshRunnable = null;
            Log.d(TAG, BG_TRACE_PREFIX + " stopChatRefresh: stopped");
        } else {
            Log.d(TAG, BG_TRACE_PREFIX + " stopChatRefresh: already stopped");
        }
    }

    // Запрос обновления чата через скрытый chatRefrWebView.
    // Выполняем серверный poll чата через скрытый WebView (ch_refr).
    private void requestChatRefresh() {
        if (chatRefrWebView == null) return;
        long ts = System.currentTimeMillis();
        lastChatRefreshAtMs = ts;
        String url = "http://neverlands.ru/ch.php?" + ts + "&show=1&fyo=" + chatFyo;
        Log.d(TAG, BG_TRACE_PREFIX + " requestChatRefresh: " + url);
        chatRefrWebView.loadUrl(url);

        // Поддерживаем room-list "живым" только при включенном "Слежении за локацией".
        // Это соответствует логике ПК-версии: polling комнаты привязан к LocationTracking.
        try {
            if (AutoFunctionsManager.getInstance(this).isLocationTrackingEnabled()) {
                requestRoomUsersRefreshSoon();
            }
        } catch (Exception e) {
            Log.w(TAG, "requestChatRefresh: failed to refresh room users list", e);
        }
    }

    // Немедленное обновление чата (из JS-моста).
    public void requestChatRefreshNow() {
        requestChatRefresh();
    }

    /**
     * Возвращает таймстамп последнего запроса chat polling.
     * Нужен foreground-сервису для детекта "зависшего" опроса в фоне.
     */
    public long getLastChatRefreshAtMs() {
        return lastChatRefreshAtMs;
    }

    public int getChatRefreshSeconds() {
        return chatRefreshSeconds;
    }

    // Настройка частоты опроса чата (кнопка скорости в chat buttons).
    public void setChatRefreshSeconds(int seconds) {
        if (seconds <= 0) return;
        chatRefreshSeconds = seconds;
        startChatRefresh();
    }

    public int getChatFyo() {
        return chatFyo;
    }

    /**
     * Возвращает true, если периодический chat polling должен быть активен.
     * При `fyo=2` пользователь отключил показ сообщений, poll не форсируем.
     */
    public boolean isChatRefreshEnabled() {
        return chatFyo != 2;
    }

    // Режимы чата: 0-все, 1-только личные, 2-не показывать.
    // Установка режима чата: 0-все, 1-личные, 2-скрыть.
    public void setChatFyo(int fyo) {
        chatFyo = fyo;
        if (chatFyo == 2) {
            stopChatRefresh();
        } else {
            startChatRefresh();
            requestChatRefreshSoon();
        }
    }

    public boolean isChatLatrus() {
        return chatLatrus;
    }

    public void setChatLatrus(boolean value) {
        chatLatrus = value;
    }
    
    // Обновление часов в статус-баре с учетом server diff.
    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        long now = System.currentTimeMillis();
        // Отображаем "серверное" время как в ПК‑версии:
        // localNow - ServDiff = serverNow. Если ServDiff ещё не получен — показываем локальное.
        if (AppVars.Profile != null && AppVars.Profile.ServDiff != Long.MIN_VALUE) {
            now = now - AppVars.Profile.ServDiff;
        }
        binding.appBarMain.contentMain.statusBar.clockTextView.setText(sdf.format(new Date(now)));
        binding.appBarMain.contentMain.statusBar.networkDebugTextView.setText(RuntimeNetTrace.snapshotForUi());
    }
    
    public void updateServerTime(Date serverDateTime) {
        AppVars.ServerDateTime = serverDateTime;
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        binding.appBarMain.contentMain.statusBar.serverTimeTextView.setText(sdf.format(serverDateTime));
    }
    
    // Периодическая проверка соединения (перезагрузка main.php раз в 5 минут).
    private void checkConnection() {
        if (System.currentTimeMillis() > AppVars.NextCheckNoConnection.getTime()) {
            AppVars.NextCheckNoConnection = new Date(System.currentTimeMillis() + 5 * 60 * 1000);
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/main.php");
        }
    }
    
    public void addAddressToStatusString(String address) {
        binding.appBarMain.contentMain.statusBar.statusTextView.setText(address);
    }
    
    public void removeAddressFromStatusString(String address) {
        if (binding.appBarMain.contentMain.statusBar.statusTextView.getText().toString().equals(address)) {
            binding.appBarMain.contentMain.statusBar.statusTextView.setText("");
        }
    }
    
    public void updateSavedTraffic(int bytes) {
        String text = binding.appBarMain.contentMain.statusBar.trafficTextView.getText().toString();
        int savedBytes = 0;
        try {
            savedBytes = Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
        }
        savedBytes += bytes;
        binding.appBarMain.contentMain.statusBar.trafficTextView.setText(String.valueOf(savedBytes));
    }
    
    public void updateSavedTrafficSafe(int bytes) {
        runOnUiThread(() -> updateSavedTraffic(bytes));
    }
    
    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Вы действительно хотите выйти из приложения?")
                .setPositiveButton("Да", (dialog, which) -> {
                    isExiting = true;
                    finish();
                })
                .setNegativeButton("Нет", null)
                .show();
    }

    public void updateRoom(List<RoomManager.MenuItem> pvList, String travmText, List<RoomManager.MenuItem> travmList) {
        runOnUiThread(() -> {
            Spinner pvSpinner = binding.appBarMain.pvSpinner;
            Spinner travmSpinner = binding.appBarMain.travmSpinner;

            if (pvList != null) {
                List<String> pvTitles = new ArrayList<>();
                for (RoomManager.MenuItem item : pvList) {
                    pvTitles.add(item.title);
                }
                ArrayAdapter<String> pvAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, pvTitles);
                pvAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                pvSpinner.setAdapter(pvAdapter);
                pvSpinner.setEnabled(pvList.size() > 0);
            }

            if (travmList != null) {
                List<String> travmTitles = new ArrayList<>();
                travmTitles.add(travmText);
                for (RoomManager.MenuItem item : travmList) {
                    travmTitles.add(item.title);
                }
                ArrayAdapter<String> travmAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, travmTitles);
                travmAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                travmSpinner.setAdapter(travmAdapter);
                travmSpinner.setEnabled(travmList.size() > 0);
            }
        });
    }

    public void addMessageToChat(String message) {
        runOnUiThread(() -> {
            if (message != null) {
                Chat.addMessageToChat(message);
            }
        });
    }
    
    /**
     * Инъекция JavaScript для перехвата кликов по ссылкам.
     * Перехватываем клики и вызываем AndroidBridge для открытия в новой вкладке.
     */
    private void injectClickInterceptor(WebView view) {
        String script = 
            "(function() {" +
            "  if (window._clickInterceptorInjected) return;" +
            "  window._clickInterceptorInjected = true;" +
            "  " +
            "  document.addEventListener('click', function(e) {" +
            "    var target = e.target;" +
            "    while (target) {" +
            "      if (target.tagName === 'A' && target.href) {" +
            "        var href = target.href;" +
            "        if (href.indexOf('forum.neverlands.ru') !== -1 ||" +
            "            href.indexOf('pinfo.cgi') !== -1 ||" +
            "            href.indexOf('ch.php') !== -1 ||" +
            "            href.indexOf('log.php') !== -1 ||" +
            "            href.indexOf('fight') !== -1 ||" +
            "            href.indexOf('pname.cgi') !== -1 ||" +
            "            href.indexOf('pbots.cgi') !== -1) {" +
            "          e.preventDefault();" +
            "          e.stopPropagation();" +
            "          var title = 'Новая вкладка';" +
            "          if (href.indexOf('forum.neverlands.ru') !== -1) title = 'Форум';" +
            "          else if (href.indexOf('pinfo.cgi') !== -1) title = 'PINFO';" +
            "          else if (href.indexOf('ch.php') !== -1) title = 'Комната';" +
            "          else if (href.indexOf('log.php') !== -1 || href.indexOf('fight') !== -1) title = 'Бой';" +
            "          if (window.AndroidBridge) {" +
            "            window.AndroidBridge.openInNewTab(href, title);" +
            "          }" +
            "          return;" +
            "        }" +
            "      }" +
            "      target = target.parentElement;" +
            "    }" +
            "  }, true);" +
            "})()";
        view.evaluateJavascript(script, null);
    }

    // WebViewClient с перехватом запросов, JS-инъекциями и обработкой POST-ответов.
    private class CustomWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            AppLogger.write("Page loaded: " + url);
            if (binding != null && binding.appBarMain != null && binding.appBarMain.contentMain != null
                    && view == binding.appBarMain.contentMain.webView) {
                AppVars.url_main_top = url != null ? url : "";
            }

            if (chatPopupWebViews.contains(view)) {
                chatPopupWebViews.remove(view);
                view.postDelayed(() -> destroyWebView(view), 500);
            }

            if (!"http://neverlands.ru/main.php".equals(url)) {
                resetPostReloadGuard("stable_url");
            }

            // POST-ответ от сервера: document.ff.submit() → WebView загружает main.php без параметров.
            // Это голая страница результата действия (windows-1251, без наших фреймов).
            // Нужно: 1) извлечь системное сообщение, 2) перезагрузить нормальную страницу.
            if ("http://neverlands.ru/main.php".equals(url)) {
                view.evaluateJavascript(
                        "(function(){"
                                + "try{"
                                + "  return (document.getElementsByTagName('frameset').length > 0) ? 'FRAMESET' : 'NO_FRAMESET';"
                                + "}catch(e){"
                                + "  return 'NO_FRAMESET';"
                                + "}"
                                + "})()",
                        modeResult -> {
                            String mode = normalizeJsStringResult(modeResult);
                            if ("FRAMESET".equalsIgnoreCase(mode)) {
                                Log.d(TAG, "onPageFinished: main.php has frameset, skip POST fallback");
                                resetPostReloadGuard("frameset_main");
                                applyPageFinishedFixes(view, url);
                                return;
                            }

                            Log.d(TAG, "onPageFinished: POST-like main.php, processing fallback");
                            handlePostMainPhpResponse(view);
                        }
                );
                return;
            }
            applyPageFinishedFixes(view, url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return WebViewRequestInterceptor.intercept(request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            Log.d(TAG, "shouldOverrideUrlLoading: " + url);
            
            if (url == null) {
                return false;
            }

            String host = "";
            try {
                Uri parsedUri = Uri.parse(url);
                host = parsedUri != null && parsedUri.getHost() != null
                        ? parsedUri.getHost().toLowerCase(Locale.ROOT)
                        : "";
            } catch (Exception ignored) {
            }
            boolean isNeverlandsHost = "neverlands.ru".equals(host) || host.endsWith(".neverlands.ru");
            boolean isForumHost = "forum.neverlands.ru".equals(host);
            String lowerUrl = url.toLowerCase(Locale.ROOT);

            boolean isChatWebView = (binding != null
                    && binding.appBarMain != null
                    && binding.appBarMain.contentMain != null
                    && (view == binding.appBarMain.contentMain.chatMsgWebview
                        || view == binding.appBarMain.contentMain.chatUsersWebview
                        || view == binding.appBarMain.contentMain.chatButtonsWebview))
                    || view == chatRefrWebView;

            if (isChatWebView) {
                // Для внутренних чатовских навигаций не открываем новые вкладки.
                if (url.contains("ch.php")) {
                    return false;
                }
            }
            
            // Определяем тип ссылки (аналог BeforeNewWindow в C# версии)
            String title = "Новая вкладка";
            boolean shouldOpenInNewTab = false;
            
            if (isForumHost) {
                title = "Форум";
                shouldOpenInNewTab = true;
            } else if (isNeverlandsHost && lowerUrl.contains("pinfo.cgi")) {
                // PInfo - открываем в новой вкладке
                title = "Информация";
                shouldOpenInNewTab = true;
            } else if (isNeverlandsHost && lowerUrl.contains("ch.php")) {
                // Комната
                title = "Комната";
                shouldOpenInNewTab = true;
            } else if (isNeverlandsHost && (lowerUrl.contains("log.php") || lowerUrl.contains("fight"))) {
                title = "Бой";
                shouldOpenInNewTab = true;
            } else if (isNeverlandsHost && lowerUrl.contains("pname.cgi")) {
                title = "Поиск";
                shouldOpenInNewTab = true;
            } else if (isNeverlandsHost && lowerUrl.contains("pbots.cgi")) {
                title = "Боты";
                shouldOpenInNewTab = true;
            }
            
            // Открываем в новой вкладке если это special URL
            if (shouldOpenInNewTab) {
                Log.d(TAG, "shouldOverrideUrlLoading: открываем вкладку " + title + " -> " + url);
                openInNewTab(url, title);
                return true; // Не даём WebView загружать этот URL
            }
            
            // Для pinfo.cgi также открываем PinfoActivity
            if (isNeverlandsHost && lowerUrl.contains("/pinfo.cgi")) {
                Intent intent = new Intent(MainActivity.this, PinfoActivity.class);
                intent.putExtra("url", url);
                startActivity(intent);
                return true;
            }
            
            return false;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            String failingUrl = "";
            boolean isMainFrame = true;
            if (request != null) {
                isMainFrame = request.isForMainFrame();
                if (request.getUrl() != null) {
                    failingUrl = request.getUrl().toString();
                }
            }
            int errorCode = error != null ? error.getErrorCode() : Integer.MIN_VALUE;
            String description = error != null && error.getDescription() != null
                    ? error.getDescription().toString()
                    : "";

            Log.e(TAG, "onReceivedError: code=" + errorCode
                    + ", mainFrame=" + isMainFrame
                    + ", url=" + failingUrl
                    + ", desc=" + description);

            maybeRetryMainFrameTimeout(view, failingUrl, errorCode, description, isMainFrame);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            String safeUrl = failingUrl == null ? "" : failingUrl;
            String safeDescription = description == null ? "" : description;

            Log.e(TAG, "onReceivedError(legacy): code=" + errorCode
                    + ", url=" + safeUrl
                    + ", desc=" + safeDescription);

            // На старом колбэке Android не даёт флаг mainFrame; для совместимости считаем этот вызов главным.
            maybeRetryMainFrameTimeout(view, safeUrl, errorCode, safeDescription, true);
        }


    }

    // Чтение файла из assets (например, JS для инъекций).
    private void applyPageFinishedFixes(WebView view, String url) {
        String jsFix = ru.neverlands.abclient.utils.HtmlUtils.getJsFix();
        view.evaluateJavascript(jsFix, null);

        if (url.contains("main.php")) {
            // NOTE: Keep this JS one-line: a literal newline inside quotes breaks parsing (SyntaxError).
            view.evaluateJavascript("javascript:(function() { var frameset = document.getElementsByTagName('frameset')[0]; if (frameset) { frameset.rows = '*,0'; } })()", null);

            try {
                String extractorJs = new String(readAssetFile("js/extract_fight_state.js"));
                view.evaluateJavascript("javascript:" + extractorJs, null);
            } catch (IOException e) {
                Log.e(TAG, "Failed to read extract_fight_state.js", e);
            }

            if (!isRoomManagerStarted) {
                ru.neverlands.abclient.manager.RoomManager.startTracing(MainActivity.this);
                isRoomManagerStarted = true;
            }
        } else if (url.contains("ch.php")) {
            view.evaluateJavascript("javascript:(function() { var frameset = document.getElementsByTagName('frameset')[0]; if (frameset) { frameset.cols = '0, *'; } })()", null);
        }

        injectClickInterceptor(view);
    }

    private void handlePostMainPhpResponse(WebView view) {
        Log.d(TAG, "onPageFinished: POST-ответ main.php, извлекаем сообщение и перезагружаем");
        view.evaluateJavascript(
                "(function() {"
                        + "  var b = document.body ? document.body.innerHTML : '';"
                        + "  var marker = '<font class=nickname><font color=#cc0000><b>';"
                        + "  var end = '<br><br></b></font></font>';"
                        + "  var s = b.indexOf(marker);"
                        + "  if (s >= 0) {"
                        + "    var e = b.indexOf(end, s);"
                        + "    if (e >= 0) return b.substring(s + marker.length, e);"
                        + "  }"
                        + "  return '';"
                        + "})()",
                result -> {
                    boolean hasPostMessage = result != null && !result.equals("\"\"") && !result.equals("null");
                    boolean autoSkinEnabled = AppVars.Profile != null && AppVars.Profile.SkinAuto;

                    if (!hasPostMessage && !autoSkinEnabled) {
                        Log.d(TAG, "onPageFinished: no POST marker on main.php, skip POST fallback");
                        resetPostReloadGuard("no_post_marker");
                        applyPageFinishedFixes(view, "http://neverlands.ru/main.php");
                        return;
                    }

                    if (hasPostMessage) {
                        String msg = result.replaceAll("^\"|\"$", "");
                        if (!msg.isEmpty()) {
                            Log.d(TAG, "onPageFinished: sysMessage из POST = " + msg);
                            Intent msgIntent = new Intent(ru.neverlands.abclient.utils.AppVars.ACTION_ADD_CHAT_MESSAGE);
                            msgIntent.putExtra("message", "<font color=#cc0000><b>" + msg + "</b></font>");
                            LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(msgIntent);
                        }
                    }

                    if (autoSkinEnabled) {
                        view.evaluateJavascript(
                                "(function(){"
                                        + "try{"
                                        + " if(typeof fight_ty!=='undefined' && fight_ty && fight_ty.length>9 && fight_ty[9] && fight_ty[9].length>5){"
                                        + "   var t=fight_ty[9];"
                                        + "   return 'http://neverlands.ru/main.php?get_id=17&type='+t[0]+'&p='+t[1]+'&uid='+t[2]+'&s='+t[3]+'&m='+t[4]+'&vcode='+t[5];"
                                        + " }"
                                        + " var btn=document.querySelector('input[onclick*=\\'get_id=17\\']');"
                                        + " if(btn&&btn.onclick){"
                                        + "   var s=String(btn.onclick);"
                                        + "   var i=s.indexOf('main.php?get_id=17');"
                                        + "   if(i>=0){"
                                        + "     var j=s.indexOf(\"'\",i);"
                                        + "     return j>i ? ('http://neverlands.ru/'+s.substring(i,j)) : '';"
                                        + "   }"
                                        + " }"
                                        + " var a=document.querySelector('a[href*=\\'get_id=17\\']');"
                                        + " if(a&&a.getAttribute('href')){"
                                        + "   var href=a.getAttribute('href');"
                                        + "   return href.indexOf('http')===0 ? href : ('http://neverlands.ru/'+href.replace(/^\\/+/,''));"
                                        + " }"
                                        + "}catch(e){}"
                                        + "return '';"
                                        + "})()",
                                razResult -> {
                                    String razUrl = normalizeJsStringResult(razResult)
                                            .replace("\\u0026", "&")
                                            .replace("&amp;", "&")
                                            .trim();

                                    if (!razUrl.isEmpty() && razUrl.contains("get_id=17")) {
                                        Log.d(TAG, "onPageFinished: POST-ответ, найдена разделка -> " + razUrl);
                                        view.loadUrl(razUrl);
                                        return;
                                    }

                                    schedulePostResponseReload(view, true);
                                }
                        );
                        return;
                    }

                    schedulePostResponseReload(view, false);
                }
        );
    }

    private static String normalizeJsStringResult(String raw) {
        if (raw == null) {
            return "";
        }
        String result = raw.trim();
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result.replace("\\/", "/").replace("\\\"", "\"");
    }

    private byte[] readAssetFile(String fileName) throws IOException {
        AssetManager assetManager = getAssets();
        InputStream inputStream = assetManager.open(fileName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    // Простейшее определение MIME по расширению (для внутренних ответов).
    private String getMimeTypeFromUrl(String url) {
        if (url.endsWith(".css")) return "text/css";
        if (url.endsWith(".js")) return "application/javascript";
        if (url.endsWith(".jpg") || url.endsWith(".jpeg")) return "image/jpeg";
        if (url.endsWith(".png")) return "image/png";
        if (url.endsWith(".gif")) return "image/gif";
        if (url.endsWith(".ico")) return "image/x-icon";
        if (url.endsWith(".swf")) return "application/x-shockwave-flash";
        if (url.contains(".php") || url.endsWith("/") || !url.substring(url.lastIndexOf("/") + 1).contains(".")) return "text/html";
        return "text/plain";
    }

    // Декодирование gzip-ответов, если необходимо.
    private byte[] decompressGzip(byte[] compressedData) throws IOException {
        if (compressedData == null || compressedData.length == 0) {
            return compressedData;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    // Логика кеширования статических ресурсов (изображения/JS/CSS).
    private boolean isCacheable(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
               lowerUrl.endsWith(".png") || lowerUrl.endsWith(".swf") || lowerUrl.endsWith(".ico") ||
               lowerUrl.endsWith(".css") || lowerUrl.contains(".js") ||
               lowerUrl.contains("neverlands.ru/ch.php") || lowerUrl.contains("neverlands.ru/main.php");
    }

    /**
     * POST fallback-навигация после submit-страницы `main.php`.
     * - при AutoSkin=true перезагружает полный `main.php` (для серверного `fight_ty[9]`);
     * - иначе возвращает быстрый `go=inf` кадр.
     */
    private void schedulePostResponseReload(WebView view, boolean autoSkinEnabled) {
        view.postDelayed(() -> {
            if (AppVars.IsFightCaptchaDialogVisible) {
                Log.d(TAG, "onPageFinished: skip POST reload while captcha dialog is visible");
                return;
            }

            String reloadUrl;
            if (autoSkinEnabled) {
                reloadUrl = "http://neverlands.ru/main.php?r=" + System.currentTimeMillis();
            } else {
                reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf";
                if (ru.neverlands.abclient.utils.AppVars.VCode != null
                        && !ru.neverlands.abclient.utils.AppVars.VCode.isEmpty()) {
                    reloadUrl += "&vcode=" + ru.neverlands.abclient.utils.AppVars.VCode;
                }
            }

            if (isPostReloadBlocked(autoSkinEnabled ? "autoskin" : "fight")) {
                Log.w(TAG, "onPageFinished: POST reload blocked by anti-loop guard, url=" + reloadUrl);
                return;
            }

            Log.d(TAG, "onPageFinished: POST-ответ, перезагружаем " + reloadUrl);
            view.loadUrl(reloadUrl);
        }, 300);
    }

    // Создает временный popup WebView (используется chat buttons → ch_refr).

    private boolean isPostReloadBlocked(String key) {
        long now = System.currentTimeMillis();
        if (now < postReloadGuardBlockUntilMs) {
            return true;
        }

        if (!key.equals(postReloadGuardKey) || now - postReloadGuardWindowStartMs > POST_RELOAD_GUARD_WINDOW_MS) {
            postReloadGuardKey = key;
            postReloadGuardWindowStartMs = now;
            postReloadGuardCount = 0;
        }

        postReloadGuardCount++;
        if (postReloadGuardCount > POST_RELOAD_GUARD_MAX_COUNT) {
            postReloadGuardBlockUntilMs = now + POST_RELOAD_GUARD_BLOCK_MS;
            Log.w(TAG, "POST_RELOAD_GUARD: block enabled for " + POST_RELOAD_GUARD_BLOCK_MS
                    + "ms, key=" + key + ", count=" + postReloadGuardCount);
            return true;
        }
        return false;
    }

    private void resetPostReloadGuard(String reason) {
        if (postReloadGuardCount == 0 && postReloadGuardBlockUntilMs == 0L && postReloadGuardKey.isEmpty()) {
            return;
        }
        postReloadGuardWindowStartMs = 0L;
        postReloadGuardCount = 0;
        postReloadGuardBlockUntilMs = 0L;
        postReloadGuardKey = "";
        Log.d(TAG, "POST_RELOAD_GUARD: reset, reason=" + reason);
    }

    /**
     * Перезапускает загрузку main-frame при сетевом timeout (ERR_CONNECTION_TIMED_OUT) с anti-loop guard.
     *
     * Зависимости:
     * - callback-и `WebViewClient.onReceivedError(...)` (API23+ и legacy),
     * - `lastMainFrameTimeoutRetryUrl/AtMs` для дедупликации повторов,
     * - URL-хост Neverlands (`neverlands.ru`) как единственная зона автоповтора.
     */
    private void maybeRetryMainFrameTimeout(WebView view,
                                            String failingUrl,
                                            int errorCode,
                                            String description,
                                            boolean isMainFrame) {
        if (view == null || !isMainFrame) {
            return;
        }
        if (failingUrl == null || failingUrl.isEmpty()) {
            return;
        }
        if (!isNeverlandsHostUrl(failingUrl)) {
            return;
        }
        if (!isTimeoutError(errorCode, description)) {
            return;
        }

        String timeoutContext = buildMainFrameTimeoutContext();
        long now = System.currentTimeMillis();
        if (failingUrl.equals(lastMainFrameTimeoutRetryUrl)
                && (now - lastMainFrameTimeoutRetryAtMs) < MAINFRAME_TIMEOUT_RETRY_DEDUP_MS) {
            Log.w(TAG, "onReceivedError: timeout retry skipped by dedup, url=" + failingUrl
                    + ", context={" + timeoutContext + "}");
            return;
        }

        lastMainFrameTimeoutRetryUrl = failingUrl;
        lastMainFrameTimeoutRetryAtMs = now;
        Log.w(TAG, "onReceivedError: timeout on main frame, retry in "
                + MAINFRAME_TIMEOUT_RETRY_DELAY_MS + "ms, url=" + failingUrl
                + ", context={" + timeoutContext + "}");

        view.postDelayed(() -> {
            if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                return;
            }
            view.loadUrl(failingUrl);
        }, MAINFRAME_TIMEOUT_RETRY_DELAY_MS);
    }

    /**
     * Формирует диагностический контекст main-frame timeout для logcat.
     *
     * Назначение:
     * - быстро понять, в каком режиме был клиент при `ERR_CONNECTION_TIMED_OUT`;
     * - разделить сценарии "бой / рыбалка / обычная навигация" без ручной реконструкции.
     *
     * Зависимости:
     * - AutoFunctionsManager: флаги авто-режимов;
     * - AppVars.ContentMainPhp/FightLink/LastFightPulseAtMs: боевой контекст;
     * - AppVars.NeverTimer: серверный cooldown (часто связан с авто-рыбалкой);
     * - текущий URL WebView + `AppVars.url_main_top`: куда фактически смотрел верхний фрейм.
     */
    private String buildMainFrameTimeoutContext() {
        boolean autoFightEnabled = false;
        boolean autoFishEnabled = false;
        try {
            AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(this);
            autoFightEnabled = autoFunctionsManager.isAutoFightEnabled();
            autoFishEnabled = autoFunctionsManager.isAutoFishEnabled();
        } catch (Exception ignored) {
            // Не ломаем обработку timeout из-за несущественной ошибки диагностики.
        }

        String contentMainPhp = AppVars.ContentMainPhp;
        boolean hasFightHtml = contentMainPhp != null
                && (contentMainPhp.contains("var fight_ty") || contentMainPhp.contains("magic_slots();"));
        String fightLink = AppVars.FightLink;
        boolean hasFightLink = fightLink != null && fightLink.contains("get_id=61&act=");
        boolean recentFightPulse = AppVars.LastFightPulseAtMs > 0L
                && (System.currentTimeMillis() - AppVars.LastFightPulseAtMs) <= 15000L;

        String currentMainUrl = "";
        try {
            if (binding != null && binding.appBarMain != null && binding.appBarMain.contentMain != null
                    && binding.appBarMain.contentMain.webView != null
                    && binding.appBarMain.contentMain.webView.getUrl() != null) {
                currentMainUrl = binding.appBarMain.contentMain.webView.getUrl();
            }
        } catch (Exception ignored) {
            currentMainUrl = "";
        }

        long now = System.currentTimeMillis();
        long neverTimerDeltaMs = AppVars.NeverTimer - now;

        return "autoFight=" + autoFightEnabled
                + ", autoFish=" + autoFishEnabled
                + ", currentMainUrl=" + currentMainUrl
                + ", appVarsMainTopUrl=" + AppVars.url_main_top
                + ", hasFightHtml=" + hasFightHtml
                + ", hasFightLink=" + hasFightLink
                + ", recentFightPulse=" + recentFightPulse
                + ", neverTimerDeltaMs=" + neverTimerDeltaMs
                + ", captchaVisible=" + AppVars.IsFightCaptchaDialogVisible;
    }

    /**
     * Проверяет, относится ли URL к игровому хосту Neverlands.
     */
    private boolean isNeverlandsHostUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri != null && uri.getHost() != null
                    ? uri.getHost().toLowerCase(Locale.ROOT)
                    : "";
            return "neverlands.ru".equals(host) || host.endsWith(".neverlands.ru");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Унифицированная проверка timeout-ошибки для разных Android/WebView callback-форматов.
     */
    private boolean isTimeoutError(int errorCode, String description) {
        if (errorCode == WebViewClient.ERROR_TIMEOUT) {
            return true;
        }
        if (description == null || description.isEmpty()) {
            return false;
        }
        String lower = description.toLowerCase(Locale.ROOT);
        return lower.contains("err_connection_timed_out")
                || lower.contains("connection timed out")
                || lower.contains("timed out");
    }

    private WebView createChatPopupWebView() {
        WebView popup = new WebView(this);
        setupWebView(popup, new CustomWebViewClient());
        popup.setVisibility(View.GONE);
        if (binding != null && binding.getRoot() instanceof ViewGroup) {
            ((ViewGroup) binding.getRoot()).addView(popup, new ViewGroup.LayoutParams(1, 1));
        }
        chatPopupWebViews.add(popup);
        return popup;
    }
}
