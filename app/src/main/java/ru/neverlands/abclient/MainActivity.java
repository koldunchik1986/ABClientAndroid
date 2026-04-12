package ru.neverlands.abclient;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
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
import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.manager.AppTimerManager;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.databinding.ActivityMainBinding;
import ru.neverlands.abclient.manager.TabManager;
import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.network.NetworkClient;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.utils.LogcatFileRecorder;
import ru.neverlands.abclient.utils.RuntimeNetTrace;
import ru.neverlands.abclient.utils.SessionManager;
import ru.neverlands.abclient.utils.Russian;
import ru.neverlands.abclient.webview.WebViewRequestInterceptor;
import ru.neverlands.abclient.service.AutoModeForegroundService;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Observer;
import ru.neverlands.abclient.ui.viewmodel.FightViewModel;
import ru.neverlands.abclient.ui.QuickButtonsPanel;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
    private static final String BUILD_MARKER = "2026-02-27_01-34";
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1002;
    private static final int CHAT_REFRESH_DEFAULT_SECONDS = 12;
    private static final int CHAT_REFRESH_AUTO_BOSS_SECONDS = 3;
    private static final int CHAT_REFRESH_INITIAL_DELAY_MS = 1000;
    private static final long CHAT_POLL_FAILURE_RETRY_BASE_MS = 1200L;
    private static final long CHAT_POLL_FAILURE_RETRY_MAX_MS = 4000L;
    private static final long CHAT_POLL_FAILURE_RETRY_BOSS_MS = 350L;
    private static final long CHAT_POLL_FAILURE_DEDUP_MS = 600L;
    private static final long ROOM_USERS_SUPPRESS_AFTER_CHAT_FAIL_MS = 1800L;
    private static final long CHAT_ROOM_COLLISION_GUARD_MS = 700L;
    private static final int AUTO_SUBMIT_RETRY_DELAY_MS = 180;
    private static final int AUTO_SUBMIT_MAX_RETRY_COUNT = 3;
    private static final long CAPTCHA_IMAGE_STABILIZE_DELAY_MS = 180L;
    private static final long CAPTCHA_NETWORK_FALLBACK_DELAY_MS = 900L;
    private static final int CAPTCHA_NOTIFICATION_ID = 6107;
    private static final String CAPTCHA_NOTIFICATION_CHANNEL_ID = "captcha_alerts";
    private static final long POST_RELOAD_GUARD_WINDOW_MS = 5000L;
    private static final Pattern COMPASS_CELL_URL_PATTERN =
            Pattern.compile("^https?://(\\d{1,4}-\\d{1,5})(?:[/?#].*)?$", Pattern.CASE_INSENSITIVE);
    private static final int POST_RELOAD_GUARD_MAX_COUNT = 4;
    private static final long POST_RELOAD_GUARD_BLOCK_MS = 12000L;
    private static final long MAINFRAME_TIMEOUT_RETRY_DELAY_MS = 1500L;
    private static final long MAINFRAME_TIMEOUT_RETRY_DEDUP_MS = 12000L;
    private static final long AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS = 1200L;
    private static final long AUTO_TURN_FIRST_FRAME_RENDER_GUARD_MS = 420L;
    private static final long AUTO_TURN_MANUAL_NAV_SUPPRESS_MS = 4500L;
    private static final int AUTO_TURN_SERVER_PROBE_TIMEOUT_MS = 12000;
    private static final long SERVER_TIMER_TICK_MARGIN_MS = 300L;
    private static final long SERVER_TIMER_TICK_DEDUP_MS = 4000L;
    private static final long NAV_TICK_NETWORK_BACKOFF_MS = 8000L;
    private static final long NAV_TICK_ERROR_BURST_WINDOW_MS = 6000L;
    private static final int NAV_TICK_ERROR_BURST_THRESHOLD = 2;
    private static final String LOGOUT_URL = "http://neverlands.ru/exit.php";
    private static final String LOGOUT_REFERER = "http://neverlands.ru/game.php";
    private static final int LOGOUT_HTTP_TIMEOUT_MS = 10000;
    public ActivityMainBinding binding;
    private Timer timer;
    private boolean isExiting = false;
    // DEPRECATED: RoomManager.startTracing() no longer needed after HTML injection fix
    // private boolean isRoomManagerStarted = false;
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
    // Временное окно подавления `ch.php?lo=1` после деградации `ch.php?show=1`.
    // Нужен, чтобы не "дожимать" сервер room-list запросами в момент, когда chat-poll
    // уже вернул 535/546 или пустое тело.
    private long roomUsersRefreshSuppressedUntilMs = 0L;
    // Диагностические счетчики восстановления chat-poll.
    private long lastChatPollFailureAtMs = 0L;
    private int consecutiveChatPollFailures = 0;
    private Runnable chatPollRecoveryRunnable;
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
    /**
     * Текущее поле ввода кода в активном popup капчи.
     *
     * Зависимости:
     * - {@link #showCaptchaDialog(String, String)} назначает ссылку на актуальный EditText;
     * - {@link #updateFightCaptchaSubmitButtonState()} читает введённый код и управляет кнопкой "ОК";
     * - в onDismiss ссылка сбрасывается, чтобы не держать stale-ссылку на старый View.
     */
    private android.widget.EditText activeFightCaptchaInput;
    private boolean replacingFightCaptchaDialog = false;
    private boolean appBroadcastReceiverRegistered = false;
    private boolean screenStateReceiverRegistered = false;
    private long postReloadGuardWindowStartMs = 0L;
    private int postReloadGuardCount = 0;
    private long postReloadGuardBlockUntilMs = 0L;
    private String postReloadGuardKey = "";
    private long lastMainFrameTimeoutRetryAtMs = 0L;
    private String lastMainFrameTimeoutRetryUrl = "";
    private long lastServerTimerDrivenReloadAtMs = 0L;
    private long lastServerTimerDrivenReloadDueAtMs = Long.MIN_VALUE;
    private long navTickNetworkBackoffUntilMs = 0L;
    private long navTickErrorBurstWindowStartMs = 0L;
    private int navTickErrorBurstCount = 0;
    private boolean lastQuickPanelAutoMovingState = false;
    private long lastAutoBattleSubmitAtMs = 0L;
    private final Handler autoBattleDelayHandler = createMainHandler();
    private Runnable pendingAutoBattleSubmitRunnable;
    private String pendingAutoBattleSubmitPayload = "";
    private Observer<String> fightSubmitObserver;
    private final Object autoTurnServerProbeLock = new Object();
    private volatile boolean autoTurnServerProbeInFlight = false;
    private volatile long lastAutoTurnServerProbeAtMs = 0L;
    private volatile long autoTurnManualNavSuppressUntilMs = 0L;
    // true между onResume/onPause; используется для отключения server-probe в активном UI.
    private volatile boolean isActivityResumedState = false;
    private boolean suppressBackgroundLoopsForContacts = false;
    private boolean suppressChatRefreshOnceAfterContacts = false;
    private boolean suppressRoomRefreshOnceAfterContacts = false;
    private boolean shouldRestoreChatRefreshAfterContacts = false;
    private final ActivityResultLauncher<Intent> contactsActivityLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        suppressBackgroundLoopsForContacts = false;
                        Intent data = result.getData();
                        if (result.getResultCode() == RESULT_OK && data != null) {
                            String url = data.getStringExtra("open_pinfo_url");
                            String title = data.getStringExtra("open_pinfo_title");
                            if (url != null && tabManager != null) {
                                tabManager.openTab(url, title != null ? title : "PINFO");
                            }
                        }
                        if (shouldRestoreChatRefreshAfterContacts && chatRefreshRunnable == null) {
                            AppLog.d("contacts_nav", TAG, BG_TRACE_PREFIX + " contacts-return: restoring chat refresh");
                            startChatRefresh();
                        }
                        shouldRestoreChatRefreshAfterContacts = false;
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
        if (suppressRoomRefreshOnceAfterContacts) {
            suppressRoomRefreshOnceAfterContacts = false;
            AppLog.d("contacts_nav", TAG, BG_TRACE_PREFIX + " requestRoomUsersRefreshSoon: skipped once after contacts");
            return;
        }

        long now = System.currentTimeMillis();
        if (now < roomUsersRefreshSuppressedUntilMs) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestRoomUsersRefreshSoon: suppressed by chat-recovery, waitMs="
                    + (roomUsersRefreshSuppressedUntilMs - now));
            return;
        }
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
        boolean deviceLocked = false;
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
            deviceLocked = isDeviceLocked();
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " " + stage + ": failed to read power state", e);
        }

        Log.d(TAG, BG_TRACE_PREFIX + " " + stage
                + ": interactive=" + isInteractive
                + ", deviceLocked=" + deviceLocked
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
        requestAutoTurnInternal(false);
    }

    /**
     * Фоновый авто-ход с server-fallback.
     *
     * Сценарий:
     * - foreground путь остается прежним: берем HTML из main WebView;
     * - если маркеров боя нет (типично для background-throttling Chromium),
     *   вызывается server probe и бой парсится по ответу сервера.
     *
     * Зависимости:
     * - `requestAutoTurnInternal(...)`: общая оркестрация цепочки;
     * - `requestAutoTurnFromServerProbe(...)`: сетевой fallback;
     * - `FightViewModel.autoTurnOnce(...)`: фактический разбор/формирование хода.
     */
    /**
     * Запуск одного шага авто-боя в «фонобезопасном» режиме.
     *
     * Правила выполнения:
     * - используется тот же оркестратор, что и в обычном авто-ходе, но с разрешением server-probe;
     * - если WebView не отдал актуальный бой, разрешается прямой HTTP-запрос к серверу для получения кадра боя;
     * - логика отправки удара остается централизованной в FightViewModel.
     *
     * Зависимости:
     * - {@link #requestAutoTurnInternal(boolean)} — основной сценарий с fallback-ветками;
     * - {@link #requestAutoTurnFromServerProbe(String)} — сетевое восстановление бой-контекста;
     * - {@link FightViewModel#autoTurnOnce(String)} — формирование/отправка действия.
     */
    public void requestAutoTurnBackgroundAware() {
        requestAutoTurnInternal(true);
    }

    /**
     * Немедленный запрос хода при анонсе нового боя (event-driven).
     * Используется FightViewModel для избежания 24+ секундной задержки polling-цикла.
     *
     * Вызывается только когда заведомо известно:
     * - Бой объявлен (новый LogBoi получен)
     * - Автобой активен (проверено в FightViewModel)
     *
     * Безопасность:
     * - VCode получится из SessionManager (boевой HTML только что парсился)
     * - requestAutoTurnInternal(false) как fallback, если MainActivity недоступна
     */
    public void requestImmediateAutoTurnOnFightAnnounce() {
        if (AppVars.IsFightCaptchaDialogVisible) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestImmediateAutoTurnOnFightAnnounce: skip, captcha dialog visible");
            return;
        }

        AppLog.d(TAG, BG_TRACE_PREFIX + " requestImmediateAutoTurnOnFightAnnounce: triggered by fight announcement");

        // Используем фоновый механизм, т.к. в момент анонсации UI может быть неинтерактивным
        requestAutoTurnBackgroundAware();
    }

    /**
     * Принимает метаданные ответа `ch.php?show=1` из перехватчика WebView.
     *
     * Зависимости:
     * - источник вызова: `WebViewRequestInterceptor` (каждый `show=1` ответ);
     * - управляет временным подавлением `requestRoomUsersRefreshSoon()` и планирует
     *   ускоренный recovery-ретрай chat poll без перезапуска общего periodic runnable.
     *
     * Назначение:
     * - уменьшить вероятность пропуска системных сообщений (например, событий Босса),
     *   когда один poll-тик падает с HTTP 546/535 или пустым body.
     */
    public void onChatPollResponseMeta(int httpCode, int rawBytes, boolean hasAddMsg, boolean hasSetLmid, String url) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> onChatPollResponseMeta(httpCode, rawBytes, hasAddMsg, hasSetLmid, url));
            return;
        }

        boolean pollFailed = httpCode >= 535 || rawBytes <= 0;
        long now = System.currentTimeMillis();

        if (!pollFailed) {
            if (consecutiveChatPollFailures > 0) {
                Log.d(TAG, BG_TRACE_PREFIX + " chat-poll recovered: code=" + httpCode
                        + ", bytes=" + rawBytes
                        + ", hasAdd=" + hasAddMsg
                        + ", hasSetLmid=" + hasSetLmid
                        + ", failures=" + consecutiveChatPollFailures);
                FileLogger.trace("chat_poll", "RECOVERED code=" + httpCode
                        + ", bytes=" + rawBytes
                        + ", failures=" + consecutiveChatPollFailures);
            }
            consecutiveChatPollFailures = 0;
            lastChatPollFailureAtMs = 0L;
            roomUsersRefreshSuppressedUntilMs = 0L;
            if (chatPollRecoveryRunnable != null) {
                chatRefreshHandler.removeCallbacks(chatPollRecoveryRunnable);
                chatPollRecoveryRunnable = null;
            }
            return;
        }

        if (now - lastChatPollFailureAtMs < CHAT_POLL_FAILURE_DEDUP_MS) {
            return;
        }
        lastChatPollFailureAtMs = now;
        consecutiveChatPollFailures = Math.min(consecutiveChatPollFailures + 1, 10);

        long retryDelayMs = Math.min(
                CHAT_POLL_FAILURE_RETRY_MAX_MS,
                CHAT_POLL_FAILURE_RETRY_BASE_MS + (consecutiveChatPollFailures - 1) * 400L
        );
        boolean autoBossEnabled = false;
        try {
            autoBossEnabled = AutoFunctionsManager.getInstance(this).isAutoBossEnabled();
            if (autoBossEnabled) {
                retryDelayMs = Math.min(retryDelayMs, CHAT_POLL_FAILURE_RETRY_BOSS_MS);
            }
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " chat-poll degraded: failed to read autoBoss flag", e);
        }
        roomUsersRefreshSuppressedUntilMs = Math.max(
                roomUsersRefreshSuppressedUntilMs,
                now + ROOM_USERS_SUPPRESS_AFTER_CHAT_FAIL_MS
        );

        Log.w(TAG, BG_TRACE_PREFIX + " chat-poll degraded: code=" + httpCode
                + ", bytes=" + rawBytes
                + ", hasAdd=" + hasAddMsg
                + ", hasSetLmid=" + hasSetLmid
                + ", failures=" + consecutiveChatPollFailures
                + ", retryInMs=" + retryDelayMs
                + ", autoBoss=" + autoBossEnabled
                + ", suppressRoomUntil=" + roomUsersRefreshSuppressedUntilMs
                + ", url=" + url);
        FileLogger.warn("chat_poll", "DEGRADED code=" + httpCode
                + ", bytes=" + rawBytes
                + ", hasAdd=" + hasAddMsg
                + ", hasSetLmid=" + hasSetLmid
                + ", failures=" + consecutiveChatPollFailures
                + ", retryInMs=" + retryDelayMs
                + ", autoBoss=" + autoBossEnabled
                + ", suppressRoomUntil=" + roomUsersRefreshSuppressedUntilMs
                + ", url=" + url);

        if (chatPollRecoveryRunnable != null) {
            chatRefreshHandler.removeCallbacks(chatPollRecoveryRunnable);
        }
        chatPollRecoveryRunnable = () -> {
            if (isFinishing() || isDestroyed() || !isChatRefreshEnabled()) {
                return;
            }
            Log.d(TAG, BG_TRACE_PREFIX + " chat-poll recovery retry: manual tick");
            requestChatRefresh(false);
        };
        chatRefreshHandler.postDelayed(chatPollRecoveryRunnable, retryDelayMs);
    }

    /**
     * Внутренний оркестратор одного шага авто-удара.
     *
     * Сценарий выполнения:
     * - читает HTML из текущего main WebView;
     * - если в нём есть маркеры боя — передаёт HTML в `fightViewModel.autoTurnOnce(...)`;
     * - если маркеров нет, пробует использовать `AppVars.ContentMainPhp` как кэш последнего боевого кадра;
     * - если кэш пустой/устаревший и разрешён fallback, запускает server-probe
     *   (`requestAutoTurnFromServerProbe(...)`) для получения актуального бой-HTML напрямую с сервера.
     *
     * Защитные условия:
     * - не выполняется, пока открыт popup боевой капчи (`IsFightCaptchaDialogVisible`);
     * - в foreground-UI может кратко отложить самый первый удар (`shouldDeferAutoTurnForFirstFrameRender()`),
     *   чтобы кадр боя успел отрисоваться до submit.
     *
     * Зависимости:
     * - `fightViewModel.autoTurnOnce(...)` — фактический парсер/submit шага;
     * - `hasFightMarkers(...)`, `isActiveFightContext(...)` — валидация контекста кадра;
     * - `AppVars.ContentMainPhp` — кэш последнего сервера бой-HTML;
     * - `requestAutoTurnFromServerProbe(...)` — fallback-источник HTML в background throttling-сценарии.
     */
    /**
     * Оркестратор одного тика авто-боя.
     *
     * Правила и защита от зацикливания:
     * - при активном popup капчи бой не обрабатывается;
     * - при свежем announce в foreground первый тик может быть отложен для корректной отрисовки кадра;
     * - если в текущем HTML есть маркеры боя, но контекст неактивен (stale `fight_ty`, `IsBoi=false`),
     *   выполняется восстановление: активный cache -> server-probe;
     * - если уже есть pending-ссылка завершения боя (`act=7`), лишний server-probe не запускается;
     * - при полном отсутствии маркеров в текущем HTML используется cache, затем server-probe.
     *
     * Зависимости:
     * - {@link #hasFightMarkers(String)}, {@link #isActiveFightContext(String)} — валидация бой-контекста;
     * - {@link #hasPendingAct7FightLink(String)} — защита от лишнего probe во время завершения боя;
     * - {@link #requestAutoTurnFromServerProbe(String)} — восстановление бой-HTML;
     * - {@link FightViewModel#autoTurnOnce(String)} — отправка шага.
     */
    private void requestAutoTurnInternal(boolean allowServerProbeFallback) {
        if (AppVars.IsFightCaptchaDialogVisible) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: skip, captcha dialog visible");
            return;
        }
        if (shouldDeferAutoTurnForFirstFrameRender()) {
            return;
        }
        // Когда приложение в фоне/экран заблокирован — WebView.evaluateJavascript может не сработать
        // (Android приостанавливает рендеринг WebView). Сразу идём через HTTP server probe.
        if (allowServerProbeFallback && !isActivityResumedState) {
            String cachedFightHtml = AppVars.ContentMainPhp;
            boolean cachedActive = hasFightMarkers(cachedFightHtml) && isActiveFightContext(cachedFightHtml);
            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: app backgrounded, skip WebView evaluateJavascript"
                    + ", cachedActive=" + cachedActive);
            if (cachedActive) {
                // Есть актуальный кэш боя — используем как основу + параллельно обновляем через HTTP
                fightViewModel.autoTurnOnce(cachedFightHtml);
                long sinceLastProbeMs = System.currentTimeMillis() - lastAutoTurnServerProbeAtMs;
                if (sinceLastProbeMs >= AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS) {
                    requestAutoTurnFromServerProbe("bg_cached_active_keepalive");
                }
            } else {
                // Нет актуального кэша — HTTP probe как единственный источник
                requestAutoTurnFromServerProbe("bg_no_webview");
            }
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
                        
                        // Проверка на race condition при multi-enemy fight (09:55 issue):
                        // Если HTML слишком мало (<1000 bytes), это означает что WebView еще loading
                        // Добавляем задержку вместо skip чтобы дать WebView время завершить page load
                        if (unquoted != null && unquoted.length() < 1000 && !hasFightMarkers(unquoted)) {
                            String msg = "[FIGHT_RACE_CONDITION] html size too small (WebView loading), size=" + unquoted.length() 
                                    + ". Deferring turn check 200ms";
                            Log.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: " + msg);
                            ru.neverlands.abclient.utils.FileLogger.trace("fight_auto", msg);
                            // Откладываем проверку на 200ms чтобы дать WebView время завершить page load
                            new Handler(Looper.getMainLooper()).postDelayed(this::requestAutoTurn, 200);
                            return;
                        }
                        
                        String autoTurnHtml = unquoted;
                        if (hasFightMarkers(unquoted)) {
                            if (allowServerProbeFallback) {
                                boolean currentActiveFight = isActiveFightContext(unquoted);
                                if (!currentActiveFight) {
                                    Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: current html has stale fight markers, inactive context"
                                            + ", fightLink=" + AppVars.FightLink);

                                    String cachedFightHtml = AppVars.ContentMainPhp;
                                    boolean cachedHasMarkers = hasFightMarkers(cachedFightHtml);
                                    boolean cachedActiveFight = cachedHasMarkers && isActiveFightContext(cachedFightHtml);
                                    if (cachedActiveFight) {
                                        autoTurnHtml = cachedFightHtml;
                                        Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: fallback to cached active fight html after inactive current html, len="
                                                + cachedFightHtml.length());
                                        if (!hasPendingAct7FightLink(AppVars.FightLink)) {
                                            long sinceLastProbeMs = System.currentTimeMillis() - lastAutoTurnServerProbeAtMs;
                                            if (sinceLastProbeMs >= AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS) {
                                                requestAutoTurnFromServerProbe("cached_active_fight_html_keepalive_after_inactive_current");
                                            }
                                        }
                                    } else {
                                        if (cachedHasMarkers) {
                                            AppVars.ContentMainPhp = "";
                                            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: drop stale cached fight html after inactive current html");
                                        }

                                        if (!hasPendingAct7FightLink(AppVars.FightLink)) {
                                            boolean probeAllowedByUiState = isAutoTurnServerProbeAllowedNow();
                                            if (!probeAllowedByUiState) {
                                                Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: forcing server probe after inactive current fight html");
                                            }
                                            requestAutoTurnFromServerProbe("current_fight_html_inactive");
                                        } else {
                                            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: skip server probe, pending act=7 finish link present");
                                        }
                                    }
                                }
                            }
                        } else {
                            String cachedFightHtml = AppVars.ContentMainPhp;
                            if (hasFightMarkers(cachedFightHtml)) {
                                if (isActiveFightContext(cachedFightHtml)) {
                                    autoTurnHtml = cachedFightHtml;
                                    Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: fallback to cached active fight html, len="
                                            + cachedFightHtml.length());
                                    if (allowServerProbeFallback && !hasPendingAct7FightLink(AppVars.FightLink)) {
                                        long sinceLastProbeMs = System.currentTimeMillis() - lastAutoTurnServerProbeAtMs;
                                        if (sinceLastProbeMs >= AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS) {
                                            requestAutoTurnFromServerProbe("cached_active_fight_html_keepalive");
                                        }
                                    }
                                } else {
                                    Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: cached fight html is stale (inactive), drop and probe");
                                    AppVars.ContentMainPhp = "";
                                    if (allowServerProbeFallback) {
                                        boolean probeAllowedByUiState = isAutoTurnServerProbeAllowedNow();
                                        if (!probeAllowedByUiState) {
                                            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: forcing server probe after stale cached fight html");
                                        }
                                        requestAutoTurnFromServerProbe("cached_fight_html_inactive");
                                    }
                                }
                            } else {
                                Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: no fight markers in current/cached html");
                                if (allowServerProbeFallback) {
                                    boolean probeAllowedByUiState = isAutoTurnServerProbeAllowedNow();
                                    if (!probeAllowedByUiState) {
                                        Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: forcing server probe from background-aware path");
                                    }
                                    requestAutoTurnFromServerProbe("no_fight_markers_current_and_cached");
                                }
                            }
                        }
                        fightViewModel.autoTurnOnce(autoTurnHtml);
                    } else {
                        Log.d(TAG, "requestAutoTurn: html is null");
                        String cachedFightHtml = AppVars.ContentMainPhp;
                        if (hasFightMarkers(cachedFightHtml)) {
                            if (isActiveFightContext(cachedFightHtml)) {
                                Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: null html, fallback to cached active fight html, len="
                                        + cachedFightHtml.length());
                                fightViewModel.autoTurnOnce(cachedFightHtml);
                                if (allowServerProbeFallback && !hasPendingAct7FightLink(AppVars.FightLink)) {
                                    long sinceLastProbeMs = System.currentTimeMillis() - lastAutoTurnServerProbeAtMs;
                                    if (sinceLastProbeMs >= AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS) {
                                        requestAutoTurnFromServerProbe("null_html_cached_active_fight_html_keepalive");
                                    }
                                }
                            } else {
                                Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: null html with stale cached fight html, drop and probe");
                                AppVars.ContentMainPhp = "";
                                if (allowServerProbeFallback) {
                                    boolean probeAllowedByUiState = isAutoTurnServerProbeAllowedNow();
                                    if (!probeAllowedByUiState) {
                                        Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: forcing null-html server probe after stale cached fight html");
                                    }
                                    requestAutoTurnFromServerProbe("null_html_stale_cached_fight_html");
                                }
                            }
                        } else {
                            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: html is null and cached html has no fight markers");
                            if (allowServerProbeFallback) {
                                boolean probeAllowedByUiState = isAutoTurnServerProbeAllowedNow();
                                if (!probeAllowedByUiState) {
                                    Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: forcing null-html server probe from background-aware path");
                                }
                                requestAutoTurnFromServerProbe("null_html_and_no_cached_markers");
                            }
                        }
                    }
                });
    }

    /**
     * Защищает первый боевой кадр от "слишком раннего" авто-удара в активном UI.
     *
     * Проблема:
     * - при старте нового боя (особенно 1 противник с убийством за 1 ход) авто-цикл мог отправить ход
     *   в первые десятки миллисекунд после announce, пока fight-frame ещё не успел визуально отрисоваться.
     *
     * Решение:
     * - если бой только что анонсирован и UI вероятно в foreground, даём короткое окно на рендер первого кадра;
     * - в фоне/на lockscreen задержка не применяется.
     */
    private boolean shouldDeferAutoTurnForFirstFrameRender() {
        boolean autoFightRuntimeEnabled = AppVars.Autoboi == ru.neverlands.abclient.model.AutoboiState.AutoboiOn
                || (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi);
        if (!autoFightRuntimeEnabled) {
            return false;
        }
        if (!isUiForegroundLikely()) {
            return false;
        }
        long announceAtMs = AppVars.LastFightAnnounceAtMs;
        if (announceAtMs <= 0L) {
            return false;
        }
        long sinceAnnounceMs = System.currentTimeMillis() - announceAtMs;
        if (sinceAnnounceMs < 0L || sinceAnnounceMs >= AUTO_TURN_FIRST_FRAME_RENDER_GUARD_MS) {
            return false;
        }
        Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: defer first turn for frame render, remainingMs="
                + (AUTO_TURN_FIRST_FRAME_RENDER_GUARD_MS - sinceAnnounceMs)
                + ", sinceAnnounceMs=" + sinceAnnounceMs);
        return true;
    }

    /**
     * Разрешает server probe только в background-контуре (пользователь не в активном UI).
     *
     * Зависимости:
     * - `isActivityResumedState` (lifecycle onResume/onPause),
     * - `PowerManager.isInteractive()` (доп. проверка lockscreen/погашенного экрана).
     */
    private boolean isAutoTurnServerProbeAllowedNow() {
        if (!isActivityResumedState) {
            return true;
        }
        if (isDeviceLocked()) {
            return true;
        }
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                return !powerManager.isInteractive();
            }
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " isAutoTurnServerProbeAllowedNow: fallback by resumed-state", e);
        }
        return false;
    }

    /**
     * Выполняет server probe боя с анти-спам guard:
     * - троттлит частоту probe,
     * - не запускает параллельные probe,
     * - при валидном fight HTML передает его в `FightViewModel.autoTurnOnce(...)`.
     *
     * Зависимости:
     * - `AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS`, `autoTurnServerProbeInFlight`;
     * - `loadFightProbeHtmlViaHttp()` как источник серверного HTML;
     * - `hasFightMarkers(...)` и `AppVars.ContentMainPhp` для синхронизации контекста.
     */
    /**
     * Проверяет, что URL относится к ручной навигации `go=inf` без внутренних служебных маркеров.
     *
     * Правило:
     * - ручные переходы пользователя временно подавляют server-probe,
     *   чтобы авто-бой не перебивал явную навигацию.
     *
     * Зависимости:
     * - используется в логике подавления probe через {@link #suppressAutoTurnServerProbeForManualNavigation(String)}.
     */
    private static boolean isManualMainNavigationUrl(String lowerUrl) {
        if (lowerUrl == null || lowerUrl.isEmpty()) {
            return false;
        }
        if (!lowerUrl.contains("main.php")) {
            return false;
        }
        if (lowerUrl.contains("ab_bg_probe=1")) {
            return false;
        }
        if (lowerUrl.contains("ab_") || lowerUrl.contains("af_")) {
            return false;
        }
        if (lowerUrl.contains("post_id=7")) {
            return false;
        }
        if (lowerUrl.contains("get_id=61") && lowerUrl.contains("act=7")) {
            return false;
        }
        return lowerUrl.contains("get_id=")
                || lowerUrl.contains("?im=")
                || lowerUrl.contains("&im=")
                || lowerUrl.contains("?wca=")
                || lowerUrl.contains("&wca=")
                || lowerUrl.contains("?wsi=")
                || lowerUrl.contains("&wsi=")
                || lowerUrl.contains("?wfo=")
                || lowerUrl.contains("&wfo=")
                || lowerUrl.contains("?go=inv")
                || lowerUrl.contains("&go=inv")
                || lowerUrl.contains("?go=inf")
                || lowerUrl.contains("&go=inf")
                || lowerUrl.contains("?go=ret")
                || lowerUrl.contains("&go=ret");
    }

    /**
     * Извлекает номер клетки из "кликабельной" ссылки компаса.
     *
     * Поддерживаемые форматы:
     * - `abmove://3-378` (основной формат для новых сообщений),
     * - `abcell://3-378` (совместимость),
     * - `http://3-378` / `https://3-378` (совместимость со старыми сообщениями).
     */
    @NonNull
    private String extractCompassCellRegNumFromUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        try {
            Uri uri = Uri.parse(trimmed);
            String scheme = uri != null ? uri.getScheme() : null;
            if ("abmove".equalsIgnoreCase(scheme) || "abcell".equalsIgnoreCase(scheme)) {
                String host = uri.getHost();
                if (host != null && !host.trim().isEmpty()) {
                    return normalizeCompassCellRegNum(host);
                }
                String path = uri.getPath();
                if (path != null && !path.trim().isEmpty()) {
                    return normalizeCompassCellRegNum(path.replace("/", ""));
                }
            }
        } catch (Exception ignored) {
        }

        Matcher matcher = COMPASS_CELL_URL_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return normalizeCompassCellRegNum(matcher.group(1));
        }
        return "";
    }

    @NonNull
    private String normalizeCompassCellRegNum(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replace('\u00A0', ' ');
        if (normalized.matches("\\d{1,4}-\\d{1,5}")) {
            return normalized;
        }
        return "";
    }

    /**
     * Включает временное окно подавления server-probe после ручной навигации.
     *
     * Правила:
     * - окно подавления задается константой `AUTO_TURN_MANUAL_NAV_SUPPRESS_MS`;
     * - повторный вызов продлевает suppress-window только вперед.
     *
     * Зависимости:
     * - состояние хранится в `autoTurnManualNavSuppressUntilMs`;
     * - учитывается в {@link #requestAutoTurnFromServerProbe(String)}.
     */
    private void suppressAutoTurnServerProbeForManualNavigation(String url) {
        long now = System.currentTimeMillis();
        long suppressUntil = now + AUTO_TURN_MANUAL_NAV_SUPPRESS_MS;
        if (suppressUntil > autoTurnManualNavSuppressUntilMs) {
            autoTurnManualNavSuppressUntilMs = suppressUntil;
        }
        Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: suppress server probe after manual main navigation"
                + ", suppressMs=" + AUTO_TURN_MANUAL_NAV_SUPPRESS_MS
                + ", url=" + url);
    }

    /**
     * Проверяет, что main WebView сейчас находится на «ручной» странице интерфейса.
     *
     * Назначение:
     * - использовать существующий классификатор {@link #isManualMainNavigationUrl(String)}
     *   не только в shouldOverrideUrlLoading, но и в защите фонового auto-turn probe;
     * - не давать idle-probe перебивать действия пользователя на страницах вида
     *   `main.php?useaction=...`, `main.php?wca=...`, `main.php?get_id=...`.
     *
     * Зависимости:
     * - `binding.appBarMain.contentMain.webView.getUrl()` как источник текущего URL верхнего фрейма;
     * - `isManualMainNavigationUrl(...)` как единая точка правил классификации ручной навигации.
     */
    private boolean isManualMainNavigationContextActive() {
        try {
            if (binding == null
                    || binding.appBarMain == null
                    || binding.appBarMain.contentMain == null
                    || binding.appBarMain.contentMain.webView == null) {
                return false;
            }
            String currentUrl = binding.appBarMain.contentMain.webView.getUrl();
            if (currentUrl == null || currentUrl.isEmpty()) {
                return false;
            }
            return isManualMainNavigationUrl(currentUrl.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: failed to inspect manual navigation context", e);
            return false;
        }
    }

    /**
     * Во время активной навигации Авто-Клада не запускаем idle server-probe авто-боя.
     *
     * Причина:
     * - в этом режиме `requestAutoTurn` не должен генерировать лишние `ab_bg_probe`,
     *   т.к. они создают конкурентную сетевую нагрузку (особенно через proxy) и мешают map-циклу.
     *
     * Ограничение:
     * - блокируем только idle-probe без признаков боя;
     * - если уже есть маркеры боя/finish-link, probe не подавляется.
     */
    private boolean shouldSkipAutoTurnServerProbeForMapAutomation() {
        if (!AppVars.AutoMoving) {
            return false;
        }
        if (AppVars.IsFightCaptchaDialogVisible) {
            return false;
        }
        if (hasFightMarkers(AppVars.ContentMainPhp)) {
            return false;
        }
        return !hasPendingAct7FightLink(AppVars.FightLink);
    }

    /**
     * Выполняет server-probe для получения актуального бой-HTML напрямую с сервера.
     *
     * Правила:
     * - соблюдает suppress-window ручной навигации;
     * - троттлит частоту запросов (`AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS`);
     * - предотвращает параллельные probe (`autoTurnServerProbeInFlight`);
     * - в UI-потоке передает результат в FightViewModel только при наличии маркеров боя.
     *
     * Зависимости:
     * - {@link #loadFightProbeHtmlViaHttp()} — источник HTML;
     * - {@link #hasFightMarkers(String)} — первичная валидация;
     * - {@link FightViewModel#autoTurnOnce(String)} — шаг авто-боя;
     * - `AppVars.ContentMainPhp` — cache последнего бой-кадра.
     */
    private void requestAutoTurnFromServerProbe(String reason) {
        long now = System.currentTimeMillis();
        if (shouldSkipAutoTurnServerProbeForMapAutomation()) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe suppressed during map automation, reason="
                    + reason + ", autoMoving=" + AppVars.AutoMoving + ", doSearchBox=" + AppVars.DoSearchBox);
            return;
        }
        if (!isAutoTurnServerProbeAllowedNow()
                && isManualMainNavigationContextActive()
                && !hasFightMarkers(AppVars.ContentMainPhp)
                && !hasPendingAct7FightLink(AppVars.FightLink)) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe suppressed in active UI manual context, reason="
                    + reason);
            return;
        }
        if (now < autoTurnManualNavSuppressUntilMs) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe suppressed by manual main navigation, reason="
                    + reason + ", remainingMs=" + (autoTurnManualNavSuppressUntilMs - now));
            return;
        }
        if (now - lastAutoTurnServerProbeAtMs < AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS) {
            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe throttled, reason=" + reason
                    + ", remainingMs=" + (AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS - (now - lastAutoTurnServerProbeAtMs)));
            return;
        }
        synchronized (autoTurnServerProbeLock) {
            if (autoTurnServerProbeInFlight) {
                Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe skipped, in-flight, reason=" + reason);
                return;
            }
            autoTurnServerProbeInFlight = true;
            lastAutoTurnServerProbeAtMs = now;
        }

        final String probeReason = reason;
        new Thread(() -> {
            try {
                FightProbeResult probeResult = loadFightProbeHtmlViaHttp();
                String probeHtml = probeResult.html;
                if (probeHtml == null || probeHtml.isEmpty()) {
                    Log.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe empty, reason=" + probeReason);
                    return;
                }
                runOnUiThread(() -> {
                    Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe htmlLen=" + probeHtml.length()
                            + ", hasFightMarkers=" + probeResult.hasFightMarkers
                            + ", probeUrl=" + probeResult.url
                            + ", reason=" + probeReason);
                    if (probeResult.hasFightMarkers) {
                        AppVars.ContentMainPhp = probeHtml;
                        fightViewModel.autoTurnOnce(probeHtml);
                    } else {
                        Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe has no fight markers, probeUrl="
                                + probeResult.url);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe failed, reason=" + probeReason, e);
            } finally {
                synchronized (autoTurnServerProbeLock) {
                    autoTurnServerProbeInFlight = false;
                }
            }
        }, "auto-turn-server-probe").start();
    }

    /**
     * Загружает `main.php?get_id=56&act=10&go=inf` напрямую по HTTP как server probe боя.
     *
     * Требования безопасности:
     * - при `strict proxy` и неактивном proxy runtime запрос блокируется (anti-leak реального IP);
     * - в запрос передаются browser UA и cookie текущей игровой сессии.
     *
     * Зависимости:
     * - `ProxyRuntimeManager.getActiveJavaProxyOrNull()` / `isStrictProxyRequiredForCurrentProfile()`;
     * - `CookieManager` + `CookiesManager.obtain(...)` для cookie-header;
     * - `Russian.getString(...)` для декодирования windows-1251 ответа сервера.
     */
    /**
     * Пробует получить бой-HTML по набору server-probe URL.
     *
     * Правила:
     * - выполняет несколько попыток с разными URL;
     * - сразу возвращает результат при первом HTML с маркерами боя;
     * - если маркеры не найдены, возвращает первый непустой HTML как диагностический fallback.
     *
     * Зависимости:
     * - {@link #loadFightProbeHtmlViaHttp(String)} — низкоуровневая загрузка;
     * - {@link #hasFightMarkers(String)} — оценка результата.
     */
    private FightProbeResult loadFightProbeHtmlViaHttp() {
        long nonce = System.currentTimeMillis();
        List<String> probeUrls = Arrays.asList(
                "http://neverlands.ru/main.php?ab_bg_probe=1&r=" + nonce,
                "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_bg_probe=1&r=" + nonce
        );

        String firstNonEmptyHtml = null;
        String firstNonEmptyUrl = "";
        for (String probeUrl : probeUrls) {
            String probeHtml = loadFightProbeHtmlViaHttp(probeUrl);
            boolean hasMarkers = hasFightMarkers(probeHtml);
            Log.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: probe attempt url=" + probeUrl
                    + ", htmlLen=" + (probeHtml == null ? 0 : probeHtml.length())
                    + ", hasFightMarkers=" + hasMarkers);

            if (probeHtml != null && !probeHtml.isEmpty() && firstNonEmptyHtml == null) {
                firstNonEmptyHtml = probeHtml;
                firstNonEmptyUrl = probeUrl;
            }
            if (hasMarkers) {
                return new FightProbeResult(probeUrl, probeHtml, true);
            }
        }

        if (firstNonEmptyHtml != null) {
            return new FightProbeResult(firstNonEmptyUrl, firstNonEmptyHtml, false);
        }
        return new FightProbeResult("", null, false);
    }

    /**
     * Низкоуровневая HTTP-загрузка HTML для server-probe.
     *
     * Правила:
     * - при strict-proxy и неактивном runtime-proxy запрос блокируется (anti-leak);
     * - используется браузерный User-Agent и cookie текущей игровой сессии;
     * - ответ декодируется в соответствии с серверной кодировкой.
     *
     * Зависимости:
     * - `ProxyRuntimeManager`, `CookiesManager`, `CookieManager`;
     * - `AppVars.BROWSER_USER_AGENT`;
     * - `Russian.getString(...)` для декодирования ответа.
     */
    private String loadFightProbeHtmlViaHttp(String probeUrl) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            URL url = new URL(probeUrl);
            java.net.Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                Log.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct fight probe");
                return null;
            }

            connection = activeProxy != null
                    ? (HttpURLConnection) url.openConnection(activeProxy)
                    : (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(AUTO_TURN_SERVER_PROBE_TIMEOUT_MS);
            connection.setReadTimeout(AUTO_TURN_SERVER_PROBE_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Referer", "http://neverlands.ru/main.php");
            connection.setRequestProperty("User-Agent", AppVars.BROWSER_USER_AGENT);

            String cookie = CookieManager.getInstance().getCookie(probeUrl);
            if (cookie == null || cookie.isEmpty()) {
                cookie = CookieManager.getInstance().getCookie("http://neverlands.ru/");
            }
            if ((cookie == null || cookie.isEmpty()) && url.getHost() != null) {
                cookie = CookiesManager.obtain(url.getHost());
            }
            if (cookie != null && !cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            } else {
                Log.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: fight probe cookie is empty");
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                Log.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: fight probe HTTP " + responseCode + ", probeUrl=" + probeUrl);
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
            return data.length > 0 ? Russian.getString(data) : null;
        } catch (Exception e) {
            Log.e(TAG, BG_TRACE_PREFIX + " requestAutoTurn: fight probe request failed: " + probeUrl, e);
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

    private static final class FightProbeResult {
        final String url;
        final String html;
        final boolean hasFightMarkers;

        FightProbeResult(String url, String html, boolean hasFightMarkers) {
            this.url = url == null ? "" : url;
            this.html = html;
            this.hasFightMarkers = hasFightMarkers;
        }
    }

    /**
     * Автовосстановление fight-frame, если авто-тик подряд получает небоевой HTML.
     *
     * Зависимости:
     * - `AppVars.Autoboi` / `Profile.LezDoAutoboi`: runtime-флаг активного авто-боя;
     * - `AppVars.VCode`: добавляется в URL восстановления, если доступен;
     * - `binding.appBarMain.contentMain.webView`: целевой WebView верхнего фрейма.
     */


    /**
     * Быстрая эвристика «это боевой HTML или нет».
     *
     * Зависимости:
     * - вызывается в recovery-логике авто-боя перед принудительной перезагрузкой fight-frame;
     * - опирается на серверные маркеры `var fight_ty` и `magic_slots()`,
     *   которые присутствуют в боевом кадре neverlands.
     *
     * @param html HTML верхнего фрейма.
     * @return {@code true}, если страница похожа на активный бой.
     */
    /**
     * Быстрая эвристика «похоже на бой».
     *
     * Правила:
     * - проверяются только серверные маркеры бой-кадра (`var fight_ty`, `magic_slots();`);
     * - метод не подтверждает активность боя, только факт «бой-подобного» HTML.
     *
     * Зависимости:
     * - используется в авто-ходе, server-probe и валидации fallback-контекста.
     */
    private boolean hasFightMarkers(String html) {
        return html != null && (html.contains("var fight_ty") || html.contains("magic_slots();"));
    }

    /**
     * Проверяет наличие pending-ссылки завершения боя (`act=7`) в `FightLink`.
     *
     * Правила:
     * - если ссылка завершения уже готова, лишний server-probe не запускается;
     * - проверка делается без изменения состояния.
     *
     * Зависимости:
     * - используется в {@link #requestAutoTurnInternal(boolean)} для anti-loop сценария.
     */
    private boolean hasPendingAct7FightLink(String fightLink) {
        if (fightLink == null || fightLink.isEmpty()) {
            return false;
        }
        String lower = fightLink.toLowerCase(Locale.ROOT);
        return lower.contains("get_id=61") && lower.contains("act=7");
    }

    /**
     * Определяет, что fight HTML действительно относится к активному бою, а не к stale-кадру после завершения.
     *
     * Нужен для фонового `requestAutoTurn(...)`: без этой проверки сервис мог зациклиться на старом `fight_ty`,
     * где маркеры боя есть, но `IsBoi=false` и реальный ход уже невозможен.
     */
    /**
     * Подтверждает, что HTML относится к активному бою, а не к stale-состоянию.
     *
     * Правила:
     * - сначала проверяются бой-маркеры;
     * - затем выполняется парсинг через `LezFight` и проверка `IsValid && IsBoi`;
     * - ошибки парсинга трактуются как неактивный контекст (safe fallback).
     *
     * Зависимости:
     * - {@link #hasFightMarkers(String)};
     * - {@link LezFight} как источник фактического состояния `IsBoi`.
     */
    private boolean isActiveFightContext(String html) {
        if (!hasFightMarkers(html)) {
            return false;
        }
        try {
            LezFight fight = new LezFight(html);
            return fight.IsValid && fight.IsBoi;
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " isActiveFightContext: parse failed, treat as inactive", e);
            return false;
        }
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
        // JS-submit с fallback:
        // 1) пытаемся отправить payload через скрытую POST-форму;
        // 2) если DOM не готов (часто в фоне/при перезагрузке кадра), возвращаем missing/error без падения;
        // 3) Java-слой повторяет попытку с задержкой, пока WebView восстановит submit-контур.
        String script = "(function(payload){"
                + "try{"
                + "var tryPayloadSubmit=function(raw){"
                + "if(!raw){return false;}"
                + "var ss=(''+raw).split('|');"
                + "if(ss.length<9){return false;}"
                + "if(!document||typeof document.createElement!=='function'){return false;}"
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
                + "var host=(document.body||document.documentElement||((document.getElementsByTagName&&document.getElementsByTagName('html'))?document.getElementsByTagName('html')[0]:null));"
                + "if(host&&typeof host.appendChild==='function'){host.appendChild(f);}"
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

            // error считаем recoverable как и missing:
            // в фоне WebView может временно не иметь готового DOM для appendChild/submit.
            boolean missing = status != null && (status.contains("missing") || status.contains("error"));
            if (missing && retriesLeft > 0) {
                int nextRetriesLeft = retriesLeft - 1;
                Log.d(TAG, BG_TRACE_PREFIX + " submitAutoBattleAction: submit path unstable (" + status
                        + "), retry left=" + nextRetriesLeft);
                binding.appBarMain.contentMain.webView.postDelayed(
                        () -> submitAutoBattleActionToWebView(result, nextRetriesLeft),
                        AUTO_SUBMIT_RETRY_DELAY_MS
                );
                return;
            }

            if (missing) {
                Log.w(TAG, BG_TRACE_PREFIX + " submitAutoBattleAction: submit path still unstable after retries, status=" + status);
            } else {
                Log.d(TAG, BG_TRACE_PREFIX + " submitAutoBattleAction: status=" + status);
            }
        });
    }

    /**
     * Прямой HTTP POST для отправки авто-удара, когда Activity в background.
     *
     * Зачем:
     * - Android WebView откладывает навигационные запросы (form.submit) при фоновой Activity;
     * - evaluateJavascript возвращает "ok_payload_submit", но HTTP POST физически не уходит;
     * - прямой HTTP POST через HttpURLConnection гарантирует отправку в любом состоянии lifecycle.
     *
     * Формат payload: `vcode|enemy|group|inf_bot|lev_bot|ftr|inu|inb|ina`.
     *
     * Зависимости:
     * - {@link ProxyRuntimeManager} (proxy/strict-proxy);
     * - {@link CookiesManager}, {@link CookieManager} (cookie сессии);
     * - {@link AppVars#BROWSER_USER_AGENT};
     * - {@link Russian#getString(byte[])} (декодирование ответа);
     * - {@link SessionManager#parseVCodeFromHtml(String, String)} (обновление VCode из ответа).
     */
    private void submitAutoBattleActionViaDirectHttp(String payload) {
        if (payload == null || payload.isEmpty()) {
            AppLog.w(TAG, TAG, BG_TRACE_PREFIX + " directHttpSubmit: empty payload, skip");
            return;
        }

        // Извлекаем VCode из payload перед отправкой (для синхронизации SessionManager)
        adoptVCodeFromAutoSubmitPayload(payload);

        String[] parts = payload.split("\\|");
        if (parts.length < 9) {
            AppLog.w(TAG, TAG, BG_TRACE_PREFIX + " directHttpSubmit: payload parts=" + parts.length + ", need 9, skip");
            return;
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            ByteArrayOutputStream outputStream = null;
            try {
                URL url = new URL("http://neverlands.ru/main.php");
                java.net.Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
                if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                    AppLog.e(TAG, TAG, BG_TRACE_PREFIX + " directHttpSubmit: PROXY_FAIL strict proxy required but unavailable");
                    return;
                }

                connection = activeProxy != null
                        ? (HttpURLConnection) url.openConnection(activeProxy)
                        : (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(AUTO_TURN_SERVER_PROBE_TIMEOUT_MS);
                connection.setReadTimeout(AUTO_TURN_SERVER_PROBE_TIMEOUT_MS);
                connection.setUseCaches(false);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setRequestProperty("Referer", "http://neverlands.ru/main.php");
                connection.setRequestProperty("User-Agent", AppVars.BROWSER_USER_AGENT);

                String cookie = CookieManager.getInstance().getCookie("http://neverlands.ru/main.php");
                if (cookie == null || cookie.isEmpty()) {
                    cookie = CookieManager.getInstance().getCookie("http://neverlands.ru/");
                }
                if ((cookie == null || cookie.isEmpty())) {
                    cookie = CookiesManager.obtain("neverlands.ru");
                }
                if (cookie != null && !cookie.isEmpty()) {
                    connection.setRequestProperty("Cookie", cookie);
                } else {
                    AppLog.w(TAG, TAG, BG_TRACE_PREFIX + " directHttpSubmit: cookie is empty");
                }

                // Формируем POST body: post_id=7&vcode=...&enemy=...&group=...&inf_bot=...&lev_bot=...&ftr=...&inu=...&inb=...&ina=...
                StringBuilder postBody = new StringBuilder();
                postBody.append("post_id=7");
                postBody.append("&vcode=").append(java.net.URLEncoder.encode(parts[0], "UTF-8"));
                postBody.append("&enemy=").append(java.net.URLEncoder.encode(parts[1], "UTF-8"));
                postBody.append("&group=").append(java.net.URLEncoder.encode(parts[2], "UTF-8"));
                postBody.append("&inf_bot=").append(java.net.URLEncoder.encode(parts[3], "UTF-8"));
                postBody.append("&lev_bot=").append(java.net.URLEncoder.encode(parts[4], "UTF-8"));
                postBody.append("&ftr=").append(java.net.URLEncoder.encode(parts[5], "UTF-8"));
                postBody.append("&inu=").append(java.net.URLEncoder.encode(parts[6], "UTF-8"));
                postBody.append("&inb=").append(java.net.URLEncoder.encode(parts[7], "UTF-8"));
                postBody.append("&ina=").append(java.net.URLEncoder.encode(parts[8], "UTF-8"));

                byte[] postData = postBody.toString().getBytes("UTF-8");
                connection.setRequestProperty("Content-Length", String.valueOf(postData.length));
                connection.getOutputStream().write(postData);
                connection.getOutputStream().flush();

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    AppLog.w(TAG, TAG, BG_TRACE_PREFIX + " directHttpSubmit: HTTP " + responseCode);
                    return;
                }

                inputStream = connection.getInputStream();
                outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                byte[] data = outputStream.toByteArray();
                String responseHtml = data.length > 0 ? Russian.getString(data) : null;

                if (responseHtml != null && !responseHtml.isEmpty()) {
                    boolean hasMarkers = hasFightMarkers(responseHtml);
                    AppLog.d(TAG, TAG, BG_TRACE_PREFIX + " directHttpSubmit: OK, responseLen="
                            + responseHtml.length() + ", hasFightMarkers=" + hasMarkers);

                    // Обновляем кэш HTML для следующего автохода
                    if (hasMarkers) {
                        AppVars.ContentMainPhp = responseHtml;
                    }
                    // Парсим VCode из ответа сервера
                    SessionManager.getInstance().parseVCodeFromHtml(responseHtml, "direct_http_fight_submit");
                } else {
                    AppLog.w(TAG, TAG, BG_TRACE_PREFIX + " directHttpSubmit: empty response");
                }

            } catch (Exception e) {
                AppLog.e(TAG, TAG, BG_TRACE_PREFIX + " directHttpSubmit: failed: " + e.getMessage());
            } finally {
                if (inputStream != null) {
                    try { inputStream.close(); } catch (IOException ignored) {}
                }
                if (outputStream != null) {
                    try { outputStream.close(); } catch (IOException ignored) {}
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "auto-battle-direct-http-submit").start();
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
        // RULE 5: Мигрирована на SessionManager
        SessionManager.getInstance().parseVCodeFromHtml("vcode=" + vcode, "auto_submit_payload");
        AppLog.i("vcode", TAG, BG_TRACE_PREFIX + "[VCODE_ADOPT] adoptVCodeFromAutoSubmitPayload: vcode updated via SessionManager, vcode=" + vcode);
    }

    /**
     * Выделяет `vcode` из сериализованного payload авто-удара.
     *
     * Формат payload:
     * - `vcode|enemy|group|inf_bot|lev_bot|ftr|inu|inb|ina`.
     *
     * Зависимости:
     * - используется в {@link #adoptVCodeFromAutoSubmitPayload(String)};
     * - валидирует токен регулярным шаблоном hex, чтобы не перетирать `AppVars.VCode`
     *   случайными значениями из неполного/битого payload.
     *
     * @param payload строка payload из `BuildResult`.
     * @return валидный vcode или пустая строка.
     */
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
    /**
     * Основной UI-контур ввода боевой/рыбацкой капчи.
     *
     * Правила работы:
     * - один активный диалог на один challenge (captchaUrl + finishUrl);
     * - при новом challenge старый диалог закрывается и открывается заново;
     * - обработчик кнопки "ОК" назначается в onShow до первого клика, чтобы не потерять submit;
     * - ввод валидируется только как цифры 1..6 символов.
     *
     * Зависимости:
     * - AppVars.IsFightCaptchaDialogVisible / ResumeAutoboiAfterCaptcha / FightLink / CodeAddress;
     * - updateFightCaptchaSubmitButtonState() для UI-состояния кнопки;
     * - appendOrReplaceCaptchaCode() для сборки URL submit;
     * - buildFightCaptchaFinishKey() + AppVars.LastSubmittedFightCaptcha* для anti-duplicate;
     * - submitCaptchaSolution() для отправки решения (бой/рыбалка);
     * - loadCaptchaImageAsync() / startFightCaptchaAutoRefresh() для синхронизации картинки challenge.
     *
     * Важно:
     * - setOnShowListener должен быть установлен до dialog.show(), иначе стиль/клик могут не примениться.
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
                new android.text.InputFilter.LengthFilter(6)
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
                .setPositiveButton("ОК", null)
                .setNegativeButton("Отмена", (d, which) -> {
                    AppVars.ResumeAutoboiAfterCaptcha = false;
                    AppVars.ResumeSearchBoxAfterCaptcha = false;
                })
                .create();

        dialog.setOnCancelListener(d -> {
            AppVars.ResumeAutoboiAfterCaptcha = false;
            AppVars.ResumeSearchBoxAfterCaptcha = false;
        });
        dialog.setOnDismissListener(d -> {
            stopFightCaptchaAutoRefresh();
            AppVars.IsFightCaptchaDialogVisible = false;
            cancelCaptchaSystemNotification();
            activeFightCaptchaDialog = null;
            activeFightCaptchaUrl = "";
            activeFightFinishUrl = "";
            activeFightCaptchaInput = null;
            activeFightCaptchaImageLocked = false;
            activeFightCaptchaLoadSeq++;
            if (replacingFightCaptchaDialog) {
                replacingFightCaptchaDialog = false;
            } else if (!captchaSubmitted[0]) {
                AppVars.ResumeAutoboiAfterCaptcha = false;
                AppVars.ResumeSearchBoxAfterCaptcha = false;
            }
        });
        activeFightCaptchaDialog = dialog;
        activeFightCaptchaUrl = captchaUrl == null ? "" : captchaUrl;
        activeFightFinishUrl = finishUrl == null ? "" : finishUrl;
        // Фиксируем ожидаемый challenge для interceptor-guard: пока открыт popup,
        // внешние code.php-URL (другой challenge) не должны подменять текущую капчу.
        AppVars.CodeAddress = activeFightCaptchaUrl;
        activeFightCaptchaInput = input;

        dialog.setOnShowListener(d -> {
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                int primaryColor = ContextCompat.getColor(this, R.color.purple_500);
                int textColor = ContextCompat.getColor(this, R.color.white);
                positiveButton.setBackgroundColor(primaryColor);
                positiveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
                positiveButton.setTextColor(textColor);
                positiveButton.setEnabled(true);
                positiveButton.setOnClickListener(v -> {
                    String code = input.getText().toString().trim();
                    Log.d(TAG, "showCaptchaDialog: ok clicked, codeLen=" + code.length());
                    if (code.isEmpty()) {
                        input.setError("Введите код");
                        input.requestFocus();
                        return;
                    }
                    if (!code.matches("\\d{1,6}")) {
                        input.setError("Код должен содержать только цифры");
                        input.requestFocus();
                        return;
                    }

                    captchaSubmitted[0] = true;
                    if (AppVars.ResumeAutoboiAfterCaptcha
                            && AppVars.Profile != null
                            && AppVars.Profile.LezDoAutoboi) {
                        AppVars.Autoboi = ru.neverlands.abclient.model.AutoboiState.AutoboiOn;
                        Log.d(TAG, "showCaptchaDialog: restoring autoboi after captcha submit");
                    }
                    AppVars.ResumeAutoboiAfterCaptcha = false;
                    boolean resumeSearchBox = AppVars.ResumeSearchBoxAfterCaptcha;
                    AppVars.ResumeSearchBoxAfterCaptcha = false;

                    String submitUrl = appendOrReplaceCaptchaCode(finishUrl, code);
                    if (!isFishCaptcha) {
                        AppVars.LastSubmittedFightCaptchaFinishKey = buildFightCaptchaFinishKey(submitUrl);
                        AppVars.LastSubmittedFightCaptchaAtMs = System.currentTimeMillis();
                        // Сбрасываем текущие captcha-маркеры, чтобы stale-значения не триггерили повторный popup.
                        AppVars.FightLink = "";
                        AppVars.CodeAddress = "";
                    }
                    Log.d(TAG, "showCaptchaDialog: submitting " + submitUrl);
                    submitCaptchaSolution(submitUrl, isFishCaptcha);
                    if (!isFishCaptcha && resumeSearchBox && AppVars.DoSearchBox && !AppVars.AutoMoving) {
                        Log.d(TAG, "showCaptchaDialog: bootstrap auto treasure after captcha submit");
                        WebView targetWebView = null;
                        if (binding != null
                                && binding.appBarMain != null
                                && binding.appBarMain.contentMain != null) {
                            targetWebView = binding.appBarMain.contentMain.webView;
                        }
                        if (targetWebView != null) {
                            WebView finalTargetWebView = targetWebView;
                            finalTargetWebView.postDelayed(() -> {
                                try {
                                    finalTargetWebView.loadUrl("http://neverlands.ru/main.php?ab_search_box_bootstrap=1");
                                } catch (Exception e) {
                                    Log.e(TAG, "showCaptchaDialog: auto treasure bootstrap failed", e);
                                }
                            }, 450L);
                        } else {
                            Log.w(TAG, "showCaptchaDialog: skip auto treasure bootstrap, webView is null");
                        }
                    }
                    dialog.dismiss();
                });
            }
            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) { }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    updateFightCaptchaSubmitButtonState();
                }
            });
            updateFightCaptchaSubmitButtonState();
        });
        dialog.show();

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
        // ✅ Permission check for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(this).notify(CAPTCHA_NOTIFICATION_ID, builder.build());
            }
        } else {
            NotificationManagerCompat.from(this).notify(CAPTCHA_NOTIFICATION_ID, builder.build());
        }
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
    /**
     * Формирует итоговый URL submit капчи без изменения бизнес-логики остальных параметров.
     *
     * Что делает:
     * - заменяет существующий query-параметр code=...;
     * - если code отсутствует, добавляет его первым параметром query;
     * - сохраняет все остальные параметры (включая vcode/min/max/sum/ftype);
     * - сохраняет фрагмент #... если он присутствовал.
     *
     * Зависимости:
     * - Uri.encode(...) для безопасной передачи пользовательского ввода;
     * - вызывается из showCaptchaDialog() перед submitCaptchaSolution().
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
     * Нормализует finish-link для анти-дубля popup:
     * - приводит относительный URL к `http://neverlands.ru/...`,
     * - заменяет конкретный `code=12345` на единый `code=????`.
     *
     * Зависимости:
     * - `AutoModeForegroundService.maybeShowFightCaptchaDialog(...)` сравнивает тот же ключ,
     *   чтобы не поднимать повторно идентичный challenge после уже отправленного submit.
     */
    /**
     * Нормализует finish-link в детерминированный ключ для дедупликации повторных popup.
     *
     * Правила:
     * - относительный URL приводится к абсолютному http://neverlands.ru/...;
     * - конкретный code=<digits> заменяется на code=????;
     * - ключ должен быть одинаковым для одного и того же challenge, независимо от введённого кода.
     *
     * Зависимости:
     * - AppVars.LastSubmittedFightCaptchaFinishKey;
     * - AutoModeForegroundService.maybeShowFightCaptchaDialog(...), где этот ключ сравнивается
     *   с текущим pending finish-link.
     */
    private String buildFightCaptchaFinishKey(String finishUrl) {
        String normalized = finishUrl == null ? "" : finishUrl.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            if (normalized.startsWith("/")) {
                normalized = "http://neverlands.ru" + normalized;
            } else {
                normalized = "http://neverlands.ru/" + normalized;
            }
        }
        normalized = normalized.replaceFirst("([?&])code=[^&]*", "$1code=????");
        return normalized;
    }

    /**
     * Отправляет решение captcha в корректный серверный контур (бой/рыбалка), не ломая frame-flow.
     *
     * Зависимости:
     * - `binding.appBarMain.contentMain.webView`: основной игровой WebView;
     * - `submitFishCaptchaViaAjaxOrFallback(...)`: fish captcha (`fish_ajax.php?act=2`) через Ajax;
     * - `WebView.loadUrl(...)`: fallback и штатный submit для боя (`main.php?get_id=61&act=7...`).
     */
    /**
     * Отправляет решение капчи в правильный серверный поток.
     *
     * Ветки:
     * - isFishCaptcha=true: отправка через submitFishCaptchaViaAjaxOrFallback(...), чтобы сохранить JS-flow рыбалки;
     * - isFishCaptcha=false: прямой переход mainWebView.loadUrl(submitUrl) для боевого act=7.
     *
     * Зависимости:
     * - binding.appBarMain.contentMain.webView;
     * - submitFishCaptchaViaAjaxOrFallback(...);
     * - логи showCaptchaDialog: submitting ... для трассировки отправки.
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
                        updateFightCaptchaSubmitButtonState();
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

    /**
     * Мягко обновляет изображение капчи из последнего перехваченного байтового кэша.
     *
     * Зависимости:
     * - источник данных: `AppVars.LastFightCaptchaImage*` (обновляется перехватчиком сети);
     * - вызывается сразу после показа popup и до сетевого fallback-запроса,
     *   чтобы быстрее показать пользователю актуальную картинку без дополнительного RTT.
     *
     * Ограничения:
     * - применяет обновление только если URL совпадает с текущим challenge;
     * - игнорирует устаревшие байты (полученные до показа текущего диалога).
     */
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

    /**
     * Останавливает таймер автообновления изображения капчи.
     *
     * Зависимости:
     * - парный метод к {@link #startFightCaptchaAutoRefresh(android.widget.ImageView, android.widget.ProgressBar, String)};
     * - вызывается при закрытии popup и при «lock» первой стабильной картинки challenge.
     */
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
            boolean canTrySwitch = activeFightCaptchaDialog != null
                    && activeFightCaptchaDialog.isShowing()
                    && !replacingFightCaptchaDialog
                    && activeFightFinishUrl != null
                    && activeFightFinishUrl.contains("get_id=61")
                    && activeFightFinishUrl.contains("act=7")
                    && activeFightFinishUrl.contains("code=????");
            if (canTrySwitch) {
                long latestAtMs = AppVars.LastFightCaptchaImageAtMs;
                long challengeAgeMs = latestAtMs > 0L
                        ? (System.currentTimeMillis() - latestAtMs)
                        : Long.MAX_VALUE;
                if (challengeAgeMs >= CAPTCHA_IMAGE_STABILIZE_DELAY_MS && challengeAgeMs < 15000L) {
                    String latestCaptchaUrl = latestUrl == null ? "" : latestUrl.trim();
                    if (!latestCaptchaUrl.isEmpty() && !latestCaptchaUrl.startsWith("http")) {
                        if (latestCaptchaUrl.startsWith("/")) {
                            latestCaptchaUrl = "http://neverlands.ru" + latestCaptchaUrl;
                        } else {
                            latestCaptchaUrl = "http://neverlands.ru/" + latestCaptchaUrl;
                        }
                    }

                    String candidateFinishUrl = AppVars.FightLink == null ? "" : AppVars.FightLink.trim();
                    if (!candidateFinishUrl.isEmpty() && !candidateFinishUrl.startsWith("http")) {
                        if (candidateFinishUrl.startsWith("/")) {
                            candidateFinishUrl = "http://neverlands.ru" + candidateFinishUrl;
                        } else {
                            candidateFinishUrl = "http://neverlands.ru/" + candidateFinishUrl;
                        }
                    }
                    boolean candidateIsFightFinish = candidateFinishUrl.contains("get_id=61")
                            && candidateFinishUrl.contains("act=7")
                            && candidateFinishUrl.contains("code=????");
                    if (!candidateIsFightFinish) {
                        candidateFinishUrl = activeFightFinishUrl;
                    }

                    boolean captchaChanged = !isSameCaptchaUrl(activeFightCaptchaUrl, latestCaptchaUrl);
                    boolean finishChanged = !isSameFightFinishUrl(activeFightFinishUrl, candidateFinishUrl);
                    if ((captchaChanged || finishChanged) && !latestCaptchaUrl.isEmpty()) {
                        Log.d(TAG, "updateCaptchaImageFromCaptured: switch to latest challenge, expected="
                                + expectedCaptchaUrl + ", latest=" + latestCaptchaUrl
                                + ", finishChanged=" + finishChanged
                                + ", challengeAgeMs=" + challengeAgeMs);
                        showCaptchaDialog(latestCaptchaUrl, candidateFinishUrl);
                        return false;
                    }
                }
            }
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
        // Стабилизируем challenge в текущем popup: после первой успешной отрисовки
        // блокируем авто-перерисовку, чтобы пользователь вводил код по той же картинке,
        // которую реально видит в диалоге.
        if (previousHash == 0) {
            activeFightCaptchaImageLocked = true;
            stopFightCaptchaAutoRefresh();
        }
        updateFightCaptchaSubmitButtonState();
        Log.d(TAG, "updateCaptchaImageFromCaptured: image updated, bytes=" + latestBytes.length
                + ", atMs=" + latestAtMs + ", url=" + latestUrl);
        return true;
    }

    /**
     * Управляет доступностью кнопки «ОК» в popup капчи.
     *
     * Зависимости:
     * - поля состояния текущего challenge: `activeFightCaptchaImageAtMs`, `activeFightCaptchaImageHash`;
     * - пользовательский ввод: `activeFightCaptchaInput`;
     * - вызывается после каждого обновления картинки и после изменения текста ввода.
     *
     * Правило валидации:
     * - код должен быть числом 0..99999;
     * - кнопка активируется только когда есть валидный код и зафиксированная картинка капчи.
     */
    /**
     * Обновляет визуальное состояние кнопки подтверждения в капча-диалоге.
     *
     * Текущая стратегия:
     * - кнопка не блокируется жёстко из-за race/focus-сценариев;
     * - фактическая валидация выполняется в OnClick кнопки "ОК";
     * - здесь остаётся диагностика: validCode/imageReady/btnEnabled.
     *
     * Зависимости:
     * - activeFightCaptchaDialog / activeFightCaptchaInput;
     * - activeFightCaptchaImageAtMs / activeFightCaptchaImageHash;
     * - AppVars.LastFightCaptchaImageBytes / LastFightCaptchaImageUrl.
     */
    private void updateFightCaptchaSubmitButtonState() {
        AlertDialog dialog = activeFightCaptchaDialog;
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        android.widget.Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (btn == null) {
            return;
        }
        android.widget.EditText input = activeFightCaptchaInput;
        String value = input == null ? "" : input.getText().toString().trim();
        boolean validCode = !value.isEmpty() && value.length() <= 6 && value.matches("\\d{1,6}");
        boolean hasCapturedImage = AppVars.LastFightCaptchaImageBytes != null
                && AppVars.LastFightCaptchaImageBytes.length > 0
                && isSameCaptchaUrl(activeFightCaptchaUrl, AppVars.LastFightCaptchaImageUrl);
        boolean imageReady = (activeFightCaptchaImageAtMs > 0L && activeFightCaptchaImageHash != 0)
                || hasCapturedImage;
        // Не блокируем кнопку полностью: валидация и удержание диалога делается в onClick.
        // Это устраняет кейс, когда из-за race/фокуса кнопка остаётся неактивной.
        btn.setEnabled(true);
        Log.d(TAG, "updateFightCaptchaSubmitButtonState: codeLen=" + value.length()
                + ", validCode=" + validCode
                + ", imageReady=" + imageReady
                + ", btnEnabled=" + btn.isEnabled());
    }

    /**
     * Сравнивает два URL капчи после нормализации host/protocol.
     *
     * Зависимости:
     * - {@link #normalizeCaptchaUrlForCompare(String)};
     * - используется в логике защиты от «устаревшей» капчи, когда байты/диалог относятся
     *   к разным challenge.
     */
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

    /**
     * Нормализует URL завершения боя для безопасного сравнения challenge.
     *
     * Зависимости:
     * - используется в {@link #isSameFightFinishUrl(String, String)};
     * - выравнивает `https -> http` и `www.neverlands.ru -> neverlands.ru`,
     *   чтобы различия транспортного уровня не ломали детекцию «тот же бой/новый бой».
     */
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
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                isActivityResumedState = false;
            }
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
                        if (message.contains("Нападение") || message.contains("НАПАДЕНИЕ")) {
                            AppVars.LastFightAnnounceAtMs = System.currentTimeMillis();
                            Log.d(TAG, BG_TRACE_PREFIX + " ACTION_ADD_CHAT_MESSAGE: attack announce pulse");
                        }
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
        // DEPRECATED: isRoomManagerStarted removed - HTML injection handles player list display
        AppVars.init(this);
        registerAppBroadcastReceiverIfNeeded();
        registerScreenStateReceiverIfNeeded();
        createCaptchaNotificationChannel();
        requestPostNotificationsPermissionIfNeeded();
        ContactsManager.initialize(this);
        AppVars.mainActivity = new WeakReference<>(this);
        if (AppVars.Profile != null) {
            LogcatFileRecorder.setEnabled(this, AppVars.Profile.RecordLogcatToFile);
        }
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
        lastQuickPanelAutoMovingState = AppVars.AutoMoving;
        
        // Первичная загрузка main.php + чат-фреймов.
        loadInitialUrls();
        AutoFunctionsManager.getInstance(this).restorePersistentAutoModesAfterLogin();

        // Подписка на действия автободя: результат -> AutoSubmit() в WebView.
        fightViewModel = new ViewModelProvider(this).get(FightViewModel.class);
        if (fightSubmitObserver != null) {
            fightViewModel.getSubmitAction().removeObserver(fightSubmitObserver);
        }
        fightSubmitObserver = result -> {
            if (result != null) {
                enqueueAutoBattleSubmit(result);
                fightViewModel.onActionSubmitted();
            }
        };
        // observeForever нужен, чтобы submit авто-удара не задерживался до onResume,
        // когда бой начался в фоне/при блокировке экрана.
        // Observer обязательно снимаем в onDestroy.
        fightViewModel.getSubmitAction().observeForever(fightSubmitObserver);

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
        // Когда Activity не в foreground, WebView form.submit() реально не отправляет HTTP POST —
        // Android WebView откладывает навигацию до возврата в foreground.
        // Поэтому в background используем прямой HTTP POST, минуя WebView.
        if (!isActivityResumedState) {
            AppLog.d(TAG, TAG, BG_TRACE_PREFIX + " autoBattleDelay: submit via direct HTTP (background)");
            submitAutoBattleActionViaDirectHttp(payload);
            return;
        }
        Log.d(TAG, BG_TRACE_PREFIX + " autoBattleDelay: submit now (foreground, WebView)");
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

        CookieManager cookieManager = CookieManager.getInstance();
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

            String urlNeverlands = "http://neverlands.ru/";
            String urlWwwNeverlands = "http://www.neverlands.ru/";
            for (java.net.HttpCookie cookie : filteredCookies) {
                StringBuilder cookieValue = new StringBuilder()
                        .append(cookie.getName())
                        .append("=")
                        .append(cookie.getValue() == null ? "" : cookie.getValue())
                        .append("; Path=/");
                if (cookie.getSecure()) {
                    cookieValue.append("; Secure");
                }
                cookieManager.setCookie(urlNeverlands, cookieValue.toString());
                cookieManager.setCookie(urlWwwNeverlands, cookieValue.toString());
            }
            cookieManager.flush();
            Log.d(TAG, "AUTH_COOKIE_SYNC: applied lastCookies names=" + names);
            AppVars.lastCookies = null;
        }
        syncSessionCookiesAcrossHosts(cookieManager, "after_lastCookies_apply");
    }

    // Первичная загрузка основных и чат-фреймов.
    /**
     * Синхронизирует cookie между `neverlands.ru` и `www.neverlands.ru`.
     *
     * Назначение:
     * - после auth-flow через один host устранить рассинхрон host-only cookie;
     * - гарантировать валидную сессию для room/chat-фреймов.
     *
     * Зависимости:
     * - CookieManager: общее WebView-хранилище cookie.
     * - hasSessionCookieTokens(...): проверка наличия сессионных токенов.
     * - mirrorCookieHeaderToHost(...): перенос cookie-пар в host без сессии.
     */
    private void syncSessionCookiesAcrossHosts(CookieManager manager, String stage) {
        if (manager == null) {
            return;
        }
        final String neverUrl = "http://neverlands.ru/";
        final String wwwUrl = "http://www.neverlands.ru/";
        String neverCookie = manager.getCookie(neverUrl);
        String wwwCookie = manager.getCookie(wwwUrl);

        boolean neverHasSession = hasSessionCookieTokens(neverCookie);
        boolean wwwHasSession = hasSessionCookieTokens(wwwCookie);
        Log.d(TAG, "AUTH_COOKIE_SYNC[" + stage + "]: never=" + summarizeCookieHeaderNames(neverCookie)
                + ", www=" + summarizeCookieHeaderNames(wwwCookie));

        boolean changed = false;
        if (!neverHasSession && wwwHasSession) {
            changed |= mirrorCookieHeaderToHost(manager, wwwCookie, neverUrl);
        } else if (!wwwHasSession && neverHasSession) {
            changed |= mirrorCookieHeaderToHost(manager, neverCookie, wwwUrl);
        }

        if (changed) {
            manager.flush();
            String neverAfter = manager.getCookie(neverUrl);
            String wwwAfter = manager.getCookie(wwwUrl);
            Log.d(TAG, "AUTH_COOKIE_SYNC[" + stage + "]: mirrored never=" + summarizeCookieHeaderNames(neverAfter)
                    + ", www=" + summarizeCookieHeaderNames(wwwAfter));
        }
    }

    /**
     * Копирует `name=value` cookie-пары из source-header в target-host.
     *
     * Важно:
     * - cookie-атрибуты (`Path`, `Domain`, `Expires`, ...) не копируются;
     * - переносится только полезная сессионная часть.
     */
    private boolean mirrorCookieHeaderToHost(CookieManager manager, String sourceHeader, String targetUrl) {
        if (sourceHeader == null || sourceHeader.isEmpty() || targetUrl == null || targetUrl.isEmpty()) {
            return false;
        }
        boolean changed = false;
        String[] parts = sourceHeader.split(";");
        for (String rawPart : parts) {
            if (rawPart == null) {
                continue;
            }
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = part.substring(0, eq).trim();
            if (name.isEmpty() || isCookieAttributeName(name)) {
                continue;
            }
            String value = part.substring(eq + 1).trim();
            manager.setCookie(targetUrl, name + "=" + value + "; Path=/");
            changed = true;
        }
        return changed;
    }

    /**
     * Проверяет, содержит ли cookie-header признаки валидной игровой сессии.
     */
    private boolean hasSessionCookieTokens(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return false;
        }
        String lower = cookieHeader.toLowerCase(Locale.ROOT);
        return lower.contains("phpsessid=")
                || lower.contains("nevercode=")
                || lower.contains("neverhash=")
                || lower.contains("neverpuid=")
                || lower.contains("watermark=");
    }

    /**
     * Формирует краткую сводку cookie (`count + names`) для AUTH_COOKIE_SYNC логов.
     */
    private String summarizeCookieHeaderNames(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return "empty";
        }
        ArrayList<String> names = new ArrayList<>();
        String[] parts = cookieHeader.split(";");
        for (String rawPart : parts) {
            if (rawPart == null) {
                continue;
            }
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = part.substring(0, eq).trim();
            if (!name.isEmpty() && !isCookieAttributeName(name)) {
                names.add(name);
            }
        }
        return "count=" + names.size() + ", names=" + names;
    }

    /**
     * Возвращает `true`, если token является cookie-атрибутом, а не именем cookie.
     */
    private boolean isCookieAttributeName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return "path".equals(lower)
                || "domain".equals(lower)
                || "expires".equals(lower)
                || "max-age".equals(lower)
                || "secure".equals(lower)
                || "httponly".equals(lower)
                || "samesite".equals(lower);
    }

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
        isActivityResumedState = true;
        registerAppBroadcastReceiverIfNeeded();
        registerScreenStateReceiverIfNeeded();
        startRoomUsersPolling();
        logBackgroundState("onResume");
        
        // КРИТИЧНО: Очищаем кэш озера при возобновлении приложения
        // Если приложение было свёрнуто более 2 минут, vcode в кэше озера истеёк
        // Нужно перезагрузить озеро при следующем цикле рыбалки
        long timeInBackground = System.currentTimeMillis() - (isActivityResumedState ? 0 : 1000);
        if (AppVars.ContentLakeHtmlLastUpdateAtMs > 0) {
            long lakeAgeMs = System.currentTimeMillis() - AppVars.ContentLakeHtmlLastUpdateAtMs;
            if (lakeAgeMs > 120_000) {  // озеро старше 2 минут
                Log.d(TAG, "BG_TRACE onResume: clearing expired lake cache (age=" + lakeAgeMs + "ms)");
                AppVars.ContentLakeHtml = "";
                AppVars.ContentLakeHtmlLastUpdateAtMs = 0;
            }
        }
        
        restorePendingFightCaptchaDialogIfNeeded();
        AutoModeForegroundService.syncServiceState(this, "onResume");
    }

    /**
     * Восстанавливает popup боевой капчи после возврата в приложение,
     * если challenge уже зафиксирован в AppVars, но диалог еще не отображается.
     *
     * Когда нужно:
     * - captcha-state мог быть выставлен в фоне сервисом/парсером;
     * - пользователь вернулся в Activity, а окно капчи не было показано.
     *
     * Зависимости:
     * - `AppVars.IsFightCaptchaDialogVisible`: флаг ожидания ручного ввода капчи;
     * - `AppVars.FightLink`: finish-link (`get_id=61&act=7&code=????...`);
     * - `AppVars.CodeAddress`: URL картинки капчи (`/modules/code/code.php?...`);
     * - `showCaptchaDialog(...)`: единый UI-контур ввода и submit кода.
     */
    private void restorePendingFightCaptchaDialogIfNeeded() {
        if (!AppVars.IsFightCaptchaDialogVisible) {
            return;
        }
        if (activeFightCaptchaDialog != null && activeFightCaptchaDialog.isShowing()) {
            return;
        }

        String finishUrl = AppVars.FightLink;
        if (finishUrl == null
                || !finishUrl.contains("get_id=61")
                || !finishUrl.contains("act=7")
                || !finishUrl.contains("code=????")) {
            return;
        }

        String captchaUrl = AppVars.CodeAddress;
        if (captchaUrl == null || captchaUrl.isEmpty()) {
            Log.w(TAG, "restorePendingFightCaptchaDialogIfNeeded: captcha url is empty");
            return;
        }

        Log.d(TAG, "restorePendingFightCaptchaDialogIfNeeded: restoring pending fight captcha dialog");
        showCaptchaDialog(captchaUrl, finishUrl);
    }

    // Отписка от LocalBroadcast событий (во избежание утечек).
    @Override
    protected void onPause() {
        super.onPause();
        isActivityResumedState = false;
        boolean keepBackgroundLoops = shouldKeepBackgroundLoops();
        if (suppressBackgroundLoopsForContacts) {
            keepBackgroundLoops = false;
            AppLog.d("contacts_nav", TAG, BG_TRACE_PREFIX + " onPause: forcing background loops off due to contacts navigation");
        }
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
            private String escapeHtml(String value) {
                if (value == null) return "";
                return value
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&#39;");
            }

            private void forwardServerPopupToChat(String kind, String message) {
                String normalized = message == null ? "" : message.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
                if (normalized.isEmpty()) {
                    return;
                }
                String payload = ru.neverlands.abclient.postfilter.MainPhp.buildServerChatTimeHtmlExternal()
                        + "<font color=#333399><b>Сервер (" + escapeHtml(kind) + "):</b></font> "
                        + escapeHtml(normalized);
                Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
                intent.putExtra("message", payload);
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.e("JS_CONSOLE", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                forwardServerPopupToChat("alert", message);
                result.confirm();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                forwardServerPopupToChat("confirm", message);
                result.confirm();
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
                // Здесь ничего не делаем — просто возвращаем false
                resultMsg.sendToTarget();
                return false;
            }
        });
    }
    // Освобождаем таймеры/вебвью и менеджеры при уничтожении Activity.
    @Override
    protected void onDestroy() {
        ru.neverlands.abclient.utils.FileLogger.log("MainActivity: onDestroy() called.");
        isActivityResumedState = false;
        logBackgroundState("onDestroy_enter");
        stopTimer();
        stopChatRefresh();
        stopRoomUsersPolling();
        clearPendingAutoBattleSubmit();
        if (fightViewModel != null && fightSubmitObserver != null) {
            fightViewModel.getSubmitAction().removeObserver(fightSubmitObserver);
            fightSubmitObserver = null;
        }
        unregisterAppBroadcastReceiverIfNeeded();
        unregisterScreenStateReceiverIfNeeded();
        // DEPRECATED: RoomManager.stopTracing() removed - no longer needed
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
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
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
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_contacts) {
            suppressBackgroundLoopsForContacts = true;
            suppressChatRefreshOnceAfterContacts = true;
            suppressRoomRefreshOnceAfterContacts = true;
            shouldRestoreChatRefreshAfterContacts = (chatRefreshRunnable != null);
            Log.d(TAG, BG_TRACE_PREFIX + " nav_contacts: pause chat/room polling, chatActive="
                    + (chatRefreshRunnable != null) + ", roomActive=" + (roomUsersPollingRunnable != null));
            FileLogger.trace("contacts_nav",
                    "open contacts: chatActive=" + (chatRefreshRunnable != null)
                            + ", roomActive=" + (roomUsersPollingRunnable != null));
            stopChatRefresh();
            stopRoomUsersPolling();
            Intent intent = new Intent(this, ContactsActivity.class);
            contactsActivityLauncher.launch(intent);
        } else if (id == R.id.nav_clans) {
            Intent intent = new Intent(this, ClansActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_logs) {
            Intent intent = new Intent(this, LogsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_logout) {
            performLogoutToLogin();
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
                    checkServerTimerDrivenActions();
                    syncQuickButtonsRuntimeState();
                    AppTimerManager.getInstance(MainActivity.this).processDueTimers();
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
                try {
                    requestChatRefresh();
                } catch (Throwable t) {
                    Log.e(TAG, BG_TRACE_PREFIX + " requestChatRefresh runnable failed", t);
                } finally {
                    chatRefreshHandler.postDelayed(this, getEffectiveChatRefreshSeconds() * 1000L);
                }
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
        requestChatRefresh(true);
    }

    private void requestChatRefresh(boolean refreshRoomUsers) {
        ensureChatRefrWebViewReady();
        if (chatRefrWebView == null) {
            Log.w(TAG, BG_TRACE_PREFIX + " requestChatRefresh skipped: chatRefrWebView is null");
            return;
        }
        if (suppressChatRefreshOnceAfterContacts) {
            suppressChatRefreshOnceAfterContacts = false;
            AppLog.d("contacts_nav", TAG, BG_TRACE_PREFIX + " requestChatRefresh: skipped once after contacts");
            return;
        }
        long now = System.currentTimeMillis();
        long roomDeltaMs = now - lastRoomUsersRefreshAtMs;
        if (roomDeltaMs >= 0 && roomDeltaMs < CHAT_ROOM_COLLISION_GUARD_MS) {
            long waitMs = CHAT_ROOM_COLLISION_GUARD_MS - roomDeltaMs;
            AppLog.d("chat_poll", TAG, BG_TRACE_PREFIX + " requestChatRefresh: defer by room-collision guard, waitMs="
                    + waitMs + ", roomDeltaMs=" + roomDeltaMs);
            chatRefreshHandler.postDelayed(() -> requestChatRefresh(refreshRoomUsers), waitMs);
            return;
        }
        long ts = now;
        lastChatRefreshAtMs = ts;
        String url = "http://neverlands.ru/ch.php?" + ts + "&show=1&fyo=" + chatFyo;
        Log.d(TAG, BG_TRACE_PREFIX + " requestChatRefresh: " + url);
        try {
            chatRefrWebView.loadUrl(url);
        } catch (Throwable t) {
            AppLog.e("chat_poll", TAG, BG_TRACE_PREFIX + " requestChatRefresh loadUrl failed, url=" + url, t);
            ensureChatRefrWebViewReady();
            if (chatRefrWebView != null) {
                try {
                    chatRefrWebView.loadUrl(url);
                    AppLog.w("chat_poll", TAG, BG_TRACE_PREFIX + " requestChatRefresh: retry after WebView rebind, url=" + url);
                } catch (Throwable retryError) {
                    AppLog.e("chat_poll", TAG, BG_TRACE_PREFIX + " requestChatRefresh retry failed, url=" + url, retryError);
                }
            }
        }

        // Поддерживаем room-list "живым" только при включенном "Слежении за локацией".
        // Это соответствует логике ПК-версии: polling комнаты привязан к LocationTracking.
        try {
            if (refreshRoomUsers && AutoFunctionsManager.getInstance(this).isLocationTrackingEnabled()) {
                requestRoomUsersRefreshSoon();
            }
        } catch (Exception e) {
            Log.w(TAG, "requestChatRefresh: failed to refresh room users list", e);
        }
    }

    // Немедленное обновление чата (из JS-моста).
    private void ensureChatRefrWebViewReady() {
        if (chatRefrWebView == null) {
            chatRefrWebView = new WebView(this);
            setupWebView(chatRefrWebView, new CustomWebViewClient());
            chatRefrWebView.setVisibility(View.GONE);
        }
        if (binding != null && binding.getRoot() instanceof ViewGroup && chatRefrWebView.getParent() == null) {
            ((ViewGroup) binding.getRoot()).addView(chatRefrWebView, new ViewGroup.LayoutParams(1, 1));
            Log.d(TAG, BG_TRACE_PREFIX + " ensureChatRefrWebViewReady: attached hidden ch_refr view");
        }
    }

    private int getEffectiveChatRefreshSeconds() {
        int effective = chatRefreshSeconds;
        try {
            if (AutoFunctionsManager.getInstance(this).isAutoBossEnabled()) {
                effective = Math.min(effective, CHAT_REFRESH_AUTO_BOSS_SECONDS);
            }
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " getEffectiveChatRefreshSeconds: failed to read autoBoss flag", e);
        }
        return Math.max(1, effective);
    }

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

    /**
     * Признак активного интерактивного foreground UI.
     *
     * Зависимости:
     * - `isActivityResumedState` (onResume/onPause),
     * - `PowerManager.isInteractive()` (экран не погашен/не lockscreen),
     * - `hasWindowFocus()` (Activity действительно в фокусе пользователя).
     */
    public boolean isUiForegroundInteractive() {
        if (!isActivityResumedState) {
            return false;
        }
        if (isDeviceLocked()) {
            return false;
        }
        boolean interactive = true;
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                interactive = powerManager.isInteractive();
            }
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " isUiForegroundInteractive: fallback by resumed-state", e);
        }
        return interactive && hasWindowFocus();
    }

    /**
     * Более мягкий признак активного foreground UI без требования `windowFocus`.
     *
     * Нужен для устранения гонки при старте/возврате Activity, когда `hasWindowFocus()` ещё `false`,
     * но приложение уже на экране и фоновые авто-действия запускать рано.
     */
    public boolean isUiForegroundLikely() {
        if (!isActivityResumedState) {
            return false;
        }
        if (isDeviceLocked()) {
            return false;
        }
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                return powerManager.isInteractive();
            }
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " isUiForegroundLikely: fallback by resumed-state", e);
        }
        return true;
    }

    private boolean isDeviceLocked() {
        try {
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return keyguardManager.isDeviceLocked();
            }
            return keyguardManager.isKeyguardLocked();
        } catch (Exception e) {
            Log.w(TAG, BG_TRACE_PREFIX + " isDeviceLocked: fallback false", e);
            return false;
        }
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
        int netColorState = RuntimeNetTrace.colorStateForUi();
        int netColor;
        switch (netColorState) {
            case RuntimeNetTrace.COLOR_OK:
                netColor = ContextCompat.getColor(this, R.color.teal_200);
                break;
            case RuntimeNetTrace.COLOR_WARN:
                netColor = ContextCompat.getColor(this, R.color.colorAccent);
                break;
            case RuntimeNetTrace.COLOR_FAIL:
                netColor = ContextCompat.getColor(this, R.color.red);
                break;
            default:
                netColor = ContextCompat.getColor(this, R.color.white);
                break;
        }
        binding.appBarMain.contentMain.statusBar.networkDebugTextView.setTextColor(netColor);
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

    /**
     * Запрашивает безопасную перезагрузку main-frame из фоновой автоматики.
     *
     * Используется, когда внешний модуль (например, RoomManager) поставил в очередь действие,
     * которое исполняется в `MainPhp.process(...)` и требует ближайшего non-combat тика.
     */
    public void requestMainFrameReloadFromAutomation(String reason) {
        runOnUiThread(() -> {
            try {
                if (binding == null || binding.appBarMain == null || binding.appBarMain.contentMain == null
                        || binding.appBarMain.contentMain.webView == null) {
                    return;
                }
                long now = System.currentTimeMillis();
                String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_auto=1&r=" + now;
                // RULE 5: VCode получается через SessionManager
                String vcode = SessionManager.getInstance().getValidVCodeForAction("auto_cure_reload");
                if (vcode != null && !vcode.isEmpty()) {
                    reloadUrl += "&vcode=" + vcode;
                }
                Log.d(TAG, "AUTO_CURE_TRACE requestMainFrameReloadFromAutomation: reason=" + reason
                        + ", url=" + reloadUrl);
                binding.appBarMain.contentMain.webView.loadUrl(reloadUrl);
            } catch (Exception e) {
                Log.w(TAG, "AUTO_CURE_TRACE requestMainFrameReloadFromAutomation failed: reason=" + reason, e);
            }
        });
    }

    private void checkServerTimerDrivenActions() {
        long dueAt = AppVars.NeverTimer;
        if (dueAt <= 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now + SERVER_TIMER_TICK_MARGIN_MS < dueAt) {
            return;
        }

        if (dueAt == lastServerTimerDrivenReloadDueAtMs
                && (now - lastServerTimerDrivenReloadAtMs) < SERVER_TIMER_TICK_DEDUP_MS) {
            return;
        }
        if (now < navTickNetworkBackoffUntilMs) {
            Log.d(TAG, "SERVER_TIMER_TICK backoff active: waitMs=" + (navTickNetworkBackoffUntilMs - now)
                    + ", dueAt=" + dueAt);
            return;
        }

        AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(this);
        boolean autoMoving = AppVars.AutoMoving;
        boolean autoFish = autoFunctionsManager.isAutoFishEnabled();
        if (!autoMoving && !autoFish) {
            return;
        }
        if (AppVars.TreasureDigPauseNonCombatAutoFunctions) {
            Log.d(TAG, "SERVER_TIMER_TICK skip: treasure dig preparation pause is active"
                    + ", autoMoving=" + autoMoving
                    + ", dueAt=" + dueAt);
            return;
        }
        // Блокируем фоновый probe во время критической последовательности рыбалки (act=1→act=2).
        // Без этой защиты фоновый main.php?go=inf перезагружает PHPSESSID и портит vcode.
        if (AppVars.suppressBackgroundProbesDuringFishing) {
            long timeSinceStartMs = System.currentTimeMillis() - AppVars.fishingSequenceStartAtMs;
            long dynamicTimeoutMs = AppVars.fishingExpectedDurationMs; // динамический таймаут по навыку
            if (timeSinceStartMs < dynamicTimeoutMs) {
                Log.d(TAG, "SERVER_TIMER_TICK skip: fishing sequence in progress (duration=" + timeSinceStartMs + "ms"
                        + ", timeout=" + dynamicTimeoutMs + "ms)"
                        + ", dueAt=" + dueAt);
                return;
            } else {
                // Timeout - очищаем флаг на случай, если act=2 не прошел (сервер long-polling навыка)
                Log.w(TAG, "SERVER_TIMER_TICK TIMEOUT: clearing lost fishing suppression flag after " + timeSinceStartMs + "ms"
                        + " (timeout=" + dynamicTimeoutMs + "ms)");
                AppVars.suppressBackgroundProbesDuringFishing = false;
                // Сбрасываем NeverTimer чтобы авто-рыбалка могла перезапуститься
                // (без этого NeverTimer застревает от go=inf wtime обновлений)
                AppVars.NeverTimer = 0L;
            }
        }
        if (AppVars.IsFightCaptchaDialogVisible) {
            return;
        }

        String contentMainPhp = AppVars.ContentMainPhp;
        boolean hasFightHtml = contentMainPhp != null
                && (contentMainPhp.contains("var fight_ty") || contentMainPhp.contains("magic_slots();"));
        if (hasFightHtml) {
            return;
        }

        if (binding == null || binding.appBarMain == null || binding.appBarMain.contentMain == null
                || binding.appBarMain.contentMain.webView == null) {
            return;
        }

        String currentUrl = "";
        try {
            String value = binding.appBarMain.contentMain.webView.getUrl();
            currentUrl = value == null ? "" : value;
        } catch (Exception ignored) {
            currentUrl = "";
        }

        String reloadUrl;
        if (autoMoving) {
            reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_nav_tick=1&r=" + now;
        } else {
            // КРИТИЧНО: af_tick никогда не должен отправляться БЕЗ vcode!
            // Если vcode пуст → происходит загрузка БЕЗ vcode → сервер обновляет PHPSESSID → старый vcode невалиден
            // ⚠️ ЗАЩИТА: при активной рыбалке не отправляем af_tick, даже если vcode есть
            if (AppVars.suppressBackgroundProbesDuringFishing) {
                // Рыбалка все еще идет - не отправляем af_tick, перезагружаем озеро для получения vcode
                reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=ret&r=" + now;
                Log.w(TAG, "SERVER_TIMER_TICK: fishing still active! Reloading lake for fresh vcode instead of af_tick.");
            } else {
                // RULE 5: VCode получается через SessionManager
                String vcode = SessionManager.getInstance().getValidVCodeForAction("server_timer_tick_af_tick");
                if (vcode == null || vcode.isEmpty()) {
                    // Vcode пуст - нужна защита: загружаем озеро чтобы получить свежий vcode
                    reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=ret&r=" + now;
                    Log.w(TAG, "SERVER_TIMER_TICK: VCode пуст при af_tick! Загружаем озеро для получения vcode.");
                } else {
                    reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&af_tick=1&r=" + now + "&vcode=" + vcode;
                }
            }
        }

        lastServerTimerDrivenReloadAtMs = now;
        lastServerTimerDrivenReloadDueAtMs = dueAt;
        // Локальный anti-loop guard до получения следующего server cooldown.
        AppVars.NeverTimer = now + 1500L;

        Log.d(TAG, "SERVER_TIMER_TICK reload: autoMoving=" + autoMoving
                + ", autoFish=" + autoFish
                + ", dueAt=" + dueAt
                + ", currentUrl=" + currentUrl
                + ", reloadUrl=" + reloadUrl);
        binding.appBarMain.contentMain.webView.loadUrl(reloadUrl);
    }

    private void syncQuickButtonsRuntimeState() {
        if (quickButtonsPanel == null) {
            return;
        }
        boolean autoMovingNow = AppVars.AutoMoving;
        if (autoMovingNow == lastQuickPanelAutoMovingState) {
            return;
        }
        lastQuickPanelAutoMovingState = autoMovingNow;
        quickButtonsPanel.refreshActionStates();
        Log.d(TAG, "QUICK_UI_SYNC: AutoMoving changed -> refresh quick buttons, state=" + autoMovingNow);
    }

    public void refreshQuickButtonsPanelState(String reason) {
        if (quickButtonsPanel == null) {
            return;
        }
        quickButtonsPanel.refreshActionStates();
        Log.d(TAG, "QUICK_UI_SYNC: refresh requested, reason=" + reason);
    }
    
    public void invalidateQuickButtonsUI() {
        if (quickButtonsPanel == null) {
            return;
        }
        quickButtonsPanel.refreshActionStates();
        Log.d(TAG, "QUICK_UI_SYNC: invalidateQuickButtonsUI called -> refresh");
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

    /**
     * Выполняет серверный logout и переводит приложение на экран входа.
     *
     * Поток:
     * - best-effort `GET /exit.php` с cookie текущей сессии;
     * - очистка локальной сессии (WebView + OkHttp cookie jar);
     * - переход в LoginActivity с очисткой back stack.
     */
    private void performLogoutToLogin() {
        if (isExiting) {
            return;
        }
        Log.i(TAG, "LOGOUT_FLOW: started from navigation drawer");
        new Thread(() -> {
            performLogoutRequestBestEffort();
            runOnUiThread(this::finalizeLogoutAndOpenLogin);
        }, "logout-flow").start();
    }

    /**
     * Серверный выход: `GET http://neverlands.ru/exit.php` с Referer как в браузере.
     */
    private void performLogoutRequestBestEffort() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(LOGOUT_URL);
            Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                Log.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, skip server logout request");
                return;
            }

            connection = activeProxy != null
                    ? (HttpURLConnection) url.openConnection(activeProxy)
                    : (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(LOGOUT_HTTP_TIMEOUT_MS);
            connection.setReadTimeout(LOGOUT_HTTP_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Referer", LOGOUT_REFERER);
            connection.setRequestProperty("User-Agent", AppVars.BROWSER_USER_AGENT);

            String cookie = CookieManager.getInstance().getCookie(LOGOUT_URL);
            if (cookie == null || cookie.isEmpty()) {
                cookie = CookieManager.getInstance().getCookie("http://neverlands.ru/");
            }
            if ((cookie == null || cookie.isEmpty()) && url.getHost() != null) {
                cookie = CookiesManager.obtain(url.getHost());
            }
            if (cookie != null && !cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            } else {
                Log.w(TAG, "LOGOUT_FLOW: cookie is empty for server logout request");
            }

            int responseCode = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            Log.i(TAG, "LOGOUT_FLOW: exit.php responseCode=" + responseCode
                    + ", location=" + (location == null ? "" : location));
        } catch (Exception e) {
            Log.w(TAG, "LOGOUT_FLOW: server logout request failed", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Локальная очистка и возврат на LoginActivity.
     */
    private void finalizeLogoutAndOpenLogin() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        isExiting = true;
        AppVars.lastCookies = null;
        NetworkClient.clearCookies();
        CookiesManager.clear();
        try {
            CookieManager manager = CookieManager.getInstance();
            manager.removeSessionCookies(null);
            manager.removeAllCookies(null);
            manager.flush();
        } catch (Throwable t) {
            Log.w(TAG, "LOGOUT_FLOW: WebView cookie cleanup failed", t);
        }

        AutoModeForegroundService.syncServiceState(this, "logout_to_login");
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
            FileLogger.log("Page loaded: " + url);
            if (binding != null && binding.appBarMain != null && binding.appBarMain.contentMain != null
                    && view == binding.appBarMain.contentMain.webView) {
                AppVars.url_main_top = url != null ? url : "";
                if (isNeverlandsHostUrl(url)) {
                    resetNavTickNetworkBackoff("main_frame_loaded");
                }
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

            String compassCellRegNum = extractCompassCellRegNumFromUrl(url);
            if (!compassCellRegNum.isEmpty()) {
                try {
                    AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(MainActivity.this);
                    if (autoFunctionsManager.isAutoCompassEnabled()) {
                        autoFunctionsManager.setAutoCompassEnabled(false);
                    }
                    autoFunctionsManager.startAutoMoving(compassCellRegNum);
                    Log.d(TAG, "shouldOverrideUrlLoading: compass cell link -> startAutoMoving "
                            + compassCellRegNum + ", sourceUrl=" + url);
                } catch (Exception e) {
                    Log.e(TAG, "shouldOverrideUrlLoading: failed to start navigation from compass link: " + url, e);
                }
                return true;
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
            boolean isMainGameWebView = binding != null
                    && binding.appBarMain != null
                    && binding.appBarMain.contentMain != null
                    && view == binding.appBarMain.contentMain.webView;

            if (isMainGameWebView && isNeverlandsHost && isManualMainNavigationUrl(lowerUrl)) {
                suppressAutoTurnServerProbeForManualNavigation(url);
            }

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
            // ✅ API 23 compatibility check for WebResourceError methods
            int errorCode = Integer.MIN_VALUE;
            String description = "";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && error != null) {
                errorCode = error.getErrorCode();
                if (error.getDescription() != null) {
                    description = error.getDescription().toString();
                }
            }

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
            // DEPRECATED: RoomManager.startTracing() removed - HTML injection in Filter/RoomManager handles player list
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
                // RULE 5: VCode получается через SessionManager
                String vcode = SessionManager.getInstance().getValidVCodeForAction("autoskin_reload");
                if (vcode != null && !vcode.isEmpty()) {
                    reloadUrl += "&vcode=" + vcode;
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
        if (isTransientNetworkDropError(errorCode, description)) {
            onMainFrameTransientNetworkError(failingUrl, errorCode, description);
        }
        if (!isRetryableMainFrameError(errorCode, description)) {
            return;
        }

        String timeoutContext = buildMainFrameTimeoutContext();
        long now = System.currentTimeMillis();
        if (failingUrl.equals(lastMainFrameTimeoutRetryUrl)
                && (now - lastMainFrameTimeoutRetryAtMs) < MAINFRAME_TIMEOUT_RETRY_DEDUP_MS) {
            Log.w(TAG, "onReceivedError: network retry skipped by dedup, url=" + failingUrl
                    + ", context={" + timeoutContext + "}");
            return;
        }

        lastMainFrameTimeoutRetryUrl = failingUrl;
        lastMainFrameTimeoutRetryAtMs = now;
        Log.w(TAG, "onReceivedError: transient main-frame error, retry in "
                + MAINFRAME_TIMEOUT_RETRY_DELAY_MS + "ms, url=" + failingUrl
                + ", context={" + timeoutContext + "}");

        view.postDelayed(() -> {
            if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                return;
            }
            view.loadUrl(failingUrl);
        }, MAINFRAME_TIMEOUT_RETRY_DELAY_MS);
    }

    private void onMainFrameTransientNetworkError(String failingUrl, int errorCode, String description) {
        boolean autoFishEnabled = false;
        try {
            autoFishEnabled = AutoFunctionsManager.getInstance(this).isAutoFishEnabled();
        } catch (Exception ignored) {
            autoFishEnabled = false;
        }
        if (!AppVars.AutoMoving && !autoFishEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        if (navTickErrorBurstWindowStartMs <= 0L
                || (now - navTickErrorBurstWindowStartMs) > NAV_TICK_ERROR_BURST_WINDOW_MS) {
            navTickErrorBurstWindowStartMs = now;
            navTickErrorBurstCount = 0;
        }
        navTickErrorBurstCount++;

        if (navTickErrorBurstCount >= NAV_TICK_ERROR_BURST_THRESHOLD) {
            long newBackoffUntil = now + NAV_TICK_NETWORK_BACKOFF_MS;
            if (newBackoffUntil > navTickNetworkBackoffUntilMs) {
                navTickNetworkBackoffUntilMs = newBackoffUntil;
            }
        }

        Log.w(TAG, "SERVER_TIMER_TICK network failure: url=" + failingUrl
                + ", code=" + errorCode
                + ", desc=" + description
                + ", burstCount=" + navTickErrorBurstCount
                + ", backoffRemainMs=" + Math.max(0L, navTickNetworkBackoffUntilMs - now));
    }

    private void resetNavTickNetworkBackoff(String reason) {
        if (navTickNetworkBackoffUntilMs == 0L
                && navTickErrorBurstWindowStartMs == 0L
                && navTickErrorBurstCount == 0) {
            return;
        }
        navTickNetworkBackoffUntilMs = 0L;
        navTickErrorBurstWindowStartMs = 0L;
        navTickErrorBurstCount = 0;
        Log.d(TAG, "SERVER_TIMER_TICK backoff reset: reason=" + reason);
    }

    private boolean isTransientNetworkDropError(int errorCode, String description) {
        if (errorCode == WebViewClient.ERROR_TIMEOUT
                || errorCode == WebViewClient.ERROR_CONNECT
                || errorCode == WebViewClient.ERROR_IO
                || errorCode == WebViewClient.ERROR_HOST_LOOKUP) {
            return true;
        }
        if (description == null || description.isEmpty()) {
            return false;
        }
        String lower = description.toLowerCase(Locale.ROOT);
        return lower.contains("err_empty_response")
                || lower.contains("unexpected end of stream")
                || lower.contains("econnrefused")
                || lower.contains("err_connection_refused")
                || lower.contains("err_connection_closed")
                || lower.contains("connection reset")
                || lower.contains("failed to connect")
                || lower.contains("connection aborted");
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
    private boolean isRetryableMainFrameError(int errorCode, String description) {
        if (errorCode == WebViewClient.ERROR_TIMEOUT) {
            return true;
        }
        if (isTransientNetworkDropError(errorCode, description)) {
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
