package ru.neverlands.anclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import ru.neverlands.anclient.MainActivity;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;

/**
 * Runtime and persisted settings for Auto-Mine.
 *
 * The manager only stores state and makes decisions. Navigation and inventory preparation stay in
 * MainPhp/AutoMineHandler so the existing WebView request contour remains the single transport.
 */
public final class AutoMineManager {
    private static final String TAG = "AutoMineManager";
    public static final String TRACE_CHAIN = "AUTO_MINE_TRACE";

    private static final String PREFS_NAME = "auto_mine_prefs";
    private static final String KEY_PICKAXES_PREFIX = "pickaxes_";
    private static final String KEY_TORCHES_PREFIX = "torches_";
    private static final String KEY_CHAT_REPORT_PREFIX = "chat_report_";
    private static final String KEY_STOP_ON_EMPTY_PREFIX = "stop_on_empty_";
    private static final String MAP_ASSET_NAME = "map_mines.xml";
    private static final long AUTO_DIG_DEDUP_MS = 1500L;
    private static final long AUTO_DIG_TIMER_MARGIN_MS = 120L;
    private static final long AUTO_DIG_EXTRA_DELAY_MS = 2_000L;
    private static final long MINE_DIGG_EVENT_DEDUP_MS = 10_000L;
    private static final long MOVE_ATTEMPT_BLOCK_WINDOW_MS = 120_000L;
    private static final int MINE_ROUTE_TIED_STEP_COST = 2;
    private static final long MINE_BLAZ_TRIGGER_COOLDOWN_MS = 6_000L;
    private static final long MINE_BLAZ_WAIT_DEDUP_MS = 1_500L;
    private static final String BLISS_ELIXIR_NAME = "Эликсир Блаженства";

    private static final Pattern DIGG_BUTTON_PATTERN = Pattern.compile(
            "[\\\"']digg[\\\"']\\s*,\\s*[\\\"']Начать добычу[\\\"']\\s*,\\s*[\\\"']([^\\\"']+)[\\\"']",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DIGG_CALL_PATTERN = Pattern.compile(
            "Digg\\s*\\(\\s*[\\\"']([^\\\"']+)[\\\"']\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern POS_PATTERN = Pattern.compile(
            "var\\s+pos\\s*=\\s*\\[([^\\]]*)\\]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MINE_ID_PATTERN = Pattern.compile(
            "var\\s+mineid\\s*=\\s*[\\\"']?([^;\\\"']+)[\\\"']?\\s*;",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MINE_NAME_PATTERN = Pattern.compile(
            "var\\s+mine\\s*=\\s*\\[\\s*[\\\"']([^\\\"']+)[\\\"']",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern OBJECT_ENTRY_PATTERN = Pattern.compile("\\{([^{}]*)\\}",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "[\\\"']?([a-zA-Z_][a-zA-Z0-9_]*)[\\\"']?\\s*[:=]\\s*([\\\"'][^\\\"']*[\\\"']|[^,}]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    private static final String[] PICKAXE_NAMES = new String[]{
            "Легкая кирка",
            "Тяжелая кирка",
            "Сбалансированная кирка",
            "Кирка Мастера-рудокопа",
            "Праздничная Кирка Рудокопа"
    };
    private static final String[] TORCH_NAMES = new String[]{
            "Смоляной факел",
            "Масляный факел",
            "Дварфийский фонарь"
    };

    private static AutoMineManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile String lastMineId = "";
    private volatile String lastX = "";
    private volatile String lastY = "";
    private volatile String lastLevel = "";
    private volatile String lastDigCode = "";
    private volatile String lastDigDispatchKey = "";
    private volatile long lastDigDispatchAtMs = 0L;
    private volatile long nextAutoDigAllowedAtMs = 0L;
    private volatile String lastMineDiggEventKey = "";
    private volatile long lastMineDiggEventAtMs = 0L;
    private volatile String pendingTargetX = "";
    private volatile String pendingTargetY = "";
    private volatile int pendingRouteSteps = -1;
    private final Map<String, MineCell> mineCells = new LinkedHashMap<>();
    private final Map<String, Map<String, MineCell>> mapMineCells = new LinkedHashMap<>();
    private final List<String> pendingRouteKeys = new ArrayList<>();
    private final Set<String> runtimeBlockedEdges = new LinkedHashSet<>();
    private volatile boolean mapMineCellsLoaded = false;
    private volatile boolean mapMineCellsLoadFailed = false;
    private volatile String lastMoveAttemptFromX = "";
    private volatile String lastMoveAttemptFromY = "";
    private volatile String lastMoveAttemptToX = "";
    private volatile String lastMoveAttemptToY = "";
    private volatile String lastMoveAttemptDirection = "";
    private volatile long lastMoveAttemptAtMs = 0L;
    private volatile long lastMineBlazTriggerAtMs = 0L;
    private volatile long lastMineBlazWaitScheduledAtMs = 0L;
    private volatile long lastMineBlazWaitDueAtMs = 0L;

    private AutoMineManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AutoMineManager getInstance(Context context) {
        if (instance == null) {
            instance = new AutoMineManager(context);
        }
        return instance;
    }

    public static String[] getDefaultPickaxeNames() {
        return PICKAXE_NAMES.clone();
    }

    public static String[] getDefaultTorchNames() {
        return TORCH_NAMES.clone();
    }

    public String[] getAvailablePickaxeNames() {
        return getDefaultPickaxeNames();
    }

    public String[] getAvailableTorchNames() {
        return getDefaultTorchNames();
    }

    public synchronized List<String> getEnabledPickaxeNames() {
        return getEnabledNames(KEY_PICKAXES_PREFIX, PICKAXE_NAMES);
    }

    public synchronized List<String> getEnabledTorchNames() {
        return getEnabledNames(KEY_TORCHES_PREFIX, TORCH_NAMES);
    }

    public synchronized void setEnabledPickaxeNames(Set<String> selectedNames) {
        setEnabledNames(KEY_PICKAXES_PREFIX, PICKAXE_NAMES, selectedNames, "pickaxes");
    }

    public synchronized void setEnabledTorchNames(Set<String> selectedNames) {
        setEnabledNames(KEY_TORCHES_PREFIX, TORCH_NAMES, selectedNames, "torches");
    }

    public synchronized boolean isChatReportEnabled() {
        return prefs.getBoolean(scopedKey(KEY_CHAT_REPORT_PREFIX), true);
    }

    public synchronized void setChatReportEnabled(boolean enabled) {
        prefs.edit().putBoolean(scopedKey(KEY_CHAT_REPORT_PREFIX), enabled).apply();
        AppLog.i(TRACE_CHAIN, TAG, "chat report setting saved: " + enabled);
    }

    public synchronized boolean isStopOnEmptyEnabled() {
        return prefs.getBoolean(scopedKey(KEY_STOP_ON_EMPTY_PREFIX), false);
    }

    public synchronized void setStopOnEmptyEnabled(boolean enabled) {
        prefs.edit().putBoolean(scopedKey(KEY_STOP_ON_EMPTY_PREFIX), enabled).apply();
        AppLog.i(TRACE_CHAIN, TAG, "stop-on-empty setting saved: " + enabled);
    }

    public void onAutoMineEnabled(AutoFunctionsManager manager) {
        AppVars.AutoMineCheckPickaxe = true;
        AppVars.AutoMineArmedPickaxe = false;
        AppVars.AutoMinePickaxeHand = "";
        AppVars.AutoMinePickaxeHandD = "";
        AppVars.AutoMineCheckTorch = false;
        AppVars.AutoMineTorchReady = false;
        AppVars.AutoMinePauseNonCombatAutoFunctions = false;
        lastDigDispatchKey = "";
        lastDigDispatchAtMs = 0L;
        nextAutoDigAllowedAtMs = 0L;
        lastMineDiggEventKey = "";
        lastMineDiggEventAtMs = 0L;
        AppLog.i(TRACE_CHAIN, TAG, "auto mine enabled: pickaxe check primed");
        reloadMainFrame("enabled", "main.php?get_id=56&act=10&go=inf&an_auto_mine_bootstrap=1&r="
                + System.currentTimeMillis(), 350L);
    }

    public void onAutoMineDisabled() {
        AppVars.AutoMineCheckPickaxe = false;
        AppVars.AutoMineArmedPickaxe = false;
        AppVars.AutoMinePickaxeHand = "";
        AppVars.AutoMinePickaxeHandD = "";
        AppVars.AutoMineCheckTorch = false;
        AppVars.AutoMineTorchReady = false;
        AppVars.AutoMinePauseNonCombatAutoFunctions = false;
        lastDigCode = "";
        lastDigDispatchKey = "";
        lastDigDispatchAtMs = 0L;
        nextAutoDigAllowedAtMs = 0L;
        lastMineDiggEventKey = "";
        lastMineDiggEventAtMs = 0L;
        pendingTargetX = "";
        pendingTargetY = "";
        pendingRouteSteps = -1;
        synchronized (this) {
            mineCells.clear();
            pendingRouteKeys.clear();
            runtimeBlockedEdges.clear();
        }
        lastMoveAttemptFromX = "";
        lastMoveAttemptFromY = "";
        lastMoveAttemptToX = "";
        lastMoveAttemptToY = "";
        lastMoveAttemptDirection = "";
        lastMoveAttemptAtMs = 0L;
        lastMineBlazTriggerAtMs = 0L;
        lastMineBlazWaitScheduledAtMs = 0L;
        lastMineBlazWaitDueAtMs = 0L;
        AppLog.i(TRACE_CHAIN, TAG, "auto mine disabled: runtime state cleared");
    }

    public boolean isAutoMinePage(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        return html.contains("view_mine();")
                || html.contains("var mine = [")
                || html.contains("var mine = [\"")
                || html.contains("var pos = [")
                || html.contains("\"digg\",\"Начать добычу\"");
    }

    public void updateMinePageSnapshot(String html, String address) {
        if (html == null || html.isEmpty()) {
            return;
        }
        String digCode = extractDigCode(html);
        if (!digCode.isEmpty()) {
            lastDigCode = digCode;
        }
        Matcher posMatcher = POS_PATTERN.matcher(html);
        if (posMatcher.find()) {
            String[] parts = posMatcher.group(1).split(",");
            // IBClient mine.js uses pos as [y, x, level, timer]: scrollToElement reads pos[1] for X.
            if (parts.length > 1) lastX = cleanJsToken(parts[1]);
            if (parts.length > 0) lastY = cleanJsToken(parts[0]);
            if (parts.length > 2) lastLevel = cleanJsToken(parts[2]);
        }
        Matcher mineMatcher = MINE_ID_PATTERN.matcher(html);
        if (mineMatcher.find()) {
            lastMineId = cleanJsToken(mineMatcher.group(1));
        }
        Matcher mineNameMatcher = MINE_NAME_PATTERN.matcher(html);
        if (mineNameMatcher.find()) {
            String mineId = deriveMineIdFromMineName(cleanJsToken(mineNameMatcher.group(1)));
            if (!mineId.isEmpty()) {
                lastMineId = mineId;
            }
        }
        if (lastMineId.isEmpty()) {
            lastMineId = deriveMineIdFromHtml(html);
        }
        ensureMapMineCellsLoaded();
        int parsedCells = parseMineCellsFromHtml(html);
        ensureCurrentCellSnapshot();
        refreshPendingRoute("snapshot");
        AppLog.d(TRACE_CHAIN, TAG, "mine page snapshot: address=" + safe(address)
                + ", mineId=" + lastMineId
                + ", pos=" + lastX + "/" + lastY + "/" + lastLevel
                + ", hasDig=" + !digCode.isEmpty()
                + ", cells=" + parsedCells
                + ", assetCells=" + getActiveMapMineCellCount()
                + ", pendingTarget=" + pendingTargetX + "/" + pendingTargetY);
    }

    public String extractDigCode(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Matcher buttonMatcher = DIGG_BUTTON_PATTERN.matcher(html);
        if (buttonMatcher.find()) {
            return cleanJsToken(buttonMatcher.group(1));
        }
        Matcher callMatcher = DIGG_CALL_PATTERN.matcher(html);
        if (callMatcher.find()) {
            return cleanJsToken(callMatcher.group(1));
        }
        return "";
    }

    public boolean shouldDispatchAutoDigFromBridge(String code, String source) {
        String safeCode = cleanJsToken(code);
        if (safeCode.isEmpty()) {
            AppLog.d(TRACE_CHAIN, TAG, "auto dig rejected: empty code, source=" + safe(source));
            return false;
        }
        if (!isAutoMineEnabledByPreference()) {
            AppLog.d(TRACE_CHAIN, TAG, "auto dig rejected: disabled, source=" + safe(source));
            return false;
        }
        if (AppVars.FastNeed || AppVars.AutoMoving) {
            AppLog.d(TRACE_CHAIN, TAG, "auto dig rejected: busy, fast=" + AppVars.FastNeed
                    + ", moving=" + AppVars.AutoMoving + ", source=" + safe(source));
            return false;
        }
        long autoDigWaitMs = getAutoDigWaitMs();
        if (autoDigWaitMs > 0L) {
            AppLog.d(TRACE_CHAIN, TAG, "auto dig rejected: server timer active, dueInMs="
                    + autoDigWaitMs
                    + ", source=" + safe(source));
            return false;
        }
        if (AppVars.AutoMineCheckPickaxe || !AppVars.AutoMineArmedPickaxe) {
            requestPickaxeCheckBeforeDig("bridge:" + safe(source));
            AppLog.i(TRACE_CHAIN, TAG, "auto dig waits pickaxe check, source=" + safe(source));
            return false;
        }
        long nowMs = System.currentTimeMillis();
        String key = safeCode + "|" + lastMineId + "|" + lastX + "|" + lastY;
        if (key.equals(lastDigDispatchKey) && nowMs - lastDigDispatchAtMs < AUTO_DIG_DEDUP_MS) {
            AppLog.d(TRACE_CHAIN, TAG, "auto dig rejected: dedup key=" + key
                    + ", ageMs=" + (nowMs - lastDigDispatchAtMs));
            return false;
        }
        lastDigDispatchKey = key;
        lastDigDispatchAtMs = nowMs;
        lastDigCode = safeCode;
        AppLog.i(TRACE_CHAIN, TAG, "auto dig approved: source=" + safe(source)
                + ", mineId=" + lastMineId + ", pos=" + lastX + "/" + lastY
                + ", code=" + shortCode(safeCode));
        return true;
    }

    public String buildAutoDigInjection(String code, String source) {
        String safeCode = cleanJsToken(code);
        if (safeCode.isEmpty()) {
            return "";
        }
        return "\n<script>\n"
                + "(function(){\n"
                + "if(window.__anAutoMineDigClicked) return;\n"
                + "function tr(m){try{if(window.AndroidBridge&&AndroidBridge.TraceAutoMineRuntime)AndroidBridge.TraceAutoMineRuntime(String(m));}catch(e){}}\n"
                + "function waitMs(){try{var b=window.AndroidBridge||window.external;if(b&&b.getMineAutoDigWaitMs)return Math.max(0,parseInt(b.getMineAutoDigWaitMs(),10)||0);}catch(e){}return 0;}\n"
                + "function run(){\n"
                + " try{\n"
                + "  var code='" + escapeJs(safeCode) + "';\n"
                + "  var wait=waitMs();\n"
                + "  if(wait>0){tr('Digg waits timer '+wait+' source=" + escapeJs(source) + "');setTimeout(run,Math.min(Math.max(wait,250),600000));return;}\n"
                + "  var ok=true;\n"
                + "  if(window.AndroidBridge&&AndroidBridge.DoMineAutoDig) ok=!!AndroidBridge.DoMineAutoDig(code);\n"
                + "  else if(window.external&&window.external.DoMineAutoDig) ok=!!window.external.DoMineAutoDig(code);\n"
                + "  if(!ok){var retry=waitMs();if(retry>0){tr('Digg waits bridge timer '+retry+' source=" + escapeJs(source) + "');setTimeout(run,Math.min(Math.max(retry,250),600000));return;}tr('skip bridge rejected source=" + escapeJs(source) + "');return;}\n"
                + "  if(typeof Digg!=='function'){tr('skip Digg missing source=" + escapeJs(source) + "');return;}\n"
                + "  window.__anAutoMineDigClicked=true;\n"
                + "  tr('Digg auto click source=" + escapeJs(source) + "');\n"
                + "  Digg(code);\n"
                + "  setTimeout(function(){window.__anAutoMineDigClicked=false;}, 1800);\n"
                + " }catch(e){tr('error '+e);}\n"
                + "}\n"
                + "setTimeout(run, 250);\n"
                + "})();\n"
                + "</script>\n";
    }

    public void requestPickaxeCheckBeforeDig(String source) {
        AppVars.AutoMineCheckPickaxe = true;
        AppVars.AutoMineArmedPickaxe = false;
        AppLog.i(TRACE_CHAIN, TAG, "pickaxe check requested: source=" + safe(source));
        reloadMainFrame("pickaxe_check:" + safe(source), "main.php?get_id=56&act=10&go=inf&an_auto_mine_pickaxe=1&r="
                + System.currentTimeMillis(), 500L);
    }

    public void requestTorchCheckBeforeMove(String source) {
        AppVars.AutoMineCheckTorch = true;
        AppVars.AutoMineTorchReady = false;
        AppLog.i(TRACE_CHAIN, TAG, "torch check requested: source=" + safe(source));
        reloadMainFrame("torch_check:" + safe(source), "main.php?an_auto_mine_torch=1&r="
                + System.currentTimeMillis(), 500L);
    }

    public void stopBecauseNoPickaxe() {
        disableAutoMine("no_pickaxe", "Кирки не найдены в инвентаре. Отключаем автошахту.");
    }

    public void stopBecauseNoDigButton() {
        disableAutoMine("no_digg_button", "Нет кнопки \"Начать добычу\". Автошахта отключена.");
    }

    public void stopBecauseNoTorch() {
        clearPendingRoute("no_torch");
        disableAutoMine("no_torch", "Факел или фонарь не найден. Автошахта отключена.");
    }

    public void onMineAjaxResponse(String address, String response) {
        String text = response == null ? "" : response;
        String lower = text.toLowerCase(Locale.ROOT);
        String ajaxDigCode = extractDigCode(text);
        if (!ajaxDigCode.isEmpty()) {
            lastDigCode = ajaxDigCode;
            AppLog.i(TRACE_CHAIN, TAG, "mine ajax: next digg code captured, address="
                    + safe(address) + ", code=" + shortCode(ajaxDigCode));
        }
        if (lower.contains("невозможно пройти") || (lower.contains("невозможно") && lower.contains("пройти"))) {
            AppLog.w(TRACE_CHAIN, TAG, "mine ajax: server rejected route step, address=" + safe(address));
            markLastMoveAttemptBlocked("server_impossible", address);
            return;
        }
        if (lower.contains("вам нужна кирка")) {
            AppLog.w(TRACE_CHAIN, TAG, "mine ajax: server requires pickaxe, address=" + safe(address));
            requestPickaxeCheckBeforeDig("mine_ajax_pickaxe");
            return;
        }
        if (lower.contains("вам нужен факел")) {
            AppLog.w(TRACE_CHAIN, TAG, "mine ajax: server requires torch, address=" + safe(address));
            requestTorchCheckBeforeMove("mine_ajax_torch");
            return;
        }
        if (lower.contains("нет кнопки") && lower.contains("начать добычу")) {
            AppLog.w(TRACE_CHAIN, TAG, "mine ajax: no digg button, address=" + safe(address));
            stopBecauseNoDigButton();
            return;
        }
        if (lower.contains("неверный код защиты")) {
            AppLog.w(TRACE_CHAIN, TAG, "mine ajax: invalid protection code, invalidate session context");
            ru.neverlands.anclient.utils.SessionManager.getInstance().invalidateContext("auto_mine_invalid_code");
            reloadMainFrame("invalid_vcode", "main.php?get_id=56&act=10&go=inf&an_auto_mine_vcode=1&r="
                    + System.currentTimeMillis(), 700L);
            return;
        }
        if (lower.contains("вы не нашли ни одного ресурса")) {
            AppLog.i(TRACE_CHAIN, TAG, "mine ajax: empty dig result, stopOnEmpty=" + isStopOnEmptyEnabled());
            if (isStopOnEmptyEnabled()) {
                disableAutoMine("empty_dig", "Вы не нашли ни одного ресурса. Автошахта остановлена настройкой.");
            }
            return;
        }
        if (lower.contains("обнаружены ресурсы") || lower.contains("добыты ресурсы")) {
            AppLog.i(TRACE_CHAIN, TAG, "mine ajax: resource event, address=" + safe(address)
                    + ", hasNextDigCode=" + !ajaxDigCode.isEmpty());
        }
    }

    public void onMineRouteTooTiredFromServer(String address, String response) {
        CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.updateTied(
                100,
                "AutoMineManager.serverTooTired");
        int threshold = getAutoDrinkBlazThreshold();
        AppLog.w(TRACE_CHAIN, TAG, "mine ajax: server too tired, pause route and request bliss"
                + ", tied=" + snapshot.tied
                + ", threshold=" + threshold
                + ", pendingTarget=" + pendingTargetX + "/" + pendingTargetY
                + ", address=" + safe(address));
        requestMineRouteBlissIfNeeded("server_too_tired", snapshot.tied, threshold);
    }

    public String getCellImg(String x, String y) {
        MineCell cell = getMineCell(x, y);
        return cell == null ? "" : safe(cell.img);
    }

    public String mineMoveTo(String x, String y) {
        String safeX = cleanJsToken(x);
        String safeY = cleanJsToken(y);
        if (!isIntegerLike(safeX) || !isIntegerLike(safeY)) {
            AppLog.w(TRACE_CHAIN, TAG, "mineMoveTo rejected: invalid target x=" + safeX + ", y=" + safeY);
            return "";
        }
        pendingTargetX = safeX;
        pendingTargetY = safeY;
        refreshPendingRoute("mineMoveTo");
        boolean autoMineEnabled = isAutoMineEnabledByPreference();
        AppLog.i(TRACE_CHAIN, TAG, "mineMoveTo requested: x=" + safeX + ", y=" + safeY
                + ", autoMineEnabled=" + autoMineEnabled
                + ", torchReady=" + AppVars.AutoMineTorchReady
                + ", routeSteps=" + pendingRouteSteps);
        if (safeX.equals(lastX) && safeY.equals(lastY)) {
            clearPendingRoute("already_at_target");
            return "";
        }
        if (pausePendingRouteForFatigue("mineMoveTo")) {
            return "";
        }
        if (!AppVars.AutoMineTorchReady) {
            AppLog.i(TRACE_CHAIN, TAG, "no_torch: mineMoveTo waits torch, target="
                    + safeX + "/" + safeY + ", autoMineEnabled=" + autoMineEnabled);
            requestTorchCheckBeforeMove("mineMoveTo");
            return "";
        }
        return getPendingMoveDirection("mineMoveTo");
    }

    public String getNextMineMoveDirection(String x, String y, String lvl, String source) {
        updateMinePositionFromJs(x, y, lvl, source);
        return getPendingMoveDirection(source);
    }

    public void markMineRouteMoveDispatched(String x, String y, String lvl, String direction, String source) {
        updateMinePositionFromJs(x, y, lvl, "dispatch:" + safe(source));
        String safeDirection = safe(direction).toLowerCase(Locale.ROOT);
        if (safeDirection.isEmpty()) {
            return;
        }
        String nextKey = neighborKey(lastX, lastY, safeDirection);
        String nextX = "";
        String nextY = "";
        int split = nextKey.indexOf('-');
        if (split > 0 && split < nextKey.length() - 1) {
            nextX = nextKey.substring(0, split);
            nextY = nextKey.substring(split + 1);
        }
        lastMoveAttemptFromX = lastX;
        lastMoveAttemptFromY = lastY;
        lastMoveAttemptToX = nextX;
        lastMoveAttemptToY = nextY;
        lastMoveAttemptDirection = safeDirection;
        lastMoveAttemptAtMs = System.currentTimeMillis();
        AppLog.i(TRACE_CHAIN, TAG, "route move dispatched: direction=" + safeDirection
                + ", from=" + lastMoveAttemptFromX + "/" + lastMoveAttemptFromY
                + ", next=" + lastMoveAttemptToX + "/" + lastMoveAttemptToY
                + ", target=" + pendingTargetX + "/" + pendingTargetY
                + ", source=" + safe(source));
    }

    public boolean hasPendingMineRoute() {
        return !safe(pendingTargetX).isEmpty() && !safe(pendingTargetY).isEmpty();
    }

    public String buildPendingMoveInjection(String source) {
        String targetX = safe(pendingTargetX);
        String targetY = safe(pendingTargetY);
        if (targetX.isEmpty() || targetY.isEmpty()) {
            return "";
        }
        boolean autoMineEnabled = isAutoMineEnabledByPreference();
        if (pausePendingRouteForFatigue("pending_move:" + safe(source))) {
            return "";
        }
        if (!AppVars.AutoMineTorchReady) {
            AppLog.i(TRACE_CHAIN, TAG, "no_torch: pending move waits torch, target="
                    + targetX + "/" + targetY + ", autoMineEnabled=" + autoMineEnabled
                    + ", source=" + safe(source));
            if (!AppVars.AutoMineCheckTorch) {
                requestTorchCheckBeforeMove("pending_move:" + safe(source));
            }
            return "";
        }
        String direction = getPendingMoveDirection(source);
        if (direction.isEmpty()) {
            return "";
        }
        return "\n<script>\n"
                + "(function(){\n"
                + " function tr(m){try{if(window.AndroidBridge&&AndroidBridge.TraceAutoMineRuntime)AndroidBridge.TraceAutoMineRuntime(String(m));}catch(e){}}\n"
                + " try{\n"
                + "  var source='" + escapeJs(source) + "';\n"
                + "  var fallbackDir='" + escapeJs(direction) + "';\n"
                + "  function mark(dir){try{var b=window.AndroidBridge||window.external;if(b&&b.MarkMineRouteMoveDispatched)b.MarkMineRouteMoveDispatched(String((window.pos&&pos[1])||''),String((window.pos&&pos[0])||''),String((window.pos&&pos[2])||''),String(dir||''),source);}catch(ignore){}}\n"
                + "  function waitMs(){var wait=0,why=[];try{var tl=parseInt(time_left||0,10);if(!isNaN(tl)&&tl>0){wait=Math.max(wait,tl+350);why.push('time_left='+tl);}}catch(e){}try{if(typeof disable_move!=='undefined'&&disable_move){wait=Math.max(wait,1000);why.push('disable_move=true');}}catch(e){}try{var ms=parseInt(moving_status||0,10);if(!isNaN(ms)&&ms!==0){wait=Math.max(wait,1000);why.push('moving_status='+ms);}}catch(e){}waitMs.reason=why.join(',');return wait;}\n"
                + "  function run(){\n"
                + "   try{\n"
                + "    if(window.__anScheduleMineRouteStep){tr('route schedule source='+source);window.__anScheduleMineRouteStep(source,350);return;}\n"
                + "    if(window.__anRunMineRouteStep){tr('route run source='+source);window.__anRunMineRouteStep(source);return;}\n"
                + "    var wait=waitMs();if(wait>0){tr('route injection waits timer '+wait+(waitMs.reason?' reason='+waitMs.reason:'')+' source='+source);setTimeout(run,wait);return;}\n"
                + "    if(typeof move_tunnels!=='function'){tr('move_tunnels missing source='+source);return;}\n"
                + "    try{window.__anMineRouteLastAt=Date.now();}catch(ignore){}\n"
                + "    mark(fallbackDir);\n"
                + "    tr('route move_tunnels '+fallbackDir+' source='+source);\n"
                + "    move_tunnels(fallbackDir);\n"
                + "   }catch(e){tr('move run error '+e);}\n"
                + "  }\n"
                + "  setTimeout(run,350);\n"
                + " }catch(e){tr('move error '+e);}\n"
                + "})();\n"
                + "</script>\n";
    }

    public String getMineCellHTML(String x, String y, String lvl) {
        String safeX = safe(x);
        String safeY = safe(y);
        String safeLvl = safe(lvl);
        MineCell cell = getMineCell(safeX, safeY);
        String useful = cell == null ? "" : safe(cell.usefull);
        StringBuilder html = new StringBuilder();
        appendUsefulOverlay(html, useful);
        boolean isRouteCell = isPendingRouteCell(safeX, safeY);
        if (safeX.equals(lastX) && safeY.equals(lastY)) {
            html.append("<div class=\"an-mine-current\" style=\"box-shadow:0 0 0 4px #F5F5CE inset; position:absolute; left:0; top:0; right:0; bottom:0; z-index:3; pointer-events:none;\"></div>");
        } else if (safeX.equals(pendingTargetX) && safeY.equals(pendingTargetY)) {
            html.append("<div class=\"an-mine-route-blink\" style=\"box-shadow:0 0 0 4px #FF2A2A inset; position:absolute; left:0; top:0; right:0; bottom:0; z-index:3; pointer-events:none; animation:anMineRouteBlink .75s infinite alternate;\"></div>");
        } else if (isRouteCell) {
            html.append("<div class=\"an-mine-route-blink\" style=\"box-shadow:0 0 0 3px #FF2A2A inset; position:absolute; left:0; top:0; right:0; bottom:0; z-index:3; pointer-events:none; animation:anMineRouteBlink .75s infinite alternate;\"></div>");
        }
        html.append("<span style=\"color:white; font-weight:bold; font-size:10px; text-shadow:black 1px 1px 0, black -1px -1px 0, black -1px 1px 0, black 1px -1px 0; position:absolute; top:50px; width:130px; text-align:center; z-index:2;\">")
                .append(escapeHtml(safeX))
                .append("-")
                .append(escapeHtml(safeY));
        if (!safeLvl.isEmpty()) {
            html.append("<br>Ур. ").append(escapeHtml(safeLvl));
        }
        html.append("</span>");
        return html.toString();
    }

    public String getMoveText() {
        String targetX = safe(pendingTargetX);
        String targetY = safe(pendingTargetY);
        if (targetX.isEmpty() || targetY.isEmpty()) {
            return "";
        }
        int steps = estimateSteps(lastX, lastY, targetX, targetY);
        StringBuilder text = new StringBuilder();
        text.append("Пункт назначения: ")
                .append(escapeHtml(targetX))
                .append("-")
                .append(escapeHtml(targetY));
        int routeSteps = pendingRouteSteps;
        if (routeSteps >= 0) {
            text.append("<br>Ещё переходов: ").append(routeSteps);
        } else if (steps >= 0) {
            text.append("<br>Ещё переходов: ").append(steps);
        }
        return text.toString();
    }

    private int parseMineCellsFromHtml(String html) {
        String mineArray = extractJsArray(html, "mine");
        if (mineArray.isEmpty()) {
            return 0;
        }
        Map<String, MineCell> parsed = new LinkedHashMap<>();
        parseObjectMineEntries(mineArray, parsed);
        parseTopLevelMineEntries(mineArray, parsed);
        if (parsed.isEmpty()) {
            AppLog.d(TRACE_CHAIN, TAG, "mine map parser: no cells parsed from live var mine");
            return 0;
        }
        synchronized (this) {
            mineCells.clear();
            mineCells.putAll(parsed);
        }
        AppLog.i(TRACE_CHAIN, TAG, "mine map parser: cells=" + parsed.size()
                + ", mineId=" + safe(lastMineId));
        return parsed.size();
    }

    private void parseObjectMineEntries(String mineArray, Map<String, MineCell> parsed) {
        Matcher matcher = OBJECT_ENTRY_PATTERN.matcher(mineArray);
        while (matcher.find()) {
            Map<String, String> values = new LinkedHashMap<>();
            Matcher keyMatcher = KEY_VALUE_PATTERN.matcher(matcher.group(1));
            while (keyMatcher.find()) {
                values.put(keyMatcher.group(1).toLowerCase(Locale.ROOT), cleanJsToken(keyMatcher.group(2)));
            }
            String x = firstNonEmpty(values.get("x"), values.get("cx"));
            String y = firstNonEmpty(values.get("y"), values.get("cy"));
            if (!isIntegerLike(x) || !isIntegerLike(y)) {
                continue;
            }
            MineCell cell = new MineCell(
                    firstNonEmpty(values.get("mineid"), lastMineId),
                    x,
                    y,
                    firstNonEmpty(values.get("img"), values.get("image")),
                    firstNonEmpty(values.get("usefull"), values.get("useful")),
                    values.get("right"),
                    values.get("up"),
                    values.get("down"));
            parsed.put(cellKey(cell.x, cell.y), cell);
        }
    }

    private void parseTopLevelMineEntries(String mineArray, Map<String, MineCell> parsed) {
        List<String> entries = splitTopLevel(mineArray);
        if (entries.isEmpty()) {
            return;
        }
        for (String entry : entries) {
            String trimmed = entry == null ? "" : entry.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                tryAddCellFromTokens(splitTopLevel(trimmed.substring(1, trimmed.length() - 1)), parsed);
                continue;
            }
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                continue;
            }
            String unquoted = cleanJsToken(trimmed);
            if (unquoted.contains("|") || unquoted.contains(";")) {
                tryAddCellFromDelimitedString(unquoted, parsed);
            }
        }
        tryAddFlatCellGroups(entries, parsed, 8);
        tryAddFlatCellGroups(entries, parsed, 7);
    }

    private void tryAddCellFromDelimitedString(String raw, Map<String, MineCell> parsed) {
        String normalized = raw == null ? "" : raw.replace("\\n", "\n").trim();
        if (normalized.isEmpty()) {
            return;
        }
        String[] lines = normalized.split("\\n+");
        for (String line : lines) {
            String cleanLine = line == null ? "" : line.trim();
            if (cleanLine.isEmpty()) {
                continue;
            }
            String[] parts = cleanLine.contains("|")
                    ? cleanLine.split("\\|")
                    : cleanLine.split(";");
            List<String> tokens = new ArrayList<>();
            for (String part : parts) {
                tokens.add(part);
            }
            tryAddCellFromTokens(tokens, parsed);
        }
    }

    private void tryAddFlatCellGroups(List<String> entries, Map<String, MineCell> parsed, int groupSize) {
        if (entries.size() < groupSize || entries.size() % groupSize != 0) {
            return;
        }
        for (int i = 0; i + groupSize <= entries.size(); i += groupSize) {
            tryAddCellFromTokens(entries.subList(i, i + groupSize), parsed);
        }
    }

    private void tryAddCellFromTokens(List<String> tokens, Map<String, MineCell> parsed) {
        if (tokens == null || tokens.size() < 4) {
            return;
        }
        List<String> clean = new ArrayList<>();
        for (String token : tokens) {
            clean.add(cleanJsToken(token));
        }
        MineCell cell;
        if (clean.size() >= 8 && isIntegerLike(clean.get(1)) && isIntegerLike(clean.get(2))) {
            cell = new MineCell(clean.get(0), clean.get(1), clean.get(2), clean.get(3), clean.get(4), clean.get(5), clean.get(6), clean.get(7));
        } else if (clean.size() >= 7 && isIntegerLike(clean.get(0)) && isIntegerLike(clean.get(1))) {
            cell = new MineCell(lastMineId, clean.get(0), clean.get(1), clean.get(2), clean.get(3), clean.get(4), clean.get(5), clean.get(6));
        } else if (isIntegerLike(clean.get(0)) && isIntegerLike(clean.get(1))) {
            cell = new MineCell(lastMineId, clean.get(0), clean.get(1), clean.get(2), clean.get(3), "", "", "");
        } else {
            return;
        }
        parsed.put(cellKey(cell.x, cell.y), cell);
    }

    private void ensureCurrentCellSnapshot() {
        if (!isIntegerLike(lastX) || !isIntegerLike(lastY)) {
            return;
        }
        synchronized (this) {
            String key = cellKey(lastX, lastY);
            if (!mineCells.containsKey(key)) {
                mineCells.put(key, new MineCell(lastMineId, lastX, lastY, "", "0", "", "", ""));
            }
        }
    }

    private MineCell chooseNextStepToPendingTarget(String targetX, String targetY) {
        List<MineCell> route = buildRouteToPendingTarget(targetX, targetY);
        storePendingRoute(route);
        if (route == null || route.size() < 2) {
            return null;
        }
        return route.get(1);
    }

    private String getPendingMoveDirection(String source) {
        String targetX = safe(pendingTargetX);
        String targetY = safe(pendingTargetY);
        if (targetX.isEmpty() || targetY.isEmpty()) {
            return "";
        }
        if (targetX.equals(lastX) && targetY.equals(lastY)) {
            AppLog.i(TRACE_CHAIN, TAG, "next_cell arrived: target=" + targetX + "/" + targetY
                    + ", source=" + safe(source));
            clearPendingRoute("arrived");
            return "";
        }
        if (pausePendingRouteForFatigue(source)) {
            return "";
        }
        MineCell nextCell = chooseNextStepToPendingTarget(targetX, targetY);
        if (nextCell == null) {
            AppLog.w(TRACE_CHAIN, TAG, "no_useful_cell: cannot build mine move step, current="
                    + lastX + "/" + lastY + ", target=" + targetX + "/" + targetY
                    + ", cells=" + getMineCellCount() + ", source=" + safe(source));
            return "";
        }
        String direction = directionFromTo(lastX, lastY, nextCell.x, nextCell.y);
        if (direction.isEmpty() || isDirectionBlocked(direction, nextCell)) {
            AppLog.w(TRACE_CHAIN, TAG, "blocked_direction: direction=" + direction
                    + ", current=" + lastX + "/" + lastY + ", next=" + nextCell.x + "/" + nextCell.y
                    + ", target=" + targetX + "/" + targetY + ", source=" + safe(source));
            return "";
        }
        AppLog.i(TRACE_CHAIN, TAG, "next_cell: direction=" + direction
                + ", current=" + lastX + "/" + lastY + ", next=" + nextCell.x + "/" + nextCell.y
                + ", target=" + targetX + "/" + targetY
                + ", routeSteps=" + pendingRouteSteps
                + ", source=" + safe(source));
        return direction;
    }

    private void updateMinePositionFromJs(String x, String y, String lvl, String source) {
        String safeX = cleanJsToken(x);
        String safeY = cleanJsToken(y);
        String safeLvl = cleanJsToken(lvl);
        if (!isIntegerLike(safeX) || !isIntegerLike(safeY)) {
            AppLog.w(TRACE_CHAIN, TAG, "mine position sync rejected: x=" + safeX
                    + ", y=" + safeY + ", source=" + safe(source));
            return;
        }
        boolean changed = !safeX.equals(lastX) || !safeY.equals(lastY) || !safeLvl.equals(lastLevel);
        lastX = safeX;
        lastY = safeY;
        if (!safeLvl.isEmpty()) {
            lastLevel = safeLvl;
        }
        ensureMapMineCellsLoaded();
        ensureCurrentCellSnapshot();
        refreshPendingRoute("js:" + safe(source));
        if (changed) {
            AppLog.i(TRACE_CHAIN, TAG, "mine position synced: pos=" + lastX + "/" + lastY
                    + "/" + lastLevel + ", source=" + safe(source));
            if (isAcceptedMoveSyncSource(source)) {
                CharacterVitalsManager.Snapshot stepped = CharacterVitalsManager.increaseTied(
                        MINE_ROUTE_TIED_STEP_COST,
                        "AutoMineManager.acceptedMove");
                AppLog.i(TRACE_CHAIN, TAG, "mine route tied +step: cost=" + MINE_ROUTE_TIED_STEP_COST
                        + ", tied=" + stepped.tied
                        + ", source=" + safe(source));
            }
        }
    }

    private boolean pausePendingRouteForFatigue(String source) {
        if (!hasPendingMineRoute()) {
            return false;
        }
        if (AppVars.Profile == null) {
            return false;
        }
        int threshold = getAutoDrinkBlazThreshold();
        int tied = CharacterVitalsManager.snapshot().tied;
        if (tied < threshold) {
            if (AppVars.FastNeed) {
                AppLog.d(TRACE_CHAIN, TAG, "mine route waits active fast-action below fatigue threshold"
                        + ", fastId=" + safe(AppVars.FastId)
                        + ", tied=" + tied
                        + ", threshold=" + threshold
                        + ", source=" + safe(source));
                return true;
            }
            return false;
        }
        if (!AppVars.Profile.DoAutoDrinkBlaz) {
            AppLog.w(TRACE_CHAIN, TAG, "mine route paused by fatigue: auto bliss disabled"
                    + ", tied=" + tied
                    + ", threshold=" + threshold
                    + ", target=" + pendingTargetX + "/" + pendingTargetY
                    + ", source=" + safe(source));
            return true;
        }
        return requestMineRouteBlissIfNeeded(source, tied, threshold);
    }

    private boolean requestMineRouteBlissIfNeeded(String source, int tied, int threshold) {
        if (AppVars.Profile == null || !AppVars.Profile.DoAutoDrinkBlaz) {
            return false;
        }
        if (tied < threshold) {
            AppVars.AutoDrinkBlazPending = false;
            return false;
        }
        long now = System.currentTimeMillis();
        AppVars.AutoDrinkBlazPending = true;
        if (AppVars.FastNeed) {
            AppLog.i(TRACE_CHAIN, TAG, "mine route waits active fast-action before bliss"
                    + ", fastId=" + safe(AppVars.FastId)
                    + ", tied=" + tied
                    + ", threshold=" + threshold
                    + ", source=" + safe(source));
            return true;
        }
        if (AppVars.NeverTimer > now + 50L) {
            scheduleMineBlissAfterNeverTimer(source, AppVars.NeverTimer, threshold);
            AppLog.i(TRACE_CHAIN, TAG, "mine route waits NeverTimer before bliss"
                    + ", tied=" + tied
                    + ", threshold=" + threshold
                    + ", waitMs=" + Math.max(0L, AppVars.NeverTimer - now)
                    + ", source=" + safe(source));
            return true;
        }
        long sinceLastTrigger = now - lastMineBlazTriggerAtMs;
        if (sinceLastTrigger >= 0L && sinceLastTrigger < MINE_BLAZ_TRIGGER_COOLDOWN_MS) {
            AppLog.d(TRACE_CHAIN, TAG, "mine route bliss trigger cooldown"
                    + ", tied=" + tied
                    + ", threshold=" + threshold
                    + ", ageMs=" + sinceLastTrigger
                    + ", source=" + safe(source));
            return true;
        }
        lastMineBlazTriggerAtMs = now;
        AppLog.i(TRACE_CHAIN, TAG, "mine route triggers bliss elixir"
                + ", item=" + BLISS_ELIXIR_NAME
                + ", tied=" + tied
                + ", threshold=" + threshold
                + ", target=" + pendingTargetX + "/" + pendingTargetY
                + ", source=" + safe(source));
        FastActionManager.fastAttackBlazElixir("Авто-Шахта");
        return true;
    }

    private void scheduleMineBlissAfterNeverTimer(String source, long dueAtMs, int threshold) {
        long now = System.currentTimeMillis();
        if (Math.abs(dueAtMs - lastMineBlazWaitDueAtMs) <= 1000L
                && now - lastMineBlazWaitScheduledAtMs < MINE_BLAZ_WAIT_DEDUP_MS) {
            return;
        }
        lastMineBlazWaitDueAtMs = dueAtMs;
        lastMineBlazWaitScheduledAtMs = now;
        long delayMs = Math.max(350L, dueAtMs - now + 350L);
        mainHandler.postDelayed(() -> {
            int tiedNow = CharacterVitalsManager.snapshot().tied;
            if (tiedNow < threshold) {
                AppVars.AutoDrinkBlazPending = false;
                AppLog.i(TRACE_CHAIN, TAG, "mine route bliss wait resolved without action"
                        + ", tied=" + tiedNow
                        + ", threshold=" + threshold
                        + ", source=" + safe(source));
                return;
            }
            requestMineRouteBlissIfNeeded("never_timer_elapsed:" + safe(source), tiedNow, threshold);
        }, delayMs);
    }

    private static boolean isAcceptedMoveSyncSource(String source) {
        return safe(source).contains("advance:");
    }

    private static int getAutoDrinkBlazThreshold() {
        if (AppVars.Profile == null) {
            return 100;
        }
        return Math.max(0, Math.min(100, AppVars.Profile.AutoDrinkBlazTied));
    }

    private void refreshPendingRoute(String source) {
        String targetX = safe(pendingTargetX);
        String targetY = safe(pendingTargetY);
        if (targetX.isEmpty() || targetY.isEmpty()) {
            storePendingRoute(null);
            return;
        }
        List<MineCell> route = buildRouteToPendingTarget(targetX, targetY);
        storePendingRoute(route);
        if (route == null || route.size() < 2) {
            AppLog.d(TRACE_CHAIN, TAG, "mine route pending: route unavailable, current="
                    + lastX + "/" + lastY + ", target=" + targetX + "/" + targetY
                    + ", source=" + safe(source));
            return;
        }
        AppLog.d(TRACE_CHAIN, TAG, "mine route pending: steps=" + (route.size() - 1)
                + ", current=" + lastX + "/" + lastY + ", target=" + targetX + "/" + targetY
                + ", source=" + safe(source));
    }

    private void clearPendingRoute(String reason) {
        pendingTargetX = "";
        pendingTargetY = "";
        pendingRouteSteps = -1;
        synchronized (this) {
            pendingRouteKeys.clear();
        }
        AppLog.i(TRACE_CHAIN, TAG, "mine route cleared: reason=" + safe(reason));
    }

    private void storePendingRoute(List<MineCell> route) {
        synchronized (this) {
            pendingRouteKeys.clear();
            pendingRouteSteps = -1;
            if (route == null || route.size() < 2) {
                return;
            }
            for (MineCell cell : route) {
                if (cell != null) {
                    pendingRouteKeys.add(cellKey(cell.x, cell.y));
                }
            }
            pendingRouteSteps = route.size() - 1;
        }
    }

    private boolean isPendingRouteCell(String x, String y) {
        String key = cellKey(x, y);
        synchronized (this) {
            return pendingRouteKeys.contains(key);
        }
    }

    private List<MineCell> buildRouteToPendingTarget(String targetX, String targetY) {
        if (!isIntegerLike(lastX) || !isIntegerLike(lastY)
                || !isIntegerLike(targetX) || !isIntegerLike(targetY)) {
            return null;
        }
        String startKey = cellKey(lastX, lastY);
        String targetKey = cellKey(targetX, targetY);
        if (startKey.equals(targetKey)) {
            return Collections.emptyList();
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        Map<String, String> parent = new LinkedHashMap<>();
        queue.add(startKey);
        visited.add(startKey);
        parent.put(startKey, "");
        while (!queue.isEmpty()) {
            String currentKey = queue.removeFirst();
            if (currentKey.equals(targetKey)) {
                break;
            }
            MineCell current = getMineCellByKey(currentKey);
            if (current == null) {
                continue;
            }
            for (String direction : new String[]{"up", "right", "down", "left"}) {
                if (!isImageDirectionOpen(current.img, direction)) {
                    continue;
                }
                if (isRuntimeEdgeBlocked(current.x, current.y, direction)) {
                    continue;
                }
                String nextKey = neighborKey(current, direction);
                if (nextKey.isEmpty() || visited.contains(nextKey)) {
                    continue;
                }
                MineCell next = getMineCellByKey(nextKey);
                if (next == null) {
                    continue;
                }
                visited.add(nextKey);
                parent.put(nextKey, currentKey);
                if (nextKey.equals(targetKey)) {
                    queue.clear();
                    break;
                }
                queue.addLast(nextKey);
            }
        }
        if (!parent.containsKey(targetKey)) {
            return null;
        }
        List<MineCell> route = new ArrayList<>();
        String cursor = targetKey;
        while (cursor != null && !cursor.isEmpty()) {
            MineCell cell = getMineCellByKey(cursor);
            if (cell == null) {
                return null;
            }
            route.add(cell);
            cursor = parent.get(cursor);
        }
        Collections.reverse(route);
        return route;
    }

    private MineCell getMineCellByKey(String key) {
        String safeKey = safe(key);
        int split = safeKey.indexOf('-');
        if (split <= 0 || split >= safeKey.length() - 1) {
            return null;
        }
        return getMineCell(safeKey.substring(0, split), safeKey.substring(split + 1));
    }

    private static String neighborKey(MineCell cell, String direction) {
        if (cell == null || !isIntegerLike(cell.x) || !isIntegerLike(cell.y)) {
            return "";
        }
        return neighborKey(cell.x, cell.y, direction);
    }

    private static String neighborKey(String xValue, String yValue, String direction) {
        if (!isIntegerLike(xValue) || !isIntegerLike(yValue)) {
            return "";
        }
        int x = Integer.parseInt(xValue);
        int y = Integer.parseInt(yValue);
        if ("up".equals(direction)) y--;
        else if ("right".equals(direction)) x++;
        else if ("down".equals(direction)) y++;
        else if ("left".equals(direction)) x--;
        else return "";
        if (x < 0 || x > 20 || y < 0 || y > 20) {
            return "";
        }
        return cellKey(String.valueOf(x), String.valueOf(y));
    }

    private boolean isDirectionBlocked(String direction, MineCell nextCell) {
        if (direction == null || direction.isEmpty()) {
            return true;
        }
        if (isRuntimeEdgeBlocked(lastX, lastY, direction)) {
            return true;
        }
        MineCell current = getMineCell(lastX, lastY);
        return current == null || !isImageDirectionOpen(current.img, direction);
    }

    private boolean isRuntimeEdgeBlocked(String fromX, String fromY, String direction) {
        String edgeKey = routeEdgeKey(fromX, fromY, direction);
        if (edgeKey.isEmpty()) {
            return false;
        }
        synchronized (this) {
            return runtimeBlockedEdges.contains(edgeKey);
        }
    }

    private void markLastMoveAttemptBlocked(String reason, String address) {
        long ageMs = System.currentTimeMillis() - lastMoveAttemptAtMs;
        String edgeKey = routeEdgeKey(lastMoveAttemptFromX, lastMoveAttemptFromY, lastMoveAttemptDirection);
        if (edgeKey.isEmpty() || ageMs < 0L || ageMs > MOVE_ATTEMPT_BLOCK_WINDOW_MS) {
            AppLog.w(TRACE_CHAIN, TAG, "route block skipped: no recent attempt, reason=" + safe(reason)
                    + ", ageMs=" + ageMs + ", address=" + safe(address));
            return;
        }
        synchronized (this) {
            runtimeBlockedEdges.add(edgeKey);
        }
        AppLog.w(TRACE_CHAIN, TAG, "route edge blocked: edge=" + edgeKey
                + ", from=" + lastMoveAttemptFromX + "/" + lastMoveAttemptFromY
                + ", next=" + lastMoveAttemptToX + "/" + lastMoveAttemptToY
                + ", direction=" + lastMoveAttemptDirection
                + ", reason=" + safe(reason)
                + ", target=" + pendingTargetX + "/" + pendingTargetY);
        lastMoveAttemptDirection = "";
        refreshPendingRoute("blocked:" + safe(reason));
    }

    private static String routeEdgeKey(String fromX, String fromY, String direction) {
        String safeDirection = safe(direction).toLowerCase(Locale.ROOT);
        if (!isIntegerLike(fromX) || !isIntegerLike(fromY) || safeDirection.isEmpty()) {
            return "";
        }
        return safe(fromX) + "-" + safe(fromY) + ":" + safeDirection;
    }

    private MineCell getMineCell(String x, String y) {
        String key = cellKey(cleanJsToken(x), cleanJsToken(y));
        MineCell mapCell = getMapMineCell(key);
        if (mapCell != null) {
            return mapCell;
        }
        synchronized (this) {
            return mineCells.get(key);
        }
    }

    private int getMineCellCount() {
        int mapCount = getActiveMapMineCellCount();
        if (mapCount > 0) {
            return mapCount;
        }
        synchronized (this) {
            return mineCells.size();
        }
    }

    private MineCell getMapMineCell(String cellKey) {
        ensureMapMineCellsLoaded();
        String activeMapKey = mineMapKey(lastMineId, lastLevel);
        synchronized (this) {
            MineCell cell = getMapCellByKey(activeMapKey, cellKey);
            if (cell != null) {
                return cell;
            }
            if (!"1".equals(safe(lastLevel))) {
                cell = getMapCellByKey(mineMapKey(lastMineId, "1"), cellKey);
                if (cell != null) {
                    return cell;
                }
            }
            String minePrefix = safe(lastMineId) + "-";
            if (!minePrefix.equals("-")) {
                for (Map.Entry<String, Map<String, MineCell>> entry : mapMineCells.entrySet()) {
                    if (entry.getKey().startsWith(minePrefix)) {
                        cell = entry.getValue().get(cellKey);
                        if (cell != null) {
                            return cell;
                        }
                    }
                }
            }
        }
        return null;
    }

    private MineCell getMapCellByKey(String mapKey, String cellKey) {
        if (mapKey.isEmpty()) {
            return null;
        }
        Map<String, MineCell> levelCells = mapMineCells.get(mapKey);
        return levelCells == null ? null : levelCells.get(cellKey);
    }

    private int getActiveMapMineCellCount() {
        ensureMapMineCellsLoaded();
        String activeMapKey = mineMapKey(lastMineId, lastLevel);
        synchronized (this) {
            Map<String, MineCell> activeCells = mapMineCells.get(activeMapKey);
            if (activeCells != null) {
                return activeCells.size();
            }
            Map<String, MineCell> firstLevelCells = mapMineCells.get(mineMapKey(lastMineId, "1"));
            return firstLevelCells == null ? 0 : firstLevelCells.size();
        }
    }

    private void ensureMapMineCellsLoaded() {
        if (mapMineCellsLoaded || mapMineCellsLoadFailed) {
            return;
        }
        synchronized (this) {
            if (mapMineCellsLoaded || mapMineCellsLoadFailed) {
                return;
            }
            try (InputStream input = appContext.getAssets().open(MAP_ASSET_NAME)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setIgnoringComments(true);
                trySetXmlFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
                trySetXmlFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
                trySetXmlFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
                Document document = factory.newDocumentBuilder().parse(input);
                document.getDocumentElement().normalize();
                NodeList mineNodes = document.getElementsByTagName("mine");
                int cellCount = 0;
                for (int i = 0; i < mineNodes.getLength(); i++) {
                    Node mineNode = mineNodes.item(i);
                    if (mineNode.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element mineElement = (Element) mineNode;
                    String mineId = safe(mineElement.getAttribute("mineid"));
                    String level = safe(mineElement.getAttribute("level"));
                    if (mineId.isEmpty() || level.isEmpty()) {
                        continue;
                    }
                    Map<String, MineCell> levelCells = new LinkedHashMap<>();
                    NodeList cellNodes = mineElement.getElementsByTagName("cell");
                    for (int j = 0; j < cellNodes.getLength(); j++) {
                        Node cellNode = cellNodes.item(j);
                        if (cellNode.getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        }
                        Element cellElement = (Element) cellNode;
                        MineCell cell = new MineCell(
                                mineId,
                                cellElement.getAttribute("x"),
                                cellElement.getAttribute("y"),
                                cellElement.getAttribute("img"),
                                firstNonEmpty(cellElement.getAttribute("usefull"), "0"),
                                "",
                                "",
                                "");
                        if (!cell.x.isEmpty() && !cell.y.isEmpty()) {
                            levelCells.put(cellKey(cell.x, cell.y), cell);
                            cellCount++;
                        }
                    }
                    mapMineCells.put(mineMapKey(mineId, level), levelCells);
                }
                mapMineCellsLoaded = true;
                AppLog.i(TRACE_CHAIN, TAG, "map_mines asset loaded: mines=" + mapMineCells.size()
                        + ", cells=" + cellCount);
            } catch (Exception e) {
                mapMineCellsLoadFailed = true;
                AppLog.w(TRACE_CHAIN, TAG, "map_mines asset load failed", e);
            }
        }
    }

    private static String extractJsArray(String html, String varName) {
        if (html == null || html.isEmpty() || varName == null || varName.isEmpty()) {
            return "";
        }
        int varPos = html.indexOf("var " + varName);
        if (varPos < 0) {
            varPos = html.indexOf(varName + " =");
        }
        if (varPos < 0) {
            return "";
        }
        int open = html.indexOf('[', varPos);
        if (open < 0) {
            return "";
        }
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = open; i < html.length(); i++) {
            char c = html.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return html.substring(open + 1, i);
                }
            }
        }
        return "";
    }

    public long getAutoDigWaitMs() {
        long nowMs = System.currentTimeMillis();
        long dueAtMs = Math.max(nextAutoDigAllowedAtMs, AppVars.NeverTimer);
        long waitMs = dueAtMs - nowMs;
        if (waitMs <= AUTO_DIG_TIMER_MARGIN_MS) {
            if (nextAutoDigAllowedAtMs > 0L && nextAutoDigAllowedAtMs <= nowMs) {
                nextAutoDigAllowedAtMs = 0L;
            }
            return 0L;
        }
        return waitMs;
    }

    public synchronized boolean onMineDiggReport(String eventKey, int serverDelaySeconds) {
        String safeKey = safe(eventKey);
        long nowMs = System.currentTimeMillis();
        if (!safeKey.isEmpty()
                && safeKey.equals(lastMineDiggEventKey)
                && nowMs - lastMineDiggEventAtMs < MINE_DIGG_EVENT_DEDUP_MS) {
            AppLog.d(TRACE_CHAIN, TAG, "mine digg report duplicate skipped: key=" + shortCode(safeKey)
                    + ", ageMs=" + (nowMs - lastMineDiggEventAtMs));
            return false;
        }
        lastMineDiggEventKey = safeKey;
        lastMineDiggEventAtMs = nowMs;
        if (serverDelaySeconds > 0) {
            long safeDelayMs = serverDelaySeconds * 1000L;
            nextAutoDigAllowedAtMs = nowMs + safeDelayMs + AUTO_DIG_EXTRA_DELAY_MS;
            AppLog.i(TRACE_CHAIN, TAG, "mine digg delay scheduled: serverDelaySec=" + serverDelaySeconds
                    + ", extraMs=" + AUTO_DIG_EXTRA_DELAY_MS
                    + ", dueInMs=" + Math.max(0L, nextAutoDigAllowedAtMs - nowMs));
        }
        return true;
    }

    private static List<String> splitTopLevel(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        int squareDepth = 0;
        int braceDepth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
            } else if (c == '[') {
                squareDepth++;
                current.append(c);
            } else if (c == ']') {
                squareDepth--;
                current.append(c);
            } else if (c == '{') {
                braceDepth++;
                current.append(c);
            } else if (c == '}') {
                braceDepth--;
                current.append(c);
            } else if (c == ',' && squareDepth == 0 && braceDepth == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private static void appendUsefulOverlay(StringBuilder html, String useful) {
        String safeUseful = safe(useful);
        if (safeUseful.isEmpty() || "0".equals(safeUseful)) {
            return;
        }
        String color = "-1".equals(safeUseful) ? "red" : "green";
        html.append("<div style=\"position:absolute; left:2px; top:2px; width:126px; height:126px; box-sizing:border-box; background: ")
                .append(color)
                .append("; opacity: 0.3; z-index:1;\"></div>");
    }

    private static String directionFromTo(String fromX, String fromY, String toX, String toY) {
        if (!isIntegerLike(fromX) || !isIntegerLike(fromY) || !isIntegerLike(toX) || !isIntegerLike(toY)) {
            return "";
        }
        int x1 = Integer.parseInt(fromX);
        int y1 = Integer.parseInt(fromY);
        int x2 = Integer.parseInt(toX);
        int y2 = Integer.parseInt(toY);
        if (x2 - x1 == 1 && y1 == y2) return "right";
        if (x2 - x1 == -1 && y1 == y2) return "left";
        if (y2 - y1 == 1 && x1 == x2) return "down";
        if (y2 - y1 == -1 && x1 == x2) return "up";
        return "";
    }

    private static int estimateSteps(String fromX, String fromY, String toX, String toY) {
        if (!isIntegerLike(fromX) || !isIntegerLike(fromY) || !isIntegerLike(toX) || !isIntegerLike(toY)) {
            return -1;
        }
        return Math.abs(Integer.parseInt(toX) - Integer.parseInt(fromX))
                + Math.abs(Integer.parseInt(toY) - Integer.parseInt(fromY));
    }

    private static boolean isImageDirectionOpen(String img, String direction) {
        String base = safe(img).toLowerCase(Locale.ROOT);
        int suffixIndex = base.indexOf('_');
        if (suffixIndex >= 0) {
            base = base.substring(0, suffixIndex);
        }
        if ("right".equals(direction)) return base.indexOf('r') >= 0;
        if ("left".equals(direction)) return base.indexOf('l') >= 0;
        if ("up".equals(direction)) return base.indexOf('t') >= 0;
        if ("down".equals(direction)) return base.indexOf('b') >= 0;
        return false;
    }

    private static boolean isIntegerLike(String value) {
        String safeValue = safe(value);
        if (safeValue.isEmpty()) {
            return false;
        }
        int start = safeValue.charAt(0) == '-' ? 1 : 0;
        if (start >= safeValue.length()) {
            return false;
        }
        for (int i = start; i < safeValue.length(); i++) {
            if (!Character.isDigit(safeValue.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String firstNonEmpty(String first, String second) {
        String safeFirst = safe(first);
        return safeFirst.isEmpty() ? safe(second) : safeFirst;
    }

    private static String deriveMineIdFromHtml(String html) {
        String text = html == null ? "" : html;
        String mineId = deriveMineIdFromMineName(text);
        if (!mineId.isEmpty()) {
            return mineId;
        }
        return "";
    }

    private static String deriveMineIdFromMineName(String mineName) {
        String text = mineName == null ? "" : mineName;
        if (text.contains("Шахта в Деревне Подгорная")) {
            return "1";
        }
        if (text.contains("Рудник Провал")) {
            return "2";
        }
        if (text.contains("Рудник Пыльный")) {
            return "3";
        }
        return "";
    }

    private static String cellKey(String x, String y) {
        return safe(x) + "-" + safe(y);
    }

    private static String mineMapKey(String mineId, String level) {
        String safeMineId = safe(mineId);
        String safeLevel = safe(level);
        return safeMineId.isEmpty() || safeLevel.isEmpty() ? "" : safeMineId + "-" + safeLevel;
    }

    private static void trySetXmlFeature(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
            // Some Android XML parsers do not expose every hardening feature.
        }
    }

    private boolean isAutoMineEnabledByPreference() {
        try {
            return AutoFunctionsManager.getInstance(appContext).isAutoMineEnabled();
        } catch (Exception e) {
            AppLog.w(TRACE_CHAIN, TAG, "failed to read auto mine state", e);
            return false;
        }
    }

    private void disableAutoMine(String reason, String chatMessage) {
        AppLog.w(TRACE_CHAIN, TAG, "auto mine hard-stop: reason=" + safe(reason)
                + ", message=" + safe(chatMessage));
        if (AppVars.getContext() != null) {
            AutoFunctionsManager.getInstance(AppVars.getContext()).setAutoMineEnabled(false);
        }
    }

    private List<String> getEnabledNames(String prefix, String[] defaults) {
        Set<String> stored = prefs.getStringSet(scopedKey(prefix), null);
        if (stored == null || stored.isEmpty()) {
            List<String> result = new ArrayList<>();
            for (String value : defaults) {
                result.add(value);
            }
            return result;
        }
        List<String> result = new ArrayList<>();
        for (String available : defaults) {
            for (String selected : stored) {
                if (available.equals(selected)) {
                    result.add(available);
                    break;
                }
            }
        }
        if (result.isEmpty()) {
            for (String value : defaults) {
                result.add(value);
            }
        }
        return result;
    }

    private void setEnabledNames(String prefix, String[] available, Set<String> selectedNames, String label) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (selectedNames != null) {
            for (String item : available) {
                if (selectedNames.contains(item)) {
                    selected.add(item);
                }
            }
        }
        if (selected.isEmpty()) {
            for (String item : available) {
                selected.add(item);
            }
        }
        prefs.edit().putStringSet(scopedKey(prefix), selected).apply();
        AppLog.i(TRACE_CHAIN, TAG, label + " settings saved: count=" + selected.size());
    }

    private String scopedKey(String prefix) {
        String nick = AppVars.Profile != null && AppVars.Profile.UserNick != null
                ? AppVars.Profile.UserNick.trim().toLowerCase(Locale.ROOT)
                : "default";
        return prefix + nick;
    }

    private void reloadMainFrame(String source, String url, long delayMs) {
        mainHandler.postDelayed(() -> {
            try {
                MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
                if (activity == null || activity.getMainWebView() == null) {
                    AppLog.w(TRACE_CHAIN, TAG, "reload skipped: activity/webview null, source=" + safe(source));
                    return;
                }
                String loadUrl = normalizeGameUrl(url);
                activity.getMainWebView().loadUrl(loadUrl);
                AppLog.i(TRACE_CHAIN, TAG, "reload main frame: source=" + safe(source) + ", url=" + loadUrl);
            } catch (Exception e) {
                AppLog.w(TRACE_CHAIN, TAG, "reload failed: source=" + safe(source), e);
            }
        }, Math.max(0L, delayMs));
    }

    private static String normalizeGameUrl(String url) {
        String safeUrl = safe(url);
        if (safeUrl.startsWith("http://") || safeUrl.startsWith("https://") || safeUrl.startsWith("javascript:")) {
            return safeUrl;
        }
        if (safeUrl.startsWith("/")) {
            return "http://neverlands.ru" + safeUrl;
        }
        return "http://neverlands.ru/" + safeUrl;
    }

    private static String cleanJsToken(String value) {
        if (value == null) return "";
        String result = value.trim();
        while ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("'") && result.endsWith("'"))) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String shortCode(String value) {
        String safeValue = safe(value);
        if (safeValue.length() <= 8) {
            return safeValue;
        }
        return safeValue.substring(0, 8) + "...";
    }

    private static String escapeJs(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static final class MineCell {
        final String mineId;
        final String x;
        final String y;
        final String img;
        final String usefull;
        final String right;
        final String up;
        final String down;

        MineCell(String mineId, String x, String y, String img, String usefull,
                 String right, String up, String down) {
            this.mineId = safe(mineId);
            this.x = safe(x);
            this.y = safe(y);
            this.img = safe(img);
            this.usefull = safe(usefull);
            this.right = safe(right);
            this.up = safe(up);
            this.down = safe(down);
        }
    }
}
