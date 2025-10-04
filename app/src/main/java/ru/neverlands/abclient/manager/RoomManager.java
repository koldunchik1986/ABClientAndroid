package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.utils.Russian;

public class RoomManager {
    public static String process(Context context, String html) {
        ru.neverlands.abclient.utils.DebugLogger.log("RoomManager.process: HTML before processing:\n" + html);
        try {
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("js/ch_list.js");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            String chListJs = baos.toString();

            FilterProcRoomResult filterResult = FilterProcRoom(html);

            String script = "<script>" +
                          "window.external = window.AndroidBridge;\n" +
                          "var ChatListU = [" + filterResult.chatListU + "];\n" +
                          chListJs +
                          "</script>";

            ru.neverlands.abclient.utils.DebugLogger.log("RoomManager.process: Generated HTML:\n" + filterResult.html);
            String newHtml = html.replace("</body>", filterResult.html + "</body>");
            newHtml = newHtml.replace("</head>", script + "</head>");

            ru.neverlands.abclient.utils.DebugLogger.log("RoomManager.process: HTML after processing:\n" + newHtml);
            return newHtml;
        } catch (IOException e) {
            e.printStackTrace();
            return html;
        }
    }

    public static void startTracing(MainActivity mainActivity) {
    }

    public static void stopTracing() {
    }

    private static String HtmlChar(String schar) {
        String[] strArray = schar.split(":");
        String nnSec = strArray[1];
        String login = strArray[1];
        while (nnSec.contains("+")) {
            nnSec = nnSec.replace("+", "%2B");
        }

        if (login.contains("<i>")) {
            login = login.replace("<i>", "");
            login = login.replace("</i>", "");
            nnSec = nnSec.replace("<i>", "");
            nnSec = nnSec.replace("</i>", "");
        }

        String ss = "";
        String altadd = "";
        if (strArray[3].length() > 1) {
            String[] signArray = strArray[3].split(";");
            if (signArray.length > 2 && signArray[2].length() > 1) {
                altadd = " (" + signArray[2] + ")";
            }

            ss =
                "<img src=http://image.neverlands.ru/signs/" +
                signArray[0] +
                " width=15 height=12 align=absmiddle alt=\"" +
                signArray[1] +
                altadd +
                "\">&nbsp;";
        }

        String sleeps = "";
        if (strArray.length > 4 && strArray[4].length() > 1) {
            sleeps =
                "<img src=http://image.neverlands.ru/signs/molch.gif width=15 height=12 border=0 alt=\"" +
                strArray[4] +
                "\" align=absmiddle>";
        }

        String ign = "";
        if (strArray.length > 5 && strArray[5].equals("1")) {
            ign =
                "<a href=\"javascript:ch_clear_ignor('" +
                login +
                "');\"><img src=http://image.neverlands.ru/signs/ignor/3.gif width=15 height=12 border=0 alt=\"Снять игнорирование\"></a>";
        }

        String inj = "";
        if (strArray.length > 6 && !strArray[6].equals("0")) {
            inj = "<img src=http://image.neverlands.ru/chat/tr4.gif border=0 width=15 height=12 alt=\"" +
                  strArray[6] +
                  "\" align=absmiddle>";

            if (strArray[6].contains("боевая")) {
                strArray[1] = "<font color=\"#666600\">" + strArray[1] + "</font>";
            } else if (strArray[6].contains("тяжелая")) {
                strArray[1] = "<font color=\"#c10000\">" + strArray[1] + "</font>";
            } else if (strArray[6].contains("средняя")) {
                strArray[1] = "<font color=\"#e94c69\">" + strArray[1] + "</font>";
            } else if (strArray[6].contains("легкая")) {
                strArray[1] = "<font color=\"#ef7f94\">" + strArray[1] + "</font>";
            }
        }

        String psg = "";
        if (strArray.length > 7 && !strArray[7].equals("0")) {
            String[] dilers = {"", "Дилер", "", "", "", "", "", "", "", "", "", "Помощник дилера"};
            psg =
                "<img src=http://image.neverlands.ru/signs/d_sm_" +
                strArray[7] +
                ".gif width=15 height=12 align=absmiddle border=0 alt=\"" +
                dilers[Integer.parseInt(strArray[7])] +
                "\">&nbsp;";
        }

        String align = "";
        if (strArray.length > 8 && !strArray[8].equals("0")) {
            String[] signArray = strArray[8].split(";");
            if (signArray.length >= 2) {
                align =
                    "<img src=http://image.neverlands.ru/signs/" +
                    signArray[0] +
                    " width=15 height=12 align=absmiddle border=0 alt=\"" +
                    signArray[1] +
                    "\">&nbsp";
            }
        }

        return
            "<a href=\"#\" onclick=\"top.say_private('" +
            login +
            "');\"><img src=http://image.neverlands.ru/chat/private.gif width=11 height=12 border=0 align=absmiddle></a>&nbsp;" +
            psg +
            align +
            ss +
            "<a class=\"activenick\" href=\"#\" onclick=\"top.say_to('" +
            login +
            "');\"><font class=nickname><b>" +
            strArray[1] +
            "</b></a>[" +
            strArray[2] +
            "]</font><a href=\"http://www.neverlands.ru/pinfo.cgi?" +
            nnSec +
            "\" onclick=\"window.open(this.href);\"><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>" +
            sleeps +
            ign +
            inj;
    }

    private static FilterProcRoomResult FilterProcRoom(String html) {
        FilterProcRoomResult result = new FilterProcRoomResult();

        Pattern pattern = Pattern.compile("var ChatListU = new Array\\((.*)\\);", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String chatListU = matcher.group(1);
            String[] par = chatListU.split("\\\",\\\"");
            result.numCharsInRoom = par.length;

            StringBuilder sb = new StringBuilder();
            StringBuilder chatListUBuilder = new StringBuilder();
            for (int i = 0; i < par.length; i++) {
                sb.append(HtmlChar(par[i]));
                chatListUBuilder.append("\"" + par[i] + "\"");
                if (i < par.length - 1) {
                    chatListUBuilder.append(",");
                }
            }
            result.html = sb.toString();
            result.chatListU = chatListUBuilder.toString();
        }

        return result;
    }

    public static class MenuItem {
        public String title;
    }

    private static class FilterProcRoomResult {
        int numCharsInRoom;
        String enemyAttack;
        String html;
        String chatListU;
    }
}