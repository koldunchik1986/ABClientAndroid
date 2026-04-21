package ru.neverlands.abclient.postfilter;

import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.model.ParsedDressed;
import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ChatStats;
import ru.neverlands.abclient.utils.HelperStrings;

final class AutoSkinHandler {

    private static final String TAG = "AutoSkinHandler";
    static final long AUTO_SKIN_KNIFE_RECHECK_INTERVAL_MS = 60_000L;

    private AutoSkinHandler() {
    }

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

    static boolean mainPhpArmedKnife(String html) {
        ParsedDressed parsedDressed = new ParsedDressed(html);
        if (!parsedDressed.Valid) {
            return false;
        }
        return parsedDressed.IsWearKnife();
    }

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
