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
 *
 * Зависимости:
 * - `Filter.process(...)` маршрутизирует сюда ответы `/gameplay/ajax/alchemy_ajax.php`;
 * - `AutoCutManager` хранит выбранные травы, checked-клетки, таймеры и cleanup-состояние;
 * - `MainActivity.showCaptchaDialog(...)` принимает `ACTION_SHOW_CAPTCHA` и подставляет код в `finishUrl`;
 * - `WebAppInterface.DoHerbAutoCut()` решает, когда map.js вообще нажимает `Оглядеться`;
 * - `AppVars.AutoCutArmedSickle` защищает `act=3` от среза без подготовленного серпа.
 */
public final class AlchemyAjaxPhp {
    private static final String TAG = "AlchemyAjaxPhp";
    /** Базовый URL server captcha image; query-часть приходит в `RESO@` как `captchaToken`. */
    private static final String ALCHEMY_CAPTCHA_URL_PREFIX = "http://neverlands.ru/modules/code/code.php?";
    /** Дедуп popup, чтобы один и тот же `captchaToken|res_id|cutVcode` не открыл несколько диалогов. */
    private static final long ALCHEMY_CAPTCHA_DIALOG_DEDUP_MS = 1500L;
    /** TTL pending-среза между `act=1` и подтверждением успеха `act=3`. */
    private static final long PENDING_CUT_TTL_MS = 120_000L;

    /** Последний popup-key для защиты от повторного native captcha dialog. */
    private static volatile String lastAlchemyCaptchaDialogKey = "";
    /** Время последнего popup-key; используется вместе с `ALCHEMY_CAPTCHA_DIALOG_DEDUP_MS`. */
    private static volatile long lastAlchemyCaptchaDialogAtMs = 0L;
    /** Последняя выбранная трава, по которой ждём ответ `act=3`; нужна для таймера и массы. */
    private static volatile PendingCut pendingCut = null;

    private AlchemyAjaxPhp() {
    }

    /**
     * Единая точка обработки ajax-ответа.
     *
     * Переменные:
     * - `address` — URL запроса, по `act=1/act=3` выбирается ветка;
     * - `array` — исходные байты Windows-1251/UTF, возвращаются без модификации, чтобы JS-карта
     *   продолжила штатно обрабатывать `RESO@`.
     */
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

    /**
     * Разбор ответа `act=1` (`Оглядеться`).
     *
     * Алгоритм:
     * - парсит `RESO@` и регистрирует все увиденные травы в словаре настроек;
     * - выбирает первую доступную выбранную траву (`availableCount > 0` и `cutVcode` не пустой);
     * - если серп не готов, откладывает выбранный ресурс и запускает main.php-проверку серпа;
     * - если captcha нужна, открывает native popup; иначе отправляет `act=3` через `AjaxGet(...)`.
     */
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
            pendingCut = new PendingCut(selected, buildCellResourcesSummary(state.resources), System.currentTimeMillis());
            autoCut.requestSickleCheckBeforeCut("alchemy_act1:" + selected.resId);
            AppLog.i(AutoCutManager.TRACE_CHAIN, TAG,
                    "act1: selected herb waits for sickle check, herb=" + selected.name);
            return;
        }

        pendingCut = new PendingCut(selected, buildCellResourcesSummary(state.resources), System.currentTimeMillis());
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

    /**
     * Разбор ответа `act=3` после среза.
     *
     * Зависимости:
     * - успешный серверный маркер: `Всё прошло успешно.` / `Все прошло успешно.`;
     * - `pendingCut` содержит `resId/name/rTime/mass`, чтобы `AutoCutManager.markHerbCut(...)`
     *   поставил таймер роста, пометил клетку checked и оценил cleanup-порог;
     * - fallback `TraceCut` используется, если серверный успех пришёл без актуального pending-состояния.
     */
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
                    current.resource.massAsDouble(),
                    current.cellResourcesSummary);
            return;
        }

        String tracedName = autoCut.consumeRecentTraceCutName();
        if (!TextUtils.isEmpty(tracedName)) {
            autoCut.markHerbCut("", tracedName, 0, resolveCurrentRegNum(), "alchemy_act3_tracecut");
        }
    }

    /**
     * Парсит общий `RESO@` payload в DTO `ResourceState`.
     *
     * Формат source tokens:
     * - `tokens[1]` — captcha token;
     * - `tokens[2]/tokens[3]` — координаты ресурса;
     * - `tokens[4..]` — массивы конкретных трав.
     */
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

    /**
     * Парсит одну запись травы из `ingr[i]`.
     *
     * Поля соответствуют HAR/browser protocol:
     * `res_id`, `res_name`, `l_time`, `r_time`, `uid`, `curs`, `mass`, `p`,
     * `availableCount`, `cutVcode`, `r_type`, `totalCount`.
     */
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

    /** Собирает человекочитаемый snapshot всех трав клетки для chat-report после успешного среза. */
    private static String buildCellResourcesSummary(List<ResourceCandidate> resources) {
        if (resources == null || resources.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ResourceCandidate resource : resources) {
            if (resource == null || TextUtils.isEmpty(resource.name)) {
                continue;
            }
            int available = Math.max(0, resource.availableCount);
            int total = resource.totalCount > 0 ? resource.totalCount : available;
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append('"')
                    .append(resource.name.trim())
                    .append("\" ")
                    .append(available)
                    .append('/')
                    .append(Math.max(0, total));
        }
        return builder.toString();
    }

    /**
     * Открывает native captcha popup ровно один раз на challenge.
     *
     * `finishUrl` содержит `code=????`; `MainActivity` заменяет placeholder на ответ пользователя
     * или Anti-Captcha и отправляет тот же URL через `AjaxGet(...)`.
     */
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

    /**
     * Отправляет no-captcha срез внутри текущего WebView JS-контекста.
     *
     * Почему не `loadUrl`:
     * - `alchemy_ajax.php` должен остаться в map ajax flow;
     * - Java postfilter всё равно увидит response и вызовет `processAlchemyAct3(...)`;
     * - ручные main.php frame navigation не перехватываются.
     */
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

    /** License-gated проверка persisted AutoCut-флага; fail-closed при ошибках context/manager. */
    private static boolean isAutoCutEnabled() {
        try {
            return AppVars.getContext() != null
                    && AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoCutEnabled();
        } catch (Exception e) {
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG, "failed to read AutoCut state", e);
            return false;
        }
    }

    /** Удаляет внешние `[` и `]` у JS-массива без выполнения JS. */
    private static String stripOuterArray(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.startsWith("[") && safe.endsWith("]") && safe.length() >= 2) {
            return safe.substring(1, safe.length() - 1).trim();
        }
        return "";
    }

    /** Нормализует absolute gameplay ajax URL в относительный путь, который ожидает `AjaxGet(...)`. */
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

    /** Экранирует строку для одинарных кавычек JS snippet, отправляемого через `ACTION_WEBVIEW_EVAL_JS`. */
    private static String escapeJsSingleQuoted(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "");
    }

    /** Текущая клетка персонажа из профиля; используется для текста таймера и checked-cell state. */
    private static String resolveCurrentRegNum() {
        if (AppVars.Profile == null || TextUtils.isEmpty(AppVars.Profile.MapLocation)) {
            return "";
        }
        return AppVars.Profile.MapLocation.trim();
    }

    /** DTO всего `act=1` состояния: captcha token, координаты и список кандидатов-трав. */
    private static final class ResourceState {
        /** Серверный id картинки captcha без base URL. */
        String captchaToken = "";
        /** X-координата resource-state; дублируется в `act=3`. */
        int rX = 0;
        /** Y-координата resource-state; дублируется в `act=3`. */
        int rY = 0;
        /** Все травы из `RESO@`, включая недоступные и невыбранные. */
        final List<ResourceCandidate> resources = new ArrayList<>();
    }

    /** DTO одной травы из `ingr[i]`; поля напрямую соответствуют параметрам `act=3`. */
    private static final class ResourceCandidate {
        /** `res_id` — стабильный id травы. */
        String resId = "";
        /** Человекочитаемое имя травы. */
        String name = "";
        /** Координата X из общего resource-state. */
        int rX = 0;
        /** Координата Y из общего resource-state. */
        int rY = 0;
        /** `l_time` — серверный параметр времени локации/среза. */
        int lTime = 0;
        /** `r_time` — время роста/восстановления травы в минутах. */
        int rTime = 0;
        /** `uid` — серверный uid ресурса для `act=3`. */
        String uid = "";
        /** `curs` — серверный параметр курсора/состояния ресурса. */
        String curs = "";
        /** `mass` — вес одной срезанной травы, используется для cleanup threshold. */
        String mass = "";
        /** `p` — серверный параметр среза, передаётся обратно без изменения. */
        String p = "";
        /** `ingr[i][8]`: сколько единиц доступно сейчас; 0 запрещает автосрез. */
        int availableCount = 0;
        /** Per-herb protection code из `ingr[i][9]`; не заменять на SessionManager vcode. */
        String cutVcode = "";
        /** `r_type` — тип ресурса для `act=3`. */
        String rType = "";
        /** Общее количество ресурса на клетке, только для диагностики. */
        int totalCount = 0;

        /** Строит точный `act=3` URL по HAR-протоколу, подставляя captcha/manual code. */
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

        /** Возвращает `mass` как double для накопления cleanup-порога. */
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

    /** Pending-снимок выбранной травы между `act=1` и подтверждением `act=3`. */
    private static final class PendingCut {
        /** Выбранный resource candidate; содержит id/name/rTime/mass для success handling. */
        final ResourceCandidate resource;
        /** Человекочитаемый список всех трав на клетке из того же `RESO@`. */
        final String cellResourcesSummary;
        /** Локальное время создания; защищает от stale success после reload/нового `Оглядеться`. */
        final long createdAtMs;

        PendingCut(ResourceCandidate resource, String cellResourcesSummary, long createdAtMs) {
            this.resource = resource;
            this.cellResourcesSummary = cellResourcesSummary == null ? "" : cellResourcesSummary.trim();
            this.createdAtMs = createdAtMs;
        }

        /** true, если pending-состояние устарело и нельзя использовать его для таймера/массы. */
        boolean isExpired() {
            return System.currentTimeMillis() - createdAtMs > PENDING_CUT_TTL_MS;
        }
    }
}
