package ru.neverlands.abclient.bridge;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.manager.CharacterVitalsManager;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.model.Cell;
import ru.neverlands.abclient.model.Position;
import ru.neverlands.abclient.model.Prims;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.Russian;

/**
 * Класс-мост (bridge) для взаимодействия между JavaScript в WebView и нативным кодом Android.
 * Методы, аннотированные @JavascriptInterface, могут быть вызваны из JS.
 * В JS этот объект доступен как `AndroidBridge`.
 */
public class WebAppInterface {
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";
    private static final String MAIN_TOP_TRACE_PREFIX = "[MAIN_TOP_TRACE]";
    private static final long MAIN_TOP_FIGHT_PULSE_GUARD_MS = 15000L;
    private static final long MAIN_TOP_CLIENT_RELOAD_GUARD_MS = 2500L;
    private static final long MAP_BRIDGE_LOG_THROTTLE_MS = 1500L;
    private static volatile long lastNeverTimerLogAtMs = 0L;
    private static volatile long lastNeverTimerLoggedValueMs = Long.MIN_VALUE;
    private static volatile long lastMapBridgeLogAtMs = 0L;
    private static volatile String lastMapBridgeSignature = "";
    private static volatile long lastMapRuntimeTraceAtMs = 0L;
    private static volatile String lastMapRuntimeTrace = "";
    private static volatile long lastClientMainTopReloadAtMs = 0L;
    private static volatile String lastClientMainTopReloadSource = "";
    private static volatile String lastClientMainTopReloadPayload = "";
    Context mContext;

    /** Конструктор, инициализирующий контекст. */
    public WebAppInterface(Context c) {
        mContext = c;
    }

    private MainActivity getMainActivityOrNull() {
        if (AppVars.mainActivity == null) return null;
        return AppVars.mainActivity.get();
    }

    private void logMapBridgeValue(String source, int mapBigWidth, int mapBigHeight, int mapBigScale, int halfW, int halfH) {
        long nowMs = System.currentTimeMillis();
        String signature = source
                + "|w=" + mapBigWidth
                + "|h=" + mapBigHeight
                + "|s=" + mapBigScale
                + "|half=" + halfW + "x" + halfH;
        boolean shouldLog = !signature.equals(lastMapBridgeSignature)
                || (nowMs - lastMapBridgeLogAtMs) >= MAP_BRIDGE_LOG_THROTTLE_MS;
        if (!shouldLog) {
            return;
        }
        lastMapBridgeLogAtMs = nowMs;
        lastMapBridgeSignature = signature;
        Log.d("WebAppInterface", "MAP_BRIDGE " + signature);
    }

    /** Показывает всплывающее сообщение (Toast) из веб-страницы. */
    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
    }

    /**
     * Возвращает ID класса контакта (друг, враг, нейтрал).
     * Вызывается из ch_list.js для определения цвета ника.
     * @param name Ник персонажа.
     * @return ID класса.
     */
    @JavascriptInterface
    public String GetClassIdOfContact(String name) {
        return String.valueOf(ContactsManager.getClassIdOfContact(name));
    }

    /**
     * C# parity: сообщает map.js, включена ли Авто-Рыбалка.
     */
    @JavascriptInterface
    public boolean IsAutoFish() {
        try {
            if (mContext != null) {
                return AutoFunctionsManager.getInstance(mContext).isAutoFishEnabled();
            }
        } catch (Exception e) {
            Log.w("WebAppInterface", "IsAutoFish failed", e);
        }
        return AppVars.Profile != null && AppVars.Profile.AutoFish;
    }

    /**
     * C# parity: map.js может проверить, активен ли сейчас Навигатор.
     * Используется в patched timerst, чтобы после завершения server timer
     * продолжать цикл переходов через go=inf без ручного клика.
     */
    @JavascriptInterface
    public boolean IsAutoMoving() {
        return AppVars.AutoMoving;
    }

    /**
     * C# parity (`ScriptManager.ShowOverWarning`):
     * map.js спрашивает, нужно ли показывать окно предупреждения о перегрузе.
     */
    @JavascriptInterface
    public boolean ShowOverWarning() {
        return AppVars.Profile != null && AppVars.Profile.ShowOverWarning;
    }

    /**
     * C# parity (`ScriptManager.GetHalfMapWidth`): half-width большой карты в ячейках.
     *
     * Зависимости:
     * - `UserConfig.MapBigWidth` (полная ширина карты);
     * - JS `map.js`, где размер окна карты строится через `GetHalfMapWidth()`.
     *
     * Примечание:
     * - значение принудительно ограничено минимумом 1, чтобы не допустить нулевую/отрицательную ширину.
     */
    @JavascriptInterface
    public int GetHalfMapWidth() {
        int mapBigWidth = (AppVars.Profile != null) ? AppVars.Profile.MapBigWidth : 9;
        int mapBigHeight = (AppVars.Profile != null) ? AppVars.Profile.MapBigHeight : 7;
        int mapBigScale = (AppVars.Profile != null) ? AppVars.Profile.MapBigScale : 75;
        int half = (mapBigWidth - 1) / 2;
        int halfW = Math.max(1, half);
        int halfH = Math.max(1, (mapBigHeight - 1) / 2);
        logMapBridgeValue("GetHalfMapWidth", mapBigWidth, mapBigHeight, mapBigScale, halfW, halfH);
        return halfW;
    }

    /**
     * C# parity (`ScriptManager.GetHalfMapHeight`): half-height большой карты в ячейках.
     *
     * Зависимости:
     * - `UserConfig.MapBigHeight` (полная высота карты);
     * - JS `map.js`, где размер окна карты строится через `GetHalfMapHeight()`.
     *
     * Примечание:
     * - значение принудительно ограничено минимумом 1, чтобы не допустить нулевую/отрицательную высоту.
     */
    @JavascriptInterface
    public int GetHalfMapHeight() {
        int mapBigWidth = (AppVars.Profile != null) ? AppVars.Profile.MapBigWidth : 9;
        int mapBigHeight = (AppVars.Profile != null) ? AppVars.Profile.MapBigHeight : 7;
        int mapBigScale = (AppVars.Profile != null) ? AppVars.Profile.MapBigScale : 75;
        int half = (mapBigHeight - 1) / 2;
        int halfH = Math.max(1, half);
        int halfW = Math.max(1, (mapBigWidth - 1) / 2);
        logMapBridgeValue("GetHalfMapHeight", mapBigWidth, mapBigHeight, mapBigScale, halfW, halfH);
        return halfH;
    }

    /**
     * C# parity (`ScriptManager.GetMapScale`): масштаб большой карты в процентах.
     *
     * Зависимости:
     * - `UserConfig.MapBigScale`;
     * - настройки "Общие" (`SettingsActivity` + `root_preferences.xml`);
     * - JS `map.js`, где `scale` используется для размера тайлов.
     *
     * Ограничение диапазона:
     * - поддерживаем 50..100 (в рамках Android UI-настроек);
     * - fallback 75 для профилей без параметра.
     */
    @JavascriptInterface
    public int GetMapScale() {
        int mapBigWidth = (AppVars.Profile != null) ? AppVars.Profile.MapBigWidth : 9;
        int mapBigHeight = (AppVars.Profile != null) ? AppVars.Profile.MapBigHeight : 7;
        int scale = (AppVars.Profile != null) ? AppVars.Profile.MapBigScale : 75;
        if (scale < 50) scale = 50;
        if (scale > 100) scale = 100;
        int halfW = Math.max(1, (mapBigWidth - 1) / 2);
        int halfH = Math.max(1, (mapBigHeight - 1) / 2);
        logMapBridgeValue("GetMapScale", mapBigWidth, mapBigHeight, scale, halfW, halfH);
        return scale;
    }

    /**
     * C# parity (`ScriptManager.DoHideMiniMap`): скрытие миникарты во время движения.
     *
     * В текущем Android-профиле отдельная настройка миникарты не портирована, поэтому
     * возвращаем `false` (миникарта не скрывается).
     */
    @JavascriptInterface
    public boolean DoHideMiniMap() {
        return false;
    }

    /**
     * C# parity (`ScriptManager.UsersOnline`): HTML-вставка "кто онлайн" в верхней панели карты.
     *
     * В Android-порте отдельная строка `UsersOnline` не ведётся, поэтому возвращаем пустую вставку.
     */
    @JavascriptInterface
    public String UsersOnline() {
        return "";
    }

    private void ensureExtMapInitialized() {
        try {
            if (mContext != null) {
                ExtMap.init(mContext);
            }
        } catch (Exception e) {
            Log.e("WebAppInterface", "ensureExtMapInitialized failed", e);
        }
    }

    /**
     * C# parity (`ScriptManager.IsCellExists`): проверка, что клетка доступна в справочнике карты.
     */
    @JavascriptInterface
    public boolean IsCellExists(int x, int y) {
        ensureExtMapInitialized();
        String pos = ExtMap.makePosition(x, y);
        Position p = ExtMap.Location.get(pos);
        return p != null && p.RegNum != null && ExtMap.Cells.containsKey(p.RegNum);
    }

    /**
     * C# parity (`ScriptManager.GenMoveLink`): генерация назначения для MoveTo.
     *
     * Для карты это `regnum` клетки (например `8-259`).
     */
    @JavascriptInterface
    public String GenMoveLink(int x, int y) {
        ensureExtMapInitialized();
        String pos = ExtMap.makePosition(x, y);
        Position p = ExtMap.Location.get(pos);
        if (p == null || p.RegNum == null) {
            return "";
        }
        return p.RegNum;
    }

    /**
     * C# parity (`ScriptManager.MoveTo`): запуск навигации к клетке назначения.
     *
     * Зависимости:
     * - `AutoFunctionsManager.startAutoMoving(dest)` — старт маршрута;
     * - `MainPhp/MapAjax` — выполнение переходов по серверным ответам.
     */
    @JavascriptInterface
    public void MoveTo(String dest) {
        if (dest == null) {
            return;
        }
        String safeDest = dest.trim();
        if (safeDest.isEmpty()) {
            return;
        }
        ensureExtMapInitialized();
        if (!ExtMap.Cells.containsKey(safeDest)) {
            Log.w("WebAppInterface", "MoveTo: unknown destination " + safeDest);
            return;
        }
        try {
            AutoFunctionsManager.getInstance(mContext).startAutoMoving(safeDest);
            Log.d("WebAppInterface", "MoveTo: startAutoMoving " + safeDest);
        } catch (Exception e) {
            Log.e("WebAppInterface", "MoveTo failed for " + safeDest, e);
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String shortLabel(Cell cell) {
        if (cell == null) {
            return "";
        }
        String base = cell.Tooltip != null && !cell.Tooltip.isEmpty() ? cell.Tooltip : cell.Name;
        if (base == null) {
            return "";
        }
        int comma = base.indexOf(',');
        if (comma >= 0 && comma + 1 < base.length()) {
            return base.substring(comma + 1).trim();
        }
        return base.trim();
    }

    private static String hexColorCost(int cost) {
        if (cost <= 20) return "#66CC66";
        if (cost <= 30) return "#C3C35A";
        if (cost <= 40) return "#D9A24D";
        if (cost <= 60) return "#E07A5F";
        return "#CC6666";
    }

    private static long toServerClockMs(long localMs) {
        if (AppVars.Profile != null && AppVars.Profile.ServDiff != Long.MIN_VALUE) {
            return localMs - AppVars.Profile.ServDiff;
        }
        return localMs;
    }

    private static long nowServerClockMs() {
        return toServerClockMs(System.currentTimeMillis());
    }

    private static int interpolateColorComponent(int start, int end, double fraction) {
        double clamped = Math.max(0.0d, Math.min(1.0d, fraction));
        return (int) Math.round((start * (1.0d - clamped)) + (end * clamped));
    }

    private static String hexColorVisited(double hours) {
        int startR;
        int startG;
        int startB;
        int endR;
        int endG;
        int endB;
        double fraction;

        // C# parity (`Map.ColorVisited`): LightGreen -> Yellow -> Red в пределах первых 6 часов.
        if (hours < 0.0d) {
            startR = endR = 0x90;
            startG = endG = 0xEE;
            startB = endB = 0x90;
            fraction = 0.0d;
        } else if (hours < 1.0d) {
            startR = 0x90; startG = 0xEE; startB = 0x90;
            endR = 0xFF; endG = 0xFF; endB = 0x00;
            fraction = hours;
        } else if (hours < 6.0d) {
            startR = 0xFF; startG = 0xFF; startB = 0x00;
            endR = 0xFF; endG = 0x00; endB = 0x00;
            fraction = (hours - 1.0d) / 5.0d;
        } else {
            startR = endR = 0xFF;
            startG = endG = 0x00;
            startB = endB = 0x00;
            fraction = 0.0d;
        }

        int r = interpolateColorComponent(startR, endR, fraction);
        int g = interpolateColorComponent(startG, endG, fraction);
        int b = interpolateColorComponent(startB, endB, fraction);
        return String.format(Locale.US, "#%02X%02X%02X", r, g, b);
    }

    private static boolean isRegnumInCurrentPath(String regNum) {
        if (!AppVars.AutoMoving || AppVars.AutoMovingMapPath == null || regNum == null) {
            return false;
        }
        String[] path = AppVars.AutoMovingMapPath.path;
        if (path == null || path.length == 0) {
            return false;
        }
        int startIndex = 0;
        String currentLocation = (AppVars.Profile != null) ? AppVars.Profile.MapLocation : null;
        if (currentLocation != null && !currentLocation.isEmpty()) {
            for (int i = 0; i < path.length; i++) {
                if (currentLocation.equals(path[i])) {
                    startIndex = i + 1;
                    break;
                }
            }
        }
        if (startIndex >= path.length) {
            return false;
        }
        for (int i = startIndex; i < path.length; i++) {
            if (regNum.equals(path[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * C# parity (`ScriptManager.CellDivText`): HTML-оверлей клетки на карте.
     *
     * Возвращает компактную подпись (номер, название, спец-метки) и подсветку:
     * - красная рамка для текущей клетки/шага движения;
     * - красная рамка для клеток текущего маршрута AutoMoving.
     */
    @JavascriptInterface
    public String CellDivText(int x, int y, int scale, String link, boolean showmove, boolean isframe) {
        ensureExtMapInitialized();
        String pos = ExtMap.makePosition(x, y);
        Position p = ExtMap.Location.get(pos);
        if (p == null || p.RegNum == null) {
            return "";
        }
        Cell cell = ExtMap.Cells.get(p.RegNum);
        if (cell == null) {
            return "";
        }

        int tileSize = Math.max(24, scale);
        int borderSizePx = Math.max(1, Math.round(tileSize * 0.015f));
        int paddingPx = Math.max(1, Math.round(tileSize * 0.03f));
        int fontSizePx = (AppVars.Profile != null) ? AppVars.Profile.MapCellFontSize : 9;
        if (fontSizePx < 6) fontSizePx = 6;
        if (fontSizePx > 24) fontSizePx = 24;
        int regNumFontSizePx = Math.min(28, fontSizePx + 2);
        boolean highlight = showmove || isframe || isRegnumInCurrentPath(p.RegNum);
        String border = highlight ? "border:" + borderSizePx + "px solid red;" : "";
        String idAttr = showmove ? "id=\"movingcell\" " : "";

        StringBuilder sb = new StringBuilder();
        sb.append("<div ").append(idAttr)
                .append("style=\"position:relative; width:")
                .append(tileSize)
                .append("px; height:")
                .append(tileSize)
                .append("px; box-sizing:border-box; overflow:hidden; ")
                .append(border)
                .append(" text-shadow:none; font-family:Tahoma; font-size:")
                .append(fontSizePx)
                .append("px; line-height:1.15; font-weight:bold; text-decoration:none;\">");
        if (isframe) {
            sb.append("<img src=\"http://image.neverlands.ru/map/nl_cursor.png\" style=\"position:absolute;left:0;top:0;width:")
                    .append(tileSize)
                    .append("px;height:")
                    .append(tileSize)
                    .append("px;pointer-events:none;z-index:1;\"/>");
        }
        sb.append("<div style=\"position:relative;z-index:2;padding:")
                .append(paddingPx)
                .append("px;box-sizing:border-box;width:100%;height:100%;\">");
        sb.append("<span style=\"font-size:")
                .append(regNumFontSizePx)
                .append("px; color:")
                .append(hexColorCost(cell.Cost))
                .append("\">")
                .append(escapeHtml(p.RegNum))
                .append("</span>");
        String shortLabel = shortLabel(cell);
        if (!shortLabel.isEmpty()) {
            sb.append("<br><span style=\"color:#C0C0C0\">")
                    .append(escapeHtml(shortLabel))
                    .append("</span>");
        }
        if (cell.HasFish) {
            sb.append("<br><span style=\"color:#33CCFF\">Рыба</span>");
        } else if (cell.HasWater) {
            sb.append("<br><span style=\"color:#33CCFF\">Вода</span>");
        }
        if (cell.MaxBotLevel > 0) {
            sb.append("<br><span style=\"color:#88BBDD\">Боты до ")
                    .append(cell.MaxBotLevel)
                    .append("</span>");
        }
        if (cell.HerbGroup != null && !cell.HerbGroup.isEmpty() && !"0".equals(cell.HerbGroup)) {
            sb.append("<br><span style=\"color:#999999\">Травы ")
                    .append(escapeHtml(cell.HerbGroup))
                    .append("</span>");
        }
        Long visitedAtMs = AppVars.SearchBoxVisited.get(p.RegNum);
        if (visitedAtMs != null && visitedAtMs > 0L) {
            long visitedServerMs = toServerClockMs(visitedAtMs);
            long spanMs = Math.max(0L, nowServerClockMs() - visitedServerMs);
            if (spanMs < (24L * 60L * 60L * 1000L)) {
                double spanHours = (double) spanMs / (60.0d * 60.0d * 1000.0d);
                String visitedColor = hexColorVisited(spanHours);
                String visitedTime = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(new Date(visitedServerMs));
                sb.append("<br><span style=\"color:")
                        .append(visitedColor)
                        .append("\">")
                        .append(escapeHtml(visitedTime))
                        .append("</span>");
            }
        }
        sb.append("</div></div>");
        return sb.toString();
    }

    /**
     * C# parity (`ScriptManager.CellAltText`): tooltip клетки.
     */
    @JavascriptInterface
    public String CellAltText(int x, int y, int scale) {
        ensureExtMapInitialized();
        String pos = ExtMap.makePosition(x, y);
        Position p = ExtMap.Location.get(pos);
        if (p == null || p.RegNum == null) {
            return "";
        }
        Cell cell = ExtMap.Cells.get(p.RegNum);
        if (cell == null) {
            return "";
        }
        String tooltip = cell.Tooltip != null && !cell.Tooltip.isEmpty() ? cell.Tooltip : cell.Name;
        return tooltip == null ? "" : tooltip;
    }

    /**
     * C# parity (`FormMain.MapText`): текст-подсказка над картой.
     */
    @JavascriptInterface
    public String MapText() {
        if (AppVars.AutoMoving && AppVars.AutoMovingJumps > 0) {
            String destination = AppVars.AutoMovingDestinaton == null ? "?" : escapeHtml(AppVars.AutoMovingDestinaton);
            int curTire = Math.max(0, Math.min(100, AppVars.Tied));
            return "<font color=#FF3333>Пункт назначения:</font> <font color=#FFFF00>" + destination + "</font>"
                    + "<br><font color=#FF3333>Еще переходов:</font> <font color=#FFFF00>" + AppVars.AutoMovingJumps + "</font>"
                    + "<br><font color=#FF3333>Текущая Усталость:</font> <font color=#FFFF00>" + curTire + "</font>";
        }
        return "Перемещаемся на соседнюю клетку...";
    }

    /**
     * C# parity (`FormMain.HerbsList`): приём списка доступных трав из map.js.
     *
     * Пока используется как трассировка для отладки и совместимости.
     */
    /**
     * Runtime-синхронизация усталости из map.js/hpmp.js.
     */
    @JavascriptInterface
    public void SetCurrentTied(int curTire) {
        CharacterVitalsManager.Snapshot before = CharacterVitalsManager.snapshot();
        CharacterVitalsManager.Snapshot after = CharacterVitalsManager.updateTied(curTire, "WebAppInterface.SetCurrentTied");
        if (before.tied != after.tied) {
            Log.d("WebAppInterface", "SetCurrentTied: old=" + before.tied + ", new=" + after.tied);
        }
    }
    @JavascriptInterface
    public String HerbsList(String list) {
        if (list != null && !list.isEmpty()) {
            Log.d("WebAppInterface", "HerbsList: " + list);
        }
        return "";
    }

    /**
     * C# parity (`FormMain.TraceCut`): сигнал выбора травы в map.js.
     *
     * Сейчас сохраняем диагностику; функционал авто-среза обрабатывается в map/ajax потоке.
     */
    @JavascriptInterface
    public void TraceCut(String herb) {
        if (herb != null && !herb.isEmpty()) {
            Log.d("WebAppInterface", "TraceCut: " + herb);
        }
    }

    /**
     * Runtime-трассировка состояния карты из map.js.
     *
     * Назначение:
     * - диагностировать расхождения 3x3 vs 9x7;
     * - видеть фактические width/height/scale в момент рендера и после redraw.
     *
     * Зависимости:
     * - вызывается из патча `MAP_DIM_RUNTIME_GUARD_PATCH` в `MapJs`;
     * - логируется в Logcat тегом `WebAppInterface`.
     */
    @JavascriptInterface
    public void TraceMapRuntime(String payload) {
        String safePayload = payload == null ? "" : payload.trim();
        long nowMs = System.currentTimeMillis();
        boolean shouldLog = !safePayload.equals(lastMapRuntimeTrace)
                || (nowMs - lastMapRuntimeTraceAtMs) >= MAP_BRIDGE_LOG_THROTTLE_MS;
        if (!shouldLog) {
            return;
        }
        lastMapRuntimeTraceAtMs = nowMs;
        lastMapRuntimeTrace = safePayload;
        Log.d("WebAppInterface", "MAP_RUNTIME " + safePayload);
    }

    /**
     * C# parity: map.js передаёт текущую массу инвентаря перед забросом.
     */
    @JavascriptInterface
    public void SetAutoFishMassa(String massa) {
        AppVars.AutoFishMassa = massa == null ? "" : massa.trim();
    }

    /**
     * C# parity (`ScriptManager.SetNeverTimer`): map.js передаёт остаток таймера в миллисекундах
     * (переменная `time_left_sec`), а клиент фиксирует абсолютное время следующего действия.
     *
     * Зависимости:
     * - `app/src/main/assets/js/map.js`: `window.external.SetNeverTimer(time_left_sec)` в `timerst(lp)`;
     * - `AppVars.NeverTimer`: общий cooldown-гейт для авто-функций в `MainPhp`;
     * - `MainPhp`: проверки вида `System.currentTimeMillis() > AppVars.NeverTimer`.
     *
     * Поведение:
     * - вход < 0 нормализуется в 0, чтобы не уводить таймер в прошлое;
     * - `NeverTimer` сохраняется как `текущее_время + остаток_мс` (как в ПК-версии: `DateTime.Now.AddMilliseconds`).
     */
    @JavascriptInterface
    public void SetNeverTimer(long msLeft) {
        if (msLeft < 0L) {
            msLeft = 0L;
        }
        long nowMs = System.currentTimeMillis();
        long dueAtMs = nowMs + msLeft;
        AppVars.NeverTimer = dueAtMs;

        // Debug-трасса countdown из map.js (throttle: по дельте/времени/последним секундам).
        long prevLogAt = lastNeverTimerLogAtMs;
        long prevLoggedValue = lastNeverTimerLoggedValueMs;
        boolean shouldLog = prevLoggedValue == Long.MIN_VALUE
                || Math.abs(prevLoggedValue - msLeft) >= 5000L
                || (nowMs - prevLogAt) >= 10000L
                || msLeft <= 5000L;
        if (shouldLog) {
            lastNeverTimerLogAtMs = nowMs;
            lastNeverTimerLoggedValueMs = msLeft;
            Log.d("WebAppInterface", "SetNeverTimer: msLeft=" + msLeft
                    + " (" + (msLeft / 1000L) + "s), dueInMs=" + Math.max(0L, dueAtMs - nowMs));
        }
    }

    /**
     * C# parity (`CheckPri`): выбирает первую подходящую приманку (остаток > 4) и
     * возвращает `" CHECKED"` для вставки в HTML radio.
     */
    @JavascriptInterface
    public String CheckPri(String namePri, int myst) {
        if (AppVars.Profile == null) {
            return "";
        }
        if (AppVars.PriSelected || myst <= 4 || namePri == null || namePri.isEmpty()) {
            return "";
        }

        if (isPrimEnabledByName(namePri)) {
            AppVars.PriSelected = true;
            AppVars.NamePri = namePri;
            AppVars.ValPri = myst;
            String primId = resolvePrimIdByName(namePri);
            if (primId != null) {
                AppVars.AutoFishLikeId = primId;
                AppVars.AutoFishLikeVal = String.valueOf(myst);
            }
            return " CHECKED";
        }
        return "";
    }

    /**
     * C# parity: map.js сигнализирует, что капчи нет и можно сразу продолжить заброс.
     *
     * Что делает метод:
     * - в UI-потоке пытается нажать `fishbutton` в текущем map/fish фрейме;
     * - если кнопка недоступна, делает fallback на `FishStart(ingr[2], 0)`.
     *
     * Зависимости:
     * - `MainActivity` + `WebView` (доступ к текущей странице);
     * - JS-контекст страницы (`fishbutton`, `FishStart`, `ingr`);
     * - runtime-логирование `AUTO_FISH` через `Log.d`.
     */
    @JavascriptInterface
    public void SetFishNoCaptchaReady() {
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (activity.binding == null || activity.binding.appBarMain == null
                    || activity.binding.appBarMain.contentMain == null
                    || activity.binding.appBarMain.contentMain.webView == null) {
                return;
            }
            activity.binding.appBarMain.contentMain.webView.evaluateJavascript(
                    "(function(){"
                            + "try{"
                            + "  var btn=document.getElementById('fishbutton');"
                            + "  if(btn&&typeof btn.click==='function'){btn.click(); return 'button';}"
                            + "  if(typeof FishStart==='function' && typeof ingr!=='undefined' && ingr && ingr.length>2){"
                            + "    FishStart(ingr[2],0); return 'fishstart';"
                            + "  }"
                            + "}catch(e){return 'error:'+e;}"
                            + "return 'miss';"
                            + "})()",
                    value -> Log.d("WebAppInterface", "SetFishNoCaptchaReady: " + value));
        });
    }

    /**
     * C# parity (`InsertGuaDiv`): вставляет блок Guamod под captcha в fish-окне.
     */
    @JavascriptInterface
    public String InsertGuaDiv(String code) {
        if (AppVars.Profile != null && AppVars.Profile.DoGuamod) {
            return "<br><img src=http://image.neverlands.ru/1x1.gif width=1 height=8><br>"
                    + "<span id=guamod3><font class=nickname><font color=#004A7F><b>* * * *</b></font></font></span>";
        }
        return "";
    }

    /**
     * C# parity (`FormMainCross.FishOverload`):
     * вызывается из map.js при перегрузе инвентаря во время рыбалки.
     *
     * Поведение (1:1 с C#):
     * - Если {@code FishStopOverWeight = false} — просто возвращаемся, ничего не делаем;
     * - Если {@code FishStopOverWeight = true} — останавливаем авто-рыбалку.
     *
     * Зависимости:
     * - {@link ru.neverlands.abclient.model.UserConfig#FishStopOverWeight};
     * - {@link AutoFunctionsManager#setAutoFishEnabled(boolean)} — останавливает авто-рыбалку.
     */
    @JavascriptInterface
    public void FishOverload() {
        if (AppVars.Profile == null || !AppVars.Profile.FishStopOverWeight) {
            return;
        }
        try {
            AutoFunctionsManager.getInstance(mContext).setAutoFishEnabled(false);
        } catch (Exception e) {
            android.util.Log.e("WebAppInterface", "FishOverload: stopAutoFish failed", e);
        }
    }

    /**
     * Проверяет, включена ли приманка в настройках профиля по её текстовому имени.
     *
     * Зависимости:
     * - `AppVars.Profile.FishEnabledPrims` (битовая маска);
     * - константы `Prims` как источник соответствия "имя -> флаг".
     *
     * Используется из `CheckPri(...)` при парсинге HTML-радиокнопок приманок.
     */
    private boolean isPrimEnabledByName(String namePri) {
        if (AppVars.Profile == null || namePri == null) {
            return false;
        }
        int mask = AppVars.Profile.FishEnabledPrims;
        if ("Хлеб".equalsIgnoreCase(namePri)) return (mask & Prims.Bread) != 0;
        if ("Червяк".equalsIgnoreCase(namePri)) return (mask & Prims.Worm) != 0;
        if ("Крупный червяк".equalsIgnoreCase(namePri)) return (mask & Prims.BigWorm) != 0;
        if ("Опарыш".equalsIgnoreCase(namePri)) return (mask & Prims.Stink) != 0;
        if ("Мотыль".equalsIgnoreCase(namePri)) return (mask & Prims.Fly) != 0;
        if ("Блесна".equalsIgnoreCase(namePri)) return (mask & Prims.Light) != 0;
        if ("Донка".equalsIgnoreCase(namePri)) return (mask & Prims.Donka) != 0;
        if ("Мормышка".equalsIgnoreCase(namePri)) return (mask & Prims.Morm) != 0;
        if ("Заговоренная блесна".equalsIgnoreCase(namePri)) return (mask & Prims.HiFlight) != 0;
        return false;
    }

    /**
     * Преобразует имя приманки в server `primid` (38..46) для `fish_ajax.php`.
     *
     * Зависимости:
     * - стабильное соответствие имён приманок и id из протокола сервера;
     * - используется в `CheckPri(...)` для заполнения runtime-полей `AutoFishLikeId/Val`.
     */
    private String resolvePrimIdByName(String namePri) {
        if (namePri == null) return null;
        if ("Хлеб".equalsIgnoreCase(namePri)) return "38";
        if ("Червяк".equalsIgnoreCase(namePri)) return "39";
        if ("Крупный червяк".equalsIgnoreCase(namePri)) return "40";
        if ("Опарыш".equalsIgnoreCase(namePri)) return "41";
        if ("Мотыль".equalsIgnoreCase(namePri)) return "42";
        if ("Блесна".equalsIgnoreCase(namePri)) return "43";
        if ("Донка".equalsIgnoreCase(namePri)) return "44";
        if ("Мормышка".equalsIgnoreCase(namePri)) return "45";
        if ("Заговоренная блесна".equalsIgnoreCase(namePri)) return "46";
        return null;
    }

    // --- Методы для проверки отображения кнопок быстрых действий --- //
    // --- Логика портирована из ScriptManager.cs --- //

    /**
     * Проверяет, нужно ли отображать кнопку быстрого действия.
     * @param login Ник цели.
     * @param wmlabQ HTML-код кнопки.
     * @return HTML-код кнопки, если ее нужно показать, иначе - пустая строка.
     */
    @JavascriptInterface
    public String CheckQuick(String login, String wmlabQ) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return wmlabQ;
    }

    @JavascriptInterface
    public String CheckFastAttack(String login, String wmlabFA) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttack ? wmlabFA : "";
    }

    @JavascriptInterface
    public String CheckFastAttackBlood(String login, String wmlabFAB) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackBlood ? wmlabFAB : "";
    }

    @JavascriptInterface
    public String CheckFastAttackUltimate(String login, String wmlabFAU) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackUltimate ? wmlabFAU : "";
    }

    @JavascriptInterface
    public String CheckFastAttackClosedUltimate(String login, String wmlabFACU) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackClosedUltimate ? wmlabFACU : "";
    }

    @JavascriptInterface
    public String CheckFastAttackFist(String login, String wmlabFAF) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackFist ? wmlabFAF : "";
    }

    @JavascriptInterface
    public String CheckFastAttackClosedFist(String login, String wmlabFACF) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackClosedFist ? wmlabFACF : "";
    }

    @JavascriptInterface
    public String CheckFastAttackPortal(String login, String wmlabFP) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackPortal ? wmlabFP : "";
    }

    @JavascriptInterface
    public String CheckFastAttackClosed(String login, String wmlabFC) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackClosed ? wmlabFC : "";
    }

    @JavascriptInterface
    public String CheckFastAttackPoison(String login, String wmlabFAP) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackPoison ? wmlabFAP : "";
    }

    @JavascriptInterface
    public String CheckFastAttackStrong(String login, String wmlabFAS) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackStrong ? wmlabFAS : "";
    }

    @JavascriptInterface
    public String CheckFastAttackNevid(String login, String wmlabFAN) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackNevid ? wmlabFAN : "";
    }

    @JavascriptInterface
    public String CheckFastAttackFog(String login, String wmlabFAFG) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackFog ? wmlabFAFG : "";
    }

    @JavascriptInterface
    public String CheckFastAttackZas(String login, String wmlabFAZ) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackZas ? wmlabFAZ : "";
    }

    @JavascriptInterface
    public String CheckFastAttackTotem(String login, String wmlabFTOT) {
        if (AppVars.Profile == null || login.equalsIgnoreCase(AppVars.Profile.UserNick)) {
            return "";
        }
        return AppVars.Profile.doShowFastAttackTotem ? wmlabFTOT : "";
    }

    // --- Методы для выполнения быстрых действий --- //

    @JavascriptInterface
    public void Quick(String login) {
        Toast.makeText(mContext, "Quick: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttack(String login) {
        Toast.makeText(mContext, "FastAttack: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackBlood(String login) {
        Toast.makeText(mContext, "FastAttackBlood: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackUltimate(String login) {
        Toast.makeText(mContext, "FastAttackUltimate: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackClosedUltimate(String login) {
        Toast.makeText(mContext, "FastAttackClosedUltimate: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackFist(String login) {
        Toast.makeText(mContext, "FastAttackFist: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackClosedFist(String login) {
        Toast.makeText(mContext, "FastAttackClosedFist: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackPortal(String login) {
        Toast.makeText(mContext, "FastAttackPortal: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackClosed(String login) {
        Toast.makeText(mContext, "FastAttackClosed: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackPoison(String login) {
        Toast.makeText(mContext, "FastAttackPoison: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackStrong(String login) {
        Toast.makeText(mContext, "FastAttackStrong: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackNevid(String login) {
        Toast.makeText(mContext, "FastAttackNevid: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackFog(String login) {
        Toast.makeText(mContext, "FastAttackFog: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackZas(String login) {
        Toast.makeText(mContext, "FastAttackZas: " + login, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void FastAttackTotem(String login) {
        Toast.makeText(mContext, "FastAttackTotem: " + login, Toast.LENGTH_SHORT).show();
    }


    @JavascriptInterface
    public void showSmiles(int index) {
        // TODO: Implement a native smiles dialog
        Toast.makeText(mContext, "Show smiles: " + index, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public String chatFilter(String message) {
        // Пропуск входящих сообщений чата через Java‑фильтр (XP/лут/системные).
        String safeMessage = message == null ? "" : message;
        Log.d("ChatFilter", safeMessage);
        return ru.neverlands.abclient.utils.ChatFilter.filter(safeMessage);
    }

    @JavascriptInterface
    public void chatUpdated() {
        // Сигнал из JS: чат обновился (нужен для автоответов).
        Log.d("WebAppInterface", "Chat updated");
        ru.neverlands.abclient.utils.Chat.chatUpdated();
    }

    @JavascriptInterface
    public void chatAddMsg(String message) {
        // Добавление сообщения в окно чата (вызов JS add_msg в chatMsgWebview).
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        String safeMessage = message == null ? "" : message;
        safeMessage = adjustClanMarkers(safeMessage);
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(safeMessage);
        activity.runOnUiThread(() -> {
            if (activity.binding != null && activity.binding.appBarMain != null
                    && activity.binding.appBarMain.contentMain != null
                    && activity.binding.appBarMain.contentMain.chatMsgWebview != null) {
                activity.binding.appBarMain.contentMain.chatMsgWebview
                        .evaluateJavascript("if (typeof add_msg === 'function') { add_msg(" + json + "); }", null);
            }
        });
    }

    @JavascriptInterface
    public void chatSetLmid(String lmid) {
        // Обновление lmid (last message id) в форме чата.
        String safe = lmid == null ? "" : lmid;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(safe);
        evalChatButtonsJs("if(document.FBT && document.FBT.lmid){document.FBT.lmid.value=" + json + ";}");
    }

    @JavascriptInterface
    public void chatRefreshN() {
        // Мягкий запрос на скорое обновление чата (top.ch_refresh_n).
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(activity::requestChatRefreshSoon);
    }

    @JavascriptInterface
    public void chatClearInput() {
        evalChatButtonsJs("if(document.FBT && document.FBT.text){document.FBT.text.value='';document.FBT.text.focus();}");
    }

    @JavascriptInterface
    public void chatSubmit(String action, String method, String data) {
        // Отправка сообщения чата: формируем POST и грузим в скрытый ch_refr WebView.
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        String url = action == null ? "" : action;
        if (!url.startsWith("http")) {
            url = "http://neverlands.ru/" + url.replaceFirst("^/+", "");
        }
        String safeMethod = method == null ? "POST" : method.toUpperCase();
        String payload = data == null ? "" : data;
        payload = recodeUrlEncoded(payload, Charset.forName("UTF-8"), Charset.forName("windows-1251"));
        Log.d("WebAppInterface", "chatSubmit: " + safeMethod + " " + url + " dataLen=" + payload.length());
        final String baseUrl = url;
        final String finalPayload = payload;
        if ("GET".equals(safeMethod)) {
            activity.runOnUiThread(() -> {
                String getUrl = baseUrl;
                if (!finalPayload.isEmpty()) {
                    getUrl = getUrl + (getUrl.contains("?") ? "&" : "?") + finalPayload;
                }
                activity.loadChatRefrUrl(getUrl);
            });
            return;
        }

        // POST выполняем в фоне, ответ парсим: add_msg / set_lmid.
        new Thread(() -> {
            ChatPostResult result = postChatMessage(baseUrl, finalPayload);
            if (result == null) {
                MainActivity fallbackActivity = getMainActivityOrNull();
                if (fallbackActivity != null) {
                    fallbackActivity.runOnUiThread(() -> fallbackActivity.postChatRefrUrl(baseUrl, finalPayload));
                }
                return;
            }
            if (result.lmid != null && !result.lmid.isEmpty()) {
                chatSetLmid(result.lmid);
            }
            if (!result.messages.isEmpty()) {
                for (String msg : result.messages) {
                    if (msg != null && !msg.isEmpty()) {
                        chatAddMsg(msg);
                    }
                }
            }
            chatClearInput();
            MainActivity refreshActivity = getMainActivityOrNull();
            if (refreshActivity != null) {
                refreshActivity.runOnUiThread(refreshActivity::requestChatRefreshSoon);
            }
        }, "chat-submit").start();
    }

    private static class ChatPostResult {
        final List<String> messages = new ArrayList<>();
        String lmid;
    }

    // POST в ch.php: возвращает кусок JS с add_msg/set_lmid, парсим вручную.
    private ChatPostResult postChatMessage(String url, String payload) {
        HttpURLConnection connection = null;
        try {
            URL target = new URL(url);
            java.net.Proxy activeProxy = ProxyRuntimeManager.getActiveJavaProxyOrNull();
            if (activeProxy == null && ProxyRuntimeManager.isStrictProxyRequiredForCurrentProfile()) {
                Log.e("WebAppInterface", "PROXY_FAIL: strict proxy enabled and runtime proxy unavailable, blocking direct chat POST: " + url);
                return null;
            }
            Log.d("WebAppInterface", "PROXY_BINDING: chat POST via "
                    + (activeProxy != null ? "local proxy" : "direct")
                    + ", url=" + url);
            connection = activeProxy != null
                    ? (HttpURLConnection) target.openConnection(activeProxy)
                    : (HttpURLConnection) target.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=windows-1251");
            connection.setRequestProperty("Accept-Encoding", "identity");

            String wvCookie = CookieManager.getInstance().getCookie(url);
            if (wvCookie != null && !wvCookie.isEmpty()) {
                connection.setRequestProperty("Cookie", wvCookie);
            } else {
                String cookie = CookiesManager.obtain(target.getHost());
                if (cookie != null && !cookie.isEmpty()) {
                    connection.setRequestProperty("Cookie", cookie);
                }
            }

            byte[] body = Russian.getBytes(payload == null ? "" : payload);
            connection.setRequestProperty("Content-Length", String.valueOf(body.length));
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int code = connection.getResponseCode();
            byte[] bytes;
            try (InputStream responseStream = code >= 400 && connection.getErrorStream() != null
                    ? connection.getErrorStream()
                    : connection.getInputStream()) {
                bytes = readAllBytes(responseStream);
            }

            String contentEncoding = connection.getContentEncoding();
            if ("gzip".equalsIgnoreCase(contentEncoding) && bytes.length > 2
                    && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
                bytes = decompressGzip(bytes);
            }
            if (bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
                bytes = decompressGzip(bytes);
            }

            Map<String, List<String>> headers = connection.getHeaderFields();
            applySetCookies(target, headers);

            String response = new String(bytes, Charset.forName("windows-1251"));
            ChatPostResult result = parseChatPostResponse(response);
            Log.d("WebAppInterface", "chatSubmit: response bytes=" + bytes.length
                    + " addMsg=" + result.messages.size()
                    + " lmid=" + (result.lmid == null ? "" : result.lmid));
            return result;
        } catch (Exception e) {
            Log.e("WebAppInterface", "chatSubmit: POST failed", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void applySetCookies(URL url, Map<String, List<String>> headers) {
        if (headers == null) return;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && "Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                if (values == null) continue;
                for (String sc : values) {
                    CookiesManager.assign(url.getHost(), sc);
                    CookieManager.getInstance().setCookie(url.getProtocol() + "://" + url.getHost(), sc);
                }
                CookieManager.getInstance().flush();
                break;
            }
        }
    }

    private ChatPostResult parseChatPostResponse(String html) {
        ChatPostResult result = new ChatPostResult();
        if (html == null || html.isEmpty()) return result;
        int idx = 0;
        while (idx >= 0 && idx < html.length()) {
            idx = html.indexOf("add_msg", idx);
            if (idx < 0) break;
            int p = html.indexOf('(', idx);
            if (p < 0) break;
            String msg = extractJsStringArg(html, p + 1);
            if (msg != null) {
                result.messages.add(msg);
            }
            idx = p + 1;
        }
        int lidx = 0;
        while (lidx >= 0 && lidx < html.length()) {
            lidx = html.indexOf("set_lmid", lidx);
            if (lidx < 0) break;
            int p = html.indexOf('(', lidx);
            if (p < 0) break;
            String lmid = extractJsTokenArg(html, p + 1);
            if (lmid != null && !lmid.isEmpty()) {
                result.lmid = lmid;
            }
            lidx = p + 1;
        }
        return result;
    }

    private String extractJsStringArg(String text, int start) {
        int i = start;
        int len = text.length();
        while (i < len && Character.isWhitespace(text.charAt(i))) i++;
        if (i >= len) return null;
        char quote = text.charAt(i);
        if (quote != '\'' && quote != '"') return null;
        i++;
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (; i < len; i++) {
            char c = text.charAt(i);
            if (escape) {
                if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
                else sb.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == quote) {
                return sb.toString();
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String extractJsTokenArg(String text, int start) {
        int i = start;
        int len = text.length();
        while (i < len && Character.isWhitespace(text.charAt(i))) i++;
        if (i >= len) return null;
        char c = text.charAt(i);
        if (c == '\'' || c == '"') {
            return extractJsStringArg(text, i);
        }
        StringBuilder sb = new StringBuilder();
        for (; i < len; i++) {
            c = text.charAt(i);
            if (c == ')' || c == ';' || Character.isWhitespace(c)) break;
            sb.append(c);
        }
        return sb.toString();
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private byte[] decompressGzip(byte[] compressed) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzis = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
        }
        return baos.toByteArray();
    }

    private String recodeUrlEncoded(String payload, Charset from, Charset to) {
        if (payload == null || payload.isEmpty()) return payload;
        try {
            String[] pairs = payload.split("&", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pairs.length; i++) {
                String pair = pairs[i];
                int eq = pair.indexOf('=');
                String key = eq >= 0 ? pair.substring(0, eq) : pair;
                String val = eq >= 0 ? pair.substring(eq + 1) : "";
                String keyDecoded = URLDecoder.decode(key, from.name());
                String valDecoded = URLDecoder.decode(val, from.name());
                String keyEncoded = URLEncoder.encode(keyDecoded, to.name());
                String valEncoded = URLEncoder.encode(valDecoded, to.name());
                sb.append(keyEncoded);
                if (eq >= 0) {
                    sb.append('=').append(valEncoded);
                }
                if (i < pairs.length - 1) sb.append('&');
            }
            return sb.toString();
        } catch (Exception e) {
            return payload;
        }
    }

    private void evalChatButtonsJs(String js) {
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (activity.binding != null && activity.binding.appBarMain != null
                    && activity.binding.appBarMain.contentMain != null
                    && activity.binding.appBarMain.contentMain.chatButtonsWebview != null) {
                activity.binding.appBarMain.contentMain.chatButtonsWebview.evaluateJavascript(js, null);
            }
        });
    }

    private String jsQuote(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    @JavascriptInterface
    public void chatFocus() {
        // Фокус на поле ввода чата (chat buttons).
        evalChatButtonsJs("if(document.FBT && document.FBT.text){document.FBT.text.focus();}");
    }

    @JavascriptInterface
    public void chatAppend(String text) {
        // Добавить текст в поле ввода чата (например, смайлы).
        String t = jsQuote(text);
        evalChatButtonsJs("if(document.FBT && document.FBT.text){document.FBT.text.value += '" + t + "';document.FBT.text.focus();}");
    }

    @JavascriptInterface
    public void chatSayPrivate(String nick) {
        // Вставка адресата в поле ввода (учёт %clan/%pair/%private).
        String raw = nick == null ? "" : nick;
        boolean isPair = raw.startsWith("%%%");
        boolean isClan = !isPair && raw.startsWith("%%");
        boolean isPrivate = !isPair && !isClan && raw.startsWith("%");
        if (isPair) raw = raw.substring(3);
        else if (isClan) raw = raw.substring(2);
        else if (isPrivate) raw = raw.substring(1);
        String prefix = isPair ? "%pair%" : (isClan ? "%clan%" : "");
        String n = jsQuote(raw);
        String p = jsQuote(prefix);
        evalChatButtonsJs("if(document.FBT && document.FBT.text){var v=document.FBT.text.value;if(v.length<255) document.FBT.text.value='" + p + "%<" + n + "> '+v;document.FBT.text.focus();}");
    }

    @JavascriptInterface
    public void chatSayTo(String nick) {
        // Вставка "<Nick> " в поле ввода (обычный чат).
        String n = jsQuote(nick);
        evalChatButtonsJs("if(document.FBT && document.FBT.text){var v=document.FBT.text.value;if(v.length<255) document.FBT.text.value='<" + n + "> '+v;document.FBT.text.focus();}");
    }

    @JavascriptInterface
    public void chatClearChat() {
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (activity.binding != null && activity.binding.appBarMain != null
                    && activity.binding.appBarMain.contentMain != null
                    && activity.binding.appBarMain.contentMain.chatMsgWebview != null) {
                activity.binding.appBarMain.contentMain.chatMsgWebview
                        .evaluateJavascript("if(document.getElementById('msg')){document.getElementById('msg').innerHTML='';}", null);
            }
        });
        chatFocus();
    }

    @JavascriptInterface
    public void chatRefreshNow() {
        // Немедленное обновление чата (top.ch_refresh).
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(activity::requestChatRefreshNow);
    }

    @JavascriptInterface
    public void chatClanPrivate() {
        evalChatButtonsJs("if(document.FBT && document.FBT.text){document.FBT.text.value='%clan% ' + document.FBT.text.value;document.FBT.text.focus();}");
    }

    private String adjustClanMarkers(String message) {
        if (message == null || message.isEmpty()) return message;
        String lower = message.toLowerCase();
        String updated = message;
        if (lower.contains("pair:") || lower.contains("%<pair>")) {
            updated = updated
                    .replaceAll("(?i)(<span\\s+alt=\")%(?!%)", "$1%%%")
                    .replaceAll("(?i)(<span\\s+title=\")%(?!%)", "$1%%%")
                    .replaceAll("(?i)(<span\\s+alt=')%(?!%)", "$1%%%")
                    .replaceAll("(?i)(<span\\s+title=')%(?!%)", "$1%%%");
        } else if (lower.contains("clan:") || lower.contains("%<clan>")) {
            updated = updated
                    .replaceAll("(?i)(<span\\s+alt=\")%(?!%)", "$1%%")
                    .replaceAll("(?i)(<span\\s+title=\")%(?!%)", "$1%%")
                    .replaceAll("(?i)(<span\\s+alt=')%(?!%)", "$1%%")
                    .replaceAll("(?i)(<span\\s+title=')%(?!%)", "$1%%");
        }
        return updated;
    }

    @JavascriptInterface
    public void chatChangeChatSpeed() {
        // Переключение скорости опроса чата (10/30/60 сек).
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        int current = activity.getChatRefreshSeconds();
        int next = (current == 10) ? 30 : (current == 30 ? 60 : 10);
        activity.setChatRefreshSeconds(next);
        evalChatButtonsJs("if(document.FBT && document.FBT.spchat){document.FBT.spchat.src='http://image.neverlands.ru/chat/bb_" + next + ".gif';" +
                "document.FBT.spchat.alt='Скорость обновления (раз в " + next + " секунд)';" +
                "document.FBT.spchat.title='Скорость обновления (раз в " + next + " секунд)';}");
    }

    @JavascriptInterface
    public void chatChangeChatSetup() {
        // Переключение режима чата (все/личные/выкл).
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        int current = activity.getChatFyo();
        int next = (current == 0) ? 1 : (current == 1 ? 2 : 0);
        activity.setChatFyo(next);
        String img;
        String alt;
        if (next == 1) {
            img = "http://image.neverlands.ru/chat/bb3_me.gif";
            alt = "Режим чата (Показывать только личные сообщения)";
        } else if (next == 2) {
            img = "http://image.neverlands.ru/chat/bb3_none.gif";
            alt = "Режим чата (Не показывать сообщения)";
        } else {
            img = "http://image.neverlands.ru/chat/bb3_all.gif";
            alt = "Режим чата (Показывать все сообщения)";
        }
        evalChatButtonsJs("if(document.FBT){if(document.FBT.fyo) document.FBT.fyo.value=" + next + ";" +
                "if(document.FBT.schat){document.FBT.schat.src='" + img + "';document.FBT.schat.alt='" + alt + "';document.FBT.schat.title='" + alt + "';}}");
    }

    @JavascriptInterface
    public void chatChangeLatrus() {
        // Переключение LAT<->RUS (транслит) для чата.
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        boolean next = !activity.isChatLatrus();
        activity.setChatLatrus(next);
        String img = next ? "http://image.neverlands.ru/chat/bb4_ac.gif" : "http://image.neverlands.ru/chat/bb4_nc.gif";
        String alt = next ? "LAT <-> RUS (Транслит включён)" : "LAT <-> RUS (Транслит выключен)";
        evalChatButtonsJs("top.latrus=" + (next ? "1" : "0") + ";" +
                "if(document.FBT && document.FBT.lrchat){document.FBT.lrchat.src='" + img + "';document.FBT.lrchat.alt='" + alt + "';document.FBT.lrchat.title='" + alt + "';}");
    }

    @JavascriptInterface
    public void AutoSelect() {
        Log.d("WebAppInterface", BG_TRACE_PREFIX + " AutoSelect called");
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(activity::requestAutoSelect);
    }

    @JavascriptInterface
    public void AutoTurn() {
        Log.d("WebAppInterface", BG_TRACE_PREFIX + " AutoTurn called");
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(activity::requestAutoTurn);
    }

    @JavascriptInterface
    public void AutoBoi() {
        Log.d("WebAppInterface", "AutoBoi called, current state: " + AppVars.Autoboi);
        boolean enable = AppVars.Autoboi != AutoboiState.AutoboiOn;
        try {
            if (mContext != null) {
                AutoFunctionsManager.getInstance(mContext).setAutoFightEnabled(enable);
            } else {
                AppVars.Autoboi = enable ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff;
                if (AppVars.Profile != null) {
                    AppVars.Profile.LezDoAutoboi = enable;
                }
            }
            Log.d("WebAppInterface", BG_TRACE_PREFIX + " AutoBoi toggled: enable=" + enable
                    + ", appVarsAutoboi=" + AppVars.Autoboi);
        } catch (Exception e) {
            Log.e("WebAppInterface", BG_TRACE_PREFIX + " AutoBoi toggle failed", e);
        }
    }

    @JavascriptInterface
    public void processFightHtml(String html) {
        Log.d("WebAppInterface", BG_TRACE_PREFIX + " processFightHtml called"
                + ", htmlLen=" + (html == null ? 0 : html.length()));
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().getFightViewModel().processFightHtml(html);
        }
    }

    @JavascriptInterface
    public void AutoUd() {
        Log.d("WebAppInterface", BG_TRACE_PREFIX + " AutoUd called");
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        activity.runOnUiThread(activity::requestAutoTurn);
    }

    @JavascriptInterface
    public void ResetCure() {
        Log.d("WebAppInterface", "ResetCure called");
        // Сброс состояния "восстановление" — возвращаем автобой в активное состояние
        // Аналог ResetCure() в C# ScriptManager.cs
        if (AppVars.Autoboi == AutoboiState.Restoring || AppVars.Autoboi == AutoboiState.Timeout) {
            AppVars.Autoboi = AutoboiState.AutoboiOn;
            Log.d("WebAppInterface", "ResetCure: Autoboi reset to AutoboiOn");
        }
    }

    @JavascriptInterface
    public void ResetLastBoiTimer() {
        Log.d("WebAppInterface", "ResetLastBoiTimer called");
        AppVars.LastBoiTimer = new java.util.Date();
    }

    @JavascriptInterface
    public String XodButtonElapsedTime() {
        long millis = System.currentTimeMillis() - AppVars.LastBoiTimer.getTime();
        return " ход " + ru.neverlands.abclient.utils.ConverterUtils.timeSpanToString(millis) + " ";
    }

    @JavascriptInterface
    public String InfoToolTip(String name, String alt) {
        Log.d("WebAppInterface", "InfoToolTip: " + name);
        return alt;
    }

    @JavascriptInterface
    public int BulkSellOldArg1() {
        // TODO: Implement bulk sell logic
        return 0;
    }

    @JavascriptInterface
    public int BulkSellOldArg2() {
        // TODO: Implement bulk sell logic
        return 0;
    }

    @JavascriptInterface
    public void TraceDrinkPotion(String nick, String potion) {
        Log.d("WebAppInterface", "TraceDrinkPotion: " + nick + ", " + potion);
    }

    @JavascriptInterface
    public void startBulkSell(String thing, String price, String link) {
        Log.d("WebAppInterface", "Start bulk sell: " + thing);
        // TODO: Set AppVars and trigger refresh
    }

    @JavascriptInterface
    public void startBulkDrop(String thing, String price) {
        Log.d("WebAppInterface", "Start bulk drop: " + thing);
        // TODO: Set AppVars and trigger refresh
    }

    @JavascriptInterface
    public void showHpMaTimers(String s, float curHP, int maxHP, float intHP, float curMA, int maxMA, float intMA) {
        CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.updateFromHpJs(
                curHP, maxHP, curMA, maxMA, intHP, intMA, "WebAppInterface.showHpMaTimers");
        Log.d("WebAppInterface", "showHpMaTimers: hp=" + snapshot.curHp + "/" + snapshot.maxHp
                + " ma=" + snapshot.curMa + "/" + snapshot.maxMa
                + " intHP=" + snapshot.intHp + " intMA=" + snapshot.intMa);
    }

    @JavascriptInterface
    public void startBulkOldSell(String name, String price) {
        // TODO: Pass this data to a ViewModel
        System.out.println("Bulk sell: " + name + " for " + price);
    }

    @JavascriptInterface
    public void loadFrame(String frameName, String url) {
        // Замена frameset‑навигации: перенаправляем в нужный WebView.
        Log.d("WebAppInterface", "loadFrame: " + frameName + " to " + url);
        if (AppVars.mainActivity == null || AppVars.mainActivity.get() == null) {
            return;
        }

        // Ensure the URL is absolute
        if (!url.startsWith("http")) {
            url = "http://neverlands.ru/" + url.replaceFirst("^/+", "");
        }

        final String finalUrl = url;
        AppVars.mainActivity.get().runOnUiThread(() -> {
            switch (frameName) {
                case "main_top":
                    MainTopRoutingDecision routingDecision = buildMainTopRoutingDecision(finalUrl);
                    logMainTopRoutingDecision("request", routingDecision);
                    if (routingDecision.suppress) {
                        logMainTopRoutingDecision("suppressed", routingDecision);
                        return;
                    }
                    logMainTopRoutingDecision("accepted", routingDecision);
                    AppVars.url_main_top = finalUrl;
                    AppVars.mainActivity.get().binding.appBarMain.contentMain.webView.loadUrl(finalUrl);
                    break;
                case "ch_list":
                    AppVars.mainActivity.get().binding.appBarMain.contentMain.chatUsersWebview.loadUrl(finalUrl);
                    break;
                case "ch_buttons":
                    AppVars.mainActivity.get().binding.appBarMain.contentMain.chatButtonsWebview.loadUrl(finalUrl);
                    break;
                case "chmain":
                    AppVars.mainActivity.get().binding.appBarMain.contentMain.chatMsgWebview.loadUrl(finalUrl);
                    break;
                case "ch_refr":
                    AppVars.url_ch_refr = finalUrl;
                    Log.d("WebAppInterface", "loadFrame: ch_refr to " + finalUrl);
                    AppVars.mainActivity.get().loadChatRefrUrl(finalUrl);
                    break;
                default:
                    // Load in the main webview by default
                    AppVars.url_main_top = finalUrl;
                    AppVars.mainActivity.get().binding.appBarMain.contentMain.webView.loadUrl(finalUrl);
                    break;
            }
        });
    }

    /**
     * JS trace hook для client-side попыток перезагрузки `main_top`.
     *
     * Назначение:
     * - фиксирует источник "клиент попросил reload" (например, `game_js_toprefresh`);
     * - дает тайм-маркер для последующей маршрутизации в {@link #loadFrame(String, String)};
     * - позволяет в логах отделить серверные переходы от клиентских.
     *
     * Зависимости:
     * - GameJs.process(...): вызывает `traceMainTopReload(...)` из `toprefresh`;
     * - loadFrame(...): читает `lastClientMainTopReload*` для anti-loop решения.
     */
    @JavascriptInterface
    public void traceMainTopReload(String source, String payload) {
        long now = System.currentTimeMillis();
        lastClientMainTopReloadAtMs = now;
        lastClientMainTopReloadSource = source == null ? "" : source;
        lastClientMainTopReloadPayload = payload == null ? "" : payload;
        Log.d("WebAppInterface", MAIN_TOP_TRACE_PREFIX
                + " client-reload: source=" + lastClientMainTopReloadSource
                + ", payload=" + lastClientMainTopReloadPayload
                + ", atMs=" + now);
    }

    /**
     * Формирует решение по маршрутизации `main_top` с полным диагностическим контекстом.
     *
     * Правила:
     * 1) `main.php` от недавнего client-side reload (game.js) подавляем;
     * 2) `main.php` в активной фазе auto-fight (fight html/link/pulse) подавляем;
     * 3) остальные URL пропускаем как серверный источник истины.
     *
     * Зависимости:
     * - AppVars.ContentMainPhp / FightLink / LastFightPulseAtMs (контекст боя);
     * - AppVars.Autoboi / Profile.LezDoAutoboi (факт включенного авто-боя);
     * - traceMainTopReload(...) (контекст client-side инициатора).
     */
    private MainTopRoutingDecision buildMainTopRoutingDecision(String finalUrl) {
        MainTopRoutingDecision d = new MainTopRoutingDecision();
        d.url = finalUrl == null ? "" : finalUrl;
        String lowerUrl = d.url.toLowerCase(Locale.ROOT);
        d.targetIsPlainMain = "http://neverlands.ru/main.php".equals(lowerUrl);
        d.targetIsGoInf = lowerUrl.contains("get_id=56") && lowerUrl.contains("go=inf");
        d.targetIsFightFinish = lowerUrl.contains("get_id=61") && lowerUrl.contains("act=7");

        d.autoFightEnabled = AppVars.Autoboi == AutoboiState.AutoboiOn
                || (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi);
        MainActivity activity = getMainActivityOrNull();
        d.uiForegroundInteractive = activity != null && activity.isUiForegroundInteractive();

        String contentMainPhp = AppVars.ContentMainPhp;
        d.hasFightHtml = contentMainPhp != null
                && (contentMainPhp.contains("var fight_ty") || contentMainPhp.contains("magic_slots();"));
        String fightLink = AppVars.FightLink;
        d.hasFightLink = fightLink != null && fightLink.contains("get_id=61&act=");

        long now = System.currentTimeMillis();
        d.recentFightPulse = AppVars.LastFightPulseAtMs > 0L
                && (now - AppVars.LastFightPulseAtMs) <= MAIN_TOP_FIGHT_PULSE_GUARD_MS;
        d.msSinceLastClientReload = lastClientMainTopReloadAtMs > 0L
                ? (now - lastClientMainTopReloadAtMs)
                : Long.MAX_VALUE;
        d.lastClientReloadSource = lastClientMainTopReloadSource == null ? "" : lastClientMainTopReloadSource;
        d.lastClientReloadPayload = lastClientMainTopReloadPayload == null ? "" : lastClientMainTopReloadPayload;
        d.recentClientReload = d.msSinceLastClientReload >= 0L
                && d.msSinceLastClientReload <= MAIN_TOP_CLIENT_RELOAD_GUARD_MS;

        if (!d.targetIsPlainMain) {
            d.suppress = false;
            d.suppressReason = "allow_non_plain_main";
            return d;
        }

        if (d.recentClientReload && d.lastClientReloadSource.startsWith("game_js_")) {
            d.suppress = true;
            d.suppressReason = "suppress_recent_game_js_reload";
            return d;
        }

        if (d.autoFightEnabled
                && !d.uiForegroundInteractive
                && (d.hasFightHtml || d.hasFightLink || d.recentFightPulse)) {
            d.suppress = true;
            d.suppressReason = "suppress_autofight_plain_main_background";
            return d;
        }

        d.suppress = false;
        d.suppressReason = "allow_server_plain_main";
        return d;
    }

    /**
     * Единый logcat-дамп решения маршрутизации `main_top`.
     * Нужен как "черный ящик" для пост-фактум анализа регрессий.
     */
    private void logMainTopRoutingDecision(String stage, MainTopRoutingDecision d) {
        if (d == null) {
            return;
        }
        Log.d("WebAppInterface", MAIN_TOP_TRACE_PREFIX + " " + stage
                + ": url=" + d.url
                + ", suppress=" + d.suppress
                + ", reason=" + d.suppressReason
                + ", plainMain=" + d.targetIsPlainMain
                + ", goInf=" + d.targetIsGoInf
                + ", fightFinish=" + d.targetIsFightFinish
                + ", autoFight=" + d.autoFightEnabled
                + ", uiForegroundInteractive=" + d.uiForegroundInteractive
                + ", hasFightHtml=" + d.hasFightHtml
                + ", hasFightLink=" + d.hasFightLink
                + ", recentFightPulse=" + d.recentFightPulse
                + ", recentClientReload=" + d.recentClientReload
                + ", clientReloadSource=" + d.lastClientReloadSource
                + ", clientReloadPayload=" + d.lastClientReloadPayload
                + ", msSinceClientReload="
                + (d.msSinceLastClientReload == Long.MAX_VALUE ? -1L : d.msSinceLastClientReload));
    }

    /**
     * DTO решения маршрутизации main_top.
     */
    private static final class MainTopRoutingDecision {
        String url;
        boolean targetIsPlainMain;
        boolean targetIsGoInf;
        boolean targetIsFightFinish;
        boolean autoFightEnabled;
        boolean uiForegroundInteractive;
        boolean hasFightHtml;
        boolean hasFightLink;
        boolean recentFightPulse;
        boolean recentClientReload;
        long msSinceLastClientReload;
        String lastClientReloadSource;
        String lastClientReloadPayload;
        boolean suppress;
        String suppressReason;
    }

    /**
     * Открыть URL в новой вспомогательной вкладке.
     * Аналог CreateNewTab в C# версии.
     * @param url URL для загрузки
     * @param title Заголовок вкладки
     */
    @JavascriptInterface
    public void openInNewTab(String url, String title) {
        Log.d("WebAppInterface", "openInNewTab: " + title + " -> " + url);
        
        // Всегда используем MainActivity для открытия вкладки
        if (AppVars.mainActivity == null || AppVars.mainActivity.get() == null) {
            return;
        }
        
        AppVars.mainActivity.get().runOnUiThread(() -> {
            AppVars.mainActivity.get().openInNewTab(url, title);
        });
    }

    @JavascriptInterface
    public void redirectToUrl(String url) {
        // Приводим ссылку к абсолютной, иначе WebView.loadUrl(...) её не откроет и перехватчик не сработает.
        String finalUrl = url;
        if (finalUrl != null && !finalUrl.startsWith("http")) {
            finalUrl = "http://neverlands.ru/" + finalUrl.replaceFirst("^/+", "");
        }

        Log.d("WebAppInterface", "redirectToUrl: " + finalUrl);
        
        // Используем локальный broadcast, чтобы его получил MainActivity (регистрируется через LocalBroadcastManager)
        if (mContext != null) {
            Intent intent = new Intent(AppVars.ACTION_WEBVIEW_LOAD_URL);
            intent.putExtra("url", finalUrl);
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        }

        // Дополнительно — прямой вызов, если активити доступна (страховка от пропуска broadcast)
        MainActivity activity = getMainActivityOrNull();
        if (activity != null) {
            final String toLoad = finalUrl;
            activity.runOnUiThread(() -> activity.getMainWebView().loadUrl(toLoad));
        }
    }
}
