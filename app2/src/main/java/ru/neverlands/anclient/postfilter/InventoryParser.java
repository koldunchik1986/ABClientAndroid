package ru.neverlands.anclient.postfilter;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ru.neverlands.anclient.model.InvComparer;
import ru.neverlands.anclient.model.InvEntry;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.HelperStrings;
import ru.neverlands.anclient.utils.HtmlUtils;

public final class InventoryParser {
    private static final String TAG = "InventoryParser";

    public static void parseAndSaveComplectsFromInventory(String html) {
        if (html == null || html.isEmpty() || AppVars.Profile == null) {
            return;
        }
        
        List<String> complectNames = new ArrayList<>();
        
        int startIdx = 0;
        while (true) {
            final String marker = "compl_view(\"";
            startIdx = html.indexOf(marker, startIdx);
            if (startIdx == -1) break;
            
            startIdx += marker.length();
            int endNameIdx = html.indexOf("\"", startIdx);
            if (endNameIdx == -1) break;
            
            String complectName = html.substring(startIdx, endNameIdx);
            if (!complectName.isEmpty() && !complectNames.contains(complectName)) {
                complectNames.add(complectName);
                String msg_found = "COMPLECT_INV_PARSE_TRACE: found complect=\"" + complectName + "\"";
                AppLog.d(TAG, TAG, msg_found);
            }
            
            startIdx = endNameIdx;
        }
        
        if (!complectNames.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < complectNames.size(); i++) {
                if (i > 0) sb.append("|");
                sb.append(complectNames.get(i));
            }
            AppVars.Profile.SavedComplectsList = sb.toString();
            AppVars.Profile.save(AppVars.getContext());
            
            String msg = "COMPLECT_PARSE: saved " + complectNames.size() + " complects: " + AppVars.Profile.SavedComplectsList;
            AppLog.d(TAG, TAG, msg);
        }
    }

    public static final class WearInvEntry {
        public String name;
        public String wearLink;
    }

    public static List<WearInvEntry> getWearInvList(String html) {
        List<WearInvEntry> invList = new ArrayList<>();
        final String patternStartInv = "</b></font></td></tr>";
        int pos = html.indexOf(patternStartInv);
        if (pos == -1) {
            return invList;
        }
        pos += patternStartInv.length();
        while (true) {
            final String patternStartTr = "<tr><td bgcolor=#F5F5F5>";
            if (pos + patternStartTr.length() > html.length()
                    || !html.substring(pos, pos + patternStartTr.length()).startsWith(patternStartTr)) {
                break;
            }
            final String patternEndTrLong = "<td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td></tr></table></td></tr></table></td></tr>";
            int posEnd = html.indexOf(patternEndTrLong, pos);
            if (posEnd == -1) {
                final String patternEndTrShort = "<img src=http://image.neverlands.ru/1x1.gif width=1 height=5></td></tr></table></td></tr>";
                posEnd = html.indexOf(patternEndTrShort, pos);
                if (posEnd == -1) {
                    return invList;
                }
                posEnd += patternEndTrShort.length();
            } else {
                posEnd += patternEndTrLong.length();
            }
            String htmlEntry = html.substring(pos, posEnd);
            WearInvEntry entry = parseWearInvEntry(htmlEntry);
            if (entry != null) {
                invList.add(entry);
            }
            pos = posEnd;
        }
        return invList;
    }

    private static WearInvEntry parseWearInvEntry(String htmlEntry) {
        WearInvEntry entry = new WearInvEntry();
        entry.name = HelperStrings.subString(htmlEntry, "<font class=nickname><b> ", "</b>");
        String wearLink = HelperStrings.subString(
                htmlEntry,
                "<input type=button class=invbut onclick=\"location='",
                "'\" value=\"Надеть\">");
        if ((wearLink == null || wearLink.isEmpty()) && htmlEntry.contains("value=\"Надеть\"")) {
            int wearButtonPos = htmlEntry.indexOf("value=\"Надеть\"");
            int locationPos = htmlEntry.lastIndexOf("location='", wearButtonPos);
            if (locationPos != -1) {
                int start = locationPos + "location='".length();
                int end = htmlEntry.indexOf('\'', start);
                if (end > start) {
                    wearLink = htmlEntry.substring(start, end);
                }
            }
        }
        entry.wearLink = wearLink == null ? "" : wearLink;
        return entry;
    }

    static WearInvEntry parseWearInvEntryPublic(String htmlEntry) {
        return parseWearInvEntry(htmlEntry);
    }

    public static boolean containsIgnoreCase(String value, String token) {
        if (value == null || token == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    static boolean mainPhpIsInv(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        if (html.contains("<a href=\"?im=0\"><img") || html.contains("<a href=?im=0><img")) {
            return true;
        }
        return hasInventoryRows(html);
    }

    static boolean hasInventoryRows(String html) {
        int firstRowPos = html.indexOf("<tr><td bgcolor=#F5F5F5>");
        if (firstRowPos == -1) {
            return false;
        }
        boolean hasNickname = containsIgnoreCase(html, "<font class=nickname><b>");
        boolean hasUseAction = containsIgnoreCase(html, "value=\"\u0418\u0441\u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u044C\"")
                || containsIgnoreCase(html, "confirm('\u0418\u0441\u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u044C ")
                || containsIgnoreCase(html, "get_id=43")
                || containsIgnoreCase(html, "magicreform(")
                || containsIgnoreCase(html, "w28_form(");
        boolean hasWearOrSell = containsIgnoreCase(html, "value=\"Надеть\"")
                || containsIgnoreCase(html, "value=\"Продать")
                || containsIgnoreCase(html, "image.neverlands.ru/del.gif")
                || hasUseAction;
        boolean hasInventoryTabs = containsIgnoreCase(html, "<a href=\"?im=")
                || containsIgnoreCase(html, "<a href=?im=")
                || containsIgnoreCase(html, "main.php?im=");
        boolean hasItemActions = containsIgnoreCase(html, "get_id=57")
                || containsIgnoreCase(html, "get_id=58")
                || containsIgnoreCase(html, "if(top.deletetrue('")
                || containsIgnoreCase(html, "image.neverlands.ru/del.gif")
                || containsIgnoreCase(html, "get_id=43")
                || containsIgnoreCase(html, "magicreform(")
                || containsIgnoreCase(html, "w28_form(");
        return (hasNickname && hasWearOrSell && (hasInventoryTabs || hasItemActions))
                || (hasInventoryTabs && hasItemActions);
    }

    static boolean isLikelyInventoryReloadSnapshot(String address, String html) {
        if (address == null || html == null || html.isEmpty()) {
            return false;
        }
        String normalizedAddress = MainPhp.normalizeNeverlandsMainLink(address).toLowerCase(Locale.ROOT);
        if (!normalizedAddress.contains("main.php?r=")) {
            return false;
        }
        if (FightAuto.isFightFrameHtml(html)) {
            return false;
        }
        return mainPhpIsInv(html) || hasInventoryRows(html);
    }

    static boolean isGeneratedTransitionPage(String address, String html) {
        if (address != null && !address.isEmpty()) {
            String normalizedAddress = MainPhp.normalizeNeverlandsMainLink(address).toLowerCase(Locale.ROOT);
            if (normalizedAddress.contains("useaction=addon-action")) {
                return true;
            }
        }
        return html != null && html.contains(HtmlUtils.GENERATED_PAGE_MARKER);
    }

    static boolean isInventoryAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        String normalizedAddress = MainPhp.normalizeNeverlandsMainLink(address).toLowerCase(Locale.ROOT);
        if (!normalizedAddress.contains("main.php")) {
            return false;
        }
        if (normalizedAddress.contains("go=inv")) {
            return true;
        }
        if (normalizedAddress.contains("?wfo=") || normalizedAddress.contains("&wfo=")) {
            return true;
        }
        if (normalizedAddress.contains("useaction=clan-action")) {
            return true;
        }
        if (normalizedAddress.contains("useaction=addon-action") && normalizedAddress.contains("addid=")) {
            return true;
        }
        return normalizedAddress.contains("?wca=")
                || normalizedAddress.contains("&wca=")
                || normalizedAddress.contains("?im=")
                || normalizedAddress.contains("&im=");
    }

    static boolean inventoryAddressMatchesFilter(String address, String filter) {
        if (!isInventoryAddress(address)) {
            return false;
        }
        String normalizedAddress = MainPhp.normalizeNeverlandsMainLink(address);
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        String filterNormalized = filter.startsWith("&") ? filter.substring(1) : filter;
        if (filterNormalized.isEmpty()) {
            return true;
        }
        String[] queryParts = filterNormalized.split("&");
        for (String part : queryParts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String key = (eq >= 0) ? part.substring(0, eq) : part;
            String expectedValue = (eq >= 0) ? part.substring(eq + 1) : "";
            String currentValue = getQueryParamValue(normalizedAddress, key);
            if (currentValue == null || !currentValue.equals(expectedValue)) {
                return false;
            }
        }
        return true;
    }

    static String applyInventoryFilterToLink(String link, String filter) {
        String normalized = MainPhp.normalizeNeverlandsMainLink(link);
        if (normalized.contains("go=inf")) {
            normalized = normalized.replace("go=inf", "go=inv");
        }
        if (filter == null || filter.isEmpty()) {
            return normalized;
        }
        String filterNormalized = filter.startsWith("&") ? filter.substring(1) : filter;
        if (filterNormalized.isEmpty()) {
            return normalized;
        }
        String[] queryParts = filterNormalized.split("&");
        String result = normalized;
        for (String part : queryParts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            String key = part;
            String value = "";
            int eq = part.indexOf('=');
            if (eq >= 0) {
                key = part.substring(0, eq);
                value = part.substring(eq + 1);
            }
            if (key.isEmpty()) {
                continue;
            }
            result = FightAuto.setOrAppendQueryParam(result, key, value);
        }
        return result;
    }

    static String getQueryParamValue(String url, String key) {
        if (url == null || url.isEmpty() || key == null || key.isEmpty()) {
            return null;
        }
        int queryStart = url.indexOf('?');
        if (queryStart == -1 || queryStart + 1 >= url.length()) {
            return null;
        }
        String query = url.substring(queryStart + 1);
        String[] parts = query.split("&");
        String keyWithEq = key + "=";
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (part.startsWith(keyWithEq)) {
                return part.substring(keyWithEq.length());
            }
            if (part.equals(key)) {
                return "";
            }
        }
        return null;
    }

    static String mainPhpFindInv(String html, String filter) {
        if (mainPhpIsInv(html)) {
            return null;
        }
        if (html.contains("view_arena()")) {
            String result = mainPhpFindInvArena(html, filter);
            if (result != null) return result;
        }
        if (html.contains("view_moor()") || html.contains("view_taverna()")
                || html.contains("view_magic_sch()") || html.contains("view_library()")
                || html.contains("view_teleport()")) {
            String result = mainPhpFindInvBuilding(html, filter);
            if (result != null) return result;
        }
        if (html.contains("\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C") || html.contains("\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C")) {
            String result = mainPhpFindInvOld(html, filter);
            if (result != null) return result;
        }
        String patternEnter = "[\"inv\",\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\",\"";
        int pos = html.indexOf(patternEnter);
        if (pos == -1) {
            patternEnter = "[\"inv\",\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\",\"";
            pos = html.indexOf(patternEnter);
        }
        if (pos != -1) {
            pos += patternEnter.length();
            int posEnd = html.indexOf('"', pos);
            if (posEnd != -1) {
                String vcodeInv = html.substring(pos, posEnd);
                String link = "main.php?get_id=56&act=10&go=inv&vcode=" + vcodeInv + filter;
                return MainPhp.buildRedirectHtml("Переключение на инвентарь", link);
            }
        }
        if (html.contains("value=\"Вернуться\">") || html.contains("value=\"\u0412\u0435\u0440\u043D\u0443\u0442\u044C\u0441\u044F\">")) {
            if (html.contains("onclick=\"location='../main.php'\"") || html.contains("onclick=\"location='main.php'\"")) {
                return MainPhp.buildRedirectHtml("Переключение на инвентарь", "main.php");
            }
        }
        return null;
    }

    static String mainPhpFindInvWithFallback(String html, String filter, String address) {
        if (inventoryAddressMatchesFilter(address, filter)) {
            return null;
        }
        if (isInventoryAddress(address)) {
            String normalizedAddress = MainPhp.normalizeNeverlandsMainLink(address);
            String filteredAddress = applyInventoryFilterToLink(normalizedAddress, filter);
            if (!normalizedAddress.equals(filteredAddress)) {
                String msg = "AUTO_FALLBACK_TRACE mainPhpFindInv: address filter sync -> ";
                AppLog.d(TAG, msg);
                return MainPhp.buildRedirectHtml("Переключение на инвентарь", filteredAddress);
            }
            return null;
        }
        String redirectHtml = mainPhpFindInv(html, filter);
        if (redirectHtml != null) {
            return redirectHtml;
        }
        String fallbackInvLink = FightAuto.findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inv", "vcode=");
        if (fallbackInvLink == null) {
            fallbackInvLink = FightAuto.findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
        }
        if (fallbackInvLink == null) {
            return null;
        }
        String filteredLink = applyInventoryFilterToLink(fallbackInvLink, filter);
        String msg = "AUTO_FALLBACK_TRACE mainPhpFindInv: regex fallback -> ";
        AppLog.d(TAG, msg);
        return MainPhp.buildRedirectHtml("Переключение на инвентарь", filteredLink);
    }

    static String mainPhpFindInvArena(String html, String filter) {
        String patternArena = "var vcode = [";
        int pos = html.indexOf(patternArena);
        if (pos == -1) return null;
        pos += patternArena.length();
        int posEnd = html.indexOf(']', pos);
        if (posEnd == -1) return null;
        String vcodeargs = html.substring(pos, posEnd);
        String[] pvcode = vcodeargs.split(",");
        if (pvcode.length < 2) return null;
        String avcode = pvcode[1].replace("\"", "").trim();
        if (avcode.isEmpty()) return null;
        String link = "main.php?get_id=56&act=10&go=inv&vcode=" + avcode + filter;
        return MainPhp.buildRedirectHtml("Переключение на инвентарь", link);
    }

    static String mainPhpFindInvBuilding(String html, String filter) {
        String patternArena = "var vcode = [";
        int pos = html.indexOf(patternArena);
        if (pos == -1) return null;
        pos += patternArena.length();
        String pattern2 = ",[1,\"";
        pos = html.indexOf(pattern2, pos);
        if (pos == -1) return null;
        pos += pattern2.length();
        int posEnd = html.indexOf("]", pos);
        if (posEnd == -1) return null;
        String avcode = html.substring(pos, posEnd - 1);
        if (avcode.isEmpty()) return null;
        String link = "main.php?get_id=56&act=10&go=inv&vcode=" + avcode + filter;
        return MainPhp.buildRedirectHtml("Переключение на инвентарь", link);
    }

    static String mainPhpFindInvOld(String html, String filter) {
        String s1 = "value=\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\">";
        int p1 = html.indexOf(s1);
        if (p1 == -1) {
            s1 = "value=\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\">";
            p1 = html.indexOf(s1);
        }
        if (p1 != -1) {
            String onclick = "onclick=\"location='";
            int p2 = html.lastIndexOf(onclick, p1);
            if (p2 != -1) {
                p2 += onclick.length();
                int p3 = html.indexOf("'", p2);
                if (p3 != -1) {
                    String link = html.substring(p2, p3) + filter;
                    return MainPhp.buildRedirectHtml("Переключение на инвентарь", link);
                }
            }
        }
        String s1x = "class=lbut value=\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\"";
        int p1x = html.indexOf(s1x);
        if (p1x == -1) {
            s1x = "class=lbut value=\"\u0418\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C\"";
            p1x = html.indexOf(s1x);
        }
        if (p1x != -1) {
            String onclick = "onclick=\"location='";
            int p2 = html.indexOf(onclick, p1x);
            if (p2 != -1) {
                p2 += onclick.length();
                int p3 = html.indexOf("'", p2);
                if (p3 != -1) {
                    String link = html.substring(p2, p3) + filter;
                    return MainPhp.buildRedirectHtml("Переключение на инвентарь", link);
                }
            }
        }
        return null;
    }

    public static void syncInventoryCacheFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return;
        }
        try {
            mainPhpInv(html, true);
        } catch (Exception e) {
            String msg = "syncInventoryCacheFromHtml: failed";
            AppLog.w(TAG, msg, e);
        }
    }

    static String mainPhpInv(String html) {
        return mainPhpInv(html, false);
    }

    static String mainPhpInv(String html, boolean cacheOnlyMode) {
        try {
            parseAndSaveComplectsFromInventory(html);

            if (!cacheOnlyMode) {
                html = InventoryEngravingResolver.replaceCoordinateEngravings(html);
            }

            final String patternStartInv = "</b></font></td></tr>";
            int pos = html.indexOf(patternStartInv);
            int posStartInv;
            if (pos == -1) {
                pos = html.indexOf("<tr><td bgcolor=#F5F5F5>");
                if (pos == -1) {
                    return html;
                }
                posStartInv = pos;
            } else {
                pos += patternStartInv.length();
                posStartInv = pos;
            }

            List<InvEntry> invList = new ArrayList<>();
            while (true) {
                final String patternStartTr = "<tr><td bgcolor=#F5F5F5>";
                if (pos + patternStartTr.length() > html.length()
                        || !html.regionMatches(true, pos, patternStartTr, 0, patternStartTr.length())) {
                    break;
                }

                final String patternEndTrLong = "<td bgcolor=#FCFAF3><img src=http://image.neverlands.ru/1x1.gif width=5 height=1></td></tr></table></td></tr></table></td></tr>";
                int posEnd = html.indexOf(patternEndTrLong, pos);
                if (posEnd == -1) {
                    final String patternEndTrShort = "<img src=http://image.neverlands.ru/1x1.gif width=1 height=5></td></tr></table></td></tr>";
                    posEnd = html.indexOf(patternEndTrShort, pos);
                    if (posEnd == -1) {
                        return html;
                    }
                    posEnd += patternEndTrShort.length();
                } else {
                    posEnd += patternEndTrLong.length();
                }

                String htmlEntry = html.substring(pos, posEnd);
                InvEntry invEntry = new InvEntry(htmlEntry);

                if (!cacheOnlyMode) {
                    boolean isBulkDropMatch = !AppVars.BulkDropThing.isEmpty()
                            && !AppVars.BulkDropPrice.isEmpty()
                            && AppVars.BulkDropThing.equalsIgnoreCase(invEntry.DropThing == null ? "" : invEntry.DropThing)
                            && AppVars.BulkDropPrice.equals(invEntry.DropPrice == null ? "" : invEntry.DropPrice);

                    if (invEntry.isExpired() || isBulkDropMatch) {
                        String dropThing = invEntry.DropThing == null ? "" : invEntry.DropThing;
                        String redirectMessage = "Выбрасывание предмета <b>&laquo;" + dropThing + "&raquo;</b>...";
                        return MainPhp.buildRedirectHtml(redirectMessage, invEntry.DropLink == null ? "" : invEntry.DropLink);
                    }

                    boolean isBulkSellMatch = invEntry.PssLink != null
                            && !invEntry.PssLink.isEmpty()
                            && !AppVars.BulkSellThing.isEmpty()
                            && invEntry.PssThing != null
                            && AppVars.BulkSellThing.equals(invEntry.PssThing)
                            && AppVars.BulkSellPrice == invEntry.PssPrice;

                    if (isBulkSellMatch) {
                        AppVars.BulkSellSum += AppVars.BulkSellPrice;
                        String messageSell = "Продажа предмета <b>&laquo;" + invEntry.PssThing
                                + "&raquo;</b>. Выручка " + AppVars.BulkSellSum + " NV...";
                        return MainPhp.buildRedirectHtml(messageSell, invEntry.PssLink);
                    }
                }

                invList.add(invEntry);
                pos = posEnd;
            }

            int parsedCount = invList.size();
            boolean doPack = AppVars.Profile != null && AppVars.Profile.DoInvPack;
            boolean doPackDolg = AppVars.Profile != null && AppVars.Profile.DoInvPackDolg;
            boolean doSort = AppVars.Profile != null && AppVars.Profile.DoInvSort;
            AppLog.d(TAG, "INV_GROUP_TRACE parsed=" + parsedCount
                    + ", doPack=" + doPack
                    + ", doPackDolg=" + doPackDolg
                    + ", doSort=" + doSort);
            int sampleLimit = Math.min(5, invList.size());
            for (int sampleIndex = 0; sampleIndex < sampleLimit; sampleIndex++) {
                InvEntry sample = invList.get(sampleIndex);
                AppLog.d(TAG, "INV_GROUP_TRACE sample[" + sampleIndex + "]"
                        + " name=" + (sample.Name == null ? "" : sample.Name)
                        + ", image=" + (sample.Image == null ? "" : sample.Image)
                        + ", dolg=" + (sample.Dolg == null ? "" : sample.Dolg)
                        + ", pss=" + (sample.PssThing == null ? "" : sample.PssThing)
                        + ", drop=" + (sample.DropThing == null ? "" : sample.DropThing));
            }

            if (!cacheOnlyMode) {
                if (!AppVars.BulkDropThing.isEmpty()) {
                    MainPhp.sendInventoryChatMessage("Выбрасывание пачки <b>&laquo;" + AppVars.BulkDropThing + "&raquo;</b> завершено.");
                    AppVars.BulkDropThing = "";
                }
                if (!AppVars.BulkSellThing.isEmpty()) {
                    MainPhp.sendInventoryChatMessage("Продажа пачки <b>&laquo;" + AppVars.BulkSellThing
                            + "&raquo;</b> завершена. Выручка составила <b>" + AppVars.BulkSellSum + "</b> NV.");
                    AppVars.BulkSellThing = "";
                }
            }

            if (invList.size() > 1 && AppVars.Profile != null && AppVars.Profile.DoInvPack) {
                for (int indexFirst = 0; indexFirst < invList.size() - 1; indexFirst++) {
                    for (int indexSecond = indexFirst + 1; indexSecond < invList.size(); indexSecond++) {
                        InvEntry firstEntry = invList.get(indexFirst);
                        InvEntry secondEntry = invList.get(indexSecond);
                        if (firstEntry.compareTo(secondEntry) != 0) {
                            continue;
                        }
                        if (firstEntry.compareDolg(secondEntry) > 0) {
                            try {
                                InvEntry selectedEntry = (InvEntry) secondEntry.clone();
                                selectedEntry.absorb(firstEntry);
                                invList.set(indexFirst, selectedEntry);
                            } catch (CloneNotSupportedException ignore) {
                                secondEntry.absorb(firstEntry);
                                invList.set(indexFirst, secondEntry);
                            }
                        } else {
                            firstEntry.absorb(secondEntry);
                        }
                        invList.remove(indexSecond);
                        indexSecond--;
                    }
                }
            }

            String msg_pack = "INV_GROUP_TRACE afterPack=" + parsedCount
                    + ", packed=" + Math.max(0, parsedCount - invList.size());
            AppLog.d(TAG, msg_pack);

            if (!cacheOnlyMode) {
                for (InvEntry entry : invList) {
                    entry.addBulkSell();
                    entry.addBulkDelete();
                }
            }

            if (AppVars.Profile != null && AppVars.Profile.DoInvSort) {
                Collections.sort(invList, new InvComparer());
            }

            String msg_sort = "INV_GROUP_TRACE afterSort=";
            AppLog.d(TAG, msg_sort);

            AppVars.InvList = new ArrayList<>(invList);
            if (cacheOnlyMode) {
                String msg_cache = "INV_GROUP_TRACE cache-only sync done: entries=";
                AppLog.d(TAG, msg_cache);
                return html;
            }

            StringBuilder sb = new StringBuilder();
            for (InvEntry entry : invList) {
                sb.append(entry.build());
            }

            StringBuilder rebuilt = new StringBuilder(html.substring(0, posStartInv));
            rebuilt.append(sb);
            rebuilt.append(html.substring(pos));
            return rebuilt.toString();
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            FileLogger.log("Error during mainPhpInv processing: \n" + sw);
            return html;
        }
    }
}
