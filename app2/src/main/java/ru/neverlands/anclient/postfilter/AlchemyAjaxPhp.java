package ru.neverlands.anclient.postfilter;

import android.content.Intent;
import android.text.TextUtils;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.neverlands.anclient.manager.AutoCutManager;
import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.Russian;

/**
 * Пост-фильтр `alchemy_ajax.php` для Авто-Травника.
 *
 * Использует существующий JS/map flow: `act=1` только анализируется, а `act=3`
 * отправляется через `AjaxGet(...)` или общий native captcha popup.
 */
public final class AlchemyAjaxPhp {
    private static final String TAG = "AlchemyAjaxPhp";
    private static final String ALCHEMY_CAPTCHA_URL_PREFIX = "http://neverlands.ru/modules/code/code.php?";
    private static final long ALCHEMY_CAPTCHA_DIALOG_DEDUP_MS = 1500L;
    private static final long PENDING_CUT_TTL_MS = 120_000L;

    private static volatile String lastAlchemyCaptchaDialogKey = "";
    private static volatile long lastAlchemyCaptchaDialogAtMs = 0L;
    private static volatile PendingCut pendingCut = null;

    private AlchemyAjaxPhp() {
    }

    public static byte[] process(String address, byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }
        String html = Russian.getString(array);
        if (TextUtils.isEmpty(html)) {
            return array;
        }
        String lowerAddress = address == null ? "" : address.toLowerCase(Locale.ROOT);
        if (lowerAddress.contains("act=1")) {
            processAlchemyAct1(html);
            return array;
        }
        if (lowerAddress.contains("act=3")) {
            processAlchemyAct3(html);
        }
        return array;
    }

    private static void processAlchemyAct1(String html) {
        if (!isAutoCutEnabled()) {
            return;
        }
        ResourceState state = parseResourceState(html);
        if (state == null || state.resources.isEmpty()) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG, "act1: no resource state");
            return;
        }

        AutoCutManager autoCut = AutoCutManager.getInstance(AppVars.getContext());
        for (ResourceCandidate resource : state.resources) {
            autoCut.registerObservedHerb(resource.resId, resource.name, 0, resource.rTime, "");
        }

        ResourceCandidate selected = null;
        for (ResourceCandidate resource : state.resources) {
            if (resource.availableCount <= 0 || TextUtils.isEmpty(resource.cutVcode)) {
                continue;
            }
            if (autoCut.isHerbSelected(resource.resId, resource.name)) {
                selected = resource;
                break;
            }
        }
        if (selected == null) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                    "act1: no selected available herb, resources=" + state.resources.size());
            autoCut.onScanWithoutSelectedHerb("alchemy_act1");
            return;
        }

        if (!autoCut.isSickleReadyForCut()) {
            pendingCut = new PendingCut(selected, System.currentTimeMillis());
            autoCut.requestSickleCheckBeforeCut("alchemy_act1:" + selected.resId);
            AppLog.i(AutoCutManager.TRACE_CHAIN, TAG,
                    "act1: selected herb waits for sickle check, herb=" + selected.name);
            return;
        }

        pendingCut = new PendingCut(selected, System.currentTimeMillis());
        String captchaToken = state.captchaToken == null ? "" : state.captchaToken.trim();
        boolean captchaRequired = !captchaToken.isEmpty() && !"00000".equals(captchaToken);
        String finishUrl = selected.buildFinishUrl(captchaRequired ? "????" : "1");
        if (captchaRequired) {
            showAlchemyCaptchaDialogOnce(ALCHEMY_CAPTCHA_URL_PREFIX + captchaToken, finishUrl, selected);
        } else {
            submitAlchemyAct3ViaAjax(finishUrl);
        }
        AppLog.i(AutoCutManager.TRACE_CHAIN, TAG, "act1: selected herb=" + selected.name
                + ", id=" + selected.resId
                + ", captchaRequired=" + captchaRequired);
    }

    private static void processAlchemyAct3(String html) {
        if (AppVars.getContext() == null) {
            return;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("невер") && lower.contains("код")) {
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG, "act3: wrong protection/captcha code response");
            return;
        }
        if (!lower.contains("всё прошло успешно") && !lower.contains("все прошло успешно")) {
            return;
        }

        AutoCutManager autoCut = AutoCutManager.getInstance(AppVars.getContext());
        PendingCut current = pendingCut;
        pendingCut = null;
        if (current != null && !current.isExpired()) {
            autoCut.markHerbCut(
                    current.resource.resId,
                    current.resource.name,
                    current.resource.rTime,
                    resolveCurrentRegNum(),
                    "alchemy_act3",
                    current.resource.massAsDouble());
            return;
        }

        String tracedName = autoCut.consumeRecentTraceCutName();
        if (!TextUtils.isEmpty(tracedName)) {
            autoCut.markHerbCut("", tracedName, 0, resolveCurrentRegNum(), "alchemy_act3_tracecut");
        }
    }

    private static ResourceState parseResourceState(String html) {
        String[] sections = html.split("@", -1);
        if (sections.length < 6) {
            return null;
        }
        String payload = sections[5] == null ? "" : sections[5].trim();
        if (!payload.startsWith("[0,")) {
            return null;
        }
        String inner = stripOuterArray(payload);
        List<String> tokens = MainPhp.splitJsTopLevelCsv(inner);
        if (tokens.size() < 4) {
            return null;
        }
        ResourceState state = new ResourceState();
        state.captchaToken = FightAuto.trimJsToken(tokens.get(1));
        state.rX = FightAuto.parseIntFromJsToken(tokens.get(2), 0);
        state.rY = FightAuto.parseIntFromJsToken(tokens.get(3), 0);
        for (int index = 4; index < tokens.size(); index++) {
            ResourceCandidate resource = parseResourceCandidate(tokens.get(index), state.rX, state.rY);
            if (resource != null) {
                state.resources.add(resource);
            }
        }
        return state;
    }

    private static ResourceCandidate parseResourceCandidate(String token, int rX, int rY) {
        String inner = stripOuterArray(token == null ? "" : token.trim());
        if (inner.isEmpty()) {
            return null;
        }
        List<String> fields = MainPhp.splitJsTopLevelCsv(inner);
        if (fields.size() < 12) {
            return null;
        }
        ResourceCandidate result = new ResourceCandidate();
        result.rX = rX;
        result.rY = rY;
        result.resId = FightAuto.trimJsToken(fields.get(0));
        result.name = FightAuto.trimJsToken(fields.get(1));
        result.lTime = FightAuto.parseIntFromJsToken(fields.get(2), 0);
        result.rTime = FightAuto.parseIntFromJsToken(fields.get(3), 0);
        result.uid = FightAuto.trimJsToken(fields.get(4));
        result.curs = FightAuto.trimJsToken(fields.get(5));
        result.mass = FightAuto.trimJsToken(fields.get(6));
        result.p = FightAuto.trimJsToken(fields.get(7));
        result.availableCount = FightAuto.parseIntFromJsToken(fields.get(8), 0);
        result.cutVcode = FightAuto.trimJsToken(fields.get(9));
        result.rType = FightAuto.trimJsToken(fields.get(10));
        result.totalCount = FightAuto.parseIntFromJsToken(fields.get(11), 0);
        if (TextUtils.isEmpty(result.resId) || TextUtils.isEmpty(result.name)) {
            return null;
        }
        return result;
    }

    private static void showAlchemyCaptchaDialogOnce(String captchaUrl, String finishUrl, ResourceCandidate resource) {
        if (AppVars.getContext() == null || TextUtils.isEmpty(captchaUrl) || TextUtils.isEmpty(finishUrl)) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = captchaUrl + "|" + resource.resId + "|" + resource.cutVcode;
        if (key.equals(lastAlchemyCaptchaDialogKey)
                && now - lastAlchemyCaptchaDialogAtMs < ALCHEMY_CAPTCHA_DIALOG_DEDUP_MS) {
            return;
        }
        lastAlchemyCaptchaDialogKey = key;
        lastAlchemyCaptchaDialogAtMs = now;

        Intent intent = new Intent(AppVars.ACTION_SHOW_CAPTCHA);
        intent.putExtra("captchaUrl", captchaUrl);
        intent.putExtra("finishUrl", finishUrl);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
        AppLog.i(AutoCutManager.TRACE_CHAIN, TAG, "captcha popup requested: herb=" + resource.name);
    }

    private static void submitAlchemyAct3ViaAjax(String absoluteUrl) {
        if (TextUtils.isEmpty(absoluteUrl) || AppVars.getContext() == null) {
            return;
        }
        String rel = toGameplayAjaxRelativeUrl(absoluteUrl);
        String js = "(function(){try{"
                + "if(typeof AjaxGet==='function'){AjaxGet('" + escapeJsSingleQuoted(rel) + "');return 'ajax';}"
                + "if(window&&typeof window.AjaxGet==='function'){window.AjaxGet('" + escapeJsSingleQuoted(rel) + "');return 'window_ajax';}"
                + "return 'missing_ajaxget';"
                + "}catch(e){return 'err:'+e;}})()";
        Intent intent = new Intent(AppVars.ACTION_WEBVIEW_EVAL_JS);
        intent.putExtra("js", js);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
        AppLog.d(AutoCutManager.TRACE_CHAIN, TAG, "act3 no-captcha submit via AjaxGet: " + rel);
    }

    private static boolean isAutoCutEnabled() {
        try {
            return AppVars.getContext() != null
                    && AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoCutEnabled();
        } catch (Exception e) {
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG, "failed to read AutoCut state", e);
            return false;
        }
    }

    private static String stripOuterArray(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.startsWith("[") && safe.endsWith("]") && safe.length() >= 2) {
            return safe.substring(1, safe.length() - 1).trim();
        }
        return "";
    }

    private static String toGameplayAjaxRelativeUrl(String url) {
        String rel = url == null ? "" : url.trim();
        rel = rel.replaceFirst("^https?://[^/]+", "");
        String prefix = "/gameplay/ajax/";
        int index = rel.toLowerCase(Locale.ROOT).indexOf(prefix);
        if (index >= 0) {
            rel = rel.substring(index + prefix.length());
        }
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        return rel;
    }

    private static String escapeJsSingleQuoted(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "");
    }

    private static String resolveCurrentRegNum() {
        if (AppVars.Profile == null || TextUtils.isEmpty(AppVars.Profile.MapLocation)) {
            return "";
        }
        return AppVars.Profile.MapLocation.trim();
    }

    private static final class ResourceState {
        String captchaToken = "";
        int rX = 0;
        int rY = 0;
        final List<ResourceCandidate> resources = new ArrayList<>();
    }

    private static final class ResourceCandidate {
        String resId = "";
        String name = "";
        int rX = 0;
        int rY = 0;
        int lTime = 0;
        int rTime = 0;
        String uid = "";
        String curs = "";
        String mass = "";
        String p = "";
        int availableCount = 0;
        String cutVcode = "";
        String rType = "";
        int totalCount = 0;

        String buildFinishUrl(String code) {
            return "http://neverlands.ru/gameplay/ajax/alchemy_ajax.php?act=3"
                    + "&res_id=" + resId
                    + "&r_x=" + rX
                    + "&r_y=" + rY
                    + "&r_time=" + rTime
                    + "&r_type=" + rType
                    + "&uid=" + uid
                    + "&curs=" + curs
                    + "&mass=" + mass
                    + "&p=" + p
                    + "&l_time=" + lTime
                    + "&vcode=" + cutVcode
                    + "&code=" + code
                    + "&r=" + System.currentTimeMillis();
        }

        double massAsDouble() {
            if (mass == null || mass.trim().isEmpty()) {
                return 0d;
            }
            try {
                return Double.parseDouble(mass.trim().replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return 0d;
            }
        }
    }

    private static final class PendingCut {
        final ResourceCandidate resource;
        final long createdAtMs;

        PendingCut(ResourceCandidate resource, long createdAtMs) {
            this.resource = resource;
            this.createdAtMs = createdAtMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAtMs > PENDING_CUT_TTL_MS;
        }
    }
}
