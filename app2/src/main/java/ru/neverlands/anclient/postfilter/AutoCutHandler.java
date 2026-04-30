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
 *
 * Назначение:
 * - не отправляет `alchemy_ajax.php?act=3` напрямую, а только готовит main.php-контекст;
 * - использует существующие inventory/navigation helpers (`MainPhp.mainPhpFindInvWithFallback`,
 *   `InventoryParser.getWearInvList`, `ParsedDressed`) вместо отдельного HTTP-контура;
 * - возвращает готовый служебный HTML redirect или `null`, если текущий main.php должен идти дальше.
 *
 * Ключевые зависимости:
 * - `AppVars.AutoCutCheckSickle/AutoCutArmedSickle` — runtime-состояние проверки серпа;
 * - `AppVars.AutoCutCleanupPending` — флаг cleanup-прохода после роста массы;
 * - `AutoCutManager` — владелец настроек, маршрутизации по CSV-клеткам и логирования `AUTO_CUT_TRACE`;
 * - `MainPhpNavigationHandler` — источник реальной ссылки возврата `go=ret` из текущего HTML.
 */
final class AutoCutHandler {
    private static final String TAG = "AutoCutHandler";

    /**
     * Фильтр inventory-вкладки, где сервер показывает вещи/инструменты.
     * Используется для поиска серпов тем же путём, что AutoSkin ищет ножи.
     */
    private static final String AUTO_CUT_SICKLE_INV_FILTER = "&im=0&wca=4";
    private static final String AUTO_CUT_CLEANUP_INV_FILTER = "&im=0";

    private AutoCutHandler() {
    }

    /**
     * Основной decision point AutoCut внутри `MainPhp.process(...)`.
     *
     * Переменные:
     * - `address` — текущий URL main.php; нужен для распознавания inventory и синхронизации фильтра;
     * - `html` — текущий HTML после decode/removeDoctype; парсится только локально;
     * - `isFightFrame`/`isFightTopFrame` — запрет любых non-combat redirect во время боя.
     *
     * Возврат:
     * - redirect HTML на персонажа/инвентарь/надевание/карту;
     * - `null`, если AutoCut не должен перехватывать текущий ответ.
     */
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

        AutoCutManager autoCut = AppVars.getContext() != null
                ? AutoCutManager.getInstance(AppVars.getContext())
                : null;

        // Синхронизация массы выполняется здесь, а не в `AlchemyAjaxPhp`, потому только main.php/inventory
        // стабильно содержит строку "Масса Вашего инвентаря: current/max". Значение нужно сразу двум
        // зависимым веткам: chat-report Авто-Травника (`AutoFishMassa`) и cleanup-порог (`AutoCutKnownMassMax`).
        if (AppVars.getContext() != null) {
            autoCut.updateMassSnapshotFromHtml(html);
        }

        if (autoCut != null && autoCut.isMassSnapshotSyncPending()) {
            String massSyncHtml = processMassSnapshotSync(address, html, autoCut);
            if (massSyncHtml != null) {
                return massSyncHtml;
            }
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

    /**
     * Пост-инвентарная точка cleanup.
     *
     * Вызывается после штатного `mainPhpInv(...)`, чтобы не ломать существующую обработку
     * группировки, сортировки и удаления просрочки. Если cleanup-флаг активен и текущая страница
     * действительно inventory, сбрасывает cleanup-состояние в `AutoCutManager` и возвращает карту.
     */
    static String afterMainPhpInventoryStep(String address, String html) {
        if (!AppVars.AutoCutCleanupPending || html == null || html.isEmpty()) {
            return null;
        }
        if (InventoryParser.isGeneratedTransitionPage(address, html)) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG, "cleanup waits generated inventory transition");
            return null;
        }
        if (!MainPhp.mainPhpIsInv(html) && !MainPhp.hasInventoryRows(html)) {
            if (MainPhp.isInventoryAddress(address)) {
                AppLog.w(AutoCutManager.TRACE_CHAIN, TAG,
                        "cleanup waits real inventory html, address=" + address);
            }
            return null;
        }
        if (AppVars.getContext() != null) {
            AutoCutManager.getInstance(AppVars.getContext()).onCleanupCompleted("inventory_pass");
        }
        return buildReturnToMapHtml("Авто-Травник: cleanup завершен", "auto_cut_cleanup_return", html);
    }

    /**
     * Проверяет, надет ли поддерживаемый серп на странице персонажа.
     *
     * Зависимости:
     * - `ParsedDressed` разбирает `slots_inv(...)`/`slots_pla(...)`;
     * - `ParsedDressed.IsWearSickle()` обновляет `AppVars.AutoCutSickleHand*`;
     * - результат записывается в `AppVars.AutoCutArmedSickle` для guard перед `act=3`.
     */
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

    /**
     * Ищет серп в текущем HTML инвентаря и строит redirect на серверную wear-link.
     *
     * Важные переменные:
     * - `invList` — список предметов с кнопкой `Надеть`, построенный `InventoryParser`;
     * - `sickles` — whitelist названий из `ParsedDressed.getAutoCutSickleNames()`;
     * - при найденной ссылке выставляется `AutoCutCheckSickle=true`, чтобы следующий main.php-кадр
     *   снова проверил руки после серверного надевания.
     */
    static String mainPhpWearSickle(String html) {
        ParsedDressed dressed = new ParsedDressed(html);
        if (dressed.Valid && dressed.IsWearSickle()) {
            AppVars.AutoCutArmedSickle = true;
            AppVars.AutoCutCheckSickle = false;
            boolean pendingCutResumed = AlchemyAjaxPhp.resumePendingCutAfterPreparation("sickle_already_ready");
            continueRouteAfterPreparationIfIdle("sickle_already_ready", pendingCutResumed);
            return buildReturnToMapHtml("Авто-Травник: серп уже надет", "auto_cut_sickle_ready", html);
        }

        List<InventoryParser.WearInvEntry> invList = InventoryParser.getWearInvList(html);
        List<String> sickles = getConfiguredSickleNames();
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

    /**
     * Шаг проверки рук: если мы уже на персонаже/inventory snapshot, читаем экипировку;
     * иначе переводим main.php на страницу персонажа через существующий навигационный helper.
     */
    private static String processSickleCheck(String address, String html) {
        if (MainPhp.mainPhpIsPerc(html) || MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address)) {
            if (mainPhpArmedSickle(html)) {
                AppVars.AutoCutCheckSickle = false;
                boolean pendingCutResumed = AlchemyAjaxPhp.resumePendingCutAfterPreparation("sickle_checked");
                continueRouteAfterPreparationIfIdle("sickle_checked", pendingCutResumed);
                return buildReturnToMapHtml("Авто-Травник: серп проверен", "auto_cut_sickle_checked", html);
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

    /**
     * Шаг надевания серпа: переводит на вкладку вещей и делегирует поиск предмета
     * в `mainPhpWearSickle(...)`. Не создаёт новый HTTP-клиент и не обходит `MainPhp`.
     */
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

    /**
     * Открывает inventory для cleanup, когда `AutoCutManager` накопил прирост массы.
     * Сам cleanup выполняет существующий `mainPhpInv(...)`; этот метод только гарантирует,
     * что мы попадём на inventory-страницу и не перехватим ручной HTML-клик.
     */
    private static String processCleanupOpenInventory(String address, String html) {
        if (MainPhp.mainPhpIsInv(html) || MainPhp.hasInventoryRows(html)) {
            return null;
        }
        if (MainPhp.isInventoryAddress(address)) {
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG,
                    "cleanup inventory address has no inventory html, reload main frame, address=" + address);
            return MainPhp.buildRedirectHtml("Авто-Травник: повторное открытие инвентаря",
                    "main.php?get_id=56&act=10&go=inf");
        }
        String inventoryFilter = AUTO_CUT_CLEANUP_INV_FILTER;
        String invHtml = MainPhp.mainPhpFindInvWithFallback(html, inventoryFilter, address);
        if (invHtml != null && !invHtml.isEmpty()) {
            AppLog.i(AutoCutManager.TRACE_CHAIN, TAG,
                    "cleanup redirect to inventory, reason=" + AppVars.AutoCutCleanupReason
                            + ", filter=" + inventoryFilter);
            return invHtml;
        }
        return MainPhp.buildRedirectHtml("Авто-Травник: cleanup инвентаря", "main.php?im=0");
    }

    /**
     * One-shot sync массы перед срезом: если go=inf не дал `current/max`, доходим до inventory
     * через существующий main.php helper и не создаём отдельный HTTP-контур.
     */
    private static String processMassSnapshotSync(String address, String html, AutoCutManager autoCut) {
        if (autoCut.hasUsableMassSnapshot()) {
            return finishMassSnapshotSync(autoCut, "mass_available", html);
        }
        boolean inventoryPage = MainPhp.mainPhpIsInv(html)
                || MainPhp.isInventoryAddress(address)
                || MainPhp.hasInventoryRows(html);
        if (inventoryPage) {
            autoCut.clearMassSnapshotSyncPending("inventory_without_mass");
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG,
                    "mass snapshot sync reached inventory but mass was not found; continue with fallback");
            return releaseMassSnapshotGuardAndReturnToMap("inventory_without_mass", html);
        }
        String invHtml = MainPhp.mainPhpFindInvWithFallback(html, "&im=0", address);
        if (invHtml != null && !invHtml.isEmpty()) {
            AppLog.i(AutoCutManager.TRACE_CHAIN, TAG, "mass snapshot redirect to inventory via parsed link");
            return invHtml;
        }
        AppLog.i(AutoCutManager.TRACE_CHAIN, TAG, "mass snapshot redirect to inventory fallback");
        return MainPhp.buildRedirectHtml("Авто-Травник: масса инвентаря", "main.php?im=0");
    }

    private static String finishMassSnapshotSync(AutoCutManager autoCut, String source, String html) {
        autoCut.clearMassSnapshotSyncPending(source);
        return releaseMassSnapshotGuardAndReturnToMap(source, html);
    }

    private static String releaseMassSnapshotGuardAndReturnToMap(String source, String html) {
        AppVars.AutoCutCheckSickle = false;
        AppLog.i(AutoCutManager.TRACE_CHAIN, TAG,
                "mass snapshot sync finished, return to map, source=" + source
                        + ", armed=" + AppVars.AutoCutArmedSickle
                        + ", mass=" + AppVars.AutoFishMassa);
        boolean pendingCutResumed = AlchemyAjaxPhp.resumePendingCutAfterPreparation("mass_snapshot:" + source);
        continueRouteAfterPreparationIfIdle("mass_snapshot:" + source, pendingCutResumed);
        return buildReturnToMapHtml("Авто-Травник: масса проверена", "auto_cut_mass_snapshot", html);
    }

    private static void continueRouteAfterPreparationIfIdle(String source, boolean pendingCutResumed) {
        if (pendingCutResumed) {
            AppLog.d(AutoCutManager.TRACE_CHAIN, TAG,
                    "preparation completed: pending cut resume scheduled, source=" + source);
            return;
        }
        if (AppVars.getContext() == null) {
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG,
                    "preparation completed: route bootstrap skipped without context, source=" + source);
            return;
        }
        AutoCutManager.getInstance(AppVars.getContext()).continueRouteAfterPreparationIfIdle(source);
    }

    /**
     * Fail-safe при отсутствии серпа: отключает AutoCut через `AutoFunctionsManager`,
     * пишет сообщение в чат и сбрасывает runtime-флаги, чтобы не зациклить переходы inventory.
     */
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

    private static List<String> getConfiguredSickleNames() {
        if (AppVars.getContext() == null) {
            return java.util.Arrays.asList(ParsedDressed.getAutoCutSickleNames());
        }
        List<String> sickles = AutoCutManager.getInstance(AppVars.getContext()).getEnabledSickleNames();
        if (sickles == null || sickles.isEmpty()) {
            return java.util.Arrays.asList(ParsedDressed.getAutoCutSickleNames());
        }
        return sickles;
    }

    /**
     * Читает persisted/license-gated состояние AutoCut.
     * При ошибке возвращает false, чтобы pipeline был fail-closed.
     */
    private static boolean isAutoCutEnabled() {
        try {
            return AppVars.getContext() != null
                    && AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoCutEnabled();
        } catch (Exception e) {
            AppLog.w(AutoCutManager.TRACE_CHAIN, TAG, "AutoCut state read failed", e);
            return false;
        }
    }

    /**
     * Возвращает WebView на карту после проверки серпа или cleanup.
     *
     * Зависимости:
     * - сначала берёт реальную ссылку `go=ret` из текущего HTML, потому что menu-vcode
     *   для `go=inf` может вернуть страницу персонажа вместо карты;
     * - `SessionManager.getValidVCodeForAction(actionName)` остаётся только fallback-источником;
     * - параметр `an_auto_cut=1` нужен только как runtime-маркер диагностики ANClient.
     */
    private static String buildReturnToMapHtml(String title, String actionName, String html) {
        String parsedReturnHtml = MainPhpNavigationHandler.mainPhpFindMapReturnForAutoMoving(html);
        if (parsedReturnHtml != null && !parsedReturnHtml.isEmpty()) {
            AppLog.i(AutoCutManager.TRACE_CHAIN, TAG,
                    "return to map using parsed link, action=" + actionName);
            return parsedReturnHtml;
        }

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
