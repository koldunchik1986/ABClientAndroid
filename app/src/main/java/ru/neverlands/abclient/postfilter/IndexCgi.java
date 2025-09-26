package ru.neverlands.abclient.postfilter;

import android.text.Html;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class IndexCgi {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        if (!html.toLowerCase().contains("<form method=\"post\" id=\"auth_form\" action=\"./game.php\">")) {
            return array;
        }

        final String errorMarker = "show_warn(\"";
        int pos = html.indexOf(errorMarker);
        if (pos != -1) {
            pos += errorMarker.length();
            int pose = html.indexOf('"', pos);
            if (pose != -1) {
                String error = html.substring(pos, pose);
                if (error != null && !error.isEmpty()) {
                    // TODO: Отправить сообщение об ошибке в UI поток
                    // Например, через LocalBroadcastManager или Handler
                    // AppVars.getUiHandler().post(() -> { /* показать ошибку */ });
                    return new byte[0]; // Возвращаем пустой ответ
                }
            }
        }

        String jump = "game.php";

        StringBuilder sb = new StringBuilder(
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\"></head><body>" +
                "Ввод имени и пароля..." +
                "<form action=\"./" + jump + "\" method=POST name=ff>" +
                "<input name=player_nick type=hidden value=\"");
        sb.append(Html.escapeHtml(AppVars.Profile.getUserNick()));
        sb.append("\"> <input name=player_password type=hidden value=\"");
        sb.append(Html.escapeHtml(AppVars.Profile.getUserPassword()));
        sb.append(
                "\"></form>" +
                "<script language=\"JavaScript\">" +
                "document.ff.submit();" +
                "</script></body></html>");

        AppVars.WaitFlash = true;
        AppVars.ContentMainPhp = sb.toString();
        return Russian.getBytes(AppVars.ContentMainPhp);
    }
}
