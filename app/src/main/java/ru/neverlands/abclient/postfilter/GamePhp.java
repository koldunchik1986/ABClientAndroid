package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.Russian;

public class GamePhp {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        html = Filter.removeDoctype(html);

        if (AppVars.WaitFlash) {
            String flashPassword = AppVars.Profile.getUserPasswordFlash();
            if (flashPassword != null && !flashPassword.isEmpty()) {
                String plid = HelperStrings.subString(html, "flashvars=\"plid=", "\"");
                if (plid != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("<html><head><title>Загрузка...</title></head><body>");
                    sb.append("Ввод флеш-пароля...");
                    sb.append("<form action=\"./game.php\" method=POST name=ff>");
                    sb.append("<input name=flcheck type=hidden value=\"").append(flashPassword).append("\">");
                    sb.append("<input name=nid type=hidden value=\"").append(plid).append("\">");
                    sb.append("</form>");
                    sb.append("<script language=\"JavaScript\">document.ff.submit();</script>");
                    sb.append("</body></html>");

                    AppVars.ContentMainPhp = sb.toString();
                    return Russian.getBytes(AppVars.ContentMainPhp);
                }
            }
        }

        AppVars.WaitFlash = false;
        AppVars.ContentMainPhp = html;
        return Russian.getBytes(AppVars.ContentMainPhp);
    }
}