package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class GameJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        html = html.replace("*,300", "*,400");

        String injection = "var ChatClearSize=12228;" + System.lineSeparator() +
                "var AutoArena = 1;" + System.lineSeparator() +
                "var AutoArenaTimer = -1;" + System.lineSeparator() +
                "function arenareload(now) {" + System.lineSeparator() +
                "  if(!AutoArena && (AutoArenaTimer < 0 || now)) {" + System.lineSeparator() +
                "    var tm = now ? 1000 : 500;" + System.lineSeparator() +
                "    AutoArenaTimer = setTimeout('toprefresh('+now+')', tm);" + System.lineSeparator() +
                "  }" + System.lineSeparator() +
                "}" + System.lineSeparator() +
                "function toprefresh(now){" + System.lineSeparator() +
                "  if(AutoArenaTimer >= 0) {" + System.lineSeparator() +
                "    clearTimeout(AutoArenaTimer);" + System.lineSeparator() +
                "    if(!AutoArena) AutoArenaTimer = setTimeout ('toprefresh(0)', 500);" + System.lineSeparator() +
                "    else AutoArenaTimer = -1;" + System.lineSeparator() +
                "  }" + System.lineSeparator() +
                "  if(!AutoArena || now) top.frames['main_top'].location = './main.php';" + System.lineSeparator() +
                "}" + System.lineSeparator();

        html = html.replace(
                "var ChatClearSize = 12228;",
                injection
        );

        return Russian.getBytes(html);
    }
}
