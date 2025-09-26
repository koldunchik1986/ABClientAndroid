package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.StringUtils;

public class ShopEntry implements Comparable<ShopEntry> {
    public String name;
    public String price;
    public String sellCall;

    private final String raw;
    private int count;

    public ShopEntry(String html) {
        this.raw = html;
        this.count = 1;
        this.name = StringUtils.subString(html, "<font class=nickname><b>", "</b>");
        this.price = StringUtils.subString(html, "value=\"Продать за ", " NV\">");
        this.sellCall = StringUtils.subString(html, " onclick=\"shop_item_sell(", ")\" ");
    }

    @Override
    public int compareTo(ShopEntry other) {
        if (other == null) return 1;
        if (price == null || other.price == null || name == null || other.name == null) return 1;

        int nameCompare = name.compareTo(other.name);
        if (nameCompare != 0) {
            return nameCompare;
        }
        return price.compareTo(other.price);
    }

    @Override
    public String toString() {
        if (count == 1 || price == null || price.isEmpty()) {
            return raw;
        }

        int pos = raw.indexOf("<font class=nickname><b>");
        if (pos != -1) {
            pos = raw.indexOf("</b>", pos);
            if (pos != -1) {
                String html = raw.substring(0, pos) + " (" + count + " шт.)" + raw.substring(pos);
                int pssStart = html.indexOf("<input type=button class=invbut onclick=\"shop_item_sell");
                if (pssStart != -1) {
                    int pssEnd = html.indexOf('>', pssStart);
                    if (pssEnd != -1) {
                        String sellAllButton = "&nbsp;<input type=button class=invbut onclick=\"javascript: AndroidBridge.StartBulkOldSell('" + name + "', '" + price + "'); shop_item_sell(" + sellCall + ");\" value=\"Продать все\">";
                        html = html.substring(0, pssEnd + 1) + sellAllButton + html.substring(pssEnd + 1);
                    }
                }
                return html;
            }
        }
        return raw;
    }

    public void inc() {
        this.count++;
    }
}
