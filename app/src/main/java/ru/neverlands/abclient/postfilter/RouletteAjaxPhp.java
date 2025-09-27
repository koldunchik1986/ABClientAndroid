package ru.neverlands.abclient.postfilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.Intent;

import ru.neverlands.abclient.ABClientApplication;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Chat;
import ru.neverlands.abclient.utils.Russian;

public class RouletteAjaxPhp {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        String[] args = html.split("@");

        if (args.length > 2 && args[0].equals("OK")) {
            final String message = "Рулетка: " + args[1];
            Intent intent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
            intent.putExtra("message", message);
            LocalBroadcastManager.getInstance(ABClientApplication.getAppContext()).sendBroadcast(intent);
        }

        Intent intent = new Intent(AppVars.ACTION_WEBVIEW_LOAD_URL);
        intent.putExtra("url", "http://www.neverlands.ru/main.php?mselect=15");
        LocalBroadcastManager.getInstance(ABClientApplication.getAppContext()).sendBroadcast(intent);

        // This filter only triggers actions, it doesn't modify the response.
        return array;
    }
}