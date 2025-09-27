package ru.neverlands.abclient.postfilter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.manager.TorgList;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.Russian;

public class TradePhp {
    // Regex for durability, pre-compiled for efficiency
    private static final Pattern DURABILITY_PATTERN = Pattern.compile("Долговечность: <b>(\\d+)/(\\d+)</b>");

    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        if (!html.contains("onclick=\"location='../main.php'\" value=\"Отказаться от покупки\">")) {
            return array;
        }

        if (AppVars.Profile == null || !AppVars.Profile.TorgActive) {
            return array;
        }

        String salesNick = HelperStrings.subString(html, "<font color=#cc0000>Купить вещь у ", " за ");
        String strSalesPrice = HelperStrings.subString(html, " за ", "NV?</font>");
        if (salesNick == null || strSalesPrice == null) {
            return array;
        }

        int salesPrice;
        try {
            salesPrice = Integer.parseInt(strSalesPrice);
        } catch (NumberFormatException e) {
            return array;
        }

        String nameThing = HelperStrings.subString(html, "NV?</font><br><br> ", "</b>");
        if (nameThing == null) {
            return array; // Or handle error appropriately
        }

        String levelThingStr = HelperStrings.subString(html, "&nbsp;Уровень: <b>", "</b>");
        int intLevelThing = -1;
        if (levelThingStr != null) {
            try {
                intLevelThing = Integer.parseInt(levelThingStr);
            } catch (NumberFormatException e) {
                intLevelThing = -1;
            }
        }
        String levelThing = (levelThingStr != null) ? "[" + levelThingStr + "]" : "";

        String uidThing = HelperStrings.subString(html, "&tradeu=", "&");
        if (uidThing == null) {
            return array;
        }

        Matcher matchS = DURABILITY_PATTERN.matcher(html);
        if (!matchS.find()) {
            return array;
        }

        int realDolg;
        int fullDolg;
        int realPrice;
        try {
            realDolg = Integer.parseInt(matchS.group(1));
            fullDolg = Integer.parseInt(matchS.group(2));
        } catch (Exception e) {
            return array;
        }

        if (fullDolg == 0) {
            return array;
        }

        String strRealPrice = HelperStrings.subString(html, "&nbsp;Цена: <b>", " NV</b>");
        if (strRealPrice == null) {
            return array;
        }

        try {
            realPrice = Integer.parseInt(strRealPrice.replaceAll("\\s", ""));
        } catch (NumberFormatException e) {
            return array;
        }

        if (realDolg < fullDolg) {
            realPrice = (realPrice * realDolg) / fullDolg;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("%<");
        sb.append(salesNick);
        sb.append("> ");

        String itemDescription = " <b>" + nameThing + levelThing + "</b>, долговечность <b>" +
                realDolg + "/" + fullDolg + "</b>, (госцена <font color=#00cc00><b>" + realPrice +
                "NV</b></font>) за <font color=#0000cc><b>" + salesPrice + "NV</b></font> у <b>" + salesNick + "</b>";

        int price90 = (int) Math.round(realPrice * 0.9);
        TorgList.INSTANCE.parse(AppVars.Profile.getTorgTabl());
        int calcPrice = TorgList.INSTANCE.calculate(realPrice);

        if (salesPrice > calcPrice) {
            Chat.addMessageToChat("<font color=#cc0000><b>Отказываемся</b></font> от покупки вещи" + itemDescription);
            sb.append(TorgList.INSTANCE.doFilter(AppVars.Profile.getTorgMessageTooExp(), nameThing, levelThing, salesPrice, calcPrice, realDolg, fullDolg, price90));
            Chat.addAnswer(sb.toString());
            AppVars.ContentMainPhp = Russian.getString(Filter.buildRedirect("Отказ от покупки", "../main.php"));
            return Russian.getBytes(AppVars.ContentMainPhp);
        }

        if (salesPrice < price90) {
            Chat.addMessageToChat("<font color=#cc0000><b>Отказываемся</b></font> от покупки вещи" + itemDescription);
            sb.append(TorgList.INSTANCE.doFilter(AppVars.Profile.getTorgMessageLess90(), nameThing, levelThing, salesPrice, calcPrice, realDolg, fullDolg, price90));
            Chat.addAnswer(sb.toString());
            AppVars.ContentMainPhp = Russian.getString(Filter.buildRedirect("Отказ от покупки", "../main.php"));
            return Russian.getBytes(AppVars.ContentMainPhp);
        }

        String[] spdeny = AppVars.Profile.getTorgDeny().trim().split(";");
        for (String keydeny : spdeny) {
            if (keydeny.isEmpty()) continue;
            keydeny = keydeny.trim();
            boolean nameContains = nameThing.toLowerCase().contains(keydeny.toLowerCase());
            if ((!keydeny.contains(" ") && nameContains) || (keydeny.contains(" ") && nameThing.equalsIgnoreCase(keydeny))) {
                Chat.addMessageToChat("В имени вещи содержится ключевое слово <b>" + keydeny + "</b>, указанное в настройках. Отказываемся от покупки.");
                AppVars.ContentMainPhp = Russian.getString(Filter.buildRedirect("Отказ от покупки", "../main.php"));
                return Russian.getBytes(AppVars.ContentMainPhp);
            }
        }

        String linkPrefix = "../main.php?get_id=0";
        String link = HelperStrings.subString(html, linkPrefix, "'");
        if (link == null) {
            return array;
        }

        Chat.addMessageToChat("<font color=#00cc00><b>Покупаем</b></font> вещь" + itemDescription);

        TorgList.INSTANCE.triggerBuy = false;
        if (AppVars.Profile.isTorgSliv()) {
            if (intLevelThing < AppVars.Profile.TorgMinLevel) {
                if (!nameThing.toLowerCase().contains("(ап)")) {
                    boolean isDisabled = false;
                    String keyword = "";
                    if (AppVars.Profile.TorgEx != null && !AppVars.Profile.TorgEx.isEmpty()) {
                        String[] sp = AppVars.Profile.TorgEx.trim().split(";");
                        for (String s : sp) {
                            keyword = s.trim();
                            boolean nameContains = nameThing.toLowerCase().contains(keyword.toLowerCase());
                            if ((!keyword.contains(" ") && nameContains) || (keyword.contains(" ") && nameThing.equalsIgnoreCase(keyword))) {
                                isDisabled = true;
                                break;
                            }
                        }
                    }

                    if (!isDisabled) {
                        Chat.addMessageToChat("Делаем попытку сдать вещь в лавку...");
                        TorgList.INSTANCE.uidThing = uidThing;
                        TorgList.INSTANCE.triggerBuy = true;
                    } else {
                        Chat.addMessageToChat("В имени вещи содержится ключевое слово <b>" + keyword + "</b>, указанное в настройках. Оставляем вещь себе.");
                    }
                } else {
                    Chat.addMessageToChat("Вещь апнута, оставляем ее себе");
                }
            } else {
                Chat.addMessageToChat("Уровень вещи <b>[" + intLevelThing + "]</b> равен или превышает уровень <b>[" + AppVars.Profile.TorgMinLevel + "]</b>, указанный в настройках. Оставляем вещь себе.");
            }
        } else {
            Chat.addMessageToChat("Перепродажа вещей в лавку отключена в настройках");
        }

        String thanksMessage = sb.toString() + TorgList.INSTANCE.doFilter(AppVars.Profile.getTorgMessageThanks(), nameThing, levelThing, salesPrice, calcPrice, realDolg, fullDolg, price90);
        TorgList.INSTANCE.messageThanks = thanksMessage;

        String noMoneyMessage = sb.toString() + TorgList.INSTANCE.doFilter(AppVars.Profile.getTorgMessageNoMoney(), nameThing, levelThing, salesPrice, calcPrice, realDolg, fullDolg, price90);
        TorgList.INSTANCE.messageNoMoney = noMoneyMessage;

        TorgList.INSTANCE.trigger = true;

        AppVars.ContentMainPhp = Russian.getString(Filter.buildRedirect("Покупка", linkPrefix + link));
        return Russian.getBytes(AppVars.ContentMainPhp);
    }
}