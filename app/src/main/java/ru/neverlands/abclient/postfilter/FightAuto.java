package ru.neverlands.abclient.postfilter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Locale;
import java.util.Random;

import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.manager.UnderAttackManager;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.utils.AppVars;
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

    // Служебный таймштамп последнего auto-finish редиректа.
    private static volatile long lastAutoFinishRedirectAtMs = 0L;
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
        android.util.Log.d(TAG, "processFight: address=" + address + ", htmlLen=" + html.length());
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
            android.util.Log.d(TAG, "processFight: HTML dump, total=" + totalLen + " bytes, chunks=" + chunks);
            for (int i = 0; i < chunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, totalLen);
                android.util.Log.d(TAG, "processFight HTML[" + start + "-" + end + "]: "
                        + html.substring(start, end));
            }
        }

        android.util.Log.d(TAG, "processFight: LezFight parsed:"
                + " IsValid=" + fight.IsValid
                + " IsBoi=" + fight.IsBoi
                + " IsWaitingForNextTurn=" + fight.IsWaitingForNextTurn
                + " DoStop=" + fight.DoStop
                + " IsLowHp=" + fight.IsLowHp
                + " IsLowMa=" + fight.IsLowMa
                + " DoExit=" + fight.DoExit
                + " LogBoi=" + fight.LogBoi);
        if (!fight.IsValid) {
            android.util.Log.d(TAG, "processFight: fight.IsValid=false, returning original HTML");
            return html;
        }

        if (fight.IsBoi) {
            AppVars.LastFightPulseAtMs = System.currentTimeMillis();
            host.clearAutoFightProbeFinishCandidate();
        }

        final boolean autoFightProbeAddress = host.isAutoFightProbeAddress(address);
        final FightFinishPageMarkers finishMarkers = inspectFightFinishPageMarkers(html, host);
        final String resolvedFightCaptchaUrl = host.resolveFightCaptchaUrl(html);
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
            android.util.Log.d(TAG, "processFight: probe transitional inactive frame detected, postpone finish flow"
                    + ", address=" + address
                    + ", logBoi=" + fight.LogBoi);
        }

        boolean fightEnded = !fight.IsBoi && !fight.IsWaitingForNextTurn && !isProbeTransitionalInactiveFrame;
        if (fightEnded) {
            host.registerFightEnd(fight);
            host.publishFightResultFromLogsIfNeeded(html, address, fight.LogBoi);
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
                android.util.Log.d(TAG, "processFight: Timeout finished on fight end -> AutoboiOn");
            }
            if (AppVars.Autoboi == AutoboiState.Restoring) {
                boolean logChanged = fight.LogBoi != null && !fight.LogBoi.equals(AppVars.AutoboiReadyLog);
                boolean timerReady = AppVars.AutoboiReadyAtMs > 0L && now >= AppVars.AutoboiReadyAtMs;
                if (!logChanged && !timerReady) {
                    long waitMs = AppVars.AutoboiReadyAtMs > now ? (AppVars.AutoboiReadyAtMs - now) : 1200L;
                    int delay = (int) Math.max(1000L, Math.min(5000L, waitMs));
                    android.util.Log.d(TAG, "processFight: restoring in progress, waitMs=" + waitMs);
                    int curHp = insHpSnapshot != null ? insHpSnapshot.curHp : fight.getCurrentHp();
                    int maxHp = insHpSnapshot != null ? insHpSnapshot.maxHp : fight.getMaxHp();
                    int curMa = insHpSnapshot != null ? insHpSnapshot.curMa : fight.getCurrentMa();
                    int maxMa = insHpSnapshot != null ? insHpSnapshot.maxMa : fight.getMaxMa();
                    return host.buildRestoringStatusHtml(
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
                    android.util.Log.d(TAG, "processFight: restoring timer elapsed, mark completed for log=" + fight.LogBoi);
                }
                AppVars.AutoboiReadyAtMs = 0L;
                AppVars.AutoboiReadyLog = "";
                AppVars.Autoboi = AutoboiState.AutoboiOn;
                android.util.Log.d(TAG, "processFight: restoring finished -> AutoboiOn");
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
                        android.util.Log.d(TAG, "processFight: set Restoring until " + AppVars.AutoboiReadyAtMs);
                        long waitMs = Math.max(0L, AppVars.AutoboiReadyAtMs - now);
                        int delay = (int) Math.max(1000L, Math.min(5000L, waitMs > 0L ? waitMs : 1200L));
                        int curHp = insHpSnapshot != null ? insHpSnapshot.curHp : fight.getCurrentHp();
                        int maxHp = insHpSnapshot != null ? insHpSnapshot.maxHp : fight.getMaxHp();
                        int curMa = insHpSnapshot != null ? insHpSnapshot.curMa : fight.getCurrentMa();
                        int maxMa = insHpSnapshot != null ? insHpSnapshot.maxMa : fight.getMaxMa();
                        return host.buildRestoringStatusHtml(
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
                    android.util.Log.d(TAG, "processFight: restoring already completed for current log, continue to finish");
                }
                AppVars.AutoboiReadyAtMs = 0L;
                AppVars.AutoboiReadyLog = "";
            }
        }

        if (fight.IsBoi && fight.LogBoi != null && !fight.LogBoi.isEmpty()
                && !fight.LogBoi.equals(AppVars.LastBoiLog)) {
            android.util.Log.d(TAG, "processFight: NEW FIGHT detected! LogBoi changed: "
                    + AppVars.LastBoiLog + " -> " + fight.LogBoi);
            AppVars.LastBoiLog = fight.LogBoi;
            AppVars.LastBoiUron = "";
            lastAutoSkinProbeFightLog = "";
            AppVars.AutoboiReadyCompletedLog = "";
            fight.updateLastBoiFromLogs();
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
            android.util.Log.d(TAG, "AUTO_SKIN_TRACE processFight: queue AutoSkinCheckRes=true after get_id=17"
                    + ", logBoi=" + fight.LogBoi);
        }
        if (fightEnded && host.isAutoSkinEnabledByPreference()) {
            boolean alreadyOnRazAddress = address != null && address.contains("get_id=17");
            if (!alreadyOnRazAddress) {
                String razHtml = host.mainPhpRaz(html);
                if (razHtml != null) {
                    android.util.Log.d(TAG, "AUTO_SKIN_TRACE processFight: fight ended, run raz before finish");
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
                    android.util.Log.d(TAG, "AUTO_SKIN_TRACE processFight: raz probe redirect to " + probeUrl
                            + ", sourceAddress=" + address);
                    return host.buildDelayedRedirectHtml("Проверка разделки", probeUrl, 260);
                }
            }
        }

        if (fightEnded
                && autoFightEnabled
                && AppVars.Autoboi == AutoboiState.AutoboiOn) {
            android.util.Log.d(TAG, "processFight: FIGHT ENDED with autoboi ON - processing finish");
            String captchaUrl = fightCaptchaUrl;
            boolean needCaptcha = captchaUrl != null && !captchaUrl.isEmpty();
            String fightLink = AppVars.FightLink;
            if (fightLink == null || fightLink.isEmpty()) {
                String recoveredFightLink = host.extractFightFinishLinkFromHtml(html, needCaptcha);
                if (recoveredFightLink != null && !recoveredFightLink.isEmpty()) {
                    fightLink = recoveredFightLink;
                    AppVars.FightLink = recoveredFightLink;
                    android.util.Log.d(TAG, "processFight: recovered finish link from html: " + recoveredFightLink);
                }
            }
            if (!needCaptcha) {
                String cleanFinishLink = host.extractFightCleanFinishLinkFromHtml(html);
                if (cleanFinishLink != null && !cleanFinishLink.isEmpty()) {
                    boolean replacedPrevious = fightLink != null
                            && !fightLink.isEmpty()
                            && !cleanFinishLink.equals(fightLink);
                    fightLink = cleanFinishLink;
                    AppVars.FightLink = cleanFinishLink;
                    android.util.Log.d(TAG, "processFight: recovered CLEAN finish link from html: "
                            + cleanFinishLink + (replacedPrevious ? " (override previous fightLink)" : ""));
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
                    && fightLink != null
                    && !fightLink.isEmpty()
                    && !fightLink.contains("????")
                    && !host.isAutoFightProbeFinishConfirmed(fight.LogBoi, fightLink)) {
                decision = FinishFlowDecision.KEEP_ORIGINAL_HTML;
                decisionReason = "probe_finish_needs_confirmation";
                android.util.Log.d(TAG, "processFight: defer direct finish on probe frame, waiting confirmation"
                        + ", address=" + address
                        + ", logBoi=" + fight.LogBoi
                        + ", fightLink=" + fightLink);
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
                android.util.Log.d(TAG, "processFight: CAPTCHA required, stopping autoboi and showing dialog: " + captchaUrl);
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
                int redirectDelay = AUTO_FINISH_MIN_DELAY_MS + RANDOM.nextInt(AUTO_FINISH_EXTRA_DELAY_MS + 1);
                if (redirectDelay >= 0) {
                    lastAutoFinishRedirectAtMs = now;
                    AppVars.FightLink = "";
                    return host.buildDelayedRedirectHtml("Завершение боя", fightLink, redirectDelay);
                }
                AppVars.FightLink = "";
                return Russian.getString(Filter.buildRedirect(" ", fightLink));
            }
            if (decision == FinishFlowDecision.FEND_AUTOSUBMIT_ALLOWED && finishFormSubmitHtml != null) {
                android.util.Log.d(TAG, "processFight: FightLink missing, auto-submit FEND form");
                AppVars.FightLink = "";
                return finishFormSubmitHtml;
            }
            android.util.Log.d(TAG, "processFight: FightLink missing and FEND not parsed, keep original fight HTML");
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
                android.util.Log.d(TAG, "processFight: manual mode CAPTCHA detected, showing dialog: " + manualCaptchaUrl);
                boolean fromCaptchaSubmit = address != null && address.contains("code=");
                if (fromCaptchaSubmit) {
                    String submittedCode = host.getUrlParam(address, "code");
                    String submittedVcode = host.getUrlParam(address, "vcode");
                    android.util.Log.d(TAG, "processFight: captcha submit still requires challenge, code="
                            + submittedCode + ", vcode=" + submittedVcode);
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
            android.util.Log.d(TAG, "processFight: waiting for opponent turn (foe HP=" + fight.FoeCurrentHp + ")");
            boolean shouldAutoRefresh = AppVars.AutoRefresh;
            if (!shouldAutoRefresh && autoFightEnabled
                    && AppVars.Autoboi == AutoboiState.AutoboiOn) {
                shouldAutoRefresh = true;
            }
            if (shouldAutoRefresh) {
                int delay = 1200 + RANDOM.nextInt(900);
                android.util.Log.d(TAG, "processFight: auto-refresh waiting enabled, reloading after " + delay + "ms: " + address);
                return host.buildInPlaceFightAutoRefreshHtml(html, address, delay);
            }
            android.util.Log.d(TAG, "processFight: AutoRefresh disabled, returning original content");
            return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
        }

        if (autoFightEnabled) {
            android.util.Log.d(TAG, "processFight: LezDoAutoboi enabled, Autoboi state=" + AppVars.Autoboi);
            if (AppVars.Autoboi == AutoboiState.AutoboiOn) {
                if (fight.IsBoi) {
                    android.util.Log.d(TAG, "processFight: in fight, checking safety conditions:"
                            + " DoStop=" + fight.DoStop
                            + " IsLowHp=" + fight.IsLowHp
                            + " IsLowMa=" + fight.IsLowMa
                            + " DoExit=" + fight.DoExit);

                    if (!fight.DoStop && !fight.IsLowHp && !fight.IsLowMa && !fight.DoExit) {
                        android.util.Log.d(TAG, "processFight: SAFE - returning fight.Frame for auto-attack");
                        android.util.Log.d(TAG, "processFight: fight.Frame = " + (fight.Frame != null ? fight.Frame.substring(0, Math.min(200, fight.Frame.length())) : "NULL"));
                        if (fight.Frame != null && !fight.Frame.isEmpty()) {
                            return fight.Frame;
                        }
                        android.util.Log.w(TAG, "processFight: fight.Frame is empty, stopping autoboi to avoid null flow");
                        if (AppVars.Autoboi != AutoboiState.Timeout) {
                            host.notifyFightStopped(fight);
                            AppVars.Autoboi = AutoboiState.Timeout;
                        }
                        return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
                    } else {
                        android.util.Log.d(TAG, "processFight: DANGEROUS - stopping autoboi, setting Timeout");
                        if (AppVars.Autoboi != AutoboiState.Timeout) {
                            host.notifyFightStopped(fight);
                            AppVars.Autoboi = AutoboiState.Timeout;
                        }
                    }
                } else {
                    android.util.Log.d(TAG, "processFight: fight ended branch already handled, keep current frame");
                }
            } else {
                android.util.Log.d(TAG, "processFight: Autoboi state is " + AppVars.Autoboi + ", not AutoboiOn");
            }
        } else {
            android.util.Log.d(TAG, "processFight: auto-fight disabled for this frame"
                    + " pref=" + autoFightEnabledByPreference
                    + ", runtimeState=" + AppVars.Autoboi);
            if (!fight.IsBoi) {
                android.util.Log.d(TAG, "processFight: autoboi disabled, keeping original fight frame for manual finish");
            }
        }

        android.util.Log.d(TAG, "processFight flags:"
                + " magic_slots=" + html.contains("magic_slots();")
                + " fight_ty=" + html.contains("var fight_ty")
                + " IsBoi_form=" + html.contains("<form")
                + " StartAct=" + html.contains("StartAct()")
                + " document.ff=" + html.contains("document.ff")
                + " autosubmit=" + html.contains("document.ff.submit")
        );
        return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
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
            android.util.Log.e(TAG, "buildFightEndFormSubmitHtml error", e);
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
            android.util.Log.e(TAG, "inspectFightFinishPageMarkers error", e);
        }
        return markers;
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
        android.util.Log.d(TAG, "processFight finishFlow:"
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
}
