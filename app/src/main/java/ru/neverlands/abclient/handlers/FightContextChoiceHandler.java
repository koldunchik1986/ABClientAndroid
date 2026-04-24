package ru.neverlands.abclient.handlers;

import ru.neverlands.abclient.utils.AppLog;

/**
 * Выбирает HTML-контекст для одного авто-хода: текущий WebView HTML, кэш боя или server-probe.
 *
 * Важно: handler не меняет глобальное состояние и не запускает сеть. Он только принимает решение,
 * а MainActivity выполняет side effects в существующем контуре.
 */
public final class FightContextChoiceHandler {
    private static final String TAG = "FightContextChoiceHandler";
    private static final String CHAIN = "fight_context_choice";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";

    private FightContextChoiceHandler() {
    }

    public interface Oracle {
        boolean hasFightMarkers(String html);

        boolean isActiveFightContext(String html);

        boolean hasPendingAct7FightLink(String fightLink);
    }

    public static Decision chooseForCurrentHtml(
            String currentHtml,
            String cachedHtml,
            String fightLink,
            boolean allowServerProbeFallback,
            Oracle oracle) {
        if (currentHtml == null) {
            return chooseForNullHtml(cachedHtml, fightLink, allowServerProbeFallback, oracle);
        }

        if (oracle.hasFightMarkers(currentHtml)) {
            if (allowServerProbeFallback && !oracle.isActiveFightContext(currentHtml)) {
                logDebug("current html has stale fight markers, inactive context, fightLink=" + fightLink);
                return chooseAfterInactiveCurrentHtml(cachedHtml, fightLink, oracle);
            }
            return Decision.autoTurn(currentHtml);
        }

        boolean cachedHasMarkers = oracle.hasFightMarkers(cachedHtml);
        if (cachedHasMarkers) {
            if (oracle.isActiveFightContext(cachedHtml)) {
                logDebug("fallback to cached active fight html, len=" + cachedHtml.length());
                return Decision.autoTurnAndMaybeProbe(
                        cachedHtml,
                        allowServerProbeFallback && !oracle.hasPendingAct7FightLink(fightLink),
                        "cached_active_fight_html_keepalive",
                        false);
            }

            logDebug("cached fight html is stale (inactive), drop and probe");
            if (allowServerProbeFallback) {
                return Decision.probeAndAutoTurnCurrent(
                        currentHtml,
                        "cached_fight_html_inactive",
                        true,
                        true);
            }
            return Decision.autoTurnAndClearCached(currentHtml);
        }

        logDebug("no fight markers in current/cached html");
        if (allowServerProbeFallback) {
            return Decision.probeAndAutoTurnCurrent(
                    currentHtml,
                    "no_fight_markers_current_and_cached",
                    false,
                    true);
        }
        return Decision.autoTurn(currentHtml);
    }

    public static Decision chooseForNullHtml(
            String cachedHtml,
            String fightLink,
            boolean allowServerProbeFallback,
            Oracle oracle) {
        if (oracle.hasFightMarkers(cachedHtml)) {
            if (oracle.isActiveFightContext(cachedHtml)) {
                logDebug("null html, fallback to cached active fight html, len=" + cachedHtml.length());
                return Decision.autoTurnAndMaybeProbe(
                        cachedHtml,
                        allowServerProbeFallback && !oracle.hasPendingAct7FightLink(fightLink),
                        "null_html_cached_active_fight_html_keepalive",
                        false);
            }

            logDebug("null html with stale cached fight html, drop and probe");
            if (allowServerProbeFallback) {
                return Decision.probeOnly("null_html_stale_cached_fight_html", true, true);
            }
            return Decision.clearCachedOnly();
        }

        logDebug("html is null and cached html has no fight markers");
        if (allowServerProbeFallback) {
            return Decision.probeOnly("null_html_and_no_cached_markers", false, true);
        }
        return Decision.none();
    }

    private static Decision chooseAfterInactiveCurrentHtml(String cachedHtml, String fightLink, Oracle oracle) {
        boolean cachedHasMarkers = oracle.hasFightMarkers(cachedHtml);
        boolean cachedActiveFight = cachedHasMarkers && oracle.isActiveFightContext(cachedHtml);
        if (cachedActiveFight) {
            logDebug("fallback to cached active fight html after inactive current html, len=" + cachedHtml.length());
            return Decision.autoTurnAndMaybeProbe(
                    cachedHtml,
                    !oracle.hasPendingAct7FightLink(fightLink),
                    "cached_active_fight_html_keepalive_after_inactive_current",
                    false);
        }

        boolean clearCached = cachedHasMarkers;
        if (clearCached) {
            logDebug("drop stale cached fight html after inactive current html");
        }

        if (oracle.hasPendingAct7FightLink(fightLink)) {
            logDebug("pending finish link selected after inactive current html");
            return Decision.pendingFinish(clearCached);
        }

        return Decision.probeOnly("current_fight_html_inactive", clearCached, true);
    }

    private static void logDebug(String message) {
        AppLog.d(CHAIN, TAG, BG_TRACE_PREFIX + " requestAutoTurn: " + message);
    }

    public static final class Decision {
        private final boolean autoTurn;
        private final String autoTurnHtml;
        private final boolean requestProbe;
        private final String probeReason;
        private final boolean clearCachedHtml;
        private final boolean navigatePendingFinish;
        private final boolean logProbeIfUiActive;

        private Decision(
                boolean autoTurn,
                String autoTurnHtml,
                boolean requestProbe,
                String probeReason,
                boolean clearCachedHtml,
                boolean navigatePendingFinish,
                boolean logProbeIfUiActive) {
            this.autoTurn = autoTurn;
            this.autoTurnHtml = autoTurnHtml;
            this.requestProbe = requestProbe;
            this.probeReason = probeReason;
            this.clearCachedHtml = clearCachedHtml;
            this.navigatePendingFinish = navigatePendingFinish;
            this.logProbeIfUiActive = logProbeIfUiActive;
        }

        public static Decision autoTurn(String html) {
            return new Decision(true, html, false, "", false, false, false);
        }

        public static Decision autoTurnAndClearCached(String html) {
            return new Decision(true, html, false, "", true, false, false);
        }

        public static Decision autoTurnAndMaybeProbe(
                String html,
                boolean requestProbe,
                String probeReason,
                boolean logProbeIfUiActive) {
            return new Decision(true, html, requestProbe, probeReason, false, false, logProbeIfUiActive);
        }

        public static Decision probeAndAutoTurnCurrent(
                String currentHtml,
                String probeReason,
                boolean clearCachedHtml,
                boolean logProbeIfUiActive) {
            return new Decision(true, currentHtml, true, probeReason, clearCachedHtml, false, logProbeIfUiActive);
        }

        public static Decision probeOnly(String probeReason, boolean clearCachedHtml, boolean logProbeIfUiActive) {
            return new Decision(false, null, true, probeReason, clearCachedHtml, false, logProbeIfUiActive);
        }

        public static Decision pendingFinish(boolean clearCachedHtml) {
            return new Decision(false, null, false, "", clearCachedHtml, true, false);
        }

        public static Decision clearCachedOnly() {
            return new Decision(false, null, false, "", true, false, false);
        }

        public static Decision none() {
            return new Decision(false, null, false, "", false, false, false);
        }

        public boolean shouldAutoTurn() {
            return autoTurn;
        }

        public String getAutoTurnHtml() {
            return autoTurnHtml;
        }

        public boolean shouldRequestProbe() {
            return requestProbe;
        }

        public String getProbeReason() {
            return probeReason;
        }

        public boolean shouldClearCachedHtml() {
            return clearCachedHtml;
        }

        public boolean shouldNavigatePendingFinish() {
            return navigatePendingFinish;
        }

        public boolean shouldLogProbeIfUiActive() {
            return logProbeIfUiActive;
        }
    }
}
