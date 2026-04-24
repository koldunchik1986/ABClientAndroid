package ru.neverlands.anclient.postfilter;

import android.content.Intent;
import android.webkit.WebView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.manager.CharacterVitalsManager;
import ru.neverlands.anclient.manager.FastActionManager;
import ru.neverlands.anclient.manager.NeverApi;
import ru.neverlands.anclient.MainActivity;
import ru.neverlands.anclient.model.ParsedDressed;
import ru.neverlands.anclient.model.Prims;
import ru.neverlands.anclient.model.UserConfig;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.HtmlUtils;
import ru.neverlands.anclient.utils.Chat;
import ru.neverlands.anclient.utils.ChatStats;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.GearParser;
import ru.neverlands.anclient.utils.HelperStrings;
import ru.neverlands.anclient.utils.InventoryParser;
import ru.neverlands.anclient.utils.ParseUtils;
import ru.neverlands.anclient.utils.Russian;
import ru.neverlands.anclient.utils.SessionManager;

// Пост-фильтр fish_ajax.php: считает отчет улова и синхронизирует runtime-флаги авто-рыбалки.
public final class FishAjaxPhp {
    private static final String TAG = "FishAjaxPhp";
    private static final String FISH_AJAX_ACT1 = "act=1";
    private static final String FISH_CAPTCHA_URL_PREFIX = "http://neverlands.ru/modules/code/code.php?";
    private static final long FISH_CAPTCHA_DIALOG_DEDUP_MS = 1500L;
    private static final long FISH_AUTORELOAD_DEDUP_MS = 2500L;
    private static final long FISH_AUTORELOAD_SAFETY_MS = 350L;
    private static final long FISH_CYCLE_RETRY_DELAY_MS = 12_000L;
    private static final int FISH_CYCLE_MAX_ATTEMPTS = 4;
    private static final int FISH_ACT1_ERR_RECOVERY_THRESHOLD = 2;
    private static final long FISH_BOOTSTRAP_DEDUP_MS = 4000L;
    private static final long FISH_FIGHT_GUARD_DELAY_MS = 1500L;
    private static final long FISH_FIGHT_PULSE_GUARD_MS = 7000L;
    private static final long FISH_NO_CAPTCHA_FALLBACK_DELAY_MS = 1200L;
    private static final long FISH_NO_CAPTCHA_FALLBACK_DEDUP_MS = 2500L;
    private static volatile String lastFishCaptchaDialogKey = "";
    private static volatile long lastFishCaptchaDialogAtMs = 0L;
    private static volatile long lastFishAutoreloadAtMs = 0L;
    private static volatile long lastFishAutoreloadDueAtMs = 0L;
    private static volatile long lastFishAct1AtMs = 0L;
    private static volatile long lastFishAct2AtMs = 0L;
    private static volatile long lastFishCycleToken = 0L;
    private static volatile long lastFishAct1ErrAtMs = 0L;
    private static volatile int consecutiveFishAct1ErrCount = 0;
    private static volatile long lastFishBootstrapAtMs = 0L;
    private static volatile String lastFishNoCaptchaFallbackKey = "";
    private static volatile long lastFishNoCaptchaFallbackAtMs = 0L;
    /** Таймер из act=1 section[4]: JS TimerStart() СКЛАДЫВАЕТ act=1 + act=2 таймеры. */
    private static volatile int lastAct1TimerSec = 0;

    private static final Map<String, Double> FISH_NV = new LinkedHashMap<>();
    private static final Map<String, Double> FISH_MASS = new LinkedHashMap<>();
    private static final Map<String, BaitInfo> BAIT_INFO = new LinkedHashMap<>();
    private static final Pattern FISH_COOLDOWN_PATTERN = Pattern.compile("@\\[0,\\[2,(\\d+)\\]\\]@");
    private static final SimpleDateFormat FISH_TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    static {
        putFish("Карась", 4.32, 2.0);
        putFish("Плотва", 3.62, 2.0);
        putFish("Пескарь", 3.94, 2.0);
        putFish("Щука", 23.15, 5.0);
        putFish("Ёрш", 3.34, 2.0);
        putFish("Окунь", 11.54, 2.0);
        putFish("Краснопёрка", 8.58, 2.0);
        putFish("Налим", 23.85, 3.0);
        putFish("Судак", 13.14, 2.0);
        putFish("Верхоплавка", 2.68, 2.0);
        putFish("Лещ", 22.20, 2.0);
        putFish("Подлещик", 4.76, 2.0);
        putFish("Карп", 5.26, 2.0);
        putFish("Форель", 29.75, 5.0);
        putFish("Бычок", 8.80, 2.0);
        putFish("Голавль", 7.26, 2.0);
        putFish("Линь", 31.62, 2.0);
        putFish("Сом", 42.04, 4.0);
        putFish("Язь", 29.12, 2.0);

        putBait("38", "Хлеб", 1.0, 0.2);
        putBait("39", "Червяк", 1.0, 0.1);
        putBait("40", "Крупный червяк", 1.0, 0.2);
        putBait("41", "Опарыш", 5.0, 0.1);
        putBait("42", "Мотыль", 5.0, 0.1);
        putBait("43", "Блесна", 10.0, 0.3);
        putBait("44", "Донка", 12.0, 0.3);
        putBait("45", "Мормышка", 15.0, 0.3);
        putBait("46", "Заговоренная блесна", 20.0, 0.4);
    }

    private FishAjaxPhp() {
    }

    /**
     * Главная точка пост-обработки fish_ajax.php.
     *
     * Зависимости:
     * - {@link Russian#getString(byte[])} / {@link Russian#getBytes(String)} для работы с cp1251-ответом;
     * - runtime-состояние из {@link AppVars} (AutoFishLikeId, AutoFishMassa, AutoFishNV, Profile.FishUm);
     * - UI-уведомления через {@link LocalBroadcastManager} (ACTION_ADD_CHAT_MESSAGE, ACTION_STOP_AUTOFISH).
     */
    public static byte[] process(String address, byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        AppVars.PriSelected = false;
        String html = Russian.getString(array);
        if (html == null || html.isEmpty()) {
            return array;
        }

        String lower = html.toLowerCase(Locale.ROOT);
        if (containsFishNoGearHardStop(lower)) {
            handleFishNoGearHardStop(address);
            return array;
        }
        if (containsFishHardStop(lower)) {
            disableAutoFish("Нет снастей/приманки или не хватает умения для текущей локации");
            return array;
        }

        String lowerAddress = address == null ? "" : address.toLowerCase(Locale.ROOT);
        if (lowerAddress.contains("act=2") && containsFishWrongProtectionCode(lower)) {
            int errCount = registerAct1ErrAndMaybeRecover("wrong_code_protection");
            AppLog.w(TAG, "AUTO_FISH_TRACE act2 returned wrong protection code, errCount=" + errCount
                    + ", address=" + address);
            long now = System.currentTimeMillis();
            long sinceAct1Ms = lastFishAct1AtMs > 0L ? (now - lastFishAct1AtMs) : -1L;
            long sinceAct2Ms = lastFishAct2AtMs > 0L ? (now - lastFishAct2AtMs) : -1L;
            long neverTimerDueInMs = AppVars.NeverTimer > 0L ? Math.max(0L, AppVars.NeverTimer - now) : 0L;
            String msgWrongCodeDiag = "AUTO_FISH_TRACE wrong-code diag: token=" + lastFishCycleToken
                    + ", sinceAct1Ms=" + sinceAct1Ms
                    + ", sinceAct2Ms=" + sinceAct2Ms
                    + ", neverTimerDueInMs=" + neverTimerDueInMs
                    + ", suppressBgProbes=" + AppVars.suppressBackgroundProbesDuringFishing
                    + ", codeAddressPresent=" + (AppVars.CodeAddress != null && !AppVars.CodeAddress.isEmpty())
                    + ", fightLinkPresent=" + (AppVars.FightLink != null && !AppVars.FightLink.isEmpty());
            AppLog.w(TAG, TAG, msgWrongCodeDiag);
            // КРИТИЧНО: vcode из озера невалиден - помечаем озеро как испорченное
            // Следующий цикл перезагружает озеро и получает свежий vcode
            AppVars.FishLakeShouldBeRefreshed = true;
            // Вместо полного bootstrap, инициируем мягкий перезапуск цикла рыбалки
            // (act=1 пересчитает vcode без перезагрузки main.php).
            lastFishAct1AtMs = 0L;
            AppVars.suppressBackgroundProbesDuringFishing = false;
            resetAct1ErrRecoveryState();
            requestSoftAutoFishRecovery("wrong_code_protection");
            return array;
        }
        if (lowerAddress.contains(FISH_AJAX_ACT1)) {
            processFishAct1(address, html);
            return array;
        }

        if (lowerAddress.contains("act=2")) {
            lastFishAct2AtMs = System.currentTimeMillis();
            // Очищаем флаг блокировки фоновых probe'ов после завершения act=2.
            AppVars.suppressBackgroundProbesDuringFishing = false;
            syncFishCooldownAndScheduleNextCycle(html);
        }

        if (!lower.contains("лёв:") && !lower.contains("клев:")) {
            return array;
        }

        String report = fishReport(html);
        if (report == null || report.isEmpty()) {
            return array;
        }

        int p1 = html.indexOf('"');
        if (p1 < 0) {
            return array;
        }
        int p2 = html.indexOf('"', p1 + 1);
        if (p2 < 0) {
            return array;
        }

        String updated = html.substring(0, p1 + 1) + report + html.substring(p2);
        return Russian.getBytes(updated);
    }

    /**
     * Совместимость старых вызовов роутера (без URL).
     */
    public static byte[] process(byte[] array) {
        return process("", array);
    }

    /**
     * Обрабатывает `fish_ajax.php?act=1`: выбирает разрешённую приманку и при captcha
     * открывает native popup по тому же контракту, что и завершение боя.
     *
     * Зависимости:
     * - профиль `AppVars.Profile.FishEnabledPrims` (битмаска `Prims`);
     * - формат payload `RESO@...@[1,"captcha","vcode",massCur,massMax,[primid,name,count]...]`;
     * - `AppVars.ACTION_SHOW_CAPTCHA` + `MainActivity.showCaptchaDialog(...)`.
     */
    private static void processFishAct1(String address, String html) {
        if (!isAutoFishEnabled()) {
            return;
        }
        if (html != null && "ERR".equalsIgnoreCase(html.trim())) {
            int errCount = registerAct1ErrAndMaybeRecover("act1_err");
            AppLog.d(TAG, "AUTO_FISH_TRACE act1 temporary ERR, will retry by cycle guard, errCount="
                    + errCount + ", address=" + address);
            return;
        }

        FishAct1State state = parseFishAct1State(html);
        if (state == null) {
            AppLog.d(TAG, "AUTO_FISH_TRACE act1 parse failed, address=" + address);
            return;
        }

        if (!state.massCurrent.isEmpty() && !state.massMax.isEmpty()) {
            AppVars.AutoFishMassa = state.massCurrent + "/" + state.massMax;
        }

        String beforeSelectMsg = "AUTO_FISH_TRACE before selectAllowedBait: state has " + state.baits.size() + " baits";
        AppLog.d(TAG, TAG, beforeSelectMsg);
        
        FishBaitSelection selection = selectAllowedBait(state.baits);
        if (selection == null) {
            String failMsg = "AUTO_FISH_TRACE act1: ❌ selectAllowedBait returned null, disabling auto-fish";
            AppLog.e(TAG, TAG, failMsg);
            disableAutoFish("Нет доступной приманки по настройкам");
            return;
        }

        String selectedMsg = "AUTO_FISH_TRACE act1: ✅ bait selected id=" + selection.id + " name=" + selection.name + " count=" + selection.count;
        AppLog.i(TAG, TAG, selectedMsg);

        AppVars.AutoFishLikeId = selection.id;
        AppVars.AutoFishLikeVal = String.valueOf(selection.count);
        AppVars.NamePri = selection.name;
        AppVars.ValPri = selection.count;

        if (state.vcode == null || state.vcode.isEmpty()) {
            AppLog.w(TAG, "AUTO_FISH_TRACE act1 skip: empty vcode");
            return;
        }
        resetAct1ErrRecoveryState();
        // Получаем свежий vcode из act=1 response
        // Маркируем успешный старт только после валидного parse + vcode.
        // Это защищает от ложного "confirmed" при ответах вида ERR.
        
        // ✅ ОБЯЗАТЕЛЬНО: Кэшируем извлеченный vcode через SessionManager (правило 5)
        // Это защищает от race condition где SERVER_TIMER_TICK видит пустой vcode
        // и принудительно перезагружает main.php, теряя сессию
        SessionManager.getInstance().parseVCodeFromHtml("vcode=" + state.vcode, "fish");
        String msg_vcode = "AUTO_FISH_TRACE act1: vcode parsed through SessionManager, vcode=" + state.vcode;
        AppLog.d(TAG, TAG, msg_vcode);
        
        lastFishAct1AtMs = System.currentTimeMillis();
        // Блокируем фоновые probe'ы (main.php?go=inf&af_tick=1) на время рыбалки.
        // После act=2 ("Ловить") сервер держит соединение весь таймер навыка (long-polling):
        // ~60с с высоким навыком, до ~300с с нулевым.
        // Safety-net таймаут = 360с (покрывает худший случай). Нормально act=2 response
        // очищает флаг сразу по получении ответа.
        AppVars.suppressBackgroundProbesDuringFishing = true;
        AppVars.fishingSequenceStartAtMs = lastFishAct1AtMs;
        AppLog.d(TAG, "AUTO_FISH_TRACE act1: suppression enabled, safety timeout="
                + AppVars.fishingExpectedDurationMs + "ms");

        // ✅ ПРАВИЛО 4 (AGENTS.MD): Проверка на сообщение сервера о перегрузе
        // Сервер может отправить "<font color=#CC0000>Внимание! Возможен перегруз." если текущая масса критична
        if (checkOverweightHtmlPattern(html)) {
            String msg_overweight = "❌ AUTO_FISH_TRACE act1: Перегруз обнаружен в HTML, рыбалка остановлена";
            AppLog.e(TAG, TAG, msg_overweight);
            return;  // Рыбалка остановлена
        }

        // C# parity: переодевание снастей выполняется только через MainPhp (AutoFishCheckUd/AutoFishWearUd).
        // Здесь оставляем лишь разбор act=1 + выбор приманки + капча/act=2.
        String parityRouteMsg = "AUTO_FISH_TRACE act1 parity route: gear/overweight checks delegated to MainPhp";
        AppLog.d(TAG, TAG, parityRouteMsg);

        boolean captchaRequired = state.captchaToken != null
                && !state.captchaToken.isEmpty()
                && !"00000".equals(state.captchaToken);
        if (!captchaRequired) {
            scheduleNoCaptchaAct2Fallback(state.vcode, selection.id, lastFishAct1AtMs);
            AppLog.d(TAG, "AUTO_FISH_TRACE act1: captcha not required, primid=" + selection.id
                    + ", vcode=" + state.vcode);
            return;
        }

        // ✅ SessionManager: получаем валидный vcode для act=2 (в случае капчи)
        String currentVcode = ru.neverlands.anclient.utils.SessionManager.getInstance()
                .getValidVCodeForAction("fish_act2");
        if (currentVcode == null || currentVcode.isEmpty()) {
            AppLog.w(TAG, "⚠️ AUTO_FISH_TRACE act1: vcode not available from SessionManager, using fallback state.vcode");
            currentVcode = state.vcode;  // fallback на переданное значение
        }
        String submitUrl = "http://neverlands.ru/gameplay/ajax/fish_ajax.php?act=2"
                + "&primid=" + selection.id
                + "&vcode=" + currentVcode
                + "&code=????"
                + "&r=" + System.currentTimeMillis();
        String captchaUrl = FISH_CAPTCHA_URL_PREFIX + state.captchaToken;
        showFishCaptchaDialogOnce(captchaUrl, submitUrl);

        AppLog.d(TAG, "AUTO_FISH_TRACE act1: captcha required, primid=" + selection.id
                + ", currentVcode=" + currentVcode + ", captcha=" + captchaUrl);
    }

    /**
     * Защитный no-captcha fallback: если сервер отдал `act=1` без капчи, но JS-ветка map.js
     * не отправила `act=2`, через короткую задержку отправляем `act=2` принудительно.
     *
     * Зависимости:
     * - `lastFishAct2AtMs`: контроль, что после текущего `act=1` уже пришёл `act=2` и дубль не нужен;
     * - `MainActivity.getMainWebView()` + JS `AjaxGet(...)`: нативный fallback остаётся в игровом ajax-контуре;
     * - `isAutoFishEnabled()`: не отправляем запрос после ручного выключения авто-рыбалки.
     */
    private static void scheduleNoCaptchaAct2Fallback(String vcode, String primid, long act1AtMs) {
        if (vcode == null || vcode.isEmpty() || primid == null || primid.isEmpty()) {
            return;
        }
        String safeVcode = vcode.replaceAll("[^a-zA-Z0-9]", "");
        String safePrimid = primid.replaceAll("[^0-9]", "");
        if (safeVcode.isEmpty() || safePrimid.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        String key = safePrimid + "|" + safeVcode;
        if (key.equals(lastFishNoCaptchaFallbackKey)
                && now - lastFishNoCaptchaFallbackAtMs < FISH_NO_CAPTCHA_FALLBACK_DEDUP_MS) {
            return;
        }
        lastFishNoCaptchaFallbackKey = key;
        lastFishNoCaptchaFallbackAtMs = now;

        MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(() -> {
            WebView webView = activity.getMainWebView();
            if (webView == null) {
                return;
            }
            webView.postDelayed(() -> {
                if (!isAutoFishEnabled()) {
                    return;
                }
                if (lastFishAct2AtMs >= act1AtMs) {
                    AppLog.d(TAG, "AUTO_FISH_TRACE no-captcha fallback skip: act2 already seen, primid="
                            + safePrimid + ", vcode=" + safeVcode);
                    return;
                }

                String js = "(function(){"
                        + "try{"
                        + " if(typeof AjaxGet==='function'){"
                        + "   AjaxGet('fish_ajax.php?act=2&primid=" + safePrimid + "&vcode=" + safeVcode + "&r='+Math.random());"
                        + "   return 'ajax';"
                        + " }"
                        + " if(typeof FishStart==='function'){ FishStart('" + safeVcode + "',0); return 'fishstart'; }"
                        + "}catch(e){ return 'err:'+e; }"
                        + "return 'miss';"
                        + "})()";
                webView.evaluateJavascript(js, value -> {
                    AppLog.d(TAG, "AUTO_FISH_TRACE no-captcha fallback submit result=" + value
                            + ", primid=" + safePrimid + ", vcode=" + safeVcode);
                    boolean jsOk = value != null
                            && (value.contains("ajax") || value.contains("fishstart"));
                    if (jsOk) {
                        return;
                    }
                    // Get fresh VCode from SessionManager for fallback request
                    String freshVcode = SessionManager.getInstance().getValidVCodeForAction("fish_act2_fallback");
                    if (freshVcode == null || freshVcode.isEmpty()) {
                        AppLog.w(TAG, "AUTO_FISH_TRACE no-captcha fallback: no VCode available, skipping loadUrl");
                        return;
                    }
                    String url = "http://neverlands.ru/gameplay/ajax/fish_ajax.php?act=2"
                            + "&primid=" + safePrimid
                            + "&vcode=" + freshVcode
                            + "&r=" + System.currentTimeMillis();
                    AppLog.d(TAG, "AUTO_FISH_TRACE no-captcha fallback loadUrl: " + url + " (freshVcode)");
                    webView.loadUrl(url);
                });
            }, FISH_NO_CAPTCHA_FALLBACK_DELAY_MS);
        });
    }

    /**
     * Единая проверка состояния AutoFish (runtime-менеджер + fallback на профиль).
     */
    public static boolean isAutoFishEnabled() {
        try {
            if (AppVars.getContext() != null) {
                return AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoFishEnabled();
            }
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_FISH_TRACE: failed to read AutoFish from manager", e);
        }
        return AppVars.Profile != null && AppVars.Profile.AutoFish;
    }

    /**
     * Дедуп-показ fish captcha popup через broadcast-контракт `ACTION_SHOW_CAPTCHA`.
     */
    private static void showFishCaptchaDialogOnce(String captchaUrl, String finishUrl) {
        if (captchaUrl == null || captchaUrl.isEmpty() || finishUrl == null || finishUrl.isEmpty()) {
            return;
        }
        if (AppVars.getContext() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        String key = captchaUrl + "|" + finishUrl;
        if (key.equals(lastFishCaptchaDialogKey)
                && now - lastFishCaptchaDialogAtMs < FISH_CAPTCHA_DIALOG_DEDUP_MS) {
            AppLog.d(TAG, "AUTO_FISH_TRACE act1: captcha dedup skip");
            return;
        }
        lastFishCaptchaDialogKey = key;
        lastFishCaptchaDialogAtMs = now;

        Intent intent = new Intent(AppVars.ACTION_SHOW_CAPTCHA);
        intent.putExtra("captchaUrl", captchaUrl);
        intent.putExtra("finishUrl", finishUrl);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }

    /**
     * Синхронизирует cooldown после `fish_ajax.php?act=2` и планирует следующий цикл авто-рыбалки.
     *
     * Зависимости:
     * - серверный payload: маркер `@[0,[2,<sec>]]@` со временем до следующего заброса;
     * - `AppVars.NeverTimer`: общий cooldown-гейт для авто-функций в `MainPhp`;
     * - `MainActivity.getMainWebView().loadUrl(...)`: гарантированный restart main.php,
     *   чтобы снова прошла ветка `AUTO_FISH_TRACE` и ушёл следующий `act=1`.
     */
    private static void syncFishCooldownAndScheduleNextCycle(String html) {
        int cooldownSec = extractFishCooldownSec(html);
        if (cooldownSec <= 0) {
            return;
        }

        // JS TimerStart() СКЛАДЫВАЕТ таймеры: act=1(section[4]) + act=2(section[4]).
        // Реальный общий таймер цикла = act1 + act2 (напр. 30+30=60, 30+291=321).
        int act1Timer = lastAct1TimerSec;
        int totalTimerSec = act1Timer + cooldownSec;
        // Обновляем fishingExpectedDurationMs с реальным серверным таймером + запас
        AppVars.fishingExpectedDurationMs = (totalTimerSec * 1000L) + 15_000L;
        AppLog.d(TAG, "AUTO_FISH_TRACE act2 timer: act1=" + act1Timer + "s + act2=" + cooldownSec
                + "s = total " + totalTimerSec + "s → fishingExpectedDurationMs="
                + AppVars.fishingExpectedDurationMs + "ms");

        long nowMs = System.currentTimeMillis();
        long dueAtMs = nowMs + (cooldownSec * 1000L);
        long prevNeverTimerMs = AppVars.NeverTimer;
        // Важно: cooldown из act=2 не должен понижать уже установленный серверный гейт из map.js.
        long effectiveDueAtMs = Math.max(dueAtMs, prevNeverTimerMs);
        AppVars.NeverTimer = effectiveDueAtMs;

        if (!isAutoFishEnabled()) {
            return;
        }

        if (Math.abs(effectiveDueAtMs - lastFishAutoreloadDueAtMs) <= 1000L
                && (nowMs - lastFishAutoreloadAtMs) < FISH_AUTORELOAD_DEDUP_MS) {
            return;
        }
        lastFishAutoreloadAtMs = nowMs;
        lastFishAutoreloadDueAtMs = effectiveDueAtMs;
        lastFishCycleToken = effectiveDueAtMs;

        MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
        if (activity == null) {
            return;
        }
        WebView webView = activity.getMainWebView();
        if (webView == null) {
            return;
        }

        long delayMs = Math.max(250L, effectiveDueAtMs - nowMs + FISH_AUTORELOAD_SAFETY_MS);
        long cycleToken = effectiveDueAtMs;
        AppLog.d(TAG, "AUTO_FISH_TRACE act2 cooldown=" + cooldownSec + "s, prevNeverTimerDelta="
                + Math.max(0L, prevNeverTimerMs - nowMs) + "ms, schedule next cycle in " + delayMs + "ms");
        activity.runOnUiThread(() -> webView.postDelayed(
                () -> kickFishCycleAttempt(cycleToken, 1),
                delayMs));
    }

    /**
     * Извлекает числовой cooldown рыбалки из ответа (act=1 или act=2).
     * Парсит section[4] через split('@'), а не regex по всему HTML —
     * это надёжнее при модификации section[1] в fishReport().
     *
     * Формат section[4]: `[N,[2,timer]]` где timer = секунды.
     * N может быть 0 или 1 — на парсинг не влияет.
     *
     * Возвращает:
     * - секунды таймера из section[4];
     * - `0`, если маркер не найден или формат некорректный.
     */
    private static int extractFishCooldownSec(String html) {
        if (html == null || html.isEmpty()) {
            return 0;
        }
        // Основной путь: split по '@', парсим section[4] напрямую
        String[] sections = html.split("@", -1);
        if (sections.length > 4) {
            String sec4 = sections[4].trim();
            Matcher m = Pattern.compile("\\[2,(\\d+)\\]").matcher(sec4);
            if (m.find()) {
                int val = ParseUtils.parseIntSafe(m.group(1));
                AppLog.d(TAG, "AUTO_FISH_TRACE extractCooldown: section[4]=" + sec4
                        + " → " + val + "s (sections=" + sections.length + ")");
                return val;
            }
            AppLog.w(TAG, "AUTO_FISH_TRACE extractCooldown: section[4]=" + sec4
                    + " → parse failed (sections=" + sections.length + ")");
        }
        // Fallback: regex по полному HTML
        Matcher matcher = FISH_COOLDOWN_PATTERN.matcher(html);
        if (!matcher.find()) {
            AppLog.w(TAG, "AUTO_FISH_TRACE extractCooldown: no match in " + html.length() + " chars");
            return 0;
        }
        int val = ParseUtils.parseIntSafe(matcher.group(1));
        AppLog.d(TAG, "AUTO_FISH_TRACE extractCooldown: fallback regex → " + val + "s");
        return val;
    }

    /**
     * Делает попытку стартовать следующий цикл рыбалки после истечения cooldown.
     *
     * Зависимости:
     * - `lastFishAct1AtMs`: маркер, что новый `act=1` уже пошёл (повтор не нужен);
     * - `lastFishCycleToken`: защита от устаревших отложенных задач;
     * - `MainActivity.getMainWebView()`: выполняем JS-старт (`FishStart`/`fishbutton`) и fallback `loadUrl(main.php)`.
     */
    private static void kickFishCycleAttempt(long cycleToken, int attempt) {
        if (!isAutoFishEnabled()) {
            return;
        }
        if (cycleToken != lastFishCycleToken) {
            return;
        }

        MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(() -> {
            WebView webView = activity.getMainWebView();
            if (webView == null) {
                return;
            }

            long nowMs = System.currentTimeMillis();
            long timerGateMs = AppVars.NeverTimer;
            if (isFightLikelyActiveForFishCycle()) {
                AppLog.d(TAG, "AUTO_FISH_TRACE cycle gate by fight markers, wait="
                        + FISH_FIGHT_GUARD_DELAY_MS + "ms, attempt=" + attempt + ", token=" + cycleToken);
                // После снятия боевых маркеров нужна свежая InfoApi-проверка снастей:
                // сброс кэша гарантирует preflight-путь и shouldBypassCooldown=true.
                AppVars.AutoFishCheckUd = true;
                lastAutoFishInfoApiPrecheckAtMs = 0L;
                webView.postDelayed(() -> kickFishCycleAttempt(cycleToken, attempt), FISH_FIGHT_GUARD_DELAY_MS);
                return;
            }
            if (timerGateMs > nowMs + 250L) {
                long waitMs = Math.max(300L, timerGateMs - nowMs + FISH_AUTORELOAD_SAFETY_MS);
                AppLog.d(TAG, "AUTO_FISH_TRACE cycle gate by NeverTimer, wait=" + waitMs
                        + "ms, attempt=" + attempt + ", token=" + cycleToken);
                webView.postDelayed(() -> kickFishCycleAttempt(cycleToken, attempt), waitMs);
                return;
            }

            // ✅ Ранняя защита от race condition: установить флаг ДО отправки HTTP-запроса
            // Это предотвратит SERVER_TIMER_TICK от перезагрузки main.php пока идет act=1
            long attemptStartedAtMs = System.currentTimeMillis();
            AutoFishInfoApiPrecheckState precheckState =
                    mainPhpBuildAutoFishCachedPrecheckState(
                            "fish_cycle_attempt",
                            "fishajax_cycle_precast");
            if (precheckState.shouldRouteViaMainPhp()) {
                AppVars.suppressBackgroundProbesDuringFishing = true;
                AppVars.fishingSequenceStartAtMs = attemptStartedAtMs;
                AppLog.d(TAG, "AUTO_FISH_TRACE preflight suppression enabled, token=" + cycleToken);
                String freshVcode = SessionManager.getInstance().getValidVCodeForAction("fish_cycle_preflight");
                String preflightUrl;
                if (freshVcode != null && !freshVcode.isEmpty()) {
                    preflightUrl = "http://neverlands.ru/main.php?af_cycle=1&af_preflight=1&vcode="
                            + freshVcode + "&r=" + System.currentTimeMillis();
                } else {
                    preflightUrl = "http://neverlands.ru/main.php?af_cycle=1&af_preflight=1&r="
                            + System.currentTimeMillis();
                }
                AppLog.d(TAG, "AUTO_FISH_TRACE cycle preflight route via main.php: mustWear="
                        + precheckState.mustWear
                        + ", needFatigueStep=" + precheckState.needFatigueStep
                        + ", tied=" + precheckState.tied
                        + ", tiedThreshold=" + precheckState.tiedThreshold
                        + ", url=" + preflightUrl);
                webView.loadUrl(preflightUrl);
                webView.postDelayed(() -> {
                    if (!isAutoFishEnabled()) {
                        return;
                    }
                    if (cycleToken != lastFishCycleToken) {
                        return;
                    }
                    if (lastFishAct1AtMs >= attemptStartedAtMs) {
                        AppLog.d(TAG, TAG, "AUTO_FISH_TRACE preflight confirmed by new act=1, attempt=" + attempt);
                        return;
                    }
                    if (attempt >= FISH_CYCLE_MAX_ATTEMPTS) {
                        AppLog.w(TAG, TAG, "AUTO_FISH_TRACE preflight exhausted attempts=" + attempt + ", token=" + cycleToken);
                        requestAutoFishBootstrap("preflight_exhausted");
                        // Если сервер вернётся за 60с — новый kickFishCycleAttempt возобновит цикл
                        // без ожидания внешнего af_tick или ручного перезапуска.
                        scheduleFreshFishCycleKick(60_000L);
                        return;
                    }
                    AppLog.d(TAG, TAG, "AUTO_FISH_TRACE preflight retry " + (attempt + 1) + "/" + FISH_CYCLE_MAX_ATTEMPTS);
                    kickFishCycleAttempt(cycleToken, attempt + 1);
                }, FISH_CYCLE_RETRY_DELAY_MS);
                return;
            }

            AppVars.suppressBackgroundProbesDuringFishing = true;
            AppVars.fishingSequenceStartAtMs = attemptStartedAtMs;
            AppLog.d(TAG, "AUTO_FISH_TRACE early suppression enabled at cycle start, token=" + cycleToken);
            String jsKick = "(function(){"
                    + "try{"
                    + "  if(typeof ButClick==='function' && document.getElementById('fis')){ButClick('fis'); return 'open_fish_by_button';}"
                    + "  if(typeof Fish==='function' && typeof bavail!=='undefined' && bavail && bavail['fis'] && bavail['fis'][0]){Fish(bavail['fis'][0]); return 'open_fish_by_vcode';}"
                    + "  var form=document.getElementById('FISHF');"
                    + "  var btn=document.getElementById('fishbutton');"
                    + "  if(form && btn && typeof btn.click==='function'){btn.click(); return 'submit_fish_button';}"
                    + "  if(typeof FishStart==='function' && typeof ingr!=='undefined' && ingr && ingr.length>2){FishStart(ingr[2],0); return 'submit_fish_start';}"
                    + "}catch(e){return 'err:'+e;}"
                    + "return 'miss';"
                    + "})()";

            webView.evaluateJavascript(jsKick, value -> {
                boolean kickedViaJs = value != null
                        && (value.contains("open_fish_by_button")
                        || value.contains("open_fish_by_vcode")
                        || value.contains("submit_fish_button")
                        || value.contains("submit_fish_start"));
                if (kickedViaJs) {
                    AppLog.d(TAG, "AUTO_FISH_TRACE cycle kick via JS, attempt=" + attempt + ", result=" + value);
                    return;
                }

                // Get fresh VCode from SessionManager for cycle kick fallback
                String freshVcode = SessionManager.getInstance().getValidVCodeForAction("fish_cycle_kick");
                String reloadUrl;
                if (freshVcode != null && !freshVcode.isEmpty()) {
                    reloadUrl = "http://neverlands.ru/main.php?af_cycle=1&vcode=" + freshVcode + "&r=" + System.currentTimeMillis();
                    AppLog.d(TAG, TAG, "AUTO_FISH_TRACE cycle kick fallback with fresh vcode");
                } else {
                    reloadUrl = "http://neverlands.ru/main.php?af_cycle=1&r=" + System.currentTimeMillis();
                    AppLog.w(TAG, TAG, "AUTO_FISH_TRACE cycle kick fallback without vcode");
                }
                AppLog.d(TAG, TAG, "AUTO_FISH_TRACE cycle kick fallback reload, attempt=" + attempt
                        + ", jsResult=" + value + ", url=" + reloadUrl);
                webView.loadUrl(reloadUrl);
            });

            webView.postDelayed(() -> {
                if (!isAutoFishEnabled()) {
                    return;
                }
                if (cycleToken != lastFishCycleToken) {
                    return;
                }
                if (lastFishAct1AtMs >= attemptStartedAtMs) {
                    AppLog.d(TAG, TAG, "AUTO_FISH_TRACE cycle kick confirmed by new act=1, attempt=" + attempt);
                    return;
                }
                if (attempt >= FISH_CYCLE_MAX_ATTEMPTS) {
                    AppLog.w(TAG, TAG, "AUTO_FISH_TRACE cycle kick exhausted attempts=" + attempt + ", token=" + cycleToken);
                    requestAutoFishBootstrap("cycle_exhausted");
                    return;
                }
                AppLog.d(TAG, TAG, "AUTO_FISH_TRACE cycle kick retry " + (attempt + 1) + "/" + FISH_CYCLE_MAX_ATTEMPTS);
                kickFishCycleAttempt(cycleToken, attempt + 1);
            }, FISH_CYCLE_RETRY_DELAY_MS);
        });
    }

    /**
     * Регистрирует серию ERR-ответов act=1 и запускает безопасный recovery-bootstrap,
     * если подряд пришло несколько ошибок.
     */
    private static int registerAct1ErrAndMaybeRecover(String reason) {
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastFishAct1ErrAtMs > 60_000L) {
            consecutiveFishAct1ErrCount = 0;
        }
        lastFishAct1ErrAtMs = nowMs;
        consecutiveFishAct1ErrCount++;
        if (consecutiveFishAct1ErrCount >= FISH_ACT1_ERR_RECOVERY_THRESHOLD) {
            requestAutoFishBootstrap(reason + "_x" + consecutiveFishAct1ErrCount);
        }
        return consecutiveFishAct1ErrCount;
    }

    /**
     * Сбрасывает счетчик ERR-ответов после валидного act=1.
     */
    private static void resetAct1ErrRecoveryState() {
        consecutiveFishAct1ErrCount = 0;
        lastFishAct1ErrAtMs = 0L;
    }

    /**
     * Мягкий перезапуск fish-цепочки (инициирует новый act=1 для обновления vcode).
     * Используется при ошибке "неверный код защиты" - не требует полной перезагрузки main.php.
     */
    private static void requestSoftAutoFishRecovery(String reason) {
        if (!isAutoFishEnabled()) {
            return;
        }
        MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
        if (activity == null) {
            return;
        }
        String safeReason = (reason == null) ? "unknown" : reason.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        activity.runOnUiThread(() -> {
            WebView webView = activity.getMainWebView();
            if (webView == null) {
                return;
            }
            // Get fresh VCode from SessionManager for soft recovery
            String freshVcode = SessionManager.getInstance().getValidVCodeForAction("fish_recovery");
            // Отправляем простой refresh игровой страницы для инициации нового act=1
            // Это мягче, чем полный bootstrap через main.php?go=inf
            String reloadUrl = "http://neverlands.ru/main.php?get_id=56&act=10";
            if (freshVcode != null && !freshVcode.isEmpty()) {
                reloadUrl += "&vcode=" + freshVcode;
                AppLog.d(TAG, TAG, "AUTO_FISH_TRACE soft recovery with fresh vcode");
            } else {
                AppLog.w(TAG, TAG, "AUTO_FISH_TRACE soft recovery without vcode");
            }
            reloadUrl += "&r=" + System.currentTimeMillis();
            AppLog.d(TAG, TAG, "AUTO_FISH_TRACE soft recovery after " + safeReason + ": " + reloadUrl);
            webView.loadUrl(reloadUrl);
        });
    }

    /**
     * Принудительно перезапускает fish-цепочку через main.php?go=inf.
     * Используется как recovery после серии ERR/stale-vcode.
     */
    static void requestAutoFishBootstrap(String reason) {
        if (!isAutoFishEnabled()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastFishBootstrapAtMs < FISH_BOOTSTRAP_DEDUP_MS) {
            return;
        }
        lastFishBootstrapAtMs = nowMs;

        MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
        if (activity == null) {
            return;
        }
        String safeReason = (reason == null) ? "unknown" : reason.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        activity.runOnUiThread(() -> {
            WebView webView = activity.getMainWebView();
            if (webView == null) {
                return;
            }
            StringBuilder url = new StringBuilder("http://neverlands.ru/main.php?get_id=56&act=10&go=inf&af_bootstrap=1&af_recover=1");
            // ✅ SessionManager: получаем валидный vcode для recovery запроса
            String vcode = ru.neverlands.anclient.utils.SessionManager.getInstance()
                    .getValidVCodeForAction("fish_recovery");
            if (vcode != null && !vcode.isEmpty()) {
                url.append("&vcode=").append(vcode);
            } else {
                AppLog.w(TAG, "⚠️ AUTO_FISH_TRACE recovery bootstrap: vcode not available from SessionManager");
            }
            url.append("&reason=").append(safeReason);
            url.append("&ts=").append(System.currentTimeMillis());
            AppLog.d(TAG, "AUTO_FISH_TRACE recovery bootstrap: " + url);
            webView.loadUrl(url.toString());
        });
    }

    /**
     * Сбрасывает cycle-token и планирует свежий kickFishCycleAttempt через {@code delayMs}.
     *
     * После любого сбоя (hard-stop no-gear, elixir, preflight-exhausted) цикл нужно перезапустить
     * именно через kickFishCycleAttempt, а не через bootstrap, потому что только он загружает URL
     * с af_preflight=1, что является обязательным условием для shouldBypassCooldown=true в
     * mainPhpPrecheckFishingHandsByInfoApi. Без bypass InfoApi throttled → gear-wear chain мертва.
     */
    private static void scheduleFreshFishCycleKick(long delayMs) {
        if (!isAutoFishEnabled()) return;
        long newToken = System.currentTimeMillis();
        lastFishCycleToken = newToken;
        AppVars.NeverTimer = 0L;
        String msg = "AUTO_FISH_TRACE scheduleFreshFishCycleKick: newToken=" + newToken + ", delayMs=" + delayMs;
        AppLog.d(TAG, msg);
        MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
        if (activity == null) {
            AppLog.w(TAG, "AUTO_FISH_TRACE scheduleFreshFishCycleKick: activity=null, skip");
            return;
        }
        activity.runOnUiThread(() -> {
            WebView webView = activity.getMainWebView();
            if (webView != null) {
                webView.postDelayed(() -> kickFishCycleAttempt(newToken, 1), delayMs);
            }
        });
    }

    /**
     * Короткий guard: не стартуем fish-cycle, пока в рантайме видны свежие маркеры активного боя.
     */
    private static boolean isFightLikelyActiveForFishCycle() {
        if (AppVars.IsFightCaptchaDialogVisible) {
            return true;
        }
        long nowMs = System.currentTimeMillis();
        if (AppVars.LastFightPulseAtMs > 0L && (nowMs - AppVars.LastFightPulseAtMs) < FISH_FIGHT_PULSE_GUARD_MS) {
            return true;
        }
        String html = AppVars.ContentMainPhp;
        if (html == null || html.isEmpty()) {
            return false;
        }
        return html.contains("var fight_ty") || html.contains("magic_slots();");
    }

    /**
     * Серверный маркер ошибки: act=2 отклонен из-за неверного/устаревшего challenge-кода.
     * Для no-captcha режима это сигнал к перезапуску fish-bootstrap и получению нового vcode.
     */
    private static boolean containsFishWrongProtectionCode(String lowerHtml) {
        if (lowerHtml == null || lowerHtml.isEmpty()) {
            return false;
        }
        return lowerHtml.contains("неверный код защиты")
                || lowerHtml.contains("код защиты введен неверно")
                || lowerHtml.contains("неправильный код защиты");
    }

    /**
     * Парсит технический payload `act=1` для получения captcha/vcode/массы/списка приманок.
     */
    /**
     * Разбирает технический payload `fish_ajax.php?act=1` в структурированное состояние.
     *
     * Что извлекаем:
     * - captcha-token и `vcode` из заголовка `[1,"captcha","vcode",massCur,massMax,...]`;
     * - массу инвентаря (`massCurrent/massMax`);
     * - список доступных приманок `[primid,name,count]`.
     *
     * Зависимости:
     * - формат протокола Neverlands `RESO@...@[]@[1,...]`;
     * - `cleanNumeric(...)` для полей массы;
     * - `parseIntSafe(...)` для количества приманок;
     * - `FishBaitSelection` как DTO результата.
     *
     * Возвращает `null`, если payload не соответствует ожидаемому формату.
     */
    private static FishAct1State parseFishAct1State(String html) {
        if (html == null || html.isEmpty()) {
            String msg = "AUTO_FISH_TRACE parseFishAct1State: html is null or empty";
            AppLog.w(TAG, TAG, msg);
            return null;
        }

        // Логируем первые 500 символов для диагностики
        String htmlPreview = html.length() > 500 ? html.substring(0, 500) + "..." : html;
        String msg1 = "AUTO_FISH_TRACE parseFishAct1State: input html=" + htmlPreview;
        AppLog.d(TAG, TAG, msg1);

        String[] sections = html.split("@");
        String msg2 = "AUTO_FISH_TRACE parseFishAct1State: split into " + sections.length + " sections";
        AppLog.d(TAG, TAG, msg2);
        
        for (int i = 0; i < sections.length && i < 10; i++) {
            String preview = sections[i].length() > 100 ? sections[i].substring(0, 100) + "..." : sections[i];
            String sectionMsg = "AUTO_FISH_TRACE section[" + i + "]=" + preview;
            AppLog.d(TAG, TAG, sectionMsg);
        }
        
        if (sections.length < 6) {
            String errMsg = "AUTO_FISH_TRACE parseFishAct1State: ❌ REJECTED expected 6+ sections, got " + sections.length;
            AppLog.w(TAG, TAG, errMsg);
            return null;
        }

        // Парсим section[4] = [N,[2,timer]] — таймер act=1 (JS TimerStart СКЛАДЫВАЕТ act=1 + act=2)
        String sec4 = sections[4] == null ? "" : sections[4].trim();
        Matcher timerMatcher = Pattern.compile("\\[2,(\\d+)\\]").matcher(sec4);
        if (timerMatcher.find()) {
            lastAct1TimerSec = ParseUtils.parseIntSafe(timerMatcher.group(1));
            AppLog.d(TAG, TAG, "AUTO_FISH_TRACE act1 section[4]=" + sec4
                    + " → timer=" + lastAct1TimerSec + "s (JS adds act1+act2)");
        } else {
            lastAct1TimerSec = 0;
            AppLog.w(TAG, TAG, "AUTO_FISH_TRACE act1 section[4]=" + sec4 + " → timer parse failed");
        }

        String payload = sections[5] == null ? "" : sections[5].trim();
        String payloadMsg = "AUTO_FISH_TRACE parseFishAct1State: payload (section[5])=" + payload;
        AppLog.d(TAG, TAG, payloadMsg);
        
        if (!payload.startsWith("[1,")) {
            String errMsg2 = "AUTO_FISH_TRACE parseFishAct1State: ❌ REJECTED payload does not start with [1,";
            AppLog.w(TAG, TAG, errMsg2);
            return null;
        }

        Matcher header = Pattern.compile(
                "^\\[1,\\s*\"([^\"]*)\",\\s*\"([^\"]*)\",\\s*([^,\\]]+),\\s*([^,\\]]+)",
                Pattern.DOTALL).matcher(payload);
        if (!header.find()) {
            String errMsg3 = "AUTO_FISH_TRACE parseFishAct1State: ❌ REJECTED header regex failed";
            AppLog.w(TAG, TAG, errMsg3);
            return null;
        }

        FishAct1State state = new FishAct1State();
        state.captchaToken = header.group(1) == null ? "" : header.group(1).trim();
        state.vcode = header.group(2) == null ? "" : header.group(2).trim();
        state.massCurrent = cleanNumeric(header.group(3));
        state.massMax = cleanNumeric(header.group(4));

        String headerMsg = "AUTO_FISH_TRACE parseFishAct1State: ✅ APPROVED captcha=" + state.captchaToken 
                + ", vcode=" + state.vcode + ", mass=" + state.massCurrent + "/" + state.massMax;
        AppLog.d(TAG, TAG, headerMsg);

        Matcher baitMatcher = Pattern.compile("\\[(38|39|40|41|42|43|44|45|46),\\s*\"([^\"]+)\",\\s*(\\d+)\\]")
                .matcher(payload);
        while (baitMatcher.find()) {
            String id = baitMatcher.group(1);
            String name = baitMatcher.group(2);
            int count = ParseUtils.parseIntSafe(baitMatcher.group(3));
            state.baits.add(new FishBaitSelection(id, name, count));
            String baitMsg = "AUTO_FISH_TRACE parseFishAct1State: found bait id=" + id + ", name=" + name + ", count=" + count;
            AppLog.d(TAG, TAG, baitMsg);
        }
        
        String finalMsg = "AUTO_FISH_TRACE parseFishAct1State: ✅ SUCCESS total baits=" + state.baits.size();
        AppLog.d(TAG, TAG, finalMsg);
        return state;
    }

    /**
     * Выбирает первую доступную приманку с остатком > 4 (C# parity `CheckPri`).
     */
    /**
     * Выбирает приманку для текущего заброса по правилам ПК-версии (`CheckPri` parity).
     *
     * Правила:
     * - остаток приманки должен быть `> 4`;
     * - приманка должна быть разрешена в профиле игрока (`FishEnabledPrims`).
     *
     * Зависимости:
     * - `AppVars.Profile.FishEnabledPrims` (битовая маска `Prims`);
     * - `isBaitEnabledInProfile(...)` для проверки конкретного `primid`.
     *
     * Возвращает первую подходящую приманку в порядке, который прислал сервер.
     */
    private static FishBaitSelection selectAllowedBait(List<FishBaitSelection> baits) {
        if (baits == null || baits.isEmpty() || AppVars.Profile == null) {
            String msg = "AUTO_FISH_TRACE selectAllowedBait: ❌ REJECTED baits null/empty or Profile null";
            AppLog.w(TAG, TAG, msg);
            return null;
        }
        
        String baitListMsg = "AUTO_FISH_TRACE selectAllowedBait: checking " + baits.size() + " baits, profile mask=" + AppVars.Profile.FishEnabledPrims;
        AppLog.d(TAG, TAG, baitListMsg);
        
        for (FishBaitSelection bait : baits) {
            if (bait == null) {
                String nullMsg = "AUTO_FISH_TRACE selectAllowedBait: ⚠️ bait is null, skipping";
                AppLog.d(TAG, TAG, nullMsg);
                continue;
            }
            
            if (bait.count <= 4) {
                String countMsg = "AUTO_FISH_TRACE selectAllowedBait: ❌ id=" + bait.id + " name=" + bait.name + " count=" + bait.count + " <= 4, skipping";
                AppLog.d(TAG, TAG, countMsg);
                continue;
            }
            
            boolean enabled = isBaitEnabledInProfile(bait.id);
            String checkMsg = "AUTO_FISH_TRACE selectAllowedBait: checking id=" + bait.id + " name=" + bait.name + " count=" + bait.count + " enabled=" + enabled;
            AppLog.d(TAG, TAG, checkMsg);
            
            if (enabled) {
                String selectedMsg = "AUTO_FISH_TRACE selectAllowedBait: ✅ SELECTED id=" + bait.id + " name=" + bait.name + " count=" + bait.count;
                AppLog.i(TAG, TAG, selectedMsg);
                return bait;
            }
        }
        
        String noSelectionMsg = "AUTO_FISH_TRACE selectAllowedBait: ❌ REJECTED no suitable bait found";
        AppLog.w(TAG, TAG, noSelectionMsg);
        return null;
    }

    /**
     * Проверка битмаски разрешённых приманок (`Prims`) по server-id.
     */
    /**
     * Маппит server `primid` на флаги профиля `Prims` и проверяет, разрешена ли приманка.
     *
     * Зависимости:
     * - `AppVars.Profile.FishEnabledPrims` (битовая маска настроек);
     * - константы `Prims` (Bread/Worm/BigWorm/.../HiFlight);
     * - используется в `selectAllowedBait(...)`.
     */
    private static boolean isBaitEnabledInProfile(String baitId) {
        if (AppVars.Profile == null || baitId == null || baitId.isEmpty()) {
            return false;
        }

        int mask = AppVars.Profile.FishEnabledPrims;
        switch (baitId) {
            case "38":
                return (mask & Prims.Bread) != 0;
            case "39":
                return (mask & Prims.Worm) != 0;
            case "40":
                return (mask & Prims.BigWorm) != 0;
            case "41":
                return (mask & Prims.Stink) != 0;
            case "42":
                return (mask & Prims.Fly) != 0;
            case "43":
                return (mask & Prims.Light) != 0;
            case "44":
                return (mask & Prims.Donka) != 0;
            case "45":
                return (mask & Prims.Morm) != 0;
            case "46":
                return (mask & Prims.HiFlight) != 0;
            default:
                return false;
        }
    }

    /**
     * Нормализует число из payload (`395.00`, `437`) в строку для `AutoFishMassa`.
     */
    /**
     * Нормализует числовые фрагменты payload (масса, лимиты) до безопасной строки числа.
     *
     * Зависимости:
     * - формат `act=1` payload, где значения могут приходить с шумом/разделителями;
     * - используется только в `parseFishAct1State(...)`.
     *
     * В результате оставляет только `0-9`, `,`, `.`, `-`.
     */
    private static String cleanNumeric(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[^0-9.,\\-]", "");
    }

    /**
     * Проверяет, можно ли отправить наживку (act=2) без перегруза инвентаря.
     * 
     * Правило: если массу инвентаря после заброска может превысить 85% от максимума,
     * отправка блокируется, и система должна инициировать смену удочки.
     * 
     * Пороги (адаптированы из C#):
     * - WARNING: 85% (осторожная зона, предотвращает вброс в бой)
     * - CRITICAL: 95% (абсолютный лимит)
     * 
     * @param massCurrent текущая масса инвентаря (строка, может быть "1225.02")
     * @param massMax максимальная масса (строка, может быть "1405")
     * @param baitMass масса выбранной наживки (факт из FishData)
     * @return true = можно отправлять act=2; false = нужна смена удочки/остановка
     */
    private static boolean checkMassBeforeCasting(String massCurrent, String massMax, double baitMass) {
        try {
            // Парсим массовые значения
            double currentMass = massCurrent.isEmpty() ? 0 : Double.parseDouble(massCurrent.replace(",", "."));
            double maxMass = massMax.isEmpty() ? 1405 : Double.parseDouble(massMax.replace(",", "."));
            
            double newMassAfterCast = currentMass + baitMass;
            double percentUsed = (newMassAfterCast / maxMass) * 100;
            
            // Пороги проверки
            final double MASS_WARNING_THRESHOLD = 85.0;
            final double MASS_CRITICAL_THRESHOLD = 95.0;
            
            FileLogger.trace(TAG, String.format(
                "[FISH_MASS_CHECK] Current: %.2f, Max: %.2f, Bait: %.2f, " +
                "Will be: %.2f (%.1f%%)",
                currentMass, maxMass, baitMass, newMassAfterCast, percentUsed));
            
            if (percentUsed >= MASS_CRITICAL_THRESHOLD) {
                // КРИТИЧЕСКИЙ уровень - инвентарь практически полный
                AppLog.w(TAG, String.format(
                    "❌ FISH_MASS_CRITICAL: %%.1f%% usage detected (%.2f/%.2f). " +
                    "Bait cast blocked - would cause inventory overflow!",
                    percentUsed, newMassAfterCast, maxMass));
                FileLogger.trace(TAG, String.format(
                    "[FISH_MASS_REJECT] CRITICAL: %.1f%% > 95%%. Blocking act=2. " +
                    "Current=%.2f, +Bait=%.2f, Max=%.2f",
                    percentUsed, currentMass, baitMass, maxMass));
                return false;
            }
            
            if (percentUsed >= MASS_WARNING_THRESHOLD) {
                // ПРЕДУПРЕДИТЕЛЬНЫЙ уровень - велик шанс вброса в бой сервером
                AppLog.w(TAG, String.format(
                    "⚠️ FISH_MASS_WARNING: %.1f%% usage detected (%.2f/%.2f). " +
                    "Approaching server overflow threshold - initiating rod replacement.",
                    percentUsed, newMassAfterCast, maxMass));
                FileLogger.trace(TAG, String.format(
                    "[FISH_MASS_WARN] WARNING: %.1f%% in [85%%,95%%]. Blocking act=2. " +
                    "Recommend rod replacement. Current=%.2f, +Bait=%.2f, Max=%.2f",
                    percentUsed, currentMass, baitMass, maxMass));
                return false;
            }
            
            // Масса в норме
            AppLog.d(TAG, String.format(
                "✅ FISH_MASS_OK: %.1f%% usage (%.2f/%.2f). Bait cast permissible.",
                percentUsed, newMassAfterCast, maxMass));
            return true;
            
        } catch (NumberFormatException e) {
            // Если не можем распарсить массу - позволяем заброс (fallback)
            AppLog.w(TAG, "[FISH_MASS_CHECK] Parse error for mass values, allowing cast as fallback: " + e.getMessage());
            return true;
        }
    }

    /**
     * Собирает строку отчета по улову и синхронизирует экономику/умелку авто-рыбалки.
     *
     * Зависимости:
     * - {@link UserConfig#FishUm}, {@link UserConfig#FishChatReport}, {@link UserConfig#FishChatReportColor};
     * - runtime-поля {@link AppVars#AutoFishLikeId}, {@link AppVars#AutoFishLikeVal}, {@link AppVars#AutoFishMassa};
     * - перевод результата в чат через {@link #pushChatMessage(String)}.
     */
    private static String fishReport(String html) {
        int p1 = html.indexOf('«');
        int p2 = p1 >= 0 ? html.indexOf('»', p1 + 1) : -1;
        if (p1 < 0 || p2 <= p1) {
            return "";
        }

        String fishName = html.substring(p1 + 1, p2);
        int fishCatch = ParseUtils.parseIntSafe(HelperStrings.subString(html, "Клёв: ", " шт."));
        int fishLoot = ParseUtils.parseIntSafe(HelperStrings.subString(html, "Улов: ", " шт."));
        if (html.toLowerCase(Locale.ROOT).contains("нет клёва")) {
            fishCatch = 0;
        }
        if (html.toLowerCase(Locale.ROOT).contains("не удалось вытащить рыбу")) {
            return "";
        }

        boolean fishUmUp = html.toLowerCase(Locale.ROOT).contains("повысилось на 1!");
        if (AppVars.Profile != null) {
            AppVars.AutoFishCheckUm = fishUmUp || AppVars.Profile.FishUm == 0;
            if (fishUmUp) {
                AppVars.Profile.FishUm++;
            }
        } else {
            AppVars.AutoFishCheckUm = fishUmUp;
        }

        BaitInfo bait = resolveBait();
        int baitRemainingBefore = ParseUtils.parseIntSafe(AppVars.AutoFishLikeVal);
        int baitRemainingAfter = Math.max(0, baitRemainingBefore - fishCatch);
        if (baitRemainingBefore > 0) {
            AppVars.AutoFishLikeVal = String.valueOf(baitRemainingAfter);
        }
        if (bait != null) {
            AppVars.NamePri = bait.name;
            AppVars.ValPri = baitRemainingAfter;
        }

        double fishNv = getOrDefault(FISH_NV, fishName, 0.0);
        double fishMass = getOrDefault(FISH_MASS, fishName, 2.0);
        double baitNv = bait == null ? 0.0 : bait.nvCost;
        double baitMass = bait == null ? 0.0 : bait.massCost;

        double totalBalance = fishNv * fishLoot - baitNv * fishCatch - 2.5;
        AppVars.AutoFishNV += totalBalance;
        updateFishMass(fishMass, fishLoot, baitMass, fishCatch);
        int cooldownSec = extractFishCooldownSec(html);

        AppVars.AutoFishCheckUd = true;
        AppVars.AutoFishWearUd = false;

        String report = buildReportHtml(
                fishName,
                fishLoot,
                fishCatch,
                fishUmUp,
                totalBalance,
                bait == null ? "" : bait.name,
                baitRemainingAfter,
                cooldownSec);
        ChatStats.addFishCatch(fishName, fishLoot, totalBalance);
        maybeWriteFishChat(
                fishName,
                fishLoot,
                fishCatch,
                fishUmUp,
                totalBalance,
                bait == null ? "" : bait.name,
                baitRemainingAfter,
                cooldownSec);
        return report;
    }

    /**
     * Пересчитывает `AppVars.AutoFishMassa` после текущего заброса.
     *
     * Зависимости:
     * - `AppVars.AutoFishMassa` в формате `current/max`;
     * - входные коэффициенты массы рыбы/приманки из таблиц `FISH_MASS` и `BAIT_INFO`.
     */
    private static void updateFishMass(double fishMass, int fishLoot, double baitMass, int fishCatch) {
        if (AppVars.AutoFishMassa == null || AppVars.AutoFishMassa.isEmpty()) {
            return;
        }
        try {
            String[] split = AppVars.AutoFishMassa.split("/");
            if (split.length < 2) {
                return;
            }
            double cur = Double.parseDouble(split[0].trim().replace(',', '.'));
            double delta = (fishMass * fishLoot) - (baitMass * fishCatch);
            double next = cur + delta;
            AppVars.AutoFishMassa = formatDouble(next) + "/" + split[1].trim();
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_FISH_TRACE mass update failed", e);
        }
    }

    /**
     * Собирает HTML-фрагмент, который подменяет первую строку `RESO@["..."]` в fish_ajax.
     *
     * Зависимости:
     * - `AppVars.Profile.FishUm` (умение рыбалки),
     * - `AppVars.AutoFishMassa` и `AppVars.AutoFishNV` (масса/баланс сессии).
     */
    private static String buildReportHtml(String fishName,
                                          int fishLoot,
                                          int fishCatch,
                                          boolean fishUmUp,
                                          double totalBalance,
                                          String baitName,
                                          int baitRemaining,
                                          int cooldownSec) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(fishName).append("</b> [<b>")
                .append(fishLoot).append('/').append(fishCatch).append("</b>]. ");
        if (AppVars.Profile != null && AppVars.Profile.FishUm > 0) {
            sb.append("<br><b>").append(getFishTimestamp()).append("</b> Умелка: <b>")
                    .append(AppVars.Profile.FishUm).append("</b>");
            if (fishUmUp) {
                sb.append(" <font color=#008800><b>(+1)</b></font>");
            }
        }
        if (baitName != null && !baitName.isEmpty()) {
            sb.append("<br><b>").append(baitName).append("</b> (остаток): <b>").append(baitRemaining).append("</b>");
        }
        if (AppVars.AutoFishMassa != null && !AppVars.AutoFishMassa.isEmpty()) {
            sb.append("<br>Масса: <b>").append(AppVars.AutoFishMassa).append("</b>");
        }
        sb.append("<br>").append(totalBalance < 0 ? "Потери" : "Доход")
                .append(": <b>").append(totalBalance < 0 ? "" : "+").append(formatDouble(totalBalance)).append(" NV</b>");
        sb.append("<br>").append(AppVars.AutoFishNV < 0 ? "Потери за рыбалку" : "Доход за рыбалку")
                .append(": <b>").append(AppVars.AutoFishNV < 0 ? "" : "+").append(formatDouble(AppVars.AutoFishNV)).append(" NV</b>");
        if (cooldownSec > 0) {
            int totalSec = lastAct1TimerSec + cooldownSec;
            sb.append("<br>Таймаут: <b>").append(totalSec).append(" сек.</b>");
            sb.append(" (act1=").append(lastAct1TimerSec).append("+act2=").append(cooldownSec).append(")");
        }
        return sb.toString();
    }

    /**
     * Пишет итог рыболовного шага в игровой чат, если это включено в профиле.
     *
     * Зависимости:
     * - `UserConfig.FishChatReport/FishChatReportColor`,
     * - транспорт чата через `pushChatMessage(...)`.
     */
    private static void maybeWriteFishChat(String fishName,
                                           int fishLoot,
                                           int fishCatch,
                                           boolean fishUmUp,
                                           double totalBalance,
                                           String baitName,
                                           int baitRemaining,
                                           int cooldownSec) {
        if (AppVars.Profile == null) {
            return;
        }
        if (!AppVars.Profile.FishChatReport && !AppVars.Profile.FishChatReportColor) {
            return;
        }
        int currentTied = CharacterVitalsManager.snapshot().tied;
        int totalSec = lastAct1TimerSec + cooldownSec;
        StringBuilder sb = new StringBuilder();
        sb.append(MainPhp.buildServerChatTimeHtml())
                .append("<font color=#333399><b>[Авто-рыбалка]</b>:</font> ");
        if (AppVars.Profile.FishUm > 0) {
            sb.append("Умелка: <b>").append(AppVars.Profile.FishUm).append("</b>");
            if (fishUmUp) {
                sb.append(" <font color=#008800><b>(+1)</b></font>");
            }
            sb.append(". ");
        }
        if (baitName != null && !baitName.isEmpty()) {
            sb.append(escapeHtmlText(baitName)).append(" &raquo; ");
        }
        sb.append("<b>").append(escapeHtmlText(fishName)).append("</b>")
                .append(" [<b>").append(fishLoot).append('/').append(fishCatch).append("</b>]. ");
        if (baitName != null && !baitName.isEmpty()) {
            sb.append("Остаток приманки: <b>").append(Math.max(0, baitRemaining)).append("</b>. ");
        }
        if (AppVars.AutoFishMassa != null && !AppVars.AutoFishMassa.isEmpty()) {
            sb.append("Масса: <b>").append(escapeHtmlText(AppVars.AutoFishMassa)).append("</b>. ");
        }
        if (currentTied >= 0) {
            sb.append("Усталость: <b>").append(currentTied).append("</b>. ");
        }
        sb.append(totalBalance < 0 ? "Потери" : "Доход")
                .append(": <b>")
                .append(totalBalance < 0 ? "" : "+")
                .append(formatDouble(totalBalance))
                .append(" NV</b>. ");
        sb.append(AppVars.AutoFishNV < 0 ? "Потери за рыбалку: <b>" : "Доход за рыбалку: <b>")
                .append(AppVars.AutoFishNV < 0 ? "" : "+")
                .append(formatDouble(AppVars.AutoFishNV))
                .append(" NV</b>. ");
        if (totalSec > 0) {
            sb.append("Таймаут: <b>").append(totalSec).append(" сек.</b> ")
                    .append("(act1=").append(lastAct1TimerSec).append("+act2=").append(cooldownSec).append(").");
        }
        pushChatMessage(sb.toString());
    }

    private static String getFishTimestamp() {
        long serverMs = System.currentTimeMillis();
        if (AppVars.Profile != null && AppVars.Profile.ServDiff != Long.MIN_VALUE) {
            serverMs = serverMs - AppVars.Profile.ServDiff;
        }
        synchronized (FISH_TIME_FORMAT) {
            return FISH_TIME_FORMAT.format(new Date(serverMs));
        }
    }

    private static String escapeHtmlText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Резолвит активную приманку по runtime-id (`AutoFishLikeId`) или fallback по имени (`NamePri`).
     *
     * Зависимости:
     * - таблица `BAIT_INFO`,
     * - поля `AppVars.AutoFishLikeId` и `AppVars.NamePri`.
     */
    private static BaitInfo resolveBait() {
        if (AppVars.AutoFishLikeId != null && !AppVars.AutoFishLikeId.isEmpty()) {
            BaitInfo byId = BAIT_INFO.get(AppVars.AutoFishLikeId.trim());
            if (byId != null) {
                return byId;
            }
        }
        if (AppVars.NamePri != null && !AppVars.NamePri.isEmpty()) {
            for (BaitInfo info : BAIT_INFO.values()) {
                if (info.name.equalsIgnoreCase(AppVars.NamePri.trim())) {
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * Аварийно выключает авто-рыбалку при серверной ошибке снастей/приманки.
     *
     * Зависимости:
     * - `AutoFunctionsManager.setAutoFishEnabled(false)` (основной путь),
     * - fallback в `AppVars.Profile.AutoFish=false`,
     * - уведомления UI через `ACTION_ADD_CHAT_MESSAGE` и `ACTION_STOP_AUTOFISH`.
     */
    private static void disableAutoFish(String reason) {
        try {
            if (AppVars.getContext() != null) {
                AutoFunctionsManager.getInstance(AppVars.getContext()).setAutoFishEnabled(false);
            } else if (AppVars.Profile != null) {
                AppVars.Profile.AutoFish = false;
            }
        } catch (Exception e) {
            AppLog.w(TAG, "AUTO_FISH_TRACE disable failed", e);
            if (AppVars.Profile != null) {
                AppVars.Profile.AutoFish = false;
            }
        }
        pushChatMessage(MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#cc0000><b>Авто-рыбалка выключена: " + reason + "</b></font>");
        if (AppVars.getContext() != null) {
            LocalBroadcastManager.getInstance(AppVars.getContext())
                    .sendBroadcast(new Intent(AppVars.ACTION_STOP_AUTOFISH));
        }
    }

    /**
     * Обрабатывает server-hard-stop "нет рыболовных снастей" без мгновенного отключения AutoFish.
     * Запускает существующий recovery-контур через MainPhp (проверка/переодевание удочки).
     */
    private static void handleFishNoGearHardStop(String address) {
        AppVars.AutoFishCheckUd = true;
        AppVars.AutoFishWearUd = false;
        // Сброс cooldown InfoApi: следующий kickFishCycleAttempt получит shouldBypassCooldown=true
        // (isPreflightAddress=true в af_cycle=1&af_preflight=1), что запустит полную InfoApi-проверку.
        lastAutoFishInfoApiPrecheckAtMs = 0L;
        AppVars.suppressBackgroundProbesDuringFishing = false;
        String msg = "AUTO_FISH_TRACE hard-stop no-gear: schedule gear recovery cycle, address=" + address;
        AppLog.w(TAG, TAG, msg);
        pushChatMessage(MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#cc6600><b>[Авто-рыбалка] Нет снастей в руках. Запускаю проверку и переодевание удочки.</b></font>");
        // kickFishCycleAttempt (через scheduleFreshFishCycleKick) загружает af_preflight=1,
        // что обязательно для shouldBypassCooldown=true и корректного gear-wear recovery.
        scheduleFreshFishCycleKick(1000L);
    }

    /**
     * Серверная фраза "нет снастей в руках" (удочка отсутствует/сломалась).
     */
    private static boolean containsFishNoGearHardStop(String lower) {
        return lower != null && lower.contains("у вас нет рыболовных снастей");
    }

    /**
     * Отправляет HTML-сообщение в чат приложения через локальный broadcast.
     *
     * Зависимости:
     * - `AppVars.ACTION_ADD_CHAT_MESSAGE`,
     * - `LocalBroadcastManager`.
     */
    private static void pushChatMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        // Надёжный путь: если MainActivity активна, пишем напрямую в чат (без зависимости от receiver).
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            Chat.addMessageToChat(message);
            return;
        }
        if (AppVars.getContext() == null) {
            return;
        }
        Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }

    /**
     * Серверные фразы, при которых цикл рыбалки нужно останавливать.
     *
     * Зависимости:
     * - raw-текст ответа fish_ajax от сервера Neverlands.
     */
    private static boolean containsFishHardStop(String lower) {
        return lower.contains("у вас нет приманки, чтобы ловить рыбу")
                || lower.contains("приманок нет в наличии")
                || lower.contains("у вас не хватает умения, чтобы ловить тут рыбу");
    }

    /**
     * Безопасный парсинг целых из строк с примесями (`"12 шт."`, `" 294 "`).
     */
    /**
     * Безопасно парсит целое число из "грязной" строки (`"294"`, `"12 шт."`, `"  -3 "`).
     *
     * Зависимости:
     * - все разборы payload/отчётов (`act=1`, `act=2`, fish report);
     * - вызывается из `extractFishCooldownSec(...)`, `parseFishAct1State(...)`,
     *   `fishReport(...)` и др.
    /**
     * Возвращает значение из таблицы коэффициентов с fallback.
     */
    /**
     * Читает коэффициент из справочной таблицы с fallback-значением.
     *
     * Зависимости:
     * - `FISH_NV`/`FISH_MASS` как карты коэффициентов по названию рыбы;
     * - используется в `fishReport(...)` для расчёта экономики и массы.
     */
    private static double getOrDefault(Map<String, Double> map, String key, double fallback) {
        Double value = map.get(key);
        return value == null ? fallback : value;
    }

    /**
     * Нормализует double для UI/чата (обрезает лишние нули).
     */
    /**
     * Форматирует число для UI/чата без лишних хвостов (`12.00 -> 12`, `12.30 -> 12.3`).
     *
     * Зависимости:
     * - `Locale.US` для стабильной десятичной точки;
     * - применяется в отчёте рыбалки и обновлении накопительных показателей.
     */
    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    /**
     * Регистрация коэффициентов вида рыбы в таблицах NV и массы.
     */
    /**
     * Регистрирует экономические коэффициенты конкретного вида рыбы.
     *
     * Зависимости:
     * - `FISH_NV` (стоимость по NV);
     * - `FISH_MASS` (масса единицы рыбы).
     *
     * Используется только при инициализации статических таблиц.
     */
    private static void putFish(String name, double nv, double mass) {
        FISH_NV.put(name, nv);
        FISH_MASS.put(name, mass);
    }

    /**
     * Регистрация приманки по server-id (`primid`) и ее коэффициентов.
     */
    /**
     * Регистрирует приманку в справочнике `BAIT_INFO`.
     *
     * Зависимости:
     * - server `primid` (38..46) как ключ;
     * - `BaitInfo` для хранения имени и коэффициентов расхода.
     *
     * Используется при статической инициализации перед запуском авто-рыбалки.
     */
    private static void putBait(String id, String name, double nvCost, double massCost) {
        BAIT_INFO.put(id, new BaitInfo(name, nvCost, massCost));
    }

    /**
     * DTO приманки: название + коэффициенты расходов по NV и массе.
     */
    private static final class BaitInfo {
        final String name;
        final double nvCost;
        final double massCost;

        BaitInfo(String name, double nvCost, double massCost) {
            this.name = name;
            this.nvCost = nvCost;
            this.massCost = massCost;
        }
    }

    /**
     * Выбор приманки из payload `act=1`.
     */
    private static final class FishBaitSelection {
        final String id;
        final String name;
        final int count;

        FishBaitSelection(String id, String name, int count) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.count = Math.max(0, count);
        }
    }

    /**
     * Распарсенное состояние `act=1` (captcha/vcode/масса/приманки).
     */
    private static final class FishAct1State {
        String captchaToken = "";
        String vcode = "";
        String massCurrent = "";
        String massMax = "";
        final List<FishBaitSelection> baits = new ArrayList<>();
    }

    // ============================================================================
    // ЭТАП 2: Интеграция цикла авто-рыбалки с озером (lake) для получения vcode
    // ============================================================================

    /**
     * executeFishingCycleCore() - Главный оркестратор авто-рыбалки
     *
     * Полностью портирует логику MainPhpFish.cs + обработка озера из ПК-версии.
     * Использует ОЗЕРО (ContentLakeHtml), а не act=1 response для получения vcode.
     *
     * Алгоритм:
     * 1. Загружает озеро из AppVars.ContentLakeHtml
     * 2. Парсит vcode, lakeid, доступные act из озера (mainPhpAutoFishPrepareFromLakeAndroid)
     * 3. Выбирает приманку (selectBaitFromLakeHtmlAndroid)
     * 4. Отправляет act=1 для проверки состояния (wounded, captcha)
     * 5. Отправляет act=2 с vcode из озера (не из act=1!)
     * 6. Обрабатывает результат и планирует следующий цикл
     *
     * Вызов: Из AutoFishForegroundService или enterFishingModeForeground() при старте авто-рыбалки
     */
    public static void executeFishingCycleCore() {
        if (!isAutoFishEnabled()) {
            AppLog.d(TAG, "AUTO_FISH_TRACE executeFishingCycleCore: AutoFish not enabled");
            return;
        }

        // КРИТИЧНО: Если озеро считается испорченным (была ошибка vcode или смена контекста)
        // - очищаем его и перезагружаем для свежего vcode
        if (AppVars.FishLakeShouldBeRefreshed) {
            AppLog.w(TAG, "AUTO_FISH_TRACE executeFishingCycleCore: lake marked as corrupted, refreshing...");
            AppVars.FishLakeShouldBeRefreshed = false;
            AppVars.ContentLakeHtml = "";
            AppVars.ContentLakeHtmlLastUpdateAtMs = 0;
            requestAutoFishBootstrap("lake_corrupted");
            return;
        }

        // КРИТИЧНО: Проверяем возраст озера перед ловлей
        // После 5+ минут в фоне vcode истекает (ошибка "Неверный код защиты")
        // Если озеро кэшировано давно, перезагружаем его для свежего vcode
        long lakeAgeMs = System.currentTimeMillis() - AppVars.ContentLakeHtmlLastUpdateAtMs;
        if (lakeAgeMs > 120_000) {  // 120 сек = 2 минуты
            AppLog.w(TAG, "AUTO_FISH_TRACE executeFishingCycleCore: lake cache too old (age=" + lakeAgeMs + "ms), requesting fresh lake");
            AppVars.ContentLakeHtml = "";  // очищаем кэш
            AppVars.ContentLakeHtmlLastUpdateAtMs = 0;
            requestAutoFishBootstrap("lake_cache_expired");
            return;
        }

        String lakeHtml = AppVars.ContentLakeHtml;
        if (lakeHtml == null || lakeHtml.isEmpty()) {
            AppLog.w(TAG, "AUTO_FISH_TRACE executeFishingCycleCore: lake HTML is empty, bootstrapping...");
            requestAutoFishBootstrap("missing_lake_html");
            return;
        }

        try {
            // Парсим озеро один раз
            LakeParseResult lakeResult = mainPhpAutoFishPrepareFromLakeAndroid(lakeHtml);
            if (lakeResult == null || lakeResult.vcode.isEmpty() || lakeResult.lakeid <= 0) {
                AppLog.w(TAG, "AUTO_FISH_TRACE executeFishingCycleCore: failed to parse lake, requesting bootstrap");
                requestAutoFishBootstrap("lake_parse_fail");
                return;
            }

            AppLog.d(TAG, "AUTO_FISH_TRACE executeFishingCycleCore: lake parsed, vcode="
                    + (lakeResult.vcode.length() > 8 ? lakeResult.vcode.substring(0, 8) + "..." : lakeResult.vcode)
                    + ", lakeid=" + lakeResult.lakeid);

            // Выбраем приманку из озера
            BaitSelectionResult baitResult = selectBaitFromLakeHtmlAndroid(lakeHtml);
            if (baitResult == null || !baitResult.isAvailable) {
                AppLog.w(TAG, "AUTO_FISH_TRACE executeFishingCycleCore: no available bait, reason="
                        + (baitResult == null ? "null" : baitResult.reason));
                disableAutoFish("Нет доступной приманки в озере: " + 
                        (baitResult == null ? "parse fail" : baitResult.reason));
                return;
            }

            AppLog.d(TAG, "AUTO_FISH_TRACE executeFishingCycleCore: bait selected, bait_id="
                    + baitResult.bait_id + ", available_count=" + baitResult.available_count);

            // ⚠️ ВАЖНО: VCode больше НЕ кешируется локально в AppVars.FishCurrentVcode.
            // Вместо этого используется SessionManager, который парсит свежий vcode из каждого
            // HTML ответа сервера через WebViewRequestInterceptor. Это предотвращает потерю vcode
            // при смене контекста и обновления PHPSESSID.
            // Для каждого AJAX запроса мы вызываем SessionManager.getValidVCodeForAction("fish_act")
            AppVars.FishCurrentLakeid = lakeResult.lakeid;

            // Готовимся к отправке act=1
            lastFishAct1AtMs = System.currentTimeMillis();
            AppVars.suppressBackgroundProbesDuringFishing = true;
            AppVars.fishingSequenceStartAtMs = lastFishAct1AtMs;

            // Отправляем act=1 через async запрос для проверки состояния рыболова
            MainActivity activity = getMainActivityOrNull();
            if (activity != null) {
                activity.runOnUiThread(() -> sendFishAct1RequestWithLake(
                        lakeResult.vcode,
                        lakeResult.lakeid,
                        baitResult.bait_id));
            }

        } catch (Exception e) {
            AppLog.e(TAG, "AUTO_FISH_TRACE executeFishingCycleCore: exception", e);
            requestAutoFishBootstrap("cycle_core_exception");
        }
    }

    /**
     * mainPhpAutoFishPrepareFromLakeAndroid() - Парсит озеро в структурированный результат
     *
     * Извлекает из HTML озера:
     * - vcode: <input name="vcode" value="...">
     * - lakeid: <input name="lakeid" value="...">
     * - act_list: все доступные <input name="act" value="...">
     *
     * Возвращает LakeParseResult(vcode, lakeid, act_list_fields) или null при ошибке.
     */
    private static LakeParseResult mainPhpAutoFishPrepareFromLakeAndroid(String lakeHtml) {
        if (lakeHtml == null || lakeHtml.isEmpty()) {
            AppLog.d(TAG, "AUTO_FISH_TRACE mainPhpAutoFishPrepareFromLakeAndroid: null html");
            return null;
        }

        try {
            LakeParseResult result = new LakeParseResult();

            // Парсим vcode
            Pattern vcodePattern = Pattern.compile("<input[^>]*name=[\"']?vcode[\"']?[^>]*value=[\"']?([^\"'\\s>]+)");
            Matcher vcodeMatcher = vcodePattern.matcher(lakeHtml);
            if (vcodeMatcher.find()) {
                result.vcode = vcodeMatcher.group(1).trim();
            }

            // Парсим lakeid
            Pattern lakeidPattern = Pattern.compile("<input[^>]*name=[\"']?lakeid[\"']?[^>]*value=[\"']?([0-9]+)");
            Matcher lakeidMatcher = lakeidPattern.matcher(lakeHtml);
            if (lakeidMatcher.find()) {
                result.lakeid = ParseUtils.parseIntSafe(lakeidMatcher.group(1));
            }

            // Парсим все доступные act
            Pattern actPattern = Pattern.compile("<input[^>]*name=[\"']?act[\"']?[^>]*value=[\"']?([^\"'\\s>]+)");
            Matcher actMatcher = actPattern.matcher(lakeHtml);
            while (actMatcher.find()) {
                String actValue = actMatcher.group(1).trim();
                if (!actValue.isEmpty()) {
                    result.act_list_fields.add(actValue);
                }
            }

            AppLog.d(TAG, "AUTO_FISH_TRACE mainPhpAutoFishPrepareFromLakeAndroid: "
                    + "vcode=" + (result.vcode.length() > 8 ? result.vcode.substring(0, 8) + "..." : result.vcode)
                    + ", lakeid=" + result.lakeid
                    + ", act_list_size=" + result.act_list_fields.size());

            return result;

        } catch (Exception e) {
            AppLog.e(TAG, "AUTO_FISH_TRACE mainPhpAutoFishPrepareFromLakeAndroid: parse exception", e);
            return null;
        }
    }

    /**
     * selectBaitFromLakeHtmlAndroid() - Выбирает оптимальную приманку для рыбалки
     *
     * Правила выбора:
     * 1. Считывает доступные приманки из озера (содержимое HTML)
     * 2. Проверяет запас приманок в инвентаре (AppVars)
     * 3. Выбирает приманку с максимальным количеством в наличии
     * 4. Проверяет что озеро поддерживает эту приманку
     * 5. Применяет ограничения профиля (FishEnabledPrims)
     *
     * Возвращает BaitSelectionResult(bait_id, available_count, isAvailable, reason)
     */
    private static BaitSelectionResult selectBaitFromLakeHtmlAndroid(String lakeHtml) {
        if (lakeHtml == null || lakeHtml.isEmpty()) {
            AppLog.d(TAG, "AUTO_FISH_TRACE selectBaitFromLakeHtmlAndroid: null html");
            return new BaitSelectionResult(-1, 0, false, "empty_lake_html");
        }

        try {
            // Парсим все приманки из озера
            List<FishBaitInfo> availableBaits = new ArrayList<>();

            // RegEx для извлечения приманок из HTML озера
            // Ищем input элементы с именами типа "pribor_bait_38", "pribor_bait_39" и т.д.
            Pattern baitPattern = Pattern.compile(
                    "<input[^>]*name=[\"']?pribor_bait_(3[8-9]|4[0-6])[\"']?[^>]*value=[\"']?([0-9]+)",
                    Pattern.CASE_INSENSITIVE);
            Matcher baitMatcher = baitPattern.matcher(lakeHtml);

            while (baitMatcher.find()) {
                String baitId = baitMatcher.group(1).trim();
                int baitsAvailableAtLake = ParseUtils.parseIntSafe(baitMatcher.group(2));

                if (baitsAvailableAtLake > 0 && isBaitEnabledInProfile(baitId)) {
                    FishBaitInfo info = new FishBaitInfo();
                    info.id = ParseUtils.parseIntSafe(baitId);
                    info.name = getBaitNameById(baitId);
                    info.stock_count = baitsAvailableAtLake;
                    availableBaits.add(info);

                    AppLog.d(TAG, "AUTO_FISH_TRACE selectBaitFromLakeHtmlAndroid: found bait, id="
                            + baitId + ", name=" + info.name + ", available=" + baitsAvailableAtLake);
                }
            }

            if (availableBaits.isEmpty()) {
                AppLog.w(TAG, "AUTO_FISH_TRACE selectBaitFromLakeHtmlAndroid: no available baits found");
                return new BaitSelectionResult(-1, 0, false, "no_baits_in_lake");
            }

            // Выбираем приманку с максимальным запасом
            FishBaitInfo selectedBait = availableBaits.get(0);
            for (FishBaitInfo bait : availableBaits) {
                if (bait.stock_count > selectedBait.stock_count) {
                    selectedBait = bait;
                }
            }

            AppLog.d(TAG, "AUTO_FISH_TRACE selectBaitFromLakeHtmlAndroid: selected bait, id="
                    + selectedBait.id + ", name=" + selectedBait.name + ", stock=" + selectedBait.stock_count);

            return new BaitSelectionResult(selectedBait.id, selectedBait.stock_count, true, "ok");

        } catch (Exception e) {
            AppLog.e(TAG, "AUTO_FISH_TRACE selectBaitFromLakeHtmlAndroid: exception", e);
            return new BaitSelectionResult(-1, 0, false, "parse_exception: " + e.getMessage());
        }
    }

    /**
     * scheduleNextFishingCycleAttempt() - Планирует следующую попытку рыбалки
     *
     * Вычисляет задержку на основе результата последней попытки:
     * - Успех (нет captcha): 5 минут
     * - Ошибка исправляемая: exponential backoff (30s → 60s → 2m)
     * - Captcha требуется: 10 минут
     * - Критическая ошибка: 15 минут
     *
     * Использует Handler.postDelayed() для асинхронного планирования из UI-потока.
     */
    private static void scheduleNextFishingCycleAttempt(String lastResultStatus) {
        if (!isAutoFishEnabled()) {
            AppLog.d(TAG, "AUTO_FISH_TRACE scheduleNextFishingCycleAttempt: AutoFish disabled");
            return;
        }

        MainActivity activity = getMainActivityOrNull();
        if (activity == null) {
            AppLog.w(TAG, "AUTO_FISH_TRACE scheduleNextFishingCycleAttempt: MainActivity not available");
            return;
        }

        long delayMs;
        if (lastResultStatus == null || lastResultStatus.isEmpty()) {
            delayMs = 5 * 60 * 1000L; // 5 минут
        } else if ("success".equalsIgnoreCase(lastResultStatus)) {
            delayMs = 5 * 60 * 1000L; // 5 минут
        } else if ("captcha_required".equalsIgnoreCase(lastResultStatus)) {
            delayMs = 10 * 60 * 1000L; // 10 минут
        } else if ("wrong_vcode".equalsIgnoreCase(lastResultStatus)) {
            delayMs = 30 * 1000L; // 30 секунд
        } else if ("err_recovery_1".equalsIgnoreCase(lastResultStatus)) {
            delayMs = 30 * 1000L; // 30 секунд
        } else if ("err_recovery_2".equalsIgnoreCase(lastResultStatus)) {
            delayMs = 60 * 1000L; // 60 секунд
        } else if ("critical_error".equalsIgnoreCase(lastResultStatus)) {
            delayMs = 15 * 60 * 1000L; // 15 минут
        } else {
            delayMs = 5 * 60 * 1000L; // 5 минут по умолчанию
        }

        final long scheduledMs = System.currentTimeMillis() + delayMs;
        AppVars.NextFishingAttemptDueAtMs = scheduledMs;

        activity.runOnUiThread(() -> {
            WebView webView = activity.getMainWebView();
            if (webView == null) {
                return;
            }

            AppLog.d(TAG, "AUTO_FISH_TRACE scheduleNextFishingCycleAttempt: scheduled in " + delayMs
                    + "ms (status=" + lastResultStatus + ")");

            webView.postDelayed(
                    () -> {
                        if (isAutoFishEnabled()) {
                            AppLog.d(TAG, "AUTO_FISH_TRACE scheduled cycle attempt triggered");
                            executeFishingCycleCore();
                        }
                    },
                    delayMs);
        });
    }

    /**
     * sendFishAct1RequestWithLake() - Отправляет act=1 запрос с параметрами из озера
     *
     * Внутренний метод для асинхронной отправки act=1.
     * При ответе вызывается processFishAct1() через стандартный пост-фильтр.
     */
    private static void sendFishAct1RequestWithLake(String vcode, int lakeid, int baitId) {
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) {
            AppLog.w(TAG, "AUTO_FISH_TRACE sendFishAct1RequestWithLake: MainActivity not available");
            return;
        }

        try {
            WebView webView = activity.getMainWebView();
            if (webView == null) {
                AppLog.w(TAG, "AUTO_FISH_TRACE sendFishAct1RequestWithLake: WebView not available");
                return;
            }

            String url = "http://neverlands.ru/gameplay/ajax/fish_ajax.php?act=1&r=" + System.currentTimeMillis();
            AppLog.d(TAG, "AUTO_FISH_TRACE sendFishAct1RequestWithLake: sending " + url);

            webView.loadUrl(url);

        } catch (Exception e) {
            AppLog.e(TAG, "AUTO_FISH_TRACE sendFishAct1RequestWithLake: exception", e);
            requestAutoFishBootstrap("act1_send_fail");
        }
    }

    /**
     * getMainActivityOrNull() - Безопасное получение MainActivity
     *
     * Предотвращает NPE при обращении к ActivityHolder/AppVars
     * если MainActivity помогает разгрузить себя или приложение закрывается.
     *
     * Возвращает MainActivity или null.
     */
    private static MainActivity getMainActivityOrNull() {
        try {
            if (AppVars.mainActivity == null) {
                return null;
            }
            MainActivity activity = AppVars.mainActivity.get();
            if (activity == null || activity.isFinishing()) {
                return null;
            }
            return activity;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * getBaitNameById() - Вспомогательный маппер server-id приманки на имя
     *
     * Используется при парсинге озера для читаемого логирования.
     */
    private static String getBaitNameById(String baitId) {
        if (baitId == null) return "Unknown";
        switch (baitId) {
            case "38": return "Хлеб";
            case "39": return "Червяк";
            case "40": return "Крупный червяк";
            case "41": return "Опарыш";
            case "42": return "Мотыль";
            case "43": return "Блесна";
            case "44": return "Донка";
            case "45": return "Мормышка";
            case "46": return "Заговоренная блесна";
            default: return "Приманка " + baitId;
        }
    }

    // ============================================================================
    // DTO классы для ЭТАП 2
    // ============================================================================

    /**
     * LakeParseResult - Результат парсинга озера (lake HTML)
     * 
     * Содержит:
     * - vcode: код валидации из <input name="vcode">
     * - lakeid: ID озера
     * - act_list_fields: список доступных action-ов для этого озера
     */
    private static final class LakeParseResult {
        String vcode = "";
        int lakeid = -1;
        final List<String> act_list_fields = new ArrayList<>();
    }

    /**
     * BaitSelectionResult - Результат выбора приманки для рыбалки
     *
     * Содержит:
     * - bait_id: server-id приманки (38-46)
     * - available_count: количество единиц приманки в озере
     * - isAvailable: флаг, доступна ли приманка (прошла все проверки)
     * - reason: текстовое объяснение при ошибке (для логирования)
     */
    private static final class BaitSelectionResult {
        final int bait_id;
        final int available_count;
        final boolean isAvailable;
        final String reason;

        BaitSelectionResult(int bait_id, int available_count, boolean isAvailable, String reason) {
            this.bait_id = bait_id;
            this.available_count = available_count;
            this.isAvailable = isAvailable;
            this.reason = reason == null ? "" : reason;
        }
    }

    /**
     * FishBaitInfo - Информация о приманке при выборе
     *
     * Содержит:
     * - id: server-id приманки
     * - name: имя приманки (для логов)
     * - stock_count: количество в озере
     * - available_at_lakes: список озёр, где доступна (расширение для будущего)
     */
    private static final class FishBaitInfo {
        int id = -1;
        String name = "";
        int stock_count = 0;
        final List<Integer> available_at_lakes = new ArrayList<>();
    }

    // ============================================================================
    // Конец ЭТАП 2 методов и DTO
    // ============================================================================

    // ============================================================================
    // Конец ЭТАП 2 методов и DTO (вспомогательные методы используют уже
    // существующие в коде: requestAutoFishBootstrap, disableAutoFish,
    // isBaitEnabledInProfile, parseIntSafe, getMainActivityOrNull,
    // sendFishAct1RequestWithLake, getBaitNameById)
    // ============================================================================

    // ============================================================================
    // Проверка целостности снаряжения для рыбалки (FishingGearCheck)
    // ============================================================================

    /**
     * isFishingGearBroken() - Проверяет, сломана ли одна из удочек по долговечности.
     * 
     * Берёт СВЕЖИЕ slots из HTML и парсит реальную durability, а не использует старые AppVars.
     * Удочка считается сломанной если текущая долговечность > 0.
     * Вызывается только когда мы парсим HTML профиля (slots_inv).
     */
    public static boolean isFishingGearBroken(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        
        // Парсим slots_inv из HTML профиля (как в ПК версии)
        String slotsInv = HelperStrings.subString(html, "slots_inv(", ");");
        if (slotsInv == null || slotsInv.isEmpty()) {
            // Пробуем slots_pla если slots_inv не найден
            slotsInv = HelperStrings.subString(html, "slots_pla(", ");");
            if (slotsInv == null || slotsInv.isEmpty()) {
                return false;
            }
        }
        
        // Парсим slots по запятым
        String[] pslots = slotsInv.split(",");
        if (pslots.length < 6) {
            return false;
        }
        
        // slmain = pslots[2] - основное снаряжение
        String[] slmain = pslots[2].split("@");
        if (slmain.length < 13) {
            return false;
        }
        
        // sldlg = pslots[5] - долговечность
        String[] sldlg = pslots[5].split("@");
        if (sldlg.length < 13) {
            return false;
        }
        
        // Проверяем Hand1 (левая рука / первая рука)
        String[] slhand1 = slmain[2].split(":");
        if (slhand1.length >= 2) {
            String hand1Name = slhand1[1];
            
            // Если слот не пустой и это удочка/спиннинг
            if (!hand1Name.toLowerCase().startsWith("слот")) {
                String curDlg1 = sldlg[2]; // текущий долг для Hand1
                if (isBrokenByCurrentDolg(curDlg1)) {
                    String msg = "❌ [FISH_GEAR_CHECK_REJECTED] Hand1 (" + hand1Name + ") broken dolg=" + curDlg1;
                    AppLog.w(TAG, TAG, msg);
                    return true; // Одета сломанная удочка - переодеть
                }
            }
        }
        
        // Проверяем Hand2 (правая рука / вторая рука)
        String[] slhand2 = slmain[12].split(":");
        if (slhand2.length >= 2) {
            String hand2Name = slhand2[1];
            
            // Если слот не пустой и это удочка/спиннинг
            if (!hand2Name.toLowerCase().startsWith("слот")) {
                String curDlg2 = sldlg[12]; // текущий долг для Hand2
                if (isBrokenByCurrentDolg(curDlg2)) {
                    String msg = "❌ [FISH_GEAR_CHECK_REJECTED] Hand2 (" + hand2Name + ") broken dolg=" + curDlg2;
                    AppLog.w(TAG, TAG, msg);
                    return true; // Одета сломанная удочка - переодеть
                }
            }
        }
        
        return false; // Обе руки в порядке или пусты
    }
    
    /**
     * isBrokenByCurrentDolg() - Проверяет текущий долг (только число перед '/').
     * Долг = урон/износ. Если долг > 0 - предмет сломан.
     * Примеры:
     * - "0" → не сломан (долг=0)
     * - "5" → СЛОМАН (долг > 0)
     * - "15" → СЛОМАН (долг > 0)
     */
    private static boolean isBrokenByCurrentDolg(String dolgStr) {
        if (dolgStr == null || dolgStr.isEmpty()) {
            return false; // Нет долга = не сломано
        }
        
        try {
            int dolgValue = Integer.parseInt(dolgStr.trim());
            boolean isBroken = dolgValue > 0; // Если долг > 0 - сломано
            return isBroken;
        } catch (NumberFormatException e) {
            String msg = "⚠️ [FISH_GEAR_CHECK_ERROR] Failed to parse dolg=" + dolgStr;
            AppLog.w(TAG, TAG, msg);
            return false;
        }
    }

    /**
     * ✅ ПРАВИЛО 4 (AGENTS.MD): Проверка на сообщение сервера о перегрузе.
     * 
     * Проверяет наличие в HTML ответа сообщения о перегрузе от сервера:
     * "<font color=#CC0000>Внимание! Возможен перегруз."
     * 
     * Если найдена и в настройках включена опция FishStopOverWeight:
     * - Останавливаем авторыбалку
     * - Отправляем уведомление в чат
     * - Логируем решение
     * 
     * @param html HTML ответ act=1
     * @return true если перегруз обнаружен и рыбалка остановлена, false если можно продолжать
     */
    private static boolean checkOverweightHtmlPattern(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        // Проверяем паттерн серверного сообщения о перегрузе
        final String OVERWEIGHT_PATTERN = "<font color=#CC0000>Внимание! Возможен перегруз.";
        boolean hasOverweightMessage = html.contains(OVERWEIGHT_PATTERN);

        if (!hasOverweightMessage) {
            return false;  // Нет перегруза - продолжаем рыбалку
        }

        // Перегруз обнаружен в HTML ответе!
        String msg = "⚠️ FISH_OVERWEIGHT: сервер отправил сообщение о перегрузе";
        AppLog.w(TAG, TAG, msg);

        // Проверяем настройку FishStopOverWeight
        boolean shouldStop = AppVars.Profile != null && AppVars.Profile.FishStopOverWeight;

        if (shouldStop) {
            // Останавливаем рыбалку по настройке
            String stopMsg = "❌ FISH_STOP_OVERWEIGHT: Авто-рыбалка остановлена - из за перегруза массы (FishStopOverWeight=true)";
            AppLog.e(TAG, TAG, stopMsg);

            disableAutoFish("из за перегруза массы");
            return true;  // Рыбалка остановлена
        } else {
            // Сервер сообщил о перегрузе, но настройка отключена
            // Ожидаем автоматическую смену удочки при следующей попытке
            String continueMsg = "⚠️ FISH_CONTINUE_ON_OVERWEIGHT: Перегруз обнаружен, но FishStopOverWeight=false, " +
                    "ожидаем смены удочки при следующем цикле";
            AppLog.i(TAG, TAG, continueMsg);
            return false;  // Продолжаем - попробуем одеть новую удочку
        }
    }

    /**
     * Проверяет текущее состояние одежды (удочка в руке).
     * Если удочка отсутствует или сломана - ищет и одевает новую из инвентаря.
     *
     * Парсит HTML:
     * - GearParser для текущей одежды (Hand1, Hand2, Empty)
     * - InventoryParser для предметов в инвентаре
     *
     * Эквивалент C# функции MainPhpWearUd из MainPhpWear.cs
     *
     * @param html HTML act=1 ответ (содержит slots_inv и инвентарь)
     * @return true если была произведена смена удочки (требуется ожидание нового ответа),
     *         false если удочка в порядке и можно продолжать рыбалку
     */
    private static boolean checkAndWearRodIfNeeded(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        try {
            // Парсим текущую одежду
            GearParser gear = new GearParser(html);
            if (!gear.isValid) {
                String msg = "⚠️ FISH_GEAR_PARSE_FAILED: could not parse current gear, skipping rod replacement";
                AppLog.w(TAG, TAG, msg);
                return false;
            }

            String gearStatus = "FISH_GEAR_STATUS: " + gear.getStatusString();
            AppLog.d(TAG, TAG, gearStatus);

            // Проверяем настройку FishAutoWear - включено ли автооформление
            boolean autoWearEnabled = AppVars.Profile != null && AppVars.Profile.FishAutoWear;
            if (!autoWearEnabled) {
                AppLog.d(TAG, "FISH_GEAR_AUTOWEAR_DISABLED: не будем одевать удочку (настройка отключена)");
                return false;
            }

            // Проверяем есть ли удочка в руке 1
            if (!gear.empty1 && gear.isRodWorn(1)) {
                // Удочка есть в руке 1 и она не пуста
                AppLog.d(TAG, "FISH_GEAR_OK_HAND1: удочка одета в руке 1");
                return false;
            }

            // Рука 1 пуста или там не удочка - пытаемся одеть
            String tryWearMsg = "FISH_GEAR_TRY_WEAR_HAND1: рука 1 пуста или требуется смена, ищем удочку в инвентаре";
            AppLog.i(TAG, TAG, tryWearMsg);

            // Парсим инвентарь
            List<InventoryParser.InventoryItem> inventory = InventoryParser.parseInventory(html);
            if (inventory.isEmpty()) {
                String noInvMsg = "❌ FISH_NO_INVENTORY: инвентарь пуст или не спарсен, не можем одеть удочку";
                AppLog.e(TAG, TAG, noInvMsg);
                disableAutoFish("нет удочки в инвентаре");
                return true;  // Остановили рыбалку
            }

            // Ищем подходящую удочку в инвентаре
            String rodPreference = AppVars.Profile.FishHandOne;
            InventoryParser.InventoryItem rodToWear = InventoryParser.findSpecificRod(inventory, rodPreference);

            if (rodToWear == null) {
                String noRodMsg = "❌ FISH_NO_ROD_FOUND: удочка '" + rodPreference + "' не найдена в инвентаре";
                AppLog.e(TAG, TAG, noRodMsg);
                disableAutoFish("нет нужной удочки в инвентаре");
                return true;  // Остановили рыбалку
            }

            // Нашли удочку - одеваем её
            String wearMsg = String.format(
                "✅ FISH_WEAR_ROD: одеваем '%s' (dur=%s) по ссылке: %s",
                rodToWear.name, rodToWear.durability, rodToWear.wearUrl
            );
            AppLog.i(TAG, TAG, wearMsg);

            // Отправляем запрос на одевание
            executeWearLink(rodToWear.wearUrl, rodToWear.name);
            return true;  // Смена произведена, нужно ждать ответа сервера

        } catch (Exception e) {
            String errMsg = "⚠️ FISH_WEAR_ERROR: " + e.getMessage();
            AppLog.w(TAG, TAG, errMsg, e);
            return false;
        }
    }

    /**
     * Отправляет HTTP GET запрос для одевания удочки.
     * Ссылка имеет вид: "main.php?get_id=57&wid=27975541&vcode=..."
     *
     * @param wearUrl относительная ссылка из InventoryParser
     * @param rodName название удочки (для логирования)
     */
    private static void executeWearLink(String wearUrl, String rodName) {
        try {
            if (AppVars.mainActivity == null || AppVars.mainActivity.get() == null) {
                AppLog.w(TAG, "executeWearLink: mainActivity is null, cannot wear");
                return;
            }

            MainActivity activity = AppVars.mainActivity.get();
            if (activity == null) {
                AppLog.w(TAG, "executeWearLink: activity reference invalid");
                return;
            }

            android.webkit.WebView webView = activity.getMainWebView();
            if (webView == null) {
                AppLog.w(TAG, "executeWearLink: mainWebView is null");
                return;
            }

            // Преобразуем относительный URL в абсолютный
            String fullUrl = wearUrl.startsWith("http") ? wearUrl : "http://neverlands.ru/" + wearUrl;

            String logMsg = String.format(
                "FISH_WEAR_EXECUTE: loading URL to wear '%s': %s",
                rodName, fullUrl
            );
            AppLog.d(TAG, TAG, logMsg);

            // Загружаем URL - это выполнит GET запрос и вернёт HTML с новым состоянием gear
            webView.loadUrl(fullUrl);

        } catch (Exception e) {
            String errMsg = "⚠️ executeWearLink error: " + e.getMessage();
            AppLog.w(TAG, TAG, errMsg, e);
        }
    }

    // ============================================================================
    // Авто-Рыбалка: методы оркестрации, перенесённые из MainPhp.java
    // (C# parity: MainPhpFish.cs, MainPhpWear.cs, MainPhpDrink.cs)
    // Логика не изменена, только перемещена для уменьшения размера MainPhp.
    // ============================================================================

    // --- Константы авторыбалки (перенесены из MainPhp) ---
    private static final long AUTO_FISH_BLAZ_TRIGGER_COOLDOWN_MS = 8000L;
    /** Серверный таймаут после шага "Пить" в рыбалке: выдерживаем ~62 сек перед следующим шагом. */
    private static final long AUTO_FISH_DRINK_SERVER_COOLDOWN_MS = 62_000L;
    private static final int AUTO_FISH_DRINK_POLL_DELAY_MS = 1500;
    private static final int AUTO_FISH_WEAR_LOOP_MAX_REPEATS = 12;
    private static final long AUTO_FISH_WEAR_LOOP_WINDOW_MS = 20_000L;
    static final long AUTO_FISH_INFOAPI_PRECHECK_COOLDOWN_MS = 900L;
    /** В info.cgi line1 массив слотов индексируется с 0: slot[3] = index 2, slot[13] = index 12. */
    static final int AUTO_FISH_INFOAPI_SLOT_HAND1_INDEX = 2;
    static final int AUTO_FISH_INFOAPI_SLOT_HAND2_INDEX = 12;
    static final String BLISS_ELIXIR_NAME = "\u042D\u043B\u0438\u043A\u0441\u0438\u0440 \u0411\u043B\u0430\u0436\u0435\u043D\u0441\u0442\u0432\u0430";
    private static final int[] FISH_PRIM_IDS = new int[]{38, 39, 40, 41, 42, 43, 44, 45, 46};
    private static final int[] FISH_PRIM_FLAGS = new int[]{
            Prims.Bread, Prims.Worm, Prims.BigWorm, Prims.Stink, Prims.Fly,
            Prims.Light, Prims.Donka, Prims.Morm, Prims.HiFlight
    };

    // --- Volatile state (перенесено из MainPhp) ---
    private static volatile long lastAutoFishBlazTriggerAtMs = 0L;
    private static volatile long lastAutoFishDrinkTriggerAtMs = 0L;
    static volatile long lastAutoFishInfoApiPrecheckAtMs = 0L;
    private static volatile long lastAutoDrinkBlazTriggerAtMs = 0L;
    /** Защита от повторного показа капчи рыбалки из main.php. */
    private static volatile String lastMainCaptchaDialogKey = "";
    private static volatile long lastMainCaptchaDialogAtMs = 0L;

    // --- Inner class: состояние предпроверки (перенесено из MainPhp) ---
    static final class AutoFishInfoApiPrecheckState {
        final boolean snapshotValid;
        final boolean mustWear;
        final boolean needFatigueStep;
        final Integer tied;
        final int tiedThreshold;

        AutoFishInfoApiPrecheckState(boolean snapshotValid,
                                     boolean mustWear,
                                     boolean needFatigueStep,
                                     Integer tied,
                                     int tiedThreshold) {
            this.snapshotValid = snapshotValid;
            this.mustWear = mustWear;
            this.needFatigueStep = needFatigueStep;
            this.tied = tied;
            this.tiedThreshold = tiedThreshold;
        }

        boolean shouldRouteViaMainPhp() {
            return mustWear || needFatigueStep;
        }
    }

    // ======================================================================
    // Методы авторыбалки (перенесены из MainPhp без изменения логики)
    // ======================================================================

    /** Проверяет, включена ли авто-рыбалка по SharedPreferences/Profile. */
    static boolean isAutoFishEnabledByPreference() {
        if (AppVars.Profile != null) {
            return AppVars.Profile.AutoFish;
        }
        try {
            if (AppVars.getContext() != null) {
                return AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoFishEnabled();
            }
        } catch (Exception e) {
            String msg = "isAutoFishEnabledByPreference: fallback=false";
            AppLog.w(TAG, msg, e);
        }
        return false;
    }

    /**
     * Восстанавливает runtime-флаг AutoFish если пользователь вернулся на карту с озером.
     */
    static void recoverAutofishRuntimeStateIfNeeded(boolean onMapOrNature, String html, String address) {
        if (!onMapOrNature) {
            return;
        }
        if (AppVars.Profile == null) {
            return;
        }
        if (AppVars.Profile.AutoFish) {
            return;
        }
        if (!isAutoFishEnabledByPreference()) {
            String msg = "recoverAutofishRuntimeStateIfNeeded: AutoFish disabled in preferences";
            AppLog.d(TAG, msg);
            return;
        }
        if (html.contains("name=primid") || html.contains("name=\"primid\"")) {
            AppVars.Profile.AutoFish = true;
            String msg = "recoverAutofishRuntimeStateIfNeeded: \u2705 restored AppVars.Profile.AutoFish -> true (lake form detected, enabled in preferences)";
            AppLog.w(TAG, msg);
        } else {
            String msg = "recoverAutofishRuntimeStateIfNeeded: \u26A0\uFE0F NOT restoring AutoFish - lake form not detected (no name=primid in HTML)";
            AppLog.d(TAG, msg);
        }
    }

    /** Парсит умение "Рыбалка" из HTML (C# parity: mainPhpProcessFishSkills). */
    static void mainPhpProcessFishSkills(String html, String address) {
        if (html == null || html.isEmpty()) {
            return;
        }
        String fishSkill = HelperStrings.subString(
                html,
                "\u0420\u044B\u0431\u0430\u043B\u043A\u0430</td><td bgcolor=#FCFAF3><font class=proce><font color=#555555><div align=center>[",
                "]");
        if (fishSkill == null || fishSkill.isEmpty()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\u0420\u044B\u0431\u0430\u043B\u043A\u0430</td><td[^\\[]*\\[(\\d+)\\]", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                    .matcher(html);
            if (matcher.find()) {
                fishSkill = matcher.group(1);
            }
        }
        if (fishSkill != null && !fishSkill.isEmpty()) {
            try {
                int fishUm = Integer.parseInt(fishSkill.trim());
                AppVars.AutoFishCheckUm = false;
                if (AppVars.Profile != null) {
                    AppVars.Profile.FishUm = fishUm;
                }
                String msg = "AUTO_FISH_TRACE skill parsed: FishUm=";
                AppLog.d(TAG, msg);
            } catch (Exception e) {
                String msg = "AUTO_FISH_TRACE skill parse failed: ";
                AppLog.w(TAG, msg, e);
            }
            return;
        }
        if (AppVars.AutoFishCheckUm && address != null && address.contains("mselect=1")) {
            AppVars.AutoFishCheckUm = false;
            String msg = "AUTO_FISH_TRACE mselect=1 without \u0420\u044B\u0431\u0430\u043B\u043A\u0430 block, forced AutoFishCheckUm=false";
            AppLog.w(TAG, msg);
        }
    }

    /**
     * Обновляет runtime-усталость из текущего HTML верхнего фрейма.
     * Зависимости: parseMainPhpTiedValue, CharacterVitalsManager.updateTied.
     */
    static void mainPhpUpdateTied(String html) {
        Integer tiedValue = parseMainPhpTiedValue(html);
        if (tiedValue == null) {
            return;
        }
        int normalized = Math.max(0, Math.min(100, tiedValue));
        CharacterVitalsManager.Snapshot before = CharacterVitalsManager.snapshot();
        if (before.tied != normalized) {
            String msg = "AUTO_FISH_TRACE tied update: old=";
            AppLog.d(TAG, msg);
        }
        CharacterVitalsManager.Snapshot after = CharacterVitalsManager.updateTied(normalized, "FishAjaxPhp.mainPhpUpdateTied");
        if (AppVars.AutoFishDrink && after.tied <= 0) {
            AppVars.AutoFishDrink = false;
            String msg = "AUTO_FISH_TRACE tied reached zero -> stop drink-to-zero mode";
            AppLog.d(TAG, msg);
        }
    }

    /** Пытается извлечь процент усталости из блока "Усталость: ...". */
    private static Integer parseMainPhpTiedValue(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        try {
            Integer tiedFromHpmp = parseMainPhpTiedFromHpmp(html);
            if (tiedFromHpmp != null) {
                return tiedFromHpmp;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?is)\u0423\u0441\u0442\u0430\u043b\u043e\u0441\u0442\u044c\\s*:</td><td[^>]*>.*?<b>\\s*(\\d{1,3})\\s*</b>")
                    .matcher(html);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            m = java.util.regex.Pattern
                    .compile("(?is)\u0423\u0441\u0442\u0430\u043b\u043e\u0441\u0442\u044c[^0-9]{0,80}(\\d{1,3})\\s*%")
                    .matcher(html);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            m = java.util.regex.Pattern
                    .compile("(?is)\u0423\u0441\u0442\u0430\u043b\u043e\u0441\u0442\u044c[^0-9]{0,80}(\\d{1,3})")
                    .matcher(html);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {
            String msg = "AUTO_FISH_TRACE tied parse failed";
            AppLog.w(TAG, msg, e);
        }
        return null;
    }

    private static Integer parseMainPhpTiedFromHpmp(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        try {
            java.util.regex.Matcher hpmpMatcher = java.util.regex.Pattern
                    .compile("(?is)\\bhpmp\\b\\s*=\\s*\\[(.*?)\\]")
                    .matcher(html);
            if (hpmpMatcher.find()) {
                List<String> hpmp = MainPhp.splitJsTopLevelCsv(hpmpMatcher.group(1));
                if (hpmp.size() >= 5) {
                    int maxTire = MainPhp.parseIntFromJsToken(hpmp.get(4), Integer.MIN_VALUE);
                    if (maxTire != Integer.MIN_VALUE) {
                        return Math.max(0, Math.min(100, 100 - maxTire));
                    }
                }
            }
            java.util.regex.Matcher maxTireMatcher = java.util.regex.Pattern
                    .compile("(?is)[\"']?maxTire[\"']?\\s*[:=]\\s*[\"']?(\\d{1,3})[\"']?")
                    .matcher(html);
            if (maxTireMatcher.find()) {
                int maxTire = Integer.parseInt(maxTireMatcher.group(1));
                return Math.max(0, Math.min(100, 100 - maxTire));
            }
        } catch (Exception e) {
            String msg = "AUTO_FISH_TRACE hpmp tied parse failed";
            AppLog.w(TAG, msg, e);
        }
        return null;
    }

    /** C# parity (MainPhpFindDrink): на карте вставляет вызов Drink('vcode') после view_map(). */
    static String mainPhpFindDrink(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String pattern = "[\"\u0064\u0072\u0069\",\"\u041F\u0438\u0442\u044C\",\"";
        int posPattern = html.indexOf(pattern);
        if (posPattern == -1) {
            return null;
        }
        posPattern += pattern.length();
        int posEnd = html.indexOf('"', posPattern);
        if (posEnd == -1) {
            return null;
        }
        String vcode = html.substring(posPattern, posEnd);
        if (vcode.isEmpty()) {
            return null;
        }
        String callDrink = "Drink('" + vcode + "');";
        String patternViewMap = "view_map();";
        int posScript = html.indexOf(patternViewMap);
        if (posScript == -1) {
            return null;
        }
        posScript += patternViewMap.length();
        return html.substring(0, posScript) + callDrink + html.substring(posScript);
    }

    /** C# parity (MainPhpFindFish): на карте вставляет вызов Fish('vcode') после view_map(). */
    static String mainPhpFindFish(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String pattern = "[\"\u0066\u0069\u0073\",\"\u0420\u044B\u0431\u0430\u043B\u043A\u0430\",\"";
        int posPattern = html.indexOf(pattern);
        if (posPattern == -1) {
            return null;
        }
        posPattern += pattern.length();
        int posEnd = html.indexOf('"', posPattern);
        if (posEnd == -1) {
            return null;
        }
        String vcode = html.substring(posPattern, posEnd);
        if (vcode.isEmpty()) {
            return null;
        }
        String callFish = "Fish('" + vcode + "');";
        String patternViewMap = "view_map();";
        int posScript = html.indexOf(patternViewMap);
        if (posScript == -1) {
            return null;
        }
        posScript += patternViewMap.length();
        if (AppVars.ContentLakeHtml == null || AppVars.ContentLakeHtml.isEmpty()) {
            String lakeForm = extractLakeFishFormHtml(html);
            if (lakeForm != null && !lakeForm.isEmpty()) {
                AppVars.ContentLakeHtml = lakeForm;
                AppVars.ContentLakeHtmlLastUpdateAtMs = System.currentTimeMillis();
                String msg = "AUTO_FISH_TRACE cached ContentLakeHtml, length=";
                AppLog.d(TAG, msg);
            }
        }
        return html.substring(0, posScript) + callFish + html.substring(posScript);
    }

    /** Извлекает HTML формы озера из полной страницы. */
    static String extractLakeFishFormHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        String lowerHtml = html.toLowerCase(java.util.Locale.ROOT);
        int posForm = lowerHtml.indexOf("id=\"fishf\"");
        if (posForm == -1) {
            posForm = lowerHtml.indexOf("id='fishf'");
        }
        if (posForm == -1) {
            return "";
        }
        int formStart = html.lastIndexOf("<form", posForm);
        if (formStart == -1) return "";
        int formEnd = html.indexOf("</form>", formStart);
        if (formEnd == -1) return "";
        formEnd += "</form>".length();
        return html.substring(formStart, formEnd);
    }

    /** Шаг контроля усталости авто-рыбалки (C# parity mainPhpAutoFishFatigueStep). */
    static String mainPhpAutoFishFatigueStep(String html) {
        if (AppVars.Profile == null || html == null || html.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        long cooldownRemainingMs = getAutoFishDrinkCooldownRemainingMs(now);
        if (AppVars.AutoFishDrinkOnce || cooldownRemainingMs > 0L) {
            if (cooldownRemainingMs > 0L) {
                // Проверяем: если усталость уже ниже порога (эликсир подействовал),
                // не устанавливаем pending — иначе бесконечный цикл resolve→fatigue→resolve.
                // Полностью очищаем drink-state чтобы рыбалка возобновилась немедленно.
                int currentTied = CharacterVitalsManager.snapshot().tied;
                int tiedHigh = Math.max(0, Math.min(99, AppVars.Profile.FishTiedHigh));
                if (currentTied <= tiedHigh) {
                    AppLog.d(TAG, "AUTO_FISH_TRACE drink cooldown active but tied=" + currentTied
                            + " <= " + tiedHigh + ", elixir resolved — clearing drink state to resume fishing");
                    // Полная очистка drink-state (как при cooldown elapsed):
                    AppVars.AutoFishDrinkOnce = false;
                    lastAutoFishDrinkTriggerAtMs = 0L;
                    AppVars.AutoDrinkBlazPending = false;
                    AppVars.AutoFishDrink = false;
                    if (AppVars.FastNeed) {
                        FastActionManager.fastCancel("elixir_resolved_during_cooldown");
                        AppLog.d(TAG, "AUTO_FISH_TRACE cancel FastNeed after elixir resolved");
                    }
                    if (FishAjaxPhp.isAutoFishEnabled()) {
                        AppVars.ProbeForceNeedAutofish = true;
                        AppLog.d(TAG, "AUTO_FISH_TRACE elixir resolved during cooldown, forcing autofish probe");
                        // Гарантируем перезапуск цикла: ProbeForceNeedAutofish ждёт af_tick,
                        // но NeverTimer мог истечь пока был FastNeed — cycle token устарел.
                        scheduleFreshFishCycleKick(300L);
                    }
                    return null;
                }
                AppVars.AutoDrinkBlazPending = true;
                if (!AppVars.AutoFishDrinkOnce) {
                    String msg_recoverCooldown = "AUTO_FISH_TRACE restored drink cooldown by timestamp: remainingMs="
                            + cooldownRemainingMs;
                    AppLog.d(TAG, msg_recoverCooldown);
                }
                AppVars.AutoFishDrinkOnce = true;
                long elapsed = Math.max(0L, now - lastAutoFishDrinkTriggerAtMs);
                AppLog.d(TAG, "AUTO_FISH_TRACE drink cooldown active: elapsedMs="
                        + elapsed + ", remainingMs=" + cooldownRemainingMs);
                return buildAutoFishDrinkCooldownHtml(cooldownRemainingMs);
            }
            long elapsed = Math.max(0L, now - lastAutoFishDrinkTriggerAtMs);
            AppLog.d(TAG, "AUTO_FISH_TRACE drink cooldown elapsed: elapsedMs="
                    + elapsed + ", recheck tied");
            AppVars.AutoFishDrinkOnce = false;
            if (AppVars.FastNeed) {
                FastActionManager.fastCancel("elixir_cooldown_finished");
                String msg_cancel = "AUTO_FISH_TRACE cooldown finished, cancel FastNeed to resume autofish";
                AppLog.d(TAG, msg_cancel);
            }
            if (FishAjaxPhp.isAutoFishEnabled()) {
                AppVars.ProbeForceNeedAutofish = true;
                String msg = "AUTO_FISH_TRACE drink cooldown finished, forcing autofish probe";
                AppLog.d(TAG, msg);
                // Гарантируем перезапуск: если NeverTimer истёк раньше конца cooldown'а, cycle мёртв.
                scheduleFreshFishCycleKick(300L);
            }
        }
        mainPhpUpdateTied(html);

        int tied = CharacterVitalsManager.snapshot().tied;
        int tiedHigh = Math.max(0, Math.min(99, AppVars.Profile.FishTiedHigh));
        if (!AppVars.AutoFishDrink) {
            AppVars.AutoFishDrink = tied > tiedHigh && AppVars.Profile.FishTiedZero;
        }
        boolean needDrinkStep = tied > tiedHigh || AppVars.AutoFishDrink;
        if (!needDrinkStep) {
            return null;
        }

        if (AppVars.Profile.FishDrinkBliss) {
            if (AppVars.FastNeed) {
                AppLog.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
                        + ", wait bliss: FastNeed=true");
                return MainPhp.buildRedirectHtml("\u0410\u0432\u0442\u043E\u0440\u044B\u0431\u0430\u043B\u043A\u0430: \u043E\u0436\u0438\u0434\u0430\u043D\u0438\u0435 \u044D\u043B\u0438\u043A\u0441\u0438\u0440\u0430", "main.php");
            }
            long sinceLast = now - lastAutoFishBlazTriggerAtMs;
            if (sinceLast >= 0 && sinceLast < AUTO_FISH_BLAZ_TRIGGER_COOLDOWN_MS) {
                AppLog.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
                        + ", bliss cooldown " + sinceLast + "ms");
                return MainPhp.buildRedirectHtml("\u0410\u0432\u0442\u043E\u0440\u044B\u0431\u0430\u043B\u043A\u0430: \u043E\u0436\u0438\u0434\u0430\u043D\u0438\u0435 \u044D\u043B\u0438\u043A\u0441\u0438\u0440\u0430", "main.php");
            }
            lastAutoFishBlazTriggerAtMs = now;
            lastAutoFishDrinkTriggerAtMs = now;
            AppVars.AutoFishDrinkOnce = true;
            AppLog.d(TAG, "[AUTO_FISH_BEFORE_FAST] \u041F\u0415\u0420\u0415\u0414 fastStart: "
                    + "tied=" + tied + ", tiedHigh=" + tiedHigh
                    + " | FastNeed=" + AppVars.FastNeed
                    + ", FastId='" + AppVars.FastId + "'"
                    + ", timestamp=" + now);
            AppLog.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
                    + ", trigger bliss elixir");
            AppVars.AutoDrinkBlazPending = true;
            FastActionManager.fastAttackBlazElixir("\u0410\u0432\u0442\u043E-\u0420\u044B\u0431\u0430\u043B\u043A\u0430");
            AppLog.d(TAG, "[AUTO_FISH_AFTER_FAST] \u041F\u041E\u0421\u041B\u0415 fastStart: "
                    + "FastNeed=" + AppVars.FastNeed
                    + ", FastId='" + AppVars.FastId + "'"
                    + ", FastCount=" + AppVars.FastCount
                    + ", timestamp=" + System.currentTimeMillis());
            return MainPhp.buildRedirectHtml("\u0410\u0432\u0442\u043E\u0440\u044B\u0431\u0430\u043B\u043A\u0430: \u042D\u043B\u0438\u043A\u0441\u0438\u0440 \u0411\u043B\u0430\u0436\u0435\u043D\u0441\u0442\u0432\u0430", "main.php");
        }

        String drinkHtml = mainPhpFindDrink(html);
        if (drinkHtml != null && !drinkHtml.isEmpty()) {
            lastAutoFishDrinkTriggerAtMs = now;
            AppVars.AutoFishDrinkOnce = true;
            AppLog.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
                    + ", inject Drink(vcode)");
            return drinkHtml;
        }

        String floraHtml = MainPhp.mainPhpFindFlora(html);
        if (floraHtml != null && !floraHtml.isEmpty()) {
            AppLog.d(TAG, "AUTO_FISH_TRACE tied=" + tied + " > " + tiedHigh
                    + ", redirect to map for Drink");
            return floraHtml;
        }
        return null;
    }

    /** Резолвит pending состояние после питья Эликсира Блаженства. */
    static String mainPhpResolveAutoDrinkBlazPending(String address, String html) {
        if (!AppVars.AutoDrinkBlazPending) {
            return null;
        }
        if (AppVars.FastNeed || AppVars.IsFightCaptchaDialogVisible) {
            return null;
        }
        long now = System.currentTimeMillis();
        AutoFishInfoApiPrecheckState autoFishPrecheckState = null;
        if (isAutoFishEnabledByPreference()) {
            autoFishPrecheckState = mainPhpPrecheckFishingHandsByInfoApi(
                    now, address, "mainphp_blaz_pending");
        } else {
            mainPhpUpdateTied(html);
        }
        int tied = CharacterVitalsManager.snapshot().tied;
        if (tied <= 0) {
            AppVars.AutoDrinkBlazPending = false;
            AppVars.AutoFishDrinkOnce = false;
            if (FishAjaxPhp.isAutoFishEnabled()) {
                AppVars.ProbeForceNeedAutofish = true;
                // FIX: Invalidate stale fishing cycle token — old JS-kick retries
                // fire on a page where the game AJAX doesn't work after redirect chain.
                lastFishCycleToken = 0L;
            }
            AppLog.i(TAG, "AUTO_BLAZ_TRACE pending resolved: tied=" + tied
                    + ", address=" + address + ", resume autos");
            // FIX: Redirect to fishing info page (not plain main.php) so the
            // interceptor calls process() and the auto-fish gate can restart.
            return MainPhp.buildRedirectHtml("\u0410\u0432\u0442\u043E\u043F\u0438\u0442\u044C\u0451 \u0431\u043B\u0430\u0436\u0430: \u0443\u0441\u0442\u0430\u043B\u043E\u0441\u0442\u044C \u0432\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D\u0430", "main.php?get_id=56&act=10&go=inf");
        }
        long cooldownRemainingMs = getAutoFishDrinkCooldownRemainingMs(now);
        if (cooldownRemainingMs > 0L) {
            AppLog.d(TAG, "AUTO_BLAZ_TRACE pending wait: tied=" + tied
                    + ", cooldownRemainingMs=" + cooldownRemainingMs
                    + ", address=" + address);
            return buildAutoFishDrinkCooldownHtml(cooldownRemainingMs);
        }
        boolean autoFishNeedsNextDrink = autoFishPrecheckState != null
                && autoFishPrecheckState.needFatigueStep;
        int autoDrinkThreshold = AppVars.Profile == null
                ? 101
                : Math.max(0, Math.min(100, AppVars.Profile.AutoDrinkBlazTied));
        boolean autoDrinkNeedsNextDrink = AppVars.Profile != null
                && AppVars.Profile.DoAutoDrinkBlaz
                && !AppVars.AutoMoving
                && tied >= autoDrinkThreshold;
        // Во время навигации blaz решает MapAjax; если tied всё ещё >= порога,
        // НЕ резолвим pending — иначе бесконечный цикл resolve→MapAjax defer→resolve.
        boolean navStillAboveThreshold = AppVars.AutoMoving
                && AppVars.Profile != null
                && AppVars.Profile.DoAutoDrinkBlaz
                && tied >= autoDrinkThreshold;
        if (autoFishNeedsNextDrink || autoDrinkNeedsNextDrink || navStillAboveThreshold) {
            AppLog.d(TAG, "AUTO_BLAZ_TRACE pending handoff: tied=" + tied
                    + ", fishNeed=" + autoFishNeedsNextDrink
                    + ", autoDrinkNeed=" + autoDrinkNeedsNextDrink
                    + ", navAbove=" + navStillAboveThreshold
                    + ", address=" + address);
            return null;
        }
        // tied > 0 но ниже обоих порогов → эликсир подействовал, резолвим pending
        AppVars.AutoDrinkBlazPending = false;
        AppVars.AutoFishDrinkOnce = false;
        if (FishAjaxPhp.isAutoFishEnabled()) {
            AppVars.ProbeForceNeedAutofish = true;
            // FIX: Invalidate stale fishing cycle token (same as tied<=0 path)
            lastFishCycleToken = 0L;
        }
        AppLog.i(TAG, "AUTO_BLAZ_TRACE pending resolved (below thresholds): tied=" + tied
                + ", autoDrinkThreshold=" + autoDrinkThreshold
                + ", address=" + address + ", resume autos");
        // FIX: Redirect to fishing info page (not plain main.php)
        return MainPhp.buildRedirectHtml("\u0410\u0432\u0442\u043E\u043F\u0438\u0442\u044C\u0451 \u0431\u043B\u0430\u0436\u0430: \u0443\u0441\u0442\u0430\u043B\u043E\u0441\u0442\u044C \u043D\u0438\u0436\u0435 \u043F\u043E\u0440\u043E\u0433\u0430", "main.php?get_id=56&act=10&go=inf");
    }

    /**
     * MainPhp-side оркестратор авто-рыбалки после выноса из MainPhp.process().
     *
     * Почему метод возвращает byte[]:
     * - большинство веток генерирует новый HTML и кодирует его через Russian.getBytes(...);
     * - ветка anti-loop `wearLoopBroken` должна вернуть исходный `originalBytes`, потому что в старом
     *   MainPhp-блоке был прямой `return array` после restartAutoFishCycle("wear_loop").
     *
     * Входные переменные:
     * - address: текущий URL main.php, используется для фильтров `&im=...`, `get_id=55`, `act=4`.
     * - html: текущий HTML main.php после декодирования и Filter.removeDoctype.
     * - originalBytes: исходный ответ `array` из MainPhp.process(), нужен только для сохранения старого return-path.
     * - isFightFrame/isFightTopFrame: блокируют non-combat AutoFish внутри боя.
     * - autoFightReloadProbeAddress: подавляет рыбалку на `ab_reload_probe`, чтобы не конфликтовать с авто-боем.
     *
     * Runtime-зависимости и флаги:
     * - MainPhp.isNonCombatAutoPausedByFastAction(): учитывает AppVars.FastNeed/FastPauseNonCombatAutoFunctions.
     * - AppVars.AutoFishCheckUm: запускает переход на персонажа и `mselect=1` для чтения навыка Рыбалка.
     * - AppVars.AutoFishCheckUd/AppVars.AutoFishWearUd: проверка/надевание снастей через `&im=0&wca=4`.
     * - AppVars.AutoFishHand1/AutoFishHand1D/AutoFishHand2/AutoFishHand2D: диагностическое состояние снастей.
     * - AppVars.NeverTimer: серверный cooldown перед следующим non-combat действием.
     * - AppVars.CodeAddress/AppVars.FightLink/IsFightCaptchaDialogVisible: капча и финальный action-заброс.
     *
     * Порядок действий сохранён из MainPhp:
     * recovery на карте -> blocked diagnostics -> InfoApi precheck -> fatigue step -> skill step -> drink cooldown
     * -> gear check/wear -> return to map -> lake form prepare -> captcha hold/redirect to AppVars.FightLink.
     */
    static byte[] processMainPhpAutoFishPipeline(String address,
                                                 String html,
                                                 byte[] originalBytes,
                                                 boolean isFightFrame,
                                                 boolean isFightTopFrame,
                                                 boolean autoFightReloadProbeAddress) {
        boolean isOnMapOrNature = MainPhp.mainPhpFindFlora(html) == null;
        if (isOnMapOrNature && FishAjaxPhp.isAutoFishEnabledByPreference()) {
            FishAjaxPhp.recoverAutofishRuntimeStateIfNeeded(true, html, address);
        }

        boolean isFastActionPaused = MainPhp.isNonCombatAutoPausedByFastAction();
        boolean shouldEnterFishingLogic = !isFastActionPaused && !isFightFrame && !isFightTopFrame
                && !autoFightReloadProbeAddress && FishAjaxPhp.isAutoFishEnabledByPreference();
        if (!shouldEnterFishingLogic && FishAjaxPhp.isAutoFishEnabledByPreference()) {
            StringBuilder diagnostics = new StringBuilder();
            diagnostics.append("AUTO_FISH_TRACE BLOCKED: ");
            diagnostics.append("FastNeed=").append(isFastActionPaused).append(", ");
            diagnostics.append("isFightFrame=").append(isFightFrame).append(", ");
            diagnostics.append("isFightTopFrame=").append(isFightTopFrame).append(", ");
            diagnostics.append("autoFightProbe=").append(autoFightReloadProbeAddress);
            String msgBlock = diagnostics.toString();
            AppLog.d(TAG, msgBlock);
        }

        if (!MainPhp.isNonCombatAutoPausedByFastAction() && !isFightFrame && !isFightTopFrame
                && !autoFightReloadProbeAddress && FishAjaxPhp.isAutoFishEnabledByPreference()) {
            long nowMs = System.currentTimeMillis();
            FishAjaxPhp.mainPhpPrecheckFishingHandsByInfoApi(nowMs, address, "mainphp_autofish_gate");
            String fishFatigueHtml = FishAjaxPhp.mainPhpAutoFishFatigueStep(html);
            if (fishFatigueHtml != null && !fishFatigueHtml.isEmpty()) {
                String msgFishFatigue = "AUTO_FISH_TRACE fatigue step executed";
                AppLog.d(TAG, msgFishFatigue);
                return Russian.getBytes(fishFatigueHtml);
            }
            boolean neverTimerReady = AppVars.NeverTimer <= 0L || nowMs > AppVars.NeverTimer;
            if (neverTimerReady) {
                if (AppVars.AutoFishCheckUm) {
                    String phtml = MainPhp.mainPhpFindPerc(html);
                    if (phtml != null && !phtml.isEmpty()) {
                        String msgFishChar = "AUTO_FISH_TRACE redirect to character page for skill check";
                        AppLog.d(TAG, msgFishChar);
                        return Russian.getBytes(phtml);
                    }
                    if (html.toLowerCase(Locale.ROOT).contains("<input type=button class=lbut value=\"умения\" onclick")) {
                        String msgFishSkills = "AUTO_FISH_TRACE redirect to skills page mselect=1";
                        AppLog.d(TAG, msgFishSkills);
                        return Russian.getBytes(MainPhp.buildRedirectHtml("Переключение на умения персонажа", "main.php?mselect=1"));
                    }
                }
                long postDrinkCooldownRemainingMs = FishAjaxPhp.getAutoFishDrinkCooldownRemainingMs(nowMs);
                if (postDrinkCooldownRemainingMs > 0L) {
                    String msgDeferFish = "AUTO_FISH_TRACE defer non-fight fish steps during drink cooldown: remainingMs="
                            + postDrinkCooldownRemainingMs
                            + ", AutoFishCheckUd=" + AppVars.AutoFishCheckUd
                            + ", AutoFishWearUd=" + AppVars.AutoFishWearUd
                            + ", address=" + address;
                    AppLog.d(TAG, msgDeferFish);
                    return Russian.getBytes(FishAjaxPhp.buildAutoFishDrinkCooldownHtml(postDrinkCooldownRemainingMs));
                }
                if (AppVars.AutoFishCheckUd) {
                    String perchtml = MainPhp.mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        String msgFishGear = "AUTO_FISH_TRACE redirect to character page for fishing gear check";
                        AppLog.d(TAG, msgFishGear);
                        return Russian.getBytes(perchtml);
                    }
                    AppVars.AutoFishWearUd = false;
                    if (MainPhp.mainPhpIsPerc(html)) {
                        AppVars.AutoFishWearUd = FishAjaxPhp.mainPhpIsMustWearUd(html);
                        AppVars.AutoFishCheckUd = false;
                        if (AppVars.AutoFishWearUd) {
                            String loopKey = FishAjaxPhp.buildAutoFishWearLoopKey();
                            boolean wearLoopBroken = FishAjaxPhp.markAutoFishWearLoop(loopKey);
                            if (wearLoopBroken) {
                                FishAjaxPhp.restartAutoFishCycle("wear_loop");
                                return originalBytes;
                            }
                        } else {
                            FishAjaxPhp.resetAutoFishWearLoopGuard();
                        }
                        String msgGearResult = "AUTO_FISH_TRACE gear check result: mustWear="
                                + AppVars.AutoFishWearUd
                                + ", hand1=" + AppVars.AutoFishHand1
                                + ", hand1D=" + AppVars.AutoFishHand1D
                                + ", hand2=" + AppVars.AutoFishHand2
                                + ", hand2D=" + AppVars.AutoFishHand2D;
                        AppLog.d(TAG, msgGearResult);
                    }
                }
                if (AppVars.AutoFishWearUd) {
                    String invHtml = MainPhp.mainPhpFindInvWithFallback(html, "&im=0&wca=4", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        String msgFishUdRed = "AUTO_FISH_TRACE redirect to inventory for fishing gear (&im=0&wca=4)";
                        AppLog.d(TAG, msgFishUdRed);
                        return Russian.getBytes(invHtml);
                    }
                    if (MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address)) {
                        invHtml = FishAjaxPhp.mainPhpWearUd(html);
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!MainPhp.inventoryAddressMatchesFilter(address, "&im=0&wca=4")) {
                                String msgFishUdSwitch = "AUTO_FISH_TRACE switch to items tab for fishing gear search";
                                AppLog.d(TAG, msgFishUdSwitch);
                                return Russian.getBytes(MainPhp.buildRedirectHtml("Переключение на вещи", "main.php?im=0&wca=4"));
                            }
                        } else {
                            return Russian.getBytes(invHtml);
                        }
                    }
                }
                if (!neverTimerReady) {
                    String msgWaitFish = "AUTO_FISH_TRACE wait NeverTimer before fish action: dueInMs="
                            + Math.max(0L, AppVars.NeverTimer - nowMs);
                    AppLog.d(TAG, msgWaitFish);
                } else {
                    String floraHtml = MainPhp.mainPhpFindFlora(html);
                    if (floraHtml != null && !floraHtml.isEmpty()) {
                        String msgFloraReturn = "AUTO_FISH_TRACE redirect to nature/map via return button";
                        AppLog.d(TAG, msgFloraReturn);
                        return Russian.getBytes(floraHtml);
                    }
                    boolean isWeAlreadyOnLake = html.contains("name=primid") || html.contains("name=\"primid\"");

                    if (isWeAlreadyOnLake) {
                        String msgOnLake = "AUTO_FISH_TRACE detected lake form (name=primid found), calling mainPhpAutoFishPrepare...";
                        AppLog.d(TAG, msgOnLake);

                        String fishPreparedHtml = FishAjaxPhp.mainPhpAutoFishPrepare(html);

                        String msgAfterPrepare = "AUTO_FISH_TRACE mainPhpAutoFishPrepare: result is " + (fishPreparedHtml == null ? "NULL" : "non-null");
                        AppLog.d(TAG, msgAfterPrepare);

                        if (fishPreparedHtml != null) {
                            html = fishPreparedHtml;
                            boolean hasCaptcha = AppVars.CodeAddress != null && !AppVars.CodeAddress.isEmpty();
                            boolean isFishActionAddress = address != null
                                    && address.contains("get_id=55")
                                    && address.contains("act=4");
                            if (hasCaptcha && AppVars.FightLink != null && !AppVars.FightLink.isEmpty() && !isFishActionAddress) {
                                String msgFishCapt = "AUTO_FISH_TRACE captcha required, show dialog for fish action";
                                AppLog.d(TAG, msgFishCapt);
                                FishAjaxPhp.showMainPhpFishCaptchaDialogOnce(AppVars.CodeAddress, AppVars.FightLink);
                                return Russian.getBytes(FishAjaxPhp.buildCaptchaDialogHoldHtml());
                            }
                            if (hasCaptcha && AppVars.IsFightCaptchaDialogVisible) {
                                String msgFishCaptHold = "AUTO_FISH_TRACE captcha dialog is visible, keep hold page";
                                AppLog.d(TAG, msgFishCaptHold);
                                return Russian.getBytes(FishAjaxPhp.buildCaptchaDialogHoldHtml());
                            }
                            if (!hasCaptcha && AppVars.FightLink != null && !AppVars.FightLink.isEmpty() && !isFishActionAddress) {
                                String msgFishAction = "AUTO_FISH_TRACE redirect to fish action: ";
                                AppLog.d(TAG, msgFishAction);
                                return Russian.getBytes(MainPhp.buildRedirectHtml("Авторыбалка: заброс", AppVars.FightLink));
                            }
                        }
                    } else {
                        String msgNotLake = "AUTO_FISH_TRACE no lake form detected (name=primid NOT found), injecting Fish()...";
                        AppLog.d(TAG, msgNotLake);

                        String fishMapHtml = FishAjaxPhp.mainPhpFindFish(html);
                        if (fishMapHtml != null && !fishMapHtml.isEmpty()) {
                            String msgFishMap = "AUTO_FISH_TRACE inject Fish(vcode) into map frame";
                            AppLog.d(TAG, msgFishMap);
                            return Russian.getBytes(fishMapHtml);
                        }
                        String msgNoFish = "AUTO_FISH_TRACE warning: Fish button not found on current page, skipping auto-fish";
                        AppLog.w(TAG, msgNoFish);
                    }
                }
            } else {
                String msgWaitFish = "AUTO_FISH_TRACE wait NeverTimer before fish action: dueInMs="
                        + Math.max(0L, AppVars.NeverTimer - nowMs);
                AppLog.d(TAG, msgWaitFish);
            }
        }
        return null;
    }

    /** C# parity: DoAutoDrinkBlaz — авто-питьё Эликсира Блаженства при высокой усталости. */
    static String mainPhpAutoDrinkBlazStep(String address, String html) {
        if (AppVars.Profile == null || html == null || html.isEmpty()) {
            return null;
        }
        if (!AppVars.Profile.DoAutoDrinkBlaz) {
            return null;
        }
        if (AppVars.AutoMoving) {
            return null;
        }
        if (AppVars.IsFightCaptchaDialogVisible) {
            return null;
        }
        long now = System.currentTimeMillis();
        // КРИТИЧНО: проверять tied ДО NeverTimer, чтобы при tied < порога
        // не устанавливать AutoDrinkBlazPending (иначе бесконечный цикл resolve→NeverTimer→resolve).
        int tied = CharacterVitalsManager.snapshot().tied;
        int tiedThreshold = Math.max(0, Math.min(100, AppVars.Profile.AutoDrinkBlazTied));
        if (tied < tiedThreshold) {
            return null;
        }
        if (!AppVars.AutoDrinkBlazPending && AppVars.NeverTimer > 0L && now < AppVars.NeverTimer) {
            AppVars.AutoDrinkBlazPending = true;
            AppLog.d(TAG, "AUTO_BLAZ_TRACE skipped by NeverTimer: dueInMs="
                    + Math.max(0L, AppVars.NeverTimer - now) + ", tied=" + tied
                    + ", threshold=" + tiedThreshold + ", address=" + address);
            return MainPhp.buildRedirectHtml("\u0410\u0432\u0442\u043E\u043F\u0438\u0442\u044C\u0451 \u0431\u043B\u0430\u0436\u0430: \u043E\u0436\u0438\u0434\u0430\u043D\u0438\u0435 \u0442\u0430\u0439\u043C\u0435\u0440\u0430", "main.php");
        }
        if (AppVars.FastNeed) {
            if (BLISS_ELIXIR_NAME.equals(AppVars.FastId)) {
                return MainPhp.buildRedirectHtml(
                        "\u0410\u0432\u0442\u043E\u043F\u0438\u0442\u044C\u0435 \u0431\u043B\u0430\u0436\u0430: \u043E\u0436\u0438\u0434\u0430\u043D\u0438\u0435 \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u0438\u044F",
                        "main.php");
            }
            return null;
        }
        long sinceLastTrigger = now - lastAutoDrinkBlazTriggerAtMs;
        if (sinceLastTrigger >= 0L && sinceLastTrigger < 2500L) {
            return null;
        }
        lastAutoDrinkBlazTriggerAtMs = now;
        AppVars.AutoDrinkBlazPending = true;
        AppLog.d(TAG, "AUTO_BLAZ_TRACE trigger quick action: " + BLISS_ELIXIR_NAME
                + ", tied=" + tied + ", threshold=" + tiedThreshold);
        FastActionManager.fastAttackBlazElixir("\u0410\u0432\u0442\u043E-\u043F\u0438\u0442\u044C\u0451 \u0411\u043B\u0430\u0436\u0430");
        return MainPhp.buildRedirectHtml(
                "\u0410\u0432\u0442\u043E\u043F\u0438\u0442\u044C\u0435 \u0431\u043B\u0430\u0436\u0430: " + BLISS_ELIXIR_NAME,
                "main.php");
    }

    /** Формирует HTML-redirect для автопитья с countdown. */
    static String buildAutoFishDrinkCooldownHtml(long remainingMs) {
        long safeRemaining = Math.max(0L, remainingMs);
        int delayMs = (int) Math.max(900L, Math.min((long) AUTO_FISH_DRINK_POLL_DELAY_MS, safeRemaining));
        long seconds = (safeRemaining + 999L) / 1000L;
        return HtmlUtils.GENERATED_PAGE_MARKER
                + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                + "<title>ANClient</title></head><body>"
                + "\u0410\u0432\u0442\u043E\u0440\u044B\u0431\u0430\u043B\u043A\u0430: \u0433\u043B\u043E\u0442\u043E\u043A \u0432 \u0440\u0430\u0431\u043E\u0442\u0435, \u043E\u0436\u0438\u0434\u0430\u043D\u0438\u0435 " + seconds + " \u0441..."
                + "<script language=\"JavaScript\">"
                + "setTimeout(function(){ window.location = 'main.php'; }, " + delayMs + ");"
                + "</script></body></html>";
    }

    /** Возвращает остаток серверного cooldown после шага "Пить" в авто-рыбалке. */
    static long getAutoFishDrinkCooldownRemainingMs(long nowMs) {
        long triggerAt = lastAutoFishDrinkTriggerAtMs;
        if (triggerAt <= 0L) {
            return 0L;
        }
        long elapsed = nowMs - triggerAt;
        if (elapsed < 0L) {
            return AUTO_FISH_DRINK_SERVER_COOLDOWN_MS;
        }
        if (elapsed >= AUTO_FISH_DRINK_SERVER_COOLDOWN_MS) {
            return 0L;
        }
        return AUTO_FISH_DRINK_SERVER_COOLDOWN_MS - elapsed;
    }

    // ======================================================================
    // Методы проверки/переодевания снастей (перенесены из MainPhp)
    // ======================================================================

    /** C# parity: проверка, нужно ли переодевать снасти авто-рыбалки. */
    static boolean mainPhpIsMustWearUd(String html) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        boolean isWear1 = parsedDressed.IsWear1();
        if (!isWear1 && AppVars.Profile != null && AppVars.Profile.FishAutoWear) {
            return true;
        }
        boolean isWear2 = parsedDressed.IsWear2();
        if (!isWear2 && AppVars.Profile != null && AppVars.Profile.FishAutoWear) {
            return true;
        }
        if (FishAjaxPhp.isFishingGearBroken(html)) {
            String msg = "[FISH_GEAR_CHECK_REJECTED] gear is broken, need re-equip";
            AppLog.w(TAG, msg);
            return true;
        }
        return false;
    }

    /** C# parity: авто-надевание снастей в обе руки (MainPhpWearUd). */
    static String mainPhpWearUd(String html) {
        ParsedDressed ud = new ParsedDressed(html);
        if (AppVars.Profile == null) {
            return null;
        }
        boolean dressedValid = ud.Valid;
        List<ru.neverlands.anclient.postfilter.InventoryParser.WearInvEntry> invList = ru.neverlands.anclient.postfilter.InventoryParser.getWearInvList(html);
        if (!dressedValid) {
            AppLog.w(TAG, "AUTO_FISH_TRACE wear: ParsedDressed invalid on inventory page, fallback to inventory-only mode");
        }
        boolean hand1DisabledByProfile = isNoFishHandSetting(AppVars.Profile.FishHandOne);
        boolean isWear1 = hand1DisabledByProfile || (dressedValid && ud.IsWear1());
        if (!isWear1 && AppVars.Profile.FishAutoWear) {
            for (ru.neverlands.anclient.postfilter.InventoryParser.WearInvEntry thing : invList) {
                if (thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) continue;
                if (ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(AppVars.Profile.FishHandOne, "\u041B\u044E\u0431\u0430\u044F \u0443\u0434\u043E\u0447\u043A\u0430")) {
                    if (ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(thing.name, "\u0443\u0434\u043E\u0447\u043A\u0430") || ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(thing.name, "\u0441\u043F\u0438\u043D\u043D\u0438\u043D\u0433")) {
                        notifyAutoFishRodWear(thing.name);
                        AppLog.d(TAG, "AUTO_FISH_TRACE wear action: hand=1, item="
                                + thing.name + ", link=" + thing.wearLink);
                        return MainPhp.buildRedirectHtml("\u041E\u0434\u0435\u0432\u0430\u0435\u043C \u043F\u0435\u0440\u0432\u0443\u044E \u043F\u043E\u043F\u0430\u0432\u0448\u0443\u044E\u0441\u044F \u0443\u0434\u043E\u0447\u043A\u0443", thing.wearLink);
                    }
                } else if (ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(thing.name, AppVars.Profile.FishHandOne)) {
                    notifyAutoFishRodWear(thing.name);
                    AppLog.d(TAG, "AUTO_FISH_TRACE wear action: hand=1, item="
                            + thing.name + ", link=" + thing.wearLink);
                    return MainPhp.buildRedirectHtml(AppVars.Profile.FishHandOne + " \u043E\u0434\u0435\u0432\u0430\u0435\u0442\u0441\u044F", thing.wearLink);
                }
            }
            disableAutoFishMain("\u041D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D \u043F\u0440\u0435\u0434\u043C\u0435\u0442 \u0434\u043B\u044F \u043F\u0435\u0440\u0432\u043E\u0439 \u0440\u0443\u043A\u0438");
            return null;
        }
        boolean isWear2 = dressedValid && ud.IsWear2();
        if (!isWear2 && AppVars.Profile.FishAutoWear) {
            boolean foundMatch = false;
            for (ru.neverlands.anclient.postfilter.InventoryParser.WearInvEntry thing : invList) {
                if (thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) continue;
                boolean matches;
                if (ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(AppVars.Profile.FishHandTwo, "\u041B\u044E\u0431\u0430\u044F \u0443\u0434\u043E\u0447\u043A\u0430")) {
                    matches = ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(thing.name, "\u0443\u0434\u043E\u0447\u043A\u0430") || ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(thing.name, "\u0441\u043F\u0438\u043D\u043D\u0438\u043D\u0433");
                } else {
                    matches = ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(thing.name, AppVars.Profile.FishHandTwo);
                }
                if (!matches) continue;
                foundMatch = true;
                boolean allowWearSecond = !dressedValid || (ud.Empty1 || ud.Empty2) || !ud.InRightSlot;
                if (allowWearSecond) {
                    notifyAutoFishRodWear(thing.name);
                    AppLog.d(TAG, "AUTO_FISH_TRACE wear action: hand=2, item="
                            + thing.name + ", link=" + thing.wearLink);
                    return MainPhp.buildRedirectHtml("\u041E\u0434\u0435\u0432\u0430\u0435\u043C \u0441\u043D\u0430\u0441\u0442\u044C \u0432\u043E \u0432\u0442\u043E\u0440\u0443\u044E \u0440\u0443\u043A\u0443", thing.wearLink);
                }
                if (dressedValid && ud.Wid != null && !ud.Wid.isEmpty() && ud.Vcod != null && !ud.Vcod.isEmpty()) {
                    String removeLink = "main.php?get_id=57&uid=" + ud.Wid + "&s=0&vcode=" + ud.Vcod;
                    AppLog.d(TAG, "AUTO_FISH_TRACE wear action: remove-hand1-before-hand2, item="
                            + ud.Hand1 + ", link=" + removeLink);
                    return MainPhp.buildRedirectHtml("\u0421\u043D\u0438\u043C\u0430\u0435\u043C " + ud.Hand1, removeLink);
                }
            }
            if (!foundMatch && !isNoFishHandSetting(AppVars.Profile.FishHandTwo)) {
                disableAutoFishMain("\u041D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D \u043F\u0440\u0435\u0434\u043C\u0435\u0442 \u0434\u043B\u044F \u0432\u0442\u043E\u0440\u043E\u0439 \u0440\u0443\u043A\u0438");
                return null;
            }
        }
        AppVars.AutoFishWearUd = false;
        return null;
    }

    /**
     * Предпроверка экипировки авто-рыбалки через InfoApi slots (line1).
     * Зависимости: NeverApi.getInfoApiSnapshotByNick, AppVars.Profile.FishHandOne/Two.
     */
    static AutoFishInfoApiPrecheckState mainPhpPrecheckFishingHandsByInfoApi(long nowMs,
                                                                              String address,
                                                                              String sourceModule) {
        if (AppVars.Profile == null) {
            return buildAutoFishInfoApiPrecheckState(false, false, null);
        }
        String selfNick = AppVars.Profile.UserNick == null ? "" : AppVars.Profile.UserNick.trim();
        if (selfNick.isEmpty()) {
            return buildAutoFishInfoApiPrecheckState(false, AppVars.AutoFishCheckUd || AppVars.AutoFishWearUd, null);
        }
        Integer tiedValue = CharacterVitalsManager.snapshot().tied;
        boolean queuedMustWear = AppVars.AutoFishCheckUd || AppVars.AutoFishWearUd;
        String safeSourceModule = sourceModule == null || sourceModule.trim().isEmpty()
                ? "mainphp_autofish_precheck"
                : sourceModule.trim();
        boolean isPreflightAddress = address != null && address.contains("af_preflight=1");
        boolean shouldBypassCooldown =
                queuedMustWear && (isPreflightAddress || safeSourceModule.contains("fishajax_cycle_precast"));
        if ((nowMs - lastAutoFishInfoApiPrecheckAtMs) < AUTO_FISH_INFOAPI_PRECHECK_COOLDOWN_MS
                && !shouldBypassCooldown) {
            AutoFishInfoApiPrecheckState cooldownState =
                    buildAutoFishInfoApiPrecheckState(false, queuedMustWear, tiedValue);
            AppLog.d(TAG, "AUTO_FISH_INFOAPI_PRECHECK cooldown skip: source=" + safeSourceModule
                    + ", address=" + address
                    + ", mustWear=" + cooldownState.mustWear
                    + ", needFatigueStep=" + cooldownState.needFatigueStep
                    + ", tied=" + cooldownState.tied);
            return cooldownState;
        }
        if (shouldBypassCooldown) {
            AppLog.d(TAG, "AUTO_FISH_INFOAPI_PRECHECK bypass cooldown: source=" + safeSourceModule
                    + ", address=" + address
                    + ", queuedMustWear=" + queuedMustWear);
        }
        lastAutoFishInfoApiPrecheckAtMs = nowMs;
        try {
            NeverApi.InfoApiSnapshot snapshot = NeverApi.getInfoApiSnapshotByNick(selfNick, safeSourceModule);
            if (snapshot == null || !snapshot.isValid()) {
                AutoFishInfoApiPrecheckState invalidState =
                        buildAutoFishInfoApiPrecheckState(false, queuedMustWear, tiedValue);
                AppLog.w(TAG, "AUTO_FISH_INFOAPI_PRECHECK invalid snapshot: nick=" + selfNick
                        + ", source=" + safeSourceModule
                        + ", address=" + address
                        + ", mustWear=" + invalidState.mustWear
                        + ", needFatigueStep=" + invalidState.needFatigueStep
                        + ", tied=" + invalidState.tied);
                return invalidState;
            }
            if (snapshot.hmu != null && snapshot.hmu.curTire != null) {
                CharacterVitalsManager.Snapshot tiedSnapshot = CharacterVitalsManager.updateTied(
                        snapshot.hmu.curTire,
                        "FishAjaxPhp.mainPhpPrecheckFishingHandsByInfoApi/" + safeSourceModule);
                tiedValue = tiedSnapshot.tied;
            }
            boolean mustWear = false;
            if (AppVars.Profile.FishAutoWear) {
                NeverApi.InfoApiSlot slotHand1 = snapshot.getSlot(AUTO_FISH_INFOAPI_SLOT_HAND1_INDEX);
                NeverApi.InfoApiSlot slotHand2 = snapshot.getSlot(AUTO_FISH_INFOAPI_SLOT_HAND2_INDEX);
                NeverApi.InfoApiSlot slotHand1ProbeOneBased = snapshot.getSlot(AUTO_FISH_INFOAPI_SLOT_HAND1_INDEX + 1);
                NeverApi.InfoApiSlot slotHand2ProbeOneBased = snapshot.getSlot(AUTO_FISH_INFOAPI_SLOT_HAND2_INDEX + 1);
                AppLog.d(TAG, "AUTO_FISH_INFOAPI_PRECHECK hands_parsed: hand1Setting=" + AppVars.Profile.FishHandOne
                        + ", hand2Setting=" + AppVars.Profile.FishHandTwo
                        + ", index2=" + describeInfoApiSlot(slotHand1)
                        + ", index12=" + describeInfoApiSlot(slotHand2)
                        + ", index3_probe=" + describeInfoApiSlot(slotHand1ProbeOneBased)
                        + ", index13_probe=" + describeInfoApiSlot(slotHand2ProbeOneBased)
                        + ", rods=" + buildInfoApiRodSlotsDigest(snapshot)
                        + ", totalSlots=" + (snapshot.slots == null ? 0 : snapshot.slots.size())
                        + ", sourceNick=" + snapshot.requestedNick
                        + ", sourceId=" + snapshot.playerId
                        + ", source=" + safeSourceModule);
                String[] slotNames = new String[]{
                        slotHand1 == null ? "" : slotHand1.itemName,
                        slotHand2 == null ? "" : slotHand2.itemName
                };
                Integer[] slotDurability = new Integer[]{
                        slotHand1 == null ? null : slotHand1.durability,
                        slotHand2 == null ? null : slotHand2.durability
                };
                boolean[] usedSlots = new boolean[]{false, false};
                boolean isWear1 = bindFishingHandFromInfoApi(
                        AppVars.Profile.FishHandOne, slotNames, slotDurability, usedSlots, true);
                boolean isWear2 = bindFishingHandFromInfoApi(
                        AppVars.Profile.FishHandTwo, slotNames, slotDurability, usedSlots, false);
                boolean slot1MatchesHand1 = matchesFishingHandSetting(slotNames[0], slotDurability[0], AppVars.Profile.FishHandOne);
                boolean slot2MatchesHand1 = matchesFishingHandSetting(slotNames[1], slotDurability[1], AppVars.Profile.FishHandOne);
                boolean slot1MatchesHand2 = matchesFishingHandSetting(slotNames[0], slotDurability[0], AppVars.Profile.FishHandTwo);
                boolean slot2MatchesHand2 = matchesFishingHandSetting(slotNames[1], slotDurability[1], AppVars.Profile.FishHandTwo);
                mustWear = !(isWear1 && isWear2);
                AppLog.d(TAG, "AUTO_FISH_INFOAPI_PRECHECK state: hand1Setting=" + AppVars.Profile.FishHandOne
                        + ", hand2Setting=" + AppVars.Profile.FishHandTwo
                        + ", slot1=" + slotNames[0] + " (" + formatInfoApiDurability(slotDurability[0]) + ")"
                        + ", slot2=" + slotNames[1] + " (" + formatInfoApiDurability(slotDurability[1]) + ")"
                        + ", isWear1=" + isWear1 + ", isWear2=" + isWear2
                        + ", slot1MatchesHand1=" + slot1MatchesHand1 + ", slot2MatchesHand1=" + slot2MatchesHand1
                        + ", slot1MatchesHand2=" + slot1MatchesHand2 + ", slot2MatchesHand2=" + slot2MatchesHand2
                        + ", mustWear=" + mustWear + ", tied=" + tiedValue);
            } else if (queuedMustWear) {
                AppVars.AutoFishCheckUd = false;
                AppVars.AutoFishWearUd = false;
                resetAutoFishWearLoopGuard();
                AppLog.d(TAG, "AUTO_FISH_INFOAPI_PRECHECK clear stale gear flags: source=" + safeSourceModule
                        + ", address=" + address);
            }
            if (mustWear) {
                AppVars.AutoFishCheckUd = false;
                AppVars.AutoFishWearUd = true;
                AppLog.w(TAG, "AUTO_FISH_INFOAPI_PRECHECK queued recovery: AutoFishWearUd=true, source="
                        + safeSourceModule + ", address=" + address);
            } else if (AppVars.AutoFishCheckUd || AppVars.AutoFishWearUd) {
                AppVars.AutoFishCheckUd = false;
                AppVars.AutoFishWearUd = false;
                resetAutoFishWearLoopGuard();
                AppLog.d(TAG, "AUTO_FISH_INFOAPI_PRECHECK clear stale recovery flags after match: source="
                        + safeSourceModule + ", address=" + address);
            }
            AutoFishInfoApiPrecheckState finalState =
                    buildAutoFishInfoApiPrecheckState(true, mustWear, tiedValue);
            AppLog.d(TAG, "AUTO_FISH_INFOAPI_PRECHECK final: source=" + safeSourceModule
                    + ", address=" + address
                    + ", mustWear=" + finalState.mustWear
                    + ", needFatigueStep=" + finalState.needFatigueStep
                    + ", tied=" + finalState.tied
                    + ", tiedThreshold=" + finalState.tiedThreshold);
            return finalState;
        } catch (Exception e) {
            AutoFishInfoApiPrecheckState errorState =
                    buildAutoFishInfoApiPrecheckState(false, queuedMustWear, tiedValue);
            String msgError = "AUTO_FISH_INFOAPI_PRECHECK failed: nick=" + selfNick
                    + ", source=" + safeSourceModule
                    + ", address=" + address
                    + ", mustWear=" + errorState.mustWear
                    + ", needFatigueStep=" + errorState.needFatigueStep
                    + ", tied=" + errorState.tied;
            AppLog.e(TAG, msgError, e);
            return errorState;
        }
    }

    static AutoFishInfoApiPrecheckState mainPhpBuildAutoFishCachedPrecheckState(String address,
                                                                                 String sourceModule) {
        String safeSourceModule = normalizeAutoFishPrecheckSource(sourceModule);
        if (AppVars.Profile == null) {
            AutoFishInfoApiPrecheckState emptyState =
                    buildAutoFishInfoApiPrecheckState(false, false, null);
            AppLog.d(TAG, "AUTO_FISH_INFOAPI_PRECHECK cached_state: source=" + safeSourceModule
                    + ", address=" + address
                    + ", mustWear=" + emptyState.mustWear
                    + ", needFatigueStep=" + emptyState.needFatigueStep
                    + ", tied=" + emptyState.tied
                    + ", tiedThreshold=" + emptyState.tiedThreshold
                    + ", profileReady=false");
            return emptyState;
        }
        Integer tiedValue = CharacterVitalsManager.snapshot().tied;
        boolean queuedMustWear = AppVars.AutoFishCheckUd || AppVars.AutoFishWearUd;
        AutoFishInfoApiPrecheckState cachedState =
                buildAutoFishInfoApiPrecheckState(false, queuedMustWear, tiedValue);
        AppLog.d(TAG, "AUTO_FISH_INFOAPI_PRECHECK cached_state: source=" + safeSourceModule
                + ", address=" + address
                + ", mustWear=" + cachedState.mustWear
                + ", needFatigueStep=" + cachedState.needFatigueStep
                + ", tied=" + cachedState.tied
                + ", tiedThreshold=" + cachedState.tiedThreshold
                + ", profileReady=true");
        return cachedState;
    }

    private static AutoFishInfoApiPrecheckState buildAutoFishInfoApiPrecheckState(boolean snapshotValid,
                                                                                   boolean mustWear,
                                                                                   Integer tiedValue) {
        if (AppVars.Profile == null) {
            return new AutoFishInfoApiPrecheckState(snapshotValid, mustWear, false, tiedValue, 0);
        }
        int tiedThreshold = Math.max(0, Math.min(99, AppVars.Profile.FishTiedHigh));
        Integer safeTiedValue = tiedValue == null ? CharacterVitalsManager.snapshot().tied : tiedValue;
        if (safeTiedValue != null && !AppVars.AutoFishDrink
                && safeTiedValue > tiedThreshold
                && AppVars.Profile.FishTiedZero) {
            AppVars.AutoFishDrink = true;
        }
        boolean needFatigueStep = safeTiedValue != null
                && (safeTiedValue > tiedThreshold || AppVars.AutoFishDrink);
        return new AutoFishInfoApiPrecheckState(snapshotValid, mustWear, needFatigueStep,
                safeTiedValue, tiedThreshold);
    }

    private static String normalizeAutoFishPrecheckSource(String sourceModule) {
        return sourceModule == null || sourceModule.trim().isEmpty()
                ? "mainphp_autofish_precheck"
                : sourceModule.trim();
    }

    // ======================================================================
    // Вспомогательные методы InfoAPI / gear matching (перенесены из MainPhp)
    // ======================================================================

    private static boolean bindFishingHandFromInfoApi(String expectedSetting,
                                                      String[] slotNames,
                                                      Integer[] slotDurability,
                                                      boolean[] usedSlots,
                                                      boolean firstHand) {
        if (isNoFishHandSetting(expectedSetting)) {
            return true;
        }
        if (slotNames == null || slotNames.length < 2 || slotDurability == null || slotDurability.length < 2
                || usedSlots == null || usedSlots.length < 2) {
            return false;
        }
        for (int idx = 0; idx < slotNames.length; idx++) {
            if (usedSlots[idx]) continue;
            if (!matchesFishingHandSetting(slotNames[idx], slotDurability[idx], expectedSetting)) continue;
            usedSlots[idx] = true;
            if (firstHand) {
                AppVars.AutoFishHand1 = slotNames[idx] == null ? "" : slotNames[idx];
                AppVars.AutoFishHand1D = formatInfoApiDurability(slotDurability[idx]);
            } else {
                AppVars.AutoFishHand2 = slotNames[idx] == null ? "" : slotNames[idx];
                AppVars.AutoFishHand2D = formatInfoApiDurability(slotDurability[idx]);
            }
            return true;
        }
        return false;
    }

    private static boolean isNoFishHandSetting(String value) {
        if (value == null) return true;
        String normalized = value.trim();
        return normalized.isEmpty() || "\u041D\u0435\u0442".equalsIgnoreCase(normalized);
    }

    private static boolean matchesFishingHandSetting(String slotItemName, Integer slotDurability, String expectedSetting) {
        String safeItemName = slotItemName == null ? "" : slotItemName.trim();
        if (safeItemName.isEmpty()) return false;
        if (slotDurability != null && slotDurability <= 0) return false;
        if ("\u041B\u044E\u0431\u0430\u044F \u0443\u0434\u043E\u0447\u043A\u0430".equalsIgnoreCase(expectedSetting == null ? "" : expectedSetting.trim())) {
            return isFishingRodName(safeItemName);
        }
        return ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(safeItemName, expectedSetting);
    }

    private static boolean isFishingRodName(String itemName) {
        return ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(itemName, "\u0443\u0434\u043E\u0447\u043A\u0430") || ru.neverlands.anclient.postfilter.InventoryParser.containsIgnoreCase(itemName, "\u0441\u043F\u0438\u043D\u043D\u0438\u043D\u0433");
    }

    private static String formatInfoApiDurability(Integer durability) {
        return durability == null ? "" : String.valueOf(durability);
    }

    private static String describeInfoApiSlot(NeverApi.InfoApiSlot slot) {
        if (slot == null) return "<empty>";
        String itemName = slot.itemName == null ? "" : slot.itemName.trim();
        String icon = slot.icon == null ? "" : slot.icon.trim();
        String durability = slot.durability == null ? "" : String.valueOf(slot.durability);
        return "{idx=" + slot.index + ", icon=" + icon + ", item=" + itemName + ", dur=" + durability + "}";
    }

    private static String buildInfoApiRodSlotsDigest(NeverApi.InfoApiSnapshot snapshot) {
        if (snapshot == null || snapshot.slots == null || snapshot.slots.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        int appended = 0;
        for (NeverApi.InfoApiSlot slot : snapshot.slots) {
            if (slot == null) continue;
            String itemName = slot.itemName == null ? "" : slot.itemName.trim();
            if (!isFishingRodName(itemName)) continue;
            if (appended > 0) sb.append(", ");
            sb.append(slot.index).append(':').append(itemName).append('(').append(formatInfoApiDurability(slot.durability)).append(')');
            appended++;
        }
        sb.append(']');
        return sb.toString();
    }

    /** Пишет в чат уведомление о фактическом автодействии "надеть удочку". */
    private static void notifyAutoFishRodWear(String rodName) {
        String safeRodName = rodName == null ? "\u041D\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043D\u0430\u044F \u0443\u0434\u043E\u0447\u043A\u0430" : rodName.trim();
        if (safeRodName.isEmpty()) safeRodName = "\u041D\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043D\u0430\u044F \u0443\u0434\u043E\u0447\u043A\u0430";
        String message = MainPhp.buildServerChatTimeHtml()
                + "<font color=#5D7C91><b>[\u0410\u0432\u0442\u043E-\u0420\u044B\u0431\u0430\u043B\u043A\u0430]</b></font>: \u041E\u0434\u0435\u0432\u0430\u0435\u043C \u043D\u043E\u0432\u0443\u044E \u0443\u0434\u043E\u0447\u043A\u0443 '"
                + MainPhp.escapeHtmlAttr(safeRodName) + "'.";
        MainPhp.sendInventoryChatMessage(message);
        String trace = "AUTO_FISH_TRACE rod wear notify: name=" + safeRodName;
        AppLog.d(TAG, trace);
    }

    // ======================================================================
    // Anti-loop guard и управление циклом (перенесены из MainPhp)
    // ======================================================================

    static String buildAutoFishWearLoopKey() {
        String cfg1 = "", cfg2 = "";
        if (AppVars.Profile != null) {
            cfg1 = AppVars.Profile.FishHandOne == null ? "" : AppVars.Profile.FishHandOne;
            cfg2 = AppVars.Profile.FishHandTwo == null ? "" : AppVars.Profile.FishHandTwo;
        }
        String h1 = AppVars.AutoFishHand1 == null ? "" : AppVars.AutoFishHand1;
        String h2 = AppVars.AutoFishHand2 == null ? "" : AppVars.AutoFishHand2;
        return cfg1 + "|" + cfg2 + "|" + h1 + "|" + h2;
    }

    static boolean markAutoFishWearLoop(String stateKey) {
        long now = System.currentTimeMillis();
        boolean sameKey = stateKey.equals(AppVars.AutoFishWearLoopKey);
        boolean inWindow = AppVars.AutoFishWearLoopStamp > 0L
                && (now - AppVars.AutoFishWearLoopStamp) <= AUTO_FISH_WEAR_LOOP_WINDOW_MS;
        if (sameKey && inWindow) {
            AppVars.AutoFishWearLoopCount++;
        } else {
            AppVars.AutoFishWearLoopKey = stateKey;
            AppVars.AutoFishWearLoopCount = 1;
        }
        AppVars.AutoFishWearLoopStamp = now;
        return AppVars.AutoFishWearLoopCount >= AUTO_FISH_WEAR_LOOP_MAX_REPEATS;
    }

    static void resetAutoFishWearLoopGuard() {
        AppVars.AutoFishWearLoopKey = "";
        AppVars.AutoFishWearLoopCount = 0;
        AppVars.AutoFishWearLoopStamp = 0L;
    }

    static void restartAutoFishCycle(String reason) {
        resetAutoFishWearLoopGuard();
        AppVars.AutoFishCheckUd = false;
        AppVars.AutoFishWearUd = false;
        lastAutoFishInfoApiPrecheckAtMs = 0L;
        AppVars.suppressBackgroundProbesDuringFishing = false;
        String safeReason = reason == null ? "unknown" : reason;
        String msg = "AUTO_FISH_TRACE restart full cycle: reason=" + safeReason
                + ", hand1=" + AppVars.AutoFishHand1
                + ", hand2=" + AppVars.AutoFishHand2;
        AppLog.w(TAG, msg);
        FishAjaxPhp.requestAutoFishBootstrap("cycle_broken_" + safeReason);
    }

    /**
     * Полное выключение авто-рыбалки с очисткой wear loop guard (вызывается из оркестрации main.php).
     * Отличается от disableAutoFish (AJAX-версия) наличием resetAutoFishWearLoopGuard()
     * и другим механизмом чат-уведомления (через ACTION_ADD_CHAT_MESSAGE).
     */
    static void disableAutoFishMain(String reason) {
        resetAutoFishWearLoopGuard();
        try {
            if (AppVars.getContext() != null) {
                AutoFunctionsManager.getInstance(AppVars.getContext()).setAutoFishEnabled(false);
            } else if (AppVars.Profile != null) {
                AppVars.Profile.AutoFish = false;
            }
        } catch (Exception e) {
            String msg = "AUTO_FISH_TRACE disable auto fish failed";
            AppLog.w(TAG, msg, e);
            if (AppVars.Profile != null) {
                AppVars.Profile.AutoFish = false;
            }
        }
        if (AppVars.getContext() != null) {
            Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
            msgIntent.putExtra("message", MainPhp.buildServerChatTimeHtml()
                    + "<font color=#cc0000><b>\u0410\u0432\u0442\u043E-\u0440\u044B\u0431\u0430\u043B\u043A\u0430 \u0432\u044B\u043A\u043B\u044E\u0447\u0435\u043D\u0430: " + reason + "</b></font>");
            LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
        }
    }

    // ======================================================================
    // Подготовка рыбалки и капча (перенесены из MainPhp)
    // ======================================================================

    private static String extractInputValue(String html, String name) {
        if (html == null || html.isEmpty() || name == null || name.isEmpty()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("name\\s*=\\s*['\"]?" + java.util.regex.Pattern.quote(name) + "['\"]?\\s+value\\s*=\\s*['\"]?([^'\"\\s>]+)",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern
                .compile("value\\s*=\\s*['\"]?([^'\"\\s>]+)['\"]?\\s+name\\s*=\\s*['\"]?" + java.util.regex.Pattern.quote(name) + "['\"]?",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
        return m.find() ? m.group(1) : "";
    }

    static int pickFishPrimId(String html) {
        if (AppVars.Profile == null || html == null || html.isEmpty()) return -1;
        String lower = html.toLowerCase(Locale.ROOT);
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < FISH_PRIM_IDS.length; i++) {
            if ((AppVars.Profile.FishEnabledPrims & FISH_PRIM_FLAGS[i]) == 0) continue;
            int primid = FISH_PRIM_IDS[i];
            String probe = "name=primid value=" + primid;
            int probePos = lower.indexOf(probe.toLowerCase(Locale.ROOT));
            if (probePos < 0) continue;
            int countStart = probePos + probe.length();
            String afterProbe = html.substring(countStart, Math.min(countStart + 200, html.length()));
            int count = -1;
            java.util.regex.Pattern countPattern = java.util.regex.Pattern.compile("<b>(\\d+)</b>|\\b(\\d+)\\b");
            java.util.regex.Matcher countMatcher = countPattern.matcher(afterProbe);
            if (countMatcher.find()) {
                String numStr = countMatcher.group(1) != null ? countMatcher.group(1) : countMatcher.group(2);
                try { count = Integer.parseInt(numStr); } catch (NumberFormatException e) { count = -1; }
            }
            if (count > 4) {
                candidates.add(primid);
            } else if (count < 0) {
                candidates.add(primid);
            }
        }
        if (candidates.isEmpty()) return -1;
        return candidates.get(0);
    }

    /** C# parity: подготовка шага рыбалки на странице выбора приманки (MainPhpAutoFishPrepare). */
    static String mainPhpAutoFishPrepare(String html) {
        if (html == null || html.isEmpty()) return null;
        String lower = html.toLowerCase(Locale.ROOT);
        if (!lower.contains("name=primid") || !lower.contains("name=get_id")) return null;
        AppVars.CodeAddress = "";
        String codeAddress = MainPhp.extractCaptchaUrl(html);
        if (codeAddress != null && !codeAddress.isEmpty()) {
            AppVars.CodeAddress = codeAddress;
        }
        String getid = extractInputValue(html, "get_id");
        String act = extractInputValue(html, "act");
        String vcode = extractInputValue(html, "vcode");
        String lakeid = extractInputValue(html, "lakeid");
        if (getid.isEmpty() || act.isEmpty() || vcode.isEmpty() || lakeid.isEmpty()) return null;
        String massa = HelperStrings.subString(html, "<b>\u041C\u0430\u0441\u0441\u0430 \u0412\u0430\u0448\u0435\u0433\u043E \u0438\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044F: ", "</b>");
        if (massa != null && !massa.isEmpty()) {
            AppVars.AutoFishMassa = massa;
        }
        int primid = pickFishPrimId(html);
        if (primid <= 0) {
            disableAutoFishMain("\u041D\u0435\u0442 \u0434\u043E\u0441\u0442\u0443\u043F\u043D\u043E\u0439 \u043F\u0440\u0438\u043C\u0430\u043D\u043A\u0438 \u043F\u043E \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0430\u043C");
            return null;
        }
        AppVars.AutoFishLikeId = String.valueOf(primid);
        AppVars.AutoFishLikeVal = "";
        String radioBaitPattern = "(?i)type\\s*=\\s*['\"]?radio['\"]?[^>]*name\\s*=\\s*['\"]?primid['\"]?[^>]*value\\s*=\\s*['\"]?" + primid + "['\"]?";
        Pattern p = Pattern.compile(radioBaitPattern);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String matchedRadio = m.group(0);
            if (!matchedRadio.toLowerCase(Locale.ROOT).contains("checked")) {
                String replacedRadio = matchedRadio.replaceAll(">\\s*$", " checked>");
                html = html.substring(0, m.start()) + replacedRadio + html.substring(m.end());
                AppLog.d(TAG, "AUTO_FISH_TRACE mainPhpAutoFishPrepare: added checked to primid=" + primid);
            }
        }
        AppVars.FightLink = "http://neverlands.ru/main.php?get_id=" + getid
                + "&lakeid=" + lakeid
                + "&act=" + act
                + "&primid=" + primid
                + (AppVars.CodeAddress == null || AppVars.CodeAddress.isEmpty() ? "" : "&code=????")
                + "&vcode=" + vcode;
        AppLog.d(TAG, "AUTO_FISH_TRACE prepared FightLink=" + AppVars.FightLink
                + ", captcha=" + (AppVars.CodeAddress != null && !AppVars.CodeAddress.isEmpty())
                + ", primid=" + primid);
        return html;
    }

    /** HTML-заглушка ожидания ввода капчи рыбалки. */
    static String buildCaptchaDialogHoldHtml() {
        return HtmlUtils.GENERATED_PAGE_MARKER
                + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                + "<title>ANClient</title></head><body>"
                + "\u041E\u0436\u0438\u0434\u0430\u043D\u0438\u0435 \u0432\u0432\u043E\u0434\u0430 \u043A\u0430\u043F\u0447\u0438...<br>"
                + "\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043A\u043E\u0434 \u0432\u043E \u0432\u0441\u043F\u043B\u044B\u0432\u0430\u044E\u0449\u0435\u043C \u043E\u043A\u043D\u0435."
                + "</body></html>";
    }

    /** Показ диалога капчи рыбалки из main.php контекста (с нормализацией URL и dedup). */
    static void showMainPhpFishCaptchaDialogOnce(String captchaUrl, String finishUrl) {
        if (captchaUrl == null || captchaUrl.isEmpty() || finishUrl == null || finishUrl.isEmpty()) return;
        long now = System.currentTimeMillis();
        String normalizedFinishUrl = finishUrl;
        if (!normalizedFinishUrl.startsWith("http")) {
            normalizedFinishUrl = "http://neverlands.ru/" + normalizedFinishUrl.replaceFirst("^/+", "");
        }
        String normalizedCaptchaUrl = captchaUrl.replaceFirst("^https://", "http://");
        String key = normalizedFinishUrl + "|" + normalizedCaptchaUrl;
        if (key.equals(lastMainCaptchaDialogKey) && (now - lastMainCaptchaDialogAtMs) < 3000L) {
            String msg_fish_dup = "showMainPhpFishCaptchaDialogOnce: duplicate key, skip";
            AppLog.d(TAG, msg_fish_dup);
            return;
        }
        lastMainCaptchaDialogKey = key;
        lastMainCaptchaDialogAtMs = now;
        if (AppVars.getContext() == null) {
            String msg_fish_null = "showMainPhpFishCaptchaDialogOnce: context is null, skip";
            AppLog.w(TAG, msg_fish_null);
            return;
        }
        AppVars.ResumeAutoboiAfterCaptcha = false;
        AppVars.IsFightCaptchaDialogVisible = true;
        Intent intent = new Intent(AppVars.ACTION_SHOW_CAPTCHA);
        intent.putExtra("captchaUrl", captchaUrl);
        intent.putExtra("finishUrl", normalizedFinishUrl);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }

}
