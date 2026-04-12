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
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.SimpleDateFormat;
import android.util.Log;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.model.InvComparer;
import ru.neverlands.abclient.model.InvEntry;
import ru.neverlands.abclient.model.ParsedDressed;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.manager.CharacterVitalsManager;
import ru.neverlands.abclient.manager.FastActionManager;
import ru.neverlands.abclient.manager.NeverApi;
import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.manager.UnderAttackManager;
import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.HtmlUtils;
import ru.neverlands.abclient.utils.Russian;
import ru.neverlands.abclient.utils.SessionManager;
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
    private static final long CAPTCHA_FALLBACK_TTL_MS = 5000L;
    private static final long AUTO_SKIN_KNIFE_RECHECK_INTERVAL_MS = 60_000L;
    private static final String AUTO_CURE_POISON_POTION_NAME = "Зелье Лечения Отравлений";
    private static final String AUTO_CURE_SELF_ELIXIR_NAME = "Эликсир Мгновенного Исцеления";
    private static final String MAP_HEAVY_INJURY_POPUP_MARKER = "Вы не можете перемещаться! У Вас тяж";
    private static final int POISON_INDEX = 0;
    private static final int LIGHT_WOUND_INDEX = 1;
    private static final int MEDIUM_WOUND_INDEX = 2;
    private static final int HEAVY_WOUND_INDEX = 3;
    // На части серверных ответов вкладка инвентаря отдается промежуточным шаблоном (без содержимого предметов).
    // Для быстрых действий по эликсирам даем расширенное окно ретраев, чтобы не падать в ложный timeout.
    private static final long WTIME_SYNC_LOG_GUARD_MS = 1500L;
    private static final long SERVER_NOTICE_CHAT_DEDUP_MS = 1500L;
    /**
     * Окно подтверждения завершения боя на probe-кадрах авто-боя.
     *
     * Правило:
     * - если кадр пришёл с `ab_reload_probe`/`ab_bg_probe` и выглядит как завершение,
     *   клиент не делает мгновенный `DIRECT_FINISH_LINK`, а требует повторного подтверждения
     *   тем же кандидатом (по LogBoi или ссылке завершения) в пределах этого окна.
     *
     * Зависимости:
     * - `isAutoFightProbeFinishConfirmed(...)`;
     * - `buildAutoFightProbeFinishCandidateKey(...)`;
     * - ветка выбора `FinishFlowDecision` в `FightAuto.processFight(...)`.
     */
    private static final long AUTO_FIGHT_PROBE_FINISH_CONFIRM_WINDOW_MS = 4500L;
    private static volatile long lastAutoDrinkTriggerAtMs = 0L;
    private static volatile long lastWtimeSyncLogAtMs = 0L;
    private static volatile long lastAutoDrinkBlazTriggerAtMs = 0L;
    private static volatile long lastMapHeavyInjurySyncAtMs = 0L;
    private static volatile long lastServerNoticeAtMs = 0L;
    private static volatile String lastServerNoticeKey = "";
    // One-shot post-fight marker:
    // after finish-link redirect to plain main.php we allow auto-drink check on ближайших страницах
    // персонажа/инвентаря (go=inf/go=inv/im=*), если "чистый" main.php не попал в Filter.process().
    private static volatile boolean autoDrinkPostFightSyncPending = false;
    private static volatile long autoDrinkPostFightSyncPendingSinceMs = 0L;
    // Защита от повторного показа одного и того же диалога капчи завершения боя.
    private static volatile String lastFightCaptchaDialogKey = "";
    private static volatile long lastFightCaptchaDialogAtMs = 0L;
    private static volatile String lastCaptchaRejectKey = "";
    private static volatile long lastCaptchaRejectAtMs = 0L;
    // Дедуп публикации результата боя:
    // - победа: один раз на LogBoi, чтобы повторные act=7/vcode не спамили "Победа за ...";
    // - лут: один раз на (LogBoi + тип + список), чтобы не терять сообщение о добыче и не дублировать его.
    private static volatile String lastFightResultWinnerBroadcastKey = "";
    private static volatile String lastFightResultLootBroadcastKey = "";
    private static volatile String lastFightSummaryBroadcastKey = "";

    /**
     * Последний кандидат завершения боя, зафиксированный на probe-кадре.
     *
     * Используется как защита от ложного финиша на переходном кадре:
     * - сначала фиксируется кандидат;
     * - на следующем probe-кадре с тем же кандидатом разрешается завершение.
     */
    private static volatile String lastAutoFightProbeFinishCandidateKey = "";
    private static volatile long lastAutoFightProbeFinishCandidateAtMs = 0L;
    /**
     * Bridge-адаптер инфраструктурных helper-методов MainPhp для модуля {@link FightAuto}.
     *
     * Назначение:
     * - после выноса боевой логики оставить в MainPhp только инфраструктуру и делегирование;
     * - исключить дублирование существующих утилит (парсинг URL, popup-капча, chat notify, finish helpers).
     *
     * Правило:
     * - бизнес-ветвления боя живут в FightAuto;
     * - MainPhp через этот bridge предоставляет только необходимые внешние зависимости.
     */
    private static final FightAuto.Host FIGHT_AUTO_HOST = new FightAuto.Host() {
        @Override
        public void logFightVariable(String html, String variableName) {
            MainPhp.logFightVariable(html, variableName);
        }

        @Override
        public FightAuto.InsHpSnapshot parseInsHpSnapshot(String html) {
            InsHpSnapshot source = MainPhp.parseInsHpSnapshot(html);
            if (source == null) {
                return null;
            }
            FightAuto.InsHpSnapshot mapped = new FightAuto.InsHpSnapshot();
            mapped.curHp = source.curHp;
            mapped.maxHp = source.maxHp;
            mapped.curMa = source.curMa;
            mapped.maxMa = source.maxMa;
            return mapped;
        }

        @Override
        public void clearAutoFightProbeFinishCandidate() {
            MainPhp.clearAutoFightProbeFinishCandidate();
        }

        @Override
        public boolean isAutoFightProbeAddress(String address) {
            return MainPhp.isAutoFightProbeAddress(address);
        }

        @Override
        public String resolveFightCaptchaUrl(String html) {
            return MainPhp.resolveFightCaptchaUrl(html);
        }

        @Override
        public boolean isFightFrameHtml(String html) {
            return MainPhp.isFightFrameHtml(html);
        }

        @Override
        public void registerFightEnd(LezFight fight) {
            MainPhp.registerFightEnd(fight);
        }

        @Override
        public void publishFightResultFromLogsIfNeeded(String html, String address, String logIdHint) {
            MainPhp.publishFightResultFromLogsIfNeeded(html, address, logIdHint);
        }

        @Override
        public void recoverAutoboiRuntimeStateIfNeeded(boolean fightEnded, String fightCaptchaUrl) {
            MainPhp.recoverAutoboiRuntimeStateIfNeeded(fightEnded, fightCaptchaUrl);
        }

        @Override
        public boolean isAutoFightEnabledByPreference() {
            return MainPhp.isAutoFightEnabledByPreference();
        }

        @Override
        public String buildRestoringStatusHtml(String address,
                                               int delayMs,
                                               long waitMs,
                                               int curHp,
                                               int maxHp,
                                               int curMa,
                                               int maxMa,
                                               boolean waitHpEnabled,
                                               int waitHpPercent,
                                               boolean waitMaEnabled,
                                               int waitMaPercent) {
            return MainPhp.buildRestoringStatusHtml(
                    address,
                    delayMs,
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

        @Override
        public void notifyNewFight(LezFight fight) {
            MainPhp.notifyNewFight(fight);
        }

        @Override
        public boolean isAutoSkinEnabledByPreference() {
            return MainPhp.isAutoSkinEnabledByPreference();
        }

        @Override
        public String mainPhpRaz(String html) {
            return MainPhp.mainPhpRaz(html);
        }

        @Override
        public String buildDelayedRedirectHtml(String description, String link, int delayMs) {
            return MainPhp.buildDelayedRedirectHtml(description, link, delayMs);
        }

        @Override
        public String extractFightFinishLinkFromHtml(String html, boolean withCaptchaPlaceholder) {
            return MainPhp.extractFightFinishLinkFromHtml(html, withCaptchaPlaceholder);
        }

        @Override
        public String extractFightCleanFinishLinkFromHtml(String html) {
            return MainPhp.extractFightCleanFinishLinkFromHtml(html);
        }

        @Override
        public String normalizeNeverlandsMainLink(String link) {
            return MainPhp.normalizeNeverlandsMainLink(link);
        }

        @Override
        public boolean isAutoFightProbeFinishConfirmed(String logBoi, String fightLink) {
            return MainPhp.isAutoFightProbeFinishConfirmed(logBoi, fightLink);
        }

        @Override
        public void showFightCaptchaDialogOnce(String captchaUrl, String finishUrl, String logBoi) {
            MainPhp.showFightCaptchaDialogOnce(captchaUrl, finishUrl, logBoi);
        }

        @Override
        public String getUrlParam(String url, String paramName) {
            return MainPhp.getUrlParam(url, paramName);
        }

        @Override
        public void notifyCaptchaRejectedOnce(String submittedCode, String submittedVcode) {
            MainPhp.notifyCaptchaRejectedOnce(submittedCode, submittedVcode);
        }

        @Override
        public String buildInPlaceFightAutoRefreshHtml(String html, String reloadUrl, int delayMs) {
            return MainPhp.buildInPlaceFightAutoRefreshHtml(html, reloadUrl, delayMs);
        }

        @Override
        public void notifyFightStopped(LezFight fight) {
            MainPhp.notifyFightStopped(fight);
        }

        @Override
        public List<String> splitJsTopLevelCsv(String raw) {
            return MainPhp.splitJsTopLevelCsv(raw);
        }

        @Override
        public String trimJsToken(String token) {
            return MainPhp.trimJsToken(token);
        }

        @Override
        public String escapeHtmlAttr(String value) {
            return MainPhp.escapeHtmlAttr(value);
        }
    };
    /**
     * Bridge-адаптер инфраструктурных helper-методов MainPhp для модуля {@link TreasureDig}.
     *
     * Это позволяет держать бизнес-логику Auto-Клада в отдельном файле без дублирования
     * уже существующих служебных методов (редиректы, инвентарь, чат-сообщения).
     */
    /**
     * FastAction host bridge for delegating MainPhp infrastructure helpers
     * into {@link FastActionManager#processMainPhpFast(String, String, FastActionManager.MainPhpFastHost)}.
     */
    private static final FastActionManager.MainPhpFastHost FAST_ACTION_HOST = new FastActionManager.MainPhpFastHost() {
        @Override
        public boolean isAttackFastId(String fastId) {
            return MainPhp.isAttackFastId(fastId);
        }

        @Override
        public String getInventoryFilter(String fastId) {
            return MainPhp.getInventoryFilter(fastId);
        }

        @Override
        public boolean isFightFrameHtml(String html) {
            return MainPhp.isFightFrameHtml(html);
        }

        @Override
        public String mainPhpFindInvWithFallback(String html, String filter, String address) {
            return MainPhp.mainPhpFindInvWithFallback(html, filter, address);
        }

        @Override
        public boolean mainPhpIsInv(String html) {
            return MainPhp.mainPhpIsInv(html);
        }

        @Override
        public boolean isInventoryAddress(String address) {
            return MainPhp.isInventoryAddress(address);
        }

        @Override
        public boolean inventoryAddressMatchesFilter(String address, String filter) {
            return MainPhp.inventoryAddressMatchesFilter(address, filter);
        }

        @Override
        public int parseUrlParamInt(String url, String paramName, int fallback) {
            return MainPhp.parseUrlParamInt(url, paramName, fallback);
        }

        @Override
        public String appendOrReplaceUrlParam(String url, String paramName, String paramValue) {
            return MainPhp.appendOrReplaceUrlParam(url, paramName, paramValue);
        }

        @Override
        public String buildFastItemNotFoundMessage(String fastId) {
            return MainPhp.buildFastItemNotFoundMessage(fastId);
        }

        @Override
        public void sendInventoryChatMessage(String messageHtml) {
            MainPhp.sendInventoryChatMessage(messageHtml);
        }
    };

    private static final TreasureDig.Host TREASURE_DIG_HOST = new TreasureDig.Host() {
        @Override
        public String mainPhpFindInvWithFallback(String html, String filter, String address) {
            return MainPhp.mainPhpFindInvWithFallback(html, filter, address);
        }

        @Override
        public String mainPhpFindMapReturnForAutoMoving(String html) {
            return MainPhp.mainPhpFindMapReturnForAutoMoving(html);
        }

        @Override
        public boolean mainPhpIsInv(String html) {
            return MainPhp.mainPhpIsInv(html);
        }

        @Override
        public boolean isInventoryAddress(String address) {
            return MainPhp.isInventoryAddress(address);
        }

        @Override
        public boolean inventoryAddressMatchesFilter(String address, String filter) {
            return MainPhp.inventoryAddressMatchesFilter(address, filter);
        }

        @Override
        public boolean hasInventoryRows(String html) {
            return MainPhp.hasInventoryRows(html);
        }

        @Override
        public int parseUrlParamInt(String url, String paramName, int fallback) {
            return MainPhp.parseUrlParamInt(url, paramName, fallback);
        }

        @Override
        public String normalizeNeverlandsMainLink(String link) {
            return MainPhp.normalizeNeverlandsMainLink(link);
        }

        @Override
        public String appendOrReplaceUrlParam(String url, String paramName, String paramValue) {
            return MainPhp.appendOrReplaceUrlParam(url, paramName, paramValue);
        }

        @Override
        public String buildRedirectHtml(String description, String link) {
            return MainPhp.buildRedirectHtml(description, link);
        }

        @Override
        public void sendInventoryChatMessage(String messageHtml) {
            MainPhp.sendInventoryChatMessage(messageHtml);
        }

        @Override
        public String buildServerChatTimeHtml() {
            return MainPhp.buildServerChatTimeHtml();
        }

        @Override
        public String escapeHtmlAttr(String value) {
            return MainPhp.escapeHtmlAttr(value);
        }

        @Override
        public List<TreasureDig.WearInvEntry> getWearInvList(String html) {
            List<WearInvEntry> source = MainPhp.getWearInvList(html);
            if (source == null || source.isEmpty()) {
                return Collections.emptyList();
            }
            List<TreasureDig.WearInvEntry> mapped = new ArrayList<>(source.size());
            for (WearInvEntry entry : source) {
                if (entry == null) {
                    continue;
                }
                mapped.add(new TreasureDig.WearInvEntry(entry.name, entry.wearLink));
            }
            return mapped;
        }
    };
    /**
     * Лёгкая DTO-запись предмета инвентаря для wear-логики (порт `GetInvList` + `InvEntry.WearLink` из C#).
     */
    static final class WearInvEntry {
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
            String msg = "isAutoFightEnabledByPreference: fallback to profile flag";
            AppLog.w(TAG, msg);
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
            String msg = "recoverAutoboiRuntimeStateIfNeeded: skip (captcha flow active)";
            AppLog.d(TAG, msg);
            return;
        }
        AppVars.Autoboi = AutoboiState.AutoboiOn;
        String msg = "recoverAutoboiRuntimeStateIfNeeded: restored AppVars.Autoboi -> AutoboiOn";
        AppLog.w(TAG, msg);
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
            String msg = "resolveFightCaptchaUrl: built from fexp[4]: " + captchaUrlFromFexp;
            AppLog.d(TAG, msg);
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
                String msg = "resolveFightCaptchaUrl: use fallback from interceptor, ageMs=";
                AppLog.d(TAG, msg);
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
            AppLog.e(TAG, "extractCaptchaUrlFromFexp error", e);
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
        String vcode = extractFightCleanVcodeFromFexp(html);
        // 4) Crash-frame fallback: если fexp отсутствует, берём vcode из fight_ty[5].
        if (vcode == null || vcode.isEmpty()) {
            vcode = extractFightCleanVcodeFromFightTy(html);
        }
        if (vcode == null || vcode.isEmpty()) {
            return null;
        }
        String finishSt = resolveFightFinishStateForAct5(html);
        String finishLink = "main.php?get_id=61&act=5&st=" + finishSt + "&vcode=" + vcode;
        return normalizeNeverlandsMainLink(finishLink);
    }

    private static String extractFightCleanVcodeFromFexp(String html) {
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
        return vcode;
    }

    private static String extractFightCleanVcodeFromFightTy(String html) {
        String rawFightTy = HelperStrings.subString(html, "var fight_ty = [", "];" );
        if (rawFightTy == null || rawFightTy.isEmpty()) {
            return null;
        }
        List<String> fightTy = splitJsTopLevelCsv(rawFightTy);
        if (fightTy.size() <= 5) {
            return null;
        }
        String vcode = trimJsToken(fightTy.get(5));
        if (vcode == null || vcode.isEmpty() || "0".equals(vcode)) {
            return null;
        }
        return vcode;
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
    /**
     * Экранирует строку для безопасной подстановки в HTML-атрибут.
     *
     * Зависимости:
     * - Используется при построении динамических HTML-форм (например, auto-submit для FEND).
     *
     * Назначение:
     * - Исключить ломание разметки из-за спецсимволов ({@code & " < >}) в значениях input-полей.
     */
    static String escapeHtmlAttr(String value) {
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
     * Порт `MainPhpInsHp.cs` (C# -> Android): синхронизация vitals из `ins_HP(...)`.
     *
     * Что делает:
     * - парсит снимок `ins_HP(curHp,maxHp,curMa,maxMa,intHp,intMa)`;
     * - передает значения в единый менеджер `CharacterVitalsManager`;
     * - фиксирует trace-лог с интервалами регена.
     *
     * Зависимости:
     * - {@link #parseInsHpSnapshot(String)} — извлечение аргументов из HTML;
     * - {@link CharacterVitalsManager#updateFromInsHpSnapshot(int, int, int, int, double, double, String)} —
     *   централизованная запись HP/MA/интервалов;
     * - боевые подсистемы (например, LezFight), которые читают PersIntHP/PersIntMA для расчета восстановления.
     */
    private static void mainPhpInsHp(String html) {
        try {
            InsHpSnapshot snapshot = parseInsHpSnapshot(html);
            if (snapshot == null) return;
            CharacterVitalsManager.Snapshot vitals = CharacterVitalsManager.updateFromInsHpSnapshot(
                    snapshot.curHp,
                    snapshot.maxHp,
                    snapshot.curMa,
                    snapshot.maxMa,
                    snapshot.intHp,
                    snapshot.intMa,
                    "MainPhp.mainPhpInsHp"
            );
            String msg = "mainPhpInsHp: parsed hpInt=";
            AppLog.d(TAG, msg);
        } catch (Exception e) {
            String msg = "mainPhpInsHp error";
            AppLog.e(TAG, msg, e);
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
        // C# parity (MainPhpDrinkHpMa.cs):
        // 1) first try `var inshp = [curHp,maxHp,curMa,maxMa,intHp,intMa];`
        // 2) fallback to `ins_HP(curHp,maxHp,curMa,maxMa,intHp,intMa);`
        int varPos = htmlLower.indexOf("var inshp");
        if (varPos != -1) {
            int bracketStart = html.indexOf('[', varPos);
            int bracketEnd = html.indexOf("];", bracketStart);
            if (bracketStart != -1 && bracketEnd != -1 && bracketEnd > bracketStart) {
                InsHpSnapshot fromVar = parseInsHpSnapshotArgs(html.substring(bracketStart + 1, bracketEnd));
                if (fromVar != null) {
                    return fromVar;
                }
            }
        }
        int start = htmlLower.indexOf("ins_hp(");
        if (start == -1) {
            return null;
        }
        start += "ins_hp(".length();
        int end = html.indexOf(')', start);
        if (end == -1 || end <= start) {
            return null;
        }
        return parseInsHpSnapshotArgs(html.substring(start, end));
    }

    private static InsHpSnapshot parseInsHpSnapshotArgs(String args) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        String[] parts = args.split(",");
        if (parts.length != 6) {
            String msg = "parseInsHpSnapshot: unexpected args count=";
            AppLog.d(TAG, msg);
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
            String msg = "AUTO_DRINK_TRACE skip: auto-fight disabled in preferences";
            AppLog.d(TAG, msg);
            return;
        }
        if (isFightFrame || isFightTopFrame) {
            return;
        }
        // C# parity: проверяем автопитьё на любом небоевом кадре, где есть валидный inshp/ins_HP snapshot.
        // Для post-fight fallback разрешаем отдельный путь через unified vitals, если в текущем HTML snapshot нет.
        InsHpSnapshot pageSnapshot = parseInsHpSnapshot(html);
        boolean hasPageSnapshot = pageSnapshot != null
                && (pageSnapshot.maxHp > 0 || pageSnapshot.maxMa > 0);
        // Логируем ins_HP snapshot с HTML-страницы для сравнения с info.cgi
        if (hasPageSnapshot) {
            AppLog.d(TAG, "AUTO_DRINK_TRACE page ins_HP: hp="
                    + pageSnapshot.curHp + "/" + pageSnapshot.maxHp
                    + ", ma=" + pageSnapshot.curMa + "/" + pageSnapshot.maxMa
                    + ", intHp=" + pageSnapshot.intHp + ", intMa=" + pageSnapshot.intMa);
            FileLogger.trace(TAG, "AUTO_DRINK_TRACE page ins_HP: hp="
                    + pageSnapshot.curHp + "/" + pageSnapshot.maxHp
                    + ", ma=" + pageSnapshot.curMa + "/" + pageSnapshot.maxMa
                    + ", intHp=" + pageSnapshot.intHp + ", intMa=" + pageSnapshot.intMa);
        }
        boolean allowPostFightFollowup = autoDrinkPostFightSyncPending
                && isPostFightAutoDrinkFollowupAddress(address);
        if (!hasPageSnapshot && !allowPostFightFollowup) {
            String msg = "AUTO_DRINK_TRACE skip: no inshp snapshot on page, address=";
            AppLog.d(TAG, msg);
            return;
        }
        if (allowPostFightFollowup) {
            CharacterVitalsManager.Snapshot preFollowupVitals = CharacterVitalsManager.snapshot();
            long preFollowupAgeMs = preFollowupVitals.updatedAtMs > 0
                    ? Math.max(0L, System.currentTimeMillis() - preFollowupVitals.updatedAtMs) : -1L;
            String msg = "AUTO_DRINK_TRACE allow post-fight follow-up: address=" + address
                    + ", currentVitals: hp=" + preFollowupVitals.curHp + "/" + preFollowupVitals.maxHp
                    + ", ma=" + preFollowupVitals.curMa + "/" + preFollowupVitals.maxMa
                    + ", tied=" + preFollowupVitals.tied
                    + ", vitalsSource=" + preFollowupVitals.source
                    + ", vitalsAgeMs=" + preFollowupAgeMs;
            AppLog.d(TAG, msg);
            FileLogger.trace(TAG, msg);
        }
        if (AppVars.FastNeed) {
            String msg = "AUTO_DRINK_TRACE skip: FastNeed active, fastId=";
            AppLog.d(TAG, msg);
            return;
        }
        if (address != null && address.contains("get_id=43")) {
            String msg = "AUTO_DRINK_TRACE skip: get_id=43 action page";
            AppLog.d(TAG, msg);
            return;
        }
        if (AppVars.IsFightCaptchaDialogVisible) {
            String msg = "AUTO_DRINK_TRACE skip: captcha dialog visible";
            AppLog.d(TAG, msg);
            return;
        }
        InsHpSnapshot snapshot = null;
        String snapshotSource = "";
        if (allowPostFightFollowup) {
            InsHpSnapshot pinfoSnapshot = tryBuildAutoDrinkSnapshotFromPinfo();
            if (pinfoSnapshot != null) {
                snapshot = pinfoSnapshot;
                snapshotSource = "pinfo/post-fight";
            }
        }
        if (snapshot == null) {
            snapshot = pageSnapshot;
            snapshotSource = "ins_HP/page";
        }
        if (snapshot == null || (snapshot.maxHp <= 0 && snapshot.maxMa <= 0)) {
            CharacterVitalsManager.Snapshot vitals = CharacterVitalsManager.snapshot();
            if (vitals.maxHp > 0 || vitals.maxMa > 0) {
                InsHpSnapshot fallback = new InsHpSnapshot();
                fallback.curHp = vitals.curHp;
                fallback.maxHp = vitals.maxHp;
                fallback.curMa = vitals.curMa;
                fallback.maxMa = vitals.maxMa;
                fallback.intHp = vitals.intHp;
                fallback.intMa = vitals.intMa;
                snapshot = fallback;
                long ageMs = vitals.updatedAtMs > 0L
                        ? Math.max(0L, System.currentTimeMillis() - vitals.updatedAtMs)
                        : -1L;
                snapshotSource = "CharacterVitalsManager(" + vitals.source + ")";
                android.util.Log.d(TAG, "AUTO_DRINK_TRACE fallback snapshot: hp="
                        + snapshot.curHp + "/" + snapshot.maxHp
                        + ", ma=" + snapshot.curMa + "/" + snapshot.maxMa
                        + ", ageMs=" + ageMs
                        + ", source=" + vitals.source);
                FileLogger.trace(TAG, "AUTO_DRINK_TRACE fallback snapshot: hp="
                        + snapshot.curHp + "/" + snapshot.maxHp
                        + ", ma=" + snapshot.curMa + "/" + snapshot.maxMa
                        + ", ageMs=" + ageMs
                        + ", source=" + vitals.source);
            } else {
                String msg = "AUTO_DRINK_TRACE skip: ins_HP snapshot missing or invalid, vitals empty";
                AppLog.d(TAG, msg);
                return;
            }
        }
        // Как только получили валидный снимок после finish-link синхронизации — считаем one-shot выполненным.
        autoDrinkPostFightSyncPending = false;
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
                    + " (enabled=" + AppVars.Profile.LezDoDrinkMa + "), address=" + address
                    + ", snapshotSource=" + snapshotSource);
            FileLogger.trace(TAG, "AUTO_DRINK_TRACE no-trigger: hp="
                    + String.format(Locale.US, "%.1f", hpPercent) + "%/" + AppVars.Profile.LezDrinkHp
                    + " (enabled=" + AppVars.Profile.LezDoDrinkHp + "), ma="
                    + String.format(Locale.US, "%.1f", maPercent) + "%/" + AppVars.Profile.LezDrinkMa
                    + " (enabled=" + AppVars.Profile.LezDoDrinkMa + "), address=" + address
                    + ", snapshotSource=" + snapshotSource);
            return;
        }
        long now = System.currentTimeMillis();
        long sinceLastTrigger = now - lastAutoDrinkTriggerAtMs;
        if (sinceLastTrigger >= 0 && sinceLastTrigger < AUTO_DRINK_TRIGGER_COOLDOWN_MS) {
            android.util.Log.d(TAG, "AUTO_DRINK_TRACE skip cooldown: sinceLastMs=" + sinceLastTrigger
                    + ", hpBelow=" + hpBelow + ", maBelow=" + maBelow);
            FileLogger.trace(TAG, "AUTO_DRINK_TRACE skip cooldown: sinceLastMs=" + sinceLastTrigger
                    + ", hpBelow=" + hpBelow + ", maBelow=" + maBelow);
            return;
        }
        lastAutoDrinkTriggerAtMs = now;
        android.util.Log.d(TAG, "AUTO_DRINK_TRACE trigger restore elixir: hp="
                + snapshot.curHp + "/" + snapshot.maxHp + " (" + String.format(Locale.US, "%.1f", hpPercent) + "%)"
                + ", ma=" + snapshot.curMa + "/" + snapshot.maxMa + " (" + String.format(Locale.US, "%.1f", maPercent) + "%)"
                + ", hpThreshold=" + AppVars.Profile.LezDrinkHp + ", maThreshold=" + AppVars.Profile.LezDrinkMa
                + ", hpEnabled=" + AppVars.Profile.LezDoDrinkHp + ", maEnabled=" + AppVars.Profile.LezDoDrinkMa
                + ", address=" + address
                + ", snapshotSource=" + snapshotSource);
        FileLogger.trace(TAG, "AUTO_DRINK_TRACE trigger restore elixir: hp="
                + snapshot.curHp + "/" + snapshot.maxHp + " (" + String.format(Locale.US, "%.1f", hpPercent) + "%)"
                + ", ma=" + snapshot.curMa + "/" + snapshot.maxMa + " (" + String.format(Locale.US, "%.1f", maPercent) + "%)"
                + ", hpThreshold=" + AppVars.Profile.LezDrinkHp + ", maThreshold=" + AppVars.Profile.LezDrinkMa
                + ", hpEnabled=" + AppVars.Profile.LezDoDrinkHp + ", maEnabled=" + AppVars.Profile.LezDoDrinkMa
                + ", address=" + address
                + ", snapshotSource=" + snapshotSource);
        FastActionManager.fastAttackMomentRestoreElixir();
    }

    /**
     * One-shot post-fight синхронизация HP/MA через pinfo.
     *
     * Нужна как страховка на кейс, когда `ins_HP(...)` на первом небоевом кадре
     * остаётся со старыми значениями и не отражает фактическое состояние после боя.
     */
    private static InsHpSnapshot tryBuildAutoDrinkSnapshotFromPinfo() {
        if (AppVars.Profile == null) {
            return null;
        }
        String nick = AppVars.Profile.UserNick != null ? AppVars.Profile.UserNick.trim() : "";
        if (nick.isEmpty()) {
            String msg = "AUTO_DRINK_TRACE pinfo skip: empty nick";
            AppLog.d(TAG, msg);
            return null;
        }
        // Снимок CharacterVitalsManager ПЕРЕД запросом info.cgi — для сравнения
        CharacterVitalsManager.Snapshot preInfoApiSnapshot = CharacterVitalsManager.snapshot();
        long preInfoApiTs = System.currentTimeMillis();
        long msSinceFightEndRedirect = autoDrinkPostFightSyncPendingSinceMs > 0
                ? (preInfoApiTs - autoDrinkPostFightSyncPendingSinceMs) : -1L;
        AppLog.d(TAG, "INFO_API_TRACE stage=info_api_runtime_call, source_module=post_fight_auto_drink, nick=" + nick
                + ", msSinceFightEndRedirect=" + msSinceFightEndRedirect);
        AppLog.d(TAG, "AUTO_DRINK_TRACE pre-info.cgi vitals: hp="
                + preInfoApiSnapshot.curHp + "/" + preInfoApiSnapshot.maxHp
                + ", ma=" + preInfoApiSnapshot.curMa + "/" + preInfoApiSnapshot.maxMa
                + ", tied=" + preInfoApiSnapshot.tied
                + ", source=" + preInfoApiSnapshot.source
                + ", ageMs=" + (preInfoApiSnapshot.updatedAtMs > 0 ? (preInfoApiTs - preInfoApiSnapshot.updatedAtMs) : -1));
        FileLogger.trace(TAG, "AUTO_DRINK_TRACE pre-info.cgi vitals: hp="
                + preInfoApiSnapshot.curHp + "/" + preInfoApiSnapshot.maxHp
                + ", ma=" + preInfoApiSnapshot.curMa + "/" + preInfoApiSnapshot.maxMa
                + ", tied=" + preInfoApiSnapshot.tied
                + ", source=" + preInfoApiSnapshot.source
                + ", ageMs=" + (preInfoApiSnapshot.updatedAtMs > 0 ? (preInfoApiTs - preInfoApiSnapshot.updatedAtMs) : -1));
        NeverApi.PinfoVitals vitals = NeverApi.getPinfoVitalsFromInfoApi(nick, "post_fight_auto_drink");
        if (vitals == null) {
            String msg = "AUTO_DRINK_TRACE pinfo skip: request failed";
            AppLog.d(TAG, msg);
            return null;
        }
        // Сравнение: MA из info.cgi vs MA из CharacterVitalsManager
        long infoApiDurationMs = System.currentTimeMillis() - preInfoApiTs;
        boolean maMismatch = vitals.curMa != null && preInfoApiSnapshot.maxMa > 0
                && Math.abs((vitals.curMa != null ? vitals.curMa : 0) - preInfoApiSnapshot.curMa) > 50;
        // info.cgi MA значительно НИЖЕ CharacterVitals → info.cgi вернул стейл-данные (лаг сервера после боя).
        // CharacterVitals (из ins_HP свежеотрендеренной страницы) достовернее — отклоняем pinfo snapshot.
        boolean infoApiMaStale = maMismatch
                && vitals.curMa != null
                && vitals.curMa < preInfoApiSnapshot.curMa;
        if (maMismatch) {
            String mismatchMsg = "⚠️ AUTO_DRINK_MA_MISMATCH: info.cgi ma="
                    + (vitals.curMa != null ? vitals.curMa : "null") + "/" + (vitals.maxMa != null ? vitals.maxMa : "null")
                    + " vs CharacterVitals ma=" + preInfoApiSnapshot.curMa + "/" + preInfoApiSnapshot.maxMa
                    + ", delta=" + ((vitals.curMa != null ? vitals.curMa : 0) - preInfoApiSnapshot.curMa)
                    + ", vitalsSource=" + preInfoApiSnapshot.source
                    + ", vitalsAgeMs=" + (preInfoApiSnapshot.updatedAtMs > 0 ? (preInfoApiTs - preInfoApiSnapshot.updatedAtMs) : -1)
                    + ", infoApiCallMs=" + infoApiDurationMs
                    + ", infoApiStale=" + infoApiMaStale;
            AppLog.w(TAG, mismatchMsg);
        }
        if (infoApiMaStale) {
            String rejectMsg = "AUTO_DRINK_TRACE pinfo REJECTED: info.cgi MA stale (info.cgi="
                    + vitals.curMa + " << CharacterVitals=" + preInfoApiSnapshot.curMa
                    + "), fallback to page ins_HP snapshot";
            AppLog.w(TAG, rejectMsg);
            return null;
        }
        AppLog.d(TAG, "AUTO_DRINK_TRACE info.cgi result: hp="
                + (vitals.curHp != null ? vitals.curHp : "null") + "/" + (vitals.maxHp != null ? vitals.maxHp : "null")
                + ", ma=" + (vitals.curMa != null ? vitals.curMa : "null") + "/" + (vitals.maxMa != null ? vitals.maxMa : "null")
                + ", tied=" + (vitals.curTire != null ? vitals.curTire : "null")
                + ", callDurationMs=" + infoApiDurationMs
                + ", maMismatch=" + maMismatch);
        boolean hasHpMa = vitals.curHp != null
                || vitals.maxHp != null
                || vitals.curMa != null
                || vitals.maxMa != null;
        if (!hasHpMa) {
            String msg = "AUTO_DRINK_TRACE pinfo skip: hp/ma not present";
            AppLog.d(TAG, msg);
            return null;
        }
        CharacterVitalsManager.Snapshot synced = CharacterVitalsManager.updateFromPinfo(
                vitals,
                "MainPhp.tryTriggerAutoDrinkRestoreElixir.postFightPinfo"
        );
        if (synced.maxHp <= 0 && synced.maxMa <= 0) {
            String msg = "AUTO_DRINK_TRACE pinfo skip: synced snapshot empty";
            AppLog.d(TAG, msg);
            return null;
        }
        InsHpSnapshot snapshot = new InsHpSnapshot();
        snapshot.curHp = synced.curHp;
        snapshot.maxHp = synced.maxHp;
        snapshot.curMa = synced.curMa;
        snapshot.maxMa = synced.maxMa;
        snapshot.intHp = synced.intHp;
        snapshot.intMa = synced.intMa;
        android.util.Log.d(TAG, "AUTO_DRINK_TRACE post-fight pinfo snapshot: hp="
                + snapshot.curHp + "/" + snapshot.maxHp
                + ", ma=" + snapshot.curMa + "/" + snapshot.maxMa
                + ", tied=" + synced.tied
                + ", source=" + synced.source);
        FileLogger.trace(TAG, "AUTO_DRINK_TRACE post-fight pinfo snapshot: hp="
                + snapshot.curHp + "/" + snapshot.maxHp
                + ", ma=" + snapshot.curMa + "/" + snapshot.maxMa
                + ", tied=" + synced.tied
                + ", source=" + synced.source);
        return snapshot;
    }

    private static boolean isPostFightAutoDrinkFollowupAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }
        String lower = address.trim().toLowerCase(Locale.ROOT);
        return lower.contains("main.php?get_id=56&act=10&go=inf")
                || lower.contains("main.php?get_id=56&act=10&go=inv")
                || lower.contains("main.php?im=");
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
                String msg = "AUTO_SKIN_TRACE skill parsed: SkinUm=" + skinUm + ", AutoSkinCheckUm=false";
                AppLog.d(TAG, msg);
            } catch (Exception e) {
                String msg = "AUTO_SKIN_TRACE skill parse failed: " + skinSkill;
                AppLog.w(TAG, msg, e);
            }
            return;
        }
        // Защитный сброс от зацикливания: мы уже на странице mselect=1,
        // но сервер не выдал ожидаемый блок навыка.
        if (AppVars.AutoSkinCheckUm && address != null && address.contains("mselect=1")) {
            AppVars.AutoSkinCheckUm = false;
            String msg = "AUTO_SKIN_TRACE mselect=1 without skill block, forced AutoSkinCheckUm=false";
            AppLog.w(TAG, msg);
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
            String msg = "isAutoSkinEnabledByPreference: fallback=false";
            AppLog.w(TAG, msg, e);
        }
        return false;
    }

    /**
     * Определяет, включено ли Авто-Лечение.
     *
     * Источник:
     * - переключатель `AUTO_CURE` в `AutoFunctionsManager` (SharedPreferences).
     */
    private static boolean isAutoCureEnabledByPreference() {
        try {
            android.content.Context context = AppVars.getContext();
            if (context == null) {
                return false;
            }
            return AutoFunctionsManager.getInstance(context).isAutoCureEnabled();
        } catch (Exception e) {
            String msg = "isAutoCureEnabledByPreference: fallback=false";
            AppLog.w(TAG, msg, e);
            return false;
        }
    }

    private static AutoFunctionsManager getAutoFunctionsManagerSafe() {
        try {
            android.content.Context context = AppVars.getContext();
            if (context == null) {
                return null;
            }
            return AutoFunctionsManager.getInstance(context);
        } catch (Exception e) {
            String msg = "getAutoFunctionsManagerSafe failed";
            AppLog.w(TAG, msg, e);
            return null;
        }
    }

    /**
     * Разрешено ли лечение конкретного типа небоевой травмы через эликсир.
     *
     * `cureTravm` parity:
     * - "1" = легкая травма
     * - "2" = средняя травма
     * - "3" = тяжелая травма
     * - "4" = боевая травма (эликсир не применяется, только боевая аптечка)
     */
    private static boolean isAutoCureSelfElixirEnabledForWound(String cureTravm) {
        int woundType = parseCureTravmType(cureTravm);
        if (woundType <= 0) {
            return false;
        }
        AutoFunctionsManager manager = getAutoFunctionsManagerSafe();
        return manager != null && manager.isAutoCureSelfElixirEnabledForWound(woundType);
    }

    /**
     * Разрешено ли лечить указанный тип травмы в Авто-Лечении.
     */
    private static boolean isAutoCureWoundTypeEnabledForTravm(String cureTravm) {
        int woundType = parseCureTravmType(cureTravm);
        if (woundType <= 0) {
            return false;
        }
        AutoFunctionsManager manager = getAutoFunctionsManagerSafe();
        return manager == null || manager.isAutoCureWoundTypeEnabled(woundType);
    }

    /**
     * Проверяет, что тип травмы разрешен к лечению для self-цели хотя бы одним методом:
     * - обычной аптечкой (чекбокс типа травмы);
     * - self-эликсиром (чекбокс "Эликсир Мгновенного Исцеления" для конкретного типа).
     */
    private static boolean isAutoCureWoundTypeEnabledForSelfByAnyMethod(String cureTravm) {
        if (isAutoCureWoundTypeEnabledForTravm(cureTravm)) {
            return true;
        }
        return isAutoCureSelfElixirEnabledForWound(cureTravm);
    }

    private static int parseCureTravmType(String cureTravm) {
        if (cureTravm == null || cureTravm.trim().isEmpty()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(cureTravm.trim());
            return (value >= 1 && value <= 4) ? value : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * Порт `MainPhpWearComplect` из C# (`MainPhpWear.cs`).
     * 
     * Парсит вызовы compl_view("название", "uid", "vcode") из HTML,
     * находит комплект, совпадающий с переданным названием,
     * и отправляет запрос на одевание: get_id=57&uid=...&s=2&vcode=...
     * 
     * Параметр s=2 используется специально для комплектов.
     */
    private static String mainPhpWearComplect(String html, String complectName) {
        if (html == null || html.isEmpty() || complectName == null || complectName.isEmpty()) {
            return null;
        }
        
        // Парсим все вызовы compl_view("название", "uid", "vcode")
        // Паттерн: compl_view("название","uid","vcode");
        
        int startIdx = 0;
        while (true) {
            final String marker = "compl_view(\"";
            startIdx = html.indexOf(marker, startIdx);
            if (startIdx == -1) break;
            
            startIdx += marker.length();
            int endNameIdx = html.indexOf("\"", startIdx);
            if (endNameIdx == -1) break;
            
            String parsedComplectName = html.substring(startIdx, endNameIdx);
            
            // Ищем следующую часть: uid
            final String uidMarker = "\",\"";
            int uidStartIdx = html.indexOf(uidMarker, endNameIdx);
            if (uidStartIdx == -1) break;
            
            uidStartIdx += uidMarker.length();
            int uidEndIdx = html.indexOf("\"", uidStartIdx);
            if (uidEndIdx == -1) break;
            
            String uid = html.substring(uidStartIdx, uidEndIdx);
            
            // Ищем VCode (третий параметр)
            int vcodeStartIdx = html.indexOf(uidMarker, uidEndIdx);
            if (vcodeStartIdx == -1) break;
            
            vcodeStartIdx += uidMarker.length();
            int vcodeEndIdx = html.indexOf("\"", vcodeStartIdx);
            if (vcodeEndIdx == -1) break;
            
            String vcode = html.substring(vcodeStartIdx, vcodeEndIdx);
            
            // Проверяем, совпадает ли название compl_view() с запрашиваемым
            if (parsedComplectName.equalsIgnoreCase(complectName)) {
                // Найден! Формируем запрос для одевания комплекта
                // Параметр s=2 - специальный код для комплектов (отличается от s=1 для вещей)
                String wearUrl = "main.php?get_id=57&uid=" + uid + "&s=2&vcode=" + vcode;
                String msg_wear = "COMPLECT_TIMER_PARSE_TRACE: found complect=\"" + parsedComplectName + "\", uid=" + uid;
                AppLog.d(TAG, msg_wear);
                return buildRedirectHtml("Таймер комплекта: одеваем " + parsedComplectName, wearUrl);
            }
            
            startIdx = vcodeEndIdx;
        }
        
        // Комплект не найден либо HTML не содержит нужного вызова compl_view
        String msg_notfound = "COMPLECT_TIMER_PARSE_TRACE: complect not found \"" + complectName + "\"";
        AppLog.d(TAG, msg_notfound);
        return null;
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
            String msg = "AUTO_SKIN_TRACE periodic knife recheck requested";
            AppLog.d(TAG, msg);
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
            int curTire = CharacterVitalsManager.snapshot().tied;
            String statusHtml = "<font class=nickname><div align=center style=\"color: #660066;\"><i>"
                    + "\u041f\u0443\u043d\u043a\u0442 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f: <b>" + AppVars.AutoMovingDestinaton + "</b><br>"
                    + "\u0415\u0449\u0435 \u043f\u0435\u0440\u0435\u0445\u043e\u0434\u043e\u0432: <b>" + AppVars.AutoMovingJumps + "</b><br>"
                    + "\u0422\u0435\u043a\u0443\u0449\u0430\u044f \u0423\u0441\u0442\u0430\u043b\u043e\u0441\u0442\u044c: <b>" + curTire + "</b>"
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
            FileLogger.trace(TAG, "SERVER_TIMER_TRACE wtime sync: timeoutSec=" + timeoutSec
                    + ", updated=" + updated + ", address=" + address
                    + ", dueInMs=" + Math.max(0L, AppVars.NeverTimer - now));
        }
    }

    private static String mainPhpRaz(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "var\\s+fight_ty\\s*=\\s*\\[(.*?)\\];",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL
        ).matcher(html);
        int variantIndex = 0;
        while (matcher.find()) {
            variantIndex++;
            String strFightTy = matcher.group(1);
            String razLink = buildRazLinkFromFightTyPayload(strFightTy);
            if (razLink != null) {
                String msg = "AUTO_SKIN_TRACE mainPhpRaz: redirect via fight_ty[";
                AppLog.d(TAG, msg);
                return buildRedirectHtml("Разделка", razLink);
            }
        }

        String fallbackRazLink = extractRazLinkFromHtml(html);
        if (fallbackRazLink != null) {
            String msg = "AUTO_SKIN_TRACE mainPhpRaz: fallback link redirect to ";
            AppLog.d(TAG, msg);
            return buildRedirectHtml("Разделка", fallbackRazLink);
        }
        return null;
    }

    private static String buildRazLinkFromFightTyPayload(String strFightTy) {
        if (strFightTy == null || strFightTy.isEmpty()) {
            return null;
        }
        List<String> fightTy = splitJsTopLevelCsv(strFightTy);
        if (fightTy.size() <= 9) {
            return null;
        }
        String fightTyNine = fightTy.get(9);
        if (fightTyNine == null) {
            return null;
        }
        String trimmed = fightTyNine.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return null;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1);
        List<String> razParams = splitJsTopLevelCsv(inner);
        if (razParams.size() <= 5) {
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
        return "http://neverlands.ru/main.php?get_id=17&type=" + type
                + "&p=" + p
                + "&uid=" + uid
                + "&s=" + s
                + "&m=" + m
                + "&vcode=" + vcode;
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
    static boolean isNonCombatAutoPausedByFastAction() {
        return (AppVars.FastNeed && AppVars.FastPauseNonCombatAutoFunctions)
                || AppVars.TreasureDigPauseNonCombatAutoFunctions
                || AppVars.TimerPauseNonCombatAutoFunctions;  // Pause всех non-combat авто при таймере
    }

    // Runtime pause for non-combat auto pipelines while external auto-cure request is active.
    // Used to prevent AutoSearch/AutoMoving overlap with doctorform processing.
    private static boolean isNonCombatAutoPausedByCureAction() {
        return AppVars.CurePauseNonCombatAutoFunctions;
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
    static boolean isInventoryAddress(String address) {
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
    static boolean inventoryAddressMatchesFilter(String address, String filter) {
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
    static String mainPhpFindPerc(String html) {
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
                String msg = "AUTO_FALLBACK_TRACE mainPhpFindPerc: regex fallback -> ";
                AppLog.d(TAG, msg);
                return buildRedirectHtml("Переключение на персонаж", fallbackLink);
            }
            return null;
        }
        posPatternEnter += patternEnter.length();
        int posEnd = html.indexOf('"', posPatternEnter);
        if (posEnd == -1) {
            String fallbackLink = findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
            if (fallbackLink != null) {
                String msg = "AUTO_FALLBACK_TRACE mainPhpFindPerc: regex fallback -> ";
                AppLog.d(TAG, msg);
                return buildRedirectHtml("Переключение на персонаж", fallbackLink);
            }
            return null;
        }
        String jsonVcode = html.substring(posPatternEnter, posEnd);
        if (jsonVcode == null || jsonVcode.isEmpty()) {
            String fallbackLink = findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
            if (fallbackLink != null) {
                String msg = "AUTO_FALLBACK_TRACE mainPhpFindPerc: regex fallback -> ";
                AppLog.d(TAG, msg);
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
    static String mainPhpFindFlora(String html) {
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

    /**
     * Fallback-синхронизация тяжелой травмы по server popup в map-HTML:
     * "Вы не можете перемещаться! У Вас тяжёлая травма."
     */
    private static void syncInjuriesFromMapHeavyPopup(String html) {
        handleHeavyInjurySignal(html, "map_html");
    }

    /**
     * Единая точка обработки server popup из JS bridge (MapJs -> WebAppInterface):
     * - синхронизирует runtime-состояние травм;
     * - при включенном авто-лечении ставит небоевые авто-функции на паузу
     *   и ставит self-лечение тяжелой травмы в приоритет.
     */
    public static void onServerPopupMessage(String popupText) {
        handleHeavyInjurySignal(popupText, "bridge_popup");
    }

    /**
     * Общий обработчик сигнала тяжелой травмы.
     *
     * Источники:
     * - map-HTML (`syncInjuriesFromMapHeavyPopup(...)`);
     * - server popup из bridge (`onServerPopupMessage(...)`).
     *
     * Правила:
     * - дедуп по времени (anti-spam);
     * - обновление runtime-снимка травм через CharacterVitalsManager;
     * - при включенном авто-лечении: pause non-combat + self CureNeed(3).
     */
    private static void handleHeavyInjurySignal(String text, String sourceTag) {
        if (!isHeavyInjurySignalText(text)) {
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastMapHeavyInjurySyncAtMs) < 1200L) {
            return;
        }
        lastMapHeavyInjurySyncAtMs = now;
        CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.ensureHeavyWoundPresent(
                "MainPhp.handleHeavyInjurySignal." + sourceTag);
        queueSelfHeavyInjuryCureIfNeeded(sourceTag);
        android.util.Log.d(TAG, "AUTO_CURE_TRACE heavy injury signal(" + sourceTag + "): pw=["
                + snapshot.poisonCount + "," + snapshot.lightWoundCount + ","
                + snapshot.mediumWoundCount + "," + snapshot.heavyWoundCount + "]");
        FileLogger.trace(TAG, "AUTO_CURE_TRACE heavy injury signal(" + sourceTag + "): pw=["
                + snapshot.poisonCount + "," + snapshot.lightWoundCount + ","
                + snapshot.mediumWoundCount + "," + snapshot.heavyWoundCount + "]");
    }

    /**
     * Детектор server-сообщения о тяжелой травме.
     *
     * Учитывает вариации:
     * - "тяжёлая"/"тяжелая";
     * - возможные HTML-вставки/дополнительный текст.
     */
    private static boolean isHeavyInjurySignalText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        boolean hasMoveBlock = lower.contains("не можете перемещ")
                || lower.contains("не можите перемещ");
        boolean hasHeavyWound = lower.contains("тяж")
                && lower.contains("травм");
        if (hasMoveBlock && hasHeavyWound) {
            return true;
        }
        String marker = MAP_HEAVY_INJURY_POPUP_MARKER.toLowerCase(Locale.ROOT).replace('ё', 'е');
        return lower.contains(marker) && hasHeavyWound;
    }

    /**
     * Ставит self-лечение тяжелой травмы в приоритет после сигнала server popup.
     *
     * Поведение:
     * - не делает ничего, если авто-лечение выключено;
     * - включает pause non-combat;
     * - формирует self-запрос CureNeed/CureNick/CureTravm=3.
     */
    private static void queueSelfHeavyInjuryCureIfNeeded(String sourceTag) {
        if (!isAutoCureEnabledByPreference()) {
            String msg = "AUTO_CURE_TRACE heavy injury signal ignored: auto-cure disabled, source=";
            AppLog.d(TAG, msg);
            return;
        }
        if (AppVars.Profile == null || AppVars.Profile.UserNick == null) {
            String msg = "AUTO_CURE_TRACE heavy injury signal ignored: empty self nick, source=";
            AppLog.d(TAG, msg);
            return;
        }
        String selfNick = AppVars.Profile.UserNick.trim();
        if (selfNick.isEmpty()) {
            String msg = "AUTO_CURE_TRACE heavy injury signal ignored: blank self nick, source=";
            AppLog.d(TAG, msg);
            return;
        }

        AppVars.CurePauseNonCombatAutoFunctions = true;
        AppVars.CureNeed = true;
        AppVars.CureNick = selfNick;
        AppVars.CureTravm = "3";
        AppVars.CureNickDone = "";
        AppVars.CureNickBoi = "";
        android.util.Log.d(TAG, "AUTO_CURE_TRACE heavy injury queued self cure: nick="
                + selfNick + ", travm=3, source=" + sourceTag);
    }

    /**
     * Выполняет внешний запрос лечения (`AppVars.CureNeed`) из RoomManager/UI.
     *
     * Источники запроса:
     * - RoomManager авто-скан (`self -> friends -> neutrals`);
     * - ручной запрос (C#-аналог контекстного меню травм).
     *
     * Правила выполнения:
     * - сначала валидирует флаги/данные (`CureNeed`, nick, travm, включено ли Авто-лечение);
     * - для self-цели при разрешении в настройках сначала пробует
     *   `Эликсир Мгновенного Исцеления` (вкладка `im=6`);
     * - если эликсир не применён, выполняет fallback на аптечки (`im=0&wca=85`, `doctorform`);
     * - запрос считается завершённым только после реального submit-действия
     *   (не на шаге навигационного редиректа в инвентарь).
     *
     * Зависимости:
     * - `AppVars.CureNeed/CureNick/CureTravm/CurePauseNonCombatAutoFunctions`;
     * - `AutoFunctionsManager` (проверка настроек типов травм и self-elixir);
     * - `RoomManager.onAutoCureSubmitted(...)` (пост-проверка после лечения);
     * - `CharacterVitalsManager` (локальная коррекция runtime-счётчиков после submit).
     */
    private static String mainPhpExternalRequestedCureStep(String address, String html) {
        if (!AppVars.CureNeed) {
            return null;
        }
        if (!isAutoCureEnabledByPreference()) {
            clearExternalCureRequest("auto-cure-disabled");
            return null;
        }

        String targetNick = AppVars.CureNick == null ? "" : AppVars.CureNick.trim();
        String cureTravm = AppVars.CureTravm == null ? "" : AppVars.CureTravm.trim();
        if (targetNick.isEmpty() || cureTravm.isEmpty()) {
            clearExternalCureRequest("empty-target-or-type");
            return null;
        }
        if (!("1".equals(cureTravm) || "2".equals(cureTravm) || "3".equals(cureTravm) || "4".equals(cureTravm))) {
            clearExternalCureRequest("invalid-wound-type");
            return null;
        }
        boolean selfTarget = isSelfNick(targetNick);
        if (selfTarget) {
            if (!isAutoCureWoundTypeEnabledForSelfByAnyMethod(cureTravm)) {
                clearExternalCureRequest("wound-type-disabled");
                return null;
            }
        } else if (!isAutoCureWoundTypeEnabledForTravm(cureTravm)) {
            clearExternalCureRequest("wound-type-disabled");
            return null;
        }

        final String woundLabel;
        switch (cureTravm) {
            case "1":
                woundLabel = "легкая";
                break;
            case "2":
                woundLabel = "средняя";
                break;
            case "3":
                woundLabel = "тяжелая";
                break;
            default:
                woundLabel = "боевая";
                break;
        }

        // Внешний запрос лечения себя (RoomManager self-priority): сначала пробуем эликсир,
        // если для типа травмы он разрешен в настройках.
        if (selfTarget && isAutoCureSelfElixirEnabledForWound(cureTravm)) {
            String selfElixirCureHtml = mainPhpTrySelfWoundCureByElixir(address, html, woundLabel);
            if (selfElixirCureHtml != null && !selfElixirCureHtml.isEmpty()) {
                if (!isSelfWoundElixirNavigationOnlyResult(selfElixirCureHtml)) {
                    decrementSelfWoundCounterIfNeeded(targetNick, cureTravm,
                            "MainPhp.mainPhpExternalRequestedCureStep.selfElixirUsed");
                    AppVars.CureNickDone = targetNick;
                    RoomManager.onAutoCureSubmitted(targetNick, cureTravm);
                    clearExternalCureRequest("submitted-self-elixir");
                    android.util.Log.d(TAG, "AUTO_CURE_TRACE self elixir submitted: nick="
                            + targetNick + ", travm=" + cureTravm);
                } else {
                    android.util.Log.d(TAG, "AUTO_CURE_TRACE self elixir navigation step: nick="
                            + targetNick + ", travm=" + cureTravm);
                }
                return selfElixirCureHtml;
            }
        }

        // Если у self-цели тип травмы разрешен только через эликсир, fallback на аптечки запрещен.
        if (selfTarget && !isAutoCureWoundTypeEnabledForTravm(cureTravm)) {
            clearExternalCureRequest("self-elixir-only-no-medkit-fallback");
            return null;
        }

        String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=85", address);
        if (invHtml != null && !invHtml.isEmpty()) {
            return invHtml;
        }
        if (!(mainPhpIsInv(html) || isInventoryAddress(address))) {
            return null;
        }
        if (!mainPhpIsInv(html)) {
            return null;
        }
        if (!inventoryAddressMatchesFilter(address, "&im=0&wca=85")) {
            return buildRedirectHtml("Переключение на аптечки", "main.php?im=0&wca=85");
        }

        String cureHtml = mainPhpBuildWoundCureForm(html, cureTravm, targetNick);
        if (cureHtml == null || cureHtml.isEmpty()) {
            sendInventoryChatMessage(buildServerChatTimeHtml()
                    + "<font color=#FF0000>Подходящая аптечка не найдена! Действие отменено.</font>");
            clearExternalCureRequest("doctorform-not-found");
            return null;
        }

        String safeNick = targetNick.replace("<", "&lt;").replace(">", "&gt;");
        sendInventoryChatMessage(buildServerChatTimeHtml()
                + "<font color=#004bbb>Лечим " + safeNick + " (" + woundLabel + " травма)...</font>");

        // Prevent stale self-wound state after doctorform submit:
        // without this, AutoCure may keep seeing old runtime counters and re-trigger self-elixir loop.
        decrementSelfWoundCounterIfNeeded(targetNick, cureTravm,
                "MainPhp.mainPhpExternalRequestedCureStep.submitted");
        AppVars.CureNickDone = targetNick;
        RoomManager.onAutoCureSubmitted(targetNick, cureTravm);

        clearExternalCureRequest("submitted");
        return cureHtml;
    }

    /**
     * Полностью очищает состояние внешнего запроса лечения.
     *
     * Что сбрасывает:
     * - `CureNeed`, `CureNick`, `CureTravm`;
     * - паузу небоевых авто-функций `CurePauseNonCombatAutoFunctions`.
     *
     * Правило:
     * - вызывать на всех финальных ветках (успех/ошибка/отмена), чтобы не оставлять "залипший" запрос.
     */
    private static void clearExternalCureRequest(String reason) {
        AppVars.CureNeed = false;
        AppVars.CureNick = "";
        AppVars.CureTravm = "";
        AppVars.CurePauseNonCombatAutoFunctions = false;
        String msg = "AUTO_CURE_TRACE clear external request: reason=" + reason;
        AppLog.d(TAG, msg);
    }

    /**
     * C# parity (`MainPhp.cs` + `MainPhpAutoCure.cs` + `MainPhpCure.cs`):
     * 1) яд (`wca=27`, magicreform);
     * 2) небоевые травмы (`wca=85`, doctorform).
     *
     * Правила:
     * - работает только в non-combat шаге `main.php`;
     * - использует единый runtime-снимок `CharacterVitalsManager.snapshot()`;
     * - соблюдает настройки типов травм из `AutoFunctionsManager`;
     * - для self-травм может приоритетно применять эликсир (если включено);
     * - декремент runtime-счётчиков делается только после фактического submit лечения
     *   (не на шаге редиректа в нужную вкладку инвентаря).
     *
     * Зависимости:
     * - `mainPhpFindInvWithFallback(...)`, `mainPhpBuildPoisonCureForm(...)`, `mainPhpBuildWoundCureForm(...)`;
     * - `CharacterVitalsManager` (чтение/коррекция состояния травм);
     * - `AppVars.NeverTimer` (ограничение по серверному таймеру).
     */
    private static String mainPhpAutoCureStep(String address, String html) {
        if (html == null || html.isEmpty()
                || AppVars.Profile == null
                || !isAutoCureEnabledByPreference()) {
            return null;
        }
        String nick = AppVars.Profile.UserNick == null ? "" : AppVars.Profile.UserNick.trim();
        if (nick.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (AppVars.NeverTimer > 0L && now < AppVars.NeverTimer) {
            android.util.Log.d(TAG, "AUTO_CURE_TRACE skipped by NeverTimer: dueInMs="
                    + Math.max(0L, AppVars.NeverTimer - now) + ", address=" + address);
            return null;
        }

        CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.snapshot();
        int poison = snapshot.poisonCount;
        int light = snapshot.lightWoundCount;
        int medium = snapshot.mediumWoundCount;
        int heavy = snapshot.heavyWoundCount;
        if (poison <= 0 && light <= 0 && medium <= 0 && heavy <= 0) {
            return null;
        }

        if (poison > 0) {
            String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=27", address);
            if (invHtml != null && !invHtml.isEmpty()) {
                return invHtml;
            }
            if (!(mainPhpIsInv(html) || isInventoryAddress(address))) {
                return null;
            }
            if (!mainPhpIsInv(html)) {
                return null;
            }
            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=27")) {
                return buildRedirectHtml("Переключение на зелья", "main.php?im=0&wca=27");
            }

            String poisonCureHtml = mainPhpBuildPoisonCureForm(html, nick);
            if (poisonCureHtml == null || poisonCureHtml.isEmpty()) {
                disableAutoCureAndNotify(
                        "У вас отравление и нет зелья лечения отравлений! Автолечение отключено. Не забудьте включить его обратно.",
                        true,
                        false
                );
                return null;
            }

            CharacterVitalsManager.decrementPoisonOrWound(POISON_INDEX, "MainPhp.mainPhpAutoCureStep.poisonUsed");
            AppVars.CureNickDone = nick;
            sendInventoryChatMessage(buildServerChatTimeHtml()
                    + "<font color=#004bbb>Лечим свое отравление...</font>");
            return poisonCureHtml;
        }

        String cureTravm;
        int woundIndex;
        String woundLabel;
        if (light > 0 && isAutoCureWoundTypeEnabledForSelfByAnyMethod("1")) {
            cureTravm = "1";
            woundIndex = LIGHT_WOUND_INDEX;
            woundLabel = "легкую";
        } else if (medium > 0 && isAutoCureWoundTypeEnabledForSelfByAnyMethod("2")) {
            cureTravm = "2";
            woundIndex = MEDIUM_WOUND_INDEX;
            woundLabel = "среднюю";
        } else if (heavy > 0 && isAutoCureWoundTypeEnabledForSelfByAnyMethod("3")) {
            cureTravm = "3";
            woundIndex = HEAVY_WOUND_INDEX;
            woundLabel = "тяжелую";
        } else {
            if (light > 0 || medium > 0 || heavy > 0) {
                android.util.Log.d(TAG, "AUTO_CURE_TRACE self wounds present but disabled by settings: "
                        + "light=" + light + "(enabled=" + isAutoCureWoundTypeEnabledForSelfByAnyMethod("1") + "), "
                        + "medium=" + medium + "(enabled=" + isAutoCureWoundTypeEnabledForSelfByAnyMethod("2") + "), "
                        + "heavy=" + heavy + "(enabled=" + isAutoCureWoundTypeEnabledForSelfByAnyMethod("3") + ")");
            }
            return null;
        }

        // Опциональный приоритет self-лечения эликсиром (Настройки -> Лечение).
        // Важно: касается только лечения себя и только небоевых травм (1/2/3).
        // Для боевой травмы (4) эликсир не применяется.
        if (isAutoCureSelfElixirEnabledForWound(cureTravm)) {
            String selfElixirCureHtml = mainPhpTrySelfWoundCureByElixir(address, html, woundLabel);
            if (selfElixirCureHtml != null && !selfElixirCureHtml.isEmpty()) {
                if (!isSelfWoundElixirNavigationOnlyResult(selfElixirCureHtml)) {
                    CharacterVitalsManager.decrementPoisonOrWound(woundIndex,
                            "MainPhp.mainPhpAutoCureStep.selfElixirUsed");
                    AppVars.CureNickDone = nick;
                    RoomManager.onAutoCureSubmitted(nick, cureTravm);
                    android.util.Log.d(TAG, "AUTO_CURE_TRACE self elixir submitted (self): travm="
                            + cureTravm + ", index=" + woundIndex);
                } else {
                    android.util.Log.d(TAG, "AUTO_CURE_TRACE self elixir navigation step (self): travm="
                            + cureTravm);
                }
                return selfElixirCureHtml;
            }
        }

        // Если для self выбран "только эликсир", в эту ветку аптечек не падаем.
        if (!isAutoCureWoundTypeEnabledForTravm(cureTravm)) {
            return null;
        }

        String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=85", address);
        if (invHtml != null && !invHtml.isEmpty()) {
            return invHtml;
        }
        if (!(mainPhpIsInv(html) || isInventoryAddress(address))) {
            return null;
        }
        if (!mainPhpIsInv(html)) {
            return null;
        }
        if (!inventoryAddressMatchesFilter(address, "&im=0&wca=85")) {
            return buildRedirectHtml("Переключение на аптечки", "main.php?im=0&wca=85");
        }

        String woundCureHtml = mainPhpBuildWoundCureForm(html, cureTravm, nick);
        if (woundCureHtml == null || woundCureHtml.isEmpty()) {
            disableAutoCureAndNotify(
                    "У вас травма, но нет возможности ее вылечить! Автолечение отключено. Не забудьте включить его обратно.",
                    false,
                    true
            );
            return null;
        }

        CharacterVitalsManager.decrementPoisonOrWound(woundIndex, "MainPhp.mainPhpAutoCureStep.woundUsed");
        AppVars.CureNickDone = nick;
        RoomManager.onAutoCureSubmitted(nick, cureTravm);
        sendInventoryChatMessage(buildServerChatTimeHtml()
                + "<font color=#004bbb>Лечим свою " + woundLabel + " травму...</font>");
        return woundCureHtml;
    }

    private static String mainPhpBuildPoisonCureForm(String html, String selfNick) {
        if (html == null || html.isEmpty() || selfNick == null || selfNick.isEmpty()) {
            return null;
        }
        String namePotion = "'" + AUTO_CURE_POISON_POTION_NAME + "'";
        String htmlLower = html.toLowerCase(Locale.ROOT);
        int p0 = htmlLower.indexOf(namePotion.toLowerCase(Locale.ROOT));
        if (p0 == -1) {
            return null;
        }
        int ps = html.lastIndexOf('<', p0);
        if (ps == -1) {
            return null;
        }
        ps++;
        int pe = html.indexOf('>', p0);
        if (pe == -1) {
            return null;
        }
        String chunk = html.substring(ps, pe);
        if (!containsIgnoreCase(chunk, "magicreform(")) {
            return null;
        }
        String args = HelperStrings.subString(chunk, "magicreform('", "')");
        if (args == null || args.isEmpty()) {
            return null;
        }
        String[] arg = args.split("'");
        if (arg.length < 7) {
            return null;
        }
        String wuid = arg[0];
        String wmcode = arg[6];
        return HtmlUtils.GENERATED_PAGE_MARKER
                + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                + "<title>ABClient</title></head><body>"
                + "Используем " + AUTO_CURE_POISON_POTION_NAME + " на себя..."
                + "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>"
                + "<input name=magicrestart type=hidden value=\"1\">"
                + "<input name=magicreuid type=hidden value=\"" + wuid + "\">"
                + "<input name=vcode type=hidden value=\"" + wmcode + "\">"
                + "<input name=post_id type=hidden value=\"46\">"
                + "<input name=fornickname type=hidden value=\"" + selfNick + "\">"
                + "</form><script language=\"JavaScript\">document.ff.submit();</script></body></html>";
    }

    /**
     * Пытается вылечить свою травму "Эликсиром Мгновенного Исцеления" (вкладка `im=6`).
     *
     * Алгоритм:
     * - идем в инвентарь эликсиров;
     * - если confirm-ссылка на эликсир присутствует, выполняем GET-redirect;
     * - если эликсир отсутствует, возвращаем `null` и даем fallback на обычные аптечки (`wca=85`).
     *
     * Правила:
     * - метод только формирует следующий HTML-шаг (редирект/submit), без прямого изменения счётчиков травм;
     * - изменение `PoisonAndWounds` выполняется выше по стеку только после подтверждённого submit.
     *
     * Зависимости:
     * - `mainPhpFindInvWithFallback(...)` и `inventoryAddressMatchesFilter(...)` для маршрутизации в `im=6`;
     * - `mainPhpBuildSelfWoundCureElixirRedirect(...)` для построения action-страницы;
     * - `sendInventoryChatMessage(...)` для системного сообщения в чат.
     */
    private static String mainPhpTrySelfWoundCureByElixir(String address, String html, String woundLabel) {
        String invHtml = mainPhpFindInvWithFallback(html, "&im=6", address);
        if (invHtml != null && !invHtml.isEmpty()) {
            return invHtml;
        }
        if (!(mainPhpIsInv(html) || isInventoryAddress(address))) {
            return null;
        }
        if (!mainPhpIsInv(html)) {
            return null;
        }
        if (!inventoryAddressMatchesFilter(address, "&im=6")) {
            return buildRedirectHtml("Переключение на эликсиры", "main.php?im=6");
        }

        String cureHtml = mainPhpBuildSelfWoundCureElixirRedirect(html);
        if (cureHtml == null || cureHtml.isEmpty()) {
            return null;
        }

        sendInventoryChatMessage(buildServerChatTimeHtml()
                + "<font color=#004bbb>Лечим свою " + woundLabel + " травму "
                + AUTO_CURE_SELF_ELIXIR_NAME + "..."
                + FastActionManager.buildElixirRemainingSuffixForMessage(
                        AUTO_CURE_SELF_ELIXIR_NAME,
                        html,
                        -1
                )
                + "</font>");
        return cureHtml;
    }

    /**
     * Проверяет, что результат self-cure эликсира является только шагом навигации (переход на инвентарь),
     * а не реальным submit/использованием эликсира.
     *
     * Правило:
     * - если это navigation-only шаг, счётчики травм и внешние cure-флаги не трогаем;
     * - если это не navigation-only (реальный submit), разрешено завершать cure-ветку и корректировать runtime.
     */
    private static boolean isSelfWoundElixirNavigationOnlyResult(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (!lower.contains(HtmlUtils.GENERATED_PAGE_MARKER.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return lower.contains("переключение на инвентарь")
                || lower.contains("переключение на эликсиры");
    }

    /**
     * Формирует auto-redirect на GET-ссылку применения `Эликсир Мгновенного Исцеления`.
     *
     * Browser parity (`HealthElik.har`):
     * - используется confirm-блок `Использовать <...> сейчас?`;
     * - nickname не передается (self-use).
     */
    private static String mainPhpBuildSelfWoundCureElixirRedirect(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String prompt = "Использовать " + AUTO_CURE_SELF_ELIXIR_NAME + " сейчас?";
        int promptPos = html.toLowerCase(Locale.ROOT).indexOf(prompt.toLowerCase(Locale.ROOT));
        if (promptPos == -1) {
            return null;
        }

        int linkStart = html.indexOf("='", promptPos);
        if (linkStart == -1) {
            return null;
        }
        linkStart += 2;
        int linkEnd = html.indexOf("'", linkStart);
        if (linkEnd == -1) {
            return null;
        }
        String link = html.substring(linkStart, linkEnd).trim();
        if (link.isEmpty()) {
            return null;
        }

        return HtmlUtils.GENERATED_PAGE_MARKER
                + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                + "<title>ABClient</title></head><body>"
                + "Используем " + AUTO_CURE_SELF_ELIXIR_NAME + "..."
                + "<script language=\"JavaScript\">window.location = \"" + link + "\";</script>"
                + "</body></html>";
    }

    private static String mainPhpBuildWoundCureForm(String html, String cureTravm, String targetNick) {
        if (html == null || html.isEmpty()
                || cureTravm == null || cureTravm.isEmpty()
                || targetNick == null || targetNick.isEmpty()) {
            return null;
        }
        String dtypeNeed;
        switch (cureTravm) {
            case "1":
                dtypeNeed = "0";
                break;
            case "2":
                dtypeNeed = "1";
                break;
            case "3":
                dtypeNeed = "2";
                break;
            case "4":
                dtypeNeed = "4";
                break;
            default:
                return null;
        }

        String htmlLower = html.toLowerCase(Locale.ROOT);
        String patternDoctorForm = "doctorform(";
        int p1 = 0;
        while (p1 != -1) {
            p1 = htmlLower.indexOf(patternDoctorForm, p1);
            if (p1 == -1) {
                break;
            }
            int argsStart = p1 + patternDoctorForm.length();
            int p2 = html.indexOf(")", argsStart);
            if (p2 == -1) {
                break;
            }
            String args = html.substring(argsStart, p2);
            p1 = p2 + 1;
            if (args.isEmpty()) {
                continue;
            }
            String[] arg = args.split(",");
            if (arg.length < 5) {
                continue;
            }

            String duid = trimJsToken(arg[0]);
            String vcode = trimJsToken(arg[1]);
            String dprice = trimJsToken(arg[2]);
            String dtype = trimJsToken(arg[3]);
            String dcurs = trimJsToken(arg[4]);
            if (!dtypeNeed.equalsIgnoreCase(dtype)) {
                continue;
            }

            return HtmlUtils.GENERATED_PAGE_MARKER
                    + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                    + "<title>ABClient</title></head><body>"
                    + "Используем аптечку на " + targetNick + "..."
                    + "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>"
                    + "<input name=dtype type=hidden value=\"" + dtype + "\">"
                    + "<input name=addid type=hidden value=\"2\">"
                    + "<input name=post_id type=hidden value=\"3\">"
                    + "<input name=dprice type=hidden value=\"" + dprice + "\">"
                    + "<input name=dcurs type=hidden value=\"" + dcurs + "\">"
                    + "<input name=duid type=hidden value=\"" + duid + "\">"
                    + "<input name=vcode type=hidden value=\"" + vcode + "\">"
                    + "<input name=fnick type=hidden value=\"" + targetNick + "\">"
                    + "</form><script language=\"JavaScript\">document.ff.submit();</script></body></html>";
        }
        return null;
    }

    private static void decrementSelfWoundCounterIfNeeded(String targetNick, String cureTravm, String source) {
        if (!isSelfNick(targetNick)) {
            return;
        }
        int woundIndex = woundIndexFromTravm(cureTravm);
        if (woundIndex < 0) {
            return;
        }
        CharacterVitalsManager.decrementPoisonOrWound(woundIndex, source);
    }

    private static boolean isSelfNick(String nick) {
        if (nick == null || nick.trim().isEmpty() || AppVars.Profile == null
                || AppVars.Profile.UserNick == null || AppVars.Profile.UserNick.trim().isEmpty()) {
            return false;
        }
        return nick.trim().equalsIgnoreCase(AppVars.Profile.UserNick.trim());
    }

    private static int woundIndexFromTravm(String cureTravm) {
        if (cureTravm == null || cureTravm.trim().isEmpty()) {
            return -1;
        }
        switch (cureTravm.trim()) {
            case "1":
                return LIGHT_WOUND_INDEX;
            case "2":
                return MEDIUM_WOUND_INDEX;
            case "3":
                return HEAVY_WOUND_INDEX;
            default:
                // "4" (battle wound) is not represented in PoisonAndWounds runtime counters.
                return -1;
        }
    }

    private static void disableAutoCureAndNotify(String message, boolean clearPoison, boolean clearWounds) {
        CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.snapshot();
        int[] current = new int[] {
                snapshot.poisonCount,
                snapshot.lightWoundCount,
                snapshot.mediumWoundCount,
                snapshot.heavyWoundCount
        };
        if (clearPoison) {
            current[POISON_INDEX] = 0;
        }
        if (clearWounds) {
            current[LIGHT_WOUND_INDEX] = 0;
            current[MEDIUM_WOUND_INDEX] = 0;
            current[HEAVY_WOUND_INDEX] = 0;
        }
        CharacterVitalsManager.updatePoisonAndWounds(current, "MainPhp.disableAutoCureAndNotify");
        try {
            android.content.Context context = AppVars.getContext();
            if (context != null) {
                AutoFunctionsManager.getInstance(context).setAutoCureEnabled(false);
            }
        } catch (Exception e) {
            String msg = "AUTO_CURE_TRACE disable failed";
            AppLog.w(TAG, msg, e);
        }
        sendInventoryChatMessage(buildServerChatTimeHtml() + "<font color=#FF0000>" + message + "</font>");
    }

    static boolean mainPhpIsPerc(String html) {
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
        if (!deltaForChat.isEmpty()) {
            boolean canReportToChat = AppVars.Profile != null && AppVars.Profile.RazdChatReport;
            if (canReportToChat) {
            String message = buildServerChatTimeHtml()
                    + "<font color=#006600><b>Результат разделки:</b></font> "
                    + String.join(", ", deltaForChat);
            if (AppVars.getContext() != null) {
                Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
                intent.putExtra("message", message);
                LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
            }
            } else {
                android.util.Log.d(TAG, "AUTO_SKIN_TRACE mainPhpGetSkinRes: chat skipped, RazdChatReport=false"
                        + ", deltaCount=" + deltaForChat.size());
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
    static List<WearInvEntry> getWearInvList(String html) {
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
    static List<String> splitJsTopLevelCsv(String source) {
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

    private static String extractServerNoticeFromMainHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        String direct = HelperStrings.subString(
                html,
                "<font class=nickname><font color=#cc0000><b>",
                "<br><br></b></font></font>");
        if (direct != null && !direct.trim().isEmpty()) {
            return direct.trim();
        }

        Matcher redBoldMatcher = Pattern.compile(
                "(?is)<font[^>]*color\\s*=\\s*['\\\"]?#?cc0000['\\\"]?[^>]*>\\s*<b>(.*?)<br\\s*/?>\\s*<br\\s*/?>\\s*</b>\\s*</font>")
                .matcher(html);
        if (redBoldMatcher.find()) {
            String candidate = redBoldMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }

        Matcher redBoldSingleBrMatcher = Pattern.compile(
                "(?is)<font[^>]*color\\s*=\\s*['\\\"]?#?cc0000['\\\"]?[^>]*>\\s*<b>(.*?)<br\\s*/?>\\s*</b>\\s*</font>")
                .matcher(html);
        if (redBoldSingleBrMatcher.find()) {
            String candidate = redBoldSingleBrMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }

        Matcher alertMatcher = Pattern.compile("(?is)alert\\s*\\(\\s*['\\\"](.*?)['\\\"]\\s*\\)")
                .matcher(html);
        if (alertMatcher.find()) {
            String candidate = alertMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return "";
    }

    /**
     * Публичный доступ к штатному парсеру серверного системного сообщения из main.php HTML.
     *
     * Используется UI-слоем (например, MainActivity POST-fallback), чтобы не дублировать
     * regex/marker-логику и не хардкодить конкретные фразы сообщений.
     */
    public static String extractServerNoticeForUi(String html) {
        return extractServerNoticeFromMainHtml(html);
    }
    /**
     * Центральный post-filter обработчик ответов {@code main.php}.
     *
     * Зависимости:
     * - {@link Russian#getString(byte[])} и {@link Russian#getBytes(String)} для конвертации кодировок.
     * - Ключевые ветви: бой ({@link #mainPhpFight(String, String)}), инвентарь ({@link #mainPhpInv(String)}),
     *   разделка ({@link #mainPhpRaz(String)}), fast-действия
     *   ({@link FastActionManager#processMainPhpFast(String, String, FastActionManager.MainPhpFastHost)}).
     * - Глобальное состояние в {@link AppVars} (таймеры, флаги автобоя, ссылки, статистика).
     * - Бродкасты в UI через {@link LocalBroadcastManager} и {@code AppVars.ACTION_*}.
     *
     * Назначение:
     * - Единая точка маршрутизации и постобработки HTML main.php с сохранением совместимости с C#-логикой.
     */
    public static byte[] process(String address, byte[] array) {
        String msg = "process() called for ";
        AppLog.d(TAG, msg);
        // Сохраняем исходный ответ, если он нужен где-то еще
        AppVars.lastMainPhpResponse = array;
        AppVars.IdleTimer = System.currentTimeMillis();
        AppVars.LastMainPhp = System.currentTimeMillis();
        AppVars.ContentMainPhp = null;
        String html = Russian.getString(array);
        String originalHtml = html;
        String msg_htmlLen = "HTML length after getString: " + html.length();
        AppLog.d(TAG, msg_htmlLen);
        String msg_htmlPreview = "HTML first 200: " + (html.length() > 200 ? html.substring(0, 200) : html);
        AppLog.d(TAG, msg_htmlPreview);
        html = Filter.removeDoctype(html);
        // Порт C# MainPhpInsHp.cs:
        // обновляем интервалы восстановления HP/MA из `ins_HP(...)` до основной логики,
        // чтобы расчёт `Restoring` выполнялся по актуальным `hp_int/ma_int`.
        mainPhpInsHp(html);
        // Обновляем runtime-усталость на каждом ответе main.php.
        FishAjaxPhp.mainPhpUpdateTied(html);
        // ⚠️ ВАЖНО: VCode больше НЕ кешируется в AppVars.VCode.
        // SessionManager парсит свежий vcode из каждого HTML ответа через WebViewRequestInterceptor.
        // Это предотвращает потерю vcode при смене контекста и обновления PHPSESSID.
        // Для использования: SessionManager.getInstance().getValidVCodeForAction("main_nav")
        // Системное сообщение (аналог MainPhp.cs строки 207-223).
        // Паттерн: <font class=nickname><font color=#cc0000><b>ТЕКСТ<br><br></b></font></font>
        // Диагностика: ищем cc0000 в HTML чтобы понять реальный паттерн сервера
        if (address.contains("get_id=43")) {
            int diagIdx = html.toLowerCase().indexOf("cc0000");
            if (diagIdx >= 0) {
                int start = Math.max(0, diagIdx - 80);
                int end = Math.min(html.length(), diagIdx + 200);
                String msg_cc0000 = "process: get_id=43 cc0000 context: ";
                AppLog.d(TAG, msg_cc0000);
            } else {
                String msg_nocc0000 = "process: get_id=43 — cc0000 не найден. HTML[0:300]=";
                AppLog.d(TAG, msg_nocc0000);
            }
        }
        String sysMessage = extractServerNoticeFromMainHtml(html);
        if (sysMessage != null && !sysMessage.isEmpty()) {
            String msgSys = "process: server sysMessage detected, address=" + address;
            AppLog.d(TAG, msgSys);
            postServerNotificationToChat(sysMessage, "main_php_sys_message", address);
        }
        syncInjuriesFromMapHeavyPopup(html);
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
        // Пост-боевой синхро-переход на "чистый" main.php для автопитья.
        // В некоторых потоках после act=7 следующий кадр process(...) на plain main.php не приходит сразу,
        // и tryTriggerAutoDrinkRestoreElixir(...) остаётся в режиме "wait plain main.php".
        // Делаем один явный переход, чтобы получить полноценный серверный кадр с ins_HP(...) и
        // уже на нём принять решение по порогам HP/MA (без запуска FastAction на act=7 странице).
        if (isFightFinishAddress
                && !isFightFrame
                && !isFightTopFrame
                && !isServerPlainMainAddress(address)
                && isAutoFightEnabledByPreference()
                && AppVars.Profile != null
                && (AppVars.Profile.LezDoDrinkHp || AppVars.Profile.LezDoDrinkMa)
                && !AppVars.FastNeed
                && !AppVars.IsFightCaptchaDialogVisible) {
            autoDrinkPostFightSyncPending = true;
            autoDrinkPostFightSyncPendingSinceMs = System.currentTimeMillis();
            String msg_postfight = "AUTO_DRINK_TRACE post-fight redirect to plain main.php, address=" + address
                    + ", ts=" + autoDrinkPostFightSyncPendingSinceMs;
            AppLog.d(TAG, msg_postfight);
            FileLogger.trace(TAG, msg_postfight);
            return Russian.getBytes(buildRedirectHtml("Автопитьё: синхронизация после боя", "main.php"));
        }
        // Проверка автопитья после получения верхнего фрейма персонажа.
        // При совпадении условий запускает единый fast-action "Эликсир Восстановления".
        tryTriggerAutoDrinkRestoreElixir(address, html, isFightFrame, isFightTopFrame);
        // C# parity (`AppVars.CureNeed/CureNick/CureTravm`):
        // внешний запрос лечения (self/friend/neutral) из RoomManager/UI.
        if (!isNonCombatAutoPausedByFastAction()
                && !isFightFrame
                && !isFightTopFrame
                && !AppVars.FastNeed) {
            String requestedCureHtml = mainPhpExternalRequestedCureStep(address, html);
            if (requestedCureHtml != null && !requestedCureHtml.isEmpty()) {
                return Russian.getBytes(requestedCureHtml);
            }
        }
        // C# parity: AutoCure (яд/небоевые травмы) выполняется в main.php-потоке
        // до fast-ветки, если нет активного fast-action и нет боевого кадра.
        if (!isNonCombatAutoPausedByFastAction()
                && !isFightFrame
                && !isFightTopFrame
                && !AppVars.FastNeed) {
            String autoCureHtml = mainPhpAutoCureStep(address, html);
            if (autoCureHtml != null && !autoCureHtml.isEmpty()) {
                return Russian.getBytes(autoCureHtml);
            }
        }
        // Обработка быстрых действий (портировано из MainPhp.cs строки 1429-1619)
        // В C# FastAction обрабатывается ВНУТРИ MainPhp, а не в отдельном менеджере.
        // Алгоритм: MainPhpFindInv → BuildRedirect на инвентарь → MainPhpIsInv → MainPhpFast → BuildRedirect на категорию
        if (AppVars.FastNeed) {
            byte[] fastResult = FastActionManager.processMainPhpFast(address, html, FAST_ACTION_HOST);
            if (fastResult != null) {
                return fastResult;
            }
        }
        if (!isFightFrame && !isFightTopFrame) {
            String autoDrinkPendingHtml = FishAjaxPhp.mainPhpResolveAutoDrinkBlazPending(address, html);
            if (autoDrinkPendingHtml != null && !autoDrinkPendingHtml.isEmpty()) {
                return Russian.getBytes(autoDrinkPendingHtml);
            }
        }
        
        // КРИТИЧНО: После завершения fast-action, если нужна проверка снастей,
        // ОБЯЗАТЕЛЬНО переходим на im=0 (основной инвентарь), а не остаемся на текущей категории.
        // ИСКЛЮЧЕНИЕ: если текущий инвентарь im=6 (эликсиры), не переключаемся (это означает был пит эликсир).
        // Иначе, если был открыт инвентарь на im=6 (эликсиры), авто-рыбалка не найдет удочки.
        if (!AppVars.FastNeed && (AppVars.AutoFishCheckUd || AppVars.AutoFishWearUd)) {
            boolean isInventoryPage = mainPhpIsInv(html) || isInventoryAddress(address);
            boolean isEliximInventory = address.contains("&im=6");  // эликсиры - был fast-action
            isEliximInventory = isEliximInventory || inventoryAddressMatchesFilter(address, "&im=6");
            if (isInventoryPage && isEliximInventory && !inventoryAddressMatchesFilter(address, "&im=0&wca=4")) {
                String msg_postfast = "⚠️ [AUTO_FISH_POST_FAST] post-fast-action: forcing switch to inventory im=0 for gear check (current=" + address + ")";
                AppLog.w(TAG, msg_postfast);
                return Russian.getBytes(buildRedirectHtml("Переключение на вещи для проверки снастей", "main.php?im=0&wca=4"));
            }
        }
        
        // One-shot post-fast resume:
        // after any FastAction completion, force "Return" to map from non-map pages.
        if (!isNonCombatAutoPausedByFastAction()
                && !isFightFrame
                && !isFightTopFrame
                && AppVars.FastReturnToMapPending
                && !AppVars.FastNeed
                && !html.contains("var map = [[")) {
            String mapReturnHtml = mainPhpFindMapReturnForAutoMoving(html);
            if (mapReturnHtml != null && !mapReturnHtml.isEmpty()) {
                AppVars.FastReturnToMapPending = false;
                String msg_fastreturn = "FAST_ACTION_TRACE force return-to-map after fast action, address=";
                AppLog.d(TAG, msg_fastreturn);
                return Russian.getBytes(mapReturnHtml);
            }
        }
        // Чтение умения "Охота" (C# parity) до оркестрации AutoSkin,
        // чтобы `AutoSkinCheckUm` корректно сбрасывался на `mselect=1`.
        if (!isNonCombatAutoPausedByFastAction()) {
            mainPhpProcessSkills(html, address);
        }
        // Чтение умения "Рыбалка" (C# parity) до оркестрации AutoFish.
        if (!isNonCombatAutoPausedByFastAction()) {
            FishAjaxPhp.mainPhpProcessFishSkills(html, address);
        }

        // ========== ТАЙМЕР: Одевание комплектов ==========
        // Порт C# FormMainTimers.cs: если таймер комплекта сработал,
        // AppVars.WearComplect содержит название комплекта.
        // Парсим HTML комплектов, находим нужный и отправляем запрос на одевание.
        if (!AppVars.WearComplect.isEmpty() && !isFightFrame && !isFightTopFrame) {
            String msg_start = "COMPLECT_TIMER_TRACE start wear, AppVars.WearComplect=\"" + AppVars.WearComplect + "\"";
            AppLog.d(TAG, msg_start);
            String complectWearHtml = mainPhpWearComplect(html, AppVars.WearComplect);
            if (complectWearHtml != null && !complectWearHtml.isEmpty()) {
                String msg_complect = "COMPLECT_TIMER_TRACE redirect to wear complect, name=" + AppVars.WearComplect;
                AppLog.d(TAG, msg_complect);
                AppVars.WearComplect = "";  // Очищаем флаг после отправки
                return Russian.getBytes(complectWearHtml);
            } else {
                // Комплект не найден - отключаем флаг и выводим ошибку
                String msg_complect_notfound = "COMPLECT_TIMER_TRACE complect not found: \"" + AppVars.WearComplect + "\"";
                AppLog.d(TAG, msg_complect_notfound);
                AppVars.WearComplect = "";
            }
        }

        // C# parity: DoAutoDrinkBlaz.
        // Порядок вызова: до AutoFish/AutoSearchBox, чтобы при высокой усталости сначала обнулить ее.
        if (!isNonCombatAutoPausedByFastAction() && !isFightFrame && !isFightTopFrame) {
            String autoDrinkBlazHtml = FishAjaxPhp.mainPhpAutoDrinkBlazStep(address, html);
            if (autoDrinkBlazHtml != null && !autoDrinkBlazHtml.isEmpty()) {
                return Russian.getBytes(autoDrinkBlazHtml);
            }
        }
        // Оркестрация Авто-Рыбалки (C# MainPhp.cs + MainPhpWear.cs + MainPhpFish.cs):
        // 1) при необходимости читаем умение Рыбалка (mselect=1);
        // 2) проверяем/переодеваем снасти в обеих руках;
        // 3) на странице рыбалки выбираем приманку и формируем FightLink.
        boolean autoFightReloadProbeAddress = isAutoFightReloadProbeAddress(address);
        if (autoFightReloadProbeAddress && FishAjaxPhp.isAutoFishEnabledByPreference()) {
            String msg_fishskip = "AUTO_FISH_TRACE skip: auto-fight reload probe address=";
            AppLog.d(TAG, msg_fishskip);
        }
        
        // Восстановление авто-рыбалки, если она была отключена во время переходов на персонаж/инвентарь
        // и теперь мы вернулись на природу (go=ret или mainPhpFindFlora() вернула null).
        // ВАЖНО: восстанавливаем только если на текущей клетке есть вода (mainPhpFindFish() != null).
        boolean isOnMapOrNature = mainPhpFindFlora(html) == null; // null = уже на карте
        if (isOnMapOrNature && FishAjaxPhp.isAutoFishEnabledByPreference()) {
            FishAjaxPhp.recoverAutofishRuntimeStateIfNeeded(true, html, address);
        }
        
        // ДИАГНОСТИКА: Логирование всех условий блокировки рыбалки на холодном старте
        boolean isFastActionPaused = isNonCombatAutoPausedByFastAction();
        boolean shouldEnterFishingLogic = !isFastActionPaused && !isFightFrame && !isFightTopFrame
                && !autoFightReloadProbeAddress && FishAjaxPhp.isAutoFishEnabledByPreference();
        if (!shouldEnterFishingLogic && FishAjaxPhp.isAutoFishEnabledByPreference()) {
            StringBuilder diagnostics = new StringBuilder();
            diagnostics.append("AUTO_FISH_TRACE BLOCKED: ");
            diagnostics.append("FastNeed=").append(isFastActionPaused).append(", ");
            diagnostics.append("isFightFrame=").append(isFightFrame).append(", ");
            diagnostics.append("isFightTopFrame=").append(isFightTopFrame).append(", ");
            diagnostics.append("autoFightProbe=").append(autoFightReloadProbeAddress);
            String msg_block = diagnostics.toString();
            AppLog.d(TAG, msg_block);
        }
        
        if (!isNonCombatAutoPausedByFastAction() && !isFightFrame && !isFightTopFrame
                && !autoFightReloadProbeAddress && FishAjaxPhp.isAutoFishEnabledByPreference()) {
            long nowMs = System.currentTimeMillis();
            FishAjaxPhp.mainPhpPrecheckFishingHandsByInfoApi(nowMs, address, "mainphp_autofish_gate");
            String fishFatigueHtml = FishAjaxPhp.mainPhpAutoFishFatigueStep(html);
            if (fishFatigueHtml != null && !fishFatigueHtml.isEmpty()) {
                String msg_fishfatigue = "AUTO_FISH_TRACE fatigue step executed";
                AppLog.d(TAG, msg_fishfatigue);
                return Russian.getBytes(fishFatigueHtml);
            }
            boolean neverTimerReady = AppVars.NeverTimer <= 0L || nowMs > AppVars.NeverTimer;
            if (neverTimerReady) {
                if (AppVars.AutoFishCheckUm) {
                    String phtml = mainPhpFindPerc(html);
                    if (phtml != null && !phtml.isEmpty()) {
                        String msg_fishchar = "AUTO_FISH_TRACE redirect to character page for skill check";
                        AppLog.d(TAG, msg_fishchar);
                        return Russian.getBytes(phtml);
                    }
                    if (html.toLowerCase(Locale.ROOT).contains("<input type=button class=lbut value=\"умения\" onclick")) {
                        String msg_fishskills = "AUTO_FISH_TRACE redirect to skills page mselect=1";
                        AppLog.d(TAG, msg_fishskills);
                        return Russian.getBytes(buildRedirectHtml("Переключение на умения персонажа", "main.php?mselect=1"));
                    }
                }
                long postDrinkCooldownRemainingMs = FishAjaxPhp.getAutoFishDrinkCooldownRemainingMs(nowMs);
                if (postDrinkCooldownRemainingMs > 0L) {
                    String msg_deferFish = "AUTO_FISH_TRACE defer non-fight fish steps during drink cooldown: remainingMs="
                            + postDrinkCooldownRemainingMs
                            + ", AutoFishCheckUd=" + AppVars.AutoFishCheckUd
                            + ", AutoFishWearUd=" + AppVars.AutoFishWearUd
                            + ", address=" + address;
                    AppLog.d(TAG, msg_deferFish);
                    return Russian.getBytes(FishAjaxPhp.buildAutoFishDrinkCooldownHtml(postDrinkCooldownRemainingMs));
                }
                if (AppVars.AutoFishCheckUd) {
                    String perchtml = mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        String msg_fishgear = "AUTO_FISH_TRACE redirect to character page for fishing gear check";
                        AppLog.d(TAG, msg_fishgear);
                        return Russian.getBytes(perchtml);
                    }
                    AppVars.AutoFishWearUd = false;
                    if (mainPhpIsPerc(html)) {
                        AppVars.AutoFishWearUd = FishAjaxPhp.mainPhpIsMustWearUd(html);
                        AppVars.AutoFishCheckUd = false;
                        if (AppVars.AutoFishWearUd) {
                            String loopKey = FishAjaxPhp.buildAutoFishWearLoopKey();
                            boolean wearLoopBroken = FishAjaxPhp.markAutoFishWearLoop(loopKey);
                            if (wearLoopBroken) {
                                FishAjaxPhp.restartAutoFishCycle("wear_loop");
                                return array;
                            }
                        } else {
                            FishAjaxPhp.resetAutoFishWearLoopGuard();
                        }
                        String msg_gearresult = "AUTO_FISH_TRACE gear check result: mustWear="
                                + AppVars.AutoFishWearUd
                                + ", hand1=" + AppVars.AutoFishHand1
                                + ", hand1D=" + AppVars.AutoFishHand1D
                                + ", hand2=" + AppVars.AutoFishHand2
                                + ", hand2D=" + AppVars.AutoFishHand2D;
                        AppLog.d(TAG, msg_gearresult);
                    }
                }
                if (AppVars.AutoFishWearUd) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=4", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        String msg_fishudred = "AUTO_FISH_TRACE redirect to inventory for fishing gear (&im=0&wca=4)";
                        AppLog.d(TAG, msg_fishudred);
                        return Russian.getBytes(invHtml);
                    }
                    if (mainPhpIsInv(html) || isInventoryAddress(address)) {
                        invHtml = FishAjaxPhp.mainPhpWearUd(html);
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=4")) {
                                String msg_fishudswitch = "AUTO_FISH_TRACE switch to items tab for fishing gear search";
                                AppLog.d(TAG, msg_fishudswitch);
                                return Russian.getBytes(buildRedirectHtml("Переключение на вещи", "main.php?im=0&wca=4"));
                            }
                        } else {
                            return Russian.getBytes(invHtml);
                        }
                    }
                }
                if (!neverTimerReady) {
                    String msg_waitFish = "AUTO_FISH_TRACE wait NeverTimer before fish action: dueInMs="
                            + Math.max(0L, AppVars.NeverTimer - nowMs);
                    AppLog.d(TAG, msg_waitFish);
                } else {
                    // C# parity (`MainPhpFindFlora`): если мы не на карте и есть кнопка "Вернуться",
                    // автоматически возвращаемся на природу перед поиском кнопки "Рыбалка".
                    String floraHtml = mainPhpFindFlora(html);
                    if (floraHtml != null && !floraHtml.isEmpty()) {
                        String msg_florareturn = "AUTO_FISH_TRACE redirect to nature/map via return button";
                        AppLog.d(TAG, msg_florareturn);
                        return Russian.getBytes(floraHtml);
                    }
                    // ★ КРИТИЧНО: проверяем, находимся ли уже на озере (есть ли форма выбора приманки)
                    // На озере есть input type=radio name=primid для выбора приманки
                    boolean isWeAlreadyOnLake = html.contains("name=primid") || html.contains("name=\"primid\"");

                    if (isWeAlreadyOnLake) {
                        // Мы на озере с формой выбора приманки - нужно выбрать и отправить act=2
                        String msg_onlake = "AUTO_FISH_TRACE detected lake form (name=primid found), calling mainPhpAutoFishPrepare...";
                        AppLog.d(TAG, msg_onlake);

                        String fishPreparedHtml = FishAjaxPhp.mainPhpAutoFishPrepare(html);

                        String msg_after_prepare = "AUTO_FISH_TRACE mainPhpAutoFishPrepare: result is " + (fishPreparedHtml == null ? "NULL" : "non-null");
                        AppLog.d(TAG, msg_after_prepare);

                        if (fishPreparedHtml != null) {
                            html = fishPreparedHtml;
                            boolean hasCaptcha = AppVars.CodeAddress != null && !AppVars.CodeAddress.isEmpty();
                            boolean isFishActionAddress = address != null
                                    && address.contains("get_id=55")
                                    && address.contains("act=4");
                            if (hasCaptcha && AppVars.FightLink != null && !AppVars.FightLink.isEmpty() && !isFishActionAddress) {
                                String msg_fishcapt = "AUTO_FISH_TRACE captcha required, show dialog for fish action";
                                AppLog.d(TAG, msg_fishcapt);
                                FishAjaxPhp.showMainPhpFishCaptchaDialogOnce(AppVars.CodeAddress, AppVars.FightLink);
                                return Russian.getBytes(FishAjaxPhp.buildCaptchaDialogHoldHtml());
                            }
                            if (hasCaptcha && AppVars.IsFightCaptchaDialogVisible) {
                                String msg_fishcapthold = "AUTO_FISH_TRACE captcha dialog is visible, keep hold page";
                                AppLog.d(TAG, msg_fishcapthold);
                                return Russian.getBytes(FishAjaxPhp.buildCaptchaDialogHoldHtml());
                            }
                            if (!hasCaptcha && AppVars.FightLink != null && !AppVars.FightLink.isEmpty() && !isFishActionAddress) {
                                String msg_fishaction = "AUTO_FISH_TRACE redirect to fish action: ";
                                AppLog.d(TAG, msg_fishaction);
                                return Russian.getBytes(buildRedirectHtml("Авторыбалка: заброс", AppVars.FightLink));
                            }
                        }
                    } else {
                        // ★ НЕ НА ОЗЕРЕ: озеро ещё не открыто, инжектируем Fish() для открытия формы озера
                        String msg_notlake = "AUTO_FISH_TRACE no lake form detected (name=primid NOT found), injecting Fish()...";
                        AppLog.d(TAG, msg_notlake);

                        // C# parity: на карте автоматически нажимаем "Рыбалка", чтобы открыть форму выбора приманки.
                        String fishMapHtml = FishAjaxPhp.mainPhpFindFish(html);
                        if (fishMapHtml != null && !fishMapHtml.isEmpty()) {
                            String msg_fishmap = "AUTO_FISH_TRACE inject Fish(vcode) into map frame";
                            AppLog.d(TAG, msg_fishmap);
                            return Russian.getBytes(fishMapHtml);
                        }
                        String msg_nofish = "AUTO_FISH_TRACE warning: Fish button not found on current page, skipping auto-fish";
                        AppLog.w(TAG, msg_nofish);
                    }
                }
            } else {
                String msg_waitFish = "AUTO_FISH_TRACE wait NeverTimer before fish action: dueInMs="
                        + Math.max(0L, AppVars.NeverTimer - nowMs);
                AppLog.d(TAG, msg_waitFish);
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
                        String msg_furychar = "AUTO_FURY_TRACE redirect to character page for scroll check";
                        AppLog.d(TAG, msg_furychar);
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
                        String msg_furyinv = "AUTO_FURY_TRACE redirect to scroll inventory (&im=0&wca=28)";
                        AppLog.d(TAG, msg_furyinv);
                        return Russian.getBytes(invHtml);
                    }
                    if (mainPhpIsInv(html) || isInventoryAddress(address)) {
                        invHtml = mainPhpWearFuryScroll(html);
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=28")) {
                                String msg_furityab = "AUTO_FURY_TRACE switch to scroll category (wca=28)";
                                AppLog.d(TAG, msg_furityab);
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
        // Точечный фикс регрессии после разделки:
        // в переходном кадре `main.php?r=...` (inventoryReload=true) оркестратор AutoSkin
        // приостановлен, но именно в этом кадре сервер часто уже отдаёт обновлённые ресурсы.
        // Если ждём только следующий "чистый" кадр, результат разделки может не попасть в чат.
        //
        // Зависимости:
        // - `AppVars.AutoSkinCheckRes` выставляется после `get_id=17` (см. publishFightResultFromLogsIfNeeded);
        // - `mainPhpGetSkinRes(...)` обновляет статистику и (при RazdChatReport=true) шлёт сообщение в чат;
        // - `mainPhpIsInv(...)` / `inventoryAddressMatchesFilter(..., "&im=5")` подтверждают, что HTML уже инвентарь ресурсов.
        if (!isNonCombatAutoPausedByFastAction()
                && !isFightFrame
                && !isFightTopFrame
                && isAutoSkinEnabledByPreference()
                && !suspendAutoSkinForFinishFlow
                && !suspendAutoSkinForGeneratedTransition
                && suspendAutoSkinForInventoryReload
                && AppVars.AutoSkinCheckRes
                && (mainPhpIsInv(html) || inventoryAddressMatchesFilter(address, "&im=5"))) {
            AppVars.AutoSkinCheckRes = false;
            String msg_skinload = "AUTO_SKIN_TRACE inventoryReload fallback: read skin resources in transition snapshot";
            AppLog.d(TAG, msg_skinload);
            mainPhpGetSkinRes(html);
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
                        String msg_skinchar = "AUTO_SKIN_TRACE redirect to character page for skill check";
                        AppLog.d(TAG, msg_skinchar);
                        return Russian.getBytes(phtml);
                    }
                    if (html.toLowerCase(Locale.ROOT).contains("<input type=button class=lbut value=\"умения\" onclick")) {
                        String msg_skinskills = "AUTO_SKIN_TRACE redirect to skills page mselect=1";
                        AppLog.d(TAG, msg_skinskills);
                        return Russian.getBytes(buildRedirectHtml("Переключение на умения персонажа", "main.php?mselect=1"));
                    }
                }
                if (AppVars.AutoSkinCheckRes) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=5", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        String msg_skinres = "AUTO_SKIN_TRACE redirect to resources inventory (&im=5)";
                        AppLog.d(TAG, msg_skinres);
                        return Russian.getBytes(invHtml);
                    }
                    if (mainPhpIsInv(html) || inventoryAddressMatchesFilter(address, "&im=5")) {
                        AppVars.AutoSkinCheckRes = false;
                        String msg_skingetres = "AUTO_SKIN_TRACE read skin resources";
                        AppLog.d(TAG, msg_skingetres);
                        mainPhpGetSkinRes(html);
                    }
                }
                if (AppVars.AutoSkinCheckKnife) {
                    String perchtml = mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        String msg_skinknife = "AUTO_SKIN_TRACE redirect to character page for knife check";
                        AppLog.d(TAG, msg_skinknife);
                        return Russian.getBytes(perchtml);
                    }
                    AppVars.AutoSkinArmedKnife = false;
                    if (mainPhpIsPerc(html)) {
                        AppVars.AutoSkinArmedKnife = mainPhpArmedKnife(html);
                        AppVars.AutoSkinCheckKnife = false;
                        String msg_skinresult = "AUTO_SKIN_TRACE knife check result: armed=";
                        AppLog.d(TAG, msg_skinresult);
                    }
                }
                if (!AppVars.AutoSkinArmedKnife) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=4", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        String msg_skinudinv = "AUTO_SKIN_TRACE redirect to items inventory (&im=0&wca=4)";
                        AppLog.d(TAG, msg_skinudinv);
                        return Russian.getBytes(invHtml);
                    }
                    if (mainPhpIsInv(html) || isInventoryAddress(address)) {
                        invHtml = mainPhpWearKnife(html);
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=4")) {
                                String msg_skinudtab = "AUTO_SKIN_TRACE switch to items tab for knife search";
                                AppLog.d(TAG, msg_skinudtab);
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
                String msg_fightnull = "process: mainPhpFight returned null, fallback to original HTML";
                AppLog.w(TAG, msg_fightnull);
                html = originalHtml;
            }
            // Preserve original fight HTML for manual mode (avoid losing images after auto frame)
            AppVars.ContentMainPhp = originalHtml;
        }
        if (!isFightFrame && !isFightTopFrame) {
            String autoTreasureDigHtml = TreasureDig.maybeStopAutoTreasureOnDig(html, address, TREASURE_DIG_HOST);
            if (autoTreasureDigHtml != null) {
                html = autoTreasureDigHtml;
                AppVars.ContentMainPhp = html;
                return Russian.getBytes(html);
            }
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
                String msg_invfallback = "INV_TRACE structural fallback matched: address=";
                AppLog.d(TAG, msg_invfallback);
            }
            html = mainPhpInv(html);
        }

        // C# parity (`DoSearchBox && !AutoMoving && DateTime.Now > NeverTimer`):
        // запускаем обход карты в поиске следующей "непосещенной" клетки.
        boolean autoSearchRetryAfterMapSync = false;
        if (!isNonCombatAutoPausedByFastAction()
                && !isNonCombatAutoPausedByCureAction()
                && !isFightFrame
                && !isFightTopFrame
                && AppVars.DoSearchBox
                && !AppVars.AutoMoving) {
            String currentMapLocation = AppVars.Profile != null ? AppVars.Profile.MapLocation : null;
            boolean hasMapPayload = html.contains("var map = [[");
            boolean bootstrapFromReload = address != null && address.contains("ab_search_box_bootstrap=1");
            boolean needMapBootstrap = !hasMapPayload
                    && (bootstrapFromReload || currentMapLocation == null || currentMapLocation.isEmpty());
            if (needMapBootstrap) {
                String mapReturnHtml = mainPhpFindMapReturnForAutoMoving(html);
                if (mapReturnHtml != null && !mapReturnHtml.isEmpty()) {
                    android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE bootstrap map via return-link, address=" + address
                            + ", mapLocation=" + currentMapLocation);
                    return Russian.getBytes(mapReturnHtml);
                }
                boolean isInfAddress = address != null && address.contains("get_id=56&act=10&go=inf");
                if (!isInfAddress) {
                    String personHtml = mainPhpFindPerc(html);
                    if (personHtml != null && !personHtml.isEmpty()) {
                        android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE bootstrap person page before map, address="
                                + address + ", mapLocation=" + currentMapLocation);
                        return Russian.getBytes(personHtml);
                    }
                }
                if (bootstrapFromReload) {
                    String bootstrapRetLink = "main.php?get_id=56&act=10&go=ret";
                    // ✅ SessionManager: получаем валидный vcode для bootstrap
                    String vcode = SessionManager.getInstance().getValidVCodeForAction("searchbox_bootstrap");
                    if (vcode != null && !vcode.trim().isEmpty()) {
                        bootstrapRetLink += "&vcode=" + vcode.trim();
                    } else {
                        String msg_vcode_err = "⚠️ AUTO_SEARCH_BOX_TRACE: vcode not available from SessionManager";
                        AppLog.w(TAG, msg_vcode_err);
                    }
                    android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE bootstrap fallback go=ret, address="
                            + address + ", mapLocation=" + currentMapLocation + ", link=" + bootstrapRetLink);
                    return Russian.getBytes(buildRedirectHtml("SearchBox bootstrap: go=ret", bootstrapRetLink));
                }
            }
            long nowMs = System.currentTimeMillis();
            if (AppVars.NeverTimer <= 0L || nowMs > AppVars.NeverTimer) {
                String nextDest = MapAjax.findNextDestForBox(currentMapLocation);
                if (nextDest != null && !nextDest.isEmpty()) {
                    startAutoSearchBoxMoving(nextDest);
                    android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE start moving to " + nextDest
                            + ", address=" + address);
                } else {
                    android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE no destination yet, mapLocation="
                            + currentMapLocation + ", address=" + address + ", hasMapPayload=" + hasMapPayload);
                    if (hasMapPayload && (currentMapLocation == null || currentMapLocation.isEmpty())) {
                        autoSearchRetryAfterMapSync = true;
                    }
                }
            }
        }

        if (!isNonCombatAutoPausedByFastAction()
                && !isNonCombatAutoPausedByCureAction()
                && !AppVars.AutoDrinkBlazPending
                && AppVars.AutoMoving
                && html.contains(" id=wtime>")) {
            html = mainPhpWtime(html);
            AppVars.ContentMainPhp = html;
            return Russian.getBytes(html);
        }
        if (!isNonCombatAutoPausedByFastAction()
                && !isNonCombatAutoPausedByCureAction()
                && !AppVars.AutoDrinkBlazPending
                && AppVars.AutoMoving) {
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
                    String msg_moving = "AUTO_MOVING_TRACE: redirect to map from ";
                    AppLog.d(TAG, msg_moving);
                    return Russian.getBytes(mapReturnHtml);
                }
                if (address != null && address.contains("ab_nav_bootstrap=1")) {
                    String bootstrapRetLink = "main.php?get_id=56&act=10&go=ret";
                    // ✅ SessionManager: получаем валидный vcode для navigator bootstrap
                    String vcode = SessionManager.getInstance().getValidVCodeForAction("nav_bootstrap");
                    if (vcode != null && !vcode.trim().isEmpty()) {
                        bootstrapRetLink += "&vcode=" + vcode.trim();
                    } else {
                        String msg_navvcode = "⚠️ AUTO_MOVING_TRACE: vcode not available from SessionManager";
                        AppLog.w(TAG, msg_navvcode);
                    }
                    android.util.Log.d(TAG, "AUTO_MOVING_TRACE: bootstrap fallback to map, address="
                            + address + ", link=" + bootstrapRetLink);
                    return Russian.getBytes(buildRedirectHtml("Navigator bootstrap: go=ret", bootstrapRetLink));
                }
            }
        }
        if (html.contains("var map = [[")) {
            if (AppVars.FastReturnToMapPending) {
                AppVars.FastReturnToMapPending = false;
                String msg_mapok = "FAST_ACTION_TRACE map reached, clear return-to-map pending flag";
                AppLog.d(TAG, msg_mapok);
            }
            html = MapAjax.process(html);
            if (autoSearchRetryAfterMapSync
                    && !isNonCombatAutoPausedByFastAction()
                    && !isNonCombatAutoPausedByCureAction()
                    && AppVars.DoSearchBox
                    && !AppVars.AutoMoving) {
                String refreshedMapLocation = AppVars.Profile != null ? AppVars.Profile.MapLocation : null;
                long retryNowMs = System.currentTimeMillis();
                if (AppVars.NeverTimer <= 0L || retryNowMs > AppVars.NeverTimer) {
                    String retryDest = MapAjax.findNextDestForBox(refreshedMapLocation);
                    if (retryDest != null && !retryDest.isEmpty()) {
                        startAutoSearchBoxMoving(retryDest);
                        android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE retry-after-map-sync start moving to "
                                + retryDest + ", mapLocation=" + refreshedMapLocation + ", address=" + address);
                        // ✅ SessionManager: получаем вaлидный vcode для retry bootstrap
                        String navVcode = SessionManager.getInstance().getValidVCodeForAction("searchbox_retry_bootstrap");
                        if (navVcode == null) {
                            navVcode = "";
                        } else {
                            navVcode = navVcode.trim();
                        }
                        String bootstrapLink;
                        if (!navVcode.isEmpty()) {
                            bootstrapLink = "main.php?get_id=56&act=10&go=ret&vcode="
                                    + navVcode + "&ab_nav_bootstrap=1&r=" + System.currentTimeMillis();
                        } else {
                            bootstrapLink = "main.php?get_id=56&act=10&go=inf&ab_nav_bootstrap=1&r="
                                    + System.currentTimeMillis();
                        }
                        return Russian.getBytes(buildRedirectHtml("SearchBox retry bootstrap", bootstrapLink));
                    } else {
                        android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE retry-after-map-sync still no destination, mapLocation="
                                + refreshedMapLocation + ", address=" + address);
                    }
                }
            }
        }
        if (!(isFightFrame || isFightTopFrame)) {
            AppVars.ContentMainPhp = html;
        }
        byte[] result = Russian.getBytes(html);
        String msg_returning = "process() returning ";
        AppLog.d(TAG, msg_returning);
        String msg_preview = "Result first 200: ";
        AppLog.d(TAG, msg_preview);
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
        return FastActionManager.processMainPhpFast(address, html, FAST_ACTION_HOST);
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
    static boolean isAutoFightReloadProbeAddress(String address) {
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
     * Признак фонового probe-запроса авто-боя (`ab_bg_probe=1`).
     *
     * Назначение:
     * - отделить технические перезагрузки боевого контекста от обычной навигации;
     * - применять более осторожные правила завершения боя на таких кадрах.
     */
    private static boolean isAutoFightBackgroundProbeAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        String lower = address.toLowerCase(Locale.ROOT);
        return lower.contains("main.php") && lower.contains("ab_bg_probe=1");
    }
    /**
     * Единая проверка: текущий адрес относится к probe-потоку авто-боя.
     *
     * Зависимости:
     * - `isAutoFightReloadProbeAddress(...)`;
     * - `isAutoFightBackgroundProbeAddress(...)`.
     */
    private static boolean isAutoFightProbeAddress(String address) {
        return isAutoFightReloadProbeAddress(address) || isAutoFightBackgroundProbeAddress(address);
    }
    /**
     * Строит стабильный ключ кандидата завершения боя.
     *
     * Правило приоритета:
     * 1) `LogBoi`, если доступен;
     * 2) нормализованная `fightLink`;
     * 3) `unknown`.
     *
     * Ключ используется для подтверждения финиша на probe-кадрах.
     */
    private static String buildAutoFightProbeFinishCandidateKey(String logBoi, String fightLink) {
        String log = logBoi == null ? "" : logBoi.trim();
        if (!log.isEmpty()) {
            return "log:" + log;
        }
        String link = fightLink == null ? "" : normalizeNeverlandsMainLink(fightLink);
        if (link != null && !link.isEmpty()) {
            return "link:" + link;
        }
        return "unknown";
    }
    /**
     * Сбрасывает состояние подтверждения probe-финиша.
     *
     * Вызывается:
     * - при возврате в активную фазу боя (`IsBoi=true`);
     * - при переходе в ветки, где подтверждение больше не требуется
     *   (например, явная капча или валидный direct-finish вне probe).
     */
    private static void clearAutoFightProbeFinishCandidate() {
        lastAutoFightProbeFinishCandidateKey = "";
        lastAutoFightProbeFinishCandidateAtMs = 0L;
    }
    /**
     * Подтверждает завершение боя на probe-кадре по правилу "два совпадения подряд".
     *
     * Логика:
     * - первый вызов только фиксирует кандидата и возвращает `false`;
     * - второй вызов с тем же кандидатом в пределах `AUTO_FIGHT_PROBE_FINISH_CONFIRM_WINDOW_MS`
     *   возвращает `true` и сбрасывает состояние.
     *
     * Назначение:
     * - убрать ложные остановки авто-боя на переходных кадрах, где `fight_ty` уже неактивен,
     *   но реальное завершение ещё не подтверждено серверным потоком.
     */
    private static boolean isAutoFightProbeFinishConfirmed(String logBoi, String fightLink) {
        String candidateKey = buildAutoFightProbeFinishCandidateKey(logBoi, fightLink);
        long now = System.currentTimeMillis();
        boolean confirmed = !candidateKey.isEmpty()
                && candidateKey.equals(lastAutoFightProbeFinishCandidateKey)
                && (now - lastAutoFightProbeFinishCandidateAtMs) <= AUTO_FIGHT_PROBE_FINISH_CONFIRM_WINDOW_MS;
        if (confirmed) {
            clearAutoFightProbeFinishCandidate();
            return true;
        }
        lastAutoFightProbeFinishCandidateKey = candidateKey;
        lastAutoFightProbeFinishCandidateAtMs = now;
        return false;
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
    static boolean containsIgnoreCase(String value, String token) {
        if (value == null || token == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }
    /**
     * Проверяет, что мы на странице инвентаря (аналог MainPhpIsInv в MainPhpDrink.cs:221-224).
     * Инвентарь содержит ссылку <a href="?im=0"><img...
     */
    static boolean mainPhpIsInv(String html) {
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
        // Для вкладок эликсиров/зелий нет "Надеть/Продать", но есть действия использования.
        boolean hasUseAction = containsIgnoreCase(html, "value=\"Использовать\"")
                || containsIgnoreCase(html, "confirm('Использовать ")
                || containsIgnoreCase(html, "get_id=43")
                || containsIgnoreCase(html, "magicreform(")
                || containsIgnoreCase(html, "w28_form(");
        boolean hasWearOrSell = containsIgnoreCase(html, "value=\"Надеть\"")
                || containsIgnoreCase(html, "value=\"Продать")
                || containsIgnoreCase(html, "image.neverlands.ru/del.gif")
                || hasUseAction;
        boolean hasInventoryTabs = containsIgnoreCase(html, "<a href=\"?im=")
                || containsIgnoreCase(html, "<a href=?im=")
                || containsIgnoreCase(html, "main.php?im=");
        boolean hasItemActions = containsIgnoreCase(html, "get_id=57")
                || containsIgnoreCase(html, "get_id=58")
                || containsIgnoreCase(html, "if(top.deletetrue('")
                || containsIgnoreCase(html, "image.neverlands.ru/del.gif")
                || containsIgnoreCase(html, "get_id=43")
                || containsIgnoreCase(html, "magicreform(")
                || containsIgnoreCase(html, "w28_form(");
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
    static String mainPhpFindInvWithFallback(String html, String filter, String address) {
        // Анти-зацикливание: если уже на go=inv с нужным фильтром, redirect не нужен.
        if (inventoryAddressMatchesFilter(address, filter)) {
            return null;
        }
        // Если уже на go=inv, но фильтр не совпадает, синхронизируем адрес с нужными параметрами.
        if (isInventoryAddress(address)) {
            String normalizedAddress = normalizeNeverlandsMainLink(address);
            String filteredAddress = applyInventoryFilterToLink(normalizedAddress, filter);
            if (!normalizedAddress.equals(filteredAddress)) {
                String msg = "AUTO_FALLBACK_TRACE mainPhpFindInv: address filter sync -> ";
                AppLog.d(TAG, msg);
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
        String msg = "AUTO_FALLBACK_TRACE mainPhpFindInv: regex fallback -> ";
        AppLog.d(TAG, msg);
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
    static String buildRedirectHtml(String description, String link) {
        String normalizedLink = normalizeNeverlandsMainLink(link);
        return ru.neverlands.abclient.utils.HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head><body>" +
                description +
                "<script language=\"JavaScript\">window.location = \"" + normalizedLink + "\";</script></body></html>";
    }
    /**
     * Тонкая обёртка боевого post-filter после выноса в {@link FightAuto}.
     *
     * Правило: в MainPhp остаётся только делегирование, а вся боевая логика/finish-flow
     * (пороги HP/MA, ветки CAPTCHA, FEND/FightLink, ожидание хода и авто-обновление)
     * поддерживается в модуле {@link FightAuto}.
     *
     * Зависимости:
     * - {@link FightAuto#processFight(String, String, FightAuto.Host)} — основная реализация;
     * - {@link #FIGHT_AUTO_HOST} — bridge к инфраструктурным helper-методам MainPhp.
     */
    private static String mainPhpFight(String address, String html) {
        return FightAuto.processFight(address, html, FIGHT_AUTO_HOST);
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
    static String extractCaptchaUrl(String html) {
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
                String msg = "extractCaptchaUrl: found ";
                AppLog.d(TAG, msg);
                return url;
            }
            if (html != null && html.contains("code.php")) {
                String msg = "extractCaptchaUrl: code.php present but url pattern not matched";
                AppLog.d(TAG, msg);
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
                String msg_visible_same = "showFightCaptchaDialogOnce: dialog already visible for same key, skip";
                AppLog.d(TAG, msg_visible_same);
                return;
            }
            // Если challenge изменился при уже открытом popup — отправляем обновление.
            // MainActivity сам корректно заменяет текущее окно на новое.
            String msg_visible_new = "showFightCaptchaDialogOnce: dialog visible, update to new key";
            AppLog.d(TAG, msg_visible_new);
            lastFightCaptchaDialogKey = key;
            lastFightCaptchaDialogAtMs = now;
            if (AppVars.getContext() == null) {
                String msg_visible_null = "showFightCaptchaDialogOnce: context is null while updating dialog";
                AppLog.w(TAG, msg_visible_null);
                return;
            }
            Intent updateIntent = new Intent(AppVars.ACTION_SHOW_CAPTCHA);
            updateIntent.putExtra("captchaUrl", captchaUrl);
            updateIntent.putExtra("finishUrl", normalizedFinishUrl);
            LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(updateIntent);
            return;
        }
        if (key.equals(lastFightCaptchaDialogKey) && (now - lastFightCaptchaDialogAtMs) < 3000L) {
            String msg_duplicate = "showFightCaptchaDialogOnce: duplicate key, skip dialog";
            AppLog.d(TAG, msg_duplicate);
            return;
        }
        lastFightCaptchaDialogKey = key;
        lastFightCaptchaDialogAtMs = now;
        if (AppVars.getContext() == null) {
            String msg_null_final = "showFightCaptchaDialogOnce: context is null, skip dialog";
            AppLog.w(TAG, msg_null_final);
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
        String msg_main = "mainPhpFightEnd: processing fight end page";
        AppLog.d(TAG, msg_main);
        
        // Проверяем, есть ли уже параметры в адресе (URL содержит все нужные параметры)
        if (address.contains("fexp=") && address.contains("act=7")) {
            String msg_fexp = "mainPhpFightEnd: has fexp, building redirect";
            AppLog.d(TAG, msg_fexp);
            
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
                String msg_error = "mainPhpFightEnd: server returned error page, returning original HTML";
                AppLog.d(TAG, msg_error);
                return html;
            }
            
            // Ищем форму завершения боя в HTML - если есть, извлекаем параметры и делаем POST
            // Но так как WebView не перехватывает POST, просто возвращаем HTML с авто-submit
            if (html.contains("<form") && html.contains("act=7")) {
                String msg_form = "mainPhpFightEnd: found form in HTML, auto-submitting";
                AppLog.d(TAG, msg_form);
                // Возвращаем HTML с авто-submit формой
                return html; // WebView сам отправит форму
            }
            
            // Строим GET редирект для завершения боя (аналог C# BuildRedirect)
            // Используем window.location для перехвата в WebView
            String msg_redirect = "mainPhpFightEnd: building redirect for fight end";
            AppLog.d(TAG, msg_redirect);
            
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
        String msg_nofexp = "mainPhpFightEnd: no fexp in URL, returning original HTML";
        AppLog.d(TAG, msg_nofexp);
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
     * Безопасный parseInt параметра URL с fallback-значением.
     */
    private static int parseUrlParamInt(String url, String paramName, int fallback) {
        try {
            if (url == null || url.isEmpty()) {
                return fallback;
            }
            String raw = getUrlParam(url, paramName);
            if (raw == null || raw.isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * Добавляет URL-параметр или заменяет его значение, если параметр уже присутствует.
     */
    private static String appendOrReplaceUrlParam(String url, String paramName, String paramValue) {
        if (url == null || url.isEmpty()) {
            return paramName + "=" + paramValue;
        }

        String search = paramName + "=";
        int idx = url.indexOf(search);
        while (idx >= 0) {
            boolean hasBoundary = idx == 0 || url.charAt(idx - 1) == '?' || url.charAt(idx - 1) == '&';
            if (hasBoundary) {
                int valueStart = idx + search.length();
                int valueEnd = url.indexOf("&", valueStart);
                if (valueEnd < 0) {
                    valueEnd = url.length();
                }
                return url.substring(0, valueStart) + paramValue + url.substring(valueEnd);
            }
            idx = url.indexOf(search, idx + search.length());
        }
        return url + (url.contains("?") ? "&" : "?") + paramName + "=" + paramValue;
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
                "\\[\\s*8\\s*,\\s*\\d+\\s*,\\s*(?:\\\"([^\\\"]+)\\\"|'([^']+)')\\s*,\\s*(\\d+)\\s*\\]"
        ).matcher(logsBlock);
        while (lootMatcher.find()) {
            String lootNameRaw = lootMatcher.group(1);
            if (lootNameRaw == null || lootNameRaw.isEmpty()) {
                lootNameRaw = lootMatcher.group(2);
            }
            if (lootNameRaw == null) {
                continue;
            }
            String skillRaiseRaw = lootMatcher.group(3);
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
        if (isSkinResult && isAutoSkinEnabledByPreference()) {
            // C# parity/fallback: после запроса get_id=17 всегда перечитываем ресурсы из инвентаря.
            // Даже если var logs не содержит [8,...], сервер мог применить разделку в этом же кадре.
            AppVars.AutoSkinCheckRes = true;
            if (skinSkillRaised) {
                AppVars.AutoSkinCheckUm = true;
            }
            android.util.Log.d(TAG, "AUTO_SKIN_TRACE publishFightResultFromLogsIfNeeded: "
                    + "queue AutoSkinCheckRes=true, AutoSkinCheckUm=" + AppVars.AutoSkinCheckUm
                    + ", lootCount=" + lootItems.size()
                    + ", shouldSendLoot=" + shouldSendLoot
                    + ", source=" + address);
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
        String msg = "notifyNewFight: ";
        AppLog.d(TAG, msg);
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
    static String buildServerChatTimeHtml() {
        long serverMs = System.currentTimeMillis();
        if (AppVars.Profile != null && AppVars.Profile.ServDiff != Long.MIN_VALUE) {
            serverMs = serverMs - AppVars.Profile.ServDiff;
        }
        Date serverTime = new Date(serverMs);
        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(serverTime);
        return "<font class=chattime>&nbsp;" + timeStr + "&nbsp;</font> ";
    }

    /**
     * Внешний доступ к стандартному чат-таймштампу в серверной шкале времени.
     *
     * Используется менеджерами вне `postfilter` (например, FastActionManager),
     * чтобы все системные сообщения имели единый формат времени.
     */
    public static String buildServerChatTimeHtmlExternal() {
        return buildServerChatTimeHtml();
    }

    public static void postServerNotificationToChat(String messageText, String sourceTag, String addressHint) {
        if (AppVars.getContext() == null) {
            return;
        }
        String normalized = normalizeServerNotificationText(messageText);
        if (normalized.isEmpty()) {
            return;
        }
        String type = resolveServerNotificationType(normalized, sourceTag, addressHint);
        boolean appendAutoCureTarget = shouldAppendAutoCureTarget(sourceTag, addressHint);
        if (appendAutoCureTarget) {
            type = "\u0410\u0432\u0442\u043e-\u041b\u0435\u0447\u0435\u043d\u0438\u0435";
            String cureTarget = resolveAutoCureNoticeTargetNick();
            if (!cureTarget.isEmpty()) {
                normalized = normalized + " на игрока '" + cureTarget + "'";
            }
        }
        String dedupKey = type + "|" + normalized;
        long nowMs = System.currentTimeMillis();
        if (dedupKey.equals(lastServerNoticeKey) && (nowMs - lastServerNoticeAtMs) < SERVER_NOTICE_CHAT_DEDUP_MS) {
            String msgDup = "SERVER_NOTICE_TRACE dedup: key=" + dedupKey + ", source=" + sourceTag;
            AppLog.d(TAG, msgDup);
            return;
        }
        lastServerNoticeKey = dedupKey;
        lastServerNoticeAtMs = nowMs;

        String messageHtml = buildServerChatTimeHtml()
                + "<font color=#333399><b>["
                + escapeHtmlText(type)
                + "]</b>:</font> "
                + escapeHtmlText(normalized);
        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);

        String msg = "SERVER_NOTICE_TRACE post: type=" + type
                + ", source=" + sourceTag
                + ", address=" + addressHint
                + ", text=" + normalized;
        AppLog.d(TAG, msg);
        if (appendAutoCureTarget) {
            AppVars.CureNickDone = "";
        }
    }

    private static boolean shouldAppendAutoCureTarget(String sourceTag, String addressHint) {
        String cureTarget = resolveAutoCureNoticeTargetNick();
        if (cureTarget.isEmpty()) {
            return false;
        }
        String lowerSource = sourceTag == null ? "" : sourceTag.toLowerCase(Locale.ROOT);
        String lowerAddress = addressHint == null ? "" : addressHint.toLowerCase(Locale.ROOT);
        return containsAny(lowerSource, "main_php_sys_message", "auto_cure", "cure")
                || containsAny(lowerAddress, "wca=85", "doctorform", "wca=27", "cure", "med");
    }

    private static String resolveAutoCureNoticeTargetNick() {
        String fromDone = AppVars.CureNickDone == null ? "" : AppVars.CureNickDone.trim();
        if (!fromDone.isEmpty()) {
            return fromDone;
        }
        String fromCurrent = AppVars.CureNick == null ? "" : AppVars.CureNick.trim();
        if (!fromCurrent.isEmpty()) {
            return fromCurrent;
        }
        return "";
    }

    private static String normalizeServerNotificationText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text
                .replace('\u00A0', ' ')
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 700) {
            normalized = normalized.substring(0, 700) + "...";
        }
        return normalized;
    }

    private static String resolveServerNotificationType(String messageText, String sourceTag, String addressHint) {
        String lowerMessage = messageText == null ? "" : messageText.toLowerCase(Locale.ROOT);
        String lowerSource = sourceTag == null ? "" : sourceTag.toLowerCase(Locale.ROOT);
        String lowerAddress = addressHint == null ? "" : addressHint.toLowerCase(Locale.ROOT);

        if (containsAny(lowerMessage, "травм", "леч", "исцел", "аптеч", "отравлен")) {
            return "Авто-лечение";
        }
        if (containsAny(lowerMessage, "рыбал", "удоч", "снаст", "приманк")) {
            return "Авто-рыбалка";
        }
        if (containsAny(lowerAddress, "wca=85", "doctorform", "im=6", "cure", "med")) {
            return "Авто-лечение";
        }
        if (containsAny(lowerAddress, "get_id=43", "get_id=17", "wca=28", "wca=27")) {
            return "Быстрое действие";
        }
        if (containsAny(lowerSource, "popup", "bridge")) {
            return "Системное окно";
        }
        return "Системное сообщение";
    }

    private static boolean containsAny(String value, String... tokens) {
        if (value == null || value.isEmpty() || tokens == null) {
            return false;
        }
        for (String token : tokens) {
            if (token != null && !token.isEmpty() && value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String escapeHtmlText(String value) {
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
            String msg = "primeLastBoiDamageFromFinishHtmlIfNeeded: list missing, logId=";
            AppLog.d(TAG, msg);
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
    static int parseIntFromJsToken(String token, int fallback) {
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
            // ⚠️ КРИТИЧНО: Очищаем боевой пульс при завершении боя.
            // Без этого рыбалка считает бой активным ещё 12+ секунд, причём gate блокирует auto-turn.
            AppVars.LastFightPulseAtMs = 0L;
            String msg = "registerFightEnd: fight counted, source=";
            AppLog.d(TAG, msg);
        } else {
            String msg = "registerFightEnd: skip duplicate, source=";
            AppLog.d(TAG, msg);
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
            String msg = "logFightVar: ";
            AppLog.d(TAG, msg);
            return;
        }
        // Берём подстроку от "var NAME" до конца строки (до \n или ;)
        int end = html.indexOf("\n", idx);
        if (end < 0 || end > idx + 500) end = Math.min(idx + 500, html.length());
        String value = html.substring(idx, end).trim();
        String msg = "logFightVar: ";
        AppLog.d(TAG, msg);
    }
    /**
     * Синхронизирует runtime-кэш инвентаря (`AppVars.InvList`) по сырому HTML страницы инвентаря.
     *
     * Важно:
     * - работает в cache-only режиме: не выполняет redirect/drop/sell и не пишет служебные сообщения в чат;
     * - используется fast-контуром (например, эликсиры `im=6`), когда кэш нужен сразу в текущем кадре.
     */
    public static void syncInventoryCacheFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return;
        }
        try {
            mainPhpInv(html, true);
        } catch (Exception e) {
            String msg = "syncInventoryCacheFromHtml: failed";
            AppLog.w(TAG, msg, e);
        }
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
        return mainPhpInv(html, false);
    }

    /**
     * Внутренняя реализация `mainPhpInv` с поддержкой cache-only режима.
     *
     * @param cacheOnlyMode
     * true  - обновляем только `AppVars.InvList`, без redirect/bulk/chat и без модификации HTML.
     * false - полный режим как в C# (группировка, bulk-кнопки, служебные действия).
     */
    private static String mainPhpInv(String html, boolean cacheOnlyMode) {
        try {
            // ========== ПАРСИНГ КОМПЛЕКТОВ ==========
            // При каждом входе в инвентарь парсируем доступные комплекты из compl_view()
            // и сохраняем их в профиль для использования в диалоге таймеров
            InventoryParser.parseAndSaveComplectsFromInventory(html);

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

                if (!cacheOnlyMode) {
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

            if (!cacheOnlyMode) {
                if (!AppVars.BulkDropThing.isEmpty()) {
                    sendInventoryChatMessage("Выбрасывание пачки <b>&laquo;" + AppVars.BulkDropThing + "&raquo;</b> завершено.");
                    AppVars.BulkDropThing = "";
                }
                if (!AppVars.BulkSellThing.isEmpty()) {
                    sendInventoryChatMessage("Продажа пачки <b>&laquo;" + AppVars.BulkSellThing
                            + "&raquo;</b> завершена. Выручка составила <b>" + AppVars.BulkSellSum + "</b> NV.");
                    AppVars.BulkSellThing = "";
                }
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

            String msg_pack = "INV_GROUP_TRACE afterPack=" + parsedCount
                    + ", packed=" + Math.max(0, parsedCount - invList.size());
            AppLog.d(TAG, msg_pack);

            if (!cacheOnlyMode) {
                for (InvEntry entry : invList) {
                    entry.addBulkSell();
                    entry.addBulkDelete();
                }
            }

            if (AppVars.Profile != null && AppVars.Profile.DoInvSort) {
                // ✅ API 21 compatible sort (List#sort requires API 24)
                Collections.sort(invList, new InvComparer());
            }

            String msg_sort = "INV_GROUP_TRACE afterSort=";
            AppLog.d(TAG, msg_sort);

            AppVars.InvList = new ArrayList<>(invList);
            if (cacheOnlyMode) {
                String msg_cache = "INV_GROUP_TRACE cache-only sync done: entries=";
                AppLog.d(TAG, msg_cache);
                return html;
            }

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
            ru.neverlands.abclient.utils.FileLogger.log("Error during mainPhpInv processing: \n" + sw);
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
    static void sendInventoryChatMessage(String messageHtml) {
        if (AppVars.getContext() == null || messageHtml == null || messageHtml.isEmpty()) {
            return;
        }
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            Chat.addMessageToChat(messageHtml);
            return;
        }
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }

    /**
     * Формирует системное сообщение об отмене fast-действия при отсутствии предмета.
     * Для эликсиров сохраняем явную формулировку по запросу: "Эликсир ... не найден, действие отменено.".
     */
    private static String buildFastItemNotFoundMessage(String fastId) {
        String safeFastId = fastId == null ? "" : fastId.trim();
        
        // Формат: 'timestamp-server' ['Обработчик вызова']: Эликсир Блаженства не найден, действие отменено
        long now = System.currentTimeMillis();
        String timestamp = String.format("%02d:%02d:%02d", 
            (now / 3600000) % 24, 
            (now / 60000) % 60, 
            (now / 1000) % 60);
        String handler = "FastActionManager";
        
        String message;
        if (safeFastId.startsWith("Эликсир ")) {
            message = "<font color=#FF0000>'" + timestamp + "' [" + handler + "]: " + safeFastId + " не найден, действие отменено.</font>";
        } else if (safeFastId.isEmpty()) {
            message = "<font color=#FF0000>'" + timestamp + "' [" + handler + "]: Предмет не найден, действие отменено.</font>";
        } else {
            message = "<font color=#FF0000>'" + timestamp + "' [" + handler + "]: " + safeFastId + " в инвентаре не найден, действие отменено.</font>";
        }
        
        return message;
    }
}
