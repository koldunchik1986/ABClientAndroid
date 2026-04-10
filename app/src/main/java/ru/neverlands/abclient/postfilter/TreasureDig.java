package ru.neverlands.abclient.postfilter;

import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.model.ParsedDressed;
import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ExtMap;
import ru.neverlands.abclient.utils.FileLogger;
import ru.neverlands.abclient.utils.SessionManager;

/**
 * Модуль Auto-Клада (поиск/экипировка лопаты/копка), выделенный из {@link MainPhp}.
 *
 * Назначение:
 * - централизовать всю логику, связанную с кнопкой "Копать" и подготовкой лопаты;
 * - снизить связность и размер MainPhp для упрощения отладки;
 * - сохранить parity с уже существующим поведением Android-версии.
 *
 * Важно:
 * - класс не хранит собственное состояние: runtime-флаги остаются в {@link AppVars};
 * - инфраструктурные операции (редиректы, детект инвентаря, чат-публикации) делегируются через {@link Host},
 *   чтобы не дублировать код MainPhp.
 */
public final class TreasureDig {
    private static final String TAG = "TreasureDig";
    private static final String DIG_BUTTON_MARKER = "[\"dig\",\"Копать\",";
    private static final int AUTO_TREASURE_SHOVEL_PREP_MAX_RETRIES = 8;
    private static final String AUTO_TREASURE_SHOVEL_PREP_RETRY_PARAM = "ab_tdig_inv_retry";
    
    /**
     * Счетчик глубины вложения для безопасного управления флагом {@link AppVars#TreasureDigPauseNonCombatAutoFunctions}.
     * Гарантирует, что флаг не будет сброшен раньше времени при вложенных вызовах applyTreasurePauseAndStopNavigator().
     * 
     * Семантика:
     * - depth > 0 => флаг должен быть true
     * - depth == 0 => флаг должен быть false
     * - depth никогда не становится отрицательным (защита от ошибок)
     */
    private static volatile int treasurePauseDepth = 0;

    /**
     * Упрощенная запись предмета инвентаря для wear-операции.
     * Передается из MainPhp через bridge, чтобы не раскрывать внутренние DTO MainPhp наружу.
     */
    public static final class WearInvEntry {
        public final String name;
        public final String wearLink;

        public WearInvEntry(String name, String wearLink) {
            this.name = name;
            this.wearLink = wearLink;
        }
    }

    /**
     * Bridge в инфраструктуру MainPhp.
     * Нужен для переиспользования существующих утилит без дублирования.
     */
    public interface Host {
        String mainPhpFindInvWithFallback(String html, String filter, String address);
        String mainPhpFindMapReturnForAutoMoving(String html);
        boolean mainPhpIsInv(String html);
        boolean isInventoryAddress(String address);
        boolean inventoryAddressMatchesFilter(String address, String filter);
        boolean hasInventoryRows(String html);
        int parseUrlParamInt(String url, String paramName, int fallback);
        String normalizeNeverlandsMainLink(String link);
        String appendOrReplaceUrlParam(String url, String paramName, String paramValue);
        String buildRedirectHtml(String description, String link);
        void sendInventoryChatMessage(String messageHtml);
        String buildServerChatTimeHtml();
        String escapeHtmlAttr(String value);
        List<WearInvEntry> getWearInvList(String html);
    }

    private TreasureDig() {
    }

    /**
     * Точка входа Auto-Клада: обрабатывает появление кнопки "Копать" и сценарий stop-on-dig.
     *
     * Поведение:
     * 1) При включенном auto-dig запускает конвейер "экипировка лопаты -> возврат на карту -> клик Копать".
     * 2) При включенном профильном DoStopOnDig останавливает Auto-Клад сразу после обнаружения кнопки "Копать".
     *
     * Возвращает:
     * - HTML-редирект/инъекцию для следующего шага конвейера;
     * - null, если вмешательство не требуется.
     */
    public static String maybeStopAutoTreasureOnDig(String html, String address, Host host) {
        if (html == null || html.isEmpty() || host == null) {
            return null;
        }

        boolean digMarkerDetected = html.contains(DIG_BUTTON_MARKER);
        boolean autoTreasureActive = AppVars.DoSearchBox
                || AppVars.AutoMoving
                || (AppVars.Profile != null && AppVars.Profile.AutoDig);

        AutoFunctionsManager autoManager = null;
        try {
            android.content.Context context = AppVars.getContext();
            if (context != null) {
                autoManager = AutoFunctionsManager.getInstance(context);
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: manager init failed", e);
        }

        boolean autoDigEnabled = autoManager != null && autoManager.isAutoTreasureDigEnabled();
        if (autoTreasureActive && autoDigEnabled) {
            String digFlowHtml = maybeHandleAutoTreasureDigFlow(html, address, autoManager, digMarkerDetected, host);
            if (digFlowHtml != null) {
                return digFlowHtml;
            }
        } else if (!autoTreasureActive || !autoDigEnabled) {
            AppVars.AutoTreasureDigPendingInventory = false;
            AppVars.AutoTreasureShovelReady = false;
            AppVars.AutoTreasureShovelReadyOption = "";
            releaseTreasurePause("dig_flow_inactive");
        }

        if (AppVars.Profile == null || !AppVars.Profile.DoStopOnDig || !digMarkerDetected || !autoTreasureActive) {
            return null;
        }

        // Полная остановка текущего маршрута (аналог C# UpdateNavigatorOff).
        AppVars.AutoMoving = false;
        AppVars.AutoMovingDestinaton = null;
        AppVars.AutoMovingMapPath = null;
        AppVars.AutoMovingNextJump = null;
        AppVars.AutoMovingJumps = 0;
        AppVars.AutoMovingCityGate = ru.neverlands.abclient.model.CityGateType.None;

        boolean disabledViaManager = false;
        try {
            android.content.Context context = AppVars.getContext();
            if (context != null) {
                AutoFunctionsManager.getInstance(context).setAutoTreasureEnabled(false);
                disabledViaManager = true;
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE stop on dig: manager disable failed", e);
        }

        if (!disabledViaManager) {
            AppVars.DoSearchBox = false;
            if (AppVars.Profile != null) {
                AppVars.Profile.AutoDig = false;
                try {
                    android.content.Context context = AppVars.getContext();
                    if (context != null) {
                        AppVars.Profile.save(context);
                    }
                } catch (Exception saveEx) {
                    android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE stop on dig: profile save failed", saveEx);
                }
            }
            ExtMap.flushVisitedToDisk();
            android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE stop on dig: keep visited cache, entries="
                    + AppVars.SearchBoxVisited.size());
            AppVars.AutoTreasureDigPendingInventory = false;
            AppVars.AutoTreasureShovelReady = false;
            AppVars.AutoTreasureShovelReadyOption = "";
        }

        releaseTreasurePause("treasure_found_on_cell");
        notifyTreasureFoundOnCurrentCell(host);
        playTreasureFoundSignal();
        android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE treasure marker detected -> stop auto treasure");
        return null;
    }

    private static String maybeHandleAutoTreasureDigFlow(String html,
                                                         String address,
                                                         AutoFunctionsManager autoManager,
                                                         boolean digMarkerDetected,
                                                         Host host) {
        if (autoManager == null) {
            return null;
        }
        String selectedShovelOption = normalizeTreasureShovelOption(autoManager.getAutoTreasureShovelOption());
        if (!selectedShovelOption.equalsIgnoreCase(AppVars.AutoTreasureShovelReadyOption)) {
            AppVars.AutoTreasureShovelReady = false;
            AppVars.AutoTreasureShovelReadyOption = "";
            if (!AppVars.AutoTreasureDigPendingInventory) {
                releaseTreasurePause("dig_flow_shovel_changed");
            }
        }

        if (AppVars.AutoTreasureDigPendingInventory) {
            String prepareHtml = continueAutoTreasureDigPreparation(html, address, selectedShovelOption, host);
            if (prepareHtml != null) {
                return prepareHtml;
            }
        }

        if (!digMarkerDetected) {
            if (!AppVars.AutoTreasureDigPendingInventory) {
                releaseTreasurePause("dig_flow_no_marker");
            }
            return null;
        }

        notifyTreasureFoundOnCurrentCell(host);
        playTreasureFoundSignal();

        boolean needWearShovel = !AutoFunctionsManager.TREASURE_SHOVEL_NONE.equalsIgnoreCase(selectedShovelOption);
        if (needWearShovel && !AppVars.AutoTreasureShovelReady) {
            AppVars.AutoTreasureDigPendingInventory = true;
            applyTreasurePauseAndStopNavigator("dig_flow_need_wear_shovel");
            android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: open shovel inventory");
            return buildAutoTreasureDigOpenInventoryRedirect(html, address, host);
        }

        AppVars.AutoTreasureDigPendingInventory = false;
        String digClickHtml = buildAutoTreasureDigClickHtml(html);
        releaseTreasurePause("dig_flow_click_ready");
        if (digClickHtml != null) {
            android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: click \"Копать\" by button");
        }
        return digClickHtml;
    }
    /**
     * Продолжает этап подготовки к копке, когда сервер потребовал лопату.
     *
     * Последовательность:
     * 1) ставим паузу небоевых auto-функций и останавливаем текущий маршрут;
     * 2) гарантируем, что открыта вкладка инвентаря `im=0&wca=3` (лопаты);
     * 3) проверяем, уже ли одета нужная лопата;
     * 4) если нет — ищем ссылку "Надеть" и выполняем редирект;
     * 5) при переходных кадрах делаем ограниченный retry, чтобы не получить ложную отмену.
     *
     * Зависимости:
     * - {@link Host} для инфраструктуры MainPhp (редиректы, разбор инвентаря, URL-параметры);
     * - {@link AutoFunctionsManager} для настроек типа лопаты;
     * - {@link AppVars} для runtime-флагов этапа подготовки.
     */
    private static String continueAutoTreasureDigPreparation(String html,
                                                             String address,
                                                             String selectedShovelOption,
                                                             Host host) {
        applyTreasurePauseAndStopNavigator("dig_flow_prepare_inventory");
        boolean inventoryContext = host.mainPhpIsInv(html) || host.isInventoryAddress(address);
        if (!inventoryContext) {
            android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: route to inventory (shovels)");
            return buildAutoTreasureDigOpenInventoryRedirect(html, address, host);
        }

        if (!host.inventoryAddressMatchesFilter(address, "&im=0&wca=3")) {
            android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: switch inventory filter to wca=3");
            return buildAutoTreasureDigOpenInventoryRedirect(html, address, host);
        }

        if (!host.hasInventoryRows(html)) {
            int currentRetry = host.parseUrlParamInt(address, AUTO_TREASURE_SHOVEL_PREP_RETRY_PARAM, 0);
            if (currentRetry < AUTO_TREASURE_SHOVEL_PREP_MAX_RETRIES) {
                int nextRetry = currentRetry + 1;
                String retryUrl = "main.php?im=0&wca=3";
                if (address != null && !address.isEmpty() && host.isInventoryAddress(address)) {
                    retryUrl = host.normalizeNeverlandsMainLink(address);
                }
                retryUrl = host.appendOrReplaceUrlParam(
                        retryUrl,
                        AUTO_TREASURE_SHOVEL_PREP_RETRY_PARAM,
                        String.valueOf(nextRetry));
                android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: inventory transitional html, retry="
                        + nextRetry + "/" + AUTO_TREASURE_SHOVEL_PREP_MAX_RETRIES + ", url=" + retryUrl);
                return host.buildRedirectHtml("Авто-Клад: ожидание загрузки инвентаря лопат ("
                        + nextRetry + "/" + AUTO_TREASURE_SHOVEL_PREP_MAX_RETRIES + ")", retryUrl);
            }
            android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: inventory transitional retry limit reached ("
                    + currentRetry + "/" + AUTO_TREASURE_SHOVEL_PREP_MAX_RETRIES + ")");
        }

        if (AutoFunctionsManager.TREASURE_SHOVEL_NONE.equalsIgnoreCase(selectedShovelOption)) {
            markAutoTreasureShovelReady(selectedShovelOption);
            return buildAutoTreasureDigReturnToMapHtml(html, host);
        }

        if (isTreasureShovelEquipped(html, selectedShovelOption)) {
            markAutoTreasureShovelReady(selectedShovelOption);
            android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: shovel already equipped");
            return buildAutoTreasureDigReturnToMapHtml(html, host);
        }

        String wearLink = resolveTreasureShovelWearLink(html, selectedShovelOption, host);
        if (wearLink != null && !wearLink.isEmpty()) {
            android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: wear shovel link=" + wearLink);
            return host.buildRedirectHtml("Авто-Клад: одеваем лопату", wearLink);
        }

        // Переходный кадр: список предметов уже есть, но ссылка "Надеть" ещё не распарсилась.
        // Делаем bounded-retry на той же вкладке, чтобы исключить ложное "лопата не найдена".
        int currentRetryWearLink = host.parseUrlParamInt(address, AUTO_TREASURE_SHOVEL_PREP_RETRY_PARAM, 0);
        if (currentRetryWearLink < AUTO_TREASURE_SHOVEL_PREP_MAX_RETRIES) {
            int nextRetry = currentRetryWearLink + 1;
            String retryUrl = "main.php?im=0&wca=3";
            if (address != null && !address.isEmpty() && host.isInventoryAddress(address)) {
                retryUrl = host.normalizeNeverlandsMainLink(address);
            }
            retryUrl = host.appendOrReplaceUrlParam(
                    retryUrl,
                    AUTO_TREASURE_SHOVEL_PREP_RETRY_PARAM,
                    String.valueOf(nextRetry));
            android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: shovel wear-link not found yet, retry="
                    + nextRetry + "/" + AUTO_TREASURE_SHOVEL_PREP_MAX_RETRIES + ", url=" + retryUrl);
            return host.buildRedirectHtml("Авто-Клад: ожидание доступности лопаты ("
                    + nextRetry + "/" + AUTO_TREASURE_SHOVEL_PREP_MAX_RETRIES + ")", retryUrl);
        }

        android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: shovel wear-link retry limit reached ("
                + currentRetryWearLink + "/" + AUTO_TREASURE_SHOVEL_PREP_MAX_RETRIES + ")");
        AppVars.AutoTreasureDigPendingInventory = false;
        AppVars.AutoTreasureShovelReady = false;
        AppVars.AutoTreasureShovelReadyOption = "";
        releaseTreasurePause("dig_flow_shovel_not_found");
        host.sendInventoryChatMessage(host.buildServerChatTimeHtml()
                + "<font color=#FF0000>Авто-Клад: лопата не найдена, копка отменена.</font>");
        android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE dig flow: shovel not found, dig cancelled");
        return buildAutoTreasureDigReturnToMapHtml(html, host);
    }

    private static void markAutoTreasureShovelReady(String selectedShovelOption) {
        AppVars.AutoTreasureDigPendingInventory = false;
        AppVars.AutoTreasureShovelReady = true;
        AppVars.AutoTreasureShovelReadyOption = selectedShovelOption == null ? "" : selectedShovelOption;
    }

    public static void applyTreasurePauseAndStopNavigator(String reason) {
        // Инкрементируем счетчик глубины и устанавливаем флаг
        synchronized (TreasureDig.class) {
            treasurePauseDepth++;
            AppVars.TreasureDigPauseNonCombatAutoFunctions = true;
            android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE pause depth++ = " + treasurePauseDepth 
                    + ", reason=" + reason);
        }
        
        if (!AppVars.AutoMoving) {
            return;
        }
        try {
            android.content.Context context = AppVars.getContext();
            if (context != null) {
                AutoFunctionsManager.getInstance(context).stopAutoMoving();
                android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE pause enabled: navigator stopped, reason=" + reason);
                return;
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE pause enabled: manager stop failed, reason=" + reason, e);
        }
        AppVars.AutoMoving = false;
        AppVars.AutoMovingDestinaton = null;
        AppVars.AutoMovingMapPath = null;
        AppVars.AutoMovingNextJump = null;
        AppVars.AutoMovingJumps = 0;
        AppVars.AutoMovingCityGate = ru.neverlands.abclient.model.CityGateType.None;
        android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE pause enabled: fallback navigator reset, reason=" + reason);
    }

    /**
     * Безопасный сброс паузы с учетом глубины вложения.
     * Гарантирует, что флаг останется true, пока есть активные вложенные вызовы applyTreasurePauseAndStopNavigator().
     */
    public static void releaseTreasurePause(String reason) {
        synchronized (TreasureDig.class) {
            if (treasurePauseDepth > 0) {
                treasurePauseDepth--;
            } else {
                android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE pause depth already 0, ignoring release, reason=" + reason);
                return;
            }
            
            if (treasurePauseDepth <= 0) {
                treasurePauseDepth = 0; // Защита от отрицательных значений
                AppVars.TreasureDigPauseNonCombatAutoFunctions = false;
                android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE pause depth-- = 0, RELEASED, reason=" + reason);
            } else {
                android.util.Log.d(TAG, "AUTO_SEARCH_BOX_TRACE pause depth-- = " + treasurePauseDepth 
                        + ", still active, reason=" + reason);
            }
        }
    }

    private static String buildAutoTreasureDigOpenInventoryRedirect(String html, String address, Host host) {
        String fallbackRedirect = host.mainPhpFindInvWithFallback(html, "&im=0&wca=3", address);
        if (fallbackRedirect != null && !fallbackRedirect.isEmpty()) {
            return fallbackRedirect;
        }
        return host.buildRedirectHtml("\u0410\u0432\u0442\u043e-\u041a\u043b\u0430\u0434: \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044c \u043b\u043e\u043f\u0430\u0442", "main.php?im=0&wca=3");
    }

    private static String buildAutoTreasureDigReturnToMapHtml(String html, Host host) {
        String mapReturnHtml = host.mainPhpFindMapReturnForAutoMoving(html);
        if (mapReturnHtml != null && !mapReturnHtml.isEmpty()) {
            return mapReturnHtml;
        }
        String link = "main.php?get_id=56&act=10&go=ret";
        String vcode = SessionManager.getInstance().getValidVCodeForAction("treasure_dig");
        if (vcode != null) {
            link += "&vcode=" + vcode;
        } else {
            AppLog.w("vcode_migration", TAG, "[VCode_MISSING] getValidVCodeForAction returned null for treasure_dig");
        }
        return host.buildRedirectHtml("Авто-Клад: возврат на природу", link);
    }

    private static String buildAutoTreasureDigClickHtml(String html) {
        if (html == null || html.isEmpty() || !html.contains(DIG_BUTTON_MARKER)) {
            return null;
        }
        String script = "<script language=\"JavaScript\">"
                + "setTimeout(function(){"
                + "try{"
                + "if(window.__abAutoTreasureDigClicked){return;}"
                + "window.__abAutoTreasureDigClicked=true;"
                + "if(typeof ButClick==='function' && document.getElementById('dig')){ButClick('dig');return;}"
                + "var digBtn=document.getElementById('dig');"
                + "if(digBtn && typeof digBtn.click==='function'){digBtn.click();}"
                + "}catch(e){}"
                + "},180);"
                + "</script>";
        int bodyEnd = html.toLowerCase(Locale.ROOT).lastIndexOf("</body>");
        if (bodyEnd != -1) {
            return html.substring(0, bodyEnd) + script + html.substring(bodyEnd);
        }
        return html + script;
    }

    private static String resolveTreasureShovelWearLink(String html, String selectedShovelOption, Host host) {
        List<WearInvEntry> invList = host.getWearInvList(html);
        if (invList == null || invList.isEmpty()) {
            return null;
        }
        for (WearInvEntry thing : invList) {
            if (thing == null || thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) {
                continue;
            }
            if (isTreasureShovelOptionMatches(thing.name, selectedShovelOption)) {
                return thing.wearLink;
            }
        }
        return null;
    }

    private static boolean isTreasureShovelEquipped(String html, String selectedShovelOption) {
        ParsedDressed dressed = new ParsedDressed(html);
        if (!dressed.Valid) {
            return false;
        }
        return isTreasureShovelOptionMatches(dressed.Hand1, selectedShovelOption)
                || isTreasureShovelOptionMatches(dressed.Hand2, selectedShovelOption);
    }

    static boolean isTreasureShovelOptionMatches(String itemName, String selectedShovelOption) {
        if (itemName == null || itemName.trim().isEmpty()) {
            return false;
        }
        if (AutoFunctionsManager.TREASURE_SHOVEL_ANY.equalsIgnoreCase(selectedShovelOption)) {
            return isTreasureShovelName(itemName);
        }
        return containsIgnoreCase(itemName, selectedShovelOption);
    }

    static boolean isTreasureShovelName(String itemName) {
        return containsIgnoreCase(itemName, AutoFunctionsManager.TREASURE_SHOVEL_SEEKER)
                || containsIgnoreCase(itemName, AutoFunctionsManager.TREASURE_SHOVEL_TRAVEL)
                || containsIgnoreCase(itemName, AutoFunctionsManager.TREASURE_SHOVEL_ARCHAEOLOGIST);
    }

    static String normalizeTreasureShovelOption(String option) {
        if (option == null || option.trim().isEmpty()) {
            return AutoFunctionsManager.TREASURE_SHOVEL_ANY;
        }
        String value = option.trim();
        if (AutoFunctionsManager.TREASURE_SHOVEL_NONE.equalsIgnoreCase(value)) {
            return AutoFunctionsManager.TREASURE_SHOVEL_NONE;
        }
        if (AutoFunctionsManager.TREASURE_SHOVEL_SEEKER.equalsIgnoreCase(value)) {
            return AutoFunctionsManager.TREASURE_SHOVEL_SEEKER;
        }
        if (AutoFunctionsManager.TREASURE_SHOVEL_TRAVEL.equalsIgnoreCase(value)) {
            return AutoFunctionsManager.TREASURE_SHOVEL_TRAVEL;
        }
        if (AutoFunctionsManager.TREASURE_SHOVEL_ARCHAEOLOGIST.equalsIgnoreCase(value)) {
            return AutoFunctionsManager.TREASURE_SHOVEL_ARCHAEOLOGIST;
        }
        return AutoFunctionsManager.TREASURE_SHOVEL_ANY;
    }

    /**
     * Публикует системное сообщение в чат о том, что на текущей клетке найден клад.
     */
    private static void notifyTreasureFoundOnCurrentCell(Host host) {
        String mapLocation = (AppVars.Profile != null && AppVars.Profile.MapLocation != null)
                ? AppVars.Profile.MapLocation.trim() : "";
        String cellSuffix = "";
        if (!mapLocation.isEmpty()) {
            cellSuffix = " <font color=#003399>на клетке № <b>"
                    + host.escapeHtmlAttr(mapLocation)
                    + "</b></font>";
        }
        String messageHtml = host.buildServerChatTimeHtml()
                + "<font color=#cc0000><b>На текущей клетке обнаружен клад!</b></font>"
                + cellSuffix;
        host.sendInventoryChatMessage(messageHtml);
    }

    /**
     * Подаёт звуковой сигнал при обнаружении клада.
     * Используется как Android-аналог C# `EventSounds.PlayAlarm()`.
     */
    private static void playTreasureFoundSignal() {
        android.content.Context context = AppVars.getContext();
        if (context == null) {
            return;
        }
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            if (alarmUri == null) {
                return;
            }
            Ringtone ringtone = RingtoneManager.getRingtone(context, alarmUri);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "AUTO_SEARCH_BOX_TRACE play alarm failed", e);
        }
    }

    private static boolean containsIgnoreCase(String value, String token) {
        if (value == null || token == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }
}
