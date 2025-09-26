package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class ShopJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        html = html.replace(
                "AjaxPost('shop_ajax.php', data, function(xdata) {",
                "AjaxPost('shop_ajax.php', data, function(xdata) { var arg1 = AndroidBridge.BulkSellOldArg1(); var arg2 =  AndroidBridge.BulkSellOldArg2(); if (arg1 > 0) shop_item_sell(arg1, arg2);");
        return Russian.getBytes(html);
    }
}
