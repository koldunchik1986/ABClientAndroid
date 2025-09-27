package ru.neverlands.abclient.postfilter;

import java.util.ArrayList;
import java.util.List;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class ShopAjaxPhp {

    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        AppVars.BulkSellOldScript = "";
        List<ShopEntry> shopList = new ArrayList<>();

        final String patternStartShop = "</b></div></td></tr>";
        int pos = html.indexOf(patternStartShop);
        if (pos == -1) {
            return array;
        }

        pos += patternStartShop.length();
        int posStartShop = pos;

        while (true) {
            final String patternStartTr = "<tr><td bgcolor=#f9f9f9>";
            if (pos + patternStartTr.length() > html.length() || !html.substring(pos).startsWith(patternStartTr)) {
                break;
            }

            final String patternEndTr = "</td></tr></table></td></tr></table></td></tr>";
            int posEnd = html.indexOf(patternEndTr, pos);
            if (posEnd == -1) {
                return array; // Malformed HTML
            }

            posEnd += patternEndTr.length();
            String htmlEntry = html.substring(pos, posEnd);
            ShopEntry shopEntry = new ShopEntry(htmlEntry);

            if (AppVars.BulkSellOldName != null && !AppVars.BulkSellOldName.isEmpty() &&
                shopEntry.name != null && shopEntry.name.equalsIgnoreCase(AppVars.BulkSellOldName) &&
                AppVars.BulkSellOldPrice != null && !AppVars.BulkSellOldPrice.isEmpty() &&
                shopEntry.price != null && shopEntry.price.equalsIgnoreCase(AppVars.BulkSellOldPrice)) {
                AppVars.BulkSellOldScript = shopEntry.sellCall;
            }

            shopList.add(shopEntry);
            pos = posEnd;
        }

        if (shopList.size() > 1) {
            for (int indexFirst = 0; indexFirst < shopList.size() - 1; indexFirst++) {
                for (int indexSecond = indexFirst + 1; indexSecond < shopList.size(); indexSecond++) {
                    if (shopList.get(indexFirst).compareTo(shopList.get(indexSecond)) != 0) {
                        continue;
                    }

                    shopList.get(indexFirst).inc();
                    shopList.remove(indexSecond);
                    indexSecond--;
                }
            }
        }

        if (AppVars.BulkSellOldScript == null || AppVars.BulkSellOldScript.isEmpty()) {
            AppVars.BulkSellOldName = "";
            AppVars.BulkSellOldPrice = "";
        }

        StringBuilder sb = new StringBuilder();
        for (ShopEntry entry : shopList) {
            sb.append(entry.toString());
        }

        StringBuilder sbnew = new StringBuilder(html.substring(0, posStartShop));
        sbnew.append(sb.toString());
        sbnew.append(html.substring(pos));
        html = sbnew.toString();
        return Russian.getBytes(html);
    }
}
