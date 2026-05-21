package ru.neverlands.anclient.postfilter;

import java.util.List;
import java.util.Locale;

import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.manager.AutoMineManager;
import ru.neverlands.anclient.model.ParsedDressed;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.SessionManager;

/** MainPhp decision point for Auto-Mine inventory preparation and mine-page auto-dig injection. */
final class AutoMineHandler {
    private static final String TAG = "AutoMineHandler";
    private static final String PICKAXE_INV_FILTER = "&im=0&wca=3";
    private static final String TORCH_INV_FILTER = "&im=0&wca=4";
    private static final int INVENTORY_RETRY_DELAY_MS = 800;

    private AutoMineHandler() {
    }

    static String processMainPhpAutoMineStep(String address,
                                             String html,
                                             boolean isFightFrame,
                                             boolean isFightTopFrame) {
        if (html == null || html.isEmpty()
                || isFightFrame
                || isFightTopFrame
                || MainPhp.isNonCombatAutoPausedByFastAction()) {
            return null;
        }
        if (InventoryParser.isGeneratedTransitionPage(address, html)) {
            AppLog.d(AutoMineManager.TRACE_CHAIN, TAG, "skip generated transition, address=" + address);
            return null;
        }

        if (AppVars.getContext() == null) {
            AppLog.w(AutoMineManager.TRACE_CHAIN, TAG, "skip mine page snapshot: context is null, address=" + address);
            return null;
        }

        AutoMineManager autoMine = AutoMineManager.getInstance(AppVars.getContext());
        if (autoMine.isAutoMinePage(html)) {
            autoMine.updateMinePageSnapshot(html, address);
        }

        if (autoMine.isAutoMinePage(html) && autoMine.hasPendingMineRoute()) {
            String moveInjection = autoMine.buildPendingMoveInjection("main_php_mine_page_route");
            if (!moveInjection.isEmpty()) {
                AppLog.i(AutoMineManager.TRACE_CHAIN, TAG, "inject pending mine route script, address=" + address);
                return injectBeforeBodyEnd(html, moveInjection);
            }
        }

        boolean autoMineEnabled = isAutoMineEnabled();
        if (AppVars.AutoMineCheckTorch && (!autoMineEnabled || autoMine.hasPendingMineRoute())) {
            String torchHtml = processTorchCheck(address, html, autoMine);
            if (torchHtml != null && !torchHtml.isEmpty()) {
                return torchHtml;
            }
        }

        if (!autoMineEnabled) {
            return null;
        }

        if (AppVars.AutoMineCheckPickaxe) {
            String checkHtml = processPickaxeCheck(address, html, autoMine);
            if (checkHtml != null && !checkHtml.isEmpty()) {
                return checkHtml;
            }
        }
        if (!AppVars.AutoMineArmedPickaxe) {
            String wearHtml = processPickaxeWear(address, html, autoMine);
            if (wearHtml != null && !wearHtml.isEmpty()) {
                return wearHtml;
            }
        }
        if (AppVars.AutoMineCheckTorch) {
            String torchHtml = processTorchCheck(address, html, autoMine);
            if (torchHtml != null && !torchHtml.isEmpty()) {
                return torchHtml;
            }
        }

        if (autoMine.isAutoMinePage(html) && AppVars.AutoMineArmedPickaxe) {
            String moveInjection = autoMine.buildPendingMoveInjection("main_php_mine_page");
            if (!moveInjection.isEmpty()) {
                AppLog.i(AutoMineManager.TRACE_CHAIN, TAG, "inject pending mine move script, address=" + address);
                return injectBeforeBodyEnd(html, moveInjection);
            }
            String digCode = autoMine.extractDigCode(html);
            if (!digCode.isEmpty()) {
                String injection = autoMine.buildAutoDigInjection(digCode, "main_php_mine_page");
                if (!injection.isEmpty() && !html.contains("__anAutoMineDigClicked")) {
                    AppLog.i(AutoMineManager.TRACE_CHAIN, TAG, "inject auto dig script, address=" + address);
                    return injectBeforeBodyEnd(html, injection);
                }
            } else {
                AppLog.w(AutoMineManager.TRACE_CHAIN, TAG,
                        "mine page has no Начать добычу button, address=" + address);
            }
        }
        return null;
    }

    static boolean mainPhpArmedPickaxe(String html, AutoMineManager autoMine) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        boolean armed = parsedDressed.IsWearAutoMinePickaxe(
                autoMine.getEnabledPickaxeNames().toArray(new String[0]));
        AppVars.AutoMineArmedPickaxe = armed;
        if (armed) {
            AppLog.i(AutoMineManager.TRACE_CHAIN, TAG,
                    "pickaxe armed: item=" + AppVars.AutoMinePickaxeHand + " " + AppVars.AutoMinePickaxeHandD);
        }
        return armed;
    }

    private static String processPickaxeCheck(String address, String html, AutoMineManager autoMine) {
        if (MainPhp.mainPhpIsPerc(html) || MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address)) {
            if (mainPhpArmedPickaxe(html, autoMine)) {
                AppVars.AutoMineCheckPickaxe = false;
                AppLog.i(AutoMineManager.TRACE_CHAIN, TAG, "pickaxe check approved, address=" + address);
                return buildReturnToMineHtml("Авто-Шахтёр: кирка проверена", "auto_mine_pickaxe_checked", html);
            }
            AppLog.d(AutoMineManager.TRACE_CHAIN, TAG, "pickaxe not armed on current page, continue wear flow");
            return null;
        }
        String personHtml = MainPhp.mainPhpFindPerc(html);
        if (personHtml != null && !personHtml.isEmpty()) {
            AppLog.d(AutoMineManager.TRACE_CHAIN, TAG, "redirect to character page for pickaxe check");
            return personHtml;
        }
        return MainPhp.buildRedirectHtml("Авто-Шахтёр: персонаж", "main.php?get_id=56&act=10&go=inf&an_auto_mine_pickaxe=1");
    }

    private static String processPickaxeWear(String address, String html, AutoMineManager autoMine) {
        String invHtml = MainPhp.mainPhpFindInvWithFallback(html, PICKAXE_INV_FILTER, address);
        if (invHtml != null && !invHtml.isEmpty()) {
            AppLog.d(AutoMineManager.TRACE_CHAIN, TAG, "redirect to inventory for pickaxe wear");
            return invHtml;
        }
        boolean inventoryHtml = MainPhp.mainPhpIsInv(html) || MainPhp.hasInventoryRows(html);
        if (inventoryHtml) {
            String wearHtml = mainPhpWearPickaxe(html, autoMine);
            if ((wearHtml == null || wearHtml.isEmpty())
                    && !MainPhp.inventoryAddressMatchesFilter(address, PICKAXE_INV_FILTER)) {
                return MainPhp.buildRedirectHtml("Авто-Шахтёр: кирки", "main.php?im=0&wca=3&an_auto_mine_pickaxe=1");
            }
            return wearHtml;
        }
        if (MainPhp.isInventoryAddress(address)) {
            AppLog.w(AutoMineManager.TRACE_CHAIN, TAG,
                    "pickaxe inventory address has no inventory html, retry, address=" + address);
            return FightAuto.buildDelayedRedirectHtml("Авто-Шахтёр: ожидание инвентаря",
                    address,
                    INVENTORY_RETRY_DELAY_MS);
        }
        return null;
    }

    private static String mainPhpWearPickaxe(String html, AutoMineManager autoMine) {
        if (mainPhpArmedPickaxe(html, autoMine)) {
            AppVars.AutoMineCheckPickaxe = false;
            AppVars.AutoMineArmedPickaxe = true;
            return buildReturnToMineHtml("Авто-Шахтёр: кирка уже надета", "auto_mine_pickaxe_ready", html);
        }
        List<InventoryParser.WearInvEntry> invList = InventoryParser.getWearInvList(html);
        List<String> pickaxes = autoMine.getEnabledPickaxeNames();
        for (InventoryParser.WearInvEntry thing : invList) {
            if (thing == null || thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) {
                continue;
            }
            for (String pickaxe : pickaxes) {
                if (InventoryParser.containsIgnoreCase(thing.name, pickaxe)) {
                    AppVars.AutoMineCheckPickaxe = true;
                    AppLog.i(AutoMineManager.TRACE_CHAIN, TAG,
                            "wear pickaxe: item=" + thing.name + ", link=" + thing.wearLink);
                    return MainPhp.buildRedirectHtml("Одеваем " + thing.name, thing.wearLink);
                }
            }
        }
        autoMine.stopBecauseNoPickaxe();
        return null;
    }

    private static String processTorchCheck(String address, String html, AutoMineManager autoMine) {
        if (mainPhpArmedTorch(html, autoMine)) {
            AppVars.AutoMineTorchReady = true;
            AppVars.AutoMineCheckTorch = false;
            AppLog.i(AutoMineManager.TRACE_CHAIN, TAG, "torch/fonar already armed, address=" + address);
            return buildReturnToMineHtml("Авто-Шахтёр: факел готов", "auto_mine_torch_ready", html);
        }

        boolean inventoryHtml = MainPhp.mainPhpIsInv(html) || MainPhp.hasInventoryRows(html);
        if (!inventoryHtml) {
            String invSearchAddress = autoMine.isAutoMinePage(html) ? "" : address;
            String invHtml = MainPhp.mainPhpFindInvWithFallback(html, TORCH_INV_FILTER, invSearchAddress);
            if (invHtml != null && !invHtml.isEmpty()) {
                AppLog.d(AutoMineManager.TRACE_CHAIN, TAG, "redirect to inventory for torch check");
                return invHtml;
            }
            if (MainPhp.isInventoryAddress(address) && !autoMine.isAutoMinePage(html)) {
                AppLog.w(AutoMineManager.TRACE_CHAIN, TAG,
                        "torch inventory address has no inventory html, retry, address=" + address);
                return FightAuto.buildDelayedRedirectHtml("Авто-Шахтёр: ожидание инвентаря",
                        address,
                        INVENTORY_RETRY_DELAY_MS);
            }
            String sessionInvHtml = buildTorchInventoryRedirectFromSession("torch_check");
            if (!sessionInvHtml.isEmpty()) {
                return sessionInvHtml;
            }
            return null;
        }
        InventoryParser.WearInvEntry torchEntry = findWearEntryForConfiguredTorch(html, autoMine);
        if (torchEntry != null && torchEntry.wearLink != null && !torchEntry.wearLink.isEmpty()) {
            AppVars.AutoMineCheckTorch = true;
            AppVars.AutoMineTorchReady = false;
            AppLog.i(AutoMineManager.TRACE_CHAIN, TAG,
                    "wear torch/fonar: item=" + torchEntry.name + ", link=" + torchEntry.wearLink);
            return MainPhp.buildRedirectHtml("Одеваем " + torchEntry.name, torchEntry.wearLink);
        }
        if (!MainPhp.inventoryAddressMatchesFilter(address, TORCH_INV_FILTER)) {
            String invHtml = MainPhp.mainPhpFindInvWithFallback(html, TORCH_INV_FILTER, address);
            if (invHtml != null && !invHtml.isEmpty()) {
                return invHtml;
            }
            String sessionInvHtml = buildTorchInventoryRedirectFromSession("torch_filter");
            if (!sessionInvHtml.isEmpty()) {
                return sessionInvHtml;
            }
        }
        AppLog.w(AutoMineManager.TRACE_CHAIN, TAG,
                "torch/fonar not found in filtered inventory; stop mine route, address="
                        + address + ", filter=" + TORCH_INV_FILTER);
        autoMine.stopBecauseNoTorch();
        return null;
    }

    private static boolean mainPhpArmedTorch(String html, AutoMineManager autoMine) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        return parsedDressed.IsWearAutoMineTorch(autoMine.getEnabledTorchNames().toArray(new String[0]));
    }

    private static InventoryParser.WearInvEntry findWearEntryForConfiguredTorch(String html, AutoMineManager autoMine) {
        List<InventoryParser.WearInvEntry> invList = InventoryParser.getWearInvList(html);
        List<String> torches = autoMine.getEnabledTorchNames();
        for (InventoryParser.WearInvEntry thing : invList) {
            if (thing == null || thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) {
                continue;
            }
            for (String torch : torches) {
                if (InventoryParser.containsIgnoreCase(thing.name, torch)) {
                    return thing;
                }
            }
        }
        return null;
    }

    private static String buildTorchInventoryRedirectFromSession(String source) {
        String vcode = SessionManager.getInstance().getValidVCodeForAction("auto_mine_torch_inventory");
        if (vcode == null || vcode.trim().isEmpty()) {
            AppLog.w(AutoMineManager.TRACE_CHAIN, TAG,
                    "NO_SESSION/EMPTY_VCODE: cannot open torch inventory, source=" + source);
            return "";
        }
        String link = "main.php?get_id=56&act=10&go=inv&vcode=" + vcode.trim()
                + TORCH_INV_FILTER + "&an_auto_mine_torch=1";
        AppLog.i(AutoMineManager.TRACE_CHAIN, TAG,
                "redirect to torch inventory via vcode, source=" + source);
        return MainPhp.buildRedirectHtml("Авто-Шахтёр: факел", link);
    }

    private static String buildReturnToMineHtml(String title, String actionName, String html) {
        String parsedReturnHtml = MainPhpNavigationHandler.mainPhpFindMapReturnForAutoMoving(html);
        if (parsedReturnHtml != null && !parsedReturnHtml.isEmpty()) {
            AppLog.i(AutoMineManager.TRACE_CHAIN, TAG,
                    "return to mine/map using parsed link, action=" + actionName);
            return parsedReturnHtml;
        }
        String link = "main.php?get_id=56&act=10&go=ret";
        String vcode = SessionManager.getInstance().getValidVCodeForAction(actionName);
        if (vcode != null && !vcode.trim().isEmpty()) {
            link += "&vcode=" + vcode.trim();
        } else {
            AppLog.w(AutoMineManager.TRACE_CHAIN, TAG,
                    "NO_SESSION/EMPTY_VCODE: skip protected return, action=" + actionName);
            return MainPhp.buildRedirectHtml(title,
                    "main.php?an_auto_mine_reload=1&r=" + System.currentTimeMillis());
        }
        link += "&an_auto_mine=1&r=" + System.currentTimeMillis();
        return MainPhp.buildRedirectHtml(title, link);
    }

    private static String injectBeforeBodyEnd(String html, String injection) {
        String lower = html.toLowerCase(Locale.ROOT);
        int bodyEnd = lower.lastIndexOf("</body>");
        if (bodyEnd >= 0) {
            return html.substring(0, bodyEnd) + injection + html.substring(bodyEnd);
        }
        return html + injection;
    }

    private static boolean isAutoMineEnabled() {
        try {
            return AppVars.getContext() != null
                    && AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoMineEnabled();
        } catch (Exception e) {
            AppLog.w(AutoMineManager.TRACE_CHAIN, TAG, "AutoMine state read failed", e);
            return false;
        }
    }
}
