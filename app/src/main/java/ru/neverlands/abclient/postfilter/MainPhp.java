package ru.neverlands.abclient.postfilter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.text.SimpleDateFormat;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.model.InvComparer;
import ru.neverlands.abclient.model.InvEntry;
import ru.neverlands.abclient.model.ParsedDressed;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.manager.FastActionManager;
import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.manager.UnderAttackManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.HtmlUtils;
import ru.neverlands.abclient.utils.Russian;
// Главный пост‑фильтр main.php: бой, инвентарь, быстрые действия, системные сообщения.
public class MainPhp {
    private static final String TAG = "MainPhp";
    private static final Random RANDOM = new Random();
    private static final int AUTO_FINISH_MIN_DELAY_MS = 1000;
    private static final int AUTO_FINISH_EXTRA_DELAY_MS = 700;
    // Для finish-состояния st=7 сервер часто требует несколько быстрых подтверждений подряд.
    // Держим отдельный короткий polling-интервал, чтобы не ждать по 1-2 секунды между попытками.
    private static final int AUTO_FINISH_ST7_MIN_DELAY_MS = 220;
    private static final int AUTO_FINISH_ST7_EXTRA_DELAY_MS = 220;
    private static final long AUTO_DRINK_TRIGGER_COOLDOWN_MS = 2500L;
    private static final long AUTO_FISH_BLAZ_TRIGGER_COOLDOWN_MS = 8000L;
    // Серверный таймаут после шага "Пить" в рыбалке: выдерживаем ~62 сек перед следующим шагом.
    private static final long AUTO_FISH_DRINK_SERVER_COOLDOWN_MS = 62_000L;
    private static final int AUTO_FISH_DRINK_POLL_DELAY_MS = 1500;
    private static final long CAPTCHA_FALLBACK_TTL_MS = 5000L;
    private static final long AUTO_SKIN_KNIFE_RECHECK_INTERVAL_MS = 60_000L;
    private static final int AUTO_FISH_WEAR_LOOP_MAX_REPEATS = 12;
    private static final long AUTO_FISH_WEAR_LOOP_WINDOW_MS = 20_000L;
    private static final String BLISS_ELIXIR_NAME = "\u042D\u043B\u0438\u043A\u0441\u0438\u0440 \u0411\u043B\u0430\u0436\u0435\u043D\u0441\u0442\u0432\u0430";
    private static final String DIG_BUTTON_MARKER = "[\"dig\",\"Копать\",";
    private static final int[] FISH_PRIM_IDS = new int[]{38, 39, 40, 41, 42, 43, 44, 45, 46};
    private static final int[] FISH_PRIM_FLAGS = new int[]{
            ru.neverlands.abclient.model.Prims.Bread,
            ru.neverlands.abclient.model.Prims.Worm,
            ru.neverlands.abclient.model.Prims.BigWorm,
            ru.neverlands.abclient.model.Prims.Stink,
            ru.neverlands.abclient.model.Prims.Fly,
            ru.neverlands.abclient.model.Prims.Light,
            ru.neverlands.abclient.model.Prims.Donka,
            ru.neverlands.abclient.model.Prims.Morm,
            ru.neverlands.abclient.model.Prims.HiFlight
    };
    private static final long WTIME_SYNC_LOG_GUARD_MS = 1500L;
    private static volatile long lastAutoFinishRedirectAtMs = 0L;
    private static volatile long lastAutoDrinkTriggerAtMs = 0L;
    private static volatile long lastAutoFishBlazTriggerAtMs = 0L;
    private static volatile long lastAutoFishDrinkTriggerAtMs = 0L;
    private static volatile long lastWtimeSyncLogAtMs = 0L;
    private static volatile long lastAutoDrinkBlazTriggerAtMs = 0L;
    // Защита от повторного показа одного и того же диалога капчи завершения боя.
    private static volatile String lastFightCaptchaDialogKey = "";
    private static volatile long lastFightCaptchaDialogAtMs = 0L;
    private static volatile String lastFishCaptchaDialogKey = "";
    private static volatile long lastFishCaptchaDialogAtMs = 0L;
    private static volatile String lastCaptchaRejectKey = "";
    private static volatile long lastCaptchaRejectAtMs = 0L;
    // Дедуп публикации результата боя:
    // - победа: один раз на LogBoi, чтобы повторные act=7/vcode не спамили "Победа за ...";
    // - лут: один раз на (LogBoi + тип + список), чтобы не терять сообщение о добыче и не дублировать его.
    private static volatile String lastFightResultWinnerBroadcastKey = "";
    private static volatile String lastFightResultLootBroadcastKey = "";
    private static volatile String lastFightSummaryBroadcastKey = "";
    private static volatile String lastAutoSkinProbeFightLog = "";
    /**
     * Явное дерево решений для обработки завершения боя.
     *
     * Зависимости:
     * - {@link AppVars#FightLink} как прямой сигнал финализации.
     * - Серверная форма завершения {@code FEND}, разобранная из текущего HTML.
     * - URL капчи, определяемый через {@link #resolveFightCaptchaUrl(String)}.
     *
     * Ветви:
     * - {@code DIRECT_FINISH_LINK}: завершение через редирект на готовый {@code get_id=61&act=7}.
     * - {@code FEND_AUTOSUBMIT_ALLOWED}: завершение через авто-submit разобранной формы {@code FEND}.
     * - {@code CAPTCHA_REQUIRED}: остановка autoboi и сохранение потока страницы/диалога капчи.
     * - {@code KEEP_ORIGINAL_HTML}: безопасного действия нет, оставляем текущий кадр для защиты от циклов.
     */
    private enum FinishFlowDecision {
        DIRECT_FINISH_LINK,
        FEND_AUTOSUBMIT_ALLOWED,
        CAPTCHA_REQUIRED,
        KEEP_ORIGINAL_HTML
    }
    /**
     * Лёгкая DTO-запись предмета инвентаря для wear-логики (порт `GetInvList` + `InvEntry.WearLink` из C#).
     */
    private static final class WearInvEntry {
        String name;
        String wearLink;
    }
    /**
     * Снимок значений из `ins_HP(curh,maxh,curm,maxm,hp_int,ma_int)`.
     * Используется для:
     * - обновления интервалов регена `AppVars.PersIntHP`/`AppVars.PersIntMA`;
     * - отображения статуса лечения в верхнем фрейме при `AutoboiState.Restoring`.
     */
    private static final class InsHpSnapshot {
        int curHp;
        int maxHp;
        int curMa;
        int maxMa;
        double intHp;
        double intMa;
    }
    /**
     * Лёгкий снимок сигналов страницы завершения боя для диагностики и детекции циклов.
     *
     * Зависимости:
     * - Заполняется из HTML через Jsoup и вспомогательные парсеры подстрок.
     * - Используется логгером решений и ключами дедупликации в потоке завершения боя.
     */
    private static final class FightFinishPageMarkers {
        boolean hasFendForm;
        boolean hasCodeInput;
        String codeState = "none";
        boolean hasFkeyScript;
        boolean hasCaptchaImage;
        String fendAction = "";
        String challengeHash = "";
        String fexpCaptchaToken = "";
    }
    /**
     * Генерирует HTML-заглушку "ожидаем ход противника" с авто-обновлением страницы.
     *
     * Зависимости:
     * - {@link HtmlUtils#GENERATED_PAGE_MARKER} для маркировки сгенерированного клиентом HTML.
     * - JavaScript-таймер {@code setTimeout(...)} для отложенного перехода на {@code reloadUrl}.
     *
     * Назначение:
     * - Не оставлять WebView в "зависшем" кадре ожидания, а мягко перевести на следующий poll.
     */
    private static String buildWaitForTurnAutoRefreshHtml(String reloadUrl, int delayMs) {
        String safeUrl = (reloadUrl != null && !reloadUrl.isEmpty()) ? reloadUrl : "main.php";
        int safeDelay = Math.max(300, delayMs);
        return HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head><body>" +
                "Ожидаем хода противника...<br>" +
                "<script language=\"JavaScript\">" +
                "setTimeout(function(){ window.location = '" + safeUrl + "'; }, " + safeDelay + ");" +
                "</script></body></html>";
    }

    /**
     * Добавляет auto-refresh прямо в текущий боевой HTML, не подменяя кадр служебной страницей ожидания.
     *
     * Зависимости:
     * - используется из {@link #mainPhpFight(String, String)} только для стадии ожидания хода противника;
     * - сохраняет серверный `FightFrame` на экране, чтобы отображение боя не переключалось на упрощённую страницу;
     * - если HTML не содержит закрывающих тегов, fallback идёт в {@link #buildWaitForTurnAutoRefreshHtml(String, int)}.
     */
    private static String buildInPlaceFightAutoRefreshHtml(String html, String reloadUrl, int delayMs) {
        if (html == null || html.isEmpty()) {
            return buildWaitForTurnAutoRefreshHtml(reloadUrl, delayMs);
        }
        String safeUrl = (reloadUrl != null && !reloadUrl.isEmpty()) ? reloadUrl : "main.php";
        int safeDelay = Math.max(300, delayMs);
        String script = "<script language=\"JavaScript\">"
                + "setTimeout(function(){ window.location = '" + safeUrl + "'; }, " + safeDelay + ");"
                + "</script>";
        int bodyClose = html.lastIndexOf("</body>");
        if (bodyClose >= 0) {
            return html.substring(0, bodyClose) + script + html.substring(bodyClose);
        }
        int htmlClose = html.lastIndexOf("</html>");
        if (htmlClose >= 0) {
            return html.substring(0, htmlClose) + script + html.substring(htmlClose);
        }
        return buildWaitForTurnAutoRefreshHtml(reloadUrl, delayMs);
    }
    /**
     * Возвращает сохранённое состояние переключателя Auto-Fight из AutoFunctionsManager.
     * Если manager/context недоступен, используется fallback на флаг профиля.
     */
    private static boolean isAutoFightEnabledByPreference() {
        try {
            android.content.Context context = AppVars.getContext();
            if (context == null) {
                return AppVars.Profile != null && AppVars.Profile.LezDoAutoboi;
            }
            return AutoFunctionsManager.getInstance(context).isAutoFightEnabled();
        } catch (Exception e) {
            android.util.Log.w(TAG, "isAutoFightEnabledByPreference: fallback to profile flag", e);
            return AppVars.Profile != null && AppVars.Profile.LezDoAutoboi;
        }
    }
    /**
     * Самовосстановление runtime-рассинхронизации, когда сохранённый Auto-Fight включён,
     * а AppVars.Autoboi выключен.
     *
     * Это восстановление намеренно блокируется при активном потоке CAPTCHA.
     */
    private static void recoverAutoboiRuntimeStateIfNeeded(boolean fightEnded, String fightCaptchaUrl) {
        if (!fightEnded || AppVars.Autoboi != AutoboiState.AutoboiOff) {
            return;
        }
        if (AppVars.Profile == null || !AppVars.Profile.LezDoAutoboi) {
            return;
        }
        if (!isAutoFightEnabledByPreference()) {
            return;
        }
        boolean captchaExpected = fightCaptchaUrl != null && !fightCaptchaUrl.isEmpty();
        if (captchaExpected || AppVars.IsFightCaptchaDialogVisible || AppVars.ResumeAutoboiAfterCaptcha) {
            android.util.Log.d(TAG, "recoverAutoboiRuntimeStateIfNeeded: skip (captcha flow active)");
            return;
        }
        AppVars.Autoboi = AutoboiState.AutoboiOn;
        android.util.Log.w(TAG, "recoverAutoboiRuntimeStateIfNeeded: restored AppVars.Autoboi -> AutoboiOn");
    }
    /**
     * Форматирует секунды в `HH:mm:ss` для UI ожидания лечения.
     */
    private static String formatHms(long seconds) {
        long total = Math.max(0L, seconds);
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }
    /**
     * Генерирует HTML статуса ожидания лечения после боя.
     *
     * Формат основной строки:
     * `(curHP/maxHP + curMA/maxMA) + HH:mm:ss`
     *
     * Зависимости:
     * - `remainingMs` рассчитывается в `LezFight.calcRestoreAfterBoiReadyAtMs()`;
     * - сам расчёт времени идёт по `hp_int/ma_int`, которые обновляются из `ins_HP(...)`.
     */
    private static String buildRestoringStatusHtml(String reloadUrl,
                                                   int reloadDelayMs,
                                                   long remainingMs,
                                                   int curHp,
                                                   int maxHp,
                                                   int curMa,
                                                   int maxMa,
                                                   boolean doWaitHp,
                                                   int waitHpPercent,
                                                   boolean doWaitMa,
                                                   int waitMaPercent) {
        String safeUrl = (reloadUrl != null && !reloadUrl.isEmpty()) ? reloadUrl : "main.php";
        int safeDelay = Math.max(900, reloadDelayMs);
        long remainSec = Math.max(0L, (long) Math.ceil(remainingMs / 1000.0));
        int hpGoal = (doWaitHp && maxHp > 0) ? (int) Math.ceil(maxHp * (waitHpPercent / 100.0)) : maxHp;
        int maGoal = (doWaitMa && maxMa > 0) ? (int) Math.ceil(maxMa * (waitMaPercent / 100.0)) : maxMa;
        int hpPercent = maxHp > 0 ? (int) Math.round((curHp * 100.0) / maxHp) : 0;
        int maPercent = maxMa > 0 ? (int) Math.round((curMa * 100.0) / maxMa) : 0;
        String hpTargetText = doWaitHp
                ? ("HP ≥ " + waitHpPercent + "% (" + hpGoal + "/" + maxHp + ")")
                : "HP: ожидание выключено";
        String maTargetText = doWaitMa
                ? ("MA ≥ " + waitMaPercent + "% (" + maGoal + "/" + maxMa + ")")
                : "MA: ожидание выключено";
        String hpMaLine = "(" + curHp + "/" + maxHp + " + " + curMa + "/" + maxMa + ")";
        return HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head>" +
                "<body style='font-family:Arial,sans-serif;padding:10px;background:#fff;'>" +
                "<div id='ab_restore_title' style='font-weight:bold;color:#7B0A0A;'>Останов лечения</div>" +
                "<div id='ab_restore_main' style='margin-top:6px;font-weight:bold;'>" + hpMaLine + " + <span id='ab_restore_eta'>" + formatHms(remainSec) + "</span></div>" +
                "<div style='margin-top:6px;'>HP: <b>" + curHp + "/" + maxHp + "</b> (" + hpPercent + "%)</div>" +
                "<div>MA: <b>" + curMa + "/" + maxMa + "</b> (" + maPercent + "%)</div>" +
                "<div style='margin-top:6px;color:#444;'>" + hpTargetText + "</div>" +
                "<div style='color:#444;'>" + maTargetText + "</div>" +
                "<script language=\"JavaScript\">" +
                "var abRemain=" + remainSec + ";" +
                "function abFmt(sec){sec=Math.max(0,Math.floor(sec));var h=Math.floor(sec/3600);var m=Math.floor((sec%3600)/60);var s=sec%60;" +
                "return (h<10?'0'+h:h)+':'+(m<10?'0'+m:m)+':'+(s<10?'0'+s:s);}" +
                "function abTick(){var n=document.getElementById('ab_restore_eta');if(n){n.innerHTML=abFmt(abRemain);}if(abRemain>0){abRemain--;}}" +
                "abTick();setInterval(abTick,1000);" +
                "setTimeout(function(){window.location='" + safeUrl + "';}," + safeDelay + ");" +
                "</script></body></html>";
    }
    /**
     * Строит HTML с отложенным redirect для завершения боя.
     *
     * Зависимости:
     * - {@link HtmlUtils#GENERATED_PAGE_MARKER} для маркировки служебной страницы.
     * - JavaScript bridge {@code AndroidBridge.redirectToUrl(...)} при наличии, иначе fallback на {@code window.location}.
     * - {@link #AUTO_FINISH_MIN_DELAY_MS} как нижняя граница задержки.
     *
     * Назначение:
     * - Ограничить частоту финальных запросов и избежать избыточного спама к серверу.
     */
    private static String buildDelayedRedirectHtml(String description, String link, int delayMs) {
        String safeLink = (link != null && !link.isEmpty()) ? link : "main.php";
        int safeDelay = Math.max(AUTO_FINISH_MIN_DELAY_MS, delayMs);
        return HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head><body>" +
                description +
                "<script language=\"JavaScript\">" +
                "setTimeout(function(){" +
                "if(typeof AndroidBridge !== 'undefined' && AndroidBridge.redirectToUrl){" +
                "AndroidBridge.redirectToUrl(\"" + safeLink + "\");" +
                "} else { window.location = \"" + safeLink + "\"; }" +
                "}," + safeDelay + ");" +
                "</script></body></html>";
    }
    /**
     * Строит промежуточную HTML-страницу удержания при активном диалоге капчи.
     *
     * Зависимости:
     * - {@link HtmlUtils#GENERATED_PAGE_MARKER} для безопасной подмены текущего кадра.
     *
     * Назначение:
     * - Сохранить контекст боя, пока пользователь вводит код в отдельном диалоге.
     */
    private static String buildCaptchaDialogHoldHtml() {
        return HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head><body>" +
                "Ожидание ввода капчи...<br>" +
                "Введите код во всплывающем окне." +
                "</body></html>";
    }
    /**
     * Вычисляет актуальный URL боевой капчи для текущего шага завершения боя.
     *
     * Порядок источников (от более приоритетного к fallback):
     * 1) прямой `img src` в HTML (`extractCaptchaUrl`),
     * 2) token из `var fexp[4]` (`extractCaptchaUrlFromFexp`) — это данные текущего fight-frame,
     * 3) `AppVars.CodeAddress` (получен из `LezFight.ParseNonFight`, аналог C# `CodeAddress`),
     * 4) последний перехваченный URL из interceptor (`AppVars.LastFightCaptchaImageUrl`) с TTL
     *    только как fallback, когда локальные маркеры текущего боя отсутствуют.
     *
     * Важно:
     * - перехваченный URL может относиться к предыдущему challenge (race при фоновых refresh),
     *   поэтому он не должен перебивать `fexp[4]`/`CodeAddress`, если они уже известны.
     *
     * Зависимости:
     * - `LezFight.BuildFightLink(...)`,
     * - `WebViewRequestInterceptor` (captured code.php URL/bytes),
     * - используется в auto/manual ветках `mainPhpFight(...)`.
     */
    private static String resolveFightCaptchaUrl(String html) {
        String captchaUrl = extractCaptchaUrl(html);
        if (captchaUrl != null && !captchaUrl.isEmpty()) {
            return captchaUrl;
        }
        // В части ответов сервера img src с code.php отсутствует в HTML,
        // но token капчи есть в var fexp[4] (как в fight_v10.js: code.php?'+fexp[4]).
        String captchaUrlFromFexp = extractCaptchaUrlFromFexp(html);
        if (captchaUrlFromFexp != null && !captchaUrlFromFexp.isEmpty()) {
            android.util.Log.d(TAG, "resolveFightCaptchaUrl: built from fexp[4]: " + captchaUrlFromFexp);
            return captchaUrlFromFexp;
        }
        if (AppVars.CodeAddress != null && !AppVars.CodeAddress.isEmpty()) {
            return AppVars.CodeAddress;
        }
        String fallbackUrl = AppVars.LastFightCaptchaImageUrl;
        long fallbackAt = AppVars.LastFightCaptchaImageAtMs;
        if (fallbackUrl != null && !fallbackUrl.isEmpty() && fallbackAt > 0L) {
            long age = System.currentTimeMillis() - fallbackAt;
            if (age >= 0 && age <= CAPTCHA_FALLBACK_TTL_MS) {
                android.util.Log.d(TAG, "resolveFightCaptchaUrl: use fallback from interceptor, ageMs=" + age);
                return fallbackUrl;
            }
        }
        return null;
    }
    /**
     * Собирает URL капчи из массива `fexp` (элемент `fexp[4]`).
     *
     * Зависимости:
     * - `HelperStrings.subString`,
     * - формат `fight_v10.js`: `code.php?` + token.
     */
    private static String extractCaptchaUrlFromFexp(String html) {
        try {
            if (html == null || html.isEmpty()) {
                return null;
            }
            String rawFexp = HelperStrings.subString(html, "var fexp = [", "];");
            if (rawFexp == null || rawFexp.isEmpty()) {
                return null;
            }
            String[] parts = rawFexp.split(",");
            if (parts.length < 5) {
                return null;
            }
            String captchaToken = parts[4].replace("\"", "").replace("'", "").trim();
            if (captchaToken.length() <= 2) {
                return null;
            }
            return "http://neverlands.ru/modules/code/code.php?" + captchaToken;
        } catch (Exception e) {
            android.util.Log.e(TAG, "extractCaptchaUrlFromFexp error", e);
            return null;
        }
    }
    /**
     * Восстанавливает ссылку завершения боя (`get_id=61&act=7`) напрямую из HTML.
     *
     * Нужен как fallback, когда `LezFight.BuildFightLink(...)` не сработал
     * и `AppVars.FightLink` остался пустым, но сервер уже прислал финальный кадр боя.
     */
    private static String extractFightFinishLinkFromHtml(String html, boolean withCaptchaPlaceholder) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        // 1) Прямой линк в HTML/JS.
        String directLink = findMainPhpLinkByQueryParts(html, "get_id=61", "act=7", "fexp=");
        if (directLink != null && !directLink.isEmpty()) {
            if (withCaptchaPlaceholder && !directLink.contains("code=")) {
                directLink = setOrAppendQueryParam(directLink, "code", "????");
            }
            return normalizeNeverlandsMainLink(directLink);
        }
        // 2) Сборка по `var fexp = [...]` (аналог LezFight.BuildFightLink).
        String rawFexp = HelperStrings.subString(html, "var fexp = [", "];");
        if (rawFexp == null || rawFexp.isEmpty()) {
            return null;
        }
        List<String> fexp = splitJsTopLevelCsv(rawFexp);
        if (fexp.size() < 14) {
            return null;
        }
        String fexp0 = trimJsToken(fexp.get(0));
        String fexp1 = trimJsToken(fexp.get(1));
        String fexp3 = trimJsToken(fexp.get(3));
        String fexp5 = trimJsToken(fexp.get(5));
        String fexp8 = trimJsToken(fexp.get(8));
        String fexp9 = trimJsToken(fexp.get(9));
        String fexp10 = trimJsToken(fexp.get(10));
        String fexp11 = trimJsToken(fexp.get(11));
        String fexp12 = trimJsToken(fexp.get(12));
        String fexp13 = trimJsToken(fexp.get(13));
        if (fexp0.isEmpty() || fexp1.isEmpty() || fexp3.isEmpty() || fexp5.isEmpty()) {
            return null;
        }
        String finishLink = (withCaptchaPlaceholder
                ? "main.php?code=????&get_id=61&act=7&fexp="
                : "main.php?get_id=61&act=7&fexp=") + fexp0
                + "&fres=" + fexp1
                + "&vcode=" + fexp3
                + "&min1=" + fexp8
                + "&max1=" + fexp9
                + "&min2=" + fexp10
                + "&max2=" + fexp11
                + "&sum1=" + fexp12
                + "&sum2=" + fexp13
                + "&ftype=" + fexp5;
        return normalizeNeverlandsMainLink(finishLink);
    }

    /**
     * Восстанавливает "голую" ссылку завершения боя (без captcha/FEND),
     * которую сервер отдает как переход вида:
     * `main.php?get_id=61&act=5&st=6&vcode=...`.
     *
     * Источники:
     * - прямой URL в HTML/JS (`findMainPhpLinkByQueryParts(...)`);
     * - fallback через `var fexp = [...]`, где `fexp[3]` содержит vcode.
     */
    private static String extractFightCleanFinishLinkFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        // 1) Direct link from HTML/JS, keep server st (6 or 7).
        String directLink = findMainPhpLinkByQueryParts(html, "get_id=61", "act=5", "st=6", "vcode=");
        if (directLink == null || directLink.isEmpty()) {
            directLink = findMainPhpLinkByQueryParts(html, "get_id=61", "act=5", "st=7", "vcode=");
        }
        if (directLink != null && !directLink.isEmpty()) {
            return normalizeNeverlandsMainLink(directLink);
        }

        // 2) Compact onclick variant ?get_id=61&act=5&st=6|7&vcode=...
        String source = html.replace("&amp;", "&");
        java.util.regex.Pattern compactPattern = java.util.regex.Pattern.compile(
                "(?:\\?|&|\\b)get_id=61&act=5&st=([67])&vcode=([A-Za-z0-9]+)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher compactMatcher = compactPattern.matcher(source);
        if (compactMatcher.find()) {
            String compactVcode = compactMatcher.group(2);
            if (compactVcode != null && !compactVcode.isEmpty()) {
                String compactSt = compactMatcher.group(1);
                if (compactSt == null || compactSt.isEmpty()) {
                    compactSt = "6";
                }
                return normalizeNeverlandsMainLink("main.php?get_id=61&act=5&st=" + compactSt + "&vcode=" + compactVcode);
            }
        }

        // 3) Fallback via fexp[3] (vcode), st inferred from fight_ty[4].
        String rawFexp = HelperStrings.subString(html, "var fexp = [", "];" );
        if (rawFexp == null || rawFexp.isEmpty()) {
            return null;
        }
        List<String> fexp = splitJsTopLevelCsv(rawFexp);
        if (fexp.size() < 4) {
            return null;
        }
        String vcode = trimJsToken(fexp.get(3));
        if (vcode == null || vcode.isEmpty() || "0".equals(vcode)) {
            return null;
        }

        String finishSt = resolveFightFinishStateForAct5(html);
        String finishLink = "main.php?get_id=61&act=5&st=" + finishSt + "&vcode=" + vcode;
        return normalizeNeverlandsMainLink(finishLink);
    }

    /**
     * Returns st for act=5 from fight_ty[4]. Fallback is "6".
     */
    private static String resolveFightFinishStateForAct5(String html) {
        String rawFightTy = HelperStrings.subString(html, "var fight_ty = [", "];" );
        if (rawFightTy == null || rawFightTy.isEmpty()) {
            return "6";
        }
        List<String> fightTy = splitJsTopLevelCsv(rawFightTy);
        if (fightTy.size() <= 4) {
            return "6";
        }
        String st = trimJsToken(fightTy.get(4));
        if ("6".equals(st) || "7".equals(st)) {
            return st;
        }
        return "6";
    }
    private static String buildFightEndFormSubmitHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        try {
            Document doc = Jsoup.parse(html);
            Element form = doc.selectFirst("form[name=FEND], form#FEND, form[action*=main.php]");
            if (form == null) {
                return null;
            }
            Element codeInput = form.selectFirst("input[name=code]");
            if (codeInput != null) {
                String codeValue = codeInput.hasAttr("value") ? codeInput.attr("value").trim() : "";
                if (codeValue.isEmpty() || "????".equals(codeValue)) {
                    android.util.Log.d(TAG, "buildFightEndFormSubmitHtml: code required, skip auto-submit");
                    return null;
                }
            }
            String action = form.hasAttr("action") ? form.attr("action").trim() : "";
            if (action.isEmpty()) {
                action = "main.php";
            }
            String method = form.hasAttr("method") ? form.attr("method").trim().toLowerCase(Locale.ROOT) : "post";
            if (!"get".equals(method) && !"post".equals(method)) {
                method = "post";
            }
            Elements fields = form.select("input[name], select[name], textarea[name]");
            if (fields.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(HtmlUtils.GENERATED_PAGE_MARKER);
            sb.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">");
            sb.append("<title>ABClient</title></head><body>");
            sb.append("Завершение боя...<br>");
            sb.append("<form id=\"ab_finish_form\" action=\"")
                    .append(escapeHtmlAttr(action))
                    .append("\" method=\"")
                    .append(method)
                    .append("\">");
            for (Element field : fields) {
                String tag = field.tagName().toLowerCase(Locale.ROOT);
                String name = field.hasAttr("name") ? field.attr("name") : "";
                if (name.isEmpty()) {
                    continue;
                }
                String value = "";
                if ("input".equals(tag)) {
                    String type = field.hasAttr("type") ? field.attr("type").toLowerCase(Locale.ROOT) : "text";
                    if ("submit".equals(type) || "button".equals(type) || "reset".equals(type)
                            || "image".equals(type) || "file".equals(type)) {
                        continue;
                    }
                    value = field.hasAttr("value") ? field.attr("value") : "";
                } else if ("textarea".equals(tag)) {
                    value = field.text();
                } else if ("select".equals(tag)) {
                    Element selected = field.selectFirst("option[selected]");
                    if (selected == null) {
                        selected = field.selectFirst("option");
                    }
                    value = selected != null ? selected.attr("value") : "";
                }
                sb.append("<input type=\"hidden\" name=\"")
                        .append(escapeHtmlAttr(name))
                        .append("\" value=\"")
                        .append(escapeHtmlAttr(value))
                        .append("\">");
            }
            sb.append("</form>");
            sb.append("<script language=\"JavaScript\">");
            sb.append("setTimeout(function(){var f=document.getElementById('ab_finish_form'); if(f){f.submit();}}, 350);");
            sb.append("</script></body></html>");
            return sb.toString();
        } catch (Exception e) {
            android.util.Log.e(TAG, "buildFightEndFormSubmitHtml error", e);
            return null;
        }
    }
    /**
     * Извлекает компактные маркеры завершения боя из сырого HTML.
     *
     * Зависимости:
     * - Селекторы Jsoup для {@code form[name=FEND]} и {@code input[name=code]}.
     * - {@link HelperStrings#subString(String, String, String)} + {@link #splitJsTopLevelCsv(String)}
     *   для массивов {@code fight_ty} / {@code fexp}.
     *
     * Результат:
     * - Флаги наличия FEND/code/fkey/картинки капчи.
     * - Опциональные challenge hash и captcha token для диагностики/дедупликации.
     */
    private static FightFinishPageMarkers inspectFightFinishPageMarkers(String html) {
        FightFinishPageMarkers markers = new FightFinishPageMarkers();
        if (html == null || html.isEmpty()) {
            return markers;
        }
        try {
            Document doc = Jsoup.parse(html);
            Element fend = doc.selectFirst("form[name=FEND], form#FEND, form[action*=main.php]");
            if (fend != null) {
                markers.hasFendForm = true;
                String action = fend.hasAttr("action") ? fend.attr("action").trim() : "";
                if (!action.isEmpty()) {
                    markers.fendAction = action;
                }
                Element codeInput = fend.selectFirst("input[name=code]");
                if (codeInput != null) {
                    markers.hasCodeInput = true;
                    String code = codeInput.hasAttr("value") ? codeInput.attr("value").trim() : "";
                    if (code.isEmpty()) {
                        markers.codeState = "empty";
                    } else if ("????".equals(code)) {
                        markers.codeState = "placeholder";
                    } else {
                        markers.codeState = "filled";
                    }
                }
            }
            markers.hasFkeyScript = html.contains("js/fkey.js") || html.contains("d.FEND.code.value");
            markers.hasCaptchaImage = html.contains("/modules/code/code.php");
            String rawFightTy = HelperStrings.subString(html, "var fight_ty = [", "];");
            if (rawFightTy != null && !rawFightTy.isEmpty()) {
                List<String> fightTy = splitJsTopLevelCsv(rawFightTy);
                if (fightTy.size() > 5) {
                    markers.challengeHash = trimJsToken(fightTy.get(5));
                }
            }
            String rawFexp = HelperStrings.subString(html, "var fexp = [", "];");
            if (rawFexp != null && !rawFexp.isEmpty()) {
                List<String> fexp = splitJsTopLevelCsv(rawFexp);
                if (fexp.size() > 4) {
                    markers.fexpCaptchaToken = trimJsToken(fexp.get(4));
                }
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "inspectFightFinishPageMarkers error", e);
        }
        return markers;
    }
    /**
     * Пишет одну структурированную диагностическую запись для выбранной ветви завершения боя.
     *
     * Зависимости:
     * - Решение из {@link FinishFlowDecision}.
     * - Разобранные маркеры страницы из {@link FightFinishPageMarkers}.
     * - Runtime-контекст из {@link LezFight}, адрес запроса, ссылки, URL капчи.
     *
     * Примечания:
     * - Этот логгер намеренно подробный и является основным источником
     *   постмортем-анализа поведения finish/captcha в runtime-логах.
     */
    private static void logFinishFlowDecision(FinishFlowDecision decision,
                                              LezFight fight,
                                              String address,
                                              String fightLink,
                                              String captchaUrl,
                                              FightFinishPageMarkers markers,
                                              String reason) {
        String logBoi = (fight != null && fight.LogBoi != null) ? fight.LogBoi : "";
        String challenge = (markers != null && markers.challengeHash != null) ? markers.challengeHash : "";
        String codeState = (markers != null && markers.codeState != null) ? markers.codeState : "none";
        boolean hasFend = markers != null && markers.hasFendForm;
        boolean hasCodeInput = markers != null && markers.hasCodeInput;
        boolean hasFkey = markers != null && markers.hasFkeyScript;
        boolean hasCaptchaImage = markers != null && markers.hasCaptchaImage;
        String fendAction = (markers != null && markers.fendAction != null) ? markers.fendAction : "";
        String fexpToken = (markers != null && markers.fexpCaptchaToken != null) ? markers.fexpCaptchaToken : "";
        String tokenState = fexpToken.isEmpty() ? "empty" : ("len=" + fexpToken.length());
        android.util.Log.d(TAG, "mainPhpFight finishFlow:"
                + " decision=" + decision
                + ", reason=" + reason
                + ", LogBoi=" + logBoi
                + ", challengeHash=" + challenge
                + ", hasFEND=" + hasFend
                + ", hasCodeInput=" + hasCodeInput
                + ", codeState=" + codeState
                + ", hasFkeyJs=" + hasFkey
                + ", hasCaptchaImage=" + hasCaptchaImage
                + ", fexpCaptchaToken=" + tokenState
                + ", fendAction=" + fendAction
                + ", fightLink=" + (fightLink == null ? "" : fightLink)
                + ", captchaUrl=" + (captchaUrl == null ? "" : captchaUrl)
                + ", address=" + (address == null ? "" : address));
    }
    /**
     * Экранирует строку для безопасной подстановки в HTML-атрибут.
     *
     * Зависимости:
     * - Используется при построении динамических HTML-форм (например, auto-submit для FEND).
     *
     * Назначение:
     * - Исключить ломание разметки из-за спецсимволов ({@code & " < >}) в значениях input-полей.
     */
    private static String escapeHtmlAttr(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
    /**
     * Порт `MainPhpInsHp.cs` (C# -> Android).
     * Извлекает `hp_int`/`ma_int` из `ins_HP(...)` и обновляет
     * `AppVars.PersIntHP`/`AppVars.PersIntMA`.
     */
    private static void mainPhpInsHp(String html) {
        try {
            InsHpSnapshot snapshot = parseInsHpSnapshot(html);
            if (snapshot == null) return;
            // Порт 1:1 из C# MainPhpInsHp.cs: par[4] -> IntHP, par[5] -> IntMA.
            if (snapshot.intHp > 0d) AppVars.PersIntHP = snapshot.intHp;
            if (snapshot.intMa > 0d) AppVars.PersIntMA = snapshot.intMa;
            android.util.Log.d(TAG, "mainPhpInsHp: parsed hpInt=" + AppVars.PersIntHP + ", maInt=" + AppVars.PersIntMA);
        } catch (Exception e) {
            android.util.Log.e(TAG, "mainPhpInsHp error", e);
        }
    }
    /**
     * Парсит снимок из вызова `ins_HP(...)`: cur/max HP, cur/max MA, hp_int/ma_int.
     * Возвращает `null`, если вызов не найден или формат невалиден.
     */
    private static InsHpSnapshot parseInsHpSnapshot(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String htmlLower = html.toLowerCase(Locale.ROOT);
        int start = htmlLower.indexOf("ins_hp(");
        if (start == -1) {
            return null;
        }
        start += "ins_hp(".length();
        int end = html.indexOf(')', start);
        if (end == -1 || end <= start) {
            return null;
        }
        String args = html.substring(start, end);
        String[] parts = args.split(",");
        if (parts.length != 6) {
            android.util.Log.d(TAG, "parseInsHpSnapshot: unexpected args count=" + parts.length + ", raw=" + args);
            return null;
        }
        Double curHpRaw = tryParseDoubleInvariant(parts[0]);
        Double maxHpRaw = tryParseDoubleInvariant(parts[1]);
        Double curMaRaw = tryParseDoubleInvariant(parts[2]);
        Double maxMaRaw = tryParseDoubleInvariant(parts[3]);
        Double intHpRaw = tryParseDoubleInvariant(parts[4]);
        Double intMaRaw = tryParseDoubleInvariant(parts[5]);
        if (curHpRaw == null || maxHpRaw == null || curMaRaw == null || maxMaRaw == null
                || intHpRaw == null || intMaRaw == null) {
            return null;
        }
        InsHpSnapshot snapshot = new InsHpSnapshot();
        snapshot.curHp = (int) Math.round(curHpRaw);
        snapshot.maxHp = (int) Math.round(maxHpRaw);
        snapshot.curMa = (int) Math.round(curMaRaw);
        snapshot.maxMa = (int) Math.round(maxMaRaw);
        snapshot.intHp = intHpRaw;
        snapshot.intMa = intMaRaw;
        return snapshot;
    }
    /**
     * Авто-питьё "Эликсира Восстановления" по данным верхнего фрейма (`ins_HP(...)`).
     *
     * Условия запуска:
     * - включен Auto-Fight (persisted state),
     * - включена хотя бы одна опция `LezDoDrinkHp`/`LezDoDrinkMa`,
     * - текущие проценты HP/MA ниже заданных порогов,
     * - нет активного fast-конвейера (`AppVars.FastNeed == false`),
     * - текущая страница не является боевым фреймом.
     *
     * Особенность:
     * - если одновременно сработали HP и MA, выполняется ОДИН запуск fast-action
     *   (`FastActionManager.fastAttackMomentRestoreElixir()`), т.к. эликсир восстанавливает оба ресурса.
     */
    private static void tryTriggerAutoDrinkRestoreElixir(String address,
                                                         String html,
                                                         boolean isFightFrame,
                                                         boolean isFightTopFrame) {
        if (html == null || html.isEmpty()) {
            return;
        }
        if (AppVars.Profile == null) {
            return;
        }
        if (!isAutoFightEnabledByPreference()) {
            android.util.Log.d(TAG, "AUTO_DRINK_TRACE skip: auto-fight disabled in preferences");
            return;
        }
        if (isFightFrame || isFightTopFrame) {
            return;
        }
        // Важный guard: автопитьё запускаем только после получения "полного" main.php от сервера
        // (без get_id/act/query), чтобы не конкурировать с post-fight цепочкой завершения боя.
        if (!isServerPlainMainAddress(address)) {
            android.util.Log.d(TAG, "AUTO_DRINK_TRACE skip: wait plain main.php, address=" + address);
            return;
        }
        if (AppVars.FastNeed) {
            android.util.Log.d(TAG, "AUTO_DRINK_TRACE skip: FastNeed active, fastId=" + AppVars.FastId);
            return;
        }
        if (address != null && address.contains("get_id=43")) {
            android.util.Log.d(TAG, "AUTO_DRINK_TRACE skip: get_id=43 action page");
            return;
        }
        if (AppVars.IsFightCaptchaDialogVisible) {
            android.util.Log.d(TAG, "AUTO_DRINK_TRACE skip: captcha dialog visible");
            return;
        }
        InsHpSnapshot snapshot = parseInsHpSnapshot(html);
        if (snapshot == null || (snapshot.maxHp <= 0 && snapshot.maxMa <= 0)) {
            android.util.Log.d(TAG, "AUTO_DRINK_TRACE skip: ins_HP snapshot missing or invalid");
            return;
        }
        double hpPercent = snapshot.maxHp > 0 ? (snapshot.curHp * 100.0 / snapshot.maxHp) : 0.0;
        double maPercent = snapshot.maxMa > 0 ? (snapshot.curMa * 100.0 / snapshot.maxMa) : 0.0;
        boolean hpBelow = AppVars.Profile.LezDoDrinkHp
                && snapshot.maxHp > 0
                && hpPercent < AppVars.Profile.LezDrinkHp;
        boolean maBelow = AppVars.Profile.LezDoDrinkMa
                && snapshot.maxMa > 0
                && maPercent < AppVars.Profile.LezDrinkMa;
        if (!hpBelow && !maBelow) {
            android.util.Log.d(TAG, "AUTO_DRINK_TRACE no-trigger: hp="
                    + String.format(Locale.US, "%.1f", hpPercent) + "%/" + AppVars.Profile.LezDrinkHp
                    + " (enabled=" + AppVars.Profile.LezDoDrinkHp + "), ma="
                    + String.format(Locale.US, "%.1f", maPercent) + "%/" + AppVars.Profile.LezDrinkMa
                    + " (enabled=" + AppVars.Profile.LezDoDrinkMa + "), address=" + address);
            return;
        }
        long now = System.currentTimeMillis();
        long sinceLastTrigger = now - lastAutoDrinkTriggerAtMs;
        if (sinceLastTrigger >= 0 && sinceLastTrigger < AUTO_DRINK_TRIGGER_COOLDOWN_MS) {
            android.util.Log.d(TAG, "AUTO_DRINK_TRACE skip cooldown: sinceLastMs=" + sinceLastTrigger
                    + ", hpBelow=" + hpBelow + ", maBelow=" + maBelow);
            return;
        }
        lastAutoDrinkTriggerAtMs = now;
        android.util.Log.d(TAG, "AUTO_DRINK_TRACE trigger restore elixir: hp="
                + snapshot.curHp + "/" + snapshot.maxHp + " (" + String.format(Locale.US, "%.1f", hpPercent) + "%)"
                + ", ma=" + snapshot.curMa + "/" + snapshot.maxMa + " (" + String.format(Locale.US, "%.1f", maPercent) + "%)"
                + ", hpThreshold=" + AppVars.Profile.LezDrinkHp + ", maThreshold=" + AppVars.Profile.LezDrinkMa
                + ", hpEnabled=" + AppVars.Profile.LezDoDrinkHp + ", maEnabled=" + AppVars.Profile.LezDoDrinkMa
                + ", address=" + address);
        FastActionManager.fastAttackMomentRestoreElixir();
    }

    /**
     * Проверяет, что адрес соответствует "чистому" серверному main.php:
     * - путь `/main.php`;
     * - без query-параметров;
     * - хост `neverlands.ru` или `www.neverlands.ru`.
     *
     * Нужен для post-fight сценария: fast-запросы на эликсир нельзя запускать на `get_id=61&act=7`,
     * иначе они могут быть вытеснены повторной отправкой finish-link.
     */
    private static boolean isServerPlainMainAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }
        String normalized = address.trim();
        if ("main.php".equalsIgnoreCase(normalized)) {
            return true;
        }
        try {
            URI uri = new URI(normalized);
            String host = uri.getHost();
            String path = uri.getPath();
            String query = uri.getRawQuery();
            boolean hostOk = host == null
                    || "neverlands.ru".equalsIgnoreCase(host)
                    || "www.neverlands.ru".equalsIgnoreCase(host);
            boolean pathOk = path != null && "/main.php".equalsIgnoreCase(path);
            boolean queryEmpty = query == null || query.isEmpty();
            return hostOk && pathOk && queryEmpty;
        } catch (Exception ignored) {
            String lower = normalized.toLowerCase(Locale.ROOT);
            return "http://neverlands.ru/main.php".equals(lower)
                    || "http://www.neverlands.ru/main.php".equals(lower)
                    || "https://neverlands.ru/main.php".equals(lower)
                    || "https://www.neverlands.ru/main.php".equals(lower);
        }
    }
    /**
     * Invariant-парсинг числа (аналог NumberStyles.Any + InvariantCulture в C#).
     * Допускает кавычки вокруг значения.
     */
    private static Double tryParseDoubleInvariant(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim()
                .replace("\"", "")
                .replace("'", "")
                .replace('\u00A0', ' ')
                .replace(" ", "")
                .replace(",", ".");
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    /**
     * Порт блока чтения умений из C# `MainPhp.cs`:
     * - при наличии блока "Охота ... [N]" обновляет `AppVars.SkinUm`;
     * - сбрасывает `AppVars.AutoSkinCheckUm = false` после успешного чтения;
     * - при росте навыка (и включённом AutoSkin) отправляет сообщение в чат.
     *
     * Это обязательная часть C#-цепочки `AutoSkinCheckUm -> mselect=1`,
     * без неё возникает бесконечный цикл "Переключение на умения персонажа".
     */
    private static void mainPhpProcessSkills(String html, String address) {
        if (html == null || html.isEmpty()) {
            return;
        }
        String skinSkill = HelperStrings.subString(
                html,
                "Охота</td><td bgcolor=#FCFAF3><font class=proce><font color=#555555><div align=center>[",
                "]");
        if (skinSkill == null || skinSkill.isEmpty()) {
            // Fallback-парсинг на случай косметических изменений HTML.
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("Охота</td><td[^\\[]*\\[(\\d+)\\]", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                    .matcher(html);
            if (matcher.find()) {
                skinSkill = matcher.group(1);
            }
        }
        if (skinSkill != null && !skinSkill.isEmpty()) {
            try {
                int skinUm = Integer.parseInt(skinSkill.trim());
                AppVars.AutoSkinCheckUm = false;
                if (AppVars.SkinUm != skinUm) {
                    StringBuilder sb = new StringBuilder("Умение разделки: <span style=\"color:#009933;font-weight:bold;\">")
                            .append(skinUm)
                            .append("</span>");
                    if (AppVars.SkinUm > 0 && AppVars.SkinUm < skinUm) {
                        sb.append(" (+").append(skinUm - AppVars.SkinUm).append(")");
                    }
                    AppVars.SkinUm = skinUm;
                    if (isAutoSkinEnabledByPreference() && AppVars.getContext() != null) {
                        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
                        msgIntent.putExtra("message", sb.toString());
                        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
                    }
                }
                android.util.Log.d(TAG, "AUTO_SKIN_TRACE skill parsed: SkinUm=" + skinUm + ", AutoSkinCheckUm=false");
            } catch (Exception e) {
                android.util.Log.w(TAG, "AUTO_SKIN_TRACE skill parse failed: " + skinSkill, e);
            }
            return;
        }
        // Защитный сброс от зацикливания: мы уже на странице mselect=1,
        // но сервер не выдал ожидаемый блок навыка.
        if (AppVars.AutoSkinCheckUm && address != null && address.contains("mselect=1")) {
            AppVars.AutoSkinCheckUm = false;
            android.util.Log.w(TAG, "AUTO_SKIN_TRACE mselect=1 without skill block, forced AutoSkinCheckUm=false");
        }
    }
    /**
     * Определяет, включена ли Авто-Охота (C# `Profile.SkinAuto` / `buttonAutoSkin`).
     */
    private static boolean isAutoSkinEnabledByPreference() {
        if (AppVars.Profile != null) {
            return AppVars.Profile.SkinAuto;
        }
        try {
            if (AppVars.getContext() != null) {
                return AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoSkinEnabled();
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "isAutoSkinEnabledByPreference: fallback=false", e);
        }
        return false;
    }
    /**
     * Определяет, включена ли Авто-Рыбалка в профиле/настройках.
     */
    private static boolean isAutoFishEnabledByPreference() {
        if (AppVars.Profile != null) {
            return AppVars.Profile.AutoFish;
        }
        try {
            if (AppVars.getContext() != null) {
                return AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoFishEnabled();
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "isAutoFishEnabledByPreference: fallback=false", e);
        }
        return false;
    }
    /**
     * Чтение умения "Рыбалка" на странице `mselect=1` (C# parity для `AutoFishCheckUm`).
     */
    private static void mainPhpProcessFishSkills(String html, String address) {
        if (html == null || html.isEmpty()) {
            return;
        }
        String fishSkill = HelperStrings.subString(
                html,
                "Рыбалка</td><td bgcolor=#FCFAF3><font class=proce><font color=#555555><div align=center>[",
                "]");
        if (fishSkill == null || fishSkill.isEmpty()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("Рыбалка</td><td[^\\[]*\\[(\\d+)\\]", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                    .matcher(html);
            if (matcher.find()) {
                fishSkill = matcher.group(1);
            }
        }
        if (fishSkill != null && !fishSkill.isEmpty()) {
            try {
                int fishUm = Integer.parseInt(fishSkill.trim());
                AppVars.AutoFishCheckUm = false;
                if (AppVars.Profile != null) {
                    AppVars.Profile.FishUm = fishUm;
                }
                android.util.Log.d(TAG, "AUTO_FISH_TRACE skill parsed: FishUm=" + fishUm + ", AutoFishCheckUm=false");
            } catch (Exception e) {
                android.util.Log.w(TAG, "AUTO_FISH_TRACE skill parse failed: " + fishSkill, e);
            }
            return;
        }
        if (AppVars.AutoFishCheckUm && address != null && address.contains("mselect=1")) {
            AppVars.AutoFishCheckUm = false;
            android.util.Log.w(TAG, "AUTO_FISH_TRACE mselect=1 without Рыбалка block, forced AutoFishCheckUm=false");
        }
    }
    /**
     * Определяет, включен ли режим "Снежок/Ярость (первый удар на осаде)".
     *
     * Источник:
     * - профильный флаг `UserConfig.LezDoFury`;
     * - fallback на runtime-флаг `AppVars.DoFury`.
     */
    private static boolean isAutoFuryEnabledByPreference() {
        if (AppVars.Profile != null) {
            boolean enabled = AppVars.Profile.hasAnyLezFuryGroup();
            // Поддерживаем синхронизацию legacy-глобального флага для обратной совместимости.
            // Зависимости:
            // - старые ветки, где читается `Profile.LezDoFury` как единый переключатель;
            // - новый режим, где источник истины — пер-групповые `group@doFury`.
            AppVars.Profile.LezDoFury = enabled;
            if (!enabled) {
                AppVars.DoFury = false;
            }
            return enabled;
        }
        return AppVars.DoFury;
    }
    /**
     * Периодическая (раз в минуту) установка флага проверки ножа,
     * аналог `FormMainTicks.cs`: `AutoSkinLastChecked` -> `AutoSkinCheckKnife = true`.
     */
    private static void maybeMarkAutoSkinKnifeRecheck() {
        if (!isAutoSkinEnabledByPreference()) {
            return;
        }
        long now = System.currentTimeMillis();
        long lastChecked = AppVars.AutoSkinLastChecked;
        if (lastChecked <= 0L || (now - lastChecked) > AUTO_SKIN_KNIFE_RECHECK_INTERVAL_MS) {
            AppVars.AutoSkinLastChecked = now;
            AppVars.AutoSkinCheckKnife = true;
            android.util.Log.d(TAG, "AUTO_SKIN_TRACE periodic knife recheck requested");
        }
    }
    private static String mainPhpWtime(String html) {
        html = html.replace("id=wtime></div>", "id=wtime><i>\u0412\u044b\u043f\u043e\u043b\u043d\u044f\u0435\u0442\u0441\u044f \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435...</i></div>");
        String staticScriptEnd = "</SCRIPT>";
        int poswt = html.toLowerCase(java.util.Locale.ROOT).lastIndexOf(staticScriptEnd.toLowerCase());
        if (poswt != -1) {
            poswt += staticScriptEnd.length();
        }
        if (poswt != -1 && AppVars.AutoMoving && AppVars.AutoMovingJumps > 0) {
            String statusHtml = "<font class=nickname><div align=center style=\"color: #660066;\"><i>"
                    + "\u041f\u0443\u043d\u043a\u0442 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f: <b>" + AppVars.AutoMovingDestinaton + "</b><br>"
                    + "\u0415\u0449\u0435 \u043f\u0435\u0440\u0435\u0445\u043e\u0434\u043e\u0432: <b>" + AppVars.AutoMovingJumps + "</b>"
                    + (AppVars.DoSearchBox ? "<br>\u0418\u0449\u0435\u043c \u043a\u043b\u0430\u0434..." : "")
                    + "</i></div></font>";
            html = html.substring(0, poswt) + statusHtml + html.substring(poswt);
        }
        return html;
    }

    /**
     * Запускает авто-переход к целевой клетке для режима "Авто-Клад" (DoSearchBox).
     */
    private static void startAutoSearchBoxMoving(String destination) {
        if (destination == null || destination.isEmpty()) {
            return;
        }
        AppVars.AutoMoving = true;
        AppVars.AutoMovingDestinaton = destination;
        AppVars.AutoMovingMapPath = null;
        AppVars.AutoMovingNextJump = null;
        AppVars.AutoMovingJumps = 0;
        AppVars.AutoMovingCityGate = ru.neverlands.abclient.model.CityGateType.None;

        String mapLocation = (AppVars.Profile != null) ? AppVars.Profile.MapLocation : null;
        if (mapLocation != null && !mapLocation.isEmpty()) {
            ru.neverlands.abclient.utils.MapPath path =
                    new ru.neverlands.abclient.utils.MapPath(mapLocation, Collections.singletonList(destination));
            AppVars.AutoMovingMapPath = path;
            AppVars.AutoMovingNextJump = path.nextJump;
            AppVars.AutoMovingJumps = path.jumps;
            AppVars.AutoMovingCityGate = path.cityGate;
        }
    }

    private static int parseUnsignedIntFrom(String text, int fromIndex) {
        if (text == null || fromIndex < 0 || fromIndex >= text.length()) {
            return -1;
        }
        int i = fromIndex;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                break;
            }
            if (c == '\n' || c == '\r' || c == ';' || c == '<') {
                return -1;
            }
            i++;
        }
        if (i >= text.length()) {
            return -1;
        }
        int start = i;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            i++;
        }
        if (i <= start) {
            return -1;
        }
        try {
            return Integer.parseInt(text.substring(start, i));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static int extractWtimeTimeoutSeconds(String html) {
        if (html == null || html.isEmpty()) {
            return 0;
        }
        String lower = html.toLowerCase(Locale.ROOT);

        int tdSecIdx = lower.indexOf("id=tdsec");
        if (tdSecIdx < 0) tdSecIdx = lower.indexOf("id=\"tdsec\"");
        if (tdSecIdx < 0) tdSecIdx = lower.indexOf("id='tdsec'");
        if (tdSecIdx >= 0) {
            int gt = lower.indexOf('>', tdSecIdx);
            if (gt >= 0) {
                int sec = parseUnsignedIntFrom(lower, gt + 1);
                if (sec > 0 && sec < 86400) {
                    return sec;
                }
            }
        }

        int leftIdx = lower.indexOf("time_left_sec");
        if (leftIdx >= 0) {
            int eq = lower.indexOf('=', leftIdx);
            if (eq >= 0) {
                int value = parseUnsignedIntFrom(lower, eq + 1);
                if (value > 0) {
                    if (value > 1000) {
                        return (int) Math.ceil(value / 1000.0d);
                    }
                    return value;
                }
            }
        }

        int secGoIdx = lower.indexOf("secgo");
        if (secGoIdx >= 0) {
            int eq = lower.indexOf('=', secGoIdx);
            if (eq >= 0) {
                int sec = parseUnsignedIntFrom(lower, eq + 1);
                if (sec > 0 && sec < 86400) {
                    return sec;
                }
            }
        }

        return 0;
    }

    private static void syncNeverTimerFromWtime(String html, String address) {
        int timeoutSec = extractWtimeTimeoutSeconds(html);
        if (timeoutSec <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long dueAt = now + timeoutSec * 1000L;
        long prev = AppVars.NeverTimer;
        long prevDelta = prev - now;
        long newDelta = timeoutSec * 1000L;
        boolean updated = prev <= now || Math.abs(prevDelta - newDelta) > 1500L;
        if (updated) {
            AppVars.NeverTimer = dueAt;
        }

        if (updated || (now - lastWtimeSyncLogAtMs) >= WTIME_SYNC_LOG_GUARD_MS) {
            lastWtimeSyncLogAtMs = now;
            android.util.Log.d(TAG, "SERVER_TIMER_TRACE wtime sync: timeoutSec=" + timeoutSec
                    + ", updated=" + updated + ", address=" + address
                    + ", dueInMs=" + Math.max(0L, AppVars.NeverTimer - now));
        }
    }

    private static String mainPhpRaz(String html) {
        String strFightTy = HelperStrings.subString(html, "var fight_ty = [", "];");
        if (strFightTy == null || strFightTy.isEmpty()) {
            String fallbackRazLink = extractRazLinkFromHtml(html);
            if (fallbackRazLink != null) {
                android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpRaz: fallback link redirect to " + fallbackRazLink);
                return buildRedirectHtml("Разделка", fallbackRazLink);
            }
            return null;
        }
        List<String> fightTy = splitJsTopLevelCsv(strFightTy);
        if (fightTy.size() <= 9) {
            String fallbackRazLink = extractRazLinkFromHtml(html);
            if (fallbackRazLink != null) {
                android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpRaz: fallback link redirect to " + fallbackRazLink);
                return buildRedirectHtml("Разделка", fallbackRazLink);
            }
            return null;
        }
        String fightTyNine = fightTy.get(9);
        if (fightTyNine == null) {
            String fallbackRazLink = extractRazLinkFromHtml(html);
            if (fallbackRazLink != null) {
                android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpRaz: fallback link redirect to " + fallbackRazLink);
                return buildRedirectHtml("Разделка", fallbackRazLink);
            }
            return null;
        }
        String trimmed = fightTyNine.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            String fallbackRazLink = extractRazLinkFromHtml(html);
            if (fallbackRazLink != null) {
                android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpRaz: fallback link redirect to " + fallbackRazLink);
                return buildRedirectHtml("Разделка", fallbackRazLink);
            }
            return null;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1);
        List<String> razParams = splitJsTopLevelCsv(inner);
        if (razParams.size() <= 5) {
            String fallbackRazLink = extractRazLinkFromHtml(html);
            if (fallbackRazLink != null) {
                android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpRaz: fallback link redirect to " + fallbackRazLink);
                return buildRedirectHtml("Разделка", fallbackRazLink);
            }
            return null;
        }
        String type = trimJsToken(razParams.get(0));
        String p = trimJsToken(razParams.get(1));
        String uid = trimJsToken(razParams.get(2));
        String s = trimJsToken(razParams.get(3));
        String m = trimJsToken(razParams.get(4));
        String vcode = trimJsToken(razParams.get(5));
        if (type.isEmpty() || uid.isEmpty() || vcode.isEmpty()) {
            return null;
        }
        String razLink = "http://neverlands.ru/main.php?get_id=17&type=" + type
                + "&p=" + p
                + "&uid=" + uid
                + "&s=" + s
                + "&m=" + m
                + "&vcode=" + vcode;
        android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpRaz: redirect to " + razLink);
        return buildRedirectHtml("Разделка", razLink);
    }
    /**
     * Порт `MainPhpFindPerc` из C# (`MainPhpDrink.cs`).
     */
    /**
     * Fallback-поиск ссылки `main.php?get_id=17...` в HTML боя.
     * Используется, когда `fight_ty[9]` пустой/урезанный, но сервер отдает прямую ссылку "Разделать".
     */
    private static String extractRazLinkFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        return findMainPhpLinkByQueryParts(html, "get_id=17");
    }
    /**
     * Нормализует ссылку `main.php` для auto-redirect:
     * - приводит хост к `http://neverlands.ru` без `www`;
     * - разворачивает относительные варианты (`main.php`, `/main.php`, `../main.php`);
     * - декодирует `&amp;` в query-строке.
     */
    private static String normalizeNeverlandsMainLink(String link) {
        if (link == null || link.trim().isEmpty()) {
            return "http://neverlands.ru/main.php";
        }
        String normalized = link.trim().replace("&amp;", "&");
        while (normalized.startsWith("../")) {
            normalized = normalized.substring(3);
        }
        if (normalized.startsWith("//neverlands.ru/")) {
            normalized = "http:" + normalized;
        } else if (normalized.startsWith("/main.php")) {
            normalized = "http://neverlands.ru" + normalized;
        } else if (normalized.startsWith("main.php")) {
            normalized = "http://neverlands.ru/" + normalized;
        } else if (normalized.startsWith("?")) {
            normalized = "http://neverlands.ru/main.php" + normalized;
        }
        normalized = normalized.replace("https://www.neverlands.ru/", "http://neverlands.ru/");
        normalized = normalized.replace("http://www.neverlands.ru/", "http://neverlands.ru/");
        normalized = normalized.replace("https://neverlands.ru/", "http://neverlands.ru/");
        return normalized;
    }
    /**
     * Универсальный fallback-поиск ссылки `main.php?...` по набору query-маркеров.
     * Возвращает первую подходящую ссылку в нормализованном виде.
     */
    private static String findMainPhpLinkByQueryParts(String html, String... queryParts) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String source = html.replace("&amp;", "&");
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?:https?://(?:www\\.)?neverlands\\.ru/|\\.\\./|/)?main\\.php\\?[^'\"\\s<]+",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            String normalized = normalizeNeverlandsMainLink(candidate);
            boolean matches = true;
            if (queryParts != null) {
                for (String part : queryParts) {
                    if (part == null || part.isEmpty()) {
                        continue;
                    }
                    if (!normalized.contains(part)) {
                        matches = false;
                        break;
                    }
                }
            }
            if (matches) {
                return normalized;
            }
        }
        return null;
    }
    /**
     * Добавляет/дополняет query-параметры фильтра инвентаря (`&im=...&wca=...`) к найденной ссылке.
     * Если ссылка указывает на `go=inf`, переводит её на `go=inv`.
     */
    private static String applyInventoryFilterToLink(String link, String filter) {
        String normalized = normalizeNeverlandsMainLink(link);
        if (normalized.contains("go=inf")) {
            normalized = normalized.replace("go=inf", "go=inv");
        }
        if (filter == null || filter.isEmpty()) {
            return normalized;
        }
        String filterNormalized = filter.startsWith("&") ? filter.substring(1) : filter;
        if (filterNormalized.isEmpty()) {
            return normalized;
        }
        String[] queryParts = filterNormalized.split("&");
        String result = normalized;
        for (String part : queryParts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            String key = part;
            String value = "";
            int eq = part.indexOf('=');
            if (eq >= 0) {
                key = part.substring(0, eq);
                value = part.substring(eq + 1);
            }
            if (key.isEmpty()) {
                continue;
            }
            result = setOrAppendQueryParam(result, key, value);
        }
        return result;
    }

    // Global runtime pause for non-combat auto pipelines while FastAction is active.
    // Autoboi/fight flow must continue and therefore is not controlled by this helper.
    private static boolean isNonCombatAutoPausedByFastAction() {
        return AppVars.FastNeed && AppVars.FastPauseNonCombatAutoFunctions;
    }
    /**
     * Устанавливает query-параметр в URL:
     * - если параметр уже есть, заменяет его значение;
     * - если параметра нет, добавляет его в query.
     */
    private static String setOrAppendQueryParam(String url, String key, String value) {
        if (url == null || url.isEmpty() || key == null || key.isEmpty()) {
            return url;
        }
        String safeValue = (value == null) ? "" : value;
        String keyWithEq = key + "=";
        int queryStart = url.indexOf('?');
        if (queryStart == -1) {
            return url + "?" + keyWithEq + safeValue;
        }
        int from = queryStart + 1;
        while (from < url.length()) {
            int amp = url.indexOf('&', from);
            int end = (amp == -1) ? url.length() : amp;
            String part = url.substring(from, end);
            if (part.startsWith(keyWithEq) || part.equals(key)) {
                return url.substring(0, from) + keyWithEq + safeValue + url.substring(end);
            }
            if (amp == -1) {
                break;
            }
            from = amp + 1;
        }
        return url + "&" + keyWithEq + safeValue;
    }
    /**
     * Проверяет, что текущий адрес уже находится в разделе инвентаря (`go=inv`).
     * Используется как fallback-маркер, когда HTML-шаблон инвентаря может отличаться.
     */
    private static boolean isInventoryAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        String normalizedAddress = normalizeNeverlandsMainLink(address).toLowerCase(Locale.ROOT);
        if (!normalizedAddress.contains("main.php")) {
            return false;
        }
        if (normalizedAddress.contains("go=inv")) {
            return true;
        }
        if (normalizedAddress.contains("?wfo=") || normalizedAddress.contains("&wfo=")) {
            return true;
        }
        if (normalizedAddress.contains("useaction=clan-action")) {
            return true;
        }
        if (normalizedAddress.contains("useaction=addon-action") && normalizedAddress.contains("addid=")) {
            return true;
        }
        // C# parity: категории инвентаря часто приходят как main.php?wca=... или main.php?im=...
        // (включая переходы через "Вернуться"/useaction), без явного go=inv.
        return normalizedAddress.contains("?wca=")
                || normalizedAddress.contains("&wca=")
                || normalizedAddress.contains("?im=")
                || normalizedAddress.contains("&im=");
    }
    /**
     * Проверяет, что адрес инвентаря уже содержит все параметры из требуемого фильтра
     * с совпадающими значениями (`im`, `wca` и т.д.).
     */
    private static boolean inventoryAddressMatchesFilter(String address, String filter) {
        if (!isInventoryAddress(address)) {
            return false;
        }
        String normalizedAddress = normalizeNeverlandsMainLink(address);
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        String filterNormalized = filter.startsWith("&") ? filter.substring(1) : filter;
        if (filterNormalized.isEmpty()) {
            return true;
        }
        String[] queryParts = filterNormalized.split("&");
        for (String part : queryParts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String key = (eq >= 0) ? part.substring(0, eq) : part;
            String expectedValue = (eq >= 0) ? part.substring(eq + 1) : "";
            String currentValue = getQueryParamValue(normalizedAddress, key);
            if (currentValue == null || !currentValue.equals(expectedValue)) {
                return false;
            }
        }
        return true;
    }
    /**
     * Возвращает значение query-параметра из URL или null, если параметр отсутствует.
     */
    private static String getQueryParamValue(String url, String key) {
        if (url == null || url.isEmpty() || key == null || key.isEmpty()) {
            return null;
        }
        int queryStart = url.indexOf('?');
        if (queryStart == -1 || queryStart + 1 >= url.length()) {
            return null;
        }
        String query = url.substring(queryStart + 1);
        String[] parts = query.split("&");
        String keyWithEq = key + "=";
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (part.startsWith(keyWithEq)) {
                return part.substring(keyWithEq.length());
            }
            if (part.equals(key)) {
                return "";
            }
        }
        return null;
    }
    /**
     * Ищет и строит redirect на страницу персонажа ({@code go=inf}) через {@code vcode}.
     *
     * Зависимости:
     * - {@link HelperStrings#subString(String, String, String)} для прямого извлечения vcode.
     * - {@link #findMainPhpLinkByQueryParts(String, String...)} как fallback-поиск ссылки в HTML.
     * - {@link #buildRedirectHtml(String, String)} для формирования служебного redirect HTML.
     *
     * Назначение:
     * - Гарантированно вернуть контекст на "персонажа" после авто-действий, даже при отличиях шаблона страницы.
     */
    private static String mainPhpFindPerc(String html) {
        String vcode = HelperStrings.subString(html, "'main.php?get_id=56&act=10&go=inf&vcode=", "'");
        if (vcode != null && !vcode.isEmpty()) {
            String link = "main.php?get_id=56&act=10&go=inf&vcode=" + vcode;
            return buildRedirectHtml("Переключение на персонаж", link);
        }
        String patternEnter = "[\"inf\",\"Ваш персонаж\",\"";
        int posPatternEnter = html.indexOf(patternEnter);
        if (posPatternEnter == -1) {
            String fallbackLink = findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
            if (fallbackLink != null) {
                android.util.Log.d(TAG, "AUTO_FALLBACK_TRACE mainPhpFindPerc: regex fallback -> " + fallbackLink);
                return buildRedirectHtml("Переключение на персонаж", fallbackLink);
            }
            return null;
        }
        posPatternEnter += patternEnter.length();
        int posEnd = html.indexOf('"', posPatternEnter);
        if (posEnd == -1) {
            String fallbackLink = findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
            if (fallbackLink != null) {
                android.util.Log.d(TAG, "AUTO_FALLBACK_TRACE mainPhpFindPerc: regex fallback -> " + fallbackLink);
                return buildRedirectHtml("Переключение на персонаж", fallbackLink);
            }
            return null;
        }
        String jsonVcode = html.substring(posPatternEnter, posEnd);
        if (jsonVcode == null || jsonVcode.isEmpty()) {
            String fallbackLink = findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
            if (fallbackLink != null) {
                android.util.Log.d(TAG, "AUTO_FALLBACK_TRACE mainPhpFindPerc: regex fallback -> " + fallbackLink);
                return buildRedirectHtml("Переключение на персонаж", fallbackLink);
            }
            return null;
        }
        String link = "main.php?get_id=56&act=10&go=inf&vcode=" + jsonVcode;
        return buildRedirectHtml("Переключение на персонаж", link);
    }
    /**
     * Порт `MainPhpFindFlora` из ПК-версии (`MainPhpDrink.cs`):
     * автоматический переход на "природу/карту" через кнопку "Вернуться".
     *
     * Зависимости:
     * - `buildRedirectHtml(...)` для формирования redirect-страницы;
     * - HTML-кнопка вида `value="Вернуться"` + `onclick="location='...'"`.
     *
     * Назначение:
     * - после авто-надевания снастей вернуть клиента из "Ваш персонаж" обратно на карту,
     *   где доступна кнопка "Рыбалка";
     * - убрать ручной шаг пользователя "нажать Вернуться".
     */
    private static String mainPhpFindFlora(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        // C# parity: если "Причал" DISABLED, автопереход на природу не нужен.
        if (containsIgnoreCase(html, "<input type=button class=lbutdis value=\"Причал\" disabled>")) {
            return null;
        }
        final String returnMarker = "value=\"Вернуться\">";
        int posReturn = html.toLowerCase(Locale.ROOT).indexOf(returnMarker.toLowerCase(Locale.ROOT));
        if (posReturn == -1) {
            return null;
        }
        final String onClickPrefix = "onclick=\"location='";
        int posOnClick = html.toLowerCase(Locale.ROOT).lastIndexOf(
                onClickPrefix.toLowerCase(Locale.ROOT),
                posReturn
        );
        if (posOnClick == -1) {
            return null;
        }
        int linkStart = posOnClick + onClickPrefix.length();
        int linkEnd = html.indexOf('\'', linkStart);
        if (linkEnd == -1 || linkEnd <= linkStart) {
            return null;
        }
        String link = html.substring(linkStart, linkEnd);
        if (link.isEmpty()) {
            return null;
        }
        return buildRedirectHtml("Переключение на природу", link);
    }
    /**
     * C# parity (`MainPhpFindFish`): на карте вставляет вызов `Fish('<vcode>')` после `view_map();`.
     */
    private static String mainPhpFindMapReturnForAutoMoving(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        String retVcode = mainPhpExtractMenuVcode(html, "ret");

        String mapLink = findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=ret", "vcode=");
        if (mapLink == null) {
            mapLink = findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=ret");
        }
        if ((mapLink == null || mapLink.isEmpty()) && retVcode != null && !retVcode.isEmpty()) {
            mapLink = normalizeNeverlandsMainLink("main.php?get_id=56&act=10&go=ret&vcode=" + retVcode);
        }
        if (mapLink == null) {
            String infLink = findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
            if (infLink != null && !infLink.isEmpty()) {
                mapLink = normalizeNeverlandsMainLink(infLink.replace("go=inf", "go=ret"));
            }
        }

        if (mapLink == null) {
            final String returnMarker = "value=\"Вернуться\">";
            int posReturn = html.toLowerCase(Locale.ROOT).indexOf(returnMarker.toLowerCase(Locale.ROOT));
            if (posReturn != -1) {
                final String onClickPrefix = "onclick=\"location='";
                int posOnClick = html.toLowerCase(Locale.ROOT).lastIndexOf(
                        onClickPrefix.toLowerCase(Locale.ROOT),
                        posReturn
                );
                if (posOnClick != -1) {
                    int linkStart = posOnClick + onClickPrefix.length();
                    int linkEnd = html.indexOf('\'', linkStart);
                    if (linkEnd > linkStart) {
                        mapLink = normalizeNeverlandsMainLink(html.substring(linkStart, linkEnd));
                        if (retVcode != null && !retVcode.isEmpty()) {
                            mapLink = normalizeNeverlandsMainLink("main.php?get_id=56&act=10&go=ret&vcode=" + retVcode);
                        } else if (mapLink.contains("go=inf")) {
                            mapLink = normalizeNeverlandsMainLink(mapLink.replace("go=inf", "go=ret"));
                        } else if (mapLink.endsWith("/main.php") || mapLink.endsWith("/main.php?")) {
                            mapLink = normalizeNeverlandsMainLink("main.php?get_id=56&act=10&go=ret");
                        }
                    }
                }
            }
        }

        if (mapLink == null || mapLink.isEmpty()) {
            return null;
        }
        return buildRedirectHtml("Навигатор: переход на карту", mapLink);
    }

    private static String mainPhpExtractMenuVcode(String html, String menuKey) {
        if (html == null || html.isEmpty() || menuKey == null || menuKey.isEmpty()) {
            return null;
        }
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\\[\\s*\"" + java.util.regex.Pattern.quote(menuKey) + "\"\\s*,\\s*\"[^\"]*\"\\s*,\\s*\"([^\"]+)\"",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(html);
            if (m.find()) {
                String vcode = m.group(1);
                if (vcode != null && !vcode.isEmpty()) {
                    return vcode;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String mainPhpFindFish(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String pattern = "[\"fis\",\"Рыбалка\",\"";
        int posPattern = html.indexOf(pattern);
        if (posPattern == -1) {
            return null;
        }
        posPattern += pattern.length();
        int posEnd = html.indexOf('"', posPattern);
        if (posEnd == -1) {
            return null;
        }
        String vcode = html.substring(posPattern, posEnd);
        if (vcode.isEmpty()) {
            return null;
        }
        String callFish = "Fish('" + vcode + "');";
        String patternViewMap = "view_map();";
        int posScript = html.indexOf(patternViewMap);
        if (posScript == -1) {
            return null;
        }
        posScript += patternViewMap.length();
        return html.substring(0, posScript) + callFish + html.substring(posScript);
    }
    /**
     * Порт `MainPhpIsPerc` из C# (`MainPhpDrink.cs`).
     */
    /**
     * Обновляет runtime-значение усталости (`AppVars.Tied`) из текущего HTML верхнего фрейма.
     */
    private static void mainPhpUpdateTied(String html) {
        Integer tiedValue = parseMainPhpTiedValue(html);
        if (tiedValue == null) {
            return;
        }
        int normalized = Math.max(0, Math.min(100, tiedValue));
        if (AppVars.Tied != normalized) {
            android.util.Log.d(TAG, "AUTO_FISH_TRACE tied update: old=" + AppVars.Tied + ", new=" + normalized);
        }
        AppVars.Tied = normalized;
        if (AppVars.AutoFishDrink && normalized <= 0) {
            AppVars.AutoFishDrink = false;
            android.util.Log.d(TAG, "AUTO_FISH_TRACE tied reached zero -> stop drink-to-zero mode");
        }
    }

    /**
     * Пытается извлечь процент усталости из блока "Усталость: ...".
     */
    private static Integer parseMainPhpTiedValue(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        try {
            Integer tiedFromHpmp = parseMainPhpTiedFromHpmp(html);
            if (tiedFromHpmp != null) {
                return tiedFromHpmp;
            }

            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?is)\u0423\u0441\u0442\u0430\u043b\u043e\u0441\u0442\u044c\\s*:</td><td[^>]*>.*?<b>\\s*(\\d{1,3})\\s*</b>")
                    .matcher(html);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            m = java.util.regex.Pattern
                    .compile("(?is)\u0423\u0441\u0442\u0430\u043b\u043e\u0441\u0442\u044c[^0-9]{0,80}(\\d{1,3})\\s*%")
                    .matcher(html);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            m = java.util.regex.Pattern
                    .compile("(?is)\u0423\u0441\u0442\u0430\u043b\u043e\u0441\u0442\u044c[^0-9]{0,80}(\\d{1,3})")
                    .matcher(html);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_FISH_TRACE tied parse failed", e);
        }
        return null;
    }

    private static Integer parseMainPhpTiedFromHpmp(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        try {
            java.util.regex.Matcher hpmpMatcher = java.util.regex.Pattern
                    .compile("(?is)\\bhpmp\\b\\s*=\\s*\\[\\s*[^\\]]*?,\\s*[^\\]]*?,\\s*[^\\]]*?,\\s*[^\\]]*?,\\s*(\\d{1,3})\\s*\\]")
                    .matcher(html);
            if (hpmpMatcher.find()) {
                int maxTire = Integer.parseInt(hpmpMatcher.group(1));
                return Math.max(0, Math.min(100, 100 - maxTire));
            }

            java.util.regex.Matcher maxTireMatcher = java.util.regex.Pattern
                    .compile("(?is)\\bmaxTire\\b\\s*[:=]\\s*(\\d{1,3})")
                    .matcher(html);
            if (maxTireMatcher.find()) {
                int maxTire = Integer.parseInt(maxTireMatcher.group(1));
                return Math.max(0, Math.min(100, 100 - maxTire));
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_FISH_TRACE hpmp tied parse failed", e);
        }
        return null;
    }

    /**
     * C# parity (`MainPhpFindDrink`): на карте вставляет вызов `Drink('<vcode>')` после `view_map();`.
     */
    private static String mainPhpFindDrink(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String pattern = "[\"dri\",\"Пить\",\"";
        int posPattern = html.indexOf(pattern);
        if (posPattern == -1) {
            return null;
        }
        posPattern += pattern.length();
        int posEnd = html.indexOf('"', posPattern);
        if (posEnd == -1) {
            return null;
        }
        String vcode = html.substring(posPattern, posEnd);
        if (vcode.isEmpty()) {
            return null;
        }
        String callDrink = "Drink('" + vcode + "');";
        String patternViewMap = "view_map();";
        int posScript = html.indexOf(patternViewMap);
        if (posScript == -1) {
            return null;
        }
        posScript += patternViewMap.length();
        return html.substring(0, posScript) + callDrink + html.substring(posScript);
    }

    /**
     * Шаг контроля усталости авто-рыбалки:
     * - если усталость выше порога, выполняет "Пить" (или "Эликсир Блаженства" по настройке),
     * - иначе возвращает null и цикл продолжает обычный шаг "Рыбалка".
     */
    private static String mainPhpAutoFishFatigueStep(String html) {
        if (AppVars.Profile == null || html == null || html.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (AppVars.AutoFishDrinkOnce) {
            long elapsed = now - lastAutoFishDrinkTriggerAtMs;
            if (elapsed >= 0 && elapsed < AUTO_FISH_DRINK_SERVER_COOLDOWN_MS) {
                long remainingMs = AUTO_FISH_DRINK_SERVER_COOLDOWN_MS - elapsed;
                android.util.Log.d(TAG, "AUTO_FISH_TRACE drink cooldown active: elapsedMs="
                        + elapsed + ", remainingMs=" + remainingMs);
                return buildAutoFishDrinkCooldownHtml(remainingMs);
            }
            android.util.Log.d(TAG, "AUTO_FISH_TRACE drink cooldown elapsed: elapsedMs="
                    + elapsed + ", recheck tied");
            AppVars.AutoFishDrinkOnce = false;
        }
        mainPhpUpdateTied(html);

        int tied = Math.max(0, AppVars.Tied);
        int tiedHigh = Math.max(0, Math.min(99, AppVars.Profile.FishTiedHigh));
        if (!AppVars.AutoFishDrink) {
            AppVars.AutoFishDrink = tied > tiedHigh && AppVars.Profile.FishTiedZero;
        }
        boolean needDrinkStep = tied > tiedHigh || AppVars.AutoFishDrink;
        if (!needDrinkStep) {
            return null;
        }

        if (AppVars.Profile.FishDrinkBliss) {
            if (AppVars.FastNeed) {
                android.util.Log.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
                        + ", wait bliss: FastNeed=true");
                return buildRedirectHtml("Авторыбалка: ожидание эликсира", "main.php");
            }
            long sinceLast = now - lastAutoFishBlazTriggerAtMs;
            if (sinceLast >= 0 && sinceLast < AUTO_FISH_BLAZ_TRIGGER_COOLDOWN_MS) {
                android.util.Log.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
                        + ", bliss cooldown " + sinceLast + "ms");
                return buildRedirectHtml("Авторыбалка: ожидание эликсира", "main.php");
            }
            lastAutoFishBlazTriggerAtMs = now;
            lastAutoFishDrinkTriggerAtMs = now;
            AppVars.AutoFishDrinkOnce = true;
            android.util.Log.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
                    + ", trigger bliss elixir");
            FastActionManager.fastAttackBlazElixir();
            return buildRedirectHtml("Авторыбалка: Эликсир Блаженства", "main.php");
        }

        String drinkHtml = mainPhpFindDrink(html);
        if (drinkHtml != null && !drinkHtml.isEmpty()) {
            lastAutoFishDrinkTriggerAtMs = now;
            AppVars.AutoFishDrinkOnce = true;
            android.util.Log.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
                    + ", inject Drink(vcode)");
            return drinkHtml;
        }

        String floraHtml = mainPhpFindFlora(html);
        if (floraHtml != null && !floraHtml.isEmpty()) {
            android.util.Log.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
                    + ", redirect to map for Drink");
            return floraHtml;
        }
        return null;
    }

    private static String mainPhpAutoDrinkBlazStep(String address, String html) {
        if (AppVars.Profile == null || html == null || html.isEmpty()) {
            return null;
        }
        if (!AppVars.Profile.DoAutoDrinkBlaz) {
            return null;
        }
        if (AppVars.IsFightCaptchaDialogVisible) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (AppVars.NeverTimer > 0L && now <= AppVars.NeverTimer) {
            return null;
        }

        int tied = Math.max(0, Math.min(100, AppVars.Tied));
        int tiedThreshold = Math.max(0, Math.min(100, AppVars.Profile.AutoDrinkBlazTied));
        if (tied < tiedThreshold) {
            return null;
        }

        if (AppVars.FastNeed) {
            // Если уже выполняется fast-action по блажу, не запускаем параллельный поток.
            if (BLISS_ELIXIR_NAME.equals(AppVars.FastId)) {
                return buildRedirectHtml(
                        "\u0410\u0432\u0442\u043E\u043F\u0438\u0442\u044C\u0435 \u0431\u043B\u0430\u0436\u0430: \u043E\u0436\u0438\u0434\u0430\u043D\u0438\u0435 \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u0438\u044F",
                        "main.php");
            }
            return null;
        }

        long sinceLastTrigger = now - lastAutoDrinkBlazTriggerAtMs;
        if (sinceLastTrigger >= 0L && sinceLastTrigger < 2500L) {
            return null;
        }

        lastAutoDrinkBlazTriggerAtMs = now;
        android.util.Log.d(TAG, "AUTO_BLAZ_TRACE trigger quick action: " + BLISS_ELIXIR_NAME
                + ", tied=" + tied + ", threshold=" + tiedThreshold);
        FastActionManager.fastAttackBlazElixir();
        return buildRedirectHtml(
                "\u0410\u0432\u0442\u043E\u043F\u0438\u0442\u044C\u0435 \u0431\u043B\u0430\u0436\u0430: " + BLISS_ELIXIR_NAME,
                "main.php");
    }

    /**
     * Генерирует "мягкое" ожидание завершения серверного таймаута после шага "Пить".
     * Не спамит повторными глотками и возвращает цикл на main.php после небольшой паузы.
     */
    private static String buildAutoFishDrinkCooldownHtml(long remainingMs) {
        long safeRemaining = Math.max(0L, remainingMs);
        int delayMs = (int) Math.max(900L, Math.min((long) AUTO_FISH_DRINK_POLL_DELAY_MS, safeRemaining));
        long seconds = (safeRemaining + 999L) / 1000L;
        return HtmlUtils.GENERATED_PAGE_MARKER
                + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                + "<title>ABClient</title></head><body>"
                + "Авторыбалка: глоток в работе, ожидание " + seconds + " с..."
                + "<script language=\"JavaScript\">"
                + "setTimeout(function(){ window.location = 'main.php'; }, " + delayMs + ");"
                + "</script></body></html>";
    }

    private static boolean mainPhpIsPerc(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("input type=button class=lbut value=\"умения\"");
    }
    /**
     * Порт `MainPhpArmedKinfe` из C# (`MainPhpWear.cs`).
     */
    private static boolean mainPhpArmedKnife(String html) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        return parsedDressed.IsWearKnife();
    }
    /**
     * Порт `MainPhpWearKnife` из C# (`MainPhpWear.cs`).
     */
    private static String mainPhpWearKnife(String html) {
        ParsedDressed dressed = new ParsedDressed(html);
        if (!dressed.Valid) {
            return null;
        }
        boolean isWear = dressed.IsWearKnife();
        if (!isWear) {
            List<WearInvEntry> invList = getWearInvList(html);
            String[] knives = ParsedDressed.getSkinKnifeNames();
            for (WearInvEntry thing : invList) {
                if (thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) {
                    continue;
                }
                for (String knife : knives) {
                    if (containsIgnoreCase(thing.name, knife)) {
                        android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpWearKnife: wear " + thing.name
                                + ", link=" + thing.wearLink);
                        return buildRedirectHtml("Одеваем " + thing.name, thing.wearLink);
                    }
                }
            }
        }
        AppVars.AutoSkinArmedKnife = false;
        return null;
    }
    /**
     * C# parity: проверка, нужно ли переодевать снасти авто-рыбалки.
     */
    private static boolean mainPhpIsMustWearUd(String html) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        boolean isWear1 = parsedDressed.IsWear1();
        if (!isWear1 && AppVars.Profile != null && AppVars.Profile.FishAutoWear) {
            return true;
        }
        boolean isWear2 = parsedDressed.IsWear2();
        return !isWear2 && AppVars.Profile != null && AppVars.Profile.FishAutoWear;
    }
    /**
     * C# parity: авто-надевание снастей в обе руки (`MainPhpWearUd`).
     */
    private static String mainPhpWearUd(String html) {
        ParsedDressed ud = new ParsedDressed(html);
        if (!ud.Valid || AppVars.Profile == null) {
            return null;
        }
        List<WearInvEntry> invList = getWearInvList(html);
        boolean isWear1 = ud.IsWear1();
        if (!isWear1 && AppVars.Profile.FishAutoWear) {
            for (WearInvEntry thing : invList) {
                if (thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) continue;
                if (containsIgnoreCase(AppVars.Profile.FishHandOne, "Любая удочка")) {
                    if (containsIgnoreCase(thing.name, "удочка") || containsIgnoreCase(thing.name, "спиннинг")) {
                        return buildRedirectHtml("Одеваем первую попавшуюся удочку", thing.wearLink);
                    }
                } else if (containsIgnoreCase(thing.name, AppVars.Profile.FishHandOne)) {
                    return buildRedirectHtml(AppVars.Profile.FishHandOne + " одевается", thing.wearLink);
                }
            }
            disableAutoFish("Не найден предмет для первой руки");
            return null;
        }
        boolean isWear2 = ud.IsWear2();
        if (!isWear2 && AppVars.Profile.FishAutoWear) {
            for (WearInvEntry thing : invList) {
                if (thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) continue;
                boolean matches;
                if (containsIgnoreCase(AppVars.Profile.FishHandTwo, "Любая удочка")) {
                    matches = containsIgnoreCase(thing.name, "удочка") || containsIgnoreCase(thing.name, "спиннинг");
                } else {
                    matches = containsIgnoreCase(thing.name, AppVars.Profile.FishHandTwo);
                }
                if (!matches) continue;
                if ((ud.Empty1 || ud.Empty2) || !ud.InRightSlot) {
                    return buildRedirectHtml("Одеваем снасть во вторую руку", thing.wearLink);
                }
                if (ud.Wid != null && !ud.Wid.isEmpty() && ud.Vcod != null && !ud.Vcod.isEmpty()) {
                    String removeLink = "main.php?get_id=57&uid=" + ud.Wid + "&s=0&vcode=" + ud.Vcod;
                    return buildRedirectHtml("Снимаем " + ud.Hand1, removeLink);
                }
            }
        }
        AppVars.AutoFishWearUd = false;
        return null;
    }
    /**
     * Формирует ключ текущего состояния проверки снастей авто-рыбалки.
     *
     * Назначение:
     * - нужен для anti-loop guard, чтобы различать повторение одной и той же
     *   ситуации `mustWear=true` от нормального прогресса.
     *
     * Зависимости:
     * - `AppVars.AutoFishHand1/2` (фактически распознанные надетые снасти);
     * - `AppVars.Profile.FishHandOne/FishHandTwo` (целевая конфигурация из настроек).
     */
    private static String buildAutoFishWearLoopKey() {
        String cfg1 = "";
        String cfg2 = "";
        if (AppVars.Profile != null) {
            cfg1 = AppVars.Profile.FishHandOne == null ? "" : AppVars.Profile.FishHandOne;
            cfg2 = AppVars.Profile.FishHandTwo == null ? "" : AppVars.Profile.FishHandTwo;
        }
        String h1 = AppVars.AutoFishHand1 == null ? "" : AppVars.AutoFishHand1;
        String h2 = AppVars.AutoFishHand2 == null ? "" : AppVars.AutoFishHand2;
        return cfg1 + "|" + cfg2 + "|" + h1 + "|" + h2;
    }
    /**
     * Обновляет счётчик повторов для anti-loop guard авто-рыбалки.
     *
     * Поведение:
     * - если ключ состояния не меняется в пределах окна `AUTO_FISH_WEAR_LOOP_WINDOW_MS`,
     *   увеличиваем счётчик повторов;
     * - при смене ключа или истечении окна — сбрасываем счётчик на 1.
     *
     * @return `true`, если достигнут лимит повторов и нужно аварийно останавливать авто-рыбалку.
     */
    private static boolean markAutoFishWearLoop(String stateKey) {
        long now = System.currentTimeMillis();
        boolean sameKey = stateKey.equals(AppVars.AutoFishWearLoopKey);
        boolean inWindow = AppVars.AutoFishWearLoopStamp > 0L
                && (now - AppVars.AutoFishWearLoopStamp) <= AUTO_FISH_WEAR_LOOP_WINDOW_MS;
        if (sameKey && inWindow) {
            AppVars.AutoFishWearLoopCount++;
        } else {
            AppVars.AutoFishWearLoopKey = stateKey;
            AppVars.AutoFishWearLoopCount = 1;
        }
        AppVars.AutoFishWearLoopStamp = now;
        return AppVars.AutoFishWearLoopCount >= AUTO_FISH_WEAR_LOOP_MAX_REPEATS;
    }
    /**
     * Сбрасывает anti-loop runtime-состояние авто-рыбалки.
     *
     * Вызывается:
     * - при успешной проверке `mustWear=false`;
     * - при ручном/аварийном выключении авто-рыбалки.
     */
    private static void resetAutoFishWearLoopGuard() {
        AppVars.AutoFishWearLoopKey = "";
        AppVars.AutoFishWearLoopCount = 0;
        AppVars.AutoFishWearLoopStamp = 0L;
    }
    private static void disableAutoFish(String reason) {
        resetAutoFishWearLoopGuard();
        android.util.Log.w(TAG, "AUTO_FISH_TRACE disable auto fish: " + reason);
        try {
            if (AppVars.getContext() != null) {
                AutoFunctionsManager.getInstance(AppVars.getContext()).setAutoFishEnabled(false);
            } else if (AppVars.Profile != null) {
                AppVars.Profile.AutoFish = false;
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_FISH_TRACE disable auto fish failed", e);
            if (AppVars.Profile != null) {
                AppVars.Profile.AutoFish = false;
            }
        }
        if (AppVars.getContext() != null) {
            Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
            msgIntent.putExtra("message", "<font color=#cc0000><b>Авто-рыбалка выключена: " + reason + "</b></font>");
            LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
        }
    }
    private static String extractInputValue(String html, String name) {
        if (html == null || html.isEmpty() || name == null || name.isEmpty()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("name\\s*=\\s*['\"]?" + java.util.regex.Pattern.quote(name) + "['\"]?\\s+value\\s*=\\s*['\"]?([^'\"\\s>]+)",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern
                .compile("value\\s*=\\s*['\"]?([^'\"\\s>]+)['\"]?\\s+name\\s*=\\s*['\"]?" + java.util.regex.Pattern.quote(name) + "['\"]?",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
        return m.find() ? m.group(1) : "";
    }
    private static int pickFishPrimId(String html) {
        if (AppVars.Profile == null) return -1;
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < FISH_PRIM_IDS.length; i++) {
            if ((AppVars.Profile.FishEnabledPrims & FISH_PRIM_FLAGS[i]) == 0) continue;
            String probe = "name=primid value=" + FISH_PRIM_IDS[i];
            if (html.toLowerCase(Locale.ROOT).contains(probe.toLowerCase(Locale.ROOT))) {
                candidates.add(FISH_PRIM_IDS[i]);
            }
        }
        if (candidates.isEmpty()) return -1;
        Collections.shuffle(candidates, RANDOM);
        return candidates.get(0);
    }
    /**
     * C# parity: подготовка шага рыбалки на странице выбора приманки (`MainPhpAutoFishPrepare`).
     */
    private static String mainPhpAutoFishPrepare(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (!lower.contains("name=primid") || !lower.contains("name=get_id")) {
            return null;
        }
        AppVars.CodeAddress = "";
        String codeAddress = extractCaptchaUrl(html);
        if (codeAddress != null && !codeAddress.isEmpty()) {
            AppVars.CodeAddress = codeAddress;
        }
        String getid = extractInputValue(html, "get_id");
        String act = extractInputValue(html, "act");
        String vcode = extractInputValue(html, "vcode");
        String lakeid = extractInputValue(html, "lakeid");
        if (getid.isEmpty() || act.isEmpty() || vcode.isEmpty() || lakeid.isEmpty()) {
            return null;
        }
        String massa = HelperStrings.subString(html, "<b>Масса Вашего инвентаря: ", "</b>");
        if (massa != null && !massa.isEmpty()) {
            AppVars.AutoFishMassa = massa;
        }
        int primid = pickFishPrimId(html);
        if (primid <= 0) {
            disableAutoFish("Нет доступной приманки по настройкам");
            return null;
        }
        AppVars.AutoFishLikeId = String.valueOf(primid);
        AppVars.AutoFishLikeVal = "";
        String marker = "name=primid value=" + primid;
        int markerPos = lower.indexOf(marker.toLowerCase(Locale.ROOT));
        if (markerPos >= 0) {
            int insertPos = markerPos + "name=primid value=".length() + String.valueOf(primid).length() + 1;
            if (insertPos > 0 && insertPos <= html.length() && !lower.contains(marker.toLowerCase(Locale.ROOT) + " checked")) {
                html = html.substring(0, insertPos) + "checked " + html.substring(insertPos);
            }
        }
        AppVars.FightLink = "http://neverlands.ru/main.php?get_id=" + getid
                + "&lakeid=" + lakeid
                + "&act=" + act
                + "&primid=" + primid
                + (AppVars.CodeAddress == null || AppVars.CodeAddress.isEmpty() ? "" : "&code=????")
                + "&vcode=" + vcode;
        android.util.Log.d(TAG, "AUTO_FISH_TRACE prepared FightLink=" + AppVars.FightLink
                + ", captcha=" + (AppVars.CodeAddress != null && !AppVars.CodeAddress.isEmpty())
                + ", primid=" + primid);
        return html;
    }
    /**
     * Проверка, надет ли целевой свиток режима осады (`Свиток Удар Ярости`/`Снежок`).
     */
    private static boolean mainPhpArmedFuryScroll(String html) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        return parsedDressed.IsWearFuryScroll();
    }
    /**
     * Поиск и надевание свитка режима осады в инвентаре (`wca=28`).
     *
     * Возвращает redirect-HTML на wear-link при успехе, иначе null.
     */
    private static String mainPhpWearFuryScroll(String html) {
        ParsedDressed dressed = new ParsedDressed(html);
        if (!dressed.Valid) {
            return null;
        }
        boolean isWear = dressed.IsWearFuryScroll();
        if (!isWear) {
            List<WearInvEntry> invList = getWearInvList(html);
            String[] scrollNames = ParsedDressed.getFuryScrollNames();
            for (WearInvEntry thing : invList) {
                if (thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) {
                    continue;
                }
                for (String scrollName : scrollNames) {
                    if (containsIgnoreCase(thing.name, scrollName)) {
                        android.util.Log.d(TAG, "AUTO_FURY_TRACE mainPhpWearFuryScroll: wear " + thing.name
                                + ", link=" + thing.wearLink);
                        return buildRedirectHtml("Одеваем " + thing.name, thing.wearLink);
                    }
                }
            }
        }
        AppVars.AutoFuryArmedScroll = false;
        return null;
    }
    /**
     * Порт `MainPhpGetSkinRes` из C# (`MainPhpWear.cs`).
     */
    private static void mainPhpGetSkinRes(String html) {
        final String patternStartRes = "<B>Рост</B></td></tr>";
        int pos = html.indexOf(patternStartRes);
        boolean anchorFound = pos != -1;
        if (!anchorFound) {
            // Fallback: на некоторых кадрах "Рост" может быть в другом шаблоне/регистре.
            // В этом случае пробуем парсить весь HTML по шаблону ресурсных строк.
            pos = 0;
        }
        StringBuilder sb = new StringBuilder();
        List<String> deltaForChat = new ArrayList<>();
        Map<String, Double> deltaForStatsKg = new LinkedHashMap<>();
        boolean baselineFill = AppVars.SkinRes.isEmpty();
        int parsedResources = 0;
        int diffResources = 0;
        if (anchorFound) {
            pos += patternStartRes.length();
        }
        while (true) {
            final String patternStartTr = "<input type=checkbox name=";
            pos = html.indexOf(patternStartTr, pos);
            if (pos == -1) {
                break;
            }
            pos += patternStartTr.length();
            final String patternEndTr = "</tr>";
            int posEnd = html.indexOf(patternEndTr, pos);
            if (posEnd == -1) {
                break;
            }
            posEnd += patternEndTr.length();
            String htmlEntry = html.substring(pos, posEnd);
            String valString = HelperStrings.subString(htmlEntry, " width=15% class=travma align=center>", "</td>");
            Double val = tryParseDoubleInvariant(valString);
            if (val != null) {
                String name = HelperStrings.subString(htmlEntry, " width=25% class=travma align=center><B>", "</B><BR>");
                if (name != null && !name.isEmpty()) {
                    parsedResources++;
                    if (AppVars.SkinRes.containsKey(name)) {
                        double oldVal = AppVars.SkinRes.get(name);
                        if (Math.abs(oldVal - val) > 0.009d) {
                            double diff = val - oldVal;
                            sb.append("<span style=\"color:#009933;font-weight:bold;\">«")
                                    .append(name).append(" ").append(String.format(Locale.US, "%.2f", val))
                                    .append("»</span> (+")
                                    .append(String.format(Locale.US, "%.2f", diff))
                                    .append(")");
                            AppVars.SkinRes.put(name, val);
                            if (diff > 0d) {
                                diffResources++;
                                deltaForChat.add(name + " (+" + String.format(Locale.US, "%.2f", diff) + " кг)");
                                Double existingDelta = deltaForStatsKg.get(name);
                                deltaForStatsKg.put(name, (existingDelta == null ? 0d : existingDelta) + diff);
                            }
                        } else {
                            sb.append("<span style=\"color:#009933;font-weight:bold;\">«")
                                    .append(name).append(" ").append(String.format(Locale.US, "%.2f", val))
                                    .append("»</span>");
                        }
                    } else {
                        sb.append("<span style=\"color:#009933;font-weight:bold;\">«")
                                .append(name).append(" ").append(String.format(Locale.US, "%.2f", val))
                                .append("»</span>");
                        AppVars.SkinRes.put(name, val);
                        if (!baselineFill && val > 0d) {
                            diffResources++;
                            deltaForChat.add(name + " (+" + String.format(Locale.US, "%.2f", val) + " кг)");
                            Double existingDelta = deltaForStatsKg.get(name);
                            deltaForStatsKg.put(name, (existingDelta == null ? 0d : existingDelta) + val);
                        }
                    }
                }
            }
            pos = posEnd;
        }
        // Вывод результата разделки в чат зависит от профильного флага `RazdChatReport`.
        //
        // Зависимости:
        // - `UserConfig.RazdChatReport` (load/save в XML профиля, C# parity);
        // - `SettingsActivity` / `root_preferences.xml` (чекбокс "Показывать результат разделки в чат");
        // - `ChatStats` обновляется отдельно и не зависит от этого флага.
        if (!deltaForChat.isEmpty() && AppVars.Profile != null && AppVars.Profile.RazdChatReport) {
            String message = buildServerChatTimeHtml()
                    + "<font color=#006600><b>Результат разделки:</b></font> "
                    + String.join(", ", deltaForChat);
            if (AppVars.getContext() != null) {
                Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
                intent.putExtra("message", message);
                LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
            }
        }
        if (!deltaForStatsKg.isEmpty()) {
            ru.neverlands.abclient.utils.ChatStats.addResourceDeltaKg(deltaForStatsKg);
        }
        android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpGetSkinRes: anchorFound=" + anchorFound
                + ", baselineFill=" + baselineFill
                + ", parsedResources=" + parsedResources
                + ", diffResources=" + diffResources
                + ", deltaMapSize=" + deltaForStatsKg.size());
    }
    /**
     * Порт `GetInvList` из C# (`MainPhpWear.cs`) для поиска `WearLink`.
     */
    private static List<WearInvEntry> getWearInvList(String html) {
        List<WearInvEntry> invList = new ArrayList<>();
        final String patternStartInv = "</b></font></td></tr>";
        int pos = html.indexOf(patternStartInv);
        if (pos == -1) {
            return invList;
        }
        pos += patternStartInv.length();
        while (true) {
            final String patternStartTr = "<tr><td bgcolor=#F5F5F5>";
            if (pos + patternStartTr.length() > html.length()
                    || !html.substring(pos, pos + patternStartTr.length()).startsWith(patternStartTr)) {
                break;
            }
            final String patternEndTrLong = "<td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td></tr></table></td></tr></table></td></tr>";
            int posEnd = html.indexOf(patternEndTrLong, pos);
            if (posEnd == -1) {
                final String patternEndTrShort = "<img src=http://image.neverlands.ru/1x1.gif width=1 height=5></td></tr></table></td></tr>";
                posEnd = html.indexOf(patternEndTrShort, pos);
                if (posEnd == -1) {
                    return invList;
                }
                posEnd += patternEndTrShort.length();
            } else {
                posEnd += patternEndTrLong.length();
            }
            String htmlEntry = html.substring(pos, posEnd);
            WearInvEntry entry = parseWearInvEntry(htmlEntry);
            if (entry != null) {
                invList.add(entry);
            }
            pos = posEnd;
        }
        return invList;
    }
    /**
     * Парсит одну запись предмета инвентаря для wear-логики.
     *
     * Зависимости:
     * - {@link HelperStrings#subString(String, String, String)} для извлечения имени и ссылки "Надеть".
     * - Локальный fallback-парсинг {@code location='...'} перед кнопкой value="Надеть".
     *
     * Назначение:
     * - Получить минимальный DTO ({@link WearInvEntry}) для автопоиска и экипировки ножа.
     */
    private static WearInvEntry parseWearInvEntry(String htmlEntry) {
        WearInvEntry entry = new WearInvEntry();
        entry.name = HelperStrings.subString(htmlEntry, "<font class=nickname><b> ", "</b>");
        String wearLink = HelperStrings.subString(
                htmlEntry,
                "<input type=button class=invbut onclick=\"location='",
                "'\" value=\"Надеть\">");
        if ((wearLink == null || wearLink.isEmpty()) && htmlEntry.contains("value=\"Надеть\"")) {
            int wearButtonPos = htmlEntry.indexOf("value=\"Надеть\"");
            int locationPos = htmlEntry.lastIndexOf("location='", wearButtonPos);
            if (locationPos != -1) {
                int start = locationPos + "location='".length();
                int end = htmlEntry.indexOf('\'', start);
                if (end > start) {
                    wearLink = htmlEntry.substring(start, end);
                }
            }
        }
        entry.wearLink = wearLink == null ? "" : wearLink;
        return entry;
    }
    /**
     * Делит JS-список значений по верхнеуровневым запятым.
     *
     * Зависимости:
     * - Используется парсерами массивов {@code fight_ty}/{@code fexp}, где элементы могут содержать
     *   вложенные скобки, массивы и строки с запятыми.
     *
     * Назначение:
     * - Избежать некорректного split по запятым внутри строк/вложенных структур.
     */
    private static List<String> splitJsTopLevelCsv(String source) {
        List<String> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        int depthSquare = 0;
        int depthRound = 0;
        char quote = 0;
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < source.length(); index++) {
            char currentChar = source.charAt(index);
            if (quote != 0) {
                current.append(currentChar);
                if (currentChar == quote && (index == 0 || source.charAt(index - 1) != '\\')) {
                    quote = 0;
                }
                continue;
            }
            if (currentChar == '\'' || currentChar == '"') {
                quote = currentChar;
                current.append(currentChar);
                continue;
            }
            if (currentChar == '[') {
                depthSquare++;
                current.append(currentChar);
                continue;
            }
            if (currentChar == ']') {
                depthSquare--;
                current.append(currentChar);
                continue;
            }
            if (currentChar == '(') {
                depthRound++;
                current.append(currentChar);
                continue;
            }
            if (currentChar == ')') {
                depthRound--;
                current.append(currentChar);
                continue;
            }
            if (currentChar == ',' && depthSquare == 0 && depthRound == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        result.add(current.toString().trim());
        return result;
    }
    /**
     * Нормализует JS-токен: trim + снятие внешних кавычек.
     *
     * Зависимости:
     * - Используется после {@link #splitJsTopLevelCsv(String)} для маркеров и параметров fight-flow.
     *
     * Назначение:
     * - Получить "чистое" строковое значение независимо от формата токена в исходном JS.
     */
    private static String trimJsToken(String token) {
        if (token == null) {
            return "";
        }
        String trimmed = token.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.trim();
    }
    /**
     * Центральный post-filter обработчик ответов {@code main.php}.
     *
     * Зависимости:
     * - {@link Russian#getString(byte[])} и {@link Russian#getBytes(String)} для конвертации кодировок.
     * - Ключевые ветви: бой ({@link #mainPhpFight(String, String)}), инвентарь ({@link #mainPhpInv(String)}),
     *   разделка ({@link #mainPhpRaz(String)}), fast-действия ({@link #processMainPhpFast(String, String)}).
     * - Глобальное состояние в {@link AppVars} (таймеры, флаги автобоя, ссылки, статистика).
     * - Бродкасты в UI через {@link LocalBroadcastManager} и {@code AppVars.ACTION_*}.
     *
     * Назначение:
     * - Единая точка маршрутизации и постобработки HTML main.php с сохранением совместимости с C#-логикой.
     */
    public static byte[] process(String address, byte[] array) {
        android.util.Log.d(TAG, "process() called for " + address + ", bytes=" + (array != null ? array.length : 0));
        // Сохраняем исходный ответ, если он нужен где-то еще
        AppVars.lastMainPhpResponse = array;
        AppVars.IdleTimer = System.currentTimeMillis();
        AppVars.LastMainPhp = System.currentTimeMillis();
        AppVars.ContentMainPhp = null;
        String html = Russian.getString(array);
        String originalHtml = html;
        android.util.Log.d(TAG, "HTML length after getString: " + html.length());
        android.util.Log.d(TAG, "HTML first 200: " + html.substring(0, Math.min(200, html.length())));
        html = Filter.removeDoctype(html);
        // Порт C# MainPhpInsHp.cs:
        // обновляем интервалы восстановления HP/MA из `ins_HP(...)` до основной логики,
        // чтобы расчёт `Restoring` выполнялся по актуальным `hp_int/ma_int`.
        mainPhpInsHp(html);
        // Обновляем runtime-усталость на каждом ответе main.php.
        mainPhpUpdateTied(html);
        // Извлечение vcode - полезная логика из новой версии
        String vcode = HelperStrings.subString(html, "'main.php?get_id=56&act=10&go=inf&vcode=", "'");
        if (vcode != null) {
            AppVars.VCode = vcode;
        }
        // Системное сообщение (аналог MainPhp.cs строки 207-223).
        // Паттерн: <font class=nickname><font color=#cc0000><b>ТЕКСТ<br><br></b></font></font>
        // Диагностика: ищем cc0000 в HTML чтобы понять реальный паттерн сервера
        if (address.contains("get_id=43")) {
            int diagIdx = html.toLowerCase().indexOf("cc0000");
            if (diagIdx >= 0) {
                int start = Math.max(0, diagIdx - 80);
                int end = Math.min(html.length(), diagIdx + 200);
                android.util.Log.d(TAG, "process: get_id=43 cc0000 context: " + html.substring(start, end));
            } else {
                android.util.Log.d(TAG, "process: get_id=43 — cc0000 не найден. HTML[0:300]=" + html.substring(0, Math.min(300, html.length())));
            }
        }
        String sysMessage = HelperStrings.subString(html,
                "<font class=nickname><font color=#cc0000><b>",
                "<br><br></b></font></font>");
        // Fallback: попробуем без учёта регистра через поиск lower-case копии
        if (sysMessage == null || sysMessage.isEmpty()) {
            sysMessage = HelperStrings.subString(html.toLowerCase(),
                    "<font class=nickname><font color=#cc0000><b>",
                    "<br><br></b></font></font>");
            if (sysMessage != null && !sysMessage.isEmpty()) {
                // Найдено в lowercase — берём кусок из оригинала
                int idx = html.toLowerCase().indexOf("<font class=nickname><font color=#cc0000><b>");
                if (idx >= 0) {
                    int eIdx = html.toLowerCase().indexOf("<br><br></b></font></font>", idx);
                    if (eIdx >= 0) {
                        sysMessage = html.substring(idx + "<font class=nickname><font color=#cc0000><b>".length(), eIdx);
                    }
                }
            }
        }
        if (sysMessage != null && !sysMessage.isEmpty() && AppVars.getContext() != null) {
            android.util.Log.d(TAG, "process: sysMessage=" + sysMessage);
            Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
            msgIntent.putExtra("message", "<font color=#cc0000><b>" + sysMessage + "</b></font>");
            LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
        }
        // Аналог C# MainPhp.cs: если авто-нападение уткнулось в закрытый бой,
        // добавляем цель во временный blacklist и отменяем fast-цикл.
        // Зависимости:
        // - `RoomManager.charAddToBlackList(...)` (защита от мгновенного повторного нападения),
        // - `FastActionManager.fastCancel()` (сброс текущего fast-состояния),
        // - `AppVars.FastNick` (цель текущего fast-действия).
        String htmlLower = html.toLowerCase(Locale.ROOT);
        if (htmlLower.contains("id=wtime")
                || htmlLower.contains("id=\"wtime\"")
                || htmlLower.contains("id='wtime'")
                || htmlLower.contains("id=tdsec")
                || htmlLower.contains("id=\"tdsec\"")
                || htmlLower.contains("id='tdsec'")
                || htmlLower.contains("time_left_sec")
                || htmlLower.contains("secgo")) {
            syncNeverTimerFromWtime(html, address);
        }
        boolean isFightFrame = html.contains("magic_slots();");
        boolean isFightTopFrame = html.contains("var fight_ty");
        boolean isFightFinishAddress = address != null && address.contains("get_id=61") && address.contains("act=7");
        boolean isFightFinishAddressForInv = address != null
                && address.contains("get_id=61")
                && (address.contains("act=7") || address.contains("act=5"));
        // Важно: fallback-публикацию итога боя для act=7 делаем РАНЬШЕ AutoSkin/FastAction/AutoFish-веток.
        // Иначе ранний return (например, редирект в инвентарь после боя) срежет системную сводку
        // "Бой против ... завершен ... Нанесено ... Получено опыта ...".
        //
        // Зависимости:
        // - registerFightEndByLogId(...): дедуп учёта боя в статистике;
        // - publishFightResultFromLogsIfNeeded(...): "Победа/добыча";
        // - publishFightSummaryFromFinishHtmlIfNeeded(...): fallback через единый ChatFilter-пайплайн.
        if (isFightFinishAddress) {
            registerFightEndByLogId(AppVars.LastBoiLog, "fight_finish_url_early");
            publishFightResultFromLogsIfNeeded(html, address, AppVars.LastBoiLog);
            publishFightSummaryFromFinishHtmlIfNeeded(html, address, AppVars.LastBoiLog);
        }
        maybeMarkAutoSkinKnifeRecheck();
        boolean closedFightInterfereError = htmlLower.contains("ошибка при использовании. нельзя вмешаться в закрытый бой");
        if (closedFightInterfereError && AppVars.AutoAttackToolId != 0 && AppVars.FastNick != null && !AppVars.FastNick.isEmpty()) {
            String blockedNick = AppVars.FastNick;
            android.util.Log.d(TAG, "[AA_TRACE] closed fight error: add to blacklist and cancel fast, nick=" + blockedNick
                    + ", fastId=" + AppVars.FastId + ", autoTool=" + AppVars.AutoAttackToolId);
            RoomManager.charAddToBlackList(blockedNick);
            if (AppVars.getContext() != null) {
                Intent blockedMsgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
                blockedMsgIntent.putExtra("message", "<b>" + blockedNick + "</b> в бою, отменяем действие!");
                LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(blockedMsgIntent);
            }
            FastActionManager.fastCancel("closed-fight-interfere-error");
        }
        // Проверка автопитья после получения верхнего фрейма персонажа.
        // При совпадении условий запускает единый fast-action "Эликсир Восстановления".
        tryTriggerAutoDrinkRestoreElixir(address, html, isFightFrame, isFightTopFrame);
        // Обработка быстрых действий (портировано из MainPhp.cs строки 1429-1619)
        // В C# FastAction обрабатывается ВНУТРИ MainPhp, а не в отдельном менеджере.
        // Алгоритм: MainPhpFindInv → BuildRedirect на инвентарь → MainPhpIsInv → MainPhpFast → BuildRedirect на категорию
        if (AppVars.FastNeed) {
            byte[] fastResult = processMainPhpFast(address, html);
            if (fastResult != null) {
                return fastResult;
            }
        }
        // Чтение умения "Охота" (C# parity) до оркестрации AutoSkin,
        // чтобы `AutoSkinCheckUm` корректно сбрасывался на `mselect=1`.
        if (!isNonCombatAutoPausedByFastAction()) {
            mainPhpProcessSkills(html, address);
        }
        // Чтение умения "Рыбалка" (C# parity) до оркестрации AutoFish.
        if (!isNonCombatAutoPausedByFastAction()) {
            mainPhpProcessFishSkills(html, address);
        }

        // C# parity: DoAutoDrinkBlaz.
        // Порядок вызова: до AutoFish/AutoSearchBox, чтобы при высокой усталости сначала обнулить ее.
        if (!isNonCombatAutoPausedByFastAction() && !isFightFrame && !isFightTopFrame) {
            String autoDrinkBlazHtml = mainPhpAutoDrinkBlazStep(address, html);
            if (autoDrinkBlazHtml != null && !autoDrinkBlazHtml.isEmpty()) {
                return Russian.getBytes(autoDrinkBlazHtml);
            }
        }
        // Оркестрация Авто-Рыбалки (C# MainPhp.cs + MainPhpWear.cs + MainPhpFish.cs):
        // 1) при необходимости читаем умение Рыбалка (mselect=1);
        // 2) проверяем/переодеваем снасти в обеих руках;
        // 3) на странице рыбалки выбираем приманку и формируем FightLink.
        boolean autoFightReloadProbeAddress = isAutoFightReloadProbeAddress(address);
        if (autoFightReloadProbeAddress && isAutoFishEnabledByPreference()) {
            android.util.Log.d(TAG, "AUTO_FISH_TRACE skip: auto-fight reload probe address=" + address);
        }
        if (!isNonCombatAutoPausedByFastAction() && !isFightFrame && !isFightTopFrame
                && !autoFightReloadProbeAddress && isAutoFishEnabledByPreference()) {
            long nowMs = System.currentTimeMillis();
            if (AppVars.NeverTimer <= 0L || nowMs > AppVars.NeverTimer) {
                String fishFatigueHtml = mainPhpAutoFishFatigueStep(html);
                if (fishFatigueHtml != null && !fishFatigueHtml.isEmpty()) {
                    android.util.Log.d(TAG, "AUTO_FISH_TRACE fatigue step executed");
                    return Russian.getBytes(fishFatigueHtml);
                }
                if (AppVars.AutoFishCheckUm) {
                    String phtml = mainPhpFindPerc(html);
                    if (phtml != null && !phtml.isEmpty()) {
                        android.util.Log.d(TAG, "AUTO_FISH_TRACE redirect to character page for skill check");
                        return Russian.getBytes(phtml);
                    }
                    if (html.toLowerCase(Locale.ROOT).contains("<input type=button class=lbut value=\"умения\" onclick")) {
                        android.util.Log.d(TAG, "AUTO_FISH_TRACE redirect to skills page mselect=1");
                        return Russian.getBytes(buildRedirectHtml("Переключение на умения персонажа", "main.php?mselect=1"));
                    }
                }
                if (AppVars.AutoFishCheckUd) {
                    String perchtml = mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        android.util.Log.d(TAG, "AUTO_FISH_TRACE redirect to character page for fishing gear check");
                        return Russian.getBytes(perchtml);
                    }
                    AppVars.AutoFishWearUd = false;
                    if (mainPhpIsPerc(html)) {
                        AppVars.AutoFishWearUd = mainPhpIsMustWearUd(html);
                        AppVars.AutoFishCheckUd = false;
                        if (AppVars.AutoFishWearUd) {
                            String loopKey = buildAutoFishWearLoopKey();
                            if (markAutoFishWearLoop(loopKey)) {
                                disableAutoFish("Зацикливание проверки/надевания снастей");
                                AppVars.AutoFishWearUd = false;
                            }
                        } else {
                            resetAutoFishWearLoopGuard();
                        }
                        android.util.Log.d(TAG, "AUTO_FISH_TRACE gear check result: mustWear=" + AppVars.AutoFishWearUd);
                    }
                }
                if (AppVars.AutoFishWearUd) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=4", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        android.util.Log.d(TAG, "AUTO_FISH_TRACE redirect to inventory for fishing gear (&im=0&wca=4)");
                        return Russian.getBytes(invHtml);
                    }
                    if (mainPhpIsInv(html) || isInventoryAddress(address)) {
                        invHtml = mainPhpWearUd(html);
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=4")) {
                                android.util.Log.d(TAG, "AUTO_FISH_TRACE switch to items tab for fishing gear search");
                                return Russian.getBytes(buildRedirectHtml("Переключение на вещи", "main.php?im=0&wca=4"));
                            }
                        } else {
                            AppVars.AutoFishCheckUd = true;
                            return Russian.getBytes(invHtml);
                        }
                    }
                }
                // C# parity (`MainPhpFindFlora`): если мы не на карте и есть кнопка "Вернуться",
                // автоматически возвращаемся на природу перед поиском кнопки "Рыбалка".
                String floraHtml = mainPhpFindFlora(html);
                if (floraHtml != null && !floraHtml.isEmpty()) {
                    android.util.Log.d(TAG, "AUTO_FISH_TRACE redirect to nature/map via return button");
                    return Russian.getBytes(floraHtml);
                }
                // C# parity: на карте автоматически нажимаем "Рыбалка", чтобы открыть форму выбора приманки.
                String fishMapHtml = mainPhpFindFish(html);
                if (fishMapHtml != null && !fishMapHtml.isEmpty()) {
                    android.util.Log.d(TAG, "AUTO_FISH_TRACE inject Fish(vcode) into map frame");
                    return Russian.getBytes(fishMapHtml);
                }
                String fishPreparedHtml = mainPhpAutoFishPrepare(html);
                if (fishPreparedHtml != null) {
                    html = fishPreparedHtml;
                    boolean hasCaptcha = AppVars.CodeAddress != null && !AppVars.CodeAddress.isEmpty();
                    boolean isFishActionAddress = address != null
                            && address.contains("get_id=55")
                            && address.contains("act=4");
                    if (hasCaptcha && AppVars.FightLink != null && !AppVars.FightLink.isEmpty() && !isFishActionAddress) {
                        android.util.Log.d(TAG, "AUTO_FISH_TRACE captcha required, show dialog for fish action");
                        showFishCaptchaDialogOnce(AppVars.CodeAddress, AppVars.FightLink);
                        return Russian.getBytes(buildCaptchaDialogHoldHtml());
                    }
                    if (hasCaptcha && AppVars.IsFightCaptchaDialogVisible) {
                        android.util.Log.d(TAG, "AUTO_FISH_TRACE captcha dialog is visible, keep hold page");
                        return Russian.getBytes(buildCaptchaDialogHoldHtml());
                    }
                    if (!hasCaptcha && AppVars.FightLink != null && !AppVars.FightLink.isEmpty() && !isFishActionAddress) {
                        android.util.Log.d(TAG, "AUTO_FISH_TRACE redirect to fish action: " + AppVars.FightLink);
                        return Russian.getBytes(buildRedirectHtml("Авторыбалка: заброс", AppVars.FightLink));
                    }
                }
            }
        }
        // Оркестрация режима "Снежок/Ярость" (buttonFury из C#) + авто-надевание свитка:
        // 1) проверка надетого свитка на странице персонажа;
        // 2) авто-переход в инвентарь свитков (`im=0&wca=28`);
        // 3) авто-нажатие wear-link (`get_id=57&uid=...&s=1&vcode=...`).
        if (!isNonCombatAutoPausedByFastAction() && !isFightFrame && !isFightTopFrame && isAutoFuryEnabledByPreference()) {
            long nowMs = System.currentTimeMillis();
            if (AppVars.NeverTimer <= 0L || nowMs > AppVars.NeverTimer) {
                if (AppVars.AutoFuryCheckScroll) {
                    String perchtml = mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        android.util.Log.d(TAG, "AUTO_FURY_TRACE redirect to character page for scroll check");
                        return Russian.getBytes(perchtml);
                    }
                    AppVars.AutoFuryArmedScroll = false;
                    if (mainPhpIsPerc(html)) {
                        AppVars.AutoFuryArmedScroll = mainPhpArmedFuryScroll(html);
                        AppVars.AutoFuryCheckScroll = false;
                        android.util.Log.d(TAG, "AUTO_FURY_TRACE scroll check result: armed=" + AppVars.AutoFuryArmedScroll
                                + ", hand=" + AppVars.AutoFuryHand);
                    }
                }
                if (!AppVars.AutoFuryArmedScroll) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=28", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        android.util.Log.d(TAG, "AUTO_FURY_TRACE redirect to scroll inventory (&im=0&wca=28)");
                        return Russian.getBytes(invHtml);
                    }
                    if (mainPhpIsInv(html) || isInventoryAddress(address)) {
                        invHtml = mainPhpWearFuryScroll(html);
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=28")) {
                                android.util.Log.d(TAG, "AUTO_FURY_TRACE switch to scroll category (wca=28)");
                                return Russian.getBytes(buildRedirectHtml("Переходим к свиткам", "main.php?im=0&wca=28"));
                            }
                        } else {
                            AppVars.AutoFuryCheckScroll = true;
                            return Russian.getBytes(invHtml);
                        }
                    }
                }
            }
        }
        // Авто-разделка (MainPhpRaz.cs): если в текущем боевом кадре доступна кнопка "Разделать",
        // выполняем редирект на действие разделки до стандартной боевой обработки.
        if (!isNonCombatAutoPausedByFastAction() && isAutoSkinEnabledByPreference()) {
            String razHtml = mainPhpRaz(html);
            if (razHtml != null) {
                return Russian.getBytes(razHtml);
            }
        }
        // Оркестрация AutoSkin из C# MainPhp.cs (MainPhpWear.cs + TInvUd.cs):
        // 1) проверка/чтение умения "Охота";
        // 2) считывание охотничьих ресурсов;
        // 3) проверка надетого ножа;
        // 4) авто-надевание ножа через инвентарь.
        boolean suspendAutoSkinForFinishFlow = isFightFinishAddressForInv;
        boolean suspendAutoSkinForInventoryReload = isLikelyInventoryReloadSnapshot(address, html);
        boolean suspendAutoSkinForGeneratedTransition = isGeneratedTransitionPage(address, html);
        if (suspendAutoSkinForFinishFlow || suspendAutoSkinForInventoryReload || suspendAutoSkinForGeneratedTransition) {
            android.util.Log.d(TAG, "AUTO_SKIN_TRACE suspended: finishFlow=" + suspendAutoSkinForFinishFlow
                    + ", inventoryReload=" + suspendAutoSkinForInventoryReload
                    + ", generatedTransition=" + suspendAutoSkinForGeneratedTransition
                    + ", address=" + address);
        }
        if (!isNonCombatAutoPausedByFastAction() && !isFightFrame && !isFightTopFrame && isAutoSkinEnabledByPreference()
                && !suspendAutoSkinForFinishFlow
                && !suspendAutoSkinForInventoryReload
                && !suspendAutoSkinForGeneratedTransition) {
            long nowMs = System.currentTimeMillis();
            if (AppVars.NeverTimer <= 0L || nowMs > AppVars.NeverTimer) {
                if (AppVars.AutoSkinCheckUm) {
                    String phtml = mainPhpFindPerc(html);
                    if (phtml != null && !phtml.isEmpty()) {
                        android.util.Log.d(TAG, "AUTO_SKIN_TRACE redirect to character page for skill check");
                        return Russian.getBytes(phtml);
                    }
                    if (html.toLowerCase(Locale.ROOT).contains("<input type=button class=lbut value=\"умения\" onclick")) {
                        android.util.Log.d(TAG, "AUTO_SKIN_TRACE redirect to skills page mselect=1");
                        return Russian.getBytes(buildRedirectHtml("Переключение на умения персонажа", "main.php?mselect=1"));
                    }
                }
                if (AppVars.AutoSkinCheckRes) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=5", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        android.util.Log.d(TAG, "AUTO_SKIN_TRACE redirect to resources inventory (&im=5)");
                        return Russian.getBytes(invHtml);
                    }
                    if (mainPhpIsInv(html) || inventoryAddressMatchesFilter(address, "&im=5")) {
                        AppVars.AutoSkinCheckRes = false;
                        android.util.Log.d(TAG, "AUTO_SKIN_TRACE read skin resources");
                        mainPhpGetSkinRes(html);
                    }
                }
                if (AppVars.AutoSkinCheckKnife) {
                    String perchtml = mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        android.util.Log.d(TAG, "AUTO_SKIN_TRACE redirect to character page for knife check");
                        return Russian.getBytes(perchtml);
                    }
                    AppVars.AutoSkinArmedKnife = false;
                    if (mainPhpIsPerc(html)) {
                        AppVars.AutoSkinArmedKnife = mainPhpArmedKnife(html);
                        AppVars.AutoSkinCheckKnife = false;
                        android.util.Log.d(TAG, "AUTO_SKIN_TRACE knife check result: armed=" + AppVars.AutoSkinArmedKnife);
                    }
                }
                if (!AppVars.AutoSkinArmedKnife) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=4", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        android.util.Log.d(TAG, "AUTO_SKIN_TRACE redirect to items inventory (&im=0&wca=4)");
                        return Russian.getBytes(invHtml);
                    }
                    if (mainPhpIsInv(html) || isInventoryAddress(address)) {
                        invHtml = mainPhpWearKnife(html);
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=4")) {
                                android.util.Log.d(TAG, "AUTO_SKIN_TRACE switch to items tab for knife search");
                                return Russian.getBytes(buildRedirectHtml("Переключение на вещи", "main.php?im=0&wca=4"));
                            }
                        } else {
                            AppVars.AutoSkinCheckKnife = true;
                            return Russian.getBytes(invHtml);
                        }
                    }
                }
            }
        }
        // Обработка страницы боя
        // magic_slots() — признак страницы боя (fight frame)
        // var fight_ty — признак верхнего фрейма с данными о противнике
        if (isFightFrame || isFightTopFrame) {
            android.util.Log.d(TAG, "=== FIGHT FRAME DETECTED ==="
                    + " isFightFrame=" + isFightFrame
                    + " isFightTopFrame=" + isFightTopFrame
                    + " address=" + address);
            html = mainPhpFight(address, html);
            if (html == null) {
                android.util.Log.w(TAG, "process: mainPhpFight returned null, fallback to original HTML");
                html = originalHtml;
            }
            // Preserve original fight HTML for manual mode (avoid losing images after auto frame)
            AppVars.ContentMainPhp = originalHtml;
        }
        if (!isFightFrame && !isFightTopFrame) {
            maybeStopAutoTreasureOnDig(html);
        }
        // Обработка инвентаря выполняется ТОЛЬКО на странице инвентаря.
        // Важно: не запускать на страницах боя (`act=7`) и прочих `main.php`,
        // иначе можно сломать finish-flow и схлопнуть HTML по чужим шаблонам.
        boolean finishResponseStillFight = isFightFinishAddressForInv && isFightFrameHtml(html);
        boolean invByTemplate = mainPhpIsInv(html);
        boolean invByRows = hasInventoryRows(html);
        boolean invByAddress = isInventoryAddress(address);
        if (!finishResponseStillFight
                && (invByTemplate || invByRows || invByAddress)) {
            if (!invByTemplate && !invByAddress && invByRows) {
                android.util.Log.d(TAG, "INV_TRACE structural fallback matched: address=" + address);
            }
            html = mainPhpInv(html);
        }

        // C# parity (`DoSearchBox && !AutoMoving && DateTime.Now > NeverTimer`):
        // запускаем обход карты в поиске следующей "непосещенной" клетки.
        if (!isNonCombatAutoPausedByFastAction() && !isFightFrame && !isFightTopFrame && AppVars.DoSearchBox && !AppVars.AutoMoving) {
            long nowMs = System.currentTimeMillis();
            if (AppVars.NeverTimer <= 0L || nowMs > AppVars.NeverTimer) {
                String nextDest = MapAjax.findNextDestForBox(AppVars.Profile != null ? AppVars.Profile.MapLocation : null);
                if (nextDest != null && !nextDest.isEmpty()) {
                    startAutoSearchBoxMoving(nextDest);
                    android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE start moving to " + nextDest
                            + ", address=" + address);
                }
            }
        }

        if (!isNonCombatAutoPausedByFastAction() && AppVars.AutoMoving && html.contains(" id=wtime>")) {
            html = mainPhpWtime(html);
            AppVars.ContentMainPhp = html;
            return Russian.getBytes(html);
        }
        if (!isNonCombatAutoPausedByFastAction() && AppVars.AutoMoving) {
            String cityNavHtml = MainPhpCityNavigation.process(html);
            if (cityNavHtml != null && !cityNavHtml.isEmpty()) {
                return Russian.getBytes(cityNavHtml);
            }
            if (html.contains("var telep = ")) {
                String telepHtml = TeleportAjax.process(html);
                if (telepHtml != null && !telepHtml.isEmpty()) {
                    return Russian.getBytes(telepHtml);
                }
            }
            if (!html.contains("var map = [[")) {
                String mapReturnHtml = mainPhpFindMapReturnForAutoMoving(html);
                if (mapReturnHtml != null && !mapReturnHtml.isEmpty()) {
                    android.util.Log.d(TAG, "AUTO_MOVING_TRACE: redirect to map from " + address);
                    return Russian.getBytes(mapReturnHtml);
                }
                if (address != null && address.contains("ab_nav_bootstrap=1")) {
                    String bootstrapRetLink = "main.php?get_id=56&act=10&go=ret";
                    if (AppVars.VCode != null && !AppVars.VCode.trim().isEmpty()) {
                        bootstrapRetLink += "&vcode=" + AppVars.VCode.trim();
                    }
                    android.util.Log.d(TAG, "AUTO_MOVING_TRACE: bootstrap fallback to map, address="
                            + address + ", link=" + bootstrapRetLink);
                    return Russian.getBytes(buildRedirectHtml("Navigator bootstrap: go=ret", bootstrapRetLink));
                }
            }
        }
        if (html.contains("var map = [[")) {
            html = MapAjax.process(html);
        }
        if (!(isFightFrame || isFightTopFrame)) {
            AppVars.ContentMainPhp = html;
        }
        byte[] result = Russian.getBytes(html);
        android.util.Log.d(TAG, "process() returning " + result.length + " bytes for " + address);
        android.util.Log.d(TAG, "Result first 200: " + html.substring(0, Math.min(200, html.length())));
        return result;
    }
    /**
     * Обработка FastAction внутри MainPhp (аналог C# MainPhp.cs строки 1429-1619).
     *
     * Алгоритм C#:
     * 1. Определяем нужную категорию (wca=28 для свитков, wca=27 для зелий)
     * 2. Если мы НЕ на инвентаре — MainPhpFindInv → BuildRedirect на инвентарь с фильтром
     * 3. Если мы НА инвентаре — MainPhpFast → ищем предмет → авто-submit
     * 4. Если предмет не найден и мы не на нужной вкладке — BuildRedirect на вкладку
     * 5. Если мы на нужной вкладке и предмет не найден — отмена
     *
     * @return byte[] с результатом (HTML redirect или форма), или null если FastAction не обработан
     */
    private static byte[] processMainPhpFast(String address, String html) {
        if (!AppVars.FastNeed || AppVars.FastId == null) return null;
        String fastId = AppVars.FastId;
        android.util.Log.d(TAG, "processMainPhpFast: FastId=" + fastId + ", address=" + address);
        // NeverTimer — cooldown (аналог DateTime.Now > AppVars.NeverTimer в C#)
        if (AppVars.NeverTimer > 0 && System.currentTimeMillis() < AppVars.NeverTimer) {
            android.util.Log.d(TAG, "processMainPhpFast: NeverTimer ещё не истёк, пропускаем");
            return null;
        }
        // --- Особый случай: get_id=43 — это страница применения эликсира/предмета.
        // Сервер уже применил действие (по GET-запросу), поэтому FastNeed нужно сбросить.
        // Иначе мы будем бесконечно перезапускать процесс.
        if (address.contains("get_id=43")) {
            android.util.Log.d(TAG, "processMainPhpFast: get_id=43 — действие уже выполнено, сбрасываем FastNeed");
            FastActionManager.fastCancel("fast-get_id=43-action-already-applied");
            return null;
        }
        // Если fast-атака уже привела нас в бой (fight frame), дальнейший поиск инвентаря
        // становится бессмысленным и только мешает автобою.
        // Сценарий:
        // 1) Авто-нападение стартует из комнаты -> FastNeed=true.
        // 2) Сервер переводит в fight.frame.
        // 3) Мы продолжаем крутить processMainPhpFast на каждом обновлении боя.
        // В C# после входа в бой fast-цикл для нападалки фактически завершён.
        if (isFightFrameHtml(html)
                && address.contains("get_id=56&act=10&go=inf")
                && isAttackFastId(fastId)) {
            android.util.Log.d(TAG, "processMainPhpFast: вошли в бой с FastId=" + fastId
                    + ", сбрасываем FastNeed чтобы не блокировать авто-удары");
            FastActionManager.fastCancel("entered-fight-frame-attack-fastid");
            return null;
        }
        // Определяем нужный фильтр категории
        String filter = getInventoryFilter(fastId);
        if (filter == null) {
            android.util.Log.w(TAG, "processMainPhpFast: неизвестный FastId=" + fastId);
            return null;
        }
        android.util.Log.d(TAG, "processMainPhpFast: filter=" + filter
                + ", isInv=" + mainPhpIsInv(html)
                + ", isInvByAddress=" + isInventoryAddress(address)
                + ", w28_form=" + html.contains("w28_form(")
                + ", magicreform=" + html.contains("magicreform("));
        // --- Особый случай: Тотем НЕ требует инвентаря ---
        // В C# тотем ищет ["fig","Напасть","vcode"] на основной странице.
        // mainPhpFindFlora делает redirect на основную страницу, если нужно.
        if ("TOTEM".equals(filter)) {
            android.util.Log.d(TAG, "processMainPhpFast: тотем — без навигации на инвентарь");
            String fastHtml = FastActionManager.processMainPhp(html);
            if (fastHtml != null) {
                android.util.Log.d(TAG, "processMainPhpFast: УСПЕХ, тотем найден");
                return Russian.getBytes(fastHtml);
            }
            android.util.Log.w(TAG, "processMainPhpFast: тотем не найден, отмена");
            FastActionManager.fastCancel("inventory-fast-item-not-found");
            return null;
        }
        // 1. Если мы НЕ на инвентаре — ищем ссылку на инвентарь с фильтром
        String invRedirect = mainPhpFindInvWithFallback(html, filter, address);
        if (invRedirect != null) {
            android.util.Log.d(TAG, "processMainPhpFast: redirect на инвентарь: " + invRedirect);
            return Russian.getBytes(invRedirect);
        }
        // 2. Если мы НА инвентаре — проверяем категорию и ищем предмет
        if (mainPhpIsInv(html) || isInventoryAddress(address)) {
            String filterClean = filter.startsWith("&") ? filter.substring(1) : filter;
            // 2a. Сначала проверяем, на правильной ли мы вкладке категории.
            // Если address не содержит нужный фильтр (wca=28/wca=27),
            // перенаправляем на нужную категорию ПЕРЕД поиском предмета.
            // Это критично при 500+ предметах в инвентаре — поиск по всему
            // HTML (695KB) вместо отфильтрованной страницы (28KB) слишком медленный.
            if (!inventoryAddressMatchesFilter(address, filter)) {
                android.util.Log.d(TAG, "processMainPhpFast: на инвентаре, но не на нужной категории ("
                        + filterClean + "), переключаем");
                return Filter.buildRedirect("Переключение на нужную категорию",
                        "main.php?" + filterClean);
            }
            // 2b. Мы на правильной вкладке — ищем предмет
            String fastHtml = FastActionManager.processMainPhp(html);
            if (fastHtml != null) {
                // Предмет найден! processMainPhp уже обработал FastCount
                android.util.Log.d(TAG, "processMainPhpFast: УСПЕХ, предмет найден");
                return Russian.getBytes(fastHtml);
            }
            // 3. Мы на правильной вкладке, предмет не найден — отмена
            android.util.Log.w(TAG, "processMainPhpFast: предмет не найден на правильной вкладке ("
                    + filterClean + "), отмена");
            FastActionManager.fastCancel("inventory-fast-unsupported-context");
            return null;
        }
        // Мы не на инвентаре и MainPhpFindInv не нашла ссылку — вероятно, нужен обычный reload
        android.util.Log.d(TAG, "processMainPhpFast: не на инвентаре, MainPhpFindInv не нашла ссылку");
        return null;
    }
    /**
     * Проверяет, что HTML относится к боевому фрейму (верхний/основной бой).
     */
    private static boolean isFightFrameHtml(String html) {
        return html != null && (html.contains("var fight_ty") || html.contains("magic_slots();"));
    }
    /**
     * Технический URL-пробник, который AutoFight использует для форс-обновления fight.frame:
     * `main.php?get_id=56&act=10&go=inf&ts=...`.
     * На таком кадре нельзя запускать цепочку AutoFish, иначе начинается race навигации.
     */
    private static boolean isAutoFightReloadProbeAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        String lower = address.toLowerCase(Locale.ROOT);
        return lower.contains("get_id=56")
                && lower.contains("act=10")
                && lower.contains("go=inf")
                && lower.contains("ab_reload_probe=1")
                && lower.contains("ts=");
    }
    /**
     * FastId, запускающие нападение/вход в бой (а не бафы/зелья).
     * Для них при входе в бой fast-цикл должен завершаться.
     */
    private static boolean isAttackFastId(String fastId) {
        if (fastId == null) return false;
        switch (fastId) {
            case "i_svi_001.gif":
            case "i_svi_002.gif":
            case "i_w28_26.gif":
            case "i_w28_26X.gif":
            case "i_svi_205.gif":
            case "i_w28_24.gif":
            case "i_w28_25.gif":
                return true;
            default:
                return false;
        }
    }
    /**
     * Определяет фильтр инвентаря по FastId.
     * Аналог switch в C# MainPhp.cs строки 1436-1534
     *
     * @return строка фильтра (например "&im=0&wca=28") или null для неизвестного FastId
     */
    private static String getInventoryFilter(String fastId) {
        if (fastId == null) return null;
        String normalizedFastId = normalizeFastId(fastId);
        switch (normalizedFastId) {
            // Свитки и нападалки → wca=28
            case "i_svi_001.gif":
            case "i_svi_002.gif":
            case "i_w28_26.gif":
            case "i_w28_26X.gif":
            case "i_svi_205.gif":
            case "i_w28_24.gif":
            case "i_w28_25.gif":
            case "i_w28_22.gif":
            case "i_w28_23.gif":
            case "i_w28_28.gif":
            case "i_svi_213.gif":
            case "i_w28_27.gif":
            case "i_w28_86.gif":
                return "&im=0&wca=28";
            // Зелья → wca=27
            case "Яд":
            case "Зелье Сильной Спины":
            case "Превосходное Зелье Сильной Спины":
            case "Зелье Невидимости":
            case "Зелье Блаженства":
            case "Зелье Метаболизма":
            case "Зелье Просветления":
            case "Зелье Сокрушительных Ударов":
            case "Зелье Стойкости":
            case "Зелье Недосягаемости":
            case "Зелье Точного Попадания":
            case "Зелье Ловких Ударов":
            case "Зелье Мужества":
            case "Зелье Жизни":
            case "Зелье Лечения":
            case "Зелье Восстановления Маны":
            case "Зелье Энергии":
            case "Зелье Удачи":
            case "Зелье Силы":
            case "Зелье Ловкости":
            case "Зелье Гения":
            case "Зелье Боевой Славы":
            case "Зелье Секрет Волшебника":
            case "Зелье Медитации":
            case "Зелье Иммунитета":
            case "Зелье Лечения Отравлений":
            case "Зелье Огненного Ореола":
            case "Зелье Колкости":
            case "Зелье Загрубелой Кожи":
            case "Зелье Панциря":
            case "Зелье Человек-гора":
            case "Зелье Скорости":
            case "Жажда Жизни":
            case "Ментальная Жажда":
            case "Зелье подвижности":
            case "Ярость Берсерка":
            case "Зелье Хрупкости":
            case "Зелье Мифриловый Стержень":
            case "Зелье Соколиный взор":
            case "Секретное Зелье":
                return "&im=0&wca=27";
            // Эликсиры → im=6
            case "Эликсир Блаженства":
            case "Эликсир Мгновенного Исцеления":
            case "Эликсир Восстановления":
                return "&im=6";
            // Телепорт остров
            case "Телепорт (Остров Туротор)":
                return "&im=0&wca=28";
            // Тотем — НЕ требует инвентаря, работает с основной страницы
            // Возвращаем специальный маркер, processMainPhpFast обрабатывает его отдельно
            case "Тотем":
                return "TOTEM";
            default:
                // Дополнительная нормализация для Android-порта:
                // в настройках/контактах название может прийти с отличиями по регистру
                // и пробелам (включая неразрывные), поэтому для ключевых автодействий
                // делаем мягкое сопоставление по фрагменту названия.
                if (containsIgnoreCase(normalizedFastId, "сильной спины")) {
                    return "&im=0&wca=27";
                }
                if (containsIgnoreCase(normalizedFastId, "яд")) {
                    return "&im=0&wca=27";
                }
                return null;
        }
    }
    /**
     * Нормализует FastId перед сопоставлением:
     * - заменяет неразрывные пробелы на обычные;
     * - убирает BOM/zero-width символы;
     * - схлопывает повторяющиеся пробелы;
     * - trim по краям.
     */
    private static String normalizeFastId(String fastId) {
        if (fastId == null) return "";
        String normalized = fastId
                .replace('\u00A0', ' ')
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .trim();
        return normalized.replaceAll("\\s{2,}", " ");
    }
    /**
     * Проверка вхождения подстроки без учёта регистра.
     *
     * Зависимости:
     * - {@link Locale#ROOT} для стабильного lower-case без локалезависимых эффектов.
     *
     * Назначение:
     * - Нормализация сравнения FastId/названий предметов при разном регистре и раскладке источника.
     */
    private static boolean containsIgnoreCase(String value, String token) {
        if (value == null || token == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }
    /**
     * Проверяет, что мы на странице инвентаря (аналог MainPhpIsInv в MainPhpDrink.cs:221-224).
     * Инвентарь содержит ссылку <a href="?im=0"><img...
     */
    private static boolean mainPhpIsInv(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        if (html.contains("<a href=\"?im=0\"><img") || html.contains("<a href=?im=0><img")) {
            return true;
        }
        return hasInventoryRows(html);
    }

    /**
     * Структурный fallback-детект инвентаря для кейсов, где URL/табы `?im=0` отсутствуют
     * (например, переходы через `Вернуться` или `wca=...&useaction=...`).
     *
     * Нужен для C#-паритета: в ПК версии упаковка/сортировка применяется по факту HTML списка предметов.
     */
    private static boolean hasInventoryRows(String html) {
        int firstRowPos = html.indexOf("<tr><td bgcolor=#F5F5F5>");
        if (firstRowPos == -1) {
            return false;
        }
        boolean hasNickname = containsIgnoreCase(html, "<font class=nickname><b>");
        boolean hasWearOrSell = containsIgnoreCase(html, "value=\"Надеть\"")
                || containsIgnoreCase(html, "value=\"Продать")
                || containsIgnoreCase(html, "image.neverlands.ru/del.gif");
        boolean hasInventoryTabs = containsIgnoreCase(html, "<a href=\"?im=")
                || containsIgnoreCase(html, "<a href=?im=")
                || containsIgnoreCase(html, "main.php?im=");
        boolean hasItemActions = containsIgnoreCase(html, "get_id=57")
                || containsIgnoreCase(html, "get_id=58")
                || containsIgnoreCase(html, "if(top.deletetrue('")
                || containsIgnoreCase(html, "image.neverlands.ru/del.gif");
        // Important: a plain "inventory tab exists" signal is too broad for non-inventory pages
        // (character/return/map can still contain menu links). Require stronger evidence.
        return (hasNickname && hasWearOrSell && (hasInventoryTabs || hasItemActions))
                || (hasInventoryTabs && hasItemActions);
    }

    private static boolean isLikelyInventoryReloadSnapshot(String address, String html) {
        if (address == null || html == null || html.isEmpty()) {
            return false;
        }
        String normalizedAddress = normalizeNeverlandsMainLink(address).toLowerCase(Locale.ROOT);
        if (!normalizedAddress.contains("main.php?r=")) {
            return false;
        }
        if (isFightFrameHtml(html)) {
            return false;
        }
        return mainPhpIsInv(html) || hasInventoryRows(html);
    }

    /**
     * Returns true for intermediate generated transition pages, where AutoSkin must not
     * start a new redirect step. This prevents redirect races and manual-link hijacking
     * on first login (especially around useaction=addon-action hops).
     */
    private static boolean isGeneratedTransitionPage(String address, String html) {
        if (address != null && !address.isEmpty()) {
            String normalizedAddress = normalizeNeverlandsMainLink(address).toLowerCase(Locale.ROOT);
            if (normalizedAddress.contains("useaction=addon-action")) {
                return true;
            }
        }
        return html != null && html.contains(HtmlUtils.GENERATED_PAGE_MARKER);
    }
    /**
     * Ищет ссылку на инвентарь в текущем HTML и генерирует redirect.
     * Портировано из MainPhpDrink.cs — MainPhpFindInv (строки 86-219).
     *
     * В C# есть несколько стратегий поиска vcode:
     * 1. view_arena() + var vcode = [...] — арена
     * 2. view_moor()/view_taverna()/etc + var vcode = [[1,"..."]] — здания
     * 3. Кнопка "Инвентарь" с onclick location='...'
     * 4. JSON массив ["inv","Инвентарь","vcode"...]
     *
     * @param html   HTML страницы
     * @param filter Фильтр категории (например "&im=0&wca=28")
     * @return HTML redirect строка или null
     */
    private static String mainPhpFindInv(String html, String filter) {
        // Если мы уже на инвентаре — не нужен redirect
        if (mainPhpIsInv(html)) {
            return null;
        }
        // Стратегия 1: view_arena() — арена
        if (html.contains("view_arena()")) {
            String result = mainPhpFindInvArena(html, filter);
            if (result != null) return result;
        }
        // Стратегия 2: view_moor/taverna/magic_sch/library/teleport — здания
        if (html.contains("view_moor()") || html.contains("view_taverna()")
                || html.contains("view_magic_sch()") || html.contains("view_library()")
                || html.contains("view_teleport()")) {
            String result = mainPhpFindInvBuilding(html, filter);
            if (result != null) return result;
        }
        // Стратегия 3: Кнопка "Инвентарь" с onclick
        if (html.contains("Инвентарь") || html.contains("\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C")) {
            String result = mainPhpFindInvOld(html, filter);
            if (result != null) return result;
        }
        // Стратегия 4: JSON ["inv","Инвентарь","vcode"...]
        String patternEnter = "[\"inv\",\"Инвентарь\",\"";
        int pos = html.indexOf(patternEnter);
        if (pos == -1) {
            // Пробуем варианты с экранированными кавычками
            patternEnter = "[\"inv\",\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\",\"";
            pos = html.indexOf(patternEnter);
        }
        if (pos != -1) {
            pos += patternEnter.length();
            int posEnd = html.indexOf('"', pos);
            if (posEnd != -1) {
                String vcodeInv = html.substring(pos, posEnd);
                String link = "main.php?get_id=56&act=10&go=inv&vcode=" + vcodeInv + filter;
                return buildRedirectHtml("Переключение на инвентарь", link);
            }
        }
        // Стратегия 5: Кнопка "Вернуться" → main.php (для случая когда мы внутри инвентаря, но на другой странице)
        if (html.contains("value=\"Вернуться\">") || html.contains("value=\"\u0412\u0435\u0440\u043D\u0443\u0442\u044C\u0441\u044F\">")) {
            if (html.contains("onclick=\"location='../main.php'\"") || html.contains("onclick=\"location='main.php'\"")) {
                return buildRedirectHtml("Переключение на инвентарь", "main.php");
            }
        }
        return null;
    }
    /**
     * Стратегия поиска инвентаря на арене (view_arena).
     * Аналог MainPhpDrink.cs строки 99-130
     */
    private static String mainPhpFindInvWithFallback(String html, String filter, String address) {
        // Анти-зацикливание: если уже на go=inv с нужным фильтром, redirect не нужен.
        if (inventoryAddressMatchesFilter(address, filter)) {
            return null;
        }
        // Если уже на go=inv, но фильтр не совпадает, синхронизируем адрес с нужными параметрами.
        if (isInventoryAddress(address)) {
            String normalizedAddress = normalizeNeverlandsMainLink(address);
            String filteredAddress = applyInventoryFilterToLink(normalizedAddress, filter);
            if (!normalizedAddress.equals(filteredAddress)) {
                android.util.Log.d(TAG, "AUTO_FALLBACK_TRACE mainPhpFindInv: address filter sync -> " + filteredAddress);
                return buildRedirectHtml("Переключение на инвентарь", filteredAddress);
            }
            return null;
        }
        String redirectHtml = mainPhpFindInv(html, filter);
        if (redirectHtml != null) {
            return redirectHtml;
        }
        String fallbackInvLink = findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inv", "vcode=");
        if (fallbackInvLink == null) {
            fallbackInvLink = findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
        }
        if (fallbackInvLink == null) {
            return null;
        }
        String filteredLink = applyInventoryFilterToLink(fallbackInvLink, filter);
        android.util.Log.d(TAG, "AUTO_FALLBACK_TRACE mainPhpFindInv: regex fallback -> " + filteredLink);
        return buildRedirectHtml("Переключение на инвентарь", filteredLink);
    }
    /**
     * Поиск ссылки инвентаря в шаблоне арены ({@code view_arena} + {@code var vcode=[...]}).
     *
     * Зависимости:
     * - Привязка к формату JS-массива {@code vcode} на аренной странице.
     * - {@link #buildRedirectHtml(String, String)} для выдачи перехода на {@code go=inv}.
     *
     * Назначение:
     * - Вернуть рабочую ссылку на инвентарь в arena-layout, когда обычные шаблоны не подходят.
     */
    private static String mainPhpFindInvArena(String html, String filter) {
        String patternArena = "var vcode = [";
        int pos = html.indexOf(patternArena);
        if (pos == -1) return null;
        pos += patternArena.length();
        int posEnd = html.indexOf(']', pos);
        if (posEnd == -1) return null;
        String vcodeargs = html.substring(pos, posEnd);
        String[] pvcode = vcodeargs.split(",");
        if (pvcode.length < 2) return null;
        String avcode = pvcode[1].replace("\"", "").trim();
        if (avcode.isEmpty()) return null;
        String link = "main.php?get_id=56&act=10&go=inv&vcode=" + avcode + filter;
        return buildRedirectHtml("Переключение на инвентарь", link);
    }
    /**
     * Стратегия поиска инвентаря в зданиях (view_moor, view_taverna и т.д.).
     * Аналог MainPhpDrink.cs строки 142-180
     */
    private static String mainPhpFindInvBuilding(String html, String filter) {
        String patternArena = "var vcode = [";
        int pos = html.indexOf(patternArena);
        if (pos == -1) return null;
        pos += patternArena.length();
        // Ищем второй vcode в формате [1,"hash"]
        String pattern2 = ",[1,\"";
        pos = html.indexOf(pattern2, pos);
        if (pos == -1) return null;
        pos += pattern2.length();
        int posEnd = html.indexOf("]", pos);
        if (posEnd == -1) return null;
        // vcode заканчивается перед последней кавычкой и скобкой
        String avcode = html.substring(pos, posEnd - 1);
        if (avcode.isEmpty()) return null;
        String link = "main.php?get_id=56&act=10&go=inv&vcode=" + avcode + filter;
        return buildRedirectHtml("Переключение на инвентарь", link);
    }
    /**
     * Ищет кнопку "Инвентарь" с onclick (аналог MainPhpFindInvOld в MainPhpDrink.cs:33-84).
     */
    private static String mainPhpFindInvOld(String html, String filter) {
        // Вариант 1: value="Инвентарь">
        String s1 = "value=\"Инвентарь\">";
        int p1 = html.indexOf(s1);
        if (p1 == -1) {
            s1 = "value=\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\">";
            p1 = html.indexOf(s1);
        }
        if (p1 != -1) {
            String onclick = "onclick=\"location='";
            int p2 = html.lastIndexOf(onclick, p1);
            if (p2 != -1) {
                p2 += onclick.length();
                int p3 = html.indexOf("'", p2);
                if (p3 != -1) {
                    String link = html.substring(p2, p3) + filter;
                    return buildRedirectHtml("Переключение на инвентарь", link);
                }
            }
        }
        // Вариант 2: class=lbut value="Инвентарь"
        String s1x = "class=lbut value=\"Инвентарь\"";
        int p1x = html.indexOf(s1x);
        if (p1x == -1) {
            s1x = "class=lbut value=\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\"";
            p1x = html.indexOf(s1x);
        }
        if (p1x != -1) {
            String onclick = "onclick=\"location='";
            int p2 = html.indexOf(onclick, p1x);
            if (p2 != -1) {
                p2 += onclick.length();
                int p3 = html.indexOf("'", p2);
                if (p3 != -1) {
                    String link = html.substring(p2, p3) + filter;
                    return buildRedirectHtml("Переключение на инвентарь", link);
                }
            }
        }
        return null;
    }
    /**
     * Генерирует HTML-страницу с JavaScript redirect (String-версия buildRedirect).
     * Аналог BuildRedirect в Filter.cs:280-291
     */
    private static String buildRedirectHtml(String description, String link) {
        String normalizedLink = normalizeNeverlandsMainLink(link);
        return ru.neverlands.abclient.utils.HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head><body>" +
                description +
                "<script language=\"JavaScript\">window.location = \"" + normalizedLink + "\";</script></body></html>";
    }
    /**
     * Обработка страницы боя (портирование MainPhpFight.cs).
     * Анализирует HTML боя, генерирует авто-ход если автобой включен.
     */
    private static String mainPhpFight(String address, String html) {
        android.util.Log.d(TAG, "mainPhpFight: address=" + address + ", htmlLen=" + html.length());
        // --- Логирование переменных верхнего фрейма (fight_ty, param_en, slots_en) ---
        logFightVariable(html, "fight_ty");
        logFightVariable(html, "param_en");
        logFightVariable(html, "slots_en");
        logFightVariable(html, "param_my");
        logFightVariable(html, "slots_my");
        logFightVariable(html, "LogBoi");
        // --- Парсинг боя с помощью LezFight ---
        LezFight fight = new LezFight(html);
        // Снимок ins_HP(...) для UI ожидания лечения (Restoring).
        // Приоритет: серверные cur/max/int из HTML верхнего фрейма.
        InsHpSnapshot insHpSnapshot = parseInsHpSnapshot(html);
        
        // Детальный дамп HTML для диагностики (если нужен)
        boolean dumpFightHtml = AppVars.DebugDumpFightHtml
                || (AppVars.Profile != null && AppVars.Profile.DoHttpLog);
        if (dumpFightHtml) {
            int chunkSize = 800;
            int totalLen = html.length();
            int chunks = (totalLen + chunkSize - 1) / chunkSize;
            android.util.Log.d(TAG, "mainPhpFight: HTML dump, total=" + totalLen + " bytes, chunks=" + chunks);
            for (int i = 0; i < chunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, totalLen);
                android.util.Log.d(TAG, "mainPhpFight HTML[" + start + "-" + end + "]: "
                        + html.substring(start, end));
            }
        }
        
        android.util.Log.d(TAG, "mainPhpFight: LezFight parsed:"
                + " IsValid=" + fight.IsValid
                + " IsBoi=" + fight.IsBoi
                + " IsWaitingForNextTurn=" + fight.IsWaitingForNextTurn
                + " DoStop=" + fight.DoStop
                + " IsLowHp=" + fight.IsLowHp
                + " IsLowMa=" + fight.IsLowMa
                + " DoExit=" + fight.DoExit
                + " LogBoi=" + fight.LogBoi);
        if (!fight.IsValid) {
            android.util.Log.d(TAG, "mainPhpFight: fight.IsValid=false, returning original HTML");
            return html;
        }
        // Фоновый "пульс боя" для AutoModeForegroundService:
        // когда LezFight подтверждает активную фазу (IsBoi=true), фиксируем метку времени.
        // Это помогает пережить переходные кадры без fight_ty (например, краткий main.php после submit).
        if (fight.IsBoi) {
            AppVars.LastFightPulseAtMs = System.currentTimeMillis();
        }
        // Унифицированный флаг "бой завершён":
        // - IsBoi=false: мы уже не в активной фазе ударов,
        // - IsWaitingForNextTurn=false: это не ожидание ответа противника.
        // Используется сразу в двух потоках:
        // 1) AutoBoi-поток (автозавершение/капча),
        // 2) ручной поток (показ капчи без авто-нажатия).
        boolean fightEnded = !fight.IsBoi && !fight.IsWaitingForNextTurn;
        if (fightEnded) {
            registerFightEnd(fight);
            publishFightResultFromLogsIfNeeded(html, address, fight.LogBoi);
        }
        String fightCaptchaUrl = fightEnded ? resolveFightCaptchaUrl(html) : null;
        recoverAutoboiRuntimeStateIfNeeded(fightEnded, fightCaptchaUrl);
        final boolean autoFightEnabledByPreference = isAutoFightEnabledByPreference();
        final boolean autoFightEnabled = autoFightEnabledByPreference
                || AppVars.Autoboi == AutoboiState.AutoboiOn;
        final boolean waitHpEnabled = AppVars.Profile != null && AppVars.Profile.LezDoWaitHp;
        final int waitHpPercent = AppVars.Profile != null ? AppVars.Profile.LezWaitHp : 100;
        final boolean waitMaEnabled = AppVars.Profile != null && AppVars.Profile.LezDoWaitMa;
        final int waitMaPercent = AppVars.Profile != null ? AppVars.Profile.LezWaitMa : 100;
        // Синхронизация Timeout/Restoring как в C# MainPhpFight.cs.
        if (fightEnded && autoFightEnabled) {
            long now = System.currentTimeMillis();
            if (AppVars.Autoboi == AutoboiState.Timeout) {
                AppVars.AutoboiReadyAtMs = 0L;
                AppVars.AutoboiReadyLog = "";
                AppVars.Autoboi = AutoboiState.AutoboiOn;
                android.util.Log.d(TAG, "mainPhpFight: Timeout finished on fight end -> AutoboiOn");
            }
            if (AppVars.Autoboi == AutoboiState.Restoring) {
                boolean logChanged = fight.LogBoi != null && !fight.LogBoi.equals(AppVars.AutoboiReadyLog);
                boolean timerReady = AppVars.AutoboiReadyAtMs > 0L && now >= AppVars.AutoboiReadyAtMs;
                if (!logChanged && !timerReady) {
                    long waitMs = AppVars.AutoboiReadyAtMs > now ? (AppVars.AutoboiReadyAtMs - now) : 1200L;
                    int delay = (int) Math.max(1000L, Math.min(5000L, waitMs));
                    android.util.Log.d(TAG, "mainPhpFight: restoring in progress, waitMs=" + waitMs);
                    int curHp = insHpSnapshot != null ? insHpSnapshot.curHp : fight.getCurrentHp();
                    int maxHp = insHpSnapshot != null ? insHpSnapshot.maxHp : fight.getMaxHp();
                    int curMa = insHpSnapshot != null ? insHpSnapshot.curMa : fight.getCurrentMa();
                    int maxMa = insHpSnapshot != null ? insHpSnapshot.maxMa : fight.getMaxMa();
                    return buildRestoringStatusHtml(
                            address,
                            delay,
                            waitMs,
                            curHp,
                            maxHp,
                            curMa,
                            maxMa,
                            waitHpEnabled,
                            waitHpPercent,
                            waitMaEnabled,
                            waitMaPercent
                    );
                }
                if (!logChanged && timerReady && fight.LogBoi != null && !fight.LogBoi.isEmpty()) {
                    AppVars.AutoboiReadyCompletedLog = fight.LogBoi;
                    android.util.Log.d(TAG, "mainPhpFight: restoring timer elapsed, mark completed for log=" + fight.LogBoi);
                }
                AppVars.AutoboiReadyAtMs = 0L;
                AppVars.AutoboiReadyLog = "";
                AppVars.Autoboi = AutoboiState.AutoboiOn;
                android.util.Log.d(TAG, "mainPhpFight: restoring finished -> AutoboiOn");
            }
            if (AppVars.Autoboi == AutoboiState.AutoboiOn) {
                boolean restoreAlreadyCompletedForCurrentLog =
                        fight.LogBoi != null
                                && !fight.LogBoi.isEmpty()
                                && fight.LogBoi.equals(AppVars.AutoboiReadyCompletedLog);
                if (!restoreAlreadyCompletedForCurrentLog) {
                    long newReadyAtMs = fight.calcRestoreAfterBoiReadyAtMs();
                    if (newReadyAtMs > 0L) {
                        if (fight.LogBoi != null && (!fight.LogBoi.equals(AppVars.AutoboiReadyLog) || now > AppVars.AutoboiReadyAtMs)) {
                            AppVars.AutoboiReadyLog = fight.LogBoi;
                            AppVars.AutoboiReadyAtMs = newReadyAtMs;
                        }
                        AppVars.Autoboi = AutoboiState.Restoring;
                        android.util.Log.d(TAG, "mainPhpFight: set Restoring until " + AppVars.AutoboiReadyAtMs);
                        long waitMs = Math.max(0L, AppVars.AutoboiReadyAtMs - now);
                        int delay = (int) Math.max(1000L, Math.min(5000L, waitMs > 0L ? waitMs : 1200L));
                        int curHp = insHpSnapshot != null ? insHpSnapshot.curHp : fight.getCurrentHp();
                        int maxHp = insHpSnapshot != null ? insHpSnapshot.maxHp : fight.getMaxHp();
                        int curMa = insHpSnapshot != null ? insHpSnapshot.curMa : fight.getCurrentMa();
                        int maxMa = insHpSnapshot != null ? insHpSnapshot.maxMa : fight.getMaxMa();
                        return buildRestoringStatusHtml(
                                address,
                                delay,
                                waitMs,
                                curHp,
                                maxHp,
                                curMa,
                                maxMa,
                                waitHpEnabled,
                                waitHpPercent,
                                waitMaEnabled,
                                waitMaPercent
                        );
                    }
                } else {
                    android.util.Log.d(TAG, "mainPhpFight: restoring already completed for current log, continue to finish");
                }
                AppVars.AutoboiReadyAtMs = 0L;
                AppVars.AutoboiReadyLog = "";
            }
        }
        // Этап 2: Уведомление о нападении при смене LogBoi
        // Аналог ParseFightLog + TrayBalloon в C# (MainPhp.cs)
        if (fight.IsBoi && fight.LogBoi != null && !fight.LogBoi.isEmpty()
                && !fight.LogBoi.equals(AppVars.LastBoiLog)) {
            android.util.Log.d(TAG, "mainPhpFight: NEW FIGHT detected! LogBoi changed: "
                    + AppVars.LastBoiLog + " -> " + fight.LogBoi);
            AppVars.LastBoiLog = fight.LogBoi;
            AppVars.LastBoiUron = "";
            lastAutoSkinProbeFightLog = "";
            AppVars.AutoboiReadyCompletedLog = "";
            fight.updateLastBoiFromLogs();
            notifyNewFight(fight);
            // C# аналог UnderAttack.Parse(html): анонс в чат с учётом LezSay (Chat/Clan/Pair/No).
            UnderAttackManager.parseAsync(html);
        }
        // Перед авто-завершением боя (act=7) проверяем разделку ещё раз.
        // Это страхует кейсы, когда данные разделки не были доступны на ранней стадии обработки.
        if (fightEnded && isAutoSkinEnabledByPreference()) {
            boolean alreadyOnRazAddress = address != null && address.contains("get_id=17");
            if (!alreadyOnRazAddress) {
                String razHtml = mainPhpRaz(html);
                if (razHtml != null) {
                    android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpFight: fight ended, run raz before finish");
                    return razHtml;
                }
                // Если бой шел через go=inf и fight_ty[9] оказался пустым, делаем один probe полного main.php.
                // Это повторяет поведение ПК-клиента, где после submit обрабатывается обычный main.php кадр,
                // из которого приходит заполненный массив параметров разделки.
                boolean infAddress = address != null && address.contains("get_id=56&act=10&go=inf");
                boolean hasFightLog = fight.LogBoi != null && !fight.LogBoi.isEmpty();
                boolean probeNotDoneForFight = hasFightLog && !fight.LogBoi.equals(lastAutoSkinProbeFightLog);
                if (infAddress && probeNotDoneForFight) {
                    lastAutoSkinProbeFightLog = fight.LogBoi;
                    String probeUrl = "http://neverlands.ru/main.php?r=" + System.currentTimeMillis();
                    android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpFight: raz probe redirect to " + probeUrl);
                    return buildDelayedRedirectHtml("Проверка разделки", probeUrl, 260);
                }
            }
        }
        // Ветка завершения боя в AutoBoi:
        // - зависит от AppVars.Profile.LezDoAutoboi и AppVars.Autoboi==AutoboiOn,
        // - использует AppVars.FightLink, который формируется в LezFight.BuildFightLink(),
        // - при капче вызывает showFightCaptchaDialogOnce(...) и НЕ делает auto-submit.
        if (fightEnded
                && autoFightEnabled
                && AppVars.Autoboi == AutoboiState.AutoboiOn) {
            android.util.Log.d(TAG, "mainPhpFight: FIGHT ENDED with autoboi ON - processing finish");
            String captchaUrl = fightCaptchaUrl;
            boolean needCaptcha = captchaUrl != null && !captchaUrl.isEmpty();
            String fightLink = AppVars.FightLink;
            // Fallback: если LezFight не успел собрать ссылку завершения, достаём её из текущего HTML.
            if (fightLink == null || fightLink.isEmpty()) {
                String recoveredFightLink = extractFightFinishLinkFromHtml(html, needCaptcha);
                if (recoveredFightLink != null && !recoveredFightLink.isEmpty()) {
                    fightLink = recoveredFightLink;
                    AppVars.FightLink = recoveredFightLink;
                    android.util.Log.d(TAG, "mainPhpFight: recovered finish link from html: " + recoveredFightLink);
                }
            }
            // Отдельная ветка "голой" кнопки завершения (без captcha/FEND):
            // браузерный эталон: GET main.php?get_id=61&act=5&st=6&vcode=...
            if (!needCaptcha) {
                String cleanFinishLink = extractFightCleanFinishLinkFromHtml(html);
                if (cleanFinishLink != null && !cleanFinishLink.isEmpty()) {
                    boolean replacedPrevious = fightLink != null
                            && !fightLink.isEmpty()
                            && !cleanFinishLink.equals(fightLink);
                    fightLink = cleanFinishLink;
                    AppVars.FightLink = cleanFinishLink;
                    android.util.Log.d(TAG, "mainPhpFight: recovered CLEAN finish link from html: "
                            + cleanFinishLink + (replacedPrevious ? " (override previous fightLink)" : ""));
                }
            }
            FightFinishPageMarkers markers = inspectFightFinishPageMarkers(html);
            FinishFlowDecision decision;
            String decisionReason;
            String finishFormSubmitHtml = null;
            if (needCaptcha) {
                decision = FinishFlowDecision.CAPTCHA_REQUIRED;
                decisionReason = "captcha_url_detected";
                if (fightLink == null || fightLink.isEmpty()) {
                    fightLink = address;
                }
                if (fightLink == null || fightLink.isEmpty()) {
                    fightLink = "http://neverlands.ru/main.php";
                }
                String normalizedCaptchaFinish = normalizeNeverlandsMainLink(fightLink);
                if (normalizedCaptchaFinish != null && !normalizedCaptchaFinish.isEmpty()) {
                    fightLink = normalizedCaptchaFinish;
                }
            } else if (fightLink != null && !fightLink.isEmpty() && !fightLink.contains("????")) {
                decision = FinishFlowDecision.DIRECT_FINISH_LINK;
                decisionReason = "fight_link_ready";
            } else {
                finishFormSubmitHtml = buildFightEndFormSubmitHtml(html);
                if (finishFormSubmitHtml != null) {
                    decision = FinishFlowDecision.FEND_AUTOSUBMIT_ALLOWED;
                    decisionReason = "fight_link_missing_but_fend_ready";
                } else {
                    decision = FinishFlowDecision.KEEP_ORIGINAL_HTML;
                    decisionReason = "fight_link_missing_and_fend_not_ready";
                }
            }
            logFinishFlowDecision(decision, fight, address, fightLink, captchaUrl, markers, decisionReason);
                // Важно: фикс восстановления AutoBoi после ручной капчи.
                // Если капча пришла в режиме AutoboiOn, запоминаем это состояние,
                // чтобы MainActivity после submit кода вернул `AutoboiOn`.
            if (decision == FinishFlowDecision.CAPTCHA_REQUIRED) {
                android.util.Log.d(TAG, "mainPhpFight: CAPTCHA required, stopping autoboi and showing dialog: " + captchaUrl);
                boolean fromCaptchaSubmit = address != null
                        && address.contains("get_id=61")
                        && address.contains("act=7")
                        && address.contains("code=");
                if (fromCaptchaSubmit) {
                    // Сервер снова требует капчу после submit: снимаем anti-duplicate guard
                    // для того же finish-key и разрешаем повторный показ popup.
                    AppVars.LastSubmittedFightCaptchaFinishKey = "";
                    AppVars.LastSubmittedFightCaptchaAtMs = 0L;
                }
                AppVars.ResumeAutoboiAfterCaptcha = true;
                AppVars.Autoboi = AutoboiState.AutoboiOff;
                AppVars.ContentMainPhp = html;
                showFightCaptchaDialogOnce(captchaUrl, fightLink, fight.LogBoi);
                return html;
            }
            if (decision == FinishFlowDecision.DIRECT_FINISH_LINK) {
                long now = System.currentTimeMillis();
                int redirectDelay = AUTO_FINISH_MIN_DELAY_MS + RANDOM.nextInt(AUTO_FINISH_EXTRA_DELAY_MS + 1);
                if (redirectDelay >= 0) {
                    lastAutoFinishRedirectAtMs = now;
                    AppVars.FightLink = "";
                    return buildDelayedRedirectHtml("Завершение боя", fightLink, redirectDelay);
                }
                AppVars.FightLink = "";
                return Russian.getString(Filter.buildRedirect(" ", fightLink));
            }
            if (decision == FinishFlowDecision.FEND_AUTOSUBMIT_ALLOWED && finishFormSubmitHtml != null) {
                android.util.Log.d(TAG, "mainPhpFight: FightLink missing, auto-submit FEND form");
                AppVars.FightLink = "";
                return finishFormSubmitHtml;
            }
            // Специальный post-fight кейс: сервер держит состояние завершения (fight_ty[4]=6),
            // но ещё не отдал ни FightLink, ни FEND.
            // Если оставить "как есть", кадр может зависнуть и не дожидаться появления "голой" кнопки завершения.
            // Поэтому делаем контролируемый re-poll верхнего фрейма до появления валидного finish-path.
            android.util.Log.d(TAG, "mainPhpFight: FightLink missing and FEND not parsed, keep original fight HTML");
            AppVars.FightLink = "";
            AppVars.ContentMainPhp = html;
            return html;
        }
        // Ветка ручного режима:
        // если сервер вернул капчу на странице завершения боя, показываем тот же popup,
        // что и в AutoBoi, но без попытки автоматического нажатия "Завершить".
        // Зависимости:
        // - extractCaptchaUrl(html): извлечение URL картинки,
        // - AppVars.FightLink/address: URL, куда будет отправлен code=<digits>,
        // - showFightCaptchaDialogOnce(...): broadcast в MainActivity.
        if (fightEnded) {
            String manualCaptchaUrl = fightCaptchaUrl;
            if (manualCaptchaUrl != null && !manualCaptchaUrl.isEmpty()) {
                String finishLink = AppVars.FightLink;
                if (finishLink == null || finishLink.isEmpty()) {
                    // fallback: используем текущий адрес страницы завершения
                    finishLink = address;
                }
                android.util.Log.d(TAG, "mainPhpFight: manual mode CAPTCHA detected, showing dialog: " + manualCaptchaUrl);
                boolean fromCaptchaSubmit = address != null && address.contains("code=");
                if (fromCaptchaSubmit) {
                    String submittedCode = getUrlParam(address, "code");
                    String submittedVcode = getUrlParam(address, "vcode");
                    android.util.Log.d(TAG, "mainPhpFight: captcha submit still requires challenge, code="
                            + submittedCode + ", vcode=" + submittedVcode);
                    AppVars.LastSubmittedFightCaptchaFinishKey = "";
                    AppVars.LastSubmittedFightCaptchaAtMs = 0L;
                    notifyCaptchaRejectedOnce(submittedCode, submittedVcode);
                }
                showFightCaptchaDialogOnce(manualCaptchaUrl, finishLink, fight.LogBoi);
                AppVars.ContentMainPhp = html;
                return html;
            }
        }
        // Проверяем, ждём ли мы хода противника - нужно auto-refresh
        if (fight.IsWaitingForNextTurn) {
            android.util.Log.d(TAG, "mainPhpFight: waiting for opponent turn (foe HP=" + fight.FoeCurrentHp + ")");
            boolean shouldAutoRefresh = AppVars.AutoRefresh;
            if (!shouldAutoRefresh && autoFightEnabled
                    && AppVars.Autoboi == AutoboiState.AutoboiOn) {
                // Для AutoBoi нужно продолжать обновлять фрейм, иначе после 1 удара остановимся на ходе противника.
                shouldAutoRefresh = true;
            }
            if (shouldAutoRefresh) {
                int delay = 1200 + RANDOM.nextInt(900); // 1200-2100ms
                android.util.Log.d(TAG, "mainPhpFight: auto-refresh waiting enabled, reloading after " + delay + "ms: " + address);
                return buildInPlaceFightAutoRefreshHtml(html, address, delay);
            }
            android.util.Log.d(TAG, "mainPhpFight: AutoRefresh disabled, returning original content");
            return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
        }
        // Проверяем, включен ли автобой в профиле
        if (autoFightEnabled) {
            android.util.Log.d(TAG, "mainPhpFight: LezDoAutoboi enabled, Autoboi state=" + AppVars.Autoboi);
            
            // Проверяем состояние автобоя
            if (AppVars.Autoboi == AutoboiState.AutoboiOn) {
                if (fight.IsBoi) {
                    // Мы в бою
                    android.util.Log.d(TAG, "mainPhpFight: in fight, checking safety conditions:"
                            + " DoStop=" + fight.DoStop
                            + " IsLowHp=" + fight.IsLowHp
                            + " IsLowMa=" + fight.IsLowMa
                            + " DoExit=" + fight.DoExit);
                    
                    if (!fight.DoStop && !fight.IsLowHp && !fight.IsLowMa && !fight.DoExit) {
                        // Бой идёт, условия безопасные - возвращаем авто-ход
                        android.util.Log.d(TAG, "mainPhpFight: SAFE - returning fight.Frame for auto-attack");
                        android.util.Log.d(TAG, "mainPhpFight: fight.Frame = " + (fight.Frame != null ? fight.Frame.substring(0, Math.min(200, fight.Frame.length())) : "NULL"));
                        if (fight.Frame != null && !fight.Frame.isEmpty()) {
                            return fight.Frame;
                        }
                        android.util.Log.w(TAG, "mainPhpFight: fight.Frame is empty, stopping autoboi to avoid null flow");
                        if (AppVars.Autoboi != AutoboiState.Timeout) {
                            notifyFightStopped(fight);
                            AppVars.Autoboi = AutoboiState.Timeout;
                        }
                        return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
                    } else {
                        // Опасная ситуация - останавливаем автобой
                        android.util.Log.d(TAG, "mainPhpFight: DANGEROUS - stopping autoboi, setting Timeout");
                        if (AppVars.Autoboi != AutoboiState.Timeout) {
                            notifyFightStopped(fight);
                            AppVars.Autoboi = AutoboiState.Timeout;
                        }
                    }
                } else {
                    // Завершение уже обработано выше в блоке fightEnded (Timeout/Restoring/FightLink/капча).
                    android.util.Log.d(TAG, "mainPhpFight: fight ended branch already handled, keep current frame");
                }
            } else {
                android.util.Log.d(TAG, "mainPhpFight: Autoboi state is " + AppVars.Autoboi + ", not AutoboiOn");
            }
        } else {
            android.util.Log.d(TAG, "mainPhpFight: auto-fight disabled for this frame"
                    + " pref=" + autoFightEnabledByPreference
                    + ", runtimeState=" + AppVars.Autoboi);
            if (!fight.IsBoi) {
                android.util.Log.d(TAG, "mainPhpFight: autoboi disabled, keeping original fight frame for manual finish");
            }
        }
        // Логируем ключевые признаки страницы боя для диагностики
        android.util.Log.d(TAG, "mainPhpFight flags:"
                + " magic_slots=" + html.contains("magic_slots();")
                + " fight_ty=" + html.contains("var fight_ty")
                + " IsBoi_form=" + html.contains("<form")
                + " StartAct=" + html.contains("StartAct()")
                + " document.ff=" + html.contains("document.ff")
                + " autosubmit=" + html.contains("document.ff.submit")
        );
        // Аналог C# версии - возвращаем AppVars.ContentMainPhp (оригинальный HTML)
        // а не изменённый html, чтобы избежать белого фрейма
        return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
    }
    /**
     * Извлекает URL капчи завершения боя из HTML.
     *
     * Назначение:
     * - определить, что сервер потребовал ручной ввод цифр (капча),
     * - передать абсолютный URL картинки в диалог MainActivity.
     *
     * Что поддерживается:
     * - `/modules/code/code.php?...`
     * - `modules/code/code.php?...`
     * - `http://neverlands.ru/modules/code/code.php?...`
     *
     * Зависимости:
     * - вызывается из mainPhpFight(...) в AutoBoi и ручной ветках завершения,
     * - результат используется showFightCaptchaDialogOnce(...),
     * - диагностические логи пишутся в TAG `MainPhp`.
     */
    private static String extractCaptchaUrl(String html) {
        try {
            // Поддержка разных вариантов src:
            // 1) /modules/code/code.php?...
            // 2) modules/code/code.php?...
            // 3) http://neverlands.ru/modules/code/code.php?...
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "(?i)(https?://[^\\s\"']+?/modules/code/code\\.php\\?[^\\s\"']+|/?modules/code/code\\.php\\?[^\\s\"']+)"
            );
            java.util.regex.Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String url = matcher.group(1);
                if (url == null || url.isEmpty()) {
                    return null;
                }
                if (!url.startsWith("http")) {
                    if (!url.startsWith("/")) {
                        url = "/" + url;
                    }
                    url = "http://neverlands.ru" + url;
                }
                android.util.Log.d(TAG, "extractCaptchaUrl: found " + url);
                return url;
            }
            if (html != null && html.contains("code.php")) {
                android.util.Log.d(TAG, "extractCaptchaUrl: code.php present but url pattern not matched");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "extractCaptchaUrl error", e);
        }
        return null;
    }
    /**
     * Инициирует показ popup-капчи завершения боя через LocalBroadcast.
     *
     * Поведение:
     * - нормализует finishUrl до абсолютного адреса `http://neverlands.ru/...`,
     * - дедуплицирует показ по ключу `logBoi|captchaUrl|finishUrl`,
     * - отправляет `AppVars.ACTION_SHOW_CAPTCHA`, который принимает MainActivity
     *   и открывает showCaptchaDialog(...).
     *
     * Зависимости:
     * - `AppVars.ACTION_SHOW_CAPTCHA` (контракт события),
     * - `AppVars.getContext()` (доступ к LocalBroadcastManager),
     * - `MainActivity.broadcastReceiver` и `MainActivity.showCaptchaDialog(...)`,
     * - `lastFightCaptchaDialogKey` (защита от многократного открытия окна).
     */
    private static void showFightCaptchaDialogOnce(String captchaUrl, String finishUrl, String logBoi) {
        if (captchaUrl == null || captchaUrl.isEmpty() || finishUrl == null || finishUrl.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        String normalizedFinishUrl = finishUrl;
        if (!normalizedFinishUrl.startsWith("http")) {
            normalizedFinishUrl = "http://neverlands.ru/" + normalizedFinishUrl.replaceFirst("^/+", "");
        }
        String fightExp = getUrlParam(normalizedFinishUrl, "fexp");
        String finishVcode = getUrlParam(normalizedFinishUrl, "vcode");
        String normalizedCaptchaUrl = captchaUrl.replaceFirst("^https://", "http://");
        String key = (logBoi == null ? "" : logBoi) + "|" + (fightExp == null ? "" : fightExp) + "|"
                + (finishVcode == null ? "" : finishVcode) + "|" + normalizedCaptchaUrl;
        if (AppVars.IsFightCaptchaDialogVisible) {
            if (key.equals(lastFightCaptchaDialogKey)) {
                android.util.Log.d(TAG, "showFightCaptchaDialogOnce: dialog already visible for same key, skip");
                return;
            }
            // Если challenge изменился при уже открытом popup — отправляем обновление.
            // MainActivity сам корректно заменяет текущее окно на новое.
            android.util.Log.d(TAG, "showFightCaptchaDialogOnce: dialog visible, update to new key");
            lastFightCaptchaDialogKey = key;
            lastFightCaptchaDialogAtMs = now;
            if (AppVars.getContext() == null) {
                android.util.Log.w(TAG, "showFightCaptchaDialogOnce: context is null while updating dialog");
                return;
            }
            Intent updateIntent = new Intent(AppVars.ACTION_SHOW_CAPTCHA);
            updateIntent.putExtra("captchaUrl", captchaUrl);
            updateIntent.putExtra("finishUrl", normalizedFinishUrl);
            LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(updateIntent);
            return;
        }
        if (key.equals(lastFightCaptchaDialogKey) && (now - lastFightCaptchaDialogAtMs) < 3000L) {
            android.util.Log.d(TAG, "showFightCaptchaDialogOnce: duplicate key, skip dialog");
            return;
        }
        lastFightCaptchaDialogKey = key;
        lastFightCaptchaDialogAtMs = now;
        if (AppVars.getContext() == null) {
            android.util.Log.w(TAG, "showFightCaptchaDialogOnce: context is null, skip dialog");
            AppVars.IsFightCaptchaDialogVisible = false;
            return;
        }
        AppVars.IsFightCaptchaDialogVisible = true;
        Intent intent = new Intent(AppVars.ACTION_SHOW_CAPTCHA);
        intent.putExtra("captchaUrl", captchaUrl);
        intent.putExtra("finishUrl", normalizedFinishUrl);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }
    /**
     * Инициирует показ captcha-диалога для авто-рыбалки (fish action `get_id=55&act=4`) с дедупликацией.
     *
     * Зависимости:
     * - `AppVars.ACTION_SHOW_CAPTCHA` + `MainActivity.broadcastReceiver` (контракт открытия UI captcha);
     * - `AppVars.IsFightCaptchaDialogVisible` как единый флаг видимости captcha-окна;
     * - `lastFishCaptchaDialogKey/AtMs` для защиты от циклических повторов одного challenge.
     */
    private static void showFishCaptchaDialogOnce(String captchaUrl, String finishUrl) {
        if (captchaUrl == null || captchaUrl.isEmpty() || finishUrl == null || finishUrl.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        String normalizedFinishUrl = finishUrl;
        if (!normalizedFinishUrl.startsWith("http")) {
            normalizedFinishUrl = "http://neverlands.ru/" + normalizedFinishUrl.replaceFirst("^/+", "");
        }
        String normalizedCaptchaUrl = captchaUrl.replaceFirst("^https://", "http://");
        String key = normalizedFinishUrl + "|" + normalizedCaptchaUrl;
        if (key.equals(lastFishCaptchaDialogKey) && (now - lastFishCaptchaDialogAtMs) < 3000L) {
            android.util.Log.d(TAG, "showFishCaptchaDialogOnce: duplicate key, skip");
            return;
        }
        lastFishCaptchaDialogKey = key;
        lastFishCaptchaDialogAtMs = now;
        if (AppVars.getContext() == null) {
            android.util.Log.w(TAG, "showFishCaptchaDialogOnce: context is null, skip");
            return;
        }
        AppVars.ResumeAutoboiAfterCaptcha = false;
        AppVars.IsFightCaptchaDialogVisible = true;
        Intent intent = new Intent(AppVars.ACTION_SHOW_CAPTCHA);
        intent.putExtra("captchaUrl", captchaUrl);
        intent.putExtra("finishUrl", normalizedFinishUrl);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }
    /**
     * Обработка страницы завершения боя (get_id=61&act=7).
     * Автоматически нажимает кнопку "Завершить" - аналог PC версии.
     */
    private static String mainPhpFightEnd(String address, String html) {
        android.util.Log.d(TAG, "mainPhpFightEnd: processing fight end page");
        
        // Проверяем, есть ли уже параметры в адресе (URL содержит все нужные параметры)
        if (address.contains("fexp=") && address.contains("act=7")) {
            android.util.Log.d(TAG, "mainPhpFightEnd: has fexp, building redirect");
            
            // Параметры уже есть в URL - извлекаем их
            String fexp = getUrlParam(address, "fexp");
            String fres = getUrlParam(address, "fres");
            String vcode = getUrlParam(address, "vcode");
            String ftype = getUrlParam(address, "ftype");
            String min1 = getUrlParam(address, "min1");
            String max1 = getUrlParam(address, "max1");
            String min2 = getUrlParam(address, "min2");
            String max2 = getUrlParam(address, "max2");
            String sum1 = getUrlParam(address, "sum1");
            String sum2 = getUrlParam(address, "sum2");
            
            // Проверяем, не является ли это повторным заходом на страницу завершения боя
            // (защита от бесконечного цикла)
            if (html.contains("error.css") || html.contains("Ошибка") || html.contains("error")) {
                // Сервер вернул ошибку - возвращаем оригинальную страницу
                android.util.Log.d(TAG, "mainPhpFightEnd: server returned error page, returning original HTML");
                return html;
            }
            
            // Ищем форму завершения боя в HTML - если есть, извлекаем параметры и делаем POST
            // Но так как WebView не перехватывает POST, просто возвращаем HTML с авто-submit
            if (html.contains("<form") && html.contains("act=7")) {
                android.util.Log.d(TAG, "mainPhpFightEnd: found form in HTML, auto-submitting");
                // Возвращаем HTML с авто-submit формой
                return html; // WebView сам отправит форму
            }
            
            // Строим GET редирект для завершения боя (аналог C# BuildRedirect)
            // Используем window.location для перехвата в WebView
            android.util.Log.d(TAG, "mainPhpFightEnd: building redirect for fight end");
            
            // Формируем URL с GET параметрами
            String redirectUrl = "main.php?get_id=61&act=7" +
                "&fexp=" + fexp +
                "&fres=" + fres +
                "&vcode=" + vcode +
                "&ftype=" + ftype +
                "&min1=" + min1 +
                "&max1=" + max1 +
                "&min2=" + min2 +
                "&max2=" + max2 +
                "&sum1=" + sum1 +
                "&sum2=" + sum2;
            
            return Russian.getString(Filter.buildRedirect("Завершение боя", redirectUrl));
        }
        
        // Параметры не найдены в URL - ищем в HTML (fexp может быть в JavaScript)
        // Пока возвращаем как есть
        android.util.Log.d(TAG, "mainPhpFightEnd: no fexp in URL, returning original HTML");
        return html;
    }
    /**
     * Извлекает параметр из URL.
     */
    private static String getUrlParam(String url, String paramName) {
        try {
            String search = paramName + "=";
            int idx = url.indexOf(search);
            if (idx >= 0) {
                idx += search.length();
                int end = url.indexOf("&", idx);
                if (end < 0) end = url.length();
                return url.substring(idx, end);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "getUrlParam error: " + paramName, e);
        }
        return "";
    }
    /**
     * Отправляет уведомление в чат об остановке боя.
     */
    /**
     * Уведомление о начале нового боя (аналог TrayBalloon при смене LogBoi в C#).
     * Отправляет сообщение в чат: имя противника, уровень, HP/MA, тип боя.
     */
    /**
     * Публикует в чат итог боя/разделки на основе массива `var logs = [...]`.
     * Нужен как fallback для кейсов, когда серверный итог не доходит до нижнего чата.
     */
    private static void publishFightResultFromLogsIfNeeded(String html, String address, String logIdHint) {
        if (html == null || html.isEmpty() || !html.contains("var logs = ")) {
            return;
        }
        String logsBlock = HelperStrings.subString(html, "var logs = ", ";");
        if (logsBlock == null || logsBlock.isEmpty()) {
            return;
        }
        String winnerNick = "";
        java.util.regex.Matcher winnerMatcher = java.util.regex.Pattern.compile(
                "\"<B>Победа за</B>\",\\[1,2,\\\"([^\\\"]+)\\\""
        ).matcher(logsBlock);
        if (winnerMatcher.find()) {
            String winnerRaw = winnerMatcher.group(1);
            winnerNick = winnerRaw == null ? "" : winnerRaw.trim();
        }
        boolean isSkinResult = address != null && address.contains("get_id=17");
        boolean skinSkillRaised = false;
        List<String> lootItems = new ArrayList<>();
        java.util.regex.Matcher lootMatcher = java.util.regex.Pattern.compile(
                "\\[8,\\d+,\\\"([^\\\"]+)\\\",(\\d+)\\]"
        ).matcher(logsBlock);
        while (lootMatcher.find()) {
            String lootNameRaw = lootMatcher.group(1);
            if (lootNameRaw == null) {
                continue;
            }
            String skillRaiseRaw = lootMatcher.group(2);
            String lootName = lootNameRaw.trim();
            if (lootName.isEmpty()) {
                continue;
            }
            if (isSkinResult && skillRaiseRaw != null && !skillRaiseRaw.isEmpty()) {
                try {
                    skinSkillRaised = skinSkillRaised || Integer.parseInt(skillRaiseRaw) > 0;
                } catch (NumberFormatException ignore) {
                    // Игнорируем битые значения в логе сервера.
                }
            }
            if (!lootItems.contains(lootName)) {
                lootItems.add(lootName);
            }
        }
        if ((winnerNick == null || winnerNick.isEmpty()) && lootItems.isEmpty()) {
            return;
        }
        String logId = (logIdHint == null || logIdHint.isEmpty()) ? AppVars.LastBoiLog : logIdHint;
        if (logId == null) {
            logId = "";
        }
        String lootPrefix = isSkinResult
                ? "Результат разделки"
                : "Результат обыска бота";
        String lootLine = String.join(",", lootItems);
        String winnerDedupKey = logId.isEmpty() ? ("nol|" + winnerNick) : logId;
        String lootDedupKey = (logId.isEmpty() ? "nol" : logId) + "|" + lootPrefix + "|" + lootLine;
        boolean shouldSendWinner = winnerNick != null
                && !winnerNick.isEmpty()
                && !winnerDedupKey.equals(lastFightResultWinnerBroadcastKey);
        boolean shouldSendLoot = !lootItems.isEmpty()
                && !lootDedupKey.equals(lastFightResultLootBroadcastKey);
        if (!shouldSendWinner && !shouldSendLoot) {
            android.util.Log.d(TAG, "publishFightResultFromLogsIfNeeded: skip duplicate"
                    + ", logId=" + logId
                    + ", winnerKey=" + winnerDedupKey
                    + ", lootKey=" + lootDedupKey
                    + ", source=" + address);
            return;
        }
        if (AppVars.getContext() != null) {
            if (shouldSendWinner) {
                Intent victoryIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
                victoryIntent.putExtra(
                        "message",
                        "<font color=#009933><b>Победа за " + winnerNick + ".</b></font>"
                );
                LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(victoryIntent);
                lastFightResultWinnerBroadcastKey = winnerDedupKey;
            }
            if (shouldSendLoot) {
                if (!isSkinResult) {
                    Intent lootIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
                    lootIntent.putExtra(
                            "message",
                            "<font color=#006600><b>" + lootPrefix + ":</b></font> " + String.join(", ", lootItems)
                    );
                    LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(lootIntent);
                }
                lastFightResultLootBroadcastKey = lootDedupKey;
            }
        }
        if (isSkinResult && shouldSendLoot) {
            AppVars.AutoSkinCheckRes = true;
            if (skinSkillRaised) {
                AppVars.AutoSkinCheckUm = true;
            }
            android.util.Log.d(TAG, "AUTO_SKIN_TRACE publishFightResultFromLogsIfNeeded: "
                    + "queue AutoSkinCheckRes=true, AutoSkinCheckUm=" + AppVars.AutoSkinCheckUm
                    + ", lootCount=" + lootItems.size());
        }
        if (!isSkinResult && shouldSendLoot) {
            ru.neverlands.abclient.utils.ChatStats.addLoot("", lootItems);
        }
        android.util.Log.d(TAG, "publishFightResultFromLogsIfNeeded: winner=" + winnerNick
                + ", lootCount=" + lootItems.size()
                + ", source=" + address);
    }
    /**
     * Публикует чат-уведомление о начале боя.
     *
     * Зависимости:
     * - {@link LezFight} для определения типа противника и признаков опасности.
     * - {@link AppVars#Profile} / {@code ServDiff} для формирования времени в серверной шкале.
     * - {@link LocalBroadcastManager} и {@code AppVars.ACTION_ADD_CHAT_MESSAGE} для отправки в чат UI.
     *
     * Назначение:
     * - Синхронно информировать пользователя о старте боя в формате, близком к поведению ПК-клиента.
     */
    private static void notifyNewFight(LezFight fight) {
        if (AppVars.getContext() == null) return;
        // Определяем тип противника по имени и ftype
        String foeType;
        boolean isDangerous = fight.IsDangerousFoe();
        if (isDangerous) {
            foeType = " ⚠ ОПАСНЫЙ";
        } else if (fight.IsBoss()) {
            foeType = " [БОСС]";
        } else {
            foeType = "";
        }
        String foes = (AppVars.LastBoiSostav != null && !AppVars.LastBoiSostav.isEmpty())
                ? AppVars.LastBoiSostav
                : (fight.FoeName + " [" + fight.FoeLevel + "]");
        String timeHtml = buildServerChatTimeHtml();
        String message = "Нападение: " + foes + foeType;
        String messageHtml =
                timeHtml +
                "<b><font color=#cc0000>Нападение:</font></b> " +
                "<font color=#004bbb>" + foes + "</font>" +
                foeType;
        android.util.Log.d(TAG, "notifyNewFight: " + message);
        AppVars.LastFightAnnounceAtMs = System.currentTimeMillis();
        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
    }
    /**
     * Формирует timestamp в серверной шкале времени для HTML-сообщений чата.
     *
     * Зависимости:
     * - {@link AppVars.Profile#ServDiff}: смещение локального времени относительно сервера;
     * - единый формат `chattime` используется в уведомлениях автобоя/авто-охоты.
     *
     * Назначение:
     * - унифицировать время в сообщениях "Нападение"/"Результат разделки",
     *   чтобы по чату видно было, на каком этапе (до или после нападения) остановился цикл.
     */
    private static String buildServerChatTimeHtml() {
        long serverMs = System.currentTimeMillis();
        if (AppVars.Profile != null && AppVars.Profile.ServDiff != Long.MIN_VALUE) {
            serverMs = serverMs - AppVars.Profile.ServDiff;
        }
        Date serverTime = new Date(serverMs);
        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(serverTime);
        return "<font class=chattime>&nbsp;" + timeStr + "&nbsp;</font> ";
    }
    /**
     * Внешняя точка входа для анонса нового боя из путей, которые обходят mainPhpFight NEW-FIGHT ветку
     * (например, JS-bridge -> FightViewModel).
     *
     * Зависимости:
     * - {@link #notifyNewFight(LezFight)}: локальный чат-анонс "Нападение".
     * - {@link UnderAttackManager#parseAsync(String)}: серверный announce по настройке LezSay.
     */
    public static void notifyNewFightFromExternalSource(LezFight fight, String html) {
        if (fight == null) {
            return;
        }
        notifyNewFight(fight);
        if (html != null && !html.isEmpty()) {
            UnderAttackManager.parseAsync(html);
        }
    }
    /**
     * Публикует чат-уведомление об остановке автобоя с причинами.
     *
     * Зависимости:
     * - Флаги состояния из {@link LezFight}: {@code DoStop}, {@code IsLowHp}, {@code IsLowMa}, {@code DoExit}.
     * - {@link LocalBroadcastManager} и {@code AppVars.ACTION_ADD_CHAT_MESSAGE}.
     *
     * Назначение:
     * - Явно показать пользователю, почему автоматика остановила бой.
     */
    private static void notifyFightStopped(LezFight fight) {
        if (AppVars.getContext() == null) return;
        
        String message = "Автобой остановлен: ";
        if (fight.DoStop) {
            message += "остановка в группе; ";
        }
        if (fight.IsLowHp) {
            message += "низкое HP; ";
        }
        if (fight.IsLowMa) {
            message += "низкая мана; ";
        }
        if (fight.DoExit) {
            message += "выход из боя; ";
        }
        
        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", "<font color=#cc0000><b>" + message + "</b></font>");
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
    }
    /**
     * Публикует уведомление "капча отклонена" с дедупликацией по паре code/vcode.
     *
     * Зависимости:
     * - Статические ключи дедупа: {@code lastCaptchaRejectKey}, {@code lastCaptchaRejectAtMs}.
     * - {@link LocalBroadcastManager} и {@code AppVars.ACTION_ADD_CHAT_MESSAGE}.
     *
     * Назначение:
     * - Не спамить повторными одинаковыми уведомлениями при дублирующих callback/перезагрузках.
     */
    private static void notifyCaptchaRejectedOnce(String submittedCode, String submittedVcode) {
        if (AppVars.getContext() == null) return;
        String code = submittedCode == null ? "" : submittedCode;
        String vcode = submittedVcode == null ? "" : submittedVcode;
        String key = code + "|" + vcode;
        long now = System.currentTimeMillis();
        if (key.equals(lastCaptchaRejectKey) && (now - lastCaptchaRejectAtMs) < 2000L) {
            return;
        }
        lastCaptchaRejectKey = key;
        lastCaptchaRejectAtMs = now;
        String message = "Капча не принята сервером. Введите код заново.";
        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", "<font color=#cc0000><b>" + message + "</b></font>");
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
    }
    /**
     * Публикует fallback-сводку завершённого боя через единый пайплайн ChatFilter.
     *
     * Зачем:
     * - не дублировать форматирование системной строки в MainPhp;
     * - использовать уже существующую логику ChatFilter (подмена "Поединок завершён" на
     *   полноценную сводку, дедуп боя по LastBoiEndLog, учёт XP/лога).
     *
     * Зависимости:
     * - AppVars.LastBoi* (log/sostav/travm/uron), собранные в боевом потоке;
     * - {@link #extractBattleXpFromHtml(String)} для добавления XP в synthetic chat-line;
     * - {@link ru.neverlands.abclient.utils.ChatFilter#filter(String)} как единая точка парсинга;
     * - {@link LocalBroadcastManager} + ACTION_ADD_CHAT_MESSAGE для вывода уже обработанной строки в UI.
     *
     * Дедупликация:
     * - fallback выполняется один раз на связку logId|xp для act=7-перезагрузок.
     */
    private static void publishFightSummaryFromFinishHtmlIfNeeded(String html, String address, String logIdHint) {
        if (AppVars.getContext() == null) return;
        String logId = (logIdHint == null || logIdHint.isEmpty()) ? AppVars.LastBoiLog : logIdHint;
        if (logId == null || logId.isEmpty()) return;
        String foes = AppVars.LastBoiSostav == null ? "" : AppVars.LastBoiSostav.trim();
        if (foes.isEmpty()) return;
        primeLastBoiDamageFromFinishHtmlIfNeeded(html, logId);
        String battleXp = extractBattleXpFromHtml(html);
        boolean uiForegroundInteractive = false;
        try {
            ru.neverlands.abclient.MainActivity activity =
                    AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
            uiForegroundInteractive = activity != null && activity.isUiForegroundInteractive();
        } catch (Exception ignore) {
        }
        // Поведение, близкое к C#:
        // - если сервер ожидает опыт за бой (fexp>0), в foreground ждём системную chat-строку и не шлём fallback без XP;
        // - если fexp=0 (опыт не положен), fallback обязателен, иначе пользователь не увидит завершение боя.
        //
        // Зависимости:
        // - fexp читаем из текущего URL завершения (`act=7`) через getUrlParam(...),
        // - uiForegroundInteractive берём из MainActivity.isUiForegroundInteractive().
        int fexp = 0;
        try {
            String fexpRaw = address == null ? "" : getUrlParam(address, "fexp");
            fexp = Integer.parseInt(fexpRaw == null || fexpRaw.isEmpty() ? "0" : fexpRaw);
        } catch (Exception ignore) {
            fexp = 0;
        }
        boolean expectXpByFexp = fexp > 0;
        if (battleXp.isEmpty() && expectXpByFexp && uiForegroundInteractive) {
            android.util.Log.d(TAG, "publishFightSummaryFromFinishHtmlIfNeeded: skip foreground fallback without XP"
                    + ", logId=" + logId + ", foes=" + foes + ", fexp=" + fexp);
            return;
        }
        String dedupKey = logId + "|" + battleXp;
        if (dedupKey.equals(lastFightSummaryBroadcastKey)) return;
        lastFightSummaryBroadcastKey = dedupKey;
        StringBuilder synthetic = new StringBuilder();
        synthetic.append(buildServerChatTimeHtml())
                .append("<font color=#000000><b>Системная информация.</b></font> Поединок завершён.");
        if (!battleXp.isEmpty()) {
            synthetic.append(" Получено <font color=#CC0000>боевого</font> опыта: <b><font color=#CC0000>")
                    .append(battleXp)
                    .append("</font></b>.");
        }
        String filteredMessage;
        try {
            filteredMessage = ru.neverlands.abclient.utils.ChatFilter.filter(synthetic.toString());
        } catch (Exception e) {
            android.util.Log.e(TAG, "publishFightSummaryFromFinishHtmlIfNeeded: ChatFilter failed", e);
            filteredMessage = synthetic.toString();
        }
        if (filteredMessage == null || filteredMessage.isEmpty()) {
            filteredMessage = synthetic.toString();
        }
        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", filteredMessage);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
        android.util.Log.d(TAG, "publishFightSummaryFromFinishHtmlIfNeeded: viaChatFilter logId=" + logId
                + ", battleXp=" + battleXp + ", foes=" + foes + ", damage=" + AppVars.LastBoiUron);
    }
    /**
     * Извлекает числовое значение боевого опыта из HTML страницы завершения боя.
     *
     * Зависимости:
     * - использует тот же маркер, что и `ChatFilter`: блок
     *   "Получено <font color=#CC0000>боевого</font> опыта: ...".
     *
     * @param html HTML ответа `main.php?get_id=61&act=7...`.
     * @return строка с XP (только цифры) либо пустая строка, если XP не найден.
     */
    /**
     * Точечный добор LastBoiUron из текущего finish-HTML (`act=7`) до публикации fallback-системки.
     *
     * Нужен для кейса "быстрый бой 1x1", когда fallback-сводка публикуется раньше,
     * чем стандартный боевой парсер успевает обновить `AppVars.LastBoiUron`.
     */
    private static void primeLastBoiDamageFromFinishHtmlIfNeeded(String html, String logId) {
        if (html == null || html.isEmpty() || logId == null || logId.isEmpty()) {
            return;
        }
        String currentDamage = AppVars.LastBoiUron == null ? "" : AppVars.LastBoiUron.trim();
        if (!currentDamage.isEmpty()) {
            return;
        }
        String[] list = extractJsArrayTokens(html, "var list = [[");
        if (list == null || list.length <= 10) {
            android.util.Log.d(TAG, "primeLastBoiDamageFromFinishHtmlIfNeeded: list missing, logId=" + logId);
            return;
        }
        int damage = 0;
        for (int idx = 6; idx <= 10; idx++) {
            damage += parseIntFromJsToken(list[idx], 0);
        }
        AppVars.LastBoiUron = String.valueOf(Math.max(0, damage));
        android.util.Log.d(TAG, "primeLastBoiDamageFromFinishHtmlIfNeeded: logId=" + logId
                + ", damage=" + AppVars.LastBoiUron);
    }
    /**
     * Извлекает CSV-токены JS-массива до первого закрывающего `]`.
     */
    private static String[] extractJsArrayTokens(String html, String prefix) {
        if (html == null || html.isEmpty() || prefix == null || prefix.isEmpty()) {
            return null;
        }
        String args = HelperStrings.subString(html, prefix, "]");
        if (args == null || args.isEmpty()) {
            return null;
        }
        return args.split(",");
    }
    /**
     * Безопасный parseInt для токена JS-массива с очисткой кавычек и пробелов.
     */
    private static int parseIntFromJsToken(String token, int fallback) {
        try {
            String normalized = token == null ? "" : token.replace("\"", "").trim();
            if (normalized.isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(normalized);
        } catch (Exception ignored) {
            return fallback;
        }
    }
    private static String extractBattleXpFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String xp = HelperStrings.subString(
                html,
                "Получено <font color=#CC0000>боевого</font> опыта: <b><font color=#CC0000>",
                "</font></b>.");
        if (xp != null && !xp.trim().isEmpty()) {
            String normalized = xp.replaceAll("[^0-9]", "");
            return normalized == null ? "" : normalized.trim();
        }
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "боевого</font>\\s*опыта:\\s*<b><font[^>]*>(\\d+)</font></b>",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            ).matcher(html);
            if (matcher.find()) {
                String value = matcher.group(1);
                return value == null ? "" : value.trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
    /**
     * Обёртка регистрации завершения боя по текущему log-id.
     *
     * Зависимости:
     * - Делегирует в {@link #registerFightEndByLogId(String, String)} с источником {@code fight_frame}.
     *
     * Назначение:
     * - Унифицировать точку вызова в местах, где доступен объект {@link LezFight}.
     */
    private static void registerFightEnd(LezFight fight) {
        String logId = fight != null ? fight.LogBoi : "";
        registerFightEndByLogId(logId, "fight_frame");
    }
    /**
     * Унифицированный учёт завершённого боя в статистике.
     * Дедуп по `AppVars.LastBoiEndLog`, чтобы не считать один и тот же бой повторно
     * при дублирующих загрузках верхнего фрейма/страницы завершения.
     */
    private static void registerFightEndByLogId(String logId, String source) {
        if (logId == null || logId.isEmpty()) return;
        if (!logId.equals(AppVars.LastBoiEndLog)) {
            AppVars.LastBoiEndLog = logId;
            ru.neverlands.abclient.utils.ChatStats.addFight();
            android.util.Log.d(TAG, "registerFightEnd: fight counted, source=" + source + ", LogBoi=" + logId);
        } else {
            android.util.Log.d(TAG, "registerFightEnd: skip duplicate, source=" + source + ", LogBoi=" + logId);
        }
    }
    /**
     * Логирует JavaScript-переменную из HTML боя.
     * Ищет паттерн: var NAME = [...] или var NAME = "..."
     */
    private static void logFightVariable(String html, String varName) {
        String pattern = "var " + varName;
        int idx = html.indexOf(pattern);
        if (idx < 0) {
            android.util.Log.d(TAG, "logFightVar: " + varName + " — NOT FOUND");
            return;
        }
        // Берём подстроку от "var NAME" до конца строки (до \n или ;)
        int end = html.indexOf("\n", idx);
        if (end < 0 || end > idx + 500) end = Math.min(idx + 500, html.length());
        String value = html.substring(idx, end).trim();
        android.util.Log.d(TAG, "logFightVar: " + varName + " = " + value);
    }
    /**
     * Порт C# `MainPhpInv`: парсинг инвентаря, упаковка дублей, сортировка и bulk-кнопки.
     *
     * Зависимости:
     * - строковые паттерны инвентаря из ПК-версии (`<tr><td bgcolor=#F5F5F5>`, long/short end-pattern);
     * - {@link InvEntry} как парсер одной записи и носитель compare/build-логики;
     * - профильные флаги {@code DoInvPack}/{@code DoInvSort};
     * - runtime-поля {@link AppVars}: `BulkDropThing`, `BulkSellThing`, `BulkSellSum`, `InvList`.
     *
     * Назначение:
     * - воспроизвести C#-поведение группировки одинаковых предметов при открытии инвентаря и категорий.
     */
    private static String mainPhpInv(String html) {
        try {
            final String patternStartInv = "</b></font></td></tr>";
            int pos = html.indexOf(patternStartInv);
            int posStartInv;
            if (pos == -1) {
                pos = html.indexOf("<tr><td bgcolor=#F5F5F5>");
                if (pos == -1) {
                    return html;
                }
                posStartInv = pos;
            } else {
                pos += patternStartInv.length();
                posStartInv = pos;
            }

            List<InvEntry> invList = new ArrayList<>();
            while (true) {
                final String patternStartTr = "<tr><td bgcolor=#F5F5F5>";
                if (pos + patternStartTr.length() > html.length()
                        || !html.regionMatches(true, pos, patternStartTr, 0, patternStartTr.length())) {
                    break;
                }

                final String patternEndTrLong = "<td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td></tr></table></td></tr></table></td></tr>";
                int posEnd = html.indexOf(patternEndTrLong, pos);
                if (posEnd == -1) {
                    final String patternEndTrShort = "<img src=http://image.neverlands.ru/1x1.gif width=1 height=5></td></tr></table></td></tr>";
                    posEnd = html.indexOf(patternEndTrShort, pos);
                    if (posEnd == -1) {
                        return html;
                    }
                    posEnd += patternEndTrShort.length();
                } else {
                    posEnd += patternEndTrLong.length();
                }

                String htmlEntry = html.substring(pos, posEnd);
                InvEntry invEntry = new InvEntry(htmlEntry);

                boolean isBulkDropMatch = !AppVars.BulkDropThing.isEmpty()
                        && !AppVars.BulkDropPrice.isEmpty()
                        && AppVars.BulkDropThing.equalsIgnoreCase(invEntry.DropThing == null ? "" : invEntry.DropThing)
                        && AppVars.BulkDropPrice.equals(invEntry.DropPrice == null ? "" : invEntry.DropPrice);

                if (invEntry.isExpired() || isBulkDropMatch) {
                    String dropThing = invEntry.DropThing == null ? "" : invEntry.DropThing;
                    String redirectMessage = "Выбрасывание предмета <b>&laquo;" + dropThing + "&raquo;</b>...";
                    return buildRedirectHtml(redirectMessage, invEntry.DropLink == null ? "" : invEntry.DropLink);
                }

                boolean isBulkSellMatch = invEntry.PssLink != null
                        && !invEntry.PssLink.isEmpty()
                        && !AppVars.BulkSellThing.isEmpty()
                        && invEntry.PssThing != null
                        && AppVars.BulkSellThing.equals(invEntry.PssThing)
                        && AppVars.BulkSellPrice == invEntry.PssPrice;

                if (isBulkSellMatch) {
                    AppVars.BulkSellSum += AppVars.BulkSellPrice;
                    String messageSell = "Продажа предмета <b>&laquo;" + invEntry.PssThing
                            + "&raquo;</b>. Выручка " + AppVars.BulkSellSum + " NV...";
                    return buildRedirectHtml(messageSell, invEntry.PssLink);
                }

                invList.add(invEntry);
                pos = posEnd;
            }

            int parsedCount = invList.size();
            boolean doPack = AppVars.Profile != null && AppVars.Profile.DoInvPack;
            boolean doPackDolg = AppVars.Profile != null && AppVars.Profile.DoInvPackDolg;
            boolean doSort = AppVars.Profile != null && AppVars.Profile.DoInvSort;
            android.util.Log.d(TAG, "INV_GROUP_TRACE parsed=" + parsedCount
                    + ", doPack=" + doPack
                    + ", doPackDolg=" + doPackDolg
                    + ", doSort=" + doSort);
            int sampleLimit = Math.min(5, invList.size());
            for (int sampleIndex = 0; sampleIndex < sampleLimit; sampleIndex++) {
                InvEntry sample = invList.get(sampleIndex);
                android.util.Log.d(TAG, "INV_GROUP_TRACE sample[" + sampleIndex + "]"
                        + " name=" + (sample.Name == null ? "" : sample.Name)
                        + ", image=" + (sample.Image == null ? "" : sample.Image)
                        + ", dolg=" + (sample.Dolg == null ? "" : sample.Dolg)
                        + ", pss=" + (sample.PssThing == null ? "" : sample.PssThing)
                        + ", drop=" + (sample.DropThing == null ? "" : sample.DropThing));
            }

            if (!AppVars.BulkDropThing.isEmpty()) {
                sendInventoryChatMessage("Выбрасывание пачки <b>&laquo;" + AppVars.BulkDropThing + "&raquo;</b> завершено.");
                AppVars.BulkDropThing = "";
            }
            if (!AppVars.BulkSellThing.isEmpty()) {
                sendInventoryChatMessage("Продажа пачки <b>&laquo;" + AppVars.BulkSellThing
                        + "&raquo;</b> завершена. Выручка составила <b>" + AppVars.BulkSellSum + "</b> NV.");
                AppVars.BulkSellThing = "";
            }

            if (invList.size() > 1 && AppVars.Profile != null && AppVars.Profile.DoInvPack) {
                for (int indexFirst = 0; indexFirst < invList.size() - 1; indexFirst++) {
                    for (int indexSecond = indexFirst + 1; indexSecond < invList.size(); indexSecond++) {
                        InvEntry firstEntry = invList.get(indexFirst);
                        InvEntry secondEntry = invList.get(indexSecond);
                        if (firstEntry.compareTo(secondEntry) != 0) {
                            continue;
                        }
                        if (firstEntry.compareDolg(secondEntry) > 0) {
                            try {
                                InvEntry selectedEntry = (InvEntry) secondEntry.clone();
                                selectedEntry.absorb(firstEntry);
                                invList.set(indexFirst, selectedEntry);
                            } catch (CloneNotSupportedException ignore) {
                                secondEntry.absorb(firstEntry);
                                invList.set(indexFirst, secondEntry);
                            }
                        } else {
                            firstEntry.absorb(secondEntry);
                        }
                        invList.remove(indexSecond);
                        indexSecond--;
                    }
                }
            }

            android.util.Log.d(TAG, "INV_GROUP_TRACE afterPack=" + invList.size()
                    + ", packed=" + Math.max(0, parsedCount - invList.size()));

            for (InvEntry entry : invList) {
                entry.addBulkSell();
                entry.addBulkDelete();
            }

            if (AppVars.Profile != null && AppVars.Profile.DoInvSort) {
                invList.sort(new InvComparer());
            }

            android.util.Log.d(TAG, "INV_GROUP_TRACE afterSort=" + invList.size());

            AppVars.InvList = new ArrayList<>(invList);

            StringBuilder sb = new StringBuilder();
            for (InvEntry entry : invList) {
                sb.append(entry.build());
            }

            StringBuilder rebuilt = new StringBuilder(html.substring(0, posStartInv));
            rebuilt.append(sb);
            rebuilt.append(html.substring(pos));
            return rebuilt.toString();
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            ru.neverlands.abclient.utils.DebugLogger.log("Error during mainPhpInv processing: \n" + sw);
            return html;
        }
    }

    /**
     * Публикует системную строку по операциям инвентаря в основной чат.
     *
     * Зависимости:
     * - {@link LocalBroadcastManager};
     * - action {@link AppVars#ACTION_ADD_CHAT_MESSAGE}.
     */
    /**
     * C# parity (`AppVars.Profile.DoStopOnDig`):
     * при появлении кнопки "Копать" останавливает Авто-Клад и сообщает пользователю.
     */
    private static void maybeStopAutoTreasureOnDig(String html) {
        if (html == null || html.isEmpty() || AppVars.Profile == null || !AppVars.Profile.DoStopOnDig) {
            return;
        }
        if (!html.contains(DIG_BUTTON_MARKER)) {
            return;
        }

        boolean autoTreasureActive = AppVars.DoSearchBox || AppVars.AutoMoving || AppVars.Profile.AutoDig;
        if (!autoTreasureActive) {
            return;
        }

        // Полная остановка текущего маршрута (аналог C# UpdateNavigatorOff).
        AppVars.AutoMoving = false;
        AppVars.AutoMovingDestinaton = null;
        AppVars.AutoMovingMapPath = null;
        AppVars.AutoMovingNextJump = null;
        AppVars.AutoMovingJumps = 0;
        AppVars.AutoMovingCityGate = ru.neverlands.abclient.model.CityGateType.None;

        boolean disabledViaManager = false;
        try {
            android.content.Context context = AppVars.getContext();
            if (context != null) {
                AutoFunctionsManager.getInstance(context).setAutoTreasureEnabled(false);
                disabledViaManager = true;
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE stop on dig: manager disable failed", e);
        }

        if (!disabledViaManager) {
            AppVars.DoSearchBox = false;
            if (AppVars.Profile != null) {
                AppVars.Profile.AutoDig = false;
                try {
                    android.content.Context context = AppVars.getContext();
                    if (context != null) {
                        AppVars.Profile.save(context);
                    }
                } catch (Exception saveEx) {
                    android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE stop on dig: profile save failed", saveEx);
                }
            }
            ExtMap.flushVisitedToDisk();
            AppVars.SearchBoxVisited.clear();
        }

        notifyTreasureFoundOnCurrentCell();
        playTreasureFoundSignal();
        android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE treasure marker detected -> stop auto treasure");
    }

    /**
     * Публикует системное сообщение в чат о том, что на текущей клетке найден клад.
     */
    private static void notifyTreasureFoundOnCurrentCell() {
        if (AppVars.getContext() == null) {
            return;
        }
        String messageHtml = buildServerChatTimeHtml()
                + "<font color=#cc0000><b>На текущей клетке обнаружен клад!</b></font>";
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }

    /**
     * Подаёт звуковой сигнал при обнаружении клада.
     * Используется как Android-аналог C# `EventSounds.PlayAlarm()`.
     */
    private static void playTreasureFoundSignal() {
        android.content.Context context = AppVars.getContext();
        if (context == null) {
            return;
        }
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            if (alarmUri == null) {
                return;
            }
            Ringtone ringtone = RingtoneManager.getRingtone(context, alarmUri);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE play alarm failed", e);
        }
    }

    private static void sendInventoryChatMessage(String messageHtml) {
        if (AppVars.getContext() == null || messageHtml == null || messageHtml.isEmpty()) {
            return;
        }
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }
}
