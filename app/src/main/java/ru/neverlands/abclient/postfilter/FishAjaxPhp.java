package ru.neverlands.abclient.postfilter;

import android.content.Intent;
import android.util.Log;
import android.webkit.WebView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.model.Prims;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.Russian;

// Пост-фильтр fish_ajax.php: считает отчет улова и синхронизирует runtime-флаги авто-рыбалки.
public final class FishAjaxPhp {
    private static final String TAG = "FishAjaxPhp";
    private static final String FISH_AJAX_ACT1 = "act=1";
    private static final String FISH_CAPTCHA_URL_PREFIX = "http://neverlands.ru/modules/code/code.php?";
    private static final long FISH_CAPTCHA_DIALOG_DEDUP_MS = 1500L;
    private static final long FISH_AUTORELOAD_DEDUP_MS = 2500L;
    private static final long FISH_AUTORELOAD_SAFETY_MS = 350L;
    private static volatile String lastFishCaptchaDialogKey = "";
    private static volatile long lastFishCaptchaDialogAtMs = 0L;
    private static volatile long lastFishAutoreloadAtMs = 0L;
    private static volatile long lastFishAutoreloadDueAtMs = 0L;

    private static final Map<String, Double> FISH_NV = new LinkedHashMap<>();
    private static final Map<String, Double> FISH_MASS = new LinkedHashMap<>();
    private static final Map<String, BaitInfo> BAIT_INFO = new LinkedHashMap<>();
    private static final Pattern FISH_COOLDOWN_PATTERN = Pattern.compile("@\\[0,\\[2,(\\d+)\\]\\]@");

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
        if (containsFishHardStop(lower)) {
            disableAutoFish("Нет снастей/приманки или не хватает умения для текущей локации");
            return array;
        }

        String lowerAddress = address == null ? "" : address.toLowerCase(Locale.ROOT);
        if (lowerAddress.contains(FISH_AJAX_ACT1)) {
            processFishAct1(address, html);
            return array;
        }

        if (lowerAddress.contains("act=2")) {
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

        FishAct1State state = parseFishAct1State(html);
        if (state == null) {
            Log.d(TAG, "AUTO_FISH_TRACE act1 parse failed, address=" + address);
            return;
        }

        if (!state.massCurrent.isEmpty() && !state.massMax.isEmpty()) {
            AppVars.AutoFishMassa = state.massCurrent + "/" + state.massMax;
        }

        FishBaitSelection selection = selectAllowedBait(state.baits);
        if (selection == null) {
            disableAutoFish("Нет доступной приманки по настройкам");
            return;
        }

        AppVars.AutoFishLikeId = selection.id;
        AppVars.AutoFishLikeVal = String.valueOf(selection.count);
        AppVars.NamePri = selection.name;
        AppVars.ValPri = selection.count;

        if (state.vcode == null || state.vcode.isEmpty()) {
            Log.w(TAG, "AUTO_FISH_TRACE act1 skip: empty vcode");
            return;
        }

        boolean captchaRequired = state.captchaToken != null
                && !state.captchaToken.isEmpty()
                && !"00000".equals(state.captchaToken);
        if (!captchaRequired) {
            Log.d(TAG, "AUTO_FISH_TRACE act1: captcha not required, primid=" + selection.id
                    + ", vcode=" + state.vcode);
            return;
        }

        String submitUrl = "http://neverlands.ru/gameplay/ajax/fish_ajax.php?act=2"
                + "&primid=" + selection.id
                + "&vcode=" + state.vcode
                + "&code=????"
                + "&r=" + System.currentTimeMillis();
        String captchaUrl = FISH_CAPTCHA_URL_PREFIX + state.captchaToken;
        showFishCaptchaDialogOnce(captchaUrl, submitUrl);

        Log.d(TAG, "AUTO_FISH_TRACE act1: captcha required, primid=" + selection.id
                + ", vcode=" + state.vcode + ", captcha=" + captchaUrl);
    }

    /**
     * Единая проверка состояния AutoFish (runtime-менеджер + fallback на профиль).
     */
    private static boolean isAutoFishEnabled() {
        try {
            if (AppVars.getContext() != null) {
                return AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoFishEnabled();
            }
        } catch (Exception e) {
            Log.w(TAG, "AUTO_FISH_TRACE: failed to read AutoFish from manager", e);
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
            Log.d(TAG, "AUTO_FISH_TRACE act1: captcha dedup skip");
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

        long nowMs = System.currentTimeMillis();
        long dueAtMs = nowMs + (cooldownSec * 1000L);
        AppVars.NeverTimer = dueAtMs;

        if (!isAutoFishEnabled()) {
            return;
        }

        if (Math.abs(dueAtMs - lastFishAutoreloadDueAtMs) <= 1000L
                && (nowMs - lastFishAutoreloadAtMs) < FISH_AUTORELOAD_DEDUP_MS) {
            return;
        }
        lastFishAutoreloadAtMs = nowMs;
        lastFishAutoreloadDueAtMs = dueAtMs;

        MainActivity activity = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
        if (activity == null) {
            return;
        }
        WebView webView = activity.getMainWebView();
        if (webView == null) {
            return;
        }

        long delayMs = Math.max(250L, cooldownSec * 1000L + FISH_AUTORELOAD_SAFETY_MS);
        Log.d(TAG, "AUTO_FISH_TRACE act2 cooldown=" + cooldownSec + "s, schedule main.php reload in " + delayMs + "ms");

        activity.runOnUiThread(() -> webView.postDelayed(() -> {
            if (!isAutoFishEnabled()) {
                Log.d(TAG, "AUTO_FISH_TRACE act2 scheduled reload skipped: auto-fish disabled");
                return;
            }
            MainActivity active = (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
            if (active == null || active.getMainWebView() == null) {
                return;
            }
            String reloadUrl = "http://neverlands.ru/main.php?af_cycle=1&r=" + System.currentTimeMillis();
            Log.d(TAG, "AUTO_FISH_TRACE act2 scheduled reload fire: " + reloadUrl);
            active.getMainWebView().loadUrl(reloadUrl);
        }, delayMs));
    }

    /**
     * Извлекает серверный fish-cooldown из payload (`@[0,[2,294]]@` -> `294` секунд).
     */
    private static int extractFishCooldownSec(String html) {
        if (html == null || html.isEmpty()) {
            return 0;
        }
        Matcher matcher = FISH_COOLDOWN_PATTERN.matcher(html);
        if (!matcher.find()) {
            return 0;
        }
        return parseIntSafe(matcher.group(1));
    }

    /**
     * Парсит технический payload `act=1` для получения captcha/vcode/массы/списка приманок.
     */
    private static FishAct1State parseFishAct1State(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        String[] sections = html.split("@");
        if (sections.length < 6) {
            return null;
        }

        String payload = sections[5] == null ? "" : sections[5].trim();
        if (!payload.startsWith("[1,")) {
            return null;
        }

        Matcher header = Pattern.compile(
                "^\\[1,\\s*\"([^\"]*)\",\\s*\"([^\"]*)\",\\s*([^,\\]]+),\\s*([^,\\]]+)",
                Pattern.DOTALL).matcher(payload);
        if (!header.find()) {
            return null;
        }

        FishAct1State state = new FishAct1State();
        state.captchaToken = header.group(1) == null ? "" : header.group(1).trim();
        state.vcode = header.group(2) == null ? "" : header.group(2).trim();
        state.massCurrent = cleanNumeric(header.group(3));
        state.massMax = cleanNumeric(header.group(4));

        Matcher baitMatcher = Pattern.compile("\\[(38|39|40|41|42|43|44|45|46),\\s*\"([^\"]+)\",\\s*(\\d+)\\]")
                .matcher(payload);
        while (baitMatcher.find()) {
            String id = baitMatcher.group(1);
            String name = baitMatcher.group(2);
            int count = parseIntSafe(baitMatcher.group(3));
            state.baits.add(new FishBaitSelection(id, name, count));
        }
        return state;
    }

    /**
     * Выбирает первую доступную приманку с остатком > 4 (C# parity `CheckPri`).
     */
    private static FishBaitSelection selectAllowedBait(List<FishBaitSelection> baits) {
        if (baits == null || baits.isEmpty() || AppVars.Profile == null) {
            return null;
        }
        for (FishBaitSelection bait : baits) {
            if (bait == null || bait.count <= 4) {
                continue;
            }
            if (isBaitEnabledInProfile(bait.id)) {
                return bait;
            }
        }
        return null;
    }

    /**
     * Проверка битмаски разрешённых приманок (`Prims`) по server-id.
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
    private static String cleanNumeric(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[^0-9.,\\-]", "");
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
        int fishCatch = parseIntSafe(HelperStrings.subString(html, "Клёв: ", " шт."));
        int fishLoot = parseIntSafe(HelperStrings.subString(html, "Улов: ", " шт."));
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
        int baitRemainingBefore = parseIntSafe(AppVars.AutoFishLikeVal);
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

        AppVars.AutoFishCheckUd = true;
        AppVars.AutoFishWearUd = false;

        String report = buildReportHtml(
                fishName,
                fishLoot,
                fishCatch,
                fishUmUp,
                totalBalance,
                bait == null ? "" : bait.name,
                baitRemainingAfter);
        maybeWriteFishChat(fishName, fishLoot, fishCatch, fishUmUp);
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
            Log.w(TAG, "AUTO_FISH_TRACE mass update failed", e);
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
                                          int baitRemaining) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(fishName).append("</b> [<b>")
                .append(fishLoot).append('/').append(fishCatch).append("</b>]. ");
        if (AppVars.Profile != null && AppVars.Profile.FishUm > 0) {
            sb.append("Умелка: <b>").append(AppVars.Profile.FishUm).append("</b>");
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
        return sb.toString();
    }

    /**
     * Пишет итог рыболовного шага в игровой чат, если это включено в профиле.
     *
     * Зависимости:
     * - `UserConfig.FishChatReport/FishChatReportColor`,
     * - транспорт чата через `pushChatMessage(...)`.
     */
    private static void maybeWriteFishChat(String fishName, int fishLoot, int fishCatch, boolean fishUmUp) {
        if (AppVars.Profile == null) {
            return;
        }
        if (!AppVars.Profile.FishChatReport && !AppVars.Profile.FishChatReportColor) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (AppVars.Profile.FishChatReportColor) {
            sb.append("Умелка <b>").append(AppVars.Profile.FishUm).append("</b>. ");
        } else {
            sb.append("Умелка ").append(AppVars.Profile.FishUm).append(". ");
        }
        if (AppVars.NamePri != null && !AppVars.NamePri.isEmpty()) {
            sb.append(AppVars.NamePri).append(" » ");
        }
        sb.append(fishName).append(" [").append(fishLoot).append('/').append(fishCatch).append("]. ");
        if (AppVars.AutoFishNV < 0) {
            sb.append("Потери");
        } else {
            sb.append("Доход");
        }
        if (AppVars.Profile.FishChatReportColor) {
            sb.append(": <b>").append(formatDouble(AppVars.AutoFishNV)).append(" NV</b>.");
        } else {
            sb.append(" за сеанс: ").append(formatDouble(AppVars.AutoFishNV)).append(" NV.");
        }
        if (fishUmUp) {
            sb.append(" Умение \"Рыбалка\" ").append(AppVars.Profile.FishChatReportColor ? "<b>повысилось на 1</b>!" : "повысилось на 1!");
        }
        pushChatMessage(sb.toString());
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
            Log.w(TAG, "AUTO_FISH_TRACE disable failed", e);
            if (AppVars.Profile != null) {
                AppVars.Profile.AutoFish = false;
            }
        }
        pushChatMessage("<font color=#cc0000><b>Авто-рыбалка выключена: " + reason + "</b></font>");
        if (AppVars.getContext() != null) {
            LocalBroadcastManager.getInstance(AppVars.getContext())
                    .sendBroadcast(new Intent(AppVars.ACTION_STOP_AUTOFISH));
        }
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
        return lower.contains("у вас нет рыболовных снастей")
                || lower.contains("у вас нет приманки, чтобы ловить рыбу")
                || lower.contains("приманок нет в наличии")
                || lower.contains("у вас не хватает умения, чтобы ловить тут рыбу");
    }

    /**
     * Безопасный парсинг целых из строк с примесями (`"12 шт."`, `" 294 "`).
     */
    private static int parseIntSafe(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim().replaceAll("[^0-9\\-]", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * Возвращает значение из таблицы коэффициентов с fallback.
     */
    private static double getOrDefault(Map<String, Double> map, String key, double fallback) {
        Double value = map.get(key);
        return value == null ? fallback : value;
    }

    /**
     * Нормализует double для UI/чата (обрезает лишние нули).
     */
    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    /**
     * Регистрация коэффициентов вида рыбы в таблицах NV и массы.
     */
    private static void putFish(String name, double nv, double mass) {
        FISH_NV.put(name, nv);
        FISH_MASS.put(name, mass);
    }

    /**
     * Регистрация приманки по server-id (`primid`) и ее коэффициентов.
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
}
