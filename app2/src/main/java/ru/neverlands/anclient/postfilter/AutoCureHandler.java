package ru.neverlands.anclient.postfilter;

import java.util.Locale;

import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.manager.CharacterVitalsManager;
import ru.neverlands.anclient.manager.FastActionManager;
import ru.neverlands.anclient.manager.RoomManager;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.HelperStrings;
import ru.neverlands.anclient.utils.HtmlUtils;

final class AutoCureHandler {

    private static final String TAG = "AutoCureHandler";

    static final String AUTO_CURE_POISON_POTION_NAME = "Зелье Лечения Отравлений";
    static final String AUTO_CURE_SELF_ELIXIR_NAME = "Эликсир Мгновенного Исцеления";
    static final String MAP_HEAVY_INJURY_POPUP_MARKER = "Вы не можете перемещаться! У Вас тяж";
    static final int POISON_INDEX = 0;
    static final int LIGHT_WOUND_INDEX = 1;
    static final int MEDIUM_WOUND_INDEX = 2;
    static final int HEAVY_WOUND_INDEX = 3;

    static volatile long lastMapHeavyInjurySyncAtMs = 0L;

    private AutoCureHandler() {
    }

    static String mainPhpAutoCureStep(String address, String html) {
        if (html == null || html.isEmpty()
                || AppVars.Profile == null
                || !isAutoCureEnabledByPreference()) {
            return null;
        }
        String nick = AppVars.Profile.UserNick == null ? "" : AppVars.Profile.UserNick.trim();
        if (nick.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (AppVars.NeverTimer > 0L && now < AppVars.NeverTimer) {
            AppLog.d(TAG, "AUTO_CURE_TRACE skipped by NeverTimer: dueInMs="
                    + Math.max(0L, AppVars.NeverTimer - now) + ", address=" + address);
            return null;
        }

        CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.snapshot();
        int poison = snapshot.poisonCount;
        int light = snapshot.lightWoundCount;
        int medium = snapshot.mediumWoundCount;
        int heavy = snapshot.heavyWoundCount;
        if (poison <= 0 && light <= 0 && medium <= 0 && heavy <= 0) {
            return null;
        }

        if (poison > 0) {
            String invHtml = MainPhp.mainPhpFindInvWithFallback(html, "&im=0&wca=27", address);
            if (invHtml != null && !invHtml.isEmpty()) {
                return invHtml;
            }
            if (!(MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address))) {
                return null;
            }
            if (!MainPhp.mainPhpIsInv(html)) {
                return null;
            }
            if (!MainPhp.inventoryAddressMatchesFilter(address, "&im=0&wca=27")) {
                return MainPhp.buildRedirectHtml("Переключение на зелья", "main.php?im=0&wca=27");
            }

            String poisonCureHtml = mainPhpBuildPoisonCureForm(html, nick);
            if (poisonCureHtml == null || poisonCureHtml.isEmpty()) {
                disableAutoCureAndNotify(
                        "У вас отравление и нет зелья лечения отравлений! Автолечение отключено. Не забудьте включить его обратно.",
                        true,
                        false
                );
                return null;
            }

            CharacterVitalsManager.decrementPoisonOrWound(POISON_INDEX, "AutoCureHandler.mainPhpAutoCureStep.poisonUsed");
            AppVars.CureNickDone = nick;
            MainPhp.sendInventoryChatMessage(FightAuto.buildServerChatTimeHtml()
                    + "<font color=#004bbb>Лечим свое отравление...</font>");
            return poisonCureHtml;
        }

        String cureTravm;
        int woundIndex;
        String woundLabel;
        if (light > 0 && isAutoCureWoundTypeEnabledForSelfByAnyMethod("1")) {
            cureTravm = "1";
            woundIndex = LIGHT_WOUND_INDEX;
            woundLabel = "легкую";
        } else if (medium > 0 && isAutoCureWoundTypeEnabledForSelfByAnyMethod("2")) {
            cureTravm = "2";
            woundIndex = MEDIUM_WOUND_INDEX;
            woundLabel = "среднюю";
        } else if (heavy > 0 && isAutoCureWoundTypeEnabledForSelfByAnyMethod("3")) {
            cureTravm = "3";
            woundIndex = HEAVY_WOUND_INDEX;
            woundLabel = "тяжелую";
        } else {
            if (light > 0 || medium > 0 || heavy > 0) {
                AppLog.d(TAG, "AUTO_CURE_TRACE self wounds present but disabled by settings: "
                        + "light=" + light + "(enabled=" + isAutoCureWoundTypeEnabledForSelfByAnyMethod("1") + "), "
                        + "medium=" + medium + "(enabled=" + isAutoCureWoundTypeEnabledForSelfByAnyMethod("2") + "), "
                        + "heavy=" + heavy + "(enabled=" + isAutoCureWoundTypeEnabledForSelfByAnyMethod("3") + ")");
            }
            return null;
        }

        if (isAutoCureSelfElixirEnabledForWound(cureTravm)) {
            String selfElixirCureHtml = mainPhpTrySelfWoundCureByElixir(address, html, woundLabel);
            if (selfElixirCureHtml != null && !selfElixirCureHtml.isEmpty()) {
                if (!isSelfWoundElixirNavigationOnlyResult(selfElixirCureHtml)) {
                    CharacterVitalsManager.decrementPoisonOrWound(woundIndex,
                            "AutoCureHandler.mainPhpAutoCureStep.selfElixirUsed");
                    AppVars.CureNickDone = nick;
                    RoomManager.onAutoCureSubmitted(nick, cureTravm);
                    AppLog.d(TAG, "AUTO_CURE_TRACE self elixir submitted (self): travm="
                            + cureTravm + ", index=" + woundIndex);
                } else {
                    AppLog.d(TAG, "AUTO_CURE_TRACE self elixir navigation step (self): travm="
                            + cureTravm);
                }
                return selfElixirCureHtml;
            }
        }

        if (!isAutoCureWoundTypeEnabledForTravm(cureTravm)) {
            return null;
        }

        String invHtml = MainPhp.mainPhpFindInvWithFallback(html, "&im=0&wca=85", address);
        if (invHtml != null && !invHtml.isEmpty()) {
            return invHtml;
        }
        if (!(MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address))) {
            return null;
        }
        if (!MainPhp.mainPhpIsInv(html)) {
            return null;
        }
        if (!MainPhp.inventoryAddressMatchesFilter(address, "&im=0&wca=85")) {
            return MainPhp.buildRedirectHtml("Переключение на аптечки", "main.php?im=0&wca=85");
        }

        String woundCureHtml = mainPhpBuildWoundCureForm(html, cureTravm, nick);
        if (woundCureHtml == null || woundCureHtml.isEmpty()) {
            disableAutoCureAndNotify(
                    "У вас травма, но нет возможности ее вылечить! Автолечение отключено. Не забудьте включить его обратно.",
                    false,
                    true
            );
            return null;
        }

        CharacterVitalsManager.decrementPoisonOrWound(woundIndex, "AutoCureHandler.mainPhpAutoCureStep.woundUsed");
        AppVars.CureNickDone = nick;
        RoomManager.onAutoCureSubmitted(nick, cureTravm);
        MainPhp.sendInventoryChatMessage(FightAuto.buildServerChatTimeHtml()
                + "<font color=#004bbb>Лечим свою " + woundLabel + " травму...</font>");
        return woundCureHtml;
    }

    static String mainPhpExternalRequestedCureStep(String address, String html) {
        if (!AppVars.CureNeed) {
            return null;
        }
        if (!isAutoCureEnabledByPreference()) {
            clearExternalCureRequest("auto-cure-disabled");
            return null;
        }

        String targetNick = AppVars.CureNick == null ? "" : AppVars.CureNick.trim();
        String cureTravm = AppVars.CureTravm == null ? "" : AppVars.CureTravm.trim();
        if (targetNick.isEmpty() || cureTravm.isEmpty()) {
            clearExternalCureRequest("empty-target-or-type");
            return null;
        }
        if (!("1".equals(cureTravm) || "2".equals(cureTravm) || "3".equals(cureTravm) || "4".equals(cureTravm))) {
            clearExternalCureRequest("invalid-wound-type");
            return null;
        }
        boolean selfTarget = isSelfNick(targetNick);
        if (selfTarget) {
            if (!isAutoCureWoundTypeEnabledForSelfByAnyMethod(cureTravm)) {
                clearExternalCureRequest("wound-type-disabled");
                return null;
            }
        } else if (!isAutoCureWoundTypeEnabledForTravm(cureTravm)) {
            clearExternalCureRequest("wound-type-disabled");
            return null;
        }

        final String woundLabel;
        switch (cureTravm) {
            case "1":
                woundLabel = "легкая";
                break;
            case "2":
                woundLabel = "средняя";
                break;
            case "3":
                woundLabel = "тяжелая";
                break;
            default:
                woundLabel = "боевая";
                break;
        }

        if (selfTarget && isAutoCureSelfElixirEnabledForWound(cureTravm)) {
            String selfElixirCureHtml = mainPhpTrySelfWoundCureByElixir(address, html, woundLabel);
            if (selfElixirCureHtml != null && !selfElixirCureHtml.isEmpty()) {
                if (!isSelfWoundElixirNavigationOnlyResult(selfElixirCureHtml)) {
                    decrementSelfWoundCounterIfNeeded(targetNick, cureTravm,
                            "AutoCureHandler.mainPhpExternalRequestedCureStep.selfElixirUsed");
                    AppVars.CureNickDone = targetNick;
                    RoomManager.onAutoCureSubmitted(targetNick, cureTravm);
                    clearExternalCureRequest("submitted-self-elixir");
                    AppLog.d(TAG, "AUTO_CURE_TRACE self elixir submitted: nick="
                            + targetNick + ", travm=" + cureTravm);
                } else {
                    AppLog.d(TAG, "AUTO_CURE_TRACE self elixir navigation step: nick="
                            + targetNick + ", travm=" + cureTravm);
                }
                return selfElixirCureHtml;
            }
        }

        if (selfTarget && !isAutoCureWoundTypeEnabledForTravm(cureTravm)) {
            clearExternalCureRequest("self-elixir-only-no-medkit-fallback");
            return null;
        }

        String invHtml = MainPhp.mainPhpFindInvWithFallback(html, "&im=0&wca=85", address);
        if (invHtml != null && !invHtml.isEmpty()) {
            return invHtml;
        }
        if (!(MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address))) {
            return null;
        }
        if (!MainPhp.mainPhpIsInv(html)) {
            return null;
        }
        if (!MainPhp.inventoryAddressMatchesFilter(address, "&im=0&wca=85")) {
            return MainPhp.buildRedirectHtml("Переключение на аптечки", "main.php?im=0&wca=85");
        }

        String cureHtml = mainPhpBuildWoundCureForm(html, cureTravm, targetNick);
        if (cureHtml == null || cureHtml.isEmpty()) {
            MainPhp.sendInventoryChatMessage(FightAuto.buildServerChatTimeHtml()
                    + "<font color=#FF0000>Подходящая аптечка не найдена! Действие отменено.</font>");
            clearExternalCureRequest("doctorform-not-found");
            return null;
        }

        String safeNick = targetNick.replace("<", "&lt;").replace(">", "&gt;");
        MainPhp.sendInventoryChatMessage(FightAuto.buildServerChatTimeHtml()
                + "<font color=#004bbb>Лечим " + safeNick + " (" + woundLabel + " травма)...</font>");

        decrementSelfWoundCounterIfNeeded(targetNick, cureTravm,
                "AutoCureHandler.mainPhpExternalRequestedCureStep.submitted");
        AppVars.CureNickDone = targetNick;
        RoomManager.onAutoCureSubmitted(targetNick, cureTravm);

        clearExternalCureRequest("submitted");
        return cureHtml;
    }

    static String mainPhpBuildPoisonCureForm(String html, String selfNick) {
        if (html == null || html.isEmpty() || selfNick == null || selfNick.isEmpty()) {
            return null;
        }
        String namePotion = "'" + AUTO_CURE_POISON_POTION_NAME + "'";
        String htmlLower = html.toLowerCase(Locale.ROOT);
        int p0 = htmlLower.indexOf(namePotion.toLowerCase(Locale.ROOT));
        if (p0 == -1) {
            return null;
        }
        int ps = html.lastIndexOf('<', p0);
        if (ps == -1) {
            return null;
        }
        ps++;
        int pe = html.indexOf('>', p0);
        if (pe == -1) {
            return null;
        }
        String chunk = html.substring(ps, pe);
        if (!MainPhp.containsIgnoreCase(chunk, "magicreform(")) {
            return null;
        }
        String args = HelperStrings.subString(chunk, "magicreform('", "')");
        if (args == null || args.isEmpty()) {
            return null;
        }
        String[] arg = args.split("'");
        if (arg.length < 7) {
            return null;
        }
        String wuid = arg[0];
        String wmcode = arg[6];
        return HtmlUtils.GENERATED_PAGE_MARKER
                + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                + "<title>ANClient</title></head><body>"
                + "Используем " + AUTO_CURE_POISON_POTION_NAME + " на себя..."
                + "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>"
                + "<input name=magicrestart type=hidden value=\"1\">"
                + "<input name=magicreuid type=hidden value=\"" + wuid + "\">"
                + "<input name=vcode type=hidden value=\"" + wmcode + "\">"
                + "<input name=post_id type=hidden value=\"46\">"
                + "<input name=fornickname type=hidden value=\"" + selfNick + "\">"
                + "</form><script language=\"JavaScript\">document.ff.submit();</script></body></html>";
    }

    static String mainPhpTrySelfWoundCureByElixir(String address, String html, String woundLabel) {
        String invHtml = MainPhp.mainPhpFindInvWithFallback(html, "&im=6", address);
        if (invHtml != null && !invHtml.isEmpty()) {
            return invHtml;
        }
        if (!(MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address))) {
            return null;
        }
        if (!MainPhp.mainPhpIsInv(html)) {
            return null;
        }
        if (!MainPhp.inventoryAddressMatchesFilter(address, "&im=6")) {
            return MainPhp.buildRedirectHtml("Переключение на эликсиры", "main.php?im=6");
        }

        String cureHtml = mainPhpBuildSelfWoundCureElixirRedirect(html);
        if (cureHtml == null || cureHtml.isEmpty()) {
            return null;
        }

        MainPhp.sendInventoryChatMessage(FightAuto.buildServerChatTimeHtml()
                + "<font color=#004bbb>Лечим свою " + woundLabel + " травму "
                + AUTO_CURE_SELF_ELIXIR_NAME + "..."
                + FastActionManager.buildElixirRemainingSuffixForMessage(
                        AUTO_CURE_SELF_ELIXIR_NAME,
                        html,
                        -1
                )
                + "</font>");
        return cureHtml;
    }

    static String mainPhpBuildSelfWoundCureElixirRedirect(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String prompt = "Использовать " + AUTO_CURE_SELF_ELIXIR_NAME + " сейчас?";
        int promptPos = html.toLowerCase(Locale.ROOT).indexOf(prompt.toLowerCase(Locale.ROOT));
        if (promptPos == -1) {
            return null;
        }

        int linkStart = html.indexOf("='", promptPos);
        if (linkStart == -1) {
            return null;
        }
        linkStart += 2;
        int linkEnd = html.indexOf("'", linkStart);
        if (linkEnd == -1) {
            return null;
        }
        String link = html.substring(linkStart, linkEnd).trim();
        if (link.isEmpty()) {
            return null;
        }

        return HtmlUtils.GENERATED_PAGE_MARKER
                + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                + "<title>ANClient</title></head><body>"
                + "Используем " + AUTO_CURE_SELF_ELIXIR_NAME + "..."
                + "<script language=\"JavaScript\">window.location = \"" + link + "\";</script>"
                + "</body></html>";
    }

    static String mainPhpBuildWoundCureForm(String html, String cureTravm, String targetNick) {
        if (html == null || html.isEmpty()
                || cureTravm == null || cureTravm.isEmpty()
                || targetNick == null || targetNick.isEmpty()) {
            return null;
        }
        String dtypeNeed;
        switch (cureTravm) {
            case "1":
                dtypeNeed = "0";
                break;
            case "2":
                dtypeNeed = "1";
                break;
            case "3":
                dtypeNeed = "2";
                break;
            case "4":
                dtypeNeed = "4";
                break;
            default:
                return null;
        }

        String htmlLower = html.toLowerCase(Locale.ROOT);
        String patternDoctorForm = "doctorform(";
        int p1 = 0;
        while (p1 != -1) {
            p1 = htmlLower.indexOf(patternDoctorForm, p1);
            if (p1 == -1) {
                break;
            }
            int argsStart = p1 + patternDoctorForm.length();
            int p2 = html.indexOf(")", argsStart);
            if (p2 == -1) {
                break;
            }
            String args = html.substring(argsStart, p2);
            p1 = p2 + 1;
            if (args.isEmpty()) {
                continue;
            }
            String[] arg = args.split(",");
            if (arg.length < 5) {
                continue;
            }

            String duid = FightAuto.trimJsToken(arg[0]);
            String vcode = FightAuto.trimJsToken(arg[1]);
            String dprice = FightAuto.trimJsToken(arg[2]);
            String dtype = FightAuto.trimJsToken(arg[3]);
            String dcurs = FightAuto.trimJsToken(arg[4]);
            if (!dtypeNeed.equalsIgnoreCase(dtype)) {
                continue;
            }

            return HtmlUtils.GENERATED_PAGE_MARKER
                    + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                    + "<title>ANClient</title></head><body>"
                    + "Используем аптечку на " + targetNick + "..."
                    + "<form action=\"http://neverlands.ru/main.php\" method=POST name=ff>"
                    + "<input name=dtype type=hidden value=\"" + dtype + "\">"
                    + "<input name=addid type=hidden value=\"2\">"
                    + "<input name=post_id type=hidden value=\"3\">"
                    + "<input name=dprice type=hidden value=\"" + dprice + "\">"
                    + "<input name=dcurs type=hidden value=\"" + dcurs + "\">"
                    + "<input name=duid type=hidden value=\"" + duid + "\">"
                    + "<input name=vcode type=hidden value=\"" + vcode + "\">"
                    + "<input name=fnick type=hidden value=\"" + targetNick + "\">"
                    + "</form><script language=\"JavaScript\">document.ff.submit();</script></body></html>";
        }
        return null;
    }

    static boolean isSelfWoundElixirNavigationOnlyResult(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (!lower.contains(HtmlUtils.GENERATED_PAGE_MARKER.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return lower.contains("переключение на инвентарь")
                || lower.contains("переключение на эликсиры");
    }

    static void clearExternalCureRequest(String reason) {
        AppVars.CureNeed = false;
        AppVars.CureNick = "";
        AppVars.CureTravm = "";
        AppVars.CurePauseNonCombatAutoFunctions = false;
        String msg = "AUTO_CURE_TRACE clear external request: reason=" + reason;
        AppLog.d(TAG, msg);
    }

    static boolean isAutoCureEnabledByPreference() {
        try {
            android.content.Context context = AppVars.getContext();
            if (context == null) {
                return false;
            }
            return AutoFunctionsManager.getInstance(context).isAutoCureEnabled();
        } catch (Exception e) {
            String msg = "isAutoCureEnabledByPreference: fallback=false";
            AppLog.w(TAG, msg, e);
            return false;
        }
    }

    static boolean isAutoCureSelfElixirEnabledForWound(String cureTravm) {
        int woundType = parseCureTravmType(cureTravm);
        if (woundType <= 0) {
            return false;
        }
        AutoFunctionsManager manager = getAutoFunctionsManagerSafe();
        return manager != null && manager.isAutoCureSelfElixirEnabledForWound(woundType);
    }

    static boolean isAutoCureWoundTypeEnabledForTravm(String cureTravm) {
        int woundType = parseCureTravmType(cureTravm);
        if (woundType <= 0) {
            return false;
        }
        AutoFunctionsManager manager = getAutoFunctionsManagerSafe();
        return manager == null || manager.isAutoCureWoundTypeEnabled(woundType);
    }

    static boolean isAutoCureWoundTypeEnabledForSelfByAnyMethod(String cureTravm) {
        if (isAutoCureWoundTypeEnabledForTravm(cureTravm)) {
            return true;
        }
        return isAutoCureSelfElixirEnabledForWound(cureTravm);
    }

    static int parseCureTravmType(String cureTravm) {
        if (cureTravm == null || cureTravm.trim().isEmpty()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(cureTravm.trim());
            return (value >= 1 && value <= 4) ? value : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    static void disableAutoCureAndNotify(String message, boolean clearPoison, boolean clearWounds) {
        CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.snapshot();
        int[] current = new int[] {
                snapshot.poisonCount,
                snapshot.lightWoundCount,
                snapshot.mediumWoundCount,
                snapshot.heavyWoundCount
        };
        if (clearPoison) {
            current[POISON_INDEX] = 0;
        }
        if (clearWounds) {
            current[LIGHT_WOUND_INDEX] = 0;
            current[MEDIUM_WOUND_INDEX] = 0;
            current[HEAVY_WOUND_INDEX] = 0;
        }
        CharacterVitalsManager.updatePoisonAndWounds(current, "AutoCureHandler.disableAutoCureAndNotify");
        try {
            android.content.Context context = AppVars.getContext();
            if (context != null) {
                AutoFunctionsManager.getInstance(context).setAutoCureEnabled(false);
            }
        } catch (Exception e) {
            String msg = "AUTO_CURE_TRACE disable failed";
            AppLog.w(TAG, msg, e);
        }
        MainPhp.sendInventoryChatMessage(FightAuto.buildServerChatTimeHtml() + "<font color=#FF0000>" + message + "</font>");
    }

    static void decrementSelfWoundCounterIfNeeded(String targetNick, String cureTravm, String source) {
        if (!isSelfNick(targetNick)) {
            return;
        }
        int woundIndex = woundIndexFromTravm(cureTravm);
        if (woundIndex < 0) {
            return;
        }
        CharacterVitalsManager.decrementPoisonOrWound(woundIndex, source);
    }

    static boolean isSelfNick(String nick) {
        if (nick == null || nick.trim().isEmpty() || AppVars.Profile == null
                || AppVars.Profile.UserNick == null || AppVars.Profile.UserNick.trim().isEmpty()) {
            return false;
        }
        return nick.trim().equalsIgnoreCase(AppVars.Profile.UserNick.trim());
    }

    static int woundIndexFromTravm(String cureTravm) {
        if (cureTravm == null || cureTravm.trim().isEmpty()) {
            return -1;
        }
        switch (cureTravm.trim()) {
            case "1":
                return LIGHT_WOUND_INDEX;
            case "2":
                return MEDIUM_WOUND_INDEX;
            case "3":
                return HEAVY_WOUND_INDEX;
            default:
                return -1;
        }
    }

    static void handleHeavyInjurySignal(String text, String sourceTag) {
        if (!isHeavyInjurySignalText(text)) {
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastMapHeavyInjurySyncAtMs) < 1200L) {
            return;
        }
        lastMapHeavyInjurySyncAtMs = now;
        CharacterVitalsManager.Snapshot snapshot = CharacterVitalsManager.ensureHeavyWoundPresent(
                "AutoCureHandler.handleHeavyInjurySignal." + sourceTag);
        queueSelfHeavyInjuryCureIfNeeded(sourceTag);
        AppLog.d(TAG, "AUTO_CURE_TRACE heavy injury signal(" + sourceTag + "): pw=["
                + snapshot.poisonCount + "," + snapshot.lightWoundCount + ","
                + snapshot.mediumWoundCount + "," + snapshot.heavyWoundCount + "]");
        FileLogger.trace(TAG, "AUTO_CURE_TRACE heavy injury signal(" + sourceTag + "): pw=["
                + snapshot.poisonCount + "," + snapshot.lightWoundCount + ","
                + snapshot.mediumWoundCount + "," + snapshot.heavyWoundCount + "]");
    }

    static boolean isHeavyInjurySignalText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        boolean hasMoveBlock = lower.contains("не можете перемещ")
                || lower.contains("не можите перемещ");
        boolean hasHeavyWound = lower.contains("тяж")
                && lower.contains("травм");
        if (hasMoveBlock && hasHeavyWound) {
            return true;
        }
        String marker = MAP_HEAVY_INJURY_POPUP_MARKER.toLowerCase(Locale.ROOT).replace('ё', 'е');
        return lower.contains(marker) && hasHeavyWound;
    }

    static void queueSelfHeavyInjuryCureIfNeeded(String sourceTag) {
        if (!isAutoCureEnabledByPreference()) {
            String msg = "AUTO_CURE_TRACE heavy injury signal ignored: auto-cure disabled, source=";
            AppLog.d(TAG, msg);
            return;
        }
        if (AppVars.Profile == null || AppVars.Profile.UserNick == null) {
            String msg = "AUTO_CURE_TRACE heavy injury signal ignored: empty self nick, source=";
            AppLog.d(TAG, msg);
            return;
        }
        String selfNick = AppVars.Profile.UserNick.trim();
        if (selfNick.isEmpty()) {
            String msg = "AUTO_CURE_TRACE heavy injury signal ignored: blank self nick, source=";
            AppLog.d(TAG, msg);
            return;
        }

        AppVars.CurePauseNonCombatAutoFunctions = true;
        AppVars.CureNeed = true;
        AppVars.CureNick = selfNick;
        AppVars.CureTravm = "3";
        AppVars.CureNickDone = "";
        AppVars.CureNickBoi = "";
        AppLog.d(TAG, "AUTO_CURE_TRACE heavy injury queued self cure: nick="
                + selfNick + ", travm=3, source=" + sourceTag);
    }

    static void syncInjuriesFromMapHeavyPopup(String html) {
        handleHeavyInjurySignal(html, "map_html");
    }

    public static void onServerPopupMessage(String popupText) {
        handleHeavyInjurySignal(popupText, "bridge_popup");
    }

    private static AutoFunctionsManager getAutoFunctionsManagerSafe() {
        try {
            android.content.Context context = AppVars.getContext();
            if (context == null) {
                return null;
            }
            return AutoFunctionsManager.getInstance(context);
        } catch (Exception e) {
            String msg = "getAutoFunctionsManagerSafe failed";
            AppLog.w(TAG, msg, e);
            return null;
        }
    }
}
