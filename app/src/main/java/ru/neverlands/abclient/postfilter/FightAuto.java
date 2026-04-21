package ru.neverlands.abclient.postfilter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.manager.UnderAttackManager;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.HtmlUtils;
import ru.neverlands.abclient.utils.Russian;

/**
 * Модуль боевого post-filter, выделенный из {@link MainPhp}.
 *
 * Назначение:
 * - централизовать всю логику Auto-Боя в одном классе;
 * - убрать перегрузку MainPhp и сократить риск регрессий при фиксе боя;
 * - сохранить parity поведения с уже работающей Android-логикой (без изменения алгоритмов).
 *
 * Границы ответственности:
 * - этот класс управляет только runtime-потоком боевого кадра/finish-flow;
 * - инфраструктурные операции (чаты, popup, bridge-redirect, извлечение URL) делегируются через {@link Host};
 * - глобальное состояние по-прежнему живёт в {@link AppVars}, как и было до выноса.
 */
public final class FightAuto {
    private static final String TAG = "FightAuto";
    private static final Random RANDOM = new Random();
    // Базовая задержка для auto-finish redirect (ограничение частоты финальных кликов).
    private static final int AUTO_FINISH_MIN_DELAY_MS = 1000;
    // Случайная добавка к задержке (микро-джиттер, чтобы не бомбить одинаковым интервалом).
    private static final int AUTO_FINISH_EXTRA_DELAY_MS = 700;
    private static final int AUTO_FINISH_MAX_REDIRECTS_PER_LOG = 5;
    private static final long AUTO_FINISH_LOOP_WINDOW_MS = 25_000L;
    private static final int AUTO_FINISH_LOOP_FALLBACK_DELAY_MS = 250;
    private static final String AUTO_FINISH_TITLE = "\u0417\u0430\u0432\u0435\u0440\u0448\u0435\u043D\u0438\u0435 \u0431\u043E\u044F";

    // Служебный таймштамп последнего auto-finish редиректа.
    private static volatile long lastAutoFinishRedirectAtMs = 0L;
    private static volatile String lastAutoFinishRedirectLog = "";
    private static volatile int lastAutoFinishRedirectCount = 0;
    private static volatile long lastAutoFinishRedirectWindowStartMs = 0L;
    // Дедуп ключ probe-шага авто-разделки после боя (на уровне конкретного LogBoi).
    private static volatile String lastAutoSkinProbeFightLog = "";

    private FightAuto() {
    }

    /**
     * Минимальный снимок HP/MA, достаточный для Restoring-экрана и пороговой логики.
     *
     * Источник:
     * - парсинг `ins_HP(...)` на стороне MainPhp через bridge-метод {@link Host#parseInsHpSnapshot(String)}.
     */
    public static final class InsHpSnapshot {
        public int curHp;
        public int maxHp;
        public int curMa;
        public int maxMa;
    }

    /**
     * Явная модель выбора ветки завершения боя.
     *
     * Нужна для:
     * - предсказуемой диагностики (вместо неявного каскада if/return);
     * - стабильного пост-анализа в логах через {@link #logFinishFlowDecision(FinishFlowDecision, LezFight, String, String, String, FightFinishPageMarkers, String)}.
     */
    private enum FinishFlowDecision {
        DIRECT_FINISH_LINK,
        FEND_AUTOSUBMIT_ALLOWED,
        CAPTCHA_REQUIRED,
        KEEP_ORIGINAL_HTML
    }

    /**
     * Компактный набор маркеров finish-страницы.
     *
     * Используется только как диагностическая/решающая структура внутри finish-flow:
     * - наличие FEND-формы и поля `code`;
     * - признаки fkey/captcha;
     * - служебные токены из `fight_ty`/`fexp`.
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
     * Bridge в инфраструктуру MainPhp.
     *
     * Правило:
     * - FightAuto не дублирует уже существующие helper-методы, а использует делегирование;
     * - любые операции, завязанные на внешний контекст (чаты, popup, URL-утилиты, парсеры MainPhp),
     *   должны приходить через Host.
     *
     * Это позволяет менять боевую логику в одном месте, не ломая остальной pipeline MainPhp.
     */
    public interface Host {
        void logFightVariable(String html, String variableName);

        InsHpSnapshot parseInsHpSnapshot(String html);

        void clearAutoFightProbeFinishCandidate();

        boolean isAutoFightProbeAddress(String address);

        String resolveFightCaptchaUrl(String html);

        boolean isFightFrameHtml(String html);

        void registerFightEnd(LezFight fight);

        void publishFightResultFromLogsIfNeeded(String html, String address, String logIdHint);

        void recoverAutoboiRuntimeStateIfNeeded(boolean fightEnded, String fightCaptchaUrl);

        boolean isAutoFightEnabledByPreference();

        String buildRestoringStatusHtml(String address,
                                        int delayMs,
                                        long waitMs,
                                        int curHp,
                                        int maxHp,
                                        int curMa,
                                        int maxMa,
                                        boolean waitHpEnabled,
                                        int waitHpPercent,
                                        boolean waitMaEnabled,
                                        int waitMaPercent);

        void notifyNewFight(LezFight fight);

        boolean isAutoSkinEnabledByPreference();

        String mainPhpRaz(String html);

        String buildDelayedRedirectHtml(String description, String link, int delayMs);

        String extractFightFinishLinkFromHtml(String html, boolean withCaptchaPlaceholder);

        String extractFightCleanFinishLinkFromHtml(String html);

        String normalizeNeverlandsMainLink(String link);

        boolean isAutoFightProbeFinishConfirmed(String logBoi, String fightLink);

        void showFightCaptchaDialogOnce(String captchaUrl, String finishUrl, String logBoi);

        String getUrlParam(String url, String paramName);

        void notifyCaptchaRejectedOnce(String submittedCode, String submittedVcode);

        String buildInPlaceFightAutoRefreshHtml(String html, String reloadUrl, int delayMs);

        void notifyFightStopped(LezFight fight);

        List<String> splitJsTopLevelCsv(String raw);

        String trimJsToken(String token);

        String escapeHtmlAttr(String value);
    }

    /**
     * Основная точка обработки боевого кадра.
     *
     * Поток решения:
     * 1) Парсинг LezFight + снимка HP/MA.
     * 2) Детект текущей фазы: активный бой / ожидание / завершение.
     * 3) Синхронизация runtime-состояний AutoBoi (Timeout/Restoring/On).
     * 4) Finish-flow (FightLink/FEND/CAPTCHA/manual).
     * 5) Возврат кадра авто-удара либо исходного HTML в соответствии с текущими флагами.
     *
     * Зависимости:
     * - {@link LezFight} как источник боевых флагов и frame-HTML;
     * - {@link AppVars} как единое runtime-хранилище;
     * - {@link Host} для всех внешних helper-операций MainPhp.
     *
     * Важно:
     * - метод intentionally сохраняет прежнюю логику ветвлений;
     * - любые изменения здесь должны быть только осознанными, с обязательной проверкой логов боя.
     */
    public static String processFight(String address, String html, Host host) {
        if (host == null || html == null) {
            return html;
        }
        String msg1 = "processFight: address=" + address + ", htmlLen=" + html.length();
        AppLog.d(TAG, TAG, msg1);
        host.logFightVariable(html, "fight_ty");
        host.logFightVariable(html, "param_en");
        host.logFightVariable(html, "slots_en");
        host.logFightVariable(html, "param_my");
        host.logFightVariable(html, "slots_my");
        host.logFightVariable(html, "LogBoi");

        LezFight fight = new LezFight(html);
        InsHpSnapshot insHpSnapshot = host.parseInsHpSnapshot(html);

        boolean dumpFightHtml = AppVars.DebugDumpFightHtml
                || (AppVars.Profile != null && AppVars.Profile.DoHttpLog);
        if (dumpFightHtml) {
            int chunkSize = 800;
            int totalLen = html.length();
            int chunks = (totalLen + chunkSize - 1) / chunkSize;
            String msg2 = "processFight: HTML dump, total=" + totalLen + " bytes, chunks=" + chunks;
            AppLog.d(TAG, TAG, msg2);
            for (int i = 0; i < chunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, totalLen);
                String msg3 = "processFight HTML[" + start + "-" + end + "]: "
                        + html.substring(start, end);
                AppLog.d(TAG, TAG, msg3);
            }
        }

        String msg4 = "processFight: LezFight parsed:"
                + " IsValid=" + fight.IsValid
                + " IsBoi=" + fight.IsBoi
                + " IsWaitingForNextTurn=" + fight.IsWaitingForNextTurn
                + " DoStop=" + fight.DoStop
                + " IsLowHp=" + fight.IsLowHp
                + " IsLowMa=" + fight.IsLowMa
                + " DoExit=" + fight.DoExit
                + " LogBoi=" + fight.LogBoi;
        AppLog.d(TAG, TAG, msg4);
        if (!fight.IsValid) {
            String msg = "processFight: fight.IsValid=false, returning original HTML";
            AppLog.d(TAG, TAG, msg);
            return html;
        }

        if (fight.IsBoi) {
            AppVars.LastFightPulseAtMs = System.currentTimeMillis();
            host.clearAutoFightProbeFinishCandidate();
        }

        final boolean autoFightProbeAddress = host.isAutoFightProbeAddress(address);
        final FightFinishPageMarkers finishMarkers = inspectFightFinishPageMarkers(html, host);
        final String resolvedFightCaptchaUrl = host.resolveFightCaptchaUrl(html);
        final String cleanFinishLinkCandidate = host.extractFightCleanFinishLinkFromHtml(html);
        final boolean isCrashWaitingFinishFrame = !fight.IsBoi
            && fight.IsWaitingForNextTurn
            && (resolvedFightCaptchaUrl == null || resolvedFightCaptchaUrl.isEmpty())
            && !finishMarkers.hasFendForm
            && !finishMarkers.hasCodeInput
            && !finishMarkers.hasCaptchaImage
            && isCrashWaitingFinishFrameHtml(html, cleanFinishLinkCandidate);
        final boolean isProbeTransitionalInactiveFrame = autoFightProbeAddress
                && !fight.IsBoi
                && !fight.IsWaitingForNextTurn
                && (resolvedFightCaptchaUrl == null || resolvedFightCaptchaUrl.isEmpty())
                && !finishMarkers.hasFendForm
                && !finishMarkers.hasCodeInput
                && !finishMarkers.hasCaptchaImage
                && finishMarkers.hasFkeyScript
                && host.isFightFrameHtml(html);
        if (isProbeTransitionalInactiveFrame) {
            String msg = "processFight: probe transitional inactive frame detected, postpone finish flow"
                    + ", address=" + address
                    + ", logBoi=" + fight.LogBoi;
            AppLog.d(TAG, TAG, msg);
        }
        if (isCrashWaitingFinishFrame) {
            String msg = "processFight: crash waiting frame with clean finish detected, force finish flow"
                + ", address=" + address
                + ", logBoi=" + fight.LogBoi
                + ", cleanLink=" + cleanFinishLinkCandidate;
            AppLog.w(TAG, TAG, msg);
        }

        boolean fightEnded = (!fight.IsBoi && !fight.IsWaitingForNextTurn && !isProbeTransitionalInactiveFrame)
            || isCrashWaitingFinishFrame;
        if (fightEnded) {
            host.registerFightEnd(fight);
            host.publishFightResultFromLogsIfNeeded(html, address, fight.LogBoi);
            // 🎯 Бой закончился - SessionManager может вернуться на обычный 5-минутный timeout
            ru.neverlands.abclient.utils.SessionManager.getInstance().clearFightContext();            
            // ✅ КРИТИЧНОЕ ЛОГИРОВАНИЕ: Очистить FastNeed когда бой завершился
            if (AppVars.FastNeed) {
                String msg = "[FIGHT_ENDED_CLEANUP] fight ended, clearing FastNeed to allow auto-fishing resume"
                        + ", logBoi=" + fight.LogBoi
                        + ", oldFastNeed=true"
                        + ", oldFastId='" + AppVars.FastId + "'";
                AppLog.i(TAG, TAG, msg);
                
                ru.neverlands.abclient.manager.FastActionManager.fastCancel("fight_ended");
                
                String cancelMsg = "[FIGHT_ENDED_CLEANUP_COMPLETED] FastNeed cleared after fight end";
                AppLog.i(TAG, TAG, cancelMsg);
            }
            if (FishAjaxPhp.isAutoFishEnabled()) {
                String resumeMsg = "[FIGHT_ENDED_AUTOFISH_RESUME] fight ended, restart auto-fishing cycle"
                        + ", logBoi=" + fight.LogBoi;
                AppLog.i(TAG, TAG, resumeMsg);
                FishAjaxPhp.restartAutoFishCycle("fight_ended");
            }
        }
        String fightCaptchaUrl = fightEnded ? resolvedFightCaptchaUrl : null;
        host.recoverAutoboiRuntimeStateIfNeeded(fightEnded, fightCaptchaUrl);

        final boolean autoFightEnabledByPreference = host.isAutoFightEnabledByPreference();
        final boolean autoFightEnabled = autoFightEnabledByPreference
                || AppVars.Autoboi == AutoboiState.AutoboiOn;
        final boolean waitHpEnabled = AppVars.Profile != null && AppVars.Profile.LezDoWaitHp;
        final int waitHpPercent = AppVars.Profile != null ? AppVars.Profile.LezWaitHp : 100;
        final boolean waitMaEnabled = AppVars.Profile != null && AppVars.Profile.LezDoWaitMa;
        final int waitMaPercent = AppVars.Profile != null ? AppVars.Profile.LezWaitMa : 100;

        if (fightEnded && autoFightEnabled) {
            long now = System.currentTimeMillis();
            if (AppVars.Autoboi == AutoboiState.Timeout) {
                AppVars.AutoboiReadyAtMs = 0L;
                AppVars.AutoboiReadyLog = "";
                AppVars.Autoboi = AutoboiState.AutoboiOn;
                String msg_timeout = "processFight: Timeout finished on fight end -> AutoboiOn";
                AppLog.d(TAG, TAG, msg_timeout);
            }
            if (AppVars.Autoboi == AutoboiState.Restoring) {
                boolean logChanged = fight.LogBoi != null && !fight.LogBoi.equals(AppVars.AutoboiReadyLog);
                boolean timerReady = AppVars.AutoboiReadyAtMs > 0L && now >= AppVars.AutoboiReadyAtMs;
                if (logChanged || timerReady) {
                    if (!logChanged && timerReady && fight.LogBoi != null && !fight.LogBoi.isEmpty()) {
                        AppVars.AutoboiReadyCompletedLog = fight.LogBoi;
                        String msg = "processFight: restoring timer elapsed, mark completed for log=" + fight.LogBoi;
                        AppLog.d(TAG, TAG, msg);
                    }
                    AppVars.AutoboiReadyAtMs = 0L;
                    AppVars.AutoboiReadyLog = "";
                    AppVars.Autoboi = AutoboiState.AutoboiOn;
                    String msg = "processFight: restoring finished -> AutoboiOn";
                    AppLog.d(TAG, TAG, msg);
                }
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
                        String msg_set_restoring = "processFight: set Restoring until " + AppVars.AutoboiReadyAtMs
                                + " (deferred until after finish link)";
                        AppLog.d(TAG, TAG, msg_set_restoring);
                    }
                } else {
                    String msg = "processFight: restoring already completed for current log, continue to finish";
                    AppLog.d(TAG, TAG, msg);
                }
                AppVars.AutoboiReadyAtMs = 0L;
                AppVars.AutoboiReadyLog = "";
            }
        }

        if (fight.IsBoi && fight.LogBoi != null && !fight.LogBoi.isEmpty()
                && !fight.LogBoi.equals(AppVars.LastBoiLog)) {
            String msg_new_fight = "processFight: NEW FIGHT detected! LogBoi changed: "
                    + AppVars.LastBoiLog + " -> " + fight.LogBoi;
            AppLog.d(TAG, TAG, msg_new_fight);
            AppVars.LastBoiLog = fight.LogBoi;
            AppVars.LastBoiUron = "";
            lastAutoSkinProbeFightLog = "";
            AppVars.AutoboiReadyCompletedLog = "";
            resetAutoFinishLoopGuard();
            fight.updateLastBoiFromLogs();
            // 🎯 Отметить что начался новый бой - SessionManager должен дольше хранить vcode
            ru.neverlands.abclient.utils.SessionManager.getInstance().markFightInProgress();
            host.notifyNewFight(fight);
            UnderAttackManager.parseAsync(html);
        }
        if (fightEnded
                && host.isAutoSkinEnabledByPreference()
                && address != null
                && address.contains("get_id=17")) {
            // Fallback C#-семантики AutoSkin:
            // после кадра get_id=17 (разделка) в любом случае планируем проверку ресурсов.
            // Это покрывает кейсы, когда var logs не содержит [8,...], но разделка сервером уже применена.
            AppVars.AutoSkinCheckRes = true;
            String msg_autoskin = "AUTO_SKIN_TRACE processFight: queue AutoSkinCheckRes=true after get_id=17"
                    + ", logBoi=" + fight.LogBoi;
            AppLog.d(TAG, TAG, msg_autoskin);
        }
        if (fightEnded && host.isAutoSkinEnabledByPreference()) {
            boolean alreadyOnRazAddress = address != null && address.contains("get_id=17");
            if (!alreadyOnRazAddress) {
                String razHtml = host.mainPhpRaz(html);
                if (razHtml != null) {
                    String msg_raz_before_finish = "AUTO_SKIN_TRACE processFight: fight ended, run raz before finish";
                    AppLog.d(TAG, TAG, msg_raz_before_finish);
                    return razHtml;
                }
                boolean infAddress = address != null && address.contains("get_id=56&act=10&go=inf");
                boolean finishAddress = address != null && address.contains("get_id=61&act=5");
                boolean runtimeMainAddress = address != null
                        && address.contains("main.php?r=")
                        && !host.isAutoFightProbeAddress(address);
                boolean probeCandidateAddress = infAddress || finishAddress || runtimeMainAddress;
                boolean hasFightLog = fight.LogBoi != null && !fight.LogBoi.isEmpty();
                boolean probeNotDoneForFight = hasFightLog && !fight.LogBoi.equals(lastAutoSkinProbeFightLog);
                if (probeCandidateAddress && probeNotDoneForFight) {
                    lastAutoSkinProbeFightLog = fight.LogBoi;
                    String probeUrl = "http://neverlands.ru/main.php?r=" + System.currentTimeMillis();
                    String msg_raz_probe = "AUTO_SKIN_TRACE processFight: raz probe redirect to " + probeUrl
                            + ", sourceAddress=" + address;
                    AppLog.d(TAG, TAG, msg_raz_probe);
                    return host.buildDelayedRedirectHtml("Проверка разделки", probeUrl, 260);
                }
            }
        }

        if (fightEnded
                && autoFightEnabled) {
            String msg_fight_ended = "processFight: FIGHT ENDED with autoboi ON - processing finish"
                    + ", AutoboiState=" + AppVars.Autoboi;
            AppLog.d(TAG, TAG, msg_fight_ended);
            String captchaUrl = fightCaptchaUrl;
            boolean needCaptcha = captchaUrl != null && !captchaUrl.isEmpty();
            String fightLink = AppVars.FightLink;
            if (fightLink == null || fightLink.isEmpty()) {
                String recoveredFightLink = host.extractFightFinishLinkFromHtml(html, needCaptcha);
                if (recoveredFightLink != null && !recoveredFightLink.isEmpty()) {
                    fightLink = recoveredFightLink;
                    AppVars.FightLink = recoveredFightLink;
                    String msg_recovered_link = "processFight: recovered finish link from html: " + recoveredFightLink;
                    AppLog.d(TAG, TAG, msg_recovered_link);
                }
            }
            if (!needCaptcha) {
                // 🔥 FIX: Не заменять валидный act=7 (полный финальный) линк на act=5 (промежуточный).
                // act=7 — реальный "Завершить бой" (с fexp), отправляет результат бою на сервер.
                // act=5&st=6 — только refresh/статус боя, может зацикливаться без смены состояния.
                // Clean link используется ТОЛЬКО если act=7 не доступен.
                boolean alreadyHasAct7 = fightLink != null && !fightLink.isEmpty()
                        && fightLink.contains("act=7");
                if (alreadyHasAct7) {
                    String msg_skip_clean = "processFight: skip CLEAN link override, already have act=7: " + fightLink;
                    AppLog.d(TAG, TAG, msg_skip_clean);
                }
                String cleanFinishLink = alreadyHasAct7 ? null : host.extractFightCleanFinishLinkFromHtml(html);
                if (cleanFinishLink != null && !cleanFinishLink.isEmpty()) {
                    boolean replacedPrevious = fightLink != null
                            && !fightLink.isEmpty()
                            && !cleanFinishLink.equals(fightLink);
                    fightLink = cleanFinishLink;
                    AppVars.FightLink = cleanFinishLink;
                    String msg_clean_link = "processFight: recovered CLEAN finish link from html: "
                            + cleanFinishLink + (replacedPrevious ? " (override previous fightLink)" : "");
                    AppLog.d(TAG, TAG, msg_clean_link);
                }
            }

            FightFinishPageMarkers markers = finishMarkers;
            FinishFlowDecision decision;
            String decisionReason;
            String finishFormSubmitHtml = null;
            if (needCaptcha) {
                host.clearAutoFightProbeFinishCandidate();
                decision = FinishFlowDecision.CAPTCHA_REQUIRED;
                decisionReason = "captcha_url_detected";
                if (fightLink == null || fightLink.isEmpty()) {
                    fightLink = address;
                }
                if (fightLink == null || fightLink.isEmpty()) {
                    fightLink = "http://neverlands.ru/main.php";
                }
                String normalizedCaptchaFinish = host.normalizeNeverlandsMainLink(fightLink);
                if (normalizedCaptchaFinish != null && !normalizedCaptchaFinish.isEmpty()) {
                    fightLink = normalizedCaptchaFinish;
                }
                } else if (autoFightProbeAddress
                    && !isCrashWaitingFinishFrame
                    && fightLink != null
                    && !fightLink.isEmpty()
                    && !fightLink.contains("????")
                    && !host.isAutoFightProbeFinishConfirmed(fight.LogBoi, fightLink)) {
                decision = FinishFlowDecision.KEEP_ORIGINAL_HTML;
                decisionReason = "probe_finish_needs_confirmation";
                String msg_probe_defer = "processFight: defer direct finish on probe frame, waiting confirmation"
                        + ", address=" + address
                        + ", logBoi=" + fight.LogBoi
                        + ", fightLink=" + fightLink;
                AppLog.d(TAG, TAG, msg_probe_defer);
            } else if (fightLink != null && !fightLink.isEmpty() && !fightLink.contains("????")) {
                host.clearAutoFightProbeFinishCandidate();
                decision = FinishFlowDecision.DIRECT_FINISH_LINK;
                decisionReason = "fight_link_ready";
            } else {
                if (!autoFightProbeAddress) {
                    host.clearAutoFightProbeFinishCandidate();
                }
                finishFormSubmitHtml = buildFightEndFormSubmitHtml(html, host);
                if (finishFormSubmitHtml != null) {
                    decision = FinishFlowDecision.FEND_AUTOSUBMIT_ALLOWED;
                    decisionReason = "fight_link_missing_but_fend_ready";
                } else {
                    decision = FinishFlowDecision.KEEP_ORIGINAL_HTML;
                    decisionReason = "fight_link_missing_and_fend_not_ready";
                }
            }

            logFinishFlowDecision(decision, fight, address, fightLink, captchaUrl, markers, decisionReason);
            if (decision == FinishFlowDecision.CAPTCHA_REQUIRED) {
                String msg_captcha_required = "processFight: CAPTCHA required, stopping autoboi and showing dialog: " + captchaUrl;
                AppLog.d(TAG, TAG, msg_captcha_required);
                boolean fromCaptchaSubmit = address != null
                        && address.contains("get_id=61")
                        && address.contains("act=7")
                        && address.contains("code=");
                if (fromCaptchaSubmit) {
                    AppVars.LastSubmittedFightCaptchaFinishKey = "";
                    AppVars.LastSubmittedFightCaptchaAtMs = 0L;
                }
                AppVars.ResumeSearchBoxAfterCaptcha = AppVars.DoSearchBox || AppVars.AutoMoving;
                AppVars.ResumeAutoboiAfterCaptcha = true;
                AppVars.Autoboi = AutoboiState.AutoboiOff;
                AppVars.ContentMainPhp = html;
                host.showFightCaptchaDialogOnce(captchaUrl, fightLink, fight.LogBoi);
                return html;
            }
            if (decision == FinishFlowDecision.DIRECT_FINISH_LINK) {
                long now = System.currentTimeMillis();
                if (isFinishRedirectLoopDetected(fight.LogBoi, fightLink, now)) {
                    String msg_finish_loop = "processFight: finish loop detected, fallback to main.php"
                            + ", logBoi=" + (fight.LogBoi == null ? "" : fight.LogBoi)
                            + ", fightLink=" + fightLink
                            + ", redirects=" + lastAutoFinishRedirectCount;
                    AppLog.w(TAG, TAG, msg_finish_loop);
                    resetAutoFinishLoopGuard();
                    AppVars.FightLink = "";
                    return host.buildDelayedRedirectHtml(
                            AUTO_FINISH_TITLE,
                            "http://neverlands.ru/main.php",
                            AUTO_FINISH_LOOP_FALLBACK_DELAY_MS);
                }
                int redirectDelay = AUTO_FINISH_MIN_DELAY_MS + RANDOM.nextInt(AUTO_FINISH_EXTRA_DELAY_MS + 1);
                if (redirectDelay >= 0) {
                    lastAutoFinishRedirectAtMs = now;
                    AppVars.FightLink = "";
                    return host.buildDelayedRedirectHtml(AUTO_FINISH_TITLE, fightLink, redirectDelay);
                }
                AppVars.FightLink = "";
                return Russian.getString(Filter.buildRedirect(" ", fightLink));
            }
            if (decision == FinishFlowDecision.FEND_AUTOSUBMIT_ALLOWED && finishFormSubmitHtml != null) {
                String msg_fend_autosubmit = "processFight: FightLink missing, auto-submit FEND form";
                AppLog.d(TAG, TAG, msg_fend_autosubmit);
                AppVars.FightLink = "";
                return finishFormSubmitHtml;
            }
            String msg_fend_missing = "processFight: FightLink missing and FEND not parsed, keep original fight HTML";
            AppLog.d(TAG, TAG, msg_fend_missing);
            AppVars.FightLink = "";
            AppVars.ContentMainPhp = html;
            return html;
        }

        if (fightEnded) {
            String manualCaptchaUrl = fightCaptchaUrl;
            if (manualCaptchaUrl != null && !manualCaptchaUrl.isEmpty()) {
                String finishLink = AppVars.FightLink;
                if (finishLink == null || finishLink.isEmpty()) {
                    finishLink = address;
                }
                String msg_manual_captcha = "processFight: manual mode CAPTCHA detected, showing dialog: " + manualCaptchaUrl;
                AppLog.d(TAG, TAG, msg_manual_captcha);
                boolean fromCaptchaSubmit = address != null && address.contains("code=");
                if (fromCaptchaSubmit) {
                    String submittedCode = host.getUrlParam(address, "code");
                    String submittedVcode = host.getUrlParam(address, "vcode");
                    String msg_captcha_submit = "processFight: captcha submit still requires challenge, code="
                            + submittedCode + ", vcode=" + submittedVcode;
                    AppLog.d(TAG, TAG, msg_captcha_submit);
                    AppVars.LastSubmittedFightCaptchaFinishKey = "";
                    AppVars.LastSubmittedFightCaptchaAtMs = 0L;
                    host.notifyCaptchaRejectedOnce(submittedCode, submittedVcode);
                }
                host.showFightCaptchaDialogOnce(manualCaptchaUrl, finishLink, fight.LogBoi);
                AppVars.ContentMainPhp = html;
                return html;
            }
        }
        if (fight.IsWaitingForNextTurn) {
            String msg = "processFight: waiting for opponent turn (foe HP=" + fight.FoeCurrentHp + ")";
            AppLog.d(TAG, TAG, msg);
            boolean shouldAutoRefresh = AppVars.AutoRefresh;
            if (!shouldAutoRefresh && autoFightEnabled
                    && AppVars.Autoboi == AutoboiState.AutoboiOn) {
                shouldAutoRefresh = true;
            }
            if (shouldAutoRefresh) {
                int delay = 1200 + RANDOM.nextInt(900);
                String msg_autorefresh = "processFight: auto-refresh waiting enabled, reloading after " + delay + "ms: " + address;
                AppLog.d(TAG, TAG, msg_autorefresh);
                return host.buildInPlaceFightAutoRefreshHtml(html, address, delay);
            }
            String msg_autorefresh_disabled = "processFight: AutoRefresh disabled, returning original content";
            AppLog.d(TAG, TAG, msg_autorefresh_disabled);
            return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
        }

        if (autoFightEnabled) {
            String msg_lezdoautoboi = "processFight: LezDoAutoboi enabled, Autoboi state=" + AppVars.Autoboi;
            AppLog.d(TAG, TAG, msg_lezdoautoboi);
            if (AppVars.Autoboi == AutoboiState.AutoboiOn) {
                if (fight.IsBoi) {
                    String msg_safety_check = "processFight: in fight, checking safety conditions:"
                            + " DoStop=" + fight.DoStop
                            + " IsLowHp=" + fight.IsLowHp
                            + " IsLowMa=" + fight.IsLowMa
                            + " DoExit=" + fight.DoExit;
                    AppLog.d(TAG, TAG, msg_safety_check);

                    if (!fight.DoStop && !fight.IsLowHp && !fight.IsLowMa && !fight.DoExit) {
                        String msg_safe = "processFight: SAFE - returning fight.Frame for auto-attack";
                        AppLog.d(TAG, TAG, msg_safe);
                        String msg_frame = "processFight: fight.Frame = " + (fight.Frame != null ? fight.Frame.substring(0, Math.min(200, fight.Frame.length())) : "NULL");
                        AppLog.d(TAG, TAG, msg_frame);
                        if (fight.Frame != null && !fight.Frame.isEmpty()) {
                            return fight.Frame;
                        }
                        AppLog.w(TAG, "processFight: fight.Frame is empty, stopping autoboi to avoid null flow");
                        if (AppVars.Autoboi != AutoboiState.Timeout) {
                            host.notifyFightStopped(fight);
                            AppVars.Autoboi = AutoboiState.Timeout;
                        }
                        return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
                    } else {
                        String msg_dangerous = "processFight: DANGEROUS - stopping autoboi, setting Timeout";
                        AppLog.d(TAG, TAG, msg_dangerous);
                        if (AppVars.Autoboi != AutoboiState.Timeout) {
                            host.notifyFightStopped(fight);
                            AppVars.Autoboi = AutoboiState.Timeout;
                        }
                    }
                } else {
                    String msg_fight_ended_handled = "processFight: fight ended branch already handled, keep current frame";
                    AppLog.d(TAG, TAG, msg_fight_ended_handled);
                }
            } else {
                String msg_autoboi_state = "processFight: Autoboi state is " + AppVars.Autoboi + ", not AutoboiOn";
                AppLog.d(TAG, TAG, msg_autoboi_state);
            }
        } else {
            String msg_autofight_disabled = "processFight: auto-fight disabled for this frame"
                    + " pref=" + autoFightEnabledByPreference
                    + ", runtimeState=" + AppVars.Autoboi;
            AppLog.d(TAG, TAG, msg_autofight_disabled);
            if (!fight.IsBoi) {
                String msg_autofight_disabled_manual = "processFight: autoboi disabled, keeping original fight frame for manual finish";
                AppLog.d(TAG, TAG, msg_autofight_disabled_manual);
            }
        }

        String msg_flags = "processFight flags:"
                + " magic_slots=" + html.contains("magic_slots();")
                + " fight_ty=" + html.contains("var fight_ty")
                + " IsBoi_form=" + html.contains("<form")
                + " StartAct=" + html.contains("StartAct()")
                + " document.ff=" + html.contains("document.ff")
                + " autosubmit=" + html.contains("document.ff.submit");
        AppLog.d(TAG, TAG, msg_flags);
        return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
    }

    private static void resetAutoFinishLoopGuard() {
        lastAutoFinishRedirectAtMs = 0L;
        lastAutoFinishRedirectLog = "";
        lastAutoFinishRedirectCount = 0;
        lastAutoFinishRedirectWindowStartMs = 0L;
    }

    private static boolean isFinishRedirectLoopDetected(String logBoi, String fightLink, long nowMs) {
        String safeLogBoi = logBoi == null ? "" : logBoi.trim();
        String safeFightLink = fightLink == null ? "" : fightLink.trim();
        String loopKey = !safeLogBoi.isEmpty() ? safeLogBoi : safeFightLink;
        if (loopKey.isEmpty()) {
            return false;
        }

        boolean sameLoopKey = loopKey.equals(lastAutoFinishRedirectLog);
        boolean windowExpired = nowMs - lastAutoFinishRedirectWindowStartMs > AUTO_FINISH_LOOP_WINDOW_MS;
        if (!sameLoopKey || windowExpired) {
            lastAutoFinishRedirectLog = loopKey;
            lastAutoFinishRedirectWindowStartMs = nowMs;
            lastAutoFinishRedirectCount = 1;
            return false;
        }

        lastAutoFinishRedirectCount++;
        return lastAutoFinishRedirectCount > AUTO_FINISH_MAX_REDIRECTS_PER_LOG;
    }

    /**
     * Строит auto-submit HTML для FEND-формы завершения боя.
     *
     * Правила:
     * - если сервер требует ручной код (`code` пустой/`????`), авто-submit запрещён;
     * - сериализуются только безопасные поля формы (без submit/button/reset/image/file).
     *
     * Зависимости:
     * - Jsoup для извлечения формы/полей;
     * - {@link Host#escapeHtmlAttr(String)} для безопасной подстановки в HTML-атрибуты;
     * - {@link HtmlUtils#GENERATED_PAGE_MARKER} для маркировки служебной страницы.
     */
    private static String buildFightEndFormSubmitHtml(String html, Host host) {
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
                    String msg_code_required = "buildFightEndFormSubmitHtml: code required, skip auto-submit";
                    AppLog.d(TAG, TAG, msg_code_required);
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
                    .append(host.escapeHtmlAttr(action))
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
                        .append(host.escapeHtmlAttr(name))
                        .append("\" value=\"")
                        .append(host.escapeHtmlAttr(value))
                        .append("\">");
            }
            sb.append("</form>");
            sb.append("<script language=\"JavaScript\">");
            sb.append("setTimeout(function(){var f=document.getElementById('ab_finish_form'); if(f){f.submit();}}, 350);");
            sb.append("</script></body></html>");
            return sb.toString();
        } catch (Exception e) {
            AppLog.e(TAG, "buildFightEndFormSubmitHtml error", e);
            return null;
        }
    }
    /**
     * Считывает маркеры finish-страницы из сырого HTML.
     *
     * Назначение:
     * - подготовить фактические признаки для выбора {@link FinishFlowDecision};
     * - дать полный диагностический контекст для логирования причины ветки.
     *
     * Зависимости:
     * - Jsoup-селекторы (`form[name=FEND]`, `input[name=code]`);
     * - {@link HelperStrings#subString(String, String, String)} + JS-token helper-методы Host
     *   для разбора `fight_ty`/`fexp`.
     */
    private static FightFinishPageMarkers inspectFightFinishPageMarkers(String html, Host host) {
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
            String rawFightTy = HelperStrings.subString(html, "var fight_ty = [", "];" );
            if (rawFightTy != null && !rawFightTy.isEmpty()) {
                List<String> fightTy = host.splitJsTopLevelCsv(rawFightTy);
                if (fightTy.size() > 5) {
                    markers.challengeHash = host.trimJsToken(fightTy.get(5));
                }
            }
            String rawFexp = HelperStrings.subString(html, "var fexp = [", "];" );
            if (rawFexp != null && !rawFexp.isEmpty()) {
                List<String> fexp = host.splitJsTopLevelCsv(rawFexp);
                if (fexp.size() > 4) {
                    markers.fexpCaptchaToken = host.trimJsToken(fexp.get(4));
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "inspectFightFinishPageMarkers error", e);
        }
        return markers;
    }

    private static boolean isCrashWaitingFinishFrameHtml(String html, String cleanFinishLinkCandidate) {
        if (html == null || html.isEmpty() || cleanFinishLinkCandidate == null || cleanFinishLinkCandidate.isEmpty()) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        boolean hasAct5Finish = lower.contains("get_id=61") && lower.contains("act=5");
        boolean hasShortFinishButton = lower.contains("value=\"завершить\"")
                || lower.contains("value='завершить'")
                || lower.contains(">завершить<");
        boolean hasRegularFightFinishButton = lower.contains("завершить бой");
        return hasAct5Finish && hasShortFinishButton && !hasRegularFightFinishButton;
    }

    /**
     * Пишет структурированный лог выбранной ветки завершения боя.
     *
     * Назначение:
     * - упростить разбор сложных кейсов, когда бой зависает на finish/captcha;
     * - фиксировать не только выбор ветки, но и контекст (маркеры HTML, токены, URL).
     *
     * Правило:
     * - лог должен быть максимально информативным, но не менять runtime-поведение.
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
        String msg_finish_flow = "processFight finishFlow:"
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
                + ", address=" + (address == null ? "" : address);
        AppLog.d(TAG, TAG, msg_finish_flow);
    }

    static String formatHms(long seconds) {
        long total = Math.max(0L, seconds);
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    static String getUrlParam(String url, String paramName) {
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
            AppLog.e(TAG, "getUrlParam error: " + paramName, e);
        }
        return "";
    }

    static int parseUrlParamInt(String url, String paramName, int fallback) {
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

    static String appendOrReplaceUrlParam(String url, String paramName, String paramValue) {
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

    static String[] extractJsArrayTokens(String html, String prefix) {
        if (html == null || html.isEmpty() || prefix == null || prefix.isEmpty()) {
            return null;
        }
        String args = HelperStrings.subString(html, prefix, "]");
        if (args == null || args.isEmpty()) {
            return null;
        }
        return args.split(",");
    }

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

    static String trimJsToken(String token) {
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

    static String normalizeNeverlandsMainLink(String link) {
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

    static String findMainPhpLinkByQueryParts(String html, String... queryParts) {
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

    static String setOrAppendQueryParam(String url, String key, String value) {
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

    static String buildRestoringStatusHtml(String reloadUrl,
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
                ? ("HP \u2265 " + waitHpPercent + "% (" + hpGoal + "/" + maxHp + ")")
                : "HP: \u043e\u0436\u0438\u0434\u0430\u043d\u0438\u0435 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u043e";
        String maTargetText = doWaitMa
                ? ("MA \u2265 " + waitMaPercent + "% (" + maGoal + "/" + maxMa + ")")
                : "MA: \u043e\u0436\u0438\u0434\u0430\u043d\u0438\u0435 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u043e";
        String hpMaLine = "(" + curHp + "/" + maxHp + " + " + curMa + "/" + maxMa + ")";
        return HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head>" +
                "<body style='font-family:Arial,sans-serif;padding:10px;background:#fff;'>" +
                "<div id='ab_restore_title' style='font-weight:bold;color:#7B0A0A;'>\u041e\u0441\u0442\u0430\u043d\u043e\u0432 \u043b\u0435\u0447\u0435\u043d\u0438\u044f</div>" +
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

    static String buildDelayedRedirectHtml(String description, String link, int delayMs) {
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

    static String buildWaitForTurnAutoRefreshHtml(String reloadUrl, int delayMs) {
        String safeUrl = (reloadUrl != null && !reloadUrl.isEmpty()) ? reloadUrl : "main.php";
        int safeDelay = Math.max(300, delayMs);
        return HtmlUtils.GENERATED_PAGE_MARKER +
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">" +
                "<title>ABClient</title></head><body>" +
                "\u041e\u0436\u0438\u0434\u0430\u0435\u043c \u0445\u043e\u0434\u0430 \u043f\u0440\u043e\u0442\u0438\u0432\u043d\u0438\u043a\u0430...<br>" +
                "<script language=\"JavaScript\">" +
                "setTimeout(function(){ window.location = '" + safeUrl + "'; }, " + safeDelay + ");" +
                "</script></body></html>";
    }

    static String buildInPlaceFightAutoRefreshHtml(String html, String reloadUrl, int delayMs) {
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

    private static final long CAPTCHA_FALLBACK_TTL_MS = 5000L;
    private static volatile String lastFightCaptchaDialogKey = "";
    private static volatile long lastFightCaptchaDialogAtMs = 0L;
    private static volatile String lastCaptchaRejectKey = "";
    private static volatile long lastCaptchaRejectAtMs = 0L;
    private static volatile String lastFightResultWinnerBroadcastKey = "";
    private static volatile String lastFightResultLootBroadcastKey = "";
    private static volatile String lastFightSummaryBroadcastKey = "";

    private static final long AUTO_FIGHT_PROBE_FINISH_CONFIRM_WINDOW_MS = 4500L;
    private static volatile String lastAutoFightProbeFinishCandidateKey = "";
    private static volatile long lastAutoFightProbeFinishCandidateAtMs = 0L;

    static boolean isFightFrameHtml(String html) {
        return html != null && (html.contains("var fight_ty") || html.contains("magic_slots();"));
    }

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

    static boolean isAutoFightBackgroundProbeAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        String lower = address.toLowerCase(Locale.ROOT);
        return lower.contains("main.php") && lower.contains("ab_bg_probe=1");
    }

    static boolean isAutoFightProbeAddress(String address) {
        return isAutoFightReloadProbeAddress(address) || isAutoFightBackgroundProbeAddress(address);
    }

    static String buildAutoFightProbeFinishCandidateKey(String logBoi, String fightLink) {
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

    static void clearAutoFightProbeFinishCandidate() {
        lastAutoFightProbeFinishCandidateKey = "";
        lastAutoFightProbeFinishCandidateAtMs = 0L;
    }

    static boolean isAutoFightProbeFinishConfirmed(String logBoi, String fightLink) {
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

    static boolean isAutoFightEnabledByPreference() {
        try {
            android.content.Context context = AppVars.getContext();
            if (context == null) {
                return AppVars.Profile != null && AppVars.Profile.LezDoAutoboi;
            }
            return ru.neverlands.abclient.manager.AutoFunctionsManager.getInstance(context).isAutoFightEnabled();
        } catch (Exception e) {
            String msg = "isAutoFightEnabledByPreference: fallback to profile flag";
            AppLog.w(TAG, msg);
            return AppVars.Profile != null && AppVars.Profile.LezDoAutoboi;
        }
    }

    static void recoverAutoboiRuntimeStateIfNeeded(boolean fightEnded, String fightCaptchaUrl) {
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

    static String extractCaptchaUrl(String html) {
        try {
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
            AppLog.e(TAG, "extractCaptchaUrl error", e);
        }
        return null;
    }

    static String extractCaptchaUrlFromFexp(String html) {
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

    static String resolveFightCaptchaUrl(String html) {
        String captchaUrl = extractCaptchaUrl(html);
        if (captchaUrl != null && !captchaUrl.isEmpty()) {
            return captchaUrl;
        }
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

    static void showFightCaptchaDialogOnce(String captchaUrl, String finishUrl, String logBoi) {
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

    static void notifyCaptchaRejectedOnce(String submittedCode, String submittedVcode) {
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
        String message = "\u041a\u0430\u043f\u0447\u0430 \u043d\u0435 \u043f\u0440\u0438\u043d\u044f\u0442\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u043e\u043c. \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043a\u043e\u0434 \u0437\u0430\u043d\u043e\u0432\u043e.";
        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", "<font color=#cc0000><b>" + message + "</b></font>");
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
    }

    static String extractFightFinishLinkFromHtml(String html, boolean withCaptchaPlaceholder) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String directLink = findMainPhpLinkByQueryParts(html, "get_id=61", "act=7", "fexp=");
        if (directLink != null && !directLink.isEmpty()) {
            if (withCaptchaPlaceholder && !directLink.contains("code=")) {
                directLink = setOrAppendQueryParam(directLink, "code", "????");
            }
            return normalizeNeverlandsMainLink(directLink);
        }
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

    static String extractFightCleanFinishLinkFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String directLink = findMainPhpLinkByQueryParts(html, "get_id=61", "act=5", "st=6", "vcode=");
        if (directLink == null || directLink.isEmpty()) {
            directLink = findMainPhpLinkByQueryParts(html, "get_id=61", "act=5", "st=7", "vcode=");
        }
        if (directLink != null && !directLink.isEmpty()) {
            return normalizeNeverlandsMainLink(directLink);
        }
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
        String vcode = extractFightCleanVcodeFromFexp(html);
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

    static String extractFightCleanVcodeFromFexp(String html) {
        String rawFexp = HelperStrings.subString(html, "var fexp = [", "];");
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

    static String extractFightCleanVcodeFromFightTy(String html) {
        String rawFightTy = HelperStrings.subString(html, "var fight_ty = [", "];");
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

    static String resolveFightFinishStateForAct5(String html) {
        String rawFightTy = HelperStrings.subString(html, "var fight_ty = [", "];");
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

    static void registerFightEnd(LezFight fight) {
        String logId = fight != null ? fight.LogBoi : "";
        registerFightEndByLogId(logId, "fight_frame");
    }

    static void registerFightEndByLogId(String logId, String source) {
        if (logId == null || logId.isEmpty()) return;
        if (!logId.equals(AppVars.LastBoiEndLog)) {
            AppVars.LastBoiEndLog = logId;
            ru.neverlands.abclient.utils.ChatStats.addFight();
            AppVars.LastFightPulseAtMs = 0L;
            String msg = "registerFightEnd: fight counted, source=" + source;
            AppLog.d(TAG, msg);
        } else {
            String msg = "registerFightEnd: skip duplicate, source=" + source;
            AppLog.d(TAG, msg);
        }
    }

    static void logFightVariable(String html, String variableName) {
        String pattern = "var " + variableName;
        int idx = html.indexOf(pattern);
        if (idx < 0) {
            String msg = "logFightVar: " + variableName + " not found";
            AppLog.d(TAG, msg);
            return;
        }
        int end = html.indexOf("\n", idx);
        if (end < 0 || end > idx + 500) end = Math.min(idx + 500, html.length());
        String value = html.substring(idx, end).trim();
        String msg = "logFightVar: " + value;
        AppLog.d(TAG, msg);
    }

    static void publishFightResultFromLogsIfNeeded(String html, String address, String logIdHint) {
        if (html == null || html.isEmpty() || !html.contains("var logs = ")) {
            return;
        }
        String logsBlock = HelperStrings.subString(html, "var logs = ", ";");
        if (logsBlock == null || logsBlock.isEmpty()) {
            return;
        }
        String winnerNick = "";
        java.util.regex.Matcher winnerMatcher = java.util.regex.Pattern.compile(
                "\"<B>\u041f\u043e\u0431\u0435\u0434\u0430 \u0437\u0430</B>\",[1,2,\\\"([^\\\"]+)\\\""
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
                ? "\u0420\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442 \u0440\u0430\u0437\u0434\u0435\u043b\u043a\u0438"
                : "\u0420\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442 \u043e\u0431\u044b\u0441\u043a\u0430 \u0431\u043e\u0442\u0430";
        String lootLine = String.join(",", lootItems);
        String winnerDedupKey = logId.isEmpty() ? ("nol|" + winnerNick) : logId;
        String lootDedupKey = (logId.isEmpty() ? "nol" : logId) + "|" + lootPrefix + "|" + lootLine;
        boolean shouldSendWinner = winnerNick != null
                && !winnerNick.isEmpty()
                && !winnerDedupKey.equals(lastFightResultWinnerBroadcastKey);
        boolean shouldSendLoot = !lootItems.isEmpty()
                && !lootDedupKey.equals(lastFightResultLootBroadcastKey);
        if (!shouldSendWinner && !shouldSendLoot) {
            AppLog.d(TAG, "publishFightResultFromLogsIfNeeded: skip duplicate"
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
                        "<font color=#009933><b>\u041f\u043e\u0431\u0435\u0434\u0430 \u0437\u0430 " + winnerNick + ".</b></font>"
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
        if (isSkinResult) {
            AppVars.AutoSkinCheckRes = true;
            if (skinSkillRaised) {
                AppVars.AutoSkinCheckUm = true;
            }
            AppLog.d(TAG, "AUTO_SKIN_TRACE publishFightResultFromLogsIfNeeded: "
                    + "queue AutoSkinCheckRes=true, AutoSkinCheckUm=" + AppVars.AutoSkinCheckUm
                    + ", lootCount=" + lootItems.size()
                    + ", shouldSendLoot=" + shouldSendLoot
                    + ", source=" + address);
        }
        if (!isSkinResult && shouldSendLoot) {
            ru.neverlands.abclient.utils.ChatStats.addLoot("", lootItems);
        }
        AppLog.d(TAG, "publishFightResultFromLogsIfNeeded: winner=" + winnerNick
                + ", lootCount=" + lootItems.size()
                + ", source=" + address);
    }

    static void publishFightSummaryFromFinishHtmlIfNeeded(String html, String address, String logIdHint) {
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
        int fexp = 0;
        try {
            String fexpRaw = address == null ? "" : getUrlParam(address, "fexp");
            fexp = Integer.parseInt(fexpRaw == null || fexpRaw.isEmpty() ? "0" : fexpRaw);
        } catch (Exception ignore) {
            fexp = 0;
        }
        boolean expectXpByFexp = fexp > 0;
        if (battleXp.isEmpty() && expectXpByFexp && uiForegroundInteractive) {
            AppLog.d(TAG, "publishFightSummaryFromFinishHtmlIfNeeded: skip foreground fallback without XP"
                    + ", logId=" + logId + ", foes=" + foes + ", fexp=" + fexp);
            return;
        }
        String dedupKey = logId + "|" + battleXp;
        if (dedupKey.equals(lastFightSummaryBroadcastKey)) return;
        lastFightSummaryBroadcastKey = dedupKey;
        StringBuilder synthetic = new StringBuilder();
        synthetic.append(buildServerChatTimeHtml())
                .append("<font color=#000000><b>\u0421\u0438\u0441\u0442\u0435\u043c\u043d\u0430\u044f \u0438\u043d\u0444\u043e\u0440\u043c\u0430\u0446\u0438\u044f.</b></font> \u041f\u043e\u0435\u0434\u0438\u043d\u043e\u043a \u0437\u0430\u0432\u0435\u0440\u0448\u0451\u043d.");
        if (!battleXp.isEmpty()) {
            synthetic.append(" \u041f\u043e\u043b\u0443\u0447\u0435\u043d\u043e <font color=#CC0000>\u0431\u043e\u0435\u0432\u043e\u0433\u043e</font> \u043e\u043f\u044b\u0442\u0430: <b><font color=#CC0000>")
                    .append(battleXp)
                    .append("</font></b>.");
        }
        String filteredMessage;
        try {
            filteredMessage = ru.neverlands.abclient.utils.ChatFilter.filter(synthetic.toString());
        } catch (Exception e) {
            AppLog.e(TAG, "publishFightSummaryFromFinishHtmlIfNeeded: ChatFilter failed", e);
            filteredMessage = synthetic.toString();
        }
        if (filteredMessage == null || filteredMessage.isEmpty()) {
            filteredMessage = synthetic.toString();
        }
        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", filteredMessage);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
        AppLog.d(TAG, "publishFightSummaryFromFinishHtmlIfNeeded: viaChatFilter logId=" + logId
                + ", battleXp=" + battleXp + ", foes=" + foes + ", damage=" + AppVars.LastBoiUron);
    }

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
            String msg = "primeLastBoiDamageFromFinishHtmlIfNeeded: list missing, logId=" + logId;
            AppLog.d(TAG, msg);
            return;
        }
        int damage = 0;
        for (int idx = 6; idx <= 10; idx++) {
            damage += parseIntFromJsToken(list[idx], 0);
        }
        AppVars.LastBoiUron = String.valueOf(Math.max(0, damage));
        AppLog.d(TAG, "primeLastBoiDamageFromFinishHtmlIfNeeded: logId=" + logId
                + ", damage=" + AppVars.LastBoiUron);
    }

    static String extractBattleXpFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String xp = HelperStrings.subString(
                html,
                "\u041f\u043e\u043b\u0443\u0447\u0435\u043d\u043e <font color=#CC0000>\u0431\u043e\u0435\u0432\u043e\u0433\u043e</font> \u043e\u043f\u044b\u0442\u0430: <b><font color=#CC0000>",
                "</font></b>.");
        if (xp != null && !xp.trim().isEmpty()) {
            String normalized = xp.replaceAll("[^0-9]", "");
            return normalized == null ? "" : normalized.trim();
        }
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "\u0431\u043e\u0435\u0432\u043e\u0433\u043e</font>\\s*\u043e\u043f\u044b\u0442\u0430:\\s*<b><font[^>]*>(\\d+)</font></b>",
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

    static void notifyNewFight(LezFight fight) {
        if (AppVars.getContext() == null) return;
        String foeType;
        boolean isDangerous = fight.IsDangerousFoe();
        if (isDangerous) {
            foeType = " \u26a0 \u041e\u041f\u0410\u0421\u041d\u042b\u0419";
        } else if (fight.IsBoss()) {
            foeType = " [\u0411\u041e\u0421\u0421]";
        } else {
            foeType = "";
        }
        String foes = (AppVars.LastBoiSostav != null && !AppVars.LastBoiSostav.isEmpty())
                ? AppVars.LastBoiSostav
                : (fight.FoeName + " [" + fight.FoeLevel + "]");
        String timeHtml = buildServerChatTimeHtml();
        String messageHtml =
                timeHtml +
                "<b><font color=#cc0000>\u041d\u0430\u043f\u0430\u0434\u0435\u043d\u0438\u0435:</font></b> " +
                "<font color=#004bbb>" + foes + "</font>" +
                foeType;
        String msg = "notifyNewFight: " + foes;
        AppLog.d(TAG, msg);
        AppVars.LastFightAnnounceAtMs = System.currentTimeMillis();
        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", messageHtml);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
    }

    public static void notifyNewFightFromExternalSource(LezFight fight, String html) {
        if (fight == null) {
            return;
        }
        notifyNewFight(fight);
        if (html != null && !html.isEmpty()) {
            UnderAttackManager.parseAsync(html);
        }
    }

    static void notifyFightStopped(LezFight fight) {
        if (AppVars.getContext() == null) return;
        String message = "\u0410\u0432\u0442\u043e\u0431\u043e\u0439 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d: ";
        if (fight.DoStop) {
            message += "\u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043a\u0430 \u0432 \u0433\u0440\u0443\u043f\u043f\u0435; ";
        }
        if (fight.IsLowHp) {
            message += "\u043d\u0438\u0437\u043a\u043e\u0435 HP; ";
        }
        if (fight.IsLowMa) {
            message += "\u043d\u0438\u0437\u043a\u0430\u044f \u043c\u0430\u043d\u0430; ";
        }
        if (fight.DoExit) {
            message += "\u0432\u044b\u0445\u043e\u0434 \u0438\u0437 \u0431\u043e\u044f; ";
        }
        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        msgIntent.putExtra("message", "<font color=#cc0000><b>" + message + "</b></font>");
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
    }

    static String buildServerChatTimeHtml() {
        long serverMs = System.currentTimeMillis();
        if (AppVars.Profile != null && AppVars.Profile.ServDiff != Long.MIN_VALUE) {
            serverMs = serverMs - AppVars.Profile.ServDiff;
        }
        Date serverTime = new Date(serverMs);
        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(serverTime);
        return "<font class=chattime>&nbsp;" + timeStr + "&nbsp;</font> ";
    }

    static String mainPhpFightEnd(String address, String html) {
        String msg_main = "mainPhpFightEnd: processing fight end page";
        AppLog.d(TAG, msg_main);
        if (address.contains("fexp=") && address.contains("act=7")) {
            String msg_fexp = "mainPhpFightEnd: has fexp, building redirect";
            AppLog.d(TAG, msg_fexp);
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
            if (html.contains("error.css") || html.contains("\u041e\u0448\u0438\u0431\u043a\u0430") || html.contains("error")) {
                String msg_error = "mainPhpFightEnd: server returned error page, returning original HTML";
                AppLog.d(TAG, msg_error);
                return html;
            }
            if (html.contains("<form") && html.contains("act=7")) {
                String msg_form = "mainPhpFightEnd: found form in HTML, auto-submitting";
                AppLog.d(TAG, msg_form);
                return html;
            }
            String msg_redirect = "mainPhpFightEnd: building redirect for fight end";
            AppLog.d(TAG, msg_redirect);
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
            return Russian.getString(Filter.buildRedirect("\u0417\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0438\u0435 \u0431\u043e\u044f", redirectUrl));
        }
        String msg_nofexp = "mainPhpFightEnd: no fexp in URL, returning original HTML";
        AppLog.d(TAG, msg_nofexp);
        return html;
    }
}
