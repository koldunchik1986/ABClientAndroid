package ru.neverlands.abclient.postfilter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.manager.TorgList;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.Russian;

public class TradePhp {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        if (!html.contains("onclick=\"location='../main.php'\" value=\"Отказаться от покупки\">")) {
            return array;
        }

        String salesNick = HelperStrings.subString(html, "<font color=#cc0000>Купить вещь у ", " за ");
        String strSalesPrice = HelperStrings.subString(html, " за ", "NV?</font>");
        if (strSalesPrice == null) return array;

        int salesPrice;
        try {
            salesPrice = Integer.parseInt(strSalesPrice);
        } catch (NumberFormatException e) {
            return array;
        }

        String nameThing = HelperStrings.subString(html, "NV?</font><br><br> ", "</b>");
        String levelThing = HelperStrings.subString(html, "&nbsp;Уровень: <b>", "</b>");
        int intLevelThing = -1;
        if (levelThing != null) {
            try {
                intLevelThing = Integer.parseInt(levelThing);
                levelThing = "[" + levelThing + "]";
            } catch (NumberFormatException e) {
                intLevelThing = -1;
            }
        }

        String uidThing = HelperStrings.subString(html, "&tradeu=", "&");
        if (uidThing == null) return array;

        // Используем более общее регулярное выражение, чтобы избежать проблем с кодировкой
        Pattern regS = Pattern.compile("\u0414\u043e\u043b\u0433\u043e\u0432\u0435\u0447\u043d\u043e\u0441\u0442\u044c: <b>(\\d+)/(\\d+)</b>");
        Matcher matchS = regS.matcher(html);
        if (!matchS.find()) return array;

        int realDolg = Integer.parseInt(matchS.group(1));
        int fullDolg = Integer.parseInt(matchS.group(2));
        if (fullDolg == 0) return array;

        String strRealPrice = HelperStrings.subString(html, "&nbsp;Цена: <b>", " NV</b>");
        if (strRealPrice == null) return array;

        int realPrice = Integer.parseInt(strRealPrice.replace(" ", ""));

        if (realDolg < fullDolg) {
            realPrice = (realPrice * realDolg) / fullDolg;
        }

        String toNick = "%<" + salesNick + "> ";

        String sbmsg = " <b>" + nameThing + levelThing + "</b>, долговечность <b>" + realDolg + "/" + fullDolg +
                "</b>, (госцена <font color=#00cc00><b>" + realPrice + "NV</b></font>) за <font color=#0000cc><b>" +
                salesPrice + "NV</b></font> у <b>" + salesNick + "</b>";

        int price90 = (int) Math.round(realPrice * 0.9);
        int calcPrice = TorgList.calculate(realPrice);

        if (salesPrice > calcPrice) {
            String message = TorgList.doFilter(AppVars.Profile.getTorgMessageTooExp(), nameThing, levelThing, salesPrice, calcPrice, realDolg, fullDolg, price90);
            return Filter.buildRedirect("Отказ от покупки", "../main.php");
        }

        if (salesPrice < price90) {
            String message = TorgList.doFilter(AppVars.Profile.getTorgMessageLess90(), nameThing, levelThing, salesPrice, calcPrice, realDolg, fullDolg, price90);
            return Filter.buildRedirect("Отказ от покупки", "../main.php");
        }

        String[] spdeny = AppVars.Profile.getTorgDeny().trim().split(";");
        for (String s : spdeny) {
            if (s.isEmpty()) continue;
            String keydeny = s.trim();
            if ((!keydeny.contains(" ") && nameThing.toLowerCase().contains(keydeny.toLowerCase())) ||
                (keydeny.contains(" ") && nameThing.equalsIgnoreCase(keydeny))) {
                return Filter.buildRedirect("Отказ от покупки", "../main.php");
            }
        }

        String linkPrefix = "../main.php?get_id=0";
        String link = HelperStrings.subString(html, linkPrefix, "'");
        if (link == null) return array;

        TorgList.triggerBuy = false;
        if (AppVars.Profile.isTorgSliv()) {
            // ... (логика авто-слива) ...
        }

        String thanksMessage = TorgList.doFilter(AppVars.Profile.getTorgMessageThanks(), nameThing, levelThing, salesPrice, calcPrice, realDolg, fullDolg, price90);
        TorgList.messageThanks = toNick + thanksMessage;

        String noMoneyMessage = TorgList.doFilter(AppVars.Profile.getTorgMessageNoMoney(), nameThing, levelThing, salesPrice, calcPrice, realDolg, fullDolg, price90);
        TorgList.messageNoMoney = toNick + noMoneyMessage;

        TorgList.trigger = true;

        return Filter.buildRedirect("Покупка", linkPrefix + link);
    }
}
