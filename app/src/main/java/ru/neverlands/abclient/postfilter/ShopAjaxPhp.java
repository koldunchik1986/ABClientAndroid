package ru.neverlands.abclient.postfilter;

import java.util.ArrayList;
import java.util.ListIterator;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class ShopAjaxPhp {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        AppVars.BulkSellOldScript = "";
        AppVars.ShopList.clear();

        final String patternStartShop = "</b></div></td></tr>";
        int pos = html.indexOf(patternStartShop);
        if (pos == -1) return array;

        pos += patternStartShop.length();
        int posStartShop = pos;

        while (true) {
            final String patternStartTr = "<tr><td bgcolor=#f9f9f9>";
            if (pos + patternStartTr.length() > html.length() || !html.substring(pos).startsWith(patternStartTr)) {
                break;
            }

            final String patternEndTr = "</td></tr></table></td></tr></table></td></tr>";
            int posEnd = html.indexOf(patternEndTr, pos);
            if (posEnd == -1) return array;

            posEnd += patternEndTr.length();
            String htmlEntry = html.substring(pos, posEnd);
            ShopEntry shopEntry = new ShopEntry(htmlEntry);

            if (AppVars.BulkSellOldScript.isEmpty() &&
                AppVars.BulkSellOldName != null && !AppVars.BulkSellOldName.isEmpty() &&
                shopEntry.name != null && shopEntry.name.equalsIgnoreCase(AppVars.BulkSellOldName) &&
                AppVars.BulkSellOldPrice != null && !AppVars.BulkSellOldPrice.isEmpty() &&
                shopEntry.price != null && shopEntry.price.equalsIgnoreCase(AppVars.BulkSellOldPrice)) {
                AppVars.BulkSellOldScript = shopEntry.sellCall;
            }

            AppVars.ShopList.add(shopEntry);
            pos = posEnd;
        }

        if (AppVars.ShopList.size() > 1) {
            for (int indexFirst = 0; indexFirst < AppVars.ShopList.size() - 1; indexFirst++) {
                for (int indexSecond = indexFirst + 1; indexSecond < AppVars.ShopList.size(); indexSecond++) {
                    if (AppVars.ShopList.get(indexFirst).compareTo(AppVars.ShopList.get(indexSecond)) == 0) {
                        AppVars.ShopList.get(indexFirst).inc();
                        AppVars.ShopList.remove(indexSecond);
                        indexSecond--;
                    }
                }
            }
        }

        if (AppVars.BulkSellOldScript.isEmpty()) {
            AppVars.BulkSellOldName = "";
            AppVars.BulkSellOldPrice = "";
        }

        StringBuilder sb = new StringBuilder();
        for (ShopEntry entry : AppVars.ShopList) {
            sb.append(entry.toString());
        }

        String finalHtml = html.substring(0, posStartShop) + sb.toString() + html.substring(pos);
        return Russian.getBytes(finalHtml);
    }
}
