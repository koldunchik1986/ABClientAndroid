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
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.model.InvComparer;
import ru.neverlands.abclient.model.InvEntry;
import ru.neverlands.abclient.model.ParsedDressed;
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

    private static final long CAPTCHA_FALLBACK_TTL_MS = 5000L;

    // На части серверных ответов вкладка инвентаря отдается промежуточным шаблоном (без содержимого предметов).
    // Для быстрых действий по эликсирам даем расширенное окно ретраев, чтобы не падать в ложный timeout.
    private static final long WTIME_SYNC_LOG_GUARD_MS = 1500L;

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


    private static volatile long lastWtimeSyncLogAtMs = 0L;

    // One-shot post-fight marker:
    // after finish-link redirect to plain main.php we allow auto-drink check on ближайших страницах
    // персонажа/инвентаря (go=inf/go=inv/im=*), если "чистый" main.php не попал в Filter.process().

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
            FightAuto.logFightVariable(html, variableName);
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
            FightAuto.clearAutoFightProbeFinishCandidate();
        }

        @Override
        public boolean isAutoFightProbeAddress(String address) {
            return FightAuto.isAutoFightProbeAddress(address);
        }

        @Override
        public String resolveFightCaptchaUrl(String html) {
            return FightAuto.resolveFightCaptchaUrl(html);
        }

        @Override
        public boolean isFightFrameHtml(String html) {
            return             FightAuto.isFightFrameHtml(html);
        }

        @Override
        public void registerFightEnd(LezFight fight) {
            FightAuto.registerFightEnd(fight);
        }

        @Override
        public void publishFightResultFromLogsIfNeeded(String html, String address, String logIdHint) {
            FightAuto.publishFightResultFromLogsIfNeeded(html, address, logIdHint);
        }

        @Override
        public void recoverAutoboiRuntimeStateIfNeeded(boolean fightEnded, String fightCaptchaUrl) {
            FightAuto.recoverAutoboiRuntimeStateIfNeeded(fightEnded, fightCaptchaUrl);
        }

        @Override
        public boolean isAutoFightEnabledByPreference() {
            return FightAuto.isAutoFightEnabledByPreference();
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
            return FightAuto.buildRestoringStatusHtml(
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
            FightAuto.notifyNewFight(fight);
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
            return FightAuto.buildDelayedRedirectHtml(description, link, delayMs);
        }

        @Override
        public String extractFightFinishLinkFromHtml(String html, boolean withCaptchaPlaceholder) {
            return             FightAuto.extractFightFinishLinkFromHtml(html, withCaptchaPlaceholder);
        }

        @Override
        public String extractFightCleanFinishLinkFromHtml(String html) {
            return             FightAuto.extractFightCleanFinishLinkFromHtml(html);
        }

        @Override
        public String normalizeNeverlandsMainLink(String link) {
            return FightAuto.normalizeNeverlandsMainLink(link);
        }

        @Override
        public boolean isAutoFightProbeFinishConfirmed(String logBoi, String fightLink) {
            return             FightAuto.isAutoFightProbeFinishConfirmed(logBoi, fightLink);
        }

        @Override
        public void showFightCaptchaDialogOnce(String captchaUrl, String finishUrl, String logBoi) {
            FightAuto.showFightCaptchaDialogOnce(captchaUrl, finishUrl, logBoi);
        }

        @Override
        public String getUrlParam(String url, String paramName) {
            return FightAuto.getUrlParam(url, paramName);
        }

        @Override
        public void notifyCaptchaRejectedOnce(String submittedCode, String submittedVcode) {
            FightAuto.notifyCaptchaRejectedOnce(submittedCode, submittedVcode);
        }

        @Override
        public String buildInPlaceFightAutoRefreshHtml(String html, String reloadUrl, int delayMs) {
            return FightAuto.buildInPlaceFightAutoRefreshHtml(html, reloadUrl, delayMs);
        }

        @Override
        public void notifyFightStopped(LezFight fight) {
            FightAuto.notifyFightStopped(fight);
        }

        @Override
        public List<String> splitJsTopLevelCsv(String raw) {
            return FightAuto.splitJsTopLevelCsv(raw);
        }

        @Override
        public String trimJsToken(String token) {
            return FightAuto.trimJsToken(token);
        }

        @Override
        public String escapeHtmlAttr(String value) {
            return FightAuto.escapeHtmlAttr(value);
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
            return             FightAuto.isFightFrameHtml(html);
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
            return FightAuto.buildServerChatTimeHtml();
        }

        @Override
        public String escapeHtmlAttr(String value) {
            return MainPhp.escapeHtmlAttr(value);
        }

        @Override
        public List<TreasureDig.WearInvEntry> getWearInvList(String html) {
            List<InventoryParser.WearInvEntry> source = InventoryParser.getWearInvList(html);
            if (source == null || source.isEmpty()) {
                return Collections.emptyList();
            }
            List<TreasureDig.WearInvEntry> mapped = new ArrayList<>(source.size());
            for (InventoryParser.WearInvEntry entry : source) {
                if (entry == null) {
                    continue;
                }
                mapped.add(new TreasureDig.WearInvEntry(entry.name, entry.wearLink));
            }
            return mapped;
        }
    };
    static final class WearInvEntry {
        String name;
        String wearLink;
        WearInvEntry() {}
        WearInvEntry(String name, String wearLink) { this.name = name; this.wearLink = wearLink; }
        static List<WearInvEntry> fromInventoryParser(List<InventoryParser.WearInvEntry> source) {
            if (source == null) return Collections.emptyList();
            List<WearInvEntry> result = new ArrayList<>(source.size());
            for (InventoryParser.WearInvEntry e : source) {
                if (e == null) continue;
                result.add(new WearInvEntry(e.name, e.wearLink));
            }
            return result;
        }
    }
    /**
     * Снимок значений из `ins_HP(curh,maxh,curm,maxm,hp_int,ma_int)`.
     * Используется для:
     * - обновления интервалов регена `AppVars.PersIntHP`/`AppVars.PersIntMA`;
     * - отображения статуса лечения в верхнем фрейме при `AutoboiState.Restoring`.
     */
    static final class InsHpSnapshot {
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
        return FightAuto.buildWaitForTurnAutoRefreshHtml(reloadUrl, delayMs);
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
        return FightAuto.buildInPlaceFightAutoRefreshHtml(html, reloadUrl, delayMs);
    }
    /**
     * Возвращает сохранённое состояние переключателя Auto-Fight из AutoFunctionsManager.
     * Если manager/context недоступен, используется fallback на флаг профиля.
     */
    private static boolean isAutoFightEnabledByPreference() {
        return FightAuto.isAutoFightEnabledByPreference();
    }
    /**
     * Самовосстановление runtime-рассинхронизации, когда сохранённый Auto-Fight включён,
     * а AppVars.Autoboi выключен.
     *
     * Это восстановление намеренно блокируется при активном потоке CAPTCHA.
     */
    private static void recoverAutoboiRuntimeStateIfNeeded(boolean fightEnded, String fightCaptchaUrl) {
        FightAuto.recoverAutoboiRuntimeStateIfNeeded(fightEnded, fightCaptchaUrl);
    }

    /**
     * Форматирует секунды в `HH:mm:ss` для UI ожидания лечения.
     */
    private static String formatHms(long seconds) {
        return FightAuto.formatHms(seconds);
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
        return FightAuto.buildRestoringStatusHtml(reloadUrl, reloadDelayMs, remainingMs, curHp, maxHp, curMa, maxMa, doWaitHp, waitHpPercent, doWaitMa, waitMaPercent);
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
        return FightAuto.buildDelayedRedirectHtml(description, link, delayMs);
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
        return FightAuto.resolveFightCaptchaUrl(html);
    }
    /**
     * Собирает URL капчи из массива `fexp` (элемент `fexp[4]`).
     *
     * Зависимости:
     * - `HelperStrings.subString`,
     * - формат `fight_v10.js`: `code.php?` + token.
     */
    private static String extractCaptchaUrlFromFexp(String html) {
        return FightAuto.extractCaptchaUrlFromFexp(html);
    }
    /**
     * Восстанавливает ссылку завершения боя (`get_id=61&act=7`) напрямую из HTML.
     *
     * Нужен как fallback, когда `LezFight.BuildFightLink(...)` не сработал
     * и `AppVars.FightLink` остался пустым, но сервер уже прислал финальный кадр боя.
     */
    private static String extractFightFinishLinkFromHtml(String html, boolean withCaptchaPlaceholder) {
        return FightAuto.extractFightFinishLinkFromHtml(html, withCaptchaPlaceholder);
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
        return FightAuto.extractFightCleanFinishLinkFromHtml(html);
    }

    private static String extractFightCleanVcodeFromFexp(String html) {
        return FightAuto.extractFightCleanVcodeFromFexp(html);
    }

    private static String extractFightCleanVcodeFromFightTy(String html) {
        return FightAuto.extractFightCleanVcodeFromFightTy(html);
    }

    /**
     * Returns st for act=5 from fight_ty[4]. Fallback is "6".
     */
    private static String resolveFightFinishStateForAct5(String html) {
        return FightAuto.resolveFightFinishStateForAct5(html);
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
        return FightAuto.escapeHtmlAttr(value);
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
    static InsHpSnapshot parseInsHpSnapshot(String html) {
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
        AutoDrinkHandler.tryTriggerAutoDrinkRestoreElixir(address, html, isFightFrame, isFightTopFrame);
    }

    /**
     * One-shot post-fight синхронизация HP/MA через pinfo.
     *
     * Нужна как страховка на кейс, когда `ins_HP(...)` на первом небоевом кадре
     * остаётся со старыми значениями и не отражает фактическое состояние после боя.
     */
    private static InsHpSnapshot tryBuildAutoDrinkSnapshotFromPinfo() {
        return AutoDrinkHandler.tryBuildAutoDrinkSnapshotFromPinfo();
    }

    private static boolean isPostFightAutoDrinkFollowupAddress(String address) {
        return AutoDrinkHandler.isPostFightAutoDrinkFollowupAddress(address);
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
        return AutoDrinkHandler.isServerPlainMainAddress(address);
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
        AutoSkinHandler.mainPhpProcessSkills(html, address);
    }
    /**
     * Определяет, включена ли Авто-Охота (C# `Profile.SkinAuto` / `buttonAutoSkin`).
     */
    private static boolean isAutoSkinEnabledByPreference() {
        return AutoSkinHandler.isAutoSkinEnabledByPreference();
    }

    /**
     * Определяет, включено ли Авто-Лечение.
     *
     * Источник:
     * - переключатель `AUTO_CURE` в `AutoFunctionsManager` (SharedPreferences).
     */
    private static boolean isAutoCureEnabledByPreference() {
        return AutoCureHandler.isAutoCureEnabledByPreference();
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
        return AutoCureHandler.isAutoCureSelfElixirEnabledForWound(cureTravm);
    }

    private static boolean isAutoCureWoundTypeEnabledForTravm(String cureTravm) {
        return AutoCureHandler.isAutoCureWoundTypeEnabledForTravm(cureTravm);
    }

    private static boolean isAutoCureWoundTypeEnabledForSelfByAnyMethod(String cureTravm) {
        return AutoCureHandler.isAutoCureWoundTypeEnabledForSelfByAnyMethod(cureTravm);
    }

    private static int parseCureTravmType(String cureTravm) {
        return AutoCureHandler.parseCureTravmType(cureTravm);
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
        return AutoFuryHandler.isAutoFuryEnabledByPreference();
    }
    /**
     * Периодическая (раз в минуту) установка флага проверки ножа,
     * аналог `FormMainTicks.cs`: `AutoSkinLastChecked` -> `AutoSkinCheckKnife = true`.
     */
    private static void maybeMarkAutoSkinKnifeRecheck() {
        AutoSkinHandler.maybeMarkAutoSkinKnifeRecheck();
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
            AppLog.d(TAG, "SERVER_TIMER_TRACE wtime sync: timeoutSec=" + timeoutSec
                    + ", updated=" + updated + ", address=" + address
                    + ", dueInMs=" + Math.max(0L, AppVars.NeverTimer - now));
            FileLogger.trace(TAG, "SERVER_TIMER_TRACE wtime sync: timeoutSec=" + timeoutSec
                    + ", updated=" + updated + ", address=" + address
                    + ", dueInMs=" + Math.max(0L, AppVars.NeverTimer - now));
        }
    }

    private static String mainPhpRaz(String html) {
        return AutoSkinHandler.mainPhpRaz(html);
    }

    private static String buildRazLinkFromFightTyPayload(String strFightTy) {
        return AutoSkinHandler.buildRazLinkFromFightTyPayload(strFightTy);
    }
    /**
     * Порт `MainPhpFindPerc` из C# (`MainPhpDrink.cs`).
     */
    /**
     * Fallback-поиск ссылки `main.php?get_id=17...` в HTML боя.
     * Используется, когда `fight_ty[9]` пустой/урезанный, но сервер отдает прямую ссылку "Разделать".
     */
    private static String extractRazLinkFromHtml(String html) {
        return AutoSkinHandler.extractRazLinkFromHtml(html);
    }
    /**
     * Нормализует ссылку `main.php` для auto-redirect:
     * - приводит хост к `http://neverlands.ru` без `www`;
     * - разворачивает относительные варианты (`main.php`, `/main.php`, `../main.php`);
     * - декодирует `&amp;` в query-строке.
     */
    static String normalizeNeverlandsMainLink(String link) {
        return FightAuto.normalizeNeverlandsMainLink(link);
    }
    /**
     * Универсальный fallback-поиск ссылки `main.php?...` по набору query-маркеров.
     * Возвращает первую подходящую ссылку в нормализованном виде.
     */
    private static String findMainPhpLinkByQueryParts(String html, String... queryParts) {
        return FightAuto.findMainPhpLinkByQueryParts(html, queryParts);
    }
    /**
     * Добавляет/дополняет query-параметры фильтра инвентаря (`&im=...&wca=...`) к найденной ссылке.
     * Если ссылка указывает на `go=inf`, переводит её на `go=inv`.
     */
    private static String applyInventoryFilterToLink(String link, String filter) {
        return InventoryParser.applyInventoryFilterToLink(link, filter);
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
        return FightAuto.setOrAppendQueryParam(url, key, value);
    }
    /**
     * Проверяет, что текущий адрес уже находится в разделе инвентаря (`go=inv`).
     * Используется как fallback-маркер, когда HTML-шаблон инвентаря может отличаться.
     */
    static boolean isInventoryAddress(String address) {
        return InventoryParser.isInventoryAddress(address);
    }
    /**
     * Проверяет, что адрес инвентаря уже содержит все параметры из требуемого фильтра
     * с совпадающими значениями (`im`, `wca` и т.д.).
     */
    static boolean inventoryAddressMatchesFilter(String address, String filter) {
        return InventoryParser.inventoryAddressMatchesFilter(address, filter);
    }
    /**
     * Возвращает значение query-параметра из URL или null, если параметр отсутствует.
     */
    private static String getQueryParamValue(String url, String key) {
        return InventoryParser.getQueryParamValue(url, key);
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
        AutoCureHandler.syncInjuriesFromMapHeavyPopup(html);
    }

    /**
     * Единая точка обработки server popup из JS bridge (MapJs -> WebAppInterface):
     * - синхронизирует runtime-состояние травм;
     * - при включенном авто-лечении ставит небоевые авто-функции на паузу
     *   и ставит self-лечение тяжелой травмы в приоритет.
     */
    public static void onServerPopupMessage(String popupText) {
        AutoCureHandler.onServerPopupMessage(popupText);
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
        AutoCureHandler.handleHeavyInjurySignal(text, sourceTag);
    }

    private static boolean isHeavyInjurySignalText(String text) {
        return AutoCureHandler.isHeavyInjurySignalText(text);
    }

    private static void queueSelfHeavyInjuryCureIfNeeded(String sourceTag) {
        AutoCureHandler.queueSelfHeavyInjuryCureIfNeeded(sourceTag);
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
        return AutoCureHandler.mainPhpExternalRequestedCureStep(address, html);
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
        AutoCureHandler.clearExternalCureRequest(reason);
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
        return AutoCureHandler.mainPhpAutoCureStep(address, html);
    }

    private static String mainPhpBuildPoisonCureForm(String html, String selfNick) {
        return AutoCureHandler.mainPhpBuildPoisonCureForm(html, selfNick);
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
        return AutoCureHandler.mainPhpTrySelfWoundCureByElixir(address, html, woundLabel);
    }

    private static boolean isSelfWoundElixirNavigationOnlyResult(String html) {
        return AutoCureHandler.isSelfWoundElixirNavigationOnlyResult(html);
    }

    private static String mainPhpBuildSelfWoundCureElixirRedirect(String html) {
        return AutoCureHandler.mainPhpBuildSelfWoundCureElixirRedirect(html);
    }

    private static String mainPhpBuildWoundCureForm(String html, String cureTravm, String targetNick) {
        return AutoCureHandler.mainPhpBuildWoundCureForm(html, cureTravm, targetNick);
    }

    private static void decrementSelfWoundCounterIfNeeded(String targetNick, String cureTravm, String source) {
        AutoCureHandler.decrementSelfWoundCounterIfNeeded(targetNick, cureTravm, source);
    }

    private static boolean isSelfNick(String nick) {
        return AutoCureHandler.isSelfNick(nick);
    }

    private static int woundIndexFromTravm(String cureTravm) {
        return AutoCureHandler.woundIndexFromTravm(cureTravm);
    }

    private static void disableAutoCureAndNotify(String message, boolean clearPoison, boolean clearWounds) {
        AutoCureHandler.disableAutoCureAndNotify(message, clearPoison, clearWounds);
    }

    static boolean mainPhpIsPerc(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("input type=button class=lbut value=\"умения\"");
    }
    /**
     * Порт `MainPhpArmedKinfe` из C# (`MainPhpWear.cs`).
     */
    private static boolean mainPhpArmedKnife(String html) {
        return AutoSkinHandler.mainPhpArmedKnife(html);
    }
    /**
     * Порт `MainPhpWearKnife` из C# (`MainPhpWear.cs`).
     */
    private static String mainPhpWearKnife(String html) {
        return AutoSkinHandler.mainPhpWearKnife(html);
    }
    /**
     * Проверка, надет ли целевой свиток режима осады (`Свиток Удар Ярости`/`Снежок`).
     */
    private static boolean mainPhpArmedFuryScroll(String html) {
        return AutoFuryHandler.mainPhpArmedFuryScroll(html);
    }
    /**
     * Поиск и надевание свитка режима осады в инвентаре (`wca=28`).
     *
     * Возвращает redirect-HTML на wear-link при успехе, иначе null.
     */
    private static String mainPhpWearFuryScroll(String html) {
        return AutoFuryHandler.mainPhpWearFuryScroll(html);
    }
    /**
     * Порт `MainPhpGetSkinRes` из C# (`MainPhpWear.cs`).
     */
    private static void mainPhpGetSkinRes(String html) {
        AutoSkinHandler.mainPhpGetSkinRes(html);
    }
    /**
     * Порт `GetInvList` из C# (`MainPhpWear.cs`) для поиска `WearLink`.
     */
    static List<WearInvEntry> getWearInvList(String html) {
        return WearInvEntry.fromInventoryParser(InventoryParser.getWearInvList(html));
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
        InventoryParser.WearInvEntry src = InventoryParser.parseWearInvEntryPublic(htmlEntry);
        if (src == null) return null;
        return new WearInvEntry(src.name, src.wearLink);
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
        return FightAuto.splitJsTopLevelCsv(source);
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
    static String trimJsToken(String token) {
        return FightAuto.trimJsToken(token);
    }

    private static String extractServerNoticeFromMainHtml(String html) {
        return ServerNoticeParser.extractServerNoticeFromMainHtml(html);
    }

    private static String extractServerNoticeFromPlainText(String plainText) {
        return ServerNoticeParser.extractServerNoticeFromPlainText(plainText);
    }

    /**
     * Публичный доступ к штатному парсеру серверного системного сообщения из main.php HTML.
     *
     * Используется UI-слоем (например, MainActivity POST-fallback), чтобы не дублировать
     * regex/marker-логику и не хардкодить конкретные фразы сообщений.
     */
    public static String extractServerNoticeForUi(String html) {
        return ServerNoticeParser.extractServerNoticeForUi(html);
    }

    public static String extractServerNoticeForUi(String html, String plainText) {
        return ServerNoticeParser.extractServerNoticeForUi(html, plainText);
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
            AppLog.d(TAG, "[AA_TRACE] closed fight error: add to blacklist and cancel fast, nick=" + blockedNick
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
            AutoDrinkHandler.autoDrinkPostFightSyncPending = true;
            AutoDrinkHandler.autoDrinkPostFightSyncPendingSinceMs = System.currentTimeMillis();
            String msg_postfight = "AUTO_DRINK_TRACE post-fight redirect to plain main.php, address=" + address
                    + ", ts=" + AutoDrinkHandler.autoDrinkPostFightSyncPendingSinceMs;
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
                        AppLog.d(TAG, "AUTO_FURY_TRACE scroll check result: armed=" + AppVars.AutoFuryArmedScroll
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
            AppLog.d(TAG, "AUTO_SKIN_TRACE suspended: finishFlow=" + suspendAutoSkinForFinishFlow
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
            AppLog.d(TAG, "=== FIGHT FRAME DETECTED ==="
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
                    AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE bootstrap map via return-link, address=" + address
                            + ", mapLocation=" + currentMapLocation);
                    return Russian.getBytes(mapReturnHtml);
                }
                boolean isInfAddress = address != null && address.contains("get_id=56&act=10&go=inf");
                if (!isInfAddress) {
                    String personHtml = mainPhpFindPerc(html);
                    if (personHtml != null && !personHtml.isEmpty()) {
                        AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE bootstrap person page before map, address="
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
                    AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE bootstrap fallback go=ret, address="
                            + address + ", mapLocation=" + currentMapLocation + ", link=" + bootstrapRetLink);
                    return Russian.getBytes(buildRedirectHtml("SearchBox bootstrap: go=ret", bootstrapRetLink));
                }
            }
            long nowMs = System.currentTimeMillis();
            if (AppVars.NeverTimer <= 0L || nowMs > AppVars.NeverTimer) {
                String nextDest = MapAjax.findNextDestForBox(currentMapLocation);
                if (nextDest != null && !nextDest.isEmpty()) {
                    startAutoSearchBoxMoving(nextDest);
                    AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE start moving to " + nextDest
                            + ", address=" + address);
                } else {
                    AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE no destination yet, mapLocation="
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
                    AppLog.d(TAG, "AUTO_MOVING_TRACE: bootstrap fallback to map, address="
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
                        AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE retry-after-map-sync start moving to "
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
                        AppLog.d(TAG, "AUTO_SEARCH_BOX_TRACE retry-after-map-sync still no destination, mapLocation="
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
        return FightAuto.isFightFrameHtml(html);
    }
    /**
     * Технический URL-пробник, который AutoFight использует для форс-обновления fight.frame:
     * `main.php?get_id=56&act=10&go=inf&ts=...`.
     * На таком кадре нельзя запускать цепочку AutoFish, иначе начинается race навигации.
     */
    static boolean isAutoFightReloadProbeAddress(String address) {
        return FightAuto.isAutoFightReloadProbeAddress(address);
    }
    /**
     * Признак фонового probe-запроса авто-боя (`ab_bg_probe=1`).
     *
     * Назначение:
     * - отделить технические перезагрузки боевого контекста от обычной навигации;
     * - применять более осторожные правила завершения боя на таких кадрах.
     */
    private static boolean isAutoFightBackgroundProbeAddress(String address) {
        return FightAuto.isAutoFightBackgroundProbeAddress(address);
    }
    /**
     * Единая проверка: текущий адрес относится к probe-потоку авто-боя.
     *
     * Зависимости:
     * - `isAutoFightReloadProbeAddress(...)`;
     * - `isAutoFightBackgroundProbeAddress(...)`.
     */
    private static boolean isAutoFightProbeAddress(String address) {
        return FightAuto.isAutoFightProbeAddress(address);
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
        return FightAuto.buildAutoFightProbeFinishCandidateKey(logBoi, fightLink);
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
        FightAuto.clearAutoFightProbeFinishCandidate();
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
        return FightAuto.isAutoFightProbeFinishConfirmed(logBoi, fightLink);
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
        return InventoryParser.containsIgnoreCase(value, token);
    }
    /**
     * Проверяет, что мы на странице инвентаря (аналог MainPhpIsInv в MainPhpDrink.cs:221-224).
     * Инвентарь содержит ссылку <a href="?im=0"><img...
     */
    static boolean mainPhpIsInv(String html) {
        return InventoryParser.mainPhpIsInv(html);
    }

    /**
     * Структурный fallback-детект инвентаря для кейсов, где URL/табы `?im=0` отсутствуют
     * (например, переходы через `Вернуться` или `wca=...&useaction=...`).
     *
     * Нужен для C#-паритета: в ПК версии упаковка/сортировка применяется по факту HTML списка предметов.
     */
    private static boolean hasInventoryRows(String html) {
        return InventoryParser.hasInventoryRows(html);
    }

    private static boolean isLikelyInventoryReloadSnapshot(String address, String html) {
        return InventoryParser.isLikelyInventoryReloadSnapshot(address, html);
    }

    /**
     * Returns true for intermediate generated transition pages, where AutoSkin must not
     * start a new redirect step. This prevents redirect races and manual-link hijacking
     * on first login (especially around useaction=addon-action hops).
     */
    private static boolean isGeneratedTransitionPage(String address, String html) {
        return InventoryParser.isGeneratedTransitionPage(address, html);
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
        return InventoryParser.mainPhpFindInv(html, filter);
    }
    /**
     * Стратегия поиска инвентаря на арене (view_arena).
     * Аналог MainPhpDrink.cs строки 99-130
     */
    static String mainPhpFindInvWithFallback(String html, String filter, String address) {
        return InventoryParser.mainPhpFindInvWithFallback(html, filter, address);
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
        return InventoryParser.mainPhpFindInvArena(html, filter);
    }
    /**
     * Стратегия поиска инвентаря в зданиях (view_moor, view_taverna и т.д.).
     * Аналог MainPhpDrink.cs строки 142-180
     */
    private static String mainPhpFindInvBuilding(String html, String filter) {
        return InventoryParser.mainPhpFindInvBuilding(html, filter);
    }
    /**
     * Ищет кнопку "Инвентарь" с onclick (аналог MainPhpFindInvOld в MainPhpDrink.cs:33-84).
     */
    private static String mainPhpFindInvOld(String html, String filter) {
        return InventoryParser.mainPhpFindInvOld(html, filter);
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
        return FightAuto.extractCaptchaUrl(html);
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
        FightAuto.showFightCaptchaDialogOnce(captchaUrl, finishUrl, logBoi);
    }
    /**
     * Обработка страницы завершения боя (get_id=61&act=7).
     * Автоматически нажимает кнопку "Завершить" - аналог PC версии.
     */
    private static String mainPhpFightEnd(String address, String html) {
        return FightAuto.mainPhpFightEnd(address, html);
    }
    /**
     * Извлекает параметр из URL.
     */
    private static String getUrlParam(String url, String paramName) {
        return FightAuto.getUrlParam(url, paramName);
    }

    /**
     * Безопасный parseInt параметра URL с fallback-значением.
     */
    private static int parseUrlParamInt(String url, String paramName, int fallback) {
        return FightAuto.parseUrlParamInt(url, paramName, fallback);
    }

    /**
     * Добавляет URL-параметр или заменяет его значение, если параметр уже присутствует.
     */
    private static String appendOrReplaceUrlParam(String url, String paramName, String paramValue) {
        return FightAuto.appendOrReplaceUrlParam(url, paramName, paramValue);
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
        FightAuto.publishFightResultFromLogsIfNeeded(html, address, logIdHint);
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
        FightAuto.notifyNewFight(fight);
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
        return FightAuto.buildServerChatTimeHtml();
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
        ServerNoticeParser.postServerNotificationToChat(messageText, sourceTag, addressHint);
    }

    private static boolean shouldSuppressAutoFishPopupChatNotice(String normalized, String sourceTag) {
        return ServerNoticeParser.shouldSuppressAutoFishPopupChatNotice(normalized, sourceTag);
    }

    private static boolean shouldAppendAutoCureTarget(String sourceTag, String addressHint) {
        return ServerNoticeParser.shouldAppendAutoCureTarget(sourceTag, addressHint);
    }

    private static String resolveAutoCureNoticeTargetNick() {
        return ServerNoticeParser.resolveAutoCureNoticeTargetNick();
    }

    private static String normalizeServerNotificationText(String text) {
        return ServerNoticeParser.normalizeServerNotificationText(text);
    }

    private static String resolveServerNotificationType(String messageText, String sourceTag, String addressHint) {
        return ServerNoticeParser.resolveServerNotificationType(messageText, sourceTag, addressHint);
    }

    private static boolean containsAny(String value, String... tokens) {
        return ServerNoticeParser.containsAny(value, tokens);
    }

    private static String escapeHtmlText(String value) {
        return ServerNoticeParser.escapeHtmlText(value);
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
        FightAuto.notifyNewFightFromExternalSource(fight, html);
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
        FightAuto.notifyFightStopped(fight);
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
        FightAuto.notifyCaptchaRejectedOnce(submittedCode, submittedVcode);
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
        FightAuto.publishFightSummaryFromFinishHtmlIfNeeded(html, address, logIdHint);
    }

    /**
     * Извлекает CSV-токены JS-массива до первого закрывающего `]`.
     */
    private static String[] extractJsArrayTokens(String html, String prefix) {
        return FightAuto.extractJsArrayTokens(html, prefix);
    }
    /**
     * Безопасный parseInt для токена JS-массива с очисткой кавычек и пробелов.
     */
    static int parseIntFromJsToken(String token, int fallback) {
        return FightAuto.parseIntFromJsToken(token, fallback);
    }
    private static String extractBattleXpFromHtml(String html) {
        return FightAuto.extractBattleXpFromHtml(html);
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
        FightAuto.registerFightEnd(fight);
    }
    /**
     * Унифицированный учёт завершённого боя в статистике.
     * Дедуп по `AppVars.LastBoiEndLog`, чтобы не считать один и тот же бой повторно
     * при дублирующих загрузках верхнего фрейма/страницы завершения.
     */
    private static void registerFightEndByLogId(String logId, String source) {
        FightAuto.registerFightEndByLogId(logId, source);
    }
    /**
     * Логирует JavaScript-переменную из HTML боя.
     * Ищет паттерн: var NAME = [...] или var NAME = "..."
     */
    private static void logFightVariable(String html, String varName) {
        FightAuto.logFightVariable(html, varName);
    }
    /**
     * Синхронизирует runtime-кэш инвентаря (`AppVars.InvList`) по сырому HTML страницы инвентаря.
     *
     * Важно:
     * - работает в cache-only режиме: не выполняет redirect/drop/sell и не пишет служебные сообщения в чат;
     * - используется fast-контуром (например, эликсиры `im=6`), когда кэш нужен сразу в текущем кадре.
     */
    public static void syncInventoryCacheFromHtml(String html) {
        InventoryParser.syncInventoryCacheFromHtml(html);
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
        return InventoryParser.mainPhpInv(html);
    }

    /**
     * Внутренняя реализация `mainPhpInv` с поддержкой cache-only режима.
     *
     * @param cacheOnlyMode
     * true  - обновляем только `AppVars.InvList`, без redirect/bulk/chat и без модификации HTML.
     * false - полный режим как в C# (группировка, bulk-кнопки, служебные действия).
     */
    private static String mainPhpInv(String html, boolean cacheOnlyMode) {
        return InventoryParser.mainPhpInv(html, cacheOnlyMode);
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
