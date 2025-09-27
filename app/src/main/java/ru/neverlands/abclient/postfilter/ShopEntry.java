package ru.neverlands.abclient.postfilter;

import androidx.annotation.NonNull;
import ru.neverlands.abclient.utils.HelperStrings;

public class ShopEntry implements Comparable<ShopEntry> {
    public String name;
    public String price;
    public String sellCall;
    private String rawHtml;
    private int count;

    public ShopEntry(String html) {
        this.rawHtml = html;
        this.count = 1;
        this.name = HelperStrings.subString(html, "<font class=nickname><b>", "</b>");
        this.price = HelperStrings.subString(html, "value=\"Продать за ", " NV\">");
        this.sellCall = HelperStrings.subString(html, " onclick=\"shop_item_sell(", ")\" ");
    }

    @Override
    public int compareTo(@NonNull ShopEntry other) {
        if (this.name == null || other.name == null || this.price == null || other.price == null) {
            return 1;
        }
        int nameCompare = this.name.compareTo(other.name);
        if (nameCompare != 0) {
            return nameCompare;
        }
        return this.price.compareTo(other.price);
    }

    @NonNull
    @Override
    public String toString() {
        if (count == 1 || price == null) {
            return rawHtml;
        }

        StringBuilder htmlBuilder = new StringBuilder(rawHtml);

        // Inject item count
        String countStr = " (" + count + " шт.)";
        int nameEndPos = htmlBuilder.indexOf("</b>", htmlBuilder.indexOf("<font class=nickname><b>"));
        if (nameEndPos != -1) {
            htmlBuilder.insert(nameEndPos, countStr);
        }

        // Inject "Sell All" button
        int sellButtonPos = htmlBuilder.indexOf("<input type=button class=invbut onclick=\"shop_item_sell");
        if (sellButtonPos != -1) {
            int endOfButtonTag = htmlBuilder.indexOf(">", sellButtonPos);
            if (endOfButtonTag != -1) {
                // Replace single quotes to avoid breaking JS string
                String safeName = this.name.replace("'", "\' ");
                String safePrice = this.price.replace("'", "\' ");

                String sellAllButton = "&nbsp;<input type=button class=invbut onclick=\"javascript: AndroidBridge.startBulkOldSell('" + safeName + "', '" + safePrice + "'); shop_item_sell(" + this.sellCall + ");\" value=\"Продать все\">";
                htmlBuilder.insert(endOfButtonTag + 1, sellAllButton);
            }
        }

        return htmlBuilder.toString();
    }

    public void inc() {
        this.count++;
    }
}