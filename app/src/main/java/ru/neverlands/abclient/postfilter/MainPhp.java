package ru.neverlands.abclient.postfilter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.text.SimpleDateFormat;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.model.InvComparer;
import ru.neverlands.abclient.model.InvEntry;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.manager.FastActionManager;
import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.manager.UnderAttackManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.HtmlUtils;
import ru.neverlands.abclient.utils.Russian;

// Главный пост‑фильтр main.php: бой, инвентарь, быстрые действия, системные сообщения.
public class MainPhp {
    private static final String TAG = "MainPhp";
    private static final Random RANDOM = new Random();
    private static final int AUTO_FINISH_MIN_DELAY_MS = 1000;
    private static final int AUTO_FINISH_EXTRA_DELAY_MS = 700;
    private static final long CAPTCHA_FALLBACK_TTL_MS = 30000L;
    private static volatile long lastAutoFinishRedirectAtMs = 0L;
    // Защита от повторного показа одного и того же диалога капчи завершения боя.
    private static volatile String lastFightCaptchaDialogKey = "";
    private static volatile long lastFightCaptchaDialogAtMs = 0L;
    private static volatile String lastCaptchaRejectKey = "";
    private static volatile long lastCaptchaRejectAtMs = 0L;

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

    // HTML-заглушка "ожидаем ход противника" с авто-обновлением.
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
     * Returns persisted Auto-Fight switch state from AutoFunctionsManager.
     * Fallbacks to profile flag if manager/context is not available.
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
     * Self-heals runtime desync where persisted Auto-Fight is ON, but AppVars.Autoboi is OFF.
     *
     * This recovery is intentionally blocked while CAPTCHA flow is active.
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

    // Отложенный redirect для завершения боя (чтобы не спамить сервер слишком частыми запросами).
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
     * 1) `AppVars.CodeAddress` (получен из `LezFight.ParseNonFight`, аналог C# `CodeAddress`),
     * 2) прямой `img src` в HTML (`extractCaptchaUrl`),
     * 3) token из `var fexp[4]` (`extractCaptchaUrlFromFexp`),
     * 4) последний перехваченный URL из interceptor (`AppVars.LastFightCaptchaImageUrl`) с TTL.
     *
     * Зависимости:
     * - `LezFight.BuildFightLink(...)`,
     * - `WebViewRequestInterceptor` (captured code.php URL/bytes),
     * - используется в auto/manual ветках `mainPhpFight(...)`.
     */
    private static String resolveFightCaptchaUrl(String html) {
        if (AppVars.CodeAddress != null && !AppVars.CodeAddress.isEmpty()) {
            return AppVars.CodeAddress;
        }

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
     * Invariant-парсинг числа (аналог NumberStyles.Any + InvariantCulture в C#).
     * Допускает кавычки вокруг значения.
     */
    private static Double tryParseDoubleInvariant(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim()
                .replace("\"", "")
                .replace("'", "");
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Центральный обработчик main.php (порт логики из C# MainPhp.cs).
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

        // Обработка быстрых действий (портировано из MainPhp.cs строки 1429-1619)
        // В C# FastAction обрабатывается ВНУТРИ MainPhp, а не в отдельном менеджере.
        // Алгоритм: MainPhpFindInv → BuildRedirect на инвентарь → MainPhpIsInv → MainPhpFast → BuildRedirect на категорию
        if (AppVars.FastNeed) {
            byte[] fastResult = processMainPhpFast(address, html);
            if (fastResult != null) {
                return fastResult;
            }
        }

        // Обработка страницы боя
        // magic_slots() — признак страницы боя (fight frame)
        // var fight_ty — признак верхнего фрейма с данными о противнике
        boolean isFightFrame = html.contains("magic_slots();");
        boolean isFightTopFrame = html.contains("var fight_ty");
        boolean isFightFinishAddress = address != null && address.contains("get_id=61") && address.contains("act=7");

        // Fallback-подсчёт завершённого поединка по URL завершения боя.
        // Нужен для случаев, когда сервер сразу переводит на `act=7` без повторного кадра `fight_ty`,
        // и обычный путь `mainPhpFight(...)->registerFightEnd(...)` не успевает сработать.
        if (isFightFinishAddress) {
            registerFightEndByLogId(AppVars.LastBoiLog, "fight_finish_url");
        }

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

        // Обработка инвентаря, основанная на стабильной версии из app_work
        if (html.contains("/invent/0.gif")) {
            html = mainPhpInv(html);
        }

        if (html.contains("var map = [[")) {
            html = MapAjax.process(html);
        }

        // ... other placeholders ...

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
        String invRedirect = mainPhpFindInv(html, filter);
        if (invRedirect != null) {
            android.util.Log.d(TAG, "processMainPhpFast: redirect на инвентарь: " + invRedirect);
            return Russian.getBytes(invRedirect);
        }

        // 2. Если мы НА инвентаре — проверяем категорию и ищем предмет
        if (mainPhpIsInv(html)) {
            String filterClean = filter.startsWith("&") ? filter.substring(1) : filter;

            // 2a. Сначала проверяем, на правильной ли мы вкладке категории.
            // Если address не содержит нужный фильтр (wca=28/wca=27),
            // перенаправляем на нужную категорию ПЕРЕД поиском предмета.
            // Это критично при 500+ предметах в инвентаре — поиск по всему
            // HTML (695KB) вместо отфильтрованной страницы (28KB) слишком медленный.
            if (!address.contains(filterClean)) {
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

    private static boolean containsIgnoreCase(String value, String token) {
        if (value == null || token == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    /**
     * Проверяет, что мы на странице инвентаря (аналог MainPhpIsInv в MainPhpDrink.cs:221-224).
     * Инвентарь содержит ссылку <a href="?im=0"><img...
     */
    private static boolean mainPhpIsInv(String html) {
        return html.contains("<a href=\"?im=0\"><img") || html.contains("<a href=?im=0><img");
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
        return ru.neverlands.abclient.utils.HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head><body>" +
                description +
                "<script language=\"JavaScript\">window.location = \"" + link + "\";</script></body></html>";
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

        // Унифицированный флаг "бой завершён":
        // - IsBoi=false: мы уже не в активной фазе ударов,
        // - IsWaitingForNextTurn=false: это не ожидание ответа противника.
        // Используется сразу в двух потоках:
        // 1) AutoBoi-поток (автозавершение/капча),
        // 2) ручной поток (показ капчи без авто-нажатия).
        boolean fightEnded = !fight.IsBoi && !fight.IsWaitingForNextTurn;
        if (fightEnded) {
            registerFightEnd(fight);
        }
        String fightCaptchaUrl = fightEnded ? resolveFightCaptchaUrl(html) : null;
        recoverAutoboiRuntimeStateIfNeeded(fightEnded, fightCaptchaUrl);

        // Синхронизация Timeout/Restoring как в C# MainPhpFight.cs.
        if (fightEnded && AppVars.Profile != null && AppVars.Profile.LezDoAutoboi) {
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
                            AppVars.Profile.LezDoWaitHp,
                            AppVars.Profile.LezWaitHp,
                            AppVars.Profile.LezDoWaitMa,
                            AppVars.Profile.LezWaitMa
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
                                AppVars.Profile.LezDoWaitHp,
                                AppVars.Profile.LezWaitHp,
                                AppVars.Profile.LezDoWaitMa,
                                AppVars.Profile.LezWaitMa
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
            AppVars.AutoboiReadyCompletedLog = "";
            fight.updateLastBoiFromLogs();
            notifyNewFight(fight);
            // C# аналог UnderAttack.Parse(html): анонс в чат с учётом LezSay (Chat/Clan/Pair/No).
            UnderAttackManager.parseAsync(html);
        }

        // Ветка завершения боя в AutoBoi:
        // - зависит от AppVars.Profile.LezDoAutoboi и AppVars.Autoboi==AutoboiOn,
        // - использует AppVars.FightLink, который формируется в LezFight.BuildFightLink(),
        // - при капче вызывает showFightCaptchaDialogOnce(...) и НЕ делает auto-submit.
        if (fightEnded
                && AppVars.Profile != null && AppVars.Profile.LezDoAutoboi
                && AppVars.Autoboi == AutoboiState.AutoboiOn) {
            android.util.Log.d(TAG, "mainPhpFight: FIGHT ENDED with autoboi ON - processing finish");

            String captchaUrl = fightCaptchaUrl;
            boolean needCaptcha = captchaUrl != null && !captchaUrl.isEmpty();
            String fightLink = AppVars.FightLink;

            if (needCaptcha && fightLink != null && !fightLink.isEmpty()) {
                android.util.Log.d(TAG, "mainPhpFight: CAPTCHA required, stopping autoboi and showing dialog: " + captchaUrl);

                // Важно: фикс восстановления AutoBoi после ручной капчи.
                // Если капча пришла в режиме AutoboiOn, запоминаем это состояние,
                // чтобы MainActivity после submit кода вернул `AutoboiOn`.
                AppVars.ResumeAutoboiAfterCaptcha = true;
                AppVars.Autoboi = AutoboiState.AutoboiOff;
                    AppVars.ContentMainPhp = html; // отрисовать форму с капчей
                    showFightCaptchaDialogOnce(captchaUrl, fightLink, fight.LogBoi);
                    // отдаем страницу с капчей напрямую
                    return html;
                }

            if (fightLink != null && !fightLink.isEmpty() && !fightLink.contains("????")) {
                long now = System.currentTimeMillis();
                long sinceLast = now - lastAutoFinishRedirectAtMs;
                if (sinceLast >= 0 && sinceLast < AUTO_FINISH_MIN_DELAY_MS) {
                    int waitMs = (int) (AUTO_FINISH_MIN_DELAY_MS - sinceLast) + 120;
                    android.util.Log.d(TAG, "mainPhpFight: throttling finish redirect, waitMs=" + waitMs);
                    return buildWaitForTurnAutoRefreshHtml(address, waitMs);
                }

                int redirectDelay = AUTO_FINISH_MIN_DELAY_MS + RANDOM.nextInt(AUTO_FINISH_EXTRA_DELAY_MS + 1);
                if (redirectDelay >= 0) {
                    lastAutoFinishRedirectAtMs = now;
                    AppVars.FightLink = "";
                    return buildDelayedRedirectHtml("Завершение боя", fightLink, redirectDelay);
                }

                AppVars.FightLink = "";
                return Russian.getString(Filter.buildRedirect("Завершение боя", fightLink));
            }
            // FightLink пустой или содержит "????" - делаем редирект на main.php
            // Это нужно для обновления верхнего фрейма и предотвращения белого экрана

            android.util.Log.d(TAG, "mainPhpFight: FightLink missing, redirecting to main.php");
            AppVars.FightLink = "";
            int redirectDelayToMain = AUTO_FINISH_MIN_DELAY_MS + RANDOM.nextInt(AUTO_FINISH_EXTRA_DELAY_MS + 1);
            if (redirectDelayToMain >= 0) {
                return buildDelayedRedirectHtml("Завершение боя - обновление", "main.php", redirectDelayToMain);
            }
            return Russian.getString(Filter.buildRedirect("Завершение боя - обновление", "main.php"));
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
            if (!shouldAutoRefresh && AppVars.Profile != null && AppVars.Profile.LezDoAutoboi
                    && AppVars.Autoboi == AutoboiState.AutoboiOn) {
                // Для AutoBoi нужно продолжать обновлять фрейм, иначе после 1 удара остановимся на ходе противника.
                shouldAutoRefresh = true;
            }

            if (shouldAutoRefresh) {
                int delay = 1200 + RANDOM.nextInt(900); // 1200-2100ms
                android.util.Log.d(TAG, "mainPhpFight: auto-refresh waiting enabled, reloading after " + delay + "ms: " + address);
                return buildWaitForTurnAutoRefreshHtml(address, delay);
            }

            android.util.Log.d(TAG, "mainPhpFight: AutoRefresh disabled, returning original content");
            return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
        }

        // Проверяем, включен ли автобой в профиле
        if (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi) {
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
            android.util.Log.d(TAG, "mainPhpFight: LezDoAutoboi disabled or Profile is null");
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

        // Временная метка "Нападение" должна быть в серверном времени (как в C#).
        // Используем ServDiff, вычисленный из but.php (hour/min/sec).
        long serverMs = System.currentTimeMillis();
        if (AppVars.Profile != null && AppVars.Profile.ServDiff != Long.MIN_VALUE) {
            serverMs = serverMs - AppVars.Profile.ServDiff;
        }
        Date serverTime = new Date(serverMs);
        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(serverTime);
        String timeHtml = "<font class=chattime>&nbsp;" + timeStr + "&nbsp;</font> ";

        String message = "Нападение: " + foes + foeType;

        String messageHtml =
                timeHtml +
                "<b><font color=#cc0000>Нападение:</font></b> " +
                "<font color=#004bbb>" + foes + "</font>" +
                foeType;

        android.util.Log.d(TAG, "notifyNewFight: " + message);

        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
    }

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

    // Этот метод основан на стабильной и производительной реализации из app_work
    private static String mainPhpInv(String html) {
        try {
            Document doc = Jsoup.parse(html);
            
            // Ищем контейнер с инвентарем. Используем более гибкий селектор.
            Elements inventoryContainers = doc.select("td[background*='i_bg_2.gif']");
            if (inventoryContainers.isEmpty()) {
                return html; // Не нашли инвентарь, ничего не делаем
            }
            
            Element inventoryContainer = inventoryContainers.first();
            if (inventoryContainer == null) {
                return html;
            }

            // Ищем все таблицы внутри контейнера, которые могут быть предметами
            Elements tables = inventoryContainer.select("table");
            List<InvEntry> invList = new ArrayList<>();

            for (Element table : tables) {
                // Предмет в инвентаре обычно имеет картинку из /weapon/ или /invent/
                String tableHtml = table.html();
                if (tableHtml.contains("/weapon/") || tableHtml.contains("/invent/")) {
                    // В оригинальном коде брался parent().parent().parent(), 
                    // что соответствует строке таблицы инвентаря.
                    Element row = table;
                    while (row != null && !row.tagName().equals("tr")) {
                        row = row.parent();
                    }
                    if (row != null) {
                        invList.add(new InvEntry(row));
                    }
                }
            }

            if (invList.isEmpty()) {
                return html;
            }

            // Логика группировки (упаковки) предметов
            if (AppVars.Profile != null && AppVars.Profile.DoInvPack) {
                for (int i = 0; i < invList.size() - 1; i++) {
                    for (int j = i + 1; j < invList.size(); j++) {
                        if (invList.get(i).compareTo(invList.get(j)) == 0) {
                            if (invList.get(i).compareDolg(invList.get(j)) > 0) {
                                invList.set(i, invList.get(j));
                            }
                            invList.get(i).inc();
                            invList.remove(j);
                            j--;
                        }
                    }
                }
            }

            // Добавляем кастомные кнопки
            for (InvEntry entry : invList) {
                entry.addBulkSell();
                entry.addBulkDelete();
            }

            // Логика сортировки
            if (AppVars.Profile != null && AppVars.Profile.DoInvSort) {
                Collections.sort(invList, new InvComparer());
            }

            // Сохраняем в AppVars для доступа из других компонентов
            AppVars.InvList = new ArrayList<>(invList);

            // Пересобираем HTML инвентаря
            StringBuilder newHtml = new StringBuilder();
            newHtml.append("<tr><td align=center bgcolor=#f5f5f5>");
            for (InvEntry entry : invList) {
                newHtml.append(entry.build());
            }
            newHtml.append("</td></tr>");

            // Заменяем содержимое контейнера инвентаря
            inventoryContainer.parent().parent().html(newHtml.toString());
            
            return doc.outerHtml();
        } catch (Exception e) {
            // В случае любой ошибки парсинга, возвращаем исходный HTML, чтобы не уронить приложение
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String exceptionAsString = sw.toString();
            ru.neverlands.abclient.utils.DebugLogger.log("Error during mainPhpInv processing: \n" + exceptionAsString);
            return html;
        }
    }
}
