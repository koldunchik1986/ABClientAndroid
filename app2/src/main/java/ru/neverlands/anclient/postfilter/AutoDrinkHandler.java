package ru.neverlands.anclient.postfilter;

import java.net.URI;
import java.util.Locale;

import ru.neverlands.anclient.manager.CharacterVitalsManager;
import ru.neverlands.anclient.manager.FastActionManager;
import ru.neverlands.anclient.manager.NeverApi;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;

class AutoDrinkHandler {

    private static final String TAG = "AutoDrinkHandler";

    static final long AUTO_DRINK_TRIGGER_COOLDOWN_MS = 2500L;

    static volatile long lastAutoDrinkTriggerAtMs = 0L;
    static volatile long lastAutoDrinkBlazTriggerAtMs = 0L;
    static volatile boolean autoDrinkPostFightSyncPending = false;
    static volatile long autoDrinkPostFightSyncPendingSinceMs = 0L;

    static void tryTriggerAutoDrinkRestoreElixir(String address,
                                                  String html,
                                                  boolean isFightFrame,
                                                  boolean isFightTopFrame) {
        if (html == null || html.isEmpty()) {
            return;
        }
        if (AppVars.Profile == null) {
            return;
        }
        if (!FightAuto.isAutoFightEnabledByPreference()) {
            String msg = "AUTO_DRINK_TRACE skip: auto-fight disabled in preferences";
            AppLog.d(TAG, msg);
            return;
        }
        if (isFightFrame || isFightTopFrame) {
            return;
        }
        MainPhp.InsHpSnapshot pageSnapshot = MainPhp.parseInsHpSnapshot(html);
        boolean hasPageSnapshot = pageSnapshot != null
                && (pageSnapshot.maxHp > 0 || pageSnapshot.maxMa > 0);
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
        MainPhp.InsHpSnapshot snapshot = null;
        String snapshotSource = "";
        if (allowPostFightFollowup) {
            MainPhp.InsHpSnapshot pinfoSnapshot = tryBuildAutoDrinkSnapshotFromPinfo();
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
                MainPhp.InsHpSnapshot fallback = new MainPhp.InsHpSnapshot();
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
                AppLog.d(TAG, "AUTO_DRINK_TRACE fallback snapshot: hp="
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
            AppLog.d(TAG, "AUTO_DRINK_TRACE no-trigger: hp="
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
            AppLog.d(TAG, "AUTO_DRINK_TRACE skip cooldown: sinceLastMs=" + sinceLastTrigger
                    + ", hpBelow=" + hpBelow + ", maBelow=" + maBelow);
            FileLogger.trace(TAG, "AUTO_DRINK_TRACE skip cooldown: sinceLastMs=" + sinceLastTrigger
                    + ", hpBelow=" + hpBelow + ", maBelow=" + maBelow);
            return;
        }
        lastAutoDrinkTriggerAtMs = now;
        AppLog.d(TAG, "AUTO_DRINK_TRACE trigger restore elixir: hp="
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

    static MainPhp.InsHpSnapshot tryBuildAutoDrinkSnapshotFromPinfo() {
        if (AppVars.Profile == null) {
            return null;
        }
        String nick = AppVars.Profile.UserNick != null ? AppVars.Profile.UserNick.trim() : "";
        if (nick.isEmpty()) {
            String msg = "AUTO_DRINK_TRACE pinfo skip: empty nick";
            AppLog.d(TAG, msg);
            return null;
        }
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
        long infoApiDurationMs = System.currentTimeMillis() - preInfoApiTs;
        boolean maMismatch = vitals.curMa != null && preInfoApiSnapshot.maxMa > 0
                && Math.abs((vitals.curMa != null ? vitals.curMa : 0) - preInfoApiSnapshot.curMa) > 50;
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
                "AutoDrinkHandler.tryTriggerAutoDrinkRestoreElixir.postFightPinfo"
        );
        if (synced.maxHp <= 0 && synced.maxMa <= 0) {
            String msg = "AUTO_DRINK_TRACE pinfo skip: synced snapshot empty";
            AppLog.d(TAG, msg);
            return null;
        }
        MainPhp.InsHpSnapshot snapshot = new MainPhp.InsHpSnapshot();
        snapshot.curHp = synced.curHp;
        snapshot.maxHp = synced.maxHp;
        snapshot.curMa = synced.curMa;
        snapshot.maxMa = synced.maxMa;
        snapshot.intHp = synced.intHp;
        snapshot.intMa = synced.intMa;
        AppLog.d(TAG, "AUTO_DRINK_TRACE post-fight pinfo snapshot: hp="
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

    static boolean isPostFightAutoDrinkFollowupAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }
        String lower = address.trim().toLowerCase(Locale.ROOT);
        return lower.contains("main.php?get_id=56&act=10&go=inf")
                || lower.contains("main.php?get_id=56&act=10&go=inv")
                || lower.contains("main.php?im=");
    }

    static boolean isServerPlainMainAddress(String address) {
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
}
