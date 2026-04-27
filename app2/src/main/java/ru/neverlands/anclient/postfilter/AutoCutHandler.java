package ru.neverlands.anclient.postfilter;

import java.util.List;

import ru.neverlands.anclient.manager.AutoCutManager;
import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.model.ParsedDressed;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.SessionManager;

/**
 * MainPhp-пайплайн Авто-Травника для подготовки серпа и inventory cleanup.
 */
final class AutoCutHandler {
    private static final String TAG = "AutoCutHandler";
    private static final String AUTO_CUT_SICKLE_INV_FILTER = "&im=0&wca=4";

    private AutoCutHandler() {
    }

    static String processMainPhpAutoCutStep(String address,
                                            String html,
                                            boolean isFightFrame,
                                            boolean isFightTopFrame) {
        if (html == null || html.isEmpty()
                || isFightFrame
                || isFightTopFrame
                || MainPhp.isNonCombatAutoPausedByFastAction()
                || !isAutoCutEnabled()) {
            return null;
        }

        boolean generatedTransition = InventoryParser.isGeneratedTransitionPage(address, html);
        if (generatedTransition) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG, "skip generated transition, address=" + address);
            return null;
        }

        if (AppVars.AutoCutCleanupPending) {
            String cleanupHtml = processCleanupOpenInventory(address, html);
            if (cleanupHtml != null) {
                return cleanupHtml;
            }
        }

        if (!AppVars.AutoCutCheckSickle && AppVars.AutoCutArmedSickle) {
            return null;
        }

        if (AppVars.AutoCutCheckSickle) {
            String checkHtml = processSickleCheck(address, html);
            if (checkHtml != null) {
                return checkHtml;
            }
        }

        if (!AppVars.AutoCutArmedSickle) {
            String wearHtml = processSickleWear(address, html);
            if (wearHtml != null) {
                return wearHtml;
            }
        }
        return null;
    }

    static String afterMainPhpInventoryStep(String address, String html) {
        if (!AppVars.AutoCutCleanupPending || html == null || html.isEmpty()) {
            return null;
        }
        if (InventoryParser.isGeneratedTransitionPage(address, html)) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG, "cleanup waits generated inventory transition");
            return null;
        }
        if (!MainPhp.mainPhpIsInv(html) && !MainPhp.isInventoryAddress(address) && !MainPhp.hasInventoryRows(html)) {
            return null;
        }
        if (AppVars.getContext() != null) {
            AutoCutManager.getInstance(AppVars.getContext()).onCleanupCompleted("inventory_pass");
        }
        return buildReturnToMapHtml("Авто-Травник: cleanup завершен", "auto_cut_cleanup_return");
    }

    static boolean mainPhpArmedSickle(String html) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        boolean armed = parsedDressed.IsWearSickle();
        AppVars.AutoCutArmedSickle = armed;
        if (armed) {
            AppLog.i(AutoCutManager.TRACE_CHAIN, TAG,
                    "sickle armed: " + AppVars.AutoCutSickleHand + " " + AppVars.AutoCutSickleHandD);
        }
        return armed;
    }

    static String mainPhpWearSickle(String html) {
        ParsedDressed dressed = new ParsedDressed(html);
        if (dressed.Valid && dressed.IsWearSickle()) {
            AppVars.AutoCutArmedSickle = true;
            AppVars.AutoCutCheckSickle = false;
            return buildReturnToMapHtml("Авто-Травник: серп уже надет", "auto_cut_sickle_ready");
        }

        List<InventoryParser.WearInvEntry> invList = InventoryParser.getWearInvList(html);
        String[] sickles = ParsedDressed.getAutoCutSickleNames();
        for (InventoryParser.WearInvEntry thing : invList) {
            if (thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) {
                continue;
            }
            for (String sickle : sickles) {
                if (InventoryParser.containsIgnoreCase(thing.name, sickle)) {
                    AppVars.AutoCutCheckSickle = true;
                    AppLog.i(AutoCutManager.TRACE_CHAIN, TAG,
                            "wear sickle: " + thing.name + ", link=" + thing.wearLink);
                    return MainPhp.buildRedirectHtml("Одеваем " + thing.name, thing.wearLink);
                }
            }
        }
        stopAutoCutNoSickle();
        return null;
    }

    private static String processSickleCheck(String address, String html) {
        if (MainPhp.mainPhpIsPerc(html) || MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address)) {
            if (mainPhpArmedSickle(html)) {
                AppVars.AutoCutCheckSickle = false;
                return buildReturnToMapHtml("Авто-Травник: серп проверен", "auto_cut_sickle_checked");
            }
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                    "sickle not armed on current page, continue wear flow, address=" + address);
            return null;
        }

        String personHtml = MainPhp.mainPhpFindPerc(html);
        if (personHtml != null && !personHtml.isEmpty()) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG, "redirect to character page for sickle check");
            return personHtml;
        }
        return MainPhp.buildRedirectHtml("Авто-Травник: персонаж", "main.php?get_id=56&act=10&go=inf");
    }

    private static String processSickleWear(String address, String html) {
        String invHtml = MainPhp.mainPhpFindInvWithFallback(html, AUTO_CUT_SICKLE_INV_FILTER, address);
        if (invHtml != null && !invHtml.isEmpty()) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG, "redirect to inventory for sickle wear");
            return invHtml;
        }
        if (MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address)) {
            String wearHtml = mainPhpWearSickle(html);
            if (wearHtml == null || wearHtml.isEmpty()) {
                if (!MainPhp.inventoryAddressMatchesFilter(address, AUTO_CUT_SICKLE_INV_FILTER)) {
                    return MainPhp.buildRedirectHtml("Авто-Травник: вещи", "main.php?im=0&wca=4");
                }
            }
            return wearHtml;
        }
        return null;
    }

    private static String processCleanupOpenInventory(String address, String html) {
        if (MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address) || MainPhp.hasInventoryRows(html)) {
            return null;
        }
        String invHtml = MainPhp.mainPhpFindInvWithFallback(html, "&im=0", address);
        if (invHtml != null && !invHtml.isEmpty()) {
            AppLog.i(AutoCutManager.TRACE_CHAIN, TAG,
                    "cleanup redirect to inventory, reason=" + AppVars.AutoCutCleanupReason);
            return invHtml;
        }
        return MainPhp.buildRedirectHtml("Авто-Травник: cleanup инвентаря", "main.php?im=0");
    }

    private static void stopAutoCutNoSickle() {
        AppVars.AutoCutCheckSickle = false;
        AppVars.AutoCutArmedSickle = false;
        AppLog.w(AutoCutManager.TRACE_CHAIN, TAG, "sickle not found, stop AutoCut");
        MainPhp.sendInventoryChatMessage(MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#990000><b>[auto_cut]</b> Серп для Авто-Травника не найден, автосрез остановлен.</font>");
        if (AppVars.getContext() != null) {
            AutoFunctionsManager.getInstance(AppVars.getContext()).setAutoCutEnabled(false);
        }
    }

    private static boolean isAutoCutEnabled() {
        try {
            return AppVars.getContext() != null
                    && AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoCutEnabled();
        } catch (Exception e) {
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG, "AutoCut state read failed", e);
            return false;
        }
    }

    private static String buildReturnToMapHtml(String title, String actionName) {
        String link = "main.php?get_id=56&act=10&go=ret";
        String vcode = SessionManager.getInstance().getValidVCodeForAction(actionName);
        if (vcode != null && !vcode.trim().isEmpty()) {
            link += "&vcode=" + vcode.trim();
        } else {
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG, "return to map without vcode, action=" + actionName);
        }
        link += "&an_auto_cut=1&r=" + System.currentTimeMillis();
        return MainPhp.buildRedirectHtml(title, link);
    }
}
