package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class RouletteAjaxPhp {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        String[] args = html.split("@");

        if (args.length > 2 && args[0].equals("OK")) {
            String message = "Рулетка: " + args[1];
            // TODO: Send message to chat via Handler or EventBus
        }

        // TODO: Reload main frame via Handler or EventBus
        // webView.loadUrl("http://www.neverlands.ru/main.php?mselect=15");

        return array;
    }
}