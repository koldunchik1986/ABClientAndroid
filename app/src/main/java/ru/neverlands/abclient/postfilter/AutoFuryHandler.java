package ru.neverlands.abclient.postfilter;

import java.util.List;

import ru.neverlands.abclient.model.ParsedDressed;
import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Handler режима "Снежок/Ярость" из main.php.
 *
 * Источник выноса: AutoFury-блок из MainPhp.process(). Handler владеет проверкой
 * AutoFury preference, переходом на персонажа, переходом в инвентарь свитков и wear-link.
 *
 * Ключевые runtime-переменные:
 * - AppVars.AutoFuryCheckScroll: нужна проверка страницы персонажа.
 * - AppVars.AutoFuryArmedScroll: результат mainPhpArmedFuryScroll(html).
 * - AppVars.AutoFuryHand: диагностическое состояние руки/слота для логов.
 * - AppVars.NeverTimer: запрещает новый non-combat redirect до истечения server timer.
 */
public class AutoFuryHandler {

    private static final String TAG = "AutoFuryHandler";

    static boolean isAutoFuryEnabledByPreference() {
        if (AppVars.Profile != null) {
            boolean enabled = AppVars.Profile.hasAnyLezFuryGroup();
            AppVars.Profile.LezDoFury = enabled;
            if (!enabled) {
                AppVars.DoFury = false;
            }
            return enabled;
        }
        return AppVars.DoFury;
    }

    static boolean mainPhpArmedFuryScroll(String html) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        return parsedDressed.IsWearFuryScroll();
    }

    static String mainPhpWearFuryScroll(String html) {
        ParsedDressed dressed = new ParsedDressed(html);
        if (!dressed.Valid) {
            return null;
        }
        boolean isWear = dressed.IsWearFuryScroll();
        if (!isWear) {
            List<InventoryParser.WearInvEntry> invList = InventoryParser.getWearInvList(html);
            String[] scrollNames = ParsedDressed.getFuryScrollNames();
            for (InventoryParser.WearInvEntry thing : invList) {
                if (thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) {
                    continue;
                }
                for (String scrollName : scrollNames) {
                    if (InventoryParser.containsIgnoreCase(thing.name, scrollName)) {
                        AppLog.d(TAG, "AUTO_FURY_TRACE mainPhpWearFuryScroll: wear " + thing.name
                                + ", link=" + thing.wearLink);
                        return MainPhp.buildRedirectHtml("Одеваем " + thing.name, thing.wearLink);
                    }
                }
            }
        }
        AppVars.AutoFuryArmedScroll = false;
        return null;
    }

    /**
     * Главная точка AutoFury из MainPhp.process().
     *
     * Входные переменные:
     * - address/html: текущий main.php URL и HTML.
     * - isFightFrame/isFightTopFrame: защита от запуска non-combat свитка в бою.
     *
     * Порядок старого MainPhp-блока сохранён:
     * 1. Проверить pause guard и isAutoFuryEnabledByPreference().
     * 2. Дождаться AppVars.NeverTimer.
     * 3. Если AppVars.AutoFuryCheckScroll=true, перейти на персонажа через MainPhp.mainPhpFindPerc(html).
     * 4. На странице персонажа обновить AppVars.AutoFuryArmedScroll и AppVars.AutoFuryCheckScroll.
     * 5. Если свиток не надет, перейти в инвентарь `&im=0&wca=28`, найти wear-link или переключить вкладку.
     *
     * Возврат: redirect-HTML или null, если AutoFury ничего не делает на текущем кадре.
     */
    static String processMainPhpAutoFuryStep(String address,
                                             String html,
                                             boolean isFightFrame,
                                             boolean isFightTopFrame) {
        if (MainPhp.isNonCombatAutoPausedByFastAction()
                || isFightFrame
                || isFightTopFrame
                || !isAutoFuryEnabledByPreference()) {
            return null;
        }
        long nowMs = System.currentTimeMillis();
        if (AppVars.NeverTimer > 0L && nowMs <= AppVars.NeverTimer) {
            return null;
        }
        if (AppVars.AutoFuryCheckScroll) {
            String perchtml = MainPhp.mainPhpFindPerc(html);
            if (perchtml != null && !perchtml.isEmpty()) {
                String msgFuryChar = "AUTO_FURY_TRACE redirect to character page for scroll check";
                AppLog.d(TAG, msgFuryChar);
                return perchtml;
            }
            AppVars.AutoFuryArmedScroll = false;
            if (MainPhp.mainPhpIsPerc(html)) {
                AppVars.AutoFuryArmedScroll = mainPhpArmedFuryScroll(html);
                AppVars.AutoFuryCheckScroll = false;
                AppLog.d(TAG, "AUTO_FURY_TRACE scroll check result: armed=" + AppVars.AutoFuryArmedScroll
                        + ", hand=" + AppVars.AutoFuryHand);
            }
        }
        if (!AppVars.AutoFuryArmedScroll) {
            String invHtml = MainPhp.mainPhpFindInvWithFallback(html, "&im=0&wca=28", address);
            if (invHtml != null && !invHtml.isEmpty()) {
                String msgFuryInv = "AUTO_FURY_TRACE redirect to scroll inventory (&im=0&wca=28)";
                AppLog.d(TAG, msgFuryInv);
                return invHtml;
            }
            if (MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address)) {
                invHtml = mainPhpWearFuryScroll(html);
                if (invHtml == null || invHtml.isEmpty()) {
                    if (!MainPhp.inventoryAddressMatchesFilter(address, "&im=0&wca=28")) {
                        String msgFuryTab = "AUTO_FURY_TRACE switch to scroll category (wca=28)";
                        AppLog.d(TAG, msgFuryTab);
                        return MainPhp.buildRedirectHtml("Переходим к свиткам", "main.php?im=0&wca=28");
                    }
                } else {
                    AppVars.AutoFuryCheckScroll = true;
                    return invHtml;
                }
            }
        }
        return null;
    }
}
