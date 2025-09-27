package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.manager.ChatUserList;
import ru.neverlands.abclient.model.ChatUser;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.Russian;

public class Pinfo {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        String params0 = HelperStrings.subString(html, "var params = [[", "]");
        if (params0 != null && !params0.isEmpty()) {
            String[] spar0 = HelperStrings.parseArguments(params0);
            if (spar0.length >= 9) {
                String nick = spar0[0].trim();
                String align = spar0[1];
                String sign = spar0[2];
                String level = spar0[3];
                String clan = spar0[8];
                String status = (spar0.length > 9) ? spar0[9] : "";

                String ali1 = "";
                String ali2 = "";
                switch (align) {
                    case "1":
                        ali1 = "darks.gif";
                        ali2 = "Дети Тьмы";
                        break;
                    case "2":
                        ali1 = "lights.gif";
                        ali2 = "Дети Света";
                        break;
                    case "3":
                        ali1 = "sumers.gif";
                        ali2 = "Дети Сумерек";
                        break;
                    case "4":
                        ali1 = "chaoss.gif";
                        ali2 = "Дети Хаоса";
                        break;
                    case "5":
                        ali1 = "light.gif";
                        ali2 = "Истинный Свет";
                        break;
                    case "6":
                        ali1 = "dark.gif";
                        ali2 = "Истинная Тьма";
                        break;
                    case "7":
                        ali1 = "sumer.gif";
                        ali2 = "Нейтральные Сумерки";
                        break;
                    case "8":
                        ali1 = "chaos.gif";
                        ali2 = "Абсолютный Хаос";
                        break;
                    case "9":
                        ali1 = "angel.gif";
                        ali2 = "Ангел";
                        break;
                }

                if (status != null && !status.isEmpty()) {
                    clan = clan + ", " + status;
                }

                ChatUserList.addUser(new ChatUser(nick, level, sign, clan, ali1, ali2));
            }
        }

        // The original C# code also injected CSS for tooltips.
        // This will be handled differently in Android (native tooltips),
        // so we don't inject the CSS here.

        return Filter.removeDoctype(array);
    }
}