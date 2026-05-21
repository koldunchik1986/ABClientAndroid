package ru.neverlands.anclient;

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

import org.json.JSONException;
import org.json.JSONObject;

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

import ru.neverlands.anclient.bridge.WebAppInterface;
import ru.neverlands.anclient.handlers.FightContextChoiceHandler;
import ru.neverlands.anclient.handlers.SessionReloginHandler;
import ru.neverlands.anclient.lez.LezFight;
import ru.neverlands.anclient.license.LicenseFeature;
import ru.neverlands.anclient.license.LicenseRuntime;
import ru.neverlands.anclient.license.LicenseSession;
import ru.neverlands.anclient.manager.AntiCaptchaManager;
import ru.neverlands.anclient.manager.AutoCutManager;
import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.manager.AppTimerManager;
import ru.neverlands.anclient.manager.ContactsManager;
import ru.neverlands.anclient.databinding.ActivityMainBinding;
import ru.neverlands.anclient.manager.ProfessionRatingMonitor;
import ru.neverlands.anclient.manager.TabManager;
import ru.neverlands.anclient.manager.RoomManager;
import ru.neverlands.anclient.model.Position;
import ru.neverlands.anclient.model.QuickActionType;
import ru.neverlands.anclient.model.UserConfig;
import ru.neverlands.anclient.network.NetworkClient;
import ru.neverlands.anclient.proxy.CookiesManager;
import ru.neverlands.anclient.proxy.ProxyRuntimeManager;
import ru.neverlands.anclient.postfilter.FightAuto;
import ru.neverlands.anclient.postfilter.MainPhp;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.ExtMap;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.Chat;
import ru.neverlands.anclient.utils.LogcatFileRecorder;
import ru.neverlands.anclient.utils.MapPath;
import ru.neverlands.anclient.utils.RuntimeNetTrace;
import ru.neverlands.anclient.utils.SessionManager;
import ru.neverlands.anclient.utils.Russian;
import ru.neverlands.anclient.webview.WebViewRequestInterceptor;
import ru.neverlands.anclient.service.AutoModeForegroundService;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Observer;
import ru.neverlands.anclient.ui.viewmodel.FightViewModel;
import ru.neverlands.anclient.ui.QuickButtonsPanel;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
    private static final String BUILD_MARKER = "2026-02-27_01-34";
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1002;
    private static final int FRAME_FONT_SCALE_MIN = 50;
    private static final int FRAME_FONT_SCALE_MAX = 200;
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
    private static final long AUTO_BATTLE_LEGACY_RANDOM_MIN_DELAY_MS = 1000L;
    private static final long AUTO_BATTLE_LEGACY_RANDOM_DELAY_RANGE_MS = 1001L;
    private static final long AUTO_BATTLE_DUPLICATE_SUBMIT_SUPPRESS_MS = 5000L;
    private static final long CAPTCHA_IMAGE_STABILIZE_DELAY_MS = 180L;
    /**
     * TTL fallback-адреса картинки боевой капчи, перехваченной сетевым контуром раньше UI.
     *
     * Назначение:
     * - дать `restorePendingFightCaptchaDialogIfNeeded()` короткое окно, чтобы восстановить popup,
     *   если `AppVars.CodeAddress` ещё пустой, но `WebViewRequestInterceptor` уже сохранил
     *   `AppVars.LastFightCaptchaImageUrl` / `AppVars.LastFightCaptchaImageAtMs`.
     *
     * Зависимости:
     * - `AppVars.LastFightCaptchaImageUrl` и `AppVars.LastFightCaptchaImageAtMs`;
     * - `resolvePendingFightCaptchaUrlForRestore()` как единственная UI-точка fallback-восстановления;
     * - `AutoModeForegroundService.resolveFightCaptchaUrlForPopup(...)` использует такой же TTL в фоне.
     */
    private static final long FIGHT_CAPTCHA_CAPTURE_FALLBACK_TTL_MS = 5_000L;
    private static final int CAPTCHA_IMAGE_MIN_USABLE_BYTES = 1024;
    private static final int ANTI_CAPTCHA_CREATE_RETRY_MAX_COUNT = 5;
    private static final long ANTI_CAPTCHA_CREATE_RETRY_BASE_DELAY_MS = 10_000L;
    private static final Pattern MAP_DECL_PATTERN = Pattern.compile(
            "var\\s+map\\s*=\\s*\\[\\s*\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MAPBT_LOOK_PATTERN = Pattern.compile(
            "\\[\\s*\"(?:look|ogl)\"\\s*,\\s*\"[^\"]*\"\\s*,\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final long CAPTCHA_NETWORK_FALLBACK_DELAY_MS = 900L;
    private static final int ANTI_CAPTCHA_IMAGE_WAIT_MAX_RETRIES = 30;
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
    /**
     * Окно, в котором серверный сигнал "main_top должен открыть main.php" считается fight-сигналом.
     *
     * Назначение:
     * - не запускать постоянный idle-probe во время `AppVars.AutoMoving`;
     * - но разрешить разовый recovery server-probe, когда `ch.php?show=1` сообщил
     *   `top.frames['main_top'].location='main.php'` и включён авто-бой.
     *
     * Зависимости:
     * - `WebViewRequestInterceptor.hasMainTopMainPhpReloadSignal(...)` находит серверный JS-сигнал;
     * - `onChatPollResponseMeta(...)` переносит сигнал в `AppVars.LastFightAnnounceAtMs`;
     * - `shouldSkipAutoTurnServerProbeForMapAutomation()` читает этот TTL перед подавлением probe.
     */
    private static final long AUTO_TURN_FIGHT_ANNOUNCE_PROBE_GRACE_MS = 25_000L;
    private static final long PENDING_FINISH_REPEAT_WINDOW_MS = 6000L;
    private static final int PENDING_FINISH_REPEAT_LIMIT = 4;
    private static final int AUTO_TURN_SERVER_PROBE_TIMEOUT_MS = 12000;
    // C# parity: в ПК-версии NeverTimer write-only (нет аналога checkServerTimerDrivenActions).
    // Margin=0 означает, что Java TICK срабатывает ПОСЛЕ истечения таймера, а не на 300мс раньше.
    // Это устраняет преждевременные шаги навигации (сервер отвечает ERR если таймер не истёк).
    private static final long SERVER_TIMER_TICK_MARGIN_MS = 0L;
    private static final long SERVER_TIMER_TICK_DEDUP_MS = 4000L;
    private static final long NAV_TICK_NETWORK_BACKOFF_MS = 8000L;
    private static final long NAV_TICK_ERROR_BURST_WINDOW_MS = 6000L;
    private static final int NAV_TICK_ERROR_BURST_THRESHOLD = 2;
    private static final String LOGOUT_URL = "http://neverlands.ru/exit.php";
    private static final String LOGOUT_REFERER = "http://neverlands.ru/game.php";
    private static final int LOGOUT_HTTP_TIMEOUT_MS = 10000;
    private static final long SESSION_RELOGIN_DEDUP_MS = 15000L;
    private static final String SESSION_RELOGIN_CHAIN = "session_relogin";
    public ActivityMainBinding binding;
    private Timer timer;
    private boolean isExiting = false;
    // DEPRECATED: RoomManager.startTracing() no longer needed after HTML injection fix
    // private boolean isRoomManagerStarted = false;
    private FightViewModel fightViewModel;
    private TabManager tabManager;
    private QuickButtonsPanel quickButtonsPanel;
    private WebView chatRefrWebView;
    private int appliedMainFrameFontScale = -1;
    private int appliedChatFrameFontScale = -1;
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
    private boolean antiCaptchaInFlight = false;
    private String antiCaptchaChallengeKey = "";
    private int antiCaptchaImageWaitAttempts = 0;
    private int antiCaptchaCreateRetryAttempts = 0;
    private Runnable antiCaptchaRetryRunnable;
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
    private String lastAutoBattleSubmitPayloadKey = "";
    private final Handler autoBattleDelayHandler = createMainHandler();
    private Runnable pendingAutoBattleSubmitRunnable;
    private String pendingAutoBattleSubmitPayload = "";
    private Observer<String> fightSubmitObserver;
    private final Object autoTurnServerProbeLock = new Object();
    private volatile boolean autoTurnServerProbeInFlight = false;
    private volatile long lastAutoTurnServerProbeAtMs = 0L;
    private volatile long autoTurnManualNavSuppressUntilMs = 0L;
    private String lastPendingFinishAbsoluteUrl = "";
    private long lastPendingFinishAtMs = 0L;
    private int pendingFinishRepeatCount = 0;
    // true между onResume/onPause; используется для отключения server-probe в активном UI.
    private volatile boolean isActivityResumedState = false;
    private boolean suppressBackgroundLoopsForContacts = false;
    private boolean suppressChatRefreshOnceAfterContacts = false;
    private boolean suppressRoomRefreshOnceAfterContacts = false;
    private boolean shouldRestoreChatRefreshAfterContacts = false;
    private boolean sessionReloginInFlight = false;
    private long lastSessionReloginStartAtMs = 0L;
    private String lastSessionReloginUrl = "";
    private boolean restoreChatRefreshAfterSessionRelogin = false;
    private boolean restoreRoomPollingAfterSessionRelogin = false;
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
        byte[] body = data == null ? new byte[0] : ru.neverlands.anclient.utils.Russian.getBytes(data);
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
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestRoomUsersRefreshSoon: skip binding=null");
            return;
        }
        WebView chatUsersWebView = binding.appBarMain.contentMain.chatUsersWebview;
        if (chatUsersWebView == null) {
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestRoomUsersRefreshSoon: skip chatUsersWebView=null");
            return;
        }
        if (suppressRoomRefreshOnceAfterContacts) {
            suppressRoomRefreshOnceAfterContacts = false;
            AppLog.d("contacts_nav", TAG, BG_TRACE_PREFIX + " requestRoomUsersRefreshSoon: skipped once after contacts");
            return;
        }

        long now = System.currentTimeMillis();
        if (now < roomUsersRefreshSuppressedUntilMs) {
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestRoomUsersRefreshSoon: suppressed by chat-recovery, waitMs="
                    + (roomUsersRefreshSuppressedUntilMs - now));
            return;
        }
        if (now - lastRoomUsersRefreshAtMs < ROOM_USERS_REFRESH_MIN_INTERVAL_MS) {
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestRoomUsersRefreshSoon: throttled, deltaMs="
                    + (now - lastRoomUsersRefreshAtMs));
            return;
        }

        String roomUrl = "http://neverlands.ru/ch.php?lo=1&" + now;
        AppLog.d(TAG, "[AA_TRACE] requestRoomUsersRefreshSoon: " + roomUrl);
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
            AppLog.w(TAG, BG_TRACE_PREFIX + " " + stage + ": failed to read auto flags", e);
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
            AppLog.w(TAG, BG_TRACE_PREFIX + " " + stage + ": failed to read power state", e);
        }

        AppLog.d(TAG, BG_TRACE_PREFIX + " " + stage
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
        AppLog.d(TAG, BG_TRACE_PREFIX + " registerAppBroadcastReceiverIfNeeded: registered");
    }

    private void unregisterAppBroadcastReceiverIfNeeded() {
        if (!appBroadcastReceiverRegistered) {
            return;
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        appBroadcastReceiverRegistered = false;
        AppLog.d(TAG, BG_TRACE_PREFIX + " unregisterAppBroadcastReceiverIfNeeded: unregistered");
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
        AppLog.d(TAG, BG_TRACE_PREFIX + " registerScreenStateReceiverIfNeeded: registered");
    }

    private void unregisterScreenStateReceiverIfNeeded() {
        if (!screenStateReceiverRegistered) {
            return;
        }
        unregisterReceiver(screenStateReceiver);
        screenStateReceiverRegistered = false;
        AppLog.d(TAG, BG_TRACE_PREFIX + " unregisterScreenStateReceiverIfNeeded: unregistered");
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
        AppLog.d(TAG, BG_TRACE_PREFIX + " restartRoomUsersPolling");
        stopRoomUsersPolling();
        startRoomUsersPolling();
    }

    private void startRoomUsersPolling() {
        AppLog.d(TAG, BG_TRACE_PREFIX + " startRoomUsersPolling: begin, hasRunnable="
                + (roomUsersPollingRunnable != null));
        if (roomUsersPollingRunnable != null) {
            roomUsersPollingHandler.removeCallbacks(roomUsersPollingRunnable);
        }
        roomUsersPollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) {
                    AppLog.d(TAG, BG_TRACE_PREFIX + " roomUsersPolling: stop due to finishing/destroyed");
                    return;
                }
                AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(MainActivity.this);
                if (!autoFunctionsManager.isLocationTrackingEnabled()) {
                    AppLog.d(TAG, BG_TRACE_PREFIX + " roomUsersPolling: skip tick, locationTracking=false");
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
            AppLog.d(TAG, BG_TRACE_PREFIX + " startRoomUsersPolling: first post");
            roomUsersPollingHandler.post(roomUsersPollingRunnable);
        } else {
            AppLog.d(TAG, BG_TRACE_PREFIX + " startRoomUsersPolling: locationTracking disabled");
        }
    }

    private void stopRoomUsersPolling() {
        if (roomUsersPollingRunnable != null) {
            roomUsersPollingHandler.removeCallbacks(roomUsersPollingRunnable);
            roomUsersPollingRunnable = null;
            AppLog.d(TAG, BG_TRACE_PREFIX + " stopRoomUsersPolling: stopped");
        } else {
            AppLog.d(TAG, BG_TRACE_PREFIX + " stopRoomUsersPolling: already stopped");
        }
    }

    public void requestAutoSelect() {
        AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoSelect: start");
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
     * - активный captcha popup Авто-Травника (`alchemy_ajax.php?act=3`) не считается боевой captcha:
     *   stale popup закрывается, чтобы не блокировать event-driven автоход в новом бою.
     */
    public void requestImmediateAutoTurnOnFightAnnounce() {
        if (AppVars.IsFightCaptchaDialogVisible) {
            if (isActiveAlchemyCaptchaDialog()) {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    runOnUiThread(this::requestImmediateAutoTurnOnFightAnnounce);
                    return;
                }
                dismissActiveAlchemyCaptchaForFight("fight_announce");
            } else {
                AppLog.d(TAG, BG_TRACE_PREFIX + " requestImmediateAutoTurnOnFightAnnounce: skip, fight captcha dialog visible");
                return;
            }
        }

        AppLog.d(TAG, BG_TRACE_PREFIX + " requestImmediateAutoTurnOnFightAnnounce: triggered by fight announcement");

        // Используем фоновый механизм, т.к. в момент анонсации UI может быть неинтерактивным
        requestAutoTurnBackgroundAware();
    }

    /**
     * true только для captcha popup Авто-Травника, который нельзя считать боевой captcha.
     *
     * Зависимости:
     * - `activeFightCaptchaDialog`/`activeFightFinishUrl` исторически общие для fight/fish/alchemy captcha;
     * - `FightViewModel.isBlockingFightCaptchaVisible()` и `requestImmediateAutoTurnOnFightAnnounce()`
     *   используют этот метод, чтобы AutoCut popup не стопорил автобой;
     * - manual fallback сохраняется: popup закрывается только при боевом action/recovery, а не при обычном UI idle.
     */
    public boolean isActiveAlchemyCaptchaDialog() {
        return activeFightCaptchaDialog != null
                && activeFightCaptchaDialog.isShowing()
                && isAlchemyCaptchaFinishUrl(activeFightFinishUrl);
    }

    /**
     * Распознаёт finishUrl captcha-среза Авто-Травника.
     *
     * Контракт: проверяем endpoint и `act=3`, потому именно этот запрос отправляет код captcha
     * в `AlchemyAjaxPhp`; обычные fight captcha идут через `main.php?get_id=61&act=7`.
     */
    private boolean isAlchemyCaptchaFinishUrl(String finishUrl) {
        return finishUrl != null
                && finishUrl.contains("/gameplay/ajax/alchemy_ajax.php")
                && finishUrl.contains("act=3");
    }

    /**
     * Закрывает stale captcha popup Авто-Травника перед боевым автоходом.
     *
     * Зависимости:
     * - вызывается только после `isActiveAlchemyCaptchaDialog()`;
     * - очищает `CodeAddress` и `IsFightCaptchaDialogVisible`, чтобы guard-ы автобоя не видели
     *   alchemy popup как настоящую боевую captcha;
     * - не трогает `FightLink`, потому alchemy captcha не владеет боевым finish link.
     */
    private void dismissActiveAlchemyCaptchaForFight(String reason) {
        if (activeFightCaptchaDialog == null || !activeFightCaptchaDialog.isShowing()) {
            return;
        }
        AppLog.w(TAG, BG_TRACE_PREFIX + " dismiss stale AutoCut captcha before fight auto-turn, reason=" + reason
                + ", finishUrl=" + activeFightFinishUrl);
        AppVars.CodeAddress = "";
        activeFightCaptchaDialog.dismiss();
        AppVars.IsFightCaptchaDialogVisible = false;
    }

    /**
     * Принимает метаданные ответа `ch.php?show=1` из перехватчика WebView.
     *
     * Зависимости:
     * - источник вызова: `WebViewRequestInterceptor` (каждый `show=1` ответ);
     * - `WebViewRequestInterceptor.hasMainTopMainPhpReloadSignal(...)`: распознаёт серверный JS
     *   `top.frames['main_top'].location='main.php'` как ранний признак боя/капчи;
     * - `AppVars.LastFightAnnounceAtMs`: единый runtime-сигнал для `AutoModeForegroundService`
     *   и `requestAutoTurnFromServerProbe(...)`;
     * - управляет временным подавлением `requestRoomUsersRefreshSoon()` и планирует
     *   ускоренный recovery-ретрай chat poll без перезапуска общего periodic runnable.
     *
     * Назначение:
     * - уменьшить вероятность пропуска системных сообщений (например, событий Босса),
     *   когда один poll-тик падает с HTTP 546/535 или пустым body.
     * - не выполнять прямую навигацию из chat-poll callback: метод только ставит fight-сигнал,
     *   а существующие decision points (`AutoModeForegroundService` и `requestAutoTurn...`) сами
     *   решают, когда безопасно делать sync/probe.
     *
     * @param hasMainTopMainPhpReload true, если в ответе `ch.php?show=1` найден server-side reload
     *                                верхнего фрейма на `main.php`.
     */
    public void onChatPollResponseMeta(
            int httpCode,
            int rawBytes,
            boolean hasAddMsg,
            boolean hasSetLmid,
            boolean hasMainTopMainPhpReload,
            String url) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> onChatPollResponseMeta(
                    httpCode,
                    rawBytes,
                    hasAddMsg,
                    hasSetLmid,
                    hasMainTopMainPhpReload,
                    url));
            return;
        }

        boolean pollFailed = httpCode >= 535 || rawBytes <= 0;
        long now = System.currentTimeMillis();

        // Переносим server/chat signal в уже существующий `LastFightAnnounceAtMs` вместо создания
        // нового флага: так `AutoModeForegroundService` и `requestAutoTurnFromServerProbe(...)`
        // используют тот же путь, что и обычный чатовый анонс "Нападение".
        if (hasMainTopMainPhpReload && isAutoFightRuntimeEnabled()) {
            AppVars.LastFightAnnounceAtMs = now;
            AppLog.d("chat_poll", TAG, BG_TRACE_PREFIX + " chat-poll main_top reload signal -> fight announce pulse"
                    + ", code=" + httpCode
                    + ", bytes=" + rawBytes
                    + ", url=" + url);
        }

        if (!pollFailed) {
            if (consecutiveChatPollFailures > 0) {
                AppLog.d(TAG, BG_TRACE_PREFIX + " chat-poll recovered: code=" + httpCode
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
            AppLog.w(TAG, BG_TRACE_PREFIX + " chat-poll degraded: failed to read autoBoss flag", e);
        }
        roomUsersRefreshSuppressedUntilMs = Math.max(
                roomUsersRefreshSuppressedUntilMs,
                now + ROOM_USERS_SUPPRESS_AFTER_CHAT_FAIL_MS
        );

        AppLog.w(TAG, BG_TRACE_PREFIX + " chat-poll degraded: code=" + httpCode
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
            AppLog.d(TAG, BG_TRACE_PREFIX + " chat-poll recovery retry: manual tick");
            requestChatRefresh(false);
        };
        chatRefreshHandler.postDelayed(chatPollRecoveryRunnable, retryDelayMs);
    }

    /**
     * Запускает восстановление игровой сессии после HTML-страницы `css/error.css`.
     *
     * Вызовы приходят из общего WebView response pipeline и могут повториться из нескольких
     * фреймов, поэтому здесь расположен единый debounce и orchestration.
     */
    public void onSessionErrorHtmlDetected(String url, String source) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> onSessionErrorHtmlDetected(url, source));
            return;
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }

        long now = System.currentTimeMillis();
        String safeUrl = url == null ? "" : url;
        String safeSource = source == null ? "" : source;
        if (sessionReloginInFlight) {
            AppLog.d(SESSION_RELOGIN_CHAIN, TAG, "SESSION_RELOGIN_SKIP: already in flight, source="
                    + safeSource + ", url=" + safeUrl + ", firstUrl=" + lastSessionReloginUrl);
            return;
        }
        if (now - lastSessionReloginStartAtMs < SESSION_RELOGIN_DEDUP_MS) {
            AppLog.d(SESSION_RELOGIN_CHAIN, TAG, "SESSION_RELOGIN_SKIP: dedup window, source="
                    + safeSource + ", url=" + safeUrl);
            return;
        }

        sessionReloginInFlight = true;
        lastSessionReloginStartAtMs = now;
        lastSessionReloginUrl = safeUrl;
        restoreChatRefreshAfterSessionRelogin = chatRefreshRunnable != null;
        restoreRoomPollingAfterSessionRelogin = roomUsersPollingRunnable != null;

        AppLog.w(SESSION_RELOGIN_CHAIN, TAG, "SESSION_RELOGIN_DETECTED: source=" + safeSource
                + ", url=" + safeUrl
                + ", restoreChat=" + restoreChatRefreshAfterSessionRelogin
                + ", restoreRoom=" + restoreRoomPollingAfterSessionRelogin);
        MainPhp.postServerNotificationToChat(
                "Сеанс работы прерван. Перезаход в игру",
                "session_relogin",
                safeUrl
        );

        stopChatRefresh();
        stopRoomUsersPolling();
        clearPendingAutoBattleSubmit();

        SessionReloginHandler.start(getApplicationContext(), AppVars.Profile, new SessionReloginHandler.Callback() {
            @Override
            public void onReloginSuccess(List<java.net.HttpCookie> cookies) {
                handleSessionReloginSuccess(cookies, safeUrl);
            }

            @Override
            public void onReloginFallbackRequired(String reason) {
                handleSessionReloginFallback(reason, safeUrl);
            }
        });
    }

    private void handleSessionReloginSuccess(List<java.net.HttpCookie> cookies, String sourceUrl) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> handleSessionReloginSuccess(cookies, sourceUrl));
            return;
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }

        sessionReloginInFlight = false;
        AppLog.i(SESSION_RELOGIN_CHAIN, TAG, "SESSION_RELOGIN_SUCCESS: cookies="
                + (cookies == null ? 0 : cookies.size()) + ", sourceUrl=" + sourceUrl);

        if (AppVars.Profile != null) {
            AppVars.Profile.LastLogin = currentDotNetTicksForSessionRelogin();
            AppVars.Profile.save(this);
        }
        applyAuthCookiesToWebView(cookies, "session_relogin");
        SessionManager.getInstance().invalidateContext("session_relogin_success");
        SessionManager.getInstance().clearFightContext();
        AppVars.ContentMainPhp = "";
        AppVars.NextCheckNoConnection = new Date(System.currentTimeMillis() + 5 * 60 * 1000);
        resetPostReloadGuard("session_relogin_success");
        lastMainFrameTimeoutRetryAtMs = 0L;
        lastMainFrameTimeoutRetryUrl = "";

        loadInitialUrls();
        if (restoreChatRefreshAfterSessionRelogin && isChatRefreshEnabled()) {
            startChatRefresh();
        }
        if (restoreRoomPollingAfterSessionRelogin) {
            startRoomUsersPolling();
        }
        restoreChatRefreshAfterSessionRelogin = false;
        restoreRoomPollingAfterSessionRelogin = false;

        MainPhp.postServerNotificationToChat(
                "Сеанс восстановлен",
                "session_relogin",
                sourceUrl == null ? "" : sourceUrl
        );
        AutoModeForegroundService.syncServiceState(this, "session_relogin_success");
    }

    private void handleSessionReloginFallback(String reason, String sourceUrl) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> handleSessionReloginFallback(reason, sourceUrl));
            return;
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }
        sessionReloginInFlight = false;
        restoreChatRefreshAfterSessionRelogin = false;
        restoreRoomPollingAfterSessionRelogin = false;
        String safeReason = reason == null ? "unknown" : reason;
        AppLog.w(SESSION_RELOGIN_CHAIN, TAG, "SESSION_RELOGIN_FALLBACK: reason=" + safeReason
                + ", sourceUrl=" + (sourceUrl == null ? "" : sourceUrl));
        MainPhp.postServerNotificationToChat(
                "Не удалось восстановить сеанс: " + safeReason + ". Открываю экран входа",
                "session_relogin",
                sourceUrl == null ? "" : sourceUrl
        );
        finalizeLogoutAndOpenLogin();
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
            if (isActiveAlchemyCaptchaDialog()) {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    runOnUiThread(() -> requestAutoTurnInternal(allowServerProbeFallback));
                    return;
                }
                dismissActiveAlchemyCaptchaForFight("request_auto_turn");
            } else {
                AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: skip, fight captcha dialog visible");
                return;
            }
        }
        if (shouldPauseAutoBattleForFightCaptcha()) {
            clearPendingAutoBattleSubmit();
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: skip, fight captcha pending");
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
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: app backgrounded, skip WebView evaluateJavascript"
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
        AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: start");
        AppLog.d(TAG, "requestAutoTurn: grabbing current HTML for auto-turn");
        binding.appBarMain.contentMain.webView.evaluateJavascript(
                "(function() { return document.documentElement.innerHTML; })();",
                html -> {
                    if (html != null && !html.equals("null")) {
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        String unquoted = gson.fromJson(html, String.class);
                        AppLog.d(TAG, "requestAutoTurn: html length=" + (unquoted != null ? unquoted.length() : 0));

                        // Проверка на race condition при multi-enemy fight (09:55 issue):
                        // Если HTML слишком мало (<1000 bytes), это означает что WebView еще loading
                        // Добавляем задержку вместо skip чтобы дать WebView время завершить page load
                        if (unquoted != null && unquoted.length() < 1000 && !hasFightMarkers(unquoted)) {
                            String msg = "[FIGHT_RACE_CONDITION] html size too small (WebView loading), size=" + unquoted.length() 
                                    + ". Deferring turn check 200ms";
                            AppLog.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: " + msg);
                            ru.neverlands.anclient.utils.FileLogger.trace("fight_auto", msg);
                            // Откладываем проверку на 200ms чтобы дать WebView время завершить page load
                            new Handler(Looper.getMainLooper()).postDelayed(this::requestAutoTurn, 200);
                            return;
                        }

                        FightContextChoiceHandler.Decision decision = FightContextChoiceHandler.chooseForCurrentHtml(
                                unquoted,
                                AppVars.ContentMainPhp,
                                AppVars.FightLink,
                                allowServerProbeFallback,
                                createFightContextOracle());
                        applyFightContextDecision(decision);
                    } else {
                        AppLog.d(TAG, "requestAutoTurn: html is null");
                        FightContextChoiceHandler.Decision decision = FightContextChoiceHandler.chooseForNullHtml(
                                AppVars.ContentMainPhp,
                                AppVars.FightLink,
                                allowServerProbeFallback,
                                createFightContextOracle());
                        applyFightContextDecision(decision);
                    }
                });
    }

    private FightContextChoiceHandler.Oracle createFightContextOracle() {
        return new FightContextChoiceHandler.Oracle() {
            @Override
            public boolean hasFightMarkers(String html) {
                return MainActivity.this.hasFightMarkers(html);
            }

            @Override
            public boolean isActiveFightContext(String html) {
                return MainActivity.this.isActiveFightContext(html);
            }

            @Override
            public boolean hasPendingAct7FightLink(String fightLink) {
                return MainActivity.this.hasPendingAct7FightLink(fightLink)
                        || MainActivity.this.hasPendingAct7FightLink(AppVars.FightLink);
            }
        };
    }

    private void applyFightContextDecision(FightContextChoiceHandler.Decision decision) {
        if (decision == null) {
            return;
        }
        if (decision.shouldClearCachedHtml()) {
            AppVars.ContentMainPhp = "";
        }
        if (decision.shouldNavigatePendingFinish()) {
            handlePendingFightFinishNavigation();
            return;
        }
        if (decision.shouldRequestProbe()) {
            if (decision.shouldLogProbeIfUiActive() && !isAutoTurnServerProbeAllowedNow()) {
                AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: forcing server probe, reason=" + decision.getProbeReason());
            }
            requestAutoTurnFromServerProbe(decision.getProbeReason());
        }
        if (decision.shouldAutoTurn()) {
            if (shouldPauseAutoBattleForFightCaptcha()) {
                clearPendingAutoBattleSubmit();
                AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: skip autoTurn, fight captcha pending, reason="
                        + decision.getProbeReason());
                return;
            }
            fightViewModel.autoTurnOnce(decision.getAutoTurnHtml());
        }
    }

    private void handlePendingFightFinishNavigation() {
        String finishUrl = AppVars.FightLink;
        if (finishUrl == null || finishUrl.isEmpty()) {
            return;
        }
        AppVars.FightLink = "";
        String absoluteUrl = finishUrl.startsWith("http") ? finishUrl : "http://neverlands.ru/" + finishUrl;
        long nowMs = System.currentTimeMillis();
        if (absoluteUrl.equals(lastPendingFinishAbsoluteUrl)
                && (nowMs - lastPendingFinishAtMs) <= PENDING_FINISH_REPEAT_WINDOW_MS) {
            pendingFinishRepeatCount++;
        } else {
            pendingFinishRepeatCount = 1;
            lastPendingFinishAbsoluteUrl = absoluteUrl;
        }
        lastPendingFinishAtMs = nowMs;

        if (pendingFinishRepeatCount > PENDING_FINISH_REPEAT_LIMIT) {
            AppLog.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: repeated same pending finish link too many times"
                    + ", repeats=" + pendingFinishRepeatCount
                    + ", fallback=plain_main"
                    + ", url=" + absoluteUrl);
            pendingFinishRepeatCount = 0;
            lastPendingFinishAbsoluteUrl = "";
            lastPendingFinishAtMs = 0L;
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/main.php");
            return;
        }

        if (pendingFinishRepeatCount > 1) {
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: suppress duplicate pending finish navigation"
                    + ", repeats=" + pendingFinishRepeatCount
                    + ", url=" + absoluteUrl);
            return;
        }

        AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: navigating to pending finish link: " + absoluteUrl);
        binding.appBarMain.contentMain.webView.loadUrl(absoluteUrl);
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
        if (!isAutoFightRuntimeEnabled()) {
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
        AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: defer first turn for frame render, remainingMs="
                + (AUTO_TURN_FIRST_FRAME_RENDER_GUARD_MS - sinceAnnounceMs)
                + ", sinceAnnounceMs=" + sinceAnnounceMs);
        return true;
    }

    /**
     * Единая проверка runtime-включения авто-боя для UI и background-контуров MainActivity.
     *
     * Назначение:
     * - не дублировать условие `AppVars.Autoboi == AutoboiOn || Profile.LezDoAutoboi`
     *   в разных recovery-ветках;
     * - гарантировать, что chat/server signal из `onChatPollResponseMeta(...)` не запускает
     *   fight recovery, если авто-бой выключен профилем или runtime-флагом.
     *
     * Зависимости:
     * - `AppVars.Autoboi`: runtime-состояние кнопки/автобоя;
     * - `AppVars.Profile.LezDoAutoboi`: профильная настройка автобоя;
     * - `shouldDeferAutoTurnForFirstFrameRender()` и `onChatPollResponseMeta(...)`.
     */
    private boolean isAutoFightRuntimeEnabled() {
        return AppVars.Autoboi == ru.neverlands.anclient.model.AutoboiState.AutoboiOn
                || (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi);
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
            AppLog.w(TAG, BG_TRACE_PREFIX + " isAutoTurnServerProbeAllowedNow: fallback by resumed-state", e);
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
            if ("anmove".equalsIgnoreCase(scheme) || "ancell".equalsIgnoreCase(scheme)) {
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
        AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: suppress server probe after manual main navigation"
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
            AppLog.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: failed to inspect manual navigation context", e);
            return false;
        }
    }

    /**
     * Во время активной навигации Авто-Клада не запускаем idle server-probe авто-боя.
     *
     * Причина:
     * - в этом режиме `requestAutoTurn` не должен генерировать лишние background probe,
     *   т.к. они создают конкурентную сетевую нагрузку (особенно через proxy) и мешают map-циклу.
     *
     * Ограничение:
     * - блокируем только idle-probe без признаков боя;
     * - если уже есть маркеры боя/finish-link, probe не подавляется.
     * - если `ch.php?show=1` недавно прислал `top.frames['main_top'].location='main.php'`,
     *   probe разрешается как fight recovery, потому сервер уже требует обновить верхний фрейм.
     *
     * Зависимости:
     * - `AppVars.AutoMoving`: активная навигация Авто-Клада/карты;
     * - `AppVars.ContentMainPhp`: кэш текущего верхнего фрейма с fight-маркерами;
     * - `AppVars.FightLink`: pending finish/captcha link, сформированный `LezFight`;
     * - `AppVars.LastFightAnnounceAtMs`: общий fight-сигнал от чата и `WebViewRequestInterceptor`.
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
        if (hasRecentFightAnnounceSignal(System.currentTimeMillis())) {
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: allow server probe during map automation, reason=recent_fight_signal");
            return false;
        }
        return !hasPendingAct7FightLink(AppVars.FightLink);
    }

    /**
     * Проверяет, свежий ли fight-сигнал для обхода map-automation suppression.
     *
     * Назначение:
     * - отличить постоянный no-fight фон от короткого окна, когда сервер уже сообщил о новом
     *   `main.php`/fight-контексте через чатовый frame;
     * - не вводить отдельный флаг для AutoMoving recovery и не плодить параллельный контур.
     *
     * Зависимости:
     * - `AUTO_TURN_FIGHT_ANNOUNCE_PROBE_GRACE_MS`: TTL валидности сигнала;
     * - `AppVars.LastFightAnnounceAtMs`: устанавливается обычным "Нападение" и
     *   `onChatPollResponseMeta(...)` при `main_top -> main.php`.
     */
    private boolean hasRecentFightAnnounceSignal(long nowMs) {
        long announceAtMs = AppVars.LastFightAnnounceAtMs;
        return announceAtMs > 0L
                && nowMs >= announceAtMs
                && (nowMs - announceAtMs) <= AUTO_TURN_FIGHT_ANNOUNCE_PROBE_GRACE_MS;
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
        if (shouldPauseAutoBattleForFightCaptcha()) {
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe skipped, fight captcha pending, reason="
                    + reason);
            return;
        }
        if (shouldSkipAutoTurnServerProbeForMapAutomation()) {
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe suppressed during map automation, reason="
                    + reason + ", autoMoving=" + AppVars.AutoMoving + ", doSearchBox=" + AppVars.DoSearchBox);
            return;
        }
        if (!isAutoTurnServerProbeAllowedNow()
                && isManualMainNavigationContextActive()
                && !hasFightMarkers(AppVars.ContentMainPhp)
                && !hasPendingAct7FightLink(AppVars.FightLink)) {
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe suppressed in active UI manual context, reason="
                    + reason);
            return;
        }
        if (now < autoTurnManualNavSuppressUntilMs) {
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe suppressed by manual main navigation, reason="
                    + reason + ", remainingMs=" + (autoTurnManualNavSuppressUntilMs - now));
            return;
        }
        if (now - lastAutoTurnServerProbeAtMs < AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS) {
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe throttled, reason=" + reason
                    + ", remainingMs=" + (AUTO_TURN_SERVER_PROBE_MIN_INTERVAL_MS - (now - lastAutoTurnServerProbeAtMs)));
            return;
        }
        synchronized (autoTurnServerProbeLock) {
            if (autoTurnServerProbeInFlight) {
                AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe skipped, in-flight, reason=" + reason);
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
                    AppLog.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe empty, reason=" + probeReason);
                    return;
                }
                runOnUiThread(() -> {
                    if (shouldPauseAutoBattleForFightCaptcha()) {
                        AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: ignore server probe result, fight captcha pending, reason="
                                + probeReason);
                        return;
                    }
                    AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe htmlLen=" + probeHtml.length()
                            + ", hasFightMarkers=" + probeResult.hasFightMarkers
                            + ", probeUrl=" + probeResult.url
                            + ", reason=" + probeReason);
                    if (probeResult.hasFightMarkers) {
                        AppVars.ContentMainPhp = probeHtml;
                        fightViewModel.autoTurnOnce(probeHtml);
                    } else {
                        AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe has no fight markers, probeUrl="
                                + probeResult.url);
                    }
                });
            } catch (Exception e) {
                AppLog.e(TAG, BG_TRACE_PREFIX + " requestAutoTurn: server probe failed, reason=" + probeReason, e);
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
            AppLog.d(TAG, BG_TRACE_PREFIX + " requestAutoTurn: probe attempt url=" + probeUrl
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
                AppLog.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct fight probe");
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
                AppLog.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: fight probe cookie is empty");
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                AppLog.w(TAG, BG_TRACE_PREFIX + " requestAutoTurn: fight probe HTTP " + responseCode + ", probeUrl=" + probeUrl);
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
            AppLog.e(TAG, BG_TRACE_PREFIX + " requestAutoTurn: fight probe request failed: " + probeUrl, e);
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
     * - `SessionManager`: предоставляет vcode для защищённых recovery-запросов;
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
     * Проверяет наличие готовой pending-ссылки завершения боя (`act=7`) в `FightLink`.
     *
     * Правила:
     * - если ссылка завершения уже готова, лишний server-probe не запускается;
     * - captcha-placeholder (`code=????`) не считается готовой ссылкой для навигации;
     * - проверка делается без изменения состояния.
     *
     * Зависимости:
     * - используется в {@link #requestAutoTurnInternal(boolean)} для anti-loop сценария.
     * - `AppVars.FightLink` может быть сформирован не только основным auto-turn parser,
     *   но и validation-парсером `isActiveFightContext(...)`, поэтому проверка не должна
     *   иметь side effects и не должна чистить состояние.
     */
    private boolean hasPendingAct7FightLink(String fightLink) {
        if (!hasFightFinishAct7Link(fightLink)) {
            return false;
        }
        return !fightLink.toLowerCase(Locale.ROOT).contains("code=????");
    }

    /**
     * Базовая проверка, что ссылка относится к завершению боя `get_id=61&act=7`.
     *
     * Отличие от `hasPendingAct7FightLink(...)`:
     * - этот метод принимает и captcha-placeholder `code=????`, потому он нужен для сохранения
     *   side effects `LezFight` в `isActiveFightContext(...)`;
     * - прямую навигацию по `code=????` он не разрешает, её фильтрует `hasPendingAct7FightLink(...)`.
     *
     * Зависимости:
     * - `LezFight.BuildFightLink(...)`: формирует normal/captcha finish-link;
     * - `AutoModeForegroundService.isFightCaptchaFinishLink(...)`: позднее отделяет captcha-flow.
     */
    private boolean hasFightFinishAct7Link(String fightLink) {
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
     * - `AppVars.FightLink` / `AppVars.CodeAddress`: `LezFight` может заполнить их даже
     *   при `IsBoi=false`, когда страница уже является финальной формой `act=7`.
     *
     * Важное изменение:
     * - validation-парсер больше не откатывает сгенерированную finish-link `get_id=61&act=7`;
     * - если это не finish-link, старые значения восстанавливаются, чтобы stale side effects
     *   не попали в основной auto-turn контур.
     */
    private boolean isActiveFightContext(String html) {
        if (!hasFightMarkers(html)) {
            return false;
        }
        String previousFightLink = AppVars.FightLink;
        String previousCodeAddress = AppVars.CodeAddress;
        LezFight parsedFight = null;
        try {
            parsedFight = new LezFight(html);
            return parsedFight.IsValid && parsedFight.IsBoi;
        } catch (Exception e) {
            AppLog.w(TAG, BG_TRACE_PREFIX + " isActiveFightContext: parse failed, treat as inactive", e);
            return false;
        } finally {
            boolean fightLinkChanged = !java.util.Objects.equals(previousFightLink, AppVars.FightLink);
            boolean codeAddressChanged = !java.util.Objects.equals(previousCodeAddress, AppVars.CodeAddress);
            String generatedFightLink = AppVars.FightLink;
            String generatedCodeAddress = AppVars.CodeAddress;
            if (fightLinkChanged || codeAddressChanged) {
                AppLog.d(TAG, BG_TRACE_PREFIX + " isActiveFightContext: validation side effects"
                        + ", generatedFightLink=" + (AppVars.FightLink == null ? "null" : AppVars.FightLink)
                        + ", generatedCodeAddress=" + (AppVars.CodeAddress == null ? "null" : AppVars.CodeAddress));
            }
            boolean finishAlreadyConfirmed = parsedFight != null
                    && FightAuto.isFightFinishConfirmedForLog(parsedFight.LogBoi);
            if (hasFightFinishAct7Link(generatedFightLink) && !finishAlreadyConfirmed) {
                AppLog.d(TAG, BG_TRACE_PREFIX + " isActiveFightContext: keep generated finish link from inactive fight html"
                        + ", finishLink=" + generatedFightLink
                        + ", codeAddress=" + (generatedCodeAddress == null ? "" : generatedCodeAddress));
                AppVars.FightLink = generatedFightLink;
                AppVars.CodeAddress = generatedCodeAddress;
            } else {
                if (finishAlreadyConfirmed && hasFightFinishAct7Link(generatedFightLink)) {
                    AppLog.d(TAG, BG_TRACE_PREFIX + " isActiveFightContext: drop generated finish link for confirmed fight"
                            + ", logBoi=" + parsedFight.LogBoi
                            + ", finishLink=" + generatedFightLink);
                }
                AppVars.FightLink = previousFightLink;
                AppVars.CodeAddress = previousCodeAddress;
            }
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
            AppLog.w(TAG, "submitAutoBattleActionToWebView: skip, invalid state");
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
                + "console.log('ANCLIENT_AUTOBATTLE_SUBMIT_ERR:'+e);"
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
                AppLog.d(TAG, BG_TRACE_PREFIX + " submitAutoBattleAction: submit path unstable (" + status
                        + "), retry left=" + nextRetriesLeft);
                binding.appBarMain.contentMain.webView.postDelayed(
                        () -> submitAutoBattleActionToWebView(result, nextRetriesLeft),
                        AUTO_SUBMIT_RETRY_DELAY_MS
                );
                return;
            }

            if (missing) {
                AppLog.w(TAG, BG_TRACE_PREFIX + " submitAutoBattleAction: submit path still unstable after retries, status=" + status);
            } else {
                AppLog.d(TAG, BG_TRACE_PREFIX + " submitAutoBattleAction: status=" + status);
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

        // В payload допустимо пустое trailing-поле `ina`; обычный split() его отрежет
        // и direct HTTP авто-ход будет ошибочно отклонён как parts=8 вместо 9.
        String[] parts = payload.split("\\|", -1);
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
     * Извлекает vcode из payload авто-удара (`vcode|enemy|group|...`) и синхронизирует SessionManager.
     *
     * Зависимости:
     * - `FightViewModel -> LezFight.BuildResult()` формирует payload с vcode первым токеном;
     * - `schedulePostResponseReload(...)` и recovery-ветка берут код защиты через SessionManager.
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
     * - валидирует токен регулярным шаблоном hex, чтобы не сохранять в SessionManager
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
        AppLog.d(TAG, "openInNewTab: " + title + " -> " + url);
        
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
                AppLog.e(TAG, "Error decoding nick", e);
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
        AppLog.d(TAG, "showCaptchaDialog: " + captchaUrl + " -> " + finishUrl);
        if (activeFightCaptchaDialog != null && activeFightCaptchaDialog.isShowing()) {
            boolean sameCaptcha = isSameCaptchaUrl(captchaUrl, activeFightCaptchaUrl);
            boolean sameFinish = isSameFightFinishUrl(finishUrl, activeFightFinishUrl);
            if (sameCaptcha && sameFinish) {
                AppLog.d(TAG, "showCaptchaDialog: already visible with same challenge, skip duplicate");
            } else {
                AppLog.d(TAG, "showCaptchaDialog: replacing dialog with newer challenge");
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
        boolean isAlchemyCaptcha = finishUrl != null
                && finishUrl.contains("/gameplay/ajax/alchemy_ajax.php")
                && finishUrl.contains("act=3");
        boolean useAjaxSubmit = isFishCaptcha || isAlchemyCaptcha;
        String captchaTitle = isFishCaptcha
                ? "Введите капчу для рыбалки"
                : isAlchemyCaptcha
                ? "Введите капчу для травника"
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
            resetAntiCaptchaState("dialog dismissed");
            activeFightCaptchaLoadSeq++;
            if (!replacingFightCaptchaDialog) {
                AppVars.FightCaptchaSubmitNotBeforeMs = 0L;
            }
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
        if (isPendingFightCaptchaFinishLink(activeFightFinishUrl)) {
            clearPendingAutoBattleSubmit();
            AppLog.d(TAG, BG_TRACE_PREFIX + " showCaptchaDialog: cleared pending auto battle submit for fight captcha");
        }
        // Фиксируем ожидаемый challenge для interceptor-guard: пока открыт popup,
        // внешние code.php-URL (другой challenge) не должны подменять текущую капчу.
        AppVars.CodeAddress = activeFightCaptchaUrl;
        activeFightCaptchaInput = input;

        dialog.setOnShowListener(d -> {
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                int primaryColor = ContextCompat.getColor(this, R.color.purple_500);
                int textColor = ContextCompat.getColor(this, R.color.colorOnPrimarySurface);
                positiveButton.setBackgroundColor(primaryColor);
                positiveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
                positiveButton.setTextColor(textColor);
                positiveButton.setEnabled(true);
                positiveButton.setOnClickListener(v -> {
                    String code = input.getText().toString().trim();
                    submitCaptchaCodeFromDialog(
                            code,
                            resolveActiveCaptchaFinishUrl(finishUrl),
                            useAjaxSubmit,
                            captchaSubmitted,
                            input,
                            dialog,
                            "manual_ok");
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
            resetAntiCaptchaState("captcha refresh");
            loadCaptchaImageAsync(captchaUrl, imageView, progressBar);
            startFightCaptchaAutoRefresh(imageView, progressBar, captchaUrl);
            maybeStartAntiCaptchaForActiveChallenge(finishUrl, useAjaxSubmit, captchaSubmitted, input, dialog);
        });
        startFightCaptchaAutoRefresh(imageView, progressBar, captchaUrl);
        maybeStartAntiCaptchaForActiveChallenge(finishUrl, useAjaxSubmit, captchaSubmitted, input, dialog);
    }

    private void submitCaptchaCodeFromDialog(String code,
                                             String finishUrl,
                                             boolean useAjaxSubmit,
                                             boolean[] captchaSubmitted,
                                             android.widget.EditText input,
                                             AlertDialog dialog,
                                             String source) {
        String safeCode = code == null ? "" : code.trim();
        AppLog.d(TAG, "showCaptchaDialog: submit source=" + source + ", codeLen=" + safeCode.length());
        if (safeCode.isEmpty()) {
            input.setError("Введите код");
            input.requestFocus();
            return;
        }
        if (!safeCode.matches("\\d{1,6}")) {
            input.setError("Код должен содержать только цифры");
            input.requestFocus();
            return;
        }
        String currentFinishUrl = resolveActiveCaptchaFinishUrl(finishUrl);
        if (!useAjaxSubmit && !isFightCaptchaFinishVCodeCurrent(currentFinishUrl, "submit_" + source)) {
            input.setError("Капча обновилась, дождитесь новой картинки");
            input.requestFocus();
            return;
        }

        captchaSubmitted[0] = true;
        if (AppVars.ResumeAutoboiAfterCaptcha
                && AppVars.Profile != null
                && AppVars.Profile.LezDoAutoboi) {
            AppVars.Autoboi = ru.neverlands.anclient.model.AutoboiState.AutoboiOn;
            AppLog.d(TAG, "showCaptchaDialog: restoring autoboi after captcha submit");
        }
        AppVars.ResumeAutoboiAfterCaptcha = false;
        boolean resumeSearchBox = AppVars.ResumeSearchBoxAfterCaptcha;
        AppVars.ResumeSearchBoxAfterCaptcha = false;

        String submitUrl = appendOrReplaceCaptchaCode(currentFinishUrl, safeCode);
        if (!useAjaxSubmit) {
            AppVars.FightCaptchaSubmitNotBeforeMs = 0L;
            AppVars.LastSubmittedFightCaptchaFinishKey = buildFightCaptchaFinishKey(submitUrl);
            AppVars.LastSubmittedFightCaptchaAtMs = System.currentTimeMillis();
            // Сбрасываем текущие captcha-маркеры, чтобы stale-значения не триггерили повторный popup.
            AppVars.FightLink = "";
            AppVars.CodeAddress = "";
        }
        AppLog.d(TAG, "showCaptchaDialog: submitting " + submitUrl);
        submitCaptchaSolution(submitUrl, useAjaxSubmit);
        if (!useAjaxSubmit && resumeSearchBox && AppVars.DoSearchBox && !AppVars.AutoMoving) {
            AppLog.d(TAG, "showCaptchaDialog: bootstrap auto treasure after captcha submit");
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
                        finalTargetWebView.loadUrl("http://neverlands.ru/main.php?an_search_box_bootstrap=1");
                    } catch (Exception e) {
                        AppLog.e(TAG, "showCaptchaDialog: auto treasure bootstrap failed", e);
                    }
                }, 450L);
            } else {
                AppLog.w(TAG, "showCaptchaDialog: skip auto treasure bootstrap, webView is null");
            }
        }
        dialog.dismiss();
    }

    private void maybeStartAntiCaptchaForActiveChallenge(String finishUrl,
                                                         boolean useAjaxSubmit,
                                                         boolean[] captchaSubmitted,
                                                         android.widget.EditText input,
                                                         AlertDialog dialog) {
        // Anti-Captcha встраивается только в существующий ручной popup captcha.
        // Зависимости:
        // - `showCaptchaDialog(...)` уже знает finishUrl, тип captcha и общий submit-контур;
        // - `AppVars.LastFightCaptchaImageBytes/Url` заполняются текущей загрузкой картинки;
        // - `AutoFunctionsManager.isAntiCaptchaEnabled()` повторно проверяет license feature
        //   и сбрасывает persisted ON-флаг, если временный full/custom grant истёк;
        // - `captchaSubmitted[0]` общий с кнопкой OK, чтобы auto-submit и manual-submit
        //   не отправили один и тот же finishUrl дважды.
        if (captchaSubmitted == null || captchaSubmitted[0]) {
            return;
        }
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        AutoFunctionsManager autoManager = AutoFunctionsManager.getInstance(this);
        if (!autoManager.isAntiCaptchaEnabled()) {
            return;
        }
        AntiCaptchaManager.Config config = autoManager.getAntiCaptchaConfig();
        if (!config.hasClientKey()) {
            AppLog.w(TAG, "ANTI_CAPTCHA_TRACE skip: API key is empty");
            return;
        }
        String currentFinishUrl = resolveActiveCaptchaFinishUrl(finishUrl);
        if (!useAjaxSubmit && !isFightCaptchaFinishVCodeCurrent(currentFinishUrl, "anti_captcha_start")) {
            AppLog.w(TAG, "ANTI_CAPTCHA_TRACE skip: finishUrl vcode is stale, waiting for matching challenge");
            return;
        }
        byte[] imageBytes = AppVars.LastFightCaptchaImageBytes;
        boolean imageReady = imageBytes != null
                && activeFightCaptchaImageHash != 0
                && isSameCaptchaUrl(activeFightCaptchaUrl, AppVars.LastFightCaptchaImageUrl)
                && decodeUsableCaptchaBitmap(imageBytes, "ANTI_CAPTCHA_TRACE.ready") != null;
        if (!imageReady) {
            // Картинка captcha иногда приходит позже самого AlertDialog. Ждём только текущий URL,
            // чтобы не отправить stale bytes от прошлого challenge и не получить неверный ответ.
            if (antiCaptchaImageWaitAttempts >= ANTI_CAPTCHA_IMAGE_WAIT_MAX_RETRIES) {
                AppLog.w(TAG, "ANTI_CAPTCHA_TRACE skip: captcha image bytes timeout, captchaUrl=" + activeFightCaptchaUrl);
                return;
            }
            antiCaptchaImageWaitAttempts++;
            fightCaptchaHandler.postDelayed(
                    () -> maybeStartAntiCaptchaForActiveChallenge(finishUrl, useAjaxSubmit, captchaSubmitted, input, dialog),
                    350L
            );
            return;
        }

        String challengeKey = buildAntiCaptchaChallengeKey(currentFinishUrl);
        if (challengeKey.isEmpty()) {
            return;
        }
        if (challengeKey.equals(antiCaptchaChallengeKey)) {
            return;
        }

        antiCaptchaChallengeKey = challengeKey;
        antiCaptchaInFlight = true;
        byte[] safeBytes = imageBytes.clone();
        AppLog.i(TAG, "ANTI_CAPTCHA_TRACE start: key=" + challengeKey + ", bytes=" + safeBytes.length);
        // Callback не трогает WebView/AlertDialog из worker-thread: результат всегда возвращается
        // в UI thread и затем проходит через тот же submitCaptchaCodeFromDialog(...), что ручной OK.
        AntiCaptchaManager.solveImageAsync(safeBytes, config, challengeKey, new AntiCaptchaManager.Callback() {
            @Override
            public void onSolved(String solvedChallengeKey, String text) {
                runOnUiThread(() -> handleAntiCaptchaSolved(
                        solvedChallengeKey,
                        text,
                        config,
                        currentFinishUrl,
                        useAjaxSubmit,
                        captchaSubmitted,
                        input,
                        dialog
                ));
            }

            @Override
            public void onFailed(String failedChallengeKey, String message) {
                runOnUiThread(() -> {
                    if (failedChallengeKey != null && failedChallengeKey.equals(antiCaptchaChallengeKey)) {
                        antiCaptchaInFlight = false;
                    }
                    AppLog.w(TAG, "ANTI_CAPTCHA_TRACE failed for active popup: " + message);
                    if (shouldRetryAntiCaptchaFailure(message)) {
                        scheduleAntiCaptchaRetry(
                                failedChallengeKey,
                                message,
                                currentFinishUrl,
                                useAjaxSubmit,
                                captchaSubmitted,
                                input,
                                dialog
                        );
                    }
                });
            }
        });
    }

    private boolean shouldRetryAntiCaptchaFailure(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("error_no_slot_available")
                || normalized.contains("no idle workers")
                || normalized.contains("http 5")
                || normalized.contains("timeout");
    }

    private void scheduleAntiCaptchaRetry(String failedChallengeKey,
                                          String message,
                                          String finishUrl,
                                          boolean useAjaxSubmit,
                                          boolean[] captchaSubmitted,
                                          android.widget.EditText input,
                                          AlertDialog dialog) {
        if (captchaSubmitted == null || captchaSubmitted[0]) {
            return;
        }
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        String currentFinishUrl = resolveActiveCaptchaFinishUrl(finishUrl);
        String currentChallengeKey = buildAntiCaptchaChallengeKey(currentFinishUrl);
        if (failedChallengeKey == null || !failedChallengeKey.equals(currentChallengeKey)) {
            AppLog.d(TAG, "ANTI_CAPTCHA_TRACE retry skip: stale failedKey=" + failedChallengeKey
                    + ", currentKey=" + currentChallengeKey);
            return;
        }
        if (antiCaptchaCreateRetryAttempts >= ANTI_CAPTCHA_CREATE_RETRY_MAX_COUNT) {
            AppLog.w(TAG, "ANTI_CAPTCHA_TRACE retry give up: attempts=" + antiCaptchaCreateRetryAttempts
                    + ", reason=" + message);
            return;
        }

        antiCaptchaCreateRetryAttempts++;
        long delayMs = ANTI_CAPTCHA_CREATE_RETRY_BASE_DELAY_MS * antiCaptchaCreateRetryAttempts;
        if (antiCaptchaRetryRunnable != null) {
            fightCaptchaHandler.removeCallbacks(antiCaptchaRetryRunnable);
        }
        antiCaptchaRetryRunnable = () -> {
            antiCaptchaRetryRunnable = null;
            if (captchaSubmitted[0]) {
                return;
            }
            if (dialog == null || !dialog.isShowing()) {
                return;
            }
            String retryFinishUrl = resolveActiveCaptchaFinishUrl(finishUrl);
            String retryChallengeKey = buildAntiCaptchaChallengeKey(retryFinishUrl);
            if (!failedChallengeKey.equals(retryChallengeKey)) {
                AppLog.d(TAG, "ANTI_CAPTCHA_TRACE retry skip: challenge changed, failedKey="
                        + failedChallengeKey + ", currentKey=" + retryChallengeKey);
                return;
            }
            antiCaptchaChallengeKey = "";
            antiCaptchaInFlight = false;
            AppLog.i(TAG, "ANTI_CAPTCHA_TRACE retry start: attempt=" + antiCaptchaCreateRetryAttempts
                    + ", delayMs=" + delayMs);
            maybeStartAntiCaptchaForActiveChallenge(retryFinishUrl, useAjaxSubmit, captchaSubmitted, input, dialog);
        };
        AppLog.i(TAG, "ANTI_CAPTCHA_TRACE retry scheduled: attempt=" + antiCaptchaCreateRetryAttempts
                + ", delayMs=" + delayMs + ", reason=" + message);
        fightCaptchaHandler.postDelayed(antiCaptchaRetryRunnable, delayMs);
    }

    private void handleAntiCaptchaSolved(String solvedChallengeKey,
                                         String text,
                                         AntiCaptchaManager.Config config,
                                         String finishUrl,
                                         boolean useAjaxSubmit,
                                         boolean[] captchaSubmitted,
                                         android.widget.EditText input,
                                         AlertDialog dialog) {
        antiCaptchaInFlight = false;
        if (captchaSubmitted == null || captchaSubmitted[0]) {
            AppLog.d(TAG, "ANTI_CAPTCHA_TRACE ignore solved code: already submitted");
            return;
        }
        if (dialog == null || !dialog.isShowing()) {
            AppLog.d(TAG, "ANTI_CAPTCHA_TRACE ignore solved code: dialog is not showing");
            return;
        }
        String currentFinishUrl = resolveActiveCaptchaFinishUrl(finishUrl);
        String currentChallengeKey = buildAntiCaptchaChallengeKey(currentFinishUrl);
        if (solvedChallengeKey == null || !solvedChallengeKey.equals(currentChallengeKey)) {
            // Защита от гонки: пользователь мог обновить captcha или popup мог быть заменён новым
            // challenge, пока anti-captcha.com решал старую картинку.
            AppLog.w(TAG, "ANTI_CAPTCHA_TRACE ignore stale solution, solvedKey=" + solvedChallengeKey
                    + ", currentKey=" + currentChallengeKey);
            return;
        }
        String code = text == null ? "" : text.trim();
        if (!isAntiCaptchaSolutionValid(code, config)) {
            AppLog.w(TAG, "ANTI_CAPTCHA_TRACE invalid solution textLen=" + code.length());
            input.setText(code);
            input.setError("Ответ Anti-Captcha не прошёл проверку");
            input.requestFocus();
            return;
        }

        AppLog.i(TAG, "ANTI_CAPTCHA_TRACE solved, auto-submit codeLen=" + code.length());
        input.setText(code);
        input.setSelection(code.length());
        // Единая точка отправки: manual OK и Anti-Captcha разделяют URL-normalization,
        // флаги autoboi/search-box resume и anti-duplicate markers.
        submitCaptchaCodeFromDialog(code, currentFinishUrl, useAjaxSubmit, captchaSubmitted, input, dialog, "anti_captcha");
        postAntiCaptchaCodeSubmittedToChat(code);
    }

    private void postAntiCaptchaCodeSubmittedToChat(String code) {
        String safeCode = escapeHtmlText(code == null ? "" : code.trim());
        String message = MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#008000>[Анти-Captcha]: ответ сервиса '"
                + safeCode
                + "' - код отправлен.</font>";
        Chat.addMessageToChat(message);
        AppLog.i(TAG, "ANTI_CAPTCHA_TRACE chat notification posted, codeLen=" + safeCode.length());
    }

    private String escapeHtmlText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private boolean isAntiCaptchaSolutionValid(String code, AntiCaptchaManager.Config config) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        if (config != null && config.numeric == AutoFunctionsManager.ANTI_CAPTCHA_NUMERIC_NUMBERS_ONLY
                && !code.matches("\\d+")) {
            return false;
        }
        int minLength = config == null ? 0 : config.minLength;
        int maxLength = config == null ? 0 : config.maxLength;
        if (minLength > 0 && code.length() < minLength) {
            return false;
        }
        return maxLength <= 0 || code.length() <= maxLength;
    }

    private String resolveActiveCaptchaFinishUrl(String fallbackFinishUrl) {
        String activeFinishUrl = activeFightFinishUrl == null ? "" : activeFightFinishUrl.trim();
        if (activeFightCaptchaDialog != null
                && activeFightCaptchaDialog.isShowing()
                && !activeFinishUrl.isEmpty()) {
            return activeFinishUrl;
        }
        return fallbackFinishUrl == null ? "" : fallbackFinishUrl.trim();
    }

    private boolean isFightCaptchaFinishVCodeCurrent(String finishUrl, String source) {
        if (!hasFightFinishAct7Link(finishUrl)) {
            return true;
        }
        String finishVCode = getQueryParameterSafely(finishUrl, "vcode");
        if (finishVCode.isEmpty()) {
            return true;
        }
        String sessionVCode = SessionManager.getInstance().getValidVCodeForAction("fight_fallback");
        if (sessionVCode == null || sessionVCode.trim().isEmpty()) {
            AppLog.w(TAG, "CAPTCHA_VCODE_TRACE " + source + ": SessionManager vcode is empty, allow submit fallback");
            return true;
        }
        boolean sameVCode = finishVCode.equals(sessionVCode.trim());
        if (!sameVCode) {
            AppLog.w(TAG, "CAPTCHA_VCODE_TRACE " + source + ": stale finish vcode, finish="
                    + abbreviateToken(finishVCode) + ", session=" + abbreviateToken(sessionVCode));
        }
        return sameVCode;
    }

    private String getQueryParameterSafely(String rawUrl, String paramName) {
        if (rawUrl == null || rawUrl.isEmpty() || paramName == null || paramName.isEmpty()) {
            return "";
        }
        try {
            String value = Uri.parse(rawUrl).getQueryParameter(paramName);
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            try {
                Pattern pattern = Pattern.compile("(?:[?&])" + Pattern.quote(paramName) + "=([^&#]*)",
                        Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(rawUrl);
                if (matcher.find()) {
                    return Uri.decode(matcher.group(1)).trim();
                }
            } catch (Exception ignoredAgain) {
                return "";
            }
        }
        return "";
    }

    private String abbreviateToken(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        String safeToken = token.trim();
        return safeToken.length() <= 8 ? safeToken : safeToken.substring(0, 8) + "...";
    }

    private String buildAntiCaptchaChallengeKey(String finishUrl) {
        if (activeFightCaptchaImageHash == 0 || activeFightCaptchaUrl == null || activeFightCaptchaUrl.isEmpty()) {
            return "";
        }
        return normalizeCaptchaUrlForCompare(activeFightCaptchaUrl)
                + "|" + normalizeFightFinishUrlForCompare(finishUrl)
                + "|" + activeFightCaptchaImageHash;
    }

    private void resetAntiCaptchaState(String reason) {
        antiCaptchaInFlight = false;
        antiCaptchaChallengeKey = "";
        antiCaptchaImageWaitAttempts = 0;
        antiCaptchaCreateRetryAttempts = 0;
        if (antiCaptchaRetryRunnable != null) {
            fightCaptchaHandler.removeCallbacks(antiCaptchaRetryRunnable);
            antiCaptchaRetryRunnable = null;
        }
        AppLog.d(TAG, "ANTI_CAPTCHA_TRACE reset: " + reason);
    }

    private android.graphics.Bitmap decodeUsableCaptchaBitmap(byte[] captchaBytes, String source) {
        String safeSource = source == null ? "captcha" : source;
        if (captchaBytes == null || captchaBytes.length == 0) {
            AppLog.w(TAG, safeSource + ": captcha image bytes are empty");
            return null;
        }
        if (captchaBytes.length < CAPTCHA_IMAGE_MIN_USABLE_BYTES) {
            AppLog.w(TAG, safeSource + ": captcha image bytes too small, bytes="
                    + captchaBytes.length + ", min=" + CAPTCHA_IMAGE_MIN_USABLE_BYTES);
            return null;
        }
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                captchaBytes,
                0,
                captchaBytes.length
        );
        if (bitmap == null) {
            AppLog.w(TAG, safeSource + ": captcha bitmap decode failed, bytes=" + captchaBytes.length);
            return null;
        }
        return bitmap;
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
            AppLog.d(TAG, "showCaptchaSystemNotification: POST_NOTIFICATIONS not granted");
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
     * - `submitAjaxCaptchaViaAjaxOrFallback(...)`: ajax captcha (`fish_ajax.php`/`alchemy_ajax.php`) через Ajax;
     * - `WebView.loadUrl(...)`: fallback и штатный submit для боя (`main.php?get_id=61&act=7...`).
     */
    /**
     * Отправляет решение капчи в правильный серверный поток.
     *
     * Ветки:
     * - useAjaxSubmit=true: отправка через AjaxGet(...), чтобы сохранить JS-flow ajax-модулей;
     * - useAjaxSubmit=false: прямой переход mainWebView.loadUrl(submitUrl) для боевого act=7.
     *
     * Зависимости:
     * - binding.appBarMain.contentMain.webView;
     * - submitAjaxCaptchaViaAjaxOrFallback(...);
     * - логи showCaptchaDialog: submitting ... для трассировки отправки.
     */
    private void submitCaptchaSolution(String submitUrl, boolean useAjaxSubmit) {
        if (binding == null || binding.appBarMain == null || binding.appBarMain.contentMain == null) {
            AppLog.w(TAG, "submitCaptchaSolution: skip, binding/content is null");
            return;
        }
        WebView mainWebView = binding.appBarMain.contentMain.webView;
        if (mainWebView == null) {
            AppLog.w(TAG, "submitCaptchaSolution: skip, mainWebView is null");
            return;
        }
        if (useAjaxSubmit) {
            submitAjaxCaptchaViaAjaxOrFallback(mainWebView, submitUrl);
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
    private void submitAjaxCaptchaViaAjaxOrFallback(WebView mainWebView, String submitUrl) {
        if (submitUrl == null || submitUrl.isEmpty()) {
            AppLog.w(TAG, "submitAjaxCaptchaViaAjaxOrFallback: submitUrl is empty");
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
                + "console.log('ANCLIENT_FISH_CAPTCHA_SUBMIT_ERR:'+e);"
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
            AppLog.d(TAG, "submitAjaxCaptchaViaAjaxOrFallback: status=" + status);
            if (!ajaxOk) {
                AppLog.w(TAG, "submitAjaxCaptchaViaAjaxOrFallback: fallback to loadUrl, status=" + status);
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
        AppLog.d(TAG, "loadCaptchaImageAsync: waiting captured bytes before network fallback, delayMs="
                + CAPTCHA_NETWORK_FALLBACK_DELAY_MS);

        byte[] cachedBytes = AppVars.LastFightCaptchaImageBytes;
        String cachedUrl = AppVars.LastFightCaptchaImageUrl;
        long cachedAt = AppVars.LastFightCaptchaImageAtMs;
        long cachedAge = cachedAt > 0 ? (System.currentTimeMillis() - cachedAt) : Long.MAX_VALUE;
        boolean isSameCaptchaUrl = isSameCaptchaUrl(cachedUrl, captchaUrl);
        boolean hasFreshSameUrlCaptchaBytes = isSameCaptchaUrl && cachedBytes != null
                && cachedAge >= 0 && cachedAge <= 30000L;
        android.graphics.Bitmap cachedBitmap = null;
        if (hasFreshSameUrlCaptchaBytes) {
            cachedBitmap = decodeUsableCaptchaBitmap(cachedBytes, "loadCaptchaImageAsync.cached");
            if (cachedBitmap != null) {
                AppLog.d(TAG, "loadCaptchaImageAsync: showing cached captcha preview, size=" + cachedBytes.length + ", ageMs=" + cachedAge);
                imageView.setImageBitmap(cachedBitmap);
            }
        }
        final boolean hasUsableFreshSameUrlCaptchaBytes = hasFreshSameUrlCaptchaBytes && cachedBitmap != null;

        // Важно: повторный GET code.php может сгенерировать новый challenge.
        // Поэтому даём WebView/interceptor приоритет и только после таймаута делаем fallback-запрос.
        fightCaptchaHandler.postDelayed(() -> {
            if (loadSeq != activeFightCaptchaLoadSeq) {
                AppLog.d(TAG, "loadCaptchaImageAsync: skip stale delayed fallback, loadSeq=" + loadSeq
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
                AppLog.d(TAG, "loadCaptchaImageAsync: challenge already locked from interceptor, skip network fallback");
                return;
            }
            if (updateCaptchaImageFromCaptured(imageView, progressBar, captchaUrl, true)) {
                AppLog.d(TAG, "loadCaptchaImageAsync: captured bytes arrived before fallback, skip network");
                return;
            }
            if (activeFightCaptchaImageLocked) {
                AppLog.d(TAG, "loadCaptchaImageAsync: challenge locked after captured update, skip network fallback");
                return;
            }

            final long fallbackStartedAtMs = System.currentTimeMillis();
            new Thread(() -> {
                byte[] captchaBytes = downloadCaptchaImageBytes(captchaUrl);
                final android.graphics.Bitmap bitmap = decodeUsableCaptchaBitmap(captchaBytes, "loadCaptchaImageAsync.network");

                runOnUiThread(() -> {
                    if (loadSeq != activeFightCaptchaLoadSeq) {
                        AppLog.d(TAG, "loadCaptchaImageAsync: skip stale network result, loadSeq=" + loadSeq
                                + ", activeSeq=" + activeFightCaptchaLoadSeq);
                        return;
                    }
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (!isSameCaptchaUrl(captchaUrl, activeFightCaptchaUrl)) {
                        AppLog.d(TAG, "loadCaptchaImageAsync: skip stale fallback, activeCaptchaUrl=" + activeFightCaptchaUrl);
                        return;
                    }

                    // Пока шёл fallback-запрос, bytes могли уже приехать из WebView interceptor — приоритет у них.
                    if (activeFightCaptchaImageLocked) {
                        AppLog.d(TAG, "loadCaptchaImageAsync: skip network apply, challenge already locked");
                        return;
                    }
                    if (updateCaptchaImageFromCaptured(imageView, progressBar, captchaUrl, true)) {
                        AppLog.d(TAG, "loadCaptchaImageAsync: intercepted bytes arrived during fallback, network result ignored");
                        return;
                    }
                    if (activeFightCaptchaImageLocked) {
                        AppLog.d(TAG, "loadCaptchaImageAsync: skip network apply after captured lock");
                        return;
                    }

                    progressBar.setVisibility(View.GONE);
                    if (bitmap != null && captchaBytes != null) {
                        if (hasUsableFreshSameUrlCaptchaBytes && captchaBytes.length < CAPTCHA_IMAGE_MIN_USABLE_BYTES) {
                            AppLog.w(TAG, "loadCaptchaImageAsync: network captcha is too small, keep cached preview, bytes=" + captchaBytes.length);
                            return;
                        }
                        AppLog.d(TAG, "loadCaptchaImageAsync: bitmap decoded from network, url=" + captchaUrl
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
                        AppLog.w(TAG, "loadCaptchaImageAsync: bitmap decode failed, url=" + captchaUrl
                                + ", bytes=" + (captchaBytes != null ? captchaBytes.length : 0));
                        if (!hasUsableFreshSameUrlCaptchaBytes) {
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

        android.graphics.Bitmap latestBitmap = decodeUsableCaptchaBitmap(latestBytes, "tryRefreshCaptchaImageFromLatest");
        if (latestBitmap == null) {
            return;
        }

        imageView.setImageBitmap(latestBitmap);
        AppLog.d(TAG, "tryRefreshCaptchaImageFromLatest: image updated from latest bytes,"
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

                    String parserCaptchaUrl = AppVars.CodeAddress == null ? "" : AppVars.CodeAddress.trim();
                    boolean latestMatchesParserCaptcha = !parserCaptchaUrl.isEmpty()
                            && isSameCaptchaUrl(latestCaptchaUrl, parserCaptchaUrl);
                    boolean captchaChanged = !isSameCaptchaUrl(activeFightCaptchaUrl, latestCaptchaUrl);
                    boolean finishChanged = candidateIsFightFinish
                            && !isSameFightFinishUrl(activeFightFinishUrl, candidateFinishUrl);
                    boolean candidateHasCurrentVCode = candidateIsFightFinish
                            && isFightCaptchaFinishVCodeCurrent(candidateFinishUrl, "image_switch");
                    if ((captchaChanged || finishChanged)
                            && !latestCaptchaUrl.isEmpty()
                            && latestMatchesParserCaptcha
                            && candidateIsFightFinish
                            && candidateHasCurrentVCode) {
                        AppLog.d(TAG, "updateCaptchaImageFromCaptured: switch to latest challenge, expected="
                                + expectedCaptchaUrl + ", latest=" + latestCaptchaUrl
                                + ", finishChanged=" + finishChanged
                                + ", challengeAgeMs=" + challengeAgeMs);
                        showCaptchaDialog(latestCaptchaUrl, candidateFinishUrl);
                        return false;
                    } else if ((captchaChanged || finishChanged) && !latestCaptchaUrl.isEmpty()) {
                        AppLog.d(TAG, "updateCaptchaImageFromCaptured: skip foreign latest challenge, expected="
                                + expectedCaptchaUrl + ", latest=" + latestCaptchaUrl
                                + ", parserCaptcha=" + parserCaptchaUrl
                                + ", candidateFinish=" + candidateFinishUrl
                                + ", candidateIsFightFinish=" + candidateIsFightFinish
                                + ", candidateHasCurrentVCode=" + candidateHasCurrentVCode
                                + ", finishChanged=" + finishChanged
                                + ", challengeAgeMs=" + challengeAgeMs);
                    }
                }
            }
            if (forceUpdate) {
                progressBar.setVisibility(View.VISIBLE);
                AppLog.d(TAG, "updateCaptchaImageFromCaptured: skip foreign bytes, expected="
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
                AppLog.d(TAG, "updateCaptchaImageFromCaptured: wait stabilize, ageMs=" + ageMs
                        + ", need=" + CAPTCHA_IMAGE_STABILIZE_DELAY_MS);
                return false;
            }
        }

        android.graphics.Bitmap latestBitmap = decodeUsableCaptchaBitmap(latestBytes, "updateCaptchaImageFromCaptured");
        if (latestBitmap == null) {
            if (forceUpdate) {
                progressBar.setVisibility(View.VISIBLE);
            }
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
        AppLog.d(TAG, "updateCaptchaImageFromCaptured: image updated, bytes=" + latestBytes.length
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
        AppLog.d(TAG, "updateFightCaptchaSubmitButtonState: codeLen=" + value.length()
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
                AppLog.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct captcha download: " + captchaUrl);
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
                AppLog.d(TAG, "downloadCaptchaImageBytes: using cookie len=" + cookie.length());
            } else {
                AppLog.w(TAG, "downloadCaptchaImageBytes: cookie is empty for " + captchaUrl);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                AppLog.w(TAG, "downloadCaptchaImageBytes: HTTP " + responseCode + " for " + captchaUrl);
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
            AppLog.d(TAG, "downloadCaptchaImageBytes: loaded " + data.length + " bytes from " + captchaUrl);
            return data.length > 0 ? data : null;
        } catch (Exception e) {
            AppLog.e(TAG, "downloadCaptchaImageBytes: failed for " + captchaUrl, e);
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
            AppLog.d(TAG, BG_TRACE_PREFIX + " screenStateReceiver: action=" + action);
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
                            AppLog.d(TAG, BG_TRACE_PREFIX + " ACTION_ADD_CHAT_MESSAGE: attack announce pulse");
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
        // Второй license gate после LoginActivity: Android может пересоздать MainActivity,
        // пока `AppVars.Profile` ещё присутствует, поэтому надо повторно проверить/восстановить
        // `LicenseSession` до инициализации WebView, proxy runtime, timers и auto-functions.
        if (!ensureLicenseRuntimeForMainActivity()) {
            return;
        }
        if (AppVars.Profile != null) {
            LogcatFileRecorder.setEnabled(this, AppVars.Profile.RecordLogcatToFile);
        }
        AppLog.i(TAG, "ANCLIENT_ANDROID_BUILD=" + BUILD_MARKER);
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
        AppLog.i(TAG, "PROXY_BOOT: MainActivity runtime state, running="
                + ProxyRuntimeManager.isRunning() + ", port=" + ProxyRuntimeManager.getActivePort());
        
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        applyLicenseNavigationVisibility(navigationView.getMenu());

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
            if (actionType == ru.neverlands.anclient.model.QuickActionType.QUICK_ACTIONS) {
                ru.neverlands.anclient.ui.QuickActionsBottomSheet.newInstance(null)
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

    private boolean ensureLicenseRuntimeForMainActivity() {
        if (AppVars.Profile == null || AppVars.Profile.UserNick == null || AppVars.Profile.UserNick.trim().isEmpty()) {
            AppLog.w("ANCLIENT_LICENSE", TAG, "LICENSE_RUNTIME_MAIN_REJECTED: profile missing");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return false;
        }
        // `ensureActiveForProfile` переиспользует валидную `currentSession` только для того же
        // `AppVars.Profile.UserNick`; иначе повторно запускается `LicenseManager`, который может
        // пересоздать request.txt / вернуть пользователя в LoginActivity.
        LicenseSession session = LicenseRuntime.getInstance().ensureActiveForProfile(
                this,
                AppVars.Profile.UserNick,
                "main_activity"
        );
        if (session != null) {
            AppLog.i("ANCLIENT_LICENSE", TAG, "LICENSE_RUNTIME_MAIN_APPROVED: "
                    + LicenseRuntime.getInstance().describeCurrentSession());
            return true;
        }
        AppLog.w("ANCLIENT_LICENSE", TAG, "LICENSE_RUNTIME_MAIN_REJECTED: session missing");
        Toast.makeText(this, "Лицензия не подтверждена", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
        return false;
    }

    private void applyLicenseNavigationVisibility(Menu menu) {
        if (menu == null) {
            return;
        }
        // Пункты drawer скрываются, а не просто disable-ятся. Так limited/public users
        // не видят full-only entry points; click handlers ниже всё равно повторяют эти проверки.
        setMenuItemVisible(menu, R.id.nav_quick_actions,
                LicenseRuntime.getInstance().isActionAllowed(QuickActionType.QUICK_ACTIONS));
        setMenuItemVisible(menu, R.id.nav_contacts,
                LicenseRuntime.getInstance().isActionAllowed(QuickActionType.OPEN_CONTACTS));
        setMenuItemVisible(menu, R.id.nav_clans,
                LicenseRuntime.getInstance().hasFeature(LicenseFeature.FEATURE_CLANS));
        setMenuItemVisible(menu, R.id.nav_logs,
                LicenseRuntime.getInstance().isActionAllowed(QuickActionType.OPEN_LOGS));
    }

    private void setMenuItemVisible(Menu menu, int itemId, boolean visible) {
        MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    /**
     * Планирует авто-удар с учётом пользовательской задержки между ударами.
     *
     * Поведение:
     * - первый удар после анонса боя отправляем сразу, чтобы сохранить event-driven отклик;
     * - между следующими ударами соблюдаем задержку: заданную в группе или legacy 1-2 сек при 0;
     * - если задержка ещё не прошла, ставим отложенную отправку payload (postDelayed), а не теряем ход;
     * - повторный payload того же состояния боя подавляется коротким окном, чтобы WebView/JS-bridge
     *   не создавал submit-storm при повторной обработке одного и того же fight_pm/vcode.
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
        if (shouldPauseAutoBattleForFightCaptcha()) {
            clearPendingAutoBattleSubmit();
            AppLog.d(TAG, BG_TRACE_PREFIX + " autoBattleDelay: skip submit, fight captcha pending");
            return;
        }

        long nowMs = System.currentTimeMillis();
        String payloadKey = buildAutoBattleSubmitPayloadKey(payload);
        if (!payloadKey.isEmpty()) {
            if (payloadKey.equals(lastAutoBattleSubmitPayloadKey)) {
                long sinceLastSameSubmitMs = nowMs - lastAutoBattleSubmitAtMs;
                if (sinceLastSameSubmitMs >= 0L
                        && sinceLastSameSubmitMs < AUTO_BATTLE_DUPLICATE_SUBMIT_SUPPRESS_MS) {
                    AppLog.d(TAG, BG_TRACE_PREFIX + " autoBattleDelay: duplicate payload suppressed"
                            + ", payloadKey=" + payloadKey
                            + ", sinceLastMs=" + sinceLastSameSubmitMs
                            + ", suppressMs=" + AUTO_BATTLE_DUPLICATE_SUBMIT_SUPPRESS_MS);
                    return;
                }
            }
            if (pendingAutoBattleSubmitRunnable != null
                    && payloadKey.equals(buildAutoBattleSubmitPayloadKey(pendingAutoBattleSubmitPayload))) {
                AppLog.d(TAG, BG_TRACE_PREFIX + " autoBattleDelay: duplicate payload already queued"
                        + ", payloadKey=" + payloadKey);
                return;
            }
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
            if (shouldPauseAutoBattleForFightCaptcha()) {
                AppLog.d(TAG, BG_TRACE_PREFIX + " autoBattleDelay: drop delayed submit, fight captcha pending");
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
        AppLog.d(TAG, BG_TRACE_PREFIX + " autoBattleDelay: queued, waitMs="
                + waitMs + ", configuredSec=" + Math.max(0, AppVars.CurrentAutoBattleHitDelaySec));
    }

    private void submitAutoBattleNow(String payload) {
        if (shouldPauseAutoBattleForFightCaptcha()) {
            clearPendingAutoBattleSubmit();
            AppLog.d(TAG, BG_TRACE_PREFIX + " autoBattleDelay: skip immediate submit, fight captcha pending");
            return;
        }
        lastAutoBattleSubmitAtMs = System.currentTimeMillis();
        lastAutoBattleSubmitPayloadKey = buildAutoBattleSubmitPayloadKey(payload);
        // Когда Activity не в foreground, WebView form.submit() реально не отправляет HTTP POST —
        // Android WebView откладывает навигацию до возврата в foreground.
        // Поэтому в background используем прямой HTTP POST, минуя WebView.
        if (!isActivityResumedState) {
            AppLog.d(TAG, TAG, BG_TRACE_PREFIX + " autoBattleDelay: submit via direct HTTP (background)");
            submitAutoBattleActionViaDirectHttp(payload);
            return;
        }
        AppLog.d(TAG, BG_TRACE_PREFIX + " autoBattleDelay: submit now (foreground, WebView)");
        // Подаём действие через безопасный wrapper с retry,
        // чтобы избежать race "AutoSubmit is not defined" после resume/screen on.
        submitAutoBattleActionToWebView(payload, AUTO_SUBMIT_MAX_RETRY_COUNT);
    }

    private long getAutoBattleSubmitWaitMs() {
        if (lastAutoBattleSubmitAtMs <= 0L) {
            return 0L;
        }
        int delaySec = Math.max(0, AppVars.CurrentAutoBattleHitDelaySec);
        long configuredDelayMs = delaySec > 0
                ? Math.min(300_000L, delaySec * 1000L)
                : resolveLegacyRandomAutoBattleDelayMs();
        long elapsedMs = System.currentTimeMillis() - lastAutoBattleSubmitAtMs;
        if (elapsedMs >= configuredDelayMs) {
            return 0L;
        }
        return configuredDelayMs - elapsedMs;
    }

    /**
     * Legacy-режим `HitDelaySec=0`: первый event-driven удар остаётся мгновенным,
     * а следующие удары получают стабильное для последнего submit окно 1-2 секунды.
     */
    private long resolveLegacyRandomAutoBattleDelayMs() {
        return AUTO_BATTLE_LEGACY_RANDOM_MIN_DELAY_MS
                + (lastAutoBattleSubmitAtMs % AUTO_BATTLE_LEGACY_RANDOM_DELAY_RANGE_MS);
    }

    private String buildAutoBattleSubmitPayloadKey(String payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        return Integer.toHexString(payload.hashCode());
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
        applyMainFrameFontScale();
        applyChatFrameFontScale();
        // Скрытый WebView для ch_refr (серверные ответы чата и отправка форм).
        if (chatRefrWebView == null) {
            chatRefrWebView = new WebView(this);
            setupWebView(chatRefrWebView, customWebViewClient);
            chatRefrWebView.setVisibility(View.GONE);
            if (binding != null && binding.getRoot() instanceof ViewGroup) {
                ((ViewGroup) binding.getRoot()).addView(chatRefrWebView, new ViewGroup.LayoutParams(1, 1));
            }
        }

        applyAuthCookiesToWebView(AppVars.lastCookies, "lastCookies_apply");
        AppVars.lastCookies = null;
    }

    private void applyAuthCookiesToWebView(List<java.net.HttpCookie> cookies, String stage) {
        CookieManager cookieManager = CookieManager.getInstance();
        if (cookies != null && !cookies.isEmpty()) {
            java.util.List<java.net.HttpCookie> filteredCookies = new java.util.ArrayList<>();
            java.util.Set<String> names = new java.util.HashSet<>();
            for (int i = cookies.size() - 1; i >= 0; i--) {
                java.net.HttpCookie cookie = cookies.get(i);
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
            AppLog.d(TAG, "AUTH_COOKIE_SYNC: applied " + stage + " names=" + names);
        }
        syncSessionCookiesAcrossHosts(cookieManager, "after_" + stage);
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
        AppLog.d(TAG, "AUTH_COOKIE_SYNC[" + stage + "]: never=" + summarizeCookieHeaderNames(neverCookie)
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
            AppLog.d(TAG, "AUTH_COOKIE_SYNC[" + stage + "]: mirrored never=" + summarizeCookieHeaderNames(neverAfter)
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
        applyMainFrameFontScale();
        applyChatFrameFontScale();
        
        // КРИТИЧНО: Очищаем кэш озера при возобновлении приложения
        // Если приложение было свёрнуто более 2 минут, vcode в кэше озера истеёк
        // Нужно перезагрузить озеро при следующем цикле рыбалки
        long timeInBackground = System.currentTimeMillis() - (isActivityResumedState ? 0 : 1000);
        if (AppVars.ContentLakeHtmlLastUpdateAtMs > 0) {
            long lakeAgeMs = System.currentTimeMillis() - AppVars.ContentLakeHtmlLastUpdateAtMs;
            if (lakeAgeMs > 120_000) {  // озеро старше 2 минут
                AppLog.d(TAG, "BG_TRACE onResume: clearing expired lake cache (age=" + lakeAgeMs + "ms)");
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
     * - `AppVars.LastFightCaptchaImageUrl` / `LastFightCaptchaImageAtMs`: fallback,
     *   если картинка уже перехвачена `WebViewRequestInterceptor`, а `CodeAddress` ещё пустой;
     * - `showCaptchaDialog(...)`: единый UI-контур ввода и submit кода.
     *
     * Важное изменение:
     * - метод может восстановить отсутствующий `IsFightCaptchaDialogVisible`, если есть валидные
     *   `FightLink + captchaUrl`; это чинит сценарий, где фон увидел captcha challenge до onResume.
     */
    private void restorePendingFightCaptchaDialogIfNeeded() {
        if (isFightCaptchaDialogShowing()) {
            return;
        }

        String finishUrl = AppVars.FightLink;
        if (!isPendingFightCaptchaFinishLink(finishUrl)) {
            if (AppVars.IsFightCaptchaDialogVisible) {
                clearStaleFightCaptchaState("pending finishUrl is invalid");
            }
            return;
        }

        String captchaUrl = resolvePendingFightCaptchaUrlForRestore();
        if (captchaUrl == null || captchaUrl.trim().isEmpty()) {
            if (AppVars.IsFightCaptchaDialogVisible) {
                clearStaleFightCaptchaState("pending captchaUrl is empty");
            }
            return;
        }

        if (!AppVars.IsFightCaptchaDialogVisible) {
            AppVars.IsFightCaptchaDialogVisible = true;
            AppVars.ResumeAutoboiAfterCaptcha = true;
            AppLog.w(TAG, "restorePendingFightCaptchaDialogIfNeeded: recovered missing visible flag"
                    + ", finishUrl=" + finishUrl + ", captchaUrl=" + captchaUrl);
        } else {
            AppLog.d(TAG, "restorePendingFightCaptchaDialogIfNeeded: restoring pending fight captcha dialog");
        }
        showCaptchaDialog(captchaUrl, finishUrl);
    }

    /**
     * Возвращает URL картинки боевой капчи для восстановления popup после onResume.
     *
     * Порядок источников:
     * - сначала `AppVars.CodeAddress`, если его успел заполнить parser/LezFight;
     * - затем свежий `AppVars.LastFightCaptchaImageUrl`, если image request уже прошёл через
     *   `WebViewRequestInterceptor`, но parser ещё не синхронизировал `CodeAddress`.
     *
     * Зависимости:
     * - `FIGHT_CAPTCHA_CAPTURE_FALLBACK_TTL_MS`: защита от использования старого challenge;
     * - `AppVars.CodeAddress`: обновляется при выборе fallback, чтобы остальные контуры увидели
     *   тот же URL капчи.
     */
    private String resolvePendingFightCaptchaUrlForRestore() {
        String captchaUrl = AppVars.CodeAddress == null ? "" : AppVars.CodeAddress.trim();
        if (!captchaUrl.isEmpty()) {
            return captchaUrl;
        }
        String capturedUrl = AppVars.LastFightCaptchaImageUrl == null ? "" : AppVars.LastFightCaptchaImageUrl.trim();
        long capturedAtMs = AppVars.LastFightCaptchaImageAtMs;
        long capturedAgeMs = capturedAtMs > 0L ? (System.currentTimeMillis() - capturedAtMs) : Long.MAX_VALUE;
        if (!capturedUrl.isEmpty()
                && capturedAgeMs >= 0L
                && capturedAgeMs <= FIGHT_CAPTCHA_CAPTURE_FALLBACK_TTL_MS) {
            AppVars.CodeAddress = capturedUrl;
            AppLog.d(TAG, "restorePendingFightCaptchaDialogIfNeeded: use captured captcha url fallback"
                    + ", ageMs=" + capturedAgeMs + ", url=" + capturedUrl);
            return capturedUrl;
        }
        return "";
    }

    /**
     * true только для активного blocking fight captcha popup.
     *
     * Зависимость: AutoCut captcha использует тот же dialog field, но определяется по
     * `alchemy_ajax.php?act=3` и исключается, чтобы fight recovery/event-driven ход не ждал
     * завершения травника.
     */
    public boolean isFightCaptchaDialogShowing() {
        return activeFightCaptchaDialog != null
                && activeFightCaptchaDialog.isShowing()
                && !isAlchemyCaptchaFinishUrl(activeFightFinishUrl);
    }

    private boolean shouldPauseAutoBattleForFightCaptcha() {
        return isFightCaptchaDialogShowing()
                || isPendingFightCaptchaFinishLink(activeFightFinishUrl)
                || isPendingFightCaptchaFinishLink(AppVars.FightLink);
    }

    private boolean isPendingFightCaptchaFinishLink(String finishUrl) {
        if (finishUrl == null || finishUrl.trim().isEmpty()) {
            return false;
        }
        String normalized = finishUrl.trim();
        return normalized.contains("get_id=61")
                && normalized.contains("act=7")
                && normalized.contains("code=????");
    }

    private void clearStaleFightCaptchaState(String reason) {
        AppLog.w(TAG, "restorePendingFightCaptchaDialogIfNeeded: clear stale captcha state, reason=" + reason
                + ", fightLink=" + (AppVars.FightLink == null ? "null" : AppVars.FightLink)
                + ", codeAddress=" + (AppVars.CodeAddress == null ? "null" : AppVars.CodeAddress));
        AppVars.IsFightCaptchaDialogVisible = false;
        AppVars.ResumeAutoboiAfterCaptcha = false;
        AppVars.ResumeSearchBoxAfterCaptcha = false;
        AppVars.FightLink = "";
        AppVars.CodeAddress = "";
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

    private static int normalizeFrameFontScalePercent(int value) {
        if (value < FRAME_FONT_SCALE_MIN) return FRAME_FONT_SCALE_MIN;
        if (value > FRAME_FONT_SCALE_MAX) return FRAME_FONT_SCALE_MAX;
        return value;
    }

    private int getConfiguredFrameFontScalePercent() {
        int value = AppVars.Profile != null ? AppVars.Profile.FrameFontScale : 100;
        return normalizeFrameFontScalePercent(value);
    }

    private int getConfiguredChatFrameFontScalePercent() {
        int value = AppVars.Profile != null ? AppVars.Profile.ChatFrameFontScale : 100;
        return normalizeFrameFontScalePercent(value);
    }

    private void applyMainFrameFontScale() {
        if (binding == null || binding.appBarMain == null || binding.appBarMain.contentMain == null) {
            return;
        }
        WebView mainWebView = binding.appBarMain.contentMain.webView;
        if (mainWebView == null) {
            return;
        }
        int scale = getConfiguredFrameFontScalePercent();
        mainWebView.getSettings().setTextZoom(scale);
        if (appliedMainFrameFontScale != scale) {
            appliedMainFrameFontScale = scale;
            AppLog.d(TAG, "FRAME_FONT_SCALE: main frame textZoom=" + scale + "%");
        }
    }

    private void applyChatFrameFontScale() {
        if (binding == null || binding.appBarMain == null || binding.appBarMain.contentMain == null) {
            return;
        }
        WebView chatMsgWebView = binding.appBarMain.contentMain.chatMsgWebview;
        if (chatMsgWebView == null) {
            return;
        }
        int scale = getConfiguredChatFrameFontScalePercent();
        chatMsgWebView.getSettings().setTextZoom(scale);
        if (appliedChatFrameFontScale != scale) {
            appliedChatFrameFontScale = scale;
            AppLog.d(TAG, "FRAME_FONT_SCALE: chat frame textZoom=" + scale + "%");
        }
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
                String payload = ru.neverlands.anclient.postfilter.MainPhp.buildServerChatTimeHtmlExternal()
                        + "<font color=#333399><b>Сервер (" + escapeHtml(kind) + "):</b></font> "
                        + escapeHtml(normalized);
                Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
                intent.putExtra("message", payload);
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                AppLog.e("JS_CONSOLE", consoleMessage.message() + " -- From line "
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
        ru.neverlands.anclient.utils.FileLogger.log("MainActivity: onDestroy() called.");
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
            mainSuccess = ru.neverlands.anclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
        }

        if (AppVars.lastChatMsgResponse != null) {
            String fileName = "HtmlLog_Chat_" + timeStamp + ".txt";
            String html = Russian.getString(AppVars.lastChatMsgResponse);
            chatSuccess = ru.neverlands.anclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
        }

        binding.appBarMain.contentMain.chatUsersWebview.evaluateJavascript(
                "(function() { return '<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'; })();",
                html -> {
                    String fileName = "HtmlLog_ChatUsers_" + timeStamp + ".txt";
                    ru.neverlands.anclient.utils.DataManager.writeStringToFile("Logs/" + fileName, html);
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
            // Пункт меню исторически назывался `Инвентарь`, но для ANClient/app2
            // теперь открывает портированный модуль `Казна`. Внутри `KaznaActivity`
            // используются `KaznaManager`, `KaznaParser`, локальные комплекты и
            // UID-cache из inventory HTML; WebView-инвентарь остаётся доступен через
            // существующие fast-action/wear сценарии и не дублируется здесь.
            Intent intent = new Intent(this, KaznaActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_profile) {
            binding.appBarMain.contentMain.webView.loadUrl("http://neverlands.ru/main.php?get_id=33&act=1");
        } else if (id == R.id.nav_quick_actions) {
            if (!LicenseRuntime.getInstance().isActionAllowed(QuickActionType.QUICK_ACTIONS)) {
                Toast.makeText(this, "Быстрые действия недоступны", Toast.LENGTH_SHORT).show();
                return true;
            }
            ru.neverlands.anclient.ui.QuickActionsBottomSheet.newInstance(null).show(getSupportFragmentManager(), "QuickActions");
        } else if (id == R.id.nav_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_contacts) {
            if (!LicenseRuntime.getInstance().isActionAllowed(QuickActionType.OPEN_CONTACTS)) {
                Toast.makeText(this, "Контакты недоступны", Toast.LENGTH_SHORT).show();
                return true;
            }
            suppressBackgroundLoopsForContacts = true;
            suppressChatRefreshOnceAfterContacts = true;
            suppressRoomRefreshOnceAfterContacts = true;
            shouldRestoreChatRefreshAfterContacts = (chatRefreshRunnable != null);
            AppLog.d(TAG, BG_TRACE_PREFIX + " nav_contacts: pause chat/room polling, chatActive="
                    + (chatRefreshRunnable != null) + ", roomActive=" + (roomUsersPollingRunnable != null));
            FileLogger.trace("contacts_nav",
                    "open contacts: chatActive=" + (chatRefreshRunnable != null)
                            + ", roomActive=" + (roomUsersPollingRunnable != null));
            stopChatRefresh();
            stopRoomUsersPolling();
            Intent intent = new Intent(this, ContactsActivity.class);
            contactsActivityLauncher.launch(intent);
        } else if (id == R.id.nav_clans) {
            if (!LicenseRuntime.getInstance().hasFeature(LicenseFeature.FEATURE_CLANS)) {
                Toast.makeText(this, "Кланы недоступны", Toast.LENGTH_SHORT).show();
                return true;
            }
            Intent intent = new Intent(this, ClansActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_forpost_info) {
            Intent intent = new Intent(this, ForpostInfoActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_tables) {
            Intent intent = new Intent(this, TablesActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_logs) {
            if (!LicenseRuntime.getInstance().isActionAllowed(QuickActionType.OPEN_LOGS)) {
                Toast.makeText(this, "Логи недоступны", Toast.LENGTH_SHORT).show();
                return true;
            }
            Intent intent = new Intent(this, LogsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_logout) {
            performLogoutToLogin();
        }
        
        DrawerLayout drawer = binding.drawerLayout;
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void openQuickActionsForNick(String nick) {
        runOnUiThread(() -> {
            if (!LicenseRuntime.getInstance().isActionAllowed(QuickActionType.QUICK_ACTIONS)) {
                Toast.makeText(this, "Быстрые действия недоступны", Toast.LENGTH_SHORT).show();
                return;
            }
            String cleanNick = nick == null ? "" : nick.trim();
            ru.neverlands.anclient.ui.QuickActionsBottomSheet.newInstance(cleanNick)
                    .show(getSupportFragmentManager(), "QuickActions");
        });
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
                    ProfessionRatingMonitor.maybeCheck(MainActivity.this);
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
            AppLog.d(TAG, "onRequestPermissionsResult: POST_NOTIFICATIONS granted=" + granted);
        }
    }

    // Запуск периодического опроса чата (ch.php?show=1&fyo=...).
    private void startChatRefresh() {
        AppLog.d(TAG, BG_TRACE_PREFIX + " startChatRefresh: seconds=" + chatRefreshSeconds);
        stopChatRefresh();
        chatRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    requestChatRefresh();
                } catch (Throwable t) {
                    AppLog.e(TAG, BG_TRACE_PREFIX + " requestChatRefresh runnable failed", t);
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
            AppLog.d(TAG, BG_TRACE_PREFIX + " stopChatRefresh: stopped");
        } else {
            AppLog.d(TAG, BG_TRACE_PREFIX + " stopChatRefresh: already stopped");
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
            AppLog.w(TAG, BG_TRACE_PREFIX + " requestChatRefresh skipped: chatRefrWebView is null");
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
        AppLog.d(TAG, BG_TRACE_PREFIX + " requestChatRefresh: " + url);
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
            AppLog.w(TAG, "requestChatRefresh: failed to refresh room users list", e);
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
            AppLog.d(TAG, BG_TRACE_PREFIX + " ensureChatRefrWebViewReady: attached hidden ch_refr view");
        }
    }

    private int getEffectiveChatRefreshSeconds() {
        int effective = chatRefreshSeconds;
        try {
            if (AutoFunctionsManager.getInstance(this).isAutoBossEnabled()) {
                effective = Math.min(effective, CHAT_REFRESH_AUTO_BOSS_SECONDS);
            }
        } catch (Exception e) {
            AppLog.w(TAG, BG_TRACE_PREFIX + " getEffectiveChatRefreshSeconds: failed to read autoBoss flag", e);
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
            AppLog.w(TAG, BG_TRACE_PREFIX + " isUiForegroundInteractive: fallback by resumed-state", e);
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
            AppLog.w(TAG, BG_TRACE_PREFIX + " isUiForegroundLikely: fallback by resumed-state", e);
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
            AppLog.w(TAG, BG_TRACE_PREFIX + " isDeviceLocked: fallback false", e);
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
                netColor = ContextCompat.getColor(this, R.color.colorOnPrimarySurface);
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
                String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&an_auto=1&r=" + now;
                // RULE 5: VCode получается через SessionManager
                String vcode = SessionManager.getInstance().getValidVCodeForAction("auto_cure_reload");
                if (vcode != null && !vcode.isEmpty()) {
                    reloadUrl += "&vcode=" + vcode;
                }
                AppLog.d(TAG, "AUTO_CURE_TRACE requestMainFrameReloadFromAutomation: reason=" + reason
                        + ", url=" + reloadUrl);
                binding.appBarMain.contentMain.webView.loadUrl(reloadUrl);
            } catch (Exception e) {
                AppLog.w(TAG, "AUTO_CURE_TRACE requestMainFrameReloadFromAutomation failed: reason=" + reason, e);
            }
        });
    }

    /**
     * Dispatcher server-timer действий на ближайший `NeverTimer` tick.
     *
     * Зависимости:
     * - основной источник времени — `AppVars.NeverTimer`, который выставляют серверные JS/ajax ответы;
     * - AutoCut дополнительно хранит собственный due-time retry, потому cleanup/повторный look не должны
     *   наследовать дальний `NeverTimer` от боя, навигации или другого фонового процесса;
     * - AutoMoving и AutoFish используют существующие reload ветки;
     * - AutoCut подключён только как pending look retry: state хранит `AutoCutManager`, а этот метод
     *   по due tick возвращает WebView в нужный штатный HTML-контекст без второго native HTTP-контура;
     * - обычный look retry идёт через `go=ret&an_auto_cut_tick=1`, cleanup-inventory retry идёт
     *   через `go=inv&im=0`, чтобы bulk-drop получил реальную inventory page;
     * - fight/captcha/fishing suppression guard-ы имеют приоритет, чтобы не ломать ручные и боевые действия.
     */
    private void checkServerTimerDrivenActions() {
        long now = System.currentTimeMillis();
        long serverDueAt = AppVars.NeverTimer;
        AutoFunctionsManager autoFunctionsManager = AutoFunctionsManager.getInstance(this);
        AutoCutManager autoCutManager = AutoCutManager.getInstance(this);
        boolean autoCutRetryPending = autoFunctionsManager.isAutoCutLikeEnabled()
                && autoCutManager.hasPendingLookRetry();
        long autoCutRetryDueAt = autoCutRetryPending ? autoCutManager.getPendingLookRetryDueAtMs() : 0L;
        boolean serverTimerDue = serverDueAt > 0L && now + SERVER_TIMER_TICK_MARGIN_MS >= serverDueAt;
        boolean autoCutRetryDue = autoCutRetryPending && autoCutManager.isPendingLookRetryDue(now);
        boolean autoCutCleanupRetryDue = autoCutRetryPending && autoCutManager.isPendingCleanupInventoryRetryDue(now);
        if (!serverTimerDue && !autoCutRetryDue) {
            return;
        }

        long dueAt = serverTimerDue ? serverDueAt : autoCutRetryDueAt;
        if (autoCutRetryDue && autoCutRetryDueAt > 0L && (!serverTimerDue || autoCutRetryDueAt < dueAt)) {
            dueAt = autoCutRetryDueAt;
        }

        if (dueAt == lastServerTimerDrivenReloadDueAtMs
                && (now - lastServerTimerDrivenReloadAtMs) < SERVER_TIMER_TICK_DEDUP_MS) {
            return;
        }
        if (now < navTickNetworkBackoffUntilMs) {
            AppLog.d(TAG, "SERVER_TIMER_TICK backoff active: waitMs=" + (navTickNetworkBackoffUntilMs - now)
                    + ", dueAt=" + dueAt);
            return;
        }

        boolean autoMoving = AppVars.AutoMoving && serverTimerDue && !autoCutCleanupRetryDue;
        boolean autoFish = autoFunctionsManager.isAutoFishEnabled() && serverTimerDue && !autoCutCleanupRetryDue;
        boolean autoCutRetry = autoCutRetryPending && autoCutRetryDue;
        if (!autoMoving && !autoFish && !autoCutRetry) {
            return;
        }
        if (AppVars.TreasureDigPauseNonCombatAutoFunctions) {
            AppLog.d(TAG, "SERVER_TIMER_TICK skip: treasure dig preparation pause is active"
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
                AppLog.d(TAG, "SERVER_TIMER_TICK skip: fishing sequence in progress (duration=" + timeSinceStartMs + "ms"
                        + ", timeout=" + dynamicTimeoutMs + "ms)"
                        + ", dueAt=" + dueAt);
                return;
            } else {
                // Timeout - очищаем флаг на случай, если act=2 не прошел (сервер long-polling навыка)
                AppLog.w(TAG, "SERVER_TIMER_TICK TIMEOUT: clearing lost fishing suppression flag after " + timeSinceStartMs + "ms"
                        + " (timeout=" + dynamicTimeoutMs + "ms)");
                AppVars.suppressBackgroundProbesDuringFishing = false;
                // Сбрасываем NeverTimer чтобы авто-рыбалка могла перезапуститься
                // (без этого NeverTimer застревает от go=inf wtime обновлений)
                AppVars.clearNeverTimer("server_timer_tick:fishing_suppression_timeout");
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

        String autoCutRetrySource = "";
        if (!autoMoving && !autoFish && autoCutRetry) {
            autoCutRetrySource = autoCutManager.consumePendingLookRetryIfDue(now);
            if (autoCutRetrySource.isEmpty()) {
                return;
            }
        }

        String reloadUrl;
        boolean autoCutRetryRescheduled = false;
        if (autoMoving) {
            String backgroundStepUrl = buildBackgroundAutoCutNavigationStepUrl(autoCutManager, now);
            if (!backgroundStepUrl.isEmpty()) {
                reloadUrl = backgroundStepUrl;
            } else {
                reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&an_nav_tick=1&r=" + now;
            }
        } else if (!autoCutRetrySource.isEmpty()) {
            if (isAutoCutCleanupInventoryRetrySource(autoCutRetrySource)) {
                reloadUrl = buildAutoCutCleanupInventoryReloadUrl(autoCutRetrySource, now);
                if (reloadUrl.isEmpty()) {
                    autoCutManager.scheduleLookRetryAfterDelay(
                            "cleanup_inventory:no_vcode:" + autoCutRetrySource,
                            1500L);
                    AppLog.w(AutoCutManager.TRACE_CHAIN, TAG,
                            "SERVER_TIMER_TICK auto-cut cleanup inventory rescheduled: no vcode, source="
                                    + autoCutRetrySource);
                    return;
                }
            } else {
                String backgroundLookUrl = buildBackgroundAutoCutLookUrl(autoCutManager, now);
                if (!backgroundLookUrl.isEmpty()) {
                    reloadUrl = backgroundLookUrl;
                } else {
                    reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=ret";
                    String vcode = SessionManager.getInstance().getValidVCodeForAction("server_timer_tick_auto_cut");
                    if (vcode != null && !vcode.isEmpty()) {
                        reloadUrl += "&vcode=" + vcode;
                    } else {
                        AppLog.w(AutoCutManager.TRACE_CHAIN, TAG,
                                "SERVER_TIMER_TICK auto-cut retry without vcode, source=" + autoCutRetrySource);
                    }
                    reloadUrl += "&an_auto_cut_tick=1&r=" + now;
                    if (!isUiForegroundLikely()) {
                        autoCutRetryRescheduled = true;
                        autoCutManager.scheduleLookRetryAfterDelay("background_map_reload:" + autoCutRetrySource, 1500L);
                        AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                                "SERVER_TIMER_TICK background auto-cut waits for fresh map before direct look, source="
                                        + autoCutRetrySource);
                    }
                }
            }
        } else {
            // КРИТИЧНО: af_tick никогда не должен отправляться БЕЗ vcode!
            // Если vcode пуст → происходит загрузка БЕЗ vcode → сервер обновляет PHPSESSID → старый vcode невалиден
            // ⚠️ ЗАЩИТА: при активной рыбалке не отправляем af_tick, даже если vcode есть
            if (AppVars.suppressBackgroundProbesDuringFishing) {
                // Рыбалка все еще идет - не отправляем af_tick, перезагружаем озеро для получения vcode
                reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=ret&r=" + now;
                AppLog.w(TAG, "SERVER_TIMER_TICK: fishing still active! Reloading lake for fresh vcode instead of af_tick.");
            } else {
                // RULE 5: VCode получается через SessionManager
                String vcode = SessionManager.getInstance().getValidVCodeForAction("server_timer_tick_af_tick");
                if (vcode == null || vcode.isEmpty()) {
                    // Vcode пуст - нужна защита: загружаем озеро чтобы получить свежий vcode
                    reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=ret&r=" + now;
                    AppLog.w(TAG, "SERVER_TIMER_TICK: VCode пуст при af_tick! Загружаем озеро для получения vcode.");
                } else {
                    reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inf&af_tick=1&r=" + now + "&vcode=" + vcode;
                }
            }
        }

        lastServerTimerDrivenReloadAtMs = now;
        lastServerTimerDrivenReloadDueAtMs = dueAt;
        if (!autoCutRetrySource.isEmpty()) {
            if (autoCutRetryRescheduled) {
                if (AppVars.NeverTimer <= now) {
                    AppVars.NeverTimer = now + 1500L;
                }
                AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                        "SERVER_TIMER_TICK auto-cut retry rescheduled after map reload, source=" + autoCutRetrySource
                                + ", retryDueInMs="
                                + Math.max(0L, autoCutManager.getPendingLookRetryDueAtMs() - now)
                                + ", globalDueInMs=" + Math.max(0L, AppVars.NeverTimer - now));
            } else {
                if (serverTimerDue || AppVars.NeverTimer <= now) {
                    AppVars.clearNeverTimer("server_timer_tick:auto_cut_retry_released");
                    AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                            "SERVER_TIMER_TICK auto-cut retry released expired NeverTimer, source=" + autoCutRetrySource);
                } else {
                    AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                            "SERVER_TIMER_TICK auto-cut retry preserved future global NeverTimer, source="
                                    + autoCutRetrySource
                                    + ", globalDueInMs=" + Math.max(0L, AppVars.NeverTimer - now));
                }
            }
        } else {
            // Локальный anti-loop guard до получения следующего server cooldown.
            AppVars.NeverTimer = now + 1500L;
        }

        AppLog.d(TAG, "SERVER_TIMER_TICK reload: autoMoving=" + autoMoving
                + ", autoFish=" + autoFish
                + ", autoCutRetry=" + !autoCutRetrySource.isEmpty()
                + ", autoCutRetrySource=" + autoCutRetrySource
                + ", dueAt=" + dueAt
                + ", currentUrl=" + currentUrl
                + ", reloadUrl=" + reloadUrl);
        binding.appBarMain.contentMain.webView.loadUrl(reloadUrl);
    }

    private boolean isAutoCutCleanupInventoryRetrySource(String source) {
        return source != null && source.contains("cleanup_inventory:");
    }

    private String buildAutoCutCleanupInventoryReloadUrl(String source, long now) {
        String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10&go=inv";
        String vcode = SessionManager.getInstance().getValidVCodeForAction("server_timer_tick_auto_cut_cleanup_inventory");
        if (vcode == null || vcode.isEmpty()) {
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG,
                    "SERVER_TIMER_TICK auto-cut cleanup inventory without vcode, source=" + source);
            return "";
        }
        reloadUrl += "&vcode=" + vcode;
        reloadUrl += "&im=0&an_auto_cut_tick=1&r=" + now;
        AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                "SERVER_TIMER_TICK auto-cut cleanup inventory reload, source=" + source
                        + ", url=" + reloadUrl);
        return reloadUrl;
    }

    private String buildBackgroundAutoCutNavigationStepUrl(AutoCutManager autoCutManager, long now) {
        if (isUiForegroundLikely() || autoCutManager == null) {
            return "";
        }
        String destination = AppVars.AutoMovingDestinaton == null ? "" : AppVars.AutoMovingDestinaton.trim();
        if (destination.isEmpty() || !autoCutManager.isAutoCutRouteDestination(destination)) {
            return "";
        }
        String current = AppVars.Profile == null || AppVars.Profile.MapLocation == null
                ? ""
                : AppVars.Profile.MapLocation.trim();
        if (current.isEmpty() || current.equals(destination)) {
            return "";
        }

        MapPath path = AppVars.AutoMovingMapPath;
        if (path == null || !path.canUseExistingPath(current, destination)) {
            path = new MapPath(current, java.util.Collections.singletonList(destination));
            AppVars.AutoMovingMapPath = path;
        }
        if (!path.pathExists || path.nextJump == null || path.nextJump.trim().isEmpty()) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                    "SERVER_TIMER_TICK background nav fallback: path unavailable, current=" + current
                            + ", destination=" + destination);
            return "";
        }

        AppVars.AutoMovingNextJump = path.nextJump;
        AppVars.AutoMovingJumps = path.jumps;
        AppVars.AutoMovingCityGate = path.cityGate;

        String coordKey = ExtMap.InvLocation.get(path.nextJump);
        Position position = coordKey == null ? null : ExtMap.Location.get(coordKey);
        String stepVCode = coordKey == null ? "" : ExtMap.MovableCells.get(coordKey);
        String gti = extractMapGtiFromContent(AppVars.ContentMainPhp);
        if (position == null || stepVCode == null || stepVCode.trim().isEmpty() || gti.isEmpty()) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                    "SERVER_TIMER_TICK background nav waits for fresh map: current=" + current
                            + ", destination=" + destination
                            + ", nextJump=" + path.nextJump
                            + ", hasPosition=" + (position != null)
                            + ", hasVCode=" + (stepVCode != null && !stepVCode.trim().isEmpty())
                            + ", hasGti=" + !gti.isEmpty());
            return "";
        }

        String url = "http://neverlands.ru/gameplay/ajax/map_ajax.php?act=1"
                + "&mx=" + position.X
                + "&my=" + position.Y
                + "&gti=" + gti
                + "&vcode=" + stepVCode.trim()
                + "&an_auto_cut_bg_nav=1&r=" + now;
        AppLog.i(AutoCutManager.TRACE_CHAIN, TAG,
                "SERVER_TIMER_TICK background nav step: current=" + current
                        + ", destination=" + destination
                        + ", nextJump=" + path.nextJump
                        + ", jumps=" + path.jumps
                        + ", url=" + url);
        return url;
    }

    private String buildBackgroundAutoCutLookUrl(AutoCutManager autoCutManager, long now) {
        if (isUiForegroundLikely() || autoCutManager == null) {
            return "";
        }
        if (!autoCutManager.shouldAutoLookOnCurrentCell()) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                    "SERVER_TIMER_TICK background auto-cut look skipped: current cell not ready");
            return "";
        }
        String current = AppVars.Profile == null || AppVars.Profile.MapLocation == null
                ? ""
                : AppVars.Profile.MapLocation.trim();
        String contentCell = extractMapRegNumFromContent(AppVars.ContentMainPhp);
        if (current.isEmpty() || contentCell.isEmpty() || !current.equals(contentCell)) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                    "SERVER_TIMER_TICK background auto-cut look waits for matching map: current=" + current
                            + ", contentCell=" + contentCell);
            return "";
        }
        String lookCode = extractMapLookCodeFromContent(AppVars.ContentMainPhp);
        if (lookCode.isEmpty()) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                    "SERVER_TIMER_TICK background auto-cut look waits for look button: current=" + current);
            return "";
        }
        String url = "http://neverlands.ru/gameplay/ajax/alchemy_ajax.php?act=1&vcode="
                + lookCode
                + "&an_auto_cut_bg_look=1&r=" + now;
        AppLog.i(AutoCutManager.TRACE_CHAIN, TAG,
                "SERVER_TIMER_TICK background auto-cut direct look: current=" + current + ", url=" + url);
        return url;
    }

    private String extractMapGtiFromContent(String html) {
        Matcher matcher = html == null ? null : MAP_DECL_PATTERN.matcher(html);
        return matcher != null && matcher.find() ? matcher.group(3) : "";
    }

    private String extractMapRegNumFromContent(String html) {
        Matcher matcher = html == null ? null : MAP_DECL_PATTERN.matcher(html);
        if (matcher == null || !matcher.find()) {
            return "";
        }
        String x = matcher.group(1);
        String y = matcher.group(2);
        Position position = ExtMap.Location.get(y + "/" + x + "_" + y);
        return position == null || position.RegNum == null ? "" : position.RegNum.trim();
    }

    private String extractMapLookCodeFromContent(String html) {
        Matcher matcher = html == null ? null : MAPBT_LOOK_PATTERN.matcher(html);
        return matcher != null && matcher.find() ? matcher.group(1).trim() : "";
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
        AppLog.d(TAG, "QUICK_UI_SYNC: AutoMoving changed -> refresh quick buttons, state=" + autoMovingNow);
    }

    public void refreshQuickButtonsPanelState(String reason) {
        if (quickButtonsPanel == null) {
            return;
        }
        quickButtonsPanel.refreshActionStates();
        AppLog.d(TAG, "QUICK_UI_SYNC: refresh requested, reason=" + reason);
    }
    
    public void invalidateQuickButtonsUI() {
        if (quickButtonsPanel == null) {
            return;
        }
        quickButtonsPanel.refreshActionStates();
        AppLog.d(TAG, "QUICK_UI_SYNC: invalidateQuickButtonsUI called -> refresh");
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
        AppLog.i(TAG, "LOGOUT_FLOW: started from navigation drawer");
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
                AppLog.e(TAG, "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, skip server logout request");
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
                AppLog.w(TAG, "LOGOUT_FLOW: cookie is empty for server logout request");
            }

            int responseCode = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            AppLog.i(TAG, "LOGOUT_FLOW: exit.php responseCode=" + responseCode
                    + ", location=" + (location == null ? "" : location));
        } catch (Exception e) {
            AppLog.w(TAG, "LOGOUT_FLOW: server logout request failed", e);
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
        AppVars.clearRuntimeAuthCredentials();
        NetworkClient.clearCookies();
        CookiesManager.clear();
        LicenseRuntime.getInstance().clear("logout_to_login");
        try {
            CookieManager manager = CookieManager.getInstance();
            manager.removeSessionCookies(null);
            manager.removeAllCookies(null);
            manager.flush();
        } catch (Throwable t) {
            AppLog.w(TAG, "LOGOUT_FLOW: WebView cookie cleanup failed", t);
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
            "            href.indexOf('logs.fcg') !== -1 ||" +
            "            href.indexOf('fight') !== -1 ||" +
            "            href.indexOf('pname.cgi') !== -1 ||" +
            "            href.indexOf('pbots.cgi') !== -1) {" +
            "          e.preventDefault();" +
            "          e.stopPropagation();" +
            "          var title = 'Новая вкладка';" +
            "          if (href.indexOf('forum.neverlands.ru') !== -1) title = 'Форум';" +
            "          else if (href.indexOf('pinfo.cgi') !== -1) title = 'PINFO';" +
            "          else if (href.indexOf('ch.php') !== -1) title = 'Комната';" +
            "          else if (href.indexOf('log.php') !== -1 || href.indexOf('logs.fcg') !== -1 || href.indexOf('fight') !== -1) title = 'Бой';" +
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
                                AppLog.d("main_post", TAG, "onPageFinished: main.php has frameset, skip POST fallback");
                                resetPostReloadGuard("frameset_main");
                                applyPageFinishedFixes(view, url);
                                return;
                            }

                            AppLog.d("main_post", TAG, "onPageFinished: POST-like main.php, processing fallback");
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
            AppLog.d(TAG, "shouldOverrideUrlLoading: " + url);
            
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
                    AppLog.d(TAG, "shouldOverrideUrlLoading: compass cell link -> startAutoMoving "
                            + compassCellRegNum + ", sourceUrl=" + url);
                } catch (Exception e) {
                    AppLog.e(TAG, "shouldOverrideUrlLoading: failed to start navigation from compass link: " + url, e);
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
            } else if (isNeverlandsHost && (lowerUrl.contains("log.php") || lowerUrl.contains("logs.fcg") || lowerUrl.contains("fight"))) {
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
                AppLog.d(TAG, "shouldOverrideUrlLoading: открываем вкладку " + title + " -> " + url);
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

            AppLog.e(TAG, "onReceivedError: code=" + errorCode
                    + ", mainFrame=" + isMainFrame
                    + ", url=" + failingUrl
                    + ", desc=" + description);

            maybeRetryMainFrameTimeout(view, failingUrl, errorCode, description, isMainFrame);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            String safeUrl = failingUrl == null ? "" : failingUrl;
            String safeDescription = description == null ? "" : description;

            AppLog.e(TAG, "onReceivedError(legacy): code=" + errorCode
                    + ", url=" + safeUrl
                    + ", desc=" + safeDescription);

            // На старом колбэке Android не даёт флаг mainFrame; для совместимости считаем этот вызов главным.
            maybeRetryMainFrameTimeout(view, safeUrl, errorCode, safeDescription, true);
        }


    }

    // Чтение файла из assets (например, JS для инъекций).
    private void applyPageFinishedFixes(WebView view, String url) {
        String jsFix = ru.neverlands.anclient.utils.HtmlUtils.getJsFix();
        view.evaluateJavascript(jsFix, null);

        if (url.contains("main.php")) {
            // NOTE: Keep this JS one-line: a literal newline inside quotes breaks parsing (SyntaxError).
            view.evaluateJavascript("javascript:(function() { var frameset = document.getElementsByTagName('frameset')[0]; if (frameset) { frameset.rows = '*,0'; } })()", null);

            try {
                String extractorJs = new String(readAssetFile("js/extract_fight_state.js"));
                view.evaluateJavascript("javascript:" + extractorJs, null);
            } catch (IOException e) {
                AppLog.e(TAG, "Failed to read extract_fight_state.js", e);
            }
            // DEPRECATED: RoomManager.startTracing() removed - HTML injection in Filter/RoomManager handles player list
        } else if (url.contains("ch.php")) {
            view.evaluateJavascript("javascript:(function() { var frameset = document.getElementsByTagName('frameset')[0]; if (frameset) { frameset.cols = '0, *'; } })()", null);
        }

        injectClickInterceptor(view);
    }

    private void handlePostMainPhpResponse(WebView view) {
        AppLog.d("main_post", TAG, "onPageFinished: POST-ответ main.php, извлекаем сообщение и перезагружаем");
        view.evaluateJavascript(
                "(function() {"
                        + "  try {"
                        + "    var body = document.body;"
                        + "    var transfer = document.getElementById('transfer');"
                        + "    return JSON.stringify({"
                        + "      bodyHtml: body ? (body.innerHTML || '') : '',"
                        + "      bodyText: body ? (body.innerText || body.textContent || '') : '',"
                        + "      transferHtml: transfer ? (transfer.innerHTML || '') : '',"
                        + "      transferText: transfer ? (transfer.innerText || transfer.textContent || '') : ''"
                        + "    });"
                        + "  } catch(e) {"
                        + "    return JSON.stringify({ bodyHtml: '', bodyText: '', transferHtml: '', transferText: '' });"
                        + "  }"
                        + "})()",
                result -> {
                    String payload = normalizeJsStringResult(result);
                    String htmlBody = "";
                    String bodyText = "";
                    String transferHtml = "";
                    String transferText = "";
                    try {
                        JSONObject json = new JSONObject(payload);
                        htmlBody = json.optString("bodyHtml", "");
                        bodyText = json.optString("bodyText", "");
                        transferHtml = json.optString("transferHtml", "");
                        transferText = json.optString("transferText", "");
                    } catch (JSONException e) {
                        AppLog.w("main_post", TAG, "onPageFinished: failed to parse POST payload JSON", e);
                        htmlBody = payload;
                    }

                    if (isSessionInterruptedHtml(htmlBody, bodyText)) {
                        AppLog.w(SESSION_RELOGIN_CHAIN, TAG,
                                "SESSION_RELOGIN_DETECTED: source=page_finished_main, url=http://neverlands.ru/main.php");
                        onSessionErrorHtmlDetected("http://neverlands.ru/main.php", "page_finished_main");
                        return;
                    }

                    String noticeHtml = !transferHtml.isEmpty() ? transferHtml : htmlBody;
                    String noticeText = !transferText.isEmpty() ? transferText : bodyText;
                    String parsedServerMessage = MainPhp.extractServerNoticeForUi(noticeHtml, noticeText);
                    boolean hasPostMessage = parsedServerMessage != null && !parsedServerMessage.isEmpty();
                    boolean autoSkinEnabled = AppVars.Profile != null && AppVars.Profile.SkinAuto;
                    boolean fastActionActive = AppVars.FastNeed
                            && AppVars.FastId != null
                            && !AppVars.FastId.isEmpty();

                    if (!transferText.isEmpty()) {
                        AppLog.d("main_post", TAG, "onPageFinished: transferText preview = "
                                + (transferText.length() > 200 ? transferText.substring(0, 200) : transferText));
                    }

                    if (!hasPostMessage && !autoSkinEnabled && !fastActionActive) {
                        AppLog.d("main_post", TAG, "onPageFinished: no POST marker on main.php, skip POST fallback");
                        resetPostReloadGuard("no_post_marker");
                        applyPageFinishedFixes(view, "http://neverlands.ru/main.php");
                        return;
                    }

                    if (!hasPostMessage && fastActionActive) {
                        String fastMsg = "[FAST_POST_FALLBACK] no POST marker but fast action active"
                                + ", fastId=" + AppVars.FastId
                                + ", fastCount=" + AppVars.FastCount;
                        AppLog.w("fast_action", TAG, fastMsg);
                    }

                    if (hasPostMessage) {
                        String msg = parsedServerMessage;
                        if (!msg.isEmpty()) {
                            AppLog.d("main_post", TAG, "onPageFinished: sysMessage из POST = " + msg);
                            Intent msgIntent = new Intent(ru.neverlands.anclient.utils.AppVars.ACTION_ADD_CHAT_MESSAGE);
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
                                        AppLog.d("main_post", TAG, "onPageFinished: POST-ответ, найдена разделка -> " + razUrl);
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
        StringBuilder sb = new StringBuilder(result.length());
        for (int i = 0; i < result.length(); i++) {
            char current = result.charAt(i);
            if (current == '\\' && i + 1 < result.length()) {
                char next = result.charAt(++i);
                switch (next) {
                    case '/':
                        sb.append('/');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '"':
                        sb.append('"');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'u':
                        if (i + 4 < result.length()) {
                            String hex = result.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                                break;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        sb.append('u');
                        break;
                    default:
                        sb.append(next);
                        break;
                }
            } else {
                sb.append(current);
            }
        }
        return sb.toString();
    }

    private boolean isSessionInterruptedHtml(String html, String text) {
        String combined = (html == null ? "" : html) + " " + (text == null ? "" : text);
        if (combined.trim().isEmpty()) {
            return false;
        }
        String lower = combined.toLowerCase(Locale.ROOT);
        return lower.contains("css/error.css")
                && (lower.contains("сеанс работы прерван")
                || lower.contains("сессия работы прервана")
                || lower.contains("session"));
    }

    private static long currentDotNetTicksForSessionRelogin() {
        long unixEpochMs = System.currentTimeMillis();
        return (unixEpochMs + 62135596800000L) * 10_000L;
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
                AppLog.d("main_post", TAG, "onPageFinished: skip POST reload while captcha dialog is visible");
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
                AppLog.w("main_post", TAG, "onPageFinished: POST reload blocked by anti-loop guard, url=" + reloadUrl);
                return;
            }

            AppLog.d("main_post", TAG, "onPageFinished: POST-ответ, перезагружаем " + reloadUrl);
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
            AppLog.w("main_post", TAG, "POST_RELOAD_GUARD: block enabled for " + POST_RELOAD_GUARD_BLOCK_MS
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
        AppLog.d("main_post", TAG, "POST_RELOAD_GUARD: reset, reason=" + reason);
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
            AppLog.w(TAG, "onReceivedError: network retry skipped by dedup, url=" + failingUrl
                    + ", context={" + timeoutContext + "}");
            return;
        }

        lastMainFrameTimeoutRetryUrl = failingUrl;
        lastMainFrameTimeoutRetryAtMs = now;
        AppLog.w(TAG, "onReceivedError: transient main-frame error, retry in "
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

        AppLog.w(TAG, "SERVER_TIMER_TICK network failure: url=" + failingUrl
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
        AppLog.d(TAG, "SERVER_TIMER_TICK backoff reset: reason=" + reason);
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
