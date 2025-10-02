package ru.neverlands.abclient.manager;

import android.content.Context;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.model.ChatUser;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class RoomManager {
    private static ScheduledExecutorService scheduler;
    private static volatile boolean doStop = false;
    private static String oldRoom = "";

    public static class MenuItem {
        public String title;
        public String action;

        public MenuItem(String title, String action) {
            this.title = title;
            this.action = action;
        }
    }

    private static class FilterProcRoomResult {
        int numCharsInRoom;
        String enemyAttack;
        List<MenuItem> pvList;
        List<MenuItem> trList;
        List<String> myLocation;
    }

    public static void startTracing(Context context) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> {
            if (doStop) {
                return;
            }

            try {
                URL url = new URL("http://www.neverlands.ru/ch.php?lo=1");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Cookie", CookiesManager.obtain(url.toString()));
                InputStream inputStream = connection.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                byte[] data = baos.toByteArray();
                String html = Russian.getString(data);

                if (!html.equals(oldRoom)) {
                    oldRoom = html;
                    process(html);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public static void stopTracing() {
        doStop = true;
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public static String process(String html) {
        String description = find("<font class=placename><b>(.*?)</b></font>", html);
        if (description != null) {
            description.replace("<br>", " ");
            // AppVars.LocationName = description;
        }

        FilterProcRoomResult resultFilterProcRoom = filterProcRoom(html);
        filterGetWalkers(resultFilterProcRoom.myLocation);

        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().updateRoom(resultFilterProcRoom.pvList, "Травмы: " + resultFilterProcRoom.trList.size(), resultFilterProcRoom.trList);
        }

        html = html.replace(
            "</HEAD>",
            "<style type=\"text/css\">"
            +"a.activenick { background-color:inherit; padding: 2 2 2 2; }"
            +"a.activenick:hover { background-color:#99CCFF; padding: 2 2 2 2; }"
            +"a.activeico { background-color:inherit; padding: 1 1 1 1; }"
            +"a.activeico:hover { background-color:#FF9933; padding: 1 1 1 1; }"
            +"</style>"
            +"</head>");

        int pos = html.indexOf("<font");
        if (pos != -1) {
            html = new StringBuilder(html).insert(pos,
                "<script Language=\"JavaScript\">"
                +"function navto()"
                +"{"
                +"e_m = get_by_id ('navbox');"
                +"location = e_m.value;"
                +"}"
                +"function get_by_id(name)"
                +"{"
                +"if (document.getElementById) return document.getElementById(name);"
                +"else if (document.all) return document.all[name];"
                +"}"
                +"</script>"
                +"<select id=\"navbox\" onchange=\"navto()\" style=\"font-family:Arial; font-size:8pt\">"
                +"<option value=\"ch.php?lo=1&\">Текущая клетка</option>"
                +"<option value=\"ch.php?lo=1&\">Исходная клетка</option>"
                +"<option value=\"ch.php?lo=1&r=arena0\">Зал Помощи</option>"
                +"<option value=\"ch.php?lo=1&r=arena1\">Тренировочный зал</option>"
                +"<option value=\"ch.php?lo=1&r=arena2\">Зал Испытаний</option>"
                +"<option value=\"ch.php?lo=1&r=arena3\">Зал Посвящения</option>"
                +"<option value=\"ch.php?lo=1&r=arena4\">Зал Покровителей</option>"
                +"<option value=\"ch.php?lo=1&r=arena5\">Зал Закона</option>"
                +"<option value=\"ch.php?lo=1&r=main\">Городская площадь</option>"
                +"<option value=\"ch.php?lo=1&r=shop_1\">Лавка</option>"
                +"<option value=\"ch.php?lo=1&r=workshop\">Мастерская</option>"
                +"<option value=\"ch.php?lo=1&r=bar0\">Таверна, Большой Зал</option>"
                +"<option value=\"ch.php?lo=1&r=hospi\">Больница</option>"
                +"<option value=\"ch.php?lo=1&r=hospi1\">Комната отдыха</option>"
                +"<option value=\"ch.php?lo=1&r=hospi2\">Палата</option>"
                +"<option value=\"ch.php?lo=1&r=hpr\">Магазин подарков</option>"
                +"<option value=\"ch.php?lo=1&r=hdi\">Дом дилеров</option>"
                +"<option value=\"ch.php?lo=1&r=hau\">Аукцион</option>"
                +"<option value=\"ch.php?lo=1&r=hba\">Банк</option>"
                +"<option value=\"ch.php?lo=1&r=obe\">Обелиск</option>"
                +"<option value=\"ch.php?lo=1&r=post\">Почтовая служба</option>"
                +"<option value=\"ch.php?lo=1&r=market\">Рынок</option>"
                +"<option value=\"ch.php?lo=1&r=prison\">Тюрьма</option>"
                +"<option value=\"ch.php?lo=1&r=shop_2\">Деревня:Лавка</option>"
                +"<option value=\"ch.php?lo=1&r=arena20\">Деревня:Арена</option>"
                +"<option value=\"ch.php?lo=1&r=hsp_1\">Октал:Больница</option>"
                +"<option value=\"ch.php?lo=1&r=shop_3\">Октал:Лавка</option>"
                +"<option value=\"ch.php?lo=1&r=rem_1\">Октал:Пункт переработки</option>"
                +"</select>"
                +"<br>").toString();
        }


        return html;
    }

    private static FilterProcRoomResult filterProcRoom(String html) {
        FilterProcRoomResult result = new FilterProcRoomResult();
        result.pvList = new ArrayList<>();
        result.trList = new ArrayList<>();
        result.myLocation = new ArrayList<>();

        Pattern pattern = Pattern.compile("new Array\\((.*)\\);", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return result;
        }

        String arg = matcher.group(1);
        if (arg == null) {
            return result;
        }

        ru.neverlands.abclient.utils.DebugLogger.log("RoomManager: ChatListU: " + arg);

        String[] par = arg.split(",");
        if (par.length == 0) {
            return result;
        }

        List<ChatUser> users = new ArrayList<>();
        List<String> enemyAttack = new ArrayList<>();

        for (String s : par) {
            String[] pars = s.split(":");
            if (pars.length < 8) {
                continue;
            }

            String userName = pars[1].replace("<i>", "").replace("</i>", "");
            String userLevel = pars[2];
            String userSign = "";
            String userStatus = "";
            if (pars[3].length() > 1) {
                String[] splpar = pars[3].split(";");
                if (splpar.length == 3) {
                    userSign = splpar[0];
                    userStatus = splpar[1] + ", " + splpar[2];
                }
            }

            if (pars[3].startsWith("pv")) {
                result.pvList.add(new MenuItem(userName, "pv"));
            }

            if (!pars[6].equals("0")) {
                result.trList.add(new MenuItem(userName, "tr"));
            }

            int classId = ContactsManager.getClassIdOfContact(userName);
            if (classId == 1) {
                enemyAttack.add(userName);
            }


            users.add(new ChatUser(userName, userLevel, userSign, userStatus));
            result.myLocation.add(userName);
        }

        // AppVars.ChatUsers = users;
        result.numCharsInRoom = par.length;
        result.enemyAttack = TextUtils.join(",", enemyAttack);
        return result;
    }

    private static void filterGetWalkers(List<String> myLocation) {
        if (!AppVars.DoShowWalkers) {
            return;
        }

        if (AppVars.myCharsOld.isEmpty()) {
            AppVars.myCharsOld = myLocation;
            return;
        }

        StringBuilder walkersIn = new StringBuilder();
        for (String user : myLocation) {
            if (!AppVars.myCharsOld.contains(user)) {
                if (walkersIn.length() > 0) {
                    walkersIn.append(", ");
                }
                walkersIn.append(user);
            }
        }

        StringBuilder walkersOut = new StringBuilder();
        for (String user : AppVars.myCharsOld) {
            if (!myLocation.contains(user)) {
                if (walkersOut.length() > 0) {
                    walkersOut.append(", ");
                }
                walkersOut.append(user);
            }
        }

        if (walkersIn.length() > 0) {
            AppVars.myWalkers1 = "Вошли: " + walkersIn.toString();
            // EventSounds.PlayAlarm();
            if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
                AppVars.mainActivity.get().addMessageToChat(AppVars.myWalkers1);
            }
        }

        if (walkersOut.length() > 0) {
            AppVars.myWalkers2 = "Вышли: " + walkersOut.toString();
            if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
                AppVars.mainActivity.get().addMessageToChat(AppVars.myWalkers2);
            }
        }

        AppVars.myCharsOld = myLocation;
    }

    private static String htmlChar(String schar) {
        String[] strArray = schar.split(":");
        String nnSec = strArray[1];
        String login = strArray[1];
        while (nnSec.contains("+")) {
            nnSec = nnSec.replace("+", "%2B");
        }

        if (login.contains("<i>")) {
            login = login.replace("<i>", "").replace("</i>", "");
            nnSec = nnSec.replace("<i>", "").replace("</i>", "");
        }

        String ss = "";
        String altadd = "";
        if (strArray[3].length() > 1) {
            String[] signArray = strArray[3].split(";");
            if (signArray[2].length() > 1) {
                altadd = " (" + signArray[2] + ")";
            }
            ss =
                "<img src=http://image.neverlands.ru/signs/"
                + signArray[0]
                + " width=15 height=12 align=absmiddle alt=\""
                + signArray[1]
                + altadd
                + "\">";
        }

        String sleeps = "";
        if (strArray[4].length() > 1) {
            sleeps =
                "<img src=http://image.neverlands.ru/signs/molch.gif width=15 height=12 border=0 alt=\""
                + strArray[4]
                + "\" align=absmiddle>";
        }

        String ign = "";
        if (strArray[5].equals("1")) {
            ign =
                "<a href=\"javascript:ch_clear_ignor('" +
                login +
                "');\"><img src=http://image.neverlands.ru/signs/ignor/3.gif width=15 height=12 border=0 alt=\"Снять игнорирование\"></a>";
        }

        String inj = "";
        if (!strArray[6].equals("0")) {
            inj = "<img src=http://image.neverlands.ru/chat/tr4.gif border=0 width=15 height=12 alt=\""
                  + strArray[6]
                  + "\" align=absmiddle>";

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
        if (!strArray[7].equals("0")) {
            String[] dilers = { "", "Дилер", "", "", "", "", "", "", "", "", "", "Помощник дилера" };
            psg =
                "<img src=http://image.neverlands.ru/signs/d_sm_"
                + strArray[7]
                + ".gif width=15 height=12 align=absmiddle border=0 alt=\""
                + dilers[Integer.parseInt(strArray[7])]
                + "\">";
        }

        String align = "";
        if (!strArray[8].equals("0")) {
            String[] signArray = strArray[8].split(";");
            if (signArray.length >= 2) {
                align =
                    "<img src=http://image.neverlands.ru/signs/"
                    + signArray[0]
                    + " width=15 height=12 align=absmiddle border=0 alt=\""
                    + signArray[1]
                    + "\">";
            }
        }

        String wmlabQ = " <a class=\"activeico\" href=\"javascript:AndroidBridge.quick('" + login + "')\"><img src=http://image.neverlands.ru/signs/c227.gif width=15 height=12 border=0 title=' ' align=absmiddle></a>";

        return
            "<a href=\"#\" onclick=\"top.say_private('" +
            login +
            "');\"><img src=http://image.neverlands.ru/chat/private.gif width=11 height=12 border=0 align=absmiddle></a>&nbsp;"
            + psg
            + align
            + ss
            + "<a class=\"activenick\" href=\"#\" onclick=\"top.say_to('" +
            login +
            "');\"><font class=nickname><b>" +
            strArray[1]
            + "</b></a>[" +
            strArray[2]
            + "]</font><a href=\"http://www.neverlands.ru/pinfo.cgi?" +
            nnSec +
            "\" target=\"_blank\"><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>"
            + sleeps
            + ign
            + inj
            + wmlabQ;
    }

    public static String find(String pattern, String text) {
        Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}