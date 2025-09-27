package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;
import ru.neverlands.abclient.utils.HelperStrings;

public class IndexCgi {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        // Check if it's actually the login page
        if (!html.contains("<form method=\"post\" id=\"auth_form\" action=\"./game.php\">")) {
            // If not the login page, check for blocking errors that sometimes appear here
            String error = HelperStrings.subString(html, "show_warn(\"", "\"");
            if (error != null && !error.isEmpty()) {
                // In a real implementation, this should trigger a UI update.
                // For now, we log it and prevent further action.
                System.out.println("Login Error: " + error);
                // Returning an empty byte array will show a blank page.
                return new byte[0];
            }
            return array; // Not the login page, do nothing
        }

        // If we are on the login page, perform auto-login
        String userNick = AppVars.Profile.getUserNick();
        String userPassword = AppVars.Profile.getUserPassword();

        if (userNick == null || userNick.isEmpty() || userPassword == null || userPassword.isEmpty()) {
            // If no credentials are saved, just return the original login page
            return array;
        }

        // Build a new HTML page with a self-submitting form
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>Загрузка...</title></head><body>");
        sb.append("Ввод имени и пароля...");
        sb.append("<form action=\"./game.php\" method=POST name=ff>");
        sb.append("<input name=player_nick type=hidden value=\"").append(userNick).append("\"> ");
        sb.append("<input name=player_password type=hidden value=\"").append(userPassword).append("\">");
        sb.append("</form>");
        sb.append("<script language=\"JavaScript\">document.ff.submit();</script>");
        sb.append("</body></html>");

        AppVars.WaitFlash = true;
        AppVars.ContentMainPhp = sb.toString();
        return Russian.getBytes(sb.toString());
    }
}
