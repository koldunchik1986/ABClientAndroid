package ru.neverlands.abclient.postfilter;

import android.util.Log;
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
import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.HtmlUtils;
import ru.neverlands.abclient.utils.Russian;

/**
 * РњРѕРґСѓР»СЊ Р±РѕРµРІРѕРіРѕ post-filter, РІС‹РґРµР»РµРЅРЅС‹Р№ РёР· {@link MainPhp}.
 *
 * РќР°Р·РЅР°С‡РµРЅРёРµ:
 * - С†РµРЅС‚СЂР°Р»РёР·РѕРІР°С‚СЊ РІСЃСЋ Р»РѕРіРёРєСѓ Auto-Р‘РѕСЏ РІ РѕРґРЅРѕРј РєР»Р°СЃСЃРµ;
 * - СѓР±СЂР°С‚СЊ РїРµСЂРµРіСЂСѓР·РєСѓ MainPhp Рё СЃРѕРєСЂР°С‚РёС‚СЊ СЂРёСЃРє СЂРµРіСЂРµСЃСЃРёР№ РїСЂРё С„РёРєСЃРµ Р±РѕСЏ;
 * - СЃРѕС…СЂР°РЅРёС‚СЊ parity РїРѕРІРµРґРµРЅРёСЏ СЃ СѓР¶Рµ СЂР°Р±РѕС‚Р°СЋС‰РµР№ Android-Р»РѕРіРёРєРѕР№ (Р±РµР· РёР·РјРµРЅРµРЅРёСЏ Р°Р»РіРѕСЂРёС‚РјРѕРІ).
 *
 * Р“СЂР°РЅРёС†С‹ РѕС‚РІРµС‚СЃС‚РІРµРЅРЅРѕСЃС‚Рё:
 * - СЌС‚РѕС‚ РєР»Р°СЃСЃ СѓРїСЂР°РІР»СЏРµС‚ С‚РѕР»СЊРєРѕ runtime-РїРѕС‚РѕРєРѕРј Р±РѕРµРІРѕРіРѕ РєР°РґСЂР°/finish-flow;
 * - РёРЅС„СЂР°СЃС‚СЂСѓРєС‚СѓСЂРЅС‹Рµ РѕРїРµСЂР°С†РёРё (С‡Р°С‚С‹, popup, bridge-redirect, РёР·РІР»РµС‡РµРЅРёРµ URL) РґРµР»РµРіРёСЂСѓСЋС‚СЃСЏ С‡РµСЂРµР· {@link Host};
 * - РіР»РѕР±Р°Р»СЊРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ РїРѕ-РїСЂРµР¶РЅРµРјСѓ Р¶РёРІС‘С‚ РІ {@link AppVars}, РєР°Рє Рё Р±С‹Р»Рѕ РґРѕ РІС‹РЅРѕСЃР°.
 */
public final class FightAuto {
    private static final String TAG = "FightAuto";
    private static final Random RANDOM = new Random();
    // Р‘Р°Р·РѕРІР°СЏ Р·Р°РґРµСЂР¶РєР° РґР»СЏ auto-finish redirect (РѕРіСЂР°РЅРёС‡РµРЅРёРµ С‡Р°СЃС‚РѕС‚С‹ С„РёРЅР°Р»СЊРЅС‹С… РєР»РёРєРѕРІ).
    private static final int AUTO_FINISH_MIN_DELAY_MS = 1000;
    // РЎР»СѓС‡Р°Р№РЅР°СЏ РґРѕР±Р°РІРєР° Рє Р·Р°РґРµСЂР¶РєРµ (РјРёРєСЂРѕ-РґР¶РёС‚С‚РµСЂ, С‡С‚РѕР±С‹ РЅРµ Р±РѕРјР±РёС‚СЊ РѕРґРёРЅР°РєРѕРІС‹Рј РёРЅС‚РµСЂРІР°Р»РѕРј).
    private static final int AUTO_FINISH_EXTRA_DELAY_MS = 700;

    // РЎР»СѓР¶РµР±РЅС‹Р№ С‚Р°Р№РјС€С‚Р°РјРї РїРѕСЃР»РµРґРЅРµРіРѕ auto-finish СЂРµРґРёСЂРµРєС‚Р°.
    private static volatile long lastAutoFinishRedirectAtMs = 0L;
    // Р”РµРґСѓРї РєР»СЋС‡ probe-С€Р°РіР° Р°РІС‚Рѕ-СЂР°Р·РґРµР»РєРё РїРѕСЃР»Рµ Р±РѕСЏ (РЅР° СѓСЂРѕРІРЅРµ РєРѕРЅРєСЂРµС‚РЅРѕРіРѕ LogBoi).
    private static volatile String lastAutoSkinProbeFightLog = "";

    private FightAuto() {
    }

    /**
     * РњРёРЅРёРјР°Р»СЊРЅС‹Р№ СЃРЅРёРјРѕРє HP/MA, РґРѕСЃС‚Р°С‚РѕС‡РЅС‹Р№ РґР»СЏ Restoring-СЌРєСЂР°РЅР° Рё РїРѕСЂРѕРіРѕРІРѕР№ Р»РѕРіРёРєРё.
     *
     * РСЃС‚РѕС‡РЅРёРє:
     * - РїР°СЂСЃРёРЅРі `ins_HP(...)` РЅР° СЃС‚РѕСЂРѕРЅРµ MainPhp С‡РµСЂРµР· bridge-РјРµС‚РѕРґ {@link Host#parseInsHpSnapshot(String)}.
     */
    public static final class InsHpSnapshot {
        public int curHp;
        public int maxHp;
        public int curMa;
        public int maxMa;
    }

    /**
     * РЇРІРЅР°СЏ РјРѕРґРµР»СЊ РІС‹Р±РѕСЂР° РІРµС‚РєРё Р·Р°РІРµСЂС€РµРЅРёСЏ Р±РѕСЏ.
     *
     * РќСѓР¶РЅР° РґР»СЏ:
     * - РїСЂРµРґСЃРєР°Р·СѓРµРјРѕР№ РґРёР°РіРЅРѕСЃС‚РёРєРё (РІРјРµСЃС‚Рѕ РЅРµСЏРІРЅРѕРіРѕ РєР°СЃРєР°РґР° if/return);
     * - СЃС‚Р°Р±РёР»СЊРЅРѕРіРѕ РїРѕСЃС‚-Р°РЅР°Р»РёР·Р° РІ Р»РѕРіР°С… С‡РµСЂРµР· {@link #logFinishFlowDecision(FinishFlowDecision, LezFight, String, String, String, FightFinishPageMarkers, String)}.
     */
    private enum FinishFlowDecision {
        DIRECT_FINISH_LINK,
        FEND_AUTOSUBMIT_ALLOWED,
        CAPTCHA_REQUIRED,
        KEEP_ORIGINAL_HTML
    }

    /**
     * РљРѕРјРїР°РєС‚РЅС‹Р№ РЅР°Р±РѕСЂ РјР°СЂРєРµСЂРѕРІ finish-СЃС‚СЂР°РЅРёС†С‹.
     *
     * РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РєР°Рє РґРёР°РіРЅРѕСЃС‚РёС‡РµСЃРєР°СЏ/СЂРµС€Р°СЋС‰Р°СЏ СЃС‚СЂСѓРєС‚СѓСЂР° РІРЅСѓС‚СЂРё finish-flow:
     * - РЅР°Р»РёС‡РёРµ FEND-С„РѕСЂРјС‹ Рё РїРѕР»СЏ `code`;
     * - РїСЂРёР·РЅР°РєРё fkey/captcha;
     * - СЃР»СѓР¶РµР±РЅС‹Рµ С‚РѕРєРµРЅС‹ РёР· `fight_ty`/`fexp`.
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
     * Bridge РІ РёРЅС„СЂР°СЃС‚СЂСѓРєС‚СѓСЂСѓ MainPhp.
     *
     * РџСЂР°РІРёР»Рѕ:
     * - FightAuto РЅРµ РґСѓР±Р»РёСЂСѓРµС‚ СѓР¶Рµ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёРµ helper-РјРµС‚РѕРґС‹, Р° РёСЃРїРѕР»СЊР·СѓРµС‚ РґРµР»РµРіРёСЂРѕРІР°РЅРёРµ;
     * - Р»СЋР±С‹Рµ РѕРїРµСЂР°С†РёРё, Р·Р°РІСЏР·Р°РЅРЅС‹Рµ РЅР° РІРЅРµС€РЅРёР№ РєРѕРЅС‚РµРєСЃС‚ (С‡Р°С‚С‹, popup, URL-СѓС‚РёР»РёС‚С‹, РїР°СЂСЃРµСЂС‹ MainPhp),
     *   РґРѕР»Р¶РЅС‹ РїСЂРёС…РѕРґРёС‚СЊ С‡РµСЂРµР· Host.
     *
     * Р­С‚Рѕ РїРѕР·РІРѕР»СЏРµС‚ РјРµРЅСЏС‚СЊ Р±РѕРµРІСѓСЋ Р»РѕРіРёРєСѓ РІ РѕРґРЅРѕРј РјРµСЃС‚Рµ, РЅРµ Р»РѕРјР°СЏ РѕСЃС‚Р°Р»СЊРЅРѕР№ pipeline MainPhp.
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
     * РћСЃРЅРѕРІРЅР°СЏ С‚РѕС‡РєР° РѕР±СЂР°Р±РѕС‚РєРё Р±РѕРµРІРѕРіРѕ РєР°РґСЂР°.
     *
     * РџРѕС‚РѕРє СЂРµС€РµРЅРёСЏ:
     * 1) РџР°СЂСЃРёРЅРі LezFight + СЃРЅРёРјРєР° HP/MA.
     * 2) Р”РµС‚РµРєС‚ С‚РµРєСѓС‰РµР№ С„Р°Р·С‹: Р°РєС‚РёРІРЅС‹Р№ Р±РѕР№ / РѕР¶РёРґР°РЅРёРµ / Р·Р°РІРµСЂС€РµРЅРёРµ.
     * 3) РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ runtime-СЃРѕСЃС‚РѕСЏРЅРёР№ AutoBoi (Timeout/Restoring/On).
     * 4) Finish-flow (FightLink/FEND/CAPTCHA/manual).
     * 5) Р’РѕР·РІСЂР°С‚ РєР°РґСЂР° Р°РІС‚Рѕ-СѓРґР°СЂР° Р»РёР±Рѕ РёСЃС…РѕРґРЅРѕРіРѕ HTML РІ СЃРѕРѕС‚РІРµС‚СЃС‚РІРёРё СЃ С‚РµРєСѓС‰РёРјРё С„Р»Р°РіР°РјРё.
     *
     * Р—Р°РІРёСЃРёРјРѕСЃС‚Рё:
     * - {@link LezFight} РєР°Рє РёСЃС‚РѕС‡РЅРёРє Р±РѕРµРІС‹С… С„Р»Р°РіРѕРІ Рё frame-HTML;
     * - {@link AppVars} РєР°Рє РµРґРёРЅРѕРµ runtime-С…СЂР°РЅРёР»РёС‰Рµ;
     * - {@link Host} РґР»СЏ РІСЃРµС… РІРЅРµС€РЅРёС… helper-РѕРїРµСЂР°С†РёР№ MainPhp.
     *
     * Р’Р°Р¶РЅРѕ:
     * - РјРµС‚РѕРґ intentionally СЃРѕС…СЂР°РЅСЏРµС‚ РїСЂРµР¶РЅСЋСЋ Р»РѕРіРёРєСѓ РІРµС‚РІР»РµРЅРёР№;
     * - Р»СЋР±С‹Рµ РёР·РјРµРЅРµРЅРёСЏ Р·РґРµСЃСЊ РґРѕР»Р¶РЅС‹ Р±С‹С‚СЊ С‚РѕР»СЊРєРѕ РѕСЃРѕР·РЅР°РЅРЅС‹РјРё, СЃ РѕР±СЏР·Р°С‚РµР»СЊРЅРѕР№ РїСЂРѕРІРµСЂРєРѕР№ Р»РѕРіРѕРІ Р±РѕСЏ.
     */
    public static String processFight(String address, String html, Host host) {
        if (host == null || html == null) {
            return html;
        }
        String msg1 = "processFight: address=" + address + ", htmlLen=" + html.length();
        Log.d(TAG, msg1);
        FileLogger.trace(TAG, msg1);
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
            Log.d(TAG, msg2);
            FileLogger.trace(TAG, msg2);
            for (int i = 0; i < chunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, totalLen);
                String msg3 = "processFight HTML[" + start + "-" + end + "]: "
                        + html.substring(start, end);
                Log.d(TAG, msg3);
                FileLogger.trace(TAG, msg3);
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
        Log.d(TAG, msg4);
        FileLogger.trace(TAG, msg4);
        if (!fight.IsValid) {
            String msg = "processFight: fight.IsValid=false, returning original HTML";
            Log.d(TAG, msg);
            FileLogger.trace(TAG, msg);
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
            String msg = "processFight: probe transitional inactive frame detected, postpone finish flow"
                    + ", address=" + address
                    + ", logBoi=" + fight.LogBoi;
            Log.d(TAG, msg);
            FileLogger.trace(TAG, msg);
        }

        boolean fightEnded = !fight.IsBoi && !fight.IsWaitingForNextTurn && !isProbeTransitionalInactiveFrame;
        if (fightEnded) {
            host.registerFightEnd(fight);
            host.publishFightResultFromLogsIfNeeded(html, address, fight.LogBoi);
            // рџЋЇ Р‘РѕР№ Р·Р°РєРѕРЅС‡РёР»СЃСЏ - SessionManager РјРѕР¶РµС‚ РІРµСЂРЅСѓС‚СЊСЃСЏ РЅР° РѕР±С‹С‡РЅС‹Р№ 5-РјРёРЅСѓС‚РЅС‹Р№ timeout
            ru.neverlands.abclient.utils.SessionManager.getInstance().clearFightContext();            
            // ✅ КРИТИЧНОЕ ЛОГИРОВАНИЕ: Очистить FastNeed когда бой завершился
            if (AppVars.FastNeed) {
                String msg = "[FIGHT_ENDED_CLEANUP] fight ended, clearing FastNeed to allow auto-fishing resume"
                        + ", logBoi=" + fight.LogBoi
                        + ", oldFastNeed=true"
                        + ", oldFastId='" + AppVars.FastId + "'";
                Log.i(TAG, msg);
                FileLogger.trace(TAG, msg);
                
                ru.neverlands.abclient.manager.FastActionManager.fastCancel("fight_ended");
                
                String cancelMsg = "[FIGHT_ENDED_CLEANUP_COMPLETED] FastNeed cleared after fight end";
                Log.i(TAG, cancelMsg);
                FileLogger.trace(TAG, cancelMsg);
            }        }
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
                Log.d(TAG, msg_timeout);
                FileLogger.trace(TAG, msg_timeout);
            }
            if (AppVars.Autoboi == AutoboiState.Restoring) {
                boolean logChanged = fight.LogBoi != null && !fight.LogBoi.equals(AppVars.AutoboiReadyLog);
                boolean timerReady = AppVars.AutoboiReadyAtMs > 0L && now >= AppVars.AutoboiReadyAtMs;
                if (!logChanged && !timerReady) {
                    long waitMs = AppVars.AutoboiReadyAtMs > now ? (AppVars.AutoboiReadyAtMs - now) : 1200L;
                    int delay = (int) Math.max(1000L, Math.min(5000L, waitMs));
                    String msg_restoring_inprogress = "processFight: restoring in progress, waitMs=" + waitMs;
                    Log.d(TAG, msg_restoring_inprogress);
                    FileLogger.trace(TAG, msg_restoring_inprogress);
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
                    String msg = "processFight: restoring timer elapsed, mark completed for log=" + fight.LogBoi;
                    Log.d(TAG, msg);
                    FileLogger.trace(TAG, msg);
                }
                AppVars.AutoboiReadyAtMs = 0L;
                AppVars.AutoboiReadyLog = "";
                AppVars.Autoboi = AutoboiState.AutoboiOn;
                String msg = "processFight: restoring finished -> AutoboiOn";
                Log.d(TAG, msg);
                FileLogger.trace(TAG, msg);
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
                        String msg_set_restoring = "processFight: set Restoring until " + AppVars.AutoboiReadyAtMs;
                        Log.d(TAG, msg_set_restoring);
                        FileLogger.trace(TAG, msg_set_restoring);
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
                    String msg = "processFight: restoring already completed for current log, continue to finish";
                Log.d(TAG, msg);
                FileLogger.trace(TAG, msg);
                }
                AppVars.AutoboiReadyAtMs = 0L;
                AppVars.AutoboiReadyLog = "";
            }
        }

        if (fight.IsBoi && fight.LogBoi != null && !fight.LogBoi.isEmpty()
                && !fight.LogBoi.equals(AppVars.LastBoiLog)) {
            String msg_new_fight = "processFight: NEW FIGHT detected! LogBoi changed: "
                    + AppVars.LastBoiLog + " -> " + fight.LogBoi;
            Log.d(TAG, msg_new_fight);
            FileLogger.trace(TAG, msg_new_fight);
            AppVars.LastBoiLog = fight.LogBoi;
            AppVars.LastBoiUron = "";
            lastAutoSkinProbeFightLog = "";
            AppVars.AutoboiReadyCompletedLog = "";
            fight.updateLastBoiFromLogs();
            // рџЋЇ РћС‚РјРµС‚РёС‚СЊ С‡С‚Рѕ РЅР°С‡Р°Р»СЃСЏ РЅРѕРІС‹Р№ Р±РѕР№ - SessionManager РґРѕР»Р¶РµРЅ РґРѕР»СЊС€Рµ С…СЂР°РЅРёС‚СЊ vcode
            ru.neverlands.abclient.utils.SessionManager.getInstance().markFightInProgress();
            host.notifyNewFight(fight);
            UnderAttackManager.parseAsync(html);
        }
        if (fightEnded
                && host.isAutoSkinEnabledByPreference()
                && address != null
                && address.contains("get_id=17")) {
            // Fallback C#-СЃРµРјР°РЅС‚РёРєРё AutoSkin:
            // РїРѕСЃР»Рµ РєР°РґСЂР° get_id=17 (СЂР°Р·РґРµР»РєР°) РІ Р»СЋР±РѕРј СЃР»СѓС‡Р°Рµ РїР»Р°РЅРёСЂСѓРµРј РїСЂРѕРІРµСЂРєСѓ СЂРµСЃСѓСЂСЃРѕРІ.
            // Р­С‚Рѕ РїРѕРєСЂС‹РІР°РµС‚ РєРµР№СЃС‹, РєРѕРіРґР° var logs РЅРµ СЃРѕРґРµСЂР¶РёС‚ [8,...], РЅРѕ СЂР°Р·РґРµР»РєР° СЃРµСЂРІРµСЂРѕРј СѓР¶Рµ РїСЂРёРјРµРЅРµРЅР°.
            AppVars.AutoSkinCheckRes = true;
            String msg_autoskin = "AUTO_SKIN_TRACE processFight: queue AutoSkinCheckRes=true after get_id=17"
                    + ", logBoi=" + fight.LogBoi;
            Log.d(TAG, msg_autoskin);
            FileLogger.trace(TAG, msg_autoskin);
        }
        if (fightEnded && host.isAutoSkinEnabledByPreference()) {
            boolean alreadyOnRazAddress = address != null && address.contains("get_id=17");
            if (!alreadyOnRazAddress) {
                String razHtml = host.mainPhpRaz(html);
                if (razHtml != null) {
                    String msg_raz_before_finish = "AUTO_SKIN_TRACE processFight: fight ended, run raz before finish";
                    Log.d(TAG, msg_raz_before_finish);
                    FileLogger.trace(TAG, msg_raz_before_finish);
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
                    Log.d(TAG, msg_raz_probe);
                    FileLogger.trace(TAG, msg_raz_probe);
                    return host.buildDelayedRedirectHtml("РџСЂРѕРІРµСЂРєР° СЂР°Р·РґРµР»РєРё", probeUrl, 260);
                }
            }
        }

        if (fightEnded
                && autoFightEnabled
                && AppVars.Autoboi == AutoboiState.AutoboiOn) {
            String msg_fight_ended = "processFight: FIGHT ENDED with autoboi ON - processing finish";
            Log.d(TAG, msg_fight_ended);
            FileLogger.trace(TAG, msg_fight_ended);
            String captchaUrl = fightCaptchaUrl;
            boolean needCaptcha = captchaUrl != null && !captchaUrl.isEmpty();
            String fightLink = AppVars.FightLink;
            if (fightLink == null || fightLink.isEmpty()) {
                String recoveredFightLink = host.extractFightFinishLinkFromHtml(html, needCaptcha);
                if (recoveredFightLink != null && !recoveredFightLink.isEmpty()) {
                    fightLink = recoveredFightLink;
                    AppVars.FightLink = recoveredFightLink;
                    String msg_recovered_link = "processFight: recovered finish link from html: " + recoveredFightLink;
                    Log.d(TAG, msg_recovered_link);
                    FileLogger.trace(TAG, msg_recovered_link);
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
                    String msg_clean_link = "processFight: recovered CLEAN finish link from html: "
                            + cleanFinishLink + (replacedPrevious ? " (override previous fightLink)" : "");
                    Log.d(TAG, msg_clean_link);
                    FileLogger.trace(TAG, msg_clean_link);
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
                String msg_probe_defer = "processFight: defer direct finish on probe frame, waiting confirmation"
                        + ", address=" + address
                        + ", logBoi=" + fight.LogBoi
                        + ", fightLink=" + fightLink;
                Log.d(TAG, msg_probe_defer);
                FileLogger.trace(TAG, msg_probe_defer);
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
                Log.d(TAG, msg_captcha_required);
                FileLogger.trace(TAG, msg_captcha_required);
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
                    return host.buildDelayedRedirectHtml("Р—Р°РІРµСЂС€РµРЅРёРµ Р±РѕСЏ", fightLink, redirectDelay);
                }
                AppVars.FightLink = "";
                return Russian.getString(Filter.buildRedirect(" ", fightLink));
            }
            if (decision == FinishFlowDecision.FEND_AUTOSUBMIT_ALLOWED && finishFormSubmitHtml != null) {
                String msg_fend_autosubmit = "processFight: FightLink missing, auto-submit FEND form";
                Log.d(TAG, msg_fend_autosubmit);
                FileLogger.trace(TAG, msg_fend_autosubmit);
                AppVars.FightLink = "";
                return finishFormSubmitHtml;
            }
            String msg_fend_missing = "processFight: FightLink missing and FEND not parsed, keep original fight HTML";
            Log.d(TAG, msg_fend_missing);
            FileLogger.trace(TAG, msg_fend_missing);
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
                Log.d(TAG, msg_manual_captcha);
                FileLogger.trace(TAG, msg_manual_captcha);
                boolean fromCaptchaSubmit = address != null && address.contains("code=");
                if (fromCaptchaSubmit) {
                    String submittedCode = host.getUrlParam(address, "code");
                    String submittedVcode = host.getUrlParam(address, "vcode");
                    String msg_captcha_submit = "processFight: captcha submit still requires challenge, code="
                            + submittedCode + ", vcode=" + submittedVcode;
                    Log.d(TAG, msg_captcha_submit);
                    FileLogger.trace(TAG, msg_captcha_submit);
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
            Log.d(TAG, msg);
            FileLogger.trace(TAG, msg);
            boolean shouldAutoRefresh = AppVars.AutoRefresh;
            if (!shouldAutoRefresh && autoFightEnabled
                    && AppVars.Autoboi == AutoboiState.AutoboiOn) {
                shouldAutoRefresh = true;
            }
            if (shouldAutoRefresh) {
                int delay = 1200 + RANDOM.nextInt(900);
                String msg_autorefresh = "processFight: auto-refresh waiting enabled, reloading after " + delay + "ms: " + address;
                Log.d(TAG, msg_autorefresh);
                FileLogger.trace(TAG, msg_autorefresh);
                return host.buildInPlaceFightAutoRefreshHtml(html, address, delay);
            }
            String msg_autorefresh_disabled = "processFight: AutoRefresh disabled, returning original content";
            Log.d(TAG, msg_autorefresh_disabled);
            FileLogger.trace(TAG, msg_autorefresh_disabled);
            return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
        }

        if (autoFightEnabled) {
            String msg_lezdoautoboi = "processFight: LezDoAutoboi enabled, Autoboi state=" + AppVars.Autoboi;
            Log.d(TAG, msg_lezdoautoboi);
            FileLogger.trace(TAG, msg_lezdoautoboi);
            if (AppVars.Autoboi == AutoboiState.AutoboiOn) {
                if (fight.IsBoi) {
                    String msg_safety_check = "processFight: in fight, checking safety conditions:"
                            + " DoStop=" + fight.DoStop
                            + " IsLowHp=" + fight.IsLowHp
                            + " IsLowMa=" + fight.IsLowMa
                            + " DoExit=" + fight.DoExit;
                    Log.d(TAG, msg_safety_check);
                    FileLogger.trace(TAG, msg_safety_check);

                    if (!fight.DoStop && !fight.IsLowHp && !fight.IsLowMa && !fight.DoExit) {
                        String msg_safe = "processFight: SAFE - returning fight.Frame for auto-attack";
                        Log.d(TAG, msg_safe);
                        FileLogger.trace(TAG, msg_safe);
                        String msg_frame = "processFight: fight.Frame = " + (fight.Frame != null ? fight.Frame.substring(0, Math.min(200, fight.Frame.length())) : "NULL");
                        Log.d(TAG, msg_frame);
                        FileLogger.trace(TAG, msg_frame);
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
                        String msg_dangerous = "processFight: DANGEROUS - stopping autoboi, setting Timeout";
                        Log.d(TAG, msg_dangerous);
                        FileLogger.trace(TAG, msg_dangerous);
                        if (AppVars.Autoboi != AutoboiState.Timeout) {
                            host.notifyFightStopped(fight);
                            AppVars.Autoboi = AutoboiState.Timeout;
                        }
                    }
                } else {
                    String msg_fight_ended_handled = "processFight: fight ended branch already handled, keep current frame";
                    Log.d(TAG, msg_fight_ended_handled);
                    FileLogger.trace(TAG, msg_fight_ended_handled);
                }
            } else {
                String msg_autoboi_state = "processFight: Autoboi state is " + AppVars.Autoboi + ", not AutoboiOn";
                Log.d(TAG, msg_autoboi_state);
                FileLogger.trace(TAG, msg_autoboi_state);
            }
        } else {
            String msg_autofight_disabled = "processFight: auto-fight disabled for this frame"
                    + " pref=" + autoFightEnabledByPreference
                    + ", runtimeState=" + AppVars.Autoboi;
            Log.d(TAG, msg_autofight_disabled);
            FileLogger.trace(TAG, msg_autofight_disabled);
            if (!fight.IsBoi) {
                String msg_autofight_disabled_manual = "processFight: autoboi disabled, keeping original fight frame for manual finish";
                Log.d(TAG, msg_autofight_disabled_manual);
                FileLogger.trace(TAG, msg_autofight_disabled_manual);
            }
        }

        String msg_flags = "processFight flags:"
                + " magic_slots=" + html.contains("magic_slots();")
                + " fight_ty=" + html.contains("var fight_ty")
                + " IsBoi_form=" + html.contains("<form")
                + " StartAct=" + html.contains("StartAct()")
                + " document.ff=" + html.contains("document.ff")
                + " autosubmit=" + html.contains("document.ff.submit");
        Log.d(TAG, msg_flags);
        FileLogger.trace(TAG, msg_flags);
        return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;
    }

    /**
     * РЎС‚СЂРѕРёС‚ auto-submit HTML РґР»СЏ FEND-С„РѕСЂРјС‹ Р·Р°РІРµСЂС€РµРЅРёСЏ Р±РѕСЏ.
     *
     * РџСЂР°РІРёР»Р°:
     * - РµСЃР»Рё СЃРµСЂРІРµСЂ С‚СЂРµР±СѓРµС‚ СЂСѓС‡РЅРѕР№ РєРѕРґ (`code` РїСѓСЃС‚РѕР№/`????`), Р°РІС‚Рѕ-submit Р·Р°РїСЂРµС‰С‘РЅ;
     * - СЃРµСЂРёР°Р»РёР·СѓСЋС‚СЃСЏ С‚РѕР»СЊРєРѕ Р±РµР·РѕРїР°СЃРЅС‹Рµ РїРѕР»СЏ С„РѕСЂРјС‹ (Р±РµР· submit/button/reset/image/file).
     *
     * Р—Р°РІРёСЃРёРјРѕСЃС‚Рё:
     * - Jsoup РґР»СЏ РёР·РІР»РµС‡РµРЅРёСЏ С„РѕСЂРјС‹/РїРѕР»РµР№;
     * - {@link Host#escapeHtmlAttr(String)} РґР»СЏ Р±РµР·РѕРїР°СЃРЅРѕР№ РїРѕРґСЃС‚Р°РЅРѕРІРєРё РІ HTML-Р°С‚СЂРёР±СѓС‚С‹;
     * - {@link HtmlUtils#GENERATED_PAGE_MARKER} РґР»СЏ РјР°СЂРєРёСЂРѕРІРєРё СЃР»СѓР¶РµР±РЅРѕР№ СЃС‚СЂР°РЅРёС†С‹.
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
                    Log.d(TAG, msg_code_required);
                    FileLogger.trace(TAG, msg_code_required);
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
            sb.append("Р—Р°РІРµСЂС€РµРЅРёРµ Р±РѕСЏ...<br>");
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
     * РЎС‡РёС‚С‹РІР°РµС‚ РјР°СЂРєРµСЂС‹ finish-СЃС‚СЂР°РЅРёС†С‹ РёР· СЃС‹СЂРѕРіРѕ HTML.
     *
     * РќР°Р·РЅР°С‡РµРЅРёРµ:
     * - РїРѕРґРіРѕС‚РѕРІРёС‚СЊ С„Р°РєС‚РёС‡РµСЃРєРёРµ РїСЂРёР·РЅР°РєРё РґР»СЏ РІС‹Р±РѕСЂР° {@link FinishFlowDecision};
     * - РґР°С‚СЊ РїРѕР»РЅС‹Р№ РґРёР°РіРЅРѕСЃС‚РёС‡РµСЃРєРёР№ РєРѕРЅС‚РµРєСЃС‚ РґР»СЏ Р»РѕРіРёСЂРѕРІР°РЅРёСЏ РїСЂРёС‡РёРЅС‹ РІРµС‚РєРё.
     *
     * Р—Р°РІРёСЃРёРјРѕСЃС‚Рё:
     * - Jsoup-СЃРµР»РµРєС‚РѕСЂС‹ (`form[name=FEND]`, `input[name=code]`);
     * - {@link HelperStrings#subString(String, String, String)} + JS-token helper-РјРµС‚РѕРґС‹ Host
     *   РґР»СЏ СЂР°Р·Р±РѕСЂР° `fight_ty`/`fexp`.
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
     * РџРёС€РµС‚ СЃС‚СЂСѓРєС‚СѓСЂРёСЂРѕРІР°РЅРЅС‹Р№ Р»РѕРі РІС‹Р±СЂР°РЅРЅРѕР№ РІРµС‚РєРё Р·Р°РІРµСЂС€РµРЅРёСЏ Р±РѕСЏ.
     *
     * РќР°Р·РЅР°С‡РµРЅРёРµ:
     * - СѓРїСЂРѕСЃС‚РёС‚СЊ СЂР°Р·Р±РѕСЂ СЃР»РѕР¶РЅС‹С… РєРµР№СЃРѕРІ, РєРѕРіРґР° Р±РѕР№ Р·Р°РІРёСЃР°РµС‚ РЅР° finish/captcha;
     * - С„РёРєСЃРёСЂРѕРІР°С‚СЊ РЅРµ С‚РѕР»СЊРєРѕ РІС‹Р±РѕСЂ РІРµС‚РєРё, РЅРѕ Рё РєРѕРЅС‚РµРєСЃС‚ (РјР°СЂРєРµСЂС‹ HTML, С‚РѕРєРµРЅС‹, URL).
     *
     * РџСЂР°РІРёР»Рѕ:
     * - Р»РѕРі РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РјР°РєСЃРёРјР°Р»СЊРЅРѕ РёРЅС„РѕСЂРјР°С‚РёРІРЅС‹Рј, РЅРѕ РЅРµ РјРµРЅСЏС‚СЊ runtime-РїРѕРІРµРґРµРЅРёРµ.
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
        Log.d(TAG, msg_finish_flow);
        FileLogger.trace(TAG, msg_finish_flow);
    }
}
