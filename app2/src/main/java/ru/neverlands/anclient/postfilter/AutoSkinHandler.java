package ru.neverlands.anclient.postfilter;

import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.model.ParsedDressed;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.ChatStats;
import ru.neverlands.anclient.utils.HelperStrings;

/**
 * Владелец AutoSkin-пайплайна для main.php.
 *
 * Источник выноса: MainPhp.process() и старые helpers `mainPhpRaz`, `mainPhpProcessSkills`,
 * `mainPhpArmedKnife`, `mainPhpWearKnife`, `mainPhpGetSkinRes`.
 *
 * Основные runtime-зависимости:
 * - AppVars.AutoSkinCheckUm: нужно открыть `mselect=1` и прочитать навык "Охота".
 * - AppVars.AutoSkinCheckRes: нужно открыть инвентарь ресурсов `&im=5` и обновить AppVars.SkinRes.
 * - AppVars.AutoSkinCheckKnife/AppVars.AutoSkinArmedKnife: нужно проверить/надеть нож через `&im=0&wca=4`.
 * - AppVars.NeverTimer: запрещает новый non-combat redirect до истечения серверного таймера.
 * - InventoryParser/FightAuto/MainPhp facades: навигация, inventory-detect, redirect HTML, parsing `fight_ty`.
 */
final class AutoSkinHandler {

    private static final String TAG = "AutoSkinHandler";
    static final long AUTO_SKIN_KNIFE_RECHECK_INTERVAL_MS = 60_000L;

    private AutoSkinHandler() {
    }

    /**
     * Авто-разделка: ищет ссылку `get_id=17` в боевом HTML и строит redirect.
     *
     * Зависимости:
     * - html: текущий main.php/fight кадр, где может быть JS `var fight_ty = [...]`.
     * - buildRazLinkFromFightTyPayload(strFightTy): основной C#-совместимый путь через `fight_ty[9]`.
     * - extractRazLinkFromHtml(html): fallback для обычной ссылки `main.php?get_id=17...`.
     * - MainPhp.buildRedirectHtml(...): единый redirect-шаблон с HtmlUtils.GENERATED_PAGE_MARKER.
     */
    static String mainPhpRaz(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "var\\s+fight_ty\\s*=\\s*\\[(.*?)\\];",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL
        ).matcher(html);
        int variantIndex = 0;
        while (matcher.find()) {
            variantIndex++;
            String strFightTy = matcher.group(1);
            String razLink = buildRazLinkFromFightTyPayload(strFightTy);
            if (razLink != null) {
                String msg = "AUTO_SKIN_TRACE mainPhpRaz: redirect via fight_ty[";
                AppLog.d(TAG, msg);
                return MainPhp.buildRedirectHtml("Разделка", razLink);
            }
        }

        String fallbackRazLink = extractRazLinkFromHtml(html);
        if (fallbackRazLink != null) {
            String msg = "AUTO_SKIN_TRACE mainPhpRaz: fallback link redirect to ";
            AppLog.d(TAG, msg);
            return MainPhp.buildRedirectHtml("Разделка", fallbackRazLink);
        }
        return null;
    }

    static String buildRazLinkFromFightTyPayload(String strFightTy) {
        if (strFightTy == null || strFightTy.isEmpty()) {
            return null;
        }
        List<String> fightTy = FightAuto.splitJsTopLevelCsv(strFightTy);
        if (fightTy.size() <= 9) {
            return null;
        }
        String fightTyNine = fightTy.get(9);
        if (fightTyNine == null) {
            return null;
        }
        String trimmed = fightTyNine.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return null;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1);
        List<String> razParams = FightAuto.splitJsTopLevelCsv(inner);
        if (razParams.size() <= 5) {
            return null;
        }
        String type = FightAuto.trimJsToken(razParams.get(0));
        String p = FightAuto.trimJsToken(razParams.get(1));
        String uid = FightAuto.trimJsToken(razParams.get(2));
        String s = FightAuto.trimJsToken(razParams.get(3));
        String m = FightAuto.trimJsToken(razParams.get(4));
        String vcode = FightAuto.trimJsToken(razParams.get(5));
        if (type.isEmpty() || uid.isEmpty() || vcode.isEmpty()) {
            return null;
        }
        return "http://neverlands.ru/main.php?get_id=17&type=" + type
                + "&p=" + p
                + "&uid=" + uid
                + "&s=" + s
                + "&m=" + m
                + "&vcode=" + vcode;
    }

    static String extractRazLinkFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        return FightAuto.findMainPhpLinkByQueryParts(html, "get_id=17");
    }

    /**
     * Читает навык "Охота" со страницы `mselect=1` и синхронизирует AppVars.SkinUm.
     *
     * Важные переменные:
     * - AppVars.AutoSkinCheckUm: сбрасывается в false после успешного чтения или fallback-сброса на mselect=1.
     * - AppVars.SkinUm: предыдущее значение навыка, используется для расчёта прироста `(+N)`.
     * - address: нужен только для защиты от зависания AutoSkinCheckUm, если сервер отдал mselect=1 без блока навыка.
     * - AppVars.ACTION_ADD_CHAT_MESSAGE: уведомление о росте навыка уходит в локальный чат.
     */
    static void mainPhpProcessSkills(String html, String address) {
        if (html == null || html.isEmpty()) {
            return;
        }
        String skinSkill = HelperStrings.subString(
                html,
                "Охота</td><td bgcolor=#FCFAF3><font class=proce><font color=#555555><div align=center>[",
                "]");
        if (skinSkill == null || skinSkill.isEmpty()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("Охота</td><td[^\\[]*\\[(\\d+)\\]", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                    .matcher(html);
            if (matcher.find()) {
                skinSkill = matcher.group(1);
            }
        }
        if (skinSkill != null && !skinSkill.isEmpty()) {
            try {
                int skinUm = Integer.parseInt(skinSkill.trim());
                AppVars.AutoSkinCheckUm = false;
                if (AppVars.SkinUm != skinUm) {
                    StringBuilder sb = new StringBuilder("Умение разделки: <span style=\"color:#009933;font-weight:bold;\">")
                            .append(skinUm)
                            .append("</span>");
                    if (AppVars.SkinUm > 0 && AppVars.SkinUm < skinUm) {
                        sb.append(" (+").append(skinUm - AppVars.SkinUm).append(")");
                    }
                    AppVars.SkinUm = skinUm;
                    if (isAutoSkinEnabledByPreference() && AppVars.getContext() != null) {
                        Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
                        msgIntent.putExtra("message", sb.toString());
                        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(msgIntent);
                    }
                }
                String msg = "AUTO_SKIN_TRACE skill parsed: SkinUm=" + skinUm + ", AutoSkinCheckUm=false";
                AppLog.d(TAG, msg);
            } catch (Exception e) {
                String msg = "AUTO_SKIN_TRACE skill parse failed: " + skinSkill;
                AppLog.w(TAG, msg, e);
            }
            return;
        }
        if (AppVars.AutoSkinCheckUm && address != null && address.contains("mselect=1")) {
            AppVars.AutoSkinCheckUm = false;
            String msg = "AUTO_SKIN_TRACE mselect=1 without skill block, forced AutoSkinCheckUm=false";
            AppLog.w(TAG, msg);
        }
    }

    static boolean isAutoSkinEnabledByPreference() {
        if (AppVars.Profile != null) {
            return AppVars.Profile.SkinAuto;
        }
        try {
            if (AppVars.getContext() != null) {
                return AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoSkinEnabled();
            }
        } catch (Exception e) {
            String msg = "isAutoSkinEnabledByPreference: fallback=false";
            AppLog.w(TAG, msg, e);
        }
        return false;
    }

    static void maybeMarkAutoSkinKnifeRecheck() {
        if (!isAutoSkinEnabledByPreference()) {
            return;
        }
        long now = System.currentTimeMillis();
        long lastChecked = AppVars.AutoSkinLastChecked;
        if (lastChecked <= 0L || (now - lastChecked) > AUTO_SKIN_KNIFE_RECHECK_INTERVAL_MS) {
            AppVars.AutoSkinLastChecked = now;
            AppVars.AutoSkinCheckKnife = true;
            String msg = "AUTO_SKIN_TRACE periodic knife recheck requested";
            AppLog.d(TAG, msg);
        }
    }

    /**
     * Главная точка AutoSkin из MainPhp.process().
     *
     * Порядок сохранён из старого MainPhp-блока:
     * 1. До боевой обработки пытаемся выполнить авто-разделку через mainPhpRaz(html).
     * 2. Вычисляем suspend-флаги, чтобы не перехватить finish-flow или generated transition page.
     * 3. При inventory reload snapshot читаем ресурсы, если AppVars.AutoSkinCheckRes=true.
     * 4. При готовом AppVars.NeverTimer запускаем skill/resource/knife pipeline.
     *
     * Входные переменные:
     * - address/html: текущий main.php URL и HTML после Filter.removeDoctype/Russian.getString.
     * - isFightFrame/isFightTopFrame: запрещают non-combat навигацию в бою.
     * - isFightFinishAddressForInv: suspendAutoSkinForFinishFlow, чтобы act=7 не сломал finish-flow.
     *
     * Runtime-зависимости:
     * - MainPhp.isNonCombatAutoPausedByFastAction(): подавляет AutoSkin при активном FastNeed/таймере/кладе.
     * - InventoryParser.isLikelyInventoryReloadSnapshot(...) и isGeneratedTransitionPage(...): защита от race redirect.
     * - AppVars.AutoSkinCheckUm/AutoSkinCheckRes/AutoSkinCheckKnife/AutoSkinArmedKnife/NeverTimer.
     * - MainPhp.mainPhpFindPerc/mainPhpFindInvWithFallback/mainPhpIsInv/isInventoryAddress для navigation facades.
     *
     * Возврат: готовый redirect-HTML/hold-HTML или null, если AutoSkin не должен менять текущий ответ.
     */
    static String processMainPhpAutoSkinStep(String address,
                                             String html,
                                             boolean isFightFrame,
                                             boolean isFightTopFrame,
                                             boolean isFightFinishAddressForInv) {
        if (!MainPhp.isNonCombatAutoPausedByFastAction() && isAutoSkinEnabledByPreference()) {
            String razHtml = mainPhpRaz(html);
            if (razHtml != null) {
                return razHtml;
            }
        }

        boolean suspendAutoSkinForFinishFlow = isFightFinishAddressForInv;
        boolean suspendAutoSkinForInventoryReload = InventoryParser.isLikelyInventoryReloadSnapshot(address, html);
        boolean suspendAutoSkinForGeneratedTransition = InventoryParser.isGeneratedTransitionPage(address, html);
        if (suspendAutoSkinForFinishFlow || suspendAutoSkinForInventoryReload || suspendAutoSkinForGeneratedTransition) {
            AppLog.d(TAG, "AUTO_SKIN_TRACE suspended: finishFlow=" + suspendAutoSkinForFinishFlow
                    + ", inventoryReload=" + suspendAutoSkinForInventoryReload
                    + ", generatedTransition=" + suspendAutoSkinForGeneratedTransition
                    + ", address=" + address);
        }

        if (!MainPhp.isNonCombatAutoPausedByFastAction()
                && !isFightFrame
                && !isFightTopFrame
                && isAutoSkinEnabledByPreference()
                && !suspendAutoSkinForFinishFlow
                && !suspendAutoSkinForGeneratedTransition
                && suspendAutoSkinForInventoryReload
                && AppVars.AutoSkinCheckRes
                && (MainPhp.mainPhpIsInv(html) || MainPhp.inventoryAddressMatchesFilter(address, "&im=5"))) {
            AppVars.AutoSkinCheckRes = false;
            String msgSkinLoad = "AUTO_SKIN_TRACE inventoryReload fallback: read skin resources in transition snapshot";
            AppLog.d(TAG, msgSkinLoad);
            mainPhpGetSkinRes(html);
        }

        if (!MainPhp.isNonCombatAutoPausedByFastAction()
                && !isFightFrame
                && !isFightTopFrame
                && isAutoSkinEnabledByPreference()
                && !suspendAutoSkinForFinishFlow
                && !suspendAutoSkinForInventoryReload
                && !suspendAutoSkinForGeneratedTransition) {
            long nowMs = System.currentTimeMillis();
            if (AppVars.NeverTimer <= 0L || nowMs > AppVars.NeverTimer) {
                if (AppVars.AutoSkinCheckUm) {
                    String phtml = MainPhp.mainPhpFindPerc(html);
                    if (phtml != null && !phtml.isEmpty()) {
                        String msgSkinChar = "AUTO_SKIN_TRACE redirect to character page for skill check";
                        AppLog.d(TAG, msgSkinChar);
                        return phtml;
                    }
                    if (html.toLowerCase(Locale.ROOT).contains("<input type=button class=lbut value=\"умения\" onclick")) {
                        String msgSkinSkills = "AUTO_SKIN_TRACE redirect to skills page mselect=1";
                        AppLog.d(TAG, msgSkinSkills);
                        return MainPhp.buildRedirectHtml("Переключение на умения персонажа", "main.php?mselect=1");
                    }
                }
                if (AppVars.AutoSkinCheckRes) {
                    String invHtml = MainPhp.mainPhpFindInvWithFallback(html, "&im=5", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        String msgSkinRes = "AUTO_SKIN_TRACE redirect to resources inventory (&im=5)";
                        AppLog.d(TAG, msgSkinRes);
                        return invHtml;
                    }
                    if (MainPhp.mainPhpIsInv(html) || MainPhp.inventoryAddressMatchesFilter(address, "&im=5")) {
                        AppVars.AutoSkinCheckRes = false;
                        String msgSkinGetRes = "AUTO_SKIN_TRACE read skin resources";
                        AppLog.d(TAG, msgSkinGetRes);
                        mainPhpGetSkinRes(html);
                    }
                }
                if (AppVars.AutoSkinCheckKnife) {
                    String perchtml = MainPhp.mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        String msgSkinKnife = "AUTO_SKIN_TRACE redirect to character page for knife check";
                        AppLog.d(TAG, msgSkinKnife);
                        return perchtml;
                    }
                    AppVars.AutoSkinArmedKnife = false;
                    if (MainPhp.mainPhpIsPerc(html)) {
                        AppVars.AutoSkinArmedKnife = mainPhpArmedKnife(html);
                        AppVars.AutoSkinCheckKnife = false;
                        String msgSkinResult = "AUTO_SKIN_TRACE knife check result: armed=";
                        AppLog.d(TAG, msgSkinResult);
                    }
                }
                if (!AppVars.AutoSkinArmedKnife) {
                    String invHtml = MainPhp.mainPhpFindInvWithFallback(html, "&im=0&wca=4", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        String msgSkinUdInv = "AUTO_SKIN_TRACE redirect to items inventory (&im=0&wca=4)";
                        AppLog.d(TAG, msgSkinUdInv);
                        return invHtml;
                    }
                    if (MainPhp.mainPhpIsInv(html) || MainPhp.isInventoryAddress(address)) {
                        invHtml = mainPhpWearKnife(html);
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!MainPhp.inventoryAddressMatchesFilter(address, "&im=0&wca=4")) {
                                String msgSkinUdTab = "AUTO_SKIN_TRACE switch to items tab for knife search";
                                AppLog.d(TAG, msgSkinUdTab);
                                return MainPhp.buildRedirectHtml("Переключение на вещи", "main.php?im=0&wca=4");
                            }
                        } else {
                            AppVars.AutoSkinCheckKnife = true;
                            return invHtml;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Проверяет, надет ли охотничий нож на странице персонажа.
     * Зависимость: ParsedDressed.Valid и ParsedDressed.IsWearKnife(), html должен быть страницей персонажа.
     */
    static boolean mainPhpArmedKnife(String html) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        return parsedDressed.IsWearKnife();
    }

    /**
     * Ищет охотничий нож в инвентаре и возвращает redirect на wear-link.
     *
     * Важные переменные:
     * - InventoryParser.getWearInvList(html): список предметов с thing.name/thing.wearLink.
     * - ParsedDressed.getSkinKnifeNames(): допустимые названия ножей.
     * - AppVars.AutoSkinArmedKnife: сбрасывается в false, если нож не найден/не надет.
     */
    static String mainPhpWearKnife(String html) {
        ParsedDressed dressed = new ParsedDressed(html);
        if (!dressed.Valid) {
            return null;
        }
        boolean isWear = dressed.IsWearKnife();
        if (!isWear) {
            List<InventoryParser.WearInvEntry> invList = InventoryParser.getWearInvList(html);
            String[] knives = ParsedDressed.getSkinKnifeNames();
            for (InventoryParser.WearInvEntry thing : invList) {
                if (thing.name == null || thing.wearLink == null || thing.wearLink.isEmpty()) {
                    continue;
                }
                for (String knife : knives) {
                    if (InventoryParser.containsIgnoreCase(thing.name, knife)) {
                        AppLog.d(TAG, "AUTO_SKIN_TRACE mainPhpWearKnife: wear " + thing.name
                                + ", link=" + thing.wearLink);
                        return MainPhp.buildRedirectHtml("Одеваем " + thing.name, thing.wearLink);
                    }
                }
            }
        }
        AppVars.AutoSkinArmedKnife = false;
        return null;
    }

    /**
     * Считывает результат разделки из инвентаря ресурсов и обновляет статистику.
     *
     * Важные переменные:
     * - AppVars.SkinRes: map ресурс -> последний вес, baselineFill отличает первый проход от реального прироста.
     * - deltaForChat: список приростов для локального чата, отправляется только при Profile.RazdChatReport=true.
     * - deltaForStatsKg: агрегат прироста в килограммах для ChatStats.addResourceDeltaKg(...).
     * - parsedResources/diffResources: диагностические счётчики для AUTO_SKIN_TRACE.
     */
    static void mainPhpGetSkinRes(String html) {
        final String patternStartRes = "<B>Рост</B></td></tr>";
        int pos = html.indexOf(patternStartRes);
        boolean anchorFound = pos != -1;
        if (!anchorFound) {
            pos = 0;
        }
        StringBuilder sb = new StringBuilder();
        List<String> deltaForChat = new ArrayList<>();
        Map<String, Double> deltaForStatsKg = new LinkedHashMap<>();
        boolean baselineFill = AppVars.SkinRes.isEmpty();
        int parsedResources = 0;
        int diffResources = 0;
        if (anchorFound) {
            pos += patternStartRes.length();
        }
        while (true) {
            final String patternStartTr = "<input type=checkbox name=";
            pos = html.indexOf(patternStartTr, pos);
            if (pos == -1) {
                break;
            }
            pos += patternStartTr.length();
            final String patternEndTr = "</tr>";
            int posEnd = html.indexOf(patternEndTr, pos);
            if (posEnd == -1) {
                break;
            }
            posEnd += patternEndTr.length();
            String htmlEntry = html.substring(pos, posEnd);
            String valString = HelperStrings.subString(htmlEntry, " width=15% class=travma align=center>", "</td>");
            Double val = tryParseDoubleInvariant(valString);
            if (val != null) {
                String name = HelperStrings.subString(htmlEntry, " width=25% class=travma align=center><B>", "</B><BR>");
                if (name != null && !name.isEmpty()) {
                    parsedResources++;
                    if (AppVars.SkinRes.containsKey(name)) {
                        double oldVal = AppVars.SkinRes.get(name);
                        if (Math.abs(oldVal - val) > 0.009d) {
                            double diff = val - oldVal;
                            sb.append("<span style=\"color:#009933;font-weight:bold;\">«")
                                    .append(name).append(" ").append(String.format(Locale.US, "%.2f", val))
                                    .append("»</span> (+")
                                    .append(String.format(Locale.US, "%.2f", diff))
                                    .append(")");
                            AppVars.SkinRes.put(name, val);
                            if (diff > 0d) {
                                diffResources++;
                                deltaForChat.add(name + " (+" + String.format(Locale.US, "%.2f", diff) + " кг)");
                                Double existingDelta = deltaForStatsKg.get(name);
                                deltaForStatsKg.put(name, (existingDelta == null ? 0d : existingDelta) + diff);
                            }
                        } else {
                            sb.append("<span style=\"color:#009933;font-weight:bold;\">«")
                                    .append(name).append(" ").append(String.format(Locale.US, "%.2f", val))
                                    .append("»</span>");
                        }
                    } else {
                        sb.append("<span style=\"color:#009933;font-weight:bold;\">«")
                                .append(name).append(" ").append(String.format(Locale.US, "%.2f", val))
                                .append("»</span>");
                        AppVars.SkinRes.put(name, val);
                        if (!baselineFill && val > 0d) {
                            diffResources++;
                            deltaForChat.add(name + " (+" + String.format(Locale.US, "%.2f", val) + " кг)");
                            Double existingDelta = deltaForStatsKg.get(name);
                            deltaForStatsKg.put(name, (existingDelta == null ? 0d : existingDelta) + val);
                        }
                    }
                }
            }
            pos = posEnd;
        }
        if (!deltaForChat.isEmpty()) {
            boolean canReportToChat = AppVars.Profile != null && AppVars.Profile.RazdChatReport;
            if (canReportToChat) {
            String message = FightAuto.buildServerChatTimeHtml()
                    + "<font color=#006600><b>Результат разделки:</b></font> "
                    + String.join(", ", deltaForChat);
            if (AppVars.getContext() != null) {
                Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
                intent.putExtra("message", message);
                LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
            }
            } else {
                AppLog.d(TAG, "AUTO_SKIN_TRACE mainPhpGetSkinRes: chat skipped, RazdChatReport=false"
                        + ", deltaCount=" + deltaForChat.size());
            }
        }
        if (!deltaForStatsKg.isEmpty()) {
            ChatStats.addResourceDeltaKg(deltaForStatsKg);
        }
        AppLog.d(TAG, "AUTO_SKIN_TRACE mainPhpGetSkinRes: anchorFound=" + anchorFound
                + ", baselineFill=" + baselineFill
                + ", parsedResources=" + parsedResources
                + ", diffResources=" + diffResources
                + ", deltaMapSize=" + deltaForStatsKg.size());
    }

    private static Double tryParseDoubleInvariant(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim()
                .replace("\"", "")
                .replace("'", "")
                .replace('\u00A0', ' ')
                .replace(" ", "")
                .replace(",", ".");
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
